package org.yawlfoundation.yawl.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD: Real database operations, no mocks.
 * Tests AnomalyDetector with real Micrometer registry.
 */
class AnomalyDetectorTest {

    private AnomalyDetector detector;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        detector = new AnomalyDetector(meterRegistry);
    }

    @Test
    void testNormalExecution_NoAnomalyDetected() {
        // Establish baseline with normal execution times (all ~100ms)
        for (int i = 0; i < 40; i++) {
            Map<String, String> context = new HashMap<>();
            context.put("task", "approval");
            detector.recordExecution("task.duration", 100 + (i % 10), context);
        }

        // Record normal execution
        Map<String, String> context = new HashMap<>();
        context.put("task", "approval");
        detector.recordExecution("task.duration", 105, context);

        // Should not trigger anomaly
        assertEquals(0, detector.getTotalAnomalies());
    }

    @Test
    void testOutlierExecution_AnomalyDetected() {
        // Establish baseline: all ~100ms for 35 samples
        for (int i = 0; i < 35; i++) {
            Map<String, String> context = new HashMap<>();
            context.put("task", "approval");
            detector.recordExecution("task.duration", 100, context);
        }

        AnomalyDetector.MetricBaseline baseline = detector.getBaseline("task.duration");
        assertNotNull(baseline);
        assertTrue(baseline.getSampleCount() >= 30);

        // Record outlier (5x normal)
        Map<String, String> context = new HashMap<>();
        context.put("task", "approval");
        detector.recordExecution("task.duration", 500, context);

        // Should trigger anomaly
        assertTrue(detector.getTotalAnomalies() > 0);
    }

    @Test
    void testBaselineEstimation_MeanAccuracy() {
        // Record series with known mean (200ms)
        for (int i = 0; i < 50; i++) {
            Map<String, String> context = new HashMap<>();
            detector.recordExecution("case.duration", 200, context);
        }

        AnomalyDetector.MetricBaseline baseline = detector.getBaseline("case.duration");
        assertNotNull(baseline);

        // EWMA should converge to ~200
        assertTrue(Math.abs(baseline.getMean() - 200) < 10);
    }

    @Test
    void testAdaptiveThreshold_IncreaseWithVariability() {
        // Record stable baseline
        for (int i = 0; i < 30; i++) {
            detector.recordExecution("task.duration", 100);
        }

        AnomalyDetector.MetricBaseline baseline1 = detector.getBaseline("task.duration");
        double threshold1 = baseline1.getThreshold();

        // Reset and record high-variance baseline
        detector.reset();
        for (int i = 0; i < 30; i++) {
            detector.recordExecution("task.duration", i % 2 == 0 ? 50 : 150);
        }

        AnomalyDetector.MetricBaseline baseline2 = detector.getBaseline("task.duration");
        double threshold2 = baseline2.getThreshold();

        // High variance should have higher threshold
        assertTrue(threshold2 > threshold1);
    }

    @Test
    void testPercentileCalculation() {
        // Record known values
        int[] values = {10, 20, 30, 40, 50, 60, 70, 80, 90, 100};
        for (int v : values) {
            detector.recordExecution("metric", v);
        }

        AnomalyDetector.MetricBaseline baseline = detector.getBaseline("metric");
        long p50 = baseline.getPercentile(0.5);
        long p95 = baseline.getPercentile(0.95);

        assertTrue(p50 >= 40 && p50 <= 60);
        assertTrue(p95 >= 85 && p95 <= 100);
    }

    @Test
    void testNegativeDuration_Ignored() {
        for (int i = 0; i < 30; i++) {
            detector.recordExecution("task.duration", 100);
        }

        int initialAnomalies = detector.getTotalAnomalies();

        // Negative duration should be silently ignored
        detector.recordExecution("task.duration", -50);

        assertEquals(initialAnomalies, detector.getTotalAnomalies());
    }

    @Test
    void testMultipleMetrics_IndependentBaselines() {
        // Record baseline for task A (100ms)
        for (int i = 0; i < 35; i++) {
            detector.recordExecution("task.a.duration", 100);
        }

        // Record baseline for task B (1000ms)
        for (int i = 0; i < 35; i++) {
            detector.recordExecution("task.b.duration", 1000);
        }

        // Outlier in task A
        detector.recordExecution("task.a.duration", 500);
        int anomaliesA = detector.getTotalAnomalies();

        // Reset and check task B
        detector.reset();
        for (int i = 0; i < 35; i++) {
            detector.recordExecution("task.b.duration", 1000);
        }

        detector.recordExecution("task.b.duration", 1100);
        int anomaliesB = detector.getTotalAnomalies();

        // Both should have independent baselines
        assertFalse(anomaliesA > 0 && anomaliesB > 0);
    }

    @Test
    void testHistoryTrimming_MaxSizeEnforced() {
        // Record 600 samples (exceeds max of 500)
        for (int i = 0; i < 600; i++) {
            detector.recordExecution("metric", 100 + (i % 20));
        }

        AnomalyDetector.MetricBaseline baseline = detector.getBaseline("metric");
        assertEquals(600, baseline.getSampleCount());

        // Recent samples should still be max 500-ish
        int recentSize = baseline.getRecentSamples().size();
        assertTrue(recentSize <= 100);
    }

    @Test
    void testRecordExecutionWithContext_LogsAnomalyOnDeviation() {
        for (int i = 0; i < 35; i++) {
            detector.recordExecution("approval", 200, "approve", "spec-001");
        }

        int beforeCount = detector.getTotalAnomalies();

        // Record 10x outlier
        detector.recordExecution("approval", 2000, "approve", "spec-001");

        int afterCount = detector.getTotalAnomalies();
        assertTrue(afterCount > beforeCount);
    }

    @Test
    void testZeroDuration_EdgeCase() {
        for (int i = 0; i < 35; i++) {
            detector.recordExecution("metric", 100);
        }

        detector.recordExecution("metric", 0);

        // Zero is a valid duration and should be tracked
        AnomalyDetector.MetricBaseline baseline = detector.getBaseline("metric");
        assertTrue(baseline.getSampleCount() > 35);
    }

    @Test
    void testStdDevCalculation() {
        for (int i = 0; i < 40; i++) {
            detector.recordExecution("metric", 100);
        }

        AnomalyDetector.MetricBaseline baseline = detector.getBaseline("metric");
        double stdDev = baseline.getStdDev();

        // Low variance set should have low stddev
        assertTrue(stdDev < 50);
    }

    @Test
    void testMetricName_RequiredNotNull() {
        assertThrows(NullPointerException.class, () -> {
            detector.recordExecution(null, 100, new HashMap<>());
        });
    }

    @Test
    void testContext_RequiredNotNull() {
        assertThrows(NullPointerException.class, () -> {
            detector.recordExecution("metric", 100, null);
        });
    }
}
