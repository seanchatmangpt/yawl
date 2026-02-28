/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it/modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.dspy.scoring;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import java.util.List;

/**
 * Tests for FootprintScorer.
 */
class FootprintScorerTest {

    @Test
    void testIdenticalFootprints() {
        BehavioralFootprint ref = createFootprint(
            Set.of(List.of("A", "B"), List.of("B", "C")),
            Set.of(List.of("A", "C")),
            Set.of(List.of("B", "D"))
        );

        BehavioralFootprint generated = createFootprint(
            Set.of(List.of("A", "B"), List.of("B", "C")),
            Set.of(List.of("A", "C")),
            Set.of(List.of("B", "D"))
        );

        FootprintScorer scorer = new FootprintScorer();
        double score = scorer.scoreFootprint(ref, generated);

        assertEquals(1.0, score, 0.001, "Identical footprints should score 1.0");
    }

    @Test
    void testNoCommonRelationships() {
        BehavioralFootprint ref = createFootprint(
            Set.of(List.of("A", "B")),
            Set.of(List.of("C", "D")),
            Set.of(List.of("E", "F"))
        );

        BehavioralFootprint generated = createFootprint(
            Set.of(List.of("X", "Y")),
            Set.of(List.of("Z", "W")),
            Set.of(List.of("V", "U"))
        );

        FootprintScorer scorer = new FootprintScorer();
        double score = scorer.scoreFootprint(ref, generated);

        assertEquals(0.0, score, 0.001, "No common relationships should score 0.0");
    }

    @Test
    void testEmptyFootprints() {
        BehavioralFootprint ref = BehavioralFootprint.empty();
        BehavioralFootprint generated = BehavioralFootprint.empty();

        FootprintScorer scorer = new FootprintScorer();
        double score = scorer.scoreFootprint(ref, generated);

        assertEquals(1.0, score, 0.001, "Both empty footprints should score 1.0");
    }

    @Test
    void testPartialMatch() {
        BehavioralFootprint ref = createFootprint(
            Set.of(List.of("A", "B"), List.of("B", "C")),
            Set.of(List.of("A", "C")),
            Set.of(List.of("B", "D"))
        );

        BehavioralFootprint generated = createFootprint(
            Set.of(List.of("A", "B"), List.of("B", "X")),
            Set.of(List.of("A", "C")),
            Set.of(List.of("B", "Y"))
        );

        FootprintScorer scorer = new FootprintScorer();
        double score = scorer.scoreFootprint(ref, generated);

        assertTrue(score > 0.0 && score < 1.0,
            "Partial match should give score between 0 and 1, got: " + score);
    }

    @Test
    void testNullReference() {
        BehavioralFootprint generated = createFootprint(
            Set.of(List.of("A", "B")),
            Set.of(),
            Set.of()
        );

        FootprintScorer scorer = new FootprintScorer();

        assertThrows(IllegalArgumentException.class, () ->
            scorer.scoreFootprint(null, generated)
        );
    }

    @Test
    void testNullGenerated() {
        BehavioralFootprint ref = createFootprint(
            Set.of(List.of("A", "B")),
            Set.of(),
            Set.of()
        );

        FootprintScorer scorer = new FootprintScorer();

        assertThrows(IllegalArgumentException.class, () ->
            scorer.scoreFootprint(ref, null)
        );
    }

    private static BehavioralFootprint createFootprint(
            Set<List<String>> directSuccession,
            Set<List<String>> concurrency,
            Set<List<String>> exclusivity) {
        return new BehavioralFootprint(directSuccession, concurrency, exclusivity);
    }
}