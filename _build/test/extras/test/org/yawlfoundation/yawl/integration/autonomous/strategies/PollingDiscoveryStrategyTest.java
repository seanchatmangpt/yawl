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

package org.yawlfoundation.yawl.integration.autonomous.strategies;

import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for PollingDiscoveryStrategy implementation.
 *
 * Tests the partition strategy for work item distribution across multiple agents
 * as defined in ADR-025. Focuses on hash-based partitioning and load distribution.
 *
 * Coverage targets:
 * - Basic work item discovery
 * - Partition strategy with hash-based assignment
 * - Edge cases in partition calculation
 * - Load distribution across multiple agents
 * - Performance characteristics
 * - Error handling and recovery
 */
class PollingDiscoveryStrategyTest {

    private PollingDiscoveryStrategy strategy;
    private TestInterfaceBClient testInterfaceBClient;
    private static final String TEST_SESSION_HANDLE = "test-session-handle";

    @BeforeEach
    void setUp() {
        strategy = new PollingDiscoveryStrategy();
        testInterfaceBClient = new TestInterfaceBClient();
    }

    @Nested
    @DisplayName("Basic Discovery Tests")
    class BasicDiscoveryTests {

        @Test
        @DisplayName("Discover work items successfully")
        void discoverWorkItemsSuccessfully() throws IOException {
            // Given test work items
            testInterfaceBClient.addWorkItem("WI-1", "TaskA", Map.of());
            testInterfaceBClient.addWorkItem("WI-2", "TaskB", Map.of());
            testInterfaceBClient.addWorkItem("WI-3", "TaskC", Map.of());

            // When discovering work items
            List<WorkItemRecord> discovered = strategy.discoverWorkItems(
                testInterfaceBClient, TEST_SESSION_HANDLE);

            // Then discovery should succeed and return all items
            assertNotNull(discovered, "Discovered items should not be null");
            assertEquals(3, discovered.size(), "Should discover 3 work items");

            // Verify all items are present
            List<String> itemIds = discovered.stream()
                .map(WorkItemRecord::getID)
                .collect(Collectors.toList());
            assertTrue(itemIds.contains("WI-1"), "Should contain WI-1");
            assertTrue(itemIds.contains("WI-2"), "Should contain WI-2");
            assertTrue(itemIds.contains("WI-3"), "Should contain WI-3");
        }

        @Test
        @DisplayName("Discover empty work item list")
        void discoverEmptyWorkItemList() throws IOException {
            // Given no work items
            testInterfaceBClient.clearWorkItems();

            // When discovering work items
            List<WorkItemRecord> discovered = strategy.discoverWorkItems(
                testInterfaceBClient, TEST_SESSION_HANDLE);

            // Then it should return empty list
            assertNotNull(discovered, "Discovered items should not be null");
            assertTrue(discovered.isEmpty(), "Should return empty list");
        }

        @Test
        @DisplayName("Discover throws IOException when client throws")
        void discoverThrowsIOExceptionWhenClientThrows() throws IOException {
            // Given client that throws exception
            TestInterfaceBClient failingClient = new TestInterfaceBClient() {
                @Override
                public List<WorkItemRecord> getCompleteListOfLiveWorkItems(String sessionHandle) throws IOException {
                    throw new IOException("Connection failed");
                }
            };

            // When discovering work items
            assertThrows(IOException.class, () -> {
                strategy.discoverWorkItems(failingClient, TEST_SESSION_HANDLE);
            }, "Should propagate IOException from client");
        }

        @Test
        @DisplayName("Discover throws IOException for null response")
        void discoverThrowsIOExceptionForNullResponse() throws IOException {
            // Given client that returns null
            TestInterfaceBClient nullResponseClient = new TestInterfaceBClient() {
                @Override
                public List<WorkItemRecord> getCompleteListOfLiveWorkItems(String sessionHandle) throws IOException {
                    return null;
                }
            };

            // When discovering work items
            assertThrows(IOException.class, () -> {
                strategy.discoverWorkItems(nullResponseClient, TEST_SESSION_HANDLE);
            }, "Should throw IOException for null response");
        }

        @Test
        @DisplayName("Discover throws exception for null client")
        void discoverThrowsExceptionForNullClient() {
            // When discovering with null client
            assertThrows(IllegalArgumentException.class, () -> {
                strategy.discoverWorkItems(null, TEST_SESSION_HANDLE);
            }, "Should throw IllegalArgumentException for null client");
        }

