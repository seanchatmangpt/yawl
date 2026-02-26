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
import org.yawlfoundation.yawl.ggen.rl.GroupAdvantage;
import org.yawlfoundation.yawl.ggen.rl.RlConfig;
import org.yawlfoundation.yawl.ggen.rl.scoring.FootprintExtractor;
import org.yawlfoundation.yawl.ggen.rl.scoring.FootprintMatrix;
import org.yawlfoundation.yawl.ggen.rl.scoring.FootprintScorer;
import org.yawlfoundation.yawl.ggen.rl.scoring.RewardFunction;
import org.yawlfoundation.yawl.ggen.rl.benchmark.RlBenchmarkRunner.BenchmarkResult;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parameter sweep benchmarks for PhD thesis data collection.
 *
 * <p>Measures effects of:
 * <ul>
 *   <li>K value (1, 2, 4, 8, 16) on algorithm latency</li>
 *   <li>Process complexity (simple, medium, complex, very complex) on latency</li>
 *   <li>K value on selection quality (best index variance)</li>
 *   <li>Temperature distribution effects on advantage spread</li>
 * </ul>
 *
 * <h2>Running Benchmarks</h2>
 * <pre>{@code
 * mvn test -Dtest=ParameterSweepBenchmark -Dmaven.test.skip=false
 * }</pre>
 */
public final class ParameterSweepBenchmark {

    private static final int WARMUP = 100;
    private static final int ITERATIONS = 1000;
    private static final int K_VALUES[] = {1, 2, 4, 8, 16};

    private ParameterSweepBenchmark() {
        throw new UnsupportedOperationException("Run via main()");
    }

    public static void main(String[] args) {
        System.out.println("=== Parameter Sweep Benchmarks ===\n");
        System.out.println("Warmup: " + WARMUP + " iterations");
        System.out.println("Measured: " + ITERATIONS + " iterations\n");

        // Run sweeps
        Map<String, Object> kSweepResults = sweepKValues();
        Map<String, Object> complexitySweepResults = sweepComplexity();
        Map<String, Object> advantageSpreadResults = sweepAdvantageSpread();

        // Print results
        printKSweepResults(kSweepResults);
        printComplexityResults(complexitySweepResults);
        printAdvantageSpreadResults(advantageSpreadResults);

        // Generate summary for thesis
        generateThesisSummary(kSweepResults, complexitySweepResults, advantageSpreadResults);

        System.out.println("\n=== All parameter sweeps completed ===");
    }

    // ─── K Value Sweep ────────────────────────────────────────────────────────

    private static Map<String, Object> sweepKValues() {
        System.out.println("Running K value sweep (K = 1, 2, 4, 8, 16)...\n");

        Map<String, Object> results = new HashMap<>();
        List<BenchmarkResult> computeResults = new ArrayList<>();
        List<BenchmarkResult> bestIndexResults = new ArrayList<>();
        List<BenchmarkResult> optimizeResults = new ArrayList<>();

        for (int k : K_VALUES) {
            // GroupAdvantage.compute sweep
            List<Double> rewards = BenchmarkTestFixtures.createRewards(k);
            BenchmarkResult computeResult = RlBenchmarkRunner.run(
                    "GroupAdvantage.compute_K" + k,
                    () -> GroupAdvantage.compute(rewards),
                    WARMUP, ITERATIONS);
            computeResults.add(computeResult);

            // bestIndex sweep
            GroupAdvantage ga = GroupAdvantage.compute(rewards);
            BenchmarkResult bestIndexResult = RlBenchmarkRunner.run(
                    "GroupAdvantage.bestIndex_K" + k,
                    ga::bestIndex,
                    WARMUP, ITERATIONS);
            bestIndexResults.add(bestIndexResult);

            // Full optimize sweep
            BenchmarkResult optimizeResult = runOptimizeBenchmark(k);
            optimizeResults.add(optimizeResult);

            System.out.printf("  K=%2d: compute=%.2f ns, bestIndex=%.2f ns, optimize=%.2f ns%n",
                    k, computeResult.meanLatencyNs(), bestIndexResult.meanLatencyNs(),
                    optimizeResult.meanLatencyNs());
        }

        results.put("compute", computeResults);
        results.put("bestIndex", bestIndexResults);
        results.put("optimize", optimizeResults);

        return results;
    }

