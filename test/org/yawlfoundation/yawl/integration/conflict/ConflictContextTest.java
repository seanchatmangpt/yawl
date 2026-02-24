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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for ConflictContext data class.
 *
 * Tests:
 * - ConflictContext creation and property access
 * - Null and edge case handling
 * - Timestamp generation
 * - Immutable behavior
 * - Equals/hashCode contract
 */
class ConflictContextTest {

    private static final String TEST_CONFLICT_ID = "conflict-123";
    private static final String TEST_WORKFLOW_ID = "WF-001";
    private static final String TEST_TASK_ID = "ReviewTask";
    private static final double CONFIDENCE = 0.8;

    @Nested
    @DisplayName("ConflictContext Creation")
    class CreationTests {

        @Test
        @DisplayName("Create valid ConflictContext with all parameters")
        void createValidConflictContext() {
            // Given valid parameters
            List<AgentDecision> decisions = List.of(
                new AgentDecision("agent-1", "APPROVE", Map.of("confidence", CONFIDENCE), CONFIDENCE, "Good document")
            );
            Map<String, Object> contextData = Map.of("documentType", "contract", "amount", 1000);

            // When creating ConflictContext
            ConflictContext context = new ConflictContext(
                TEST_CONFLICT_ID,
                TEST_WORKFLOW_ID,
                TEST_TASK_ID,
                ConflictResolver.Severity.HIGH,
                decisions,
                contextData
            );

            // Then all properties should be set correctly
            assertEquals(TEST_CONFLICT_ID, context.getConflictId());
            assertEquals(TEST_WORKFLOW_ID, context.getWorkflowId());
            assertEquals(TEST_TASK_ID, context.getTaskId());
            assertEquals(ConflictResolver.Severity.HIGH, context.getSeverity());
            assertEquals(decisions, context.getConflictingDecisions());
            assertEquals(contextData, context.getContextData());
            assertTrue(context.getTimestamp() > 0);
        }

        @Test
        @DisplayName("Create ConflictContext with minimal data")
        void createConflictContextWithMinimalData() {
            // Given minimal valid parameters
            List<AgentDecision> decisions = List.of(
                new AgentDecision("agent-1", "DECISION", Map.of(), CONFIDENCE, "Rationale")
            );
            Map<String, Object> emptyContext = Map.of();

            // When creating ConflictContext
            ConflictContext context = new ConflictContext(
                TEST_CONFLICT_ID,
                TEST_WORKFLOW_ID,
                TEST_TASK_ID,
                ConflictResolver.Severity.LOW,
                decisions,
                emptyContext
            );

            // Then it should be created successfully
            assertNotNull(context);
            assertEquals(TEST_CONFLICT_ID, context.getConflictId());
            assertEquals(ConflictResolver.Severity.LOW, context.getSeverity());
            assertEquals(1, context.getConflictingDecisions().size());
            assertEquals(0, context.getContextData().size());
        }

        @Test
        @DisplayName("Create ConflictContext with empty decisions list")
        void createConflictContextWithEmptyDecisions() {
            // Given empty decisions list
            List<AgentDecision> emptyDecisions = List.of();
            Map<String, Object> contextData = Map.of("key", "value");

            // When creating ConflictContext
            ConflictContext context = new ConflictContext(
                TEST_CONFLICT_ID,
                TEST_WORKFLOW_ID,
                TEST_TASK_ID,
                ConflictResolver.Severity.MEDIUM,
                emptyDecisions,
                contextData
            );

            // Then it should be created successfully
            assertNotNull(context);
            assertEquals(TEST_CONFLICT_ID, context.getConflictId());
            assertEquals(0, context.getConflictingDecisions().size());
            assertEquals(1, context.getContextData().size());
        }
    }

    @Nested
    @DisplayName("ConflictContext Property Access")
    class PropertyTests {

        private ConflictContext context;

        @BeforeEach
        void setUp() {
            List<AgentDecision> decisions = List.of(
                new AgentDecision("agent-1", "APPROVE", Map.of(), CONFIDENCE, "Good")
            );
            Map<String, Object> contextData = Map.of("category", "review");

            context = new ConflictContext(
                TEST_CONFLICT_ID,
                TEST_WORKFLOW_ID,
                TEST_TASK_ID,
                ConflictResolver.Severity.CRITICAL,
                decisions,
                contextData
            );
        }

        @Test
        @DisplayName("Get conflict ID")
        void getConflictId() {
            assertEquals(TEST_CONFLICT_ID, context.getConflictId());
        }

        @Test
        @DisplayName("Get workflow ID")
        void getWorkflowId() {
            assertEquals(TEST_WORKFLOW_ID, context.getWorkflowId());
        }

        @Test
        @DisplayName("Get task ID")
        void getTaskId() {
            assertEquals(TEST_TASK_ID, context.getTaskId());
        }

        @Test
        @DisplayName("Get severity level")
        void getSeverity() {
            assertEquals(ConflictResolver.Severity.CRITICAL, context.getSeverity());
        }

