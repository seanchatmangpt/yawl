package org.yawlfoundation.yawl.observability;

import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

/**
 * Structured logging markers for resilience4j pattern events.
 *
 * SLF4J Markers enable semantic tagging of log entries for aggregation and filtering
 * in ELK/Logstash pipelines. Each marker represents a specific event type and can
 * be used to filter, alert, and correlate logs across distributed systems.
 *
 * Usage in code:
 * ```java
 * logger.warn("Circuit breaker opened", LogMarkers.circuitBreakerOpen("inventory-service"));
 * logger.info("Retry succeeded", LogMarkers.retrySuccess("payment-processor"));
 * ```
 *
 * Logstash can filter on these markers:
 * ```
 * filter {
 *   if "CIRCUIT_BREAKER_OPEN" in [markers] {
 *     mutate { add_tag => ["alert"] }
 *   }
 * }
 * ```
 */
public final class LogMarkers {

    // Circuit Breaker Markers
    private static final Marker CIRCUIT_BREAKER_OPEN = MarkerFactory.getMarker("CIRCUIT_BREAKER_OPEN");
    private static final Marker CIRCUIT_BREAKER_HALF_OPEN = MarkerFactory.getMarker("CIRCUIT_BREAKER_HALF_OPEN");
    private static final Marker CIRCUIT_BREAKER_CLOSED = MarkerFactory.getMarker("CIRCUIT_BREAKER_CLOSED");
    private static final Marker CIRCUIT_BREAKER_ERROR = MarkerFactory.getMarker("CIRCUIT_BREAKER_ERROR");
    private static final Marker CIRCUIT_BREAKER_SUCCESS = MarkerFactory.getMarker("CIRCUIT_BREAKER_SUCCESS");
    private static final Marker CIRCUIT_BREAKER_SLOW_CALL = MarkerFactory.getMarker("CIRCUIT_BREAKER_SLOW_CALL");
    private static final Marker CIRCUIT_BREAKER_IGNORED_ERROR = MarkerFactory.getMarker("CIRCUIT_BREAKER_IGNORED_ERROR");

    // Rate Limiter Markers
    private static final Marker RATE_LIMIT_ALLOWED = MarkerFactory.getMarker("RATE_LIMIT_ALLOWED");
    private static final Marker RATE_LIMIT_EXCEEDED = MarkerFactory.getMarker("RATE_LIMIT_EXCEEDED");

    // Retry Markers
    private static final Marker RETRY_ATTEMPT = MarkerFactory.getMarker("RETRY_ATTEMPT");
    private static final Marker RETRY_SUCCESS = MarkerFactory.getMarker("RETRY_SUCCESS");
    private static final Marker RETRY_EXHAUSTED = MarkerFactory.getMarker("RETRY_EXHAUSTED");
    private static final Marker RETRY_IGNORED_ERROR = MarkerFactory.getMarker("RETRY_IGNORED_ERROR");

    /**
     * Marker for circuit breaker open state transitions.
     * Indicates that the circuit breaker has transitioned to OPEN state,
     * preventing calls from reaching the protected service.
     */
    public static Marker circuitBreakerOpen(String breaker) {
        Marker m = MarkerFactory.getMarker("CIRCUIT_BREAKER_OPEN:" + breaker);
        m.add(CIRCUIT_BREAKER_OPEN);
        return m;
    }

    /**
     * Marker for circuit breaker half-open state transitions.
     * Indicates that the circuit breaker is attempting recovery by allowing
     * a limited number of test calls.
     */
    public static Marker circuitBreakerHalfOpen(String breaker) {
        Marker m = MarkerFactory.getMarker("CIRCUIT_BREAKER_HALF_OPEN:" + breaker);
        m.add(CIRCUIT_BREAKER_HALF_OPEN);
        return m;
    }

    /**
     * Marker for circuit breaker closed state transitions.
     * Indicates that the circuit breaker has transitioned to CLOSED state,
     * allowing normal operation to resume.
     */
    public static Marker circuitBreakerClosed(String breaker) {
        Marker m = MarkerFactory.getMarker("CIRCUIT_BREAKER_CLOSED:" + breaker);
        m.add(CIRCUIT_BREAKER_CLOSED);
        return m;
    }

