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

package org.yawlfoundation.yawl.graaljs;

import org.graalvm.polyglot.Value;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Main entry point for in-process JavaScript execution within YAWL workflows.
 *
 * <p>The {@code JavaScriptExecutionEngine} provides a high-level, thread-safe API for
 * Java enterprise applications to execute JavaScript code directly inside the JVM
 * memory space via GraalJS, eliminating the IPC overhead of separate JavaScript
 * microservices.</p>
 *
 * <h2>Architecture</h2>
 * <pre>
 * JavaScriptExecutionEngine
 *   ├─ JavaScriptContextPool          thread-safe pool of GraalJS Contexts
 *   │    └─ JavaScriptExecutionContext  one GraalJS interpreter per pool slot
 *   ├─ JsTypeMarshaller               Java ↔ JavaScript type conversion
 *   └─ JavaScriptSandboxConfig        security policy (STRICT/STANDARD/PERMISSIVE/forWasm)
 * </pre>
 *
 * <h2>Quick start</h2>
 * <pre>{@code
 * // Build once, reuse across workflow tasks
 * JavaScriptExecutionEngine engine = JavaScriptExecutionEngine.builder()
 *     .sandboxed(true)
 *     .contextPoolSize(4)
 *     .build();
 *
 * // Execute a JavaScript expression
 * String greeting = engine.evalToString("'Hello from GraalJS ' + 42");
 * // greeting == "Hello from GraalJS 42"
 *
 * // Invoke a specific JavaScript function from a module file
 * engine.evalScript(Path.of("rules.js"));
 * double score = engine.evalToDouble("evaluateRisk('high-complexity')");
 *
 * // Convert JavaScript object result to Java Map
 * Map<String, Object> stats = engine.evalToMap("({ mean: 3.14, n: 100 })");
 *
 * engine.close();
 * }</pre>
 *
 * <h2>Thread safety</h2>
 * <p>All public methods are thread-safe. The internal {@link JavaScriptContextPool}
 * distributes concurrent requests across pooled GraalJS contexts.</p>
 *
 * <h2>Runtime requirement</h2>
 * <p>GraalVM JDK 24.1+ must be used to launch the application. On standard JDK
 * (e.g., Temurin), construction succeeds but all {@code eval*} calls will throw
 * {@link JavaScriptException} with kind {@code RUNTIME_NOT_AVAILABLE}.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class JavaScriptExecutionEngine implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(JavaScriptExecutionEngine.class);

    private final JavaScriptContextPool contextPool;

    private JavaScriptExecutionEngine(Builder builder) {
        JavaScriptSandboxConfig sandboxConfig = builder.sandboxed
                ? JavaScriptSandboxConfig.strict()
                : JavaScriptSandboxConfig.permissive();
        if (builder.sandboxConfig != null) {
            sandboxConfig = builder.sandboxConfig;
        }

        this.contextPool = JavaScriptContextPool.builder()
                .sandboxConfig(sandboxConfig)
                .maxPoolSize(builder.contextPoolSize)
                .build();

        log.info("JavaScriptExecutionEngine ready: poolSize={}", builder.contextPoolSize);
    }

    /**
     * Evaluates a JavaScript expression or statement block.
     *
     * <p>The result is returned as an untyped {@link Object}. For typed variants,
     * see {@link #evalToString}, {@link #evalToDouble}, {@link #evalToMap},
     * and {@link #evalToList}.</p>
     *
     * @param javaScriptSource  JavaScript source to evaluate; must not be null
     * @return the result of the last expression, converted via {@link JsTypeMarshaller}; may be null
     * @throws JavaScriptException  if execution fails
     */
    public @Nullable Object eval(String javaScriptSource) {
        return contextPool.execute(ctx -> {
            Value result = ctx.eval(javaScriptSource);
            return JsTypeMarshaller.toJava(result);
        });
    }

    /**
     * Evaluates a JavaScript expression and returns the result as a {@link String}.
     *
     * @param javaScriptSource  JavaScript source that evaluates to a string; must not be null
     * @return the string result; never null
     * @throws JavaScriptException  if the result is not a string or execution fails
     */
    public String evalToString(String javaScriptSource) {
        return contextPool.execute(ctx -> {
            Value result = ctx.eval(javaScriptSource);
            return JsTypeMarshaller.toString(result);
        });
    }

    /**
     * Evaluates a JavaScript expression and returns the result as a {@code double}.
     *
     * @param javaScriptSource  JavaScript source that evaluates to a numeric value; must not be null
     * @return the double result
     * @throws JavaScriptException  if the result is not numeric or execution fails
     */
    public double evalToDouble(String javaScriptSource) {
        return contextPool.execute(ctx -> {
            Value result = ctx.eval(javaScriptSource);
            return JsTypeMarshaller.toDouble(result);
        });
    }

    /**
     * Evaluates a JavaScript expression and returns the result as a {@code long}.
     *
     * @param javaScriptSource  JavaScript source that evaluates to an integer; must not be null
     * @return the long result
     * @throws JavaScriptException  if the result is not an integer or execution fails
     */
    public long evalToLong(String javaScriptSource) {
        return contextPool.execute(ctx -> {
            Value result = ctx.eval(javaScriptSource);
            return JsTypeMarshaller.toLong(result);
        });
    }

    /**
     * Evaluates a JavaScript expression and returns the result as a {@code Map<String, Object>}.
     *
     * @param javaScriptSource  JavaScript source that evaluates to an object; must not be null
     * @return the map result with recursively converted values; never null
     * @throws JavaScriptException  if the result is not an object or execution fails
     */
    public Map<String, Object> evalToMap(String javaScriptSource) {
        return contextPool.execute(ctx -> {
            Value result = ctx.eval(javaScriptSource);
            return JsTypeMarshaller.toMap(result);
        });
    }

    /**
     * Evaluates a JavaScript expression and returns the result as a {@code List<Object>}.
     *
     * @param javaScriptSource  JavaScript source that evaluates to an array; must not be null
     * @return the list result with recursively converted elements; never null
     * @throws JavaScriptException  if the result is not an array or execution fails
     */
    public List<Object> evalToList(String javaScriptSource) {
        return contextPool.execute(ctx -> {
            Value result = ctx.eval(javaScriptSource);
            return JsTypeMarshaller.toList(result);
        });
    }

    /**
     * Evaluates a JavaScript source file in a pooled context.
     *
     * <p>The script is evaluated once per context to define module-level
     * names, functions, and classes. Subsequent {@link #eval} calls can reference
     * the names defined by the script.</p>
     *
     * <p><em>Note</em>: Each context in the pool is independent. To ensure
     * scripts are available in all pool contexts, pre-warm the pool by calling
     * this method concurrently, or use {@link #evalScriptInAllContexts(Path)}.</p>
     *
     * @param scriptPath  path to a {@code .js} file; must not be null and must exist
     * @return the raw result of the script's last expression; may be null
     * @throws JavaScriptException  if the file cannot be read or contains errors
     */
    public @Nullable Object evalScript(Path scriptPath) {
        return contextPool.execute(ctx -> {
            Value result = ctx.evalScript(scriptPath);
            return JsTypeMarshaller.toJava(result);
        });
    }

    /**
     * Evaluates a JavaScript source file in ALL pool contexts to ensure the script's
     * definitions are available regardless of which context handles future requests.
     *
     * <p>This method uses virtual threads for parallel loading. Call once at startup
     * for scripts that define shared functions or classes used across many tasks.</p>
     *
     * @param scriptPath  path to a {@code .js} file; must not be null and must exist
     * @throws JavaScriptException  if the file contains errors
     */
    public void evalScriptInAllContexts(Path scriptPath) {
        int maxContexts = contextPool.getMaxTotal();
        Thread[] threads = new Thread[maxContexts];
        for (int i = 0; i < maxContexts; i++) {
            threads[i] = Thread.ofVirtual().start(() -> evalScript(scriptPath));
        }
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new JavaScriptException(
                        "Interrupted while loading script in all pool contexts: " + scriptPath,
                        JavaScriptException.ErrorKind.CONTEXT_ERROR, e);
            }
        }
        log.info("Script loaded in all {} pool contexts: {}", maxContexts, scriptPath);
    }

    /**
     * Invokes a top-level JavaScript function from the global namespace.
     *
     * @param functionName  the name of the function in global scope; must not be null
     * @param args  arguments to pass to the function (Java values are auto-marshalled)
     * @return the return value of the JavaScript function, converted via {@link JsTypeMarshaller}; may be null
     * @throws JavaScriptException  if the function is not found, not callable, or invocation fails
     */
    public @Nullable Object invokeJsFunction(String functionName, Object... args) {
        return contextPool.execute(ctx -> {
            Value result = ctx.invokeFunction(functionName, args);
            return JsTypeMarshaller.toJava(result);
        });
    }

    /**
     * Evaluates a JavaScript expression and maps the result to a Java interface type
     * via the GraalVM Polyglot type coercion system.
     *
     * @param javaScriptSource  JavaScript source that evaluates to an object matching {@code type}
     * @param type  the Java interface or class to coerce to; must not be null
     * @param <T>  the target type
     * @return the coerced value; never null
     * @throws JavaScriptException  if coercion fails or the JavaScript value is incompatible
     */
    public <T> T evalAs(String javaScriptSource, Class<T> type) {
        return contextPool.execute(ctx -> {
            Value result = ctx.eval(javaScriptSource);
            return JsTypeMarshaller.as(result, type);
        });
    }

    /**
     * Evaluates a JavaScript expression that produces a JSON-serializable object,
     * parses the result via JSON.stringify() and jackson, and returns it as a Map.
     *
     * <p>This method is more reliable for complex nested objects than toMap(),
     * as it uses JSON serialization to ensure correct type conversions.</p>
     *
     * @param javaScriptSource  JavaScript source that evaluates to a JSON-serializable object
     * @return a Map representation of the parsed JSON; never null
     * @throws JavaScriptException  if the result is not JSON-serializable or parsing fails
     */
    public Map<String, Object> evalAsJson(String javaScriptSource) {
        return contextPool.execute(ctx -> {
            Value jsonStr = ctx.eval("JSON.stringify(" + javaScriptSource + ")");
            return JsTypeMarshaller.parseJsonString(JsTypeMarshaller.toString(jsonStr));
        });
    }

    /**
     * Returns the underlying context pool for direct pool management.
     *
     * <p>Use this to inspect pool health metrics (active/idle counts) or to execute
     * multi-step operations within a single borrowed context for atomicity.</p>
     *
     * @return the context pool; never null
     */
    public JavaScriptContextPool getContextPool() { return contextPool; }

    /**
     * Closes the engine and all pooled JavaScript contexts.
     *
     * <p>Blocks until all in-flight executions complete. After closing, all
     * {@code eval*} methods will throw.</p>
     */
    @Override
    public void close() {
        contextPool.close();
        log.info("JavaScriptExecutionEngine closed");
    }

    /** Returns a new {@link Builder} for configuring a {@code JavaScriptExecutionEngine}. */
    public static Builder builder() { return new Builder(); }

    // ── Builder ───────────────────────────────────────────────────────────────────

    /**
     * Builder for {@link JavaScriptExecutionEngine}.
     *
     * <pre>{@code
     * JavaScriptExecutionEngine engine = JavaScriptExecutionEngine.builder()
     *     .sandboxConfig(JavaScriptSandboxConfig.standard())
     *     .contextPoolSize(8)
     *     .build();
     * }</pre>
     */
    public static final class Builder {
        private boolean sandboxed = true;
        private @Nullable JavaScriptSandboxConfig sandboxConfig = null;
        private int contextPoolSize = Math.max(1, Runtime.getRuntime().availableProcessors());

        private Builder() {}

        /**
         * Enables or disables sandboxing. {@code true} (default) applies the STRICT
         * sandbox; use {@link #sandboxConfig(JavaScriptSandboxConfig)} for custom policies.
         *
         * @param sandboxed  {@code true} for STRICT sandbox; {@code false} for PERMISSIVE
         * @return this builder
         */
        public Builder sandboxed(boolean sandboxed) { this.sandboxed = sandboxed; return this; }

        /**
         * Sets a custom sandbox configuration, overriding the {@link #sandboxed(boolean)} flag.
         *
         * @param config  the sandbox configuration; must not be null
         * @return this builder
         */
        public Builder sandboxConfig(JavaScriptSandboxConfig config) { this.sandboxConfig = config; return this; }

        /**
         * Sets the number of GraalJS contexts in the pool.
         *
         * <p>Defaults to {@code Runtime.getRuntime().availableProcessors()}.
         * Higher values increase parallelism at the cost of memory and initialisation time.</p>
         *
         * @param poolSize  number of pooled contexts (≥ 1)
         * @return this builder
         */
        public Builder contextPoolSize(int poolSize) { this.contextPoolSize = poolSize; return this; }

        /**
         * Builds the {@link JavaScriptExecutionEngine}.
         *
         * @return a new engine; never null
         * @throws IllegalArgumentException  if contextPoolSize < 1
         */
        public JavaScriptExecutionEngine build() {
            if (contextPoolSize < 1) {
                throw new IllegalArgumentException("contextPoolSize must be at least 1");
            }
            return new JavaScriptExecutionEngine(this);
        }
    }
}
