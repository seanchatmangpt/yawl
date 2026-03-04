/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.resilience.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongGauge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Observability metrics for retry operations.
 *
 * <p>Provides metrics tracking for retry operations including success/failure counts,
 * retry attempts, timing, and circuit breaker state.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@Component
public class RetryObservability {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetryObservability.class);

    private static final String COMPONENT_NAME = "retry";
    private static final String METRIC_PREFIX = "resilience.retry.";

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    @Autowired(required = false)
    private Meter openTelemetryMeter;

    private Counter retrySuccessCounter;
    private Counter retryFailureCounter;
    private Counter retryAttemptsCounter;
    private Timer retryTimer;

    private final AtomicLong circuitBreakerOpenCount = new AtomicLong(0);
    private final AtomicLong circuitBreakerClosedCount = new AtomicLong(0);
    private final AtomicLong circuitBreakerHalfOpenCount = new AtomicLong(0);

    private static RetryObservability instance;

    public RetryObservability() {
        instance = this;
    }

    public static RetryObservability getInstance() {
        if (instance == null) {
            instance = new RetryObservability();
        }
        return instance;
    }

    /**
     * Context for tracking individual retry operations.
     */
    public static class RetryContext {
        private final String componentName;
        private final String operation;
        private final int attempt;
        private final int maxAttempts;
        private final long backoffMs;
        private final long startTime;
        private RetryObservability observability;

        public RetryContext(String componentName, String operation, int attempt, 
                          int maxAttempts, long backoffMs, RetryObservability observability) {
            this.componentName = componentName;
            this.operation = operation;
            this.attempt = attempt;
            this.maxAttempts = maxAttempts;
            this.backoffMs = backoffMs;
            this.startTime = System.currentTimeMillis();
            this.observability = observability;
        }

        /**
         * Records successful completion of this retry attempt.
         */
        public void recordSuccess() {
            if (observability != null) {
                observability.recordSuccess();
            }
            LOGGER.debug("Retry operation succeeded: component={}, operation={}, attempt={}/{}", 
                       componentName, operation, attempt, maxAttempts);
        }

        /**
         * Records failure of this retry attempt.
         *
         * @param cause the exception that caused the failure
         */
        public void recordFailure(Throwable cause) {
            if (observability != null) {
                observability.recordFailure(cause);
            }
            LOGGER.debug("Retry operation failed: component={}, operation={}, attempt={}/{}, error={}", 
                       componentName, operation, attempt, maxAttempts, cause.getMessage());
        }
    }

    @PostConstruct
    public void init() {
        if (meterRegistry != null) {
            // Prometheus/Grafana metrics
            retrySuccessCounter = Counter.builder(METRIC_PREFIX + "success.count")
                .description("Number of successful retry operations")
                .tag("component", COMPONENT_NAME)
                .register(meterRegistry);

            retryFailureCounter = Counter.builder(METRIC_PREFIX + "failure.count")
                .description("Number of failed retry operations")
                .tag("component", COMPONENT_NAME)
                .register(meterRegistry);

            retryAttemptsCounter = Counter.builder(METRIC_PREFIX + "attempts.count")
                .description("Number of retry attempts")
                .tag("component", COMPONENT_NAME)
                .register(meterRegistry);

            retryTimer = Timer.builder(METRIC_PREFIX + "duration")
                .description("Duration of retry operations")
                .tag("component", COMPONENT_NAME)
                .register(meterRegistry);
        }

        // OpenTelemetry metrics temporarily disabled due to API changes
        // OPENTELEMETRY METRICS DISABLED - Throws UnsupportedOperationException
        LOGGER.debug("OpenTelemetry metrics disabled - API version incompatibility");
    }

    /**
     * Starts a new retry operation context.
     *
     * @param componentName the name of the component performing the retry
     * @param operation the name of the operation being retried
     * @param attempt the current attempt number (1-based)
     * @param maxAttempts the maximum number of attempts allowed
     * @param backoffMs the backoff duration for this attempt in milliseconds
     * @return a new RetryContext for this operation
     */
    public RetryContext startRetry(String componentName, String operation, 
                                 int attempt, int maxAttempts, long backoffMs) {
        recordAttempt();
        return new RetryContext(componentName, operation, attempt, maxAttempts, backoffMs, this);
    }

    /**
     * Records completion of a retry sequence.
     *
     * @param componentName the name of the component performing the retry
     * @param operation the name of the operation being retried
     * @param success whether the sequence was successful
     * @param attempts the total number of attempts made
     */
    public void completeSequence(String componentName, String operation, 
                                boolean success, int attempts) {
        if (success) {
            recordSuccess();
        } else {
            recordFailure(new RuntimeException("Operation failed after " + attempts + " attempts"));
        }
        
        LOGGER.info("Retry sequence completed: component={}, operation={}, success={}, attempts={}", 
                   componentName, operation, success, attempts);
    }

    /**
     * Records a backoff event during retry.
     *
     * @param componentName the name of the component performing the retry
     * @param operation the name of the operation being retried
     * @param backoffMs the backoff duration in milliseconds
     */
    public void recordBackoff(String componentName, String operation, long backoffMs) {
        LOGGER.debug("Retry backoff recorded: component={}, operation={}, backoff={}ms", 
                   componentName, operation, backoffMs);
    }

    /**
     * Records a successful retry operation.
     */
    public void recordSuccess() {
        if (retrySuccessCounter != null) {
            retrySuccessCounter.increment();
        }
        LOGGER.debug("Retry operation succeeded");
    }

    /**
     * Records a failed retry operation.
     *
     * @param cause the exception that caused the failure
     */
    public void recordFailure(Throwable cause) {
        if (retryFailureCounter != null) {
            retryFailureCounter.increment();
        }
        LOGGER.debug("Retry operation failed: {}", cause.getMessage());
    }

    /**
     * Records a retry attempt.
     */
    public void recordAttempt() {
        if (retryAttemptsCounter != null) {
            retryAttemptsCounter.increment();
        }
    }

    /**
     * Records the duration of a retry operation.
     *
     * @param duration the duration of the operation
     */
    public void recordDuration(Duration duration) {
        if (retryTimer != null) {
            retryTimer.record(duration);
        }
        LOGGER.debug("Retry operation took {} ms", duration.toMillis());
    }

    /**
     * Records circuit breaker state transitions.
     *
     * @param state the new state (0=closed, 1=half-open, 2=open)
     */
    public void recordCircuitBreakerState(int state) {
        switch (state) {
            case 0: // Closed
                circuitBreakerClosedCount.incrementAndGet();
                break;
            case 1: // Half-open
                circuitBreakerHalfOpenCount.incrementAndGet();
                break;
            case 2: // Open
                circuitBreakerOpenCount.incrementAndGet();
                break;
        }
    }

    /**
     * Gets the current circuit breaker state count.
     *
     * @param state the state to query (0=closed, 1=half-open, 2=open)
     * @return the count for the given state
     */
    public long getCircuitBreakerStateCount(int state) {
        return switch (state) {
            case 0 -> circuitBreakerClosedCount.get();
            case 1 -> circuitBreakerHalfOpenCount.get();
            case 2 -> circuitBreakerOpenCount.get();
            default -> 0;
        };
    }

    private Runnable circuitBreakerStateGaugeReader() {
        return () -> {
            // This is a simplified implementation - in practice, you'd want to
            // track the actual state and return it here
            // No-op for gauge reader
        };
    }
}