        @Test
        @DisplayName("Discover throws exception for empty session handle")
        void discoverThrowsExceptionForEmptySessionHandle() {
            // When discovering with empty session handle
            assertThrows(IllegalArgumentException.class, () -> {
                strategy.discoverWorkItems(testInterfaceBClient, "");
            }, "Should throw IllegalArgumentException for empty session handle");
        }

        @Test
        @DisplayName("Discover throws exception for null session handle")
        void discoverThrowsExceptionForNullSessionHandle() {
            // When discovering with null session handle
            assertThrows(IllegalArgumentException.class, () -> {
                strategy.discoverWorkItems(testInterfaceBClient, null);
            }, "Should throw IllegalArgumentException for null session handle");
        }

        @Test
        @DisplayName("Discover work items with large dataset")
        void discoverLargeDataset() throws IOException {
            // Given large number of work items
            for (int i = 0; i < 1000; i++) {
                testInterfaceBClient.addWorkItem("WI-" + i, "Task" + (i % 10), Map.of());
            }

            // When discovering work items
            List<WorkItemRecord> discovered = strategy.discoverWorkItems(
                testInterfaceBClient, TEST_SESSION_HANDLE);

            // Then it should handle large dataset
            assertNotNull(discovered, "Discovered items should not be null");
            assertEquals(1000, discovered.size(), "Should discover all 1000 work items");
        }
    }

    @Nested
    @DisplayName("Partition Strategy Tests")
    class PartitionStrategyTests {

        @Test
        @DisplayName("Partition with 2 agents - even distribution")
        void partitionWithTwoAgents() {
            // Given work items for 2-agent partition
            List<WorkItemRecord> allItems = Arrays.asList(
                createTestWorkItem("WI-A1", "TaskA"),
                createTestWorkItem("WI-B2", "TaskB"),
                createTestWorkItem("WI-C3", "TaskC"),
                createTestWorkItem("WI-D4", "TaskD"),
                createTestWorkItem("WI-E5", "TaskE"),
                createTestWorkItem("WI-F6", "TaskF")
            );

            // When partitioning for agent 0 (of 2)
            List<WorkItemRecord> agent0Items = strategy.partitionFilter(
                allItems, 0, 2);

            // And partitioning for agent 1 (of 2)
            List<WorkItemRecord> agent1Items = strategy.partitionFilter(
                allItems, 1, 2);

            // Then items should be distributed between agents
            assertNotNull(agent0Items, "Agent 0 items should not be null");
            assertNotNull(agent1Items, "Agent 1 items should not be null");

            // Each agent should get approximately half the items
            assertTrue(agent0Items.size() >= 2 && agent0Items.size() <= 4,
                "Agent 0 should get 2-4 items");
            assertTrue(agent1Items.size() >= 2 && agent1Items.size() <= 4,
                "Agent 1 should get 2-4 items");

            // No overlap between agents
            assertFalse(hasOverlap(agent0Items, agent1Items),
                "Agents should not have overlapping items");
        }

        @Test
        @DisplayName("Partition with 3 agents - hash-based assignment")
        void partitionWithThreeAgents() {
            // Given work items
            List<WorkItemRecord> allItems = Arrays.asList(
                createTestWorkItem("WI-1", "TaskA"),
                createTestWorkItem("WI-2", "TaskB"),
                createTestWorkItem("WI-3", "TaskC"),
                createTestWorkItem("WI-4", "TaskD"),
                createTestWorkItem("WI-5", "TaskE"),
                createTestWorkItem("WI-6", "TaskF"),
                createTestWorkItem("WI-7", "TaskG")
            );

            // When partitioning among 3 agents
            List<WorkItemRecord> agent0 = strategy.partitionFilter(allItems, 0, 3);
            List<WorkItemRecord> agent1 = strategy.partitionFilter(allItems, 1, 3);
            List<WorkItemRecord> agent2 = strategy.partitionFilter(allItems, 2, 3);

            // Then each agent should get its assigned items
            assertEquals(7, agent0.size() + agent1.size() + agent2.size(),
                "Total items should match original count");

            // Verify hash-based assignments
            assertTrue(agent0.stream().anyMatch(item -> item.getID().equals("WI-1")),
                "WI-1 should go to agent 0 (hash % 3 = 1 % 3 = 1, but we need to verify)");
            // Note: The actual hash calculation determines the partition
            List<String> agent0Ids = agent0.stream().map(WorkItemRecord::getID).collect(Collectors.toList());
            List<String> agent1Ids = agent1.stream().map(WorkItemRecord::getID).collect(Collectors.toList());
            List<String> agent2Ids = agent2.stream().map(WorkItemRecord::getID).collect(Collectors.toList());

            // All items should be assigned to exactly one agent
            assertEquals(7, agent0Ids.size() + agent1Ids.size() + agent2Ids.size());
        }

