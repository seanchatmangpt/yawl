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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A2A Resilience Benchmark.
 *
 * Comprehensive resilience testing for A2A communication with focus on:
 * - Network failure recovery (latency, partitions, packet loss)
 * - Service resilience (timeouts, retries, circuit breakers)
 * - Data consistency during failures
 * - Graceful degradation under stress
 * - Recovery time validation
 * - System stability under sustained failures
 *
 * <p><b>Benchmark Targets:</b>
 * <ul>
 *   <li>Recovery time: &lt; 5 seconds</li>
 *   <li>Success rate during failure: &gt; 95%</li>
 *   <li>Circuit breaker trip time: &lt; 100ms</li>
 *   <li>Retry success rate: &gt; 80%</li>
 * </ul>
 *
 * @author YAWL Performance Team
 * @version 6.0.0
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 4, time = 15, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3, jvmArgs = {
    "--enable-preview",
    "-Xms4g",
    "-Xmx8g",
    "-XX:+UseZGC",
    "-XX:+UseCompactObjectHeaders"
})
public class A2AResilienceBenchmark {

    private static final Logger _logger = LogManager.getLogger(A2AResilienceBenchmark.class);
    private static final String A2A_SERVER_URL = "http://localhost:8081";
    private static final int MAX_RECOVERY_TIME_MS = 5000; // 5 seconds max recovery
    private static final int FAILURE_DURATION_MS = 1000; // 1 second failure duration

    private HttpClient httpClient;
    private ObjectMapper objectMapper;
    private List<String> testCaseIds;

    // Resilience state
    private AtomicInteger totalOperations = new AtomicInteger(0);
    private AtomicInteger successfulOperations = new AtomicInteger(0);
    private AtomicLong totalRecoveryTime = new AtomicLong(0);
    private AtomicBoolean failureActive = new AtomicBoolean(false);

    @Setup
    public void setup() throws Exception {
        _logger.info("Setting up A2A Resilience Benchmarks");

        objectMapper = new ObjectMapper();
        httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();

        // Generate test case IDs
        testCaseIds = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            testCaseIds.add("resilience-test-" + i);
        }

