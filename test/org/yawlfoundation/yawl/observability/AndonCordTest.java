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
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AndonCord alert system.
 *
 * @author YAWL Foundation
 * @since 6.0.0
 */
@DisplayName("AndonCord Alert System Tests")
class AndonCordTest {

    private AndonCord andonCord;
    private List<AndonCord.Alert> capturedAlerts;
    private Consumer<AndonCord.Alert> alertListener;

    @BeforeEach
    void setUp() {
        capturedAlerts = new ArrayList<>();
        alertListener = capturedAlerts::add;

        AndonCord.Configuration config = AndonCord.Configuration.builder()
            .enableMetrics(true)
            .enableTracing(true)
            .escalationInterval(Duration.ofSeconds(1))
            .build();

        AndonCord.initialize(config);
        andonCord = AndonCord.getInstance();
        andonCord.addListener(alertListener);
    }

    @AfterEach
    void tearDown() {
        andonCord.removeListener(alertListener);
        andonCord.shutdown();
    }

    @Nested
    @DisplayName("Alert Severity Tests")
    class SeverityTests {

        @Test
        @DisplayName("P0 Critical has correct SLA of 1 minute")
        void p0CriticalHasCorrectSla() {
            assertEquals(Duration.ofMinutes(1), AndonCord.Severity.P0_CRITICAL.getResponseSla());
            assertEquals(0, AndonCord.Severity.P0_CRITICAL.getLevel());
            assertEquals("P0", AndonCord.Severity.P0_CRITICAL.getCode());
            assertEquals("CRITICAL", AndonCord.Severity.P0_CRITICAL.getLabel());
        }

        @Test
        @DisplayName("P1 High has correct SLA of 4 hours")
        void p1HighHasCorrectSla() {
            assertEquals(Duration.ofHours(4), AndonCord.Severity.P1_HIGH.getResponseSla());
            assertEquals(1, AndonCord.Severity.P1_HIGH.getLevel());
        }

        @Test
        @DisplayName("P2 Medium has correct SLA of 24 hours")
        void p2MediumHasCorrectSla() {
            assertEquals(Duration.ofHours(24), AndonCord.Severity.P2_MEDIUM.getResponseSla());
            assertEquals(2, AndonCord.Severity.P2_MEDIUM.getLevel());
        }

        @Test
        @DisplayName("P3 Low has correct SLA of 7 days")
        void p3LowHasCorrectSla() {
            assertEquals(Duration.ofDays(7), AndonCord.Severity.P3_LOW.getResponseSla());
            assertEquals(3, AndonCord.Severity.P3_LOW.getLevel());
        }
    }

    @Nested
    @DisplayName("Pull Alert Tests")
    class PullAlertTests {

        @Test
        @DisplayName("Pull creates alert with all parameters")
        void pullCreatesAlert() {
            Map<String, Object> context = Map.of(
                "case_id", "case-123",
                "task_id", "task-456"
            );

            AndonCord.Alert alert = andonCord.pull(
                AndonCord.Severity.P1_HIGH,
                "test_alert",
                context
            );

            assertNotNull(alert);
            assertNotNull(alert.getId());
            assertEquals(AndonCord.Severity.P1_HIGH, alert.getSeverity());
            assertEquals("test_alert", alert.getName());
            assertEquals(AndonCord.State.FIRING, alert.getState());
            assertTrue(alert.getContext().containsKey("case_id"));
            assertEquals("case-123", alert.getContext().get("case_id"));

            assertEquals(1, capturedAlerts.size());
        }

        @Test
        @DisplayName("Pull handles null context gracefully")
        void pullHandlesNullContext() {
            AndonCord.Alert alert = andonCord.pull(
                AndonCord.Severity.P2_MEDIUM,
                "no_context_alert",
                null
            );

            assertNotNull(alert);
            assertTrue(alert.getContext().isEmpty());
        }
    }

    @Nested
    @DisplayName("Deadlock Alert Tests")
    class DeadlockTests {

