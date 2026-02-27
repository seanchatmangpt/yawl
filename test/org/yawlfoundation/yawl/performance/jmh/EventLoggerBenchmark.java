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

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * JMH benchmark for YEventLogger performance comparing platform vs virtual threads.
 *
 * Simulates YAWL event logging patterns:
 * - Concurrent event notifications
 * - Async logging to database
 * - High-volume event streams
 *
 * Target metrics:
 * - Throughput: > 1000 events/sec
 * - Latency p95: < 100ms
 * - Memory: < 50MB for 1000 concurrent loggers
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
public class EventLoggerBenchmark {

    @Param({"100", "500", "1000", "5000"})
    private int eventCount;

    @Param({"10", "100", "1000"})
    private int listenerCount;

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
     * Benchmark event notification using platform threads.
     */
    @Benchmark
    public void platformThreadEventNotification(Blackhole bh) throws Exception {
        broadcastEvents(platformExecutor, eventCount, listenerCount, bh);
    }

    /**
     * Benchmark event notification using virtual threads.
     */
    @Benchmark
    public void virtualThreadEventNotification(Blackhole bh) throws Exception {
        broadcastEvents(virtualExecutor, eventCount, listenerCount, bh);
    }

    /**
     * Simulate broadcasting events to multiple listeners.
     */
    private void broadcastEvents(ExecutorService executor, int events, int listeners, Blackhole bh)
            throws Exception {
        int totalNotifications = events * listeners;
        CountDownLatch latch = new CountDownLatch(totalNotifications);
        AtomicInteger processedCount = new AtomicInteger(0);

        for (int event = 0; event < events; event++) {
            final String eventData = "event-" + event;
            
            for (int listener = 0; listener < listeners; listener++) {
                final int listenerId = listener;
                executor.execute(() -> {
                    try {
                        processEvent(eventData, listenerId);
                        processedCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }

        if (!latch.await(60, TimeUnit.SECONDS)) {
            throw new TimeoutException(
                "Event broadcast did not complete. Processed: " + processedCount.get() +
                "/" + totalNotifications
            );
        }

        bh.consume(processedCount.get());
    }

    /**
     * Simulate event processing (I/O operation like database write).
     */
    private void processEvent(String eventData, int listenerId) {
        try {
            Thread.sleep(5);
            String result = "listener-" + listenerId + "-processed-" + eventData;
            if (result.length() == 0) {
                throw new IllegalStateException("Invalid processing");
            }
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
            .include(EventLoggerBenchmark.class.getSimpleName())
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
