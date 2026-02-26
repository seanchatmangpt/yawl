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
 * A workflow control-flow pattern (WCP) insight mapped to a specific life area.
 *
 * <p>Van der Aalst's Workflow Control-flow Patterns describe how activities are
 * ordered and coordinated in a process. This record captures which pattern was
 * selected for a particular occupational therapy goal area and whether it was
 * successfully demonstrated by {@code PatternDemoRunner} in self-play mode.</p>
 *
 * @param patternId     canonical WCP identifier (e.g. "WCP-1")
 * @param patternName   human-readable pattern name (e.g. "Sequence")
 * @param goalArea      OT target area this pattern structures (e.g. "productivity")
 * @param applicability how this pattern applies to the given life area
 * @param demonstrated  true if {@code PatternDemoRunner} executed it without error
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record WcpPatternInsight(
    String patternId,
    String patternName,
    String goalArea,
    String applicability,
    boolean demonstrated
) {
    /** Canonical constructor with validation. */
    public WcpPatternInsight {
        if (patternId == null || patternId.isBlank()) {
            throw new IllegalArgumentException("WcpPatternInsight.patternId must be non-blank");
        }
        if (patternName == null || patternName.isBlank()) {
            throw new IllegalArgumentException("WcpPatternInsight.patternName must be non-blank");
        }
        if (goalArea == null || goalArea.isBlank()) {
            throw new IllegalArgumentException("WcpPatternInsight.goalArea must be non-blank");
        }
        if (applicability == null || applicability.isBlank()) {
            throw new IllegalArgumentException("WcpPatternInsight.applicability must be non-blank");
        }
    }

    /** Label suitable for display: "WCP-1 (Sequence) → productivity". */
    public String label() {
        return patternId + " (" + patternName + ") → " + goalArea;
    }
}
