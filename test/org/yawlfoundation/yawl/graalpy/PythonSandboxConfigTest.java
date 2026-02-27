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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD unit tests for {@link PythonSandboxConfig}.
 *
 * <p>Verifies sandbox configuration construction and properties without
 * requiring GraalPy at runtime.</p>
 */
@DisplayName("PythonSandboxConfig")
class PythonSandboxConfigTest {

    // ── Factory methods ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("strict() returns STRICT mode with Java POSIX backend")
    void strictFactoryReturnsCorrectConfig() {
        PythonSandboxConfig config = PythonSandboxConfig.strict();

        assertThat(config.getMode(), is(PythonSandboxConfig.SandboxMode.STRICT));
        assertTrue(config.isPosixJavaBackend());
        assertFalse(config.isAllowNativeExtensions());
        assertFalse(config.isAllowEnvironmentAccess());
        assertThat(config.getAllowedReadPaths(), empty());
    }

    @Test
    @DisplayName("standard() returns STANDARD mode with Java POSIX backend")
    void standardFactoryReturnsCorrectConfig() {
        PythonSandboxConfig config = PythonSandboxConfig.standard();

        assertThat(config.getMode(), is(PythonSandboxConfig.SandboxMode.STANDARD));
        assertTrue(config.isPosixJavaBackend());
        assertFalse(config.isAllowNativeExtensions());
    }

    @Test
    @DisplayName("permissive() returns PERMISSIVE mode without POSIX emulation")
    void permissiveFactoryReturnsCorrectConfig() {
        PythonSandboxConfig config = PythonSandboxConfig.permissive();

        assertThat(config.getMode(), is(PythonSandboxConfig.SandboxMode.PERMISSIVE));
        assertFalse(config.isPosixJavaBackend());
    }

    // ── Builder tests ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("builder allows configuring allowed read paths")
    void builderAllowsReadPaths() {
        Path allowedPath = Path.of("/data/models");
        PythonSandboxConfig config = PythonSandboxConfig.builder()
                .mode(PythonSandboxConfig.SandboxMode.STANDARD)
                .allowRead(allowedPath)
                .build();

        assertThat(config.getAllowedReadPaths(), contains(allowedPath));
    }

    @Test
    @DisplayName("builder allows configuring allowed write paths")
    void builderAllowsWritePaths() {
        Path writePath = Path.of("/tmp/output");
        PythonSandboxConfig config = PythonSandboxConfig.builder()
                .mode(PythonSandboxConfig.SandboxMode.STANDARD)
                .allowWrite(writePath)
                .build();

        assertThat(config.getAllowedWritePaths(), contains(writePath));
    }

    @Test
    @DisplayName("builder enables native extensions when configured")
    void builderEnablesNativeExtensions() {
        PythonSandboxConfig config = PythonSandboxConfig.builder()
                .mode(PythonSandboxConfig.SandboxMode.STANDARD)
                .allowNativeExtensions(true)
                .build();

        assertTrue(config.isAllowNativeExtensions());
    }

    @Test
    @DisplayName("builder enables environment access when configured")
    void builderEnablesEnvironmentAccess() {
        PythonSandboxConfig config = PythonSandboxConfig.builder()
                .mode(PythonSandboxConfig.SandboxMode.STANDARD)
                .allowEnvironmentAccess(true)
                .build();

        assertTrue(config.isAllowEnvironmentAccess());
    }

    @Test
    @DisplayName("builder disables POSIX Java backend when configured")
    void builderDisablesPosixBackend() {
        PythonSandboxConfig config = PythonSandboxConfig.builder()
                .mode(PythonSandboxConfig.SandboxMode.PERMISSIVE)
                .posixJavaBackend(false)
                .build();

        assertFalse(config.isPosixJavaBackend());
    }

    @Test
    @DisplayName("getAllowedReadPaths returns unmodifiable set")
    void allowedReadPathsIsUnmodifiable() {
        PythonSandboxConfig config = PythonSandboxConfig.strict();

        assertThrows(UnsupportedOperationException.class,
                () -> config.getAllowedReadPaths().add(Path.of("/hack")));
    }

    @Test
    @DisplayName("getAllowedWritePaths returns unmodifiable set")
    void allowedWritePathsIsUnmodifiable() {
        PythonSandboxConfig config = PythonSandboxConfig.strict();

        assertThrows(UnsupportedOperationException.class,
                () -> config.getAllowedWritePaths().add(Path.of("/hack")));
    }
}
