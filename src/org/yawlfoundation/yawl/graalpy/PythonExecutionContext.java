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

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A managed wrapper around a GraalVM {@link Context} configured for Python execution.
 *
 * <p>Each instance holds exactly one GraalPy context — a single-threaded Python
 * interpreter with its own heap, module namespace, and GIL. Contexts are pooled
 * by {@link PythonContextPool} to support concurrent Java threads.</p>
 *
 * <h2>Thread safety</h2>
 * <p>A single {@code PythonExecutionContext} is <strong>not thread-safe</strong>.
 * Callers must ensure exclusive access, either by using {@link PythonContextPool}
 * (recommended) or by applying external synchronisation.</p>
 *
 * <h2>Lifecycle</h2>
 * <p>Call {@link #close()} when the context is no longer needed. Pooled contexts
 * are returned to the pool rather than closed; only the pool itself calls
 * {@code close()} on eviction.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class PythonExecutionContext implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PythonExecutionContext.class);

    static final String PYTHON_LANGUAGE_ID = "python";

    private final Context graalContext;
    private final PythonSandboxConfig sandboxConfig;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Creates a new Python execution context with the given sandbox configuration.
     *
     * @param sandboxConfig  security and I/O policy for this context
     * @throws PythonException  if GraalPy is not available at runtime or context
     *                          initialisation fails
     */
    public PythonExecutionContext(PythonSandboxConfig sandboxConfig) {
        this.sandboxConfig = sandboxConfig;
        try {
            Context.Builder builder = Context.newBuilder(PYTHON_LANGUAGE_ID)
                    .option("engine.WarnInterpreterOnly", "false");
            sandboxConfig.applyTo(builder);
            this.graalContext = builder.build();
            // Trigger Python runtime initialisation eagerly to detect missing GraalPy early
            this.graalContext.initialize(PYTHON_LANGUAGE_ID);
            log.debug("PythonExecutionContext initialised with sandbox={}", sandboxConfig.getMode());
        } catch (PolyglotException e) {
            throw new PythonException(
                    "GraalPy runtime initialisation failed. "
                    + "Ensure GraalVM JDK 24.1+ is used at runtime "
                    + "and 'python' language support is on the classpath. "
                    + "Cause: " + e.getMessage(),
                    PythonException.ErrorKind.RUNTIME_NOT_AVAILABLE, e);
        }
    }

    /**
     * Evaluates a Python expression or statement block and returns the result value.
     *
     * <p>The source string is compiled and executed in this context's global namespace.
     * Bindings created during execution persist for the lifetime of this context.</p>
     *
     * @param pythonSource  Python source code to execute; must not be null
     * @return the result of the last expression, or {@code null} for statement blocks
     * @throws PythonException  if the source contains a syntax error or raises a Python exception
     */
    public Value eval(String pythonSource) {
        assertOpen();
        try {
            return graalContext.eval(PYTHON_LANGUAGE_ID, pythonSource);
        } catch (PolyglotException e) {
            if (e.isSyntaxError()) {
                throw new PythonException(
                        "Python syntax error: " + e.getMessage(),
                        PythonException.ErrorKind.SYNTAX_ERROR, e);
            }
            throw new PythonException(
                    "Python execution error: " + e.getMessage(),
                    PythonException.ErrorKind.RUNTIME_ERROR, e);
        }
    }

    /**
     * Evaluates a Python source file in this context.
     *
     * <p>The file is compiled once and cached. Subsequent calls with the same path
     * reuse the compiled form from the bytecode cache.</p>
     *
     * @param scriptPath  path to a {@code .py} file; must not be null
     * @return the result of the last expression in the file, or {@code null}
     * @throws PythonException  if the file cannot be read or contains errors
     */
    public Value evalScript(Path scriptPath) {
        assertOpen();
        try {
            Source source = Source.newBuilder(PYTHON_LANGUAGE_ID, scriptPath.toFile()).build();
            return graalContext.eval(source);
        } catch (IOException e) {
            throw new PythonException(
                    "Cannot read Python script: " + scriptPath + " — " + e.getMessage(),
                    PythonException.ErrorKind.RUNTIME_ERROR, e);
        } catch (PolyglotException e) {
            if (e.isSyntaxError()) {
                throw new PythonException(
                        "Python syntax error in " + scriptPath + ": " + e.getMessage(),
                        PythonException.ErrorKind.SYNTAX_ERROR, e);
            }
            throw new PythonException(
                    "Python execution error in " + scriptPath + ": " + e.getMessage(),
                    PythonException.ErrorKind.RUNTIME_ERROR, e);
        }
    }

    /**
     * Returns the top-level Python bindings (global namespace) for this context.
     *
     * <p>Use this to read or write Python global variables, or to retrieve module
     * members after an {@code import} statement.</p>
     *
     * @return the Python polyglot bindings; never null
     * @throws PythonException  if the context is closed
     */
    public Value getBindings() {
        assertOpen();
        return graalContext.getBindings(PYTHON_LANGUAGE_ID);
    }

    /**
     * Imports a Python module and returns it as a polyglot {@link Value}.
     *
     * <p>Equivalent to executing {@code import <moduleName>} and returning the
     * module object from the Python namespace.</p>
     *
     * @param moduleName  fully-qualified Python module name (e.g. {@code "numpy"})
     * @return the imported module value; never null
     * @throws PythonException  if the module is not found or import fails
     */
    public Value importModule(String moduleName) {
        assertOpen();
        try {
            eval("import " + moduleName);
            Value bindings = getBindings();
            Value module = bindings.getMember(moduleName);
            if (module == null || module.isNull()) {
                throw new PythonException(
                        "Module '" + moduleName + "' not found after import. "
                        + "Verify the module is installed in the Python virtual environment.",
                        PythonException.ErrorKind.RUNTIME_ERROR);
            }
            return module;
        } catch (PythonException e) {
            throw e;
        } catch (PolyglotException e) {
            throw new PythonException(
                    "Failed to import Python module '" + moduleName + "': " + e.getMessage(),
                    PythonException.ErrorKind.RUNTIME_ERROR, e);
        }
    }

    /**
     * Invokes a member function on a Python object value.
     *
     * <p>Use this to call methods on imported modules or Python objects obtained
     * from prior {@link #eval} calls.</p>
     *
     * @param receiver  the Python object that owns the method; must not be null
     * @param methodName  the method name on {@code receiver}; must not be null
     * @param args  arguments to pass (Java values are auto-marshalled to Python)
     * @return the return value of the Python method; may be null if Python returns None
     * @throws PythonException  if the method does not exist or raises a Python exception
     */
    public Value invokeMember(Value receiver, String methodName, Object... args) {
        assertOpen();
        if (!receiver.canInvokeMember(methodName)) {
            throw new PythonException(
                    "Python object has no callable member '" + methodName + "'. "
                    + "Object type: " + receiver.getMetaObject(),
                    PythonException.ErrorKind.RUNTIME_ERROR);
        }
        try {
            return receiver.invokeMember(methodName, args);
        } catch (PolyglotException e) {
            throw new PythonException(
                    "Python method '" + methodName + "' raised an exception: " + e.getMessage(),
                    PythonException.ErrorKind.RUNTIME_ERROR, e);
        }
    }

    /**
     * Returns the underlying GraalVM {@link Context} for advanced direct-access use cases.
     *
     * <p>Direct Context usage bypasses sandbox enforcement and type safety provided by
     * this class. Prefer the higher-level API methods where possible.</p>
     *
     * @return the raw GraalVM Context; never null
     * @throws PythonException  if the context is closed
     */
    public Context getRawContext() {
        assertOpen();
        return graalContext;
    }

    /** Returns the sandbox configuration applied to this context. */
    public PythonSandboxConfig getSandboxConfig() { return sandboxConfig; }

    /** Returns {@code true} if this context has been closed. */
    public boolean isClosed() { return closed.get(); }

    /**
     * Closes the underlying GraalVM context, releasing all Python heap and resources.
     *
     * <p>Idempotent: subsequent calls are no-ops.</p>
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                graalContext.close();
                log.debug("PythonExecutionContext closed");
            } catch (Exception e) {
                log.warn("Error closing GraalPy context: {}", e.getMessage(), e);
            }
        }
    }

    private void assertOpen() {
        if (closed.get()) {
            throw new PythonException(
                    "PythonExecutionContext has been closed and cannot be reused. "
                    + "Obtain a new context from PythonContextPool.",
                    PythonException.ErrorKind.CONTEXT_ERROR);
        }
    }
}
