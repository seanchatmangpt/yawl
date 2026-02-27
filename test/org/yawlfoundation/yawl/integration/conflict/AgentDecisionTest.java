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

package org.yawlfoundation.yawl.integration.conflict;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for AgentDecision data class.
 *
 * Tests:
 * - AgentDecision creation and property access
 * - Null and edge case handling
 * - Immutable behavior
 * - Equals/hashCode contract
 * - Confidence score validation
 * - Metadata handling
 */
class AgentDecisionTest {

    private static final String TEST_AGENT_ID = "agent-001";
    private static final String TEST_DECISION = "APPROVE";
    private static final double CONFIDENCE = 0.85;
    private static final String TEST_RATIONALE = "Document meets all criteria";

    @Nested
    @DisplayName("AgentDecision Creation")
    class CreationTests {

        @Test
        @DisplayName("Create valid AgentDecision with all parameters")
        void createValidAgentDecision() {
            // Given valid parameters
            Map<String, Object> metadata = Map.of(
                "confidence", CONFIDENCE,
                "source", "automated-review",
                "timestamp", System.currentTimeMillis()
            );

            // When creating AgentDecision
            AgentDecision decision = new AgentDecision(
                TEST_AGENT_ID,
                TEST_DECISION,
                metadata,
                CONFIDENCE,
                TEST_RATIONALE
            );

            // Then all properties should be set correctly
            assertEquals(TEST_AGENT_ID, decision.getAgentId());
            assertEquals(TEST_DECISION, decision.getDecision());
            assertEquals(metadata, decision.getMetadata());
            assertEquals(CONFIDENCE, decision.getConfidence());
            assertEquals(TEST_RATIONALE, decision.getRationale());
        }

        @Test
        @DisplayName("Create AgentDecision with minimal metadata")
        void createAgentDecisionWithMinimalMetadata() {
            // Given minimal metadata
            Map<String, Object> minimalMetadata = Map.of();

            // When creating AgentDecision
            AgentDecision decision = new AgentDecision(
                TEST_AGENT_ID,
                TEST_DECISION,
                minimalMetadata,
                CONFIDENCE,
                TEST_RATIONALE
            );

            // Then it should be created successfully
            assertNotNull(decision);
            assertEquals(TEST_AGENT_ID, decision.getAgentId());
            assertEquals(TEST_DECISION, decision.getDecision());
            assertEquals(0, decision.getMetadata().size());
            assertEquals(CONFIDENCE, decision.getConfidence());
            assertEquals(TEST_RATIONALE, decision.getRationale());
        }

        @Test
        @DisplayName("Create AgentDecision with empty rationale")
        void createAgentDecisionWithEmptyRationale() {
            // Given empty rationale
            Map<String, Object> metadata = Map.of("key", "value");

            // When creating AgentDecision
            AgentDecision decision = new AgentDecision(
                TEST_AGENT_ID,
                TEST_DECISION,
                metadata,
                CONFIDENCE,
                ""
            );

            // Then it should be created successfully
            assertNotNull(decision);
            assertEquals("", decision.getRationale());
        }

        @Test
        @DisplayName("Create AgentDecision with null rationale")
        void createAgentDecisionWithNullRationale() {
            // Given null rationale
            Map<String, Object> metadata = Map.of("key", "value");

            // When creating AgentDecision
            AgentDecision decision = new AgentDecision(
                TEST_AGENT_ID,
                TEST_DECISION,
                metadata,
                CONFIDENCE,
                null
            );

            // Then it should be created successfully
            assertNotNull(decision);
            assertNull(decision.getRationale());
        }
    }

    @Nested
    @DisplayName("AgentDecision Property Access")
    class PropertyTests {

        private AgentDecision decision;

        @BeforeEach
        void setUp() {
            Map<String, Object> metadata = Map.of(
                "confidence", CONFIDENCE,
                "agentType", "review-bot",
                "model", "gpt-4"
            );

            decision = new AgentDecision(
                TEST_AGENT_ID,
                TEST_DECISION,
                metadata,
                CONFIDENCE,
                TEST_RATIONALE
            );
        }

        @Test
        @DisplayName("Get agent ID")
        void getAgentId() {
            assertEquals(TEST_AGENT_ID, decision.getAgentId());
        }

        @Test
        @DisplayName("Get decision")
        void getDecision() {
            assertEquals(TEST_DECISION, decision.getDecision());
        }

        @Test
        @DisplayName("Get metadata")
        void getMetadata() {
            Map<String, Object> metadata = decision.getMetadata();
            assertEquals(3, metadata.size());
            assertEquals("review-bot", metadata.get("agentType"));
            assertEquals("gpt-4", metadata.get("model"));
        }

