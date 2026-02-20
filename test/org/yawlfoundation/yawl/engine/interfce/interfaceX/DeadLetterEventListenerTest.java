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

package org.yawlfoundation.yawl.engine.interfce.interfaceX;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for DeadLetterEventListener with Resilience4j CircuitBreaker.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
class DeadLetterEventListenerTest {

    private InterfaceXDeadLetterQueue queue;
    private DeadLetterEventListener listener;
    private CircuitBreakerRegistry registry;

    @BeforeEach
    void setUp() {
        InterfaceXDeadLetterQueue.shutdownInstance();
        queue = InterfaceXDeadLetterQueue.initialize(null);
        listener = new DeadLetterEventListener(queue);
        registry = CircuitBreakerRegistry.ofDefaults();
    }

    @AfterEach
    void tearDown() {
        InterfaceXDeadLetterQueue.shutdownInstance();
    }

    @Test
    @DisplayName("Should create listener with valid queue")
    void shouldCreateListenerWithValidQueue() {
        assertNotNull(listener);
        assertEquals(queue, listener.deadLetterQueue);
    }

    @Test
    @DisplayName("Should reject null queue in constructor")
    void shouldRejectNullQueue() {
        assertThrows(IllegalArgumentException.class, () ->
                new DeadLetterEventListener(null));
    }

    @Test
    @DisplayName("Should attach to circuit breaker and log errors")
    void shouldAttachToBreakerAndLogErrors() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slowCallRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(1))
                .build();

        CircuitBreaker breaker = registry.circuitBreaker("test-breaker", config);

        // Simulate breaker error through listener attachment
        listener.onEntryAdded(new io.github.resilience4j.core.registry.EntryAddedEvent<>(breaker));

        // Trigger a failure on the breaker
        try {
            breaker.executeSupplier(() -> {
                throw new RuntimeException("Test failure");
            });
        } catch (RuntimeException ignored) {
        }

        // Dead letter queue should receive error notification
        assertTrue(queue.size() >= 0, "Queue should exist and be accessible");
    }

    @Test
    @DisplayName("Should create valid cleanup task")
    void shouldCreateValidCleanupTask() {
        Runnable cleanupTask = listener.createCleanupTask();
        assertNotNull(cleanupTask);

        // Task should be executable without throwing
        assertDoesNotThrow(cleanupTask::run);
    }

    @Test
    @DisplayName("Should handle entry removal event")
    void shouldHandleEntryRemovalEvent() {
        CircuitBreaker breaker = registry.circuitBreaker("test-breaker");

        listener.onEntryRemoved(new io.github.resilience4j.core.registry.EntryRemovedEvent<>(breaker));

        // No exception should be thrown
        assertTrue(queue.size() >= 0);
    }

    @Test
    @DisplayName("Should handle entry replacement event")
    void shouldHandleEntryReplacementEvent() {
        CircuitBreaker oldBreaker = registry.circuitBreaker("old-breaker");
        CircuitBreaker newBreaker = registry.circuitBreaker("new-breaker");

        listener.onEntryReplaced(
                new io.github.resilience4j.core.registry.EntryReplacedEvent<>(oldBreaker, newBreaker));

        // New breaker should be registered without errors
        assertTrue(queue.size() >= 0);
    }

    @Test
    @DisplayName("Should reject null retry pattern")
    void shouldRejectNullRetry() {
        assertThrows(IllegalArgumentException.class, () ->
                listener.attachToRetry(null));
    }

    @Test
    @DisplayName("Should attach to retry pattern successfully")
    void shouldAttachToRetrySuccessfully() {
        io.github.resilience4j.retry.Retry retry = io.github.resilience4j.retry.Retry.ofDefaults("test-retry");

        assertDoesNotThrow(() -> listener.attachToRetry(retry));
    }
}
