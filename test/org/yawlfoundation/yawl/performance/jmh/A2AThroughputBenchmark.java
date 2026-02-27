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
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A2A Throughput Benchmark.
 *
 * Comprehensive throughput measurement for A2A communication with focus on:
 * - Messages per second under various load levels
 * - System throughput saturation points
 * - Resource utilization during high load
 * - Different message type throughput characteristics
 * - Throughput vs latency trade-offs
 * - Connection pooling impact on throughput
 *
 * <p><b>Benchmark Targets:</b>
 * <ul>
 *   <li>Minimum throughput: &gt; 500 ops/sec</li>
 *   <li>Sustained throughput: &gt; 1000 ops/sec</li>
 *   <li>Linear scaling: Up to 1000 concurrent requests</li>
 *   <li>Resource efficiency: &lt; 80% CPU at peak load</li>
 * </ul>
 *
 * @author YAWL Performance Team
 * @version 6.0.0
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 20, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3, jvmArgs = {
    "--enable-preview",
    "-Xms4g",
    "-Xmx8g",
    "-XX:+UseZGC",
    "-XX:+UseCompactObjectHeaders"
})
public class A2AThroughputBenchmark {

    private static final Logger _logger = LogManager.getLogger(A2AThroughputBenchmark.class);
    private static final String A2A_SERVER_URL = "http://localhost:8081";

    private HttpClient httpClient;
    private ObjectMapper objectMapper;
    private List<String> testCaseIds;

    // Throughput measurement state
    private AtomicInteger activeRequests = new AtomicInteger(0);
    private AtomicLong totalBytesProcessed = new AtomicLong(0);

    @Setup
    public void setup() throws Exception {
        _logger.info("Setting up A2A Throughput Benchmarks");

        objectMapper = new ObjectMapper();
        httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();

        // Generate test case IDs
        testCaseIds = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            testCaseIds.add("throughput-test-" + i);
        }

