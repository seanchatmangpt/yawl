/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.rl.benchmark;

import org.yawlfoundation.yawl.ggen.rl.benchmark.RlBenchmarkRunner.BenchmarkResult;
import org.yawlfoundation.yawl.ggen.rl.benchmark.RlBenchmarkRunner.BenchmarkSuiteResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Main entry point to run all RL benchmarks and generate a report.
 *
 * <p>Usage:
 * <pre>{@code
 * mvn test-compile exec:java -Dexec.mainClass=org.yawlfoundation.yawl.ggen.rl.benchmark.AllBenchmarks
 * }</pre>
 *
 * <p>Or run directly:
 * <pre>{@code
 * java -cp target/test-classes:target/classes org.yawlfoundation.yawl.ggen.rl.benchmark.AllBenchmarks
 * }</pre>
 *
 * <h2>Output</h2>
 * Generates JSON report at {@code docs/RL_BENCHMARK_RESULTS.json}
 */
public final class AllBenchmarks {

    private static final Path REPORT_PATH = Paths.get("docs/RL_BENCHMARK_RESULTS.json");
    private static final int WARMUP = 500;
    private static final int ITERATIONS = 5000;

    private AllBenchmarks() {
        throw new UnsupportedOperationException("Run via main()");
    }

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║          YAWL RL Benchmark Suite - PhD Thesis Data          ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Timestamp: " + Instant.now());
        System.out.println("Java: " + System.getProperty("java.version"));
        System.out.println("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
        System.out.println("Warmup iterations: " + WARMUP);
        System.out.println("Measured iterations: " + ITERATIONS);
        System.out.println();

        List<BenchmarkResult> allResults = new ArrayList<>();

        // Run all benchmark categories
        allResults.addAll(runGroupAdvantageBenchmarks());
        allResults.addAll(runFootprintBenchmarks());
        allResults.addAll(runGrpoOptimizerBenchmarks());
        allResults.addAll(runMemoryBenchmarks());
        allResults.addAll(runParameterSweepBenchmarks());

        // Generate report
        BenchmarkSuiteReport report = new BenchmarkSuiteReport(
                Instant.now(),
                System.getProperty("java.version", "unknown"),
                System.getProperty("os.name", "unknown"),
                System.getProperty("os.version", "unknown"),
                WARMUP,
                ITERATIONS,
                allResults
        );

        // Write JSON report
        writeReport(report);

        // Print summary
        printSummary(report);

        System.out.println("\n✓ All benchmarks completed!");
        System.out.println("✓ Report written to: " + REPORT_PATH.toAbsolutePath());
    }

    // ─── Benchmark Runners ────────────────────────────────────────────────────

    private static List<BenchmarkResult> runGroupAdvantageBenchmarks() {
        System.out.println("\n━━━ GroupAdvantage Micro-Benchmarks ━━━\n");

        List<BenchmarkResult> results = new ArrayList<>();
        int[] kValues = {1, 2, 4, 8, 16};

        for (int k : kValues) {
            List<Double> rewards = BenchmarkTestFixtures.createRewards(k);

            BenchmarkResult result = RlBenchmarkRunner.run(
                    "GroupAdvantage.compute_K" + k,
                    () -> org.yawlfoundation.yawl.ggen.rl.GroupAdvantage.compute(rewards),
                    WARMUP, ITERATIONS);
            results.add(result);

            System.out.printf("  K=%2d: mean=%.2f ns (std=%.2f)%n",
                    k, result.meanLatencyNs(), result.stdLatencyNs());
        }

        return results;
    }