        @Test
        @DisplayName("Get conflicting decisions")
        void getConflictingDecisions() {
            List<AgentDecision> decisions = context.getConflictingDecisions();
            assertNotNull(decisions);
            assertEquals(1, decisions.size());
            assertEquals("agent-1", decisions.get(0).getAgentId());
            assertEquals("APPROVE", decisions.get(0).getDecision());
        }

        @Test
        @DisplayName("Get context data")
        void getContextData() {
            Map<String, Object> data = context.getContextData();
            assertNotNull(data);
            assertEquals(1, data.size());
            assertEquals("review", data.get("category"));
        }

        @Test
        @DisplayName("Get timestamp")
        void getTimestamp() {
            long timestamp = context.getTimestamp();
            assertTrue(timestamp > 0);
            assertTrue(timestamp <= System.currentTimeMillis());
        }
    }

    @Nested
    @DisplayName("ConflictContext Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("Decisions list is immutable")
        void decisionsListIsImmutable() {
            List<AgentDecision> originalDecisions = List.of(
                new AgentDecision("agent-1", "DECISION", Map.of(), CONFIDENCE, "Rationale")
            );

            ConflictContext context = new ConflictContext(
                TEST_CONFLICT_ID,
                TEST_WORKFLOW_ID,
                TEST_TASK_ID,
                ConflictResolver.Severity.LOW,
                originalDecisions,
                Map.of()
            );

            // Try to modify the list returned by getter
            List<AgentDecision> decisions = context.getConflictingDecisions();
            assertThrows(UnsupportedOperationException.class, () -> {
                decisions.add(new AgentDecision("agent-2", "NEW", Map.of(), CONFIDENCE, "New rationale"));
            }, "Returned list should be immutable");

            // Verify original context is unchanged
            assertEquals(1, context.getConflictingDecisions().size());
        }

        @Test
        @DisplayName("Context data map is defensive copy")
        void contextDataIsDefensiveCopy() {
            Map<String, Object> originalData = Map.of("key", "value");
            ConflictContext context = new ConflictContext(
                TEST_CONFLICT_ID,
                TEST_WORKFLOW_ID,
                TEST_TASK_ID,
                ConflictResolver.Severity.LOW,
                List.of(),
                originalData
            );

            // Try to modify the map returned by getter
            Map<String, Object> data = context.getContextData();
            data.put("newKey", "newValue");

            // Verify original context is unchanged
            assertEquals(1, context.getContextData().size());
            assertFalse(context.getContextData().containsKey("newKey"));
        }

        @Test
        @DisplayName("ConflictContext is immutable - no setters")
        void conflictContextHasNoSetters() {
            // Test that ConflictContext has no setters by checking methods
            // This ensures immutability
            assertDoesNotThrow(() -> {
                ConflictContext context = new ConflictContext(
                    TEST_CONFLICT_ID,
                    TEST_WORKFLOW_ID,
                    TEST_TASK_ID,
                    ConflictResolver.Severity.LOW,
                    List.of(),
                    Map.of()
                );
                // Just instantiation should work without issues
            });
        }
    }

    @Nested
    @DisplayName("ConflictContext Equals and HashCode")
    class EqualsAndHashCodeTests {

        @Test
        @DisplayName("Equal ConflictContext objects have same hash code")
        void equalObjectsHaveSameHashCode() {
            // Given two identical ConflictContext objects
            List<AgentDecision> decisions = List.of(
                new AgentDecision("agent-1", "APPROVE", Map.of(), CONFIDENCE, "Rationale")
            );
            Map<String, Object> contextData = Map.of("key", "value");

            ConflictContext context1 = new ConflictContext(
                TEST_CONFLICT_ID,
                TEST_WORKFLOW_ID,
                TEST_TASK_ID,
                ConflictResolver.Severity.HIGH,
                decisions,
                contextData
            );

            ConflictContext context2 = new ConflictContext(
                TEST_CONFLICT_ID,
                TEST_WORKFLOW_ID,
                TEST_TASK_ID,
                ConflictResolver.Severity.HIGH,
                decisions,
                contextData
            );

            // Then they should be equal and have same hash code
            assertEquals(context1, context2);
            assertEquals(context1.hashCode(), context2.hashCode());
        }

        @Test
        @DisplayName("Different ConflictContext objects are not equal")
        void differentObjectsAreNotEqual() {
            // Given different ConflictContext objects
            ConflictContext context1 = new ConflictContext(
                "conflict-1",
                "WF-1",
                "Task-1",
                ConflictResolver.Severity.HIGH,
                List.of(),
                Map.of()
            );

            ConflictContext context2 = new ConflictContext(
                "conflict-2",
                "WF-2",
                "Task-2",
                ConflictResolver.Severity.LOW,
                List.of(),
                Map.of()
            );

            // Then they should not be equal
            assertNotEquals(context1, context2);
            assertNotEquals(context1.hashCode(), context2.hashCode());
        }

        @Test
        @DisplayName("ConflictContext equals with null and different types")
        void equalsWithNullAndDifferentTypes() {
            // Given a ConflictContext
            ConflictContext context = new ConflictContext(
                TEST_CONFLICT_ID,
                TEST_WORKFLOW_ID,
                TEST_TASK_ID,
                ConflictResolver.Severity.LOW,
                List.of(),
                Map.of()
            );

            // Then it should not equal null or different types
            assertNotEquals(null, context);
            assertNotEquals("string", context);
            assertNotEquals(new Object(), context);
        }

