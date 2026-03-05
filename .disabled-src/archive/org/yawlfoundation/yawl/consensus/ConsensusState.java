/*
 * Copyright (c) 2026 YAWL Foundation. All rights reserved.
 * DO NOT MODIFY THIS FILE - GENERATED CODE
 */

package org.yawlfoundation.yawl.consensus;

import java.io.Serializable;
import java.util.Objects;

/**
 * Consensus State
 *
 * Represents the current state of the consensus system.
 * Provides information about cluster health, leadership, and performance.
 */
public class ConsensusState implements Serializable {
    private final long currentTerm;
    private final NodeRole role;
    private final UUID leaderId;
    private final int totalNodes;
    private final boolean hasQuorum;
    private final int activeProposals;
    private final int pendingProposals;
    private final double averageLatencyMs;
    private final ConsensusStrategy strategy;
    private final long uptimeMs;
    private final int successfulProposals;
    private final int failedProposals;

    /**
     * Constructor for consensus state
     *
     * @param currentTerm Current consensus term
     * @param role Role of this node in the cluster
     * @param leaderId ID of the current leader (null if none)
     * @param totalNodes Total number of nodes in the cluster
     * @param hasQuorum Whether the cluster has sufficient nodes for quorum
     * @param activeProposals Number of currently active proposals
     * @param pendingProposals Number of pending proposals
     * @param averageLatencyMs Average consensus latency in milliseconds
     * @param strategy Current consensus strategy being used
     * @param uptimeMs Uptime of the consensus system in milliseconds
     * @param successfulProposals Number of successful proposals
     * @param failedProposals Number of failed proposals
     */
    public ConsensusState(long currentTerm, NodeRole role, UUID leaderId,
                        int totalNodes, boolean hasQuorum, int activeProposals,
                        int pendingProposals, double averageLatencyMs,
                        ConsensusStrategy strategy, long uptimeMs,
                        int successfulProposals, int failedProposals) {
        this.currentTerm = currentTerm;
        this.role = role;
        this.leaderId = leaderId;
        this.totalNodes = totalNodes;
        this.hasQuorum = hasQuorum;
        this.activeProposals = activeProposals;
        this.pendingProposals = pendingProposals;
        this.averageLatencyMs = averageLatencyMs;
        this.strategy = strategy;
        this.uptimeMs = uptimeMs;
        this.successfulProposals = successfulProposals;
        this.failedProposals = failedProposals;
    }

    // Simplified constructor for basic state
    public ConsensusState(long currentTerm, NodeRole role, UUID leaderId,
                        int totalNodes, boolean hasQuorum, int activeProposals,
                        int pendingProposals) {
        this(currentTerm, role, leaderId, totalNodes, hasQuorum,
             activeProposals, pendingProposals, 0.0, null, 0, 0, 0);
    }

    public long getCurrentTerm() {
        return currentTerm;
    }

    public NodeRole getRole() {
        return role;
    }

    public UUID getLeaderId() {
        return leaderId;
    }

    public int getTotalNodes() {
        return totalNodes;
    }

    public boolean hasQuorum() {
        return hasQuorum;
    }

    public int getActiveProposals() {
        return activeProposals;
    }

    public int getPendingProposals() {
        return pendingProposals;
    }

    public double getAverageLatencyMs() {
        return averageLatencyMs;
    }

    public ConsensusStrategy getStrategy() {
        return strategy;
    }

    public long getUptimeMs() {
        return uptimeMs;
    }

    public int getSuccessfulProposals() {
        return successfulProposals;
    }

    public int getFailedProposals() {
        return failedProposals;
    }

    /**
     * Get success rate (successful / total proposals)
     */
    public double getSuccessRate() {
        int total = successfulProposals + failedProposals;
        return total == 0 ? 1.0 : (double) successfulProposals / total;
    }

    /**
     * Get total proposals (successful + failed)
     */
    public int getTotalProposals() {
        return successfulProposals + failedProposals;
    }

    /**
     * Check if the system is healthy
     */
    public boolean isHealthy() {
        return hasQuorum && role != null && (leaderId != null || role == NodeRole.CANDIDATE);
    }

    /**
     * Check if consensus latency is within target (<100ms)
     */
    public boolean isLatencyHealthy() {
        return averageLatencyMs < 100.0;
    }

    /**
     * Get cluster health percentage
     */
    public int getHealthPercentage() {
        int healthScore = 0;

        if (hasQuorum) healthScore += 40;
        if (isHealthy()) healthScore += 30;
        if (isLatencyHealthy()) healthScore += 20;
        if (getSuccessRate() > 0.9) healthScore += 10;

        return Math.min(healthScore, 100);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConsensusState that = (ConsensusState) o;
        return currentTerm == that.currentTerm &&
                totalNodes == that.totalNodes &&
                hasQuorum == that.hasQuorum &&
                activeProposals == that.activeProposals &&
                pendingProposals == that.pendingProposals &&
                Double.compare(that.averageLatencyMs, averageLatencyMs) == 0 &&
                uptimeMs == that.uptimeMs &&
                successfulProposals == that.successfulProposals &&
                failedProposals == that.failedProposals &&
                role == that.role &&
                Objects.equals(leaderId, that.leaderId) &&
                strategy == that.strategy;
    }

    @Override
    public int hashCode() {
        return Objects.hash(currentTerm, role, leaderId, totalNodes, hasQuorum,
                          activeProposals, pendingProposals, averageLatencyMs,
                          strategy, uptimeMs, successfulProposals, failedProposals);
    }

    @Override
    public String toString() {
        return "ConsensusState{" +
                "currentTerm=" + currentTerm +
                ", role=" + role +
                ", leaderId=" + leaderId +
                ", totalNodes=" + totalNodes +
                ", hasQuorum=" + hasQuorum +
                ", activeProposals=" + activeProposals +
                ", pendingProposals=" + pendingProposals +
                ", averageLatencyMs=" + averageLatencyMs +
                ", strategy=" + strategy +
                ", uptimeMs=" + uptimeMs +
                ", successfulProposals=" + successfulProposals +
                ", failedProposals=" + failedProposals +
                '}';
    }
}