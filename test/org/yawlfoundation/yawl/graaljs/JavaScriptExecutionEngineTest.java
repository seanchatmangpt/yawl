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
 * Unit tests for {@link JavaScriptExecutionEngine}.
 *
 * Tests run on standard (non-GraalVM) JDK, so all eval* methods throw.
 */
class JavaScriptExecutionEngineTest {

    @Test
    void builder_withPoolSizeZero_throwsIllegalArgumentException() {
        try {
            JavaScriptExecutionEngine.builder()
                    .contextPoolSize(0)
                    .build();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsString("at least 1"));
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException");
    }

    @Test
    void builder_defaultSucceeds() {
        JavaScriptExecutionEngine engine = JavaScriptExecutionEngine.builder().build();
        assertThat(engine, notNullValue());
        engine.close();
    }

    @Test
    void builder_sandboxedTrue_succeeds() {
        JavaScriptExecutionEngine engine = JavaScriptExecutionEngine.builder()
                .sandboxed(true)
                .build();
        assertThat(engine, notNullValue());
        engine.close();
    }

    @Test
    void builder_sandboxedFalse_succeeds() {
        JavaScriptExecutionEngine engine = JavaScriptExecutionEngine.builder()
                .sandboxed(false)
                .build();
        assertThat(engine, notNullValue());
        engine.close();
    }

    @Test
    void builder_withSandboxConfig_succeeds() {
        JavaScriptSandboxConfig config = JavaScriptSandboxConfig.standard();
        JavaScriptExecutionEngine engine = JavaScriptExecutionEngine.builder()
                .sandboxConfig(config)
                .build();
        assertThat(engine, notNullValue());
        engine.close();
    }

    @Test
    void getContextPool_returnsNonNull() {
        JavaScriptExecutionEngine engine = JavaScriptExecutionEngine.builder().build();
        assertThat(engine.getContextPool(), notNullValue());
        engine.close();
    }

    @Test
    void getContextPool_maxTotalMatchesPoolSize() {
        JavaScriptExecutionEngine engine = JavaScriptExecutionEngine.builder()
                .contextPoolSize(4)
                .build();
        assertThat(engine.getContextPool().getMaxTotal(), is(4));
        engine.close();
    }

    @Test
    void eval_throwsWhenGraalJSAbsent() {
        JavaScriptExecutionEngine engine = JavaScriptExecutionEngine.builder().build();
        JavaScriptException ex = null;
        try {
            engine.eval("1 + 2");
        } catch (JavaScriptException e) {
            ex = e;
        } finally {
            engine.close();
        }

        assertThat(ex, notNullValue());
        assertThat(ex.getErrorKind(), is(JavaScriptException.ErrorKind.CONTEXT_ERROR));
    }

    @Test
    void evalToString_throwsWhenAbsent() {
        JavaScriptExecutionEngine engine = JavaScriptExecutionEngine.builder().build();
        JavaScriptException ex = null;
        try {
            engine.evalToString("'hello'");
        } catch (JavaScriptException e) {
            ex = e;
        } finally {
            engine.close();
        }

        assertThat(ex, notNullValue());
    }

    @Test
    void evalToDouble_throwsWhenAbsent() {
        JavaScriptExecutionEngine engine = JavaScriptExecutionEngine.builder().build();
        JavaScriptException ex = null;
        try {
            engine.evalToDouble("3.14");
        } catch (JavaScriptException e) {
            ex = e;
        } finally {
            engine.close();
        }

        assertThat(ex, notNullValue());
    }

    @Test
    void evalToLong_throwsWhenAbsent() {
        JavaScriptExecutionEngine engine = JavaScriptExecutionEngine.builder().build();
        JavaScriptException ex = null;
        try {
            engine.evalToLong("42");
        } catch (JavaScriptException e) {
            ex = e;
        } finally {
            engine.close();
        }

        assertThat(ex, notNullValue());
    }

    @Test
    void evalToMap_throwsWhenAbsent() {
        JavaScriptExecutionEngine engine = JavaScriptExecutionEngine.builder().build();
        JavaScriptException ex = null;
        try {
            engine.evalToMap("({})");
        } catch (JavaScriptException e) {
            ex = e;
        } finally {
            engine.close();
        }

        assertThat(ex, notNullValue());
    }

    @Test
    void evalToList_throwsWhenAbsent() {
        JavaScriptExecutionEngine engine = JavaScriptExecutionEngine.builder().build();
        JavaScriptException ex = null;
        try {
            engine.evalToList("[]");
        } catch (JavaScriptException e) {
            ex = e;
        } finally {
            engine.close();
        }

        assertThat(ex, notNullValue());
    }

    @Test
    void evalScript_throwsWhenAbsent() {
        JavaScriptExecutionEngine engine = JavaScriptExecutionEngine.builder().build();
        JavaScriptException ex = null;
        try {
            java.nio.file.Path tmpFile = java.nio.file.Files.createTempFile("test", ".js");
            java.nio.file.Files.writeString(tmpFile, "1 + 2;");
            try {
                engine.evalScript(tmpFile);
            } finally {
                java.nio.file.Files.delete(tmpFile);
            }
        } catch (JavaScriptException e) {
            ex = e;
        } catch (Exception e) {
            throw new AssertionError("Unexpected exception: " + e, e);
        } finally {
            engine.close();
        }

        assertThat(ex, notNullValue());
    }

    @Test
    void invokeJsFunction_throwsWhenAbsent() {
        JavaScriptExecutionEngine engine = JavaScriptExecutionEngine.builder().build();
        JavaScriptException ex = null;
        try {
            engine.invokeJsFunction("myFunc", 42);
        } catch (JavaScriptException e) {
            ex = e;
        } finally {
            engine.close();
        }

        assertThat(ex, notNullValue());
    }

    @Test
    void evalAsJson_throwsWhenAbsent() {
        JavaScriptExecutionEngine engine = JavaScriptExecutionEngine.builder().build();
        JavaScriptException ex = null;
        try {
            engine.evalAsJson("({ name: 'test' })");
        } catch (JavaScriptException e) {
            ex = e;
        } finally {
            engine.close();
        }

        assertThat(ex, notNullValue());
    }

    @Test
    void eval_exceptionIsContextError() {
        JavaScriptExecutionEngine engine = JavaScriptExecutionEngine.builder().build();
        JavaScriptException ex = null;
        try {
            engine.eval("1 + 2");
        } catch (JavaScriptException e) {
            ex = e;
        } finally {
            engine.close();
        }

        assertThat(ex, notNullValue());
        assertThat(ex.getErrorKind(), is(JavaScriptException.ErrorKind.CONTEXT_ERROR));
    }

    @Test
    void close_doesNotThrow() {
        JavaScriptExecutionEngine engine = JavaScriptExecutionEngine.builder().build();
        engine.close();
    }

    @Test
    void close_isIdempotent() {
        JavaScriptExecutionEngine engine = JavaScriptExecutionEngine.builder().build();
        engine.close();
        engine.close();
    }
}
