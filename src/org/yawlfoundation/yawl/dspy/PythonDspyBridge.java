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

package org.yawlfoundation.yawl.dspy;

import org.graalvm.polyglot.Value;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.graalpy.PythonException;
import org.yawlfoundation.yawl.graalpy.PythonExecutionEngine;
import org.yawlfoundation.yawl.graalpy.TypeMarshaller;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Main Java wrapper for executing DSPy programs via GraalPy.
 *
 * <p>The {@code PythonDspyBridge} provides high-level API for compiling and executing
 * DSPy programs within YAWL workflow tasks. It leverages {@link PythonExecutionEngine}
 * for Python execution infrastructure and {@link DspyProgramCache} for compiled program
 * caching.</p>
 *
 * <h2>Architecture</h2>
 * <pre>
 * PythonDspyBridge
 *   ├─ PythonExecutionEngine     (reused; provides context pooling)
 *   ├─ DspyProgramCache          (LRU cache for compiled programs)
 *   └─ PythonExecutionContext    (borrowed from engine's pool)
 * </pre>
 *
 * <h2>Workflow</h2>
 * <ol>
 *   <li><strong>First execution</strong> of a DSPy program:
 *       <ul>
 *         <li>Compile: Execute {@code dspy.compile(program)}</li>
 *         <li>Cache: Store compiled module in memory LRU cache</li>
 *         <li>Execute: Call compiled module's forward() with inputs</li>
 *       </ul>
 *   </li>
 *   <li><strong>Subsequent executions</strong> (same program name + source hash):
 *       <ul>
 *         <li>Load from cache (skip compilation)</li>
 *         <li>Execute: Call forward() with inputs</li>
 *       </ul>
 *   </li>
 * </ol>
 *
 * <h2>Quick Start</h2>
 * <pre>{@code
 * // Create bridge (once per JVM)
 * PythonExecutionEngine engine = PythonExecutionEngine.builder()
 *     .contextPoolSize(4)
 *     .build();
 * PythonDspyBridge bridge = new PythonDspyBridge(engine);
 *
 * // Define a DSPy program
 * DspyProgram program = DspyProgram.builder()
 *     .name("sentiment-analyzer")
 *     .source("""
 *         import dspy
 *         class SentimentAnalyzer(dspy.Module):
 *             def __init__(self):
 *                 self.classify = dspy.ChainOfThought("text -> sentiment")
 *             def forward(self, text):
 *                 return self.classify(text=text)
 *         """)
 *     .build();
 *
 * // Execute (compiled programs are cached by name + source hash)
 * DspyExecutionResult result = bridge.execute(program, Map.of(
 *     "text", "YAWL is fantastic!"
 * ));
 *
 * // Access result
 * Map<String, Object> output = result.output();
 * String sentiment = (String) output.get("sentiment");
 * DspyExecutionMetrics metrics = result.metrics();
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>All public methods are thread-safe. The internal cache and context pool
 * support concurrent access from multiple threads.</p>
 *
 * <h2>Error Handling</h2>
 * <p>DSPy runtime errors are wrapped in {@link PythonException}. The exception
 * includes the Python traceback and source code for debugging.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class PythonDspyBridge {

    private static final Logger log = LoggerFactory.getLogger(PythonDspyBridge.class);

    private final PythonExecutionEngine engine;
    private final DspyProgramCache cache;

    /**
     * Creates a new PythonDspyBridge.
     *
     * @param engine the PythonExecutionEngine to use for Python execution; must not be null
     * @throws NullPointerException if engine is null
     */
    public PythonDspyBridge(PythonExecutionEngine engine) {
        this.engine = Objects.requireNonNull(engine, "PythonExecutionEngine must not be null");
        this.cache = new DspyProgramCache();
        log.info("PythonDspyBridge initialized with context pool");
    }

    /**
     * Creates a new PythonDspyBridge with a custom cache.
     *
     * <p>This constructor is primarily for testing with a mock cache.</p>
     *
     * @param engine the PythonExecutionEngine to use; must not be null
     * @param cache  the program cache to use; must not be null
     * @throws NullPointerException if either parameter is null
     */
    PythonDspyBridge(PythonExecutionEngine engine, DspyProgramCache cache) {
        this.engine = Objects.requireNonNull(engine, "PythonExecutionEngine must not be null");
        this.cache = Objects.requireNonNull(cache, "DspyProgramCache must not be null");
        log.info("PythonDspyBridge initialized with custom cache");
    }

    /**
     * Executes a DSPy program with the given inputs.
     *
     * <p>The program is compiled on first execution and cached thereafter.
     * Subsequent executions with the same program name and source hash
     * skip compilation overhead.</p>
     *
     * @param program the DSPy program to execute; must not be null
     * @param inputs  the input parameters; must not be null
     * @return the execution result with output, trace, and metrics
     * @throws NullPointerException if program or inputs is null
     * @throws PythonException      if compilation or execution fails
     */
    public DspyExecutionResult execute(DspyProgram program, Map<String, Object> inputs) {
        Objects.requireNonNull(program, "DspyProgram must not be null");
        Objects.requireNonNull(inputs, "Inputs must not be null");

        long startTime = System.currentTimeMillis();
        Instant metricsTimestamp = Instant.now();

        try {
            String cacheKey = program.cacheKey();
            boolean cacheHit = cache.contains(cacheKey);

            // Step 1: Get or compile the DSPy module
            long compilationStart = System.currentTimeMillis();
            String compiledModuleName = cacheHit
                    ? cache.get(cacheKey)
                    : compileProgram(program, cacheKey);
            long compilationTimeMs = System.currentTimeMillis() - compilationStart;

            // Step 2: Execute the compiled module
            long executionStart = System.currentTimeMillis();
            Map<String, Object> output = executeCompiledModule(compiledModuleName, inputs);
            long executionTimeMs = System.currentTimeMillis() - executionStart;

            // Step 3: Build result with metrics
            DspyExecutionMetrics metrics = DspyExecutionMetrics.builder()
                    .compilationTimeMs(compilationTimeMs)
                    .executionTimeMs(executionTimeMs)
                    .inputTokens(estimateTokens(inputs))
                    .outputTokens(estimateTokens(output))
                    .qualityScore(null)  // Not available in base implementation
                    .cacheHit(cacheHit)
                    .contextReused(true)  // GraalPy context is reused from pool
                    .timestamp(metricsTimestamp)
                    .build();

            log.debug("DSPy program '{}' executed: cacheHit={}, compilation={}ms, execution={}ms",
                    program.name(), cacheHit, compilationTimeMs, executionTimeMs);

            return new DspyExecutionResult(output, null, metrics);

        } catch (PythonException e) {
            log.error("DSPy execution failed for program '{}': {}", program.name(), e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error executing DSPy program '{}': {}", program.name(), e.getMessage(), e);
            throw new PythonException("DSPy execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Compiles a DSPy program and caches the compiled module.
     *
     * <p>This method:
     * <ol>
     *   <li>Evaluates the program source code</li>
     *   <li>Instantiates the DSPy module class</li>
     *   <li>Calls dspy.compile() to optimize the module</li>
     *   <li>Stores the compiled module in cache</li>
     * </ol>
     * </p>
     *
     * @param program  the program to compile; must not be null
     * @param cacheKey the cache key for storing the compiled module; must not be null
     * @return the variable name of the compiled module
     * @throws PythonException if compilation fails
     */
    private String compileProgram(DspyProgram program, String cacheKey) {
        log.debug("Compiling DSPy program '{}' with source hash '{}'",
                program.name(), program.sourceHash());

        // Step 1: Evaluate the DSPy program source code
        engine.eval(program.source());
        log.debug("Program source evaluated for '{}'", program.name());

        // Step 2: Extract the main class name from the source
        // (Heuristic: look for "class ClassName(dspy.Module):" pattern)
        String className = extractMainClassName(program.source());
        if (className == null) {
            throw new PythonException("Could not find dspy.Module class in program source");
        }

        // Step 3: Instantiate the DSPy module
        String instanceVar = "dspy_instance_" + System.nanoTime();
        String instantiateCode = instanceVar + " = " + className + "()";
        engine.eval(instantiateCode);
        log.debug("Module instantiated as '{}'", instanceVar);

        // Step 4: Compile using dspy.compile()
        String compiledVar = "dspy_compiled_" + System.nanoTime();
        String compileCode = compiledVar + " = __import__('dspy').compile(" + instanceVar + ")";
        engine.eval(compileCode);
        log.debug("Module compiled and cached as '{}'", compiledVar);

        // Step 5: Cache the compiled module variable name
        cache.put(cacheKey, compiledVar);

        return compiledVar;
    }

    /**
     * Executes a compiled DSPy module with the given inputs.
     *
     * <p>Calls the module's forward() method with keyword arguments,
     * converts the result back to a Java Map.</p>
     *
     * @param compiledModuleName the variable name of the compiled module; must not be null
     * @param inputs             the input parameters; must not be null
     * @return the output as a Map<String, Object>
     * @throws PythonException if execution fails
     */
    private Map<String, Object> executeCompiledModule(String compiledModuleName, Map<String, Object> inputs) {
        log.debug("Executing compiled module '{}' with {} inputs", compiledModuleName, inputs.size());

        // Build Python code to call forward() with inputs as keyword arguments
        StringBuilder forwardCall = new StringBuilder();
        forwardCall.append("__dspy_result = ").append(compiledModuleName).append(".forward(");

        int paramCount = 0;
        for (Map.Entry<String, Object> entry : inputs.entrySet()) {
            if (paramCount > 0) {
                forwardCall.append(", ");
            }
            // Note: This assumes inputs are JSON-serializable
            forwardCall.append(entry.getKey()).append("=").append(formatPythonLiteral(entry.getValue()));
            paramCount++;
        }
        forwardCall.append(")");

        // Execute the forward() call
        engine.eval(forwardCall.toString());
        log.debug("forward() executed successfully");

        // Retrieve and convert the result
        @Nullable Object result = engine.eval("__dspy_result");
        if (result == null) {
            throw new PythonException("DSPy forward() returned null");
        }

        if (result instanceof Map<?, ?> resultMap) {
            @SuppressWarnings("unchecked")
            Map<String, Object> outputMap = (Map<String, Object>) resultMap;
            return outputMap;
        }

        // If result is not a map, wrap it
        Map<String, Object> output = new HashMap<>();
        output.put("result", result);
        return output;
    }

    /**
     * Extracts the main DSPy module class name from source code.
     *
     * <p>Looks for a pattern like "class ClassName(dspy.Module):" and returns
     * the class name.</p>
     *
     * @param source the Python source code; must not be null
     * @return the class name, or null if not found
     */
    private @Nullable String extractMainClassName(String source) {
        // Simple regex-based heuristic: find "class \w+\(dspy\.Module\)"
        String pattern = "class\\s+(\\w+)\\s*\\(\\s*dspy\\.Module\\s*\\)";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(source);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    /**
     * Formats a Java object as a Python literal string.
     *
     * <p>Supports basic types: String, Integer, Long, Double, Boolean.
     * Collections are converted to Python repr.</p>
     *
     * @param value the value to format; may be null
     * @return the Python literal representation
     */
    private String formatPythonLiteral(@Nullable Object value) {
        if (value == null) {
            return "None";
        }
        if (value instanceof String s) {
            // Escape quotes and newlines
            String escaped = s.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r");
            return "\"" + escaped + "\"";
        }
        if (value instanceof Boolean b) {
            return b ? "True" : "False";
        }
        if (value instanceof Number) {
            return value.toString();
        }
        if (value instanceof Map<?, ?> map) {
            // Convert Map to Python dict literal
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
            // Convert Iterable to Python list literal
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
        // Fallback: use toString()
        return "\"" + value.toString().replace("\"", "\\\"") + "\"";
    }

    /**
     * Estimates the token count for a given object.
     *
     * <p>This is a simple heuristic: roughly 1 token per 4 characters.
     * A more accurate implementation would use the actual LLM tokenizer.</p>
     *
     * @param obj the object to estimate tokens for; may be null
     * @return estimated token count
     */
    private long estimateTokens(@Nullable Object obj) {
        if (obj == null) {
            return 0L;
        }
        String str = obj.toString();
        return Math.max(1, str.length() / 4);
    }

    /**
     * Returns the cache statistics.
     *
     * <p>Useful for monitoring and tuning cache size.</p>
     *
     * @return cache statistics as a map
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("cacheSize", cache.size());
        stats.put("cacheMaxSize", cache.maxSize());
        return stats;
    }

    /**
     * Clears the program cache.
     *
     * <p>Call this if you need to free memory or reset compiled programs.</p>
     */
    public void clearCache() {
        cache.clear();
        log.info("DSPy program cache cleared");
    }
}
