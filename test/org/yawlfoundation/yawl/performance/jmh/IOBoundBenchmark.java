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
 * JMH benchmark comparing I/O-bound operations with platform threads vs virtual threads.
 *
 * Measures throughput (ops/second) for concurrent I/O-bound tasks typical in YAWL:
 * - Database queries
 * - Service invocations
 * - File operations
 * - Network calls
 *
 * Expected results: Virtual threads should show 2-10x improvement for I/O-bound work.
 *
 * @author YAWL Performance Team
 * @date 2026-02-16
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgs = {
    "-Xms2g", "-Xmx4g",
    "-XX:+UseZGC",
    "-XX:+UseCompactObjectHeaders",
    "-Djmh.executor=VIRTUAL_TPE"
})
public class IOBoundBenchmark {

    @Param({"100", "500", "1000", "5000", "10000"})
    private int taskCount;

    @Param({"5", "10", "50"})
    private int ioDelayMs;

    private ExecutorService platformExecutor;
    private ExecutorService virtualExecutor;

    @Setup(Level.Trial)
    public void setup() {
        platformExecutor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors() * 2
        );
        virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @TearDown(Level.Trial)
    public void teardown() throws InterruptedException {
        shutdownExecutor(platformExecutor);
        shutdownExecutor(virtualExecutor);
    }

    @Benchmark
    public void platformThreads(Blackhole bh) throws Exception {
        executeIOBoundTasks(platformExecutor, taskCount, ioDelayMs, bh);
    }

    @Benchmark
    public void virtualThreads(Blackhole bh) throws Exception {
        executeIOBoundTasks(virtualExecutor, taskCount, ioDelayMs, bh);
    }

    private void executeIOBoundTasks(ExecutorService executor, int tasks, int delay, Blackhole bh)
            throws Exception {
        CountDownLatch latch = new CountDownLatch(tasks);
        List<Future<String>> futures = new ArrayList<>(tasks);

        for (int i = 0; i < tasks; i++) {
            final int taskId = i;
            Future<String> future = executor.submit(() -> {
                try {
                    simulateIO(delay);
                    return "task-" + taskId;
                } finally {
                    latch.countDown();
                }
            });
            futures.add(future);
        }

        if (!latch.await(60, TimeUnit.SECONDS)) {
            throw new TimeoutException("Tasks did not complete within timeout");
        }

        for (Future<String> future : futures) {
            bh.consume(future.get());
        }
    }

    private void simulateIO(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private void shutdownExecutor(ExecutorService executor) throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(IOBoundBenchmark.class.getSimpleName())
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
