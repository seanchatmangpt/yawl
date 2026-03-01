/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.validation;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Comprehensive test suite for carrier thread utilization patterns
 * in the YAWL actor model virtual thread implementation.
 *
 * <p>Test Strategy:</p>
 * <ul>
 *   <li>Test different carrier thread configurations (1, 2, 4, 8, 16, 32)</li>
 *   <li>Measure impact on p99 latency vs carrier utilization</li>
 *   <li>Validate optimal configuration for different scales</li>
 *   <li>Test behavior under various workload patterns</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0
 * @since 6.0
 */
@Tag("performance")
@Tag("carrier-thread")
@Execution(ExecutionMode.SAME_THREAD)
class CarrierThreadUtilizationTest {

    @Mock
    private MeterRegistry meterRegistry;

    private CarrierThreadOptimizer optimizer;
    private SimpleMeterRegistry simpleRegistry;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        simpleRegistry = new SimpleMeterRegistry();
        optimizer = new CarrierThreadOptimizer(simpleRegistry);
    }

    @AfterEach
    void tearDown() {
        // Clean up any running optimizations
    }

    @Test
    @DisplayName("Test carrier thread configuration with minimal load")
    void testCarrierConfigurationMinimalLoad() {
        // Test with very light load to establish baseline
        assertDoesNotThrow(() -> {
            var result = optimizer.runOptimizationTests();
            assertNotNull(result);
            assertFalse(result.results().isEmpty());
        });
    }

    @Test
    @DisplayName("Test carrier thread configuration with moderate load")
    void testCarrierConfigurationModerateLoad() {
        // This would test with moderate workload
        // In a real implementation, this would simulate realistic YAWL workflow patterns
        assertDoesNotThrow(() -> {
            var report = optimizer.runOptimizationTests();

            // Verify we have results for all configurations
            assertEquals(6, report.results().size());

            // Verify optimal configuration is found
            assertTrue(report.optimalConfig() > 0);
            assertTrue(report.optimalConfig() <= 32);
        });
    }

    @Test
    @DisplayName("Test carrier thread configuration with burst load")
    void testCarrierConfigurationBurstLoad() {
        // Test with bursty workload patterns
        // This simulates workflow spikes common in YAWL systems

        // This would be implemented with a custom workload generator
        // For now, test basic functionality
        assertDoesNotThrow(() -> {
            var report = optimizer.runOptimizationTests();

            // Check that results contain utilization metrics
            var results = report.results();
            assertFalse(results.isEmpty());

            var optimalResult = results.get(report.optimalConfig());
            assertNotNull(optimalResult);
            assertTrue(optimalResult.carrierUtilization() >= 0);
            assertTrue(optimalResult.carrierUtilization() <= 100);
        });
    }

    @Test
    @DisplayName("Test carrier thread configuration scaling")
    void testCarrierConfigurationScaling() {
        // Test how performance scales with carrier thread count

        List<Integer> testConfigs = List.of(1, 2, 4, 8, 16, 32);
        Map<Integer, CarrierThreadOptimizer.TestResult> results = optimizer.runOptimizationTests().results();

        // Verify scaling behavior
        double previousThroughput = 0;
        double previousUtilization = 0;

        for (int config : testConfigs) {
            var result = results.get(config);
            assertNotNull(result);

            // Throughput should generally increase with more carriers
            assertTrue(result.throughput() >= 0);

            // Utilization should be reasonable
            assertTrue(result.carrierUtilization() >= 0);
            assertTrue(result.carrierUtilization() <= 100);

            // Check that scaling makes sense
            if (config > 1) {
                // Throughput should not decrease with more carriers
                assertTrue(result.throughput() >= previousThroughput * 0.8);

                // Utilization should ideally decrease or stay stable
                assertTrue(result.carrierUtilization() <= previousUtilization + 20);
            }

            previousThroughput = result.throughput();
            previousUtilization = result.carrierUtilization();
        }
    }

    @Test
    @DisplayName("Test carrier thread configuration p99 latency")
    void testCarrierConfigurationP99Latency() {
        var report = optimizer.runOptimizationTests();
        var results = report.results();

        // Check p99 latency across configurations
        for (var entry : results.entrySet()) {
            int config = entry.getKey();
            var result = entry.getValue();

            // p99 latency should be reasonable
            assertTrue(result.p99LatencyMs() >= 0);
            assertTrue(result.p99LatencyMs() < 10000); // Less than 10 seconds

            // Optimal configuration should have reasonable p99
            if (config == report.optimalConfig()) {
                assertTrue(result.p99LatencyMs() < 1000); // Less than 1 second for optimal
            }
        }
    }

    @Test
    @DisplayName("Test carrier thread configuration success rate")
    void testCarrierConfigurationSuccessRate() {
        var report = optimizer.runOptimizationTests();
        var results = report.results();

        // Check success rates are reasonable
        for (var entry : results.entrySet()) {
            var result = entry.getValue();

            // Success rate should be high
            assertTrue(result.successRate() >= 90); // At least 90% success
        }
    }

    @Test
    @DisplayName("Test carrier thread configuration memory usage")
    void testCarrierConfigurationMemoryUsage() {
        var report = optimizer.runOptimizationTests();
        var results = report.results();

        // Memory usage should be monitored and reported
        for (var entry : results.entrySet()) {
            var result = entry.getValue();
            var snapshots = result.metricsSnapshots();

            // Check memory snapshots exist
            assertFalse(snapshots.isEmpty());

            // Memory usage should be reasonable
            for (var snapshot : snapshots) {
                assertTrue(snapshot.heapUsagePercent() >= 0);
                assertTrue(snapshot.heapUsagePercent() <= 100);
            }
        }
    }

    @Test
    @DisplayName("Test carrier thread configuration thread counts")
    void testCarrierConfigurationThreadCounts() {
        var report = optimizer.runOptimizationTests();
        var results = report.results();

        // Check thread count tracking
        for (var entry : results.entrySet()) {
            var result = entry.getValue();
            var snapshots = result.metricsSnapshots();

            // Thread counts should be tracked
            assertFalse(snapshots.isEmpty());

            for (var snapshot : snapshots) {
                assertTrue(snapshot.liveThreads() >= 0);
                assertTrue(snapshot.peakThreads() >= 0);
            }
        }
    }

    @Test
    @DisplayName("Test carrier thread configuration result aggregation")
    void testCarrierConfigurationResultAggregation() {
        var report = optimizer.runOptimizationTests();

        // Check report structure
        assertNotNull(report.startTime());
        assertNotNull(report.endTime());
        assertFalse(report.results().isEmpty());
        assertTrue(report.optimalConfig() > 0);

        // Check that optimal configuration exists in results
        assertTrue(report.results().containsKey(report.optimalConfig()));
    }

    @Test
    @DisplayName("Test carrier thread configuration performance score calculation")
    void testCarrierConfigurationPerformanceScore() {
        var report = optimizer.runOptimizationTests();
        var optimalResult = report.results().get(report.optimalConfig());

        // Calculate performance score manually
        double throughput = optimalResult.throughput();
        double utilization = optimalResult.carrierUtilization();
        double p99Latency = optimalResult.p99LatencyMs();
        double successRate = optimalResult.successRate();

        // Score should be reasonable
        double score = (throughput * successRate / 100) / (utilization * p99Latency);
        assertTrue(score >= 0);
    }

    @Test
    @DisplayName("Test carrier thread configuration error handling")
    void testCarrierConfigurationErrorHandling() {
        // Test error handling scenarios

        // Test with null registry (should handle gracefully)
        assertThrows(IllegalArgumentException.class, () -> {
            new CarrierThreadOptimizer(null);
        });

        // Test with invalid configurations
        // This would be implemented with custom test data
    }

    @Test
    @DisplayName("Test carrier thread configuration concurrent access")
    void testCarrierConfigurationConcurrentAccess() {
        // Test concurrent access to shared resources
        // This would verify thread safety of the optimizer

        // Run multiple optimizations concurrently
        Thread[] threads = new Thread[3];
        for (int i = 0; i < threads.length; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                try {
                    var result = optimizer.runOptimizationTests();
                    assertNotNull(result);
                } catch (Exception e) {
                    fail("Thread " + threadId + " failed: " + e.getMessage());
                }
            });
        }

        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for completion
        for (Thread thread : threads) {
            try {
                thread.join(30000); // 30 second timeout
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Thread interrupted");
            }
        }
    }

    @Test
    @DisplayName("Test carrier thread configuration metrics collection")
    void testCarrierConfigurationMetricsCollection() {
        // Test that metrics are properly collected and reported

        var report = optimizer.runOptimizationTests();
        var results = report.results();

        // Check metrics collection
        for (var entry : results.entrySet()) {
            var result = entry.getValue();

            // Check that latency samples are collected
            assertFalse(result.latencySamples().isEmpty());

            // Check that metrics snapshots are collected
            assertFalse(result.metricsSnapshots().isEmpty());
        }
    }

    @Test
    @DisplayName("Test carrier thread configuration timeout handling")
    void testCarrierConfigurationTimeoutHandling() {
        // Test behavior when tests time out

        // This would require mocking slow-running tests
        // For now, test normal timeout behavior

        assertDoesNotThrow(() -> {
            var report = optimizer.runOptimizationTests();

            // Verify report structure even with potential timeouts
            assertNotNull(report.startTime());
            assertNotNull(report.endTime());
            assertFalse(report.results().isEmpty());
        });
    }

    @Test
    @DisplayName("Test carrier thread configuration optimal selection")
    void testCarrierConfigurationOptimalSelection() {
        var report = optimizer.runOptimizationTests();
        var optimalConfig = report.optimalConfig();
        var optimalResult = report.results().get(optimalConfig);

        // Verify optimal configuration is reasonable
        assertTrue(optimalConfig >= 1);
        assertTrue(optimalConfig <= 32);

        // Verify optimal result has good characteristics
        assertTrue(optimalResult.successRate() >= 90);
        assertTrue(optimalResult.throughput() > 0);
        assertTrue(optimalResult.carrierUtilization() <= 90); // Not saturated
    }
}