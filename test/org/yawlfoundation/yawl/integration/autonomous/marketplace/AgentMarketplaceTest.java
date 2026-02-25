package org.yawlfoundation.yawl.integration.autonomous.marketplace;

import junit.framework.TestCase;
import org.junit.jupiter.api.Tag;
import org.yawlfoundation.yawl.integration.autonomous.AgentCapability;
import org.yawlfoundation.yawl.integration.autonomous.registry.AgentInfo;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Chicago TDD tests for the five-dimensional agent marketplace.
 *
 * <p>No mocks. Real marketplace with real listings, real query predicate composition,
 * and real temporal liveness checking. All assertions target observable state.</p>
 *
 * <p>Test structure mirrors the five marketplace dimensions plus cross-cutting
 * concerns (liveness, ordering, heartbeat). Each dimension has dedicated test
 * methods; integration tests compose multiple dimensions in single queries.</p>
 *
 * @since YAWL 6.0
 */
@Tag("unit")
public class AgentMarketplaceTest extends TestCase {

    // -------------------------------------------------------------------------
    // Shared fixtures
    // -------------------------------------------------------------------------

    private static final String YAWL_NS = "http://www.yawlfoundation.org/yawlschema#";
    private static final String OWL_NS  = "http://www.w3.org/2002/07/owl#";
    private static final String RDF_NS  = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";

    private static final String PATTERN_TASK_DECOMP = "?task yawls:hasDecomposition ?decomp";
    private static final String PATTERN_WORKITEM    = "?wi yawls:status ?status";

    private AgentMarketplace marketplace;