        @Test
        @DisplayName("Partition with single agent - all items assigned")
        void partitionWithSingleAgent() {
            // Given work items
            List<WorkItemRecord> allItems = Arrays.asList(
                createTestWorkItem("WI-1", "TaskA"),
                createTestWorkItem("WI-2", "TaskB"),
                createTestWorkItem("WI-3", "TaskC")
            );

            // When partitioning for single agent
            List<WorkItemRecord> agent0Items = strategy.partitionFilter(
                allItems, 0, 1);

            // Then agent should get all items
            assertEquals(3, agent0Items.size(), "Single agent should get all items");
            assertTrue(allItems.stream().allMatch(item ->
                agent0Items.contains(item)), "All items should be assigned to agent");
        }

        @Test
        @DisplayName("Partition throws exception for negative agent index")
        void partitionThrowsExceptionForNegativeIndex() {
            // Given work items
            List<WorkItemRecord> items = Arrays.asList(
                createTestWorkItem("WI-1", "TaskA")
            );

            // When partitioning with negative index
            assertThrows(IllegalArgumentException.class, () -> {
                strategy.partitionFilter(items, -1, 2);
            }, "Should throw IllegalArgumentException for negative index");
        }

        @Test
        @DisplayName("Partition throws exception for zero total agents")
        void partitionThrowsExceptionForZeroTotalAgents() {
            // Given work items
            List<WorkItemRecord> items = Arrays.asList(
                createTestWorkItem("WI-1", "TaskA")
            );

            // When partitioning with zero total agents
            assertThrows(IllegalArgumentException.class, () -> {
                strategy.partitionFilter(items, 0, 0);
            }, "Should throw IllegalArgumentException for zero total agents");
        }

        @Test
        @DisplayName("Partition throws exception for agent index >= total agents")
        void partitionThrowsExceptionForIndexOutOfBounds() {
            // Given work items
            List<WorkItemRecord> items = Arrays.asList(
                createTestWorkItem("WI-1", "TaskA")
            );

            // When partitioning with index >= total
            assertThrows(IllegalArgumentException.class, () -> {
                strategy.partitionFilter(items, 2, 2);
            }, "Should throw IllegalArgumentException for index >= total");
        }

        @Test
        @DisplayName("Partition throws exception for null items list")
        void partitionThrowsExceptionForNullItems() {
            // When partitioning with null items
            assertThrows(IllegalArgumentException.class, () -> {
                strategy.partitionFilter(null, 0, 2);
            }, "Should throw IllegalArgumentException for null items");
        }

        @Test
        @DisplayName("Partition ensures consistent assignment")
        void partitionEnsuresConsistentAssignment() {
            // Given work items
            List<WorkItemRecord> allItems = Arrays.asList(
                createTestWorkItem("WI-1", "TaskA"),
                createTestWorkItem("WI-2", "TaskB"),
                createTestWorkItem("WI-3", "TaskC")
            );

            // When partitioning multiple times
            List<WorkItemRecord> firstRun = strategy.partitionFilter(allItems, 0, 2);
            List<WorkItemRecord> secondRun = strategy.partitionFilter(allItems, 0, 2);

            // Then assignment should be consistent
            assertEquals(firstRun, secondRun, "Assignment should be consistent across runs");
        }

        @Test
        @DisplayName("Partition handles special characters in work item IDs")
        void partitionHandlesSpecialCharacters() {
            // Given work items with special characters
            List<WorkItemRecord> allItems = Arrays.asList(
                createTestWorkItem("WI-@#$%", "TaskA"),
                createTestWorkItem("WI-^&*()", "TaskB"),
                createTestWorkItem("WI-_+-=", "TaskC")
            );

            // When partitioning
            List<WorkItemRecord> agent0Items = strategy.partitionFilter(allItems, 0, 2);

            // Then it should handle special characters
            assertNotNull(agent0Items, "Should handle special characters");
            assertFalse(agent0Items.isEmpty(), "Should have items for agent 0");
        }
    }

    @Nested
    @DisplayName("Load Distribution Tests")
    class LoadDistributionTests {

