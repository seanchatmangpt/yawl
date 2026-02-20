package org.yawlfoundation.yawl.mcp.a2a.service;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for MCP client circuit breaker patterns.
 *
 * <p>Binds to {@code yawl.mcp.resilience.*} properties in application.yml.
 * Provides type-safe configuration for circuit breaker, retry, timeout limiter, and fallback behaviors.</p>
 *
 * <h2>Configuration Example</h2>
 * <pre>{@code
 * yawl:
 *   mcp:
 *     resilience:
 *       circuit-breaker:
 *         enabled: true
 *         failure-rate-threshold: 50
 *         slow-call-rate-threshold: 50
 *         wait-duration-open-state: 30s
 *         sliding-window-size: 100
 *         minimum-number-of-calls: 10
 *       retry:
 *         enabled: true
 *         max-attempts: 3
 *         wait-duration: 500ms
 *         exponential-backoff-multiplier: 2.0
 *         jitter-factor: 0.5
 *       timeout:
 *         enabled: true
 *         timeout-seconds: 30
 *       fallback:
 *         enabled: true
 *         cache-ttl-seconds: 60
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@Validated
@ConfigurationProperties(prefix = "yawl.mcp.resilience")
public record CircuitBreakerProperties(

    /**
     * Enable resilience patterns for MCP client.
     * When enabled, the MCP client wrapper applies circuit breaker,
     * retry with jitter, timeout limiter, and fallback strategies.
     *
     * @return true if resilience patterns are enabled
     */
    boolean enabled,

    /**
     * Circuit breaker configuration.
     */
    CircuitBreakerConfig circuitBreaker,

    /**
     * Retry configuration.
     */
    RetryConfig retry,

    /**
     * Timeout limiter configuration.
     */
    TimeoutConfig timeout,

    /**
     * Fallback configuration.
     */
    FallbackConfig fallback

) {

    /**
     * Default configuration values.
     */
    public static final int DEFAULT_FAILURE_RATE_THRESHOLD = 50;
    public static final int DEFAULT_SLOW_CALL_RATE_THRESHOLD = 50;
    public static final int DEFAULT_WAIT_DURATION_SECONDS = 30;
    public static final int DEFAULT_SLIDING_WINDOW_SIZE = 100;
    public static final int DEFAULT_MINIMUM_CALLS = 10;
    public static final int DEFAULT_PERMITTED_CALLS_HALF_OPEN = 5;
    public static final int DEFAULT_MAX_RETRY_ATTEMPTS = 3;
    public static final long DEFAULT_WAIT_DURATION_MS = 500L;
    public static final double DEFAULT_BACKOFF_MULTIPLIER = 2.0;
    public static final double DEFAULT_JITTER_FACTOR = 0.5;
    public static final int DEFAULT_CACHE_TTL_SECONDS = 60;
    public static final int DEFAULT_SLOW_CALL_DURATION_SECONDS = 10;

    /**
     * Creates default configuration with resilience enabled.
     *
     * @return default CircuitBreakerProperties instance
     */
    public static CircuitBreakerProperties defaults() {
        return new CircuitBreakerProperties(
            true,
            CircuitBreakerConfig.defaults(),
            RetryConfig.defaults(),
            TimeoutConfig.defaults(),
            FallbackConfig.defaults()
        );
    }

    /**
     * Creates configuration with resilience disabled.
     *
     * @return disabled CircuitBreakerProperties instance
     */
    public static CircuitBreakerProperties disabled() {
        return new CircuitBreakerProperties(
            false,
            CircuitBreakerConfig.defaults(),
            RetryConfig.defaults(),
            TimeoutConfig.defaults(),
            FallbackConfig.disabled()
        );
    }

    /**
     * Circuit breaker specific configuration.
     */
    public record CircuitBreakerConfig(
        /**
         * Enable circuit breaker pattern.
         *
         * @return true if circuit breaker is enabled
         */
        boolean enabled,

        /**
         * Failure rate threshold percentage that triggers circuit open.
         * When the failure rate exceeds this value, the circuit transitions to OPEN.
         * Range: 1-100. Default: 50%
         *
         * @return failure rate threshold percentage
         */
        @Min(1) @Max(100)
        int failureRateThreshold,

        /**
         * Slow call rate threshold percentage.
         * When the slow call rate exceeds this value, the circuit transitions to OPEN.
         * Range: 1-100. Default: 50%
         *
         * @return slow call rate threshold percentage
         */
        @Min(1) @Max(100)
        int slowCallRateThreshold,

        /**
         * Duration threshold for slow calls in seconds.
         * Calls exceeding this duration are considered slow.
         * Default: 10 seconds
         *
         * @return slow call duration threshold in seconds
         */
        @Min(1) @Max(300)
        int slowCallDurationSeconds,

        /**
         * Duration the circuit stays open before transitioning to half-open.
         * Default: 30 seconds
         *
         * @return wait duration in open state in seconds
         */
        @Min(1) @Max(600)
        int waitDurationOpenStateSeconds,

        /**
         * Size of the sliding window for failure rate calculation.
         * Default: 100 calls
         *
         * @return sliding window size
         */
        @Min(10) @Max(1000)
        int slidingWindowSize,

        /**
         * Minimum number of calls before circuit breaker evaluates failure rate.
         * Default: 10 calls
         *
         * @return minimum number of calls
         */
        @Min(5) @Max(100)
        int minimumNumberOfCalls,

        /**
         * Number of permitted calls in half-open state.
         * Default: 5 calls
         *
         * @return permitted number of calls in half-open state
         */
        @Min(1) @Max(50)
        int permittedNumberOfCallsInHalfOpenState,

        /**
         * Enable automatic transition from open to half-open state.
         * Default: true
         *
         * @return true if automatic transition is enabled
         */
        boolean automaticTransitionFromOpenToHalfOpen

    ) {
        /**
         * Creates default circuit breaker configuration.
         *
         * @return default CircuitBreakerConfig instance
         */
        public static CircuitBreakerConfig defaults() {
            return new CircuitBreakerConfig(
                true,
                DEFAULT_FAILURE_RATE_THRESHOLD,
                DEFAULT_SLOW_CALL_RATE_THRESHOLD,
                DEFAULT_SLOW_CALL_DURATION_SECONDS,
                DEFAULT_WAIT_DURATION_SECONDS,
                DEFAULT_SLIDING_WINDOW_SIZE,
                DEFAULT_MINIMUM_CALLS,
                DEFAULT_PERMITTED_CALLS_HALF_OPEN,
                true
            );
        }
    }

    /**
     * Retry specific configuration.
     */
    public record RetryConfig(
        /**
         * Enable retry pattern.
         *
         * @return true if retry is enabled
         */
        boolean enabled,

        /**
         * Maximum number of retry attempts.
         * Default: 3 attempts
         *
         * @return maximum retry attempts
         */
        @Min(1) @Max(10)
        int maxAttempts,

        /**
         * Base wait duration between retry attempts in milliseconds.
         * Default: 500ms
         *
         * @return base wait duration in milliseconds
         */
        @Min(50) @Max(30000)
        long waitDurationMs,

        /**
         * Exponential backoff multiplier.
         * Each retry waits: waitDuration * (multiplier ^ attemptNumber).
         * Default: 2.0
         *
         * @return exponential backoff multiplier
         */
        double exponentialBackoffMultiplier,

        /**
         * Jitter factor for randomized wait durations.
         * Adds randomness to retry timing to avoid thundering herd.
         * Range: 0.0 (no jitter) to 1.0 (max jitter).
         * Default: 0.5
         *
         * @return jitter factor
         */
        double jitterFactor

    ) {
        /**
         * Creates default retry configuration.
         *
         * @return default RetryConfig instance
         */
        public static RetryConfig defaults() {
            return new RetryConfig(
                true,
                DEFAULT_MAX_RETRY_ATTEMPTS,
                DEFAULT_WAIT_DURATION_MS,
                DEFAULT_BACKOFF_MULTIPLIER,
                DEFAULT_JITTER_FACTOR
            );
        }
    }

    /**
     * Timeout limiter specific configuration.
     */
    public record TimeoutConfig(
        /**
         * Enable timeout limiter pattern.
         * When enabled, all MCP operations are bounded by an execution time limit.
         * Default: true
         *
         * @return true if timeout limiter is enabled
         */
        boolean enabled,

        /**
         * Timeout duration in seconds for MCP client operations.
         * Applies to entire operation chain (circuit breaker + retry + execution).
         * Default: 30 seconds
         *
         * @return timeout duration in seconds
         */
        @Min(1) @Max(600)
        int timeoutSeconds

    ) {
        /**
         * Creates default timeout configuration.
         *
         * @return default TimeoutConfig instance
         */
        public static TimeoutConfig defaults() {
            return new TimeoutConfig(true, 30);
        }
    }

    /**
     * Fallback specific configuration.
     */
    public record FallbackConfig(
        /**
         * Enable fallback pattern.
         *
         * @return true if fallback is enabled
         */
        boolean enabled,

        /**
         * Time-to-live for cached fallback responses in seconds.
         * Default: 60 seconds
         *
         * @return cache TTL in seconds
         */
        @Min(0) @Max(3600)
        int cacheTtlSeconds,

        /**
         * Enable stale-while-revalidate pattern.
         * Returns cached data while fetching fresh data in background.
         * Default: true
         *
         * @return true if stale-while-revalidate is enabled
         */
        boolean staleWhileRevalidate,

        /**
         * Fallback response behavior when circuit is open and no cache.
         * Options: "empty", "error", "default".
         * Default: "error"
         *
         * @return fallback response behavior
         */
        @NotBlank
        String fallbackResponseBehavior

    ) {
        /**
         * Creates default fallback configuration.
         *
         * @return default FallbackConfig instance
         */
        public static FallbackConfig defaults() {
            return new FallbackConfig(
                true,
                DEFAULT_CACHE_TTL_SECONDS,
                true,
                "error"
            );
        }

        /**
         * Creates disabled fallback configuration.
         *
         * @return disabled FallbackConfig instance
         */
        public static FallbackConfig disabled() {
            return new FallbackConfig(
                false,
                0,
                false,
                "error"
            );
        }
    }
}
