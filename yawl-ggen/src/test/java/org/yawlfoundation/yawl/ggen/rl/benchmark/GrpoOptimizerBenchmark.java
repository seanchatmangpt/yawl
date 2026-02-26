/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.rl.benchmark;

import org.yawlfoundation.yawl.ggen.powl.PowlModel;
import org.yawlfoundation.yawl.ggen.rl.CandidateSampler;
import org.yawlfoundation.yawl.ggen.rl.CandidateSet;
import org.yawlfoundation.yawl.ggen.rl.CurriculumStage;
import org.yawlfoundation.yawl.ggen.rl.GrpoOptimizer;
import org.yawlfoundation.yawl.ggen.rl.RlConfig;
import org.yawlfoundation.yawl.ggen.rl.scoring.FootprintExtractor;
import org.yawlfoundation.yawl.ggen.rl.scoring.FootprintMatrix;
import org.yawlfoundation.yawl.ggen.rl.scoring.FootprintScorer;
import org.yawlfoundation.yawl.ggen.rl.scoring.RewardFunction;
import org.yawlfoundation.yawl.ggen.rl.benchmark.RlBenchmarkRunner.BenchmarkResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * End-to-end benchmarks for GrpoOptimizer pipeline.
 *
 * <p>Uses mock components to isolate and measure pure GRPO algorithm overhead:
 * <ul>
 *   <li>{@link InstantSampler} - returns pre-built models instantly</li>
 *   <li>{@link FixedRewardFunction} - returns fixed scores instantly</li>
 * </ul>
 *
 * <h2>Running Benchmarks</h2>
 * <pre>{@code
 * mvn test -Dtest=GrpoOptimizerBenchmark -Dmaven.test.skip=false
 * }</pre>
 */
public final class GrpoOptimizerBenchmark {

    private static final int WARMUP = 100;
    private static final int ITERATIONS = 1000;

    private GrpoOptimizerBenchmark() {
        throw new UnsupportedOperationException("Run via main()");
    }

    public static void main(String[] args) {
        System.out.println("=== GrpoOptimizer End-to-End Benchmarks ===\n");
        System.out.println("Warmup: " + WARMUP + " iterations");
        System.out.println("Measured: " + ITERATIONS + " iterations\n");
        System.out.println("NOTE: Uses InstantSampler (no I/O) to measure pure GRPO overhead\n");

        List<BenchmarkResult> results = new ArrayList<>();

        // Optimize benchmarks by K value
        results.add(benchmarkOptimizeK1());
        results.add(benchmarkOptimizeK2());
        results.add(benchmarkOptimizeK4());
        results.add(benchmarkOptimizeK8());
        results.add(benchmarkOptimizeK16());

        // Evaluate candidates benchmarks
        results.add(benchmarkEvaluateCandidatesK4());
        results.add(benchmarkEvaluateCandidatesK16());

        // Model complexity effect
        results.add(benchmarkOptimizeSimple());
        results.add(benchmarkOptimizeComplex());

        // Print results
        System.out.println("\n=== Results ===\n");
        for (BenchmarkResult r : results) {
            System.out.printf("%s:%n", r.name());
            System.out.printf("  Mean: %.2f ns (%.4f ms)%n", r.meanLatencyNs(), r.meanLatencyMs());
            System.out.printf("  Std:  %.2f ns%n", r.stdLatencyNs());
            System.out.printf("  P50:  %d ns | P95: %d ns | P99: %d ns%n",
                    r.p50LatencyNs(), r.p95LatencyNs(), r.p99LatencyNs());
            System.out.println();
        }

        // Verify correctness
        verifyCorrectness();

        System.out.println("\n=== All benchmarks completed ===");
    }

    // ─── Optimize Benchmarks by K Value ───────────────────────────────────────

    private static BenchmarkResult benchmarkOptimizeK1() {
        return runBenchmark("GrpoOptimizer.optimize_K1", 1);
    }

    private static BenchmarkResult benchmarkOptimizeK2() {
        return runBenchmark("GrpoOptimizer.optimize_K2", 2);
    }

    private static BenchmarkResult benchmarkOptimizeK4() {
        return runBenchmark("GrpoOptimizer.optimize_K4", 4);
    }

    private static BenchmarkResult benchmarkOptimizeK8() {
        return runBenchmark("GrpoOptimizer.optimize_K8", 8);
    }

