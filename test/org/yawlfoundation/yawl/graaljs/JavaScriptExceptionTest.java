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
 * Unit tests for {@link JavaScriptException}.
 */
class JavaScriptExceptionTest {

    @Test
    void errorKind_hasExactlySevenValues() {
        JavaScriptException.ErrorKind[] values = JavaScriptException.ErrorKind.values();
        assertThat(values, arrayWithSize(7));
    }

    @Test
    void errorKind_containsAllExpectedValues() {
        assertThat(JavaScriptException.ErrorKind.RUNTIME_NOT_AVAILABLE, notNullValue());
        assertThat(JavaScriptException.ErrorKind.SYNTAX_ERROR, notNullValue());
        assertThat(JavaScriptException.ErrorKind.RUNTIME_ERROR, notNullValue());
        assertThat(JavaScriptException.ErrorKind.TYPE_CONVERSION_ERROR, notNullValue());
        assertThat(JavaScriptException.ErrorKind.SANDBOX_VIOLATION, notNullValue());
        assertThat(JavaScriptException.ErrorKind.CONTEXT_ERROR, notNullValue());
        assertThat(JavaScriptException.ErrorKind.MODULE_LOAD_ERROR, notNullValue());
    }

    @Test
    void twoArgConstructor_storesMessageAndKind() {
        JavaScriptException ex = new JavaScriptException(
                "test message",
                JavaScriptException.ErrorKind.RUNTIME_ERROR);

        assertThat(ex.getMessage(), is("test message"));
        assertThat(ex.getErrorKind(), is(JavaScriptException.ErrorKind.RUNTIME_ERROR));
    }

    @Test
    void twoArgConstructor_causeIsNull() {
        JavaScriptException ex = new JavaScriptException(
                "test message",
                JavaScriptException.ErrorKind.RUNTIME_ERROR);

        assertThat(ex.getCause(), nullValue());
    }

    @Test
    void threeArgConstructor_chainsCause() {
        Throwable cause = new IllegalStateException("underlying cause");
        JavaScriptException ex = new JavaScriptException(
                "wrapper message",
                JavaScriptException.ErrorKind.CONTEXT_ERROR,
                cause);

        assertThat(ex.getMessage(), is("wrapper message"));
        assertThat(ex.getErrorKind(), is(JavaScriptException.ErrorKind.CONTEXT_ERROR));
        assertThat(ex.getCause(), sameInstance(cause));
    }

    @Test
    void getErrorKind_returnsConfiguredKind() {
        JavaScriptException ex = new JavaScriptException(
                "msg",
                JavaScriptException.ErrorKind.SYNTAX_ERROR);

        assertThat(ex.getErrorKind(), is(JavaScriptException.ErrorKind.SYNTAX_ERROR));
    }

    @Test
    void toString_containsKindAndMessage() {
        JavaScriptException ex = new JavaScriptException(
                "test message",
                JavaScriptException.ErrorKind.RUNTIME_ERROR);

        String str = ex.toString();
        assertThat(str, containsString("JavaScriptException"));
        assertThat(str, containsString("RUNTIME_ERROR"));
        assertThat(str, containsString("test message"));
    }

    @Test
    void isRuntimeException() {
        JavaScriptException ex = new JavaScriptException(
                "test",
                JavaScriptException.ErrorKind.RUNTIME_ERROR);

        assertThat(ex, instanceOf(RuntimeException.class));
    }
}
