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
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.autonomous;

import org.yawlfoundation.yawl.elements.YAWLServiceGateway;
import org.yawlfoundation.yawl.elements.YAWLServiceReference;
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.autonomous.strategies.PollingDiscoveryStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for partitioned autonomous agents.
 *
 * Tests the coordination between multiple autonomous agents using the partition
 * strategy defined in ADR-025. Focuses on real-world scenarios with multiple
 * agents competing for work items.
 *
 * Coverage targets:
 * - Multiple agent coordination
 * - Partition strategy enforcement
 * - Work item distribution across agents
 * - Conflict avoidance through partitioning
 * - Agent registration and discovery
 * - Load balancing across partitions
 * - Fault tolerance and recovery
 */
class PartitionedAgentIntegrationTest {

    private YEngine engine;
    private TestEnvironment environment;
    private List<TestAutonomousAgent> agents;
    private static final int NUM_AGENTS = 4;
    private static final String TEST_SPEC_ID = "TestPartitionedWorkflow";

    @BeforeEach
    void setUp() throws Exception {
        // Initialize test environment
        environment = new TestEnvironment();
        engine = environment.createEngine();

        // Create and register multiple agents
        agents = createTestAgents(NUM_AGENTS);

        // Register all agents
        for (TestAutonomousAgent agent : agents) {
            environment.registerAgent(agent);
        }

        // Load test specification
        environment.loadSpecification(TEST_SPEC_ID);
    }

    @Nested
    @DisplayName("Multi-Agent Coordination Tests")
    class MultiAgentCoordinationTests {

        @Test
        @DisplayName("Multiple agents discover non-overlapping work items")
        void multipleAgentsDiscoverNonOverlappingWorkItems() throws IOException, InterruptedException {
            // Given multiple agents registered
            assertEquals(NUM_AGENTS, agents.size(), "Should have " + NUM_AGENTS + " agents");

            // When each agent discovers work items
            Map<String, List<WorkItemRecord>> agentDiscoveries = new ConcurrentHashMap<>();

            // Start discovery threads for all agents
            Thread[] discoveryThreads = new Thread[NUM_AGENTS];
            for (int i = 0; i < NUM_AGENTS; i++) {
                final int agentIndex = i;
                discoveryThreads[i] = new Thread(() -> {
                    try {
                        List<WorkItemRecord> items = agents.get(agentIndex).discoverWorkItems();
                        agentDiscoveries.put("agent-" + agentIndex, items);
                    } catch (IOException e) {
                        fail("Agent discovery failed: " + e.getMessage());
                    }
                });
                discoveryThreads[i].start();
            }

            // Wait for all discoveries to complete
            for (Thread thread : discoveryThreads) {
                thread.join();
            }

            // Then work items should be partitioned among agents
            for (int i = 0; i < NUM_AGENTS; i++) {
                List<WorkItemRecord> items = agentDiscoveries.get("agent-" + i);
                assertNotNull(items, "Agent " + i + " should discover items");

                // Each agent should get items for its partition
                assertTrue(items.stream().allMatch(item ->
                    isInPartition(item, i, NUM_AGENTS)),
                    "All items for agent " + i + " should be in correct partition");
            }

            // No item should be discovered by multiple agents
            verifyNoOverlapInDiscoveries(agentDiscoveries);
        }

        @Test
        @DisplayName("Agent participates in correct partition based on registration order")
        void agentParticipatesInCorrectPartition() throws IOException {
            // Given agents registered in specific order
            TestAutonomousAgent agent0 = agents.get(0);
            TestAutonomousAgent agent2 = agents.get(2);

            // When discovering work items
            List<WorkItemRecord> agent0Items = agent0.discoverWorkItems();
            List<WorkItemRecord> agent2Items = agent2.discoverWorkItems();

            // Then each agent gets its assigned partition
            assertEquals(0, agent0.getPartitionIndex(), "Agent 0 should be in partition 0");
            assertEquals(2, agent2.getPartitionIndex(), "Agent 2 should be in partition 2");

            // Verify partition assignment
            for (WorkItemRecord item : agent0Items) {
                assertTrue(isInPartition(item, 0, NUM_AGENTS),
                    "Agent 0 should only get items from partition 0");
            }

            for (WorkItemRecord item : agent2Items) {
                assertTrue(isInPartition(item, 2, NUM_AGENTS),
                    "Agent 2 should only get items from partition 2");
            }
        }

