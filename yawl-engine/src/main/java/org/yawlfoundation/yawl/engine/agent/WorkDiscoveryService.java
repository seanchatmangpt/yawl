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
     * Idempotent: if already assigned to the same agent, no-op.
     *
     * @param item The work item to assign
     * @param agentId The UUID of the agent to assign to
     * @throws NullPointerException if item or agentId is null
     * @throws IllegalStateException if item is already assigned to a different agent
     *                                or in a terminal state
     */
    public void assignWork(WorkItem item, UUID agentId) {
        Objects.requireNonNull(item, "Work item cannot be null");
        Objects.requireNonNull(agentId, "Agent ID cannot be null");

        // Idempotent: if already assigned to the same agent, no-op
        if (item.getAssignedAgent() != null && item.getAssignedAgent().equals(agentId)) {
            return;
        }

        // Transition to ASSIGNED state
        item.assignTo(agentId);
    }

    /**
     * Checks out a work item by its ID, transitioning it to ASSIGNED state.
     * This is a convenience method for one-shot checkout operations.
     *
     * @param itemId The unique ID of the work item to check out
     * @param agentId The UUID of the agent checking out the item
     * @return an Optional containing the checked-out work item, or empty if not found
     * @throws NullPointerException if itemId or agentId is null
     * @throws IllegalStateException if the item is already in a terminal state
     */
    public Optional<WorkItem> checkoutItem(UUID itemId, UUID agentId) {
        Objects.requireNonNull(itemId, "Item ID cannot be null");
        Objects.requireNonNull(agentId, "Agent ID cannot be null");

        WorkItem item = queue.findById(itemId);
        if (item == null) {
            return Optional.empty();
        }

        assignWork(item, agentId);
        return Optional.of(item);
    }

    /**
     * Checks in a work item, transitioning it to a terminal state (COMPLETED or FAILED).
     * This method handles the final state transition of a work item.
     *
     * @param itemId The unique ID of the work item to check in
     * @param status The terminal status (COMPLETED or FAILED)
     * @param reason Optional failure reason (required if status is FAILED)
     * @return an Optional containing the checked-in work item, or empty if not found
     * @throws NullPointerException if itemId or status is null
     * @throws IllegalArgumentException if status is not a terminal status
     * @throws IllegalStateException if item is not in ASSIGNED state
     */
    public Optional<WorkItem> checkinItem(UUID itemId,
                                           WorkItemStatus status,
                                           Optional<String> reason) {
        Objects.requireNonNull(itemId, "Item ID cannot be null");
        Objects.requireNonNull(status, "Status cannot be null");

        WorkItem item = queue.findById(itemId);
        if (item == null) {
            return Optional.empty();
        }

        // Handle completion
        if (status instanceof WorkItemStatus.Completed) {
            item.complete();
            return Optional.of(item);
        }

        // Handle failure
        if (status instanceof WorkItemStatus.Failed failed) {
            String failureReason = reason
                .orElseThrow(() ->
                    new IllegalArgumentException(
                        "Failure reason is required for FAILED status"));
            item.fail(failureReason);
            return Optional.of(item);
        }

        // Invalid status for check-in
        throw new IllegalArgumentException(
            "Check-in requires terminal status (COMPLETED or FAILED), got: " + status);
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
