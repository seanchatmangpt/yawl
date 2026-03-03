package org.yawlfoundation.yawl.ggen.validation;

import org.yawlfoundation.yawl.ggen.validation.model.GuardReceipt;
import org.yawlfoundation.yawl.ggen.validation.model.GuardSummary;
import org.yawlfoundation.yawl.ggen.validation.model.GuardViolation;
import org.yawlfoundation.yawl.ggen.validation.Severity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * HyperStandardsValidator - Orchestrator for guard pattern validation.
 *
 * This validator implements the H (Guards) phase of the YAWL validation pipeline,
 * enforcing Fortune 5 production standards by detecting and blocking 7+ forbidden
 * patterns in generated code.
 *
 * <p><b>Guard Patterns Enforced:</b></p>
 * <ul>
 *   <li><b>H_TODO:</b> Deferred work markers (TODO, FIXME, etc.)</li>
 *   <li><b>H_MOCK:</b> Mock implementations (class/method names)</li>
 *   <li><b>H_STUB:</b> Empty/placeholder returns</li>
 *   <li><b>H_EMPTY:</b> No-op method bodies</li>
 *   <li><b>H_FALLBACK:</b> Silent degradation catch blocks</li>
 *   <li><b>H_LIE:</b> Code ≠ documentation</li>
 *   <li><b>H_SILENT:</b> Log instead of throw</li>
 *   <li><b>H_PRINT_DEBUG:</b> System.out/err.println</li>
 *   <li><b>H_SWALLOWED:</b> Empty catch blocks</li>
 * </ul>
 *
 * <p><b>Exit Codes:</b></p>
 * <ul>
 *   <li>0: No violations - proceed to next phase</li>
 *   <li>1: Transient error (IO, parse) - retry</li>
 *   <li>2: Guard violations found - fix and re-run</li>
 * </ul>
 */
public class HyperStandardsValidator {

    private final List<GuardChecker> checkers;
    private GuardReceipt receipt;

    /**
     * Constructs a new HyperStandardsValidator with all guard checkers initialized.
     */
    public HyperStandardsValidator() {
        this.checkers = initializeGuardCheckers();
    }

    /**
     * Initializes all guard checkers (7 core + 2 extended patterns).
     */
    private List<GuardChecker> initializeGuardCheckers() {
        List<GuardChecker> checkers = new ArrayList<>();

        // Core guard patterns (7)
        checkers.add(RegexGuardChecker.Factory.createTodoChecker());
        checkers.add(RegexGuardChecker.Factory.createPatternChecker());
        checkers.add(new SparqlGuardChecker("H_STUB", SparqlGuardChecker.QueryFactory.createStubReturnQuery().toString()));
        checkers.add(new SparqlGuardChecker("H_EMPTY", SparqlGuardChecker.QueryFactory.createEmptyQuery().toString()));
        checkers.add(new SparqlGuardChecker("H_FALLBACK", SparqlGuardChecker.QueryFactory.createFallbackQuery().toString()));
        checkers.add(new SparqlGuardChecker("H_LIE", SparqlGuardChecker.QueryFactory.createLieQuery().toString()));
        checkers.add(RegexGuardChecker.Factory.createSilentChecker());

        // Extended guard patterns (2)
        checkers.add(new RegexGuardChecker("H_PRINT_DEBUG",
            "System\\.(out|err)\\.print(ln)?\\("));
        checkers.add(new RegexGuardChecker("H_SWALLOWED",
            "}\\s*catch\\s*\\([^)]+\\)\\s*\\{\\s*\\}"));

        return checkers;
    }

    /**
     * Validates all Java files in the specified emit directory.
     *
     * @param emitDir Directory containing generated Java files to validate
     * @return GuardReceipt containing validation results
     * @throws IOException if directory scanning fails
     */
    public GuardReceipt validateEmitDir(Path emitDir) throws IOException {
        receipt = new GuardReceipt();
        receipt.setFilesScanned(0);
        receipt.setViolations(new ArrayList<>());

        // Scan for Java files
        List<Path> javaFiles = findJavaFiles(emitDir);
        receipt.setFilesScanned(javaFiles.size());

        // Validate each Java file
        for (Path javaFile : javaFiles) {
            validateFile(javaFile);
        }

        // Generate error message for violations
        if (receipt.isRed()) {
            receipt.setErrorMessage(String.format(
                "%d guard violations found. Fix violations or throw UnsupportedOperationException.",
                receipt.getViolations().size()
            ));
        }

        // Write receipt to JSON file
        writeReceiptToFile();

        return receipt;
    }

    /**
     * Helper method to find all Java files in a directory (recursive).
     */
    private List<Path> findJavaFiles(Path directory) throws IOException {
        return Files.walk(directory)
            .filter(Files::isRegularFile)
            .filter(p -> p.toString().endsWith(".java"))
            .collect(Collectors.toList());
    }

