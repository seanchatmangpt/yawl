/*
 * Copyright (c) 2026 YAWL Foundation
 *
 * This file is part of YAWL (Yet Another Workflow Language).
 *
 * YAWL is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * YAWL is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.HashSet;

/**
 * Unit tests for SimilarityMetrics utility class.
 */
class SimilarityMetricsTest {

    @Test
    void jaccardSimilarity_overlappingSets() {
        Set<String> set1 = new HashSet<>(Set.of("a", "b", "c", "d"));
        Set<String> set2 = new HashSet<>(Set.of("b", "c", "e", "f"));

        // Intersection: {b, c} = 2
        // Union: {a, b, c, d, e, f} = 6
        // Jaccard = 2/6 = 0.333...
        assertEquals(0.3333333333333333, SimilarityMetrics.jaccardSimilarity(set1, set2));
    }

    @Test
    void jaccardSimilarity_identicalSets() {
        Set<String> set1 = new HashSet<>(Set.of("a", "b", "c"));
        Set<String> set2 = new HashSet<>(Set.of("a", "b", "c"));

        // Intersection: {a, b, c} = 3
        // Union: {a, b, c} = 3
        // Jaccard = 3/3 = 1.0
        assertEquals(1.0, SimilarityMetrics.jaccardSimilarity(set1, set2));
    }

    @Test
    void jaccardSimilarity_disjointSets() {
        Set<String> set1 = new HashSet<>(Set.of("a", "b", "c"));
        Set<String> set2 = new HashSet<>(Set.of("d", "e", "f"));

        // Intersection: {} = 0
        // Union: {a, b, c, d, e, f} = 6
        // Jaccard = 0/6 = 0.0
        assertEquals(0.0, SimilarityMetrics.jaccardSimilarity(set1, set2));
    }

    @Test
    void jaccardSimilarity_oneEmptySet() {
        Set<String> set1 = new HashSet<>();
        Set<String> set2 = new HashSet<>(Set.of("a", "b", "c"));

        // Jaccard = 0.0 when one set is empty
        assertEquals(0.0, SimilarityMetrics.jaccardSimilarity(set1, set2));
    }

    @Test
    void jaccardSimilarity_bothEmptySets() {
        Set<String> set1 = new HashSet<>();
        Set<String> set2 = new HashSet<>();

        // Jaccard = 1.0 when both sets are empty (by convention)
        assertEquals(1.0, SimilarityMetrics.jaccardSimilarity(set1, set2));
    }

    @Test
    void jaccardSimilarity_nullSet1_throwsNullPointerException() {
        Set<String> set2 = new HashSet<>(Set.of("a", "b"));
        assertThrows(NullPointerException.class,
            () -> SimilarityMetrics.jaccardSimilarity(null, set2));
    }

    @Test
    void jaccardSimilarity_nullSet2_throwsNullPointerException() {
        Set<String> set1 = new HashSet<>(Set.of("a", "b"));
        assertThrows(NullPointerException.class,
            () -> SimilarityMetrics.jaccardSimilarity(set1, null));
    }

    @Test
    void cosineSimilarity_identicalVectors() {
        double[] vec1 = {1.0, 2.0, 3.0};
        double[] vec2 = {1.0, 2.0, 3.0};

        // Cosine similarity = 1.0 for identical vectors
        assertEquals(1.0, SimilarityMetrics.cosineSimilarity(vec1, vec2), 1e-10);
    }

    @Test
    void cosineSimilarity_oppositeVectors() {
        double[] vec1 = {1.0, 2.0, 3.0};
        double[] vec2 = {-1.0, -2.0, -3.0};

        // Cosine similarity = -1.0 for opposite vectors
        assertEquals(-1.0, SimilarityMetrics.cosineSimilarity(vec1, vec2), 1e-10);
    }

    @Test
    void cosineSimilarity_orthogonalVectors() {
        double[] vec1 = {1.0, 0.0, 0.0};
        double[] vec2 = {0.0, 1.0, 0.0};

        // Cosine similarity = 0.0 for orthogonal vectors
        assertEquals(0.0, SimilarityMetrics.cosineSimilarity(vec1, vec2), 1e-10);
    }

