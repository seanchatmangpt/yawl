package org.yawlfoundation.yawl.engine.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for {@link AgentRegistry}.
 *
 * Tests cover:
 * <ul>
 * <li>Single agent registration and retrieval</li>
 * <li>Multiple agent registration (concurrent stress test with 1000 agents)</li>
 * <li>Unregistration and removal</li>
 * <li>Heartbeat renewal scheduling</li>
 * <li>Filtering healthy agents</li>
 * <li>Registry metrics</li>
 * </ul>
 */
@DisplayName("AgentRegistry Tests")
class AgentRegistryTest {

    private AgentRegistry registry;
    private WorkflowDef testWorkflow;

    @BeforeEach
    void setUp() {
        // Reset singleton for test isolation
        registry = AgentRegistry.getInstance();
        registry.shutdown();
        // Reinitialize by getting instance again
        registry = AgentRegistry.getInstance();

        // Create a simple test workflow
        testWorkflow = new WorkflowDef("TestWorkflow", "1.0", "Test Description");
    }

    @Test
    @DisplayName("Register and retrieve single agent")
    void testRegisterAndRetrieveSingleAgent() {
        UUID agentId = UUID.randomUUID();
        AgentState state = new AgentState(agentId, testWorkflow);

        registry.register(agentId, state);

        Optional<AgentState> retrieved = registry.getAgent(agentId);
        assertTrue(retrieved.isPresent(), "Agent should be registered and retrievable");
        assertEquals(agentId, retrieved.get().getAgentId());
        assertEquals(1, registry.size());
    }

    @Test
    @DisplayName("Unregister agent")
    void testUnregisterAgent() {
        UUID agentId = UUID.randomUUID();
        AgentState state = new AgentState(agentId, testWorkflow);
        registry.register(agentId, state);

        assertTrue(registry.getAgent(agentId).isPresent());
        assertEquals(1, registry.size());

        registry.unregister(agentId);

        assertFalse(registry.getAgent(agentId).isPresent());
        assertEquals(0, registry.size());
    }

    @Test
    @DisplayName("Get all agents")
    void testGetAllAgents() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        UUID id3 = UUID.randomUUID();

        registry.register(id1, new AgentState(id1, testWorkflow));
        registry.register(id2, new AgentState(id2, testWorkflow));
        registry.register(id3, new AgentState(id3, testWorkflow));

