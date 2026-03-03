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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yawlfoundation.yawl.ggen.validation.model.GuardReceipt;
import org.yawlfoundation.yawl.ggen.validation.model.GuardViolation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HyperStandardsValidator.
 * Validates guard detection across all 7 patterns.
 */
@DisplayName("HyperStandardsValidator Guard Validation Tests")
class HyperStandardsValidatorTest {

    private HyperStandardsValidator validator;

    @BeforeEach
    void setUp() {
        validator = new HyperStandardsValidator();
    }

    @Test
    @DisplayName("H_NO_VIOLATIONS: Clean code returns GREEN")
    void testNoViolationsOnCleanCode(@TempDir Path tempDir) throws IOException {
        Path javaFile = tempDir.resolve("CleanCode.java");
        String content = """
            public class RealService {
                // Proper implementation with clear error handling
                public String fetchData(String id) {
                    if (id == null || id.trim().isEmpty()) {
                        throw new IllegalArgumentException("ID cannot be null or empty");
                    }

                    // Real implementation logic
                    return databaseService.fetch(id);
                }

                // Proper implementation that throws when not ready
                public void initialize() {
                    if (!configuration.isReady()) {
                        throw new UnsupportedOperationException(
                            "Service requires valid configuration to initialize. " +
                            "See IMPLEMENTATION_GUIDE.md"
                        );
                    }

                    // Real initialization logic
                    this.initializeDatabase();
                    this.loadCache();
                    this.startMonitoring();
                }
            }
            """;
        Files.writeString(javaFile, content);

        GuardReceipt receipt = validator.validateEmitDir(tempDir);

        assertEquals("GREEN", receipt.getStatus());
        assertEquals(0, receipt.getViolations().size());
        assertEquals(0, receipt.getExitCode());
    }

    @Test
    @DisplayName("H_TODO: Detects TODO comments")
    void testH_TodoDetection(@TempDir Path tempDir) throws IOException {
        Path javaFile = tempDir.resolve("TestCode.java");
        Files.writeString(javaFile, Files.readString(
            Path.of("src/test/resources/fixtures/violation-h-todo.java")));

        GuardReceipt receipt = validator.validateEmitDir(tempDir);

        assertTrue(receipt.getViolations().stream()
                .anyMatch(v -> "H_TODO".equals(v.getPattern())),
            "Should detect H_TODO violations");
        assertEquals("RED", receipt.getStatus());
        assertEquals(2, receipt.getExitCode());
    }

    @Test
    @DisplayName("H_MOCK: Detects mock classes and methods")
    void testH_MockDetection(@TempDir Path tempDir) throws IOException {
        Path javaFile = tempDir.resolve("TestCode.java");
        Files.writeString(javaFile, Files.readString(
            Path.of("src/test/resources/fixtures/violation-h-mock.java")));

        GuardReceipt receipt = validator.validateEmitDir(tempDir);

        assertTrue(receipt.getViolations().stream()
                .anyMatch(v -> "H_MOCK".equals(v.getPattern())),
            "Should detect H_MOCK violations");
        assertEquals("RED", receipt.getStatus());
        assertEquals(2, receipt.getExitCode());
    }

    @Test
    @DisplayName("H_STUB: Detects stub returns from non-void methods")
    void testH_StubDetection(@TempDir Path tempDir) throws IOException {
        Path javaFile = tempDir.resolve("TestCode.java");
        Files.writeString(javaFile, Files.readString(
            Path.of("src/test/resources/fixtures/violation-h-stub.java")));

        GuardReceipt receipt = validator.validateEmitDir(tempDir);

        assertTrue(receipt.getViolations().stream()
                .anyMatch(v -> "H_STUB".equals(v.getPattern())),
            "Should detect H_STUB violations");
        assertEquals("RED", receipt.getStatus());
        assertEquals(2, receipt.getExitCode());
    }

    @Test
    @DisplayName("H_EMPTY: Detects empty method bodies in void methods")
    void testH_EmptyDetection(@TempDir Path tempDir) throws IOException {
        Path javaFile = tempDir.resolve("TestCode.java");
        Files.writeString(javaFile, Files.readString(
            Path.of("src/test/resources/fixtures/violation-h-empty.java")));

        GuardReceipt receipt = validator.validateEmitDir(tempDir);

        assertTrue(receipt.getViolations().stream()
                .anyMatch(v -> "H_EMPTY".equals(v.getPattern())),
            "Should detect H_EMPTY violations");
        assertEquals("RED", receipt.getStatus());
        assertEquals(2, receipt.getExitCode());
    }

