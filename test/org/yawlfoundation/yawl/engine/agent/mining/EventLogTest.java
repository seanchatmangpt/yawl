package org.yawlfoundation.yawl.engine.agent.mining;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Chicago TDD tests for EventLog, Trace, and ProcessEvent.
 */
@DisplayName("EventLog and Trace Tests")
class EventLogTest {

    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T1 = T0.plusSeconds(10);
    private static final Instant T2 = T0.plusSeconds(20);
    private static final Instant T3 = T0.plusSeconds(30);
    private static final Instant T4 = T0.plusSeconds(40);

    @Test
    @DisplayName("builds event log with builder pattern")
    void buildsEventLogWithBuilder() {
        EventLog log = EventLog.builder()
            .completed("case-1", "A", T0)
            .completed("case-1", "B", T1)
            .completed("case-1", "C", T2)
            .completed("case-2", "A", T0)
            .completed("case-2", "B", T1)
            .build();

        assertThat(log.eventCount()).isEqualTo(5);
        assertThat(log.traceCount()).isEqualTo(2);
        assertThat(log.activities()).containsExactlyInAnyOrder("A", "B", "C");
        assertThat(log.caseIds()).containsExactlyInAnyOrder("case-1", "case-2");
    }

    @Test
    @DisplayName("groups events into traces by case ID")
    void groupsEventsIntoTraces() {
        EventLog log = EventLog.builder()
            .completed("case-1", "A", T0)
            .completed("case-2", "X", T0)
            .completed("case-1", "B", T1)
            .completed("case-2", "Y", T1)
            .build();

        Trace trace1 = log.trace("case-1");
        assertThat(trace1).isNotNull();
        assertThat(trace1.activities()).containsExactly("A", "B");

        Trace trace2 = log.trace("case-2");
        assertThat(trace2).isNotNull();
        assertThat(trace2.activities()).containsExactly("X", "Y");
    }

    @Test
    @DisplayName("sorts events within trace by timestamp")
    void sortsEventsByTimestamp() {
        EventLog log = EventLog.builder()
            .completed("case-1", "C", T2)
            .completed("case-1", "A", T0)
            .completed("case-1", "B", T1)
            .build();

        assertThat(log.trace("case-1").activities()).containsExactly("A", "B", "C");
    }

    @Test
    @DisplayName("computes directly-follows relation")
    void computesDirectlyFollowsRelation() {
        EventLog log = EventLog.builder()
            .completed("case-1", "A", T0)
            .completed("case-1", "B", T1)
            .completed("case-1", "C", T2)
            .completed("case-2", "A", T0)
            .completed("case-2", "B", T1)
            .completed("case-2", "C", T2)
            .build();

        Map<EventLog.ActivityPair, Integer> df = log.directlyFollowsRelation();

        assertThat(df.get(new EventLog.ActivityPair("A", "B"))).isEqualTo(2);
        assertThat(df.get(new EventLog.ActivityPair("B", "C"))).isEqualTo(2);
        assertThat(df.get(new EventLog.ActivityPair("A", "C"))).isNull();
    }

    @Test
    @DisplayName("trace detects directly-follows between activities")
    void traceDirectlyFollows() {
        Trace trace = new Trace("case-1", List.of(
            ProcessEvent.completed("case-1", "A", T0),
            ProcessEvent.completed("case-1", "B", T1),
            ProcessEvent.completed("case-1", "C", T2)
        ));

        assertThat(trace.directlyFollows("A", "B")).isTrue();
        assertThat(trace.directlyFollows("B", "C")).isTrue();
        assertThat(trace.directlyFollows("A", "C")).isFalse();
        assertThat(trace.directlyFollows("C", "A")).isFalse();
    }

    @Test
    @DisplayName("trace contains activity check")
    void traceContainsActivity() {
        Trace trace = new Trace("case-1", List.of(
            ProcessEvent.completed("case-1", "A", T0),
            ProcessEvent.completed("case-1", "B", T1)
        ));

        assertThat(trace.containsActivity("A")).isTrue();
        assertThat(trace.containsActivity("Z")).isFalse();
    }

    @Test
    @DisplayName("ProcessEvent factory methods")
    void processEventFactories() {
        ProcessEvent completed = ProcessEvent.completed("c1", "act", T0);
        assertThat(completed.lifecycle()).isEqualTo(ProcessEvent.Lifecycle.COMPLETE);

        ProcessEvent started = ProcessEvent.started("c1", "act", T0);
        assertThat(started.lifecycle()).isEqualTo(ProcessEvent.Lifecycle.START);
    }

    @Test
    @DisplayName("rejects null fields in ProcessEvent")
    void rejectsNullsInProcessEvent() {
        assertThatThrownBy(() -> new ProcessEvent(null, "A", T0, ProcessEvent.Lifecycle.COMPLETE))
            .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new ProcessEvent("c1", null, T0, ProcessEvent.Lifecycle.COMPLETE))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("creates event log from list via of()")
    void createsFromList() {
        List<ProcessEvent> events = List.of(
            ProcessEvent.completed("c1", "A", T0),
            ProcessEvent.completed("c1", "B", T1)
        );

        EventLog log = EventLog.of(events);
        assertThat(log.eventCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("empty event log has zero counts")
    void emptyEventLog() {
        EventLog log = EventLog.builder().build();
        assertThat(log.eventCount()).isZero();
        assertThat(log.traceCount()).isZero();
        assertThat(log.activities()).isEmpty();
    }

    @Test
    @DisplayName("mergeFrom combines two logs")
    void mergeFromCombinesLogs() {
        EventLog log1 = EventLog.builder()
            .completed("c1", "A", T0)
            .build();

        EventLog log2 = EventLog.builder()
            .completed("c2", "B", T1)
            .mergeFrom(log1)
            .build();

        assertThat(log2.eventCount()).isEqualTo(2);
        assertThat(log2.traceCount()).isEqualTo(2);
    }
}
