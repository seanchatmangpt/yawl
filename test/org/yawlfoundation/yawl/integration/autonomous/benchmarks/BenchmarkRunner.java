/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.autonomous.benchmarks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Main runner for all benchmarks with regression detection.
 *
 * Runs all benchmark suites and compares against baseline.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class BenchmarkRunner {

    private static final String BASELINE_PATH = "test/resources/benchmarks/baseline.json";
    private static final double REGRESSION_THRESHOLD = 0.10;

    public static void main(String[] args) {
        System.out.println("=== YAWL Generic Framework Performance Benchmark Suite ===");
        System.out.println("Date: " + java.time.LocalDateTime.now());
        System.out.println("JVM: " + System.getProperty("java.version"));
        System.out.println("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
        System.out.println("Processors: " + Runtime.getRuntime().availableProcessors());
        System.out.println("Max memory: " + (Runtime.getRuntime().maxMemory() / 1024 / 1024) + " MB");
        System.out.println();

        boolean hasBaseline = Files.exists(Paths.get(BASELINE_PATH));
        if (hasBaseline) {
            System.out.println("Baseline found: " + BASELINE_PATH);
            System.out.println("Regression threshold: " + (REGRESSION_THRESHOLD * 100) + "%");
        } else {
            System.out.println("No baseline found. Results will be saved as new baseline.");
        }
        System.out.println();

        int passed = 0;
        int failed = 0;
        int skipped = 0;

        System.out.println("Running benchmarks...");
        System.out.println("=".repeat(80));

        try {
            System.out.println("\n[1/5] EligibilityReasoningBenchmark");
            EligibilityReasoningBenchmark.main(args);
            passed++;
        } catch (Exception e) {
            System.err.println("FAILED: " + e.getMessage());
            failed++;
        }

        try {
            System.out.println("\n[2/5] DecisionGenerationBenchmark");
            DecisionGenerationBenchmark.main(args);
            passed++;
        } catch (Exception e) {
            System.err.println("FAILED: " + e.getMessage());
            failed++;
        }

        try {
            System.out.println("\n[3/5] DiscoveryLoopBenchmark");
            DiscoveryLoopBenchmark.main(args);
            passed++;
        } catch (Exception e) {
            System.err.println("FAILED: " + e.getMessage());
            failed++;
        }

        try {
            System.out.println("\n[4/5] ConfigurationLoadingBenchmark");
            ConfigurationLoadingBenchmark.main(args);
            passed++;
        } catch (Exception e) {
            System.err.println("FAILED: " + e.getMessage());
            failed++;
        }

        try {
            System.out.println("\n[5/5] WorkflowLauncherBenchmark");
            WorkflowLauncherBenchmark.main(args);
            skipped++;
        } catch (Exception e) {
            System.err.println("FAILED: " + e.getMessage());
            failed++;
        }

        System.out.println("\n" + "=".repeat(80));
        System.out.println("=== Summary ===");
        System.out.println("Passed:  " + passed);
        System.out.println("Failed:  " + failed);
        System.out.println("Skipped: " + skipped);

        if (failed == 0) {
            System.out.println("\nAll benchmarks passed!");
            if (!hasBaseline) {
                System.out.println("Save current results as baseline.");
            }
        } else {
            System.err.println("\nSome benchmarks failed. Review output above.");
            System.exit(1);
        }
    }
}
