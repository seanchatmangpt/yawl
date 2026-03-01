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
package org.yawlfoundation.yawl.erlang.schema;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Loads and caches ODCS schema contracts from classpath YAML resources at startup.
 *
 * <p>The registry scans a list of task classes for {@link TaskSchemaContract}
 * annotations and loads the referenced YAML files from the classpath. Loaded
 * schemas are cached in-memory for the lifetime of the registry.</p>
 *
 * <p>The YAML schema parser is a lightweight built-in implementation that handles
 * the simple ODCS subset needed for field-level validation. It does not require
 * any external YAML library.</p>
 *
 * <p>Usage:
 * <pre>
 *   SchemaContractRegistry registry = new SchemaContractRegistry(
 *       List.of(ValidateOrderTask.class, RouteOrderTask.class));
 *
 *   Optional&lt;ParsedSchema&gt; schema = registry.getInputSchema("ValidateOrderTask");
 * </pre>
 */
public final class SchemaContractRegistry {

    private static final Logger LOG = Logger.getLogger(SchemaContractRegistry.class.getName());

    final Map<String, ParsedSchema> inputSchemas = new HashMap<>();
    final Map<String, ParsedSchema> outputSchemas = new HashMap<>();
    final Map<String, ParsedSchema> inputFallbackSchemas = new HashMap<>();

    /**
     * Creates a registry from the given task classes, loading their schema contracts.
     *
     * @param taskClasses classes to scan for {@link TaskSchemaContract} annotations
     * @throws IllegalArgumentException if taskClasses is null
     */
    public SchemaContractRegistry(List<Class<?>> taskClasses) {
        if (taskClasses == null) throw new IllegalArgumentException("taskClasses must not be null");

        for (Class<?> taskClass : taskClasses) {
            TaskSchemaContract contract = taskClass.getAnnotation(TaskSchemaContract.class);
            if (contract == null) continue;

            String taskId = taskClass.getSimpleName();

            if (!contract.input().isBlank()) {
                loadAndCache(contract.input(), taskId, inputSchemas);
            }
            if (!contract.output().isBlank()) {
                loadAndCache(contract.output(), taskId, outputSchemas);
            }
            if (!contract.inputFallback().isBlank()) {
                loadAndCache(contract.inputFallback(), taskId, inputFallbackSchemas);
            }
        }

        LOG.info("SchemaContractRegistry: loaded " + inputSchemas.size() + " input schemas, "
                + outputSchemas.size() + " output schemas");
    }

    /**
     * Returns the primary input schema for the given task ID.
     *
     * @param taskId the task class simple name
     * @return the parsed schema, or empty if none registered
     */
    public Optional<ParsedSchema> getInputSchema(String taskId) {
        return Optional.ofNullable(inputSchemas.get(taskId));
    }

    /**
     * Returns the output schema for the given task ID.
     *
     * @param taskId the task class simple name
     * @return the parsed schema, or empty if none registered
     */
    public Optional<ParsedSchema> getOutputSchema(String taskId) {
        return Optional.ofNullable(outputSchemas.get(taskId));
    }

    /**
     * Returns the fallback input schema for the given task ID.
     *
     * @param taskId the task class simple name
     * @return the fallback schema, or empty if none registered
     */
    public Optional<ParsedSchema> getInputFallbackSchema(String taskId) {
        return Optional.ofNullable(inputFallbackSchemas.get(taskId));
    }

    private void loadAndCache(String resourcePath, String taskId, Map<String, ParsedSchema> target) {
        try {
            ParsedSchema schema = loadSchema(resourcePath);
            target.put(taskId, schema);
            LOG.fine("Loaded schema '" + schema.name() + "' for task '" + taskId
                    + "' from " + resourcePath);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to load schema from '" + resourcePath + "' for task '" + taskId + "'", e);
        }
    }

