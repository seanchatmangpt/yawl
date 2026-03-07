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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * Structured process specification - output of Stage 1 (NL → Spec).
 *
 * <p>This is the intermediate representation between natural language
 * and the process graph. It captures the semantic structure of the
 * process without committing to a specific control flow representation.
 *
 * <h2>Example:</h2>
 * <pre>{@code
 * ProcessSpec spec = new ProcessSpec(
 *     "PatientAdmission",
 *     List.of(
 *         new TaskDef("triage", "Triage Assessment", "atomic"),
 *         new TaskDef("registration", "Patient Registration", "atomic"),
 *         new TaskDef("insurance", "Insurance Verification", "atomic"),
 *         new TaskDef("bed_assignment", "Bed Assignment", "atomic")
 *     ),
 *     List.of(
 *         new DataObjectDef("patient_id", "Patient ID", "string"),
 *         new DataObjectDef("triage_level", "Triage Level", "integer")
 *     ),
 *     List.of(
 *         new ConstraintDef("parallel", "registration", "insurance"),
 *         new ConstraintDef("sequence", "triage", "registration")
 *     ),
 *     List.of("triage"),  // OR-join tasks
 *     List.of()           // No cancellation regions
 * );
 * }</pre>
 *
 * @param processName Process name in PascalCase
 * @param tasks List of task definitions
 * @param dataObjects List of data object definitions
 * @param constraints List of constraints (temporal, resource, data)
 * @param orJoins Task IDs that require OR-join semantics (YAWL-specific)
 * @param cancellationRegions Cancellation region definitions (YAWL-specific)
 * @param metadata Optional generation metadata
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProcessSpec(
    String processName,
    List<TaskDef> tasks,
    List<DataObjectDef> dataObjects,
    List<ConstraintDef> constraints,
    List<String> orJoins,
    List<CancellationRegionDef> cancellationRegions,
    Map<String, Object> metadata
) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ProcessSpec {
        tasks = tasks != null ? List.copyOf(tasks) : List.of();
        dataObjects = dataObjects != null ? List.copyOf(dataObjects) : List.of();
        constraints = constraints != null ? List.copyOf(constraints) : List.of();
        orJoins = orJoins != null ? List.copyOf(orJoins) : List.of();
        cancellationRegions = cancellationRegions != null ? List.copyOf(cancellationRegions) : List.of();
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
    }

    /**
     * Create a ProcessSpec with minimal required fields.
     */
    public static ProcessSpec of(String processName, List<TaskDef> tasks) {
        return new ProcessSpec(processName, tasks, List.of(), List.of(), List.of(), List.of(), Map.of());
    }

    /**
     * Convert to JSON for DSPy serialization.
     */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize ProcessSpec to JSON", e);
        }
    }

    /**
     * Parse from JSON.
     */
    public static ProcessSpec fromJson(String json) {
        try {
            return MAPPER.readValue(json, ProcessSpec.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse ProcessSpec from JSON", e);
        }
    }

    /**
     * Find a task by ID.
     */
    public TaskDef findTask(String id) {
        return tasks.stream()
            .filter(t -> t.id().equals(id))
            .findFirst()
            .orElse(null);
    }

    /**
     * Get task count.
     */
    public int taskCount() {
        return tasks.size();
    }

    /**
     * Check if this spec has OR-join semantics.
     */
    public boolean hasOrJoins() {
        return !orJoins.isEmpty();
    }

    /**
     * Check if this spec has cancellation regions.
     */
    public boolean hasCancellationRegions() {
        return !cancellationRegions.isEmpty();
    }
}
