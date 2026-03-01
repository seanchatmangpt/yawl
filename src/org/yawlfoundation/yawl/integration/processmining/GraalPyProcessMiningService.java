/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * This file is part of YAWL. YAWL is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License.
 */
package org.yawlfoundation.yawl.integration.processmining;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.graalvm.polyglot.Value;
import org.yawlfoundation.yawl.graalpy.PythonException;
import org.yawlfoundation.yawl.graalpy.PythonExecutionEngine;
import org.yawlfoundation.yawl.graalpy.TypeMarshaller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * In-process {@link ProcessMiningService} implementation backed by pm4py via GraalPy.
 *
 * <p>Replaces the HTTP-based {@code ProcessMiningServiceClient} (which delegated to
 * Rust {@code yawl-native} → pm4py). All process mining runs in-process: no network
 * round-trips, no serialisation overhead for intermediate objects.</p>
 *
 * <h2>Context constraint</h2>
 * <p>Every method borrows a <strong>single</strong> GraalPy context for its entire
 * operation. Python objects ({@link Value} references) never cross pool context
 * boundaries. This is enforced by performing all steps inside one
 * {@link Pm4py#inContext(java.util.function.Function)} call per method.</p>
 *
 * <h2>Runtime requirement</h2>
 * <p>GraalVM JDK 24.1+ at runtime. On standard JDK, {@link #isHealthy()} returns
 * {@code false} and all other methods throw {@link IOException} wrapping
 * {@link PythonException}.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * try (GraalPyProcessMiningService svc = new GraalPyProcessMiningService()) {
 *     if (svc.isHealthy()) {
 *         String dfgJson = svc.discoverDfg(xesXml);
 *     }
 * }
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public final class GraalPyProcessMiningService implements ProcessMiningService, AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Pm4py pm4py;

    /**
     * Creates a service backed by a new {@link Pm4py} instance with default settings.
     *
     * <p>Blocks until {@code pm4py_bridge.py} is loaded into all pool contexts.</p>
     */
    public GraalPyProcessMiningService() {
        this.pm4py = new Pm4py();
    }

    /**
     * Package-private constructor for test injection.
     *
     * @param engine  pre-built engine; must not be null
     */
    GraalPyProcessMiningService(PythonExecutionEngine engine) {
        this.pm4py = new Pm4py(engine);
    }

    // ─── ProcessMiningService ────────────────────────────────────────────────────────

    /**
     * Run token-based replay conformance check against a Petri net model and event log.
     *
     * <p>All steps run in a single GraalPy context borrow:
     * {@code read_pnml_string} → {@code read_xes} →
     * {@code fitness_token_based_replay} → {@code conformance_diagnostics_token_based_replay}
     * → marshal to JSON.</p>
     *
     * @param pnmlXml  PNML-formatted Petri net model; must not be null
     * @param xesXml   XES-formatted event log; must not be null
     * @return JSON with fitness metrics and deviating cases
     * @throws IllegalArgumentException if pnmlXml or xesXml is null
     * @throws IOException if GraalPy or pm4py execution fails
     */
    @Override
    public String tokenReplay(String pnmlXml, String xesXml) throws IOException {
        Objects.requireNonNull(pnmlXml, "pnmlXml must not be null");
        Objects.requireNonNull(xesXml, "xesXml must not be null");
        try {
            return pm4py.inContext(bc -> {
                // All in ONE context borrow — Values never cross contexts
                Pm4py.EventLog log = bc.readXes(xesXml);
                Pm4py.PetriNetResult pnr = bc.readPnmlString(pnmlXml);

                Map<String, Object> fitness = bc.fitnessTokenBasedReplay(
                    log, pnr.net(), pnr.initialMarking(), pnr.finalMarking());

                List<Map<String, Object>> diagnostics = bc.conformanceDiagnosticsTokenBasedReplay(
                    log, pnr.net(), pnr.initialMarking(), pnr.finalMarking());

                List<String> deviatingCases = extractDeviatingCases(diagnostics);

                Map<String, Object> result = new HashMap<>(fitness);
                result.put("deviating_cases", deviatingCases);
                result.put("deviated_case_count", deviatingCases.size());
                return marshalJson(result);
            });
        } catch (PythonException e) {
            throw new IOException("Token replay failed: " + e.getMessage(), e);
        }
    }

    /**
     * Discover a Directly-Follows Graph from an event log.
     *
     * <p>Returns the JSON produced by {@code discover_dfg_as_json}:
     * {@code {"edges": [...], "start_activities": {...}, "end_activities": {...}}}.</p>
     *
     * @param xesXml  XES-formatted event log; must not be null
     * @return DFG JSON string
     * @throws IllegalArgumentException if xesXml is null
     * @throws IOException if GraalPy or pm4py execution fails
     */
    @Override
    public String discoverDfg(String xesXml) throws IOException {
        Objects.requireNonNull(xesXml, "xesXml must not be null");
        try {
            return pm4py.inContext(bc -> {
                Pm4py.EventLog log = bc.readXes(xesXml);
                return bc.discoverDfgAsJson(log);
            });
        } catch (PythonException e) {
            throw new IOException("DFG discovery failed: " + e.getMessage(), e);
        }
    }

    /**
     * Discover a Petri net using the Alpha+++ algorithm.
     *
     * <p>Returns PNML XML string of the discovered Petri net.</p>
     *
     * @param xesXml  XES-formatted event log; must not be null
     * @return PNML XML string
     * @throws IllegalArgumentException if xesXml is null
     * @throws IOException if GraalPy or pm4py execution fails
     */
    @Override
    public String discoverAlphaPpp(String xesXml) throws IOException {
        Objects.requireNonNull(xesXml, "xesXml must not be null");
        try {
            return pm4py.inContext(bc -> {
                Pm4py.EventLog log = bc.readXes(xesXml);
                Pm4py.PetriNetResult pnr = bc.discoverPetriNetAlphaPlusPlus(log);
                return bc.exportPetriNetToPnmlString(pnr);
            });
        } catch (PythonException e) {
            throw new IOException("Alpha+++ discovery failed: " + e.getMessage(), e);
        }
    }

    /**
     * Compute performance statistics from an event log.
     *
     * <p>Uses pm4py's {@code get_all_case_durations} to compute flow time statistics.
     * Returns JSON with trace count, average flow time, and duration distribution.</p>
     *
     * @param xesXml  XES-formatted event log; must not be null
     * @return JSON with performance metrics
     * @throws IllegalArgumentException if xesXml is null
     * @throws IOException if GraalPy or pm4py execution fails
     */
    @Override
    public String performanceAnalysis(String xesXml) throws IOException {
        Objects.requireNonNull(xesXml, "xesXml must not be null");
        try {
            return pm4py.inContext(bc -> {
                Pm4py.EventLog log = bc.readXes(xesXml);
                List<Double> durations = bc.getAllCaseDurations(log);
                Map<String, Long> startActivities = bc.getStartActivities(log);
                Map<String, Long> endActivities = bc.getEndActivities(log);

                Map<String, Object> result = buildPerformanceJson(durations, startActivities, endActivities);
                return marshalJson(result);
            });
        } catch (PythonException e) {
            throw new IOException("Performance analysis failed: " + e.getMessage(), e);
        }
    }

    /**
     * Convert XES event log to OCEL 2.0 (Object-Centric Event Log) format.
     *
     * <p>Delegates to the {@code xes_to_ocel_json} bridge function which handles
     * the full XES → pm4py EventLog → OCEL conversion chain.</p>
     *
     * @param xesXml  XES-formatted event log; must not be null
     * @return OCEL 2.0 JSON string
     * @throws IllegalArgumentException if xesXml is null
     * @throws IOException if GraalPy or pm4py execution fails
     */
    @Override
    public String xesToOcel(String xesXml) throws IOException {
        Objects.requireNonNull(xesXml, "xesXml must not be null");
        try {
            return pm4py.xesToOcelJson(xesXml);
        } catch (PythonException e) {
            throw new IOException("XES to OCEL conversion failed: " + e.getMessage(), e);
        }
    }

    /**
     * Check if pm4py is reachable in the GraalPy environment.
     *
     * <p>Returns {@code false} rather than throwing when GraalPy is unavailable
     * (standard JDK) or pm4py is not installed in the GraalPy venv.</p>
     *
     * @return {@code true} if pm4py is importable; {@code false} otherwise
     */
    @Override
    public boolean isHealthy() {
        return pm4py.ping();
    }

    /** Closes the underlying {@link Pm4py} engine and all pooled GraalPy contexts. */
    @Override
    public void close() {
        pm4py.close();
    }

    // ─── Private helpers ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static List<String> extractDeviatingCases(List<Map<String, Object>> diagnostics) {
        List<String> deviating = new ArrayList<>();
        for (Map<String, Object> trace : diagnostics) {
            Object isFit = trace.get("trace_is_fit");
            if (Boolean.FALSE.equals(isFit)) {
                Object caseId = trace.get("case_id");
                if (caseId != null) {
                    deviating.add(caseId.toString());
                }
            }
        }
        return deviating;
    }

    private static Map<String, Object> buildPerformanceJson(
            List<Double> durationsSeconds,
            Map<String, Long> startActivities,
            Map<String, Long> endActivities) {

        Map<String, Object> result = new HashMap<>();
        result.put("trace_count", durationsSeconds.size());

        if (!durationsSeconds.isEmpty()) {
            DoubleSummaryStatistics stats = durationsSeconds.stream()
                .mapToDouble(Double::doubleValue)
                .summaryStatistics();
            double avgMs = stats.getAverage() * 1000.0;
            double minMs = stats.getMin() * 1000.0;
            double maxMs = stats.getMax() * 1000.0;
            double avgHours = stats.getAverage() / 3600.0;
            double throughputPerHour = avgHours > 0 ? 1.0 / avgHours : 0.0;

            result.put("avg_flow_time_ms", avgMs);
            result.put("min_flow_time_ms", minMs);
            result.put("max_flow_time_ms", maxMs);
            result.put("throughput_per_hour", throughputPerHour);
            result.put("case_durations_seconds", durationsSeconds);
        } else {
            result.put("avg_flow_time_ms", 0.0);
            result.put("min_flow_time_ms", 0.0);
            result.put("max_flow_time_ms", 0.0);
            result.put("throughput_per_hour", 0.0);
        }

        result.put("start_activities", startActivities);
        result.put("end_activities", endActivities);
        return result;
    }

    private static String marshalJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new PythonException(
                "Failed to marshal result to JSON: " + e.getMessage(),
                PythonException.ErrorKind.TYPE_CONVERSION_ERROR, e);
        }
    }
}
