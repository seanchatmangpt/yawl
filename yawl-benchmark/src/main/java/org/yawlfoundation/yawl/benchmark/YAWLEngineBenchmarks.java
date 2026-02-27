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
import org.yawlfoundation.yawl.exceptions.*;
import org.yawlfoundation.yawl.stateless.YStatelessEngine;
import org.yawlfoundation.yawl.stateless.elements.YSpecification;
import org.yawlfoundation.yawl.stateless.engine.YNetRunner;
import org.yawlfoundation.yawl.stateless.engine.YWorkItem;
import org.yawlfoundation.yawl.stateless.listener.YCaseEventListener;
import org.yawlfoundation.yawl.stateless.listener.YWorkItemEventListener;
import org.yawlfoundation.yawl.stateless.listener.event.YCaseEvent;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;
import org.yawlfoundation.yawl.stateless.listener.event.YWorkItemEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Core YAWL Engine Performance Benchmarks.
 *
 * <p>All benchmarks use {@link YStatelessEngine} — the modern, cloud-native
 * engine that requires no Hibernate persistence layer. Workflow execution is
 * event-driven: a shared {@link YWorkItemEventListener} auto-starts every
 * enabled item and auto-completes every started item, driving each case to
 * completion without external intervention.</p>
 *
 * <p>Measurements:</p>
 * <ul>
 *   <li>{@link #specUnmarshalLatency} — XML → YSpecification parse time</li>
 *   <li>{@link #sequentialCaseLaunch} — full 2-task sequential case latency</li>
 *   <li>{@link #seq4TaskCaseLaunch} — full 4-task sequential case latency</li>
 *   <li>{@link #caseLaunchThroughput} — cases launched per second</li>
 *   <li>{@link #caseRestoreLatency} — marshal + unmarshal round-trip latency</li>
 *   <li>{@link #parallelBatchLaunch} — batch parallel launch via virtual threads</li>
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
@Threads(1)
public class YAWLEngineBenchmarks implements YWorkItemEventListener, YCaseEventListener {

    // Shared engine — one per benchmark thread (Scope.Thread)
    private YStatelessEngine engine;

    // Parsed specs — reused across invocations to isolate engine overhead
    private YSpecification seq2Spec;
    private YSpecification seq4Spec;

    // Latch signalling case completion for the current invocation
    private volatile CountDownLatch completionLatch;

    // Capture any exception thrown inside the listener
    private final AtomicReference<Exception> listenerError = new AtomicReference<>();

    // ── Setup ─────────────────────────────────────────────────────────────

    @Setup(Level.Trial)
    public void setup() throws YSyntaxException {
        engine = new YStatelessEngine();
        engine.addWorkItemEventListener(this);
        engine.addCaseEventListener(this);

        seq2Spec = engine.unmarshalSpecification(BenchmarkSpecFactory.SEQUENTIAL_2_TASK);
        seq4Spec = engine.unmarshalSpecification(BenchmarkSpecFactory.SEQUENTIAL_4_TASK);
    }

    // ── Event listener: auto-drive workflow to completion ─────────────────

    @Override
    public void handleWorkItemEvent(YWorkItemEvent event) {
        YWorkItem item = event.getWorkItem();
        try {
            switch (event.getEventType()) {
                case ITEM_ENABLED -> engine.startWorkItem(item);
                case ITEM_STARTED -> engine.completeWorkItem(item, null, null);
                default -> { /* other transitions not needed */ }
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

    // ── Helper ────────────────────────────────────────────────────────────

    private void runCase(YSpecification spec) throws Exception {
        listenerError.set(null);
        completionLatch = new CountDownLatch(1);
        engine.launchCase(spec);
        if (!completionLatch.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Case did not complete within timeout");
        }
        Exception err = listenerError.get();
        if (err != null) throw err;
    }

    // ── Benchmarks ────────────────────────────────────────────────────────

    /**
     * Measures the latency of parsing a YAWL spec XML string into a
     * {@link YSpecification} object.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public YSpecification specUnmarshalLatency() throws YSyntaxException {
        return engine.unmarshalSpecification(BenchmarkSpecFactory.SEQUENTIAL_2_TASK);
    }

    /**
     * End-to-end latency for launching and completing a 2-task sequential
     * workflow case.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void sequentialCaseLaunch() throws Exception {
        runCase(seq2Spec);
    }

    /**
     * End-to-end latency for launching and completing a 4-task sequential
     * workflow case (deeper task chain).
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void seq4TaskCaseLaunch() throws Exception {
        runCase(seq4Spec);
    }

    /**
     * Case-launch throughput: 2-task cases completed per second.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void caseLaunchThroughput() throws Exception {
        runCase(seq2Spec);
    }

    /**
     * Round-trip latency of marshalling a completed case runner to XML and
     * restoring it into a fresh engine — the critical path for case
     * hand-off between cloud nodes.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void caseRestoreLatency() throws Exception {
        listenerError.set(null);
        completionLatch = new CountDownLatch(1);
        YNetRunner runner = engine.launchCase(seq2Spec);
        completionLatch.await(10, TimeUnit.SECONDS);

        String caseXml = engine.marshalCase(runner);

        YStatelessEngine restoreEngine = new YStatelessEngine();
        restoreEngine.restoreCase(caseXml);
    }

    /**
     * Batch parallel launch: 10 cases launched concurrently via virtual
     * threads using {@link YStatelessEngine#launchCasesParallel}.
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void parallelBatchLaunch() throws Exception {
        int batchSize = 10;
        List<String> params = new ArrayList<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            params.add(null);
        }

        completionLatch = new CountDownLatch(batchSize);
        listenerError.set(null);

        engine.launchCasesParallel(seq2Spec, params);

        if (!completionLatch.await(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Batch did not complete within timeout");
        }
        Exception err = listenerError.get();
        if (err != null) throw err;
    }
}
