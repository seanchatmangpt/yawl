package org.yawlfoundation.yawl.engine.agent;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * PartitionConfig provides configuration and factory methods for the partitioned
 * queue system, enabling 10M+ agent scaling with minimal contention.
 *
 * Responsibilities:
 * 1. Manages PartitionedWorkQueue singleton lifecycle
 * 2. Provides polling strategy configuration
 * 3. Routes agents to partitions via consistent hashing
 * 4. Exposes monitoring and diagnostics APIs
 *
 * Singleton Pattern:
 * - PartitionedWorkQueue is lazily initialized on first access
 * - AdaptivePollingStrategy is configured per deployment
 * - Thread-safe initialization via class loading
 *
 * Configuration:
 * ```properties
 * yawl.partition.queue.enabled=true
 * yawl.partition.polling.initial.ms=1
 * yawl.partition.polling.max.ms=1000
 * ```
 *
 * Usage:
 * ```java
 * PartitionConfig config = PartitionConfig.getInstance();
 *
 * // Enqueue work
 * WorkItem item = WorkItem.create("ReviewDocument");
 * config.enqueueWork(item);
 *
 * // Dequeue as agent
 * UUID agentId = UUID.randomUUID();
 * WorkItem work = config.dequeueWork(agentId, 1, TimeUnit.SECONDS);
 *
 * // Monitor queue health
 * PartitionedWorkQueue.PartitionStats stats = config.getQueueStats();
 * System.out.println("Queue depth: " + stats.totalDepth());
 * ```
 *
 * @since Java 21
 */
public final class PartitionConfig {

    /**
     * Singleton instance of PartitionConfig.
     */
    private static final PartitionConfig INSTANCE = new PartitionConfig();

    /**
     * Shared PartitionedWorkQueue instance (1024 partitions).
     */
    private final PartitionedWorkQueue workQueue;

    /**
     * Adaptive polling strategy for backoff management.
     */
    private final AdaptivePollingStrategy pollingStrategy;

    /**
     * Whether partitioned queue is enabled (vs legacy global queue).
     */
    private final boolean partitionedQueueEnabled;

    /**
     * Initial polling timeout in milliseconds.
     * Default: 1ms (responsive, minimal CPU usage)
     */
    private final long pollInitialMs;

    /**
     * Maximum polling timeout in milliseconds.
     * Default: 1000ms (prevents indefinite polling)
     */
    private final long pollMaxMs;

    /**
     * Private constructor for singleton pattern.
     * Initializes partitioned queue and polling strategy from system properties.
     */
    private PartitionConfig() {
        this.partitionedQueueEnabled = Boolean.parseBoolean(
            System.getProperty("yawl.partition.queue.enabled", "true")
        );

        this.pollInitialMs = Long.parseLong(
            System.getProperty("yawl.partition.polling.initial.ms", "1")
        );

        this.pollMaxMs = Long.parseLong(
            System.getProperty("yawl.partition.polling.max.ms", "1000")
        );

        this.workQueue = new PartitionedWorkQueue();
        this.pollingStrategy = new AdaptivePollingStrategy(pollInitialMs, pollMaxMs);
    }

    /**
     * Gets the singleton instance of PartitionConfig.
     * Thread-safe initialization via class loading.
     *
     * @return The global PartitionConfig instance
     */
    public static PartitionConfig getInstance() {
        return INSTANCE;
    }

    /**
     * Enqueues a work item into the partitioned queue.
     * Routes to partition based on assigned agent UUID.
     *
     * @param workItem The work item to enqueue
     * @throws NullPointerException if workItem is null
     * @throws InterruptedException if thread is interrupted
     */
    public void enqueueWork(WorkItem workItem) throws InterruptedException {
        Objects.requireNonNull(workItem, "Work item cannot be null");
        if (partitionedQueueEnabled) {
            workQueue.enqueue(workItem);
        } else {
            // Fallback to legacy global queue (backward compatibility)
            WorkItemQueue.getInstance().enqueue(workItem);
        }
    }

