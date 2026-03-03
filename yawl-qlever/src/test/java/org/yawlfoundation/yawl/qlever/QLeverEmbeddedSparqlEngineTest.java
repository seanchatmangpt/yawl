package org.yawlfoundation.yawl.qlever;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledOnJre;
import org.junit.jupiter.api.condition.JRE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Tests for the embedded QLever SPARQL engine wrapper.
 * Tests integration with YAWL workflow engine and proper lifecycle management.
 */
@DisplayName("QLever Embedded SPARQL Engine Tests")
@EnabledOnJre(JRE.JAVA_25)
class QLeverEmbeddedSparqlEngineTest {

    private QLeverEmbeddedSparqlEngine engine;
    private static final String TEST_RDF = "@prefix ex: <http://example.org/> . ex:subj ex:pred ex:obj .";
    private static final String SIMPLE_QUERY = "SELECT ?s ?p ?o WHERE { ?s ?p ?o }";
    private static final String NAMED_GRAPH_QUERY = "SELECT ?s ?p ?o WHERE { GRAPH ?g { ?s ?p ?o } }";

    @BeforeEach
    void setUp() throws Exception {
        engine = new QLeverEmbeddedSparqlEngine();
        engine.initialize();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (engine != null && engine.isInitialized()) {
            engine.shutdown();
        }
    }

    @Test
    @DisplayName("Engine initialization and shutdown")
    void testEngineLifecycle() throws Exception {
        // Initial state should not be initialized
        assertFalse(engine.isInitialized());

        // Initialize
        engine.initialize();
        assertTrue(engine.isInitialized());

        // Shutdown
        engine.shutdown();
        assertFalse(engine.isInitialized());
    }

    @Test
    @DisplayName("Load RDF data from string")
    void testLoadRdfFromString() throws Exception {
        QLeverResult result = engine.loadRdfData(TEST_RDF, "TURTLE");
        assertEquals(QLeverStatus.READY, result.status());
        assertNotNull(result.metadata());
        assertTrue(result.metadata().contains("loaded"));
    }

