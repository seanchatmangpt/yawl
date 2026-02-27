package org.yawlfoundation.yawl.integration.util;

import java.util.Set;

/**
 * Utility class for calculating similarity metrics.
 */
public final class SimilarityMetrics {

    private SimilarityMetrics() {}

    public static double jaccardSimilarity(Set<?> set1, Set<?> set2) {
        if (set1 == null || set2 == null) {
            throw new NullPointerException("Sets cannot be null");
        }

        if (set1.isEmpty() && set2.isEmpty()) {
            return 1.0;
        }

        Set<?> intersection = new java.util.HashSet<>(set1);
        intersection.retainAll(set2);

        Set<?> union = new java.util.HashSet<>(set1);
        union.addAll(set2);

        return (double) intersection.size() / union.size();
    }

    public static double cosineSimilarity(double[] vec1, double[] vec2) {
        if (vec1 == null || vec2 == null) {
            throw new NullPointerException("Vectors cannot be null");
        }

        if (vec1.length != vec2.length) {
            throw new IllegalArgumentException("Vectors must be of the same length");
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
            norm1 += vec1[i] * vec1[i];
            norm2 += vec2[i] * vec2[i];
        }

        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    public static String interpretScore(double score, double threshold) {
        if (score >= threshold + 0.05) {
            return "Excellent";
        } else if (score >= threshold) {
            return "Good";
        } else {
            return "Poor";
        }
    }
}