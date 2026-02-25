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

package org.yawlfoundation.yawl.pi.optimization;

import java.util.Map;

/**
 * Immutable result of an optimal resource assignment computation.
 *
 * <p>Contains the mapping of work items to resources, the total cost
 * of the assignment, and the time taken to compute the solution.</p>
 *
 * @param assignments mapping from workItemId to resourceId
 * @param totalCost sum of costs for all assignments
 * @param solveTimeMs time in milliseconds to compute the solution
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record AssignmentSolution(
    Map<String, String> assignments,
    double totalCost,
    long solveTimeMs
) {}
