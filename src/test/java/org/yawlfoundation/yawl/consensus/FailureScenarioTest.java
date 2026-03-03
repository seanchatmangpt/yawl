/*
 * Copyright (c) 2026 YAWL Foundation. All rights reserved.
 * DO NOT MODIFY THIS FILE - GENERATED CODE
 */

package org.yawlfoundation.yawl.consensus;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test suite for failure scenarios in consensus system
 */
@ExtendWith(MockitoExtension.class)
class FailureScenarioTest {
    private ConsensusEngine consensusEngine;
    private ConsensusNode node1;
    private ConsensusNode node2;
    private ConsensusNode node3;

    @BeforeEach
    void setUp() {
        consensusEngine = new RaftConsensus();
        node1 = new ConsensusNodeImpl("node1:8080", consensusEngine);
        node2 = new ConsensusNodeImpl("node2:8080", consensusEngine);
        node3 = new ConsensusNodeImpl("node3:8080", consensusEngine);

        // Setup cluster
        consensusEngine.registerNode(node1);
        consensusEngine.registerNode(node2);
        consensusEngine.registerNode(node3);
    }

    @Test
    void testLeaderFailureDuringConsensus() throws Exception {
        // Create proposal
        Proposal proposal = new Proposal("test-value", node1.getId(), ProposalType.WORKFLOW_STATE, 1);

        // Start consensus process
        CompletableFuture<ConsensusResult> future = consensusEngine.propose(proposal);

        // Simulate leader failure by stepping down leader
        // In real implementation, this would be detected via heartbeat timeout
        ((ConsensusNodeImpl) node1).stepDown();

        // New leader should be elected and consensus should continue
        ConsensusResult result = future.get(2, TimeUnit.SECONDS);

        assertTrue(result.isSuccess());
        assertEquals("test-value", result.getValue());
    }

    @Test
    void testNetworkPartitionScenario() throws Exception {
        // Simulate network partition by deactivating one node
        ((ConsensusNodeImpl) node3).active = false;

        Proposal proposal = new Proposal("test-value", node1.getId(), ProposalType.WORKFLOW_STATE, 1);

        // Should still work with remaining 2 nodes (majority)
        CompletableFuture<ConsensusResult> future = consensusEngine.propose(proposal);
        ConsensusResult result = future.get(1, TimeUnit.SECONDS);

        assertTrue(result.isSuccess());
    }

    @Test
    void testSplitBrainScenario() throws Exception {
        // Create two partitions
        ((ConsensusNodeImpl) node1).active = true;
        ((ConsensusNodeImpl) node2).active = true;
        ((ConsensusNodeImpl) node3).active = false; // Node 3 partitioned

        // First partition (nodes 1,2) should achieve consensus
        Proposal proposal1 = new Proposal("partition1-value", node1.getId(), ProposalType.WORKFLOW_STATE, 1);
        CompletableFuture<ConsensusResult> result1 = consensusEngine.propose(proposal1);
        assertTrue(result1.get(1, TimeUnit.SECONDS).isSuccess());

        // After network recovery, the system should detect inconsistency
        // and resolve it (this is a simplified test - real implementation would be more complex)
        ((ConsensusNodeImpl) node3).active = true; // Restore network

        // New proposal should reach consensus across all nodes
        Proposal proposal2 = new Proposal("recovered-value", node1.getId(), ProposalType.WORKFLOW_STATE, 1);
        CompletableFuture<ConsensusResult> result2 = consensusEngine.propose(proposal2);
        assertTrue(result2.get(1, TimeUnit.SECONDS).isSuccess());
    }

    @Test
    void testMultipleNodeFailures() throws Exception {
        // Fail two nodes, leaving only one
        ((ConsensusNodeImpl) node2).active = false;
        ((ConsensusNodeImpl) node3).active = false;

        Proposal proposal = new Proposal("test-value", node1.getId(), ProposalType.WORKFLOW_STATE, 1);

        // Should fail due to no quorum
        CompletableFuture<ConsensusResult> future = consensusEngine.propose(proposal);
        ConsensusResult result = future.get(1, TimeUnit.SECONDS);

        assertFalse(result.isSuccess());
        assertEquals(ConsensusStatus.INSUFFICIENT_NODES, result.getStatus());
    }

