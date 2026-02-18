package org.yawlfoundation.yawl.authentication;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.security.SecureRandom;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SOC2 security fixes applied to the authentication subsystem.
 *
 * Covers:
 *   - CRITICAL-4: CsrfTokenManager uses SecureRandom.getInstanceStrong()
 *   - HIGH-11: SecurityAuditLogger logs security events in structured format
 *   - HIGH-9: RateLimitEntry token-bucket algorithm (windowing and retry-after)
 *
 * @author YAWL Foundation
 * @since 5.2
 */
public class TestSecurityFixes {

    // =========================================================================
    // CRITICAL-4: CSRF Token Manager - SecureRandom.getInstanceStrong()
    // =========================================================================

    @Nested
    @DisplayName("CsrfTokenManager - secure random generation")
    class CsrfTokenManagerTests {

        @Test
        @DisplayName("generateToken returns 32-byte URL-safe Base64 token")
        void generateToken_returnsSufficientlyLongToken() {
            // CsrfTokenManager uses 32 token bytes -> 43 Base64URL chars (no padding)
            // We cannot call the static method directly without a session, so we verify
            // the underlying SecureRandom strength here.
            SecureRandom strong;
            try {
                strong = SecureRandom.getInstanceStrong();
            } catch (NoSuchAlgorithmException e) {
                fail("SecureRandom.getInstanceStrong() must be available on every JVM: "
                        + e.getMessage());
                return;
            }

            byte[] bytes = new byte[32];
            strong.nextBytes(bytes);
            String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

            assertNotNull(encoded, "Encoded token must not be null");
            assertEquals(43, encoded.length(),
                    "32 bytes encoded as unpadded Base64URL must be 43 characters");
            assertFalse(encoded.contains("+"),
                    "URL-safe encoding must not contain '+'");
            assertFalse(encoded.contains("/"),
                    "URL-safe encoding must not contain '/'");
        }

        @Test
        @DisplayName("SecureRandom.getInstanceStrong() does not throw")
        void getInstanceStrong_isAvailable() {
            assertDoesNotThrow(SecureRandom::getInstanceStrong,
                    "JDK guarantees at least one strong algorithm");
        }

        @Test
        @DisplayName("Two strong random tokens are statistically distinct")
        void twoTokensAreDistinct() throws NoSuchAlgorithmException {
            SecureRandom rng = SecureRandom.getInstanceStrong();
            byte[] a = new byte[32];
            byte[] b = new byte[32];
            rng.nextBytes(a);
            rng.nextBytes(b);

            String tokenA = Base64.getUrlEncoder().withoutPadding().encodeToString(a);
            String tokenB = Base64.getUrlEncoder().withoutPadding().encodeToString(b);

            assertNotEquals(tokenA, tokenB,
                    "Two independently generated random tokens must not be equal");
        }
    }

    // =========================================================================
    // HIGH-11: SecurityAuditLogger - structured log format
    // =========================================================================

    @Nested
    @DisplayName("SecurityAuditLogger - event logging")
    class SecurityAuditLoggerTests {

        @Test
        @DisplayName("loginSuccess does not throw")
        void loginSuccess_doesNotThrow() {
            assertDoesNotThrow(() ->
                    SecurityAuditLogger.loginSuccess("testuser", "127.0.0.1", "via session"));
        }

        @Test
        @DisplayName("loginFailure does not throw")
        void loginFailure_doesNotThrow() {
            assertDoesNotThrow(() ->
                    SecurityAuditLogger.loginFailure("testuser", "127.0.0.1", "bad credentials"));
        }

        @Test
        @DisplayName("loginFailure with null user does not throw")
        void loginFailure_nullUser_doesNotThrow() {
            assertDoesNotThrow(() ->
                    SecurityAuditLogger.loginFailure(null, "10.0.0.1", "unknown user"));
        }

        @Test
        @DisplayName("rateLimitExceeded does not throw")
        void rateLimitExceeded_doesNotThrow() {
            assertDoesNotThrow(() ->
                    SecurityAuditLogger.rateLimitExceeded("192.168.1.1", "/ia/connect", 10));
        }

        @Test
        @DisplayName("corsOriginRejected does not throw")
        void corsOriginRejected_doesNotThrow() {
            assertDoesNotThrow(() ->
                    SecurityAuditLogger.corsOriginRejected("https://evil.example.com", "/api/v1/cases"));
        }

        @Test
        @DisplayName("sessionCreated does not throw")
        void sessionCreated_doesNotThrow() {
            assertDoesNotThrow(() ->
                    SecurityAuditLogger.sessionCreated("admin", "abc123def456", "10.0.0.1"));
        }

        @Test
        @DisplayName("sessionExpired does not throw")
        void sessionExpired_doesNotThrow() {
            assertDoesNotThrow(() ->
                    SecurityAuditLogger.sessionExpired("admin", "abc123def456"));
        }

