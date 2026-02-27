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
import org.graalvm.polyglot.Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Main entry point for in-process WASM execution within YAWL workflows.
 *
 * <p>Creates GraalWasm contexts on demand; caller owns returned WasmModule and must close() it.
 * Caches parsed Source objects via WasmBinaryCache for fast repeated loading.</p>
 *
 * <h2>Quick start</h2>
 * <pre>{@code
 * WasmExecutionEngine engine = WasmExecutionEngine.builder().build();
 * try (WasmModule mod = engine.loadModuleFromClasspath("wasm/compute.wasm", "compute")) {
 *     Value result = mod.execute("add", 3, 4);
 * }
 * engine.close();
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class WasmExecutionEngine implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(WasmExecutionEngine.class);
    private static final String WASM_LANGUAGE_ID = "wasm";

    private final WasmSandboxConfig sandboxConfig;
    private final WasmBinaryCache binaryCache;

    private WasmExecutionEngine(Builder builder) {
        this.sandboxConfig = builder.sandboxConfig;
        this.binaryCache = new WasmBinaryCache();
    }

    /**
     * Loads a WASM module from a classpath resource.
     *
     * <p>Caller must close the returned WasmModule to release the GraalWasm context.</p>
     *
     * @param resourcePath  the classpath resource path (e.g. {@code "wasm/module.wasm"})
     * @param moduleName  a friendly name for this module
     * @return a loaded and instantiated WasmModule; never null
     * @throws WasmException  if the resource is not found or instantiation fails
     */
    public WasmModule loadModuleFromClasspath(String resourcePath, String moduleName) {
        Source source = binaryCache.loadFromClasspath(resourcePath);
        return instantiate(source, moduleName);
    }

    /**
     * Loads a WASM module from a filesystem path.
     *
     * <p>Caller must close the returned WasmModule to release the GraalWasm context.</p>
     *
     * @param wasmPath  the path to a WASM file
     * @param moduleName  a friendly name for this module
     * @return a loaded and instantiated WasmModule; never null
     * @throws WasmException  if the file cannot be read or instantiation fails
     */
    public WasmModule loadModuleFromPath(Path wasmPath, String moduleName) {
        Source source = binaryCache.loadFromPath(wasmPath);
        return instantiate(source, moduleName);
    }

    /**
     * Instantiates a WASM module from a cached Source.
     *
     * @param source  the parsed WASM Source; must not be null
     * @param moduleName  a friendly name for this module; must not be null
     * @return a new WasmModule instance; never null
     * @throws WasmException  if instantiation fails
     */
    private WasmModule instantiate(Source source, String moduleName) {
        Context.Builder builder = Context.newBuilder(WASM_LANGUAGE_ID)
                .option("engine.WarnInterpreterOnly", "false");
        sandboxConfig.applyTo(builder);
        Context context = builder.build();
        context.initialize(WASM_LANGUAGE_ID);
        log.debug("WasmExecutionEngine instantiated module '{}'", moduleName);
        return new WasmModule(context, source, moduleName);
    }

    /**
     * Returns the sandbox configuration applied to all contexts created by this engine.
     *
     * @return the sandbox config; never null
     */
    public WasmSandboxConfig getSandboxConfig() {
        return sandboxConfig;
    }

    /**
     * Clears the binary cache and releases internal resources.
     *
     * <p>Idempotent: subsequent calls are no-ops. Does not affect WasmModule instances
     * already created by this engine; those must be closed separately.</p>
     */
    @Override
    public void close() {
        binaryCache.clear();
        log.debug("WasmExecutionEngine closed");
    }

    /**
     * Returns a new Builder for customising the engine.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for WasmExecutionEngine.
     */
    public static final class Builder {
        private WasmSandboxConfig sandboxConfig = WasmSandboxConfig.pureWasm();

        private Builder() {}

        /**
         * Sets the sandbox configuration.
         *
         * @param config  the configuration; must not be null
         * @return this builder
         */
        public Builder sandboxConfig(WasmSandboxConfig config) {
            this.sandboxConfig = config;
            return this;
        }

        /**
         * Builds the WasmExecutionEngine.
         *
         * @return a new WasmExecutionEngine instance
         */
        public WasmExecutionEngine build() {
            return new WasmExecutionEngine(this);
        }
    }
}
