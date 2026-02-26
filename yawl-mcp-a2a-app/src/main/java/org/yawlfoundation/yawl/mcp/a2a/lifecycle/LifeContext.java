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

package org.yawlfoundation.yawl.mcp.a2a.lifecycle;

import java.util.List;

/**
 * Minimal user input for the zero cognitive load life management system.
 *
 * <p>This is the only input a user provides. The orchestrator derives all downstream
 * configuration — OT patient profile, WCP pattern selection, and GregVerse advisor
 * routing — from this single record.</p>
 *
 * @param name                 full name of the person (non-blank)
 * @param age                  chronological age 1–120
 * @param focusAreas           1–5 life areas to optimise (e.g. "productivity", "health")
 * @param weeklyHoursAvailable hours per week available for improvement work (1–168)
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record LifeContext(
    String name,
    int age,
    List<String> focusAreas,
    int weeklyHoursAvailable
) {
    /** Canonical constructor with full validation. */
    public LifeContext {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("LifeContext.name must be non-blank");
        }
        if (age < 1 || age > 120) {
            throw new IllegalArgumentException("LifeContext.age must be 1–120, got: " + age);
        }
        if (focusAreas == null || focusAreas.isEmpty()) {
            throw new IllegalArgumentException("LifeContext.focusAreas must contain at least one area");
        }
        if (focusAreas.size() > 5) {
            throw new IllegalArgumentException("LifeContext.focusAreas must not exceed 5 areas");
        }
        for (String area : focusAreas) {
            if (area == null || area.isBlank()) {
                throw new IllegalArgumentException("LifeContext.focusAreas must not contain blank entries");
            }
        }
        if (weeklyHoursAvailable < 1 || weeklyHoursAvailable > 168) {
            throw new IllegalArgumentException(
                "LifeContext.weeklyHoursAvailable must be 1–168, got: " + weeklyHoursAvailable);
        }
        focusAreas = List.copyOf(focusAreas);
    }

    /**
     * Factory method for concise construction.
     *
     * @param name                 full name
     * @param age                  age in years
     * @param focusAreas           life areas to optimise
     * @param weeklyHoursAvailable hours per week available
     * @return validated LifeContext instance
     */
    public static LifeContext of(String name, int age, List<String> focusAreas,
                                 int weeklyHoursAvailable) {
        return new LifeContext(name, age, focusAreas, weeklyHoursAvailable);
    }

    /** Returns the primary focus area (first in the list). */
    public String primaryFocusArea() {
        return focusAreas.getFirst();
    }

    /** Returns true if this context includes the given area (case-insensitive). */
    public boolean hasFocusArea(String area) {
        if (area == null) return false;
        return focusAreas.stream().anyMatch(a -> a.equalsIgnoreCase(area));
    }
}
