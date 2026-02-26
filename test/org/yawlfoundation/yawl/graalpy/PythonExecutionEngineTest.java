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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD unit tests for {@link PythonExecutionEngine}.
 *
 * <p>Tests builder validation, construction, and delegation behaviour on standard
 * JDK where GraalPy is absent. All {@code eval*} methods must throw
 * {@link PythonException} when the GraalPy runtime is not available.</p>
 */
@DisplayName("PythonExecutionEngine")
class PythonExecutionEngineTest {

    private PythonExecutionEngine engine;

    @BeforeEach
    void createEngine() {
        engine = PythonExecutionEngine.builder()
                .contextPoolSize(1)
                .sandboxed(true)
                .build();
    }

    @AfterEach
    void closeEngine() {
        if (engine != null) {
            engine.close();
        }
    }

    // ── Builder validation ────────────────────────────────────────────────────────

    @Test
    @DisplayName("builder().contextPoolSize(0).build() throws IllegalArgumentException")
    void builderWithZeroPoolSizeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> PythonExecutionEngine.builder().contextPoolSize(0).build());
    }

    @Test
    @DisplayName("builder().contextPoolSize(-1).build() throws IllegalArgumentException")
    void builderWithNegativePoolSizeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> PythonExecutionEngine.builder().contextPoolSize(-1).build());
    }

    @Test
    @DisplayName("builder().contextPoolSize(-10).build() throws IllegalArgumentException")
    void builderWithLargeNegativePoolSizeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> PythonExecutionEngine.builder().contextPoolSize(-10).build());
    }

    // ── Builder construction ──────────────────────────────────────────────────────

    @Test
    @DisplayName("builder().build() returns non-null engine with default pool size")
    void builderWithDefaultsReturnsEngine() {
        try (PythonExecutionEngine e = PythonExecutionEngine.builder().build()) {
            assertThat(e, notNullValue());
        }
    }

    @Test
    @DisplayName("builder().sandboxed(true).build() succeeds without GraalPy")
    void builderWithSandboxedTrueSucceeds() {
        try (PythonExecutionEngine e = PythonExecutionEngine.builder()
                .contextPoolSize(1)
                .sandboxed(true)
                .build()) {
            assertThat(e, notNullValue());
        }
    }

    @Test
    @DisplayName("builder().sandboxed(false).build() succeeds without GraalPy")
    void builderWithSandboxedFalseSucceeds() {
        try (PythonExecutionEngine e = PythonExecutionEngine.builder()
                .contextPoolSize(1)
                .sandboxed(false)
                .build()) {
            assertThat(e, notNullValue());
        }
    }

    @Test
    @DisplayName("builder().sandboxConfig(strict()).build() succeeds without GraalPy")
    void builderWithExplicitSandboxConfigSucceeds() {
        try (PythonExecutionEngine e = PythonExecutionEngine.builder()
                .contextPoolSize(1)
                .sandboxConfig(PythonSandboxConfig.strict())
                .build()) {
            assertThat(e, notNullValue());
        }
    }

    @Test
    @DisplayName("builder().sandboxConfig(permissive()).build() succeeds")
    void builderWithPermissiveSandboxSucceeds() {
        try (PythonExecutionEngine e = PythonExecutionEngine.builder()
                .contextPoolSize(1)
                .sandboxConfig(PythonSandboxConfig.permissive())
                .build()) {
            assertThat(e, notNullValue());
        }
    }

    @Test
    @DisplayName("builder().bytecodeCacheDir() sets cache dir without error")
    void builderWithBytecodeCacheDirSucceeds() {
        try (PythonExecutionEngine e = PythonExecutionEngine.builder()
                .contextPoolSize(1)
                .bytecodeCacheDir(Path.of("/tmp/graalpy-cache-test"))
                .build()) {
            assertThat(e, notNullValue());
        }
    }

    // ── getContextPool() ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("getContextPool() returns non-null pool")
    void getContextPoolReturnsNonNull() {
        assertThat(engine.getContextPool(), notNullValue());
    }

    @Test
    @DisplayName("getContextPool().getMaxTotal() matches configured pool size")
    void contextPoolMaxTotalMatchesConfiguredSize() {
        try (PythonExecutionEngine e = PythonExecutionEngine.builder()
                .contextPoolSize(3)
                .build()) {
            assertThat(e.getContextPool().getMaxTotal(), is(3));
        }
    }

    // ── eval*() methods throw PythonException when GraalPy absent ────────────────

    @Test
    @DisplayName("eval() throws PythonException when GraalPy is absent")
    void evalThrowsPythonException() {
        assertThrows(PythonException.class, () -> engine.eval("1 + 1"));
    }

    @Test
    @DisplayName("evalToString() throws PythonException when GraalPy is absent")
    void evalToStringThrowsPythonException() {
        assertThrows(PythonException.class, () -> engine.evalToString("'hello'"));
    }

    @Test
    @DisplayName("evalToDouble() throws PythonException when GraalPy is absent")
    void evalToDoubleThrowsPythonException() {
        assertThrows(PythonException.class, () -> engine.evalToDouble("3.14"));
    }

    @Test
    @DisplayName("evalToLong() throws PythonException when GraalPy is absent")
    void evalToLongThrowsPythonException() {
        assertThrows(PythonException.class, () -> engine.evalToLong("42"));
    }

    @Test
    @DisplayName("evalToMap() throws PythonException when GraalPy is absent")
    void evalToMapThrowsPythonException() {
        assertThrows(PythonException.class, () -> engine.evalToMap("{}"));
    }

    @Test
    @DisplayName("evalToList() throws PythonException when GraalPy is absent")
    void evalToListThrowsPythonException() {
        assertThrows(PythonException.class, () -> engine.evalToList("[]"));
    }

    @Test
    @DisplayName("evalAs() throws PythonException when GraalPy is absent")
    void evalAsThrowsPythonException() {
        assertThrows(PythonException.class, () -> engine.evalAs("'x'", String.class));
    }

    @Test
    @DisplayName("evalScript() throws PythonException when GraalPy is absent")
    void evalScriptThrowsPythonException() {
        assertThrows(PythonException.class, () -> engine.evalScript(Path.of("/tmp/test.py")));
    }

    @Test
    @DisplayName("invokePythonFunction() throws PythonException when GraalPy is absent")
    void invokePythonFunctionThrowsPythonException() {
        assertThrows(PythonException.class,
                () -> engine.invokePythonFunction("os", "getcwd"));
    }

    // ── eval() PythonException carries CONTEXT_ERROR kind ────────────────────────

    @Test
    @DisplayName("eval() PythonException has CONTEXT_ERROR kind when GraalPy absent")
    void evalPythonExceptionHasContextErrorKind() {
        PythonException ex = assertThrows(PythonException.class, () -> engine.eval("1 + 1"));
        assertThat(ex.getErrorKind(), is(PythonException.ErrorKind.CONTEXT_ERROR));
    }

    @Test
    @DisplayName("eval() PythonException has non-null message")
    void evalPythonExceptionHasNonNullMessage() {
        PythonException ex = assertThrows(PythonException.class, () -> engine.eval("1 + 1"));
        assertThat(ex.getMessage(), notNullValue());
        assertThat(ex.getMessage(), not(emptyString()));
    }

    // ── close() ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("close() does not throw")
    void closeDoesNotThrow() {
        PythonExecutionEngine e = PythonExecutionEngine.builder().contextPoolSize(1).build();
        assertDoesNotThrow(e::close);
    }

    @Test
    @DisplayName("close() is idempotent — second close does not throw")
    void closeIsIdempotent() {
        PythonExecutionEngine e = PythonExecutionEngine.builder().contextPoolSize(1).build();
        e.close();
        assertDoesNotThrow(e::close);
    }

    @Test
    @DisplayName("try-with-resources closes engine without throwing")
    void tryWithResourcesClosesEngine() {
        assertDoesNotThrow(() -> {
            try (PythonExecutionEngine e = PythonExecutionEngine.builder()
                    .contextPoolSize(1).build()) {
                assertThat(e.getContextPool(), notNullValue());
            }
        });
    }
}
