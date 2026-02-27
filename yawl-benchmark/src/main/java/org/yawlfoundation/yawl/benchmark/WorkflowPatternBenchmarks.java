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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Workflow Pattern Performance Benchmarks.
 *
 * <p>Measures the end-to-end execution latency for the four principal
 * workflow control-flow patterns. Each benchmark uses a real
 * {@link YStatelessEngine} with genuine YAWL specification XML; the
 * event-driven auto-driver starts and completes every enabled work item
 * so each case runs to completion without external intervention.</p>
 *
 * <p>Patterns benchmarked:</p>
 * <ul>
 *   <li>{@link #sequentialPattern} — 2-task linear chain</li>
 *   <li>{@link #parallelSplitSyncPattern} — AND-split followed by AND-join</li>
 *   <li>{@link #exclusiveChoicePattern} — XOR-split (default branch)</li>
 *   <li>{@link #multiChoicePattern} — OR-split (all branches enabled)</li>
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
public class WorkflowPatternBenchmarks implements YWorkItemEventListener, YCaseEventListener {

    private YStatelessEngine engine;

    private YSpecification seqSpec;
    private YSpecification parallelSpec;
    private YSpecification xorSpec;
    private YSpecification orSpec;

    private volatile CountDownLatch completionLatch;
    private final AtomicReference<Exception> listenerError = new AtomicReference<>();

    // ── Setup ─────────────────────────────────────────────────────────────

    @Setup(Level.Trial)
    public void setup() throws YSyntaxException {
        engine = new YStatelessEngine();
        engine.addWorkItemEventListener(this);
        engine.addCaseEventListener(this);

        seqSpec     = engine.unmarshalSpecification(BenchmarkSpecFactory.SEQUENTIAL_2_TASK);
        parallelSpec = engine.unmarshalSpecification(BenchmarkSpecFactory.PARALLEL_SPLIT_SYNC);
        xorSpec     = engine.unmarshalSpecification(BenchmarkSpecFactory.EXCLUSIVE_CHOICE);
        orSpec      = engine.unmarshalSpecification(BenchmarkSpecFactory.MULTI_CHOICE);
    }

    // ── Event listener ────────────────────────────────────────────────────

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
     * 2-task sequential pattern: Start → t1 → t2 → End.
     * Baseline for all other pattern benchmarks.
     */
    @Benchmark
    public void sequentialPattern() throws Exception {
        runCase(seqSpec);
    }

    /**
     * AND-split / AND-join pattern: split → [branchA || branchB] → sync → End.
     * Measures token forking + AND-join synchronization overhead.
     */
    @Benchmark
    public void parallelSplitSyncPattern() throws Exception {
        runCase(parallelSpec);
    }

    /**
     * XOR-split / XOR-join (exclusive choice): choice → defaultPath → merge → End.
     * Measures routing guard evaluation overhead.
     */
    @Benchmark
    public void exclusiveChoicePattern() throws Exception {
        runCase(xorSpec);
    }

    /**
     * OR-split (multi-choice): mc → [brA + brB + brC] → End.
     * Measures OR-split firing all branches simultaneously.
     */
    @Benchmark
    public void multiChoicePattern() throws Exception {
        runCase(orSpec);
    }
}
