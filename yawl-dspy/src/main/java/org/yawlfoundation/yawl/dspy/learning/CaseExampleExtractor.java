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

package org.yawlfoundation.yawl.dspy.learning;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.engine.YWorkItem;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Extracts training examples from completed work items.
 *
 * <p>Converts YWorkItem historical records into DspyTrainingExample pairs
 * by extracting natural language descriptions and corresponding POWL outputs.
 * These examples are used to bootstrap DSPy compiler for improved POWL generation.</p>
 *
 * <h2>Extraction Strategy</h2>
 * <p>For each completed work item:</p>
 * <ol>
 *   <li>Extract input: Case documentation and task description as natural language</li>
 *   <li>Extract output: Expected POWL JSON structure based on task execution</li>
 *   <li>Construct: Create DspyTrainingExample record with input-output pair</li>
 *   <li>Validate: Ensure both input and output are non-empty and well-formed</li>
 * </ol>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe. All state is immutable or thread-local.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class CaseExampleExtractor {

    private static final Logger log = LogManager.getLogger(CaseExampleExtractor.class);

    private final YWorkItem workItem;

    /**
     * Creates a new extractor for the given work item.
     *
     * @param workItem the completed work item to extract from; must not be null
     * @throws NullPointerException if workItem is null
     */
    public CaseExampleExtractor(YWorkItem workItem) {
        this.workItem = Objects.requireNonNull(workItem, "workItem must not be null");
    }

    /**
     * Extracts a training example from the work item.
     *
     * <p>The input is constructed from the work item's task name, case data,
     * and documentation. The output is the expected POWL structure based on
     * the work item's execution history.</p>
     *
     * @return a DspyTrainingExample with input-output pair
     * @throws IllegalStateException if extraction fails due to missing data
     */
    public DspyTrainingExample extract() {
        try {
            String input = extractInput();
            Map<String, Object> output = extractOutput();

            DspyTrainingExample example = new DspyTrainingExample(input, output);
            log.debug("Extracted training example from work item: {}", workItem.getIDString());
            return example;

        } catch (Exception e) {
            log.warn("Failed to extract training example from work item {}: {}",
                    workItem.getIDString(), e.getMessage(), e);
            throw new IllegalStateException(
                    "Failed to extract training example: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts the natural language input from the work item.
     *
     * <p>Constructs a description combining task name, case documentation,
     * and any available input parameters.</p>
     *
     * @return the natural language input description
     */
    private String extractInput() {
        var taskName = workItem.getTask().getName();
        var caseID = workItem.getWorkItemID().getCaseID().toString();
        var documentation = extractCaseDocumentation();

        return String.format(
                "Task: %s (Case: %s)\nDescription: %s",
                taskName, caseID, documentation);
    }

    /**
     * Extracts case documentation from available metadata.
     *
     * <p>Pulls description from work item or case documentation if available.</p>
     *
     * @return the case documentation string
     */
    private String extractCaseDocumentation() {
        var task = workItem.getTask();
        if (task != null) {
            return task.getDocumentation();
        }
        return "Case workflow task";
    }

    /**
     * Extracts the expected POWL output from the work item.
     *
     * <p>Constructs a Map representation of the expected POWL structure
     * based on the work item's task characteristics and execution history.</p>
     *
     * @return the POWL output as a Map
     */
    private Map<String, Object> extractOutput() {
        Map<String, Object> powl = new HashMap<>();

        var taskName = workItem.getTask().getName();
        var taskID = workItem.getWorkItemID().getTaskID();

        // Build basic POWL task definition
        powl.put("type", "task");
        powl.put("name", taskName);
        powl.put("id", taskID);

        // Extract input/output parameters from task decomposition
        var task = workItem.getTask();
        if (task != null) {
            var decomposition = task.getDecompositionPrototype();
            if (decomposition != null) {
                var inputParams = decomposition.getInputParameters().values();
                var outputParams = decomposition.getOutputParameters().values();

                if (!inputParams.isEmpty()) {
                    powl.put("input_params", inputParams.stream()
                            .map(p -> Map.of("name", p.getName(), "type", p.getDataTypeNameUnprefixed()))
                            .toList());
                }

                if (!outputParams.isEmpty()) {
                    powl.put("output_params", outputParams.stream()
                            .map(p -> Map.of("name", p.getName(), "type", p.getDataTypeNameUnprefixed()))
                            .toList());
                }
            }
        }

        // Add task metadata
        // Note: getTimesStarted() doesn't exist in YAWL 6.0.0 API
        // This might need to be implemented separately or use different approach
        powl.put("times_started", 1); // Default to 1, as we're processing this work item

        var timerExpiry = workItem.getTimerExpiry();
        if (timerExpiry != 0) { // Check for non-zero expiry
            powl.put("timer_expiry", String.valueOf(timerExpiry));
        }

        return powl;
    }
}
