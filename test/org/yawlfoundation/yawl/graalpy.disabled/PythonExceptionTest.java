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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD unit tests for {@link PythonException}.
 *
 * <p>Fully unit-testable with zero GraalPy dependency.</p>
 */
@DisplayName("PythonException")
class PythonExceptionTest {

    // ── ErrorKind enum ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("ErrorKind has exactly 8 distinct values")
    void errorKindHasEightValues() {
        assertThat(PythonException.ErrorKind.values().length, is(8));
    }

    @ParameterizedTest
    @EnumSource(PythonException.ErrorKind.class)
    @DisplayName("each ErrorKind has a non-null name")
    void eachErrorKindHasNonNullName(PythonException.ErrorKind kind) {
        assertThat(kind.name(), notNullValue());
        assertThat(kind.name(), not(emptyString()));
    }

    @Test
    @DisplayName("all expected ErrorKind values are present")
    void allExpectedErrorKindsPresent() {
        var kinds = java.util.EnumSet.allOf(PythonException.ErrorKind.class);

        assertTrue(kinds.contains(PythonException.ErrorKind.RUNTIME_NOT_AVAILABLE));
        assertTrue(kinds.contains(PythonException.ErrorKind.SYNTAX_ERROR));
        assertTrue(kinds.contains(PythonException.ErrorKind.RUNTIME_ERROR));
        assertTrue(kinds.contains(PythonException.ErrorKind.TYPE_CONVERSION_ERROR));
        assertTrue(kinds.contains(PythonException.ErrorKind.SANDBOX_VIOLATION));
        assertTrue(kinds.contains(PythonException.ErrorKind.CONTEXT_ERROR));
        assertTrue(kinds.contains(PythonException.ErrorKind.VENV_ERROR));
        assertTrue(kinds.contains(PythonException.ErrorKind.INTERFACE_GENERATION_ERROR));
    }

    // ── Two-arg constructor ──────────────────────────────────────────────────────

    @Test
    @DisplayName("two-arg constructor stores message")
    void twoArgConstructorStoresMessage() {
        PythonException ex = new PythonException("test message", PythonException.ErrorKind.RUNTIME_ERROR);
        assertThat(ex.getMessage(), is("test message"));
    }

    @Test
    @DisplayName("two-arg constructor stores error kind")
    void twoArgConstructorStoresErrorKind() {
        PythonException ex = new PythonException("msg", PythonException.ErrorKind.SYNTAX_ERROR);
        assertThat(ex.getErrorKind(), is(PythonException.ErrorKind.SYNTAX_ERROR));
    }

    @Test
    @DisplayName("two-arg constructor leaves cause null")
    void twoArgConstructorHasNullCause() {
        PythonException ex = new PythonException("msg", PythonException.ErrorKind.CONTEXT_ERROR);
        assertNull(ex.getCause());
    }

    @ParameterizedTest
    @EnumSource(PythonException.ErrorKind.class)
    @DisplayName("two-arg constructor accepts every ErrorKind")
    void twoArgConstructorAcceptsEveryKind(PythonException.ErrorKind kind) {
        PythonException ex = new PythonException("msg", kind);
        assertThat(ex.getErrorKind(), is(kind));
    }

    // ── Three-arg constructor (with cause) ───────────────────────────────────────

    @Test
    @DisplayName("three-arg constructor stores message")
    void threeArgConstructorStoresMessage() {
        RuntimeException cause = new RuntimeException("root");
        PythonException ex = new PythonException("outer", PythonException.ErrorKind.CONTEXT_ERROR, cause);
        assertThat(ex.getMessage(), is("outer"));
    }

    @Test
    @DisplayName("three-arg constructor stores error kind")
    void threeArgConstructorStoresErrorKind() {
        RuntimeException cause = new RuntimeException("root");
        PythonException ex = new PythonException("msg", PythonException.ErrorKind.VENV_ERROR, cause);
        assertThat(ex.getErrorKind(), is(PythonException.ErrorKind.VENV_ERROR));
    }

    @Test
    @DisplayName("three-arg constructor chains cause")
    void threeArgConstructorChainsCause() {
        RuntimeException cause = new RuntimeException("underlying");
        PythonException ex = new PythonException("outer", PythonException.ErrorKind.RUNTIME_NOT_AVAILABLE, cause);
        assertThat(ex.getCause(), is(cause));
    }

    @Test
    @DisplayName("three-arg constructor: cause getMessage() is accessible")
    void threeArgConstructorCauseMessageAccessible() {
        RuntimeException cause = new RuntimeException("underlying error detail");
        PythonException ex = new PythonException("msg", PythonException.ErrorKind.RUNTIME_ERROR, cause);
        assertThat(ex.getCause().getMessage(), is("underlying error detail"));
    }

    @ParameterizedTest
    @EnumSource(PythonException.ErrorKind.class)
    @DisplayName("three-arg constructor accepts every ErrorKind")
    void threeArgConstructorAcceptsEveryKind(PythonException.ErrorKind kind) {
        RuntimeException cause = new RuntimeException("c");
        PythonException ex = new PythonException("msg", kind, cause);
        assertThat(ex.getErrorKind(), is(kind));
        assertThat(ex.getCause(), is(cause));
    }

    // ── toString() ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("toString includes error kind in brackets")
    void toStringIncludesErrorKindInBrackets() {
        PythonException ex = new PythonException("Something broke", PythonException.ErrorKind.SYNTAX_ERROR);
        assertThat(ex.toString(), containsString("[SYNTAX_ERROR]"));
    }

    @Test
    @DisplayName("toString includes message text")
    void toStringIncludesMessageText() {
        PythonException ex = new PythonException("Something broke", PythonException.ErrorKind.SYNTAX_ERROR);
        assertThat(ex.toString(), containsString("Something broke"));
    }

    @Test
    @DisplayName("toString format is PythonException[KIND]: message")
    void toStringHasExpectedFormat() {
        PythonException ex = new PythonException("test msg", PythonException.ErrorKind.RUNTIME_ERROR);
        assertThat(ex.toString(), is("PythonException[RUNTIME_ERROR]: test msg"));
    }

    @ParameterizedTest
    @EnumSource(PythonException.ErrorKind.class)
    @DisplayName("toString contains the kind name for every ErrorKind")
    void toStringContainsKindNameForEveryKind(PythonException.ErrorKind kind) {
        PythonException ex = new PythonException("msg", kind);
        assertThat(ex.toString(), containsString(kind.name()));
    }

    // ── Is-a RuntimeException ────────────────────────────────────────────────────

    @Test
    @DisplayName("PythonException is a RuntimeException")
    void isARuntimeException() {
        PythonException ex = new PythonException("msg", PythonException.ErrorKind.CONTEXT_ERROR);
        assertThat(ex, instanceOf(RuntimeException.class));
    }

    @Test
    @DisplayName("PythonException is an Exception")
    void isAnException() {
        PythonException ex = new PythonException("msg", PythonException.ErrorKind.CONTEXT_ERROR);
        assertThat(ex, instanceOf(Exception.class));
    }

    // ── getErrorKind() never-null contract ───────────────────────────────────────

    @Test
    @DisplayName("getErrorKind is never null")
    void getErrorKindIsNeverNull() {
        PythonException ex = new PythonException("msg", PythonException.ErrorKind.SANDBOX_VIOLATION);
        assertThat(ex.getErrorKind(), notNullValue());
    }
}
