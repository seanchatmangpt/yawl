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

/**
 * Insight contributed by a GregVerse business advisor agent.
 *
 * <p>Captures the output from a single advisor agent after the self-play run,
 * tagged to the occupational therapy goal area most relevant to that agent's
 * domain of expertise.</p>
 *
 * @param targetArea  OT area this advisor covers ("productivity", "self-care", "leisure")
 * @param agentId     GregVerse agent identifier (e.g. "greg-isenberg")
 * @param agentName   human-readable display name
 * @param insight     the advisor's output narrative (may be empty if agent failed)
 * @param success     true if the agent produced a non-error response
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record AdvisorInsight(
    String targetArea,
    String agentId,
    String agentName,
    String insight,
    boolean success
) {
    /** Canonical constructor with validation. */
    public AdvisorInsight {
        if (targetArea == null || targetArea.isBlank()) {
            throw new IllegalArgumentException("AdvisorInsight.targetArea must be non-blank");
        }
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("AdvisorInsight.agentId must be non-blank");
        }
        if (agentName == null || agentName.isBlank()) {
            throw new IllegalArgumentException("AdvisorInsight.agentName must be non-blank");
        }
        if (insight == null) {
            insight = "";
        }
    }

    /** Returns true if the insight text is non-empty. */
    public boolean hasContent() {
        return !insight.isBlank();
    }
}
