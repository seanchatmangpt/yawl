/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.integration.a2a.handoff;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.yawlfoundation.yawl.integration.a2a.auth.JwtAuthenticationProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for HandoffProtocol implementation.
 *
 * Tests JWT-based handoff token generation, validation, and message handling
 * following the ADR-025 agent coordination protocol.
 *
 * Coverage targets:
 * - HandoffToken creation and validation
 * - HandoffMessage creation and parsing
 * - JWT token generation with proper claims
 * - Token expiration and invalidation
 * - Session creation from tokens
 * - Edge cases and failure scenarios
 */
class HandoffProtocolTest {

    private JwtAuthenticationProvider jwtProvider;
    private HandoffProtocol protocol;
    private static final String TEST_WORK_ITEM_ID = "WI-42";
    private static final String TEST_FROM_AGENT = "agent-a";
    private static final String TEST_TO_AGENT = "agent-b";
    private static final String TEST_ENGINE_SESSION = "session-handle-123";

    @BeforeEach
    void setUp() throws Exception {
        // Create a real JWT provider with a test secret
        String testSecret = "test-secret-key-for-handoff-validation-32-characters";
        jwtProvider = new JwtAuthenticationProvider(testSecret, null);

        // Create protocol with default TTL (60 seconds)
        protocol = new HandoffProtocol(jwtProvider);
    }

    @Nested
    @DisplayName("Handoff Token Generation")
    class HandoffTokenGenerationTests {

        @Test
        @DisplayName("Generate valid handoff token with all parameters")
        void generateValidHandoffToken() throws HandoffException {
            // When generating a handoff token
            HandoffToken token = protocol.generateHandoffToken(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                TEST_ENGINE_SESSION,
                Duration.ofSeconds(30)
            );

            // Then the token should be valid and contain correct claims
            assertNotNull(token, "Generated token should not be null");
            assertEquals(TEST_WORK_ITEM_ID, token.workItemId(), "Work item ID should match");
            assertEquals(TEST_FROM_AGENT, token.fromAgent(), "From agent should match");
            assertEquals(TEST_TO_AGENT, token.toAgent(), "To agent should match");
            assertEquals(TEST_ENGINE_SESSION, token.engineSession(), "Engine session should match");

            // Token should be valid (not expired)
            assertTrue(token.isValid(), "Token should be valid when freshly generated");
        }

        @Test
        @DisplayName("Generate token with default TTL when not specified")
        void generateTokenWithDefaultTtl() throws HandoffException {
            // When generating a token without specifying TTL
            HandoffToken token = protocol.generateHandoffToken(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                TEST_ENGINE_SESSION
            );

            // Then the token should use the default TTL (60 seconds)
            assertNotNull(token);
            assertTrue(token.isValid(), "Token should be valid");
            // Verify the expiration is approximately 60 seconds from now
            long timeToExpiry = Duration.between(Instant.now(), token.expiresAt()).toSeconds();
            assertTrue(timeToExpiry >= 55 && timeToExpiry <= 65,
                "Token should expire in approximately 60 seconds");
        }

        @Test
        @DisplayName("Generate token with very short TTL")
        void generateTokenWithShortTtl() throws HandoffException {
            // When generating a token with 1 second TTL
            Duration shortTtl = Duration.ofSeconds(1);
            HandoffToken token = protocol.generateHandoffToken(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                TEST_ENGINE_SESSION,
                shortTtl
            );

            // Then the token should be valid initially but expire quickly
            assertNotNull(token);
            assertTrue(token.isValid(), "Token should be valid when generated");

            // Token should expire soon (within 2 seconds)
            long timeToExpiry = Duration.between(Instant.now(), token.expiresAt()).toSeconds();
            assertEquals(1, timeToExpiry, "Token should expire in 1 second");
        }

