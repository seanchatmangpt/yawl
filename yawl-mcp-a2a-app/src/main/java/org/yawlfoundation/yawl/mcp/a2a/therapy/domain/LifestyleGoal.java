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
 * A SMART lifestyle goal identified through collaborative goal-setting.
 *
 * <p>Goals are derived from the COPM priority areas and formulated as
 * Specific, Measurable, Achievable, Relevant, Time-bound (SMART) objectives.</p>
 *
 * @param id unique goal identifier
 * @param description SMART description of the goal
 * @param priority priority ranking (1 = highest)
 * @param targetArea occupational performance area (self-care, productivity, leisure)
 * @param measurementCriteria how goal attainment will be measured
 * @param targetWeeks expected weeks to achieve goal
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record LifestyleGoal(
    String id,
    String description,
    int priority,
    String targetArea,
    String measurementCriteria,
    int targetWeeks
) {
    /** Canonical constructor with validation. */
    public LifestyleGoal {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Goal id required");
        if (description == null || description.isBlank()) throw new IllegalArgumentException("Goal description required");
        if (priority < 1) throw new IllegalArgumentException("Priority must be >= 1");
        if (targetArea == null || targetArea.isBlank()) throw new IllegalArgumentException("Target area required");
        if (measurementCriteria == null || measurementCriteria.isBlank()) throw new IllegalArgumentException("Measurement criteria required");
        if (targetWeeks < 1 || targetWeeks > 52) throw new IllegalArgumentException("Target weeks must be 1-52");
    }

    /** Returns whether this is the primary (highest priority) goal. */
    public boolean isPrimary() {
        return priority == 1;
    }

    /** Returns a brief label suitable for UI display. */
    public String label() {
        return "G" + priority + ": " + targetArea + " (" + targetWeeks + "w)";
    }
}
