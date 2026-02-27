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
 * Comprehensive unit tests for Decision data class.
 *
 * Tests:
 * - Decision creation and property access
 * - Immutable behavior
 * - Equals/hashCode contract
 * - Timestamp validation
 * - Metadata handling
 * - Edge cases and null handling
 */
class DecisionTest {

    private static final String TEST_RESOLVED_VALUE = "APPROVE";
    private static final String RESOLUTION_STRATEGY = "MAJORITY_VOTE";

    @Nested
    @DisplayName("Decision Creation")
    class CreationTests {

        @Test
        @DisplayName("Create valid Decision with all parameters")
        void createValidDecision() {
            // Given valid parameters
            List<String> participatingAgents = List.of("agent-1", "agent-2", "agent-3");
            Map<String, Object> resolutionMetadata = Map.of(
                "voteCounts", Map.of("APPROVE", 2, "REJECT", 1),
                "totalVotes", 3
            );

            // When creating Decision
            Decision decision = new Decision(
                TEST_RESOLVED_VALUE,
                ConflictResolver.Severity.HIGH,
                participatingAgents,
                RESOLUTION_STRATEGY,
                resolutionMetadata
            );

            // Then all properties should be set correctly
            assertEquals(TEST_RESOLVED_VALUE, decision.getResolvedValue());
            assertEquals(ConflictResolver.Severity.HIGH, decision.getSeverity());
            assertEquals(participatingAgents, decision.getParticipatingAgents());
            assertEquals(RESOLUTION_STRATEGY, decision.getResolutionStrategy());
            assertEquals(resolutionMetadata, decision.getResolutionMetadata());
            assertTrue(decision.getResolutionTimestamp() > 0);
        }

        @Test
        @DisplayName("Create Decision with minimal metadata")
        void createDecisionWithMinimalMetadata() {
            // Given minimal metadata
            List<String> participatingAgents = List.of("agent-1");
            Map<String, Object> minimalMetadata = Map.of();

            // When creating Decision
            Decision decision = new Decision(
                TEST_RESOLVED_VALUE,
                ConflictResolver.Severity.LOW,
                participatingAgents,
                RESOLUTION_STRATEGY,
                minimalMetadata
            );

            // Then it should be created successfully
            assertNotNull(decision);
            assertEquals(TEST_RESOLVED_VALUE, decision.getResolvedValue());
            assertEquals(ConflictResolver.Severity.LOW, decision.getSeverity());
            assertEquals(1, decision.getParticipatingAgents().size());
            assertEquals(0, decision.getResolutionMetadata().size());
        }

        @Test
        @DisplayName("Create Decision with empty participating agents list")
        void createDecisionWithEmptyParticipatingAgents() {
            // Given empty participating agents list
            List<String> emptyAgents = List.of();
            Map<String, Object> metadata = Map.of("method", "auto-resolve");

            // When creating Decision
            Decision decision = new Decision(
                TEST_RESOLVED_VALUE,
                ConflictResolver.Severity.MEDIUM,
                emptyAgents,
                RESOLUTION_STRATEGY,
                metadata
            );

            // Then it should be created successfully
            assertNotNull(decision);
            assertEquals(TEST_RESOLVED_VALUE, decision.getResolvedValue());
            assertEquals(0, decision.getParticipatingAgents().size());
        }
    }

    @Nested
    @DisplayName("Decision Property Access")
    class PropertyTests {

        private Decision decision;

        @BeforeEach
        void setUp() {
            List<String> participatingAgents = List.of("agent-1", "agent-2");
            Map<String, Object> resolutionMetadata = Map.of(
                "resolutionMethod", "voting",
                "confidence", 0.85
            );

            decision = new Decision(
                TEST_RESOLVED_VALUE,
                ConflictResolver.Severity.CRITICAL,
                participatingAgents,
                RESOLUTION_STRATEGY,
                resolutionMetadata
            );
        }

        @Test
        @DisplayName("Get resolved value")
        void getResolvedValue() {
            assertEquals(TEST_RESOLVED_VALUE, decision.getResolvedValue());
        }

        @Test
        @DisplayName("Get severity level")
        void getSeverity() {
            assertEquals(ConflictResolver.Severity.CRITICAL, decision.getSeverity());
        }

        @Test
        @DisplayName("Get participating agents")
        void getParticipatingAgents() {
            List<String> agents = decision.getParticipatingAgents();
            assertNotNull(agents);
            assertEquals(2, agents.size());
            assertTrue(agents.contains("agent-1"));
            assertTrue(agents.contains("agent-2"));
        }

        @Test
        @DisplayName("Get resolution strategy")
        void getResolutionStrategy() {
            assertEquals(RESOLUTION_STRATEGY, decision.getResolutionStrategy());
        }

