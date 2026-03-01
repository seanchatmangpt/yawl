/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.engine.validation;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;

/**
 * Actor Model Optimizer
 *
 * Main orchestrator that integrates all validation components to optimize
 * virtual thread configuration for the YAWL actor model.
 *
 * <p>Workflow:</p>
 * <ol>
 *   <li>Carrier thread optimization testing (1, 2, 4, 8, 16, 32 threads)</li>
 *   <li>Virtual thread behavior profiling</li>
 *   <li>StructuredTaskScope integration validation</li>
 *   <li>Stack depth analysis</li>
 *   <li>Lifecycle management validation</li>
 *   <li>Performance benchmarking</li>
 *   <li>Report generation and recommendations</li>
 * </ol>
 *
 * @author YAWL Foundation
 * @version 6.0
 * @since 6.0
 */
public class ActorModelOptimizer {

    private static final Logger _logger = LogManager.getLogger(ActorModelOptimizer.class);

    // Configuration
    private final MeterRegistry meterRegistry;
    private final Tracer tracer;
    private final ExecutorService executor;
    private final AnalysisReportGenerator reportGenerator;

    // Analysis components
    private CarrierThreadOptimizer carrierThreadOptimizer;
    private VirtualThreadProfiler virtualThreadProfiler;
    private StructuredTaskScopeIntegrationTester structuredTaskScopeTester;
    private StackDepthAnalyzer stackDepthAnalyzer;
    private VirtualThreadLifecycleManager lifecycleManager;
    private PerformanceBenchmarkSuite performanceBenchmarkSuite;

    // Optimization state
    private volatile boolean optimizing = false;
    private Instant optimizationStartTime;
    private Instant optimizationEndTime;

    /**
     * Create a new ActorModelOptimizer.
     */
    public ActorModelOptimizer(MeterRegistry meterRegistry, Tracer tracer) {
        this.meterRegistry = meterRegistry;
        this.tracer = tracer;
        this.executor = Executors.newFixedThreadPool(6);
        this.reportGenerator = new AnalysisReportGenerator();
    }

    /**
     * Run complete optimization workflow.
     */
    public OptimizationResult optimizeVirtualThreadConfiguration() {
        if (optimizing) {
            throw new IllegalStateException("Optimization already in progress");
        }

        optimizing = true;
        optimizationStartTime = Instant.now();

        _logger.info("Starting YAWL Actor Model virtual thread optimization");

        try {
            // Step 1: Initialize all components
            initializeComponents();

            // Step 2: Run carrier thread optimization
            _logger.info("Step 1: Carrier thread optimization");
            CarrierThreadOptimizer.OptimizationReport carrierReport =
                runCarrierThreadOptimization();

            // Step 3: Run virtual thread profiling
            _logger.info("Step 2: Virtual thread profiling");
            VirtualThreadProfiler.ProfileReport profileReport =
                runVirtualThreadProfiling();

            // Step 4: Run structured concurrency validation
            _logger.info("Step 3: Structured concurrency validation");
            StructuredTaskScopeIntegrationTester.IntegrationTestResults integrationResults =
                runStructuredConcurrencyValidation();

            // Step 5: Run stack depth analysis
            _logger.info("Step 4: Stack depth analysis");
            StackDepthAnalyzer.StackDepthAnalysisReport stackReport =
                runStackDepthAnalysis();

            // Step 6: Run lifecycle validation
            _logger.info("Step 5: Lifecycle management validation");
            VirtualThreadLifecycleManager.LifecycleManagementReport lifecycleReport =
                runLifecycleValidation();

            // Step 7: Run performance benchmarks
            _logger.info("Step 6: Performance benchmarking");
            PerformanceBenchmarkSuite.BenchmarkReport benchmarkReport =
                runPerformanceBenchmarking();

            // Step 8: Generate comprehensive report
            _logger.info("Step 7: Generating optimization report");
            AnalysisReportGenerator.AnalysisReport analysisReport =
                reportGenerator.generateReport(
                    carrierReport,
                    profileReport,
                    integrationResults,
                    stackReport,
                    lifecycleReport,
                    benchmarkReport
                );

            optimizationEndTime = Instant.now();

            _logger.info("YAWL Actor Model optimization complete");

            return new OptimizationResult(
                optimizationStartTime,
                optimizationEndTime,
                analysisReport,
                calculateOptimizationScore(analysisReport)
            );

        } catch (Exception e) {
            _logger.error("Optimization failed: {}", e.getMessage(), e);
            throw new RuntimeException("Virtual thread optimization failed", e);
        } finally {
            optimizing = false;
            shutdownComponents();
        }
    }

