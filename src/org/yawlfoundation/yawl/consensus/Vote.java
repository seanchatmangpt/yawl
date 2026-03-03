/*
 * Copyright (c) 2026 YAWL Foundation. All rights reserved.
 * DO NOT MODIFY THIS FILE - GENERATED CODE
 */

package org.yawlfoundation.yawl.consensus;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Vote Message
 *
 * Represents a vote in a consensus election.
 * Used during leader election processes.
 */
public class Vote implements Serializable {
    private final long term;
    private final UUID candidateId;
    private final UUID voterId;
    private final boolean voteGranted;
    private final long timestamp;

    /**
     * Constructor for a vote request
     *
     * @param term The election term
     * @param candidateId ID of the candidate requesting the vote
     * @param voterId ID of the voter casting the vote
     */
    public Vote(long term, UUID candidateId, UUID voterId) {
        this.term = term;
        this.candidateId = candidateId;
        this.voterId = voterId;
        this.voteGranted = true; // Assume granted until rejected
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Constructor for a vote response
     *
     * @param term The election term
     * @param candidateId ID of the candidate
     * @param voterId ID of the voter
     * @param voteGranted Whether the vote was granted
     */
    public Vote(long term, UUID candidateId, UUID voterId, boolean voteGranted) {
        this.term = term;
        this.candidateId = candidateId;
        this.voterId = voterId;
        this.voteGranted = voteGranted;
        this.timestamp = System.currentTimeMillis();
    }

    public long getTerm() {
        return term;
    }

    public UUID getCandidateId() {
        return candidateId;
    }

    public UUID getVoterId() {
        return voterId;
    }

    public boolean isVoteGranted() {
        return voteGranted;
    }

    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Check if this vote is for the current term
     */
    public boolean isCurrentTerm() {
        return term == System.currentTimeMillis() / 1000; // Approximate current term
    }

    /**
     * Check if the voter has already voted in this term
     */
    public boolean hasAlreadyVoted() {
        return System.currentTimeMillis() - timestamp < 5000; // 5 second window
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Vote vote = (Vote) o;
        return term == vote.term &&
                voteGranted == vote.voteGranted &&
                Objects.equals(candidateId, vote.candidateId) &&
                Objects.equals(voterId, vote.voterId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(term, candidateId, voterId, voteGranted);
    }

    @Override
    public String toString() {
        return "Vote{" +
                "term=" + term +
                ", candidateId=" + candidateId +
                ", voterId=" + voterId +
                ", voteGranted=" + voteGranted +
                ", timestamp=" + timestamp +
                '}';
    }
}