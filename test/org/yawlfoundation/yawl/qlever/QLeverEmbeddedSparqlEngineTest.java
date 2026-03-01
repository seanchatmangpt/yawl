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
import org.yawlfoundation.yawl.integration.autonomous.marketplace.SparqlEngine;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.SparqlEngineException;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.SparqlEngineUnavailableException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for QLever Embedded SPARQL Engine.
 *
 * <p>Tests the full engine lifecycle and query execution. Requires:</p>
 * <ul>
 *   <li>libqlever_ffi native library in java.library.path</li>
 *   <li>Pre-built test index at the path specified by qlever.test.index system property</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @since YAWL 6.0
 */
@DisplayName("QLever Embedded SPARQL Engine")
class QLeverEmbeddedSparqlEngineTest {

/**
 * Integration tests for QLever Embedded SPARQL Engine.
 *
 * <p>Tests the full engine lifecycle and query execution. Requires:</p>
 * <ul>
 *   <li>libqlever_ffi native library in java.library.path</li>
 *   <li>Pre-built test index at the path specified by qlever.test.index system property</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @since YAWL 6.0
 */
@DisplayName("QLever Embedded SPARQL Engine")
class QLeverEmbeddedSparqlEngineTest {

    /** Test index path - set via system property or defaults to /tmp */
    private static final Path TEST_INDEX = Paths.get(
        System.getProperty("qlever.test.index", "/tmp/qlever-test-index")
    );

    private static SparqlEngine engine;

    @BeforeAll
    static void setup() {
        Assumptions.assumeTrue(
            isNativeLibraryAvailable(),
            "libqlever_ffi not available - skipping engine tests"
        );
        Assumptions.assumeTrue(
            TEST_INDEX.toFile().exists(),
            "Test index not found at: " + TEST_INDEX + ". " +
            "Build a test index first or set -Dqlever.test.index=/path/to/index"
        );

        engine = new QLeverEmbeddedSparqlEngine(TEST_INDEX);
    }

