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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WasmExecutionEngineTest {

    @Test
    void builder_defaultSucceeds() {
        assertDoesNotThrow(() -> WasmExecutionEngine.builder().build());
    }

    @Test
    void builder_withSandboxConfig_succeeds() {
        WasmSandboxConfig config = WasmSandboxConfig.withWasi();
        WasmExecutionEngine engine = assertDoesNotThrow(() ->
                WasmExecutionEngine.builder().sandboxConfig(config).build());
        assertThat(engine, notNullValue());
    }

    @Test
    void getSandboxConfig_returnsConfiguredConfig() {
        WasmSandboxConfig config = WasmSandboxConfig.pureWasm();
        WasmExecutionEngine engine = WasmExecutionEngine.builder()
                .sandboxConfig(config)
                .build();

        assertThat(engine.getSandboxConfig(), equalTo(config));
        engine.close();
    }

    @Test
    void loadModuleFromClasspath_nonExistentResource_throwsModuleLoadError() {
        WasmExecutionEngine engine = WasmExecutionEngine.builder().build();

        assertThrows(WasmException.class,
                () -> engine.loadModuleFromClasspath("missing/module.wasm", "test"));

        engine.close();
    }

    @Test
    void loadModuleFromPath_nonExistentPath_throwsModuleLoadError(@TempDir Path tempDir) {
        WasmExecutionEngine engine = WasmExecutionEngine.builder().build();
        Path nonExistent = tempDir.resolve("missing.wasm");

        assertThrows(WasmException.class,
                () -> engine.loadModuleFromPath(nonExistent, "test"));

        engine.close();
    }

    @Test
    void loadModuleFromClasspath_throwsWasmException() {
        WasmExecutionEngine engine = WasmExecutionEngine.builder().build();

        WasmException ex = assertThrows(WasmException.class,
                () -> engine.loadModuleFromClasspath("nonexistent/path.wasm", "module"));

        assertThat(ex.getErrorKind(), equalTo(WasmException.ErrorKind.MODULE_LOAD_ERROR));
        engine.close();
    }

    @Test
    void close_doesNotThrow() {
        WasmExecutionEngine engine = WasmExecutionEngine.builder().build();
        assertDoesNotThrow(engine::close);
    }

    @Test
    void close_isIdempotent() {
        WasmExecutionEngine engine = WasmExecutionEngine.builder().build();
        assertDoesNotThrow(() -> {
            engine.close();
            engine.close();
        });
    }
}
