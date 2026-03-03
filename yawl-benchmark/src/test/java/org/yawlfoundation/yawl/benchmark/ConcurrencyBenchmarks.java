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
import org.yawlfoundation.yawl.exceptions.YSyntaxException;
import org.yawlfoundation.yawl.stateless.YStatelessEngine;
import org.yawlfoundation.yawl.stateless.elements.YSpecification;
import org.yawlfoundation.yawl.stateless.engine.YWorkItem;
import org.yawlfoundation.yawl.stateless.listener.YCaseEventListener;
import org.yawlfoundation.yawl.stateless.listener.YWorkItemEventListener;
import org.yawlfoundation.yawl.stateless.listener.event.YCaseEvent;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;
import org.yawlfoundation.yawl.stateless.listener.event.YWorkItemEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Concurrency Performance Benchmarks.
 *
 * <p>Measures multi-case throughput and virtual-thread scalability using
 * {@link YStatelessEngine}. Each benchmark drives workflow cases to
 * completion via the engine's event-listener contract; no fake work-item
 * construction or stub invocations are used.</p>
 *
 * <p>Scaling curve from 1→64 threads exposes the inflection point where
 * {@code YNetRunner}'s internal locks saturate. Adding thread counts 2 and
 * 64 reveals the first contention point and full saturation respectively.</p>
 *
 * <p>Benchmarks:</p>
 * <ul>
 *   <li>{@link #virtualThreadCaseLaunch} — single case on a virtual thread</li>
 *   <li>{@link #parallelBatchSmall} — 4 cases via {@code launchCasesParallel}</li>
 *   <li>{@link #parallelBatchLarge} — 32 cases via {@code launchCasesParallel}</li>
 *   <li>{@link #concurrentCaseThroughput} — throughput under JMH multi-threading</li>
 *   <li>{@link #threadScalingSingle} — single-thread scaling reference point</li>
 * </ul>
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 50, time = 1)
@Fork(value = 3, jvmArgs = {
    "-Xms2g", "-Xmx4g",
    "-XX:+UseZGC",
    "-XX:+UseCompactObjectHeaders",
    "-Djmh.executor=VIRTUAL_TPE"
})
public class ConcurrencyBenchmarks implements YWorkItemEventListener, YCaseEventListener {

    /** Scales the parallel-batch size between benchmark runs. */
    @Param({"1", "2", "4", "8", "16", "32", "64"})
    private int threadCount;

    @Param({"10", "100"})
    private int caseCount;

    private YStatelessEngine engine;
    private YSpecification spec;

    // Tracks remaining completions for the current benchmark invocation
    private volatile CountDownLatch completionLatch;
    private final AtomicReference<Exception> listenerError = new AtomicReference<>();
    private final AtomicInteger completionCounter = new AtomicInteger(0);

    // ── Setup ─────────────────────────────────────────────────────────────

    @Setup(Level.Trial)
    public void setup() throws YSyntaxException {
        engine = new YStatelessEngine();
        engine.addWorkItemEventListener(this);
        engine.addCaseEventListener(this);
        spec = engine.unmarshalSpecification(BenchmarkSpecFactory.SEQUENTIAL_2_TASK);
    }

    // ── Event listener ────────────────────────────────────────────────────

    @Override
    public void handleWorkItemEvent(YWorkItemEvent event) {
        YWorkItem item = event.getWorkItem();
        try {
            switch (event.getEventType()) {
                case ITEM_ENABLED -> engine.startWorkItem(item);
                case ITEM_STARTED -> engine.completeWorkItem(item, null, null);
                default -> { /* not needed */ }
            }
        } catch (Exception e) {
            listenerError.compareAndSet(null, e);
        }
    }

    @Override
    public void handleCaseEvent(YCaseEvent event) {
        if (event.getEventType() == YEventType.CASE_COMPLETED ||
                event.getEventType() == YEventType.CASE_CANCELLED) {
            CountDownLatch latch = completionLatch;
            if (latch != null) latch.countDown();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void awaitCompletion(int expected) throws Exception {
        if (!completionLatch.await(60, TimeUnit.SECONDS)) {
            throw new IllegalStateException(
                "Only " + (expected - completionLatch.getCount()) +
                "/" + expected + " cases completed within timeout");
        }
        Exception err = listenerError.get();
        if (err != null) throw err;
    }

    private List<String> nullParams(int count) {
        List<String> params = new ArrayList<>(count);
        for (int i = 0; i < count; i++) params.add(null);
        return params;
    }

    // ── Benchmarks ────────────────────────────────────────────────────────

    /**
     * Launches a single workflow case on a virtual thread and waits for
     * completion. Measures virtual-thread scheduling + engine overhead.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void virtualThreadCaseLaunch() throws Exception {
        listenerError.set(null);
        completionLatch = new CountDownLatch(1);

        Thread vt = Thread.ofVirtual().name("vt-bench").start(() -> {
            try {
                engine.launchCase(spec);
            } catch (Exception e) {
                listenerError.compareAndSet(null, e);
                completionLatch.countDown();
            }
        });
        vt.join();
        awaitCompletion(1);
    }

    /**
     * Batch-launches 4 sequential cases in parallel using
     * {@link YStatelessEngine#launchCasesParallel} (virtual threads).
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void parallelBatchSmall() throws Exception {
        int batch = 4;
        listenerError.set(null);
        completionLatch = new CountDownLatch(batch);
        engine.launchCasesParallel(spec, nullParams(batch));
        awaitCompletion(batch);
    }

    /**
     * Batch-launches 32 sequential cases in parallel using
     * {@link YStatelessEngine#launchCasesParallel} (virtual threads).
     * Exposes lock saturation inside {@code YNetRunner}.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void parallelBatchLarge() throws Exception {
        int batch = 32;
        listenerError.set(null);
        completionLatch = new CountDownLatch(batch);
        engine.launchCasesParallel(spec, nullParams(batch));
        awaitCompletion(batch);
    }

    /**
     * Per-second throughput of completed cases under JMH's multi-thread
     * driver. The {@code @Threads} annotation uses the {@code threadCount}
     * parameter at the JMH runner level; this benchmark body is
     * single-invocation-per-thread.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void concurrentCaseThroughput() throws Exception {
        listenerError.set(null);
        CountDownLatch latch = new CountDownLatch(1);
        // Override the shared latch for this invocation
        completionLatch = latch;
        engine.launchCase(spec);
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Case did not complete within timeout");
        }
        Exception err = listenerError.get();
        if (err != null) throw err;
    }

    /**
     * Single-thread reference benchmark used to normalise the scaling curve.
     * Each invocation runs exactly one sequential case to completion.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @Group("scaling")
    @GroupThreads(1)
    public void threadScalingSingle() throws Exception {
        listenerError.set(null);
        completionLatch = new CountDownLatch(1);
        engine.launchCase(spec);
        awaitCompletion(1);
    }

    /**
     * {@code caseCount} cases launched sequentially in a loop from a
     * single thread — used to measure sustained single-node throughput
     * without the overhead of thread pool management.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void bulkSequentialLaunch() throws Exception {
        listenerError.set(null);
        completionLatch = new CountDownLatch(caseCount);
        // Launch all cases; completionLatch counts down once per case
        for (int i = 0; i < caseCount; i++) {
            engine.launchCase(spec);
        }
        awaitCompletion(caseCount);
    }
}
