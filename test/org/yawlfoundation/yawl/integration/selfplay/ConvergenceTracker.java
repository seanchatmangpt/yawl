package org.yawlfoundation.yawl.integration.selfplay;

import org.yawlfoundation.yawl.integration.selfplay.model.FitnessScore;
import org.yawlfoundation.yawl.integration.selfplay.model.V7SimulationReport;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Tracks convergence metrics over multiple self-play simulation runs.
 *
 * <p>Provides trend analysis and convergence detection by maintaining a historical
 * record of fitness scores across simulation runs. Detects plateaus, divergence,
 * and provides actionable insights for self-play optimization.
 *
 * <p>Features:
 * <ul>
 *   <li>Historical fitness tracking with configurable window size</li>
 *   <li>Plateau detection using statistical methods</li>
 *   <li>Divergence detection with configurable thresholds</li>
 *   <li>Trend analysis using moving averages and slopes</li>
 *   <li>Actionable alerts with severity levels</li>
 * </ul>
 */
public final class ConvergenceTracker {

    private static final int DEFAULT_HISTORY_SIZE = 50;
    private static final double DEFAULT_PLATEAU_THRESHOLD = 0.001;
    private static final double DEFAULT_DIVERGENCE_THRESHOLD = 0.1;
    private static final int DEFAULT_PLATEAU_MIN_SAMPLES = 5;
    private static final int DEFAULT_TREND_WINDOW = 10;

    private final NavigableMap<Instant, SimulationRecord> history;
    private final int maxHistorySize;
    private final double plateauThreshold;
    private final double divergenceThreshold;
    private final int plateauMinSamples;
    private final int trendWindow;

    private ConvergenceTracker(Builder builder) {
        this.history = new TreeMap<>();
        this.maxHistorySize = builder.maxHistorySize;
        this.plateauThreshold = builder.plateauThreshold;
        this.divergenceThreshold = builder.divergenceThreshold;
        this.plateauMinSamples = builder.plateauMinSamples;
        this.trendWindow = builder.trendWindow;
    }

    /**
     * Records a simulation run for convergence analysis.
     *
     * @param report the simulation report to record
     * @return the convergence analysis result
     */
    public ConvergenceAnalysis recordSimulation(V7SimulationReport report) {
        Instant timestamp = report.completedAt();
        SimulationRecord record = new SimulationRecord(
            report.totalRounds(),
            report.finalFitness(),
            report.converged(),
            timestamp
        );

        // Add to history, maintaining size limit
        history.put(timestamp, record);
        if (history.size() > maxHistorySize) {
            history.pollFirstEntry();
        }

        return analyzeConvergence();
    }

    /**
     * Performs convergence analysis on the current history.
     *
     * @return convergence analysis with detected patterns and recommendations
     */
    public ConvergenceAnalysis analyzeConvergence() {
        if (history.isEmpty()) {
            return ConvergenceAnalysis.noHistory();
        }

        List<SimulationRecord> records = new ArrayList<>(history.values());
        ConvergenceStatus status = determineConvergenceStatus(records);
        List<ConvergenceAlert> alerts = detectAlerts(records);
        TrendAnalysis trend = analyzeTrend(records);
        PlateauDetection plateau = detectPlateau(records);

        return new ConvergenceAnalysis(
            status,
            alerts,
            trend,
            plateau,
            records.size(),
            records.get(records.size() - 1).finalFitness().total(),
            calculateAverageImprovementRate(records)
        );
    }

    /**
     * Determines the overall convergence status.
     */
    private ConvergenceStatus determineConvergenceStatus(List<SimulationRecord> records) {
        if (records.size() < plateauMinSamples) {
            return ConvergenceStatus.INSUFFICIENT_DATA;
        }

        SimulationRecord latest = records.get(records.size() - 1);

        if (latest.converged()) {
            return ConvergenceStatus.CONVERGED;
        }

        // Check if we're approaching convergence
        double latestFitness = latest.finalFitness().total();
        if (latestFitness > 0.8) { // Close to threshold
            return ConvergenceStatus.APPROACHING_CONVERGENCE;
        }

        // Check for negative trend
        if (isDiverging(records)) {
            return ConvergenceStatus.DIVERGING;
        }

        // Check for plateau
        if (isPlateau(records)) {
            return ConvergenceStatus.PLATEAUED;
        }

        return ConvergenceStatus.NORMAL_PROGRESS;
    }