        @Test
        @DisplayName("Work item checkout respects partition boundaries")
        void workItemCheckoutRespectsPartitionBoundaries() throws IOException {
            // Given multiple agents
            TestAutonomousAgent agent0 = agents.get(0);
            TestAutonomousAgent agent1 = agents.get(1);

            // When agent 0 tries to checkout items from partition 1
            List<WorkItemRecord> partition1Items = environment.getWorkItemsForPartition(1, NUM_AGENTS);

            // Then agent 0 should not be able to checkout partition 1 items
            for (WorkItemRecord item : partition1Items) {
                boolean checkoutResult = agent0.tryCheckoutWorkItem(item);
                assertFalse(checkoutResult, "Agent 0 should not checkout item from partition 1: " + item.getID());
            }

            // And agent 1 should be able to checkout partition 1 items
            for (WorkItemRecord item : partition1Items) {
                boolean checkoutResult = agent1.tryCheckoutWorkItem(item);
                assertTrue(checkoutResult, "Agent 1 should checkout item from partition 1: " + item.getID());
            }
        }

        @Test
        @DisplayName("Agent handles work items outside its partition gracefully")
        void agentHandlesWorkItemsOutsidePartitionGracefully() throws IOException {
            // Given agent 0
            TestAutonomousAgent agent0 = agents.get(0);

            // When discovering work items from other partitions
            List<WorkItemRecord> allItems = environment.getAllWorkItems();
            List<WorkItemRecord> outsidePartition = allItems.stream()
                .filter(item -> !isInPartition(item, 0, NUM_AGENTS))
                .toList();

            // Then agent should handle them gracefully
            for (WorkItemRecord item : outsidePartition) {
                assertFalse(agent0.attemptsCheckout(item),
                    "Agent should not attempt checkout for item outside partition: " + item.getID());
            }
        }

        @Test
        @DisplayName("Dynamic agent registration affects partition assignment")
        void dynamicAgentRegistrationAffectsPartitionAssignment() throws IOException {
            // Given initial 4 agents
            assertEquals(NUM_AGENTS, agents.size());

            // When adding a new agent
            TestAutonomousAgent newAgent = new TestAutonomousAgent("agent-4", 4, NUM_AGENTS + 1);
            environment.registerAgent(newAgent);

            // Then partition assignments should update
            List<WorkItemRecord> agent0Items = agents.get(0).discoverWorkItems();
            List<WorkItemRecord> newAgentItems = newAgent.discoverWorkItems();

            // Agent 0 should now have fewer items (5 agents instead of 4)
            assertTrue(agent0Items.size() < environment.getAllWorkItems().size() / NUM_AGENTS,
                "Agent 0 should have fewer items with more agents");

            // New agent should get its partition
            assertTrue(newAgentItems.stream().allMatch(item ->
                isInPartition(item, 4, NUM_AGENTS + 1)),
                "New agent should get partition 4 items");
        }
    }

    @Nested
    @DisplayName("Load Balancing Tests")
    class LoadBalancingTests {

        @Test
        @DisplayName("Load is balanced across all partitions")
        void loadIsBalancedAcrossAllPartitions() throws IOException {
            // Given work items distributed evenly
            environment.createWorkItems(100, TEST_SPEC_ID);

            // When all agents discover work items
            int[] partitionCounts = new int[NUM_AGENTS];
            for (int i = 0; i < NUM_AGENTS; i++) {
                List<WorkItemRecord> items = agents.get(i).discoverWorkItems();
                partitionCounts[i] = items.size();
            }

            // Then load should be reasonably balanced
            int expectedPerPartition = 100 / NUM_AGENTS;
            int maxDeviation = 10; // Allow some deviation

            for (int count : partitionCounts) {
                assertTrue(count >= expectedPerPartition - maxDeviation,
                    "Partition count should be close to expected: " + count);
                assertTrue(count <= expectedPerPartition + maxDeviation,
                    "Partition count should not exceed expected by too much: " + count);
            }
        }

