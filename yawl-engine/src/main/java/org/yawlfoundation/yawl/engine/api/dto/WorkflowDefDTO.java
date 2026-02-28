package org.yawlfoundation.yawl.engine.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Data Transfer Object for workflow definition in REST API requests.
 * Used to create new agents with a specific workflow definition.
 *
 * @param workflowId Unique workflow identifier
 * @param name Human-readable workflow name
 * @param version Workflow version (e.g., "1.0")
 * @param description Workflow description
 * @param specificationXml YAWL workflow specification in XML format
 */
public record WorkflowDefDTO(
        @JsonProperty("workflowId")
        String workflowId,

        @JsonProperty("name")
        String name,

        @JsonProperty("version")
        String version,

        @JsonProperty("description")
        String description,

        @JsonProperty("specificationXml")
        String specificationXml
) {

    /**
     * Constructor with validation.
     */
    public WorkflowDefDTO {
        if (workflowId == null || workflowId.isBlank()) {
            throw new IllegalArgumentException("workflowId cannot be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name cannot be blank");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version cannot be blank");
        }
        if (specificationXml == null || specificationXml.isBlank()) {
            throw new IllegalArgumentException("specificationXml cannot be blank");
        }
    }

    /**
     * Check if this workflow definition is valid for creation.
     *
     * @return true if all required fields are present and non-empty
     */
    public boolean isValid() {
        return workflowId != null && !workflowId.isBlank() &&
               name != null && !name.isBlank() &&
               version != null && !version.isBlank() &&
               specificationXml != null && !specificationXml.isBlank();
    }

    /**
     * Get a summary string of this workflow definition.
     *
     * @return Formatted summary
     */
    public String getSummary() {
        return String.format("%s v%s: %s", name, version, description != null ? description : "");
    }
}
