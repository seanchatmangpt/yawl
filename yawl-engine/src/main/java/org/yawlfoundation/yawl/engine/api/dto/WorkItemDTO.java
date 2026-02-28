package org.yawlfoundation.yawl.engine.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

/**
 * Data Transfer Object for work item information in REST API responses.
 * Represents a single unit of work in the YAWL workflow engine.
 *
 * @param id Unique work item identifier
 * @param taskName Name of the task being executed
 * @param status Current work item status (RECEIVED, ENABLED, FIRED, SUSPENDED, ALLOCATED, ACTIVE, COMPLETED)
 * @param assignedAgent ID of the agent handling this work item (nullable if unassigned)
 * @param createdTime Timestamp when work item was created
 * @param completedTime Timestamp when work item was completed (nullable if still in progress)
 */
public record WorkItemDTO(
        @JsonProperty("id")
        UUID id,

        @JsonProperty("taskName")
        String taskName,

        @JsonProperty("status")
        String status,

        @JsonProperty("assignedAgent")
        UUID assignedAgent,

        @JsonProperty("createdTime")
        Instant createdTime,

        @JsonProperty("completedTime")
        Instant completedTime
) {

    /**
     * Constructor with validation.
     */
    public WorkItemDTO {
        if (id == null) {
            throw new IllegalArgumentException("id cannot be null");
        }
        if (taskName == null || taskName.isBlank()) {
            throw new IllegalArgumentException("taskName cannot be blank");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status cannot be blank");
        }
        if (createdTime == null) {
            throw new IllegalArgumentException("createdTime cannot be null");
        }
    }

    /**
     * Factory method to create WorkItemDTO from work item data.
     *
     * @param id Work item ID
     * @param taskName Task name
     * @param status Work item status
     * @param assignedAgent Assigned agent ID (nullable)
     * @param createdTime Creation timestamp
     * @param completedTime Completion timestamp (nullable)
     * @return New WorkItemDTO instance
     */
    public static WorkItemDTO create(UUID id, String taskName, String status, UUID assignedAgent,
                                      Instant createdTime, Instant completedTime) {
        return new WorkItemDTO(id, taskName, status, assignedAgent, createdTime, completedTime);
    }

    /**
     * Check if this work item is still in progress (not completed).
     *
     * @return true if completedTime is null
     */
    public boolean isInProgress() {
        return completedTime == null;
    }

    /**
     * Get elapsed time in milliseconds since work item was created.
     *
     * @return Milliseconds since creation, or milliseconds until completion if completed
     */
    public long getElapsedMillis() {
        Instant end = completedTime != null ? completedTime : Instant.now();
        return end.toEpochMilli() - createdTime.toEpochMilli();
    }

    /**
     * Check if this work item is assigned to an agent.
     *
     * @return true if assignedAgent is not null
     */
    public boolean isAssigned() {
        return assignedAgent != null;
    }

    /**
     * Get human-readable elapsed time string (e.g., "2h 15m").
     *
     * @return Formatted time string
     */
    public String getFormattedElapsedTime() {
        long totalSeconds = getElapsedMillis() / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;

        if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        }
        return String.format("%dm", minutes);
    }
}
