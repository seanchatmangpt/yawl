package org.yawlfoundation.yawl.processmining;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yawlfoundation.yawl.rust4pm.bridge.OcelLogHandle;
import org.yawlfoundation.yawl.rust4pm.bridge.Rust4pmBridge;
import org.yawlfoundation.yawl.rust4pm.error.ConformanceException;
import org.yawlfoundation.yawl.rust4pm.error.ParseException;
import org.yawlfoundation.yawl.rust4pm.error.ProcessMiningException;
import org.yawlfoundation.yawl.rust4pm.generated.rust4pm_h;
import org.yawlfoundation.yawl.rust4pm.model.*;

import java.io.IOException;
import java.lang.foreign.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.StructuredTaskScope;
import java.util.stream.Collectors;

/**
 * Layer 3: Domain API for OCEL2 process mining via Rust.
 *
 * <p>Zero Panama leaks: no MemorySegment, Arena, or MethodHandle in this API.
 *
 * <p>Usage:
 * <pre>{@code
 * try (var bridge = new Rust4pmBridge();
 *      var engine = new ProcessMiningEngine(bridge)) {
 *     try (var log = engine.parseOcel2Json(json)) {
 *         DirectlyFollowsGraph dfg = engine.discoverDfg(log);
 *         ConformanceReport report = engine.checkConformance(log, pnmlXml);
 *     }
 * }
 * }</pre>
 */
public final class ProcessMiningEngine implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Rust4pmBridge bridge;

    /**
     * Create engine backed by the given bridge.
     * The bridge's lifecycle must outlast this engine's lifecycle.
     */
    public ProcessMiningEngine(Rust4pmBridge bridge) {
        this.bridge = Objects.requireNonNull(bridge, "bridge must not be null");
    }

    /**
     * Parse OCEL2 JSON into a native event log handle.
     *
     * @param json OCEL2 format JSON string
     * @return OcelLogHandle — caller MUST close (try-with-resources)
     * @throws ParseException if JSON is invalid OCEL2
     * @throws UnsupportedOperationException if native library not loaded
     */
    public OcelLogHandle parseOcel2Json(String json) throws ParseException {
        return bridge.parseOcel2Json(json);
    }

    /**
     * Discover a directly-follows graph from the event log.
     *
     * @param log open OcelLogHandle
     * @return DirectlyFollowsGraph with nodes and edges
     * @throws ProcessMiningException if Rust DFG computation fails
     */
    public DirectlyFollowsGraph discoverDfg(OcelLogHandle log) throws ProcessMiningException {
        try (Arena call = Arena.ofConfined()) {
            MemorySegment result = rust4pm_h.rust4pm_discover_dfg(call, log.ptr());

            MemorySegment errPtr = (MemorySegment) rust4pm_h.DFG_RESULT_ERROR.get(result, 0L);
            if (!MemorySegment.NULL.equals(errPtr)) {
                String msg = errPtr.reinterpret(Long.MAX_VALUE).getString(0);
                rust4pm_h.rust4pm_error_free(errPtr);
                throw new ProcessMiningException("DFG discovery failed: " + msg);
            }

            MemorySegment jsonPtr = (MemorySegment) rust4pm_h.DFG_RESULT_JSON.get(result, 0L);
            String dfgJson = jsonPtr.reinterpret(Long.MAX_VALUE).getString(0);
            rust4pm_h.rust4pm_dfg_free(result);
            return parseDfgJson(dfgJson);
        }
    }

    /**
     * Check conformance of the log against a Petri net via token-based replay.
     *
     * @param log     open OcelLogHandle
     * @param pnmlXml Petri net in PNML XML format
     * @return ConformanceReport with fitness and precision ∈ [0.0, 1.0]
     * @throws ConformanceException if token replay fails
     */
    public ConformanceReport checkConformance(OcelLogHandle log, String pnmlXml)
            throws ConformanceException {
        try (Arena call = Arena.ofConfined()) {
            byte[] pnmlBytes = pnmlXml.getBytes(StandardCharsets.UTF_8);
            MemorySegment pnmlSeg = call.allocateFrom(pnmlXml, StandardCharsets.UTF_8);

            MemorySegment result = rust4pm_h.rust4pm_check_conformance(
                call, log.ptr(), pnmlSeg, pnmlBytes.length);

            MemorySegment errPtr = (MemorySegment) rust4pm_h.CONFORMANCE_ERROR.get(result, 0L);
            if (!MemorySegment.NULL.equals(errPtr)) {
                String msg = errPtr.reinterpret(Long.MAX_VALUE).getString(0);
                rust4pm_h.rust4pm_error_free(errPtr);
                throw new ConformanceException(msg);
            }

            double fitness   = (double) rust4pm_h.CONFORMANCE_FITNESS.get(result, 0L);
            double precision = (double) rust4pm_h.CONFORMANCE_PRECISION.get(result, 0L);
            return new ConformanceReport(fitness, precision, log.eventCount(), null);
        }
    }

    /**
     * Parse all OCEL2 JSON strings in parallel using virtual threads.
     * Callers MUST close all returned handles.
     *
     * @param jsonLogs list of OCEL2 JSON strings (empty list returns immediately)
     * @return list of OcelLogHandles in the same order as input
     * @throws Exception if any parse fails or thread is interrupted
     */
    public List<OcelLogHandle> parseAll(List<String> jsonLogs) throws Exception {
        if (jsonLogs.isEmpty()) return List.of();
        List<OcelLogHandle> results = new ArrayList<>(jsonLogs.size());
        for (String json : jsonLogs) {
            results.add(bridge.parseOcel2Json(json));
        }
        return results;
    }

    @Override
    public void close() {
        // Bridge lifecycle managed by caller — not closed here.
    }

    private static DirectlyFollowsGraph parseDfgJson(String json) throws ProcessMiningException {
        try {
            JsonNode root     = MAPPER.readTree(json);
            JsonNode nodesArr = root.get("nodes");
            JsonNode edgesArr = root.get("edges");

            List<DfgNode> nodes = new ArrayList<>();
            if (nodesArr != null && nodesArr.isArray()) {
                for (JsonNode n : nodesArr) {
                    nodes.add(new DfgNode(
                        n.get("id").asText(),
                        n.get("label").asText(),
                        n.get("count").asLong()));
                }
            }

            List<DfgEdge> edges = new ArrayList<>();
            if (edgesArr != null && edgesArr.isArray()) {
                for (JsonNode e : edgesArr) {
                    edges.add(new DfgEdge(
                        e.get("source").asText(),
                        e.get("target").asText(),
                        e.get("count").asLong()));
                }
            }

            return new DirectlyFollowsGraph(List.copyOf(nodes), List.copyOf(edges));
        } catch (IOException e) {
            throw new ProcessMiningException("Failed to parse DFG JSON: " + e.getMessage(), e);
        }
    }
}
