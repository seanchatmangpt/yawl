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

package org.yawlfoundation.yawl.validation;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerProvider;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.sdk.trace.SdkTracerProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Comprehensive validation suite for the YAWL Actor Model Phase 3 validation plan.
 *
 * <p>This suite tests:</p>
 * <ul>
 *   <li>Carrier thread optimization (1, 2, 4, 8, 16, 32 threads)</li>
 *   <li>Virtual thread behavior monitoring</li>
 *   <li>StructuredTaskScope integration validation</li>
 *   <li>Stack depth analysis</li>
 *   <li>Virtual thread lifecycle management</li>
 *   <li>Performance benchmarking</li>
 *   <li>Optimization report generation</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0
 * @since 6.0
 */
@Tag("integration")
@Tag("validation")
@Tag("actor-model")
@Execution(ExecutionMode.SAME_THREAD)
class ActorModelValidationSuite {

    @Mock
    private MeterRegistry meterRegistry;

    private SimpleMeterRegistry simpleRegistry;
    private Tracer tracer;
    private ActorModelOptimizer optimizer;

    private static final Path OUTPUT_DIR = Paths.get("validation-reports");

    @BeforeAll
    static void setupClass() {
        // Create output directory
        try {
            if (!Files.exists(OUTPUT_DIR)) {
                Files.createDirectories(OUTPUT_DIR);
            }
        } catch (Exception e) {
            fail("Failed to create output directory: " + e.getMessage());
        }
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        simpleRegistry = new SimpleMeterRegistry();

        // Create tracer
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder().build();
        tracer = tracerProvider.get("ActorModelValidationSuite");

        optimizer = new ActorModelOptimizer(simpleRegistry, tracer);
    }

    @AfterEach
    void tearDown() {
        // Clean up any running optimizations
        if (optimizer.isOptimizing()) {
            // Note: In real implementation, would have proper shutdown mechanism
            System.out.println("Warning: Optimizer was still running during teardown");
        }
    }

    @Test
    @DisplayName("Test Phase 3: Carrier Thread Optimization")
    void testPhase3CarrierThreadOptimization() {
        System.out.println("\n=== Testing Phase 3: Carrier Thread Optimization ===");

        // Test carrier thread configurations
        var carrierOptimizer = new CarrierThreadOptimizer(simpleRegistry);

        assertDoesNotThrow(() -> {
            var report = carrierOptimizer.runOptimizationTests();

            // Verify results
            assertNotNull(report);
            assertEquals(6, report.results().size()); // 6 configurations tested

            // Verify optimal configuration found
            assertTrue(report.optimalConfig() >= 1);
            assertTrue(report.optimalConfig() <= 32);

            // Verify metrics are reasonable
            var optimalResult = report.results().get(report.optimalConfig());
            assertTrue(optimalResult.successRate() >= 90);
            assertTrue(optimalResult.throughput() > 0);
            assertTrue(optimalResult.carrierUtilization() <= 90);

            System.out.println("Optimal carrier threads: " + report.optimalConfig());
            System.out.println("Success rate: " + optimalResult.successRate() + "%");
            System.out.println("Throughput: " + optimalResult.throughput() + " ops/sec");
            System.out.println("Carrier utilization: " + optimalResult.carrierUtilization() + "%");
        });
    }

    @Test
    @DisplayName("Test Phase 3: Virtual Thread Behavior Profiling")
    void testPhase3VirtualThreadProfiling() {
        System.out.println("\n=== Testing Phase 3: Virtual Thread Behavior Profiling ===");

        var profiler = new VirtualThreadProfiler();

        assertDoesNotThrow(() -> {
            // Start profiling
            profiler.startProfiling();

            // Allow profiling to run
            Thread.sleep(30000); // 30 seconds

            // Stop profiling and get results
            var report = profiler.stopProfiling();

            // Verify results
            assertNotNull(report);
            assertTrue(report.snapshots().size() > 0);

            // Check metrics are reasonable
            double parkDuration = report.avgParkDurationMs();
            assertTrue(parkDuration >= 0);
            assertTrue(parkDuration < 1000); // Less than 1 second

            System.out.println("Average park duration: " + parkDuration + " ms");
            System.out.println("Peak virtual threads: " + report.getPeakVirtualThreads());
            System.out.println("Carrier utilization: " + report.getCarrierUtilization() + "%");
        });
    }

    @Test
    @DisplayName("Test Phase 3: StructuredTaskScope Integration")
    void testPhase3StructuredTaskScopeIntegration() {
        System.out.println("\n=== Testing Phase 3: StructuredTaskScope Integration ===");

        var tester = new StructuredTaskScopeIntegrationTester(tracer);

        assertDoesNotThrow(() -> {
            var results = tester.runIntegrationTests();

            // Verify results
            assertNotNull(results);
            assertNotNull(results.summary());

            // Check integration metrics
            var summary = results.summary();
            assertTrue(summary.totalTasks() > 0);
            assertTrue(summary.successRate() >= 90);
            assertTrue(summary.throughput() > 0);

            System.out.println("Total tasks: " + summary.totalTasks());
            System.out.println("Success rate: " + summary.successRate() + "%");
            System.out.println("Throughput: " + summary.throughput() + " ops/sec");
        });
    }

