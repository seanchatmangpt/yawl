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
 * Unit tests for {@link JavaScriptContextPool}.
 *
 * Tests run on standard (non-GraalVM) JDK, so context creation always throws.
 */
class JavaScriptContextPoolTest {

    @Test
    void create_withZeroPoolSize_throwsIllegalArgumentException() {
        try {
            JavaScriptContextPool.create(JavaScriptSandboxConfig.standard(), 0);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsString("at least 1"));
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException");
    }

    @Test
    void create_withNegativePoolSize_throws() {
        try {
            JavaScriptContextPool.create(JavaScriptSandboxConfig.standard(), -5);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsString("at least 1"));
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException");
    }

    @Test
    void create_withPoolSizeOne_succeeds() {
        JavaScriptContextPool pool = JavaScriptContextPool.create(JavaScriptSandboxConfig.standard(), 1);
        assertThat(pool, notNullValue());
        pool.close();
    }

    @Test
    void builder_withZeroPoolSize_throwsIllegalArgumentException() {
        try {
            JavaScriptContextPool.builder()
                    .maxPoolSize(0)
                    .build();
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsString("at least 1"));
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException");
    }

    @Test
    void builder_defaultSucceeds() {
        JavaScriptContextPool pool = JavaScriptContextPool.builder().build();
        assertThat(pool, notNullValue());
        pool.close();
    }

    @Test
    void getMaxTotal_matchesConfiguredSize() {
        JavaScriptContextPool pool = JavaScriptContextPool.create(JavaScriptSandboxConfig.standard(), 4);
        assertThat(pool.getMaxTotal(), is(4));
        pool.close();
    }

    @Test
    void getMaxTotal_viaBuilder() {
        JavaScriptContextPool pool = JavaScriptContextPool.builder()
                .maxPoolSize(8)
                .build();
        assertThat(pool.getMaxTotal(), is(8));
        pool.close();
    }

    @Test
    void getSandboxConfig_returnsConfiguredConfig() {
        JavaScriptSandboxConfig config = JavaScriptSandboxConfig.permissive();
        JavaScriptContextPool pool = JavaScriptContextPool.create(config, 2);
        assertThat(pool.getSandboxConfig(), sameInstance(config));
        pool.close();
    }

    @Test
    void getActiveCount_isZeroOnFreshPool() {
        JavaScriptContextPool pool = JavaScriptContextPool.create(JavaScriptSandboxConfig.standard(), 2);
        assertThat(pool.getActiveCount(), is(0));
        pool.close();
    }

    @Test
    void getIdleCount_isZeroOnFreshPool() {
        JavaScriptContextPool pool = JavaScriptContextPool.create(JavaScriptSandboxConfig.standard(), 2);
        assertThat(pool.getIdleCount(), is(0));
        pool.close();
    }

    @Test
    void execute_throwsJavaScriptException_contextError() {
        JavaScriptContextPool pool = JavaScriptContextPool.create(JavaScriptSandboxConfig.standard(), 1);
        JavaScriptException ex = null;
        try {
            pool.execute(ctx -> "ignored");
        } catch (JavaScriptException e) {
            ex = e;
        } finally {
            pool.close();
        }

        assertThat(ex, notNullValue());
        assertThat(ex.getErrorKind(), is(JavaScriptException.ErrorKind.CONTEXT_ERROR));
    }

    @Test
    void execute_contextError_messageContainsActiveCount() {
        JavaScriptContextPool pool = JavaScriptContextPool.create(JavaScriptSandboxConfig.standard(), 2);
        JavaScriptException ex = null;
        try {
            pool.execute(ctx -> "ignored");
        } catch (JavaScriptException e) {
            ex = e;
        } finally {
            pool.close();
        }

        assertThat(ex, notNullValue());
        assertThat(ex.getMessage(), containsString("active="));
    }

    @Test
    void execute_contextError_hasCause() {
        JavaScriptContextPool pool = JavaScriptContextPool.create(JavaScriptSandboxConfig.standard(), 1);
        JavaScriptException ex = null;
        try {
            pool.execute(ctx -> "ignored");
        } catch (JavaScriptException e) {
            ex = e;
        } finally {
            pool.close();
        }

        assertThat(ex, notNullValue());
        assertThat(ex.getCause(), notNullValue());
    }

    @Test
    void executeVoid_throwsWhenRuntimeAbsent() {
        JavaScriptContextPool pool = JavaScriptContextPool.create(JavaScriptSandboxConfig.standard(), 1);
        JavaScriptException ex = null;
        try {
            pool.executeVoid(ctx -> {});
        } catch (JavaScriptException e) {
            ex = e;
        } finally {
            pool.close();
        }

        assertThat(ex, notNullValue());
        assertThat(ex.getErrorKind(), is(JavaScriptException.ErrorKind.CONTEXT_ERROR));
    }

    @Test
    void close_doesNotThrow() {
        JavaScriptContextPool pool = JavaScriptContextPool.create(JavaScriptSandboxConfig.standard(), 1);
        pool.close();
    }

    @Test
    void close_isIdempotent() {
        JavaScriptContextPool pool = JavaScriptContextPool.create(JavaScriptSandboxConfig.standard(), 1);
        pool.close();
        pool.close();
    }
}
