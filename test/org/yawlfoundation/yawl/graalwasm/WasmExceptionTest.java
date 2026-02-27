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

package org.yawlfoundation.yawl.graalwasm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class WasmExceptionTest {

    @Test
    void errorKind_hasExactlySevenValues() {
        WasmException.ErrorKind[] values = WasmException.ErrorKind.values();
        assertEquals(7, values.length, "ErrorKind enum should have exactly 7 values");
    }

    @Test
    void errorKind_containsAllExpectedValues() {
        WasmException.ErrorKind[] expected = {
                WasmException.ErrorKind.RUNTIME_NOT_AVAILABLE,
                WasmException.ErrorKind.MODULE_LOAD_ERROR,
                WasmException.ErrorKind.INSTANTIATION_ERROR,
                WasmException.ErrorKind.FUNCTION_NOT_FOUND,
                WasmException.ErrorKind.EXECUTION_ERROR,
                WasmException.ErrorKind.TYPE_CONVERSION_ERROR,
                WasmException.ErrorKind.WASI_ERROR
        };

        for (WasmException.ErrorKind kind : expected) {
            assertNotNull(kind, "Expected ErrorKind should not be null: " + kind);
        }
    }

    @Test
    void twoArgConstructor_storesMessageAndKind() {
        String message = "Test error message";
        WasmException.ErrorKind kind = WasmException.ErrorKind.EXECUTION_ERROR;
        WasmException ex = new WasmException(message, kind);

        assertEquals(message, ex.getMessage());
        assertEquals(kind, ex.getErrorKind());
    }

    @Test
    void twoArgConstructor_causeIsNull() {
        WasmException ex = new WasmException("message", WasmException.ErrorKind.EXECUTION_ERROR);
        assertNull(ex.getCause());
    }

    @Test
    void threeArgConstructor_chainsCause() {
        String message = "Wrapper message";
        WasmException.ErrorKind kind = WasmException.ErrorKind.MODULE_LOAD_ERROR;
        IOException cause = new IOException("Root cause");

        WasmException ex = new WasmException(message, kind, cause);

        assertEquals(message, ex.getMessage());
        assertEquals(kind, ex.getErrorKind());
        assertThat(ex.getCause(), is(cause));
    }

    @Test
    void getErrorKind_returnsConfiguredKind() {
        WasmException.ErrorKind kind = WasmException.ErrorKind.INSTANTIATION_ERROR;
        WasmException ex = new WasmException("msg", kind);
        assertEquals(kind, ex.getErrorKind());
    }

    @ParameterizedTest
    @EnumSource(WasmException.ErrorKind.class)
    void toString_matchesFormat(WasmException.ErrorKind kind) {
        String message = "test message";
        WasmException ex = new WasmException(message, kind);
        String result = ex.toString();

        assertThat(result, containsString("WasmException[" + kind + "]"));
        assertThat(result, containsString(message));
        assertEquals("WasmException[" + kind + "]: " + message, result);
    }

    @Test
    void isRuntimeException() {
        WasmException ex = new WasmException("msg", WasmException.ErrorKind.EXECUTION_ERROR);
        assertThat(ex, instanceOf(RuntimeException.class));
    }
}
