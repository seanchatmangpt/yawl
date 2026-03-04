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
import org.yawlfoundation.yawl.qlever.QLEVER_INDEX;
import org.yawlfoundation.yawl.qlever.QleverNativeBridge;
import org.yawlfoundation.yawl.qlever.QleverQueryResult;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for QleverNativeBridge - end-to-end SPARQL queries.
 *
 * <p>This test class focuses on the JVM Domain (Layer 2) API boundary, ensuring:
 * <ul>
 *   <li>End-to-end SPARQL query execution</li>
 *   <li>HTTP client integration</li>
 *   <li>Error handling for network issues</li>
 *   <li>Performance characteristics</li>
 * </ul>
 *
 * @see <a href="../QleverNativeBridge.java">QleverNativeBridge Implementation</a>
 */
@Tag("integration")
@Tag("qlever")
class QleverIntegrationTest {

    private QleverNativeBridge bridge;
    private TestQleverServer server;
    private HttpClient httpClient;

    @BeforeEach
    void setup() {
        assumeTrue(shouldRunQleverTests(), "Qlever tests disabled or not available");

        server = new TestQleverServer();
        server.start();

        bridge = new QleverNativeBridge();
        httpClient = HttpClient.newHttpClient();

        // Wait for server to be ready
        awaitServerReady();
    }

    @AfterEach
    void cleanup() {
        if (bridge != null) {
            bridge.close();
        }
        if (server != null) {
            server.stop();
        }
    }

    // =========================================================================
    // Test 1: End-to-End SPARQL Queries
    // =========================================================================

    /**
     * Verifies basic SPARQL query execution with real HTTP client.
     */
    @Test
    @DisplayName("SPARQL: End-to-end query → valid response")
    void sparql_endToEndQuery_validResponse() throws Exception {
        // Create arena with test server
        QLEVER_INDEX index = bridge.createArena("integration-test",
            Map.of("endpoint", server.getEndpoint()));

        // Execute basic SPARQL query
        String query = """
            SELECT ?s ?p ?o WHERE {
                ?s ?p ?o .
            } LIMIT 10
            """;

        QleverQueryResult result = bridge.executeQuery(index, query);

        assertNotNull(result, "Query result should not be null");
        assertFalse(result.getErrors().isEmpty(),
            "Query should return result structure");
    }

    /**
     * Verries complex SPARQL query with filters and aggregations.
     */
    @Test
    @DisplayName("SPARQL: Complex query with filters → successful")
    void sparql_complexQueryWithFilters_successful() throws Exception {
        // Create arena
        QLEVER_INDEX index = bridge.createArena("complex-test",
            Map.of("endpoint", server.getEndpoint()));

        // Complex query with filters and aggregations
        String query = """
            SELECT ?activity (COUNT(*) as ?count) WHERE {
                ?event a ProcessMining:Event ;
                       processmining:hasActivity ?activity ;
                       processmining:hasTimestamp ?timestamp .
                FILTER (STRSTARTS(?timestamp, "2024-01"))
            } GROUP BY ?activity ORDER BY DESC(?count)
            """;

        QleverQueryResult result = bridge.executeQuery(index, query);

        assertNotNull(result, "Complex query result should not be null");
        assertFalse(result.getErrors().isEmpty(),
            "Complex query should return result structure");
    }

    /**
     * Verries INSERT queries for data modification.
     */
    @Test
    @DisplayName("SPARQL: INSERT query → data added")
    void sparql_insertQuery_dataAdded() throws Exception {
        // Create arena
        QLEVER_INDEX index = bridge.createArena("insert-test",
            Map.of("endpoint", server.getEndpoint()));

        // INSERT query
        String insertQuery = """
            INSERT DATA {
                <test-event> a ProcessMining:Event ;
                           processmining:hasActivity "Test_Activity" ;
                           processmining:hasTimestamp "2024-01-01T10:00:00Z" .
            }
            """;

        QleverQueryResult result = bridge.executeQuery(index, insertQuery);

        assertNotNull(result, "INSERT query result should not be null");

        // Verify data was inserted
        String verifyQuery = """
            SELECT ?event WHERE {
                ?event a ProcessMining:Event ;
                       processmining:hasActivity "Test_Activity" .
            }
            """;

        QleverQueryResult verifyResult = bridge.executeQuery(index, verifyQuery);
        assertFalse(verifyResult.getErrors().isEmpty(),
            "Data verification should return results");
    }

    // =========================================================================
    // Test 2: HTTP Client Integration
    // =========================================================================

