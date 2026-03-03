package org.yawlfoundation.yawl.integration.selfplay;

import org.yawlfoundation.yawl.integration.selfplay.model.FitnessScore;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Result of convergence analysis containing detected patterns and recommendations.
 *
 * <p>Provides a comprehensive analysis of self-play convergence including:
 * <ul>
 *   <li>Overall convergence status</li>
 *   <li>Detected alerts with severity levels</li>
 *   <li>Trend analysis with correlation metrics</li>
 *   <li>Plateau detection with statistical analysis</li>
 *   <li>Recommendations for improvement</li>
 * </ul>
 */
public final class ConvergenceAnalysis {

    private final ConvergenceTracker.ConvergenceStatus status;
    private final List<ConvergenceAlert> alerts;
    private final TrendAnalysis trend;
    private final PlateauDetection plateau;
    private final int totalSimulations;
    private final double currentFitness;
    private final double averageImprovementRate;
    private final Instant generatedAt;

    private ConvergenceAnalysis(Builder builder) {
        this.status = builder.status;
        this.alerts = Collections.unmodifiableList(builder.alerts);
        this.trend = builder.trend;
        this.plateau = builder.plateau;
        this.totalSimulations = builder.totalSimulations;
        this.currentFitness = builder.currentFitness;
        this.averageImprovementRate = builder.averageImprovementRate;
        this.generatedAt = builder.generatedAt;
    }

    /**
     * Creates an analysis result when there's insufficient history.
     */
    public static ConvergenceAnalysis noHistory() {
        return new ConvergenceAnalysis.Builder()
            .status(ConvergenceTracker.ConvergenceStatus.INSUFFICIENT_DATA)
            .alerts(List.of())
            .trend(new TrendAnalysis(0.0, ConvergenceTracker.TrendDirection.STABLE, 0.0))
            .plateau(new PlateauDetection(false, 0.0, 0))
            .totalSimulations(0)
            .currentFitness(0.0)
            .averageImprovementRate(0.0)
            .generatedAt(Instant.now())
            .build();
    }

    /**
     * Returns the overall convergence status.
     */
    public ConvergenceTracker.ConvergenceStatus getStatus() {
        return status;
    }

    /**
     * Returns the list of detected alerts.
     */
    public List<ConvergenceAlert> getAlerts() {
        return alerts;
    }

    /**
     * Returns the trend analysis.
     */
    public TrendAnalysis getTrend() {
        return trend;
    }

    /**
     * Returns the plateau detection results.
     */
    public PlateauDetection getPlateau() {
        return plateau;
    }

    /**
     * Returns the total number of simulations recorded.
     */
    public int getTotalSimulations() {
        return totalSimulations;
    }

    /**
     * Returns the current fitness score.
     */
    public double getCurrentFitness() {
        return currentFitness;
    }

    /**
     * Returns the average improvement rate between simulations.
     */
    public double getAverageImprovementRate() {
        return averageImprovementRate;
    }

    /**
     * Returns when this analysis was generated.
     */
    public Instant getGeneratedAt() {
        return generatedAt;
    }

    /**
     * Returns whether this analysis indicates any problems.
     */
    public boolean hasProblems() {
        return !alerts.isEmpty();
    }

    /**
     * Returns whether convergence has been achieved.
     */
    public boolean isConverged() {
        return status == ConvergenceTracker.ConvergenceStatus.CONVERGED;
    }

    /**
     * Returns a human-readable summary of the analysis.
     */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Convergence Analysis ===\n");
        sb.append("Generated: ").append(generatedAt).append("\n");
        sb.append("Status: ").append(status).append("\n");
        sb.append("Current Fitness: ").append(String.format("%.4f", currentFitness)).append("\n");
        sb.append("Total Simulations: ").append(totalSimulations).append("\n");
        sb.append("Improvement Rate: ").append(String.format("%.4f", averageImprovementRate)).append("\n");

        if (trend != null) {
            sb.append("\nTrend Analysis:\n");
            sb.append("  Direction: ").append(trend.direction()).append("\n");
            sb.append("  Slope: ").append(String.format("%.6f", trend.slope())).append("\n");
            sb.append("  Correlation: ").append(String.format("%.4f", trend.correlation())).append("\n");
        }