    // ─── Complexity Sweep ─────────────────────────────────────────────────────

    private static Map<String, Object> sweepComplexity() {
        System.out.println("\nRunning complexity sweep...\n");

        Map<String, Object> results = new HashMap<>();
        FootprintExtractor extractor = new FootprintExtractor();

        // Simple model
        PowlModel simple = BenchmarkTestFixtures.createSimpleSequence();
        BenchmarkResult simpleExtract = RlBenchmarkRunner.run(
                "Footprint.extract_SIMPLE",
                () -> extractor.extract(simple),
                WARMUP, ITERATIONS);

        // Medium model
        PowlModel medium = BenchmarkTestFixtures.createMediumModel();
        BenchmarkResult mediumExtract = RlBenchmarkRunner.run(
                "Footprint.extract_MEDIUM",
                () -> extractor.extract(medium),
                WARMUP, ITERATIONS);

        // Complex model
        PowlModel complex = BenchmarkTestFixtures.createComplexModel();
        BenchmarkResult complexExtract = RlBenchmarkRunner.run(
                "Footprint.extract_COMPLEX",
                () -> extractor.extract(complex),
                WARMUP, ITERATIONS);

        // Very complex model
        PowlModel veryComplex = BenchmarkTestFixtures.createVeryComplexModel();
        BenchmarkResult veryComplexExtract = RlBenchmarkRunner.run(
                "Footprint.extract_VERY_COMPLEX",
                () -> extractor.extract(veryComplex),
                WARMUP, ITERATIONS);

        results.put("simple", Map.of(
                "model", simple,
                "result", simpleExtract,
                "activities", countActivities(simple),
                "footprint", extractor.extract(simple)));

        results.put("medium", Map.of(
                "model", medium,
                "result", mediumExtract,
                "activities", countActivities(medium),
                "footprint", extractor.extract(medium)));

        results.put("complex", Map.of(
                "model", complex,
                "result", complexExtract,
                "activities", countActivities(complex),
                "footprint", extractor.extract(complex)));

        results.put("veryComplex", Map.of(
                "model", veryComplex,
                "result", veryComplexExtract,
                "activities", countActivities(veryComplex),
                "footprint", extractor.extract(veryComplex)));

        System.out.printf("  SIMPLE:      %d activities, %.2f ns%n",
                countActivities(simple), simpleExtract.meanLatencyNs());
        System.out.printf("  MEDIUM:      %d activities, %.2f ns%n",
                countActivities(medium), mediumExtract.meanLatencyNs());
        System.out.printf("  COMPLEX:     %d activities, %.2f ns%n",
                countActivities(complex), complexExtract.meanLatencyNs());
        System.out.printf("  VERY_COMPLEX: %d activities, %.2f ns%n",
                countActivities(veryComplex), veryComplexExtract.meanLatencyNs());

        return results;
    }

    // ─── Advantage Spread Sweep ───────────────────────────────────────────────

