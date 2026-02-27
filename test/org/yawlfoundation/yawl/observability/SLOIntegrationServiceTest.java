/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will and should be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test suite for SLOIntegrationService functionality.
 *
 * Tests:
 * - Service initialization and lifecycle
 * - Event batching and processing
 * - Component coordination (Tracker, Dashboard, Analytics, Alerts)
 * - Metrics aggregation
 * - Performance under load
 * - Graceful shutdown
 */
class SLOIntegrationServiceTest {

    private MeterRegistry meterRegistry;
    private AndonCord andonCord;
    private SLOIntegrationService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        andonCord = mock(AndonCord.class);
        service = new SLOIntegrationService.Builder()
            .meterRegistry(meterRegistry)
            .andonCord(andonCord)
            .enableDashboard(true)
            .enablePredictiveAnalytics(true)
            .enableAlertManager(true)
            .build();
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    @Test
    void testServiceInitialization() {
        // Initialize service
        service.initialize();
        assertEquals(SLOIntegrationService.ServiceStatus.STOPPED, service.getStatus());

        // Initialize should be idempotent
        service.initialize();
        assertEquals(SLOIntegrationService.ServiceStatus.STOPPED, service.getStatus());
    }

    @Test
    void testServiceStartAndStop() {
        // Initialize and start service
        service.initialize();
        service.start();
        assertEquals(SLOIntegrationService.ServiceStatus.RUNNING, service.getStatus());

        // Stop service
        service.shutdown();
        assertEquals(SLOIntegrationService.ServiceStatus.STOPPED, service.getStatus());
    }

    @Test
    void testEventRecordingAndProcessing() {
        // Initialize and start service
        service.initialize();
        service.start();

        // Record events
        Map<String, String> context = new HashMap<>();
        context.put("test", "value");

        service.recordCaseCompletion("case-1", 1000, context);
        service.recordTaskExecution("task-1", "case-1", 2000, context);
        service.recordQueueResponse("queue-1", 3000, context);

        // Wait for processing
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Verify events were processed
        Map<String, SLOTracker.ComplianceStatus> status = service.getComplianceStatus();
        assertNotNull(status);
        assertFalse(status.isEmpty());
    }

    @Test
    void testEventBatching() {
        // Initialize and start service
        service.initialize();
        service.start();

        // Record many events to test batching
        Map<String, String> context = new HashMap<>();
        context.put("batch_test", "true");

        for (int i = 0; i < 150; i++) {
            service.recordCaseCompletion("case-batch-" + i, i * 1000, context);
        }

        // Wait for processing
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Verify metrics
        assertTrue(service.getProcessedEventsCount() > 0);
        assertTrue(service.getEventQueueSize() >= 0);
    }

    @Test
    void testComponentIntegration() {
        // Initialize and start service
        service.initialize();
        service.start();

        // Record events to trigger all components
        Map<String, String> context = new HashMap<>();
        context.put("integration_test", "true");

        // Record violating case completion
        service.recordCaseCompletion("case-violation", 25 * 60 * 60 * 1000L, context);

        // Record task execution
        service.recordTaskExecution("task-violation", "case-violation", 90 * 60 * 1000L, context);

        // Wait for processing
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Verify all components are working
        Map<String, SLOTracker.ComplianceStatus> complianceStatus = service.getComplianceStatus();
        assertNotNull(complianceStatus);

        SLODashboard.DashboardSnapshot snapshot = service.getDashboardSnapshot();
        assertNotNull(snapshot);

        Map<String, SLOPredictiveAnalytics.PredictionResult> predictions = service.getPredictions();
        assertNotNull(predictions);

        Collection<SLOAlertManager.Alert> alerts = service.getActiveAlerts();
        assertNotNull(alerts);

        SLOAlertManager.AlertStats alertStats = service.getAlertStats();
        assertNotNull(alertStats);
    }

