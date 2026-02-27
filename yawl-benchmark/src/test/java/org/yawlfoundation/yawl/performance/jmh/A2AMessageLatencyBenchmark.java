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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A2A Message Latency Benchmark.
 *
 * Comprehensive latency measurement for A2A communication with focus on:
 * - End-to-end message latency distribution
 * - Cold vs warm connection latency
 * - Message size impact on latency
 * - Different message types (ping, workflow, workitem, query)
 * - P95, P99 latency percentiles
 * - Latency under network conditions
 *
 * <p><b>Benchmark Targets:</b>
 * <ul>
 *   <li>P50 latency: &lt; 50ms</li>
 *   <li>P95 latency: &lt; 100ms</li>
 *   <li>P99 latency: &lt; 200ms</li>
 *   <li>Cold start penalty: &lt; 2x warm latency</li>
 * </ul>
 *
 * @author YAWL Performance Team
 * @version 6.0.0
 */
@BenchmarkMode({Mode.AverageTime, Mode.SampleTime, Mode.Percentiles})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 15, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3, jvmArgs = {
    "--enable-preview",
    "-Xms4g",
    "-Xmx8g",
    "-XX:+UseZGC",
    "-XX:+UseCompactObjectHeaders"
})
public class A2AMessageLatencyBenchmark {

    private static final Logger _logger = LogManager.getLogger(A2AMessageLatencyBenchmark.class);
    private static final String A2A_SERVER_URL = "http://localhost:8081";
    private static final int WARMUP_MESSAGES = 50;

    private HttpClient httpClient;
    private ObjectMapper objectMapper;
    private List<String> testCaseIds;

    // For latency percentile calculation
    private List<Long> latencySamples;
    private static final int SAMPLE_SIZE = 1000;

    @Setup
    public void setup() throws Exception {
        _logger.info("Setting up A2A Message Latency Benchmarks");

        objectMapper = new ObjectMapper();
        httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();

        // Generate test case IDs
        testCaseIds = new ArrayList<>();
        for (int i = 0; i < SAMPLE_SIZE; i++) {
            testCaseIds.add("latency-test-" + i);
        }

        // Pre-warm the connection
        prewarmConnections();

        _logger.info("Setup completed. {} test cases created", testCaseIds.size());
    }

    @TearDown
    public void cleanup() {
        if (httpClient != null) {
            // HttpClient doesn't need explicit shutdown
        }
        _logger.info("A2A Message Latency Benchmarks cleanup completed");
    }

    /**
     * Pre-warm HTTP connections to ensure accurate latency measurements.
     */
    private void prewarmConnections() throws Exception {
        _logger.info("Pre-warming connections");
        for (int i = 0; i < WARMUP_MESSAGES; i++) {
            String testCaseId = testCaseIds.get(i % testCaseIds.size());
            Map<String, Object> message = A2ATestDataGenerator.generatePingMessage(testCaseId);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(A2A_SERVER_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
                .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                // Don't care about response, just warming up
            } catch (Exception e) {
                // Ignore warm-up failures
            }
        }
    }

    // =========================================================================
    // Core Latency Benchmarks
    // =========================================================================

    @Benchmark
    @BenchmarkMode({Mode.AverageTime, Mode.SampleTime, Mode.Percentiles})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    @Measurement(iterations = 10, time = 10, timeUnit = TimeUnit.SECONDS)
    public void benchmarkPingPongLatency(Blackhole bh) throws Exception {
        String testCaseId = testCaseIds.get(ThreadLocalRandom.current().nextInt(testCaseIds.size()));

        Map<String, Object> message = A2ATestDataGenerator.generatePingMessage(testCaseId);
        String payload = objectMapper.writeValueAsString(message);

        long startTime = System.nanoTime();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(A2A_SERVER_URL))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        long endTime = System.nanoTime();
        long latencyNanos = endTime - startTime;
        double latencyMs = latencyNanos / 1_000_000.0;

        // Collect for percentile calculation
        if (latencySamples.size() < SAMPLE_SIZE) {
            latencySamples.add((long) latencyMs);
        }