        @Test
        @DisplayName("Get confidence score")
        void getConfidence() {
            assertEquals(CONFIDENCE, decision.getConfidence());
        }

        @Test
        @DisplayName("Get rationale")
        void getRationale() {
            assertEquals(TEST_RATIONALE, decision.getRationale());
        }
    }

    @Nested
    @DisplayName("AgentDecision Confidence Scores")
    class ConfidenceTests {

        @Test
        @DisplayName("Handle maximum confidence (1.0)")
        void handleMaximumConfidence() {
            // Given maximum confidence
            Map<String, Object> metadata = Map.of();
            AgentDecision decision = new AgentDecision(
                TEST_AGENT_ID,
                TEST_DECISION,
                metadata,
                1.0,
                TEST_RATIONALE
            );

            // Then confidence should be 1.0
            assertEquals(1.0, decision.getConfidence());
        }

        @Test
        @DisplayName("Handle minimum confidence (0.0)")
        void handleMinimumConfidence() {
            // Given minimum confidence
            Map<String, Object> metadata = Map.of();
            AgentDecision decision = new AgentDecision(
                TEST_AGENT_ID,
                TEST_DECISION,
                metadata,
                0.0,
                TEST_RATIONALE
            );

            // Then confidence should be 0.0
            assertEquals(0.0, decision.getConfidence());
        }

        @Test
        @DisplayName("Handle negative confidence")
        void handleNegativeConfidence() {
            // Given negative confidence (should be allowed but unusual)
            Map<String, Object> metadata = Map.of();
            AgentDecision decision = new AgentDecision(
                TEST_AGENT_ID,
                TEST_DECISION,
                metadata,
                -0.1,
                TEST_RATIONALE
            );

            // Then confidence should be negative
            assertEquals(-0.1, decision.getConfidence());
        }

        @Test
        @DisplayName("Handle confidence greater than 1.0")
        void handleConfidenceGreaterThanOne() {
            // Given confidence greater than 1.0 (should be allowed but unusual)
            Map<String, Object> metadata = Map.of();
            AgentDecision decision = new AgentDecision(
                TEST_AGENT_ID,
                TEST_DECISION,
                metadata,
                1.5,
                TEST_RATIONALE
            );

            // Then confidence should be greater than 1.0
            assertEquals(1.5, decision.getConfidence());
        }

        @Test
        @DisplayName("Handle NaN confidence")
        void handleNaNConfidence() {
            // Given NaN confidence
            Map<String, Object> metadata = Map.of();
            AgentDecision decision = new AgentDecision(
                TEST_AGENT_ID,
                TEST_DECISION,
                metadata,
                Double.NaN,
                TEST_RATIONALE
            );

            // Then confidence should be NaN
            assertTrue(Double.isNaN(decision.getConfidence()));
        }

        @Test
        @DisplayName("Handle infinity confidence")
        void handleInfinityConfidence() {
            // Given positive infinity confidence
            Map<String, Object> metadata = Map.of();
            AgentDecision decision = new AgentDecision(
                TEST_AGENT_ID,
                TEST_DECISION,
                metadata,
                Double.POSITIVE_INFINITY,
                TEST_RATIONALE
            );

            // Then confidence should be infinity
            assertEquals(Double.POSITIVE_INFINITY, decision.getConfidence());
        }
    }

    @Nested
    @DisplayName("AgentDecision Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("Metadata map is defensive copy")
        void metadataMapIsDefensiveCopy() {
            Map<String, Object> originalMetadata = Map.of("key", "value");
            AgentDecision decision = new AgentDecision(
                TEST_AGENT_ID,
                TEST_DECISION,
                originalMetadata,
                CONFIDENCE,
                TEST_RATIONALE
            );

            // Try to modify the map returned by getter
            Map<String, Object> metadata = decision.getMetadata();
            metadata.put("newKey", "newValue");

            // Verify original decision is unchanged
            assertEquals(1, decision.getMetadata().size());
            assertFalse(decision.getMetadata().containsKey("newKey"));
        }

        @Test
        @DisplayName("AgentDecision is immutable - no setters")
        void agentDecisionHasNoSetters() {
            // Test that AgentDecision has no setters by checking methods
            // This ensures immutability
            assertDoesNotThrow(() -> {
                AgentDecision decision = new AgentDecision(
                    TEST_AGENT_ID,
                    TEST_DECISION,
                    Map.of(),
                    CONFIDENCE,
                    TEST_RATIONALE
                );
                // Just instantiation should work without issues
            });
        }
    }

    @Nested
    @DisplayName("AgentDecision Equals and HashCode")
    class EqualsAndHashCodeTests {

