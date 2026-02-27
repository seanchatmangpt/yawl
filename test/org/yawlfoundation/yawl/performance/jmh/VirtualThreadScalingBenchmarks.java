/*
 * YAWL v6.0.0-GA Validation
 * Virtual Thread Scaling Benchmarks
 *
 * Validates virtual thread performance vs platform threads for YAWL v6.0.0
 */
package org.yawlfoundation.yawl.performance.jmh;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.yawlfoundation.yawl.engine.YAWLServiceGateway;
import org.yawlfoundation.yawl.engine.YNetRunner;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.openjdk.jmh.annotations.Mode.*;

/**
 * Virtual thread scaling benchmarks for YAWL v6.0.0
 * Validates virtual thread performance vs platform threads
 */
@BenchmarkMode({Throughput, AverageTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 10)
@Measurement(iterations = 5, time = 30)
@Fork(value = 1, jvmArgs = {
    "-Xms4g", "-Xmx8g",
    "-XX:+UseG1GC",
    "-XX:+UseCompactObjectHeaders",
    "--enable-preview"
})
@State(Scope.Benchmark)
public class VirtualThreadScalingBenchmarks {

    private YAWLServiceGateway serviceGateway;
    private YNetRunner workflowRunner;

    // Test configuration
    private static final int CASE_DURATION_MS = 100;
    private static final String WORKFLOW_ID = "virtual-thread-workflow";

    @Setup
    public void setup() {
        serviceGateway = new YAWLServiceGateway();
        workflowRunner = serviceGateway.getNet(WORKFLOW_ID);
        if (workflowRunner == null) {
            throw new RuntimeException("Test workflow not found: " + WORKFLOW_ID);
        }
    }

    @TearDown
    public void tearDown() {
        serviceGateway.shutdown();
    }

    /**
     * Benchmark: Virtual thread scalability
     */
    @Benchmark
    @BenchmarkMode(Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void testVirtualThreadConcurrency(@Param({"100", "1000", "10000", "50000"}) int concurrency) {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(concurrency);
        AtomicInteger completed = new AtomicInteger(0);
        Instant startTime = Instant.now();

        for (int i = 0; i < concurrency; i++) {
            final int taskId = i;
            executor.submit(() -> {
                try {
                    executeVirtualThreadTask(taskId);
                    completed.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(1, TimeUnit.MINUTES);
            Instant endTime = Instant.now();
            long duration = Duration.between(startTime, endTime).toMillis();

            // Verify all tasks completed
            assertEquals(concurrency, completed.get(),
                "Not all virtual thread tasks completed: " + completed.get() + "/" + concurrency);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Virtual thread benchmark interrupted", e);
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Benchmark: Platform thread scalability (baseline)
     */
    @Benchmark
    @BenchmarkMode(Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void testPlatformThreadConcurrency(@Param({"100", "1000", "10000", "50000"}) int concurrency) {
        ExecutorService executor = Executors.newFixedThreadPool(
            Math.min(concurrency, Runtime.getRuntime().availableProcessors() * 2)
        );
        CountDownLatch latch = new CountDownLatch(concurrency);
        AtomicInteger completed = new AtomicInteger(0);
        Instant startTime = Instant.now();

        for (int i = 0; i < concurrency; i++) {
            final int taskId = i;
            executor.submit(() -> {
                try {
                    executePlatformThreadTask(taskId);
                    completed.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(1, TimeUnit.MINUTES);
            Instant endTime = Instant.now();
            long duration = Duration.between(startTime, endTime).toMillis();

            // Verify all tasks completed
            assertEquals(concurrency, completed.get(),
                "Not all platform thread tasks completed: " + completed.get() + "/" + concurrency);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Platform thread benchmark interrupted", e);
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Benchmark: Carrier thread pool optimization
     */
    @Benchmark
    @BenchmarkMode(AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void testCarrierThreadPoolOptimization(
            @Param({"1", "2", "4", "8"}) int carriers,
            @Param({"1000", "5000", "10000"}) int taskCount) {

        // Configure carrier thread pool
        ForkJoinPool carrierPool = new ForkJoinPool(carriers);
        AtomicInteger completed = new AtomicInteger(0);

        Instant startTime = Instant.now();

        try {
            carrierPool.submit(() -> {
                for (int i = 0; i < taskCount; i++) {
                    ForkJoinPool.commonPool().submit(() -> {
                        executeCarrierTask(i);
                        completed.incrementAndGet();
                    });
                }
            }).get();

            Instant endTime = Instant.now();
            double avgTime = Duration.between(startTime, endTime).toMillis() / (double) taskCount;

            // Verify all tasks completed
            assertEquals(taskCount, completed.get(),
                "Not all carrier pool tasks completed: " + completed.get() + "/" + taskCount);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Carrier thread pool benchmark interrupted", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Carrier thread pool benchmark failed", e);
        } finally {
            carrierPool.shutdown();
        }
    }

    /**
     * Benchmark: Mixed workloads with virtual threads
     */
    @Benchmark
    @BenchmarkMode(Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void testVirtualThreadMixedWorkload(
            @Param({"I/O bound", "CPU bound", "Mixed"}) String workloadType,
            @Param({"100", "1000", "10000"}) int concurrency) {

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch latch = new CountDownLatch(concurrency);
        AtomicInteger completed = new AtomicInteger(0);

        Instant startTime = Instant.now();

        for (int i = 0; i < concurrency; i++) {
            final int taskId = i;
            executor.submit(() -> {
                try {
                    switch (workloadType) {
                        case "I/O bound":
                            executeIoBoundVirtualTask(taskId);
                            break;
                        case "CPU bound":
                            executeCpuBoundVirtualTask(taskId);
                            break;
                        case "Mixed":
                            if (taskId % 3 == 0) {
                                executeIoBoundVirtualTask(taskId);
                            } else if (taskId % 3 == 1) {
                                executeCpuBoundVirtualTask(taskId);
                            } else {
                                executeMixedVirtualTask(taskId);
                            }
                            break;
                    }
                    completed.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(1, TimeUnit.MINUTES);
            Instant endTime = Instant.now();
            long duration = Duration.between(startTime, endTime).toMillis();
            double throughput = concurrency / (duration / 1000.0);

            // Throughput should scale linearly with virtual threads
            assertTrue(throughput > 10, "Throughput too low for virtual threads: " + throughput);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Mixed workload benchmark interrupted", e);
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Benchmark: Virtual thread memory efficiency
     */
    @Benchmark
    @BenchmarkMode(AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void testVirtualThreadMemoryEfficiency(
            @Param({"1000", "5000", "10000", "50000"}) int concurrency) {

        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<?>> futures = new ArrayList<>();

        // Submit tasks
        for (int i = 0; i < concurrency; i++) {
            final int taskId = i;
            futures.add(executor.submit(() -> {
                // Create objects that would be expensive in platform threads
                List<String> data = new ArrayList<>();
                for (int j = 0; j < 100; j++) {
                    data.add("data-" + taskId + "-" + j);
                }
                return data;
            }));
        }

        // Wait for completion
        for (Future<?> future : futures) {
            try {
                future.get(1, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Memory benchmark interrupted", e);
            } catch (ExecutionException e) {
                throw new RuntimeException("Memory benchmark failed", e);
            }
        }

        executor.shutdown();

        // Measure memory usage
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryDelta = finalMemory - initialMemory;
        double memoryPerThread = (double) memoryDelta / concurrency;

        // Virtual threads should be memory efficient (< 1KB per thread)
        assertTrue(memoryPerThread < 1024,
            "Virtual thread memory usage too high: " + memoryPerThread + " bytes/thread");
    }

    /**
     * Benchmark: Virtual thread vs platform thread comparison
     */
    @Benchmark
    @BenchmarkMode(AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void compareVirtualVsPlatform(
            @Param({"100", "1000", "10000"}) int concurrency) {

        // Test virtual threads
        long virtualTime = testThreadConcurrency(
            Executors.newVirtualThreadPerTaskExecutor(), concurrency);

        // Test platform threads
        long platformTime = testThreadConcurrency(
            Executors.newFixedThreadPool(
                Math.min(concurrency, Runtime.getRuntime().availableProcessors() * 2)),
            concurrency);

        // Virtual threads should be significantly faster for I/O bound work
        double speedup = (double) platformTime / virtualTime;
        assertTrue(speedup > 1.5, "Virtual threads not faster: " + speedup + "x");
    }

    /**
     * Helper method to test thread concurrency
     */
    private long testThreadConcurrency(ExecutorService executor, int concurrency) {
        CountDownLatch latch = new CountDownLatch(concurrency);
        AtomicInteger completed = new AtomicInteger(0);
        Instant startTime = Instant.now();

        for (int i = 0; i < concurrency; i++) {
            final int taskId = i;
            executor.submit(() -> {
                try {
                    executeIoBoundVirtualTask(taskId);
                    completed.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(1, TimeUnit.MINUTES);
            return Duration.between(startTime, Instant.now()).toMillis();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread concurrency test interrupted", e);
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Virtual thread task execution methods
     */
    private void executeVirtualThreadTask(int taskId) {
        try {
            // Simulate workflow execution
            if (workflowRunner != null) {
                workflowRunner.setAttribute("taskId", taskId);
                workflowRunner.setAttribute("threadType", "virtual");
                workflowRunner.launchCase();
            }

            // Simulate I/O operation
            Thread.sleep(CASE_DURATION_MS);

        } catch (Exception e) {
            throw new RuntimeException("Virtual thread task failed: " + taskId, e);
        }
    }

    private void executePlatformThreadTask(int taskId) {
        try {
            // Simulate workflow execution
            if (workflowRunner != null) {
                workflowRunner.setAttribute("taskId", taskId);
                workflowRunner.setAttribute("threadType", "platform");
                workflowRunner.launchCase();
            }

            // Simulate I/O operation
            Thread.sleep(CASE_DURATION_MS);

        } catch (Exception e) {
            throw new RuntimeException("Platform thread task failed: " + taskId, e);
        }
    }

    private void executeCarrierTask(int taskId) {
        // Simulate carrier thread work
        try {
            Thread.sleep(CASE_DURATION_MS / 2);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Carrier task interrupted", e);
        }
    }

    private void executeIoBoundVirtualTask(int taskId) {
        try {
            // Simulate I/O bound work
            Thread.sleep(CASE_DURATION_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("I/O task interrupted", e);
        }
    }

    private void executeCpuBoundVirtualTask(int taskId) {
        // Simulate CPU bound work
        double result = 0;
        for (int i = 0; i < 10000; i++) {
            result += Math.sqrt(i) * Math.sin(i);
        }
    }

    private void executeMixedVirtualTask(int taskId) {
        try {
            // Mix of I/O and CPU work
            Thread.sleep(CASE_DURATION_MS / 3);
            double result = 0;
            for (int i = 0; i < 5000; i++) {
                result += Math.sqrt(i) * Math.cos(i);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Mixed task interrupted", e);
        }
    }

    /**
     * Assertion methods
     */
    private void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " (expected: " + expected + ", actual: " + actual + ")");
        }
    }

    private void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}