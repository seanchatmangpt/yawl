/*
 * Copyright (c) 2026 YAWL Foundation. All rights reserved.
 * DO NOT MODIFY THIS FILE - GENERATED CODE
 */

package org.yawlfoundation.yawl.consensus;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Consensus Proposal
 *
 * Represents a value proposed for consensus in the distributed system.
 * Contains the value itself and metadata about the proposal.
 */
public class Proposal implements Serializable {
    private final long proposalId;
    private final String value;
    private final UUID proposerId;
    private final long timestamp;
    private final ProposalType type;
    private final int priority;

    /**
     * Constructor for a new proposal
     *
     * @param value The value to propose for consensus
     * @param proposerId ID of the node proposing this value
     * @param type Type of the proposal
     * @param priority Priority of the proposal (higher = more important)
     */
    public Proposal(String value, UUID proposerId, ProposalType type, int priority) {
        this.proposalId = System.currentTimeMillis();
        this.value = value;
        this.proposerId = proposerId;
        this.timestamp = System.currentTimeMillis();
        this.type = type;
        this.priority = priority;
    }

    /**
     * Constructor for a proposal with explicit ID
     *
     * @param proposalId Unique ID for this proposal
     * @param value The value to propose for consensus
     * @param proposerId ID of the node proposing this value
     * @param type Type of the proposal
     * @param priority Priority of the proposal (higher = more important)
     */
    public Proposal(long proposalId, String value, UUID proposerId, ProposalType type, int priority) {
        this.proposalId = proposalId;
        this.value = value;
        this.proposerId = proposerId;
        this.timestamp = System.currentTimeMillis();
        this.type = type;
        this.priority = priority;
    }

    public long getProposalId() {
        return proposalId;
    }

    public String getValue() {
        return value;
    }

    public UUID getProposerId() {
        return proposerId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public ProposalType getType() {
        return type;
    }

    public int getPriority() {
        return priority;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Proposal proposal = (Proposal) o;
        return proposalId == proposal.proposalId &&
               priority == proposal.priority &&
               Objects.equals(value, proposal.value) &&
               Objects.equals(proposerId, proposal.proposerId) &&
               type == proposal.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(proposalId, value, proposerId, type, priority);
    }

    @Override
    public String toString() {
        return "Proposal{" +
                "proposalId=" + proposalId +
                ", value='" + value + '\'' +
                ", proposerId=" + proposerId +
                ", timestamp=" + timestamp +
                ", type=" + type +
                ", priority=" + priority +
                '}';
    }
}