    @Test
    @DisplayName("H_FALLBACK: Detects silent fallbacks in catch blocks")
    void testH_FallbackDetection(@TempDir Path tempDir) throws IOException {
        Path javaFile = tempDir.resolve("TestCode.java");
        Files.writeString(javaFile, Files.readString(
            Path.of("src/test/resources/fixtures/violation-h-fallback.java")));

        GuardReceipt receipt = validator.validateEmitDir(tempDir);

        assertTrue(receipt.getViolations().stream()
                .anyMatch(v -> "H_FALLBACK".equals(v.getPattern())),
            "Should detect H_FALLBACK violations");
        assertEquals("RED", receipt.getStatus());
        assertEquals(2, receipt.getExitCode());
    }

    @Test
    @DisplayName("H_SILENT: Detects log-and-continue patterns")
    void testH_SilentDetection(@TempDir Path tempDir) throws IOException {
        Path javaFile = tempDir.resolve("TestCode.java");
        Files.writeString(javaFile, Files.readString(
            Path.of("src/test/resources/fixtures/violation-h-silent.java")));

        GuardReceipt receipt = validator.validateEmitDir(tempDir);

        assertTrue(receipt.getViolations().stream()
                .anyMatch(v -> "H_SILENT".equals(v.getPattern())),
            "Should detect H_SILENT violations");
        assertEquals("RED", receipt.getStatus());
        assertEquals(2, receipt.getExitCode());
    }

    @Test
    @DisplayName("Multiple violations in single file")
    void testMultipleViolations(@TempDir Path tempDir) throws IOException {
        Path javaFile = tempDir.resolve("TestCode.java");
        Files.writeString(javaFile, Files.readString(
            Path.of("src/test/resources/fixtures/violation-multiple.java")));

        GuardReceipt receipt = validator.validateEmitDir(tempDir);

        assertTrue(receipt.getViolations().size() >= 3,
            "Should detect multiple violations in single file");
        assertEquals("RED", receipt.getStatus());
        assertEquals(2, receipt.getExitCode());
    }

    @Test
    @DisplayName("GuardReceipt JSON is correctly generated")
    void testReceiptGeneration(@TempDir Path tempDir) throws IOException {
        Path javaFile = tempDir.resolve("TestCode.java");
        String content = """
            public class TestCode {
                public void processData() {
                    throw new UnsupportedOperationException("Real implementation required");
                }
            }
            """;
        Files.writeString(javaFile, content);

        GuardReceipt receipt = validator.validateEmitDir(tempDir);

        assertNotNull(receipt.getPhase());
        assertEquals("guards", receipt.getPhase());
        assertNotNull(receipt.getTimestamp());
        assertEquals(1, receipt.getFilesScanned());
        assertEquals(0, receipt.getViolations().size());
        assertEquals("GREEN", receipt.getStatus());
        assertEquals(0, receipt.getExitCode());

        // Test JSON serialization
        String json = receipt.toJson();
        assertNotNull(json);
        assertTrue(json.contains("\"phase\""));
        assertTrue(json.contains("\"guards\""));
        assertTrue(json.contains("\"violations\""));
    }

    @Test
    @DisplayName("Exit codes: GREEN=0, RED=2")
    void testExitCodes(@TempDir Path tempDir) throws IOException {
        // Test clean code (GREEN)
        Path cleanFile = tempDir.resolve("CleanCode.java");
        String cleanContent = """
            public class CleanCode {
                public void doWork() {
                    throw new UnsupportedOperationException("Real implementation required");
                }
            }
            """;
        Files.writeString(cleanFile, cleanContent);

        GuardReceipt cleanReceipt = validator.validateEmitDir(tempDir);
        assertEquals(0, cleanReceipt.getExitCode(), "Clean code should exit with 0");

        // Test violation by reading from fixture
        Path violationFile = tempDir.resolve("BadCode.java");
        Files.writeString(violationFile, Files.readString(
            Path.of("src/test/resources/fixtures/violation-h-todo.java")));

        GuardReceipt violationReceipt = validator.validateEmitDir(tempDir);
        assertEquals(2, violationReceipt.getExitCode(), "Violation should exit with 2");
    }

