/*
 * Copyright 2026 YAWL Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.yawlfoundation.yawl.qlever;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test examples demonstrating QLever Embedded usage patterns.
 * Each test method provides a self-contained example with detailed comments.
 */
@DisplayName("QLever Embedded Examples")
public class QLeverExamplesTest {

    private static final Logger logger = LoggerFactory.getLogger(QLeverExamplesTest.class);
    private QLeverEngine qleverEngine;
    private static final String TEST_INDEX_NAME = "yawl-test-index";

    @BeforeEach
    void setUp() throws Exception {
        // Initialize QLever engine with test configuration
        QLeverEngineConfiguration config = new QLeverEngineConfiguration();
        config.setServerPort(0); // Use random available port
        config.setIndexDir(createTestIndexDir());
        config.setDataDir(createTestDataDir());

        qleverEngine = new QLeverEngine(config);
        qleverEngine.start();

        // Wait for server to be ready
        Thread.sleep(1000);

        logger.info("QLever engine started on port: {}", qleverEngine.getServerPort());
    }

    @AfterEach
    void tearDown() {
        if (qleverEngine != null && qleverEngine.isRunning()) {
            qleverEngine.stop();
            logger.info("QLever engine stopped");
        }

        // Clean up test directories
        cleanupTestDirectories();
    }

    /**
     * Creates a temporary directory for test indices
     */
    private Path createTestIndexDir() throws IOException {
        Path indexDir = Files.createTempDirectory("yawl-qlever-test-index");
        logger.info("Created test index directory: {}", indexDir);
        return indexDir;
    }

    /**
     * Creates a temporary directory for test data
     */
    private Path createTestDataDir() throws IOException {
        Path dataDir = Files.createTempDirectory("yawl-qlever-test-data");
        logger.info("Created test data directory: {}", dataDir);
        return dataDir;
    }

    /**
     * Cleans up test directories
     */
    private void cleanupTestDirectories() {
        try {
            Path indexDir = Paths.get(System.getProperty("java.io.tmpdir"))
                               .resolve("yawl-qlever-test-index");
            Path dataDir = Paths.get(System.getProperty("java.io.tmpdir"))
                              .resolve("yawl-qlever-test-data");

            if (Files.exists(indexDir)) {
                deleteDirectory(indexDir);
                logger.info("Cleaned up index directory: {}", indexDir);
            }

            if (Files.exists(dataDir)) {
                deleteDirectory(dataDir);
                logger.info("Cleaned up data directory: {}", dataDir);
            }
        } catch (IOException e) {
            logger.error("Error cleaning up test directories", e);
        }
    }

    /**
     * Recursively deletes a directory
     */
    private void deleteDirectory(Path path) throws IOException {
        Files.walk(path)
             .sorted((a, b) -> -a.compareTo(b))
             .forEach(p -> {
                 try {
                     Files.delete(p);
                 } catch (IOException e) {
                     logger.error("Failed to delete: " + p, e);
                 }
             });
    }

    /**
     * Example 1: Basic index loading and query execution
     *
     * This demonstrates the fundamental workflow:
     * 1. Start QLever server
     * 2. Load an index
     * 3. Execute a simple query
     * 4. Process the results
     * 5. Clean up resources
     */
    @Test
    @DisplayName("Basic Index Loading and Query Execution")
    void testBasicQueryExecution() throws Exception {
        // Create a simple test index
        String indexName = TEST_INDEX_NAME + "-basic";
        String sparqlQuery = "SELECT COUNT(*) AS ?count WHERE { ?s ?p ?o }";

        // Load index (this would normally load a pre-existing index)
        // qleverEngine.loadIndex(indexName);

        // Execute the query
        QLeverQueryResult result = qleverEngine.executeSparqlQuery(sparqlQuery);

        // Verify the result structure
        assertNotNull(result, "Query result should not be null");
        assertEquals(200, result.getStatusCode(), "Query should succeed");
        assertTrue(result.hasResults(), "Query should return results");

        // Parse and process the results
        QLeverResultParser parser = new QLeverResultParser(result.getResponse());
        Map<String, Object> firstRow = parser.getFirstRow();

        logger.info("Basic query result: {}", firstRow);

        // Verify we got a count result
        assertTrue(firstRow.containsKey("count"), "Result should contain count");
    }

