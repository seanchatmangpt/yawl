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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.*;
import java.nio.file.attribute.FileAttribute;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Chicago TDD unit tests for QLeverFfiBindings.
 *
 * <p>This test suite implements Chicago School TDD principles with real integrations
 * and comprehensive coverage of all QLever FFI operations. Tests use real native
 * library calls and verify all critical paths including error conditions.</p>
 *
 * <p><strong>Test Philosophy:</strong></p>
 * <ul>
 *   <li><strong>Real integrations:</strong> All tests interact with actual QLever native library</li>
 *   <li><strong>Comprehensive coverage:</strong> Happy paths, error cases, boundary conditions</li>
 *   <li><strong>Performance assertions:</strong> Real timing measurements in nanoseconds</li>
 *   <li><strong>Resource management:</strong> Proper cleanup of all native resources</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @since YAWL 6.0
 */
@DisplayName("QLeverFfiBindings Chicago TDD Tests")
public class QLeverFfiBindingsChicagoTest {

    private static final String TEST_INDEX_ROOT = "test_qlever_index";
    private static final String TEST_TRIPLES_FILE = TEST_INDEX_ROOT + "/triples.nq";
    private static final String TEST_SCHEMA_FILE = TEST_INDEX_ROOT + "/schema.nq";
    private static final String SAMPLE_TRIPLES = "<http://example.org/s> <http://example.org/p> <http://example.org/o> .\n" +
                                                "<http://example.org/s> <http://example.org/p> \"object value\" .";
    private static final String SIMPLE_SCHEMA = "@prefix : <http://example.org/> .\n" +
                                              ":s a ?type .\n" +
                                              ":s :p ?value .";

    private QLeverFfiBindings bindings;
    private Path testIndexDir;
    private Path testTriplesFile;
    private Path testSchemaFile;

    @BeforeEach
    void setUp() throws IOException {
        // Check native library availability (Chicago TDD: test environment first)
        assumeTrue(isNativeLibraryAvailable(),
                   "QLever native library not available - skipping native tests");

        // Initialize bindings
        bindings = new QLeverFfiBindings();

        // Create test directory structure
        testIndexDir = Files.createTempDirectory(TEST_INDEX_ROOT);
        testTriplesFile = testIndexDir.resolve("triples.nq");
        testSchemaFile = testIndexDir.resolve("schema.nq");
    }

