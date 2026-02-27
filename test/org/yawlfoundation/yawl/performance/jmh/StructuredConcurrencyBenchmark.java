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

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * JMH benchmark comparing StructuredTaskScope vs CompletableFuture.
 *
 * Measures:
 * - Execution overhead
 * - Error propagation time
 * - Cancellation efficiency
 * - Resource cleanup
 *
 * Expected: StructuredTaskScope should show better cancellation and cleanup.
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
public class StructuredConcurrencyBenchmark {

    @Param({"10", "50", "100", "500"})
    private int taskCount;

    @Param({"5", "10", "20"})
    private int taskDurationMs;

    /**
     * Benchmark using StructuredTaskScope (Java 21+ structured concurrency).
     */
    @Benchmark
    public void structuredTaskScope(Blackhole bh) throws Exception {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            List<StructuredTaskScope.Subtask<String>> subtasks = new ArrayList<>();

            for (int i = 0; i < taskCount; i++) {
                final int taskId = i;
                StructuredTaskScope.Subtask<String> subtask = scope.fork(() -> {
                    Thread.sleep(taskDurationMs);
                    return "task-" + taskId;
                });
                subtasks.add(subtask);
            }

            scope.join();
            scope.throwIfFailed();

            for (StructuredTaskScope.Subtask<String> subtask : subtasks) {
                bh.consume(subtask.get());
            }
        }
    }

    /**
     * Benchmark using traditional CompletableFuture.
     */
    @Benchmark
    public void completableFuture(Blackhole bh) throws Exception {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        try {
            List<CompletableFuture<String>> futures = new ArrayList<>();

            for (int i = 0; i < taskCount; i++) {
                final int taskId = i;
                CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        Thread.sleep(taskDurationMs);
                        return "task-" + taskId;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                }, executor);
                futures.add(future);
            }

            CompletableFuture<Void> allOf = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
            );
            allOf.get(30, TimeUnit.SECONDS);

            for (CompletableFuture<String> future : futures) {
                bh.consume(future.get());
            }
        } finally {
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    /**
     * Benchmark error propagation with StructuredTaskScope.
     */
    @Benchmark
    public void structuredTaskScopeWithError(Blackhole bh) throws Exception {
        boolean exceptionCaught = false;
        
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            for (int i = 0; i < taskCount; i++) {
                final int taskId = i;
                scope.fork(() -> {
                    Thread.sleep(taskDurationMs);
                    if (taskId == taskCount / 2) {
                        throw new RuntimeException("Simulated failure");
                    }
                    return "task-" + taskId;
                });
            }

            scope.join();
            scope.throwIfFailed();
        } catch (ExecutionException e) {
            exceptionCaught = true;
        }

        bh.consume(exceptionCaught);
    }

    /**
     * Benchmark error propagation with CompletableFuture.
     */
    @Benchmark
    public void completableFutureWithError(Blackhole bh) throws Exception {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        boolean exceptionCaught = false;

        try {
            List<CompletableFuture<String>> futures = new ArrayList<>();

            for (int i = 0; i < taskCount; i++) {
                final int taskId = i;
                CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        Thread.sleep(taskDurationMs);
                        if (taskId == taskCount / 2) {
                            throw new RuntimeException("Simulated failure");
                        }
                        return "task-" + taskId;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                }, executor);
                futures.add(future);
            }

            CompletableFuture<Void> allOf = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
            );
            allOf.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            exceptionCaught = true;
        } finally {
            executor.shutdown();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }

        bh.consume(exceptionCaught);
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(StructuredConcurrencyBenchmark.class.getSimpleName())
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