    /**
     * Example 2: SELECT query with JSON output
     *
     * Demonstrates executing SPARQL SELECT queries and parsing JSON responses.
     * This is the most common query type for data retrieval.
     */
    @Test
    @DisplayName("SELECT Query with JSON Output")
    void testSelectQueryWithJsonOutput() throws Exception {
        // Define a SPARQL SELECT query
        String sparqlQuery = "SELECT ?workitem ?name ?created WHERE { " +
                           "?workitem a <http://example.org/yawl#WorkItem> ; " +
                           "<http://example.org/yawl#name> ?name ; " +
                           "<http://example.org/yawl#created> ?created . " +
                           "FILTER (STRSTARTS(?name, \"Task\")) } " +
                           "LIMIT 10";

        // Execute the query
        QLeverQueryResult result = qleverEngine.executeSparqlQuery(sparqlQuery);

        // Verify response
        assertEquals(200, result.getStatusCode(), "Query should succeed");
        assertTrue(result.hasResults(), "Query should return results");

        // Parse JSON results
        QLeverResultParser parser = new QLeverResultParser(result.getResponse());

        // Get all rows
        Map<String, Object>[] rows = parser.getRows();
        logger.info("Found {} rows in SELECT query result", rows.length);

        // Process each row
        for (Map<String, Object> row : rows) {
            String workitem = (String) row.get("workitem");
            String name = (String) row.get("name");
            String created = (String) row.get("created");

            logger.info("WorkItem: {}, Name: {}, Created: {}",
                       workitem, name, created);
        }

        // Verify column names
        String[] columnNames = parser.getColumnNames();
        assertTrue(columnNames.length >= 3, "Should have at least 3 columns");
        assertArrayEquals(new String[]{"workitem", "name", "created"},
                          columnNames, "Column names should match");
    }

    /**
     * Example 3: CONSTRUCT query with Turtle output
     *
     * Demonstrates executing SPARQL CONSTRUCT queries that return RDF graphs
     * in Turtle format. This is useful for retrieving entire subgraphs.
     */
    @Test
    @DisplayName("CONSTRUCT Query with Turtle Output")
    void testConstructQueryWithTurtleOutput() throws Exception {
        // Define a CONSTRUCT query
        String sparqlQuery = "CONSTRUCT { " +
                          "?workitem <http://example.org/yawl#name> ?name . " +
                          "?workitem <http://example.org/yawl#status> ?status . " +
                          "?workitem <http://example.org/yawl#created> ?created " +
                          "} WHERE { " +
                          "?workitem a <http://example.org/yawl#WorkItem> ; " +
                          "<http://example.org/yawl#name> ?name ; " +
                          "<http://example.org/yawl#status> ?status ; " +
                          "<http://example.org/yawl#created> ?created . " +
                          "FILTER (?status = \"pending\") } " +
                          "LIMIT 5";

        // Execute with Turtle output format
        QLeverQueryResult result = qleverEngine.executeSparqlQuery(sparqlQuery,
                                                                 "text/turtle");

        // Verify response
        assertEquals(200, result.getStatusCode(), "Query should succeed");

        // Parse Turtle content
        String turtleContent = result.getResponse();
        assertNotNull(turtleContent, "Turtle content should not be null");
        assertFalse(turtleContent.isEmpty(), "Turtle content should not be empty");

        logger.info("CONSTRUCT Turtle output:\n{}", turtleContent);

        // Verify it's valid Turtle format (basic check)
        assertTrue(turtleContent.startsWith("<"), "Turtle should start with URI");
        assertTrue(turtleContent.contains("a "), "Turtle should contain 'a' for type");
        assertTrue(turtleContent.contains(" . "), "Turtle should end statements with ' . '");
    }