    /**
     * Detects convergence alerts.
     */
    private List<ConvergenceAlert> detectAlerts(List<SimulationRecord> records) {
        List<ConvergenceAlert> alerts = new ArrayList<>();

        if (isDiverging(records)) {
            alerts.add(new ConvergenceAlert(
                ConvergenceAlert.AlertType.DIVERGENCE,
                "Fitness scores are diverging - performance is degrading",
                ConvergenceAlert.AlertSeverity.HIGH
            ));
        }

        if (isPlateau(records)) {
            alerts.add(new ConvergenceAlert(
                ConvergenceAlert.AlertType.PLATEAU,
                "Convergence plateau detected - fitness improvement has stalled",
                ConvergenceAlert.AlertSeverity.MEDIUM
            ));
        }

        // Check for slow progress
        if (records.size() >= 10 && calculateAverageImprovementRate(records) < 0.01) {
            alerts.add(new ConvergenceAlert(
                ConvergenceAlert.AlertType.SLOW_PROGRESS,
                "Progress is very slow (<1% average improvement per round)",
                ConvergenceAlert.AlertSeverity.LOW
            ));
        }

        // Check for oscillation
        if (isOscillating(records)) {
            alerts.add(new ConvergenceAlert(
                ConvergenceAlert.AlertType.OSCILLATION,
                "Fitness scores are oscillating without clear direction",
                ConvergenceAlert.AlertSeverity.MEDIUM
            ));
        }

        return alerts;
    }

    /**
     * Analyzes the trend of fitness scores over time.
     */
    private TrendAnalysis analyzeTrend(List<SimulationRecord> records) {
        if (records.size() < 2) {
            return new TrendAnalysis(0.0, TrendDirection.STABLE, 0.0);
        }

        int windowSize = Math.min(trendWindow, records.size());
        List<SimulationRecord> recentRecords = records.subList(
            records.size() - windowSize, records.size()
        );

        // Calculate linear regression slope
        double slope = calculateLinearRegressionSlope(recentRecords);
        TrendDirection direction = determineTrendDirection(slope);
        double correlation = calculateCorrelation(recentRecords);

        return new TrendAnalysis(slope, direction, correlation);
    }

    /**
     * Detects plateau conditions.
     */
    private PlateauDetection detectPlateau(List<SimulationRecord> records) {
        if (records.size() < plateauMinSamples) {
            return new PlateauDetection(false, 0.0, 0);
        }

        int windowSize = Math.min(plateauMinSamples, records.size());
        List<SimulationRecord> recentRecords = records.subList(
            records.size() - windowSize, records.size()
        );

        // Calculate variance in recent fitness scores
        double variance = calculateVariance(recentRecords);
        double maxFitness = recentRecords.stream()
            .mapToDouble(r -> r.finalFitness().total())
            .max()
            .orElse(0.0);
        double minFitness = recentRecords.stream()
            .mapToDouble(r -> r.finalFitness().total())
            .min()
            .orElse(0.0);

        boolean isPlateau = (maxFitness - minFitness) < plateauThreshold;

        return new PlateauDetection(isPlateau, maxFitness - minFitness, windowSize);
    }

    /**
     * Checks if fitness scores are diverging.
     */
    private boolean isDiverging(List<SimulationRecord> records) {
        if (records.size() < 5) return false;

        // Compare recent performance with earlier performance
        int splitPoint = records.size() / 2;
        double earlierAvg = records.subList(0, splitPoint).stream()
            .mapToDouble(r -> r.finalFitness().total())
            .average()
            .orElse(0.0);

        double recentAvg = records.subList(splitPoint, records.size()).stream()
            .mapToDouble(r -> r.finalFitness().total())
            .average()
            .orElse(0.0);

        return (earlierAvg - recentAvg) > divergenceThreshold;
    }

    /**
     * Checks if fitness scores have plateaued.
     */
    private boolean isPlateau(List<SimulationRecord> records) {
        PlateauDetection plateau = detectPlateau(records);
        return plateau.detected();
    }

    /**
     * Checks if fitness scores are oscillating without clear direction.
     */
    private boolean isOscillating(List<SimulationRecord> records) {
        if (records.size() < 6) return false;

        int directionChanges = 0;
        for (int i = 1; i < records.size() - 1; i++) {
            double prev = records.get(i - 1).finalFitness().total();
            double curr = records.get(i).finalFitness().total();
            double next = records.get(i + 1).finalFitness().total();

            if ((curr > prev && curr > next) || (curr < prev && curr < next)) {
                directionChanges++;
            }
        }

        return directionChanges >= records.size() / 3; // 33% of points are local extrema
    }

    /**
     * Calculates linear regression slope for trend analysis.
     */
    private double calculateLinearRegressionSlope(List<SimulationRecord> records) {
        int n = records.size();
        if (n < 2) return 0.0;

        double sumX = 0.0;
        double sumY = 0.0;
        double sumXY = 0.0;
        double sumX2 = 0.0;

        for (int i = 0; i < n; i++) {
            double x = i;
            double y = records.get(i).finalFitness().total();
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }

        return (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);
    }

    /**
     * Determines trend direction from slope.
     */
    private TrendDirection determineTrendDirection(double slope) {
        if (slope > 0.001) return TrendDirection.IMPROVING;
        if (slope < -0.001) return TrendDirection.DECLINING;
        return TrendDirection.STABLE;
    }