    /**
     * Initialize all analysis components.
     */
    private void initializeComponents() {
        _logger.debug("Initializing analysis components");

        carrierThreadOptimizer = new CarrierThreadOptimizer(meterRegistry);
        virtualThreadProfiler = new VirtualThreadProfiler();
        structuredTaskScopeTester = new StructuredTaskScopeIntegrationTester(tracer);
        stackDepthAnalyzer = new StackDepthAnalyzer();
        lifecycleManager = new VirtualThreadLifecycleManager();
        performanceBenchmarkSuite = new PerformanceBenchmarkSuite(meterRegistry);

        _logger.info("All analysis components initialized");
    }

    /**
     * Run carrier thread optimization in parallel.
     */
    private CarrierThreadOptimizer.OptimizationReport runCarrierThreadOptimization() {
        try {
            // Submit optimization task
            var future = executor.submit(() -> {
                _logger.info("Running carrier thread optimization...");
                return carrierThreadOptimizer.runOptimizationTests();
            });

            // Wait for completion with timeout
            return future.get(2, TimeUnit.MINUTES);

        } catch (Exception e) {
            _logger.error("Carrier thread optimization failed: {}", e.getMessage());
            throw new RuntimeException("Carrier thread optimization failed", e);
        }
    }

    /**
     * Run virtual thread profiling in parallel.
     */
    private VirtualThreadProfiler.ProfileReport runVirtualThreadProfiling() {
        try {
            // Submit profiling task
            var future = executor.submit(() -> {
                _logger.info("Starting virtual thread profiling...");
                virtualThreadProfiler.startProfiling();

                // Allow profiling to run for the configured duration
                Thread.sleep(60000); // 60 seconds

                _logger.info("Stopping virtual thread profiling...");
                return virtualThreadProfiler.stopProfiling();
            });

            // Wait for completion with timeout
            return future.get(2, TimeUnit.MINUTES);

        } catch (Exception e) {
            _logger.error("Virtual thread profiling failed: {}", e.getMessage());
            throw new RuntimeException("Virtual thread profiling failed", e);
        }
    }

    /**
     * Run structured concurrency validation in parallel.
     */
    private StructuredTaskScopeIntegrationTester.IntegrationTestResults runStructuredConcurrencyValidation() {
        try {
            // Submit validation task
            var future = executor.submit(() -> {
                _logger.info("Running structured concurrency validation...");
                return structuredTaskScopeTester.runIntegrationTests();
            });

            // Wait for completion with timeout
            return future.get(1, TimeUnit.MINUTES);

        } catch (Exception e) {
            _logger.error("Structured concurrency validation failed: {}", e.getMessage());
            throw new RuntimeException("Structured concurrency validation failed", e);
        }
    }

    /**
     * Run stack depth analysis in parallel.
     */
    private StackDepthAnalyzer.StackDepthAnalysisReport runStackDepthAnalysis() {
        try {
            // Submit analysis task
            var future = executor.submit(() -> {
                _logger.info("Starting stack depth analysis...");
                stackDepthAnalyzer.startAnalysis();

                // Allow analysis to run for the configured duration
                Thread.sleep(60000); // 60 seconds

                _logger.info("Stopping stack depth analysis...");
                return stackDepthAnalyzer.stopAnalysis();
            });

            // Wait for completion with timeout
            return future.get(2, TimeUnit.MINUTES);

        } catch (Exception e) {
            _logger.error("Stack depth analysis failed: {}", e.getMessage());
            throw new RuntimeException("Stack depth analysis failed", e);
        }
    }

    /**
     * Run lifecycle validation in parallel.
     */
    private VirtualThreadLifecycleManager.LifecycleManagementReport runLifecycleValidation() {
        try {
            // Submit validation task
            var future = executor.submit(() -> {
                _logger.info("Starting lifecycle management validation...");
                lifecycleManager.startLifecycleValidation();

                // Allow validation to run for the configured duration
                Thread.sleep(60000); // 60 seconds

                _logger.info("Stopping lifecycle management validation...");
                return lifecycleManager.stopLifecycleValidation();
            });

            // Wait for completion with timeout
            return future.get(2, TimeUnit.MINUTES);

        } catch (Exception e) {
            _logger.error("Lifecycle validation failed: {}", e.getMessage());
            throw new RuntimeException("Lifecycle validation failed", e);
        }
    }

