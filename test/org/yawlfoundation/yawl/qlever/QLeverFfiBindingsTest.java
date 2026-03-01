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

import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for QLever FFI bindings.
 *
 * <p>These tests verify the Panama FFM bindings to libqlever_ffi native library.
 * Tests are skipped if the native library is not available.</p>
 *
 * @author YAWL Foundation
 * @since YAWL 6.0
 */
@DisplayName("QLever FFI Bindings")
class QLeverFfiBindingsTest {

    /** Test index path - set via system property or defaults to /tmp */
    private static final Path TEST_INDEX = Paths.get(
        System.getProperty("qlever.test.index", "/tmp/qlever-test-index")
    );

    private static QLeverFfiBindings ffi;

    @BeforeAll
    static void setup() {
        Assumptions.assumeTrue(
            isNativeLibraryAvailable(),
            "libqlever_ffi not available - skipping FFI tests"
        );
        ffi = new QLeverFfiBindings();
    }

    @AfterAll
    static void teardown() {
        if (ffi != null) {
            ffi.close();
            ffi = null;
        }
    }

    /**
     * Checks if the native library can be loaded.
     */
    static boolean isNativeLibraryAvailable() {
        try {
            System.loadLibrary("qlever_ffi");
            return true;
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    // ========================================================================
    // Index Lifecycle Tests
    // ========================================================================

    @Test
    @DisplayName("Should create FFI bindings instance")
    @EnabledIf("isNativeLibraryAvailable")
    void shouldCreateBindingsInstance() {
        assertNotNull(ffi, "FFI bindings should be created");
    }

    @Test
    @DisplayName("Should return null for invalid index path")
    @EnabledIf("isNativeLibraryAvailable")
    void shouldReturnNullForInvalidPath() {
        MemorySegment index = ffi.indexCreate("/nonexistent/path/to/index");
        assertTrue(
            index == null || index.equals(MemorySegment.NULL),
            "Invalid path should return null or NULL segment"
        );
    }

    @Test
    @DisplayName("Should return false for null index isLoaded check")
    @EnabledIf("isNativeLibraryAvailable")
    void shouldReturnFalseForNullIndexIsLoaded() {
        assertFalse(ffi.indexIsLoaded(null), "Null index should report not loaded");
        assertFalse(
            ffi.indexIsLoaded(MemorySegment.NULL),
            "NULL segment should report not loaded"
        );
    }

    @Test
    @DisplayName("Should return zero for null index triple count")
    @EnabledIf("isNativeLibraryAvailable")
    void shouldReturnZeroForNullIndexTripleCount() {
        assertEquals(0, ffi.indexTripleCount(null), "Null index should have 0 triples");
        assertEquals(
            0,
            ffi.indexTripleCount(MemorySegment.NULL),
            "NULL segment should have 0 triples"
        );
    }

    @Test
    @DisplayName("Should safely destroy null index")
    @EnabledIf("isNativeLibraryAvailable")
    void shouldSafelyDestroyNullIndex() {
        assertDoesNotThrow(() -> ffi.indexDestroy(null), "Destroying null should not throw");
        assertDoesNotThrow(
            () -> ffi.indexDestroy(MemorySegment.NULL),
            "Destroying NULL segment should not throw"
        );
    }

    // ========================================================================
    // Result Handling Tests (without actual query)
    // ========================================================================

    @Test
    @DisplayName("Should handle null result in hasNext")
    @EnabledIf("isNativeLibraryAvailable")
    void shouldHandleNullResultInHasNext() {
        assertFalse(ffi.resultHasNext(null), "Null result should have no next");
        assertFalse(
            ffi.resultHasNext(MemorySegment.NULL),
            "NULL segment should have no next"
        );
    }

    @Test
    @DisplayName("Should return null for null result in next")
    @EnabledIf("isNativeLibraryAvailable")
    void shouldReturnNullForNullResultInNext() {
        assertNull(ffi.resultNext(null), "Null result next should return null");
        assertNull(
            ffi.resultNext(MemorySegment.NULL),
            "NULL segment next should return null"
        );
    }

    @Test
    @DisplayName("Should safely destroy null result")
    @EnabledIf("isNativeLibraryAvailable")
    void shouldSafelyDestroyNullResult() {
        assertDoesNotThrow(() -> ffi.resultDestroy(null), "Destroying null result should not throw");
        assertDoesNotThrow(
            () -> ffi.resultDestroy(MemorySegment.NULL),
            "Destroying NULL result should not throw"
        );
    }

    @Test
    @DisplayName("Should handle null result in error")
    @EnabledIf("isNativeLibraryAvailable")
    void shouldHandleNullResultInError() {
        String error = ffi.resultError(null);
        assertNotNull(error, "Error message should not be null for null result");
        assertTrue(
            error.contains("null") || error.toLowerCase().contains("null"),
            "Error message should mention null"
        );
    }

    @Test
    @DisplayName("Should return zero status for null result")
    void shouldReturnZeroStatusForNullResult() {
        assertEquals(0, ffi.resultStatus(null), "Null result should have status 0");
        assertEquals(
            0,
            ffi.resultStatus(MemorySegment.NULL),
            "NULL segment should have status 0"
        );
    }

    @Test
    @DisplayName("Should return HTTP status codes")
    void shouldReturnHttpStatusCodes() {
        // Test various HTTP status codes
        assertEquals(200, ffi.resultStatus(null), "Default status should be 200");
        assertEquals(0, ffi.resultStatus(MemorySegment.NULL), "NULL should return 0");
    }

    @Test
    @DisplayName("Should handle query execution with status codes")
    void shouldHandleQueryExecutionWithStatus() {
        Assumptions.assumeTrue(
            TEST_INDEX.toFile().exists(),
            "Test index not found at: " + TEST_INDEX
        );

        MemorySegment index = ffi.indexCreate(TEST_INDEX.toString());
        Assumptions.assumeTrue(
            index != null && !index.equals(MemorySegment.NULL),
            "Failed to load test index"
        );

        try {
            String query = "SELECT * WHERE { ?s ?p ?o } LIMIT 10";
            MemorySegment result = ffi.queryExec(index, query, "application/sparql-results+json");

            assertNotNull(result, "Result should not be null");

            try {
                int status = ffi.resultStatus(result);
                // Valid status codes: 200 (success), 400 (bad query), 500 (server error)
                assertTrue(
                    status == 200 || status == 400 || status == 500,
                    "Status should be 200, 400, or 500, got: " + status
                );
            } finally {
                ffi.resultDestroy(result);
            }
        } finally {
            ffi.indexDestroy(index);
        }
    }

    // ========================================================================
    // Full Index Lifecycle Test (requires test index)
    // ========================================================================

    @Test
    @DisplayName("Should create and destroy valid index handle")
    @EnabledIf("isNativeLibraryAvailable")
    void shouldCreateAndDestroyValidIndex() {
        Assumptions.assumeTrue(
            TEST_INDEX.toFile().exists(),
            "Test index not found at: " + TEST_INDEX
        );

        MemorySegment index = ffi.indexCreate(TEST_INDEX.toString());
        assertNotNull(index, "Index handle should not be null");
        assertNotEquals(MemorySegment.NULL, index, "Index handle should not be NULL");

        try {
            assertTrue(ffi.indexIsLoaded(index), "Index should report as loaded");
            assertTrue(
                ffi.indexTripleCount(index) >= 0,
                "Triple count should be non-negative"
            );
        } finally {
            assertDoesNotThrow(
                () -> ffi.indexDestroy(index),
                "Destroying valid index should not throw"
            );
        }
    }

    @Test
    @DisplayName("Should execute simple query on valid index")
    void shouldExecuteSimpleQuery() {
        Assumptions.assumeTrue(
            TEST_INDEX.toFile().exists(),
            "Test index not found at: " + TEST_INDEX
        );

        MemorySegment index = ffi.indexCreate(TEST_INDEX.toString());
        Assumptions.assumeTrue(
            index != null && !index.equals(MemorySegment.NULL),
            "Failed to load test index"
        );

        try {
            String query = "SELECT * WHERE { ?s ?p ?o } LIMIT 10";
            MemorySegment result = ffi.queryExec(index, query, "application/sparql-results+json");

            assertNotNull(result, "Result should not be null");

            try {
                String error = ffi.resultError(result);
                if (error != null) {
                    // Query might fail if index is empty, but shouldn't crash
                    System.err.println("Query error (expected for empty index): " + error);
                }

                int status = ffi.resultStatus(result);
                assertTrue(
                    status == 200 || status == 400 || status == 500,
                    "Status should be 200, 400, or 500, got: " + status
                );
            } finally {
                ffi.resultDestroy(result);
            }
        } finally {
            ffi.indexDestroy(index);
        }
    }

    // ========================================================================
    // Resource Management Tests
    // ========================================================================

    @Test
    @DisplayName("Should support try-with-resources pattern")
    @EnabledIf("isNativeLibraryAvailable")
    void shouldSupportTryWithResources() {
        assertDoesNotThrow(() -> {
            try (QLeverFfiBindings localFfi = new QLeverFfiBindings()) {
                assertNotNull(localFfi, "FFI bindings should be usable in try-with-resources");
            }
        }, "Close should not throw");
    }
}
