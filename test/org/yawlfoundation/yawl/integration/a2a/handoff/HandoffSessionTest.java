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
import java.util.Map;
import java.util.Collections;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for HandoffSession record.
 *
 * Tests session creation, context management, token conversion,
 * and all edge cases including validation scenarios.
 *
 * Coverage targets:
 * - Session constructor validation
 * - fromToken() method functionality
 * - Context management (withContext, merging)
 * - Token conversion expectations
 * - Field validation (null, blank, edge cases)
 * - Immutability guarantees
 */
class HandoffSessionTest {

    private static final String TEST_WORK_ITEM_ID = "WI-42";
    private static final String TEST_FROM_AGENT = "source-agent";
    private static final String TEST_TO_AGENT = "target-agent";
    private static final String TEST_ENGINE_SESSION = "session-handle-123";
    private static final Instant TEST_EXPIRATION = Instant.now().plus(Duration.ofMinutes(5));

    private HandoffSession basicSession;
    private Map<String, Object> testContext;

    @BeforeEach
    void setUp() {
        // Create a basic session without context
        basicSession = new HandoffSession(
            TEST_WORK_ITEM_ID,
            TEST_FROM_AGENT,
            TEST_TO_AGENT,
            TEST_ENGINE_SESSION,
            TEST_EXPIRATION
        );

        // Create test context data
        testContext = new HashMap<>();
        testContext.put("reason", "Document language not supported");
        testContext.put("confidence", 0.95);
        testContext.put("metadata", Map.of(
            "documentType", "pdf",
            "pages", 42,
            "size", "2.5MB"
        ));
    }

    @Nested
    @DisplayName("Constructor Validation")
    class ConstructorValidationTests {

        @Test
        @DisplayName("Create session with minimal parameters")
        void createSessionWithMinimalParameters() {
            // When creating session with minimal parameters
            HandoffSession session = new HandoffSession(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                TEST_ENGINE_SESSION,
                TEST_EXPIRATION
            );

            // Then all basic fields should be set correctly
            assertEquals(TEST_WORK_ITEM_ID, session.workItemId());
            assertEquals(TEST_FROM_AGENT, session.fromAgent());
            assertEquals(TEST_TO_AGENT, session.toAgent());
            assertEquals(TEST_ENGINE_SESSION, session.engineSession());
            assertEquals(TEST_EXPIRATION, session.tokenExpiration());
            assertTrue(session.context().isEmpty(), "Context should be empty when not provided");
        }

        @Test
        @DisplayName("Create session with full parameters")
        void createSessionWithFullParameters() {
            // When creating session with context
            HandoffSession session = new HandoffSession(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                TEST_ENGINE_SESSION,
                TEST_EXPIRATION,
                testContext
            );

            // Then all fields should be set correctly
            assertEquals(TEST_WORK_ITEM_ID, session.workItemId());
            assertEquals(TEST_FROM_AGENT, session.fromAgent());
            assertEquals(TEST_TO_AGENT, session.toAgent());
            assertEquals(TEST_ENGINE_SESSION, session.engineSession());
            assertEquals(TEST_EXPIRATION, session.tokenExpiration());
            assertEquals(testContext, session.context());
        }

        @Test
        @DisplayName("Reject null workItemId")
        void rejectNullWorkItemId() {
            assertThrows(IllegalArgumentException.class, () -> {
                new HandoffSession(null, TEST_FROM_AGENT, TEST_TO_AGENT, TEST_ENGINE_SESSION, TEST_EXPIRATION, testContext);
            }, "Should reject null workItemId");
        }

        @Test
        @DisplayName("Reject blank workItemId")
        void rejectBlankWorkItemId() {
            assertThrows(IllegalArgumentException.class, () -> {
                new HandoffSession("", TEST_FROM_AGENT, TEST_TO_AGENT, TEST_ENGINE_SESSION, TEST_EXPIRATION, testContext);
            }, "Should reject blank workItemId");
        }

