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
import java.util.List;
import java.util.Collections;
import java.util.HashMap;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for HandoffMessage record.
 *
 * Tests message creation, validation, conversion between formats,
 * A2A message parsing, and all edge cases including validation scenarios.
 *
 * Coverage targets:
 * - Message constructor validation
 * - A2A message format conversion (toA2AMessage, fromA2AMessage)
 * - JSON conversion (toJson, fromJsonWithValidation)
 * - Validation methods (validate, validateWithBusinessRules)
 * - Field validation (null, blank, consistency)
 * - Expiration handling
 * - Payload management
 */
class HandoffMessageTest {

    private static final String TEST_WORK_ITEM_ID = "WI-42";
    private static final String TEST_FROM_AGENT = "source-agent";
    private static final String TEST_TO_AGENT = "target-agent";
    private static final String TEST_ENGINE_SESSION = "session-handle-123";
    private static final Instant TEST_TIMESTAMP = Instant.now();
    private static final String TEST_JWT = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJoYW5kb2ZmIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";

    private HandoffToken testToken;
    private HandoffMessage basicMessage;
    private Map<String, Object> testPayload;

    @BeforeEach
    void setUp() {
        // Create test token
        testToken = new HandoffToken(
            TEST_WORK_ITEM_ID,
            TEST_FROM_AGENT,
            TEST_TO_AGENT,
            TEST_ENGINE_SESSION,
            TEST_TIMESTAMP.plus(Duration.ofMinutes(5)),
            TEST_JWT
        );

        // Create basic message without payload
        basicMessage = new HandoffMessage(
            TEST_WORK_ITEM_ID,
            TEST_FROM_AGENT,
            TEST_TO_AGENT,
            testToken,
            TEST_TIMESTAMP
        );

        // Create test payload
        testPayload = new HashMap<>();
        testPayload.put("reason", "Document language not supported");
        testPayload.put("confidence", 0.95);
        testPayload.put("metadata", Map.of(
            "documentType", "pdf",
            "pages", 42,
            "size", "2.5MB"
        ));
    }

    @Nested
    @DisplayName("Constructor Validation")
    class ConstructorValidationTests {

        @Test
        @DisplayName("Create message with minimal parameters")
        void createMessageWithMinimalParameters() {
            // When creating message without payload
            HandoffMessage message = new HandoffMessage(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                testToken,
                TEST_TIMESTAMP
            );

            // Then all fields should be set correctly
            assertEquals(TEST_WORK_ITEM_ID, message.workItemId());
            assertEquals(TEST_FROM_AGENT, message.fromAgent());
            assertEquals(TEST_TO_AGENT, message.toAgent());
            assertEquals(testToken, message.token());
            assertEquals(TEST_TIMESTAMP, message.timestamp());
            assertTrue(message.payload().isEmpty(), "Payload should be empty when not provided");
        }

        @Test
        @DisplayName("Create message with full parameters")
        void createMessageWithFullParameters() {
            // When creating message with payload
            HandoffMessage message = new HandoffMessage(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                testToken,
                testPayload,
                TEST_TIMESTAMP
            );

            // Then all fields should be set correctly
            assertEquals(TEST_WORK_ITEM_ID, message.workItemId());
            assertEquals(TEST_FROM_AGENT, message.fromAgent());
            assertEquals(TEST_TO_AGENT, message.toAgent());
            assertEquals(testToken, message.token());
            assertEquals(TEST_TIMESTAMP, message.timestamp());
            assertEquals(testPayload, message.payload());
        }

        @Test
        @DisplayName("Reject null workItemId")
        void rejectNullWorkItemId() {
            assertThrows(IllegalArgumentException.class, () -> {
                new HandoffMessage(null, TEST_FROM_AGENT, TEST_TO_AGENT, testToken, TEST_TIMESTAMP);
            }, "Should reject null workItemId");
        }

