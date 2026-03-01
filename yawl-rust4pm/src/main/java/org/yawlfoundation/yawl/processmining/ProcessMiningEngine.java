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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;

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
                String msg = errPtr.reinterpret(Long.MAX_VALUE).getString(0, StandardCharsets.UTF_8);
                rust4pm_h.rust4pm_error_free(errPtr);
                throw new ProcessMiningException("DFG discovery failed: " + msg);
            }

            MemorySegment jsonPtr = (MemorySegment) rust4pm_h.DFG_RESULT_JSON.get(result, 0L);
            String dfgJson = jsonPtr.reinterpret(Long.MAX_VALUE).getString(0, StandardCharsets.UTF_8);
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
                String msg = errPtr.reinterpret(Long.MAX_VALUE).getString(0, StandardCharsets.UTF_8);
                rust4pm_h.rust4pm_error_free(errPtr);
                throw new ConformanceException(msg);
            }

            double fitness   = (double) rust4pm_h.CONFORMANCE_FITNESS.get(result, 0L);
            double precision = (double) rust4pm_h.CONFORMANCE_PRECISION.get(result, 0L);
            return new ConformanceReport(fitness, precision, log.eventCount(), null);
        }
    }

    /**
     * Parse all OCEL2 JSON strings in parallel using virtual threads via
     * {@link StructuredTaskScope} with {@code Joiner.awaitAllSuccessfulOrThrow()}.
     *
     * <p>All logs are parsed concurrently. If any parse fails, all in-flight
     * tasks are cancelled and the first failure propagates.
     *
     * <p>Callers MUST close all returned handles.
     *
     * @param jsonLogs list of OCEL2 JSON strings (empty list returns immediately)
     * @return list of OcelLogHandles in the same order as input
     * @throws InterruptedException if the calling thread is interrupted
     * @throws ExecutionException   if any parse fails
     */
    public List<OcelLogHandle> parseAll(List<String> jsonLogs)
            throws InterruptedException, ExecutionException {
        if (jsonLogs.isEmpty()) return List.of();
        try (var scope = StructuredTaskScope.open(
                StructuredTaskScope.Joiner.<OcelLogHandle>awaitAllSuccessfulOrThrow())) {
            var tasks = jsonLogs.stream()
                .map(json -> scope.fork(() -> parseOcel2Json(json)))
                .toList();
            scope.join();
            return tasks.stream()
                .map(StructuredTaskScope.Subtask::get)
                .toList();
        }
    }

    /**
     * Parse XES format event log into a native event log handle.
     *
     * <p>XES (eXtensible Event Stream) is a standard format for event logs.
     * This method converts XES to an OCEL2-compatible internal representation.
     *
     * @param xes XES format event log string
     * @return OcelLogHandle — caller MUST close (try-with-resources)
     * @throws ParseException if XES format is invalid
     * @throws UnsupportedOperationException if native function not yet implemented
     */
    public OcelLogHandle parseXes(String xes) throws ParseException {
        throw new UnsupportedOperationException(
            "parseXes requires rust4pm_parse_xes() C function.\n" +
            "Add to lib.rs:\n" +
            "  #[no_mangle]\n" +
            "  pub extern \"C\" fn rust4pm_parse_xes(\n" +
            "      xes: *const c_char,\n" +
            "      len: usize\n" +
            "  ) -> ParseResult\n" +
            "See: rust/rust4pm/src/lib.rs"
        );
    }

    /**
     * Discover a Petri net using the Alpha++ algorithm.
     *
     * <p>Alpha++ is an enhanced version of the Alpha algorithm that handles
     * more complex process structures including non-local dependencies.
     *
     * @param log open OcelLogHandle
     * @return PetriNet discovered from the event log
     * @throws ProcessMiningException if discovery fails
     * @throws UnsupportedOperationException if native function not yet implemented
     */
    public PetriNet discoverAlphaPpp(OcelLogHandle log) throws ProcessMiningException {
        throw new UnsupportedOperationException(
            "discoverAlphaPpp requires rust4pm_discover_alpha_ppp() C function.\n" +
            "Add to lib.rs:\n" +
            "  #[no_mangle]\n" +
            "  pub extern \"C\" fn rust4pm_discover_alpha_ppp(\n" +
            "      log: OcelLogHandle\n" +
            "  ) -> DiscoveryResult\n" +
            "See: rust/rust4pm/src/lib.rs"
        );
    }

    /**
     * Compute performance statistics from the event log.
     *
     * <p>Analyzes timing information in the log to produce metrics including
     * median and mean waiting times, throughput, and per-activity statistics.
     *
     * @param log open OcelLogHandle
     * @return PerformanceStats with aggregated and per-activity metrics
     * @throws ProcessMiningException if computation fails
     * @throws UnsupportedOperationException if native function not yet implemented
     */
    public PerformanceStats computePerformanceStats(OcelLogHandle log)
            throws ProcessMiningException {
        throw new UnsupportedOperationException(
            "computePerformanceStats requires rust4pm_compute_performance_stats() C function.\n" +
            "Add to lib.rs:\n" +
            "  #[no_mangle]\n" +
            "  pub extern \"C\" fn rust4pm_compute_performance_stats(\n" +
            "      log: OcelLogHandle\n" +
            "  ) -> StatsResult\n" +
            "See: rust/rust4pm/src/lib.rs"
        );
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
