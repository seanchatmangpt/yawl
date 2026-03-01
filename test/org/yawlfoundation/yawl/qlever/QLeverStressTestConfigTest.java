package org.yawlfoundation.yawl.qlever;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for StressTestConfig to verify all configurations are properly set up.
 */
public class QLeverStressTestConfigTest {

    @Test
    @DisplayName("Verify StressTestConfig Configuration")
    void testStressTestConfig() {
        // Test thread count configuration
        int[] threadCounts = StressTestConfig.SCALE_TEST_THREADS;
        assertNotNull(threadCounts, "Thread count array should not be null");
        assertTrue(threadCounts.length > 0, "Should have thread count configurations");

        // Verify thread counts are in ascending order
        for (int i = 1; i < threadCounts.length; i++) {
            assertTrue(threadCounts[i] > threadCounts[i-1],
                "Thread counts should be in ascending order");
        }

        // Test query configurations
        String[] queries = StressTestConfig.QUERIES;
        assertNotNull(queries, "Queries array should not be null");
        assertTrue(queries.length > 0, "Should have query configurations");

        // Verify all queries contain YAWL-related keywords
        for (String query : queries) {
            assertNotNull(query, "Query should not be null");
            assertFalse(query.trim().isEmpty(), "Query should not be empty");
            assertTrue(query.contains("YTask") || query.contains("YCase") || query.contains("YData"),
                "Query should contain YAWL table references");
        }

        // Test duration configurations
        int[] durations = StressTestConfig.TEST_DURATIONS;
        assertNotNull(durations, "Durations array should not be null");
        assertTrue(durations.length > 0, "Should have duration configurations");

        // Test threshold configurations
        assertTrue(StressTestConfig.Thresholds.P50_LATENCY_MS > 0,
            "P50 latency should be positive");
        assertTrue(StressTestConfig.Thresholds.P95_LATENCY_MS > 0,
            "P95 latency should be positive");
        assertTrue(StressTestConfig.Thresholds.P99_LATENCY_MS > 0,
            "P99 latency should be positive");
        assertTrue(StressTestConfig.Thresholds.MAX_ERROR_RATE > 0,
            "Max error rate should be positive");
        assertTrue(StressTestConfig.Thresholds.MAX_ERROR_RATE < 1,
            "Max error rate should be less than 1");

        // Test timeout configurations
        assertTrue(StressTestConfig.Timeouts.QUERY_TIMEOUT_MS > 0,
            "Query timeout should be positive");
        assertTrue(StressTestConfig.Timeouts.SHORT_QUERY_TIMEOUT_MS > 0,
            "Short query timeout should be positive");
        assertTrue(StressTestConfig.Timeouts.LONG_QUERY_TIMEOUT_MS > 0,
            "Long query timeout should be positive");
    }

    @Test
    @DisplayName("Verify Configuration String Representation")
    void testConfigDescription() {
        String description = StressTestConfig.getDescription();
        assertNotNull(description, "Config description should not be null");
        assertFalse(description.trim().isEmpty(), "Config description should not be empty");
        assertTrue(description.contains("Stress Test Configuration"),
            "Description should contain title");
    }

    @Test
    @DisplayName("Verify Custom Test Configurations")
    void testCustomTestConfigurations() {
        // Test production simulation configuration
        assertTrue(StressTestConfig.CustomTests.PRODUCTION_SIMULATION_ENABLED,
            "Production simulation should be enabled");
        assertTrue(StressTestConfig.CustomTests.PRODUCTION_QUERY_VARIETY > 0,
            "Production query variety should be positive");
        assertTrue(StressTestConfig.CustomTests.PRODUCTION_CONCURRENT_USERS > 0,
            "Production concurrent users should be positive");

        // Test connection pool configuration
        assertTrue(StressTestConfig.CustomTests.CONNECTION_POOL_TEST_ENABLED,
            "Connection pool test should be enabled");
        assertTrue(StressTestConfig.CustomTests.CONNECTION_POOL_SIZE > 0,
            "Connection pool size should be positive");

        // Test query optimization configuration
        assertTrue(StressTestConfig.CustomTests.QUERY_OPTIMIZATION_TEST_ENABLED,
            "Query optimization test should be enabled");
        assertNotNull(StressTestConfig.CustomTests.OPTIMIZATION_TEST_QUERIES,
            "Optimization test queries should not be null");
        assertTrue(StressTestConfig.CustomTests.OPTIMIZATION_TEST_QUERIES.length > 0,
            "Should have optimization test queries");
    }

    @Test
    @DisplayName("Verify Breaking Point Detection Configuration")
    void testBreakingPointDetectionConfiguration() {
        assertTrue(StressTestConfig.BreakingPointDetection.THROUGHPUT_DROP_THRESHOLD > 0,
            "Throughput drop threshold should be positive");
        assertTrue(StressTestConfig.BreakingPointDetection.LATENCY_INCREASE_THRESHOLD > 1,
            "Latency increase threshold should be > 1");
        assertTrue(StressTestConfig.BreakingPointDetection.CONFIRMATION_INTERVALS > 0,
            "Confirmation intervals should be positive");
        assertTrue(StressTestConfig.BreakingPointDetection.MIN_BREAKING_POINT_LOAD > 0,
            "Min breaking point load should be positive");
    }

    @Test
    @DisplayName("Verify Recovery Testing Configuration")
    void testRecoveryTestingConfiguration() {
        assertTrue(StressTestConfig.RecoveryTesting.RECOVERY_GRACE_PERIOD_MS > 0,
            "Recovery grace period should be positive");
        assertTrue(StressTestConfig.RecoveryTesting.RECOVERY_SUCCESS_THRESHOLD > 0,
            "Recovery success threshold should be positive");
        assertTrue(StressTestConfig.RecoveryTesting.RECOVERY_SUCCESS_THRESHOLD < 1,
            "Recovery success threshold should be < 1");
    }

    @Test
    @DisplayName("Verify CI Configuration")
    void testCIConfiguration() {
        assertTrue(StressTestConfig.CIConfig.MAX_THROUGHPUT_REGRESSION > 0,
            "Max throughput regression should be positive");
        assertTrue(StressTestConfig.CIConfig.MAX_THROUGHPUT_REGRESSION < 1,
            "Max throughput regression should be < 1");
        assertTrue(StressTestConfig.CIConfig.MAX_LATENCY_INCREASE > 0,
            "Max latency increase should be positive");
    }
}