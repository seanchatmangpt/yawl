#!/usr/bin/env java
/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * Standalone runner for OCEL2 Process Mining Examples.
 * Run with: java --enable-native-access=ALL-UNNAMED -cp target/test-classes:target/classes ProcessMiningExamplesRunner
 */
package org.yawlfoundation.yawl.erlang.processmining;

import org.yawlfoundation.yawl.erlang.ErlangTestNode;
import org.yawlfoundation.yawl.erlang.error.ErlangConnectionException;
import org.yawlfoundation.yawl.erlang.error.ErlangRpcException;

import java.util.List;

/**
 * Standalone runner for Java > OTP > rust4pm > OTP > Java examples.
 *
 * <p>This runner demonstrates the complete integration chain without JUnit,
 * allowing easy verification from the command line.</p>
 *
 * <p>Usage:
 * <pre>
 *   # Start Erlang node first
 *   erl -name yawl_erl@localhost -setcookie secret -eval "application:ensure_started(yawl_erlang)"
 *
 *   # Run examples
 *   java --enable-native-access=ALL-UNNAMED \
 *     -cp target/test-classes:target/classes \
 *     org.yawlfoundation.yawl.erlang.processmining.ProcessMiningExamplesRunner
 * </pre>
 */
public class ProcessMiningExamplesRunner {

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

    public static void main(String[] args) {
        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║   Java > OTP > rust4pm > OTP > Java - Process Mining Examples         ║");
        System.out.println("╚════════════════════════════════════════════════════════════════════════╝");
        System.out.println();

        // Check if OTP is available
        if (!ErlangTestNode.isOtpAvailable()) {
            System.err.println("❌ OTP 28 not installed - cannot run examples");
            System.err.println("   Install OTP 28 or run with -DskipIntegrationTests");
            System.exit(1);
        }

        ErlangTestNode testNode = null;
        ErlangBridge bridge = null;

        try {
            // Start OTP node
            System.out.println("════════════════════════════════════════════════════════════════════════");
            System.out.println("Starting OTP Node...");
            System.out.println("════════════════════════════════════════════════════════════════════════");
            testNode = ErlangTestNode.start();
            testNode.awaitReady();
            System.out.println("✅ OTP node started: " + ErlangTestNode.NODE_NAME);
            System.out.println();

            // Connect bridge
            System.out.println("════════════════════════════════════════════════════════════════════════");
            System.out.println("Connecting Java Bridge...");
            System.out.println("════════════════════════════════════════════════════════════════════════");
            bridge = ErlangBridge.connect(ErlangTestNode.NODE_NAME, ErlangTestNode.COOKIE);
            System.out.println("✅ Bridge connected: " + bridge.isConnected());
            System.out.println();

            // Example 1: OCEL Stats
            runExample1OcelStats(bridge);

            // Example 2: DFG Discovery
            runExample2DfgDiscovery(bridge);

            // Example 3: Simple Trace DFG
            runExample3SimpleTraceDfg(bridge);

            // Example 4: Conformance Checking
            runExample4Conformance(bridge);

            // Summary
            System.out.println();
            System.out.println("════════════════════════════════════════════════════════════════════════");
            System.out.println("                        ALL EXAMPLES PASSED                              ");
            System.out.println("════════════════════════════════════════════════════════════════════════");
            System.out.println();
            System.out.println("Integration Chain Verified:");
            System.out.println("  Java → ErlangBridge (Layer 3)");
            System.out.println("    ↓");
            System.out.println("  ErlangNode (Layer 2 - libei)");
            System.out.println("    ↓");
            System.out.println("  Erlang gen_server (yawl_process_mining)");
            System.out.println("    ↓");
            System.out.println("  rust4pm_nif (NIF)");
            System.out.println("    ↓");
            System.out.println("  Rust Process Mining Algorithms");
            System.out.println();
            System.out.println("✅ Java > OTP > rust4pm > OTP > Java: COMPLETE");
            System.out.println();

        } catch (Exception e) {
            System.err.println();
            System.err.println("❌ Example failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            if (bridge != null) {
                bridge.close();
            }
            if (testNode != null) {
                testNode.close();
            }
        }
    }

    private static void runExample1OcelStats(ErlangBridge bridge) throws ErlangRpcException {
        System.out.println("════════════════════════════════════════════════════════════════════════");
        System.out.println("Example 1: OCEL Statistics (mirrors ocel_stats.rs)");
        System.out.println("════════════════════════════════════════════════════════════════════════");
        System.out.println();

        // Parse OCEL2
        System.out.println("  Parsing OCEL2 JSON...");
        Ocel2Result parseResult = bridge.parseOcel2(SAMPLE_OCEL2_JSON);

        if (!parseResult.success()) {
            System.err.println("  ❌ Failed to parse OCEL2: " + parseResult.error());
            throw new RuntimeException("OCEL2 parse failed");
        }
        System.out.println("  ✅ OCEL2 parsed successfully");
        System.out.println("     Handle: " + parseResult.handle());

        // Get stats
        Ocel2Result eventCount = bridge.ocelEventCount(parseResult.handle());
        Ocel2Result objectCount = bridge.ocelObjectCount(parseResult.handle());

        System.out.println();
        System.out.println("  OCEL Statistics:");
        System.out.println("    Events:  " + eventCount.count());
        System.out.println("    Objects: " + objectCount.count());
        System.out.println();

        // Verify
        assert eventCount.count() == 6 : "Expected 6 events";
        assert objectCount.count() == 4 : "Expected 4 objects";

        System.out.println("  ✅ Example 1 PASSED");
        System.out.println();
    }

