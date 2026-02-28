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

package org.yawlfoundation.yawl.dspy.a2a;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.dspy.persistence.DspyProgramRegistry;
import org.yawlfoundation.yawl.integration.a2a.skills.A2ASkill;
import org.yawlfoundation.yawl.integration.a2a.skills.SkillRequest;
import org.yawlfoundation.yawl.integration.a2a.skills.SkillResult;

/**
 * A2A skill that wraps a DSPy program for autonomous agent access.
 *
 * <p>Each saved DSPy program is exposed as an A2A skill that can be invoked
 * by autonomous agents. Skills provide semantic descriptions for agent discovery
 * and structured input/output for programmatic access.</p>
 *
 * <h2>Available Skills</h2>
 * <ul>
 *   <li>{@code dspy_worklet_selector} - ML-optimized worklet selection</li>
 *   <li>{@code dspy_resource_router} - Predictive resource allocation</li>
 *   <li>{@code dspy_anomaly_forensics} - Root cause analysis</li>
 *   <li>{@code dspy_runtime_adaptation} - Autonomous workflow adaptation</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Create skills from registry
 * List<A2ASkill> skills = DspyA2ASkill.createAll(registry);
 *
 * // Execute a skill
 * SkillRequest request = SkillRequest.builder("dspy_worklet_selector")
 *     .parameter("context", "Task: Review, Case: {urgency: high}")
 *     .build();
 * SkillResult result = skill.execute(request);
 *
 * // Access result
 * String workletId = (String) result.get("worklet_id");
 * double confidence = (Double) result.get("confidence");
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class DspyA2ASkill implements A2ASkill {

    private static final Logger log = LoggerFactory.getLogger(DspyA2ASkill.class);

    private final String id;
    private final String name;
    private final String description;
    private final String programName;
    private final DspyProgramRegistry registry;

    /**
     * Creates a new DSPy A2A skill.
     *
     * @param programName   the DSPy program name
     * @param displayName   human-readable skill name
     * @param description   skill description for agent discovery
     * @param registry      the DSPy program registry
     * @throws NullPointerException if any parameter is null
     */
    public DspyA2ASkill(
            String programName,
            String displayName,
            String description,
            DspyProgramRegistry registry
    ) {
        this.programName = Objects.requireNonNull(programName, "Program name must not be null");
        this.id = "dspy_" + programName;
        this.name = Objects.requireNonNull(displayName, "Display name must not be null");
        this.description = Objects.requireNonNull(description, "Description must not be null");
        this.registry = Objects.requireNonNull(registry, "Registry must not be null");
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of("dspy:execute");
    }

    @Override
    public List<String> getTags() {
        return List.of("dspy", "ml", "inference", "optimized", "workflow");
    }

    @Override
    public SkillResult execute(SkillRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            log.debug("Executing A2A skill {} for request {}", id, request.getRequestId());

            // Convert request parameters to program inputs
            Map<String, Object> inputs = convertParameters(request.getParameters());

            // Execute the DSPy program
            var result = registry.execute(programName, inputs);

            // Build success response
            Map<String, Object> data = new LinkedHashMap<>();
            data.putAll(result.output());
            data.put("confidence", extractConfidence(result.output()));
            data.put("execution_time_ms", result.metrics().executionTimeMs());

            long totalTimeMs = System.currentTimeMillis() - startTime;
            log.debug("A2A skill {} completed in {}ms", id, totalTimeMs);

            return SkillResult.success(data, totalTimeMs);

        } catch (Exception e) {
            log.error("A2A skill {} failed: {}", id, e.getMessage(), e);
            return SkillResult.error(e.getMessage());
        }
    }

    /**
     * Converts string parameters to object inputs for DSPy.
     */
    private Map<String, Object> convertParameters(Map<String, String> parameters) {
        Map<String, Object> inputs = new HashMap<>();
        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            inputs.put(entry.getKey(), entry.getValue());
        }
        return inputs;
    }

    /**
     * Extracts confidence from DSPy output.
     */
    private double extractConfidence(Map<String, Object> output) {
        Object confidence = output.get("confidence");
        if (confidence instanceof Number n) {
            return n.doubleValue();
        }
        return 0.5;
    }

    /**
     * Creates A2A skills for all available DSPy programs.
     *
     * @param registry the program registry
     * @return list of A2A skills
     * @throws NullPointerException if registry is null
     */
    public static List<A2ASkill> createAll(DspyProgramRegistry registry) {
        Objects.requireNonNull(registry, "Registry must not be null");

        List<A2ASkill> skills = new ArrayList<>();

        // Predefined skill configurations for known programs
        Map<String, SkillConfig> configs = new HashMap<>();
        configs.put("worklet_selector", new SkillConfig(
                "DSPy Worklet Selector",
                "ML-optimized worklet selection using GEPA-tuned prompts. " +
                "Analyzes task context and historical patterns to select the best worklet."
        ));
        configs.put("resource_router", new SkillConfig(
                "DSPy Resource Router",
                "Predictive resource allocation with optimized Chain-of-Thought reasoning. " +
                "Routes tasks to the best available agent based on capabilities and history."
        ));
        configs.put("anomaly_forensics", new SkillConfig(
                "DSPy Anomaly Forensics",
                "Root cause analysis using MultiChainComparison. " +
                "Generates competing hypotheses and selects the most plausible root cause."
        ));
        configs.put("runtime_adaptation", new SkillConfig(
                "DSPy Runtime Adaptation",
                "Autonomous workflow adaptation using ReAct agent. " +
                "Suggests SkipTask, AddResource, ReRoute, or EscalateCase actions."
        ));

        // Create skills for all registered programs
        for (String programName : registry.listProgramNames()) {
            SkillConfig config = configs.get(programName);
            if (config == null) {
                config = new SkillConfig(
                        "DSPy " + formatProgramName(programName),
                        "GEPA-optimized DSPy program: " + programName
                );
            }

            skills.add(new DspyA2ASkill(
                    programName,
                    config.displayName,
                    config.description,
                    registry
            ));
        }

        log.info("Created {} DSPy A2A skills", skills.size());
        return skills;
    }

    /**
     * Formats a program name for display (e.g., "worklet_selector" -> "Worklet Selector").
     */
    private static String formatProgramName(String name) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char character = name.charAt(i);
            if (i == 0) {
                result.append(Character.toUpperCase(character));
            } else if (character == '_') {
                result.append(' ');
            } else if (i > 0 && name.charAt(i - 1) == '_') {
                result.append(Character.toUpperCase(character));
            } else {
                result.append(character);
            }
        }
        return result.toString();
    }

    /**
     * Configuration for a DSPy A2A skill.
     */
    private static final class SkillConfig {
        final String displayName;
        final String description;

        SkillConfig(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }
    }
}