    /**
     * Dequeues a work item for an agent with adaptive polling.
     * Blocks with exponential backoff if partition is empty.
     *
     * Polling behavior:
     * - Initial timeout: 1ms (responsive)
     * - Empty poll: Double timeout (exponential backoff)
     * - Successful dequeue: Reset to 1ms
     * - Max timeout: 1000ms
     *
     * @param agentId The UUID of the requesting agent
     * @param timeout Maximum wait time before returning null
     * @param unit Time unit for timeout
     * @return The next work item, or null if none available
     * @throws NullPointerException if agentId or unit is null
     * @throws InterruptedException if thread is interrupted
     */
    public WorkItem dequeueWork(UUID agentId, long timeout, TimeUnit unit) throws InterruptedException {
        Objects.requireNonNull(agentId, "Agent ID cannot be null");
        Objects.requireNonNull(unit, "TimeUnit cannot be null");

        if (!partitionedQueueEnabled) {
            // Fallback to legacy global queue
            WorkItem item = WorkItemQueue.getInstance().dequeue();
            if (item == null && timeout > 0) {
                // Simulate blocking for legacy behavior
                Thread.sleep(Math.min(unit.toMillis(timeout), 100));
            }
            return item;
        }

        WorkItem item = workQueue.dequeue(agentId, pollingStrategy.getTimeout(agentId), TimeUnit.MILLISECONDS);

        if (item != null) {
            pollingStrategy.recordSuccess(agentId);
        } else {
            pollingStrategy.recordEmpty(agentId);
        }

        return item;
    }

    /**
     * Non-blocking variant: attempts immediate dequeue without waiting.
     *
     * @param agentId The UUID of the requesting agent
     * @return The next work item, or null if none available
     * @throws NullPointerException if agentId is null
     */
    public WorkItem tryDequeueWork(UUID agentId) {
        Objects.requireNonNull(agentId, "Agent ID cannot be null");

        if (!partitionedQueueEnabled) {
            return WorkItemQueue.getInstance().dequeue();
        }

        WorkItem item = workQueue.tryDequeue(agentId);
        if (item != null) {
            pollingStrategy.recordSuccess(agentId);
        }
        return item;
    }

    /**
     * Gets the partition index for an agent.
     * Enables agents to understand which partition they read from.
     *
     * Consistent hashing ensures:
     * - Same agent always uses same partition
     * - Work items are pre-routed to agent's partition
     * - Low contention per partition
     *
     * @param agentId The agent UUID
     * @return Partition index [0, 1024)
     * @throws NullPointerException if agentId is null
     */
    public int getPartitionForAgent(UUID agentId) {
        Objects.requireNonNull(agentId, "Agent ID cannot be null");
        return Math.abs(agentId.hashCode()) & (PartitionedWorkQueue.NUM_PARTITIONS - 1);
    }

    /**
     * Gets current queue statistics for monitoring.
     *
     * @return PartitionStats with depth distribution info
     */
    public PartitionedWorkQueue.PartitionStats getQueueStats() {
        if (!partitionedQueueEnabled) {
            return null; // Legacy queue doesn't provide stats
        }
        return workQueue.getStats();
    }

    /**
     * Gets the total number of pending work items across all partitions.
     *
     * @return Total items in queue
     */
    public int getQueueDepth() {
        if (!partitionedQueueEnabled) {
            return WorkItemQueue.getInstance().size();
        }
        return workQueue.getTotalDepth();
    }

    /**
     * Gets depth array for all partitions (for monitoring).
     *
     * @return Array of 1024 depth values
     */
    public int[] getAllPartitionDepths() {
        if (!partitionedQueueEnabled) {
            return new int[0]; // Legacy queue doesn't partition
        }
        return workQueue.getDepths();
    }

    /**
     * Gets the polling state for an agent (for monitoring).
     *
     * @param agentId The agent UUID
     * @return Current polling timeout in milliseconds
     * @throws NullPointerException if agentId is null
     */
    public long getPollingTimeout(UUID agentId) {
        Objects.requireNonNull(agentId, "Agent ID cannot be null");
        return pollingStrategy.getTimeout(agentId);
    }

    /**
     * Resets polling backoff for an agent.
     * Used when reassigning agents or clearing backoff state.
     *
     * @param agentId The agent UUID
     * @throws NullPointerException if agentId is null
     */
    public void resetPollingBackoff(UUID agentId) {
        Objects.requireNonNull(agentId, "Agent ID cannot be null");
        pollingStrategy.reset(agentId);
    }

    /**
     * Clears all state (for testing or shutdown).
     * Drains queues and resets polling strategy.
     */
    public void clear() {
        workQueue.clear();
        pollingStrategy.resetAll();
    }

    /**
     * Checks if partitioned queue is enabled.
     *
     * @return true if using partitioned queue, false if using legacy global queue
     */
    public boolean isPartitionedQueueEnabled() {
        return partitionedQueueEnabled;
    }

    /**
     * Gets configuration summary (for debugging).
     */
    @Override
    public String toString() {
        if (partitionedQueueEnabled) {
            return "PartitionConfig[enabled=true, polling=%dms-%dms, queue=%s]"
                .formatted(pollInitialMs, pollMaxMs, workQueue);
        } else {
            return "PartitionConfig[enabled=false, using legacy global queue]";
        }
    }
}
