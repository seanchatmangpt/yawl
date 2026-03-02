/*
 * Copyright (c) 2026 YAWL Foundation. All rights reserved.
 * DO NOT MODIFY THIS FILE - GENERATED CODE
 */

package org.yawlfoundation.yawl.consensus;

/**
 * Consensus Strategy Enumeration
 *
 * Defines available consensus algorithms with different properties:
 * - RAFT: Leader-based, linearizable ordering
 * - PAXOS: Quorum-based, strong consistency
 * - PBFT: Byzantine fault tolerant, handles malicious nodes
 */
public enum ConsensusStrategy {
    /**
     * Raft Consensus Algorithm
     *
     * Leader-based consensus with strong consistency guarantees.
     * Linearizable ordering and <100ms latency.
     * Suitable for crash faults only.
     */
    RAFT("Leader-based", "Linearizable", "Crash faults", "<100ms", "Strong consistency"),

    /**
     * Paxos Consensus Algorithm
     *
     * Quorum-based consensus with strong consistency.
     * Multi-leader support, handles network partitions.
     * Suitable for crash faults only.
     */
    PAXOS("Quorum-based", "Strong consistency", "Crash faults", "<200ms", "Multi-leader"),

    /**
     * Practical Byzantine Fault Tolerance (PBFT)
     *
     * Byzantine fault tolerant consensus.
     * Handles up to f malicious nodes in 3f+1 cluster.
     * Slower but secure against malicious behavior.
     */
    PBFT("Byzantine tolerant", "State machine", "Byzantine faults", "<500ms", "Malicious nodes");

    private final String description;
    private final String consistency;
    private final String faultTolerance;
    private final String latency;
    private final String useCase;

    ConsensusStrategy(String description, String consistency, String faultTolerance,
                     String latency, String useCase) {
        this.description = description;
        this.consistency = consistency;
        this.faultTolerance = faultTolerance;
        this.latency = latency;
        this.useCase = useCase;
    }

    public String getDescription() {
        return description;
    }

    public String getConsistency() {
        return consistency;
    }

    public String getFaultTolerance() {
        return faultTolerance;
    }

    public String getLatency() {
        return latency;
    }

    public String getUseCase() {
        return useCase;
    }

    /**
     * Maximum number of faults tolerated (f)
     */
    public int getMaxFaults(int totalNodes) {
        switch (this) {
            case RAFT:
            case PAXOS:
                // 2f+1 nodes tolerate f crashes
                return (totalNodes - 1) / 2;
            case PBFT:
                // 3f+1 nodes tolerate f Byzantine faults
                return (totalNodes - 1) / 3;
            default:
                return 0;
        }
    }

    /**
     * Minimum nodes required for quorum
     */
    public int getMinQuorum(int totalNodes) {
        switch (this) {
            case RAFT:
            case PAXOS:
                // Majority: floor(n/2) + 1
                return (totalNodes / 2) + 1;
            case PBFT:
                // 2f+1 (majority of correct nodes)
                return totalNodes - getMaxFaults(totalNodes);
            default:
                return totalNodes;
        }
    }
}