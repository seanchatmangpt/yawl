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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.yawlfoundation.yawl.util.XNode;
import org.yawlfoundation.yawl.util.XNodeParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Performance analysis over XES event logs.
 * Computes flow time, throughput, and activity-level statistics.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class PerformanceAnalyzer {

    /**
     * Result of performance analysis.
     */
    public static final class PerformanceResult {
        public final int traceCount;
        public final double avgFlowTimeMs;
        public final double throughputPerHour;
        public final Map<String, Long> activityCounts;
        public final Map<String, Double> avgTimeBetweenActivities;

        PerformanceResult(int traceCount, double avgFlowTimeMs, double throughputPerHour,
                         Map<String, Long> activityCounts,
                         Map<String, Double> avgTimeBetweenActivities) {
            this.traceCount = traceCount;
            this.avgFlowTimeMs = avgFlowTimeMs;
            this.throughputPerHour = throughputPerHour;
            this.activityCounts = activityCounts;
            this.avgTimeBetweenActivities = avgTimeBetweenActivities;
        }
    }

    /**
     * Analyze XES log for performance metrics.
     *
     * @param xesXml XES log XML
     * @return performance result
     */
    public PerformanceResult analyze(String xesXml) {
        List<ParsedTrace> traces = parseTraces(xesXml);
        Map<String, Long> activityCounts = new HashMap<>();
        Map<String, List<Long>> pairDurations = new HashMap<>();
        double totalFlowTime = 0;

        for (ParsedTrace t : traces) {
            if (t.startTime != null && t.endTime != null) {
                long flow = t.endTime - t.startTime;
                totalFlowTime += flow;
            }
            for (Event e : t.events) {
                activityCounts.merge(e.activity, 1L, Long::sum);
            }
            for (int i = 0; i < t.events.size() - 1; i++) {
                Event a = t.events.get(i);
                Event b = t.events.get(i + 1);
                if (a.timestamp != null && b.timestamp != null) {
                    long dur = b.timestamp - a.timestamp;
                    String pair = a.activity + ">>" + b.activity;
                    pairDurations.computeIfAbsent(pair, k -> new ArrayList<>()).add(dur);
                }
            }
        }

        double avgFlow = traces.isEmpty() ? 0 : totalFlowTime / traces.size();
        double throughput = (traces.isEmpty() || avgFlow <= 0)
            ? 0 : (3600000.0 * traces.size()) / totalFlowTime;

        Map<String, Double> avgBetween = new HashMap<>();
        for (Map.Entry<String, List<Long>> e : pairDurations.entrySet()) {
            double avg = e.getValue().stream().mapToLong(Long::longValue).average().orElse(0);
            avgBetween.put(e.getKey(), avg);
        }

        return new PerformanceResult(traces.size(), avgFlow, throughput,
            activityCounts, avgBetween);
    }

    private static List<ParsedTrace> parseTraces(String xesXml) {
        List<ParsedTrace> traces = new ArrayList<>();
        if (xesXml == null || xesXml.isEmpty()) return traces;
        try {
            XNode root = new XNodeParser().parse(xesXml);
            if (root == null) return traces;
            for (XNode traceNode : root.getChildren("trace")) {
                String caseId = getStringAttr(traceNode, "concept:name");
                if (caseId == null) caseId = "unknown";
                List<Event> events = new ArrayList<>();
                Long startTime = null;
                Long endTime = null;
                for (XNode eventNode : traceNode.getChildren("event")) {
                    String act = getStringAttr(eventNode, "concept:name");
                    String ts = getStringAttr(eventNode, "time:timestamp");
                    Long timestamp = parseTimestamp(ts);
                    if (act != null && !act.isEmpty()) {
                        events.add(new Event(act, timestamp));
                        if (timestamp != null) {
                            if (startTime == null || timestamp < startTime) startTime = timestamp;
                            if (endTime == null || timestamp > endTime) endTime = timestamp;
                        }
                    }
                }
                traces.add(new ParsedTrace(caseId, events, startTime, endTime));
            }
        } catch (Exception e) {
            logger.warn("Failed to parse XES trace data: " + e.getMessage(), e);
        }
        return traces;
    }

    private static String getStringAttr(XNode node, String key) {
        for (XNode child : node.getChildren()) {
            if ("string".equals(child.getName()) && key.equals(child.getAttributeValue("key"))) {
                return child.getAttributeValue("value");
            }
        }
        return null;
    }

    private static Long parseTimestamp(String ts) {
        if (ts == null || ts.isEmpty()) return null;
        try {
            return java.time.Instant.parse(ts).toEpochMilli();
        } catch (Exception e) {
            return null;
        }
    }

    private static final class Event {
        final String activity;
        final Long timestamp;

        Event(String activity, Long timestamp) {
            this.activity = activity;
            this.timestamp = timestamp;
        }
    }

    private static final class ParsedTrace {
        final String caseId;
        final List<Event> events;
        final Long startTime;
        final Long endTime;

        ParsedTrace(String caseId, List<Event> events, Long startTime, Long endTime) {
            this.caseId = caseId;
            this.events = events;
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }
}
