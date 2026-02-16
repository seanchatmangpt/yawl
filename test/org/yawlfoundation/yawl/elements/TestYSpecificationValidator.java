package org.yawlfoundation.yawl.elements;

import junit.framework.TestCase;
import org.yawlfoundation.yawl.elements.YSpecificationValidator.ValidationError;

import java.util.List;

/**
 * Test cases for YSpecificationValidator.
 *
 * @author YAWL Development Team
 * @since 5.2
 */
public class TestYSpecificationValidator extends TestCase {

    public TestYSpecificationValidator(String name) {
        super(name);
    }

    /**
     * Test that an empty specification fails validation.
     */
    public void testValidateEmptySpecificationFails() {
        YSpecification spec = new YSpecification();
        YSpecificationValidator validator = new YSpecificationValidator(spec);

        boolean isValid = validator.validate();

        assertFalse("Empty specification should fail validation", isValid);
        assertTrue("Should have validation errors", validator.getErrors().size() > 0);
    }

    /**
     * Test that a specification with valid URI passes basic validation.
     */
    public void testValidateSpecificationWithURIPasses() {
        YSpecification spec = new YSpecification("test://spec/uri");
        YSpecificationValidator validator = new YSpecificationValidator(spec);

        validator.validate();
        List<ValidationError> errors = validator.getErrors();

        boolean hasURIError = errors.stream()
            .anyMatch(e -> e.getMessage().contains("URI"));

        assertFalse("Specification with URI should not have URI error", hasURIError);
    }

    /**
     * Test that a specification without a root net fails validation.
     */
    public void testValidateSpecificationWithoutRootNetFails() {
        YSpecification spec = new YSpecification("test://spec/uri");
        YSpecificationValidator validator = new YSpecificationValidator(spec);

        boolean isValid = validator.validate();

        assertFalse("Specification without root net should fail", isValid);

        boolean hasRootNetError = validator.getErrors().stream()
            .anyMatch(e -> e.getMessage().contains("root net"));

        assertTrue("Should have root net error", hasRootNetError);
    }

    /**
     * Test that a specification with a valid root net structure validates.
     */
    public void testValidateSpecificationWithRootNet() {
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

        assertTrue("Specification with complete root net should validate", isValid);
    }

    /**
     * Test that null specification throws exception.
     */
    public void testNullSpecificationThrowsException() {
        try {
            new YSpecificationValidator(null);
            fail("Should throw IllegalArgumentException for null specification");
        }
        catch (IllegalArgumentException e) {
            assertTrue("Exception message should mention null",
                      e.getMessage().contains("null"));
        }
    }

    /**
     * Test validation error properties.
     */
    public void testValidationErrorProperties() {
        ValidationError error = new ValidationError("Test message", "test-code", "element-123");

        assertEquals("Test message", error.getMessage());
        assertEquals("test-code", error.getErrorCode());
        assertEquals("element-123", error.getElementID());
        assertTrue("Timestamp should be set", error.getTimestamp() > 0);
    }

    /**
     * Test validation error toString.
     */
    public void testValidationErrorToString() {
        ValidationError error = new ValidationError("Test error", "ERR-001", "task-1");

        String errorString = error.toString();

        assertTrue("String should contain message", errorString.contains("Test error"));
        assertTrue("String should contain error code", errorString.contains("ERR-001"));
        assertTrue("String should contain element ID", errorString.contains("task-1"));
    }

    /**
     * Test error report generation.
     */
    public void testGetErrorReport() {
        YSpecification spec = new YSpecification();
        YSpecificationValidator validator = new YSpecificationValidator(spec);

        validator.validate();

        String report = validator.getErrorReport();

        assertNotNull("Error report should not be null", report);
        assertTrue("Report should mention errors", report.contains("Error"));
    }

    /**
     * Test error report for valid specification.
     */
    public void testGetErrorReportForValidSpec() {
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

        assertTrue("Report should indicate no errors",
                  report.contains("No validation errors"));
    }

    /**
     * Test that multiple validation runs work correctly.
     */
    public void testMultipleValidationRuns() {
        YSpecification spec = new YSpecification();
        YSpecificationValidator validator = new YSpecificationValidator(spec);

        validator.validate();
        int firstErrorCount = validator.getErrors().size();

        validator.validate();
        int secondErrorCount = validator.getErrors().size();

        assertEquals("Error count should be consistent across runs",
                    firstErrorCount, secondErrorCount);
    }
}
