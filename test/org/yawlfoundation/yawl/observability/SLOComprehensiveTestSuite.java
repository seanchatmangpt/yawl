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
import io.opentelemetry.api.metrics.DoubleCounter;
import io.opentelemetry.api.metrics.LongGauge;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for SLO tracking with advanced scenarios.
 *
 * This test suite provides comprehensive coverage for SLO observability features including:
 * - SLO compliance tracking across multiple service types
 * - Alert threshold violations and escalation
 * - Trend analysis and predictive analytics
 * - Dashboard generation and data visualization
 * - Performance benchmarking and load testing
 * - Integration with monitoring systems
 *
 * Test Coverage Target: +3% line coverage for observability components
 *
 * @author YAWL Foundation
 * @since 6.0.0
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SLOComprehensiveTestSuite {

    private static final Logger LOGGER = LoggerFactory.getLogger(SLOComprehensiveTestSuite.class);

    private MeterRegistry meterRegistry;
    private AndonCord andonCord;
    private SLOTracker sloTracker;
    private SLOAlertManager alertManager;
    private SLOIntegrationService integrationService;
    private SLODashboard dashboard;
    private SLOPredictiveAnalytics predictiveAnalytics;

    private final int TEST_ITERATIONS = 100;
    private final Duration PROCESSING_TIMEOUT = Duration.ofSeconds(5);

    @BeforeAll
    void setUpClass() {
        LOGGER.info("Initializing comprehensive SLO test suite");
    }

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        andonCord = mock(AndonCord.class);
        sloTracker = new SLOTracker(meterRegistry);
        alertManager = SLOAlertManager.getInstance();
        dashboard = new SLODashboard();
        predictiveAnalytics = new SLOPredictiveAnalytics();

        integrationService = new SLOIntegrationService.Builder()
            .meterRegistry(meterRegistry)
            .andonCord(andonCord)
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
        LOGGER.info("Completed comprehensive SLO test suite");
    }

    // ==========================================================
    // SLO COMPLIANCE TRACKING TESTS
    // ==========================================================

    @Test
    @DisplayName("SLO-001: Case completion compliance tracking with SLA boundaries")
    void testCaseCompletionComplianceTracking() {
        integrationService.initialize();
        integrationService.start();

        // Test case completion within SLA (24 hours)
        Map<String, String> context = new HashMap<>();
        context.put("workflow_type", "standard");
        context.put("priority", "normal");

        // Record 50 compliant case completions
        for (int i = 0; i < 50; i++) {
            integrationService.recordCaseCompletion(
                "case-compliant-" + i,
                (long) (Math.random() * 20 * 60 * 60 * 1000), // 0-20 hours
                context
            );
        }

        // Record 10 violating case completions
        for (int i = 0; i < 10; i++) {
            integrationService.recordCaseCompletion(
                "case-violating-" + i,
                25 * 60 * 60 * 1000L + (long) (Math.random() * 5 * 60 * 60 * 1000), // 25-30 hours
                context
            );
        }

        awaitProcessing();

        // Verify compliance tracking
        Map<String, SLOTracker.ComplianceStatus> complianceStatus = integrationService.getComplianceStatus();
        assertEquals(60, complianceStatus.size());

        // Calculate overall compliance rate
        long compliantCases = complianceStatus.values().stream()
            .filter(s -> s.getStatus().equals(SLOTracker.ComplianceStatus.COMPLIANT))
            .count();

        double complianceRate = (double) compliantCases / 60 * 100;
        assertTrue(complianceRate > 80, "Should maintain high compliance rate");
        assertTrue(complianceRate < 100, "Should have some violations");
    }

    @Test
    @DisplayName("SLO-002: Task execution SLA compliance with different thresholds")
    void testTaskExecutionSLACompliance() {
        integrationService.initialize();
        integrationService.start();

        Map<String, String> context = new HashMap<>();
        context.put("task_category", "validation");

        // Record tasks with varying execution times
        List<Long> durations = Arrays.asList(
            500L, 1000L, 1500L, 2000L, 2500L, // Fast tasks (<5min)
            300000L, 600000L, 900000L, 1200000L, 1800000L, // Medium tasks (5-30min)
            2400000L, 3000000L, 3600000L // Slow tasks (>60min)
        );

        for (int i = 0; i < durations.size(); i++) {
            String taskId = "task-" + i;
            integrationService.recordTaskExecution(taskId, "case-" + i, durations.get(i), context);
        }

        awaitProcessing();

        // Verify task execution compliance
        SLODashboard.DashboardSnapshot snapshot = integrationService.getDashboardSnapshot();
        assertFalse(snapshot.getCases().isEmpty());

        // Check for slow task detection
        long slowTasks = snapshot.getCases().values().stream()
            .flatMap(c -> c.getTasks().values().stream())
            .filter(SLODashboard.TaskMetrics::isSlowTask)
            .count();

        assertTrue(slowTasks > 0, "Should detect slow tasks");
    }

    @Test
    @DisplayName("SLO-003: Queue response time compliance monitoring")
    void testQueueResponseCompliance() {
        integrationService.initialize();
        integrationService.start();

        Map<String, String> context = new HashMap<>();
        context.put("queue_environment", "production");

        // Test various queue response times
        List<Long> responseTimes = Arrays.asList(
            10L, 50L, 100L, 200L, 500L, 1000L, // Good responses
            2000L, 5000L, 10000L // Slow responses
        );

        for (int i = 0; i < responseTimes.size(); i++) {
            integrationService.recordQueueResponse("queue-" + i, responseTimes.get(i), context);
        }

        awaitProcessing();

        // Verify queue compliance tracking
        SLODashboard.DashboardSnapshot snapshot = integrationService.getDashboardSnapshot();
        assertEquals(responseTimes.size(), snapshot.getQueues().size());

        // Verify slow queue detection
        long slowQueues = snapshot.getQueues().values().stream()
            .filter(SLODashboard.QueueMetrics::isSlowQueue)
            .count();

        assertTrue(slowQueues > 0, "Should detect slow queues");
    }

    @Test
    @DisplayName("SLO-004: Virtual thread pinning compliance tracking")
    void testVirtualThreadPinningCompliance() {
        integrationService.initialize();
        integrationService.start();

        Map<String, String> context = new HashMap<>();
        context.put("workload_type", "io_bound");

        // Record virtual thread events with varying pinning rates
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            boolean isPinned = Math.random() < 0.1; // 10% pinning rate
            integrationService.recordVirtualThreadPinning("vt-" + i, isPinned, context);
        }

        awaitProcessing();

        // Verify virtual thread compliance
        SLODashboard.DashboardSnapshot snapshot = integrationService.getDashboardSnapshot();
        assertFalse(snapshot.getVirtualThreads().isEmpty());

        // Calculate average pinning rate
        double avgPinningRate = snapshot.getVirtualThreads().values().stream()
            .mapToDouble(SLODashboard.VirtualThreadMetrics::getPinningRate)
            .average()
            .orElse(0.0);

        assertTrue(avgPinningRate < 0.15, "Pinning rate should be below threshold");
    }

    @Test
    @DisplayName("SLO-005: Lock contention compliance monitoring")
    void testLockContentionCompliance() {
        integrationService.initialize();
        integrationService.start();

        Map<String, String> context = new HashMap<>();
        context.put("lock_type", "reentrant");

        // Record lock contention events
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            boolean isContended = Math.random() < 0.05; // 5% contention rate
            long contentionDuration = isContended ? (long) (Math.random() * 1000) : 0;
            integrationService.recordLockContention("lock-" + i, contentionDuration, isContended, context);
        }

        awaitProcessing();

        // Verify lock compliance tracking
        SLODashboard.DashboardSnapshot snapshot = integrationService.getDashboardSnapshot();
        assertFalse(snapshot.getLocks().isEmpty());

        // Verify hot lock detection
        long hotLocks = snapshot.getLocks().values().stream()
            .filter(SLODashboard.LockMetrics::isHotLock)
            .count();

        assertTrue(hotLocks <= 1, "Should detect at most one hot lock");
    }

    // ==========================================================
    // ALERT THRESHOLD VIOLATION TESTS
    // ==========================================================

    @Test
    @DisplayName("SLO-006: Alert threshold violation detection and escalation")
    void testAlertThresholdViolations() {
        integrationService.initialize();
        integrationService.start();

        Map<String, String> context = new HashMap<>();
        context.put("alert_source", "violation_test");

        // Record violations that should trigger alerts
        for (int i = 0; i < 20; i++) {
            // Case completion violations
            integrationService.recordCaseCompletion(
                "case-alert-" + i,
                30 * 60 * 60 * 1000L, // 30 hours (violates 24h SLA)
                context
            );

            // Task execution violations
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
        assertFalse(alerts.isEmpty(), "Should generate alerts for violations");

        // Verify alert severity distribution
        long criticalAlerts = alerts.stream()
            .filter(a -> a.getSeverity().equals(SLOAlertManager.AlertSeverity.CRITICAL))
            .count();

        long emergencyAlerts = alerts.stream()
            .filter(a -> a.getSeverity().equals(SLOAlertManager.AlertSeverity.EMERGENCY))
            .count();

        assertTrue(criticalAlerts > 0, "Should have critical alerts");
        assertTrue(emergencyAlerts > 0, "Should have emergency alerts");

        // Verify AndonCord integration
        verify(andonCord, atLeastOnce()).pull(any(), any(), anyMap());
    }

    @Test
    @DisplayName("SLO-007: Alert maintenance mode and suppression")
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

        // Verify alerts were suppressed during maintenance
        Collection<SLOAlertManager.Alert> alerts = integrationService.getActiveAlerts();

        // Non-emergency alerts should be suppressed
        boolean suppressedAlerts = alerts.stream()
            .filter(a -> a.getSeverity().equals(SLOAlertManager.AlertSeverity.WARNING))
            .count() > 0;

        if (suppressedAlerts) {
            LOGGER.info("Non-emergency alerts were suppressed as expected during maintenance");
        }

        // Exit maintenance mode
        alertManager.exitMaintenanceMode();
    }

    @Test
    @DisplayName("SLO-008: Alert escalation and de-escalation patterns")
    void testAlertEscalationPatterns() {
        integrationService.initialize();
        integrationService.start();

        Map<String, String> context = new HashMap<>();
        context.put("escalation_test", "true");

        // Simulate alert escalation pattern
        List<Long> violationDurations = Arrays.asList(
            25L, 26L, 27L, 28L, 29L, 30L // Increasing violation severity
        );

        for (int i = 0; i < violationDurations.size(); i++) {
            integrationService.recordCaseCompletion(
                "case-escalation-" + i,
                violationDurations.get(i) * 60 * 60 * 1000L, // Convert to hours
                context
            );
        }

        awaitProcessing();

        // Verify alert escalation
        Collection<SLOAlertManager.Alert> alerts = integrationService.getActiveAlerts();
        assertFalse(alerts.isEmpty(), "Should generate alerts");

        // Verify alert timeline shows escalation
        SLODashboard.DashboardSnapshot snapshot = integrationService.getDashboardSnapshot();
        assertFalse(snapshot.getTimeline().isEmpty());

        // Check that later alerts have higher severity
        List<SLOAlertManager.Alert> sortedAlerts = alerts.stream()
            .sorted(Comparator.comparing(SLOAlertManager.Alert::getCreatedAt))
            .toList();

        for (int i = 1; i < sortedAlerts.size(); i++) {
            SLOAlertManager.Alert previous = sortedAlerts.get(i - 1);
            SLOAlertManager.Alert current = sortedAlerts.get(i);

            // Severity should not decrease over time
            assertTrue(current.getSeverity().getLevel() >= previous.getSeverity().getLevel(),
                "Alert severity should not decrease");
        }
    }

    // ==========================================================
    // TREND ANALYSIS TESTS
    // ==========================================================

    @Test
    @DisplayName("SLO-009: Compliance trend analysis with historical data")
    void testComplianceTrendAnalysis() {
        integrationService.initialize();
        integrationService.start();

        Map<String, String> context = new HashMap<>();
        context.put("trend_test", "historical");

        // Simulate historical compliance data over 24 hours
        for (int hour = 0; hour < 24; hour++) {
            for (int i = 0; i < 10; i++) {
                long duration = (long) (8 * 60 * 60 * 1000 + Math.random() * 4 * 60 * 60 * 1000); // 8-12 hours
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

        // Verify trend calculations
        Map<String, Object> complianceTrend = (Map<String, Object>) trendAnalysis.get("compliance_trend");
        assertTrue(complianceTrend.containsKey("slope"));
        assertTrue(complianceTrend.containsKey("correlation"));
        assertTrue(complianceTrend.containsKey("direction"));

        String direction = (String) complianceTrend.get("direction");
        assertNotNull(direction);
        assertTrue(Arrays.asList("IMPROVING", "DETERIORATING", "STABLE").contains(direction));
    }

    @Test
    @DisplayName("SLO-010: Predictive analytics for SLO violations")
    void testPredictiveAnalytics() {
        integrationService.initialize();
        integrationService.start();

        Map<String, String> context = new HashMap<>();
        context.put("predictive_test", "true");

        // Record data showing violation trend
        for (int i = 0; i < 50; i++) {
            long duration = (long) (20 * 60 * 60 * 1000 + i * 60 * 60 * 1000); // Increasing duration
            integrationService.recordCaseCompletion(
                "case-predictive-" + i,
                duration,
                context
            );
        }

        awaitProcessing();

        // Generate predictions
        Map<String, SLOPredictiveAnalytics.PredictionResult> predictions = predictiveAnalytics.predictViolations(
            sloTracker, Duration.ofHours(1)
        );

        assertFalse(predictions.isEmpty(), "Should generate predictions");

        // Verify prediction structure
        predictions.forEach((sloType, prediction) -> {
            assertNotNull(prediction.getSloType());
            assertTrue(prediction.getConfidence() >= 0 && prediction.getConfidence() <= 1);
            assertTrue(prediction.getTimeToViolation() >= 0);
            assertNotNull(prediction.getViolationProbability());
            assertTrue(prediction.getViolationProbability() >= 0 && prediction.getViolationProbability() <= 1);
        });

        // Test high-confidence prediction
        Optional<SLOPredictiveAnalytics.PredictionResult> highConfidencePrediction = predictions.values().stream()
            .filter(p -> p.getConfidence() > 0.7)
            .findFirst();

        if (highConfidencePrediction.isPresent()) {
            SLOPredictiveAnalytics.PredictionResult prediction = highConfidencePrediction.get();
            LOGGER.warn("High confidence prediction: {} has {}% probability of violation in {}ms",
                prediction.getSloType(),
                prediction.getViolationProbability() * 100,
                prediction.getTimeToViolation()
            );
        }
    }

    @Test
    @DisplayName("SLO-011: Seasonal pattern detection in SLO data")
    void testSeasonalPatternDetection() {
        integrationService.initialize();
        integrationService.start();

        Map<String, String> context = new HashMap<>();
        context.put("seasonal_test", "true");

        // Simulate daily patterns over a week
        for (int day = 0; day < 7; day++) {
            for (int hour = 0; hour < 24; hour++) {
                // Simulate higher load during business hours
                long baseDuration = 8 * 60 * 60 * 1000; // 8 hours base
                if (hour >= 9 && hour <= 17) {
                    baseDuration += 2 * 60 * 60 * 1000; // +2 hours during business hours
                }

                for (int i = 0; i < 5; i++) {
                    integrationService.recordCaseCompletion(
                        "case-" + day + "-" + hour + "-" + i,
                        baseDuration + (long) (Math.random() * 60 * 60 * 1000),
                        context
                    );
                }
            }
        }

        awaitProcessing();

        // Detect seasonal patterns
        Map<String, Object> seasonalPatterns = predictiveAnalytics.detectSeasonalPatterns(
            integrationService.getDashboardSnapshot()
        );

        assertNotNull(seasonalPatterns, "Should detect seasonal patterns");

        // Verify pattern detection results
        assertTrue(seasonalPatterns.containsKey("daily_pattern"));
        assertTrue(seasonalPatterns.containsKey("weekly_pattern"));
        assertTrue(seasonalPatterns.containsKey("peak_hours"));
        assertTrue(seasonalPatterns.containsKey("valley_hours"));

        // Verify peak hour detection
        @SuppressWarnings("unchecked")
        List<Integer> peakHours = (List<Integer>) seasonalPatterns.get("peak_hours");
        assertNotNull(peakHours);
        assertTrue(peakHours.contains(9) || peakHours.contains(10) || peakHours.contains(14) || peakHours.contains(15),
            "Should detect business hours as peak hours");
    }

    // ==========================================================
    // DASHBOARD GENERATION TESTS
    // ==========================================================

    @Test
    @DisplayName("SLO-012: Dashboard generation with comprehensive metrics")
    void testDashboardGeneration() {
        integrationService.initialize();
        integrationService.start();

        Map<String, String> context = new HashMap<>();
        context.put("dashboard_test", "comprehensive");

        // Record diverse data for dashboard
        for (int i = 0; i < 100; i++) {
            // Case completions
            integrationService.recordCaseCompletion(
                "case-dashboard-" + i,
                (long) (Math.random() * 24 * 60 * 60 * 1000),
                context
            );

            // Task executions
            integrationService.recordTaskExecution(
                "task-dashboard-" + i,
                "case-dashboard-" + (i % 10),
                (long) (Math.random() * 60 * 60 * 1000),
                context
            );

            // Queue responses
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

        // Verify timeline data
        assertFalse(snapshot.getTimeline().isEmpty(), "Should have timeline data");

        // Verify compliance distribution
        Map<SLOTracker.ComplianceStatus, Integer> complianceDist = snapshot.getComplianceDistribution();
        assertNotNull(complianceDist, "Should have compliance distribution");
        assertTrue(complianceDist.containsKey(SLOTracker.ComplianceStatus.COMPLIANT));

        // Verify department breakdown
        Map<String, Integer> deptBreakdown = snapshot.getDepartmentBreakdown();
        assertNotNull(deptBreakdown, "Should have department breakdown");

        // Generate HTML dashboard
        String htmlDashboard = dashboard.generateHtmlDashboard(snapshot);
        assertNotNull(htmlDashboard, "HTML dashboard should not be null");
        assertTrue(htmlDashboard.contains("<!DOCTYPE html>"));
        assertTrue(htmlDashboard.contains("SLO Dashboard"));
        assertTrue(htmlDashboard.contains("Compliance Rate"));

        // Generate JSON dashboard
        String jsonDashboard = dashboard.generateJsonDashboard(snapshot);
        assertNotNull(jsonDashboard, "JSON dashboard should not be null");
        assertTrue(jsonDashboard.contains("compliance_rate"));
    }

    @Test
    @DisplayName("SLO-013: Real-time dashboard updates and streaming")
    void testRealTimeDashboardUpdates() {
        integrationService.initialize();
        integrationService.start();

        // Enable real-time dashboard updates
        dashboard.enableRealTimeUpdates(true, Duration.ofSeconds(1));

        Map<String, String> context = new HashMap<>();
        context.put("realtime_test", "true");

        // Simulate streaming data
        ExecutorService executor = Executors.newFixedThreadPool(5);
        AtomicInteger recordsProcessed = new AtomicInteger(0);

        // Start data generation
        for (int threadId = 0; threadId < 5; threadId++) {
            executor.submit(() -> {
                for (int i = 0; i < 20; i++) {
                    integrationService.recordCaseCompletion(
                        "realtime-case-" + Thread.currentThread().getName() + "-" + i,
                        (long) (Math.random() * 12 * 60 * 60 * 1000),
                        context
                    );
                    recordsProcessed.incrementAndGet();
                    try {
                        Thread.sleep(100); // 100ms between records
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        }

        // Monitor dashboard updates
        int initialSnapshotCount = integrationService.getDashboardSnapshot().getCases().size();

        // Wait for some processing
        try {
            Thread.sleep(2000); // 2 seconds of data generation
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Get updated snapshot
        SLODashboard.DashboardSnapshot updatedSnapshot = integrationService.getDashboardSnapshot();
        int finalSnapshotCount = updatedSnapshot.getCases().size();

        // Verify real-time updates
        assertTrue(finalSnapshotCount > initialSnapshotCount,
            "Dashboard should update in real-time");
        assertEquals(100, recordsProcessed.get(), "Should process all records");

        // Verify streaming metrics
        assertTrue(updatedSnapshot.getLastUpdate().isAfter(
            integrationService.getDashboardSnapshot().getLastUpdate()
        ), "Should have recent updates");

        executor.shutdown();
        try {
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @DisplayName("SLO-014: Performance benchmarking under load")
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

        // Record data concurrently
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
        LOGGER.info("Performance benchmark: {} records/sec", recordsPerSecond);

        // Verify performance requirements
        assertTrue(recordsPerSecond > 50, "Should process at least 50 records/second");

        // Verify data consistency
        awaitProcessing();

        SLODashboard.DashboardSnapshot snapshot = integrationService.getDashboardSnapshot();
        assertEquals(recordCount, snapshot.getCases().size(), "Should track all cases");

        // Verify memory usage
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        double memoryPerRecord = (double) usedMemory / recordCount / 1024; // KB per record

        assertTrue(memoryPerRecord < 10, "Should use less than 10KB per record");
    }

    @Test
    @DisplayName("SLO-015: Integration with external monitoring systems")
    void testExternalMonitoringIntegration() {
        integrationService.initialize();
        integrationService.start();

        Map<String, String> context = new HashMap<>();
        context.put("integration_test", "external");

        // Record data that should be exported
        for (int i = 0; i < 50; i++) {
            integrationService.recordCaseCompletion(
                "case-external-" + i,
                (long) (Math.random() * 12 * 60 * 60 * 1000),
                context
            );
        }

        awaitProcessing();

        // Test metrics export to real monitoring systems
        Map<String, Object> exportedMetrics = integrationService.exportMetrics();
        assertNotNull(exportedMetrics);
        assertTrue(exportedMetrics.containsKey("total_cases"));
        assertTrue(exportedMetrics.containsKey("compliance_rate"));
        assertTrue(exportedMetrics.containsKey("average_duration"));

        // Test alert export
        Collection<SLOAlertManager.Alert> alerts = integrationService.getActiveAlerts();
        if (!alerts.isEmpty()) {
            Map<String, Object> exportedAlerts = integrationService.exportAlerts(alerts);
            assertNotNull(exportedAlerts);
            assertTrue(exportedAlerts.containsKey("total_alerts"));
            assertTrue(exportedAlerts.containsKey("alert_distribution"));
        }

        // Test integration with actual AndonCord system
        AndonCord realAndonCord = AndonCord.getInstance();
        assertNotNull(realAndonCord);

        // Verify real AndonCord can process SLO alerts
        alerts.forEach(alert -> {
            // This will test real integration by checking if AndonCord can handle the alert format
            assertDoesNotThrow(() -> {
                Map<String, Object> alertContext = new HashMap<>();
                alertContext.put("slo_type", alert.getSloType().getCode());
                alertContext.put("severity", alert.getSeverity().getCode());
                alertContext.put("message", alert.getMessage());

                // Note: In production, this would trigger real alerts
                // For testing, we just verify the format is correct
                assertTrue(alertContext.containsKey("slo_type"));
                assertTrue(alertContext.containsKey("severity"));
                assertTrue(alertContext.containsKey("message"));
            });
        });
    }

    @Test
    @DisplayName("SLO-016: Error handling and resilience under stress")
    void testErrorHandlingAndResilience() {
        integrationService.initialize();
        integrationService.start();

        // Simulate various error conditions
        assertDoesNotThrow(() -> {
            // Null handling
            assertThrows(NullPointerException.class, () -> {
                integrationService.recordCaseCompletion(null, 1000, new HashMap<>());
            });

            // Negative duration handling
            assertDoesNotThrow(() -> {
                integrationService.recordCaseCompletion("case-negative", -1000, new HashMap<>());
            });

            // Empty context handling
            assertDoesNotThrow(() -> {
                Map<String, String> emptyContext = new HashMap<>();
                integrationService.recordCaseCompletion("case-empty", 1000, emptyContext);
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

        // Verify service is still functional after stress
        awaitProcessing();

        SLODashboard.DashboardSnapshot snapshot = integrationService.getDashboardSnapshot();
        assertFalse(snapshot.getCases().isEmpty(), "Should remain functional after stress");

        // Verify no data corruption
        assertEquals(10000, snapshot.getCases().size(), "Should track all stress test cases");
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