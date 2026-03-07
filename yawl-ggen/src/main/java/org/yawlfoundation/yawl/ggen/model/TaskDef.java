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
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Task definition in a ProcessSpec.
 *
 * @param id Unique task identifier (snake_case)
 * @param name Human-readable task name
 * @param type Task type: "atomic", "composite", "multiple"
 * @param inputVars Input variable names (optional)
 * @param outputVars Output variable names (optional)
 * @param resourceClass Required resource class (optional)
 * @param decomposesTo Sub-process ID for composite tasks (optional)
 * @param metadata Additional metadata (optional)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskDef(
    String id,
    String name,
    String type,
    @JsonProperty("input_vars") List<String> inputVars,
    @JsonProperty("output_vars") List<String> outputVars,
    @JsonProperty("resource_class") String resourceClass,
    @JsonProperty("decomposes_to") String decomposesTo,
    Map<String, Object> metadata
) {

    public TaskDef {
        inputVars = inputVars != null ? List.copyOf(inputVars) : List.of();
        outputVars = outputVars != null ? List.copyOf(outputVars) : List.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Create a simple atomic task.
     */
    public static TaskDef atomic(String id, String name) {
        return new TaskDef(id, name, "atomic", List.of(), List.of(), null, null, Map.of());
    }

    /**
     * Create a composite task (sub-process).
     */
    public static TaskDef composite(String id, String name, String decomposesTo) {
        return new TaskDef(id, name, "composite", List.of(), List.of(), null, decomposesTo, Map.of());
    }

    /**
     * Create a multiple instance task.
     */
    public static TaskDef multiple(String id, String name) {
        return new TaskDef(id, name, "multiple", List.of(), List.of(), null, null, Map.of());
    }

    /**
     * Create a task with resource requirement.
     */
    public static TaskDef withResource(String id, String name, String resourceClass) {
        return new TaskDef(id, name, "atomic", List.of(), List.of(), resourceClass, null, Map.of());
    }

    /**
     * Check if this task has resource requirements.
     */
    public boolean hasResource() {
        return resourceClass != null && !resourceClass.isEmpty();
    }

    /**
     * Check if this is a composite task.
     */
    public boolean isComposite() {
        return "composite".equals(type);
    }

    /**
     * Check if this is a multiple instance task.
     */
    public boolean isMultiple() {
        return "multiple".equals(type);
    }
}
