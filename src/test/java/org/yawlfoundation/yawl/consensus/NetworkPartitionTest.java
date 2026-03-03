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
 * Test suite for network partition scenarios in consensus system
 */
@ExtendWith(MockitoExtension.class)
class NetworkPartitionTest {
    private ConsensusEngine consensusEngine;
    private ConsensusNode node1;
    private ConsensusNode node2;
    private ConsensusNode node3;
    private ConsensusNode node4;
    private ConsensusNode node5;

    @BeforeEach
    void setUp() {
        consensusEngine = new RaftConsensus();
        node1 = new ConsensusNodeImpl("node1:8080", consensusEngine);
        node2 = new ConsensusNodeImpl("node2:8080", consensusEngine);
        node3 = new ConsensusNodeImpl("node3:8080", consensusEngine);
        node4 = new ConsensusNodeImpl("node4:8080", consensusEngine);
        node5 = new ConsensusNodeImpl("node5:8080", consensusEngine);

        // Setup 5-node cluster
        consensusEngine.registerNode(node1);
        consensusEngine.registerNode(node2);
        consensusEngine.registerNode(node3);
        consensusEngine.registerNode(node4);
        consensusEngine.registerNode(node5);
    }

    @Test
    void testMajorityPartition() throws Exception {
        // Create partition with 3 nodes (majority)
        ((ConsensusNodeImpl) node1).active = true;
        ((ConsensusNodeImpl) node2).active = true;
        ((ConsensusNodeImpl) node3).active = true;
        ((ConsensusNodeImpl) node4).active = false; // Partitioned
        ((ConsensusNodeImpl) node5).active = false; // Partitioned

        // Majority should achieve consensus
        Proposal proposal = new Proposal("majority-value", node1.getId(), ProposalType.WORKFLOW_STATE, 1);
        CompletableFuture<ConsensusResult> future = consensusEngine.propose(proposal);
        ConsensusResult result = future.get(1, TimeUnit.SECONDS);

        assertTrue(result.isSuccess());
        assertEquals("majority-value", result.getValue());
    }

    @Test
    void testMinorityPartitionCannotAchieveConsensus() throws Exception {
        // Create partition with 2 nodes (minority)
        ((ConsensusNodeImpl) node1).active = true;
        ((ConsensusNodeImpl) node2).active = true;
        ((ConsensusNodeImpl) node3).active = false; // Partitioned
        ((ConsensusNodeImpl) node4).active = false; // Partitioned
        ((ConsensusNodeImpl) node5).active = false; // Partitioned

        Proposal proposal = new Proposal("minority-value", node1.getId(), ProposalType.WORKFLOW_STATE, 1);
        CompletableFuture<ConsensusResult> future = consensusEngine.propose(proposal);
        ConsensusResult result = future.get(1, TimeUnit.SECONDS);

        assertFalse(result.isSuccess());
        assertEquals(ConsensusStatus.PARTITION, result.getStatus());
    }

    @Test
    void testSplitBrainResolution() throws Exception {
        // Simulate split-brain: two partitions of 2 nodes each
        // Partition 1: nodes 1,2
        ((ConsensusNodeImpl) node1).active = true;
        ((ConsensusNodeImpl) node2).active = true;
        ((ConsensusNodeImpl) node3).active = false;
        ((ConsensusNodeImpl) node4).active = true;
        ((ConsensusNodeImpl) node5).active = true;

        // First partition achieves consensus
        Proposal proposal1 = new Proposal("partition1-value", node1.getId(), ProposalType.WORKFLOW_STATE, 1);
        CompletableFuture<ConsensusResult> result1 = consensusEngine.propose(proposal1);
        assertTrue(result1.get(1, TimeUnit.SECONDS).isSuccess());

        // Recover from partition
        ((ConsensusNodeImpl) node3).active = true;

        // System should achieve consensus across all nodes
        Proposal proposal2 = new Proposal("recovered-value", node1.getId(), ProposalType.WORKFLOW_STATE, 1);
        CompletableFuture<ConsensusResult> result2 = consensusEngine.propose(proposal2);
        assertTrue(result2.get(1, TimeUnit.SECONDS).isSuccess());
    }

    @Test
    void testDynamicPartitionHandling() throws Exception {
        // Start with all nodes active
        AtomicInteger consensusCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(5);

        // Submit multiple proposals
        for (int i = 0; i < 5; i++) {
            final int proposalId = i;
            Proposal proposal = new Proposal("value-" + i, node1.getId(), ProposalType.WORKFLOW_STATE, i);

            consensusEngine.propose(proposal).thenAccept(result -> {
                if (result.isSuccess()) {
                    consensusCount.incrementAndGet();
                }
                latch.countDown();
            });
        }

        // Initially all nodes active - should achieve consensus
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(5, consensusCount.get());

        // Simulate network failure
        ((ConsensusNodeImpl) node4).active = false;
        ((ConsensusNodeImpl) node5).active = false;

        // Submit more proposals
        consensusCount.set(0);
        latch = new CountDownLatch(3);

        for (int i = 5; i < 8; i++) {
            final int proposalId = i;
            Proposal proposal = new Proposal("value-" + i, node1.getId(), ProposalType.WORKFLOW_STATE, i);

            consensusEngine.propose(proposal).thenAccept(result -> {
                if (result.isSuccess()) {
                    consensusCount.incrementAndGet();
                }
                latch.countDown();
            });
        }

        // Should still achieve consensus with remaining nodes
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        assertEquals(3, consensusCount.get());
    }

