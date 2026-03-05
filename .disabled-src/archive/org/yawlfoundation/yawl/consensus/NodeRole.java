/*
 * Copyright (c) 2026 YAWL Foundation. All rights reserved.
 * DO NOT MODIFY THIS FILE - GENERATED CODE
 */

package org.yawlfoundation.yawl.consensus;

/**
 * Node Role Enumeration
 *
 * Defines the role a node plays in the consensus protocol.
 * Roles change dynamically based on election and network conditions.
 */
public enum NodeRole {
    /**
     * Follower node
     *
     * Passively follows the leader and responds to requests.
     * Initiates elections if no heartbeat received.
     */
    FOLLOWER("Passive", "Follows leader", "Waits for heartbeats"),

    /**
     * Candidate node
     *
     * Actively seeking election as leader.
     * Votes for self and requests votes from others.
     */
    CANDIDATE("Election", "Seeks leadership", "Requests votes"),

    /**
     * Leader node
     *
     * Coordinates the cluster and makes decisions.
     * Processes proposals and sends heartbeats.
     */
    LEADER("Coordinator", "Manages cluster", "Processes proposals"),

    /**
     * Observer node
     *
     * Receives data but doesn't participate in consensus.
     * Used for monitoring and data replication.
     */
    OBSERVER("Monitor", "Observes only", "Read-only access"),

    /**
     * Standby node
     *
     * Ready to take over if leader fails.
     * Pre-elected but not yet active.
     */
    STANDBY("Backup", "Ready to take over", "Pre-elected");

    private final String description;
    private final String responsibility;
    private final String behavior;

    NodeRole(String description, String responsibility, String behavior) {
        this.description = description;
        this.responsibility = responsibility;
        this.behavior = behavior;
    }

    public String getDescription() {
        return description;
    }

    public String getResponsibility() {
        return responsibility;
    }

    public String getBehavior() {
        return behavior;
    }
}