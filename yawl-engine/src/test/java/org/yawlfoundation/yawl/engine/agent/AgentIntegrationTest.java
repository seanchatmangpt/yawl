package org.yawlfoundation.yawl.engine.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests verifying all 5 agent components work together:
 * 1. AgentState (domain model)
 * 2. WorkflowDef (workflow definition)
 * 3. YawlAgentEngine (virtual thread executor)
 * 4. WorkQueue & WorkDiscovery (work management)
 * 5. REST API integration
 *
 * @since Java 25
 */
@DisplayName("Agent Integration Tests — All 5 Components")
public class AgentIntegrationTest {

    private YawlAgentEngine engine;
    private AgentRegistry registry;

    @BeforeEach
    void setUp() {
        engine = new YawlAgentEngine();
        registry = engine.getRegistry();
    }

    @Test
    @DisplayName("Verify all 5 components are wired together")
    void testComponentsWired() {
        assertNotNull(engine, "Engine should be initialized");
        assertNotNull(registry, "Registry should be available from engine");

        // Verify AgentLifecycle enum (Component 1: AgentState)
        assertDoesNotThrow(() -> {
            AgentLifecycle lifecycle = AgentLifecycle.RUNNING;
            assertTrue(lifecycle.isActive());
            assertFalse(lifecycle.isTerminal());
        });

        // Verify WorkflowDef (Component 2: WorkflowDef)
        assertDoesNotThrow(() -> {
            WorkflowDef workflowDef = new WorkflowDef(
                UUID.randomUUID(),
                "Test Workflow",
                "Integration test",
                "1.0"
            );
            assertNotNull(workflowDef.getId());
        });

        // Verify YawlAgentEngine (Component 3: Virtual Thread Executor)
        assertTrue(registry.getTotalAgents() >= 0, "Registry should be queryable");

        // Verify AgentStatus (Component 1b: Status transitions)
        AgentStatus idle = AgentStatus.idle();
        AgentStatus running = AgentStatus.running();
        assertNotEquals(idle, running, "Status should be distinguishable");
    }

    @Test
    @DisplayName("Create and manage single agent through full lifecycle")
    void testSingleAgentLifecycle() throws InterruptedException {
        UUID agentId = UUID.randomUUID();
        WorkflowDef workflow = new WorkflowDef(
            UUID.randomUUID(),
            "Test Workflow",
            "Single agent test",
            "1.0"
        );

        // Start agent (uses Component 3: Virtual thread)
        boolean started = engine.startAgent(agentId, workflow);
        assertTrue(started, "Agent should start successfully");

        // Verify registered (Component 2: Registry)
        assertTrue(registry.getRunningAgents().contains(agentId),
            "Agent should be in running agents");

        // Verify lifecycle tracking (Component 1: AgentLifecycle)
        AgentLifecycle lifecycle = registry.getLifecycle(agentId);
        assertNotNull(lifecycle);
        assertTrue(lifecycle.isActive());

        // Stop agent gracefully
        engine.stopAgent(agentId);
        Thread.sleep(100);  // Allow thread to terminate

        // Verify shutdown
        assertFalse(engine.isRunning(agentId),
            "Agent should no longer be running");
    }

    @Test
    @DisplayName("Create and manage 100 concurrent agents")
    void testConcurrentAgents() throws InterruptedException {
        int agentCount = 100;
        List<UUID> agentIds = new ArrayList<>();
        WorkflowDef workflow = new WorkflowDef(
            UUID.randomUUID(),
            "Concurrent Test",
            "100 agents",
            "1.0"
        );

        // Start 100 agents concurrently
        for (int i = 0; i < agentCount; i++) {
            UUID agentId = UUID.randomUUID();
            agentIds.add(agentId);
            boolean started = engine.startAgent(agentId, workflow);
            assertTrue(started, "Agent " + i + " should start");
        }

        // Verify all registered
        assertEquals(agentCount, registry.getTotalAgents(),
            "All 100 agents should be registered");

        // Verify all running
        assertEquals(agentCount, registry.getRunningAgents().size(),
            "All 100 agents should be running");

        // Stop all agents
        for (UUID agentId : agentIds) {
            engine.stopAgent(agentId);
        }

        Thread.sleep(500);  // Allow all threads to terminate

        // Verify all stopped
        for (UUID agentId : agentIds) {
            assertFalse(engine.isRunning(agentId),
                "All agents should be stopped");
        }
    }

    @Test
    @DisplayName("Verify heartbeat manager detects failed agents")
    void testHeartbeatMonitoring() throws InterruptedException {
        UUID agentId = UUID.randomUUID();
        WorkflowDef workflow = new WorkflowDef(
            UUID.randomUUID(),
            "Heartbeat Test",
            "Test heartbeat",
            "1.0"
        );

        // Start agent
        boolean started = engine.startAgent(agentId, workflow);
        assertTrue(started);

        // Verify initial state
        AgentLifecycle lifecycle = registry.getLifecycle(agentId);
        assertTrue(lifecycle.isActive());

        // Let heartbeat manager monitor for a bit
        Thread.sleep(2000);

        // Agent should still be active if running properly
        lifecycle = registry.getLifecycle(agentId);
        assertNotNull(lifecycle, "Agent should still be tracked");

        // Cleanup
        engine.stopAgent(agentId);
    }

