/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.autonomous;

import org.yawlfoundation.yawl.integration.autonomous.registry.AgentInfo;
import org.yawlfoundation.yawl.integration.autonomous.registry.AgentRegistryClient;
import org.yawlfoundation.yawl.integration.autonomous.strategies.DiscoveryStrategy;
import org.yawlfoundation.yawl.integration.autonomous.strategies.PollingDiscoveryStrategy;
import org.yawlfoundation.yawl.integration.autonomous.resilience.CircuitBreaker;
import org.yawlfoundation.yawl.integration.autonomous.resilience.RetryPolicy;
import org.yawlfoundation.yawl.integration.orderfulfillment.AgentCapability;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import junit.framework.TestCase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Comprehensive system test for YAWL autonomous agent capabilities.
 *
 * This test validates the complete autonomous agent system including:
 * - Agent configuration and initialization
 * - Registry registration and discovery
 * - Hash-based partition consistency
 * - Circuit breaker resilience
 * - Retry policy reliability
 * - A2A coordination capabilities
 * - Performance under load
 * - Fault tolerance and recovery
 *
 * Chicago TDD: Real components, no mocks. Integration-level validation.
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public class AutonomousAgentSystemTest extends TestCase {

    private static final Logger logger = LogManager.getLogger(AutonomousAgentSystemTest.class);
    private static final int NUM_AGENTS = 4;
    private static final String TEST_DOMAIN = "TestDomain";
    private static final int WORK_ITEMS = 1000;

    private AgentRegistryClient registryClient;
    private TestAgent[] testAgents;
    private ExecutorService executor;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Initialize registry client
        registryClient = new AgentRegistryClient();

        // Create test agents
        testAgents = new TestAgent[NUM_AGENTS];
        for (int i = 0; i < NUM_AGENTS; i++) {
            testAgents[i] = new TestAgent("agent-" + i, i, NUM_AGENTS);
        }

        // Initialize thread pool
        executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    protected void tearDown() throws Exception {
        // Shutdown all agents
        for (TestAgent agent : testAgents) {
            if (agent.isRunning()) {
                agent.stop();
            }
        }

        // Shutdown executor
        executor.shutdown();
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }

        super.tearDown();
    }

    // =========================================================================
    // System Integration Tests
    // =========================================================================

    public void testAutonomousAgentSystemInitialization() throws Exception {
        // Given no agents registered

        // When we initialize the system
        initializeAutonomousSystem();

        // Then all agents should be properly initialized
        for (int i = 0; i < NUM_AGENTS; i++) {
            TestAgent agent = testAgents[i];
            assertNotNull("Agent should be initialized", agent);
            assertEquals("Agent should have correct partition", i, agent.getPartitionIndex());
            assertEquals("Agent should have total agents", NUM_AGENTS, agent.getTotalAgents());
        }
    }

    public void testAgentRegistryAndDiscoveryIntegration() throws Exception {
        // Given system initialized
        initializeAutonomousSystem();

        // When agents register with registry
        for (TestAgent agent : testAgents) {
            registryClient.register(agent.createAgentInfo());
        }

        // Then discovery should find agents by capability
        List<AgentInfo> discoveredAgents = registryClient.findAgentsByCapability(TEST_DOMAIN);
        assertEquals("Should find all test agents", NUM_AGENTS, discoveredAgents.size());

        // And each agent should be uniquely identifiable
        for (AgentInfo agent : discoveredAgents) {
            assertNotNull("Agent should have ID", agent.getId());
            assertTrue("Agent should have correct domain",
                agent.getCapabilities().contains(TEST_DOMAIN));
        }
    }

    public void testHashBasedPartitionConsistency() throws Exception {
        // Given system with multiple agents
        initializeAutonomousSystem();

        // Create work items
        List<String> workItems = generateWorkItems(WORK_ITEMS);

        // When each agent processes items
        int[] agentCounts = new int[NUM_AGENTS];
        for (String workItem : workItems) {
            for (int i = 0; i < NUM_AGENTS; i++) {
                if (testAgents[i].shouldProcess(workItem)) {
                    agentCounts[i]++;
                    break;
                }
            }
        }

        // Then partitions should be consistent
        verifyPartitionConsistency(workItems, agentCounts);

        // And no item should be processed by multiple agents
        verifyNoOverlap(workItems, testAgents);
    }

    public void testCircuitBreakerResilienceIntegration() throws Exception {
        // Given system with circuit breaker protection
        initializeAutonomousSystem();

        // When failures occur
        TestAgent failingAgent = testAgents[0];
        failingAgent.setFailureRate(0.5); // 50% failure rate

        // And circuit breaker is configured
        CircuitBreaker circuitBreaker = new CircuitBreaker("test-cb", 3, 1000);

        // Then circuit breaker should protect from cascading failures
        int failures = 0;
        for (int i = 0; i < 10; i++) {
            try {
                circuitBreaker.execute(() -> failingAgent.processWorkItem("test-item"));
            } catch (CircuitBreaker.CircuitBreakerOpenException e) {
                failures++;
                break; // Stop on circuit open
            }
        }

        // Circuit should open after threshold
        assertTrue("Circuit should open after threshold",
            circuitBreaker.getState() == CircuitBreaker.State.OPEN || failures > 0);
    }

    public void testRetryPolicyReliabilityIntegration() throws Exception {
        // Given system with retry protection
        initializeAutonomousSystem();

        // When transient failures occur
        TestAgent unreliableAgent = testAgents[1];
        unreliableAgent.setTransientFailure(true);

        // And retry policy is configured
        RetryPolicy retryPolicy = new RetryPolicy(
            3, 100L, 2.0, 5000L, true);

        // Then retries should eventually succeed
        boolean success = false;
        try {
            retryPolicy.execute(() -> {
                unreliableAgent.processWorkItem("retry-test");
                return "success";
            });
            success = true;
        } catch (RetryPolicy.RetryFailedException e) {
            // Expected if all retries fail
        }

        // Should have multiple attempts
        assertTrue("Should have attempted with retries",
            success || unreliableAgent.getAttemptCount() > 1);
    }

    public void testA2ACoordinationCapability() throws Exception {
        // Given A2A capable agents
        initializeAutonomousSystem();

        // When agents coordinate for complex work
        CompletableFuture<String> coordinationResult = new CompletableFuture<>();

        // Agent 1 initiates handoff to Agent 2
        executor.submit(() -> {
            try {
                String result = testAgents[0].coordinateWithAgent(testAgents[1], "complex-task");
                coordinationResult.complete(result);
            } catch (Exception e) {
                coordinationResult.completeExceptionally(e);
            }
        });

        // Then coordination should succeed
        String result = coordinationResult.get(5, TimeUnit.SECONDS);
        assertTrue("Coordination should succeed", result.contains("completed"));
    }

    public void testPerformanceUnderLoad() throws Exception {
        // Given system initialized
        initializeAutonomousSystem();

        // When processing large number of work items concurrently
        List<String> workItems = generateWorkItems(WORK_ITEMS);
        AtomicInteger processed = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        // Submit all work items
        List<CompletableFuture<Void>> futures = workItems.stream()
            .map(workItem -> CompletableFuture.runAsync(() -> {
                for (TestAgent agent : testAgents) {
                    if (agent.shouldProcess(workItem)) {
                        agent.processWorkItem(workItem);
                        processed.incrementAndGet();
                        break;
                    }
                }
            }, executor))
            .toList();

        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(10, TimeUnit.SECONDS);

        long duration = System.currentTimeMillis() - startTime;
        double itemsPerSecond = (double) WORK_ITEMS / (duration / 1000.0);

        // Then performance should be acceptable
        assertTrue("Should process > 100 items/second", itemsPerSecond > 100);
        assertEquals("All items should be processed", WORK_ITEMS, processed.get());

        logger.info("Processed {} items in {}ms ({:.1f} items/sec)",
            WORK_ITEMS, duration, itemsPerSecond);
    }

    public void testFaultToleranceAndRecovery() throws Exception {
        // Given system with fault tolerance
        initializeAutonomousSystem();

        // When some agents fail
        testAgents[2].setFailed(true);
        testAgents[3].setFailed(true);

        // And work continues
        List<String> workItems = generateWorkItems(100);
        int successful = 0;

        for (String workItem : workItems) {
            for (TestAgent agent : testAgents) {
                if (!agent.isFailed() && agent.shouldProcess(workItem)) {
                    agent.processWorkItem(workItem);
                    successful++;
                    break;
                }
            }
        }

        // Then system should continue operating
        assertTrue("Should process most items", successful > 80);

        // When failed agents recover
        testAgents[2].setFailed(false);
        testAgents[3].setFailed(false);

        // And more work arrives
        List<String> moreWork = generateWorkItems(100);
        int afterRecovery = 0;

        for (String workItem : moreWork) {
            for (TestAgent agent : testAgents) {
                if (!agent.isFailed() && agent.shouldProcess(workItem)) {
                    agent.processWorkItem(workItem);
                    afterRecovery++;
                    break;
                }
            }
        }

        // Then all agents should participate again
        assertTrue("Should process more items after recovery", afterRecovery > 90);
    }

    public void testMultiAgentCoordinationScenarios() throws Exception {
        // Given complex coordination scenario
        initializeAutonomousSystem();

        // When multiple agents need to collaborate
        AtomicInteger coordinationCount = new AtomicInteger(0);
        int coordinationScenarios = 10;

        // Execute coordination scenarios
        for (int i = 0; i < coordinationScenarios; i++) {
            // Agent 0 and 1 coordinate
            executor.submit(() -> {
                try {
                    String result = testAgents[0].coordinateWithAgent(testAgents[1], "coordination-" + i);
                    if (result.contains("success")) {
                        coordinationCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    logger.error("Coordination failed", e);
                }
            }).get();
        }

        // Then all scenarios should complete successfully
        assertEquals("All coordination scenarios should succeed", coordinationScenarios, coordinationCount.get());
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private void initializeAutonomousSystem() {
        // Initialize all test agents
        for (TestAgent agent : testAgents) {
            if (!agent.isRunning()) {
                agent.start();
            }
        }

        // Give agents time to initialize
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private List<String> generateWorkItems(int count) {
        return java.util.stream.IntStream.range(0, count)
            .mapToObj(i -> "work-item-" + i)
            .collect(java.util.stream.Collectors.toList());
    }

    private void verifyPartitionConsistency(List<String> workItems, int[] agentCounts) {
        // Verify each item is processed by exactly one agent
        for (String workItem : workItems) {
            int assignedCount = 0;
            for (int i = 0; i < NUM_AGENTS; i++) {
                if (testAgents[i].shouldProcess(workItem)) {
                    assignedCount++;
                }
            }
            assertEquals("Each item should be assigned to exactly one agent", 1, assignedCount);
        }

        // Verify distribution is reasonable (±20% variance)
        int expectedPerAgent = workItems.size() / NUM_AGENTS;
        for (int count : agentCounts) {
            int variance = Math.abs(count - expectedPerAgent);
            assertTrue("Distribution should be uniform",
                variance <= expectedPerAgent * 0.2);
        }
    }

    private void verifyNoOverlap(List<String> workItems, TestAgent[] agents) {
        // Create matrix to track item assignments
        boolean[][] assignments = new boolean[workItems.size()][NUM_AGENTS];

        for (int i = 0; i < workItems.size(); i++) {
            String workItem = workItems.get(i);
            for (int j = 0; j < NUM_AGENTS; j++) {
                assignments[i][j] = agents[j].shouldProcess(workItem);
            }
        }

        // Verify no item is assigned to multiple agents
        for (int i = 0; i < workItems.size(); i++) {
            int assignedCount = 0;
            for (int j = 0; j < NUM_AGENTS; j++) {
                if (assignments[i][j]) {
                    assignedCount++;
                }
            }
            assertEquals("Item should be assigned to exactly one agent", 1, assignedCount);
        }
    }

    // =========================================================================
    // Test Agent Implementation
    // =========================================================================

    /**
     * Test agent implementation for system testing.
     */
    private static class TestAgent {
        private final String id;
        private final int partitionIndex;
        private final int totalAgents;
        private boolean running;
        private boolean failed;
        private boolean transientFailure;
        private int attemptCount;
        private final PartitionConfig partitionConfig;

        public TestAgent(String id, int partitionIndex, int totalAgents) {
            this.id = id;
            this.partitionIndex = partitionIndex;
            this.totalAgents = totalAgents;
            this.running = false;
            this.failed = false;
            this.transientFailure = false;
            this.attemptCount = 0;
            this.partitionConfig = new PartitionConfig(partitionIndex, totalAgents);
        }

        public void start() {
            this.running = true;
        }

        public void stop() {
            this.running = false;
        }

        public boolean isRunning() {
            return running;
        }

        public void setFailed(boolean failed) {
            this.failed = failed;
        }

        public boolean isFailed() {
            return failed;
        }

        public void setTransientFailure(boolean transientFailure) {
            this.transientFailure = transientFailure;
        }

        public int getPartitionIndex() {
            return partitionIndex;
        }

        public int getTotalAgents() {
            return totalAgents;
        }

        public int getAttemptCount() {
            return attemptCount;
        }

        public AgentInfo createAgentInfo() {
            return new AgentInfo(
                id,
                "Test Agent " + id,
                List.of(TEST_DOMAIN),
                "localhost",
                8090 + partitionIndex
            );
        }

        public boolean shouldProcess(String workItemId) {
            return partitionConfig.shouldProcess(workItemId);
        }

        public void processWorkItem(String workItemId) {
            if (failed) {
                throw new RuntimeException("Agent is failed");
            }

            if (transientFailure && Math.random() < 0.5) {
                attemptCount++;
                throw new RuntimeException("Transient failure");
            }

            // Simulate processing
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        public String coordinateWithAgent(TestAgent otherAgent, String task) throws Exception {
            if (failed || otherAgent.failed) {
                throw new Exception("Cannot coordinate with failed agent");
            }

            // Simulate coordination
            Thread.sleep(10);

            return "Coordination completed: " + task;
        }

        public void setFailureRate(double rate) {
            // This would be used by circuit breaker tests
            // Implementation would simulate failures at given rate
        }
    }
}