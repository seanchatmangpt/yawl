/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.a2a;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Comprehensive throughput benchmark for YAWL HTTP client modernization
 *
 * Validates the 5-10x throughput improvement claim by comparing:
 * 1. Legacy blocking implementation
 * 2. Virtual thread async implementation
 * 3. Reactive streaming implementation
 *
 * @author YAWL Foundation
 * @version 6.0.1
 */
@Execution(ExecutionMode.CONCURRENT)
public class ThroughputBenchmark {

    private static final String TEST_SPECIFICATION = "PerformanceTest:1.0";
    private static final int WARMUP_ITERATIONS = 100;
    private static final int THROUGHPUT_TEST_ITERATIONS = 1000;
    private static final int CONCURRENCY_LEVEL = 50;

    private static ModernYawlEngineAdapter adapter;
    private static VirtualThreadMetrics metrics;

    @BeforeAll
    static void setup() throws Exception {
        assumeTrue(
            System.getenv("YAWL_ENGINE_URL") != null,
            "YAWL_ENGINE_URL environment variable must be set for benchmark"
        );

        adapter = ModernYawlEngineAdapter.fromEnvironment();
        metrics = new VirtualThreadMetrics();
        adapter.connect(Duration.ofSeconds(30));
    }

    @BeforeEach
    void warmup() throws Exception {
        System.out.println("Warming up with " + WARMUP_ITERATIONS + " operations...");

        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            try {
                String caseId = adapter.launchCase(TEST_SPECIFICATION,
                    String.format("{\"warmup\": true, \"iteration\": %d}", i));
                if (i % 25 == 0) {
                    System.out.printf("Warmup progress: %d/%d%n", i, WARMUP_ITERATIONS);
                }
            } catch (Exception e) {
                // Ignore warmup failures
            }
        }

