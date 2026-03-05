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
package org.yawlfoundation.yawl.erlang.processmining;

import java.util.List;

/**
 * Result record for DFG (Directly-Follows Graph) discovery operations.
 *
 * <p>Represents a process model discovered from an event log,
 * containing activity nodes and transition edges with frequencies.</p>
 *
 * @param success  whether the discovery succeeded
 * @param nodes    list of activity nodes in the DFG
 * @param edges    list of directly-follows edges between activities
 * @param error    error message if discovery failed
 */
public record DfgResult(
    boolean success,
    List<DfgNode> nodes,
    List<DfgEdge> edges,
    String error
) {
    /**
     * Creates a successful DFG result.
     */
    public static DfgResult success(List<DfgNode> nodes, List<DfgEdge> edges) {
        return new DfgResult(true, nodes, edges, null);
    }

    /**
     * Creates a failed DFG result.
     */
    public static DfgResult error(String error) {
        return new DfgResult(false, List.of(), List.of(), error);
    }
}
