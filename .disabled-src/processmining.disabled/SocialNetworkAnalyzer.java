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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.util.XNode;
import org.yawlfoundation.yawl.util.XNodeParser;

/**
 * Social Network Analysis over XES event logs.
 * Builds handover-of-work networks showing resource interaction patterns.
 * Based on van der Aalst's Social Network Miner (ProM plugin).
 *
 * Analyzes:
 * - Handover of Work: Resource A→B transitions in process execution
 * - Working Together: Resources in the same case
 * - Resource Workload: Event counts per resource
 * - Network Centrality: Resources with most connections
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class SocialNetworkAnalyzer {

    private static final Logger logger = LogManager.getLogger(SocialNetworkAnalyzer.class);
    private static final String UNKNOWN_RESOURCE = "UNKNOWN";

    /**
     * Result of social network analysis.
     */
    public static final class SocialNetworkResult {
        public final Set<String> resources;
        public final Map<String, Map<String, Long>> handoverMatrix;
        public final Map<String, Long> workloadByResource;
        public final String mostCentralResource;
        public final Map<String, Set<String>> workingTogether;

        SocialNetworkResult(
            Set<String> resources,
            Map<String, Map<String, Long>> handoverMatrix,
            Map<String, Long> workloadByResource,
            String mostCentralResource,
            Map<String, Set<String>> workingTogether) {
            this.resources = resources;
            this.handoverMatrix = handoverMatrix;
            this.workloadByResource = workloadByResource;
            this.mostCentralResource = mostCentralResource;
            this.workingTogether = workingTogether;
        }
    }

    /**
     * Analyze XES log for social network patterns (handover of work).
     *
     * @param xesXml XES log XML
     * @return social network result
     */
    public SocialNetworkResult analyze(String xesXml) {
        List<ParsedTrace> traces = parseTraces(xesXml);

        Map<String, Map<String, Long>> handoverMatrix = new HashMap<>();
        Map<String, Long> workloadByResource = new HashMap<>();
        Map<String, Set<String>> workingTogether = new HashMap<>();
        Set<String> allResources = new HashSet<>();

        for (ParsedTrace trace : traces) {
            // Build handover edges for this trace
            for (int i = 0; i < trace.resources.size() - 1; i++) {
                String fromResource = trace.resources.get(i);
                String toResource = trace.resources.get(i + 1);

                // Skip if either resource is unknown
                if (UNKNOWN_RESOURCE.equals(fromResource) || UNKNOWN_RESOURCE.equals(toResource)) {
                    continue;
                }

                // Ensure resources are in set
                allResources.add(fromResource);
                allResources.add(toResource);

                // Add to handover matrix
                handoverMatrix.computeIfAbsent(fromResource, k -> new HashMap<>())
                    .merge(toResource, 1L, Long::sum);
            }

            // Count workload for all resources in trace (including UNKNOWN)
            for (String resource : trace.resources) {
                workloadByResource.merge(resource, 1L, Long::sum);
                if (!UNKNOWN_RESOURCE.equals(resource)) {
                    allResources.add(resource);
                }
            }

            // Build working together relationships
            Set<String> uniqueResources = new HashSet<>(trace.resources);
            uniqueResources.remove(UNKNOWN_RESOURCE);
            for (String resource : uniqueResources) {
                Set<String> coworkers = uniqueResources.stream()
                    .filter(r -> !r.equals(resource))
                    .collect(Collectors.toSet());
                workingTogether.computeIfAbsent(resource, k -> new HashSet<>())
                    .addAll(coworkers);
            }
        }

        // Determine most central resource (highest degree in handover network)
        String mostCentral = findMostCentralResource(handoverMatrix, allResources);

        return new SocialNetworkResult(
            allResources,
            handoverMatrix,
            workloadByResource,
            mostCentral,
            workingTogether
        );
    }

    /**
     * Get top-N handover pairs by frequency.
     *
     * @param xesXml XES log XML
     * @param n number of top pairs to return
     * @return list of [fromResource, toResource, count] sorted by count descending
     */
    public List<String[]> getTopHandovers(String xesXml, int n) {
        SocialNetworkResult result = analyze(xesXml);

        List<String[]> pairs = new ArrayList<>();
        for (Map.Entry<String, Map<String, Long>> fromEntry : result.handoverMatrix.entrySet()) {
            String fromRes = fromEntry.getKey();
            for (Map.Entry<String, Long> toEntry : fromEntry.getValue().entrySet()) {
                String toRes = toEntry.getKey();
                Long count = toEntry.getValue();
                pairs.add(new String[]{fromRes, toRes, count.toString()});
            }
        }

        // Sort by count descending (third element)
        pairs.sort((a, b) -> Long.compare(
            Long.parseLong(b[2]),
            Long.parseLong(a[2])
        ));

        // Return top N
        return pairs.stream().limit(n).collect(Collectors.toList());
    }

    /**
     * Find the most central resource (highest total degree in handover network).
     *
     * @param handoverMatrix handover adjacency matrix
     * @param allResources all resources in network
     * @return most central resource name, or null if empty
     */
    private String findMostCentralResource(
        Map<String, Map<String, Long>> handoverMatrix,
        Set<String> allResources) {
        if (allResources.isEmpty()) {
            return null;
        }

        Map<String, Long> centrality = new HashMap<>();

        // Sum outgoing edges
        for (Map.Entry<String, Map<String, Long>> entry : handoverMatrix.entrySet()) {
            String resource = entry.getKey();
            long outDegree = entry.getValue().values().stream()
                .mapToLong(Long::longValue)
                .sum();
            centrality.merge(resource, outDegree, Long::sum);
        }

        // Sum incoming edges
        for (Map.Entry<String, Map<String, Long>> entry : handoverMatrix.entrySet()) {
            for (Map.Entry<String, Long> inner : entry.getValue().entrySet()) {
                String toResource = inner.getKey();
                long count = inner.getValue();
                centrality.merge(toResource, count, Long::sum);
            }
        }

        // Find resource with highest centrality
        return centrality.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
    }

    /**
     * Parse traces from XES XML, extracting resources from each event.
     *
     * @param xesXml XES log XML
     * @return list of parsed traces
     */
    private static List<ParsedTrace> parseTraces(String xesXml) {
        List<ParsedTrace> traces = new ArrayList<>();
        if (xesXml == null || xesXml.isEmpty()) {
            return traces;
        }
        try {
            XNode root = new XNodeParser().parse(xesXml);
            if (root == null) return traces;

            for (XNode traceNode : root.getChildren("trace")) {
                String caseId = getStringAttr(traceNode, "concept:name");
                if (caseId == null) caseId = "unknown";

                List<String> resources = new ArrayList<>();
                for (XNode eventNode : traceNode.getChildren("event")) {
                    String resource = getStringAttr(eventNode, "org:resource");
                    if (resource == null || resource.isEmpty()) {
                        resource = UNKNOWN_RESOURCE;
                    }
                    resources.add(resource);
                }
                traces.add(new ParsedTrace(caseId, resources));
            }
        } catch (Exception e) {
            logger.warn("Failed to parse XES trace data: " + e.getMessage(), e);
        }
        return traces;
    }

    /**
     * Extract string attribute from XNode.
     *
     * @param node XNode to search
     * @param key attribute key
     * @return attribute value or null if not found
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
     * Parsed trace with resources.
     */
    private static final class ParsedTrace {
        final String caseId;
        final List<String> resources;

        ParsedTrace(String caseId, List<String> resources) {
            this.caseId = caseId;
            this.resources = resources;
        }
    }
}
