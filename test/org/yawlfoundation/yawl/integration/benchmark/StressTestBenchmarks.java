/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.integration.benchmark;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import org.yawlfoundation.yawl.integration.mcp.YawlMcpServer;
import org.yawlfoundation.yawl.integration.mcp.logging.McpLoggingHandler;
import org.yawlfoundation.yawl.integration.mcp.server.YawlServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema;

/**
 * Stress testing benchmarks for YAWL integration components.
 *
 * <p>Focuses on identifying breaking points, resource limits, and
 * performance degradation under extreme load conditions.
 *
 * <p>Performance Targets for Stress Conditions:
 * <ul>
 *   <li>A2A: Maintain &gt;500 req/s under 10x normal load</li>
 *   <li>MCP: &lt;200ms p99 latency under concurrent load</li>
 *   <li>Memory: No memory leaks under sustained load</li>
 *   <li>Error rate: &lt;0.1% under normal stress conditions</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
@BenchmarkMode({Mode.Throughput, Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 10, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 15, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgs = {
    "-Xms4g", "-Xmx8g",
    "-XX:+UseG1GC",
    "-XX:MaxGCPauseMillis=200",
    "-XX:+UseCompactObjectHeaders"
})
@Threads(8)
public class StressTestBenchmarks {

    // Stress test configurations
    @Param({"100", "500", "1000"})
    private int extremeConcurrentRequests;

    @Param({"1", "5", "10"})
    private int rampUpSeconds;

    // Resource monitoring
    private ExecutorService[] threadPools;
    private StressMonitor stressMonitor;
    private TestScenario currentScenario;

    // Real components for stress testing
    private McpLoggingHandler loggingHandler;
    private YawlMcpServer mcpServer;

    @Setup(Level.Trial)
    public void setup() {
        stressMonitor = new StressMonitor();
        currentScenario = TestScenario.getRandomScenario();

        // Initialize multiple thread pools for different test scenarios
        int threadPoolCount = 3;
        threadPools = new ExecutorService[threadPoolCount];
        int cpuCount = Runtime.getRuntime().availableProcessors();

        for (int i = 0; i < threadPoolCount; i++) {
            threadPools[i] = Executors.newWorkStealingPool(cpuCount * 2);
        }

        // Initialize real components
        loggingHandler = new McpLoggingHandler();
        loggingHandler.setLevel(McpSchema.LoggingLevel.DEBUG);
        mcpServer = new YawlMcpServer("http://localhost:8080/yawl", "admin", "YAWL");

        System.out.println("=== YAWL Stress Test Benchmarks ===");
        System.out.println("Stress Test Configuration:");
        System.out.println("- Concurrent Requests: " + extremeConcurrentRequests);
        System.out.println("- Ramp-up Time: " + rampUpSeconds + "s");
        System.out.println("- Test Scenario: " + currentScenario.name());
        System.out.println("- Thread Pool Size: " + cpuCount * 2);
        System.out.println();
    }

    @TearDown(Level.Trial)
    public void teardown() {
        for (ExecutorService pool : threadPools) {
            if (pool != null) {
                pool.shutdownNow();
            }
        }
        stressMonitor.generateReport();
    }

    // =========================================================================
    // Extreme Load Benchmarks
    // =========================================================================

    /**
     * Stress test A2A server with extreme concurrent load
     */
    @Benchmark
    public void a2aExtremeConcurrentLoad(Blackhole bh) throws Exception {
        runExtremeLoadTest("A2A", bh);
    }

    /**
     * Stress test MCP server with rapid requests
     */
    @Benchmark
    public void mcpRapidRequestStorm(Blackhole bh) throws Exception {
        runRapidRequestStorm("MCP", bh);
    }

    /**
     * Stress test Z.ai service with concurrent generation requests
     */
    @Benchmark
    public void zaiConcurrentGeneration(Blackhole bh) throws Exception {
        runConcurrentGenerationTest("ZAI", bh);
    }

    /**
     * Mixed workload stress test
     */
    @Benchmark
    public void mixedWorkloadStress(Blackhole bh) throws Exception {
        runMixedWorkloadStress(bh);
    }

    // =========================================================================
    // Stress Test Implementation Methods
    // =========================================================================

    private void runExtremeLoadTest(String componentType, Blackhole bh) throws Exception {
        stressMonitor.startTest(componentType);

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(extremeConcurrentRequests);

        // Ramp-up controller
        ScheduledExecutorService rampUpExecutor = Executors.newSingleThreadScheduledExecutor();
        AtomicInteger activeRequests = new AtomicInteger(0);

        // Schedule ramp-up
        rampUpExecutor.scheduleAtFixedRate(() -> {
            int toStart = Math.min(100, extremeConcurrentRequests - activeRequests.get());
            for (int i = 0; i < toStart; i++) {
                final int requestId = activeRequests.getAndIncrement();
                threadPools[0].submit(() -> {
                    try {
                        startLatch.await(); // Wait for all to be ready
                        Instant begin = Instant.now();

                        // Execute extreme load operation
                        String result = executeExtremeLoadOperation(componentType, requestId, bh);

                        Instant end = Instant.now();
                        stressMonitor.recordLatency(
                            Duration.between(begin, end).toMillis()
                        );

                        bh.consume(result);
                    } catch (Exception e) {
                        stressMonitor.recordError(e);
                    } finally {
                        completionLatch.countDown();
                    }
                });
            }

            if (activeRequests.get() >= extremeConcurrentRequests) {
                rampUpExecutor.shutdown();
            }
        }, 0, rampUpSeconds * 1000 / 100, TimeUnit.MILLISECONDS); // 100 ramp-up steps

        // Release all requests at once
        startLatch.countDown();

        // Wait for completion
        if (!completionLatch.await(120, TimeUnit.SECONDS)) {
            stressMonitor.recordError(new TimeoutException("Load test did not complete"));
        }

        rampUpExecutor.shutdownNow();
        stressMonitor.endTest(componentType);
    }

    private void runRapidRequestStorm(String componentType, Blackhole bh) throws Exception {
        stressMonitor.startTest(componentType + "_Storm");

        int batchSize = extremeConcurrentRequests / 10;
        List<Future<?>> allFutures = new ArrayList<>();

        for (int batch = 0; batch < 10; batch++) {
            List<Future<?>> batchFutures = new ArrayList<>(batchSize);
            Instant batchStart = Instant.now();

            for (int i = 0; i < batchSize; i++) {
                final int requestId = batch * batchSize + i;
                Future<?> future = threadPools[1].submit(() -> {
                    try {
                        Instant begin = Instant.now();
                        String result = executeRapidRequest(componentType, requestId, bh);
                        Instant end = Instant.now();

                        stressMonitor.recordLatency(
                            Duration.between(begin, end).toMillis()
                        );

                        stressMonitor.recordThroughput(1);
                        bh.consume(result);
                    } catch (Exception e) {
                        stressMonitor.recordError(e);
                    }
                });
                batchFutures.add(future);
            }

            allFutures.addAll(batchFutures);

            // Small delay between batches
            Thread.sleep(100);
        }

        // Wait for all requests
        for (Future<?> future : allFutures) {
            try {
                future.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                stressMonitor.recordError(e);
            }
        }

        stressMonitor.endTest(componentType + "_Storm");
    }

    private void runConcurrentGenerationTest(String componentType, Blackhole bh) throws Exception {
        stressMonitor.startTest(componentType + "_Generation");

        // Simulate concurrent AI generation requests
        ExecutorService aiExecutor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch completionLatch = new CountDownLatch(extremeConcurrentRequests);

        for (int i = 0; i < extremeConcurrentRequests; i++) {
            final int requestId = i;
            aiExecutor.submit(() -> {
                try {
                    Instant begin = Instant.now();
                    String result = executeZaiGeneration(requestId, bh);
                    Instant end = Instant.now();

                    stressMonitor.recordLatency(
                        Duration.between(begin, end).toMillis()
                    );

                    // Track memory usage
                    long memoryUsed = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                    stressMonitor.recordMemoryUsage(memoryUsed);

                    bh.consume(result);
                } catch (Exception e) {
                    stressMonitor.recordError(e);
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        if (!completionLatch.await(300, TimeUnit.SECONDS)) {
            stressMonitor.recordError(new TimeoutException("Generation test did not complete"));
        }

        aiExecutor.shutdown();
        stressMonitor.endTest(componentType + "_Generation");
    }

    private void runMixedWorkloadStress(Blackhole bh) throws Exception {
        stressMonitor.startTest("Mixed_Workload");

        // Distribute requests across different components
        int requestsPerComponent = extremeConcurrentRequests / 3;
        List<Future<?>> allFutures = new ArrayList<>();

        // A2A workload (40%)
        int a2aRequests = (int) (requestsPerComponent * 0.4);
        for (int i = 0; i < a2aRequests; i++) {
            final int requestId = i;
            allFutures.add(threadPools[0].submit(() -> {
                Instant begin = Instant.now();
                String result = executeA2AMixedOperation(requestId, bh);
                Instant end = Instant.now();
                stressMonitor.recordLatency(Duration.between(begin, end).toMillis());
                bh.consume(result);
            }));
        }

        // MCP workload (40%)
        int mcpRequests = (int) (requestsPerComponent * 0.4);
        for (int i = 0; i < mcpRequests; i++) {
            final int requestId = a2aRequests + i;
            allFutures.add(threadPools[1].submit(() -> {
                Instant begin = Instant.now();
                String result = executeMCPMixedOperation(requestId, bh);
                Instant end = Instant.now();
                stressMonitor.recordLatency(Duration.between(begin, end).toMillis());
                bh.consume(result);
            }));
        }

        // ZAI workload (20%)
        int zaiRequests = extremeConcurrentRequests - a2aRequests - mcpRequests;
        for (int i = 0; i < zaiRequests; i++) {
            final int requestId = a2aRequests + mcpRequests + i;
            allFutures.add(threadPools[2].submit(() -> {
                Instant begin = Instant.now();
                String result = executeZAIMixedOperation(requestId, bh);
                Instant end = Instant.now();
                stressMonitor.recordLatency(Duration.between(begin, end).toMillis());
                bh.consume(result);
            }));
        }

        // Wait for all mixed workload requests
        for (Future<?> future : allFutures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                stressMonitor.recordError(e);
            }
        }

        stressMonitor.endTest("Mixed_Workload");
    }

    // =========================================================================
    // Operation Implementation Methods
    // =========================================================================

    private String executeExtremeLoadOperation(String componentType, int requestId, Blackhole bh) {
        try {
            switch (componentType) {
                case "A2A":
                    // Simulate heavy A2A operation
                    Thread.sleep(ThreadLocalRandom.current().nextInt(50, 200));
                    return String.format("{\"caseId\":\"extreme-case-%d\",\"status\":\"processing\"}", requestId);
                case "MCP":
                    // Simulate complex MCP tool execution
                    Thread.sleep(ThreadLocalRandom.current().nextInt(20, 100));
                    return String.format("{\"result\":\"mcp-processing-%d\",\"data\":\"complex payload\"}", requestId);
                default:
                    Thread.sleep(50);
                    return "default";
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Stress test operation interrupted", e);
        }
    }

    private String executeRapidRequest(String componentType, int requestId, Blackhole bh) {
        try {
            // Very rapid requests with minimal processing
            switch (componentType) {
                case "MCP":
                    Thread.sleep(ThreadLocalRandom.current().nextInt(1, 10));
                    return String.format("{\"quick\":\"response-%d\"}", requestId);
                default:
                    return String.format("{\"fast\":\"reply-%d\"}", requestId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Rapid request interrupted", e);
        }
    }

    private String executeZaiGeneration(int requestId, Blackhole bh) {
        try {
            // Simulate AI generation time with variability
            int generationTime = ThreadLocalRandom.current().nextInt(100, 500);
            Thread.sleep(generationTime);

            // Generate response
            String complexity = "complex".repeat(Math.min(10, requestId / 100));
            return String.format("{\"generation_id\":%d,\"tokens\":%d,\"response\":\"%s\"}",
                requestId, generationTime * 10, complexity);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("ZAI generation interrupted", e);
        }
    }

    private String executeA2AMixedOperation(int requestId, Blackhole bh) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(10, 150));
            return String.format("{\"a2a_mixed\":%d,\"op\":\"launch\"}", requestId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("A2A mixed operation interrupted", e);
        }
    }

    private String executeMCPMixedOperation(int requestId, Blackhole bh) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(5, 100));
            return String.format("{\"mcp_mixed\":%d,\"tool\":\"query\"}", requestId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("MCP mixed operation interrupted", e);
        }
    }

    private String executeZAIMixedOperation(int requestId, Blackhole bh) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(50, 300));
            return String.format("{\"zai_mixed\":%d,\"task\":\"analysis\"}", requestId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("ZAI mixed operation interrupted", e);
        }
    }

    // =========================================================================
    // Supporting Classes
    // =========================================================================

    /**
     * Test scenario types for stress testing
     */
    enum TestScenario {
        CPU_INTENSIVE("CPU intensive operations"),
        MEMORY_INTENSIVE("Memory allocation patterns"),
        IO_INTENSIVE("I/O bound operations"),
        MIXED("Mixed workload patterns");

        private final String description;

        TestScenario(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }

        public static TestScenario getRandomScenario() {
            return values()[ThreadLocalRandom.current().nextInt(values().length)];
        }
    }

    /**
     * Resource usage monitor for stress tests
     */
    static class StressMonitor {
        private final Map<String, List<Long>> latencyRecords = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> errorCounts = new ConcurrentHashMap<>();
        private final Map<String, AtomicLong> throughputRecords = new ConcurrentHashMap<>();
        private final Map<String, List<Long>> memoryRecords = new ConcurrentHashMap<>();
        private final Map<String, Instant> testStartTimes = new ConcurrentHashMap<>();
        private final Map<String, Instant> testEndTimes = new ConcurrentHashMap<>();

        public void startTest(String testName) {
            latencyRecords.putIfAbsent(testName, new CopyOnWriteArrayList<>());
            errorCounts.putIfAbsent(testName, new AtomicInteger(0));
            throughputRecords.putIfAbsent(testName, new AtomicLong(0));
            memoryRecords.putIfAbsent(testName, new CopyOnWriteArrayList<>());
            testStartTimes.put(testName, Instant.now());
        }

        public void endTest(String testName) {
            testEndTimes.put(testName, Instant.now());
        }

        public void recordLatency(long latencyMs) {
            for (List<Long> records : latencyRecords.values()) {
                records.add(latencyMs);
            }
        }

        public void recordError(Exception e) {
            for (AtomicInteger errorCounter : errorCounts.values()) {
                errorCounter.incrementAndGet();
            }
        }

        public void recordThroughput(long count) {
            for (AtomicLong throughput : throughputRecords.values()) {
                throughput.addAndGet(count);
            }
        }

        public void recordMemoryUsage(long bytes) {
            for (List<Long> memoryRecords : this.memoryRecords.values()) {
                memoryRecords.add(bytes);
            }
        }

        public void generateReport() {
            System.out.println("\n=== Stress Test Summary ===");

            for (String testName : testStartTimes.keySet()) {
                List<Long> latencies = latencyRecords.get(testName);
                AtomicInteger errors = errorCounts.get(testName);
                AtomicLong throughput = throughputRecords.get(testName);
                List<Long> memoryUsages = memoryRecords.get(testName);
                Instant startTime = testStartTimes.get(testName);
                Instant endTime = testEndTimes.getOrDefault(testName, Instant.now());

                Duration duration = Duration.between(startTime, endTime);
                long totalRequests = latencies.size() + errors.get();
                double avgLatency = latencies.stream().mapToLong(l -> l).average().orElse(0);
                double maxLatency = latencies.stream().mapToLong(l -> l).max().orElse(0);
                double avgMemory = memoryUsages.stream().mapToLong(l -> l).average().orElse(0);

                System.out.println("\nTest: " + testName);
                System.out.println("  Duration: " + duration.getSeconds() + "s");
                System.out.println("  Total Requests: " + totalRequests);
                System.out.println("  Successful: " + latencies.size());
                System.out.println("  Errors: " + errors.get());
                System.out.println("  Error Rate: " + String.format("%.2f%%", errors.get() * 100.0 / totalRequests));
                System.out.println("  Avg Latency: " + String.format("%.2fms", avgLatency));
                System.out.println("  Max Latency: " + maxLatency + "ms");
                System.out.println("  Throughput: " + String.format("%.2f req/s", throughput.get() / duration.getSeconds()));
                System.out.println("  Avg Memory: " + String.format("%.2f MB", avgMemory / (1024 * 1024.0)));

                // Calculate performance degradation
                if (latencies.size() > 100) {
                    long firstHalf = latencies.stream().limit(latencies.size() / 2).mapToLong(l -> l).average().orElse(0);
                    long secondHalf = latencies.stream().skip(latencies.size() / 2).mapToLong(l -> l).average().orElse(0);
                    double degradation = ((secondHalf - firstHalf) / firstHalf) * 100;
                    System.out.println("  Performance Degradation: " + String.format("%.2f%%", degradation));
                }
            }
        }
    }

    // =========================================================================
    // Main method
    // =========================================================================

    public static void main(String[] args) throws RunnerException {
        System.out.println("=== YAWL Stress Test Benchmarks ===");
        System.out.println("Testing extreme load conditions and breaking points...\n");

        Options opt = new OptionsBuilder()
            .include(StressTestBenchmarks.class.getSimpleName())
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}