    /**
     * Marker for circuit breaker state changes (generic).
     * Used for tracking any state transition event.
     */
    public static Marker circuitBreakerStateChange(String breaker) {
        return MarkerFactory.getMarker("CIRCUIT_BREAKER_STATE_CHANGE:" + breaker);
    }

    /**
     * Marker for circuit breaker errors.
     * Indicates that a call through the circuit breaker failed.
     */
    public static Marker circuitBreakerError(String breaker) {
        Marker m = MarkerFactory.getMarker("CIRCUIT_BREAKER_ERROR:" + breaker);
        m.add(CIRCUIT_BREAKER_ERROR);
        return m;
    }

    /**
     * Marker for circuit breaker successful calls.
     * Indicates that a call through the circuit breaker succeeded.
     */
    public static Marker circuitBreakerSuccess(String breaker) {
        Marker m = MarkerFactory.getMarker("CIRCUIT_BREAKER_SUCCESS:" + breaker);
        m.add(CIRCUIT_BREAKER_SUCCESS);
        return m;
    }

    /**
     * Marker for circuit breaker slow calls.
     * Indicates that a call exceeded the configured slowness threshold.
     */
    public static Marker circuitBreakerSlowCall(String breaker) {
        Marker m = MarkerFactory.getMarker("CIRCUIT_BREAKER_SLOW_CALL:" + breaker);
        m.add(CIRCUIT_BREAKER_SLOW_CALL);
        return m;
    }

    /**
     * Marker for circuit breaker ignored errors.
     * Indicates that the circuit breaker ignored a specific exception type.
     */
    public static Marker circuitBreakerIgnoredError(String breaker) {
        Marker m = MarkerFactory.getMarker("CIRCUIT_BREAKER_IGNORED_ERROR:" + breaker);
        m.add(CIRCUIT_BREAKER_IGNORED_ERROR);
        return m;
    }

    /**
     * Marker for rate limiter allowing requests.
     * Indicates that the rate limiter permitted a request to proceed.
     */
    public static Marker rateLimiterAllowed(String limiter) {
        Marker m = MarkerFactory.getMarker("RATE_LIMIT_ALLOWED:" + limiter);
        m.add(RATE_LIMIT_ALLOWED);
        return m;
    }

    /**
     * Marker for rate limiter rejecting requests.
     * Indicates that the rate limiter rejected a request due to limit being exceeded.
     */
    public static Marker rateLimiterExceeded(String limiter) {
        Marker m = MarkerFactory.getMarker("RATE_LIMIT_EXCEEDED:" + limiter);
        m.add(RATE_LIMIT_EXCEEDED);
        return m;
    }

    /**
     * Marker for retry attempts.
     * Indicates that a retry was initiated for a failed operation.
     */
    public static Marker retryAttempt(String retry) {
        Marker m = MarkerFactory.getMarker("RETRY_ATTEMPT:" + retry);
        m.add(RETRY_ATTEMPT);
        return m;
    }

    /**
     * Marker for successful retries.
     * Indicates that a retried operation ultimately succeeded.
     */
    public static Marker retrySuccess(String retry) {
        Marker m = MarkerFactory.getMarker("RETRY_SUCCESS:" + retry);
        m.add(RETRY_SUCCESS);
        return m;
    }

    /**
     * Marker for exhausted retries.
     * Indicates that all retry attempts were exhausted and the operation finally failed.
     */
    public static Marker retryExhausted(String retry) {
        Marker m = MarkerFactory.getMarker("RETRY_EXHAUSTED:" + retry);
        m.add(RETRY_EXHAUSTED);
        return m;
    }

    /**
     * Marker for retry ignored errors.
     * Indicates that the retry mechanism ignored a specific exception type.
     */
    public static Marker retryIgnoredError(String retry) {
        Marker m = MarkerFactory.getMarker("RETRY_IGNORED_ERROR:" + retry);
        m.add(RETRY_IGNORED_ERROR);
        return m;
    }

    /**
     * Creates a custom marker with a single parent marker.
     * Useful for creating domain-specific markers that inherit classification.
     */
    public static Marker withParent(String markerName, Marker parent) {
        Marker m = MarkerFactory.getMarker(markerName);
        m.add(parent);
        return m;
    }

    private LogMarkers() {
        // Utility class - no instantiation
    }

}