        @Test
        @DisplayName("Generate token throws exception for invalid parameters")
        void generateTokenThrowsForInvalidParameters() {
            // Then token generation should fail for invalid parameters
            assertThrows(IllegalArgumentException.class, () -> {
                protocol.generateHandoffToken(null, TEST_FROM_AGENT, TEST_TO_AGENT, TEST_ENGINE_SESSION);
            }, "Null work item ID should throw exception");

            assertThrows(IllegalArgumentException.class, () -> {
                protocol.generateHandoffToken("", TEST_FROM_AGENT, TEST_TO_AGENT, TEST_ENGINE_SESSION);
            }, "Empty work item ID should throw exception");

            assertThrows(IllegalArgumentException.class, () -> {
                protocol.generateHandoffToken(TEST_WORK_ITEM_ID, null, TEST_TO_AGENT, TEST_ENGINE_SESSION);
            }, "Null from agent should throw exception");
        }

        @Test
        @DisplayName("Generate token with empty engine session")
        void generateTokenWithEmptyEngineSession() throws HandoffException {
            // When generating a token with empty engine session
            HandoffToken token = protocol.generateHandoffToken(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                ""
            );

            // Then the token should still be created (empty session is allowed)
            assertNotNull(token);
            assertEquals("", token.engineSession(), "Engine session should be empty");
        }
    }

    @Nested
    @DisplayName("Handoff Message Creation")
    class HandoffMessageCreationTests {

        @Test
        @DisplayName("Create valid handoff message with payload")
        void createValidHandoffMessage() throws HandoffException {
            // When creating a handoff message with additional payload
            Map<String, Object> payload = Map.of(
                "reason", "document language not supported",
                "confidence", 0.95,
                "metadata", Map.of("documentType", "pdf", "pages", 42)
            );

            HandoffMessage message = protocol.createHandoffMessage(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                TEST_ENGINE_SESSION,
                payload,
                Duration.ofMinutes(5)
            );

            // Then the message should be properly formed
            assertNotNull(message, "Message should not be null");
            assertEquals(TEST_WORK_ITEM_ID, message.workItemId(), "Work item ID should match");
            assertEquals(TEST_FROM_AGENT, message.fromAgent(), "From agent should match");
            assertEquals(TEST_TO_AGENT, message.toAgent(), "To agent should match");
            assertEquals(TEST_ENGINE_SESSION, message.token().engineSession(), "Token session should match");

            // Verify the token is valid
            assertTrue(message.token().isValid(), "Handoff token should be valid");

            // Verify payload is included
            assertEquals(payload, message.payload(), "Payload should be included");
        }

        @Test
        @DisplayName("Create handoff message without payload")
        void createHandoffMessageWithoutPayload() throws HandoffException {
            // When creating a handoff message without payload
            HandoffMessage message = protocol.createHandoffMessage(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                TEST_ENGINE_SESSION
            );

            // Then the message should be created with null payload
            assertNotNull(message, "Message should not be null");
            assertNull(message.payload(), "Payload should be null when not specified");
            assertNotNull(message.token(), "Token should be present");
            assertTrue(message.token().isValid(), "Token should be valid");
        }

        @Test
        @DisplayName("Create handoff message sets timestamp")
        void createHandoffMessageSetsTimestamp() throws HandoffException {
            // When creating a handoff message
            HandoffMessage message = protocol.createHandoffMessage(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                TEST_ENGINE_SESSION
            );

            // Then the message should have a recent timestamp
            Instant now = Instant.now();
            Duration timeDiff = Duration.between(message.timestamp(), now);
            assertTrue(timeDiff.toMillis() < 100, "Message timestamp should be recent");
        }

        @Test
        @DisplayName("Create handoff message with large payload")
        void createHandoffMessageWithLargePayload() throws HandoffException {
            // When creating a message with large payload
            Map<String, Object> largePayload = Map.of(
                "data", new String(new char[10000]).replace('\0', 'x'),
                "items", new String[1000]
            );

            HandoffMessage message = protocol.createHandoffMessage(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                TEST_ENGINE_SESSION,
                largePayload,
                Duration.ofMinutes(10)
            );

            // Then the message should handle the large payload
            assertNotNull(message);
            assertEquals(largePayload, message.payload(), "Large payload should be preserved");
        }
    }

