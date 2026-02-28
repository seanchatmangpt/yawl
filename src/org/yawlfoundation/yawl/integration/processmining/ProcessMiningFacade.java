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

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.yawlfoundation.yawl.integration.eventsourcing.WorkflowEventStore;
import org.yawlfoundation.yawl.integration.messagequeue.WorkflowEvent;

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
 * coordinating separate classes.</p>
 *
 * <p>All pm4py operations run in-process via {@link GraalPyProcessMiningService}, which
 * uses GraalPy to embed pm4py directly in the JVM — no network round-trips.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>
 *   ProcessMiningFacade facade = new ProcessMiningFacade(engineUrl, user, password);
 *   ProcessMiningReport report = facade.analyze(specId, net);
 *   System.out.println("Fitness: " + report.conformance.fitness());
 *   System.out.println("Flow time: " + report.performance.avgFlowTimeMs() + " ms");
 * </pre>
 *
 * <h2>Analysis Components</h2>
 * <ul>
 *   <li><b>XES Export</b> ({@link EventLogExporter}) - Event log in eXtensible Event Stream format</li>
 *   <li><b>Performance</b> ({@link ProcessMiningService#performanceAnalysis}) - Flow time, throughput</li>
 *   <li><b>Conformance</b> ({@link ProcessMiningService#tokenReplay}) - Token-based replay fitness</li>
 *   <li><b>Variants</b> - Unique activity sequences ranked by frequency (inline computation)</li>
 *   <li><b>OCEL</b> ({@link ProcessMiningService#xesToOcel}) - Object-centric event log</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public final class ProcessMiningFacade {

    private static final Logger _log = LogManager.getLogger(ProcessMiningFacade.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final EventLogExporter exporter;
    private final ProcessMiningService service;

    // ─── Result types ─────────────────────────────────────────────────────────────

    /**
     * Conformance checking result from token-based replay.
     *
     * @param fitness  log fitness score in [0.0, 1.0]; higher is better
     * @param rawJson  full pm4py token replay JSON for further inspection
     */
    public record ConformanceResult(double fitness, String rawJson) {
        /** Returns the fitness score (alias for {@link #fitness()} for backward compat). */
        public double computeFitness() { return fitness; }
    }

    /**
     * Performance analysis result from pm4py case-duration statistics.
     *
     * @param traceCount         number of traces (cases) in the log
     * @param avgFlowTimeMs      average case flow time in milliseconds
     * @param throughputPerHour  estimated throughput in cases per hour
     * @param activityCounts     occurrence count per activity (start activities proxy)
     * @param rawJson            full pm4py performance analysis JSON
     */
    public record PerformanceResult(
            int traceCount,
            double avgFlowTimeMs,
            double throughputPerHour,
            Map<String, Integer> activityCounts,
            String rawJson) {}

    // ─── Report ───────────────────────────────────────────────────────────────────

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
        public final ConformanceResult conformance;

        /**
         * Performance metrics: flow time, throughput, activity counts.
         */
        public final PerformanceResult performance;

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

        ProcessMiningReport(String xesXml,
                           ConformanceResult conformance,
                           PerformanceResult performance,
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

    // ─── Constructors ─────────────────────────────────────────────────────────────

    /**
     * Create a facade connected to a YAWL engine, backed by an in-process
     * {@link GraalPyProcessMiningService}.
     *
     * @param engineUrl Base URL of YAWL engine (e.g., "http://localhost:8080/yawl")
     * @param username Username for authentication
     * @param password Password for authentication
     * @throws IOException If connection to engine fails
     */
    public ProcessMiningFacade(String engineUrl, String username, String password)
            throws IOException {
        this(engineUrl, username, password, new GraalPyProcessMiningService());
    }

    /**
     * Create a facade with an externally supplied {@link ProcessMiningService}.
     *
     * <p>The facade does not own the service lifecycle; the caller is responsible
     * for closing it. Use this constructor for testing or when sharing a service
     * across multiple facades.</p>
     *
     * @param engineUrl Base URL of YAWL engine
     * @param username  Username for authentication
     * @param password  Password for authentication
     * @param service   pm4py service to delegate analysis to; must not be null
     * @throws IOException If connection to engine fails
     */
    public ProcessMiningFacade(String engineUrl, String username, String password,
                                ProcessMiningService service) throws IOException {
        this.exporter = new EventLogExporter(engineUrl, username, password);
        this.service = Objects.requireNonNull(service, "service");
    }

    // ─── Analysis ─────────────────────────────────────────────────────────────────

    /**
     * Run complete process mining analysis: XES → performance → conformance → variants → OCEL.
     *
     * <p>This is the main orchestration method. It coordinates all analysis phases in sequence:</p>
     * <ol>
     *   <li>Export event log to XES via {@link EventLogExporter}</li>
     *   <li>Analyze performance metrics via {@link ProcessMiningService#performanceAnalysis}</li>
     *   <li>Check conformance via token-based replay (if YNet provided)</li>
     *   <li>Extract variants (unique activity sequences)</li>
     *   <li>Convert to OCEL 2.0 format via {@link ProcessMiningService#xesToOcel}</li>
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

        // Step 2: Analyze performance via pm4py
        String perfJson = service.performanceAnalysis(xesXml);
        PerformanceResult performance = parsePerformanceResult(perfJson);
        _log.debug("Performance analysis complete: {} traces, avg flow time {} ms",
                   performance.traceCount(), performance.avgFlowTimeMs());

        // Step 3: Conformance check (if YNet provided)
        ConformanceResult conformance = null;
        if (net != null) {
            String pnmlXml = PnmlExporter.netToPnml(net);
            String replayJson = service.tokenReplay(pnmlXml, xesXml);
            conformance = parseConformanceResult(replayJson);
            _log.debug("Conformance analysis complete: fitness = {}", conformance.fitness());
        } else {
            _log.debug("Skipping conformance check (no YNet provided)");
        }

        // Step 4: Extract variants
        Map<String, Long> variantFrequencies = computeVariants(xesXml);
        _log.debug("Variant extraction complete: {} variants", variantFrequencies.size());

        // Step 5: Export OCEL
        String ocelJson = service.xesToOcel(xesXml);
        _log.debug("OCEL export complete: {} bytes", ocelJson.length());

        _log.info("Process mining analysis complete for {}", specId);

        return new ProcessMiningReport(
            xesXml,
            conformance,
            performance,
            variantFrequencies,
            ocelJson,
            performance.traceCount(),
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
     * @throws WorkflowEventStore.EventStoreException If reading from the store or analysis fails
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

        try {
            String perfJson = service.performanceAnalysis(xesXml);
            PerformanceResult performance = parsePerformanceResult(perfJson);
            _log.debug("Performance analysis complete: {} traces, avg flow time {} ms",
                       performance.traceCount(), performance.avgFlowTimeMs());

            Map<String, Long> variants = computeVariants(xesXml);
            _log.debug("Variant extraction complete: {} variants", variants.size());

            String ocelJson = service.xesToOcel(xesXml);
            _log.debug("OCEL export complete: {} bytes", ocelJson.length());

            _log.info("Event-store analysis complete for case '{}': {} events, {} variants",
                      caseId, events.size(), variants.size());

            return new ProcessMiningReport(
                xesXml, null, performance, variants, ocelJson,
                performance.traceCount(), caseId);

        } catch (IOException e) {
            throw new WorkflowEventStore.EventStoreException(
                "Process mining analysis failed for case '" + caseId + "': " + e.getMessage(), e);
        }
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
     * Close connection to YAWL engine and the process mining service (if it implements
     * {@link AutoCloseable}).
     *
     * @throws IOException If disconnection fails
     */
    public void close() throws IOException {
        exporter.close();
        if (service instanceof AutoCloseable ac) {
            try {
                ac.close();
            } catch (Exception e) {
                throw new IOException("Failed to close ProcessMiningService", e);
            }
        }
    }

    // ─── Private helpers ──────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static PerformanceResult parsePerformanceResult(String json) throws IOException {
        try {
            Map<String, Object> m = MAPPER.readValue(json, Map.class);
            int traceCount = ((Number) m.getOrDefault("trace_count", 0)).intValue();
            double avgFlowTimeMs = ((Number) m.getOrDefault("avg_flow_time_ms", 0.0)).doubleValue();
            double throughputPerHour = ((Number) m.getOrDefault("throughput_per_hour", 0.0)).doubleValue();
            // start_activities is Map<String, Long>; convert to Map<String, Integer>
            Map<String, Integer> activityCounts = new HashMap<>();
            Object startActivities = m.get("start_activities");
            if (startActivities instanceof Map<?, ?> rawMap) {
                rawMap.forEach((k, v) ->
                    activityCounts.put(String.valueOf(k), ((Number) v).intValue()));
            }
            return new PerformanceResult(traceCount, avgFlowTimeMs, throughputPerHour, activityCounts, json);
        } catch (Exception e) {
            throw new IOException("Failed to parse performance result: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static ConformanceResult parseConformanceResult(String json) throws IOException {
        try {
            Map<String, Object> m = MAPPER.readValue(json, Map.class);
            // pm4py fitness_token_based_replay returns log_fitness
            double fitness = ((Number) m.getOrDefault("log_fitness",
                m.getOrDefault("average_trace_fitness", 0.0))).doubleValue();
            return new ConformanceResult(fitness, json);
        } catch (Exception e) {
            throw new IOException("Failed to parse conformance result: " + e.getMessage(), e);
        }
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
        Objects.requireNonNull(value, "XML attribute value must not be null");
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
}
