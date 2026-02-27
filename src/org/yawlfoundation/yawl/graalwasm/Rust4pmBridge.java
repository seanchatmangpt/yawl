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

import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.graaljs.JavaScriptExecutionContext;
import org.yawlfoundation.yawl.graaljs.JavaScriptExecutionEngine;
import org.yawlfoundation.yawl.graaljs.JavaScriptSandboxConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;

/**
 * Bridge to the @aarkue/process_mining_wasm Rust-compiled WebAssembly module.
 *
 * <p>Exposes OCEL2 (Object-Centric Event Log 2.0) process mining functions
 * from the rust4pm library, compiled to WebAssembly via wasm-bindgen.</p>
 *
 * <h2>Architecture</h2>
 * <pre>
 * Rust4pmBridge
 *   └─ JavaScriptExecutionEngine (from yawl-graaljs, JS+WASM polyglot)
 *        └─ JavaScriptExecutionContext (Context.newBuilder("js","wasm"))
 *             └─ process_mining_wasm.js (ES module, wasm-bindgen glue)
 *                  └─ process_mining_wasm_bg.wasm (Rust logic)
 * </pre>
 *
 * <h2>Bundled resources</h2>
 * <ul>
 *   <li>{@code wasm/process_mining_wasm_bg.wasm} — Rust-compiled WASM binary</li>
 *   <li>{@code wasm/process_mining_wasm.js} — ES module wasm-bindgen glue</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 * <p>Thread-safe via JavaScriptContextPool.</p>
 *
 * <h2>Quick start</h2>
 * <pre>{@code
 * try (Rust4pmBridge bridge = new Rust4pmBridge()) {
 *     String json = bridge.parseOcel2XmlToJsonString(ocel2XmlContent);
 * }
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class Rust4pmBridge implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(Rust4pmBridge.class);

    static final String WASM_RESOURCE = "wasm/process_mining_wasm_bg.wasm";
    static final String GLUE_RESOURCE = "wasm/process_mining_wasm.js";

    private final JavaScriptExecutionEngine jsEngine;
    private final Path tempDir;

    /**
     * Constructs and initialises the bridge with a custom context pool size.
     *
     * <p>Extracts WASM and JS glue to a temp directory, then loads the glue
     * into all pool contexts via initSync.</p>
     *
     * @param poolSize  number of concurrent JS+WASM contexts (1 for most use cases)
     * @throws WasmException  MODULE_LOAD_ERROR if classpath resources are not found
     * @throws IllegalArgumentException  if poolSize < 1
     */
    public Rust4pmBridge(int poolSize) {
        if (poolSize < 1) {
            throw new IllegalArgumentException("poolSize must be at least 1, got: " + poolSize);
        }
        this.tempDir = extractResourcesToTemp();
        Path wasmPath = tempDir.resolve("process_mining_wasm_bg.wasm");
        Path gluePath = tempDir.resolve("process_mining_wasm.js");

        // Build a custom JS sandbox that:
        //  1. enables WASM language
        //  2. allows reading from tempDir (for ES module WASM file reference)
        //  3. enables ES module evaluation with exports returned
        JavaScriptSandboxConfig sandboxConfig = JavaScriptSandboxConfig.builder()
                .mode(JavaScriptSandboxConfig.SandboxMode.STANDARD)
                .wasmEnabled(true)
                .allowExperimentalOptions(true)
                .ecmaScriptVersion("2024")
                .allowRead(tempDir)
                .build();

        this.jsEngine = JavaScriptExecutionEngine.builder()
                .sandboxConfig(sandboxConfig)
                .contextPoolSize(poolSize)
                .build();

        // In each context: load the JS glue as ES module, get initSync, init the WASM
        Path finalGluePath = gluePath;
        Path finalWasmPath = wasmPath;
        jsEngine.getContextPool().executeVoid(ctx -> initWasmInContext(ctx, finalGluePath, finalWasmPath));

        log.info("Rust4pmBridge initialised: poolSize={}", poolSize);
    }

    /**
     * Convenience constructor with poolSize=1.
     *
     * @throws WasmException  if resource extraction or initialisation fails
     */
    public Rust4pmBridge() {
        this(1);
    }

    // ── Public API: OCEL2 process mining ─────────────────────────────────────

    /**
     * Parses an OCEL2 XML event log and returns a JSON string representation.
     *
     * @param ocel2Xml  OCEL2-formatted XML string; must not be null
     * @return JSON string representing the parsed OCEL2 log; never null
     * @throws WasmException  EXECUTION_ERROR if the WASM function traps
     */
    public String parseOcel2XmlToJsonString(String ocel2Xml) {
        return jsEngine.getContextPool().execute(ctx -> {
            ctx.getRawContext().getBindings("js").putMember("__ocel_input__", ocel2Xml);
            try {
                Value result = ctx.getRawContext().eval("js", "wasm_parse_ocel2_xml_to_json_str(__ocel_input__)");
                return result.asString();
            } finally {
                ctx.getRawContext().getBindings("js").removeMember("__ocel_input__");
            }
        });
    }

    /**
     * Parses an OCEL2 JSON event log and returns the parsed result.
     *
     * @param ocel2Json  OCEL2-formatted JSON string; must not be null
     * @return JSON string representing the parsed result; never null
     * @throws WasmException  EXECUTION_ERROR if the WASM function traps
     */
    public String parseOcel2Json(String ocel2Json) {
        return jsEngine.getContextPool().execute(ctx -> {
            ctx.getRawContext().getBindings("js").putMember("__ocel_input__", ocel2Json);
            try {
                Value result = ctx.getRawContext().eval("js", "wasm_parse_ocel2_json(__ocel_input__)");
                return result.toString();
            } finally {
                ctx.getRawContext().getBindings("js").removeMember("__ocel_input__");
            }
        });
    }

    /**
     * Parses an OCEL2 XML event log, keeps state in WASM memory, and returns
     * a pointer that can be used with getNumEventsFromPointer().
     *
     * <p>The returned pointer must be freed with destroyPointer() to prevent
     * WASM memory leaks.</p>
     *
     * @param ocel2Xml  OCEL2-formatted XML string; must not be null
     * @return WASM memory pointer (long) for subsequent operations; never null
     * @throws WasmException  EXECUTION_ERROR if the WASM function traps
     */
    public long parseOcel2XmlKeepInWasm(String ocel2Xml) {
        return jsEngine.getContextPool().execute(ctx -> {
            ctx.getRawContext().getBindings("js").putMember("__ocel_input__", ocel2Xml);
            try {
                Value result = ctx.getRawContext().eval("js", "wasm_parse_ocel2_xml_keep_state_in_wasm(__ocel_input__)");
                return result.asLong();
            } finally {
                ctx.getRawContext().getBindings("js").removeMember("__ocel_input__");
            }
        });
    }

    /**
     * Returns the number of events from a WASM-held OCEL2 pointer.
     *
     * @param pointer  WASM memory pointer from parseOcel2XmlKeepInWasm(); must not be 0
     * @return number of events in the log
     * @throws WasmException  EXECUTION_ERROR if the WASM function traps
     */
    public long getNumEventsFromPointer(long pointer) {
        return jsEngine.getContextPool().execute(ctx -> {
            Value result = ctx.getRawContext().eval("js",
                    "wasm_get_ocel_num_events_from_pointer(" + pointer + ")");
            return result.asLong();
        });
    }

    /**
     * Frees a WASM memory pointer obtained from parseOcel2XmlKeepInWasm().
     *
     * <p>Must be called to prevent WASM memory leaks.</p>
     *
     * @param pointer  WASM memory pointer to free; must not be 0
     * @throws WasmException  EXECUTION_ERROR if the WASM function traps
     */
    public void destroyPointer(long pointer) {
        jsEngine.getContextPool().executeVoid(ctx ->
                ctx.getRawContext().eval("js", "wasm_destroy_ocel_pointer(" + pointer + ")")
        );
    }

    /**
     * Closes the bridge, releasing all JS contexts and temp directory resources.
     *
     * <p>Idempotent: subsequent calls are no-ops.</p>
     */
    @Override
    public void close() {
        jsEngine.close();
        deleteTempDir(tempDir);
        log.info("Rust4pmBridge closed");
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    /**
     * Initialises WASM in a single JavaScript context.
     *
     * @param ctx  the JavaScript context; must not be null
     * @param gluePath  the path to process_mining_wasm.js; must not be null
     * @param wasmPath  the path to process_mining_wasm_bg.wasm; must not be null
     * @throws WasmException  if initialisation fails
     */
    private void initWasmInContext(JavaScriptExecutionContext ctx, Path gluePath, Path wasmPath) {
        try {
            // Load JS glue as ES module — returns module exports namespace
            Source glueSource = Source.newBuilder("js", gluePath.toFile())
                    .mimeType("application/javascript+module")
                    .build();
            Value moduleExports = ctx.getRawContext().eval(glueSource);

            // Get initSync function from module exports
            Value initSync = moduleExports.getMember("initSync");
            if (initSync == null || initSync.isNull()) {
                throw new WasmException(
                        "process_mining_wasm.js does not export 'initSync'. "
                        + "Expected wasm-bindgen ES module format.",
                        WasmException.ErrorKind.INSTANTIATION_ERROR);
            }

            // Load WASM bytes and initialize synchronously
            byte[] wasmBytes = Files.readAllBytes(wasmPath);
            // Pass bytes to initSync as a typed array via JS evaluation
            ctx.getRawContext().getBindings("js").putMember("__wasmBytes__", wasmBytes);
            ctx.getRawContext().eval("js",
                    "const __wasmMod__ = new WebAssembly.Module(__wasmBytes__); "
                    + "initSync(__wasmMod__); "
                    + "delete __wasmBytes__; delete __wasmMod__;");

        } catch (IOException e) {
            throw new WasmException(
                    "Failed to read WASM file from temp directory: " + e.getMessage(),
                    WasmException.ErrorKind.MODULE_LOAD_ERROR, e);
        }
    }

    /**
     * Extracts WASM and JS glue resources to a temp directory.
     *
     * @return the temp directory path; never null
     * @throws WasmException  MODULE_LOAD_ERROR if resources are not found or IO fails
     */
    private Path extractResourcesToTemp() {
        try {
            Path dir = Files.createTempDirectory("yawl-rust4pm-");
            extractResource(WASM_RESOURCE, dir.resolve("process_mining_wasm_bg.wasm"));
            extractResource(GLUE_RESOURCE, dir.resolve("process_mining_wasm.js"));
            return dir;
        } catch (IOException e) {
            throw new WasmException(
                    "Cannot create temp directory for WASM resources: " + e.getMessage(),
                    WasmException.ErrorKind.MODULE_LOAD_ERROR, e);
        }
    }

    /**
     * Extracts a single classpath resource to a target file.
     *
     * @param resourcePath  the classpath resource path; must not be null
     * @param targetPath  the target file path; must not be null
     * @throws IOException  if IO fails
     * @throws WasmException  MODULE_LOAD_ERROR if resource is not found
     */
    private void extractResource(String resourcePath, Path targetPath) throws IOException {
        try (InputStream is = Rust4pmBridge.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new WasmException(
                        "Required classpath resource not found: " + resourcePath
                        + ". Ensure yawl-graalwasm JAR contains wasm/ resources.",
                        WasmException.ErrorKind.MODULE_LOAD_ERROR);
            }
            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Recursively deletes a temp directory and its contents.
     *
     * @param dir  the directory to delete; must not be null
     */
    private void deleteTempDir(Path dir) {
        try {
            Files.walk(dir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException ignored) {
                            // Best effort; some temp dirs may be in use
                        }
                    });
        } catch (IOException e) {
            log.warn("Cannot clean up WASM temp directory {}: {}", dir, e.getMessage());
        }
    }
}
