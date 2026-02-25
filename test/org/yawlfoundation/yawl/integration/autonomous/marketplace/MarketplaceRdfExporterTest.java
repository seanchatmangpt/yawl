package org.yawlfoundation.yawl.integration.autonomous.marketplace;

import junit.framework.TestCase;
import org.junit.jupiter.api.Tag;

import java.time.Instant;
import java.util.List;

/**
 * Chicago TDD tests for {@link MarketplaceRdfExporter}.
 *
 * <p>No mocks. All assertions target real Turtle output structure.
 * Tests verify all five marketplace dimensions are present in the export.</p>
 *
 * @since YAWL 6.0
 */
@Tag("unit")
public class MarketplaceRdfExporterTest extends TestCase {

    private final MarketplaceRdfExporter exporter = new MarketplaceRdfExporter();

    // -------------------------------------------------------------------------
    // Structural tests
    // -------------------------------------------------------------------------

    public void testEmptyMarketplaceProducesValidTurtle() {
        AgentMarketplace empty = new AgentMarketplace();
        String turtle = exporter.exportToTurtle(empty);

        assertNotNull(turtle);
        assertTrue("Turtle must declare mkt prefix",
                turtle.contains("@prefix mkt:"));
        assertTrue("Turtle must declare xsd prefix",
                turtle.contains("@prefix xsd:"));
    }

    public void testEmptyListProducesOnlyPrefixes() {
        String turtle = exporter.exportToTurtle(List.of());
        assertTrue(turtle.contains("@prefix mkt:"));
        assertFalse("No listings means no AgentListing subjects",
                turtle.contains("mkt:AgentListing"));
    }

    public void testSingleListingProducesAgentListingType() {
        AgentMarketplaceListing listing = TestFixtures.buildLiveListing("agent-rdf-1");
        String turtle = exporter.exportToTurtle(List.of(listing));

        assertTrue("Turtle must declare listing as mkt:AgentListing",
                turtle.contains("a mkt:AgentListing"));
    }

    public void testListingSubjectUsesAgentId() {
        AgentMarketplaceListing listing = TestFixtures.buildLiveListing("my-unique-agent");
        String turtle = exporter.exportToTurtle(List.of(listing));

        assertTrue("Listing subject IRI must contain agent ID",
                turtle.contains("my-unique-agent"));
    }

    // -------------------------------------------------------------------------
    // Dimension 1: Capability
    // -------------------------------------------------------------------------

    public void testCapabilityDomainNameIsExported() {
        AgentMarketplaceListing listing = TestFixtures.buildLiveListing("cap-test");
        String turtle = exporter.exportToTurtle(List.of(listing));

        assertTrue("Turtle must contain mkt:domainName",
                turtle.contains("mkt:domainName"));
        assertTrue("Turtle must contain the domain value",
                turtle.contains("cap-test-domain"));
    }

    // -------------------------------------------------------------------------
    // Dimension 2: Ontological coverage
    // -------------------------------------------------------------------------

    public void testDeclaredNamespaceIsExported() {
        AgentMarketplaceListing listing = TestFixtures.buildLiveListing("onto-test");
        String turtle = exporter.exportToTurtle(List.of(listing));

        assertTrue("Turtle must contain mkt:declaredNamespace",
                turtle.contains("mkt:declaredNamespace"));
        assertTrue("Turtle must contain the YAWL schema namespace",
                turtle.contains("yawlfoundation.org/yawlschema"));
    }

    public void testSparqlPatternIsExported() {
        AgentMarketplaceListing listing = TestFixtures.buildLiveListing("onto-sparql");
        String turtle = exporter.exportToTurtle(List.of(listing));

        assertTrue("Turtle must contain mkt:sparqlPattern",
                turtle.contains("mkt:sparqlPattern"));
        assertTrue("Turtle must contain the SPARQL pattern",
                turtle.contains("hasDecomposition"));
    }

    // -------------------------------------------------------------------------
    // Dimension 3: Workflow transition contracts
    // -------------------------------------------------------------------------

    public void testWcpPatternIsExported() {
        AgentMarketplaceListing listing = TestFixtures.buildLiveListing("wcp-test");
        String turtle = exporter.exportToTurtle(List.of(listing));

        assertTrue("Turtle must contain mkt:wcpPattern",
                turtle.contains("mkt:wcpPattern"));
        assertTrue("Turtle must contain WCP-1",
                turtle.contains("WCP-1"));
    }

    public void testInputOutputTokenTypesAreExported() {
        AgentMarketplaceListing listing = TestFixtures.buildLiveListing("token-test");
        String turtle = exporter.exportToTurtle(List.of(listing));

        assertTrue("Turtle must contain mkt:inputTokenType",
                turtle.contains("mkt:inputTokenType"));
        assertTrue("Turtle must contain mkt:outputTokenType",
                turtle.contains("mkt:outputTokenType"));
    }

