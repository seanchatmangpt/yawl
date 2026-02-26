/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.rl;

import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.ggen.powl.PowlActivity;
import org.yawlfoundation.yawl.ggen.powl.PowlModel;
import org.yawlfoundation.yawl.ggen.powl.PowlOperatorNode;
import org.yawlfoundation.yawl.ggen.powl.PowlOperatorType;
import org.yawlfoundation.yawl.ggen.rl.scoring.RewardFunction;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GroupAdvantage, CandidateSet, and GrpoOptimizer.
 *
 * <p>Uses an inner-class CandidateSampler implementation (not a mock) to provide
 * fixed candidate models for deterministic testing. The GrpoOptimizer is tested
 * end-to-end with real PowlValidator and real advantage computation.
 */
class GrpoOptimizerTest {

    // ----- GroupAdvantage -----

    @Test
    void computeGroupAdvantage_knownRewards_correctMeanAndStd() {
        // rewards = [0.5, 0.8, 0.3, 0.7]
        // mean = 2.3/4 = 0.575
        List<Double> rewards = List.of(0.5, 0.8, 0.3, 0.7);
        GroupAdvantage ga = GroupAdvantage.compute(rewards);

        assertEquals(0.575, ga.mean(), 1e-9, "Mean must equal 0.575");
        // variance = ((0.5-0.575)^2 + (0.8-0.575)^2 + (0.3-0.575)^2 + (0.7-0.575)^2) / 4
        //           = (0.005625 + 0.050625 + 0.075625 + 0.015625) / 4 = 0.036875
        // std = sqrt(0.036875) ≈ 0.19203
        assertEquals(Math.sqrt(0.036875), ga.std(), 1e-9, "Std must equal sqrt(0.036875)");
    }

    @Test
    void computeGroupAdvantage_advantagesSumToZero() {
        // Advantages should sum to approximately zero (mean-centered)
        List<Double> rewards = List.of(0.5, 0.8, 0.3, 0.7);
        GroupAdvantage ga = GroupAdvantage.compute(rewards);

        double sum = ga.advantages().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(0.0, sum, 1e-6, "Advantages should sum to ~0 (mean-centered)");
    }

    @Test
    void computeGroupAdvantage_highestRewardHasHighestAdvantage() {
        // reward 0.8 at index 1 should have the highest advantage
        List<Double> rewards = List.of(0.5, 0.8, 0.3, 0.7);
        GroupAdvantage ga = GroupAdvantage.compute(rewards);

        int bestIdx = ga.bestIndex();
        assertEquals(1, bestIdx, "Index 1 (reward=0.8) should have highest advantage");
    }

    @Test
    void computeGroupAdvantage_lowestRewardHasLowestAdvantage() {
        // reward 0.3 at index 2 should have the most negative advantage
        List<Double> rewards = List.of(0.5, 0.8, 0.3, 0.7);
        GroupAdvantage ga = GroupAdvantage.compute(rewards);

        double minAdvantage = ga.advantages().stream().mapToDouble(Double::doubleValue).min().orElseThrow();
        assertEquals(ga.advantages().get(2), minAdvantage, 1e-9,
            "Index 2 (reward=0.3) should have the lowest advantage");
    }

    @Test
    void computeGroupAdvantage_equalRewards_allAdvantagesNearZero() {
        List<Double> rewards = List.of(0.7, 0.7, 0.7, 0.7);
        GroupAdvantage ga = GroupAdvantage.compute(rewards);

        for (double adv : ga.advantages()) {
            assertEquals(0.0, adv, 1e-6, "All equal rewards: advantage must be near zero (std≈0)");
        }
    }

