/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.worklet;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a running worklet instance — the result of an RDR selection.
 *
 * <p>A WorkletRecord captures the outcome of selecting a worklet for a task:
 * <ul>
 *   <li>The worklet name (the RDR conclusion)</li>
 *   <li>The case ID of the host case (the case that triggered worklet selection)</li>
 *   <li>The task ID of the work item that triggered selection</li>
 *   <li>The worklet case ID (the launched worklet case, if started)</li>
 *   <li>The selection timestamp</li>
 * </ul>
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>{@code PENDING} — selection completed, worklet not yet launched</li>
 *   <li>{@code RUNNING} — worklet case has been launched</li>
 *   <li>{@code COMPLETE} — worklet case has completed</li>
 *   <li>{@code CANCELLED} — worklet was cancelled before completion</li>
 * </ol>
 *
 * @author YAWL Foundation
 * @version 6.0.0-Beta
 */
public class WorkletRecord {

    /**
     * Lifecycle states for a worklet instance.
     */
    public enum Status {
        PENDING, RUNNING, COMPLETE, CANCELLED
    }

    private final String workletName;
    private final String hostCaseId;
    private final String hostTaskId;
    private final Instant selectionTime;
    private String workletCaseId;
    private Status status;

    /** A2A agent endpoint, set when this record represents an A2A-delegated worklet. */
    private final String a2aEndpoint;

    /** A2A skill name, set when this record represents an A2A-delegated worklet. */
    private final String a2aSkill;

    /**
     * Constructs a WorkletRecord representing a selected worklet.
     *
     * @param workletName the name of the selected worklet (must not be null or blank)
     * @param hostCaseId  the case ID of the host workflow case (must not be null or blank)
     * @param hostTaskId  the task ID of the host work item (must not be null or blank)
     * @throws IllegalArgumentException if any argument is null or blank
     */
    public WorkletRecord(String workletName, String hostCaseId, String hostTaskId) {
        if (workletName == null || workletName.isBlank()) {
            throw new IllegalArgumentException("Worklet name must not be null or blank");
        }
        if (hostCaseId == null || hostCaseId.isBlank()) {
            throw new IllegalArgumentException("Host case ID must not be null or blank");
        }
        if (hostTaskId == null || hostTaskId.isBlank()) {
            throw new IllegalArgumentException("Host task ID must not be null or blank");
        }
        this.workletName = workletName.trim();
        this.hostCaseId = hostCaseId.trim();
        this.hostTaskId = hostTaskId.trim();
        this.selectionTime = Instant.now();
        this.status = Status.PENDING;
        this.workletCaseId = null;
        this.a2aEndpoint = null;
        this.a2aSkill = null;
    }

    /**
     * Constructs a WorkletRecord representing an A2A-delegated worklet.
     *
     * @param a2aEndpoint the A2A agent HTTP endpoint (must not be null or blank)
     * @param a2aSkill    the skill name to invoke on the agent (must not be null or blank)
     * @param hostCaseId  the case ID of the host workflow case (must not be null or blank)
     * @param hostTaskId  the task ID of the host work item (must not be null or blank)
     * @throws IllegalArgumentException if any argument is null or blank
     */
    public WorkletRecord(String a2aEndpoint, String a2aSkill,
                         String hostCaseId, String hostTaskId) {
        if (a2aEndpoint == null || a2aEndpoint.isBlank()) {
            throw new IllegalArgumentException("A2A endpoint must not be null or blank");
        }
        if (a2aSkill == null || a2aSkill.isBlank()) {
            throw new IllegalArgumentException("A2A skill must not be null or blank");
        }
        if (hostCaseId == null || hostCaseId.isBlank()) {
            throw new IllegalArgumentException("Host case ID must not be null or blank");
        }
        if (hostTaskId == null || hostTaskId.isBlank()) {
            throw new IllegalArgumentException("Host task ID must not be null or blank");
        }
        this.a2aEndpoint = a2aEndpoint.trim();
        this.a2aSkill = a2aSkill.trim();
        this.workletName = a2aEndpoint.trim() + "/" + a2aSkill.trim();
        this.hostCaseId = hostCaseId.trim();
        this.hostTaskId = hostTaskId.trim();
        this.selectionTime = Instant.now();
        this.status = Status.PENDING;
        this.workletCaseId = null;
    }