    @AfterAll
    static void teardown() {
        if (engine != null) {
            engine.close();
            engine = null;
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
    // Engine Lifecycle Tests
    // ========================================================================

    @Test
    @DisplayName("Should report available when index loaded")
    void shouldBeAvailable() {
        assertTrue(engine.isAvailable(), "Engine should be available after construction");
    }

    @Test
    @DisplayName("Should return engine type qlever-embedded")
    void shouldReturnEngineType() {
        assertEquals("qlever-embedded", engine.engineType(), "Engine type should be qlever-embedded");
    }

    @Test
    @DisplayName("Should return non-negative triple count")
    void shouldReturnNonNegativeTripleCount() {
        QLeverEmbeddedSparqlEngine qleverEngine = (QLeverEmbeddedSparqlEngine) engine;
        long count = qleverEngine.getTripleCount();
        assertTrue(count >= 0, "Triple count should be non-negative, got: " + count);
    }

    @Test
    @DisplayName("Should return index path")
    void shouldReturnIndexPath() {
        QLeverEmbeddedSparqlEngine qleverEngine = (QLeverEmbeddedSparqlEngine) engine;
        assertEquals(TEST_INDEX, qleverEngine.getIndexPath(), "Index path should match constructor argument");
    }

    // ========================================================================
    // Query Execution Tests
    // ========================================================================

    @Test
    @DisplayName("Should execute simple CONSTRUCT query")
    void shouldExecuteConstructQuery() throws SparqlEngineException {
        String query = """
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            CONSTRUCT { ?s ?p ?o }
            WHERE { ?s ?p ?o }
            LIMIT 10
            """;

        String result = engine.constructToTurtle(query);

        assertNotNull(result, "Query result should not be null");
        // Result may be empty if index is empty, but should not throw
    }

    @Test
    @DisplayName("Should execute CONSTRUCT query and return Turtle format")
    void shouldExecuteConstructQueryWithTurtleFormat() throws SparqlEngineException {
        String query = """
            PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
            CONSTRUCT { ?s ?p ?o }
            WHERE { ?s ?p ?o }
            LIMIT 5
            """;

        String result = engine.constructToTurtle(query);

        assertNotNull(result, "CONSTRUCT result should not be null");
        // Should return Turtle format (starts with @prefix or contains triples)
        assertTrue(result.startsWith("@prefix") || result.contains(" a ") || result.trim().isEmpty(),
                   "Result should be in Turtle format");
    }

    @Test
    @DisplayName("Should throw for null query")
    void shouldThrowForNullQuery() {
        assertThrows(
            NullPointerException.class,
            () -> engine.constructToTurtle(null),
            "Null query should throw NullPointerException"
        );
    }

    @Test
    @DisplayName("Should throw for null SELECT query")
    void shouldThrowForNullSelectQuery() {
        assertThrows(
            NullPointerException.class,
            () -> ((QLeverEmbeddedSparqlEngine) engine).selectToJson(null),
            "Null SELECT query should throw NullPointerException"
        );
    }

    @Test
    @DisplayName("Should throw SparqlEngineException for invalid query syntax")
    void shouldThrowForInvalidQuerySyntax() {
        String invalidQuery = "THIS IS NOT VALID SPARQL";

        SparqlEngineException exception = assertThrows(
            SparqlEngineException.class,
            () -> engine.constructToTurtle(invalidQuery),
            "Invalid query should throw SparqlEngineException"
        );

        assertTrue(
            exception.getMessage().toLowerCase().contains("error") ||
            exception.getMessage().toLowerCase().contains("parse") ||
            exception.getMessage().toLowerCase().contains("syntax"),
            "Exception message should mention error or parse: " + exception.getMessage()
        );
    }

    @Test
    @DisplayName("Should throw SparqlEngineException for invalid SELECT query syntax")
    void shouldThrowForInvalidSelectQuerySyntax() {
        String invalidQuery = "INVALID SELECT QUERY";

        SparqlEngineException exception = assertThrows(
            SparqlEngineException.class,
            () -> ((QLeverEmbeddedSparqlEngine) engine).selectToJson(invalidQuery),
            "Invalid SELECT query should throw SparqlEngineException"
        );

        assertTrue(
            exception.getMessage().toLowerCase().contains("error") ||
            exception.getMessage().toLowerCase().contains("parse") ||
            exception.getMessage().toLowerCase().contains("syntax"),
            "Exception message should mention error or parse: " + exception.getMessage()
        );
    }

    @Test
    @DisplayName("Should execute SELECT query and return JSON with default media type")
    void shouldExecuteSelectQueryWithDefaultMediaType() throws SparqlEngineException {
        QLeverEmbeddedSparqlEngine qleverEngine = (QLeverEmbeddedSparqlEngine) engine;

        String query = """
            SELECT ?s ?p ?o
            WHERE { ?s ?p ?o }
            LIMIT 5
            """;

        String result = qleverEngine.selectToJson(query);

        assertNotNull(result, "SELECT result should not be null");
        // Result may be empty JSON object if index is empty
    }

    @Test
    @DisplayName("Should execute SELECT query with JSON media type")
    void shouldExecuteSelectQueryWithJsonMediaType() throws SparqlEngineException {
        QLeverEmbeddedSparqlEngine qleverEngine = (QLeverEmbeddedSparqlEngine) engine;

        String query = """
            SELECT ?s ?p ?o
            WHERE { ?s ?p ?o }
            LIMIT 5
            """;

        // Test that it returns JSON format
        String result = qleverEngine.selectToJson(query);

        assertNotNull(result, "SELECT result should not be null");
        // Result should be valid JSON (even if empty)
        assertTrue(result.startsWith("{") || result.trim().isEmpty(), "Result should be JSON format");
    }

    @Test
    @DisplayName("Should execute SELECT query and handle empty results")
    void shouldExecuteSelectQueryWithEmptyResults() throws SparqlEngineException {
        QLeverEmbeddedSparqlEngine qleverEngine = (QLeverEmbeddedSparqlEngine) engine;

        // Query that likely returns no results
        String query = """
            SELECT ?s ?p ?o
            WHERE { ?s ?p ?o . FILTER (?s = <http://nonexistent.uri>) }
            LIMIT 1
            """;

        String result = qleverEngine.selectToJson(query);

        assertNotNull(result, "SELECT result should not be null");
        // Should return empty JSON result structure
        assertTrue(result.contains("bindings") || result.contains("results"), "Result should contain JSON SPARQL results structure");
    }

    // ========================================================================
    // Engine Close Tests
    // ========================================================================

    @Test
    @DisplayName("Should report unavailable after close")
    @EnabledIf("isNativeLibraryAvailable")
    void shouldReportUnavailableAfterClose() {
        // Create a separate engine for this test
        Path tempIndex = Paths.get(System.getProperty("qlever.test.index", "/tmp/qlever-test-index"));
        Assumptions.assumeTrue(
            tempIndex.toFile().exists(),
            "Test index not found"
        );

        SparqlEngine localEngine = new QLeverEmbeddedSparqlEngine(tempIndex);
        assertTrue(localEngine.isAvailable(), "Engine should be available before close");

        localEngine.close();

        assertFalse(localEngine.isAvailable(), "Engine should not be available after close");
    }

    @Test
    @DisplayName("Should throw SparqlEngineUnavailableException after close")
    @EnabledIf("isNativeLibraryAvailable")
    void shouldThrowUnavailableAfterClose() {
        Path tempIndex = Paths.get(System.getProperty("qlever.test.index", "/tmp/qlever-test-index"));
        Assumptions.assumeTrue(
            tempIndex.toFile().exists(),
            "Test index not found"
        );

        SparqlEngine localEngine = new QLeverEmbeddedSparqlEngine(tempIndex);
        localEngine.close();

        assertThrows(
            SparqlEngineUnavailableException.class,
            () -> localEngine.constructToTurtle("SELECT * WHERE { ?s ?p ?o }"),
            "Query after close should throw SparqlEngineUnavailableException"
        );
    }

    // ========================================================================
    // Constructor Validation Tests
    // ========================================================================

    @Test
    @DisplayName("Should throw for null index path")
    void shouldThrowForNullIndexPath() {
        assertThrows(
            NullPointerException.class,
            () -> new QLeverEmbeddedSparqlEngine(null),
            "Null index path should throw NullPointerException"
        );
    }

    @Test
    @DisplayName("Should throw for non-existent index path")
    void shouldThrowForNonExistentIndexPath() {
        Path nonExistent = Paths.get("/nonexistent/path/to/qlever/index");

        assertThrows(
            IllegalArgumentException.class,
            () -> new QLeverEmbeddedSparqlEngine(nonExistent),
            "Non-existent path should throw IllegalArgumentException"
        );
    }

    @Test
    @DisplayName("Should throw for file path (not directory)")
    @EnabledIf("isNativeLibraryAvailable")
    void shouldThrowForFilePath() throws Exception {
        // Create a temp file (not directory)
        Path tempFile = Files.createTempFile("qlever-test", ".txt");
        tempFile.toFile().deleteOnExit();

        assertThrows(
            IllegalArgumentException.class,
            () -> new QLeverEmbeddedSparqlEngine(tempFile),
            "File path (not directory) should throw IllegalArgumentException"
        );
    }

    // ========================================================================
    // Try-With-Resources Pattern Test
    // ========================================================================

    @Test
    @DisplayName("Should support try-with-resources pattern")
    @EnabledIf("isNativeLibraryAvailable")
    void shouldSupportTryWithResources() {
        Path tempIndex = Paths.get(System.getProperty("qlever.test.index", "/tmp/qlever-test-index"));
        Assumptions.assumeTrue(
            tempIndex.toFile().exists(),
            "Test index not found"
        );

        assertDoesNotThrow(() -> {
            try (SparqlEngine localEngine = new QLeverEmbeddedSparqlEngine(tempIndex)) {
                assertTrue(localEngine.isAvailable(), "Engine should be usable in try-with-resources");
            }
        }, "Close should not throw in try-with-resources");
    }
}
