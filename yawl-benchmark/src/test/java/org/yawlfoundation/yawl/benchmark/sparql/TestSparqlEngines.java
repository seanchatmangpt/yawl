package org.yawlfoundation.yawl.benchmark.sparql;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.yawlfoundation.yawl.qlever.QLeverEmbeddedSparqlEngine;
import org.yawlfoundation.yawl.qlever.QLeverResult;
import org.yawlfoundation.yawl.qlever.QLeverFfiException;

/**
 * Simple test to verify SPARQL engine classes work.
 *
 * IMPORTANT: QLever is an embedded FFI engine (NOT HTTP).
 * All QLever tests use QLeverEmbeddedSparqlEngine directly.
 * The embedded engine uses executeQuery() returning QLeverResult.
 */
class TestSparqlEngines {

    @Test
    void testQLeverEmbeddedCreation() {
        // This should compile even if native library is not available
        assertDoesNotThrow(() -> {
            QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine();
            assertNotNull(engine);
        });
    }

    @Test
    void testQLeverEmbeddedEngineType() {
        QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine();
        // Engine is not initialized until initialize() is called
        assertFalse(engine.isInitialized());
    }

    @Test
    void testQLeverEmbeddedInitializeAndShutdown() {
        QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine();
        try {
            engine.initialize();
            assertTrue(engine.isInitialized());
        } catch (QLeverFfiException e) {
            // Native library not available - this is expected in some environments
            assertTrue(e.getMessage().contains("native") ||
                       e.getMessage().contains("library") ||
                       e.getMessage().contains("not available"));
        } finally {
            try {
                engine.shutdown();
            } catch (Exception e) {
                // Ignore shutdown errors
            }
        }
    }

    @Test
    void testQLeverEmbeddedQueryWithoutInitThrows() {
        QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine();
        assertThrows(QLeverFfiException.class, () -> {
            engine.executeQuery("SELECT ?s WHERE { ?s ?p ?o } LIMIT 1");
        });
    }

    @Test
    void testQLeverEmbeddedTimeout() {
        QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine();
        engine.setQueryTimeout(5000);
        assertEquals(5000, engine.getQueryTimeout());
    }

    @Test
    void testQLeverEmbeddedMemoryLimit() {
        QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine();
        engine.setMemoryLimit(1024 * 1024 * 100); // 100MB
        assertEquals(1024L * 1024 * 100, engine.getMemoryLimit());
    }

    @Test
    void testQLeverEmbeddedWorkflowContext() {
        QLeverEmbeddedSparqlEngine engine = new QLeverEmbeddedSparqlEngine();
        engine.setWorkflowContext("test-case-123");
        assertEquals("test-case-123", engine.getWorkflowContext());
    }
}
