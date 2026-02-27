package org.yawlfoundation.yawl.integration.util;

import java.util.Set;

/**
 * Utility class for calculating similarity metrics across conformance checking tools.
 * Provides Jaccard similarity for sets, cosine similarity for vectors,
 * and score interpretation methods with predefined thresholds.
 *
 * <p>All methods are static and thread-safe. Score thresholds:
 * <ul>
 *   <li>EXCELLENT: ≥ 0.9</li>
 *   <li>GOOD: ≥ 0.7</li>
 *   <li>ACCEPTABLE: ≥ 0.5</li>
 *   <li>POOR: ≥ 0.3</li>
 *   <li>CRITICAL: < 0.3</li>
 * </ul>
 *
 * @author YAWL Integration Team
 * @since 6.0.0
 */
public final class SimilarityMetrics {

    /**
     * Threshold for excellent similarity scores (0.9 and above).
     */
    public static final double THRESHOLD_EXCELLENT = 0.9;

    /**
     * Threshold for good similarity scores (0.7 and above).
     */
    public static final double THRESHOLD_GOOD = 0.7;

    /**
     * Threshold for acceptable similarity scores (0.5 and above).
     */
    public static final double THRESHOLD_ACCEPTABLE = 0.5;

    /**
     * Threshold for poor similarity scores (0.3 and above).
     */
    public static final double THRESHOLD_POOR = 0.3;

    // Private constructor to prevent instantiation
    private SimilarityMetrics() {}

    /**
     * Calculate Jaccard similarity between two sets.
     *
     * <p>The Jaccard similarity coefficient is defined as the size of the
     * intersection divided by the size of the union of the two sets.
     * Returns 1.0 if both sets are empty (two empty sets are considered identical).
     *
     * @param <T> the type of elements in the sets
     * @param setA the first set
     * @param setB the second set
     * @return the Jaccard similarity coefficient between 0.0 and 1.0,
     *         or 1.0 if both sets are empty
     * @throws NullPointerException if either set is null
     */
    public static <T> double jaccardSimilarity(Set<T> setA, Set<T> setB) {
        if (setA == null || setB == null) {
            throw new NullPointerException("Sets cannot be null");
        }

        if (setA.isEmpty() && setB.isEmpty()) {
            return 1.0;
        }

        Set<T> intersection = new java.util.HashSet<>(setA);
        intersection.retainAll(setB);

        Set<T> union = new java.util.HashSet<>(setA);
        union.addAll(setB);

        return union.isEmpty() ? 1.0 : (double) intersection.size() / union.size();
    }

    /**
     * Calculate cosine similarity between two vectors.
     *
     * <p>Cosine similarity measures the cosine of the angle between two vectors
     * in a multidimensional space. A value of 1.0 indicates identical vectors,
     * 0.0 indicates orthogonal vectors, and negative values indicate opposite directions.
     *
     * @param vectorA the first vector (must not be null)
     * @param vectorB the second vector (must not be null)
     * @return the cosine similarity between -1.0 and 1.0,
     *         or 0.0 if either vector has zero magnitude
     * @throws IllegalArgumentException if the vectors have different lengths
     * @throws NullPointerException if either vector is null
     */
    public static double cosineSimilarity(double[] vectorA, double[] vectorB) {
        if (vectorA == null || vectorB == null) {
            throw new NullPointerException("Vectors cannot be null");
        }

        if (vectorA.length != vectorB.length) {
            throw new IllegalArgumentException("Vectors must have same length");
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += vectorA[i] * vectorA[i];
            normB += vectorB[i] * vectorB[i];
        }

        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        return denominator == 0 ? 0.0 : dotProduct / denominator;
    }

    /**
     * Interpret a similarity score as a quality label based on predefined thresholds.
     *
     * <p>Score ranges:
     * <ul>
     *   <li>EXCELLENT: score ≥ 0.9</li>
     *   <li>GOOD: 0.7 ≤ score < 0.9</li>
     *   <li>ACCEPTABLE: 0.5 ≤ score < 0.7</li>
     *   <li>POOR: 0.3 ≤ score < 0.5</li>
     *   <li>CRITICAL: score < 0.3</li>
     * </ul>
     *
     * @param score the similarity score to interpret (should be in range 0.0-1.0)
     * @return a quality label string
     * @throws IllegalArgumentException if score is outside 0.0-1.0 range
     */
    public static String interpretScore(double score) {
        if (score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException("Score must be between 0.0 and 1.0");
        }

        if (score >= THRESHOLD_EXCELLENT) return "EXCELLENT";
        if (score >= THRESHOLD_GOOD) return "GOOD";
        if (score >= THRESHOLD_ACCEPTABLE) return "ACCEPTABLE";
        if (score >= THRESHOLD_POOR) return "POOR";
        return "CRITICAL";
    }

    /**
     * Check if a similarity score meets or exceeds the specified threshold.
     *
     * @param score the similarity score to check
     * @param threshold the minimum threshold required
     * @return true if score meets or exceeds threshold, false otherwise
     * @throws IllegalArgumentException if either score or threshold is outside 0.0-1.0 range
     */
    public static boolean meetsThreshold(double score, double threshold) {
        if (score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException("Score must be between 0.0 and 1.0");
        }
        if (threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("Threshold must be between 0.0 and 1.0");
        }
        return score >= threshold;
    }

    /**
     * Get detailed interpretation of a conformance score.
     *
     * @param score the conformance score to interpret
     * @return detailed explanation of what the score means
     * @throws IllegalArgumentException if score is outside 0.0-1.0 range
     */
    public static String getDetailedInterpretation(double score) {
        if (score < 0.0 || score > 1.0) {
            throw new IllegalArgumentException("Score must be between 0.0 and 1.0");
        }

        if (score >= THRESHOLD_EXCELLENT) {
            return "EXCELLENT conformance — the candidate model closely matches the reference model's " +
                 "behavioral structure across sequence, concurrency, and exclusive choice.";
        } else if (score >= THRESHOLD_GOOD) {
            return "GOOD conformance — the candidate model largely matches the reference model's " +
                 "behavioral structure, with minor deviations.";
        } else if (score >= THRESHOLD_ACCEPTABLE) {
            return "ACCEPTABLE conformance — the candidate model has moderate structural differences " +
                 "from the reference model but maintains reasonable behavioral consistency.";
        } else if (score >= THRESHOLD_POOR) {
            return "POOR conformance — the candidate model has significant behavioral divergence " +
                 "and does not closely match the reference model's control-flow structure.";
        } else {
            return "CRITICAL conformance — the candidate model's behavioral structure differs " +
                 "substantially from the reference model, indicating poor conformance.";
        }
    }
}