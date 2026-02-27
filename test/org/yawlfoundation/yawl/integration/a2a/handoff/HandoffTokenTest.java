/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.a2a.handoff;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.time.Instant;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for HandoffToken record.
 *
 * Tests all aspects of the handoff token including validation,
 * expiration handling, and immutability guarantees.
 *
 * Coverage targets:
 * - Record constructor validation
 * - Field validation (null, blank, edge cases)
 * - isValid() method with various scenarios
 * - timeToExpiry() method accuracy
 * - withExpiresAt() method functionality
 */
class HandoffTokenTest {

    private static final String TEST_WORK_ITEM_ID = "WI-42";
    private static final String TEST_FROM_AGENT = "source-agent";
    private static final String TEST_TO_AGENT = "target-agent";
    private static final String TEST_ENGINE_SESSION = "session-handle-123";
    private static final String TEST_JWT = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJoYW5kb2ZmIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";

    private Instant testExpiresAt;
    private HandoffToken validToken;

    @BeforeEach
    void setUp() {
        // Set expiration to 5 minutes from now
        testExpiresAt = Instant.now().plus(Duration.ofMinutes(5));
        validToken = new HandoffToken(
            TEST_WORK_ITEM_ID,
            TEST_FROM_AGENT,
            TEST_TO_AGENT,
            TEST_ENGINE_SESSION,
            testExpiresAt,
            TEST_JWT
        );
    }

    @Nested
    @DisplayName("Constructor Validation")
    class ConstructorValidationTests {

        @Test
        @DisplayName("Create valid token with all parameters")
        void createValidToken() {
            // When creating a token with valid parameters
            HandoffToken token = new HandoffToken(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                TEST_ENGINE_SESSION,
                testExpiresAt,
                TEST_JWT
            );

            // Then all fields should be set correctly
            assertEquals(TEST_WORK_ITEM_ID, token.workItemId());
            assertEquals(TEST_FROM_AGENT, token.fromAgent());
            assertEquals(TEST_TO_AGENT, token.toAgent());
            assertEquals(TEST_ENGINE_SESSION, token.engineSession());
            assertEquals(testExpiresAt, token.expiresAt());
            assertEquals(TEST_JWT, token.getJwt());
        }

        @Test
        @DisplayName("Reject null workItemId")
        void rejectNullWorkItemId() {
            // When creating token with null workItemId
            assertThrows(IllegalArgumentException.class, () -> {
                new HandoffToken(null, TEST_FROM_AGENT, TEST_TO_AGENT, TEST_ENGINE_SESSION, testExpiresAt, TEST_JWT);
            }, "Should reject null workItemId");
        }

        @Test
        @DisplayName("Reject blank workItemId")
        void rejectBlankWorkItemId() {
            // When creating token with blank workItemId
            assertThrows(IllegalArgumentException.class, () -> {
                new HandoffToken("", TEST_FROM_AGENT, TEST_TO_AGENT, TEST_ENGINE_SESSION, testExpiresAt, TEST_JWT);
            }, "Should reject blank workItemId");
        }

        @Test
        @DisplayName("Reject null fromAgent")
        void rejectNullFromAgent() {
            assertThrows(IllegalArgumentException.class, () -> {
                new HandoffToken(TEST_WORK_ITEM_ID, null, TEST_TO_AGENT, TEST_ENGINE_SESSION, testExpiresAt, TEST_JWT);
            }, "Should reject null fromAgent");
        }

        @Test
        @DisplayName("Reject blank fromAgent")
        void rejectBlankFromAgent() {
            assertThrows(IllegalArgumentException.class, () -> {
                new HandoffToken(TEST_WORK_ITEM_ID, "", TEST_TO_AGENT, TEST_ENGINE_SESSION, testExpiresAt, TEST_JWT);
            }, "Should reject blank fromAgent");
        }

        @Test
        @DisplayName("Reject null toAgent")
        void rejectNullToAgent() {
            assertThrows(IllegalArgumentException.class, () -> {
                new HandoffToken(TEST_WORK_ITEM_ID, TEST_FROM_AGENT, null, TEST_ENGINE_SESSION, testExpiresAt, TEST_JWT);
            }, "Should reject null toAgent");
        }

