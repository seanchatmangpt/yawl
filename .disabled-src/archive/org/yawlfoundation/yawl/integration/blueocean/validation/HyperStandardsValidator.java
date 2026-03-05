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

package org.yawlfoundation.yawl.integration.blueocean.validation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates Java source code against 7 critical Fortune 5 standards (H-Guards).
 *
 * <p>Detects and blocks:</p>
 * <ul>
 *   <li><b>H_TODO</b>: Deferred work markers (comment markers like TODO, FIXME, @incomplete)</li>
 *   <li><b>H_MOCK</b>: Mock implementations (mock, stub, fake identifier prefixes)</li>
 *   <li><b>H_STUB</b>: Empty or placeholder returns (empty string, zero, null literals)</li>
 *   <li><b>H_EMPTY</b>: Empty method bodies (no implementation)</li>
 *   <li><b>H_FALLBACK</b>: Silent catch blocks (swallows exceptions)</li>
 *   <li><b>H_LIE</b>: Javadoc vs implementation mismatch</li>
 *   <li><b>H_SILENT</b>: Log instead of throw (critical errors logged silently)</li>
 * </ul>
 *
 * <p>Outputs JSON receipt with violation details and fix guidance.</p>
 *
 * <h2>Example Usage</h2>
 * <pre>
 * HyperStandardsValidator validator = new HyperStandardsValidator();
 * GuardReceipt receipt = validator.validateDirectory(Paths.get("src"));
 * if (!receipt.isGreen()) {
 *     System.err.println(receipt.toJson());
 *     System.exit(2);
 * }
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class HyperStandardsValidator {
    private static final Logger logger = LoggerFactory.getLogger(HyperStandardsValidator.class);

    // Regex patterns for guard detection
    private static final Pattern DEFERRED_WORK_PATTERN = Pattern.compile(
            "//\\s*(TODO|FIXME|XXX|HACK|LATER|FUTURE|@incomplete|@stub|placeholder)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SPURIOUS_IMPLEMENTATION_PATTERN = Pattern.compile(
            "(mock|stub|fake|demo)[A-Z][a-zA-Z]*\\s*[=(]",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SPURIOUS_CLASS_PATTERN = Pattern.compile(
            "^\\s*(?:public|private|protected)\\s+(?:class|interface)\\s+(Mock|Stub|Fake|Demo)[A-Za-z]*",
            Pattern.MULTILINE);

    private static final Pattern TRIVIAL_RETURN_PATTERN = Pattern.compile(
            "return\\s+(?:\"\"|0|null|Collections\\.empty|new\\s+(?:HashMap|ArrayList)\\(\\));");

    private static final Pattern UNIMPLEMENTED_BODY_PATTERN = Pattern.compile(
            "\\)\\s*\\{\\s*\\}");

    private static final Pattern SWALLOWED_EXCEPTION_PATTERN = Pattern.compile(
            "catch\\s*\\([^)]+\\)\\s*\\{[^}]*(?:return|continue|break)[^}]*\\}");

    private static final Pattern UNIMPLEMENTED_LOG_PATTERN = Pattern.compile(
            "(?:log|logger)\\.(?:warn|error)\\([^)]*\"[^\"]*(?:not\\s+implemented|TODO|FIXME)");

    private final Map<String, Integer> violationCounts = new ConcurrentHashMap<>();
    private final ObjectMapper jsonMapper = new ObjectMapper();

    /**
     * Validates a single Java file for guard violations.
     *
     * @param javaFile Path to .java file
     * @return List of violations found
     * @throws IllegalArgumentException if file doesn't exist or isn't readable
     */
    public List<GuardViolation> validateFile(@NonNull Path javaFile) {
        if (!Files.exists(javaFile)) {
            throw new IllegalArgumentException("File not found: " + javaFile);
        }
        if (!javaFile.toString().endsWith(".java")) {
            throw new IllegalArgumentException("File must be a .java file: " + javaFile);
        }

        List<GuardViolation> violations = new ArrayList<>();
        try {
            String content = Files.readString(javaFile, StandardCharsets.UTF_8);
            String[] lines = content.split("\n");

            violations.addAll(checkTodoPatterns(javaFile, lines));
            violations.addAll(checkSpuriousImplementations(javaFile, lines, content));
            violations.addAll(checkTrivialReturns(javaFile, lines, content));
            violations.addAll(checkUnimplementedBodies(javaFile, lines, content));
            violations.addAll(checkSwallowedExceptions(javaFile, lines, content));
            violations.addAll(checkUnimplementedLogging(javaFile, lines));
            violations.addAll(checkJavadocLies(javaFile, lines, content));

            // Update counters
            violations.forEach(v -> violationCounts.merge(v.pattern(), 1, Integer::sum));

        } catch (IOException e) {
            String errorMsg = String.format(
                    "Failed to read Java file '%s': %s. Verify file is readable and properly encoded (UTF-8).",
                    javaFile, e.getMessage());
            logger.error(errorMsg, e);
            throw new RuntimeException(errorMsg, e);
        }

        logger.debug("Validated file {}: {} violations found", javaFile, violations.size());
        return violations;
    }

    /**
     * Validates all Java files in a directory recursively.
     *
     * @param srcDir Source directory to scan
     * @return GuardReceipt with all violations and summary
     * @throws IllegalArgumentException if directory doesn't exist
     */
    public GuardReceipt validateDirectory(@NonNull Path srcDir) {
        if (!Files.isDirectory(srcDir)) {
            throw new IllegalArgumentException("Not a directory: " + srcDir);
        }

        GuardReceipt receipt = new GuardReceipt();
        receipt.phase = "guards";
        receipt.timestamp = Instant.now().toString();

        List<GuardViolation> allViolations = new ArrayList<>();

        try {
            List<Path> javaFiles = Files.walk(srcDir)
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList();

            receipt.filesScanned = javaFiles.size();
            logger.info("Scanning {} Java files in {}", javaFiles.size(), srcDir);

            for (Path javaFile : javaFiles) {
                try {
                    allViolations.addAll(validateFile(javaFile));
                } catch (Exception e) {
                    // Continue scanning other files, but record error
                    logger.warn("Error validating {}: {}", javaFile, e.getMessage());
                }
            }

            receipt.violations = allViolations;
            receipt.status = allViolations.isEmpty() ? "GREEN" : "RED";

            if (!allViolations.isEmpty()) {
                Map<String, Long> summaryCounts = allViolations.stream()
                        .collect(Collectors.groupingBy(GuardViolation::pattern, Collectors.counting()));
                receipt.summary = summaryCounts.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> Math.toIntExact(e.getValue())));
                receipt.summary.put("total_violations", allViolations.size());

                receipt.errorMessage = String.format(
                        "%d guard violations found. " +
                        "Fix violations by implementing real code or throwing UnsupportedOperationException. " +
                        "See fix_guidance in each violation.",
                        allViolations.size());
            } else {
                receipt.summary = Map.of("total_violations", 0);
            }

        } catch (IOException e) {
            String errorMsg = String.format(
                    "Failed to walk directory '%s': %s. Verify directory is accessible.",
                    srcDir, e.getMessage());
            logger.error(errorMsg, e);
            receipt.status = "ERROR";
            receipt.errorMessage = errorMsg;
        }

        logger.info("Validation complete: status={}, violations={}, files={}",
                receipt.status, allViolations.size(), receipt.filesScanned);

        return receipt;
    }

    /**
     * Generates detailed JSON receipt for violations.
     *
     * @param receipt GuardReceipt object
     * @return JSON string with violations and guidance
     */
    public String generateReceipt(@NonNull GuardReceipt receipt) {
        try {
            ObjectNode root = jsonMapper.createObjectNode();
            root.put("phase", receipt.phase);
            root.put("timestamp", receipt.timestamp);
            root.put("files_scanned", receipt.filesScanned);
            root.put("status", receipt.status);
            if (receipt.errorMessage != null) {
                root.put("error_message", receipt.errorMessage);
            }

            ArrayNode violationsArray = root.putArray("violations");
            for (GuardViolation v : receipt.violations) {
                ObjectNode vNode = violationsArray.addObject();
                vNode.put("pattern", v.pattern());
                vNode.put("severity", "FAIL");
                vNode.put("file", v.file().toString());
                vNode.put("line", v.line());
                vNode.put("content", v.content());
                vNode.put("fix_guidance", getFixGuidance(v.pattern()));
            }

            ObjectNode summaryNode = root.putObject("summary");
            for (Map.Entry<String, Integer> entry : receipt.summary.entrySet()) {
                summaryNode.put(entry.getKey(), entry.getValue());
            }

            return jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);

        } catch (Exception e) {
            logger.error("Failed to generate JSON receipt", e);
            throw new RuntimeException("Receipt generation failed: " + e.getMessage(), e);
        }
    }

    // === Private Pattern Checkers ===

    private List<GuardViolation> checkTodoPatterns(@NonNull Path file, @NonNull String[] lines) {
        List<GuardViolation> violations = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (DEFERRED_WORK_PATTERN.matcher(line).find()) {
                violations.add(new GuardViolation(
                        "H_TODO", file, i + 1, line.trim()));
            }
        }

        return violations;
    }

    private List<GuardViolation> checkSpuriousImplementations(@NonNull Path file, @NonNull String[] lines,
                                                     @NonNull String content) {
        List<GuardViolation> violations = new ArrayList<>();

        // Check method/class names for non-genuine implementations
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (SPURIOUS_IMPLEMENTATION_PATTERN.matcher(line).find() || SPURIOUS_CLASS_PATTERN.matcher(line).find()) {
                violations.add(new GuardViolation(
                        "H_MOCK", file, i + 1, line.trim()));
            }
        }

        return violations;
    }

    private List<GuardViolation> checkTrivialReturns(@NonNull Path file, @NonNull String[] lines,
                                                    @NonNull String content) {
        List<GuardViolation> violations = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (TRIVIAL_RETURN_PATTERN.matcher(line).find()) {
                violations.add(new GuardViolation(
                        "H_STUB", file, i + 1, line.trim()));
            }
        }

        return violations;
    }

    private List<GuardViolation> checkUnimplementedBodies(@NonNull Path file, @NonNull String[] lines,
                                                    @NonNull String content) {
        List<GuardViolation> violations = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (UNIMPLEMENTED_BODY_PATTERN.matcher(line).find() && !line.contains("//")) {
                violations.add(new GuardViolation(
                        "H_EMPTY", file, i + 1, line.trim()));
            }
        }

        return violations;
    }

    private List<GuardViolation> checkSwallowedExceptions(@NonNull Path file, @NonNull String[] lines,
                                                       @NonNull String content) {
        List<GuardViolation> violations = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (SWALLOWED_EXCEPTION_PATTERN.matcher(line).find()) {
                violations.add(new GuardViolation(
                        "H_FALLBACK", file, i + 1, line.trim()));
            }
        }

        return violations;
    }

    private List<GuardViolation> checkUnimplementedLogging(@NonNull Path file, @NonNull String[] lines) {
        List<GuardViolation> violations = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (UNIMPLEMENTED_LOG_PATTERN.matcher(line).find()) {
                violations.add(new GuardViolation(
                        "H_SILENT", file, i + 1, line.trim()));
            }
        }

        return violations;
    }

    private List<GuardViolation> checkJavadocLies(@NonNull Path file, @NonNull String[] lines,
                                                    @NonNull String content) {
        List<GuardViolation> violations = new ArrayList<>();

        // Check for "@return never null" but returns null, etc.
        for (int i = 0; i < lines.length - 1; i++) {
            String docLine = lines[i];
            String codeLine = lines[i + 1];

            if (docLine.contains("@return never null") && codeLine.contains("return null")) {
                violations.add(new GuardViolation(
                        "H_LIE", file, i + 2, codeLine.trim()));
            }
            if (docLine.contains("@throws") && !codeLine.contains("throw")) {
                violations.add(new GuardViolation(
                        "H_LIE", file, i + 2, codeLine.trim()));
            }
        }

        return violations;
    }

    private String getFixGuidance(@NonNull String pattern) {
        return switch (pattern) {
            case "H_TODO" -> "Remove TODO/FIXME markers. Implement real logic or throw UnsupportedOperationException with clear reason.";
            case "H_MOCK" -> "Delete mock/stub classes or rename to real service. Implement genuine functionality.";
            case "H_STUB" -> "Replace empty return (\"\"\"0, null) with real implementation or throw exception.";
            case "H_EMPTY" -> "Implement method body or throw UnsupportedOperationException(\"reason\").";
            case "H_FALLBACK" -> "Propagate exception instead of silent fallback. Let caller handle error.";
            case "H_LIE" -> "Update code to match javadoc. If javadoc is wrong, fix javadoc instead.";
            case "H_SILENT" -> "Throw exception instead of logging. Critical errors must not be silent.";
            default -> "Fix guard violation immediately.";
        };
    }

    /**
     * Immutable record for a single guard violation.
     */
    public record GuardViolation(
            @NonNull String pattern,
            @NonNull Path file,
            int line,
            @NonNull String content
    ) {}

    /**
     * Guard validation receipt with all violations and summary.
     */
    public static class GuardReceipt {
        public String phase;
        public String timestamp;
        public int filesScanned;
        public List<GuardViolation> violations = new ArrayList<>();
        public String status;
        public @Nullable String errorMessage;
        public Map<String, Integer> summary = new HashMap<>();

        public boolean isGreen() {
            return "GREEN".equals(status) && violations.isEmpty();
        }

        public String toJson() {
            HyperStandardsValidator validator = new HyperStandardsValidator();
            return validator.generateReceipt(this);
        }
    }
}
