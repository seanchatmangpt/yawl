/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.graalpy;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates strongly-typed Java interfaces from Python type stub ({@code .pyi}) files.
 *
 * <p>Eliminates the need for untyped string-based Python calls in Java by generating
 * compile-time-checked Java interfaces from Python's {@code mypy} type annotations.
 * The generated interfaces are intended to be used with {@link PythonExecutionEngine#evalAs}
 * or the GraalPy polyglot type coercion system.</p>
 *
 * <h2>Python → Java type mapping</h2>
 * <pre>
 * Python type annotation    Java type
 * ─────────────────────    ─────────────────────────────
 * str                       String
 * int                       long
 * float                     double
 * bool                      boolean
 * None                      void
 * dict[str, V]              Map&lt;String, V&gt;
 * list[T]                   List&lt;T&gt;
 * Optional[T]               @Nullable T
 * Any                       Object
 * </pre>
 *
 * <h2>Input format (.pyi)</h2>
 * <p>Python type stub files use the same syntax as Python source but contain only
 * type signatures (no implementations). Example:</p>
 * <pre>
 * class TextAnalyzer:
 *     def analyze(self, text: str) -> dict[str, float]: ...
 *     def classify(self, text: str) -> str: ...
 *     def batch_analyze(self, texts: list[str]) -> list[dict[str, float]]: ...
 * </pre>
 *
 * <h2>Generated Java interface</h2>
 * <pre>{@code
 * public interface TextAnalyzer {
 *     Map<String, Double> analyze(String text);
 *     String classify(String text);
 *     List<Map<String, Double>> batchAnalyze(List<String> texts);
 * }
 * }</pre>
 *
 * <h2>Naming conventions</h2>
 * <ul>
 *   <li>Python {@code snake_case} method names are converted to Java {@code camelCase}</li>
 *   <li>Python constructor ({@code __init__}) maps to a factory method prefixed with {@code new}</li>
 *   <li>Python fields map to JavaBean getter/setter pairs</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class PythonInterfaceGenerator {

    private static final Logger log = LoggerFactory.getLogger(PythonInterfaceGenerator.class);

    /** Pattern matching a Python class definition line: {@code class Foo:} or {@code class Foo(Base):} */
    private static final Pattern CLASS_PATTERN =
            Pattern.compile("^class\\s+(\\w+)(?:\\s*\\([^)]*\\))?\\s*:");

    /** Pattern matching a Python method stub: {@code def method_name(self, p: type) -> ret: ...} */
    private static final Pattern METHOD_PATTERN =
            Pattern.compile("^\\s{4}def\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*->\\s*([^:]+)\\s*:\\s*\\.{3}");

    /** Pattern matching a typed field/attribute: {@code    field_name: type} */
    private static final Pattern FIELD_PATTERN =
            Pattern.compile("^\\s{4}(\\w+)\\s*:\\s*(\\S+)\\s*$");

    /** Maps Python built-in type annotations to Java types. */
    private static final Map<String, String> TYPE_MAP = Map.ofEntries(
            Map.entry("str", "String"),
            Map.entry("int", "long"),
            Map.entry("float", "double"),
            Map.entry("bool", "boolean"),
            Map.entry("None", "void"),
            Map.entry("Any", "Object"),
            Map.entry("bytes", "byte[]"),
            Map.entry("object", "Object")
    );

    private final String targetPackage;

    /**
     * Creates a generator that places generated interfaces in the given Java package.
     *
     * @param targetPackage  Java package name for generated interfaces (e.g.
     *                       {@code "org.yawlfoundation.yawl.graalpy.generated"})
     */
    public PythonInterfaceGenerator(String targetPackage) {
        if (targetPackage == null || targetPackage.isBlank()) {
            throw new IllegalArgumentException("targetPackage must not be null or blank");
        }
        this.targetPackage = targetPackage;
    }

    /**
     * Generates a Java interface source file from a Python {@code .pyi} stub file.
     *
     * <p>The generated interface is written to {@code outputDir/<ClassName>.java}.
     * If the stub contains multiple classes, only the first class is processed.
     * Call this method once per class by splitting stubs into separate files.</p>
     *
     * @param stubFile   path to a Python {@code .pyi} stub file; must exist
     * @param outputDir  directory where the generated {@code .java} file will be written
     * @return path to the generated {@code .java} file
     * @throws PythonException  if the stub file cannot be read, parsed, or the interface
     *                          cannot be written
     */
    public Path generateFromStub(Path stubFile, Path outputDir) {
        if (!Files.exists(stubFile)) {
            throw new PythonException(
                    "Python stub file not found: " + stubFile,
                    PythonException.ErrorKind.INTERFACE_GENERATION_ERROR);
        }

        List<String> lines;
        try {
            lines = Files.readAllLines(stubFile, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new PythonException(
                    "Cannot read stub file: " + stubFile + " — " + e.getMessage(),
                    PythonException.ErrorKind.INTERFACE_GENERATION_ERROR, e);
        }

        GeneratedInterface generated = parseStub(lines, stubFile.getFileName().toString());
        String javaSource = renderJavaInterface(generated);

        try {
            Files.createDirectories(outputDir);
            Path outputFile = outputDir.resolve(generated.className + ".java");
            Files.writeString(outputFile, javaSource, StandardCharsets.UTF_8);
            log.info("Generated interface {} from stub {}", outputFile, stubFile);
            return outputFile;
        } catch (IOException e) {
            throw new PythonException(
                    "Cannot write generated interface to " + outputDir + " — " + e.getMessage(),
                    PythonException.ErrorKind.INTERFACE_GENERATION_ERROR, e);
        }
    }

    /**
     * Generates Java interface source code from a Python stub string.
     *
     * <p>Use this for programmatic generation without file I/O.</p>
     *
     * @param stubSource   Python stub source code; must not be null
     * @param stubName     descriptive name for the stub (used in error messages)
     * @return the generated Java interface source code
     * @throws PythonException  if the stub cannot be parsed
     */
    public String generateFromString(String stubSource, String stubName) {
        List<String> lines = List.of(stubSource.split("\\n", -1));
        GeneratedInterface generated = parseStub(lines, stubName);
        return renderJavaInterface(generated);
    }

    // ── Private parsing logic ────────────────────────────────────────────────────

    private GeneratedInterface parseStub(List<String> lines, String stubName) {
        String className = null;
        List<MethodSpec> methods = new ArrayList<>();
        List<FieldSpec> fields = new ArrayList<>();

        for (String line : lines) {
            if (line.isBlank() || line.trim().startsWith("#")) {
                continue;
            }

            // Try to match class definition
            Matcher classMatcher = CLASS_PATTERN.matcher(line);
            if (classMatcher.find()) {
                className = classMatcher.group(1);
                continue;
            }

            if (className == null) {
                continue; // Skip module-level content; only process class members
            }

            // Try to match method definition
            Matcher methodMatcher = METHOD_PATTERN.matcher(line);
            if (methodMatcher.find()) {
                String methodName = methodMatcher.group(1);
                String params = methodMatcher.group(2).trim();
                String returnType = methodMatcher.group(3).trim();

                if ("__init__".equals(methodName)) {
                    continue; // Skip constructors; factory methods are out of scope here
                }
                if (methodName.startsWith("__") && methodName.endsWith("__")) {
                    continue; // Skip dunder methods
                }

                methods.add(new MethodSpec(
                        toJavaCamelCase(methodName),
                        parseParameters(params),
                        mapType(returnType)
                ));
                continue;
            }

            // Try to match field definition
            Matcher fieldMatcher = FIELD_PATTERN.matcher(line);
            if (fieldMatcher.find()) {
                String fieldName = fieldMatcher.group(1);
                String fieldType = fieldMatcher.group(2);
                if (!fieldName.startsWith("_")) { // Skip private fields
                    fields.add(new FieldSpec(fieldName, mapType(fieldType)));
                }
            }
        }

        if (className == null) {
            throw new PythonException(
                    "No class definition found in stub: " + stubName + ". "
                    + "Ensure the stub contains exactly one 'class Foo:' declaration.",
                    PythonException.ErrorKind.INTERFACE_GENERATION_ERROR);
        }

        return new GeneratedInterface(className, methods, fields);
    }

    private List<ParamSpec> parseParameters(String params) {
        List<ParamSpec> result = new ArrayList<>();
        if (params.isBlank()) {
            return result;
        }
        for (String param : params.split(",")) {
            param = param.trim();
            if (param.equals("self") || param.equals("cls")) {
                continue; // Skip Python self/cls
            }
            if (param.contains(":")) {
                String[] parts = param.split(":", 2);
                String name = toJavaCamelCase(parts[0].trim());
                String type = mapType(parts[1].trim());
                result.add(new ParamSpec(name, type));
            } else if (!param.isBlank()) {
                // No type annotation — map to Object
                result.add(new ParamSpec(toJavaCamelCase(param), "Object"));
            }
        }
        return result;
    }

    private String mapType(@Nullable String pythonType) {
        if (pythonType == null || pythonType.isBlank() || pythonType.equals("...")) {
            return "Object";
        }
        pythonType = pythonType.trim();

        // Handle Optional[T]
        if (pythonType.startsWith("Optional[") && pythonType.endsWith("]")) {
            String inner = pythonType.substring(9, pythonType.length() - 1);
            return "@Nullable " + mapType(inner);
        }

        // Handle list[T] → List<T>
        if (pythonType.startsWith("list[") && pythonType.endsWith("]")) {
            String inner = pythonType.substring(5, pythonType.length() - 1);
            return "List<" + boxed(mapType(inner)) + ">";
        }
        if (pythonType.startsWith("List[") && pythonType.endsWith("]")) {
            String inner = pythonType.substring(5, pythonType.length() - 1);
            return "List<" + boxed(mapType(inner)) + ">";
        }

        // Handle dict[K, V] → Map<K, V>
        if ((pythonType.startsWith("dict[") || pythonType.startsWith("Dict["))
                && pythonType.endsWith("]")) {
            String inner = pythonType.substring(5, pythonType.length() - 1);
            String[] kv = splitGenericArgs(inner);
            if (kv.length == 2) {
                return "Map<" + boxed(mapType(kv[0].trim())) + ", " + boxed(mapType(kv[1].trim())) + ">";
            }
        }

        // Look up primitive types
        String mapped = TYPE_MAP.get(pythonType);
        return mapped != null ? mapped : pythonType; // Unknown types pass through as-is
    }

    /** Returns the boxed (reference) form of a Java type for use in generics. */
    private static String boxed(String javaType) {
        return switch (javaType) {
            case "long" -> "Long";
            case "int" -> "Integer";
            case "double" -> "Double";
            case "float" -> "Float";
            case "boolean" -> "Boolean";
            case "byte[]" -> "byte[]";
            default -> javaType;
        };
    }

    /** Converts Python snake_case to Java camelCase. */
    private static String toJavaCamelCase(String snakeCase) {
        if (!snakeCase.contains("_")) {
            return snakeCase;
        }
        StringBuilder result = new StringBuilder();
        boolean capitaliseNext = false;
        for (char c : snakeCase.toCharArray()) {
            if (c == '_') {
                capitaliseNext = true;
            } else if (capitaliseNext) {
                result.append(Character.toUpperCase(c));
                capitaliseNext = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /** Splits generic type arguments respecting nested brackets. */
    private static String[] splitGenericArgs(String args) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') depth--;
            else if (c == ',' && depth == 0) {
                parts.add(args.substring(start, i).trim());
                start = i + 1;
            }
        }
        parts.add(args.substring(start).trim());
        return parts.toArray(new String[0]);
    }

    private String renderJavaInterface(GeneratedInterface gi) {
        StringBuilder sb = new StringBuilder();
        sb.append("/*\n");
        sb.append(" * Auto-generated by YAWL PythonInterfaceGenerator.\n");
        sb.append(" * Source: Python type stub (.pyi)\n");
        sb.append(" * Do not edit manually — regenerate from the stub file.\n");
        sb.append(" */\n\n");
        sb.append("package ").append(targetPackage).append(";\n\n");

        // Determine required imports
        boolean needsList = gi.methods.stream().anyMatch(m ->
                m.returnType.startsWith("List<")
                || m.params.stream().anyMatch(p -> p.type.startsWith("List<")));
        boolean needsMap = gi.methods.stream().anyMatch(m ->
                m.returnType.startsWith("Map<")
                || m.params.stream().anyMatch(p -> p.type.startsWith("Map<")));
        boolean needsNullable = gi.methods.stream().anyMatch(m ->
                m.returnType.contains("@Nullable")
                || m.params.stream().anyMatch(p -> p.type.contains("@Nullable")));

        if (needsList) sb.append("import java.util.List;\n");
        if (needsMap) sb.append("import java.util.Map;\n");
        if (needsNullable) sb.append("import org.jspecify.annotations.Nullable;\n");
        if (needsList || needsMap || needsNullable) sb.append("\n");

        sb.append("/**\n");
        sb.append(" * Java interface generated from Python type stub for {@code ")
          .append(gi.className).append("}.\n");
        sb.append(" *\n");
        sb.append(" * <p>Use with {@link org.yawlfoundation.yawl.graalpy.PythonExecutionEngine#evalAs}\n");
        sb.append(" * to obtain a type-safe proxy over a GraalPy Python object.</p>\n");
        sb.append(" */\n");
        sb.append("public interface ").append(gi.className).append(" {\n");

        // Generate getter/setter pairs for fields
        for (FieldSpec field : gi.fields) {
            String capitalised = Character.toUpperCase(field.name.charAt(0)) + field.name.substring(1);
            sb.append("\n    /** Returns the {@code ").append(field.name).append("} attribute. */\n");
            sb.append("    ").append(field.type).append(" get").append(capitalised).append("();\n");
        }

        // Generate method declarations
        for (MethodSpec method : gi.methods) {
            sb.append("\n    /**\n");
            sb.append("     * Invokes the Python {@code ").append(method.name).append("} method.\n");
            sb.append("     */\n");
            sb.append("    ").append(method.returnType).append(" ").append(method.name).append("(");
            for (int i = 0; i < method.params.size(); i++) {
                ParamSpec p = method.params.get(i);
                sb.append(p.type).append(" ").append(p.name);
                if (i < method.params.size() - 1) sb.append(", ");
            }
            sb.append(");\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    // ── Value types ───────────────────────────────────────────────────────────────

    private record GeneratedInterface(String className, List<MethodSpec> methods, List<FieldSpec> fields) {}
    private record MethodSpec(String name, List<ParamSpec> params, String returnType) {}
    private record ParamSpec(String name, String type) {}
    private record FieldSpec(String name, String type) {}
}
