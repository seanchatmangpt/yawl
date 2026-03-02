/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */
package org.yawlfoundation.yawl.erlang.processmining;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.erlang.ErlangTestNode;
import org.yawlfoundation.yawl.erlang.bridge.ErlangNode;
import org.yawlfoundation.yawl.erlang.error.ErlangConnectionException;
import org.yawlfoundation.yawl.erlang.error.ErlangRpcException;
import org.yawlfoundation.yawl.erlang.term.ErlAtom;
import org.yawlfoundation.yawl.erlang.term.ErlBinary;
import org.yawlfoundation.yawl.erlang.term.ErlInteger;
import org.yawlfoundation.yawl.erlang.term.ErlTerm;
import org.yawlfoundation.yawl.erlang.term.ErlTuple;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Latency and throughput benchmarks for all 53 process_mining capabilities.
 *
 * <p>Measures per-capability round-trip latency (p50/p95/p99/max in microseconds) and
 * sustained throughput (ops/sec) across the full Java→Erlang gen_server→Rust NIF stack.
 *
 * <p>Skips automatically when OTP is not available. Run with a live OTP node to get
 * meaningful results.
 *
 * <p>Each test:
 * <ol>
 *   <li>Warms up ({@value #WARMUP} calls, results discarded)</li>
 *   <li>Measures exactly {@value #SAMPLES} calls (nanosecond timestamps)</li>
 *   <li>Runs a {@value #THROUGHPUT_MS}ms sustained window and counts completions</li>
 * </ol>
 *
 * <p>Results are printed as a formatted table in {@code @AfterAll}.
 */
@Tag("pm-bridge-perf")
@DisplayName("PM Bridge — Latency & Throughput (53 capabilities)")
class PmBridgePerformanceTest {

    /** Warmup iterations per capability (discarded). */
    private static final int WARMUP = 20;

    /** Measurement iterations per capability. */
    private static final int SAMPLES = 100;

    /** Duration of the sustained throughput window in milliseconds. */
    private static final long THROUGHPUT_MS = 2_000L;

    /**
     * Number of pre-created OCEL UUIDs for the ConsumeStore benchmark.
     * add_init_exit_events_to_ocel uses maps:take (removes from state), so each
     * call needs a fresh UUID. Pool = WARMUP + SAMPLES + throughput headroom.
     */
    private static final int CONSUME_POOL_SIZE = 500;

    // ── Shared resources (populated in @BeforeAll) ───────────────────────────

    private static ErlangTestNode testNode;
    private static ErlangNode erlNode;
    private static ProcessMiningBridge bridge;

    /** SlimLinkedOCEL built via locel_new + locel_add_* calls. */
    private static String locelUuid;

    /** OCEL derived from locel_construct_ocel(locelUuid). */
    private static String ocelUuid;

    /** SlimLinkedOCEL derived from slim_link_ocel(ocelUuid). */
    private static String slimOcelUuid;

    /** IndexLinkedOCEL derived from index_link_ocel(ocelUuid). */
    private static String indexOcelUuid;

    /** EventLog derived from flatten_ocel_on(ocelUuid, "order"). */
    private static String eventLogUuid;

    /** DirectlyFollowsGraph derived from discover_dfg(eventLogUuid). */
    private static String dfgUuid;

    /** OCDirectlyFollowsGraph derived from discover_dfg_from_ocel(ocelUuid). */
    private static String ocDfgUuid;

    /** EventLogActivityProjection derived from log_to_activity_projection(eventLogUuid). */
    private static String logProjUuid;

    /** PetriNet derived from discover_alpha_ppp(logProjUuid, "{}"). */
    private static String petriNetUuid;

    /**
     * UUID pool for add_init_exit_events_to_ocel (ConsumeStore shape).
     * Each call consumes a UUID from gen_server state (maps:take), so a fresh
     * UUID is needed per iteration. Pre-filled in @BeforeAll.
     */
    private static final Queue<String> consumePool = new LinkedList<>();

    /**
     * Counter for write-operation benchmarks that benefit from unique IDs
     * (locel_add_event, locel_add_object, etc.) to avoid duplicate-key errors.
     */
    private static final AtomicLong writeCounter = new AtomicLong(1_000_000L);

    // ── Result storage for @AfterAll table ──────────────────────────────────

    private static final Map<String, long[]> latencies = new LinkedHashMap<>();
    private static final Map<String, Double> throughputs = new LinkedHashMap<>();

    // ── Infrastructure ───────────────────────────────────────────────────────

    @BeforeAll
    static void setup() throws Exception {
        assumeTrue(ErlangTestNode.isOtpAvailable(),
                "OTP not available — skipping pm-bridge performance tests");

        testNode = ErlangTestNode.start();
        testNode.awaitReady();

        erlNode = new ErlangNode("pm_perf@127.0.0.1");
        try {
            erlNode.connect(ErlangTestNode.NODE_NAME, ErlangTestNode.COOKIE);
        } catch (ErlangConnectionException e) {
            // libei.so (Erlang C-node native library) is not available on this host.
            // Skip all performance tests gracefully rather than erroring.
            assumeTrue(false,
                    "Native Erlang C-node bridge (libei.so) not available: " + e.getMessage());
        }
        bridge = new ProcessMiningBridge(erlNode);

        // ── Step 1: Build a SlimLinkedOCEL from scratch via locel_* API ──────
        locelUuid = rpcUuid("locel_new", List.of());
        bridge.locelAddEventType(locelUuid, "task_a", "[]");
        bridge.locelAddObjectType(locelUuid, "order", "[]");
        bridge.locelAddEvent(
                locelUuid, "task_a", "2024-01-01T08:00:00Z", "ev1", "[]", "[]");
        bridge.locelAddObject(locelUuid, "order", "ob1", "[]", "[]");
        bridge.locelAddE2o(locelUuid, 0L, 0L, "related");

        // ── Step 2: Derive all other registry types from the locel ────────────
        ocelUuid      = rpcUuid("locel_construct_ocel",       List.of(bin(locelUuid)));
        slimOcelUuid  = rpcUuid("slim_link_ocel",             List.of(bin(ocelUuid)));
        indexOcelUuid = rpcUuid("index_link_ocel",            List.of(bin(ocelUuid)));
        eventLogUuid  = rpcUuid("flatten_ocel_on",            List.of(bin(ocelUuid), bin("order")));
        dfgUuid       = rpcUuid("discover_dfg",               List.of(bin(eventLogUuid)));
        ocDfgUuid     = rpcUuid("discover_dfg_from_ocel",     List.of(bin(ocelUuid)));
        logProjUuid   = rpcUuid("log_to_activity_projection", List.of(bin(eventLogUuid)));
        petriNetUuid  = rpcUuid("discover_alpha_ppp",         List.of(bin(logProjUuid), bin("{}")));

        // ── Step 3: Pre-fill OCEL UUID pool for ConsumeStore benchmark ────────
        // Each locel_construct_ocel(locelUuid) creates a fresh OCEL UUID without
        // consuming locelUuid (ResolveStore = maps:get, not maps:take).
        for (int i = 0; i < CONSUME_POOL_SIZE; i++) {
            consumePool.add(rpcUuid("locel_construct_ocel", List.of(bin(locelUuid))));
        }
    }

    @AfterAll
    static void teardown() {
        printTable();
        if (erlNode != null) erlNode.close();
        if (testNode != null) testNode.close();
    }

    // ── Analysis (3 capabilities) ────────────────────────────────────────────

    @Test
    @DisplayName("get_dotted_chart — analysis")
    void perf_getDottedChart() throws Exception {
        bench("get_dotted_chart", () -> bridge.getDottedChart(eventLogUuid, "{}"));
    }

    @Test
    @DisplayName("get_event_timestamps — analysis")
    void perf_getEventTimestamps() throws Exception {
        bench("get_event_timestamps", () -> bridge.getEventTimestamps(eventLogUuid, "{}"));
    }

    @Test
    @DisplayName("get_object_attribute_changes — analysis")
    void perf_getObjectAttributeChanges() throws Exception {
        bench("get_object_attribute_changes",
                () -> bridge.getObjectAttributeChanges(ocelUuid, "ob1"));
    }

    // ── Conformance (1 capability) ───────────────────────────────────────────

    @Test
    @DisplayName("oc_declare_conformance — conformance")
    void perf_ocDeclareConformance() throws Exception {
        bench("oc_declare_conformance", () -> bridge.ocDeclareConformance(ocelUuid, "{}"));
    }

    // ── Discovery (4 capabilities) ───────────────────────────────────────────

    @Test
    @DisplayName("discover_alpha_ppp — discovery")
    void perf_discoverAlphaPpp() throws Exception {
        bench("discover_alpha_ppp", () -> bridge.discoverAlphaPpp(logProjUuid, "{}"));
    }

    @Test
    @DisplayName("discover_dfg — discovery")
    void perf_discoverDfg() throws Exception {
        bench("discover_dfg", () -> bridge.discoverDfg(eventLogUuid));
    }

    @Test
    @DisplayName("discover_dfg_from_ocel — discovery")
    void perf_discoverDfgFromOcel() throws Exception {
        bench("discover_dfg_from_ocel", () -> bridge.discoverDfgFromOcel(ocelUuid));
    }

    @Test
    @DisplayName("discover_oc_declare — discovery")
    void perf_discoverOcDeclare() throws Exception {
        bench("discover_oc_declare", () -> bridge.discoverOcDeclare(slimOcelUuid, "{}"));
    }

    // ── I/O (9 capabilities) ─────────────────────────────────────────────────

    @Test
    @DisplayName("add_init_exit_events_to_ocel — io [ConsumeStore]")
    void perf_addInitExitEventsToOcel() throws Exception {
        benchConsumeStore("add_init_exit_events_to_ocel");
    }

    @Test
    @DisplayName("export_dfg_image_png — io")
    void perf_exportDfgImagePng() throws Exception {
        bench("export_dfg_image_png", () -> bridge.exportDfgImagePng(dfgUuid, "/dev/null"));
    }

    @Test
    @DisplayName("export_dfg_image_svg — io")
    void perf_exportDfgImageSvg() throws Exception {
        bench("export_dfg_image_svg", () -> bridge.exportDfgImageSvg(dfgUuid, "/dev/null"));
    }

    @Test
    @DisplayName("export_dfg_png — io")
    void perf_exportDfgPng() throws Exception {
        bench("export_dfg_png", () -> bridge.exportDfgPng(dfgUuid, "/dev/null"));
    }

    @Test
    @DisplayName("export_petri_net_image_png — io")
    void perf_exportPetriNetImagePng() throws Exception {
        bench("export_petri_net_image_png",
                () -> bridge.exportPetriNetImagePng(petriNetUuid, "/dev/null"));
    }

    @Test
    @DisplayName("export_petri_net_image_svg — io")
    void perf_exportPetriNetImageSvg() throws Exception {
        bench("export_petri_net_image_svg",
                () -> bridge.exportPetriNetImageSvg(petriNetUuid, "/dev/null"));
    }

    @Test
    @DisplayName("flatten_ocel_on — io")
    void perf_flattenOcelOn() throws Exception {
        bench("flatten_ocel_on", () -> bridge.flattenOcelOn(ocelUuid, "order"));
    }

    @Test
    @DisplayName("log_to_activity_projection — io")
    void perf_logToActivityProjection() throws Exception {
        bench("log_to_activity_projection",
                () -> bridge.logToActivityProjection(eventLogUuid));
    }

    // ── locel (31 capabilities) ──────────────────────────────────────────────

    @Test
    @DisplayName("locel_add_e2o — locel")
    void perf_locelAddE2o() throws Exception {
        bench("locel_add_e2o", () -> bridge.locelAddE2o(locelUuid, 0L, 0L, "related"));
    }

    @Test
    @DisplayName("locel_add_event — locel")
    void perf_locelAddEvent() throws Exception {
        bench("locel_add_event", () -> bridge.locelAddEvent(
                locelUuid, "task_a", "2024-01-01T08:00:00Z",
                "ev_" + writeCounter.getAndIncrement(), "[]", "[]"));
    }

    @Test
    @DisplayName("locel_add_event_type — locel")
    void perf_locelAddEventType() throws Exception {
        bench("locel_add_event_type", () -> bridge.locelAddEventType(
                locelUuid, "t_" + writeCounter.getAndIncrement(), "[]"));
    }

    @Test
    @DisplayName("locel_add_o2o — locel")
    void perf_locelAddO2o() throws Exception {
        bench("locel_add_o2o", () -> bridge.locelAddO2o(locelUuid, 0L, 0L, "linked"));
    }

    @Test
    @DisplayName("locel_add_object — locel")
    void perf_locelAddObject() throws Exception {
        bench("locel_add_object", () -> bridge.locelAddObject(
                locelUuid, "order", "ob_" + writeCounter.getAndIncrement(), "[]", "[]"));
    }

    @Test
    @DisplayName("locel_add_object_type — locel")
    void perf_locelAddObjectType() throws Exception {
        bench("locel_add_object_type", () -> bridge.locelAddObjectType(
                locelUuid, "ot_" + writeCounter.getAndIncrement(), "[]"));
    }

    @Test
    @DisplayName("locel_construct_ocel — locel")
    void perf_locelConstructOcel() throws Exception {
        bench("locel_construct_ocel", () -> bridge.locelConstructOcel(locelUuid));
    }

    @Test
    @DisplayName("locel_delete_e2o — locel")
    void perf_locelDeleteE2o() throws Exception {
        bench("locel_delete_e2o", () -> bridge.locelDeleteE2o(locelUuid, 0L, 0L));
    }

    @Test
    @DisplayName("locel_delete_o2o — locel")
    void perf_locelDeleteO2o() throws Exception {
        bench("locel_delete_o2o", () -> bridge.locelDeleteO2o(locelUuid, 0L, 0L));
    }

    @Test
    @DisplayName("locel_get_e2o — locel")
    void perf_locelGetE2o() throws Exception {
        bench("locel_get_e2o", () -> bridge.locelGetE2o(locelUuid, 0L));
    }

    @Test
    @DisplayName("locel_get_e2o_rev — locel")
    void perf_locelGetE2oRev() throws Exception {
        bench("locel_get_e2o_rev", () -> bridge.locelGetE2oRev(locelUuid, 0L));
    }

    @Test
    @DisplayName("locel_get_ev_attr_val — locel")
    void perf_locelGetEvAttrVal() throws Exception {
        bench("locel_get_ev_attr_val",
                () -> bridge.locelGetEvAttrVal(locelUuid, 0L, "concept:name"));
    }

    @Test
    @DisplayName("locel_get_ev_by_id — locel")
    void perf_locelGetEvById() throws Exception {
        bench("locel_get_ev_by_id", () -> bridge.locelGetEvById(locelUuid, "ev1"));
    }

    @Test
    @DisplayName("locel_get_ev_id — locel")
    void perf_locelGetEvId() throws Exception {
        bench("locel_get_ev_id", () -> bridge.locelGetEvId(locelUuid, 0L));
    }

    @Test
    @DisplayName("locel_get_ev_time — locel")
    void perf_locelGetEvTime() throws Exception {
        bench("locel_get_ev_time", () -> bridge.locelGetEvTime(locelUuid, 0L));
    }

    @Test
    @DisplayName("locel_get_ev_type — locel")
    void perf_locelGetEvType() throws Exception {
        bench("locel_get_ev_type", () -> bridge.locelGetEvType(locelUuid, "task_a"));
    }

    @Test
    @DisplayName("locel_get_ev_type_of — locel")
    void perf_locelGetEvTypeOf() throws Exception {
        bench("locel_get_ev_type_of", () -> bridge.locelGetEvTypeOf(locelUuid, 0L));
    }

    @Test
    @DisplayName("locel_get_ev_types — locel")
    void perf_locelGetEvTypes() throws Exception {
        bench("locel_get_ev_types", () -> bridge.locelGetEvTypes(locelUuid));
    }

    @Test
    @DisplayName("locel_get_evs_of_type — locel")
    void perf_locelGetEvsOfType() throws Exception {
        bench("locel_get_evs_of_type",
                () -> bridge.locelGetEvsOfType(locelUuid, "task_a"));
    }

    @Test
    @DisplayName("locel_get_full_ev — locel")
    void perf_locelGetFullEv() throws Exception {
        bench("locel_get_full_ev", () -> bridge.locelGetFullEv(locelUuid, 0L));
    }

    @Test
    @DisplayName("locel_get_full_ob — locel")
    void perf_locelGetFullOb() throws Exception {
        bench("locel_get_full_ob", () -> bridge.locelGetFullOb(locelUuid, 0L));
    }

    @Test
    @DisplayName("locel_get_o2o — locel")
    void perf_locelGetO2o() throws Exception {
        bench("locel_get_o2o", () -> bridge.locelGetO2o(locelUuid, 0L));
    }

    @Test
    @DisplayName("locel_get_o2o_rev — locel")
    void perf_locelGetO2oRev() throws Exception {
        bench("locel_get_o2o_rev", () -> bridge.locelGetO2oRev(locelUuid, 0L));
    }

    @Test
    @DisplayName("locel_get_ob_attr_vals — locel")
    void perf_locelGetObAttrVals() throws Exception {
        bench("locel_get_ob_attr_vals",
                () -> bridge.locelGetObAttrVals(locelUuid, 0L, "concept:name"));
    }

    @Test
    @DisplayName("locel_get_ob_by_id — locel")
    void perf_locelGetObById() throws Exception {
        bench("locel_get_ob_by_id", () -> bridge.locelGetObById(locelUuid, "ob1"));
    }

    @Test
    @DisplayName("locel_get_ob_id — locel")
    void perf_locelGetObId() throws Exception {
        bench("locel_get_ob_id", () -> bridge.locelGetObId(locelUuid, 0L));
    }

    @Test
    @DisplayName("locel_get_ob_type — locel")
    void perf_locelGetObType() throws Exception {
        bench("locel_get_ob_type", () -> bridge.locelGetObType(locelUuid, "order"));
    }

    @Test
    @DisplayName("locel_get_ob_type_of — locel")
    void perf_locelGetObTypeOf() throws Exception {
        bench("locel_get_ob_type_of", () -> bridge.locelGetObTypeOf(locelUuid, 0L));
    }

    @Test
    @DisplayName("locel_get_ob_types — locel")
    void perf_locelGetObTypes() throws Exception {
        bench("locel_get_ob_types", () -> bridge.locelGetObTypes(locelUuid));
    }

    @Test
    @DisplayName("locel_get_obs_of_type — locel")
    void perf_locelGetObsOfType() throws Exception {
        bench("locel_get_obs_of_type",
                () -> bridge.locelGetObsOfType(locelUuid, "order"));
    }

    @Test
    @DisplayName("locel_new — locel [PureStore]")
    void perf_locelNew() throws Exception {
        bench("locel_new", () -> bridge.locelNew());
    }

    // ── Utility (5 capabilities) ─────────────────────────────────────────────

    @Test
    @DisplayName("index_link_ocel — utility")
    void perf_indexLinkOcel() throws Exception {
        bench("index_link_ocel", () -> bridge.indexLinkOcel(ocelUuid));
    }

    @Test
    @DisplayName("num_events — utility")
    void perf_numEvents() throws Exception {
        bench("num_events", () -> bridge.numEvents(ocelUuid));
    }

    @Test
    @DisplayName("num_objects — utility")
    void perf_numObjects() throws Exception {
        bench("num_objects", () -> bridge.numObjects(ocelUuid));
    }

    @Test
    @DisplayName("ocel_type_stats — utility")
    void perf_ocelTypeStats() throws Exception {
        bench("ocel_type_stats", () -> bridge.ocelTypeStats(ocelUuid));
    }

    @Test
    @DisplayName("slim_link_ocel — utility")
    void perf_slimLinkOcel() throws Exception {
        bench("slim_link_ocel", () -> bridge.slimLinkOcel(ocelUuid));
    }

    // ── Test utility (1 capability, Pure shape) ───────────────────────────────

    @Test
    @DisplayName("test_some_inputs — (Pure: no registry ops)")
    void perf_testSomeInputs() throws Exception {
        bench("test_some_inputs",
                () -> bridge.testSomeInputs("hello", 1, 2, 3.14, true));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Benchmark helpers
    // ═════════════════════════════════════════════════════════════════════════

    @FunctionalInterface
    private interface BenchCallable {
        String call() throws Exception;
    }

    /**
     * Standard benchmark: warmup → sample SAMPLES latencies → throughput window.
     * Asserts non-negative latency and positive throughput.
     */
    private static void bench(String name, BenchCallable fn) throws Exception {
        // Warmup: calls are issued but timings are discarded
        for (int i = 0; i < WARMUP; i++) {
            fn.call();
        }

        // Measure: capture per-call latency in nanoseconds
        long[] ns = new long[SAMPLES];
        for (int i = 0; i < SAMPLES; i++) {
            long t0 = System.nanoTime();
            fn.call();
            ns[i] = System.nanoTime() - t0;
        }
        Arrays.sort(ns);
        latencies.put(name, ns);

        // Throughput: count completions in a fixed window
        long end = System.currentTimeMillis() + THROUGHPUT_MS;
        long count = 0;
        while (System.currentTimeMillis() < end) {
            fn.call();
            count++;
        }
        double ops = (double) count * 1000.0 / THROUGHPUT_MS;
        throughputs.put(name, ops);

        assertTrue(ns[0] >= 0, name + ": p0 latency must be non-negative");
        assertTrue(ops > 0, name + ": throughput must be positive");
    }

    /**
     * ConsumeStore benchmark for add_init_exit_events_to_ocel.
     *
     * <p>This function uses maps:take in the gen_server, consuming each UUID from state.
     * Each call therefore requires a fresh OCEL UUID from the pre-filled pool.
     * Throughput measurement stops when the pool is exhausted.
     */
    private static void benchConsumeStore(String name) throws Exception {
        // Warmup: drain WARMUP UUIDs from pool
        for (int i = 0; i < WARMUP; i++) {
            String uuid = consumePool.poll();
            if (uuid != null) {
                bridge.addInitExitEventsToOcel(uuid);
            }
        }

        // Measure: drain SAMPLES UUIDs, record latency for each valid call
        long[] ns = new long[SAMPLES];
        int measured = 0;
        for (int i = 0; i < SAMPLES; i++) {
            String uuid = consumePool.poll();
            if (uuid == null) {
                break; // pool exhausted
            }
            long t0 = System.nanoTime();
            bridge.addInitExitEventsToOcel(uuid);
            ns[measured++] = System.nanoTime() - t0;
        }
        long[] valid = measured > 0 ? Arrays.copyOf(ns, measured) : new long[]{0L};
        Arrays.sort(valid);
        latencies.put(name, valid);

        // Throughput: drain remaining pool within time window
        long end = System.currentTimeMillis() + THROUGHPUT_MS;
        long count = 0;
        while (System.currentTimeMillis() < end) {
            String uuid = consumePool.poll();
            if (uuid == null) {
                break; // pool exhausted before time window closed
            }
            bridge.addInitExitEventsToOcel(uuid);
            count++;
        }
        double ops = (double) count * 1000.0 / THROUGHPUT_MS;
        throughputs.put(name, ops);

        assertTrue(measured > 0, name + ": consume pool exhausted before any measurement");
        assertTrue(valid[0] >= 0, name + ": p0 latency must be non-negative");
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Setup helpers
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Call the process_mining_bridge gen_server and extract the UUID from the reply.
     * Handles both plain binary replies and {ok, binary} tuple replies from ResolveStore
     * / PureStore shapes.
     */
    private static String rpcUuid(String fn, List<ErlTerm> args) throws ErlangRpcException {
        ErlTerm result = erlNode.rpc("process_mining_bridge", fn, args);
        return extractUuid(fn, result);
    }

    /**
     * Extracts a UUID string from an Erlang reply term.
     *
     * <p>Handles two reply shapes:
     * <ul>
     *   <li>Plain binary: {@code <<"uuid">>} — returned by direct NIF calls</li>
     *   <li>OK tuple: {@code {ok, <<"uuid">>}} — returned by PureStore/ResolveStore clauses</li>
     * </ul>
     */
    private static String extractUuid(String context, ErlTerm result) {
        return switch (result) {
            case ErlBinary eb -> new String(eb.data(), StandardCharsets.UTF_8);
            case ErlTuple t when t.elements().size() == 2
                    && t.elements().get(0) instanceof ErlAtom a
                    && "ok".equals(a.value())
                    && t.elements().get(1) instanceof ErlBinary eb ->
                    new String(eb.data(), StandardCharsets.UTF_8);
            default -> throw new IllegalStateException(
                    "Expected UUID reply for '" + context + "' but got: "
                    + result.getClass().getSimpleName());
        };
    }

    /** Convenience factory: wrap a String as an ErlBinary (UTF-8 encoded). */
    private static ErlBinary bin(String s) {
        return new ErlBinary(s.getBytes(StandardCharsets.UTF_8));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Reporting
    // ═════════════════════════════════════════════════════════════════════════

    private static void printTable() {
        if (latencies.isEmpty()) {
            return; // nothing measured (all skipped)
        }
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║    PM Bridge — Performance Results (Java → Erlang → Rust NIF)       ║");
        System.out.printf( "║    warmup=%d  samples=%d  throughput_window=%dms%30s║%n",
                WARMUP, SAMPLES, THROUGHPUT_MS, "");
        System.out.println("╠══════════════════════════════════════════════════════════════════════╣");
        System.out.printf("║  %-40s %7s %7s %7s %7s %8s  ║%n",
                "Capability", "p50µs", "p95µs", "p99µs", "maxµs", "ops/s");
        System.out.println("╠══════════════════════════════════════════════════════════════════════╣");

        for (Map.Entry<String, long[]> entry : latencies.entrySet()) {
            String name = entry.getKey();
            long[] ns = entry.getValue();
            double ops = throughputs.getOrDefault(name, 0.0);
            System.out.printf("║  %-40s %7.1f %7.1f %7.1f %7.1f %8.1f  ║%n",
                    name,
                    pct(ns, 50) / 1_000.0,
                    pct(ns, 95) / 1_000.0,
                    pct(ns, 99) / 1_000.0,
                    ns[ns.length - 1] / 1_000.0,
                    ops);
        }
        System.out.println("╚══════════════════════════════════════════════════════════════════════╝");
    }

    private static long pct(long[] sorted, double percentile) {
        int idx = (int) Math.ceil(percentile / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(idx, sorted.length - 1))];
    }
}
