package org.yawlfoundation.yawl.elements;

import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.elements.YSpecificationValidator.ValidationError;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for YSpecificationValidator.
 *
 * @author YAWL Development Team
 * @since 5.2
 */
class TestYSpecificationValidator {

    @Test
    void testValidateEmptySpecificationFails() {
        YSpecification spec = new YSpecification();
        YSpecificationValidator validator = new YSpecificationValidator(spec);

        boolean isValid = validator.validate();

        assertFalse(isValid, "Empty specification should fail validation");
        assertTrue(validator.getErrors().size() > 0, "Should have validation errors");
    }

    @Test
    void testValidateSpecificationWithURIPasses() {
        YSpecification spec = new YSpecification("test://spec/uri");
        YSpecificationValidator validator = new YSpecificationValidator(spec);

        validator.validate();
        List<ValidationError> errors = validator.getErrors();

        boolean hasURIError = errors.stream()
            .anyMatch(e -> e.getMessage().contains("URI"));

        assertFalse(hasURIError, "Specification with URI should not have URI error");
    }

    @Test
    void testValidateSpecificationWithoutRootNetFails() {
        YSpecification spec = new YSpecification("test://spec/uri");
        YSpecificationValidator validator = new YSpecificationValidator(spec);

        boolean isValid = validator.validate();

        assertFalse(isValid, "Specification without root net should fail");

        boolean hasRootNetError = validator.getErrors().stream()
            .anyMatch(e -> e.getMessage().contains("root net"));

        assertTrue(hasRootNetError, "Should have root net error");
    }

    @Test
    void testValidateSpecificationWithRootNet() {
        YSpecification spec = new YSpecification("test://spec/uri");
        YNet rootNet = new YNet("RootNet", spec);

        YInputCondition input = new YInputCondition("InputCondition", rootNet);
        rootNet.setInputCondition(input);

        YOutputCondition output = new YOutputCondition("OutputCondition", rootNet);
        rootNet.setOutputCondition(output);

        spec.setRootNet(rootNet);

        YSpecificationValidator validator = new YSpecificationValidator(spec);
        boolean isValid = validator.validate();

        if (!isValid) {
            System.out.println("Validation errors:");
            for (ValidationError error : validator.getErrors()) {
                System.out.println("  - " + error);
            }
        }

        assertTrue(isValid, "Specification with complete root net should validate");
    }

    @Test
    void testNullSpecificationThrowsException() {
        try {
            new YSpecificationValidator(null);
            fail("Should throw IllegalArgumentException for null specification");
        }
        catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("null"),
                      "Exception message should mention null");
        }
    }

    @Test
    void testValidationErrorProperties() {
        ValidationError error = new ValidationError("Test message", "test-code", "element-123");

        assertEquals("Test message", error.getMessage());
        assertEquals("test-code", error.getErrorCode());
        assertEquals("element-123", error.getElementID());
        assertTrue(error.getTimestamp() > 0, "Timestamp should be set");
    }

    @Test
    void testValidationErrorToString() {
        ValidationError error = new ValidationError("Test error", "ERR-001", "task-1");

        String errorString = error.toString();

        assertTrue(errorString.contains("Test error"), "String should contain message");
        assertTrue(errorString.contains("ERR-001"), "String should contain error code");
        assertTrue(errorString.contains("task-1"), "String should contain element ID");
    }

    @Test
    void testGetErrorReport() {
        YSpecification spec = new YSpecification();
        YSpecificationValidator validator = new YSpecificationValidator(spec);

        validator.validate();

        String report = validator.getErrorReport();

        assertNotNull(report, "Error report should not be null");
        assertTrue(report.contains("Error"), "Report should mention errors");
    }

    @Test
    void testGetErrorReportForValidSpec() {
        YSpecification spec = new YSpecification("test://valid/spec");
        YNet rootNet = new YNet("RootNet", spec);
        YInputCondition input = new YInputCondition("Input", rootNet);
        YOutputCondition output = new YOutputCondition("Output", rootNet);
        rootNet.setInputCondition(input);
        rootNet.setOutputCondition(output);
        spec.setRootNet(rootNet);

        YSpecificationValidator validator = new YSpecificationValidator(spec);
        validator.validate();

        String report = validator.getErrorReport();

        assertTrue(report.contains("No validation errors"),
                  "Report should indicate no errors");
    }

    @Test
    void testMultipleValidationRuns() {
        YSpecification spec = new YSpecification();
        YSpecificationValidator validator = new YSpecificationValidator(spec);

        validator.validate();
        int firstErrorCount = validator.getErrors().size();

        validator.validate();
        int secondErrorCount = validator.getErrors().size();

        assertEquals(firstErrorCount, secondErrorCount,
                    "Error count should be consistent across runs");
    }
}
