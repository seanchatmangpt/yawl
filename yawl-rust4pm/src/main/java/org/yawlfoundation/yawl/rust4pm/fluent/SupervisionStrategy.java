package org.yawlfoundation.yawl.rust4pm.fluent;

import java.time.Duration;
import java.util.Objects;

/**
 * OTP-inspired supervision strategy for process mining pipelines.
 *
 * <p>Controls how pipeline stage failures are handled:
 * <ul>
 *   <li>{@link #FAIL_FAST} — Abort pipeline on first failure (default)</li>
 *   <li>{@link #RETRY} — Retry failed stage up to {@code maxRetries} times</li>
 *   <li>{@link #SKIP} — Skip failed stage and continue pipeline</li>
 * </ul>
 *
 * <p>Modeled after Erlang/OTP supervisor restart strategies, adapted
 * for sequential pipeline stages rather than concurrent actor trees.
 *
 * @param policy       how to handle stage failures
 * @param maxRetries   maximum retry attempts (only used with RETRY policy)
 * @param retryDelay   delay between retries (only used with RETRY policy)
 */
public record SupervisionStrategy(Policy policy, int maxRetries, Duration retryDelay) {

    /** Abort pipeline on first failure. */
    public static final SupervisionStrategy FAIL_FAST =
        new SupervisionStrategy(Policy.FAIL_FAST, 0, Duration.ZERO);

    /** Retry failed stage up to 3 times with 100ms delay. */
    public static final SupervisionStrategy RETRY_DEFAULT =
        new SupervisionStrategy(Policy.RETRY, 3, Duration.ofMillis(100));

    /** Skip failed stage and continue pipeline. */
    public static final SupervisionStrategy SKIP_ON_FAILURE =
        new SupervisionStrategy(Policy.SKIP, 0, Duration.ZERO);

    public SupervisionStrategy {
        Objects.requireNonNull(policy, "policy must not be null");
        Objects.requireNonNull(retryDelay, "retryDelay must not be null");
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be >= 0, got: " + maxRetries);
        }
    }

    /**
     * Create a RETRY strategy with custom parameters.
     *
     * @param maxRetries maximum retry attempts
     * @param retryDelay delay between retries
     * @return configured retry strategy
     */
    public static SupervisionStrategy retry(int maxRetries, Duration retryDelay) {
        return new SupervisionStrategy(Policy.RETRY, maxRetries, retryDelay);
    }

    /**
     * Supervision policy — how to handle stage failures.
     */
    public enum Policy {
        /** Abort pipeline immediately on failure. */
        FAIL_FAST,
        /** Retry failed stage up to maxRetries times. */
        RETRY,
        /** Skip failed stage and continue with next. */
        SKIP
    }
}