    private static Map<String, Object> sweepAdvantageSpread() {
        System.out.println("\nRunning advantage spread sweep...\n");

        Map<String, Object> results = new HashMap<>();

        // Uniform distribution (all rewards equal)
        for (int k : K_VALUES) {
            List<Double> uniformRewards = BenchmarkTestFixtures.createUniformRewards(k, 0.5);
            GroupAdvantage ga = GroupAdvantage.compute(uniformRewards);
            double spread = computeSpread(ga.advantages());
            results.put("uniform_K" + k, Map.of(
                    "k", k,
                    "spread", spread,
                    "std", ga.std(),
                    "advantages", ga.advantages()));
        }

        // Narrow distribution (small variance)
        for (int k : K_VALUES) {
            List<Double> narrowRewards = createNarrowDistribution(k);
            GroupAdvantage ga = GroupAdvantage.compute(narrowRewards);
            double spread = computeSpread(ga.advantages());
            results.put("narrow_K" + k, Map.of(
                    "k", k,
                    "spread", spread,
                    "std", ga.std(),
                    "advantages", ga.advantages()));
        }

        // Wide distribution (large variance)
        for (int k : K_VALUES) {
            List<Double> wideRewards = createWideDistribution(k);
            GroupAdvantage ga = GroupAdvantage.compute(wideRewards);
            double spread = computeSpread(ga.advantages());
            results.put("wide_K" + k, Map.of(
                    "k", k,
                    "spread", spread,
                    "std", ga.std(),
                    "advantages", ga.advantages()));
        }

        System.out.println("  Distribution effects on advantage spread:");
        System.out.println("    K  | Uniform | Narrow | Wide");
        System.out.println("    ----|---------|--------|------");
        for (int k : K_VALUES) {
            double uniformSpread = (double) ((Map<?, ?>) results.get("uniform_K" + k)).get("spread");
            double narrowSpread = (double) ((Map<?, ?>) results.get("narrow_K" + k)).get("spread");
            double wideSpread = (double) ((Map<?, ?>) results.get("wide_K" + k)).get("spread");
            System.out.printf("    %2d  | %.3f   | %.3f  | %.3f%n",
                    k, uniformSpread, narrowSpread, wideSpread);
        }

        return results;
    }

    // ─── Helper Methods ───────────────────────────────────────────────────────

