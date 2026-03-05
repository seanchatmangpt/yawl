/*
 * Copyright (c) 2026 YAWL Foundation. All rights reserved.
 * DO NOT MODIFY THIS FILE - GENERATED CODE
 */

package org.yawlfoundation.yawl.consensus;

/**
 * Consensus Status Enumeration
 *
 * Defines the possible outcomes of a consensus operation.
 * Provides detailed information about why consensus succeeded or failed.
 */
public enum ConsensusStatus {
    /**
     * Consensus was successfully achieved
     */
    SUCCESS("Consensus achieved", "Proposal was accepted by the cluster"),

    /**
     * Consensus timed out
     */
    TIMEOUT("Timeout", "No consensus reached within timeout period"),

    /**
     * Network partition prevented consensus
     */
    PARTITION("Network Partition", "Cluster was split and couldn't achieve quorum"),

    /**
     * Insufficient nodes for quorum
     */
    INSUFFICIENT_NODES("Insufficient Nodes", "Not enough nodes available for quorum"),

    /**
     * Proposal was rejected by voters
     */
    REJECTED("Rejected", "Proposal was explicitly rejected by cluster majority"),

    /**
     * Conflict with existing consensus
     */
    CONFLICT("Conflict", "Proposal conflicts with existing consensus value"),

    /**
     * Node is not in the correct state
     */
    INVALID_STATE("Invalid State", "Node cannot process proposal in current state"),

    /**
     * Proposal contains invalid data
     */
    INVALID_PROPOSAL("Invalid Proposal", "Proposal contains invalid or malformed data"),

    /**
     * Authentication/authorization failed
     */
    AUTH_FAILED("Authentication Failed", "Node is not authorized to propose value"),

    /**
     * System error occurred during consensus
     */
    SYSTEM_ERROR("System Error", "Internal system error prevented consensus"),

    /**
     * Leader changed during consensus process
     */
    LEADER_CHANGED("Leader Changed", "Leadership changed during consensus process");

    private final String description;
    private final String detail;

    ConsensusStatus(String description, String detail) {
        this.description = description;
        this.detail = detail;
    }

    public String getDescription() {
        return description;
    }

    public String getDetail() {
        return detail;
    }

    /**
     * Check if this status represents a successful consensus
     */
    public boolean isSuccess() {
        return this == SUCCESS;
    }

    /**
     * Check if this status represents a retryable failure
     */
    public boolean isRetryable() {
        return this == TIMEOUT || this == LEADER_CHANGED || this == PARTITION;
    }

    /**
     * Check if this status represents a permanent failure
     */
    public boolean isPermanent() {
        return this == REJECTED || this == CONFLICT || this == INVALID_PROPOSAL;
    }

    /**
     * Check if this status requires immediate attention
     */
    public boolean isCritical() {
        return this == SYSTEM_ERROR || this == INSUFFICIENT_NODES;
    }
}