    @Test
    @DisplayName("Load RDF data from file")
    void testLoadRdfFromFile() throws Exception {
        // Create temporary file
        Path tempFile = Files.createTempFile("test", ".ttl");
        Files.write(tempFile, TEST_RDF.getBytes());

        try {
            QLeverResult result = engine.loadRdfDataFromFile(tempFile.toString(), "TURTLE");
            assertEquals(QLeverStatus.READY, result.status());
            assertNotNull(result.metadata());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    @Test
    @DisplayName("Execute SPARQL SELECT query")
    void testExecuteSelectQuery() throws Exception {
        // First load some data
        engine.loadRdfData(TEST_RDF, "TURTLE");

        QLeverResult result = engine.executeQuery(SIMPLE_QUERY);
        assertEquals(QLeverStatus.READY, result.status());
        assertNotNull(result.data());
        assertTrue(result.isSuccess());
        // Should contain JSON results
        assertTrue(result.data().contains("\"results\""));
    }

    @Test
    @DisplayName("Execute SPARQL query with variables")
    void testExecuteQueryWithVariables() throws Exception {
        engine.loadRdfData(TEST_RDF, "TURTLE");

        String query = "SELECT ?s WHERE { ?s ex:pred ex:obj }";
        QLeverResult result = engine.executeQuery(query);
        assertEquals(QLeverStatus.READY, result.status());
        assertNotNull(result.data());
    }

    @Test
    @DisplayName("Execute SPARQL query with named graphs")
    void testExecuteQueryWithNamedGraphs() throws Exception {
        // Load data with named graph
        String namedGraphRdf = "@prefix ex: <http://example.org/> . @prefix g: <http://example.org/graph/> . g:graph { ex:subj ex:pred ex:obj } .";
        engine.loadRdfData(namedGraphRdf, "TURTLE");

        QLeverResult result = engine.executeQuery(NAMED_GRAPH_QUERY);
        assertEquals(QLeverStatus.READY, result.status());
        assertNotNull(result.data());
    }

    @Test
    @DisplayName("Execute SPARQL update query")
    void testExecuteUpdate() throws Exception {
        String update = "INSERT DATA { <http://example.org/new> <http://example.org/pred> <http://example.org/new> }";
        QLeverResult result = engine.executeUpdate(update);
        // Updates may not return data, but should have OK status
        assertNotNull(result.status());
    }

    @Test
    @DisplayName("Execute query with timeout")
    void testExecuteQueryWithTimeout() throws Exception {
        engine.loadRdfData(TEST_RDF, "TURTLE");

        QLeverResult result = engine.executeQuery(SIMPLE_QUERY, 1000); // 1 second timeout
        assertEquals(QLeverStatus.READY, result.status());
    }

    @Test
    @DisplayName("Execute query asynchronously")
    void testExecuteQueryAsync() throws Exception {
        engine.loadRdfData(TEST_RDF, "TURTLE");

        CompletableFuture<QLeverResult> future = engine.executeQueryAsync(SIMPLE_QUERY);
        QLeverResult result = future.get(5, TimeUnit.SECONDS); // 5 second timeout

        assertEquals(QLeverStatus.READY, result.status());
        assertNotNull(result.data());
    }

    @Test
    @DisplayName("Get engine statistics")
    void testGetStatistics() throws Exception {
        engine.loadRdfData(TEST_RDF, "TURTLE");

        String stats = engine.getStatistics();
        assertNotNull(stats);
        assertTrue(stats.contains("triples") || stats.contains("queries"));
    }

    @Test
    @DisplayName("Clear engine data")
    void testClearData() throws Exception {
        engine.loadRdfData(TEST_RDF, "TURTLE");
        engine.clearData();

        QLeverResult result = engine.executeQuery(SIMPLE_QUERY);
        // Should return empty results, not error
        assertEquals(QLeverStatus.READY, result.status());
    }

    @Test
    @DisplayName("Engine resilience - restart after failure")
    void testEngineRestartAfterFailure() throws Exception {
        // First, create a failure scenario
        assertThrows(QLeverFfiException.class, () -> {
            engine.simulateFailure();
        });

        // Engine should recover gracefully
        engine.initialize();
        engine.loadRdfData(TEST_RDF, "TURTLE");
        QLeverResult result = engine.executeQuery(SIMPLE_QUERY);
        assertEquals(QLeverStatus.READY, result.status());
    }

    @Test
    @DisplayName("Concurrent queries")
    void testConcurrentQueries() throws Exception {
        // Load test data
        engine.loadRdfData(TEST_RDF, "TURTLE");

        // Execute multiple queries concurrently
        int queryCount = 5;
        CompletableFuture<QLeverResult>[] futures = new CompletableFuture[queryCount];

        for (int i = 0; i < queryCount; i++) {
            final int queryId = i;
            futures[i] = engine.executeQueryAsync(
                "SELECT * WHERE { ?s ?p ?o } LIMIT " + queryId
            );
        }

        // Wait for all queries to complete
        for (CompletableFuture<QLeverResult> future : futures) {
            QLeverResult result = future.get(5, TimeUnit.SECONDS);
            assertEquals(QLeverStatus.READY, result.status());
        }
    }

    @Test
    @DisplayName("Memory management - check memory usage")
    void testMemoryManagement() throws Exception {
        // Load some data
        engine.loadRdfData(TEST_RDF, "TURTLE");

        long initialMemory = engine.getCurrentMemoryUsage();
        assertTrue(initialMemory >= 0);

        // Execute queries to increase memory usage
        for (int i = 0; i < 10; i++) {
            engine.executeQuery(SIMPLE_QUERY);
        }

        long finalMemory = engine.getCurrentMemoryUsage();
        // Memory may increase due to query results
        assertTrue(finalMemory >= 0);
    }

    @Test
    @DisplayName("Error handling - invalid SPARQL query")
    void testInvalidSparqlQuery() throws Exception {
        assertThrows(QLeverFfiException.class, () -> {
            engine.executeQuery("INVALID SPARQL QUERY");
        });
    }

    @Test
    @DisplayName("Error handling - unsupported RDF format")
    void testUnsupportedRdfFormat() throws Exception {
        assertThrows(QLeverFfiException.class, () -> {
            engine.loadRdfData(TEST_RDF, "INVALID_FORMAT");
        });
    }

    @Test
    @DisplayName("Configuration - set memory limit")
    void testSetMemoryLimit() throws Exception {
        long memoryLimit = 1024 * 1024 * 100; // 100 MB
        engine.setMemoryLimit(memoryLimit);
        assertEquals(memoryLimit, engine.getMemoryLimit());
    }

    @Test
    @DisplayName("Configuration - set query timeout")
    void testSetQueryTimeout() throws Exception {
        long timeout = 5000; // 5 seconds
        engine.setQueryTimeout(timeout);
        assertEquals(timeout, engine.getQueryTimeout());
    }

    @Test
    @DisplayName("Integration with YAWL workflow engine")
    void testYawlIntegration() throws Exception {
        // Test that QLever engine can be used within YAWL context
        String yawlCaseId = "case-12345";

        // Set workflow context
        engine.setWorkflowContext(yawlCaseId);

        // Execute a workflow-specific query
        String workflowQuery = String.format(
            "SELECT * FROM yawl:workitem WHERE caseId = '%s'", yawlCaseId);

        engine.loadRdfData(TEST_RDF, "TURTLE");
        QLeverResult result = engine.executeQuery(workflowQuery);

        assertEquals(QLeverStatus.READY, result.status());
        assertTrue(result.isSuccess());
    }
}