        @Test
        @DisplayName("Reject blank toAgent")
        void rejectBlankToAgent() {
            assertThrows(IllegalArgumentException.class, () -> {
                new HandoffToken(TEST_WORK_ITEM_ID, TEST_FROM_AGENT, "", TEST_ENGINE_SESSION, testExpiresAt, TEST_JWT);
            }, "Should reject blank toAgent");
        }

        @Test
        @DisplayName("Reject null engineSession")
        void rejectNullEngineSession() {
            assertThrows(IllegalArgumentException.class, () -> {
                new HandoffToken(TEST_WORK_ITEM_ID, TEST_FROM_AGENT, TEST_TO_AGENT, null, testExpiresAt, TEST_JWT);
            }, "Should reject null engineSession");
        }

        @Test
        @DisplayName("Reject blank engineSession")
        void rejectBlankEngineSession() {
            assertThrows(IllegalArgumentException.class, () -> {
                new HandoffToken(TEST_WORK_ITEM_ID, TEST_FROM_AGENT, TEST_TO_AGENT, "", testExpiresAt, TEST_JWT);
            }, "Should reject blank engineSession");
        }

        @Test
        @DisplayName("Reject null expiresAt")
        void rejectNullExpiresAt() {
            assertThrows(IllegalArgumentException.class, () -> {
                new HandoffToken(TEST_WORK_ITEM_ID, TEST_FROM_AGENT, TEST_TO_AGENT, TEST_ENGINE_SESSION, null, TEST_JWT);
            }, "Should reject null expiresAt");
        }

        @Test
        @DisplayName("Reject null JWT")
        void rejectNullJWT() {
            assertThrows(IllegalArgumentException.class, () -> {
                new HandoffToken(TEST_WORK_ITEM_ID, TEST_FROM_AGENT, TEST_TO_AGENT, TEST_ENGINE_SESSION, testExpiresAt, null);
            }, "Should reject null JWT");
        }

        @Test
        @DisplayName("Reject blank JWT")
        void rejectBlankJWT() {
            assertThrows(IllegalArgumentException.class, () -> {
                new HandoffToken(TEST_WORK_ITEM_ID, TEST_FROM_AGENT, TEST_TO_AGENT, TEST_ENGINE_SESSION, testExpiresAt, "");
            }, "Should reject blank JWT");
        }
    }

    @Nested
    @DisplayName("Token Validity")
    class TokenValidityTests {

        @Test
        @DisplayName("Valid token returns true for isValid()")
        void validTokenReturnsTrue() {
            // Given a valid token (not expired)
            // Then isValid() should return true
            assertTrue(validToken.isValid(), "Valid token should return true for isValid()");
        }

        @Test
        @DisplayName("Expired token returns false for isValid()")
        void expiredTokenReturnsFalse() throws InterruptedException {
            // Given an expired token
            Instant past = Instant.now().minusSeconds(1);
            HandoffToken expiredToken = new HandoffToken(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                TEST_ENGINE_SESSION,
                past,
                TEST_JWT
            );

            // Then isValid() should return false
            assertFalse(expiredToken.isValid(), "Expired token should return false for isValid()");
        }

        @Test
        @DisplayName("Token expiring immediately is still valid")
        void tokenExpiringImmediatelyIsValid() {
            // Given a token expiring now
            Instant now = Instant.now();
            HandoffToken tokenExpiringNow = new HandoffToken(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                TEST_ENGINE_SESSION,
                now,
                TEST_JWT
            );

            // Then isValid() should return false (expires at now, not after now)
            assertFalse(tokenExpiringNow.isValid(), "Token expiring now should be invalid");
        }

        @Test
        @DisplayName("Token expiring in future is valid")
        void tokenExpiringInFutureIsValid() {
            // Given a token expiring in 1 second
            Instant future = Instant.now().plusSeconds(1);
            HandoffToken futureToken = new HandoffToken(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                TEST_ENGINE_SESSION,
                future,
                TEST_JWT
            );

            // Then isValid() should return true
            assertTrue(futureToken.isValid(), "Token expiring in future should be valid");
        }
    }

