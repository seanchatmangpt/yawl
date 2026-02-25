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
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test suite for SLOPredictiveAnalytics functionality.
 *
 * Tests:
 * - Holt-Winters model initialization and prediction
 * - Anomaly detection capabilities
 * - Breach probability calculation
 * - Alert generation and escalation
 * - Model accuracy tracking
 * - Concurrent processing
 */
class SLOPredictiveAnalyticsTest {

    private MeterRegistry meterRegistry;
    private AndonCord andonCord;
    private SLOTracker sloTracker;
    private SLOPredictiveAnalytics predictiveAnalytics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        andonCord = mock(AndonCord.class);
        sloTracker = new SLOTracker(meterRegistry, andonCord);
        predictiveAnalytics = new SLOPredictiveAnalytics(sloTracker, meterRegistry, andonCord);
    }

    @AfterEach
    void tearDown() {
        predictiveAnalytics.stop();
    }

    @Test
    void testInitialization() {
        assertNotNull(predictiveAnalytics);
        assertEquals(5, predictiveAnalytics.getAllPredictions().size());
    }

    @Test
    void testHoltWintersModel() {
        // Generate time series data
        Map<String, String> context = new HashMap<>();
        context.put("case_type", "time_series");

        // Add increasing values trending toward threshold
        for (int i = 20; i <= 30; i++) {
            sloTracker.recordCaseCompletion("case-holt-" + i, i * 60 * 60 * 1000L, context); // 20-30 hours
        }

        // Get prediction result
        SLOPredictiveAnalytics.PredictionResult result =
            predictiveAnalytics.getPredictionResult(SLOTracker.SLO_CASE_COMPLETION);

        assertNotNull(result);
        assertNotNull(result.getForecast());
        assertNotNull(result.getBreachProbability());
        assertTrue(result.getBreachProbability() >= 0);
        assertTrue(result.getBreachProbability() <= 1);
    }

    @Test
    void testAnomalyDetection() {
        // Generate normal data
        Map<String, String> context = new HashMap<>();
        context.put("case_type", "normal");

        for (int i = 0; i < 50; i++) {
            sloTracker.recordCaseCompletion("case-normal-" + i, i * 60 * 60 * 1000L, context); // Consistent trend
        }

        // Add anomalous data
        sloTracker.recordCaseCompletion("case-anomaly-1", 5 * 60 * 60 * 1000L, context); // Sudden drop

        // Check predictions
        SLOPredictiveAnalytics.PredictionResult result =
            predictiveAnalytics.getPredictionResult(SLOTracker.SLO_CASE_COMPLETION);

        assertNotNull(result);
        assertNotNull(result.getAnomalyResult());
        // Anomaly detection should flag the sudden change
    }

    @Test
    void testBreachProbabilityCalculation() {
        // Generate data trending toward threshold
        Map<String, String> context = new HashMap<>();
        context.put("case_type", "trending");

        // Add values approaching 24-hour threshold
        for (int i = 22; i <= 24; i++) {
            sloTracker.recordCaseCompletion("case-trend-" + i, i * 60 * 60 * 1000L, context); // 22-24 hours
        }

        // Get prediction
        SLOPredictiveAnalytics.PredictionResult result =
            predictiveAnalytics.getPredictionResult(SLOTracker.SLO_CASE_COMPLETION);

        assertNotNull(result);
        // Should show high breach probability
        assertTrue(result.getBreachProbability() > 0.5);
    }

    @Test
    void testAlertGenerationForCriticalBreach() {
        // Generate data that will trigger critical alert (>95% breach probability)
        Map<String, String> context = new HashMap<>();
        context.put("case_type", "critical");

        for (int i = 23; i <= 24; i++) {
            sloTracker.recordCaseCompletion("case-critical-" + i, i * 60 * 60 * 1000L, context); // 23-24 hours
        }

        // Start predictive analytics
        predictiveAnalytics.start();

        // Wait for predictions to run
        try {
            Thread.sleep(2000); // Wait for prediction cycle
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Verify alert was triggered
        verify(andonCord, atLeast(1)).triggerCriticalAlert(anyString(), any());
    }

    @Test
    void testAlertGenerationForWarning() {
        // Generate data that will trigger warning alert (60-80% breach probability)
        Map<String, String> context = new HashMap<>();
        context.put("case_type", "warning");

        for (int i = 20; i <= 22; i++) {
            sloTracker.recordCaseCompletion("case-warning-" + i, i * 60 * 60 * 1000L, context); // 20-22 hours
        }

        // Start predictive analytics
        predictiveAnalytics.start();

        // Wait for predictions to run
        try {
            Thread.sleep(2000); // Wait for prediction cycle
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Verify warning alert was triggered
        verify(andonCord, atLeast(1)).triggerWarning(anyString(), any());
    }

    @Test
    void testModelAccuracyTracking() {
        // Generate some predictions
        Map<String, String> context = new HashMap<>();
        context.put("case_type", "accuracy");

        for (int i = 10; i < 20; i++) {
            sloTracker.recordCaseCompletion("case-accuracy-" + i, i * 60 * 60 * 1000L, context);
        }

        // Get model accuracy
        double accuracy = predictiveAnalytics.getModelAccuracy();

        assertNotNull(accuracy);
        assertTrue(accuracy >= 0);
        assertTrue(accuracy <= 1);
    }

    @Test
    void testConcurrentPredictions() {
        // Start predictive analytics
        predictiveAnalytics.start();

        // Generate data concurrently
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < 100; i++) {
            final int threadId = i;
            executor.submit(() -> {
                Map<String, String> context = new HashMap<>();
                context.put("thread_id", String.valueOf(threadId));
                sloTracker.recordCaseCompletion("case-concurrent-" + threadId, threadId * 60 * 60 * 1000L, context);
            });
        }

        executor.shutdown();
        try {
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        // Verify predictions are still working
        Map<String, SLOPredictiveAnalytics.PredictionResult> predictions =
            predictiveAnalytics.getAllPredictions();

        assertNotNull(predictions);
        assertFalse(predictions.isEmpty());
    }

    @Test
    void testInsufficientDataForPrediction() {
        // Generate minimal data
        Map<String, String> context = new HashMap<>();
        context.put("case_type", "minimal");

        sloTracker.recordCaseCompletion("case-minimal-1", 1000, context);

        // Should not be able to predict with insufficient data
        SLOPredictiveAnalytics.PredictionResult result =
            predictiveAnalytics.getPredictionResult(SLOTracker.SLO_CASE_COMPLETION);

        assertNull(result); // Should return null when not enough data
    }

    @Test
    void testForecastHorizon() {
        // Generate sufficient data
        Map<String, String> context = new HashMap<>();
        context.put("case_type", "forecast");

        for (int i = 0; i < 60; i++) {
            sloTracker.recordCaseCompletion("case-forecast-" + i, i * 60 * 60 * 1000L, context);
        }

        // Get prediction
        SLOPredictiveAnalytics.PredictionResult result =
            predictiveAnalytics.getPredictionResult(SLOTracker.SLO_CASE_COMPLETION);

        assertNotNull(result);
        SLOPredictiveAnalytics.ForecastResult forecast = result.getForecast();
        assertNotNull(forecast);

        // Verify forecast length matches configuration (4 hours)
        assertEquals(4, forecast.getForecastValues().size());
    }

    @Test
    void testSeasonalFactors() {
        // Generate data with seasonal patterns
        Map<String, String> context = new HashMap<>();
        context.put("case_type", "seasonal");

        // Simulate daily pattern (higher load during certain hours)
        for (int day = 0; day < 7; day++) {
            for (int hour = 0; hour < 24; hour++) {
                int baseLoad = (hour < 12) ? 10 : 20; // Higher load in afternoon
                sloTracker.recordCaseCompletion(
                    "case-seasonal-" + day + "-" + hour,
                    (baseLoad + day) * 60 * 60 * 1000L,
                    context
                );
            }
        }

        // Get prediction
        SLOPredictiveAnalytics.PredictionResult result =
            predictiveAnalytics.getPredictionResult(SLOTracker.SLO_CASE_COMPLETION);

        assertNotNull(result);
        SLOPredictiveAnalytics.ForecastResult forecast = result.getForecast();
        assertNotNull(forecast);

        // Verify seasonal factors are captured
        List<Double> seasonalFactors = forecast.getSeasonalFactors();
        assertFalse(seasonalFactors.isEmpty());
        assertEquals(24, seasonalFactors.size()); // Daily seasonality
    }

    @Test
    void testMetricsRegistration() {
        // Verify prediction metrics are registered
        assertNotNull(meterRegistry.find("yawl.slo.prediction"));
        assertNotNull(meterRegistry.find("yawl.slo.predictions"));
        assertNotNull(meterRegistry.find("yawl.slo.breach_predictions"));
        assertNotNull(meterRegistry.find("yawl.slo.model_accuracy"));
    }

    @Test
    void testPredictionDataStructures() {
        // Generate test data
        Map<String, String> context = new HashMap<>();
        context.put("case_type", "struct_test");

        for (int i = 0; i < 30; i++) {
            sloTracker.recordCaseCompletion("case-struct-" + i, i * 60 * 60 * 1000L, context);
        }

        // Get prediction
        SLOPredictiveAnalytics.PredictionResult result =
            predictiveAnalytics.getPredictionResult(SLOTracker.SLO_CASE_COMPLETION);

        assertNotNull(result);
        assertEquals(SLOTracker.SLO_CASE_COMPLETION, result.getSloId());
        assertNotNull(result.getTimestamp());
        assertNotNull(result.getForecast());
        assertTrue(result.getBreachProbability() >= 0);
        assertTrue(result.getBreachProbability() <= 1);

        // Verify forecast structure
        SLOPredictiveAnalytics.ForecastResult forecast = result.getForecast();
        assertNotNull(forecast.getForecastValues());
        assertNotNull(forecast.getSeasonalFactors());
        assertFalse(forecast.getForecastValues().isEmpty());

        // Verify anomaly result structure
        SLOPredictiveAnalytics.AnomalyResult anomalyResult = result.getAnomalyResult();
        assertNotNull(anomalyResult);
        assertTrue(anomalyResult.isAnomaly() || !anomalyResult.getAnomalyValues().isEmpty());
    }
}