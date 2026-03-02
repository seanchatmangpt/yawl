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

package org.yawlfoundation.yawl.schema;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SchemaContractException}.
 *
 * @since 6.0.0
 */
class SchemaContractExceptionTest {

    @Test
    void constructor_withMessage_setsMessage() {
        var ex = new SchemaContractException("Contract not found: contracts/missing.yaml");

        assertEquals("Contract not found: contracts/missing.yaml", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    void constructor_withMessageAndCause_setsBoth() {
        var cause = new RuntimeException("underlying IO error");
        var ex = new SchemaContractException("Failed to load contract", cause);

        assertEquals("Failed to load contract", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void constructor_withoutMessage_nullMessage() {
        var ex = new SchemaContractException((String) null);

        assertNull(ex.getMessage());
    }

    @Test
    void constructor_emptyMessage_emptyButNotNull() {
        var ex = new SchemaContractException("");

        assertEquals("", ex.getMessage());
        assertNotNull(ex.getMessage());
    }

    @Test
    void isRuntimeException_noCheckedException() {
        SchemaContractException ex = new SchemaContractException("test");
        assertInstanceOf(RuntimeException.class, ex);
    }

    @Test
    void constructor_withCauseOnly() {
        var cause = new IllegalArgumentException("invalid schema");
        var ex = new SchemaContractException(null, cause);

        assertNull(ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    void constructor_withMessageAndNullCause() {
        var ex = new SchemaContractException("Error occurred", null);

        assertEquals("Error occurred", ex.getMessage());
        assertNull(ex.getCause());
    }

    @Test
    void exception_canBeThrown() {
        SchemaContractException ex = new SchemaContractException("test error");

        assertThrows(SchemaContractException.class, () -> {
            throw ex;
        });
    }

    @Test
    void exception_canBeCaught() {
        SchemaContractException ex = new SchemaContractException("test");

        try {
            throw ex;
        } catch (SchemaContractException caught) {
            assertEquals("test", caught.getMessage());
        }
    }

    @Test
    void exception_causeChain() {
        var root = new IOException("file not found");
        var wrapped = new SchemaContractException("Failed to parse contract", root);

        assertEquals("Failed to parse contract", wrapped.getMessage());
        assertInstanceOf(IOException.class, wrapped.getCause());
    }

    @Test
    void exception_nestedCauseChain() {
        var root = new IOException("disk error");
        var level1 = new SchemaContractException("IO failure", root);
        var level2 = new SchemaContractException("Contract load failed", level1);

        assertEquals("Contract load failed", level2.getMessage());
        assertSame(level1, level2.getCause());
        assertSame(root, level1.getCause());
    }

    @Test
    void exception_stackTraceCanBeGenerated() {
        var ex = new SchemaContractException("test error");

        // Should not throw
        StackTraceElement[] trace = ex.getStackTrace();
        assertNotNull(trace);
    }

    @Test
    void exception_toString_containsMessage() {
        var ex = new SchemaContractException("custom message");

        String str = ex.toString();
        assertTrue(str.contains("SchemaContractException"));
        assertTrue(str.contains("custom message"));
    }

    @Test
    void exception_toString_withCause() {
        var cause = new RuntimeException("root cause");
        var ex = new SchemaContractException("outer", cause);

        String str = ex.toString();
        assertTrue(str.contains("SchemaContractException"));
    }

    @Test
    void exception_multipleInstances_independent() {
        var ex1 = new SchemaContractException("error 1");
        var ex2 = new SchemaContractException("error 2");

        assertNotEquals(ex1.getMessage(), ex2.getMessage());
    }

    @Test
    void exception_withSpecialCharactersInMessage() {
        var ex = new SchemaContractException("Error @ line 42: <invalid> & 'quotes'");

        assertEquals("Error @ line 42: <invalid> & 'quotes'", ex.getMessage());
    }

    @Test
    void exception_longMessage() {
        String longMessage = "Contract validation failed: " +
                "expected field 'order_id' of type string, " +
                "expected field 'total' of type decimal, " +
                "expected field 'created_at' of type timestamp";
        var ex = new SchemaContractException(longMessage);

        assertEquals(longMessage, ex.getMessage());
        assertTrue(ex.getMessage().length() > 100);
    }

    @Test
    void exception_compatibleWithJavaExceptionHandling() {
        SchemaContractException ex = new SchemaContractException("test");

        // Should be catchable as RuntimeException
        try {
            throw ex;
        } catch (RuntimeException e) {
            assertEquals("test", e.getMessage());
        }

        // Should be catchable as Exception
        try {
            throw ex;
        } catch (Exception e) {
            assertEquals("test", e.getMessage());
        }

        // Should be catchable as Throwable
        try {
            throw ex;
        } catch (Throwable t) {
            assertEquals("test", t.getMessage());
        }
    }

    @Test
    void exception_causeLinkage() {
        var originalError = new Exception("original");
        var ex = new SchemaContractException("wrapper", originalError);

        Throwable current = ex;
        int depth = 0;
        while (current != null && depth < 10) {
            current = current.getCause();
            depth++;
        }
        assertEquals(2, depth, "Should traverse one level of cause chain (2 getCause calls to reach null)");
    }

    @Test
    void exception_messageFormattingOptions() {
        var ex1 = new SchemaContractException("Simple message");
        var ex2 = new SchemaContractException("Message: " + "concatenated");
        var ex3 = new SchemaContractException(String.format("Message with %s", "format"));

        assertEquals("Simple message", ex1.getMessage());
        assertEquals("Message: concatenated", ex2.getMessage());
        assertEquals("Message with format", ex3.getMessage());
    }
}