    @Nested
    @DisplayName("Time to Expiry")
    class TimeToExpiryTests {

        @Test
        @DisplayName("Valid token returns positive duration")
        void validTokenReturnsPositiveDuration() {
            // Given a valid token
            Duration timeToExpiry = validToken.timeToExpiry();

            // Then timeToExpiry should be positive
            assertTrue(timeToExpiry.isPositive(), "Time to expiry should be positive for valid token");
            assertTrue(timeToExpiry.toSeconds() > 0, "Time to expiry should be greater than 0 seconds");
        }

        @Test
        @DisplayName("Expired token returns zero duration")
        void expiredTokenReturnsZeroDuration() {
            // Given an expired token
            Instant past = Instant.now().minusSeconds(1);
            HandoffToken expiredToken = new HandoffToken(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                TEST_ENGINE_SESSION,
                past,
                TEST_JWT
            );

            // Then timeToExpiry should be zero or negative
            Duration timeToExpiry = expiredToken.timeToExpiry();
            assertTrue(timeToExpiry.isNegative() || timeToExpiry.isZero(),
                "Time to expiry should be zero or negative for expired token");
        }

        @Test
        @DisplayName("Token expiring soon returns small duration")
        void tokenExpiringSoonReturnsSmallDuration() {
            // Given a token expiring in 100ms
            Instant soon = Instant.now().plusMillis(100);
            HandoffToken soonToken = new HandoffToken(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                TEST_ENGINE_SESSION,
                soon,
                TEST_JWT
            );

            // Then timeToExpiry should be less than 200ms
            Duration timeToExpiry = soonToken.timeToExpiry();
            assertTrue(timeToExpiry.toMillis() < 200, "Time to expiry should be less than 200ms");
        }
    }

    @Nested
    @DisplayName("Immutable Operations")
    class ImmutableOperationsTests {

        @Test
        @DisplayName("withExpiresAt returns new token with updated expiration")
        void withExpiresAtReturnsNewToken() {
            // Given a token with original expiration
            Instant newExpiresAt = Instant.now().plus(Duration.ofHours(1));

            // When updating expiration
            HandoffToken updatedToken = validToken.withExpiresAt(newExpiresAt);

            // Then the original token should be unchanged
            assertEquals(testExpiresAt, validToken.expiresAt(), "Original token should be unchanged");

            // And the new token should have updated expiration
            assertEquals(newExpiresAt, updatedToken.expiresAt(), "Updated token should have new expiration");

            // And other fields should be the same
            assertEquals(TEST_WORK_ITEM_ID, updatedToken.workItemId());
            assertEquals(TEST_FROM_AGENT, updatedToken.fromAgent());
            assertEquals(TEST_TO_AGENT, updatedToken.toAgent());
            assertEquals(TEST_ENGINE_SESSION, updatedToken.engineSession());
            assertEquals(TEST_JWT, updatedToken.getJwt());
        }

