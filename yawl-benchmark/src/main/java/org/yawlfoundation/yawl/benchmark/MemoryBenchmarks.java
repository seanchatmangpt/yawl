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
import org.yawlfoundation.yawl.exceptions.YSyntaxException;
import org.yawlfoundation.yawl.stateless.YStatelessEngine;
import org.yawlfoundation.yawl.stateless.elements.YSpecification;
import org.yawlfoundation.yawl.stateless.engine.YNetRunner;
import org.yawlfoundation.yawl.stateless.engine.YWorkItem;
import org.yawlfoundation.yawl.stateless.listener.YCaseEventListener;
import org.yawlfoundation.yawl.stateless.listener.YWorkItemEventListener;
import org.yawlfoundation.yawl.stateless.listener.event.YCaseEvent;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;
import org.yawlfoundation.yawl.stateless.listener.event.YWorkItemEvent;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Memory Performance Benchmarks.
 *
 * <p>Measures heap allocation, GC pressure, and marshal/restore memory cost
 * using the real {@link YStatelessEngine}. All case execution uses the
 * engine's genuine event-driven lifecycle — no fake work items or stub
 * completions.</p>
 *
 * <p>Benchmarks:</p>
 * <ul>
 *   <li>{@link #heapDeltaPerCase} — net heap allocated per sequential case</li>
 *   <li>{@link #gcCountUnderLoad} — GC collections triggered by {@code caseCount} cases</li>
 *   <li>{@link #marshalMemoryCost} — heap allocated to marshal a completed runner</li>
 *   <li>{@link #specParseMemoryCost} — heap allocated per spec unmarshal call</li>
 *   <li>{@link #caseCompletionMemoryRecovery} — heap delta before/after GC hint</li>
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
    "-XX:+UseCompactObjectHeaders"
})
@Threads(1)
public class MemoryBenchmarks implements YWorkItemEventListener, YCaseEventListener {

    @Param({"10", "100", "1000"})
    private int caseCount;

    private YStatelessEngine engine;
    private YSpecification spec;
    private MemoryMXBean memoryMXBean;
    private List<GarbageCollectorMXBean> gcBeans;

    private volatile CountDownLatch completionLatch;
    private final AtomicReference<Exception> listenerError = new AtomicReference<>();

    // ── Setup ─────────────────────────────────────────────────────────────

    @Setup(Level.Trial)
    public void setup() throws YSyntaxException {
        engine = new YStatelessEngine();
        engine.addWorkItemEventListener(this);
        engine.addCaseEventListener(this);
        spec = engine.unmarshalSpecification(BenchmarkSpecFactory.SEQUENTIAL_2_TASK);

        memoryMXBean = ManagementFactory.getMemoryMXBean();
        gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
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

    private void runCase() throws Exception {
        listenerError.set(null);
        completionLatch = new CountDownLatch(1);
        engine.launchCase(spec);
        if (!completionLatch.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Case did not complete within timeout");
        }
        Exception err = listenerError.get();
        if (err != null) throw err;
    }

    private long totalGcCount() {
        return gcBeans.stream().mapToLong(GarbageCollectorMXBean::getCollectionCount).sum();
    }

    private long totalGcTimeMs() {
        return gcBeans.stream().mapToLong(GarbageCollectorMXBean::getCollectionTime).sum();
    }

    // ── Benchmarks ────────────────────────────────────────────────────────

    /**
     * Net heap bytes allocated per 2-task sequential case.
     * Runs one case and measures the heap delta. JMH blackhole prevents
     * dead-code elimination of the measured value.
     */
    @Benchmark
    public void heapDeltaPerCase(Blackhole bh) throws Exception {
        long before = memoryMXBean.getHeapMemoryUsage().getUsed();
        runCase();
        long after = memoryMXBean.getHeapMemoryUsage().getUsed();
        bh.consume(after - before);
    }

    /**
     * Total GC collections triggered by running {@code caseCount} cases
     * back-to-back. Reveals allocation pressure caused by case object churn.
     */
    @Benchmark
    public void gcCountUnderLoad(Blackhole bh) throws Exception {
        long gcBefore = totalGcCount();
        long gcTimeBefore = totalGcTimeMs();

        for (int i = 0; i < caseCount; i++) {
            runCase();
        }

        bh.consume(totalGcCount() - gcBefore);
        bh.consume(totalGcTimeMs() - gcTimeBefore);
    }

    /**
     * Heap bytes allocated to marshal one completed {@link YNetRunner} to
     * XML. Measures the serialization footprint for cloud hand-off scenarios.
     */
    @Benchmark
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void marshalMemoryCost(Blackhole bh) throws Exception {
        listenerError.set(null);
        completionLatch = new CountDownLatch(1);
        YNetRunner runner = engine.launchCase(spec);
        completionLatch.await(10, TimeUnit.SECONDS);

        long before = memoryMXBean.getHeapMemoryUsage().getUsed();
        String xml = engine.marshalCase(runner);
        long after = memoryMXBean.getHeapMemoryUsage().getUsed();

        bh.consume(xml);
        bh.consume(after - before);
    }

    /**
     * Heap bytes allocated to parse one YAWL spec XML into a
     * {@link YSpecification}. One-time start-up cost per workflow definition.
     */
    @Benchmark
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void specParseMemoryCost(Blackhole bh) throws YSyntaxException {
        long before = memoryMXBean.getHeapMemoryUsage().getUsed();
        YSpecification parsed = engine.unmarshalSpecification(BenchmarkSpecFactory.SEQUENTIAL_4_TASK);
        long after = memoryMXBean.getHeapMemoryUsage().getUsed();

        bh.consume(parsed);
        bh.consume(after - before);
    }

    /**
     * Heap usage delta before and after running {@code caseCount} cases
     * followed by a GC hint. A large positive result suggests object
     * retention (potential memory leak); a near-zero result indicates
     * proper cleanup.
     */
    @Benchmark
    public void caseCompletionMemoryRecovery(Blackhole bh) throws Exception {
        long baseline = memoryMXBean.getHeapMemoryUsage().getUsed();

        for (int i = 0; i < caseCount; i++) {
            runCase();
        }

        long peakMemory = memoryMXBean.getHeapMemoryUsage().getUsed();
        System.gc();   // hint only — GC may not run immediately
        long postGc = memoryMXBean.getHeapMemoryUsage().getUsed();

        bh.consume(peakMemory - baseline);   // allocation during load
        bh.consume(postGc - baseline);       // retained after GC
    }

    /**
     * Engine re-creation cost: creates a fresh {@link YStatelessEngine},
     * parses the spec, launches one case, and completes it. Measures the
     * full start-up + first-case overhead (cold-start scenario).
     */
    @Benchmark
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void coldStartFirstCase(Blackhole bh) throws Exception {
        YStatelessEngine freshEngine = new YStatelessEngine();
        AtomicReference<Exception> err = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        freshEngine.addWorkItemEventListener(event -> {
            YWorkItem item = event.getWorkItem();
            try {
                switch (event.getEventType()) {
                    case ITEM_ENABLED -> freshEngine.startWorkItem(item);
                    case ITEM_STARTED -> freshEngine.completeWorkItem(item, null, null);
                    default -> { }
                }
            } catch (Exception e) {
                err.compareAndSet(null, e);
            }
        });
        freshEngine.addCaseEventListener(caseEvent -> {
            if (caseEvent.getEventType() == YEventType.CASE_COMPLETED ||
                    caseEvent.getEventType() == YEventType.CASE_CANCELLED) {
                latch.countDown();
            }
        });

        YSpecification freshSpec =
            freshEngine.unmarshalSpecification(BenchmarkSpecFactory.SEQUENTIAL_2_TASK);
        YNetRunner runner = freshEngine.launchCase(freshSpec);

        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Cold-start case did not complete");
        }
        if (err.get() != null) throw err.get();
        bh.consume(runner);
    }
}
