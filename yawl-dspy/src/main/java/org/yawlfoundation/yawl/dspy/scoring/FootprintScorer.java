/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.dspy.scoring;

// No YAWL imports needed for core scoring functionality

import java.util.*;
import java.util.stream.Collectors;
import java.util.Objects;

/**
 * Scores behavioral footprint agreement between reference and generated workflows.
 * Calculates behavioral conformance scores (0.0 to 1.0) based on control-flow relationships.
 *
 * <h2>Behavioral Footprint Dimensions</h2>
 * <ul>
 *   <li><b>Direct Succession</b>: Task A → Task B (immediate execution order)</li>
 *   <li><b>Concurrency</b>: Task A || Task B (can execute in parallel)</li>
 *   <li><b>Exclusivity</b>: Task A # Task B (mutually exclusive execution)</li>
 * </ul>
 *
 * <h2>Scoring Methodology</h2>
 * <p>Uses Jaccard similarity for each footprint dimension:
 * Jaccard(A, B) = |A ∩ B| / |A ∪ B|
 * Macro-averages scores across all three dimensions.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create footprint scorer with reference workflow
 * FootprintScorer scorer = new FootprintScorer(referenceFootprint);
 *
 * // Calculate agreement score
 * double agreement = scorer.scoreFootprint(generatedFootprint, referenceFootprint);
 *
 * // Perfect generation: agreement == 1.0
 * if (agreement == 1.0) {
 *     System.out.println("Perfect behavioral conformance!");
 * }
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class FootprintScorer {

    /**
     * Calculates behavioral footprint agreement score between reference and generated footprints.
     *
     * @param reference the reference behavioral footprint (must not be null)
     * @param generated the generated behavioral footprint (must not be null)
     * @return agreement score in [0.0, 1.0]; 1.0 indicates perfect behavioral conformance
     * @throws IllegalArgumentException if either footprint is null
     */
    public double scoreFootprint(BehavioralFootprint reference, BehavioralFootprint generated) {
        Objects.requireNonNull(reference, "Reference footprint must not be null");
        Objects.requireNonNull(generated, "Generated footprint must not be null");

        // Calculate Jaccard similarity for each footprint dimension
        double dsSimilarity = jaccardSimilarity(
            reference.directSuccession(),
            generated.directSuccession()
        );
        double concSimilarity = jaccardSimilarity(
            reference.concurrency(),
            generated.concurrency()
        );
        double exclSimilarity = jaccardSimilarity(
            reference.exclusivity(),
            generated.exclusivity()
        );

        // Macro-average (equal weight to each dimension)
        return (dsSimilarity + concSimilarity + exclSimilarity) / 3.0;
    }

    /**
     * Computes Jaccard similarity between two sets of relationships.
     * Jaccard(A, B) = |A ∩ B| / |A ∪ B|
     * If both sets are empty, returns 1.0 (perfect match).
     *
     * @param setA the first set
     * @param setB the second set
     * @return Jaccard similarity in [0.0, 1.0]
     */
    private <T> double jaccardSimilarity(Set<T> setA, Set<T> setB) {
        if (setA.isEmpty() && setB.isEmpty()) {
            return 1.0; // Both empty is a perfect match
        }

        Set<T> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);

        Set<T> union = new HashSet<>(setA);
        union.addAll(setB);

        if (union.isEmpty()) {
            return 0.0;
        }

        return (double) intersection.size() / union.size();
    }

  }