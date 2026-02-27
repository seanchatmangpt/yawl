/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.performance.jmh;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * JMH benchmark for real-world YAWL workflow execution patterns.
 *
 * Simulates:
 * - Multi-stage workflow execution
 * - Parallel task processing
 * - Work item lifecycle (create -> checkout -> execute -> checkin)
 * - Service invocations
 *
 * Target metrics:
 * - Workflow completion time: < 5s for 100 tasks
 * - Task throughput: > 200 tasks/sec
 * - Resource usage: stable under load
 *
 * @author YAWL Performance Team
 * @date 2026-02-16
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgs = {
    "-Xms2g", "-Xmx4g",
    "-XX:+UseZGC",
    "-XX:+UseCompactObjectHeaders",
    "-Djmh.executor=VIRTUAL_TPE"
})
public class WorkflowExecutionBenchmark {

    @Param({"10", "50", "100"})
    private int parallelTasks;

    @Param({"3", "5", "10"})
    private int workflowStages;

    private ExecutorService platformExecutor;
    private ExecutorService virtualExecutor;

    @Setup(Level.Trial)
    public void setup() {
        int cpuCount = Runtime.getRuntime().availableProcessors();
        platformExecutor = Executors.newFixedThreadPool(cpuCount * 2);
        virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @TearDown(Level.Trial)
    public void teardown() throws InterruptedException {
        shutdownExecutor(platformExecutor);
        shutdownExecutor(virtualExecutor);
    }

    /**
     * Benchmark workflow execution using platform threads.
     */
    @Benchmark
    public void platformThreadWorkflow(Blackhole bh) throws Exception {
        executeWorkflow(platformExecutor, workflowStages, parallelTasks, bh);
    }

    /**
     * Benchmark workflow execution using virtual threads.
     */
    @Benchmark
    public void virtualThreadWorkflow(Blackhole bh) throws Exception {
        executeWorkflow(virtualExecutor, workflowStages, parallelTasks, bh);
    }

    /**
     * Execute a multi-stage workflow with parallel tasks per stage.
     */
    private void executeWorkflow(ExecutorService executor, int stages, int tasksPerStage, Blackhole bh)
            throws Exception {
        AtomicInteger completedTasks = new AtomicInteger(0);

        for (int stage = 0; stage < stages; stage++) {
            List<Future<WorkItemState>> stageFutures = new ArrayList<>();

            for (int task = 0; task < tasksPerStage; task++) {
                final int stageId = stage;
                final int taskId = task;

                Future<WorkItemState> future = executor.submit(() -> executeTask(stageId, taskId));
                stageFutures.add(future);
            }

            for (Future<WorkItemState> future : stageFutures) {
                WorkItemState item = future.get(30, TimeUnit.SECONDS);
                completedTasks.incrementAndGet();
                bh.consume(item);
            }
        }

        int totalTasks = stages * tasksPerStage;
        if (completedTasks.get() != totalTasks) {
            throw new IllegalStateException(
                "Expected " + totalTasks + " tasks, completed " + completedTasks.get()
            );
        }
    }

    /**
     * Simulate executing a single workflow task.
     * Represents: checkout -> execute -> checkin cycle.
     * Uses sealed state machine — each transition returns a new record state.
     */
    private WorkItemState executeTask(int stageId, int taskId) {
        try {
            Thread.sleep(2);

            WorkItemState item = WorkItemState.create("stage-" + stageId + "-task-" + taskId);

            Thread.sleep(3);
            item = item.checkout();

            Thread.sleep(5);
            item = item.execute();

            Thread.sleep(3);
            item = item.checkin();

            return item;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Task interrupted", e);
        }
    }

    private void shutdownExecutor(ExecutorService executor) throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    /**
     * WorkItem state machine — sealed hierarchy with record states.
     *
     * <p>Each transition returns a new immutable record state (no mutable fields).
     * Java 25: sealed + records enable exhaustive pattern matching in switch expressions.</p>
     */
    private sealed interface WorkItemState
            permits WorkItemState.Created, WorkItemState.CheckedOut,
                    WorkItemState.Executing, WorkItemState.Completed {

        record Created(String id)    implements WorkItemState {}
        record CheckedOut(String id) implements WorkItemState {}
        record Executing(String id)  implements WorkItemState {}
        record Completed(String id)  implements WorkItemState {}

        default String id() {
            return switch (this) {
                case Created(var i)    -> i;
                case CheckedOut(var i) -> i;
                case Executing(var i)  -> i;
                case Completed(var i)  -> i;
            };
        }

        static WorkItemState create(String id) { return new Created(id); }
        default WorkItemState checkout()       { return new CheckedOut(id()); }
        default WorkItemState execute()        { return new Executing(id()); }
        default WorkItemState checkin()        { return new Completed(id()); }
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(WorkflowExecutionBenchmark.class.getSimpleName())
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
