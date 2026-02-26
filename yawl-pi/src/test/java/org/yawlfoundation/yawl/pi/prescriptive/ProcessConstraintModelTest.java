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

package org.yawlfoundation.yawl.pi.prescriptive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Unit tests for ProcessConstraintModel.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class ProcessConstraintModelTest {

    private ProcessConstraintModel constraintModel;

    @BeforeEach
    public void setUp() {
        constraintModel = new ProcessConstraintModel();
    }

    @Test
    public void testEmptyModelAllowsAnyAction() {
        String caseId = "case-001";
        RerouteAction action = new RerouteAction(
            caseId,
            "task-a",
            "task-b",
            "Reroute to alternate task",
            0.7
        );

        assertTrue(constraintModel.isFeasible(action),
            "Empty constraint model should allow any action");
    }

    @Test
    public void testPopulateFrom() {
        String specId = "test-spec";
        List<String> taskNames = List.of("task-a", "task-b", "task-c");

        constraintModel.populateFrom(specId, taskNames);

        List<String> precedences = constraintModel.getTaskPrecedences("task-a");
        assertFalse(precedences == null, "Should return non-null list");
    }

    @Test
    public void testIsFeasibleForEscalateAction() {
        String caseId = "case-002";
        EscalateAction action = new EscalateAction(
            caseId,
            "task-a",
            "manager_group",
            "Escalate for urgent review",
            0.8
        );

        assertTrue(constraintModel.isFeasible(action),
            "EscalateAction should always be feasible");
    }

    @Test
    public void testIsFeasibleForReallocateAction() {
        String caseId = "case-003";
        ReallocateResourceAction action = new ReallocateResourceAction(
            caseId,
            "task-a",
            "resource-1",
            "resource-2",
            "Reallocate to better resource",
            0.6
        );

        assertTrue(constraintModel.isFeasible(action),
            "ReallocateResourceAction should always be feasible");
    }

    @Test
    public void testIsFeasibleForNoOpAction() {
        String caseId = "case-004";
        NoOpAction action = new NoOpAction(
            caseId,
            "No intervention needed",
            0.1
        );

        assertTrue(constraintModel.isFeasible(action),
            "NoOpAction should always be feasible");
    }
}
