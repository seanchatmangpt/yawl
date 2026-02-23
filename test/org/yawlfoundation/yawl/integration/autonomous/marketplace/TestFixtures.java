package org.yawlfoundation.yawl.integration.autonomous.marketplace;

import org.yawlfoundation.yawl.integration.autonomous.AgentCapability;
import org.yawlfoundation.yawl.integration.autonomous.registry.AgentInfo;

import java.time.Instant;
import java.util.List;

/**
 * Shared test fixtures for marketplace SPARQL/RDF/binding tests.
 *
 * <p>No mocks — all fixtures use real domain objects with real field values.</p>
 */
final class TestFixtures {

    private TestFixtures() {
        throw new UnsupportedOperationException("Test fixture helper — do not instantiate");
    }

    static AgentInfo agentInfo(String id) {
        return new AgentInfo(id, "Agent " + id, List.of(), "localhost", 8080);
    }

    static AgentCapability capability(String domain) {
        return new AgentCapability(domain, domain + " processing and coordination");
    }

    static AgentMarketplaceSpec buildSpec(String domain) {
        return AgentMarketplaceSpec.builder("1.0.0", capability(domain))
                .ontologicalCoverage(OntologicalCoverage.builder()
                        .namespace("http://www.yawlfoundation.org/yawlschema#")
                        .sparqlPattern("?task yawls:hasDecomposition ?decomp")
                        .build())
                .transitionContract(WorkflowTransitionContract.builder()
                        .wcp("WCP-1")
                        .inputType("yawls:WorkItem")
                        .outputType("yawls:CompletedItem")
                        .build())
                .costProfile(CoordinationCostProfile.pureGraphMatching(0.50))
                .latencyProfile(LatencyProfile.qleverClass())
                .build();
    }

    static AgentMarketplaceListing buildLiveListing(String agentId) {
        Instant now = Instant.now();
        return new AgentMarketplaceListing(
                agentInfo(agentId),
                buildSpec(agentId + "-domain"),
                now,
                now);
    }

    static AgentMarketplaceListing buildStaleListing(String agentId) {
        Instant longAgo = Instant.now().minusSeconds(3600); // 1 hour ago
        return new AgentMarketplaceListing(
                agentInfo(agentId),
                buildSpec(agentId + "-domain"),
                longAgo,
                longAgo);
    }

    static AgentMarketplace buildTestMarketplace(int liveCount) {
        AgentMarketplace marketplace = new AgentMarketplace();
        for (int i = 0; i < liveCount; i++) {
            marketplace.publish(buildLiveListing("agent-" + i));
        }
        return marketplace;
    }
}