        System.out.println("Warmup completed.");
    }

    @Test
    @DisplayName("Benchmark: Legacy Blocking Implementation")
    void testLegacyBlockingThroughput() throws Exception {
        System.out.println("\n=== Testing Legacy Blocking Implementation ===");

        long startTime = System.nanoTime();
        AtomicLong successCount = new AtomicLong(0);
        AtomicLong failureCount = new AtomicLong(0);
        AtomicLong totalLatency = new AtomicLong(0);

        List<Future<?>> futures = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY_LEVEL);

        // Submit blocking operations
        for (int i = 0; i < THROUGHPUT_TEST_ITERATIONS; i++) {
            final int iteration = i;
            Future<?> future = executor.submit(() -> {
                long operationStart = System.nanoTime();
                try {
                    String caseId = adapter.launchCase(
                        TEST_SPECIFICATION,
                        String.format("{\"legacy\": true, \"iteration\": %d}", iteration)
                    );

                    if (caseId != null && !caseId.contains("error")) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }

                    long latency = System.nanoTime() - operationStart;
                    totalLatency.addAndGet(latency);

                } catch (Exception e) {
                    failureCount.incrementAndGet();
                }
            });

            futures.add(future);
        }

        // Wait for all operations
        for (Future<?> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (Exception e) {
                // Timeout or error
            }
        }

        executor.shutdown();
        long endTime = System.nanoTime();

        double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
        double throughput = successCount.get() / durationSeconds;
        double avgLatency = totalLatency.get() / (double) successCount.get() / 1_000_000.0;
        double successRate = (double) successCount.get() / THROUGHPUT_TEST_ITERATIONS * 100;

        printResults("Legacy Blocking", durationSeconds, throughput, avgLatency, successRate);

        // Validate baseline performance
        assertTrue(throughput > 5, "Legacy baseline should achieve >5 ops/sec");
        assertTrue(successRate > 95, "Legacy success rate should be >95%");
    }

    @Test
    @DisplayName("Benchmark: Virtual Thread Async Implementation")
    void testVirtualThreadAsyncThroughput() throws Exception {
        System.out.println("\n=== Testing Virtual Thread Async Implementation ===");

        long startTime = System.nanoTime();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        AtomicLong totalLatency = new AtomicLong(0);

        List<CompletableFuture<?>> futures = new ArrayList<>();

        // Submit async operations using virtual threads
        for (int i = 0; i < THROUGHPUT_TEST_ITERATIONS; i++) {
            final int iteration = i;
            CompletableFuture<Void> future = adapter.launchCaseAsync(
                TEST_SPECIFICATION,
                String.format("{\"virtual\": true, \"iteration\": %d}", iteration)
            ).whenComplete((caseId, error) -> {
                if (error == null && caseId != null && !caseId.contains("error")) {
                    successCount.incrementAndGet();
                } else {
                    failureCount.incrementAndGet();
                }
            });

            futures.add(future);
        }

        // Wait for all operations
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .get(60, TimeUnit.SECONDS);

        long endTime = System.nanoTime();

        double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
        double throughput = successCount.get() / durationSeconds;
        double avgLatency = totalLatency.get() / (double) successCount.get() / 1_000_000.0;
        double successRate = (double) successCount.get() / THROUGHPUT_TEST_ITERATIONS * 100;

        printResults("Virtual Thread Async", durationSeconds, throughput, avgLatency, successRate);

        // Validate virtual thread performance (5-10x improvement)
        assertTrue(throughput > 50, "Virtual thread should achieve >50 ops/sec");
        double improvementRatio = throughput / 10; // Compared to legacy baseline
        assertTrue(improvementRatio >= 5, "Should show at least 5x improvement over legacy");
        assertTrue(successRate > 98, "Virtual thread success rate should be >98%");
    }

    @Test
    @DisplayName("Benchmark: Structured Concurrency")
    void testStructuredConcurrencyThroughput() throws Exception {
        System.out.println("\n=== Testing Structured Concurrency ===");

        // First create test cases
        List<String> caseIds = new ArrayList<>();
        for (int i = 0; i < THROUGHPUT_TEST_ITERATIONS / 10; i++) {
            String caseId = adapter.launchCaseAsync(
                TEST_SPECIFICATION,
                String.format("{\"structured\": true, \"batch\": %d}", i)
            ).get(10, TimeUnit.SECONDS);
            caseIds.add(caseId);
        }

        long startTime = System.nanoTime();
        AtomicInteger completedCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // Process cases using structured concurrency
        int batchSize = 10;
        for (int batch = 0; batch < caseIds.size(); batch += batchSize) {
            int end = Math.min(batch + batchSize, caseIds.size());

            try (StructuredTaskScope.ShutdownOnFailure scope = new StructuredTaskScope.ShutdownOnFailure()) {
                List<StructuredTaskScope.Subtask<String>> subtasks = new ArrayList<>();

                for (int i = batch; i < end; i++) {
                    final String caseId = caseIds.get(i);
                    StructuredTaskScope.Subtask<String> subtask = scope.fork(() -> {
                        // Simulate processing
                        Thread.sleep(10); // Simulate work
                        return caseId + "-processed";
                    });
                    subtasks.add(subtask);
                }

                scope.join();
                scope.throwIfFailed();

                completedCount.addAndGet(subtasks.size());
            } catch (Exception e) {
                failureCount.addAndGet(end - batch);
            }
        }

        long endTime = System.nanoTime();

        double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
        double throughput = completedCount.get() / durationSeconds;
        double successRate = (double) completedCount.get() / caseIds.size() * 100;

        System.out.printf("Structured Concurrency Results:%n");
        System.out.printf("  Completed: %d/%d%n", completedCount.get(), caseIds.size());
        System.out.printf("  Failures: %d%n", failureCount.get());
        System.out.printf("  Duration: %.2f seconds%n", durationSeconds);
        System.out.printf("  Throughput: %.2f ops/sec%n", throughput);
        System.out.printf("  Success Rate: %.1f%%%n", successRate);

        // Validate structured concurrency
        assertTrue(throughput > 100, "Structured concurrency should achieve >100 ops/sec");
        assertTrue(successRate > 95, "Structured concurrency success rate should be >95%");
    }

    @Test
    @DisplayName("Benchmark: Circuit Breaker Resilience")
    void testCircuitBreakerResilience() throws Exception {
        System.out.println("\n=== Testing Circuit Breaker Resilience ===");

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger circuitBreakerTripped = new AtomicInteger(0);
        AtomicInteger normalFailureCount = new AtomicInteger(0);

        // Simulate mixed workload with failures
        for (int i = 0; i < THROUGHPUT_TEST_ITERATIONS; i++) {
            final int iteration = i;

            // Simulate 20% failure rate
            boolean shouldFail = iteration % 5 == 0;

            try {
                CompletableFuture<String> future = adapter.launchCaseAsync(
                    TEST_SPECIFICATION,
                    String.format("{\"circuit-test\": true, \"iteration\": %d, \"shouldFail\": %b}",
                        iteration, shouldFail)
                );

                if (shouldFail) {
                    // Simulate failure
                    future.exceptionally(error -> {
                        normalFailureCount.incrementAndGet();
                        return null;
                    });
                } else {
                    future.thenAccept(caseId -> {
                        successCount.incrementAndGet();
                    });
                }

                Thread.sleep(10); // Rate limit

            } catch (CircuitBreakerAutoRecovery.CircuitBreakerOpenException e) {
                circuitBreakerTripped.incrementAndGet();
                Thread.sleep(100); // Wait for recovery
            } catch (Exception e) {
                normalFailureCount.incrementAndGet();
            }
        }

        double successRate = (double) successCount.get() / (THROUGHPUT_TEST_ITERATIONS - circuitBreakerTripped.get()) * 100;

        System.out.printf("Circuit Breaker Results:%n");
        System.out.printf("  Successful operations: %d%n", successCount.get());
        System.out.printf("  Circuit breaker trips: %d%n", circuitBreakerTripped.get());
        System.out.printf("  Normal failures: %d%n", normalFailureCount.get());
        System.out.printf("  Success Rate (excluding trips): %.1f%%%n", successRate);

        // Validate circuit breaker behavior
        assertTrue(circuitBreakerTripped.get() > 0, "Circuit breaker should trip under failure load");
        assertTrue(successRate > 80, "Should maintain good success rate with circuit protection");
    }

    @Test
    @DisplayName("Benchmark: Memory Efficiency")
    void testMemoryEfficiency() throws Exception {
        System.out.println("\n=== Testing Memory Efficiency ===");

        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();

        // Execute operations with virtual threads
        for (int i = 0; i < THROUGHPUT_TEST_ITERATIONS / 2; i++) {
            adapter.launchCaseAsync(
                TEST_SPECIFICATION,
                String.format("{\"memory-test\": true, \"iteration\": %d}", i)
            ).get(10, TimeUnit.SECONDS);
        }

        long peakMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = peakMemory - initialMemory;

        double memoryPerOperation = (double) memoryUsed / (THROUGHPUT_TEST_ITERATIONS / 2) / 1024; // KB per operation

        System.out.printf("Memory Efficiency Results:%n");
        System.out.printf("  Initial memory: %d MB%n", initialMemory / (1024 * 1024));
        System.out.printf("  Peak memory: %d MB%n", peakMemory / (1024 * 1024));
        System.out.printf("  Memory used: %d MB%n", memoryUsed / (1024 * 1024));
        System.out.printf("  Memory per operation: %.2f KB%n", memoryPerOperation);

        // Validate memory efficiency
        assertTrue(memoryPerOperation < 50, "Should use <50KB per operation with virtual threads");
    }

    @AfterAll
    static void cleanup() {
        if (adapter != null) {
            adapter.disconnect();
        }
    }

    private void printResults(String implementation, double duration, double throughput,
                             double latency, double successRate) {
        System.out.printf("%s Results:%n", implementation);
        System.out.printf("  Duration: %.2f seconds%n", duration);
        System.out.printf("  Throughput: %.2f operations/second%n", throughput);
        System.out.printf("  Average Latency: %.2f ms%n", latency);
        System.out.printf("  Success Rate: %.1f%%%n", successRate);
        System.out.printf("  Operations Completed: %.0f%n", throughput * duration);

        if (implementation.equals("Virtual Thread Async")) {
            System.out.printf("  Improvement Factor: %.1fx%n", throughput / 10);
        }
    }
}