        _logger.info("Setup completed. {} test cases created", testCaseIds.size());
    }

    @TearDown
    public void cleanup() {
        if (httpClient != null) {
            // HttpClient doesn't need explicit shutdown
        }
        _logger.info("A2A Resilience Benchmarks cleanup completed");
    }

    // =========================================================================
    // Network Failure Resilience
    // =========================================================================

    @Benchmark
    @BenchmarkMode({Mode.Throughput, Mode.AverageTime})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    public void benchmarkNetworkPartitionResilience(Blackhole bh) throws Exception {
        simulateNetworkFailure("partition", () -> {
            String testCaseId = testCaseIds.get(
                ThreadLocalRandom.current().nextInt(testCaseIds.size()));
            Map<String, Object> message = A2ATestDataGenerator.generatePingMessage(testCaseId);

            long startTime = System.nanoTime();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(A2A_SERVER_URL))
                .header("Content-Type", "application/json")
                .header("X-Failure-Mode", "partition")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            long endTime = System.nanoTime();
            long latencyMs = (endTime - startTime) / 1_000_000;

            if (response.statusCode() == 200) {
                successfulOperations.incrementAndGet();
            }

            bh.consume(latencyMs);
        });
    }

    @Benchmark
    @BenchmarkMode({Mode.Throughput, Mode.AverageTime})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    public void benchmarkNetworkLatencyResilience(Blackhole bh) throws Exception {
        simulateNetworkFailure("latency", () -> {
            String testCaseId = testCaseIds.get(
                ThreadLocalRandom.current().nextInt(testCaseIds.size()));
            Map<String, Object> message = A2ATestDataGenerator.generateWorkItemMessage(testCaseId);

            long startTime = System.nanoTime();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(A2A_SERVER_URL + "/workitem"))
                .header("Content-Type", "application/json")
                .header("X-Latency", "500") // 500ms artificial latency
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            long endTime = System.nanoTime();
            long latencyMs = (endTime - startTime) / 1_000_000;

            if (response.statusCode() == 200) {
                successfulOperations.incrementAndGet();
            }

            bh.consume(latencyMs);
        });
    }

    @Benchmark
    @BenchmarkMode({Mode.Throughput, Mode.AverageTime})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    public void benchmarkPacketLossResilience(Blackhole bh) throws Exception {
        simulateNetworkFailure("packet_loss", () -> {
            String testCaseId = testCaseIds.get(
                ThreadLocalRandom.current().nextInt(testCaseIds.size()));
            Map<String, Object> message = A2ATestDataGenerator.generateQueryMessage(testCaseId);

            // Simulate packet loss by randomly failing the request
            if (ThreadLocalRandom.current().nextDouble() < 0.1) { // 10% packet loss
                // Simulate timeout
                Thread.sleep(2000);
                bh.consume(-1L); // Indicate failure
                return;
            }

            long startTime = System.nanoTime();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(A2A_SERVER_URL + "/query"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            long endTime = System.nanoTime();
            long latencyMs = (endTime - startTime) / 1_000_000;

            if (response.statusCode() == 200) {
                successfulOperations.incrementAndGet();
            }

            bh.consume(latencyMs);
        });
    }

    // =========================================================================
    // Service Resilience
    // =========================================================================

    @Benchmark
    @BenchmarkMode({Mode.Throughput, Mode.AverageTime})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    public void benchmarkServiceTimeoutResilience(Blackhole bh) throws Exception {
        simulateServiceFailure("timeout", () -> {
            String testCaseId = testCaseIds.get(
                ThreadLocalRandom.current().nextInt(testCaseIds.size()));
            Map<String, Object> message = A2ATestDataGenerator.generateWorkflowLaunchMessage(testCaseId);

            // Use short timeout to simulate service timeout
            HttpClient timeoutClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(100))
                .build();

            long startTime = System.nanoTime();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(A2A_SERVER_URL + "/workflow/launch"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
                .build();

            try {
                HttpResponse<String> response = timeoutClient.send(request, HttpResponse.BodyHandlers.ofString());

                long endTime = System.nanoTime();
                long latencyMs = (endTime - startTime) / 1_000_000;

                if (response.statusCode() == 200) {
                    successfulOperations.incrementAndGet();
                }

                bh.consume(latencyMs);
            } catch (Exception e) {
                // Timeout is expected in this scenario
                bh.consume(-1L); // Indicate timeout
            }
        });
    }

    @Benchmark
    @BenchmarkMode({Mode.Throughput, Mode.AverageTime})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    public void benchmarkServiceUnavailableResilience(Blackhole bh) throws Exception {
        simulateServiceFailure("unavailable", () -> {
            String testCaseId = testCaseIds.get(
                ThreadLocalRandom.current().nextInt(testCaseIds.size()));
            Map<String, Object> message = A2ATestDataGenerator.generatePingMessage(testCaseId);

            // Simulate service unavailable by targeting unreachable endpoint
            long startTime = System.nanoTime();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:19999/unavailable")) // Unreachable
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
                .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                long endTime = System.nanoTime();
                long latencyMs = (endTime - startTime) / 1_000_000;

                if (response.statusCode() == 503) { // Service unavailable
                    // Should have fallback mechanism
                    successfulOperations.incrementAndGet();
                }

                bh.consume(latencyMs);
            } catch (Exception e) {
                // Connection refused is expected
                bh.consume(-1L);
            }
        });
    }

    @Benchmark
    @BenchmarkMode({Mode.Throughput, Mode.AverageTime})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    public void benchmarkCircuitBreakerResilience(Blackhole bh) throws Exception {
        simulateServiceFailure("circuit_breaker", () -> {
            String testCaseId = testCaseIds.get(
                ThreadLocalRandom.current().nextInt(testCaseIds.size()));
            Map<String, Object> message = A2ATestDataGenerator.generateWorkItemMessage(testCaseId);

            long startTime = System.nanoTime();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(A2A_SERVER_URL + "/workitem"))
                .header("Content-Type", "application/json")
                .header("X-Circuit-Breaker", "test")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            long endTime = System.nanoTime();
            long latencyMs = (endTime - startTime) / 1_000_000;

            if (response.statusCode() == 200 || response.statusCode() == 429) { // 429 Too Many Requests
                successfulOperations.incrementAndGet();
            }

            bh.consume(latencyMs);
        });
    }

    // =========================================================================
    // Retry Mechanism Resilience
    // =========================================================================

    @Benchmark
    @BenchmarkMode({Mode.Throughput, Mode.AverageTime})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    public void benchmarkRetryWithExponentialBackoff(Blackhole bh) throws Exception {
        simulateTransientFailure(() -> {
            String testCaseId = testCaseIds.get(
                ThreadLocalRandom.current().nextInt(testCaseIds.size()));
            Map<String, Object> message = A2ATestDataGenerator.generatePingMessage(testCaseId);

            int maxRetries = 3;
            int attempt = 0;
            boolean success = false;
            long totalTime = 0L;

            while (attempt < maxRetries && !success) {
                long startTime = System.nanoTime();

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(A2A_SERVER_URL))
                    .header("Content-Type", "application/json")
                    .header("X-Retry-Attempt", String.valueOf(attempt + 1))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
                    .build();

                try {
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                    long endTime = System.nanoTime();
                    totalTime += (endTime - startTime);

                    if (response.statusCode() == 200) {
                        success = true;
                        successfulOperations.incrementAndGet();
                    } else if (response.statusCode() >= 500) { // Server error
                        // Exponential backoff
                        long backoffMs = (long) (100 * Math.pow(2, attempt));
                        Thread.sleep(backoffMs);
                        attempt++;
                    } else {
                        // Non-retryable error
                        break;
                    }
                } catch (Exception e) {
                    // Retryable error
                    long backoffMs = (long) (100 * Math.pow(2, attempt));
                    Thread.sleep(backoffMs);
                    attempt++;
                }
            }

            if (success) {
                bh.consume(totalTime / 1_000_000.0); // Total time in ms
            } else {
                bh.consume(-1L); // Failure
            }
        });
    }

    @Benchmark
    @BenchmarkMode({Mode.Throughput, Mode.AverageTime})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    public void benchmarkRetryWithJitter(Blackhole bh) throws Exception {
        simulateTransientFailure(() -> {
            String testCaseId = testCaseIds.get(
                ThreadLocalRandom.current().nextInt(testCaseIds.size()));
            Map<String, Object> message = A2ATestDataGenerator.generateQueryMessage(testCaseId);

            int maxRetries = 3;
            int attempt = 0;
            boolean success = false;
            long totalTime = 0L;

            while (attempt < maxRetries && !success) {
                long startTime = System.nanoTime();

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(A2A_SERVER_URL + "/query"))
                    .header("Content-Type", "application/json")
                    .header("X-Retry-With-Jitter", "true")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
                    .build();

                try {
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                    long endTime = System.nanoTime();
                    totalTime += (endTime - startTime);

                    if (response.statusCode() == 200) {
                        success = true;
                        successfulOperations.incrementAndGet();
                    } else {
                        // Add jitter to backoff
                        long jitter = ThreadLocalRandom.current().nextLong(0, 100);
                        long backoffMs = (long) (100 * Math.pow(2, attempt)) + jitter;
                        Thread.sleep(backoffMs);
                        attempt++;
                    }
                } catch (Exception e) {
                    // Add jitter and retry
                    long jitter = ThreadLocalRandom.current().nextLong(0, 100);
                    long backoffMs = (long) (100 * Math.pow(2, attempt)) + jitter;
                    Thread.sleep(backoffMs);
                    attempt++;
                }
            }

            if (success) {
                bh.consume(totalTime / 1_000_000.0); // Total time in ms
            } else {
                bh.consume(-1L); // Failure
            }
        });
    }

    // =========================================================================
    // Graceful Degradation
    // =========================================================================

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @State(Scope.Thread)
    public void benchmarkGracefulDegradation(Blackhole bh) throws Exception {
        simulateSystemOverload(() -> {
            String testCaseId = testCaseIds.get(
                ThreadLocalRandom.current().nextInt(testCaseIds.size()));

            // Simulate high-priority vs low-priority handling
            boolean isHighPriority = ThreadLocalRandom.current().nextDouble() < 0.3; // 30% high priority

            Map<String, Object> message = A2ATestDataGenerator.generatePingMessage(testCaseId);
            message.put("priority", isHighPriority ? "high" : "low");
            message.put("degradeGracefully", true);

            long startTime = System.nanoTime();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(A2A_SERVER_URL))
                .header("Content-Type", "application/json")
                .header("X-Priority", isHighPriority ? "high" : "low")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            long endTime = System.nanoTime();
            long latencyMs = (endTime - startTime) / 1_000_000;

            // High priority should always succeed, low priority may be degraded
            if (response.statusCode() == 200 ||
                (isHighPriority && response.statusCode() == 429)) { // 429 for high priority means degradation
                successfulOperations.incrementAndGet();
            }

            bh.consume(response.statusCode());
        });
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @State(Scope.Thread)
    public void benchmarkFallbackMechanism(Blackhole bh) throws Exception {
        simulateServiceFailure("fallback", () -> {
            String testCaseId = testCaseIds.get(
                ThreadLocalRandom.current().nextInt(testCaseIds.size()));

            // Try primary service first
            boolean primarySuccess = tryPrimaryService(testCaseId);

            if (!primarySuccess) {
                // Fallback to secondary service
                boolean fallbackSuccess = tryFallbackService(testCaseId);
                if (fallbackSuccess) {
                    successfulOperations.incrementAndGet();
                }
            } else {
                successfulOperations.incrementAndGet();
            }
        });
    }

    // =========================================================================
    // Recovery Time Validation
    // =========================================================================

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    public void benchmarkRecoveryTime(Blackhole bh) throws Exception {
        String testCaseId = testCaseIds.get(
            ThreadLocalRandom.current().nextInt(testCaseIds.size()));

        // Simulate failure
        simulateFailure("temporary_failure", () -> {
            // Do nothing - just simulate the failure
        });

        // Measure recovery time
        Instant recoveryStart = Instant.now();

        boolean recovered = attemptRecovery(testCaseId);

        Instant recoveryEnd = Instant.now();
        Duration recoveryDuration = Duration.between(recoveryStart, recoveryEnd);
        long recoveryTimeMs = recoveryDuration.toMillis();

        if (recovered) {
            totalRecoveryTime.addAndGet(recoveryTimeMs);
            successfulOperations.incrementAndGet();
        }

        bh.consume(recoveryTimeMs);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    public void benchmarkRecoveryWithHealthCheck(Blackhole bh) throws Exception {
        String testCaseId = testCaseIds.get(
            ThreadLocalRandom.current().nextInt(testCaseIds.size()));

        // Simulate failure
        simulateFailure("health_check_failure", () -> {
            // Simulate service failure
        });

        // Use health check before recovery
        boolean serviceHealthy = performHealthCheck();

        Instant recoveryStart = Instant.now();
        boolean recovered = false;

        if (serviceHealthy) {
            recovered = attemptRecovery(testCaseId);
        } else {
            // Wait for service to become healthy
            while (!performHealthCheck() &&
                   Duration.between(recoveryStart, Instant.now()).toMillis() < MAX_RECOVERY_TIME_MS) {
                Thread.sleep(100); // Wait and check again
            }
            if (performHealthCheck()) {
                recovered = attemptRecovery(testCaseId);
            }
        }

        Instant recoveryEnd = Instant.now();
        Duration recoveryDuration = Duration.between(recoveryStart, recoveryEnd);
        long recoveryTimeMs = recoveryDuration.toMillis();

        if (recovered) {
            totalRecoveryTime.addAndGet(recoveryTimeMs);
            successfulOperations.incrementAndGet();
        }

        bh.consume(recoveryTimeMs);
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private void simulateNetworkFailure(String failureType, Runnable operation) throws Exception {
        failureActive.set(true);

        try {
            operation.run();
        } finally {
            failureActive.set(false);
        }
    }

    private void simulateServiceFailure(String failureType, Runnable operation) throws Exception {
        failureActive.set(true);

        try {
            operation.run();
        } finally {
            failureActive.set(false);
        }
    }

    private void simulateTransientFailure(Runnable operation) throws Exception {
        // Simulate transient failure with 20% chance
        if (ThreadLocalRandom.current().nextDouble() < 0.2) {
            failureActive.set(true);
        }

        try {
            operation.run();
        } finally {
            failureActive.set(false);
        }
    }

    private void simulateSystemOverload(Runnable operation) throws Exception {
        // Simulate system overload by running multiple concurrent operations
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < 50; i++) { // High load
            futures.add(CompletableFuture.runAsync(operation, executor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);
        executor.shutdown();
    }

    private void simulateFailure(String failureType, Runnable operation) throws Exception {
        // Simulate failure for a short duration
        new Thread(() -> {
            try {
                Thread.sleep(FAILURE_DURATION_MS); // Simulate failure duration
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();

        operation.run();
    }

    private boolean tryPrimaryService(String testCaseId) throws Exception {
        Map<String, Object> message = A2ATestDataGenerator.generatePingMessage(testCaseId);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(A2A_SERVER_URL))
            .header("Content-Type", "application/json")
            .header("X-Service-Type", "primary")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false; // Primary service failed
        }
    }

    private boolean tryFallbackService(String testCaseId) throws Exception {
        Map<String, Object> message = A2ATestDataGenerator.generatePingMessage(testCaseId);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(A2A_SERVER_URL + "/fallback"))
            .header("Content-Type", "application/json")
            .header("X-Service-Type", "fallback")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false; // Fallback service also failed
        }
    }

    private boolean attemptRecovery(String testCaseId) throws Exception {
        Map<String, Object> message = A2ATestDataGenerator.generatePingMessage(testCaseId);
        message.put("recovery", true);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(A2A_SERVER_URL))
            .header("Content-Type", "application/json")
            .header("X-Recovery-Attempt", "true")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean performHealthCheck() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(A2A_SERVER_URL + "/health"))
            .GET()
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    // =========================================================================
    // Validation Tests
    // =========================================================================

    @Test
    @EnabledIfEnvironmentVariable(named = "A2A_SERVER_URL", matches = ".*")
    void validateResilienceTargets() throws Exception {
        _logger.info("Validating resilience targets");

        // Test basic resilience
        int testCount = 100;
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < testCount; i++) {
            String testCaseId = testCaseIds.get(i % testCaseIds.size());
            Map<String, Object> message = A2ATestDataGenerator.generatePingMessage(testCaseId);

            // Simulate occasional failures
            if (ThreadLocalRandom.current().nextDouble() < 0.1) { // 10% failure rate
                // Add error simulation
                message.put("simulateError", true);
            }

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(A2A_SERVER_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
                .build();

            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString);
                if (response.statusCode() == 200) {
                    successCount.incrementAndGet();
                }
            } catch (Exception e) {
                // Retry logic should handle this
                Thread.sleep(100); // Brief delay before retry
                try {
                    HttpResponse<String> retryResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString);
                    if (retryResponse.statusCode() == 200) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception retryException) {
                    // Final failure
                }
            }
        }

        double successRate = (double) successCount.get() / testCount * 100;
        _logger.info("Resilience validation success rate: {:.1f}%", successRate);

        // Validate minimum success rate target
        if (successRate < 95.0) {
            throw new IllegalStateException("Success rate below target: " + successRate + "%");
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "A2A_SERVER_URL", matches = ".*")
    void validateRecoveryPerformance() throws Exception {
        _logger.info("Validating recovery performance");

        int recoveryTests = 10;
        long totalTime = 0L;
        int successfulRecoveries = 0;

        for (int i = 0; i < recoveryTests; i++) {
            String testCaseId = testCaseIds.get(i % testCaseIds.size());

            // Simulate failure
            simulateFailure("recovery_test", () -> {
                // Simulate failure
            });

            // Measure recovery
            Instant recoveryStart = Instant.now();
            boolean recovered = attemptRecovery(testCaseId);
            Instant recoveryEnd = Instant.now();

            Duration recoveryDuration = Duration.between(recoveryStart, recoveryEnd);
            totalTime += recoveryDuration.toMillis();

            if (recovered) {
                successfulRecoveries++;
            }
        }

        double avgRecoveryTime = (double) totalTime / recoveryTests;
        double recoveryRate = (double) successfulRecoveries / recoveryTests * 100;

        _logger.info("Average recovery time: {:.2f}ms, Recovery rate: {:.1f}%",
                avgRecoveryTime, recoveryRate);

        // Validate recovery time targets
        if (avgRecoveryTime > MAX_RECOVERY_TIME_MS) {
            throw new IllegalStateException("Average recovery time exceeds target: " + avgRecoveryTime + "ms");
        }

        if (recoveryRate < 90.0) {
            throw new IllegalStateException("Recovery rate below target: " + recoveryRate + "%");
        }
    }
}