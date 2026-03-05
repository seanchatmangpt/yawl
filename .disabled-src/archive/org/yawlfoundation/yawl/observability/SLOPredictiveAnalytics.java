/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * SLO Predictive Analytics using Holt-Winters triple exponential smoothing.
 *
 * <p>Provides predictive analytics for SLO compliance with:
 * <ul>
 *   <li>4-hour forecast horizon</li>
 *   <li>24-hour seasonality detection</li>
 *   <li>Breach probability calculation (0.0-1.0)</li>
 *   <li>Confidence intervals</li>
 * </ul>
 *
 * <h2>Algorithm</h2>
 * <p>Uses Holt-Winters Triple Exponential Smoothing with configurable parameters:
 * <ul>
 *   <li>Alpha (level smoothing): 0.3</li>
 *   <li>Beta (trend smoothing): 0.1</li>
 *   <li>Gamma (seasonality smoothing): 0.2</li>
 *   <li>Seasonality period: 24 hours (hourly data points)</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * SLOPredictiveAnalytics analytics = new SLOPredictiveAnalytics();
 *
 * // Add historical data points
 * analytics.addDataPoint(SLOTracker.SLOType.CASE_COMPLETION, 99.5, Instant.now());
 *
 * // Get forecast
 * ForecastResult forecast = analytics.forecast(SLOTracker.SLOType.CASE_COMPLETION, 4);
 *
 * // Check breach probability
 * double breachProb = analytics.getBreachProbability(SLOTracker.SLOType.CASE_COMPLETION, 0.999);
 * if (breachProb > 0.7) {
 *     andonCord.pull(AndonCord.Severity.P2_MEDIUM, "predicted_slo_breach", Map.of(
 *         "slo_type", SLOTracker.SLOType.CASE_COMPLETION.name(),
 *         "breach_probability", breachProb
 *     ));
 * }
 * }</pre>
 *
 * @author YAWL Foundation
 * @since 6.0.0
 */
public final class SLOPredictiveAnalytics {

    private static final Logger LOGGER = LoggerFactory.getLogger(SLOPredictiveAnalytics.class);

    // Default smoothing parameters
    private static final double DEFAULT_ALPHA = 0.3;   // Level smoothing
    private static final double DEFAULT_BETA = 0.1;    // Trend smoothing
    private static final double DEFAULT_GAMMA = 0.2;   // Seasonality smoothing

    // Forecast configuration
    private static final int SEASONALITY_PERIOD = 24;  // 24 hours (hourly data)
    private static final int MAX_FORECAST_HORIZON = 4; // 4 hours max forecast
    private static final int MIN_DATA_POINTS = 24;     // Need at least 24 points

    /**
     * Forecast result with confidence interval.
     */
    public static final class ForecastResult {
        private final SLOTracker.SLOType sloType;
        private final Instant forecastTime;
        private final Instant generatedAt;
        private final double predictedValue;
        private final double lowerBound;
        private final double upperBound;
        private final double confidenceLevel;
        private final int horizonHours;

        public ForecastResult(SLOTracker.SLOType sloType, Instant forecastTime,
                             double predictedValue, double lowerBound, double upperBound,
                             double confidenceLevel, int horizonHours) {
            this.sloType = sloType;
            this.forecastTime = forecastTime;
            this.generatedAt = Instant.now();
            this.predictedValue = predictedValue;
            this.lowerBound = lowerBound;
            this.upperBound = upperBound;
            this.confidenceLevel = confidenceLevel;
            this.horizonHours = horizonHours;
        }

        public SLOTracker.SLOType getSloType() { return sloType; }
        public Instant getForecastTime() { return forecastTime; }
        public Instant getGeneratedAt() { return generatedAt; }
        public double getPredictedValue() { return predictedValue; }
        public double getLowerBound() { return lowerBound; }
        public double getUpperBound() { return upperBound; }
        public double getConfidenceLevel() { return confidenceLevel; }
        public int getHorizonHours() { return horizonHours; }