    @Test
    void testRecoveryAfterFailure() throws Exception {
        // Fail a node
        ((ConsensusNodeImpl) node3).active = false;

        // Should still work
        Proposal proposal1 = new Proposal("value1", node1.getId(), ProposalType.WORKFLOW_STATE, 1);
        CompletableFuture<ConsensusResult> result1 = consensusEngine.propose(proposal1);
        assertTrue(result1.get(1, TimeUnit.SECONDS).isSuccess());

        // Recover the node
        ((ConsensusNodeImpl) node3).active = true;

        // Should work again
        Proposal proposal2 = new Proposal("value2", node1.getId(), ProposalType.WORKFLOW_STATE, 1);
        CompletableFuture<ConsensusResult> result2 = consensusEngine.propose(proposal2);
        assertTrue(result2.get(1, TimeUnit.SECONDS).isSuccess());
    }

    @Test
    void testConsistencyAfterRecovery() throws Exception {
        // Run consensus while all nodes are active
        Proposal proposal1 = new Proposal("initial-value", node1.getId(), ProposalType.WORKFLOW_STATE, 1);
        CompletableFuture<ConsensusResult> result1 = consensusEngine.propose(proposal1);
        assertTrue(result1.get(1, TimeUnit.SECONDS).isSuccess());

        // Fail node 3
        ((ConsensusNodeImpl) node3).active = false;

        // Run more consensus
        Proposal proposal2 = new Proposal("partition-value", node1.getId(), ProposalType.WORKFLOW_STATE, 1);
        CompletableFuture<ConsensusResult> result2 = consensusEngine.propose(proposal2);
        assertTrue(result2.get(1, TimeUnit.SECONDS).isSuccess());

        // Recover node 3
        ((ConsensusNodeImpl) node3).active = true;

        // Node 3 should catch up with latest consensus
        Proposal proposal3 = new Proposal("recovered-value", node1.getId(), ProposalType.WORKFLOW_STATE, 1);
        CompletableFuture<ConsensusResult> result3 = consensusEngine.propose(proposal3);
        assertTrue(result3.get(1, TimeUnit.SECONDS).isSuccess());
    }

    @Test
    void testSlowNodeRecovery() throws Exception {
        // Create a slow node
        ConsensusNode slowNode = new ConsensusNode("slow-node:8080", consensusEngine) {
            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public CompletableFuture<ConsensusResult> handleProposal(Proposal proposal) {
                // Simulate slow processing
                try {
                    Thread.sleep(200); // 200ms delay
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return CompletableFuture.completedFuture(
                    new ConsensusResult(
                        proposal.getProposalId(),
                        proposal.getValue(),
                        1,
                        true,
                        ConsensusStatus.SUCCESS,
                        200,
                        2, 0, null
                    )
                );
            }
        };

        consensusEngine.registerNode(slowNode);

        // Should still achieve consensus despite slow node
        Proposal proposal = new Proposal("test-value", node1.getId(), ProposalType.WORKFLOW_STATE, 1);
        CompletableFuture<ConsensusResult> future = consensusEngine.propose(proposal);

        // May take longer due to slow node, but should still succeed
        ConsensusResult result = future.get(1, TimeUnit.SECONDS);
        assertTrue(result.isSuccess());
    }

    @Test
    void testConsensusWithMalformedProposal() throws Exception {
        // Create null proposal
        Proposal nullProposal = new Proposal(null, node1.getId(), ProposalType.WORKFLOW_STATE, 1);

        CompletableFuture<ConsensusResult> future = consensusEngine.propose(nullProposal);
        ConsensusResult result = future.get(1, TimeUnit.SECONDS);

        assertFalse(result.isSuccess());
        assertEquals(ConsensusStatus.INVALID_PROPOSAL, result.getStatus());
    }

    @AfterEach
    void tearDown() {
        if (consensusEngine instanceof RaftConsensus) {
            ((RaftConsensus) consensusEngine).shutdown();
        }
    }
}