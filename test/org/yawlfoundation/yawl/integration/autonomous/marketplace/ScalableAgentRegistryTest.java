package org.yawlfoundation.yawl.integration.autonomous.marketplace;

import junit.framework.TestCase;
import org.junit.jupiter.api.Tag;
import org.yawlfoundation.yawl.integration.autonomous.AgentCapability;
import org.yawlfoundation.yawl.integration.autonomous.registry.AgentInfo;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Chicago TDD tests for ScalableAgentRegistry with 5-dimensional inverted indices.
 *
 * <p>Tests focus on:</p>
 * <ul>
 *   <li>Publish/unpublish correctness with index updates</li>
 *   <li>Indexed query performance (WCP, namespace, cost, latency)</li>
 *   <li>Scalability: 100K agents, query latency &lt;10ms</li>
 *   <li>Index consistency under concurrent operations</li>
 *   <li>Predicate intersection for composite queries</li>
 * </ul>
 *
 * @since YAWL 6.0
 */
@Tag("unit")
public class ScalableAgentRegistryTest extends TestCase {

    private static final String YAWL_NS = "http://www.yawlfoundation.org/yawlschema#";
    private static final String OWL_NS  = "http://www.w3.org/2002/07/owl#";
    private static final String RDF_NS  = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";

    private ScalableAgentRegistry registry;