        @Test
        @DisplayName("Reject null fromAgent")
        void rejectNullFromAgent() {
            assertThrows(IllegalArgumentException.class, () -> {
                new HandoffSession(TEST_WORK_ITEM_ID, null, TEST_TO_AGENT, TEST_ENGINE_SESSION, TEST_EXPIRATION, testContext);
            }, "Should reject null fromAgent");
        }

        @Test
        @DisplayName("Reject blank fromAgent")
        void rejectBlankFromAgent() {
            assertThrows(IllegalArgumentException.class, () -> {
                new HandoffSession(TEST_WORK_ITEM_ID, "", TEST_TO_AGENT, TEST_ENGINE_SESSION, TEST_EXPIRATION, testContext);
            }, "Should reject blank fromAgent");
        }

        @Test
        @DisplayName("Reject null toAgent")
        void rejectNullToAgent() {
            assertThrows(IllegalArgumentException.class, () -> {
                new HandoffSession(TEST_WORK_ITEM_ID, TEST_FROM_AGENT, null, TEST_ENGINE_SESSION, TEST_EXPIRATION, testContext);
            }, "Should reject null toAgent");
        }

        @Test
        @DisplayName("Reject blank toAgent")
        void rejectBlankToAgent() {
            assertThrows(IllegalArgumentException.class, () -> {
                new HandoffSession(TEST_WORK_ITEM_ID, TEST_FROM_AGENT, "", TEST_ENGINE_SESSION, TEST_EXPIRATION, testContext);
            }, "Should reject blank toAgent");
        }

        @Test
        @DisplayName("Reject null engineSession")
        void rejectNullEngineSession() {
            assertThrows(IllegalArgumentException.class, () -> {
                new HandoffSession(TEST_WORK_ITEM_ID, TEST_FROM_AGENT, TEST_TO_AGENT, null, TEST_EXPIRATION, testContext);
            }, "Should reject null engineSession");
        }

        @Test
        @DisplayName("Reject blank engineSession")
        void rejectBlankEngineSession() {
            assertThrows(IllegalArgumentException.class, () -> {
                new HandoffSession(TEST_WORK_ITEM_ID, TEST_FROM_AGENT, TEST_TO_AGENT, "", TEST_EXPIRATION, testContext);
            }, "Should reject blank engineSession");
        }

        @Test
        @DisplayName("Reject null tokenExpiration")
        void rejectNullTokenExpiration() {
            assertThrows(IllegalArgumentException.class, () -> {
                new HandoffSession(TEST_WORK_ITEM_ID, TEST_FROM_AGENT, TEST_TO_AGENT, TEST_ENGINE_SESSION, null, testContext);
            }, "Should reject null tokenExpiration");
        }

        @Test
        @DisplayName("Reject null context")
        void rejectNullContext() {
            assertThrows(IllegalArgumentException.class, () -> {
                new HandoffSession(TEST_WORK_ITEM_ID, TEST_FROM_AGENT, TEST_TO_AGENT, TEST_ENGINE_SESSION, TEST_EXPIRATION, null);
            }, "Should reject null context");
        }
    }

    @Nested
    @DisplayName("Token Conversion")
    class TokenConversionTests {

        @Test
        @DisplayName("fromToken creates session from HandoffToken")
        void fromTokenCreatesSession() {
            // Given a handoff token
            HandoffToken token = new HandoffToken(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                TEST_ENGINE_SESSION,
                TEST_EXPIRATION,
                "test-jwt"
            );

            // When converting to session
            HandoffSession session = HandoffSession.fromToken(token);

            // Then session should match token data
            assertEquals(TEST_WORK_ITEM_ID, session.workItemId());
            assertEquals(TEST_FROM_AGENT, session.fromAgent());
            assertEquals(TEST_TO_AGENT, session.toAgent());
            assertEquals(TEST_ENGINE_SESSION, session.engineSession());
            assertEquals(TEST_EXPIRATION, session.tokenExpiration());
            assertTrue(session.context().isEmpty(), "Context should be empty when converting from token");
        }

