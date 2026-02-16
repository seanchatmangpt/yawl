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
 * Results are output to:
 * - Console (human-readable)
 * - JSON file (machine-readable)
 * - CSV file (for spreadsheet analysis)
 *
 * Usage:
 *   java -jar benchmarks.jar
 *   mvn exec:java -Dexec.mainClass="org.yawlfoundation.yawl.performance.jmh.AllBenchmarksRunner"
 *
 * @author YAWL Performance Team
 * @date 2026-02-16
 */
public class AllBenchmarksRunner {

    public static void main(String[] args) throws RunnerException {
        System.out.println("=".repeat(80));
        System.out.println("YAWL Virtual Threads Performance Benchmark Suite");
        System.out.println("=".repeat(80));
        System.out.println();
        System.out.println("This suite compares platform threads vs virtual threads across:");
        System.out.println("  - I/O-bound operations");
        System.out.println("  - Event notification patterns");
        System.out.println("  - HTTP client performance");
        System.out.println("  - Structured concurrency");
        System.out.println("  - Memory usage");
        System.out.println("  - Real workflow execution");
        System.out.println();
        System.out.println("Estimated runtime: 30-45 minutes");
        System.out.println("=".repeat(80));
        System.out.println();

        Options opt = new OptionsBuilder()
            .include("org.yawlfoundation.yawl.performance.jmh.*Benchmark")
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
        System.out.println("=".repeat(80));
    }
}
