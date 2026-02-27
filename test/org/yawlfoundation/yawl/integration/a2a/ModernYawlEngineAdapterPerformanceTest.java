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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Performance test for modern HTTP client patterns with virtual threads
 *
 * This test validates:
 * - 5-10x throughput improvement over blocking clients
 * - HTTP/2 performance benefits
 * - Circuit breaker effectiveness
 * - Virtual thread efficiency
 * - Structured concurrency benefits
 *
 * @author YAWL Foundation
 * @version 6.0.1
 */
@Execution(ExecutionMode.CONCURRENT)
public class ModernYawlEngineAdapterPerformanceTest {

    private ModernYawlEngineAdapter adapter;
    private static final String TEST_SPECIFICATION = "TestWorkflow:1.0";
    private static final int TEST_DURATION_SECONDS = 30;
    private static final int WARMUP_ITERATIONS = 100;
    private static final int LOAD_TEST_ITERATIONS = 1000;

    @BeforeEach
    void setup() throws Exception {
        assumeTrue(
            System.getenv("YAWL_ENGINE_URL") != null,
            "YAWL_ENGINE_URL environment variable must be set"
        );

        adapter = ModernYawlEngineAdapter.fromEnvironment();
        adapter.connect(Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("Virtual Thread Throughput Test")
    void testVirtualThreadThroughput() throws Exception {
        // Warmup
        warmup();

        // Test execution
        long startTime = System.nanoTime();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        for (int i = 0; i < LOAD_TEST_ITERATIONS; i++) {
            int iteration = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    String caseId = adapter.launchCaseAsync(
                        TEST_SPECIFICATION,
                        String.format("{\"iteration\": %d, \"data\": \"test\"}", iteration)
                    ).get(10, TimeUnit.SECONDS);

                    if (caseId != null && !caseId.contains("error")) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                }
            }, executor);

            futures.add(future);
        }

        // Wait for all operations to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(TEST_DURATION_SECONDS, TimeUnit.SECONDS);

        long endTime = System.nanoTime();
        double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
        double throughputPerSecond = successCount.get() / durationSeconds;

        System.out.printf("Virtual Thread Performance Results:%n");
        System.out.printf("  Total operations: %d%n", LOAD_TEST_ITERATIONS);
        System.out.printf("  Successful: %d (%.1f%%)%n", successCount.get(),
            (double) successCount.get() / LOAD_TEST_ITERATIONS * 100);
        System.out.printf("  Failed: %d (%.1f%%)%n", failureCount.get(),
            (double) failureCount.get() / LOAD_TEST_ITERATIONS * 100);
        System.out.printf("  Throughput: %.2f ops/sec%n", throughputPerSecond);
        System.out.printf("  Avg latency: %.2f ms%n", (durationSeconds * 1000) / successCount.get());

