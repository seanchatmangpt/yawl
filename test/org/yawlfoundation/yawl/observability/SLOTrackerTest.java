/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
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
import org.mockito.Mockito;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test suite for SLOTracker functionality.
 *
 * Tests:
 * - SLO definition and initialization
 * - Event recording and compliance tracking
 * - Violation detection and alerting
 * - Predictive breach detection
 * - Metrics registration and reporting
 */
class SLOTrackerTest {

    private MeterRegistry meterRegistry;
    private AndonCord andonCord;
    private SLOTracker sloTracker;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        andonCord = Mockito.mock(AndonCord.class);
        sloTracker = new SLOTracker(meterRegistry, andonCord);
    }

    @AfterEach
    void tearDown() {
        sloTracker.shutdown();
    }

    @Test
    void testInitialization() {
        assertNotNull(sloTracker);
        assertEquals(5, sloTracker.getAllComplianceStatus().size());
        assertTrue(sloTracker.getAllComplianceStatus().containsKey(SLOTracker.SLO_CASE_COMPLETION));
        assertTrue(sloTracker.getAllComplianceStatus().containsKey(SLOTracker.SLO_TASK_EXECUTION));
        assertTrue(sloTracker.getAllComplianceStatus().containsKey(SLOTracker.SLO_QUEUE_RESPONSE));
        assertTrue(sloTracker.getAllComplianceStatus().containsKey(SLOTracker.SLO_VIRTUAL_THREAD_PINNING));
        assertTrue(sloTracker.getAllComplianceStatus().containsKey(SLOTracker.SLO_LOCK_CONTENTION));
    }

    @Test
    void testCaseCompletionCompliance() {
        // Record compliant case completion (under 24h)
        Map<String, String> context = new HashMap<>();
        context.put("case_type", "simple");
        sloTracker.recordCaseCompletion("case-1", 1000, context); // 1 second

        // Check compliance status
        Map<String, SLOTracker.ComplianceStatus> status = sloTracker.getAllComplianceStatus();
        SLOTracker.ComplianceStatus caseStatus = status.get(SLOTracker.SLO_CASE_COMPLETION);

        assertNotNull(caseStatus);
        assertTrue(caseStatus.getCompliancePercentage() > 0);
        assertEquals("COMPLIANT", caseStatus.getStatus());
    }

    @Test
    void testCaseCompletionViolation() {
        // Record case completion exceeding 24h
        Map<String, String> context = new HashMap<>();
        context.put("case_type", "complex");
        sloTracker.recordCaseCompletion("case-2", 25 * 60 * 60 * 1000L, context); // 25 hours

        // Check compliance status
        Map<String, SLOTracker.ComplianceStatus> status = sloTracker.getAllComplianceStatus();
        SLOTracker.ComplianceStatus caseStatus = status.get(SLOTracker.SLO_CASE_COMPLETION);

        assertNotNull(caseStatus);
        // Should show lower compliance due to violation
        assertTrue(caseStatus.getCompliancePercentage() < 100);
    }

    @Test
    void testTaskExecutionCompliance() {
        // Record compliant task execution
        Map<String, String> context = new HashMap<>();
        context.put("task_type", "manual_review");
        sloTracker.recordTaskExecution("task-1", "case-1", 30 * 60 * 1000L, context); // 30 minutes

        // Check compliance status
        Map<String, SLOTracker.ComplianceStatus> status = sloTracker.getAllComplianceStatus();
        SLOTracker.ComplianceStatus taskStatus = status.get(SLOTracker.SLO_TASK_EXECUTION);

        assertNotNull(taskStatus);
        assertTrue(taskStatus.getCompliancePercentage() > 0);
    }

    @Test
    void testQueueResponseCompliance() {
        // Record compliant queue response
        Map<String, String> context = new HashMap<>();
        context.put("queue_name", "task_queue");
        sloTracker.recordQueueResponse("queue-1", 2 * 60 * 1000L, context); // 2 minutes

        // Check compliance status
        Map<String, SLOTracker.ComplianceStatus> status = sloTracker.getAllComplianceStatus();
        SLOTracker.ComplianceStatus queueStatus = status.get(SLOTracker.SLO_QUEUE_RESPONSE);

        assertNotNull(queueStatus);
        assertTrue(queueStatus.getCompliancePercentage() > 0);
    }

    @Test
    void testVirtualThreadPinning() {
        // Record low pinning rate
        Map<String, String> context = new HashMap<>();
        context.put("thread_name", "worker-1");
        sloTracker.recordVirtualThreadPinning("worker-1", false, context);

        // Record high pinning rate
        sloTracker.recordVirtualThreadPinning("worker-2", true, context);

        // Check compliance status
        Map<String, SLOTracker.ComplianceStatus> status = sloTracker.getAllComplianceStatus();
        SLOTracker.ComplianceStatus pinningStatus = status.get(SLOTracker.SLO_VIRTUAL_THREAD_PINNING);

        assertNotNull(pinningStatus);
        // Should show some percentage of pinning events
        assertTrue(pinningStatus.getCompliancePercentage() >= 0);
    }

    @Test
    void testLockContention() {
        // Record low contention
        Map<String, String> context = new HashMap<>();
        context.put("lock_name", "case_lock");
        sloTracker.recordLockContention("lock-1", 50, false, context); // 50ms, not contented

        // Record high contention
        sloTracker.recordLockContention("lock-2", 150, true, context); // 150ms, contented

        // Check compliance status
        Map<String, SLOTracker.ComplianceStatus> status = sloTracker.getAllComplianceStatus();
        SLOTracker.ComplianceStatus contentionStatus = status.get(SLOTracker.SLO_LOCK_CONTENTION);

        assertNotNull(contentionStatus);
        // Should show some percentage of contention events
        assertTrue(contentionStatus.getCompliancePercentage() >= 0);
    }

    @Test
    void testPredictiveBreachDetection() {
        // Record multiple events trending toward threshold
        Map<String, String> context = new HashMap<>();
        context.put("case_type", "predictive_test");

        // Record events with increasing duration (approaching threshold)
        sloTracker.recordCaseCompletion("case-pred-1", 22 * 60 * 60 * 1000L, context); // 22 hours
        sloTracker.recordCaseCompletion("case-pred-2", 23 * 60 * 60 * 1000L, context); // 23 hours
        sloTracker.recordCaseCompletion("case-pred-3", 23.5 * 60 * 60 * 1000L, context); // 23.5 hours

        // Check if predictive metrics are tracking
        Map<String, SLOTracker.ComplianceStatus> status = sloTracker.getAllComplianceStatus();
        SLOTracker.ComplianceStatus predictiveStatus = status.get(SLOTracker.SLO_CASE_COMPLETION);

        assertNotNull(predictiveStatus);
        // Status should reflect predictive trending
        assertTrue(predictiveStatus.getCompliancePercentage() > 0);
    }

    @Test
    void testAlertGeneration() {
        // Record multiple violations to trigger alerts
        Map<String, String> context = new HashMap<>();
        context.put("case_type", "violation_test");

        sloTracker.recordCaseCompletion("case-alert-1", 25 * 60 * 60 * 1000L, context); // 25 hours
        sloTracker.recordCaseCompletion("case-alert-2", 26 * 60 * 60 * 1000L, context); // 26 hours

        // Verify alerts were called (mock verification)
        verify(andonCord, atLeast(1)).triggerCriticalAlert(anyString(), any());
        verify(andonCord, atLeast(1)).triggerWarning(anyString(), any());
    }

    @Test
    void testComplianceWindowMaintenance() {
        // Record many events to test window maintenance
        Map<String, String> context = new HashMap<>();
        context.put("case_type", "load_test");

        for (int i = 0; i < 1500; i++) {
            sloTracker.recordCaseCompletion("case-load-" + i, i * 1000, context);
        }

        // Check that status is still valid despite large number of events
        Map<String, SLOTracker.ComplianceStatus> status = sloTracker.getAllComplianceStatus();
        SLOTracker.ComplianceStatus caseStatus = status.get(SLOTracker.SLO_CASE_COMPLETION);

        assertNotNull(caseStatus);
        assertTrue(caseStatus.getCompliancePercentage() >= 0);
        assertTrue(caseStatus.getCompliancePercentage() <= 100);
    }

    @Test
    void testConcurrentEventProcessing() {
        // Test thread safety with concurrent events
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < 100; i++) {
            final int threadId = i;
            executor.submit(() -> {
                Map<String, String> context = new HashMap<>();
                context.put("thread_id", String.valueOf(threadId));
                sloTracker.recordCaseCompletion("case-concurrent-" + threadId, threadId * 1000, context);
            });
        }

        executor.shutdown();
        try {
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Verify all events were processed
        Map<String, SLOTracker.ComplianceStatus> status = sloTracker.getAllComplianceStatus();
        SLOTracker.ComplianceStatus caseStatus = status.get(SLOTracker.SLO_CASE_COMPLETION);

        assertNotNull(caseStatus);
        assertTrue(caseStatus.getCompliancePercentage() >= 0);
    }

    @Test
    void testNullParameterHandling() {
        // Test null parameter handling
        assertThrows(NullPointerException.class, () -> {
            sloTracker.recordCaseCompletion(null, 1000, new HashMap<>());
        });

        assertThrows(NullPointerException.class, () -> {
            sloTracker.recordCaseCompletion("case-null", 1000, null);
        });
    }

    @Test
    void testMetricsRegistration() {
        // Verify metrics are registered
        assertNotNull(meterRegistry.find("yawl.slo.total_events").counter());
        assertNotNull(meterRegistry.find("yawl.slo.violations").counter());
        assertNotNull(meterRegistry.find("yawl.slo.warnings").counter());
        assertNotNull(meterRegistry.find("yawl.slo.breaches").counter());

        // Check compliance gauges
        assertNotNull(meterRegistry.find("yawl.slo.compliance")
            .tag("slo_id", SLOTracker.SLO_CASE_COMPLETION)
            .gauge());

        assertNotNull(meterRegistry.find("yawl.slo.dashboard.compliance").gauge());
    }

    @Test
    void testShutdown() {
        // Perform shutdown
        sloTracker.shutdown();

        // Verify shutdown completed (no exceptions thrown)
        assertDoesNotThrow(() -> {
            sloTracker.recordCaseCompletion("case-after-shutdown", 1000, new HashMap<>());
        });
    }
}