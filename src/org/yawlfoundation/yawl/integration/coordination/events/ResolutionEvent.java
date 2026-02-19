/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.coordination.events;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Event for tracking resolution outcomes of coordination conflicts.
 *
 * <p>This event captures the complete lifecycle of resolution processes,
 * from initiation through final decision. It tracks which agents participated,
 * what decisions were made, and how the resolution affects workflow execution.
 * Resolution events are generated when conflicts are successfully resolved or
 * when resolution attempts fail.
 *
 * <h2>Resolution Types</h2>
 * <ul>
 *   <li><b>Automatic Resolution</b> - System-enforced resolution policies</li>
 *   <li><b>Consensus Resolution</b> - Multi-agent agreement required</li>
 *   <li><b>Arbitration Resolution</b> - Third-party decision maker</li>
 *   <li><b>Voting Resolution</b> - Majority vote decides outcome</li>
 *   <li><b>Escalation Resolution</b> - Higher-level authority intervenes</li>
 * </ul>
 *
 * <h2>JSON Schema</h2>
 * <pre>
 * {
 *   "resolutionId": "550e8400-e29b-41d4-a716-446655440003",
 *   "conflictId": "conflict-123",
 *   "resolutionType": "CONSENSUS",
 *   "status": "COMPLETED",
 *   "participatingAgents": ["agent-1", "agent-2", "agent-3"],
 *   "votingAgents": ["agent-1", "agent-2"],
 *   "votes": {"agent-1": "APPROVE", "agent-2": "APPROVE"},
 *   "finalDecision": "APPROVED",
 *   "resolutionAgent": "coordinator-service",
 *   "resolutionTime": "2026-02-17T10:00:05Z",
 *   "durationMs": 5000,
 *   "outcome": {
 *     "resolution": "WORKFLOW_CONTINUE",
 *     "affectedWorkItems": ["wi-123"],
 *     "priorityAdjustments": {"wi-456": 10},
 *     "resourceAllocations": {"server-1": "agent-2"}
 *   },
 *   "metadata": {"quorumReached": true, "timeoutOccurred": false},
 *   "isSuccess": true,
 *   "failureReason": null
 * }
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class ResolutionEvent {

    public enum ResolutionType {
        AUTOMATIC,    // System-enforced automatic resolution
        CONSENSUS,    // Multi-agent consensus required
        ARBITRATION,  // Third-party arbitration decision
        VOTING,       // Majority vote determines outcome
        ESCALATION,   // Escalate to higher authority
        NEGOTIATION,  // Agent negotiation and compromise
        TIMEOUT,      // Timeout-based resolution (last-resort)
        MANUAL        // Human intervention required
    }

    public enum ResolutionStatus {
        INITIATED,    // Resolution process started
        VOTING,       // Voting in progress (for voting type)
        CONSENSUS,    // Consensus building in progress
        PENDING,      // Waiting for external input
        COMPLETED,    // Resolution completed successfully
        FAILED,       // Resolution failed
        TIMEOUT,      // Resolution timed out
        CANCELLED     // Resolution was cancelled
    }

    public enum FinalDecision {
        APPROVE,      // Approved with conditions
        REJECT,       // Rejected with reason
        APPROVE_CONDITIONAL, // Approved with conditions attached
        ESCALATE,     // Escalated for higher-level review
        DEFER,        // Deferred for later resolution
        RETRY         // Retry resolution with different parameters
    }

    public enum WorkflowOutcome {
        CONTINUE,     // Continue with normal execution
        PAUSE,        // Pause workflow until resolved
        REDIRECT,     // Redirect to alternative path
        RESTART,      // Restart workflow from checkpoint
        CANCEL,       // Cancel the workflow case
        MODIFY_CASE,  // Modify case parameters and continue
        SPLIT,        // Split into parallel execution paths
        MERGE         // Merge parallel paths back together
    }

    private final String resolutionId;
    private final String conflictId;  // References the original conflict
    private final ResolutionType resolutionType;
    private final ResolutionStatus status;
    private final String[] participatingAgents;
    private final String[] votingAgents;
    private final Map<String, String> votes;  // agent -> vote (APPROVE/REJECT/etc.)
    private final FinalDecision finalDecision;
    private final String resolutionAgent;
    private final Instant resolutionTime;
    private final long durationMs;
    private final ResolutionOutcome outcome;
    private final Map<String, Object> metadata;
    private final boolean isSuccess;
    private final String failureReason;

    /**
     * Create a resolution event with the required fields.
     *
     * @param resolutionId unique identifier for this resolution instance
     * @param conflictId reference to the original conflict (must not be blank)
     * @param resolutionType the resolution method used (must not be null)
     * @param status current resolution status (must not be null)
     * @param participatingAgents agents participating in resolution (may be empty)
     * @param votingAgents agents who cast votes (may be empty)
     * @param votes mapping of agent to vote cast (may be empty)
     * @param finalDecision the final resolution decision (null if not completed)
     * @param resolutionAgent the agent that made the resolution (null if not completed)
     * @param resolutionTime when resolution occurred (null if not completed)
     * @param durationMs resolution duration in milliseconds (0 if not completed)
     * @param outcome the resolution outcome affecting workflow (null if not completed)
     * @param metadata additional resolution metadata (may be empty)
     * @param whether the resolution succeeded (only meaningful for completed resolutions)
     * @param if failed, description of the failure (may be blank)
     */
    public ResolutionEvent(String resolutionId, String conflictId, ResolutionType resolutionType,
                         ResolutionStatus status, String[] participatingAgents,
                         String[] votingAgents, Map<String, String> votes,
                         FinalDecision finalDecision, String resolutionAgent,
                         Instant resolutionTime, long durationMs, ResolutionOutcome outcome,
                         Map<String, Object> metadata, boolean isSuccess, String failureReason) {
        this.resolutionId = Objects.requireNonNull(resolutionId, "resolutionId");
        this.conflictId = Objects.requireNonNull(conflictId, "conflictId");
        this.resolutionType = Objects.requireNonNull(resolutionType, "resolutionType");
        this.status = Objects.requireNonNull(status, "status");
        this.participatingAgents = participatingAgents != null ? participatingAgents : new String[0];
        this.votingAgents = votingAgents != null ? votingAgents : new String[0];
        this.votes = votes != null ? Map.copyOf(votes) : Map.of();
        this.finalDecision = finalDecision;
        this.resolutionAgent = resolutionAgent;
        this.resolutionTime = resolutionTime;
        this.durationMs = durationMs;
        this.outcome = outcome;
        this.metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        this.isSuccess = isSuccess;
        this.failureReason = failureReason != null ? failureReason : "";
    }

    /**
     * Create a new resolution event for a conflict.
     */
    public static ResolutionEvent initiated(String conflictId, ResolutionType resolutionType,
                                         String[] participatingAgents, Instant timestamp) {
        String resolutionId = java.util.UUID.randomUUID().toString();
        return new ResolutionEvent(resolutionId, conflictId, resolutionType,
                                ResolutionStatus.INITIATED, participatingAgents,
                                new String[0], Map.of(), null, null, null, 0,
                                null, Map.of(), false, "");
    }

    /**
     * Create a completed resolution event.
     */
    public ResolutionEvent completed(FinalDecision finalDecision, String resolutionAgent,
                                   Instant resolutionTime, ResolutionOutcome outcome,
                                   Map<String, Object> metadata, boolean isSuccess,
                                   String failureReason) {
        long duration = resolutionTime != null ?
            resolutionTime.toEpochMilli() - System.currentTimeMillis() : 0;
        return new ResolutionEvent(this.resolutionId, this.conflictId, this.resolutionType,
                              ResolutionStatus.COMPLETED, this.participatingAgents,
                              this.votingAgents, this.votes, finalDecision,
                              resolutionAgent, resolutionTime, duration, outcome,
                              metadata, isSuccess, failureReason);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Unique identifier for this resolution instance. */
    public String getResolutionId() { return resolutionId; }

    /** Reference to the original conflict being resolved. */
    public String getConflictId() { return conflictId; }

    /** Method used for resolution. */
    public ResolutionType getResolutionType() { return resolutionType; }

    /** Current status of the resolution process. */
    public ResolutionStatus getStatus() { return status; }

    /** Agents participating in resolution. */
    public String[] getParticipatingAgents() { return participatingAgents.clone(); }

    /** Agents who cast votes (for voting type resolutions). */
    public String[] getVotingAgents() { return votingAgents.clone(); }

    /** Votes cast by participating agents. */
    public Map<String, String> getVotes() { return votes; }

    /** The final resolution decision (null if not completed). */
    public FinalDecision getFinalDecision() { return finalDecision; }

    /** Agent that made the resolution (null if not completed). */
    public String getResolutionAgent() { return resolutionAgent; }

    /** When resolution occurred (null if not completed). */
    public Instant getResolutionTime() { return resolutionTime; }

    /** Resolution duration in milliseconds (0 if not completed). */
    public long getDurationMs() { return durationMs; }

    /** Outcome affecting workflow execution (null if not completed). */
    public ResolutionOutcome getOutcome() { return outcome; }

    /** Additional resolution metadata. */
    public Map<String, Object> getMetadata() { return metadata; }

    /** Whether the resolution succeeded (only meaningful for completed resolutions). */
    public boolean isSuccess() { return isSuccess; }

    /** If failed, description of the failure. */
    public String getFailureReason() { return failureReason; }

    /** Returns true if the resolution has been completed. */
    public boolean isCompleted() {
        return status == ResolutionStatus.COMPLETED || status == ResolutionStatus.FAILED ||
               status == ResolutionStatus.TIMEOUT || status == ResolutionStatus.CANCELLED;
    }

    // -------------------------------------------------------------------------
    // Serialization Support
    // -------------------------------------------------------------------------

    /**
     * Convert this event to a map for JSON serialization.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("resolutionId", resolutionId);
        map.put("conflictId", conflictId);
        map.put("resolutionType", resolutionType.name());
        map.put("status", status.name());
        map.put("participatingAgents", java.util.List.of(participatingAgents));
        map.put("votingAgents", java.util.List.of(votingAgents));
        map.put("votes", votes);
        map.put("finalDecision", finalDecision != null ? finalDecision.name() : null);
        map.put("resolutionAgent", resolutionAgent);
        map.put("resolutionTime", resolutionTime != null ? resolutionTime.toString() : null);
        map.put("durationMs", durationMs);
        map.put("outcome", outcome != null ? outcome.toMap() : null);
        map.put("metadata", metadata);
        map.put("isSuccess", isSuccess);
        map.put("failureReason", failureReason);
        return map;
    }

    /**
     * Create a ResolutionEvent from a map (deserialization).
     */
    @SuppressWarnings("unchecked")
    public static ResolutionEvent fromMap(Map<String, Object> map) {
        String resolutionId = (String) map.get("resolutionId");
        String conflictId = (String) map.get("conflictId");
        ResolutionType resolutionType = ResolutionType.valueOf((String) map.get("resolutionType"));
        ResolutionStatus status = ResolutionStatus.valueOf((String) map.get("status"));

        @SuppressWarnings("unchecked")
        String[] participatingAgents = ((java.util.List<String>) map.get("participatingAgents")).toArray(new String[0]);
        @SuppressWarnings("unchecked")
        String[] votingAgents = ((java.util.List<String>) map.get("votingAgents")).toArray(new String[0]);
        Map<String, String> votes = (Map<String, String>) map.get("votes");

        FinalDecision finalDecision = null;
        String finalDecisionStr = (String) map.get("finalDecision");
        if (finalDecisionStr != null) {
            finalDecision = FinalDecision.valueOf(finalDecisionStr);
        }

        String resolutionAgent = (String) map.get("resolutionAgent");
        String resolutionTimeStr = (String) map.get("resolutionTime");
        Instant resolutionTime = resolutionTimeStr != null ? Instant.parse(resolutionTimeStr) : null;

        long durationMs = ((Number) map.get("durationMs")).longValue();

        @SuppressWarnings("unchecked")
        ResolutionOutcome outcome = null;
        @SuppressWarnings("unchecked")
        Map<String, Object> outcomeMap = (Map<String, Object>) map.get("outcome");
        if (outcomeMap != null) {
            outcome = ResolutionOutcome.fromMap(outcomeMap);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) map.get("metadata");
        boolean isSuccess = (Boolean) map.get("isSuccess");
        String failureReason = (String) map.get("failureReason");

        return new ResolutionEvent(resolutionId, conflictId, resolutionType,
                              status, participatingAgents, votingAgents, votes,
                              finalDecision, resolutionAgent, resolutionTime,
                              durationMs, outcome, metadata, isSuccess, failureReason);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResolutionEvent)) return false;
        ResolutionEvent that = (ResolutionEvent) o;
        return Objects.equals(resolutionId, that.resolutionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resolutionId);
    }

    @Override
    public String toString() {
        return "ResolutionEvent{resolutionId='" + resolutionId + "', conflictId='" + conflictId +
               "', type=" + resolutionType + ", status=" + status +
               ", completed=" + isCompleted() + "}";
    }

    /**
     * Nested class for resolution outcome data.
     */
    public static final class ResolutionOutcome {
        private final WorkflowOutcome workflowOutcome;
        private final String[] affectedWorkItems;
        private final Map<String, Integer> priorityAdjustments;
        private final Map<String, String> resourceAllocations;

        public ResolutionOutcome(WorkflowOutcome workflowOutcome, String[] affectedWorkItems,
                               Map<String, Integer> priorityAdjustments,
                               Map<String, String> resourceAllocations) {
            this.workflowOutcome = workflowOutcome;
            this.affectedWorkItems = affectedWorkItems != null ? affectedWorkItems : new String[0];
            this.priorityAdjustments = priorityAdjustments != null ?
                Map.copyOf(priorityAdjustments) : Map.of();
            this.resourceAllocations = resourceAllocations != null ?
                Map.copyOf(resourceAllocations) : Map.of();
        }

        public WorkflowOutcome getWorkflowOutcome() { return workflowOutcome; }
        public String[] getAffectedWorkItems() { return affectedWorkItems.clone(); }
        public Map<String, Integer> getPriorityAdjustments() { return priorityAdjustments; }
        public Map<String, String> getResourceAllocations() { return resourceAllocations; }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("workflowOutcome", workflowOutcome.name());
            map.put("affectedWorkItems", java.util.List.of(affectedWorkItems));
            map.put("priorityAdjustments", priorityAdjustments);
            map.put("resourceAllocations", resourceAllocations);
            return map;
        }

        public static ResolutionOutcome fromMap(Map<String, Object> map) {
            WorkflowOutcome outcome = WorkflowOutcome.valueOf((String) map.get("workflowOutcome"));
            @SuppressWarnings("unchecked")
            String[] workItems = ((java.util.List<String>) map.get("affectedWorkItems")).toArray(new String[0]);
            @SuppressWarnings("unchecked")
            Map<String, Integer> priority = (Map<String, Integer>) map.get("priorityAdjustments");
            @SuppressWarnings("unchecked")
            Map<String, String> resources = (Map<String, String>) map.get("resourceAllocations");

            return new ResolutionOutcome(outcome, workItems, priority, resources);
        }
    }
}