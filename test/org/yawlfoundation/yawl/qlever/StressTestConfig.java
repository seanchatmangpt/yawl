package org.yawlfoundation.yawl.qlever;

/**
 * Configuration class for stress testing parameters.
 * Centralizes all test configuration to make it easier to adjust.
 */
public class StressTestConfig {

    /**
     * Thread count configurations for scale testing
     */
    public static final int[] SCALE_TEST_THREADS = {10, 50, 100, 200, 500, 1000};

    /**
     * Query types for testing
     */
    public static final String[] QUERIES = {
        // Simple queries
        "SELECT * FROM YTask WHERE status = 'running'",
        "SELECT * FROM YTask WHERE caseID = 'case123'",
        "SELECT caseID, status FROM YTask",

        // Medium complexity queries
        "SELECT t.* FROM YTask t JOIN YCase c ON t.caseID = c.caseID WHERE c.status = 'active'",
        "SELECT t.*, d.* FROM YTask t JOIN YData d ON t.caseID = d.caseID WHERE t.priority > 5",
        "SELECT caseID, COUNT(*) as taskCount FROM YTask GROUP BY caseID",

        // Complex queries
        "SELECT t.*, c.*, d.* FROM YTask t JOIN YCase c ON t.caseID = c.caseID JOIN YData d ON t.caseID = d.caseID WHERE t.dueDate < NOW() AND c.priority > 5",
        "SELECT c.caseID, c.status, COUNT(t.taskID) as taskCount, AVG(t.executionTime) as avgTime FROM YCase c LEFT JOIN YTask t ON c.caseID = t.caseID GROUP BY c.caseID, c.status",
        "SELECT t.* FROM YTask t WHERE EXISTS (SELECT 1 FROM YData d WHERE d.caseID = t.caseID AND d.value > 1000) AND t.status != 'completed'"
    };

    /**
     * Test durations in seconds
     */
    public static final int[] TEST_DURATIONS = {
        5,   // Very short quick tests
        10,  // Short tests
        30,  // Medium tests
        60,  // Long tests
        120  // Very long tests
    };

    /**
     * Workload mix configurations (percentage of read vs metadata queries)
     */
    public static final int[] READ_PERCENTAGES = {70, 80, 90, 95};

    /**
     * Performance thresholds
     */
    public static class Thresholds {
        // Latency thresholds in milliseconds
        public static final int P50_LATENCY_MS = 50;
        public static final int P95_LATENCY_MS = 200;
        public static final int P99_LATENCY_MS = 500;

        // Throughput thresholds
        public static final double MIN_THROUGHPUT_QPS = 10.0;
        public static final double OPTIMAL_THROUGHPUT_QPS = 100.0;

        // Error rate thresholds
        public static final double MAX_ERROR_RATE = 0.05; // 5%
        public static final double CRITICAL_ERROR_RATE = 0.20; // 20%

        // Memory thresholds
        public static final long MAX_MEMORY_GROWTH_MB = 100;
        public static final long CRITICAL_MEMORY_GROWTH_MB = 500;

        // Resource thresholds
        public static final int MAX_OPEN_HANDLES = 1000;
        public static final int CRITICAL_OPEN_HANDLES = 5000;
    }

    /**
     * Timeout configurations
     */
    public static class Timeouts {
        // Query timeout in milliseconds
        public static final long QUERY_TIMEOUT_MS = 10000; // 10 seconds
        public static final long SHORT_QUERY_TIMEOUT_MS = 2000; // 2 seconds
        public static final long LONG_QUERY_TIMEOUT_MS = 30000; // 30 seconds

        // Test timeouts
        public static final long STRESS_TEST_TIMEOUT_MS = 300000; // 5 minutes
        public static final long RECOVERY_TEST_TIMEOUT_MS = 60000; // 1 minute
    }

    /**
     * Test report configurations
     */
    public static class Reporting {
        public static final String REPORT_DIRECTORY = "stress-test-reports";
        public static final String TIMESTAMP_FORMAT = "yyyy-MM-dd_HH-mm-ss";
        public static final boolean DETAILED_LOGGING = true;
        public static final boolean INCLUDE_LATENCY_HISTOGRAM = true;
        public static final boolean INCLUDE_MEMORY_SNAPSHOTS = true;
    }

    /**
     * Load test patterns
     */
    public static class LoadPatterns {
        // Ramping up load
        public static final String RAMP_UP = "ramp-up";

        // Constant load
        public static final String CONSTANT = "constant";

        // Burst load
        public static final String BURST = "burst";

