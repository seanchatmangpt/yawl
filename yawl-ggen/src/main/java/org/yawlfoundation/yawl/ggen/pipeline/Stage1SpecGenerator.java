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

package org.yawlfoundation.yawl.ggen.pipeline;

import org.yawlfoundation.yawl.dspy.fluent.Dspy;
import org.yawlfoundation.yawl.dspy.fluent.DspyModule;
import org.yawlfoundation.yawl.dspy.fluent.DspyResult;
import org.yawlfoundation.yawl.dspy.fluent.DspySignature;
import org.yawlfoundation.yawl.ggen.model.ProcessSpec;

import org.yawlfoundation.yawl.ggen.model.TaskDef;
import org.yawlfoundation.yawl.ggen.model.DataObjectDef;
import org.yawlfoundation.yawl.ggen.model.ConstraintDef;
import org.yawlfoundation.yawl.ggen.model.CancellationRegionDef;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * Stage 1: Natural Language → ProcessSpec generator.
 *
 * <p>Transforms natural language process descriptions into structured
 * ProcessSpec using DSPy Chain-of-Thought for complex reasoning.
 *
 * <h2>Example:</h2>
 * <pre>{@code
 * Stage1SpecGenerator gen = new Stage1SpecGenerator();
 *
 * ProcessSpec spec = gen.generate(
 *     "Patient admission: triage, registration, bed assignment. " +
 *     "OR-join for emergency. Cancellation if ICU needed.",
 *     "healthcare"
 * );
 *
 * // spec.processName() == "PatientAdmission"
 * // spec.tasks().size() == 4
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class Stage1SpecGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DspyModule specPredictor;

    /**
     * Create a new Stage1SpecGenerator.
     */
    public Stage1SpecGenerator() {
        DspySignature sig = Dspy.signatureBuilder()
            .description("Transform natural language process description into structured YAWL specification")
            .input("nl_description", "Natural language process description")
            .input("domain_context", "Optional domain knowledge (healthcare, finance, manufacturing, etc.)")
            .output("process_name", "Process name in PascalCase (e.g., PatientAdmission)")
            .output("tasks", "JSON array of task definitions with id, name, type, input_vars, output_vars")
            .output("data_objects", "JSON array of data object definitions with id, name, type")
            .output("constraints", "JSON array of constraints with type, source. target, condition")
            .output("or_joins", "JSON array of task IDs that require OR-join semantics (YAWL-specific)")
            .output("cancellation_regions", "JSON array of cancellation region definitions with trigger_task, cancelled_tasks")
            .build();

        this.specPredictor = Dspy.chainOfThought(sig);
    }

    /**
     * Generate ProcessSpec from natural language.
     *
     * @param nlDescription Natural language process description
     * @param domainContext Optional domain context (e.g., "healthcare", "finance")
     * @return Generated ProcessSpec
     */
    public ProcessSpec generate(String nlDescription, String domainContext) {
        DspyResult result = specPredictor.predict(
            "nl_description", nlDescription,
            "domain_context", domainContext != null ? domainContext : ""
        );

        return parseResult(result);
    }

    /**
     * Generate ProcessSpec with default domain context.
     */
    public ProcessSpec generate(String nlDescription) {
        return generate(nlDescription, "");
    }

    private ProcessSpec parseResult(DspyResult result) {
        String processName = result.getString("process_name");
        List<TaskDef> tasks = parseTasks(result.getString("tasks"));
        List<DataObjectDef> dataObjects = parseDataObjects(result.getString("data_objects"));
        List<ConstraintDef> constraints = parseConstraints(result.getString("constraints"));
        List<String> orJoins = parseOrJoins(result.getString("or_joins"));
        List<CancellationRegionDef> cancellationRegions = parseCancellationRegions(result.getString("cancellation_regions"));

        return new ProcessSpec(processName, tasks, dataObjects, constraints, orJoins, cancellationRegions, Map.of());
    }

    private List<TaskDef> parseTasks(String json) {
        if (json == null || json.isEmpty()) return List.of();
        try {
            return MAPPER.readValue(json, new TypeReference<List<TaskDef>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<DataObjectDef> parseDataObjects(String json) {
        if (json == null || json.isEmpty()) return List.of();
        try {
            return MAPPER.readValue(json, new TypeReference<List<DataObjectDef>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<ConstraintDef> parseConstraints(String json) {
        if (json == null || json.isEmpty()) return List.of();
        try {
            return MAPPER.readValue(json, new TypeReference<List<ConstraintDef>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> parseOrJoins(String json) {
        if (json == null || json.isEmpty()) return List.of();
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<CancellationRegionDef> parseCancellationRegions(String json) {
        if (json == null || json.isEmpty()) return List.of();
        try {
            return MAPPER.readValue(json, new TypeReference<List<CancellationRegionDef>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
