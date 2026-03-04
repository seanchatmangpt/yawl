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

package org.yawlfoundation.yawl.graalpy;

import org.graalvm.polyglot.Value;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Main entry point for in-process Python execution within YAWL workflows.
 *
 * <p>The {@code PythonExecutionEngine} provides a high-level, thread-safe API for
 * Java enterprise applications to execute Python code directly inside the JVM
 * memory space via GraalPy, eliminating the IPC overhead of separate Python
 * microservices.</p>
 *
 * <h2>Architecture</h2>
 * <pre>
 * PythonExecutionEngine
 *   ├─ PythonContextPool          thread-safe pool of GraalPy Contexts
 *   │    └─ PythonExecutionContext  one GraalPy interpreter per pool slot
 *   ├─ TypeMarshaller              Java ↔ Python type conversion
 *   ├─ PythonSandboxConfig         security policy (STRICT / STANDARD / PERMISSIVE)
 *   └─ PythonBytecodeCache         .pyc caching for cold-start mitigation
 * </pre>
 *
 * <h2>Quick start</h2>
 * <pre>{@code
 * // Build once, reuse across workflow tasks
 * PythonExecutionEngine engine = PythonExecutionEngine.builder()
 *     .sandboxed(true)
 *     .contextPoolSize(4)
 *     .build();
 *
 * // Execute a Python expression
 * String greeting = engine.evalToString("'Hello from GraalPy ' + str(42)");
 * // greeting == "Hello from GraalPy 42"
 *
 * // Invoke a specific Python function from a module file
 * engine.evalScript(Path.of("sentiment.py"));
 * double score = engine.evalToDouble("sentiment.analyze('YAWL is great!')");
 *
 * // Convert Python dict result to Java Map
 * Map<String, Object> stats = engine.evalToMap("{'mean': 3.14, 'n': 100}");
 *
 * engine.close();
 * }</pre>
 *
 * <h2>Thread safety</h2>
 * <p>All public methods are thread-safe. The internal {@link PythonContextPool}
 * distributes concurrent requests across pooled GraalPy contexts.</p>
 *
 * <h2>Runtime requirement</h2>
 * <p>GraalVM JDK 24.1+ must be used to launch the application. On standard JDK
 * (e.g., Temurin), construction succeeds but all {@code eval*} calls will throw
 * {@link PythonException} with kind {@code RUNTIME_NOT_AVAILABLE}.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class PythonExecutionEngine implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PythonExecutionEngine.class);

    private final PythonContextPool contextPool;
    private final @Nullable PythonBytecodeCache bytecodeCache;

    private PythonExecutionEngine(Builder builder) {
        PythonSandboxConfig sandboxConfig = builder.sandboxed
                ? PythonSandboxConfig.strict()
                : PythonSandboxConfig.permissive();
        if (builder.sandboxConfig != null) {
            sandboxConfig = builder.sandboxConfig;
        }

        this.contextPool = PythonContextPool.builder()
                .sandboxConfig(sandboxConfig)
                .maxPoolSize(builder.contextPoolSize)
                .build();

        this.bytecodeCache = builder.bytecodeCacheDir != null
                ? new PythonBytecodeCache(builder.bytecodeCacheDir)
                : null;

        log.info("PythonExecutionEngine ready: poolSize={}, bytecodeCache={}",
                builder.contextPoolSize, builder.bytecodeCacheDir != null ? "enabled" : "disabled");
    }

    /**
     * Evaluates a Python expression or statement block.
     *
     * <p>The result is returned as an untyped {@link Object}. For typed variants,
     * see {@link #evalToString}, {@link #evalToDouble}, {@link #evalToMap},
     * and {@link #evalToList}.</p>
     *
     * @param pythonSource  Python source to evaluate; must not be null
     * @return the result of the last expression, converted via {@link TypeMarshaller}; may be null
     * @throws PythonException  if execution fails
     */
    public @Nullable Object eval(String pythonSource) {
        return contextPool.execute(ctx -> {
            Value result = ctx.eval(pythonSource);
            return TypeMarshaller.toJava(result);
        });
    }

    /**
     * Evaluates a Python expression and returns the result as a {@link String}.
     *
     * @param pythonSource  Python source that evaluates to a string; must not be null
     * @return the string result; never null
     * @throws PythonException  if the result is not a string or execution fails
     */
    public String evalToString(String pythonSource) {
        return contextPool.execute(ctx -> {
            Value result = ctx.eval(pythonSource);
            return TypeMarshaller.toString(result);
        });
    }

    /**
     * Evaluates a Python expression and returns the result as a {@code double}.
     *
     * @param pythonSource  Python source that evaluates to a numeric value; must not be null
     * @return the double result
     * @throws PythonException  if the result is not numeric or execution fails
     */
    public double evalToDouble(String pythonSource) {
        return contextPool.execute(ctx -> {
            Value result = ctx.eval(pythonSource);
            return TypeMarshaller.toDouble(result);
        });
    }

    /**
     * Evaluates a Python expression and returns the result as a {@code long}.
     *
     * @param pythonSource  Python source that evaluates to an integer; must not be null
     * @return the long result
     * @throws PythonException  if the result is not an integer or execution fails
     */
    public long evalToLong(String pythonSource) {
        return contextPool.execute(ctx -> {
            Value result = ctx.eval(pythonSource);
            return TypeMarshaller.toLong(result);
        });
    }

    /**
     * Evaluates a Python expression and returns the result as a {@code Map<String, Object>}.
     *
     * @param pythonSource  Python source that evaluates to a dict; must not be null
     * @return the map result with recursively converted values; never null
     * @throws PythonException  if the result is not a dict or execution fails
     */
    public Map<String, Object> evalToMap(String pythonSource) {
        return contextPool.execute(ctx -> {
            Value result = ctx.eval(pythonSource);
            return TypeMarshaller.toMap(result);
        });
    }

    /**
     * Evaluates a Python expression and returns the result as a {@code List<Object>}.
     *
     * @param pythonSource  Python source that evaluates to a list or tuple; must not be null
     * @return the list result with recursively converted elements; never null
     * @throws PythonException  if the result is not a list/tuple or execution fails
     */
    public List<Object> evalToList(String pythonSource) {
        return contextPool.execute(ctx -> {
            Value result = ctx.eval(pythonSource);
            return TypeMarshaller.toList(result);
        });
    }

    /**
     * Evaluates a Python source file in a pooled context.
     *
     * <p>The script is evaluated once per context creation to define module-level
     * names, functions, and classes. Subsequent {@link #eval} calls can reference
     * the names defined by the script.</p>
     *
     * <p><em>Note</em>: Each context in the pool is independent. To ensure
     * scripts are available in all pool contexts, pre-warm the pool by calling
     * this method concurrently, or use {@link #evalScriptInAllContexts(Path)}.</p>
     *
     * @param scriptPath  path to a {@code .py} file; must not be null and must exist
     * @return the raw result of the script's last expression; may be null
     * @throws PythonException  if the file cannot be read or contains errors
     */
    public @Nullable Object evalScript(Path scriptPath) {
        return contextPool.execute(ctx -> {
            Value result = ctx.evalScript(scriptPath);
            if (bytecodeCache != null) {
                bytecodeCache.markCached(scriptPath);
            }
            return TypeMarshaller.toJava(result);
        });
    }

    /**
     * Evaluates a Python source file in ALL pool contexts to ensure the script's
     * definitions are available regardless of which context handles future requests.
     *
     * <p>This method uses virtual threads for parallel loading. Call once at startup
     * for scripts that define shared functions or classes used across many tasks.</p>
     *
     * @param scriptPath  path to a {@code .py} file; must not be null and must exist
     * @throws PythonException  if the file contains errors
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
                throw new PythonException(
                        "Interrupted while loading script in all pool contexts: " + scriptPath,
                        PythonException.ErrorKind.CONTEXT_ERROR, e);
            }
        }
        log.info("Script loaded in all {} pool contexts: {}", maxContexts, scriptPath);
    }

    /**
     * Imports a Python module and invokes a named function on it.
     *
     * <p>Equivalent to:
     * <pre>{@code
     * import <moduleName>
     * result = <moduleName>.<functionName>(*args)
     * }</pre>
     * </p>
     *
     * @param moduleName    Python module name (e.g. {@code "numpy"})
     * @param functionName  name of the function to invoke on the module
     * @param args          arguments to pass to the function (Java values are auto-marshalled)
     * @return the return value of the Python function, converted via {@link TypeMarshaller}; may be null
     * @throws PythonException  if the module or function is not found, or invocation fails
     */
    public @Nullable Object invokePythonFunction(String moduleName, String functionName, Object... args) {
        return contextPool.execute(ctx -> {
            Value module = ctx.importModule(moduleName);
            Value function = module.getMember(functionName);
            if (function == null || function.isNull()) {
                throw new PythonException(
                        "Function '" + functionName + "' not found in module '" + moduleName + "'.",
                        PythonException.ErrorKind.RUNTIME_ERROR);
            }
            if (!function.canExecute()) {
                throw new PythonException(
                        "'" + moduleName + "." + functionName + "' is not callable.",
                        PythonException.ErrorKind.RUNTIME_ERROR);
            }
            Value result = function.execute(args);
            return TypeMarshaller.toJava(result);
        });
    }

    /**
     * Evaluates a Python expression and maps the result to a Java interface type
     * via the GraalVM Polyglot type coercion system.
     *
     * <p>Requires that the Python object implements the interface contract as
     * understood by GraalPy's interop protocol. Use {@link PythonInterfaceGenerator}
     * to generate strongly-typed Java interfaces from Python type stubs.</p>
     *
     * @param pythonSource  Python source that evaluates to an object matching {@code type}
     * @param type          the Java interface or class to coerce to; must not be null
     * @param <T>           the target type
     * @return the coerced value; never null
     * @throws PythonException  if coercion fails or the Python value is incompatible
     */
    public <T> T evalAs(String pythonSource, Class<T> type) {
        return contextPool.execute(ctx -> {
            Value result = ctx.eval(pythonSource);
            return TypeMarshaller.as(result, type);
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
    public PythonContextPool getContextPool() { return contextPool; }

    /**
     * Closes the engine and all pooled Python contexts.
     *
     * <p>Blocks until all in-flight executions complete. After closing, all
     * {@code eval*} methods will throw.</p>
     */
    @Override
    public void close() {
        contextPool.close();
        log.info("PythonExecutionEngine closed");
    }

    /** Returns a new {@link Builder} for configuring a {@code PythonExecutionEngine}. */
    public static Builder builder() { return new Builder(); }

    // ── Builder ───────────────────────────────────────────────────────────────────

    /**
     * Builder for {@link PythonExecutionEngine}.
     *
     * <pre>{@code
     * PythonExecutionEngine engine = PythonExecutionEngine.builder()
     *     .sandboxConfig(PythonSandboxConfig.standard())
     *     .contextPoolSize(8)
     *     .bytecodeCacheDir(Path.of("/tmp/graalpy-cache"))
     *     .build();
     * }</pre>
     */
    public static final class Builder {
        private boolean sandboxed = true;
        private @Nullable PythonSandboxConfig sandboxConfig = null;
        private int contextPoolSize = Math.max(1, Runtime.getRuntime().availableProcessors());
        private @Nullable Path bytecodeCacheDir = null;

        private Builder() {}

        /**
         * Enables or disables sandboxing. {@code true} (default) applies the STRICT
         * sandbox; use {@link #sandboxConfig(PythonSandboxConfig)} for custom policies.
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
        public Builder sandboxConfig(PythonSandboxConfig config) { this.sandboxConfig = config; return this; }

        /**
         * Sets the number of GraalPy contexts in the pool.
         *
         * <p>Defaults to {@code Runtime.getRuntime().availableProcessors()}.
         * Higher values increase parallelism at the cost of memory and initialisation time.</p>
         *
         * @param poolSize  number of pooled contexts (≥ 1)
         * @return this builder
         */
        public Builder contextPoolSize(int poolSize) { this.contextPoolSize = poolSize; return this; }

        /**
         * Enables bytecode caching for compiled Python scripts at the given directory.
         *
         * <p>When set, evaluated script files ({@code .py}) are compiled once and cached
         * as {@code .pyc} files. The cache is invalidated when the source file changes.</p>
         *
         * @param cacheDir  directory for bytecode cache files; must not be null
         * @return this builder
         */
        public Builder bytecodeCacheDir(Path cacheDir) { this.bytecodeCacheDir = cacheDir; return this; }

        /**
         * Builds the {@link PythonExecutionEngine}.
         *
         * @return a new engine; never null
         * @throws IllegalArgumentException  if contextPoolSize < 1
         */
        public PythonExecutionEngine build() {
            if (contextPoolSize < 1) {
                throw new IllegalArgumentException("contextPoolSize must be at least 1");
            }
            return new PythonExecutionEngine(this);
        }
    }
}
