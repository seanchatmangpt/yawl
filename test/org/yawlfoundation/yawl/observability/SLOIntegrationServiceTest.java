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
}