    /**
     * Validates a single Java file using all guard checkers.
     *
     * @param javaFile Java file to validate
     * @throws IOException if file cannot be read
     */
    private void validateFile(Path javaFile) throws IOException {
        for (GuardChecker checker : checkers) {
            try {
                List<GuardViolation> violations = checker.check(javaFile);
                for (GuardViolation violation : violations) {
                    // Create a new violation with the file path set
                    GuardViolation violationWithFile = new GuardViolation(
                        violation.getPattern(),
                        violation.getSeverity(),
                        javaFile.toString(),
                        violation.getLine(),
                        violation.getContent()
                    );
                    receipt.addViolation(violationWithFile);
                }
            } catch (IOException e) {
                // Log transient error but continue with other checkers
                System.err.println("Error checking file " + javaFile + " with " +
                    checker.patternName() + ": " + e.getMessage());

                // Add violation for transient error
                GuardViolation errorViolation = new GuardViolation(
                    "H_ERROR",
                    Severity.FAIL,
                    javaFile.toString(),
                    0,
                    "IO error while checking: " + e.getMessage()
                );
                receipt.addViolation(errorViolation);
            } catch (Exception e) {
                // Handle unexpected errors gracefully
                System.err.println("Unexpected error checking file " + javaFile + " with " +
                    checker.patternName() + ": " + e.getMessage());

                // Add violation for unexpected error
                GuardViolation errorViolation = new GuardViolation(
                    "H_ERROR",
                    Severity.FAIL,
                    javaFile.toString(),
                    0,
                    "Unexpected error: " + e.getMessage()
                );
                receipt.addViolation(errorViolation);
            }
        }
    }

    /**
     * Writes the validation receipt to a JSON file.
     */
    private void writeReceiptToFile() throws IOException {
        // Create receipts directory if it doesn't exist
        Path receiptsDir = Paths.get(".claude/receipts");
        Files.createDirectories(receiptsDir);

        // Write receipt to JSON file
        Path receiptFile = receiptsDir.resolve("guard-receipt.json");
        String json = receiptToJson();
        Files.writeString(receiptFile, json);

        System.out.println("Guard validation receipt written to: " + receiptFile);
    }

    /**
     * Converts the receipt to JSON string for output.
     */
    private String receiptToJson() {
        // Simple JSON serialization - in production, use Jackson/ObjectMapper
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"phase\": \"guards\",\n");
        json.append("  \"timestamp\": \"").append(receipt.getTimestamp()).append("\",\n");
        json.append("  \"files_scanned\": ").append(receipt.getFilesScanned()).append(",\n");
        json.append("  \"status\": \"").append(receipt.getStatus()).append("\",\n");

        if (receipt.isRed()) {
            json.append("  \"error_message\": \"").append(receipt.getErrorMessage()).append("\",\n");
        }

        json.append("  \"violations\": [\n");

        List<GuardViolation> violations = receipt.getViolations();
        for (int i = 0; i < violations.size(); i++) {
            GuardViolation v = violations.get(i);
            json.append("    {\n");
            json.append("      \"pattern\": \"").append(v.getPattern()).append("\",\n");
            json.append("      \"severity\": \"").append(v.getSeverity()).append("\",\n");
            json.append("      \"file\": \"").append(escapeJson(v.getFile())).append("\",\n");
            json.append("      \"line\": ").append(v.getLine()).append(",\n");
            json.append("      \"content\": \"").append(escapeJson(v.getContent())).append("\",\n");
            json.append("      \"fix_guidance\": \"").append(escapeJson(v.getFixGuidance())).append("\"\n");
            json.append("    }");
            if (i < violations.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }

        json.append("  ],\n");

        // Add summary
        GuardSummary summary = receipt.getSummary();
        if (summary != null) {
            json.append("  \"summary\": {\n");
            json.append("    \"h_todo_count\": ").append(summary.getH_todo_count()).append(",\n");
            json.append("    \"h_mock_count\": ").append(summary.getH_mock_violation_count()).append(",\n");
            json.append("    \"h_stub_count\": ").append(summary.getH_stub_violation_count()).append(",\n");
            json.append("    \"h_empty_count\": ").append(summary.getH_empty_count()).append(",\n");
            json.append("    \"h_fallback_count\": ").append(summary.getH_fallback_count()).append(",\n");
            json.append("    \"h_lie_count\": ").append(summary.getH_lie_count()).append(",\n");
            json.append("    \"h_silent_count\": ").append(summary.getH_silent_count()).append(",\n");
            json.append("    \"total_violations\": ").append(summary.getTotal_violations()).append("\n");
            json.append("  },\n");
        }

        json.append("  \"exit_code\": ").append(getExitCode()).append("\n");
        json.append("}");

        return json.toString();
    }

    /**
     * Escapes special characters in JSON strings.
     */
    private String escapeJson(String input) {
        if (input == null) {
            throw new UnsupportedOperationException(
                "escapeJson requires non-null input. " +
                "Real validation cannot proceed with null input."
            );
        }
        return input.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    /**
     * Determines the appropriate exit code based on validation results.
     *
     * @return 0 for success, 1 for transient error, 2 for violations
     */
    public int getExitCode() {
        if (receipt.isGreen()) {
            return 0; // Success - proceed to next phase
        } else if (hasTransientErrors()) {
            return 1; // Transient error - retry
        } else {
            return 2; // Violations found - fix and re-run
        }
    }

    /**
     * Checks if receipt contains only transient errors (H_ERROR patterns).
     */
    private boolean hasTransientErrors() {
        return receipt.getViolations().stream()
            .allMatch(v -> "H_ERROR".equals(v.getPattern()));
    }

    /**
     * Gets the list of registered guard checkers.
     */
    public List<GuardChecker> getCheckers() {
        return new ArrayList<>(checkers);
    }

    /**
     * Adds a custom guard checker to the validator.
     */
    public void addChecker(GuardChecker checker) {
        checkers.add(checker);
    }

    /**
     * Validates using the legacy validateEmitDir method for backward compatibility.
     *
     * @deprecated Use {@link #validateEmitDir(Path)} instead.
     */
    @Deprecated
    public GuardReceipt validateEmitDir(String emitDirPath) throws IOException {
        return validateEmitDir(Paths.get(emitDirPath));
    }
}