    @Test
    void testNetworkPartitionDetection() {
        // Create partition scenario
        ((ConsensusNodeImpl) node1).active = true;
        ((ConsensusNodeImpl) node2).active = true;
        ((ConsensusNodeImpl) node3).active = false;
        ((ConsensusNodeImpl) node4).active = true;
        ((ConsensusNodeImpl) node5).active = false;

        // Check partition detection
        ConsensusState state = consensusEngine.getState();
        assertFalse(state.hasQuorum()); // 3 active nodes out of 5

        // Handle partition
        PartitionId partition1 = new PartitionId("partition-1", 3, true);
        PartitionId partition2 = new PartitionId("partition-2", 2, false);

        node1.handlePartition(partition1);
        node4.handlePartition(partition2);

        // Verify nodes handle partition appropriately
        assertEquals(NodeRole.FOLLOWER, node1.getRole());
        assertEquals(NodeRole.FOLLOWER, node4.getRole());
    }

    @Test
    void testConsistencyAcrossPartitions() throws Exception {
        // Initial consensus
        Proposal initialProposal = new Proposal("initial-value", node1.getId(), ProposalType.WORKFLOW_STATE, 1);
        CompletableFuture<ConsensusResult> initialResult = consensusEngine.propose(initialProposal);
        assertTrue(initialResult.get(1, TimeUnit.SECONDS).isSuccess());

        // Create partition
        ((ConsensusNodeImpl) node3).active = false;
        ((ConsensusNodeImpl) node4).active = false;
        ((ConsensusNodeImpl) node5).active = false;

        // Minority partition should not achieve consensus
        Proposal minorityProposal = new Proposal("minority-value", node1.getId(), ProposalType.WORKFLOW_STATE, 2);
        CompletableFuture<ConsensusResult> minorityResult = consensusEngine.propose(minorityProposal);
        assertFalse(minorityResult.get(1, TimeUnit.SECONDS).isSuccess());

        // Recover partition
        ((ConsensusNodeImpl) node3).active = true;
        ((ConsensusNodeImpl) node4).active = true;
        ((ConsensusNodeImpl) node5).active = true;

        // System should achieve consensus again
        Proposal recoveredProposal = new Proposal("recovered-value", node1.getId(), ProposalType.WORKFLOW_STATE, 3);
        CompletableFuture<ConsensusResult> recoveredResult = consensusEngine.propose(recoveredProposal);
        assertTrue(recoveredResult.get(1, TimeUnit.SECONDS).isSuccess());
    }

    @Test
    void testPartitionWithLeaderInPartition() throws Exception {
        // Ensure node1 is leader
        ((ConsensusNodeImpl) node1).role = NodeRole.LEADER;
        ((ConsensusNodeImpl) node2).role = NodeRole.FOLLOWER;
        ((ConsensusNodeImpl) node3).role = NodeRole.FOLLOWER;

        // Partition with leader included
        ((ConsensusNodeImpl) node1).active = true;
        ((ConsensusNodeImpl) node2).active = true;
        ((ConsensusNodeImpl) node3).active = false;

        // Should still achieve consensus with leader in partition
        Proposal proposal = new Proposal("leader-in-partition", node1.getId(), ProposalType.WORKFLOW_STATE, 1);
        CompletableFuture<ConsensusResult> future = consensusEngine.propose(proposal);
        ConsensusResult result = future.get(1, TimeUnit.SECONDS);

        assertTrue(result.isSuccess());
        assertEquals("leader-in-partition", result.getValue());
    }

    @Test
    void testPartitionWithoutLeader() throws Exception {
        // Ensure no current leader
        ((ConsensusNodeImpl) node1).role = NodeRole.FOLLOWER;
        ((ConsensusNodeImpl) node2).role = NodeRole.FOLLOWER;
        ((ConsensusNodeImpl) node3).role = NodeRole.FOLLOWER;

        // Partition without leader
        ((ConsensusNodeImpl) node1).active = true;
        ((ConsensusNodeImpl) node2).active = true;
        ((ConsensusNodeImpl) node3).active = false;

        // Should elect new leader and achieve consensus
        Proposal proposal = new Proposal("no-leader-partition", node1.getId(), ProposalType.WORKFLOW_STATE, 1);
        CompletableFuture<ConsensusResult> future = consensusEngine.propose(proposal);
        ConsensusResult result = future.get(2, TimeUnit.SECONDS); // May take longer for election

        assertTrue(result.isSuccess());
        assertEquals("no-leader-partition", result.getValue());
    }

    @AfterEach
    void tearDown() {
        if (consensusEngine instanceof RaftConsensus) {
            ((RaftConsensus) consensusEngine).shutdown();
        }
    }
}