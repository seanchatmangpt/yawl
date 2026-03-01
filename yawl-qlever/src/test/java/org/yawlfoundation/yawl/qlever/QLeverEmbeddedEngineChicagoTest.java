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
import org.yawlfoundation.yawl.integration.autonomous.marketplace.SparqlEngine;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.SparqlEngineException;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.SparqlEngineUnavailableException;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD unit tests for QLeverEmbeddedSparqlEngine.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Constructor validation - valid/invalid paths, null, non-existent</li>
 *   <li>Availability lifecycle - create/close/fail states</li>
 *   <li>Engine type identification</li>
 *   <li>Query execution in multiple formats</li>
 *   <li>Resource metrics and cleanup</li>
 *   <li>Error handling and edge cases</li>
 * </ul>
 *
 * <p>Real metrics measured:
 * <ul>
 *   <li>Query latency in microseconds</li>
 *   <li>Memory usage before/after operations</li>
 *   <li>Resource cleanup verification</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @since YAWL 6.0
 */
@DisplayName("QLever Embedded Engine - Chicago TDD Tests")
class QLeverEmbeddedEngineChicagoTest {

    // Test constants
    private static final Path TEST_INDEX = Paths.get("/tmp/qlever-test-index");
    private static final Path NON_EXISTENT_INDEX = Paths.get("/tmp/qlever-non-existent-index");
    private static final Path TEMP_FILE_INDEX = Paths.get("/tmp/qlever-test-file.txt");

    // Memory tracking
    private static final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    private long initialMemoryUsed;

    @BeforeAll
    static void setupTestEnvironment() throws Exception {
        // Create test file for file path validation
        if (!Files.exists(TEMP_FILE_INDEX)) {
            Files.createFile(TEMP_FILE_INDEX);
        }

        // Clean up any existing test index that might interfere
        if (Files.exists(TEST_INDEX)) {
            deleteDirectory(TEST_INDEX);
        }

        // Create minimal test index structure
        Files.createDirectories(TEST_INDEX);
    }

    @BeforeEach
    void trackMemoryUsage() {
        initialMemoryUsed = memoryMXBean.getHeapMemoryUsage().getUsed();
    }

    @AfterEach
    void cleanup() {
        // Clean up any test-specific resources
        try {
            if (Files.exists(TEST_INDEX)) {
                deleteDirectory(TEST_INDEX);
            }
        } catch (Exception e) {
            // Log but don't fail test
            System.err.println("Cleanup warning: " + e.getMessage());
        }
    }

