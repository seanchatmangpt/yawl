/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
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
 * You should have received a copy of the GNU Lesser General
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Security tests for Jackson deserialization of sealed UpgradeOutcome class hierarchy.
 *
 * <p>Verifies that:</p>
 * <ul>
 *   <li>All permitted subtypes can be deserialized correctly</li>
 *   <li>Unknown types throw JsonMappingException (not silently instantiated)</li>
 *   <li>The sealed class constraints are enforced at deserialization time</li>
 *   <li>Polymorphic type information is properly validated</li>
 *   <li>Deserialized instances are correctly typed and functional</li>
 * </ul>
 *
 * <h2>Pattern Tested</h2>
 * <pre>
 * @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@type")
 * @JsonSubTypes({
 *     @JsonSubTypes.Type(value = Success.class, name = "success"),
 *     @JsonSubTypes.Type(value = Failure.class, name = "failure"),
 *     @JsonSubTypes.Type(value = Partial.class, name = "partial"),
 *     @JsonSubTypes.Type(value = InProgress.class, name = "inProgress")
 * })
 * sealed interface UpgradeOutcome permits Success, Failure, Partial, InProgress
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@DisplayName("UpgradeOutcome Jackson Deserialization Security Tests")
class UpgradeOutcomeDeserializationSecurityTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Create ObjectMapper using the same configuration as UpgradeMemoryStore
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
    }

    /**
     * Tests for deserialization of each permitted subtype.
     */
    @Nested
    @DisplayName("Permitted Subtype Deserialization Tests")
    class PermittedSubtypeTests {

        @Test
        @DisplayName("Deserialize Success outcome from JSON")
        void testDeserializeSuccessOutcome() throws JsonProcessingException {
            // Arrange
            String json = """
                    {
                        "@type": "success",
                        "message": "Upgrade completed successfully"
                    }
                    """;

            // Act
            UpgradeMemoryStore.UpgradeOutcome outcome = objectMapper
                    .readValue(json, UpgradeMemoryStore.UpgradeOutcome.class);

            // Assert
            assertNotNull(outcome);
            assertInstanceOf(UpgradeMemoryStore.Success.class, outcome);
            assertTrue(outcome.isSuccessful());
            assertEquals("SUCCESS: Upgrade completed successfully", outcome.description());

            UpgradeMemoryStore.Success success = (UpgradeMemoryStore.Success) outcome;
            assertEquals("Upgrade completed successfully", success.message());
        }

        @Test
        @DisplayName("Deserialize Failure outcome from JSON")
        void testDeserializeFailureOutcome() throws JsonProcessingException {
            // Arrange
            String json = """
                    {
                        "@type": "failure",
                        "errorMessage": "Compilation failed: cannot find symbol",
                        "errorType": "CompileError",
                        "stackTrace": "at java.lang.Thread.run(Thread.java:1234)"
                    }
                    """;

            // Act
            UpgradeMemoryStore.UpgradeOutcome outcome = objectMapper
                    .readValue(json, UpgradeMemoryStore.UpgradeOutcome.class);

            // Assert
            assertNotNull(outcome);
            assertInstanceOf(UpgradeMemoryStore.Failure.class, outcome);
            assertTrue(!outcome.isSuccessful());
            assertTrue(outcome.description().contains("CompileError"));

            UpgradeMemoryStore.Failure failure = (UpgradeMemoryStore.Failure) outcome;
            assertEquals("Compilation failed: cannot find symbol", failure.errorMessage());
            assertEquals("CompileError", failure.errorType());
            assertEquals("at java.lang.Thread.run(Thread.java:1234)", failure.stackTrace());
        }

        @Test
        @DisplayName("Deserialize Partial outcome from JSON")
        void testDeserializePartialOutcome() throws JsonProcessingException {
            // Arrange
            String json = """
                    {
                        "@type": "partial",
                        "completedPhases": 2,
                        "totalPhases": 3,
                        "lastCompletedPhase": "test"
                    }
                    """;

            // Act
            UpgradeMemoryStore.UpgradeOutcome outcome = objectMapper
                    .readValue(json, UpgradeMemoryStore.UpgradeOutcome.class);

            // Assert
            assertNotNull(outcome);
            assertInstanceOf(UpgradeMemoryStore.Partial.class, outcome);
            assertTrue(!outcome.isSuccessful());
            assertTrue(outcome.description().contains("2/3"));

            UpgradeMemoryStore.Partial partial = (UpgradeMemoryStore.Partial) outcome;
            assertEquals(2, partial.completedPhases());
            assertEquals(3, partial.totalPhases());
            assertEquals("test", partial.lastCompletedPhase());
        }

        @Test
        @DisplayName("Deserialize InProgress outcome from JSON")
        void testDeserializeInProgressOutcome() throws JsonProcessingException {
            // Arrange
            String json = """
                    {
                        "@type": "inProgress",
                        "currentPhase": "validation",
                        "progressPercent": 75.5
                    }
                    """;

            // Act
            UpgradeMemoryStore.UpgradeOutcome outcome = objectMapper
                    .readValue(json, UpgradeMemoryStore.UpgradeOutcome.class);

            // Assert
            assertNotNull(outcome);
            assertInstanceOf(UpgradeMemoryStore.InProgress.class, outcome);
            assertTrue(!outcome.isSuccessful());
            assertTrue(outcome.description().contains("75.5"));

            UpgradeMemoryStore.InProgress inProgress = (UpgradeMemoryStore.InProgress) outcome;
            assertEquals("validation", inProgress.currentPhase());
            assertEquals(75.5, inProgress.progressPercent());
        }

        @Test
        @DisplayName("Deserialize Success with empty message")
        void testDeserializeSuccessWithEmptyMessage() throws JsonProcessingException {
            // Arrange
            String json = """
                    {
                        "@type": "success"
                    }
                    """;

            // Act
            UpgradeMemoryStore.UpgradeOutcome outcome = objectMapper
                    .readValue(json, UpgradeMemoryStore.UpgradeOutcome.class);

            // Assert
            assertNotNull(outcome);
            assertInstanceOf(UpgradeMemoryStore.Success.class, outcome);
            UpgradeMemoryStore.Success success = (UpgradeMemoryStore.Success) outcome;
            assertEquals("", success.message());
        }

        @Test
        @DisplayName("Deserialize Failure with default error type")
        void testDeserializeFailureWithDefaults() throws JsonProcessingException {
            // Arrange
            String json = """
                    {
                        "@type": "failure"
                    }
                    """;

            // Act
            UpgradeMemoryStore.UpgradeOutcome outcome = objectMapper
                    .readValue(json, UpgradeMemoryStore.UpgradeOutcome.class);

            // Assert
            assertNotNull(outcome);
            assertInstanceOf(UpgradeMemoryStore.Failure.class, outcome);
            UpgradeMemoryStore.Failure failure = (UpgradeMemoryStore.Failure) outcome;
            assertEquals("Unknown error", failure.errorMessage());
            assertEquals("Unknown", failure.errorType());
            assertEquals("", failure.stackTrace());
        }

        @Test
        @DisplayName("Deserialize InProgress with default progress")
        void testDeserializeInProgressWithDefaults() throws JsonProcessingException {
            // Arrange
            String json = """
                    {
                        "@type": "inProgress",
                        "progressPercent": -50.0
                    }
                    """;

            // Act
            UpgradeMemoryStore.UpgradeOutcome outcome = objectMapper
                    .readValue(json, UpgradeMemoryStore.UpgradeOutcome.class);

            // Assert
            assertNotNull(outcome);
            assertInstanceOf(UpgradeMemoryStore.InProgress.class, outcome);
            UpgradeMemoryStore.InProgress inProgress = (UpgradeMemoryStore.InProgress) outcome;
            // Negative progress should be clamped to 0.0
            assertEquals(0.0, inProgress.progressPercent());
        }

        @Test
        @DisplayName("Deserialize InProgress with overflow progress")
        void testDeserializeInProgressWithOverflowProgress() throws JsonProcessingException {
            // Arrange
            String json = """
                    {
                        "@type": "inProgress",
                        "progressPercent": 150.0
                    }
                    """;

            // Act
            UpgradeMemoryStore.UpgradeOutcome outcome = objectMapper
                    .readValue(json, UpgradeMemoryStore.UpgradeOutcome.class);

            // Assert
            assertNotNull(outcome);
            assertInstanceOf(UpgradeMemoryStore.InProgress.class, outcome);
            UpgradeMemoryStore.InProgress inProgress = (UpgradeMemoryStore.InProgress) outcome;
            // Progress > 100 should be clamped to 100.0
            assertEquals(100.0, inProgress.progressPercent());
        }
    }

    /**
     * Tests for rejection of unknown/malicious types.
     */
    @Nested
    @DisplayName("Unknown Type Rejection Tests (Security)")
    class UnknownTypeRejectionTests {

        @Test
        @DisplayName("Reject unknown type: 'malicious'")
        void testRejectUnknownTypeMalicious() {
            // Arrange
            String json = """
                    {
                        "@type": "malicious",
                        "payload": "evil code"
                    }
                    """;

            // Act & Assert
            assertThrows(JsonMappingException.class, () ->
                    objectMapper.readValue(json, UpgradeMemoryStore.UpgradeOutcome.class),
                "Unknown type 'malicious' should throw JsonMappingException"
            );
        }

        @Test
        @DisplayName("Reject unknown type: 'custom'")
        void testRejectUnknownTypeCustom() {
            // Arrange
            String json = """
                    {
                        "@type": "custom",
                        "data": "something"
                    }
                    """;

            // Act & Assert
            assertThrows(JsonMappingException.class, () ->
                    objectMapper.readValue(json, UpgradeMemoryStore.UpgradeOutcome.class),
                "Unknown type 'custom' should throw JsonMappingException"
            );
        }

        @Test
        @DisplayName("Reject unknown type: 'Success' (case-sensitive)")
        void testRejectCaseSensitiveTypeName() {
            // Arrange
            String json = """
                    {
                        "@type": "Success",
                        "message": "test"
                    }
                    """;

            // Act & Assert
            assertThrows(JsonMappingException.class, () ->
                    objectMapper.readValue(json, UpgradeMemoryStore.UpgradeOutcome.class),
                "Type name is case-sensitive; 'Success' != 'success'"
            );
        }

        @Test
        @DisplayName("Reject type with typo: 'succes' instead of 'success'")
        void testRejectTypoInTypeName() {
            // Arrange
            String json = """
                    {
                        "@type": "succes",
                        "message": "test"
                    }
                    """;

            // Act & Assert
            assertThrows(JsonMappingException.class, () ->
                    objectMapper.readValue(json, UpgradeMemoryStore.UpgradeOutcome.class),
                "Typo in type name should throw JsonMappingException"
            );
        }

        @Test
        @DisplayName("Reject missing @type property")
        void testRejectMissingTypeProperty() {
            // Arrange
            String json = """
                    {
                        "message": "test"
                    }
                    """;

            // Act & Assert
            assertThrows(JsonMappingException.class, () ->
                    objectMapper.readValue(json, UpgradeMemoryStore.UpgradeOutcome.class),
                "Missing @type property should throw JsonMappingException"
            );
        }

        @Test
        @DisplayName("Reject null @type property")
        void testRejectNullTypeProperty() {
            // Arrange
            String json = """
                    {
                        "@type": null,
                        "message": "test"
                    }
                    """;

            // Act & Assert
            assertThrows(JsonMappingException.class, () ->
                    objectMapper.readValue(json, UpgradeMemoryStore.UpgradeOutcome.class),
                "Null @type property should throw JsonMappingException"
            );
        }

        @Test
        @DisplayName("Reject empty @type property")
        void testRejectEmptyTypeProperty() {
            // Arrange
            String json = """
                    {
                        "@type": "",
                        "message": "test"
                    }
                    """;

            // Act & Assert
            assertThrows(JsonMappingException.class, () ->
                    objectMapper.readValue(json, UpgradeMemoryStore.UpgradeOutcome.class),
                "Empty @type property should throw JsonMappingException"
            );
        }

        @Test
        @DisplayName("Reject integer @type property")
        void testRejectIntegerTypeProperty() {
            // Arrange
            String json = """
                    {
                        "@type": 123,
                        "message": "test"
                    }
                    """;

            // Act & Assert
            assertThrows(JsonMappingException.class, () ->
                    objectMapper.readValue(json, UpgradeMemoryStore.UpgradeOutcome.class),
                "Non-string @type property should throw JsonMappingException"
            );
        }
    }

    /**
     * Tests for serialization round-trip (serialization then deserialization).
     */
    @Nested
    @DisplayName("Serialization Round-Trip Tests")
    class RoundTripTests {

        @Test
        @DisplayName("Round-trip serialize/deserialize Success")
        void testRoundTripSuccess() throws JsonProcessingException {
            // Arrange
            UpgradeMemoryStore.Success original = new UpgradeMemoryStore.Success("Test message");

            // Act
            String json = objectMapper.writeValueAsString(original);
            UpgradeMemoryStore.UpgradeOutcome deserialized = objectMapper
                    .readValue(json, UpgradeMemoryStore.UpgradeOutcome.class);

            // Assert
            assertInstanceOf(UpgradeMemoryStore.Success.class, deserialized);
            assertEquals(original, deserialized);
        }

        @Test
        @DisplayName("Round-trip serialize/deserialize Failure")
        void testRoundTripFailure() throws JsonProcessingException {
            // Arrange
            UpgradeMemoryStore.Failure original = new UpgradeMemoryStore.Failure(
                    "Test error",
                    "TestError",
                    "trace"
            );

            // Act
            String json = objectMapper.writeValueAsString(original);
            UpgradeMemoryStore.UpgradeOutcome deserialized = objectMapper
                    .readValue(json, UpgradeMemoryStore.UpgradeOutcome.class);

            // Assert
            assertInstanceOf(UpgradeMemoryStore.Failure.class, deserialized);
            assertEquals(original, deserialized);
        }

        @Test
        @DisplayName("Round-trip serialize/deserialize Partial")
        void testRoundTripPartial() throws JsonProcessingException {
            // Arrange
            UpgradeMemoryStore.Partial original = new UpgradeMemoryStore.Partial(2, 3, "validate");

            // Act
            String json = objectMapper.writeValueAsString(original);
            UpgradeMemoryStore.UpgradeOutcome deserialized = objectMapper
                    .readValue(json, UpgradeMemoryStore.UpgradeOutcome.class);

            // Assert
            assertInstanceOf(UpgradeMemoryStore.Partial.class, deserialized);
            assertEquals(original, deserialized);
        }

        @Test
        @DisplayName("Round-trip serialize/deserialize InProgress")
        void testRoundTripInProgress() throws JsonProcessingException {
            // Arrange
            UpgradeMemoryStore.InProgress original = new UpgradeMemoryStore.InProgress("compile", 50.0);

            // Act
            String json = objectMapper.writeValueAsString(original);
            UpgradeMemoryStore.UpgradeOutcome deserialized = objectMapper
                    .readValue(json, UpgradeMemoryStore.UpgradeOutcome.class);

            // Assert
            assertInstanceOf(UpgradeMemoryStore.InProgress.class, deserialized);
            assertEquals(original, deserialized);
        }
    }

    /**
     * Tests for UpgradeRecord with polymorphic outcomes.
     */
    @Nested
    @DisplayName("UpgradeRecord with Polymorphic Outcomes Tests")
    class UpgradeRecordTests {

        @Test
        @DisplayName("Deserialize UpgradeRecord with Success outcome")
        void testDeserializeRecordWithSuccessOutcome() throws JsonProcessingException {
            // Arrange
            String json = """
                    {
                        "id": "test-123",
                        "sessionId": "session-456",
                        "targetVersion": "6.0.0",
                        "sourceVersion": "5.0.0",
                        "phases": [],
                        "agents": {},
                        "startTime": "2026-02-20T10:00:00Z",
                        "endTime": "2026-02-20T10:05:00Z",
                        "outcome": {
                            "@type": "success",
                            "message": "Upgraded successfully"
                        },
                        "metadata": {}
                    }
                    """;

            // Act
            UpgradeMemoryStore.UpgradeRecord record = objectMapper
                    .readValue(json, UpgradeMemoryStore.UpgradeRecord.class);

            // Assert
            assertNotNull(record);
            assertEquals("test-123", record.id());
            assertInstanceOf(UpgradeMemoryStore.Success.class, record.outcome());
            assertTrue(record.isSuccessful());
        }

        @Test
        @DisplayName("Deserialize UpgradeRecord with Failure outcome")
        void testDeserializeRecordWithFailureOutcome() throws JsonProcessingException {
            // Arrange
            String json = """
                    {
                        "id": "test-789",
                        "sessionId": "session-012",
                        "targetVersion": "6.0.0",
                        "sourceVersion": "5.0.0",
                        "phases": [],
                        "agents": {},
                        "startTime": "2026-02-20T10:00:00Z",
                        "endTime": "2026-02-20T10:05:00Z",
                        "outcome": {
                            "@type": "failure",
                            "errorMessage": "Compilation failed",
                            "errorType": "CompileError",
                            "stackTrace": ""
                        },
                        "metadata": {}
                    }
                    """;

            // Act
            UpgradeMemoryStore.UpgradeRecord record = objectMapper
                    .readValue(json, UpgradeMemoryStore.UpgradeRecord.class);

            // Assert
            assertNotNull(record);
            assertEquals("test-789", record.id());
            assertInstanceOf(UpgradeMemoryStore.Failure.class, record.outcome());
            assertTrue(!record.isSuccessful());
        }

        @Test
        @DisplayName("Reject UpgradeRecord with unknown outcome type")
        void testRejectRecordWithUnknownOutcomeType() {
            // Arrange
            String json = """
                    {
                        "id": "test-bad",
                        "sessionId": "session-bad",
                        "targetVersion": "6.0.0",
                        "sourceVersion": "5.0.0",
                        "phases": [],
                        "agents": {},
                        "startTime": "2026-02-20T10:00:00Z",
                        "outcome": {
                            "@type": "unknown",
                            "payload": "malicious"
                        },
                        "metadata": {}
                    }
                    """;

            // Act & Assert
            assertThrows(JsonMappingException.class, () ->
                    objectMapper.readValue(json, UpgradeMemoryStore.UpgradeRecord.class),
                "UpgradeRecord with unknown outcome type should throw JsonMappingException"
            );
        }
    }
}
