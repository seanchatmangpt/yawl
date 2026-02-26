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
 * A recurring schedule that fires a YAWL case on every occurrence of a
 * {@link CronExpression}.
 *
 * <p>The schedule tracks how many times it has fired and the last/next
 * computed occurrence. {@link WorkflowScheduler} calls {@link #nextOccurrence(Instant)}
 * after each firing to advance the schedule.
 *
 * @since YAWL 6.0
 */
public final class RecurringSchedule {

    private final String id;
    private final String specificationId;
    private final String caseData;
    private final CronExpression cronExpression;
    private volatile boolean active;
    private volatile long fireCount;
    private volatile Instant lastFiredAt;

    /**
     * Creates an active recurring schedule.
     *
     * @param specificationId the YAWL specification to launch on each occurrence; must not be null or blank
     * @param caseData        optional XML case data; may be null
     * @param cronExpression  the parsed cron expression; must not be null
     * @throws IllegalArgumentException if specificationId is null/blank or cronExpression is null
     */
    public RecurringSchedule(String specificationId, String caseData, CronExpression cronExpression) {
        if (specificationId == null || specificationId.isBlank()) {
            throw new IllegalArgumentException("specificationId must not be null or blank");
        }
        Objects.requireNonNull(cronExpression, "cronExpression must not be null");
        this.id              = UUID.randomUUID().toString();
        this.specificationId = specificationId;
        this.caseData        = caseData;
        this.cronExpression  = cronExpression;
        this.active          = true;
        this.fireCount       = 0L;
        this.lastFiredAt     = null;
    }

    /**
     * Computes the next occurrence strictly after {@code after}.
     *
     * @param after the reference instant (exclusive lower bound)
     * @return the next scheduled instant
     * @throws SchedulingException if this schedule is inactive or no occurrence can be found
     */
    public Instant nextOccurrence(Instant after) throws SchedulingException {
        if (!active) {
            throw new SchedulingException("RecurringSchedule " + id + " is not active");
        }
        return cronExpression.nextAfter(after);
    }

    /**
     * Records a successful firing at {@code firedAt} and returns the next occurrence.
     *
     * @param firedAt the instant at which the case was launched
     * @return the next scheduled instant
     * @throws SchedulingException if the next occurrence cannot be computed
     */
    public Instant recordFiring(Instant firedAt) throws SchedulingException {
        Objects.requireNonNull(firedAt, "firedAt must not be null");
        this.lastFiredAt = firedAt;
        this.fireCount++;
        return nextOccurrence(firedAt);
    }

    /** Deactivates this schedule; further calls to {@link #nextOccurrence} will throw. */
    public void cancel() { this.active = false; }

    /** Returns the unique schedule ID. */
    public String getId() { return id; }

    /** Returns the specification ID that is launched on each occurrence. */
    public String getSpecificationId() { return specificationId; }

    /** Returns the optional XML case data, or {@code null}. */
    public String getCaseData() { return caseData; }

    /** Returns the underlying cron expression. */
    public CronExpression getCronExpression() { return cronExpression; }

    /** Returns {@code true} if this schedule is active. */
    public boolean isActive() { return active; }

    /** Returns the number of times this schedule has fired. */
    public long getFireCount() { return fireCount; }

    /** Returns the instant of the last firing, or {@code null} if it has never fired. */
    public Instant getLastFiredAt() { return lastFiredAt; }

    @Override
    public String toString() {
        return "RecurringSchedule{id=" + id
            + ", spec=" + specificationId
            + ", cron=" + cronExpression.getExpression()
            + ", active=" + active
            + ", fireCount=" + fireCount + "}";
    }
}
