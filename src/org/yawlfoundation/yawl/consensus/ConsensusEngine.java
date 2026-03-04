/*
 * Copyright (c) 2026 YAWL Foundation. All rights reserved.
 * DO NOT MODIFY THIS FILE - GENERATED CODE
 */

package org.yawlfoundation.yawl.consensus;

import java.util.concurrent.CompletableFuture;

/**
 * Byzantine Consensus Engine Interface
 *
 * Provides pluggable consensus strategies with 2f+1 fault tolerance
 * and sub-100ms latency for A2A agent integration.
 */
public interface ConsensusEngine {

    /**
     * Propose a value for consensus
     *
     * @param proposal The proposal to reach consensus on
     * @return CompletableFuture<ConsensusResult> with consensus result
     */
    CompletableFuture<ConsensusResult> propose(Proposal proposal);

    /**
     * Register a consensus node in the cluster
     *
     * @param node The node to register
     */
    void registerNode(ConsensusNode node);

    /**
     * Get current consensus state
     *
     * @return ConsensusState representing current system state
     */
    ConsensusState getState();

    /**
     * Set consensus strategy
     *
     * @param strategy The consensus algorithm to use
     */
    void setStrategy(ConsensusStrategy strategy);

    /**
     * Get current consensus strategy
     *
     * @return ConsensusStrategy being used
     */
    ConsensusStrategy getStrategy();

    /**
     * Get number of nodes in consensus cluster
     *
     * @return Number of registered nodes
     */
    int getNodeCount();

    /**
     * Check if cluster can achieve quorum
     *
     * @return true if quorum is possible (2f+1 nodes)
     */
    boolean hasQuorum();

    /**
     * Get current consensus strategy
     *
     * @return ConsensusStrategy being used
     */
    ConsensusStrategy getStrategy();
}