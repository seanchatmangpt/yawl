/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.rl.scoring.benchmark;

import org.yawlfoundation.yawl.ggen.powl.PowlModel;
import org.yawlfoundation.yawl.ggen.rl.benchmark.BenchmarkTestFixtures;
import org.yawlfoundation.yawl.ggen.rl.benchmark.RlBenchmarkRunner;
import org.yawlfoundation.yawl.ggen.rl.benchmark.RlBenchmarkRunner.BenchmarkResult;
import org.yawlfoundation.yawl.ggen.rl.scoring.FootprintExtractor;
import org.yawlfoundation.yawl.ggen.rl.scoring.FootprintMatrix;
import org.yawlfoundation.yawl.ggen.rl.scoring.FootprintScorer;

import java.util.ArrayList;
import java.util.List;

/**
 * Benchmarks for footprint extraction and scoring operations.
 *
 * <p>Measures latency of:
 * <ul>
 *   <li>{@link FootprintExtractor#extract(PowlModel)} by model complexity</li>
 *   <li>{@link FootprintScorer#score(PowlModel, String)} with Jaccard similarity</li>
 *   <li>Jaccard similarity computation for various set sizes</li>
 * </ul>
 *
 * <h2>Running Benchmarks</h2>
 * <pre>{@code
 * mvn test -Dtest=FootprintBenchmark -Dmaven.test.skip=false
 * }</pre>
 */
public final class FootprintBenchmark {

    private static final int WARMUP = 500;
    private static final int ITERATIONS = 5000;

    private FootprintBenchmark() {
        throw new UnsupportedOperationException("Run via main()");
    }

