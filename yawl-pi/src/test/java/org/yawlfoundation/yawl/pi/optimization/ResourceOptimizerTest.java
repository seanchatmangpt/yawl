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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.pi.PIException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ResourceOptimizer.
 *
 * Tests verify that the Hungarian Algorithm correctly solves assignment
 * problems of various sizes and cost structures.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
class ResourceOptimizerTest {

    private ResourceOptimizer optimizer;

    @BeforeEach
    void setUp() {
        optimizer = new ResourceOptimizer();
    }

    @Test
    void testSolve_2x2OptimalAssignment() throws PIException {
        // Cost matrix: [[5,4],[3,6]]
        // Optimal: item0->resource1 (cost 4) + item1->resource0 (cost 3) = 7
        double[][] costMatrix = {{5, 4}, {3, 6}};
        AssignmentProblem problem = new AssignmentProblem(
            List.of("item0", "item1"),
            List.of("res0", "res1"),
            costMatrix
        );

        AssignmentSolution solution = optimizer.solve(problem);

        assertNotNull(solution);
        assertEquals(2, solution.assignments().size());
        assertEquals(7.0, solution.totalCost(), 0.01);
        assertTrue(solution.solveTimeMs() >= 0);
    }

    @Test
    void testSolve_IdentityCostMatrix() throws PIException {
        // Cost matrix where diagonal is 0, off-diagonal is 1
        // Optimal: each item assigned to matching resource
        double[][] costMatrix = {
            {0, 1, 1},
            {1, 0, 1},
            {1, 1, 0}
        };
        AssignmentProblem problem = new AssignmentProblem(
            List.of("item0", "item1", "item2"),
            List.of("res0", "res1", "res2"),
            costMatrix
        );

        AssignmentSolution solution = optimizer.solve(problem);

        assertNotNull(solution);
        assertEquals(3, solution.assignments().size());
        assertEquals(0.0, solution.totalCost(), 0.01);
    }

    @Test
    void testSolve_SingleItemSingleResource() throws PIException {
        double[][] costMatrix = {{5.0}};
        AssignmentProblem problem = new AssignmentProblem(
            List.of("item0"),
            List.of("res0"),
            costMatrix
        );

        AssignmentSolution solution = optimizer.solve(problem);

        assertNotNull(solution);
        assertEquals(1, solution.assignments().size());
        assertEquals("res0", solution.assignments().get("item0"));
        assertEquals(5.0, solution.totalCost(), 0.01);
    }

    @Test
    void testSolve_EmptyProblem() throws PIException {
        AssignmentProblem problem = new AssignmentProblem(
            List.of(),
            List.of(),
            new double[0][0]
        );

        AssignmentSolution solution = optimizer.solve(problem);

        assertNotNull(solution);
        assertEquals(0, solution.assignments().size());
        assertEquals(0.0, solution.totalCost());
    }

    @Test
    void testSolve_NullProblemThrows() {
        assertThrows(PIException.class, () -> {
            optimizer.solve(null);
        });
    }

    @Test
    void testSolve_AllAssignmentsExist() throws PIException {
        // Every work item should be assigned to exactly one resource
        double[][] costMatrix = {
            {1, 2, 3},
            {4, 5, 6},
            {7, 8, 9}
        };
        AssignmentProblem problem = new AssignmentProblem(
            List.of("w1", "w2", "w3"),
            List.of("r1", "r2", "r3"),
            costMatrix
        );

        AssignmentSolution solution = optimizer.solve(problem);

        assertEquals(3, solution.assignments().size());
        for (String workItem : List.of("w1", "w2", "w3")) {
            assertTrue(solution.assignments().containsKey(workItem));
            assertNotNull(solution.assignments().get(workItem));
        }
    }

    @Test
    void testSolve_LargerProblem() throws PIException {
        // 5x5 problem - should still solve correctly
        double[][] costMatrix = {
            {10, 19, 8, 15, 20},
            {10, 18, 7, 17, 19},
            {13, 16, 9, 14, 15},
            {12, 19, 8, 18, 21},
            {14, 17, 10, 19, 16}
        };
        AssignmentProblem problem = new AssignmentProblem(
            List.of("w1", "w2", "w3", "w4", "w5"),
            List.of("r1", "r2", "r3", "r4", "r5"),
            costMatrix
        );

        AssignmentSolution solution = optimizer.solve(problem);

        assertEquals(5, solution.assignments().size());
        assertTrue(solution.totalCost() > 0);
        assertTrue(solution.solveTimeMs() >= 0);
    }

    @Test
    void testSolve_UniformCosts() throws PIException {
        // All costs the same - any assignment is optimal
        double[][] costMatrix = {
            {5, 5},
            {5, 5}
        };
        AssignmentProblem problem = new AssignmentProblem(
            List.of("item0", "item1"),
            List.of("res0", "res1"),
            costMatrix
        );

        AssignmentSolution solution = optimizer.solve(problem);

        assertEquals(2, solution.assignments().size());
        assertEquals(10.0, solution.totalCost(), 0.01);
    }
}