    @Test
    void computeGroupAdvantage_emptyRewards_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> GroupAdvantage.compute(List.of()));
    }

    @Test
    void computeGroupAdvantage_nullRewards_throwsNullPointer() {
        assertThrows(NullPointerException.class, () -> GroupAdvantage.compute(null));
    }

    @Test
    void computeGroupAdvantage_singleReward_advantageIsZero() {
        GroupAdvantage ga = GroupAdvantage.compute(List.of(0.6));
        assertEquals(0.0, ga.advantages().get(0), 1e-6, "Single reward: advantage = 0/ε ≈ 0");
    }

    // ----- CandidateSet -----

    @Test
    void candidateSet_bestReturnsHighestRewardCandidate() {
        PowlModel modelA = PowlModel.of("a", new PowlActivity("a", "A"));
        PowlModel modelB = PowlModel.of("b", new PowlActivity("b", "B"));
        PowlModel modelC = PowlModel.of("c", new PowlActivity("c", "C"));

        CandidateSet cs = new CandidateSet(
            List.of(modelA, modelB, modelC),
            List.of(0.3, 0.9, 0.5)
        );

        assertEquals(1, cs.bestIndex(), "bestIndex() must return index 1 (reward=0.9)");
        assertSame(modelB, cs.best(), "best() must return modelB");
    }

    @Test
    void candidateSet_sizeMismatch_throwsIllegalArgument() {
        PowlModel modelA = PowlModel.of("a", new PowlActivity("a", "A"));
        assertThrows(IllegalArgumentException.class,
            () -> new CandidateSet(List.of(modelA), List.of(0.5, 0.7)));
    }

    @Test
    void candidateSet_empty_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> new CandidateSet(List.of(), List.of()));
    }

    // ----- GrpoOptimizer (with inner-class CandidateSampler) -----

    /**
     * Fixed CandidateSampler for deterministic testing.
     * Returns K pre-built POWL models regardless of process description.
     */
    static class FixedCandidateSampler implements CandidateSampler {
        private final List<PowlModel> candidates;

        FixedCandidateSampler(List<PowlModel> candidates) {
            this.candidates = candidates;
        }

        @Override
        public List<PowlModel> sample(String processDescription, int k) {
            return candidates.subList(0, Math.min(k, candidates.size()));
        }
    }

    /**
     * Deterministic reward function based on model ID → score from a fixed map.
     */
    static class MappedRewardFunction implements RewardFunction {
        private final Map<String, Double> scores;

        MappedRewardFunction(Map<String, Double> scores) {
            this.scores = scores;
        }

        @Override
        public double score(PowlModel candidate, String processDescription) {
            return scores.getOrDefault(candidate.id(), 0.0);
        }
    }

    @Test
    void evaluateCandidates_returnsAllCandidatesWithCorrectRewards() throws IOException, PowlParseException {
        PowlModel m1 = PowlModel.of("m1", new PowlActivity("a", "A"));
        PowlModel m2 = PowlModel.of("m2", new PowlActivity("b", "B"));
        PowlModel m3 = PowlModel.of("m3", new PowlActivity("c", "C"));

        CandidateSampler sampler = new FixedCandidateSampler(List.of(m1, m2, m3));
        RewardFunction rf = new MappedRewardFunction(Map.of("m1", 0.4, "m2", 0.9, "m3", 0.6));
        RlConfig config = new RlConfig(3, CurriculumStage.VALIDITY_GAP, 3,
            "http://localhost:11434", "test-model", 30);

        GrpoOptimizer optimizer = new GrpoOptimizer(sampler, rf, config);
        CandidateSet result = optimizer.evaluateCandidates("Process description");

        assertEquals(3, result.candidates().size(), "All 3 candidates should be returned");
        assertEquals(0.4, result.rewards().get(0), 1e-9);
        assertEquals(0.9, result.rewards().get(1), 1e-9);
        assertEquals(0.6, result.rewards().get(2), 1e-9);
    }

    @Test
    void optimize_returnsBestCandidate() throws IOException, PowlParseException {
        PowlModel m1 = PowlModel.of("m1", new PowlActivity("a", "A"));
        PowlModel m2 = PowlModel.of("m2", new PowlActivity("b", "B"));
        PowlModel m3 = PowlModel.of("m3", new PowlActivity("c", "C"));

        CandidateSampler sampler = new FixedCandidateSampler(List.of(m1, m2, m3));
        // m2 has the highest reward
        RewardFunction rf = new MappedRewardFunction(Map.of("m1", 0.4, "m2", 0.9, "m3", 0.6));
        RlConfig config = new RlConfig(3, CurriculumStage.VALIDITY_GAP, 3,
            "http://localhost:11434", "test-model", 30);

        GrpoOptimizer optimizer = new GrpoOptimizer(sampler, rf, config);
        PowlModel best = optimizer.optimize("Process description");

        assertSame(m2, best, "GrpoOptimizer must return the highest-reward candidate");
    }

    @Test
    void evaluateCandidates_blankDescription_throwsIllegalArgument() {
        CandidateSampler sampler = new FixedCandidateSampler(
            List.of(PowlModel.of("m1", new PowlActivity("a", "A")))
        );
        RewardFunction rf = (c, d) -> 0.5;
        RlConfig config = new RlConfig(1, CurriculumStage.VALIDITY_GAP, 3,
            "http://localhost:11434", "test-model", 30);
        GrpoOptimizer optimizer = new GrpoOptimizer(sampler, rf, config);

        assertThrows(IllegalArgumentException.class,
            () -> optimizer.evaluateCandidates("   "));
    }

    @Test
    void grpoOptimizer_nullSampler_throwsNullPointer() {
        RewardFunction rf = (c, d) -> 0.5;
        RlConfig config = RlConfig.defaults();
        assertThrows(NullPointerException.class, () -> new GrpoOptimizer(null, rf, config));
    }

    @Test
    void grpoOptimizer_nullRewardFunction_throwsNullPointer() {
        CandidateSampler sampler = new FixedCandidateSampler(List.of());
        RlConfig config = RlConfig.defaults();
        assertThrows(NullPointerException.class, () -> new GrpoOptimizer(sampler, null, config));
    }

    @Test
    void grpoOptimizer_nullConfig_throwsNullPointer() {
        CandidateSampler sampler = new FixedCandidateSampler(List.of());
        RewardFunction rf = (c, d) -> 0.5;
        assertThrows(NullPointerException.class, () -> new GrpoOptimizer(sampler, rf, null));
    }

    // ----- RlConfig -----

    @Test
    void rlConfig_defaults_hasExpectedValues() {
        RlConfig config = RlConfig.defaults();
        assertEquals(4, config.k());
        assertEquals(CurriculumStage.VALIDITY_GAP, config.stage());
        assertTrue(config.timeoutSecs() > 0);
    }

    @Test
    void rlConfig_outOfRangeK_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> new RlConfig(0, CurriculumStage.VALIDITY_GAP, 3,
                "http://localhost:11434", "model", 60));
    }

    @Test
    void rlConfig_kTooLarge_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> new RlConfig(17, CurriculumStage.VALIDITY_GAP, 3,
                "http://localhost:11434", "model", 60));
    }
}
