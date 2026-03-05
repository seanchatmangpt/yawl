package org.yawlfoundation.yawl.integration.selfplay.model;

/**
 * Weighted fitness score for the YAWL v7 design state.
 *
 * <p>Four axes, each in [0.0, 1.0], combined with fixed weights:
 * <ul>
 *   <li>{@code completeness} (35%) — fraction of 7 known v7 gaps with at least one accepted proposal</li>
 *   <li>{@code consistency} (25%) — absence of inter-proposal contradictions; 1.0 = no contradictions</li>
 *   <li>{@code compatibility} (25%) — mean backward-compat score of all accepted proposals</li>
 *   <li>{@code performance} (15%) — mean estimated performance gain of all accepted proposals</li>
 * </ul>
 *
 * <p>Convergence threshold: {@link #total()} ≥ 0.85.
 *
 * @param completeness fraction of known gaps with an accepted proposal (0.0–1.0)
 * @param consistency absence of contradictions between accepted proposals (0.0–1.0)
 * @param compatibility mean backward-compat score of accepted proposals (0.0–1.0)
 * @param performance mean estimated performance gain of accepted proposals (0.0–1.0)
 */
public record FitnessScore(
    double completeness,
    double consistency,
    double compatibility,
    double performance
) {

    private static final double W_COMPLETENESS = 0.35;
    private static final double W_CONSISTENCY  = 0.25;
    private static final double W_COMPAT       = 0.25;
    private static final double W_PERFORMANCE  = 0.15;

    public FitnessScore {
        assertInRange("completeness", completeness);
        assertInRange("consistency", consistency);
        assertInRange("compatibility", compatibility);
        assertInRange("performance", performance);
    }

    private static void assertInRange(String name, double value) {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be in [0.0, 1.0], got: " + value);
        }
    }

    /** Weighted total fitness score. */
    public double total() {
        return W_COMPLETENESS * completeness
            + W_CONSISTENCY  * consistency
            + W_COMPAT       * compatibility
            + W_PERFORMANCE  * performance;
    }

    /** Returns true if total has reached or exceeded the convergence threshold. */
    public boolean hasConverged(double threshold) {
        return total() >= threshold;
    }

    /** Zero fitness (initial state). */
    public static FitnessScore zero() {
        return new FitnessScore(0.0, 0.0, 0.0, 0.0);
    }

    @Override
    public String toString() {
        return String.format(
            "FitnessScore{total=%.3f, completeness=%.3f, consistency=%.3f, compat=%.3f, perf=%.3f}",
            total(), completeness, consistency, compatibility, performance
        );
    }
}