    /**
     * Example 4: Error handling patterns
     *
     * Demonstrates proper error handling for various failure scenarios:
     * - Invalid SPARQL syntax
     * - Non-existent resources
     * - Server errors
     * - Timeout handling
     */
    @Test
    @DisplayName("Error Handling Patterns")
    void testErrorHandling() throws Exception {
        // Test 1: Invalid SPARQL syntax
        String invalidQuery = "SELECT WHERE { ?s ?p ?o "; // Missing closing brace
        QLeverQueryResult invalidResult = qleverEngine.executeSparqlQuery(invalidQuery);

        assertNotEquals(200, invalidResult.getStatusCode(),
                       "Invalid query should return error");
        assertTrue(invalidResult.getResponse().contains("error"),
                  "Error response should contain 'error'");

        // Test 2: Non-existent triple pattern
        String noResultsQuery = "SELECT ?s WHERE { ?s <http://example.org/nonexistent> ?o }";
        QLeverQueryResult emptyResult = qleverEngine.executeSparqlQuery(noResultsQuery);

        assertEquals(200, emptyResult.getStatusCode(),
                    "Query with no results should succeed");
        assertFalse(emptyResult.hasResults(),
                   "Query with no results should have empty result set");

        // Test 3: Malformed Turtle output request
        QLeverQueryResult malformedResult = qleverEngine.executeSparqlQuery(
            "SELECT ?s WHERE { ?s ?p ?o }", "invalid/format");

        // Handle gracefully - QLever should default to JSON or return error
        logger.info("Malformed format result status: {}", malformedResult.getStatusCode());

        // Test 4: Timeout handling pattern
        try {
            // This would normally be a query that takes too long
            // For testing, we'll demonstrate the pattern
            String timeoutQuery = "SELECT * WHERE { ?s ?p ?o } LIMIT 1000000";

            // In real usage, you might set a timeout
            QLeverQueryResult timeoutResult = qleverEngine.executeSparqlQuery(timeoutQuery);

            // Check if response indicates timeout
            if (timeoutResult.getStatusCode() == 408 ||
                timeoutResult.getResponse().contains("timeout")) {
                logger.warn("Query timed out, as expected");
            }

        } catch (Exception e) {
            logger.warn("Timeout handling caught exception: {}", e.getMessage());
            // Handle timeout gracefully
        }
    }

    /**
     * Example 5: Multiple output formats
     *
     * Demonstrates executing the same query in different output formats:
     * - CSV
     * - TSV
     * - XML
     * - JSON (default)
     */
    @Test
    @DisplayName("Multiple Output Formats")
    void testMultipleOutputFormats() throws Exception {
        String sparqlQuery = "SELECT ?workitem ?name ?status WHERE { " +
                           "?workitem a <http://example.org/yawl#WorkItem> ; " +
                           "<http://example.org/yawl#name> ?name ; " +
                           "<http://example.org/yawl#status> ?status } " +
                           "LIMIT 5";

        // Test CSV format
        QLeverQueryResult csvResult = qleverEngine.executeSparqlQuery(sparqlQuery,
                                                                    "text/csv");
        assertEquals(200, csvResult.getStatusCode(), "CSV query should succeed");
        assertTrue(csvResult.getResponse().contains(","), "CSV should contain commas");
        logger.info("CSV output:\n{}", csvResult.getResponse());

        // Test TSV format
        QLeverQueryResult tsvResult = qleverEngine.executeSparqlQuery(sparqlQuery,
                                                                    "text/tab-separated-values");
        assertEquals(200, tsvResult.getStatusCode(), "TSV query should succeed");
        assertTrue(tsvResult.getResponse().contains("\t"), "TSV should contain tabs");
        logger.info("TSV output:\n{}", tsvResult.getResponse());

        // Test XML format
        QLeverQueryResult xmlResult = qleverEngine.executeSparqlQuery(sparqlQuery,
                                                                   "application/sparql-results+xml");
        assertEquals(200, xmlResult.getStatusCode(), "XML query should succeed");
        assertTrue(xmlResult.getResponse().contains("<result>"), "XML should contain results");
        logger.info("XML output:\n{}", xmlResult.getResponse());

        // Test JSON format (default)
        QLeverQueryResult jsonResult = qleverEngine.executeSparqlQuery(sparqlQuery);
        assertEquals(200, jsonResult.getStatusCode(), "JSON query should succeed");
        assertTrue(jsonResult.getResponse().contains("\"head\""),
                  "JSON should contain head section");
        logger.info("JSON output:\n{}", jsonResult.getResponse());
    }

