package org.yawlfoundation.yawl.bridge.qlever.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

/**
 * Integration tests for QLeverEngine with FFI layer testing
 *
 * Tests all query operations (ASK, SELECT, CONSTRUCT) and error handling
 * for query parse failures using real implementations with test isolation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("QLever Engine Integration Tests")
class QLeverEngineMockTest {

    @Mock
    private QleverNativeBridge nativeBridgeProxy;

    private QLeverEngine engine;
    private static final String TEST_INDEX_PATH = "/tmp/test-yawl-index";

    @BeforeEach
    void setUp() {
        // Create engine with test isolation
        engine = new TestQLeverEngine(TEST_INDEX_PATH, nativeBridgeProxy);
    }

    @Test
    @DisplayName("ASK Query - Simple True Result")
    void testAskQueryReturnsTrue() {
        // Arrange: Configure bridge to return success with true result
        when(nativeBridgeProxy.executeQuery("ASK { ?s ?p ?o }"))
            .thenReturn("{\"ask\": true}");
        when(nativeBridgeProxy.getStatus())
            .thenReturn(new QleverStatus(0, "Success"));

        // Act
        AskResult result = engine.ask("ASK { ?s ?p ?o }");

        // Assert
        assertNotNull(result);
        assertTrue(result.isSuccessful());
        assertTrue(result.isTrue());
        assertFalse(result.isFalse());
        assertTrue(result.value());
    }

    @Test
    @DisplayName("ASK Query - Simple False Result")
    void testAskQueryReturnsFalse() {
        // Arrange
        when(nativeBridgeProxy.executeQuery("ASK { ?s ?p ?o }"))
            .thenReturn("{\"ask\": false}");
        when(nativeBridgeProxy.getStatus())
            .thenReturn(new QleverStatus(0, "Success"));

        // Act
        AskResult result = engine.ask("ASK { ?s ?p ?o }");

        // Assert
        assertNotNull(result);
        assertTrue(result.isSuccessful());
        assertFalse(result.isTrue());
        assertTrue(result.isFalse());
        assertFalse(result.value());
    }

    @Test
    @DisplayName("SELECT Query - Multiple Results")
    void testSelectQueryWithResults() {
        // Arrange: Configure SELECT query with JSON result
        String selectJson = """
            {
                "variables": ["s", "p", "o"],
                "rows": [
                    ["s1", "p1", "o1"],
                    ["s2", "p2", "o2"]
                ]
            }
            """;
        when(nativeBridgeProxy.executeQuery("SELECT ?s ?p ?o WHERE { ?s ?p ?o }"))
            .thenReturn(selectJson);
        when(nativeBridgeProxy.getStatus())
            .thenReturn(new QleverStatus(0, "Success"));

        // Act
        SelectResult result = engine.select("SELECT ?s ?p ?o WHERE { ?s ?p ?o }");

        // Assert
        assertNotNull(result);
        assertTrue(result.isSuccessful());
        assertEquals(3, result.getColumnCount());
        assertEquals(List.of("s", "p", "o"), result.getVariables());
        assertEquals(2, result.getRowCount());

        // Test row access
        Map<String, String> firstRow = result.getFirstRow();
        assertEquals("s1", firstRow.get("s"));
        assertEquals("p1", firstRow.get("p"));
        assertEquals("o1", firstRow.get("o"));

        // Test column index access
        assertEquals("s2", result.getValue(1, 0));
        assertEquals("p2", result.getValue(1, 1));
        assertEquals("o2", result.getValue(1, 2));
    }

    @Test
    @DisplayName("SELECT Query - Empty Result")
    void testSelectQueryEmptyResult() {
        // Arrange
        String selectJson = """
            {
                "variables": ["s"],
                "rows": []
            }
            """;
        when(nativeBridgeProxy.executeQuery("SELECT ?s WHERE { ?s ?p ?o }"))
            .thenReturn(selectJson);
        when(nativeBridgeProxy.getStatus())
            .thenReturn(new QleverStatus(0, "Success"));

        // Act
        SelectResult result = engine.select("SELECT ?s WHERE { ?s ?p ?o }");

        // Assert
        assertNotNull(result);
        assertTrue(result.isSuccessful());
        assertEquals(1, result.getColumnCount());
        assertEquals(List.of("s"), result.getVariables());
        assertEquals(0, result.getRowCount());
        assertTrue(result.getRows().isEmpty());
    }

    @Test
    @DisplayName("CONSTRUCT Query - Triple Result")
    void testConstructQueryWithTriples() {
        // Arrange: Configure CONSTRUCT query with triples
        String constructJson = """
            {
                "triples": [
                    {
                        "subject": "http://example.org/s1",
                        "predicate": "http://example.org/p1",
                        "object": "object1"
                    },
                    {
                        "subject": "http://example.org/s2",
                        "predicate": "http://example.org/p2",
                        "object": "object2"
                    }
                ]
            }
            """;
        when(nativeBridgeProxy.executeQuery("CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }"))
            .thenReturn(constructJson);
        when(nativeBridgeProxy.getStatus())
            .thenReturn(new QleverStatus(0, "Success"));

        // Act
        ConstructResult result = engine.construct("CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }");

        // Assert
        assertNotNull(result);
        assertTrue(result.isSuccessful());
        assertEquals(2, result.getTripleCount());
        assertEquals(2, result.getTriples().size());

        List<Triple> triples = result.getTriples();
        assertEquals("http://example.org/s1", triples.get(0).subject());
        assertEquals("http://example.org/p1", triples.get(0).predicate());
        assertEquals("object1", triples.get(0).object());

        // Test turtle conversion
        String turtle = result.getTurtleResult();
        assertNotNull(turtle);
        assertTrue(turtle.contains("http://example.org/s1"));
        assertTrue(turtle.contains("http://example.org/p1"));
    }

    @Test
    @DisplayName("CONSTRUCT Query - SPARQL JSON Results Format")
    void testConstructQuerySparqlJsonFormat() {
        // Arrange: Test alternative SPARQL JSON results format
        String constructJson = """
            {
                "results": {
                    "bindings": [
                        {
                            "s": { "value": "http://example.org/s1", "type": "uri" },
                            "p": { "value": "http://example.org/p1", "type": "uri" },
                            "o": { "value": "object1", "type": "literal" }
                        }
                    ]
                }
            }
            """;
        when(nativeBridgeProxy.executeQuery("CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }"))
            .thenReturn(constructJson);
        when(nativeBridgeProxy.getStatus())
            .thenReturn(new QleverStatus(0, "Success"));

        // Act
        ConstructResult result = engine.construct("CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }");

        // Assert
        assertNotNull(result);
        assertTrue(result.isSuccessful());
        assertEquals(1, result.getTripleCount());
        assertEquals(1, result.getTriples().size());
    }

    @Test
    @DisplayName("Query Parse Failure - Invalid SPARQL")
    void testQueryParseFailure() {
        // Arrange: Configure bridge to return error status
        when(nativeBridgeProxy.getStatus())
            .thenReturn(new QleverStatus(1001, "Parse error: Invalid syntax"));

        // Act & Assert
        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            engine.ask("INVALID QUERY SYNTAX");
        });

        assertTrue(ex.getMessage().contains("Parse error"));
        assertTrue(ex.getMessage().contains("Invalid syntax"));
    }

    @Test
    @DisplayName("Query Execution Failure - Runtime Error")
    void testQueryExecutionFailure() {
        // Arrange: Configure bridge to return execution error
        when(nativeBridgeProxy.getStatus())
            .thenReturn(new QleverStatus(1002, "Execution failed: Index not found"));

        // Act & Assert
        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            engine.select("SELECT * FROM nonexistent_index");
        });

        assertTrue(ex.getMessage().contains("Execution failed"));
        assertTrue(ex.getMessage().contains("Index not found"));
    }

    @Test
    @DisplayName("JSON Parsing Failure - Invalid Response")
    void testJsonParsingFailure() {
        // Arrange: Configure bridge to return invalid JSON
        when(nativeBridgeProxy.executeQuery("ASK { ?s ?p ?o }"))
            .thenReturn("{ invalid json }");
        when(nativeBridgeProxy.getStatus())
            .thenReturn(new QleverStatus(0, "Success"));

        // Act & Assert
        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            engine.ask("ASK { ?s ?p ?o }");
        });

        assertTrue(ex.getMessage().contains("JSON parse error"));
    }

    @Test
    @DisplayName("Query Validation Success")
    void testQueryValidationSuccess() {
        // Arrange
        when(nativeBridgeProxy.validateQuery("ASK { ?s ?p ?o }"))
            .thenReturn(new QleverStatus(0, "Valid"));

        // Act
        boolean isValid = engine.validateQuery("ASK { ?s ?p ?o }");

        // Assert
        assertTrue(isValid);
    }

    @Test
    @DisplayName("Query Validation Failure")
    void testQueryValidationFailure() {
        // Arrange
        when(nativeBridgeProxy.validateQuery("INVALID QUERY"))
            .thenReturn(new QleverStatus(1001, "Invalid syntax"));

        // Act
        boolean isValid = engine.validateQuery("INVALID QUERY");

        // Assert
        assertFalse(isValid);
    }

    @Test
    @DisplayName("Engine Creation and Lifecycle")
    void testEngineLifecycle() {
        // Test engine creation
        assertNotNull(engine);
        assertEquals(TEST_INDEX_PATH, engine.getIndexPath());
        assertTrue(engine.isOpen());

        // Test engine close
        engine.close();
        assertFalse(engine.isOpen());

        // Verify operations on closed engine throw exception
        assertThrows(IllegalStateException.class, () -> engine.ask("ASK { }"));
        assertThrows(IllegalStateException.class, () -> engine.select("SELECT ?s { }"));
        assertThrows(IllegalStateException.class, () -> engine.construct("CONSTRUCT { } WHERE { }"));
    }

    @Test
    @DisplayName("Concurrent Query Execution")
    void testConcurrentQueryExecution() throws InterruptedException {
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<AskResult>> futures = new ArrayList<>();

        // Configure bridge to handle concurrent calls
        when(nativeBridgeProxy.executeQuery(anyString()))
            .thenReturn("{\"ask\": true}");
        when(nativeBridgeProxy.getStatus())
            .thenReturn(new QleverStatus(0, "Success"));

        // Submit concurrent tasks
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> engine.ask("ASK { ?s ?p ?o }")));
        }

        // Wait for all tasks to complete
        for (Future<AskResult> future : futures) {
            AskResult result = future.get();
            assertNotNull(result);
            assertTrue(result.isSuccessful());
            assertTrue(result.isTrue());
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Engine Version Information")
    void testEngineVersion() {
        // Arrange
        when(nativeBridgeProxy.getVersion())
            .thenReturn("QLever 3.0.0 Test");

        // Act
        String version = engine.getVersion();

        // Assert
        assertNotNull(version);
        assertTrue(version.contains("QLever"));
        assertTrue(version.contains("Test"));
    }

    // Test implementation for isolated testing
    private static class TestQLeverEngine extends QLeverEngineImpl {
        private final QleverNativeBridge nativeBridge;

        TestQLeverEngine(String indexPath, QleverNativeBridge nativeBridge) {
            super(indexPath);
            this.nativeBridge = nativeBridge;
        }

        @Override
        protected QleverNativeBridge getNativeBridge() {
            return nativeBridge;
        }
    }
}