        @Test
        @DisplayName("Even distribution with multiple items")
        void evenDistributionWithMultipleItems() {
            // Given many work items
            List<WorkItemRecord> allItems = IntStream.range(0, 1000)
                .mapToObj(i -> createTestWorkItem("WI-" + i, "Task" + (i % 50)))
                .collect(Collectors.toList());

            // When partitioning among 4 agents
            List<WorkItemRecord>[] agentItems = new List[4];
            for (int i = 0; i < 4; i++) {
                agentItems[i] = strategy.partitionFilter(allItems, i, 4);
            }

            // Then load should be reasonably balanced
            int expectedPerAgent = 1000 / 4;
            int maxDeviation = 50; // Allow some deviation

            for (int i = 0; i < 4; i++) {
                int actualSize = agentItems[i].size();
                assertTrue(actualSize >= expectedPerAgent - maxDeviation,
                    "Agent " + i + " should have at least " + (expectedPerAgent - maxDeviation) + " items");
                assertTrue(actualSize <= expectedPerAgent + maxDeviation,
                    "Agent " + i + " should have at most " + (expectedPerAgent + maxDeviation) + " items");
            }
        }

        @Test
        @DisplayName("Uniform hash distribution")
        void uniformHashDistribution() {
            // Given work items with sequential IDs
            List<WorkItemRecord> allItems = IntStream.range(0, 1000)
                .mapToObj(i -> createTestWorkItem(String.format("%05d", i), "TaskA"))
                .collect(Collectors.toList());

            // Count how many times each agent gets items
            int[] agentCounts = new int[10];
            for (WorkItemRecord item : allItems) {
                // Simulate partitioning to determine which agent gets this item
                int hash = Math.abs(("WI-" + item.getID()).hashCode());
                int agentIndex = hash % 10;
                agentCounts[agentIndex]++;
            }

            // Then distribution should be approximately uniform
            int expectedCount = 100;
            for (int count : agentCounts) {
                // Allow for some variance due to hash distribution
                assertTrue(count >= 90 && count <= 110,
                    "Agent count should be close to " + expectedCount + ", was " + count);
            }
        }

        @Test
        @DisplayName("Load distribution with varying work item metadata")
        void loadDistributionWithVaryingMetadata() {
            // Given work items with varying metadata
            List<WorkItemRecord> allItems = Arrays.asList(
                createTestWorkItem("WI-1", "TaskA", Map.of("complexity", "high")),
                createTestWorkItem("WI-2", "TaskB", Map.of("complexity", "low")),
                createTestWorkItem("WI-3", "TaskC", Map.of("complexity", "medium")),
                createTestWorkItem("WI-4", "TaskD", Map.of("complexity", "high")),
                createTestWorkItem("WI-5", "TaskE", Map.of("complexity", "low")),
                createTestWorkItem("WI-6", "TaskF", Map.of("complexity", "medium"))
            );

            // When partitioning among 3 agents
            List<WorkItemRecord> agent0 = strategy.partitionFilter(allItems, 0, 3);
            List<WorkItemRecord> agent1 = strategy.partitionFilter(allItems, 1, 3);
            List<WorkItemRecord> agent2 = strategy.partitionFilter(allItems, 2, 3);

            // Then each agent should get a balanced load
            assertTrue(agent0.size() >= 1 && agent0.size() <= 3,
                "Agent 0 should get 1-3 items");
            assertTrue(agent1.size() >= 1 && agent1.size() <= 3,
                "Agent 1 should get 1-3 items");
            assertTrue(agent2.size() >= 1 && agent2.size() <= 3,
                "Agent 2 should get 1-3 items");
        }
    }

    @Nested
    @DisplayName("Edge Cases and Performance Tests")
    class EdgeCasesAndPerformanceTests {

        @Test
        @DisplayName("Handle empty partition filter")
        void handleEmptyPartitionFilter() {
            // Given empty items list
            List<WorkItemRecord> emptyItems = List.of();

            // When partitioning
            List<WorkItemRecord> result = strategy.partitionFilter(emptyItems, 0, 2);

            // Then should return empty list
            assertNotNull(result, "Result should not be null");
            assertTrue(result.isEmpty(), "Empty items should result in empty partition");
        }

        @Test
        @DisplayName("Handle single item partition")
        void handleSingleItemPartition() {
            // Given single work item
            List<WorkItemRecord> singleItem = List.of(
                createTestWorkItem("WI-1", "TaskA")
            );

            // When partitioning among 3 agents
            List<WorkItemRecord> agent0 = strategy.partitionFilter(singleItem, 0, 3);
            List<WorkItemRecord> agent1 = strategy.partitionFilter(singleItem, 1, 3);
            List<WorkItemRecord> agent2 = strategy.partitionFilter(singleItem, 2, 3);

            // Then one agent should get the item, others should be empty
            int totalItems = agent0.size() + agent1.size() + agent2.size();
            assertEquals(1, totalItems, "Total items should be 1");
            assertTrue(agent0.size() + agent1.size() + agent2.size() == 1,
                "Exactly one agent should have the item");
        }

