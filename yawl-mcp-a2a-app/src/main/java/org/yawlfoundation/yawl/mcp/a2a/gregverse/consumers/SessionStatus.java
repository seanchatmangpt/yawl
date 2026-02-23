/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.gregverse.consumers;

import java.time.Instant;
import java.util.List;

/**
 * Represents the current status of a therapy session.
 *
 * @param sessionId the session ID
 * @param status the session status
 * @param artifacts session artifacts or notes
 * @param lastUpdated when the status was last updated
 */
public record SessionStatus(
    String sessionId,
    String status,
    List<String> artifacts,
    Instant lastUpdated
) {

    /**
     * Session status constants.
     */
    public static final String STATUS_SCHEDULED = "scheduled";
    public static final String STATUS_CONFIRMED = "confirmed";
    public static final String STATUS_IN_PROGRESS = "in_progress";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_CANCELLED = "cancelled";
    public static final String STATUS_ADAPTED = "adapted";

    /**
     * Creates a session status.
     */
    public SessionStatus {
        artifacts = List.copyOf(artifacts);
    }

    /**
     * Returns true if the session is scheduled.
     */
    public boolean isScheduled() {
        return STATUS_SCHEDULED.equals(status);
    }

    /**
     * Returns true if the session is confirmed.
     */
    public boolean isConfirmed() {
        return STATUS_CONFIRMED.equals(status);
    }

    /**
     * Returns true if the session is in progress.
     */
    public boolean isInProgress() {
        return STATUS_IN_PROGRESS.equals(status);
    }

    /**
     * Returns true if the session is completed.
     */
    public boolean isCompleted() {
        return STATUS_COMPLETED.equals(status);
    }

    /**
     * Returns true if the session was cancelled.
     */
    public boolean isCancelled() {
        return STATUS_CANCELLED.equals(status);
    }

    /**
     * Returns true if the session was adapted.
     */
    public boolean isAdapted() {
        return STATUS_ADAPTED.equals(status);
    }

    /**
     * Returns true if the session can be started.
     */
    public boolean canStart() {
        return isConfirmed() || isScheduled();
    }

    /**
     * Returns true if the session is finished (completed, cancelled, or adapted).
     */
    public boolean isFinished() {
        return isCompleted() || isCancelled() || isAdapted();
    }

    /**
     * Gets a user-friendly status description.
     */
    public String getFriendlyStatus() {
        return switch (status) {
            case STATUS_SCHEDULED -> "Session scheduled";
            case STATUS_CONFIRMED -> "Session confirmed";
            case STATUS_IN_PROGRESS -> "Session in progress";
            case STATUS_COMPLETED -> "Session completed";
            case STATUS_CANCELLED -> "Session cancelled";
            case STATUS_ADAPTED -> "Session adapted";
            default -> "Unknown status";
        };
    }

    /**
     * Gets the number of artifacts available.
     */
    public int getArtifactCount() {
        return artifacts.size();
    }

    /**
     * Returns true if artifacts are available.
     */
    public boolean hasArtifacts() {
        return !artifacts.isEmpty();
    }
}