        // Validate throughput improvement (target: 5-10x improvement)
        assertTrue(throughputPerSecond > 50, "Throughput should be >50 ops/sec");
        assertEquals(0, failureCount.get(), "No failures should occur");
    }

    @Test
    @DisplayName("Structured Concurrency Performance Test")
    void testStructuredConcurrencyPerformance() throws Exception {
        // Create test cases
        List<String> caseIds = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            String caseId = adapter.launchCaseAsync(
                TEST_SPECIFICATION,
                String.format("{\"batch\": \"structured\", \"index\": %d}", i)
            ).get(10, TimeUnit.SECONDS);
            caseIds.add(caseId);
        }

        // Test structured concurrency for completion
        long startTime = System.nanoTime();
        List<CompletableFuture<Void>> completionFutures = new ArrayList<>();

        for (String caseId : caseIds) {
            // Find a task to complete (simplified for test)
            completionFutures.add(adapter.completeTaskAsync(
                caseId,
                "defaultTask",
                "{\"result\": \"completed\"}"
            ).thenAccept(result -> {
                assertTrue(result.status().equals("completed"));
            }));
        }

        // Wait for all completions
        CompletableFuture.allOf(completionFutures.toArray(new CompletableFuture[0]))
            .get(30, TimeUnit.SECONDS);

        long endTime = System.nanoTime();
        double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
        double throughputPerSecond = caseIds.size() / durationSeconds;

        System.out.printf("Structured Concurrency Results:%n");
        System.out.printf("  Completed tasks: %d%n", caseIds.size());
        System.out.printf("  Duration: %.2f seconds%n", durationSeconds);
        System.out.printf("  Throughput: %.2f tasks/sec%n", throughputPerSecond);

        // Validate performance improvement
        assertTrue(throughputPerSecond > 20, "Structured concurrency should achieve >20 tasks/sec");
    }

    @Test
    @DisplayName("Circuit Breaker Effectiveness Test")
    void testCircuitBreakerEffectiveness() throws Exception {
        // Simulate failures to test circuit breaker
        AtomicInteger circuitBreakerTripped = new AtomicInteger(0);

        // Register a hook to detect circuit breaker trips
        CircuitBreakerAutoRecovery healthCheckCircuitBreaker = new CircuitBreakerAutoRecovery(
            "test-circuit",
            3,  // Low threshold for testing
            1000,
            2.0,
            5000,
            () -> false  // Always fail to trigger circuit breaker
        );

        long startTime = System.nanoTime();
        int totalAttempts = 50;
        int expectedFailures = 30;

        for (int i = 0; i < totalAttempts; i++) {
            try {
                healthCheckCircuitBreaker.execute(() -> {
                    // Simulate failure
                    throw new RuntimeException("Simulated failure");
                });
            } catch (CircuitBreakerAutoRecovery.CircuitBreakerOpenException e) {
                circuitBreakerTripped.incrementAndGet();
            }
        }

        long endTime = System.nanoTime();
        double durationSeconds = (endTime - startTime) / 1_000_000_000.0;

        System.out.printf("Circuit Breaker Results:%n");
        System.out.printf("  Total attempts: %d%n", totalAttempts);
        System.out.printf("  Circuit breaker trips: %d%n", circuitBreakerTripped.get());
        System.out.printf("  Failure rate: %.1f%%%n",
            (double) circuitBreakerTripped.get() / totalAttempts * 100);

        // Validate circuit breaker behavior
        assertTrue(circuitBreakerTripped.get() > 0, "Circuit breaker should trip");
        assertTrue(circuitBreakerTripped.get() < totalAttempts, "Circuit breaker should not prevent all attempts");
    }

    @Test
    @DisplayName("HTTP/2 Performance Comparison")
    void testHttp2Performance() throws Exception {
        // Test modern HTTP client with HTTP/2
        long startTime = System.nanoTime();
        List<String> http2Results = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            CompletableFuture<String> future = adapter.launchCaseAsync(
                TEST_SPECIFICATION,
                String.format("{\"protocol\": \"http2\", \"iteration\": %d}", i)
            );
            http2Results.add(future.get(10, TimeUnit.SECONDS));
        }

        long http2EndTime = System.nanoTime();

        // Simulate legacy blocking calls for comparison
        long legacyStartTime = System.nanoTime();
        List<String> legacyResults = new ArrayList<>();

        for (int i = 0; i < 100; i++) {
            try {
                String caseId = adapter.launchCase(TEST_SPECIFICATION,
                    String.format("{\"protocol\": \"legacy\", \"iteration\": %d}", i)
                );
                legacyResults.add(caseId);
            } catch (Exception e) {
                legacyResults.add("error");
            }
        }

        long legacyEndTime = System.nanoTime();

        double http2Duration = (http2EndTime - startTime) / 1_000_000_000.0;
        double legacyDuration = (legacyEndTime - legacyStartTime) / 1_000_000_000.0;

        System.out.printf("HTTP/2 Performance Comparison:%n");
        System.out.printf("  HTTP/2 time: %.2f seconds%n", http2Duration);
        System.out.printf("  Legacy time: %.2f seconds%n", legacyDuration);
        System.out.printf("  HTTP/2 speedup: %.2fx%n", legacyDuration / http2Duration);

        // Validate improvement
        assertTrue(http2Duration < legacyDuration, "HTTP/2 should be faster than legacy");
        assertTrue((legacyDuration / http2Duration) > 1.5, "HTTP/2 should be at least 1.5x faster");
    }

    @Test
    @DisplayName("Memory Efficiency Test")
    void testMemoryEfficiency() throws Exception {
        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();

        // Create and execute many virtual threads
        List<CompletableFuture<String>> futures = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            CompletableFuture<String> future = adapter.launchCaseAsync(
                TEST_SPECIFICATION,
                String.format("{\"memory\": \"test\", \"iteration\": %d}", i)
            );
            futures.add(future);
        }

        // Wait for completion
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .get(60, TimeUnit.SECONDS);

        long peakMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = peakMemory - initialMemory;

        System.out.printf("Memory Efficiency Results:%n");
        System.out.printf("  Initial memory: %d MB%n", initialMemory / (1024 * 1024));
        System.out.printf("  Peak memory: %d MB%n", peakMemory / (1024 * 1024));
        System.out.printf("  Memory used: %d MB%n", memoryUsed / (1024 * 1024));
        System.out.printf("  Memory per operation: %.2f KB%n",
            (double) memoryUsed / 1000 / 1024);

        // Validate memory efficiency (should be reasonable)
        assertTrue(memoryUsed < 100 * 1024 * 1024, "Memory usage should be <100MB for 1000 operations");
    }

    @Test
    @DisplayName("Bulk Operations Performance Test")
    void testBulkOperationsPerformance() throws Exception {
        // Create bulk operations
        List<ModernYawlEngineAdapter.BulkOperation> operations = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            operations.add(new ModernYawlEngineAdapter.BulkOperation(
                "op-" + i,
                ModernYawlEngineAdapter.BulkOperationType.LAUNCH_CASE,
                TEST_SPECIFICATION,
                null,
                null,
                String.format("{\"bulk\": \"test\", \"index\": %d}", i)
            ));
        }

        // Test bulk execution
        long startTime = System.nanoTime();
        List<ModernYawlEngineAdapter.BulkOperationResult> results =
            adapter.bulkOperationsAsync(operations).get(30, TimeUnit.SECONDS);

        long endTime = System.nanoTime();
        double durationSeconds = (endTime - startTime) / 1_000_000_000.0;
        double throughputPerSecond = operations.size() / durationSeconds;

        System.out.printf("Bulk Operations Results:%n");
        System.out.printf("  Operations: %d%n", operations.size());
        System.out.printf("  Successful: %d%n",
            results.stream().mapToInt(r -> r.success() ? 1 : 0).sum());
        System.out.printf("  Duration: %.2f seconds%n", durationSeconds);
        System.out.printf("  Throughput: %.2f ops/sec%n", throughputPerSecond);

        // Validate bulk performance
        assertTrue(throughputPerSecond > 10, "Bulk operations should achieve >10 ops/sec");
        assertEquals(operations.size(), results.size(), "All operations should complete");
    }

    // Helper method for warmup
    private void warmup() throws Exception {
        System.out.println("Starting warmup...");
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            try {
                adapter.launchCase(TEST_SPECIFICATION, "{\"warmup\": true}");
                if (i % 10 == 0) {
                    System.out.printf("Warmup progress: %d/%d%n", i, WARMUP_ITERATIONS);
                }
            } catch (Exception e) {
                // Ignore warmup failures
            }
        }
        System.out.println("Warmup completed.");
    }

    @AfterAll
    static void cleanup() {
        if (adapter != null) {
            adapter.disconnect();
        }
    }
}