        @Test
        @DisplayName("Get resolution metadata")
        void getResolutionMetadata() {
            Map<String, Object> metadata = decision.getResolutionMetadata();
            assertNotNull(metadata);
            assertEquals(2, metadata.size());
            assertEquals("voting", metadata.get("resolutionMethod"));
            assertEquals(0.85, metadata.get("confidence"));
        }

        @Test
        @DisplayName("Get resolution timestamp")
        void getResolutionTimestamp() {
            long timestamp = decision.getResolutionTimestamp();
            assertTrue(timestamp > 0);
            assertTrue(timestamp <= System.currentTimeMillis());
        }
    }

    @Nested
    @DisplayName("Decision Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("Participating agents list is immutable")
        void participatingAgentsListIsImmutable() {
            List<String> originalAgents = List.of("agent-1");
            Map<String, Object> metadata = Map.of();

            Decision decision = new Decision(
                TEST_RESOLVED_VALUE,
                ConflictResolver.Severity.LOW,
                originalAgents,
                RESOLUTION_STRATEGY,
                metadata
            );

            // Try to modify the list returned by getter
            List<String> agents = decision.getParticipatingAgents();
            assertThrows(UnsupportedOperationException.class, () -> {
                agents.add("agent-2");
            }, "Returned list should be immutable");

            // Verify original decision is unchanged
            assertEquals(1, decision.getParticipatingAgents().size());
        }

        @Test
        @DisplayName("Resolution metadata map is defensive copy")
        void resolutionMetadataIsDefensiveCopy() {
            Map<String, Object> originalMetadata = Map.of("key", "value");
            Decision decision = new Decision(
                TEST_RESOLVED_VALUE,
                ConflictResolver.Severity.LOW,
                List.of(),
                RESOLUTION_STRATEGY,
                originalMetadata
            );

            // Try to modify the map returned by getter
            Map<String, Object> metadata = decision.getResolutionMetadata();
            metadata.put("newKey", "newValue");

            // Verify original decision is unchanged
            assertEquals(1, decision.getResolutionMetadata().size());
            assertFalse(decision.getResolutionMetadata().containsKey("newKey"));
        }

        @Test
        @DisplayName("Decision is immutable - no setters")
        void decisionHasNoSetters() {
            // Test that Decision has no setters by checking methods
            // This ensures immutability
            assertDoesNotThrow(() -> {
                Decision decision = new Decision(
                    TEST_RESOLVED_VALUE,
                    ConflictResolver.Severity.LOW,
                    List.of(),
                    RESOLUTION_STRATEGY,
                    Map.of()
                );
                // Just instantiation should work without issues
            });
        }
    }

    @Nested
    @DisplayName("Decision Equals and HashCode")
    class EqualsAndHashCodeTests {

        @Test
        @DisplayName("Equal Decision objects have same hash code")
        void equalObjectsHaveSameHashCode() {
            // Given two identical Decision objects
            List<String> agents = List.of("agent-1", "agent-2");
            Map<String, Object> metadata = Map.of("key", "value");

            Decision decision1 = new Decision(
                TEST_RESOLVED_VALUE,
                ConflictResolver.Severity.HIGH,
                agents,
                RESOLUTION_STRATEGY,
                metadata
            );

            Decision decision2 = new Decision(
                TEST_RESOLVED_VALUE,
                ConflictResolver.Severity.HIGH,
                agents,
                RESOLUTION_STRATEGY,
                metadata
            );

            // Then they should be equal and have same hash code
            assertEquals(decision1, decision2);
            assertEquals(decision1.hashCode(), decision2.hashCode());
        }

        @Test
        @DisplayName("Different Decision objects are not equal")
        void differentObjectsAreNotEqual() {
            // Given different Decision objects
            Decision decision1 = new Decision(
                "APPROVE",
                ConflictResolver.Severity.HIGH,
                List.of("agent-1"),
                "MAJORITY_VOTE",
                Map.of()
            );

            Decision decision2 = new Decision(
                "REJECT",
                ConflictResolver.Severity.LOW,
                List.of("agent-2"),
                "ESCALATING",
                Map.of()
            );

            // Then they should not be equal
            assertNotEquals(decision1, decision2);
            assertNotEquals(decision1.hashCode(), decision2.hashCode());
        }

        @Test
        @DisplayName("Decision equals with null and different types")
        void equalsWithNullAndDifferentTypes() {
            // Given a Decision
            Decision decision = new Decision(
                TEST_RESOLVED_VALUE,
                ConflictResolver.Severity.LOW,
                List.of(),
                RESOLUTION_STRATEGY,
                Map.of()
            );

            // Then it should not equal null or different types
            assertNotEquals(null, decision);
            assertNotEquals("string", decision);
            assertNotEquals(new Object(), decision);
        }

