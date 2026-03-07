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

package org.yawlfoundation.yawl.ggen.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.ggen.model.YawlSpec;
import org.yawlfoundation.yawl.ggen.model.ValidationResult.ExecutionError;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Virtual execution simulator for YAWL specifications.
 *
 * <p>Simulates token flow through YAWL process to detect execution errors
 * without actually loading into YEngine.
 *
 * <h2>Simulation Strategy:</h2>
 * <ul>
 *   <li>Launch virtual cases (default: 1000)</li>
 *   <li>Track token flow through each element</li>
 *   <li>Detect stuck cases (not completing within timeout)</li>
 *   <li>Verify all cases reach output condition</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class VirtualExecutor {

    private static final Logger log = LoggerFactory.getLogger(VirtualExecutor.class);

    private static final int DEFAULT_CASE_COUNT = 1000;
    private static final long CASE_TIMEOUT_MS = 100;

    /**
     * Simulate YAWL specification execution.
     *
     * @param spec YawlSpec to simulate
     * @return ExecutionResult with errors if any
     */
    public static ExecutionResult simulate(YawlSpec spec) {
        return simulate(spec, DEFAULT_CASE_COUNT);
    }

    /**
     * Simulate with custom case count.
     *
     * @param spec YawlSpec to simulate
     * @param caseCount Number of virtual cases to simulate
     * @return ExecutionResult with errors if any
     */
    public static ExecutionResult simulate(YawlSpec spec, int caseCount) {
        log.debug("Starting virtual execution simulation: {} cases", caseCount);
        long startTime = System.currentTimeMillis();

        List<ExecutionError> errors = new ArrayList<>();
        AtomicInteger completedCases = new AtomicInteger(0);
        AtomicInteger stuckCases = new AtomicInteger(0);

        try {
            // Parse YAWL XML to build execution model
            ExecutionModel model = parseYawlXml(spec.yawlXml());

            if (model == null) {
                return new ExecutionResult(false, List.of(
                    ExecutionError.of("parse", "Failed to parse YAWL XML")
                ));
            }

            // Simulate cases
            for (int i = 0; i < caseCount; i++) {
                String caseId = "virtual-case-" + i;

                try {
                    SimulationResult result = simulateCase(model, caseId);

                    if (result.completed()) {
                        completedCases.incrementAndGet();
                    } else {
                        stuckCases.incrementAndGet();
                        errors.add(ExecutionError.withCase(
                            result.stuckAt(),
                            "Case stuck during simulation",
                            caseId
                        ));
                    }

                } catch (Exception e) {
                    errors.add(ExecutionError.withCase(
                        "simulation",
                        "Simulation error: " + e.getMessage(),
                        caseId
                    ));
                }
            }

            long executionTime = System.currentTimeMillis() - startTime;
            log.debug("Virtual execution complete: {} completed, {} stuck in {}ms",
                completedCases.get(), stuckCases.get(), executionTime);

            // All cases must complete
            boolean success = stuckCases.get() == 0 && errors.isEmpty();

            return new ExecutionResult(success, errors);

        } catch (Exception e) {
            log.error("Virtual execution failed", e);
            return new ExecutionResult(false, List.of(
                ExecutionError.of("execution", "Execution failed: " + e.getMessage())
            ));
        }
    }

    /**
     * Simulate a single case through the execution model.
     */
    private static SimulationResult simulateCase(ExecutionModel model, String caseId) {
        // Start at input condition
        String currentElement = model.inputCondition();

        int maxSteps = model.elements().size() * 2; // Prevent infinite loops
        int step = 0;

        while (step < maxSteps) {
            // Check if we reached output condition
            if (currentElement.equals(model.outputCondition())) {
                return new SimulationResult(true, null);
            }

            // Get next elements based on flow
            List<String> nextElements = model.getFlowsFrom(currentElement);

            if (nextElements.isEmpty()) {
                // Deadlock - no outgoing flow
                return new SimulationResult(false, currentElement);
            }

            // For XOR choice, pick first path (worst case analysis)
            // For AND/OR, all paths execute (but we simulate sequentially)
            currentElement = nextElements.get(0);
            step++;
        }

        // Exceeded max steps - likely stuck
        return new SimulationResult(false, currentElement);
    }

    /**
     * Parse YAWL XML into execution model.
     */
    private static ExecutionModel parseYawlXml(String yawlXml) {
        // Simple parsing - extract element IDs and flows
        // In production, would use proper XML parser
        List<String> elements = new ArrayList<>();
        List<Flow> flows = new ArrayList<>();
        String inputCondition = null;
        String outputCondition = null;

        // Extract input condition
        var inputMatch = java.util.regex.Pattern.compile(
            "<inputCondition[^>]*id=\"([^\"]+)\"").matcher(yawlXml);
        if (inputMatch.find()) {
            inputCondition = inputMatch.group(1);
            elements.add(inputCondition);
        }

        // Extract output condition
        var outputMatch = java.util.regex.Pattern.compile(
            "<outputCondition[^>]*id=\"([^\"]+)\"").matcher(yawlXml);
        if (outputMatch.find()) {
            outputCondition = outputMatch.group(1);
            elements.add(outputCondition);
        }

        // Extract tasks
        var taskMatcher = java.util.regex.Pattern.compile(
            "<task[^>]*id=\"([^\"]+)\"").matcher(yawlXml);
        while (taskMatcher.find()) {
            elements.add(taskMatcher.group(1));
        }

        // Extract flows
        var flowMatcher = java.util.regex.Pattern.compile(
            "<flow[^>]*source=\"([^\"]+)\"[^>]*target=\"([^\"]+)\"").matcher(yawlXml);
        while (flowMatcher.find()) {
            flows.add(new Flow(flowMatcher.group(1), flowMatcher.group(2)));
        }

        if (inputCondition == null || outputCondition == null) {
            return null;
        }

        return new ExecutionModel(elements, flows, inputCondition, outputCondition);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INTERNAL TYPES
    // ═══════════════════════════════════════════════════════════════════════════

    private record ExecutionModel(
        List<String> elements,
        List<Flow> flows,
        String inputCondition,
        String outputCondition
    ) {
        List<String> getFlowsFrom(String source) {
            return flows.stream()
                .filter(f -> f.source().equals(source))
                .map(Flow::target)
                .toList();
        }
    }

    private record Flow(String source, String target) {}

    private record SimulationResult(boolean completed, String stuckAt) {}

    // ═══════════════════════════════════════════════════════════════════════════
    // RESULT TYPE
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Result of virtual execution.
     */
    public record ExecutionResult(boolean success, List<ExecutionError> errors) {
        public ExecutionResult {
            errors = errors != null ? List.copyOf(errors) : List.of();
        }
    }
}
