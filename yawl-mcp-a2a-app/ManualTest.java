import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

public class ManualTest {
    public static void main(String[] args) throws Exception {
        // Load the PatternDemoConfig class
        Class<?> configClass = Class.forName("org.yawlfoundation.yawl.mcp.a2a.demo.config.PatternDemoConfig");
        Method fromCommandLine = configClass.getMethod("fromCommandLine", String[].class);

        // Test 1: timeout with spaces " 120 "
        System.out.println("=== Test 1: timeout with spaces ===");
        try {
            String[] testArgs = {"-t", " 120 "};
            Object config = fromCommandLine.invoke(null, (Object) testArgs);
            Method timeoutMethod = configClass.getMethod("timeoutSeconds");
            int timeout = (int) timeoutMethod.invoke(config);
            System.out.println("Result: " + timeout + (timeout == 120 ? " ✓ PASS" : " ✗ FAIL"));
        } catch (Exception e) {
            System.out.println("✗ FAIL: " + e.getCause().getMessage());
        }

        // Test 2: format with spaces "  json  "
        System.out.println("\n=== Test 2: format with spaces ===");
        try {
            String[] testArgs = {"-f", "  json  "};
            Object config = fromCommandLine.invoke(null, (Object) testArgs);
            Method formatMethod = configClass.getMethod("outputFormat");
            Object format = formatMethod.invoke(config);

            // Get the JSON enum value
            Class<?> formatEnumClass = Class.forName("org.yawlfoundation.yawl.mcp.a2a.demo.config.PatternDemoConfig$OutputFormat");
            Object jsonFormat = Enum.valueOf((Class<Enum>) formatEnumClass, "JSON");

            System.out.println("Result: " + format + (format.equals(jsonFormat) ? " ✓ PASS" : " ✗ FAIL"));
        } catch (Exception e) {
            System.out.println("✗ FAIL: " + e.getCause().getMessage());
        }

        // Test 3: null args
        System.out.println("\n=== Test 3: null args ===");
        try {
            String[] testArgs = null;
            fromCommandLine.invoke(null, (Object) testArgs);
            System.out.println("✗ FAIL: Expected exception but none was thrown");
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) {
                System.out.println("✓ PASS: Correctly threw IllegalArgumentException");
            } else if (cause instanceof NullPointerException) {
                System.out.println("✗ FAIL: Threw NullPointerException instead of IllegalArgumentException");
            } else {
                System.out.println("✗ FAIL: Threw " + cause.getClass().getSimpleName());
            }
        }
    }
}