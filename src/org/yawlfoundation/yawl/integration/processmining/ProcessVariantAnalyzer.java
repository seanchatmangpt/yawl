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

import java.util.*;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.util.XNode;
import org.yawlfoundation.yawl.util.XNodeParser;

/**
 * Process variant analysis over XES event logs.
 *
 * A process variant is a unique sequence of activities observed across cases.
 * Variant analysis answers: "What are the top-5 variants? What % of cases are conforming?"
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class ProcessVariantAnalyzer {

    private static final Logger logger = LogManager.getLogger(ProcessVariantAnalyzer.class);

    /**
     * Represents a unique process variant (activity sequence).
     */
    public static final class Variant {
        public final List<String> activities;
        public final int frequency;
        public final double relativeFrequency;
        public final Set<String> caseIds;

        Variant(List<String> activities, int frequency, double relativeFrequency, Set<String> caseIds) {
            this.activities = Collections.unmodifiableList(activities);
            this.frequency = frequency;
            this.relativeFrequency = relativeFrequency;
            this.caseIds = Collections.unmodifiableSet(caseIds);
        }
    }

    /**
     * Result of process variant analysis.
     */
    public static final class VariantAnalysisResult {
        public final int totalTraces;
        public final int variantCount;
        public final List<Variant> variants;
        public final double topVariantCoverage;
        public final double top5Coverage;

        VariantAnalysisResult(int totalTraces, int variantCount, List<Variant> variants,
                              double topVariantCoverage, double top5Coverage) {
            this.totalTraces = totalTraces;
            this.variantCount = variantCount;
            this.variants = Collections.unmodifiableList(variants);
            this.topVariantCoverage = topVariantCoverage;
            this.top5Coverage = top5Coverage;
        }
    }

    /**
     * Analyze XES log for process variants.
     *
     * @param xesXml XES log XML string
     * @return variant analysis result
     */
    public VariantAnalysisResult analyze(String xesXml) {
        List<ParsedTrace> traces = parseTraces(xesXml);

        if (traces.isEmpty()) {
            return new VariantAnalysisResult(0, 0, Collections.emptyList(), 0.0, 0.0);
        }

        // Group traces by activity sequence
        Map<String, VariantData> variantMap = new HashMap<>();

        for (ParsedTrace trace : traces) {
            String variantKey = String.join(",", trace.activities);
            VariantData data = variantMap.computeIfAbsent(variantKey, k ->
                new VariantData(new ArrayList<>(trace.activities)));
            data.caseIds.add(trace.caseId);
            data.frequency++;
        }

        // Sort variants by frequency descending
        List<Variant> sortedVariants = variantMap.values().stream()
            .map(data -> new Variant(
                data.activities,
                data.frequency,
                (double) data.frequency / traces.size(),
                data.caseIds
            ))
            .sorted(Comparator.comparingInt((Variant v) -> v.frequency).reversed())
            .collect(Collectors.toList());

        // Calculate top variant coverage
        double topVariantCoverage = sortedVariants.isEmpty() ? 0.0 :
            (double) sortedVariants.get(0).frequency / traces.size();

        // Calculate top-5 coverage
        double top5Coverage = sortedVariants.stream()
            .limit(5)
            .mapToInt(v -> v.frequency)
            .sum() / (double) traces.size();

        return new VariantAnalysisResult(
            traces.size(),
            sortedVariants.size(),
            sortedVariants,
            topVariantCoverage,
            top5Coverage
        );
    }

    /**
     * Get the top-N most frequent variants.
     *
     * @param xesXml XES log XML string
     * @param n number of top variants to return
     * @return list of top-N variants, sorted by frequency descending
     */
    public List<Variant> getTopVariants(String xesXml, int n) {
        VariantAnalysisResult result = analyze(xesXml);
        return result.variants.stream()
            .limit(Math.max(0, n))
            .collect(Collectors.toList());
    }

    /**
     * Find deviating cases (cases not in the most common variant).
     *
     * @param xesXml XES log XML string
     * @return set of case IDs that deviate from the most common variant
     */
    public Set<String> getDeviatingCases(String xesXml) {
        VariantAnalysisResult result = analyze(xesXml);

        if (result.variants.isEmpty()) {
            return Collections.emptySet();
        }

        // Get case IDs from the most common variant
        Set<String> conformingCases = result.variants.get(0).caseIds;

        // Find all deviating cases
        Set<String> deviatingCases = new HashSet<>();
        for (Variant variant : result.variants) {
            if (variant != result.variants.get(0)) {
                deviatingCases.addAll(variant.caseIds);
            }
        }

        return deviatingCases;
    }

    /**
     * Parse XES traces from XML.
     *
     * @param xesXml XES log XML string
     * @return list of parsed traces
     */
    private static List<ParsedTrace> parseTraces(String xesXml) {
        List<ParsedTrace> traces = new ArrayList<>();

        if (xesXml == null || xesXml.isEmpty()) {
            return traces;
        }

        try {
            XNode root = new XNodeParser().parse(xesXml);
            if (root == null) {
                return traces;
            }

            for (XNode traceNode : root.getChildren("trace")) {
                String caseId = getStringAttr(traceNode, "concept:name");
                if (caseId == null) {
                    caseId = "unknown";
                }

                List<String> activities = new ArrayList<>();
                for (XNode eventNode : traceNode.getChildren("event")) {
                    String activity = getStringAttr(eventNode, "concept:name");
                    if (activity != null && !activity.isEmpty()) {
                        activities.add(activity);
                    }
                }

                traces.add(new ParsedTrace(caseId, activities));
            }
        } catch (Exception e) {
            logger.warn("Failed to parse XES trace data: {}", e.getMessage(), e);
        }

        return traces;
    }

    /**
     * Extract a string attribute from an XNode.
     *
     * @param node XNode to extract from
     * @param key attribute key
     * @return attribute value, or null if not found
     */
    private static String getStringAttr(XNode node, String key) {
        for (XNode child : node.getChildren()) {
            if ("string".equals(child.getName()) && key.equals(child.getAttributeValue("key"))) {
                return child.getAttributeValue("value");
            }
        }
        return null;
    }

    /**
     * Internal class to hold variant data during aggregation.
     */
    private static final class VariantData {
        List<String> activities;
        int frequency;
        Set<String> caseIds;

        VariantData(List<String> activities) {
            this.activities = activities;
            this.frequency = 0;
            this.caseIds = new HashSet<>();
        }
    }

    /**
     * Internal class to represent a parsed trace during analysis.
     */
    private static final class ParsedTrace {
        String caseId;
        List<String> activities;

        ParsedTrace(String caseId, List<String> activities) {
            this.caseId = caseId;
            this.activities = activities;
        }
    }
}
