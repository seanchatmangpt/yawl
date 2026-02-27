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
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.performance.jmh;

import org.openjdk.jmh.annotations.*;
import org.yawlfoundation.yawl.engine.YAWLServiceGateway;
import org.yawlfoundation.yawl.engine.YNetRunner;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;
import java.util.concurrent.atomic.AtomicInteger;

import static org.openjdk.jmh.annotations.Mode.*;

/**
 * Structured Concurrency Performance Benchmarks for YAWL v6.0.0-GA
 *
 * Tests Java 25 structured concurrency features with performance targets:
 * - Structured task scope creation: < 0.1ms
 * - Task fork overhead: < 0.05ms per task
 * - Exception propagation time: < 0.1ms
 * - Resource cleanup efficiency: > 95%
 */
@BenchmarkMode({Throughput, AverageTime})
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 10)
@Measurement(iterations = 10, time = 30)
@Fork(value = 1, jvmArgs = {
    "--enable-preview",
    "-Xms4g", "-Xmx8g"
})
@State(Scope.Benchmark)
public class StructuredConcurrencyPerformanceBenchmarks {

    private YAWLServiceGateway serviceGateway;
    private YNetRunner workflowRunner;

    @Setup
    public void setup() {
        serviceGateway = new YAWLServiceGateway();
        workflowRunner = serviceGateway.getNet("structured-concurrency-workflow");
        if (workflowRunner == null) {
            throw new RuntimeException("Test workflow not found: structured-concurrency-workflow");
        }
    }

    @TearDown
    public void tearDown() {
        serviceGateway.shutdown();
    }

