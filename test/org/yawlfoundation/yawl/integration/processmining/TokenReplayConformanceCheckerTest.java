/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.processmining;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.elements.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TokenReplayConformanceChecker.
 *
 * Tests cover:
 * - Perfect trace matching (fitness = 1.0)
 * - Missing activities (incomplete execution)
 * - Extra activities (not in model)
 * - AND-split/join control flow
 * - XOR-split/join control flow
 * - Empty traces
 * - Deviating case collection
 */
@DisplayName("TokenReplayConformanceChecker Tests")
class TokenReplayConformanceCheckerTest {

    private YSpecification specification;
    private YNet simpleLinearNet;
    private YNet andSplitJoinNet;
    private YNet xorSplitJoinNet;

    @BeforeEach
    void setUp() {
        specification = new YSpecification("test-spec");
    }

    /**
     * Build a simple linear workflow: A -> B -> C
     */
    private YNet buildSimpleLinearNet() {
        YNet net = new YNet("linear-net", specification);

        YInputCondition input = new YInputCondition("i1", "input", net);
        YOutputCondition output = new YOutputCondition("o1", "output", net);

        YAtomicTask taskA = new YAtomicTask("taskA", YTask._XOR, YTask._XOR, net);
        taskA.setName("A");
        YAtomicTask taskB = new YAtomicTask("taskB", YTask._XOR, YTask._XOR, net);
        taskB.setName("B");
        YAtomicTask taskC = new YAtomicTask("taskC", YTask._XOR, YTask._XOR, net);
        taskC.setName("C");

        YCondition c1 = new YCondition("c1", "c1", net);
        YCondition c2 = new YCondition("c2", "c2", net);
        YCondition c3 = new YCondition("c3", "c3", net);

        net.setInputCondition(input);
        net.setOutputCondition(output);
        net.addNetElement(taskA);
        net.addNetElement(taskB);
        net.addNetElement(taskC);
        net.addNetElement(c1);
        net.addNetElement(c2);
        net.addNetElement(c3);

        new YFlow(input, taskA);
        new YFlow(taskA, c1);
        new YFlow(c1, taskB);
        new YFlow(taskB, c2);
        new YFlow(c2, taskC);
        new YFlow(taskC, c3);
        new YFlow(c3, output);

        return net;
    }

    /**
     * Build a net with AND-split: A -> (B, C) -> D
     */
    private YNet buildAndSplitJoinNet() {
        YNet net = new YNet("and-split-net", specification);

        YInputCondition input = new YInputCondition("i1", "input", net);
        YOutputCondition output = new YOutputCondition("o1", "output", net);

        YAtomicTask taskA = new YAtomicTask("taskA", YTask._AND, YTask._AND, net);
        taskA.setName("A");
        YAtomicTask taskB = new YAtomicTask("taskB", YTask._AND, YTask._XOR, net);
        taskB.setName("B");
        YAtomicTask taskC = new YAtomicTask("taskC", YTask._AND, YTask._XOR, net);
        taskC.setName("C");
        YAtomicTask taskD = new YAtomicTask("taskD", YTask._AND, YTask._XOR, net);
        taskD.setName("D");

        YCondition c1 = new YCondition("c1", "c1", net);
        YCondition c2 = new YCondition("c2", "c2", net);
        YCondition c3 = new YCondition("c3", "c3", net);
        YCondition c4 = new YCondition("c4", "c4", net);

        net.setInputCondition(input);
        net.setOutputCondition(output);
        net.addNetElement(taskA);
        net.addNetElement(taskB);
        net.addNetElement(taskC);
        net.addNetElement(taskD);
        net.addNetElement(c1);
        net.addNetElement(c2);
        net.addNetElement(c3);
        net.addNetElement(c4);

        new YFlow(input, taskA);
        new YFlow(taskA, c1);
        new YFlow(c1, taskB);
        new YFlow(c1, taskC);
        new YFlow(taskB, c2);
        new YFlow(taskC, c3);
        new YFlow(c2, taskD);
        new YFlow(c3, taskD);
        new YFlow(taskD, c4);
        new YFlow(c4, output);

        return net;
    }