        @Test
        @DisplayName("Deadlock creates P0 critical alert")
        void deadlockCreatesP0Alert() {
            List<String> deadlockedTasks = List.of("task-A", "task-B", "task-C");

            AndonCord.Alert alert = andonCord.deadlockDetected(
                "case-123",
                "spec-456",
                deadlockedTasks
            );

            assertEquals(AndonCord.Severity.P0_CRITICAL, alert.getSeverity());
            assertEquals(AndonCord.Category.DEADLOCK, alert.getCategory());
            assertEquals("deadlock_detected", alert.getName());
            assertTrue(alert.getMessage().contains("case-123"));
            assertTrue(alert.getMessage().contains("3 tasks blocked"));

            assertEquals("case-123", alert.getContext().get("case_id"));
            assertEquals("spec-456", alert.getContext().get("spec_id"));
            assertEquals(3, alert.getContext().get("deadlocked_task_count"));
        }

        @Test
        @DisplayName("Deadlock appears in active alerts")
        void deadlockAppearsInActiveAlerts() {
            andonCord.deadlockDetected("case-999", "spec-111", List.of("task-X"));

            List<AndonCord.Alert> active = andonCord.getActiveAlerts();
            assertEquals(1, active.size());
            assertEquals(AndonCord.Category.DEADLOCK, active.get(0).getCategory());
        }
    }

    @Nested
    @DisplayName("Lock Contention Tests")
    class LockContentionTests {

        @Test
        @DisplayName("High contention (>500ms) creates P1 alert")
        void highContentionCreatesP1Alert() {
            AndonCord.Alert alert = andonCord.lockContentionHigh(
                "case-123",
                "engine-lock",
                750
            );

            assertEquals(AndonCord.Severity.P1_HIGH, alert.getSeverity());
            assertEquals(AndonCord.Category.LOCK_CONTENTION, alert.getCategory());
            assertTrue(alert.getMessage().contains("750.00ms"));
        }

        @Test
        @DisplayName("Medium contention (<500ms) creates P2 alert")
        void mediumContentionCreatesP2Alert() {
            AndonCord.Alert alert = andonCord.lockContentionHigh(
                "case-456",
                "queue-lock",
                200
            );

            assertEquals(AndonCord.Severity.P2_MEDIUM, alert.getSeverity());
        }

        @Test
        @DisplayName("Lock contention updates heat map")
        void lockContentionUpdatesHeatMap() {
            andonCord.lockContentionHigh("case-1", "lock-A", 100);
            andonCord.lockContentionHigh("case-2", "lock-A", 200);
            andonCord.lockContentionHigh("case-3", "lock-A", 300);

            Map<String, AndonCord.LockContentionEntry> heatMap = andonCord.getLockContentionHeatMap();
            assertTrue(heatMap.containsKey("lock-A"));

            AndonCord.LockContentionEntry entry = heatMap.get("lock-A");
            assertEquals(3, entry.getContentionCount());
            assertEquals(300.0, entry.getMaxWaitMs());
            assertEquals(200.0, entry.getAvgWaitMs(), 0.1);
        }
    }

    @Nested
    @DisplayName("JWKS Cache Tests")
    class JwksCacheTests {

        @Test
        @DisplayName("Stale JWKS creates P0 critical alert")
        void staleJwksCreatesP0Alert() {
            AndonCord.Alert alert = andonCord.jwksCacheStale(3600);

            assertEquals(AndonCord.Severity.P0_CRITICAL, alert.getSeverity());
            assertEquals(AndonCord.Category.JWKS_STALE, alert.getCategory());
            assertTrue(alert.getMessage().contains("3600 seconds old"));
            assertEquals(3600L, alert.getContext().get("cache_age_seconds"));
        }

        @Test
        @DisplayName("Stale JWKS updates health matrix")
        void staleJwksUpdatesHealthMatrix() {
            andonCord.jwksCacheStale(7200);

            Map<String, AndonCord.HealthMatrixEntry> matrix = andonCord.getHealthMatrix();
            assertTrue(matrix.containsKey("jwks"));

            AndonCord.HealthMatrixEntry entry = matrix.get("jwks");
            assertEquals(AndonCord.Severity.P0_CRITICAL, entry.getSeverity());
            assertEquals("STALE", entry.getStatus());
        }
    }

    @Nested
    @DisplayName("Circuit Breaker Tests")
    class CircuitBreakerTests {

        @Test
        @DisplayName("Circuit breaker open creates P1 alert")
        void circuitBreakerOpenCreatesP1Alert() {
            AndonCord.Alert alert = andonCord.circuitBreakerOpen("InterfaceB");

            assertEquals(AndonCord.Severity.P1_HIGH, alert.getSeverity());
            assertEquals(AndonCord.Category.CIRCUIT_BREAKER, alert.getCategory());
            assertTrue(alert.getMessage().contains("InterfaceB"));
        }

