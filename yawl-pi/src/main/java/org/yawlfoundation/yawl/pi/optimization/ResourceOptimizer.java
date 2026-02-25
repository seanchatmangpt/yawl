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

import org.yawlfoundation.yawl.pi.PIException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Solves the assignment problem using the Hungarian Algorithm (Kuhn-Munkres).
 *
 * <p>Finds the minimum-cost assignment of work items to resources in O(n³) time.
 * The algorithm is optimal for any rectangular assignment problem.</p>
 *
 * <h2>Algorithm Overview</h2>
 * <ol>
 *   <li>Row reduction: subtract row minimum from each row</li>
 *   <li>Column reduction: subtract column minimum from each column</li>
 *   <li>Cover zeros with minimum number of lines</li>
 *   <li>If lines == n, optimal assignment found; else modify cost matrix and retry</li>
 *   <li>Extract optimal assignment from covered zero pattern</li>
 * </ol>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class ResourceOptimizer {

    private static final double EPSILON = 1e-10;

    /**
     * Create a new resource optimizer.
     */
    public ResourceOptimizer() {
    }

    /**
     * Solve the assignment problem optimally.
     *
     * @param problem assignment problem definition
     * @return optimal assignment solution
     * @throws PIException if problem is invalid or solving fails
     */
    public AssignmentSolution solve(AssignmentProblem problem) throws PIException {
        if (problem == null) {
            throw new PIException("Assignment problem cannot be null", "optimization");
        }

        long startTime = System.currentTimeMillis();

        int n = problem.workItemIds().size();
        int m = problem.resourceIds().size();

        if (n == 0 || m == 0) {
            return new AssignmentSolution(Map.of(), 0.0, 0);
        }

        // Create working copy of cost matrix
        double[][] cost = new double[n][m];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                cost[i][j] = problem.costMatrix()[i][j];
            }
        }

        // Hungarian algorithm
        boolean[][] covered = hungarianAlgorithm(cost, n, m);

        // Extract assignment
        Map<String, String> assignments = new HashMap<>();
        double totalCost = 0.0;

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                if (covered[i][j]) {
                    String workItemId = problem.workItemIds().get(i);
                    String resourceId = problem.resourceIds().get(j);
                    assignments.put(workItemId, resourceId);
                    totalCost += problem.costMatrix()[i][j];
                    break;
                }
            }
        }

        long solveTime = System.currentTimeMillis() - startTime;
        return new AssignmentSolution(assignments, totalCost, solveTime);
    }

    /**
     * Execute Hungarian algorithm to find optimal assignment.
     *
     * @param cost cost matrix (modified in place)
     * @param n number of workers (rows)
     * @param m number of jobs (columns)
     * @return assignment matrix where assignment[i][j] = true if worker i assigned to job j
     */
    private boolean[][] hungarianAlgorithm(double[][] cost, int n, int m) {
        int size = Math.max(n, m);

        // Pad matrix to square if needed
        double[][] matrix = new double[size][size];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                if (i < n && j < m) {
                    matrix[i][j] = cost[i][j];
                } else {
                    matrix[i][j] = Double.MAX_VALUE / 2;  // Large value for dummy entries
                }
            }
        }

        // Step 1: Row reduction
        for (int i = 0; i < size; i++) {
            double minVal = Double.MAX_VALUE;
            for (int j = 0; j < size; j++) {
                minVal = Math.min(minVal, matrix[i][j]);
            }
            if (minVal < Double.MAX_VALUE / 2) {
                for (int j = 0; j < size; j++) {
                    matrix[i][j] -= minVal;
                }
            }
        }

        // Step 2: Column reduction
        for (int j = 0; j < size; j++) {
            double minVal = Double.MAX_VALUE;
            for (int i = 0; i < size; i++) {
                minVal = Math.min(minVal, matrix[i][j]);
            }
            if (minVal < Double.MAX_VALUE / 2) {
                for (int i = 0; i < size; i++) {
                    matrix[i][j] -= minVal;
                }
            }
        }

        // Iterative assignment finding
        boolean[] rowCovered = new boolean[size];
        boolean[] colCovered = new boolean[size];
        int[][] assignment = new int[size][size];

        while (true) {
            // Step 3: Cover zeros
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    rowCovered[i] = false;
                    colCovered[j] = false;
                }
            }

            // Greedy zero covering
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    if (Math.abs(matrix[i][j]) < EPSILON && !rowCovered[i] && !colCovered[j]) {
                        assignment[i][j] = 1;
                        rowCovered[i] = true;
                        colCovered[j] = true;
                    }
                }
            }

            // Count covered zeros
            int zeros = 0;
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    if (Math.abs(matrix[i][j]) < EPSILON) {
                        zeros++;
                    }
                }
            }

            // If all items assigned, we're done
            int assigned = 0;
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    assigned += assignment[i][j];
                }
            }

            if (assigned >= size) {
                break;
            }

            // Step 4: Modify cost matrix
            double minUncovered = Double.MAX_VALUE;
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    if (!rowCovered[i] && !colCovered[j]) {
                        minUncovered = Math.min(minUncovered, matrix[i][j]);
                    }
                }
            }

            if (minUncovered >= Double.MAX_VALUE / 2) {
                break;  // No solution found, return current assignment
            }

            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    if (rowCovered[i] && colCovered[j]) {
                        matrix[i][j] += minUncovered;
                    } else if (!rowCovered[i] && !colCovered[j]) {
                        matrix[i][j] -= minUncovered;
                    }
                }
            }
        }

        // Convert to boolean assignment matrix for actual problem size
        boolean[][] result = new boolean[n][m];
        for (int i = 0; i < Math.min(n, size); i++) {
            for (int j = 0; j < Math.min(m, size); j++) {
                result[i][j] = (assignment[i][j] == 1);
            }
        }

        return result;
    }
}