    @Test
    void testConcurrentEventRecording() {
        // Initialize and start service
        service.initialize();
        service.start();

        // Record events concurrently
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger eventsRecorded = new AtomicInteger(0);

        for (int i = 0; i < 500; i++) {
            final int eventId = i;
            executor.submit(() -> {
                Map<String, String> context = new HashMap<>();
                context.put("thread_id", String.valueOf(eventId));

                service.recordCaseCompletion("case-concurrent-" + eventId, eventId * 1000, context);
                eventsRecorded.incrementAndGet();
            });
        }

        executor.shutdown();
        try {
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Wait for final processing
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Verify all events were recorded
        assertEquals(500, eventsRecorded.get());
        assertTrue(service.getProcessedEventsCount() > 0);
    }

    @Test
    void testReportGeneration() {
        // Initialize and start service
        service.initialize();
        service.start();

        // Record some data
        Map<String, String> context = new HashMap<>();
        context.put("report_test", "true");

        for (int i = 0; i < 10; i++) {
            service.recordCaseCompletion("case-report-" + i, i * 1000, context);
        }

        // Wait for processing
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Test report generation
        String jsonReport = service.generateJsonReport(
            Instant.now().minusHours(1),
            Instant.now()
        );
        assertNotNull(jsonReport);
        assertFalse(jsonReport.isEmpty());

        String htmlReport = service.generateHtmlReport();
        assertNotNull(htmlReport);
        assertFalse(htmlReport.isEmpty());
        assertTrue(htmlReport.contains("<!DOCTYPE html>"));
    }

    @Test
    void testGracefulShutdown() {
        // Initialize and start service
        service.initialize();
        service.start();

        // Record events
        Map<String, String> context = new HashMap<>();
        context.put("shutdown_test", "true");

        for (int i = 0; i < 100; i++) {
            service.recordCaseCompletion("case-shutdown-" + i, i * 1000, context);
        }

        // Initiate shutdown
        long startTime = System.currentTimeMillis();
        service.shutdown();
        long endTime = System.currentTimeMillis();

        // Verify shutdown completed quickly
        assertTrue(endTime - startTime < 10000); // Should complete within 10 seconds

        // Verify status
        assertEquals(SLOIntegrationService.ServiceStatus.STOPPED, service.getStatus());

        // Verify remaining events were processed
        assertTrue(service.getProcessedEventsCount() > 0);
    }

    @Test
    void testServiceHealthCheck() {
        // Initialize and start service
        service.initialize();
        service.start();

        // Record some events
        Map<String, String> context = new HashMap<>();
        context.put("health_test", "true");

        service.recordCaseCompletion("case-health-1", 1000, context);
        service.recordCaseCompletion("case-health-2", 1000, context);

        // Wait for health check cycle
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Verify health metrics are present
        assertTrue(meterRegistry.find("yawl.slo.queue_size").gauge().value() >= 0);
        assertTrue(meterRegistry.find("yawl.slo.active_events").gauge().value() >= 0);
        assertTrue(meterRegistry.find("yawl.slo.active_batches").gauge().value() >= 0);
        assertTrue(meterRegistry.find("yawl.slo.processed_events").gauge().value() >= 0);
        assertTrue(meterRegistry.find("yawl.slo.failed_events").gauge().value() >= 0);
    }

    @Test
    void testNullHandling() {
        // Initialize service
        service.initialize();

        // Test null parameter handling
        assertThrows(NullPointerException.class, () -> {
            service.recordCaseCompletion(null, 1000, new HashMap<>());
        });

        assertThrows(NullPointerException.class, () -> {
            service.recordCaseCompletion("case-null", 1000, null);
        });
    }

    @Test
    void testComponentDisabling() {
        // Create service with disabled components
        SLOIntegrationService minimalService = new SLOIntegrationService.Builder()
            .meterRegistry(meterRegistry)
            .andonCord(andonCord)
            .enableDashboard(false)
            .enablePredictiveAnalytics(false)
            .enableAlertManager(false)
            .build();

        minimalService.initialize();
        minimalService.start();

        // Record events
        Map<String, String> context = new HashMap<>();
        context.put("minimal_test", "true");

        minimalService.recordCaseCompletion("case-minimal", 1000, context);

        // Wait for processing
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Verify service still works without optional components
        assertNotNull(minimalService.getComplianceStatus());
        assertNull(minimalService.getDashboardSnapshot());
        assertTrue(minimalService.getPredictions().isEmpty());
        assertTrue(minimalService.getActiveAlerts().isEmpty());

        minimalService.shutdown();
    }

    @Test
    void testEventTypes() {
        // Initialize and start service
        service.initialize();
        service.start();

        // Test all event types
        Map<String, String> context = new HashMap<>();
        context.put("event_type_test", "true");

        // Case completion
        service.recordCaseCompletion("case-event-types", 1000, context);

        // Task execution
        service.recordTaskExecution("task-event-types", "case-event-types", 2000, context);

        // Queue response
        service.recordQueueResponse("queue-event-types", 3000, context);

        // Virtual thread pinning
        service.recordVirtualThreadPinning("thread-event-types", false, context);

        // Lock contention
        service.recordLockContention("lock-event-types", 50, false, context);

        // Wait for processing
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Verify all events were recorded
        Map<String, SLOTracker.ComplianceStatus> status = service.getComplianceStatus();
        assertEquals(5, status.size());
    }

    @Test
    void testServiceBuilder() {
        // Test builder pattern
        SLOIntegrationService customService = new SLOIntegrationService.Builder()
            .meterRegistry(meterRegistry)
            .andonCord(andonCord)
            .enableDashboard(true)
            .enablePredictiveAnalytics(false)
            .enableAlertManager(true)
            .build();

        assertNotNull(customService);
        assertEquals(SLOIntegrationService.ServiceStatus.NOT_INITIALIZED, customService.getStatus());

        // Initialize and test
        customService.initialize();
        customService.start();
        assertEquals(SLOIntegrationService.ServiceStatus.RUNNING, customService.getStatus());

        customService.shutdown();
        assertEquals(SLOIntegrationService.ServiceStatus.STOPPED, customService.getStatus());
    }

    // ========================================
    // CASE COMPLETION TRACKING TESTS
    // ========================================

    @Test
    void testCaseCompletionHappyPathWithinSLO() {
        service.initialize();
        service.start();

        // Record case completion within SLO (5 minutes = 300000ms)
        Map<String, String> context = new HashMap<>();
        context.put("priority", "normal");
        context.put("workflow", "standard");

        service.recordCaseCompletion("case-happy", 120000, context);

        // Wait for processing
        awaitProcessing();

        // Verify compliance status shows within bounds
        Map<String, SLOTracker.ComplianceStatus> status = service.getComplianceStatus();
        SLOTracker.ComplianceStatus caseStatus = status.get("case-happy");
        assertNotNull(caseStatus);
        assertEquals(SLOTracker.ComplianceStatus.COMPLIANT, caseStatus.getStatus());
        assertTrue(caseStatus.getDurationMs() > 0);
        assertTrue(caseStatus.getSloMs() >= caseStatus.getDurationMs());
    }

    @Test
    void testCaseCompletionViolationExceedsSLO() {
        service.initialize();
        service.start();

        // Record case completion exceeding SLO (25 minutes = 1500000ms)
        Map<String, String> context = new HashMap<>();
        context.put("priority", "high");
        context.put("workflow", "complex");

        service.recordCaseCompletion("case-violation", 2500000, context);

        // Wait for processing
        awaitProcessing();

        // Verify compliance status shows violation
        Map<String, SLOTracker.ComplianceStatus> status = service.getComplianceStatus();
        SLOTracker.ComplianceStatus caseStatus = status.get("case-violation");
        assertNotNull(caseStatus);
        assertEquals(SLOTracker.ComplianceStatus.VIOLATION, caseStatus.getStatus());
        assertTrue(caseStatus.getDurationMs() > caseStatus.getSloMs());
    }

    @Test
    void testCaseCompletionZeroDuration() {
        service.initialize();
        service.start();

        // Record case with zero duration (edge case)
        Map<String, String> context = new HashMap<>();
        context.put("workflow", "instant");

        service.recordCaseCompletion("case-zero", 0, context);

        // Wait for processing
        awaitProcessing();

        // Verify zero duration is handled
        Map<String, SLOTracker.ComplianceStatus> status = service.getComplianceStatus();
        SLOTracker.ComplianceStatus caseStatus = status.get("case-zero");
        assertNotNull(caseStatus);
        assertEquals(SLOTracker.ComplianceStatus.COMPLIANT, caseStatus.getStatus());
        assertEquals(0, caseStatus.getDurationMs());
    }

    @Test
    void testCaseCompletionNegativeDuration() {
        service.initialize();
        service.start();

        // Record case with negative duration (should be treated as 0)
        Map<String, String> context = new HashMap<>();
        context.put("workflow", "error");

        service.recordCaseCompletion("case-negative", -1000, context);

        // Wait for processing
        awaitProcessing();

        // Verify negative duration is normalized
        Map<String, SLOTracker.ComplianceStatus> status = service.getComplianceStatus();
        SLOTracker.ComplianceStatus caseStatus = status.get("case-negative");
        assertNotNull(caseStatus);
        assertEquals(SLOTracker.ComplianceStatus.COMPLIANT, caseStatus.getStatus());
        assertEquals(0, caseStatus.getDurationMs());
    }

    @Test
    void testCaseCompletionContextPropagation() {
        service.initialize();
        service.start();

        // Record case with detailed context
        Map<String, String> context = new HashMap<>();
        context.put("workflow", "approval");
        context.put("priority", "urgent");
        context.put("department", "finance");
        context.put("approver", "john.doe@company.com");
        context.put("amount", "50000");

        service.recordCaseCompletion("case-context", 300000, context);

        // Wait for processing
        awaitProcessing();

        // Verify context is preserved and enriched
        Map<String, SLOTracker.ComplianceStatus> status = service.getComplianceStatus();
        SLOTracker.ComplianceStatus caseStatus = status.get("case-context");
        assertNotNull(caseStatus);
        assertEquals(SLOTracker.ComplianceStatus.COMPLIANT, caseStatus.getStatus());

        // Check dashboard has enriched context
        SLODashboard.DashboardSnapshot snapshot = service.getDashboardSnapshot();
        assertNotNull(snapshot);
        assertFalse(snapshot.getCases().isEmpty());

        SLODashboard.CaseMetrics caseMetrics = snapshot.getCases().get("case-context");
        assertNotNull(caseMetrics);
        assertFalse(caseMetrics.getContext().isEmpty());
        assertTrue(caseMetrics.getContext().containsKey("workflow"));
        assertTrue(caseMetrics.getContext().containsKey("priority"));
        assertTrue(caseMetrics.getContext().containsKey("department"));
    }

    @Test
    void testCaseCompletionConcurrentTracking() {
        service.initialize();
        service.start();

        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // Record many cases concurrently
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                Map<String, String> context = new HashMap<>();
                context.put("thread", String.valueOf(threadId));

                for (int j = 0; j < 20; j++) {
                    service.recordCaseCompletion("case-concurrent-" + threadId + "-" + j,
                        (threadId * 1000) + (j * 100), context);
                }
                latch.countDown();
            });
        }

        // Wait for all threads to complete
        try {
            assertTrue(latch.await(30, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        executor.shutdown();

        // Wait for final processing
        awaitProcessing();

        // Verify all cases were tracked
        Map<String, SLOTracker.ComplianceStatus> status = service.getComplianceStatus();
        assertEquals(100, status.size()); // 5 threads * 20 cases each

        // Verify no duplicates and all have valid status
        status.values().forEach(s -> {
            assertNotNull(s);
            assertTrue(s.getStatus() == SLOTracker.ComplianceStatus.COMPLIANT ||
                       s.getStatus() == SLOTracker.ComplianceStatus.VIOLATION);
            assertTrue(s.getDurationMs() >= 0);
        });
    }

    // ========================================
    // TASK EXECUTION TRACKING TESTS
    // ========================================

    @Test
    void testTaskExecutionWithinBounds() {
        service.initialize();
        service.start();

        // Record task execution within bounds (2 seconds = 2000ms)
        Map<String, String> context = new HashMap<>();
        context.put("task_type", "validation");
        context.put("complexity", "low");

        service.recordTaskExecution("task-normal", "case-normal", 1500, context);

        // Wait for processing
        awaitProcessing();

        // Verify task tracking
        Map<String, SLOTracker.ComplianceStatus> status = service.getComplianceStatus();
        // Case should exist
        assertTrue(status.containsKey("case-normal"));

        // Check dashboard for task details
        SLODashboard.DashboardSnapshot snapshot = service.getDashboardSnapshot();
        assertNotNull(snapshot.getCases());
        SLODashboard.CaseMetrics caseMetrics = snapshot.getCases().get("case-normal");
        assertNotNull(caseMetrics);
        assertFalse(caseMetrics.getTasks().isEmpty());

        SLODashboard.TaskMetrics taskMetrics = caseMetrics.getTasks().get("task-normal");
        assertNotNull(taskMetrics);
        assertTrue(taskMetrics.getDurationMs() > 0);
    }

    @Test
    void testSlowTaskDetection() {
        service.initialize();
        service.start();

        // Record slow task execution (30 seconds = 30000ms)
        Map<String, String> context = new HashMap<>();
        context.put("task_type", "data_processing");
        context.put("complexity", "high");

        service.recordTaskExecution("task-slow", "case-slow", 45000, context);

        // Wait for processing
        awaitProcessing();

        // Verify slow task detection
        Map<String, SLOTracker.ComplianceStatus> status = service.getComplianceStatus();
        SLOTracker.ComplianceStatus caseStatus = status.get("case-slow");
        assertNotNull(caseStatus);

        // Dashboard should show slow task flag
        SLODashboard.DashboardSnapshot snapshot = service.getDashboardSnapshot();
        SLODashboard.CaseMetrics caseMetrics = snapshot.getCases().get("case-slow");
        assertNotNull(caseMetrics);

        SLODashboard.TaskMetrics slowTask = caseMetrics.getTasks().get("task-slow");
        assertNotNull(slowTask);
        assertTrue(slowTask.isSlowTask());
        assertEquals(45000, slowTask.getDurationMs());
    }

    @Test
    void testTaskExecutionMultiTaskScenario() {
        service.initialize();
        service.start();

        String caseId = "case-multi";
        Map<String, String> context = new HashMap<>();
        context.put("workflow", "parallel_tasks");

        // Record multiple tasks for the same case
        service.recordTaskExecution("task-fast", caseId, 500, context);
        service.recordTaskExecution("task-medium", caseId, 2000, context);
        service.recordTaskExecution("task-slow", caseId, 8000, context);

        // Wait for processing
        awaitProcessing();

        // Verify all tasks are tracked
        SLODashboard.DashboardSnapshot snapshot = service.getDashboardSnapshot();
        SLODashboard.CaseMetrics caseMetrics = snapshot.getCases().get(caseId);
        assertNotNull(caseMetrics);
        assertEquals(3, caseMetrics.getTasks().size());

        // Verify task durations
        SLODashboard.TaskMetrics fastTask = caseMetrics.getTasks().get("task-fast");
        SLODashboard.TaskMetrics mediumTask = caseMetrics.getTasks().get("task-medium");
        SLODashboard.TaskMetrics slowTask = caseMetrics.getTasks().get("task-slow");

        assertEquals(500, fastTask.getDurationMs());
        assertEquals(2000, mediumTask.getDurationMs());
        assertEquals(8000, slowTask.getDurationMs());
        assertTrue(slowTask.isSlowTask());
    }

    @Test
    void testTaskExecutionContextEnrichment() {
        service.initialize();
        service.start();

        // Record task with rich context
        Map<String, String> context = new HashMap<>();
        context.put("task_type", "payment_processing");
        context.put("currency", "USD");
        context.put("amount", "1250.00");
        context.put("processor", "stripe");
        context.put("region", "NA");

        service.recordTaskExecution("task-payment", "case-payment", 3500, context);

        // Wait for processing
        awaitProcessing();

        // Verify context enrichment
        SLODashboard.DashboardSnapshot snapshot = service.getDashboardSnapshot();
        SLODashboard.CaseMetrics caseMetrics = snapshot.getCases().get("case-payment");
        assertNotNull(caseMetrics);

        SLODashboard.TaskMetrics taskMetrics = caseMetrics.getTasks().get("task-payment");
        assertNotNull(taskMetrics);
        assertFalse(taskMetrics.getContext().isEmpty());

        // Verify enriched context
        assertTrue(taskMetrics.getContext().contains("payment_processing"));
        assertTrue(taskMetrics.getContext().contains("currency"));
        assertTrue(taskMetrics.getContext().contains("amount"));
        assertTrue(taskMetrics.getContext().contains("processor"));
        assertTrue(taskMetrics.getContext().contains("region"));
    }

    @Test
    void testTaskExecutionThreadSafety() {
        service.initialize();
        service.start();

        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger taskCount = new AtomicInteger(0);

        // Record tasks concurrently from multiple threads
        for (int i = 0; i < 500; i++) {
            final int taskId = i;
            final int threadId = i % threadCount;
            executor.submit(() -> {
                Map<String, String> context = new HashMap<>();
                context.put("thread", String.valueOf(threadId));
                context.put("batch", String.valueOf(taskId / 50));

                service.recordTaskExecution("task-" + taskId, "case-" + (taskId / 10),
                    taskId % 1000 + 100, context);
                taskCount.incrementAndGet();
            });
        }

        executor.shutdown();
        try {
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Wait for final processing
        awaitProcessing();

        // Verify no data corruption
        SLODashboard.DashboardSnapshot snapshot = service.getDashboardSnapshot();
        int actualCaseCount = snapshot.getCases().size();
        assertEquals(50, actualCaseCount); // 500 tasks / 10 tasks per case

        // Verify all tasks under each case
        snapshot.getCases().values().forEach(caseMetrics -> {
            int taskCountForCase = caseMetrics.getTasks().size();
            assertTrue(taskCountForCase > 0);
            caseMetrics.getTasks().values().forEach(task -> {
                assertTrue(task.getDurationMs() > 0);
            });
        });
    }

    // ========================================
    // QUEUE RESPONSE TESTS
    // ========================================

    @Test
    void testFastQueueResponse() {
        service.initialize();
        service.start();

        // Record fast queue response (10ms)
        Map<String, String> context = new HashMap<>();
        context.put("queue_type", "task_queue");
        context.put("priority", "high");

        service.recordQueueResponse("queue-fast", 10, context);

        // Wait for processing
        awaitProcessing();

        // Verify fast response recorded
        SLODashboard.DashboardSnapshot snapshot = service.getDashboardSnapshot();
        SLODashboard.QueueMetrics queueMetrics = snapshot.getQueues().get("queue-fast");
        assertNotNull(queueMetrics);
        assertEquals(10, queueMetrics.getResponseTimeMs());
        assertFalse(queueMetrics.isSlowQueue());
    }

    @Test
    void testSlowQueueDetection() {
        service.initialize();
        service.start();

        // Record slow queue response (5 seconds = 5000ms)
        Map<String, String> context = new HashMap<>();
        context.put("queue_type", "workitem_queue");
        context.put("priority", "normal");

        service.recordQueueResponse("queue-slow", 5500, context);

        // Wait for processing
        awaitProcessing();

        // Verify slow queue detection
        SLODashboard.DashboardSnapshot snapshot = service.getDashboardSnapshot();
        SLODashboard.QueueMetrics queueMetrics = snapshot.getQueues().get("queue-slow");
        assertNotNull(queueMetrics);
        assertEquals(5500, queueMetrics.getResponseTimeMs());
        assertTrue(queueMetrics.isSlowQueue());
    }

    @Test
    void testMultipleQueues() {
        service.initialize();
        service.start();

        Map<String, String> context = new HashMap<>();
        context.put("env", "production");

        // Record responses from multiple queues
        service.recordQueueResponse("queue-api", 50, context);
        service.recordQueueResponse("queue-db", 100, context);
        service.recordQueueResponse("queue-cache", 5, context);
        service.recordQueueResponse("queue-worker", 2000, context);

        // Wait for processing
        awaitProcessing();

        // Verify all queues tracked
        SLODashboard.DashboardSnapshot snapshot = service.getDashboardSnapshot();
        assertEquals(4, snapshot.getQueues().size());

        // Verify individual queue metrics
        SLODashboard.QueueMetrics apiQueue = snapshot.getQueues().get("queue-api");
        SLODashboard.QueueMetrics dbQueue = snapshot.getQueues().get("queue-db");
        SLODashboard.QueueMetrics cacheQueue = snapshot.getQueues().get("queue-cache");
        SLODashboard.QueueMetrics workerQueue = snapshot.getQueues().get("queue-worker");

        assertEquals(50, apiQueue.getResponseTimeMs());
        assertEquals(100, dbQueue.getResponseTimeMs());
        assertEquals(5, cacheQueue.getResponseTimeMs());
        assertEquals(2000, workerQueue.getResponseTimeMs());
        assertTrue(workerQueue.isSlowQueue());
    }

    @Test
    void testBurstQueueHandling() {
        service.initialize();
        service.start();

        Map<String, String> context = new HashMap<>();
        context.put("queue_type", "event_queue");

        // Record burst of queue responses
        for (int i = 0; i < 100; i++) {
            int responseTime = (i < 90) ? 10 + (i % 10) : 5000; // 90 fast, 10 slow
            service.recordQueueResponse("queue-burst-" + i, responseTime, context);
        }

        // Wait for processing
        awaitProcessing();

        // Verify burst handling
        SLODashboard.DashboardSnapshot snapshot = service.getDashboardSnapshot();

        // Check overall queue statistics
        assertTrue(snapshot.getAverageQueueResponseTime() > 0);
        assertTrue(snapshot.getSlowQueueCount() >= 10); // At least 10 slow responses

        // Check percentiles
        assertTrue(snapshot.getQueueResponseP95() > 0);
        assertTrue(snapshot.getQueueResponseP99() > 0);
        assertTrue(snapshot.getQueueResponseP99() >= snapshot.getQueueResponseP95());
    }

    // ========================================
    // VIRTUAL THREAD PINNING TESTS
    // ========================================

    @Test
    void testVirtualThreadNoPinningScenario() {
        service.initialize();
        service.start();

        // Record virtual thread without pinning
        Map<String, String> context = new HashMap<>();
        context.put("thread_usage", "io_bound");
        context.put("carrier_count", "1");

        service.recordVirtualThreadPinning("thread-no-pin", false, context);

        // Wait for processing
        awaitProcessing();

        // Verify no pinning recorded
        SLODashboard.DashboardSnapshot snapshot = service.getDashboardSnapshot();
        SLODashboard.VirtualThreadMetrics threadMetrics = snapshot.getVirtualThreads().get("thread-no-pin");
        assertNotNull(threadMetrics);
        assertFalse(threadMetrics.isPinned());
        assertEquals(1, threadMetrics.getCarrierThreadCount());
        assertEquals(0, threadMetrics.getPinningRate());
    }

    @Test
    void testVirtualThreadPinningDetection() {
        service.initialize();
        service.start();

        // Record virtual thread with pinning
        Map<String, String> context = new HashMap<>();
        context.put("thread_usage", "cpu_bound");
        context.put("carrier_count", "2");

        service.recordVirtualThreadPinning("thread-pinned", true, context);

        // Wait for processing
        awaitProcessing();

        // Verify pinning detection
        SLODashboard.DashboardSnapshot snapshot = service.getDashboardSnapshot();
        SLODashboard.VirtualThreadMetrics threadMetrics = snapshot.getVirtualThreads().get("thread-pinned");
        assertNotNull(threadMetrics);
        assertTrue(threadMetrics.isPinned());
        assertEquals(2, threadMetrics.getCarrierThreadCount());
        assertTrue(threadMetrics.getPinningRate() > 0);
    }

    @Test
    void testVirtualThreadPinningRateCalculation() {
        service.initialize();
        service.start();

        // Record multiple pinning events with different frequencies
        Map<String, String> context = new HashMap<>();
        context.put("workload", "mixed");

        // Low pinning rate (1 out of 10)
        for (int i = 0; i < 10; i++) {
            service.recordVirtualThreadPinning("thread-low-pin-" + i, (i == 5), context);
        }

        // High pinning rate (8 out of 10)
        for (int i = 0; i < 10; i++) {
            service.recordVirtualThreadPinning("thread-high-pin-" + i, (i < 8), context);
        }

        // Wait for processing
        awaitProcessing();

        // Verify pinning rate calculations
        SLODashboard.DashboardSnapshot snapshot = service.getDashboardSnapshot();

        SLODashboard.VirtualThreadMetrics lowPinMetrics = snapshot.getVirtualThreads().get("thread-low-pin-5");
        SLODashboard.VirtualThreadMetrics highPinMetrics = snapshot.getVirtualThreads().get("thread-high-pin-5");

        assertNotNull(lowPinMetrics);
        assertNotNull(highPinMetrics);

        assertTrue(lowPinMetrics.getPinningRate() < highPinMetrics.getPinningRate());
    }

    @Test
    void testVirtualThreadCarrierThreadTracking() {
        service.initialize();
        service.start();

        // Record virtual thread with carrier thread information
        Map<String, String> context = new HashMap<>();
        context.put("carrier_threads", "3");
        context.put("pinning_duration", "100");

        service.recordVirtualThreadPinning("thread-carriers", true, context);

        // Wait for processing
        awaitProcessing();

        // Verify carrier thread tracking
        SLODashboard.DashboardSnapshot snapshot = service.getDashboardSnapshot();
        SLODashboard.VirtualThreadMetrics threadMetrics = snapshot.getVirtualThreads().get("thread-carriers");
        assertNotNull(threadMetrics);
        assertEquals(3, threadMetrics.getCarrierThreadCount());
        assertTrue(threadMetrics.getPinningRate() > 0);
    }

    @Test
    void testVirtualThreadAlertTriggering() {
        service.initialize();
        service.start();

        // Record pinning events that should trigger alerts
        Map<String, String> context = new HashMap<>();
        context.put("threshold", "high");

        for (int i = 0; i < 100; i++) {
            service.recordVirtualThreadPinning("thread-alert-" + i, true, context);
        }

        // Wait for processing
        awaitProcessing();

        // Verify alerts triggered
        Collection<SLOAlertManager.Alert> alerts = service.getActiveAlerts();
        boolean virtualThreadAlertFound = alerts.stream()
            .anyMatch(alert -> alert.getType().contains("virtual_thread") ||
                              alert.getType().contains("pinning"));

        // At least one virtual thread alert should be triggered
        assertTrue(virtualThreadAlertFound);
    }

    // ========================================
    // LOCK CONTENTION TESTS
    // ========================================

    @Test
    void testLockContentionNoContention() {
        service.initialize();
        service.start();

        // Record lock with no contention
        Map<String, String> context = new HashMap<>();
        context.put("lock_type", "reentrant");
        context.put("contention_level", "none");

        service.recordLockContention("lock-no-contention", 5, false, context);

        // Wait for processing
        awaitProcessing();

        // Verify no contention recorded
        SLODashboard.DashboardSnapshot snapshot = service.getDashboardSnapshot();
        SLODashboard.LockMetrics lockMetrics = snapshot.getLocks().get("lock-no-contention");
        assertNotNull(lockMetrics);
        assertEquals(5, lockMetrics.getContentionDurationMs());
        assertFalse(lockMetrics.isContended());
        assertEquals(0, lockMetrics.getContentionCount());
    }

    @Test
    void testLockContentionDetection() {
        service.initialize();
        service.start();

        // Record lock with contention
        Map<String, String> context = new HashMap<>();
        context.put("lock_type", "synchronized");
        context.put("contention_level", "high");

        service.recordLockContention("lock-contented", 100, true, context);

        // Wait for processing
        awaitProcessing();

        // Verify contention detection
        SLODashboard.DashboardSnapshot snapshot = service.getDashboardSnapshot();
        SLODashboard.LockMetrics lockMetrics = snapshot.getLocks().get("lock-contented");
        assertNotNull(lockMetrics);
        assertEquals(100, lockMetrics.getContentionDurationMs());
        assertTrue(lockMetrics.isContended());
        assertTrue(lockMetrics.getContentionCount() > 0);
    }

    @Test
    void testLockContentionDurationTracking() {
        service.initialize();
        service.start();

        // Record lock contention with various durations
        Map<String, String> context = new HashMap<>();
        context.put("lock_type", "reentrant");

        service.recordLockContention("lock-short", 10, true, context);
        service.recordLockContention("lock-medium", 50, true, context);
        service.recordLockContention("lock-long", 200, true, context);

        // Wait for processing
        awaitProcessing();

        // Verify duration tracking
        SLODashboard.DashboardSnapshot snapshot = service.getDashboardSnapshot();

        SLODashboard.LockMetrics shortLock = snapshot.getLocks().get("lock-short");
        SLODashboard.LockMetrics mediumLock = snapshot.getLocks().get("lock-medium");
        SLODashboard.LockMetrics longLock = snapshot.getLocks().get("lock-long");

        assertEquals(10, shortLock.getContentionDurationMs());
        assertEquals(50, mediumLock.getContentionDurationMs());
        assertEquals(200, longLock.getContentionDurationMs());

        assertTrue(shortLock.getContentionCount() > 0);
        assertTrue(mediumLock.getContentionCount() > 0);
        assertTrue(longLock.getContentionCount() > 0);
    }

    @Test
    void testLockContentionHotLockIdentification() {
        service.initialize();
        service.start();

        // Record many contention events for the same lock (hot lock)
        Map<String, String> context = new HashMap<>();
        context.put("lock_type", "database_lock");

        for (int i = 0; i < 100; i++) {
            service.recordLockContention("lock-hot", 20, true, context);
        }

        // Wait for processing
        awaitProcessing();

        // Verify hot lock identification
        SLODashboard.DashboardSnapshot snapshot = service.getDashboardSnapshot();
        SLODashboard.LockMetrics hotLock = snapshot.getLocks().get("lock-hot");
        assertNotNull(hotLock);
        assertTrue(hotLock.isContended());
        assertTrue(hotLock.getContentionCount() >= 100);
        assertTrue(hotLock.getContentionDurationMs() >= 2000); // 100 * 20ms

        // Check if identified as hot lock
        assertTrue(hotLock.isHotLock());
    }

    @Test
    void testLockContentionAlertEscalation() {
        service.initialize();
        service.start();

        // Record lock contention that should trigger alerts
        Map<String, String> context = new HashMap<>();
        context.put("severity", "critical");

        for (int i = 0; i < 50; i++) {
            service.recordLockContention("lock-alert-" + i, 100, true, context);
        }

        // Wait for processing
        awaitProcessing();

        // Verify alerts triggered
        Collection<SLOAlertManager.Alert> alerts = service.getActiveAlerts();
        boolean lockContentionAlertFound = alerts.stream()
            .anyMatch(alert -> alert.getType().contains("lock_contention") ||
                              alert.getType().contains("hot_lock"));

        // At least one lock contention alert should be triggered
        assertTrue(lockContentionAlertFound);
    }

    // ========================================
    // INTEGRATION TESTS
    // ========================================

    @Test
    void testEndToEndSLOFlow() {
        service.initialize();
        service.start();

        // Simulate complete workflow case
        Map<String, String> caseContext = new HashMap<>();
        caseContext.put("workflow", "order_processing");
        caseContext.put("customer", "enterprise_client");

        // Case completion
        service.recordCaseCompletion("case-order-123", 180000, caseContext); // 3 minutes

        // Task executions
        Map<String, String> taskContext = new HashMap<>();
        taskContext.put("case", "case-order-123");
        service.recordTaskExecution("task-validate", "case-order-123", 500, taskContext);
        service.recordTaskExecution("task-fulfill", "case-order-123", 45000, taskContext); // 45 seconds

        // Queue responses
        service.recordQueueResponse("queue-orders", 25, taskContext);
        service.recordQueueResponse("queue-inventory", 150, taskContext);

        // Virtual thread pinning
        service.recordVirtualThreadPinning("thread-order-123", false, taskContext);

        // Lock contention
        service.recordLockContention("lock-inventory", 10, false, taskContext);

        // Wait for processing
        awaitProcessing();

        // Verify complete flow
        Map<String, SLOTracker.ComplianceStatus> complianceStatus = service.getComplianceStatus();
        assertNotNull(complianceStatus);
        assertTrue(complianceStatus.containsKey("case-order-123"));

        SLODashboard.DashboardSnapshot snapshot = service.getDashboardSnapshot();
        assertNotNull(snapshot);
        assertFalse(snapshot.getCases().isEmpty());
        assertFalse(snapshot.getQueues().isEmpty());
        assertFalse(snapshot.getVirtualThreads().isEmpty());
        assertFalse(snapshot.getLocks().isEmpty());

        // Verify predictive analytics
        Map<String, SLOPredictiveAnalytics.PredictionResult> predictions = service.getPredictions();
        assertFalse(predictions.isEmpty());
        assertTrue(predictions.containsKey("case-order-123"));
    }

    @Test
    void testAndonCordIntegration() {
        service.initialize();
        service.start();

        // Record data that should trigger AndonCord alerts
        Map<String, String> context = new HashMap<>();
        context.put("severity", "critical");

        // Violating case completion
        service.recordCaseCompletion("case-andon-violation", 3600000, context); // 1 hour

        // Multiple slow tasks
        for (int i = 0; i < 10; i++) {
            service.recordTaskExecution("task-andon-slow-" + i, "case-andon-slow",
                120000, context); // 2 minutes each
        }

        // Wait for processing
        awaitProcessing();

        // Verify AndonCord integration
        // Verify alerts were sent to AndonCord (mock verification)
        verify(andonCord, atLeastOnce()).triggerAlert(any(), any(), any());

        Collection<SLOAlertManager.Alert> alerts = service.getActiveAlerts();
        assertFalse(alerts.isEmpty());

        // Check that AndonCord received the alerts
        for (SLOAlertManager.Alert alert : alerts) {
            assertNotNull(alert.getMessage());
            assertFalse(alert.getSeverity().isEmpty());
        }
    }

    @Test
    void testMetricsExport() {
        service.initialize();
        service.start();

        // Record various events
        Map<String, String> context = new HashMap<>();
        context.put("env", "test");

        service.recordCaseCompletion("case-metrics", 1000, context);
        service.recordTaskExecution("task-metrics", "case-metrics", 2000, context);
        service.recordQueueResponse("queue-metrics", 50, context);

        // Wait for processing
        awaitProcessing();

        // Test metrics export
        Map<String, Object> exportedMetrics = service.exportMetrics();
        assertNotNull(exportedMetrics);
        assertFalse(exportedMetrics.isEmpty());

        // Verify metric structure
        assertTrue(exportedMetrics.containsKey("total_cases"));
        assertTrue(exportedMetrics.containsKey("total_tasks"));
        assertTrue(exportedMetrics.containsKey("total_queues"));
        assertTrue(exportedMetrics.containsKey("compliance_rate"));
        assertTrue(exportedMetrics.containsKey("average_case_duration"));

        // Verify metric values
        assertTrue((int) exportedMetrics.get("total_cases") > 0);
        assertTrue((int) exportedMetrics.get("total_tasks") > 0);
        assertTrue((int) exportedMetrics.get("total_queues") > 0);
        assertTrue((double) exportedMetrics.get("compliance_rate") >= 0);
        assertTrue((double) exportedMetrics.get("average_case_duration") > 0);
    }

    @Test
    void testDashboardData() {
        service.initialize();
        service.start();

        // Record data for dashboard visualization
        Map<String, String> context = new HashMap<>();
        context.put("department", "finance");

        // Cases
        service.recordCaseCompletion("case-finance-1", 60000, context);
        service.recordCaseCompletion("case-finance-2", 120000, context);

        // Tasks
        service.recordTaskExecution("task-finance-1", "case-finance-1", 1000, context);
        service.recordTaskExecution("task-finance-2", "case-finance-1", 2000, context);

        // Queues
        service.recordQueueResponse("queue-finance", 30, context);

        // Wait for processing
        awaitProcessing();

        // Test dashboard data generation
        SLODashboard.DashboardSnapshot snapshot = service.getDashboardSnapshot();
        assertNotNull(snapshot);

        // Verify timeline data
        assertFalse(snapshot.getTimeline().isEmpty());

        // Verify compliance distribution
        Map<SLOTracker.ComplianceStatus, Integer> complianceDist = snapshot.getComplianceDistribution();
        assertNotNull(complianceDist);
        assertTrue(complianceDist.containsKey(SLOTracker.ComplianceStatus.COMPLIANT));
        assertTrue(complianceDist.get(SLOTracker.ComplianceStatus.COMPLIANT) >= 2);

        // Verify department breakdown
        Map<String, Integer> deptBreakdown = snapshot.getDepartmentBreakdown();
        assertNotNull(deptBreakdown);
        assertTrue(deptBreakdown.containsKey("finance"));
        assertEquals(2, deptBreakdown.get("finance"));
    }

    @Test
    void testTrendAnalysis() {
        service.initialize();
        service.start();

        // Record historical data for trend analysis
        Map<String, String> context = new HashMap<>();
        context.put("period", "morning");

        // Simulate data over time
        for (int hour = 0; hour < 24; hour++) {
            for (int i = 0; i < 5; i++) {
                service.recordCaseCompletion("case-" + hour + "-" + i,
                    (hour * 60000) + (i * 10000), context);
            }
        }

        // Wait for processing
        awaitProcessing();

        // Test trend analysis
        Map<String, Object> trends = service.analyzeTrends();
        assertNotNull(trends);
        assertFalse(trends.isEmpty());

        // Verify trend calculations
        assertTrue(trends.containsKey("compliance_trend"));
        assertTrue(trends.containsKey("duration_trend"));
        assertTrue(trends.containsKey("throughput_trend"));
        assertTrue(trends.containsKey("violation_pattern"));

        // Verify trend values
        Map<String, Double> complianceTrend = (Map<String, Double>) trends.get("compliance_trend");
        assertNotNull(complianceTrend);
        assertTrue(complianceTrend.containsKey("slope"));
        assertTrue(complianceTrend.containsKey("correlation"));

        // Check pattern detection
        Map<String, Object> pattern = (Map<String, Object>) trends.get("violation_pattern");
        assertNotNull(pattern);
        assertTrue(pattern.containsKey("detected"));
        assertTrue(pattern.containsKey("confidence"));
    }

    // ========================================
    // HELPER METHODS
    // ========================================

    /**
     * Helper method to wait for SLO processing to complete.
     */
    private void awaitProcessing() {
        try {
            Thread.sleep(2000); // Wait for async processing
        } catch (InterruptedException e) {
            fail("Test interrupted during processing wait");
        }
    }
}