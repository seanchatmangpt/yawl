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

package org.yawlfoundation.yawl.integration.a2a.skills;

import org.openjdk.jmh.annotations.*;
import org.yawlfoundation.yawl.ggen.mining.generators.YawlExportException;
import org.yawlfoundation.yawl.ggen.mining.generators.YawlSpecExporter;
import org.yawlfoundation.yawl.ggen.mining.model.PetriNet;
import org.yawlfoundation.yawl.ggen.polyglot.PowlPythonBridge;
import org.yawlfoundation.yawl.ggen.powl.PowlModel;
import org.yawlfoundation.yawl.ggen.powl.PowlToYawlConverter;
import org.yawlfoundation.yawl.ggen.rl.PowlParseException;
import org.yawlfoundation.yawl.graalpy.PythonException;
import org.yawlfoundation.yawl.integration.synthesis.PatternBasedSynthesizer;
import org.yawlfoundation.yawl.integration.synthesis.SynthesisResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmarks for GraalPy synthesis performance.
 *
 * <p>Measures performance of:
 * <ul>
 *   <li>GraalPy bridge initialization</li>
 *   <li>Workflow synthesis from description</li>
 *   <li>Mining from XES event logs</li>
 *   <li>Fallback vs primary path comparison</li>
 *   <li>Memory usage during Python execution</li>
 * </ul>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class GraalPySynthesisBenchmark {

    // Test data - small process descriptions
    private static final String SMALL_DESCRIPTION = """
        A simple order process with 3 tasks:
        1. Receive Order
        2. Process Payment
        3. Ship Product
        """;

    private static final String MEDIUM_DESCRIPTION = """
        An order fulfillment process with 8 tasks:
        1. Receive Order
        2. Check Inventory
        3. Process Payment
        4. Authorize Shipment
        5. Pack Items
        6. Generate Shipping Label
        7. Ship Product
        8. Send Confirmation
        """;

    private static final String LARGE_DESCRIPTION = """
        A complex supply chain process with 15 tasks:
        1. Receive Purchase Order
        2. Validate Order Details
        3. Check Inventory Levels
        4. Reserve Stock
        5. Process Payment
        6. Payment Verification
        7. Generate Pick List
        8. Pick Items from Warehouse
        9. Quality Check
        10. Pack Items
        11. Generate Shipping Documents
        12. Schedule Delivery
        13. Handover to Carrier
        14. Track Shipment
        15. Confirm Delivery
        """;

    private static final String SAMPLE_XES = """
        <?xml version="1.0" encoding="UTF-8"?>
        <log xes:version="1.0" xmlns:xes="http://www.xes-standard.org/">
          <trace>
            <string key="concept:name" value="case1"/>
            <event>
              <string key="concept:name" value="Start"/>
              <date key="time:timestamp" value="2024-01-01T10:00:00"/>
            </event>
            <event>
              <string key="concept:name" value="Task_A"/>
              <date key="time:timestamp" value="2024-01-01T10:05:00"/>
            </event>
            <event>
              <string key="concept:name" value="Task_B"/>
              <date key="time:timestamp" value="2024-01-01T10:10:00"/>
            </event>
            <event>
              <string key="concept:name" value="End"/>
              <date key="time:timestamp" value="2024-01-01T10:15:00"/>
            </event>
          </trace>
        </log>
        """;

    // Fallback pattern specs for testing
    private static final PatternBasedSynthesizer.PatternSpec SMALL_PATTERN =
        new PatternBasedSynthesizer.PatternSpec.Sequential(
            List.of("ReceiveOrder", "ProcessPayment", "ShipProduct")
        );

    private static final PatternBasedSynthesizer.PatternSpec MEDIUM_PATTERN =
        new PatternBasedSynthesizer.PatternSpec.Sequential(
            List.of("ReceiveOrder", "CheckInventory", "ProcessPayment",
                    "AuthorizeShipment", "PackItems", "GenerateLabel",
                    "ShipProduct", "SendConfirmation")
        );

    private PowlPythonBridge bridge;
    private boolean graalPyAvailable;

    @Setup(Level.Trial)
    public void setup() {
        try {
            bridge = new PowlPythonBridge();
            graalPyAvailable = true;
        } catch (Throwable e) {
            graalPyAvailable = false;
            System.out.println("GraalPy not available, some benchmarks will be skipped: " + e.getMessage());
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (bridge != null) {
            try {
                bridge.close();
            } catch (Exception e) {
                // Ignore cleanup errors
            }
        }
    }

    /**
     * Benchmark: GraalPy bridge initialization time
     */
    @Benchmark
    public long benchmarkBridgeInitialization() throws Exception {
        if (!graalPyAvailable) {
            return -1; // Skip if GraalPy not available
        }
        long start = System.nanoTime();
        try (PowlPythonBridge localBridge = new PowlPythonBridge()) {
            // Just measure creation time
        }
        return System.nanoTime() - start;
    }

    /**
     * Benchmark: Synthesis from small description
     */
    @Benchmark
    public Map<String, Object> benchmarkSynthesizeSmallDescription() throws Exception {
        return synthesizeFromDescription(SMALL_DESCRIPTION);
    }

    /**
     * Benchmark: Synthesis from medium description
     */
    @Benchmark
    public Map<String, Object> benchmarkSynthesizeMediumDescription() throws Exception {
        return synthesizeFromDescription(MEDIUM_DESCRIPTION);
    }

    /**
     * Benchmark: Synthesis from large description
     */
    @Benchmark
    public Map<String, Object> benchmarkSynthesizeLargeDescription() throws Exception {
        return synthesizeFromDescription(LARGE_DESCRIPTION);
    }

    /**
     * Benchmark: Mining from XES log
     */
    @Benchmark
    public Map<String, Object> benchmarkMineFromXes() throws Exception {
        if (!graalPyAvailable || bridge == null) {
            return Map.of("error", "GraalPy not available");
        }

        long start = System.currentTimeMillis();
        Map<String, Object> result = new java.util.HashMap<>();

        try {
            PowlModel model = bridge.mineFromLog(SAMPLE_XES);
            if (model != null) {
                result.put("mined", true);
                result.put("model_type", model.getClass().getSimpleName());
            }
        } catch (PythonException e) {
            result.put("error", e.getMessage());
        }

        result.put("elapsed_ms", System.currentTimeMillis() - start);
        return result;
    }

    /**
     * Benchmark: Fallback vs primary path comparison
     */
    @Benchmark
    public Map<String, Object> benchmarkFallbackVsPrimary() throws Exception {
        Map<String, Object> result = new java.util.HashMap<>();

        // Measure primary path (GraalPy)
        long primaryStart = System.currentTimeMillis();
        Map<String, Object> primaryResult = synthesizeFromDescription(SMALL_DESCRIPTION);
        long primaryElapsed = System.currentTimeMillis() - primaryStart;

        // Measure fallback path (PatternBasedSynthesizer)
        long fallbackStart = System.currentTimeMillis();
        Map<String, Object> fallbackResult = synthesizeFallback(SMALL_PATTERN);
        long fallbackElapsed = System.currentTimeMillis() - fallbackStart;

        result.put("primary_ms", primaryElapsed);
        result.put("fallback_ms", fallbackElapsed);
        result.put("speedup", (double) fallbackElapsed / Math.max(primaryElapsed, 1));

        return result;
    }

    /**
     * Benchmark: Memory usage during Python execution
     */
    @Benchmark
    public Map<String, Object> benchmarkMemoryUsage() throws Exception {
        Runtime runtime = Runtime.getRuntime();

        long beforeMem = runtime.totalMemory() - runtime.freeMemory();

        Map<String, Object> result = synthesizeFromDescription(MEDIUM_DESCRIPTION);

        long afterMem = runtime.totalMemory() - runtime.freeMemory();

        result.put("memory_before_bytes", beforeMem);
        result.put("memory_after_bytes", afterMem);
        result.put("memory_delta_bytes", afterMem - beforeMem);

        return result;
    }

    /**
     * Benchmark: Error handling performance
     */
    @Benchmark
    public Map<String, Object> benchmarkErrorHandling() {
        long start = System.currentTimeMillis();
        Map<String, Object> result = new java.util.HashMap<>();

        try {
            // Intentionally invalid input - empty description
            synthesizeFromDescription("");
        } catch (Exception e) {
            result.put("error_handled", true);
            result.put("error_type", e.getClass().getSimpleName());
        }

        result.put("elapsed_ms", System.currentTimeMillis() - start);
        return result;
    }

    // Helper methods

    private Map<String, Object> synthesizeFromDescription(String description) throws Exception {
        Map<String, Object> result = new java.util.HashMap<>();
        long start = System.currentTimeMillis();

        if (!graalPyAvailable || bridge == null) {
            result.put("error", "GraalPy not available");
            result.put("elapsed_ms", 0L);
            return result;
        }

        try {
            PowlModel model = bridge.generate(description);
            if (model != null) {
                PowlToYawlConverter converter = new PowlToYawlConverter();
                PetriNet petriNet = converter.convert(model);
                if (petriNet != null) {
                    YawlSpecExporter exporter = new YawlSpecExporter();
                    String yawlXml = exporter.export(petriNet);
                    result.put("success", true);
                    result.put("xml_length", yawlXml != null ? yawlXml.length() : 0);
                }
            }
        } catch (PythonException e) {
            result.put("error", "PythonException: " + e.getMessage());
        } catch (PowlParseException e) {
            result.put("error", "PowlParseException: " + e.getMessage());
        } catch (YawlExportException e) {
            result.put("error", "YawlExportException: " + e.getMessage());
        }

        result.put("elapsed_ms", System.currentTimeMillis() - start);
        return result;
    }

    private Map<String, Object> synthesizeFallback(PatternBasedSynthesizer.PatternSpec pattern) {
        Map<String, Object> result = new java.util.HashMap<>();
        long start = System.currentTimeMillis();

        try {
            PatternBasedSynthesizer synthesizer = new PatternBasedSynthesizer();
            SynthesisResult synthResult = synthesizer.synthesize(pattern);
            if (synthResult != null && synthResult.successful()) {
                result.put("success", true);
                result.put("xml_length", synthResult.specXml() != null ? synthResult.specXml().length() : 0);
            }
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }

        result.put("elapsed_ms", System.currentTimeMillis() - start);
        return result;
    }
}
