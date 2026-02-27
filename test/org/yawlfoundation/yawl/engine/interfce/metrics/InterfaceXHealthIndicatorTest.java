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

package org.yawlfoundation.yawl.engine.interfce.metrics;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for InterfaceXHealthIndicator.
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@DisplayName("InterfaceXHealthIndicator Tests")
class InterfaceXHealthIndicatorTest {

    private CircuitBreakerRegistry circuitBreakerRegistry;
    private InterfaceXHealthIndicator healthIndicator;
    private InterfaceMetrics interfaceMetrics;

    @BeforeEach
    void setUp() {
        circuitBreakerRegistry = CircuitBreakerRegistry.ofDefaults();
        healthIndicator = new InterfaceXHealthIndicator(circuitBreakerRegistry);
        interfaceMetrics = InterfaceMetrics.getInstance();
        interfaceMetrics.reset();
        healthIndicator.reset();
    }

    @Nested
    @DisplayName("Health Status Tests")
    class HealthStatusTests {

        @Test
        @DisplayName("health returns UP when no issues")
        void health_returnsUpWhenNoIssues() {
            Health health = healthIndicator.health();

            assertEquals(Status.UP, health.getStatus(), "Should be UP when no issues");
        }

        @Test
        @DisplayName("health returns UP status details")
        void health_returnsUpStatusDetails() {
            Health health = healthIndicator.health();

            assertEquals("UP", health.getDetails().get("status"), "Status detail should be UP");
        }

        @Test
        @DisplayName("health includes circuit breaker details")
        void health_includesCircuitBreakerDetails() {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("interfaceX-notifications");

            Health health = healthIndicator.health();

            assertTrue(health.getDetails().containsKey("circuitBreaker.state"),
                    "Should include circuit breaker state");
        }

        @Test
        @DisplayName("health includes notification metrics")
        void health_includesNotificationMetrics() {
            interfaceMetrics.recordInterfaceXNotification("workItemAbort", true);
            interfaceMetrics.recordInterfaceXNotification("timeout", true);
            interfaceMetrics.recordInterfaceXRetry("timeout", 1);

            Health health = healthIndicator.health();

            assertTrue(health.getDetails().containsKey("notifications.total"),
                    "Should include total notifications");
            assertTrue(health.getDetails().containsKey("notifications.retries"),
                    "Should include retry count");
            assertTrue(health.getDetails().containsKey("notifications.retryRate"),
                    "Should include retry rate");
        }

        @Test
        @DisplayName("health includes dead letter queue size")
        void health_includesDeadLetterQueueSize() {
            healthIndicator.addToDeadLetterQueue("workItemAbort", "<payload/>", "Connection refused");

            Health health = healthIndicator.health();

            assertEquals(1, health.getDetails().get("deadLetterQueue.size"),
                    "Should include DLQ size");
        }
    }

    @Nested
    @DisplayName("Circuit Breaker State Tests")
    class CircuitBreakerStateTests {

        @Test
        @DisplayName("UP when circuit breaker is CLOSED")
        void upWhenCircuitBreakerClosed() {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("interfaceX-notifications");

            Health health = healthIndicator.health();

            assertEquals(Status.UP, health.getStatus(), "Should be UP when circuit breaker is CLOSED");
        }

        @Test
        @DisplayName("DOWN when circuit breaker is OPEN")
        void downWhenCircuitBreakerOpen() {
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(
                    "interfaceX-notifications",
                    CircuitBreakerConfig.custom()
                            .failureRateThreshold(1.0f)
                            .slidingWindowSize(1)
                            .build()
            );

            circuitBreaker.onError(0, java.util.concurrent.TimeUnit.NANOSECONDS, new RuntimeException("Test"));

            Health health = healthIndicator.health();

            assertEquals(Status.DOWN, health.getStatus(), "Should be DOWN when circuit breaker is OPEN");
        }
    }

    @Nested
    @DisplayName("Dead Letter Queue Tests")
    class DeadLetterQueueTests {

        @Test
        @DisplayName("addToDeadLetterQueue increases queue size")
        void addToDeadLetterQueue_increasesQueueSize() {
            healthIndicator.addToDeadLetterQueue("workItemAbort", "<payload/>", "Connection refused");

            assertEquals(1, healthIndicator.getDeadLetterQueueSize(), "DLQ should have 1 entry");
        }

        @Test
        @DisplayName("addToDeadLetterQueue stores entry details")
        void addToDeadLetterQueue_storesEntryDetails() {
            healthIndicator.addToDeadLetterQueue("timeout", "<workitem/>", "Timeout");

            Map<String, InterfaceXHealthIndicator.DeadLetterEntry> entries =
                    healthIndicator.getDeadLetterEntriesForRetry(10);

            assertEquals(1, entries.size(), "Should have 1 entry");
            InterfaceXHealthIndicator.DeadLetterEntry entry = entries.values().iterator().next();
            assertEquals("timeout", entry.getEventType(), "Event type should be stored");
            assertEquals("<workitem/>", entry.getPayload(), "Payload should be stored");
            assertEquals("Timeout", entry.getErrorMessage(), "Error message should be stored");
        }

