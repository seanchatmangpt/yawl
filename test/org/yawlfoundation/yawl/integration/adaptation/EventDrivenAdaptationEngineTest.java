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
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.adaptation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD integration tests for EventDrivenAdaptationEngine.
 * Tests real implementations with actual event processing, rule matching,
 * and action execution. No mocks or stubs.
 *
 * @author YAWL Foundation
 * @since 6.0
 */
@DisplayName("Event-Driven Adaptation Engine")
@Tag("integration")
@Tag("adaptation")
class EventDrivenAdaptationEngineTest {

    private EventDrivenAdaptationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new EventDrivenAdaptationEngine(List.of());
    }

    // ========== High-Risk Fraud Tests ==========

    @Test
    @DisplayName("High-risk fraud (risk_score > 0.9) triggers immediate rejection")
    void testHighRiskFraud_triggersReject() {
        // ARRANGE: Create fraud rule and engine
        AdaptationRule fraudRule = new AdaptationRule(
            "rule-fraud-high",
            "Reject high-risk fraud",
            AdaptationCondition.and(
                AdaptationCondition.eventType("FRAUD_ALERT"),
                AdaptationCondition.payloadAbove("risk_score", 0.9)
            ),
            AdaptationAction.REJECT_IMMEDIATELY,
            10,
            "Automatically reject fraud with risk_score > 0.9"
        );
        engine = new EventDrivenAdaptationEngine(List.of(fraudRule));

        // Create high-risk fraud event
        ProcessEvent event = new ProcessEvent(
            "evt-fraud-001",
            "FRAUD_ALERT",
            "fraud-detector",
            Instant.now(),
            Map.of("risk_score", 0.95, "transaction_id", "tx-123"),
            EventSeverity.CRITICAL
        );

        // ACT: Process event
        AdaptationResult result = engine.process(event);

        // ASSERT: Event should be adapted and action should be REJECT
        assertTrue(result.adapted(), "High-risk fraud should trigger adaptation");
        assertEquals(AdaptationAction.REJECT_IMMEDIATELY, result.executedAction(),
            "Should execute REJECT_IMMEDIATELY action");
        assertEquals(1, result.matchedRules().size(), "Should match exactly 1 rule");
        assertEquals(fraudRule.ruleId(), result.matchedRules().get(0).ruleId(),
            "Matched rule should be fraud rule");
        assertTrue(result.explanation().contains("Reject high-risk fraud"),
            "Explanation should contain rule name");
    }

    // ========== Low-Risk (No Adaptation) Tests ==========

    @Test
    @DisplayName("Low-risk event (risk_score < 0.8) does not trigger adaptation")
    void testLowRisk_noAdaptation() {
        // ARRANGE: Create fraud rule with high threshold
        AdaptationRule fraudRule = new AdaptationRule(
            "rule-fraud-high",
            "Reject high-risk fraud",
            AdaptationCondition.and(
                AdaptationCondition.eventType("FRAUD_ALERT"),
                AdaptationCondition.payloadAbove("risk_score", 0.9)
            ),
            AdaptationAction.REJECT_IMMEDIATELY,
            10,
            "Reject fraud > 0.9"
        );
        engine = new EventDrivenAdaptationEngine(List.of(fraudRule));

        // Create low-risk event
        ProcessEvent event = new ProcessEvent(
            "evt-low-001",
            "FRAUD_ALERT",
            "fraud-detector",
            Instant.now(),
            Map.of("risk_score", 0.2),
            EventSeverity.LOW
        );

        // ACT: Process event
        AdaptationResult result = engine.process(event);

        // ASSERT: Event should not be adapted
        assertFalse(result.adapted(), "Low-risk fraud should not trigger adaptation");
        assertNull(result.executedAction(), "No action should be executed");
        assertEquals(0, result.matchedRules().size(), "No rules should match");
        assertTrue(result.explanation().contains("No adaptation rules matched"),
            "Explanation should indicate no match");
    }

    // ========== Critical Severity Tests ==========

    @Test
    @DisplayName("Critical severity event triggers escalation to manual review")
    void testCriticalSeverity_escalates() {
        // ARRANGE: Create critical severity rule
        AdaptationRule criticalRule = new AdaptationRule(
            "rule-critical-escalate",
            "Escalate critical events",
            AdaptationCondition.severityAtLeast(EventSeverity.CRITICAL),
            AdaptationAction.ESCALATE_TO_MANUAL,
            5,
            "Escalate all critical events"
        );
        engine = new EventDrivenAdaptationEngine(List.of(criticalRule));

        // Create critical event
        ProcessEvent event = new ProcessEvent(
            "evt-critical-001",
            "SYSTEM_ALERT",
            "monitoring",
            Instant.now(),
            Map.of("alert_type", "resource_exhaustion"),
            EventSeverity.CRITICAL
        );

        // ACT: Process event
        AdaptationResult result = engine.process(event);

        // ASSERT: Event should be escalated
        assertTrue(result.adapted(), "Critical event should trigger adaptation");
        assertEquals(AdaptationAction.ESCALATE_TO_MANUAL, result.executedAction(),
            "Should escalate to manual");
        assertEquals(1, result.matchedRules().size(), "Should match critical rule");
    }

    // ========== Priority Ordering Tests ==========

    @Test
    @DisplayName("Higher-priority rule (lower number) wins when multiple rules match")
    void testPriorityOrdering_higherPriorityWins() {
        // ARRANGE: Create two overlapping rules with different priorities
        AdaptationRule lowPriorityRule = new AdaptationRule(
            "rule-fraud-medium",
            "Escalate medium-risk fraud",
            AdaptationCondition.eventType("FRAUD_ALERT"),
            AdaptationAction.ESCALATE_TO_MANUAL,
            100,  // Lower priority (higher number)
            "Escalate medium-risk fraud"
        );

        AdaptationRule highPriorityRule = new AdaptationRule(
            "rule-fraud-high",
            "Reject high-risk fraud",
            AdaptationCondition.and(
                AdaptationCondition.eventType("FRAUD_ALERT"),
                AdaptationCondition.payloadAbove("risk_score", 0.5)
            ),
            AdaptationAction.REJECT_IMMEDIATELY,
            10,  // Higher priority (lower number)
            "Reject fraud > 0.5"
        );

        engine = new EventDrivenAdaptationEngine(List.of(lowPriorityRule, highPriorityRule));

        // Create event that matches both rules
        ProcessEvent event = new ProcessEvent(
            "evt-fraud-002",
            "FRAUD_ALERT",
            "fraud-detector",
            Instant.now(),
            Map.of("risk_score", 0.8),
            EventSeverity.HIGH
        );

        // ACT: Process event
        AdaptationResult result = engine.process(event);

        // ASSERT: High-priority rule should win
        assertTrue(result.adapted(), "Event should match rules");
        assertEquals(AdaptationAction.REJECT_IMMEDIATELY, result.executedAction(),
            "Higher priority (lower number) rule should execute");
        assertEquals(2, result.matchedRules().size(),
            "Both rules should match, but only first executes");
        assertEquals(10, result.matchedRules().get(0).priority(),
            "First matched rule should be high-priority");
    }

    // ========== Batch Processing Tests ==========

    @Test
    @DisplayName("Batch processing: all events are processed and results returned in order")
    void testBatchProcessing_allEventsProcessed() {
        // ARRANGE: Create fraud rule
        AdaptationRule fraudRule = new AdaptationRule(
            "rule-fraud",
            "Detect fraud",
            AdaptationCondition.eventType("FRAUD_ALERT"),
            AdaptationAction.REJECT_IMMEDIATELY,
            10,
            "Reject fraud"
        );
        engine = new EventDrivenAdaptationEngine(List.of(fraudRule));

        // Create batch of 5 events (2 fraud, 3 normal)
        List<ProcessEvent> events = List.of(
            new ProcessEvent("evt-1", "FRAUD_ALERT", "detector", Instant.now(),
                Map.of("risk", 0.9), EventSeverity.HIGH),
            new ProcessEvent("evt-2", "NORMAL_EVENT", "system", Instant.now(),
                Map.of("type", "status"), EventSeverity.LOW),
            new ProcessEvent("evt-3", "FRAUD_ALERT", "detector", Instant.now(),
                Map.of("risk", 0.8), EventSeverity.MEDIUM),
            new ProcessEvent("evt-4", "RATE_CHANGE", "market", Instant.now(),
                Map.of("rate", 1.5), EventSeverity.MEDIUM),
            new ProcessEvent("evt-5", "NORMAL_EVENT", "system", Instant.now(),
                Map.of("type", "update"), EventSeverity.LOW)
        );

        // ACT: Process batch
        List<AdaptationResult> results = engine.processBatch(events);

        // ASSERT: All results returned in order, 2 adapted
        assertEquals(5, results.size(), "Should return 5 results");
        assertTrue(results.get(0).adapted(), "Event 1 (FRAUD) should be adapted");
        assertFalse(results.get(1).adapted(), "Event 2 (NORMAL) should not be adapted");
        assertTrue(results.get(2).adapted(), "Event 3 (FRAUD) should be adapted");
        assertFalse(results.get(3).adapted(), "Event 4 (RATE_CHANGE) should not be adapted");
        assertFalse(results.get(4).adapted(), "Event 5 (NORMAL) should not be adapted");

        // Verify order
        for (int i = 0; i < results.size(); i++) {
            assertEquals(events.get(i).eventId(), results.get(i).triggeringEvent().eventId(),
                "Results should be in same order as events");
        }
    }

    // ========== Immutable Update (withRule) Tests ==========

    @Test
    @DisplayName("withRule: adds rule to new engine (immutable update)")
    void testWithRule_addsRuleToNewEngine() {
        // ARRANGE: Start with empty engine
        assertEquals(0, engine.ruleCount(), "Engine should start empty");

        // Create first rule
        AdaptationRule rule1 = new AdaptationRule(
            "rule-1",
            "Rule 1",
            AdaptationCondition.eventType("TYPE_A"),
            AdaptationAction.ESCALATE_TO_MANUAL,
            10,
            "First rule"
        );

        // ACT: Add rule via withRule (immutable update)
        EventDrivenAdaptationEngine engine2 = engine.withRule(rule1);

        // ASSERT: Original engine unchanged, new engine has rule
        assertEquals(0, engine.ruleCount(), "Original engine should still be empty");
        assertEquals(1, engine2.ruleCount(), "New engine should have 1 rule");

        // Add second rule
        AdaptationRule rule2 = new AdaptationRule(
            "rule-2",
            "Rule 2",
            AdaptationCondition.eventType("TYPE_B"),
            AdaptationAction.REJECT_IMMEDIATELY,
            20,
            "Second rule"
        );

        // ACT: Add second rule to engine2
        EventDrivenAdaptationEngine engine3 = engine2.withRule(rule2);

        // ASSERT: Both engines unchanged, new engine has 2 rules (sorted by priority)
        assertEquals(1, engine2.ruleCount(), "Engine2 should still have 1 rule");
        assertEquals(2, engine3.ruleCount(), "Engine3 should have 2 rules");

        // Verify priority sorting
        List<AdaptationRule> rules = engine3.rulesForAction(AdaptationAction.ESCALATE_TO_MANUAL);
        rules.addAll(engine3.rulesForAction(AdaptationAction.REJECT_IMMEDIATELY));
        assertEquals(10, rules.get(0).priority(), "First rule should have priority 10");
        assertEquals(20, rules.get(1).priority(), "Second rule should have priority 20");
    }

    // ========== Logical Condition Tests (AND) ==========

    @Test
    @DisplayName("AND condition: both conditions must match for rule to execute")
    void testAndCondition_bothMustMatch() {
        // ARRANGE: Create rule with AND condition
        AdaptationRule rule = new AdaptationRule(
            "rule-and",
            "AND test",
            AdaptationCondition.and(
                AdaptationCondition.eventType("FRAUD_ALERT"),
                AdaptationCondition.payloadAbove("risk_score", 0.7)
            ),
            AdaptationAction.ESCALATE_TO_MANUAL,
            10,
            "Both type and risk must match"
        );
        engine = new EventDrivenAdaptationEngine(List.of(rule));

        // Test 1: Both conditions true
        ProcessEvent eventBoth = new ProcessEvent(
            "evt-both",
            "FRAUD_ALERT",
            "detector",
            Instant.now(),
            Map.of("risk_score", 0.8),
            EventSeverity.MEDIUM
        );
        AdaptationResult resultBoth = engine.process(eventBoth);
        assertTrue(resultBoth.adapted(), "Should match when both conditions true");

        // Test 2: First condition true, second false
        ProcessEvent eventFirstOnly = new ProcessEvent(
            "evt-first",
            "FRAUD_ALERT",
            "detector",
            Instant.now(),
            Map.of("risk_score", 0.5),
            EventSeverity.LOW
        );
        AdaptationResult resultFirstOnly = engine.process(eventFirstOnly);
        assertFalse(resultFirstOnly.adapted(), "Should not match when second condition false");

        // Test 3: First condition false, second true
        ProcessEvent eventSecondOnly = new ProcessEvent(
            "evt-second",
            "RATE_CHANGE",
            "market",
            Instant.now(),
            Map.of("risk_score", 0.9),
            EventSeverity.HIGH
        );
        AdaptationResult resultSecondOnly = engine.process(eventSecondOnly);
        assertFalse(resultSecondOnly.adapted(), "Should not match when first condition false");

        // Test 4: Both conditions false
        ProcessEvent eventNeither = new ProcessEvent(
            "evt-neither",
            "NORMAL_EVENT",
            "system",
            Instant.now(),
            Map.of("value", 0.3),
            EventSeverity.LOW
        );
        AdaptationResult resultNeither = engine.process(eventNeither);
        assertFalse(resultNeither.adapted(), "Should not match when both conditions false");
    }

    // ========== Logical Condition Tests (OR) ==========

    @Test
    @DisplayName("OR condition: either condition matching is sufficient for rule to execute")
    void testOrCondition_eitherMatches() {
        // ARRANGE: Create rule with OR condition
        AdaptationRule rule = new AdaptationRule(
            "rule-or",
            "OR test",
            AdaptationCondition.or(
                AdaptationCondition.eventType("FRAUD_ALERT"),
                AdaptationCondition.severityAtLeast(EventSeverity.CRITICAL)
            ),
            AdaptationAction.ESCALATE_TO_MANUAL,
            10,
            "Match if fraud OR critical"
        );
        engine = new EventDrivenAdaptationEngine(List.of(rule));

        // Test 1: First condition true
        ProcessEvent eventFirst = new ProcessEvent(
            "evt-1",
            "FRAUD_ALERT",
            "detector",
            Instant.now(),
            Map.of("risk", 0.5),
            EventSeverity.LOW
        );
        AdaptationResult resultFirst = engine.process(eventFirst);
        assertTrue(resultFirst.adapted(), "Should match when first condition true");

        // Test 2: Second condition true
        ProcessEvent eventSecond = new ProcessEvent(
            "evt-2",
            "NORMAL_EVENT",
            "system",
            Instant.now(),
            Map.of("value", 1),
            EventSeverity.CRITICAL
        );
        AdaptationResult resultSecond = engine.process(eventSecond);
        assertTrue(resultSecond.adapted(), "Should match when second condition true");

        // Test 3: Both conditions true
        ProcessEvent eventBoth = new ProcessEvent(
            "evt-3",
            "FRAUD_ALERT",
            "detector",
            Instant.now(),
            Map.of("risk", 0.8),
            EventSeverity.CRITICAL
        );
        AdaptationResult resultBoth = engine.process(eventBoth);
        assertTrue(resultBoth.adapted(), "Should match when both conditions true");

        // Test 4: Neither condition true
        ProcessEvent eventNeither = new ProcessEvent(
            "evt-4",
            "RATE_CHANGE",
            "market",
            Instant.now(),
            Map.of("rate", 1.5),
            EventSeverity.LOW
        );
        AdaptationResult resultNeither = engine.process(eventNeither);
        assertFalse(resultNeither.adapted(), "Should not match when both conditions false");
    }

    // ========== rulesForAction Tests ==========

    @Test
    @DisplayName("rulesForAction: returns all rules with a specific action, sorted by priority")
    void testRulesForAction_filtersAndSorts() {
        // ARRANGE: Create multiple rules with different actions
        AdaptationRule reject1 = new AdaptationRule(
            "reject-1",
            "Reject 1",
            AdaptationCondition.eventType("TYPE_A"),
            AdaptationAction.REJECT_IMMEDIATELY,
            30,
            "Reject 1"
        );

        AdaptationRule reject2 = new AdaptationRule(
            "reject-2",
            "Reject 2",
            AdaptationCondition.eventType("TYPE_B"),
            AdaptationAction.REJECT_IMMEDIATELY,
            10,
            "Reject 2"
        );

        AdaptationRule escalate = new AdaptationRule(
            "escalate",
            "Escalate",
            AdaptationCondition.eventType("TYPE_C"),
            AdaptationAction.ESCALATE_TO_MANUAL,
            20,
            "Escalate"
        );

        engine = new EventDrivenAdaptationEngine(List.of(reject1, reject2, escalate));

        // ACT: Get rules for REJECT action
        List<AdaptationRule> rejectRules = engine.rulesForAction(AdaptationAction.REJECT_IMMEDIATELY);

        // ASSERT: Should return 2 rules sorted by priority
        assertEquals(2, rejectRules.size(), "Should return 2 REJECT rules");
        assertEquals(10, rejectRules.get(0).priority(), "First should be priority 10");
        assertEquals(30, rejectRules.get(1).priority(), "Second should be priority 30");
        assertEquals("reject-2", rejectRules.get(0).ruleId(), "Should be sorted by priority");

        // ACT: Get rules for ESCALATE action
        List<AdaptationRule> escalateRules = engine.rulesForAction(AdaptationAction.ESCALATE_TO_MANUAL);

        // ASSERT: Should return 1 rule
        assertEquals(1, escalateRules.size(), "Should return 1 ESCALATE rule");
        assertEquals("escalate", escalateRules.get(0).ruleId(), "Should be the escalate rule");
    }

    // ========== NoMatch Factory Method Tests ==========

    @Test
    @DisplayName("AdaptationResult.noMatch: creates non-adapted result with no rules")
    void testNoMatchFactory() {
        // ARRANGE: Create an event
        ProcessEvent event = new ProcessEvent(
            "evt-1",
            "UNKNOWN_TYPE",
            "source",
            Instant.now(),
            Map.of("data", 123),
            EventSeverity.LOW
        );

        // ACT: Create noMatch result
        AdaptationResult result = AdaptationResult.noMatch(event);

        // ASSERT: Result should indicate no adaptation
        assertFalse(result.adapted(), "noMatch result should not be adapted");
        assertNull(result.executedAction(), "No action should be executed");
        assertEquals(0, result.matchedRules().size(), "No rules should be matched");
        assertTrue(result.explanation().contains("No adaptation rules matched"),
            "Explanation should indicate no match");
        assertEquals(event, result.triggeringEvent(), "Should contain original event");
    }

    // ========== Null Input Tests ==========

    @Test
    @DisplayName("process: throws NullPointerException if event is null")
    void testProcess_nullEvent_throwsException() {
        engine = new EventDrivenAdaptationEngine(List.of());
        assertThrows(NullPointerException.class, () -> engine.process(null),
            "process should throw NullPointerException for null event");
    }

    @Test
    @DisplayName("processBatch: throws NullPointerException if events list is null")
    void testProcessBatch_nullList_throwsException() {
        engine = new EventDrivenAdaptationEngine(List.of());
        assertThrows(NullPointerException.class, () -> engine.processBatch(null),
            "processBatch should throw NullPointerException for null list");
    }

    @Test
    @DisplayName("withRule: throws NullPointerException if rule is null")
    void testWithRule_nullRule_throwsException() {
        engine = new EventDrivenAdaptationEngine(List.of());
        assertThrows(NullPointerException.class, () -> engine.withRule(null),
            "withRule should throw NullPointerException for null rule");
    }

    // ========== EventSeverity Tests ==========

    @Test
    @DisplayName("EventSeverity.level(): returns correct numeric level")
    void testEventSeverityLevel() {
        assertEquals(1, EventSeverity.LOW.level(), "LOW should be level 1");
        assertEquals(2, EventSeverity.MEDIUM.level(), "MEDIUM should be level 2");
        assertEquals(3, EventSeverity.HIGH.level(), "HIGH should be level 3");
        assertEquals(4, EventSeverity.CRITICAL.level(), "CRITICAL should be level 4");
    }

    @Test
    @DisplayName("EventSeverity.isAtLeast(): correctly compares severity levels")
    void testEventSeverityIsAtLeast() {
        assertTrue(EventSeverity.CRITICAL.isAtLeast(EventSeverity.CRITICAL),
            "CRITICAL >= CRITICAL");
        assertTrue(EventSeverity.CRITICAL.isAtLeast(EventSeverity.HIGH),
            "CRITICAL >= HIGH");
        assertFalse(EventSeverity.LOW.isAtLeast(EventSeverity.MEDIUM),
            "LOW < MEDIUM");
        assertTrue(EventSeverity.HIGH.isAtLeast(EventSeverity.MEDIUM),
            "HIGH >= MEDIUM");
    }

    // ========== ProcessEvent Tests ==========

    @Test
    @DisplayName("ProcessEvent.hasPayloadKey(): correctly checks payload keys")
    void testProcessEventHasPayloadKey() {
        ProcessEvent event = new ProcessEvent(
            "evt-1",
            "TEST",
            "source",
            Instant.now(),
            Map.of("risk_score", 0.8, "count", 10),
            EventSeverity.MEDIUM
        );

        assertTrue(event.hasPayloadKey("risk_score"), "Should have risk_score");
        assertTrue(event.hasPayloadKey("count"), "Should have count");
        assertFalse(event.hasPayloadKey("missing"), "Should not have missing");
    }

    @Test
    @DisplayName("ProcessEvent.payloadValue(): retrieves correct value from payload")
    void testProcessEventPayloadValue() {
        ProcessEvent event = new ProcessEvent(
            "evt-1",
            "TEST",
            "source",
            Instant.now(),
            Map.of("name", "test", "value", 42.5),
            EventSeverity.MEDIUM
        );

        assertEquals("test", event.payloadValue("name"), "Should retrieve string");
        assertEquals(42.5, event.payloadValue("value"), "Should retrieve double");
        assertNull(event.payloadValue("missing"), "Missing key should return null");
    }

    @Test
    @DisplayName("ProcessEvent.numericPayload(): parses and returns numeric values")
    void testProcessEventNumericPayload() {
        ProcessEvent event = new ProcessEvent(
            "evt-1",
            "TEST",
            "source",
            Instant.now(),
            Map.of(
                "int_value", 100,
                "double_value", 42.5,
                "string_number", "123.45"
            ),
            EventSeverity.MEDIUM
        );

        assertEquals(100.0, event.numericPayload("int_value"), 0.001,
            "Should parse integer as double");
        assertEquals(42.5, event.numericPayload("double_value"), 0.001,
            "Should return double directly");
        assertEquals(123.45, event.numericPayload("string_number"), 0.001,
            "Should parse string number");
    }

    @Test
    @DisplayName("ProcessEvent.numericPayload(): throws for non-numeric values")
    void testProcessEventNumericPayload_nonNumeric_throws() {
        ProcessEvent event = new ProcessEvent(
            "evt-1",
            "TEST",
            "source",
            Instant.now(),
            Map.of("name", "text", "missing", 0),
            EventSeverity.MEDIUM
        );

        assertThrows(IllegalArgumentException.class, () -> event.numericPayload("name"),
            "Should throw for non-numeric value");
        assertThrows(IllegalArgumentException.class, () -> event.numericPayload("nonexistent"),
            "Should throw for missing key");
    }

    // ========== AdaptationAction Tests ==========

    @Test
    @DisplayName("AdaptationAction.description(): returns action description")
    void testAdaptationActionDescription() {
        assertEquals("Immediately reject the case",
            AdaptationAction.REJECT_IMMEDIATELY.description());
        assertEquals("Escalate case to manual review",
            AdaptationAction.ESCALATE_TO_MANUAL.description());
        assertEquals("Pause case execution",
            AdaptationAction.PAUSE_CASE.description());
        assertEquals("Cancel the case",
            AdaptationAction.CANCEL_CASE.description());
    }

    // ========== AdaptationRule Tests ==========

    @Test
    @DisplayName("AdaptationRule.isHighPriority(): returns true for priority <= 10")
    void testAdaptationRuleIsHighPriority() {
        AdaptationRule highPriority = new AdaptationRule(
            "rule-1",
            "High Priority",
            AdaptationCondition.eventType("TEST"),
            AdaptationAction.REJECT_IMMEDIATELY,
            10,
            "Test"
        );
        assertTrue(highPriority.isHighPriority(), "Priority 10 should be high");

        AdaptationRule lowPriority = new AdaptationRule(
            "rule-2",
            "Low Priority",
            AdaptationCondition.eventType("TEST"),
            AdaptationAction.REJECT_IMMEDIATELY,
            50,
            "Test"
        );
        assertFalse(lowPriority.isHighPriority(), "Priority 50 should not be high");
    }

    // ========== Edge Cases & Integration ==========

    @Test
    @DisplayName("Empty engine: processes events but matches nothing")
    void testEmptyEngine() {
        engine = new EventDrivenAdaptationEngine(List.of());

        ProcessEvent event = new ProcessEvent(
            "evt-1",
            "ANY_TYPE",
            "source",
            Instant.now(),
            Map.of("data", 1),
            EventSeverity.CRITICAL
        );

        AdaptationResult result = engine.process(event);

        assertFalse(result.adapted(), "Empty engine should not adapt");
        assertEquals(0, result.matchedRules().size(), "No rules to match");
    }

    @Test
    @DisplayName("Complex nested conditions: AND(OR(...), OR(...))")
    void testComplexNestedConditions() {
        // Rule: (TYPE_A OR CRITICAL) AND (risk > 0.5)
        AdaptationRule rule = new AdaptationRule(
            "rule-complex",
            "Complex rule",
            AdaptationCondition.and(
                AdaptationCondition.or(
                    AdaptationCondition.eventType("TYPE_A"),
                    AdaptationCondition.severityAtLeast(EventSeverity.CRITICAL)
                ),
                AdaptationCondition.payloadAbove("risk", 0.5)
            ),
            AdaptationAction.ESCALATE_TO_MANUAL,
            10,
            "Complex condition"
        );

        engine = new EventDrivenAdaptationEngine(List.of(rule));

        // Test: TYPE_A with risk=0.8 (should match)
        ProcessEvent event1 = new ProcessEvent(
            "evt-1",
            "TYPE_A",
            "source",
            Instant.now(),
            Map.of("risk", 0.8),
            EventSeverity.LOW
        );
        assertTrue(engine.process(event1).adapted(), "TYPE_A with risk > 0.5 should match");

        // Test: CRITICAL with risk=0.8 (should match)
        ProcessEvent event2 = new ProcessEvent(
            "evt-2",
            "TYPE_B",
            "source",
            Instant.now(),
            Map.of("risk", 0.8),
            EventSeverity.CRITICAL
        );
        assertTrue(engine.process(event2).adapted(), "CRITICAL with risk > 0.5 should match");

        // Test: TYPE_A with risk=0.2 (should not match - risk too low)
        ProcessEvent event3 = new ProcessEvent(
            "evt-3",
            "TYPE_A",
            "source",
            Instant.now(),
            Map.of("risk", 0.2),
            EventSeverity.LOW
        );
        assertFalse(engine.process(event3).adapted(), "TYPE_A with risk < 0.5 should not match");

        // Test: TYPE_B with risk=0.8 and LOW severity (should not match - both OR conditions false)
        ProcessEvent event4 = new ProcessEvent(
            "evt-4",
            "TYPE_B",
            "source",
            Instant.now(),
            Map.of("risk", 0.8),
            EventSeverity.LOW
        );
        assertFalse(engine.process(event4).adapted(), "TYPE_B with LOW severity should not match");
    }
}
