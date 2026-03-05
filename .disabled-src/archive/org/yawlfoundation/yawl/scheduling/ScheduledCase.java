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

package org.yawlfoundation.yawl.scheduling;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An immutable descriptor for a single scheduled YAWL case launch.
 *
 * <p>Each {@code ScheduledCase} has a unique ID, a target specification,
 * optional XML case data, a scheduled firing time, and a lifecycle status
 * managed by {@link WorkflowScheduler}.
 *
 * @since YAWL 6.0
 */
public final class ScheduledCase {

    /** Lifecycle status of a scheduled case. */
    public enum Status {
        /** Waiting for its scheduled time. */
        PENDING,
        /** Launched successfully into the engine. */
        FIRED,
        /** Cancelled before it could fire. */
        CANCELLED,
        /** Launch attempted but the engine reported an error. */
        FAILED
    }

    private final String id;
    private final String specificationId;
    private final String caseData;
    private final Instant scheduledAt;
    private volatile Status status;

    /**
     * Creates a new scheduled case.
     *
     * @param specificationId the YAWL specification identifier; must not be null or blank
     * @param caseData        optional XML case data; may be null
     * @param scheduledAt     the instant at which to launch the case; must not be null
     * @throws IllegalArgumentException if specificationId is null/blank or scheduledAt is null
     */
    public ScheduledCase(String specificationId, String caseData, Instant scheduledAt) {
        if (specificationId == null || specificationId.isBlank()) {
            throw new IllegalArgumentException("specificationId must not be null or blank");
        }
        Objects.requireNonNull(scheduledAt, "scheduledAt must not be null");
        this.id              = UUID.randomUUID().toString();
        this.specificationId = specificationId;
        this.caseData        = caseData;
        this.scheduledAt     = scheduledAt;
        this.status          = Status.PENDING;
    }

    /** Returns the unique identifier for this scheduled case. */
    public String getId() { return id; }

    /** Returns the YAWL specification ID to launch. */
    public String getSpecificationId() { return specificationId; }

    /** Returns the XML case data, or {@code null} if none was provided. */
    public String getCaseData() { return caseData; }

    /** Returns the instant at which this case is scheduled to fire. */
    public Instant getScheduledAt() { return scheduledAt; }

    /** Returns the current lifecycle status. */
    public Status getStatus() { return status; }

    /**
     * Updates the lifecycle status.
     *
     * <p>Only {@link WorkflowScheduler} and its internal tasks should call this.
     *
     * @param status the new status; must not be null
     */
    void setStatus(Status status) {
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    @Override
    public String toString() {
        return "ScheduledCase{id=" + id
            + ", spec=" + specificationId
            + ", at=" + scheduledAt
            + ", status=" + status + "}";
    }
}