    @Override
    protected void setUp() {
        marketplace = new AgentMarketplace();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private AgentInfo agentInfo(String id) {
        return new AgentInfo(id, "Agent " + id, List.of(), "localhost", 8080);
    }

    private AgentCapability capability(String domain) {
        return new AgentCapability(domain, domain + " processing and coordination");
    }

    /** Live listing: heartbeat just now. */
    private AgentMarketplaceListing liveListing(String id, AgentMarketplaceSpec spec) {
        return AgentMarketplaceListing.publishNow(agentInfo(id), spec);
    }

    /** Stale listing: heartbeat 10 minutes ago — outside default 5-min staleness. */
    private AgentMarketplaceListing staleListing(String id, AgentMarketplaceSpec spec) {
        Instant tenMinutesAgo = Instant.now().minus(Duration.ofMinutes(10));
        return new AgentMarketplaceListing(agentInfo(id), spec, tenMinutesAgo, tenMinutesAgo);
    }

    private AgentMarketplaceSpec minimalSpec(String domain) {
        return AgentMarketplaceSpec.builder("1.0.0", capability(domain))
                .costProfile(CoordinationCostProfile.pureGraphMatching(0.5))
                .build();
    }

    // -------------------------------------------------------------------------
    // Publish / Unpublish
    // -------------------------------------------------------------------------

    public void testPublishAddsListing() {
        marketplace.publish(liveListing("a1", minimalSpec("Ordering")));
        assertEquals(1, marketplace.size());
    }

    public void testPublishReplacesPriorListing() {
        AgentMarketplaceSpec spec1 = minimalSpec("Ordering");
        AgentMarketplaceSpec spec2 = minimalSpec("Carrier");
        marketplace.publish(liveListing("a1", spec1));
        marketplace.publish(liveListing("a1", spec2));
        assertEquals(1, marketplace.size());
        assertEquals("Carrier",
                marketplace.allLiveListings().get(0).spec().capability().domainName());
    }

    public void testUnpublishRemovesListing() {
        marketplace.publish(liveListing("a1", minimalSpec("Ordering")));
        marketplace.unpublish("a1");
        assertEquals(0, marketplace.size());
    }

    public void testUnpublishUnknownIdIsNoOp() {
        marketplace.unpublish("nonexistent");
        assertEquals(0, marketplace.size());
    }

    public void testPublishNullThrows() {
        try {
            marketplace.publish(null);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            // expected
        }
    }

    // -------------------------------------------------------------------------
    // Liveness
    // -------------------------------------------------------------------------

    public void testStaleListingExcludedFromAllLiveListings() {
        marketplace.publish(staleListing("stale", minimalSpec("Ordering")));
        marketplace.publish(liveListing("live", minimalSpec("Carrier")));
        List<AgentMarketplaceListing> live = marketplace.allLiveListings();
        assertEquals(1, live.size());
        assertEquals("live", live.get(0).agentInfo().getId());
    }

    public void testLiveCountOnlyCountsLiveAgents() {
        marketplace.publish(staleListing("s1", minimalSpec("Ordering")));
        marketplace.publish(liveListing("l1", minimalSpec("Ordering")));
        marketplace.publish(liveListing("l2", minimalSpec("Carrier")));
        assertEquals(2, marketplace.liveCount());
        assertEquals(3, marketplace.size());
    }

    public void testHeartbeatRevivesStaleAgent() {
        // Publish stale
        Instant tenMinutesAgo = Instant.now().minus(Duration.ofMinutes(10));
        AgentMarketplaceSpec spec = minimalSpec("Ordering");
        marketplace.publish(new AgentMarketplaceListing(
                agentInfo("a1"), spec, tenMinutesAgo, tenMinutesAgo));

        assertEquals(0, marketplace.liveCount());

        // Heartbeat now
        marketplace.heartbeat("a1", Instant.now());

        assertEquals(1, marketplace.liveCount());
    }

    public void testHeartbeatForUnknownAgentIsNoOp() {
        marketplace.heartbeat("ghost", Instant.now()); // must not throw
        assertEquals(0, marketplace.size());
    }

    // -------------------------------------------------------------------------
    // Dimension 2: Ontological coverage queries
    // -------------------------------------------------------------------------

    public void testFindByNamespaceMatchesDeclaredNamespace() {
        OntologicalCoverage coverage = OntologicalCoverage.builder()
                .namespace(YAWL_NS)
                .namespace(OWL_NS)
                .build();
        AgentMarketplaceSpec spec = AgentMarketplaceSpec.builder("1.0.0", capability("Engine"))
                .ontologicalCoverage(coverage)
                .costProfile(CoordinationCostProfile.pureGraphMatching(0.3))
                .build();

        marketplace.publish(liveListing("engine", spec));
        marketplace.publish(liveListing("other", minimalSpec("Carrier")));

        List<AgentMarketplaceListing> results = marketplace.findByNamespace(YAWL_NS);
        assertEquals(1, results.size());
        assertEquals("engine", results.get(0).agentInfo().getId());
    }

    public void testFindByNamespaceExcludesStaleListings() {
        OntologicalCoverage coverage = OntologicalCoverage.builder()
                .namespace(YAWL_NS)
                .build();
        AgentMarketplaceSpec spec = AgentMarketplaceSpec.builder("1.0.0", capability("Engine"))
                .ontologicalCoverage(coverage)
                .costProfile(CoordinationCostProfile.pureGraphMatching(0.3))
                .build();

        marketplace.publish(staleListing("stale-engine", spec));
        List<AgentMarketplaceListing> results = marketplace.findByNamespace(YAWL_NS);
        assertTrue(results.isEmpty());
    }

    public void testOntologicalCoverageCoversAllNamespaces() {
        OntologicalCoverage coverage = OntologicalCoverage.builder()
                .namespace(YAWL_NS)
                .namespace(OWL_NS)
                .namespace(RDF_NS)
                .build();
        assertTrue(coverage.coversAllNamespaces(List.of(YAWL_NS, OWL_NS)));
        assertTrue(coverage.coversAllNamespaces(List.of(YAWL_NS)));
        assertFalse(coverage.coversAllNamespaces(List.of(YAWL_NS, "http://unknown.ns/")));
    }

    public void testOntologicalCoverageMatchesAllPatterns() {
        OntologicalCoverage coverage = OntologicalCoverage.builder()
                .sparqlPattern(PATTERN_TASK_DECOMP)
                .sparqlPattern(PATTERN_WORKITEM)
                .build();
        assertTrue(coverage.matchesAllPatterns(List.of(PATTERN_TASK_DECOMP)));
        assertTrue(coverage.matchesAllPatterns(List.of(PATTERN_TASK_DECOMP, PATTERN_WORKITEM)));
        assertFalse(coverage.matchesAllPatterns(List.of("?unknown yawls:foo ?bar")));
    }

    // -------------------------------------------------------------------------
    // Dimension 3: Workflow transition contract queries
    // -------------------------------------------------------------------------

    public void testFindByWcpPatternMatchesDeclaredPattern() {
        WorkflowTransitionContract contract = WorkflowTransitionContract.builder()
                .wcp("WCP-1").wcp("WCP-5")
                .inputType(YAWL_NS + "WorkItem")
                .outputType(YAWL_NS + "WorkItem")
                .maintainsSoundness()
                .build();
        AgentMarketplaceSpec spec = AgentMarketplaceSpec.builder("1.0.0", capability("Engine"))
                .transitionContract(contract)
                .costProfile(CoordinationCostProfile.pureGraphMatching(0.3))
                .build();

        marketplace.publish(liveListing("wcp-agent", spec));
        marketplace.publish(liveListing("no-wcp", minimalSpec("Carrier")));

        List<AgentMarketplaceListing> results = marketplace.findByWcpPattern("WCP-1");
        assertEquals(1, results.size());
        assertEquals("wcp-agent", results.get(0).agentInfo().getId());
    }

    public void testFindByWcpPatternMissForUndeclaredPattern() {
        WorkflowTransitionContract contract = WorkflowTransitionContract.builder()
                .wcp("WCP-1")
                .build();
        AgentMarketplaceSpec spec = AgentMarketplaceSpec.builder("1.0.0", capability("Engine"))
                .transitionContract(contract)
                .costProfile(CoordinationCostProfile.pureGraphMatching(0.3))
                .build();

        marketplace.publish(liveListing("wcp-agent", spec));
        assertTrue(marketplace.findByWcpPattern("WCP-17").isEmpty());
    }

    public void testWorkflowContractAcceptsInputTypes() {
        WorkflowTransitionContract contract = WorkflowTransitionContract.builder()
                .inputType(YAWL_NS + "WorkItem")
                .inputType(YAWL_NS + "Task")
                .build();
        assertTrue(contract.acceptsInputTypes(List.of(YAWL_NS + "WorkItem")));
        assertTrue(contract.acceptsInputTypes(List.of(YAWL_NS + "WorkItem", YAWL_NS + "Task")));
        assertFalse(contract.acceptsInputTypes(List.of(YAWL_NS + "WorkItem", YAWL_NS + "Net")));
    }

    // -------------------------------------------------------------------------
    // Dimension 4: Economic cost queries
    // -------------------------------------------------------------------------

    public void testFindByMaxCostFiltersOverBudget() {
        AgentMarketplaceSpec cheap = AgentMarketplaceSpec.builder("1.0.0", capability("A"))
                .costProfile(new CoordinationCostProfile(
                        1.0, 0.0, 0.2, CoordinationCostProfile.PricingModel.PER_CYCLE))
                .build();
        AgentMarketplaceSpec expensive = AgentMarketplaceSpec.builder("1.0.0", capability("B"))
                .costProfile(new CoordinationCostProfile(
                        5.0, 0.8, 2.5, CoordinationCostProfile.PricingModel.PER_CYCLE))
                .build();

        marketplace.publish(liveListing("cheap", cheap));
        marketplace.publish(liveListing("expensive", expensive));

        List<AgentMarketplaceListing> results = marketplace.findByMaxCost(1.0);
        assertEquals(1, results.size());
        assertEquals("cheap", results.get(0).agentInfo().getId());
    }

    public void testFindByMaxCostOrdersByCostAscending() {
        for (int i = 3; i >= 1; i--) {
            double cost = i * 0.3;
            AgentMarketplaceSpec spec = AgentMarketplaceSpec.builder("1.0.0", capability("A" + i))
                    .costProfile(new CoordinationCostProfile(
                            1.0, 0.1, cost, CoordinationCostProfile.PricingModel.PER_CYCLE))
                    .build();
            marketplace.publish(liveListing("agent" + i, spec));
        }

        List<AgentMarketplaceListing> results = marketplace.findByMaxCost(2.0);
        assertEquals(3, results.size());
        assertEquals("agent1", results.get(0).agentInfo().getId()); // cheapest first
        assertEquals("agent3", results.get(2).agentInfo().getId()); // most expensive last
    }

    public void testCoordinationCostProfileIsWithinBudget() {
        CoordinationCostProfile profile =
                new CoordinationCostProfile(2.0, 0.3, 1.5, CoordinationCostProfile.PricingModel.PER_CYCLE);
        assertTrue(profile.isWithinBudget(1.5));
        assertTrue(profile.isWithinBudget(2.0));
        assertFalse(profile.isWithinBudget(1.4));
    }

    public void testCoordinationCostProfileGraphMatchingDominant() {
        CoordinationCostProfile graphDominant =
                new CoordinationCostProfile(3.0, 0.1, 0.5, CoordinationCostProfile.PricingModel.PER_CYCLE);
        CoordinationCostProfile inferenceDominant =
                new CoordinationCostProfile(0.5, 0.9, 1.5, CoordinationCostProfile.PricingModel.PER_CYCLE);

        assertTrue(graphDominant.isGraphMatchingDominant(0.2));
        assertFalse(inferenceDominant.isGraphMatchingDominant(0.2));
    }

    // -------------------------------------------------------------------------
    // Dimension 5: Latency queries
    // -------------------------------------------------------------------------

    public void testFindByMaxLatencyFiltersSlowAgents() {
        LatencyProfile fast = new LatencyProfile(1_000_000_000L, 5L, 50L, 95L);   // QLever-class
        LatencyProfile slow = new LatencyProfile(1_000_000_000L, 80L, 500L, 1200L); // standard SPARQL

        AgentMarketplaceSpec fastSpec = AgentMarketplaceSpec.builder("1.0.0", capability("Fast"))
                .costProfile(CoordinationCostProfile.pureGraphMatching(0.5))
                .latencyProfile(fast)
                .build();
        AgentMarketplaceSpec slowSpec = AgentMarketplaceSpec.builder("1.0.0", capability("Slow"))
                .costProfile(CoordinationCostProfile.pureGraphMatching(0.5))
                .latencyProfile(slow)
                .build();

        marketplace.publish(liveListing("fast-agent", fastSpec));
        marketplace.publish(liveListing("slow-agent", slowSpec));

        List<AgentMarketplaceListing> results = marketplace.findByMaxLatency(100L);
        assertEquals(1, results.size());
        assertEquals("fast-agent", results.get(0).agentInfo().getId());
    }

    public void testFindByMaxLatencyExcludesUndeclaredProfiles() {
        // Agent with no latency profile declared
        marketplace.publish(liveListing("no-profile", minimalSpec("Ordering")));

        // No profiles declared → cannot make a verifiable claim → excluded
        List<AgentMarketplaceListing> results = marketplace.findByMaxLatency(500L);
        assertTrue(results.isEmpty());
    }

    public void testLatencyProfileSatisfiesLatency() {
        LatencyProfile profile = new LatencyProfile(100_000L, 10L, 80L, 150L);
        assertTrue(profile.satisfiesLatency(150L));
        assertTrue(profile.satisfiesLatency(200L));
        assertFalse(profile.satisfiesLatency(149L));
    }

    public void testLatencyProfileOrdering() {
        try {
            new LatencyProfile(1000L, 100L, 50L, 200L); // p95 < p50
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("p95Ms must be >= p50Ms"));
        }
        try {
            new LatencyProfile(1000L, 10L, 100L, 80L); // p99 < p95
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("p99Ms must be >= p95Ms"));
        }
    }

    public void testLatencyAtNearestGraphSize() {
        LatencyProfile small = new LatencyProfile(1_000_000L, 10L, 50L, 90L);
        LatencyProfile large = new LatencyProfile(1_000_000_000L, 5L, 50L, 95L);

        AgentMarketplaceSpec spec = AgentMarketplaceSpec.builder("1.0.0", capability("Engine"))
                .costProfile(CoordinationCostProfile.pureGraphMatching(0.3))
                .latencyProfile(small)
                .latencyProfile(large)
                .build();

        // 800M is closer to 1B (|200M| gap) than to 1M (|799M| gap) → large profile selected
        Optional<LatencyProfile> nearest = spec.latencyAt(800_000_000L);
        assertTrue(nearest.isPresent());
        assertEquals(1_000_000_000L, nearest.get().graphSizeTriples());
    }

    // -------------------------------------------------------------------------
    // findForTransitionSlot: multi-dimensional query
    // -------------------------------------------------------------------------

    public void testTransitionSlotQueryMatchesAllFiveDimensions() {
        OntologicalCoverage coverage = OntologicalCoverage.builder()
                .namespace(YAWL_NS)
                .sparqlPattern(PATTERN_TASK_DECOMP)
                .constructTemplate("CONSTRUCT { ?task yawls:done true } WHERE { ?task yawls:status \"complete\" }")
                .build();

        WorkflowTransitionContract contract = WorkflowTransitionContract.builder()
                .wcp("WCP-1")
                .inputType(YAWL_NS + "WorkItem")
                .outputType(YAWL_NS + "WorkItem")
                .maintainsSoundness()
                .build();

        CoordinationCostProfile cost =
                new CoordinationCostProfile(2.0, 0.1, 0.4, CoordinationCostProfile.PricingModel.PER_CYCLE);

        LatencyProfile latency = LatencyProfile.qleverClass();

        AgentMarketplaceSpec spec = AgentMarketplaceSpec.builder("1.0.0", capability("Engine"))
                .ontologicalCoverage(coverage)
                .transitionContract(contract)
                .costProfile(cost)
                .latencyProfile(latency)
                .ggenTomlHash("abc123def456")
                .build();

        marketplace.publish(liveListing("perfect", spec));
        marketplace.publish(liveListing("no-match", minimalSpec("Carrier")));

        TransitionSlotQuery query = TransitionSlotQuery.builder()
                .requireNamespace(YAWL_NS)
                .requireSparqlPattern(PATTERN_TASK_DECOMP)
                .requireWcpPattern("WCP-1")
                .requireInputType(YAWL_NS + "WorkItem")
                .maxCostPerCycle(1.0)
                .maxP99LatencyMs(100L)
                .atGraphSize(1_000_000_000L)
                .build();

        List<AgentMarketplaceListing> results = marketplace.findForTransitionSlot(query);
        assertEquals(1, results.size());
        assertEquals("perfect", results.get(0).agentInfo().getId());
    }

    public void testTransitionSlotQueryFailsOnMissingNamespace() {
        OntologicalCoverage coverage = OntologicalCoverage.builder()
                .namespace(OWL_NS)  // has OWL but not YAWL
                .build();
        AgentMarketplaceSpec spec = AgentMarketplaceSpec.builder("1.0.0", capability("Engine"))
                .ontologicalCoverage(coverage)
                .costProfile(CoordinationCostProfile.pureGraphMatching(0.3))
                .build();

        marketplace.publish(liveListing("partial-coverage", spec));

        TransitionSlotQuery query = TransitionSlotQuery.builder()
                .requireNamespace(YAWL_NS)  // requires YAWL
                .build();

        assertTrue(marketplace.findForTransitionSlot(query).isEmpty());
    }

    public void testTransitionSlotQueryFailsOnCostExceeded() {
        AgentMarketplaceSpec spec = AgentMarketplaceSpec.builder("1.0.0", capability("Engine"))
                .costProfile(new CoordinationCostProfile(
                        1.0, 0.0, 5.0, CoordinationCostProfile.PricingModel.PER_CYCLE))
                .build();

        marketplace.publish(liveListing("expensive", spec));

        TransitionSlotQuery query = TransitionSlotQuery.builder()
                .maxCostPerCycle(1.0)
                .build();

        assertTrue(marketplace.findForTransitionSlot(query).isEmpty());
    }

    public void testTransitionSlotQueryFailsOnLatencyExceeded() {
        LatencyProfile slow = new LatencyProfile(1_000_000L, 500L, 2000L, 5000L);
        AgentMarketplaceSpec spec = AgentMarketplaceSpec.builder("1.0.0", capability("Engine"))
                .costProfile(CoordinationCostProfile.pureGraphMatching(0.1))
                .latencyProfile(slow)
                .build();

        marketplace.publish(liveListing("slow", spec));

        TransitionSlotQuery query = TransitionSlotQuery.builder()
                .maxP99LatencyMs(100L)
                .atGraphSize(1_000_000L)
                .build();

        assertTrue(marketplace.findForTransitionSlot(query).isEmpty());
    }

    public void testTransitionSlotQueryExcludesStaleListings() {
        AgentMarketplaceSpec spec = minimalSpec("Engine");
        marketplace.publish(staleListing("stale", spec));

        TransitionSlotQuery query = TransitionSlotQuery.builder().build();
        assertTrue(marketplace.findForTransitionSlot(query).isEmpty());
    }

    public void testTransitionSlotQueryResultsOrderedByCostThenLatency() {
        // Three agents: different costs
        for (int i = 1; i <= 3; i++) {
            double cost = i * 0.5;
            AgentMarketplaceSpec spec = AgentMarketplaceSpec.builder("1.0.0", capability("E" + i))
                    .costProfile(new CoordinationCostProfile(
                            1.0, 0.0, cost, CoordinationCostProfile.PricingModel.PER_CYCLE))
                    .build();
            marketplace.publish(liveListing("agent" + i, spec));
        }

        List<AgentMarketplaceListing> results =
                marketplace.findForTransitionSlot(TransitionSlotQuery.builder().build());

        assertEquals(3, results.size());
        // Cheapest (0.5) first
        assertEquals("agent1", results.get(0).agentInfo().getId());
        assertEquals("agent2", results.get(1).agentInfo().getId());
        assertEquals("agent3", results.get(2).agentInfo().getId());
    }

    public void testTransitionSlotQueryCapabilityKeywordFilter() {
        marketplace.publish(liveListing("ordering", minimalSpec("Ordering")));
        marketplace.publish(liveListing("carrier", minimalSpec("Carrier")));

        TransitionSlotQuery query = TransitionSlotQuery.builder()
                .capabilityKeyword("ordering")
                .build();

        List<AgentMarketplaceListing> results = marketplace.findForTransitionSlot(query);
        assertEquals(1, results.size());
        assertEquals("ordering", results.get(0).agentInfo().getId());
    }

    // -------------------------------------------------------------------------
    // AgentMarketplaceSpec: ggen.toml integration
    // -------------------------------------------------------------------------

    public void testSpecIsDerivedFromGgenToml() {
        AgentMarketplaceSpec withHash = AgentMarketplaceSpec.builder("1.0.0", capability("Engine"))
                .costProfile(CoordinationCostProfile.pureGraphMatching(0.3))
                .ggenTomlHash("sha256:deadbeef")
                .build();
        AgentMarketplaceSpec withoutHash = minimalSpec("Engine");

        assertTrue(withHash.isDerivedFromGgenToml());
        assertFalse(withoutHash.isDerivedFromGgenToml());
    }

    public void testSpecWorstCaseLatencySelectsHighestP99() {
        LatencyProfile fast = new LatencyProfile(1_000_000L, 5L, 40L, 80L);
        LatencyProfile slow = new LatencyProfile(1_000_000_000L, 20L, 200L, 450L);

        AgentMarketplaceSpec spec = AgentMarketplaceSpec.builder("1.0.0", capability("Engine"))
                .costProfile(CoordinationCostProfile.pureGraphMatching(0.3))
                .latencyProfile(fast)
                .latencyProfile(slow)
                .build();

        Optional<LatencyProfile> worst = spec.worstCaseLatency();
        assertTrue(worst.isPresent());
        assertEquals(450L, worst.get().p99Ms());
    }

    public void testSpecWorstCaseLatencyEmptyWhenNoProfiles() {
        AgentMarketplaceSpec spec = minimalSpec("Engine");
        assertFalse(spec.worstCaseLatency().isPresent());
    }

    // -------------------------------------------------------------------------
    // AgentMarketplaceListing: liveness and heartbeat
    // -------------------------------------------------------------------------

    public void testListingIsLiveWithinStaleness() {
        AgentMarketplaceListing listing = AgentMarketplaceListing.publishNow(
                agentInfo("a1"), minimalSpec("Ordering"));
        assertTrue(listing.isLive(Duration.ofMinutes(5)));
    }

    public void testListingIsStaleOutsideStaleness() {
        Instant old = Instant.now().minus(Duration.ofHours(1));
        AgentMarketplaceListing listing = new AgentMarketplaceListing(
                agentInfo("a1"), minimalSpec("Ordering"), old, old);
        assertFalse(listing.isLive(Duration.ofMinutes(5)));
    }

    public void testListingWithHeartbeatPreservesRegistration() {
        Instant registered = Instant.now().minus(Duration.ofMinutes(30));
        AgentMarketplaceListing original = new AgentMarketplaceListing(
                agentInfo("a1"), minimalSpec("Ordering"), registered, registered);

        Instant newHb = Instant.now();
        AgentMarketplaceListing updated = original.withHeartbeat(newHb);

        assertEquals(registered, updated.registeredAt());
        assertEquals(newHb, updated.lastHeartbeatAt());
        assertTrue(updated.isLive(Duration.ofMinutes(5)));
    }

    public void testListingHeartbeatBeforeRegistrationThrows() {
        Instant now = Instant.now();
        Instant earlier = now.minus(Duration.ofSeconds(1));
        try {
            new AgentMarketplaceListing(agentInfo("a1"), minimalSpec("Ordering"), now, earlier);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("lastHeartbeatAt must be >= registeredAt"));
        }
    }

    // -------------------------------------------------------------------------
    // findById
    // -------------------------------------------------------------------------

    public void testFindByIdReturnsLiveListing() {
        marketplace.publish(liveListing("a1", minimalSpec("Ordering")));
        Optional<AgentMarketplaceListing> found =
                marketplace.findById("a1", Duration.ofMinutes(5));
        assertTrue(found.isPresent());
        assertEquals("a1", found.get().agentInfo().getId());
    }

    public void testFindByIdReturnsEmptyForStaleListing() {
        marketplace.publish(staleListing("a1", minimalSpec("Ordering")));
        Optional<AgentMarketplaceListing> found =
                marketplace.findById("a1", Duration.ofMinutes(5));
        assertFalse(found.isPresent());
    }

    public void testFindByIdReturnsEmptyForUnknownAgent() {
        assertFalse(marketplace.findById("ghost", Duration.ofMinutes(5)).isPresent());
    }

    // -------------------------------------------------------------------------
    // Factory methods and static builders
    // -------------------------------------------------------------------------

    public void testOntologicalCoverageEmpty() {
        OntologicalCoverage empty = OntologicalCoverage.empty();
        assertTrue(empty.declaredNamespaces().isEmpty());
        assertTrue(empty.sparqlPatterns().isEmpty());
        assertTrue(empty.constructTemplates().isEmpty());
        assertTrue(empty.coversAllNamespaces(List.of()));
        assertFalse(empty.coversAllNamespaces(List.of(YAWL_NS)));
    }

    public void testWorkflowTransitionContractUnconstrained() {
        WorkflowTransitionContract c = WorkflowTransitionContract.unconstrained();
        assertFalse(c.maintainsSoundness());
        assertTrue(c.wcpPatterns().isEmpty());
        assertTrue(c.acceptsInputTypes(List.of()));
        assertFalse(c.acceptsInputTypes(List.of(YAWL_NS + "WorkItem")));
    }

    public void testLatencyProfileFactoryMethods() {
        LatencyProfile qlever = LatencyProfile.qleverClass();
        assertEquals(1_000_000_000L, qlever.graphSizeTriples());
        assertTrue(qlever.satisfiesLatency(100L));

        LatencyProfile inference = LatencyProfile.inferenceLatency();
        assertFalse(inference.satisfiesLatency(100L));
    }

    public void testCoordinationCostProfilePureGraphMatching() {
        CoordinationCostProfile p = CoordinationCostProfile.pureGraphMatching(0.5);
        assertEquals(0.0, p.llmInferenceRatio(), 0.001);
        assertEquals(CoordinationCostProfile.PricingModel.PER_CYCLE, p.pricingModel());
    }

    public void testCoordinationCostProfilePureInference() {
        CoordinationCostProfile p = CoordinationCostProfile.pureInference(2.0);
        assertEquals(1.0, p.llmInferenceRatio(), 0.001);
        assertEquals(0.0, p.constructQueriesPerCall(), 0.001);
    }

    // -------------------------------------------------------------------------
    // Validation: illegal argument guards
    // -------------------------------------------------------------------------

    public void testOntologicalCoverageRejectsNullNamespace() {
        try {
            OntologicalCoverage.builder().namespace(null).build();
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            // expected
        }
    }

    public void testCoordinationCostProfileRejectsNegativePrice() {
        try {
            new CoordinationCostProfile(1.0, 0.5, -0.1, CoordinationCostProfile.PricingModel.PER_CYCLE);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("basePricePerCycle must be >= 0.0"));
        }
    }

    public void testCoordinationCostProfileRejectsInvalidInferenceRatio() {
        try {
            new CoordinationCostProfile(1.0, 1.5, 0.5, CoordinationCostProfile.PricingModel.PER_CYCLE);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("llmInferenceRatio must be in [0.0, 1.0]"));
        }
    }

    public void testLatencyProfileRejectsZeroGraphSize() {
        try {
            new LatencyProfile(0L, 10L, 50L, 100L);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("graphSizeTriples must be > 0"));
        }
    }

    public void testAgentMarketplaceSpecRejectsBlankVersion() {
        try {
            AgentMarketplaceSpec.builder("  ", capability("Engine"))
                    .costProfile(CoordinationCostProfile.pureGraphMatching(0.3))
                    .build();
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("specVersion must not be blank"));
        }
    }
}