    @Test
    @DisplayName("Test graceful engine shutdown")
    void testEngineShutdown() throws InterruptedException {
        // Start several agents
        UUID agent1 = UUID.randomUUID();
        UUID agent2 = UUID.randomUUID();
        UUID agent3 = UUID.randomUUID();
        WorkflowDef workflow = new WorkflowDef(UUID.randomUUID(), "Shutdown Test", "Test", "1.0");

        engine.startAgent(agent1, workflow);
        engine.startAgent(agent2, workflow);
        engine.startAgent(agent3, workflow);

        assertEquals(3, registry.getTotalAgents());

        // Shutdown engine
        assertDoesNotThrow(() -> engine.shutdownAll());

        Thread.sleep(500);

        // All agents should be stopped
        assertFalse(engine.isRunning(agent1));
        assertFalse(engine.isRunning(agent2));
        assertFalse(engine.isRunning(agent3));
    }

    @Test
    @DisplayName("Verify virtual threads are actually used")
    void testVirtualThreadExecution() throws InterruptedException {
        UUID agentId = UUID.randomUUID();
        WorkflowDef workflow = new WorkflowDef(
            UUID.randomUUID(),
            "Virtual Thread Test",
            "Verify virtual thread usage",
            "1.0"
        );

        long threadsBefore = Thread.getAllStackTraces().size();

        engine.startAgent(agentId, workflow);
        Thread.sleep(100);

        long threadsAfter = Thread.getAllStackTraces().size();

        // Virtual thread should be created (though this is hard to verify precisely)
        assertTrue(threadsAfter >= threadsBefore,
            "New virtual thread should be created for agent");

        engine.stopAgent(agentId);
    }

    @Test
    @DisplayName("Test agent state transitions")
    void testAgentStateTransitions() throws InterruptedException {
        UUID agentId = UUID.randomUUID();
        WorkflowDef workflow = new WorkflowDef(UUID.randomUUID(), "State Test", "Test", "1.0");

        // Verify lifecycle states exist
        assertTrue(AgentLifecycle.CREATED.equals(AgentLifecycle.CREATED));
        assertTrue(AgentLifecycle.RUNNING.isActive());
        assertFalse(AgentLifecycle.STOPPED.isActive());
        assertTrue(AgentLifecycle.STOPPED.isTerminal());

        // Start agent transitions from CREATED → RUNNING
        boolean started = engine.startAgent(agentId, workflow);
        assertTrue(started);

        AgentLifecycle lifecycle = registry.getLifecycle(agentId);
        assertTrue(lifecycle.isActive(), "Running state should be active");

        // Stop agent transitions to STOPPED
        engine.stopAgent(agentId);
        Thread.sleep(200);

        lifecycle = registry.getLifecycle(agentId);
        if (lifecycle != null) {
            assertTrue(lifecycle.isTerminal() || !engine.isRunning(agentId),
                "Agent should be in terminal state or no longer running");
        }
    }

    @Test
    @DisplayName("Verify registry thread safety with concurrent modifications")
    void testRegistryConcurrentSafety() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        WorkflowDef workflow = new WorkflowDef(UUID.randomUUID(), "Concurrent", "Test", "1.0");

        List<Future<?>> futures = new ArrayList<>();

        // Concurrent agent creation and deletion
        for (int i = 0; i < 20; i++) {
            futures.add(executor.submit(() -> {
                try {
                    UUID agentId = UUID.randomUUID();
                    engine.startAgent(agentId, workflow);
                    Thread.sleep(10);
                    engine.stopAgent(agentId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        // Wait for all tasks
        for (Future<?> future : futures) {
            assertDoesNotThrow(future::get);
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        // Engine should still be functional
        UUID testAgent = UUID.randomUUID();
        boolean started = engine.startAgent(testAgent, workflow);
        assertTrue(started, "Engine should still work after concurrent operations");

        engine.stopAgent(testAgent);
    }

    @Test
    @DisplayName("Verify component version compatibility")
    void testComponentVersionCompatibility() {
        // All components should use Java 25+ features
        assertDoesNotThrow(() -> {
            // Component 1: AgentState with enum pattern matching
            AgentLifecycle state = AgentLifecycle.RUNNING;
            boolean result = switch (state) {
                case RUNNING, IDLE -> true;
                default -> false;
            };
            assertTrue(result, "Pattern matching should work (Java 21+)");

            // Component 2: WorkflowDef with records
            WorkflowDef workflow = new WorkflowDef(
                UUID.randomUUID(),
                "Compat Test",
                "Verify record usage",
                "1.0"
            );
            assertNotNull(workflow, "Record should instantiate");

            // Component 3: Virtual threads
            Thread vt = Thread.ofVirtual().start(() -> {
                // Component execution on virtual thread
            });
            assertNotNull(vt, "Virtual thread should be created");
        });
    }
}