        if (plateau != null) {
            sb.append("\nPlateau Detection:\n");
            sb.append("  Detected: ").append(plateau.detected()).append("\n");
            if (plateau.detected()) {
                sb.append("  Range: ").append(String.format("%.6f", plateau.range())).append("\n");
                sb.append("  Sample Size: ").append(plateau.sampleSize()).append("\n");
            }
        }

        if (!alerts.isEmpty()) {
            sb.append("\nAlerts (").append(alerts.size()).append("):\n");
            for (ConvergenceAlert alert : alerts) {
                sb.append("  [").append(alert.severity()).append("] ")
                  .append(alert.type()).append(": ")
                  .append(alert.message()).append("\n");
            }
        }

        sb.append("\nRecommendations:\n");
        sb.append(getRecommendations());

        return sb.toString();
    }

    /**
     * Gets actionable recommendations based on the analysis.
     */
    public String getRecommendations() {
        StringBuilder recommendations = new StringBuilder();

        if (status == ConvergenceTracker.ConvergenceStatus.INSUFFICIENT_DATA) {
            recommendations.append("- Run more simulations to gather sufficient data for analysis\n");
        } else if (status == ConvergenceTracker.ConvergenceStatus.CONVERGED) {
            recommendations.append("- Convergence achieved! Consider running validation tests\n");
        } else if (status == ConvergenceTracker.ConvergenceStatus.DIVERGING) {
            recommendations.append("- Check for configuration issues or data corruption\n");
            recommendations.append("- Consider adjusting fitness thresholds or proposal quality\n");
        } else if (status == ConvergenceTracker.ConvergenceStatus.PLATEAUED) {
            recommendations.append("- Try different proposal services or parameters\n");
            recommendations.append("- Consider increasing diversity in agent recruitment\n");
            recommendations.append("- Review fitness evaluation criteria\n");
        } else if (status == ConvergenceTracker.ConvergenceStatus.APPROACHING_CONVERGENCE) {
            recommendations.append("- Continue current strategy, convergence is near\n");
        }

        for (ConvergenceAlert alert : alerts) {
            switch (alert.type()) {
                case DIVERGENCE:
                    recommendations.append("- Investigate data sources and validate fitness calculations\n");
                    break;
                case PLATEAU:
                    recommendations.append("- Introduce randomness or new agents to break plateau\n");
                    break;
                case SLOW_PROGRESS:
                    recommendations.append("- Optimize proposal generation process\n");
                    recommendations.append("- Consider increasing fitness threshold gradually\n");
                    break;
                case OSCILLATION:
                    recommendations.append("- Stabilize the evaluation process\n");
                    recommendations.append("- Consider smoothing fitness calculations\n");
                    break;
            }
        }

        if (Math.abs(averageImprovementRate) < 0.01) {
            recommendations.append("- Current improvement rate is very low (<1%)\n");
            recommendations.append("- Consider reviewing the fitness evaluation criteria\n");
        }

        return recommendations.toString();
    }

    /**
     * Builder for creating ConvergenceAnalysis instances.
     */
    public static final class Builder {
        private ConvergenceTracker.ConvergenceStatus status;
        private List<ConvergenceAlert> alerts = List.of();
        private TrendAnalysis trend;
        private PlateauDetection plateau;
        private int totalSimulations;
        private double currentFitness;
        private double averageImprovementRate;
        private Instant generatedAt = Instant.now();

        public Builder status(ConvergenceTracker.ConvergenceStatus val) {
            status = val;
            return this;
        }

        public Builder alerts(List<ConvergenceAlert> val) {
            alerts = val;
            return this;
        }

        public Builder trend(TrendAnalysis val) {
            trend = val;
            return this;
        }

        public Builder plateau(PlateauDetection val) {
            plateau = val;
            return this;
        }

        public Builder totalSimulations(int val) {
            totalSimulations = val;
            return this;
        }

        public Builder currentFitness(double val) {
            currentFitness = val;
            return this;
        }

        public Builder averageImprovementRate(double val) {
            averageImprovementRate = val;
            return this;
        }

        public Builder generatedAt(Instant val) {
            generatedAt = val;
            return this;
        }

        public ConvergenceAnalysis build() {
            Objects.requireNonNull(status, "Status is required");
            Objects.requireNonNull(alerts, "Alerts are required");
            Objects.requireNonNull(trend, "Trend is required");
            Objects.requireNonNull(plateau, "Plateau is required");

            return new ConvergenceAnalysis(this);
        }
    }
}