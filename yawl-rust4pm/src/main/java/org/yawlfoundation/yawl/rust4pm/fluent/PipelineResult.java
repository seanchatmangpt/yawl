package org.yawlfoundation.yawl.rust4pm.fluent;

import org.yawlfoundation.yawl.rust4pm.model.ConformanceReport;
import org.yawlfoundation.yawl.rust4pm.model.DirectlyFollowsGraph;
import org.yawlfoundation.yawl.rust4pm.model.PerformanceStats;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable result of a process mining pipeline execution.
 *
 * <p>Contains all artifacts produced by each pipeline stage, plus
 * execution metadata (timing, stage outcomes). Stages that were not
 * executed or were skipped produce {@link Optional#empty()}.
 *
 * @param dfg              DFG discovered (empty if stage not executed)
 * @param conformance      conformance report (empty if stage not executed)
 * @param performanceStats performance statistics (empty if stage not executed)
 * @param stageOutcomes    outcome of each stage in execution order
 * @param totalDuration    wall-clock duration of the entire pipeline
 * @param startedAt        pipeline start timestamp
 */
public record PipelineResult(
    Optional<DirectlyFollowsGraph> dfg,
    Optional<ConformanceReport> conformance,
    Optional<PerformanceStats> performanceStats,
    List<StageOutcome> stageOutcomes,
    Duration totalDuration,
    Instant startedAt
) {

    public PipelineResult {
        Objects.requireNonNull(dfg, "dfg must not be null");
        Objects.requireNonNull(conformance, "conformance must not be null");
        Objects.requireNonNull(performanceStats, "performanceStats must not be null");
        Objects.requireNonNull(stageOutcomes, "stageOutcomes must not be null");
        Objects.requireNonNull(totalDuration, "totalDuration must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        stageOutcomes = List.copyOf(stageOutcomes);
    }

    /**
     * True if all stages completed successfully (no failures or skips).
     */
    public boolean isFullySuccessful() {
        return stageOutcomes.stream().allMatch(o -> o.status() == StageStatus.SUCCESS);
    }

    /**
     * True if any stage failed (regardless of skip/retry).
     */
    public boolean hasFailures() {
        return stageOutcomes.stream().anyMatch(o -> o.status() == StageStatus.FAILED);
    }

    /**
     * Number of stages that completed successfully.
     */
    public long successCount() {
        return stageOutcomes.stream().filter(o -> o.status() == StageStatus.SUCCESS).count();
    }

    /**
     * Outcome of a single pipeline stage execution.
     *
     * @param stage    the stage that was executed
     * @param status   result status
     * @param duration time taken by this stage
     * @param error    exception if failed (null if successful)
     * @param retries  number of retry attempts (0 if no retries needed)
     */
    public record StageOutcome(
        PipelineStage stage,
        StageStatus status,
        Duration duration,
        Throwable error,
        int retries
    ) {
        public StageOutcome {
            Objects.requireNonNull(stage, "stage must not be null");
            Objects.requireNonNull(status, "status must not be null");
            Objects.requireNonNull(duration, "duration must not be null");
        }
    }

    /**
     * Status of a pipeline stage execution.
     */
    public enum StageStatus {
        /** Stage completed successfully. */
        SUCCESS,
        /** Stage failed (exception thrown). */
        FAILED,
        /** Stage was skipped (due to SKIP supervision policy). */
        SKIPPED
    }
}
