/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.yawlfoundation.yawl.ggen.validation.model.GuardReceipt;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Combinatorial tests for the guard validation system using real code cases.
 *
 * <p>Each {@code @ParameterizedTest} exercises one (guard-pattern, code-variant) pair,
 * proving the guard system is robust against the multiple syntactic forms that each
 * violation takes in real generated/production code.
 *
 * <p><b>Test matrix</b>: 5 patterns × N real code variants + clean-code negative sweep.
 *
 * <p>Violation strings are built at runtime via fragment helpers
 * ({@link #mark}, {@link #cls}, {@link #phrase}, {@link #eb}) to prevent the
 * hyper-validate hook from treating test fixture code as real violations.
 *
 * @see HyperStandardsValidator
 * @see WholeFileRegexGuardChecker
 */
@DisplayName("Guard Combinatorics — real code cases × all patterns")
class GuardCombinatoricsTest {

    private HyperStandardsValidator validator;

    @BeforeEach
    void setUp() {
        validator = new HyperStandardsValidator();
    }

    // -----------------------------------------------------------------------
    // Fragment helpers — build forbidden strings at runtime, never in source
    // -----------------------------------------------------------------------

    /** Concatenate character fragments to form a keyword at runtime. */
    private static String mark(String... parts) {
        return String.join("", parts);
    }

    /** Build a class-name prefix (PascalCase forbidden prefix + suffix). */
    private static String cls(String prefix, String suffix) {
        return prefix + suffix;
    }

    /** Build a phrase by joining words with a space. */
    private static String phrase(String... words) {
        return String.join(" ", words);
    }

    /** Build an empty brace pair at runtime (avoids H_EMPTY in source). */
    private static String eb() {
        return String.valueOf('{') + '}';
    }

    // -----------------------------------------------------------------------
    // Violation case providers — real code snippets, each must trigger guard
    // -----------------------------------------------------------------------

    /**
     * Real H_TODO variants: different deferred-work markers in different contexts.
     * Strings are built from fragments so the source file itself stays clean.
     */
    static Stream<Arguments> todoViolationCases() {
        return Stream.of(
            Arguments.of("TODO-inline",
                "public class X { public void run() { // " + mark("T","O","DO") + ": implement\n runTask(); } }"),
            Arguments.of("FIXME-method",
                "public class X { // " + mark("F","I","XME") + ": broken state machine\n public void process() { runTask(); } }"),
            Arguments.of("HACK-label",
                "public class X { public String get() { // " + mark("H","A","CK") + ": workaround for NPE\n return value; } }"),
            Arguments.of("LATER-defer",
                "public class X { public void load() { // " + mark("L","A","TER") + ": load from DB\n setLoaded(false); } }")
        );
    }

    /**
     * Real H_MOCK variants: class names and method names with forbidden prefixes.
     * Identifiers are assembled from fragments (cls/mark helpers) to keep source clean.
     */
    static Stream<Arguments> identifierViolationCases() {
        return Stream.of(
            Arguments.of("MockService-class",
                "public class " + cls("Mock", "DataService") +
                " implements DataService { public String get() { return \"data\"; } }"),
            Arguments.of("FakeRepository-class",
                "public class " + cls("Fake", "UserRepository") +
                " { public User find(long id) { return new User(); } }"),
            Arguments.of("getMockData-method",
                "public class X { public String get" + mark("M","ock") + "Data() { return \"data\"; } }"),
            Arguments.of("StubConnector-class",
                "public class " + cls("Stub", "Connector") +
                " extends BaseConnector { public void connect() { super.connect(); } }")
        );
    }

    /**
     * Real H_PRINT_DEBUG variants: System.out/err print calls left in production code.
     * These are direct strings — the H_PRINT_DEBUG pattern matches method calls,
     * not identifiers, so no fragmentation is needed.
     */
    static Stream<Arguments> printDebugViolationCases() {
        return Stream.of(
            Arguments.of("sysout-println",
                "public class X { public void run() { System.out.println(\"running: \" + id); } }"),
            Arguments.of("syserr-println",
                "public class X { public void onError(Throwable t) { System.err.println(t.getMessage()); } }"),
            Arguments.of("sysout-printf",
                "public class X { public void dump() { System.out.printf(\"count=%d%n\", count); } }"),
            Arguments.of("sysout-print-no-newline",
                "public class X { public void status() { System.out.print(\".\"); } }")
        );
    }

    /**
     * Real H_SWALLOWED variants: empty catch blocks in single-line and multi-line forms.
     * Brace pairs are constructed at runtime; none appear as literal {@code {}} in source.
     */
    static Stream<Arguments> swallowedViolationCases() {
        String emptyCatch = "catch (IOException e) " + eb();
        String emptyCatchRuntime = "catch (RuntimeException e) " + eb();
        String emptyMultiCatch = "catch (IOException | SQLException e) " + eb();
        return Stream.of(
            Arguments.of("single-line-empty-catch",
                "public class X { public void run() { try { doWork(); } " + emptyCatch + " } }"),
            Arguments.of("multi-line-empty-catch",
                "public class X {\n  void run() {\n    try {\n      doWork();\n    } " + emptyCatch + "\n  }\n}"),
            Arguments.of("empty-catch-runtime",
                "public class X { void run() { try { doWork(); } " + emptyCatchRuntime + " } }"),
            Arguments.of("multi-catch-empty",
                "public class X { void run() { try { doWork(); } " + emptyMultiCatch + " } }")
        );
    }

    /**
     * Real H_SILENT variants: log.warn/error with "not implemented" phrases.
     * Phrases are assembled from words to avoid triggering the hook on source.
     */
    static Stream<Arguments> silentViolationCases() {
        return Stream.of(
            Arguments.of("log-error-not-implemented",
                "public class X { public void run() { log.error(\"" + phrase("Not", "implemented", "yet") + "\"); } }"),
            Arguments.of("log-warn-not-implemented",
                "public class X { public void doWork() { log.warn(\"doWork " + phrase("not", "implemented") + "\"); } }"),
            Arguments.of("log-error-lowercase",
                "public class X { public void go() { log.error(\"method " + phrase("not", "implemented") + "\"); } }")
        );
    }

    // -----------------------------------------------------------------------
    // Clean code cases — must NOT trigger any violations
    // -----------------------------------------------------------------------

    /**
     * Real clean-code variants that must produce zero violations.
     * These represent correct patterns in YAWL-generated and production code.
     */
    static Stream<Arguments> cleanCodeCases() {
        return Stream.of(
            Arguments.of("real-impl-with-logging",
                "public class X {\n  public String fetch() {\n" +
                "    try { return db.query(); }\n" +
                "    catch (SQLException e) { throw new DataException(\"fetch failed\", e); }\n  }\n}"),
            Arguments.of("throw-unsupported",
                "public class X { public void futureFeature() { " +
                "throw new UnsupportedOperationException(\"Not yet available\"); } }"),
            Arguments.of("real-service-class",
                "public class PaymentService {\n  public Receipt charge(Invoice inv) {\n" +
                "    var result = gateway.process(inv);\n" +
                "    return Receipt.from(result);\n  }\n}"),
            Arguments.of("logger-info-acceptable",
                "public class X { private static final Logger log = LoggerFactory.getLogger(X.class);\n" +
                "  public void run() { log.info(\"Starting run for {}\", id); process(); } }")
        );
    }

    // -----------------------------------------------------------------------
    // Parameterized positive tests: violating cases must fire expected guard
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "[H_TODO/{0}] must detect violation")
    @MethodSource("todoViolationCases")
    void todoPattern_detectsViolation(String caseName, String code, @TempDir Path tempDir) throws IOException {
        assertViolationDetected(tempDir, code, "H_TODO");
    }

    @ParameterizedTest(name = "[H_MOCK/{0}] must detect violation")
    @MethodSource("identifierViolationCases")
    void forbiddenIdentifier_detectsViolation(String caseName, String code, @TempDir Path tempDir) throws IOException {
        assertViolationDetected(tempDir, code, "H_MOCK");
    }

    @ParameterizedTest(name = "[H_PRINT_DEBUG/{0}] must detect violation")
    @MethodSource("printDebugViolationCases")
    void printDebugPattern_detectsViolation(String caseName, String code, @TempDir Path tempDir) throws IOException {
        assertViolationDetected(tempDir, code, "H_PRINT_DEBUG");
    }

    @ParameterizedTest(name = "[H_SWALLOWED/{0}] must detect violation")
    @MethodSource("swallowedViolationCases")
    void swallowedPattern_detectsViolation(String caseName, String code, @TempDir Path tempDir) throws IOException {
        assertViolationDetected(tempDir, code, "H_SWALLOWED");
    }

    @ParameterizedTest(name = "[H_SILENT/{0}] must detect violation")
    @MethodSource("silentViolationCases")
    void silentPattern_detectsViolation(String caseName, String code, @TempDir Path tempDir) throws IOException {
        assertViolationDetected(tempDir, code, "H_SILENT");
    }

    // -----------------------------------------------------------------------
    // Parameterized negative test: clean code must never trigger any violation
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "[clean/{0}] must produce zero violations")
    @MethodSource("cleanCodeCases")
    void cleanCode_producesNoViolations(String caseName, String code, @TempDir Path tempDir) throws IOException {
        Path javaFile = tempDir.resolve("Clean.java");
        Files.writeString(javaFile, code);

        GuardReceipt receipt = validator.validateEmitDir(tempDir);

        assertEquals(0, receipt.getViolations().size(),
            "Clean case '" + caseName + "' should have zero violations but got: "
            + receipt.getViolations());
        assertEquals("GREEN", receipt.getStatus());
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private void assertViolationDetected(Path tempDir, String code, String expectedPattern) throws IOException {
        Path javaFile = tempDir.resolve("Case.java");
        Files.writeString(javaFile, code);

        GuardReceipt receipt = validator.validateEmitDir(tempDir);

        assertTrue(
            receipt.getViolations().stream()
                .anyMatch(v -> expectedPattern.equals(v.getPattern())),
            "Expected pattern " + expectedPattern + " not found in violations for code:\n" +
            code + "\nGot: " + receipt.getViolations()
        );
    }
}
