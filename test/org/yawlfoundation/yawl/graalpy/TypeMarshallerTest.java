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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD unit tests for {@link TypeMarshaller}.
 *
 * <p>These tests verify the TypeMarshaller's error handling behaviour without
 * requiring GraalPy at runtime. Type conversion from real {@code Value} objects
 * is covered by integration tests that run with GraalVM JDK.</p>
 */
@DisplayName("TypeMarshaller")
class TypeMarshallerTest {

    // ── Null input tests ────────────────────────────────────────────────────────

    @Test
    @DisplayName("toJava returns null for null input")
    void toJavaReturnsNullForNullInput() {
        Object result = TypeMarshaller.toJava(null);
        assertNull(result);
    }

    @Test
    @DisplayName("toString throws PythonException for null Value")
    void toStringThrowsForNullValue() {
        PythonException ex = assertThrows(PythonException.class,
                () -> TypeMarshaller.toString(null));

        assertThat(ex.getErrorKind(), is(PythonException.ErrorKind.TYPE_CONVERSION_ERROR));
        assertThat(ex.getMessage(), containsString("None"));
    }

    @Test
    @DisplayName("toDouble throws PythonException for null Value")
    void toDoubleThrowsForNullValue() {
        PythonException ex = assertThrows(PythonException.class,
                () -> TypeMarshaller.toDouble(null));

        assertThat(ex.getErrorKind(), is(PythonException.ErrorKind.TYPE_CONVERSION_ERROR));
        assertThat(ex.getMessage(), containsString("None"));
    }

    @Test
    @DisplayName("toLong throws PythonException for null Value")
    void toLongThrowsForNullValue() {
        PythonException ex = assertThrows(PythonException.class,
                () -> TypeMarshaller.toLong(null));

        assertThat(ex.getErrorKind(), is(PythonException.ErrorKind.TYPE_CONVERSION_ERROR));
        assertThat(ex.getMessage(), containsString("None"));
    }

    @Test
    @DisplayName("toMap throws PythonException for null Value")
    void toMapThrowsForNullValue() {
        PythonException ex = assertThrows(PythonException.class,
                () -> TypeMarshaller.toMap(null));

        assertThat(ex.getErrorKind(), is(PythonException.ErrorKind.TYPE_CONVERSION_ERROR));
        assertThat(ex.getMessage(), containsString("None"));
    }

    @Test
    @DisplayName("toList throws PythonException for null Value")
    void toListThrowsForNullValue() {
        PythonException ex = assertThrows(PythonException.class,
                () -> TypeMarshaller.toList(null));

        assertThat(ex.getErrorKind(), is(PythonException.ErrorKind.TYPE_CONVERSION_ERROR));
        assertThat(ex.getMessage(), containsString("None"));
    }

    @Test
    @DisplayName("as throws PythonException for null Value")
    void asThrowsForNullValue() {
        PythonException ex = assertThrows(PythonException.class,
                () -> TypeMarshaller.as(null, String.class));

        assertThat(ex.getErrorKind(), is(PythonException.ErrorKind.TYPE_CONVERSION_ERROR));
        assertThat(ex.getMessage(), containsString("None"));
    }

    // ── PythonException structure tests ────────────────────────────────────────

    @Test
    @DisplayName("PythonException carries the correct ErrorKind")
    void pythonExceptionCarriesErrorKind() {
        PythonException ex = new PythonException(
                "Test error", PythonException.ErrorKind.RUNTIME_ERROR);

        assertThat(ex.getErrorKind(), is(PythonException.ErrorKind.RUNTIME_ERROR));
        assertThat(ex.getMessage(), is("Test error"));
    }

    @Test
    @DisplayName("PythonException.toString includes error kind")
    void pythonExceptionToStringIncludesKind() {
        PythonException ex = new PythonException(
                "Something broke", PythonException.ErrorKind.SYNTAX_ERROR);

        assertThat(ex.toString(), containsString("SYNTAX_ERROR"));
        assertThat(ex.toString(), containsString("Something broke"));
    }

    @Test
    @DisplayName("PythonException wraps a cause")
    void pythonExceptionWrapsACause() {
        RuntimeException cause = new RuntimeException("root cause");
        PythonException ex = new PythonException(
                "Outer message", PythonException.ErrorKind.RUNTIME_NOT_AVAILABLE, cause);

        assertThat(ex.getCause(), is(cause));
        assertThat(ex.getErrorKind(), is(PythonException.ErrorKind.RUNTIME_NOT_AVAILABLE));
    }

    // ── TypeMarshaller utility class constraint ─────────────────────────────────

    @Test
    @DisplayName("TypeMarshaller cannot be instantiated")
    void typeMarshallerCannotBeInstantiated() throws Exception {
        var constructor = TypeMarshaller.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThrows(Exception.class, constructor::newInstance,
                "TypeMarshaller must throw from its private constructor");
    }
}