        // Spike load
        public static final String SPIKE = "spike";

        // Wave pattern (alternating high/low load)
        public static final String WAVE = "wave";
    }

    /**
     * Resource types to monitor
     */
    public static class ResourceTypes {
        public static final String MEMORY = "memory";
        public static final String CPU = "cpu";
        public static final String THREADS = "threads";
        public static final String OPEN_CONNECTIONS = "open-connections";
        public static final String QUERY_EXECUTIONS = "query-executions";
    }

    /**
     * Alert levels
     */
    public static class AlertLevels {
        public static final String INFO = "info";
        public static final String WARNING = "warning";
        public static final String ERROR = "error";
        public static final String CRITICAL = "critical";
    }

    /**
     * Configuration for custom test scenarios
     */
    public static class CustomTests {
        // Test to simulate production workload
        public static final boolean PRODUCTION_SIMULATION_ENABLED = true;
        public static final int PRODUCTION_QUERY_VARIETY = 10;
        public static final int PRODUCTION_CONCURRENT_USERS = 100;

        // Test for database connection handling
        public static final boolean CONNECTION_POOL_TEST_ENABLED = true;
        public static final int CONNECTION_POOL_SIZE = 50;

        // Test for query optimization
        public static final boolean QUERY_OPTIMIZATION_TEST_ENABLED = true;
        public static final String[] OPTIMIZATION_TEST_QUERIES = {
            "SELECT * FROM YTask WHERE status = 'running' AND createdDate > '2024-01-01'",
            "SELECT t.* FROM YTask t JOIN YCase c ON t.caseID = c.caseID WHERE c.priority = 'high'"
        };
    }

    /**
     * Configuration for breaking point detection
     */
    public static class BreakingPointDetection {
        // Thresholds for detecting breaking points
        public static final double THROUGHPUT_DROP_THRESHOLD = 0.2; // 20% drop
        public static final double LATENCY_INCREASE_THRESHOLD = 2.0; // 2x increase
        public static final double ERROR_RATE_INCREASE_THRESHOLD = 0.1; // 10% increase

        // Number of consecutive measurements to confirm breaking point
        public static final int CONFIRMATION_INTERVALS = 3;

        // Minimum load to start breaking point detection
        public static final int MIN_BREAKING_POINT_LOAD = 100;
    }

    /**
     * Configuration for recovery testing
     */
    public static class RecoveryTesting {
        // How long to wait before testing recovery
        public static final long RECOVERY_GRACE_PERIOD_MS = 5000;

        // Whether to test recovery multiple times
        public static final boolean MULTI_RECOVERY_TEST_ENABLED = true;
        public static final int RECOVERY_TEST_ITERATIONS = 3;

        // Recovery success criteria
        public static final double RECOVERY_SUCCESS_THRESHOLD = 0.8; // 80% of baseline
    }

    /**
     * Configuration for continuous integration
     */
    public static class CIConfig {
        // Maximum acceptable performance regression
        public static final double MAX_THROUGHPUT_REGRESSION = 0.1; // 10%
        public static final double MAX_LATENCY_INCREASE = 0.2; // 20%

        // Whether to run stress tests in CI
        public static final boolean ENABLE_STRESS_TESTS_IN_CI = false;

        // Maximum test duration in CI
        public static final long MAX_TEST_DURATION_MS = 120000; // 2 minutes
    }

    /**
     * Gets a description of the configuration
     */
    public static String getDescription() {
        return String.format(
            "Stress Test Configuration\n" +
            "========================\n" +
            "Scale Test Threads: %s\n" +
            "Number of Query Types: %d\n" +
            "Test Durations: %s seconds\n" +
            "Read Percentages: %s%%\n" +
            "P50 Latency Threshold: %d ms\n" +
            "P95 Latency Threshold: %d ms\n" +
            "Max Error Rate: %.1f%%\n" +
            "Query Timeout: %d ms\n" +
            "Max Memory Growth: %d MB",
            String.join(", ", Arrays.stream(SCALE_TEST_THREADS).mapToObj(String::valueOf).toArray(String[]::new)),
            QUERIES.length,
            Arrays.toString(TEST_DURATIONS),
            Arrays.toString(READ_PERCENTAGES),
            Thresholds.P50_LATENCY_MS,
            Thresholds.P95_LATENCY_MS,
            Thresholds.MAX_ERROR_RATE * 100,
            Timeouts.QUERY_TIMEOUT_MS,
            Thresholds.MAX_MEMORY_GROWTH_MB
        );
    }
}