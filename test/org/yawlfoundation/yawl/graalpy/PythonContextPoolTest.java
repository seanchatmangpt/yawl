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

import java.time.Duration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD unit tests for {@link PythonContextPool}.
 *
 * <p>Tests pool creation, configuration, and behaviour on standard JDK (no GraalPy).
 * When GraalPy is absent, {@link PythonContextPool#execute} must throw
 * {@link PythonException} with kind {@code CONTEXT_ERROR} because context
 * initialisation fails inside Commons Pool2's factory.</p>
 */
@DisplayName("PythonContextPool")
class PythonContextPoolTest {

    private static final PythonSandboxConfig STANDARD = PythonSandboxConfig.standard();
    private static final PythonSandboxConfig STRICT   = PythonSandboxConfig.strict();

    // ── create() validation ──────────────────────────────────────────────────────

    @Test
    @DisplayName("create() with maxPoolSize=0 throws IllegalArgumentException")
    void createWithZeroPoolSizeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> PythonContextPool.create(STANDARD, 0));
    }

    @Test
    @DisplayName("create() with maxPoolSize=-1 throws IllegalArgumentException")
    void createWithNegativePoolSizeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> PythonContextPool.create(STANDARD, -1));
    }

    @Test
    @DisplayName("create() with maxPoolSize=-5 throws IllegalArgumentException")
    void createWithLargeNegativePoolSizeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> PythonContextPool.create(STANDARD, -5));
    }

    @Test
    @DisplayName("create() with maxPoolSize=1 returns non-null pool")
    void createWithMinPoolSizeSucceeds() {
        try (PythonContextPool pool = PythonContextPool.create(STANDARD, 1)) {
            assertThat(pool, notNullValue());
        }
    }

    @Test
    @DisplayName("create() with maxPoolSize=4 returns non-null pool")
    void createWithValidPoolSizeReturnsPool() {
        try (PythonContextPool pool = PythonContextPool.create(STANDARD, 4)) {
            assertThat(pool, notNullValue());
        }
    }

    // ── Builder validation ────────────────────────────────────────────────────────

    @Test
    @DisplayName("builder().maxPoolSize(0).build() throws IllegalArgumentException")
    void builderWithZeroSizeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> PythonContextPool.builder().maxPoolSize(0).build());
    }

    @Test
    @DisplayName("builder().maxPoolSize(-1).build() throws IllegalArgumentException")
    void builderWithNegativeSizeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> PythonContextPool.builder().maxPoolSize(-1).build());
    }

    @Test
    @DisplayName("builder().build() with defaults succeeds")
    void builderWithDefaultsSucceeds() {
        try (PythonContextPool pool = PythonContextPool.builder().build()) {
            assertThat(pool, notNullValue());
        }
    }

    @Test
    @DisplayName("builder().sandboxConfig(strict()).build() succeeds")
    void builderWithStrictSandboxSucceeds() {
        try (PythonContextPool pool = PythonContextPool.builder()
                .sandboxConfig(STRICT)
                .maxPoolSize(2)
                .build()) {
            assertThat(pool, notNullValue());
        }
    }

    @Test
    @DisplayName("builder().maxWait().build() accepts custom wait duration")
    void builderWithCustomMaxWaitSucceeds() {
        try (PythonContextPool pool = PythonContextPool.builder()
                .maxWait(Duration.ofSeconds(5))
                .maxPoolSize(2)
                .build()) {
            assertThat(pool, notNullValue());
        }
    }

    // ── Pool state accessors ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getMaxTotal() returns configured pool size from create()")
    void getMaxTotalReturnsConfiguredSizeFromCreate() {
        try (PythonContextPool pool = PythonContextPool.create(STANDARD, 4)) {
            assertThat(pool.getMaxTotal(), is(4));
        }
    }

    @Test
    @DisplayName("getMaxTotal() returns configured pool size from builder")
    void getMaxTotalReturnsConfiguredSizeFromBuilder() {
        try (PythonContextPool pool = PythonContextPool.builder().maxPoolSize(3).build()) {
            assertThat(pool.getMaxTotal(), is(3));
        }
    }

    @Test
    @DisplayName("getSandboxConfig() returns the configured sandbox config")
    void getSandboxConfigReturnsConfiguredConfig() {
        try (PythonContextPool pool = PythonContextPool.create(STRICT, 2)) {
            assertThat(pool.getSandboxConfig(), is(STRICT));
        }
    }

    @Test
    @DisplayName("getActiveCount() is 0 on a fresh pool")
    void getActiveCountIsZeroOnFreshPool() {
        try (PythonContextPool pool = PythonContextPool.create(STANDARD, 2)) {
            assertThat(pool.getActiveCount(), is(0));
        }
    }

    @Test
    @DisplayName("getIdleCount() is 0 on a fresh pool (no pre-warming)")
    void getIdleCountIsZeroOnFreshPool() {
        try (PythonContextPool pool = PythonContextPool.create(STANDARD, 2)) {
            assertThat(pool.getIdleCount(), is(0));
        }
    }

    // ── execute() on non-GraalVM JDK ─────────────────────────────────────────────

    @Test
    @DisplayName("execute() throws PythonException when GraalPy is absent")
    void executeThrowsPythonExceptionWhenGraalPyAbsent() {
        try (PythonContextPool pool = PythonContextPool.create(STANDARD, 1)) {
            assertThrows(PythonException.class,
                    () -> pool.execute(ctx -> "result"));
        }
    }

    @Test
    @DisplayName("execute() throws PythonException with CONTEXT_ERROR kind when GraalPy is absent")
    void executeThrowsContextErrorKindWhenGraalPyAbsent() {
        try (PythonContextPool pool = PythonContextPool.create(STANDARD, 1)) {
            PythonException ex = assertThrows(PythonException.class,
                    () -> pool.execute(ctx -> "result"));
            assertThat(ex.getErrorKind(), is(PythonException.ErrorKind.CONTEXT_ERROR));
        }
    }

    @Test
    @DisplayName("execute() CONTEXT_ERROR message mentions pool state")
    void executeContextErrorMessageMentionsPoolState() {
        try (PythonContextPool pool = PythonContextPool.create(STANDARD, 1)) {
            PythonException ex = assertThrows(PythonException.class,
                    () -> pool.execute(ctx -> "result"));
            assertThat(ex.getMessage(), containsString("active="));
        }
    }

    @Test
    @DisplayName("execute() exception has a non-null cause")
    void executeExceptionHasCause() {
        try (PythonContextPool pool = PythonContextPool.create(STANDARD, 1)) {
            PythonException ex = assertThrows(PythonException.class,
                    () -> pool.execute(ctx -> "result"));
            assertThat(ex.getCause(), notNullValue());
        }
    }

    @Test
    @DisplayName("executeVoid() throws PythonException when GraalPy is absent")
    void executeVoidThrowsPythonExceptionWhenGraalPyAbsent() {
        try (PythonContextPool pool = PythonContextPool.create(STANDARD, 1)) {
            assertThrows(PythonException.class,
                    () -> pool.executeVoid(ctx -> { }));
        }
    }

    // ── close() ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("close() does not throw")
    void closeDoesNotThrow() {
        PythonContextPool pool = PythonContextPool.create(STANDARD, 1);
        assertDoesNotThrow(pool::close);
    }

    @Test
    @DisplayName("close() is idempotent — second call does not throw")
    void closeIsIdempotent() {
        PythonContextPool pool = PythonContextPool.create(STANDARD, 1);
        pool.close();
        assertDoesNotThrow(pool::close);
    }

    @Test
    @DisplayName("try-with-resources closes pool without throwing")
    void tryWithResourcesClosesPool() {
        assertDoesNotThrow(() -> {
            try (PythonContextPool pool = PythonContextPool.create(STANDARD, 2)) {
                assertThat(pool.getMaxTotal(), is(2));
            }
        });
    }
}