    // -------------------------------------------------------------------------
    // Dimension 4: Economic cost
    // -------------------------------------------------------------------------

    public void testBasePricePerCycleIsExported() {
        AgentMarketplaceListing listing = TestFixtures.buildLiveListing("cost-test");
        String turtle = exporter.exportToTurtle(List.of(listing));

        assertTrue("Turtle must contain mkt:basePricePerCycle",
                turtle.contains("mkt:basePricePerCycle"));
        assertTrue("Turtle must contain xsd:decimal for price",
                turtle.contains("xsd:decimal"));
    }

    public void testLlmInferenceRatioIsExported() {
        AgentMarketplaceListing listing = TestFixtures.buildLiveListing("ratio-test");
        String turtle = exporter.exportToTurtle(List.of(listing));

        assertTrue("Turtle must contain mkt:llmInferenceRatio",
                turtle.contains("mkt:llmInferenceRatio"));
    }

    // -------------------------------------------------------------------------
    // Dimension 5: Latency
    // -------------------------------------------------------------------------

    public void testLatencyProfileIsExported() {
        AgentMarketplaceListing listing = TestFixtures.buildLiveListing("latency-test");
        String turtle = exporter.exportToTurtle(List.of(listing));

        assertTrue("Turtle must contain mkt:p99LatencyMs",
                turtle.contains("mkt:p99LatencyMs"));
        assertTrue("Turtle must contain mkt:graphSizeTriples",
                turtle.contains("mkt:graphSizeTriples"));
        assertTrue("Latency values use xsd:long",
                turtle.contains("xsd:long"));
    }

    public void testQLeverClassLatencyValues() {
        // qleverClass: p50=5ms, p95=50ms, p99=95ms at 1B triples
        AgentMarketplaceListing listing = TestFixtures.buildLiveListing("qlever-lat");
        String turtle = exporter.exportToTurtle(List.of(listing));

        assertTrue("p99 must be 95ms", turtle.contains("\"95\"^^xsd:long"));
        assertTrue("graphSize must be 1B", turtle.contains("\"1000000000\"^^xsd:long"));
    }

    // -------------------------------------------------------------------------
    // Multiple listings
    // -------------------------------------------------------------------------

    public void testMultipleListingsAreAllExported() {
        AgentMarketplace marketplace = TestFixtures.buildTestMarketplace(3);
        String turtle = exporter.exportToTurtle(marketplace);

        assertTrue(turtle.contains("agent-0"));
        assertTrue(turtle.contains("agent-1"));
        assertTrue(turtle.contains("agent-2"));
    }

    // -------------------------------------------------------------------------
    // Literal escaping
    // -------------------------------------------------------------------------

    public void testLiteralEscapesDoubleQuote() {
        // literal("\"") produces "\"" (4 chars: outer-quote backslash inner-quote outer-quote)
        // Removing all " leaves only the backslash: \
        assertEquals("\\", MarketplaceRdfExporter.literal("\"").replace("\"", ""));
    }

    public void testLiteralEscapesBackslash() {
        String result = MarketplaceRdfExporter.literal("a\\b");
        assertTrue("Backslash must be escaped", result.contains("\\\\"));
    }

    public void testLiteralEscapesNewline() {
        String result = MarketplaceRdfExporter.literal("line1\nline2");
        assertTrue("Newline must be escaped to \\n", result.contains("\\n"));
        assertFalse("Raw newline must not appear in literal", result.contains("\n"));
    }

    public void testLiteralNullThrows() {
        try {
            MarketplaceRdfExporter.literal(null);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            // expected
        }
    }

    public void testEncodeNullThrows() {
        try {
            MarketplaceRdfExporter.encode(null);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            // expected
        }
    }

    // -------------------------------------------------------------------------
    // Liveness: stale listings are excluded when exporting from marketplace
    // -------------------------------------------------------------------------

    public void testStaleListingIsExcludedFromMarketplaceExport() {
        AgentMarketplace marketplace = new AgentMarketplace();
        marketplace.publish(TestFixtures.buildStaleListing("stale-agent"));

        String turtle = exporter.exportToTurtle(marketplace);
        assertFalse("Stale agent must not appear in export",
                turtle.contains("stale-agent"));
    }

    public void testLiveListingIsIncludedInMarketplaceExport() {
        AgentMarketplace marketplace = new AgentMarketplace();
        marketplace.publish(TestFixtures.buildLiveListing("live-agent"));

        String turtle = exporter.exportToTurtle(marketplace);
        assertTrue("Live agent must appear in export",
                turtle.contains("live-agent"));
    }
}
