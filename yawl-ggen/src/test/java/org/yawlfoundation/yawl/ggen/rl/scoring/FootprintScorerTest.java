/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.rl.scoring;

import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.ggen.powl.PowlActivity;
import org.yawlfoundation.yawl.ggen.powl.PowlModel;
import org.yawlfoundation.yawl.ggen.powl.PowlOperatorNode;
import org.yawlfoundation.yawl.ggen.powl.PowlOperatorType;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FootprintExtractor and FootprintScorer.
 *
 * <p>Behavioral footprints are extracted from POWL models and compared using
 * macro-averaged Jaccard similarity. Tests verify extraction correctness and
 * score boundary conditions.
 */
class FootprintScorerTest {

    private static final String DESC = "test process";

    // ----- FootprintExtractor -----

    @Test
    void extract_sequence_populatesDirectSuccession() {
        // SEQUENCE(a, b, c)
        PowlActivity a = new PowlActivity("a", "a");
        PowlActivity b = new PowlActivity("b", "b");
        PowlActivity c = new PowlActivity("c", "c");
        PowlOperatorNode seq = new PowlOperatorNode("seq", PowlOperatorType.SEQUENCE, List.of(a, b, c));
        PowlModel model = PowlModel.of("m", seq);

        FootprintMatrix fp = new FootprintExtractor().extract(model);

        // SEQUENCE adds (last_i, first_{i+1}) for consecutive children
        assertTrue(fp.directSuccession().contains(List.of("a", "b")),
            "SEQUENCE(a,b,c): directSuccession must contain (a,b)");
        assertTrue(fp.directSuccession().contains(List.of("b", "c")),
            "SEQUENCE(a,b,c): directSuccession must contain (b,c)");
        assertEquals(2, fp.directSuccession().size(),
            "SEQUENCE(a,b,c): exactly 2 succession pairs");
        assertTrue(fp.concurrency().isEmpty(), "SEQUENCE has no concurrency");
        assertTrue(fp.exclusive().isEmpty(), "SEQUENCE has no exclusivity");
    }

    @Test
    void extract_xor_populatesExclusive() {
        PowlActivity a = new PowlActivity("a", "a");
        PowlActivity b = new PowlActivity("b", "b");
        PowlOperatorNode xor = new PowlOperatorNode("xor", PowlOperatorType.XOR, List.of(a, b));
        PowlModel model = PowlModel.of("m", xor);

        FootprintMatrix fp = new FootprintExtractor().extract(model);

        // XOR adds symmetric pairs: (a,b) and (b,a)
        assertTrue(fp.exclusive().contains(List.of("a", "b")),
            "XOR must add (a,b) to exclusive");
        assertTrue(fp.exclusive().contains(List.of("b", "a")),
            "XOR must add (b,a) to exclusive (symmetric)");
        assertEquals(2, fp.exclusive().size(), "XOR(a,b): exactly 2 exclusive pairs");
        assertTrue(fp.directSuccession().isEmpty(), "XOR has no direct succession");
        assertTrue(fp.concurrency().isEmpty(), "XOR has no concurrency");
    }

    @Test
    void extract_parallel_populatesConcurrency() {
        PowlActivity a = new PowlActivity("a", "a");
        PowlActivity b = new PowlActivity("b", "b");
        PowlOperatorNode par = new PowlOperatorNode("par", PowlOperatorType.PARALLEL, List.of(a, b));
        PowlModel model = PowlModel.of("m", par);

        FootprintMatrix fp = new FootprintExtractor().extract(model);

        // PARALLEL adds symmetric pairs: (a,b) and (b,a)
        assertTrue(fp.concurrency().contains(List.of("a", "b")),
            "PARALLEL must add (a,b) to concurrency");
        assertTrue(fp.concurrency().contains(List.of("b", "a")),
            "PARALLEL must add (b,a) to concurrency (symmetric)");
        assertEquals(2, fp.concurrency().size(), "PARALLEL(a,b): exactly 2 concurrency pairs");
        assertTrue(fp.directSuccession().isEmpty(), "PARALLEL has no direct succession");
        assertTrue(fp.exclusive().isEmpty(), "PARALLEL has no exclusivity");
    }

    @Test
    void extract_loop_populatesDirectSuccessionBothWays() {
        PowlActivity doAct = new PowlActivity("do", "do");
        PowlActivity redoAct = new PowlActivity("redo", "redo");
        PowlOperatorNode loop = new PowlOperatorNode("loop", PowlOperatorType.LOOP,
            List.of(doAct, redoAct));
        PowlModel model = PowlModel.of("m", loop);

        FootprintMatrix fp = new FootprintExtractor().extract(model);

        // LOOP adds forward (do→redo) and back-edge (redo→do)
        assertTrue(fp.directSuccession().contains(List.of("do", "redo")),
            "LOOP must add (do,redo) to directSuccession");
        assertTrue(fp.directSuccession().contains(List.of("redo", "do")),
            "LOOP must add (redo,do) to directSuccession (back-edge)");
    }