        @Test
        @DisplayName("fromToken with null token throws exception")
        void fromTokenWithNullToken() {
            // When converting null token
            // Then should throw exception
            assertThrows(NullPointerException.class, () -> {
                HandoffSession.fromToken(null);
            }, "Should throw NullPointerException for null token");
        }
    }

    @Nested
    @DisplayName("Context Management")
    class ContextManagementTests {

        @Test
        @DisplayName("Basic session has empty context")
        void basicSessionHasEmptyContext() {
            // Given basic session without context
            // Then context should be empty
            assertTrue(basicSession.context().isEmpty(), "Basic session should have empty context");
        }

        @Test
        @DisplayName("Session with context preserves context")
        void sessionWithContextPreservesContext() {
            // Given session with context
            HandoffSession session = new HandoffSession(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                TEST_ENGINE_SESSION,
                TEST_EXPIRATION,
                testContext
            );

            // Then context should be preserved
            assertEquals(testContext, session.context());
        }

        @Test
        @DisplayName("withContext adds new context")
        void withContextAddsNewContext() {
            // Given session with basic context
            Map<String, Object> additionalContext = Map.of(
                "priority", "high",
                "estimatedDuration", "PT30M"
            );

            // When adding new context
            HandoffSession updatedSession = basicSession.withContext(additionalContext);

            // Then original session should be unchanged
            assertTrue(basicSession.context().isEmpty(), "Original session should be unchanged");

            // And new session should have merged context
            assertEquals(additionalContext, updatedSession.context());
        }

        @Test
        @DisplayName("withContext merges existing context")
        void withContextMergesExistingContext() {
            // Given session with existing context
            HandoffSession sessionWithContext = new HandoffSession(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                TEST_ENGINE_SESSION,
                TEST_EXPIRATION,
                Map.of("existing", "value")
            );

            // When adding new context
            Map<String, Object> additionalContext = Map.of(
                "priority", "high",
                "existing", "new-value"  // Override existing
            );

            HandoffSession updatedSession = sessionWithContext.withContext(additionalContext);

            // Then context should be merged
            Map<String, Object> expectedContext = new HashMap<>();
            expectedContext.put("existing", "new-value");
            expectedContext.put("priority", "high");

            assertEquals(expectedContext, updatedSession.context());
        }

        @Test
        @DisplayName("withContext creates immutable copy")
        void withContextCreatesImmutableCopy() {
            // Given session with mutable context
            Map<String, Object> mutableContext = new HashMap<>(testContext);
            HandoffSession session = new HandoffSession(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                TEST_ENGINE_SESSION,
                TEST_EXPIRATION,
                mutableContext
            );

            // When modifying original context
            mutableContext.put("modified", true);

            // Then session context should be unchanged (immutable copy)
            assertFalse(session.context().containsKey("modified"), "Session context should be immutable");
        }
    }

    @Nested
    @DisplayName("Session Updates")
    class SessionUpdatesTests {

        @Test
        @DisplayName("withTokenExpiration updates expiration")
        void withTokenExpirationUpdatesExpiration() {
            // Given original session
            Instant newExpiration = Instant.now().plus(Duration.ofHours(1));

            // When updating expiration
            HandoffSession updatedSession = basicSession.withTokenExpiration(newExpiration);

            // Then original session should be unchanged
            assertEquals(TEST_EXPIRATION, basicSession.tokenExpiration());

            // And new session should have updated expiration
            assertEquals(newExpiration, updatedSession.tokenExpiration());

            // And other fields should be preserved
            assertEquals(TEST_WORK_ITEM_ID, updatedSession.workItemId());
            assertEquals(TEST_FROM_AGENT, updatedSession.fromAgent());
        }