        @Test
        @DisplayName("getDeadLetterEntriesForRetry limits entries")
        void getDeadLetterEntriesForRetry_limitsEntries() {
            for (int i = 0; i < 10; i++) {
                healthIndicator.addToDeadLetterQueue("event" + i, "<payload/>", "error");
            }

            Map<String, InterfaceXHealthIndicator.DeadLetterEntry> entries =
                    healthIndicator.getDeadLetterEntriesForRetry(3);

            assertTrue(entries.size() <= 3, "Should return at most 3 entries");
        }

        @Test
        @DisplayName("removeFromDeadLetterQueue removes entry")
        void removeFromDeadLetterQueue_removesEntry() {
            healthIndicator.addToDeadLetterQueue("event1", "<payload/>", "error");
            healthIndicator.addToDeadLetterQueue("event2", "<payload/>", "error");

            long timestamp = healthIndicator.getDeadLetterEntriesForRetry(10)
                    .values().iterator().next().getTimestamp();
            healthIndicator.removeFromDeadLetterQueue("event1", timestamp);

            assertEquals(1, healthIndicator.getDeadLetterQueueSize(), "Should have 1 entry remaining");
        }

        @Test
        @DisplayName("clearDeadLetterQueue removes all entries")
        void clearDeadLetterQueue_removesAllEntries() {
            healthIndicator.addToDeadLetterQueue("event1", "<payload/>", "error");
            healthIndicator.addToDeadLetterQueue("event2", "<payload/>", "error");
            healthIndicator.addToDeadLetterQueue("event3", "<payload/>", "error");

            healthIndicator.clearDeadLetterQueue();

            assertEquals(0, healthIndicator.getDeadLetterQueueSize(), "DLQ should be empty");
        }
    }

    @Nested
    @DisplayName("Notification Tracking Tests")
    class NotificationTrackingTests {

        @Test
        @DisplayName("recordNotificationAttempt tracks total")
        void recordNotificationAttempt_tracksTotal() {
            healthIndicator.recordNotificationAttempt(true);
            healthIndicator.recordNotificationAttempt(true);
            healthIndicator.recordNotificationAttempt(false);

            assertEquals(3, healthIndicator.getTotalNotifications(), "Should track 3 notifications");
        }

        @Test
        @DisplayName("recordNotificationAttempt tracks successful")
        void recordNotificationAttempt_tracksSuccessful() {
            healthIndicator.recordNotificationAttempt(true);
            healthIndicator.recordNotificationAttempt(true);
            healthIndicator.recordNotificationAttempt(false);

            assertEquals(2, healthIndicator.getSuccessfulNotifications(), "Should track 2 successful");
        }

        @Test
        @DisplayName("recordNotificationRetry increments counter")
        void recordNotificationRetry_incrementsCounter() {
            healthIndicator.recordNotificationRetry();
            healthIndicator.recordNotificationRetry();

            assertEquals(2, healthIndicator.getRetriedNotifications(), "Should track 2 retries");
        }

        @Test
        @DisplayName("recordNotificationFailure increments counter")
        void recordNotificationFailure_incrementsCounter() {
            healthIndicator.recordNotificationFailure();
            healthIndicator.recordNotificationFailure();

            assertEquals(2, healthIndicator.getFailedNotifications(), "Should track 2 failures");
        }
    }

    @Nested
    @DisplayName("Reset Tests")
    class ResetTests {

        @Test
        @DisplayName("reset clears all counters and DLQ")
        void reset_clearsAll() {
            healthIndicator.recordNotificationAttempt(true);
            healthIndicator.recordNotificationRetry();
            healthIndicator.recordNotificationFailure();
            healthIndicator.addToDeadLetterQueue("event", "<payload/>", "error");

            healthIndicator.reset();

            assertEquals(0, healthIndicator.getTotalNotifications(), "Total should be 0");
            assertEquals(0, healthIndicator.getSuccessfulNotifications(), "Successful should be 0");
            assertEquals(0, healthIndicator.getRetriedNotifications(), "Retries should be 0");
            assertEquals(0, healthIndicator.getFailedNotifications(), "Failures should be 0");
            assertEquals(0, healthIndicator.getDeadLetterQueueSize(), "DLQ should be empty");
        }
    }

    @Nested
    @DisplayName("Health Response Details Tests")
    class HealthResponseDetailsTests {

        @Test
        @DisplayName("health response contains all expected details")
        void healthResponse_containsAllExpectedDetails() {
            Health health = healthIndicator.health();

            assertTrue(health.getDetails().containsKey("status"), "Should contain status");
            assertTrue(health.getDetails().containsKey("circuitBreaker.state"), "Should contain circuit breaker state");
            assertTrue(health.getDetails().containsKey("circuitBreaker.failureRate"), "Should contain failure rate");
            assertTrue(health.getDetails().containsKey("notifications.total"), "Should contain total notifications");
            assertTrue(health.getDetails().containsKey("notifications.retryRate"), "Should contain retry rate");
            assertTrue(health.getDetails().containsKey("deadLetterQueue.size"), "Should contain DLQ size");
        }
    }
}
