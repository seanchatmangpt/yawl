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
 * Unit tests for {@link JavaScriptExecutionContext}.
 *
 * Tests run on standard (non-GraalVM) JDK, so GraalJS is never available.
 * All construction attempts throw JavaScriptException with RUNTIME_NOT_AVAILABLE.
 */
class JavaScriptExecutionContextTest {

    @Test
    void construction_withStrictSandbox_throwsRuntimeNotAvailable() {
        JavaScriptException ex = null;
        try {
            new JavaScriptExecutionContext(JavaScriptSandboxConfig.strict());
        } catch (JavaScriptException e) {
            ex = e;
        }

        assertThat(ex, notNullValue());
        assertThat(ex.getErrorKind(), is(JavaScriptException.ErrorKind.RUNTIME_NOT_AVAILABLE));
    }

    @Test
    void construction_throwsJavaScriptException_notSomeOtherException() {
        try {
            new JavaScriptExecutionContext(JavaScriptSandboxConfig.standard());
        } catch (JavaScriptException e) {
            assertThat(e, instanceOf(JavaScriptException.class));
            return;
        }
        throw new AssertionError("Expected JavaScriptException but nothing was thrown");
    }

    @Test
    void construction_errorKindIsRuntimeNotAvailable() {
        JavaScriptException ex = null;
        try {
            new JavaScriptExecutionContext(JavaScriptSandboxConfig.standard());
        } catch (JavaScriptException e) {
            ex = e;
        }

        assertThat(ex, notNullValue());
        assertThat(ex.getErrorKind(), is(JavaScriptException.ErrorKind.RUNTIME_NOT_AVAILABLE));
    }

    @Test
    void construction_errorMessageMentionsGraalVM() {
        JavaScriptException ex = null;
        try {
            new JavaScriptExecutionContext(JavaScriptSandboxConfig.standard());
        } catch (JavaScriptException e) {
            ex = e;
        }

        assertThat(ex, notNullValue());
        assertThat(ex.getMessage(), containsStringIgnoringCase("GraalVM"));
    }

    @Test
    void construction_hasCause() {
        JavaScriptException ex = null;
        try {
            new JavaScriptExecutionContext(JavaScriptSandboxConfig.standard());
        } catch (JavaScriptException e) {
            ex = e;
        }

        assertThat(ex, notNullValue());
        assertThat(ex.getCause(), notNullValue());
    }

    @Test
    void construction_withPermissiveSandbox_alsoThrows() {
        JavaScriptException ex = null;
        try {
            new JavaScriptExecutionContext(JavaScriptSandboxConfig.permissive());
        } catch (JavaScriptException e) {
            ex = e;
        }

        assertThat(ex, notNullValue());
        assertThat(ex.getErrorKind(), is(JavaScriptException.ErrorKind.RUNTIME_NOT_AVAILABLE));
    }

    @Test
    void construction_withWasmEnabled_alsoThrows() {
        JavaScriptException ex = null;
        try {
            new JavaScriptExecutionContext(JavaScriptSandboxConfig.forWasm());
        } catch (JavaScriptException e) {
            ex = e;
        }

        assertThat(ex, notNullValue());
        assertThat(ex.getErrorKind(), is(JavaScriptException.ErrorKind.RUNTIME_NOT_AVAILABLE));
    }
}
