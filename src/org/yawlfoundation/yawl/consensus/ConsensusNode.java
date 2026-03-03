/*
 * Copyright (c) 2026 YAWL Foundation. All rights reserved.
 * DO NOT MODIFY THIS FILE - GENERATED CODE
 */

package org.yawlfoundation.yawl.consensus;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Consensus Node Interface
 *
 * Represents a node in the consensus cluster. Each node participates
 * in the consensus protocol and maintains state.
 */
public interface ConsensusNode {

    /**
     * Get unique node identifier
     *
     * @return UUID representing this node
     */
    UUID getId();

    /**
     * Get node address for communication
     *
     * @return String representing node address
     */
    String getAddress();

    /**
     * Check if node is currently active
     *
     * @return true if node is active and participating
     */
    boolean isActive();

    /**
     * Handle incoming consensus proposal
     *
     * @param proposal The proposal to handle
     * @return CompletableFuture<ConsensusResult> with result
     */
    CompletableFuture<ConsensusResult> handleProposal(Proposal proposal);

    /**
     * Handle consensus vote
     *
     * @param vote The vote received from another node
     * @return true if vote is accepted
     */
    boolean handleVote(Vote vote);

    /**
     * Handle consensus heartbeat
     *
     * @param heartbeat Heartbeat from leader or other node
     */
    void handleHeartbeat(Heartbeat heartbeat);

    /**
     * Handle network partition detection
     *
     * @param partitionId Identifier for the partition
     */
    void handlePartition(PartitionId partitionId);

    /**
     * Get current role in consensus
     *
     * @return NodeRole for this node
     */
    NodeRole getRole();

    /**
     * Force this node to step down from leadership
     */
    void stepDown();

    /**
     * Check if this node can be leader (has sufficient votes)
     *
     * @return true if node can become leader
     */
    boolean canBeLeader();
}

/**
 * Concrete implementation of ConsensusNode
 */
class ConsensusNodeImpl implements ConsensusNode {
    private final UUID id;
    private final String address;
    private volatile NodeRole role;
    private volatile boolean active;
    private final ConsensusEngine engine;

    public ConsensusNodeImpl(String address, ConsensusEngine engine) {
        this.id = UUID.randomUUID();
        this.address = address;
        this.engine = engine;
        this.role = NodeRole.FOLLOWER;
        this.active = true;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public String getAddress() {
        return address;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public CompletableFuture<ConsensusResult> handleProposal(Proposal proposal) {
        if (!isActive()) {
            CompletableFuture<ConsensusResult> future = new CompletableFuture<>();
            future.completeExceptionally(new ConsensusException("Node is inactive"));
            return future;
        }

        // Delegate to consensus engine based on strategy
        return engine.propose(proposal);
    }

    @Override
    public boolean handleVote(Vote vote) {
        if (!isActive()) {
            return false;
        }

        // Implement voting logic based on consensus strategy
        // This will be overridden by specific strategy implementations
        return true;
    }

    @Override
    public void handleHeartbeat(Heartbeat heartbeat) {
        if (isActive()) {
            // Reset election timer based on heartbeat
            // Implementation varies by consensus strategy
        }
    }

    @Override
    public void handlePartition(PartitionId partitionId) {
        if (isActive()) {
            // Handle network partition
            // Implementation varies by consensus strategy
        }
    }

    @Override
    public NodeRole getRole() {
        return role;
    }

    @Override
    public void stepDown() {
        this.role = NodeRole.FOLLOWER;
        // Clean up leader resources
    }

    @Override
    public boolean canBeLeader() {
        // Check if this node has sufficient votes or qualifications to be leader
        return isActive() && (role == NodeRole.CANDIDATE || role == NodeRole.FOLLOWER);
    }
}