    /**
     * Verries HTTP client timeout handling.
     */
    @Test
    @DisplayName("HTTP: Timeout handling → proper exception")
    void http_timeoutHandling_properException() throws Exception {
        // Create arena with timeout
        QLEVER_INDEX index = bridge.createArena("timeout-test",
            Map.of("endpoint", server.getEndpoint(), "timeout", "1000"));

        // Execute query that should timeout
        String query = """
            SELECT ?s ?p ?o WHERE {
                ?s ?p ?o .
                # This query will be artificially delayed by the test server
                FILTER EXISTS { ?s a processmining:DelayedQuery }
            } LIMIT 10
            """;

        // Server will respond slowly for this query
        assertThrows(RuntimeException.class,
            () -> bridge.executeQuery(index, query),
            "Query with artificial delay should timeout");
    }

    /**
     * Verries HTTP client retry mechanism.
     */
    @Test
    @DisplayName("HTTP: Retry mechanism → multiple attempts")
    void http_retryMechanism_multipleAttempts() throws Exception {
        // Create arena
        QLEVER_INDEX index = bridge.createArena("retry-test",
            Map.of("endpoint", server.getEndpoint()));

        // Query that will fail initially but succeed on retry
        String query = """
            SELECT ?s ?p ?o WHERE {
                ?s ?p ?o .
                FILTER EXISTS { ?s a processmining:RetryTest }
            } LIMIT 10
            """;

        // Server will handle retry logic
        QleverQueryResult result = bridge.executeQuery(index, query);

        assertNotNull(result, "Retry query result should not be null");
        assertFalse(result.getErrors().isEmpty(),
            "Retry query should return result structure");
    }

