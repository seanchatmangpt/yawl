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
 */

package org.yawlfoundation.yawl.integration.processmining;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD conformance analyzer tests.
 * Tests real ConformanceAnalyzer behavior with synthesized XES traces.
 *
 * @author Test Specialist
 */
class ConformanceAnalyzerTest {

    private static final String XES_DECLARATION =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <log xes.version="1.0" xmlns="http://www.xes-standard.org/">
          <extension name="Concept" prefix="concept" uri="http://www.xes-standard.org/concept.xesext"/>
          <extension name="Time" prefix="time" uri="http://www.xes-standard.org/time.xesext"/>
        """;

    private static final String XES_FOOTER = "</log>";

    /**
     * Build minimal XES XML for a single case with ordered activities.
     * Each activity becomes an event with concept:name.
     *
     * @param caseId case identifier
     * @param activities activity names in order
     * @return valid XES XML string
     */
    private static String xes(String caseId, String... activities) {
        StringBuilder sb = new StringBuilder(XES_DECLARATION);
        sb.append("  <trace>\n");
        sb.append("    <string key=\"concept:name\" value=\"").append(caseId).append("\"/>\n");
        for (String activity : activities) {
            sb.append("    <event>\n");
            sb.append("      <string key=\"concept:name\" value=\"").append(activity).append("\"/>\n");
            sb.append("    </event>\n");
        }
        sb.append("  </trace>\n");
        sb.append(XES_FOOTER);
        return sb.toString();
    }

    /**
     * Build XES XML with multiple cases.
     * Each inner array is a case with activities.
     *
     * @param cases array of case arrays: new String[][] {{"case1", "A", "B"}, {"case2", "A", "X"}}
     * @return valid XES XML string with multiple traces
     */
    private static String multiXes(String[][] cases) {
        StringBuilder sb = new StringBuilder(XES_DECLARATION);
        for (String[] caseData : cases) {
            String caseId = caseData[0];
            sb.append("  <trace>\n");
            sb.append("    <string key=\"concept:name\" value=\"").append(caseId).append("\"/>\n");
            for (int i = 1; i < caseData.length; i++) {
                sb.append("    <event>\n");
                sb.append("      <string key=\"concept:name\" value=\"").append(caseData[i]).append("\"/>\n");
                sb.append("    </event>\n");
            }
            sb.append("  </trace>\n");
        }
        sb.append(XES_FOOTER);
        return sb.toString();
    }

    /**
     * Test: empty XES returns zero trace count and perfect fitness.
     */
    @Test
    void analyze_emptyXes_returnsZeroCounts() {
        ConformanceAnalyzer analyzer = new ConformanceAnalyzer(
            new HashSet<>(), new HashSet<>());

        ConformanceAnalyzer.ConformanceResult result = analyzer.analyze("");

        assertEquals(0, result.traceCount);
        assertEquals(0, result.fittingTraces);
        assertEquals(1.0, result.fitness);
        assertTrue(result.observedActivities.isEmpty());
        assertTrue(result.deviatingTraces.isEmpty());
    }

    /**
     * Test: null XES is treated as empty.
     */
    @Test
    void analyze_nullXes_returnsZeroCounts() {
        ConformanceAnalyzer analyzer = new ConformanceAnalyzer(
            new HashSet<>(), new HashSet<>());

        ConformanceAnalyzer.ConformanceResult result = analyzer.analyze(null);

        assertEquals(0, result.traceCount);
        assertEquals(1.0, result.fitness);
    }

    /**
     * Test: single trace with activities matching expected set has fitness 1.0.
     * Trace: [A, B, C], expected activities: {A, B, C}, expected follows: {A>>B, B>>C}
     */
    @Test
    void analyze_singleTrace_allActivitiesFit() {
        String xesXml = xes("case-001", "A", "B", "C");

        Set<String> expectedActivities = Set.of("A", "B", "C");
        Set<String> expectedFollows = Set.of("A>>B", "B>>C");

        ConformanceAnalyzer analyzer = new ConformanceAnalyzer(
            expectedActivities, expectedFollows);

        ConformanceAnalyzer.ConformanceResult result = analyzer.analyze(xesXml);

        assertEquals(1, result.traceCount);
        assertEquals(1, result.fittingTraces);
        assertEquals(1.0, result.fitness);
        assertEquals(Set.of("A", "B", "C"), result.observedActivities);
        assertTrue(result.deviatingTraces.isEmpty());
    }

    /**
     * Test: single trace with unexpected activity is marked as deviating, fitness 0.0.
     * Trace: [A, X, C], expected activities: {A, B, C}
     */
    @Test
    void analyze_traceWithUnexpectedActivity_markedDeviating() {
        String xesXml = xes("case-001", "A", "X", "C");

        Set<String> expectedActivities = Set.of("A", "B", "C");
        Set<String> expectedFollows = Set.of("A>>B", "B>>C");

        ConformanceAnalyzer analyzer = new ConformanceAnalyzer(
            expectedActivities, expectedFollows);

        ConformanceAnalyzer.ConformanceResult result = analyzer.analyze(xesXml);

        assertEquals(1, result.traceCount);
        assertEquals(0, result.fittingTraces);
        assertEquals(0.0, result.fitness);
        assertTrue(result.observedActivities.contains("X"));
        assertEquals(Set.of("case-001"), result.deviatingTraces);
    }

    /**
     * Test: single trace with unexpected directly-follows pair is marked as deviating.
     * Trace: [A, B, C], expected follows: {A>>B} (missing B>>C)
     */
    @Test
    void analyze_traceWithUnexpectedFollow_markedDeviating() {
        String xesXml = xes("case-002", "A", "B", "C");

        Set<String> expectedActivities = Set.of("A", "B", "C");
        Set<String> expectedFollows = Set.of("A>>B");  // Missing B>>C

        ConformanceAnalyzer analyzer = new ConformanceAnalyzer(
            expectedActivities, expectedFollows);

        ConformanceAnalyzer.ConformanceResult result = analyzer.analyze(xesXml);

        assertEquals(1, result.traceCount);
        assertEquals(0, result.fittingTraces);
        assertEquals(0.0, result.fitness);
        assertEquals(Set.of("case-002"), result.deviatingTraces);
    }

    /**
     * Test: two traces, one fits and one deviates, fitness = 0.5.
     * Case 1: [A, B] fits
     * Case 2: [A, X] deviates (X is unexpected)
     * Expected activities: {A, B}
     */
    @Test
    void analyze_mixedTraces_correctFitnessRatio() {
        String xesXml = multiXes(new String[][] {
            {"case-001", "A", "B"},
            {"case-002", "A", "X"}
        });

        Set<String> expectedActivities = Set.of("A", "B");
        Set<String> expectedFollows = Set.of("A>>B");

        ConformanceAnalyzer analyzer = new ConformanceAnalyzer(
            expectedActivities, expectedFollows);

        ConformanceAnalyzer.ConformanceResult result = analyzer.analyze(xesXml);

        assertEquals(2, result.traceCount);
        assertEquals(1, result.fittingTraces);
        assertEquals(0.5, result.fitness);
        assertEquals(Set.of("case-002"), result.deviatingTraces);
        assertTrue(result.observedActivities.contains("X"));
    }

    /**
     * Test: three traces with varying conformance.
     * Case 1: [A, B, C] fits
     * Case 2: [A, B, C] fits
     * Case 3: [A, X, C] deviates
     * Expected fitness: 2/3 ≈ 0.667
     */
    @Test
    void analyze_threeTraces_twoFit() {
        String xesXml = multiXes(new String[][] {
            {"case-001", "A", "B", "C"},
            {"case-002", "A", "B", "C"},
            {"case-003", "A", "X", "C"}
        });

        Set<String> expectedActivities = Set.of("A", "B", "C");
        Set<String> expectedFollows = Set.of("A>>B", "B>>C");

        ConformanceAnalyzer analyzer = new ConformanceAnalyzer(
            expectedActivities, expectedFollows);

        ConformanceAnalyzer.ConformanceResult result = analyzer.analyze(xesXml);

        assertEquals(3, result.traceCount);
        assertEquals(2, result.fittingTraces);
        assertEquals(2.0 / 3.0, result.fitness, 0.001);
        assertEquals(Set.of("case-003"), result.deviatingTraces);
    }

    /**
     * Test: empty expected activities/follows means all traces fit.
     * When no constraints, all traces are conformant.
     */
    @Test
    void analyze_noConstraints_allTracesFit() {
        String xesXml = xes("case-001", "A", "X", "Z");

        ConformanceAnalyzer analyzer = new ConformanceAnalyzer(
            new HashSet<>(), new HashSet<>());

        ConformanceAnalyzer.ConformanceResult result = analyzer.analyze(xesXml);

        assertEquals(1, result.traceCount);
        assertEquals(1, result.fittingTraces);
        assertEquals(1.0, result.fitness);
        assertTrue(result.deviatingTraces.isEmpty());
        assertEquals(Set.of("A", "X", "Z"), result.observedActivities);
    }

    /**
     * Test: trace with single activity.
     */
    @Test
    void analyze_singleActivity_fits() {
        String xesXml = xes("case-123", "ProcessOrder");

        Set<String> expected = Set.of("ProcessOrder");

        ConformanceAnalyzer analyzer = new ConformanceAnalyzer(
            expected, new HashSet<>());

        ConformanceAnalyzer.ConformanceResult result = analyzer.analyze(xesXml);

        assertEquals(1, result.traceCount);
        assertEquals(1, result.fittingTraces);
        assertEquals(1.0, result.fitness);
        assertEquals(expected, result.observedActivities);
    }

    /**
     * Test: observed activities are correctly extracted and recorded.
     */
    @Test
    void analyze_observedActivities_extracted() {
        String xesXml = multiXes(new String[][] {
            {"case-001", "A", "B", "C"},
            {"case-002", "B", "C", "D"},
            {"case-003", "A", "E"}
        });

        ConformanceAnalyzer analyzer = new ConformanceAnalyzer(
            new HashSet<>(), new HashSet<>());

        ConformanceAnalyzer.ConformanceResult result = analyzer.analyze(xesXml);

        assertEquals(Set.of("A", "B", "C", "D", "E"), result.observedActivities);
    }

    /**
     * Test: constructor with null expected activities/follows is handled gracefully.
     */
    @Test
    void constructor_nullSets_handledGracefully() {
        assertDoesNotThrow(() ->
            new ConformanceAnalyzer(null, null)
        );

        ConformanceAnalyzer analyzer = new ConformanceAnalyzer(null, null);
        ConformanceAnalyzer.ConformanceResult result = analyzer.analyze(
            xes("case-001", "A", "B"));

        assertEquals(1, result.traceCount);
        assertEquals(1, result.fittingTraces);
    }

    /**
     * Test: complex workflow with 5 activities and multiple traces.
     */
    @Test
    void analyze_complexWorkflow_fiveActivities() {
        String xesXml = multiXes(new String[][] {
            {"proc-001", "Start", "Validate", "Process", "Approve", "End"},
            {"proc-002", "Start", "Validate", "Process", "Reject", "End"},
            {"proc-003", "Start", "Validate", "Process", "Approve", "End"}
        });

        Set<String> expected = Set.of("Start", "Validate", "Process", "Approve", "Reject", "End");
        Set<String> follows = Set.of(
            "Start>>Validate",
            "Validate>>Process",
            "Process>>Approve",
            "Process>>Reject",
            "Approve>>End",
            "Reject>>End"
        );

        ConformanceAnalyzer analyzer = new ConformanceAnalyzer(expected, follows);
        ConformanceAnalyzer.ConformanceResult result = analyzer.analyze(xesXml);

        assertEquals(3, result.traceCount);
        assertEquals(3, result.fittingTraces);
        assertEquals(1.0, result.fitness);
    }

    /**
     * Test: deviating trace is correctly identified by case ID.
     */
    @Test
    void analyze_deviatingTraceId_correctly_captured() {
        String xesXml = multiXes(new String[][] {
            {"success-case", "A", "B"},
            {"failure-case-xyz", "A", "X"}
        });

        Set<String> expected = Set.of("A", "B");

        ConformanceAnalyzer analyzer = new ConformanceAnalyzer(
            expected, Set.of("A>>B"));

        ConformanceAnalyzer.ConformanceResult result = analyzer.analyze(xesXml);

        assertEquals(Set.of("failure-case-xyz"), result.deviatingTraces);
    }

    /**
     * Test: malformed XES is handled gracefully (returns empty result).
     */
    @Test
    void analyze_malformedXes_gracefullyHandled() {
        String badXes = "not valid xml at all";

        ConformanceAnalyzer analyzer = new ConformanceAnalyzer(
            new HashSet<>(), new HashSet<>());

        ConformanceAnalyzer.ConformanceResult result = analyzer.analyze(badXes);

        assertEquals(0, result.traceCount);
        assertEquals(1.0, result.fitness);
    }

    /**
     * Test: trace with no activities (empty activity list).
     */
    @Test
    void analyze_traceWithNoActivities_counted() {
        StringBuilder sb = new StringBuilder(XES_DECLARATION);
        sb.append("  <trace>\n");
        sb.append("    <string key=\"concept:name\" value=\"empty-case\"/>\n");
        sb.append("  </trace>\n");
        sb.append(XES_FOOTER);
        String xesXml = sb.toString();

        ConformanceAnalyzer analyzer = new ConformanceAnalyzer(
            new HashSet<>(), new HashSet<>());

        ConformanceAnalyzer.ConformanceResult result = analyzer.analyze(xesXml);

        assertEquals(1, result.traceCount);
        assertEquals(1, result.fittingTraces);
        assertTrue(result.observedActivities.isEmpty());
    }
}