    /**
     * Calculates correlation coefficient for trend.
     */
    private double calculateCorrelation(List<SimulationRecord> records) {
        int n = records.size();
        if (n < 2) return 0.0;

        double sumX = 0.0;
        double sumY = 0.0;
        double sumXY = 0.0;
        double sumX2 = 0.0;
        double sumY2 = 0.0;

        for (int i = 0; i < n; i++) {
            double x = i;
            double y = records.get(i).finalFitness().total();
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
            sumY2 += y * y;
        }

        double numerator = n * sumXY - sumX * sumY;
        double denominator = Math.sqrt((n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY));

        return denominator == 0.0 ? 0.0 : numerator / denominator;
    }

    /**
     * Calculates variance of fitness scores.
     */
    private double calculateVariance(List<SimulationRecord> records) {
        if (records.isEmpty()) return 0.0;

        double mean = records.stream()
            .mapToDouble(r -> r.finalFitness().total())
            .average()
            .orElse(0.0);

        return records.stream()
            .mapToDouble(r -> Math.pow(r.finalFitness().total() - mean, 2))
            .average()
            .orElse(0.0);
    }

    /**
     * Calculates average improvement rate between consecutive simulations.
     */
    private double calculateAverageImprovementRate(List<SimulationRecord> records) {
        if (records.size() < 2) return 0.0;

        double totalImprovement = 0.0;
        int comparisons = 0;

        for (int i = 1; i < records.size(); i++) {
            double prev = records.get(i - 1).finalFitness().total();
            double curr = records.get(i).finalFitness().total();

            if (prev > 0.0) { // Avoid division by zero
                totalImprovement += (curr - prev) / prev;
                comparisons++;
            }
        }

        return comparisons == 0 ? 0.0 : totalImprovement / comparisons;
    }

    /**
     * Returns a summary of the tracking history.
     */
    public TrackingSummary getSummary() {
        if (history.isEmpty()) {
            return new TrackingSummary(0, 0.0, 0.0, 0.0, 0.0);
        }

        List<SimulationRecord> records = new ArrayList<>(history.values());
        double currentFitness = records.get(records.size() - 1).finalFitness().total();
        double maxFitness = records.stream()
            .mapToDouble(r -> r.finalFitness().total())
            .max()
            .orElse(0.0);
        double minFitness = records.stream()
            .mapToDouble(r -> r.finalFitness().total())
            .min()
            .orElse(0.0);
        double avgFitness = records.stream()
            .mapToDouble(r -> r.finalFitness().total())
            .average()
            .orElse(0.0);

        return new TrackingSummary(
            records.size(),
            currentFitness,
            maxFitness,
            minFitness,
            avgFitness
        );
    }

    /**
     * Clears the tracking history.
     */
    public void clearHistory() {
        history.clear();
    }

    /**
     * Builder for creating ConvergenceTracker instances.
     */
    public static final class Builder {
        private int maxHistorySize = DEFAULT_HISTORY_SIZE;
        private double plateauThreshold = DEFAULT_PLATEAU_THRESHOLD;
        private double divergenceThreshold = DEFAULT_DIVERGENCE_THRESHOLD;
        private int plateauMinSamples = DEFAULT_PLATEAU_MIN_SAMPLES;
        private int trendWindow = DEFAULT_TREND_WINDOW;

        public Builder maxHistorySize(int val) {
            maxHistorySize = val;
            return this;
        }

        public Builder plateauThreshold(double val) {
            plateauThreshold = val;
            return this;
        }

        public Builder divergenceThreshold(double val) {
            divergenceThreshold = val;
            return this;
        }

        public Builder plateauMinSamples(int val) {
            plateauMinSamples = val;
            return this;
        }

        public Builder trendWindow(int val) {
            trendWindow = val;
            return this;
        }

        public ConvergenceTracker build() {
            return new ConvergenceTracker(this);
        }
    }

    /**
     * Internal record for storing simulation data.
     */
    private static record SimulationRecord(
        int totalRounds,
        FitnessScore finalFitness,
        boolean converged,
        Instant timestamp
    ) {}

    /**
     * Status of convergence analysis.
     */
    public enum ConvergenceStatus {
        INSUFFICIENT_DATA,
        NORMAL_PROGRESS,
        APPROACHING_CONVERGENCE,
        CONVERGED,
        PLATEAUED,
        DIVERGING
    }

    /**
     * Direction of trend.
     */
    public enum TrendDirection {
        IMPROVING,
        STABLE,
        DECLINING
    }

    /**
     * Alert types for convergence issues.
     */
    public enum AlertType {
        DIVERGENCE,
        PLATEAU,
        SLOW_PROGRESS,
        OSCILLATION
    }

    /**
     * Alert severity levels.
     */
    public enum AlertSeverity {
        LOW,
        MEDIUM,
        HIGH
    }
}