        @Test
        @DisplayName("withTokenExpiration preserves context")
        void withTokenExpirationPreservesContext() {
            // Given session with context
            HandoffSession sessionWithContext = new HandoffSession(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                TEST_ENGINE_SESSION,
                TEST_EXPIRATION,
                testContext
            );

            // When updating expiration
            Instant newExpiration = Instant.now().plus(Duration.ofHours(2));
            HandoffSession updatedSession = sessionWithContext.withTokenExpiration(newExpiration);

            // Then context should be preserved
            assertEquals(testContext, updatedSession.context());
        }
    }

    @Nested
    @DisplayName("Token Conversion Expectations")
    class TokenConversionExpectationsTests {

        @Test
        @DisplayName("toToken throws UnsupportedOperationException")
        void toTokenThrowsUnsupportedOperationException() {
            // Given any session
            // Then toToken should throw UnsupportedOperationException
            assertThrows(UnsupportedOperationException.class, () -> {
                basicSession.toToken();
            }, "toToken should throw UnsupportedOperationException");
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCasesTests {

        @Test
        @DisplayName("Session with very large context")
        void sessionWithLargeContext() {
            // Given session with large context
            Map<String, Object> largeContext = new HashMap<>();
            largeContext.put("data", new String(new char[10000]).replace('\0', 'x'));
            largeContext.put("items", new String[1000]);

            HandoffSession session = new HandoffSession(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                TEST_ENGINE_SESSION,
                TEST_EXPIRATION,
                largeContext
            );

            // Then large context should be preserved
            assertEquals(largeContext, session.context());
        }

        @Test
        @DisplayName("Session with special characters in context")
        void sessionWithSpecialCharactersInContext() {
            // Given context with special characters
            Map<String, Object> specialContext = Map.of(
                "reason", "Document @#$%^&*()_+-={}[]|\\:;\"'<>,.?/~ contains special chars",
                "confidence", 0.95
            );

            HandoffSession session = new HandoffSession(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                TEST_ENGINE_SESSION,
                TEST_EXPIRATION,
                specialContext
            );

            // Then special characters should be preserved
            assertEquals(specialContext, session.context());
        }

        @Test
        @DisplayName("Session with null values in context")
        void sessionWithNullValuesInContext() {
            // Given context with null values
            Map<String, Object> contextWithNulls = new HashMap<>();
            contextWithNulls.put("valid", "value");
            contextWithNulls.put("nullValue", null);

            // Should allow null values in context (not validated)
            assertDoesNotThrow(() -> {
                new HandoffSession(
                    TEST_WORK_ITEM_ID,
                    TEST_FROM_AGENT,
                    TEST_TO_AGENT,
                    TEST_ENGINE_SESSION,
                    TEST_EXPIRATION,
                    contextWithNulls
                );
            });
        }

        @Test
        @DisplayName("Session with minimum expiration time")
        void sessionWithMinimumExpiration() {
            // Given session with minimum expiration (1ms in future)
            Instant minExpiration = Instant.now().plus(Duration.ofMillis(1));
            HandoffSession session = new HandoffSession(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                TEST_ENGINE_SESSION,
                minExpiration
            );

            // Then session should be valid
            assertNotNull(session, "Session should be created with minimum expiration");
        }
    }

    @Nested
    @DisplayName("Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("Session is immutable - field access returns copies")
        void sessionIsImmutable() {
            // Given session with context
            Map<String, Object> context = new HashMap<>(testContext);
            HandoffSession session = new HandoffSession(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                TEST_ENGINE_SESSION,
                TEST_EXPIRATION,
                context
            );

            // When modifying original context
            context.clear();

            // Then session context should be unchanged
            assertEquals(testContext, session.context());
        }

        @Test
        @DisplayName("Session context is immutable copy")
        void sessionContextIsImmutableCopy() {
            // Given session
            HandoffSession session = basicSession.withContext(testContext);

            // When trying to modify returned context
            assertThrows(UnsupportedOperationException.class, () -> {
                session.context().put("test", "value");
            }, "Session context should be immutable");
        }
    }
}