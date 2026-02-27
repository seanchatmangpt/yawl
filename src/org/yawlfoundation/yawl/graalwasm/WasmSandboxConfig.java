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
import org.graalvm.polyglot.io.IOAccess;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for WASM sandboxing in GraalWasm.
 *
 * <p>Controls WASI support level, I/O access, and environment configuration
 * for WASM module execution.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class WasmSandboxConfig {

    /** WASI (WebAssembly System Interface) support level. */
    public enum WasiLevel {
        /** No WASI system calls available; pure computational WASM only. */
        NONE,
        /** WASI snapshot preview 1 support enabled. */
        PREVIEW1
    }

    private final WasiLevel wasiLevel;
    private final List<Path> allowedDirectories;
    private final Map<String, String> environmentVars;
    private final List<String> wasmArguments;
    private final boolean allowIO;

    private WasmSandboxConfig(Builder builder) {
        this.wasiLevel = builder.wasiLevel;
        this.allowedDirectories = new ArrayList<>(builder.allowedDirectories);
        this.environmentVars = new LinkedHashMap<>(builder.environmentVars);
        this.wasmArguments = new ArrayList<>(builder.wasmArguments);
        this.allowIO = builder.allowIO;
    }

    /**
     * Applies WASM/WASI configuration to a Context.Builder.
     *
     * <p>Sets WASI builtins option if applicable, and configures I/O access policy.</p>
     *
     * @param builder  the Context.Builder to configure; must not be null
     * @return the modified builder for method chaining
     */
    public Context.Builder applyTo(Context.Builder builder) {
        if (wasiLevel == WasiLevel.PREVIEW1) {
            builder.option("wasm.Builtins", "wasi_snapshot_preview1");
        }
        builder.allowIO(allowIO ? IOAccess.ALL : IOAccess.NONE);
        builder.allowAllAccess(false);
        return builder;
    }

    /**
     * Returns a config suitable for pure computational WASM with no I/O.
     *
     * @return a WasmSandboxConfig with WASI disabled and I/O disabled
     */
    public static WasmSandboxConfig pureWasm() {
        return new Builder()
                .wasiLevel(WasiLevel.NONE)
                .allowIO(false)
                .build();
    }

    /**
     * Returns a config suitable for WASI preview 1 support.
     *
     * @return a WasmSandboxConfig with WASI enabled and I/O disabled
     */
    public static WasmSandboxConfig withWasi() {
        return new Builder()
                .wasiLevel(WasiLevel.PREVIEW1)
                .allowIO(false)
                .build();
    }

    /**
     * Returns a new Builder for customising WASM sandbox configuration.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    public WasiLevel getWasiLevel() {
        return wasiLevel;
    }

    public List<Path> getAllowedDirectories() {
        return Collections.unmodifiableList(allowedDirectories);
    }

    public Map<String, String> getEnvironmentVars() {
        return Collections.unmodifiableMap(environmentVars);
    }

    public List<String> getWasmArguments() {
        return Collections.unmodifiableList(wasmArguments);
    }

    public boolean isAllowIO() {
        return allowIO;
    }

    /**
     * Builder for WasmSandboxConfig.
     */
    public static final class Builder {
        private WasiLevel wasiLevel = WasiLevel.NONE;
        private final List<Path> allowedDirectories = new ArrayList<>();
        private final Map<String, String> environmentVars = new LinkedHashMap<>();
        private final List<String> wasmArguments = new ArrayList<>();
        private boolean allowIO = false;

        private Builder() {}

        /**
         * Sets the WASI support level.
         *
         * @param level  the WASI level; must not be null
         * @return this builder
         */
        public Builder wasiLevel(WasiLevel level) {
            this.wasiLevel = level;
            return this;
        }

        /**
         * Adds a directory that WASI can access.
         *
         * @param dir  the directory path; must not be null
         * @return this builder
         */
        public Builder allowDirectory(Path dir) {
            this.allowedDirectories.add(dir);
            return this;
        }

        /**
         * Sets a WASI environment variable.
         *
         * @param key  the variable name; must not be null
         * @param value  the variable value; must not be null
         * @return this builder
         */
        public Builder env(String key, String value) {
            this.environmentVars.put(key, value);
            return this;
        }

        /**
         * Adds a WASI argument.
         *
         * @param arg  the argument; must not be null
         * @return this builder
         */
        public Builder arg(String arg) {
            this.wasmArguments.add(arg);
            return this;
        }

        /**
         * Enables or disables I/O access.
         *
         * @param allow  {@code true} to allow I/O, {@code false} to disable
         * @return this builder
         */
        public Builder allowIO(boolean allow) {
            this.allowIO = allow;
            return this;
        }

        /**
         * Builds the WasmSandboxConfig.
         *
         * @return a new WasmSandboxConfig instance
         */
        public WasmSandboxConfig build() {
            return new WasmSandboxConfig(this);
        }
    }
}
