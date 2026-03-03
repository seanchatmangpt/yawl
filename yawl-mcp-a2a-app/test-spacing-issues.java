import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

public class test-spacing-issues {
    public static void main(String[] args) {
        try {
            // Load the PatternDemoConfig class
            Class<?> configClass = Class.forName("org.yawlfoundation.yawl.mcp.a2a.demo.config.PatternDemoConfig");
            Method fromCommandLine = configClass.getMethod("fromCommandLine", String[].class);

            // Test 1: timeoutWithSpacesShouldBeParsed - " 120 " should be trimmed
            System.out.println("=== Test 1: timeoutWithSpacesShouldBeParsed ===");
            try {
                String[] args1 = {"-t", " 120 "};
                Object config1 = fromCommandLine.invoke(null, (Object) args1);
                Method timeoutMethod = configClass.getMethod("timeoutSeconds");
                int timeout = (int) timeoutMethod.invoke(config1);
                System.out.println("Timeout: " + timeout);
                if (timeout == 120) {
                    System.out.println("✓ PASS: Timeout with spaces parsed correctly");
                } else {
                    System.out.println("✗ FAIL: Expected 120, got " + timeout);
                }
            } catch (Exception e) {
                System.out.println("✗ FAIL: " + e.getMessage());
            }

            // Test 2: formatWithSpacesShouldBeTrimmed - " JSON " should become JSON
            System.out.println("\n=== Test 2: formatWithSpacesShouldBeTrimmed ===");
            try {
                String[] args2 = {"-f", "  json  "};
                Object config2 = fromCommandLine.invoke(null, (Object) args2);
                Method formatMethod = configClass.getMethod("outputFormat");
                Object format = formatMethod.invoke(config2);
                Class<?> formatClass = Class.forName("org.yawlfoundation.yawl.mcp.a2a.demo.config.PatternDemoConfig$OutputFormat");
                Object jsonFormat = Enum.valueOf((Class<Enum>) formatClass, "JSON");
                if (format.equals(jsonFormat)) {
                    System.out.println("✓ PASS: Format with spaces trimmed correctly");
                } else {
                    System.out.println("✗ FAIL: Expected JSON, got " + format);
                }
            } catch (Exception e) {
                System.out.println("✗ FAIL: " + e.getMessage());
            }

            // Test 3: nullArgsShouldThrow - should throw IllegalArgumentException not NullPointerException
            System.out.println("\n=== Test 3: nullArgsShouldThrow ===");
            try {
                String[] args3 = null;
                fromCommandLine.invoke(null, (Object) args3);
                System.out.println("✗ FAIL: Expected exception, but none was thrown");
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IllegalArgumentException) {
                    System.out.println("✓ PASS: Correctly threw IllegalArgumentException");
                } else if (cause instanceof NullPointerException) {
                    System.out.println("✗ FAIL: Threw NullPointerException instead of IllegalArgumentException");
                } else {
                    System.out.println("✗ FAIL: Threw unexpected exception: " + cause.getClass().getSimpleName());
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}