    @Test
    void cosineSimilarity_zeroVectors() {
        double[] vec1 = {0.0, 0.0, 0.0};
        double[] vec2 = {1.0, 2.0, 3.0};

        // Cosine similarity = 0 when one vector is zero
        assertEquals(0.0, SimilarityMetrics.cosineSimilarity(vec1, vec2), 1e-10);
    }

    @Test
    void cosineSimilarity_nullVector1_throwsNullPointerException() {
        double[] vec2 = {1.0, 2.0};
        assertThrows(NullPointerException.class,
            () -> SimilarityMetrics.cosineSimilarity(null, vec2));
    }

    @Test
    void cosineSimilarity_nullVector2_throwsNullPointerException() {
        double[] vec1 = {1.0, 2.0};
        assertThrows(NullPointerException.class,
            () -> SimilarityMetrics.cosineSimilarity(vec1, null));
    }

    @Test
    void cosineSimilarity_differentLengths_throwsIllegalArgumentException() {
        double[] vec1 = {1.0, 2.0};
        double[] vec2 = {1.0, 2.0, 3.0};
        assertThrows(IllegalArgumentException.class,
            () -> SimilarityMetrics.cosineSimilarity(vec1, vec2));
    }

    @Test
    void interpretScore_highThreshold() {
        assertEquals("Excellent", SimilarityMetrics.interpretScore(0.95, 0.9));
        assertEquals("Good", SimilarityMetrics.interpretScore(0.85, 0.9));
        assertEquals("Poor", SimilarityMetrics.interpretScore(0.7, 0.9));
    }

    @Test
    void interpretScore_mediumThreshold() {
        assertEquals("Excellent", SimilarityMetrics.interpretScore(0.85, 0.8));
        assertEquals("Good", SimilarityMetrics.interpretScore(0.75, 0.8));
        assertEquals("Poor", SimilarityMetrics.interpretScore(0.6, 0.8));
    }

    @Test
    void interpretScore_lowThreshold() {
        assertEquals("Excellent", SimilarityMetrics.interpretScore(0.75, 0.7));
        assertEquals("Good", SimilarityMetrics.interpretScore(0.65, 0.7));
        assertEquals("Poor", SimilarityMetrics.interpretScore(0.5, 0.7));
    }

    @Test
    void interpretScore_edgeCases() {
        // Equal to threshold should be "Good"
        assertEquals("Good", SimilarityMetrics.interpretScore(0.8, 0.8));

        // Just above threshold
        assertEquals("Excellent", SimilarityMetrics.interpretScore(0.81, 0.8));

        // Just below threshold
        assertEquals("Good", SimilarityMetrics.interpretScore(0.79, 0.8));
    }

    @Test
    void interpretScore_thresholdZero() {
        // All scores should be "Excellent" when threshold is 0
        assertEquals("Excellent", SimilarityMetrics.interpretScore(0.0, 0.0));
        assertEquals("Excellent", SimilarityMetrics.interpretScore(0.1, 0.0));
        assertEquals("Excellent", SimilarityMetrics.interpretScore(1.0, 0.0));
    }

    @Test
    void interpretScore_scoreZero() {
        // Any score below threshold should be "Poor"
        assertEquals("Poor", SimilarityMetrics.interpretScore(0.0, 0.1));
        assertEquals("Poor", SimilarityMetrics.interpretScore(0.0, 0.5));
        assertEquals("Poor", SimilarityMetrics.interpretScore(0.0, 0.9));
    }

    @Test
    void interpretScore_scoreOne() {
        // Score 1.0 should be "Excellent" above threshold
        assertEquals("Excellent", SimilarityMetrics.interpretScore(1.0, 0.5));
    }

    @Test
    void interpretScore_negativeThreshold() {
        // Negative threshold should be treated as 0
        assertEquals("Excellent", SimilarityMetrics.interpretScore(0.5, -0.1));
    }

    @Test
    void interpretScore_aboveOne() {
        // Score > 1 should be capped at 1.0 for comparison
        assertEquals("Excellent", SimilarityMetrics.interpretScore(1.5, 0.8));
    }
}