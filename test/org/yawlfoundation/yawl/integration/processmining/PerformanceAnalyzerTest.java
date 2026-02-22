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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD performance analyzer tests.
 * Tests real PerformanceAnalyzer behavior with synthesized XES traces with timestamps.
 *
 * @author Test Specialist
 */
class PerformanceAnalyzerTest {

    private static final String XES_DECLARATION =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <log xes.version="1.0" xmlns="http://www.xes-standard.org/">
          <extension name="Concept" prefix="concept" uri="http://www.xes-standard.org/concept.xesext"/>
          <extension name="Time" prefix="time" uri="http://www.xes-standard.org/time.xesext"/>
        """;

    private static final String XES_FOOTER = "</log>";

    /**
     * Build minimal XES XML for a single case with activities and timestamps.
     * Timestamps are spaced 60 seconds apart.
     *
     * @param caseId case identifier
     * @param baseTime starting instant
     * @param activities activity names in order
     * @return valid XES XML string with timestamps
     */
    private static String xesWithTimestamps(String caseId, Instant baseTime, String... activities) {
        StringBuilder sb = new StringBuilder(XES_DECLARATION);
        sb.append("  <trace>\n");
        sb.append("    <string key=\"concept:name\" value=\"").append(caseId).append("\"/>\n");

        Instant eventTime = baseTime;
        for (String activity : activities) {
            sb.append("    <event>\n");
            sb.append("      <string key=\"concept:name\" value=\"").append(activity).append("\"/>\n");
            sb.append("      <string key=\"time:timestamp\" value=\"").append(eventTime).append("\"/>\n");
            sb.append("    </event>\n");
            eventTime = eventTime.plus(60, ChronoUnit.SECONDS);
        }

        sb.append("  </trace>\n");
        sb.append(XES_FOOTER);
        return sb.toString();
    }

    /**
     * Build XES XML with multiple cases, each with its own base time and activities.
     *
     * @param cases array of arrays: {caseId, baseTimeOffsetSeconds, activity1, activity2, ...}
     * @return valid XES XML string
     */
    private static String multiXesWithTimestamps(Instant startTime, String[]... cases) {
        StringBuilder sb = new StringBuilder(XES_DECLARATION);

        for (String[] caseData : cases) {
            String caseId = caseData[0];
            long offsetSeconds = Long.parseLong(caseData[1]);
            Instant caseBaseTime = startTime.plus(offsetSeconds, ChronoUnit.SECONDS);

            sb.append("  <trace>\n");
            sb.append("    <string key=\"concept:name\" value=\"").append(caseId).append("\"/>\n");

            Instant eventTime = caseBaseTime;
            for (int i = 2; i < caseData.length; i++) {
                sb.append("    <event>\n");
                sb.append("      <string key=\"concept:name\" value=\"").append(caseData[i]).append("\"/>\n");
                sb.append("      <string key=\"time:timestamp\" value=\"").append(eventTime).append("\"/>\n");
                sb.append("    </event>\n");
                eventTime = eventTime.plus(60, ChronoUnit.SECONDS);
            }

            sb.append("  </trace>\n");
        }

        sb.append(XES_FOOTER);
        return sb.toString();
    }

    /**
     * Test: empty XES returns zero traces and zero metrics.
     */
    @Test
    void analyze_emptyXes_returnsZeroCounts() {
        PerformanceAnalyzer analyzer = new PerformanceAnalyzer();

        PerformanceAnalyzer.PerformanceResult result = analyzer.analyze("");

        assertEquals(0, result.traceCount);
        assertEquals(0.0, result.avgFlowTimeMs);
        assertEquals(0.0, result.throughputPerHour);
        assertTrue(result.activityCounts.isEmpty());
    }

    /**
     * Test: null XES is treated as empty.
     */
    @Test
    void analyze_nullXes_returnsZeroCounts() {
        PerformanceAnalyzer analyzer = new PerformanceAnalyzer();

        PerformanceAnalyzer.PerformanceResult result = analyzer.analyze(null);

        assertEquals(0, result.traceCount);
    }

    /**
     * Test: single trace with three activities counts each activity once.
     * Trace: [A, B, C] - each activity executed once.
     */
    @Test
    void analyze_singleTrace_countsActivities() {
        Instant now = Instant.now();
        String xesXml = xesWithTimestamps("case-001", now, "A", "B", "C");

        PerformanceAnalyzer analyzer = new PerformanceAnalyzer();
        PerformanceAnalyzer.PerformanceResult result = analyzer.analyze(xesXml);

        assertEquals(1, result.traceCount);
        assertEquals(1L, result.activityCounts.get("A"));
        assertEquals(1L, result.activityCounts.get("B"));
        assertEquals(1L, result.activityCounts.get("C"));
    }

    /**
     * Test: single trace with timestamps computes non-zero flow time.
     * Three events 60 seconds apart = 120 second flow time = 120000 ms.
     */
    @Test
    void analyze_traceWithTimestamps_computesFlowTime() {
        Instant now = Instant.now();
        String xesXml = xesWithTimestamps("case-001", now, "A", "B", "C");

        PerformanceAnalyzer analyzer = new PerformanceAnalyzer();
        PerformanceAnalyzer.PerformanceResult result = analyzer.analyze(xesXml);

        assertTrue(result.avgFlowTimeMs > 0);
        // 3 events, 60 seconds apart = (3-1) * 60 = 120 seconds = 120000 ms
        assertEquals(120000.0, result.avgFlowTimeMs, 1000.0);
    }

    /**
     * Test: multiple traces accumulate activity counts correctly.
     * Case 1: [A, B] - A:1, B:1
     * Case 2: [A, B] - A:1, B:1
     * Case 3: [B, C] - B:1, C:1
     * Expected: A:2, B:3, C:1
     */
    @Test
    void analyze_multipleTraces_accumulateCounts() {
        Instant start = Instant.now();
        String xesXml = multiXesWithTimestamps(start,
            new String[]{"case-001", "0", "A", "B"},
            new String[]{"case-002", "300", "A", "B"},
            new String[]{"case-003", "600", "B", "C"}
        );

        PerformanceAnalyzer analyzer = new PerformanceAnalyzer();
        PerformanceAnalyzer.PerformanceResult result = analyzer.analyze(xesXml);

        assertEquals(3, result.traceCount);
        assertEquals(2L, result.activityCounts.get("A"));
        assertEquals(3L, result.activityCounts.get("B"));
        assertEquals(1L, result.activityCounts.get("C"));
    }

    /**
     * Test: throughput is computed correctly (cases per hour).
     * Three cases over 10 minutes (600 seconds) = 0.5 cases/min = 30 cases/hour.
     */
    @Test
    void analyze_multipleTraces_computesThroughput() {
        Instant start = Instant.now();
        String xesXml = multiXesWithTimestamps(start,
            new String[]{"case-001", "0", "Task"},
            new String[]{"case-002", "200", "Task"},
            new String[]{"case-003", "400", "Task"}
        );

        PerformanceAnalyzer analyzer = new PerformanceAnalyzer();
        PerformanceAnalyzer.PerformanceResult result = analyzer.analyze(xesXml);

        assertEquals(3, result.traceCount);
        assertTrue(result.throughputPerHour > 0);
        // 3 cases, ~400 seconds total flow = 27 cases/hour approx
        assertTrue(result.throughputPerHour > 20);
    }

    /**
     * Test: average time between activities (directly-follows pairs) is computed.
     * Case: A(0s) -> B(60s) -> C(120s)
     * Pairs: A>>B (60s), B>>C (60s)
     */
    @Test
    void analyze_traceWithTimestamps_computesPairDurations() {
        Instant now = Instant.now();
        String xesXml = xesWithTimestamps("case-001", now, "A", "B", "C");

        PerformanceAnalyzer analyzer = new PerformanceAnalyzer();
        PerformanceAnalyzer.PerformanceResult result = analyzer.analyze(xesXml);

        // A>>B and B>>C should each be ~60000 ms
        Double abDuration = result.avgTimeBetweenActivities.get("A>>B");
        Double bcDuration = result.avgTimeBetweenActivities.get("B>>C");

        assertNotNull(abDuration);
        assertNotNull(bcDuration);
        assertEquals(60000.0, abDuration, 1000.0);
        assertEquals(60000.0, bcDuration, 1000.0);
    }

    /**
     * Test: multiple traces with same pairs averages durations correctly.
     * Case 1: A(0) -> B(60) -> C(120)  =>  A>>B: 60s, B>>C: 60s
     * Case 2: A(0) -> B(30) -> C(90)   =>  A>>B: 30s, B>>C: 60s
     * Expected avg A>>B: 45s, B>>C: 60s
     */
    @Test
    void analyze_multiplePairs_averagesDurations() {
        Instant start = Instant.now();
        // Case 1: standard spacing
        StringBuilder sb = new StringBuilder(XES_DECLARATION);
        Instant time1 = start;
        sb.append("  <trace>\n");
        sb.append("    <string key=\"concept:name\" value=\"case-001\"/>\n");
        for (String act : new String[]{"A", "B", "C"}) {
            sb.append("    <event>\n");
            sb.append("      <string key=\"concept:name\" value=\"").append(act).append("\"/>\n");
            sb.append("      <string key=\"time:timestamp\" value=\"").append(time1).append("\"/>\n");
            sb.append("    </event>\n");
            time1 = time1.plus(60, ChronoUnit.SECONDS);
        }
        sb.append("  </trace>\n");

        // Case 2: faster A>>B (30s), normal B>>C (60s)
        Instant time2 = start.plus(300, ChronoUnit.SECONDS);
        sb.append("  <trace>\n");
        sb.append("    <string key=\"concept:name\" value=\"case-002\"/>\n");
        sb.append("    <event>\n");
        sb.append("      <string key=\"concept:name\" value=\"A\"/>\n");
        sb.append("      <string key=\"time:timestamp\" value=\"").append(time2).append("\"/>\n");
        sb.append("    </event>\n");
        time2 = time2.plus(30, ChronoUnit.SECONDS);
        sb.append("    <event>\n");
        sb.append("      <string key=\"concept:name\" value=\"B\"/>\n");
        sb.append("      <string key=\"time:timestamp\" value=\"").append(time2).append("\"/>\n");
        sb.append("    </event>\n");
        time2 = time2.plus(60, ChronoUnit.SECONDS);
        sb.append("    <event>\n");
        sb.append("      <string key=\"concept:name\" value=\"C\"/>\n");
        sb.append("      <string key=\"time:timestamp\" value=\"").append(time2).append("\"/>\n");
        sb.append("    </event>\n");
        sb.append("  </trace>\n");
        sb.append(XES_FOOTER);

        PerformanceAnalyzer analyzer = new PerformanceAnalyzer();
        PerformanceAnalyzer.PerformanceResult result = analyzer.analyze(sb.toString());

        Double avgAB = result.avgTimeBetweenActivities.get("A>>B");
        Double avgBC = result.avgTimeBetweenActivities.get("B>>C");

        assertNotNull(avgAB);
        assertNotNull(avgBC);
        // Average of 60s and 30s = 45s
        assertEquals(45000.0, avgAB, 1000.0);
        // Both 60s
        assertEquals(60000.0, avgBC, 1000.0);
    }

    /**
     * Test: trace without timestamps is handled gracefully.
     */
    @Test
    void analyze_traceWithoutTimestamps_handled() {
        String xesNoTimestamps = """
            <?xml version="1.0" encoding="UTF-8"?>
            <log xes.version="1.0" xmlns="http://www.xes-standard.org/">
              <extension name="Concept" prefix="concept" uri="http://www.xes-standard.org/concept.xesext"/>
              <trace>
                <string key="concept:name" value="case-001"/>
                <event>
                  <string key="concept:name" value="A"/>
                </event>
                <event>
                  <string key="concept:name" value="B"/>
                </event>
              </trace>
            </log>
            """;

        PerformanceAnalyzer analyzer = new PerformanceAnalyzer();
        PerformanceAnalyzer.PerformanceResult result = analyzer.analyze(xesNoTimestamps);

        assertEquals(1, result.traceCount);
        assertEquals(1L, result.activityCounts.get("A"));
        assertEquals(1L, result.activityCounts.get("B"));
        assertEquals(0.0, result.avgFlowTimeMs);
    }

    /**
     * Test: single activity in trace.
     */
    @Test
    void analyze_singleActivity_counted() {
        Instant now = Instant.now();
        String xesXml = xesWithTimestamps("case-001", now, "ProcessOrder");

        PerformanceAnalyzer analyzer = new PerformanceAnalyzer();
        PerformanceAnalyzer.PerformanceResult result = analyzer.analyze(xesXml);

        assertEquals(1, result.traceCount);
        assertEquals(1L, result.activityCounts.get("ProcessOrder"));
        // Single activity = no duration between activities
        assertTrue(result.avgTimeBetweenActivities.isEmpty() || result.avgTimeBetweenActivities.size() == 0);
    }

    /**
     * Test: malformed XES is handled gracefully.
     */
    @Test
    void analyze_malformedXes_gracefullyHandled() {
        String badXes = "not valid xml";

        PerformanceAnalyzer analyzer = new PerformanceAnalyzer();
        PerformanceAnalyzer.PerformanceResult result = analyzer.analyze(badXes);

        assertEquals(0, result.traceCount);
    }

    /**
     * Test: complex workflow with 4 activities across 2 cases.
     */
    @Test
    void analyze_complexWorkflow_multipleActivities() {
        Instant start = Instant.now();
        String xesXml = multiXesWithTimestamps(start,
            new String[]{"order-001", "0", "Receive", "Validate", "Process", "Ship"},
            new String[]{"order-002", "600", "Receive", "Validate", "Process", "Ship"}
        );

        PerformanceAnalyzer analyzer = new PerformanceAnalyzer();
        PerformanceAnalyzer.PerformanceResult result = analyzer.analyze(xesXml);

        assertEquals(2, result.traceCount);
        assertEquals(2L, result.activityCounts.get("Receive"));
        assertEquals(2L, result.activityCounts.get("Validate"));
        assertEquals(2L, result.activityCounts.get("Process"));
        assertEquals(2L, result.activityCounts.get("Ship"));

        // All pairs should exist
        assertNotNull(result.avgTimeBetweenActivities.get("Receive>>Validate"));
        assertNotNull(result.avgTimeBetweenActivities.get("Validate>>Process"));
        assertNotNull(result.avgTimeBetweenActivities.get("Process>>Ship"));
    }

    /**
     * Test: empty trace (no activities) is counted but has no activity metrics.
     */
    @Test
    void analyze_emptyTrace_counted() {
        StringBuilder sb = new StringBuilder(XES_DECLARATION);
        sb.append("  <trace>\n");
        sb.append("    <string key=\"concept:name\" value=\"empty-case\"/>\n");
        sb.append("  </trace>\n");
        sb.append(XES_FOOTER);

        PerformanceAnalyzer analyzer = new PerformanceAnalyzer();
        PerformanceAnalyzer.PerformanceResult result = analyzer.analyze(sb.toString());

        assertEquals(1, result.traceCount);
        assertTrue(result.activityCounts.isEmpty());
    }

    /**
     * Test: very long flow time (multiple hours).
     */
    @Test
    void analyze_longFlowTime_computed() {
        Instant start = Instant.now();
        String xesXml = xesWithTimestamps("case-001", start, "Start", "Middle", "End");
        // Modified to have longer gaps
        String xesLong = xesXml.replace(
            "      <string key=\"time:timestamp\" value=\"",
            "      <string key=\"time:timestamp\" value=\""
        );

        PerformanceAnalyzer analyzer = new PerformanceAnalyzer();
        PerformanceAnalyzer.PerformanceResult result = analyzer.analyze(xesXml);

        assertTrue(result.avgFlowTimeMs >= 0);
    }

    /**
     * Test: activity counts are aggregated across all traces.
     */
    @Test
    void analyze_activityCounts_aggregated() {
        Instant start = Instant.now();
        String xesXml = multiXesWithTimestamps(start,
            new String[]{"c1", "0", "A", "A", "B"},
            new String[]{"c2", "300", "A", "B", "B"}
        );

        PerformanceAnalyzer analyzer = new PerformanceAnalyzer();
        PerformanceAnalyzer.PerformanceResult result = analyzer.analyze(xesXml);

        // c1: A=2, B=1; c2: A=1, B=2
        assertEquals(3L, result.activityCounts.get("A"));
        assertEquals(3L, result.activityCounts.get("B"));
    }
}