    private static BenchmarkResult runOptimizeBenchmark(int k) {
        List<PowlModel> models = List.of(
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

        InstantSampler sampler = new InstantSampler(models, true);
        RlConfig config = new RlConfig(k, CurriculumStage.VALIDITY_GAP, 3,
                "http://localhost", "test", 60);
        RewardFunction rewardFn = (model, desc) -> 0.75;
        GrpoOptimizer optimizer = new GrpoOptimizer(sampler, rewardFn, config);

        return RlBenchmarkRunner.run("GrpoOptimizer.optimize_K" + k, () -> {
            try {
                return optimizer.optimize("test");
            } catch (IOException | org.yawlfoundation.yawl.ggen.rl.PowlParseException e) {
                throw new RuntimeException(e);
            }
        }, WARMUP / 2, ITERATIONS / 2);
    }

    private static int countActivities(PowlModel model) {
        return countActivitiesRecursive(model.root());
    }

    private static int countActivitiesRecursive(Object node) {
        if (node instanceof org.yawlfoundation.yawl.ggen.powl.PowlActivity) {
            return 1;
        } else if (node instanceof org.yawlfoundation.yawl.ggen.powl.PowlOperatorNode op) {
            int count = 0;
            for (var child : op.children()) {
                count += countActivitiesRecursive(child);
            }
            return count;
        }
        return 0;
    }

    private static double computeSpread(List<Double> advantages) {
        if (advantages.isEmpty()) return 0.0;
        double max = advantages.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double min = advantages.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        return max - min;
    }

    private static List<Double> createNarrowDistribution(int k) {
        List<Double> rewards = new ArrayList<>(k);
        for (int i = 0; i < k; i++) {
            rewards.add(0.48 + (i * 0.01)); // 0.48, 0.49, 0.50, ...
        }
        return rewards;
    }

    private static List<Double> createWideDistribution(int k) {
        List<Double> rewards = new ArrayList<>(k);
        for (int i = 0; i < k; i++) {
            rewards.add(0.1 + (i * 0.8 / k)); // 0.1 to 0.9 spread
        }
        return rewards;
    }

    // ─── Print Methods ────────────────────────────────────────────────────────

    private static void printKSweepResults(Map<String, Object> results) {
        System.out.println("\n=== K Value Sweep Results ===\n");
        System.out.println("K  | Compute (ns) | BestIndex (ns) | Optimize (ns)");
        System.out.println("---|--------------|----------------|---------------");

        @SuppressWarnings("unchecked")
        List<BenchmarkResult> computeResults = (List<BenchmarkResult>) results.get("compute");
        @SuppressWarnings("unchecked")
        List<BenchmarkResult> bestIndexResults = (List<BenchmarkResult>) results.get("bestIndex");
        @SuppressWarnings("unchecked")
        List<BenchmarkResult> optimizeResults = (List<BenchmarkResult>) results.get("optimize");

        for (int i = 0; i < K_VALUES.length; i++) {
            System.out.printf("%2d | %12.2f | %14.2f | %13.2f%n",
                    K_VALUES[i],
                    computeResults.get(i).meanLatencyNs(),
                    bestIndexResults.get(i).meanLatencyNs(),
                    optimizeResults.get(i).meanLatencyNs());
        }
    }

    private static void printComplexityResults(Map<String, Object> results) {
        System.out.println("\n=== Complexity Sweep Results ===\n");
        System.out.println("Complexity     | Activities | Extract (ns) | Extract (ms)");
        System.out.println("---------------|------------|--------------|--------------");

        String[] levels = {"simple", "medium", "complex", "veryComplex"};
        String[] names = {"SIMPLE", "MEDIUM", "COMPLEX", "VERY_COMPLEX"};

        for (int i = 0; i < levels.length; i++) {
            @SuppressWarnings("unchecked")
            Map<String, Object> levelData = (Map<String, Object>) results.get(levels[i]);
            BenchmarkResult r = (BenchmarkResult) levelData.get("result");
            int activities = (int) levelData.get("activities");

            System.out.printf("%-14s | %10d | %12.2f | %12.4f%n",
                    names[i], activities, r.meanLatencyNs(), r.meanLatencyMs());
        }
    }

    private static void printAdvantageSpreadResults(Map<String, Object> results) {
        System.out.println("\n=== Advantage Spread Results ===\n");
        System.out.println("Distribution | K  | Spread | Std");
        System.out.println("-------------|----|--------|-------");

        for (String dist : new String[]{"uniform", "narrow", "wide"}) {
            for (int k : K_VALUES) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) results.get(dist + "_K" + k);
                double spread = (double) data.get("spread");
                double std = (double) data.get("std");
                System.out.printf("%-12s | %2d | %.3f  | %.3f%n",
                        dist.toUpperCase(), k, spread, std);
            }
        }
    }

    private static void generateThesisSummary(
            Map<String, Object> kSweep,
            Map<String, Object> complexitySweep,
            Map<String, Object> advantageSpread) {

        System.out.println("\n=== Thesis Summary (Copy-Paste Ready) ===\n");

        System.out.println("```");
        System.out.println("GRPO Algorithm Latency by K Value:");
        System.out.println("----------------------------------");
        @SuppressWarnings("unchecked")
        List<BenchmarkResult> computeResults = (List<BenchmarkResult>) kSweep.get("compute");
        for (int i = 0; i < K_VALUES.length; i++) {
            BenchmarkResult r = computeResults.get(i);
            System.out.printf("K=%2d: mean=%.2f ns, std=%.2f ns, p95=%d ns%n",
                    K_VALUES[i], r.meanLatencyNs(), r.stdLatencyNs(), r.p95LatencyNs());
        }

        System.out.println();
        System.out.println("Footprint Extraction by Complexity:");
        System.out.println("-----------------------------------");
        String[] levels = {"simple", "medium", "complex", "veryComplex"};
        for (String level : levels) {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) complexitySweep.get(level);
            BenchmarkResult r = (BenchmarkResult) data.get("result");
            int activities = (int) data.get("activities");
            System.out.printf("%s: %d activities, %.2f ns%n",
                    level.toUpperCase(), activities, r.meanLatencyNs());
        }
        System.out.println("```");
    }

    // ─── Mock Components ───────────────────────────────────────────────────────

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
}