        @Test
        @DisplayName("Circuit breaker updates status map")
        void circuitBreakerUpdatesStatusMap() {
            andonCord.circuitBreakerOpen("InterfaceX");

            Map<String, AndonCord.CircuitBreakerEntry> status = andonCord.getCircuitBreakerStatus();
            assertTrue(status.containsKey("InterfaceX"));

            AndonCord.CircuitBreakerEntry entry = status.get("InterfaceX");
            assertEquals("OPEN", entry.getState());
        }
    }

    @Nested
    @DisplayName("Queue Depth Tests")
    class QueueDepthTests {

        @Test
        @DisplayName("Queue depth >70% creates P2 alert")
        void queueDepthCreatesP2Alert() {
            AndonCord.Alert alert = andonCord.queueDepthExceeded(750, 1000);

            assertEquals(AndonCord.Severity.P2_MEDIUM, alert.getSeverity());
            assertEquals(AndonCord.Category.QUEUE_DEPTH, alert.getCategory());
            assertTrue(alert.getMessage().contains("75.0%"));
        }
    }

    @Nested
    @DisplayName("SLA Breach Tests")
    class SlaBreachTests {

        @Test
        @DisplayName("SLA breach creates P2 alert with breach details")
        void slaBreachCreatesP2Alert() {
            Duration actual = Duration.ofSeconds(30);
            Duration sla = Duration.ofSeconds(10);

            AndonCord.Alert alert = andonCord.slaBreach(
                "case-123",
                "case_completion",
                actual,
                sla
            );

            assertEquals(AndonCord.Severity.P2_MEDIUM, alert.getSeverity());
            assertEquals(AndonCord.Category.SLA_BREACH, alert.getCategory());
            assertEquals(30000L, alert.getContext().get("actual_ms"));
            assertEquals(10000L, alert.getContext().get("sla_ms"));
            assertEquals(20000L, alert.getContext().get("breach_ms"));
        }
    }

    @Nested
    @DisplayName("Interface Latency Tests")
    class InterfaceLatencyTests {

        @Test
        @DisplayName("Interface latency creates P3 alert")
        void interfaceLatencyCreatesP3Alert() {
            AndonCord.Alert alert = andonCord.interfaceLatency("InterfaceE", 500);

            assertEquals(AndonCord.Severity.P3_LOW, alert.getSeverity());
            assertEquals(AndonCord.Category.INTERFACE_LATENCY, alert.getCategory());
        }
    }

    @Nested
    @DisplayName("Deprecated Schema Tests")
    class DeprecatedSchemaTests {

        @Test
        @DisplayName("Deprecated schema creates P3 alert")
        void deprecatedSchemaCreatesP3Alert() {
            AndonCord.Alert alert = andonCord.deprecatedSchema("2.0", "spec-789");

            assertEquals(AndonCord.Severity.P3_LOW, alert.getSeverity());
            assertEquals(AndonCord.Category.DEPRECATED_SCHEMA, alert.getCategory());
        }
    }

    @Nested
    @DisplayName("Engine Down Tests")
    class EngineDownTests {

        @Test
        @DisplayName("Engine down creates P0 critical alert")
        void engineDownCreatesP0Alert() {
            AndonCord.Alert alert = andonCord.engineDown("engine-1", "Database connection lost");

            assertEquals(AndonCord.Severity.P0_CRITICAL, alert.getSeverity());
            assertEquals(AndonCord.Category.ENGINE_DOWN, alert.getCategory());
            assertTrue(alert.getMessage().contains("Database connection lost"));
        }
    }

    @Nested
    @DisplayName("Alert Lifecycle Tests")
    class LifecycleTests {

        @Test
        @DisplayName("Acknowledge changes alert state")
        void acknowledgeChangesState() {
            AndonCord.Alert created = andonCord.deadlockDetected("case-1", "spec-1", List.of("task-1"));
            assertEquals(AndonCord.State.FIRING, created.getState());

            AndonCord.Alert acknowledged = andonCord.acknowledge(created.getId());

            assertNotNull(acknowledged);
            assertEquals(AndonCord.State.ACKNOWLEDGED, acknowledged.getState());
            assertNotNull(acknowledged.getAcknowledgedAt());
        }

