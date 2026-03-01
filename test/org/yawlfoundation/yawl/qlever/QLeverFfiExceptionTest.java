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

package org.yawlfoundation.yawl.qlever;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for QLeverFfiException factory methods and constructors.
 *
 * @author YAWL Foundation
 * @since YAWL 6.0
 */
@DisplayName("QLever FFI Exception")
class QLeverFfiExceptionTest {

/**
 * Unit tests for QLeverFfiException factory methods and constructors.
 *
 * @author YAWL Foundation
 * @since YAWL 6.0
 */
@DisplayName("QLever FFI Exception")
class QLeverFfiExceptionTest {

    @Test
    @DisplayName("Should create exception with message")
    void shouldCreateWithMessage() {
        String message = "Test FFI error";
        QLeverFfiException ex = new QLeverFfiException(message);

        assertEquals(message, ex.getMessage(), "Message should match");
        assertNull(ex.getCause(), "Cause should be null");
    }

    @Test
    @DisplayName("Should create exception with empty message")
    void shouldCreateWithEmptyMessage() {
        String message = "";
        QLeverFfiException ex = new QLeverFfiException(message);

        assertEquals(message, ex.getMessage(), "Message should be empty");
        assertNull(ex.getCause(), "Cause should be null");
    }

    @Test
    @DisplayName("Should create exception with message and cause")
    void shouldCreateWithMessageAndCause() {
        String message = "Test FFI error with cause";
        Throwable cause = new RuntimeException("Underlying error");
        QLeverFfiException ex = new QLeverFfiException(message, cause);

        assertEquals(message, ex.getMessage(), "Message should match");
        assertSame(cause, ex.getCause(), "Cause should match");
    }

    @Test
    @DisplayName("Should create exception with null cause")
    void shouldCreateWithNullCause() {
        String message = "Test FFI error";
        QLeverFfiException ex = new QLeverFfiException(message, null);

        assertEquals(message, ex.getMessage(), "Message should match");
        assertNull(ex.getCause(), "Cause should be null");
    }

    @Test
    @DisplayName("Should create index load failed exception")
    void shouldCreateIndexLoadFailedException() {
        String indexPath = "/var/lib/qlever/test-index";
        Throwable cause = new RuntimeException("File not found");
        QLeverFfiException ex = QLeverFfiException.indexLoadFailed(indexPath, cause);

        assertTrue(
            ex.getMessage().contains(indexPath),
            "Message should contain index path: " + ex.getMessage()
        );
        assertTrue(
            ex.getMessage().toLowerCase().contains("failed"),
            "Message should mention failure: " + ex.getMessage()
        );
        assertSame(cause, ex.getCause(), "Cause should be preserved");
    }

    @Test
    @DisplayName("Should create index load failed exception with null cause")
    void shouldCreateIndexLoadFailedExceptionWithNullCause() {
        String indexPath = "/var/lib/qlever/test-index";
        QLeverFfiException ex = QLeverFfiException.indexLoadFailed(indexPath, null);

        assertTrue(
            ex.getMessage().contains(indexPath),
            "Message should contain index path: " + ex.getMessage()
        );
        assertTrue(
            ex.getMessage().toLowerCase().contains("failed"),
            "Message should mention failure: " + ex.getMessage()
        );
        assertNull(ex.getCause(), "Cause should be null");
    }

    @Test
    @DisplayName("Should create library load failed exception")
    void shouldCreateLibraryLoadFailedException() {
        String libraryName = "qlever_ffi";
        Throwable cause = new UnsatisfiedLinkError("Library not found");
        QLeverFfiException ex = QLeverFfiException.libraryLoadFailed(libraryName, cause);

        assertTrue(
            ex.getMessage().contains(libraryName),
            "Message should contain library name: " + ex.getMessage()
        );
        assertTrue(
            ex.getMessage().toLowerCase().contains("load"),
            "Message should mention loading: " + ex.getMessage()
        );
        assertSame(cause, ex.getCause(), "Cause should be preserved");
    }

    @Test
    @DisplayName("Should create library load failed exception with different library names")
    void shouldCreateLibraryLoadFailedExceptionWithDifferentLibraryNames() {
        String[] libraryNames = {"qlever_ffi", "libqlever_ffi", "qlever"};

        for (String libraryName : libraryNames) {
            Throwable cause = new UnsatisfiedLinkError("Library not found");
            QLeverFfiException ex = QLeverFfiException.libraryLoadFailed(libraryName, cause);

            assertTrue(
                ex.getMessage().contains(libraryName),
                "Message should contain library name '" + libraryName + "': " + ex.getMessage()
            );
            assertSame(cause, ex.getCause(), "Cause should be preserved");
        }
    }

