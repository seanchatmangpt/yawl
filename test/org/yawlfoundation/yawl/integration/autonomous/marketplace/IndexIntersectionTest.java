package org.yawlfoundation.yawl.integration.autonomous.marketplace;

import junit.framework.TestCase;
import org.junit.jupiter.api.Tag;
import org.yawlfoundation.yawl.integration.autonomous.AgentCapability;
import org.yawlfoundation.yawl.integration.autonomous.registry.AgentInfo;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Chicago TDD tests for inverted index intersection correctness.
 *
 * <p>Focus: Verifies that predicate intersection across all 5 dimensions
 * produces correct results without false positives or false negatives.</p>
 *
 * <ul>
 *   <li>Set intersection semantics (AND logic)</li>
 *   <li>Empty result handling</li>
 *   <li>Single-dimension vs multi-dimension queries</li>
 *   <li>Partial matches and early termination</li>
 *   <li>Cost and latency range queries</li>
 * </ul>
 *
 * @since YAWL 6.0
 */
@Tag("unit")
public class IndexIntersectionTest extends TestCase {

    private static final String YAWL_NS = "http://www.yawlfoundation.org/yawlschema#";
    private static final String OWL_NS  = "http://www.w3.org/2002/07/owl#";
    private static final String RDF_NS  = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";

    private ScalableAgentRegistry registry;