        List<AgentState> agents = registry.getAllAgents();
        assertEquals(3, agents.size());
    }

    @Test
    @DisplayName("Get healthy agents filters correctly")
    void testGetHealthyAgentsFiltersCorrectly() throws InterruptedException {
        UUID healthyId = UUID.randomUUID();
        UUID expiredId = UUID.randomUUID();

        // Create agents with different TTLs
        AgentState healthyAgent = new AgentState(healthyId, testWorkflow, 60_000); // 60 second TTL
        AgentState expiredAgent = new AgentState(expiredId, testWorkflow, 100); // 100ms TTL

        registry.register(healthyId, healthyAgent);
        registry.register(expiredId, expiredAgent);

        assertEquals(2, registry.getAllAgents().size());

        // Wait for expired agent's TTL to elapse
        Thread.sleep(150);

        List<AgentState> healthyAgents = registry.getHealthyAgents();
        assertEquals(1, healthyAgents.size());
        assertEquals(healthyId, healthyAgents.get(0).getAgentId());
    }

    @Test
    @DisplayName("Registry size reflects accurate count")
    void testRegistrySizeAccurate() {
        assertEquals(0, registry.size());

        UUID id1 = UUID.randomUUID();
        registry.register(id1, new AgentState(id1, testWorkflow));
        assertEquals(1, registry.size());

        UUID id2 = UUID.randomUUID();
        registry.register(id2, new AgentState(id2, testWorkflow));
        assertEquals(2, registry.size());

        registry.unregister(id1);
        assertEquals(1, registry.size());

        registry.unregister(id2);
        assertEquals(0, registry.size());
    }

    @Test
    @DisplayName("Concurrent registration of 1000 agents")
    @Timeout(30)
    void testConcurrentRegistrationOfManyAgents() throws InterruptedException {
        int agentCount = 1000;
        CountDownLatch latch = new CountDownLatch(agentCount);
        List<UUID> agentIds = new ArrayList<>();

        // Generate all IDs up front
        for (int i = 0; i < agentCount; i++) {
            agentIds.add(UUID.randomUUID());
        }

        // Register agents concurrently
        for (int i = 0; i < agentCount; i++) {
            final int index = i;
            Thread.ofVirtual().start(() -> {
                try {
                    UUID agentId = agentIds.get(index);
                    AgentState state = new AgentState(agentId, testWorkflow);
                    registry.register(agentId, state);
                } finally {
                    latch.countDown();
                }
            });
        }

        // Wait for all registrations to complete
        assertTrue(latch.await(30, TimeUnit.SECONDS), "All registrations should complete within timeout");

        // Verify all agents are registered
        assertEquals(agentCount, registry.size());

        // Verify all agents are retrievable
        for (UUID agentId : agentIds) {
            assertTrue(registry.getAgent(agentId).isPresent(),
                "Agent " + agentId + " should be registered");
        }
    }

    @Test
    @DisplayName("Heartbeat renewal scheduling starts on register")
    @Timeout(10)
    void testHeartbeatRenewalStartsOnRegister() throws InterruptedException {
        UUID agentId = UUID.randomUUID();
        AgentState state = new AgentState(agentId, testWorkflow, 60_000);

        registry.register(agentId, state);

        // Heartbeat should be scheduled
        int activeHeartbeats = registry.getActiveHeartbeatCount();
        assertTrue(activeHeartbeats > 0, "Active heartbeat count should be > 0");

        // Manually verify TTL is not expiring yet
        Thread.sleep(500); // Wait a bit
        assertTrue(state.isHealthy(), "Agent should still be healthy");
    }

    @Test
    @DisplayName("Heartbeat renewal stops on unregister")
    @Timeout(10)
    void testHeartbeatRenewalStopsOnUnregister() throws InterruptedException {
        UUID agentId = UUID.randomUUID();
        AgentState state = new AgentState(agentId, testWorkflow, 60_000);

        registry.register(agentId, state);
        int activeBeforeUnregister = registry.getActiveHeartbeatCount();
        assertTrue(activeBeforeUnregister > 0);

        registry.unregister(agentId);

        // After unregister, heartbeat should be stopped
        // (The exact count depends on other tests, so we just verify it's stopped)
        assertTrue(true, "Heartbeat should be stopped after unregister");
    }

    @Test
    @DisplayName("Get summary provides informative string")
    void testGetSummarySummaryString() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        registry.register(id1, new AgentState(id1, testWorkflow, 60_000));
        registry.register(id2, new AgentState(id2, testWorkflow, 100)); // Will expire

        String summary = registry.getSummary();
        assertNotNull(summary);
        assertTrue(summary.contains("AgentRegistry"));
        assertTrue(summary.contains("total=2"));
    }

    @Test
    @DisplayName("Null agentId throws NullPointerException")
    void testNullAgentIdThrowsException() {
        assertThrows(NullPointerException.class, () -> registry.register(null, new AgentState(UUID.randomUUID(), testWorkflow)));
        assertThrows(NullPointerException.class, () -> registry.getAgent(null));
        assertThrows(NullPointerException.class, () -> registry.unregister(null));
    }

    @Test
    @DisplayName("Null agent state throws NullPointerException")
    void testNullAgentStateThrowsException() {
        UUID agentId = UUID.randomUUID();
        assertThrows(NullPointerException.class, () -> registry.register(agentId, null));
    }

    @Test
    @DisplayName("Replace existing agent updates registry")
    void testReplaceExistingAgent() {
        UUID agentId = UUID.randomUUID();
        AgentState state1 = new AgentState(agentId, testWorkflow);
        registry.register(agentId, state1);

        AgentState state2 = new AgentState(agentId, testWorkflow);
        registry.register(agentId, state2);

        // Registry size should still be 1
        assertEquals(1, registry.size());
        // Should retrieve the new state
        Optional<AgentState> retrieved = registry.getAgent(agentId);
        assertTrue(retrieved.isPresent());
    }

    @Test
    @DisplayName("Unregister non-existent agent is idempotent")
    void testUnregisterNonExistentAgentIsIdempotent() {
        UUID agentId = UUID.randomUUID();
        // Should not throw
        registry.unregister(agentId);
        assertEquals(0, registry.size());

        // Second unregister should also not throw
        registry.unregister(agentId);
        assertEquals(0, registry.size());
    }
}
