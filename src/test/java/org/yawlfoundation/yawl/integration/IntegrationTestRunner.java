package org.yawlfoundation.yawl.integration;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.MarketplaceMcpBindingE2ETest;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.QLeverEmbeddedVsHttpTest;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.QLeverErrorRecoveryTest;
import org.yawlfoundation.yawl.integration.autonomous.analytics.WorkflowAnalyticsTest;
import org.yawlfoundation.yawl.integration.autonomous.analytics.WorkflowQueryServiceIntegrationTest;

/**
 * Integration Test Runner for Autonomous Agents Integration.
 *
 * <p>This class provides a central entry point for running all integration tests
 * with common configuration and reporting.</p>
 *
 * <h3>Usage:</h3>
 * <pre>
 * // Run all integration tests
 * junit.textui.TestRunner.run(IntegrationTestSuite.suite());
 *
 * // Run specific test categories
 * junit.textui.TestRunner.run(new IntegrationTestSuite("marketplace"));
 * </pre>
 *
 * @since YAWL 6.0
 */
public class IntegrationTestRunner {

    /**
     * Main method to run tests from command line.
     */
    public static void main(String[] args) {
        // Run all integration tests
        Test suite = IntegrationTestSuite.suite();
        junit.textui.TestRunner.run(suite);

        // Exit with error code if tests failed
        System.exit(junit.textui.TestRunner.run(suite) ? 0 : 1);
    }

    /**
     * Run only marketplace-related integration tests.
     */
    public static void runMarketplaceTests() {
        Test suite = new TestSuite("Marketplace Integration Tests");
        suite.addTestSuite(MarketplaceMcpBindingE2ETest.class);
        suite.addTestSuite(QLeverEmbeddedVsHttpTest.class);
        suite.addTestSuite(QLeverErrorRecoveryTest.class);

        junit.textui.TestRunner.run(suite);
    }

    /**
     * Run only analytics-related integration tests.
     */
    public static void runAnalyticsTests() {
        Test suite = new TestSuite("Analytics Integration Tests");
        suite.addTestSuite(WorkflowQueryServiceIntegrationTest.class);
        suite.addTestSuite(WorkflowAnalyticsTest.class);

        junit.textui.TestRunner.run(suite);
    }

    /**
     * Run all integration tests with custom configuration.
     */
    public static void runAllIntegrationTests() {
        Test suite = IntegrationTestSuite.suite();

        // Configure test execution
        configureTestExecution(suite);

        junit.textui.TestRunner.run(suite);
    }

    /**
     * Configure test execution with common settings.
     */
    private static void configureTestExecution(Test suite) {
        // Configuration would be applied here
        // This could include:
        // - Test timeouts
        // - Parallel execution
        // - Logging configuration
        // - Dependency setup

        System.out.println("Running Integration Test Suite...");
        System.out.println("External Dependencies Required:");
        System.out.println("  - QLever instance on port 7001");
        System.out.println("  - Oxigraph instance on port 19877");
        System.out.println("  - YAWL Native on port 8083");
        System.out.println();
    }
}

/**
 * Integration Test Suite with organized test grouping.
 */
class IntegrationTestSuite extends TestSuite {

    /**
     * Create a suite with all integration tests.
     */
    public static Test suite() {
        TestSuite suite = new TestSuite("Autonomous Agents Integration Tests");

        // Add all test categories
        suite.addTest(createMarketplaceSuite());
        suite.addTest(createAnalyticsSuite());
        suite.addTest(createComparisonSuite());
        suite.addTest(createRecoverySuite());

        return suite;
    }

    /**
     * Create marketplace integration test suite.
     */
    private static Test createMarketplaceSuite() {
        TestSuite suite = new TestSuite("Marketplace Integration");
        suite.addTestSuite(MarketplaceMcpBindingE2ETest.class);
        return suite;
    }

    /**
     * Create analytics integration test suite.
     */
    private static Test createAnalyticsSuite() {
        TestSuite suite = new TestSuite("Analytics Integration");
        suite.addTestSuite(WorkflowQueryServiceIntegrationTest.class);
        suite.addTestSuite(WorkflowAnalyticsTest.class);
        return suite;
    }

    /**
     * Create comparison test suite.
     */
    private static Test createComparisonSuite() {
        TestSuite suite = new TestSuite("Engine Comparison");
        suite.addTestSuite(QLeverEmbeddedVsHttpTest.class);
        return suite;
    }

    /**
     * Create recovery test suite.
     */
    private static Test createRecoverySuite() {
        TestSuite suite = new TestSuite("Error Recovery");
        suite.addTestSuite(QLeverErrorRecoveryTest.class);
        return suite;
    }
}