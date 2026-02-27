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

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * Unit tests for {@link JavaScriptSandboxConfig}.
 */
class JavaScriptSandboxConfigTest {

    @Test
    void strict_returnsModeStrict() {
        JavaScriptSandboxConfig config = JavaScriptSandboxConfig.strict();
        assertThat(config.getMode(), is(JavaScriptSandboxConfig.SandboxMode.STRICT));
    }

    @Test
    void strict_wasmDisabled() {
        JavaScriptSandboxConfig config = JavaScriptSandboxConfig.strict();
        assertThat(config.isWasmEnabled(), is(false));
    }

    @Test
    void standard_returnsModeStandard() {
        JavaScriptSandboxConfig config = JavaScriptSandboxConfig.standard();
        assertThat(config.getMode(), is(JavaScriptSandboxConfig.SandboxMode.STANDARD));
    }

    @Test
    void standard_wasmDisabled() {
        JavaScriptSandboxConfig config = JavaScriptSandboxConfig.standard();
        assertThat(config.isWasmEnabled(), is(false));
    }

    @Test
    void permissive_returnsModePermissive() {
        JavaScriptSandboxConfig config = JavaScriptSandboxConfig.permissive();
        assertThat(config.getMode(), is(JavaScriptSandboxConfig.SandboxMode.PERMISSIVE));
    }

    @Test
    void forWasm_returnsModeStandard() {
        JavaScriptSandboxConfig config = JavaScriptSandboxConfig.forWasm();
        assertThat(config.getMode(), is(JavaScriptSandboxConfig.SandboxMode.STANDARD));
    }

    @Test
    void forWasm_wasmEnabled() {
        JavaScriptSandboxConfig config = JavaScriptSandboxConfig.forWasm();
        assertThat(config.isWasmEnabled(), is(true));
    }

    @Test
    void builder_defaultIsStandard() {
        JavaScriptSandboxConfig config = JavaScriptSandboxConfig.builder().build();
        assertThat(config.getMode(), is(JavaScriptSandboxConfig.SandboxMode.STANDARD));
    }

    @Test
    void builder_setWasmEnabled() {
        JavaScriptSandboxConfig config = JavaScriptSandboxConfig.builder()
                .wasmEnabled(true)
                .build();
        assertThat(config.isWasmEnabled(), is(true));
    }

    @Test
    void builder_setEcmaScriptVersion() {
        JavaScriptSandboxConfig config = JavaScriptSandboxConfig.builder()
                .ecmaScriptVersion("2023")
                .build();
        assertThat(config.getEcmaScriptVersion(), is("2023"));
    }

    @Test
    void builder_defaultEcmaScriptVersion() {
        JavaScriptSandboxConfig config = JavaScriptSandboxConfig.builder().build();
        assertThat(config.getEcmaScriptVersion(), is("2024"));
    }

    @Test
    void builder_allowExperimentalOptions() {
        JavaScriptSandboxConfig config = JavaScriptSandboxConfig.builder()
                .allowExperimentalOptions(false)
                .build();
        assertThat(config.isAllowExperimentalOptions(), is(false));
    }

    @Test
    void builder_defaultAllowExperimentalOptions() {
        JavaScriptSandboxConfig config = JavaScriptSandboxConfig.builder().build();
        assertThat(config.isAllowExperimentalOptions(), is(true));
    }

    @Test
    void builder_allowedReadPathsUnmodifiable() {
        JavaScriptSandboxConfig config = JavaScriptSandboxConfig.builder().build();
        assertThat(config.getAllowedReadPaths(), notNullValue());
        assertThat(config.getAllowedReadPaths(), empty());
    }

    @Test
    void builder_nodeModulesPath() {
        JavaScriptSandboxConfig config = JavaScriptSandboxConfig.builder()
                .nodeModulesPath("/path/to/node_modules")
                .build();
        assertThat(config.getNodeModulesPath(), is("/path/to/node_modules"));
    }

    @Test
    void builder_defaultNodeModulesPathIsNull() {
        JavaScriptSandboxConfig config = JavaScriptSandboxConfig.builder().build();
        assertThat(config.getNodeModulesPath(), nullValue());
    }
}