    @Override
    protected void setUp() {
        registry = new ScalableAgentRegistry();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private AgentInfo agentInfo(String id) {
        return new AgentInfo(id, "Agent " + id, List.of(), "localhost", 8080);
    }

    private AgentCapability capability(String domain) {
        return new AgentCapability(domain, domain + " capability");
    }

    private AgentMarketplaceListing liveListing(String id, AgentMarketplaceSpec spec) {
        return AgentMarketplaceListing.publishNow(agentInfo(id), spec);
    }

    /**
     * Builds a complete agent spec with all 5 dimensional attributes.
     * Useful for testing intersection across all indices.
     */
    private AgentMarketplaceSpec fullSpec(
            String domain,
            String[] wcpPatterns,
            String[] namespaces,
            double cost,
            long p99Ms) {

        var contractBuilder = WorkflowTransitionContract.builder();
        for (String wcp : wcpPatterns) {
            contractBuilder.wcp(wcp);
        }

        var coverage = new OntologicalCoverage(List.of(namespaces), List.of(), List.of());
        var latency = new LatencyProfile(1_000_000, p99Ms - 10, p99Ms - 5, p99Ms);

        return AgentMarketplaceSpec.builder("1.0.0", capability(domain))
                .transitionContract(contractBuilder.build())
                .ontologicalCoverage(coverage)
                .costProfile(CoordinationCostProfile.pureGraphMatching(cost))
                .latencyProfile(latency)
                .build();
    }

    // =========================================================================
    // WCP + Namespace Intersection
    // =========================================================================

    public void testWcpAndNamespaceIntersection() {
        // Agent a1: WCP-1, YAWL_NS
        registry.publish(liveListing("a1",
                fullSpec("A", new String[]{"WCP-1"}, new String[]{YAWL_NS}, 0.5, 50)));

        // Agent a2: WCP-1, OWL_NS
        registry.publish(liveListing("a2",
                fullSpec("B", new String[]{"WCP-1"}, new String[]{OWL_NS}, 0.5, 50)));

        // Agent a3: WCP-5, YAWL_NS
        registry.publish(liveListing("a3",
                fullSpec("C", new String[]{"WCP-5"}, new String[]{YAWL_NS}, 0.5, 50)));

        // Query: WCP-1 AND YAWL_NS - should match only a1
        var query = TransitionSlotQuery.builder()
                .requireWcpPattern("WCP-1")
                .requireNamespace(YAWL_NS)
                .build();

        var results = registry.findForTransitionSlot(query);
        assertEquals(1, results.size());
        assertEquals("a1", results.get(0).agentInfo().getId());
    }

    public void testWcpAndNamespaceNoMatch() {
        // Only a1: WCP-1, YAWL_NS
        registry.publish(liveListing("a1",
                fullSpec("A", new String[]{"WCP-1"}, new String[]{YAWL_NS}, 0.5, 50)));

        // Query: WCP-1 AND OWL_NS - no agent has both
        var query = TransitionSlotQuery.builder()
                .requireWcpPattern("WCP-1")
                .requireNamespace(OWL_NS)
                .build();

        var results = registry.findForTransitionSlot(query);
        assertEquals(0, results.size());
    }

    // =========================================================================
    // WCP + Cost Intersection
    // =========================================================================

    public void testWcpAndCostIntersection() {
        // a1: WCP-1, cost 0.5
        registry.publish(liveListing("a1",
                fullSpec("A", new String[]{"WCP-1"}, new String[]{YAWL_NS}, 0.5, 50)));

        // a2: WCP-1, cost 1.5
        registry.publish(liveListing("a2",
                fullSpec("B", new String[]{"WCP-1"}, new String[]{YAWL_NS}, 1.5, 50)));

        // a3: WCP-5, cost 0.5
        registry.publish(liveListing("a3",
                fullSpec("C", new String[]{"WCP-5"}, new String[]{YAWL_NS}, 0.5, 50)));

        // Query: WCP-1 AND cost <= 1.0 - should match only a1
        var query = TransitionSlotQuery.builder()
                .requireWcpPattern("WCP-1")
                .maxCostPerCycle(1.0)
                .build();

        var results = registry.findForTransitionSlot(query);
        assertEquals(1, results.size());
        assertEquals("a1", results.get(0).agentInfo().getId());
    }

    public void testWcpAndCostNoMatch() {
        // a1: WCP-1, cost 2.0
        registry.publish(liveListing("a1",
                fullSpec("A", new String[]{"WCP-1"}, new String[]{YAWL_NS}, 2.0, 50)));

        // Query: WCP-1 AND cost <= 1.0 - no match
        var query = TransitionSlotQuery.builder()
                .requireWcpPattern("WCP-1")
                .maxCostPerCycle(1.0)
                .build();

        var results = registry.findForTransitionSlot(query);
        assertEquals(0, results.size());
    }

    // =========================================================================
    // Namespace + Cost Intersection
    // =========================================================================

    public void testNamespaceAndCostIntersection() {
        // a1: YAWL_NS, cost 0.5
        registry.publish(liveListing("a1",
                fullSpec("A", new String[]{"WCP-1"}, new String[]{YAWL_NS}, 0.5, 50)));

        // a2: YAWL_NS, cost 1.5
        registry.publish(liveListing("a2",
                fullSpec("B", new String[]{"WCP-1"}, new String[]{YAWL_NS}, 1.5, 50)));

        // a3: OWL_NS, cost 0.5
        registry.publish(liveListing("a3",
                fullSpec("C", new String[]{"WCP-1"}, new String[]{OWL_NS}, 0.5, 50)));

        // Query: YAWL_NS AND cost <= 1.0 - should match only a1
        var query = TransitionSlotQuery.builder()
                .requireNamespace(YAWL_NS)
                .maxCostPerCycle(1.0)
                .build();

        var results = registry.findForTransitionSlot(query);
        assertEquals(1, results.size());
        assertEquals("a1", results.get(0).agentInfo().getId());
    }

    // =========================================================================
    // Namespace + Latency Intersection
    // =========================================================================

    public void testNamespaceAndLatencyIntersection() {
        // a1: YAWL_NS, latency 50ms
        registry.publish(liveListing("a1",
                fullSpec("A", new String[]{"WCP-1"}, new String[]{YAWL_NS}, 0.5, 50)));

        // a2: YAWL_NS, latency 150ms
        registry.publish(liveListing("a2",
                fullSpec("B", new String[]{"WCP-1"}, new String[]{YAWL_NS}, 0.5, 150)));

        // a3: OWL_NS, latency 50ms
        registry.publish(liveListing("a3",
                fullSpec("C", new String[]{"WCP-1"}, new String[]{OWL_NS}, 0.5, 50)));

        // Query: YAWL_NS AND latency <= 100ms - should match only a1
        var query = TransitionSlotQuery.builder()
                .requireNamespace(YAWL_NS)
                .maxP99LatencyMs(100)
                .build();

        var results = registry.findForTransitionSlot(query);
        assertEquals(1, results.size());
        assertEquals("a1", results.get(0).agentInfo().getId());
    }

    // =========================================================================
    // Cost + Latency Intersection
    // =========================================================================

    public void testCostAndLatencyIntersection() {
        // a1: cost 0.5, latency 50ms
        registry.publish(liveListing("a1",
                fullSpec("A", new String[]{"WCP-1"}, new String[]{YAWL_NS}, 0.5, 50)));

        // a2: cost 1.5, latency 50ms
        registry.publish(liveListing("a2",
                fullSpec("B", new String[]{"WCP-1"}, new String[]{YAWL_NS}, 1.5, 50)));

        // a3: cost 0.5, latency 150ms
        registry.publish(liveListing("a3",
                fullSpec("C", new String[]{"WCP-1"}, new String[]{YAWL_NS}, 0.5, 150)));

        // Query: cost <= 1.0 AND latency <= 100ms - should match only a1
        var query = TransitionSlotQuery.builder()
                .maxCostPerCycle(1.0)
                .maxP99LatencyMs(100)
                .build();

        var results = registry.findForTransitionSlot(query);
        assertEquals(1, results.size());
        assertEquals("a1", results.get(0).agentInfo().getId());
    }

    // =========================================================================
    // 3-Dimensional Intersections
    // =========================================================================

    public void testWcpNamespaceCostIntersection() {
        // a1: WCP-1, YAWL_NS, cost 0.5
        registry.publish(liveListing("a1",
                fullSpec("A", new String[]{"WCP-1"}, new String[]{YAWL_NS}, 0.5, 50)));

        // a2: WCP-1, YAWL_NS, cost 1.5
        registry.publish(liveListing("a2",
                fullSpec("B", new String[]{"WCP-1"}, new String[]{YAWL_NS}, 1.5, 50)));

        // a3: WCP-1, OWL_NS, cost 0.5
        registry.publish(liveListing("a3",
                fullSpec("C", new String[]{"WCP-1"}, new String[]{OWL_NS}, 0.5, 50)));

        // a4: WCP-5, YAWL_NS, cost 0.5
        registry.publish(liveListing("a4",
                fullSpec("D", new String[]{"WCP-5"}, new String[]{YAWL_NS}, 0.5, 50)));

        // Query: WCP-1 AND YAWL_NS AND cost <= 1.0 - should match only a1
        var query = TransitionSlotQuery.builder()
                .requireWcpPattern("WCP-1")
                .requireNamespace(YAWL_NS)
                .maxCostPerCycle(1.0)
                .build();

        var results = registry.findForTransitionSlot(query);
        assertEquals(1, results.size());
        assertEquals("a1", results.get(0).agentInfo().getId());
    }

    // =========================================================================
    // Multiple Namespaces Intersection (All Required)
    // =========================================================================

    public void testMultipleNamespacesIntersection() {
        // a1: YAWL_NS and OWL_NS
        registry.publish(liveListing("a1",
                fullSpec("A", new String[]{"WCP-1"}, new String[]{YAWL_NS, OWL_NS}, 0.5, 50)));

        // a2: YAWL_NS and RDF_NS (missing OWL_NS)
        registry.publish(liveListing("a2",
                fullSpec("B", new String[]{"WCP-1"}, new String[]{YAWL_NS, RDF_NS}, 0.5, 50)));

        // a3: OWL_NS and RDF_NS (missing YAWL_NS)
        registry.publish(liveListing("a3",
                fullSpec("C", new String[]{"WCP-1"}, new String[]{OWL_NS, RDF_NS}, 0.5, 50)));

        // Query: YAWL_NS AND OWL_NS - should match only a1
        var query = TransitionSlotQuery.builder()
                .requireNamespace(YAWL_NS)
                .requireNamespace(OWL_NS)
                .build();

        var results = registry.findForTransitionSlot(query);
        assertEquals(1, results.size());
        assertEquals("a1", results.get(0).agentInfo().getId());
    }

    // =========================================================================
    // Multiple Namespaces with Single Match
    // =========================================================================

    public void testMultipleNamespacesOnlyOneAgent() {
        // a1: All required namespaces
        registry.publish(liveListing("a1",
                fullSpec("A", new String[]{"WCP-1"}, new String[]{YAWL_NS, OWL_NS, RDF_NS}, 0.5, 50)));

        // a2: Two of three namespaces
        registry.publish(liveListing("a2",
                fullSpec("B", new String[]{"WCP-1"}, new String[]{YAWL_NS, OWL_NS}, 0.5, 50)));

        // Query: all three namespaces - only a1 matches
        var query = TransitionSlotQuery.builder()
                .requireNamespace(YAWL_NS)
                .requireNamespace(OWL_NS)
                .requireNamespace(RDF_NS)
                .build();

        var results = registry.findForTransitionSlot(query);
        assertEquals(1, results.size());
        assertEquals("a1", results.get(0).agentInfo().getId());
    }

    // =========================================================================
    // Multiple WCP Patterns (Agent must support ANY)
    // =========================================================================

    public void testMultipleWcpPatterns() {
        // a1: WCP-1 and WCP-5
        registry.publish(liveListing("a1",
                fullSpec("A", new String[]{"WCP-1", "WCP-5"}, new String[]{YAWL_NS}, 0.5, 50)));

        // a2: WCP-1 only
        registry.publish(liveListing("a2",
                fullSpec("B", new String[]{"WCP-1"}, new String[]{YAWL_NS}, 0.5, 50)));

        // a3: WCP-17 only
        registry.publish(liveListing("a3",
                fullSpec("C", new String[]{"WCP-17"}, new String[]{YAWL_NS}, 0.5, 50)));

        // Query: WCP-1 - should match a1 and a2 (both support it)
        var query = TransitionSlotQuery.builder()
                .requireWcpPattern("WCP-1")
                .build();

        var results = registry.findForTransitionSlot(query);
        assertEquals(2, results.size());
    }

    // =========================================================================
    // Early Termination (Empty Result at Any Step)
    // =========================================================================

    public void testEarlyTerminationOnEmptyWcp() {
        // Publish agents with various combinations
        registry.publish(liveListing("a1",
                fullSpec("A", new String[]{"WCP-1"}, new String[]{YAWL_NS}, 0.5, 50)));
        registry.publish(liveListing("a2",
                fullSpec("B", new String[]{"WCP-1"}, new String[]{YAWL_NS}, 1.5, 50)));

        // Query: WCP-99 (doesn't exist) - should short-circuit immediately
        long start = System.nanoTime();
        var query = TransitionSlotQuery.builder()
                .requireWcpPattern("WCP-99")
                .build();
        var results = registry.findForTransitionSlot(query);
        long elapsed = System.nanoTime() - start;

        assertEquals(0, results.size());
        // Should complete very quickly (no namespace filtering needed)
        assertTrue("Early termination should be <1ms, was " + (elapsed / 1_000_000) + "ms",
                elapsed < 1_000_000);
    }

    public void testEarlyTerminationOnEmptyNamespace() {
        // Publish agents
        registry.publish(liveListing("a1",
                fullSpec("A", new String[]{"WCP-1"}, new String[]{YAWL_NS}, 0.5, 50)));
        registry.publish(liveListing("a2",
                fullSpec("B", new String[]{"WCP-1"}, new String[]{OWL_NS}, 0.5, 50)));

        // Query: YAWL_NS + non-existent namespace
        var query = TransitionSlotQuery.builder()
                .requireNamespace(YAWL_NS)
                .requireNamespace("http://nonexistent.org/ns#")
                .build();

        var results = registry.findForTransitionSlot(query);
        assertEquals(0, results.size());
    }

    // =========================================================================
    // Ordering Correctness After Intersection
    // =========================================================================

    public void testOrderingAfterIntersection() {
        // a1: WCP-1, cost 1.0, latency 100ms
        registry.publish(liveListing("a1",
                fullSpec("A", new String[]{"WCP-1"}, new String[]{YAWL_NS}, 1.0, 100)));

        // a2: WCP-1, cost 0.5, latency 50ms
        registry.publish(liveListing("a2",
                fullSpec("B", new String[]{"WCP-1"}, new String[]{YAWL_NS}, 0.5, 50)));

        // a3: WCP-1, cost 0.8, latency 75ms
        registry.publish(liveListing("a3",
                fullSpec("C", new String[]{"WCP-1"}, new String[]{YAWL_NS}, 0.8, 75)));

        // Query all WCP-1 agents - should order by cost: a2 (0.5), a3 (0.8), a1 (1.0)
        var query = TransitionSlotQuery.builder()
                .requireWcpPattern("WCP-1")
                .build();

        var results = registry.findForTransitionSlot(query);
        assertEquals(3, results.size());
        assertEquals("a2", results.get(0).agentInfo().getId());
        assertEquals("a3", results.get(1).agentInfo().getId());
        assertEquals("a1", results.get(2).agentInfo().getId());
    }

    public void testOrderingByLatencyTieBreaker() {
        // a1: WCP-1, cost 1.0, latency 100ms
        registry.publish(liveListing("a1",
                fullSpec("A", new String[]{"WCP-1"}, new String[]{YAWL_NS}, 1.0, 100)));

        // a2: WCP-1, cost 1.0, latency 50ms (same cost, faster)
        registry.publish(liveListing("a2",
                fullSpec("B", new String[]{"WCP-1"}, new String[]{YAWL_NS}, 1.0, 50)));

        // Query all WCP-1 - should order by cost (tie), then latency: a2 (50ms), a1 (100ms)
        var query = TransitionSlotQuery.builder()
                .requireWcpPattern("WCP-1")
                .build();

        var results = registry.findForTransitionSlot(query);
        assertEquals(2, results.size());
        assertEquals("a2", results.get(0).agentInfo().getId());
        assertEquals("a1", results.get(1).agentInfo().getId());
    }

    // =========================================================================
    // Correctness: No False Positives
    // =========================================================================

    public void testNoFalsePositivesOnNamespace() {
        // a1: YAWL_NS
        registry.publish(liveListing("a1",
                fullSpec("A", new String[]{"WCP-1"}, new String[]{YAWL_NS}, 0.5, 50)));

        // a2: OWL_NS (not YAWL_NS)
        registry.publish(liveListing("a2",
                fullSpec("B", new String[]{"WCP-1"}, new String[]{OWL_NS}, 0.5, 50)));

        // Query: YAWL_NS - should NOT include a2
        var query = TransitionSlotQuery.builder()
                .requireNamespace(YAWL_NS)
                .build();

        var results = registry.findForTransitionSlot(query);
        assertEquals(1, results.size());
        assertEquals("a1", results.get(0).agentInfo().getId());

        // Verify a2 is definitely not in results
        for (var listing : results) {
            assertNotSame("a2", listing.agentInfo().getId());
        }
    }

    public void testNoFalsePositivesOnCost() {
        // a1: cost 0.5
        registry.publish(liveListing("a1",
                fullSpec("A", new String[]{"WCP-1"}, new String[]{YAWL_NS}, 0.5, 50)));

        // a2: cost 1.5 (too expensive)
        registry.publish(liveListing("a2",
                fullSpec("B", new String[]{"WCP-1"}, new String[]{YAWL_NS}, 1.5, 50)));

        // Query: cost <= 1.0
        var query = TransitionSlotQuery.builder()
                .maxCostPerCycle(1.0)
                .build();

        var results = registry.findForTransitionSlot(query);
        assertEquals(1, results.size());
        assertEquals("a1", results.get(0).agentInfo().getId());
    }

    public void testNoFalsePositivesOnLatency() {
        // a1: latency 50ms
        registry.publish(liveListing("a1",
                fullSpec("A", new String[]{"WCP-1"}, new String[]{YAWL_NS}, 0.5, 50)));

        // a2: latency 150ms (too slow)
        registry.publish(liveListing("a2",
                fullSpec("B", new String[]{"WCP-1"}, new String[]{YAWL_NS}, 0.5, 150)));

        // Query: latency <= 100ms
        var query = TransitionSlotQuery.builder()
                .maxP99LatencyMs(100)
                .build();

        var results = registry.findForTransitionSlot(query);
        assertEquals(1, results.size());
        assertEquals("a1", results.get(0).agentInfo().getId());
    }

    // =========================================================================
    // Correctness: No False Negatives
    // =========================================================================

    public void testNoFalseNegativesWhenAllMatch() {
        // a1, a2, a3: all match query criteria
        for (int i = 1; i <= 3; i++) {
            registry.publish(liveListing("a" + i,
                    fullSpec("Agent" + i, new String[]{"WCP-1"}, new String[]{YAWL_NS}, 0.5, 50)));
        }

        // Query: WCP-1 AND YAWL_NS - should include all 3
        var query = TransitionSlotQuery.builder()
                .requireWcpPattern("WCP-1")
                .requireNamespace(YAWL_NS)
                .build();

        var results = registry.findForTransitionSlot(query);
        assertEquals(3, results.size());
    }

    public void testNoFalseNegativesWithBoundaryValues() {
        // a1: cost exactly 1.0
        registry.publish(liveListing("a1",
                fullSpec("A", new String[]{"WCP-1"}, new String[]{YAWL_NS}, 1.0, 50)));

        // Query: cost <= 1.0 (should include a1 since boundary is inclusive)
        var query = TransitionSlotQuery.builder()
                .maxCostPerCycle(1.0)
                .build();

        var results = registry.findForTransitionSlot(query);
        assertEquals(1, results.size());
        assertEquals("a1", results.get(0).agentInfo().getId());
    }

    // =========================================================================
    // Index Consistency After Updates
    // =========================================================================

    public void testIndexConsistencyAfterUpdate() {
        // Initial: a1 has WCP-1
        registry.publish(liveListing("a1",
                fullSpec("A", new String[]{"WCP-1"}, new String[]{YAWL_NS}, 0.5, 50)));

        var query1 = TransitionSlotQuery.builder().requireWcpPattern("WCP-1").build();
        assertEquals(1, registry.findForTransitionSlot(query1).size());

        // Update: a1 changes to WCP-5
        registry.publish(liveListing("a1",
                fullSpec("A", new String[]{"WCP-5"}, new String[]{YAWL_NS}, 0.5, 50)));

        // WCP-1 index should be empty now
        var query2 = TransitionSlotQuery.builder().requireWcpPattern("WCP-1").build();
        assertEquals(0, registry.findForTransitionSlot(query2).size());

        // WCP-5 index should have a1
        var query3 = TransitionSlotQuery.builder().requireWcpPattern("WCP-5").build();
        assertEquals(1, registry.findForTransitionSlot(query3).size());
    }
}