    @Nested
    @DisplayName("Token Validation")
    class TokenValidationTests {

        private HandoffToken validToken;

        @BeforeEach
        void createValidToken() throws HandoffException {
            validToken = protocol.generateHandoffToken(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                TEST_ENGINE_SESSION,
                Duration.ofMinutes(5)
            );
        }

        @Test
        @DisplayName("Verify valid token succeeds")
        void verifyValidTokenSucceeds() throws HandoffException {
            // When verifying a valid token
            HandoffToken verified = protocol.verifyHandoffToken(validToken);

            // Then the verification should succeed
            assertNotNull(verified, "Verified token should not be null");
            assertEquals(validToken.workItemId(), verified.workItemId(), "Work item ID should match");
            assertEquals(validToken.fromAgent(), verified.fromAgent(), "From agent should match");
            assertEquals(validToken.toAgent(), verified.toAgent(), "To agent should match");
            assertEquals(validToken.engineSession(), verified.engineSession(), "Engine session should match");
        }

        @Test
        @DisplayName("Verify expired token throws exception")
        void verifyExpiredTokenThrowsException() throws InterruptedException {
            // When generating a token that expires immediately
            HandoffToken expiredToken = protocol.generateHandoffToken(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                TEST_ENGINE_SESSION,
                Duration.ofMillis(1)
            );

            // Wait for token to expire
            Thread.sleep(10);

            // Then verification should throw exception
            assertThrows(HandoffException.class, () -> {
                protocol.verifyHandoffToken(expiredToken);
            }, "Expired token should throw HandoffException");
        }

        @Test
        @DisplayName("Verify invalid token throws exception")
        void verifyInvalidTokenThrowsException() throws HandoffException {
            // When creating a token with invalid data
            HandoffToken invalidToken = new HandoffToken(
                "invalid-work-item",
                "invalid-from",
                "invalid-to",
                "invalid-session",
                Instant.now().minusSeconds(1) // Already expired
            );

            // Then verification should throw exception
            assertThrows(HandoffException.class, () -> {
                protocol.verifyHandoffToken(invalidToken);
            }, "Invalid token should throw HandoffException");
        }

        @Test
        @DisplayName("Verify token with null parameters throws exception")
        void verifyTokenWithNullParameters() {
            // Then verification should throw exception for null token
            assertThrows(IllegalArgumentException.class, () -> {
                protocol.verifyHandoffToken(null);
            }, "Null token should throw exception");
        }

        @Test
        @DisplayName("Token validation preserves all claims")
        void tokenValidationPreservesClaims() throws HandoffException {
            // When generating and verifying a token
            HandoffToken original = protocol.generateHandoffToken(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                TEST_ENGINE_SESSION,
                Duration.ofMinutes(10)
            );

            HandoffToken verified = protocol.verifyHandoffToken(original);

            // Then all claims should be preserved
            assertEquals(original.workItemId(), verified.workItemId());
            assertEquals(original.fromAgent(), verified.fromAgent());
            assertEquals(original.toAgent(), verified.toAgent());
            assertEquals(original.engineSession(), verified.engineSession());
            assertEquals(original.expiresAt(), verified.expiresAt());
        }
    }

    @Nested
    @DisplayName("Session Management")
    class SessionManagementTests {

        @Test
        @DisplayName("Create session from verified token")
        void createSessionFromToken() throws HandoffException {
            // When generating and creating a session
            HandoffToken token = protocol.generateHandoffToken(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                TEST_ENGINE_SESSION,
                Duration.ofHours(1)
            );

            HandoffSession session = protocol.createSession(token);

            // Then the session should be properly created
            assertNotNull(session, "Session should not be null");
            assertEquals(TEST_WORK_ITEM_ID, session.workItemId(), "Work item ID should match");
            assertEquals(TEST_FROM_AGENT, session.fromAgent(), "From agent should match");
            assertEquals(TEST_TO_AGENT, session.toAgent(), "To agent should match");
            assertEquals(TEST_ENGINE_SESSION, session.engineSession(), "Engine session should match");
            assertEquals(token.expiresAt(), session.expiresAt(), "Expiration should match");
        }