        @Test
        @DisplayName("withExpiresAt preserves JWT")
        void withExpiresAtPreservesJWT() {
            // When updating expiration
            HandoffToken updatedToken = validToken.withExpiresAt(Instant.now().plusHours(2));

            // Then JWT should be preserved
            assertEquals(TEST_JWT, updatedToken.getJwt(), "JWT should be preserved in updated token");
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCasesTests {

        @Test
        @DisplayName("Token with very long field values")
        void tokenWithVeryLongFields() {
            // When creating token with very long field values
            String longWorkItemId = new String(new char[1000]).replace('\0', 'W');
            String longAgentId = new String(new char[500]).replace('\0', 'A');
            String longSession = new String(new char[2000]).replace('\0', 'S');
            String longJWT = new String(new char[2048]).replace('\0', 'J');

            HandoffToken longToken = new HandoffToken(
                longWorkItemId,
                longAgentId,
                longAgentId + "-target",
                longSession,
                testExpiresAt,
                longJWT
            );

            // Then all fields should be preserved
            assertEquals(longWorkItemId, longToken.workItemId());
            assertEquals(longAgentId, longToken.fromAgent());
            assertEquals(longAgentId + "-target", longToken.toAgent());
            assertEquals(longSession, longToken.engineSession());
            assertEquals(testExpiresAt, longToken.expiresAt());
        }

        @Test
        @DisplayName("Token with special characters in fields")
        void tokenWithSpecialCharacters() {
            // When creating token with special characters
            String specialWorkItemId = "WI-@#$%^&*()_+-={}[]|\\:;\"'<>,.?/~";
            String specialAgentId = "agent-@special.chars!123";

            HandoffToken specialToken = new HandoffToken(
                specialWorkItemId,
                specialAgentId,
                specialAgentId + "-target",
                TEST_ENGINE_SESSION,
                testExpiresAt,
                TEST_JWT
            );

            // Then special characters should be preserved
            assertEquals(specialWorkItemId, specialToken.workItemId());
            assertEquals(specialAgentId, specialToken.fromAgent());
        }

        @Test
        @DisplayName("Token with Unicode characters")
        void tokenWithUnicodeCharacters() {
            // When creating token with Unicode characters
            String unicodeWorkItemId = "WI-中文-🚀-测试";
            String unicodeAgentId = "agent-日本語-🌍";

            HandoffToken unicodeToken = new HandoffToken(
                unicodeWorkItemId,
                unicodeAgentId,
                unicodeAgentId + "-target",
                TEST_ENGINE_SESSION,
                testExpiresAt,
                TEST_JWT
            );

            // Then Unicode characters should be preserved
            assertEquals(unicodeWorkItemId, unicodeToken.workItemId());
            assertEquals(unicodeAgentId, unicodeToken.fromAgent());
        }

        @Test
        @DisplayName("Token with minimum expiration time")
        void tokenWithMinimumExpiration() {
            // When creating token with minimum expiration (1ms in future)
            Instant minExpiration = Instant.now().plus(Duration.ofMillis(1));
            HandoffToken minToken = new HandoffToken(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                TEST_ENGINE_SESSION,
                minExpiration,
                TEST_JWT
            );

            // Then token should be valid
            assertTrue(minToken.isValid(), "Token with minimum expiration should be valid");
        }
    }

    @Nested
    @DisplayName("Performance")
    class PerformanceTests {

        @Test
        @DisplayName("Token creation performance")
        void tokenCreationPerformance() {
            // When creating many tokens
            int iterations = 1000;
            long startTime = System.nanoTime();

            for (int i = 0; i < iterations; i++) {
                new HandoffToken(
                    TEST_WORK_ITEM_ID + "-" + i,
                    TEST_FROM_AGENT,
                    TEST_TO_AGENT,
                    TEST_ENGINE_SESSION,
                    testExpiresAt,
                    TEST_JWT
                );
            }

            long endTime = System.nanoTime();
            long durationMs = (endTime - startTime) / 1_000_000;

            // Then average creation time should be reasonable
            double avgTimeMs = (double) durationMs / iterations;
            assertTrue(avgTimeMs < 0.1, "Average token creation time should be < 0.1ms, was " + avgTimeMs + "ms");
        }

        @Test
        @DisplayName("Token validation performance")
        void tokenValidationPerformance() {
            // When creating and validating many tokens
            int iterations = 1000;
            HandoffToken[] tokens = new HandoffToken[iterations];

            // Create tokens
            for (int i = 0; i < iterations; i++) {
                tokens[i] = new HandoffToken(
                    TEST_WORK_ITEM_ID + "-" + i,
                    TEST_FROM_AGENT,
                    TEST_TO_AGENT,
                    TEST_ENGINE_SESSION,
                    testExpiresAt,
                    TEST_JWT
                );
            }

            // Validate tokens
            long startTime = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                tokens[i].isValid();
            }

            long endTime = System.nanoTime();
            long durationMs = (endTime - startTime) / 1_000_000;

            // Then average validation time should be reasonable
            double avgTimeMs = (double) durationMs / iterations;
            assertTrue(avgTimeMs < 0.01, "Average token validation time should be < 0.01ms, was " + avgTimeMs + "ms");
        }
    }
}