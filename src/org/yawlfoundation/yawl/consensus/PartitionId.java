/*
 * Copyright (c) 2026 YAWL Foundation. All rights reserved.
 * DO NOT MODIFY THIS FILE - GENERATED CODE
 */

package org.yawlfoundation.yawl.consensus;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Partition Identifier
 *
 * Represents a network partition in the consensus cluster.
 * Used to handle split-brain scenarios and maintain consistency.
 */
public class PartitionId implements Serializable {
    private final UUID partitionId;
    private final long timestamp;
    private final String partitionName;
    private final int memberCount;
    private final boolean hasLeader;

    /**
     * Constructor for a partition ID
     *
     * @param partitionName Name of the partition
     * @param memberCount Number of nodes in this partition
     * @param hasLeader Whether this partition has a leader
     */
    public PartitionId(String partitionName, int memberCount, boolean hasLeader) {
        this.partitionId = UUID.randomUUID();
        this.timestamp = System.currentTimeMillis();
        this.partitionName = partitionName;
        this.memberCount = memberCount;
        this.hasLeader = hasLeader;
    }

    /**
     * Constructor for a partition with explicit ID
     *
     * @param partitionId Unique ID for this partition
     * @param partitionName Name of the partition
     * @param memberCount Number of nodes in this partition
     * @param hasLeader Whether this partition has a leader
     */
    public PartitionId(UUID partitionId, String partitionName, int memberCount, boolean hasLeader) {
        this.partitionId = partitionId;
        this.timestamp = System.currentTimeMillis();
        this.partitionName = partitionName;
        this.memberCount = memberCount;
        this.hasLeader = hasLeader;
    }

    public UUID getPartitionId() {
        return partitionId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getPartitionName() {
        return partitionName;
    }

    public int getMemberCount() {
        return memberCount;
    }

    public boolean hasLeader() {
        return hasLeader;
    }

    /**
     * Check if this partition can achieve quorum
     */
    public boolean canAchieveQuorum(int totalNodes) {
        // Simple majority check
        return memberCount > totalNodes / 2;
    }

    /**
     * Check if this partition is the majority partition
     */
    public boolean isMajority(int totalNodes) {
        return memberCount > totalNodes / 2;
    }

    /**
     * Get the age of this partition in milliseconds
     */
    public long getAgeMs() {
        return System.currentTimeMillis() - timestamp;
    }

    /**
     * Check if this partition is stale
     */
    public boolean isStale() {
        return getAgeMs() > 30000; // 30 seconds
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PartitionId that = (PartitionId) o;
        return memberCount == that.memberCount &&
                hasLeader == that.hasLeader &&
                Objects.equals(partitionId, that.partitionId) &&
                Objects.equals(partitionName, that.partitionName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(partitionId, partitionName, memberCount, hasLeader);
    }

    @Override
    public String toString() {
        return "PartitionId{" +
                "partitionId=" + partitionId +
                ", partitionName='" + partitionName + '\'' +
                ", memberCount=" + memberCount +
                ", hasLeader=" + hasLeader +
                ", timestamp=" + timestamp +
                '}';
    }
}