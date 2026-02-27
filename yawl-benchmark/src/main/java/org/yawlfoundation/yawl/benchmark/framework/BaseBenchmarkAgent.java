/*
 * YAWL v6.0.0-GA Base Benchmark Agent Framework
 *
 * Base framework for all specialized benchmark agents
 * Implements common agent behavior, monitoring, and error handling
 */

package org.yawlfoundation.yawl.benchmark.framework;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.yawlfoundation.yawl.engine.YCase;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.YNet;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Base class for all benchmark agents
 * Provides common functionality for:
 * - Virtual thread management
 * - Performance monitoring
 * - Error handling and fallback mechanisms
 * - Result reporting
 */
public abstract class BaseBenchmarkAgent implements AutoCloseable {

    // Agent configuration
    protected final String agentName;
    protected final String benchmarkArea;
    protected final ExecutorService virtualThreadExecutor;

    // Performance monitoring
    protected final PerformanceMonitor performanceMonitor;
    protected final AtomicLong totalOperations;
    protected final AtomicLong successfulOperations;
    protected final AtomicLong failedOperations;

    // Benchmark configuration
    protected BenchmarkConfig config;
    protected Instant benchmarkStart;
    protected Instant benchmarkEnd;

    // Error handling
    protected final List<BenchmarkError> errors;
    protected final FallbackStrategy fallbackStrategy;

    public BaseBenchmarkAgent(String agentName, String benchmarkArea, BenchmarkConfig config) {
        this.agentName = agentName;
        this.benchmarkArea = benchmarkArea;
        this.config = config;
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

        // Initialize monitoring
        this.performanceMonitor = new PerformanceMonitor(agentName);
        this.totalOperations = new AtomicLong(0);
        this.successfulOperations = new AtomicLong(0);
        this.failedOperations = new AtomicLong(0);

        // Error handling
        this.errors = Collections.synchronizedList(new ArrayList<>());
        this.fallbackStrategy = new FallbackStrategy();

        this.benchmarkStart = Instant.now();
    }

    /**
     * Main benchmark execution method - to be implemented by subclasses
     */
    @Benchmark
    @GroupThreads(1)
    public abstract void executeBenchmark(Blackhole bh);

    /**
     * Execute benchmark with virtual thread scaling
     */
    @Benchmark
    @GroupThreads(10)
    public void executeBenchmarkScaled_10(Blackhole bh) {
        executeBenchmarkWithScaling(10, bh);
    }

    @Benchmark
    @GroupThreads(50)
    public void executeBenchmarkScaled_50(Blackhole bh) {
        executeBenchmarkWithScaling(50, bh);
    }

    @Benchmark
    @GroupThreads(100)
    public void executeBenchmarkScaled_100(Blackhole bh) {
        executeBenchmarkWithScaling(100, bh);
    }

    @Benchmark
    @GroupThreads(500)
    public void executeBenchmarkScaled_500(Blackhole bh) {
        executeBenchmarkWithScaling(500, bh);
    }

    /**
     * Execute benchmark with structured concurrency
     */
    @Benchmark
    public void executeBenchmarkStructured(Blackhole bh) throws InterruptedException {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            List<Future<YCase>> futures = new ArrayList<>();

            for (int i = 0; i < config.getStructuredConcurrencyLevel(); i++) {
                Future<YCase> future = scope.fork(() -> {
                    try {
                        return runSingleIteration(i);
                    } catch (Exception e) {
                        recordError(e, "structured_iteration_" + i);
                        throw e;
                    }
                });
                futures.add(future);
            }

            scope.join();

            // Collect results
            for (Future<YCase> future : futures) {
                try {
                    YCase result = future.resultNow();
                    bh.consume(result);
                    successfulOperations.incrementAndGet();
                } catch (Exception e) {
                    bh.consume(e);
                    failedOperations.incrementAndGet();
                }
            }
        }
    }

    /**
     * Execute benchmark with fallback mechanisms
     */
    protected final void executeBenchmarkWithScaling(int threadCount, Blackhole bh) {
        try {
            Instant start = Instant.now();

            List<Future<BenchmarkResult>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                Future<BenchmarkResult> future = virtualThreadExecutor.submit(() -> {
                    try {
                        YCase result = runSingleIteration(threadId);
                        totalOperations.incrementAndGet();
                        successfulOperations.incrementAndGet();
                        return new BenchmarkResult(result, true, null);
                    } catch (Exception e) {
                        totalOperations.incrementAndGet();
                        failedOperations.incrementAndGet();
                        recordError(e, "scaling_iteration_" + threadId);

                        // Attempt fallback
                        try {
                            BenchmarkResult fallback = fallbackStrategy.executeFallback(this, threadId);
                            return fallback;
                        } catch (Exception fallbackEx) {
                            return new BenchmarkResult(null, false, fallbackEx.getMessage());
                        }
                    }
                });
                futures.add(future);
            }

            // Wait for all operations to complete
            for (Future<BenchmarkResult> future : futures) {
                try {
                    BenchmarkResult result = future.get(config.getOperationTimeout(), TimeUnit.SECONDS);
                    bh.consume(result.caseResult());
                } catch (Exception e) {
                    bh.consume(e);
                    recordError(e, "scaling_result_collection");
                }
            }

            Instant end = Instant.now();
            Duration duration = Duration.between(start, end);

            performanceMonitor.recordOperation(
                threadCount,
                duration.toMillis(),
                successfulOperations.get(),
                failedOperations.get()
            );

        } catch (Exception e) {
            bh.consume(e);
            recordError(e, "scaling_execution");
        }
    }

    /**
     * Single benchmark iteration - to be implemented by subclasses
     */
    protected abstract YCase runSingleIteration(int iterationId) throws Exception;

    /**
     * Error handling and recording
     */
    protected final void recordError(Exception e, String context) {
        BenchmarkError error = new BenchmarkError(
            agentName,
            benchmarkArea,
            e.getClass().getSimpleName(),
            e.getMessage(),
            context,
            Instant.now()
        );
        errors.add(error);

        // Log error
        performanceMonitor.recordError(error);
    }

    /**
     * Performance monitoring and reporting
     */
    protected final BenchmarkReport generateFinalReport() {
        benchmarkEnd = Instant.now();

        return new BenchmarkReport(
            agentName,
            benchmarkArea,
            benchmarkStart,
            benchmarkEnd,
            totalOperations.get(),
            successfulOperations.get(),
            failedOperations.get(),
            errors,
            performanceMonitor.generateSummary()
        );
    }

    /**
     * Cleanup and shutdown
     */
    @Override
    public void close() {
        if (virtualThreadExecutor != null) {
            virtualThreadExecutor.shutdown();
            try {
                if (!virtualThreadExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                    virtualThreadExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                virtualThreadExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    // Inner classes for data structures
    public static record BenchmarkResult(YCase caseResult, boolean success, String errorMessage) {}

    public static record BenchmarkError(
        String agentName,
        String benchmarkArea,
        String errorType,
        String errorMessage,
        String context,
        Instant timestamp
    ) {}

    public static record BenchmarkConfig(
        int structuredConcurrencyLevel,
        Duration operationTimeout,
        boolean enableVirtualThreads,
        boolean enableFallbackMechanisms
    ) {}

    // Configuration factory
    public static BenchmarkConfig defaultConfig() {
        return new BenchmarkConfig(
            5,
            Duration.ofSeconds(30),
            true,
            true
        );
    }

    public static BenchmarkConfig aggressiveConfig() {
        return new BenchmarkConfig(
            100,
            Duration.ofSeconds(10),
            true,
            false
        );
    }
}