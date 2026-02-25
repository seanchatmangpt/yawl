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

import java.util.List;

/**
 * Immutable representation of a resource assignment optimization problem.
 *
 * <p>Defines work items to be assigned to resources with associated costs.
 * The cost matrix is indexed as [workItemIndex][resourceIndex].</p>
 *
 * @param workItemIds list of work item identifiers
 * @param resourceIds list of resource identifiers
 * @param costMatrix 2D cost matrix where costMatrix[i][j] is the cost
 *                   of assigning workItemIds[i] to resourceIds[j]
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record AssignmentProblem(
    List<String> workItemIds,
    List<String> resourceIds,
    double[][] costMatrix
) {
    /**
     * Compact constructor: validate dimensions match.
     *
     * @throws IllegalArgumentException if dimensions don't match
     */
    public AssignmentProblem {
        if (workItemIds.size() != costMatrix.length) {
            throw new IllegalArgumentException(
                "workItemIds count (" + workItemIds.size() +
                ") must match costMatrix rows (" + costMatrix.length + ")"
            );
        }
        if (costMatrix.length > 0 && resourceIds.size() != costMatrix[0].length) {
            throw new IllegalArgumentException(
                "resourceIds count (" + resourceIds.size() +
                ") must match costMatrix columns (" + costMatrix[0].length + ")"
            );
        }
    }
}
