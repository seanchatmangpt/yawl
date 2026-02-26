package org.yawlfoundation.yawl.integration.autonomous.marketplace;

import junit.framework.TestCase;
import org.junit.jupiter.api.Tag;

/**
 * Chicago TDD tests for {@link QLeverSparqlEngine}.
 *
 * <p>Tests validate graceful unavailability (always-run) and basic API
 * shape. Integration tests self-skip when a QLever instance is not running.</p>
 *
 * @since YAWL 6.0
 */
@Tag("unit")
public class QLeverSparqlEngineTest extends TestCase {

    // -------------------------------------------------------------------------
    // Always-run: graceful unavailability
    // -------------------------------------------------------------------------

    public void testIsAvailableReturnsFalseOnUnusedPort() {
        QLeverSparqlEngine engine = new QLeverSparqlEngine("http://localhost:19877");
        assertFalse("QLever engine must not claim availability on unused port 19877",
                engine.isAvailable());
    }

    public void testConstructToTurtleThrowsUnavailableWhenDown() {
        QLeverSparqlEngine engine = new QLeverSparqlEngine("http://localhost:19877");
        try {
            engine.constructToTurtle("CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }");
            fail("Expected SparqlEngineUnavailableException");
        } catch (SparqlEngineUnavailableException e) {
            assertTrue(e.getMessage().contains("qlever"));
        } catch (SparqlEngineException e) {
            fail("Expected SparqlEngineUnavailableException, got: " + e.getMessage());
        }
    }

    public void testEngineTypeIsQlever() {
        QLeverSparqlEngine engine = new QLeverSparqlEngine("http://localhost:19877");
        assertEquals("qlever", engine.engineType());
    }

    public void testDefaultBaseUrl() {
        QLeverSparqlEngine engine = new QLeverSparqlEngine();
        assertEquals("qlever", engine.engineType());
        assertFalse(engine.isAvailable()); // default port 7001 likely not running in CI
    }

    public void testImplementsSparqlEngineInterface() {
        SparqlEngine engine = new QLeverSparqlEngine("http://localhost:19877");
        assertNotNull(engine);
        assertFalse(engine.isAvailable());
        assertEquals("qlever", engine.engineType());
    }

    public void testCloseIsNoOp() {
        QLeverSparqlEngine engine = new QLeverSparqlEngine("http://localhost:19877");
        engine.close();
    }

    public void testNullQueryThrows() {
        QLeverSparqlEngine engine = new QLeverSparqlEngine("http://localhost:19877");
        try {
            engine.constructToTurtle(null);
            fail("Expected NullPointerException or SparqlEngineException");
        } catch (NullPointerException | SparqlEngineException e) {
            // expected — null query is invalid
        }
    }

    // -------------------------------------------------------------------------
    // Self-skipping: live QLever roundtrip (port 7001)
    // -------------------------------------------------------------------------

    public void testConstructQueryWhenQLeverRunning() throws Exception {
        QLeverSparqlEngine engine = new QLeverSparqlEngine();
        if (!engine.isAvailable()) return; // skip gracefully when QLever not running

        String turtle = engine.constructToTurtle(
                "CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o } LIMIT 1");
        assertNotNull(turtle);
    }
}
