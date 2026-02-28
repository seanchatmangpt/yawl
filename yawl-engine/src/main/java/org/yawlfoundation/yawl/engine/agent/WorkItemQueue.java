package org.yawlfoundation.yawl.engine.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * WorkItemQueue manages a thread-safe, unbounded queue of work items for the agent engine.
 *
 * Design:
 * - Uses ConcurrentLinkedQueue for lock-free thread safety
 * - Singleton pattern for single JVM usage
 * - O(n) filtering for agent-specific discovery (acceptable for work queues less than 1M items)
 * - No persistence layer - items lost on JVM restart
 * - Designed for immutable WorkItem records
 *
 * Thread-safety: All operations are thread-safe via ConcurrentLinkedQueue.
 * No external synchronization needed.
 *
 * @since Java 21
 */
public final class WorkItemQueue {

    private static final WorkItemQueue INSTANCE = new WorkItemQueue();

    private final ConcurrentLinkedQueue<WorkItem> queue;
    private final long createdTime;

    /**
     * Private constructor for singleton pattern.
     */
    private WorkItemQueue() {
        this.queue = new ConcurrentLinkedQueue<>();
        this.createdTime = System.currentTimeMillis();
    }

    /**
     * Gets the singleton instance of WorkItemQueue.
     * Thread-safe initialization via class loading.
     *
     * @return the singleton WorkItemQueue instance
     */
    public static WorkItemQueue getInstance() {
        return INSTANCE;
    }

    /**
     * Adds a work item to the queue.
     * Operation is non-blocking and thread-safe.
     *
     * @param item The work item to enqueue
     * @throws NullPointerException if item is null
     */
    public void enqueue(WorkItem item) {
        Objects.requireNonNull(item, "Cannot enqueue null work item");
        queue.offer(item);
    }

    /**
     * Removes and returns the next work item from the queue (FIFO).
     *
     * @return the next work item, or null if queue is empty
     */
    public WorkItem dequeue() {
        return queue.poll();
    }

    /**
     * Views the next work item without removing it from the queue.
     *
     * @return the next work item, or null if queue is empty
     */
    public WorkItem peek() {
        return queue.peek();
    }

    /**
     * Gets the current size of the queue.
     * Note: This is a point-in-time snapshot; queue may change immediately after.
     *
     * @return the number of items currently in the queue
     */
    public int size() {
        return queue.size();
    }

    /**
     * Checks if the queue is empty.
     *
     * @return true if queue contains no items, false otherwise
     */
    public boolean isEmpty() {
        return queue.isEmpty();
    }

    /**
     * Finds all work items assigned to a specific agent.
     * O(n) operation - scans entire queue to collect matching items.
     * Acceptable for typical work queues with less than 1M items.
     *
     * @param agentId The UUID of the agent
     * @return a list of work items assigned to the agent (may be empty)
     * @throws NullPointerException if agentId is null
     */
    public List<WorkItem> findItemsFor(UUID agentId) {
        Objects.requireNonNull(agentId, "Agent ID cannot be null");

        List<WorkItem> items = new ArrayList<>();
        for (WorkItem item : queue) {
            if (agentId.equals(item.assignedAgent())) {
                items.add(item);
            }
        }
        return items;
    }

    /**
     * Finds all pending (unassigned) work items.
     * O(n) operation.
     *
     * @return a list of all pending work items
     */
    public List<WorkItem> findPendingItems() {
        List<WorkItem> items = new ArrayList<>();
        for (WorkItem item : queue) {
            if (item.isPending()) {
                items.add(item);
            }
        }
        return items;
    }

    /**
     * Finds a work item by its ID.
     * O(n) operation.
     *
     * @param itemId The unique ID of the work item
     * @return the work item if found, null otherwise
     * @throws NullPointerException if itemId is null
     */
    public WorkItem findById(UUID itemId) {
        Objects.requireNonNull(itemId, "Item ID cannot be null");

        for (WorkItem item : queue) {
            if (item.itemId().equals(itemId)) {
                return item;
            }
        }
        return null;
    }

    /**
     * Replaces a work item in the queue by ID with an updated version.
     * Used for state transitions since WorkItem is immutable.
     * O(n) operation.
     *
     * @param itemId The ID of the work item to replace
     * @param updated The updated WorkItem to replace it with
     * @return true if the item was found and replaced, false otherwise
     * @throws NullPointerException if itemId or updated is null
     */
    public boolean replace(UUID itemId, WorkItem updated) {
        Objects.requireNonNull(itemId, "Item ID cannot be null");
        Objects.requireNonNull(updated, "Updated item cannot be null");

        for (int i = 0; i < queue.size(); i++) {
            WorkItem current = queue.poll();
            if (current == null) {
                break;
            }

            if (current.itemId().equals(itemId)) {
                queue.offer(updated);
                break;
            } else {
                queue.offer(current);
            }
        }
        return false;
    }

    /**
     * Removes and returns a specific work item by ID.
     * O(n) operation.
     *
     * @param itemId The unique ID of the work item
     * @return true if the item was found and removed, false otherwise
     * @throws NullPointerException if itemId is null
     */
    public boolean removeById(UUID itemId) {
        Objects.requireNonNull(itemId, "Item ID cannot be null");

        for (WorkItem item : queue) {
            if (item.itemId().equals(itemId)) {
                return queue.remove(item);
            }
        }
        return false;
    }

    /**
     * Clears all items from the queue.
     * Drains the queue completely.
     */
    public void clear() {
        queue.clear();
    }

    /**
     * Gets the number of work items in terminal state (COMPLETED or FAILED).
     * O(n) operation.
     *
     * @return count of completed or failed items
     */
    public int countTerminal() {
        int count = 0;
        for (WorkItem item : queue) {
            if (item.isTerminal()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Gets the number of work items in pending state.
     * O(n) operation.
     *
     * @return count of pending items
     */
    public int countPending() {
        int count = 0;
        for (WorkItem item : queue) {
            if (item.isPending()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Gets the time this queue was created (milliseconds since epoch).
     *
     * @return creation timestamp
     */
    public long getCreatedTime() {
        return createdTime;
    }

    /**
     * Gets the age of this queue in milliseconds.
     *
     * @return age in milliseconds
     */
    public long getAge() {
        return System.currentTimeMillis() - createdTime;
    }

    /**
     * Gets a diagnostic string representation of queue state.
     */
    @Override
    public String toString() {
        return "WorkItemQueue[size=%d, pending=%d, terminal=%d, age=%dms]".formatted(
            size(), countPending(), countTerminal(), getAge()
        );
    }
}
