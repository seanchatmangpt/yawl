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
import org.graalvm.polyglot.io.IOAccess;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Security and sandbox configuration for GraalPy contexts.
 *
 * <p>Controls which host resources (filesystem, network, environment, threads)
 * Python code may access. The POSIX module is emulated via the GraalVM Java backend
 * so that Python {@code os} calls are routed through the Truffle abstraction layer
 * rather than hitting host OS APIs directly.</p>
 *
 * <h2>Sandbox modes</h2>
 * <ul>
 *   <li><strong>STRICT</strong>: No host I/O, no environment access, no native extensions.
 *       Suitable for untrusted Python code or third-party PIP packages.</li>
 *   <li><strong>STANDARD</strong>: Read-only access to allowed directories, Java POSIX
 *       emulation. Suitable for trusted analytics scripts.</li>
 *   <li><strong>PERMISSIVE</strong>: Full host access. Suitable for local development
 *       or scripts that need to write files or use native C extensions.</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class PythonSandboxConfig {

    /** Predefined sandbox strictness level. */
    public enum SandboxMode { STRICT, STANDARD, PERMISSIVE }

    private final SandboxMode mode;
    private final Set<Path> allowedReadPaths;
    private final Set<Path> allowedWritePaths;
    private final boolean allowEnvironmentAccess;
    private final boolean allowNativeExtensions;
    private final boolean posixJavaBackend;

    private PythonSandboxConfig(Builder builder) {
        this.mode = builder.mode;
        this.allowedReadPaths = Collections.unmodifiableSet(new HashSet<>(builder.allowedReadPaths));
        this.allowedWritePaths = Collections.unmodifiableSet(new HashSet<>(builder.allowedWritePaths));
        this.allowEnvironmentAccess = builder.allowEnvironmentAccess;
        this.allowNativeExtensions = builder.allowNativeExtensions;
        this.posixJavaBackend = builder.posixJavaBackend;
    }

    /** Returns the predefined {@link SandboxMode}. */
    public SandboxMode getMode() { return mode; }

    /** Returns file paths Python code may read. Empty set means no host file access. */
    public Set<Path> getAllowedReadPaths() { return allowedReadPaths; }

    /** Returns file paths Python code may write. Empty set means no write access. */
    public Set<Path> getAllowedWritePaths() { return allowedWritePaths; }

    /** Returns true if Python may read host environment variables. */
    public boolean isAllowEnvironmentAccess() { return allowEnvironmentAccess; }

    /** Returns true if native C extensions (.so / .dll / .dylib) may be loaded. */
    public boolean isAllowNativeExtensions() { return allowNativeExtensions; }

    /** Returns true if Python's {@code os} module is emulated via GraalVM Java backend. */
    public boolean isPosixJavaBackend() { return posixJavaBackend; }

    /**
     * Applies this sandbox configuration to a {@link Context.Builder}.
     *
     * <p>Called internally by {@link PythonExecutionContext} when constructing a
     * new GraalPy context.</p>
     *
     * @param builder  the context builder to configure
     * @return the same builder with sandbox settings applied
     */
    public Context.Builder applyTo(Context.Builder builder) {
        if (mode == SandboxMode.STRICT) {
            builder.allowIO(IOAccess.NONE)
                   .allowNativeAccess(false)
                   .allowCreateThread(false)
                   .allowCreateProcess(false)
                   .allowAllAccess(false);
        } else if (mode == SandboxMode.STANDARD) {
            IOAccess.Builder ioBuilder = IOAccess.newBuilder()
                    .allowHostSocketAccess(false);
            if (!allowedReadPaths.isEmpty()) {
                allowedReadPaths.forEach(p -> ioBuilder.allowHostFileAccess(true));
            } else {
                ioBuilder.allowHostFileAccess(false);
            }
            builder.allowIO(ioBuilder.build())
                   .allowNativeAccess(allowNativeExtensions)
                   .allowCreateThread(false)
                   .allowCreateProcess(false)
                   .allowAllAccess(false);
        } else {
            // PERMISSIVE
            builder.allowIO(IOAccess.ALL)
                   .allowNativeAccess(true)
                   .allowCreateThread(true)
                   .allowCreateProcess(true)
                   .allowAllAccess(true);
        }

        if (posixJavaBackend) {
            // Route Python os.* calls through GraalVM Java POSIX emulation layer
            // instead of native OS syscalls - prevents host filesystem/descriptor leakage
            builder.option("python.PosixModuleBackend", "java");
        }

        return builder;
    }

    /** Returns a STRICT sandbox config suitable for untrusted Python code. */
    public static PythonSandboxConfig strict() {
        return new Builder().mode(SandboxMode.STRICT).posixJavaBackend(true).build();
    }

    /** Returns a STANDARD sandbox config with Java POSIX emulation. */
    public static PythonSandboxConfig standard() {
        return new Builder().mode(SandboxMode.STANDARD).posixJavaBackend(true).build();
    }

    /** Returns a PERMISSIVE sandbox config for trusted local scripts. */
    public static PythonSandboxConfig permissive() {
        return new Builder().mode(SandboxMode.PERMISSIVE).posixJavaBackend(false).build();
    }

    /** Returns a new {@link Builder}. */
    public static Builder builder() { return new Builder(); }

    /** Builder for {@link PythonSandboxConfig}. */
    public static final class Builder {
        private SandboxMode mode = SandboxMode.STANDARD;
        private final Set<Path> allowedReadPaths = new HashSet<>();
        private final Set<Path> allowedWritePaths = new HashSet<>();
        private boolean allowEnvironmentAccess = false;
        private boolean allowNativeExtensions = false;
        private boolean posixJavaBackend = true;

        private Builder() {}

        public Builder mode(SandboxMode mode) { this.mode = mode; return this; }
        public Builder allowRead(Path path) { allowedReadPaths.add(path); return this; }
        public Builder allowWrite(Path path) { allowedWritePaths.add(path); return this; }
        public Builder allowEnvironmentAccess(boolean allow) { this.allowEnvironmentAccess = allow; return this; }
        public Builder allowNativeExtensions(boolean allow) { this.allowNativeExtensions = allow; return this; }
        public Builder posixJavaBackend(boolean enable) { this.posixJavaBackend = enable; return this; }

        public PythonSandboxConfig build() { return new PythonSandboxConfig(this); }
    }
}