        @Test
        @DisplayName("Resolve removes from active alerts")
        void resolveRemovesFromActive() {
            AndonCord.Alert created = andonCord.deadlockDetected("case-2", "spec-2", List.of("task-2"));
            assertEquals(1, andonCord.getActiveAlerts().size());

            AndonCord.Alert resolved = andonCord.resolve(created.getId());

            assertNotNull(resolved);
            assertEquals(AndonCord.State.RESOLVED, resolved.getState());
            assertNotNull(resolved.getResolvedAt());
            assertTrue(andonCord.getActiveAlerts().isEmpty());
        }

        @Test
        @DisplayName("Resolve adds to history")
        void resolveAddsToHistory() {
            AndonCord.Alert created = andonCord.deadlockDetected("case-3", "spec-3", List.of("task-3"));
            andonCord.resolve(created.getId());

            List<AndonCord.Alert> history = andonCord.getAlertHistory();
            assertFalse(history.isEmpty());
            assertEquals(AndonCord.State.RESOLVED, history.get(history.size() - 1).getState());
        }

        @Test
        @DisplayName("Acknowledge non-existent alert returns null")
        void acknowledgeNonExistentReturnsNull() {
            AndonCord.Alert result = andonCord.acknowledge("non-existent-id");
            assertNull(result);
        }

        @Test
        @DisplayName("Resolve non-existent alert returns null")
        void resolveNonExistentReturnsNull() {
            AndonCord.Alert result = andonCord.resolve("non-existent-id");
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("Alert Overdue Tests")
    class OverdueTests {

        @Test
        @DisplayName("New alert is not overdue")
        void newAlertIsNotOverdue() {
            AndonCord.Alert alert = andonCord.pull(AndonCord.Severity.P0_CRITICAL, "test", Map.of());
            assertFalse(alert.isOverdue());
        }

        @Test
        @DisplayName("Alert age calculation is correct")
        void alertAgeIsCorrect() throws InterruptedException {
            AndonCord.Alert alert = andonCord.pull(AndonCord.Severity.P3_LOW, "test", Map.of());
            Thread.sleep(100);
            assertTrue(alert.getAge().toMillis() >= 100);
        }
    }

    @Nested
    @DisplayName("Query Tests")
    class QueryTests {

        @Test
        @DisplayName("Get active alerts by severity")
        void getActiveAlertsBySeverity() {
            andonCord.deadlockDetected("case-1", "spec-1", List.of("t1")); // P0
            andonCord.lockContentionHigh("case-2", "lock", 600); // P1
            andonCord.lockContentionHigh("case-3", "lock", 200); // P2

            assertEquals(1, andonCord.getActiveAlertsBySeverity(AndonCord.Severity.P0_CRITICAL).size());
            assertEquals(1, andonCord.getActiveAlertsBySeverity(AndonCord.Severity.P1_HIGH).size());
            assertEquals(1, andonCord.getActiveAlertsBySeverity(AndonCord.Severity.P2_MEDIUM).size());
        }

        @Test
        @DisplayName("Get overdue alerts")
        void getOverdueAlerts() {
            andonCord.pull(AndonCord.Severity.P0_CRITICAL, "test", Map.of());
            // Not overdue immediately after creation
            assertTrue(andonCord.getOverdueAlerts().isEmpty());
        }
    }

    @Nested
    @DisplayName("Health Matrix Tests")
    class HealthMatrixTests {

        @Test
        @DisplayName("Health matrix initialized with default components")
        void healthMatrixInitialized() {
            Map<String, AndonCord.HealthMatrixEntry> matrix = andonCord.getHealthMatrix();

            assertTrue(matrix.containsKey("engine"));
            assertTrue(matrix.containsKey("cases"));
            assertTrue(matrix.containsKey("locks"));
            assertTrue(matrix.containsKey("jwks"));
            assertTrue(matrix.containsKey("db"));
        }

        @Test
        @DisplayName("Health matrix entry has required fields")
        void healthMatrixEntryHasFields() {
            andonCord.engineDown("engine-test", "Test shutdown");

            AndonCord.HealthMatrixEntry entry = andonCord.getHealthMatrix().get("engine");
            assertNotNull(entry);
            assertEquals("engine", entry.getComponent());
            assertEquals(AndonCord.Severity.P0_CRITICAL, entry.getSeverity());
            assertEquals("DOWN", entry.getStatus());
            assertNotNull(entry.getLastUpdated());
        }
    }

    @Nested
    @DisplayName("Listener Tests")
    class ListenerTests {

        @Test
        @DisplayName("Listener receives all alerts")
        void listenerReceivesAlerts() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(3);
            AtomicReference<AndonCord.Alert> lastAlert = new AtomicReference<>();

            Consumer<AndonCord.Alert> listener = alert -> {
                lastAlert.set(alert);
                latch.countDown();
            };

            andonCord.addListener(listener);

            andonCord.deadlockDetected("case-1", "spec-1", List.of("t1"));
            andonCord.lockContentionHigh("case-2", "lock", 100);
            andonCord.interfaceLatency("InterfaceX", 50);

            assertTrue(latch.await(1, TimeUnit.SECONDS));
            assertNotNull(lastAlert.get());

            andonCord.removeListener(listener);
        }
    }

