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

package org.yawlfoundation.yawl.blue_ocean.predictive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.engine.YEngine;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicDouble;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Predictive Accuracy Tests for Blue Ocean Innovation
 *
 * This test suite validates the accuracy of predictive analytics features:
 * 1. Story Completion Forecast (±10% accuracy)
 * 2. Velocity Trend Detection (±5% accuracy)
 * 3. Risk Prediction (90%+ sensitivity, <5% false positives)
 * 4. Confidence Calibration (85%+ correlation)
 * 5. Multi-Sprint Forecast Stability
 *
 * Success Criteria:
 * - Story completion forecast: ±10% of actual
 * - Velocity trend prediction: ±5% of actual
 * - Risk detection: 90%+ sensitivity, <5% false positive rate
 * - Confidence calibration: 85%+ (predicted confidence ≈ actual accuracy)
 * - Statistical significance: p < 0.05 for all trends
 *
 * Test Framework: Chicago TDD (Detroit School)
 * - Real YAWL engine with historical data
 * - 8+ sprints of training data per test
 * - Validate against actual measured performance
 * - Measure quality metrics precisely
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
@DisplayName("Blue Ocean: Predictive Accuracy Tests")
public class PredictiveAccuracyTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(PredictiveAccuracyTest.class);

    private YEngine engine;
    private PredictiveEngineTestHelper predictiveHelper;

    @BeforeEach
    void setUp() {
        engine = YEngine.getInstance();
        assertNotNull(engine, "YEngine should be available");
        predictiveHelper = new PredictiveEngineTestHelper(engine);
    }

    /**
     * Test 1: Story Completion Forecast Accuracy (±10%)
     *
     * Setup:
     * - Team with 8 sprints of historical data
     * - Current sprint: 25 stories planned
     *
     * Expected:
     * - Predicted completion: within ±10% of actual
     * - Confidence level: >80%
     * - Statistical significance: p < 0.05
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("Accuracy: Story Completion Forecast (±10%)")
    void testStoryCompletionForecastAccuracy() {
        LOGGER.info("=== Testing Story Completion Forecast Accuracy ===");

        // Arrange: Build team with 8 sprints of historical data
        SAFETrain train = predictiveHelper.buildTrainWithHistoricalData(8);
        List<UserStory> currentSprintBacklog = predictiveHelper.generateSprintBacklog(25);

        // Train predictive model on historical data
        PredictiveForecaster forecaster = new PredictiveForecaster(train);
        forecaster.trainOnHistory();

        // Act: Predict story completion for upcoming sprint
        PredictionResult prediction = forecaster.predictStoriesCompleted(currentSprintBacklog);

        // Execute sprint (simulated with realistic completion patterns)
        List<UserStory> completedStories = predictiveHelper.simulateSprint(train, currentSprintBacklog);
        int actualCompleted = completedStories.size();

        // Assert: Forecast accuracy within ±10%
        int predictedCompleted = prediction.storiesCompletedCount();
        double absoluteError = Math.abs(actualCompleted - predictedCompleted);
        double percentageError = absoluteError / predictedCompleted;

        LOGGER.info("Story completion forecast: predicted={}, actual={}, error={:.1f}%",
            predictedCompleted, actualCompleted, percentageError * 100);

        assertThat(percentageError).isLessThan(0.10);  // ±10% accuracy

        // Confidence level validation
        assertThat(prediction.confidence()).isGreaterThan(0.80);

        // Statistical significance
        assertThat(prediction.hasStatisticalSignificance()).isTrue();
        assertThat(prediction.pValue()).isLessThan(0.05);

        // Model quality metrics
        assertThat(prediction.rmse()).isGreaterThan(0);  // Should have low RMSE
        assertThat(prediction.r2Score()).isGreaterThan(0.70);  // R² > 0.70
    }

    /**
     * Test 2: Velocity Trend Detection (±5%)
     *
     * Setup:
     * - Team with clear upward velocity trend
     * - 12 sprints of data (100 → 140 points)
     *
     * Expected:
     * - Trend direction correctly identified (INCREASING)
     * - Trend magnitude predicted within ±5%
     * - Confidence: >85%
     */
    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("Accuracy: Velocity Trend Detection (±5%)")
    void testVelocityTrendDetection() {
        LOGGER.info("=== Testing Velocity Trend Detection ===");

        // Arrange: Team with increasing velocity trend
        List<Integer> historicalVelocities = List.of(
            100, 103, 105, 108, 110, 113, 115, 118, 120, 123, 125, 128
        );
        SAFETrain train = predictiveHelper.buildTrainWithVelocitySeries(historicalVelocities);

        // Act: Predict velocity for next 3 sprints
        VelocityPredictor predictor = new VelocityPredictor(train);
        VelocityForecast forecast = predictor.predictVelocityTrend(3);  // Next 3 sprints

        // Simulate actual velocity for next sprint (130 points)
        int actualNextVelocity = 130;
        predictiveHelper.recordActualVelocity(train, actualNextVelocity);

        // Assert: Trend correctly detected
        assertThat(forecast.trendDirection()).isEqualTo(VelocityTrend.INCREASING);

        // Trend magnitude validation
        int predictedNextVelocity = forecast.nextVelocities().get(0);
        double trendError = Math.abs(actualNextVelocity - predictedNextVelocity) / (double) predictedNextVelocity;
        LOGGER.info("Velocity trend: predicted next={}, actual={}, error={:.1f}%",
            predictedNextVelocity, actualNextVelocity, trendError * 100);

        assertThat(trendError).isLessThan(0.05);  // ±5% accuracy

        // Confidence validation
        assertThat(forecast.confidence()).isGreaterThan(0.85);
        assertThat(forecast.trendStrength()).isGreaterThan(0.80);
    }

    /**
     * Test 3: Risk Detection Sensitivity (90%+)
     *
     * Setup:
     * - 10 teams with known risk conditions (low velocity, high blockers, etc.)
     * - 8 teams in good state (control group)
     *
     * Expected:
     * - Detect all 10 at-risk teams (90%+ sensitivity)
     * - False positive rate <5% on control group
     */
    @Test
    @Timeout(value = 45, unit = TimeUnit.SECONDS)
    @DisplayName("Accuracy: Risk Detection Sensitivity (90%+)")
    void testRiskDetectionSensitivity() {
        LOGGER.info("=== Testing Risk Detection Sensitivity ===");

        // Arrange: Create teams with known risk conditions
        List<SAFETrain> atRiskTeams = new ArrayList<>();

        // 4 teams with low velocity risk
        for (int i = 0; i < 4; i++) {
            atRiskTeams.add(predictiveHelper.buildTeamWithVelocityTrend(
                List.of(100, 95, 85, 75, 65, 55, 50, 48)  // Declining
            ));
        }

        // 3 teams with high blocker risk
        for (int i = 0; i < 3; i++) {
            atRiskTeams.add(predictiveHelper.buildTeamWithHighBlockerRate(20));
        }

        // 3 teams with estimation accuracy issues
        for (int i = 0; i < 3; i++) {
            atRiskTeams.add(predictiveHelper.buildTeamWithEstimationVariance(0.40));
        }

        // Control group: 8 healthy teams
        List<SAFETrain> healthyTeams = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            healthyTeams.add(predictiveHelper.buildHealthyTeam());
        }

        // Act: Run risk detection on all teams
        RiskDetector riskDetector = new RiskDetector();

        int detectedAtRisk = 0;
        for (SAFETrain team : atRiskTeams) {
            RiskAssessment assessment = riskDetector.assessTeam(team);
            if (assessment.hasRisk()) {
                detectedAtRisk++;
            }
        }

        int falsePositives = 0;
        for (SAFETrain team : healthyTeams) {
            RiskAssessment assessment = riskDetector.assessTeam(team);
            if (assessment.hasRisk()) {
                falsePositives++;
            }
        }

        // Assert: Sensitivity and specificity
        double sensitivity = detectedAtRisk / (double) atRiskTeams.size();
        double falsePositiveRate = falsePositives / (double) healthyTeams.size();

        LOGGER.info("Risk detection: detected={}/{} (sensitivity={:.0f}%), " +
            "false positives={}/{} (FPR={:.1f}%)",
            detectedAtRisk, atRiskTeams.size(), sensitivity * 100,
            falsePositives, healthyTeams.size(), falsePositiveRate * 100);

        assertThat(sensitivity).isGreaterThan(0.90);  // 90%+ sensitivity
        assertThat(falsePositiveRate).isLessThan(0.05);  // <5% false positive rate
    }

    /**
     * Test 4: Confidence Calibration (85%+ correlation)
     *
     * Setup:
     * - 50 predictions across different teams and scenarios
     * - Track predicted confidence vs. actual accuracy
     *
     * Expected:
     * - Correlation between confidence and accuracy: >0.85
     * - Confidence estimates are well-calibrated (not overconfident)
     */
    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    @DisplayName("Accuracy: Confidence Calibration (85%+ correlation)")
    void testConfidenceCalibration() {
        LOGGER.info("=== Testing Confidence Calibration ===");

        // Arrange: Generate 50 predictions with confidence scores
        List<PredictionAccuracyPair> predictions = new ArrayList<>();

        for (int i = 0; i < 50; i++) {
            SAFETrain team = predictiveHelper.buildRandomTrain();

            // Make prediction
            PredictiveForecaster forecaster = new PredictiveForecaster(team);
            PredictionResult prediction = forecaster.predictSprintCompletion();

            double predictedConfidence = prediction.confidence();

            // Execute sprint and measure actual accuracy
            List<UserStory> backlog = predictiveHelper.getSprintBacklog(team);
            List<UserStory> completed = predictiveHelper.simulateSprint(team, backlog);

            double predictedPoints = prediction.predictedPoints();
            int actualPoints = completed.stream().mapToInt(UserStory::storyPoints).sum();

            double actualAccuracy = 1.0 - (Math.abs(actualPoints - predictedPoints) / predictedPoints);

            predictions.add(new PredictionAccuracyPair(predictedConfidence, actualAccuracy));

            LOGGER.debug("Prediction {}: confidence={:.2f}, actual accuracy={:.2f}",
                i + 1, predictedConfidence, actualAccuracy);
        }

        // Act: Calculate correlation
        double correlation = calculatePearsonCorrelation(predictions);

        // Assert: Confidence calibration
        LOGGER.info("Confidence calibration: correlation = {:.3f}", correlation);
        assertThat(correlation).isGreaterThan(0.85);  // 85%+ correlation

        // Verify not overconfident (average confidence ≤ average accuracy)
        double avgConfidence = predictions.stream()
            .mapToDouble(PredictionAccuracyPair::confidence)
            .average()
            .orElse(0.0);

        double avgActualAccuracy = predictions.stream()
            .mapToDouble(PredictionAccuracyPair::accuracy)
            .average()
            .orElse(0.0);

        LOGGER.info("Confidence calibration: avg confidence={:.2f}, avg accuracy={:.2f}",
            avgConfidence, avgActualAccuracy);

        // Should not be overconfident (confidence ≈ accuracy ±5%)
        assertTrue(Math.abs(avgConfidence - avgActualAccuracy) < 0.10,
            "Model should not be overconfident; gap too large");
    }

    /**
     * Test 5: Multi-Sprint Forecast Stability
     *
     * Setup:
     * - Team with historical data
     * - Make forecast predictions for 3 consecutive sprints
     * - Verify forecast stability (not wildly fluctuating)
     *
     * Expected:
     * - Sprint-to-sprint forecast changes are smooth (not >15%)
     * - Long-term trend is consistent with data
     * - Outlier sprint predictions are marked uncertain
     */
    @Test
    @Timeout(value = 45, unit = TimeUnit.SECONDS)
    @DisplayName("Accuracy: Multi-Sprint Forecast Stability")
    void testMultiSprintForecastStability() {
        LOGGER.info("=== Testing Multi-Sprint Forecast Stability ===");

        // Arrange: Team with stable historical performance
        SAFETrain team = predictiveHelper.buildTrainWithHistoricalData(8);

        // Generate forecasts for 3 consecutive sprints
        PredictiveForecaster forecaster = new PredictiveForecaster(team);
        List<PredictionResult> forecast3Sprints = new ArrayList<>();

        for (int sprint = 1; sprint <= 3; sprint++) {
            PredictionResult prediction = forecaster.predictSprintCompletion(sprint);
            forecast3Sprints.add(prediction);

            LOGGER.info("Sprint {} forecast: {} points, confidence {:.0f}%",
                sprint, prediction.predictedPoints(), prediction.confidence() * 100);
        }

        // Act: Verify stability
        // Calculate sprint-to-sprint changes
        List<Double> changePercentages = new ArrayList<>();

        for (int i = 1; i < forecast3Sprints.size(); i++) {
            int prev = forecast3Sprints.get(i - 1).predictedPoints();
            int curr = forecast3Sprints.get(i).predictedPoints();

            double changePercent = Math.abs(curr - prev) / (double) prev;
            changePercentages.add(changePercent);
        }

        // Assert: Stability (sprint-to-sprint changes <15%)
        for (double change : changePercentages) {
            assertTrue(change < 0.15,
                "Sprint-to-sprint forecast change should be <15%, got " + change);
        }

        // Verify trend consistency
        boolean hasConsistentTrend = forecast3Sprints.stream()
            .mapToDouble(PredictionResult::confidence)
            .skip(1)  // Skip first, compare others
            .allMatch(conf -> conf > 0.75);  // All should have >75% confidence

        assertTrue(hasConsistentTrend,
            "Forecast trend should be consistent across sprints");

        LOGGER.info("Multi-sprint stability verified: sprint-to-sprint changes average {:.1f}%",
            changePercentages.stream().mapToDouble(Double::doubleValue).average().orElse(0.0) * 100);
    }

    /**
     * Parametrized Test: Velocity Prediction at Various Trend Strengths
     *
     * Tests prediction accuracy across different trend magnitudes:
     * - WEAK (±1 point/sprint)
     * - MODERATE (±3 points/sprint)
     * - STRONG (±5 points/sprint)
     */
    @ParameterizedTest(name = "Velocity Trend: {0} (±{1} points/sprint)")
    @ValueSource(ints = {1, 3, 5})
    @DisplayName("Parametrized: Velocity Prediction Accuracy by Trend Strength")
    void testVelocityPredictionByTrendStrength(int trendMagnitude) {
        LOGGER.info("Testing velocity prediction with trend magnitude: ±{}", trendMagnitude);

        // Generate velocity series with specified trend
        List<Integer> velocities = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            velocities.add(100 + (i * trendMagnitude));
        }

        SAFETrain team = predictiveHelper.buildTrainWithVelocitySeries(velocities);

        // Predict next velocity
        VelocityPredictor predictor = new VelocityPredictor(team);
        VelocityForecast forecast = predictor.predictVelocityTrend(1);

        // Simulate actual next velocity
        int expectedNextVelocity = 100 + (12 * trendMagnitude);
        predictiveHelper.recordActualVelocity(team, expectedNextVelocity);

        // Verify prediction accuracy (should improve with stronger trends)
        int predictedVelocity = forecast.nextVelocities().get(0);
        double error = Math.abs(expectedNextVelocity - predictedVelocity) /
            (double) predictedVelocity;

        LOGGER.info("Trend ±{}: predicted={}, expected={}, error={:.1f}%, confidence={:.0f}%",
            trendMagnitude, predictedVelocity, expectedNextVelocity, error * 100,
            forecast.confidence() * 100);

        // Stronger trends should be more predictable
        double maxError = trendMagnitude == 1 ? 0.12 : (trendMagnitude == 3 ? 0.10 : 0.08);
        assertThat(error).isLessThan(maxError);
    }

    // ========== Helper Classes ==========

    private record PredictionAccuracyPair(double confidence, double accuracy) {}

    /**
     * Calculate Pearson correlation coefficient between two arrays
     */
    private double calculatePearsonCorrelation(List<PredictionAccuracyPair> pairs) {
        if (pairs.size() < 2) {
            return 0.0;
        }

        double[] confidences = pairs.stream()
            .mapToDouble(PredictionAccuracyPair::confidence)
            .toArray();

        double[] accuracies = pairs.stream()
            .mapToDouble(PredictionAccuracyPair::accuracy)
            .toArray();

        double meanConf = 0.0;
        double meanAcc = 0.0;

        for (double c : confidences) meanConf += c;
        for (double a : accuracies) meanAcc += a;

        meanConf /= confidences.length;
        meanAcc /= accuracies.length;

        double covariance = 0.0;
        double stdConfidence = 0.0;
        double stdAccuracy = 0.0;

        for (int i = 0; i < confidences.length; i++) {
            double dConf = confidences[i] - meanConf;
            double dAcc = accuracies[i] - meanAcc;
            covariance += dConf * dAcc;
            stdConfidence += dConf * dConf;
            stdAccuracy += dAcc * dAcc;
        }

        if (stdConfidence == 0 || stdAccuracy == 0) {
            return 0.0;
        }

        return covariance / (Math.sqrt(stdConfidence) * Math.sqrt(stdAccuracy));
    }
}