    private static List<BenchmarkResult> runFootprintBenchmarks() {
        System.out.println("\n━━━ Footprint Extraction Benchmarks ━━━\n");

        List<BenchmarkResult> results = new ArrayList<>();
        org.yawlfoundation.yawl.ggen.rl.scoring.FootprintExtractor extractor =
                new org.yawlfoundation.yawl.ggen.rl.scoring.FootprintExtractor();

        var models = List.of(
                new ModelFixture("SIMPLE", BenchmarkTestFixtures.createSimpleSequence()),
                new ModelFixture("MEDIUM", BenchmarkTestFixtures.createMediumModel()),
                new ModelFixture("COMPLEX", BenchmarkTestFixtures.createComplexModel()),
                new ModelFixture("VERY_COMPLEX", BenchmarkTestFixtures.createVeryComplexModel())
        );

        for (ModelFixture fixture : models) {
            BenchmarkResult result = RlBenchmarkRunner.run(
                    "Footprint.extract_" + fixture.name(),
                    () -> extractor.extract(fixture.model()),
                    WARMUP, ITERATIONS);
            results.add(result);

            System.out.printf("  %-12s: mean=%.2f ns (%.4f ms)%n",
                    fixture.name(), result.meanLatencyNs(), result.meanLatencyMs());
        }

        return results;
    }

    private static List<BenchmarkResult> runGrpoOptimizerBenchmarks() {
        System.out.println("\n━━━ GrpoOptimizer End-to-End Benchmarks ━━━\n");
        System.out.println("  (Using InstantSampler - no I/O)");

        List<BenchmarkResult> results = new ArrayList<>();
        int[] kValues = {1, 2, 4, 8, 16};

        // Create instant sampler
        var models = List.of(
                BenchmarkTestFixtures.createSimpleSequence(),
                BenchmarkTestFixtures.createSimpleXor(),
                BenchmarkTestFixtures.createSimpleParallel(),
                BenchmarkTestFixtures.createSimpleLoop(),
                BenchmarkTestFixtures.createMediumModel(),
                BenchmarkTestFixtures.createComplexModel(),
                BenchmarkTestFixtures.createVeryComplexModel()
        );

        for (int k : kValues) {
            var sampler = new InstantSampler(models);
            var config = new org.yawlfoundation.yawl.ggen.rl.RlConfig(
                    k,
                    org.yawlfoundation.yawl.ggen.rl.CurriculumStage.VALIDITY_GAP,
                    3, "http://localhost", "test", 60);
            var rewardFn = (org.yawlfoundation.yawl.ggen.rl.scoring.RewardFunction)
                    (model, desc) -> 0.75;
            var optimizer = new org.yawlfoundation.yawl.ggen.rl.GrpoOptimizer(
                    sampler, rewardFn, config);

            BenchmarkResult result = RlBenchmarkRunner.run(
                    "GrpoOptimizer.optimize_K" + k,
                    () -> {
                        try {
                            return optimizer.optimize("test");
                        } catch (java.io.IOException e) {
                            throw new RuntimeException(e);
                        }
                    },
                    WARMUP / 5, ITERATIONS / 5);
            results.add(result);

            System.out.printf("  K=%2d: mean=%.2f ns (%.4f ms)%n",
                    k, result.meanLatencyNs(), result.meanLatencyMs());
        }

        return results;
    }

