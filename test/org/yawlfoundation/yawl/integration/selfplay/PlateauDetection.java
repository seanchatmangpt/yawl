package org.yawlfoundation.yawl.integration.selfplay;

/**
 * Results of plateau detection analysis.
 *
 * <p>Provides information about whether fitness scores have plateaued:
 * <ul>
 *   <li>Whether a plateau was detected</li>
 *   <li>The range of fitness scores in the plateau window</li>
 *   <li>The number of samples analyzed</li>
 * </ul>
 */
public final class PlateauDetection {

    private final boolean detected;
    private final double range;
    private final int sampleSize;

    /**
     * Creates a plateau detection result.
     *
     * @param detected whether a plateau was detected
     * @param range the range (max - min) of fitness scores in the analysis window
     * @param sampleSize the number of samples analyzed for plateau detection
     */
    public PlateauDetection(boolean detected, double range, int sampleSize) {
        this.detected = detected;
        this.range = range;
        this.sampleSize = sampleSize;
    }

    /**
     * Returns whether a plateau was detected.
     */
    public boolean isDetected() {
        return detected;
    }

    /**
     * Returns whether a plateau was detected (deprecated, use isDetected() instead).
     */
    public boolean detected() {
        return detected;
    }

    /**
     * Returns the range of fitness scores in the analysis window.
     *
     * <p>This is calculated as max fitness - min fitness in the recent samples.
     * Smaller values indicate more stability (plateau behavior).
     */
    public double getRange() {
        return range;
    }

    /**
     * Returns the range of fitness scores (deprecated, use getRange() instead).
     */
    public double range() {
        return range;
    }

    /**
     * Returns the number of samples analyzed for plateau detection.
     */
    public int getSampleSize() {
        return sampleSize;
    }

    /**
     * Returns the number of samples analyzed (deprecated, use getSampleSize() instead).
     */
    public int sampleSize() {
        return sampleSize;
    }

    /**
     * Returns whether the plateau is very stable (range < 0.0001).
     */
    public boolean isVeryStable() {
        return detected && range < 0.0001;
    }

    /**
     * Returns whether the plateau is slightly stable (range < 0.01).
     */
    public boolean isSlightlyStable() {
        return detected && range < 0.01;
    }

    /**
     * Returns whether we have enough samples for reliable detection.
     */
    public boolean hasSufficientSamples() {
        return sampleSize >= 5;
    }

    /**
     * Returns a human-readable interpretation of the plateau detection.
     */
    public String getInterpretation() {
        if (!detected) {
            return "No plateau detected - fitness scores are still changing";
        }

        if (!hasSufficientSamples()) {
            return "Plateau detected but with insufficient samples for confirmation";
        }

        if (isVeryStable()) {
            return "Very stable plateau detected - fitness scores are nearly constant";
        } else if (isSlightlyStable()) {
            return "Stable plateau detected - fitness scores have minimal variation";
        } else {
            return "Plateau detected - fitness scores have low variation (" +
                   String.format("%.6f", range) + " range)";
        }
    }

    /**
     * Returns the plateau level based on the range.
     */
    public PlateauLevel getPlateauLevel() {
        if (!detected) {
            return PlateauLevel.NONE;
        }

        if (range < 0.0001) {
            return PlateauLevel.VERY_STABLE;
        } else if (range < 0.001) {
            return PlateauLevel.STABLE;
        } else if (range < 0.01) {
            return PlateauLevel.SLIGHTLY_STABLE;
        } else {
            return PlateauLevel.MARGINALLY_STABLE;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlateauDetection that = (PlateauDetection) o;
        return detected == that.detected &&
               Double.compare(that.range, range) == 0 &&
               sampleSize == that.sampleSize;
    }

    @Override
    public int hashCode() {
        return Objects.hash(detected, range, sampleSize);
    }

    @Override
    public String toString() {
        return "PlateauDetection{" +
               "detected=" + detected +
               ", range=" + String.format("%.6f", range) +
               ", sampleSize=" + sampleSize +
               '}';
    }

    /**
     * Levels of plateau stability.
     */
    public enum PlateauLevel {
        NONE,
        MARGINALLY_STABLE,
        SLIGHTLY_STABLE,
        STABLE,
        VERY_STABLE
    }
}