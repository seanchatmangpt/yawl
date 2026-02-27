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
 * Chicago TDD unit tests for {@link PythonExecutionContext}.
 *
 * <p>On standard JDK (Temurin), constructing a {@code PythonExecutionContext} must
 * throw {@link PythonException} with kind {@code RUNTIME_NOT_AVAILABLE} because
 * GraalPy is not installed. This validates the documented contract:
 * <em>"GraalVM JDK 24.1+ must be used at runtime"</em>.</p>
 *
 * <p>All tests in this class verify the error-path behaviour on non-GraalVM JDK.
 * Integration tests covering successful construction run only in GraalVM environments.</p>
 */
@DisplayName("PythonExecutionContext")
class PythonExecutionContextTest {

    // ── Construction fails when GraalPy absent ───────────────────────────────────

    @Test
    @DisplayName("construction with strict sandbox throws PythonException on non-GraalVM JDK")
    void constructionWithStrictSandboxThrowsPythonException() {
        assertThrows(PythonException.class,
                () -> new PythonExecutionContext(PythonSandboxConfig.strict()));
    }

    @Test
    @DisplayName("construction with standard sandbox throws PythonException on non-GraalVM JDK")
    void constructionWithStandardSandboxThrowsPythonException() {
        assertThrows(PythonException.class,
                () -> new PythonExecutionContext(PythonSandboxConfig.standard()));
    }

    @Test
    @DisplayName("construction with permissive sandbox throws PythonException on non-GraalVM JDK")
    void constructionWithPermissiveSandboxThrowsPythonException() {
        assertThrows(PythonException.class,
                () -> new PythonExecutionContext(PythonSandboxConfig.permissive()));
    }

    @Test
    @DisplayName("construction throws PythonException with RUNTIME_NOT_AVAILABLE kind")
    void constructionThrowsRuntimeNotAvailableKind() {
        PythonException ex = assertThrows(PythonException.class,
                () -> new PythonExecutionContext(PythonSandboxConfig.standard()));

        assertThat(ex.getErrorKind(), is(PythonException.ErrorKind.RUNTIME_NOT_AVAILABLE));
    }

    @Test
    @DisplayName("RUNTIME_NOT_AVAILABLE message mentions GraalVM JDK")
    void runtimeNotAvailableMessageMentionsGraalVm() {
        PythonException ex = assertThrows(PythonException.class,
                () -> new PythonExecutionContext(PythonSandboxConfig.standard()));

        assertThat(ex.getMessage(), containsStringIgnoringCase("GraalVM"));
    }

    @Test
    @DisplayName("RUNTIME_NOT_AVAILABLE exception chains the underlying cause")
    void runtimeNotAvailableChainsCause() {
        PythonException ex = assertThrows(PythonException.class,
                () -> new PythonExecutionContext(PythonSandboxConfig.standard()));

        assertThat(ex.getCause(), notNullValue());
    }

    @Test
    @DisplayName("RUNTIME_NOT_AVAILABLE message mentions classpath")
    void runtimeNotAvailableMessageMentionsClasspath() {
        PythonException ex = assertThrows(PythonException.class,
                () -> new PythonExecutionContext(PythonSandboxConfig.standard()));

        assertThat(ex.getMessage(), containsStringIgnoringCase("classpath"));
    }

    // ── PYTHON_LANGUAGE_ID constant ────────────────────────────────────────────

    @Test
    @DisplayName("PYTHON_LANGUAGE_ID constant is 'python'")
    void pythonLanguageIdConstantIsPython() {
        assertThat(PythonExecutionContext.PYTHON_LANGUAGE_ID, is("python"));
    }
}
