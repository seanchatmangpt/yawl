/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.yawlfoundation.yawl.validation.shacl.ShaclValidationChecker;
import org.yawlfoundation.yawl.validation.shacl.YAWLShaclValidator;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for SHACL validation functionality.
 */
public class SHACLValidatorTest {

    private ShaclValidationChecker checker;
    private YAWLShaclValidator validator;
    private HyperStandardsValidator hyperValidator;

    @BeforeEach
    void setUp() {
        checker = new ShaclValidationChecker();
        validator = new YAWLShaclValidator();
        hyperValidator = new HyperStandardsValidator();
    }

    @Test
    void testCheckerInterfaceImplementation() {
        // Test that checker implements required methods
        assertNotNull(checker);
        assertEquals("SHACL_VALIDATION", checker.patternName());
        assertEquals("FAIL", checker.severity());
    }

    @Test
    void testHyperStandardsValidatorInitialization() {
        // Test that hyper-standards validator initializes correctly
        assertEquals(1, hyperValidator.getCheckerCount());
        assertEquals("SHACL_VALIDATION",
            hyperValidator.getCheckerByName("SHACL_VALIDATION").patternName());
    }

    @Test
    void testValidYAWLSpecification() throws Exception {
        // Create a minimal valid YAWL specification
        String validSpec = """
            @prefix yawl: <http://www.yawlfoundation.org/yawl#> .
            @prefix ex: <http://example.org/yawl-shapes#> .

            <#workflow> a yawl:YAWLSpecification ;
                yawl:hasSpecificationVersion "2.0" ;
                yawl:hasYAWLNet <#net> .

            <#net> a yawl:YAWLNet ;
                yawl:hasInputCondition <#start> ;
                yawl:hasOutputCondition <#end> ;
                yawl:hasYAWLTask <#task1> .

            <#start> a yawl:Condition ;
                yawl:hasName "start" .

            <#end> a yawl:Condition ;
                yawl:hasName "end" .

            <#task1> a yawl:Task ;
                yawl:hasName "task1" ;
                yawl:hasInputFlow <#flow1> ;
                yawl:hasOutputFlow <#flow2> .

            <#flow1> a yawl:Flow ;
                yawl:hasSource <#start> ;
                yawl:hasTarget <#task1> .

            <#flow2> a yawl:Flow ;
                yawl:hasSource <#task1> ;
                yawl:hasTarget <#end> .
            """;

        // Write valid specification to temporary file
        Path tempFile = Paths.get("test-valid-spec.ttl");
        java.nio.file.Files.writeString(tempFile, validSpec);

        try {
            // Validate the specification
            List<GuardViolation> violations = checker.check(tempFile);

            // A valid specification should pass SHACL validation
            assertTrue(violations.isEmpty(),
                "Valid specification should have no violations");
        } finally {
            // Clean up
            java.nio.file.Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void testInvalidYAWLSpecification() throws Exception {
        // Create an invalid YAWL specification (missing input condition)
        String invalidSpec = """
            @prefix yawl: <http://www.yawlfoundation.org/yawl#> .
            @prefix ex: <http://example.org/yawl-shapes#> .

            <#workflow> a yawl:YAWLSpecification ;
                yawl:hasSpecificationVersion "2.0" ;
                yawl:hasYAWLNet <#net> .

            <#net> a yawl:YAWLNet ;
                yawl:hasOutputCondition <#end> ;
                yawl:hasYAWLTask <#task1> .
                # Missing input condition - should violate constraint

            <#end> a yawl:Condition ;
                yawl:hasName "end" .

            <#task1> a yawl:Task ;
                yawl:hasName "task1" .
            """;

        // Write invalid specification to temporary file
        Path tempFile = Paths.get("test-invalid-spec.ttl");
        java.nio.file.Files.writeString(tempFile, invalidSpec);

        try {
            // Validate the specification
            List<GuardViolation> violations = checker.check(tempFile);

            // An invalid specification should have violations
            assertFalse(violations.isEmpty(),
                "Invalid specification should have violations");

            // Check that violations are properly formatted
            GuardViolation violation = violations.get(0);
            assertEquals("SHACL_VALIDATION", violation.getPattern());
            assertEquals("FAIL", violation.getSeverity());
            assertEquals(tempFile.toString(), violation.getFile());
            assertNotNull(violation.getFixGuidance());
        } finally {
            // Clean up
            java.nio.file.Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void testHyperStandardsValidator() throws Exception {
        // Test with a valid Java file
        String validJava = """
            public class TestClass {
                public void validMethod() {
                    throw new UnsupportedOperationException("Not implemented yet");
                }
            }
            """;

        Path tempFile = Paths.get("test-valid.java");
        java.nio.file.Files.writeString(tempFile, validJava);

        try {
            GuardReceipt receipt = hyperValidator.validateFile(tempFile);

            // Should complete without errors
            assertNotNull(receipt);
            assertEquals("hyper-standards", receipt.getPhase());
            assertNotNull(receipt.getTimestamp());
            assertEquals(1, receipt.getFilesScanned());
        } finally {
            java.nio.file.Files.deleteIfExists(tempFile);
        }
    }

    @Test
    void testGuardViolationProperties() {
        // Test GuardViolation properties
        GuardViolation violation = new GuardViolation(
            "SHACL_TEST",
            "FAIL",
            42,
            "Test violation message"
        );

        assertEquals("SHACL_TEST", violation.getPattern());
        assertEquals("FAIL", violation.getSeverity());
        assertEquals(42, violation.getLine());
        assertEquals("Test violation message", violation.getContent());
        assertNotNull(violation.getFixGuidance());
        assertNotNull(violation.getTimestamp());
    }

    @Test
    void testGuardReceiptProperties() {
        // Test GuardReceipt properties
        GuardReceipt receipt = new GuardReceipt();

        receipt.setPhase("test-phase");
        receipt.setFilesScanned(5);
        receipt.setStatus("GREEN");

        assertEquals("test-phase", receipt.getPhase());
        assertEquals(5, receipt.getFilesScanned());
        assertEquals("GREEN", receipt.getStatus());

        // Test summary
        assertNotNull(receipt.getSummary());
        assertEquals(5, receipt.getSummary().getTotalFiles());
    }

    @Test
    void testShapeLoading() {
        // Test that SHACL shapes are loaded correctly
        assertNotNull(validator);

        // Test saving shapes to a file
        try {
            Path shapesFile = Paths.get("test-shapes.ttl");
            validator.saveShapes(shapesFile);

            // Check that shapes file was created and contains expected content
            assertTrue(java.nio.file.Files.exists(shapesFile));
            String content = java.nio.file.Files.readString(shapesFile);
            assertTrue(content.contains("ex:YAWLElementShape"));

            java.nio.file.Files.deleteIfExists(shapesFile);
        } catch (Exception e) {
            fail("Shape loading failed: " + e.getMessage());
        }
    }

    @Test
    void testMultipleSpecificationFiles() throws Exception {
        // Test validation of multiple files
        String spec1 = """
            @prefix yawl: <http://www.yawlfoundation.org/yawl#> .
            <#workflow1> a yawl:YAWLSpecification .
            """;

        String spec2 = """
            @prefix yawl: <http://www.yawlfoundation.org/yawl#> .
            <#workflow2> a yawl:YAWLSpecification .
            """;

        Path tempDir = Paths.get("test-specs");
        Path spec1File = tempDir.resolve("spec1.ttl");
        Path spec2File = tempDir.resolve("spec2.ttl");

        java.nio.file.Files.createDirectories(tempDir);
        java.nio.file.Files.writeString(spec1File, spec1);
        java.nio.file.Files.writeString(spec2File, spec2);

        try {
            GuardReceipt receipt = validator.validateSpecifications(tempDir);

            // Should process 2 files
            assertEquals(2, receipt.getFilesScanned());
        } finally {
            java.nio.file.Files.deleteIfExists(spec1File);
            java.nio.file.Files.deleteIfExists(spec2File);
            java.nio.file.Files.deleteIfExists(tempDir);
        }
    }
}