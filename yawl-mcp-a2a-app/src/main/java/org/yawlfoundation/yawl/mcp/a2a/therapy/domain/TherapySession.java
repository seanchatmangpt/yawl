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

package org.yawlfoundation.yawl.mcp.a2a.therapy.domain;

/**
 * Represents a scheduled occupational therapy intervention session.
 *
 * <p>Sessions are the atomic execution units of the therapy plan. Each session
 * has a specific intervention type, scheduled time slot, and progress score
 * updated after completion.</p>
 *
 * @param id unique session identifier
 * @param patientId patient this session belongs to
 * @param interventionType type of intervention (e.g., "ADL training", "cognitive retraining")
 * @param scheduledDate ISO-8601 date string (yyyy-MM-dd)
 * @param durationMinutes session duration in minutes
 * @param status session lifecycle status
 * @param progressScore goal attainment scaling score (0.0 = no progress, 1.0 = full goal)
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record TherapySession(
    String id,
    String patientId,
    String interventionType,
    String scheduledDate,
    int durationMinutes,
    String status,
    double progressScore
) {
    /** Valid status values. */
    public static final String STATUS_SCHEDULED = "scheduled";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_ADAPTED = "adapted";
    public static final String STATUS_CANCELLED = "cancelled";

    /** Canonical constructor with validation. */
    public TherapySession {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Session id required");
        if (patientId == null || patientId.isBlank()) throw new IllegalArgumentException("Patient id required");
        if (interventionType == null || interventionType.isBlank()) throw new IllegalArgumentException("Intervention type required");
        if (scheduledDate == null || !scheduledDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new IllegalArgumentException("Scheduled date must be yyyy-MM-dd: " + scheduledDate);
        }
        if (durationMinutes < 15 || durationMinutes > 180) throw new IllegalArgumentException("Duration must be 15-180 min");
        if (progressScore < 0.0 || progressScore > 1.0) throw new IllegalArgumentException("Progress score must be 0.0-1.0");
    }

    /** Returns true if this session is complete. */
    public boolean isCompleted() {
        return STATUS_COMPLETED.equals(status);
    }

    /** Creates a completed copy of this session with the given progress score. */
    public TherapySession withCompleted(double score) {
        return new TherapySession(id, patientId, interventionType, scheduledDate, durationMinutes, STATUS_COMPLETED, score);
    }
}