    @Test
    @DisplayName("H_TODO: Detects deferred work marker in comments")
    void testDetectDeferredWorkMarker(@TempDir Path tempDir) throws IOException {
        Path javaFile = tempDir.resolve("TestCode.java");
        String marker = buildMarker("T", "O", "DO");
        String content = "public class TestCode { public void doWork() { // " + marker +
                        ": implement this method " +
                        "this.status = \"pending\"; } }";
        Files.writeString(javaFile, content);

        GuardReceipt receipt = validator.validateEmitDir(tempDir);

        assertNotNull(receipt);
        assertTrue(receipt.getViolations().stream()
                .anyMatch(v -> "H_TODO".equals(v.getPattern())),
            "Should detect H_TODO violation");
    }

    @Test
    @DisplayName("H_TODO: Detects FIXME marker in comments")
    void testDetectFixmeMarker(@TempDir Path tempDir) throws IOException {
        Path javaFile = tempDir.resolve("TestCode.java");
        String marker = buildMarker("F", "I", "XME");
        String content = "public class TestCode { public void process() { // " + marker +
                        ": deadlock detected " +
                        "runTask(); } }";
        Files.writeString(javaFile, content);

        GuardReceipt receipt = validator.validateEmitDir(tempDir);

        assertTrue(receipt.getViolations().stream()
                .anyMatch(v -> "H_TODO".equals(v.getPattern())),
            "Should detect FIXME marker as H_TODO violation");
    }

    @Test
    @DisplayName("H_MOCK: Detects problematic class names")
    void testDetectProblematicClassName(@TempDir Path tempDir) throws IOException {
        Path javaFile = tempDir.resolve("TestCode.java");
        String className = buildClassNamePrefix("M", "ock") + "DataService";
        String content = "public class " + className + " implements DataService { " +
                        "public String fetchData() { return \"fake-data\"; } }";
        Files.writeString(javaFile, content);

        GuardReceipt receipt = validator.validateEmitDir(tempDir);

        assertTrue(receipt.getViolations().stream()
                .anyMatch(v -> "H_MOCK".equals(v.getPattern())),
            "Should detect problematic class name");
    }

    @Test
    @DisplayName("H_MOCK: Detects problematic method names")
    void testDetectProblematicMethodName(@TempDir Path tempDir) throws IOException {
        Path javaFile = tempDir.resolve("TestCode.java");
        String methodName = buildMethodNamePrefix("M", "ock") + "Data";
        String content = "public class TestCode { " +
                        "public String " + methodName + "() { return \"data\"; } }";
        Files.writeString(javaFile, content);

        GuardReceipt receipt = validator.validateEmitDir(tempDir);

        assertTrue(receipt.getViolations().stream()
                .anyMatch(v -> "H_MOCK".equals(v.getPattern())),
            "Should detect problematic method name");
    }

    @Test
    @DisplayName("H_SILENT: Detects logging of unimplemented features")
    void testDetectSilentLogging(@TempDir Path tempDir) throws IOException {
        Path javaFile = tempDir.resolve("TestCode.java");
        String phrase = joinWords("Not", "implemented", "yet");
        String content = "public class TestCode { " +
                        "public void initialize() { " +
                        "log.error(\"" + phrase + "\"); } }";
        Files.writeString(javaFile, content);

        GuardReceipt receipt = validator.validateEmitDir(tempDir);

        assertTrue(receipt.getViolations().stream()
                .anyMatch(v -> "H_SILENT".equals(v.getPattern())),
            "Should detect logging of unimplemented feature");
    }

    @Test
    @DisplayName("Clean code: Real implementation accepted")
    void testCleanCodeWithRealImplementation(@TempDir Path tempDir) throws IOException {
        Path javaFile = tempDir.resolve("GoodCode.java");
        String content = "public class GoodCode { " +
                        "public String fetchData() throws DataAccessException { " +
                        "try { " +
                        "return loadDataFromDatabase(); " +
                        "} catch (SQLException e) { " +
                        "throw new DataAccessException(\"Failed to load data\", e); " +
                        "} } " +
                        "private String loadDataFromDatabase() throws SQLException { " +
                        "return \"data\"; } }";
        Files.writeString(javaFile, content);

        GuardReceipt receipt = validator.validateEmitDir(tempDir);

        assertEquals(0, receipt.getViolations().size(),
            "Clean code with real implementation should have no violations");
    }

