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
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Orchestrates guard validation across 9 guard patterns (7 core + 2 blue-ocean extensions).
 * Coordinates multiple GuardChecker implementations to detect violations
 * and produce a GuardReceipt for audit and debugging.
 * File scanning runs concurrently on Java 25 VirtualThreads for O(1) wall-clock
 * scalability regardless of codebase size.
 *
 * <p><b>Core patterns (7)</b> — original hyper-standards gate:
 * <ul>
 *   <li>H_TODO: Deferred work markers (TODO, FIXME, XXX, HACK, …)</li>
 *   <li>H_MOCK: Mock implementations (class/method names, fake data)</li>
 *   <li>H_STUB: Empty/placeholder returns from non-void methods</li>
 *   <li>H_EMPTY: Empty void method bodies</li>
 *   <li>H_FALLBACK: Silent catch-and-fake error handling</li>
 *   <li>H_LIE: Documentation mismatches (code ≠ javadoc)</li>
 *   <li>H_SILENT: Log instead of throw exception</li>
 * </ul>
 *
 * <p><b>Blue-ocean extensions (2)</b> — production hardening:
 * <ul>
 *   <li>H_PRINT_DEBUG: {@code System.out/err.print*()} calls that must never ship</li>
 *   <li>H_SWALLOWED: Empty catch blocks that silently discard exceptions</li>
 * </ul>
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
     * Create a new HyperStandardsValidator with all 9 guard checkers registered.
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
     * Register all 9 guard checkers (7 core + 2 blue-ocean extensions).
     * Uses regex checkers for simple patterns and SPARQL checkers for complex ones.
     *
     * <p><b>Blue-ocean extensions</b>:
     * <ul>
     *   <li>H_PRINT_DEBUG — {@code System.out/err.print*()} catches debug artifacts that
     *       slip through code review into generated/production code. No other code-generation
     *       quality gate targets this pattern specifically.</li>
     *   <li>H_SWALLOWED — empty catch blocks silently discard all exception information.
     *       Detected via whole-file regex to cover both single-line and multi-line forms.</li>
     * </ul>
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

        // --- Blue-ocean extension #1: H_PRINT_DEBUG ---
        // System.out.println / System.err.println / System.out.printf left in generated code
        // are a production reliability hazard: they bypass logging frameworks, can expose
        // sensitive data in stdout, and signal incomplete implementation. No competitor
        // code-generation gate blocks this pattern.
        checkers.add(new RegexGuardChecker(
            "H_PRINT_DEBUG",
            "System\\.(out|err)\\.(print|println|printf)\\(",
            GuardChecker.Severity.FAIL
        ));

        // --- Blue-ocean extension #2: H_SWALLOWED ---
        // Empty catch blocks silently discard all exception state. Unlike H_FALLBACK (which
        // requires fake data to be returned), H_SWALLOWED catches the worst case: an exception
        // is caught and literally nothing happens. Uses WholeFileRegexGuardChecker so both
        // single-line ("catch (E e) { }") and multi-line forms are detected.
        checkers.add(new WholeFileRegexGuardChecker(
            "H_SWALLOWED",
            "catch\\s*\\([^)]+\\)\\s*\\{\\s*\\}",
            GuardChecker.Severity.FAIL
        ));

        LOGGER.info("Registered {} guard checkers", checkers.size());
    }

    /**
     * Check if a path matches a glob pattern using Java's built-in PathMatcher.
     * Supports the full glob syntax: ** for recursive matching, * for single segment,
     * ? for single character.
     *
     * @param relativePath the file path relative to the emit directory (forward slashes)
     * @param pattern the glob pattern (e.g. "&#42;&#42;/target/&#42;&#42;", "&#42;&#42;/&#42;Test.java")
     * @return true if the path matches the pattern
     */
    private boolean matchesPattern(String relativePath, String pattern) {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        return matcher.matches(Path.of(relativePath));
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
     * File scanning runs concurrently on Java 25 VirtualThreads — one thread per file —
     * so wall-clock time scales with the slowest file rather than total file count.
     * All checker logic is read-only; violations are collected per-file and merged
     * into the receipt on the calling thread after all futures complete.
     *
     * @param emitDir the directory containing generated Java source files
     * @return a GuardReceipt with validation results
     * @throws IOException if directory access or file reading fails
     */
    public GuardReceipt validateEmitDir(Path emitDir) throws IOException {
        Objects.requireNonNull(emitDir, "emitDir must not be null");

        if (!Files.isDirectory(emitDir)) {
            throw new IOException("emitDir is not a directory: " + emitDir);
        }

        receipt = new GuardReceipt();
        receipt.setPhase("guards");

      // Collect all Java source files up-front
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
        LOGGER.info("Scanning {} Java files in {} (VirtualThread parallel)", javaFiles.size(), emitDir);

        // Submit each file as an independent VirtualThread task.
        // collectViolationsForFile() is pure (no shared mutable state), so concurrent
        // execution is safe. We merge results sequentially after all futures resolve.
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<List<GuardViolation>>> futures = javaFiles.stream()
                .map(javaFile -> executor.submit(() -> collectViolationsForFile(javaFile)))
                .toList();

            for (Future<List<GuardViolation>> future : futures) {
                try {
                    for (GuardViolation violation : future.get()) {
                        receipt.addViolation(violation);
                    }
                } catch (ExecutionException e) {
                    LOGGER.warn("VirtualThread task failed: {}", e.getCause().getMessage());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Guard validation interrupted", e);
                }
            }
        }

        // Finalize status and error message
        receipt.finalizeStatus();

        LOGGER.info("Guard validation complete: {} violation(s) across {} file(s) — {}",
                   receipt.getViolations().size(), javaFiles.size(),
                   severityBand(receipt.getViolations().size()));

        return receipt;
    }

    /**
     * Run all registered checkers against a single Java file and return all violations.
     * This method is pure (no side effects on shared state) and safe for concurrent use.
     *
     * @param javaFile the Java source file to check
     * @return list of violations found (empty if file is clean)
     */
    private List<GuardViolation> collectViolationsForFile(Path javaFile) {
        List<GuardViolation> fileViolations = new ArrayList<>();
        for (GuardChecker checker : checkers) {
            try {
                List<GuardViolation> violations = checker.check(javaFile);
                for (GuardViolation violation : violations) {
                    violation.setFile(javaFile.toString());
                    LOGGER.debug("Found violation: {} at {}:{}", violation.getPattern(), javaFile, violation.getLine());
                }
                fileViolations.addAll(violations);
            } catch (IOException e) {
                LOGGER.warn("Failed to check {} with {}: {}", javaFile, checker.patternName(), e.getMessage());
            }
        }
        return fileViolations;
    }

    /**
     * Classify a violation count into a coarse severity label.
     *
     * <p><b>JEP 455 — Primitive Types in Patterns (Java 25 preview)</b>:
     * {@code switch (int)} with {@code case int n when n == 0} uses primitive type patterns
     * to avoid boxing and enables exhaustive checking. The compiler verifies all int values
     * are covered by the final unguarded {@code case int n}.
     *
     * @param count violation count (non-negative)
     * @return "GREEN", "YELLOW", or "RED"
     */
    @SuppressWarnings("preview")
    private static String severityBand(int count) {
        return switch (count) {
            case int n when n == 0 -> "GREEN";
            case int n when n < 10 -> "YELLOW";
            case int n             -> "RED";
        };
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