        /**
         * Checks if the prediction indicates a potential breach.
         */
        public boolean isBreachPredicted() {
            return predictedValue < sloType.getTargetPercentage();
        }

        /**
         * Gets the deviation from target as a percentage.
         */
        public double getDeviationFromTarget() {
            return predictedValue - sloType.getTargetPercentage();
        }

        @Override
        public String toString() {
            return String.format("Forecast[%s: predicted=%.2f%%, range=[%.2f%%, %.2f%%], horizon=%dh]",
                sloType.name(), predictedValue, lowerBound, upperBound, horizonHours);
        }
    }

    /**
     * Breach prediction with probability.
     */
    public static final class BreachPrediction {
        private final SLOTracker.SLOType sloType;
        private final double breachProbability;
        private final Instant predictedBreachTime;
        private final double predictedValueAtBreach;
        private final double targetThreshold;
        private final int hoursUntilBreach;
        private final String severity;

        public BreachPrediction(SLOTracker.SLOType sloType, double breachProbability,
                               Instant predictedBreachTime, double predictedValueAtBreach,
                               int hoursUntilBreach) {
            this.sloType = sloType;
            this.breachProbability = breachProbability;
            this.predictedBreachTime = predictedBreachTime;
            this.predictedValueAtBreach = predictedValueAtBreach;
            this.targetThreshold = sloType.getTargetPercentage();
            this.hoursUntilBreach = hoursUntilBreach;

            if (breachProbability >= 0.8) {
                this.severity = "CRITICAL";
            } else if (breachProbability >= 0.5) {
                this.severity = "HIGH";
            } else if (breachProbability >= 0.3) {
                this.severity = "MEDIUM";
            } else {
                this.severity = "LOW";
            }
        }

        public SLOTracker.SLOType getSloType() { return sloType; }
        public double getBreachProbability() { return breachProbability; }
        public Instant getPredictedBreachTime() { return predictedBreachTime; }
        public double getPredictedValueAtBreach() { return predictedValueAtBreach; }
        public double getTargetThreshold() { return targetThreshold; }
        public int getHoursUntilBreach() { return hoursUntilBreach; }
        public String getSeverity() { return severity; }

        public boolean isImminent() {
            return hoursUntilBreach <= 1 && breachProbability >= 0.5;
        }

        @Override
        public String toString() {
            return String.format("BreachPrediction[%s: prob=%.1f%%, in=%dh, severity=%s]",
                sloType.name(), breachProbability * 100, hoursUntilBreach, severity);
        }
    }

    /**
     * Data point for time series.
     */
    private static final class TimeSeriesPoint {
        final Instant timestamp;
        final double value;
        final int hourOfDay;

        TimeSeriesPoint(Instant timestamp, double value) {
            this.timestamp = timestamp;
            this.value = value;
            this.hourOfDay = timestamp.atZone(java.time.ZoneOffset.UTC).getHour();
        }
    }

    /**
     * Holt-Winters model state.
     */
    private static final class HoltWintersModel {
        double level;
        double trend;
        double[] seasonals;
        boolean initialized;
        int dataPointCount;

        HoltWintersModel() {
            this.seasonals = new double[SEASONALITY_PERIOD];
            this.initialized = false;
            this.dataPointCount = 0;
        }
    }

    // Storage for time series data per SLO type
    private final Map<SLOTracker.SLOType, ConcurrentLinkedQueue<TimeSeriesPoint>> timeSeriesData;
    private final Map<SLOTracker.SLOType, HoltWintersModel> models;

    // Smoothing parameters
    private final double alpha;
    private final double beta;
    private final double gamma;

    // Alerting
    private final AndonCord andonCord;

    /**
     * Creates analytics with default parameters.
     */
    public SLOPredictiveAnalytics() {
        this(DEFAULT_ALPHA, DEFAULT_BETA, DEFAULT_GAMMA);
    }

