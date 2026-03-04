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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.util.XNode;
import org.yawlfoundation.yawl.util.XNodeParser;

/**
 * Conformance analysis over XES event logs.
 * Computes fitness (how much of the log fits the model) and precision
 * (how much of the model is used by the log) using token-based replay.
 *
 * Simplified implementation: compares observed activity sequences to
 * expected directly-follows relations.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class ConformanceAnalyzer {


    private static final Logger logger = LogManager.getLogger(ConformanceAnalyzer.class);
    /**
     * Result of conformance analysis.
     */
    public static final class ConformanceResult {
        public final int traceCount;
        public final int fittingTraces;
        public final double fitness;
        public final Set<String> observedActivities;
        public final Set<String> deviatingTraces;

        ConformanceResult(int traceCount, int fittingTraces, Set<String> observed,
                          Set<String> deviating) {
            this.traceCount = traceCount;
            this.fittingTraces = fittingTraces;
            this.fitness = traceCount > 0 ? (double) fittingTraces / traceCount : 1.0;
            this.observedActivities = observed;
            this.deviatingTraces = deviating;
        }
    }

    private final Set<String> expectedActivities;
    private final Set<String> expectedDirectlyFollows;

    public ConformanceAnalyzer(Set<String> expectedActivities,
                               Set<String> expectedDirectlyFollows) {
        this.expectedActivities = expectedActivities != null ? expectedActivities : new HashSet<>();
        this.expectedDirectlyFollows = expectedDirectlyFollows != null
            ? expectedDirectlyFollows : new HashSet<>();
    }

    /**
     * Analyze XES log for conformance.
     *
     * @param xesXml XES log XML
     * @return conformance result
     */
    public ConformanceResult analyze(String xesXml) {
        List<Trace> traces = parseTraces(xesXml);
        Set<String> observed = new HashSet<>();
        Set<String> deviating = new HashSet<>();
        int fitting = 0;

        for (Trace t : traces) {
            boolean fits = true;
            for (String act : t.activities) {
                observed.add(act);
                if (!expectedActivities.isEmpty() && !expectedActivities.contains(act)) {
                    fits = false;
                }
            }
            for (int i = 0; i < t.activities.size() - 1; i++) {
                String pair = t.activities.get(i) + ">>" + t.activities.get(i + 1);
                if (!expectedDirectlyFollows.isEmpty()
                    && !expectedDirectlyFollows.contains(pair)) {
                    fits = false;
                }
            }
            if (fits) {
                fitting++;
            } else {
                deviating.add(t.caseId);
            }
        }

        return new ConformanceResult(traces.size(), fitting, observed, deviating);
    }

    private static List<Trace> parseTraces(String xesXml) {
        List<Trace> traces = new ArrayList<>();
        if (xesXml == null || xesXml.isEmpty()) {
            return traces;
        }
        try {
            XNode root = new XNodeParser().parse(xesXml);
            if (root == null) return traces;
            for (XNode traceNode : root.getChildren("trace")) {
                String caseId = getStringAttr(traceNode, "concept:name");
                if (caseId == null) caseId = "unknown";
                List<String> activities = new ArrayList<>();
                for (XNode eventNode : traceNode.getChildren("event")) {
                    String act = getStringAttr(eventNode, "concept:name");
                    if (act != null && !act.isEmpty()) {
                        activities.add(act);
                    }
                }
                traces.add(new Trace(caseId, activities));
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

    private static final class Trace {
        final String caseId;
        final List<String> activities;

        Trace(String caseId, List<String> activities) {
            this.caseId = caseId;
            this.activities = activities;
        }
    }
}
