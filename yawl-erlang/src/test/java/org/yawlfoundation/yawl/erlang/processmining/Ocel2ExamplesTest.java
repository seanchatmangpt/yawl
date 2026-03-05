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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.yawlfoundation.yawl.erlang.ErlangTestNode;
import org.yawlfoundation.yawl.erlang.error.ErlangConnectionException;
import org.yawlfoundation.yawl.erlang.error.ErlangRpcException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * OCEL2 Process Mining Examples - Java > OTP > rust4pm > OTP > Java Chain
 *
 * <p>These examples demonstrate the complete integration chain:
 * <ol>
 *   <li>Java calls ErlangBridge (Layer 3 domain API)</li>
 *   <li>ErlangBridge calls ErlangNode (Layer 2 libei bridge)</li>
 *   <li>ErlangNode calls Erlang gen_server via RPC</li>
 *   <li>Erlang gen_server calls rust4pm_nif NIF</li>
 *   <li>NIF executes Rust process mining algorithms</li>
 *   <li>Results flow back: Rust -> NIF -> Erlang -> libei -> Java</li>
 * </ol>
 *
 * <p>Examples mirror rust4pm examples from:
 * https://github.com/aarkue/rust4pm/tree/7bcbcbf2e27c40917a2f8b1e2943f005b8cc59c8/process_mining/examples
 *
 * @see ErlangBridge
 * @see Ocel2Result
 */
@Tag("integration")
class Ocel2ExamplesTest {

    private static ErlangTestNode testNode;
    private static ErlangBridge bridge;

    /**
     * Sample OCEL2 JSON for testing (minimal order management scenario).
     */
    private static final String SAMPLE_OCEL2_JSON = """
        {
          "objectTypes": [
            {"name": "Order", "attributes": []},
            {"name": "Item", "attributes": []},
            {"name": "Customer", "attributes": []}
          ],
          "eventTypes": [
            {"name": "Create Order", "attributes": []},
            {"name": "Add Item", "attributes": []},
            {"name": "Ship Order", "attributes": []},
            {"name": "Deliver Order", "attributes": []},
            {"name": "Invoice Order", "attributes": []}
          ],
          "objects": [
            {"id": "order_1", "type": "Order", "attributes": []},
            {"id": "item_1", "type": "Item", "attributes": []},
            {"id": "item_2", "type": "Item", "attributes": []},
            {"id": "customer_1", "type": "Customer", "attributes": []}
          ],
          "events": [
            {
              "id": "e1",
              "type": "Create Order",
              "time": "2024-01-01T10:00:00Z",
              "attributes": [],
              "relationships": [
                {"objectId": "order_1", "qualifier": ""},
                {"objectId": "customer_1", "qualifier": ""}
              ]
            },
            {
              "id": "e2",
              "type": "Add Item",
              "time": "2024-01-01T10:05:00Z",
              "attributes": [],
              "relationships": [
                {"objectId": "order_1", "qualifier": ""},
                {"objectId": "item_1", "qualifier": ""}
              ]
            },
            {
              "id": "e3",
              "type": "Add Item",
              "time": "2024-01-01T10:10:00Z",
              "attributes": [],
              "relationships": [
                {"objectId": "order_1", "qualifier": ""},
                {"objectId": "item_2", "qualifier": ""}
              ]
            },
            {
              "id": "e4",
              "type": "Ship Order",
              "time": "2024-01-01T11:00:00Z",
              "attributes": [],
              "relationships": [
                {"objectId": "order_1", "qualifier": ""}
              ]
            },
            {
              "id": "e5",
              "type": "Deliver Order",
              "time": "2024-01-01T14:00:00Z",
              "attributes": [],
              "relationships": [
                {"objectId": "order_1", "qualifier": ""},
                {"objectId": "customer_1", "qualifier": ""}
              ]
            },
            {
              "id": "e6",
              "type": "Invoice Order",
              "time": "2024-01-01T15:00:00Z",
              "attributes": [],
              "relationships": [
                {"objectId": "order_1", "qualifier": ""}
              ]
            }
          ]
        }
        """;

    @BeforeAll
    static void startOtp() throws Exception {
        assumeTrue(ErlangTestNode.isOtpAvailable(), "OTP 28 not installed — skipping integration tests");
        testNode = ErlangTestNode.start();
        testNode.awaitReady();
        bridge = ErlangBridge.connect(ErlangTestNode.NODE_NAME, ErlangTestNode.COOKIE);
    }

    @AfterAll
    static void stopOtp() {
        if (bridge != null) {
            bridge.close();
        }
        if (testNode != null) {
            testNode.close();
        }
    }

    // =========================================================================
    // Example 1: OCEL Stats (mirrors ocel_stats.rs)
    // =========================================================================

