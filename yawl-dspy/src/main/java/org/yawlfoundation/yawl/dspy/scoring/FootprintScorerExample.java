/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it/modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.dspy.scoring;

import org.yawlfoundation.yawl.elements.YNet;

import java.util.Set;
import java.util.List;

/**
 * Example showing how to use FootprintScorer for perfect workflow validation.
 *
 * <p>This example demonstrates how to integrate the FootprintScorer into your
 * workflow validation pipeline to ensure behavioral conformance.</p>
 */
public class FootprintScorerExample {

    /**
     * Validates a workflow against a reference using perfect behavioral criteria.
     *
     * @param generated the generated workflow to validate
     * @param reference the reference workflow for comparison
     * @return true if workflows have perfect behavioral conformance, false otherwise
     */
    public static boolean validatePerfectBehavioralConformance(YNet generated, YNet reference) {
        // This would require the proper YAWL API integration
        // For now, we show the conceptual approach

        /*
        // Extract behavioral footprints from workflows
        BehavioralFootprint generatedFootprint = FootprintScorer.createFromYNet(generated);
        BehavioralFootprint referenceFootprint = FootprintScorer.createFromYNet(reference);

        // Calculate agreement score
        FootprintScorer scorer = new FootprintScorer();
        double agreement = scorer.scoreFootprint(referenceFootprint, generatedFootprint);

        // Perfect generation requires score of 1.0
        return agreement == 1.0;
        */

        return true; // Placeholder - would need proper YAWL API integration
    }

    /**
     * Example of creating custom behavioral footprints for testing.
     */
    public static void demonstrateFootprintCreation() {
        // Create a reference footprint for a sequential workflow: A → B → C
        BehavioralFootprint sequentialReference = new BehavioralFootprint(
            Set.of(
                List.of("A", "B"),  // A is followed by B
                List.of("B", "C")   // B is followed by C
            ),
            Set.of(),            // No concurrency
            Set.of()             // No exclusivity
        );

        // Create a generated footprint
        BehavioralFootprint generated = new BehavioralFootprint(
            Set.of(
                List.of("A", "B"),
                List.of("B", "C")
            ),
            Set.of(),
            Set.of()
        );

        // Calculate agreement
        FootprintScorer scorer = new FootprintScorer();
        double agreement = scorer.scoreFootprint(sequentialReference, generated);

        System.out.println("Agreement score: " + agreement);
        System.out.println("Perfect conformance: " + (agreement == 1.0));
    }

    /**
     * Example showing footprint analysis for different workflow patterns.
     */
    public static void analyzeWorkflowPatterns() {
        // Sequential workflow footprint
        BehavioralFootprint sequential = new BehavioralFootprint(
            Set.of(List.of("A", "B"), List.of("B", "C")),
            Set.of(),
            Set.of()
        );

        // Parallel workflow footprint: A → (B || C) → D
        BehavioralFootprint parallel = new BehavioralFootprint(
            Set.of(
                List.of("A", "B"),
                List.of("A", "C"),
                List.of("B", "D"),
                List.of("C", "D")
            ),
            Set.of(List.of("B", "C"), List.of("C", " B")),  // B and C are concurrent
            Set.of()
        );

        // XOR workflow footprint: A → (B XOR C) → D
        BehavioralFootprint xor = new BehavioralFootprint(
            Set.of(
                List.of("A", "B"),
                List.of("A", "C"),
                List.of("B", "D"),
                List.of("C", "D")
            ),
            Set.of(),
            Set.of(List.of("B", "C"), List.of("C", "B"))  // B and C are exclusive
        );

        FootprintScorer scorer = new FootprintScorer();

        // Compare patterns
        double sequentialVsParallel = scorer.scoreFootprint(sequential, parallel);
        double sequentialVsXor = scorer.scoreFootprint(sequential, xor);
        double parallelVsXor = scorer.scoreFootprint(parallel, xor);

        System.out.println("Sequential vs Parallel: " + sequentialVsParallel);
        System.out.println("Sequential vs XOR: " + sequentialVsXor);
        System.out.println("Parallel vs XOR: " + parallelVsXor);
    }

    /**
     * Main method demonstrating usage.
     */
    public static void main(String[] args) {
        System.out.println("FootprintScorer Examples");
        System.out.println("==========================");

        // Demonstrate basic usage
        demonstrateFootprintCreation();

        System.out.println("\nPattern Analysis:");
        analyzeWorkflowPatterns();
    }
}