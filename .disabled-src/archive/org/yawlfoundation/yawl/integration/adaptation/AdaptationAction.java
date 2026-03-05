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

package org.yawlfoundation.yawl.integration.adaptation;

/**
 * Actions that can be taken in response to process events in the adaptation engine.
 *
 * <p>Each action represents a different type of workflow modification or notification
 * that can be applied to an executing case.</p>
 *
 * @author YAWL Foundation
 * @since 6.0
 */
public enum AdaptationAction {
    /**
     * Immediately reject the case, terminating execution with a rejection status.
     * Used for cases that violate critical business rules or pass fraud checks.
     */
    REJECT_IMMEDIATELY("Immediately reject the case"),

    /**
     * Escalate the case to manual review by a human operator.
     * Used for cases with high-risk characteristics that require human judgment.
     */
    ESCALATE_TO_MANUAL("Escalate case to manual review"),

    /**
     * Pause case execution to allow investigation or approval before continuing.
     * The case can be resumed later from its paused point.
     */
    PAUSE_CASE("Pause case execution"),

    /**
     * Cancel the case, terminating execution and marking the case as cancelled.
     * Used when a case becomes invalid or impossible to complete.
     */
    CANCEL_CASE("Cancel the case"),

    /**
     * Reroute the case to a different subprocess or alternative workflow path.
     * Used when the current path is no longer appropriate or optimal.
     */
    REROUTE_TO_SUBPROCESS("Reroute to alternative subprocess"),

    /**
     * Notify stakeholders (users, systems, audit logs) about the event.
     * Used for alerting interested parties without modifying case execution.
     */
    NOTIFY_STAKEHOLDERS("Notify stakeholders"),

    /**
     * Increase the priority of the case in the execution queue.
     * Used when events indicate the case requires faster processing.
     */
    INCREASE_PRIORITY("Increase case priority"),

    /**
     * Decrease the priority of the case in the execution queue.
     * Used when events indicate the case can be processed more slowly.
     */
    DECREASE_PRIORITY("Decrease case priority");

    private final String description;

    AdaptationAction(String description) {
        this.description = description;
    }

    /**
     * Returns a human-readable description of this action.
     *
     * @return the description string
     */
    public String description() {
        return description;
    }
}