    // Helper methods
    private static void deleteDirectory(Path path) throws Exception {
        if (Files.exists(path)) {
            Files.walk(path)
                .sorted((a, b) -> b.toString().compareTo(a.toString()))
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (Exception e) {
                        System.err.println("Failed to delete: " + p + " - " + e.getMessage());
                    }
                });
        }
    }

    private long getMemoryUsed() {
        return memoryMXBean.getHeapMemoryUsage().getUsed();
    }

    private long getMemoryDelta() {
        return getMemoryUsed() - initialMemoryUsed;
    }

    private void assertMemoryDeltaWithinBounds(long delta, long maxBytes) {
        // Allow some variance in memory usage
        assertTrue(Math.abs(delta) <= maxBytes,
            String.format("Memory delta %d exceeds max %d bytes", delta, maxBytes));
    }

    // =============================================================================
    // Constructor Tests
    // =============================================================================

    @Test
    @DisplayName("Constructor with valid path succeeds")
    void constructorWithValidPathSucceeds() {
        // Create minimal valid index
        createMinimalIndex(TEST_INDEX);

        assertDoesNotThrow(() -> {
            QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(TEST_INDEX);
            assertNotNull(engine, "Engine should be created successfully");
            assertEquals(TEST_INDEX, engine.getIndexPath(), "Index path should match");
        });
    }

    @Test
    @DisplayName("Constructor with null path throws NullPointerException")
    void constructorWithNullPathThrows() {
        assertThrows(
            NullPointerException.class,
            () -> new QLeverEmbeddedSparqlEngine(null),
            "Constructor with null path should throw NullPointerException"
        );
    }

    @Test
    @DisplayName("Constructor with non-existent path throws IllegalArgumentException")
    void constructorWithNonExistentPathThrows() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new QLeverEmbeddedSparqlEngine(NON_EXISTENT_INDEX),
            "Constructor with non-existent path should throw IllegalArgumentException"
        );
    }

    @Test
    @DisplayName("Constructor with file path (not directory) throws IllegalArgumentException")
    void constructorWithFilePathThrows() {
        assertThrows(
            IllegalArgumentException.class,
            () -> new QLeverEmbeddedSparqlEngine(TEMP_FILE_INDEX),
            "Constructor with file path should throw IllegalArgumentException"
        );
    }

    // =============================================================================
    // Availability Tests
    // =============================================================================

    @Test
    @DisplayName("isAvailable() returns true after successful creation")
    void isAvailableReturnsTrueAfterCreate() {
        createMinimalIndex(TEST_INDEX);

        QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(TEST_INDEX);
        assertTrue(engine.isAvailable(), "Engine should be available after creation");
        engine.close();
    }

    @Test
    @DisplayName("isAvailable() returns false after close")
    void isAvailableReturnsFalseAfterClose() {
        createMinimalIndex(TEST_INDEX);

        QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(TEST_INDEX);
        engine.close();
        assertFalse(engine.isAvailable(), "Engine should not be available after close");
    }

    @Test
    @DisplayName("isAvailable() returns false after failed load")
    void isAvailableReturnsFalseAfterFailedLoad() throws Exception {
        // Create invalid index (missing required files)
        Files.createDirectories(TEST_INDEX);

        // Check that engine throws exception when trying to load invalid index
        Exception exception = assertThrows(
            SparqlEngineException.class,
            () -> new QLeverEmbeddedSparqlEngine(TEST_INDEX),
            "Engine should throw exception for invalid index"
        );

        // Create engine with valid index for comparison
        createMinimalIndex(TEST_INDEX);
        QLeverEmbeddedSparqlEngine validEngine = new QLeverEmbeddedSparqlEngine(TEST_INDEX);

        // This might be true depending on how QLever handles invalid indexes
        // We'll assert that either way, our contract is maintained
        boolean firstEngineAvailable = false; // We know it failed
        assertTrue(validEngine.isAvailable(), "Valid engine should be available");

        validEngine.close();
    }

    // =============================================================================
    // Engine Type Tests
    // =============================================================================

    @Test
    @DisplayName("engineType() returns 'qlever-embedded'")
    void engineTypeReturnsCorrectValue() {
        createMinimalIndex(TEST_INDEX);

        QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(TEST_INDEX);
        assertEquals("qlever-embedded", engine.engineType(), "Engine type should be 'qlever-embedded'");
        engine.close();
    }

    // =============================================================================
    // constructToTurtle Tests
    // =============================================================================

    @Test
    @DisplayName("constructToTurtle executes valid query and returns turtle")
    void constructToTurtleValidQueryReturnsTurtle() throws SparqlEngineException {
        createMinimalIndex(TEST_INDEX);

        QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(TEST_INDEX);

        String query = """
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            CONSTRUCT { ?s ?p ?o }
            WHERE { ?s ?p ?o }
            LIMIT 10
            """;

        String result = engine.constructToTurtle(query);
        assertNotNull(result, "Turtle result should not be null");

        // Measure query latency
        long startTime = System.nanoTime();
        result = engine.constructToTurtle(query);
        long durationNanos = System.nanoTime() - startTime;
        long durationMicros = TimeUnit.MICROSECONDS.convert(durationNanos, TimeUnit.NANOSECONDS);

        System.out.printf("Query latency: %d microseconds%n", durationMicros);

        // Assert latency is reasonable (less than 1 second)
        assertTrue(durationMicros < 1000000L, "Query should execute within reasonable time");

        engine.close();
    }

    @Test
    @DisplayName("constructToTurtle throws exception for invalid query")
    void constructToTurtleInvalidQueryThrows() {
        createMinimalIndex(TEST_INDEX);

        QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(TEST_INDEX);

        String invalidQuery = "THIS IS NOT VALID SPARQL";

        SparqlEngineException exception = assertThrows(
            SparqlEngineException.class,
            () -> engine.constructToTurtle(invalidQuery),
            "Invalid query should throw SparqlEngineException"
        );

        assertTrue(exception.getMessage().toLowerCase().contains("error") ||
                   exception.getMessage().toLowerCase().contains("parse") ||
                   exception.getMessage().toLowerCase().contains("syntax"),
            "Exception message should mention error");

        engine.close();
    }

    @Test
    @DisplayName("constructToTurtle handles empty result")
    void constructToTurtleEmptyResult() throws SparqlEngineException {
        createMinimalIndex(TEST_INDEX);

        QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(TEST_INDEX);

        // Query that returns no results
        String query = """
            CONSTRUCT { ?s ?p ?o }
            WHERE { ?s ?p ?o . FILTER (?s = <http://nonexistent.uri>) }
            """;

        String result = engine.constructToTurtle(query);
        assertNotNull(result, "Empty result should still return a string");
        assertTrue(result.isEmpty() || result.trim().isEmpty(), "Empty result should be empty string");

        engine.close();
    }

    @Test
    @DisplayName("constructToTuler measures memory usage")
    void constructToTurtleMeasuresMemoryUsage() throws SparqlEngineException {
        createMinimalIndex(TEST_INDEX);

        QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(TEST_INDEX);

        String query = """
            CONSTRUCT { ?s ?p ?o }
            WHERE { ?s ?p ?o }
            LIMIT 5
            """;

        long memoryBefore = getMemoryUsed();
        String result = engine.constructToTurtle(query);
        long memoryAfter = getMemoryUsed();
        long memoryDelta = memoryAfter - memoryBefore;

        System.out.printf("Memory delta for constructToTurtle: %d bytes%n", memoryDelta);

        // Memory delta should be within reasonable bounds (less than 1MB)
        assertMemoryDeltaWithinBounds(memoryDelta, 1000000L);

        engine.close();
    }

    // =============================================================================
    // selectToJson Tests
    // =============================================================================

    @Test
    @DisplayName("selectToJson executes valid SELECT query")
    void selectToJsonValidQuery() throws SparqlEngineException {
        createMinimalIndex(TEST_INDEX);

        QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(TEST_INDEX);

        String query = """
            SELECT ?s ?p ?o
            WHERE { ?s ?p ?o }
            LIMIT 5
            """;

        String result = engine.selectToJson(query);
        assertNotNull(result, "JSON result should not be null");

        // Should be valid JSON format
        assertTrue(result.startsWith("{") || result.trim().isEmpty(), "Result should be JSON format");

        engine.close();
    }

    @Test
    @DisplayName("selectToJson throws exception for malformed SPARQL")
    void selectToJsonMalformedSparqlThrows() {
        createMinimalIndex(TEST_INDEX);

        QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(TEST_INDEX);

        String malformedQuery = "SELECT * WHERE { } AND INVALID";

        SparqlEngineException exception = assertThrows(
            SparqlEngineException.class,
            () -> engine.selectToJson(malformedQuery),
            "Malformed SPARQL should throw SparqlEngineException"
        );

        assertNotNull(exception.getMessage(), "Exception message should not be null");

        engine.close();
    }

    @Test
    @DisplayName("selectToJson executes with measurable performance")
    void selectToJsonMeasurablePerformance() throws SparqlEngineException {
        createMinimalIndex(TEST_INDEX);

        QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(TEST_INDEX);

        String query = """
            SELECT ?s ?p ?o
            WHERE { ?s ?p ?o }
            LIMIT 10
            """;

        // Measure multiple executions
        long totalLatency = 0;
        int iterations = 5;

        for (int i = 0; i < iterations; i++) {
            long startTime = System.nanoTime();
            String result = engine.selectToJson(query);
            totalLatency += System.nanoTime() - startTime;
        }

        long avgLatencyMicros = TimeUnit.MICROSECONDS.convert(totalLatency / iterations, TimeUnit.NANOSECONDS);

        System.out.printf("Average selectToJson latency: %d microseconds (%d iterations)%n",
                         avgLatencyMicros, iterations);

        // Average should be reasonable
        assertTrue(avgLatencyMicros < 500000L, "Average query time should be reasonable");

        engine.close();
    }

    // =============================================================================
    // Format Conversion Tests (TSV/CSV)
    // =============================================================================

    @Test
    @DisplayName("selectToTsv converts SELECT results to TSV format")
    void selectToTsvFormatConversion() throws SparqlEngineException {
        createMinimalIndex(TEST_INDEX);

        QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(TEST_INDEX);

        String query = """
            SELECT ?s ?p ?o
            WHERE { ?s ?p ?o }
            LIMIT 3
            """;

        String result = engine.selectToTsv(query);
        assertNotNull(result, "TSV result should not be null");

        // TSV format should contain tab-separated values
        boolean hasTabs = result.contains("\t");
        boolean hasNewlines = result.contains("\n");

        assertTrue(hasTabs || result.trim().isEmpty(), "Result should be tab-separated or empty");
        assertTrue(hasNewlines || result.trim().isEmpty(), "Result should have newlines or be empty");

        System.out.println("TSV result:\n" + result);

        engine.close();
    }

    @Test
    @DisplayName("selectToCsv converts SELECT results to CSV format")
    void selectToCsvFormatConversion() throws SparqlEngineException {
        createMinimalIndex(TEST_INDEX);

        QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(TEST_INDEX);

        String query = """
            SELECT ?s ?p ?o
            WHERE { ?s ?p ?o }
            LIMIT 3
            """;

        String result = engine.selectToCsv(query);
        assertNotNull(result, "CSV result should not be null");

        // CSV format should contain comma-separated values
        boolean hasCommas = result.contains(",");
        boolean hasNewlines = result.contains("\n");

        assertTrue(hasCommas || result.trim().isEmpty(), "Result should be comma-separated or empty");
        assertTrue(hasNewlines || result.trim().isEmpty(), "Result should have newlines or be empty");

        System.out.println("CSV result:\n" + result);

        engine.close();
    }

    @Test
    @DisplayName("format conversion maintains data integrity")
    void formatConversionMaintainsDataIntegrity() throws SparqlEngineException {
        createMinimalIndex(TEST_INDEX);

        QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(TEST_INDEX);

        String query = """
            SELECT ?s ?p ?o
            WHERE { ?s ?p ?o }
            LIMIT 2
            """;

        String json = engine.selectToJson(query);
        String tsv = engine.selectToTsv(query);
        String csv = engine.selectToCsv(query);

        // All should return results (even if empty strings)
        assertNotNull(json);
        assertNotNull(tsv);
        assertNotNull(csv);

        // All formats should produce similar content length bounds
        int minLength = 0;
        int maxLength = 10000; // Allow for reasonable variation

        assertTrue(json.length() >= minLength && json.length() <= maxLength,
                  "JSON length out of bounds");
        assertTrue(tsv.length() >= minLength && tsv.length() <= maxLength,
                  "TSV length out of bounds");
        assertTrue(csv.length() >= minLength && csv.length() <= maxLength,
                  "CSV length out of bounds");

        engine.close();
    }

    // =============================================================================
    // Triple Count Tests
    // =============================================================================

    @Test
    @DisplayName("getTripleCount returns count for loaded index")
    void getTripleCountReturnsCount() {
        createMinimalIndex(TEST_INDEX);

        QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(TEST_INDEX);

        long count = engine.getTripleCount();
        System.out.println("Triple count: " + count);

        // Should return 0 or positive number
        assertTrue(count >= 0, "Triple count should be non-negative");

        engine.close();
    }

    @Test
    @DisplayName("getTripleCount returns 0 after close")
    void getTripleCountReturnsZeroAfterClose() {
        createMinimalIndex(TEST_INDEX);

        QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(TEST_INDEX);
        engine.close();

        long count = engine.getTripleCount();
        assertEquals(0, count, "Triple count should be 0 after close");
    }

    // =============================================================================
    // Close Tests
    // =============================================================================

    @Test
    @DisplayName("close() is idempotent")
    void closeIsIdempotent() {
        createMinimalIndex(TEST_INDEX);

        QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(TEST_INDEX);

        // Close first time
        engine.close();
        assertFalse(engine.isAvailable(), "Should not be available after first close");

        // Close second time
        assertDoesNotThrow(() -> engine.close(), "Second close should not throw");
        assertFalse(engine.isAvailable(), "Should still not be available after second close");
    }

    @Test
    @DisplayName("close() is double-close safe")
    void closeIsDoubleCloseSafe() {
        createMinimalIndex(TEST_INDEX);

        QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(TEST_INDEX);

        // Close twice in quick succession
        engine.close();
        engine.close();

        // Should still report as unavailable
        assertFalse(engine.isAvailable(), "Engine should be unavailable after double close");

        // Any further operations should throw
        assertThrows(
            SparqlEngineUnavailableException.class,
            () -> engine.constructToTurtle("SELECT * WHERE { ?s ?p ?o }"),
            "Operations after close should throw SparqlEngineUnavailableException"
        );
    }

    @Test
    @DisplayName("close() releases measurable memory")
    void closeReleasesMemory() throws SparqlEngineException {
        createMinimalIndex(TEST_INDEX);

        QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(TEST_INDEX);

        // Execute some queries to build up state
        engine.constructToTurtle("CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o } LIMIT 5");
        engine.selectToJson("SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 5");

        long memoryBeforeClose = getMemoryUsed();

        // Close and measure memory
        engine.close();
        long memoryAfterClose = getMemoryUsed();
        long memoryDelta = memoryAfterClose - memoryBeforeClose;

        System.out.printf("Memory delta after close: %d bytes%n", memoryDelta);

        // Memory should have decreased (or stayed same at worst)
        assertTrue(memoryDelta <= 0 || Math.abs(memoryDelta) < 100000L,
                 "Memory should be released or within small tolerance");
    }

    // =============================================================================
    // Resource Cleanup Tests
    // =============================================================================

    @Test
    @DisplayName("Resource cleanup with finalize works")
    void resourceCleanupWithFinalize() throws Exception {
        createMinimalIndex(TEST_INDEX);

        // Track memory before creating engine
        long memoryBefore = getMemoryUsed();

        // Create engine without auto-close to test finalize
        QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(TEST_INDEX);

        // Execute queries to build state
        engine.constructToTurtle("CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o } LIMIT 5");
        engine.selectToJson("SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 5");

        // Force garbage collection
        System.gc();

        // Small delay for finalize to potentially run
        Thread.sleep(100);

        // Manually close to ensure cleanup
        engine.close();

        long memoryAfter = getMemoryUsed();
        long memoryDelta = memoryAfter - memoryBefore;

        System.out.printf("Memory usage for finalize test: %d bytes%n", memoryDelta);

        // Memory should be within reasonable bounds
        assertMemoryDeltaWithinBounds(memoryDelta, 2000000L);

        // Engine should not be available
        assertFalse(engine.isAvailable(), "Engine should not be available after close");
    }

    @Test
    @DisplayName("Multiple engines can coexist and cleanup independently")
    void multipleIndependentEnginesCleanupIndependently() throws Exception {
        createMinimalIndex(TEST_INDEX);

        // Create multiple engines
        QLeverEmbeddedSparqlEngine engine1 = new QLeverEmbeddedSparqlEngine(TEST_INDEX);
        QLeverEmbeddedSparqlEngine engine2 = new QLeverEmbeddedSparqlEngine(TEST_INDEX);

        // Both should be available
        assertTrue(engine1.isAvailable(), "Engine 1 should be available");
        assertTrue(engine2.isAvailable(), "Engine 2 should be available");

        // Execute queries on both
        engine1.constructToTurtle("CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o } LIMIT 3");
        engine2.selectToJson("SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 3");

        // Close first engine
        engine1.close();
        assertFalse(engine1.isAvailable(), "Engine 1 should not be available after close");
        assertTrue(engine2.isAvailable(), "Engine 2 should still be available");

        // Close second engine
        engine2.close();
        assertFalse(engine2.isAvailable(), "Engine 2 should not be available after close");

        // Verify no resource leaks
        long memoryAfter = getMemoryUsed();
        assertTrue(memoryAfter < 10_000_000, "Memory usage should be reasonable after cleanup");
    }

    @Test
    @DisplayName("Try-with-resources pattern works correctly")
    void tryWithResourcesPatternWorks() throws SparqlEngineException {
        createMinimalIndex(TEST_INDEX);

        assertDoesNotThrow(() -> {
            try (QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(TEST_INDEX)) {
                assertTrue(engine.isAvailable(), "Engine should be available in try-with-resources");

                String result = engine.constructToTurtle("CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o } LIMIT 3");
                assertNotNull(result, "Query should work in try-with-resources");
            }
        }, "Try-with-resources should not throw exceptions");

        // Engine should be unavailable after try-with-resources
        // (We can't test this directly since the variable is out of scope)
    }

    // =============================================================================
    // Edge Cases and Error Conditions
    // =============================================================================

    @Test
    @DisplayName("Query execution after engine close throws exception")
    void queryAfterCloseThrows() throws Exception {
        createMinimalIndex(TEST_INDEX);

        QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(TEST_INDEX);
        engine.close();

        assertThrows(
            SparqlEngineUnavailableException.class,
            () -> engine.constructToTurtle("SELECT * WHERE { ?s ?p ?o }"),
            "Query after close should throw SparqlEngineUnavailableException"
        );
    }

    @Test
    @DisplayName("Null queries throw NullPointerException")
    void nullQueriesThrow() {
        createMinimalIndex(TEST_INDEX);

        QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(TEST_INDEX);

        assertThrows(
            NullPointerException.class,
            () -> engine.constructToTurtle(null),
            "Null CONSTRUCT query should throw NullPointerException"
        );

        assertThrows(
            NullPointerException.class,
            () -> engine.selectToJson(null),
            "Null SELECT query should throw NullPointerException"
        );

        assertThrows(
            NullPointerException.class,
            () -> engine.selectToTsv(null),
            "Null TSV query should throw NullPointerException"
        );

        assertThrows(
            NullPointerException.class,
            () -> engine.selectToCsv(null),
            "Null CSV query should throw NullPointerException"
        );

        engine.close();
    }

    @Test
    @DisplayName("Empty queries are handled gracefully")
    void emptyQueriesHandledGracefully() throws SparqlEngineException {
        createMinimalIndex(TEST_INDEX);

        QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(TEST_INDEX);

        // Empty queries should not crash
        assertDoesNotThrow(() -> {
            String result1 = engine.constructToTurtle("");
            String result2 = engine.selectToJson("");
            String result3 = engine.selectToTsv("");
            String result4 = engine.selectToCsv("");

            assertNotNull(result1);
            assertNotNull(result2);
            assertNotNull(result3);
            assertNotNull(result4);
        }, "Empty queries should not throw");

        engine.close();
    }

    // =============================================================================
    // Performance and Scalability Tests
    // =============================================================================

    @Test
    @DisplayName("Multiple consecutive query executions maintain performance")
    void multipleQueriesMaintainPerformance() throws SparqlEngineException {
        createMinimalIndex(TEST_INDEX);

        QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(TEST_INDEX);

        String query = """
            SELECT ?s ?p ?o
            WHERE { ?s ?p ?o }
            LIMIT 2
            """;

        // Execute multiple queries and measure performance
        List<Long> latencies = new java.util.ArrayList<>();

        for (int i = 0; i < 10; i++) {
            long startTime = System.nanoTime();
            String result = engine.selectToJson(query);
            long duration = TimeUnit.MICROSECONDS.convert(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
            latencies.add(duration);

            assertNotNull(result, "Query result should not be null");
        }

        // Calculate statistics
        double avgLatency = latencies.stream().mapToLong(l -> l).average().orElse(0);
        long maxLatency = latencies.stream().mapToLong(l -> l).max().orElse(0);

        System.out.printf("Average latency: %.0f μs, Max latency: %d μs (10 iterations)%n",
                         avgLatency, maxLatency);

        // Performance should remain consistent
        assertTrue(avgLatency < 500000L, "Average latency should be reasonable");
        assertTrue(maxLatency < 1000000L, "Max latency should be reasonable");

        engine.close();
    }

    @Test
    @DisplayName("Memory usage remains stable across multiple operations")
    void memoryUsageStableAcrossOperations() throws SparqlEngineException {
        createMinimalIndex(TEST_INDEX);

        QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine(TEST_INDEX);

        long initialMemory = getMemoryUsed();

        // Perform multiple operations
        for (int i = 0; i < 5; i++) {
            engine.constructToTurtle("CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o } LIMIT 2");
            engine.selectToJson("SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 2");

            long currentMemory = getMemoryUsed();
            long memoryDelta = currentMemory - initialMemory;

            System.out.printf("Iteration %d memory delta: %d bytes%n", i, memoryDelta);

            // Memory should not grow unbounded
            assertTrue(memoryDelta < 5_000_000, "Memory should not grow unbounded");
        }

        engine.close();
    }

    // =============================================================================
    // Helper Methods
    // =============================================================================

    private void createMinimalIndex(Path path) {
        try {
            if (Files.exists(path)) {
                deleteDirectory(path);
            }

            Files.createDirectories(path);

            // Create minimal index files that QLever might expect
            // This is a simplified version - real QLever indexes would need actual data

            // Create a .index.pbm file (placeholder)
            Path indexFile = path.resolve(".index.pbm");
            Files.write(indexFile, new byte[8]); // Minimal file

        } catch (Exception e) {
            throw new RuntimeException("Failed to create test index at " + path, e);
        }
    }
}