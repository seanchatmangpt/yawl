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
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.wizard.a2a;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for A2ASkillMapping.
 *
 * Verifies that skill mappings are correctly created, immutable, and provide
 * accessor methods for parameter retrieval.
 */
class A2ASkillMappingTest {

    @Test
    void testSkillMappingCreation() {
        A2ASkillMapping mapping = new A2ASkillMapping(
                "task_1",
                "launch_workflow",
                "yawl-local",
                Map.of("timeout", 30, "retries", 3),
                false,
                null
        );

        assertEquals("task_1", mapping.workflowTaskSlot());
        assertEquals("launch_workflow", mapping.skillId());
        assertEquals("yawl-local", mapping.agentId());
        assertFalse(mapping.isHandoffTarget());
        assertNull(mapping.handoffSourceAgentId());
    }

    @Test
    void testSkillMappingAsHandoffTarget() {
        A2ASkillMapping mapping = new A2ASkillMapping(
                "task_review",
                "manage_workitems",
                "yawl-local",
                Map.of(),
                false,
                null
        );

        A2ASkillMapping handoffMapping = mapping.asHandoffTarget("external-agent");
        assertTrue(handoffMapping.isHandoffTarget());
        assertEquals("external-agent", handoffMapping.handoffSourceAgentId());
        assertEquals("task_review", handoffMapping.workflowTaskSlot());
        assertEquals("manage_workitems", handoffMapping.skillId());
    }

    @Test
    void testSkillMappingIsRegularTask() {
        A2ASkillMapping regularTask = new A2ASkillMapping(
                "task_1",
                "launch_workflow",
                "yawl-local",
                Map.of(),
                false,
                null
        );
        assertTrue(regularTask.isRegularTask());

        A2ASkillMapping handoffTask = new A2ASkillMapping(
                "task_2",
                "manage_workitems",
                "yawl-local",
                Map.of(),
                true,
                "external-agent"
        );
        assertFalse(handoffTask.isRegularTask());
    }

    @Test
    void testSkillParametersImmutable() {
        Map<String, Object> params = Map.of("timeout", 30);
        A2ASkillMapping mapping = new A2ASkillMapping(
                "task_1",
                "launch_workflow",
                "yawl-local",
                params,
                false,
                null
        );

        assertThrows(UnsupportedOperationException.class, () -> {
            mapping.skillParameters().put("newKey", "newValue");
        });
    }

    @Test
    void testGetParameterWithDefault() {
        A2ASkillMapping mapping = new A2ASkillMapping(
                "task_1",
                "launch_workflow",
                "yawl-local",
                Map.of("timeout", 30),
                false,
                null
        );

        assertEquals(30, mapping.getParameter("timeout", 60));
        assertEquals(60, mapping.getParameter("missing", 60));
    }

    @Test
    void testGetParameterAsString() {
        A2ASkillMapping mapping = new A2ASkillMapping(
                "task_1",
                "launch_workflow",
                "yawl-local",
                Map.of("mode", "fast", "timeout", 30),
                false,
                null
        );

        assertEquals("fast", mapping.getParameterAsString("mode"));
        assertEquals("30", mapping.getParameterAsString("timeout"));
        assertNull(mapping.getParameterAsString("missing"));
    }

    @Test
    void testGetParameterAsInteger() {
        A2ASkillMapping mapping = new A2ASkillMapping(
                "task_1",
                "launch_workflow",
                "yawl-local",
                Map.of("timeout", 30, "maxRetries", "5", "invalidNumber", "abc"),
                false,
                null
        );

        assertEquals(30, mapping.getParameterAsInteger("timeout"));
        assertEquals(5, mapping.getParameterAsInteger("maxRetries"));
        assertNull(mapping.getParameterAsInteger("invalidNumber"));
        assertNull(mapping.getParameterAsInteger("missing"));
    }

    @Test
    void testWithSkillParameters() {
        A2ASkillMapping original = new A2ASkillMapping(
                "task_1",
                "launch_workflow",
                "yawl-local",
                Map.of("timeout", 30),
                false,
                null
        );

        Map<String, Object> newParams = Map.of("timeout", 60, "retries", 2);
        A2ASkillMapping updated = original.withSkillParameters(newParams);

        // Original unchanged
        assertEquals(30, original.getParameterAsInteger("timeout"));
        assertNull(original.getParameterAsInteger("retries"));

        // New has updated params
        assertEquals(60, updated.getParameterAsInteger("timeout"));
        assertEquals(2, updated.getParameterAsInteger("retries"));

        // Other fields unchanged
        assertEquals(original.workflowTaskSlot(), updated.workflowTaskSlot());
        assertEquals(original.skillId(), updated.skillId());
        assertEquals(original.agentId(), updated.agentId());
    }

    @Test
    void testSkillMappingNullValidation() {
        assertThrows(NullPointerException.class, () ->
                new A2ASkillMapping(null, "skill", "agent", Map.of(), false, null)
        );
        assertThrows(NullPointerException.class, () ->
                new A2ASkillMapping("task", null, "agent", Map.of(), false, null)
        );
        assertThrows(NullPointerException.class, () ->
                new A2ASkillMapping("task", "skill", null, Map.of(), false, null)
        );
        assertThrows(NullPointerException.class, () ->
                new A2ASkillMapping("task", "skill", "agent", null, false, null)
        );
    }

    @Test
    void testSkillMappingBlankValidation() {
        assertThrows(IllegalArgumentException.class, () ->
                new A2ASkillMapping("", "skill", "agent", Map.of(), false, null)
        );
        assertThrows(IllegalArgumentException.class, () ->
                new A2ASkillMapping("task", "", "agent", Map.of(), false, null)
        );
        assertThrows(IllegalArgumentException.class, () ->
                new A2ASkillMapping("task", "skill", "", Map.of(), false, null)
        );
    }

    @Test
    void testHandoffTargetWithoutSource() {
        A2ASkillMapping mapping = new A2ASkillMapping(
                "task_1",
                "manage_workitems",
                "yawl-local",
                Map.of(),
                true,
                null
        );

        assertTrue(mapping.isHandoffTarget());
        assertNull(mapping.handoffSourceAgentId());
    }

    @Test
    void testComplexParameterTypes() {
        Map<String, Object> complexParams = Map.of(
                "timeout", 30,
                "retries", 3,
                "enabled", true,
                "mode", "async"
        );

        A2ASkillMapping mapping = new A2ASkillMapping(
                "task_1",
                "launch_workflow",
                "yawl-local",
                complexParams,
                false,
                null
        );

        assertEquals(30, mapping.getParameterAsInteger("timeout"));
        assertEquals("async", mapping.getParameterAsString("mode"));
        assertEquals(true, mapping.getParameter("enabled", false));
    }
}
