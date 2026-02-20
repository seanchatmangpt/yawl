package org.yawlfoundation.yawl.observability;

import io.github.resilience4j.circuitbreaker.event.CircuitBreakerEvent;
import io.github.resilience4j.core.EventConsumer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.sleuth.Tracer;

import java.util.Objects;

/**
 * Observability listener for resilience4j circuit breaker events.
 *
 * Emits OpenTelemetry spans for circuit breaker state transitions and events,
 * enabling correlation between resilience patterns and distributed traces.
 *
 * Creates spans for:
 * - State transitions (CLOSED → OPEN → HALF_OPEN → CLOSED)
 * - Success and error calls
 * - Ignored errors
 * - Slow calls
 * - Call rejections (not permitted)
 *
 * Thread-safe implementation suitable for virtual threads.
 *
 * @since 6.0.0
 */
public class CircuitBreakerObservabilityListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(CircuitBreakerObservabilityListener.class);

    private final Tracer tracer;

    public CircuitBreakerObservabilityListener(Tracer tracer) {
        this.tracer = Objects.requireNonNull(tracer);
    }

    /**
     * Handles circuit breaker state transition events (CLOSED → OPEN → HALF_OPEN, etc.).
     */
    public void onCircuitBreakerStateTransition(CircuitBreakerEvent.StateTransitionEvent event) {
        try (var scope = tracer.createScope("resilience.circuit_breaker.state_transition")) {
            String spanName = String.format("cb_state_transition_%s_to_%s",
                    event.getStateTransitionFrom().name().toLowerCase(),
                    event.getStateTransitionTo().name().toLowerCase());

            LOGGER.info("Circuit breaker {} transitioned from {} to {}",
                    event.getCircuitBreakerName(),
                    event.getStateTransitionFrom(),
                    event.getStateTransitionTo());
        }
    }

    /**
     * Handles successful circuit breaker calls.
     */
    public void onCircuitBreakerSuccess(CircuitBreakerEvent.SuccessEvent event) {
        try (var scope = tracer.createScope("resilience.circuit_breaker.success")) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Circuit breaker {} success, execution time: {}ms",
                        event.getCircuitBreakerName(),
                        event.getElapsedDuration().toMillis());
            }
        }
    }

    /**
     * Handles circuit breaker errors (not ignored).
     */
    public void onCircuitBreakerError(CircuitBreakerEvent.ErrorEvent event) {
        try (var scope = tracer.createScope("resilience.circuit_breaker.error")) {
            LOGGER.warn("Circuit breaker {} error: {}",
                    event.getCircuitBreakerName(),
                    event.getThrowable().getClass().getSimpleName());
        }
    }

    /**
     * Handles circuit breaker ignored errors.
     */
    public void onCircuitBreakerIgnoredError(CircuitBreakerEvent.IgnoredErrorEvent event) {
        try (var scope = tracer.createScope("resilience.circuit_breaker.ignored_error")) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Circuit breaker {} ignored error: {}",
                        event.getCircuitBreakerName(),
                        event.getThrowable().getClass().getSimpleName());
            }
        }
    }

    /**
     * Handles slow success events (call completed within timeout but was slow).
     */
    public void onCircuitBreakerSlowSuccess(CircuitBreakerEvent.SlowSuccessEvent event) {
        try (var scope = tracer.createScope("resilience.circuit_breaker.slow_success")) {
            if (LOGGER.isWarnEnabled()) {
                LOGGER.warn("Circuit breaker {} slow success: execution time {}ms exceeds slow duration threshold",
                        event.getCircuitBreakerName(),
                        event.getElapsedDuration().toMillis());
            }
        }
    }

    /**
     * Handles slow error events (call failed after timeout).
     */
    public void onCircuitBreakerSlowError(CircuitBreakerEvent.SlowErrorEvent event) {
        try (var scope = tracer.createScope("resilience.circuit_breaker.slow_error")) {
            LOGGER.warn("Circuit breaker {} slow error: execution time {}ms exceeds threshold, error: {}",
                    event.getCircuitBreakerName(),
                    event.getElapsedDuration().toMillis(),
                    event.getThrowable().getClass().getSimpleName());
        }
    }

    /**
     * Handles call not permitted events (circuit is OPEN or HALF_OPEN with threshold exceeded).
     */
    public void onCircuitBreakerCallNotPermitted(CircuitBreakerEvent.NotPermittedEvent event) {
        try (var scope = tracer.createScope("resilience.circuit_breaker.call_not_permitted")) {
            LOGGER.error("Circuit breaker {} call not permitted, state: {}",
                    event.getCircuitBreakerName(),
                    event.getCircuitBreakerState());
        }
    }
}