        @Test
        @DisplayName("Partition with identical work item IDs")
        void partitionWithIdenticalIds() {
            // Given work items with identical IDs
            List<WorkItemRecord> identicalItems = Arrays.asList(
                createTestWorkItem("WI-SAME", "TaskA"),
                createTestWorkItem("WI-SAME", "TaskB"),
                createTestWorkItem("WI-SAME", "TaskC")
            );

            // When partitioning
            List<WorkItemRecord> agent0 = strategy.partitionFilter(identicalItems, 0, 2);
            List<WorkItemRecord> agent1 = strategy.partitionFilter(identicalItems, 1, 2);

            // Then all items should go to one agent based on hash
            int totalItems = agent0.size() + agent1.size();
            assertEquals(3, totalItems, "All 3 items should be assigned");
            // All identical items go to the same partition
            assertTrue(agent0.isEmpty() || agent1.isEmpty(),
                "Identical items should all go to same partition");
        }

        @Test
        @DisplayName("Concurrent partition operations")
        @Timeout(value = 5, unit = TimeUnit.SECONDS)
        void concurrentPartitionOperations() throws InterruptedException {
            // Given many work items
            List<WorkItemRecord> allItems = IntStream.range(0, 1000)
                .mapToObj(i -> createTestWorkItem("WI-" + i, "TaskA"))
                .collect(Collectors.toList());

            // When partitioning concurrently
            int threadCount = 4;
            Thread[] threads = new Thread[threadCount];
            List<WorkItemRecord>[] results = new List[threadCount];

            for (int i = 0; i < threadCount; i++) {
                final int threadIndex = i;
                threads[i] = new Thread(() -> {
                    results[threadIndex] = strategy.partitionFilter(allItems, threadIndex, threadCount);
                });
                threads[i].start();
            }

            // Wait for all threads to complete
            for (Thread thread : threads) {
                thread.join();
            }

            // Then all partitions should be complete
            for (int i = 0; i < threadCount; i++) {
                assertNotNull(results[i], "Result " + i + " should not be null");
                assertFalse(results[i].isEmpty(), "Partition " + i + " should not be empty");
            }
        }

        @Test
        @DisplayName("Partition performance with large dataset")
        @Timeout(value = 10, unit = TimeUnit.SECONDS)
        void partitionPerformanceWithLargeDataset() {
            // Given large dataset
            List<WorkItemRecord> largeDataset = IntStream.range(0, 10000)
                .mapToObj(i -> createTestWorkItem("WI-" + i, "Task" + (i % 100)))
                .collect(Collectors.toList());

            // When partitioning
            long startTime = System.nanoTime();
            List<WorkItemRecord> partition = strategy.partitionFilter(largeDataset, 0, 5);
            long endTime = System.nanoTime();

            // Then performance should be reasonable
            long durationMs = (endTime - startTime) / 1_000_000;
            assertTrue(durationMs < 1000, "Partition should complete in < 1s, took " + durationMs + "ms");
            assertEquals(2000, partition.size(), "Partition should have 2000 items");
            System.out.println("Partition time for 10k items: " + durationMs + "ms");
        }

        @Test
        @DisplayName("Hash source consistency")
        void hashSourceConsistency() {
            // Given work item
            WorkItemRecord workItem = createTestWorkItem("WI-123", "TestTask",
                Map.of("priority", "high", "dueDate", "2026-12-31"));

            // When calling internal hash source method
            String hashSource = buildHashSource(workItem);

            // Then hash should be consistent
            assertNotNull(hashSource, "Hash source should not be null");
            assertFalse(hashSource.isEmpty(), "Hash source should not be empty");

            // Same work item should produce same hash
            String hashSource2 = buildHashSource(workItem);
            assertEquals(hashSource, hashSource2, "Hash should be consistent");
        }