    /**
     * Returns the name of the selected worklet.
     */
    public String getWorkletName() {
        return workletName;
    }

    /**
     * Returns the case ID of the host workflow case.
     */
    public String getHostCaseId() {
        return hostCaseId;
    }

    /**
     * Returns the task ID of the host work item that triggered worklet selection.
     */
    public String getHostTaskId() {
        return hostTaskId;
    }

    /**
     * Returns the time at which the worklet was selected.
     */
    public Instant getSelectionTime() {
        return selectionTime;
    }

    /**
     * Returns the case ID of the launched worklet case, or null if not yet launched.
     */
    public String getWorkletCaseId() {
        return workletCaseId;
    }

    /**
     * Returns the current lifecycle status of this worklet instance.
     */
    public Status getStatus() {
        return status;
    }

    /**
     * Marks this worklet as launched with the given worklet case ID.
     *
     * @param workletCaseId the case ID assigned by the engine (must not be null or blank)
     * @throws IllegalArgumentException if workletCaseId is null or blank
     * @throws IllegalStateException    if this worklet is not in PENDING state
     */
    public void launch(String workletCaseId) {
        if (workletCaseId == null || workletCaseId.isBlank()) {
            throw new IllegalArgumentException("Worklet case ID must not be null or blank");
        }
        if (status != Status.PENDING) {
            throw new IllegalStateException(
                    "Cannot launch worklet in state " + status + "; must be PENDING");
        }
        this.workletCaseId = workletCaseId.trim();
        this.status = Status.RUNNING;
    }

    /**
     * Marks this worklet as complete.
     *
     * @throws IllegalStateException if this worklet is not in RUNNING state
     */
    public void complete() {
        if (status != Status.RUNNING) {
            throw new IllegalStateException(
                    "Cannot complete worklet in state " + status + "; must be RUNNING");
        }
        this.status = Status.COMPLETE;
    }

    /**
     * Cancels this worklet. May be called from PENDING or RUNNING state.
     *
     * @throws IllegalStateException if this worklet is already COMPLETE or CANCELLED
     */
    public void cancel() {
        if (status == Status.COMPLETE || status == Status.CANCELLED) {
            throw new IllegalStateException(
                    "Cannot cancel worklet in state " + status);
        }
        this.status = Status.CANCELLED;
    }

    /**
     * Returns true if this worklet is currently running.
     */
    public boolean isRunning() {
        return status == Status.RUNNING;
    }

    /**
     * Returns true if this worklet has completed.
     */
    public boolean isComplete() {
        return status == Status.COMPLETE;
    }

    /**
     * Returns true if this worklet was cancelled.
     */
    public boolean isCancelled() {
        return status == Status.CANCELLED;
    }

    /**
     * Returns true if this worklet is pending launch.
     */
    public boolean isPending() {
        return status == Status.PENDING;
    }

    /**
     * Returns the A2A agent endpoint, or null if this is not an A2A-delegated worklet.
     */
    public String getA2aEndpoint() {
        return a2aEndpoint;
    }

    /**
     * Returns the A2A skill name, or null if this is not an A2A-delegated worklet.
     */
    public String getA2aSkill() {
        return a2aSkill;
    }

    /**
     * Returns true if this record represents an A2A agent delegation (not a sub-case worklet).
     */
    public boolean isA2aDelegated() {
        return a2aEndpoint != null;
    }

    /**
     * Returns a composite key for this worklet record: {@code hostCaseId:hostTaskId}.
     */
    public String getCompositeKey() {
        return hostCaseId + ":" + hostTaskId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof WorkletRecord other)) return false;
        return Objects.equals(workletName, other.workletName) &&
               Objects.equals(hostCaseId, other.hostCaseId) &&
               Objects.equals(hostTaskId, other.hostTaskId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(workletName, hostCaseId, hostTaskId);
    }

    @Override
    public String toString() {
        return "WorkletRecord{worklet='%s', hostCase='%s', hostTask='%s', status=%s}"
                .formatted(workletName, hostCaseId, hostTaskId, status);
    }
}