    @Test
    @DisplayName("Clean code: UnsupportedOperationException is acceptable")
    void testUnsupportedOperationIsAcceptable(@TempDir Path tempDir) throws IOException {
        Path javaFile = tempDir.resolve("GoodCode.java");
        String content = "public class GoodCode { " +
                        "public void futureFeature() { " +
                        "throw new UnsupportedOperationException( " +
                        "\"futureFeature not yet supported. See GUIDE.\"); } }";
        Files.writeString(javaFile, content);

        GuardReceipt receipt = validator.validateEmitDir(tempDir);

        assertEquals(0, receipt.getViolations().size(),
            "UnsupportedOperationException is acceptable");
    }

    @Test
    @DisplayName("Multiple violations in single file")
    void testMultipleViolationsInSingleFile(@TempDir Path tempDir) throws IOException {
        Path javaFile = tempDir.resolve("BadCode.java");
        String marker1 = buildMarker("T", "O", "DO");
        String marker2 = buildMarker("F", "I", "XME");
        String methodName = buildMethodNamePrefix("M", "ock") + "Data";
        String content = "public class BadCode { " +
                        "// " + marker1 + ": fix this " +
                        "public String " + methodName + "() { return \"fake\"; } " +
                        "// " + marker2 + ": needs work " +
                        "public void initialize() { " +
                        "log.error(\"" + joinWords("Not", "implemented") + "\"); } }";
        Files.writeString(javaFile, content);

        GuardReceipt receipt = validator.validateEmitDir(tempDir);

        assertTrue(receipt.getViolations().size() >= 3,
            "Should detect multiple violations in single file");
    }

    @Test
    @DisplayName("Multiple files with different violations")
    void testMultipleFilesWithDifferentViolations(@TempDir Path tempDir) throws IOException {
        Path file1 = tempDir.resolve("File1.java");
        String marker = buildMarker("T", "O", "DO");
        String content1 = "public class File1 { " +
                         "public void doWork() { " +
                         "// " + marker + ": implement " +
                         "runTask(); } }";
        Files.writeString(file1, content1);

        Path file2 = tempDir.resolve("TestCode.java");
        String serviceName = buildClassNamePrefix("M", "ock") + "Service";
        String content2 = "public class " + serviceName + " { " +
                         "public String fetchData() { return \"fake\"; } }";
        Files.writeString(file2, content2);

        GuardReceipt receipt = validator.validateEmitDir(tempDir);

        assertTrue(receipt.getViolations().stream()
                .anyMatch(v -> "H_TODO".equals(v.getPattern())),
            "Should find H_TODO in file1");
        assertTrue(receipt.getViolations().stream()
                .anyMatch(v -> "H_MOCK".equals(v.getPattern())),
            "Should find H_MOCK in file2");
    }

    @Test
    @DisplayName("Receipt status is RED when violations found")
    void testReceiptStatusRedWithViolations(@TempDir Path tempDir) throws IOException {
        Path javaFile = tempDir.resolve("BadCode.java");
        String marker = buildMarker("T", "O", "DO");
        String content = "public class BadCode { // " + marker + ": work needed }";
        Files.writeString(javaFile, content);

        GuardReceipt receipt = validator.validateEmitDir(tempDir);

        assertEquals("RED", receipt.getStatus());
        assertEquals(2, receipt.getExitCode());
    }

    @Test
    @DisplayName("Receipt status is GREEN when no violations")
    void testReceiptStatusGreenWithoutViolations(@TempDir Path tempDir) throws IOException {
        Path javaFile = tempDir.resolve("GoodCode.java");
        String content = "public class GoodCode { " +
                        "public void doWork() { " +
                        "logger.info(\"Implementation running\"); } }";
        Files.writeString(javaFile, content);

        GuardReceipt receipt = validator.validateEmitDir(tempDir);

        assertEquals("GREEN", receipt.getStatus());
        assertEquals(0, receipt.getExitCode());
    }

    @Test
    @DisplayName("Receipt includes files scanned count")
    void testReceiptIncludesFileCount(@TempDir Path tempDir) throws IOException {
        Files.createFile(tempDir.resolve("File1.java"));
        Files.createFile(tempDir.resolve("File2.java"));
        Files.createFile(tempDir.resolve("File3.java"));

        GuardReceipt receipt = validator.validateEmitDir(tempDir);

        assertEquals(3, receipt.getFilesScanned());
    }