    private static List<BenchmarkResult> runMemoryBenchmarks() {
        System.out.println("\n━━━ ProcessKnowledgeGraph Memory Benchmarks ━━━\n");

        List<BenchmarkResult> results = new ArrayList<>();

        // Remember benchmark
        var candidateSet = BenchmarkTestFixtures.createCandidateSet(4);
        BenchmarkResult rememberResult = RlBenchmarkRunner.run(
                "ProcessKnowledgeGraph.remember_SINGLE",
                () -> {
                    var graph = new org.yawlfoundation.yawl.ggen.memory.ProcessKnowledgeGraph();
                    graph.remember(candidateSet);
                    return null;
                },
                WARMUP, ITERATIONS);
        results.add(rememberResult);
        System.out.printf("  remember(): mean=%.2f ns%n", rememberResult.meanLatencyNs());

        // BiasHint benchmark
        var populatedGraph = new org.yawlfoundation.yawl.ggen.memory.ProcessKnowledgeGraph();
        for (int i = 0; i < 50; i++) {
            populatedGraph.remember(BenchmarkTestFixtures.createCandidateSet(4));
        }
        BenchmarkResult biasHintResult = RlBenchmarkRunner.run(
                "ProcessKnowledgeGraph.biasHint_K10",
                () -> populatedGraph.biasHint("test", 10),
                WARMUP, ITERATIONS);
        results.add(biasHintResult);
        System.out.printf("  biasHint(K=10): mean=%.2f ns%n", biasHintResult.meanLatencyNs());

        // Fingerprint benchmark
        var complexModel = BenchmarkTestFixtures.createComplexModel();
        BenchmarkResult fingerprintResult = RlBenchmarkRunner.run(
                "ProcessKnowledgeGraph.fingerprint_COMPLEX",
                () -> org.yawlfoundation.yawl.ggen.memory.ProcessKnowledgeGraph.fingerprint(complexModel),
                WARMUP, ITERATIONS);
        results.add(fingerprintResult);
        System.out.printf("  fingerprint(): mean=%.2f ns%n", fingerprintResult.meanLatencyNs());

        return results;
    }

    private static List<BenchmarkResult> runParameterSweepBenchmarks() {
        System.out.println("\n━━━ Parameter Sweep Benchmarks ━━━\n");

        List<BenchmarkResult> results = new ArrayList<>();
        int[] kValues = {1, 2, 4, 8, 16};

        // K value effect on GroupAdvantage
        System.out.println("  K effect on compute latency:");
        for (int k : kValues) {
            List<Double> rewards = BenchmarkTestFixtures.createRewards(k);
            BenchmarkResult result = RlBenchmarkRunner.run(
                    "Sweep_GroupAdvantage_K" + k,
                    () -> org.yawlfoundation.yawl.ggen.rl.GroupAdvantage.compute(rewards),
                    WARMUP, ITERATIONS);
            results.add(result);
            System.out.printf("    K=%2d: %.2f ns%n", k, result.meanLatencyNs());
        }

        // Complexity effect on footprint extraction
        System.out.println("\n  Complexity effect on extraction:");
        var extractor = new org.yawlfoundation.yawl.ggen.rl.scoring.FootprintExtractor();
        var fixtures = List.of(
                new ModelFixture("3act", BenchmarkTestFixtures.createSimpleSequence()),
                new ModelFixture("5act", BenchmarkTestFixtures.createMediumModel()),
                new ModelFixture("10act", BenchmarkTestFixtures.createComplexModel()),
                new ModelFixture("25act", BenchmarkTestFixtures.createVeryComplexModel())
        );
        for (var fixture : fixtures) {
            BenchmarkResult result = RlBenchmarkRunner.run(
                    "Sweep_Footprint_" + fixture.name(),
                    () -> extractor.extract(fixture.model()),
                    WARMUP, ITERATIONS);
            results.add(result);
            System.out.printf("    %-6s: %.2f ns%n", fixture.name(), result.meanLatencyNs());
        }

        return results;
    }

    // ─── Report Generation ────────────────────────────────────────────────────

    private static void writeReport(BenchmarkSuiteReport report) {
        try {
            Files.createDirectories(REPORT_PATH.getParent());
            Files.writeString(REPORT_PATH, report.toJson());
        } catch (Exception e) {
            System.err.println("Failed to write report: " + e.getMessage());
        }
    }

