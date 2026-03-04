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
 * A managed wrapper around a GraalVM {@link Context} configured for JavaScript execution.
 *
 * <p>Each instance holds exactly one GraalJS context — a single-threaded JavaScript
 * interpreter with its own heap, module namespace, and execution engine. Contexts
 * are pooled by {@link JavaScriptContextPool} to support concurrent Java threads.</p>
 *
 * <h2>Thread safety</h2>
 * <p>A single {@code JavaScriptExecutionContext} is <strong>not thread-safe</strong>.
 * Callers must ensure exclusive access, either by using {@link JavaScriptContextPool}
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
public final class JavaScriptExecutionContext implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(JavaScriptExecutionContext.class);

    static final String JS_LANGUAGE_ID = "js";
    static final String WASM_LANGUAGE_ID = "wasm";

    private final Context graalContext;
    private final JavaScriptSandboxConfig sandboxConfig;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Creates a new JavaScript execution context with the given sandbox configuration.
     *
     * @param sandboxConfig  security and I/O policy for this context
     * @throws JavaScriptException  if GraalJS is not available at runtime or context
     *                              initialisation fails
     */
    public JavaScriptExecutionContext(JavaScriptSandboxConfig sandboxConfig) {
        this.sandboxConfig = sandboxConfig;
        try {
            String[] languages = sandboxConfig.isWasmEnabled()
                    ? new String[]{JS_LANGUAGE_ID, WASM_LANGUAGE_ID}
                    : new String[]{JS_LANGUAGE_ID};

            Context.Builder builder = Context.newBuilder(languages)
                    .option("engine.WarnInterpreterOnly", "false");
            sandboxConfig.applyTo(builder);
            this.graalContext = builder.build();

            this.graalContext.initialize(JS_LANGUAGE_ID);
            if (sandboxConfig.isWasmEnabled()) {
                this.graalContext.initialize(WASM_LANGUAGE_ID);
            }
            log.debug("JavaScriptExecutionContext initialised with sandbox={}, wasm={}",
                    sandboxConfig.getMode(), sandboxConfig.isWasmEnabled());
        } catch (PolyglotException | IllegalStateException | IllegalArgumentException e) {
            throw new JavaScriptException(
                    "GraalJS runtime initialisation failed. "
                    + "Ensure GraalVM JDK 24.1+ is used at runtime "
                    + "and 'js' language support is on the classpath. "
                    + "Cause: " + e.getMessage(),
                    JavaScriptException.ErrorKind.RUNTIME_NOT_AVAILABLE, e);
        }
    }

    /**
     * Evaluates a JavaScript expression or statement block and returns the result value.
     *
     * <p>The source string is compiled and executed in this context's global namespace.
     * Bindings created during execution persist for the lifetime of this context.</p>
     *
     * @param javaScriptSource  JavaScript source code to execute; must not be null
     * @return the result of the last expression, or {@code null} for statement blocks
     * @throws JavaScriptException  if the source contains a syntax error or raises a JavaScript exception
     */
    public Value eval(String javaScriptSource) {
        assertOpen();
        try {
            return graalContext.eval(JS_LANGUAGE_ID, javaScriptSource);
        } catch (PolyglotException e) {
            if (e.isSyntaxError()) {
                throw new JavaScriptException(
                        "JavaScript syntax error: " + e.getMessage(),
                        JavaScriptException.ErrorKind.SYNTAX_ERROR, e);
            }
            throw new JavaScriptException(
                    "JavaScript execution error: " + e.getMessage(),
                    JavaScriptException.ErrorKind.RUNTIME_ERROR, e);
        }
    }

    /**
     * Evaluates a JavaScript source file in this context.
     *
     * <p>The file is compiled and executed. Subsequent calls with the same path
     * compile and execute again (no caching).</p>
     *
     * @param scriptPath  path to a {@code .js} file; must not be null
     * @return the result of the last expression in the file, or {@code null}
     * @throws JavaScriptException  if the file cannot be read or contains errors
     */
    public Value evalScript(Path scriptPath) {
        assertOpen();
        try {
            Source source = Source.newBuilder(JS_LANGUAGE_ID, scriptPath.toFile()).build();
            return graalContext.eval(source);
        } catch (IOException e) {
            throw new JavaScriptException(
                    "Cannot read JavaScript script: " + scriptPath + " — " + e.getMessage(),
                    JavaScriptException.ErrorKind.RUNTIME_ERROR, e);
        } catch (PolyglotException e) {
            if (e.isSyntaxError()) {
                throw new JavaScriptException(
                        "JavaScript syntax error in " + scriptPath + ": " + e.getMessage(),
                        JavaScriptException.ErrorKind.SYNTAX_ERROR, e);
            }
            throw new JavaScriptException(
                    "JavaScript execution error in " + scriptPath + ": " + e.getMessage(),
                    JavaScriptException.ErrorKind.RUNTIME_ERROR, e);
        }
    }

    /**
     * Returns the top-level JavaScript bindings (global namespace) for this context.
     *
     * <p>Use this to read or write JavaScript global variables, or to retrieve module
     * members after script evaluation.</p>
     *
     * @return the JavaScript polyglot bindings; never null
     * @throws JavaScriptException  if the context is closed
     */
    public Value getBindings() {
        assertOpen();
        return graalContext.getBindings(JS_LANGUAGE_ID);
    }

    /**
     * Gets a named member from the global JavaScript namespace.
     *
     * @param name  the global variable or function name; must not be null
     * @return the value of the named binding; never null
     * @throws JavaScriptException  if the member is not found or context is closed
     */
    public Value getGlobalMember(String name) {
        assertOpen();
        Value bindings = getBindings();
        Value member = bindings.getMember(name);
        if (member == null || member.isNull()) {
            throw new JavaScriptException(
                    "Global variable '" + name + "' not found in JavaScript namespace.",
                    JavaScriptException.ErrorKind.MODULE_LOAD_ERROR);
        }
        return member;
    }

    /**
     * Invokes a top-level JavaScript function from the global namespace.
     *
     * @param functionName  the name of the function in global scope; must not be null
     * @param args  arguments to pass to the function (Java values are auto-marshalled)
     * @return the return value of the JavaScript function; may be null
     * @throws JavaScriptException  if the function is not found, not callable, or invocation fails
     */
    public Value invokeFunction(String functionName, Object... args) {
        assertOpen();
        try {
            Value function = getGlobalMember(functionName);
            if (!function.canExecute()) {
                throw new JavaScriptException(
                        "Global '" + functionName + "' is not callable. "
                        + "Type: " + function.getMetaObject(),
                        JavaScriptException.ErrorKind.RUNTIME_ERROR);
            }
            return function.execute(args);
        } catch (JavaScriptException e) {
            throw e;
        } catch (PolyglotException e) {
            throw new JavaScriptException(
                    "JavaScript function '" + functionName + "' raised an exception: " + e.getMessage(),
                    JavaScriptException.ErrorKind.RUNTIME_ERROR, e);
        }
    }

    /**
     * Invokes a member function on a JavaScript object value.
     *
     * <p>Use this to call methods on objects obtained from prior {@link #eval} calls
     * or from the global namespace.</p>
     *
     * @param receiver  the JavaScript object that owns the method; must not be null
     * @param methodName  the method name on {@code receiver}; must not be null
     * @param args  arguments to pass (Java values are auto-marshalled to JavaScript)
     * @return the return value of the JavaScript method; may be null
     * @throws JavaScriptException  if the method does not exist or raises a JavaScript exception
     */
    public Value invokeMember(Value receiver, String methodName, Object... args) {
        assertOpen();
        if (!receiver.canInvokeMember(methodName)) {
            throw new JavaScriptException(
                    "JavaScript object has no callable member '" + methodName + "'. "
                    + "Object type: " + receiver.getMetaObject(),
                    JavaScriptException.ErrorKind.RUNTIME_ERROR);
        }
        try {
            return receiver.invokeMember(methodName, args);
        } catch (PolyglotException e) {
            throw new JavaScriptException(
                    "JavaScript method '" + methodName + "' raised an exception: " + e.getMessage(),
                    JavaScriptException.ErrorKind.RUNTIME_ERROR, e);
        }
    }

    /**
     * Returns the underlying GraalVM {@link Context} for advanced direct-access use cases.
     *
     * <p>Direct Context usage bypasses sandbox enforcement and type safety provided by
     * this class. Prefer the higher-level API methods where possible.</p>
     *
     * @return the raw GraalVM Context; never null
     * @throws JavaScriptException  if the context is closed
     */
    public Context getRawContext() {
        assertOpen();
        return graalContext;
    }

    /** Returns the sandbox configuration applied to this context. */
    public JavaScriptSandboxConfig getSandboxConfig() { return sandboxConfig; }

    /** Returns {@code true} if this context has been closed. */
    public boolean isClosed() { return closed.get(); }

    /**
     * Closes the underlying GraalVM context, releasing all JavaScript heap and resources.
     *
     * <p>Idempotent: subsequent calls are no-ops.</p>
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                graalContext.close();
                log.debug("JavaScriptExecutionContext closed");
            } catch (Exception e) {
                log.warn("Error closing GraalJS context: {}", e.getMessage(), e);
            }
        }
    }

    private void assertOpen() {
        if (closed.get()) {
            throw new JavaScriptException(
                    "JavaScriptExecutionContext has been closed and cannot be reused. "
                    + "Obtain a new context from JavaScriptContextPool.",
                    JavaScriptException.ErrorKind.CONTEXT_ERROR);
        }
    }
}