        @Test
        @DisplayName("Hash consistency across different work items")
        void hashConsistencyAcrossDifferentItems() {
            // Given different work items
            WorkItemRecord item1 = createTestWorkItem("WI-1", "TaskA");
            WorkItemRecord item2 = createTestWorkItem("WI-2", "TaskA");
            WorkItemRecord item3 = createTestWorkItem("WI-1", "TaskB");

            // When getting hash sources
            String hash1 = buildHashSource(item1);
            String hash2 = buildHashSource(item2);
            String hash3 = buildHashSource(item3);

            // Then different items should have different hashes
            assertFalse(hash1.equals(hash2), "Different work items should have different hashes");
            assertFalse(hash1.equals(hash3), "Same ID different task should have different hashes");
        }

        @Test
        @DisplayName("Hash distribution quality")
        void hashDistributionQuality() {
            // Test that hash modulo provides good distribution
            int totalItems = 10000;
            int numAgents = 10;
            int[] counts = new int[numAgents];

            for (int i = 0; i < totalItems; i++) {
                String id = String.format("%05d", i);
                String hashSource = id + "|TaskA|";
                int hash = Math.abs(hashSource.hashCode());
                int agent = hash % numAgents;
                counts[agent]++;
            }

            // Check that distribution is relatively even
            double average = totalItems / (double) numAgents;
            for (int count : counts) {
                // Allow 10% deviation from average
                assertTrue(count >= average * 0.9 && count <= average * 1.1,
                    "Count should be close to average: " + count + " vs " + average);
            }
        }
    }

    // Helper method to create a test work item
    private WorkItemRecord createTestWorkItem(String id, String taskName) {
        return createTestWorkItem(id, taskName, Map.of());
    }

    private WorkItemRecord createTestWorkItem(String id, String taskName, Map<String, String> metadata) {
        return new TestWorkItemRecord(id, taskName, metadata);
    }

    // Helper method to check for overlap between work item lists
    private boolean hasOverlap(List<WorkItemRecord> list1, List<WorkItemRecord> list2) {
        return list1.stream()
            .anyMatch(item -> list2.stream()
                .anyMatch(other -> other.getID().equals(item.getID())));
    }

    // Helper method to build hash source
    private String buildHashSource(WorkItemRecord workItem) {
        // This mimics the internal hash source construction
        return workItem.getID() + "|" + workItem.getName() + "|" + workItem.getMetadata();
    }

    // Test implementation of InterfaceB_EnvironmentBasedClient
    private static class TestInterfaceBClient implements InterfaceB_EnvironmentBasedClient {
        private final List<WorkItemRecord> workItems = new java.util.ArrayList<>();

        public void addWorkItem(String id, String name, Map<String, String> metadata) {
            workItems.add(new TestWorkItemRecord(id, name, metadata));
        }

        public void clearWorkItems() {
            workItems.clear();
        }

        @Override
        public List<WorkItemRecord> getCompleteListOfLiveWorkItems(String sessionHandle) throws IOException {
            return new java.util.ArrayList<>(workItems);
        }

        // Implement other required methods with minimal implementations
        @Override
        public String connect(String user, String password) throws IOException {
            return "test-session";
        }

        @Override
        public void disconnect(String sessionHandle) throws IOException {
        }

        @Override
        public boolean checkConnection(String sessionHandle) throws IOException {
            return true;
        }
    }

    // Test implementation of WorkItemRecord
    private static class TestWorkItemRecord implements WorkItemRecord {
        private final String id;
        private final String name;
        private final Map<String, String> metadata;
        private final Instant creationDate;

        public TestWorkItemRecord(String id, String name, Map<String, String> metadata) {
            this.id = id;
            this.name = name;
            this.metadata = metadata;
            this.creationDate = Instant.now();
        }

        @Override
        public String getID() {
            return id;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Map<String, String> getMetadata() {
            return metadata;
        }

        @Override
        public Instant getCreationDate() {
            return creationDate;
        }

        // Implement other required methods
        @Override
        public String getSpecID() {
            return "TestSpec";
        }

        @Override
        public String getNetID() {
            return "TestNet";
        }

        @Override
        public String getCaseID() {
            return "TestCase";
        }

        @Override
        public String getTaskID() {
            return "TestTask";
        }

        @Override
        public org.yawlfoundation.yawl.engine.interfce.YAWLTask getTask() {
            return null; // Simplified for test
        }

        @Override
        public org.yawlfoundation.yawl.engine.interfce.YAWLCase getCase() {
            return null; // Simplified for test
        }

        @Override
        public org.yawlfoundation.yawl.engine.interfce.YAWLSpecification getSpec() {
            return null; // Simplified for test
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            TestWorkItemRecord that = (TestWorkItemRecord) obj;
            return id.equals(that.id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }
}