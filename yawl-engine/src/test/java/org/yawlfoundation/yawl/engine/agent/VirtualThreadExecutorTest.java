package org.yawlfoundation.yawl.engine.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for virtual thread executor and agent lifecycle management.
 * Verifies that agents are created on virtual threads and operate correctly.
 */
class VirtualThreadExecutorTest {

    private YawlAgentEngine engine;
    private WorkflowDef simpleWorkflow;

    @BeforeEach
    void setUp() {
        // Create a simple test workflow
        Place startPlace = new Place("start", "Start Place");
        Place endPlace = new Place("end", "End Place");
        Transition transition = new Transition("t1", "Task 1", "task");

        simpleWorkflow = new WorkflowDef(
                UUID.randomUUID(),
                "TestWorkflow",
                List.of(startPlace, endPlace),
                List.of(transition),
                "start"
        );

        engine = new YawlAgentEngine();
        engine.start();
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testStartAgentCreatesNewThread() {
        UUID agentId = UUID.randomUUID();

        boolean started = engine.startAgent(agentId, simpleWorkflow);

        assertTrue(started, "Agent should be started successfully");
        assertTrue(engine.isRunning(agentId), "Agent should be running");

        AgentState agent = engine.getAgent(agentId);
        assertNotNull(agent, "Agent state should be found");
        assertEquals(agentId, agent.getAgentId(), "Agent ID should match");

        engine.stopAgent(agentId);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testAgentThreadIsVirtual() {
        UUID agentId = UUID.randomUUID();

        engine.startAgent(agentId, simpleWorkflow);

        Thread agentThread = engine.getRegistry().getThread(agentId);
        assertNotNull(agentThread, "Agent thread should exist");
        assertTrue(agentThread.isVirtual(), "Agent thread should be a virtual thread");

        engine.stopAgent(agentId);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testVirtualThreadCountIncreases() {
        int initialCount = engine.getActiveThreadCount();

        UUID agentId1 = UUID.randomUUID();
        engine.startAgent(agentId1, simpleWorkflow);

        int countAfterFirst = engine.getActiveThreadCount();
        assertEquals(initialCount + 1, countAfterFirst, "Thread count should increase by 1");

        UUID agentId2 = UUID.randomUUID();
        engine.startAgent(agentId2, simpleWorkflow);

        int countAfterSecond = engine.getActiveThreadCount();
        assertEquals(initialCount + 2, countAfterSecond, "Thread count should increase by 2");

        engine.stopAgent(agentId1);
        engine.stopAgent(agentId2);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testStopAgentTerminatesThread() {
        UUID agentId = UUID.randomUUID();

        engine.startAgent(agentId, simpleWorkflow);
        assertTrue(engine.isRunning(agentId), "Agent should be running");

        Thread agentThread = engine.getRegistry().getThread(agentId);
        assertTrue(agentThread.isAlive(), "Thread should be alive");

        boolean stopped = engine.stopAgent(agentId);

        assertTrue(stopped, "Agent should be stopped successfully");
        assertFalse(engine.isRunning(agentId), "Agent should not be running after stop");

        // Give thread time to fully terminate
        try {
            agentThread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertFalse(agentThread.isAlive(), "Thread should be terminated");
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testConcurrentAgentStartup() {
        int agentCount = 100;
        List<UUID> agentIds = new ArrayList<>();

        for (int i = 0; i < agentCount; i++) {
            UUID agentId = UUID.randomUUID();
            agentIds.add(agentId);
            engine.startAgent(agentId, simpleWorkflow);
        }

        assertEquals(agentCount, engine.getAgentCount(), "All agents should be registered");
        assertEquals(agentCount, engine.getActiveThreadCount(), "All threads should be active");

        // Verify all threads are virtual
        assertTrue(engine.areAllThreadsVirtual(), "All threads should be virtual");

        // Verify all agents are running
        for (UUID agentId : agentIds) {
            assertTrue(engine.isRunning(agentId), "Agent " + agentId + " should be running");
        }

        // Stop all agents
        for (UUID agentId : agentIds) {
            engine.stopAgent(agentId);
        }

        // Small delay for cleanup
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        assertEquals(0, engine.getActiveThreadCount(), "All threads should be terminated");
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testAgentLifecycleTransitions() {
        UUID agentId = UUID.randomUUID();

        // Initial state: not registered
        assertFalse(engine.isRunning(agentId), "Agent should not be running initially");

        // Start agent: CREATED -> RUNNING
        engine.startAgent(agentId, simpleWorkflow);
        AgentLifecycle state = engine.getAgentState(agentId);
        assertTrue(state == AgentLifecycle.RUNNING || state == AgentLifecycle.IDLE,
                "Agent should be in RUNNING or IDLE state");

        // Stop agent: -> STOPPING -> STOPPED
        engine.stopAgent(agentId);
        state = engine.getAgentState(agentId);
        assertEquals(AgentLifecycle.STOPPED, state, "Agent should be STOPPED");
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testEngineShutdown() throws InterruptedException {
        UUID agentId1 = UUID.randomUUID();
        UUID agentId2 = UUID.randomUUID();

        engine.startAgent(agentId1, simpleWorkflow);
        engine.startAgent(agentId2, simpleWorkflow);

        assertEquals(2, engine.getAgentCount(), "Both agents should be registered");

        engine.stop();

        assertFalse(engine.isEngineRunning(), "Engine should be stopped");
        assertEquals(0, engine.getAgentCount(), "All agents should be unregistered");
        assertEquals(0, engine.getActiveThreadCount(), "All threads should be terminated");
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testAgentRegistryTracking() {
        UUID agentId = UUID.randomUUID();

        engine.startAgent(agentId, simpleWorkflow);

        AgentState agent = engine.getRegistry().getAgent(agentId);
        assertNotNull(agent, "Agent should be found in registry");

        AgentLifecycle lifecycle = engine.getRegistry().getLifecycle(agentId);
        assertTrue(lifecycle.isActive(), "Agent should be in an active state");

        Thread thread = engine.getRegistry().getThread(agentId);
        assertNotNull(thread, "Agent thread should be found in registry");

        engine.stopAgent(agentId);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testMultipleWorkflowSupport() {
        // Create a second workflow
        Place place1 = new Place("p1", "Place 1");
        Place place2 = new Place("p2", "Place 2");
        Transition trans = new Transition("t2", "Task 2", "task");

        WorkflowDef secondWorkflow = new WorkflowDef(
                UUID.randomUUID(),
                "SecondWorkflow",
                List.of(place1, place2),
                List.of(trans),
                "p1"
        );

        UUID agentId1 = UUID.randomUUID();
        UUID agentId2 = UUID.randomUUID();

        engine.startAgent(agentId1, simpleWorkflow);
        engine.startAgent(agentId2, secondWorkflow);

        AgentState agent1 = engine.getAgent(agentId1);
        AgentState agent2 = engine.getAgent(agentId2);

        assertNotNull(agent1, "Agent 1 should exist");
        assertNotNull(agent2, "Agent 2 should exist");
        assertNotEquals(agent1.getWorkflow().getWorkflowId(),
                agent2.getWorkflow().getWorkflowId(),
                "Agents should have different workflows");

        engine.stopAgent(agentId1);
        engine.stopAgent(agentId2);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testHeartbeatMonitoring() {
        UUID agentId = UUID.randomUUID();

        engine.startAgent(agentId, simpleWorkflow);

        AgentState agent = engine.getAgent(agentId);
        assertTrue(agent.isHealthy(), "Agent should be healthy after startup");

        long remaining = agent.getHeartbeatRemaining();
        assertTrue(remaining > 0, "Heartbeat TTL should be positive");

        engine.stopAgent(agentId);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testAgentCountByLifecycle() {
        UUID agentId1 = UUID.randomUUID();
        UUID agentId2 = UUID.randomUUID();

        engine.startAgent(agentId1, simpleWorkflow);
        engine.startAgent(agentId2, simpleWorkflow);

        long activeCount = engine.getAgentCountByState(AgentLifecycle.IDLE) +
                          engine.getAgentCountByState(AgentLifecycle.RUNNING);

        assertEquals(2, activeCount, "Both agents should be in active states");

        engine.stopAgent(agentId1);

        long stoppedCount = engine.getAgentCountByState(AgentLifecycle.STOPPED);
        assertEquals(1, stoppedCount, "One agent should be stopped");

        engine.stopAgent(agentId2);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testEngineNotRunningException() {
        YawlAgentEngine stoppedEngine = new YawlAgentEngine();

        UUID agentId = UUID.randomUUID();

        assertThrows(IllegalStateException.class,
                () -> stoppedEngine.startAgent(agentId, simpleWorkflow),
                "Should throw IllegalStateException when engine is not running");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testDuplicateAgentRegistration() {
        UUID agentId = UUID.randomUUID();

        engine.startAgent(agentId, simpleWorkflow);

        assertThrows(IllegalArgumentException.class,
                () -> engine.startAgent(agentId, simpleWorkflow),
                "Should throw IllegalArgumentException for duplicate agent registration");

        engine.stopAgent(agentId);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testVirtualThreadNaming() {
        UUID agentId = UUID.randomUUID();

        engine.startAgent(agentId, simpleWorkflow);

        Thread agentThread = engine.getRegistry().getThread(agentId);
        String threadName = agentThread.getName();

        assertTrue(threadName.startsWith(VirtualThreadConfig.AGENT_THREAD_NAME_PREFIX),
                "Thread name should start with AGENT_THREAD_NAME_PREFIX");
        assertTrue(threadName.contains(agentId.toString()),
                "Thread name should contain agent ID");

        engine.stopAgent(agentId);
    }
}
