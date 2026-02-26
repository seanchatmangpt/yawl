import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

/**
 * Simple test runner to verify YNetRunner edge case tests
 */
public class TestRunner {
    public static void main(String[] args) {
        JUnitCore junit = new JUnitCore();

        // Run the edge case tests
        Result result = junit.run(
            org.yawlfoundation.yawl.engine.YNetRunnerEdgeCaseTest.class
        );

        System.out.println("Tests run: " + result.getRunCount());
        System.out.println("Failures: " + result.getFailureCount());

        if (result.getFailureCount() > 0) {
            System.out.println("\nFailures:");
            for (Failure failure : result.getFailures()) {
                System.out.println("- " + failure.getDescription() + ": " + failure.getMessage());
                failure.getTrace().forEach(System.out::println);
            }
        }

        if (result.wasSuccessful()) {
            System.out.println("\nAll tests passed!");
        } else {
            System.out.println("\nSome tests failed!");
        }
    }
}