    @Test
    @DisplayName("Test Phase 3: Stack Depth Analysis")
    void testPhase3StackDepthAnalysis() {
        System.out.println("\n=== Testing Phase 3: Stack Depth Analysis ===");

        var analyzer = new StackDepthAnalyzer();

        assertDoesNotThrow(() -> {
            // Start analysis
            analyzer.startAnalysis();

            // Allow analysis to run
            Thread.sleep(30000); // 30 seconds

            // Stop analysis and get results
            var report = analyzer.stopAnalysis();

            // Verify results
            assertNotNull(report);
            assertTrue(report.samples().size() > 0);

            // Check stack depth metrics
            double avgDepth = report.averageStackDepth();
            assertTrue(avgDepth > 0);
            assertTrue(avgDepth < 10000); // Reasonable upper bound

            System.out.println("Average stack depth: " + avgDepth);
            System.out.println("Maximum stack depth: " + report.maximumStackDepth());
            System.out.println("Stack overflows: " + report.stackOverflowCount());
        });
    }

    @Test
    @DisplayName("Test Phase 3: Virtual Thread Lifecycle Management")
    void testPhase3VirtualThreadLifecycleManagement() {
        System.out.println("\n=== Testing Phase 3: Virtual Thread Lifecycle Management ===");

        var lifecycleManager = new VirtualThreadLifecycleManager();

        assertDoesNotThrow(() -> {
            // Start validation
            lifecycleManager.startLifecycleValidation();

            // Allow validation to run
            Thread.sleep(30000); // 30 seconds

            // Stop validation and get results
            var report = lifecycleManager.stopLifecycleValidation();

            // Verify results
            assertNotNull(report);
            assertTrue(report.totalCreated() >= 0);
            assertTrue(report.totalTerminated() >= 0);

            System.out.println("Total threads created: " + report.totalCreated());
            System.out.println("Total threads terminated: " + report.totalTerminated());
            System.out.println("Leaks detected: " + report.totalLeaksDetected());
        });
    }

    @Test
    @DisplayName("Test Phase 3: Performance Benchmarking")
    void testPhase3PerformanceBenchmarking() {
        System.out.println("\n=== Testing Phase 3: Performance Benchmarking ===");

        var benchmarkSuite = new PerformanceBenchmarkSuite(simpleRegistry);

        assertDoesNotThrow(() -> {
            var report = benchmarkSuite.runBenchmarkSuite();

            // Verify results
            assertNotNull(report);
            assertFalse(report.results().isEmpty());

            // Check benchmark metrics
            var benchmarkResults = report.results().values().iterator().next();
            var workloadResults = benchmarkResults.values().iterator().next();

            assertTrue(workloadResults.totalOperations() > 0);
            assertTrue(workloadResults.successRate() >= 90);
            assertTrue(workloadResults.p99LatencyMs() >= 0);

            System.out.println("Total operations: " + workloadResults.totalOperations());
            System.out.println("Success rate: " + workloadResults.successRate() + "%");
            System.out.println("p99 latency: " + workloadResults.p99LatencyMs() + " ms");
        });
    }

    @Test
    @DisplayName("Test Phase 3: Comprehensive Optimization Workflow")
    void testPhase3ComprehensiveOptimization() {
        System.out.println("\n=== Testing Phase 3: Comprehensive Optimization Workflow ===");

        // Run complete optimization workflow
        assertDoesNotThrow(() -> {
            var result = optimizer.optimizeVirtualThreadConfiguration();

            // Verify results
            assertNotNull(result);
            assertNotNull(result.analysisReport());
            assertNotNull(result.optimizationScore());

            // Check optimization score
            double score = result.optimizationScore();
            assertTrue(score >= 0);
            assertTrue(score <= 1);

            // Check analysis report
            var analysisReport = result.analysisReport();
            assertNotNull(analysisReport.recommendations());
            assertNotNull(analysisReport.bottlenecks());
            assertNotNull(analysisReport.summary());

            System.out.println("Optimization score: " + score);
            System.out.println("Recommendations: " + analysisReport.recommendations().size());
            System.out.println("Bottlenecks: " + analysisReport.bottlenecks().size());
            System.out.println("Duration: " + result.getDuration().toSeconds() + " seconds");
        });
    }