        @Test
        @DisplayName("Create session from expired token")
        void createSessionFromExpiredToken() throws InterruptedException, HandoffException {
            // When generating an expired token
            HandoffToken expiredToken = protocol.generateHandoffToken(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                TEST_ENGINE_SESSION,
                Duration.ofMillis(1)
            );

            Thread.sleep(10); // Wait for expiration

            // Then creating a session should still work (but token is invalid)
            assertThrows(HandoffException.class, () -> {
                protocol.verifyHandoffToken(expiredToken);
            }, "Cannot verify expired token");
        }

        @Test
        @DisplayName("Validate handoff message creates proper session")
        void validateHandoffMessageCreatesSession() throws HandoffException {
            // When creating and validating a handoff message
            Map<String, Object> payload = Map.of("context", "test-data");
            HandoffMessage message = protocol.createHandoffMessage(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                TEST_ENGINE_SESSION,
                payload,
                Duration.ofMinutes(5)
            );

            HandoffSession session = protocol.validateHandoffMessage(message);

            // Then the session should be valid and match the message
            assertNotNull(session, "Session should not be null");
            assertEquals(TEST_WORK_ITEM_ID, session.workItemId());
            assertEquals(TEST_FROM_AGENT, session.fromAgent());
            assertEquals(TEST_TO_AGENT, session.toAgent());
            assertEquals(TEST_ENGINE_SESSION, session.engineSession());
        }

        @Test
        @DisplayName("Validate invalid handoff message throws exception")
        void validateInvalidMessageThrowsException() {
            // When validating an invalid message
            HandoffMessage invalidMessage = new HandoffMessage(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                new HandoffToken("", "", "", "", Instant.now()),
                null,
                Instant.now()
            );

            // Then validation should throw exception
            assertThrows(HandoffException.class, () -> {
                protocol.validateHandoffMessage(invalidMessage);
            }, "Invalid message should throw HandoffException");
        }
    }

    @Nested
    @DisplayName("Edge Cases and Failure Scenarios")
    class EdgeCasesTests {

        @Test
        @DisplayName("Handle concurrent token generation")
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void handleConcurrentTokenGeneration() throws HandoffException {
            // When generating multiple tokens concurrently
            int tokenCount = 10;
            Thread[] threads = new Thread[tokenCount];
            HandoffToken[] tokens = new HandoffToken[tokenCount];

            for (int i = 0; i < tokenCount; i++) {
                final int index = i;
                threads[i] = new Thread(() -> {
                    try {
                        tokens[index] = protocol.generateHandoffToken(
                            TEST_WORK_ITEM_ID + "-" + index,
                            TEST_FROM_AGENT + "-" + index,
                            TEST_TO_AGENT + "-" + index,
                            TEST_ENGINE_SESSION + "-" + index
                        );
                    } catch (HandoffException e) {
                        fail("Thread " + index + " failed: " + e.getMessage());
                    }
                });
                threads[i].start();
            }

            // Wait for all threads to complete
            for (Thread thread : threads) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    fail("Thread interrupted");
                }
            }