    @Nested
    @DisplayName("Configuration Tests")
    class ConfigurationTests {

        @Test
        @DisplayName("Configuration builder creates valid config")
        void configurationBuilderCreatesValidConfig() {
            AndonCord.Configuration config = AndonCord.Configuration.builder()
                .pagerDutyRoutingKey("test-key")
                .slackWebhookUrl("https://hooks.slack.com/test")
                .escalationInterval(Duration.ofMinutes(10))
                .enableMetrics(false)
                .enableTracing(false)
                .build();

            assertEquals("test-key", config.getPagerDutyRoutingKey());
            assertEquals("https://hooks.slack.com/test", config.getSlackWebhookUrl());
            assertEquals(Duration.ofMinutes(10), config.getEscalationInterval());
            assertFalse(config.isMetricsEnabled());
            assertFalse(config.isTracingEnabled());
            assertTrue(config.isPagerDutyConfigured());
            assertTrue(config.isSlackConfigured());
        }

        @Test
        @DisplayName("Default configuration has correct values")
        void defaultConfigurationHasCorrectValues() {
            AndonCord.Configuration config = AndonCord.Configuration.builder().build();

            assertFalse(config.isPagerDutyConfigured());
            assertFalse(config.isSlackConfigured());
            assertTrue(config.isMetricsEnabled());
            assertTrue(config.isTracingEnabled());
            assertEquals(Duration.ofMinutes(5), config.getEscalationInterval());
        }
    }

    @Nested
    @DisplayName("Alert Builder Tests")
    class AlertBuilderTests {

        @Test
        @DisplayName("Alert builder creates complete alert")
        void alertBuilderCreatesCompleteAlert() {
            AndonCord.Alert alert = new AndonCord.Builder()
                .severity(AndonCord.Severity.P1_HIGH)
                .category(AndonCord.Category.RESOURCE_EXHAUSTION)
                .name("memory_exhausted")
                .message("Memory usage at 95%")
                .context("heap_used", "4.8GB")
                .context("heap_max", "5GB")
                .build();

            assertEquals(AndonCord.Severity.P1_HIGH, alert.getSeverity());
            assertEquals(AndonCord.Category.RESOURCE_EXHAUSTION, alert.getCategory());
            assertEquals("memory_exhausted", alert.getName());
            assertEquals("Memory usage at 95%", alert.getMessage());
            assertEquals("4.8GB", alert.getContext().get("heap_used"));
        }

        @Test
        @DisplayName("Alert toBuilder preserves all fields")
        void alertToBuilderPreservesFields() {
            AndonCord.Alert original = new AndonCord.Builder()
                .severity(AndonCord.Severity.P0_CRITICAL)
                .category(AndonCord.Category.DEADLOCK)
                .name("test")
                .message("test message")
                .context("key", "value")
                .build();

            AndonCord.Alert copy = original.toBuilder().build();

            assertEquals(original.getSeverity(), copy.getSeverity());
            assertEquals(original.getCategory(), copy.getCategory());
            assertEquals(original.getName(), copy.getName());
            assertEquals(original.getMessage(), copy.getMessage());
            assertEquals(original.getContext(), copy.getContext());
        }
    }

    @Nested
    @DisplayName("Concurrency Tests")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent alert firing is thread-safe")
        void concurrentAlertFiringIsThreadSafe() throws InterruptedException {
            int threadCount = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                new Thread(() -> {
                    try {
                        startLatch.await();
                        andonCord.pull(
                            AndonCord.Severity.P2_MEDIUM,
                            "concurrent_alert_" + index,
                            Map.of("thread", index)
                        );
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                }).start();
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(5, TimeUnit.SECONDS));
            assertEquals(threadCount, andonCord.getActiveAlerts().size());
        }
    }
}
