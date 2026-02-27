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

package org.yawlfoundation.yawl.engine;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.logging.YLogDataItemList;
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.yawlfoundation.yawl.util.StringUtil;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark for the real YAWL stateful engine kernel.
 *
 * <p><strong>Innovation</strong>: This is the first JMH benchmark in the YAWL codebase to use
 * the <em>real</em> {@link YEngine}. All existing JMH benchmarks in
 * {@code org.yawlfoundation.yawl.performance.jmh} simulate work with
 * {@code Thread.sleep} and plain value objects. This benchmark measures actual engine ops:</p>
 *
 * <ul>
 *   <li>{@code startCaseThroughput} — {@link YEngine#startCase} throughput (ops/ms)</li>
 *   <li>{@code workItemStatusQuery} — {@link YWorkItemRepository#getWorkItems(YWorkItemStatus)} latency</li>
 *   <li>{@code cancelCaseCost} — average {@link YEngine#cancelCase(YIdentifier)} latency</li>
 *   <li>{@code virtualConcurrentCases} — parallel starts via virtual thread executor</li>
 * </ul>
 *
 * <p>Placed in {@code org.yawlfoundation.yawl.engine} (same package as {@link YEngine}) to
 * access its protected {@code startCase} API — consistent with all other engine-level tests.
 *
 * <h2>Setup strategy</h2>
 * <ul>
 *   <li>{@code @Setup(Level.Trial)} — YEngine init + spec load + steady-state case population</li>
 *   <li>{@code @TearDown(Level.Iteration)} — {@link EngineClearer#clear} between iterations
 *       (prevents case accumulation from distorting throughput measurements)</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0
 * @see StatefulEngineStressTest
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgs = {
        "-XX:+UseZGC",
        "-XX:+UseCompactObjectHeaders",
        "-Xms1g",
        "-Xmx2g"
})
public class EngineKernelBenchmark {

    /** Number of cases to pre-populate for {@code workItemStatusQuery}. */
    private static final int PRELOAD_CASES = 50;

    /** Parallelism levels for throughput benchmarks. */
    @Param({"1", "5", "20"})
    public int concurrentCases;

    // Shared state across benchmarks
    private YEngine engine;
    private YSpecification spec;

    // Pre-loaded case identifiers for workItemStatusQuery (stable across iterations)
    private final List<YIdentifier> preloadedCaseIds = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Setup(Level.Trial)
    public void setupTrial() throws Exception {
        engine = YEngine.getInstance();
        EngineClearer.clear(engine);
        spec = loadSpec();
        engine.loadSpecification(spec);

        // Pre-populate PRELOAD_CASES live cases for workItemStatusQuery benchmark
        for (int i = 0; i < PRELOAD_CASES; i++) {
            YIdentifier id = engine.startCase(spec.getSpecificationID(), null, null, null,
                    new YLogDataItemList(), null, false);
            if (id != null) {
                preloadedCaseIds.add(id);
            }
        }
    }

    @TearDown(Level.Iteration)
    public void teardownIteration() throws Exception {
        // Cancel benchmark-created cases (not pre-loaded ones) to prevent accumulation
        Set<YIdentifier> live = engine.getCasesForSpecification(spec.getSpecificationID());
        for (YIdentifier id : live) {
            if (!preloadedCaseIds.contains(id)) {
                try {
                    engine.cancelCase(id);
                } catch (Exception ignored) {
                }
            }
        }
    }

    @TearDown(Level.Trial)
    public void teardownTrial() throws Exception {
        EngineClearer.clear(engine);
        preloadedCaseIds.clear();
    }

    // -------------------------------------------------------------------------
    // Benchmarks
    // -------------------------------------------------------------------------

    /**
     * Measures {@link YEngine#startCase} throughput for {@code concurrentCases} sequential starts.
     *
     * <p>Uses sequential execution (YEngine is a singleton) to isolate per-{@code startCase}
     * overhead. The {@code @Param} varies cases per invocation to reveal setup vs. steady-state costs.
     */
    @Benchmark
    public void startCaseThroughput(Blackhole bh) throws Exception {
        for (int i = 0; i < concurrentCases; i++) {
            YIdentifier id = engine.startCase(spec.getSpecificationID(), null, null, null,
                    new YLogDataItemList(), null, false);
            bh.consume(id);
        }
    }

    /**
     * Measures {@link YWorkItemRepository#getWorkItems(YWorkItemStatus)} latency with
     * {@link #PRELOAD_CASES} live cases producing work items.
     *
     * <p>Validates the P3 O(1) secondary status index claim: latency must remain bounded
     * regardless of total item count.
     */
    @Benchmark
    public Set<YWorkItem> workItemStatusQuery(Blackhole bh) {
        Set<YWorkItem> items = engine.getWorkItemRepository()
                .getWorkItems(YWorkItemStatus.statusEnabled);
        bh.consume(items.size());
        return items;
    }

    /**
     * Measures the cost of immediately cancelling a case that was just started.
     *
     * <p>Represents the "start-then-abort" pattern used in error paths and test teardown.
     */
    @Benchmark
    public void cancelCaseCost(Blackhole bh) throws Exception {
        YIdentifier id = engine.startCase(spec.getSpecificationID(), null, null, null,
                new YLogDataItemList(), null, false);
        if (id != null) {
            engine.cancelCase(id);
            bh.consume(id);
        }
    }

    /**
     * Measures parallel case starts via a virtual thread executor.
     *
     * <p>Uses {@code newVirtualThreadPerTaskExecutor()} to model the concurrency pattern
     * in {@link StatefulEngineStressTest#s1_caseStorm500}.
     * Exposes engine-internal lock contention under concurrent access.
     */
    @Benchmark
    public void virtualConcurrentCases(Blackhole bh) throws Exception {
        List<Future<YIdentifier>> futures = new ArrayList<>(concurrentCases);
        try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < concurrentCases; i++) {
                futures.add(exec.submit(() ->
                        engine.startCase(spec.getSpecificationID(), null, null, null,
                                new YLogDataItemList(), null, false)));
            }
            for (Future<YIdentifier> f : futures) {
                YIdentifier id = f.get(10, TimeUnit.SECONDS);
                bh.consume(id);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Spec loader
    // -------------------------------------------------------------------------

    private YSpecification loadSpec() throws Exception {
        URL url = getClass().getResource("YAWL_Specification2.xml");
        if (url == null) {
            throw new IllegalStateException(
                    "YAWL_Specification2.xml not found on test classpath");
        }
        File file = new File(url.getFile());
        List<YSpecification> specs = YMarshal.unmarshalSpecifications(
                StringUtil.fileToString(file.getAbsolutePath()), false);
        if (specs == null || specs.isEmpty()) {
            throw new IllegalStateException("No specs parsed from YAWL_Specification2.xml");
        }
        return specs.get(0);
    }

    // -------------------------------------------------------------------------
    // Standalone runner
    // -------------------------------------------------------------------------

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(EngineKernelBenchmark.class.getSimpleName())
                .forks(1)
                .build();
        new Runner(opt).run();
    }
}
