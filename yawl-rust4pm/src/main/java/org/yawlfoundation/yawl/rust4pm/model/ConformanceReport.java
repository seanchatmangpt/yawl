package org.yawlfoundation.yawl.rust4pm.model;

/**
 * Token-based replay conformance checking result.
 *
 * @param fitness    fraction of observed behaviour explained by the model ∈ [0.0, 1.0]
 * @param precision  fraction of model behaviour matching observed ∈ [0.0, 1.0]
 * @param eventCount number of events in the checked log
 * @param model      the process model used (null if not materialized)
 */
public record ConformanceReport(double fitness, double precision, int eventCount, ProcessModel model) {

    /** Harmonic mean of fitness and precision. Returns 0.0 if both are zero. */
    public double f1Score() {
        if (fitness + precision == 0.0) return 0.0;
        return 2.0 * (fitness * precision) / (fitness + precision);
    }

    /** True only when fitness == 1.0 and precision == 1.0. */
    public boolean isPerfectFit() {
        return fitness == 1.0 && precision == 1.0;
    }
}
