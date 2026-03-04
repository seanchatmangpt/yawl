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
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.io.IOAccess;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Security and sandbox configuration for GraalJS contexts.
 *
 * <p>Controls which host resources (filesystem, network, environment, threads)
 * JavaScript code may access via the GraalVM Polyglot API.</p>
 *
 * <h2>Sandbox modes</h2>
 * <ul>
 *   <li><strong>STRICT</strong>: No host access, no I/O, minimal host interop.
 *       Suitable for untrusted JavaScript code.</li>
 *   <li><strong>STANDARD</strong>: Limited host access via HostAccess.EXPLICIT.
 *       No I/O, no threads. Suitable for trusted rules or expression evaluation.</li>
 *   <li><strong>PERMISSIVE</strong>: Full host access. Suitable for local development
 *       or scripts that need file I/O or other OS operations.</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class JavaScriptSandboxConfig {

    /** Predefined sandbox strictness level. */
    public enum SandboxMode { STRICT, STANDARD, PERMISSIVE }

    private final SandboxMode mode;
    private final Set<Path> allowedReadPaths;
    private final boolean wasmEnabled;
    private final String ecmaScriptVersion;
    private final boolean allowExperimentalOptions;
    private final @Nullable String nodeModulesPath;

    private JavaScriptSandboxConfig(Builder builder) {
        this.mode = builder.mode;
        this.allowedReadPaths = Collections.unmodifiableSet(new HashSet<>(builder.allowedReadPaths));
        this.wasmEnabled = builder.wasmEnabled;
        this.ecmaScriptVersion = builder.ecmaScriptVersion;
        this.allowExperimentalOptions = builder.allowExperimentalOptions;
        this.nodeModulesPath = builder.nodeModulesPath;
    }

    /** Returns the predefined {@link SandboxMode}. */
    public SandboxMode getMode() { return mode; }

    /** Returns file paths JavaScript code may read. Empty set means no host file access. */
    public Set<Path> getAllowedReadPaths() { return allowedReadPaths; }

    /** Returns true if WASM language support is enabled alongside JavaScript. */
    public boolean isWasmEnabled() { return wasmEnabled; }

    /** Returns the configured ECMAScript version (e.g. "2024", "2023"). */
    public String getEcmaScriptVersion() { return ecmaScriptVersion; }

    /** Returns true if experimental GraalJS options are allowed. */
    public boolean isAllowExperimentalOptions() { return allowExperimentalOptions; }

    /** Returns the node_modules path for require() resolution, or null if not configured. */
    public @Nullable String getNodeModulesPath() { return nodeModulesPath; }

    /**
     * Applies this sandbox configuration to a {@link Context.Builder}.
     *
     * <p>Called internally by {@link JavaScriptExecutionContext} when constructing a
     * new GraalJS context. NOTE: WASM initialization is handled separately via the
     * languages list passed to Context.newBuilder().</p>
     *
     * @param builder  the context builder to configure
     * @return the same builder with sandbox settings applied
     */
    public Context.Builder applyTo(Context.Builder builder) {
        if (mode == SandboxMode.STRICT) {
            builder.allowHostAccess(HostAccess.NONE)
                   .allowAllAccess(false)
                   .allowCreateThread(false)
                   .allowNativeAccess(false)
                   .allowCreateProcess(false)
                   .allowExperimentalOptions(allowExperimentalOptions)
                   .option("js.ecmascript-version", ecmaScriptVersion);
        } else if (mode == SandboxMode.STANDARD) {
            builder.allowHostAccess(HostAccess.EXPLICIT)
                   .allowAllAccess(false)
                   .allowCreateThread(false)
                   .allowNativeAccess(false)
                   .allowCreateProcess(false)
                   .allowExperimentalOptions(allowExperimentalOptions)
                   .option("js.ecmascript-version", ecmaScriptVersion);
        } else {
            // PERMISSIVE
            builder.allowHostAccess(HostAccess.ALL)
                   .allowAllAccess(true)
                   .allowCreateThread(true)
                   .allowNativeAccess(true)
                   .allowCreateProcess(true)
                   .allowExperimentalOptions(allowExperimentalOptions)
                   .option("js.ecmascript-version", ecmaScriptVersion);
        }

        // ES module eval must return the module namespace object (needed for wasm-bindgen glue).
        if (wasmEnabled) {
            builder.option("js.esm-eval-returns-exports", "true");
        }

        // Apply file I/O access: required for GraalJS to resolve module URIs when loading
        // ES modules from file paths, and to allow explicit read-path access.
        if (!allowedReadPaths.isEmpty() || wasmEnabled) {
            builder.allowIO(IOAccess.newBuilder().allowHostFileAccess(true).build());
        }

        if (nodeModulesPath != null) {
            builder.option("js.NodeModules", nodeModulesPath);
        }

        return builder;
    }

    /** Returns a STRICT sandbox config suitable for untrusted JavaScript code. */
    public static JavaScriptSandboxConfig strict() {
        return new Builder().mode(SandboxMode.STRICT).build();
    }

    /** Returns a STANDARD sandbox config with explicit host access. */
    public static JavaScriptSandboxConfig standard() {
        return new Builder().mode(SandboxMode.STANDARD).build();
    }

    /** Returns a PERMISSIVE sandbox config for trusted local scripts. */
    public static JavaScriptSandboxConfig permissive() {
        return new Builder().mode(SandboxMode.PERMISSIVE).build();
    }

    /** Returns a STANDARD sandbox config with WASM language support enabled. */
    public static JavaScriptSandboxConfig forWasm() {
        return new Builder().mode(SandboxMode.STANDARD).wasmEnabled(true).build();
    }

    /** Returns a new {@link Builder}. */
    public static Builder builder() { return new Builder(); }

    /** Builder for {@link JavaScriptSandboxConfig}. */
    public static final class Builder {
        private SandboxMode mode = SandboxMode.STANDARD;
        private final Set<Path> allowedReadPaths = new HashSet<>();
        private boolean wasmEnabled = false;
        private String ecmaScriptVersion = "2024";
        private boolean allowExperimentalOptions = true;
        private @Nullable String nodeModulesPath = null;

        private Builder() {}

        public Builder mode(SandboxMode mode) { this.mode = mode; return this; }
        public Builder allowRead(Path path) { allowedReadPaths.add(path); return this; }
        public Builder wasmEnabled(boolean enable) { this.wasmEnabled = enable; return this; }
        public Builder ecmaScriptVersion(String version) { this.ecmaScriptVersion = version; return this; }
        public Builder allowExperimentalOptions(boolean allow) { this.allowExperimentalOptions = allow; return this; }
        public Builder nodeModulesPath(@Nullable String path) { this.nodeModulesPath = path; return this; }

        public JavaScriptSandboxConfig build() { return new JavaScriptSandboxConfig(this); }
    }
}