    @Test
    void extract_singleActivity_allEmptySets() {
        PowlModel model = PowlModel.of("m", new PowlActivity("a", "a"));
        FootprintMatrix fp = new FootprintExtractor().extract(model);
        assertTrue(fp.directSuccession().isEmpty());
        assertTrue(fp.concurrency().isEmpty());
        assertTrue(fp.exclusive().isEmpty());
    }

    @Test
    void extract_nullModel_throwsNullPointer() {
        assertThrows(NullPointerException.class, () -> new FootprintExtractor().extract(null));
    }

    // ----- FootprintScorer -----

    @Test
    void score_identicalReference_returnsOne() {
        // SEQUENCE(a, b, c) scored against its own footprint = 1.0
        PowlActivity a = new PowlActivity("a", "a");
        PowlActivity b = new PowlActivity("b", "b");
        PowlActivity c = new PowlActivity("c", "c");
        PowlOperatorNode seq = new PowlOperatorNode("seq", PowlOperatorType.SEQUENCE, List.of(a, b, c));
        PowlModel model = PowlModel.of("m", seq);

        FootprintMatrix reference = new FootprintExtractor().extract(model);
        FootprintScorer scorer = new FootprintScorer(reference);

        double score = scorer.score(model, DESC);
        assertEquals(1.0, score, 1e-9, "Identical footprint should score 1.0");
    }

    @Test
    void score_emptyModelAgainstFullReference_returnsZero() {
        // Single activity (all-empty footprint) scored against a reference with all dimensions populated
        PowlModel emptyCandidate = PowlModel.of("m", new PowlActivity("x", "x"));

        // Build a non-empty reference directly
        FootprintMatrix reference = new FootprintMatrix(
            Set.of(List.of("p", "q")),    // directSuccession
            Set.of(List.of("r", "s")),    // concurrency
            Set.of(List.of("t", "u"))     // exclusive
        );
        FootprintScorer scorer = new FootprintScorer(reference);

        double score = scorer.score(emptyCandidate, DESC);
        // candidate is empty in all 3 dims; reference is non-empty in all 3 dims
        // Jaccard({}, {p,q}) = 0/(1) = 0 for each dim → avg = 0.0
        assertEquals(0.0, score, 1e-9, "Empty candidate against non-empty reference should score 0.0");
    }

    @Test
    void score_partialMatch_returnsValueBetweenZeroAndOne() {
        // Candidate: SEQUENCE(a, b, c) → ds={(a,b),(b,c)}
        // Reference:  SEQUENCE(a, b, x) → ds={(a,b),(b,x)}
        // Shared: (a,b); ds Jaccard = 1/3; concurrency=1.0; exclusive=1.0; avg = (1/3+1+1)/3
        PowlActivity a = new PowlActivity("a", "a");
        PowlActivity b = new PowlActivity("b", "b");
        PowlActivity c = new PowlActivity("c", "c");
        PowlActivity x = new PowlActivity("x", "x");

        PowlOperatorNode candidate = new PowlOperatorNode("seq1", PowlOperatorType.SEQUENCE, List.of(a, b, c));
        PowlOperatorNode refModel = new PowlOperatorNode("seq2", PowlOperatorType.SEQUENCE, List.of(a, b, x));

        FootprintMatrix reference = new FootprintExtractor().extract(PowlModel.of("ref", refModel));
        FootprintScorer scorer = new FootprintScorer(reference);

        double score = scorer.score(PowlModel.of("cand", candidate), DESC);
        assertTrue(score > 0.0 && score < 1.0,
            "Partial footprint match should give score in (0, 1), got: " + score);
    }

    @Test
    void score_emptyModelAgainstEmptyReference_returnsOne() {
        // Both candidate and reference are single activities (all-empty footprints)
        // Jaccard({}, {}) = 1.0 for each dim → avg = 1.0
        PowlModel candidate = PowlModel.of("c", new PowlActivity("a", "a"));
        FootprintMatrix reference = FootprintMatrix.empty();
        FootprintScorer scorer = new FootprintScorer(reference);

        double score = scorer.score(candidate, DESC);
        assertEquals(1.0, score, 1e-9, "Both empty: Jaccard({},{})=1.0 for all dims");
    }

    @Test
    void score_inRange() {
        // Score should always be in [0.0, 1.0]
        PowlActivity a = new PowlActivity("a", "a");
        PowlActivity b = new PowlActivity("b", "b");
        PowlOperatorNode xor = new PowlOperatorNode("xor", PowlOperatorType.XOR, List.of(a, b));
        PowlModel model = PowlModel.of("m", xor);

        FootprintMatrix reference = FootprintMatrix.empty();
        FootprintScorer scorer = new FootprintScorer(reference);

        double score = scorer.score(model, DESC);
        assertTrue(score >= 0.0 && score <= 1.0,
            "Score must be in [0.0, 1.0], got: " + score);
    }

    @Test
    void score_nullCandidate_throwsNullPointer() {
        FootprintScorer scorer = new FootprintScorer(FootprintMatrix.empty());
        assertThrows(NullPointerException.class, () -> scorer.score(null, DESC));
    }

    @Test
    void footprintScorerConstructor_nullReference_throwsNullPointer() {
        assertThrows(NullPointerException.class, () -> new FootprintScorer(null));
    }
}
