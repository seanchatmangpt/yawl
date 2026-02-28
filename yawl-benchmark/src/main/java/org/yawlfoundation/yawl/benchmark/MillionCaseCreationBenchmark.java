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
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.yawlfoundation.yawl.stateless.YStatelessEngine;
import org.yawlfoundation.yawl.stateless.elements.YSpecification;
import org.yawlfoundation.yawl.stateless.engine.YNetRunner;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * JMH Microbenchmark: Case Creation Latency at Scale.
 *
 * <p>Measures per-case creation latency as the engine's case registry grows from
 * 100K to 1M cases. This benchmark answers the critical question:
 * "What's case creation throughput (ops/sec) when the engine is at high capacity?"</p>
 *
 * <p>Test design:
 * <ul>
 *   <li>Pre-populate engine with N cases during setup phase</li>
 *   <li>Measure time to create one new case (nanoseconds)</li>
 *   <li>Parametrize over case counts: 100K, 250K, 500K, 750K, 1M</li>
 *   <li>Detect performance cliff or hash contention</li>
 * </ul>
 * </p>
 *
 * <p>JMH Configuration:
 * <ul>
 *   <li>Mode: AverageTime (nanoseconds per operation)</li>
 *   <li>Fork: 3 separate JVMs with 8GB heap and ZGC</li>
 *   <li>Warmup: 10 iterations of 1 second each</li>
 *   <li>Measurement: 50 iterations of 1 second each</li>
 *   <li>Scope: Benchmark (one instance per trial)</li>
 * </ul>
 * </p>
 *
 * <p>Output:
 * <ul>
 *   <li>CSV results: case-creation-benchmark.csv</li>
 *   <li>JSON results: case-creation-benchmark.json</li>
 *   <li>Shows latency degradation curve as case count increases</li>
 * </ul>
 * </p>
 *
 * <p>Execution:
 * <pre>
 *   mvn clean verify -pl yawl-benchmark
 *   java -jar yawl-benchmark/target/benchmarks.jar MillionCaseCreationBenchmark -f 3 -wi 10 -i 50
 * </pre>
 * </p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Fork(value = 3, jvmArgs = {
        "-Xms8g",
        "-Xmx8g",
        "-XX:+UseZGC",
        "-XX:+UseCompactObjectHeaders",
        "-XX:+DisableExplicitGC",
        "-XX:+AlwaysPreTouch"
})
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 50, time = 1, timeUnit = TimeUnit.SECONDS)
public class MillionCaseCreationBenchmark {

    /**
     * Case count parameter: 100K, 250K, 500K, 750K, 1M.
     * Each fork will test with one of these values.
     */
    @Param({"100000", "250000", "500000", "750000", "1000000"})
    public int caseCount;

    private YStatelessEngine engine;
    private YSpecification specification;
    private List<String> caseIds;

    /**
     * Setup phase: Initialize engine and pre-populate with N cases.
     *
     * <p>This runs once per trial (before measurement begins).
     * Pre-population simulates the state space where we're measuring
     * case creation latency.</p>
     */
    @Setup(Level.Trial)
    public void setupTrial() throws Exception {
        engine = new YStatelessEngine();

        // Load a simple sequential specification
        String sequentialSpecXml = BenchmarkSpecFactory.SEQUENTIAL_2_TASK;
        specification = engine.unmarshalSpecification(sequentialSpecXml);

        // Pre-populate engine with caseCount cases
        caseIds = new ArrayList<>(caseCount);
        System.out.println("Pre-populating engine with " + caseCount + " cases...");
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < caseCount; i++) {
            String caseId = "bench-case-" + i + "-" + UUID.randomUUID();
            caseIds.add(caseId);
            try {
                engine.launchCase(specification, caseId, "<data/>");
            } catch (Exception e) {
                System.err.println("Failed to pre-populate case " + i + ": " + e.getMessage());
            }
        }

        long elapsedMs = System.currentTimeMillis() - startTime;
        System.out.println("Pre-population complete: " + caseCount + " cases in " + elapsedMs + "ms");
        System.out.println("Throughput during setup: " + (caseCount * 1000.0 / elapsedMs) + " cases/sec");
    }

    /**
     * Cleanup phase: Release resources after trial.
     */
    @TearDown(Level.Trial)
    public void tearDownTrial() {
        if (engine != null) {
            try {
                engine.close();
            } catch (Exception e) {
                System.err.println("Error closing engine: " + e.getMessage());
            }
        }
        caseIds.clear();
    }

    /**
     * Benchmark: Measure latency to create a single case when registry has caseCount cases.
     *
     * <p>Each invocation creates one new case and measures the time.
     * The Blackhole ensures the result isn't optimized away.</p>
     *
     * @param blackhole JMH Blackhole to consume result
     * @throws Exception if case creation fails
     */
    @Benchmark
    public void createCase(Blackhole blackhole) throws Exception {
        String newCaseId = "bench-new-case-" + UUID.randomUUID();
        YNetRunner runner = engine.launchCase(specification, newCaseId, "<data/>");
        blackhole.consume(runner);
    }
}