    /**
     * Example 6: Triple count and metadata retrieval
     *
     * Demonstrates how to get metadata about the index:
     * - Total triple count
     * - Index statistics
     * - Available predicates
     * - Resource counts
     */
    @Test
    @DisplayName("Triple Count and Metadata Retrieval")
    void testTripleCountAndMetadata() throws Exception {
        // Get total triple count
        String countQuery = "SELECT (COUNT(*) AS ?totalTriples) WHERE { ?s ?p ?o }";
        QLeverQueryResult countResult = qleverEngine.executeSparqlQuery(countQuery);

        assertEquals(200, countResult.getStatusCode(), "Count query should succeed");

        QLeverResultParser countParser = new QLeverResultParser(countResult.getResponse());
        Map<String, Object> countRow = countParser.getFirstRow();
        String totalTriples = countRow.get("totalTriples").toString();

        logger.info("Total triples in index: {}", totalTriples);
        assertTrue(Long.parseLong(totalTriples) >= 0, "Triple count should be non-negative");

        // Get predicate counts
        String predicateQuery = "SELECT ?predicate (COUNT(*) AS ?count) WHERE { ?s ?predicate ?o } " +
                             "GROUP BY ?predicate ORDER BY DESC(?count) LIMIT 10";
        QLeverQueryResult predicateResult = qleverEngine.executeSparqlQuery(predicateQuery);

        assertEquals(200, predicateResult.getStatusCode(), "Predicate query should succeed");

        QLeverResultParser predicateParser = new QLeverResultParser(predicateResult.getResponse());
        Map<String, Object>[] predicateRows = predicateParser.getRows();

        logger.info("Top 10 predicates:");
        for (Map<String, Object> row : predicateRows) {
            String predicate = row.get("predicate").toString();
            String count = row.get("count").toString();
            logger.info("  {}: {}", predicate, count);
        }

        // Get subject counts (distinct resources)
        String subjectQuery = "SELECT (COUNT(DISTINCT ?s) AS ?distinctSubjects) WHERE { ?s ?p ?o }";
        QLeverQueryResult subjectResult = qleverEngine.executeSparqlQuery(subjectQuery);

        assertEquals(200, subjectResult.getStatusCode(), "Subject query should succeed");

        QLeverResultParser subjectParser = new QLeverResultParser(subjectResult.getResponse());
        Map<String, Object> subjectRow = subjectParser.getFirstRow();
        String distinctSubjects = subjectRow.get("distinctSubjects").toString();

        logger.info("Distinct subjects in index: {}", distinctSubjects);
        assertTrue(Long.parseLong(distinctSubjects) >= 0, "Subject count should be non-negative");
    }

    /**
     * Example 7: Resource cleanup with try-with-resources
     *
     * Demonstrates proper resource management using try-with-resources
     * and manual cleanup patterns. This is important for preventing resource leaks.
     */
    @Test
    @DisplayName("Resource Cleanup with Try-With-Resources")
    void testResourceCleanup() throws Exception {
        // Pattern 1: Using try-with-resources with QLeverEngine
        try (QLeverEngine engine = createAndStartEngine()) {
            // Use the engine
            QLeverQueryResult result = engine.executeSparqlQuery(
                "SELECT COUNT(*) AS ?count WHERE { ?s ?p ?o }");
            assertEquals(200, result.getStatusCode());

            logger.info("Engine used with try-with-resources");
        } // Engine is automatically stopped here

        // Pattern 2: Manual cleanup with try-finally
        QLeverEngine manualEngine = createAndStartEngine();
        try {
            // Use the engine
            QLeverQueryResult result = manualEngine.executeSparqlQuery(
                "SELECT COUNT(*) AS ?count WHERE { ?s ?p ?o }");
            assertEquals(200, result.getStatusCode());

            logger.info("Engine used with manual cleanup");
        } finally {
            // Ensure cleanup even if an exception occurs
            if (manualEngine != null && manualEngine.isRunning()) {
                manualEngine.stop();
                logger.info("Engine stopped in finally block");
            }
        }

        // Pattern 3: Multiple engines with proper cleanup
        QLeverEngine engine1 = null;
        QLeverEngine engine2 = null;
        try {
            engine1 = createAndStartEngine();
            engine2 = createAndStartEngine();

            // Use both engines
            QLeverResult result1 = engine1.executeSparqlQuery("SELECT 1");
            QLeverResult result2 = engine2.executeSparqlQuery("SELECT 2");

            assertEquals(200, result1.getStatusCode());
            assertEquals(200, result2.getStatusCode());

            logger.info("Multiple engines used and cleaned up");
        } finally {
            // Cleanup in reverse order of creation
            if (engine2 != null && engine2.isRunning()) {
                engine2.stop();
            }
            if (engine1 != null && engine1.isRunning()) {
                engine1.stop();
            }
        }
    }

