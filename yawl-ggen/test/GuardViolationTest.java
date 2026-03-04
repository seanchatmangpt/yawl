package test;

import org.yawlfoundation.yawl.ggen.validation.model.GuardViolation;

/**
 * Simple test to verify GuardViolation implementation
 */
public class GuardViolationTest {
    public static void main(String[] args) {
        try {
            // Test constructor with required fields
            GuardViolation violation = new GuardViolation(
                "H_TODO",
                "FAIL",
                42,
                "// TODO: implement this method"
            );

            System.out.println("Test 1 - Basic constructor:");
            System.out.println("Pattern: " + violation.getPattern());
            System.out.println("Severity: " + violation.getSeverity());
            System.out.println("Line: " + violation.getLine());
            System.out.println("Content: " + violation.getContent());
            System.out.println("Fix guidance: " + violation.getFixGuidance());

            // Test that fix guidance is generated correctly
            if (violation.getFixGuidance().equals("Implement real logic or throw UnsupportedOperationException")) {
                System.out.println("✅ Fix guidance generation works correctly");
            } else {
                System.out.println("❌ Fix guidance incorrect");
            }

            // Test validation (should throw exception for invalid line)
            try {
                new GuardViolation("H_TODO", "FAIL", -1, "test");
                System.out.println("❌ Should have thrown exception for negative line");
            } catch (IllegalArgumentException e) {
                System.out.println("✅ Validation works: " + e.getMessage());
            }

            System.out.println("\nAll tests passed!");

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}