    @Test
    @DisplayName("Should create query failed exception")
    void shouldCreateQueryFailedException() {
        String query = "SELECT * WHERE { ?s ?p ?o }";
        String nativeError = "Parse error at line 1";
        QLeverFfiException ex = QLeverFfiException.queryFailed(query, nativeError);

        assertTrue(
            ex.getMessage().toLowerCase().contains("query"),
            "Message should mention query: " + ex.getMessage()
        );
        assertTrue(
            ex.getMessage().contains(nativeError),
            "Message should contain native error: " + ex.getMessage()
        );
        assertNull(ex.getCause(), "Cause should be null for query failed");
    }

    @Test
    @DisplayName("Should create query failed exception with different error types")
    void shouldCreateQueryFailedExceptionWithDifferentErrorTypes() {
        String query = "SELECT * WHERE { ?s ?p ?o }";
        String[] errorTypes = {
            "Parse error at line 1",
            "Semantic error: unknown predicate",
            "Syntax error near 'WHERE'",
            "Resource not found"
        };

        for (String error : errorTypes) {
            QLeverFfiException ex = QLeverFfiException.queryFailed(query, error);

            assertTrue(
                ex.getMessage().toLowerCase().contains("query"),
                "Message should mention query: " + ex.getMessage()
            );
            assertTrue(
                ex.getMessage().contains(error),
                "Message should contain native error: " + ex.getMessage()
            );
        }
    }

    @Test
    @DisplayName("Should truncate long queries in error message")
    void shouldTruncateLongQueriesInErrorMessage() {
        // Create a query longer than 100 characters
        StringBuilder sb = new StringBuilder("SELECT * WHERE { ");
        for (int i = 0; i < 20; i++) {
            sb.append("?s").append(i).append(" ?p").append(i).append(" ?o").append(i).append(" . ");
        }
        sb.append("}");
        String longQuery = sb.toString();

        assertTrue(
            longQuery.length() > 100,
            "Query should be longer than 100 chars for this test"
        );

        QLeverFfiException ex = QLeverFfiException.queryFailed(longQuery, "Test error");

        // The message should contain a truncated version with "..."
        assertTrue(
            ex.getMessage().contains("..."),
            "Message should contain ellipsis for truncated query: " + ex.getMessage()
        );
    }

    @Test
    @DisplayName("Should be a RuntimeException")
    void shouldBeRuntimeException() {
        QLeverFfiException ex = new QLeverFfiException("Test");

        assertTrue(
            ex instanceof RuntimeException,
            "QLeverFfiException should be a RuntimeException"
        );
    }

    @Test
    @DisplayName("Should have correct exception hierarchy")
    void shouldHaveCorrectExceptionHierarchy() {
        QLeverFfiException ex = new QLeverFfiException("Test");

        // Should extend RuntimeException
        assertTrue(ex instanceof RuntimeException, "Should be RuntimeException");
        assertTrue(ex instanceof Throwable, "Should be Throwable");

        // Should not be checked exceptions
        assertFalse(ex instanceof Exception, "Should not be checked Exception");
    }

    @Test
    @DisplayName("Should preserve all factory method properties")
    void shouldPreserveAllFactoryMethodProperties() {
        String indexPath = "/test/path";
        String libraryName = "test_lib";
        String query = "SELECT * WHERE { ?s ?p ?o }";
        String nativeError = "Test error";
        Throwable cause = new RuntimeException("Test cause");

        // Test index load failed
        QLeverFfiException indexEx = QLeverFfiException.indexLoadFailed(indexPath, cause);
        assertTrue(indexEx.getMessage().contains(indexPath));
        assertSame(cause, indexEx.getCause());

        // Test library load failed
        QLeverFfiException libEx = QLeverFfiException.libraryLoadFailed(libraryName, cause);
        assertTrue(libEx.getMessage().contains(libraryName));
        assertSame(cause, libEx.getCause());

        // Test query failed
        QLeverFfiException queryEx = QLeverFfiException.queryFailed(query, nativeError);
        assertTrue(queryEx.getMessage().contains(query.substring(0, 20)));
        assertTrue(queryEx.getMessage().contains(nativeError));
        assertNull(queryEx.getCause());
    }
}
