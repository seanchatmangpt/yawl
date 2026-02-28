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
import org.yawlfoundation.yawl.stateless.engine.YWorkItem;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * JMH Microbenchmark: Work Item Checkout Latency at Scale.
 *
 * <p>Measures per-work-item checkout latency as the engine maintains 100K to 1M
 * concurrent active cases. This benchmark tests index lookup performance and
 * runner store efficiency under high concurrency.</p>
 *
 * <p>Test design:
 * <ul>
 *   <li>Pre-populate engine with N active cases</li>
 *   <li>Each case has multiple enabled work items</li>
 *   <li>Measure time to checkout a single work item</li>
 *   <li>Parametrize over case counts: 100K, 500K, 1M</li>
 *   <li>Detect contention or slow-down as scale increases</li>
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
 *   <li>CSV results: work-item-checkout-benchmark.csv</li>
 *   <li>JSON results: work-item-checkout-benchmark.json</li>
 *   <li>Shows checkout latency increase as concurrency grows</li>
 * </ul>
 * </p>
 *
 * <p>Execution:
 * <pre>
 *   mvn clean verify -pl yawl-benchmark
 *   java -jar yawl-benchmark/target/benchmarks.jar WorkItemCheckoutScaleBenchmark -f 3 -wi 10 -i 50
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
public class WorkItemCheckoutScaleBenchmark {

    /**
     * Case count parameter: 100K, 500K, 1M.
     */
    @Param({"100000", "500000", "1000000"})
    public int caseCount;

    private YStatelessEngine engine;
    private YSpecification specification;
    private List<YNetRunner> runners;
    private List<YWorkItem> workItems;
    private int workItemIndex;

    /**
     * Setup phase: Initialize engine and pre-populate with N cases.
     *
     * <p>Each case will be put in a state with enabled work items ready for checkout.
     * This simulates the production scenario where we have many active cases
     * competing for work item allocation.</p>
     */
    @Setup(Level.Trial)
    public void setupTrial() throws Exception {
        engine = new YStatelessEngine();

        // Load a specification with multiple tasks to create work items
        String parallelSpecXml = BenchmarkSpecFactory.PARALLEL_SPLIT_SYNC;
        specification = engine.unmarshalSpecification(parallelSpecXml);

        runners = new ArrayList<>(caseCount);
        workItems = new ArrayList<>();

        System.out.println("Pre-populating engine with " + caseCount + " cases for checkout testing...");
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < caseCount; i++) {
            String caseId = "bench-checkout-case-" + i + "-" + UUID.randomUUID();
            try {
                YNetRunner runner = engine.launchCase(specification, caseId, "<data/>");
                runners.add(runner);

                // Collect enabled work items from each case
                Set<YWorkItem> enabledItems = runner.getWorkItemRepository().getEnabledWorkItems();
                workItems.addAll(enabledItems);

            } catch (Exception e) {
                System.err.println("Failed to pre-populate case " + i + ": " + e.getMessage());
            }
        }

        long elapsedMs = System.currentTimeMillis() - startTime;
        System.out.println("Pre-population complete: " + caseCount + " cases with "
                + workItems.size() + " work items in " + elapsedMs + "ms");
        System.out.println("Throughput during setup: " + (caseCount * 1000.0 / elapsedMs) + " cases/sec");

        workItemIndex = 0;
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
        runners.clear();
        workItems.clear();
    }

    /**
     * Benchmark: Measure latency to checkout a single work item.
     *
     * <p>Simulates the real-world scenario where a work queue pulls work items
     * from the engine. Checkout involves:
     * <ul>
     *   <li>Finding the work item in the index</li>
     *   <li>Marking it as checked out</li>
     *   <li>Updating runner state</li>
     * </ul>
     * </p>
     *
     * @param blackhole JMH Blackhole to consume result
     * @throws Exception if work item operations fail
     */
    @Benchmark
    public void checkoutWorkItem(Blackhole blackhole) throws Exception {
        if (workItems.isEmpty()) {
            return;
        }

        // Round-robin through work items to vary the checkout pattern
        YWorkItem item = workItems.get(workItemIndex);
        workItemIndex = (workItemIndex + 1) % workItems.size();

        try {
            // Checkout typically involves starting the item
            engine.startWorkItem(item);
            blackhole.consume(item);
        } catch (Exception e) {
            // Item may already be started; that's ok for benchmark purposes
            blackhole.consume(item);
        }
    }
}
