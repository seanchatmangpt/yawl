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

import java.util.List;
import java.util.Map;

/**
 * Occupational profile derived from COPM assessment.
 *
 * <p>The Canadian Occupational Performance Measure (COPM) uses a 10-point scale
 * for both performance (1=unable to do, 10=able to do extremely well) and
 * satisfaction (1=not satisfied at all, 10=extremely satisfied).</p>
 *
 * @param patientId reference to the assessed patient
 * @param performanceScores area name to COPM performance score (1-10)
 * @param satisfactionScores area name to COPM satisfaction score (1-10)
 * @param priorityAreas ordered list of occupational areas by priority
 * @param assessmentNotes qualitative notes from the assessment interview
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record OccupationalProfile(
    String patientId,
    Map<String, Integer> performanceScores,
    Map<String, Integer> satisfactionScores,
    List<String> priorityAreas,
    String assessmentNotes
) {
    /** Canonical constructor with validation. */
    public OccupationalProfile {
        if (patientId == null || patientId.isBlank()) throw new IllegalArgumentException("Patient ID required");
        if (performanceScores == null || performanceScores.isEmpty()) throw new IllegalArgumentException("Performance scores required");
        if (satisfactionScores == null || satisfactionScores.isEmpty()) throw new IllegalArgumentException("Satisfaction scores required");
        if (priorityAreas == null || priorityAreas.isEmpty()) throw new IllegalArgumentException("Priority areas required");
        if (assessmentNotes == null) assessmentNotes = "";
        // Validate score ranges
        performanceScores.forEach((k, v) -> {
            if (v < 1 || v > 10) throw new IllegalArgumentException("Performance score out of range for: " + k);
        });
        satisfactionScores.forEach((k, v) -> {
            if (v < 1 || v > 10) throw new IllegalArgumentException("Satisfaction score out of range for: " + k);
        });
        // Make defensive copies
        performanceScores = Map.copyOf(performanceScores);
        satisfactionScores = Map.copyOf(satisfactionScores);
        priorityAreas = List.copyOf(priorityAreas);
    }

    /** Computes mean COPM performance score across all areas. */
    public double meanPerformanceScore() {
        return performanceScores.values().stream()
            .mapToInt(Integer::intValue)
            .average()
            .orElseThrow(() -> new IllegalStateException("No performance scores"));
    }

    /** Computes mean COPM satisfaction score across all areas. */
    public double meanSatisfactionScore() {
        return satisfactionScores.values().stream()
            .mapToInt(Integer::intValue)
            .average()
            .orElseThrow(() -> new IllegalStateException("No satisfaction scores"));
    }

    /** Returns the highest-priority occupational area. */
    public String topPriorityArea() {
        return priorityAreas.getFirst();
    }
}
