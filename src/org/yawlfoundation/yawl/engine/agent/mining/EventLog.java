package org.yawlfoundation.yawl.engine.agent.mining;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An event log is an ordered collection of process events grouped into traces
 * (Rust4PM-inspired).
 *
 * The event log is the primary input for process mining algorithms:
 * - Alpha algorithm (process discovery)
 * - Conformance checking (model vs execution)
 * - Performance analysis (bottleneck detection)
 *
 * Provides fluent construction via {@link #builder()}.
 *
 * Immutable after construction. Thread-safe.
 */
public final class EventLog {

    private final List<ProcessEvent> events;
    private final Map<String, Trace> traces;

    private EventLog(List<ProcessEvent> events) {
        this.events = Collections.unmodifiableList(
            events.stream()
                .sorted(Comparator.comparing(ProcessEvent::timestamp))
                .toList()
        );

        Map<String, List<ProcessEvent>> grouped = new LinkedHashMap<>();
        for (ProcessEvent event : this.events) {
            grouped.computeIfAbsent(event.caseId(), k -> new ArrayList<>()).add(event);
        }

        Map<String, Trace> traceMap = new LinkedHashMap<>();
        for (var entry : grouped.entrySet()) {
            traceMap.put(entry.getKey(), new Trace(entry.getKey(), entry.getValue()));
        }
        this.traces = Collections.unmodifiableMap(traceMap);
    }

    /**
     * Create a new event log builder.
     *
     * @return a fluent builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create an event log from a list of events.
     *
     * @param events the raw events
     * @return a new EventLog
     */
    public static EventLog of(List<ProcessEvent> events) {
        return new EventLog(new ArrayList<>(Objects.requireNonNull(events)));
    }

    /**
     * Get all events in timestamp order.
     */
    public List<ProcessEvent> events() {
        return events;
    }

    /**
     * Get all traces (grouped by case ID, ordered by first event timestamp).
     */
    public List<Trace> traces() {
        return List.copyOf(traces.values());
    }

    /**
     * Get a specific trace by case ID.
     *
     * @param caseId the case identifier
     * @return the trace, or null if not found
     */
    public Trace trace(String caseId) {
        return traces.get(caseId);
    }

    /**
     * Get all unique activity names in the log.
     *
     * @return set of activity names
     */
    public Set<String> activities() {
        return events.stream()
            .map(ProcessEvent::activity)
            .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Get all unique case IDs in the log.
     *
     * @return set of case IDs
     */
    public Set<String> caseIds() {
        return Set.copyOf(traces.keySet());
    }

    /**
     * Get the number of events in this log.
     */
    public int eventCount() {
        return events.size();
    }

    /**
     * Get the number of traces (cases) in this log.
     */
    public int traceCount() {
        return traces.size();
    }

    /**
     * Compute the directly-follows relation for this log.
     * For each pair (a, b), counts how many times activity a directly precedes b.
     *
     * This is the fundamental relation for the Alpha algorithm.
     *
     * @return map from (a, b) pair to frequency count
     */
    public Map<ActivityPair, Integer> directlyFollowsRelation() {
        Map<ActivityPair, Integer> relation = new LinkedHashMap<>();
        for (Trace trace : traces.values()) {
            List<String> acts = trace.activities();
            for (int i = 0; i < acts.size() - 1; i++) {
                ActivityPair pair = new ActivityPair(acts.get(i), acts.get(i + 1));
                relation.merge(pair, 1, Integer::sum);
            }
        }
        return Collections.unmodifiableMap(relation);
    }

    /**
     * Represents a pair of activities for the directly-follows relation.
     *
     * @param from the preceding activity
     * @param to   the following activity
     */
    public record ActivityPair(String from, String to) {
        public ActivityPair {
            Objects.requireNonNull(from, "from cannot be null");
            Objects.requireNonNull(to, "to cannot be null");
        }
    }

    @Override
    public String toString() {
        return String.format("EventLog{events=%d, traces=%d, activities=%d}",
            events.size(), traces.size(), activities().size());
    }

    /**
     * Fluent builder for EventLog.
     */
    public static final class Builder {

        private final List<ProcessEvent> events = new ArrayList<>();

        private Builder() {}

        /**
         * Add a single event.
         *
         * @param event the event to add
         * @return this builder
         */
        public Builder event(ProcessEvent event) {
            events.add(Objects.requireNonNull(event, "event cannot be null"));
            return this;
        }

        /**
         * Add a COMPLETE event (convenience method).
         *
         * @param caseId   case identifier
         * @param activity activity name
         * @param timestamp when the activity completed
         * @return this builder
         */
        public Builder completed(String caseId, String activity, java.time.Instant timestamp) {
            return event(ProcessEvent.completed(caseId, activity, timestamp));
        }

        /**
         * Add all events from another log.
         *
         * @param other the event log to merge
         * @return this builder
         */
        public Builder mergeFrom(EventLog other) {
            events.addAll(Objects.requireNonNull(other).events());
            return this;
        }

        /**
         * Build the immutable EventLog.
         *
         * @return a new EventLog
         */
        public EventLog build() {
            return new EventLog(events);
        }
    }
}
