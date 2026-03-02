/*
 * Copyright (c) 2026 YAWL Foundation. All rights reserved.
 * DO NOT MODIFY THIS FILE - GENERATED CODE
 */

package org.yawlfoundation.yawl.consensus;

/**
 * Proposal Type Enumeration
 *
 * Defines the type of consensus proposal being made.
 * Different types may have different handling rules.
 */
public enum ProposalType {
    /**
     * Workflow state change proposal
     *
     * Used for changing the state of a YAWL workflow instance.
     * Typically requires strong consistency.
     */
    WORKFLOW_STATE("Workflow State Change", "Strong consistency required"),

    /**
     * Task assignment proposal
     *
     * Used for assigning tasks to participants in a workflow.
     * May allow some flexibility in ordering.
     */
    TASK_ASSIGNMENT("Task Assignment", "Eventual consistency acceptable"),

    /**
     * Resource allocation proposal
     *
     * Used for allocating resources to workflow instances.
     * Requires strong consistency to avoid conflicts.
     */
    RESOURCE_ALLOCATION("Resource Allocation", "Strong consistency required"),

    /**
     * Configuration change proposal
     *
     * Used for changing workflow or system configuration.
     * Requires strong consensus and validation.
     */
    CONFIG_CHANGE("Configuration Change", "Strong consistency + validation"),

    /**
     * Logging/metadata proposal
     *
     * Used for logging events or updating metadata.
     * May allow eventual consistency for performance.
     */
    LOGGING("Logging/Metadata", "Eventual consistency acceptable"),

    /**
     * Recovery proposal
     *
     * Used for system recovery after failures.
     * Requires strong consistency to maintain system state.
     */
    RECOVERY("System Recovery", "Strong consistency required");

    private final String description;
    private final String consistencyRequirement;

    ProposalType(String description, String consistencyRequirement) {
        this.description = description;
        this.consistencyRequirement = consistencyRequirement;
    }

    public String getDescription() {
        return description;
    }

    public String getConsistencyRequirement() {
        return consistencyRequirement;
    }

    /**
     * Check if this proposal type requires strong consistency
     */
    public boolean requiresStrongConsistency() {
        return consistencyRequirement.contains("Strong consistency");
    }
}