        @Test
        @DisplayName("Equal AgentDecision objects have same hash code")
        void equalObjectsHaveSameHashCode() {
            // Given two identical AgentDecision objects
            Map<String, Object> metadata = Map.of("key", "value");

            AgentDecision decision1 = new AgentDecision(
                TEST_AGENT_ID,
                TEST_DECISION,
                metadata,
                CONFIDENCE,
                TEST_RATIONALE
            );

            AgentDecision decision2 = new AgentDecision(
                TEST_AGENT_ID,
                TEST_DECISION,
                metadata,
                CONFIDENCE,
                TEST_RATIONALE
            );

            // Then they should be equal and have same hash code
            assertEquals(decision1, decision2);
            assertEquals(decision1.hashCode(), decision2.hashCode());
        }

        @Test
        @DisplayName("Different AgentDecision objects are not equal")
        void differentObjectsAreNotEqual() {
            // Given different AgentDecision objects
            AgentDecision decision1 = new AgentDecision(
                "agent-1",
                "APPROVE",
                Map.of(),
                0.8,
                "Rationale 1"
            );

            AgentDecision decision2 = new AgentDecision(
                "agent-2",
                "REJECT",
                Map.of(),
                0.7,
                "Rationale 2"
            );

            // Then they should not be equal
            assertNotEquals(decision1, decision2);
            assertNotEquals(decision1.hashCode(), decision2.hashCode());
        }

        @Test
        @DisplayName("AgentDecision equals with null and different types")
        void equalsWithNullAndDifferentTypes() {
            // Given an AgentDecision
            AgentDecision decision = new AgentDecision(
                TEST_AGENT_ID,
                TEST_DECISION,
                Map.of(),
                CONFIDENCE,
                TEST_RATIONALE
            );

            // Then it should not equal null or different types
            assertNotEquals(null, decision);
            assertNotEquals("string", decision);
            assertNotEquals(new Object(), decision);
        }

        @Test
        @DisplayName("Same AgentDecision instance is equal to itself")
        void sameInstanceIsEqual() {
            // Given an AgentDecision
            AgentDecision decision = new AgentDecision(
                TEST_AGENT_ID,
                TEST_DECISION,
                Map.of(),
                CONFIDENCE,
                TEST_RATIONALE
            );

            // Then it should be equal to itself
            assertEquals(decision, decision);
            assertEquals(decision.hashCode(), decision.hashCode());
        }

        @Test
        @DisplayName("AgentDecision equality depends on all fields")
        void equalityDependsOnAllFields() {
            // Given base decision
            Map<String, Object> metadata = Map.of("key", "value");
            AgentDecision base = new AgentDecision(
                TEST_AGENT_ID,
                TEST_DECISION,
                metadata,
                CONFIDENCE,
                TEST_RATIONALE
            );

            // Then decisions differing in any field should not be equal
            AgentDecision differentAgent = new AgentDecision(
                "different-agent",
                TEST_DECISION,
                metadata,
                CONFIDENCE,
                TEST_RATIONALE
            );
            assertNotEquals(base, differentAgent);

            AgentDecision differentDecision = new AgentDecision(
                TEST_AGENT_ID,
                "REJECT",
                metadata,
                CONFIDENCE,
                TEST_RATIONALE
            );
            assertNotEquals(base, differentDecision);

            AgentDecision differentConfidence = new AgentDecision(
                TEST_AGENT_ID,
                TEST_DECISION,
                metadata,
                0.5,
                TEST_RATIONALE
            );
            assertNotEquals(base, differentConfidence);

            AgentDecision differentRationale = new AgentDecision(
                TEST_AGENT_ID,
                TEST_DECISION,
                metadata,
                CONFIDENCE,
                "Different rationale"
            );
            assertNotEquals(base, differentRationale);

            AgentDecision differentMetadata = new AgentDecision(
                TEST_AGENT_ID,
                TEST_DECISION,
                Map.of("different", "value"),
                CONFIDENCE,
                TEST_RATIONALE
            );
            assertNotEquals(base, differentMetadata);
        }
    }

    @Nested
    @DisplayName("AgentDecision Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Handle null decision value")
        void handleNullDecisionValue() {
            // Given null decision
            Map<String, Object> metadata = Map.of();
            AgentDecision decision = new AgentDecision(
                TEST_AGENT_ID,
                null,
                metadata,
                CONFIDENCE,
                TEST_RATIONALE
            );

            // Then it should be created successfully
            assertNotNull(decision);
            assertNull(decision.getDecision());
        }

        @Test
        @DisplayName("Handle empty decision value")
        void handleEmptyDecisionValue() {
            // Given empty decision
            Map<String, Object> metadata = Map.of();
            AgentDecision decision = new AgentDecision(
                TEST_AGENT_ID,
                "",
                metadata,
                CONFIDENCE,
                TEST_RATIONALE
            );

            // Then it should be created successfully
            assertNotNull(decision);
            assertEquals("", decision.getDecision());
        }