    private static void printSummary(BenchmarkSuiteReport report) {
        System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                    BENCHMARK SUMMARY                        ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");

        System.out.println("Total benchmarks run: " + report.results().size());
        System.out.println();

        // Group by category
        System.out.println("Category Summary:");
        System.out.println("----------------");

        report.results().stream()
                .filter(r -> r.name().contains("GroupAdvantage"))
                .findFirst()
                .ifPresent(r -> System.out.printf("  GroupAdvantage: ~%.0f ns per compute%n",
                        r.meanLatencyNs()));

        report.results().stream()
                .filter(r -> r.name().contains("Footprint") && r.name().contains("SIMPLE"))
                .findFirst()
                .ifPresent(r -> System.out.printf("  Footprint (simple): ~%.0f ns%n",
                        r.meanLatencyNs()));

        report.results().stream()
                .filter(r -> r.name().contains("GrpoOptimizer") && r.name().contains("K4"))
                .findFirst()
                .ifPresent(r -> System.out.printf("  GrpoOptimizer (K=4): ~%.0f ns%n",
                        r.meanLatencyNs()));

        report.results().stream()
                .filter(r -> r.name().contains("remember"))
                .findFirst()
                .ifPresent(r -> System.out.printf("  Memory (remember): ~%.0f ns%n",
                        r.meanLatencyNs()));
    }

    // ─── Helper Records ────────────────────────────────────────────────────────

    record ModelFixture(String name, org.yawlfoundation.yawl.ggen.powl.PowlModel model) {}

    record BenchmarkSuiteReport(
            Instant timestamp,
            String javaVersion,
            String osName,
            String osVersion,
            int warmupIterations,
            int measuredIterations,
            List<BenchmarkResult> results
    ) {
        public String toJson() {
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"benchmark_run\": {\n");
            sb.append("    \"timestamp\": \"").append(timestamp).append("\",\n");
            sb.append("    \"java_version\": \"").append(javaVersion).append("\",\n");
            sb.append("    \"os\": \"").append(osName).append(" ").append(osVersion).append("\",\n");
            sb.append("    \"warmup_iterations\": ").append(warmupIterations).append(",\n");
            sb.append("    \"measured_iterations\": ").append(measuredIterations).append("\n");
            sb.append("  },\n");
            sb.append("  \"results\": [\n");

            for (int i = 0; i < results.size(); i++) {
                BenchmarkResult r = results.get(i);
                sb.append("    {\n");
                sb.append("      \"name\": \"").append(r.name()).append("\",\n");
                sb.append("      \"latency_ns\": {\n");
                sb.append("        \"min\": ").append(r.minLatencyNs()).append(",\n");
                sb.append("        \"max\": ").append(r.maxLatencyNs()).append(",\n");
                sb.append("        \"mean\": ").append(String.format("%.2f", r.meanLatencyNs())).append(",\n");
                sb.append("        \"std\": ").append(String.format("%.2f", r.stdLatencyNs())).append(",\n");
                sb.append("        \"p50\": ").append(r.p50LatencyNs()).append(",\n");
                sb.append("        \"p95\": ").append(r.p95LatencyNs()).append(",\n");
                sb.append("        \"p99\": ").append(r.p99LatencyNs()).append("\n");
                sb.append("      }\n");
                sb.append("    }").append(i < results.size() - 1 ? "," : "").append("\n");
            }

            sb.append("  ]\n");
            sb.append("}\n");
            return sb.toString();
        }
    }

    // ─── Mock Sampler ──────────────────────────────────────────────────────────

    static class InstantSampler implements org.yawlfoundation.yawl.ggen.rl.CandidateSampler {
        private final List<org.yawlfoundation.yawl.ggen.powl.PowlModel> models;
        private int index = 0;

        InstantSampler(List<org.yawlfoundation.yawl.ggen.powl.PowlModel> models) {
            this.models = List.copyOf(models);
        }

        @Override
        public List<org.yawlfoundation.yawl.ggen.powl.PowlModel> sample(
                String processDescription, int k) throws java.io.IOException {
            List<org.yawlfoundation.yawl.ggen.powl.PowlModel> result = new ArrayList<>();
            for (int i = 0; i < k; i++) {
                result.add(models.get(index % models.size()));
                index = (index + 1) % models.size();
            }
            return result;
        }
    }
}
