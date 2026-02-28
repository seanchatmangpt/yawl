package org.yawlfoundation.yawl.engine.agent;

import java.util.Objects;
import java.util.UUID;

/**
 * WorkItemPartitioner provides load-balancing and work distribution logic
 * for assigning work items to agents.
 *
 * Currently implements simple partitioning based on consistent hashing of task names.
 * Can be extended to support more sophisticated strategies (affinity, load-aware, etc.).
 *
 * Design:
 * - O(1) per-item partitioning check
 * - Deterministic: same task name always goes to same partition
 * - Enables future lock-free discovery via pre-partitioned queues
 * - Currently uses single shared queue, but logic is partition-aware
 */
public class WorkItemPartitioner {

    /**
     * Default number of partitions. Configurable via system property.
     */
    private static final int DEFAULT_NUM_PARTITIONS =
        Integer.parseInt(System.getProperty("yawl.workitem.partitions", "16"));

    private final int numPartitions;

    /**
     * Creates a new WorkItemPartitioner with default partition count.
     */
    public WorkItemPartitioner() {
        this(DEFAULT_NUM_PARTITIONS);
    }

    /**
     * Creates a new WorkItemPartitioner with specified partition count.
     *
     * @param numPartitions number of partitions (should be > 0)
     * @throws IllegalArgumentException if numPartitions <= 0
     */
    public WorkItemPartitioner(int numPartitions) {
        if (numPartitions <= 0) {
            throw new IllegalArgumentException("Number of partitions must be > 0, got: " + numPartitions);
        }
        this.numPartitions = numPartitions;
    }

    /**
     * Determines the partition index for a work item based on task name hashing.
     * Consistent hash ensures the same task name always maps to the same partition.
     *
     * @param item The work item
     * @return partition index (0 to numPartitions-1)
     * @throws NullPointerException if item is null
     */
    public int getPartition(WorkItem item) {
        Objects.requireNonNull(item, "Work item cannot be null");
        return getPartitionForTask(item.taskName());
    }

    /**
     * Determines the partition index for a task based on its name.
     * Uses consistent hashing to ensure deterministic mapping.
     *
     * @param taskName The task name
     * @return partition index (0 to numPartitions-1)
     * @throws NullPointerException if taskName is null
     */
    public int getPartitionForTask(String taskName) {
        Objects.requireNonNull(taskName, "Task name cannot be null");
        return Math.abs(taskName.hashCode()) % numPartitions;
    }

    /**
     * Determines the partition index for an agent based on its UUID.
     * Agents are assigned to partitions in round-robin or hash-based fashion.
     * This enables future load-balancing strategies.
     *
     * @param agentId The agent UUID
     * @return partition index (0 to numPartitions-1)
     * @throws NullPointerException if agentId is null
     */
    public int getPartitionForAgent(UUID agentId) {
        Objects.requireNonNull(agentId, "Agent ID cannot be null");
        return Math.abs((int) (agentId.getLeastSignificantBits() % numPartitions));
    }

    /**
     * Determines if a work item should be assigned to a specific agent.
     * Currently uses simple task-based partitioning: items are assigned to
     * agents if their partition matches.
     *
     * Can be extended to support:
     * - Affinity-based assignment (agent has experience with task)
     * - Load-based assignment (agent has capacity)
     * - Skill-based assignment (agent has required skills)
     *
     * @param item The work item
     * @param agentId The agent UUID
     * @return true if item should be assigned to agent, false otherwise
     * @throws NullPointerException if item or agentId is null
     */
    public boolean shouldAssignToAgent(WorkItem item, UUID agentId) {
        Objects.requireNonNull(item, "Work item cannot be null");
        Objects.requireNonNull(agentId, "Agent ID cannot be null");

        // Simple strategy: assign if partitions match
        int itemPartition = getPartition(item);
        int agentPartition = getPartitionForAgent(agentId);

        return itemPartition == agentPartition;
    }

    /**
     * Gets the number of partitions.
     *
     * @return partition count
     */
    public int getNumPartitions() {
        return numPartitions;
    }

    /**
     * Gets a diagnostic string representation.
     */
    @Override
    public String toString() {
        return String.format("WorkItemPartitioner[partitions=%d]", numPartitions);
    }
}