    /**
     * Creates analytics with custom smoothing parameters.
     *
     * @param alpha level smoothing factor (0-1)
     * @param beta trend smoothing factor (0-1)
     * @param gamma seasonality smoothing factor (0-1)
     */
    public SLOPredictiveAnalytics(double alpha, double beta, double gamma) {
        if (alpha < 0 || alpha > 1 || beta < 0 || beta > 1 || gamma < 0 || gamma > 1) {
            throw new IllegalArgumentException("Smoothing factors must be between 0 and 1");
        }

        this.alpha = alpha;
        this.beta = beta;
        this.gamma = gamma;
        this.timeSeriesData = new ConcurrentHashMap<>();
        this.models = new ConcurrentHashMap<>();
        this.andonCord = AndonCord.getInstance();

        // Initialize for all SLO types
        for (SLOTracker.SLOType type : SLOTracker.SLOType.values()) {
            timeSeriesData.put(type, new ConcurrentLinkedQueue<>());
            models.put(type, new HoltWintersModel());
        }

        LOGGER.info("SLOPredictiveAnalytics initialized: alpha={}, beta={}, gamma={}",
            alpha, beta, gamma);
    }

    /**
     * Adds a data point for an SLO type.
     *
     * @param sloType the SLO type
     * @param value the compliance value (0-100)
     * @param timestamp the timestamp
     */
    public void addDataPoint(SLOTracker.SLOType sloType, double value, Instant timestamp) {
        Objects.requireNonNull(sloType);
        Objects.requireNonNull(timestamp);

        ConcurrentLinkedQueue<TimeSeriesPoint> series = timeSeriesData.get(sloType);
        if (series == null) {
            series = new ConcurrentLinkedQueue<>();
            timeSeriesData.put(sloType, series);
        }

        series.add(new TimeSeriesPoint(timestamp, value));

        // Update model
        updateModel(sloType, value);

        LOGGER.debug("Added data point for {}: value={}, timestamp={}", sloType, value, timestamp);
    }

    /**
     * Updates the Holt-Winters model with a new value.
     */
    private void updateModel(SLOTracker.SLOType sloType, double value) {
        HoltWintersModel model = models.computeIfAbsent(sloType, k -> new HoltWintersModel());
        ConcurrentLinkedQueue<TimeSeriesPoint> series = timeSeriesData.get(sloType);

        model.dataPointCount++;

        if (!model.initialized) {
            // Initialize model with first seasonality period of data
            if (series.size() >= SEASONALITY_PERIOD) {
                initializeModel(model, series);
            }
            return;
        }

        // Get current seasonal index
        TimeSeriesPoint latest = series.peek();
        if (latest == null) return;

        int seasonIndex = latest.hourOfDay % SEASONALITY_PERIOD;

        // Update level
        double oldLevel = model.level;
        model.level = alpha * (value / model.seasonals[seasonIndex]) + (1 - alpha) * (oldLevel + model.trend);

        // Update trend
        model.trend = beta * (model.level - oldLevel) + (1 - beta) * model.trend;

        // Update seasonal
        model.seasonals[seasonIndex] = gamma * (value / model.level) + (1 - gamma) * model.seasonals[seasonIndex];
    }

    /**
     * Initializes the model with initial seasonality estimates.
     */
    private void initializeModel(HoltWintersModel model, ConcurrentLinkedQueue<TimeSeriesPoint> series) {
        List<TimeSeriesPoint> points = new ArrayList<>(series);

        // Calculate initial level (average of first season)
        double sum = 0;
        for (int i = 0; i < SEASONALITY_PERIOD && i < points.size(); i++) {
            sum += points.get(i).value;
        }
        model.level = sum / Math.min(SEASONALITY_PERIOD, points.size());

        // Calculate initial trend
        if (points.size() >= 2 * SEASONALITY_PERIOD) {
            double firstSeasonSum = 0, secondSeasonSum = 0;
            for (int i = 0; i < SEASONALITY_PERIOD; i++) {
                firstSeasonSum += points.get(i).value;
                secondSeasonSum += points.get(i + SEASONALITY_PERIOD).value;
            }
            model.trend = (secondSeasonSum - firstSeasonSum) / (SEASONALITY_PERIOD * SEASONALITY_PERIOD);
        } else {
            model.trend = 0;
        }

        // Calculate initial seasonals
        for (int i = 0; i < SEASONALITY_PERIOD && i < points.size(); i++) {
            model.seasonals[i] = points.get(i).value / model.level;
        }

        model.initialized = true;
        LOGGER.debug("Holt-Winters model initialized: level={}, trend={}", model.level, model.trend);
    }