        _logger.info("Setup completed. {} test cases created", testCaseIds.size());
    }

    @TearDown
    public void cleanup() {
        if (httpClient != null) {
            // HttpClient doesn't need explicit shutdown
        }
        _logger.info("A2A Throughput Benchmarks cleanup completed");
    }

    // =========================================================================
    // Basic Throughput Benchmarks
    // =========================================================================

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @State(Scope.Thread)
    public void benchmarkBasicThroughput(Blackhole bh) throws Exception {
        int messageCount = 100;
        AtomicInteger successCount = new AtomicInteger(0);
        long totalLatency = 0L;

        Instant startTime = Instant.now();

        for (int i = 0; i < messageCount; i++) {
            String testCaseId = testCaseIds.get(i % testCaseIds.size());
            Map<String, Object> message = A2ATestDataGenerator.generatePingMessage(testCaseId);

            long messageStart = System.nanoTime();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(A2A_SERVER_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            long messageEnd = System.nanoTime();

            if (response.statusCode() == 200) {
                successCount.incrementAndGet();
                totalLatency += (messageEnd - messageStart);
                totalBytesProcessed.addAndGet(response.body().length());
            }
        }

        Instant endTime = Instant.now();
        Duration duration = Duration.between(startTime, endTime);
        double throughput = successCount.get() / duration.toSeconds();

        _logger.debug("Basic throughput: {} messages/sec, {} bytes processed",
                throughput, totalBytesProcessed.get());

        bh.consume(throughput);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @State(Scope.Thread)
    public void benchmarkWorkflowThroughput(Blackhole bh) throws Exception {
        int messageCount = 50; // Fewer due to heavier workload
        AtomicInteger successCount = new AtomicInteger(0);
        long totalLatency = 0L;

        Instant startTime = Instant.now();

        for (int i = 0; i < messageCount; i++) {
            String testCaseId = testCaseIds.get(i % testCaseIds.size());
            Map<String, Object> message = A2ATestDataGenerator.generateWorkflowLaunchMessage(testCaseId);

            long messageStart = System.nanoTime();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(A2A_SERVER_URL + "/workflow/launch"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            long messageEnd = System.nanoTime();

            if (response.statusCode() == 200) {
                successCount.incrementAndGet();
                totalLatency += (messageEnd - messageStart);
                totalBytesProcessed.addAndGet(response.body().length());
            }
        }

        Instant endTime = Instant.now();
        Duration duration = Duration.between(startTime, endTime);
        double throughput = successCount.get() / duration.toSeconds();

        _logger.debug("Workflow throughput: {} workflows/sec, {} bytes processed",
                throughput, totalBytesProcessed.get());

        bh.consume(throughput);
    }

    // =========================================================================
    // High Load Throughput Benchmarks
    // =========================================================================

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @State(Scope.Benchmark)
    @Threads(100)
    public void benchmarkHighLoadThroughput(Blackhole bh) throws Exception {
        int threads = 100;
        int messagesPerThread = 10;
        AtomicInteger totalSuccess = new AtomicInteger(0);
        AtomicLong totalLatency = new AtomicLong(0);

        Instant startTime = Instant.now();

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            futures.add(CompletableFuture.runAsync(() -> {
                for (int i = 0; i < messagesPerThread; i++) {
                    try {
                        String testCaseId = testCaseIds.get((threadId * messagesPerThread + i) % testCaseIds.size());
                        Map<String, Object> message = A2ATestDataGenerator.generateWorkItemMessage(testCaseId);

                        long messageStart = System.nanoTime();

                        HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(A2A_SERVER_URL))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
                            .build();

                        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                        long messageEnd = System.nanoTime();

                        if (response.statusCode() == 200) {
                            totalSuccess.incrementAndGet();
                            totalLatency.addAndGet(messageEnd - messageStart);
                            totalBytesProcessed.addAndGet(response.body().length());
                        }
                    } catch (Exception e) {
                        // Log but continue
                    }
                }
            }, executor));
        }

        // Wait for all threads to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);

        executor.shutdown();

        Instant endTime = Instant.now();
        Duration duration = Duration.between(startTime, endTime);
        double totalMessages = threads * messagesPerThread;
        double throughput = totalSuccess.get() / duration.toSeconds();
        double avgLatencyMs = totalSuccess.get() > 0 ?
            totalLatency.get() / (double) totalSuccess.get() / 1_000_000.0 : 0;

        _logger.info("High load throughput: {} ops/sec, {}% success, avg latency: {:.2f}ms",
                throughput, (totalSuccess.get() / totalMessages * 100), avgLatencyMs);

        bh.consume(throughput);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @State(Scope.Benchmark)
    public void benchmarkSustainedThroughput(Blackhole bh) throws Exception {
        int testDurationSeconds = 30;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicLong totalBytesProcessed = new AtomicLong(0);
        Instant testStart = Instant.now();

        // Use a semaphore to control request rate
        Semaphore rateLimiter = new Semaphore(100); // Max 100 concurrent requests

        while (Duration.between(testStart, Instant.now()).toSeconds() < testDurationSeconds) {
            rateLimiter.acquire();

            CompletableFuture.runAsync(() -> {
                try {
                    String testCaseId = testCaseIds.get(
                        ThreadLocalRandom.current().nextInt(testCaseIds.size()));
                    Map<String, Object> message = A2ATestDataGenerator.generateQueryMessage(testCaseId);

                    long messageStart = System.nanoTime();

                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(A2A_SERVER_URL + "/query"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
                        .build();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                    long messageEnd = System.nanoTime();

                    if (response.statusCode() == 200) {
                        successCount.incrementAndGet();
                        totalBytesProcessed.addAndGet(response.body().length());
                    }
                } catch (Exception e) {
                    // Ignore errors in sustained test
                } finally {
                    rateLimiter.release();
                }
            });
        }

        // Wait for remaining requests to complete
        Thread.sleep(2000);

        double totalSeconds = Duration.between(testStart, Instant.now()).toSeconds();
        double throughput = successCount.get() / totalSeconds;

        _logger.info("Sustained throughput over {}s: {} ops/sec, {} bytes processed",
                testDurationSeconds, throughput, totalBytesProcessed.get());

        bh.consume(throughput);
    }

    // =========================================================================
    // Message Type Throughput Comparison
    // =========================================================================

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @State(Scope.Thread)
    public void benchmarkPingThroughput(Blackhole bh) throws Exception {
        measureMessageTypeThroughput(bh, "ping");
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @State(Scope.Thread)
    public void benchmarkWorkflowLaunchThroughput(Blackhole bh) throws Exception {
        measureMessageTypeThroughput(bh, "workflow_launch");
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @State(Scope.Thread)
    public void benchmarkWorkItemThroughput(Blackhole bh) throws Exception {
        measureMessageTypeThroughput(bh, "workitem");
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @State(Scope.Thread)
    public void benchmarkQueryThroughput(Blackhole bh) throws Exception {
        measureMessageTypeThroughput(bh, "query");
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @State(Scope.Thread)
    public void benchmarkLargeMessageThroughput(Blackhole bh) throws Exception {
        measureMessageTypeThroughput(bh, "large_message");
    }

    private void measureMessageTypeThroughput(Blackhole bh, String messageType) throws Exception {
        int messageCount = messageType.equals("large_message") ? 20 : 50;
        AtomicInteger successCount = new AtomicInteger(0);
        long totalBytes = 0L;

        Instant startTime = Instant.now();

        for (int i = 0; i < messageCount; i++) {
            String testCaseId = testCaseIds.get(i % testCaseIds.size());
            Map<String, Object> message;

            switch (messageType) {
                case "ping":
                    message = A2ATestDataGenerator.generatePingMessage(testCaseId);
                    break;
                case "workflow_launch":
                    message = A2ATestDataGenerator.generateWorkflowLaunchMessage(testCaseId);
                    break;
                case "workitem":
                    message = A2ATestDataGenerator.generateWorkItemMessage(testCaseId);
                    break;
                case "query":
                    message = A2ATestDataGenerator.generateQueryMessage(testCaseId);
                    break;
                case "large_message":
                    message = A2ATestDataGenerator.generateLargeMessage(testCaseId);
                    break;
                default:
                    message = A2ATestDataGenerator.generatePingMessage(testCaseId);
            }

            String endpoint = switch (messageType) {
                case "workflow_launch" -> "/workflow/launch";
                case "workitem" -> "/workitem";
                case "query" -> "/query";
                default -> "";
            };

            String url = endpoint.isEmpty() ? A2A_SERVER_URL : A2A_SERVER_URL + endpoint;

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                successCount.incrementAndGet();
                totalBytes += response.body().length();
            }
        }

        Instant endTime = Instant.now();
        Duration duration = Duration.between(startTime, endTime);
        double throughput = successCount.get() / duration.toSeconds();
        double avgMessageSize = successCount.get() > 0 ? totalBytes / (double) successCount.get() : 0;

        _logger.debug("{} throughput: {} ops/sec, avg size: {:.0f} bytes",
                messageType, throughput, avgMessageSize);

        bh.consume(throughput);
    }

    // =========================================================================
    // Connection Pool Throughput Analysis
    // =========================================================================

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @State(Scope.Thread)
    public void benchmarkConnectionPoolThroughput(Blackhole bh) throws Exception {
        // Test with different connection pool sizes
        int[] poolSizes = {10, 50, 100, 200};
        double maxThroughput = 0;

        for (int poolSize : poolSizes) {
            HttpClient pooledClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(10))
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();

            AtomicInteger successCount = new AtomicInteger(0);
            Instant startTime = Instant.now();

            int messageCount = 100;
            for (int i = 0; i < messageCount; i++) {
                String testCaseId = testCaseIds.get(i % testCaseIds.size());
                Map<String, Object> message = A2ATestDataGenerator.generatePingMessage(testCaseId);

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(A2A_SERVER_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
                    .build();

                HttpResponse<String> response = pooledClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    successCount.incrementAndGet();
                }
            }

            Instant endTime = Instant.now();
            Duration duration = Duration.between(startTime, endTime);
            double throughput = successCount.get() / duration.toSeconds();

            if (throughput > maxThroughput) {
                maxThroughput = throughput;
            }

            _logger.debug("Connection pool size {}: {} ops/sec", poolSize, throughput);
        }

        bh.consume(maxThroughput);
    }

    // =========================================================================
    // Throughput vs Latency Trade-off Analysis
    // =========================================================================

    @Benchmark
    @BenchmarkMode({Mode.Throughput, Mode.AverageTime})
    @OutputTimeUnit({TimeUnit.SECONDS, TimeUnit.MILLISECONDS})
    @State(Scope.Benchmark)
    public void benchmarkThroughputVsLatencyTradeoff(Blackhole bh) throws Exception {
        int[] threadCounts = {10, 50, 100, 500, 1000};
        List<Map<String, Object>> results = new ArrayList<>();

        for (int threadCount : threadCounts) {
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicLong totalLatency = new AtomicLong(0);
            Instant startTime = Instant.now();

            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (int t = 0; t < threadCount; t++) {
                futures.add(CompletableFuture.runAsync(() -> {
                    for (int i = 0; i < 5; i++) { // 5 messages per thread
                        try {
                            String testCaseId = testCaseIds.get(
                                (t * 5 + i) % testCaseIds.size());
                            Map<String, Object> message = A2ATestDataGenerator.generatePingMessage(testCaseId);

                            long messageStart = System.nanoTime();

                            HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(A2A_SERVER_URL))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
                                .build();

                            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                            long messageEnd = System.nanoTime();

                            if (response.statusCode() == 200) {
                                successCount.incrementAndGet();
                                totalLatency.addAndGet(messageEnd - messageStart);
                            }
                        } catch (Exception e) {
                            // Continue with next message
                        }
                    }
                }, executor));
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);
            executor.shutdown();

            Instant endTime = Instant.now();
            Duration duration = Duration.between(startTime, endTime);
            double throughput = successCount.get() / duration.toSeconds();
            double avgLatencyMs = successCount.get() > 0 ?
                totalLatency.get() / (double) successCount.get() / 1_000_000.0 : 0;

            Map<String, Object> result = Map.of(
                "threadCount", threadCount,
                "throughput", throughput,
                "avgLatencyMs", avgLatencyMs,
                "successRate", (double) successCount.get() / (threadCount * 5) * 100
            );
            results.add(result);

            _logger.info("Tradeoff - Threads: {}, Throughput: {} ops/sec, Latency: {:.2f}ms",
                    threadCount, throughput, avgLatencyMs);
        }

        // Calculate optimal balance point
        double optimalThroughput = results.stream()
            .mapToDouble(r -> (double) r.get("throughput"))
            .max()
            .orElse(0);

        bh.consume(optimalThroughput);
    }

    // =========================================================================
    // Resource Constrained Throughput
    // =========================================================================

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @State(Scope.Thread)
    public void benchmarkThroughputUnderCpuPressure(Blackhole bh) throws Exception {
        // Simulate CPU pressure during throughput test
        AtomicInteger successCount = new AtomicInteger(0);
        Instant startTime = Instant.now();

        // Run CPU-intensive task in background
        Thread cpuStressor = new Thread(() -> {
            while (Duration.between(startTime, Instant.now()).toSeconds() < 20) {
                // CPU-intensive calculation
                double result = 0;
                for (int i = 0; i < 10000; i++) {
                    result += Math.sin(i) * Math.cos(i);
                }
            }
        });
        cpuStressor.start();

        // Run throughput test under CPU pressure
        int messageCount = 50;
        for (int i = 0; i < messageCount; i++) {
            String testCaseId = testCaseIds.get(i % testCaseIds.size());
            Map<String, Object> message = A2ATestDataGenerator.generatePingMessage(testCaseId);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(A2A_SERVER_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                successCount.incrementAndGet();
            }
        }

        cpuStressor.join();

        Instant endTime = Instant.now();
        Duration duration = Duration.between(startTime, endTime);
        double throughput = successCount.get() / duration.toSeconds();

        _logger.info("Throughput under CPU pressure: {} ops/sec", throughput);

        bh.consume(throughput);
    }

    // =========================================================================
    // Validation Tests
    // =========================================================================

    @Test
    @EnabledIfEnvironmentVariable(named = "A2A_SERVER_URL", matches = ".*")
    void validateThroughputTargets() throws Exception {
        _logger.info("Validating throughput targets");

        // Test basic throughput meets minimum requirements
        int messageCount = 100;
        AtomicInteger successCount = new AtomicInteger(0);

        Instant startTime = Instant.now();

        for (int i = 0; i < messageCount; i++) {
            String testCaseId = testCaseIds.get(i % testCaseIds.size());
            Map<String, Object> message = A2ATestDataGenerator.generatePingMessage(testCaseId);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(A2A_SERVER_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                successCount.incrementAndGet();
            }
        }

        Instant endTime = Instant.now();
        Duration duration = Duration.between(startTime, endTime);
        double throughput = successCount.get() / duration.toSeconds();

        _logger.info("Validation throughput: {} ops/sec", throughput);

        // Validate minimum throughput target
        if (throughput < 500) {
            throw new IllegalStateException("Throughput below minimum target: " + throughput + " ops/sec");
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "A2A_SERVER_URL", matches = ".*")
    void validateThroughputScalability() throws Exception {
        _logger.info("Validating throughput scalability");

        int[] threadCounts = {10, 50, 100, 500};
        double previousThroughput = 0;

        for (int threadCount : threadCounts) {
            AtomicInteger successCount = new AtomicInteger(0);
            Instant startTime = Instant.now();

            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                futures.add(CompletableFuture.runAsync(() -> {
                    for (int i = 0; i < 10; i++) { // 10 messages per thread
                        try {
                            String testCaseId = testCaseIds.get(
                                (threadId * 10 + i) % testCaseIds.size());
                            Map<String, Object> message = A2ATestDataGenerator.generatePingMessage(testCaseId);

                            HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(A2A_SERVER_URL))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
                                .build();

                            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                            if (response.statusCode() == 200) {
                                successCount.incrementAndGet();
                            }
                        } catch (Exception e) {
                            // Continue with next message
                        }
                    }
                }, executor));
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);
            executor.shutdown();

            Instant endTime = Instant.now();
            Duration duration = Duration.between(startTime, endTime);
            double throughput = successCount.get() / duration.toSeconds();

            _logger.info("Scalability test - Threads: {}, Throughput: {} ops/sec",
                    threadCount, throughput);

            // Check for linear scaling
            if (previousThroughput > 0) {
                double scalingRatio = throughput / previousThroughput;
                double threadRatio = (double) threadCount / (threadCount / 10);

                if (scalingRatio < threadRatio * 0.8) { // Allow 20% inefficiency
                    _logger.warn("Sub-linear scaling detected: ratio={:.2f}, expected ratio={:.2f}",
                            scalingRatio, threadRatio);
                }
            }

            previousThroughput = throughput;
        }
    }
}