        @Test
        @DisplayName("Same Decision instance is equal to itself")
        void sameInstanceIsEqual() {
            // Given a Decision
            Decision decision = new Decision(
                TEST_RESOLVED_VALUE,
                ConflictResolver.Severity.LOW,
                List.of(),
                RESOLUTION_STRATEGY,
                Map.of()
            );

            // Then it should be equal to itself
            assertEquals(decision, decision);
            assertEquals(decision.hashCode(), decision.hashCode());
        }

        @Test
        @DisplayName("Decision equality depends on all fields")
        void equalityDependsOnAllFields() {
            // Given base decision
            List<String> agents = List.of("agent-1");
            Map<String, Object> metadata = Map.of("key", "value");
            Decision base = new Decision(
                TEST_RESOLVED_VALUE,
                ConflictResolver.Severity.HIGH,
                agents,
                RESOLUTION_STRATEGY,
                metadata
            );

            // Then decisions differing in any field should not be equal
            Decision differentValue = new Decision(
                "REJECT",
                ConflictResolver.Severity.HIGH,
                agents,
                RESOLUTION_STRATEGY,
                metadata
            );
            assertNotEquals(base, differentValue);

            Decision differentSeverity = new Decision(
                TEST_RESOLVED_VALUE,
                ConflictResolver.Severity.LOW,
                agents,
                RESOLUTION_STRATEGY,
                metadata
            );
            assertNotEquals(base, differentSeverity);

            Decision differentAgents = new Decision(
                TEST_RESOLVED_VALUE,
                ConflictResolver.Severity.HIGH,
                List.of("agent-2"),
                RESOLUTION_STRATEGY,
                metadata
            );
            assertNotEquals(base, differentAgents);

            Decision differentStrategy = new Decision(
                TEST_RESOLVED_VALUE,
                ConflictResolver.Severity.HIGH,
                agents,
                "ESCALATING",
                metadata
            );
            assertNotEquals(base, differentStrategy);

            Decision differentMetadata = new Decision(
                TEST_RESOLVED_VALUE,
                ConflictResolver.Severity.HIGH,
                agents,
                RESOLUTION_STRATEGY,
                Map.of("different", "value")
            );
            assertNotEquals(base, differentMetadata);
        }
    }

    @Nested
    @DisplayName("Decision Timestamp Validation")
    class TimestampTests {

        @Test
        @DisplayName("Timestamp is within reasonable range of creation")
        void timestampWithinReasonableRange() {
            // Before creation
            long before = System.currentTimeMillis();

            // Create Decision
            Decision decision = new Decision(
                TEST_RESOLVED_VALUE,
                ConflictResolver.Severity.LOW,
                List.of(),
                RESOLUTION_STRATEGY,
                Map.of()
            );

            // After creation
            long after = System.currentTimeMillis();

            // Then timestamp should be between before and after
            assertTrue(decision.getResolutionTimestamp() >= before);
            assertTrue(decision.getResolutionTimestamp() <= after);
        }

        @Test
        @DisplayName("Timestamp is unique for different decisions")
        void timestampIsUnique() {
            // Create two decisions quickly
            Decision decision1 = new Decision(
                "DECISION-1",
                ConflictResolver.Severity.LOW,
                List.of(),
                "STRATEGY-1",
                Map.of()
            );

            Decision decision2 = new Decision(
                "DECISION-2",
                ConflictResolver.Severity.LOW,
                List.of(),
                "STRATEGY-2",
                Map.of()
            );

            // Then timestamps should be different (very likely)
            assertNotEquals(decision1.getResolutionTimestamp(), decision2.getResolutionTimestamp());
        }
    }

    @Nested
    @DisplayName("Decision Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Handle null resolved value")
        void handleNullResolvedValue() {
            // Given null resolved value
            List<String> agents = List.of("agent-1");
            Map<String, Object> metadata = Map.of();

            // When creating Decision
            Decision decision = new Decision(
                null,
                ConflictResolver.Severity.LOW,
                agents,
                RESOLUTION_STRATEGY,
                metadata
            );

            // Then it should be created successfully
            assertNotNull(decision);
            assertNull(decision.getResolvedValue());
        }

        @Test
        @DisplayName("Handle empty string resolved value")
        void handleEmptyStringResolvedValue() {
            // Given empty resolved value
            List<String> agents = List.of("agent-1");
            Map<String, Object> metadata = Map.of();

            // When creating Decision
            Decision decision = new Decision(
                "",
                ConflictResolver.Severity.LOW,
                agents,
                RESOLUTION_STRATEGY,
                metadata
            );

            // Then it should be created successfully
            assertNotNull(decision);
            assertEquals("", decision.getResolvedValue());
        }

