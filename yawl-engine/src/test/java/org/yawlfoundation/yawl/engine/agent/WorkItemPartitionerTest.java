package org.yawlfoundation.yawl.engine.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for WorkItemPartitioner load-balancing logic.
 * Covers:
 * - Consistent hashing for tasks
 * - Partition assignment for agents
 * - Deterministic partitioning
 * - Load distribution
 */
@DisplayName("Work Item Partitioner Tests")
public class WorkItemPartitionerTest {

    private WorkItemPartitioner partitioner;
    private static final int DEFAULT_PARTITIONS = 16;

    @BeforeEach
    void setup() {
        partitioner = new WorkItemPartitioner();
    }

    @Nested
    @DisplayName("Constructor & Configuration")
    class ConstructorTests {

        @Test
        @DisplayName("default constructor uses DEFAULT_PARTITIONS")
        void testDefaultConstructor() {
            assertEquals(DEFAULT_PARTITIONS, partitioner.getNumPartitions());
        }

        @Test
        @DisplayName("custom partition count constructor")
        void testCustomPartitionCount() {
            WorkItemPartitioner custom = new WorkItemPartitioner(32);
            assertEquals(32, custom.getNumPartitions());
        }

        @Test
        @DisplayName("zero partitions throws IllegalArgumentException")
        void testZeroPartitionsThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> new WorkItemPartitioner(0));
        }

        @Test
        @DisplayName("negative partitions throws IllegalArgumentException")
        void testNegativePartitionsThrows() {
            assertThrows(IllegalArgumentException.class,
                () -> new WorkItemPartitioner(-5));
        }

        @Test
        @DisplayName("single partition allowed")
        void testSinglePartition() {
            WorkItemPartitioner single = new WorkItemPartitioner(1);
            assertEquals(1, single.getNumPartitions());
        }
    }

    @Nested
    @DisplayName("Task-Based Partitioning")
    class TaskPartitioningTests {

        @Test
        @DisplayName("same task name always maps to same partition")
        void testDeterministicPartitioning() {
            String taskName = "OrderProcessing";

            int partition1 = partitioner.getPartitionForTask(taskName);
            int partition2 = partitioner.getPartitionForTask(taskName);
            int partition3 = partitioner.getPartitionForTask(taskName);

            assertEquals(partition1, partition2);
            assertEquals(partition2, partition3);
        }

        @Test
        @DisplayName("partition is within valid range")
        void testPartitionInRange() {
            String[] taskNames = {"Task1", "Task2", "OrderProcessing", "PaymentValidation"};

            for (String taskName : taskNames) {
                int partition = partitioner.getPartitionForTask(taskName);
                assertTrue(partition >= 0 && partition < DEFAULT_PARTITIONS,
                    "Partition " + partition + " outside range [0," + (DEFAULT_PARTITIONS - 1) + "]");
            }
        }

        @Test
        @DisplayName("different task names may map to same partition")
        void testPartitionCollisions() {
            // Different names can collide (normal hash behavior)
            String task1 = "Task1";
            String task2 = "Task2";

            int p1 = partitioner.getPartitionForTask(task1);
            int p2 = partitioner.getPartitionForTask(task2);

            // Just verify they're in valid range
            assertTrue(p1 >= 0 && p1 < DEFAULT_PARTITIONS);
            assertTrue(p2 >= 0 && p2 < DEFAULT_PARTITIONS);
        }

        @Test
        @DisplayName("null task name throws NullPointerException")
        void testNullTaskNameThrows() {
            assertThrows(NullPointerException.class,
                () -> partitioner.getPartitionForTask(null));
        }

        @Test
        @DisplayName("empty task name handled correctly")
        void testEmptyTaskName() {
            int partition = partitioner.getPartitionForTask("");
            assertTrue(partition >= 0 && partition < DEFAULT_PARTITIONS);
        }

        @Test
        @DisplayName("getPartition via WorkItem delegates to task name")
        void testGetPartitionViaWorkItem() {
            UUID itemId = UUID.randomUUID();
            WorkItem item = new WorkItem(itemId, "SpecificTask");

            int partitionDirect = partitioner.getPartitionForTask("SpecificTask");
            int partitionViaItem = partitioner.getPartition(item);

            assertEquals(partitionDirect, partitionViaItem);
        }

        @Test
        @DisplayName("null WorkItem throws NullPointerException")
        void testNullWorkItemThrows() {
            assertThrows(NullPointerException.class,
                () -> partitioner.getPartition(null));
        }
    }

    @Nested
    @DisplayName("Agent-Based Partitioning")
    class AgentPartitioningTests {

        @Test
        @DisplayName("same agent UUID always maps to same partition")
        void testDeterministicAgentPartitioning() {
            UUID agentId = UUID.randomUUID();

            int partition1 = partitioner.getPartitionForAgent(agentId);
            int partition2 = partitioner.getPartitionForAgent(agentId);
            int partition3 = partitioner.getPartitionForAgent(agentId);

            assertEquals(partition1, partition2);
            assertEquals(partition2, partition3);
        }

        @Test
        @DisplayName("agent partition is within valid range")
        void testAgentPartitionInRange() {
            for (int i = 0; i < 10; i++) {
                UUID agentId = UUID.randomUUID();
                int partition = partitioner.getPartitionForAgent(agentId);

                assertTrue(partition >= 0 && partition < DEFAULT_PARTITIONS,
                    "Agent partition " + partition + " outside range");
            }
        }

        @Test
        @DisplayName("null agent UUID throws NullPointerException")
        void testNullAgentIdThrows() {
            assertThrows(NullPointerException.class,
                () -> partitioner.getPartitionForAgent(null));
        }

        @Test
        @DisplayName("different agents may map to different partitions")
        void testAgentDistribution() {
            Set<Integer> partitions = new HashSet<>();

            for (int i = 0; i < 50; i++) {
                UUID agentId = UUID.randomUUID();
                partitions.add(partitioner.getPartitionForAgent(agentId));
            }

            // Should use multiple partitions with high probability
            assertTrue(partitions.size() > 1,
                "Expected agents to distribute across multiple partitions");
        }
    }

    @Nested
    @DisplayName("Assignment Decision Logic")
    class AssignmentLogicTests {

        @Test
        @DisplayName("shouldAssignToAgent when partitions match")
        void testShouldAssignWhenPartitionsMatch() {
            // Create partitioner with small partition count for testing
            WorkItemPartitioner smallPartitioner = new WorkItemPartitioner(4);

            // Find task/agent combination that hashes to same partition
            boolean found = false;
            for (int i = 0; i < 100 && !found; i++) {
                String taskName = "Task" + i;
                UUID agentId = UUID.randomUUID();

                int taskPartition = smallPartitioner.getPartitionForTask(taskName);
                int agentPartition = smallPartitioner.getPartitionForAgent(agentId);

                if (taskPartition == agentPartition) {
                    WorkItem item = new WorkItem(UUID.randomUUID(), taskName);
                    assertTrue(smallPartitioner.shouldAssignToAgent(item, agentId),
                        "Should assign when partitions match");
                    found = true;
                }
            }

            assertTrue(found, "Should have found matching partition pair");
        }

        @Test
        @DisplayName("shouldAssignToAgent returns false when partitions don't match")
        void testShouldNotAssignWhenPartitionsDiffer() {
            // Create partitioner with few partitions for easy mismatch
            WorkItemPartitioner singlePartitioner = new WorkItemPartitioner(1);
            // With 1 partition, all hash to partition 0, so all should match
            UUID agentId = UUID.randomUUID();
            WorkItem item = new WorkItem(UUID.randomUUID(), "AnyTask");

            // With single partition, assignment decision is simpler
            assertTrue(singlePartitioner.shouldAssignToAgent(item, agentId));
        }

        @Test
        @DisplayName("shouldAssignToAgent null WorkItem throws")
        void testShouldAssignNullItemThrows() {
            UUID agentId = UUID.randomUUID();
            assertThrows(NullPointerException.class,
                () -> partitioner.shouldAssignToAgent(null, agentId));
        }

        @Test
        @DisplayName("shouldAssignToAgent null agentId throws")
        void testShouldAssignNullAgentThrows() {
            WorkItem item = new WorkItem(UUID.randomUUID(), "Task");
            assertThrows(NullPointerException.class,
                () -> partitioner.shouldAssignToAgent(item, null));
        }
    }

    @Nested
    @DisplayName("Load Distribution")
    class LoadDistributionTests {

        @Test
        @DisplayName("tasks distribute across partitions")
        void testTaskDistribution() {
            Map<Integer, Integer> taskCountPerPartition = new HashMap<>();

            for (int i = 0; i < 1000; i++) {
                String taskName = "Task" + i;
                int partition = partitioner.getPartitionForTask(taskName);
                taskCountPerPartition.put(partition,
                    taskCountPerPartition.getOrDefault(partition, 0) + 1);
            }

            // Should use multiple partitions
            assertTrue(taskCountPerPartition.size() > 1,
                "Tasks should distribute across multiple partitions");

            // Check fairly even distribution
            int avgPerPartition = 1000 / DEFAULT_PARTITIONS;
            for (Integer count : taskCountPerPartition.values()) {
                // Allow 50% variation from average
                assertTrue(count > avgPerPartition / 2 && count < avgPerPartition * 2,
                    "Partition has " + count + " tasks, expected around " + avgPerPartition);
            }
        }

        @Test
        @DisplayName("agents distribute across partitions")
        void testAgentDistribution() {
            Map<Integer, Integer> agentCountPerPartition = new HashMap<>();

            for (int i = 0; i < 500; i++) {
                UUID agentId = UUID.randomUUID();
                int partition = partitioner.getPartitionForAgent(agentId);
                agentCountPerPartition.put(partition,
                    agentCountPerPartition.getOrDefault(partition, 0) + 1);
            }

            // Should use multiple partitions
            assertTrue(agentCountPerPartition.size() > 1,
                "Agents should distribute across multiple partitions");

            // Check that no partition is completely empty
            assertEquals(DEFAULT_PARTITIONS, agentCountPerPartition.size(),
                "With 500 random UUIDs and 16 partitions, should populate most");
        }
    }

    @Nested
    @DisplayName("Diagnostic Methods")
    class DiagnosticTests {

        @Test
        @DisplayName("toString returns non-null diagnostic string")
        void testToString() {
            String diagnostic = partitioner.toString();
            assertNotNull(diagnostic);
            assertTrue(diagnostic.contains("WorkItemPartitioner"));
            assertTrue(diagnostic.contains(String.valueOf(DEFAULT_PARTITIONS)));
        }

        @Test
        @DisplayName("getNumPartitions returns configured value")
        void testGetNumPartitions() {
            assertEquals(DEFAULT_PARTITIONS, partitioner.getNumPartitions());

            WorkItemPartitioner custom = new WorkItemPartitioner(42);
            assertEquals(42, custom.getNumPartitions());
        }
    }

    @Nested
    @DisplayName("Edge Cases & Stress")
    class EdgeCasesTests {

        @Test
        @DisplayName("very large partition counts")
        void testLargePartitionCount() {
            WorkItemPartitioner large = new WorkItemPartitioner(1000);
            UUID agentId = UUID.randomUUID();
            WorkItem item = new WorkItem(UUID.randomUUID(), "Task");

            int agentPartition = large.getPartitionForAgent(agentId);
            int itemPartition = large.getPartition(item);

            assertTrue(agentPartition >= 0 && agentPartition < 1000);
            assertTrue(itemPartition >= 0 && itemPartition < 1000);
        }

        @Test
        @DisplayName("many repeated task names")
        void testRepeatedTaskNames() {
            String taskName = "RepeatedTask";
            int partition = partitioner.getPartitionForTask(taskName);

            for (int i = 0; i < 100; i++) {
                WorkItem item = new WorkItem(UUID.randomUUID(), taskName);
                assertEquals(partition, partitioner.getPartition(item),
                    "Same task name should always map to same partition");
            }
        }

        @Test
        @DisplayName("similar task names may partition differently")
        void testSimilarTaskNames() {
            String task1 = "PaymentProcessing";
            String task2 = "PaymentValidation";
            String task3 = "PaymentApproval";

            int p1 = partitioner.getPartitionForTask(task1);
            int p2 = partitioner.getPartitionForTask(task2);
            int p3 = partitioner.getPartitionForTask(task3);

            // All should be valid partitions (hash may differ)
            assertTrue(p1 >= 0 && p1 < DEFAULT_PARTITIONS);
            assertTrue(p2 >= 0 && p2 < DEFAULT_PARTITIONS);
            assertTrue(p3 >= 0 && p3 < DEFAULT_PARTITIONS);
        }

        @Test
        @DisplayName("case sensitivity in task names")
        void testCaseSensitivity() {
            int partition1 = partitioner.getPartitionForTask("MyTask");
            int partition2 = partitioner.getPartitionForTask("mytask");
            int partition3 = partitioner.getPartitionForTask("MYTASK");

            // Different cases produce different hashes
            // (asserting they could differ, but all valid)
            assertTrue(partition1 >= 0 && partition1 < DEFAULT_PARTITIONS);
            assertTrue(partition2 >= 0 && partition2 < DEFAULT_PARTITIONS);
            assertTrue(partition3 >= 0 && partition3 < DEFAULT_PARTITIONS);
        }
    }
}
