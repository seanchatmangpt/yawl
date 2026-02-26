/*
 * Copyright (c) 2024-2025 YAWL Foundation
 *
 * This file is part of YAWL v6.0.0-GA.
 *
 * YAWL v6.0.0-GA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * YAWL v6.0.0-GA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL v6.0.0-GA. If not, see <http://www.gnu.org/licenses/>.
 */
package org.yawlfoundation.yawl.integration.java_python.performance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.integration.java_python.ValidationTestBase;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance baseline tests for Java-Python integration.
 * Establishes performance benchmarks to ensure Python implementations meet enterprise standards.
 *
 * @author YAWL Foundation
 * @since v6.0.0-GA
 */
@Execution(ExecutionMode.CONCURRENT)
public class PerformanceBaselinesTest extends ValidationTestBase {

    private static final int WARMUP_ITERATIONS = 100;
    private static final int BENCHMARK_ITERATIONS = 1000;
    private static final int MAX_CONCURRENT_TASKS = 100;

    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        assumeTrue(graalpyAvailable, "GraalPy required for performance tests");
    }

    @Test
    @DisplayName("Execution Time Baseline: Simple Operations")
    void testSimpleOperationsExecutionTime() throws Exception {
        Map<String, Long> results = new HashMap<>();

        // Test various simple operations
        results.put("Integer arithmetic", benchmarkSimpleIntegerArithmetic());
        results.put("String concatenation", benchmarkStringConcatenation());
        results.put("List operations", benchmarkListOperations());
        results.put("Dictionary operations", benchmarkDictionaryOperations());

        // Validate all operations are within acceptable limits
        results.forEach((operation, timeMs) -> {
            assertThat("Performance baseline for " + operation, timeMs, lessThanOrEqualTo(5L));
        });
    }

    @Test
    @DisplayName("Memory Usage Baseline: Object Creation")
    void testMemoryUsageBaseline() throws Exception {
        // Test memory allocation patterns
        long baselineMemory = getCurrentMemoryUsage();

        // Create many objects
        List<Object> objects = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            executePythonCode(f"obj_{i} = {i}");
            objects.add(executePythonCode(f"obj_{i}"));
        }

        long memoryAfter = getCurrentMemoryUsage();
        long memoryIncrease = memoryAfter - baselineMemory;

        // Memory should increase linearly with object count
        assertThat("Memory allocation efficiency", memoryIncrease, lessThan(50_000_000L)); // 50MB
        assertThat("Object creation success", objects.size(), equalTo(1000));
    }

    @Test
    @DisplayName("Concurrency Baseline: Parallel Execution")
    void testConcurrencyBaseline() throws Exception {
        int threadCount = Runtime.getRuntime().availableProcessors();
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        long startTime = System.nanoTime();

        // Submit concurrent tasks
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 100; j++) {
                        executePythonCode(f"result = {j} * 2");
                        Object result = executePythonCode("result");
                        assertTrue(areEquivalent(j * 2, result));
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        long duration = System.nanoTime() - startTime;

        // Verify concurrency performance
        long averageTimePerTask = TimeUnit.MILLISECONDS.convert(duration, TimeUnit.NANOSECONDS) / (threadCount * 100);
        assertThat("Concurrent task performance", averageTimePerTask, lessThan(10L));

        executor.shutdown();
    }

    @Test
    @DisplayName("Throughput Baseline: Requests per Second")
    void testThroughputBaseline() throws Exception {
        // Warm up
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            executePythonCode("result = 'test'");
        }

        // Benchmark throughput
        int successfulRequests = 0;
        long startTime = System.nanoTime();

        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            try {
                executePythonCode(f"result = {i}");
                Object result = executePythonCode("result");
                if (areEquivalent(i, result)) {
                    successfulRequests++;
                }
            } catch (Exception e) {
                // Continue with next request
            }
        }

        long duration = System.nanoTime() - startTime;
        double throughput = (double) successfulRequests / TimeUnit.MILLISECONDS.convert(duration, TimeUnit.NANOSECONDS) * 1000;

        // Validate throughput meets enterprise standards (1000+ RPS)
        assertThat("Throughput baseline", throughput, greaterThan(1000.0));
        assertThat("Request success rate", (double) successfulRequests / BENCHMARK_ITERATIONS, greaterThan(0.95));
    }

    @Test
    @DisplayName("Latency Baseline: P99 Latency")
    void testLatencyBaseline() throws Exception {
        List<Long> latencies = new ArrayList<>(BENCHMARK_ITERATIONS);

        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long startTime = System.nanoTime();
            try {
                executePythonCode("result = 'latency_test'");
                Object result = executePythonCode("result");
                long endTime = System.nanoTime();

                if (areEquivalent("latency_test", result)) {
                    latencies.add(TimeUnit.MILLISECONDS.convert(endTime - startTime, TimeUnit.NANOSECONDS));
                }
            } catch (Exception e) {
                // Continue with next request
            }
        }

        // Calculate P99 latency
        Collections.sort(latencies);
        double p99Latency = latencies.get((int) (latencies.size() * 0.99));

        assertThat("P99 latency", p99Latency, lessThan(50.0));
        assertThat("Average latency", latencies.stream().mapToLong(l -> l).average().orElse(0), lessThan(10.0));
    }

    @Test
    @DisplayName("Load Testing: Concurrent Workflows")
    void testLoadTesting() throws Exception {
        int concurrentWorkflows = Math.min(MAX_CONCURRENT_TASKS, Runtime.getRuntime().availableProcessors() * 2);
        ExecutorService executor = Executors.newFixedThreadPool(concurrentWorkflows);
        List<Future<Boolean>> futures = new ArrayList<>(concurrentWorkflows);

        // Define workflow that will be executed concurrently
        String workflow = """
            class LoadTestWorkflow:
                def __init__(self, workflow_id):
                    self.id = workflow_id
                    self.completed = False
                    self.steps = []

                def execute(self):
                    self.steps.append("start")
                    # Simulate some work
                    for i in range(10):
                        self.steps.append(f"step_{i}")
                    self.completed = True
                    return True
            """;

        executePythonCode(workflow);

        long startTime = System.nanoTime();

        // Submit concurrent workflows
        for (int i = 0; i < concurrentWorkflows; i++) {
            final int workflowId = i;
            futures.add(executor.submit(() -> {
                try {
                    executePythonCode(f"workflow = LoadTestWorkflow({workflowId})");
                    executePythonCode("workflow.execute()");
                    return (Boolean) executePythonCode("workflow.completed");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        // Wait for all workflows to complete
        int completed = 0;
        for (Future<Boolean> future : futures) {
            try {
                if (future.get(10, TimeUnit.SECONDS)) {
                    completed++;
                }
            } catch (Exception e) {
                // Count as failed
            }
        }

        long duration = System.nanoTime() - startTime;
        executor.shutdown();

        // Validate load test results
        double throughput = (double) completed / TimeUnit.MILLISECONDS.convert(duration, TimeUnit.NANOSECONDS) * 1000;
        assertThat("Load test throughput", throughput, greaterThan(100.0));
        assertThat("Load test success rate", (double) completed / concurrentWorkflows, greaterThan(0.9));
    }

    @Test
    @DisplayName("Memory Leak Detection: Long Running Process")
    void testMemoryLeakDetection() throws Exception {
        long initialMemory = getCurrentMemoryUsage();
        List<Object> references = new ArrayList<>();

        // Run long process with many objects
        for (int iteration = 0; iteration < 100; iteration++) {
            // Create many objects
            for (int i = 0; i < 100; i++) {
                executePythonCode(f"obj_{iteration}_{i} = {iteration * 100 + i}");
                references.add(executePythonCode(f"obj_{iteration}_{i}"));
            }

            // Periodic cleanup in Python
            if (iteration % 10 == 0) {
                executePythonCode("import gc; gc.collect()");
            }

            // Check memory growth
            if (iteration % 20 == 0) {
                long currentMemory = getCurrentMemoryUsage();
                long memoryGrowth = currentMemory - initialMemory;
                assertThat("Memory growth at iteration " + iteration, memoryGrowth, lessThan(100_000_000L)); // 100MB
            }
        }

        // Final cleanup
        executePythonCode("import gc; gc.collect()");
        long finalMemory = getCurrentMemoryUsage();
        long totalGrowth = finalMemory - initialMemory;

        assertThat("Total memory growth", totalGrowth, lessThan(200_000_000L)); // 200MB
        assertThat("Object count", references.size(), equalTo(10_000));
    }

    @Test
    @DisplayName("Garbage Collection Impact")
    void testGarbageCollectionImpact() throws Exception {
        // Create workload that triggers GC
        List<Long> gcOverhead = new ArrayList<>();

        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long startTime = System.nanoTime();

            // Create objects to trigger GC
            for (int j = 0; j < 50; j++) {
                executePythonCode(f"temp_obj_{i}_{j} = {'x' * 100}");
            }

            // Execute target operation
            executePythonCode("result = 'gc_test'");
            Object result = executePythonCode("result");

            long endTime = System.nanoTime();
            long duration = TimeUnit.MILLISECONDS.convert(endTime - startTime, TimeUnit.NANOSECONDS);

            if (areEquivalent("gc_test", result)) {
                gcOverhead.add(duration);
            }

            // Force GC periodically
            if (i % 200 == 0) {
                executePythonCode("import gc; gc.collect()");
            }
        }

        // Analyze GC overhead
        double avgGCOverhead = gcOverhead.stream().mapToLong(l -> l).average().orElse(0);
        double p95GCOverhead = gcOverhead.stream()
            .sorted()
            .skip((long) (gcOverhead.size() * 0.95))
            .findFirst()
            .orElse(0L);

        assertThat("Average GC overhead", avgGCOverhead, lessThan(15.0));
        assertThat("P95 GC overhead", p95GCOverhead, lessThan(50.0));
    }

    @Test
    @DisplayName("Performance Degradation Under Load")
    void testPerformanceDegradation() throws Exception {
        Map<Integer, Double> throughputMap = new HashMap<>();

        // Test at different load levels
        for (int loadLevel : 1, 5, 10, 25, 50, 100) {
            int concurrentTasks = Math.min(loadLevel, MAX_CONCURRENT_TASKS);
            ExecutorService executor = Executors.newFixedThreadPool(concurrentTasks);
            CountDownLatch latch = new CountDownLatch(concurrentTasks);

            long startTime = System.nanoTime();
            int successfulTasks = 0;

            for (int i = 0; i < concurrentTasks; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < 50; j++) {
                            executePythonCode(f"result = {j}");
                            Object result = executePythonCode("result");
                            if (areEquivalent(j, result)) {
                                successfulTasks++;
                            }
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            long duration = System.nanoTime() - startTime;

            double throughput = (double) successfulTasks / TimeUnit.MILLISECONDS.convert(duration, TimeUnit.NANOSECONDS) * 1000;
            throughputMap.put(loadLevel, throughput);

            executor.shutdown();
        }

        // Check performance degradation curve
        Double baselineThroughput = throughputMap.get(1);
        for (int loadLevel : 5, 10, 25, 50, 100) {
            Double currentThroughput = throughputMap.get(loadLevel);
            double degradation = baselineThroughput / currentThroughput;

            // Performance should degrade gracefully (less than 10x)
            assertThat("Performance degradation at load " + loadLevel, degradation, lessThan(10.0));
        }
    }

    // Helper methods for benchmarking

    private long benchmarkSimpleIntegerArithmetic() throws Exception {
        return benchmarkExecution(() -> {
            try {
                executePythonCode("result = 12345 + 67890");
                executePythonCode("result = result * 2");
                executePythonCode("result = result // 3");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, BENCHMARK_ITERATIONS);
    }

    private long benchmarkStringConcatenation() throws Exception {
        return benchmarkExecution(() -> {
            try {
                executePythonCode("s1 = 'Hello'");
                executePythonCode("s2 = 'World'");
                executePythonCode("result = s1 + ' ' + s2");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, BENCHMARK_ITERATIONS);
    }

    private long benchmarkListOperations() throws Exception {
        return benchmarkExecution(() -> {
            try {
                executePythonCode("lst = [1, 2, 3, 4, 5]");
                executePythonCode("lst.append(6)");
                executePythonCode("result = len(lst)");
                executePythonCode("lst[0] = 10");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, BENCHMARK_ITERATIONS);
    }

    private long benchmarkDictionaryOperations() throws Exception {
        return benchmarkExecution(() -> {
            try {
                executePythonCode("dict = {'a': 1, 'b': 2}");
                executePythonCode("dict['c'] = 3");
                executePythonCode("result = dict.get('a')");
                executePythonCode("result = len(dict)");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, BENCHMARK_ITERATIONS);
    }

    private long getCurrentMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        runtime.gc();
        return runtime.totalMemory() - runtime.freeMemory();
    }
}