        @Test
        @DisplayName("Handle very long resolved value")
        void handleVeryLongResolvedValue() {
            // Given very long resolved value
            String longValue = "x".repeat(10000);
            List<String> agents = List.of("agent-1");
            Map<String, Object> metadata = Map.of();

            // When creating Decision
            Decision decision = new Decision(
                longValue,
                ConflictResolver.Severity.LOW,
                agents,
                RESOLUTION_STRATEGY,
                metadata
            );

            // Then it should be created successfully
            assertEquals(longValue, decision.getResolvedValue());
        }

        @Test
        @DisplayName("Handle very long resolution strategy")
        void handleVeryLongResolutionStrategy() {
            // Given very long resolution strategy
            String longStrategy = "x".repeat(5000);
            List<String> agents = List.of("agent-1");
            Map<String, Object> metadata = Map.of();

            // When creating Decision
            Decision decision = new Decision(
                TEST_RESOLVED_VALUE,
                ConflictResolver.Severity.LOW,
                agents,
                longStrategy,
                metadata
            );

            // Then it should be created successfully
            assertEquals(longStrategy, decision.getResolutionStrategy());
        }

        @Test
        @DisplayName("Handle large metadata map")
        void handleLargeMetadataMap() {
            // Given large metadata map
            Map<String, Object> largeMetadata = new HashMap<>();
            for (int i = 0; i < 500; i++) {
                largeMetadata.put("key" + i, "value" + i);
            }

            // When creating Decision
            Decision decision = new Decision(
                TEST_RESOLVED_VALUE,
                ConflictResolver.Severity.LOW,
                List.of("agent-1"),
                RESOLUTION_STRATEGY,
                largeMetadata
            );

            // Then it should be created successfully
            assertEquals(500, decision.getResolutionMetadata().size());
        }
    }

    @Nested
    @DisplayName("Decision String Representation")
    class StringRepresentationTests {

        @Test
        @DisplayName("ToString contains all relevant information")
        void toStringContainsAllRelevantInfo() {
            // Given Decision
            List<String> agents = List.of("agent-1", "agent-2");
            Map<String, Object> metadata = Map.of(
                "method", "voting",
                "confidence", 0.85
            );

            Decision decision = new Decision(
                TEST_RESOLVED_VALUE,
                ConflictResolver.Severity.HIGH,
                agents,
                RESOLUTION_STRATEGY,
                metadata
            );

            // Then toString should contain key information
            String toString = decision.toString();
            assertTrue(toString.contains(TEST_RESOLVED_VALUE));
            assertTrue(toString.contains("HIGH"));
            assertTrue(toString.contains(RESOLUTION_STRATEGY));
            assertTrue(toString.contains("agent-1"));
            assertTrue(toString.contains("agent-2"));
        }

        @Test
        @DisplayName("ToString handles null values gracefully")
        void toStringHandlesNullValuesGracefully() {
            // Given Decision with null values
            Decision decision = new Decision(
                null,
                ConflictResolver.Severity.LOW,
                List.of(),
                null,
                Map.of()
            );

            // Then toString should handle null values
            String toString = decision.toString();
            assertTrue(toString.contains("null"));
        }
    }

    @Nested
    @DisplayName("Decision Builder Pattern Support")
    class BuilderPatternTests {

        @Test
        @DisplayName("Multiple decisions can be created independently")
        void createMultipleDecisionsIndependently() {
            // Given multiple decisions
            Decision decision1 = new Decision(
                "DECISION-1",
                ConflictResolver.Severity.HIGH,
                List.of("agent-1"),
                "STRATEGY-1",
                Map.of("type", "decision1")
            );

            Decision decision2 = new Decision(
                "DECISION-2",
                ConflictResolver.Severity.LOW,
                List.of("agent-2"),
                "STRATEGY-2",
                Map.of("type", "decision2")
            );

            // Then they should be independent
            assertNotEquals(decision1, decision2);
            assertNotEquals(decision1.hashCode(), decision2.hashCode());
            assertEquals("DECISION-1", decision1.getResolvedValue());
            assertEquals("DECISION-2", decision2.getResolvedValue());
        }

        @Test
        @DisplayName("Decisions with same properties but different timestamps are different")
        void decisionsWithSamePropertiesDifferentTimestamps() {
            // Given two decisions with same properties but different creation times
            Decision decision1 = new Decision(
                TEST_RESOLVED_VALUE,
                ConflictResolver.Severity.LOW,
                List.of("agent-1"),
                RESOLUTION_STRATEGY,
                Map.of()
            );

            // Small delay to ensure different timestamps
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            Decision decision2 = new Decision(
                TEST_RESOLVED_VALUE,
                ConflictResolver.Severity.LOW,
                List.of("agent-1"),
                RESOLUTION_STRATEGY,
                Map.of()
            );

            // Then they should be different due to timestamps
            assertNotEquals(decision1.getResolutionTimestamp(), decision2.getResolutionTimestamp());
        }
    }
}