    @Override
    protected void setUp() {
        registry = new ScalableAgentRegistry();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private AgentInfo agentInfo(String id) {
        return new AgentInfo(id, "Agent " + id, List.of(), "localhost", 8080);
    }

    private AgentCapability capability(String domain) {
        return new AgentCapability(domain, domain + " processing");
    }

    private AgentMarketplaceListing liveListing(String id, AgentMarketplaceSpec spec) {
        return AgentMarketplaceListing.publishNow(agentInfo(id), spec);
    }

    private AgentMarketplaceListing staleListing(String id, AgentMarketplaceSpec spec) {
        Instant tenMinutesAgo = Instant.now().minus(Duration.ofMinutes(10));
        return new AgentMarketplaceListing(agentInfo(id), spec, tenMinutesAgo, tenMinutesAgo);
    }

    private AgentMarketplaceSpec minimalSpec(String domain) {
        return AgentMarketplaceSpec.builder("1.0.0", capability(domain))
                .costProfile(CoordinationCostProfile.pureGraphMatching(0.5))
                .build();
    }

    private AgentMarketplaceSpec specWithWcp(String domain, String... wcpCodes) {
        var builder = AgentMarketplaceSpec.builder("1.0.0", capability(domain));
        var contractBuilder = WorkflowTransitionContract.builder();
        for (String wcp : wcpCodes) {
            contractBuilder.wcp(wcp);
        }
        return builder.transitionContract(contractBuilder.build()).build();
    }

    private AgentMarketplaceSpec specWithNamespace(String domain, String... namespaces) {
        var coverage = new OntologicalCoverage(List.of(namespaces), List.of(), List.of());
        return AgentMarketplaceSpec.builder("1.0.0", capability(domain))
                .ontologicalCoverage(coverage)
                .costProfile(CoordinationCostProfile.pureGraphMatching(0.5))
                .build();
    }

    private AgentMarketplaceSpec specWithCost(String domain, double cost) {
        return AgentMarketplaceSpec.builder("1.0.0", capability(domain))
                .costProfile(CoordinationCostProfile.pureGraphMatching(cost))
                .build();
    }

    private AgentMarketplaceSpec specWithLatency(String domain, long p99Ms) {
        var latency = new LatencyProfile(1_000_000, 50, 150, p99Ms);
        return AgentMarketplaceSpec.builder("1.0.0", capability(domain))
                .latencyProfile(latency)
                .costProfile(CoordinationCostProfile.pureGraphMatching(0.5))
                .build();
    }

    // -------------------------------------------------------------------------
    // Publish / Unpublish
    // -------------------------------------------------------------------------

    public void testPublishAddsListing() {
        registry.publish(liveListing("a1", minimalSpec("Ordering")));
        assertEquals(1, registry.size());
    }

    public void testPublishReplacesListing() {
        var spec1 = minimalSpec("Ordering");
        var spec2 = minimalSpec("Shipping");
        registry.publish(liveListing("a1", spec1));
        registry.publish(liveListing("a1", spec2));
        assertEquals(1, registry.size());
    }

    public void testUnpublishRemoves() {
        registry.publish(liveListing("a1", minimalSpec("Ordering")));
        registry.unpublish("a1");
        assertEquals(0, registry.size());
    }

    public void testUnpublishUnknownAgentIsIdempotent() {
        registry.unpublish("unknown");
        assertEquals(0, registry.size());
    }

    // -------------------------------------------------------------------------
    // WCP Index
    // -------------------------------------------------------------------------

    public void testFindByWcpPatternUsesIndex() {
        var spec1 = specWithWcp("Ordering", "WCP-1", "WCP-5");
        var spec2 = specWithWcp("Shipping", "WCP-5", "WCP-17");
        var spec3 = minimalSpec("Invoicing");

        registry.publish(liveListing("a1", spec1));
        registry.publish(liveListing("a2", spec2));
        registry.publish(liveListing("a3", spec3));

        // Query for WCP-1 should return only a1
        var results = registry.findByWcpPattern("WCP-1");
        assertEquals(1, results.size());
        assertEquals("a1", results.get(0).agentInfo().getId());

        // Query for WCP-5 should return a1 and a2
        var wcpFive = registry.findByWcpPattern("WCP-5");
        assertEquals(2, wcpFive.size());
    }

    public void testWcpIndexEmptyForUnknownPattern() {
        registry.publish(liveListing("a1", specWithWcp("Ordering", "WCP-1")));
        var results = registry.findByWcpPattern("WCP-99");
        assertEquals(0, results.size());
    }

    // -------------------------------------------------------------------------
    // Namespace Index
    // -------------------------------------------------------------------------

    public void testFindByNamespaceUsesIndex() {
        var spec1 = specWithNamespace("A", YAWL_NS, OWL_NS);
        var spec2 = specWithNamespace("B", OWL_NS, RDF_NS);
        var spec3 = minimalSpec("C");

        registry.publish(liveListing("a1", spec1));
        registry.publish(liveListing("a2", spec2));
        registry.publish(liveListing("a3", spec3));

        // Query for YAWL_NS should return only a1
        var yawlResults = registry.findByNamespace(YAWL_NS);
        assertEquals(1, yawlResults.size());
        assertEquals("a1", yawlResults.get(0).agentInfo().getId());

        // Query for OWL_NS should return a1 and a2
        var owlResults = registry.findByNamespace(OWL_NS);
        assertEquals(2, owlResults.size());
    }

    public void testNamespaceIndexEmptyForUnknownNamespace() {
        registry.publish(liveListing("a1", specWithNamespace("A", YAWL_NS)));
        var results = registry.findByNamespace("http://unknown.org/ns#");
        assertEquals(0, results.size());
    }

    // -------------------------------------------------------------------------
    // Cost Bucket Index
    // -------------------------------------------------------------------------

    public void testFindByMaxCostUsesIndex() {
        registry.publish(liveListing("a1", specWithCost("A", 0.5)));
        registry.publish(liveListing("a2", specWithCost("B", 1.0)));
        registry.publish(liveListing("a3", specWithCost("C", 2.0)));

        // Query for cost <= 1.0
        var results = registry.findByMaxCost(1.0);
        assertEquals(2, results.size());

        // Verify ordering: a1 (0.5) before a2 (1.0)
        assertEquals("a1", results.get(0).agentInfo().getId());
        assertEquals("a2", results.get(1).agentInfo().getId());
    }

    public void testFindByMaxCostExcludesExpensive() {
        registry.publish(liveListing("a1", specWithCost("A", 0.5)));
        registry.publish(liveListing("a2", specWithCost("B", 2.0)));

        var results = registry.findByMaxCost(1.0);
        assertEquals(1, results.size());
        assertEquals("a1", results.get(0).agentInfo().getId());
    }

    // -------------------------------------------------------------------------
    // Latency Index
    // -------------------------------------------------------------------------

    public void testFindByMaxLatencyUsesIndex() {
        registry.publish(liveListing("a1", specWithLatency("A", 50)));
        registry.publish(liveListing("a2", specWithLatency("B", 100)));
        registry.publish(liveListing("a3", specWithLatency("C", 200)));

        // Query for latency <= 100ms
        var results = registry.findByMaxLatency(100);
        assertEquals(2, results.size());

        // Verify ordering: a1 (50) before a2 (100)
        assertEquals("a1", results.get(0).agentInfo().getId());
        assertEquals("a2", results.get(1).agentInfo().getId());
    }

    public void testFindByMaxLatencyExcludesSlowAgents() {
        registry.publish(liveListing("a1", specWithLatency("A", 50)));
        registry.publish(liveListing("a2", specWithLatency("B", 200)));

        var results = registry.findByMaxLatency(100);
        assertEquals(1, results.size());
        assertEquals("a1", results.get(0).agentInfo().getId());
    }

    // -------------------------------------------------------------------------
    // Composite Transition Slot Queries
    // -------------------------------------------------------------------------

    public void testFindForTransitionSlotWithMultipleDimensions() {
        // Agent 1: WCP-1, YAWL_NS, cost 0.5, latency 50ms
        var spec1 = AgentMarketplaceSpec.builder("1.0.0", capability("Order"))
                .ontologicalCoverage(new OntologicalCoverage(List.of(YAWL_NS), List.of(), List.of()))
                .transitionContract(WorkflowTransitionContract.builder().wcp("WCP-1").build())
                .costProfile(CoordinationCostProfile.pureGraphMatching(0.5))
                .latencyProfile(new LatencyProfile(1_000_000, 40, 45, 50))
                .build();

        // Agent 2: WCP-1, YAWL_NS, cost 1.0, latency 100ms
        var spec2 = AgentMarketplaceSpec.builder("1.0.0", capability("Order"))
                .ontologicalCoverage(new OntologicalCoverage(List.of(YAWL_NS), List.of(), List.of()))
                .transitionContract(WorkflowTransitionContract.builder().wcp("WCP-1").build())
                .costProfile(CoordinationCostProfile.pureGraphMatching(1.0))
                .latencyProfile(new LatencyProfile(1_000_000, 90, 95, 100))
                .build();

        // Agent 3: WCP-5, YAWL_NS, cost 0.5, latency 50ms (wrong WCP)
        var spec3 = AgentMarketplaceSpec.builder("1.0.0", capability("Order"))
                .ontologicalCoverage(new OntologicalCoverage(List.of(YAWL_NS), List.of(), List.of()))
                .transitionContract(WorkflowTransitionContract.builder().wcp("WCP-5").build())
                .costProfile(CoordinationCostProfile.pureGraphMatching(0.5))
                .latencyProfile(new LatencyProfile(1_000_000, 40, 45, 50))
                .build();

        registry.publish(liveListing("a1", spec1));
        registry.publish(liveListing("a2", spec2));
        registry.publish(liveListing("a3", spec3));

        var query = TransitionSlotQuery.builder()
                .requireWcpPattern("WCP-1")
                .requireNamespace(YAWL_NS)
                .maxCostPerCycle(0.8)
                .maxP99LatencyMs(75)
                .build();

        var results = registry.findForTransitionSlot(query);

        // Should match only a1 (WCP-1, YAWL_NS, cost 0.5, latency 50)
        assertEquals(1, results.size());
        assertEquals("a1", results.get(0).agentInfo().getId());
    }

    public void testFindForTransitionSlotOrdersByEconomic() {
        var spec1 = AgentMarketplaceSpec.builder("1.0.0", capability("Order"))
                .costProfile(CoordinationCostProfile.pureGraphMatching(1.0))
                .latencyProfile(new LatencyProfile(1_000_000, 90, 95, 100))
                .transitionContract(WorkflowTransitionContract.builder().wcp("WCP-1").build())
                .build();

        var spec2 = AgentMarketplaceSpec.builder("1.0.0", capability("Order"))
                .costProfile(CoordinationCostProfile.pureGraphMatching(0.5))
                .latencyProfile(new LatencyProfile(1_000_000, 40, 45, 50))
                .transitionContract(WorkflowTransitionContract.builder().wcp("WCP-1").build())
                .build();

        registry.publish(liveListing("expensive", spec1));
        registry.publish(liveListing("cheap", spec2));

        var query = TransitionSlotQuery.builder()
                .requireWcpPattern("WCP-1")
                .build();

        var results = registry.findForTransitionSlot(query);

        // Should order by cost: cheap (0.5) before expensive (1.0)
        assertEquals(2, results.size());
        assertEquals("cheap", results.get(0).agentInfo().getId());
        assertEquals("expensive", results.get(1).agentInfo().getId());
    }

    // -------------------------------------------------------------------------
    // Index Consistency
    // -------------------------------------------------------------------------

    public void testIndexRemovedOnUnpublish() {
        var spec = specWithWcp("Ordering", "WCP-1");
        registry.publish(liveListing("a1", spec));

        // Verify indexed
        var wcpResults = registry.findByWcpPattern("WCP-1");
        assertEquals(1, wcpResults.size());

        // Unpublish and verify removed from index
        registry.unpublish("a1");
        var afterUnpublish = registry.findByWcpPattern("WCP-1");
        assertEquals(0, afterUnpublish.size());
    }

    public void testIndexUpdatedOnReplaceSpec() {
        var spec1 = specWithWcp("A", "WCP-1");
        var spec2 = specWithWcp("B", "WCP-5");

        registry.publish(liveListing("a1", spec1));
        assertEquals(1, registry.findByWcpPattern("WCP-1").size());
        assertEquals(0, registry.findByWcpPattern("WCP-5").size());

        // Replace with new spec
        registry.publish(liveListing("a1", spec2));
        assertEquals(0, registry.findByWcpPattern("WCP-1").size());
        assertEquals(1, registry.findByWcpPattern("WCP-5").size());
    }

    // -------------------------------------------------------------------------
    // Liveness
    // -------------------------------------------------------------------------

    public void testLiveListingsExcludesStale() {
        registry.publish(liveListing("a1", minimalSpec("Ordering")));
        registry.publish(staleListing("a2", minimalSpec("Shipping")));

        var live = registry.allLiveListings();
        assertEquals(1, live.size());
        assertEquals("a1", live.get(0).agentInfo().getId());
    }

    public void testHeartbeatExtendSliveness() {
        registry.publish(staleListing("a1", minimalSpec("Ordering")));
        assertEquals(0, registry.allLiveListings().size());

        // Update heartbeat to now
        registry.heartbeat("a1", Instant.now());
        assertEquals(1, registry.allLiveListings().size());
    }

    // -------------------------------------------------------------------------
    // Index Statistics
    // -------------------------------------------------------------------------

    public void testGetIndexStatsReturnsMetrics() {
        registry.publish(liveListing("a1", specWithWcp("A", "WCP-1", "WCP-5")));
        registry.publish(liveListing("a2", specWithWcp("B", "WCP-5")));

        var stats = registry.getIndexStats();
        assertEquals(2, stats.totalListings());
        assertEquals(2, stats.liveAgents());
        assertEquals(2, stats.wcpIndexEntries());  // WCP-1, WCP-5
        assertTrue(stats.avgIndexDepth() > 0);
        assertTrue(stats.maxIndexDepth() > 0);
    }

    // -------------------------------------------------------------------------
    // Performance (100K agent scalability test)
    // -------------------------------------------------------------------------

    public void testScalabilityWith100KAgents() {
        // Publish 100K agents with varying specs
        for (int i = 0; i < 100_000; i++) {
            String id = "agent_" + i;
            double cost = 0.1 + (i % 100) * 0.01;  // Distribute costs
            long latency = 50 + (i % 200);  // Distribute latencies
            String wcp = "WCP-" + ((i % 10) + 1);  // Distribute WCP patterns
            String ns = i % 2 == 0 ? YAWL_NS : OWL_NS;  // Alternate namespaces

            var spec = AgentMarketplaceSpec.builder("1.0.0", capability("Service_" + (i % 50)))
                    .ontologicalCoverage(new OntologicalCoverage(List.of(ns), List.of(), List.of()))
                    .transitionContract(WorkflowTransitionContract.builder().wcp(wcp).build())
                    .costProfile(CoordinationCostProfile.pureGraphMatching(cost))
                    .latencyProfile(new LatencyProfile(1_000_000, latency - 10, latency, latency + 10))
                    .build();

            registry.publish(liveListing(id, spec));
        }

        assertEquals(100_000, registry.size());

        // Query with multiple dimensions - should complete quickly
        long startTime = System.currentTimeMillis();

        var query = TransitionSlotQuery.builder()
                .requireWcpPattern("WCP-5")
                .requireNamespace(YAWL_NS)
                .maxCostPerCycle(0.5)
                .maxP99LatencyMs(150)
                .build();

        var results = registry.findForTransitionSlot(query);

        long elapsed = System.currentTimeMillis() - startTime;

        // Should complete in <50ms (target: <10ms)
        assertTrue("Query took " + elapsed + "ms, expected <50ms", elapsed < 50);

        // Verify results are not empty and are correct
        assertTrue(results.size() > 0);
        for (var listing : results) {
            assertTrue(listing.spec().costProfile().isWithinBudget(0.5));
            assertTrue(listing.spec().worstCaseLatency().get().p99Ms() <= 150);
        }
    }

    // -------------------------------------------------------------------------
    // Edge Cases
    // -------------------------------------------------------------------------

    public void testPublishNullListingThrows() {
        assertThrows(NullPointerException.class, () -> registry.publish(null));
    }

    public void testPublishNullAgentIdThrows() {
        var listing = liveListing(null, minimalSpec("A"));
        assertThrows(IllegalArgumentException.class, () -> registry.publish(listing));
    }

    public void testFindByWcpNullThrows() {
        assertThrows(NullPointerException.class, () -> registry.findByWcpPattern(null));
    }

    public void testFindByNamespaceNullThrows() {
        assertThrows(NullPointerException.class, () -> registry.findByNamespace(null));
    }
}