    public static void main(String[] args) {
        System.out.println("=== Footprint Extraction & Scoring Benchmarks ===\n");
        System.out.println("Warmup: " + WARMUP + " iterations");
        System.out.println("Measured: " + ITERATIONS + " iterations\n");

        List<BenchmarkResult> results = new ArrayList<>();

        // Extraction benchmarks by complexity
        results.add(benchmarkExtractSimple());
        results.add(benchmarkExtractMedium());
        results.add(benchmarkExtractComplex());
        results.add(benchmarkExtractVeryComplex());

        // Scoring benchmarks
        results.add(benchmarkScoreSimple());
        results.add(benchmarkScoreMedium());
        results.add(benchmarkScoreComplex());
        results.add(benchmarkScoreVeryComplex());

        // Jaccard similarity benchmarks
        results.add(benchmarkJaccardSmall());
        results.add(benchmarkJaccardMedium());
        results.add(benchmarkJaccardLarge());

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

    // ─── Extraction Benchmarks ────────────────────────────────────────────────

    private static BenchmarkResult benchmarkExtractSimple() {
        PowlModel model = BenchmarkTestFixtures.createSimpleSequence();
        FootprintExtractor extractor = new FootprintExtractor();

        return RlBenchmarkRunner.run("FootprintExtractor.extract_SIMPLE",
                () -> extractor.extract(model), WARMUP, ITERATIONS);
    }

    private static BenchmarkResult benchmarkExtractMedium() {
        PowlModel model = BenchmarkTestFixtures.createMediumModel();
        FootprintExtractor extractor = new FootprintExtractor();

        return RlBenchmarkRunner.run("FootprintExtractor.extract_MEDIUM",
                () -> extractor.extract(model), WARMUP, ITERATIONS);
    }

    private static BenchmarkResult benchmarkExtractComplex() {
        PowlModel model = BenchmarkTestFixtures.createComplexModel();
        FootprintExtractor extractor = new FootprintExtractor();

        return RlBenchmarkRunner.run("FootprintExtractor.extract_COMPLEX",
                () -> extractor.extract(model), WARMUP, ITERATIONS);
    }

    private static BenchmarkResult benchmarkExtractVeryComplex() {
        PowlModel model = BenchmarkTestFixtures.createVeryComplexModel();
        FootprintExtractor extractor = new FootprintExtractor();

        return RlBenchmarkRunner.run("FootprintExtractor.extract_VERY_COMPLEX",
                () -> extractor.extract(model), WARMUP, ITERATIONS);
    }

    // ─── Scoring Benchmarks ───────────────────────────────────────────────────

    private static BenchmarkResult benchmarkScoreSimple() {
        PowlModel candidate = BenchmarkTestFixtures.createSimpleSequence();
        PowlModel reference = BenchmarkTestFixtures.createSimpleSequence();
        FootprintExtractor extractor = new FootprintExtractor();
        FootprintMatrix refFp = extractor.extract(reference);
        FootprintScorer scorer = new FootprintScorer(refFp);

        return RlBenchmarkRunner.run("FootprintScorer.score_SIMPLE",
                () -> scorer.score(candidate, BenchmarkTestFixtures.PROCESS_SIMPLE),
                WARMUP, ITERATIONS);
    }

    private static BenchmarkResult benchmarkScoreMedium() {
        PowlModel candidate = BenchmarkTestFixtures.createMediumModel();
        PowlModel reference = BenchmarkTestFixtures.createMediumModel();
        FootprintExtractor extractor = new FootprintExtractor();
        FootprintMatrix refFp = extractor.extract(reference);
        FootprintScorer scorer = new FootprintScorer(refFp);

        return RlBenchmarkRunner.run("FootprintScorer.score_MEDIUM",
                () -> scorer.score(candidate, BenchmarkTestFixtures.PROCESS_MEDIUM),
                WARMUP, ITERATIONS);
    }

    private static BenchmarkResult benchmarkScoreComplex() {
        PowlModel candidate = BenchmarkTestFixtures.createComplexModel();
        PowlModel reference = BenchmarkTestFixtures.createComplexModel();
        FootprintExtractor extractor = new FootprintExtractor();
        FootprintMatrix refFp = extractor.extract(reference);
        FootprintScorer scorer = new FootprintScorer(refFp);

        return RlBenchmarkRunner.run("FootprintScorer.score_COMPLEX",
                () -> scorer.score(candidate, BenchmarkTestFixtures.PROCESS_COMPLEX),
                WARMUP, ITERATIONS);
    }

    private static BenchmarkResult benchmarkScoreVeryComplex() {
        PowlModel candidate = BenchmarkTestFixtures.createVeryComplexModel();
        PowlModel reference = BenchmarkTestFixtures.createVeryComplexModel();
        FootprintExtractor extractor = new FootprintExtractor();
        FootprintMatrix refFp = extractor.extract(reference);
        FootprintScorer scorer = new FootprintScorer(refFp);

        return RlBenchmarkRunner.run("FootprintScorer.score_VERY_COMPLEX",
                () -> scorer.score(candidate, BenchmarkTestFixtures.PROCESS_VERY_COMPLEX),
                WARMUP, ITERATIONS);
    }

    // ─── Jaccard Similarity Benchmarks ────────────────────────────────────────

    private static BenchmarkResult benchmarkJaccardSmall() {
        // Create two small footprints with ~5 relationships each
        PowlModel a = BenchmarkTestFixtures.createSimpleSequence();
        PowlModel b = BenchmarkTestFixtures.createSimpleXor();
        FootprintExtractor extractor = new FootprintExtractor();
        FootprintMatrix fpA = extractor.extract(a);
        FootprintMatrix fpB = extractor.extract(b);
        FootprintScorer scorer = new FootprintScorer(fpA);

        return RlBenchmarkRunner.run("Jaccard_similarity_SMALL",
                () -> scorer.score(b, "test"), WARMUP, ITERATIONS);
    }

    private static BenchmarkResult benchmarkJaccardMedium() {
        // Create two medium footprints with ~15 relationships each
        PowlModel a = BenchmarkTestFixtures.createMediumModel();
        PowlModel b = BenchmarkTestFixtures.createComplexModel();
        FootprintExtractor extractor = new FootprintExtractor();
        FootprintMatrix fpA = extractor.extract(a);
        FootprintScorer scorer = new FootprintScorer(fpA);

        return RlBenchmarkRunner.run("Jaccard_similarity_MEDIUM",
                () -> scorer.score(b, "test"), WARMUP, ITERATIONS);
    }

    private static BenchmarkResult benchmarkJaccardLarge() {
        // Create two large footprints with ~50+ relationships each
        PowlModel a = BenchmarkTestFixtures.createVeryComplexModel();
        PowlModel b = BenchmarkTestFixtures.createVeryComplexModel();
        FootprintExtractor extractor = new FootprintExtractor();
        FootprintMatrix fpA = extractor.extract(a);
        FootprintScorer scorer = new FootprintScorer(fpA);

        return RlBenchmarkRunner.run("Jaccard_similarity_LARGE",
                () -> scorer.score(b, "test"), WARMUP, ITERATIONS);
    }

    // ─── Correctness Verification ─────────────────────────────────────────────

    private static void verifyCorrectness() {
        System.out.println("=== Correctness Verification ===\n");

        FootprintExtractor extractor = new FootprintExtractor();

        // Test 1: Self-similarity should be 1.0
        PowlModel model = BenchmarkTestFixtures.createMediumModel();
        FootprintMatrix fp = extractor.extract(model);
        FootprintScorer scorer = new FootprintScorer(fp);
        double selfScore = scorer.score(model, "test");

        if (Math.abs(selfScore - 1.0) < 0.001) {
            System.out.printf("✓ Self-similarity score: %.4f (expected 1.0)%n", selfScore);
        } else {
            System.out.printf("✗ Self-similarity score: %.4f (expected 1.0)%n", selfScore);
        }

        // Test 2: Different models should have lower similarity
        PowlModel seq = BenchmarkTestFixtures.createSimpleSequence();
        PowlModel xor = BenchmarkTestFixtures.createSimpleXor();
        FootprintMatrix seqFp = extractor.extract(seq);
        FootprintScorer seqScorer = new FootprintScorer(seqFp);
        double diffScore = seqScorer.score(xor, "test");

        System.out.printf("✓ Sequence vs XOR similarity: %.4f%n", diffScore);

        // Test 3: Verify footprint extraction captures relationships
        PowlModel complex = BenchmarkTestFixtures.createComplexModel();
        FootprintMatrix complexFp = extractor.extract(complex);

        System.out.printf("✓ Complex model footprint:%n");
        System.out.printf("    Direct successions: %d%n", complexFp.directSuccession().size());
        System.out.printf("    Concurrency: %d%n", complexFp.concurrency().size());
        System.out.printf("    Exclusive: %d%n", complexFp.exclusive().size());

        // Test 4: Verify XOR produces exclusive relationships
        FootprintMatrix xorFp = extractor.extract(xor);
        if (!xorFp.exclusive().isEmpty()) {
            System.out.printf("✓ XOR model has %d exclusive relationships%n", xorFp.exclusive().size());
        } else {
            System.out.println("✗ XOR model should have exclusive relationships");
        }

        // Test 5: Verify PARALLEL produces concurrency relationships
        PowlModel parallel = BenchmarkTestFixtures.createSimpleParallel();
        FootprintMatrix parFp = extractor.extract(parallel);
        if (!parFp.concurrency().isEmpty()) {
            System.out.printf("✓ PARALLEL model has %d concurrency relationships%n",
                    parFp.concurrency().size());
        } else {
            System.out.println("✗ PARALLEL model should have concurrency relationships");
        }

        System.out.println();
    }
}