    /**
     * Generates a forecast for an SLO type.
     *
     * @param sloType the SLO type
     * @param horizonHours forecast horizon in hours (1-4)
     * @return the forecast result
     */
    public ForecastResult forecast(SLOTracker.SLOType sloType, int horizonHours) {
        Objects.requireNonNull(sloType);

        if (horizonHours < 1 || horizonHours > MAX_FORECAST_HORIZON) {
            throw new IllegalArgumentException("Horizon must be between 1 and " + MAX_FORECAST_HORIZON);
        }

        HoltWintersModel model = models.get(sloType);
        ConcurrentLinkedQueue<TimeSeriesPoint> series = timeSeriesData.get(sloType);

        if (series == null || series.size() < MIN_DATA_POINTS || !model.initialized) {
            // Not enough data - return current value as forecast
            double currentValue = getCurrentValue(sloType);
            return new ForecastResult(
                sloType,
                Instant.now().plus(Duration.ofHours(horizonHours)),
                currentValue,
                currentValue * 0.9,
                currentValue * 1.1,
                0.5, // Low confidence
                horizonHours
            );
        }

        // Calculate forecast
        int futureHour = Instant.now().atZone(java.time.ZoneOffset.UTC).getHour() + horizonHours;
        int seasonIndex = futureHour % SEASONALITY_PERIOD;

        double forecast = model.level + horizonHours * model.trend;
        forecast *= model.seasonals[seasonIndex];

        // Calculate confidence interval (widens with horizon)
        double stdDev = calculateStandardDeviation(series);
        double confidenceWidth = stdDev * Math.sqrt(horizonHours) * 1.96; // 95% CI

        double lower = Math.max(0, forecast - confidenceWidth);
        double upper = Math.min(100, forecast + confidenceWidth);

        return new ForecastResult(
            sloType,
            Instant.now().plus(Duration.ofHours(horizonHours)),
            forecast,
            lower,
            upper,
            0.95,
            horizonHours
        );
    }

    /**
     * Calculates the breach probability for an SLO type.
     *
     * @param sloType the SLO type
     * @param threshold the threshold value (typically the target percentage)
     * @return breach probability (0.0-1.0)
     */
    public double getBreachProbability(SLOTracker.SLOType sloType, double threshold) {
        ForecastResult forecast = forecast(sloType, MAX_FORECAST_HORIZON);

        // If upper bound is below threshold, high probability of breach
        if (forecast.getUpperBound() < threshold) {
            return 0.95;
        }

        // If lower bound is above threshold, low probability of breach
        if (forecast.getLowerBound() > threshold) {
            return 0.05;
        }

        // Linear interpolation based on where threshold falls in CI
        double range = forecast.getUpperBound() - forecast.getLowerBound();
        if (range == 0) return 0.5;

        double thresholdPosition = (threshold - forecast.getLowerBound()) / range;
        return 1.0 - Math.max(0, Math.min(1, thresholdPosition));
    }

