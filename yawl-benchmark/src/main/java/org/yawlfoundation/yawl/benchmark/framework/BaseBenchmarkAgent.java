/*
 * YAWL v6.0.0-GA Base Benchmark Agent Framework
 *
 * Base framework for all specialized benchmark agents
 * Implements common agent behavior, monitoring, and error handling
 */

package org.yawlfoundation.yawl.benchmark.framework;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.instance.CaseInstance;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

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
    public void executeBenchmark(Blackhole bh) {
        throw new UnsupportedOperationException(
            "Subclasses must implement executeBenchmark method"
        );
    }

    /**
     * Execute benchmark with variation
     */
    public BenchmarkResult executeBenchmark(String variation) throws Exception {
        try {
            Instant start = Instant.now();

            // Execute benchmark based on variation
            CaseInstance result = runSingleIteration(0);

            Instant end = Instant.now();

            return new BenchmarkResult(result, true, null);
        } catch (Exception e) {
            recordError(e, "variation_" + variation);
            return new BenchmarkResult(null, false, e.getMessage());
        }
    }

    /**
     * Setup method - to be implemented by subclasses
     */
    protected void setup() throws Exception {
        // Default implementation - can be overridden by subclasses
    }

    /**
     * Cleanup method - to be implemented by subclasses
     */
    protected void cleanup() throws Exception {
        // Default implementation - can be overridden by subclasses
    }
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
     * Execute benchmark with structured concurrency (using ExecutorService)
     */
    @Benchmark
    public void executeBenchmarkStructured(Blackhole bh) throws InterruptedException {
        List<Future<CaseInstance>> futures = new ArrayList<>();
        int concurrencyLevel = config.structuredConcurrencyLevel();

        for (int i = 0; i < concurrencyLevel; i++) {
            final int iterationId = i;
            Future<CaseInstance> future = virtualThreadExecutor.submit(() -> {
                try {
                    return runSingleIteration(iterationId);
                } catch (Exception e) {
                    recordError(e, "structured_iteration_" + iterationId);
                    throw new RuntimeException(e);
                }
            });
            futures.add(future);
        }

        // Collect results
        for (Future<CaseInstance> future : futures) {
            try {
                CaseInstance result = future.get(30, TimeUnit.SECONDS);
                bh.consume(result);
            } catch (TimeoutException | ExecutionException e) {
                recordError(e, "structured_future_get");
                bh.consume(null);
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
                        CaseInstance result = runSingleIteration(threadId);
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
                    BenchmarkResult result = future.get(config.operationTimeout().toSeconds(), TimeUnit.SECONDS);
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
    protected abstract CaseInstance runSingleIteration(int iterationId) throws Exception;

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
            errors.stream()
                .map(error -> String.format("%s: %s (%s)",
                    error.timestamp().toString(),
                    error.errorType(),
                    error.context()))
                .collect(Collectors.toList()),
            new HashMap<>(performanceMonitor.generateSummary())
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
    public static record BenchmarkResult(CaseInstance caseResult, boolean success, String errorMessage) {}

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

    /**
     * Get benchmark configuration
     */
    protected BenchmarkConfig config() {
        return config;
    }

    /**
     * Create a minimal CaseInstance for fallback operations
     * To be implemented by subclasses
     */
    protected CaseInstance createMinimalCase(String identifier) {
        try {
            // This should be implemented based on specific YAWL API requirements
            // For now, create a basic mock implementation
            CaseInstance minimalCase = new CaseInstance();
            minimalCase.setCaseID("minimal_case_" + identifier + "_" + System.currentTimeMillis());
            return minimalCase;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create minimal case: " + identifier, e);
        }
    }

  }