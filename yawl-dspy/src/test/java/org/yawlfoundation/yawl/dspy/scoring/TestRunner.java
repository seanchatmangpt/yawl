/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 */

package org.yawlfoundation.yawl.dspy.scoring;

import java.util.Set;
import java.util.List;

/**
 * Simple test runner for FootprintScorer.
 */
public class TestRunner {
    public static void main(String[] args) {
        System.out.println("Testing FootprintScorer...");

        // Test 1: Identical footprints
        BehavioralFootprint ref = new BehavioralFootprint(
            Set.of(List.of("A", "B"), List.of("B", "C")),
            Set.of(List.of("A", "C")),
            Set.of(List.of("B", "D"))
        );

        BehavioralFootprint generated = new BehavioralFootprint(
            Set.of(List.of("A", "B"), List.of("B", "C")),
            Set.of(List.of("A", "C")),
            Set.of(List.of("B", "D"))
        );

        FootprintScorer scorer = new FootprintScorer();
        double score1 = scorer.scoreFootprint(ref, generated);
        System.out.println("Test 1 - Identical footprints: " + score1);
        assert score1 == 1.0 : "Should be 1.0";

        // Test 2: No common relationships
        BehavioralFootprint ref2 = new BehavioralFootprint(
            Set.of(List.of("A", "B")),
            Set.of(List.of("C", "D")),
            Set.of(List.of("E", "F"))
        );

        BehavioralFootprint generated2 = new BehavioralFootprint(
            Set.of(List.of("X", "Y")),
            Set.of(List.of("Z", "W")),
            Set.of(List.of("V", "U"))
        );

        double score2 = scorer.scoreFootprint(ref2, generated2);
        System.out.println("Test 2 - No common relationships: " + score2);
        assert score2 == 0.0 : "Should be 0.0";

        // Test 3: Empty footprints
        BehavioralFootprint empty1 = BehavioralFootprint.empty();
        BehavioralFootprint empty2 = BehavioralFootprint.empty();
        double score3 = scorer.scoreFootprint(empty1, empty2);
        System.out.println("Test 3 - Empty footprints: " + score3);
        assert score3 == 1.0 : "Should be 1.0";

        // Test 4: Partial match
        BehavioralFootprint ref4 = new BehavioralFootprint(
            Set.of(List.of("A", "B"), List.of("B", "C")),
            Set.of(List.of("A", "C")),
            Set.of(List.of("B", "D"))
        );

        BehavioralFootprint generated4 = new BehavioralFootprint(
            Set.of(List.of("A", "B"), List.of("B", "X")),
            Set.of(List.of("A", "C")),
            Set.of(List.of("B", "Y"))
        );

        double score4 = scorer.scoreFootprint(ref4, generated4);
        System.out.println("Test 4 - Partial match: " + score4);
        assert score4 > 0.0 && score4 < 1.0 : "Should be between 0 and 1";

        System.out.println("All tests passed!");
    }
}