    /**
     * Example 1: OCEL Statistics
     *
     * <p>Java > OTP > rust4pm > OTP > Java chain:
     * <ol>
     *   <li>Java: parseOcel2() sends JSON to Erlang</li>
     *   <li>Erlang: yawl_process_mining:parse_ocel2/1 calls NIF</li>
     *   <li>NIF: parse_ocel2_json() parses JSON in Rust</li>
     *   <li>Rust: Returns OcelLogResource handle</li>
     *   <li>Erlang: Returns handle reference to Java</li>
     *   <li>Java: ocelEventCount() and ocelObjectCount() query handle</li>
     * </ol>
     */
    @Test
    @Timeout(10)
    void example1_ocelStats() throws Exception {
        // Import OCEL2 JSON
        Ocel2Result parseResult = bridge.parseOcel2(SAMPLE_OCEL2_JSON);
        assertNotNull(parseResult, "parseOcel2 should return a result");
        assertTrue(parseResult.success(), "parseOcel2 should succeed: " + parseResult.error());
        assertNotNull(parseResult.handle(), "parseOcel2 should return a handle");

        // Get event count
        Ocel2Result eventCount = bridge.ocelEventCount(parseResult.handle());
        assertTrue(eventCount.success(), "ocelEventCount should succeed");
        assertEquals(6, eventCount.count(), "Should have 6 events");

        // Get object count
        Ocel2Result objectCount = bridge.ocelObjectCount(parseResult.handle());
        assertTrue(objectCount.success(), "ocelObjectCount should succeed");
        assertEquals(4, objectCount.count(), "Should have 4 objects");

        System.out.println("Example 1 - OCEL Stats:");
        System.out.println("  Events: " + eventCount.count());
        System.out.println("  Objects: " + objectCount.count());
    }

    // =========================================================================
    // Example 2: DFG Discovery (mirrors process_discovery.rs)
    // =========================================================================

    /**
     * Example 2: DFG Discovery from OCEL
     *
     * <p>Java > OTP > rust4pm > OTP > Java chain:
     * <ol>
     *   <li>Java: ocelDiscoverDfg() with handle</li>
     *   <li>Erlang: yawl_process_mining:ocel_discover_dfg/1 calls NIF</li>
     *   <li>NIF: discover_dfg() builds DFG in Rust</li>
     *   <li>Rust: Returns JSON with nodes and edges</li>
     *   <li>Erlang: Decodes JSON to map</li>
     *   <li>Java: Receives DfgResult with nodes and edges</li>
     * </ol>
     */
    @Test
    @Timeout(10)
    void example2_dfgDiscovery() throws Exception {
        // Parse OCEL2
        Ocel2Result parseResult = bridge.parseOcel2(SAMPLE_OCEL2_JSON);
        assertTrue(parseResult.success(), "parseOcel2 should succeed");

        // Discover DFG
        DfgResult dfg = bridge.ocelDiscoverDfg(parseResult.handle());
        assertTrue(dfg.success(), "DFG discovery should succeed");
        assertNotNull(dfg.nodes(), "DFG should have nodes");
        assertNotNull(dfg.edges(), "DFG should have edges");

        System.out.println("Example 2 - DFG Discovery:");
        System.out.println("  Nodes: " + dfg.nodes().size());
        System.out.println("  Edges: " + dfg.edges().size());

        // Verify expected activities are present
        assertTrue(dfg.nodes().size() >= 4, "Should have at least 4 activity types");
        assertTrue(dfg.edges().size() >= 3, "Should have at least 3 edges");

        // Print edges for verification
        dfg.edges().forEach(edge ->
            System.out.println("  Edge: " + edge.source() + " -> " + edge.target() +
                " (count: " + edge.count() + ")")
        );
    }

    // =========================================================================
    // Example 3: Conformance Checking (mirrors token replay)
    // =========================================================================