    /**
     * Run performance benchmarking in parallel.
     */
    private PerformanceBenchmarkSuite.BenchmarkReport runPerformanceBenchmarking() {
        try {
            // Submit benchmarking task
            var future = executor.submit(() -> {
                _logger.info("Running performance benchmarks...");
                return performanceBenchmarkSuite.runBenchmarkSuite();
            });

            // Wait for completion with timeout
            return future.get(5, TimeUnit.MINUTES);

        } catch (Exception e) {
            _logger.error("Performance benchmarking failed: {}", e.getMessage());
            throw new RuntimeException("Performance benchmarking failed", e);
        }
    }

    /**
     * Calculate optimization score based on analysis results.
     */
    private double calculateOptimizationScore(AnalysisReportGenerator.AnalysisReport report) {
        if (report == null || report.recommendations().isEmpty()) {
            return 0.0;
        }

        // Calculate score based on:
        // - Number of high-priority recommendations (lower is better)
        // - Number of critical bottlenecks (lower is better)
        // - Overall system health indicators

        long highPriorityRecs = report.recommendations().stream()
            .filter(r -> r.priority().equals("HIGH"))
            .count();

        long criticalBottlenecks = report.bottlenecks().stream()
            .filter(b -> b.severity().equals("HIGH"))
            .count();

        long totalRecommendations = report.recommendations().size();
        long totalBottlenecks = report.bottlenecks().size();

        // Score calculation (0.0 to 1.0)
        double recommendationScore = totalRecommendations > 0 ?
            (totalRecommendations - highPriorityRecs) / (double) totalRecommendations : 1.0;

        double bottleneckScore = totalBottlenecks > 0 ?
            (totalBottlenecks - criticalBottlenecks) / (double) totalBottlenecks : 1.0;

        // Overall score with weights
        double overallScore = (recommendationScore * 0.6) + (bottleneckScore * 0.4);

        return Math.max(0.0, Math.min(1.0, overallScore));
    }

    /**
     * Shutdown all components gracefully.
     */
    private void shutdownComponents() {
        _logger.debug("Shutting down optimization components");

        try {
            // Shutdown executor
            executor.shutdown();
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }

            _logger.info("All components shut down successfully");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
            _logger.warn("Interrupted during component shutdown");
        }
    }

    /**
     * Get optimization status.
     */
    public OptimizationStatus getStatus() {
        if (!optimizing) {
            return new OptimizationStatus("NOT_STARTED", null, null);
        }

        Duration duration = Duration.between(optimizationStartTime, Instant.now());
        String status = "IN_PROGRESS";

        // Estimate progress based on typical timing
        if (duration.toMinutes() > 10) {
            status = "NEARLY_COMPLETE";
        } else if (duration.toMinutes() > 5) {
            status = "IN_LATE_PHASE";
        } else if (duration.toMinutes() > 2) {
            status = "IN_MID_PHASE";
        } else {
            status = "IN_EARLY_PHASE";
        }

        return new OptimizationStatus(status, optimizationStartTime, duration);
    }

    /**
     * Check if optimization is running.
     */
    public boolean isOptimizing() {
        return optimizing;
    }

    /**
     * Get recommended configuration based on optimization results.
     */
    public VirtualThreadConfiguration getRecommendedConfiguration() {
        if (!optimizing) {
            throw new IllegalStateException("No optimization results available");
        }

        // This would parse the latest analysis report to extract recommendations
        // For now, return a reasonable default configuration
        return new VirtualThreadConfiguration(
            8,  // Default carrier threads
            1000,  // Max virtual threads
            100,  // Monitoring interval (ms)
            30,   // Task timeout (seconds)
            true, // Enable structured concurrency
            true  // Enable lifecycle monitoring
        );
    }

    // Record classes
    public record OptimizationResult(
        Instant startTime,
        Instant endTime,
        AnalysisReportGenerator.AnalysisReport analysisReport,
        double optimizationScore
    ) {
        public Duration getDuration() {
            return Duration.between(startTime, endTime);
        }
    }

    public record OptimizationStatus(
        String status,
        Instant startTime,
        Duration duration
    ) {}

    public record VirtualThreadConfiguration(
        int carrierThreadCount,
        int maxVirtualThreads,
        int monitoringIntervalMs,
        int taskTimeoutSeconds,
        boolean enableStructuredConcurrency,
        boolean enableLifecycleMonitoring
    ) {}
}