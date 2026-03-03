import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;

public class PatternTestRunner {
    public static void main(String[] args) throws Exception {
        System.out.println("=== YAWL Pattern Demo Test Runner ===");
        System.out.println("GROQ_API_KEY: " + System.getenv("GROQ_API_KEY"));
        System.out.println("GROQ_MODEL: " + System.getenv("GROQ_MODEL"));
        System.out.println();

        // Load and run PatternDemoRunnerTest
        System.out.println("Running PatternDemoRunnerTest...");
        runTest("org.yawlfoundation.yawl.mcp.a2a.demo.PatternDemoRunnerTest");

        // Load and run PatternDemoConfigTest
        System.out.println("\nRunning PatternDemoConfigTest...");
        runTest("org.yawlfoundation.yawl.mcp.a2a.demo.config.PatternDemoConfigTest");

        System.out.println("\n=== Test Execution Complete ===");
    }

    private static void runTest(String testClassName) throws Exception {
        try {
            Class<?> testClass = Class.forName(testClassName);
            Object testInstance = testClass.getDeclaredConstructor().newInstance();

            Method[] methods = testClass.getDeclaredMethods();
            int passed = 0;
            int failed = 0;

            for (Method method : methods) {
                if (method.isAnnotationPresent(org.junit.jupiter.api.Test.class)) {
                    System.out.println("  Running: " + method.getName());
                    try {
                        method.invoke(testInstance);
                        System.out.println("    ✓ PASSED");
                        passed++;
                    } catch (Exception e) {
                        System.out.println("    ✗ FAILED: " + e.getCause().getMessage());
                        failed++;
                    }
                }
            }

            System.out.println("  Results: " + passed + " passed, " + failed + " failed");
        } catch (ClassNotFoundException e) {
            System.out.println("  Test class not found: " + testClassName);
        }
    }
}