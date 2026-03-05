/*
 * Copyright (c) 2026 YAWL Foundation. All rights reserved.
 * DO NOT MODIFY THIS FILE - GENERATED CODE
 */

package org.yawlfoundation.yawl.consensus;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Heartbeat Message
 *
 * Represents a heartbeat message from the leader to followers.
 * Used to maintain leadership and synchronize cluster state.
 */
public class Heartbeat implements Serializable {
    private final long term;
    private final UUID leaderId;
    private final long timestamp;
    private final int leaderCommitIndex;

    /**
     * Constructor for a heartbeat message
     *
     * @param term The current term
     * @param leaderId ID of the leader sending the heartbeat
     */
    public Heartbeat(long term, UUID leaderId) {
        this.term = term;
        this.leaderId = leaderId;
        this.timestamp = System.currentTimeMillis();
        this.leaderCommitIndex = 0; // Default until committed entries are tracked
    }

    /**
     * Constructor for a heartbeat with commit index
     *
     * @param term The current term
     * @param leaderId ID of the leader sending the heartbeat
     * @param leaderCommitIndex The leader's commit index
     */
    public Heartbeat(long term, UUID leaderId, int leaderCommitIndex) {
        this.term = term;
        this.leaderId = leaderId;
        this.timestamp = System.currentTimeMillis();
        this.leaderCommitIndex = leaderCommitIndex;
    }

    public long getTerm() {
        return term;
    }

    public UUID getLeaderId() {
        return leaderId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getLeaderCommitIndex() {
        return leaderCommitIndex;
    }

    /**
     * Check if this heartbeat is stale
     */
    public boolean isStale() {
        return System.currentTimeMillis() - timestamp > 1000; // 1 second threshold
    }

    /**
     * Check if the leader is still in the same term
     */
    public boolean isCurrentTerm() {
        return term == System.currentTimeMillis() / 1000; // Approximate current term
    }

    /**
     * Get the age of this heartbeat in milliseconds
     */
    public long getAgeMs() {
        return System.currentTimeMillis() - timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Heartbeat heartbeat = (Heartbeat) o;
        return term == heartbeat.term &&
                leaderCommitIndex == heartbeat.leaderCommitIndex &&
                Objects.equals(leaderId, heartbeat.leaderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(term, leaderId, leaderCommitIndex);
    }

    @Override
    public String toString() {
        return "Heartbeat{" +
                "term=" + term +
                ", leaderId=" + leaderId +
                ", timestamp=" + timestamp +
                ", leaderCommitIndex=" + leaderCommitIndex +
                '}';
    }
}