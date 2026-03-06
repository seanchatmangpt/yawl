package org.yawlfoundation.yawl.engine.agent.mining;

import java.time.Instant;

/**
 * A single event in a process execution log (Rust4PM-inspired).
 *
 * Represents an atomic occurrence: an activity started, completed, or was skipped
 * within a specific case (workflow instance). Events are the fundamental unit
 * of process mining — all discovery and conformance algorithms operate on events.
 *
 * Immutable record — safe to share across threads and store in concurrent collections.
 *
 * @param caseId     identifies the workflow instance (case/trace)
 * @param activity   the activity/task name that occurred
 * @param timestamp  when the event occurred (UTC)
 * @param lifecycle  event lifecycle (START, COMPLETE, SKIP)
 */
public record ProcessEvent(
    String caseId,
    String activity,
    Instant timestamp,
    Lifecycle lifecycle
) {

    /**
     * Compact constructor with null checks.
     */
    public ProcessEvent {
        if (caseId == null) throw new NullPointerException("caseId cannot be null");
        if (activity == null) throw new NullPointerException("activity cannot be null");
        if (timestamp == null) throw new NullPointerException("timestamp cannot be null");
        if (lifecycle == null) throw new NullPointerException("lifecycle cannot be null");
    }

    /**
     * Convenience factory for a COMPLETE event (most common in process mining).
     *
     * @param caseId   case identifier
     * @param activity activity name
     * @param timestamp when the activity completed
     * @return a new ProcessEvent with COMPLETE lifecycle
     */
    public static ProcessEvent completed(String caseId, String activity, Instant timestamp) {
        return new ProcessEvent(caseId, activity, timestamp, Lifecycle.COMPLETE);
    }

    /**
     * Convenience factory for a START event.
     *
     * @param caseId   case identifier
     * @param activity activity name
     * @param timestamp when the activity started
     * @return a new ProcessEvent with START lifecycle
     */
    public static ProcessEvent started(String caseId, String activity, Instant timestamp) {
        return new ProcessEvent(caseId, activity, timestamp, Lifecycle.START);
    }

    /**
     * Event lifecycle transitions (XES standard).
     */
    public enum Lifecycle {
        START,
        COMPLETE,
        SKIP
    }
}
