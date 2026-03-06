package org.yawlfoundation.yawl.rust4pm.fluent;

import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.rust4pm.model.ConformanceReport;
import org.yawlfoundation.yawl.rust4pm.model.DirectlyFollowsGraph;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PipelineResult} and {@link PipelineResult.StageOutcome}.
 */
class PipelineResultTest {

    @Test
    void fullySuccessfulWhenAllStagesSucceed() {
        var outcome = new PipelineResult.StageOutcome(
            new PipelineStage.ParseOcel2("{\"events\":[]}"),
            PipelineResult.StageStatus.SUCCESS,
            Duration.ofMillis(10), null, 0);

        var result = new PipelineResult(
            Optional.empty(), Optional.empty(), Optional.empty(),
            List.of(outcome), Duration.ofMillis(10), Instant.now());

        assertTrue(result.isFullySuccessful());
        assertFalse(result.hasFailures());
        assertEquals(1, result.successCount());
    }

    @Test
    void hasFailuresWhenStagesFailed() {
        var failedOutcome = new PipelineResult.StageOutcome(
            new PipelineStage.DiscoverDfg(),
            PipelineResult.StageStatus.FAILED,
            Duration.ofMillis(5),
            new RuntimeException("native lib not loaded"), 0);

        var result = new PipelineResult(
            Optional.empty(), Optional.empty(), Optional.empty(),
            List.of(failedOutcome), Duration.ofMillis(5), Instant.now());

        assertFalse(result.isFullySuccessful());
        assertTrue(result.hasFailures());
        assertEquals(0, result.successCount());
    }

    @Test
    void skippedStagesAreNotSuccessful() {
        var skipped = new PipelineResult.StageOutcome(
            new PipelineStage.ComputeStats(),
            PipelineResult.StageStatus.SKIPPED,
            Duration.ofMillis(1), null, 0);

        var result = new PipelineResult(
            Optional.empty(), Optional.empty(), Optional.empty(),
            List.of(skipped), Duration.ofMillis(1), Instant.now());

        assertFalse(result.isFullySuccessful());
        assertFalse(result.hasFailures());
        assertEquals(0, result.successCount());
    }

    @Test
    void mixedOutcomes() {
        var success = new PipelineResult.StageOutcome(
            new PipelineStage.ParseOcel2("{\"events\":[]}"),
            PipelineResult.StageStatus.SUCCESS,
            Duration.ofMillis(10), null, 0);
        var skipped = new PipelineResult.StageOutcome(
            new PipelineStage.ComputeStats(),
            PipelineResult.StageStatus.SKIPPED,
            Duration.ofMillis(1), null, 0);
        var failed = new PipelineResult.StageOutcome(
            new PipelineStage.DiscoverDfg(),
            PipelineResult.StageStatus.FAILED,
            Duration.ofMillis(5),
            new RuntimeException("failed"), 2);

        var result = new PipelineResult(
            Optional.empty(), Optional.empty(), Optional.empty(),
            List.of(success, skipped, failed), Duration.ofMillis(16), Instant.now());

        assertFalse(result.isFullySuccessful());
        assertTrue(result.hasFailures());
        assertEquals(1, result.successCount());
        assertEquals(3, result.stageOutcomes().size());
    }

    @Test
    void stageOutcomeRecordsRetryCount() {
        var outcome = new PipelineResult.StageOutcome(
            new PipelineStage.DiscoverDfg(),
            PipelineResult.StageStatus.SUCCESS,
            Duration.ofMillis(50), null, 3);

        assertEquals(3, outcome.retries());
        assertEquals(PipelineResult.StageStatus.SUCCESS, outcome.status());
    }

    @Test
    void stageOutcomesListIsImmutable() {
        var outcome = new PipelineResult.StageOutcome(
            new PipelineStage.ParseOcel2("{\"events\":[]}"),
            PipelineResult.StageStatus.SUCCESS,
            Duration.ofMillis(1), null, 0);

        var result = new PipelineResult(
            Optional.empty(), Optional.empty(), Optional.empty(),
            List.of(outcome), Duration.ofMillis(1), Instant.now());

        assertThrows(UnsupportedOperationException.class,
            () -> result.stageOutcomes().add(outcome));
    }

    @Test
    void dfgAndConformanceArePreserved() {
        var dfg = new DirectlyFollowsGraph(List.of(), List.of());
        var conformance = new ConformanceReport(0.95, 0.88, 100, null);

        var result = new PipelineResult(
            Optional.of(dfg), Optional.of(conformance), Optional.empty(),
            List.of(), Duration.ofMillis(1), Instant.now());

        assertTrue(result.dfg().isPresent());
        assertTrue(result.conformance().isPresent());
        assertTrue(result.performanceStats().isEmpty());
        assertEquals(0.95, result.conformance().get().fitness());
    }

    @Test
    void nullFieldsThrow() {
        assertThrows(NullPointerException.class,
            () -> new PipelineResult(null, Optional.empty(), Optional.empty(),
                List.of(), Duration.ZERO, Instant.now()));
    }
}
