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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.conscience;

import junit.framework.TestCase;
import org.junit.jupiter.api.Tag;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.OxigraphSparqlEngine;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Chicago TDD tests for the Agent Conscience Graph.
 *
 * <p>Tests validate core conscience behavior: recording decisions, handling
 * unavailable engines, serializing to RDF, and escaping special characters.</p>
 *
 * @since YAWL 6.0
 */
@Tag("unit")
public class DecisionGraphTest extends TestCase {

    private OxigraphSparqlEngine engine;
    private DecisionGraph conscience;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Point to unused port to simulate unavailability
        this.engine = new OxigraphSparqlEngine("http://localhost:19876");
        this.conscience = new DecisionGraph(engine);
    }

    // -----------------------------------------------------------------------
    // Test 1: Record does not throw when engine unavailable
    // -----------------------------------------------------------------------

    public void testRecordDoesNotThrowWhenEngineUnavailable() {
        AgentDecision decision = AgentDecision.builder()
            .agentId("agent-1")
            .sessionId("session-xyz")
            .taskType("routing")
            .choiceKey("marketplace_find_for_slot")
            .rationale("Selected agent with lowest latency")
            .confidence(0.95)
            .withContext("selected_agent", "agent-42")
            .build();

        // Must not throw even though engine is unavailable
        conscience.record(decision);
    }

    // -----------------------------------------------------------------------
    // Test 2: isAvailable returns false when engine down
    // -----------------------------------------------------------------------

    public void testIsAvailableReturnsFalseWhenEngineDown() {
        assertFalse("Engine must not claim availability when nothing listens on port 19876",
                conscience.isAvailable());
    }

    // -----------------------------------------------------------------------
    // Test 3: AgentDecision builder creates correct record
    // -----------------------------------------------------------------------

    public void testAgentDecisionBuilderCreatesCorrectRecord() {
        String agentId = "agent-123";
        String sessionId = "session-abc";
        String taskType = "selection";
        String choiceKey = "choose_best_fit";
        String rationale = "Picked agent with best uptime record";
        double confidence = 0.87;

        AgentDecision decision = AgentDecision.builder()
            .agentId(agentId)
            .sessionId(sessionId)
            .taskType(taskType)
            .choiceKey(choiceKey)
            .rationale(rationale)
            .confidence(confidence)
            .withContext("uptime_percent", "99.5")
            .withContext("response_time_ms", "42")
            .build();

        assertEquals(agentId, decision.agentId());
        assertEquals(sessionId, decision.sessionId());
        assertEquals(taskType, decision.taskType());
        assertEquals(choiceKey, decision.choiceKey());
        assertEquals(rationale, decision.rationale());
        assertEquals(confidence, decision.confidence(), 0.01);
        assertNotNull(decision.timestamp());
        assertEquals("99.5", decision.context().get("uptime_percent"));
        assertEquals("42", decision.context().get("response_time_ms"));
    }

    // -----------------------------------------------------------------------
    // Test 4: DecisionRdfSerializer emits Turtle
    // -----------------------------------------------------------------------

    public void testDecisionRdfSerializerEmitsTurtle() {
        DecisionRdfSerializer serializer = new DecisionRdfSerializer();

        AgentDecision decision = AgentDecision.builder()
            .agentId("test-agent")
            .sessionId("test-session")
            .taskType("routing")
            .choiceKey("test_choice")
            .rationale("Test rationale")
            .confidence(0.75)
            .timestamp(Instant.parse("2024-02-23T14:32:15Z"))
            .build();

        String turtle = serializer.serialize(decision);

        assertNotNull(turtle);
        assertTrue("Turtle must contain AgentDecision type",
                turtle.contains("dec:AgentDecision"));
        assertTrue("Turtle must contain agentId",
                turtle.contains("dec:agentId"));
        assertTrue("Turtle must contain test-agent",
                turtle.contains("test-agent"));
        assertTrue("Turtle must contain taskType",
                turtle.contains("dec:taskType"));
        assertTrue("Turtle must contain routing",
                turtle.contains("routing"));
        assertTrue("Turtle must contain confidence",
                turtle.contains("dec:confidence"));
        assertTrue("Turtle must contain 0.75",
                turtle.contains("0.75"));
        assertTrue("Turtle must contain xsd:double type",
                turtle.contains("xsd:double"));
    }

    // -----------------------------------------------------------------------
    // Test 5: DecisionRdfSerializer escapes special characters
    // -----------------------------------------------------------------------

    public void testDecisionRdfSerializerEscapesSpecialChars() {
        DecisionRdfSerializer serializer = new DecisionRdfSerializer();

        String rationaleWithQuotes = "Choose agent with \"best\" fit";
        String rationaleWithBackslash = "Path: C:\\agents\\data";
        String rationaleWithNewline = "Reason 1:\nReason 2:\nReason 3";

        AgentDecision decision1 = AgentDecision.builder()
            .agentId("test-1")
            .sessionId("test-session")
            .taskType("routing")
            .choiceKey("choice-1")
            .rationale(rationaleWithQuotes)
            .confidence(0.8)
            .timestamp(Instant.parse("2024-02-23T14:32:15Z"))
            .build();

        AgentDecision decision2 = AgentDecision.builder()
            .agentId("test-2")
            .sessionId("test-session")
            .taskType("routing")
            .choiceKey("choice-2")
            .rationale(rationaleWithBackslash)
            .confidence(0.8)
            .timestamp(Instant.parse("2024-02-23T14:32:15Z"))
            .build();

        AgentDecision decision3 = AgentDecision.builder()
            .agentId("test-3")
            .sessionId("test-session")
            .taskType("routing")
            .choiceKey("choice-3")
            .rationale(rationaleWithNewline)
            .confidence(0.8)
            .timestamp(Instant.parse("2024-02-23T14:32:15Z"))
            .build();

        String turtle1 = serializer.serialize(decision1);
        String turtle2 = serializer.serialize(decision2);
        String turtle3 = serializer.serialize(decision3);

        // Verify quotes are escaped as \"
        assertTrue("Turtle must escape double quotes",
                turtle1.contains("\\\"best\\\""));

        // Verify backslash is escaped as \\
        assertTrue("Turtle must escape backslash",
                turtle2.contains("C:\\\\agents\\\\data"));

        // Verify newlines are escaped as \n
        assertTrue("Turtle must escape newlines",
                turtle3.contains("Reason 1:\\nReason 2:\\nReason 3"));
    }

    // -----------------------------------------------------------------------
    // Test 6: AgentDecision.withTimestampNow() sets current timestamp
    // -----------------------------------------------------------------------

    public void testRecordWithTimestampNow() {
        long beforeMs = System.currentTimeMillis();

        AgentDecision decision = AgentDecision.withTimestampNow(
            "agent-1",
            "session-1",
            "routing",
            "choice-1",
            "Selected based on latency",
            0.9,
            Map.of("latency_ms", "50")
        );

        long afterMs = System.currentTimeMillis();

        assertNotNull(decision.timestamp());
        long epochMilli = decision.timestamp().toEpochMilli();
        assertTrue("Timestamp should be recent (within 5 seconds)",
                epochMilli >= beforeMs - 5000 && epochMilli <= afterMs + 5000);
        assertEquals("agent-1", decision.agentId());
        assertEquals("session-1", decision.sessionId());
        assertEquals("routing", decision.taskType());
    }

    // -----------------------------------------------------------------------
    // Additional test: context entries are immutable
    // -----------------------------------------------------------------------

    public void testDecisionContextIsImmutable() {
        Map<String, String> mutableContext = new HashMap<>();
        mutableContext.put("key1", "value1");

        AgentDecision decision = AgentDecision.builder()
            .agentId("agent-1")
            .sessionId("session-1")
            .taskType("routing")
            .choiceKey("choice-1")
            .rationale("test")
            .confidence(0.5)
            .context(mutableContext)
            .build();

        // Modify the original map
        mutableContext.put("key2", "value2");

        // Decision's context should not reflect the change
        assertFalse("Decision context must not contain added entry",
                decision.context().containsKey("key2"));
    }

    // -----------------------------------------------------------------------
    // Additional test: builder context withContext adds entries
    // -----------------------------------------------------------------------

    public void testBuilderWithContextAddsMultipleEntries() {
        AgentDecision decision = AgentDecision.builder()
            .agentId("agent-1")
            .sessionId("session-1")
            .taskType("routing")
            .choiceKey("choice-1")
            .rationale("test")
            .confidence(0.5)
            .withContext("key1", "value1")
            .withContext("key2", "value2")
            .withContext("key3", "value3")
            .build();

        assertEquals("value1", decision.context().get("key1"));
        assertEquals("value2", decision.context().get("key2"));
        assertEquals("value3", decision.context().get("key3"));
        assertEquals(3, decision.context().size());
    }

    // -----------------------------------------------------------------------
    // Additional test: confidence validation
    // -----------------------------------------------------------------------

    public void testConfidenceValidation() {
        // Valid confidence values
        AgentDecision validLow = AgentDecision.builder()
            .agentId("agent-1")
            .sessionId("session-1")
            .taskType("routing")
            .choiceKey("choice-1")
            .rationale("test")
            .confidence(0.0)
            .build();
        assertEquals(0.0, validLow.confidence());

        AgentDecision validHigh = AgentDecision.builder()
            .agentId("agent-1")
            .sessionId("session-1")
            .taskType("routing")
            .choiceKey("choice-1")
            .rationale("test")
            .confidence(1.0)
            .build();
        assertEquals(1.0, validHigh.confidence());

        // Invalid confidence: too high
        try {
            AgentDecision.builder()
                .agentId("agent-1")
                .sessionId("session-1")
                .taskType("routing")
                .choiceKey("choice-1")
                .rationale("test")
                .confidence(1.5)
                .build();
            fail("Expected IllegalArgumentException for confidence > 1.0");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("0.0 and 1.0"));
        }

        // Invalid confidence: too low
        try {
            AgentDecision.builder()
                .agentId("agent-1")
                .sessionId("session-1")
                .taskType("routing")
                .choiceKey("choice-1")
                .rationale("test")
                .confidence(-0.5)
                .build();
            fail("Expected IllegalArgumentException for confidence < 0.0");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("0.0 and 1.0"));
        }
    }
}
