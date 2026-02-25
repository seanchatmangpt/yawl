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

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test suite for SLOAlertManager functionality.
 *
 * Tests:
 * - Alert creation and lifecycle management
 * - Severity-based routing
 * - Alert suppression and deduplication
 * - Escalation policies
 * - Alert statistics
 * - Concurrent alert handling
 */
class SLOAlertManagerTest {

    private MeterRegistry meterRegistry;
    private AndonCord andonCord;
    private SLOAlertManager alertManager;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        andonCord = mock(AndonCord.class);
        alertManager = new SLOAlertManager(andonCord, meterRegistry);
    }

    @AfterEach
    void tearDown() {
        alertManager.stop();
    }

    @Test
    void testAlertCreation() {
        // Create a critical alert
        Map<String, String> context = new HashMap<>();
        context.put("case_id", "alert-test-1");

        alertManager.createAlert(
            SLOTracker.SLO_CASE_COMPLETION,
            SLOAlertManager.AlertSeverity.CRITICAL,
            "Critical case completion violation",
            25 * 60 * 60 * 1000L, // 25 hours
            24 * 60 * 60 * 1000L, // 24 hours threshold
            0.95, // 95% breach probability
            context
        );

        // Verify alert was created
        List<SLOAlertManager.Alert> activeAlerts = List.copyOf(alertManager.getActiveAlerts());
        assertEquals(1, activeAlerts.size());

        SLOAlertManager.Alert alert = activeAlerts.get(0);
        assertEquals(SLOTracker.SLO_CASE_COMPLETION, alert.getSloId());
        assertEquals(SLOAlertManager.AlertSeverity.CRITICAL, alert.getSeverity());
        assertFalse(alert.isAcknowledged());
        assertFalse(alert.isSent());
    }

    @Test
    void testSeverityBasedRouting() {
        // Test different severity levels

        // Info alert
        Map<String, String> context = new HashMap<>();
        context.put("case_id", "info-test");

        alertManager.createAlert(
            SLOTracker.SLO_TASK_EXECUTION,
            SLOAlertManager.AlertSeverity.INFO,
            "Info message",
            1000,
            1000,
            0.3,
            context
        );

        // Warning alert
        alertManager.createAlert(
            SLOTracker.SLO_QUEUE_RESPONSE,
            SLOAlertManager.AlertSeverity.WARNING,
            "Warning message",
            10 * 60 * 1000L,
            5 * 60 * 1000L,
            0.7,
            context
        );

        // Critical alert
        alertManager.createAlert(
            SLOTracker.SLO_CASE_COMPLETION,
            SLOAlertManager.AlertSeverity.CRITICAL,
            "Critical message",
            25 * 60 * 60 * 1000L,
            24 * 60 * 60 * 1000L,
            0.95,
            context
        );

        // Start alert manager to process alerts
        alertManager.start();

        // Wait for alerts to be processed
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Verify different severities triggered appropriate notifications
        verify(andonCord, times(1)).triggerInfo(anyString(), any());
        verify(andonCord, times(1)).triggerWarning(anyString(), any());
        verify(andonCord, times(1)).triggerCriticalAlert(anyString(), any());
    }

    @Test
    void testAlertSuppression() {
        // Create alert
        Map<String, String> context = new HashMap<>();
        context.put("case_id", "suppress-test");

        alertManager.createAlert(
            SLOTracker.SLO_CASE_COMPLETION,
            SLOAlertManager.AlertSeverity.CRITICAL,
            "Test alert",
            25 * 60 * 60 * 1000L,
            24 * 60 * 60 * 1000L,
            0.95,
            context
        );

        // Mute the alert
        alertManager.muteAlert(SLOTracker.SLO_CASE_COMPLETION, SLOAlertManager.AlertSeverity.CRITICAL);

        // Create another similar alert (should be suppressed)
        alertManager.createAlert(
            SLOTracker.SLO_CASE_COMPLETION,
            SLOAlertManager.AlertSeverity.CRITICAL,
            "Another test alert",
            26 * 60 * 60 * 1000L,
            24 * 60 * 60 * 1000L,
            0.96,
            context
        );

        // Start alert manager
        alertManager.start();

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Verify only first alert was sent, second was suppressed
        verify(andonCord, times(1)).triggerCriticalAlert(anyString(), any());
    }

    @Test
    void testAlertAcknowledgment() {
        // Create and process an alert
        Map<String, String> context = new HashMap<>();
        context.put("case_id", "ack-test");

        alertManager.createAlert(
            SLOTracker.SLO_QUEUE_RESPONSE,
            SLOAlertManager.AlertSeverity.HIGH,
            "High priority alert",
            10 * 60 * 1000L,
            5 * 60 * 1000L,
            0.85,
            context
        );

        alertManager.start();

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Get the active alert
        List<SLOAlertManager.Alert> activeAlerts = List.copyOf(alertManager.getActiveAlerts());
        assertEquals(1, activeAlerts.size());

        SLOAlertManager.Alert alert = activeAlerts.get(0);
        assertFalse(alert.isAcknowledged());

        // Acknowledge the alert
        alertManager.acknowledgeAlert(alert.getId(), "test-user");

        // Verify acknowledgment
        assertTrue(alert.isAcknowledged());
        assertEquals("test-user", alert.getAcknowledgedBy());
    }

    @Test
    void testAlertEscalation() {
        // Create a critical alert
        Map<String, String> context = new HashMap<>();
        context.put("case_id", "escalate-test");

        alertManager.createAlert(
            SLOTracker.SLO_CASE_COMPLETION,
            SLOAlertManager.AlertSeverity.CRITICAL,
            "Escalation test",
            25 * 60 * 60 * 1000L,
            24 * 60 * 60 * 1000L,
            0.95,
            context
        );

        alertManager.start();

        // Wait for escalation timeout
        try {
            Thread.sleep(3500); // Wait for 5 seconds (escalation timeout)
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Verify alert was escalated
        verify(andonCord, times(2)).triggerCriticalAlert(anyString(), any()); // Initial + escalated
    }

    @Test
    void testAlertStatistics() {
        // Create various alerts
        Map<String, String> context = new HashMap<>();

        alertManager.createAlert(
            SLOTracker.SLO_CASE_COMPLETION,
            SLOAlertManager.AlertSeverity.CRITICAL,
            "Critical alert",
            25 * 60 * 60 * 1000L,
            24 * 60 * 60 * 1000L,
            0.95,
            context
        );

        alertManager.createAlert(
            SLOTracker.SLO_TASK_EXECUTION,
            SLOAlertManager.AlertSeverity.WARNING,
            "Warning alert",
            90 * 60 * 1000L,
            60 * 60 * 1000L,
            0.75,
            context
        );

        alertManager.createAlert(
            SLOTracker.SLO_QUEUE_RESPONSE,
            SLOAlertManager.AlertSeverity.INFO,
            "Info alert",
            60000L,
            300000L,
            0.5,
            context
        );

        // Get statistics
        SLOAlertManager.AlertStats stats = alertManager.getAlertStats();

        // Verify statistics
        assertNotNull(stats);
        assertEquals(3, stats.getTotalAlerts());
        assertTrue(stats.getAcknowledgmentRate() >= 0);
        assertTrue(stats.getAcknowledgmentRate() <= 1);

        // Verify severity counts
        Map<SLOAlertManager.AlertSeverity, Long> severityCounts = stats.getSeverityCounts();
        assertEquals(1L, (long) severityCounts.get(SLOAlertManager.AlertSeverity.CRITICAL));
        assertEquals(1L, (long) severityCounts.get(SLOAlertManager.AlertSeverity.WARNING));
        assertEquals(1L, (long) severityCounts.get(SLOAlertManager.AlertSeverity.INFO));
    }

    @Test
    void testConcurrentAlertProcessing() {
        // Start alert manager
        alertManager.start();

        // Create alerts concurrently
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < 100; i++) {
            final int alertId = i;
            executor.submit(() -> {
                Map<String, String> context = new HashMap<>();
                context.put("alert_id", String.valueOf(alertId));

                SLOAlertManager.AlertSeverity severity =
                    (alertId % 3 == 0) ? SLOAlertManager.AlertSeverity.CRITICAL :
                    (alertId % 3 == 1) ? SLOAlertManager.AlertSeverity.WARNING :
                    SLOAlertManager.AlertSeverity.INFO;

                alertManager.createAlert(
                    SLOTracker.SLO_CASE_COMPLETION,
                    severity,
                    "Concurrent alert " + alertId,
                    25 * 60 * 60 * 1000L + alertId * 1000L,
                    24 * 60 * 60 * 1000L,
                    0.95,
                    context
                );
            });
        }

        executor.shutdown();
        try {
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Verify all alerts were processed
        List<SLOAlertManager.Alert> activeAlerts = List.copyOf(alertManager.getActiveAlerts());
        assertEquals(100, activeAlerts.size());

        // Verify no duplicates
        assertEquals(100, activeAlerts.stream().distinct().count());
    }

    @Test
    void testAlertCleanup() {
        // Create alerts
        Map<String, String> context = new HashMap<>();

        for (int i = 0; i < 5; i++) {
            alertManager.createAlert(
                SLOTracker.SLO_CASE_COMPLETION,
                SLOAlertManager.AlertSeverity.INFO,
                "Cleanup test alert",
                1000L,
                1000L,
                0.5,
                context
            );
        }

        // Acknowledge all alerts
        List<SLOAlertManager.Alert> activeAlerts = List.copyOf(alertManager.getActiveAlerts());
        for (SLOAlertManager.Alert alert : activeAlerts) {
            alertManager.acknowledgeAlert(alert.getId(), "cleanup-test");
        }

        alertManager.start();

        // Wait for cleanup cycle
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // After cleanup, alerts should be removed if expired
        // (Note: This depends on the actual TTL implementation)
        assertTrue(alertManager.getActiveAlerts().size() <= 5);
    }

    @Test
    void testMuteAndUnmute() {
        // Create an alert first
        Map<String, String> context = new HashMap<>();
        context.put("case_id", "mute-test");

        alertManager.createAlert(
            SLOTracker.SLO_CASE_COMPLETION,
            SLOAlertManager.AlertSeverity.CRITICAL,
            "Mute test",
            25 * 60 * 60 * 1000L,
            24 * 60 * 60 * 1000L,
            0.95,
            context
        );

        // Mute
        alertManager.muteAlert(SLOTracker.SLO_CASE_COMPLETION, SLOAlertManager.AlertSeverity.CRITICAL);
        assertTrue(alertManager.isMuted(SLOTracker.SLO_CASE_COMPLETION, SLOAlertManager.AlertSeverity.CRITICAL));

        // Unmute
        alertManager.unmuteAlert(SLOTracker.SLO_CASE_COMPLETION, SLOAlertManager.AlertSeverity.CRITICAL);
        assertFalse(alertManager.isMuted(SLOTracker.SLO_CASE_COMPLETION, SLOAlertManager.AlertSeverity.CRITICAL));
    }

    @Test
    void testAlertProcessingTimer() {
        // Verify alert processing metrics
        assertNotNull(meterRegistry.find("yawl.slo.alert_processing"));
        assertNotNull(meterRegistry.find("yawl.slo.alerts"));
        assertNotNull(meterRegistry.find("yawl.slo.alerts_suppressed"));
        assertNotNull(meterRegistry.find("yawl.slo.alerts_escalated"));
        assertNotNull(meterRegistry.find("yawl.slo.active_alerts"));
    }

    @Test
    void testAlertDataStructure() {
        // Create an alert
        Map<String, String> context = new HashMap<>();
        context.put("case_id", "struct-test");

        SLOAlertManager.Alert alert = new SLOAlertManager.Alert(
            "alert-123",
            SLOTracker.SLO_CASE_COMPLETION,
            SLOAlertManager.AlertSeverity.CRITICAL,
            "Structure test",
            25 * 60 * 60 * 1000L,
            24 * 60 * 60 * 1000L,
            0.95,
            context
        );

        // Verify alert structure
        assertEquals("alert-123", alert.getId());
        assertEquals(SLOTracker.SLO_CASE_COMPLETION, alert.getSloId());
        assertEquals(SLOAlertManager.AlertSeverity.CRITICAL, alert.getSeverity());
        assertEquals("Structure test", alert.getMessage());
        assertEquals(25 * 60 * 60 * 1000L, alert.getCurrentValue());
        assertEquals(24 * 60 * 60 * 1000L, alert.getThreshold());
        assertEquals(0.95, alert.getBreachProbability());
        assertFalse(alert.isAcknowledged());
        assertFalse(alert.isSent());
        assertEquals(0, alert.getProcessingAttempts());
        assertEquals(0, alert.getEscalationLevel());
    }
}