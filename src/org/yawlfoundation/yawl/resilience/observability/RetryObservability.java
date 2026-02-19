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

package org.yawlfoundation.yawl.resilience.observability;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.engine.observability.YAWLTelemetry;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * OpenTelemetry-based observability for retry operations across YAWL components.
 *
 * <p>This class provides comprehensive metrics and tracing for retry logic including:
 * <ul>
 *   <li>Counter: yawl_retry_attempts_total - Total retry attempts by operation and component</li>
 *   <li>Histogram: yawl_retry_backoff_duration_seconds - Time spent in backoff between retries</li>
 *   <li>Tracing: Creates spans for each retry attempt with detailed attributes</li>
 * </ul>
 *
 * <h2>Metrics</h2>
 * <pre>
 * # Counter - total retry attempts
 * yawl_retry_attempts_total{component="webhook", operation="deliver", result="success|failure"}
 *
 * # Histogram - backoff duration
 * yawl_retry_backoff_duration_seconds{component="connection-pool", operation="connect"}
 * </pre>
 *
 * <h2>Tracing</h2>
 * <p>Each retry attempt creates a span named: {component}.{operation}.retry
 * <p>Span attributes include:
 * <ul>
 *   <li>retry.attempt.number - Current attempt number</li>
 *   <li>retry.attempt.total - Total configured attempts</li>
 *   <li>retry.backoff.ms - Backoff duration in milliseconds</li>
 *   <li>retry.result - "success", "failure", or "exhausted"</li>
 *   <li>retry.error.type - Exception class name on failure</li>
 *   <li>retry.error.message - Exception message on failure</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * RetryObservability observability = RetryObservability.getInstance();
 *
 * for (int attempt = 1; attempt <= maxAttempts; attempt++) {
 *     RetryObservability.RetryContext ctx = observability.startRetry(
 *         "webhook", "deliver", attempt, maxAttempts, backoffMs);
 *
 *     try {
 *         Result result = operation.execute();
 *         ctx.recordSuccess();
 *         return result;
 *     } catch (Exception e) {
 *         ctx.recordFailure(e);
 *         if (attempt < maxAttempts) {
 *             Thread.sleep(backoffMs);
 *         }
 *     }
 * }
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class RetryObservability {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetryObservability.class);

    private static volatile RetryObservability instance;

    // Metric names
    private static final String METRIC_RETRY_ATTEMPTS = "yawl_retry_attempts_total";
    private static final String METRIC_BACKOFF_DURATION = "yawl_retry_backoff_duration_seconds";

    // Attribute keys
    public static final AttributeKey<String> ATTR_COMPONENT = AttributeKey.stringKey("yawl.retry.component");
    public static final AttributeKey<String> ATTR_OPERATION = AttributeKey.stringKey("yawl.retry.operation");
    public static final AttributeKey<String> ATTR_RESULT = AttributeKey.stringKey("yawl.retry.result");
    public static final AttributeKey<Long> ATTR_ATTEMPT_NUMBER = AttributeKey.longKey("retry.attempt.number");
    public static final AttributeKey<Long> ATTR_ATTEMPT_TOTAL = AttributeKey.longKey("retry.attempt.total");
    public static final AttributeKey<Long> ATTR_BACKOFF_MS = AttributeKey.longKey("retry.backoff.ms");
    public static final AttributeKey<String> ATTR_ERROR_TYPE = AttributeKey.stringKey("retry.error.type");
    public static final AttributeKey<String> ATTR_ERROR_MESSAGE = AttributeKey.stringKey("retry.error.message");

    // Result values
    public static final String RESULT_SUCCESS = "success";
    public static final String RESULT_FAILURE = "failure";
    public static final String RESULT_EXHAUSTED = "exhausted";

    private final LongCounter retryAttemptsCounter;
    private final DoubleHistogram backoffDurationHistogram;
    private final Tracer tracer;

    // Track active retry sequences for correlation
    private final ConcurrentHashMap<String, RetrySequence> activeSequences = new ConcurrentHashMap<>();
    private final AtomicLong sequenceCounter = new AtomicLong(0);

    private RetryObservability() {
        YAWLTelemetry telemetry = YAWLTelemetry.getInstance();
        Meter meter = telemetry.getMeter();
        this.tracer = telemetry.getTracer();

        // Initialize counter for retry attempts
        this.retryAttemptsCounter = meter
                .counterBuilder(METRIC_RETRY_ATTEMPTS)
                .setDescription("Total number of retry attempts")
                .setUnit("attempts")
                .build();

        // Initialize histogram for backoff duration
        this.backoffDurationHistogram = meter
                .histogramBuilder(METRIC_BACKOFF_DURATION)
                .setDescription("Duration of backoff between retry attempts")
                .setUnit("s")
                .build();

        LOGGER.info("RetryObservability initialized with OpenTelemetry metrics");
    }

    /**
     * Get the singleton instance of RetryObservability.
     *
     * @return the RetryObservability instance
     */
    public static RetryObservability getInstance() {
        if (instance == null) {
            synchronized (RetryObservability.class) {
                if (instance == null) {
                    instance = new RetryObservability();
                }
            }
        }
        return instance;
    }

    /**
     * Start tracking a retry attempt.
     *
     * <p>This method creates a span for the retry attempt and returns a context
     * that should be used to record the result.
     *
     * @param component the component performing the retry (e.g., "webhook", "connection-pool")
     * @param operation the operation being retried (e.g., "deliver", "connect")
     * @param attemptNumber the current attempt number (1-based)
     * @param totalAttempts the total number of attempts that will be made
     * @param backoffMs the backoff duration in milliseconds (0 for first attempt)
     * @return a RetryContext to record the result
     */
    public RetryContext startRetry(String component, String operation,
                                   int attemptNumber, int totalAttempts, long backoffMs) {
        Objects.requireNonNull(component, "component must not be null");
        Objects.requireNonNull(operation, "operation must not be null");

        String spanName = component + "." + operation + ".retry";

        AttributesBuilder attrsBuilder = Attributes.builder()
                .put(ATTR_COMPONENT, component)
                .put(ATTR_OPERATION, operation)
                .put(ATTR_ATTEMPT_NUMBER, attemptNumber)
                .put(ATTR_ATTEMPT_TOTAL, totalAttempts)
                .put(ATTR_BACKOFF_MS, backoffMs);

        Span span = tracer.spanBuilder(spanName)
                .setSpanKind(SpanKind.INTERNAL)
                .setAllAttributes(attrsBuilder.build())
                .startSpan();

        // Record backoff duration if this is a retry (not first attempt)
        if (backoffMs > 0) {
            double backoffSeconds = backoffMs / 1000.0;
            backoffDurationHistogram.record(backoffSeconds, Attributes.of(
                    ATTR_COMPONENT, component,
                    ATTR_OPERATION, operation
            ));
        }

        return new RetryContext(component, operation, attemptNumber, totalAttempts, span);
    }

    /**
     * Start tracking a retry attempt without backoff (first attempt).
     *
     * @param component the component performing the retry
     * @param operation the operation being retried
     * @param attemptNumber the current attempt number
     * @param totalAttempts the total number of attempts
     * @return a RetryContext to record the result
     */
    public RetryContext startRetry(String component, String operation,
                                   int attemptNumber, int totalAttempts) {
        return startRetry(component, operation, attemptNumber, totalAttempts, 0);
    }

    /**
     * Record a retry attempt directly without span management.
     *
     * <p>Use this for simple metric recording when you don't need span tracking.
     *
     * @param component the component performing the retry
     * @param operation the operation being retried
     * @param result the result ("success", "failure", or "exhausted")
     */
    public void recordRetryAttempt(String component, String operation, String result) {
        Attributes attributes = Attributes.builder()
                .put(ATTR_COMPONENT, component)
                .put(ATTR_OPERATION, operation)
                .put(ATTR_RESULT, result)
                .build();

        retryAttemptsCounter.add(1, attributes);

        LOGGER.debug("Recorded retry attempt: component={}, operation={}, result={}",
                component, operation, result);
    }

    /**
     * Record backoff duration.
     *
     * @param component the component performing the retry
     * @param operation the operation being retried
     * @param backoffMs the backoff duration in milliseconds
     */
    public void recordBackoff(String component, String operation, long backoffMs) {
        double backoffSeconds = backoffMs / 1000.0;

        Attributes attributes = Attributes.builder()
                .put(ATTR_COMPONENT, component)
                .put(ATTR_OPERATION, operation)
                .build();

        backoffDurationHistogram.record(backoffSeconds, attributes);
    }

    /**
     * Get or create a retry sequence for tracking multi-attempt operations.
     *
     * @param component the component name
     * @param operation the operation name
     * @return a unique sequence ID for correlation
     */
    public String getOrCreateSequence(String component, String operation) {
        String key = component + ":" + operation + ":" + Thread.currentThread().getId();
        return activeSequences.computeIfAbsent(key, k -> {
            long id = sequenceCounter.incrementAndGet();
            return new RetrySequence(id, component, operation);
        }).getSequenceId();
    }

    /**
     * Complete a retry sequence.
     *
     * @param component the component name
     * @param operation the operation name
     * @param success whether the sequence ended in success
     * @param totalAttempts the total number of attempts made
     */
    public void completeSequence(String component, String operation, boolean success, int totalAttempts) {
        String key = component + ":" + operation + ":" + Thread.currentThread().getId();
        RetrySequence sequence = activeSequences.remove(key);

        if (sequence != null) {
            String result = success ? RESULT_SUCCESS : RESULT_EXHAUSTED;
            recordRetryAttempt(component, operation, result);

            LOGGER.debug("Completed retry sequence: component={}, operation={}, success={}, attempts={}",
                    component, operation, success, totalAttempts);
        }
    }

    /**
     * Context for tracking a single retry attempt.
     */
    public final class RetryContext implements AutoCloseable {
        private final String component;
        private final String operation;
        private final int attemptNumber;
        private final int totalAttempts;
        private final Span span;
        private final Scope scope;
        private boolean closed = false;

        private RetryContext(String component, String operation,
                            int attemptNumber, int totalAttempts, Span span) {
            this.component = component;
            this.operation = operation;
            this.attemptNumber = attemptNumber;
            this.totalAttempts = totalAttempts;
            this.span = span;
            this.scope = span.makeCurrent();
        }

        /**
         * Record a successful retry attempt.
         */
        public void recordSuccess() {
            if (closed) {
                LOGGER.warn("Attempted to record success on already closed RetryContext");
                return;
            }

            Attributes attributes = Attributes.builder()
                    .put(ATTR_COMPONENT, component)
                    .put(ATTR_OPERATION, operation)
                    .put(ATTR_RESULT, RESULT_SUCCESS)
                    .build();

            retryAttemptsCounter.add(1, attributes);

            span.setStatus(StatusCode.OK);
            span.setAttribute(ATTR_RESULT, RESULT_SUCCESS);
            span.addEvent("retry.success");

            LOGGER.debug("Retry attempt succeeded: component={}, operation={}, attempt={}/{}",
                    component, operation, attemptNumber, totalAttempts);

            close();
        }

        /**
         * Record a failed retry attempt.
         *
         * @param error the exception that caused the failure
         */
        public void recordFailure(Throwable error) {
            if (closed) {
                LOGGER.warn("Attempted to record failure on already closed RetryContext");
                return;
            }

            String result = (attemptNumber >= totalAttempts) ? RESULT_EXHAUSTED : RESULT_FAILURE;

            Attributes attributes = Attributes.builder()
                    .put(ATTR_COMPONENT, component)
                    .put(ATTR_OPERATION, operation)
                    .put(ATTR_RESULT, result)
                    .build();

            retryAttemptsCounter.add(1, attributes);

            span.setStatus(StatusCode.ERROR, error.getMessage());
            span.setAttribute(ATTR_RESULT, result);
            span.setAttribute(ATTR_ERROR_TYPE, error.getClass().getSimpleName());
            span.setAttribute(ATTR_ERROR_MESSAGE,
                    error.getMessage() != null ? error.getMessage() : "unknown");
            span.recordException(error);
            span.addEvent("retry.failure");

            LOGGER.debug("Retry attempt failed: component={}, operation={}, attempt={}/{}, error={}",
                    component, operation, attemptNumber, totalAttempts, error.getMessage());

            close();
        }

        /**
         * Get the underlying span for adding custom attributes.
         *
         * @return the OpenTelemetry span
         */
        public Span getSpan() {
            return span;
        }

        /**
         * Get the attempt number.
         *
         * @return the current attempt number (1-based)
         */
        public int getAttemptNumber() {
            return attemptNumber;
        }

        /**
         * Get the total attempts.
         *
         * @return the total number of attempts
         */
        public int getTotalAttempts() {
            return totalAttempts;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                scope.close();
                span.end();
            }
        }
    }

    /**
     * Represents an active retry sequence for correlation.
     */
    private static final class RetrySequence {
        private final long id;
        private final String component;
        private final String operation;
        private final long startTimeMs;

        private RetrySequence(long id, String component, String operation) {
            this.id = id;
            this.component = component;
            this.operation = operation;
            this.startTimeMs = System.currentTimeMillis();
        }

        private String getSequenceId() {
            return component + "-" + operation + "-" + id;
        }
    }
}
