package org.yawlfoundation.yawl.engine.agent.mining;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.engine.agent.WorkflowBuilder;
import org.yawlfoundation.yawl.engine.agent.WorkflowDef;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * Chicago TDD tests for ProcessMiningPipeline fluent API.
 */
@DisplayName("Process Mining Pipeline Tests")
class ProcessMiningPipelineTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T1 = T0.plusSeconds(10);
    private static final Instant T2 = T0.plusSeconds(20);
    private static final Instant T3 = T0.plusSeconds(30);

    private EventLog buildSampleLog() {
        return EventLog.builder()
            .completed("c1", "A", T0)
            .completed("c1", "B", T1)
            .completed("c1", "C", T2)
            .completed("c2", "A", T0)
            .completed("c2", "B", T1)
            .completed("c2", "C", T2)
            .completed("c3", "A", T0)
            .completed("c3", "admin-task", T1)
            .completed("c3", "C", T2)
            .build();
    }

    @Test
    @DisplayName("discovers model from pipeline")
    void discoversModelFromPipeline() {
        EventLog log = buildSampleLog();

        WorkflowDef model = ProcessMiningPipeline.fromLog(log)
            .discover("Test Process");

        assertThat(model).isNotNull();
        assertThat(model.name()).isEqualTo("Test Process");
        assertThat(model.transitions()).isNotEmpty();
    }

    @Test
    @DisplayName("filters activities in pipeline")
    void filtersActivitiesInPipeline() {
        EventLog log = buildSampleLog();

        EventLog filtered = ProcessMiningPipeline.fromLog(log)
            .filterActivities(a -> !a.equals("admin-task"))
            .materialize();

        assertThat(filtered.activities()).doesNotContain("admin-task");
        assertThat(filtered.activities()).contains("A", "B", "C");
    }

    @Test
    @DisplayName("filters cases in pipeline")
    void filtersCasesInPipeline() {
        EventLog log = buildSampleLog();

        EventLog filtered = ProcessMiningPipeline.fromLog(log)
            .filterCases(caseId -> !caseId.equals("c3"))
            .materialize();

        assertThat(filtered.traceCount()).isEqualTo(2);
        assertThat(filtered.caseIds()).doesNotContain("c3");
    }

    @Test
    @DisplayName("chains multiple filters")
    void chainsMultipleFilters() {
        EventLog log = buildSampleLog();

        EventLog filtered = ProcessMiningPipeline.fromLog(log)
            .filterActivities(a -> !a.equals("admin-task"))
            .filterCases(caseId -> !caseId.equals("c3"))
            .materialize();

        assertThat(filtered.traceCount()).isEqualTo(2);
        assertThat(filtered.activities()).doesNotContain("admin-task");
    }

    @Test
    @DisplayName("computes footprint from pipeline")
    void computesFootprintFromPipeline() {
        EventLog log = EventLog.builder()
            .completed("c1", "A", T0)
            .completed("c1", "B", T1)
            .completed("c2", "A", T0)
            .completed("c2", "B", T1)
            .build();

        AlphaDiscovery.FootprintMatrix fp = ProcessMiningPipeline.fromLog(log)
            .footprint();

        assertThat(fp.activities()).containsExactlyInAnyOrder("A", "B");
        assertThat(fp.relation("A", "B")).isEqualTo(AlphaDiscovery.Relation.CAUSALITY);
    }

    @Test
    @DisplayName("checks conformance from pipeline")
    void checksConformanceFromPipeline() {
        WorkflowDef model = WorkflowBuilder.named("Ref Process")
            .place("p0", "Start", 1)
            .place("p1", "End")
            .transition("t0", "A", "automatic")
            .transition("t1", "B", "automatic")
            .finalTransition("t2", "C", "automatic")
            .startAt("p0")
            .build();

        EventLog log = EventLog.builder()
            .completed("c1", "A", T0)
            .completed("c1", "B", T1)
            .completed("c1", "C", T2)
            .build();

        ConformanceChecker.ConformanceResult result = ProcessMiningPipeline.fromLog(log)
            .checkConformance(model);

        assertThat(result.fitness()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("summarizes log statistics")
    void summarizesLogStatistics() {
        EventLog log = buildSampleLog();

        ProcessMiningPipeline.LogSummary summary = ProcessMiningPipeline.fromLog(log)
            .summarize();

        assertThat(summary.eventCount()).isEqualTo(9);
        assertThat(summary.traceCount()).isEqualTo(3);
        assertThat(summary.activityCount()).isEqualTo(4); // A, B, C, admin-task
    }

    @Test
    @DisplayName("filters lifecycle events")
    void filtersLifecycleEvents() {
        EventLog log = EventLog.builder()
            .event(ProcessEvent.started("c1", "A", T0))
            .event(ProcessEvent.completed("c1", "A", T1))
            .event(ProcessEvent.started("c1", "B", T2))
            .event(ProcessEvent.completed("c1", "B", T3))
            .build();

        EventLog filtered = ProcessMiningPipeline.fromLog(log)
            .filterLifecycle(ProcessEvent.Lifecycle.COMPLETE)
            .materialize();

        assertThat(filtered.eventCount()).isEqualTo(2);
        assertThat(filtered.events()).allMatch(e ->
            e.lifecycle() == ProcessEvent.Lifecycle.COMPLETE);
    }

    @Test
    @DisplayName("pipeline is immutable (returns new instance)")
    void pipelineIsImmutable() {
        EventLog log = buildSampleLog();

        ProcessMiningPipeline base = ProcessMiningPipeline.fromLog(log);
        ProcessMiningPipeline filtered = base.filterActivities(a -> !a.equals("admin-task"));

        // Base should still have admin-task
        EventLog baseMaterialized = base.materialize();
        assertThat(baseMaterialized.activities()).contains("admin-task");

        // Filtered should not
        EventLog filteredMaterialized = filtered.materialize();
        assertThat(filteredMaterialized.activities()).doesNotContain("admin-task");
    }
}
