package org.yawlfoundation.yawl.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD: real Micrometer registry, no mocks.
 * Tests BlackSwanDetector with all three detection strategies.
 */
class BlackSwanDetectorTest {

    private BlackSwanDetector detector;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        // null AndonCord = log-only mode, avoids external HTTP calls in tests
        detector = new BlackSwanDetector(meterRegistry, null);
    }

    // -------------------------------------------------------------------------
    // Extreme Outlier tests
    // -------------------------------------------------------------------------

    @Test
    void testExtremeOutlier_AboveThreshold_FiresEvent() {
        Optional<BlackSwanEvent> result = detector.recordAnomaly(
                "task.duration", 5.1, 10000L, 100.0, "case-1");

        assertTrue(result.isPresent());
        assertInstanceOf(BlackSwanEvent.ExtremeOutlier.class, result.get());

        BlackSwanEvent.ExtremeOutlier outlier = (BlackSwanEvent.ExtremeOutlier) result.get();
        assertEquals("task.duration", outlier.metric());
        assertEquals(5.1, outlier.sigmaLevel(), 0.001);
        assertEquals(10000L, outlier.observedMs());
        assertEquals(100.0, outlier.meanMs(), 0.001);
        assertNotNull(outlier.detectedAt());
    }

    @Test
    void testExtremeOutlier_BelowThreshold_NoEvent() {
        Optional<BlackSwanEvent> result = detector.recordAnomaly(
                "task.duration", 4.9, 5000L, 100.0, "case-1");

        assertFalse(result.isPresent());
    }

    @Test
    void testExtremeOutlier_AtExactThreshold_FiresEvent() {
        Optional<BlackSwanEvent> result = detector.recordAnomaly(
                "task.duration", 5.0, 5000L, 100.0, "case-1");

        assertTrue(result.isPresent());
        assertInstanceOf(BlackSwanEvent.ExtremeOutlier.class, result.get());
    }

    // -------------------------------------------------------------------------
    // Anomaly Storm tests
    // -------------------------------------------------------------------------

    @Test
    void testAnomalyStorm_BurstTriggered() {
        // 5 anomalies with σ < threshold so no ExtremeOutlier fires first
        // Use same case so no systemic (only 1 case)
        Optional<BlackSwanEvent> last = Optional.empty();
        for (int i = 0; i < BlackSwanDetector.STORM_ANOMALY_COUNT; i++) {
            last = detector.recordAnomaly("metric." + i, 3.0, 500L, 100.0, "case-1");
        }

        assertTrue(last.isPresent());
        assertInstanceOf(BlackSwanEvent.AnomalyStorm.class, last.get());

        BlackSwanEvent.AnomalyStorm storm = (BlackSwanEvent.AnomalyStorm) last.get();
        assertEquals(BlackSwanDetector.STORM_ANOMALY_COUNT, storm.anomalyCount());
        assertEquals(BlackSwanDetector.STORM_WINDOW_SECONDS, storm.windowSeconds());
        assertFalse(storm.affectedMetrics().isEmpty());
        assertNotNull(storm.detectedAt());
    }

    @Test
    void testAnomalyStorm_FourAnomalies_NoEvent() {
        for (int i = 0; i < BlackSwanDetector.STORM_ANOMALY_COUNT - 1; i++) {
            Optional<BlackSwanEvent> result = detector.recordAnomaly(
                    "metric." + i, 3.0, 500L, 100.0, "case-1");
            assertFalse(result.isPresent(),
                    "Should not trigger storm before threshold at i=" + i);
        }
    }

    // -------------------------------------------------------------------------
    // Systemic Failure tests
    // -------------------------------------------------------------------------

    @Test
    void testSystemicFailure_MultiCaseDeviation() {
        // Same metric, 3 different cases, σ < extreme threshold
        detector.recordAnomaly("case.duration", 3.0, 5000L, 100.0, "case-A");
        detector.recordAnomaly("case.duration", 3.0, 5000L, 100.0, "case-B");
        Optional<BlackSwanEvent> result =
                detector.recordAnomaly("case.duration", 3.0, 5000L, 100.0, "case-C");

        assertTrue(result.isPresent());
        assertInstanceOf(BlackSwanEvent.SystemicFailure.class, result.get());

        BlackSwanEvent.SystemicFailure failure = (BlackSwanEvent.SystemicFailure) result.get();
        assertEquals("case.duration", failure.metric());
        assertEquals(BlackSwanDetector.SYSTEMIC_CASE_COUNT, failure.affectedCases());
        assertTrue(failure.meanSigmaAcrossCases() > 0);
        assertNotNull(failure.detectedAt());
    }

    @Test
    void testSystemicFailure_TwoCasesOnly_NoEvent() {
        // Use a fresh detector to avoid storm contamination from other tests
        BlackSwanDetector isolated = new BlackSwanDetector(meterRegistry, null);
        isolated.recordAnomaly("case.duration", 3.0, 5000L, 100.0, "case-A");
        Optional<BlackSwanEvent> result =
                isolated.recordAnomaly("case.duration", 3.0, 5000L, 100.0, "case-B");

        // 2 cases is below SYSTEMIC_CASE_COUNT=3 and no storm yet (only 2 entries)
        assertFalse(result.isPresent());
    }

    // -------------------------------------------------------------------------
    // clearCase tests
    // -------------------------------------------------------------------------

    @Test
    void testClearCase_RemovesFromSystemicTracking() {
        detector.recordAnomaly("task.duration", 3.0, 500L, 100.0, "case-A");
        detector.recordAnomaly("task.duration", 3.0, 500L, 100.0, "case-B");
        detector.recordAnomaly("task.duration", 3.0, 500L, 100.0, "case-C");

        // All 3 cases tracked
        assertEquals(3, detector.getDeviatingCaseCount("task.duration"));

        // Clear one case
        detector.clearCase("case-A");
        assertEquals(2, detector.getDeviatingCaseCount("task.duration"));
    }

    @Test
    void testNullCaseId_IgnoredInSystemicDetection() {
        // null caseId — should not throw and should not count toward systemic
        Optional<BlackSwanEvent> result =
                detector.recordAnomaly("task.duration", 3.0, 500L, 100.0, null);

        // No systemic tracking, no storm (only 1 entry), no extreme (σ=3.0)
        assertFalse(result.isPresent());
        assertEquals(0, detector.getDeviatingCaseCount("task.duration"));
    }

    // -------------------------------------------------------------------------
    // Metrics tests
    // -------------------------------------------------------------------------

    @Test
    void testMetricCounter_IncrementedOnBlackSwan() {
        // Trigger an extreme outlier
        detector.recordAnomaly("task.duration", 6.0, 60000L, 100.0, "case-1");

        // Verify counter was incremented
        Counter counter = meterRegistry.find("yawl.blackswan.detected")
                .tag("type", "extreme_outlier")
                .tag("metric", "task.duration")
                .counter();

        assertNotNull(counter, "yawl.blackswan.detected counter must be registered");
        assertEquals(1.0, counter.count(), 0.001);
    }

    // -------------------------------------------------------------------------
    // Impact score tests
    // -------------------------------------------------------------------------

    @Test
    void testImpactScore_ExtremeOutlier_ScalesWithSigma() {
        // σ=5.0 → score should be 60 (baseline for threshold)
        BlackSwanEvent.ExtremeOutlier at5 = new BlackSwanEvent.ExtremeOutlier(
                "m", 5.0, 100L, 10.0, java.time.Instant.now());
        assertEquals(60, at5.impactScore());

        // σ=10.0 → score should cap at 100
        BlackSwanEvent.ExtremeOutlier at10 = new BlackSwanEvent.ExtremeOutlier(
                "m", 10.0, 1000L, 10.0, java.time.Instant.now());
        assertEquals(100, at10.impactScore());

        // σ=6.0 → score should be 68
        BlackSwanEvent.ExtremeOutlier at6 = new BlackSwanEvent.ExtremeOutlier(
                "m", 6.0, 200L, 10.0, java.time.Instant.now());
        assertEquals(68, at6.impactScore());
    }
}