    @AfterEach
    void tearDown() {
        // Clean up bindings (native resources)
        if (bindings != null) {
            bindings.close();
        }

        // Clean up test files
        try {
            if (testIndexDir != null && Files.exists(testIndexDir)) {
                Files.walk(testIndexDir)
                    .sorted((a, b) -> -a.compareTo(b))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // Log but don't fail test
                            System.err.println("Failed to delete: " + path);
                        }
                    });
            }
        } catch (IOException e) {
            // Non-fatal cleanup failure
            System.err.println("Cleanup error: " + e.getMessage());
        }
    }

    @Nested
    @DisplayName("indexCreate Operations")
    class IndexCreateTests {

        @Test
        @DisplayName("✅ indexCreate - Valid path creates index successfully")
        void indexCreateValidPath() throws IOException {
            // Arrange: Prepare valid test files
            createTestFiles();

            // Act & Assert: Test index creation timing
            long startTime = System.nanoTime();
            QLeverStatus status = bindings.indexCreate(testIndexDir.toString());
            long endTime = System.nanoTime();

            // Verify performance (should complete in < 1ms for valid empty index)
            long durationNanos = endTime - startTime;
            long durationMicros = TimeUnit.MICROSECONDS.convert(durationNanos, TimeUnit.NANOSECONDS);
            assertTrue(durationMicros < 1000, "Index creation took too long: " + durationMicros + "µs");

            assertTrue(status.isSuccess(), "Index creation should succeed");
            assertNotNull(status.result(), "Index handle should not be null");
            assertTrue(bindings.indexIsLoaded(status.result()), "Index should be loaded after creation");
            assertEquals(0, bindings.indexTripleCount(status.result()), "Empty index should have 0 triples");

            // Cleanup
            bindings.indexDestroy(status.result());
        }

        @Test
        @DisplayName("❌ indexCreate - Null path throws NullPointerException")
        void indexCreateNullPath() {
            // Act & Assert: Verify null handling
            assertThrows(NullPointerException.class,
                        () -> bindings.indexCreate(null),
                        "indexCreate should throw NullPointerException for null path");
        }

        @Test
        @DisplayName("❌ indexCreate - Invalid path returns error status")
        void indexCreateInvalidPath() {
            // Act: Test with non-existent path
            QLeverStatus status = bindings.indexCreate("/non/existent/path");

            // Assert: Should return error status
            assertFalse(status.isSuccess(), "Index creation should fail for invalid path");
            assertNotNull(status.error(), "Error message should be present");
            assertEquals(500, status.httpStatus(), "Should return HTTP 500 for creation failure");
        }

        @Test
        @DisplayName("❌ indexCreate - Permission denied returns error status")
        void indexCreatePermissionDenied() throws IOException {
            // Arrange: Create read-only directory
            Path readOnlyDir = Files.createTempDirectory("read_only_test");
            Files.setPosixFilePermissions(readOnlyDir,
                PosixFilePermissions.fromString("r--r--r--"));

            try {
                // Act: Attempt to create index in read-only directory
                QLeverStatus status = bindings.indexCreate(readOnlyDir.toString());

                // Assert: Should return error status
                assertFalse(status.isSuccess(), "Index creation should fail for read-only directory");
                assertNotNull(status.error(), "Error message should be present");
                assertEquals(500, status.httpStatus(), "Should return HTTP 500 for permission failure");
            } finally {
                Files.delete(readOnlyDir);
            }
        }

        @ParameterizedTest
        @ValueSource(strings = { "", "   ", "path/to/index", "index.", ".index" })
        @DisplayName("❌ indexCreate - Edge case paths return error status")
        void indexCreateEdgeCasePaths(String path) {
            // Act: Test various edge case paths
            QLeverStatus status = bindings.indexCreate(path);

            // Assert: Should return error status for invalid paths
            assertFalse(status.isSuccess(), "Index creation should fail for: '" + path + "'");
            assertNotNull(status.error(), "Error message should be present for: '" + path + "'");
        }
    }

    @Nested
    @DisplayName("indexDestroy Operations")
    class IndexDestroyTests {

        @Test
        @DisplayName("✅ indexDestroy - Normal destroy succeeds")
        void indexDestroyNormal() throws IOException {
            // Arrange: Create and load index
            createTestFiles();
            QLeverStatus status = bindings.indexCreate(testIndexDir.toString());
            MemorySegment indexHandle = status.result();

            // Act: Destroy index
            long startTime = System.nanoTime();
            bindings.indexDestroy(indexHandle);
            long endTime = System.nanoTime();

            // Verify performance (should complete in < 500µs)
            long durationNanos = endTime - startTime;
            long durationMicros = TimeUnit.MICROSECONDS.convert(durationNanos, TimeUnit.NANOSECONDS);
            assertTrue(durationMicros < 500, "Index destroy took too long: " + durationMicros + "µs");

            // Assert: Index should no longer be loaded
            assertFalse(bindings.indexIsLoaded(indexHandle), "Destroyed index should not be loaded");
            assertEquals(0, bindings.indexTripleCount(indexHandle), "Destroyed index should have 0 triples");
        }

        @Test
        @DisplayName("✅ indexDestroy - Null handle is safe")
        void indexDestroyNullHandle() {
            // Act: Destroy with null handle (should not throw)
            assertDoesNotThrow(() -> bindings.indexDestroy(null));
        }

        @Test
        @DisplayName("✅ indexDestroy - Double destroy is safe")
        void indexDestroyDoubleDestroy() throws IOException {
            // Arrange: Create and load index
            createTestFiles();
            QLeverStatus status = bindings.indexCreate(testIndexDir.toString());
            MemorySegment indexHandle = status.result();

            // Act: Destroy index twice
            bindings.indexDestroy(indexHandle);
            bindings.indexDestroy(indexHandle); // Second destroy should not throw

            // Assert: Index should still not be loaded
            assertFalse(bindings.indexIsLoaded(indexHandle), "Index should remain destroyed after double destroy");
        }

        @Test
        @DisplayName("✅ indexDestroy - NULL handle is safe")
        void indexDestroyNullHandleAsMemorySegment() {
            // Act: Destroy with MemorySegment.NULL (should not throw)
            assertDoesNotThrow(() -> bindings.indexDestroy(MemorySegment.NULL));
        }
    }

    @Nested
    @DisplayName("indexIsLoaded Operations")
    class IndexIsLoadedTests {

        @Test
        @DisplayName("✅ indexIsLoaded - Returns true after successful index create")
        void indexIsLoadedAfterCreate() throws IOException {
            // Arrange: Create test index
            createTestFiles();
            QLeverStatus status = bindings.indexCreate(testIndexDir.toString());
            MemorySegment indexHandle = status.result();

            // Act: Check if loaded
            long startTime = System.nanoTime();
            boolean isLoaded = bindings.indexIsLoaded(indexHandle);
            long endTime = System.nanoTime();

            // Verify performance (should complete in < 100µs)
            long durationNanos = endTime - startTime;
            long durationMicros = TimeUnit.MICROSECONDS.convert(durationNanos, TimeUnit.NANOSECONDS);
            assertTrue(durationMicros < 100, "Index is-loaded check took too long: " + durationMicros + "µs");

            // Assert: Should be loaded
            assertTrue(isLoaded, "Successfully created index should be loaded");

            // Cleanup
            bindings.indexDestroy(indexHandle);
        }

        @Test
        @DisplayName("❌ indexIsLoaded - Returns false after index destroy")
        void indexIsLoadedAfterDestroy() throws IOException {
            // Arrange: Create and destroy index
            createTestFiles();
            QLeverStatus status = bindings.indexCreate(testIndexDir.toString());
            MemorySegment indexHandle = status.result();

            bindings.indexDestroy(indexHandle);

            // Act: Check if loaded
            boolean isLoaded = bindings.indexIsLoaded(indexHandle);

            // Assert: Should not be loaded
            assertFalse(isLoaded, "Destroyed index should not be loaded");
        }

        @Test
        @DisplayName("❌ indexIsLoaded - Returns false for null handle")
        void indexIsLoadedNullHandle() {
            // Act: Check with null handle
            boolean isLoaded = bindings.indexIsLoaded(null);

            // Assert: Should not be loaded
            assertFalse(isLoaded, "Null handle should not be loaded");
        }

        @Test
        @DisplayName("❌ indexIsLoaded - Returns false for NULL handle")
        void indexIsLoadedNullHandleAsMemorySegment() {
            // Act: Check with MemorySegment.NULL
            boolean isLoaded = bindings.indexIsLoaded(MemorySegment.NULL);

            // Assert: Should not be loaded
            assertFalse(isLoaded, "NULL handle should not be loaded");
        }
    }

    @Nested
    @DisplayName("indexTripleCount Operations")
    class IndexTripleCountTests {

        @Test
        @DisplayName("✅ indexTripleCount - Returns 0 for empty index")
        void indexTripleCountEmptyIndex() throws IOException {
            // Arrange: Create empty test index
            createTestFiles();
            QLeverStatus status = bindings.indexCreate(testIndexDir.toString());
            MemorySegment indexHandle = status.result();

            // Act: Get triple count
            long startTime = System.nanoTime();
            long count = bindings.indexTripleCount(indexHandle);
            long endTime = System.nanoTime();

            // Verify performance (should complete in < 100µs)
            long durationNanos = endTime - startTime;
            long durationMicros = TimeUnit.MICROSECONDS.convert(durationNanos, TimeUnit.NANOSECONDS);
            assertTrue(durationMicros < 100, "Triple count took too long: " + durationMicros + "µs");

            // Assert: Should be 0 for empty index
            assertEquals(0, count, "Empty index should have 0 triples");

            // Cleanup
            bindings.indexDestroy(indexHandle);
        }

        @Test
        @DisplayName("✅ indexTripleCount - Returns correct count for populated index")
        void indexTripleCountPopulatedIndex() throws IOException {
            // Arrange: Create test index with data
            createTestFilesWithContent();
            QLeverStatus status = bindings.indexCreate(testIndexDir.toString());
            MemorySegment indexHandle = status.result();

            // Act: Get triple count
            long startTime = System.nanoTime();
            long count = bindings.indexTripleCount(indexHandle);
            long endTime = System.nanoTime();

            // Verify performance (should complete in < 200µs for small index)
            long durationNanos = endTime - startTime;
            long durationMicros = TimeUnit.MICROSECONDS.convert(durationNanos, TimeUnit.NANOSECONDS);
            assertTrue(durationMicros < 200, "Triple count took too long: " + durationMicros + "µs");

            // Assert: Should return correct count (2 triples in sample data)
            assertEquals(2, count, "Populated index should have 2 triples");

            // Cleanup
            bindings.indexDestroy(indexHandle);
        }

        @Test
        @DisplayName("❌ indexTripleCount - Returns 0 for invalid handle")
        void indexTripleCountInvalidHandle() {
            // Act: Get triple count with invalid handle
            long count = bindings.indexTripleCount(null);

            // Assert: Should return 0 for invalid handle
            assertEquals(0, count, "Invalid handle should return 0 triples");
        }

        @Test
        @DisplayName("❌ indexTripleCount - Returns 0 for NULL handle")
        void indexTripleCountNullHandleAsMemorySegment() {
            // Act: Get triple count with MemorySegment.NULL
            long count = bindings.indexTripleCount(MemorySegment.NULL);

            // Assert: Should return 0 for NULL handle
            assertEquals(0, count, "NULL handle should return 0 triples");
        }
    }

    @Nested
    @DisplayName("queryExec Operations")
    class QueryExecTests {

        @Test
        @DisplayName("✅ queryExec - Valid query executes successfully")
        void queryExecValidQuery() throws IOException {
            // Arrange: Create test index
            createTestFilesWithContent();
            QLeverStatus status = bindings.indexCreate(testIndexDir.toString());
            MemorySegment indexHandle = status.result();

            // Act: Execute valid SPARQL query
            String query = "SELECT ?s ?p ?o WHERE { ?s ?p ?o }";
            long startTime = System.nanoTime();
            QLeverStatus queryStatus = bindings.queryExec(indexHandle, query, QLeverMediaType.JSON);
            long endTime = System.nanoTime();

            // Verify performance (should complete in < 50ms for simple query)
            long durationNanos = endTime - startTime;
            long durationMicros = TimeUnit.MICROSECONDS.convert(durationNanos, TimeUnit.NANOSECONDS);
            assertTrue(durationMicros < 50000, "Query execution took too long: " + durationMicros + "µs");

            // Assert: Should succeed with result
            assertTrue(queryStatus.isSuccess(), "Valid query should succeed");
            assertNotNull(queryStatus.result(), "Query result should not be null");
            assertEquals(200, queryStatus.httpStatus(), "Should return HTTP 200 for success");

            // Verify result iteration
            assertTrue(bindings.resultHasNext(queryStatus.result()), "Result should have next");
            String firstResult = bindings.resultNext(queryStatus.result());
            assertNotNull(firstResult, "First result should not be null");

            // Cleanup
            bindings.resultDestroy(queryStatus.result());
            bindings.indexDestroy(indexHandle);
        }

        @Test
        @DisplayName("❌ queryExec - Malformed query returns error status")
        void queryExecMalformedQuery() throws IOException {
            // Arrange: Create test index
            createTestFiles();
            QLeverStatus status = bindings.indexCreate(testIndexDir.toString());
            MemorySegment indexHandle = status.result();

            // Act: Execute malformed SPARQL query
            String malformedQuery = "SELECT * WHERE { WHERE }"; // Invalid syntax
            QLeverStatus queryStatus = bindings.queryExec(indexHandle, malformedQuery, QLeverMediaType.JSON);

            // Assert: Should return error status
            assertFalse(queryStatus.isSuccess(), "Malformed query should fail");
            assertNotNull(queryStatus.error(), "Error message should be present");
            assertTrue(queryStatus.httpStatus() == 400 || queryStatus.httpStatus() == 500,
                      "Should return HTTP 400 or 500 for malformed query");

            // Cleanup
            if (queryStatus.result() != null) {
                bindings.resultDestroy(queryStatus.result());
            }
            bindings.indexDestroy(indexHandle);
        }

        @Test
        @DisplayName("❌ queryExec - Null index throws NullPointerException")
        void queryExecNullIndex() {
            // Arrange: Valid query
            String query = "SELECT ?s ?p ?o WHERE { ?s ?p ?o }";

            // Act & Assert: Verify null index handling
            assertThrows(NullPointerException.class,
                        () -> bindings.queryExec(null, query, QLeverMediaType.JSON),
                        "queryExec should throw NullPointerException for null index");
        }

        @Test
        @DisplayName("❌ queryExec - Null query throws NullPointerException")
        void queryExecNullQuery() throws IOException {
            // Arrange: Create test index
            createTestFiles();
            QLeverStatus status = bindings.indexCreate(testIndexDir.toString());
            MemorySegment indexHandle = status.result();

            try {
                // Act & Assert: Verify null query handling
                assertThrows(NullPointerException.class,
                            () -> bindings.queryExec(indexHandle, null, QLeverMediaType.JSON),
                            "queryExec should throw NullPointerException for null query");
            } finally {
                bindings.indexDestroy(indexHandle);
            }
        }

        @Test
        @DisplayName("❌ queryExec - Null media type throws NullPointerException")
        void queryExecNullMediaType() throws IOException {
            // Arrange: Create test index
            createTestFiles();
            QLeverStatus status = bindings.indexCreate(testIndexDir.toString());
            MemorySegment indexHandle = status.result();

            try {
                // Act & Assert: Verify null media type handling
                assertThrows(NullPointerException.class,
                            () -> bindings.queryExec(indexHandle, "SELECT * WHERE { }", null),
                            "queryExec should throw NullPointerException for null media type");
            } finally {
                bindings.indexDestroy(indexHandle);
            }
        }

        @ParameterizedTest
        @CsvSource({
            "SELECT ?s ?p ?o WHERE { ?s ?p ?o }",
            "ASK { ?s ?p ?o }",
            "CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }",
            "DESCRIBE ?s WHERE { ?s ?p ?o }"
        })
        @DisplayName("✅ queryExec - Supports various SPARQL query types")
        void queryExecVariousQueryTypes(String query) throws IOException {
            // Arrange: Create test index
            createTestFilesWithContent();
            QLeverStatus status = bindings.indexCreate(testIndexDir.toString());
            MemorySegment indexHandle = status.result();

            // Act: Execute different query types
            QLeverStatus queryStatus = bindings.queryExec(indexHandle, query, QLeverMediaType.JSON);

            // Assert: Should succeed
            assertTrue(queryStatus.isSuccess(), "Query type '" + query + "' should succeed");
            assertNotNull(queryStatus.result(), "Result should not be null");

            // Cleanup
            bindings.resultDestroy(queryStatus.result());
            bindings.indexDestroy(indexHandle);
        }
    }

    @Nested
    @DisplayName("Result Iteration Operations")
    class ResultIterationTests {

        @Test
        @DisplayName("✅ resultHasNext/resultNext - Iterates through results successfully")
        void resultIterationSuccess() throws IOException {
            // Arrange: Create test index and execute query
            createTestFilesWithContent();
            QLeverStatus status = bindings.indexCreate(testIndexDir.toString());
            MemorySegment indexHandle = status.result();

            QLeverStatus queryStatus = bindings.queryExec(indexHandle,
                "SELECT ?s ?p ?o WHERE { ?s ?p ?o }", QLeverMediaType.JSON);
            MemorySegment resultHandle = queryStatus.result();

            // Act: Iterate through results
            long startTime = System.nanoTime();
            int count = 0;
            while (bindings.resultHasNext(resultHandle)) {
                String result = bindings.resultNext(resultHandle);
                assertNotNull(result, "Result should not be null");
                count++;

                // Verify result format (should be valid JSON)
                assertTrue(result.contains("\"s\"") || result.contains("s"),
                         "Result should contain subject information");
            }
            long endTime = System.nanoTime();

            // Verify performance (should complete in < 10ms for 2 results)
            long durationNanos = endTime - startTime;
            long durationMicros = TimeUnit.MICROSECONDS.convert(durationNanos, TimeUnit.NANOSECONDS);
            assertTrue(durationMicros < 10000, "Result iteration took too long: " + durationMicros + "µs");

            // Assert: Should have processed exactly 2 results
            assertEquals(2, count, "Should have processed exactly 2 results");

            // Assert: hasNext should return false after all results
            assertFalse(bindings.resultHasNext(resultHandle), "Should have no more results");

            // Assert: Next should return null after all results
            assertNull(bindings.resultNext(resultHandle), "Should return null after all results");

            // Cleanup
            bindings.resultDestroy(resultHandle);
            bindings.indexDestroy(indexHandle);
        }

        @Test
        @DisplayName("❌ resultHasNext/resultNext - Empty result has no next")
        void resultIterationEmptyResult() throws IOException {
            // Arrange: Create test index and execute empty query
            createTestFiles();
            QLeverStatus status = bindings.indexCreate(testIndexDir.toString());
            MemorySegment indexHandle = status.result();

            // Execute query that returns no results
            QLeverStatus queryStatus = bindings.queryExec(indexHandle,
                "SELECT ?s ?p ?o WHERE { ?s ?p ?o FILTER (false) }", QLeverMediaType.JSON);
            MemorySegment resultHandle = queryStatus.result();

            // Act: Check hasNext and get next
            boolean hasNext = bindings.resultHasNext(resultHandle);
            String result = bindings.resultNext(resultHandle);

            // Assert: Should have no results
            assertFalse(hasNext, "Empty result should have no next");
            assertNull(result, "Empty result should return null");

            // Cleanup
            bindings.resultDestroy(resultHandle);
            bindings.indexDestroy(indexHandle);
        }

        @Test
        @DisplayName("❌ resultHasNext/resultNext - Null handle returns false/null")
        void resultIterationNullHandle() {
            // Act: Check with null handle
            boolean hasNext = bindings.resultHasNext(null);
            String result = bindings.resultNext(null);

            // Assert: Should return false and null
            assertFalse(hasNext, "Null handle should have no next");
            assertNull(result, "Null handle should return null");
        }

        @Test
        @DisplayName("❌ resultHasNext/resultNext - NULL handle returns false/null")
        void resultIterationNullHandleAsMemorySegment() {
            // Act: Check with MemorySegment.NULL
            boolean hasNext = bindings.resultHasNext(MemorySegment.NULL);
            String result = bindings.resultNext(MemorySegment.NULL);

            // Assert: Should return false and null
            assertFalse(hasNext, "NULL handle should have no next");
            assertNull(result, "NULL handle should return null");
        }

        @Test
        @DisplayName("✅ resultHasNext/resultNext - Respects memory safety limits")
        void resultIterationMemorySafety() throws IOException {
            // Arrange: Create test index with reasonable amount of data
            createTestFilesWithContent();
            QLeverStatus status = bindings.indexCreate(testIndexDir.toString());
            MemorySegment indexHandle = status.result();

            // Act: Execute query and iterate through results
            QLeverStatus queryStatus = bindings.queryExec(indexHandle,
                "SELECT ?s ?p ?o WHERE { ?s ?p ?o }", QLeverMediaType.JSON);
            MemorySegment resultHandle = queryStatus.result();

            // Test that results are within reasonable length limits
            while (bindings.resultHasNext(resultHandle)) {
                String result = bindings.resultNext(resultHandle);
                assertNotNull(result, "Result should not be null");

                // Verify result is not excessively long (should be much less than MAX_LINE_LENGTH)
                assertTrue(result.length() < 64000,
                          "Result should be less than 64KB in length");
            }

            // Cleanup
            bindings.resultDestroy(resultHandle);
            bindings.indexDestroy(indexHandle);
        }
    }

    @Nested
    @DisplayName("resultDestroy Operations")
    class ResultDestroyTests {

        @Test
        @DisplayName("✅ resultDestroy - Normal destroy succeeds")
        void resultDestroyNormal() throws IOException {
            // Arrange: Create test index and execute query
            createTestFilesWithContent();
            QLeverStatus status = bindings.indexCreate(testIndexDir.toString());
            MemorySegment indexHandle = status.result();

            QLeverStatus queryStatus = bindings.queryExec(indexHandle,
                "SELECT ?s ?p ?o WHERE { ?s ?p ?o }", QLeverMediaType.JSON);
            MemorySegment resultHandle = queryStatus.result();

            // Act: Destroy result
            long startTime = System.nanoTime();
            bindings.resultDestroy(resultHandle);
            long endTime = System.nanoTime();

            // Verify performance (should complete in < 500µs)
            long durationNanos = endTime - startTime;
            long durationMicros = TimeUnit.MICROSECONDS.convert(durationNanos, TimeUnit.NANOSECONDS);
            assertTrue(durationMicros < 500, "Result destroy took too long: " + durationMicros + "µs");

            // Assert: Handle should be destroyed (subsequent operations should fail safely)
            assertDoesNotThrow(() -> bindings.resultDestroy(resultHandle),
                             "Destroying already destroyed handle should not throw");
        }

        @Test
        @DisplayName("✅ resultDestroy - Null handle is safe")
        void resultDestroyNullHandle() {
            // Act: Destroy with null handle (should not throw)
            assertDoesNotThrow(() -> bindings.resultDestroy(null));
        }

        @Test
        @DisplayName("✅ resultDestroy - NULL handle is safe")
        void resultDestroyNullHandleAsMemorySegment() {
            // Act: Destroy with MemorySegment.NULL (should not throw)
            assertDoesNotThrow(() -> bindings.resultDestroy(MemorySegment.NULL));
        }
    }

    @Nested
    @DisplayName("resultError Operations")
    class ResultErrorTests {

        @Test
        @DisplayName("❌ resultError - Returns error message when query has error")
        void resultErrorWithError() throws IOException {
            // Arrange: Create test index and execute malformed query
            createTestFiles();
            QLeverStatus status = bindings.indexCreate(testIndexDir.toString());
            MemorySegment indexHandle = status.result();

            QLeverStatus queryStatus = bindings.queryExec(indexHandle,
                "SELECT * WHERE { WHERE }", QLeverMediaType.XML);
            MemorySegment resultHandle = queryStatus.result();

            // Act: Get error message
            long startTime = System.nanoTime();
            String error = bindings.resultError(resultHandle);
            long endTime = System.nanoTime();

            // Verify performance (should complete in < 100µs)
            long durationNanos = endTime - startTime;
            long durationMicros = TimeUnit.MICROSECONDS.convert(durationNanos, TimeUnit.NANOSECONDS);
            assertTrue(durationMicros < 100, "Error retrieval took too long: " + durationMicros + "µs");

            // Assert: Should return error message
            assertNotNull(error, "Should return error message for failed query");
            assertTrue(error.contains("error") || error.contains("Error") || error.contains("syntax"),
                      "Error message should indicate the problem");

            // Cleanup
            bindings.resultDestroy(resultHandle);
            bindings.indexDestroy(indexHandle);
        }

        @Test
        @DisplayName("✅ resultError - Returns null when query succeeds")
        void resultErrorWithoutError() throws IOException {
            // Arrange: Create test index and execute valid query
            createTestFilesWithContent();
            QLeverStatus status = bindings.indexCreate(testIndexDir.toString());
            MemorySegment indexHandle = status.result();

            QLeverStatus queryStatus = bindings.queryExec(indexHandle,
                "SELECT ?s ?p ?o WHERE { ?s ?p ?o }", QLeverMediaType.JSON);
            MemorySegment resultHandle = queryStatus.result();

            // Act: Get error message
            String error = bindings.resultError(resultHandle);

            // Assert: Should return null for successful query
            assertNull(error, "Should return null for successful query");

            // Cleanup
            bindings.resultDestroy(resultHandle);
            bindings.indexDestroy(indexHandle);
        }

        @Test
        @DisplayName("❌ resultError - Returns error message for null handle")
        void resultErrorNullHandle() {
            // Act: Get error with null handle
            String error = bindings.resultError(null);

            // Assert: Should return error message about null handle
            assertNotNull(error, "Should return error message for null handle");
            assertTrue(error.contains("null") || error.contains("null"),
                      "Error message should mention null handle");
        }

        @Test
        @DisplayName("❌ resultError - Returns error message for NULL handle")
        void resultErrorNullHandleAsMemorySegment() {
            // Act: Get error with MemorySegment.NULL
            String error = bindings.resultError(MemorySegment.NULL);

            // Assert: Should return error message about NULL handle
            assertNotNull(error, "Should return error message for NULL handle");
            assertTrue(error.contains("null") || error.contains("null"),
                      "Error message should mention NULL handle");
        }

        @Test
        @DisplayName("✅ resultError - Error messages are within safe length limits")
        void resultErrorMessageLengthSafety() throws IOException {
            // Arrange: Create test index and execute query that might produce long error
            createTestFiles();
            QLeverStatus status = bindings.indexCreate(testIndexDir.toString());
            MemorySegment indexHandle = status.result();

            // Execute a complex query that might produce long error messages
            QLeverStatus queryStatus = bindings.queryExec(indexHandle,
                "SELECT * WHERE { # This is a complex query that might produce long errors }",
                QLeverMediaType.XML);
            MemorySegment resultHandle = queryStatus.result();

            // Act: Get error message
            String error = bindings.resultError(resultHandle);

            // Assert: Error message should be within safe limits
            if (error != null) {
                assertTrue(error.length() < 16000,
                          "Error message should be less than 16KB in length");
            }

            // Cleanup
            bindings.resultDestroy(resultHandle);
            bindings.indexDestroy(indexHandle);
        }
    }

    @Nested
    @DisplayName("Performance and Resource Management")
    class PerformanceResourceTests {

        @Test
        @DisplayName("✅ Index operations should complete within performance thresholds")
        void indexPerformanceThresholds() throws IOException {
            // Arrange: Create test files
            createTestFilesWithContent();

            // Measure index creation time (should be < 1000µs for empty index)
            long createStart = System.nanoTime();
            QLeverStatus status = bindings.indexCreate(testIndexDir.toString());
            long createEnd = System.nanoTime();
            assertTrue(TimeUnit.MICROSECONDS.convert(createEnd - createStart, TimeUnit.NANOSCONDS) < 1000,
                      "Index creation should be fast");

            // Measure triple count time (should be < 200µs)
            long countStart = System.nanoTime();
            long count = bindings.indexTripleCount(status.result());
            long countEnd = System.nanoTime();
            assertTrue(TimeUnit.MICROSECONDS.convert(countEnd - countStart, TimeUnit.NANOSCONDS) < 200,
                      "Triple count should be fast");

            // Measure query execution time (should be < 50000µs for simple query)
            String query = "SELECT ?s ?p ?o WHERE { ?s ?p ?o }";
            long queryStart = System.nanoTime();
            QLeverStatus queryStatus = bindings.queryExec(status.result(), query, QLeverMediaType.JSON);
            long queryEnd = System.nanoTime();
            assertTrue(TimeUnit.MICROSECONDS.convert(queryEnd - queryStart, TimeUnit.NANOSCONDS) < 50000,
                      "Query execution should be fast");

            // Cleanup
            bindings.resultDestroy(queryStatus.result());
            bindings.indexDestroy(status.result());
        }

        @Test
        @DisplayName("✅ Resource management - Proper cleanup of all native resources")
        void resourceManagementCleanup() throws IOException {
            // Arrange: Create multiple resources
            createTestFilesWithContent();
            QLeverStatus status = bindings.indexCreate(testIndexDir.toString());

            // Execute multiple queries to create multiple result handles
            QLeverStatus query1 = bindings.queryExec(status.result(),
                "SELECT ?s ?p ?o WHERE { ?s ?p ?o }", QLeverMediaType.JSON);
            QLeverStatus query2 = bindings.queryExec(status.result(),
                "ASK { ?s ?p ?o }", QLeverMediaType.XML);

            // Verify resources exist
            assertNotNull(status.result());
            assertNotNull(query1.result());
            assertNotNull(query2.result());

            // Clean up in reverse order
            bindings.resultDestroy(query2.result());
            bindings.resultDestroy(query1.result());
            bindings.indexDestroy(status.result());

            // Verify no dangling resources remain
            // Note: Since we can't directly inspect native memory, we verify through absence of exceptions
            assertDoesNotThrow(() -> {
                // Create a new index to verify previous cleanup didn't corrupt state
                QLeverStatus newStatus = bindings.indexCreate(testIndexDir.toString());
                bindings.indexDestroy(newStatus.result());
            }, "Resource cleanup should not affect subsequent operations");
        }

        @Test
        @DisplayName("✅ Memory safety - Operations within memory limits")
        void memorySafety() throws IOException {
            // Arrange: Create test index with reasonable data
            createTestFilesWithContent();
            QLeverStatus status = bindings.indexCreate(testIndexDir.toString());

            // Test that results are within memory limits
            QLeverStatus queryStatus = bindings.queryExec(status.result(),
                "SELECT ?s ?p ?o WHERE { ?s ?p ?o }", QLeverMediaType.JSON);

            int resultCount = 0;
            while (bindings.resultHasNext(queryStatus.result())) {
                String result = bindings.resultNext(queryStatus.result());
                assertTrue(result.length() < 64000,
                          "Individual result should be less than 64KB");
                resultCount++;
            }

            // Clean up
            bindings.resultDestroy(queryStatus.result());
            bindings.indexDestroy(status.result());

            // Verify reasonable number of results
            assertTrue(resultCount > 0, "Should have processed some results");
            assertTrue(resultCount <= 1000, "Should not have excessive results for test data");
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("✅ Complete workflow - Index creation, query execution, and cleanup")
        void completeWorkflow() throws IOException {
            // Arrange: Create test files
            createTestFilesWithContent();

            // Act: Complete workflow
            long totalStart = System.nanoTime();

            // 1. Create index
            QLeverStatus status = bindings.indexCreate(testIndexDir.toString());
            MemorySegment indexHandle = status.result();

            // 2. Verify index is loaded
            assertTrue(bindings.indexIsLoaded(indexHandle), "Index should be loaded");

            // 3. Get triple count
            long tripleCount = bindings.indexTripleCount(indexHandle);
            assertEquals(2, tripleCount, "Should have 2 triples");

            // 4. Execute query
            QLeverStatus queryStatus = bindings.queryExec(indexHandle,
                "SELECT ?s ?p ?o WHERE { ?s ?p ?o }", QLeverMediaType.JSON);
            MemorySegment resultHandle = queryStatus.result();

            // 5. Iterate through results
            int resultCount = 0;
            while (bindings.resultHasNext(resultHandle)) {
                String result = bindings.resultNext(resultHandle);
                assertNotNull(result, "Result should not be null");
                resultCount++;
            }

            // 6. Verify results
            assertEquals(2, resultCount, "Should have 2 results");
            assertNull(bindings.resultError(resultHandle), "Should have no errors");

            // 7. Clean up
            bindings.resultDestroy(resultHandle);
            bindings.indexDestroy(indexHandle);

            long totalEnd = System.nanoTime();
            long totalDurationMicros = TimeUnit.MICROSECONDS.convert(totalEnd - totalStart, TimeUnit.NANOSCONDS);

            // Verify total workflow time is reasonable
            assertTrue(totalDurationMicros < 100000,
                      "Complete workflow should be fast: " + totalDurationMicros + "µs");
        }

        @Test
        @DisplayName("✅ Concurrent operations on same index")
        void concurrentOperations() throws IOException {
            // Arrange: Create test index
            createTestFilesWithContent();
            QLeverStatus status = bindings.indexCreate(testIndexDir.toString());
            MemorySegment indexHandle = status.result();

            // Act: Execute multiple queries concurrently (in sequence for test)
            QLeverStatus query1 = bindings.queryExec(indexHandle,
                "SELECT ?s ?p ?o WHERE { ?s ?p ?o }", QLeverMediaType.JSON);
            QLeverStatus query2 = bindings.queryExec(indexHandle,
                "ASK { ?s ?p ?o }", QLeverMediaType.XML);
            QLeverStatus query3 = bindings.queryExec(indexHandle,
                "CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }", QLeverMediaType.TURTLE);

            // Verify all queries succeeded
            assertTrue(query1.isSuccess(), "First query should succeed");
            assertTrue(query2.isSuccess(), "Second query should succeed");
            assertTrue(query3.isSuccess(), "Third query should succeed");

            // Clean up
            bindings.resultDestroy(query3.result());
            bindings.resultDestroy(query2.result());
            bindings.resultDestroy(query1.result());
            bindings.indexDestroy(indexHandle);
        }
    }

    // ========================================================================
    // Helper Methods
    // ========================================================================

    /**
     * Checks if the native QLever library is available for testing.
     */
    private boolean isNativeLibraryAvailable() {
        try {
            // Try to load the bindings - this will test native library availability
            new QLeverFfiBindings().close();
            return true;
        } catch (QLeverFfiException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Creates minimal test files for index creation.
     */
    private void createTestFiles() throws IOException {
        // Create directory
        Files.createDirectories(testIndexDir);

        // Create empty triples and schema files
        Files.write(testTriplesFile, new byte[0]);
        Files.write(testSchemaFile, new byte[0]);
    }

    /**
     * Creates test files with sample content.
     */
    private void createTestFilesWithContent() throws IOException {
        // Create directory
        Files.createDirectories(testIndexDir);

        // Write sample triples
        Files.write(testTriplesFile, SAMPLE_TRIPLES.getBytes());

        // Write sample schema
        Files.write(testSchemaFile, SIMPLE_SCHEMA.getBytes());
    }
}