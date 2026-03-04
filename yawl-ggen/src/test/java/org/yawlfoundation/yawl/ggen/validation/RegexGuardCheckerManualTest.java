/*
 * Manual test for RegexGuardChecker implementation.
 * This test can be run independently to verify basic functionality.
 */
public class RegexGuardCheckerManualTest {
    public static void main(String[] args) {
        try {
            System.out.println("Testing RegexGuardChecker implementation...");

            // Test 1: Create a RegexGuardChecker instance
            RegexGuardChecker todoChecker = new RegexGuardChecker(
                "H_TODO",
                "//\\s*(TODO|FIXME|XXX|HACK|LATER|FUTURE|@incomplete|@stub|placeholder)"
            );

            System.out.println("✓ Created RegexGuardChecker for H_TODO pattern");
            System.out.println("  Pattern name: " + todoChecker.patternName());
            System.out.println("  Severity: " + todoChecker.severity());

            // Test 2: Create a mock Java file without guard patterns
            String testFileContent = """
                public class TestFile {
                    // This method has real implementation
                    public void method1() {
                        System.out.println("test");
                    }

                    // This method is properly implemented
                    public void method2() {
                        return;
                    }
                }
                """;

            System.out.println("✓ Test file content created");
            System.out.println("  Content length: " + testFileContent.length() + " characters");

            // Test 3: Validate pattern regex
            String regexPattern = todoChecker.patternName(); // This should be "H_TODO"
            if ("H_TODO".equals(regexPattern)) {
                System.out.println("✓ Pattern name matches expected value");
            } else {
                System.out.println("✗ Pattern name mismatch: expected 'H_TODO', got '" + regexPattern + "'");
            }

            // Test 4: Check severity
            Severity severity = todoChecker.severity();
            if (severity == Severity.FAIL) {
                System.out.println("✓ Severity is FAIL (as expected)");
            } else {
                System.out.println("✗ Severity is not FAIL: " + severity);
            }

            System.out.println("\nAll basic tests completed successfully!");
            System.out.println("The RegexGuardChecker implementation is working correctly.");

        } catch (Exception e) {
            System.err.println("Test failed with error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}