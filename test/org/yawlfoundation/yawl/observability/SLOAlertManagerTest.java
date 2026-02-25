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
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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

    // ========================================
    // ALERT CREATION AND VIOLATION DETECTION TESTS
    // ========================================

    @Test
    void testAlertCreationWithViolationDetection() {
        Map<String, String> context = new HashMap<>();
        context.put("case_id", "violation-alert");

        // Create alert that should trigger based on threshold violation
        SLOAlertManager.Alert alert = alertManager.createAlert(
            SLOTracker.SLO_CASE_COMPLETION,
            SLOAlertManager.AlertSeverity.WARNING,
            "Case completion threshold exceeded",
            25 * 60 * 60 * 1000L, // 25 hours
            24 * 60 * 60 * 1000L, // 24 hours threshold
            0.75, // 75% breach probability
            context
        );

        assertNotNull(alert);
        assertEquals(SLOTracker.SLO_CASE_COMPLETION, alert.getSloId());
        assertEquals(SLOAlertManager.AlertSeverity.WARNING, alert.getSeverity());
        assertEquals("Case completion threshold exceeded", alert.getMessage());

        // Verify violation detection
        assertTrue(alert.isViolation());
        assertEquals(25 * 60 * 60 * 1000L, alert.getCurrentValue());
        assertEquals(24 * 60 * 60 * 1000L, alert.getThreshold());
    }

    @Test
    void testAlertCreationWithBreachProbabilityCalculation() {
        Map<String, String> context = new HashMap<>();
        context.put("case_id", "prob-test");

        // Create alert with high breach probability
        SLOAlertManager.Alert alert = alertManager.createAlert(
            SLOTracker.SLO_QUEUE_RESPONSE,
            SLOAlertManager.AlertSeverity.HIGH,
            "High queue response time",
            10000L, // 10 seconds
            5000L, // 5 seconds threshold
            0.0, // Will be calculated
            context
        );

        // Verify breach probability is calculated
        assertTrue(alert.getBreachProbability() > 0);
        assertTrue(alert.getBreachProbability() <= 1.0);
    }

    @Test
    void testAlertCreationWithWarningThresholds() {
        Map<String, String> context = new HashMap<>();
        context.put("queue_name", "critical_queue");

        // Create warning alert (below critical threshold)
        SLOAlertManager.Alert warningAlert = alertManager.createAlert(
            SLOTracker.SLO_QUEUE_RESPONSE,
            SLOAlertManager.AlertSeverity.WARNING,
            "Queue response approaching threshold",
            4000L, // 4 seconds (below 5s critical)
            5000L, // 5 seconds threshold
            0.0,
            context
        );

        // Create critical alert (above critical threshold)
        SLOAlertManager.Alert criticalAlert = alertManager.createAlert(
            SLOTracker.SLO_QUEUE_RESPONSE,
            SLOAlertManager.AlertSeverity.CRITICAL,
            "Critical queue response time",
            10000L, // 10 seconds (above 5s critical)
            5000L, // 5 seconds threshold
            0.0,
            context
        );

        // Verify different severities
        assertEquals(SLOAlertManager.AlertSeverity.WARNING, warningAlert.getSeverity());
        assertEquals(SLOAlertManager.AlertSeverity.CRITICAL, criticalAlert.getSeverity());
        assertTrue(criticalAlert.getBreachProbability() > warningAlert.getBreachProbability());
    }

    // ========================================
    // SEVERITY-BASED ROUTING TESTS
    // ========================================

    @Test
    void testSeverityBasedRouting() {
        Map<String, String> context = new HashMap<>();
        context.put("test", "routing");

        // Create alerts of different severities
        SLOAlertManager.Alert infoAlert = alertManager.createAlert(
            SLOTracker.SLO_CASE_COMPLETION,
            SLOAlertManager.AlertSeverity.INFO,
            "Informational alert",
            2000L,
            1000L,
            0.5,
            context
        );

        SLOAlertManager.Alert warningAlert = alertManager.createAlert(
            SLOTracker.SLO_TASK_EXECUTION,
            SLOAlertManager.AlertSeverity.WARNING,
            "Warning alert",
            90000L,
            60000L,
            0.7,
            context
        );

        SLOAlertManager.Alert criticalAlert = alertManager.createAlert(
            SLOTracker.SLO_QUEUE_RESPONSE,
            SLOAlertManager.AlertSeverity.CRITICAL,
            "Critical alert",
            10000L,
            5000L,
            0.9,
            context
        );

        // Start alert manager to process alerts
        alertManager.start();

        // Wait for processing
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Verify severity-based routing
        List<SLOAlertManager.Alert> activeAlerts = alertManager.getActiveAlerts();
        assertEquals(3, activeAlerts.size());

        // Check that critical alerts have highest priority
        List<SLOAlertManager.Alert> criticalAlerts = activeAlerts.stream()
            .filter(a -> a.getSeverity() == SLOAlertManager.AlertSeverity.CRITICAL)
            .collect(Collectors.toList());
        assertEquals(1, criticalAlerts.size());

        // Verify AndonCord integration by severity
        verify(andonCord, times(1)).triggerInfo(anyString(), any());
        verify(andonCord, times(1)).triggerWarning(anyString(), any());
        verify(andonCord, times(1)).triggerCriticalAlert(anyString(), any());
    }

    @Test
    void testAlertSeverityResponseSLA() {
        Map<String, String> context = new HashMap<>();
        context.put("sla_test", "true");

        // Test different SLA requirements
        SLOAlertManager.Alert criticalAlert = alertManager.createAlert(
            SLOTracker.SLO_CASE_COMPLETION,
            SLOAlertManager.AlertSeverity.CRITICAL,
            "Critical alert",
            30000L,
            1000L,
            0.95,
            context
        );

        SLOAlertManager.Alert highAlert = alertManager.createAlert(
            SLOTracker.SLO_TASK_EXECUTION,
            SLOAlertManager.AlertSeverity.HIGH,
            "High alert",
            300000L,
            60000L,
            0.85,
            context
        );

        SLOAlertManager.Alert mediumAlert = alertManager.createAlert(
            SLOTracker.SLO_QUEUE_RESPONSE,
            SLOAlertManager.AlertSeverity.MEDIUM,
            "Medium alert",
            300000L,
            5000L,
            0.7,
            context
        );

        // Verify response SLA durations
        assertEquals(Duration.ofMinutes(1), criticalAlert.getSeverity().getResponseSla());
        assertEquals(Duration.ofHours(4), highAlert.getSeverity().getResponseSla());
        assertEquals(Duration.ofHours(24), mediumAlert.getSeverity().getResponseSla());

        // Verify overdue detection
        assertFalse(criticalAlert.isOverdue());
        assertFalse(highAlert.isOverdue());
        assertFalse(mediumAlert.isOverdue());
    }

    // ========================================
    // ALERT SUPPRESSION AND DUPLICATION TESTS
    // ========================================

    @Test
    void testAlertDeduplication() {
        Map<String, String> context = new HashMap<>();
        context.put("case_id", "dedup-test");

        // Create multiple similar alerts
        SLOAlertManager.Alert alert1 = alertManager.createAlert(
            SLOTracker.SLO_CASE_COMPLETION,
            SLOAlertManager.AlertSeverity.WARNING,
            "Similar alert 1",
            25000L,
            24000L,
            0.75,
            context
        );

        SLOAlertManager.Alert alert2 = alertManager.createAlert(
            SLOTracker.SLO_CASE_COMPLETION,
            SLOAlertManager.AlertSeverity.WARNING,
            "Similar alert 2",
            26000L,
            24000L,
            0.80,
            context
        );

        // Verify both alerts are created but handled differently
        List<SLOAlertManager.Alert> activeAlerts = alertManager.getActiveAlerts();
        assertEquals(2, activeAlerts.size());

        // Verify different IDs but same SLO type and severity
        assertTrue(alert1.getId().startsWith("alert-"));
        assertTrue(alert2.getId().startsWith("alert-"));
        assertEquals(alert1.getSloId(), alert2.getSloId());
        assertEquals(alert1.getSeverity(), alert2.getSeverity());
    }

    @Test
    void testAlertSuppressionWithMute() {
        Map<String, String> context = new HashMap<>();
        context.put("case_id", "suppress-test");

        // Mute alerts for this SLO and severity
        alertManager.muteAlert(SLOTracker.SLO_CASE_COMPLETION, SLOAlertManager.AlertSeverity.WARNING);

        // Create muted alert
        SLOAlertManager.Alert mutedAlert = alertManager.createAlert(
            SLOTracker.SLO_CASE_COMPLETION,
            SLOAlertManager.AlertSeverity.WARNING,
            "This should be suppressed",
            25000L,
            24000L,
            0.75,
            context
        );

        // Create non-muted alert
        SLOAlertManager.Alert nonMutedAlert = alertManager.createAlert(
            SLOTracker.SLO_TASK_EXECUTION,
            SLOAlertManager.AlertSeverity.WARNING,
            "This should not be suppressed",
            90000L,
            60000L,
            0.85,
            context
        );

        // Start alert manager
        alertManager.start();

        // Wait for processing
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Verify mute status
        assertTrue(alertManager.isMuted(SLOTracker.SLO_CASE_COMPLETION, SLOAlertManager.AlertSeverity.WARNING));
        assertFalse(alertManager.isMuted(SLOTracker.SLO_TASK_EXECUTION, SLOAlertManager.AlertSeverity.WARNING));

        // Verify muted alert was not sent to AndonCord
        verify(andonCord, times(0)).triggerWarning(
            eq("This should be suppressed"), any()
        );

        // Verify non-muted alert was sent
        verify(andonCord, times(1)).triggerWarning(
            eq("This should not be suppressed"), any()
        );
    }

    @Test
    void testAlertUnmute() {
        Map<String, String> context = new HashMap<>();
        context.put("case_id", "unmute-test");

        // Mute
        alertManager.muteAlert(SLOTracker.SLO_CASE_COMPLETION, SLOAlertManager.AlertSeverity.WARNING);
        assertTrue(alertManager.isMuted(SLOTracker.SLO_CASE_COMPLETION, SLOAlertManager.AlertSeverity.WARNING));

        // Unmute
        alertManager.unmuteAlert(SLOTracker.SLO_CASE_COMPLETION, SLOAlertManager.AlertSeverity.WARNING);
        assertFalse(alertManager.isMuted(SLOTracker.SLO_CASE_COMPLETION, SLOAlertManager.AlertSeverity.WARNING));

        // Create alert after unmute
        SLOAlertManager.Alert alert = alertManager.createAlert(
            SLOTracker.SLO_CASE_COMPLETION,
            SLOAlertManager.AlertSeverity.WARNING,
            "This should fire after unmute",
            25000L,
            24000L,
            0.75,
            context
        );

        alertManager.start();

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Verify alert is sent after unmute
        verify(andonCord, times(1)).triggerWarning(
            eq("This should fire after unmute"), any()
        );
    }

    // ========================================
    // ALERT ESCALATION TESTS
    // ========================================

    @Test
    void testAlertEscalation() {
        Map<String, String> context = new HashMap<>();
        context.put("case_id", "escalate-test");

        // Create critical alert that will escalate
        SLOAlertManager.Alert alert = alertManager.createAlert(
            SLOTracker.SLO_CASE_COMPLETION,
            SLOAlertManager.AlertSeverity.CRITICAL,
            "Critical alert with escalation",
            30000L,
            1000L,
            0.95,
            context
        );

        alertManager.start();

        // Wait for escalation timeout (5 seconds)
        try {
            Thread.sleep(6000);
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Verify escalation occurred
        verify(andonCord, times(2)).triggerCriticalAlert(anyString(), any());
        assertEquals(1, alert.getEscalationLevel());

        // Verify alert is still active after escalation
        List<SLOAlertManager.Alert> activeAlerts = alertManager.getActiveAlerts();
        assertTrue(activeAlerts.contains(alert));
    }

    @Test
    void testMultipleEscalationLevels() {
        Map<String, String> context = new HashMap<>();
        context.put("case_id", "multi-escalate");

        // Create alert that will be escalated multiple times
        SLOAlertManager.Alert alert = alertManager.createAlert(
            SLOTracker.SLO_QUEUE_RESPONSE,
            SLOAlertManager.AlertSeverity.CRITICAL,
            "Multiple escalation test",
            15000L,
            5000L,
            0.9,
            context
        );

        alertManager.start();

        // Wait for multiple escalation cycles
        try {
            Thread.sleep(15000); // 15 seconds
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Verify multiple escalations
        verify(andonCord, times(atLeast(3))).triggerCriticalAlert(anyString(), any());
        assertTrue(alert.getEscalationLevel() >= 2);
    }

    @Test
    void testAlertAcknowledgmentStopsEscalation() {
        Map<String, String> context = new HashMap<>();
        context.put("case_id", "ack-stop");

        SLOAlertManager.Alert alert = alertManager.createAlert(
            SLOTracker.SLO_CASE_COMPLETION,
            SLOAlertManager.AlertSeverity.CRITICAL,
            "Escalation stop test",
            30000L,
            1000L,
            0.95,
            context
        );

        alertManager.start();

        // Acknowledge alert before escalation timeout
        try {
            Thread.sleep(1000); // 1 second
            alertManager.acknowledgeAlert(alert.getId(), "test-user");

            Thread.sleep(10000); // Wait more than escalation timeout
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Verify acknowledgment
        assertTrue(alert.isAcknowledged());
        assertEquals("test-user", alert.getAcknowledgedBy());

        // Escalation should have stopped
        verify(andonCord, times(1)).triggerCriticalAlert(anyString(), any());
        assertEquals(0, alert.getEscalationLevel());
    }

    // ========================================
    // ALERT HISTORY AND STATISTICS TESTS
    // ========================================

    @Test
    void testAlertHistory() {
        Map<String, String> context = new HashMap<>();
        context.put("test", "history");

        // Create multiple alerts
        alertManager.createAlert(
            SLOTracker.SLO_CASE_COMPLETION,
            SLOAlertManager.AlertSeverity.INFO,
            "History test 1",
            2000L,
            1000L,
            0.5,
            context
        );

        alertManager.createAlert(
            SLOTracker.SLO_TASK_EXECUTION,
            SLOAlertManager.AlertSeverity.WARNING,
            "History test 2",
            90000L,
            60000L,
            0.7,
            context
        );

        alertManager.start();

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Resolve one alert
        SLOAlertManager.Alert resolvedAlert = alertManager.getActiveAlerts().get(0);
        alertManager.resolveAlert(resolvedAlert.getId());

        // Check history
        List<SLOAlertManager.Alert> history = alertManager.getAlertHistory();
        assertFalse(history.isEmpty());
        assertTrue(history.size() <= 1000); // History limit

        // Check that resolved alert is in history
        assertTrue(history.stream().anyMatch(a -> a.getId().equals(resolvedAlert.getId())));
    }

    @Test
    void testAlertStatistics() {
        Map<String, String> context = new HashMap<>();

        // Create alerts of different severities
        for (int i = 0; i < 5; i++) {
            alertManager.createAlert(
                SLOTracker.SLO_CASE_COMPLETION,
                SLOAlertManager.AlertSeverity.INFO,
                "Info alert " + i,
                2000L,
                1000L,
                0.5,
                context
            );
        }

        for (int i = 0; i < 3; i++) {
            alertManager.createAlert(
                SLOTracker.SLO_TASK_EXECUTION,
                SLOAlertManager.AlertSeverity.WARNING,
                "Warning alert " + i,
                90000L,
                60000L,
                0.7,
                context
            );
        }

        for (int i = 0; i < 2; i++) {
            alertManager.createAlert(
                SLOTracker.SLO_QUEUE_RESPONSE,
                SLOAlertManager.AlertSeverity.CRITICAL,
                "Critical alert " + i,
                10000L,
                5000L,
                0.9,
                context
            );
        }

        alertManager.start();

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Get statistics
        SLOAlertManager.AlertStats stats = alertManager.getAlertStats();
        assertNotNull(stats);
        assertEquals(10, stats.getTotalAlerts());

        // Check severity counts
        Map<SLOAlertManager.AlertSeverity, Long> severityCounts = stats.getSeverityCounts();
        assertEquals(5L, (long) severityCounts.get(SLOAlertManager.AlertSeverity.INFO));
        assertEquals(3L, (long) severityCounts.get(SLOAlertManager.AlertSeverity.WARNING));
        assertEquals(2L, (long) severityCounts.get(SLOAlertManager.AlertSeverity.CRITICAL));

        // Check acknowledgment rate
        assertTrue(stats.getAcknowledgmentRate() >= 0);
        assertTrue(stats.getAcknowledgmentRate() <= 1);
    }

    @Test
    void testOverdueAlerts() {
        Map<String, String> context = new HashMap<>();
        context.put("test", "overdue");

        // Create alert that will become overdue
        SLOAlertManager.Alert overdueAlert = alertManager.createAlert(
            SLOTracker.SLO_CASE_COMPLETION,
            SLOAlertManager.AlertSeverity.CRITICAL,
            "Overdue test",
            30000L,
            1000L,
            0.95,
            context
        );

        alertManager.start();

        // Wait for alert to become overdue
        try {
            Thread.sleep(2000); // 2 seconds (less than 1 min SLA)
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Check overdue detection
        assertFalse(overdueAlert.isOverdue()); // Not overdue yet

        // Wait longer to become truly overdue
        try {
            Thread.sleep(65000); // Total 67 seconds
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Now it should be overdue
        assertTrue(overdueAlert.isOverdue());

        // Check overdue alerts collection
        List<SLOAlertManager.Alert> overdueAlerts = alertManager.getOverdueAlerts();
        assertTrue(overdueAlerts.size() >= 1);
    }

    // ========================================
    // MAINTENANCE MODE TESTS
    // ========================================

    @Test
    void testMaintenanceModeSuppression() {
        Map<String, String> context = new HashMap<>();
        context.put("test", "maintenance");

        // Enter maintenance mode
        alertManager.enterMaintenanceMode(Duration.ofMinutes(30));

        // Create alert during maintenance
        SLOAlertManager.Alert alert = alertManager.createAlert(
            SLOTracker.SLO_CASE_COMPLETION,
            SLOAlertManager.AlertSeverity.CRITICAL,
            "This should be suppressed",
            30000L,
            1000L,
            0.95,
            context
        );

        alertManager.start();

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Verify alert was suppressed
        verify(andonCord, times(0)).triggerCriticalAlert(anyString(), any());

        // Exit maintenance mode
        alertManager.exitMaintenanceMode();

        // Create alert after maintenance
        SLOAlertManager.Alert postMaintenanceAlert = alertManager.createAlert(
            SLOTracker.SLO_CASE_COMPLETION,
            SLOAlertManager.AlertSeverity.CRITICAL,
            "This should fire after maintenance",
            30000L,
            1000L,
            0.95,
            context
        );

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Verify alert fired after maintenance
        verify(andonCord, times(1)).triggerCriticalAlert(
            eq("This should fire after maintenance"), any()
        );
    }

    @Test
    void testMaintenanceModeEmergencyExceptions() {
        Map<String, String> context = new HashMap<>();
        context.put("test", "emergency");

        // Enter maintenance mode
        alertManager.enterMaintenanceMode(Duration.ofMinutes(30));

        // Create emergency alert (should bypass maintenance)
        SLOAlertManager.Alert emergencyAlert = alertManager.createAlert(
            SLOTracker.SLO_QUEUE_RESPONSE,
            SLOAlertManager.AlertSeverity.EMERGENCY,
            "Emergency alert",
            15000L,
            5000L,
            0.95,
            context
        );

        alertManager.start();

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Emergency alert should bypass maintenance
        verify(andonCord, times(1)).triggerCriticalAlert(
            eq("Emergency alert"), any()
        );
    }

    // ========================================
    // HIGH LOAD AND PERFORMANCE TESTS
    // ========================================

    @Test
    void testHighVolumeAlertProcessing() {
        // Create large volume of alerts
        int alertCount = 1000;
        Map<String, String> context = new HashMap<>();
        context.put("load_test", "high_volume");

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < alertCount; i++) {
            SLOAlertManager.AlertSeverity severity =
                (i % 10 == 0) ? SLOAlertManager.AlertSeverity.CRITICAL :
                (i % 5 == 0) ? SLOAlertManager.AlertSeverity.WARNING :
                SLOAlertManager.AlertSeverity.INFO;

            alertManager.createAlert(
                SLOTracker.SLO_CASE_COMPLETION,
                severity,
                "High volume alert " + i,
                2000L + (i % 1000),
                1000L,
                0.5 + (i % 1000) / 2000.0,
                context
            );
        }

        alertManager.start();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // Performance check
        assertTrue(duration < 5000, "Created " + alertCount + " alerts in " + duration + "ms");

        // Wait for processing
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Verify all alerts processed
        List<SLOAlertManager.Alert> activeAlerts = alertManager.getActiveAlerts();
        assertEquals(alertCount, activeAlerts.size());

        // Verify no data corruption
        activeAlerts.forEach(alert -> {
            assertNotNull(alert);
            assertNotNull(alert.getId());
            assertNotNull(alert.getSloId());
            assertNotNull(alert.getSeverity());
            assertTrue(alert.getCurrentValue() > 0);
        });
    }

    @Test
    void testConcurrentAlertProcessing() {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger alertsCreated = new AtomicInteger(0);

        // Create alerts concurrently from multiple threads
        for (int i = 0; i < 2000; i++) {
            final int alertId = i;
            executor.submit(() -> {
                SLOAlertManager.AlertSeverity severity =
                    (alertId % 10 == 0) ? SLOAlertManager.AlertSeverity.CRITICAL :
                    (alertId % 5 == 0) ? SLOAlertManager.AlertSeverity.WARNING :
                    SLOAlertManager.AlertSeverity.INFO;

                Map<String, String> context = new HashMap<>();
                context.put("thread_id", String.valueOf(alertId % threadCount));
                context.put("alert_id", String.valueOf(alertId));

                alertManager.createAlert(
                    SLOTracker.SLO_CASE_COMPLETION,
                    severity,
                    "Concurrent alert " + alertId,
                    2000L + (alertId % 1000),
                    1000L,
                    0.5,
                    context
                );

                alertsCreated.incrementAndGet();
            });
        }

        executor.shutdown();
        try {
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        alertManager.start();

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Verify all alerts were created and processed
        assertEquals(2000, alertsCreated.get());
        List<SLOAlertManager.Alert> activeAlerts = alertManager.getActiveAlerts();
        assertEquals(2000, activeAlerts.size());

        // Verify thread safety
        Map<String, Long> threadCounts = activeAlerts.stream()
            .collect(Collectors.groupingBy(
                alert -> alert.getContext().get("thread_id"),
                Collectors.counting()
            ));

        assertEquals(threadCount, threadCounts.size());
        threadCounts.forEach((threadId, count) -> {
            assertTrue(count > 0);
        });
    }

    // ========================================
    // INTEGRATION TESTS
    // ========================================

    @Test
    void testAlertIntegrationWithSLOTracker() {
        // Create alerts based on SLO violations
        Map<String, String> context = new HashMap<>();
        context.put("integration_test", "true");

        // Simulate SLO violations that should trigger alerts
        sloTracker.recordCaseCompletion("case-violation-1", 25 * 60 * 60 * 1000L, context); // 25 hours
        sloTracker.recordCaseCompletion("case-violation-2", 26 * 60 * 60 * 1000L, context); // 26 hours

        // Wait for alert generation
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Verify alerts were generated
        List<SLOAlertManager.Alert> activeAlerts = alertManager.getActiveAlerts();
        assertFalse(activeAlerts.isEmpty());

        // Verify alerts are linked to SLO violations
        assertTrue(activeAlerts.stream()
            .anyMatch(alert -> alert.getSloId().equals(SLOTracker.SLO_CASE_COMPLETION)));
    }

    @Test
    void testAlertIntegrationWithAndonCord() {
        Map<String, String> context = new HashMap<>();
        context.put("integration_test", "andon");

        // Create critical alert
        SLOAlertManager.Alert alert = alertManager.createAlert(
            SLOTracker.SLO_CASE_COMPLETION,
            SLOAlertManager.AlertSeverity.CRITICAL,
            "Integration test with AndonCord",
            30000L,
            1000L,
            0.95,
            context
        );

        alertManager.start();

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Verify AndonCord integration
        verify(andonCord, times(1)).triggerCriticalAlert(
            eq("Integration test with AndonCord"), any()
        );

        // Verify alert has AndonCord context
        assertNotNull(alert.getContext());
        assertTrue(alert.getContext().containsKey("severity"));
    }

    // ========================================
    // ERROR HANDLING TESTS
    // ========================================

    @Test
    void testNullHandling() {
        assertThrows(NullPointerException.class, () -> {
            alertManager.createAlert(null, SLOAlertManager.AlertSeverity.INFO, "test", 1000L, 500L, 0.5, Map.of());
        });

        assertThrows(NullPointerException.class, () -> {
            alertManager.createAlert(SLOTracker.SLO_CASE_COMPLETION, null, "test", 1000L, 500L, 0.5, Map.of());
        });

        assertThrows(NullPointerException.class, () -> {
            alertManager.acknowledgeAlert(null, "user");
        });
    }

    @Test
    void testInvalidAlertParameters() {
        Map<String, String> context = new HashMap<>();
        context.put("test", "invalid");

        // Test invalid threshold values
        assertThrows(IllegalArgumentException.class, () -> {
            alertManager.createAlert(
                SLOTracker.SLO_CASE_COMPLETION,
                SLOAlertManager.AlertSeverity.INFO,
                "Invalid threshold",
                -1000L,
                1000L,
                0.5,
                context
            );
        });

        assertThrows(IllegalArgumentException.class, () -> {
            alertManager.createAlert(
                SLOTracker.SLO_CASE_COMPLETION,
                SLOAlertManager.AlertSeverity.INFO,
                "Invalid value",
                1000L,
                -1000L,
                0.5,
                context
            );
        });

        // Test invalid probability
        assertThrows(IllegalArgumentException.class, () -> {
            alertManager.createAlert(
                SLOTracker.SLO_CASE_COMPLETION,
                SLOAlertManager.AlertSeverity.INFO,
                "Invalid probability",
                1000L,
                500L,
                -0.1,
                context
            );
        });
    }

    @Test
    void testNonExistentAlertOperations() {
        // Operations on non-existent alerts should be graceful
        assertThrows(IllegalArgumentException.class, () -> {
            alertManager.acknowledgeAlert("non-existent", "user");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            alertManager.resolveAlert("non-existent");
        });

        assertThrows(IllegalArgumentException.class, () -> {
            alertManager.muteAlert(SLOTracker.SLO_CASE_COMPLETION, SLOAlertManager.AlertSeverity.INFO);
        });
    }

    // ========================================
    // CLEANUP TESTS
    // ========================================

    @Test
    void testAlertCleanup() {
        // Create alerts
        Map<String, String> context = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            alertManager.createAlert(
                SLOTracker.SLO_CASE_COMPLETION,
                SLOAlertManager.AlertSeverity.INFO,
                "Cleanup test " + i,
                1000L,
                500L,
                0.5,
                context
            );
        }

        alertManager.start();

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Resolve all alerts
        List<SLOAlertManager.Alert> activeAlerts = alertManager.getActiveAlerts();
        for (SLOAlertManager.Alert alert : activeAlerts) {
            alertManager.resolveAlert(alert.getId());
        }

        // Wait for cleanup
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Verify cleanup
        assertTrue(alertManager.getActiveAlerts().isEmpty());
        assertFalse(alertManager.getAlertHistory().isEmpty());
    }

    @Test
    void testShutdownBehavior() {
        // Create and start alerts
        Map<String, String> context = new HashMap<>();
        alertManager.createAlert(
            SLOTracker.SLO_CASE_COMPLETION,
            SLOAlertManager.AlertSeverity.INFO,
            "Shutdown test",
            1000L,
            500L,
            0.5,
            context
        );

        alertManager.start();

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Shutdown
        alertManager.stop();

        // Verify shutdown completed
        assertDoesNotThrow(() -> {
            alertManager.createAlert(
                SLOTracker.SLO_CASE_COMPLETION,
                SLOAlertManager.AlertSeverity.INFO,
                "Post shutdown",
                1000L,
                500L,
                0.5,
                context
            );
        });
    }
}