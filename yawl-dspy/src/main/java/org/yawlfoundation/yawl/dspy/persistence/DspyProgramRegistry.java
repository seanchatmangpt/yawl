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

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.dspy.DspyExecutionMetrics;
import org.yawlfoundation.yawl.dspy.DspyExecutionResult;
import org.yawlfoundation.yawl.dspy.DspyProgram;
import org.yawlfoundation.yawl.graalpy.PythonException;
import org.yawlfoundation.yawl.graalpy.PythonExecutionEngine;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages persistent DSPy programs stored as JSON files.
 *
 * <p>Programs are loaded from disk on startup and cached for fast access.
 * Supports hot-reloading when program files change. This enables GEPA-optimized
 * DSPy programs to persist across JVM restarts without recompilation.</p>
 *
 * <h2>Directory Structure</h2>
 * <pre>
 * /var/lib/yawl/dspy/programs/
 * ├── worklet_selector.json
 * ├── resource_router.json
 * ├── anomaly_forensics.json
 * └── runtime_adaptation.json
 * </pre>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Initialize registry
 * Path programsDir = Path.of("/var/lib/yawl/dspy/programs");
 * DspyProgramRegistry registry = new DspyProgramRegistry(programsDir, pythonEngine);
 *
 * // List available programs
 * List<String> programs = registry.listProgramNames();
 *
 * // Execute a saved program
 * DspyExecutionResult result = registry.execute("worklet_selector", inputs);
 *
 * // Hot-reload after external optimization
 * registry.reload("worklet_selector");
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class DspyProgramRegistry {

    private static final Logger log = LoggerFactory.getLogger(DspyProgramRegistry.class);

    private final Path programsDir;
    private final PythonExecutionEngine pythonEngine;
    private final ConcurrentHashMap<String, DspySavedProgram> programs = new ConcurrentHashMap<>();

    /**
     * Creates a new registry and loads all programs from the specified directory.
     *
     * @param programsDir  directory containing JSON program files
     * @param pythonEngine Python execution engine for running DSPy programs
     * @throws NullPointerException if programsDir or pythonEngine is null
     */
    public DspyProgramRegistry(Path programsDir, PythonExecutionEngine pythonEngine) {
        this.programsDir = Objects.requireNonNull(programsDir, "Programs directory must not be null");
        this.pythonEngine = Objects.requireNonNull(pythonEngine, "PythonExecutionEngine must not be null");
        loadAllPrograms();
    }

    /**
     * Loads all JSON program files from the programs directory.
     */
    private void loadAllPrograms() {
        if (!Files.exists(programsDir)) {
            log.warn("DSPy programs directory does not exist: {}", programsDir);
            return;
        }

        int loaded = 0;
        int failed = 0;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(programsDir, "*.json")) {
            for (Path file : stream) {
                try {
                    DspySavedProgram program = DspySavedProgram.loadFromJson(file);
                    programs.put(program.name(), program);
                    loaded++;
                    log.debug("Loaded DSPy program: {} ({} predictors)",
                            program.name(), program.predictorCount());
                } catch (Exception e) {
                    failed++;
                    log.warn("Failed to load DSPy program from {}: {}", file.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("Failed to scan DSPy programs directory: {}", e.getMessage(), e);
        }

        log.info("DspyProgramRegistry initialized: {} programs loaded, {} failed from {}",
                loaded, failed, programsDir);
    }

    /**
     * Lists all loaded program names.
     *
     * @return immutable list of program names
     */
    public List<String> listProgramNames() {
        return Collections.unmodifiableList(new ArrayList<>(programs.keySet()));
    }

    /**
     * Returns the number of loaded programs.
     *
     * @return program count
     */
    public int programCount() {
        return programs.size();
    }

    /**
     * Loads a saved DSPy program by name.
     *
     * @param name program name (e.g., "worklet_selector")
     * @return the loaded program, or empty if not found
     */
    public Optional<DspySavedProgram> load(String name) {
        Objects.requireNonNull(name, "Program name must not be null");
        return Optional.ofNullable(programs.get(name));
    }

    /**
     * Checks if a program is loaded.
     *
     * @param name program name
     * @return true if program exists
     */
    public boolean hasProgram(String name) {
        return programs.containsKey(name);
    }

    /**
     * Executes a saved DSPy program with the given inputs.
     *
     * <p>This method reconstructs a Python DSPy module from the saved configuration
     * and executes it via the GraalPy engine. The reconstructed module uses the
     * optimized prompts and few-shot examples from the saved program.</p>
     *
     * @param name   program name
     * @param inputs map of input values matching the program's signature
     * @return execution result with output and metrics
     * @throws DspyProgramNotFoundException if program is not found
     * @throws PythonException              if execution fails
     */
    public DspyExecutionResult execute(String name, Map<String, Object> inputs) {
        Objects.requireNonNull(name, "Program name must not be null");
        Objects.requireNonNull(inputs, "Inputs must not be null");

        DspySavedProgram program = programs.get(name);
        if (program == null) {
            throw new DspyProgramNotFoundException(
                    "DSPy program '" + name + "' not found. Available programs: " + programs.keySet());
        }

        return executeSavedProgram(program, inputs);
    }

    /**
     * Executes a saved program by reconstructing its Python module.
     */
    private DspyExecutionResult executeSavedProgram(DspySavedProgram program, Map<String, Object> inputs) {
        long startTime = System.currentTimeMillis();
        Instant metricsTimestamp = Instant.now();

        try {
            log.debug("Executing saved DSPy program: {}", program.name());

            // Build Python code to reconstruct and execute the module
            String pythonCode = buildExecutionPythonCode(program, inputs);

            // Execute via GraalPy
            long executionStart = System.currentTimeMillis();
            @Nullable Object result = pythonEngine.eval(pythonCode);
            long executionTimeMs = System.currentTimeMillis() - executionStart;

            // Convert result to Map
            Map<String, Object> output;
            if (result instanceof Map<?, ?> resultMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> outputMap = (Map<String, Object>) resultMap;
                output = outputMap;
            } else if (result != null) {
                output = Map.of("result", result);
            } else {
                throw new PythonException("DSPy program returned null result", org.yawlfoundation.yawl.graalpy.PythonException.ErrorKind.RUNTIME_ERROR);
            }

            // Build metrics
            long totalTimeMs = System.currentTimeMillis() - startTime;
            DspyExecutionMetrics metrics = DspyExecutionMetrics.builder()
                    .compilationTimeMs(totalTimeMs - executionTimeMs)
                    .executionTimeMs(executionTimeMs)
                    .inputTokens(estimateTokens(inputs))
                    .outputTokens(estimateTokens(output))
                    .qualityScore(program.validationScore())
                    .cacheHit(true)  // Saved programs are always "cached"
                    .contextReused(true)
                    .timestamp(metricsTimestamp)
                    .build();

            log.debug("DSPy program '{}' executed in {}ms", program.name(), totalTimeMs);

            return new DspyExecutionResult(output, null, metrics);

        } catch (PythonException e) {
            log.error("DSPy execution failed for program '{}': {}", program.name(), e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error executing DSPy program '{}': {}", program.name(), e.getMessage(), e);
            throw new PythonException("DSPy execution failed: " + e.getMessage(), PythonException.ErrorKind.RUNTIME_ERROR);
        }
    }

    /**
     * Builds Python code to reconstruct and execute a saved program.
     */
    private String buildExecutionPythonCode(DspySavedProgram program, Map<String, Object> inputs) {
        StringBuilder code = new StringBuilder();

        // Import DSPy
        code.append("import dspy\n");
        code.append("import json\n\n");

        // Reconstruct signature for first predictor
        if (!program.predictors().isEmpty()) {
            DspyPredictorConfig predictor = program.firstPredictor();
            String instructions = predictor.getEffectiveInstructions();
            List<String> inputFields = predictor.getInputFieldNames();
            List<String> outputFields = predictor.getOutputFieldNames();

            // Build signature
            code.append("class ReconstructedSignature(dspy.Signature):\n");
            code.append("    \"\"\"").append(escapePythonString(instructions)).append("\"\"\"\n");
            for (String inputField : inputFields) {
                code.append("    ").append(inputField).append(" = dspy.InputField()\n");
            }
            for (String outputField : outputFields) {
                code.append("    ").append(outputField).append(" = dspy.OutputField()\n");
            }
            code.append("\n");

            // Build module with ChainOfThought
            code.append("class ReconstructedModule(dspy.Module):\n");
            code.append("    def __init__(self):\n");
            code.append("        super().__init__()\n");
            code.append("        self.predict = dspy.ChainOfThought(ReconstructedSignature)\n");
            code.append("\n");
            code.append("    def forward(self, **kwargs):\n");
            code.append("        return self.predict(**kwargs)\n");
            code.append("\n");

            // Instantiate and execute
            code.append("_reconstructed_module = ReconstructedModule()\n");
            code.append("_dspy_result = _reconstructed_module.forward(");

            // Add input arguments
            int paramCount = 0;
            for (Map.Entry<String, Object> entry : inputs.entrySet()) {
                if (paramCount > 0) {
                    code.append(", ");
                }
                code.append(entry.getKey()).append("=").append(formatPythonLiteral(entry.getValue()));
                paramCount++;
            }
            code.append(")\n");

            // Extract result
            code.append("_output = {}\n");
            for (String outputField : outputFields) {
                code.append("_output['").append(outputField).append("'] = str(getattr(_dspy_result, '")
                        .append(outputField).append("', ''))\n");
            }
            code.append("_result = _output\n");
        }

        return code.toString();
    }

    /**
     * Hot-reloads a program from disk.
     *
     * <p>Call this after external optimization (e.g., GEPA training) to pick up
     * the new program state without restarting the JVM.</p>
     *
     * @param name program name to reload
     * @return the reloaded program
     * @throws IOException if the program file cannot be read
     * @throws DspyProgramNotFoundException if the program file does not exist
     */
    public DspySavedProgram reload(String name) throws IOException {
        Objects.requireNonNull(name, "Program name must not be null");

        Path programFile = programsDir.resolve(name + ".json");
        if (!Files.exists(programFile)) {
            throw new DspyProgramNotFoundException(
                    "Program file not found: " + programFile);
        }

        DspySavedProgram program = DspySavedProgram.loadFromJson(programFile);
        programs.put(name, program);

        log.info("Reloaded DSPy program: {} (hash={})", name, program.sourceHash());
        return program;
    }

    /**
     * Reloads all programs from disk.
     *
     * @return count of successfully reloaded programs
     */
    public int reloadAll() {
        programs.clear();
        loadAllPrograms();
        return programs.size();
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
     * Returns registry statistics.
     *
     * @return map of stats
     */
    public Map<String, Object> getStats() {
        return Map.of(
                "programCount", programs.size(),
                "programsDir", programsDir.toString(),
                "programs", listProgramNames()
        );
    }
}
