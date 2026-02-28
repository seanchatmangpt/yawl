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
 */
public class HyperStandardsValidatorTest {
    private HyperStandardsValidator validator;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        validator = new HyperStandardsValidator();
    }

    @Test
    void testDetectsTodoMarker() throws Exception {
        // Given
        Path javaFile = createJavaFile("""
                public class Example {
                    // TODO: implement this method
                    public void doWork() {}
                }
                """);

        // When
        List<HyperStandardsValidator.GuardViolation> violations = validator.validateFile(javaFile);

        // Then
        assertTrue(violations.stream().anyMatch(v -> "H_TODO".equals(v.pattern())));
    }

    @Test
    void testDetectsFixmeMarker() throws Exception {
        // Given
        Path javaFile = createJavaFile("""
                public class Example {
                    // FIXME: this is incomplete
                    public void process() {}
                }
                """);

        // When
        List<HyperStandardsValidator.GuardViolation> violations = validator.validateFile(javaFile);

        // Then
        assertTrue(violations.stream().anyMatch(v -> "H_TODO".equals(v.pattern())));
    }

    @Test
    void testDetectsTrivialReturns() throws Exception {
        // Given
        Path javaFile = createJavaFile("""
                public class Example {
                    public String getData() {
                        return "";
                    }
                }
                """);

        // When
        List<HyperStandardsValidator.GuardViolation> violations = validator.validateFile(javaFile);

        // Then
        assertTrue(violations.stream().anyMatch(v -> "H_STUB".equals(v.pattern())));
    }

    @Test
    void testDetectsEmptyMethods() throws Exception {
        // Given
        Path javaFile = createJavaFile("""
                public class Example {
                    public void initialize() { }
                }
                """);

        // When
        List<HyperStandardsValidator.GuardViolation> violations = validator.validateFile(javaFile);

        // Then
        assertTrue(violations.stream().anyMatch(v -> "H_EMPTY".equals(v.pattern())));
    }

    @Test
    void testDetectsSpuriousImplementation() throws Exception {
        // Given
        Path javaFile = createJavaFile("""
                public class MockDataService implements DataService {
                    public String fetchData() { return "mock"; }
                }
                """);

        // When
        List<HyperStandardsValidator.GuardViolation> violations = validator.validateFile(javaFile);

        // Then
        assertTrue(violations.stream().anyMatch(v -> "H_MOCK".equals(v.pattern())));
    }

    @Test
    void testValidCleanCodePasses() throws Exception {
        // Given
        Path javaFile = createJavaFile("""
                public class GoodCode {
                    public void doWork() {
                        throw new UnsupportedOperationException("Real implementation required");
                    }
                }
                """);

        // When
        List<HyperStandardsValidator.GuardViolation> violations = validator.validateFile(javaFile);

        // Then
        assertTrue(violations.isEmpty());
    }

    @Test
    void testValidateDirectoryScansAllFiles() throws Exception {
        // Given
        Path srcDir = tempDir.resolve("src");
        Files.createDirectory(srcDir);

        Path file1 = createJavaFile("public class Good { public void run() { } }", srcDir);
        Path file2 = createJavaFile("""
                public class Bad {
                    // TODO: fix this
                    public void work() {}
                }
                """, srcDir);

        // When
        HyperStandardsValidator.GuardReceipt receipt = validator.validateDirectory(srcDir);

        // Then
        assertEquals(2, receipt.filesScanned);
        assertTrue(receipt.violations.size() > 0);
        assertEquals("RED", receipt.status);
    }

    @Test
    void testGeneratesJsonReceipt() throws Exception {
        // Given
        Path javaFile = createJavaFile("""
                public class Bad {
                    // TODO: implement
                    public String fetch() { return ""; }
                }
                """);

        validator.validateFile(javaFile);
        HyperStandardsValidator.GuardReceipt receipt = new HyperStandardsValidator.GuardReceipt();
        receipt.phase = "guards";
        receipt.status = "RED";
        receipt.filesScanned = 1;

        // When
        String json = validator.generateReceipt(receipt);

        // Then
        assertNotNull(json);
        assertTrue(json.contains("\"phase\":\"guards\""));
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
        Files.writeString(txtFile, "not java");

        // When/Then
        assertThrows(IllegalArgumentException.class, () ->
                validator.validateFile(txtFile));
    }

    @Test
    void testDetectsSilentLogging() throws Exception {
        // Given
        Path javaFile = createJavaFile("""
                public class Example {
                    public void process() {
                        log.error("Not implemented yet");
                    }
                }
                """);

        // When
        List<HyperStandardsValidator.GuardViolation> violations = validator.validateFile(javaFile);

        // Then
        assertTrue(violations.stream().anyMatch(v -> "H_SILENT".equals(v.pattern())));
    }

    @Test
    void testDetectJavadocLies() throws Exception {
        // Given
        Path javaFile = createJavaFile("""
                public class Example {
                    /** @return never null */
                    public String getValue() {
                        return null;
                    }
                }
                """);

        // When
        List<HyperStandardsValidator.GuardViolation> violations = validator.validateFile(javaFile);

        // Then
        assertTrue(violations.stream().anyMatch(v -> "H_LIE".equals(v.pattern())));
    }

    // Helper methods

    private Path createJavaFile(String content) throws Exception {
        return createJavaFile(content, tempDir);
    }

    private Path createJavaFile(String content, Path dir) throws Exception {
        Path javaFile = dir.resolve("TestClass.java");
        Files.writeString(javaFile, content, StandardCharsets.UTF_8);
        return javaFile;
    }
}
