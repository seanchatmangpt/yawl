package org.yawlfoundation.yawl.engine.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * WorkDiscoveryService provides high-level work discovery and assignment operations
 * for agents in the Pure Java 25 workflow engine.
 *
 * Operations include:
 * - Discovering work items available for an agent
 * - Assigning work items to agents
 * - Checking out items for execution (mark ASSIGNED)
 * - Checking in completed or failed items
 *
 * Thread-safety: All operations delegate to WorkItemQueue which uses ConcurrentLinkedQueue.
 * No additional synchronization needed.
 *
 * Note: WorkItem is immutable, so assignments and status transitions return new instances.
 * The queue must be updated with the new instance.
 *
 * @since Java 21
 */
public class WorkDiscoveryService {

    private final WorkItemQueue queue;
    private final WorkItemPartitioner partitioner;

    /**
     * Creates a new WorkDiscoveryService with the default WorkItemQueue singleton.
     */
    public WorkDiscoveryService() {
        this.queue = WorkItemQueue.getInstance();
        this.partitioner = new WorkItemPartitioner();
    }

    /**
     * Discovers all work items assigned to a specific agent.
     * Returns items currently assigned to the agent regardless of status.
     *
     * @param agentId The UUID of the agent
     * @return a list of work items assigned to the agent (may be empty)
     * @throws NullPointerException if agentId is null
     */
    public List<WorkItem> discoverWork(UUID agentId) {
        Objects.requireNonNull(agentId, "Agent ID cannot be null");
        return queue.findItemsFor(agentId);
    }

    /**
     * Discovers all pending (unassigned) work items.
     * Returns work items not yet assigned to any agent.
     *
     * @return a list of all pending work items
     */
    public List<WorkItem> discoverPendingWork() {
        return queue.findPendingItems();
    }

    /**
     * Assigns a work item to an agent and transitions its status to ASSIGNED.
     * Since WorkItem is immutable, this returns a new assigned instance.
     * The caller must enqueue the returned item or update the queue.
     *
     * @param item The work item to assign
     * @param agentId The UUID of the agent to assign to
     * @return the assigned work item (new instance)
     * @throws NullPointerException if item or agentId is null
     * @throws IllegalStateException if item is already assigned to a different agent
     *                                or in a terminal state
     */
    public WorkItem assignWork(WorkItem item, UUID agentId) {
        Objects.requireNonNull(item, "Work item cannot be null");
        Objects.requireNonNull(agentId, "Agent ID cannot be null");

        if (item.isTerminal()) {
            throw new IllegalStateException(
                "Cannot assign work item with status: " + item.status());
        }

        // If already assigned to the same agent, return as-is (idempotent)
        if (agentId.equals(item.assignedAgent())) {
            return item;
        }

        // If assigned to different agent, reject
        if (item.assignedAgent() != null) {
            throw new IllegalStateException(
                "Cannot reassign work item already assigned to: " + item.assignedAgent());
        }

        // Transition to ASSIGNED state (returns new immutable instance)
        return item.assign(agentId);
    }

    /**
     * Checks out a work item by its ID, transitioning it to ASSIGNED state.
     * Updates the queue with the assigned instance.
     *
     * @param itemId The unique ID of the work item to check out
     * @param agentId The UUID of the agent checking out the item
     * @return an Optional containing the checked-out work item, or empty if not found
     * @throws NullPointerException if itemId or agentId is null
     * @throws IllegalStateException if the item is already assigned or in a terminal state
     */
    public Optional<WorkItem> checkoutItem(UUID itemId, UUID agentId) {
        Objects.requireNonNull(itemId, "Item ID cannot be null");
        Objects.requireNonNull(agentId, "Agent ID cannot be null");

        WorkItem item = queue.findById(itemId);
        if (item == null) {
            return Optional.empty();
        }

        WorkItem assigned = assignWork(item, agentId);

        // Update queue with the assigned version
        queue.removeById(itemId);
        queue.enqueue(assigned);

        return Optional.of(assigned);
    }

    /**
     * Checks in a work item, transitioning it to a terminal state (COMPLETED or FAILED).
     * Updates the queue with the final instance.
     *
     * @param itemId The unique ID of the work item to check in
     * @param isSuccess true for COMPLETED, false for FAILED
     * @param failureReason failure reason (required if isSuccess is false)
     * @return an Optional containing the checked-in work item, or empty if not found
     * @throws NullPointerException if itemId or failureReason (when needed) is null
     * @throws IllegalStateException if item is not in ASSIGNED state
     */
    public Optional<WorkItem> checkinItem(UUID itemId, boolean isSuccess, Optional<String> failureReason) {
        Objects.requireNonNull(itemId, "Item ID cannot be null");

        WorkItem item = queue.findById(itemId);
        if (item == null) {
            return Optional.empty();
        }

        if (!item.isAssigned()) {
            throw new IllegalStateException(
                "Cannot check in work item that is not assigned: status=" + item.status());
        }

        WorkItem completed;
        if (isSuccess) {
            completed = item.complete();
        } else {
            String reason = failureReason.orElse("Unknown failure");
            completed = item.fail(reason);
        }

        // Update queue with the final version
        queue.removeById(itemId);
        queue.enqueue(completed);

        return Optional.of(completed);
    }

    /**
     * Gets a list of work items suitable for assignment to an agent based on
     * load-balancing logic (simple hash-based partitioning for future optimization).
     *
     * Currently returns all pending items, but the underlying partitioner can
     * be extended to support hash-based assignment strategies.
     *
     * @param agentId The UUID of the agent
     * @return a list of candidate work items for the agent
     */
    public List<WorkItem> discoverWorkForAgent(UUID agentId) {
        Objects.requireNonNull(agentId, "Agent ID cannot be null");

        // For now, return all pending items; partitioner can add logic here
        List<WorkItem> pending = discoverPendingWork();
        List<WorkItem> candidates = new ArrayList<>();

        for (WorkItem item : pending) {
            if (partitioner.shouldAssignToAgent(item, agentId)) {
                candidates.add(item);
            }
        }

        return candidates;
    }

    /**
     * Gets statistics about the work queue.
     *
     * @return a string with diagnostic information
     */
    public String getQueueStats() {
        return queue.toString();
    }

    /**
     * Gets the total number of items in the work queue.
     *
     * @return queue size
     */
    public int getQueueSize() {
        return queue.size();
    }

    /**
     * Gets the number of pending work items.
     *
     * @return count of pending items
     */
    public int getPendingCount() {
        return queue.countPending();
    }

    /**
     * Gets the number of terminal work items (COMPLETED or FAILED).
     *
     * @return count of terminal items
     */
    public int getTerminalCount() {
        return queue.countTerminal();
    }
}
