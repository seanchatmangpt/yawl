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

package org.yawlfoundation.yawl.graalwasm;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A loaded and instantiated WebAssembly module.
 *
 * <p>Wraps the exports Value returned by evaluating a WASM Source in a GraalWasm context.
 * NOT thread-safe. The caller owns the lifecycle; call close() when done.</p>
 *
 * <h2>Lifecycle</h2>
 * <p>Use try-with-resources to ensure proper cleanup:
 * <pre>{@code
 * try (WasmModule mod = engine.loadModuleFromClasspath("wasm/module.wasm", "mod")) {
 *     Value result = mod.execute("function_name", args);
 * }
 * }</pre>
 * </p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class WasmModule implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(WasmModule.class);

    private final Context context;
    private final Value exports;
    private final String moduleName;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Package-private constructor; instantiated by WasmExecutionEngine.
     *
     * @param context  the GraalWasm context; must not be null
     * @param source  the WASM Source; must not be null
     * @param moduleName  the name of this module; must not be null
     * @throws WasmException  if instantiation fails
     */
    WasmModule(Context context, Source source, String moduleName) {
        this.context = context;
        this.moduleName = moduleName;
        try {
            this.exports = context.eval(source);
        } catch (PolyglotException e) {
            throw new WasmException(
                    "WASM module instantiation failed for '" + moduleName + "': " + e.getMessage(),
                    WasmException.ErrorKind.INSTANTIATION_ERROR, e);
        }
    }

    /**
     * Returns a named export from the WASM module.
     *
     * @param name  the export name; must not be null
     * @return the exported value; never null
     * @throws WasmException  if the export is not found
     */
    public Value getMember(String name) {
        assertOpen();
        Value member = exports.getMember(name);
        if (member == null || member.isNull()) {
            throw new WasmException(
                    "WASM export '" + name + "' not found in module '" + moduleName + "'.",
                    WasmException.ErrorKind.FUNCTION_NOT_FOUND);
        }
        return member;
    }

    /**
     * Executes a named WASM exported function.
     *
     * @param functionName  the name of the function to call; must not be null
     * @param args  arguments to pass to the function
     * @return the return value of the function; may be null if WASM returns null
     * @throws WasmException  if the function is not found or execution fails
     */
    public Value execute(String functionName, Object... args) {
        assertOpen();
        Value fn = getMember(functionName);
        if (!fn.canExecute()) {
            throw new WasmException(
                    "WASM export '" + functionName + "' is not callable.",
                    WasmException.ErrorKind.FUNCTION_NOT_FOUND);
        }
        try {
            return fn.execute(args);
        } catch (PolyglotException e) {
            throw new WasmException(
                    "WASM function '" + functionName + "' trapped: " + e.getMessage(),
                    WasmException.ErrorKind.EXECUTION_ERROR, e);
        }
    }

    /**
     * Executes a WASM function, discarding the return value.
     *
     * @param functionName  the name of the function to call; must not be null
     * @param args  arguments to pass to the function
     * @throws WasmException  if the function is not found or execution fails
     */
    public void executeVoid(String functionName, Object... args) {
        execute(functionName, args);
    }

    /**
     * Returns the set of exported member names in this module.
     *
     * @return an unmodifiable set of export names; never null
     * @throws WasmException  if the module is closed
     */
    public Set<String> getMemberKeys() {
        assertOpen();
        return exports.getMemberKeys();
    }

    /**
     * Returns the name of this module.
     *
     * @return the module name; never null
     */
    public String getModuleName() {
        return moduleName;
    }

    /**
     * Returns whether this module has been closed.
     *
     * @return {@code true} if closed, {@code false} otherwise
     */
    public boolean isClosed() {
        return closed.get();
    }

    /**
     * Closes the underlying GraalWasm context, releasing all resources.
     *
     * <p>Idempotent: subsequent calls are no-ops.</p>
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                context.close();
                log.debug("WasmModule '{}' closed", moduleName);
            } catch (Exception e) {
                log.warn("Error closing WasmModule context: {}", e.getMessage(), e);
            }
        }
    }

    private void assertOpen() {
        if (closed.get()) {
            throw new WasmException(
                    "WasmModule '" + moduleName + "' has been closed.",
                    WasmException.ErrorKind.EXECUTION_ERROR);
        }
    }
}
