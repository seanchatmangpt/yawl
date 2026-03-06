package org.yawlfoundation.yawl.engine.agent.mining;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * A trace represents a single case execution — an ordered sequence of events
 * belonging to one workflow instance (Rust4PM-inspired).
 *
 * Traces are the building blocks of event logs. Process mining algorithms
 * group events by case ID into traces, then analyze the activity sequences.
 *
 * Immutable: events are sorted by timestamp and stored in an unmodifiable list.
 *
 * @param caseId the case identifier for this trace
 * @param events ordered events in this trace (sorted by timestamp)
 */
public record Trace(
    String caseId,
    List<ProcessEvent> events
) {

    /**
     * Compact constructor: validates and sorts events by timestamp.
     */
    public Trace {
        Objects.requireNonNull(caseId, "caseId cannot be null");
        Objects.requireNonNull(events, "events cannot be null");

        List<ProcessEvent> sorted = new ArrayList<>(events);
        sorted.sort(Comparator.comparing(ProcessEvent::timestamp));
        events = Collections.unmodifiableList(sorted);
    }

    /**
     * Get the ordered activity names in this trace.
     * Filters to COMPLETE events only (standard for alpha mining).
     *
     * @return list of activity names in execution order
     */
    public List<String> activities() {
        return events.stream()
            .filter(e -> e.lifecycle() == ProcessEvent.Lifecycle.COMPLETE)
            .map(ProcessEvent::activity)
            .toList();
    }

    /**
     * Get the number of events in this trace.
     */
    public int size() {
        return events.size();
    }

    /**
     * Check if this trace contains a specific activity.
     *
     * @param activity the activity name to check
     * @return true if the activity appears in this trace
     */
    public boolean containsActivity(String activity) {
        return events.stream().anyMatch(e -> e.activity().equals(activity));
    }

    /**
     * Check if activity a directly follows activity b in this trace.
     * Uses COMPLETE events only.
     *
     * @param a the preceding activity
     * @param b the following activity
     * @return true if a directly precedes b at least once
     */
    public boolean directlyFollows(String a, String b) {
        List<String> acts = activities();
        for (int i = 0; i < acts.size() - 1; i++) {
            if (acts.get(i).equals(a) && acts.get(i + 1).equals(b)) {
                return true;
            }
        }
        return false;
    }
}
