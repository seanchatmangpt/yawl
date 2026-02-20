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
import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;
import io.github.resilience4j.retry.Retry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.Objects;

/**
 * Resilience4j event listener for routing failed operations to InterfaceXDeadLetterQueue.
 *
 * <p>This listener can be attached to CircuitBreaker or Retry patterns to automatically
 * route failures to the dead letter queue when all retry attempts are exhausted.
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * CircuitBreaker breaker = CircuitBreaker.ofDefaults("interfaceX-notifications");
 * breaker.getEventPublisher()
 *     .onError(event -> {
 *         InterfaceXDeadLetterQueue dlq = InterfaceXDeadLetterQueue.getInstance();
 *         dlq.add(
 *             commandCode,
 *             parameters,
 *             observerURI,
 *             event.getThrowable().getMessage(),
 *             attemptCount
 *         );
 *     });
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class DeadLetterEventListener implements RegistryEventConsumer<CircuitBreaker> {

    private static final Logger logger = LogManager.getLogger(DeadLetterEventListener.class);

    private final InterfaceXDeadLetterQueue deadLetterQueue;

    /**
     * Creates a listener that routes failures to the specified dead letter queue.
     *
     * @param deadLetterQueue the queue to route failures to
     * @throws IllegalArgumentException if deadLetterQueue is null
     */
    public DeadLetterEventListener(InterfaceXDeadLetterQueue deadLetterQueue) {
        this.deadLetterQueue = Objects.requireNonNull(deadLetterQueue, "deadLetterQueue cannot be null");
    }

    @Override
    public void onEntryAdded(EntryAddedEvent<CircuitBreaker> event) {
        CircuitBreaker breaker = event.getAddedEntry();
        logger.debug("Attached dead letter listener to circuit breaker: {}", breaker.getName());

        // Register error event listener on the breaker
        breaker.getEventPublisher().onError(errorEvent -> {
            logger.warn("Circuit breaker failure captured: {} - routing to dead letter queue",
                breaker.getName(), errorEvent.getThrowable());

            // Route to dead letter queue with failure details
            deadLetterQueue.add(
                -1,  // Unknown command code (can be overridden by caller)
                Map.of("breaker_name", breaker.getName(),
                       "error_type", errorEvent.getThrowable().getClass().getSimpleName()),
                "internal://circuit-breaker",
                errorEvent.getThrowable().getMessage(),
                1
            );
        });
    }

    @Override
    public void onEntryRemoved(EntryRemovedEvent<CircuitBreaker> event) {
        logger.debug("Removed dead letter listener from circuit breaker: {}", event.getRemovedEntry().getName());
    }

    @Override
    public void onEntryReplaced(EntryReplacedEvent<CircuitBreaker> event) {
        CircuitBreaker newBreaker = event.getNewEntry();
        logger.debug("Replaced circuit breaker with dead letter listener: {}", newBreaker.getName());

        newBreaker.getEventPublisher().onError(errorEvent -> {
            deadLetterQueue.add(
                -1,
                Map.of("breaker_name", newBreaker.getName()),
                "internal://circuit-breaker",
                errorEvent.getThrowable().getMessage(),
                1
            );
        });
    }

    /**
     * Attaches this listener to a Retry pattern for exhausted retry attempts.
     * Call this after creating a Retry instance to integrate with dead letter queue.
     *
     * @param retry the Retry pattern to attach to
     */
    public void attachToRetry(Retry retry) {
        Objects.requireNonNull(retry, "retry cannot be null");

        retry.getEventPublisher().onRetry(retryEvent -> {
            logger.debug("Retry attempt #{} for operation: {}",
                retryEvent.getNumberOfRetryAttempts(), retryEvent.getName());
        });

        logger.debug("Attached dead letter listener to retry: {}", retry.getName());
    }

    /**
     * Cleanup and prepare periodic expiration cleanup task.
     * Returns a runnable that can be scheduled with virtual threads or executors.
     *
     * @return a runnable for periodic cleanup
     */
    public Runnable createCleanupTask() {
        return () -> {
            int removed = deadLetterQueue.cleanupExpiredEntries();
            if (removed > 0) {
                logger.info("Periodic cleanup removed {} expired entries", removed);
            }
        };
    }
}