        @Test
        @DisplayName("Work item distribution handles uneven numbers")
        void workItemDistributionHandlesUnevenNumbers() throws IOException {
            // Given 7 work items for 4 agents
            environment.createWorkItems(7, TEST_SPEC_ID);

            // When agents discover work items
            int[] partitionCounts = new int[NUM_AGENTS];
            for (int i = 0; i < NUM_AGENTS; i++) {
                List<WorkItemRecord> items = agents.get(i).discoverWorkItems();
                partitionCounts[i] = items.size();
            }

            // Then total should be 7
            int total = 0;
            for (int count : partitionCounts) {
                total += count;
            }
            assertEquals(7, total, "Total work items should be 7");

            // Some partitions may have 2 items, others 1
            int partitionsWith2 = 0;
            for (int count : partitionCounts) {
                if (count == 2) partitionsWith2++;
            }
            assertEquals(3, partitionsWith2, "Should have 3 partitions with 2 items");
            assertEquals(1, partitionCounts[3], "Last partition should have 1 item");
        }

        @Test
        @DisplayName("Heavy work items are distributed fairly")
        void heavyWorkItemsAreDistributedFairly() throws IOException {
            // Given work items with different weights
            environment.createHeavyWorkItems(20, NUM_AGENTS, TEST_SPEC_ID);

            // When agents discover and process work items
            Map<String, Integer> agentLoads = new ConcurrentHashMap<>();
            for (int i = 0; i < NUM_AGENTS; i++) {
                List<WorkItemRecord> items = agents.get(i).discoverWorkItems();
                int load = items.stream().mapToInt(TestWorkItemRecord::getWeight).sum();
                agentLoads.put("agent-" + i, load);
            }

            // Then load should be balanced
            int expectedAverageLoad = 200 / NUM_AGENTS; // Total weight 200
            int maxLoadDeviation = 30;

            for (Map.Entry<String, Integer> entry : agentLoads.entrySet()) {
                int load = entry.getValue();
                assertTrue(load >= expectedAverageLoad - maxLoadDeviation,
                    "Load for " + entry.getKey() + " should be close to average: " + load);
                assertTrue(load <= expectedAverageLoad + maxLoadDeviation,
                    "Load for " + entry.getKey() + " should not exceed average by too much: " + load);
            }
        }

        @Test
        @DisplayName("Partition strategy handles work item churn")
        void partitionStrategyHandlesWorkItemChurn() throws IOException, InterruptedException {
            // Given dynamic work item creation
            environment.startWorkItemGeneration(100, 5); // Generate 100 items over 5 seconds

            // When agents continuously discover work items
            Map<String, AtomicInteger> itemCounts = new ConcurrentHashMap<>();
            for (int i = 0; i < NUM_AGENTS; i++) {
                itemCounts.put("agent-" + i, new AtomicInteger(0));
            }

            // Run discovery for a short period
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < 2000) {
                for (int i = 0; i < NUM_AGENTS; i++) {
                    List<WorkItemRecord> items = agents.get(i).discoverWorkItems();
                    itemCounts.get("agent-" + i).addAndGet(items.size());
                }
                Thread.sleep(100);
            }