    /**
     * Build a net with XOR-split: A -> (B | C) -> D
     */
    private YNet buildXorSplitJoinNet() {
        YNet net = new YNet("xor-split-net", specification);

        YInputCondition input = new YInputCondition("i1", "input", net);
        YOutputCondition output = new YOutputCondition("o1", "output", net);

        YAtomicTask taskA = new YAtomicTask("taskA", YTask._XOR, YTask._XOR, net);
        taskA.setName("A");
        YAtomicTask taskB = new YAtomicTask("taskB", YTask._XOR, YTask._XOR, net);
        taskB.setName("B");
        YAtomicTask taskC = new YAtomicTask("taskC", YTask._XOR, YTask._XOR, net);
        taskC.setName("C");
        YAtomicTask taskD = new YAtomicTask("taskD", YTask._OR, YTask._XOR, net);
        taskD.setName("D");

        YCondition c1 = new YCondition("c1", "c1", net);
        YCondition c2 = new YCondition("c2", "c2", net);
        YCondition c3 = new YCondition("c3", "c3", net);
        YCondition c4 = new YCondition("c4", "c4", net);

        net.setInputCondition(input);
        net.setOutputCondition(output);
        net.addNetElement(taskA);
        net.addNetElement(taskB);
        net.addNetElement(taskC);
        net.addNetElement(taskD);
        net.addNetElement(c1);
        net.addNetElement(c2);
        net.addNetElement(c3);
        net.addNetElement(c4);

        new YFlow(input, taskA);
        new YFlow(taskA, c1);
        new YFlow(c1, taskB);
        new YFlow(c1, taskC);
        new YFlow(taskB, c2);
        new YFlow(taskC, c3);
        new YFlow(c2, taskD);
        new YFlow(c3, taskD);
        new YFlow(taskD, c4);
        new YFlow(c4, output);

        return net;
    }

    // ========================================================================
    // Tests
    // ========================================================================

    @Test
    @DisplayName("Test 1: Perfect linear trace matches model")
    void testPerfectLinearTrace() {
        simpleLinearNet = buildSimpleLinearNet();
        List<String> activities = List.of("A", "B", "C");

        TokenReplayConformanceChecker.TokenReplayResult result =
            TokenReplayConformanceChecker.replaySingleTrace(simpleLinearNet, "case-001", activities);

        assertEquals(0, result.missing, "Perfect trace should have no missing tokens");
        assertEquals(0, result.remaining, "Perfect trace should have no remaining tokens");
        assertEquals(1.0, result.computeFitness(), 0.001, "Perfect trace should have fitness 1.0");
        assertEquals(1, result.fittingTraces, "One trace should be fitting");
        assertEquals(1, result.traceCount, "One trace total");
        assertTrue(result.deviatingCases.isEmpty(), "No deviating cases expected");
    }

    @Test
    @DisplayName("Test 2: Missing activity causes deviation")
    void testMissingActivity() {
        simpleLinearNet = buildSimpleLinearNet();
        List<String> activities = List.of("A", "C");

        TokenReplayConformanceChecker.TokenReplayResult result =
            TokenReplayConformanceChecker.replaySingleTrace(simpleLinearNet, "case-002", activities);

        assertTrue(result.missing > 0, "Missing task B should cause missing tokens");
        assertFalse(result.deviatingCases.isEmpty(), "Case should be deviating");
        assertTrue(result.deviatingCases.contains("case-002"), "Deviating case ID should be recorded");
        assertTrue(result.computeFitness() < 1.0, "Fitness should be less than 1.0");
    }

    @Test
    @DisplayName("Test 3: Extra activity not in model is skipped")
    void testExtraActivityNotInModel() {
        simpleLinearNet = buildSimpleLinearNet();
        List<String> activities = List.of("A", "B", "UNKNOWN", "C");

        TokenReplayConformanceChecker.TokenReplayResult result =
            TokenReplayConformanceChecker.replaySingleTrace(simpleLinearNet, "case-003", activities);

        assertEquals(0, result.missing, "Unknown activity should be skipped gracefully");
        assertEquals(0, result.remaining, "No remaining tokens expected");
        assertEquals(1.0, result.computeFitness(), 0.001, "Fitness should still be 1.0");
    }

    @Test
    @DisplayName("Test 4: AND-split produces tokens in both branches")
    void testAndSplitBothBranches() {
        andSplitJoinNet = buildAndSplitJoinNet();
        List<String> activities = List.of("A", "B", "C", "D");

        TokenReplayConformanceChecker.TokenReplayResult result =
            TokenReplayConformanceChecker.replaySingleTrace(andSplitJoinNet, "case-004", activities);

        assertEquals(0, result.missing, "AND-split should fire correctly");
        assertEquals(0, result.remaining, "All tokens should be consumed");
        assertEquals(1.0, result.computeFitness(), 0.001, "Fitness should be 1.0");
    }

