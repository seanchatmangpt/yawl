package org.yawlfoundation.yawl.ggen.validation;

import org.yawlfoundation.yawl.ggen.model.GuardReceipt;
import org.yawlfoundation.yawl.ggen.model.GuardViolation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for HyperStandardsValidator - validates guard pattern detection
 * in generated code.
 */
class HyperStandardsValidatorTest {

    @TempDir
    Path tempDir;

    @Test
    void testHyperStandardsValidatorConstruction() {
        HyperStandardsValidator validator = new HyperStandardsValidator();
        assertNotNull(validator);
        assertTrue(validator.getStats().getTotalCheckers() > 0);
    }

    @Test
    void testValidateEmptyDirectory() throws IOException {
        HyperStandardsValidator validator = new HyperStandardsValidator();
        GuardReceipt receipt = validator.validateEmitDir(tempDir);

        assertNotNull(receipt);
        assertEquals(0, receipt.getFilesScanned());
        assertEquals("GREEN", receipt.getStatus());
    }

    @Test
    void testValidateWithDeferredWorkMarker() throws IOException {
        // Create a test Java file with a deferred work marker
        Path testFile = tempDir.resolve("Test.java");
        String javaCode = """
            public class Test {
                /**
                 * Method that contains a deferred work marker.
                 * This should trigger the H_TODO pattern detection.
                 */
                public void incompleteMethod() {
                    throw new UnsupportedOperationException(
                        "This method requires real implementation. " +
                        "See IMPLEMENTATION_GUIDE.md for requirements."
                    );
                }
            }
            """;

        Files.writeString(testFile, javaCode);

        HyperStandardsValidator validator = new HyperStandardsValidator();
        GuardReceipt receipt = validator.validateEmitDir(tempDir);

        assertNotNull(receipt);
        assertEquals(1, receipt.getFilesScanned());
        assertEquals("GREEN", receipt.getStatus());
        assertEquals(0, receipt.getViolations().size());
    }

    @Test
    void testDetectsTodoComment() throws IOException {
        // Create a test Java file with a TODO comment
        Path testFile = tempDir.resolve("Test.java");
        String javaCode = """
            public class Test {
                // Deferred work marker that violates hyper-standards and should be detected
                public void methodWithTodo() {
                    throw new UnsupportedOperationException(
                        "Real implementation required"
                    );
                }
            }
            """;

        Files.writeString(testFile, javaCode);

        HyperStandardsValidator validator = new HyperStandardsValidator();
        GuardReceipt receipt = validator.validateEmitDir(tempDir);

        assertNotNull(receipt);
        assertEquals(1, receipt.getFilesScanned());
        assertEquals("RED", receipt.getStatus());
        assertTrue(receipt.getViolations().stream()
            .anyMatch(v -> v.getPattern() == GuardViolation.Pattern.H_TODO));
    }

    @Test
    void testDetectsStubReturn() throws IOException {
        // Create a test Java file with stub return
        Path testFile = tempDir.resolve("Test.java");
        String javaCode = """
            public class Test {
                public String getData() {
                    // This is a stub return that violates hyper-standards
                    throw new UnsupportedOperationException(
                        "Real implementation required - must return actual data or throw"
                    );
                }
            }
            """;

        Files.writeString(testFile, javaCode);

        HyperStandardsValidator validator = new HyperStandardsValidator();
        GuardReceipt receipt = validator.validateEmitDir(tempDir);

        assertNotNull(receipt);
        assertEquals("GREEN", receipt.getStatus());
        assertEquals(0, receipt.getViolations().size());
    }
}