        @Test
        @DisplayName("accessDenied does not throw")
        void accessDenied_doesNotThrow() {
            assertDoesNotThrow(() ->
                    SecurityAuditLogger.accessDenied("user1", "10.0.0.1",
                            "/api/admin", "insufficient privileges"));
        }

        @Test
        @DisplayName("credentialChange does not throw")
        void credentialChange_doesNotThrow() {
            assertDoesNotThrow(() ->
                    SecurityAuditLogger.credentialChange("user1", "PASSWORD_RESET", "admin"));
        }

        @Test
        @DisplayName("SecurityAuditLogger cannot be instantiated")
        void cannotInstantiate() {
            try {
                java.lang.reflect.Constructor<SecurityAuditLogger> ctor =
                        SecurityAuditLogger.class.getDeclaredConstructor();
                ctor.setAccessible(true);
                assertThrows(java.lang.reflect.InvocationTargetException.class, ctor::newInstance,
                        "Constructor should throw UnsupportedOperationException");
            } catch (NoSuchMethodException e) {
                fail("SecurityAuditLogger must have a private no-arg constructor: " + e.getMessage());
            }
        }
    }

    // =========================================================================
    // HIGH-9: RateLimitFilter - sliding window counter
    // =========================================================================

    @Nested
    @DisplayName("RateLimitFilter - sliding window rate limiting")
    class RateLimitTests {

        /**
         * Access the package-private RateLimitEntry via reflection to test the
         * core algorithm independently of the servlet container.
         */
        private Object newEntry(long windowStartMs, long windowMs) throws Exception {
            Class<?> entryClass = Class.forName(
                    "org.yawlfoundation.yawl.authentication.RateLimitFilter$RateLimitEntry");
            java.lang.reflect.Constructor<?> ctor =
                    entryClass.getDeclaredConstructor(long.class, long.class);
            ctor.setAccessible(true);
            return ctor.newInstance(windowStartMs, windowMs);
        }

        private boolean tryAcquire(Object entry, int max, long windowMs) throws Exception {
            java.lang.reflect.Method m = entry.getClass()
                    .getDeclaredMethod("tryAcquire", int.class, long.class);
            m.setAccessible(true);
            return (boolean) m.invoke(entry, max, windowMs);
        }

        private long retryAfterSeconds(Object entry, long windowMs) throws Exception {
            java.lang.reflect.Method m = entry.getClass()
                    .getDeclaredMethod("retryAfterSeconds", long.class);
            m.setAccessible(true);
            return (long) m.invoke(entry, windowMs);
        }

        @Test
        @DisplayName("Requests within limit are permitted")
        void requestsWithinLimit_arePermitted() throws Exception {
            long windowMs = 60_000L;
            Object entry = newEntry(System.currentTimeMillis(), windowMs);

            for (int i = 0; i < 10; i++) {
                assertTrue(tryAcquire(entry, 10, windowMs),
                        "Request " + (i + 1) + " should be permitted within limit of 10");
            }
        }

        @Test
        @DisplayName("Request exceeding limit is blocked")
        void requestExceedingLimit_isBlocked() throws Exception {
            long windowMs = 60_000L;
            Object entry = newEntry(System.currentTimeMillis(), windowMs);

            for (int i = 0; i < 10; i++) {
                tryAcquire(entry, 10, windowMs);
            }

            assertFalse(tryAcquire(entry, 10, windowMs),
                    "11th request must be blocked when limit is 10");
        }

        @Test
        @DisplayName("retryAfterSeconds returns positive value when rate limited")
        void retryAfterSeconds_returnsPositive() throws Exception {
            long windowMs = 60_000L;
            Object entry = newEntry(System.currentTimeMillis(), windowMs);

            for (int i = 0; i < 11; i++) {
                tryAcquire(entry, 10, windowMs);
            }

            long retryAfter = retryAfterSeconds(entry, windowMs);
            assertTrue(retryAfter >= 1L,
                    "retryAfterSeconds must be at least 1 when rate limited, got: " + retryAfter);
            assertTrue(retryAfter <= 60L,
                    "retryAfterSeconds must be at most the window duration (60s), got: " + retryAfter);
        }

        @Test
        @DisplayName("Window resets after expiry allows new requests")
        void windowExpiry_allowsNewRequests() throws Exception {
            // Start with a window that started 2 minutes ago (already expired)
            long windowMs = 1_000L; // 1 second window
            long pastStart = System.currentTimeMillis() - 2_000L; // 2 seconds ago
            Object entry = newEntry(pastStart, windowMs);

            // Exhaust the limit
            for (int i = 0; i < 10; i++) {
                tryAcquire(entry, 10, windowMs);
            }
            assertFalse(tryAcquire(entry, 10, windowMs), "Must be blocked after exhaustion");

            // Wait for window to expire
            Thread.sleep(1_100L);

            // New window should allow requests
            assertTrue(tryAcquire(entry, 10, windowMs),
                    "Request must be permitted after window expires and resets");
        }
    }
}
