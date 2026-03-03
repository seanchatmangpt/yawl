package org.yawlfoundation.yawl.integration.selfplay;

import java.util.Objects;

/**
 * Analysis of fitness score trends over time.
 *
 * <p>Provides statistical analysis of how fitness scores are changing:
 * <ul>
 *   <li>Linear regression slope indicating direction and rate of change</li>
 *   <li>Trend direction (improving, stable, declining)</li>
 *   <li>Correlation coefficient indicating trend strength</li>
 * </ul>
 */
public final class TrendAnalysis {

    private final double slope;
    private final ConvergenceTracker.TrendDirection direction;
    private final double correlation;

    /**
     * Creates a trend analysis result.
     *
     * @param slope the linear regression slope of fitness scores over time
     * @param direction the overall trend direction
     * @param correlation the correlation coefficient (-1 to 1) indicating trend strength
     */
    public TrendAnalysis(double slope, ConvergenceTracker.TrendDirection direction, double correlation) {
        this.slope = slope;
        this.direction = Objects.requireNonNull(direction, "Trend direction is required");
        this.correlation = correlation;
    }

    /**
     * Returns the linear regression slope of fitness scores over time.
     *
     * <p>A positive slope indicates improvement, negative indicates decline.
     * The magnitude indicates the rate of change.
     */
    public double getSlope() {
        return slope;
    }

    /**
     * Returns the linear regression slope (deprecated, use getSlope() instead).
     */
    public double slope() {
        return slope;
    }

    /**
     * Returns the overall trend direction.
     */
    public ConvergenceTracker.TrendDirection getDirection() {
        return direction;
    }

    /**
     * Returns the overall trend direction (deprecated, use getDirection() instead).
     */
    public ConvergenceTracker.TrendDirection direction() {
        return direction;
    }

    /**
     * Returns the correlation coefficient indicating trend strength.
     *
     * <p>Values range from -1.0 (perfect negative correlation) to 1.0 (perfect positive correlation).
     * Values near 0 indicate little to no trend.
     */
    public double getCorrelation() {
        return correlation;
    }

    /**
     * Returns the correlation coefficient (deprecated, use getCorrelation() instead).
     */
    public double correlation() {
        return correlation;
    }

    /**
     * Returns whether the trend is strongly positive.
     */
    public boolean isStrongImprovement() {
        return direction == ConvergenceTracker.TrendDirection.IMPROVING && correlation > 0.5;
    }

    /**
     * Returns whether the trend is strongly negative.
     */
    public boolean isStrongDecline() {
        return direction == ConvergenceTracker.TrendDirection.DECLINING && correlation < -0.5;
    }

    /**
     * Returns whether the trend is stable (little to no change).
     */
    public boolean isStable() {
        return direction == ConvergenceTracker.TrendDirection.STABLE || Math.abs(slope) < 0.001;
    }

    /**
     * Returns whether the correlation is significant (|correlation| > 0.3).
     */
    public boolean hasSignificantCorrelation() {
        return Math.abs(correlation) > 0.3;
    }

    /**
     * Returns a human-readable interpretation of the trend.
     */
    public String getInterpretation() {
        StringBuilder sb = new StringBuilder();
        sb.append("Trend is ").append(direction).append(" (slope: ").append(String.format("%.6f", slope));

        if (hasSignificantCorrelation()) {
            sb.append(", correlation: ").append(String.format("%.3f", correlation));
            if (correlation > 0.7) {
                sb.append(" - strong positive trend");
            } else if (correlation < -0.7) {
                sb.append(" - strong negative trend");
            } else if (correlation > 0.3) {
                sb.append(" - moderate positive trend");
            } else if (correlation < -0.3) {
                sb.append(" - moderate negative trend");
            }
        } else {
            sb.append(", correlation: ").append(String.format("%.3f", correlation));
            sb.append(" - no significant trend");
        }

        sb.append(")");
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TrendAnalysis that = (TrendAnalysis) o;
        return Double.compare(that.slope, slope) == 0 &&
               Double.compare(that.correlation, correlation) == 0 &&
               direction == that.direction;
    }

    @Override
    public int hashCode() {
        return Objects.hash(slope, direction, correlation);
    }

    @Override
    public String toString() {
        return "TrendAnalysis{" +
               "slope=" + String.format("%.6f", slope) +
               ", direction=" + direction +
               ", correlation=" + String.format("%.3f", correlation) +
               '}';
    }
}