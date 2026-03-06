package org.yawlfoundation.yawl.engine.agent.mining;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.engine.agent.WorkflowDef;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * Chicago TDD tests for AlphaDiscovery (Alpha algorithm process discovery).
 */
@DisplayName("Alpha Discovery Tests")
class AlphaDiscoveryTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T1 = T0.plusSeconds(10);
    private static final Instant T2 = T0.plusSeconds(20);
    private static final Instant T3 = T0.plusSeconds(30);

    @Test
    @DisplayName("discovers simple sequential process")
    void discoversSequentialProcess() {
        EventLog log = EventLog.builder()
            .completed("c1", "A", T0)
            .completed("c1", "B", T1)
            .completed("c1", "C", T2)
            .completed("c2", "A", T0)
            .completed("c2", "B", T1)
            .completed("c2", "C", T2)
            .build();

        WorkflowDef model = AlphaDiscovery.from(log)
            .withName("Sequential Process")
            .discover();

        assertThat(model).isNotNull();
        assertThat(model.name()).isEqualTo("Sequential Process");
        assertThat(model.transitions()).isNotEmpty();
        assertThat(model.places()).isNotEmpty();
        // Should have transitions for A, B, C
        assertThat(model.transitions()).hasSize(3);
    }

    @Test
    @DisplayName("discovers parallel activities")
    void discoversParallelActivities() {
        EventLog log = EventLog.builder()
            // Trace 1: A -> B -> C -> D
            .completed("c1", "A", T0)
            .completed("c1", "B", T1)
            .completed("c1", "C", T2)
            .completed("c1", "D", T3)
            // Trace 2: A -> C -> B -> D (B and C are parallel)
            .completed("c2", "A", T0)
            .completed("c2", "C", T1)
            .completed("c2", "B", T2)
            .completed("c2", "D", T3)
            .build();

        WorkflowDef model = AlphaDiscovery.from(log)
            .withName("Parallel Process")
            .discover();

        assertThat(model).isNotNull();
        assertThat(model.transitions()).hasSize(4);
    }

    @Test
    @DisplayName("computes footprint matrix")
    void computesFootprintMatrix() {
        EventLog log = EventLog.builder()
            .completed("c1", "A", T0)
            .completed("c1", "B", T1)
            .completed("c1", "C", T2)
            .completed("c2", "A", T0)
            .completed("c2", "B", T1)
            .completed("c2", "C", T2)
            .build();

        AlphaDiscovery.FootprintMatrix fp = AlphaDiscovery.from(log).footprint();

        assertThat(fp.activities()).containsExactlyInAnyOrder("A", "B", "C");
        // A -> B is causality (A follows B but not vice versa)
        assertThat(fp.relation("A", "B")).isEqualTo(AlphaDiscovery.Relation.CAUSALITY);
        assertThat(fp.relation("B", "A")).isEqualTo(AlphaDiscovery.Relation.INVERSE_CAUSALITY);
        // A # C is choice (no direct following in either direction)
        assertThat(fp.relation("A", "C")).isEqualTo(AlphaDiscovery.Relation.CHOICE);
    }

    @Test
    @DisplayName("footprint matrix renders as string")
    void footprintMatrixRenders() {
        EventLog log = EventLog.builder()
            .completed("c1", "A", T0)
            .completed("c1", "B", T1)
            .build();

        String rendered = AlphaDiscovery.from(log).footprint().render();

        assertThat(rendered).isNotBlank();
        assertThat(rendered).contains("A");
        assertThat(rendered).contains("B");
    }

    @Test
    @DisplayName("rejects empty event log")
    void rejectsEmptyEventLog() {
        EventLog emptyLog = EventLog.builder().build();

        assertThatThrownBy(() -> AlphaDiscovery.from(emptyLog).discover())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("empty");
    }

    @Test
    @DisplayName("identifies start and end activities in discovered model")
    void identifiesStartAndEndActivities() {
        EventLog log = EventLog.builder()
            .completed("c1", "Start", T0)
            .completed("c1", "Process", T1)
            .completed("c1", "End", T2)
            .completed("c2", "Start", T0)
            .completed("c2", "Process", T1)
            .completed("c2", "End", T2)
            .build();

        WorkflowDef model = AlphaDiscovery.from(log)
            .withName("Start-End Process")
            .discover();

        assertThat(model.transitions()).isNotEmpty();
        // End activity should be marked as final
        boolean hasFinal = model.transitions().stream().anyMatch(t -> t.isFinal());
        assertThat(hasFinal).isTrue();
    }
}