    /**
     * Example 3: Conformance Checking with Token Replay
     *
     * <p>Java > OTP > rust4pm > OTP > Java chain:
     * <ol>
     *   <li>Java: ocelCheckConformance() with handle and PNML</li>
     *   <li>Erlang: yawl_process_mining:ocel_check_conformance/2 calls NIF</li>
     *   <li>NIF: check_conformance() replays tokens in Rust</li>
     *   <li>Rust: Returns fitness and precision metrics</li>
     *   <li>Erlang: Decodes JSON to map</li>
     *   <li>Java: Receives ConformanceMetrics</li>
     * </ol>
     */
    @Test
    @Timeout(10)
    void example3_conformanceChecking() throws Exception {
        // Parse OCEL2
        Ocel2Result parseResult = bridge.parseOcel2(SAMPLE_OCEL2_JSON);
        assertTrue(parseResult.success(), "parseOcel2 should succeed");

        // Simple Petri net PNML for order process
        String pnml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <pnml>
              <net id="order_net">
                <place id="p_start">
                  <initialMarking><text>1</text></initialMarking>
                </place>
                <place id="p1"/>
                <place id="p2"/>
                <place id="p3"/>
                <place id="p_end"/>
                <transition id="t_create">
                  <name><text>Create Order</text></name>
                </transition>
                <transition id="t_add">
                  <name><text>Add Item</text></name>
                </transition>
                <transition id="t_ship">
                  <name><text>Ship Order</text></name>
                </transition>
                <transition id="t_deliver">
                  <name><text>Deliver Order</text></name>
                </transition>
                <arc source="p_start" target="t_create"/>
                <arc source="t_create" target="p1"/>
                <arc source="p1" target="t_add"/>
                <arc source="t_add" target="p2"/>
                <arc source="p2" target="t_ship"/>
                <arc source="t_ship" target="p3"/>
                <arc source="p3" target="t_deliver"/>
                <arc source="t_deliver" target="p_end"/>
              </net>
            </pnml>
            """;

        // Check conformance
        ConformanceMetrics metrics = bridge.ocelCheckConformance(parseResult.handle(), pnml);
        assertTrue(metrics.success(), "Conformance check should succeed");
        assertTrue(metrics.fitness() >= 0.0 && metrics.fitness() <= 1.0,
            "Fitness should be in [0, 1]");
        assertTrue(metrics.precision() >= 0.0 && metrics.precision() <= 1.0,
            "Precision should be in [0, 1]");

        System.out.println("Example 3 - Conformance Checking:");
        System.out.println("  Fitness: " + String.format("%.2f", metrics.fitness()));
        System.out.println("  Precision: " + String.format("%.2f", metrics.precision()));
        System.out.println("  Produced: " + metrics.produced());
        System.out.println("  Consumed: " + metrics.consumed());
        System.out.println("  Missing: " + metrics.missing());
        System.out.println("  Remaining: " + metrics.remaining());
    }

    // =========================================================================
    // Example 4: Simple Trace DFG (mirrors event_log_stats.rs)
    // =========================================================================

    /**
     * Example 4: Simple Trace DFG Discovery
     *
     * <p>Uses simple trace format (no OCEL) for basic process discovery.
     */
    @Test
    @Timeout(10)
    void example4_simpleTraceDfg() throws Exception {
        // Simple event log (traces as lists of activities)
        List<List<String>> traces = List.of(
            List.of("a", "b", "c", "d"),
            List.of("a", "b", "c", "e"),
            List.of("a", "b", "d", "c"),
            List.of("a", "c", "b", "d")
        );

        // Discover DFG from simple traces
        DfgResult dfg = bridge.discoverDfgFromTraces(traces);
        assertTrue(dfg.success(), "DFG discovery from traces should succeed");

        System.out.println("Example 4 - Simple Trace DFG:");
        System.out.println("  Input traces: " + traces.size());
        System.out.println("  DFG nodes: " + dfg.nodes().size());
        System.out.println("  DFG edges: " + dfg.edges().size());

        // Verify DFG structure
        assertTrue(dfg.nodes().size() >= 5, "Should have at least 5 unique activities");
        assertTrue(dfg.edges().size() >= 5, "Should have at least 5 edges");
    }

    // =========================================================================
    // Example 5: Full Analysis (combines multiple metrics)
    // =========================================================================

    /**
     * Example 5: Full Process Analysis
     *
     * <p>Combines DFG discovery, conformance, and statistics.
     */
    @Test
    @Timeout(15)
    void example5_fullAnalysis() throws Exception {
        // Parse OCEL2
        Ocel2Result parseResult = bridge.parseOcel2(SAMPLE_OCEL2_JSON);
        assertTrue(parseResult.success(), "parseOcel2 should succeed");

        // Get all statistics
        Ocel2Result eventCount = bridge.ocelEventCount(parseResult.handle());
        Ocel2Result objectCount = bridge.ocelObjectCount(parseResult.handle());
        DfgResult dfg = bridge.ocelDiscoverDfg(parseResult.handle());

        System.out.println("Example 5 - Full Analysis:");
        System.out.println("=== OCEL Statistics ===");
        System.out.println("  Events: " + eventCount.count());
        System.out.println("  Objects: " + objectCount.count());
        System.out.println("=== DFG Discovery ===");
        System.out.println("  Activities: " + dfg.nodes().size());
        System.out.println("  Transitions: " + dfg.edges().size());

        // Verify complete chain worked
        assertTrue(eventCount.success(), "Event count should succeed");
        assertTrue(objectCount.success(), "Object count should succeed");
        assertTrue(dfg.success(), "DFG discovery should succeed");

        System.out.println("=== Integration Chain Verified ===");
        System.out.println("Java > OTP > rust4pm > OTP > Java: SUCCESS");
    }
}
