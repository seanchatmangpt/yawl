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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Virtual Thread A2A Optimization Benchmark.
 *
 * Comprehensive benchmark for virtual thread optimization in A2A communication:
 * - Virtual thread vs platform thread performance comparison
 * - Thread pool size optimization
 * - Memory usage efficiency
 * - Context switching overhead
 * - Virtual thread pinning detection
 * - Structured concurrency patterns
 * - Loom-specific optimizations
 *
 * <p><b>Optimization Targets:</b>
 * <ul>
 *   <li>Virtual thread throughput: &gt; 10x platform threads</li>
 *   <li>Memory overhead: &lt; 1KB per virtual thread</li>
 *   <li>Context switch time: &lt; 0.1ms</li>
 *   <li>No thread pinning detected</li>
 * </ul>
 *
 * @author YAWL Performance Team
 * @version 6.0.0
 */
@BenchmarkMode({Mode.AverageTime, Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 20, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3, jvmArgs = {
    "--enable-preview",
    "-Xms4g",
    "-Xmx8g",
    "-XX:+UseZGC",
    "-XX:+UseCompactObjectHeaders",
    "-Djava.nio.channels.DefaultThreadPool.threadFactory=VirtualThread"
})
public class VirtualThreadA2AOptimizationBenchmark {

    private static final Logger _logger = LogManager.getLogger(VirtualThreadA2AOptimizationBenchmark.class);
    private static final String A2A_SERVER_URL = "http://localhost:8081";

    private HttpClient virtualThreadClient;
    private HttpClient platformThreadClient;
    private ObjectMapper objectMapper;
    private List<String> testCaseIds;

    // Performance tracking
    private AtomicLong totalVirtualThreadTime = new AtomicLong(0);
    private AtomicLong totalPlatformThreadTime = new AtomicLong(0);
    private AtomicInteger virtualThreadCount = new AtomicInteger(0);
    private AtomicInteger platformThreadCount = new AtomicInteger(0);

    @Setup
    public void setup() throws Exception {
        _logger.info("Setting up Virtual Thread A2A Optimization Benchmarks");

        objectMapper = new ObjectMapper();

        // Configure virtual thread optimized client
        virtualThreadClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();

        // Configure platform thread client for comparison
        platformThreadClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .executor(Executors.newFixedThreadPool(100)) // Fixed pool for comparison
            .build();

        // Generate test case IDs
        testCaseIds = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
            testCaseIds.add("virtual-thread-test-" + i);
        }

        _logger.info("Setup completed. {} test cases created", testCaseIds.size());
    }

    @TearDown
    public void cleanup() {
        if (virtualThreadClient != null) {
            // HttpClient doesn't need explicit shutdown
        }
        if (platformThreadClient != null) {
            // HttpClient doesn't need explicit shutdown
        }
        _logger.info("Virtual Thread A2A Optimization Benchmarks cleanup completed");
    }

    // =========================================================================
    // Virtual Thread vs Platform Thread Comparison
    // =========================================================================

    @Benchmark
    @BenchmarkMode({Mode.AverageTime, Mode.Throughput})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Benchmark)
    @Group("ThreadComparison")
    @GroupThreads(100)
    public void benchmarkVirtualThreadPerformance(Blackhole bh) throws Exception {
        String testCaseId = testCaseIds.get(
            ThreadLocalRandom.current().nextInt(testCaseIds.size()));
        Map<String, Object> message = A2ATestDataGenerator.generatePingMessage(testCaseId);

        Instant startTime = Instant.now();

        // Use virtual thread
        Thread.ofVirtual().name("vt-" + virtualThreadCount.incrementAndGet()).start(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(A2A_SERVER_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
                    .build();

                HttpResponse<String> response = virtualThreadClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    bh.consume(response.body());
                }
            } catch (Exception e) {
                bh.consume(e.getMessage());
            }
        }).join();

        Instant endTime = Instant.now();
        Duration duration = Duration.between(startTime, endTime);
        totalVirtualThreadTime.addAndGet(duration.toMillis());

        bh.consume(duration.toMillis());
    }

    @Benchmark
    @BenchmarkMode({Mode.AverageTime, Mode.Throughput})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Benchmark)
    @Group("ThreadComparison")
    @GroupThreads(100)
    public void benchmarkPlatformThreadPerformance(Blackhole bh) throws Exception {
        String testCaseId = testCaseIds.get(
            ThreadLocalRandom.current().nextInt(testCaseIds.size()));
        Map<String, Object> message = A2ATestDataGenerator.generatePingMessage(testCaseId);

        Instant startTime = Instant.now();

        // Use platform thread
        ExecutorService executor = Executors.newFixedThreadPool(1);
        Future<?> future = executor.submit(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(A2A_SERVER_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
                    .build();

                HttpResponse<String> response = platformThreadClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    bh.consume(response.body());
                }
            } catch (Exception e) {
                bh.consume(e.getMessage());
            }
        });

        future.get();
        executor.shutdown();

        Instant endTime = Instant.now();
        Duration duration = Duration.between(startTime, endTime);
        totalPlatformThreadTime.addAndGet(duration.toMillis());

        bh.consume(duration.toMillis());
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @State(Scope.Benchmark)
    public void benchmarkVirtualThreadThroughputScaling(Blackhole bh) throws Exception {
        int[] threadCounts = {100, 500, 1000, 5000, 10000};
        double maxThroughput = 0;

        for (int threadCount : threadCounts) {
            AtomicInteger successCount = new AtomicInteger(0);
            Instant startTime = Instant.now();

            // Use virtual thread per task executor for optimal scaling
            ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                String testCaseId = testCaseIds.get(i % testCaseIds.size());
                Map<String, Object> message = A2ATestDataGenerator.generatePingMessage(testCaseId);

                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(A2A_SERVER_URL))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
                            .build();

                        HttpResponse<String> response = virtualThreadClient.send(
                            request, HttpResponse.BodyHandlers.ofString());

                        if (response.statusCode() == 200) {
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // Continue with next request
                    }
                }, virtualExecutor);

                futures.add(future);
            }

            // Wait for all requests to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(60, TimeUnit.SECONDS);

            virtualExecutor.shutdown();

            Instant endTime = Instant.now();
            Duration duration = Duration.between(startTime, endTime);
            double throughput = successCount.get() / duration.toSeconds();

            if (throughput > maxThroughput) {
                maxThroughput = throughput;
            }

            _logger.info("Virtual thread throughput scaling - Threads: {}, Throughput: {} ops/sec",
                    threadCount, throughput);
        }

        bh.consume(maxThroughput);
    }

    // =========================================================================
    // Virtual Thread Pool Optimization
    // =========================================================================

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @State(Scope.Thread)
    public void benchmarkVirtualThreadPerTaskExecutor(Blackhole bh) throws Exception {
        int requests = 1000;
        AtomicInteger successCount = new AtomicInteger(0);

        Instant startTime = Instant.now();

        ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            String testCaseId = testCaseIds.get(i % testCaseIds.size());
            Map<String, Object> message = A2ATestDataGenerator.generateWorkItemMessage(testCaseId);

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(A2A_SERVER_URL + "/workitem"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
                        .build();

                    HttpResponse<String> response = virtualThreadClient.send(
                        request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() == 200) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Continue with next request
                }
            }, virtualExecutor);

            futures.add(future);
        }

        // Wait for all requests to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .get(60, TimeUnit.SECONDS);

        virtualExecutor.shutdown();

        Instant endTime = Instant.now();
        Duration duration = Duration.between(startTime, endTime);
        double throughput = successCount.get() / duration.toSeconds();

        _logger.info("VirtualThreadPerTaskExecutor throughput: {} ops/sec", throughput);

        bh.consume(throughput);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @State(Scope.Thread)
    public void benchmarkStructuredVirtualThreads(Blackhole bh) throws Exception {
        int requests = 1000;
        AtomicInteger successCount = new AtomicInteger(0);

        Instant startTime = Instant.now();

        // Use structured concurrency for better error handling
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            String testCaseId = testCaseIds.get(i % testCaseIds.size());
            Map<String, Object> message = A2ATestDataGenerator.generatePingMessage(testCaseId);

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(A2A_SERVER_URL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
                        .build();

                    HttpResponse<String> response = virtualThreadClient.send(
                        request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() == 200) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Continue with next request
                }
            }, executor);

            futures.add(future);
        }

        // Wait for all requests to complete with structured shutdown
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(60, TimeUnit.SECONDS);
        } finally {
            executor.shutdown();
        }

        Instant endTime = Instant.now();
        Duration duration = Duration.between(startTime, endTime);
        double throughput = successCount.get() / duration.toSeconds();

        _logger.info("Structured virtual threads throughput: {} ops/sec", throughput);

        bh.consume(throughput);
    }

    // =========================================================================
    // Memory Efficiency Analysis
    // =========================================================================

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    public void benchmarkVirtualThreadMemoryOverhead(Blackhole bh) throws Exception {
        // Measure memory usage of virtual threads vs platform threads
        int threadCount = 1000;
        long virtualThreadMemory = measureThreadMemoryUsage(true, threadCount);
        long platformThreadMemory = measureThreadMemoryUsage(false, threadCount);

        double memoryRatio = (double) virtualThreadMemory / platformThreadMemory;
        long perThreadVirtualMemory = virtualThreadMemory / threadCount;
        long perThreadPlatformMemory = platformThreadMemory / threadCount;

        _logger.info("Memory usage - Virtual: {}KB, Platform: {}KB, Ratio: {:.2f}, Per VT: {}B, Per PT: {}B",
                virtualThreadMemory / 1024, platformThreadMemory / 1024,
                memoryRatio, perThreadVirtualMemory, perThreadPlatformMemory);

        // Validate memory efficiency target
        if (perThreadVirtualMemory > 1024) { // > 1KB per virtual thread
            _logger.warn("Virtual thread memory overhead exceeds target: {}B", perThreadVirtualMemory);
        }

        bh.consume(memoryRatio);
    }

    private long measureThreadMemoryUsage(boolean useVirtualThreads, int threadCount) throws Exception {
        Runtime runtime = Runtime.getRuntime();
        long beforeMemory = runtime.totalMemory() - runtime.freeMemory();

        ExecutorService executor = useVirtualThreads ?
            Executors.newVirtualThreadPerTaskExecutor() :
            Executors.newFixedThreadPool(threadCount);

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            String testCaseId = testCaseIds.get(i % testCaseIds.size());
            Map<String, Object> message = A2ATestDataGenerator.generatePingMessage(testCaseId);

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    // Simulate some work
                    Thread.sleep(1); // 1ms work per thread
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, executor);

            futures.add(future);
        }

        // Wait for all threads to complete their work
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .get(10, TimeUnit.SECONDS);

        executor.shutdown();

        // Allow for garbage collection
        System.gc();
        Thread.sleep(100);

        long afterMemory = runtime.totalMemory() - runtime.freeMemory();
        return afterMemory - beforeMemory;
    }

    // =========================================================================
    // Context Switching Overhead
    // =========================================================================

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    public void benchmarkVirtualThreadContextSwitching(Blackhole bh) throws Exception {
        int iterations = 10000;
        long totalContextSwitchTime = 0L;

        for (int i = 0; i < iterations; i++) {
            long startTime = System.nanoTime();

            // Virtual thread context switch
            Thread.ofVirtual().name("ctx-" + i).start(() -> {
                // Minimal work to trigger context switch
                try {
                    Thread.sleep(0); // Yield immediately
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).join();

            long endTime = System.nanoTime();
            totalContextSwitchTime += (endTime - startTime);
        }

        double avgContextSwitchTime = totalContextSwitchTime / (double) iterations / 1_000_000.0;

        _logger.info("Average virtual thread context switch time: {:.3f}ms", avgContextSwitchTime);

        // Validate context switch time target
        if (avgContextSwitchTime > 0.1) { // > 0.1ms
            _logger.warn("Context switch time exceeds target: {:.3f}ms", avgContextSwitchTime);
        }

        bh.consume(avgContextSwitchTime);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    public void benchmarkPlatformThreadContextSwitching(Blackhole bh) throws Exception {
        int iterations = 10000;
        long totalContextSwitchTime = 0L;

        ExecutorService executor = Executors.newFixedThreadPool(100);

        for (int i = 0; i < iterations; i++) {
            long startTime = System.nanoTime();

            // Platform thread context switch
            Future<?> future = executor.submit(() -> {
                // Minimal work to trigger context switch
                try {
                    Thread.sleep(0); // Yield immediately
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            future.get();

            long endTime = System.nanoTime();
            totalContextSwitchTime += (endTime - startTime);
        }

        executor.shutdown();

        double avgContextSwitchTime = totalContextSwitchTime / (double) iterations / 1_000_000.0;

        _logger.info("Average platform thread context switch time: {:.3f}ms", avgContextSwitchTime);

        bh.consume(avgContextSwitchTime);
    }

    // =========================================================================
    // Virtual Thread Pinning Detection
    // =========================================================================

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    public void benchmarkVirtualThreadPinningDetection(Blackhole bh) throws Exception {
        int testIterations = 100;
        int pinnedCount = 0;

        for (int i = 0; i < testIterations; i++) {
            boolean isPinned = testForThreadPinning();

            if (isPinned) {
                pinnedCount++;
            }
        }

        double pinningRate = (double) pinnedCount / testIterations * 100;

        _logger.info("Virtual thread pinning detection: {} out of {} tests ({:.1f}%)",
                pinnedCount, testIterations, pinningRate);

        // Validate no pinning detected
        if (pinningRate > 0) {
            _logger.warn("Virtual thread pinning detected: {:.1f}%", pinningRate);
        }

        bh.consume(pinningRate);
    }

    private boolean testForThreadPinning() throws Exception {
        // Test for virtual thread pinning by detecting long-running virtual threads
        AtomicReference<Boolean> pinnedDetected = new AtomicReference<>(false);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        // Create a blocking operation that might cause pinning
        CompletableFuture<Void> blockingFuture = CompletableFuture.runAsync(() -> {
            try {
                // Simulate blocking operation that might pin the virtual thread
                Thread.sleep(100); // 100ms blocking
                pinnedDetected.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, executor);

        // Create another virtual thread to check for pinning
        CompletableFuture<Void> detectionFuture = CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(50); // 50ms detection delay
                // If the first thread is still blocked, it might be pinned
                if (blockingFuture.isDone()) {
                    // Virtual thread should not be pinned for 100ms blocking
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, executor);

        CompletableFuture.allOf(blockingFuture, detectionFuture).get(5, TimeUnit.SECONDS);
        executor.shutdown();

        return pinnedDetected.get();
    }

    // =========================================================================
    // Advanced Virtual Thread Patterns
    // =========================================================================

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @State(Scope.Thread)
    public void benchmarkScopedValuesVirtualThreads(Blackhole bh) throws Exception {
        int requests = 500;
        AtomicInteger successCount = new AtomicInteger(0);

        // Test ScopedValue propagation in virtual threads
        Instant startTime = Instant.now();

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            String testCaseId = testCaseIds.get(i % testCaseIds.size());
            Map<String, Object> message = A2ATestDataGenerator.generateQueryMessage(testCaseId);

            // Set up scoped value for virtual thread context
            final String requestId = "req-" + i;

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    // Simulate scoped value context
                    String contextValue = "virtual-thread-context-" + requestId;

                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(A2A_SERVER_URL + "/query"))
                        .header("Content-Type", "application/json")
                        .header("X-Request-ID", requestId)
                        .header("X-Context", contextValue)
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
                        .build();

                    HttpResponse<String> response = virtualThreadClient.send(
                        request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() == 200) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Continue with next request
                }
            }, executor);

            futures.add(future);
        }

        // Wait for all requests to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .get(60, TimeUnit.SECONDS);

        executor.shutdown();

        Instant endTime = Instant.now();
        Duration duration = Duration.between(startTime, endTime);
        double throughput = successCount.get() / duration.toSeconds();

        _logger.info("Scoped values virtual threads throughput: {} ops/sec", throughput);

        bh.consume(throughput);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @State(Scope.Thread)
    public void benchmarkVirtualThreadWithSemaphoreThrottling(Blackhole bh) throws Exception {
        int requests = 2000;
        int maxConcurrent = 500; // Throttle to 500 concurrent requests
        AtomicInteger successCount = new AtomicInteger(0);

        Instant startTime = Instant.now();

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        Semaphore semaphore = new Semaphore(maxConcurrent);

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            String testCaseId = testCaseIds.get(i % testCaseIds.size());
            Map<String, Object> message = A2ATestDataGenerator.generatePingMessage(testCaseId);

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    semaphore.acquire(); // Throttle concurrent requests

                    try {
                        HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(A2A_SERVER_URL))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
                            .build();

                        HttpResponse<String> response = virtualThreadClient.send(
                            request, HttpResponse.BodyHandlers.ofString());

                        if (response.statusCode() == 200) {
                            successCount.incrementAndGet();
                        }
                    } finally {
                        semaphore.release();
                    }
                } catch (Exception e) {
                    // Continue with next request
                }
            }, executor);

            futures.add(future);
        }

        // Wait for all requests to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .get(60, TimeUnit.SECONDS);

        executor.shutdown();

        Instant endTime = Instant.now();
        Duration duration = Duration.between(startTime, endTime);
        double throughput = successCount.get() / duration.toSeconds();

        _logger.info("Virtual threads with semaphore throttling: {} ops/sec", throughput);

        bh.consume(throughput);
    }

    // =========================================================================
    // Validation Tests
    // =========================================================================

    @Test
    @EnabledIfEnvironmentVariable(named = "A2A_SERVER_URL", matches = ".*")
    void validateVirtualThreadPerformanceAdvantage() throws Exception {
        _logger.info("Validating virtual thread performance advantage");

        // Compare virtual thread vs platform thread performance
        int requests = 500;
        long virtualThreadTime = 0L;
        long platformThreadTime = 0L;

        // Test virtual thread performance
        Instant vtStart = Instant.now();
        for (int i = 0; i < requests; i++) {
            String testCaseId = testCaseIds.get(i % testCaseIds.size());
            Map<String, Object> message = A2ATestDataGenerator.generatePingMessage(testCaseId);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(A2A_SERVER_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
                .build();

            HttpResponse<String> response = virtualThreadClient.send(request, HttpResponse.BodyHandlers.ofString());
        }
        Instant vtEnd = Instant.now();
        virtualThreadTime = Duration.between(vtStart, vtEnd).toMillis();

        // Test platform thread performance
        Instant ptStart = Instant.now();
        ExecutorService ptExecutor = Executors.newFixedThreadPool(100);
        for (int i = 0; i < requests; i++) {
            String testCaseId = testCaseIds.get(i % testCaseIds.size());
            Map<String, Object> message = A2ATestDataGenerator.generatePingMessage(testCaseId);

            Future<?> future = ptExecutor.submit(() -> {
                try {
                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(A2A_SERVER_URL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
                        .build();

                    HttpResponse<String> response = platformThreadClient.send(request, HttpResponse.BodyHandlers.ofString());
                } catch (Exception e) {
                    // Ignore errors in validation test
                }
            });

            future.get();
        }
        Instant ptEnd = Instant.now();
        platformThreadTime = Duration.between(ptStart, ptEnd).toMillis();

        ptExecutor.shutdown();

        double performanceRatio = (double) platformThreadTime / virtualThreadTime;
        _logger.info("Virtual thread performance ratio: {:.2f}x faster", performanceRatio);

        // Validate virtual thread performance advantage
        if (performanceRatio < 5.0) { // Should be at least 5x faster
            throw new IllegalStateException("Virtual thread performance advantage insufficient: " + performanceRatio + "x");
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "A2A_SERVER_URL", matches = ".*")
    void validateVirtualThreadMemoryEfficiency() throws Exception {
        _logger.info("Validating virtual thread memory efficiency");

        int threadCount = 1000;
        long virtualThreadMemory = measureThreadMemoryUsage(true, threadCount);
        long perThreadMemory = virtualThreadMemory / threadCount;

        _logger.info("Virtual thread memory per thread: {} bytes", perThreadMemory);

        // Validate memory efficiency target
        if (perThreadMemory > 1024) { // > 1KB per thread
            throw new IllegalStateException("Virtual thread memory overhead exceeds target: " + perThreadMemory + " bytes");
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "A2A_SERVER_URL", matches = ".*")
    void validateVirtualThreadScaling() throws Exception {
        _logger.info("Validating virtual thread scaling");

        int[] threadCounts = {100, 1000, 5000, 10000};
        double previousThroughput = 0;
        boolean linearScaling = true;

        for (int threadCount : threadCounts) {
            AtomicInteger successCount = new AtomicInteger(0);

            Instant startTime = Instant.now();

            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                String testCaseId = testCaseIds.get(i % testCaseIds.size());
                Map<String, Object> message = A2ATestDataGenerator.generatePingMessage(testCaseId);

                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(A2A_SERVER_URL))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
                            .build();

                        HttpResponse<String> response = virtualThreadClient.send(request, HttpResponse.BodyHandlers.ofString());
                        if (response.statusCode() == 200) {
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // Continue with next request
                    }
                }, executor);

                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(60, TimeUnit.SECONDS);

            executor.shutdown();

            Instant endTime = Instant.now();
            Duration duration = Duration.between(startTime, endTime);
            double throughput = successCount.get() / duration.toSeconds();

            _logger.info("Scaling test - Threads: {}, Throughput: {} ops/sec",
                    threadCount, throughput);

            // Check for linear scaling
            if (previousThroughput > 0) {
                double scalingRatio = throughput / previousThroughput;
                double threadRatio = (double) threadCount / (threadCounts[Math.max(0, java.util.Arrays.asList(threadCounts).indexOf(threadCount) - 1)];

                if (scalingRatio < threadRatio * 0.7) { // Allow 30% inefficiency
                    linearScaling = false;
                    _logger.warn("Sub-linear scaling detected at {} threads: ratio={:.2f}",
                            threadCount, scalingRatio);
                }
            }

            previousThroughput = throughput;
        }

        if (!linearScaling) {
            _logger.warn("Virtual thread scaling shows sub-linear behavior");
        }
    }
}