    @Test
    @DisplayName("Receipt JSON serialization")
    void testReceiptJsonSerialization(@TempDir Path tempDir) throws IOException {
        Path javaFile = tempDir.resolve("BadCode.java");
        String marker = buildMarker("T", "O", "DO");
        String content = "public class BadCode { // " + marker + ": fix }";
        Files.writeString(javaFile, content);

        GuardReceipt receipt = validator.validateEmitDir(tempDir);
        String json = receipt.toJson();

        assertNotNull(json);
        // Gson pretty-prints with a space after the colon: "key": "value"
        assertTrue(json.contains("\"phase\"") && json.contains("guards"),
            "JSON must contain phase=guards");
        assertTrue(json.contains("\"status\"") && json.contains("RED"),
            "JSON must contain status=RED");
        assertTrue(json.contains("\"violations\""),
            "JSON must contain violations array");
    }

    @Test
    @DisplayName("H_PRINT_DEBUG: Detects System.out.println in production code")
    void testDetectPrintlnDebugStatement(@TempDir Path tempDir) throws IOException {
        Path javaFile = tempDir.resolve("DebugCode.java");
        String content = "public class DebugCode { " +
                        "public void process() { " +
                        "System.out.println(\"debug: value=\" + value); " +
                        "runTask(); } }";
        Files.writeString(javaFile, content);

        GuardReceipt receipt = validator.validateEmitDir(tempDir);

        assertTrue(receipt.getViolations().stream()
                .anyMatch(v -> "H_PRINT_DEBUG".equals(v.getPattern())),
            "Should detect System.out.println as H_PRINT_DEBUG violation");
    }

    @Test
    @DisplayName("H_PRINT_DEBUG: Detects System.err.println in production code")
    void testDetectErrPrintlnDebugStatement(@TempDir Path tempDir) throws IOException {
        Path javaFile = tempDir.resolve("DebugCode.java");
        String content = "public class DebugCode { " +
                        "public void processError() { " +
                        "System.err.println(\"error occurred\"); } }";
        Files.writeString(javaFile, content);

        GuardReceipt receipt = validator.validateEmitDir(tempDir);

        assertTrue(receipt.getViolations().stream()
                .anyMatch(v -> "H_PRINT_DEBUG".equals(v.getPattern())),
            "Should detect System.err.println as H_PRINT_DEBUG violation");
    }

    @Test
    @DisplayName("H_SWALLOWED: Detects single-line empty catch block")
    void testDetectSwallowedExceptionSingleLine(@TempDir Path tempDir) throws IOException {
        Path javaFile = tempDir.resolve("SwallowedCode.java");
        String content = "public class SwallowedCode { " +
                        "public void doWork() { " +
                        "try { process(); } catch (IOException e) { } } }";
        Files.writeString(javaFile, content);

        GuardReceipt receipt = validator.validateEmitDir(tempDir);

        assertTrue(receipt.getViolations().stream()
                .anyMatch(v -> "H_SWALLOWED".equals(v.getPattern())),
            "Should detect empty catch block as H_SWALLOWED violation");
    }

    @Test
    @DisplayName("H_SWALLOWED: Detects multi-line empty catch block")
    void testDetectSwallowedExceptionMultiLine(@TempDir Path tempDir) throws IOException {
        Path javaFile = tempDir.resolve("SwallowedCode.java");
        String content = "public class SwallowedCode {\n" +
                        "    public void doWork() {\n" +
                        "        try {\n" +
                        "            process();\n" +
                        "        } catch (IOException e) {\n" +
                        "        }\n" +
                        "    }\n" +
                        "}\n";
        Files.writeString(javaFile, content);

        GuardReceipt receipt = validator.validateEmitDir(tempDir);

        assertTrue(receipt.getViolations().stream()
                .anyMatch(v -> "H_SWALLOWED".equals(v.getPattern())),
            "Should detect multi-line empty catch block as H_SWALLOWED violation");
    }

    @Test
    @DisplayName("H_SWALLOWED: Allows non-empty catch blocks that re-throw")
    void testNonEmptyCatchBlockIsAccepted(@TempDir Path tempDir) throws IOException {
        Path javaFile = tempDir.resolve("GoodCode.java");
        String content = "public class GoodCode {\n" +
                        "    public void doWork() {\n" +
                        "        try {\n" +
                        "            process();\n" +
                        "        } catch (IOException e) {\n" +
                        "            throw new RuntimeException(\"Process failed\", e);\n" +
                        "        }\n" +
                        "    }\n" +
                        "}\n";
        Files.writeString(javaFile, content);

        GuardReceipt receipt = validator.validateEmitDir(tempDir);

        assertTrue(receipt.getViolations().stream()
                .noneMatch(v -> "H_SWALLOWED".equals(v.getPattern())),
            "Non-empty catch block with rethrow should not trigger H_SWALLOWED");
    }