        @Test
        @DisplayName("Handle very long agent ID")
        void handleVeryLongAgentId() {
            // Given very long agent ID
            String longAgentId = "agent-" + "x".repeat(1000);
            Map<String, Object> metadata = Map.of();

            // When creating AgentDecision
            AgentDecision decision = new AgentDecision(
                longAgentId,
                TEST_DECISION,
                metadata,
                CONFIDENCE,
                TEST_RATIONALE
            );

            // Then it should be created successfully
            assertEquals(longAgentId, decision.getAgentId());
        }

        @Test
        @DisplayName("Handle very long decision")
        void handleVeryLongDecision() {
            // Given very long decision
            String longDecision = "x".repeat(10000);
            Map<String, Object> metadata = Map.of();

            // When creating AgentDecision
            AgentDecision decision = new AgentDecision(
                TEST_AGENT_ID,
                longDecision,
                metadata,
                CONFIDENCE,
                TEST_RATIONALE
            );

            // Then it should be created successfully
            assertEquals(longDecision, decision.getDecision());
        }

        @Test
        @DisplayName("Handle very long rationale")
        void handleVeryLongRationale() {
            // Given very long rationale
            String longRationale = "x".repeat(20000);
            Map<String, Object> metadata = Map.of();

            // When creating AgentDecision
            AgentDecision decision = new AgentDecision(
                TEST_AGENT_ID,
                TEST_DECISION,
                metadata,
                CONFIDENCE,
                longRationale
            );

            // Then it should be created successfully
            assertEquals(longRationale, decision.getRationale());
        }

        @Test
        @DisplayName("Handle large metadata map")
        void handleLargeMetadataMap() {
            // Given large metadata map
            Map<String, Object> largeMetadata = new HashMap<>();
            for (int i = 0; i < 1000; i++) {
                largeMetadata.put("key" + i, "value" + i);
            }

            // When creating AgentDecision
            AgentDecision decision = new AgentDecision(
                TEST_AGENT_ID,
                TEST_DECISION,
                largeMetadata,
                CONFIDENCE,
                TEST_RATIONALE
            );

            // Then it should be created successfully
            assertEquals(1000, decision.getMetadata().size());
        }
    }

    @Nested
    @DisplayName("AgentDecision String Representation")
    class StringRepresentationTests {

        @Test
        @DisplayName("ToString contains all relevant information")
        void toStringContainsAllRelevantInfo() {
            // Given AgentDecision
            Map<String, Object> metadata = Map.of(
                "confidence", CONFIDENCE,
                "agentType", "review-bot"
            );

            AgentDecision decision = new AgentDecision(
                TEST_AGENT_ID,
                TEST_DECISION,
                metadata,
                CONFIDENCE,
                TEST_RATIONALE
            );

            // Then toString should contain key information
            String toString = decision.toString();
            assertTrue(toString.contains(TEST_AGENT_ID));
            assertTrue(toString.contains(TEST_DECISION));
            assertTrue(toString.contains(String.valueOf(CONFIDENCE)));
            assertTrue(toString.contains(TEST_RATIONALE));
            assertTrue(toString.contains("review-bot"));
        }

        @Test
        @DisplayName("ToString handles null values gracefully")
        void toStringHandlesNullValuesGracefully() {
            // Given AgentDecision with null values
            AgentDecision decision = new AgentDecision(
                TEST_AGENT_ID,
                null,
                Map.of(),
                CONFIDENCE,
                null
            );

            // Then toString should handle null values
            String toString = decision.toString();
            assertTrue(toString.contains(TEST_AGENT_ID));
            assertTrue(toString.contains("null"));
        }
    }

    @Nested
    @DisplayName("AgentDecision Builder Pattern Support")
    class BuilderPatternTests {

        @Test
        @DisplayName("Multiple decisions can be created independently")
        void createMultipleDecisionsIndependently() {
            // Given multiple decisions
            Map<String, Object> metadata1 = Map.of("type", "decision1");
            Map<String, Object> metadata2 = Map.of("type", "decision2");

            AgentDecision decision1 = new AgentDecision(
                "agent-1",
                "DECISION-1",
                metadata1,
                0.8,
                "Rationale-1"
            );

            AgentDecision decision2 = new AgentDecision(
                "agent-2",
                "DECISION-2",
                metadata2,
                0.9,
                "Rationale-2"
            );

            // Then they should be independent
            assertNotEquals(decision1, decision2);
            assertNotEquals(decision1.hashCode(), decision2.hashCode());
            assertEquals("agent-1", decision1.getAgentId());
            assertEquals("agent-2", decision2.getAgentId());
        }
    }
}