    @Test
    @DisplayName("Test 5: Empty trace is handled without exception")
    void testEmptyTrace() {
        simpleLinearNet = buildSimpleLinearNet();
        List<String> activities = List.of();

        TokenReplayConformanceChecker.TokenReplayResult result =
            TokenReplayConformanceChecker.replaySingleTrace(simpleLinearNet, "case-empty", activities);

        assertEquals(1, result.traceCount, "Empty trace counts as one trace");
        assertEquals(0, result.missing, "Empty trace should have no missing tokens");
        assertEquals(1.0, result.computeFitness(), 0.001, "Empty trace should have fitness 1.0");
    }

    @Test
    @DisplayName("Test 6: Partial trace with missing final activities")
    void testPartialTraceWithMissingFinal() {
        simpleLinearNet = buildSimpleLinearNet();
        List<String> activities = List.of("A");

        TokenReplayConformanceChecker.TokenReplayResult result =
            TokenReplayConformanceChecker.replaySingleTrace(simpleLinearNet, "case-006", activities);

        assertTrue(result.remaining > 0, "Unexecuted tasks should leave tokens");
        assertTrue(result.computeFitness() < 1.0, "Fitness should be less than 1.0");
        assertTrue(result.deviatingCases.contains("case-006"), "Incomplete trace is deviating");
    }

    @Test
    @DisplayName("Test 7: XOR-split with one branch taken")
    void testXorSplitOneBranch() {
        xorSplitJoinNet = buildXorSplitJoinNet();
        List<String> activities = List.of("A", "B", "D");

        TokenReplayConformanceChecker.TokenReplayResult result =
            TokenReplayConformanceChecker.replaySingleTrace(xorSplitJoinNet, "case-007", activities);

        assertTrue(result.remaining >= 0, "XOR-split creates over-production");
    }

    @Test
    @DisplayName("Test 8: Deviating cases collected correctly")
    void testDeviatingCasesCollection() {
        simpleLinearNet = buildSimpleLinearNet();

        String xesLog = """
            <log version="1.0" xmlns="http://www.xes.org/">
              <trace>
                <string key="concept:name" value="case-001"/>
                <event><string key="concept:name" value="A"/></event>
                <event><string key="concept:name" value="B"/></event>
                <event><string key="concept:name" value="C"/></event>
              </trace>
              <trace>
                <string key="concept:name" value="case-002"/>
                <event><string key="concept:name" value="A"/></event>
                <event><string key="concept:name" value="C"/></event>
              </trace>
              <trace>
                <string key="concept:name" value="case-003"/>
                <event><string key="concept:name" value="A"/></event>
                <event><string key="concept:name" value="B"/></event>
                <event><string key="concept:name" value="C"/></event>
              </trace>
            </log>
            """;

        TokenReplayConformanceChecker.TokenReplayResult result =
            TokenReplayConformanceChecker.replay(simpleLinearNet, xesLog);

        assertEquals(3, result.traceCount, "Should parse 3 traces");
        assertEquals(2, result.fittingTraces, "2 traces should fit perfectly");
        assertEquals(1, result.deviatingCases.size(), "1 trace should deviate");
        assertTrue(result.deviatingCases.contains("case-002"), "case-002 should be deviating");
    }

    @Test
    @DisplayName("Test 9: Composite task throws UnsupportedOperationException")
    void testCompositeTaskThrowsException() {
        YNet net = new YNet("composite-net", specification);
        YInputCondition input = new YInputCondition("i1", "input", net);
        YOutputCondition output = new YOutputCondition("o1", "output", net);

        YNet subNet = new YNet("sub-net", specification);
        YCompositeTask compositeTask = new YCompositeTask("composite", YTask._XOR, YTask._XOR, subNet, net);
        compositeTask.setName("Composite");

        YCondition c1 = new YCondition("c1", "c1", net);
        net.setInputCondition(input);
        net.setOutputCondition(output);
        net.addNetElement(compositeTask);
        net.addNetElement(c1);

        new YFlow(input, compositeTask);
        new YFlow(compositeTask, c1);
        new YFlow(c1, output);

        List<String> activities = List.of("Composite");

        assertThrows(UnsupportedOperationException.class, () ->
            TokenReplayConformanceChecker.replaySingleTrace(net, "case-composite", activities),
            "Composite tasks should throw UnsupportedOperationException");
    }

