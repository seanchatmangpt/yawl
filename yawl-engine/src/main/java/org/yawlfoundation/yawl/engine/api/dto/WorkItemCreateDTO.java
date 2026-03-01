package org.yawlfoundation.yawl.engine.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data Transfer Object for work item creation in REST API requests.
 * Used to enqueue a new work item for processing.
 *
 * @param taskName Name of the task to execute
 * @param caseId Case identifier (optional, nullable)
 * @param payload Work item payload/data (optional)
 */
public record WorkItemCreateDTO(
        @JsonProperty("taskName")
        String taskName,

        @JsonProperty("caseId")
        String caseId,

        @JsonProperty("payload")
        String payload
) {

    /**
     * Constructor with validation.
     */
    public WorkItemCreateDTO {
        if (taskName == null || taskName.isBlank()) {
            throw new IllegalArgumentException("taskName cannot be blank");
        }
    }

    /**
     * Check if this work item creation request is valid.
     *
     * @return true if taskName is present
     */
    public boolean isValid() {
        return taskName != null && !taskName.isBlank();
    }

    /**
     * Get a display string for this work item.
     *
     * @return Formatted display string
     */
    public String getDisplayString() {
        StringBuilder sb = new StringBuilder();
        sb.append(taskName);
        if (caseId != null && !caseId.isBlank()) {
            sb.append(" [case=").append(caseId).append("]");
        }
        return sb.toString();
    }
}
