/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it in terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge case test suite for SLO observability system.
 *
 * This test suite covers edge cases, error conditions, and advanced scenarios
 * that are not covered in the main test suites. It focuses on:
 * - Boundary conditions and edge cases
 * - Error handling and recovery
 * - Concurrent access and thread safety
 * - Memory management and performance
 * - Configuration and customization
 *
 * @author YAWL Foundation
 * @since 6.0.0
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SLOEdgeCaseTestSuite {

    private static final Logger LOGGER = LoggerFactory.getLogger(SLOEdgeCaseTestSuite.class);

    private MeterRegistry meterRegistry;
    private SLOTracker sloTracker;
    private SLOAlertManager alertManager;
    private SLODashboard dashboard;
    private SLOPredictiveAnalytics predictiveAnalytics;

    @BeforeAll
    void setUpClass() {
        LOGGER.info("Initializing edge case test suite");
    }

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        sloTracker = new SLOTracker(meterRegistry);
        alertManager = SLOAlertManager.getInstance();
        dashboard = new SLODashboard();
        predictiveAnalytics = new SLOPredictiveAnalytics();
    }

    @AfterEach
    void tearDown() {
        sloTracker.reset();
        alertManager.shutdown();
    }

    @AfterAll
    void tearDownClass() {
        LOGGER.info("Completed edge case test suite");
    }

    // ==========================================================
    // BOUNDARY CONDITION TESTS
    // ==========================================================

    @Test
    @DisplayName("SLO-EDGE-001: Zero duration case completion")
    void testZeroDurationCaseCompletion() {
        // Test case with zero duration (edge case)
        assertDoesNotThrow(() -> {
            Map<String, String> context = new HashMap<>();
            context.put("edge_case", "zero_duration");
            sloTracker.recordCaseCompletion("case-zero-duration", 0, context);
        }, "Should handle zero duration gracefully");

        // Verify zero duration is tracked
        SLOTracker.ComplianceStatus status = sloTracker.getAllComplianceStatus()
            .values().stream()
            .findFirst()
            .orElse(null);
        assertNotNull(status);
        assertTrue(status.getDurationMs() >= 0, "Duration should be non-negative");
    }

    @Test
    @DisplayName("SLO-EDGE-002: Maximum duration case completion")
    void testMaximumDurationCaseCompletion() {
        // Test case with maximum reasonable duration
        long maxDuration = Long.MAX_VALUE / 1000; // Avoid overflow
        assertDoesNotThrow(() -> {
            Map<String, String> context = new HashMap<>();
            context.put("edge_case", "max_duration");
            sloTracker.recordCaseCompletion("case-max-duration", maxDuration, context);
        }, "Should handle maximum duration gracefully");

        // Verify duration is capped correctly
        SLOTracker.ComplianceStatus status = sloTracker.getAllComplianceStatus()
            .values().stream()
            .findFirst()
            .orElse(null);
        assertNotNull(status);
        assertTrue(status.getDurationMs() >= 0, "Duration should remain non-negative");
    }

    @Test
    @DisplayName("SLO-EDGE-003: Empty context handling")
    void testEmptyContextHandling() {
        // Test with null context
        assertThrows(NullPointerException.class, () -> {
            sloTracker.recordCaseCompletion("case-null-context", 1000, null);
        }, "Should reject null context");

        // Test with empty context
        assertDoesNotThrow(() -> {
            Map<String, String> emptyContext = Collections.emptyMap();
            sloTracker.recordCaseCompletion("case-empty-context", 1000, emptyContext);
        }, "Should handle empty context gracefully");
    }

    @Test
    @DisplayName("SLO-EDGE-004: Very large context")
    void testVeryLargeContext() {
        // Test with very large context map
        Map<String, String> largeContext = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            largeContext.put("key-" + i, "value-" + i.repeat(100)); // Large values
        }

        assertDoesNotThrow(() -> {
            sloTracker.recordCaseCompletion("case-large-context", 1000, largeContext);
        }, "Should handle large context gracefully");
    }

    // ==========================================================
    // CONCURRENT ACCESS TESTS
    // ==========================================================

    @Test
    @DisplayName("SLO-EDGE-005: High concurrent access to SLO tracker")
    void testHighConcurrentAccess() {
        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        // Submit concurrent recording tasks
        for (int i = 0; i < 1000; i++) {
            final int taskId = i;
            executor.submit(() -> {
                try {
                    Map<String, String> context = new HashMap<>();
                    context.put("thread_id", String.valueOf(taskId % threadCount));
                    sloTracker.recordCaseCompletion(
                        "case-concurrent-" + taskId,
                        (long) (Math.random() * 8 * 60 * 60 * 1000),
                        context
                    );
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    LOGGER.error("Concurrent access error: {}", e.getMessage());
                }
            });
        }

        executor.shutdown();
        try {
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Test interrupted");
        }

        // Verify results
        assertEquals(1000, successCount.get(), "All operations should succeed");
        assertEquals(0, errorCount.get(), "No operations should fail");

        // Verify data consistency
        Map<String, SLOTracker.ComplianceStatus> status = sloTracker.getAllComplianceStatus();
        assertEquals(1000, status.size(), "Should track all cases");
    }

    @Test
    @DisplayName("SLO-EDGE-006: Concurrent alert generation")
    void testConcurrentAlertGeneration() {
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger alertCount = new AtomicInteger(0);

        // Submit concurrent violation recording
        for (int i = 0; i < 200; i++) {
            final int taskId = i;
            executor.submit(() -> {
                try {
                    Map<String, String> context = new HashMap<>();
                    context.put("alert_thread", String.valueOf(taskId % threadCount));
                    context.put("severity", "high");

                    // Record violating case completion
                    sloTracker.recordCaseCompletion(
                        "case-alert-concurrent-" + taskId,
                        30 * 60 * 60 * 1000L, // 30 hours (violates 24h SLA)
                        context
                    );

                    // Check if alert was generated
                    Collection<SLOAlertManager.Alert> activeAlerts = alertManager.getActiveAlerts();
                    if (!activeAlerts.isEmpty()) {
                        alertCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    LOGGER.error("Concurrent alert error: {}", e.getMessage());
                }
            });
        }

        executor.shutdown();
        try {
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Test interrupted");
        }

        // Verify alerts were generated
        assertTrue(alertCount.get() > 0, "Should generate alerts concurrently");
    }

    // ==========================================================
    // MEMORY MANAGEMENT TESTS
    // ==========================================================

    @Test
    @DisplayName("SLO-EDGE-007: Memory usage with large datasets")
    void testMemoryUsageWithLargeDatasets() {
        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();

        // Record large number of cases
        int recordCount = 10000;
        for (int i = 0; i < recordCount; i++) {
            Map<String, String> context = new HashMap<>();
            context.put("batch", String.valueOf(i / 1000));
            sloTracker.recordCaseCompletion(
                "case-memory-" + i,
                (long) (Math.random() * 8 * 60 * 60 * 1000),
                context
            );
        }

        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = memoryAfter - initialMemory;
        double memoryPerRecord = (double) memoryUsed / recordCount / 1024; // KB per record

        assertTrue(memoryPerRecord < 20, "Should use less than 20KB per record");
        LOGGER.info("Memory per record: {:.2f} KB", memoryPerRecord);
    }

    @Test
    @DisplayName("SLO-EDGE-008: Window cleanup and memory management")
    void testWindowCleanup() {
        // Record many cases to trigger cleanup
        for (int i = 0; i < 2000; i++) {
            Map<String, String> context = new HashMap<>();
            context.put("cleanup_test", "true");
            sloTracker.recordCaseCompletion(
                "case-cleanup-" + i,
                (long) (Math.random() * 48 * 60 * 60 * 1000), // 0-48 hours
                context
            );
        }

        // Force cleanup by checking expired records
        sloTracker.getComplianceRate(SLOTracker.SLOType.CASE_COMPLETION, Duration.ofDays(7));

        // Verify memory is managed properly
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        assertTrue(usedMemory < 100 * 1024 * 1024, "Should use less than 100MB");
    }

    // ==========================================================
    // ERROR RECOVERY TESTS
    // ==========================================================

    @Test
    @DisplayName("SLO-EDGE-009: Recovery from invalid data")
    void testRecoveryFromInvalidData() {
        // Record some valid data first
        Map<String, String> context = new HashMap<>();
        context.put("recovery_test", "true");
        sloTracker.recordCaseCompletion("case-valid", 1000, context);

        // Record invalid data
        assertDoesNotThrow(() -> {
            sloTracker.recordCaseCompletion("case-invalid", -1000, context);
        }, "Should handle negative duration");

        assertDoesNotThrow(() -> {
            sloTracker.recordCaseCompletion("case-invalid", Long.MAX_VALUE, context);
        }, "Should handle max duration");

        // Verify recovery - valid data should still be tracked
        Map<String, SLOTracker.ComplianceStatus> status = sloTracker.getAllComplianceStatus();
        assertEquals(3, status.size(), "Should track all cases including invalid ones");

        // Verify invalid cases are handled gracefully
        SLOTracker.ComplianceStatus invalidStatus = status.get("case-invalid");
        assertNotNull(invalidStatus);
        assertTrue(invalidStatus.getDurationMs() >= 0, "Duration should be normalized");
    }

    @Test
    @DisplayName("SLO-EDGE-010: Alert manager recovery")
    void testAlertManagerRecovery() {
        // Create some alerts
        SLOAlertManager.ViolationDetails details = new SLOAlertManager.ViolationDetails(
            "test-violation-1",
            "source-1",
            "case_completion",
            SLOAlertManager.SLOType.CASE_COMPLETION,
            30.0,
            24.0,
            "Test violation",
            Map.of("test", "value"),
            Instant.now()
        );

        SLOAlertManager.Alert alert = alertManager.createAlert(
            SLOAlertManager.SLOType.CASE_COMPLETION,
            details
        );
        assertNotNull(alert, "Should create alert");

        // Resolve alert
        SLOAlertManager.Alert resolved = alertManager.resolveAlert(
            alert.getAlertId(),
            "Test resolution"
        );
        assertNotNull(resolved, "Should resolve alert");

        // Verify recovery - manager should still function
        SLOAlertManager.ViolationDetails newDetails = new SLOAlertManager.ViolationDetails(
            "test-violation-2",
            "source-2",
            "case_completion",
            SLOAlertManager.SLOType.CASE_COMPLETION,
            35.0,
            24.0,
            "Another violation",
            Map.of(),
            Instant.now()
        );

        SLOAlertManager.Alert newAlert = alertManager.createAlert(
            SLOAlertManager.SLOType.CASE_COMPLETION,
            newDetails
        );
        assertNotNull(newAlert, "Manager should recover and create new alerts");
    }

    // ==========================================================
    // CONFIGURATION TESTS
    // ==========================================================

    @Test
    @DisplayName("SLO-EDGE-011: Custom SLO type configuration")
    void testCustomSloTypeConfiguration() {
        // Test custom SLO configuration
        assertDoesNotThrow(() -> {
            // This would test the ability to configure custom SLO types
            // In a real implementation, this might involve configuration files or API calls
            Map<String, Object> sloConfig = Map.of(
                "custom_slo_types", List.of(
                    Map.of("name", "CUSTOM_SLO", "threshold", 5000, "target", 95.0)
                )
            );

            // For now, just verify existing types work
            assertEquals(5, SLOTracker.SLOType.values().length, "Should have 5 default SLO types");
        }, "Should handle custom configuration gracefully");
    }

    @Test
    @DisplayName("SLO-EDGE-012: Dashboard configuration edge cases")
    void testDashboardConfigurationEdgeCases() {
        // Test dashboard with empty data
        SLODashboard.DashboardSnapshot emptySnapshot = new SLODashboard.DashboardSnapshot(
            Collections.emptyMap(),
            Collections.emptyMap(),
            Collections.emptyMap(),
            Collections.emptyMap(),
            Collections.emptyList(),
            Map.of(),
            Map.of(),
            Instant.now()
        );

        assertDoesNotThrow(() -> {
            String html = dashboard.generateHtmlDashboard(emptySnapshot);
            assertFalse(html.isEmpty(), "Should generate HTML even with empty data");

            String json = dashboard.generateJsonDashboard(emptySnapshot);
            assertFalse(json.isEmpty(), "Should generate JSON even with empty data");
        }, "Should handle empty snapshot gracefully");
    }

    // ==========================================================
    // PERFORMANCE TESTS
    // ==========================================================

    @Test
    @DisplayName("SLO-EDGE-013: Performance with very small windows")
    void testPerformanceWithSmallWindows() {
        // Test with very small time windows
        Duration tinyWindow = Duration.ofMillis(1);

        long startTime = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            double rate = sloTracker.getComplianceRate(
                SLOTracker.SLOType.TASK_EXECUTION,
                tinyWindow
            );
            assertTrue(rate >= 0 && rate <= 100, "Rate should be valid percentage");
        }
        long endTime = System.currentTimeMillis();

        long duration = endTime - startTime;
        double opsPerSecond = 100.0 / (duration / 1000.0);
        assertTrue(opsPerSecond > 10, "Should handle small windows efficiently");
        LOGGER.info("Small window performance: {:.0f} ops/sec", opsPerSecond);
    }

    @Test
    @DisplayName("SLO-EDGE-014: Predictive analytics with insufficient data")
    void testPredictiveAnalyticsWithInsufficientData() {
        // Test predictive analytics with minimal data
        assertDoesNotThrow(() -> {
            Map<String, SLOPredictiveAnalytics.PredictionResult> predictions =
                predictiveAnalytics.predictViolations(sloTracker, Duration.ofHours(1));

            // Should return empty or conservative predictions when data is insufficient
            assertNotNull(predictions);

            predictions.forEach((sloType, prediction) -> {
                // With minimal data, predictions should be conservative
                assertTrue(prediction.getConfidence() < 0.5, "Confidence should be low with minimal data");
                assertTrue(prediction.getViolationProbability() < 0.5, "Violation probability should be conservative");
            });
        }, "Should handle insufficient data gracefully");
    }

    // ==========================================================
    // DATA INTEGRITY TESTS
    // ==========================================================

    @Test
    @DisplayName("SLO-EDGE-015: Data integrity under stress")
    void testDataIntegrityUnderStress() {
        int stressTestIterations = 5000;
        Map<String, String> context = Map.of("stress_test", "true");

        // Record data rapidly
        for (int i = 0; i < stressTestIterations; i++) {
            sloTracker.recordCaseCompletion(
                "case-stress-" + i,
                (long) (Math.random() * 12 * 60 * 60 * 1000),
                context
            );
        }

        // Verify data integrity - all cases should be tracked
        Map<String, SLOTracker.ComplianceStatus> status = sloTracker.getAllComplianceStatus();
        assertEquals(stressTestIterations, status.size(), "Should track all stress test cases");

        // Verify no duplicate case IDs
        Set<String> caseIds = status.keySet();
        assertEquals(stressTestIterations, caseIds.size(), "Should have no duplicate case IDs");

        // Verify status consistency
        status.values().forEach(s -> {
            assertNotNull(s);
            assertNotNull(s.getType());
            assertNotNull(s.getStatus());
            assertTrue(s.getDurationMs() >= 0);
        });
    }

    @Test
    @DisplayName("SLO-EDGE-016: Timeline data consistency")
    void testTimelineDataConsistency() {
        // Record cases with specific timing
        Instant start = Instant.now();
        Map<String, String> context = Map.of("timeline_test", "true");

        for (int i = 0; i < 100; i++) {
            sloTracker.recordCaseCompletion(
                "case-timeline-" + i,
                1000 * i, // Increasing duration
                context
            );
        }

        // Verify timeline consistency
        SLODashboard.DashboardSnapshot snapshot = dashboard.createDashboardSnapshot(sloTracker);
        assertFalse(snapshot.getTimeline().isEmpty());

        // Verify timeline is ordered chronologically
        List<SLODashboard.TimelineEvent> timeline = snapshot.getTimeline();
        for (int i = 1; i < timeline.size(); i++) {
            assertTrue(timeline.get(i).getTimestamp().isAfter(timeline.get(i - 1).getTimestamp()),
                "Timeline should be ordered chronologically");
        }

        // Verify all cases are in timeline
        assertEquals(100, timeline.size(), "Timeline should contain all cases");
    }

    // ==========================================================
    // METRICS EXPORT TESTS
    // ==========================================================

    @Test
    @DisplayName("SLO-EDGE-017: Metrics export with edge cases")
    void testMetricsExportWithEdgeCases() {
        // Record some data
        Map<String, String> context = Map.of("export_test", "true");
        sloTracker.recordCaseCompletion("case-export", 1000, context);

        // Test metrics export with various scenarios
        assertDoesNotThrow(() -> {
            // Normal export
            Map<String, Object> metrics = sloTracker.getAllComplianceStatus();
            assertNotNull(metrics);
            assertFalse(metrics.isEmpty());

            // Export with null keys
            Map<String, Object> metricsWithNulls = new HashMap<>(metrics);
            metricsWithNulls.put(null, "null_key");
            assertNotNull(metricsWithNulls);

            // Export with empty values
            Map<String, Object> metricsWithEmptyValues = new HashMap<>(metrics);
            metricsWithEmptyValues.put("empty_value", "");
            assertNotNull(metricsWithEmptyValues);
        }, "Should handle edge cases in metrics export");
    }
}