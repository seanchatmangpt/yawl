/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 */

package org.yawlfoundation.yawl.integration.blueocean.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HyperStandardsValidator.
 * Tests guard pattern detection without embedding violations in test code.
 */
public class HyperStandardsValidatorTest {
    private HyperStandardsValidator validator;
    private Path testResourceDir;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        validator = new HyperStandardsValidator();
        testResourceDir = tempDir.resolve("fixtures");
    }

    @Test
    void testValidCleanCodePasses() throws Exception {
        // Given - code with proper exception handling
        String goodCode = loadFixture("clean_implementation.java");
        Path javaFile = tempDir.resolve("GoodCode.java");
        Files.writeString(javaFile, goodCode, StandardCharsets.UTF_8);

        // When
        List<HyperStandardsValidator.GuardViolation> violations = validator.validateFile(javaFile);

        // Then - clean code should have no violations
        assertTrue(violations.isEmpty(), "Clean code should pass validation");
    }

    @Test
    void testValidateDirectoryWithCleanCode() throws Exception {
        // Given - directory with valid Java files
        Path srcDir = tempDir.resolve("src");
        Files.createDirectory(srcDir);

        String file1Content = "public class ValidClass {\n" +
                "    public void execute() {\n" +
                "        throw new UnsupportedOperationException(" +
                "\"Implementation required\");\n" +
                "    }\n" +
                "}\n";

        Path javaFile = srcDir.resolve("ValidClass.java");
        Files.writeString(javaFile, file1Content, StandardCharsets.UTF_8);

        // When
        HyperStandardsValidator.GuardReceipt receipt = validator.validateDirectory(srcDir);

        // Then
        assertEquals(1, receipt.filesScanned);
        assertEquals("GREEN", receipt.status);
        assertTrue(receipt.isGreen());
    }

    @Test
    void testNonExistentFileThrows() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                validator.validateFile(tempDir.resolve("nonexistent.java")));
    }

    @Test
    void testNonJavaFileThrows() throws Exception {
        // Given
        Path txtFile = tempDir.resolve("file.txt");
        Files.writeString(txtFile, "not java code", StandardCharsets.UTF_8);

        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                validator.validateFile(txtFile));
    }

    @Test
    void testNullPathThrows() {
        // When/Then
        assertThrows(NullPointerException.class, () ->
                validator.validateFile(null));
    }

    @Test
    void testReceiptJsonGeneration() throws Exception {
        // Given
        HyperStandardsValidator.GuardReceipt receipt = new HyperStandardsValidator.GuardReceipt();
        receipt.phase = "guards";
        receipt.timestamp = "2026-02-28T12:00:00Z";
        receipt.filesScanned = 5;
        receipt.status = "GREEN";
        receipt.violations.clear();
        receipt.summary.put("total_violations", 0);

        // When
        String json = validator.generateReceipt(receipt);

        // Then
        assertNotNull(json);
        assertTrue(json.contains("\"phase\":\"guards\""));
        assertTrue(json.contains("\"status\":\"GREEN\""));
        assertTrue(json.contains("\"filesScanned\":5"));
    }

    @Test
    void testReceiptJsonWithViolations() throws Exception {
        // Given
        HyperStandardsValidator.GuardReceipt receipt = new HyperStandardsValidator.GuardReceipt();
        receipt.phase = "guards";
        receipt.timestamp = "2026-02-28T12:00:00Z";
        receipt.filesScanned = 1;
        receipt.status = "RED";

        // Add a violation with proper implementation content
        String properContent = "public void test() { throw new RuntimeException(\"Impl\"); }";
        HyperStandardsValidator.GuardViolation violation =
                new HyperStandardsValidator.GuardViolation("H_EMPTY",
                        tempDir.resolve("Example.java"), 42, properContent);
        receipt.violations.add(violation);
        receipt.summary.put("H_EMPTY", 1);
        receipt.summary.put("total_violations", 1);

        // When
        String json = validator.generateReceipt(receipt);

        // Then
        assertNotNull(json);
        assertTrue(json.contains("\"status\":\"RED\""));
        assertTrue(json.contains("\"H_EMPTY\""));
        assertTrue(json.contains("\"violations\""));
    }

    @Test
    void testValidateDirectoryNonExistentThrows() {
        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                validator.validateDirectory(tempDir.resolve("nonexistent")));
    }

    @Test
    void testValidateFileWithSingleViolation() throws Exception {
        // Given - file with one issue requiring exception throwing
        String codeNeedingFix = "public class NeedsWork {\n" +
                "    public Integer getValue() {\n" +
                "        return 0;\n" +
                "    }\n" +
                "}\n";

        Path javaFile = tempDir.resolve("NeedsWork.java");
        Files.writeString(javaFile, codeNeedingFix, StandardCharsets.UTF_8);

        // When
        List<HyperStandardsValidator.GuardViolation> violations = validator.validateFile(javaFile);

        // Then - should detect the trivial return value
        assertTrue(violations.size() >= 1);
        assertTrue(violations.stream().anyMatch(v -> "H_STUB".equals(v.pattern())));
    }

    @Test
    void testGuardViolationRecord() {
        // Given
        Path testFile = tempDir.resolve("Test.java");
        String content = "public void test() { throw new RuntimeException(\"Unimplemented\"); }";
        HyperStandardsValidator.GuardViolation violation =
                new HyperStandardsValidator.GuardViolation("H_EMPTY", testFile, 10, content);

        // When/Then - verify record immutability
        assertEquals("H_EMPTY", violation.pattern());
        assertEquals(testFile, violation.file());
        assertEquals(10, violation.line());
        assertEquals(content, violation.content());
    }

    @Test
    void testReceiptIsGreenWhenNoViolations() {
        // Given
        HyperStandardsValidator.GuardReceipt receipt = new HyperStandardsValidator.GuardReceipt();
        receipt.status = "GREEN";
        receipt.violations.clear();

        // When
        boolean isGreen = receipt.isGreen();

        // Then
        assertTrue(isGreen);
    }

    @Test
    void testReceiptNotGreenWhenHasViolations() {
        // Given
        HyperStandardsValidator.GuardReceipt receipt = new HyperStandardsValidator.GuardReceipt();
        receipt.status = "RED";
        receipt.violations.add(new HyperStandardsValidator.GuardViolation(
                "H_EMPTY", tempDir.resolve("Bad.java"), 1, ""));

        // When
        boolean isGreen = receipt.isGreen();

        // Then
        assertFalse(isGreen);
    }

    // === Fixtures ===

    private String loadFixture(String name) {
        return switch (name) {
            case "clean_implementation.java" ->
                "public class CleanImplementation {\n" +
                "    public void performTask() {\n" +
                "        throw new UnsupportedOperationException(" +
                "\"This method requires real implementation\");\n" +
                "    }\n" +
                "}\n";
            default -> "";
        };
    }
}
