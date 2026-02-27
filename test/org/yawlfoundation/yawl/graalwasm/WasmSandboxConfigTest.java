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

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WasmSandboxConfigTest {

    @Test
    void pureWasm_returnsNoneWasiLevel() {
        WasmSandboxConfig config = WasmSandboxConfig.pureWasm();
        assertEquals(WasmSandboxConfig.WasiLevel.NONE, config.getWasiLevel());
    }

    @Test
    void pureWasm_allowIOIsFalse() {
        WasmSandboxConfig config = WasmSandboxConfig.pureWasm();
        assertFalse(config.isAllowIO());
    }

    @Test
    void withWasi_returnsPreview1WasiLevel() {
        WasmSandboxConfig config = WasmSandboxConfig.withWasi();
        assertEquals(WasmSandboxConfig.WasiLevel.PREVIEW1, config.getWasiLevel());
    }

    @Test
    void builder_defaultWasiLevelIsNone() {
        WasmSandboxConfig config = WasmSandboxConfig.builder().build();
        assertEquals(WasmSandboxConfig.WasiLevel.NONE, config.getWasiLevel());
    }

    @Test
    void builder_allowDirectory_addsToList() {
        WasmSandboxConfig config = WasmSandboxConfig.builder()
                .allowDirectory(Paths.get("/tmp"))
                .build();

        assertThat(config.getAllowedDirectories(), hasSize(1));
        assertEquals(Paths.get("/tmp"), config.getAllowedDirectories().get(0));
    }

    @Test
    void builder_envAddsToMap() {
        WasmSandboxConfig config = WasmSandboxConfig.builder()
                .env("TEST_VAR", "test_value")
                .build();

        assertThat(config.getEnvironmentVars().entrySet(), hasSize(1));
        assertEquals("test_value", config.getEnvironmentVars().get("TEST_VAR"));
    }

    @Test
    void builder_argAddsToList() {
        WasmSandboxConfig config = WasmSandboxConfig.builder()
                .arg("arg1")
                .arg("arg2")
                .build();

        assertThat(config.getWasmArguments(), hasSize(2));
        assertEquals("arg1", config.getWasmArguments().get(0));
        assertEquals("arg2", config.getWasmArguments().get(1));
    }

    @Test
    void builder_allowIO_setsFlag() {
        WasmSandboxConfig config = WasmSandboxConfig.builder()
                .allowIO(true)
                .build();

        assertTrue(config.isAllowIO());
    }

    @Test
    void getAllowedDirectories_returnsUnmodifiableList() {
        WasmSandboxConfig config = WasmSandboxConfig.builder()
                .allowDirectory(Paths.get("/tmp"))
                .build();

        List<java.nio.file.Path> dirs = config.getAllowedDirectories();
        assertThrows(UnsupportedOperationException.class, () -> dirs.add(Paths.get("/etc")));
    }

    @Test
    void getEnvironmentVars_returnsUnmodifiableMap() {
        WasmSandboxConfig config = WasmSandboxConfig.builder()
                .env("KEY", "VALUE")
                .build();

        Map<String, String> vars = config.getEnvironmentVars();
        assertThrows(UnsupportedOperationException.class, () -> vars.put("NEW_KEY", "NEW_VALUE"));
    }

    @Test
    void builder_chainable() {
        WasmSandboxConfig config = WasmSandboxConfig.builder()
                .wasiLevel(WasmSandboxConfig.WasiLevel.PREVIEW1)
                .allowDirectory(Paths.get("/tmp"))
                .env("VAR1", "val1")
                .env("VAR2", "val2")
                .arg("--arg1")
                .allowIO(true)
                .build();

        assertEquals(WasmSandboxConfig.WasiLevel.PREVIEW1, config.getWasiLevel());
        assertThat(config.getAllowedDirectories(), hasSize(1));
        assertThat(config.getEnvironmentVars().entrySet(), hasSize(2));
        assertThat(config.getWasmArguments(), hasSize(1));
        assertTrue(config.isAllowIO());
    }
}
