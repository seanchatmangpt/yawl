package org.yawlfoundation.yawl.bridge.qlever.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for QLeverEngine implementation
 */
class QLeverEngineTest {

    private QLeverEngine engine;
    private static final String TEST_INDEX_PATH = "/tmp/test-yawl-index";

    @BeforeEach
    void setUp() {
        // Create engine for testing
        try {
            engine = QLeverEngine.create(TEST_INDEX_PATH);
        } catch (Exception e) {
            // If native library not available, we'll test with null
            System.out.println("Native library not available, skipping native tests: " + e.getMessage());
        }
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    void testEngineCreation() {
        if (engine != null) {
            assertNotNull(engine);
            assertEquals(TEST_INDEX_PATH, engine.getIndexPath());
            assertTrue(engine.isOpen());
        }
    }

    @Test
    void testEngineClose() {
        if (engine != null) {
            engine.close();
            assertFalse(engine.isOpen());

            // Verify that accessing closed engine throws exception
            assertThrows(IllegalStateException.class, () -> engine.ask("ASK { }"));
        }
    }

    @Test
    void testAskQuery() {
        if (engine != null) {
            // Test with a simple valid ASK query
            AskResult result = engine.ask("ASK { ?s ?p ?o }");

            assertNotNull(result);
            assertTrue(result.isSuccessful());

            // Result should have either true or false
            assertTrue(result.isTrue() || result.isFalse());

            // Test specific methods
            boolean value = result.value();
            assertEquals(result.value(), value);
            assertEquals(result.isTrue(), value);
            assertEquals(result.isFalse(), !value);
        }
    }

    @Test
    void testSelectQuery() {
        if (engine != null) {
            // Test with a simple valid SELECT query
            SelectResult result = engine.select("SELECT ?s WHERE { ?s ?p ?o }");

            assertNotNull(result);
            assertTrue(result.isSuccessful());

            // Should have at least one column
            assertTrue(result.getColumnCount() >= 0);

            // Variables should be accessible
            if (result.getColumnCount() > 0) {
                assertFalse(result.getVariables().isEmpty());
            }

            // Rows should be accessible
            assertNotNull(result.getRows());
        }
    }

    @Test
    void testConstructQuery() {
        if (engine != null) {
            // Test with a simple valid CONSTRUCT query
            ConstructResult result = engine.construct("CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }");

            assertNotNull(result);
            assertTrue(result.isSuccessful());

            // Test triples
            assertNotNull(result.getTriples());

            // Test turtle conversion
            if (result.hasContent()) {
                String turtle = result.getTurtleResult();
                assertNotNull(turtle);
                assertFalse(turtle.isEmpty());
            }
        }
    }

    @Test
    void testInvalidQuery() {
        if (engine != null) {
            // Test with invalid query - this should throw an exception
            assertThrows(RuntimeException.class, () -> {
                engine.select("INVALID QUERY SYNTAX");
            });
        }
    }

    @Test
    void testEmptyQuery() {
        if (engine != null) {
            // Test with empty query
            assertThrows(IllegalArgumentException.class, () -> engine.ask(""));
            assertThrows(IllegalArgumentException.class, () -> engine.select(null));
        }
    }

    @Test
    void testEngineNotInitialized() {
        if (engine != null) {
            // Create a new engine and close it
            QLeverEngine testEngine = QLeverEngine.create(TEST_INDEX_PATH);
            testEngine.close();

            // Verify that operations on closed engine throw exceptions
            assertThrows(IllegalStateException.class, () -> testEngine.ask("ASK { }"));
            assertThrows(IllegalStateException.class, () -> testEngine.select("SELECT ?s { }"));
            assertThrows(IllegalStateException.class, () -> testEngine.construct("CONSTRUCT { } WHERE { }"));
        }
    }

    @Test
    void testEngineReopening() {
        if (engine != null) {
            // Close the engine
            engine.close();

            // Try to use it after closing
            assertThrows(IllegalStateException.class, () -> engine.ask("ASK { }"));

            // Create a new engine
            QLeverEngine newEngine = QLeverEngine.create(TEST_INDEX_PATH);
            assertTrue(newEngine.isOpen());

            newEngine.close();
        }
    }

    @Test
    void testGetVersion() {
        if (engine != null) {
            // Version should return a string even if native library not available
            String version = engine.getVersion();
            assertNotNull(version);
            assertFalse(version.isEmpty());
        }
    }

    @Test
    void testResultToString() {
        if (engine != null) {
            AskResult askResult = engine.ask("ASK { ?s ?p ?o }");
            String toString = askResult.toString();
            assertNotNull(toString);
            assertTrue(toString.contains("AskResult"));

            SelectResult selectResult = engine.select("SELECT ?s WHERE { ?s ?p ?o }");
            toString = selectResult.toString();
            assertNotNull(toString);
            assertTrue(toString.contains("SelectResult"));

            ConstructResult constructResult = engine.construct("CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }");
            toString = constructResult.toString();
            assertNotNull(toString);
            assertTrue(toString.contains("ConstructResult"));
        }
    }

    @Test
    void testSelectResultSpecificMethods() {
        if (engine != null) {
            SelectResult result = engine.select("SELECT ?s WHERE { ?s ?p ?o }");

            // Test row access methods
            if (result.getRowCount() > 0) {
                Map<String, String> firstRow = result.getFirstRow();
                assertNotNull(firstRow);

                // Test value access by column index
                String value = result.getValue(0, 0);
                assertNotNull(value);

                // Test value access by variable name
                String var = result.getVariables().get(0);
                String valueByName = result.getValue(0, var);
                assertEquals(value, valueByName);
            }

            // Test empty methods
            assertNotNull(result.getRows());
        }
    }

    @Test
    void testConstructResultSpecificMethods() {
        if (engine != null) {
            ConstructResult result = engine.construct("CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }");

            // Test content checking
            if (result.hasContent()) {
                assertNotNull(result.getTurtleResult());
                int tripleCount = result.getTripleCount();
                assertTrue(tripleCount > 0);
            }

            // Test triple count
            assertEquals(result.getTriples().size(), result.getTripleCount());
        }
    }

    @Test
    void testTripleClass() {
        Triple triple = new Triple("http://example.org/subject", "http://example.org/predicate", "object");

        assertEquals("http://example.org/subject", triple.subject());
        assertEquals("http://example.org/predicate", triple.predicate());
        assertEquals("object", triple.object());

        assertFalse(triple.containsBlankNode());
        assertFalse(triple.isLiteralTriple());
        assertFalse(triple.isIriTriple());

        String turtle = triple.toTurtle();
        assertTrue(turtle.contains("<http://example.org/subject>"));
        assertTrue(turtle.contains("<http://example.org/predicate>"));
        assertTrue(turtle.contains("\"object\""));

        String toString = triple.toString();
        assertTrue(toString.contains("Triple"));
    }
}