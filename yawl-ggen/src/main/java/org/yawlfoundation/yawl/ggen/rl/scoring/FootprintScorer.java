/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.rl.scoring;

import org.yawlfoundation.yawl.ggen.powl.PowlModel;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Scores POWL models based on footprint conformance.
 * Compares the candidate model's footprint against a reference footprint using Jaccard similarity.
 * Aggregates scores across three dimensions: direct succession, concurrency, and exclusivity.
 */
public class FootprintScorer implements RewardFunction {

    private final FootprintMatrix reference;
    private final FootprintExtractor extractor;

    /**
     * Constructs a FootprintScorer with a reference footprint.
     *
     * @param reference the reference FootprintMatrix to compare against (must not be null)
     * @throws IllegalArgumentException if reference is null
     */
    public FootprintScorer(FootprintMatrix reference) {
        this.reference = Objects.requireNonNull(reference, "reference must not be null");
        this.extractor = new FootprintExtractor();
    }

    /**
     * Scores a candidate POWL model by extracting its footprint and comparing it
     * against the reference footprint using macro-averaged Jaccard similarity.
     *
     * @param candidate           the POWL model to score (must not be null)
     * @param processDescription  (unused for footprint-based scoring)
     * @return a score in [0.0, 1.0]; 1.0 indicates perfect conformance
     * @throws IllegalArgumentException if candidate is null
     */
    @Override
    public double score(PowlModel candidate, String processDescription) {
        Objects.requireNonNull(candidate, "candidate must not be null");

        FootprintMatrix candidateFp = extractor.extract(candidate);

        double dsSimilarity = jaccardSimilarity(
            candidateFp.directSuccession(), reference.directSuccession()
        );
        double concSimilarity = jaccardSimilarity(
            candidateFp.concurrency(), reference.concurrency()
        );
        double exclSimilarity = jaccardSimilarity(
            candidateFp.exclusive(), reference.exclusive()
        );

        // Macro-average (equal weight to each dimension)
        return (dsSimilarity + concSimilarity + exclSimilarity) / 3.0;
    }

    /**
     * Computes Jaccard similarity between two sets of relationships.
     * Jaccard(A, B) = |A ∩ B| / |A ∪ B|
     * If both sets are empty, returns 1.0 (perfect match).
     *
     * @param candidate the candidate set
     * @param reference the reference set
     * @return Jaccard similarity in [0.0, 1.0]
     */
    private double jaccardSimilarity(Set<List<String>> candidate, Set<List<String>> reference) {
        if (candidate.isEmpty() && reference.isEmpty()) {
            return 1.0;
        }

        Set<List<String>> intersection = new HashSet<>(candidate);
        intersection.retainAll(reference);

        Set<List<String>> union = new HashSet<>(candidate);
        union.addAll(reference);

        if (union.isEmpty()) {
            return 0.0;
        }

        return (double) intersection.size() / union.size();
    }
}
