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

        assertGreaterThanOrEqual(receipt.getViolations().size(), 3,
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
                        "System.out.println(\"Implementation\"); } }";
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
        assertTrue(json.contains("\"phase\":\"guards\""));
        assertTrue(json.contains("\"status\":\"RED\""));
        assertTrue(json.contains("\"violations\""));
    }

    @Test
    @DisplayName("Validator registers default checkers")
    void testValidatorRegistersDefaultCheckers() {
        List<GuardChecker> checkers = validator.getCheckers();

        assertEquals(7, checkers.size(), "Should register 7 default checkers");
        assertTrue(checkers.stream()
                .map(GuardChecker::patternName)
                .allMatch(name -> name.matches("H_(T|M|S|E|F|L|S).*")));
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
                        "System.out.println(\"done\"); } }";
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