    /**
     * Loads and parses a schema from a classpath resource.
     *
     * <p>Implements a lightweight YAML parser for the following ODCS schema subset:
     * <pre>
     *   name: SchemaName
     *   version: "1.0"
     *   properties:
     *     fieldName:
     *       type: string
     *       required: true
     * </pre>
     */
    static ParsedSchema loadSchema(String resourcePath) throws IOException {
        InputStream stream = SchemaContractRegistry.class.getClassLoader()
                .getResourceAsStream(resourcePath);
        if (stream == null) {
            throw new IOException("Classpath resource not found: " + resourcePath);
        }

        String yaml;
        try (stream) {
            yaml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        return parseOdcsYaml(yaml, resourcePath);
    }

    /**
     * Parses a simplified ODCS YAML schema string.
     *
     * <p>Handles only the subset needed for structural validation:
     * {@code name}, {@code version}, and {@code properties} with
     * {@code type} and {@code required} per field.</p>
     */
    static ParsedSchema parseOdcsYaml(String yaml, String resourcePath) {
        String name = "";
        String version = "";
        List<SchemaField> fields = new ArrayList<>();

        String[] lines = yaml.split("\n");
        int i = 0;

        // Parse top-level keys
        while (i < lines.length) {
            String line = lines[i];
            String trimmed = line.trim();

            if (trimmed.startsWith("name:")) {
                name = extractValueAfter(trimmed, "name:");
            } else if (trimmed.startsWith("version:")) {
                version = extractValueAfter(trimmed, "version:").replace("\"", "");
            } else if (trimmed.equals("properties:")) {
                i++;
                // Parse property fields
                while (i < lines.length) {
                    String propLine = lines[i];
                    String propTrimmed = propLine.trim();

                    // A property name line is indented by 2 (or more) spaces and ends with ':'
                    if (isFieldName(propLine, propTrimmed)) {
                        String fieldName = propTrimmed.endsWith(":")
                                ? propTrimmed.substring(0, propTrimmed.length() - 1)
                                : propTrimmed;
                        String fieldType = "string";
                        boolean required = false;

                        // Look ahead for type and required
                        int j = i + 1;
                        while (j < lines.length) {
                            String attrLine = lines[j].trim();
                            if (attrLine.startsWith("type:")) {
                                fieldType = extractValueAfter(attrLine, "type:");
                            } else if (attrLine.startsWith("required:")) {
                                required = "true".equalsIgnoreCase(
                                        extractValueAfter(attrLine, "required:"));
                            } else if (!attrLine.isBlank() && isFieldName(lines[j], attrLine)) {
                                break;  // next field started
                            } else if (!attrLine.isBlank() && !attrLine.startsWith(" ")
                                    && !lines[j].startsWith("    ")) {
                                break;  // back to top-level
                            }
                            j++;
                        }

                        fields.add(new SchemaField(fieldName, fieldType, required));
                        i = j;
                        continue;
                    }
                    i++;
                }
                continue;
            }
            i++;
        }

        if (name.isBlank()) {
            // Use resource path as fallback name
            name = resourcePath.replaceAll(".*/", "").replace(".yaml", "").replace(".yml", "");
        }

        return new ParsedSchema(name.isBlank() ? "unnamed" : name, version, fields, resourcePath);
    }

    /**
     * Extracts the value portion of a YAML key-value line after the given prefix.
     *
     * <p>Callers must only invoke this method on lines that have already been confirmed
     * to start with {@code prefix} via {@code String.startsWith()}. Passing a line
     * that does not contain the prefix indicates a programming error and throws.</p>
     *
     * @param line   the trimmed YAML line
     * @param prefix the key prefix (e.g. {@code "name:"})
     * @return the trimmed value after the prefix
     * @throws IllegalStateException if prefix is not found in line (indicates caller bug)
     */
    private static String extractValueAfter(String line, String prefix) {
        int idx = line.indexOf(prefix);
        if (idx < 0) {
            throw new IllegalStateException(
                    "Expected prefix '" + prefix + "' not found in YAML line: " + line
                    + " — callers must guard with startsWith() before calling extractValueAfter()");
        }
        return line.substring(idx + prefix.length()).trim();
    }

    private static boolean isFieldName(String rawLine, String trimmedLine) {
        // A field name line starts with at least 2 spaces and doesn't start with '-'
        return rawLine.length() > 2
                && rawLine.charAt(0) == ' '
                && rawLine.charAt(1) == ' '
                && !trimmedLine.startsWith("-")
                && !trimmedLine.startsWith("#")
                && !trimmedLine.isBlank()
                && trimmedLine.endsWith(":");
    }
}
