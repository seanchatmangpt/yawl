/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.ggen.validation.model.GuardReceipt;
import org.yawlfoundation.yawl.ggen.validation.model.GuardViolation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Orchestrates guard validation across all 7 guard patterns.
 * Coordinates multiple GuardChecker implementations to detect violations
 * and produce a GuardReceipt for audit and debugging.
 *
 * Guard patterns validated:
 * - H_TODO: Deferred work markers (TODO, FIXME, XXX, etc.)
 * - H_MOCK: Mock implementations (class/method names, fake data)
 * - H_STUB: Empty/placeholder returns from non-void methods
 * - H_EMPTY: Empty void method bodies
 * - H_FALLBACK: Silent catch-and-fake error handling
 * - H_LIE: Documentation mismatches (code ≠ javadoc)
 * - H_SILENT: Log instead of throw exception
 *
 * Exit codes:
 * - 0 (GREEN): No violations, safe to proceed to next phase
 * - 2 (RED): Violations found, developer must fix or throw UnsupportedOperationException
 */
public class HyperStandardsValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(HyperStandardsValidator.class);

    private final List<GuardChecker> checkers;
    private final List<String> exclusionPatterns;
    private GuardReceipt receipt;

    // Default exclusion patterns
    private static final List<String> DEFAULT_EXCLUSIONS = List.of(
        "**/test/fixtures/**",
        "**/fixtures/**",
        "**/*fixture*/**",
        "**/src/test/**",
        "**/*Test.java",
        "**/*Tests.java",
        "**/target/**",
        "**/build/**",
        "**/node_modules/**"
    );

    /**
     * Create a new HyperStandardsValidator with all 7 guard checkers registered.
     * Uses default exclusion patterns.
     */
    public HyperStandardsValidator() {
        this.checkers = new ArrayList<>();
        this.exclusionPatterns = DEFAULT_EXCLUSIONS;
        registerDefaultCheckers();
    }

    /**
     * Create a new HyperStandardsValidator with custom checkers and exclusion patterns.
     * Useful for testing or custom guard pattern implementations.
     *
     * @param customCheckers list of GuardChecker implementations to use
     * @param exclusionPatterns list of glob patterns to exclude from validation
     */
    public HyperStandardsValidator(List<GuardChecker> customCheckers, List<String> exclusionPatterns) {
        this.checkers = Objects.requireNonNull(customCheckers, "customCheckers must not be null");
        this.exclusionPatterns = Objects.requireNonNull(exclusionPatterns, "exclusionPatterns must not be null");
        registerDefaultCheckers();
    }

    /**
     * Create a new HyperStandardsValidator with custom checkers.
     * Useful for testing or custom guard pattern implementations.
     *
     * @param customCheckers list of GuardChecker implementations to use
     */
    public HyperStandardsValidator(List<GuardChecker> customCheckers) {
        this.checkers = Objects.requireNonNull(customCheckers, "customCheckers must not be null");
        this.exclusionPatterns = DEFAULT_EXCLUSIONS;
    }

    /**
     * Register all 7 guard checkers with their patterns and severity levels.
     * Uses regex checkers for simple patterns and SPARQL checkers for complex ones.
     */
    private void registerDefaultCheckers() {
        // H_TODO: Regex-based detection of deferred work markers
        checkers.add(new RegexGuardChecker(
            "H_TODO",
            "//\\s*(TODO|FIXME|XXX|HACK|LATER|FUTURE|@incomplete|@stub|placeholder)",
            GuardChecker.Severity.FAIL
        ));

        // H_MOCK: Regex-based detection of forbidden class/method name prefixes.
        // (?i) flag catches both PascalCase (class names) and camelCase (method names).
        checkers.add(new RegexGuardChecker(
            "H_MOCK",
            "(?i)(mock|stub|fake|demo)[A-Z]\\w*",
            GuardChecker.Severity.FAIL
        ));

        // H_SILENT: Regex-based detection of logging instead of throwing.
        // (?i) flag catches phrases regardless of capitalisation ("Not implemented", "not implemented").
        checkers.add(new RegexGuardChecker(
            "H_SILENT",
            "(?i)log\\.(warn|error)\\([^)]*['\"].*not\\s+implemented",
            GuardChecker.Severity.FAIL
        ));

        // H_STUB: SPARQL-based detection of placeholder returns
        String placeholderReturnQuery = loadSparqlQuery("guards-h-stub.sparql");
        checkers.add(new SparqlGuardChecker(
            "H_STUB",
            placeholderReturnQuery,
            GuardChecker.Severity.FAIL
        ));

        // H_EMPTY: SPARQL-based detection of empty method bodies
        String emptyMethodBodyQuery = loadSparqlQuery("guards-h-empty.sparql");
        checkers.add(new SparqlGuardChecker(
            "H_EMPTY",
            emptyMethodBodyQuery,
            GuardChecker.Severity.FAIL
        ));

        // H_FALLBACK: SPARQL-based detection of silent error handling
        String silentErrorHandlingQuery = loadSparqlQuery("guards-h-fallback.sparql");
        checkers.add(new SparqlGuardChecker(
            "H_FALLBACK",
            silentErrorHandlingQuery,
            GuardChecker.Severity.FAIL
        ));

        // H_LIE: SPARQL-based detection of documentation mismatches
        String documentationMismatchQuery = loadSparqlQuery("guards-h-lie.sparql");
        checkers.add(new SparqlGuardChecker(
            "H_LIE",
            documentationMismatchQuery,
            GuardChecker.Severity.FAIL
        ));

        LOGGER.info("Registered {} guard checkers", checkers.size());
    }

    /**
     * Check if a path matches a glob pattern.
     * Supports ** for recursive matching, * for single segment, ? for single character.
     *
     * @param path the file path to check
     * @param pattern the glob pattern
     * @return true if the path matches the pattern
     */
    private boolean matchesPattern(String path, String pattern) {
        // Convert glob pattern to regex
        String regex = pattern.replace("**/", ".*")
                            .replace("/**", "(?:/.*)?")
                            .replace("*", "[^/]*")
                            .replace("?", ".");

        // Handle ** at the end
        if (pattern.endsWith("**")) {
            regex = ".*";
        }

        // Compile and match
        return path.matches(regex);
    }

    /**
     * Load a SPARQL query from classpath resources.
     * Throws exception if resource not found.
     *
     * @param filename the filename in src/main/resources/sparql/
     * @return the SPARQL query string
     * @throws IllegalStateException if resource not found
     */
    private String loadSparqlQuery(String filename) {
        try (InputStream is = getClass().getResourceAsStream("/sparql/" + filename)) {
            if (is == null) {
                throw new IllegalStateException(
                    "Required SPARQL query resource not found: " + filename +
                    " — verify src/main/resources/sparql/ contains all query files"
                );
            }
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(
                "Failed to load SPARQL query " + filename + ": " + e.getMessage(), e
            );
        }
    }

    /**
     * Validate all Java source files in the emit directory.
     * Scans for .java files and runs all registered guards.
     *
     * @param emitDir the directory containing generated Java source files
     * @return a GuardReceipt with validation results
     * @throws IOException if directory access fails
     */
    public GuardReceipt validateEmitDir(Path emitDir) throws IOException {
        Objects.requireNonNull(emitDir, "emitDir must not be null");

        if (!Files.isDirectory(emitDir)) {
            throw new IOException("emitDir is not a directory: " + emitDir);
        }

        receipt = new GuardReceipt();
        receipt.setPhase("guards");

        // Find all Java source files, excluding test fixtures and other patterns
        List<Path> javaFiles = new ArrayList<>();
        try (var stream = Files.walk(emitDir)) {
            stream.filter(p -> {
                String path = p.toString();

                // Include only .java files
                if (!path.endsWith(".java")) {
                    return false;
                }

                // Convert to relative path for pattern matching
                String relativePath = emitDir.relativize(p).toString().replace('\\', '/');

                // Check against exclusion patterns
                for (String pattern : exclusionPatterns) {
                    if (matchesPattern(relativePath, pattern)) {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("Excluding file: {} (matches pattern: {})", path, pattern);
                        }
                        return false;
                    }
                }

                return true;
            })
            .forEach(javaFiles::add);
        }

        receipt.setFilesScanned(javaFiles.size());
        LOGGER.info("Scanning {} Java files in {}", javaFiles.size(), emitDir);

        // Validate each file
        for (Path javaFile : javaFiles) {
            validateFile(javaFile);
        }

        // Finalize status and error message
        receipt.finalizeStatus();

        LOGGER.info("Guard validation complete: {} violations found",
                   receipt.getViolations().size());

        return receipt;
    }

    /**
     * Validate a single Java file using all registered checkers.
     *
     * @param javaFile the path to the Java file
     */
    private void validateFile(Path javaFile) {
        for (GuardChecker checker : checkers) {
            try {
                List<GuardViolation> violations = checker.check(javaFile);
                for (GuardViolation violation : violations) {
                    violation.setFile(javaFile.toString());
                    receipt.addViolation(violation);
                    LOGGER.debug("Found violation: {} at {}:{}",
                                violation.getPattern(), javaFile, violation.getLine());
                }
            } catch (IOException e) {
                LOGGER.warn("Failed to check {} with {}: {}",
                           javaFile, checker.patternName(), e.getMessage());
            }
        }
    }

    /**
     * Get the current guard receipt.
     * Valid after calling validateEmitDir().
     *
     * @return the GuardReceipt, or null if validation hasn't run
     */
    public GuardReceipt getReceipt() {
        return receipt;
    }

    /**
     * Get the list of registered checkers.
     *
     * @return an unmodifiable list of GuardChecker instances
     */
    public List<GuardChecker> getCheckers() {
        return List.copyOf(checkers);
    }

    /**
     * Get the exclusion patterns used for filtering files.
     *
     * @return an unmodifiable list of exclusion patterns
     */
    public List<String> getExclusionPatterns() {
        return List.copyOf(exclusionPatterns);
    }

    /**
     * Add an exclusion pattern dynamically.
     *
     * @param pattern the glob pattern to add
     */
    public void addExclusionPattern(String pattern) {
        Objects.requireNonNull(pattern, "pattern must not be null");
        if (!exclusionPatterns.contains(pattern)) {
            exclusionPatterns.add(pattern);
        }
    }

    /**
     * Remove an exclusion pattern.
     *
     * @param pattern the glob pattern to remove
     */
    public void removeExclusionPattern(String pattern) {
        exclusionPatterns.remove(Objects.requireNonNull(pattern, "pattern must not be null"));
    }

    /**
     * Add a custom guard checker (for testing or extension).
     *
     * @param checker the GuardChecker to add
     */
    public void addChecker(GuardChecker checker) {
        Objects.requireNonNull(checker, "checker must not be null");
        checkers.add(checker);
    }

    /**
     * Main entry point for command-line invocation.
     * Usage: java org.yawlfoundation.yawl.ggen.validation.HyperStandardsValidator <emitDir> [receiptFile]
     *
     * @param args [0] = emit directory path, [1] = optional receipt file output path
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: HyperStandardsValidator <emitDir> [receiptFile]");
            System.exit(1);
        }

        Path emitDir = Path.of(args[0]);
        Path receiptFile = args.length > 1 ? Path.of(args[1]) : null;

        try {
            HyperStandardsValidator validator = new HyperStandardsValidator();
            GuardReceipt receipt = validator.validateEmitDir(emitDir);

            // Output receipt to stdout or file
            String json = receipt.toJson();
            if (receiptFile != null) {
                Files.writeString(receiptFile, json);
                System.out.println("Receipt written to: " + receiptFile);
            } else {
                System.out.println(json);
            }

            // Exit with appropriate code
            System.exit(receipt.getExitCode());

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
