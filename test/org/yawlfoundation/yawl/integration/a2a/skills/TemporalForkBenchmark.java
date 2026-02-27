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
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.a2a.skills;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.yawlfoundation.yawl.integration.temporal.AllPathsForkPolicy;
import org.yawlfoundation.yawl.integration.temporal.TemporalForkEngine;
import org.yawlfoundation.yawl.integration.temporal.TemporalForkResult;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * JMH benchmark for TemporalForkEngine performance evaluation.
 *
 * This benchmark suite measures:
 * - Fork execution performance with varying numbers of concurrent forks (10, 100, 1000)
 * - XML serialization performance for synthetic case building
 * - Memory usage during parallel fork execution
 * - Real-world execution patterns with different task configurations
 *
 * Target metrics:
 * - Fork throughput: > 100 forks/sec for 100 forks
 * - Serialization time: < 1ms for 10 tasks
 * - Memory overhead: < 100MB for 1000 parallel forks
 * - Completion rate: 100% within timeout
 *
 * @author YAWL Performance Team
 * @date 2026-02-27
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class TemporalForkBenchmark {

    /**
     * Benchmark fork execution with 10 concurrent forks.
     * Measures how quickly the engine can process 10 parallel execution paths.
     */
    @Benchmark
    public void benchmarkForkExecution_10Forks(Blackhole bh) throws Exception {
        runTemporalFork(10, bh);
    }

    /**
     * Benchmark fork execution with 100 concurrent forks.
     * Measures scalability under moderate load.
     */
    @Benchmark
    public void benchmarkForkExecution_100Forks(Blackhole bh) throws Exception {
        runTemporalFork(100, bh);
    }

    /**
     * Benchmark fork execution with 1000 concurrent forks.
     * Measures scalability under high load and resource usage.
     */
    @Benchmark
    public void benchmarkForkExecution_1000Forks(Blackhole bh) throws Exception {
        runTemporalFork(1000, bh);
    }

    /**
     * Benchmark XML serialization performance.
     * Measures the time required to build synthetic case XML for different task counts.
     */
    @Benchmark
    public void benchmarkXmlSerialization(Blackhole bh) {
        List<String> taskNames = List.of("ReviewApplication", "ApproveApplication", "RejectApplication");

        // Build synthetic case XML multiple times to average out variations
        for (int i = 0; i < 10; i++) {
            String xml = buildSyntheticCaseXml(taskNames);
            bh.consume(xml);
        }
    }

    /**
     * Benchmark memory usage during parallel fork execution.
     * Tracks heap allocation when running multiple concurrent forks.
     */
    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchmarkMemoryUsage(Blackhole bh) throws Exception {
        // Measure memory before and after fork execution
        Runtime runtime = Runtime.getRuntime();
        long before = runtime.totalMemory() - runtime.freeMemory();

        // Execute a large number of forks to trigger memory allocation
        runTemporalFork(1000, bh);

        long after = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = after - before;

        // Record memory usage for analysis
        bh.consume(memoryUsed);
    }

    /**
     * Helper method to run TemporalFork with specified number of tasks.
     */
    private void runTemporalFork(int taskCount, Blackhole bh) throws Exception {
        // Generate task names for the specified count
        List<String> taskNames = generateTaskNames(taskCount);

        // Create synthetic case XML
        String syntheticCaseXml = buildSyntheticCaseXml(taskNames);

        // Create the TemporalForkEngine using lambda constructor
        TemporalForkEngine engine = TemporalForkEngine.forIntegration(
            caseId -> syntheticCaseXml,
            xml -> taskNames,
            (xml, taskId) -> xml + "<executed>" + taskId + "</executed>"
        );

        // Execute the fork with a generous timeout
        TemporalForkResult result = engine.fork(
            "benchmark-case-" + taskCount,
            new AllPathsForkPolicy(taskNames.size()),
            Duration.ofSeconds(30)
        );

        // Validate results and consume them to prevent JIT optimizations
        validateForkResult(result, taskCount);
        bh.consume(result);
    }

    /**
     * Generate task names for benchmarking.
     */
    private List<String> generateTaskNames(int count) {
        return java.util.stream.IntStream.range(0, count)
            .mapToObj(i -> "Task-" + (i + 1))
            .collect(Collectors.toList());
    }

    /**
     * Build synthetic case XML for testing.
     */
    private String buildSyntheticCaseXml(List<String> taskNames) {
        return "<case id=\"benchmark-case\"><tasks>"
            + taskNames.stream().map(t -> "<task>" + t + "</task>").collect(Collectors.joining())
            + "</tasks></case>";
    }

    /**
     * Validate fork result to ensure proper execution.
     */
    private void validateForkResult(TemporalForkResult result, int expectedTaskCount) {
        if (result.forks().isEmpty()) {
            throw new IllegalStateException("No forks completed");
        }

        if (!result.allForksCompleted()) {
            throw new IllegalStateException("Not all forks completed within timeout");
        }

        // Validate that the number of forks matches the number of tasks
        if (result.completedForks() != expectedTaskCount) {
            throw new IllegalStateException(
                "Expected " + expectedTaskCount + " forks, got " + result.completedForks()
            );
        }
    }

    /**
     * Main method for running benchmarks standalone.
     */
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
            .include(TemporalForkBenchmark.class.getSimpleName())
            .warmupIterations(3)
            .measurementIterations(5)
            .forks(1)
            .build();

        new Runner(opt).run();
    }
}