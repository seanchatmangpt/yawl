/*
 * Copyright 2026 YAWL Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.yawlfoundation.yawl.ggen.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yawlfoundation.yawl.ggen.validation.model.GuardReceipt;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for hyper-standards guard validation.
 * All code must adhere to Fortune 5 standards - no TODOs, mocks, stubs, or empty methods.
 */
class IntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void testCleanCodePassesValidation() throws IOException {
        // Create a test Java file with clean code (no violations)
        Path testFile = tempDir.resolve("CleanTest.java");
        String content = """
            public class CleanTest {
                public void properImplementation() {
                    throw new UnsupportedOperationException("Proper implementation required - see documentation");
                }

                public String fetchData() {
                    // Real implementation would go here
                    return processData();
                }

                private String processData() {
                    return "processed data";
                }
            }
            """;

        java.nio.file.Files.writeString(testFile, content);

        // Create HyperStandardsValidator
        HyperStandardsValidator validator = new HyperStandardsValidator();

        // Validate the file
        GuardReceipt receipt = validator.validateEmitDir(tempDir);

        // Verify no violations
        assertEquals(0, receipt.getViolations().size());
        assertTrue(receipt.isGreen());
    }

    @Test
    void testRealImplementationRequired() throws IOException {
        // Create a test Java file that requires real implementation
        Path testFile = tempDir.resolve("RealImplTest.java");
        String content = """
            public class RealImplTest {
                // This method needs real implementation
                public void processData() {
                    throw new UnsupportedOperationException(
                        "processData requires real implementation. " +
                        "See API documentation for requirements."
                    );
                }

                // Another method that needs real work
                public String calculateResult() {
                    throw new UnsupportedOperationException(
                        "calculateResult requires real implementation. " +
                        "Consider using algorithm X or Y based on requirements."
                    );
                }
            }
            """;

        java.nio.file.Files.writeString(testFile, content);

        // Create HyperStandardsValidator
        HyperStandardsValidator validator = new HyperStandardsValidator();

        // Validate the file - should pass (no violations)
        GuardReceipt receipt = validator.validateEmitDir(tempDir);

        // Verify no violations
        assertEquals(0, receipt.getViolations().size());
        assertTrue(receipt.isGreen());
    }

    @Test
    void testPatternDetectionWithRealViolations() throws IOException {
        // Create a test Java file with actual violations
        Path testFile = tempDir.resolve("ViolationTest.java");
        String content = """
            public class ViolationTest {
                // This violates H_TODO standard
                public void incompleteMethod() {
                    // Temporary placeholder that violates standards
                }

                public void anotherViolation() {
                    throw new UnsupportedOperationException("Real implementation required");
                }
            }
            """;

        java.nio.file.Files.writeString(testFile, content);

        // Create a specific checker for TODO patterns
        RegexGuardChecker todoChecker = new RegexGuardChecker("H_TODO",
            "//\\s*(TODO|FIXME|XXX|HACK|LATER|FUTURE|@incomplete|@stub|placeholder)");

        // Check the file
        List<GuardViolation> violations = todoChecker.check(testFile);

        // Should detect the violation
        assertTrue(violations.size() >= 1);
        assertTrue(violations.get(0).getContent().contains("incompleteMethod"));
    }

    @Test
    void testEmptyMethodBodyDetection() throws IOException {
        // Create a test Java file with empty method body
        Path testFile = tempDir.resolve("EmptyTest.java");
        String content = """
            public class EmptyTest {
                public void emptyMethod() {
                }  // This violates H_EMPTY standard
            }
            """;

        java.nio.file.Files.writeString(testFile, content);

        // Create a specific checker for empty patterns
        RegexGuardChecker emptyChecker = new RegexGuardChecker("H_EMPTY",
            "^\\s*\\{\\s*\\}\\s*$");

        // Check the file
        List<GuardViolation> violations = emptyChecker.check(testFile);

        // Should detect the violation
        assertEquals(1, violations.size());
        assertTrue(violations.get(0).getContent().contains("emptyMethod"));
    }

    @Test
    void testMultipleViolationsDetection() throws IOException {
        // Create a test Java file with multiple violations
        Path testFile = tempDir.resolve("MultiViolationTest.java");
        String content = """
            public class MultiViolationTest {
                // Violation 1: TODO comment
                public void todoMethod() {
                }

                // Violation 2: Empty method
                public void anotherEmpty() {
                }
            }
            """;

        java.nio.file.Files.writeString(testFile, content);

        // Create HyperStandardsValidator
        HyperStandardsValidator validator = new HyperStandardsValidator();

        // Validate the file
        GuardReceipt receipt = validator.validateEmitDir(tempDir);

        // Should detect multiple violations
        assertTrue(receipt.getViolations().size() >= 2);
        assertTrue(receipt.isRed());
        assertNotNull(receipt.getErrorMessage());
    }
}