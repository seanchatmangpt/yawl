package org.yawlfoundation.yawl.engine.agent;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable record representing a work item assigned to an agent.
 * Tracks lifecycle from pending assignment through completion or failure.
 *
 * Thread-safe: Uses immutable records and provides functional updates
 * via withStatus() method for state transitions.
 *
 * @param itemId Unique identifier for this work item
 * @param assignedAgent UUID of the agent assigned to this work item (nullable while pending)
 * @param taskName Human-readable name of the task/workflow step
 * @param createdTime System timestamp when work item was created
 * @param status Current lifecycle status (Pending, Assigned, Completed, Failed)
 *
 * @since Java 21 (records with sealed class support)
 */
public record WorkItem(
        UUID itemId,
        UUID assignedAgent,
        String taskName,
        long createdTime,
        WorkItemStatus status
) {

    /**
     * Constructor with validation.
     * Ensures immutability by validating non-null critical fields.
     */
    public WorkItem {
        Objects.requireNonNull(itemId, "itemId cannot be null");
        Objects.requireNonNull(taskName, "taskName cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        if (createdTime < 0) {
            throw new IllegalArgumentException("createdTime cannot be negative");
        }
    }

    /**
     * Factory method to create a new pending work item.
     *
     * @param taskName Name of the workflow task
     * @return New WorkItem in PENDING status
     */
    public static WorkItem create(String taskName) {
        return new WorkItem(
                UUID.randomUUID(),
                null, // No agent assigned yet
                taskName,
                System.currentTimeMillis(),
                WorkItemStatus.pending()
        );
    }

    /**
     * Factory method to create a new pending work item with explicit ID.
     *
     * @param itemId Explicit work item identifier
     * @param taskName Name of the workflow task
     * @return New WorkItem in PENDING status
     */
    public static WorkItem create(UUID itemId, String taskName) {
        Objects.requireNonNull(itemId, "itemId cannot be null");
        return new WorkItem(
                itemId,
                null,
                taskName,
                System.currentTimeMillis(),
                WorkItemStatus.pending()
        );
    }

    /**
     * Transition work item to ASSIGNED status.
     * Updates assignment time via new Assigned() instance.
     *
     * @param agentId UUID of the agent being assigned
     * @return Updated WorkItem with assigned status and agent
     */
    public WorkItem assign(UUID agentId) {
        Objects.requireNonNull(agentId, "agentId cannot be null");
        return new WorkItem(
                this.itemId,
                agentId,
                this.taskName,
                this.createdTime,
                WorkItemStatus.assigned()
        );
    }

    /**
     * Transition work item to COMPLETED status.
     *
     * @return Updated WorkItem with completed status
     */
    public WorkItem complete() {
        return new WorkItem(
                this.itemId,
                this.assignedAgent,
                this.taskName,
                this.createdTime,
                WorkItemStatus.completed()
        );
    }

    /**
     * Transition work item to FAILED status with error reason.
     *
     * @param reason Human-readable failure reason
     * @return Updated WorkItem with failed status
     */
    public WorkItem fail(String reason) {
        Objects.requireNonNull(reason, "failure reason cannot be null");
        return new WorkItem(
                this.itemId,
                this.assignedAgent,
                this.taskName,
                this.createdTime,
                WorkItemStatus.failed(reason)
        );
    }

    /**
     * Get the age of this work item in milliseconds since creation.
     *
     * @return Time elapsed since createdTime in milliseconds
     */
    public long getAge() {
        return System.currentTimeMillis() - this.createdTime;
    }

    /**
     * Check if this work item is still pending assignment.
     *
     * @return true if status is Pending and assignedAgent is null
     */
    public boolean isPending() {
        return status instanceof WorkItemStatus.Pending;
    }

    /**
     * Check if this work item is currently assigned.
     *
     * @return true if status is Assigned and assignedAgent is not null
     */
    public boolean isAssigned() {
        return status instanceof WorkItemStatus.Assigned && assignedAgent != null;
    }

    /**
     * Check if this work item is in a terminal state (completed or failed).
     *
     * @return true if status is Completed or Failed
     */
    public boolean isTerminal() {
        return status instanceof WorkItemStatus.Completed ||
               status instanceof WorkItemStatus.Failed;
    }

    /**
     * Human-readable representation including lifecycle information.
     *
     * @return String representation with item ID, task name, and current status
     */
    @Override
    public String toString() {
        return "WorkItem{" +
                "itemId=" + itemId +
                ", taskName='" + taskName + '\'' +
                ", assignedAgent=" + assignedAgent +
                ", age=" + getAge() + "ms" +
                ", status=" + status +
                '}';
    }
}
