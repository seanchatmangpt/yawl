package org.yawlfoundation.yawl.integration.autonomous;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.yawlfoundation.yawl.integration.a2a.A2AException;

import junit.framework.TestCase;

/**
 * Integration tests for AgentRegistry using real in-memory agent state.
 *
 * Chicago TDD: No mocks. Real AgentRegistry with real registration, discovery,
 * heartbeat and capability matching logic exercised end-to-end.
 *
 * Coverage target: 85%+ line coverage on AgentRegistry and AgentRegistry.AgentEntry.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class AgentRegistryTest extends TestCase {

    private AgentRegistry registry;

    public AgentRegistryTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Use a long timeout so heartbeat expiry never fires during tests
        registry = new AgentRegistry(3600, 7200);
    }

    @Override
    protected void tearDown() throws Exception {
        if (registry != null) {
            registry.shutdown();
        }
        super.tearDown();
    }

    /**
     * Verify a freshly constructed registry has no agents.
     */
    public void testNewRegistryIsEmpty() {
        assertEquals("New registry should have 0 active agents", 0,
                registry.getActiveAgentCount());
        assertTrue("getAllAgents should return empty list",
                registry.getAllAgents().isEmpty());
    }

    /**
     * Verify registering an agent makes it findable by ID.
     */
    public void testRegisterAgentMakesItFindable() throws A2AException {
        registry.registerAgent("agent-001", "http://localhost:8081",
                Arrays.asList("Ordering", "Procurement"), Collections.emptyMap());

        Optional<AgentRegistry.AgentEntry> result = registry.getAgent("agent-001");

        assertTrue("Registered agent should be findable", result.isPresent());
        AgentRegistry.AgentEntry entry = result.get();
        assertEquals("Agent ID should match", "agent-001", entry.getAgentId());
        assertEquals("Endpoint should match", "http://localhost:8081", entry.getEndpoint());
    }

    /**
     * Verify registered agent has correct capabilities.
     */
    public void testRegisteredAgentHasCorrectCapabilities() throws A2AException {
        List<String> caps = Arrays.asList("Finance", "Accounting");
        registry.registerAgent("agent-002", "http://localhost:8082",
                caps, Collections.emptyMap());

        Optional<AgentRegistry.AgentEntry> result = registry.getAgent("agent-002");

        assertTrue(result.isPresent());
        List<String> registeredCaps = result.get().getCapabilities();
        assertEquals("Should have 2 capabilities", 2, registeredCaps.size());
        assertTrue("Should contain Finance", registeredCaps.contains("Finance"));
        assertTrue("Should contain Accounting", registeredCaps.contains("Accounting"));
    }

    /**
     * Verify registering the same agent ID twice throws A2AException.
     */
    public void testRegisterDuplicateAgentIdThrowsException() throws A2AException {
        registry.registerAgent("agent-dup", "http://localhost:8083",
                Arrays.asList("Ordering"), Collections.emptyMap());

        try {
            registry.registerAgent("agent-dup", "http://localhost:8084",
                    Arrays.asList("Finance"), Collections.emptyMap());
            fail("Expected A2AException for duplicate agent ID");
        } catch (A2AException e) {
            assertNotNull("Exception should have a message", e.getMessage());
        }
    }

    /**
     * Verify registering agent with null ID throws IllegalArgumentException.
     */
    public void testRegisterNullAgentIdThrowsException() {
        try {
            registry.registerAgent(null, "http://localhost:8085",
                    Arrays.asList("Ordering"), Collections.emptyMap());
            fail("Expected IllegalArgumentException for null agent ID");
        } catch (Exception e) {
            assertTrue("Expected IllegalArgumentException",
                    e instanceof IllegalArgumentException || e instanceof A2AException);
        }
    }

    /**
     * Verify registering agent with empty endpoint throws exception.
     */
    public void testRegisterEmptyEndpointThrowsException() {
        try {
            registry.registerAgent("agent-empty-ep", "",
                    Arrays.asList("Ordering"), Collections.emptyMap());
            fail("Expected exception for empty endpoint");
        } catch (Exception e) {
            assertNotNull("Should throw an exception", e.getMessage());
        }
    }

    /**
     * Verify unregistering an agent removes it from the registry.
     */
    public void testUnregisterAgentRemovesIt() throws A2AException {
        registry.registerAgent("agent-unreg", "http://localhost:8086",
                Arrays.asList("Logistics"), Collections.emptyMap());

        boolean removed = registry.unregisterAgent("agent-unreg");

        assertTrue("Unregister should return true for existing agent", removed);
        assertFalse("Agent should not be findable after unregister",
                registry.getAgent("agent-unreg").isPresent());
    }

    /**
     * Verify unregistering a non-existent agent returns false.
     */
    public void testUnregisterNonExistentAgentReturnsFalse() {
        boolean result = registry.unregisterAgent("nonexistent-agent-xyz");

        assertFalse("Unregistering nonexistent agent should return false", result);
    }

    /**
     * Verify findAgentsByCapability returns agents with matching capability.
     */
    public void testFindAgentsByCapabilityReturnsMatches() throws A2AException {
        registry.registerAgent("agent-ord", "http://localhost:8087",
                Arrays.asList("Ordering", "Procurement"), Collections.emptyMap());
        registry.registerAgent("agent-fin", "http://localhost:8088",
                Arrays.asList("Finance", "Accounting"), Collections.emptyMap());
        registry.registerAgent("agent-log", "http://localhost:8089",
                Arrays.asList("Logistics"), Collections.emptyMap());

        List<AgentRegistry.AgentEntry> result = registry.findAgentsByCapability("Ordering");

        assertEquals("Should find exactly 1 ordering agent", 1, result.size());
        assertEquals("Found agent should be agent-ord", "agent-ord",
                result.get(0).getAgentId());
    }

    /**
     * Verify findAgentsByCapability returns empty list when no agents match.
     */
    public void testFindAgentsByCapabilityReturnsEmptyWhenNoMatch() throws A2AException {
        registry.registerAgent("agent-003", "http://localhost:8090",
                Arrays.asList("Finance"), Collections.emptyMap());

        List<AgentRegistry.AgentEntry> result =
                registry.findAgentsByCapability("UnknownCapability");

        assertNotNull("Result should never be null", result);
        assertTrue("Result should be empty for unmatched capability", result.isEmpty());
    }

    /**
     * Verify getActiveAgents returns all currently registered agents.
     */
    public void testGetActiveAgentsReturnsAllRegistered() throws A2AException {
        registry.registerAgent("a1", "http://localhost:9001",
                Arrays.asList("Cap1"), Collections.emptyMap());
        registry.registerAgent("a2", "http://localhost:9002",
                Arrays.asList("Cap2"), Collections.emptyMap());
        registry.registerAgent("a3", "http://localhost:9003",
                Arrays.asList("Cap3"), Collections.emptyMap());

        List<AgentRegistry.AgentEntry> active = registry.getActiveAgents();

        assertEquals("Should have 3 active agents", 3, active.size());
    }

    /**
     * Verify getActiveAgentCount matches actual registration count.
     */
    public void testGetActiveAgentCountMatchesRegistrations() throws A2AException {
        assertEquals("Initially 0 agents", 0, registry.getActiveAgentCount());

        registry.registerAgent("cnt-1", "http://localhost:9011",
                Arrays.asList("X"), Collections.emptyMap());
        assertEquals("After 1 registration: 1 agent", 1, registry.getActiveAgentCount());

        registry.registerAgent("cnt-2", "http://localhost:9012",
                Arrays.asList("Y"), Collections.emptyMap());
        assertEquals("After 2 registrations: 2 agents", 2, registry.getActiveAgentCount());

        registry.unregisterAgent("cnt-1");
        assertEquals("After unregistering 1: 1 agent", 1, registry.getActiveAgentCount());
    }

    /**
     * Verify updateHeartbeat succeeds for an existing agent.
     */
    public void testUpdateHeartbeatSucceedsForExistingAgent() throws A2AException {
        registry.registerAgent("hb-agent", "http://localhost:9020",
                Arrays.asList("Heartbeat"), Collections.emptyMap());

        // Should not throw
        registry.updateHeartbeat("hb-agent");

        Optional<AgentRegistry.AgentEntry> entry = registry.getAgent("hb-agent");
        assertTrue("Agent should still be present after heartbeat", entry.isPresent());
        assertNotNull("Heartbeat timestamp should be set",
                entry.get().getLastHeartbeat());
    }

    /**
     * Verify updateHeartbeat throws A2AException for nonexistent agent.
     */
    public void testUpdateHeartbeatForNonexistentAgentThrowsException() {
        try {
            registry.updateHeartbeat("ghost-agent");
            fail("Expected A2AException for heartbeat of nonexistent agent");
        } catch (A2AException e) {
            assertNotNull("Exception should have a message", e.getMessage());
        }
    }

    /**
     * Verify agent metadata is stored and retrieved correctly.
     */
    public void testAgentMetadataIsStoredAndRetrieved() throws A2AException {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("version", "2.1");
        metadata.put("region", "us-east-1");

        registry.registerAgent("meta-agent", "http://localhost:9030",
                Arrays.asList("Meta"), metadata);

        Optional<AgentRegistry.AgentEntry> entry = registry.getAgent("meta-agent");
        assertTrue(entry.isPresent());
        assertEquals("Version metadata should match", "2.1",
                entry.get().getMetadata().get("version"));
        assertEquals("Region metadata should match", "us-east-1",
                entry.get().getMetadata().get("region"));
    }

    /**
     * Verify agent registered with null capabilities gets empty capability list.
     */
    public void testRegisterAgentWithNullCapabilitiesGetsEmptyList() throws A2AException {
        registry.registerAgent("nocap-agent", "http://localhost:9040",
                null, Collections.emptyMap());

        Optional<AgentRegistry.AgentEntry> entry = registry.getAgent("nocap-agent");
        assertTrue(entry.isPresent());
        assertNotNull("Capabilities should not be null", entry.get().getCapabilities());
        assertTrue("Capabilities should be empty", entry.get().getCapabilities().isEmpty());
    }

    /**
     * Verify clear() removes all agents from the registry.
     */
    public void testClearRemovesAllAgents() throws A2AException {
        registry.registerAgent("cl-1", "http://localhost:9050",
                Arrays.asList("A"), Collections.emptyMap());
        registry.registerAgent("cl-2", "http://localhost:9051",
                Arrays.asList("B"), Collections.emptyMap());

        registry.clear();

        assertEquals("After clear, no active agents", 0, registry.getActiveAgentCount());
        assertTrue("After clear, getAllAgents returns empty", registry.getAllAgents().isEmpty());
    }

    /**
     * Verify AgentEntry initial status is ACTIVE.
     */
    public void testNewAgentEntryStatusIsActive() throws A2AException {
        registry.registerAgent("status-agent", "http://localhost:9060",
                Arrays.asList("Status"), Collections.emptyMap());

        Optional<AgentRegistry.AgentEntry> entry = registry.getAgent("status-agent");

        assertTrue(entry.isPresent());
        assertEquals("New agent status should be ACTIVE",
                AgentRegistry.AgentStatus.ACTIVE, entry.get().getStatus());
    }

    /**
     * Verify findAgentsByCapability finds agents matching any of multiple registered capabilities.
     */
    public void testFindAgentsByCapabilityMultiCapabilityAgent() throws A2AException {
        registry.registerAgent("multi-cap", "http://localhost:9070",
                Arrays.asList("Finance", "Ordering", "Logistics"), Collections.emptyMap());

        List<AgentRegistry.AgentEntry> financeAgents =
                registry.findAgentsByCapability("Finance");
        List<AgentRegistry.AgentEntry> orderingAgents =
                registry.findAgentsByCapability("Ordering");
        List<AgentRegistry.AgentEntry> hrAgents =
                registry.findAgentsByCapability("HR");

        assertEquals("Should find agent for Finance", 1, financeAgents.size());
        assertEquals("Should find agent for Ordering", 1, orderingAgents.size());
        assertTrue("Should not find agent for HR", hrAgents.isEmpty());
    }
}
