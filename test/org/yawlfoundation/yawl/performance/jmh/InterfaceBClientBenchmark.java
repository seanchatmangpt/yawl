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
 * JMH benchmark for InterfaceB HTTP client performance.
 *
 * Simulates concurrent HTTP operations typical in YAWL InterfaceB:
 * - Work item checkout
 * - Work item checkin
 * - Case launches
 * - Status queries
 *
 * Target metrics:
 * - Throughput: > 100 requests/sec
 * - Latency p95: < 200ms for checkout, < 300ms for checkin
 * - Scalability: Linear up to 1000 concurrent requests
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
public class InterfaceBClientBenchmark {

    @Param({"10", "50", "100", "500", "1000"})
    private int concurrentRequests;

    @Param({"10", "20", "50"})
    private int requestDelayMs;

    private ExecutorService platformExecutor;
    private ExecutorService virtualExecutor;

    @Setup(Level.Trial)
    public void setup() {
        int cpuCount = Runtime.getRuntime().availableProcessors();
        platformExecutor = Executors.newFixedThreadPool(Math.min(100, cpuCount * 4));
        virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @TearDown(Level.Trial)
    public void teardown() throws InterruptedException {
        shutdownExecutor(platformExecutor);
        shutdownExecutor(virtualExecutor);
    }

    @Benchmark
    public void platformThreadHttpRequests(Blackhole bh) throws Exception {
        executeHttpRequests(platformExecutor, concurrentRequests, requestDelayMs, bh);
    }

    @Benchmark
    public void virtualThreadHttpRequests(Blackhole bh) throws Exception {
        executeHttpRequests(virtualExecutor, concurrentRequests, requestDelayMs, bh);
    }

    /**
     * Simulates concurrent HTTP requests (checkout/checkin operations).
     */
    private void executeHttpRequests(ExecutorService executor, int requestCount, int delay, Blackhole bh)
            throws Exception {
        CountDownLatch latch = new CountDownLatch(requestCount);
        List<Future<HttpResponse>> futures = new ArrayList<>(requestCount);

        for (int i = 0; i < requestCount; i++) {
            final int requestId = i;
            Future<HttpResponse> future = executor.submit(() -> {
                try {
                    return simulateHttpCall(requestId, delay);
                } finally {
                    latch.countDown();
                }
            });
            futures.add(future);
        }

        if (!latch.await(60, TimeUnit.SECONDS)) {
            throw new TimeoutException("HTTP requests did not complete within timeout");
        }

        for (Future<HttpResponse> future : futures) {
            bh.consume(future.get());
        }
    }

    /**
     * Simulates an HTTP call with network latency.
     */
    private HttpResponse simulateHttpCall(int requestId, int delayMs) {
        try {
            Thread.sleep(delayMs);
            
            return new HttpResponse(
                200,
                "<response><success>true</success><requestId>" + requestId + "</requestId></response>"
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Request interrupted", e);
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
     * Simple HTTP response representation.
     */
    private static class HttpResponse {
        private final int statusCode;
        private final String body;

        public HttpResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getBody() {
            return body;
        }
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(InterfaceBClientBenchmark.class.getSimpleName())
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}
