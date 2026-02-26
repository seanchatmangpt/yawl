/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.polyglot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GraalPyRuntime availability detection and graceful degradation.
 *
 * <p>GraalPy JARs are not on the test classpath, so isAvailable() must
 * return false and all eval() calls must throw PolyglotException.
 */
class GraalPyRuntimeTest {

    @Test
    void isAvailable_withoutGraalPyJars_returnsFalse() {
        // GraalPy (org.graalvm.polyglot.Context) is not on the test classpath.
        // isAvailable() must detect this and return false.
        assertFalse(GraalPyRuntime.isAvailable(),
            "GraalPy is not on the test classpath; isAvailable() must return false");
    }

    @Test
    void eval_whenGraalPyUnavailable_throwsPolyglotException() {
        // When GraalPy is unavailable, eval() must throw PolyglotException immediately.
        GraalPyRuntime runtime = new GraalPyRuntime();
        PolyglotException ex = assertThrows(PolyglotException.class,
            () -> runtime.eval("1 + 1"),
            "eval() must throw PolyglotException when GraalPy is unavailable");
        assertNotNull(ex.getMessage(), "Exception message must not be null");
        assertFalse(ex.getMessage().isBlank(), "Exception message must not be blank");
    }

    @Test
    void close_whenNeverOpened_doesNotThrow() {
        // close() must be safe to call even when the context was never initialized.
        GraalPyRuntime runtime = new GraalPyRuntime();
        assertDoesNotThrow(runtime::close, "close() on a never-opened runtime must not throw");
    }

    @Test
    void close_idempotent_doesNotThrow() {
        // close() must be idempotent: safe to call multiple times.
        GraalPyRuntime runtime = new GraalPyRuntime();
        assertDoesNotThrow(runtime::close, "First close() must not throw");
        assertDoesNotThrow(runtime::close, "Second close() must not throw (idempotent)");
    }

    @Test
    void eval_afterClose_throwsPolyglotException() {
        // After close(), eval() must throw PolyglotException.
        // Even if GraalPy were available, after close() the context is invalid.
        GraalPyRuntime runtime = new GraalPyRuntime();
        runtime.close();

        // If GraalPy is not available, PolyglotException is thrown due to unavailability.
        // If GraalPy were available (not the case here), it would throw due to closed state.
        assertThrows(PolyglotException.class, () -> runtime.eval("print('hello')"),
            "eval() after close() must throw PolyglotException");
    }

    @Test
    void isAvailable_calledMultipleTimes_returnsSameValue() {
        // isAvailable() result must be cached and consistent across multiple calls.
        boolean first = GraalPyRuntime.isAvailable();
        boolean second = GraalPyRuntime.isAvailable();
        boolean third = GraalPyRuntime.isAvailable();
        assertEquals(first, second, "isAvailable() must return the same value on every call");
        assertEquals(second, third, "isAvailable() must return the same value on every call");
    }

    @Test
    void polyglotException_withMessage_hasCorrectMessage() {
        PolyglotException ex = new PolyglotException("test message");
        assertEquals("test message", ex.getMessage());
    }

    @Test
    void polyglotException_withCause_hasCorrectCause() {
        Throwable cause = new RuntimeException("root cause");
        PolyglotException ex = new PolyglotException("wrapper", cause);
        assertEquals("wrapper", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void graalPyRuntime_implementsAutoCloseable() {
        // GraalPyRuntime must implement AutoCloseable for try-with-resources usage.
        try (GraalPyRuntime runtime = new GraalPyRuntime()) {
            assertFalse(GraalPyRuntime.isAvailable(), "GraalPy must not be available in test env");
        }
        // No exception means close() worked correctly.
    }
}