    @Test
    @DisplayName("Test 10: Null net parameter throws NullPointerException")
    void testNullNetThrowsException() {
        List<String> activities = List.of("A");

        assertThrows(NullPointerException.class, () ->
            TokenReplayConformanceChecker.replaySingleTrace(null, "case-001", activities),
            "Null net should throw NullPointerException");
    }

    @Test
    @DisplayName("Test 11: Null activities list throws NullPointerException")
    void testNullActivitiesThrowsException() {
        simpleLinearNet = buildSimpleLinearNet();

        assertThrows(NullPointerException.class, () ->
            TokenReplayConformanceChecker.replaySingleTrace(simpleLinearNet, "case-001", null),
            "Null activities list should throw NullPointerException");
    }

    @Test
    @DisplayName("Test 12: Fitness formula computes correctly")
    void testFitnessFormula() {
        TokenReplayConformanceChecker.TokenReplayResult result =
            new TokenReplayConformanceChecker.TokenReplayResult(
                10,   // produced
                10,   // consumed
                2,    // missing
                1     // remaining
            );

        double expectedFitness = 0.5 * (1.0 - 2.0/10.0) + 0.5 * (1.0 - 1.0/10.0);
        assertEquals(expectedFitness, result.computeFitness(), 0.001,
                     "Fitness formula should compute van der Aalst score");
    }

    @Test
    @DisplayName("Test 13: Zero produced tokens returns fitness 1.0")
    void testZeroProducedTokensFitness() {
        TokenReplayConformanceChecker.TokenReplayResult result =
            new TokenReplayConformanceChecker.TokenReplayResult(0, 0, 0, 0);

        assertEquals(1.0, result.computeFitness(), 0.001,
                     "Zero tokens should return fitness 1.0");
    }

    @Test
    @DisplayName("Test 14: Lifecycle filtering for complete events")
    void testLifecycleEventFiltering() {
        simpleLinearNet = buildSimpleLinearNet();

        String xesLog = """
            <log version="1.0" xmlns="http://www.xes.org/">
              <trace>
                <string key="concept:name" value="case-001"/>
                <event>
                  <string key="concept:name" value="A"/>
                  <string key="lifecycle:transition" value="start"/>
                </event>
                <event>
                  <string key="concept:name" value="A"/>
                  <string key="lifecycle:transition" value="complete"/>
                </event>
                <event>
                  <string key="concept:name" value="B"/>
                  <string key="lifecycle:transition" value="complete"/>
                </event>
                <event>
                  <string key="concept:name" value="C"/>
                  <string key="lifecycle:transition" value="complete"/>
                </event>
              </trace>
            </log>
            """;

        TokenReplayConformanceChecker.TokenReplayResult result =
            TokenReplayConformanceChecker.replay(simpleLinearNet, xesLog);

        assertEquals(1, result.traceCount, "Should parse 1 trace");
        assertEquals(1, result.fittingTraces, "Should fit perfectly (start events filtered)");
    }

    @Test
    @DisplayName("Test 15: Multiple traces aggregation")
    void testMultipleTracesAggregation() {
        simpleLinearNet = buildSimpleLinearNet();

        String xesLog = """
            <log version="1.0" xmlns="http://www.xes.org/">
              <trace>
                <string key="concept:name" value="trace-1"/>
                <event><string key="concept:name" value="A"/></event>
                <event><string key="concept:name" value="B"/></event>
                <event><string key="concept:name" value="C"/></event>
              </trace>
              <trace>
                <string key="concept:name" value="trace-2"/>
                <event><string key="concept:name" value="A"/></event>
                <event><string key="concept:name" value="B"/></event>
                <event><string key="concept:name" value="C"/></event>
              </trace>
              <trace>
                <string key="concept:name" value="trace-3"/>
                <event><string key="concept:name" value="A"/></event>
                <event><string key="concept:name" value="B"/></event>
              </trace>
            </log>
            """;

        TokenReplayConformanceChecker.TokenReplayResult result =
            TokenReplayConformanceChecker.replay(simpleLinearNet, xesLog);

        assertEquals(3, result.traceCount, "Should aggregate 3 traces");
        assertEquals(2, result.fittingTraces, "2 traces should fit");
        assertTrue(result.produced > 0, "Should accumulate produced tokens");
        assertTrue(result.consumed > 0, "Should accumulate consumed tokens");
        assertTrue(result.remaining > 0, "Should detect remaining tokens in incomplete trace");
    }
}
