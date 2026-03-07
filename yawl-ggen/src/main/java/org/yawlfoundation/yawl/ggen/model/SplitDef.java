/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.ggen.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Split/join definition in a ProcessGraph.
 *
 * @param nodeId Gateway node ID
 * @param branches Branch target node IDs
 * @param conditions Branch conditions (parallel to branches list)
 * @param metadata Additional metadata (optional)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SplitDef(
    String nodeId,
    List<String> branches,
    List<String> conditions,
    Map<String, Object> metadata
) {

    public SplitDef {
        branches = branches != null ? List.copyOf(branches) : List.of();
        conditions = conditions != null ? List.copyOf(conditions) : List.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Create a simple split definition.
     */
    public static SplitDef of(String nodeId, List<String> branches) {
        return new SplitDef(nodeId, branches, List.of(), Map.of());
    }

    /**
     * Create a conditional split definition.
     */
    public static SplitDef conditional(String nodeId, List<String> branches, List<String> conditions) {
        return new SplitDef(nodeId, branches, conditions, Map.of());
    }

    /**
     * Get branch count.
     */
    public int branchCount() {
        return branches.size();
    }

    /**
     * Check if branches have conditions.
     */
    public boolean hasConditions() {
        return !conditions.isEmpty();
    }
}
