/*
 * Copyright (c) 2026 YAWL Foundation. All rights reserved.
 * DO NOT MODIFY THIS FILE - GENERATED CODE
 */

package org.yawlfoundation.yawl.consensus;

import java.io.Serializable;
import java.util.Objects;

/**
 * Consensus Result
 *
 * Represents the result of a consensus operation.
 * Contains the agreed-upon value and metadata about the consensus process.
 */
public class ConsensusResult implements Serializable {
    private final long proposalId;
    private final String value;
    private final long consensusTerm;
    private final boolean success;
    private final ConsensusStatus status;
    private final long consensusTimeMs;
    private final int votesFor;
    private final int votesAgainst;
    private final String errorMessage;

    /**
     * Constructor for a successful consensus result
     *
     * @param proposalId ID of the proposal that reached consensus
     * @param value The value that was agreed upon
     * @param consensusTerm The term in which consensus was reached
     * @param votesFor Number of votes in favor
     * @param votesAgainst Number of votes against
     * @param consensusTimeMs Time taken to reach consensus
     */
    public ConsensusResult(long proposalId, String value, long consensusTerm,
                          int votesFor, int votesAgainst, long consensusTimeMs) {
        this.proposalId = proposalId;
        this.value = value;
        this.consensusTerm = consensusTerm;
        this.success = true;
        this.status = ConsensusStatus.SUCCESS;
        this.consensusTimeMs = consensusTimeMs;
        this.votesFor = votesFor;
        this.votesAgainst = votesAgainst;
        this.errorMessage = null;
    }

    /**
     * Constructor for a failed consensus result
     *
     * @param proposalId ID of the proposal that failed
     * @param consensusTerm The term in which the proposal was attempted
     * @param status Status indicating why consensus failed
     * @param errorMessage Error message describing the failure
     */
    public ConsensusResult(long proposalId, long consensusTerm,
                          ConsensusStatus status, String errorMessage) {
        this.proposalId = proposalId;
        this.value = null;
        this.consensusTerm = consensusTerm;
        this.success = false;
        this.status = status;
        this.consensusTimeMs = 0;
        this.votesFor = 0;
        this.votesAgainst = 0;
        this.errorMessage = errorMessage;
    }

    /**
     * Constructor for a consensus result with explicit success flag
     *
     * @param proposalId ID of the proposal
     * @param value The agreed value (or null if failed)
     * @param consensusTerm The consensus term
     * @param success Whether consensus was successful
     * @param status Status of the consensus operation
     * @param consensusTimeMs Time taken for consensus
     * @param votesFor Number of votes for
     * @param votesAgainst Number of votes against
     * @param errorMessage Error message if failed
     */
    public ConsensusResult(long proposalId, String value, long consensusTerm,
                          boolean success, ConsensusStatus status, long consensusTimeMs,
                          int votesFor, int votesAgainst, String errorMessage) {
        this.proposalId = proposalId;
        this.value = value;
        this.consensusTerm = consensusTerm;
        this.success = success;
        this.status = status;
        this.consensusTimeMs = consensusTimeMs;
        this.votesFor = votesFor;
        this.votesAgainst = votesAgainst;
        this.errorMessage = errorMessage;
    }

    public long getProposalId() {
        return proposalId;
    }

    public String getValue() {
        return value;
    }

    public long getConsensusTerm() {
        return consensusTerm;
    }

    public boolean isSuccess() {
        return success;
    }

    public ConsensusStatus getStatus() {
        return status;
    }

    public long getConsensusTimeMs() {
        return consensusTimeMs;
    }

    public int getVotesFor() {
        return votesFor;
    }

    public int getVotesAgainst() {
        return votesAgainst;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Get the consensus ratio (votesFor / totalVotes)
     */
    public double getConsensusRatio() {
        int totalVotes = votesFor + votesAgainst;
        return totalVotes == 0 ? 0.0 : (double) votesFor / totalVotes;
    }

    /**
     * Check if consensus was achieved with supermajority (> 66%)
     */
    public boolean hasSupermajority() {
        return getConsensusRatio() > 0.66;
    }

    /**
     * Check if consensus was achieved with simple majority (> 50%)
     */
    public boolean hasMajority() {
        return getConsensusRatio() > 0.5;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConsensusResult that = (ConsensusResult) o;
        return proposalId == that.proposalId &&
               consensusTerm == that.consensusTerm &&
               success == that.success &&
               consensusTimeMs == that.consensusTimeMs &&
               votesFor == that.votesFor &&
               votesAgainst == that.votesAgainst &&
                Objects.equals(value, that.value) &&
               status == that.status &&
                Objects.equals(errorMessage, that.errorMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(proposalId, value, consensusTerm, success, status,
                          consensusTimeMs, votesFor, votesAgainst, errorMessage);
    }

    @Override
    public String toString() {
        return "ConsensusResult{" +
                "proposalId=" + proposalId +
                ", value='" + value + '\'' +
                ", consensusTerm=" + consensusTerm +
                ", success=" + success +
                ", status=" + status +
                ", consensusTimeMs=" + consensusTimeMs +
                ", votesFor=" + votesFor +
                ", votesAgainst=" + votesAgainst +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}