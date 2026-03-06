package org.yawlfoundation.yawl.rust4pm.fluent;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SupervisionStrategy} — OTP-inspired supervision policies.
 */
class SupervisionStrategyTest {

    @Test
    void failFastHasZeroRetriesAndZeroDelay() {
        SupervisionStrategy s = SupervisionStrategy.FAIL_FAST;
        assertEquals(SupervisionStrategy.Policy.FAIL_FAST, s.policy());
        assertEquals(0, s.maxRetries());
        assertEquals(Duration.ZERO, s.retryDelay());
    }

    @Test
    void retryDefaultHasThreeRetriesAnd100msDelay() {
        SupervisionStrategy s = SupervisionStrategy.RETRY_DEFAULT;
        assertEquals(SupervisionStrategy.Policy.RETRY, s.policy());
        assertEquals(3, s.maxRetries());
        assertEquals(Duration.ofMillis(100), s.retryDelay());
    }

    @Test
    void skipOnFailureHasZeroRetries() {
        SupervisionStrategy s = SupervisionStrategy.SKIP_ON_FAILURE;
        assertEquals(SupervisionStrategy.Policy.SKIP, s.policy());
        assertEquals(0, s.maxRetries());
    }

    @Test
    void customRetryStrategyUsesGivenValues() {
        SupervisionStrategy s = SupervisionStrategy.retry(5, Duration.ofSeconds(2));
        assertEquals(SupervisionStrategy.Policy.RETRY, s.policy());
        assertEquals(5, s.maxRetries());
        assertEquals(Duration.ofSeconds(2), s.retryDelay());
    }

    @Test
    void negativeRetriesThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new SupervisionStrategy(SupervisionStrategy.Policy.RETRY, -1, Duration.ZERO));
    }

    @Test
    void nullPolicyThrows() {
        assertThrows(NullPointerException.class,
            () -> new SupervisionStrategy(null, 0, Duration.ZERO));
    }

    @Test
    void nullRetryDelayThrows() {
        assertThrows(NullPointerException.class,
            () -> new SupervisionStrategy(SupervisionStrategy.Policy.RETRY, 3, null));
    }

    @Test
    void recordEqualityByValue() {
        SupervisionStrategy a = SupervisionStrategy.retry(3, Duration.ofMillis(100));
        SupervisionStrategy b = SupervisionStrategy.retry(3, Duration.ofMillis(100));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
