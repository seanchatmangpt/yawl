/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.performance.jmh.autonomous;

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * JMH Benchmark Suite for Autonomous Agent Integration Performance
 *
 * Comprehensive suite testing v6.0.0-GA autonomous agent capabilities:
 *
 * 1. AgentCommunicationBenchmark
 *    - Agent registration and discovery latency
 *    - Message processing throughput
 *    - Authentication overhead
 *    - Target: Discovery latency < 50ms, Throughput > 1000 ops/sec
 *
 * 2. ResourceAllocationBenchmark
 *    - Resource allocation accuracy under load
 *    - Load balancing efficiency
 *    - Resource contention handling
 *    - Target: Allocation accuracy > 95%
 *
 * 3. AgentHandoffBenchmark
 *    - Handoff success rate and performance
 *    - Cross-agent state preservation
 *    - Handoff failure recovery
 *    - Target: Success rate > 99%, Latency < 100ms
 *
 * 4. AutonomousAgentPerformanceBenchmark
 *    - End-to-end multi-agent performance
 *    - Dynamic capability matching
 *    - Multi-agent workflow coordination
 *    - Validates all performance targets
 *
 * Usage:
 *   java -jar autonomous-agent-benchmarks.jar
 *   mvn exec:java -Dexec.mainClass="org.yawlfoundation.yawl.performance.jmh.autonomous.AutonomousAgentBenchmarkSuite"
 *
 * Output formats:
 *   - Console (human-readable summary)
 *   - JSON (machine-readable results)
 *   - CSV (spreadsheet analysis)
 *
 * @author YAWL Performance Team
 * @version 6.0.0-GA
 */
public class AutonomousAgentBenchmarkSuite {

    public static void main(String[] args) throws RunnerException {
        System.out.println("=".repeat(80));
        System.out.println("YAWL v6.0.0-GA Autonomous Agent Integration Benchmark Suite");
        System.out.println("=".repeat(80));
        System.out.println();

        System.out.println("Performance Targets:");
        System.out.println("  • Agent discovery latency: < 50ms");
        System.out.println("  • Message processing throughput: > 1000 ops/sec");
        System.out.println("  • Handoff success rate: > 99%");
        System.out.println("  • Resource allocation accuracy: > 95%");
        System.out.println();

        System.out.println("Test Categories:");
        System.out.println("  1. Agent Communication & Discovery");
        System.out.println("  2. Resource Allocation & Load Balancing");
        System.out.println("  3. Agent Handoff & Reliability");
        System.out.println("  4. End-to-End Multi-Agent Performance");
        System.out.println();

        System.out.println("Running comprehensive benchmarks...");
        System.out.println();

        // Configure benchmark options
        Options options = new OptionsBuilder()
            .include("org.yawlfoundation.yawl.performance.jmh.autonomous.*")
            .shouldFailOnError(true)
            .jvmArgs("-Xms4g", "-Xmx6g", "-XX:+UseG1GC")
            .warmupIterations(3)
            .warmupTime("5s")
            .measurementIterations(5)
            .measurementTime("10s")
            .forks(1)
            .threads(1)
            .outputFormat("json")
            .resultFormat("json")
            .result("results/autonomous-agent-benchmark-results.json")
            .build();

        // Run all benchmarks
        Runner runner = new Runner(options);
        runner.run();

        System.out.println();
        System.out.println("=".repeat(80));
        System.out.println("Benchmark Suite Completed");
        System.out.println("Results saved to: results/autonomous-agent-benchmark-results.json");
        System.out.println("=".repeat(80));
    }
}