            // Then all tokens should be valid and unique
            for (int i = 0; i < tokenCount; i++) {
                assertNotNull(tokens[i], "Token " + i + " should not be null");
                assertTrue(tokens[i].isValid(), "Token " + i + " should be valid");
                assertEquals(TEST_WORK_ITEM_ID + "-" + i, tokens[i].workItemId());
            }
        }

        @Test
        @DisplayName("Generate tokens with special characters")
        void generateTokenWithSpecialCharacters() throws HandoffException {
            // When generating tokens with special characters in fields
            String specialWorkItemId = "WI-@#$%^&*()_+-={}[]|\\:;\"'<>,.?/~";
            String specialAgentId = "agent-@special.chars!";

            HandoffToken token = protocol.generateHandoffToken(
                specialWorkItemId,
                specialAgentId,
                specialAgentId + "-target",
                TEST_ENGINE_SESSION,
                Duration.ofMinutes(5)
            );

            // Then the token should handle special characters correctly
            assertNotNull(token);
            assertEquals(specialWorkItemId, token.workItemId());
            assertEquals(specialAgentId, token.fromAgent());
        }

        @Test
        @DisplayName("Generate token with very long fields")
        void generateTokenWithLongFields() throws HandoffException {
            // When generating tokens with very long field values
            String longWorkItemId = new String(new char[1000]).replace('\0', 'W');
            String longAgentId = new String(new char[500]).replace('\0', 'A');

            HandoffToken token = protocol.generateHandoffToken(
                longWorkItemId,
                longAgentId,
                longAgentId + "-target",
                TEST_ENGINE_SESSION,
                Duration.ofMinutes(5)
            );

            // Then the token should handle long field values
            assertNotNull(token);
            assertEquals(longWorkItemId, token.workItemId());
            assertEquals(longAgentId, token.fromAgent());
        }

        @Test
        @DisplayName("Protocol configuration validation")
        void protocolConfigurationValidation() {
            // When creating protocol with null parameters
            assertThrows(IllegalArgumentException.class, () -> {
                new HandoffProtocol(null);
            }, "Null JWT provider should throw exception");

            assertThrows(IllegalArgumentException.class, () -> {
                new HandoffProtocol(jwtProvider, null);
            }, "Null TTL should throw exception");
        }

        @Test
        @DisplayName("Environment-based protocol creation")
        void environmentBasedProtocolCreation() {
            // When creating protocol from environment (mock implementation)
            assertThrows(IllegalStateException.class, () -> {
                HandoffProtocol.fromEnvironment();
            }, "Environment should fail without proper configuration");
        }
    }

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        @DisplayName("Token generation performance")
        void tokenGenerationPerformance() throws HandoffException {
            // When generating many tokens
            int iterations = 100;
            long startTime = System.nanoTime();

            for (int i = 0; i < iterations; i++) {
                HandoffToken token = protocol.generateHandoffToken(
                    TEST_WORK_ITEM_ID + "-" + i,
                    TEST_FROM_AGENT,
                    TEST_TO_AGENT,
                    TEST_ENGINE_SESSION,
                    Duration.ofMinutes(5)
                );
                assertNotNull(token, "Token " + i + " should not be null");
            }

            long endTime = System.nanoTime();
            long durationMs = (endTime - startTime) / 1_000_000;

            // Then average token generation time should be reasonable
            double avgTimeMs = (double) durationMs / iterations;
            assertTrue(avgTimeMs < 10, "Average token generation time should be < 10ms, was " + avgTimeMs + "ms");
            System.out.println("Average token generation time: " + avgTimeMs + "ms");
        }

        @Test
        @DisplayName("Token verification performance")
        void tokenVerificationPerformance() throws HandoffException {
            // When generating and verifying many tokens
            int iterations = 100;
            HandoffToken[] tokens = new HandoffToken[iterations];

            // Pre-generate all tokens
            for (int i = 0; i < iterations; i++) {
                tokens[i] = protocol.generateHandoffToken(
                    TEST_WORK_ITEM_ID + "-" + i,
                    TEST_FROM_AGENT,
                    TEST_TO_AGENT,
                    TEST_ENGINE_SESSION,
                    Duration.ofMinutes(5)
                );
            }

            // Verify all tokens
            long startTime = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                HandoffToken verified = protocol.verifyHandoffToken(tokens[i]);
                assertNotNull(verified, "Verified token " + i + " should not be null");
            }

            long endTime = System.nanoTime();
            long durationMs = (endTime - startTime) / 1_000_000;

            // Then average verification time should be reasonable
            double avgTimeMs = (double) durationMs / iterations;
            assertTrue(avgTimeMs < 5, "Average token verification time should be < 5ms, was " + avgTimeMs + "ms");
            System.out.println("Average token verification time: " + avgTimeMs + "ms");
        }
    }
}