        @Test
        @DisplayName("Reject blank workItemId")
        void rejectBlankWorkItemId() {
            assertThrows(IllegalArgumentException.class, () -> {
                new HandoffMessage("", TEST_FROM_AGENT, TEST_TO_AGENT, testToken, TEST_TIMESTAMP);
            }, "Should reject blank workItemId");
        }

        @Test
        @DisplayName("Reject null fromAgent")
        void rejectNullFromAgent() {
            assertThrows(IllegalArgumentException.class, () -> {
                new HandoffMessage(TEST_WORK_ITEM_ID, null, TEST_TO_AGENT, testToken, TEST_TIMESTAMP);
            }, "Should reject null fromAgent");
        }

        @Test
        @DisplayName("Reject blank fromAgent")
        void rejectBlankFromAgent() {
            assertThrows(IllegalArgumentException.class, () -> {
                new HandoffMessage(TEST_WORK_ITEM_ID, "", TEST_TO_AGENT, testToken, TEST_TIMESTAMP);
            }, "Should reject blank fromAgent");
        }

        @Test
        @DisplayName("Reject null toAgent")
        void rejectNullToAgent() {
            assertThrows(IllegalArgumentException.class, () -> {
                new HandoffMessage(TEST_WORK_ITEM_ID, TEST_FROM_AGENT, null, testToken, TEST_TIMESTAMP);
            }, "Should reject null toAgent");
        }

        @Test
        @DisplayName("Reject blank toAgent")
        void rejectBlankToAgent() {
            assertThrows(IllegalArgumentException.class, () -> {
                new HandoffMessage(TEST_WORK_ITEM_ID, TEST_FROM_AGENT, "", testToken, TEST_TIMESTAMP);
            }, "Should reject blank toAgent");
        }

        @Test
        @DisplayName("Reject null token")
        void rejectNullToken() {
            assertThrows(IllegalArgumentException.class, () -> {
                new HandoffMessage(TEST_WORK_ITEM_ID, TEST_FROM_AGENT, TEST_TO_AGENT, null, TEST_TIMESTAMP);
            }, "Should reject null token");
        }

        @Test
        @DisplayName("Reject null timestamp")
        void rejectNullTimestamp() {
            assertThrows(IllegalArgumentException.class, () -> {
                new HandoffMessage(TEST_WORK_ITEM_ID, TEST_FROM_AGENT, TEST_TO_AGENT, testToken, null);
            }, "Should reject null timestamp");
        }

        @Test
        @DisplayName("Reject null payload")
        void rejectNullPayload() {
            assertThrows(IllegalArgumentException.class, () -> {
                new HandoffMessage(TEST_WORK_ITEM_ID, TEST_FROM_AGENT, TEST_TO_AGENT, testToken, TEST_TIMESTAMP, null);
            }, "Should reject null payload");
        }
    }

    @Nested
    @DisplayName("Field Consistency Validation")
    class FieldConsistencyValidationTests {

        @Test
        @DisplayName("Reject workItemId that doesn't match token")
        void rejectWorkItemIdMismatch() {
            // Given token with different workItemId
            HandoffToken differentToken = new HandoffToken(
                "DIFFERENT-WI",
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                TEST_ENGINE_SESSION,
                testToken.expiresAt(),
                TEST_JWT
            );

            // When creating message with mismatch
            assertThrows(IllegalArgumentException.class, () -> {
                new HandoffMessage(TEST_WORK_ITEM_ID, TEST_FROM_AGENT, TEST_TO_AGENT, differentToken, TEST_TIMESTAMP);
            }, "Should reject workItemId that doesn't match token");
        }

        @Test
        @DisplayName("Reject fromAgent that doesn't match token")
        void rejectFromAgentMismatch() {
            // Given token with different fromAgent
            HandoffToken differentToken = new HandoffToken(
                TEST_WORK_ITEM_ID,
                "DIFFERENT-AGENT",
                TEST_TO_AGENT,
                TEST_ENGINE_SESSION,
                testToken.expiresAt(),
                TEST_JWT
            );

            // When creating message with mismatch
            assertThrows(IllegalArgumentException.class, () -> {
                new HandoffMessage(TEST_WORK_ITEM_ID, TEST_FROM_AGENT, TEST_TO_AGENT, differentToken, TEST_TIMESTAMP);
            }, "Should reject fromAgent that doesn't match token");
        }

