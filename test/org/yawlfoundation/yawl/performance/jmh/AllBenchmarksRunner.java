/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.performance.jmh;

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Runs all JMH benchmarks for virtual thread performance analysis.
 *
 * Executes:
 * 1. IOBoundBenchmark - I/O-bound operations comparison
 * 2. EventLoggerBenchmark - Event logging throughput
 * 3. InterfaceBClientBenchmark - HTTP client performance
 * 4. StructuredConcurrencyBenchmark - Structured concurrency vs CompletableFuture
 * 5. MemoryUsageBenchmark - Memory efficiency comparison
 * 6. WorkflowExecutionBenchmark - Real-world workflow patterns
 *
 * Blue Ocean additions (80/20 high-ROI benchmarks):
 * 7. AdaptiveLoadBenchmark - Binary-search saturation cliff detection
 * 8. ChaosEngineeringBenchmark - Fault injection under sustained load
 * 9. PropertyBasedPerformanceBenchmark - Invariant validation across random inputs
 *
 * Results are output to:
 * - Console (human-readable)
 * - JSON file (machine-readable)
 *
 * Usage:
 *   java -jar benchmarks.jar
 *   mvn exec:java -Dexec.mainClass="org.yawlfoundation.yawl.performance.jmh.AllBenchmarksRunner"
 *
 * Blue Ocean 80/20 fast mode (top-2 benchmarks, ~15 min):
 *   java -jar benchmarks.jar --fast
 *
 * @author YAWL Performance Team
 * @date 2026-02-16
 */
public class AllBenchmarksRunner {

    public static void main(String[] args) throws RunnerException {
        boolean fastMode = args.length > 0 && "--fast".equals(args[0]);

        System.out.println("=".repeat(80));
        System.out.println("YAWL Virtual Threads Performance Benchmark Suite");
        if (fastMode) {
            System.out.println("Mode: FAST (80/20 — top-2 blue ocean benchmarks only)");
        }
        System.out.println("=".repeat(80));
        System.out.println();
        System.out.println("This suite covers:");
        System.out.println("  - I/O-bound operations (platform vs virtual threads)");
        System.out.println("  - Event notification patterns");
        System.out.println("  - Structured concurrency vs CompletableFuture");
        System.out.println("  - Memory usage and GC pressure");
        System.out.println("  - Real workflow execution");
        System.out.println("  [Blue Ocean] Adaptive saturation cliff detection");
        System.out.println("  [Blue Ocean] Chaos engineering: fault injection under load");
        System.out.println("  [Blue Ocean] Property-based invariant validation");
        System.out.println();
        System.out.println("Estimated runtime: " + (fastMode ? "~15 minutes" : "45-60 minutes"));
        System.out.println("=".repeat(80));
        System.out.println();

        String includePattern = fastMode
            ? "org.yawlfoundation.yawl.performance.jmh.(AdaptiveLoad|ChaosEngineering)Benchmark"
            : "org.yawlfoundation.yawl.performance.jmh.*Benchmark";

        Options opt = new OptionsBuilder()
            .include(includePattern)
            .forks(1)
            .warmupIterations(3)
            .measurementIterations(5)
            .resultFormat(org.openjdk.jmh.results.format.ResultFormatType.JSON)
            .result("target/jmh-results.json")
            .build();

        new Runner(opt).run();

        System.out.println();
        System.out.println("=".repeat(80));
        System.out.println("Benchmark suite completed!");
        System.out.println("Results saved to: target/jmh-results.json");
        if (!fastMode) {
            System.out.println("Tip: rerun with '--fast' for 80/20 quick iteration (~15 min)");
        }
        System.out.println("=".repeat(80));
    }
}