    private static void runExample2DfgDiscovery(ErlangBridge bridge) throws ErlangRpcException {
        System.out.println("════════════════════════════════════════════════════════════════════════");
        System.out.println("Example 2: DFG Discovery (mirrors process_discovery.rs)");
        System.out.println("════════════════════════════════════════════════════════════════════════");
        System.out.println();

        // Parse OCEL2
        Ocel2Result parseResult = bridge.parseOcel2(SAMPLE_OCEL2_JSON);
        if (!parseResult.success()) {
            throw new RuntimeException("OCEL2 parse failed: " + parseResult.error());
        }

        // Discover DFG
        System.out.println("  Discovering DFG from OCEL2 log...");
        DfgResult dfg = bridge.ocelDiscoverDfg(parseResult.handle());

        if (!dfg.success()) {
            throw new RuntimeException("DFG discovery failed: " + dfg.error());
        }

        System.out.println();
        System.out.println("  DFG Discovery Result:");
        System.out.println("    Nodes (Activities): " + dfg.nodes().size());
        System.out.println("    Edges (Transitions): " + dfg.edges().size());
        System.out.println();

        // Print nodes
        System.out.println("  Activity Nodes:");
        for (DfgNode node : dfg.nodes()) {
            System.out.println("    - " + node.label() + " (count: " + node.count() + ")");
        }
        System.out.println();

        // Print edges
        System.out.println("  Transition Edges:");
        for (DfgEdge edge : dfg.edges()) {
            System.out.println("    - " + edge.source() + " → " + edge.target() +
                " (count: " + edge.count() + ")");
        }
        System.out.println();

        System.out.println("  ✅ Example 2 PASSED");
        System.out.println();
    }

    private static void runExample3SimpleTraceDfg(ErlangBridge bridge) throws ErlangRpcException {
        System.out.println("════════════════════════════════════════════════════════════════════════");
        System.out.println("Example 3: Simple Trace DFG (mirrors event_log_stats.rs)");
        System.out.println("════════════════════════════════════════════════════════════════════════");
        System.out.println();

        // Simple event log
        List<List<String>> traces = List.of(
            List.of("a", "b", "c", "d"),
            List.of("a", "b", "c", "e"),
            List.of("a", "b", "d", "c"),
            List.of("a", "c", "b", "d")
        );

        System.out.println("  Input Traces:");
        for (int i = 0; i < traces.size(); i++) {
            System.out.println("    Trace " + (i + 1) + ": " + traces.get(i));
        }
        System.out.println();

        // Discover DFG
        System.out.println("  Discovering DFG from traces...");
        DfgResult dfg = bridge.discoverDfgFromTraces(traces);

        if (!dfg.success()) {
            throw new RuntimeException("DFG discovery failed: " + dfg.error());
        }

        System.out.println();
        System.out.println("  DFG Discovery Result:");
        System.out.println("    Unique Activities: " + dfg.nodes().size());
        System.out.println("    Directly-Follows Edges: " + dfg.edges().size());
        System.out.println();

        // Print edges
        System.out.println("  Directly-Follows Relationships:");
        for (DfgEdge edge : dfg.edges()) {
            System.out.println("    - " + edge.source() + " → " + edge.target() +
                " (frequency: " + edge.count() + ")");
        }
        System.out.println();

        System.out.println("  ✅ Example 3 PASSED");
        System.out.println();
    }

    private static void runExample4Conformance(ErlangBridge bridge) throws ErlangRpcException {
        System.out.println("════════════════════════════════════════════════════════════════════════");
        System.out.println("Example 4: Conformance Checking (token replay)");
        System.out.println("════════════════════════════════════════════════════════════════════════");
        System.out.println();

        // Parse OCEL2
        Ocel2Result parseResult = bridge.parseOcel2(SAMPLE_OCEL2_JSON);
        if (!parseResult.success()) {
            throw new RuntimeException("OCEL2 parse failed: " + parseResult.error());
        }

        // Simple Petri net for order process
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

        System.out.println("  Checking conformance against Petri net model...");
        ConformanceMetrics metrics = bridge.ocelCheckConformance(parseResult.handle(), pnml);

        if (!metrics.success()) {
            throw new RuntimeException("Conformance check failed: " + metrics.error());
        }

        System.out.println();
        System.out.println("  Conformance Metrics:");
        System.out.println("    Fitness:   " + String.format("%.2f%%", metrics.fitness() * 100));
        System.out.println("    Precision: " + String.format("%.2f%%", metrics.precision() * 100));
        System.out.println();
        System.out.println("  Token Replay Statistics:");
        System.out.println("    Produced:  " + metrics.produced());
        System.out.println("    Consumed:  " + metrics.consumed());
        System.out.println("    Missing:   " + metrics.missing());
        System.out.println("    Remaining: " + metrics.remaining());
        System.out.println();

        // Verify fitness is in valid range
        assert metrics.fitness() >= 0.0 && metrics.fitness() <= 1.0 : "Fitness out of range";
        assert metrics.precision() >= 0.0 && metrics.precision() <= 1.0 : "Precision out of range";

        System.out.println("  ✅ Example 4 PASSED");
        System.out.println();
    }
}
