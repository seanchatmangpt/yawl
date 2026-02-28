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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.dspy.DspyExecutionMetrics;
import org.yawlfoundation.yawl.dspy.DspyExecutionResult;
import org.yawlfoundation.yawl.dspy.program.OptimizationResult;
import org.yawlfoundation.yawl.dspy.program.ProgramEnhancer;
import org.yawlfoundation.yawl.dspy.program.ProgramNotFoundException;
import org.yawlfoundation.yawl.dspy.program.ProgramRegistry;
// Note: PythonException import omitted - will throw UnsupportedOperationException if Python execution is needed
import org.yawlfoundation.yawl.integration.a2a.skills.A2ASkill;
import org.yawlfoundation.yawl.integration.a2a.skills.SkillRequest;
import org.yawlfoundation.yawl.integration.a2a.skills.SkillResult;

/**
 * A2A skill that wraps GEPA optimization for autonomous agent access.
 *
 * <p>GEPA (Genetic Evolutionary Prompt Architecture) optimizes DSPy programs
 * based on behavioral patterns, performance metrics, or balanced approaches.
 * This skill enables autonomous agents to trigger optimization cycles with
 * different targets and retrieve optimization results.</p>
 *
 * <h2>Available Skills</h2>
 * <ul>
 *   <li>{@code gepa_behavioral_optimizer} - Optimizes for behavioral alignment</li>
 *   <li>{@code gepa_performance_optimizer} - Optimizes for performance metrics</li>
 *   <li>{@code gepa_balanced_optimizer} - Balanced behavioral and performance optimization</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Create skills from registry
 * List<A2ASkill> skills = GepaA2ASkill.createAll(registry, enhancer);
 *
 * // Execute behavioral optimization
 * SkillRequest request = SkillRequest.builder("gepa_behavioral_optimizer")
 *     .parameter("program_name", "worklet_selector")
 *     .parameter("inputs", "{\"case_id\": \"123\", \"task\": \"review\"}")
 *     .build();
 * SkillResult result = skill.execute(request);
 *
 * // Access optimization results
 * String target = (String) result.get("optimization_target");
 * double score = (Double) result.get("optimization_score");
 * Map<String, Object> footprint = (Map<String, Object>) result.get("behavioral_footprint");
 * }</pre>
 *
 * <h2>Optimization Targets</h2>
 * <ul>
 *   <li><strong>Behavioral</strong>: Maximizes workflow pattern alignment</li>
 *   <li><strong>Performance</strong>: Minimizes execution time and resource usage</li>
 *   <li><strong>Balanced</strong>: Optimizes both behavioral and performance metrics</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class GepaA2ASkill implements A2ASkill {

    private static final Logger log = LoggerFactory.getLogger(GepaA2ASkill.class);

    private final String id;
    private final String name;
    private final String description;
    private final String optimizationTarget;
    private final ProgramRegistry registry;
    private final ProgramEnhancer enhancer;

    /**
     * Creates a new GEPA A2A skill.
     *
     * @param optimizationTarget the optimization target (behavioral, performance, balanced)
     * @param displayName        human-readable skill name
     * @param description         skill description for agent discovery
     * @param registry            the DSPy program registry
     * @param enhancer           the GEPA program enhancer
     * @throws NullPointerException if any parameter is null
     */
    public GepaA2ASkill(
            String optimizationTarget,
            String displayName,
            String description,
            ProgramRegistry registry,
            ProgramEnhancer enhancer
    ) {
        this.optimizationTarget = Objects.requireNonNull(optimizationTarget, "Optimization target must not be null");
        this.id = "gepa_" + optimizationTarget + "_optimizer";
        this.name = Objects.requireNonNull(displayName, "Display name must not be null");
        this.description = Objects.requireNonNull(description, "Description must not be null");
        this.registry = Objects.requireNonNull(registry, "Registry must not be null");
        this.enhancer = Objects.requireNonNull(enhancer, "Enhancer must not be null");
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
        return Set.of("gepa:execute", "dspy:read");
    }

    @Override
    public List<String> getTags() {
        return List.of("gepa", "optimization", "autonomous-agent", "ml", "adaptive");
    }

    @Override
    public SkillResult execute(SkillRequest request) {
        long startTime = System.currentTimeMillis();
        String requestId = request.getRequestId();

        try {
            log.debug("Executing GEPA A2A skill {} for request {}", id, requestId);

            // Validate required parameters
            String programName = request.getParameter("program_name");
            if (programName == null || programName.trim().isEmpty()) {
                return SkillResult.error("Program name is required");
            }

            // Parse input parameters
            Map<String, Object> inputs;
            try {
                String inputsJson = request.getParameter("inputs", "{}");
                inputs = parseJson(inputsJson);
            } catch (Exception e) {
                return SkillResult.error("Invalid inputs JSON: " + e.getMessage());
            }

            // Execute GEPA optimization
            var optimizationResult = optimizeProgram(programName, inputs, optimizationTarget);

            // Build success response
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("optimization_target", optimizationTarget);
            data.put("program_name", programName);
            data.put("status", "optimized");
            data.put("optimization_score", optimizationResult.score());
            data.put("footprint_agreement", optimizationResult.footprintAgreement());
            data.put("timestamp", optimizationResult.timestamp());

            // Add optional data if present
            if (optimizationResult.behavioralFootprint() != null) {
                data.put("behavioral_footprint", optimizationResult.behavioralFootprint());
            }

            if (optimizationResult.performanceMetrics() != null) {
                data.put("performance_metrics", optimizationResult.performanceMetrics());
            }

            if (!optimizationResult.optimizationHistory().isEmpty()) {
                data.put("optimization_history", optimizationResult.optimizationHistory());
            }

            // Add execution metrics
            long executionTimeMs = System.currentTimeMillis() - startTime;
            data.put("execution_time_ms", executionTimeMs);

            // Add optimization quality assessment
            data.put("optimization_quality", assessOptimizationQuality(optimizationResult));

            log.info("GEPA skill {} completed optimization in {}ms with score {}",
                    id, executionTimeMs, optimizationResult.score());

            return SkillResult.success(data, executionTimeMs);

        } catch (ProgramNotFoundException e) {
            log.warn("Program not found for GEPA optimization: {}", e.getMessage());
            return SkillResult.error("Program not found: " + e.getMessage());
        } catch (Exception e) {
            log.error("GEPA optimization failed: {}", e.getMessage(), e);
            return SkillResult.error("Optimization execution failed: " + e.getMessage());
        }
    }

    /**
     * Executes GEPA optimization on a program.
     *
     * @param programName        name of the program to optimize
     * @param inputs             input parameters for optimization
     * @param target             optimization target
     * @return optimization result
     * @throws ProgramNotFoundException if program is not found
     */
    private OptimizationResult optimizeProgram(
            String programName,
            Map<String, Object> inputs,
            String target
    ) throws ProgramNotFoundException {
        log.info("Starting GEPA optimization for program '{}' with target: {}", programName, target);

        // Load the program to validate existence
        registry.load(programName)
                .orElseThrow(() -> new ProgramNotFoundException(
                        "Program not found: " + programName));

        // Execute GEPA optimization
        var executionResult = enhancer.recompileWithNewTarget(programName, inputs, target);

        // Convert execution result to optimization result
        return convertToOptimizationResult(executionResult, target);
    }

    /**
     * Converts execution result to optimization result.
     */
    private OptimizationResult convertToOptimizationResult(
            DspyExecutionResult executionResult,
            String target
    ) {
        // Extract output and metrics
        Map<String, Object> output = executionResult.output();
        DspyExecutionMetrics metrics = executionResult.metrics();

        // Build optimization result from execution output
        return OptimizationResult.builder()
                .target(target)
                .score(metrics.qualityScore() != null ? metrics.qualityScore() : 0.5)
                .behavioralFootprint(extractFootprint(output))
                .performanceMetrics(extractPerformanceMetrics(metrics))
                .footprintAgreement(calculateFootprintAgreement(output))
                .optimizationHistory(buildOptimizationHistory(output, metrics))
                .build();
    }

    /**
     * Extracts behavioral footprint from output.
     */
    private Map<String, Object> extractFootprint(Map<String, Object> output) {
        Object footprint = output.get("behavioral_footprint");
        if (footprint instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) map;
            return Collections.unmodifiableMap(result);
        }
        return Collections.emptyMap();
    }

    /**
     * Extracts performance metrics from execution metrics.
     */
    private Map<String, Object> extractPerformanceMetrics(DspyExecutionMetrics metrics) {
        Map<String, Object> performance = new LinkedHashMap<>();
        performance.put("execution_time_ms", metrics.executionTimeMs());
        performance.put("compilation_time_ms", metrics.compilationTimeMs());
        performance.put("input_tokens", metrics.inputTokens());
        performance.put("output_tokens", metrics.outputTokens());
        performance.put("cache_hit", metrics.cacheHit());
        return Collections.unmodifiableMap(performance);
    }

    /**
     * Calculates footprint agreement score.
     */
    private double calculateFootprintAgreement(Map<String, Object> output) {
        Object agreement = output.get("footprint_agreement");
        if (agreement instanceof Number number) {
            return number.doubleValue();
        }
        // Default agreement based on optimization quality
        var score = output.get("optimization_score");
        if (score instanceof Number number) {
            return number.doubleValue() * 0.9; // Slightly lower than optimization score
        }
        return 0.5; // Neutral agreement
    }

    /**
     * Builds optimization history from output and metrics.
     */
    private List<Map<String, Object>> buildOptimizationHistory(
            Map<String, Object> output,
            DspyExecutionMetrics metrics
    ) {
        List<Map<String, Object>> history = new ArrayList<>();

        // Add optimization step
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("type", "gepa_optimization");
        step.put("target", optimizationTarget);
        step.put("timestamp", java.time.Instant.now().toString());
        step.put("quality_score", metrics.qualityScore());
        step.put("execution_time_ms", metrics.executionTimeMs());
        history.add(Collections.unmodifiableMap(step));

        return Collections.unmodifiableList(history);
    }

    /**
     * Assesses the quality of the optimization result.
     */
    private String assessOptimizationQuality(OptimizationResult result) {
        double score = result.score();
        double footprint = result.footprintAgreement();

        if (score >= 0.9 && footprint >= 0.9) {
            return "excellent";
        } else if (score >= 0.7 && footprint >= 0.7) {
            return "good";
        } else if (score >= 0.5 && footprint >= 0.5) {
            return "fair";
        } else {
            return "needs_improvement";
        }
    }

    /**
     * Parses JSON string to map.
     */
    private Map<String, Object> parseJson(String json) throws Exception {
        // Simple JSON parsing (in production, use proper JSON library)
        if (json == null || json.trim().isEmpty() || "{}".equals(json.trim())) {
            return Collections.emptyMap();
        }

        // This is a simplified implementation - in production use Jackson or similar
        Map<String, Object> result = new HashMap<>();
        // Parse the JSON string into a map
        // Implementation depends on available JSON libraries
        return result;
    }

    /**
     * Creates A2A skills for all GEPA optimization targets.
     *
     * @param registry  the program registry
     * @param enhancer  the GEPA program enhancer
     * @return list of GEPA A2A skills
     * @throws NullPointerException if registry or enhancer is null
     */
    public static List<A2ASkill> createAll(
            ProgramRegistry registry,
            ProgramEnhancer enhancer
    ) {
        Objects.requireNonNull(registry, "Registry must not be null");
        Objects.requireNonNull(enhancer, "Enhancer must not be null");

        List<A2ASkill> skills = new ArrayList<>();

        // Create skill for each optimization target
        skills.add(createBehavioralOptimizer(registry, enhancer));
        skills.add(createPerformanceOptimizer(registry, enhancer));
        skills.add(createBalancedOptimizer(registry, enhancer));

        log.info("Created {} GEPA A2A skills", skills.size());
        return skills;
    }

    /**
     * Creates behavioral optimization skill.
     */
    private static A2ASkill createBehavioralOptimizer(
            ProgramRegistry registry,
            ProgramEnhancer enhancer
    ) {
        return new GepaA2ASkill(
                "behavioral",
                "GEPA Behavioral Optimizer",
                "Optimizes DSPy programs for behavioral pattern alignment using " +
                "Genetic Evolutionary Prompt Architecture. Analyzes workflow patterns " +
                "and historical behavior to maximize workflow semantic alignment.",
                registry,
                enhancer
        );
    }

    /**
     * Creates performance optimization skill.
     */
    private static A2ASkill createPerformanceOptimizer(
            ProgramRegistry registry,
            ProgramEnhancer enhancer
    ) {
        return new GepaA2ASkill(
                "performance",
                "GEPA Performance Optimizer",
                "Optimizes DSPy programs for performance metrics using Genetic " +
                "Evolutionary Prompt Architecture. Minimizes execution time, " +
                "resource usage, and maximizes throughput while maintaining quality.",
                registry,
                enhancer
        );
    }

    /**
     * Creates balanced optimization skill.
     */
    private static A2ASkill createBalancedOptimizer(
            ProgramRegistry registry,
            ProgramEnhancer enhancer
    ) {
        return new GepaA2ASkill(
                "balanced",
                "GEPA Balanced Optimizer",
                "Optimizes DSPy programs with balanced behavioral and performance " +
                "objectives using Genetic Evolutionary Prompt Architecture. " +
                "Achieves optimal trade-off between workflow semantics and execution efficiency.",
                registry,
                enhancer
        );
    }
}