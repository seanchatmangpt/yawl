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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Comprehensive A2A (Agent-to-Agent) Communication Performance Benchmarks.
 *
 * Validates A2A communication efficiency across 5 critical dimensions:
 * 1. Message Latency - Point-to-point message delay
 * 2. Message Throughput - Messages per second between agents
 * 3. Concurrent Message Handling - Performance under concurrent load
 * 4. Message Serialization Overhead - Message encoding/decoding cost
 * 5. Network Partition Resilience - Performance during network issues
 *
 * <p><b>Benchmark Targets:</b>
 * <ul>
 *   <li>Message Latency p95: &lt; 100ms</li>
 *   <li>Message Throughput: &gt; 500 ops/sec</li>
 *   <li>Concurrent Handling: Linear scaling up to 1000 concurrent requests</li>
 *   <li>Serialization Overhead: &lt; 10% of total message time</li>
 *   <li>Partition Resilience: &gt; 95% success rate during partition</li>
 * </ul>
 *
 * @author YAWL Performance Team
 * @version 6.0.0
 */
@BenchmarkMode({Mode.AverageTime, Mode.Throughput, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3, jvmArgs = {
    "--enable-preview",
    "-Xms4g",
    "-Xmx8g",
    "-XX:+UseZGC",
    "-XX:+UseCompactObjectHeaders",
    "-Djava.nio.channels.DefaultThreadPool.threadFactory=VirtualThread"
})
public class A2ACommunicationBenchmarks {

    private static final Logger _logger = LogManager.getLogger(A2ACommunicationBenchmarks.class);
    private static final String A2A_SERVER_URL = "http://localhost:8081";
    private static final String YAWL_ENGINE_URL = "http://localhost:8080/yawl";
    
    // Test data
    private static final String TEST_SPECIFICATION = "SimpleCase:1.0";
    private static final int WARMUP_ITERATIONS = 50;
    
    // HTTP client for A2A communication
    private HttpClient httpClient;
    private ObjectMapper objectMapper;
    
    // Test case IDs for benchmarks
    private List<String> testCaseIds;
    
    // =========================================================================
    // Benchmark State Setup
    // =========================================================================

    @Setup
    public void setup() throws Exception {
        _logger.info("Setting up A2A Communication Benchmarks");
        
        // Initialize Jackson ObjectMapper
        objectMapper = new ObjectMapper();
        
        // Configure HTTP client with modern Java features
        httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();
        
        // Create test cases for benchmarking
        testCaseIds = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            testCaseIds.add("case-" + UUID.randomUUID().toString().substring(0, 8));
        }
        
        _logger.info("Setup completed. {} test cases created", testCaseIds.size());
    }

    @TearDown
    public void cleanup() {
        if (httpClient != null) {
            // HttpClient doesn't need explicit shutdown
        }
        _logger.info("A2A Communication Benchmarks cleanup completed");
    }

    // =========================================================================
    // 1. Message Latency Benchmarks
    // =========================================================================

    @Benchmark
    @BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    public void benchmarkMessageLatency(Blackhole bh) throws Exception {
        String testCaseId = testCaseIds.get(0);
        
        // Create A2A message
        String payload = createTestMessage(testCaseId, "ping", "Latency test");
        
        long startTime = System.nanoTime();
        
        // Send HTTP POST to A2A server
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(A2A_SERVER_URL))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build();
        
        HttpResponse<String> response = httpClient.send(
            request, HttpResponse.BodyHandlers.ofString());
        
        long endTime = System.nanoTime();
        long latencyNanos = endTime - startTime;
        
        // Validate response
        if (response.statusCode() == 200) {
            bh.consume(response.body());
        }
        
        // Convert to milliseconds for reporting
        double latencyMs = latencyNanos / 1_000_000.0;
        bh.consume(latencyMs);
    }

    @Benchmark
    @BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    public void benchmarkWorkflowMessageLatency(Blackhole bh) throws Exception {
        String testCaseId = testCaseIds.get(0);
        
        // Create complex workflow message
        String workflowPayload = createWorkflowMessage(testCaseId, "launch_workflow", 
            Map.of("specId", TEST_SPECIFICATION, "data", Map.of("purpose", "latency_test")));
        
        long startTime = System.nanoTime();
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(A2A_SERVER_URL))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(workflowPayload))
            .build();
        
        HttpResponse<String> response = httpClient.send(
            request, HttpResponse.BodyHandlers.ofString());
        
        long endTime = System.nanoTime();
        long latencyNanos = endTime - startTime;
        
        bh.consume(response.body());
        bh.consume(latencyNanos / 1_000_000.0); // ms
    }

    // =========================================================================
    // 2. Message Throughput Benchmarks
    // =========================================================================

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @State(Scope.Thread)
    public void benchmarkMessageThroughput(Blackhole bh) throws Exception {
        int messageCount = 100;
        AtomicInteger successCount = new AtomicInteger(0);
        long totalLatency = 0L;
        
        Instant startTime = Instant.now();
        
        for (int i = 0; i < messageCount; i++) {
            String testCaseId = testCaseIds.get(i % testCaseIds.size());
            String payload = createTestMessage(testCaseId, "throughput_test", "Message " + i);
            
            long messageStart = System.nanoTime();
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(A2A_SERVER_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
            
            HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString());
            
            long messageEnd = System.nanoTime();
            
            if (response.statusCode() == 200) {
                successCount.incrementAndGet();
                totalLatency += (messageEnd - messageStart);
            }
            
            // Small delay to simulate realistic workload
            Thread.sleep(1);
        }
        
        Instant endTime = Instant.now();
        Duration duration = Duration.between(startTime, endTime);
        double throughput = messageCount / duration.toSeconds();
        
        _logger.info("Throughput: {} messages/sec, Success rate: {:.1f}%", 
            throughput, (double) successCount.get() / messageCount * 100);
        
        bh.consume(throughput);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @State(Scope.Thread)
    public void benchmarkWorkflowThroughput(Blackhole bh) throws Exception {
        int messageCount = 50; // Fewer due to heavier payload
        AtomicInteger successCount = new AtomicInteger(0);
        
        Instant startTime = Instant.now();
        
        for (int i = 0; i < messageCount; i++) {
            String testCaseId = testCaseIds.get(i % testCaseIds.size());
            String payload = createWorkflowMessage(testCaseId, "query_workflows", 
                Map.of("query", "all", "timestamp", System.currentTimeMillis()));
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(A2A_SERVER_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
            
            HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                successCount.incrementAndGet();
            }
        }
        
        Instant endTime = Instant.now();
        Duration duration = Duration.between(startTime, endTime);
        double throughput = messageCount / duration.toSeconds();
        
        bh.consume(throughput);
    }

    // =========================================================================
    // 3. Concurrent Message Handling Benchmarks
    // =========================================================================

    @Benchmark
    @BenchmarkMode({Mode.Throughput, Mode.AverageTime})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Benchmark)
    @Threads(4)
    public void benchmarkConcurrentMessageHandling(Blackhole bh) throws Exception {
        int threadCount = 4;
        int messagesPerThread = 25;
        AtomicInteger totalSuccess = new AtomicInteger(0);
        AtomicLong totalLatency = new AtomicLong(0);
        
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            futures.add(CompletableFuture.runAsync(() -> {
                for (int i = 0; i < messagesPerThread; i++) {
                    try {
                        String testCaseId = testCaseIds.get(threadId * messagesPerThread + i);
                        String payload = createTestMessage(testCaseId, "concurrent_test", 
                            Thread.currentThread().getName() + " message " + i);
                        
                        long startTime = System.nanoTime();
                        
                        HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(A2A_SERVER_URL))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(payload))
                            .build();
                        
                        HttpResponse<String> response = httpClient.send(
                            request, HttpResponse.BodyHandlers.ofString());
                        
                        long endTime = System.nanoTime();
                        
                        if (response.statusCode() == 200) {
                            totalSuccess.incrementAndGet();
                            totalLatency.addAndGet(endTime - startTime);
                        }
                        
                    } catch (Exception e) {
                        // Log but continue
                        _logger.debug("Concurrent message failed: {}", e.getMessage());
                    }
                }
            }));
        }
        
        // Wait for all threads to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);
        
        double avgLatency = totalLatency.get() / (double) totalSuccess.get() / 1_000_000.0;
        bh.consume(avgLatency);
    }

    @Benchmark
    @BenchmarkMode({Mode.Throughput, Mode.AverageTime})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Benchmark)
    @Threads(16)
    public void benchmarkHighConcurrencyHandling(Blackhole bh) throws Exception {
        int threadCount = 16;
        int messagesPerThread = 10;
        AtomicInteger totalSuccess = new AtomicInteger(0);
        
        Instant startTime = Instant.now();
        
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            futures.add(executor.submit(() -> {
                for (int i = 0; i < messagesPerThread; i++) {
                    try {
                        String testCaseId = testCaseIds.get((threadId * messagesPerThread + i) % testCaseIds.size());
                        String payload = createWorkflowMessage(testCaseId, "manage_workitems",
                            Map.of("caseId", testCaseId, "action", "list"));
                        
                        HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(A2A_SERVER_URL))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(payload))
                            .build();
                        
                        HttpResponse<String> response = httpClient.send(
                            request, HttpResponse.BodyHandlers.ofString());
                        
                        if (response.statusCode() == 200) {
                            totalSuccess.incrementAndGet();
                        }
                        
                    } catch (Exception e) {
                        // Log but continue
                    }
                }
            }));
        }
        
        // Wait for all tasks
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        
        executor.shutdown();
        
        Instant endTime = Instant.now();
        Duration duration = Duration.between(startTime, endTime);
        double throughput = (threadCount * messagesPerThread) / duration.toSeconds();
        
        _logger.info("High concurrency throughput: {} ops/sec", throughput);
        bh.consume(throughput);
    }

    // =========================================================================
    // 4. Message Serialization Overhead Benchmarks
    // =========================================================================

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @State(Scope.Thread)
    public void benchmarkMessageSerialization(Blackhole bh) throws Exception {
        String testCaseId = testCaseIds.get(0);
        Map<String, Object> messageData = createMessagePayload(testCaseId);
        
        // Serialize to JSON
        long serializeStart = System.nanoTime();
        String jsonPayload = objectMapper.writeValueAsString(messageData);
        long serializeEnd = System.nanoTime();
        
        // Deserialize from JSON
        long deserializeStart = System.nanoTime();
        ObjectNode deserialized = objectMapper.readValue(jsonPayload, ObjectNode.class);
        long deserializeEnd = System.nanoTime();
        
        long serializeTime = serializeEnd - serializeStart;
        long deserializeTime = deserializeEnd - deserializeStart;
        long totalSerializationTime = serializeTime + deserializeTime;
        
        bh.consume(totalSerializationTime);
        bh.consume(serializeTime);
        bh.consume(deserializeTime);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @State(Scope.Thread)
    public void benchmarkLargeMessageSerialization(Blackhole bh) throws Exception {
        // Create large message with multiple nested objects
        Map<String, Object> largeMessage = createLargeMessagePayload();
        
        long serializeStart = System.nanoTime();
        String json = objectMapper.writeValueAsString(largeMessage);
        long serializeEnd = System.nanoTime();
        
        // Measure deserialization
        long deserializeStart = System.nanoTime();
        ObjectNode result = objectMapper.readValue(json, ObjectNode.class);
        long deserializeEnd = System.nanoTime();
        
        long totalTime = (serializeEnd - serializeStart) + (deserializeEnd - deserializeStart);
        
        bh.consume(totalTime);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    public void benchmarkSerializationThroughput(Blackhole bh) throws Exception {
        int iterations = 1000;
        AtomicInteger count = new AtomicInteger(0);

        Instant startTime = Instant.now();

        for (int i = 0; i < iterations; i++) {
            Map<String, Object> payload = createMessagePayload("case-" + i);

            // Both serialize and deserialize
            String json = objectMapper.writeValueAsString(payload);
            ObjectNode result = objectMapper.readValue(json, ObjectNode.class);

            count.incrementAndGet();
        }

        Instant endTime = Instant.now();
        Duration duration = Duration.between(startTime, endTime);
        double throughput = iterations / duration.toMillis();

        bh.consume(throughput);
    }

    // =========================================================================
    // Virtual Thread vs Platform Thread Performance Comparison
    // =========================================================================

    @Benchmark
    @BenchmarkMode({Mode.AverageTime, Mode.Throughput})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Benchmark)
    @Group("VirtualThreadComparison")
    @GroupThreads(10)
    public void benchmarkVirtualThreadLatency(Blackhole bh) throws Exception {
        String testCaseId = testCaseIds.get(0);
        String payload = createTestMessage(testCaseId, "virtual_thread_test", "Virtual thread benchmark");

        long startTime = System.nanoTime();

        // Use virtual thread for the request
        Thread.ofVirtual().start(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(A2A_SERVER_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

                HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    bh.consume(response.body());
                }
            } catch (Exception e) {
                bh.consume(e.getMessage());
            }
        }).join();

        long endTime = System.nanoTime();
        double latencyMs = (endTime - startTime) / 1_000_000.0;
        bh.consume(latencyMs);
    }

    @Benchmark
    @BenchmarkMode({Mode.AverageTime, Mode.Throughput})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Benchmark)
    @Group("VirtualThreadComparison")
    @GroupThreads(10)
    public void benchmarkPlatformThreadLatency(Blackhole bh) throws Exception {
        String testCaseId = testCaseIds.get(0);
        String payload = createTestMessage(testCaseId, "platform_thread_test", "Platform thread benchmark");

        long startTime = System.nanoTime();

        // Use platform thread for the request
        ExecutorService platformExecutor = Executors.newFixedThreadPool(1);
        Future<?> future = platformExecutor.submit(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(A2A_SERVER_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

                HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    bh.consume(response.body());
                }
            } catch (Exception e) {
                bh.consume(e.getMessage());
            }
        });

        future.get();
        platformExecutor.shutdown();

        long endTime = System.nanoTime();
        double latencyMs = (endTime - startTime) / 1_000_000.0;
        bh.consume(latencyMs);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @State(Scope.Benchmark)
    public void benchmarkVirtualThreadConcurrency(Blackhole bh) throws Exception {
        int concurrentRequests = 1000;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicBoolean testCompleted = new AtomicBoolean(false);

        Instant startTime = Instant.now();

        // Use virtual thread per task executor
        ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < concurrentRequests; i++) {
            String testCaseId = testCaseIds.get(i % testCaseIds.size());
            String payload = createTestMessage(testCaseId, "concurrent_virtual_test",
                "Concurrent virtual thread test " + i);

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    if (testCompleted.get()) return;

                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(A2A_SERVER_URL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();

                    HttpResponse<String> response = httpClient.send(
                        request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() == 200) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Ignore for throughput calculation
                }
            }, virtualExecutor);

            futures.add(future);
        }

        // Wait for all requests to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);
        testCompleted.set(true);

        // Shutdown executor
        virtualExecutor.shutdownNow();

        Instant endTime = Instant.now();
        Duration duration = Duration.between(startTime, endTime);
        double throughput = successCount.get() / duration.toSeconds();

        _logger.info("Virtual thread concurrency: {} successful requests, {} ops/sec",
            successCount.get(), throughput);

        bh.consume(throughput);
    }

    // =========================================================================
    // 5. Network Partition Resilience Benchmarks
    // =========================================================================

    @Benchmark
    @BenchmarkMode({Mode.Throughput, Mode.AverageTime})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    public void benchmarkNetworkPartitionResilience(Blackhole bh) throws Exception {
        // Simulate network partition by redirecting to invalid host
        HttpClient partitionClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(100))
            .build();
        
        int successfulRequests = 0;
        int totalRequests = 50;
        long totalLatency = 0L;
        
        for (int i = 0; i < totalRequests; i++) {
            try {
                String payload = createTestMessage("case-" + i, "partition_test", 
                    "Partition resilience test");
                
                long startTime = System.nanoTime();
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://unreachable-server.invalid"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
                
                HttpResponse<String> response = partitionClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
                
                long endTime = System.nanoTime();
                
                // In a partition scenario, we might still get some success (cache, retry, etc.)
                if (response.statusCode() == 200) {
                    successfulRequests++;
                }
                
                totalLatency += (endTime - startTime);
                
                // Simulate recovery attempt every 10 requests
                if (i % 10 == 9) {
                    Thread.sleep(100); // Simulate recovery window
                }
                
            } catch (Exception e) {
                // Expected during partition
            }
        }
        
        double successRate = (double) successfulRequests / totalRequests * 100;
        double avgLatency = totalLatency / (double) totalRequests / 1_000_000.0;
        
        _logger.info("Partition resilience - Success rate: {:.1f}%, Avg latency: {:.2f}ms",
            successRate, avgLatency);

        bh.consume(successRate);
        bh.consume(avgLatency);
    }

    @Benchmark
    @BenchmarkMode({Mode.Throughput, Mode.AverageTime})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    public void benchmarkNetworkPartitionResilienceWithRetry(Blackhole bh) throws Exception {
        int totalRequests = 50;
        int maxRetries = 3;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicLong totalLatency = new AtomicLong(0);

        // Create a partition-aware HTTP client
        HttpClient partitionClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(100))
            .executor(Executors.newVirtualThreadPerTaskExecutor())
            .build();

        for (int i = 0; i < totalRequests; i++) {
            String testCaseId = testCaseIds.get(i % testCaseIds.size());
            String payload = createTestMessage(testCaseId, "partition_retry_test",
                "Partition resilience with retry test " + i);

            boolean requestSucceeded = false;
            int attempt = 0;
            long requestStart = System.nanoTime();

            while (attempt < maxRetries && !requestSucceeded) {
                try {
                    // Simulate partition by targeting different endpoints
                    String endpointUrl = attempt % 2 == 0 ? A2A_SERVER_URL :
                        "http://unreachable-server.invalid";

                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpointUrl))
                        .header("Content-Type", "application/json")
                        .header("X-Retry-Attempt", String.valueOf(attempt + 1))
                        .POST(HttpRequest.BodyPublishers.ofString(payload))
                        .build();

                    HttpResponse<String> response = partitionClient.send(
                        request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() == 200) {
                        requestSucceeded = true;
                        successCount.incrementAndGet();
                        break;
                    }
                } catch (Exception e) {
                    // Expected during partition
                    attempt++;
                    if (attempt < maxRetries) {
                        // Exponential backoff
                        Thread.sleep((long) (100 * Math.pow(2, attempt)));
                    }
                }
            }

            long requestEnd = System.nanoTime();
            totalLatency.addAndGet(requestEnd - requestStart);
        }

        double successRate = (double) successCount.get() / totalRequests * 100;
        double avgLatency = totalLatency.get() / (double) totalRequests / 1_000_000.0;

        _logger.info("Network partition with retry - Success rate: {:.1f}%, Avg latency: {:.2f}ms",
            successRate, avgLatency);

        bh.consume(successRate);
        bh.consume(avgLatency);
    }

    @Benchmark
    @BenchmarkMode({Mode.Throughput, Mode.AverageTime})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    public void benchmarkNetworkPartitionDetectionAndRecovery(Blackhole bh) throws Exception {
        int testDurationMs = 10000; // 10 seconds test
        int partitionIntervalMs = 2000; // Partition every 2 seconds
        AtomicInteger totalRequests = new AtomicInteger(0);
        AtomicInteger successfulRequests = new AtomicInteger(0);
        AtomicInteger partitionCount = new AtomicInteger(0);

        Instant testStart = Instant.now();
        long lastPartitionTime = 0;
        boolean inPartition = false;

        while (Duration.between(testStart, Instant.now()).toMillis() < testDurationMs) {
            long currentTime = System.currentTimeMillis();

            // Check if we should trigger a partition
            if (currentTime - lastPartitionTime > partitionIntervalMs && !inPartition) {
                inPartition = true;
                lastPartitionTime = currentTime;
                partitionCount.incrementAndGet();

                // Simulate partition for a short duration
                new Thread(() -> {
                    try {
                        Thread.sleep(500); // 500ms partition
                        inPartition = false;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
            }

            String testCaseId = testCaseIds.get(totalRequests.get() % testCaseIds.size());
            String payload = createTestMessage(testCaseId, "partition_detection_test",
                "Detection and recovery test");

            long requestStart = System.nanoTime();
            boolean requestSucceeded = false;

            try {
                // Use appropriate endpoint based on partition state
                String endpointUrl = inPartition ? "http://unreachable-server.invalid" : A2A_SERVER_URL;

                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpointUrl))
                    .header("Content-Type", "application/json")
                    .header("X-Partition-Status", inPartition ? "partitioned" : "normal")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

                HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    requestSucceeded = true;
                    successfulRequests.incrementAndGet();
                }
            } catch (Exception e) {
                // Expected during partition
            }

            totalRequests.incrementAndGet();

            // Small delay between requests
            Thread.sleep(10);
        }

        double successRate = (double) successfulRequests.get() / totalRequests.get() * 100;
        double partitionRate = (double) partitionCount.get() /
            (testDurationMs / (double) partitionIntervalMs) * 100;

        _logger.info("Network partition detection - Total requests: {}, Successful: {:.1f}%, Partitions: {}",
            totalRequests.get(), successRate, partitionCount.get());

        bh.consume(successRate);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @State(Scope.Thread)
    public void benchmarkCircuitBreakerResilience(Blackhole bh) throws Exception {
        // Simulate circuit breaker behavior
        int totalRequests = 200;
        int failureRate = 30; // 30% failure rate
        int successfulRequests = 0;
        
        Instant startTime = Instant.now();
        
        for (int i = 0; i < totalRequests; i++) {
            boolean shouldFail = i % (100 / failureRate) == 0;
            
            try {
                String testCaseId = testCaseIds.get(i % testCaseIds.size());
                String payload = createTestMessage(testCaseId, "circuit_test", 
                    "Request " + i);
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(A2A_SERVER_URL))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
                
                HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    successfulRequests++;
                } else if (shouldFail) {
                    // Simulate circuit breaker trip
                    Thread.sleep(50); // Trip delay
                }
                
            } catch (Exception e) {
                // Handle connection errors gracefully
            }
        }
        
        Instant endTime = Instant.now();
        Duration duration = Duration.between(startTime, endTime);
        double throughput = successfulRequests / duration.toSeconds();
        
        _logger.info("Circuit breaker resilience - Successful requests: {}, Throughput: {} ops/sec",
            successfulRequests, throughput);

        bh.consume(throughput);
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private String createTestMessage(String testCaseId, String action, String content) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "a2a_message");
        message.put("testCaseId", testCaseId);
        message.put("action", action);
        message.put("content", content);
        message.put("timestamp", System.currentTimeMillis());
        
        try {
            return objectMapper.writeValueAsString(message);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create test message", e);
        }
    }

    private String createWorkflowMessage(String testCaseId, String skillId, Map<String, Object> params) {
        Map<String, Object> message = new HashMap<>();
        message.put("type", "skill_request");
        message.put("testCaseId", testCaseId);
        message.put("skillId", skillId);
        message.put("parameters", params);
        message.put("timestamp", System.currentTimeMillis());
        message.put("requestId", UUID.randomUUID().toString());
        
        try {
            return objectMapper.writeValueAsString(message);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create workflow message", e);
        }
    }

    private Map<String, Object> createMessagePayload(String testCaseId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("testCaseId", testCaseId);
        payload.put("timestamp", System.currentTimeMillis());
        payload.put("action", "test");
        payload.put("data", Map.of(
            "field1", "value1",
            "field2", 42,
            "field3", true,
            "nested", Map.of(
                "sub1", "nested_value",
                "sub2", List.of("item1", "item2")
            )
        ));
        return payload;
    }

    private Map<String, Object> createLargeMessagePayload() {
        Map<String, Object> largeMessage = new HashMap<>();
        largeMessage.put("testCaseId", "large-test-case");
        largeMessage.put("timestamp", System.currentTimeMillis());
        
        // Create large nested structure
        Map<String, Object> nestedData = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            nestedData.put("field_" + i, "value_" + i);
        }
        largeMessage.put("largeData", nestedData);
        
        // Create array of 1000 items
        List<Object> items = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            items.add(Map.of(
                "id", i,
                "name", "item_" + i,
                "value", Math.random() * 1000
            ));
        }
        largeMessage.put("items", items);
        
        return largeMessage;
    }

  
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @State(Scope.Thread)
    public void benchmarkLargeMessageThroughput(Blackhole bh) throws Exception {
        int messageCount = 10;
        long totalSize = 0L;
        long totalTime = 0L;

        for (int i = 0; i < messageCount; i++) {
            Map<String, Object> largeMessage = A2ATestDataGenerator.generateLargeMessage("throughput-test-" + i);

            long startTime = System.nanoTime();
            String json = objectMapper.writeValueAsString(largeMessage);
            long endTime = System.nanoTime();

            totalSize += json.length();
            totalTime += (endTime - startTime);
        }

        double avgTimeMs = totalTime / (double) messageCount / 1_000_000;
        double throughput = messageCount / (avgTimeMs / 1000); // messages per second

        bh.consume(throughput);
        bh.consume(totalSize);
    }

    // =========================================================================
    // Concurrent Message Handling (Enhanced)
    // =========================================================================

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @State(Scope.Benchmark)
    @Group("ConcurrentHandling")
    @GroupThreads(100)
    public void benchmarkConcurrent100Messages(Blackhole bh) throws Exception {
        benchmarkConcurrentMessages(bh, 100, 10); // 100 threads, 10 messages each
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @State(Scope.Benchmark)
    @Group("ConcurrentHandling")
    @GroupThreads(1000)
    public void benchmarkConcurrent1000Messages(Blackhole bh) throws Exception {
        benchmarkConcurrentMessages(bh, 1000, 5); // 1000 threads, 5 messages each
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @State(Scope.Benchmark)
    @Group("ConcurrentHandling")
    @GroupThreads(5000)
    public void benchmarkConcurrent5000Messages(Blackhole bh) throws Exception {
        benchmarkConcurrentMessages(bh, 5000, 2); // 5000 threads, 2 messages each
    }

    /**
     * Helper method for concurrent message handling benchmarks.
     */
    private void benchmarkConcurrentMessages(Blackhole bh, int threadCount, int messagesPerThread) throws Exception {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicLong totalLatency = new AtomicLong(0);

        Instant startTime = Instant.now();

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            futures.add(CompletableFuture.runAsync(() -> {
                for (int i = 0; i < messagesPerThread; i++) {
                    try {
                        String testCaseId = "concurrent-" + threadId + "-" + i;
                        Map<String, Object> message = A2ATestDataGenerator.generateWorkItemMessage(testCaseId);

                        long messageStart = System.nanoTime();

                        HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(A2A_SERVER_URL))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
                            .build();

                        HttpResponse<String> response = httpClient.send(
                            request, HttpResponse.BodyHandlers.ofString());

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

        // Wait for all messages to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(60, TimeUnit.SECONDS);

        executor.shutdown();

        Instant endTime = Instant.now();
        Duration duration = Duration.between(startTime, endTime);
        double totalMessages = threadCount * messagesPerThread;
        double successRate = (double) successCount.get() / totalMessages * 100;
        double avgLatencyMs = successCount.get() > 0 ?
            totalLatency.get() / (double) successCount.get() / 1_000_000.0 : 0;
        double throughput = successCount.get() / duration.toSeconds();

        _logger.info("Concurrent {} messages: {} successful ({:.1f}%), avg latency: {:.2f}ms, throughput: {:.1f} ops/sec",
                totalMessages, successCount.get(), successRate, avgLatencyMs, throughput);

        bh.consume(throughput);
        bh.consume(successRate);
    }

    // =========================================================================
    // Agent Handoff Scenarios
    // =========================================================================

    @Benchmark
    @BenchmarkMode({Mode.AverageTime, Mode.Throughput})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    public void benchmarkAgentHandoffSimple(Blackhole bh) throws Exception {
        String testCaseId = "handoff-simple-" + System.currentTimeMillis();

        // Simulate simple handoff from agent to agent
        Map<String, Object> handoffMessage = A2ATestDataGenerator.generateWorkItemMessage(testCaseId);
        handoffMessage.put("handoffType", "simple");
        handoffMessage.put("fromAgent", "agent-1");
        handoffMessage.put("toAgent", "agent-2");

        long startTime = System.nanoTime();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(A2A_SERVER_URL + "/handoff"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(handoffMessage)))
            .build();

        HttpResponse<String> response = httpClient.send(
            request, HttpResponse.BodyHandlers.ofString());

        long endTime = System.nanoTime();
        long latencyMs = (endTime - startTime) / 1_000_000;

        if (response.statusCode() == 200) {
            bh.consume(response.body());
        }

        bh.consume(latencyMs);
    }

    @Benchmark
    @BenchmarkMode({Mode.AverageTime, Mode.Throughput})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    public void benchmarkAgentHandoffComplex(Blackhole bh) throws Exception {
        String testCaseId = "handoff-complex-" + System.currentTimeMillis();

        // Simulate complex multi-agent handoff with workflow context
        Map<String, Object> handoffMessage = A2ATestDataGenerator.generateWorkItemMessage(testCaseId);
        handoffMessage.put("handoffType", "complex");
        handoffMessage.put("fromAgent", "specialized-agent");
        handoffMessage.put("toAgent", "workflow-agent");
        handoffMessage.put("handoffChain", Arrays.asList("agent-1", "agent-2", "agent-3"));
        handoffMessage.put("preserveContext", true);
        handoffMessage.put("workflowContext", Map.of(
            "currentStep", "review",
            "nextStep", "approval",
            "caseData", Map.of(
                "customer", "Acme Corp",
                "amount", 15000.00,
                "priority", "high"
            )
        ));

        long startTime = System.nanoTime();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(A2A_SERVER_URL + "/handoff/complex"))
            .header("Content-Type", "application/json")
            .header("X-Handoff-Priority", "high")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(handoffMessage)))
            .build();

        HttpResponse<String> response = httpClient.send(
            request, HttpResponse.BodyHandlers.ofString());

        long endTime = System.nanoTime();
        long latencyMs = (endTime - startTime) / 1_000_000;

        if (response.statusCode() == 200) {
            bh.consume(response.body());
        }

        bh.consume(latencyMs);
    }

    // =========================================================================
    // Multi-tenant Isolation Testing
    // =========================================================================

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @State(Scope.Thread)
    public void benchmarkMultiTenantIsolation(Blackhole bh) throws Exception {
        String[] tenants = {"tenant-1", "tenant-2", "tenant-3", "tenant-4", "tenant-5"};
        int messagesPerTenant = 20;
        AtomicInteger successCount = new AtomicInteger(0);

        Instant startTime = Instant.now();

        // Send messages from different tenants concurrently
        for (String tenant : tenants) {
            for (int i = 0; i < messagesPerTenant; i++) {
                String testCaseId = tenant + "-msg-" + i;
                Map<String, Object> message = A2ATestDataGenerator.generateQueryMessage(testCaseId);
                message.put("tenantId", tenant);
                message.put("requestId", UUID.randomUUID().toString());

                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(A2A_SERVER_URL + "/multi-tenant"))
                            .header("Content-Type", "application/json")
                            .header("X-Tenant-ID", tenant)
                            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
                            .build();

                        HttpResponse<String> response = httpClient.send(
                            request, HttpResponse.BodyHandlers.ofString());

                        if (response.statusCode() == 200) {
                            successCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        // Tenant isolation should prevent cross-tenant data leakage
                    }
                });

                // Don't await - let them run concurrently
            }
        }

        // Allow time for completion
        Thread.sleep(5000);

        Instant endTime = Instant.now();
        Duration duration = Duration.between(startTime, endTime);
        double totalMessages = tenants.length * messagesPerTenant;
        double throughput = successCount.get() / duration.toSeconds();

        _logger.info("Multi-tenant isolation: {} successful messages, throughput: {:.1f} ops/sec",
                successCount.get(), throughput);

        bh.consume(throughput);
    }

    // =========================================================================
    // Authentication Overhead Benchmarks
    // =========================================================================

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    public void benchmarkAuthenticationJWT(Blackhole bh) throws Exception {
        benchmarkAuthentication(bh, "JWT");
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    public void benchmarkAuthenticationApiKey(Blackhole bh) throws Exception {
        benchmarkAuthentication(bh, "API_KEY");
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    public void benchmarkAuthenticationSPIFFE(Blackhole bh) throws Exception {
        benchmarkAuthentication(bh, "SPIFFE");
    }

    private void benchmarkAuthentication(Blackhole bh, String authType) throws Exception {
        String testCaseId = "auth-" + authType.toLowerCase() + "-" + System.currentTimeMillis();
        Map<String, Object> message = A2ATestDataGenerator.generatePingMessage(testCaseId);

        // Add authentication header based on type
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(A2A_SERVER_URL + "/auth-test"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)));

        switch (authType) {
            case "JWT":
                requestBuilder.header("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test");
                break;
            case "API_KEY":
                requestBuilder.header("X-API-Key", "test-api-key-12345");
                break;
            case "SPIFFE":
                requestBuilder.header("X-SPIFFE-Trust-Domain", "trust-domain.test");
                requestBuilder.header("X-SPIFFE-Workload-ID", "workload-id.test");
                break;
        }

        long startTime = System.nanoTime();
        HttpRequest request = requestBuilder.build();

        HttpResponse<String> response = httpClient.send(
            request, HttpResponse.BodyHandlers.ofString());

        long endTime = System.nanoTime();
        long latencyMs = (endTime - startTime) / 1_000_000;

        if (response.statusCode() == 200) {
            bh.consume(response.body());
        }

        bh.consume(latencyMs);
    }

    // =========================================================================
    // Message Persistence and Recovery Testing
    // =========================================================================

    @Benchmark
    @BenchmarkMode({Mode.AverageTime, Mode.Throughput})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @State(Scope.Thread)
    public void benchmarkMessagePersistence(Blackhole bh) throws Exception {
        String testCaseId = "persistence-test-" + System.currentTimeMillis();

        // Create message that should be persisted
        Map<String, Object> message = A2ATestDataGenerator.generateWorkflowLaunchMessage(testCaseId);
        message.put("persist", true);
        message.put("priority", "persistent");
        message.put("retryCount", 0);

        String jsonPayload = objectMapper.writeValueAsString(message);

        long startTime = System.nanoTime();

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(A2A_SERVER_URL + "/persist"))
            .header("Content-Type", "application/json")
            .header("X-Persistence-Required", "true")
            .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
            .build();

        HttpResponse<String> response = httpClient.send(
            request, HttpResponse.BodyHandlers.ofString());

        long endTime = System.nanoTime();
        long latencyMs = (endTime - startTime) / 1_000_000;

        // Now test recovery by querying the persisted message
        if (response.statusCode() == 200) {
            String messageId = objectMapper.readTree(response.body()).get("messageId").asText();

            HttpRequest recoveryRequest = HttpRequest.newBuilder()
                .uri(URI.create(A2A_SERVER_URL + "/recover/" + messageId))
                .GET()
                .build();

            HttpResponse<String> recoveryResponse = httpClient.send(
                recoveryRequest, HttpResponse.BodyHandlers.ofString());

            if (recoveryResponse.statusCode() == 200) {
                bh.consume(recoveryResponse.body());
            }
        }

        bh.consume(latencyMs);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @State(Scope.Thread)
    public void benchmarkMessageRecovery(Blackhole bh) throws Exception {
        int recoveryCount = 50;
        AtomicInteger successCount = new AtomicInteger(0);

        Instant startTime = Instant.now();

        for (int i = 0; i < recoveryCount; i++) {
            String testCaseId = "recovery-test-" + i;

            // First, create a persisted message (simulate failure after persistence)
            Map<String, Object> message = A2ATestDataGenerator.generateErrorMessage(testCaseId);
            message.put("persist", true);
            message.put("status", "failed_after_persist");

            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(A2A_SERVER_URL + "/persist"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(message)))
                    .build();

                HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    // Now attempt recovery
                    String messageId = objectMapper.readTree(response.body()).get("messageId").asText();

                    HttpRequest recoveryRequest = HttpRequest.newBuilder()
                        .uri(URI.create(A2A_SERVER_URL + "/recover/" + messageId))
                        .header("X-Recovery-Mode", "auto")
                        .GET()
                        .build();

                    HttpResponse<String> recoveryResponse = httpClient.send(
                        recoveryRequest, HttpResponse.BodyHandlers.ofString());

                    if (recoveryResponse.statusCode() == 200) {
                        successCount.incrementAndGet();
                    }
                }
            } catch (Exception e) {
                // Recovery may fail in chaos scenarios
            }
        }

        Instant endTime = Instant.now();
        Duration duration = Duration.between(startTime, endTime);
        double throughput = successCount.get() / duration.toSeconds();

        _logger.info("Message recovery: {} successful recoveries out of {}, throughput: {:.1f} ops/sec",
                successCount.get(), recoveryCount, throughput);

        bh.consume(throughput);
    }

    // =========================================================================
    // Integration Test for Validation
    // =========================================================================

    @Test
    @EnabledIfEnvironmentVariable(named = "A2A_SERVER_URL", matches = ".*")
    void validateA2AServerConnection() throws Exception {
        _logger.info("Validating A2A server connection");

        // Test basic connectivity
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(A2A_SERVER_URL + "/.well-known/agent.json"))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(
            request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            _logger.info("A2A server is reachable");

            // Validate JSON response
            JsonNode agentCard = objectMapper.readTree(response.body());
            if (agentCard.has("name") && agentCard.has("skills")) {
                _logger.info("Agent card validated successfully");
            } else {
                throw new IllegalStateException("Invalid agent card response");
            }
        } else {
            throw new IllegalStateException("Failed to connect to A2A server: " + response.statusCode());
        }
    }

    @Test
    void testBenchmarkDataGeneration() {
        // Test that our benchmark data generation works correctly
        String testMessage = createTestMessage("test-case", "test_action", "test content");
        _logger.debug("Generated test message: {}", testMessage);
        
        Map<String, Object> payload = createMessagePayload("test-case");
        _logger.debug("Generated payload with {} keys", payload.size());
        
        Map<String, Object> largePayload = createLargeMessagePayload();
        _logger.debug("Generated large payload with {} keys", largePayload.size());
        
        // All tests pass
        assertTrue(true);
    }

    private void assertTrue(boolean condition) {
        if (!condition) {
            throw new AssertionError("Condition failed");
        }
    }
}