        @Test
        @DisplayName("Reject toAgent that doesn't match token")
        void rejectToAgentMismatch() {
            // Given token with different toAgent
            HandoffToken differentToken = new HandoffToken(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                "DIFFERENT-AGENT",
                TEST_ENGINE_SESSION,
                testToken.expiresAt(),
                TEST_JWT
            );

            // When creating message with mismatch
            assertThrows(IllegalArgumentException.class, () -> {
                new HandoffMessage(TEST_WORK_ITEM_ID, TEST_FROM_AGENT, TEST_TO_AGENT, differentToken, TEST_TIMESTAMP);
            }, "Should reject toAgent that doesn't match token");
        }
    }

    @Nested
    @DisplayName("A2A Message Conversion")
    class A2AMessageConversionTests {

        @Test
        @DisplayName("Convert to A2A message format")
        void convertToA2AMessage() {
            // Given a handoff message with payload
            HandoffMessage message = new HandoffMessage(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                testToken,
                testPayload,
                TEST_TIMESTAMP
            );

            // When converting to A2A format
            List<Map<String, Object>> a2aMessage = message.toA2AMessage();

            // Then should have text part with handoff prefix
            Map<String, Object> textPart = a2aMessage.stream()
                .filter(part -> "text".equals(part.get("type")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Text part not found"));

            assertEquals("text", textPart.get("type"));
            assertEquals("YAWL_HANDOFF:" + TEST_WORK_ITEM_ID + ":" + TEST_FROM_AGENT, textPart.get("text"));

            // And should have data part with payload
            Map<String, Object> dataPart = a2aMessage.stream()
                .filter(part -> "data".equals(part.get("type")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Data part not found"));

            assertEquals("data", dataPart.get("type"));
            assertEquals(testPayload, dataPart.get("data"));
        }

        @Test
        @DisplayName("Convert to A2A message without payload")
        void convertToA2AMessageWithoutPayload() {
            // Given a handoff message without payload
            List<Map<String, Object>> a2aMessage = basicMessage.toA2AMessage();

            // Then should only have text part
            assertEquals(1, a2aMessage.size(), "Should only have text part");
            assertEquals("text", a2aMessage.get(0).get("type"));
            assertEquals("YAWL_HANDOFF:" + TEST_WORK_ITEM_ID + ":" + TEST_FROM_AGENT, a2aMessage.get(0).get("text"));
        }

        @Test
        @DisplayName("Parse A2A message format")
        void parseA2AMessage() throws HandoffException {
            // Given A2A message parts
            List<Map<String, Object>> a2aParts = Arrays.asList(
                Map.of("type", "text", "text", "YAWL_HANDOFF:WI-42:source-agent"),
                Map.of("type", "data", "data", Map.of(
                    "reason", "Document language not supported",
                    "confidence", 0.95
                ))
            );

            // When parsing from A2A
            HandoffMessage parsedMessage = HandoffMessage.fromA2AMessage(a2aParts);

            // Then message should be correctly parsed
            assertEquals("WI-42", parsedMessage.workItemId());
            assertEquals("source-agent", parsedMessage.fromAgent());
            assertEquals("", parsedMessage.toAgent()); // Not provided in A2A format
            assertEquals("", parsedMessage.token().engineSession()); // Not provided in A2A format
            assertEquals(Map.of("reason", "Document language not supported", "confidence", 0.95), parsedMessage.payload());
        }

        @Test
        @DisplayName("Parse A2A message without data part")
        void parseA2AMessageWithoutDataPart() throws HandoffException {
            // Given A2A message with only text part
            List<Map<String, Object>> a2aParts = Collections.singletonList(
                Map.of("type", "text", "text", "YAWL_HANDOFF:WI-42:source-agent")
            );

            // When parsing
            HandoffMessage parsedMessage = HandoffMessage.fromA2AMessage(a2aParts);

            // Then message should be parsed with empty payload
            assertEquals("WI-42", parsedMessage.workItemId());
            assertEquals("source-agent", parsedMessage.fromAgent());
            assertTrue(parsedMessage.payload().isEmpty(), "Payload should be empty");
        }

        @Test
        @DisplayName("Parse A2A message missing text part")
        void parseA2AMessageMissingTextPart() {
            // Given A2A message without text part
            List<Map<String, Object>> a2aParts = Collections.singletonList(
                Map.of("type", "data", "data", Map.of("key", "value"))
            );

            // When parsing
            assertThrows(HandoffException.class, () -> {
                HandoffMessage.fromA2AMessage(a2aParts);
            }, "Should throw exception for missing text part");
        }

        @Test
        @DisplayName("Parse A2A message with invalid format")
        void parseA2AMessageWithInvalidFormat() {
            // Given A2A message with invalid format
            List<Map<String, Object>> a2aParts = Collections.singletonList(
                Map.of("type", "text", "text", "INVALID_FORMAT")
            );

            // When parsing
            assertThrows(HandoffException.class, () -> {
                HandoffMessage.fromA2AMessage(a2aParts);
            }, "Should throw exception for invalid format");
        }
    }

    @Nested
    @DisplayName("JSON Conversion")
    class JSONConversionTests {

        @Test
        @DisplayName("Convert to JSON")
        void convertToJson() throws Exception {
            // Given a handoff message
            HandoffMessage message = basicMessage.withPayload(testPayload);

            // When converting to JSON
            String json = message.toJson();

            // Then JSON should be valid and contain message parts
            assertNotNull(json);
            assertTrue(json.contains("\"parts\""));
            assertTrue(json.contains("\"text\""));
            assertTrue(json.contains("\"YAWL_HANDOFF:" + TEST_WORK_ITEM_ID + ":" + TEST_FROM_AGENT + "\""));
        }

        @Test
        @DisplayName("Create from JSON with validation")
        void createFromJsonWithValidation() throws HandoffException {
            // Given JSON representation
            String json = "{\"parts\":[{\"type\":\"text\",\"text\":\"YAWL_HANDOFF:WI-42:source-agent\"},{\"type\":\"data\",\"data\":{\"key\":\"value\"}}]}";

            // When creating from JSON
            HandoffMessage message = HandoffMessage.fromJsonWithValidation(json);

            // Then message should be created and validated
            assertEquals("WI-42", message.workItemId());
            assertEquals("source-agent", message.fromAgent());
            assertEquals(Map.of("key", "value"), message.payload());
        }

        @Test
        @DisplayName("Create from invalid JSON throws exception")
        void createFromInvalidJsonThrowsException() {
            // Given invalid JSON
            String invalidJson = "invalid json string";

            // When creating from JSON
            assertThrows(HandoffException.class, () -> {
                HandoffMessage.fromJsonWithValidation(invalidJson);
            }, "Should throw exception for invalid JSON");
        }
    }

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        @DisplayName("Valid message validates successfully")
        void validMessageValidatesSuccessfully() throws HandoffException {
            // Given valid message
            // When validating
            assertDoesNotThrow(() -> {
                basicMessage.validate();
            }, "Valid message should validate successfully");
        }

        @Test
        @DisplayName("Valid message with business rules validates successfully")
        void validMessageWithBusinessRulesValidatesSuccessfully() throws HandoffException {
            // Given valid message with valid token
            // When validating with business rules
            assertDoesNotThrow(() -> {
                basicMessage.validateWithBusinessRules();
            }, "Valid message should validate with business rules");
        }

        @Test
        @DisplayName("Message with expired token fails business rule validation")
        void messageWithExpiredTokenFailsBusinessRuleValidation() throws InterruptedException {
            // Given message with expired token
            Instant past = Instant.now().minusSeconds(1);
            HandoffToken expiredToken = new HandoffToken(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                TEST_ENGINE_SESSION,
                past,
                TEST_JWT
            );

            HandoffMessage messageWithExpiredToken = new HandoffMessage(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                expiredToken,
                TEST_TIMESTAMP
            );

            // When validating with business rules
            assertThrows(HandoffException.class, () -> {
                messageWithExpiredToken.validateWithBusinessRules();
            }, "Message with expired token should fail business rule validation");
        }

        @Test
        @DisplayName("Message with same from and to agent fails business rule validation")
        void messageWithSameAgentIdsFailsBusinessRuleValidation() {
            // Given token with same from and to agent
            HandoffToken sameAgentToken = new HandoffToken(
                TEST_WORK_ITEM_ID,
                "same-agent",
                "same-agent",
                TEST_ENGINE_SESSION,
                testToken.expiresAt(),
                TEST_JWT
            );

            HandoffMessage sameAgentMessage = new HandoffMessage(
                TEST_WORK_ITEM_ID,
                "same-agent",
                "same-agent",
                sameAgentToken,
                TEST_TIMESTAMP
            );

            // When validating with business rules
            assertThrows(HandoffException.class, () -> {
                sameAgentMessage.validateWithBusinessRules();
            }, "Message with same from and to agent should fail business rule validation");
        }
    }

    @Nested
    @DisplayName("Payload Management")
    class PayloadManagementTests {

        @Test
        @DisplayName("Basic message has empty payload")
        void basicMessageHasEmptyPayload() {
            // Given basic message without payload
            // Then payload should be empty
            assertTrue(basicMessage.payload().isEmpty(), "Basic message should have empty payload");
        }

        @Test
        @DisplayName("Message with payload preserves payload")
        void messageWithPayloadPreservesPayload() {
            // Given message with payload
            HandoffMessage message = basicMessage.withPayload(testPayload);

            // Then payload should be preserved
            assertEquals(testPayload, message.payload());
        }

        @Test
        @DisplayName("withPayload creates new message with merged payload")
        void withPayloadCreatesNewMessageWithMergedPayload() {
            // Given message with existing payload
            HandoffMessage messageWithPayload = basicMessage.withPayload(Map.of("existing", "value"));

            // When adding new payload
            Map<String, Object> additionalPayload = Map.of(
                "priority", "high",
                "existing", "new-value"
            );

            HandoffMessage updatedMessage = messageWithPayload.withPayload(additionalPayload);

            // Then original message should be unchanged
            assertEquals(Map.of("existing", "value"), messageWithPayload.payload());

            // And new message should have merged payload
            Map<String, Object> expectedPayload = new HashMap<>();
            expectedPayload.put("existing", "new-value");
            expectedPayload.put("priority", "high");

            assertEquals(expectedPayload, updatedMessage.payload());
        }
    }

    @Nested
    @DisplayName("Expiration Handling")
    class ExpirationHandlingTests {

        @Test
        @DisplayName("Message with valid token is not expired")
        void messageWithValidTokenIsNotExpired() {
            // Given message with valid token
            // Then isExpired should return false
            assertFalse(basicMessage.isExpired(), "Message with valid token should not be expired");
        }

        @Test
        @DisplayName("Message with expired token is expired")
        void messageWithExpiredTokenIsExpired() throws InterruptedException {
            // Given message with expired token
            Instant past = Instant.now().minusSeconds(1);
            HandoffToken expiredToken = new HandoffToken(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                TEST_ENGINE_SESSION,
                past,
                TEST_JWT
            );

            HandoffMessage messageWithExpiredToken = new HandoffMessage(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                expiredToken,
                TEST_TIMESTAMP
            );

            // Then isExpired should return true
            assertTrue(messageWithExpiredToken.isExpired(), "Message with expired token should be expired");
        }

        @Test
        @DisplayName("timeToExpiry matches token's timeToExpiry")
        void timeToExpiryMatchesTokensTimeToExpiry() {
            // Given message
            // Then timeToExpiry should match token's timeToExpiry
            assertEquals(basicMessage.token().timeToExpiry(), basicMessage.timeToExpiry());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCasesTests {

        @Test
        @DisplayName("Message with very large payload")
        void messageWithLargePayload() {
            // Given message with large payload
            Map<String, Object> largePayload = new HashMap<>();
            largePayload.put("data", new String(new char[10000]).replace('\0', 'x'));
            largePayload.put("items", new String[1000]);

            HandoffMessage largeMessage = basicMessage.withPayload(largePayload);

            // Then large payload should be preserved
            assertEquals(largePayload, largeMessage.payload());
        }

        @Test
        @DisplayName("Message with special characters in fields")
        void messageWithSpecialCharactersInFields() {
            // Given message with special characters
            String specialWorkItemId = "WI-@#$%^&*()_+-={}[]|\\:;\"'<>,.?/~";
            String specialAgentId = "agent-@special.chars!123";

            HandoffToken specialToken = new HandoffToken(
                specialWorkItemId,
                specialAgentId,
                specialAgentId + "-target",
                TEST_ENGINE_SESSION,
                testToken.expiresAt(),
                TEST_JWT
            );

            HandoffMessage specialMessage = new HandoffMessage(
                specialWorkItemId,
                specialAgentId,
                specialAgentId + "-target",
                specialToken,
                TEST_TIMESTAMP,
                testPayload
            );

            // Then special characters should be preserved
            assertEquals(specialWorkItemId, specialMessage.workItemId());
            assertEquals(specialAgentId, specialMessage.fromAgent());
        }

        @Test
        @DisplayName("Message with Unicode characters")
        void messageWithUnicodeCharacters() {
            // Given message with Unicode characters
            String unicodeWorkItemId = "WI-中文-🚀-测试";
            String unicodeAgentId = "agent-日本語-🌍";

            HandoffToken unicodeToken = new HandoffToken(
                unicodeWorkItemId,
                unicodeAgentId,
                unicodeAgentId + "-target",
                TEST_ENGINE_SESSION,
                testToken.expiresAt(),
                TEST_JWT
            );

            HandoffMessage unicodeMessage = new HandoffMessage(
                unicodeWorkItemId,
                unicodeAgentId,
                unicodeAgentId + "-target",
                unicodeToken,
                TEST_TIMESTAMP
            );

            // Then Unicode characters should be preserved
            assertEquals(unicodeWorkItemId, unicodeMessage.workItemId());
            assertEquals(unicodeAgentId, unicodeMessage.fromAgent());
        }

        @Test
        @DisplayName("Message with null values in payload")
        void messageWithNullValuesInPayload() {
            // Given payload with null values
            Map<String, Object> payloadWithNulls = new HashMap<>();
            payloadWithNulls.put("valid", "value");
            payloadWithNulls.put("nullValue", null);

            // Should allow null values in payload
            assertDoesNotThrow(() -> {
                new HandoffMessage(
                    TEST_WORK_ITEM_ID,
                    TEST_FROM_AGENT,
                    TEST_TO_AGENT,
                    testToken,
                    payloadWithNulls,
                    TEST_TIMESTAMP
                );
            });
        }
    }

    @Nested
    @DisplayName("Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("Message is immutable - field access returns copies")
        void messageIsImmutable() {
            // Given message with mutable payload
            Map<String, Object> mutablePayload = new HashMap<>(testPayload);
            HandoffMessage message = new HandoffMessage(
                TEST_WORK_ITEM_ID,
                TEST_FROM_AGENT,
                TEST_TO_AGENT,
                testToken,
                mutablePayload,
                TEST_TIMESTAMP
            );

            // When modifying original payload
            mutablePayload.clear();

            // Then message payload should be unchanged
            assertEquals(testPayload, message.payload());
        }

        @Test
        @DisplayName("Message payload is immutable copy")
        void messagePayloadIsImmutableCopy() {
            // Given message
            HandoffMessage message = basicMessage.withPayload(testPayload);

            // When trying to modify returned payload
            assertThrows(UnsupportedOperationException.class, () -> {
                message.payload().put("test", "value");
            }, "Message payload should be immutable");
        }
    }
}