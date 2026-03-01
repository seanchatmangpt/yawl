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
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive smoke tests for QLever integration.
 *
 * Smoke tests verify that the basic functionality works and provide
 * performance metrics to ensure the integration is operating properly.
 * These tests should run quickly and are tagged for CI filtering.
 *
 * @Tag("smoke")
 */
@Tag("smoke")
@DisplayName("QLever Integration Smoke Tests")
public class QLeverSmokeTest {

    private static final Logger logger = LoggerFactory.getLogger(QLeverSmokeTest.class);

    private QLeverEngine qleverEngine;
    private Path testIndexDir;
    private Path testDataDir;
    private static final String TEST_INDEX_NAME = "yawl-smoke-test-index";

    // Performance metrics
    private long loadTimeMillis;
    private long firstQueryLatencyMillis;
    private double sequentialQueryAverageLatencyMillis;

    @BeforeEach
    void setUp() throws Exception {
        logger.info("Starting QLever smoke test setup...");

        // Create test directories
        testIndexDir = Files.createTempDirectory("yawl-smoke-test-index");
        testDataDir = Files.createTempDirectory("yawl-smoke-test-data");

        logger.info("Created test directories - Index: {}, Data: {}", testIndexDir, testDataDir);

        // Initialize QLever engine
        QLeverEngineConfiguration config = new QLeverEngineConfiguration();
        config.setServerPort(0); // Use random available port
        config.setIndexDir(testIndexDir);
        config.setDataDir(testDataDir);
        config.setMaxQueryTimeout(10000); // 10 seconds for smoke tests
        config.setCacheSize(100);

        long startTime = System.currentTimeMillis();
        qleverEngine = new QLeverEngine(config);
        qleverEngine.start();

        // Wait for engine to be ready
        waitForEngineReady();

        loadTimeMillis = System.currentTimeMillis() - startTime;
        logger.info("QLever engine started in {} ms", loadTimeMillis);

        // Load test data
        loadTestWorkflowData();
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
     * Waits for the QLever engine to be ready
     */
    private void waitForEngineReady() throws InterruptedException {
        int maxAttempts = 30;
        int attempt = 0;

        while (attempt < maxAttempts) {
            if (qleverEngine.isRunning() && qleverEngine.getServerPort() > 0) {
                logger.info("QLever engine is ready on port {}", qleverEngine.getServerPort());
                return;
            }

            attempt++;
            Thread.sleep(100); // Wait 100ms between attempts
        }

        throw new IllegalStateException("QLever engine failed to start within 3 seconds");
    }

    /**
     * Loads test YAWL workflow data
     */
    private void loadTestWorkflowData() throws IOException {
        String yawlData = """
            @prefix yawl: <http://example.org/yawl#> .
            @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
            @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

            # Basic YAWL classes
            yawl:WorkItem a rdfs:Class ;
                rdfs:label "Work Item" ;
                rdfs:comment "A unit of work in a YAWL workflow" .

            yawl:Case a rdfs:Class ;
                rdfs:label "Case" ;
                rdfs:comment "A workflow case instance" .

            yawl:User a rdfs:Class ;
                rdfs:label "User" ;
                rdfs:comment "A workflow participant" .

            # Basic YAWL properties
            yawl:name a rdf:Property ;
                rdfs:label "name" ;
                rdfs:domain rdfs:Resource ;
                rdfs:range xsd:string .

            yawl:status a rdf:Property ;
                rdfs:label "status" ;
                rdfs:domain rdfs:Resource ;
                rdfs:range xsd:string .

            yawl:priority a rdf:Property ;
                rdfs:label "priority" ;
                rdfs:domain rdfs:Resource ;
                rdfs:range xsd:string .

            yawl:created a rdf:Property ;
                rdfs:label "created" ;
                rdfs:domain rdfs:Resource ;
                rdfs:range xsd:dateTime .

            yawl:assignedTo a rdf:Property ;
                rdfs:label "assigned to" ;
                rdfs:domain rdfs:Resource ;
                rdfs:range yawl:User .

            yawl:hasWorkItem a rdf:Property ;
                rdfs:label "has work item" ;
                rdfs:domain yawl:Case ;
                rdfs:range yawl:WorkItem .

            yawl:case a rdf:Property ;
                rdfs:label "case" ;
                rdfs:domain yawl:WorkItem ;
                rdfs:range yawl:Case .

            # Sample test data
            <http://example.org/cases/case-001> a yawl:Case ;
                yawl:name "Customer Service Request" ;
                yawl:status "active" ;
                yawl:priority "high" ;
                yawl:created "2024-01-15T09:00:00"^^xsd:dateTime ;
                yawl:assignedTo <http://example.org/users/manager> .

            <http://example.org/cases/case-002> a yawl:Case ;
                yawl:name "Employee Onboarding" ;
                yawl:status "completed" ;
                yawl:priority "medium" ;
                yawl:created "2024-01-10T10:00:00"^^xsd:dateTime ;
                yawl:completed "2024-01-14T15:00:00"^^xsd:dateTime ;
                yawl:assignedTo <http://example.org/users/hr> .

            <http://example.org/workitems/wi-001> a yawl:WorkItem ;
                yawl:name "Review Customer Complaint" ;
                yawl:status "in_progress" ;
                yawl:priority "high" ;
                yawl:created "2024-01-15T10:00:00"^^xsd:dateTime ;
                yawl:assignedTo <http://example.org/users/agent> ;
                yawl:case <http://example.org/cases/case-001> .

            <http://example.org/workitems/wi-002> a yawl:WorkItem ;
                yawl:name "Process New Hire Documents" ;
                yawl:status "pending" ;
                yawl:priority "medium" ;
                yawl:created "2024-01-10T11:00:00"^^xsd:dateTime ;
                yawl:assignedTo <http://example.org/users/hr> ;
                yawl:case <http://example.org/cases/case-002> .

            <http://example.org/users/manager> a yawl:User ;
                yawl:name "Workflow Manager" ;
                yawl:status "active" .

            <http://example.org/users/hr> a yawl:User ;
                yawl:name "HR Specialist" ;
                yawl:status "active" .

            <http://example.org/users/agent> a yawl:User ;
                yawl:name "Customer Service Agent" ;
                yawl:status "active" .
            """;

        // Write test data to file
        Path dataFile = testDataDir.resolve("yawl-test-data.ttl");
        Files.writeString(dataFile, yawlData);
        logger.info("Created test data file: {}", dataFile);
    }

    /**
     * Cleans up test directories
     */
    private void cleanupTestDirectories() {
        try {
            if (Files.exists(testIndexDir)) {
                deleteDirectory(testIndexDir);
                logger.info("Cleaned up test index directory: {}", testIndexDir);
            }

            if (Files.exists(testDataDir)) {
                deleteDirectory(testDataDir);
                logger.info("Cleaned up test data directory: {}", testDataDir);
            }
        } catch (IOException e) {
            logger.error("Error cleaning up test directories", e);
        }
    }

    /**
     * Recursively deletes a directory
     */
    private void deleteDirectory(Path path) throws IOException {
        if (Files.exists(path)) {
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
    }

    /**
     * Test 1: Library loads successfully
     *
     * Verifies that the QLever library can be loaded and initialized
     * without errors. This is the most fundamental smoke test.
     */
    @Test
    @DisplayName("Library Loads Successfully")
    void testLibraryLoadsSuccessfully() {
        // Assertions
        assertNotNull(qleverEngine, "QLever engine should not be null");
        assertTrue(qleverEngine.isRunning(), "QLever engine should be running");
        assertTrue(qleverEngine.getServerPort() > 0, "Server port should be assigned");

        // Performance assertion - should start within 5 seconds
        assertTrue(loadTimeMillis < 5000,
                 "QLever engine should start within 5 seconds, but took " + loadTimeMillis + " ms");

        logger.info("✓ Library loaded successfully in {} ms", loadTimeMillis);
    }

    /**
     * Test 2: Index can be created from test fixture
     *
     * Verifies that an index can be created and populated with test data.
     * This ensures the data loading functionality works.
     */
    @Test
    @DisplayName("Index Creation from Test Fixture")
    void testIndexCreationFromTestFixture() throws Exception {
        // Verify test data file exists
        Path dataFile = testDataDir.resolve("yawl-test-data.ttl");
        assertTrue(Files.exists(dataFile), "Test data file should exist");

        // Query to verify data was loaded
        String countQuery = "SELECT (COUNT(*) AS ?count) WHERE { ?s ?p ?o }";
        QLeverQueryResult result = qleverEngine.executeSparqlQuery(countQuery);

        assertEquals(200, result.getStatusCode(), "Count query should succeed");
        assertTrue(result.hasResults(), "Query should return results");

        // Parse result
        QLeverResultParser parser = new QLeverResultParser(result.getResponse());
        Map<String, Object> firstRow = parser.getFirstRow();
        String countStr = firstRow.get("count").toString();
        long tripleCount = Long.parseLong(countStr);

        // Should have at least our test triples
        assertTrue(tripleCount > 0, "Index should contain triples, found " + tripleCount);

        // Additional assertion on performance - index creation should be reasonable
        assertTrue(loadTimeMillis < 10000,
                 "Index creation should complete within 10 seconds, but took " + loadTimeMillis + " ms");

        logger.info("✓ Index created with {} triples in {} ms", tripleCount, loadTimeMillis);
    }

    /**
     * Test 3: Simple SELECT query returns results
     *
     * Tests the most common query type (SELECT) and verifies that
     * results are returned in the expected format.
     */
    @Test
    @DisplayName("Simple SELECT Query Returns Results")
    void testSimpleSelectQueryReturnsResults() throws Exception {
        // Test SELECT query
        String sparqlQuery = "SELECT ?workitem ?name ?status WHERE { " +
                           "?workitem a yawl:WorkItem ; " +
                           "yawl:name ?name ; " +
                           "yawl:status ?status } " +
                           "LIMIT 10";

        long startTime = System.currentTimeMillis();
        QLeverQueryResult result = qleverEngine.executeSparqlQuery(sparqlQuery);
        firstQueryLatencyMillis = System.currentTimeMillis() - startTime;

        // Verify response
        assertEquals(200, result.getStatusCode(), "Query should succeed");
        assertTrue(result.hasResults(), "Query should return results");

        // Parse results
        QLeverResultParser parser = new QLeverResultParser(result.getResponse());
        Map<String, Object>[] rows = parser.getRows();

        // Should have some results
        assertTrue(rows.length > 0, "Should return at least one result");

        // Verify column structure
        String[] columnNames = parser.getColumnNames();
        assertEquals(3, columnNames.length, "Should have 3 columns");
        assertArrayEquals(new String[]{"workitem", "name", "status"}, columnNames);

        // Performance assertion - first query should be responsive
        assertTrue(firstQueryLatencyMillis < 2000,
                 "First query should complete within 2 seconds, but took " + firstQueryLatencyMillis + " ms");

        logger.info("✓ SELECT query returned {} rows in {} ms", rows.length, firstQueryLatencyMillis);
    }

    /**
     * Test 4: Simple CONSTRUCT query returns triples
     *
     * Tests CONSTRUCT queries which return RDF graphs in Turtle format.
     * This is important for graph-based operations.
     */
    @Test
    @DisplayName("Simple CONSTRUCT Query Returns Triples")
    void testSimpleConstructQueryReturnsTriples() throws Exception {
        // Test CONSTRUCT query
        String sparqlQuery = "CONSTRUCT { " +
                          "?workitem yawl:name ?name } " +
                          "WHERE { " +
                          "?workitem a yawl:WorkItem ; " +
                          "yawl:name ?name } " +
                          "LIMIT 5";

        QLeverQueryResult result = qleverEngine.executeSparqlQuery(sparqlQuery, "text/turtle");

        // Verify response
        assertEquals(200, result.getStatusCode(), "Query should succeed");

        String turtleContent = result.getResponse();
        assertNotNull(turtleContent, "Turtle content should not be null");
        assertFalse(turtleContent.isEmpty(), "Turtle content should not be empty");

        // Basic validation of Turtle format
        assertTrue(turtleContent.startsWith("<"), "Turtle should start with URI");
        assertTrue(turtleContent.contains("a "), "Turtle should contain type declaration");
        assertTrue(turtleContent.contains(" . "), "Turtle should end statements with ' . '");

        logger.info("✓ CONSTRUCT query returned {} bytes of Turtle data", turtleContent.length());
    }

    /**
     * Test 5: Engine reports available
     *
     * Verifies that the engine correctly reports its status and
     * provides essential information about its configuration.
     */
    @Test
    @DisplayName("Engine Reports Available")
    void testEngineReportsAvailable() {
        // Test engine status
        assertTrue(qleverEngine.isRunning(), "Engine should report as running");
        assertTrue(qleverEngine.getServerPort() > 0, "Engine should have a valid port");

        // Test engine configuration
        QLeverEngineConfiguration config = qleverEngine.getConfiguration();
        assertNotNull(config, "Configuration should be available");
        assertTrue(config.getServerPort() > 0, "Configuration should have port");

        logger.info("✓ Engine reports status: running on port {}", qleverEngine.getServerPort());
    }

    /**
     * Test 6: Close and re-open works
     *
     * Tests the ability to stop and restart the engine multiple times.
     * This is crucial for applications that need to restart or refresh data.
     */
    @Test
    @DisplayName("Close and Re-open Works")
    void testCloseAndReopenWorks() throws Exception {
        // Stop the engine
        qleverEngine.stop();
        assertFalse(qleverEngine.isRunning(), "Engine should be stopped");

        // Create new configuration for restart
        QLeverEngineConfig newConfig = new QLeverEngineConfiguration();
        newConfig.setServerPort(0);
        newConfig.setIndexDir(testIndexDir);
        newConfig.setDataDir(testDataDir);

        // Create and restart engine
        long restartStartTime = System.currentTimeMillis();
        QLeverEngine newEngine = new QLeverEngine(newConfig);
        newEngine.start();

        // Wait for new engine to be ready
        waitForEngineReady(newEngine);
        long restartTime = System.currentTimeMillis() - restartStartTime;

        // Test that the new engine works
        QLeverQueryResult result = newEngine.executeSparqlQuery("SELECT COUNT(*) AS ?count WHERE { ?s ?p ?o }");
        assertEquals(200, result.getStatusCode(), "Restarted engine should work");

        // Verify restart was reasonably fast
        assertTrue(restartTime < 5000,
                 "Engine restart should complete within 5 seconds, but took " + restartTime + " ms");

        // Clean up
        newEngine.stop();

        logger.info("✓ Engine restarted successfully in {} ms", restartTime);
    }

    /**
     * Test 7: Multiple sequential queries work
     *
     * Tests executing multiple queries in sequence to ensure
     * the engine maintains state and handles queries consistently.
     */
    @Test
    @DisplayName("Multiple Sequential Queries Work")
    void testMultipleSequentialQueriesWork() throws Exception {
        List<String> testQueries = Arrays.asList(
            "SELECT (COUNT(*) AS ?totalTriples) WHERE { ?s ?p ?o }",
            "SELECT (COUNT(DISTINCT ?s) AS ?distinctSubjects) WHERE { ?s ?p ?o }",
            "SELECT (COUNT(DISTINCT ?p) AS ?distinctPredicates) WHERE { ?s ?p ?o }",
            "SELECT ?status (COUNT(*) AS ?count) WHERE { ?workitem yawl:status ?status } GROUP BY ?status",
            "SELECT ?priority (COUNT(*) AS ?count) WHERE { ?workitem yawl:priority ?priority } GROUP BY ?priority"
        );

        List<Long> queryTimes = new ArrayList<>();
        long totalTime = 0;

        // Execute queries sequentially
        for (String query : testQueries) {
            long startTime = System.currentTimeMillis();

            QLeverQueryResult result = qleverEngine.executeSparqlQuery(query);
            assertEquals(200, result.getStatusCode(), "Query should succeed: " + query);

            long duration = System.currentTimeMillis() - startTime;
            queryTimes.add(duration);
            totalTime += duration;

            logger.info("Query executed in {} ms", duration);
        }

        // Calculate average
        sequentialQueryAverageLatencyMillis = (double) totalTime / queryTimes.size();

        // Performance assertions
        assertTrue(sequentialQueryAverageLatencyMillis < 1000,
                 "Average query time should be < 1 second, but was " + sequentialQueryAverageLatencyMillis + " ms");

        // No individual query should be too slow
        for (Long time : queryTimes) {
            assertTrue(time < 3000,
                       "Individual query should be < 3 seconds, but took " + time + " ms");
        }

        logger.info("✓ Executed {} sequential queries, average: {:.2f} ms",
                   queryTimes.size(), sequentialQueryAverageLatencyMillis);
    }

    /**
     * Test 8: All media types produce output
     *
     * Tests that the engine can produce output in various formats.
     * This is important for different client applications.
     */
    @Test
    @DisplayName("All Media Types Produce Output")
    void testAllMediaTypesProduceOutput() throws Exception {
        String testQuery = "SELECT ?workitem ?name ?status WHERE { " +
                          "?workitem a yawl:WorkItem ; " +
                          "yawl:name ?name ; " +
                          "yawl:status ?status } " +
                          "LIMIT 3";

        Map<String, String> mediaTypes = Map.of(
            "application/json", "JSON",
            "application/sparql-results+xml", "XML",
            "text/csv", "CSV",
            "text/tab-separated-values", "TSV",
            "text/turtle", "Turtle"
        );

        Map<String, Integer> responseLengths = new HashMap<>();

        for (Map.Entry<String, String> entry : mediaTypes.entrySet()) {
            String mediaType = entry.getKey();
            String formatName = entry.getValue();

            QLeverQueryResult result = qleverEngine.executeSparqlQuery(testQuery, mediaType);

            assertEquals(200, result.getStatusCode(),
                        formatName + " query should succeed");

            String response = result.getResponse();
            assertNotNull(response, formatName + " response should not be null");
            assertFalse(response.isEmpty(), formatName + " response should not be empty");

            responseLengths.put(formatName, response.length());

            // Basic format validation
            switch (mediaType) {
                case "application/json":
                    assertTrue(response.contains("\"head\""), "JSON should contain head");
                    break;
                case "application/sparql-results+xml":
                    assertTrue(response.contains("<result>"), "XML should contain results");
                    break;
                case "text/csv":
                    assertTrue(response.contains(","), "CSV should contain commas");
                    break;
                case "text/tab-separated-values":
                    assertTrue(response.contains("\t"), "TSV should contain tabs");
                    break;
                case "text/turtle":
                    assertTrue(response.startsWith("<"), "Turtle should start with URI");
                    break;
            }

            logger.info("✓ {} output: {} bytes", formatName, response.length());
        }

        // Performance assertion for media type conversion
        long longestResponse = responseLengths.values().stream()
            .mapToInt(Integer::intValue)
            .max()
            .orElse(0);

        assertTrue(longestResponse < 10000,
                   "Longest response should be < 10KB, but was " + longestResponse + " bytes");

        logger.info("✓ All media types produced valid output");
    }

    /**
     * Test 9: Performance metrics summary
     *
     * Collects and reports all performance metrics gathered during smoke tests.
     * This provides a quick overview of system performance.
     */
    @Test
    @DisplayName("Performance Metrics Summary")
    void testPerformanceMetricsSummary() {
        logger.info("=== QLever Smoke Test Performance Summary ===");
        logger.info("Load Time: {} ms", loadTimeMillis);
        logger.info("First Query Latency: {} ms", firstQueryLatencyMillis);
        logger.info("Sequential Query Average: {:.2f} ms", sequentialQueryAverageLatencyMillis);

        // Performance assertions for overall health
        assertTrue(loadTimeMillis < 10000,
                   "Total load time should be < 10 seconds");
        assertTrue(firstQueryLatencyMillis < 2000,
                   "First query should be < 2 seconds");
        assertTrue(sequentialQueryAverageLatencyMillis < 1000,
                   "Average query should be < 1 second");

        // Performance ratios
        double loadToQueryRatio = (double) loadTimeMillis / firstQueryLatencyMillis;
        logger.info("Load/Query Time Ratio: {:.2f}", loadToQueryRatio);

        // Ratio should be reasonable (load shouldn't be orders of magnitude slower than query)
        assertTrue(loadToQueryRatio < 10,
                   "Load time should not be >10x query time");

        logger.info("✓ All performance metrics within acceptable ranges");
    }

    /**
     * Helper method to wait for a specific engine to be ready
     */
    private void waitForEngineReady(QLeverEngine engine) throws InterruptedException {
        int maxAttempts = 30;
        int attempt = 0;

        while (attempt < maxAttempts) {
            if (engine.isRunning() && engine.getServerPort() > 0) {
                return;
            }

            attempt++;
            Thread.sleep(100);
        }

        throw new IllegalStateException("Engine failed to start within 3 seconds");
    }
}