    /**
     * Helper method to create and start a QLever engine for testing
     */
    private QLeverEngine createAndStartEngine() throws Exception {
        QLeverEngineConfiguration config = new QLeverEngineConfiguration();
        config.setServerPort(0); // Random port
        config.setIndexDir(createTestIndexDir());
        config.setDataDir(createTestDataDir());

        QLeverEngine engine = new QLeverEngine(config);
        engine.start();

        // Wait for server to be ready
        Thread.sleep(1000);

        return engine;
    }

    /**
     * Additional example: Complex SPARQL query patterns
     *
     * Demonstrates more complex SPARQL features:
     * - Optional patterns
     * - FILTER conditions
     * - UNION operations
     * - Aggregates with GROUP BY
     */
    @Test
    @DisplayName("Complex SPARQL Query Patterns")
    void testComplexSparqlQueries() throws Exception {
        // Query with OPTIONAL pattern
        String optionalQuery = "SELECT ?workitem ?name ?priority WHERE { " +
                             "?workitem <http://example.org/yawl#name> ?name . " +
                             "OPTIONAL { ?workitem <http://example.org/yawl#priority> ?priority } } " +
                             "LIMIT 10";

        QLeverQueryResult optionalResult = qleverEngine.executeSparqlQuery(optionalQuery);
        assertEquals(200, optionalResult.getStatusCode());

        // Query with complex FILTER
        String filterQuery = "SELECT ?workitem ?name ?created WHERE { " +
                           "?workitem <http://example.org/yawl#name> ?name ; " +
                           "<http://example.org/yawl#created> ?created . " +
                           "FILTER (STRSTARTS(?name, \"Task\") && " +
                           "xsd:dateTime(?created) > xsd:dateTime(\"2023-01-01T00:00:00\")) } " +
                           "LIMIT 10";

        QLeverQueryResult filterResult = qleverEngine.executeSparqlQuery(filterQuery);
        assertEquals(200, filterResult.getStatusCode());

        // Query with UNION
        String unionQuery = "SELECT ?resource ?name WHERE { " +
                          "{ ?resource a <http://example.org/yawl#WorkItem> ; " +
                          "<http://example.org/yawl#name> ?name } " +
                          "UNION " +
                          "{ ?resource a <http://example.org/yawl#Case> ; " +
                          "<http://example.org/yawl#name> ?name } " +
                          "} LIMIT 10";

        QLeverQueryResult unionResult = qleverEngine.executeSparqlQuery(unionQuery);
        assertEquals(200, unionResult.getStatusCode());

        // Query with aggregates and GROUP BY
        String aggregateQuery = "SELECT ?status (COUNT(*) AS ?count) WHERE { " +
                              "?workitem <http://example.org/yawl#status> ?status } " +
                              "GROUP BY ?status ORDER BY DESC(?count)";

        QLeverQueryResult aggregateResult = qleverEngine.executeSparqlQuery(aggregateQuery);
        assertEquals(200, aggregateResult.getStatusCode());

        logger.info("Complex queries executed successfully");
    }

    /**
     * Additional example: Performance benchmarking pattern
     *
     * Demonstrates how to measure query performance and optimize queries
     */
    @Test
    @DisplayName("Performance Benchmarking Pattern")
    void testPerformanceBenchmarking() throws Exception {
        // Test queries of varying complexity
        String[] testQueries = {
            "SELECT COUNT(*) AS ?count WHERE { ?s ?p ?o }", // Simple count
            "SELECT ?s ?p ?o WHERE { ?s ?p ?o } LIMIT 1000", // Medium complexity
            "SELECT ?s (COUNT(?o) AS ?count) WHERE { ?s ?p ?o } GROUP BY ?s LIMIT 100" // Complex
        };

        Map<String, Long> queryTimes = new HashMap<>();

        for (String query : testQueries) {
            long startTime = System.currentTimeMillis();

            QLeverQueryResult result = qleverEngine.executeSparqlQuery(query);
            assertEquals(200, result.getStatusCode());

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            queryTimes.put(query.substring(0, Math.min(50, query.length())) + "...", duration);
            logger.info("Query executed in {} ms", duration);
        }

        // Log performance summary
        logger.info("Query performance summary:");
        queryTimes.forEach((query, time) ->
            logger.info("  {}: {} ms", query, time));
    }
}