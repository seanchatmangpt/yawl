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

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.util.XNode;
import org.yawlfoundation.yawl.util.XNodeParser;

/**
 * Single-entry-point facade for comprehensive YAWL process mining analysis.
 *
 * <p>Orchestrates all process mining components: XES export, performance analysis,
 * conformance checking (token-based replay), variant extraction, and OCEL conversion.
 * Users call one method ({@link #analyze(YSpecificationID, YNet, boolean)}) instead of
 * coordinating five separate classes.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>
 *   ProcessMiningFacade facade = new ProcessMiningFacade(engineUrl, user, password);
 *   ProcessMiningReport report = facade.analyze(specId, net);
 *   System.out.println("Fitness: " + report.conformance.computeFitness());
 *   System.out.println("Flow time: " + report.performance.avgFlowTimeMs + " ms");
 * </pre>
 *
 * <h2>Analysis Components</h2>
 * <ul>
 *   <li><b>XES Export</b> ({@link EventLogExporter}) - Event log in eXtensible Event Stream format</li>
 *   <li><b>Performance</b> ({@link PerformanceAnalyzer}) - Flow time, throughput, activity counts</li>
 *   <li><b>Conformance</b> ({@link TokenReplayConformanceChecker}) - Token-based replay fitness (if YNet provided)</li>
 *   <li><b>Variants</b> - Unique activity sequences ranked by frequency (inline computation)</li>
 *   <li><b>OCEL</b> ({@link OcelExporter}) - Object-centric event log for multi-instance analysis</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public final class ProcessMiningFacade {

    private static final Logger _log = LogManager.getLogger(ProcessMiningFacade.class);

    private final EventLogExporter exporter;

    /**
     * Complete process mining analysis report.
     *
     * <p>Immutable record containing results from all analysis phases: XES export,
     * conformance checking, performance analysis, variant discovery, and OCEL conversion.</p>
     */
    public static final class ProcessMiningReport {
        /**
         * XES event log (XML string). Can be exported to file or further analyzed.
         */
        public final String xesXml;

        /**
         * Conformance checking result from token-based replay.
         * Null if no YNet was provided during analysis.
         */
        public final TokenReplayConformanceChecker.TokenReplayResult conformance;

        /**
         * Performance metrics: flow time, throughput, activity counts.
         */
        public final PerformanceAnalyzer.PerformanceResult performance;

        /**
         * Unique activity sequence variants, ranked by frequency.
         * Map key: variant string (comma-joined activity names).
         * Map value: occurrence count.
         */
        public final Map<String, Long> variantFrequencies;

        /**
         * Total number of distinct variants.
         */
        public final int variantCount;

        /**
         * Object-centric event log in OCEL 2.0 JSON format.
         */
        public final String ocelJson;

        /**
         * Number of traces (cases) in the analysis.
         */
        public final int traceCount;

        /**
         * Specification ID being analyzed.
         */
        public final String specificationId;

        /**
         * Timestamp when analysis was performed.
         */
        public final Instant analysisTime;

        /**
         * Construct a process mining report with all results.
         *
         * @param xesXml XES export
         * @param conformance token replay result (or null)
         * @param performance performance metrics
         * @param variantFrequencies map of variants to counts
         * @param ocelJson OCEL 2.0 JSON
         * @param traceCount number of traces
         * @param specId specification identifier
         */
        ProcessMiningReport(String xesXml,
                           TokenReplayConformanceChecker.TokenReplayResult conformance,
                           PerformanceAnalyzer.PerformanceResult performance,
                           Map<String, Long> variantFrequencies,
                           String ocelJson,
                           int traceCount,
                           String specId) {
            this.xesXml = xesXml;
            this.conformance = conformance;
            this.performance = performance;
            this.variantFrequencies = variantFrequencies;
            this.variantCount = variantFrequencies.size();
            this.ocelJson = ocelJson;
            this.traceCount = traceCount;
            this.specificationId = specId;
            this.analysisTime = Instant.now();
        }
    }

    /**
     * Create a facade connected to a YAWL engine.
     *
     * @param engineUrl Base URL of YAWL engine (e.g., "http://localhost:8080/yawl")
     * @param username Username for authentication
     * @param password Password for authentication
     * @throws IOException If connection to engine fails
     */
    public ProcessMiningFacade(String engineUrl, String username, String password)
            throws IOException {
        this.exporter = new EventLogExporter(engineUrl, username, password);
    }

    /**
     * Run complete process mining analysis: XES → performance → conformance → variants → OCEL.
     *
     * <p>This is the main orchestration method. It coordinates all analysis phases in sequence:</p>
     * <ol>
     *   <li>Export event log to XES via {@link EventLogExporter}</li>
     *   <li>Analyze performance metrics via {@link PerformanceAnalyzer}</li>
     *   <li>Check conformance via token-based replay (if YNet provided)</li>
     *   <li>Extract variants (unique activity sequences)</li>
     *   <li>Convert to OCEL 2.0 format via {@link OcelExporter}</li>
     * </ol>
     *
     * @param specId Specification to analyze
     * @param net Optional YAWL net for token-based conformance checking.
     *            If null, conformance result will be null (skipped).
     * @param withData Include data attributes in XES export
     * @return Complete analysis report with all metrics
     * @throws IOException If export or analysis fails
     * @throws NullPointerException If specId is null
     */
    public ProcessMiningReport analyze(YSpecificationID specId, YNet net, boolean withData)
            throws IOException {
        Objects.requireNonNull(specId, "Specification ID is required");

        _log.info("Starting process mining analysis for {}", specId);

        // Step 1: Export XES
        String xesXml = exporter.exportSpecificationToXes(specId, withData);
        _log.debug("XES export complete: {} bytes", xesXml.length());

        // Step 2: Analyze performance
        PerformanceAnalyzer performanceAnalyzer = new PerformanceAnalyzer();
        PerformanceAnalyzer.PerformanceResult performance = performanceAnalyzer.analyze(xesXml);
        _log.debug("Performance analysis complete: {} traces, avg flow time {} ms",
                   performance.traceCount, performance.avgFlowTimeMs);

        // Step 3: Conformance check (if YNet provided)
        TokenReplayConformanceChecker.TokenReplayResult conformance = null;
        if (net != null) {
            conformance = TokenReplayConformanceChecker.replay(net, xesXml);
            _log.debug("Conformance analysis complete: fitness = {}", conformance.computeFitness());
        } else {
            _log.debug("Skipping conformance check (no YNet provided)");
        }

        // Step 4: Extract variants
        Map<String, Long> variantFrequencies = computeVariants(xesXml);
        _log.debug("Variant extraction complete: {} variants", variantFrequencies.size());

        // Step 5: Export OCEL
        String ocelJson = OcelExporter.xesToOcel(xesXml);
        _log.debug("OCEL export complete: {} bytes", ocelJson.length());

        _log.info("Process mining analysis complete for {}", specId);

        return new ProcessMiningReport(
            xesXml,
            conformance,
            performance,
            variantFrequencies,
            ocelJson,
            performance.traceCount,
            specId.getIdentifier()
        );
    }

    /**
     * Quick analysis without conformance checking (no YNet required).
     *
     * <p>Runs performance analysis, variant extraction, and OCEL conversion,
     * but skips token-based conformance checking. Useful for analyzing existing
     * event logs without a reference model.</p>
     *
     * @param specId Specification to analyze
     * @param withData Include data attributes in XES export
     * @return Analysis report (with null conformance field)
     * @throws IOException If export or analysis fails
     * @throws NullPointerException If specId is null
     */
    public ProcessMiningReport analyzePerformance(YSpecificationID specId, boolean withData)
            throws IOException {
        return analyze(specId, null, withData);
    }

    /**
     * Export YAWL net to PNML for external process mining tools.
     *
     * <p>Converts YAWL's internal net representation to PNML (Petri Net Markup Language),
     * compatible with tools like ProM, pm4py, and rust4pm.</p>
     *
     * @param net YAWL net to export
     * @return PNML XML string
     * @throws IllegalArgumentException If net is null or has missing conditions
     */
    public String exportPnml(YNet net) {
        return PnmlExporter.netToPnml(net);
    }

    /**
     * Close connection to YAWL engine.
     *
     * @throws IOException If disconnection fails
     */
    public void close() throws IOException {
        exporter.close();
    }

    /**
     * Compute process variants from XES log.
     *
     * <p>Extracts unique activity sequences (traces) from XES and counts occurrences.
     * Traces are joined as comma-separated activity names for the variant key.</p>
     *
     * @param xesXml XES log XML
     * @return LinkedHashMap of variants sorted by frequency (descending), then by name
     */
    private static Map<String, Long> computeVariants(String xesXml) {
        Map<String, Long> variantCounts = new HashMap<>();

        if (xesXml == null || xesXml.isEmpty()) {
            return variantCounts;
        }

        try {
            XNode root = new XNodeParser().parse(xesXml);
            if (root == null) {
                return variantCounts;
            }

            for (XNode traceNode : root.getChildren("trace")) {
                List<String> activities = new ArrayList<>();
                for (XNode eventNode : traceNode.getChildren("event")) {
                    String activity = extractStringAttribute(eventNode, "concept:name");
                    if (activity != null && !activity.isEmpty()) {
                        activities.add(activity);
                    }
                }

                if (!activities.isEmpty()) {
                    String variant = String.join(",", activities);
                    variantCounts.merge(variant, 1L, Long::sum);
                }
            }
        } catch (Exception e) {
            _log.warn("Failed to extract variants from XES: {}", e.getMessage(), e);
        }

        // Sort by frequency (descending), then by variant name
        return variantCounts.entrySet()
            .stream()
            .sorted((e1, e2) -> {
                int cmp = Long.compare(e2.getValue(), e1.getValue());
                if (cmp != 0) return cmp;
                return e1.getKey().compareTo(e2.getKey());
            })
            .collect(
                java.util.LinkedHashMap::new,
                (m, e) -> m.put(e.getKey(), e.getValue()),
                Map::putAll
            );
    }

    /**
     * Extract string attribute value from XES node.
     *
     * <p>XES structure: &lt;string key="concept:name" value="value"/&gt;</p>
     *
     * @param node XNode to search
     * @param key Attribute key
     * @return Attribute value, or null if not found
     */
    private static String extractStringAttribute(XNode node, String key) {
        if (node == null) {
            return null;
        }

        for (XNode child : node.getChildren()) {
            if ("string".equals(child.getName())
                && key.equals(child.getAttributeValue("key"))) {
                return child.getAttributeValue("value");
            }
        }
        return null;
    }
}
