/*
 * Copyright (c) 2026 YAWL Foundation. All rights reserved.
 * DO NOT MODIFY THIS FILE - GENERATED CODE
 */

/**
 * Byzantine Consensus Framework for YAWL
 *
 * This package provides pluggable consensus algorithms for distributed YAWL workflows,
 * including Raft, Paxos, and PBFT implementations with Byzantine fault tolerance.
 *
 * Key Features:
 * - Pluggable consensus strategies (RAFT, PAXOS, PBFT)
 * - 2f+1 nodes tolerate f failures
 * - Sub-100ms consensus latency
 * - Seamless A2A agent integration
 * - Byzantine fault tolerance (PBFT)
 * - Automatic leader election and failover
 *
 * Usage Example:
 * <pre>{@code
 * // Create consensus engine
 * ConsensusEngine engine = new RaftConsensus();
 *
 * // Register nodes
 * ConsensusNode node1 = new ConsensusNodeImpl("node1:8080", engine);
 * engine.registerNode(node1);
 * engine.registerNode(node2);
 * engine.registerNode(node3);
 *
 * // Propose value
 * Proposal proposal = new Proposal("workflow-state", node1.getId(),
 *                               ProposalType.WORKFLOW_STATE, 1);
 *
 * CompletableFuture<ConsensusResult> future = engine.propose(proposal);
 * }</pre>
 *
 * @see org.yawlfoundation.yawl.consensus.ConsensusEngine
 * @see org.yawlfoundation.yawl.consensus.ConsensusNode
 * @see org.yawlfoundation.yawl.consensus.ConsensusStrategy
 * @see org.yawlfoundation.yawl.consensus.A2AConsensusIntegration
 */
package org.yawlfoundation.yawl.consensus;