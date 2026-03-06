package org.yawlfoundation.yawl.engine.agent.mining;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.engine.agent.WorkflowBuilder;
import org.yawlfoundation.yawl.engine.agent.WorkflowDef;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * Chicago TDD tests for ConformanceChecker.
 */
@DisplayName("Conformance Checker Tests")
class ConformanceCheckerTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T1 = T0.plusSeconds(10);
    private static final Instant T2 = T0.plusSeconds(20);
    private static final Instant T3 = T0.plusSeconds(30);

    private WorkflowDef buildReferenceModel() {
        return WorkflowBuilder.named("Order Process")
            .place("p0", "Start", 1)
            .place("p1", "Approved")
            .place("p2", "End")
            .transition("t0", "Review", "manual")
            .transition("t1", "Approve", "automatic")
            .finalTransition("t2", "Ship", "service")
            .startAt("p0")
            .build();
    }

    @Test
    @DisplayName("perfect conformance when log matches model")
    void perfectConformance() {
        WorkflowDef model = buildReferenceModel();

        EventLog log = EventLog.builder()
            .completed("c1", "Review", T0)
            .completed("c1", "Approve", T1)
            .completed("c1", "Ship", T2)
            .build();

        ConformanceChecker.ConformanceResult result = ConformanceChecker.check(log)
            .against(model)
            .run();

        assertThat(result.fitness()).isGreaterThan(0.0);
        // No unexpected activities
        assertThat(result.deviationsOfType(ConformanceChecker.DeviationType.UNEXPECTED_ACTIVITY))
            .isEmpty();
    }

    @Test
    @DisplayName("detects unexpected activities")
    void detectsUnexpectedActivities() {
        WorkflowDef model = buildReferenceModel();

        EventLog log = EventLog.builder()
            .completed("c1", "Review", T0)
            .completed("c1", "HackSystem", T1)
            .completed("c1", "Ship", T2)
            .build();

        ConformanceChecker.ConformanceResult result = ConformanceChecker.check(log)
            .against(model)
            .run();

        assertThat(result.deviationsOfType(ConformanceChecker.DeviationType.UNEXPECTED_ACTIVITY))
            .isNotEmpty()
            .anyMatch(d -> d.activity().equals("HackSystem"));
    }

    @Test
    @DisplayName("detects missing activities")
    void detectsMissingActivities() {
        WorkflowDef model = buildReferenceModel();

        // Log only has "Review" — missing "Approve" and "Ship"
        EventLog log = EventLog.builder()
            .completed("c1", "Review", T0)
            .build();

        ConformanceChecker.ConformanceResult result = ConformanceChecker.check(log)
            .against(model)
            .run();

        assertThat(result.deviationsOfType(ConformanceChecker.DeviationType.MISSING_ACTIVITY))
            .isNotEmpty();
    }

    @Test
    @DisplayName("fitness decreases with more deviations")
    void fitnessDecreasesWithDeviations() {
        WorkflowDef model = buildReferenceModel();

        // Perfect log
        EventLog perfectLog = EventLog.builder()
            .completed("c1", "Review", T0)
            .completed("c1", "Approve", T1)
            .completed("c1", "Ship", T2)
            .build();

        // Deviant log (extra activities)
        EventLog deviantLog = EventLog.builder()
            .completed("c1", "X", T0)
            .completed("c1", "Y", T1)
            .completed("c1", "Z", T2)
            .build();

        double perfectFitness = ConformanceChecker.check(perfectLog).against(model).run().fitness();
        double deviantFitness = ConformanceChecker.check(deviantLog).against(model).run().fitness();

        assertThat(perfectFitness).isGreaterThan(deviantFitness);
    }

    @Test
    @DisplayName("handles multiple traces")
    void handlesMultipleTraces() {
        WorkflowDef model = buildReferenceModel();

        EventLog log = EventLog.builder()
            .completed("c1", "Review", T0)
            .completed("c1", "Approve", T1)
            .completed("c1", "Ship", T2)
            .completed("c2", "Review", T0)
            .completed("c2", "Approve", T1)
            .completed("c2", "Ship", T2)
            .build();

        ConformanceChecker.ConformanceResult result = ConformanceChecker.check(log)
            .against(model)
            .run();

        assertThat(result.traceCount()).isEqualTo(2);
        assertThat(result.eventCount()).isEqualTo(6);
    }

    @Test
    @DisplayName("rejects run without model")
    void rejectsRunWithoutModel() {
        EventLog log = EventLog.builder()
            .completed("c1", "A", T0)
            .build();

        assertThatThrownBy(() -> ConformanceChecker.check(log).run())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("model must be set");
    }

    @Test
    @DisplayName("empty log has perfect fitness")
    void emptyLogHasPerfectFitness() {
        WorkflowDef model = buildReferenceModel();

        EventLog log = EventLog.builder().build();

        ConformanceChecker.ConformanceResult result = ConformanceChecker.check(log)
            .against(model)
            .run();

        assertThat(result.fitness()).isEqualTo(1.0);
    }
}
