/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.performance;

import java.util.concurrent.TimeUnit;

/**
 * Simple test runner to verify benchmark functionality
 * without requiring full compilation of all modules.
 */
public class BenchmarkRunner {

    public static void main(String[] args) {
        System.out.println("YAWL Performance Benchmark Suite");
        System.out.println("=================================");

        // Test basic functionality
        testBasicFunctionality();

        System.out.println("\nAll tests passed successfully!");
    }

    private static void testBasicFunctionality() {
        System.out.println("Running basic functionality tests...");

        // Test 1: Check that classes can be loaded
        try {
            Class.forName("org.yawlfoundation.yawl.performance.BenchmarkConfig");
            System.out.println("✓ BenchmarkConfig loaded successfully");
        } catch (ClassNotFoundException e) {
            System.err.println("✗ Failed to load BenchmarkConfig: " + e.getMessage());
            throw new RuntimeException(e);
        }

        // Test 2: Check threshold values
        BenchmarkConfig.PerformanceGateChecker checker = new BenchmarkConfig.PerformanceGateChecker();

        // Test case creation rate threshold
        boolean caseRatePassed = checker.checkCaseCreationRate();
        if (!caseRatePassed) {
            System.err.println("✗ Case creation rate check failed");
            throw new RuntimeException("Performance gate failed");
        }
        System.out.println("✓ Case creation rate check passed");

        // Test 3: Memory profiler creation
        try {
            MemoryUsageProfiler profiler = new MemoryUsageProfiler();
            System.out.println("✓ MemoryUsageProfiler created successfully");

            // Test memory analysis
            profiler.analyzeMemoryRegions();
            System.out.println("✓ Memory analysis completed");

            profiler.shutdown();
            System.out.println("✓ Memory profiler shutdown successfully");
        } catch (Exception e) {
            System.err.println("✗ Memory profiler test failed: " + e.getMessage());
            throw new RuntimeException(e);
        }

        // Test 4: Thread contention analyzer creation
        try {
            ThreadContentionAnalyzer analyzer = new ThreadContentionAnalyzer();
            System.out.println("✓ ThreadContentionAnalyzer created successfully");

            // Test basic analysis
            analyzer.analyzeSynchronizationPerformance();
            System.out.println("✓ Synchronization analysis completed");

            analyzer.shutdown();
            System.out.println("✓ Thread contentions analyzer shutdown successfully");
        } catch (Exception e) {
            System.err.println("✗ Thread contentions analyzer test failed: " + e.getMessage());
            throw new RuntimeException(e);
        }

        System.out.println("All basic functionality tests completed successfully!");
    }
}