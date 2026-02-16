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

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

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
@Fork(value = 1, jvmArgs = {"-Xms2g", "-Xmx4g", "-XX:+UseG1GC"})
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
            List<Future<WorkItem>> stageFutures = new ArrayList<>();

            for (int task = 0; task < tasksPerStage; task++) {
                final int stageId = stage;
                final int taskId = task;
                
                Future<WorkItem> future = executor.submit(() -> {
                    return executeTask(stageId, taskId);
                });
                stageFutures.add(future);
            }

            for (Future<WorkItem> future : stageFutures) {
                WorkItem item = future.get(30, TimeUnit.SECONDS);
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
     */
    private WorkItem executeTask(int stageId, int taskId) {
        try {
            Thread.sleep(2);
            
            WorkItem item = new WorkItem("stage-" + stageId + "-task-" + taskId);
            
            Thread.sleep(3);
            item.checkout();
            
            Thread.sleep(5);
            item.execute();
            
            Thread.sleep(3);
            item.checkin();
            
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
     * Simplified WorkItem representation for benchmarking.
     */
    private static class WorkItem {
        private final String id;
        private String status;

        public WorkItem(String id) {
            this.id = id;
            this.status = "created";
        }

        public void checkout() {
            this.status = "checked-out";
        }

        public void execute() {
            this.status = "executing";
        }

        public void checkin() {
            this.status = "completed";
        }

        public String getId() {
            return id;
        }

        public String getStatus() {
            return status;
        }
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(WorkflowExecutionBenchmark.class.getSimpleName())
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
