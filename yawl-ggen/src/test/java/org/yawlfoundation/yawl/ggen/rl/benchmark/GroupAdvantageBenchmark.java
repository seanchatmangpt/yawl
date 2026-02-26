/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.rl.benchmark;

import org.yawlfoundation.yawl.ggen.rl.GroupAdvantage;
import org.yawlfoundation.yawl.ggen.rl.benchmark.RlBenchmarkRunner.BenchmarkResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Micro-benchmarks for GroupAdvantage computation.
 *
 * <p>Measures latency of:
 * <ul>
 *   <li>{@link GroupAdvantage#compute(List)} for different K values</li>
 *   <li>{@link GroupAdvantage#bestIndex()} selection</li>
 *   <li>Advantage sum-to-zero invariant verification</li>
 * </ul>
 *
 * <h2>Running Benchmarks</h2>
 * <pre>{@code
 * mvn test -Dtest=GroupAdvantageBenchmark -Dmaven.test.skip=false
 * }</pre>
 */
public final class GroupAdvantageBenchmark {

    private static final int WARMUP = 1000;
    private static final int ITERATIONS = 10000;

    private GroupAdvantageBenchmark() {
        throw new UnsupportedOperationException("Run via main()");
    }

    public static void main(String[] args) {
        System.out.println("=== GroupAdvantage Micro-Benchmarks ===\n");
        System.out.println("Warmup: " + WARMUP + " iterations");
        System.out.println("Measured: " + ITERATIONS + " iterations\n");

        List<BenchmarkResult> results = new ArrayList<>();

        // Benchmark compute() for different K values
        results.add(benchmarkComputeK1());
        results.add(benchmarkComputeK2());
        results.add(benchmarkComputeK4());
        results.add(benchmarkComputeK8());
        results.add(benchmarkComputeK16());

        // Benchmark bestIndex()
        results.add(benchmarkBestIndexK4());
        results.add(benchmarkBestIndexK16());

        // Print results
        System.out.println("\n=== Results ===\n");
        for (BenchmarkResult r : results) {
            System.out.printf("%s:%n", r.name());
            System.out.printf("  Mean: %.2f ns (%.4f ms)%n", r.meanLatencyNs(), r.meanLatencyMs());
            System.out.printf("  Std:  %.2f ns%n", r.stdLatencyNs());
            System.out.printf("  Min:  %d ns%n", r.minLatencyNs());
            System.out.printf("  Max:  %d ns%n", r.maxLatencyNs());
            System.out.printf("  P50:  %d ns%n", r.p50LatencyNs());
            System.out.printf("  P95:  %d ns%n", r.p95LatencyNs());
            System.out.printf("  P99:  %d ns%n", r.p99LatencyNs());
            System.out.println();
        }

        // Verify correctness
        verifyCorrectness();

        System.out.println("\n=== All benchmarks completed ===");
    }

    // ─── Compute Benchmarks ───────────────────────────────────────────────────

    private static BenchmarkResult benchmarkComputeK1() {
        List<Double> rewards = BenchmarkTestFixtures.createRewards(1);
        return RlBenchmarkRunner.run("GroupAdvantage.compute_K1", () ->
                GroupAdvantage.compute(rewards), WARMUP, ITERATIONS);
    }

    private static BenchmarkResult benchmarkComputeK2() {
        List<Double> rewards = BenchmarkTestFixtures.createRewards(2);
        return RlBenchmarkRunner.run("GroupAdvantage.compute_K2", () ->
                GroupAdvantage.compute(rewards), WARMUP, ITERATIONS);
    }

    private static BenchmarkResult benchmarkComputeK4() {
        List<Double> rewards = BenchmarkTestFixtures.createRewards(4);
        return RlBenchmarkRunner.run("GroupAdvantage.compute_K4", () ->
                GroupAdvantage.compute(rewards), WARMUP, ITERATIONS);
    }

    private static BenchmarkResult benchmarkComputeK8() {
        List<Double> rewards = BenchmarkTestFixtures.createRewards(8);
        return RlBenchmarkRunner.run("GroupAdvantage.compute_K8", () ->
                GroupAdvantage.compute(rewards), WARMUP, ITERATIONS);
    }

    private static BenchmarkResult benchmarkComputeK16() {
        List<Double> rewards = BenchmarkTestFixtures.createRewards(16);
        return RlBenchmarkRunner.run("GroupAdvantage.compute_K16", () ->
                GroupAdvantage.compute(rewards), WARMUP, ITERATIONS);
    }

    // ─── Best Index Benchmarks ────────────────────────────────────────────────

    private static BenchmarkResult benchmarkBestIndexK4() {
        List<Double> rewards = BenchmarkTestFixtures.createRewards(4);
        GroupAdvantage ga = GroupAdvantage.compute(rewards);
        return RlBenchmarkRunner.run("GroupAdvantage.bestIndex_K4", ga::bestIndex,
                WARMUP, ITERATIONS);
    }

    private static BenchmarkResult benchmarkBestIndexK16() {
        List<Double> rewards = BenchmarkTestFixtures.createRewards(16);
        GroupAdvantage ga = GroupAdvantage.compute(rewards);
        return RlBenchmarkRunner.run("GroupAdvantage.bestIndex_K16", ga::bestIndex,
                WARMUP, ITERATIONS);
    }

    // ─── Correctness Verification ─────────────────────────────────────────────

    private static void verifyCorrectness() {
        System.out.println("=== Correctness Verification ===\n");

        // Test 1: Advantages sum to zero (normalized)
        for (int k : new int[]{1, 2, 4, 8, 16}) {
            List<Double> rewards = BenchmarkTestFixtures.createRewards(k);
            GroupAdvantage ga = GroupAdvantage.compute(rewards);

            double sum = ga.advantages().stream().mapToDouble(Double::doubleValue).sum();
            double tolerance = 1e-10;

            if (Math.abs(sum) < tolerance) {
                System.out.printf("✓ K=%d: Advantages sum to ~0 (sum=%.15f)%n", k, sum);
            } else {
                System.out.printf("✗ K=%d: Advantages DO NOT sum to 0 (sum=%.15f)%n", k, sum);
            }
        }

        // Test 2: Best index selects highest reward
        for (int k : new int[]{4, 8, 16}) {
            List<Double> rewards = BenchmarkTestFixtures.createRewardsWithWinner(k, k - 1);
            GroupAdvantage ga = GroupAdvantage.compute(rewards);
            int bestIdx = ga.bestIndex();

            if (bestIdx == k - 1) {
                System.out.printf("✓ K=%d: bestIndex() correctly identifies winner at index %d%n",
                        k, bestIdx);
            } else {
                System.out.printf("✗ K=%d: bestIndex()=%d but expected %d%n", k, bestIdx, k - 1);
            }
        }

        // Test 3: Uniform rewards produce equal advantages
        List<Double> uniform = BenchmarkTestFixtures.createUniformRewards(4, 0.5);
        GroupAdvantage gaUniform = GroupAdvantage.compute(uniform);
        boolean allZero = gaUniform.advantages().stream()
                .allMatch(a -> Math.abs(a) < 1e-10);
        if (allZero) {
            System.out.println("✓ Uniform rewards produce zero advantages (std=0 case)");
        } else {
            System.out.println("✗ Uniform rewards should produce zero advantages");
        }

        System.out.println();
    }
}
