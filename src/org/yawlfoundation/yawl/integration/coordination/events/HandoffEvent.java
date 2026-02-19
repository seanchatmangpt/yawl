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
import java.util.Map;
import java.util.Objects;

/**
 * Event for handoff transfer tracking between agents or systems.
 *
 * <p>This event captures the complete lifecycle of handoff operations,
 * including initiation, acceptance, completion, and any failures.
 * Handoffs are critical for maintaining continuity when agents transfer
 * responsibility for work items, workflow cases, or coordination tasks.
 *
 * <h2>Handoff Types</h2>
 * <ul>
 *   <li><b>Agent Handoff</b> - Transfer between different agents</li>
 *   <li><b>System Handoff</b> - Transfer between different systems/services</li>
 *   <li><b>State Handoff</b> - Transfer of workflow state between nodes</li>
 *   <li><b>Authority Handoff</b> - Transfer of decision-making authority</li>
 *   <li><b>Emergency Handoff</b> - Forced transfer due to failures</li>
 * </ul>
 *
 * <h2>JSON Schema</h2>
 * <pre>
 * {
 *   "handoffId": "550e8400-e29b-41d4-a716-446655440002",
 *   "handoffType": "AGENT",
 *   "sourceAgent": "agent-1",
 *   "targetAgent": "agent-2",
 *   "workItemId": "wi-123",
 *   "caseId": "case-42",
 *   "status": "INITIATED",
 *   "reason": "Workload balancing",
 *   "handoffToken": "token-abc123",
 *   "transferData": {"previousState": "in_progress"},
 *   "initiationTime": "2026-02-17T10:00:00Z",
 *   "completionTime": "2026-02-17T10:00:05Z",
 *   "durationMs": 5000,
 *   "attempts": 1,
 *   "isSuccess": true,
 *   "failureReason": null
 * }
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class HandoffEvent {

    public enum HandoffType {
        AGENT,           // Agent-to-agent transfer
        SYSTEM,          // System-to-system transfer
        STATE,           // State transfer between nodes
        AUTHORITY,       // Authority/responsibility transfer
        EMERGENCY,       // Emergency/forced transfer
        SCHEDULED,       // Scheduled transfer
        LOAD_BALANCING   // Load balancing transfer
    }

    public enum HandoffStatus {
        INITIATED,       // Handoff process started
        OFFERED,         // Offered to target (waiting acceptance)
        ACCEPTED,        // Target has accepted responsibility
        TRANSFERRING,   // Data/state being transferred
        COMPLETED,      // Handoff successfully completed
        FAILED,          // Handoff failed
        CANCELLED,       // Handoff was cancelled
        REJECTED         // Handoff offer was rejected
    }

    private final String handoffId;
    private final HandoffType handoffType;
    private final String sourceAgent;
    private final String targetAgent;
    private final String workItemId;
    private final String caseId;
    private final HandoffStatus status;
    private final String reason;
    private final String handoffToken;
    private final Map<String, String> transferData;
    private final Instant initiationTime;
    private final Instant completionTime;
    private final long durationMs;
    private final int attempts;
    private final boolean isSuccess;
    private final String failureReason;

    /**
     * Create a handoff event with the required fields.
     *
     * @param handoffId unique identifier for this handoff instance
     * @param handoffType the type of handoff (must not be null)
     * @param sourceAgent the source agent/system (must not be blank)
     * @param targetAgent the target agent/system (must not be blank)
     * @param workItemId the work item being transferred (null for case-level handoffs)
     * @param caseId the case being transferred (must not be blank)
     * @param status the current handoff status (must not be null)
     * @param reason reason for the handoff (may be blank)
     * @param handoffToken secure token for verification (may be blank)
     * @param transferData data being transferred (may be empty)
     * @param initiationTime when the handoff was initiated (must not be null)
     * @param completionTime when the handoff completed (null if in progress)
     * @param durationMs duration in milliseconds (0 if not completed)
     * @param number of attempts made (1 for initial, incremented on retries)
     * @param whether the handoff succeeded (only meaningful for completed handoffs)
     * @param failureReason if failed, description of the failure (may be blank)
     */
    public HandoffEvent(String handoffId, HandoffType handoffType, String sourceAgent,
                       String targetAgent, String workItemId, String caseId,
                       HandoffStatus status, String reason, String handoffToken,
                       Map<String, String> transferData, Instant initiationTime,
                       Instant completionTime, long durationMs, int attempts,
                       boolean isSuccess, String failureReason) {
        this.handoffId = Objects.requireNonNull(handoffId, "handoffId");
        this.handoffType = Objects.requireNonNull(handoffType, "handoffType");
        this.sourceAgent = Objects.requireNonNull(sourceAgent, "sourceAgent");
        this.targetAgent = Objects.requireNonNull(targetAgent, "targetAgent");
        this.workItemId = workItemId;
        this.caseId = Objects.requireNonNull(caseId, "caseId");
        this.status = Objects.requireNonNull(status, "status");
        this.reason = reason != null ? reason : "";
        this.handoffToken = handoffToken;
        this.transferData = transferData != null ? Map.copyOf(transferData) : Map.of();
        this.initiationTime = Objects.requireNonNull(initiationTime, "initiationTime");
        this.completionTime = completionTime;
        this.durationMs = durationMs;
        this.attempts = attempts;
        this.isSuccess = isSuccess;
        this.failureReason = failureReason != null ? failureReason : "";
    }

    /**
     * Create a new initiated handoff event.
     */
    public static HandoffEvent initiated(HandoffType handoffType, String sourceAgent,
                                       String targetAgent, String workItemId,
                                       String caseId, String reason, String handoffToken,
                                       Map<String, String> transferData, Instant timestamp) {
        String handoffId = java.util.UUID.randomUUID().toString();
        return new HandoffEvent(handoffId, handoffType, sourceAgent, targetAgent,
                              workItemId, caseId, HandoffStatus.INITIATED, reason,
                              handoffToken, transferData, timestamp, null, 0, 1, false, "");
    }

    /**
     * Create a completed handoff event.
     */
    public HandoffEvent completed(HandoffStatus finalStatus, Instant completionTime,
                                 boolean isSuccess, String failureReason) {
        long duration = completionTime != null ?
            completionTime.toEpochMilli() - initiationTime.toEpochMilli() : 0;
        return new HandoffEvent(this.handoffId, this.handoffType, this.sourceAgent,
                              this.targetAgent, this.workItemId, this.caseId,
                              finalStatus, this.reason, this.handoffToken,
                              this.transferData, this.initiationTime, completionTime,
                              duration, this.attempts, isSuccess, failureReason);
    }

    /**
     * Create a retry attempt for a failed handoff.
     */
    public HandoffEvent retry(String newTargetAgent, Instant retryTime) {
        return new HandoffEvent(this.handoffId, this.handoffType, this.sourceAgent,
                              newTargetAgent, this.workItemId, this.caseId,
                              HandoffStatus.INITIATED, this.reason,
                              java.util.UUID.randomUUID().toString(),
                              this.transferData, retryTime, null, 0, this.attempts + 1, false, "");
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Unique identifier for this handoff instance. */
    public String getHandoffId() { return handoffId; }

    /** Type of handoff operation. */
    public HandoffType getHandoffType() { return handoffType; }

    /** Source agent/system initiating the handoff. */
    public String getSourceAgent() { return sourceAgent; }

    /** Target agent/system receiving the handoff. */
    public String getTargetAgent() { return targetAgent; }

    /** Work item being transferred (null for case-level handoffs). */
    public String getWorkItemId() { return workItemId; }

    /** Case being transferred. */
    public String getCaseId() { return caseId; }

    /** Current status of the handoff. */
    public HandoffStatus getStatus() { return status; }

    /** Reason for the handoff operation. */
    public String getReason() { return reason; }

    /** Secure token for handoff verification. */
    public String getHandoffToken() { return handoffToken; }

    /** Data being transferred during handoff. */
    public Map<String, String> getTransferData() { return transferData; }

    /** When the handoff was initiated. */
    public Instant getInitiationTime() { return initiationTime; }

    /** When the handoff was completed (null if in progress). */
    public Instant getCompletionTime() { return completionTime; }

    /** Duration of the handoff in milliseconds (0 if not completed). */
    public long getDurationMs() { return durationMs; }

    /** Number of attempts made for this handoff. */
    public int getAttempts() { return attempts; }

    /** Whether the handoff succeeded (only meaningful for completed handoffs). */
    public boolean isSuccess() { return isSuccess; }

    /** If failed, description of the failure. */
    public String getFailureReason() { return failureReason; }

    /** Returns true if the handoff has been completed (successfully or not). */
    public boolean isCompleted() {
        return status == HandoffStatus.COMPLETED || status == HandoffStatus.FAILED;
    }

    /** Returns true if the handoff is in progress. */
    public boolean isInProgress() {
        return status == HandoffStatus.INITIATED || status == HandoffStatus.OFFERED ||
               status == HandoffStatus.ACCEPTED || status == HandoffStatus.TRANSFERRING;
    }

    // -------------------------------------------------------------------------
    // Serialization Support
    // -------------------------------------------------------------------------

    /**
     * Convert this event to a map for JSON serialization.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("handoffId", handoffId);
        map.put("handoffType", handoffType.name());
        map.put("sourceAgent", sourceAgent);
        map.put("targetAgent", targetAgent);
        map.put("workItemId", workItemId);
        map.put("caseId", caseId);
        map.put("status", status.name());
        map.put("reason", reason);
        map.put("handoffToken", handoffToken);
        map.put("transferData", transferData);
        map.put("initiationTime", initiationTime.toString());
        map.put("completionTime", completionTime != null ? completionTime.toString() : null);
        map.put("durationMs", durationMs);
        map.put("attempts", attempts);
        map.put("isSuccess", isSuccess);
        map.put("failureReason", failureReason);
        return map;
    }

    /**
     * Create a HandoffEvent from a map (deserialization).
     */
    @SuppressWarnings("unchecked")
    public static HandoffEvent fromMap(Map<String, Object> map) {
        String handoffId = (String) map.get("handoffId");
        HandoffType handoffType = HandoffType.valueOf((String) map.get("handoffType"));
        String sourceAgent = (String) map.get("sourceAgent");
        String targetAgent = (String) map.get("targetAgent");
        String workItemId = (String) map.get("workItemId");
        String caseId = (String) map.get("caseId");
        HandoffStatus status = HandoffStatus.valueOf((String) map.get("status"));
        String reason = (String) map.get("reason");
        String handoffToken = (String) map.get("handoffToken");

        Map<String, String> transferData = (Map<String, String>) map.get("transferData");

        String initiationTimeStr = (String) map.get("initiationTime");
        Instant initiationTime = Instant.parse(initiationTimeStr);

        String completionTimeStr = (String) map.get("completionTime");
        Instant completionTime = completionTimeStr != null ? Instant.parse(completionTimeStr) : null;

        long durationMs = ((Number) map.get("durationMs")).longValue();
        int attempts = ((Number) map.get("attempts")).intValue();
        boolean isSuccess = (Boolean) map.get("isSuccess");
        String failureReason = (String) map.get("failureReason");

        return new HandoffEvent(handoffId, handoffType, sourceAgent, targetAgent,
                              workItemId, caseId, status, reason, handoffToken,
                              transferData, initiationTime, completionTime,
                              durationMs, attempts, isSuccess, failureReason);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof HandoffEvent)) return false;
        HandoffEvent that = (HandoffEvent) o;
        return Objects.equals(handoffId, that.handoffId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(handoffId);
    }

    @Override
    public String toString() {
        return "HandoffEvent{handoffId='" + handoffId + "', type=" + handoffType +
               ", " + sourceAgent + "->" + targetAgent + ", status=" + status +
               ", completed=" + isCompleted() + "}";
    }
}