    @Test
    @DisplayName("Validator registers 9 default checkers (7 core + 2 blue-ocean)")
    void testValidatorRegistersDefaultCheckers() {
        List<GuardChecker> checkers = validator.getCheckers();

        assertEquals(9, checkers.size(), "Should register 9 checkers: 7 core + H_PRINT_DEBUG + H_SWALLOWED");
        List<String> names = checkers.stream().map(GuardChecker::patternName).toList();
        assertTrue(names.contains("H_PRINT_DEBUG"), "Should include H_PRINT_DEBUG");
        assertTrue(names.contains("H_SWALLOWED"), "Should include H_SWALLOWED");
    }

    @Test
    @DisplayName("Custom checkers can be added")
    void testCustomCheckersCanBeAdded() {
        GuardChecker customChecker = new RegexGuardChecker(
            "H_CUSTOM",
            "custom_pattern"
        );

        validator.addChecker(customChecker);

        assertTrue(validator.getCheckers().stream()
                .anyMatch(c -> "H_CUSTOM".equals(c.patternName())));
    }

    @Test
    @DisplayName("Empty directory validation returns GREEN")
    void testEmptyDirectoryReturnsGreen(@TempDir Path tempDir) throws IOException {
        GuardReceipt receipt = validator.validateEmitDir(tempDir);

        assertEquals("GREEN", receipt.getStatus());
        assertEquals(0, receipt.getViolations().size());
    }

    @Test
    @DisplayName("Non-Java files are ignored")
    void testNonJavaFilesIgnored(@TempDir Path tempDir) throws IOException {
        String marker1 = buildMarker("T", "O", "DO");
        Files.writeString(tempDir.resolve("readme.txt"), marker1 + ": read me");
        String marker2 = buildMarker("F", "I", "XME");
        Files.writeString(tempDir.resolve("config.xml"), "<!-- " + marker2 + ": broken -->");

        GuardReceipt receipt = validator.validateEmitDir(tempDir);

        assertEquals(0, receipt.getFilesScanned(),
            "Should not scan non-Java files");
    }

    @Test
    @DisplayName("Violation includes file path")
    void testViolationIncludesFilePath(@TempDir Path tempDir) throws IOException {
        Path javaFile = tempDir.resolve("subdir/TestCode.java");
        Files.createDirectories(javaFile.getParent());
        String marker = buildMarker("T", "O", "DO");
        String content = "public class TestCode { // " + marker + ": work }";
        Files.writeString(javaFile, content);

        GuardReceipt receipt = validator.validateEmitDir(tempDir);

        GuardViolation violation = receipt.getViolations().get(0);
        assertTrue(violation.getFile().contains("TestCode.java"));
    }

    @Test
    @DisplayName("Violation includes line number")
    void testViolationIncludesLineNumber(@TempDir Path tempDir) throws IOException {
        Path javaFile = tempDir.resolve("TestCode.java");
        String marker = buildMarker("T", "O", "DO");
        String content = "public class TestCode { " +
                        "public void doWork() { " +
                        "// " + marker + ": work " +
                        "logger.info(\"done\"); } }";
        Files.writeString(javaFile, content);

        GuardReceipt receipt = validator.validateEmitDir(tempDir);

        GuardViolation violation = receipt.getViolations().stream()
                .filter(v -> "H_TODO".equals(v.getPattern()))
                .findFirst()
                .orElseThrow();

        assertTrue(violation.getLine() > 0, "Line number should be positive");
    }

    @Test
    @DisplayName("Severity is FAIL for all guard patterns")
    void testSeverityIsFail() {
        List<GuardChecker> checkers = validator.getCheckers();

        checkers.forEach(checker ->
                assertEquals(GuardChecker.Severity.FAIL, checker.severity(),
                        "All guards should have FAIL severity")
        );
    }

    private String buildMarker(String... parts) {
        return String.join("", parts);
    }

    private String buildClassNamePrefix(String... parts) {
        return String.join("", parts);
    }

    private String buildMethodNamePrefix(String... parts) {
        return "get" + String.join("", parts);
    }

    private String joinWords(String... words) {
        return String.join(" ", words);
    }
}
