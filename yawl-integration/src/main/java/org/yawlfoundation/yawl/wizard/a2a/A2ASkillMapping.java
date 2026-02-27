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

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Binding between a workflow task slot and an A2A agent skill.
 *
 * <p>Records the assignment of a specific skill (provided by an agent) to
 * a workflow task slot. Enables the wizard to configure which agent capability
 * should handle which workflow task. Supports handoff semantics where a task
 * can be a handoff target (receiving work from another agent).
 *
 * @param workflowTaskSlot unique identifier for this task in the workflow
 *                         (e.g., "task_1", "review_order")
 * @param skillId          the A2A skill ID to invoke for this task
 *                         (e.g., "launch_workflow", "manage_workitems")
 * @param agentId          which agent provides this skill
 * @param skillParameters  pre-configured parameters for the skill
 *                         (e.g., {"timeout": 30, "retries": 3})
 * @param isHandoffTarget  true if this task receives handoffs from other agents
 * @param handoffSourceAgentId agent ID that can hand off to this task
 *                         (null if not a handoff target)
 *
 * @since YAWL 6.0
 */
public record A2ASkillMapping(
        String workflowTaskSlot,
        String skillId,
        String agentId,
        Map<String, Object> skillParameters,
        boolean isHandoffTarget,
        String handoffSourceAgentId
) {
    /**
     * Compact constructor ensures immutability of mutable fields.
     */
    public A2ASkillMapping {
        Objects.requireNonNull(workflowTaskSlot, "workflowTaskSlot cannot be null");
        Objects.requireNonNull(skillId, "skillId cannot be null");
        Objects.requireNonNull(agentId, "agentId cannot be null");
        Objects.requireNonNull(skillParameters, "skillParameters cannot be null");

        if (workflowTaskSlot.isBlank()) {
            throw new IllegalArgumentException("workflowTaskSlot cannot be blank");
        }
        if (skillId.isBlank()) {
            throw new IllegalArgumentException("skillId cannot be blank");
        }
        if (agentId.isBlank()) {
            throw new IllegalArgumentException("agentId cannot be blank");
        }

        skillParameters = Collections.unmodifiableMap(Map.copyOf(skillParameters));
    }

    /**
     * Creates a new mapping with updated skill parameters.
     *
     * @param newParameters updated parameters map
     * @return new mapping with updated parameters
     * @throws NullPointerException if newParameters is null
     */
    public A2ASkillMapping withSkillParameters(Map<String, Object> newParameters) {
        Objects.requireNonNull(newParameters, "newParameters cannot be null");
        return new A2ASkillMapping(
                this.workflowTaskSlot,
                this.skillId,
                this.agentId,
                newParameters,
                this.isHandoffTarget,
                this.handoffSourceAgentId
        );
    }

    /**
     * Creates a new mapping marked as a handoff target.
     *
     * @param sourceAgentId ID of the agent that will hand off to this task
     * @return new mapping with handoff target enabled
     * @throws NullPointerException if sourceAgentId is null
     */
    public A2ASkillMapping asHandoffTarget(String sourceAgentId) {
        Objects.requireNonNull(sourceAgentId, "sourceAgentId cannot be null");
        return new A2ASkillMapping(
                this.workflowTaskSlot,
                this.skillId,
                this.agentId,
                this.skillParameters,
                true,
                sourceAgentId
        );
    }

    /**
     * Checks if this mapping represents a regular task (no handoff).
     *
     * @return true if not a handoff target
     */
    public boolean isRegularTask() {
        return !isHandoffTarget;
    }

    /**
     * Gets the parameter value for a given key, or returns a default if not present.
     *
     * @param paramKey the parameter key
     * @param defaultValue the default value if key not found
     * @return parameter value or default
     * @throws NullPointerException if paramKey is null
     */
    public Object getParameter(String paramKey, Object defaultValue) {
        Objects.requireNonNull(paramKey, "paramKey cannot be null");
        return skillParameters.getOrDefault(paramKey, defaultValue);
    }

    /**
     * Gets a parameter value as a string.
     *
     * @param paramKey the parameter key
     * @return parameter value as string, or null if not present
     * @throws NullPointerException if paramKey is null
     */
    public String getParameterAsString(String paramKey) {
        Objects.requireNonNull(paramKey, "paramKey cannot be null");
        Object value = skillParameters.get(paramKey);
        return value != null ? value.toString() : null;
    }

    /**
     * Gets a parameter value as an integer.
     *
     * @param paramKey the parameter key
     * @return parameter value as integer, or null if not present or not convertible
     * @throws NullPointerException if paramKey is null
     */
    public Integer getParameterAsInteger(String paramKey) {
        Objects.requireNonNull(paramKey, "paramKey cannot be null");
        Object value = skillParameters.get(paramKey);
        if (value instanceof Integer i) {
            return i;
        } else if (value instanceof Number n) {
            return n.intValue();
        } else if (value instanceof String s) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
