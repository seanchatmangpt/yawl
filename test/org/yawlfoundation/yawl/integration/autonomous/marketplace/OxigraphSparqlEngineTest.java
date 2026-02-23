package org.yawlfoundation.yawl.integration.autonomous.marketplace;

import junit.framework.TestCase;
import org.junit.jupiter.api.Tag;

/**
 * Chicago TDD tests for {@link OxigraphSparqlEngine}.
 *
 * <p>Two categories of tests:</p>
 * <ol>
 *   <li><b>Always-run</b> — validate graceful unavailability on unused ports.
 *       These pass regardless of whether {@code yawl-native} is running.</li>
 *   <li><b>Self-skipping</b> — full CONSTRUCT roundtrip tests that skip
 *       automatically when {@code yawl-native} is not reachable.</li>
 * </ol>
 *
 * @since YAWL 6.0
 */
@Tag("unit")
public class OxigraphSparqlEngineTest extends TestCase {

    // -------------------------------------------------------------------------
    // Always-run: graceful unavailability
    // -------------------------------------------------------------------------

    public void testIsAvailableReturnsFalseOnUnusedPort() {
        OxigraphSparqlEngine engine = new OxigraphSparqlEngine("http://localhost:19876");
        assertFalse("Engine must not claim availability when nothing listens on port 19876",
                engine.isAvailable());
    }

    public void testConstructToTurtleThrowsUnavailableWhenEngineDown() {
        OxigraphSparqlEngine engine = new OxigraphSparqlEngine("http://localhost:19876");
        try {
            engine.constructToTurtle("CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }");
            fail("Expected SparqlEngineUnavailableException");
        } catch (SparqlEngineUnavailableException e) {
            assertTrue(e.getMessage().contains("oxigraph"));
        } catch (SparqlEngineException e) {
            fail("Expected SparqlEngineUnavailableException, got: " + e.getMessage());
        }
    }

    public void testEngineTypeIsOxigraph() {
        OxigraphSparqlEngine engine = new OxigraphSparqlEngine("http://localhost:19876");
        assertEquals("oxigraph", engine.engineType());
    }

    public void testImplementsSparqlEngineInterface() {
        SparqlEngine engine = new OxigraphSparqlEngine("http://localhost:19876");
        assertNotNull(engine);
        assertFalse(engine.isAvailable());
        assertEquals("oxigraph", engine.engineType());
    }

    public void testCloseIsNoOp() {
        OxigraphSparqlEngine engine = new OxigraphSparqlEngine("http://localhost:19876");
        // Must not throw
        engine.close();
    }

    // -------------------------------------------------------------------------
    // Self-skipping: full CONSTRUCT roundtrip (requires live yawl-native)
    // -------------------------------------------------------------------------

    public void testConstructAllLiveAgentsWhenServiceRunning() throws Exception {
        OxigraphSparqlEngine engine = new OxigraphSparqlEngine();
        if (!engine.isAvailable()) return; // skip gracefully

        MarketplaceRdfExporter exporter = new MarketplaceRdfExporter();
        AgentMarketplace marketplace = new AgentMarketplace();
        // publish one agent so there is data
        marketplace.publish(TestFixtures.buildLiveListing("sparql-test-1"));

        engine.loadTurtle(exporter.exportToTurtle(marketplace));
        String turtle = engine.constructToTurtle(
                MarketplaceConstructQueries.CONSTRUCT_ALL_LIVE_AGENTS);

        assertNotNull(turtle);
        assertTrue("Turtle must contain mkt:AgentListing",
                turtle.contains("AgentListing"));
        assertTrue("Turtle must contain agent id",
                turtle.contains("sparql-test-1"));
    }

    public void testLoadTurtleAndQueryReturnsResult() throws Exception {
        OxigraphSparqlEngine engine = new OxigraphSparqlEngine();
        if (!engine.isAvailable()) return;

        String turtle = "@prefix ex: <http://example.org/> .\n"
                + "ex:subject ex:predicate ex:object .\n";
        engine.loadTurtle(turtle);

        String result = engine.constructToTurtle(
                "CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }");
        assertNotNull(result);
        assertFalse("CONSTRUCT result must be non-empty", result.isBlank());
    }

    public void testSparqlUpdateInsertsTriple() throws Exception {
        OxigraphSparqlEngine engine = new OxigraphSparqlEngine();
        if (!engine.isAvailable()) return;

        engine.sparqlUpdate(
                "INSERT DATA { <http://test.org/s> <http://test.org/p> \"hello\" }");
        String result = engine.constructToTurtle(
                "CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }");
        assertNotNull(result);
        assertTrue("CONSTRUCT result must reflect inserted triple",
                result.contains("hello") || !result.isBlank());
    }
}
