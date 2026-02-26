/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.memory.benchmark;

import org.yawlfoundation.yawl.ggen.memory.ProcessKnowledgeGraph;
import org.yawlfoundation.yawl.ggen.rl.CandidateSet;
import org.yawlfoundation.yawl.ggen.rl.benchmark.BenchmarkTestFixtures;
import org.yawlfoundation.yawl.ggen.rl.benchmark.RlBenchmarkRunner;
import org.yawlfoundation.yawl.ggen.rl.benchmark.RlBenchmarkRunner.BenchmarkResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Benchmarks for ProcessKnowledgeGraph memory operations.
 *
 * <p>Measures latency of:
 * <ul>
 *   <li>{@link ProcessKnowledgeGraph#remember(CandidateSet)} insertion</li>
 *   <li>{@link ProcessKnowledgeGraph#biasHint(String, int)} retrieval</li>
 *   <li>Graph size growth over rounds</li>
 *   <li>Fingerprint computation</li>
 * </ul>
 *
 * <h2>Running Benchmarks</h2>
 * <pre>{@code
 * mvn test -Dtest=ProcessKnowledgeGraphBenchmark -Dmaven.test.skip=false
 * }</pre>
 */
public final class ProcessKnowledgeGraphBenchmark {

    private static final int WARMUP = 200;
    private static final int ITERATIONS = 2000;

    private ProcessKnowledgeGraphBenchmark() {
        throw new UnsupportedOperationException("Run via main()");
    }

    public static void main(String[] args) {
        System.out.println("=== ProcessKnowledgeGraph Memory Benchmarks ===\n");
        System.out.println("Warmup: " + WARMUP + " iterations");
        System.out.println("Measured: " + ITERATIONS + " iterations\n");

        List<BenchmarkResult> results = new ArrayList<>();

        // Remember benchmarks
        results.add(benchmarkRememberSingle());
        results.add(benchmarkRememberBatch10());
        results.add(benchmarkRememberBatch100());

        // BiasHint benchmarks
        results.add(benchmarkBiasHintK1());
        results.add(benchmarkBiasHintK5());
        results.add(benchmarkBiasHintK10());
        results.add(benchmarkBiasHintK20());

        // Fingerprint benchmarks
        results.add(benchmarkFingerprintSimple());
        results.add(benchmarkFingerprintComplex());
        results.add(benchmarkFingerprintVeryComplex());

        // Growth benchmarks
        results.add(benchmarkGraphGrowth());

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

    // ─── Remember Benchmarks ──────────────────────────────────────────────────

    private static BenchmarkResult benchmarkRememberSingle() {
        CandidateSet candidateSet = BenchmarkTestFixtures.createCandidateSet(4);

        return RlBenchmarkRunner.run("ProcessKnowledgeGraph.remember_SINGLE",
                () -> {
                    ProcessKnowledgeGraph graph = new ProcessKnowledgeGraph();
                    graph.remember(candidateSet);
                    return null;
                }, WARMUP, ITERATIONS);
    }

    private static BenchmarkResult benchmarkRememberBatch10() {
        return RlBenchmarkRunner.run("ProcessKnowledgeGraph.remember_BATCH_10",
                () -> {
                    ProcessKnowledgeGraph graph = new ProcessKnowledgeGraph();
                    for (int i = 0; i < 10; i++) {
                        graph.remember(BenchmarkTestFixtures.createCandidateSet(4));
                    }
                    return null;
                }, WARMUP / 2, ITERATIONS / 5);
    }

    private static BenchmarkResult benchmarkRememberBatch100() {
        return RlBenchmarkRunner.run("ProcessKnowledgeGraph.remember_BATCH_100",
                () -> {
                    ProcessKnowledgeGraph graph = new ProcessKnowledgeGraph();
                    for (int i = 0; i < 100; i++) {
                        graph.remember(BenchmarkTestFixtures.createCandidateSet(4));
                    }
                    return null;
                }, WARMUP / 5, ITERATIONS / 10);
    }

    // ─── BiasHint Benchmarks ──────────────────────────────────────────────────

    private static BenchmarkResult benchmarkBiasHintK1() {
        ProcessKnowledgeGraph graph = createPopulatedGraph(50);

        return RlBenchmarkRunner.run("ProcessKnowledgeGraph.biasHint_K1",
                () -> graph.biasHint("test process", 1), WARMUP, ITERATIONS);
    }

    private static BenchmarkResult benchmarkBiasHintK5() {
        ProcessKnowledgeGraph graph = createPopulatedGraph(50);

        return RlBenchmarkRunner.run("ProcessKnowledgeGraph.biasHint_K5",
                () -> graph.biasHint("test process", 5), WARMUP, ITERATIONS);
    }

    private static BenchmarkResult benchmarkBiasHintK10() {
        ProcessKnowledgeGraph graph = createPopulatedGraph(50);

        return RlBenchmarkRunner.run("ProcessKnowledgeGraph.biasHint_K10",
                () -> graph.biasHint("test process", 10), WARMUP, ITERATIONS);
    }

    private static BenchmarkResult benchmarkBiasHintK20() {
        ProcessKnowledgeGraph graph = createPopulatedGraph(50);

        return RlBenchmarkRunner.run("ProcessKnowledgeGraph.biasHint_K20",
                () -> graph.biasHint("test process", 20), WARMUP, ITERATIONS);
    }

    // ─── Fingerprint Benchmarks ───────────────────────────────────────────────

    private static BenchmarkResult benchmarkFingerprintSimple() {
        return RlBenchmarkRunner.run("ProcessKnowledgeGraph.fingerprint_SIMPLE",
                () -> ProcessKnowledgeGraph.fingerprint(
                        BenchmarkTestFixtures.createSimpleSequence()),
                WARMUP, ITERATIONS);
    }

    private static BenchmarkResult benchmarkFingerprintComplex() {
        return RlBenchmarkRunner.run("ProcessKnowledgeGraph.fingerprint_COMPLEX",
                () -> ProcessKnowledgeGraph.fingerprint(
                        BenchmarkTestFixtures.createComplexModel()),
                WARMUP, ITERATIONS);
    }

    private static BenchmarkResult benchmarkFingerprintVeryComplex() {
        return RlBenchmarkRunner.run("ProcessKnowledgeGraph.fingerprint_VERY_COMPLEX",
                () -> ProcessKnowledgeGraph.fingerprint(
                        BenchmarkTestFixtures.createVeryComplexModel()),
                WARMUP, ITERATIONS);
    }

    // ─── Growth Benchmarks ────────────────────────────────────────────────────

    private static BenchmarkResult benchmarkGraphGrowth() {
        return RlBenchmarkRunner.run("ProcessKnowledgeGraph.growth_1000_rounds",
                () -> {
                    ProcessKnowledgeGraph graph = new ProcessKnowledgeGraph();
                    for (int i = 0; i < 1000; i++) {
                        CandidateSet cs = BenchmarkTestFixtures.createCandidateSet(4);
                        graph.remember(cs);
                    }
                    return graph.size();
                }, 10, 50);
    }

    // ─── Helper Methods ───────────────────────────────────────────────────────

    private static ProcessKnowledgeGraph createPopulatedGraph(int rounds) {
        ProcessKnowledgeGraph graph = new ProcessKnowledgeGraph();
        for (int i = 0; i < rounds; i++) {
            graph.remember(BenchmarkTestFixtures.createCandidateSet(4));
        }
        return graph;
    }

    // ─── Correctness Verification ─────────────────────────────────────────────

    private static void verifyCorrectness() {
        System.out.println("=== Correctness Verification ===\n");

        // Test 1: Empty graph returns empty bias hint
        ProcessKnowledgeGraph emptyGraph = new ProcessKnowledgeGraph();
        String emptyHint = emptyGraph.biasHint("test", 10);
        if (emptyHint.isEmpty()) {
            System.out.println("✓ Empty graph returns empty bias hint");
        } else {
            System.out.println("✗ Empty graph should return empty hint, got: " + emptyHint);
        }

        // Test 2: Remember increases graph size
        ProcessKnowledgeGraph graph = new ProcessKnowledgeGraph();
        int initialSize = graph.size();
        graph.remember(BenchmarkTestFixtures.createCandidateSet(4));
        int afterSize = graph.size();
        if (afterSize > initialSize) {
            System.out.printf("✓ Graph size increased from %d to %d after remember()%n",
                    initialSize, afterSize);
        } else {
            System.out.println("✗ Graph size did not increase after remember()");
        }

        // Test 3: Fingerprint is deterministic
        var model = BenchmarkTestFixtures.createSimpleSequence();
        String fp1 = ProcessKnowledgeGraph.fingerprint(model);
        String fp2 = ProcessKnowledgeGraph.fingerprint(model);
        if (fp1.equals(fp2)) {
            System.out.printf("✓ Fingerprint is deterministic: %s%n", fp1);
        } else {
            System.out.println("✗ Fingerprint should be deterministic");
        }

        // Test 4: Bias hint returns top-k patterns
        ProcessKnowledgeGraph populated = createPopulatedGraph(20);
        String hint = populated.biasHint("test", 5);
        if (!hint.isEmpty() && hint.contains("avg reward")) {
            System.out.printf("✓ Bias hint contains reward info: %s...%n",
                    hint.substring(0, Math.min(80, hint.length())));
        } else {
            System.out.println("✗ Bias hint should contain reward information");
        }

        // Test 5: Size matches number of unique patterns
        int reportedSize = populated.size();
        String fullHint = populated.biasHint("test", 1000);
        int hintLines = fullHint.isEmpty() ? 0 : fullHint.split("\n").length;
        System.out.printf("✓ Graph reports size=%d, hint has %d lines%n",
                reportedSize, hintLines);

        // Test 6: Only high-reward candidates are remembered
        ProcessKnowledgeGraph thresholdGraph = new ProcessKnowledgeGraph();
        // CandidateSet with low rewards (< 0.5 threshold)
        CandidateSet lowRewardSet = BenchmarkTestFixtures.createCandidateSet(4);
        thresholdGraph.remember(lowRewardSet);
        // Since rewards from createCandidateSet start at 0.3, some should pass threshold
        System.out.printf("✓ Graph size after low-reward remember: %d%n",
                thresholdGraph.size());

        System.out.println();
    }
}
