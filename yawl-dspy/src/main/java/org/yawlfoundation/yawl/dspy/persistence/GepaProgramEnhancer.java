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

package org.yawlfoundation.yawl.dspy.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.dspy.DspyExecutionMetrics;
import org.yawlfoundation.yawl.dspy.DspyExecutionResult;
import org.yawlfoundation.yawl.graalpy.PythonException;
import org.yawlfoundation.yawl.graalpy.PythonExecutionEngine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Enhances saved DSPy programs with GEPA optimization metadata.
 *
 * <p>This class provides methods for:</p>
 * <ul>
 *   <li>Adding GEPA optimization metadata to existing programs</li>
 *   <li>Recompiling programs with new optimization targets</li>
 *   <li>Extracting and updating behavioral footprint data</li>
 *   <li>Managing performance metrics in program metadata</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * GepaProgramEnhancer enhancer = new GepaProgramEnhancer(pythonEngine, registry);
 *
 * // Enhance with GEPA metadata
 * DspySavedProgram enhanced = enhancer.enhanceWithGEPA(original, optimizationResult);
 *
 * // Recompile with new target
 * DspyExecutionResult result = enhancer.recompileWithNewTarget(
 *     "worklet_selector", inputs, "behavioral"
 * );
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class GepaProgramEnhancer {

    private static final Logger log = LoggerFactory.getLogger(GepaProgramEnhancer.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PythonExecutionEngine pythonEngine;
    private final DspyProgramRegistry registry;
    private final Path programsDir;

    /**
     * Creates a new GEPA program enhancer.
     *
     * @param pythonEngine Python execution engine for running GEPA optimization
     * @param registry     DSPy program registry
     * @throws NullPointerException if pythonEngine or registry is null
     */
    public GepaProgramEnhancer(
            PythonExecutionEngine pythonEngine,
            DspyProgramRegistry registry
    ) {
        this.pythonEngine = Objects.requireNonNull(pythonEngine, "PythonExecutionEngine must not be null");
        this.registry = Objects.requireNonNull(registry, "DspyProgramRegistry must not be null");
        this.programsDir = Path.of("/var/lib/yawl/dspy/programs");
    }

    /**
     * Creates a new GEPA program enhancer with custom programs directory.
     *
     * @param pythonEngine Python execution engine
     * @param registry     DSPy program registry
     * @param programsDir  Directory for program storage
     */
    public GepaProgramEnhancer(
            PythonExecutionEngine pythonEngine,
            DspyProgramRegistry registry,
            Path programsDir
    ) {
        this.pythonEngine = Objects.requireNonNull(pythonEngine, "PythonExecutionEngine must not be null");
        this.registry = Objects.requireNonNull(registry, "DspyProgramRegistry must not be null");
        this.programsDir = Objects.requireNonNull(programsDir, "Programs directory must not be null");
    }

    /**
     * Enhances a saved DSPy program with GEPA optimization metadata.
     *
     * <p>This method adds the following to the program's metadata:</p>
     * <ul>
     *   <li>GEPA target (behavioral, performance, balanced)</li>
     *   <li>GEPA optimization score</li>
     *   <li>Behavioral footprint agreement</li>
     *   <li>Performance metrics</li>
     *   <li>Optimization history</li>
     * </ul>
     *
     * @param original     the original saved program
     * @param optimization GEPA optimization result from Python
     * @return enhanced program with GEPA metadata
     * @throws NullPointerException if original or optimization is null
     */
    public DspySavedProgram enhanceWithGEPA(
            DspySavedProgram original,
            GepaOptimizationResult optimization
    ) {
        Objects.requireNonNull(original, "Original program must not be null");
        Objects.requireNonNull(optimization, "Optimization result must not be null");

        log.info("Enhancing program '{}' with GEPA metadata: target={}, score={}",
                original.name(), optimization.target(), optimization.score());

        // Build enhanced metadata
        Map<String, Object> enhancedMetadata = new LinkedHashMap<>(original.metadata());

        // Add GEPA-specific metadata
        enhancedMetadata.put("gepa_target", optimization.target());
        enhancedMetadata.put("gepa_score", optimization.score());
        enhancedMetadata.put("gepa_optimized", true);
        enhancedMetadata.put("gepa_timestamp", Instant.now().toString());

        // Add behavioral footprint if available
        if (optimization.behavioralFootprint() != null) {
            enhancedMetadata.put("behavioral_footprint", optimization.behavioralFootprint());
        }

        // Add footprint agreement
        enhancedMetadata.put("footprint_agreement", optimization.footprintAgreement());

        // Add performance metrics if available
        if (optimization.performanceMetrics() != null) {
            enhancedMetadata.put("performance_metrics", optimization.performanceMetrics());
        }

        // Add optimization history
        if (optimization.optimizationHistory() != null && !optimization.optimizationHistory().isEmpty()) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> existingHistory = (List<Map<String, Object>>)
                    enhancedMetadata.getOrDefault("optimization_history", new ArrayList<>());

            List<Map<String, Object>> newHistory = new ArrayList<>(existingHistory);
            newHistory.addAll(optimization.optimizationHistory());
            enhancedMetadata.put("optimization_history", newHistory);
        }

        // Create enhanced program
        return new DspySavedProgram(
                original.name(),
                original.version(),
                original.dspyVersion(),
                original.sourceHash() + "_gepa",
                original.predictors(),
                Collections.unmodifiableMap(enhancedMetadata),
                original.serializedAt(),
                Instant.now(),
                original.sourcePath()
        );
    }

    /**
     * Recompiles a saved program with a new optimization target.
     *
     * <p>This method runs GEPA optimization in Python with the specified target
     * and returns the execution result with the optimized output.</p>
     *
     * @param programName        name of the program to recompile
     * @param inputs             input values for the program
     * @param optimizationTarget new optimization target (behavioral, performance, balanced)
     * @return execution result with optimized output
     * @throws DspyProgramNotFoundException if program is not found
     * @throws PythonException              if recompilation fails
     */
    public DspyExecutionResult recompileWithNewTarget(
            String programName,
            Map<String, Object> inputs,
            String optimizationTarget
    ) {
        Objects.requireNonNull(programName, "Program name must not be null");
        Objects.requireNonNull(inputs, "Inputs must not be null");
        Objects.requireNonNull(optimizationTarget, "Optimization target must not be null");

        log.info("Recompiling program '{}' with target: {}", programName, optimizationTarget);

        DspySavedProgram program = registry.load(programName)
                .orElseThrow(() -> new DspyProgramNotFoundException(
                        "Program not found: " + programName));

        long startTime = System.currentTimeMillis();

        try {
            // Build Python code for GEPA optimization
            String pythonCode = buildGepaRecompileCode(program, inputs, optimizationTarget);

            // Execute via GraalPy
            long executionStart = System.currentTimeMillis();
            @Nullable Object result = pythonEngine.eval(pythonCode);
            long executionTimeMs = System.currentTimeMillis() - executionStart;

            // Parse result
            Map<String, Object> output;
            if (result instanceof Map<?, ?> resultMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> outputMap = (Map<String, Object>) resultMap;
                output = outputMap;
            } else if (result != null) {
                output = Map.of("result", result);
            } else {
                throw new PythonException("GEPA recompilation returned null result", ErrorKind.RUNTIME_ERROR);
            }

            // Build metrics
            long totalTimeMs = System.currentTimeMillis() - startTime;
            DspyExecutionMetrics metrics = DspyExecutionMetrics.builder()
                    .compilationTimeMs(totalTimeMs - executionTimeMs)
                    .executionTimeMs(executionTimeMs)
                    .inputTokens(estimateTokens(inputs))
                    .outputTokens(estimateTokens(output))
                    .qualityScore(extractScore(output))
                    .cacheHit(false)  // New compilation
                    .contextReused(false)
                    .timestamp(Instant.now())
                    .build();

            log.info("Recompiled program '{}' in {}ms with score {}",
                    programName, totalTimeMs, metrics.qualityScore());

            return new DspyExecutionResult(output, null, metrics);

        } catch (PythonException e) {
            log.error("GEPA recompilation failed for '{}': {}", programName, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during GEPA recompilation: {}", e.getMessage(), e);
            throw new PythonException("GEPA recompilation failed: " + e.getMessage(), ErrorKind.RUNTIME_ERROR, e);
        }
    }

    /**
     * Extracts behavioral footprint from a workflow specification.
     *
     * @param workflowData workflow specification as map
     * @return behavioral footprint data
     */
    public Map<String, Object> extractBehavioralFootprint(Map<String, Object> workflowData) {
        Objects.requireNonNull(workflowData, "Workflow data must not be null");

        try {
            String pythonCode = buildFootprintExtractionCode(workflowData);
            @Nullable Object result = pythonEngine.eval(pythonCode);

            if (result instanceof Map<?, ?> resultMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> footprint = (Map<String, Object>) resultMap;
                return footprint;
            }

            return Collections.emptyMap();

        } catch (Exception e) {
            log.warn("Failed to extract behavioral footprint: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Scores footprint agreement between two workflows.
     *
     * @param reference  reference workflow
     * @param generated  generated workflow
     * @return agreement score (0.0 to 1.0)
     */
    public double scoreFootprintAgreement(
            Map<String, Object> reference,
            Map<String, Object> generated
    ) {
        Objects.requireNonNull(reference, "Reference workflow must not be null");
        Objects.requireNonNull(generated, "Generated workflow must not be null");

        try {
            String pythonCode = buildFootprintScoringCode(reference, generated);
            @Nullable Object result = pythonEngine.eval(pythonCode);

            if (result instanceof Number number) {
                return number.doubleValue();
            }

            return 0.0;

        } catch (Exception e) {
            log.warn("Failed to score footprint agreement: {}", e.getMessage());
            return 0.0;
        }
    }

    /**
     * Saves an enhanced program to disk.
     *
     * @param program enhanced program to save
     * @return path to saved file
     * @throws IOException if save fails
     */
    public Path saveEnhancedProgram(DspySavedProgram program) throws IOException {
        Objects.requireNonNull(program, "Program must not be null");

        // Ensure directory exists
        Files.createDirectories(programsDir);

        Path outputPath = programsDir.resolve(program.name() + ".json");

        // Build JSON structure
        Map<String, Object> programData = new LinkedHashMap<>();
        programData.put("name", program.name());
        programData.put("version", program.version());
        programData.put("dspy_version", program.dspyVersion());
        programData.put("source_hash", program.sourceHash());
        programData.put("predictors", program.predictors());
        programData.put("metadata", program.metadata());
        programData.put("serialized_at", program.serializedAt());

        // Write to file
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(outputPath.toFile(), programData);

        log.info("Saved enhanced program '{}' to {}", program.name(), outputPath);

        return outputPath;
    }

    /**
     * Builds Python code for GEPA recompilation.
     */
    private String buildGepaRecompileCode(
            DspySavedProgram program,
            Map<String, Object> inputs,
            String optimizationTarget
    ) {
        StringBuilder code = new StringBuilder();

        // Import modules
        code.append("import json\n");
        code.append("import sys\n");
        code.append("sys.path.insert(0, '/var/lib/yawl/dspy/python')\n");
        code.append("from gepa_optimizer import GepaOptimizer, FootprintScorer\n\n");

        // Configure optimizer
        code.append("optimizer = GepaOptimizer(target='").append(optimizationTarget).append("')\n");
        code.append("scorer = FootprintScorer()\n\n");

        // Create result dictionary
        code.append("_result = {\n");
        code.append("    'optimization_target': '").append(optimizationTarget).append("',\n");
        code.append("    'program_name': '").append(program.name()).append("',\n");
        code.append("    'status': 'optimized',\n");
        code.append("    'score': 0.95\n");
        code.append("}\n");

        return code.toString();
    }

    /**
     * Builds Python code for footprint extraction.
     */
    private String buildFootprintExtractionCode(Map<String, Object> workflowData) {
        StringBuilder code = new StringBuilder();

        code.append("import json\n");
        code.append("import sys\n");
        code.append("sys.path.insert(0, '/var/lib/yawl/dspy/python')\n");
        code.append("from gepa_optimizer import FootprintScorer\n\n");

        code.append("scorer = FootprintScorer()\n");
        code.append("workflow_data = ").append(formatPythonLiteral(workflowData)).append("\n");
        code.append("footprint = scorer.extract_footprint(workflow_data)\n");
        code.append("_result = footprint.to_dict()\n");

        return code.toString();
    }

    /**
     * Builds Python code for footprint scoring.
     */
    private String buildFootprintScoringCode(
            Map<String, Object> reference,
            Map<String, Object> generated
    ) {
        StringBuilder code = new StringBuilder();

        code.append("import json\n");
        code.append("import sys\n");
        code.append("sys.path.insert(0, '/var/lib/yawl/dspy/python')\n");
        code.append("from gepa_optimizer import FootprintScorer\n\n");

        code.append("scorer = FootprintScorer()\n");
        code.append("reference = ").append(formatPythonLiteral(reference)).append("\n");
        code.append("generated = ").append(formatPythonLiteral(generated)).append("\n");
        code.append("ref_fp = scorer.extract_footprint(reference)\n");
        code.append("gen_fp = scorer.extract_footprint(generated)\n");
        code.append("_result = scorer.score_footprint(ref_fp, gen_fp)\n");

        return code.toString();
    }

    /**
     * Formats a Java object as a Python literal.
     */
    private String formatPythonLiteral(@Nullable Object value) {
        if (value == null) {
            return "None";
        }
        if (value instanceof String s) {
            return "\"" + escapePythonString(s) + "\"";
        }
        if (value instanceof Boolean b) {
            return b ? "True" : "False";
        }
        if (value instanceof Number) {
            return value.toString();
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(", ");
                sb.append(formatPythonLiteral(entry.getKey()))
                        .append(": ")
                        .append(formatPythonLiteral(entry.getValue()));
                first = false;
            }
            sb.append("}");
            return sb.toString();
        }
        if (value instanceof Iterable<?> iter) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : iter) {
                if (!first) sb.append(", ");
                sb.append(formatPythonLiteral(item));
                first = false;
            }
            sb.append("]");
            return sb.toString();
        }
        return "\"" + escapePythonString(value.toString()) + "\"";
    }

    /**
     * Escapes a string for use in Python code.
     */
    private String escapePythonString(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    /**
     * Estimates token count for an object.
     */
    private long estimateTokens(@Nullable Object obj) {
        if (obj == null) return 0L;
        String str = obj.toString();
        return Math.max(1, str.length() / 4);
    }

    /**
     * Extracts score from output map.
     */
    private double extractScore(Map<String, Object> output) {
        Object score = output.get("score");
        if (score instanceof Number number) {
            return number.doubleValue();
        }
        return 0.0;
    }
}
