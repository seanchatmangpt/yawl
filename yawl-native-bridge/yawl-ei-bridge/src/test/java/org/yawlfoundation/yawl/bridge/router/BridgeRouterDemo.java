/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */
package org.yawlfoundation.yawl.bridge.router;

import org.yawlfoundation.yawl.nativebridge.qlever.QLeverEngine;
import org.yawlfoundation.yawl.nativebridge.erlang.ProcessMiningClient;
import org.yawlfoundation.yawl.nativebridge.qlever.QLeverException;
import org.yawlfoundation.yawl.nativebridge.erlang.ErlangException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Demonstration of BridgeRouter usage patterns.
 * Shows various scenarios and best practices for routing NativeCall triples.
 */
public class BridgeRouterDemo {

    /**
     * Demonstrates basic routing for different execution domains.
     */
    public void demonstrateBasicRouting() {
        try (BridgeRouter router = new BridgeRouter(false)) {
            System.out.println("=== Basic Routing Demo ===");

            // JVM domain routing
            NativeCall jvmCall = NativeCall.of(
                "http://example.org/workflow/123",
                "http://schema.org/name",
                "Order Processing Workflow",
                CallPattern.JVM
            );

            System.out.println("Routing JVM call: " + jvmCall.toNtriple());

            // Note: In real usage, this would execute against QLeverEngine
            // For demo, we'll show the routing process
            RoutingResult result1 = router.route(jvmCall);
            if (result1.isSuccess()) {
                System.out.println("JVM result: " + result1.getResult());
            } else {
                System.out.println("JVM failed: " + result1.getErrorMessage());
            }

            // BEAM domain routing
            NativeCall beamCall = NativeCall.of(
                "/path/to/ocel.json",
                "http://example.org/import",
                "imported_log",
                CallPattern.BEAM
            );

            System.out.println("Routing BEAM call: " + beamCall.toNtriple());

            RoutingResult result2 = router.route(beamCall);
            if (result2.isSuccess()) {
                System.out.println("BEAM result: " + result2.getResult());
            } else {
                System.out.println("BEAM failed: " + result2.getErrorMessage());
            }

            // DIRECT domain (blocked)
            NativeCall directCall = NativeCall.of(
                "s1", "p1", "o1", CallPattern.DIRECT
            );

            try {
                router.route(directCall);
            } catch (UnsupportedOperationException e) {
                System.out.println("DIRECT call correctly blocked: " + e.getMessage());
            }
        }
    }

    /**
     * Demonstrates routing with result caching.
     */
    public void demonstrateCaching() {
        try (BridgeRouter router = new BridgeRouter(true)) {
            System.out.println("\n=== Caching Demo ===");

            NativeCall call = NativeCall.of(
                "http://example.org/data/1",
                "http://example.org/SELECT",
                "SELECT * WHERE { ?s ?p ?o }",
                CallPattern.JVM
            );

            // First execution
            Instant start1 = Instant.now();
            RoutingResult result1 = router.route(call);
            Duration time1 = Duration.between(start1, Instant.now());

            System.out.println("First execution time: " + time1.toMillis() + "ms");

            // Second execution (should use cache)
            Instant start2 = Instant.now();
            RoutingResult result2 = router.route(call);
            Duration time2 = Duration.between(start2, Instant.now());

            System.out.println("Cached execution time: " + time2.toMillis() + "ms");
            System.out.println("Cache size: " + router.getCacheSize());

            // Verify results are identical
            if (result1.getResult() != null && result2.getResult() != null) {
                assertEquals(result1.getResult(), result2.getResult());
            }
        }
    }

    /**
     * Demonstrates custom engine configuration.
     */
    public void demonstrateCustomEngines() throws Exception {
        System.out.println("\n=== Custom Engine Demo ===");

        // Create custom QLever engine
        try (QLeverEngine qleverEngine = QLeverEngine.create();
             BridgeRouter router = BridgeRouter.withQleverEngine(qleverEngine, false)) {

            System.out.println("Router with custom QLever engine:");
            System.out.println("Supported patterns: " + router.getExecutorStatus());

            NativeCall call = NativeCall.of(
                "http://example.org/query/1",
                "http://example.org/ASK",
                "ASK WHERE { ?s a ?type }",
                CallPattern.JVM
            );

            // Execute the call
            RoutingResult result = router.route(call);

            if (result.isSuccess()) {
                System.out.println("Query result: " + result.getResult());
            } else {
                System.out.println("Query failed: " + result.getErrorMessage());
            }
        }
    }