    @Test
    @DisplayName("Test Phase 3: Error Handling and Resilience")
    void testPhase3ErrorHandlingAndResilience() {
        System.out.println("\n=== Testing Phase 3: Error Handling and Resilience ===");

        // Test with invalid configurations
        assertThrows(IllegalArgumentException.class, () -> {
            new CarrierThreadOptimizer(null);
        });

        // Test concurrent access
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            for (int i = 0; i < 3; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        var carrierOptimizer = new CarrierThreadOptimizer(simpleRegistry);
                        var report = carrierOptimizer.runOptimizationTests();
                        assertNotNull(report);
                    } catch (Exception e) {
                        fail("Thread " + threadId + " failed: " + e.getMessage());
                    }
                });
            }
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    @DisplayName("Test Phase 3: Configuration Recommendations")
    void testPhase3ConfigurationRecommendations() {
        System.out.println("\n=== Testing Phase 3: Configuration Recommendations ===");

        // Run optimization to get recommendations
        var result = optimizer.optimizeVirtualThreadConfiguration();
        var analysisReport = result.analysisReport();

        // Check recommendations exist and are reasonable
        assertFalse(analysisReport.recommendations().isEmpty());

        for (var rec : analysisReport.recommendations()) {
            assertNotNull(rec.title());
            assertNotNull(rec.description());
            assertNotNull(rec.priority());
            assertNotNull(rec.category());

            // Check priority levels
            assertTrue(rec.priority().equals("HIGH") ||
                     rec.priority().equals("MEDIUM") ||
                     rec.priority().equals("LOW"));
        }

        // Check bottlenecks exist and are reasonable
        assertFalse(analysisReport.bottlenecks().isEmpty());

        for (var bottleneck : analysisReport.bottlenecks()) {
            assertNotNull(bottleneck.title());
            assertNotNull(bottleneck.description());
            assertNotNull(bottleneck.severity());
            assertNotNull(bottleneck.suggestion());

            // Check severity levels
            assertTrue(bottleneck.severity().equals("HIGH") ||
                     bottleneck.severity().equals("MEDIUM") ||
                     bottleneck.severity().equals("LOW"));
        }

        System.out.println("Generated " + analysisReport.recommendations().size() + " recommendations");
        System.out.println("Identified " + analysisReport.bottlenecks().size() + " bottlenecks");
    }

    @Test
    @DisplayName("Test Phase 3: Report Generation")
    void testPhase3ReportGeneration() {
        System.out.println("\n=== Testing Phase 3: Report Generation ===");

        // Generate report
        var result = optimizer.optimizeVirtualThreadConfiguration();
        var analysisReport = result.analysisReport();

        // Check report structure
        assertNotNull(analysisReport.generatedAt());
        assertNotNull(analysisReport.rawData());
        assertFalse(analysisReport.recommendations().isEmpty());
        assertFalse(analysisReport.bottlenecks().isEmpty());
        assertNotNull(analysisReport.summary());

        // Check summary
        var summary = analysisReport.summary();
        assertEquals(analysisReport.recommendations().size(), summary.totalRecommendations());
        assertEquals(analysisReport.bottlenecks().size(), summary.totalBottlenecks());

        // Verify output files were created
        assertDoesNotThrow(() -> {
            var files = Files.list(OUTPUT_DIR);
            var reportFiles = files.filter(f -> f.toString().endsWith(".html"))
                                   .toArray(java.io.File[]::new);
            assertTrue(reportFiles.length > 0);

            System.out.println("Generated " + reportFiles.length + " report files");
        });
    }

    @Test
    @DisplayName("Test Phase 3: Optimization Status Tracking")
    void testPhase3OptimizationStatusTracking() {
        System.out.println("\n=== Testing Phase 3: Optimization Status Tracking ===");

        // Check initial status
        var initialStatus = optimizer.getStatus();
        assertEquals("NOT_STARTED", initialStatus.status());

        // Start optimization (don't wait for completion)
        Thread optimizationThread = new Thread(() -> {
            try {
                optimizer.optimizeVirtualThreadConfiguration();
            } catch (Exception e) {
                System.out.println("Optimization failed: " + e.getMessage());
            }
        });

        optimizationThread.start();

        // Check status during optimization
        try {
            Thread.sleep(2000); // Wait for optimization to start

            var runningStatus = optimizer.getStatus();
            assertTrue(runningStatus.status().contains("IN_PROGRESS"));
            assertNotNull(runningStatus.startTime());
            assertNotNull(runningStatus.duration());

            System.out.println("Optimization status: " + runningStatus.status());
            System.out.println("Duration: " + runningStatus.duration().toSeconds() + " seconds");

        } finally {
            // Wait for optimization to complete
            try {
                optimizationThread.join(120000); // 2 minute timeout
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @AfterAll
    static void cleanup() {
        // Clean up output directory
        try {
            if (Files.exists(OUTPUT_DIR)) {
                Files.walk(OUTPUT_DIR)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (Exception e) {
                            System.err.println("Failed to delete " + path + ": " + e.getMessage());
                        }
                    });
            }
        } catch (Exception e) {
            System.err.println("Failed to cleanup output directory: " + e.getMessage());
        }
    }
}