    private static BenchmarkResult benchmarkOptimizeK16() {
        return runBenchmark("GrpoOptimizer.optimize_K16", 16);
    }

    // ─── Evaluate Candidates Benchmarks ───────────────────────────────────────

    private static BenchmarkResult benchmarkEvaluateCandidatesK4() {
        return runEvaluateBenchmark("GrpoOptimizer.evaluateCandidates_K4", 4);
    }

    private static BenchmarkResult benchmarkEvaluateCandidatesK16() {
        return runEvaluateBenchmark("GrpoOptimizer.evaluateCandidates_K16", 16);
    }

    // ─── Model Complexity Effect ──────────────────────────────────────────────

    private static BenchmarkResult benchmarkOptimizeSimple() {
        InstantSampler sampler = new InstantSampler(
                List.of(BenchmarkTestFixtures.createSimpleSequence()), true);
        RlConfig config = createTestConfig(4);
        RewardFunction rewardFn = new FixedRewardFunction(0.75);
        GrpoOptimizer optimizer = new GrpoOptimizer(sampler, rewardFn, config);

        return RlBenchmarkRunner.run("GrpoOptimizer.optimize_SIMPLE_MODEL",
                () -> {
                    try {
                        return optimizer.optimize("simple test");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }, WARMUP, ITERATIONS);
    }

    private static BenchmarkResult benchmarkOptimizeComplex() {
        InstantSampler sampler = new InstantSampler(
                List.of(BenchmarkTestFixtures.createComplexModel()), true);
        RlConfig config = createTestConfig(4);
        RewardFunction rewardFn = new FixedRewardFunction(0.75);
        GrpoOptimizer optimizer = new GrpoOptimizer(sampler, rewardFn, config);

        return RlBenchmarkRunner.run("GrpoOptimizer.optimize_COMPLEX_MODEL",
                () -> {
                    try {
                        return optimizer.optimize("complex test");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }, WARMUP, ITERATIONS);
    }

    // ─── Helper Methods ───────────────────────────────────────────────────────

    private static BenchmarkResult runBenchmark(String name, int k) {
        InstantSampler sampler = new InstantSampler(createDefaultModels(), true);
        RlConfig config = createTestConfig(k);
        RewardFunction rewardFn = new FixedRewardFunction(0.75);
        GrpoOptimizer optimizer = new GrpoOptimizer(sampler, rewardFn, config);

        return RlBenchmarkRunner.run(name, () -> {
            try {
                return optimizer.optimize("test process");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, WARMUP, ITERATIONS);
    }

    private static BenchmarkResult runEvaluateBenchmark(String name, int k) {
        InstantSampler sampler = new InstantSampler(createDefaultModels(), true);
        RlConfig config = createTestConfig(k);
        RewardFunction rewardFn = new FixedRewardFunction(0.75);
        GrpoOptimizer optimizer = new GrpoOptimizer(sampler, rewardFn, config);

        return RlBenchmarkRunner.run(name, () -> {
            try {
                return optimizer.evaluateCandidates("test process");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, WARMUP, ITERATIONS);
    }

    private static RlConfig createTestConfig(int k) {
        return new RlConfig(
                k,
                CurriculumStage.VALIDITY_GAP,
                3,
                "http://localhost:11434",
                "test-model",
                60
        );
    }

    private static List<PowlModel> createDefaultModels() {
        return List.of(
                BenchmarkTestFixtures.createSimpleSequence(),
                BenchmarkTestFixtures.createSimpleXor(),
                BenchmarkTestFixtures.createSimpleParallel(),
                BenchmarkTestFixtures.createSimpleLoop(),
                BenchmarkTestFixtures.createMediumModel(),
                BenchmarkTestFixtures.createComplexModel(),
                BenchmarkTestFixtures.createVeryComplexModel(),
                BenchmarkTestFixtures.createSimpleSequence(),
                BenchmarkTestFixtures.createMediumModel(),
                BenchmarkTestFixtures.createComplexModel(),
                BenchmarkTestFixtures.createSimpleXor(),
                BenchmarkTestFixtures.createSimpleParallel(),
                BenchmarkTestFixtures.createSimpleLoop(),
                BenchmarkTestFixtures.createMediumModel(),
                BenchmarkTestFixtures.createComplexModel(),
                BenchmarkTestFixtures.createVeryComplexModel()
        );
    }

    // ─── Correctness Verification ─────────────────────────────────────────────

    private static void verifyCorrectness() {
        System.out.println("=== Correctness Verification ===\n");

        try {
            // Test 1: Verify optimize() returns a model
            InstantSampler sampler = new InstantSampler(createDefaultModels(), true);
            RlConfig config = createTestConfig(4);
            RewardFunction rewardFn = new FixedRewardFunction(0.75);
            GrpoOptimizer optimizer = new GrpoOptimizer(sampler, rewardFn, config);

            PowlModel result = optimizer.optimize("test process");
            if (result != null) {
                System.out.println("✓ optimize() returns non-null model");
            } else {
                System.out.println("✗ optimize() returned null");
            }

            // Test 2: Verify evaluateCandidates() returns correct size
            CandidateSet candidates = optimizer.evaluateCandidates("test process");
            if (candidates.candidates().size() == 4) {
                System.out.printf("✓ evaluateCandidates() returns K=%d candidates%n",
                        candidates.candidates().size());
            } else {
                System.out.printf("✗ evaluateCandidates() returned %d, expected 4%n",
                        candidates.candidates().size());
            }

            // Test 3: Verify rewards are computed
            if (candidates.rewards().size() == 4) {
                System.out.printf("✓ rewards computed for all %d candidates%n",
                        candidates.rewards().size());
            } else {
                System.out.println("✗ rewards size mismatch");
            }

            // Test 4: Verify best() returns highest-reward candidate
            PowlModel best = candidates.best();
            if (best != null) {
                System.out.println("✓ best() returns a valid model");
            } else {
                System.out.println("✗ best() returned null");
            }

            // Test 5: Verify with varied rewards
            RewardFunction variedReward = new VariedRewardFunction();
            GrpoOptimizer variedOptimizer = new GrpoOptimizer(sampler, variedReward, config);
            CandidateSet varied = variedOptimizer.evaluateCandidates("test");
            int bestIdx = varied.bestIndex();
            double bestReward = varied.rewards().get(bestIdx);

            boolean isHighest = true;
            for (int i = 0; i < varied.rewards().size(); i++) {
                if (varied.rewards().get(i) > bestReward) {
                    isHighest = false;
                    break;
                }
            }
            if (isHighest) {
                System.out.printf("✓ bestIndex()=%d correctly identifies highest reward %.2f%n",
                        bestIdx, bestReward);
            } else {
                System.out.println("✗ bestIndex() did not identify highest reward");
            }

        } catch (IOException e) {
            System.out.println("✗ Exception during verification: " + e.getMessage());
        }

        System.out.println();
    }

    // ─── Mock Components ───────────────────────────────────────────────────────

    /**
     * Instant sampler that returns pre-built models without any I/O.
     */
    static class InstantSampler implements CandidateSampler {
        private final List<PowlModel> models;
        private final boolean cycle;
        private int index = 0;

        InstantSampler(List<PowlModel> models, boolean cycle) {
            this.models = List.copyOf(models);
            this.cycle = cycle;
        }

        @Override
        public List<PowlModel> sample(String processDescription, int k) {
            List<PowlModel> result = new ArrayList<>(k);
            for (int i = 0; i < k; i++) {
                result.add(models.get(index % models.size()));
                if (cycle) {
                    index = (index + 1) % models.size();
                }
            }
            return result;
        }
    }

    /**
     * Fixed reward function that returns a constant score.
     */
    static class FixedRewardFunction implements RewardFunction {
        private final double score;

        FixedRewardFunction(double score) {
            this.score = score;
        }

        @Override
        public double score(PowlModel candidate, String processDescription) {
            return score;
        }
    }

    /**
     * Varied reward function that returns different scores based on model complexity.
     */
    static class VariedRewardFunction implements RewardFunction {
        private final FootprintExtractor extractor = new FootprintExtractor();

        @Override
        public double score(PowlModel candidate, String processDescription) {
            // Score based on number of relationships (complexity)
            FootprintMatrix fp = extractor.extract(candidate);
            int total = fp.directSuccession().size() +
                    fp.concurrency().size() +
                    fp.exclusive().size();
            return Math.min(1.0, total / 20.0);
        }
    }
}