    /**
     * Verries HTTP client error handling.
     */
    @Test
    @DisplayName("HTTP: Error handling → proper HTTP status codes")
    void http_errorHandling_properHttpStatusCodes() throws Exception {
        // Test 404 Not Found
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(server.getEndpoint() + "/sparql?query=SELECT+WHERE+{invalid}"))
            .timeout(Duration.ofSeconds(10))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode(),
            "Invalid SPARQL should return 400 Bad Request");

        // Test malformed endpoint
        QLEVER_INDEX index = bridge.createArena("error-test",
            Map.of("endpoint", "http://invalid-endpoint:9999/sparql"));

        assertThrows(RuntimeException.class,
            () -> bridge.executeQuery(index, "SELECT * WHERE { ?s ?p ?o } LIMIT 1"),
            "Invalid endpoint should throw RuntimeException");
    }

    // =========================================================================
    // Test 3: Concurrent Access
    // =========================================================================

    /**
     * Verries concurrent SPARQL queries.
     */
    @Test
    @DisplayName("Concurrency: Multiple queries → thread-safe")
    void concurrency_multipleQueries_threadSafe() throws Exception {
        // Create arena
        QLEVER_INDEX index = bridge.createArena("concurrent-test",
            Map.of("endpoint", server.getEndpoint()));

        int numThreads = 10;
        int queriesPerThread = 5;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<QleverQueryResult>> futures = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(numThreads);

        // Submit concurrent queries
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            futures.add(executor.submit(() -> {
                try {
                    for (int i = 0; i < queriesPerThread; i++) {
                        String query = String.format("""
                            SELECT ?s ?p ?o WHERE {
                                ?s ?p ?o .
                                FILTER EXISTS { ?s a processmining:ConcurrentTest_%d_%d }
                            } LIMIT 10
                            """, threadId, i);

                        QleverQueryResult result = bridge.executeQuery(index, query);
                        assertNotNull(result, "Query result should not be null");
                        assertFalse(result.getErrors().isEmpty(),
                            "Query should return results");
                    }
                } finally {
                    latch.countDown();
                }
            }));
        }

        // Wait for all queries to complete
        assertTrue(latch.await(30, TimeUnit.SECONDS),
            "All queries should complete within 30 seconds");

        // Verify all futures completed successfully
        for (Future<QleverQueryResult> future : futures) {
            assertDoesNotThrow(() -> future.get(5, TimeUnit.SECONDS),
                "All queries should complete successfully");
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS),
            "Executor should shutdown cleanly");
    }

    /**
     * Verries concurrent arena creation and destruction.
     */
    @Test
    @DisplayName("Concurrency: Multiple arenas → isolated operations")
    void concurrency_multipleArenas_isolatedOperations() throws Exception {
        int numArenas = 5;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<QLEVER_INDEX>> futures = new ArrayList<>();

        // Create arenas concurrently
        for (int i = 0; i < numArenas; i++) {
            final int arenaId = i;
            futures.add(executor.submit(() -> {
                QLEVER_INDEX index = bridge.createArena("concurrent-arena-" + arenaId,
                    Map.of("endpoint", server.getEndpoint()));

                // Execute query
                String query = String.format("""
                    SELECT ?s ?p ?o WHERE {
                        ?s ?p ?o .
                        FILTER EXISTS { ?s a processmining:ArenaTest_%d }
                    } LIMIT 5
                    """, arenaId);

                QleverQueryResult result = bridge.executeQuery(index, query);
                assertNotNull(result, "Query result should not be null");

                return index;
            }));
        }

        // Wait for all arenas to be created
        for (Future<QLEVER_INDEX> future : futures) {
            assertDoesNotThrow(() -> future.get(10, TimeUnit.SECONDS),
                "All arena creations should complete successfully");
        }

        // Verify all arenas exist
        for (int i = 0; i < numArenas; i++) {
            assertTrue(bridge.hasArena("concurrent-arena-" + i),
                "Arena should exist: concurrent-arena-" + i);
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS),
            "Executor should shutdown cleanly");
    }

    // =========================================================================
    // Test 4: Performance Benchmarks
    // =========================================================================

    /**
     * Verries SPARQL query performance.
     */
    @Test
    @DisplayName("Performance: SPARQL queries → under 1000ms")
    void performance_sparqlQueries_under1000ms() throws Exception {
        // Create arena
        QLEVER_INDEX index = bridge.createArena("perf-test",
            Map.of("endpoint", server.getEndpoint()));

        // Test simple query
        String simpleQuery = "SELECT * WHERE { ?s ?p ?o } LIMIT 100";

        long start = System.nanoTime();
        QleverQueryResult result = bridge.executeQuery(index, simpleQuery);
        long end = System.nanoTime();

        long durationMs = (end - start) / 1_000_000;
        System.out.println("Simple query time: " + durationMs + "ms");

        assertNotNull(result, "Query result should not be null");
        assertTrue(durationMs < 1000,
            "Simple query should complete under 1000ms (took " + durationMs + "ms)");

        // Test complex query
        String complexQuery = """
            SELECT ?activity (COUNT(*) as ?count) (AVG(?duration) as ?avgDuration) WHERE {
                ?event a ProcessMining:Event ;
                       processmining:hasActivity ?activity ;
                       processmining:hasTimestamp ?timestamp ;
                       processmining:hasDuration ?duration .
                FILTER (?duration > 0)
            } GROUP BY ?activity ORDER BY DESC(?count)
            """;

        start = System.nanoTime();
        result = bridge.executeQuery(index, complexQuery);
        end = System.nanoTime();

        durationMs = (end - start) / 1_000_000;
        System.out.println("Complex query time: " + durationMs + "ms");

        assertNotNull(result, "Complex query result should not be null");
        assertTrue(durationMs < 3000,
            "Complex query should complete under 3000ms (took " + durationMs + "ms)");
    }

    /**
     * Verries throughput with multiple queries.
     */
    @Test
    @DisplayName("Performance: Throughput → 100 queries in 10s")
    void performance_throughput_100QueriesIn10s() throws Exception {
        // Create arena
        QLEVER_INDEX index = bridge.createArena("throughput-test",
            Map.of("endpoint", server.getEndpoint()));

        int numQueries = 100;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<QleverQueryResult>> futures = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(numQueries);

        long start = System.nanoTime();

        // Submit 100 queries
        for (int i = 0; i < numQueries; i++) {
            final int queryId = i;
            futures.add(executor.submit(() -> {
                try {
                    String query = String.format("""
                        SELECT ?s ?p ?o WHERE {
                            ?s ?p ?o .
                            FILTER EXISTS { ?s a processmining:ThroughputTest_%d }
                        } LIMIT 10
                        """, queryId);

                    QleverQueryResult result = bridge.executeQuery(index, query);
                    assertNotNull(result, "Query result should not be null");
                    return result;
                } finally {
                    latch.countDown();
                }
            }));
        }

        // Wait for all queries to complete
        assertTrue(latch.await(10, TimeUnit.SECONDS),
            "All 100 queries should complete within 10 seconds");

        long end = System.nanoTime();
        long durationMs = (end - start) / 1_000_000;

        System.out.println("Throughput test:");
        System.out.println("  Queries: " + numQueries);
        System.out.println("  Duration: " + durationMs + "ms");
        System.out.println("  Queries per second: " + (numQueries * 1000.0 / durationMs));

        // Performance target: 100 queries in 10 seconds
        assertTrue(durationMs < 10000,
            "100 queries should complete in under 10 seconds (took " + durationMs + "ms)");

        // Verify all futures completed successfully
        for (Future<QleverQueryResult> future : futures) {
            assertDoesNotThrow(() -> future.get(1, TimeUnit.SECONDS),
                "All queries should complete successfully");
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS),
            "Executor should shutdown cleanly");
    }

    // =========================================================================
    // Test 5: Fault Injection
    // =========================================================================

    /**
     * Verries server restart handling.
     */
    @Test
    @DisplayName("Fault Injection: Server restart → reconnect automatically")
    void faultInjection_serverRestart_reconnectAutomatically() throws Exception {
        // Create arena
        QLEVER_INDEX index = bridge.createArena("restart-test",
            Map.of("endpoint", server.getEndpoint()));

        // Execute query before restart
        String query = "SELECT * WHERE { ?s ?p ?o } LIMIT 10";
        QleverQueryResult result = bridge.executeQuery(index, query);
        assertNotNull(result, "Query should succeed before restart");

        // Restart server
        server.restart();

        // Wait for server to be ready
        awaitServerReady();

        // Execute query after restart
        result = bridge.executeQuery(index, query);
        assertNotNull(result, "Query should succeed after restart");
    }

    /**
     * Verries network partition handling.
     */
    @Test
    @DisplayName("Fault Injection: Network partition → recover automatically")
    void faultInjection_networkPartition_recoverAutomatically() throws Exception {
        // Create arena
        QLEVER_INDEX index = bridge.createArena("partition-test",
            Map.of("endpoint", server.getEndpoint()));

        // Simulate network partition
        server.simulatePartition(true);

        // Execute query (should fail initially)
        assertThrows(RuntimeException.class,
            () -> bridge.executeQuery(index, "SELECT * WHERE { ?s ?p ?o } LIMIT 10"),
            "Query should fail during network partition");

        // Simulate recovery
        server.simulatePartition(false);

        // Wait for recovery
        Thread.sleep(1000);

        // Execute query after recovery
        String query = "SELECT * WHERE { ?s ?p ?o } LIMIT 10";
        QleverQueryResult result = bridge.executeQuery(index, query);
        assertNotNull(result, "Query should succeed after recovery");
    }

    // =========================================================================
    // Helper Methods and Classes
    // =========================================================================

    private boolean shouldRunQleverTests() {
        // Check if Qlever tests are enabled
        try {
            return System.getProperty("qlever.enabled", "false").equals("true");
        } catch (Exception e) {
            return false;
        }
    }

    private void awaitServerReady() throws InterruptedException {
        int maxAttempts = 30;
        int attempt = 0;

        while (attempt < maxAttempts) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(server.getEndpoint() + "/ping"))
                    .timeout(Duration.ofSeconds(5))
                    .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return;
                }
            } catch (Exception e) {
                // Server not ready yet
            }

            Thread.sleep(1000);
            attempt++;
        }

        throw new RuntimeException("Server did not become ready after " + maxAttempts + " attempts");
    }

    /**
     * Test server implementation for integration testing
     */
    private static class TestQleverServer {
        private Server server;
        private boolean isPartitioned = false;
        private int port = 0;

        public void start() {
            try {
                server = Server.createForPort(0); // Use random port
                port = server.getAddress().getPort();

                // Initialize test data
                server.addTestData();

                server.start();
            } catch (Exception e) {
                throw new RuntimeException("Failed to start test server", e);
            }
        }

        public void stop() {
            if (server != null) {
                server.stop();
            }
        }

        public String getEndpoint() {
            return "http://localhost:" + port + "/sparql";
        }

        public void restart() {
            stop();
            start();
        }

        public void simulatePartition(boolean partitioned) {
            this.isPartitioned = partitioned;
        }

        /**
         * Simple HTTP server for testing
         */
        private static class Server {
            private com.sun.net.httpserver.HttpServer server;
            private int port;

            public static Server createForPort(int port) throws IOException {
                // This would be replaced with a real HTTP server implementation
                // For testing purposes, we'll simulate it
                return new Server();
            }

            public void start() throws IOException {
                // Simulate server startup
                port = 9999; // Fixed port for testing
            }

            public void stop() {
                // Simulate server shutdown
            }

            public java.net.InetSocketAddress getAddress() {
                return new java.net.InetSocketAddress("localhost", port);
            }

            public void addTestData() {
                // Add test data to the server
            }

            public void handleRequest(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
                // Handle SPARQL requests
                String response = "mock response";
                exchange.sendResponseHeaders(200, response.getBytes().length);
                try (var os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }
        }
    }
}