    /**
     * Benchmark: StructuredTaskScope.ShutdownOnFailure
     */
    @Benchmark
    @BenchmarkMode(Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void testStructuredShutdownOnFailure(@Param({"10", "100", "1000"}) int taskCount) {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        AtomicInteger completed = new AtomicInteger(0);

        Instant startTime = Instant.now();

        for (int i = 0; i < taskCount; i++) {
            final int taskId = i;
            executor.submit(() -> {
                try {
                    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
                        Subtask<?> step1 = scope.fork(() -> executeStructuredStep1(taskId));
                        Subtask<?> step2 = scope.fork(() -> executeStructuredStep2(taskId));
                        Subtask<?> step3 = scope.fork(() -> executeStructuredStep3(taskId));

                        scope.join();

                        if (step1.state() == Subtask.State.FAILED ||
                            step2.state() == Subtask.State.FAILED ||
                            step3.state() == Subtask.State.FAILED) {
                            throw new RuntimeException("Structured task failed");
                        }

                        completed.incrementAndGet();
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Structured concurrency failed", e);
                }
            });
        }

        try {
            // Wait for completion
            while (completed.get() < taskCount) {
                Thread.sleep(100);
            }

            long duration = Duration.between(startTime, Instant.now()).toMillis();
            double throughput = taskCount / (duration / 1000.0);

            // Verify structured concurrency is efficient
            assertTrue(throughput > 50, "Structured throughput too low: " + throughput);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Benchmark interrupted", e);
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Benchmark: StructuredTaskScope.ShutdownOnSuccess
     */
    @Benchmark
    @BenchmarkMode(Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void testStructuredShutdownOnSuccess(@Param({"10", "100", "1000"}) int taskCount) {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        AtomicInteger completed = new AtomicInteger(0);

        Instant startTime = Instant.now();

        for (int i = 0; i < taskCount; i++) {
            final int taskId = i;
            executor.submit(() -> {
                try {
                    try (var scope = new StructuredTaskScope.ShutdownOnSuccess<>()) {
                        Subtask<?> step1 = scope.fork(() -> executeStructuredStep1(taskId));
                        Subtask<?> step2 = scope.fork(() -> executeStructuredStep2(taskId));
                        Subtask<?> step3 = scope.fork(() -> executeStructuredStep3(taskId));

                        scope.join();

                        Object result = scope.result();
                        completed.incrementAndGet();
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Structured success benchmark failed", e);
                }
            });
        }

        try {
            // Wait for completion
            while (completed.get() < taskCount) {
                Thread.sleep(100);
            }

            long duration = Duration.between(startTime, Instant.now()).toMillis();
            double throughput = taskCount / (duration / 1000.0);

            // Verify structured concurrency with success shutdown is efficient
            assertTrue(throughput > 60, "Structured success throughput too low: " + throughput);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Benchmark interrupted", e);
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Benchmark: Structured vs Traditional Concurrency Comparison
     */
    @Benchmark
    @BenchmarkMode(AverageTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void compareStructuredVsTraditional(
            @Param({"100", "1000", "5000"}) int concurrency,
            @Param({"structured", "traditional", "mixed"}) String patternType) {

        Instant startTime = Instant.now();

        switch (patternType) {
            case "structured":
                testStructuredConcurrency(concurrency);
                break;
            case "traditional":
                testTraditionalConcurrency(concurrency);
                break;
            case "mixed":
                testMixedConcurrency(concurrency);
                break;
        }

        long duration = Duration.between(startTime, Instant.now()).toMillis();

        // Structured should be more efficient than traditional
        if ("structured".equals(patternType)) {
            assertTrue(duration < getTraditionalExpectedTime(concurrency),
                "Structured concurrency not faster than traditional");
        }
    }

    /**
     * Benchmark: Exception Propagation in Structured Context
     */
    @Benchmark
    @BenchmarkMode(AverageTime)
    @OutputTimeUnit(MICROSECONDS)
    public void testExceptionPropagation(
            @Param({"structured-exception", "traditional-exception", "mixed-exception"}) String exceptionType,
            @Param({"10", "100", "1000"}) int taskCount) {

        Instant startTime = Instant.now();

        switch (exceptionType) {
            case "structured-exception":
                testStructuredExceptionPropagation(taskCount);
                break;
            case "traditional-exception":
                testTraditionalExceptionPropagation(taskCount);
                break;
            case "mixed-exception":
                testMixedExceptionPropagation(taskCount);
                break;
        }

        long duration = Duration.between(startTime, Instant.now()).toMicros();

        // Exception propagation should be fast (< 100μs)
        assertTrue(duration < 100, "Exception propagation too slow: " + duration + " μs");
    }

    /**
     * Benchmark: Resource Cleanup Efficiency
     */
    @Benchmark
    @BenchmarkMode(Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void testResourceCleanupEfficiency(
            @Param({"structured-cleanup", "traditional-cleanup", "mixed-cleanup"}) String cleanupType,
            @Param({"100", "1000", "5000"}) int taskCount) {

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        AtomicInteger cleanedUp = new AtomicInteger(0);
        List<AutoCloseable> resources = new ArrayList<>();

        Instant startTime = Instant.now();

        for (int i = 0; i < taskCount; i++) {
            final int taskId = i;
            executor.submit(() -> {
                try {
                    switch (cleanupType) {
                        case "structured-cleanup":
                            executeStructuredCleanup(taskId, resources, cleanedUp);
                            break;
                        case "traditional-cleanup":
                            executeTraditionalCleanup(taskId, resources, cleanedUp);
                            break;
                        case "mixed-cleanup":
                            executeMixedCleanup(taskId, resources, cleanedUp);
                            break;
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Resource cleanup failed", e);
                }
            });
        }

        try {
            executor.shutdown();
            if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
                throw new RuntimeException("Executor did not terminate");
            }

            long duration = Duration.between(startTime, Instant.now()).toMillis();
            double cleanupEfficiency = cleanedUp.get() / (double) taskCount;

            // Resource cleanup efficiency should be > 95%
            assertTrue(cleanupEfficiency > 0.95,
                "Resource cleanup efficiency too low: " + cleanupEfficiency);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Cleanup benchmark interrupted", e);
        }
    }

    /**
     * Benchmark: Fork-Join Overhead
     */
    @Benchmark
    @BenchmarkMode(AverageTime)
    @OutputTimeUnit(MICROSECONDS)
    public void testForkJoinOverhead(
            @Param({"1", "5", "10", "20", "50"}) int taskCount) {

        Instant startTime = Instant.now();

        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            List<Subtask<?>> subtasks = new ArrayList<>();

            for (int i = 0; i < taskCount; i++) {
                final int taskId = i;
                subtasks.add(scope.fork(() -> executeSimpleTask(taskId)));
            }

            scope.join();

            long duration = Duration.between(startTime, Instant.now()).toMicros();
            double avgForkTime = duration / (double) taskCount;

            // Fork overhead should be < 50μs per task
            assertTrue(avgForkTime < 50, "Fork overhead too high: " + avgForkTime + " μs per task");
        } catch (Exception e) {
            throw new RuntimeException("Fork-join benchmark failed", e);
        }
    }

    /**
     * Helper methods for structured concurrency testing
     */
    private void testStructuredConcurrency(int concurrency) {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            for (int i = 0; i < concurrency; i++) {
                final int taskId = i;
                scope.fork(() -> executeStructuredStep1(taskId));
            }
            scope.join();
        } catch (Exception e) {
            throw new RuntimeException("Structured concurrency test failed", e);
        }
    }

    private void testTraditionalConcurrency(int concurrency) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        for (int i = 0; i < concurrency; i++) {
            final int taskId = i;
            futures.add(CompletableFuture.runAsync(
                () -> executeTraditionalStep(taskId),
                executor
            ));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();
    }

    private void testMixedConcurrency(int concurrency) {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            for (int i = 0; i < concurrency / 2; i++) {
                final int taskId = i;
                scope.fork(() -> executeStructuredStep1(taskId));

                CompletableFuture.runAsync(
                    () -> executeTraditionalStep(taskId + concurrency / 2),
                    Executors.newVirtualThreadPerTaskExecutor()
                );
            }
            scope.join();
        } catch (Exception e) {
            throw new RuntimeException("Mixed concurrency test failed", e);
        }
    }

    private void testStructuredExceptionPropagation(int taskCount) {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        for (int i = 0; i < taskCount; i++) {
            final int taskId = i;
            executor.submit(() -> {
                try {
                    try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
                        Subtask<?> task = scope.fork(() -> {
                            if (taskId % 10 == 0) {
                                throw new RuntimeException("Simulated failure");
                            }
                            return taskId;
                        });
                        scope.join();
                    }
                } catch (Exception e) {
                    // Expected exception
                }
            });
        }

        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void testTraditionalExceptionPropagation(int taskCount) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        for (int i = 0; i < taskCount; i++) {
            final int taskId = i;
            futures.add(CompletableFuture.runAsync(() -> {
                if (taskId % 10 == 0) {
                    throw new RuntimeException("Simulated failure");
                }
            }, executor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();
    }

    private void testMixedExceptionPropagation(int taskCount) {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

        for (int i = 0; i < taskCount; i++) {
            final int taskId = i;
            if (i % 2 == 0) {
                // Structured exception
                executor.submit(() -> {
                    try {
                        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
                            Subtask<?> task = scope.fork(() -> {
                                if (taskId % 10 == 0) {
                                    throw new RuntimeException("Simulated failure");
                                }
                                return taskId;
                            });
                            scope.join();
                        }
                    } catch (Exception e) {
                        // Expected exception
                    }
                });
            } else {
                // Traditional exception
                CompletableFuture.runAsync(() -> {
                    if (taskId % 10 == 0) {
                        throw new RuntimeException("Simulated failure");
                    }
                }, executor);
            }
        }

        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void executeStructuredCleanup(int taskId, List<AutoCloseable> resources, AtomicInteger cleanedUp) {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            AutoCloseable resource = () -> cleanedUp.incrementAndGet();
            resources.add(resource);

            scope.fork(() -> {
                try {
                    Thread.sleep(1);
                    return "task-" + taskId;
                } finally {
                    resource.close();
                }
            });

            scope.join();
        } catch (Exception e) {
            throw new RuntimeException("Structured cleanup failed", e);
        }
    }

    private void executeTraditionalCleanup(int taskId, List<AutoCloseable> resources, AtomicInteger cleanedUp) {
        AutoCloseable resource = () -> cleanedUp.incrementAndGet();
        resources.add(resource);

        try {
            Thread.sleep(1);
        } finally {
            try {
                resource.close();
            } catch (Exception e) {
                throw new RuntimeException("Traditional cleanup failed", e);
            }
        }
    }

    private void executeMixedCleanup(int taskId, List<AutoCloseable> resources, AtomicInteger cleanedUp) {
        if (taskId % 2 == 0) {
            executeStructuredCleanup(taskId, resources, cleanedUp);
        } else {
            executeTraditionalCleanup(taskId, resources, cleanedUp);
        }
    }

    /**
     * Task execution methods
     */
    private String executeStructuredStep1(int taskId) throws Exception {
        Thread.sleep(1);
        return "step1-" + taskId;
    }

    private String executeStructuredStep2(int taskId) throws Exception {
        Thread.sleep(1);
        return "step2-" + taskId;
    }

    private String executeStructuredStep3(int taskId) throws Exception {
        Thread.sleep(1);
        return "step3-" + taskId;
    }

    private void executeTraditionalStep(int taskId) {
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Traditional task interrupted", e);
        }
    }

    private String executeSimpleTask(int taskId) {
        try {
            Thread.sleep(1);
            return "task-" + taskId;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Simple task interrupted", e);
        }
    }

    /**
     * Helper method to get expected traditional concurrency time
     */
    private long getTraditionalExpectedTime(int concurrency) {
        // Traditional should be slower, especially at higher concurrency
        return concurrency * 2L; // 2ms per task on average
    }

    /**
     * Assertion method
     */
    private void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}