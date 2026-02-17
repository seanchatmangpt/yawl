/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.engine.interfce;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.yawlfoundation.yawl.util.StringUtil;

/**
 * Immutable timing information for a work item.
 * This record encapsulates all timestamp data for the work item lifecycle,
 * storing times as millisecond strings for database compatibility.
 *
 * @param enablementTimeMs Timestamp when the work item was enabled (milliseconds as string)
 * @param firingTimeMs Timestamp when the work item was fired (milliseconds as string)
 * @param startTimeMs Timestamp when the work item was started (milliseconds as string)
 * @param completionTimeMs Timestamp when the work item was completed (milliseconds as string)
 * @param timerTrigger Timer trigger value if a timer is enabled
 * @param timerExpiry Timer expiry value if a timer is enabled
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public record WorkItemTiming(
    String enablementTimeMs,
    String firingTimeMs,
    String startTimeMs,
    String completionTimeMs,
    String timerTrigger,
    String timerExpiry
) {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM dd yyyy H:mm:ss").withZone(ZoneId.systemDefault());

    /**
     * Default constructor for work item with no timing information.
     */
    public WorkItemTiming() {
        this(null, null, null, null, null, null);
    }

    /**
     * Constructor for basic timing without timer information.
     */
    public WorkItemTiming(String enablementTimeMs, String firingTimeMs,
                         String startTimeMs, String completionTimeMs) {
        this(enablementTimeMs, firingTimeMs, startTimeMs, completionTimeMs, null, null);
    }

    /**
     * Returns the enablement time formatted for display.
     */
    public String getEnablementTime() {
        return formatTime(enablementTimeMs);
    }

    /**
     * Returns the firing time formatted for display.
     */
    public String getFiringTime() {
        return formatTime(firingTimeMs);
    }

    /**
     * Returns the start time formatted for display.
     */
    public String getStartTime() {
        return formatTime(startTimeMs);
    }

    /**
     * Returns the completion time formatted for display.
     */
    public String getCompletionTime() {
        return formatTime(completionTimeMs);
    }

    /**
     * Creates a new timing record with updated enablement time.
     */
    public WorkItemTiming withEnablementTime(String timeMs) {
        return new WorkItemTiming(timeMs, firingTimeMs, startTimeMs, completionTimeMs,
                                 timerTrigger, timerExpiry);
    }

    /**
     * Creates a new timing record with updated firing time.
     */
    public WorkItemTiming withFiringTime(String timeMs) {
        return new WorkItemTiming(enablementTimeMs, timeMs, startTimeMs, completionTimeMs,
                                 timerTrigger, timerExpiry);
    }

    /**
     * Creates a new timing record with updated start time.
     */
    public WorkItemTiming withStartTime(String timeMs) {
        return new WorkItemTiming(enablementTimeMs, firingTimeMs, timeMs, completionTimeMs,
                                 timerTrigger, timerExpiry);
    }

    /**
     * Creates a new timing record with updated completion time.
     */
    public WorkItemTiming withCompletionTime(String timeMs) {
        return new WorkItemTiming(enablementTimeMs, firingTimeMs, startTimeMs, timeMs,
                                 timerTrigger, timerExpiry);
    }

    /**
     * Creates a new timing record with updated timer trigger.
     */
    public WorkItemTiming withTimerTrigger(String trigger) {
        return new WorkItemTiming(enablementTimeMs, firingTimeMs, startTimeMs, completionTimeMs,
                                 trigger, timerExpiry);
    }

    /**
     * Creates a new timing record with updated timer expiry.
     */
    public WorkItemTiming withTimerExpiry(String expiry) {
        return new WorkItemTiming(enablementTimeMs, firingTimeMs, startTimeMs, completionTimeMs,
                                 timerTrigger, expiry);
    }

    /**
     * Formats a millisecond timestamp string for display.
     * Returns null if the timestamp is not set or invalid.
     */
    private String formatTime(String msStr) {
        if (StringUtil.isNullOrEmpty(msStr)) {
            return null;
        }
        long ms = StringUtil.strToLong(msStr, 0);
        if (ms <= 0) {
            return null;
        }
        return DATE_FORMAT.format(Instant.ofEpochMilli(ms));
    }
}