    /**
     * Gets a comprehensive breach prediction for an SLO type.
     *
     * @param sloType the SLO type
     * @return the breach prediction, or null if no breach predicted
     */
    public BreachPrediction predictBreach(SLOTracker.SLOType sloType) {
        double threshold = sloType.getTargetPercentage();
        double breachProb = getBreachProbability(sloType, threshold);

        if (breachProb < 0.1) {
            return null; // Low probability, no prediction needed
        }

        // Find when breach is most likely
        int predictedHorizon = 0;
        double predictedValue = 0;

        for (int h = 1; h <= MAX_FORECAST_HORIZON; h++) {
            ForecastResult forecast = forecast(sloType, h);
            if (forecast.getPredictedValue() < threshold) {
                predictedHorizon = h;
                predictedValue = forecast.getPredictedValue();
                break;
            }
        }

        if (predictedHorizon == 0) {
            // Breach predicted but not within forecast horizon
            predictedHorizon = MAX_FORECAST_HORIZON;
            ForecastResult forecast = forecast(sloType, MAX_FORECAST_HORIZON);
            predictedValue = forecast.getPredictedValue();
        }

        return new BreachPrediction(
            sloType,
            breachProb,
            Instant.now().plus(Duration.ofHours(predictedHorizon)),
            predictedValue,
            predictedHorizon
        );
    }

    /**
     * Gets breach predictions for all SLO types.
     *
     * @return list of breach predictions (only includes types with breach probability > 0.1)
     */
    public List<BreachPrediction> getAllBreachPredictions() {
        List<BreachPrediction> predictions = new ArrayList<>();

        for (SLOTracker.SLOType type : SLOTracker.SLOType.values()) {
            BreachPrediction prediction = predictBreach(type);
            if (prediction != null) {
                predictions.add(prediction);
            }
        }

        // Sort by breach probability (highest first)
        predictions.sort((a, b) -> Double.compare(b.getBreachProbability(), a.getBreachProbability()));

        return predictions;
    }

    /**
     * Gets the current value for an SLO type.
     */
    private double getCurrentValue(SLOTracker.SLOType sloType) {
        ConcurrentLinkedQueue<TimeSeriesPoint> series = timeSeriesData.get(sloType);
        if (series == null || series.isEmpty()) {
            return sloType.getTargetPercentage();
        }

        // Return the most recent value
        TimeSeriesPoint latest = null;
        for (TimeSeriesPoint point : series) {
            latest = point;
        }
        return latest != null ? latest.value : sloType.getTargetPercentage();
    }

    /**
     * Calculates standard deviation of the time series.
     */
    private double calculateStandardDeviation(ConcurrentLinkedQueue<TimeSeriesPoint> series) {
        List<TimeSeriesPoint> points = new ArrayList<>(series);
        if (points.isEmpty()) return 0;

        double mean = points.stream().mapToDouble(p -> p.value).average().orElse(0);
        double variance = points.stream()
            .mapToDouble(p -> Math.pow(p.value - mean, 2))
            .average()
            .orElse(0);

        return Math.sqrt(variance);
    }

    /**
     * Gets the number of data points stored for an SLO type.
     */
    public int getDataPointCount(SLOTracker.SLOType sloType) {
        ConcurrentLinkedQueue<TimeSeriesPoint> series = timeSeriesData.get(sloType);
        return series != null ? series.size() : 0;
    }

    /**
     * Clears all stored data for an SLO type.
     */
    public void clearData(SLOTracker.SLOType sloType) {
        ConcurrentLinkedQueue<TimeSeriesPoint> series = timeSeriesData.get(sloType);
        if (series != null) {
            series.clear();
        }
        models.put(sloType, new HoltWintersModel());
        LOGGER.info("Cleared predictive analytics data for {}", sloType);
    }

    /**
     * Clears all stored data.
     */
    public void clearAllData() {
        for (SLOTracker.SLOType type : SLOTracker.SLOType.values()) {
            clearData(type);
        }
    }

    /**
     * Gets a summary of predictive analytics status.
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder("SLO Predictive Analytics Summary:\n");
        sb.append("==================================\n");

        for (SLOTracker.SLOType type : SLOTracker.SLOType.values()) {
            HoltWintersModel model = models.get(type);
            int dataPoints = getDataPointCount(type);
            BreachPrediction prediction = predictBreach(type);

            sb.append(String.format("%-20s: %d points, model=%s",
                type.name(), dataPoints, model.initialized ? "initialized" : "initializing"));

            if (prediction != null) {
                sb.append(String.format(", breach_prob=%.1f%%", prediction.getBreachProbability() * 100));
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