    /**
     * Demonstrates error handling patterns.
     */
    public void demonstrateErrorHandling() {
        try (BridgeRouter router = new BridgeRouter(false)) {
            System.out.println("\n=== Error Handling Demo ===");

            // Call with invalid URI
            NativeCall invalidCall = NativeCall.of(
                "invalid",
                "p1",
                "o1",
 CallPattern.JVM
            );

            try {
                router.route(invalidCall);
            } catch (IllegalArgumentException e) {
                System.out.println("Caught validation error: " + e.getMessage());
            }

            // Call that would fail at execution
            NativeCall failingCall = NativeCall.of(
                "http://example.org/invalid/query",
                "http://example.org/INVALID",
                "SELECT * FROM non_existent",
                CallPattern.JVM
            );

            RoutingResult result = router.route(failingCall);
            if (result.isFailure()) {
                System.out.println("Execution failed as expected:");
                System.out.println("  Error: " + result.getErrorMessage());
                System.out.println("  Call: " + result.getCall().toNtriple());
                System.out.println("  Timestamp: " + result.getTimestamp());
                System.out.println("  Execution time: " + result.getExecutionTime().toMillis() + "ms");
            }
        }
    }

    /**
     * Demonstrates concurrent routing.
     */
    public void demonstrateConcurrentRouting() {
        try (BridgeRouter router = new BridgeRouter(false)) {
            System.out.println("\n=== Concurrent Routing Demo ===");

            List<NativeCall> calls = List.of(
                NativeCall.of("s1", "p1", "o1", CallPattern.JVM),
                NativeCall.of("s2", "p2", "o2", CallPattern.BEAM),
                NativeCall.of("s3", "p3", "o3", CallPattern.JVM),
                NativeCall.of("s4", "p4", "o4", CallPattern.BEAM)
            );

            // Process calls concurrently
            calls.parallelStream().forEach(call -> {
                try {
                    RoutingResult result = router.route(call);
                    System.out.println("Completed " + call.toNtriple() +
                                     " in " + result.getExecutionTime().toMillis() + "ms - " +
                                     (result.isSuccess() ? "SUCCESS" : "FAILED"));
                } catch (Exception e) {
                    System.err.println("Failed to process " + call.toNtriple() + ": " + e.getMessage());
                }
            });
        }
    }

    /**
     * Demonstrates result mapping patterns.
     */
    public void demonstrateResultMapping() {
        try (BridgeRouter router = new BridgeRouter(true)) {
            System.out.println("\n=== Result Mapping Demo ===");

            NativeCall call = NativeCall.of(
                "http://example.org/data/1",
                "http://example.org/SELECT",
                "SELECT ?name WHERE { ?s ?p ?name }",
                CallPattern.JVM
            );

            RoutingResult result = router.route(call);

            // Map results to different types
            RoutingResult stringResult = result.map(Object::toString);
            RoutingResult sizeResult = result.map(r -> ((Map<?, ?>) r).size());

            System.out.println("Original result: " + result.getResult());
            System.out.println("As string: " + stringResult.getResult());
            System.out.println("Map size: " + sizeResult.getResult());
        }
    }

    /**
     * Main demonstration runner.
     */
    public static void main(String[] args) {
        BridgeRouterDemo demo = new BridgeRouterDemo();

        try {
            demo.demonstrateBasicRouting();
            demo.demonstrateCaching();
            demo.demonstrateCustomEngines();
            demo.demonstrateErrorHandling();
            demo.demonstrateConcurrentRouting();
            demo.demonstrateResultMapping();

            System.out.println("\n=== Demo Complete ===");
        } catch (Exception e) {
            System.err.println("Demo failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Helper method for assertions
    private static void assertEquals(Object expected, Object actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected: " + expected + ", but was: " + actual);
        }
    }
}