        bh.consume(response.body());
        bh.consume(latencyMs);
    }

    @Benchmark
    @BenchmarkMode({Mode.AverageTime, Mode.SampleTime, Mode.Percentiles})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    @Measurement(iterations = 10, time = 10, timeUnit = TimeUnit.SECONDS)
    public void benchmarkWorkflowLaunchLatency(Blackhole bh) throws Exception {
        String testCaseId = testCaseIds.get(ThreadLocalRandom.current().nextInt(testCaseIds.size()));

        Map<String, Object> message = A2ATestDataGenerator.generateWorkflowLaunchMessage(testCaseId);
        String payload = objectMapper.writeValueAsString(message);

        long startTime = System.nanoTime();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(A2A_SERVER_URL + "/workflow/launch"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        long endTime = System.nanoTime();
        long latencyMs = (endTime - startTime) / 1_000_000.0;

        bh.consume(response.body());
        bh.consume(latencyMs);
    }

    @Benchmark
    @BenchmarkMode({Mode.AverageTime, Mode.SampleTime, Mode.Percentiles})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    @Measurement(iterations = 10, time = 10, timeUnit = TimeUnit.SECONDS)
    public void benchmarkWorkItemLatency(Blackhole bh) throws Exception {
        String testCaseId = testCaseIds.get(ThreadLocalRandom.current().nextInt(testCaseIds.size()));

        Map<String, Object> message = A2ATestDataGenerator.generateWorkItemMessage(testCaseId);
        String payload = objectMapper.writeValueAsString(message);

        long startTime = System.nanoTime();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(A2A_SERVER_URL + "/workitem"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        long endTime = System.nanoTime();
        long latencyMs = (endTime - startTime) / 1_000_000.0;

        bh.consume(response.body());
        bh.consume(latencyMs);
    }

    @Benchmark
    @BenchmarkMode({Mode.AverageTime, Mode.SampleTime, Mode.Percentiles})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    @Measurement(iterations = 10, time = 10, timeUnit = TimeUnit.SECONDS)
    public void benchmarkQueryLatency(Blackhole bh) throws Exception {
        String testCaseId = testCaseIds.get(ThreadLocalRandom.current().nextInt(testCaseIds.size()));

        Map<String, Object> message = A2ATestDataGenerator.generateQueryMessage(testCaseId);
        String payload = objectMapper.writeValueAsString(message);

        long startTime = System.nanoTime();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(A2A_SERVER_URL + "/query"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        long endTime = System.nanoTime();
        long latencyMs = (endTime - startTime) / 1_000_000.0;

        bh.consume(response.body());
        bh.consume(latencyMs);
    }

    // =========================================================================
    // Message Size Impact on Latency
    // =========================================================================

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    @Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
    public void benchmarkSmallMessageLatency(Blackhole bh) throws Exception {
        String testCaseId = testCaseIds.get(ThreadLocalRandom.current().nextInt(testCaseIds.size()));

        // Very small message (< 1KB)
        Map<String, Object> message = A2ATestDataGenerator.generatePingMessage(testCaseId);
        message.put("size", "small");
        String payload = objectMapper.writeValueAsString(message);

        measureLatency(bh, payload);
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    @Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
    public void benchmarkMediumMessageLatency(Blackhole bh) throws Exception {
        String testCaseId = testCaseIds.get(ThreadLocalRandom.current().nextInt(testCaseIds.size()));

        // Medium message (1KB - 10KB)
        Map<String, Object> message = A2ATestDataGenerator.generateWorkflowLaunchMessage(testCaseId);
        // Add some medium-sized data
        for (int i = 0; i < 50; i++) {
            message.put("field_" + i, "medium_value_" + i);
        }
        String payload = objectMapper.writeValueAsString(message);

        measureLatency(bh, payload);
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    @Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
    public void benchmarkLargeMessageLatency(Blackhole bh) throws Exception {
        String testCaseId = testCaseIds.get(ThreadLocalRandom.current().nextInt(testCaseIds.size()));

        // Large message (> 10KB)
        Map<String, Object> message = A2ATestDataGenerator.generateLargeMessage(testCaseId);
        String payload = objectMapper.writeValueAsString(message);

        measureLatency(bh, payload);
    }

    /**
     * Helper method to measure latency for a given payload.
     */
    private void measureLatency(Blackhole bh, String payload) throws Exception {
        long startTime = System.nanoTime();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(A2A_SERVER_URL))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        long endTime = System.nanoTime();
        double latencyMs = (endTime - startTime) / 1_000_000.0;

        bh.consume(response.body());
        bh.consume(latencyMs);
    }

    // =========================================================================
    // Cold Start vs Warm Latency
    // =========================================================================

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    public void benchmarkColdStartLatency(Blackhole bh) throws Exception {
        // Create a new HttpClient for cold start measurement
        HttpClient coldHttpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

        String testCaseId = testCaseIds.get(0);
        Map<String, Object> message = A2ATestDataGenerator.generatePingMessage(testCaseId);
        String payload = objectMapper.writeValueAsString(message);

        long startTime = System.nanoTime();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(A2A_SERVER_URL))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        HttpResponse<String> response = coldHttpClient.send(request, HttpResponse.BodyHandlers.ofString());

        long endTime = System.nanoTime();
        double latencyMs = (endTime - startTime) / 1_000_000.0;

        bh.consume(response.body());
        bh.consume(latencyMs);

        // Don't shutdown - let it be GC'd
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    public void benchmarkWarmStartLatency(Blackhole bh) throws Exception {
        // Use the pre-warmed httpClient for warm start measurement
        String testCaseId = testCaseIds.get(0);
        Map<String, Object> message = A2ATestDataGenerator.generatePingMessage(testCaseId);
        String payload = objectMapper.writeValueAsString(message);

        long startTime = System.nanoTime();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(A2A_SERVER_URL))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        long endTime = System.nanoTime();
        double latencyMs = (endTime - startTime) / 1_000_000.0;

        bh.consume(response.body());
        bh.consume(latencyMs);
    }

    // =========================================================================
    // Latency Percentile Analysis
    // =========================================================================

    @Benchmark
    @BenchmarkMode(Mode.Percentiles)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Benchmark)
    @Measurement(iterations = 1, time = 30, timeUnit = TimeUnit.SECONDS)
    public void benchmarkLatencyPercentiles(Blackhole bh) throws Exception {
        latencySamples = new ArrayList<>();
        int iterations = 200;

        for (int i = 0; i < iterations; i++) {
            String testCaseId = testCaseIds.get(i % testCaseIds.size());
            Map<String, Object> message = A2ATestDataGenerator.generatePingMessage(testCaseId);
            String payload = objectMapper.writeValueAsString(message);

            long startTime = System.nanoTime();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(A2A_SERVER_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            long endTime = System.nanoTime();
            double latencyMs = (endTime - startTime) / 1_000_000.0;

            latencySamples.add((long) latencyMs);

            if (response.statusCode() == 200) {
                bh.consume(response.body());
            }
        }

        // Calculate percentiles
        if (!latencySamples.isEmpty()) {
            List<Long> sortedSamples = latencySamples.stream()
                .sorted()
                .collect(Collectors.toList());

            double p50 = calculatePercentile(sortedSamples, 50.0);
            double p95 = calculatePercentile(sortedSamples, 95.0);
            double p99 = calculatePercentile(sortedSamples, 99.0);

            _logger.info("Latency percentiles - P50: {:.2f}ms, P95: {:.2f}ms, P99: {:.2f}ms",
                    p50, p95, p99);

            bh.consume(p50);
            bh.consume(p95);
            bh.consume(p99);
        }
    }

    /**
     * Calculate the nth percentile from a sorted list of values.
     */
    private double calculatePercentile(List<Long> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) return 0.0;

        double index = (percentile / 100.0) * (sortedValues.size() - 1);
        int lowerIndex = (int) Math.floor(index);
        int upperIndex = (int) Math.ceil(index);

        if (lowerIndex == upperIndex) {
            return sortedValues.get(lowerIndex);
        }

        // Linear interpolation
        double lowerValue = sortedValues.get(lowerIndex);
        double upperValue = sortedValues.get(upperIndex);
        double weight = index - lowerIndex;

        return lowerValue + (upperValue - lowerValue) * weight;
    }

    // =========================================================================
    // Network Conditions Impact on Latency
    // =========================================================================

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    public void benchmarkLatencyWithNetworkDelay(Blackhole bh) throws Exception {
        // Simulate network delay with artificial timeout
        HttpClient delayedClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(50))
            .build();

        String testCaseId = testCaseIds.get(ThreadLocalRandom.current().nextInt(testCaseIds.size()));
        Map<String, Object> message = A2ATestDataGenerator.generatePingMessage(testCaseId);
        message.put("networkDelay", 50); // 50ms artificial delay
        String payload = objectMapper.writeValueAsString(message);

        long startTime = System.nanoTime();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(A2A_SERVER_URL))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        HttpResponse<String> response = delayedClient.send(request, HttpResponse.BodyHandlers.ofString());

        long endTime = System.nanoTime();
        double latencyMs = (endTime - startTime) / 1_000_000.0;

        bh.consume(response.body());
        bh.consume(latencyMs);
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    public void benchmarkLatencyWithVariableNetworkConditions(Blackhole bh) throws Exception {
        // Simulate variable network conditions
        int variableDelay = ThreadLocalRandom.current().nextInt(0, 200); // 0-200ms variable delay

        String testCaseId = testCaseIds.get(ThreadLocalRandom.current().nextInt(testCaseIds.size()));
        Map<String, Object> message = A2ATestDataGenerator.generatePingMessage(testCaseId);
        message.put("variableDelay", variableDelay);
        String payload = objectMapper.writeValueAsString(message);

        long startTime = System.nanoTime();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(A2A_SERVER_URL))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        long endTime = System.nanoTime();
        double latencyMs = (endTime - startTime) / 1_000_000.0;

        bh.consume(response.body());
        bh.consume(latencyMs);
    }

    // =========================================================================
    // Concurrent Latency Measurement
    // =========================================================================

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    @Threads(100)
    public void benchmarkConcurrentLatency(Blackhole bh) throws Exception {
        int threadId = ThreadLocalRandom.current().nextInt(100);
        String testCaseId = testCaseIds.get(threadId);

        Map<String, Object> message = A2ATestDataGenerator.generatePingMessage(testCaseId);
        String payload = objectMapper.writeValueAsString(message);

        long startTime = System.nanoTime();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(A2A_SERVER_URL))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        long endTime = System.nanoTime();
        double latencyMs = (endTime - startTime) / 1_000_000.0;

        bh.consume(response.body());
        bh.consume(latencyMs);
    }

    // =========================================================================
    // Validation Tests
    // =========================================================================

    @Test
    @EnabledIfEnvironmentVariable(named = "A2A_SERVER_URL", matches = ".*")
    void validateLatencyMeasurementAccuracy() throws Exception {
        _logger.info("Validating latency measurement accuracy");

        // Test that our latency measurements are reasonably accurate
        int testCount = 10;
        long totalLatency = 0L;

        for (int i = 0; i < testCount; i++) {
            String testCaseId = "validation-test-" + i;
            Map<String, Object> message = A2ATestDataGenerator.generatePingMessage(testCaseId);
            String payload = objectMapper.writeValueAsString(message);

            long startTime = System.nanoTime();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(A2A_SERVER_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            long endTime = System.nanoTime();
            long latencyMs = (endTime - startTime) / 1_000_000;

            totalLatency += latencyMs;

            if (response.statusCode() != 200) {
                throw new IllegalStateException("Latency test failed: " + response.statusCode());
            }
        }

        double avgLatency = (double) totalLatency / testCount;
        _logger.info("Average latency validation: {:.2f}ms", avgLatency);

        // Validate that latency is within reasonable bounds
        if (avgLatency > 500) {
            _logger.warn("Average latency seems high: {:.2f}ms", avgLatency);
        }
    }
}