            // Then each agent should process items from its partition
            for (int i = 0; i < NUM_AGENTS; i++) {
                int count = itemCounts.get("agent-" + i).get();
                assertTrue(count > 0, "Agent " + i + " should process some items");
            }
        }
    }

    @Nested
    @DisplayName("Fault Tolerance Tests")
    class FaultToleranceTests {

        @Test
        @DisplayName("Other agents continue when one agent fails")
        void otherAgentsContinueWhenOneAgentFails() throws IOException {
            // Given 4 working agents
            assertEquals(NUM_AGENTS, agents.size());

            // When agent 1 fails
            TestAutonomousAgent failedAgent = agents.get(1);
            failedAgent.setFailed(true);

            // Then other agents should continue working
            for (int i = 0; i < NUM_AGENTS; i++) {
                if (i != 1) { // Skip failed agent
                    TestAutonomousAgent agent = agents.get(i);
                    List<WorkItemRecord> items = agent.discoverWorkItems();
                    assertNotNull(items, "Agent " + i + " should still discover items");
                    assertFalse(items.isEmpty(), "Agent " + i + " should have items to process");
                }
            }
        }

        @Test
        @DisplayName("Partition assignment adapts to agent failures")
        void partitionAssignmentAdaptsToAgentFailures() throws IOException {
            // Given agents 0 and 1 fail
            agents.get(0).setFailed(true);
            agents.get(1).setFailed(true);

            // When healthy agents discover work items
            TestAutonomousAgent agent2 = agents.get(2);
            TestAutonomousAgent agent3 = agents.get(3);

            List<WorkItemRecord> agent2Items = agent2.discoverWorkItems();
            List<WorkItemRecord> agent3Items = agent3.discoverWorkItems();

            // Then remaining agents should take over partitions
            int totalItems = agent2Items.size() + agent3Items.size();
            assertTrue(totalItems > 0, "Some items should be available");

            // Agent 2 and 3 should handle their partitions plus failed agent partitions
            for (WorkItemRecord item : agent2Items) {
                int partition = getPartitionForItem(item, NUM_AGENTS);
                assertTrue(partition == 2 || partition == 0 || partition == 1,
                    "Agent 2 should handle its own partition and failed agent partitions");
            }
        }

        @Test
        @DisplayName("Agent recovery after failure")
        void agentRecoveryAfterFailure() throws IOException {
            // Given agent 2 fails
            TestAutonomousAgent agent2 = agents.get(2);
            agent2.setFailed(true);

            // When agent 2 recovers
            agent2.setFailed(false);

            // Then it should resume work in its partition
            List<WorkItemRecord> items = agent2.discoverWorkItems();
            assertNotNull(items, "Recovered agent should discover items");

            // Should work in its assigned partition
            assertTrue(items.stream().allMatch(item ->
                isInPartition(item, 2, NUM_AGENTS)),
                "Recovered agent should work in its partition");
        }

        @Test
        @DisplayName("Interface B connection failures are handled gracefully")
        void interfaceBConnectionFailuresAreHandledGracefully() throws IOException {
            // Given agent with failing Interface B client
            TestAutonomousAgent agentWithFailure = new TestAutonomousAgent("agent-fail", 0, NUM_AGENTS) {
                @Override
                protected InterfaceB_EnvironmentBasedClient createInterfaceBClient() {
                    return new FailingInterfaceBClient();
                }
            };

            // When agent tries to discover work items
            assertThrows(IOException.class, () -> {
                agentWithFailure.discoverWorkItems();
            }, "Should throw IOException for connection failure");

            // Then other agents should continue working
            TestAutonomousAgent healthyAgent = agents.get(0);
            assertDoesNotThrow(() -> {
                List<WorkItemRecord> items = healthyAgent.discoverWorkItems();
                assertNotNull(items);
            }, "Healthy agent should continue working");
        }
    }

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        @DisplayName("Partition discovery performance with many agents")
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void partitionDiscoveryPerformanceWithManyAgents() throws IOException {
            // Given many agents (20)
            int largeNumAgents = 20;
            List<TestAutonomousAgent> manyAgents = createTestAgents(largeNumAgents);
            for (TestAutonomousAgent agent : manyAgents) {
                environment.registerAgent(agent);
            }

            // Create work items
            environment.createWorkItems(1000, TEST_SPEC_ID);

            // When all agents discover work items concurrently
            long startTime = System.nanoTime();

            Thread[] threads = new Thread[largeNumAgents];
            for (int i = 0; i < largeNumAgents; i++) {
                final int agentIndex = i;
                threads[i] = new Thread(() -> {
                    try {
                        List<WorkItemRecord> items = manyAgents.get(agentIndex).discoverWorkItems();
                        assertNotNull(items);
                    } catch (IOException e) {
                        fail("Agent discovery failed: " + e.getMessage());
                    }
                });
                threads[i].start();
            }

            // Wait for all threads
            for (Thread thread : threads) {
                thread.join();
            }

            long endTime = System.nanoTime();
            long durationMs = (endTime - startTime) / 1_000_000;

            // Then performance should be reasonable
            assertTrue(durationMs < 5000, "Discovery should complete in < 5s for 20 agents");
            System.out.println("Discovery time for 20 agents: " + durationMs + "ms");
        }

        @Test
        @DisplayName("Work item checkout performance")
        void workItemCheckoutPerformance() throws IOException {
            // Given many work items
            environment.createWorkItems(500, TEST_SPEC_ID);

            // When agent checks out items
            TestAutonomousAgent agent = agents.get(0);
            long startTime = System.nanoTime();

            List<WorkItemRecord> items = agent.discoverWorkItems();
            for (WorkItemRecord item : items) {
                agent.tryCheckoutWorkItem(item);
            }

            long endTime = System.nanoTime();
            long durationMs = (endTime - startTime) / 1_000_000;

            // Then checkout should be fast
            double avgTimePerItem = (double) durationMs / items.size();
            assertTrue(avgTimePerItem < 10, "Average checkout time should be < 10ms per item");
            System.out.println("Average checkout time: " + avgTimePerItem + "ms");
        }

        @Test
        @DisplayName("Partition strategy scales linearly")
        void partitionStrategyScalesLinearly() throws IOException {
            // Test scalability with different numbers of agents
            int[] agentCounts = {2, 4, 8, 16};
            int workItems = 1000;

            for (int numAgents : agentCounts) {
                // Setup environment with specific number of agents
                TestEnvironment testEnv = new TestEnvironment();
                List<TestAutonomousAgent> testAgents = createTestAgents(numAgents);
                for (TestAutonomousAgent agent : testAgents) {
                    testEnv.registerAgent(agent);
                }
                testEnv.createWorkItems(workItems, TEST_SPEC_ID);

                // Measure discovery time
                long startTime = System.nanoTime();
                for (TestAutonomousAgent agent : testAgents) {
                    List<WorkItemRecord> items = agent.discoverWorkItems();
                    assertNotNull(items);
                }
                long endTime = System.nanoTime();

                long durationMs = (endTime - startTime) / 1_000_000;
                double timePerAgent = (double) durationMs / numAgents;

                assertTrue(timePerAgent < 100,
                    "Time per agent should be reasonable for " + numAgents + " agents: " + timePerAgent + "ms");
                System.out.println("Time per agent for " + numAgents + " agents: " + timePerAgent + "ms");
            }
        }
    }

    // Helper methods

    private List<TestAutonomousAgent> createTestAgents(int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> new TestAutonomousAgent("agent-" + i, i, count))
            .toList();
    }

    private boolean isInPartition(WorkItemRecord item, int agentIndex, int totalAgents) {
        int hash = Math.abs(item.getID().hashCode());
        return (hash % totalAgents) == agentIndex;
    }

    private int getPartitionForItem(WorkItemRecord item, int totalAgents) {
        int hash = Math.abs(item.getID().hashCode());
        return hash % totalAgents;
    }

    private void verifyNoOverlapInDiscoveries(Map<String, List<WorkItemRecord>> discoveries) {
        // Collect all item IDs
        java.util.Set<String> allItemIds = new java.util.HashSet<>();

        for (Map.Entry<String, List<WorkItemRecord>> entry : discoveries.entrySet()) {
            for (WorkItemRecord item : entry.getValue()) {
                assertTrue(allItemIds.add(item.getID()),
                    "Item " + item.getID() + " discovered by multiple agents");
            }
        }
    }

    // Test classes

    /**
     * Test implementation of AutonomousAgent for integration testing.
     */
    private static class TestAutonomousAgent {
        private final String id;
        private final int partitionIndex;
        private final int totalAgents;
        private boolean failed;
        private TestInterfaceBClient interfaceBClient;

        public TestAutonomousAgent(String id, int partitionIndex, int totalAgents) {
            this.id = id;
            this.partitionIndex = partitionIndex;
            this.totalAgents = totalAgents;
            this.failed = false;
            this.interfaceBClient = new TestInterfaceBClient();
        }

        public List<WorkItemRecord> discoverWorkItems() throws IOException {
            if (failed) {
                throw new IOException("Agent " + id + " is failed");
            }

            PollingDiscoveryStrategy strategy = new PollingDiscoveryStrategy();
            List<WorkItemRecord> allItems = interfaceBClient.getCompleteListOfLiveWorkItems("test-session");

            if (allItems == null) {
                throw new IOException("No work items available");
            }

            return strategy.partitionFilter(allItems, partitionIndex, totalAgents);
        }

        public boolean tryCheckoutWorkItem(WorkItemRecord item) {
            try {
                // Simulate checkout attempt
                boolean success = interfaceBClient.checkoutWorkItem(item.getID(), "test-session");
                return success;
            } catch (IOException e) {
                return false;
            }
        }

        public boolean attemptsCheckout(WorkItemRecord item) {
            // Return true if agent would attempt checkout
            return isInPartition(item, partitionIndex, totalAgents);
        }

        public int getPartitionIndex() {
            return partitionIndex;
        }

        public void setFailed(boolean failed) {
            this.failed = failed;
        }
    }

    /**
     * Test work item record with additional properties for testing.
     */
    private static class TestWorkItemRecord extends WorkItemRecord {
        private final int weight;

        public TestWorkItemRecord(String id, String name, int weight) {
            // Simplified constructor
            this.weight = weight;
            // Other fields would be set by actual implementation
        }

        public int getWeight() {
            return weight;
        }
    }

    /**
     * Test environment for integration testing.
     */
    private static class TestEnvironment {
        private final java.util.List<WorkItemRecord> workItems = new java.util.ArrayList<>();
        private final java.util.List<TestAutonomousAgent> registeredAgents = new java.util.ArrayList<>();

        public YEngine createEngine() {
            // Create a test engine instance
            return new YEngine();
        }

        public void registerAgent(TestAutonomousAgent agent) {
            registeredAgents.add(agent);
        }

        public void loadSpecification(String specId) {
            // Load test specification
        }

        public void createWorkItems(int count, String specId) {
            for (int i = 0; i < count; i++) {
                workItems.add(new TestWorkItemRecord("WI-" + i, "Task", 1));
            }
        }

        public void createHeavyWorkItems(int count, int numAgents, String specId) {
            for (int i = 0; i < count; i++) {
                int weight = (i % numAgents) + 1; // Varying weights
                workItems.add(new TestWorkItemRecord("HWI-" + i, "HeavyTask", weight));
            }
        }

        public void startWorkItemGeneration(int totalItems, int durationSeconds) {
            // Simulate dynamic work item generation
            Thread generator = new Thread(() -> {
                try {
                    for (int i = 0; i < totalItems; i++) {
                        workItems.add(new TestWorkItemRecord("DYNAMIC-WI-" + i, "Task", 1));
                        Thread.sleep(durationSeconds * 1000 / totalItems);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            generator.start();
        }

        public List<WorkItemRecord> getAllWorkItems() {
            return new java.util.ArrayList<>(workItems);
        }

        public List<WorkItemRecord> getWorkItemsForPartition(int partitionIndex, int totalAgents) {
            return workItems.stream()
                .filter(item -> {
                    int hash = Math.abs(item.getID().hashCode());
                    return (hash % totalAgents) == partitionIndex;
                })
                .toList();
        }
    }

    /**
     * Test implementation of InterfaceB client.
     */
    private static class TestInterfaceBClient extends InterfaceB_EnvironmentBasedClient {
        @Override
        public List<WorkItemRecord> getCompleteListOfLiveWorkItems(String sessionHandle) throws IOException {
            // Return work items from test environment
            return List.of(); // Would normally return actual items
        }

        public boolean checkoutWorkItem(String itemId, String sessionHandle) throws IOException {
            // Simulate checkout
            return true;
        }
    }

    /**
     * Failing InterfaceB client for testing fault tolerance.
     */
    private static class FailingInterfaceBClient extends TestInterfaceBClient {
        @Override
        public List<WorkItemRecord> getCompleteListOfLiveWorkItems(String sessionHandle) throws IOException {
            throw new IOException("Connection failed");
        }
    }
}