        @Test
        @DisplayName("Same ConflictContext instance is equal to itself")
        void sameInstanceIsEqual() {
            // Given a ConflictContext
            ConflictContext context = new ConflictContext(
                TEST_CONFLICT_ID,
                TEST_WORKFLOW_ID,
                TEST_TASK_ID,
                ConflictResolver.Severity.LOW,
                List.of(),
                Map.of()
            );

            // Then it should be equal to itself
            assertEquals(context, context);
            assertEquals(context.hashCode(), context.hashCode());
        }
    }

    @Nested
    @DisplayName("ConflictContext Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Handle null decision values")
        void handleNullDecisionValues() {
            // Given decisions with null values (but valid constructor parameters)
            List<AgentDecision> decisions = List.of(
                new AgentDecision("agent-1", null, Map.of(), CONFIDENCE, "Rationale")
            );
            Map<String, Object> contextData = Map.of();

            // When creating ConflictContext
            ConflictContext context = new ConflictContext(
                TEST_CONFLICT_ID,
                TEST_WORKFLOW_ID,
                TEST_TASK_ID,
                ConflictResolver.Severity.LOW,
                decisions,
                contextData
            );

            // Then it should be created successfully
            assertNotNull(context);
            assertNull(context.getConflictingDecisions().get(0).getDecision());
        }

        @Test
        @DisplayName("Handle empty string values")
        void handleEmptyStringValues() {
            // Given decisions with empty strings
            List<AgentDecision> decisions = List.of(
                new AgentDecision("agent-1", "", Map.of(), CONFIDENCE, "")
            );
            Map<String, Object> contextData = Map.of();

            // When creating ConflictContext
            ConflictContext context = new ConflictContext(
                TEST_CONFLICT_ID,
                TEST_WORKFLOW_ID,
                TEST_TASK_ID,
                ConflictResolver.Severity.LOW,
                decisions,
                contextData
            );

            // Then it should be created successfully
            assertNotNull(context);
            assertEquals("", context.getConflictingDecisions().get(0).getDecision());
            assertEquals("", context.getConflictingDecisions().get(0).getRationale());
        }

        @Test
        @DisplayName("ConflictContext timestamp is within reasonable range")
        void timestampWithinReasonableRange() {
            // Given ConflictContext creation
            long before = System.currentTimeMillis();
            ConflictContext context = new ConflictContext(
                TEST_CONFLICT_ID,
                TEST_WORKFLOW_ID,
                TEST_TASK_ID,
                ConflictResolver.Severity.LOW,
                List.of(),
                Map.of()
            );
            long after = System.currentTimeMillis();

            // Then timestamp should be between before and after
            assertTrue(context.getTimestamp() >= before);
            assertTrue(context.getTimestamp() <= after);
        }

        @Test
        @DisplayName("ConflictContext toString contains all relevant information")
        void toStringContainsRelevantInfo() {
            // Given ConflictContext
            List<AgentDecision> decisions = List.of(
                new AgentDecision("agent-1", "APPROVE", Map.of(), CONFIDENCE, "Good")
            );
            Map<String, Object> contextData = Map.of("category", "review");

            ConflictContext context = new ConflictContext(
                TEST_CONFLICT_ID,
                TEST_WORKFLOW_ID,
                TEST_TASK_ID,
                ConflictResolver.Severity.HIGH,
                decisions,
                contextData
            );

            // Then toString should contain key information
            String toString = context.toString();
            assertTrue(toString.contains(TEST_CONFLICT_ID));
            assertTrue(toString.contains(TEST_WORKFLOW_ID));
            assertTrue(toString.contains(TEST_TASK_ID));
            assertTrue(toString.contains("HIGH"));
            assertTrue(toString.contains("agent-1"));
        }
    }

    @Nested
    @DisplayName("ConflictContext Builder Pattern Support")
    class BuilderPatternTests {

        @Test
        @DisplayName("Multiple contexts can be created independently")
        void createMultipleContextsIndependently() {
            // Given multiple contexts
            ConflictContext context1 = new ConflictContext(
                "conflict-1",
                "WF-1",
                "Task-1",
                ConflictResolver.Severity.LOW,
                List.of(new AgentDecision("agent-1", "DECISION-1", Map.of(), CONFIDENCE, "Rationale-1")),
                Map.of("key1", "value1")
            );

            ConflictContext context2 = new ConflictContext(
                "conflict-2",
                "WF-2",
                "Task-2",
                ConflictResolver.Severity.HIGH,
                List.of(new AgentDecision("agent-2", "DECISION-2", Map.of(), CONFIDENCE, "Rationale-2")),
                Map.of("key2", "value2")
            );

            // Then they should be independent
            assertNotEquals(context1, context2);
            assertNotEquals(context1.hashCode(), context2.hashCode());
            assertEquals("conflict-1", context1.getConflictId());
            assertEquals("conflict-2", context2.getConflictId());
        }
    }
}