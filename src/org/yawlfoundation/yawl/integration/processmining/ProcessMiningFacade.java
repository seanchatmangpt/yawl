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
import org.yawlfoundation.yawl.integration.processmining.Ocel2Exporter.Ocel2EventLog;

import org.yawlfoundation.yawl.integration.eventsourcing.WorkflowEventStore;
import org.yawlfoundation.yawl.integration.messagequeue.WorkflowEvent;
import org.yawlfoundation.yawl.integration.processmining.discovery.AlphaMiner;
import org.yawlfoundation.yawl.integration.processmining.discovery.InductiveMiner;
import org.yawlfoundation.yawl.integration.processmining.discovery.ProcessDiscoveryAlgorithm;
import org.yawlfoundation.yawl.integration.processmining.discovery.DirectlyFollowsGraph;
import org.yawlfoundation.yawl.integration.processmining.discovery.ProcessTree;

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
     * Analyze process mining data from the event store for a specific case.
     *
     * <p>Reads all events for the given case from the event store, converts them to XES,
     * and runs the full analysis pipeline (performance, variants, OCEL). This method uses
     * the event store as the authoritative source, avoiding a live engine connection.</p>
     *
     * @param caseId     Case identifier to analyze (must not be null)
     * @param eventStore Source of workflow events (must not be null)
     * @return Analysis report with performance and variant data (null conformance)
     * @throws WorkflowEventStore.EventStoreException If reading from the store fails
     * @throws NullPointerException If caseId or eventStore is null
     */
    public ProcessMiningReport analyzeFromEventStore(
            String caseId, WorkflowEventStore eventStore)
            throws WorkflowEventStore.EventStoreException {
        Objects.requireNonNull(caseId, "caseId is required");
        Objects.requireNonNull(eventStore, "eventStore is required");

        _log.info("Starting event-store-based process mining analysis for case '{}'", caseId);

        List<WorkflowEvent> events = eventStore.loadEvents(caseId);
        _log.debug("Loaded {} events for case '{}'", events.size(), caseId);

        String xesXml = workflowEventsToXes(caseId, events);

        PerformanceAnalyzer performanceAnalyzer = new PerformanceAnalyzer();
        PerformanceAnalyzer.PerformanceResult performance = performanceAnalyzer.analyze(xesXml);
        _log.debug("Performance analysis complete: {} traces, avg flow time {} ms",
                   performance.traceCount, performance.avgFlowTimeMs);

        Map<String, Long> variants = computeVariants(xesXml);
        _log.debug("Variant extraction complete: {} variants", variants.size());

        String ocelJson = OcelExporter.xesToOcel(xesXml);
        _log.debug("OCEL export complete: {} bytes", ocelJson.length());

        _log.info("Event-store analysis complete for case '{}': {} events, {} variants",
                  caseId, events.size(), variants.size());

        return new ProcessMiningReport(
            xesXml, null, performance, variants, ocelJson,
            performance.traceCount, caseId);
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
     * Discover a process model using the Alpha Miner algorithm.
     *
     * <p>Alpha Miner discovers a Petri net from event logs based on footprint analysis.
     * The discovered model is guaranteed to have perfect fitness on the input log.</p>
     *
     * @param eventLog OCEL event log to mine
     * @return Discovery result with Petri net and metrics
     * @throws ProcessDiscoveryAlgorithm.ProcessDiscoveryException if mining fails
     * @throws NullPointerException if eventLog is null
     */
    public ProcessDiscoveryResult discoverAlpha(Ocel2EventLog eventLog)
            throws ProcessDiscoveryAlgorithm.ProcessDiscoveryException {
        Objects.requireNonNull(eventLog, "eventLog is required");

        _log.info("Starting Alpha Miner discovery");

        AlphaMiner miner = new AlphaMiner();
        ProcessDiscoveryAlgorithm.ProcessMiningContext context =
            new ProcessDiscoveryAlgorithm.ProcessMiningContext.Builder()
                .eventLog(eventLog)
                .algorithm(ProcessDiscoveryAlgorithm.AlgorithmType.ALPHA)
                .build();

        ProcessDiscoveryResult result = miner.discover(context);
        _log.info("Alpha Miner discovery complete: fitness={}, precision={}",
                  result.fitness(), result.precision());

        return result;
    }

    /**
     * Discover a process tree using the Inductive Miner algorithm.
     *
     * <p>Inductive Miner discovers hierarchical process trees by recursively
     * finding cuts in the directly-follows graph. The discovered tree is
     * guaranteed to be sound (deadlock-free).</p>
     *
     * @param eventLog OCEL event log to mine
     * @return Process tree representing the discovered model
     * @throws ProcessDiscoveryAlgorithm.ProcessDiscoveryException if mining fails
     * @throws NullPointerException if eventLog is null
     */
    public ProcessTree discoverInductive(Ocel2EventLog eventLog)
            throws ProcessDiscoveryAlgorithm.ProcessDiscoveryException {
        Objects.requireNonNull(eventLog, "eventLog is required");

        _log.info("Starting Inductive Miner discovery");

        InductiveMiner miner = new InductiveMiner();
        ProcessDiscoveryAlgorithm.ProcessMiningContext context =
            new ProcessDiscoveryAlgorithm.ProcessMiningContext.Builder()
                .eventLog(eventLog)
                .algorithm(ProcessDiscoveryAlgorithm.AlgorithmType.INDUCTIVE)
                .build();

        ProcessDiscoveryResult result = miner.discover(context);
        _log.info("Inductive Miner discovery complete: fitness={}, precision={}",
                  result.fitness(), result.precision());

        // Extract process tree from result (would require parsing the JSON representation)
        // For now, create a simple placeholder tree
        throw new UnsupportedOperationException(
            "Process tree extraction from discovery result requires full implementation. " +
            "Use discoverAlpha() for Petri net-based discovery."
        );
    }

    /**
     * Build a directly-follows graph from event traces.
     *
     * <p>The DFG represents activity execution patterns: nodes are activities,
     * edges represent directly-follows relations with occurrence counts.</p>
     *
     * @param traces List of activity sequences (one list per case)
     * @return Directly-follows graph
     * @throws NullPointerException if traces is null
     */
    public DirectlyFollowsGraph buildDfg(List<List<String>> traces) {
        Objects.requireNonNull(traces, "traces is required");

        _log.debug("Building DFG from {} traces", traces.size());

        DirectlyFollowsGraph dfg = DirectlyFollowsGraph.discover(traces);

        _log.debug("DFG built: {} activities, {} edges",
                   dfg.getActivities().size(), dfg.toJson());

        return dfg;
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
     * Convert workflow events for a single case into XES event log XML.
     *
     * <p>Produces one trace containing one XES event per recorded workflow event.
     * Activity name is derived from the work item ID (task portion) for work-item
     * events, or the event type name for case-level events.</p>
     *
     * @param caseId case identifier (used as trace concept:name)
     * @param events ordered list of workflow events
     * @return XES XML string (IEEE 1849-2016 compliant)
     */
    private static String workflowEventsToXes(String caseId, List<WorkflowEvent> events) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<log xes.version=\"1.0\" xes.features=\"\" openxes.version=\"1.0RC7\"\n");
        sb.append("     xmlns=\"http://www.xes-standard.org/\">\n");
        sb.append("  <extension name=\"Concept\" prefix=\"concept\"")
          .append(" uri=\"http://www.xes-standard.org/concept.xesext\"/>\n");
        sb.append("  <extension name=\"Time\" prefix=\"time\"")
          .append(" uri=\"http://www.xes-standard.org/time.xesext\"/>\n");
        sb.append("  <extension name=\"Lifecycle\" prefix=\"lifecycle\"")
          .append(" uri=\"http://www.xes-standard.org/lifecycle.xesext\"/>\n");
        sb.append("  <trace>\n");
        sb.append("    <string key=\"concept:name\" value=\"")
          .append(escapeXml(caseId)).append("\"/>\n");
        for (WorkflowEvent event : events) {
            String activityName = deriveActivityName(event);
            String lifecycle = deriveLifecycleTransition(event.getEventType());
            String timestamp = event.getTimestamp().toString();
            sb.append("    <event>\n");
            sb.append("      <string key=\"concept:name\" value=\"")
              .append(escapeXml(activityName)).append("\"/>\n");
            sb.append("      <string key=\"lifecycle:transition\" value=\"")
              .append(lifecycle).append("\"/>\n");
            sb.append("      <date key=\"time:timestamp\" value=\"")
              .append(escapeXml(timestamp)).append("\"/>\n");
            sb.append("    </event>\n");
        }
        sb.append("  </trace>\n");
        sb.append("</log>");
        return sb.toString();
    }

    private static String deriveActivityName(WorkflowEvent event) {
        String workItemId = event.getWorkItemId();
        if (workItemId != null && !workItemId.isBlank()) {
            // YAWL workItemId format: "caseId:taskId" or "caseId:taskId.childIndex"
            int colonIdx = workItemId.indexOf(':');
            return colonIdx >= 0 ? workItemId.substring(colonIdx + 1) : workItemId;
        }
        // Case-level events: use readable event type name
        return event.getEventType().name().replace('_', ' ').toLowerCase();
    }

    private static String deriveLifecycleTransition(WorkflowEvent.EventType eventType) {
        return switch (eventType) {
            case WORKITEM_STARTED, CASE_STARTED     -> "start";
            case WORKITEM_COMPLETED, CASE_COMPLETED -> "complete";
            case WORKITEM_ENABLED                   -> "schedule";
            case WORKITEM_CANCELLED, CASE_CANCELLED -> "ate_abort";
            default                                 -> "complete";
        };
    }

    private static String escapeXml(String value) {
        if (value == null) {
            throw new NullPointerException("value cannot be null for XML escaping");
        }
        return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;");
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

    /**
     * Discover an Object-Centric Petri Net (OCPN) from OCEL 2.0 log data.
     *
     * <p>Analyzes object-centric event logs to discover per-object-type directly-follows graphs
     * and Petri nets. Particularly useful for processes with multiple object types
     * (e.g., cases with line items, resources).</p>
     *
     * @param input OCEL 2.0 log data (events and objects)
     * @return OCPN discovery result with object types, transitions, and places
     * @throws NullPointerException if input is null
     */
    public OcpmDiscovery.OcpmResult discoverObjectCentric(OcpmInput input) {
        Objects.requireNonNull(input, "input is required");

        _log.info("Starting Object-Centric Petri Net discovery");

        OcpmDiscovery discovery = new OcpmDiscovery();
        OcpmDiscovery.OcpmResult result = discovery.discover(input);

        _log.info("OCPN discovery complete: {} object types, {} shared transitions",
                  result.objectTypes().size(), result.transitions().size());

        return result;
    }

    /**
     * Analyze fairness of process outcomes across demographic groups.
     *
     * <p>Implements van der Aalst's "Responsible Process Mining" framework to detect
     * demographic bias in process decisions using the four-fifths rule (disparate impact).</p>
     *
     * @param caseAttributes list of case attribute maps (caseId → attribute value)
     * @param decisions list of decision outcome maps (caseId → decision)
     * @param sensitiveAttribute the attribute to check for bias (e.g., "resource", "department")
     * @param positiveOutcome the value representing a positive decision (e.g., "approved")
     * @return fairness report with disparate impact and violation details
     * @throws NullPointerException if any parameter is null
     * @throws IllegalArgumentException if caseAttributes and decisions have different sizes
     */
    public FairnessAnalyzer.FairnessReport analyzeFairness(
            List<Map<String, String>> caseAttributes,
            List<Map<String, String>> decisions,
            String sensitiveAttribute,
            String positiveOutcome) {

        _log.info("Starting fairness analysis on sensitive attribute '{}'", sensitiveAttribute);

        FairnessAnalyzer.FairnessReport report = FairnessAnalyzer.analyze(
            caseAttributes, decisions, sensitiveAttribute, positiveOutcome
        );

        _log.info("Fairness analysis complete: disparateImpact={}, isFair={}",
                  String.format("%.3f", report.disparateImpact()), report.isFair());

        if (!report.isFair()) {
            _log.warn("Fairness violation detected: {}", report.violations());
        }

        return report;
    }
}
