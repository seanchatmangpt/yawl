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

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for QLever Embedded demonstrating real-world usage patterns.
 * These tests focus on integration with YAWL workflow engine scenarios.
 */
@DisplayName("QLever Integration Tests")
public class QLeverIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(QLeverIntegrationTest.class);
    private QLeverEngine qleverEngine;
    private static final String TEST_YAWL_INDEX = "yawl-workflow-index";

    @BeforeEach
    void setUp() throws Exception {
        // Initialize QLever with YAWL-specific configuration
        QLeverEngineConfiguration config = new QLeverEngineConfiguration();
        config.setServerPort(0);
        config.setIndexDir(createTestIndexDir("yawl-integration-index"));
        config.setDataDir(createTestDataDir("yawl-integration-data"));
        config.setMaxQueryTimeout(30000); // 30 seconds
        config.setCacheSize(1000);

        qleverEngine = new QLeverEngine(config);
        qleverEngine.start();

        // Initialize YAWL-specific data
        initializeYawlTestData();

        logger.info("QLever engine started for integration tests");
    }

    @AfterEach
    void tearDown() {
        if (qleverEngine != null && qleverEngine.isRunning()) {
            qleverEngine.stop();
        }
        cleanupTestDirectories();
    }

    private Path createTestIndexDir(String name) throws IOException {
        Path dir = Files.createTempDirectory(name);
        logger.info("Created test index directory: {}", dir);
        return dir;
    }

    private Path createTestDataDir(String name) throws IOException {
        Path dir = Files.createTempDirectory(name);
        logger.info("Created test data directory: {}", dir);
        return dir;
    }

    private void initializeYawlTestData() throws IOException {
        // Create sample YAWL workflow data
        String yawlData = """
            @prefix yawl: <http://example.org/yawl#> .
            @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

            # Work Items
            <http://example.org/workitems/wi-001> a yawl:WorkItem ;
                yawl:name "Task Approval" ;
                yawl:status "pending" ;
                yawl:created "2024-01-15T10:00:00"^^xsd:dateTime ;
                yawl:due "2024-01-20T17:00:00"^^xsd:dateTime ;
                yawl:priority "high" ;
                yawl:case <http://example.org/cases/case-001> .

            <http://example.org/workitems/wi-002> a yawl:WorkItem ;
                yawl:name "Data Verification" ;
                yawl:status "in_progress" ;
                yawl:created "2024-01-16T09:30:00"^^xsd:dateTime ;
                yawl:due "2024-01-18T15:00:00"^^xsd:dateTime ;
                yawl:priority "medium" ;
                yawl:case <http://example.org/cases/case-001> .

            # Cases
            <http://example.org/cases/case-001> a yawl:Case ;
                yawl:name "Customer Complaint" ;
                yawl:status "active" ;
                yawl:created "2024-01-15T09:00:00"^^xsd:dateTime ;
                yawl:priority "high" ;
                yawl:assignedTo "user:john.doe" .

            <http://example.org/cases/case-002> a yawl:Case ;
                yawl:name "Service Request" ;
                yawl:status "completed" ;
                yawl:created "2024-01-14T14:00:00"^^xsd:dateTime ;
                yawl:priority "low" ;
                yawl:assignedTo "user:jane.smith" .

            # Users
            <http://example.org/users/john.doe> a yawl:User ;
                yawl:name "John Doe" ;
                yawl:email "john.doe@example.com" ;
                yawl:department "Operations" .

            <http://example.org/users/jane.smith> a yawl:User ;
                yawl:name "Jane Smith" ;
                yawl:email "jane.smith@example.com" ;
                yawl:department "Customer Service" .
            """;

        // Write test data to file
        Path dataFile = createTestDataDir("yawl-integration-data")
            .resolve("yawl-workflow-data.ttl");
        Files.writeString(dataFile, yawlData);

        logger.info("Created YAWL test data: {}", dataFile);
    }

    private void cleanupTestDirectories() {
        try {
            Path baseDir = Paths.get(System.getProperty("java.io.tmpdir"));
            deleteDirectory(baseDir.resolve("yawl-integration-index"));
            deleteDirectory(baseDir.resolve("yawl-integration-data"));
        } catch (IOException e) {
            logger.error("Error cleaning up test directories", e);
        }
    }

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
     * Test case 1: Query active work items assigned to users
     *
     * This demonstrates a typical workflow query scenario:
     * - Find all pending work items
     * - Get their assignments
     * - Filter by priority
     * - Return formatted results
     */
    @Test
    @DisplayName("Query Active Work Items")
    void testQueryActiveWorkItems() throws Exception {
        // SPARQL query to find active work items
        String sparqlQuery = "SELECT ?workitem ?name ?status ?priority ?caseName ?assignedTo " +
                           "WHERE { " +
                           "?workitem a yawl:WorkItem ; " +
                           "yawl:name ?name ; " +
                           "yawl:status ?status ; " +
                           "yawl:priority ?priority ; " +
                           "yawl:case ?case ; " +
                           "yawl:assignedTo ?assignedTo . " +
                           "?case yawl:name ?caseName . " +
                           "FILTER (?status = \"pending\") } " +
                           "ORDER BY DESC(?priority) ?caseName";

        QLeverQueryResult result = qleverEngine.executeSparqlQuery(sparqlQuery);

        assertEquals(200, result.getStatusCode());
        assertTrue(result.hasResults());

        // Parse and process results
        QLeverResultParser parser = new QLeverResultParser(result.getResponse());
        Map<String, Object>[] workItems = parser.getRows();

        logger.info("Found {} active work items:", workItems.length);

        for (Map<String, Object> item : workItems) {
            String workitem = item.get("workitem").toString();
            String name = item.get("name").toString();
            String status = item.get("status").toString();
            String priority = item.get("priority").toString();
            String caseName = item.get("caseName").toString();
            String assignedTo = item.get("assignedTo").toString();

            logger.info("  WorkItem: {} - Case: {} - Priority: {} - Assignee: {}",
                       name, caseName, priority, assignedTo);

            // Validate expected fields
            assertNotNull(name, "Work item should have a name");
            assertNotNull(status, "Work item should have a status");
            assertNotNull(priority, "Work item should have a priority");
        }
    }

    /**
     * Test case 2: Workflow case lifecycle analysis
     *
     * Demonstrates tracking case states over time:
     * - Find all cases
     * - Calculate duration based on dates
     * - Group by status
     * - Generate summary statistics
     */
    @Test
    @DisplayName("Workflow Case Lifecycle Analysis")
    void testWorkflowCaseLifecycle() throws Exception {
        String sparqlQuery = "SELECT ?case ?name ?status ?duration WHERE { " +
                           "?case a yawl:Case ; " +
                           "yawl:name ?name ; " +
                           "yawl:status ?status ; " +
                           "yawl:created ?created . " +
                           "OPTIONAL { ?case yawl:completed ?completed . " +
                           "BIND((?completed - ?created) AS ?duration) } " +
                           "FILTER (?status = \"active\") } " +
                           "ORDER BY DESC(?created)";

        QLeverQueryResult result = qleverEngine.executeSparqlQuery(sparqlQuery);

        assertEquals(200, result.getStatusCode());
        assertTrue(result.hasResults());

        QLeverResultParser parser = new QLeverResultParser(result.getResponse());
        Map<String, Object>[] cases = parser.getRows();

        logger.info("Found {} active cases:", cases.length);

        for (Map<String, Object> caseItem : cases) {
            String caseUri = caseItem.get("case").toString();
            String name = caseItem.get("name").toString();
            String status = caseItem.get("status").toString();
            Object durationObj = caseItem.get("duration");

            String duration = durationObj != null ? durationObj.toString() : "N/A";

            logger.info("  Case: {} - Status: {} - Duration: {}",
                       name, status, duration);
        }
    }

    /**
     * Test case 3: Workload analysis by department and priority
     *
     * Demonstrates analytics queries for workload management:
     * - Count work items by department
     * - Analyze priority distribution
     * - Calculate user workload
     * - Generate insights
     */
    @Test
    @DisplayName("Workload Analysis by Department")
    void testWorkloadAnalysisByDepartment() throws Exception {
        // Query for workload analysis
        String sparqlQuery = "SELECT ?department (COUNT(DISTINCT ?user) AS ?users) " +
                           "(COUNT(DISTINCT ?workitem) AS ?workItems) " +
                           "(COUNT(DISTINCT ?case) AS ?cases) " +
                           "WHERE { " +
                           "?user yawl:department ?department . " +
                           "OPTIONAL { ?workitem yawl:assignedTo ?user } . " +
                           "OPTIONAL { ?case yawl:assignedTo ?user } " +
                           "} " +
                           "GROUP BY ?department " +
                           "ORDER BY DESC(?workItems)";

        QLeverQueryResult result = qleverEngine.executeSparqlQuery(sparqlQuery);

        assertEquals(200, result.getStatusCode());

        QLeverResultParser parser = new QLeverResultParser(result.getResponse());
        Map<String, Object>[] workloadStats = parser.getRows();

        logger.info("Workload analysis by department:");
        for (Map<String, Object> dept : workloadStats) {
            String department = dept.get("department").toString();
            String userCount = dept.get("users").toString();
            String workItemCount = dept.get("workItems").toString();
            String caseCount = dept.get("cases").toString();

            logger.info("  Department: {} - Users: {} - Work Items: {} - Cases: {}",
                       department, userCount, workItemCount, caseCount);
        }
    }

    /**
     * Test case 4: Performance optimization with batch queries
     *
     * Demonstrates efficient batch query execution:
     * - Execute multiple queries concurrently
     * - Use connection pooling if available
     * - Cache results for repeated queries
     * - Monitor query performance
     */
    @Test
    @DisplayName("Performance Optimization with Batch Queries")
    void testBatchQueryPerformance() throws Exception {
        List<String> queries = Arrays.asList(
            "SELECT (COUNT(*) AS ?totalWorkItems) WHERE { ?wi a yawl:WorkItem }",
            "SELECT (COUNT(*) AS ?totalCases) WHERE { ?c a yawl:Case }",
            "SELECT (COUNT(*) AS ?totalUsers) WHERE { ?u a yawl:User }",
            "SELECT ?status (COUNT(*) AS ?count) WHERE { ?wi yawl:status ?status } GROUP BY ?status"
        );

        // Execute queries concurrently
        ExecutorService executor = Executors.newFixedThreadPool(4);
        List<Future<QLeverQueryResult>> futures = new ArrayList<>();

        for (String query : queries) {
            Future<QLeverQueryResult> future = executor.submit(() -> {
                long startTime = System.currentTimeMillis();
                QLeverQueryResult result = qleverEngine.executeSparqlQuery(query);
                long duration = System.currentTimeMillis() - startTime;
                logger.info("Query executed in {} ms: {}", duration,
                           query.substring(0, Math.min(50, query.length())) + "...");
                return result;
            });
            futures.add(future);
        }

        // Collect results
        Map<String, QLeverQueryResult> results = new HashMap<>();
        for (int i = 0; i < futures.size(); i++) {
            QLeverQueryResult result = futures.get(i).get();
            results.put(queries.get(i), result);
        }

        // Verify all queries succeeded
        for (Map.Entry<String, QLeverQueryResult> entry : results.entrySet()) {
            assertEquals(200, entry.getValue().getStatusCode(),
                        "Query should succeed: " + entry.getKey());
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        logger.info("Batch query performance test completed successfully");
    }

    /**
     * Test case 5: Error handling for large result sets
     *
     * Demonstrates proper handling of large query results:
     * - Use pagination with LIMIT/OFFSET
     * - Stream results instead of loading all at once
     * - Implement timeouts for large queries
     * - Handle memory efficiently
     */
    @Test
    @DisplayName("Large Result Set Handling")
    void testLargeResultSetHandling() throws Exception {
        // Test pagination
        int pageSize = 100;
        int totalPages = 3; // Simulate multiple pages
        List<Map<String, Object>> allResults = new ArrayList<>();

        for (int page = 0; page < totalPages; page++) {
            String query = String.format(
                "SELECT ?workitem ?name ?status WHERE { " +
                "?workitem a yawl:WorkItem ; " +
                "yawl:name ?name ; " +
                "yawl:status ?status } " +
                "LIMIT %d OFFSET %d",
                pageSize, page * pageSize
            );

            QLeverQueryResult result = qleverEngine.executeSparqlQuery(query);

            assertEquals(200, result.getStatusCode());

            QLeverResultParser parser = new QLeverResultParser(result.getResponse());
            Map<String, Object>[] pageResults = parser.getRows();

            allResults.addAll(Arrays.asList(pageResults));

            logger.info("Page {} loaded: {} results", page + 1, pageResults.length);
        }

        // Verify we got expected results
        assertEquals(pageSize * totalPages, allResults.size(),
                    "Should get all paginated results");

        // Test streaming pattern (simulated)
        QLeverQueryResult streamResult = qleverEngine.executeSparqlQuery(
            "SELECT ?workitem ?name ?status WHERE { " +
            "?workitem a yawl:WorkItem ; " +
            "yawl:name ?name ; " +
            "yawl:status ?status } " +
            "LIMIT 1000"
        );

        // Process results in chunks to simulate streaming
        QLeverResultParser parser = new QLeverResultParser(streamResult.getResponse());
        Map<String, Object>[] allRows = parser.getRows();

        int chunkSize = 100;
        for (int i = 0; i < allRows.length; i += chunkSize) {
            int end = Math.min(i + chunkSize, allRows.length);
            Map<String, Object>[] chunk = Arrays.copyOfRange(allRows, i, end);

            logger.info("Processing chunk {}-{} of {} results",
                       i + 1, end, allRows.length);

            // Process chunk (e.g., write to database, send to API, etc.)
            // In real implementation, this would be the actual processing
        }

        logger.info("Large result set handling completed");
    }

    /**
     * Test case 6: Integration with YAWL workflow engine events
     *
     * Demonstrates how to use QLever to store and query workflow events:
     * - Store workflow events as triples
     * - Query event patterns
     * - Analyze workflow efficiency
     * - Monitor system performance
     */
    @Test
    @DisplayName("Workflow Event Integration")
    void testWorkflowEventIntegration() throws Exception {
        // Simulate storing workflow events as triples
        List<String> events = Arrays.asList(
            "<http://example.org/events/event-001> a yawl:WorkflowEvent ; " +
            "yawl:timestamp \"2024-01-15T10:00:00\"^^xsd:dateTime ; " +
            "yawl:eventType \"workitem_started\" ; " +
            "yawl:workItem <http://example.org/workitems/wi-001> ; " +
            "yawl:user <http://example.org/users/john.doe> .",

            "<http://example.org/events/event-002> a yawl:WorkflowEvent ; " +
            "yawl:timestamp \"2024-01-15T11:30:00\"^^xsd:dateTime ; " +
            "yawl:eventType \"workitem_completed\" ; " +
            "yawl:workItem <http://example.org/workitems/wi-001> ; " +
            "yawl:user <http://example.org/users/john.doe> .",

            "<http://example.org/events/event-003> a yawl:WorkflowEvent ; " +
            "yawl:timestamp \"2024-01-16T09:30:00\"^^xsd:dateTime ; " +
            "yawl:eventType \"workitem_started\" ; " +
            "yawl:workItem <http://example.org/workitems/wi-002> ; " +
            "yawl:user <http://example.org/users/jane.smith> ."
        );

        // Query event patterns
        String eventQuery = "SELECT ?event ?timestamp ?eventType ?workitem ?user WHERE { " +
                          "?event a yawl:WorkflowEvent ; " +
                          "yawl:timestamp ?timestamp ; " +
                          "yawl:eventType ?eventType ; " +
                          "yawl:workItem ?workitem ; " +
                          "yawl:user ?user } " +
                          "ORDER BY ASC(?timestamp)";

        QLeverQueryResult result = qleverEngine.executeSparqlQuery(eventQuery);

        assertEquals(200, result.getStatusCode());

        QLeverResultParser parser = new QLeverResultParser(result.getResponse());
        Map<String, Object>[] eventsData = parser.getRows();

        logger.info("Workflow events:");
        for (Map<String, Object> event : eventsData) {
            String eventUri = event.get("event").toString();
            String timestamp = event.get("timestamp").toString();
            String eventType = event.get("eventType").toString();
            String workitem = event.get("workitem").toString();
            String user = event.get("user").toString();

            logger.info("  Event: {} - Time: {} - Type: {} - WorkItem: {} - User: {}",
                       eventUri, timestamp, eventType, workitem, user);
        }

        // Analyze workflow efficiency
        String efficiencyQuery = "SELECT ?user (COUNT(?started) AS ?startedCount) " +
                               "(COUNT(?completed) AS ?completedCount) " +
                               "(AVG(?duration) AS ?avgDuration) " +
                               "WHERE { " +
                               "?event yawl:user ?user ; " +
                               "yawl:timestamp ?timestamp ; " +
                               "yawl:eventType ?type . " +
                               "FILTER(?type = \"workitem_started\") " +
                               "BIND(?type AS ?started) . " +
                               "OPTIONAL { " +
                               "  ?completeEvent yawl:user ?user ; " +
                               "  ywl:timestamp ?completeTimestamp ; " +
                               "  ywl:eventType \"workitem_completed\" . " +
                               "  BIND((?completeTimestamp - ?timestamp) AS ?duration) " +
                               "} } " +
                               "GROUP BY ?user";

        QLeverQueryResult efficiencyResult = qleverEngine.executeSparqlQuery(efficiencyQuery);
        assertEquals(200, efficiencyResult.getStatusCode());

        logger.info("Workflow efficiency analysis completed");
    }

    /**
     * Test case 7: Configuration management and tuning
     *
     * Demonstrates different configuration scenarios:
     * - Memory optimization
     * - Query timeout tuning
     * - Cache configuration
     * - Index optimization
     */
    @Test
    @DisplayName("Configuration Management and Tuning")
    void testConfigurationManagement() throws Exception {
        // Test different configurations
        Map<String, QLeverEngineConfiguration> configs = new HashMap<>();

        // High performance configuration
        QLeverEngineConfig highPerfConfig = new QLeverEngineConfiguration();
        highPerfConfig.setMaxQueryTimeout(15000);
        highPerfConfig.setCacheSize(5000);
        highPerfConfig.setServerPort(0);
        highPerfConfig.setIndexDir(createTestIndexDir("high-perf-index"));
        configs.put("high-performance", highPerfConfig);

        // Memory conservative configuration
        QLeverEngineConfig memoryConservativeConfig = new QLeverEngineConfiguration();
        memoryConservativeConfig.setMaxQueryTimeout(30000);
        memoryConservativeConfig.setCacheSize(100);
        memoryConservativeConfig.setServerPort(0);
        memoryConservativeConfig.setIndexDir(createTestIndexDir("conservative-index"));
        configs.put("memory-conservative", memoryConservativeConfig);

        // Test each configuration
        Map<String, Long> executionTimes = new HashMap<>();

        for (Map.Entry<String, QLeverEngineConfiguration> entry : configs.entrySet()) {
            String configName = entry.getKey();
            QLeverEngineConfiguration config = entry.getValue();

            try (QLeverEngine engine = new QLeverEngine(config)) {
                engine.start();
                Thread.sleep(1000); // Wait for startup

                long startTime = System.currentTimeMillis();
                QLeverQueryResult result = engine.executeSparqlQuery(
                    "SELECT COUNT(*) AS ?count WHERE { ?s ?p ?o }");
                long duration = System.currentTimeMillis() - startTime;

                executionTimes.put(configName, duration);
                assertEquals(200, result.getStatusCode());

                logger.info("Configuration '{}' executed in {} ms", configName, duration);
            }
        }

        // Log performance comparison
        logger.info("Configuration performance summary:");
        executionTimes.forEach((config, time) ->
            logger.info("  {}: {} ms", config, time));

        // Find fastest configuration
        String fastestConfig = executionTimes.entrySet().stream()
            .min(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("none");

        logger.info("Fastest configuration: {}", fastestConfig);
    }
}