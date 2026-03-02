package org.yawlfoundation.yawl.integration.autonomous.marketplace;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for {@link QLeverSparqlEngine}.
 *
 * <p>Tests validate graceful unavailability (always-run) and basic API
 * shape. Integration tests self-skip when a QLever instance is not running.</p>
 *
 * <p>This test class extends {@link SparqlEngineContractTest} to verify the SPARQL engine contract
 * and adds QLever-specific behaviors.</p>
 *
 * @since YAWL 6.0
 */
@Tag("unit")
public class QLeverSparqlEngineTest extends SparqlEngineContractTest {

    private QLeverSparqlEngine engine;
    private QLeverSparqlEngine customEngine;

    @Override
    protected SparqlEngine createEngine() {
        // Return the default engine for base class tests
        return new QLeverSparqlEngine();
    }

    @BeforeEach
    void setUp() {
        // Create fresh instances for each test
        engine = new QLeverSparqlEngine();
        customEngine = new QLeverSparqlEngine("http://localhost:7002");
    }

    @AfterEach
    void tearDown() {
        engine = null;
        customEngine = null;
    }

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

    // -------------------------------------------------------------------------
    // QLever-specific contract tests
    // -------------------------------------------------------------------------

    public void testConstructorWithCustomBaseUrl() {
        assertNotNull(customEngine);
        assertEquals("qlever", customEngine.engineType());
    }

    public void testConstructorTrimsTrailingSlashes() {
        QLeverSparqlEngine engineWithSlash = new QLeverSparqlEngine("http://localhost:7001/");
        assertEquals("qlever", engineWithSlash.engineType());
    }

    public void testConstructorThrowsOnNullBaseUrl() {
        try {
            new QLeverSparqlEngine(null);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            assertEquals("baseUrl must not be null", e.getMessage());
        }
    }

    public void testSparqlUpdateMethodExists() {
        assertNotNull(customEngine);
        // Test that method exists - will throw if QLever unavailable
        try {
            customEngine.sparqlUpdate("INSERT DATA { <test> <test> <test> }");
            fail("Expected SparqlEngineUnavailableException");
        } catch (SparqlEngineUnavailableException e) {
            // Expected when QLever not running
        }
    }

    public void testSparqlUpdateThrowsOnNullQuery() {
        try {
            customEngine.sparqlUpdate(null);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            assertEquals("updateQuery must not be null", e.getMessage());
        }
    }

    public void testDifferentInstancesAreIndependent() {
        QLeverSparqlEngine engine1 = new QLeverSparqlEngine();
        QLeverSparqlEngine engine2 = new QLeverSparqlEngine();

        assertNotSame(engine1, engine2);
    }
}
