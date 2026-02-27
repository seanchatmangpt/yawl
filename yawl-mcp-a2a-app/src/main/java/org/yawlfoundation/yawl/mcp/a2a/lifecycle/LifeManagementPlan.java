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

import org.yawlfoundation.yawl.mcp.a2a.therapy.domain.LifestyleGoal;

import java.util.List;
import java.util.Optional;

/**
 * Synthesised life management plan combining all three autonomous pillars.
 *
 * <p>The plan merges:</p>
 * <ul>
 *   <li>OT-assessed lifestyle goals (COPM-grounded, SMART-formatted)</li>
 *   <li>Van der Aalst WCP insights (workflow structure for executing the plan)</li>
 *   <li>GregVerse advisor insights (business / productivity strategies per goal area)</li>
 * </ul>
 *
 * @param title            descriptive title for this plan
 * @param otGoals          SMART lifestyle goals produced by the OT swarm
 * @param wcpInsights      workflow pattern insights from the van der Aalst demo
 * @param advisorInsights  GregVerse advisor outputs mapped to goal areas
 * @param narrative        human-readable synthesis paragraph
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record LifeManagementPlan(
    String title,
    List<LifestyleGoal> otGoals,
    List<WcpPatternInsight> wcpInsights,
    List<AdvisorInsight> advisorInsights,
    String narrative
) {
    /** Canonical constructor with validation and defensive copying. */
    public LifeManagementPlan {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("LifeManagementPlan.title must be non-blank");
        }
        if (otGoals == null) {
            throw new IllegalArgumentException("LifeManagementPlan.otGoals must not be null");
        }
        if (wcpInsights == null) {
            throw new IllegalArgumentException("LifeManagementPlan.wcpInsights must not be null");
        }
        if (advisorInsights == null) {
            throw new IllegalArgumentException("LifeManagementPlan.advisorInsights must not be null");
        }
        if (narrative == null || narrative.isBlank()) {
            throw new IllegalArgumentException("LifeManagementPlan.narrative must be non-blank");
        }
        otGoals = List.copyOf(otGoals);
        wcpInsights = List.copyOf(wcpInsights);
        advisorInsights = List.copyOf(advisorInsights);
    }

    /**
     * Returns the primary OT goal (priority == 1), if any.
     *
     * @return optional primary goal
     */
    public Optional<LifestyleGoal> primaryGoal() {
        return otGoals.stream().filter(LifestyleGoal::isPrimary).findFirst();
    }

    /**
     * Returns WCP insights for the specified goal area (case-insensitive).
     *
     * @param area OT target area (e.g. "productivity")
     * @return matching WCP insights (may be empty)
     */
    public List<WcpPatternInsight> wcpForArea(String area) {
        if (area == null) return List.of();
        return wcpInsights.stream()
            .filter(w -> w.goalArea().equalsIgnoreCase(area))
            .toList();
    }

    /**
     * Returns advisor insights for the specified goal area (case-insensitive).
     *
     * @param area OT target area (e.g. "productivity")
     * @return matching advisor insights (may be empty)
     */
    public List<AdvisorInsight> advisorForArea(String area) {
        if (area == null) return List.of();
        return advisorInsights.stream()
            .filter(a -> a.targetArea().equalsIgnoreCase(area))
            .toList();
    }

    /** Returns total number of unique insights across all three pillars. */
    public int totalInsightCount() {
        return otGoals.size() + wcpInsights.size() + advisorInsights.size();
    }
}
