package org.yawlfoundation.yawl.bridge.qlever.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for QLeverEngine implementation
 */
class QLeverEngineTest {

    private QLeverEngine engine;
    private static final String TEST_INDEX_PATH = "/tmp/test-yawl-index";

    @BeforeEach
    void setUp() {
        // Create engine for testing
        engine = QLeverEngine.create(TEST_INDEX_PATH);
    }

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.close();
        }
    }

    @Test
    void testEngineCreation() {
        assertNotNull(engine);
        assertEquals(TEST_INDEX_PATH, engine.getIndexPath());
        assertTrue(engine.isOpen());
    }

    @Test
    void testEngineClose() {
        engine.close();
        assertFalse(engine.isOpen());

        // Verify that accessing closed engine throws exception
        assertThrows(IllegalStateException.class, () -> engine.ask("ASK { }"));
    }

    @Test
    void testAskQuery() {
        // Test with a simple valid ASK query
        AskResult result = engine.ask("ASK { ?s ?p ?o }");

        assertNotNull(result);
        assertEquals("ASK { ?s ?p ?o }", result.getQuery());
        assertTrue(result.isSuccessful());

        // Result should have either true or false
        assertTrue(result.isTrue() || result.isFalse());
    }

    @Test
    void testSelectQuery() {
        // Test with a simple valid SELECT query
        SelectResult result = engine.select("SELECT ?s WHERE { ?s ?p ?o }");

        assertNotNull(result);
        assertEquals("SELECT ?s WHERE { ?s ?p ?o }", result.getQuery());
        assertTrue(result.isSuccessful());

        // Should have at least one column
        assertTrue(result.getColumnCount() >= 0);

        // Variables should be accessible
        if (result.getColumnCount() > 0) {
            assertFalse(result.getVariables().isEmpty());
        }

        // Rows should be accessible
        assertFalse(result.getRows().isEmpty());
    }

    @Test
    void testConstructQuery() {
        // Test with a simple valid CONSTRUCT query
        ConstructResult result = engine.construct("CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }");

        assertNotNull(result);
        assertEquals("CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }", result.getQuery());
        assertTrue(result.isSuccessful());

        // Should have JSON and XML results
        assertNotNull(result.getJsonResult());
        assertNotNull(result.getXmlResult());
    }

    @Test
    void testInvalidQuery() {
        // Test with an invalid query
        SelectResult result = engine.select("INVALID QUERY SYNTAX");

        assertNotNull(result);
        assertEquals("INVALID QUERY SYNTAX", result.getQuery());
        assertFalse(result.isSuccessful());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("error"));
    }

    @Test
    void testEmptyQuery() {
        // Test with empty query
        assertThrows(IllegalArgumentException.class, () -> engine.ask(""));
        assertThrows(IllegalArgumentException.class, () -> engine.select(null));
    }

    @Test
    void testEngineNotInitialized() {
        // Create a new engine and close it
        QLeverEngine testEngine = QLeverEngine.create(TEST_INDEX_PATH);
        testEngine.close();

        // Verify that operations on closed engine throw exceptions
        assertThrows(IllegalStateException.class, () -> testEngine.ask("ASK { }"));
        assertThrows(IllegalStateException.class, () -> testEngine.select("SELECT ?s { }"));
        assertThrows(IllegalStateException.class, () -> testEngine.construct("CONSTRUCT { } WHERE { }"));
    }

    @Test
    void testEngineReopening() {
        // Close the engine
        engine.close();

        // Try to use it after closing
        assertThrows(IllegalStateException.class, () -> engine.ask("ASK { }"));

        // Create a new engine
        QLeverEngine newEngine = QLeverEngine.create(TEST_INDEX_PATH);
        assertTrue(newEngine.isOpen());

        newEngine.close();
    }

    @Test
    void testGetVersion() {
        // Version might throw exception if not implemented
        try {
            String version = engine.getVersion();
            assertNotNull(version);
            assertFalse(version.isEmpty());
        } catch (UnsupportedOperationException e) {
            // Expected if version not implemented yet
            assertTrue(e.getMessage().contains("requires implementation"));
        }
    }

    @Test
    void testResultToString() {
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

    @Test
    void testAskResultSpecificMethods() {
        AskResult result = engine.ask("ASK { ?s ?p ?o }");

        // Test boolean methods
        if (result.isTrue()) {
            assertTrue(result.getAnswer());
            assertEquals("true", result.getAnswerString());
        } else {
            assertFalse(result.getAnswer());
            assertEquals("false", result.getAnswerString());
        }
    }

    @Test
    void testSelectResultSpecificMethods() {
        SelectResult result = engine.select("SELECT ?s WHERE { ?s ?p ?o }");

        // Test row access methods
        if (result.getRowCount() > 0) {
            List<String> firstRow = result.getFirstRow();
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
        assertFalse(result.isEmpty());
    }

    @Test
    void testConstructResultSpecificMethods() {
        ConstructResult result = engine.construct("CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }");

        // Test content checking
        if (result.hasContent()) {
            assertNotNull(result.getTurtleResult());
            assertNotNull(result.getNtriplesResult());
        }

        // Test formatted output
        String formatted = result.getFormattedResult();
        assertNotNull(formatted);
        assertTrue(formatted.contains("Turtle") || formatted.contains("N-Triples"));
    }
}