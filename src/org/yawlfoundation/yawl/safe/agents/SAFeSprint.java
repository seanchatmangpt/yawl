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

package org.yawlfoundation.yawl.safe.agents;

import java.time.LocalDate;
import java.util.List;

/**
 * SAFe sprint representation (Java 25 record).
 *
 * <p>Immutable data class for sprint planning and execution with:
 * <ul>
 *   <li>Sprint ID and number</li>
 *   <li>Start and end dates</li>
 *   <li>List of assigned user stories</li>
 *   <li>Capacity metrics (committed vs actual points)</li>
 *   <li>Sprint goal and status</li>
 * </ul>
 *
 * @param id unique sprint identifier
 * @param sprintNumber numeric sprint number in sequence
 * @param startDate sprint start date
 * @param endDate sprint end date
 * @param sprintGoal overall sprint objective
 * @param assignedStories list of user story IDs in sprint
 * @param committedPoints total story points committed
 * @param completedPoints story points actually completed
 * @param status sprint status (planned, active, complete, cancelled)
 * @param scrumMasterId ID of responsible Scrum Master
 * @since YAWL 6.0
 */
public record SAFeSprint(
        String id,
        int sprintNumber,
        LocalDate startDate,
        LocalDate endDate,
        String sprintGoal,
        List<String> assignedStories,
        int committedPoints,
        int completedPoints,
        String status,
        String scrumMasterId
) {

    /**
     * Canonical constructor with validation.
     */
    public SAFeSprint {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Sprint id is required");
        }
        if (sprintNumber <= 0) {
            throw new IllegalArgumentException("Sprint number must be positive");
        }
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Sprint dates are required");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Sprint start date must be before end date");
        }
        if (assignedStories == null) {
            assignedStories = List.of();
        }
        if (committedPoints < 0 || completedPoints < 0) {
            throw new IllegalArgumentException("Points must be non-negative");
        }
    }

    /**
     * Calculate sprint velocity (completed points / duration in days).
     *
     * @return velocity as points per day
     */
    public double velocity() {
        long days = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate);
        if (days == 0) {
            return 0;
        }
        return (double) completedPoints / days;
    }

    /**
     * Check if sprint is active (within date range).
     *
     * @param now current date
     * @return true if sprint is currently active
     */
    public boolean isActive(LocalDate now) {
        return !now.isBefore(startDate) && !now.isAfter(endDate);
    }

    /**
     * Calculate burn-down rate (% of committed points completed).
     *
     * @return percentage complete (0-100)
     */
    public int burnDownPercentage() {
        if (committedPoints == 0) {
            return 0;
        }
        return (completedPoints * 100) / committedPoints;
    }
}
