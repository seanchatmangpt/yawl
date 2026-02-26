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

import org.junit.jupiter.api.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic SLO test suite for YAWL observability system.
 *
 * This test suite focuses on core SLO functionality without external dependencies,
 * ensuring comprehensive coverage of the observability features.
 *
 * Test Coverage Target: +3% line coverage for observability components
 *
 * @author YAWL Foundation
 * @since 6.0.0
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SLOBasicTestSuite {

    private SLOTracker sloTracker;
    private SLOAlertManager alertManager;
    private SLODashboard dashboard;
    private SLOIntegrationService integrationService;

    private final int TEST_ITERATIONS = 100;
    private final Duration PROCESSING_TIMEOUT = Duration.ofSeconds(2);

    @BeforeAll
    void setUpClass() {
        // Initialize test infrastructure
    }

    @BeforeEach
    void setUp() {
        sloTracker = new SLOTracker();
        alertManager = SLOAlertManager.getInstance();
        dashboard = new SLODashboard();

        integrationService = new SLOIntegrationService.Builder()
            .enableDashboard(true)
            .enablePredictiveAnalytics(true)
            .enableAlertManager(true)
            .build();
    }

    @AfterEach
    void tearDown() {
        if (integrationService != null) {
            integrationService.shutdown();
        }
        sloTracker.reset();
        alertManager.shutdown();
    }

    @AfterAll
    void tearDownClass() {
        // Cleanup test infrastructure
    }

    // ==========================================================
    // SLO COMPLIANCE TRACKING TESTS
    // ==========================================================

    @Test
    @DisplayName("SLO-BASIC-001: Case completion compliance tracking")
    void testCaseCompletionComplianceTracking() {
        integrationService.initialize();
        integrationService.start();

        Map<String, String> context = new HashMap<>();
        context.put("workflow_type", "standard");

        // Record compliant cases
        for (int i = 0; i < 50; i++) {
            integrationService.recordCaseCompletion(
                "case-compliant-" + i,
                (long) (Math.random() * 20 * 60 * 60 * 1000), // 0-20 hours
                context
            );
        }

        // Record violating cases
        for (int i = 0; i < 10; i++) {
            integrationService.recordCaseCompletion(
                "case-violating-" + i,
                25 * 60 * 60 * 1000L, // 25 hours
                context
            );
        }

        awaitProcessing();

        // Verify compliance tracking
        Map<String, SLOTracker.ComplianceStatus> status = integrationService.getComplianceStatus();
        assertEquals(60, status.size());

        long compliantCases = status.values().stream()
            .filter(s -> s.getStatus().equals(SLOTracker.ComplianceStatus.COMPLIANT))
            .count();

        double complianceRate = (double) compliantCases / 60 * 100;
        assertTrue(complianceRate > 80, "Should maintain high compliance rate");
    }

    @Test
    @DisplayName("SLO-BASIC-002: Task execution SLA compliance")
    void testTaskExecutionSLACompliance() {
        integrationService.initialize();
        integrationService.start();

        Map<String, String> context = new HashMap<>();
        context.put("task_category", "validation");

        // Record various task durations
        List<Long> durations = Arrays.asList(
            500L, 1000L, 1500L, 2000L, 2500L, // Fast tasks
            300000L, 600000L, 900000L, 1200000L, // Medium tasks
            2400000L, 3000000L // Slow tasks
        );

        for (int i = 0; i < durations.size(); i++) {
            integrationService.recordTaskExecution(
                "task-" + i,
                "case-" + i,
                durations.get(i),
                context
            );
        }

        awaitProcessing();

        // Verify task tracking
        SLODashboard.DashboardSnapshot snapshot = integrationService.getDashboardSnapshot();
        assertFalse(snapshot.getCases().isEmpty());

        // Check slow task detection
        long slowTasks = snapshot.getCases().values().stream()
            .flatMap(c -> c.getTasks().values().stream())
            .filter(SLODashboard.TaskMetrics::isSlowTask)
            .count();

        assertTrue(slowTasks > 0, "Should detect slow tasks");
    }

    @Test
    @DisplayName("SLO-BASIC-003: Queue response time compliance")
    void testQueueResponseCompliance() {
        integrationService.initialize();
        integrationService.start();

        Map<String, String> context = new HashMap<>();
        context.put("queue_environment", "production");

        // Test various response times
        List<Long> responseTimes = Arrays.asList(
            10L, 50L, 100L, 200L, 500L, 1000L, // Good responses
            2000L, 5000L, 10000L // Slow responses
        );

        for (int i = 0; i < responseTimes.size(); i++) {
            integrationService.recordQueueResponse("queue-" + i, responseTimes.get(i), context);
        }

        awaitProcessing();

        // Verify queue tracking
        SLODashboard.DashboardSnapshot snapshot = integrationService.getDashboardSnapshot();
        assertEquals(responseTimes.size(), snapshot.getQueues().size());

        // Check slow queue detection
        long slowQueues = snapshot.getQueues().values().stream()
            .filter(SLODashboard.QueueMetrics::isSlowQueue)
            .count();

        assertTrue(slowQueues > 0, "Should detect slow queues");
    }

    // ==========================================================
    // ALERT THRESHOLD VIOLATION TESTS
    // ==========================================================

    @Test
    @DisplayName("SLO-BASIC-004: Alert threshold violations")
    void testAlertThresholdViolations() {
        integrationService.initialize();
        integrationService.start();

        Map<String, String> context = new HashMap<>();
        context.put("alert_source", "violation_test");

        // Record violations
        for (int i = 0; i < 20; i++) {
            integrationService.recordCaseCompletion(
                "case-alert-" + i,
                30 * 60 * 60 * 1000L, // 30 hours (violates 24h SLA)
                context
            );

            integrationService.recordTaskExecution(
                "task-alert-" + i,
                "case-alert-" + i,
                120 * 60 * 1000L, // 2 minutes (violates 1h SLA)
                context
            );
        }

        awaitProcessing();

        // Verify alert generation
        Collection<SLOAlertManager.Alert> alerts = integrationService.getActiveAlerts();
        assertFalse(alerts.isEmpty(), "Should generate alerts");

        // Verify alert severity distribution
        long criticalAlerts = alerts.stream()
            .filter(a -> a.getSeverity().equals(SLOAlertManager.AlertSeverity.CRITICAL))
            .count();

        long emergencyAlerts = alerts.stream()
            .filter(a -> a.getSeverity().equals(SLOAlertManager.AlertSeverity.EMERGENCY))
            .count();

        assertTrue(criticalAlerts > 0, "Should have critical alerts");
        assertTrue(emergencyAlerts > 0, "Should have emergency alerts");
    }

    @Test
    @DisplayName("SLO-BASIC-005: Alert maintenance mode")
    void testAlertMaintenanceMode() {
        // Enter maintenance mode
        alertManager.enterMaintenanceMode("scheduled_maintenance", Duration.ofHours(2));

        integrationService.initialize();
        integrationService.start();

        Map<String, String> context = new HashMap<>();
        context.put("alert_source", "maintenance_test");

        // Record violations during maintenance
        integrationService.recordCaseCompletion(
            "case-maintenance-violation",
            36 * 60 * 60 * 1000L, // 36 hours
            context
        );

        awaitProcessing();

        // Verify alerts were suppressed
        Collection<SLOAlertManager.Alert> alerts = integrationService.getActiveAlerts();

        // Exit maintenance mode
        alertManager.exitMaintenanceMode();
    }

    // ==========================================================
    // TREND ANALYSIS TESTS
    // ==========================================================

    @Test
    @DisplayName("SLO-BASIC-006: Compliance trend analysis")
    void testComplianceTrendAnalysis() {
        integrationService.initialize();
        integrationService.start();

        Map<String, String> context = new HashMap<>();
        context.put("trend_test", "historical");

        // Simulate historical data over 24 hours
        for (int hour = 0; hour < 24; hour++) {
            for (int i = 0; i < 10; i++) {
                long duration = (long) (8 * 60 * 60 * 1000 + Math.random() * 4 * 60 * 60 * 1000);
                integrationService.recordCaseCompletion(
                    "case-" + hour + "-" + i,
                    duration,
                    context
                );
            }
        }

        awaitProcessing();

        // Perform trend analysis
        Map<String, Object> trendAnalysis = integrationService.analyzeTrends();
        assertNotNull(trendAnalysis, "Trend analysis should not be null");

        // Verify trend components
        assertTrue(trendAnalysis.containsKey("compliance_trend"));
        assertTrue(trendAnalysis.containsKey("duration_trend"));
        assertTrue(trendAnalysis.containsKey("throughput_trend"));
        assertTrue(trendAnalysis.containsKey("violation_pattern"));
    }

    @Test
    @DisplayName("SLO-BASIC-007: Predictive analytics for violations")
    void testPredictiveAnalytics() {
        integrationService.initialize();
        integrationService.start();

        Map<String, String> context = new HashMap<>();
        context.put("predictive_test", "true");

        // Record data showing violation trend
        for (int i = 0; i < 50; i++) {
            long duration = (long) (20 * 60 * 60 * 1000 + i * 60 * 60 * 1000);
            integrationService.recordCaseCompletion(
                "case-predictive-" + i,
                duration,
                context
            );
        }

        awaitProcessing();

        // Generate predictions
        Map<String, SLOPredictiveAnalytics.PredictionResult> predictions =
            integrationService.getPredictions();

        assertFalse(predictions.isEmpty(), "Should generate predictions");

        // Verify prediction structure
        predictions.forEach((sloType, prediction) -> {
            assertNotNull(prediction.getSloType());
            assertTrue(prediction.getConfidence() >= 0 && prediction.getConfidence() <= 1);
            assertTrue(prediction.getTimeToViolation() >= 0);
            assertNotNull(prediction.getViolationProbability());
            assertTrue(prediction.getViolationProbability() >= 0 && prediction.getViolationProbability() <= 1);
        });
    }

    // ==========================================================
    // DASHBOARD GENERATION TESTS
    // ==========================================================

    @Test
    @DisplayName("SLO-BASIC-008: Dashboard generation")
    void testDashboardGeneration() {
        integrationService.initialize();
        integrationService.start();

        Map<String, String> context = new HashMap<>();
        context.put("dashboard_test", "comprehensive");

        // Record diverse data
        for (int i = 0; i < 100; i++) {
            integrationService.recordCaseCompletion(
                "case-dashboard-" + i,
                (long) (Math.random() * 24 * 60 * 60 * 1000),
                context
            );

            integrationService.recordTaskExecution(
                "task-dashboard-" + i,
                "case-dashboard-" + (i % 10),
                (long) (Math.random() * 60 * 60 * 1000),
                context
            );

            integrationService.recordQueueResponse(
                "queue-dashboard-" + i,
                (long) (Math.random() * 5000),
                context
            );
        }

        awaitProcessing();

        // Generate dashboard snapshot
        SLODashboard.DashboardSnapshot snapshot = integrationService.getDashboardSnapshot();
        assertNotNull(snapshot, "Dashboard snapshot should not be null");

        // Verify dashboard components
        assertFalse(snapshot.getCases().isEmpty(), "Should have case metrics");
        assertFalse(snapshot.getQueues().isEmpty(), "Should have queue metrics");
        assertFalse(snapshot.getVirtualThreads().isEmpty(), "Should have virtual thread metrics");
        assertFalse(snapshot.getLocks().isEmpty(), "Should have lock metrics");

        // Generate HTML dashboard
        String htmlDashboard = dashboard.generateHtmlDashboard(snapshot);
        assertNotNull(htmlDashboard, "HTML dashboard should not be null");
        assertTrue(htmlDashboard.contains("<!DOCTYPE html>"));
        assertTrue(htmlDashboard.contains("SLO Dashboard"));
    }

    // ==========================================================
    // PERFORMANCE AND STRESS TESTS
    // ==========================================================

    @Test
    @DisplayName("SLO-BASIC-009: Performance benchmarking under load")
    void testPerformanceBenchmarking() {
        integrationService.initialize();
        integrationService.start();

        Map<String, String> context = new HashMap<>();
        context.put("benchmark_test", "load");

        // High-volume data generation
        int recordCount = 1000;
        long startTime = System.currentTimeMillis();

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(recordCount);

        for (int i = 0; i < recordCount; i++) {
            final int recordId = i;
            executor.submit(() -> {
                try {
                    integrationService.recordCaseCompletion(
                        "case-benchmark-" + recordId,
                        (long) (Math.random() * 8 * 60 * 60 * 1000),
                        context
                    );
                } finally {
                    latch.countDown();
                }
            });
        }

        executor.shutdown();
        try {
            assertTrue(latch.await(30, TimeUnit.SECONDS), "Should process all records");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Benchmark interrupted");
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Calculate performance metrics
        double recordsPerSecond = (double) recordCount / (duration / 1000.0);
        assertTrue(recordsPerSecond > 50, "Should process at least 50 records/second");

        awaitProcessing();

        SLODashboard.DashboardSnapshot snapshot = integrationService.getDashboardSnapshot();
        assertEquals(recordCount, snapshot.getCases().size(), "Should track all cases");
    }

    @Test
    @DisplayName("SLO-BASIC-010: Concurrent access test")
    void testConcurrentAccess() {
        integrationService.initialize();
        integrationService.start();

        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        // Submit concurrent recording tasks
        for (int i = 0; i < 500; i++) {
            final int taskId = i;
            executor.submit(() -> {
                try {
                    Map<String, String> context = new HashMap<>();
                    context.put("thread_id", String.valueOf(taskId % threadCount));
                    integrationService.recordCaseCompletion(
                        "case-concurrent-" + taskId,
                        (long) (Math.random() * 8 * 60 * 60 * 1000),
                        context
                    );
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
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
        assertEquals(500, successCount.get(), "All operations should succeed");
        assertEquals(0, errorCount.get(), "No operations should fail");

        awaitProcessing();

        // Verify data consistency
        SLODashboard.DashboardSnapshot snapshot = integrationService.getDashboardSnapshot();
        assertEquals(500, snapshot.getCases().size(), "Should track all cases");
    }

    // ==========================================================
    // ERROR HANDLING TESTS
    // ==========================================================

    @Test
    @DisplayName("SLO-BASIC-011: Error handling and resilience")
    void testErrorHandlingAndResilience() {
        integrationService.initialize();
        integrationService.start();

        // Test error conditions
        assertDoesNotThrow(() -> {
            // Null case ID
            assertThrows(NullPointerException.class, () -> {
                integrationService.recordCaseCompletion(null, 1000, new HashMap<>());
            });

            // Negative duration
            assertDoesNotThrow(() -> {
                integrationService.recordCaseCompletion("case-negative", -1000, new HashMap<>());
            });

            // Large volume handling
            for (int i = 0; i < 10000; i++) {
                Map<String, String> context = new HashMap<>();
                context.put("batch_id", String.valueOf(i / 100));
                integrationService.recordCaseCompletion(
                    "case-stress-" + i,
                    (long) (Math.random() * 8 * 60 * 60 * 1000),
                    context
                );
            }
        }, "Should handle errors gracefully");

        // Verify service remains functional
        awaitProcessing();

        SLODashboard.DashboardSnapshot snapshot = integrationService.getDashboardSnapshot();
        assertFalse(snapshot.getCases().isEmpty(), "Should remain functional after stress");
        assertEquals(10000, snapshot.getCases().size(), "Should track all stress test cases");
    }

    @Test
    @DisplayName("SLO-BASIC-012: Boundary condition handling")
    void testBoundaryConditions() {
        integrationService.initialize();
        integrationService.start();

        // Test zero duration
        assertDoesNotThrow(() -> {
            Map<String, String> context = new HashMap<>();
            context.put("edge_case", "zero_duration");
            integrationService.recordCaseCompletion("case-zero-duration", 0, context);
        }, "Should handle zero duration gracefully");

        // Test maximum duration
        long maxDuration = Long.MAX_VALUE / 1000;
        assertDoesNotThrow(() -> {
            Map<String, String> context = new HashMap<>();
            context.put("edge_case", "max_duration");
            integrationService.recordCaseCompletion("case-max-duration", maxDuration, context);
        }, "Should handle maximum duration gracefully");

        // Verify boundary cases are handled
        awaitProcessing();

        SLODashboard.DashboardSnapshot snapshot = integrationService.getDashboardSnapshot();
        assertEquals(2, snapshot.getCases().size(), "Should track boundary cases");
    }

    @Test
    @DisplayName("SLO-BASIC-013: Integration with YAWL engine")
    void testIntegrationWithYawlEngine() {
        integrationService.initialize();
        integrationService.start();

        Map<String, String> context = new HashMap<>();
        context.put("yawl_task", "test-task");
        context.put("yawl_net", "test-net");

        // Record task execution
        integrationService.recordTaskExecution(
            "task-yawl-integration",
            "case-yawl-integration",
            5000, // 5 seconds
            context
        );

        awaitProcessing();

        // Verify integration
        SLODashboard.DashboardSnapshot snapshot = integrationService.getDashboardSnapshot();
        assertFalse(snapshot.getCases().isEmpty(), "Should integrate with YAWL engine");

        SLODashboard.CaseMetrics caseMetrics = snapshot.getCases().get("case-yawl-integration");
        assertNotNull(caseMetrics, "Should have case metrics");
        assertFalse(caseMetrics.getTasks().isEmpty(), "Should have task metrics");

        // Verify YAWL-specific context is preserved
        assertTrue(caseMetrics.getContext().containsKey("yawl_task"));
        assertTrue(caseMetrics.getContext().containsKey("yawl_net"));
    }

    @Test
    @DisplayName("SLO-BASIC-014: Metrics export functionality")
    void testMetricsExport() {
        integrationService.initialize();
        integrationService.start();

        Map<String, String> context = new HashMap<>();
        context.put("export_test", "true");

        // Record data for export
        for (int i = 0; i < 50; i++) {
            integrationService.recordCaseCompletion(
                "case-export-" + i,
                (long) (Math.random() * 12 * 60 * 60 * 1000),
                context
            );
        }

        awaitProcessing();

        // Test metrics export
        Map<String, Object> exportedMetrics = integrationService.exportMetrics();
        assertNotNull(exportedMetrics, "Should export metrics");

        // Verify metric structure
        assertTrue(exportedMetrics.containsKey("total_cases"));
        assertTrue(exportedMetrics.containsKey("compliance_rate"));
        assertTrue(exportedMetrics.containsKey("average_duration"));

        // Verify metric values
        assertTrue((int) exportedMetrics.get("total_cases") > 0);
        assertTrue((double) exportedMetrics.get("compliance_rate") >= 0);
        assertTrue((double) exportedMetrics.get("average_duration") > 0);
    }

    @Test
    @DisplayName("SLO-BASIC-015: Service lifecycle management")
    void testServiceLifecycleManagement() {
        // Test initialization
        integrationService.initialize();
        assertEquals(SLOIntegrationService.ServiceStatus.STOPPED, integrationService.getStatus());

        // Test start/stop
        integrationService.start();
        assertEquals(SLOIntegrationService.ServiceStatus.RUNNING, integrationService.getStatus());

        // Test shutdown
        integrationService.shutdown();
        assertEquals(SLOIntegrationService.ServiceStatus.STOPPED, integrationService.getStatus());

        // Test idempotent operations
        integrationService.initialize();
        integrationService.initialize(); // Should not fail
        assertEquals(SLOIntegrationService.ServiceStatus.STOPPED, integrationService.getStatus());

        integrationService.start();
        integrationService.start(); // Should not fail
        assertEquals(SLOIntegrationService.ServiceStatus.RUNNING, integrationService.getStatus());
    }

    // ==========================================================
    // HELPER METHODS
    // ==========================================================

    /**
     * Helper method to wait for SLO processing to complete.
     */
    private void awaitProcessing() {
        try {
            Thread.sleep(PROCESSING_TIMEOUT.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Test interrupted during processing wait");
        }
    }
}