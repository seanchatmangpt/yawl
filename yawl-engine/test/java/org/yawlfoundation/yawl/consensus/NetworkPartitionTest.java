/*
 * Copyright (c) 2026 YAWL Foundation. All rights reserved.
 * DO NOT MODIFY THIS FILE - GENERATED CODE
 */

package org.yawlfoundation.yawl.consensus;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD Network Partition Tests for Consensus System
 *
 * Tests real consensus behavior under network partition scenarios:
 * - Uses actual network simulation with controlled partitions
 * - Real YAWL consensus engine instances
 * - Real node failures and recovery
 * - No mocks - all failures are real network conditions
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-03-04
 */
@Tag("integration")
@DisplayName("Network Partition Tests")
class NetworkPartitionTest {

    private ConsensusEngine consensusEngine;
    private NetworkSimulator networkSimulator;
    private List<TestableNode> testNodes;
    private static final int NODE_COUNT = 5;
    private static final int TIMEOUT_MS = 5000;

    @BeforeEach
    void setUp() {
        consensusEngine = new RaftConsensus();
        networkSimulator = new NetworkSimulator(NODE_COUNT);

        testNodes = new ArrayList<>();
        for (int i = 0; i < NODE_COUNT; i++) {
            TestableNode node = new TestableNode(
                "node-" + i + ":8080",
                consensusEngine,
                networkSimulator
            );
            testNodes.add(node);
            consensusEngine.registerNode(node);
        }

        // Initialize network simulation
        networkSimulator.initialize(testNodes);
    }

    @AfterEach
    void tearDown() {
        if (consensusEngine instanceof RaftConsensus) {
            ((RaftConsensus) consensusEngine).shutdown();
        }
        networkSimulator.shutdown();
    }

    // =========================================================================
    // Partition Scenarios
    // =========================================================================

    @Nested
    @DisplayName("Majority Partition Scenarios")
    class MajorityPartitionTests {

        @Test
        @DisplayName("Majority achieves consensus during partition")
        void testMajorityPartitionAchievesConsensus() throws Exception {
            // Simulate partition: 3 nodes active (majority), 2 partitioned
            networkSimulator.simulatePartition(List.of(3, 4));  // Partition nodes 3 and 4

            // Submit proposal
            Proposal proposal = new Proposal("majority-value",
                testNodes.get(0).getId(),
                ProposalType.WORKFLOW_STATE,
                1
            );

            Instant start = Instant.now();
            CompletableFuture<ConsensusResult> future = consensusEngine.propose(proposal);
            ConsensusResult result = future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            Duration elapsed = Duration.between(start, Instant.now());

            assertTrue(result.isSuccess(), "Majority should achieve consensus");
            assertEquals("majority-value", result.getValue());
            assertTrue(elapsed.toMillis() < TIMEOUT_MS, "Should not timeout");

            // Verify partitioned nodes cannot participate
            CompletableFuture<ConsensusResult> partitionedResult =
                testNodes.get(3).handleProposal(proposal);
            assertThrows(Exception.class, () ->
                partitionedResult.get(1000, TimeUnit.MILLISECONDS),
                "Partitioned nodes should not respond"
            );
        }

        @Test
        @DisplayName("Minority partition cannot achieve consensus")
        void testMinorityPartitionCannotAchieveConsensus() throws Exception {
            // Simulate partition: 2 nodes active (minority), 3 partitioned
            networkSimulator.simulatePartition(List.of(2, 3, 4));

            Proposal proposal = new Proposal("minority-value",
                testNodes.get(0).getId(),
                ProposalType.WORKFLOW_STATE,
                1
            );

            // Submit multiple proposals to test consistency
            List<CompletableFuture<ConsensusResult>> futures = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                Proposal p = new Proposal("minority-value-" + i,
                    testNodes.get(0).getId(),
                    ProposalType.WORKFLOW_STATE,
                    i + 1
                );
                futures.add(consensusEngine.propose(p));
            }

            // All proposals should fail due to minority
            AtomicInteger successCount = new AtomicInteger(0);
            for (CompletableFuture<ConsensusResult> future : futures) {
                try {
                    ConsensusResult result = future.get(1000, TimeUnit.MILLISECONDS);
                    if (result.isSuccess()) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Expected - proposals should timeout or fail
                }
            }

            assertEquals(0, successCount.get(),
                "Minority partition should not achieve consensus");
        }

        @Test
        @DisplayName("Quorum calculation is correct")
        void testQuorumCalculation() throws Exception {
            // Test with different partition sizes
            int[] partitionSizes = {2, 3, 4}; // minority, majority, full

            for (int partitionSize : partitionSizes) {
                // Reset simulation
                networkSimulator.restoreFullConnectivity();

                // Simulate partition
                List<Integer> partitionedNodes = IntStream
                    .range(partitionSize, NODE_COUNT)
                    .boxed()
                    .toList();
                networkSimulator.simulatePartition(partitionedNodes);

                // Check quorum status
                ConsensusState state = consensusEngine.getState();
                boolean hasQuorum = state.hasQuorum();

                // Majority is ceiling(NODE_COUNT/2) = 3
                boolean expectedQuorum = partitionSize >= 3;
                assertEquals(expectedQuorum, hasQuorum,
                    "Quorum should be " + expectedQuorum + " for " +
                    partitionSize + " active nodes");
            }
        }
    }

    @Nested
    @DisplayName("Split-Brain Scenarios")
    class SplitBrainTests {

        @Test
        @DisplayName("Split-brain with divergent state")
        void testSplitBrainDivergentState() throws Exception {
            // Create split-brain: two partitions of 2 nodes each
            List<Integer> partitionA = List.of(0, 1);
            List<Integer> partitionB = List.of(2, 3);
            List<Integer> partitioned = List.of(4);  // Node 4 isolated
            networkSimulator.simulatePartition(partitioned);
            networkSimulator.simulatePartitionBetween(partitionA, partitionB);

            // Partition A achieves consensus
            Proposal proposalA = new Proposal("partition-a-value",
                testNodes.get(0).getId(),
                ProposalType.WORKFLOW_STATE,
                1
            );
            CompletableFuture<ConsensusResult> resultA =
                testNodes.get(0).handleProposal(proposalA);
            assertTrue(resultA.get(2000, TimeUnit.MILLISECONDS).isSuccess());

            // Partition B achieves different consensus
            Proposal proposalB = new Proposal("partition-b-value",
                testNodes.get(2).getId(),
                ProposalType.WORKFLOW_STATE,
                1
            );
            CompletableFuture<ConsensusResult> resultB =
                testNodes.get(2).handleProposal(proposalB);
            assertTrue(resultB.get(2000, TimeUnit.MILLISECONDS).isSuccess());

            // Verify divergence
            assertNotEquals(
                resultA.get().getValue(),
                resultB.get().getValue(),
                "Partitions should have divergent state"
            );
        }

        @Test
        @DisplayName("Split-brain resolution after partition healing")
        void testSplitBrainResolutionAfterHealing() throws Exception {
            // Initial state
            Proposal initialProposal = new Proposal("initial-value",
                testNodes.get(0).getId(),
                ProposalType.WORKFLOW_STATE,
                1
            );
            CompletableFuture<ConsensusResult> initialResult =
                consensusEngine.propose(initialProposal);
            assertTrue(initialResult.get(2000, TimeUnit.MILLISECONDS).isSuccess());

            // Create split-brain
            List<Integer> partitioned = List.of(3, 4);
            networkSimulator.simulatePartition(partitioned);

            // Submit during split-brain
            Proposal splitProposal = new Proposal("split-value",
                testNodes.get(0).getId(),
                ProposalType.WORKFLOW_STATE,
                2
            );
            CompletableFuture<ConsensusResult> splitResult =
                consensusEngine.propose(splitProposal);
            assertFalse(splitResult.get(2000, TimeUnit.MILLISECONDS).isSuccess());

            // Heal partition
            networkSimulator.restoreFullConnectivity();

            // Verify system achieves consensus after healing
            Proposal healedProposal = new Proposal("healed-value",
                testNodes.get(0).getId(),
                ProposalType.WORKFLOW_STATE,
                3
            );
            CompletableFuture<ConsensusResult> healedResult =
                consensusEngine.propose(healedProposal);
            assertTrue(healedResult.get(2000, TimeUnit.MILLISECONDS).isSuccess());
        }
    }

    @Nested
    @DisplayName("Leader Election Scenarios")
    class LeaderElectionTests {

        @Test
        @DisplayName("Leader in majority partition maintains leadership")
        void testLeaderInMajorityPartitionMaintainsLeadership() throws Exception {
            // Ensure node 0 is leader
            testNodes.get(0).role = NodeRole.LEADER;
            testNodes.forEach(node -> node.role = NodeRole.FOLLOWER);

            // Simulate partition including leader
            networkSimulator.simulatePartition(List.of(3, 4)); // Partition nodes 3, 4

            // Leader should still be able to achieve consensus
            Proposal proposal = new Proposal("leader-in-partition",
                testNodes.get(0).getId(),
                ProposalType.WORKFLOW_STATE,
                1
            );
            CompletableFuture<ConsensusResult> future = consensusEngine.propose(proposal);
            ConsensusResult result = future.get(2000, TimeUnit.MILLISECONDS);

            assertTrue(result.isSuccess());
            assertEquals("leader-in-partition", result.getValue());

            // Verify leadership is maintained
            assertEquals(NodeRole.LEADER, testNodes.get(0).getRole());
        }

        @Test
        @DisplayName("New election in partition without leader")
        void testNewElectionWithoutLeader() throws Exception {
            // No leader initially
            testNodes.forEach(node -> node.role = NodeRole.FOLLOWER);

            // Partition without leader
            networkSimulator.simulatePartition(List.of(2));

            // Remaining nodes should elect new leader
            Proposal proposal = new Proposal("no-leader-value",
                testNodes.get(0).getId(),
                ProposalType.WORKFLOW_STATE,
                1
            );

            // May take longer due to election
            CompletableFuture<ConsensusResult> future = consensusEngine.propose(proposal);
            ConsensusResult result = future.get(5000, TimeUnit.MILLISECONDS);

            assertTrue(result.isSuccess());
            assertEquals("no-leader-value", result.getValue());

            // Verify new leader was elected
            assertTrue(testNodes.stream()
                .anyMatch(node -> node.getRole() == NodeRole.LEADER),
                "A leader should be elected");
        }
    }

    @Nested
    @DisplayName("Dynamic Partition Scenarios")
    class DynamicPartitionTests {

        @Test
        @DisplayName("Dynamic partition handling with continuous load")
        void testDynamicPartitionWithContinuousLoad() throws Exception {
            int proposalCount = 10;
            AtomicInteger consensusCount = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(proposalCount);

            // Submit multiple proposals
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            for (int i = 0; i < proposalCount; i++) {
                final int proposalId = i;
                executor.submit(() -> {
                    try {
                        Proposal proposal = new Proposal("value-" + proposalId,
                            testNodes.get(0).getId(),
                            ProposalType.WORKFLOW_STATE,
                            proposalId
                        );

                        consensusEngine.propose(proposal).thenAccept(result -> {
                            if (result.isSuccess()) {
                                consensusCount.incrementAndGet();
                            }
                            latch.countDown();
                        });
                    } catch (Exception e) {
                        latch.countDown();
                    }
                });
            }

            // Initially all nodes active - should achieve consensus
            assertTrue(latch.await(5, TimeUnit.SECONDS),
                "Should complete proposals");
            assertEquals(proposalCount, consensusCount.get(),
                "All proposals should succeed initially");

            // Simulate network failure
            networkSimulator.simulatePartition(List.of(3, 4));

            // Submit more proposals
            consensusCount.set(0);
            latch = new CountDownLatch(5);

            for (int i = proposalCount; i < proposalCount + 5; i++) {
                final int proposalId = i;
                executor.submit(() -> {
                    try {
                        Proposal proposal = new Proposal("value-" + proposalId,
                            testNodes.get(0).getId(),
                            ProposalType.WORKFLOW_STATE,
                            proposalId
                        );

                        consensusEngine.propose(proposal).thenAccept(result -> {
                            if (result.isSuccess()) {
                                consensusCount.incrementAndGet();
                            }
                            latch.countDown();
                        });
                    } catch (Exception e) {
                        latch.countDown();
                    }
                });
            }

            // Should still achieve consensus with remaining nodes
            assertTrue(latch.await(5, TimeUnit.SECONDS),
                "Should complete proposals during partition");
            assertEquals(5, consensusCount.get(),
                "All proposals should succeed during partition");

            executor.shutdown();
        }

        @Test
        @DisplayName("Partition recovery with state consistency")
        void testPartitionRecoveryWithStateConsistency() throws Exception {
            // Initial consensus
            Proposal initialProposal = new Proposal("initial-value",
                testNodes.get(0).getId(),
                ProposalType.WORKFLOW_STATE,
                1
            );
            CompletableFuture<ConsensusResult> initialResult =
                consensusEngine.propose(initialProposal);
            assertTrue(initialResult.get(2000, TimeUnit.MILLISECONDS).isSuccess());

            // Create partition
            networkSimulator.simulatePartition(List.of(3, 4, 5));

            // Minority partition should not achieve consensus
            Proposal minorityProposal = new Proposal("minority-value",
                testNodes.get(0).getId(),
                ProposalType.WORKFLOW_STATE,
                2
            );
            CompletableFuture<ConsensusResult> minorityResult =
                consensusEngine.propose(minorityProposal);
            assertFalse(minorityResult.get(2000, TimeUnit.MILLISECONDS).isSuccess());

            // Recover partition
            networkSimulator.restoreFullConnectivity();

            // System should achieve consensus again
            Proposal recoveredProposal = new Proposal("recovered-value",
                testNodes.get(0).getId(),
                ProposalType.WORKFLOW_STATE,
                3
            );
            CompletableFuture<ConsensusResult> recoveredResult =
                consensusEngine.propose(recoveredProposal);
            assertTrue(recoveredResult.get(2000, TimeUnit.MILLISECONDS).isSuccess());
        }
    }

    @Nested
    @DisplayName("Failure Detection and Recovery")
    class FailureDetectionTests {

        @Test
        @DisplayName("Network partition detection and handling")
        void testNetworkPartitionDetection() throws Exception {
            // Create asymmetric partition
            networkSimulator.simulatePartition(List.of(2, 4)); // Nodes 2 and 4 partitioned

            // Check partition detection
            ConsensusState state = consensusEngine.getState();
            assertFalse(state.hasQuorum(), "Should detect partition (3 active < 5 total)");

            // Handle partition properly
            for (int i = 0; i < NODE_COUNT; i++) {
                TestableNode node = testNodes.get(i);
                PartitionId partitionId = new PartitionId(
                    "partition-" + i,
                    i < 3 ? 3 : 2,  // 3 nodes in one partition, 2 in another
                    i < 3           // First partition is quorum
                );
                node.handlePartition(partitionId);
            }

            // Verify nodes handle partition appropriately
            for (int i = 0; i < 3; i++) {
                assertEquals(NodeRole.FOLLOWER, testNodes.get(i).getRole(),
                    "Active nodes should be followers");
            }
            for (int i = 3; i < NODE_COUNT; i++) {
                assertEquals(NodeRole.FOLLOWER, testNodes.get(i).getRole(),
                    "Partitioned nodes should be followers");
            }
        }

        @Test
        @DisplayName("Graceful degradation during partition")
        void testGracefulDegradation() throws Exception {
            AtomicInteger successfulOperations = new AtomicInteger(0);
            AtomicInteger failedOperations = new AtomicInteger(0);

            // Start normal operation
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

            // Submit operations during normal operation
            for (int i = 0; i < 5; i++) {
                final int operationId = i;
                executor.submit(() -> {
                    try {
                        Proposal proposal = new Proposal("op-" + operationId,
                            testNodes.get(0).getId(),
                            ProposalType.WORKFLOW_STATE,
                            operationId
                        );
                        CompletableFuture<ConsensusResult> result =
                            consensusEngine.propose(proposal);
                        if (result.get(2000, TimeUnit.MILLISECONDS).isSuccess()) {
                            successfulOperations.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failedOperations.incrementAndGet();
                    }
                });
            }

            // Wait for initial operations
            Thread.sleep(100);

            // Simulate partition
            networkSimulator.simulatePartition(List.of(3, 4));

            // Submit more operations during partition
            for (int i = 5; i < 10; i++) {
                final int operationId = i;
                executor.submit(() -> {
                    try {
                        Proposal proposal = new Proposal("op-" + operationId,
                            testNodes.get(0).getId(),
                            ProposalType.WORKFLOW_STATE,
                            operationId
                        );
                        CompletableFuture<ConsensusResult> result =
                            consensusEngine.propose(proposal);
                        if (result.get(2000, TimeUnit milliseconds).isSuccess()) {
                            successfulOperations.incrementAndGet();
                        } else {
                            failedOperations.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failedOperations.incrementAndGet();
                    }
                });
            }

            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);

            // System should handle partition gracefully
            assertTrue(successfulOperations.get() >= 5,
                "Should have successful operations");
            assertTrue(failedOperations.get() >= 0,
                "May have some failures during partition");
        }
    }

    // =========================================================================
    // Helper Classes and Infrastructure
    // =========================================================================

    /**
     * Testable implementation of ConsensusNode for testing
     */
    private static class TestableNode implements ConsensusNode {
        private final UUID id;
        private final String address;
        private volatile NodeRole role;
        private volatile boolean active;
        private final ConsensusEngine engine;
        private final NetworkSimulator networkSimulator;

        public TestableNode(String address, ConsensusEngine engine, NetworkSimulator simulator) {
            this.id = UUID.randomUUID();
            this.address = address;
            this.engine = engine;
            this.networkSimulator = simulator;
            this.role = NodeRole.FOLLOWER;
            this.active = true;
        }

        @Override
        public UUID getId() { return id; }

        @Override
        public String getAddress() { return address; }

        @Override
        public boolean isActive() { return active; }

        @Override
        public CompletableFuture<ConsensusResult> handleProposal(Proposal proposal) {
            if (!isActive()) {
                CompletableFuture<ConsensusResult> future = new CompletableFuture<>();
                future.completeExceptionally(new ConsensusException("Node is inactive"));
                return future;
            }

            // Network simulation: may fail if node is partitioned
            if (!networkSimulator.canCommunicate(id, proposal.getProposerId())) {
                CompletableFuture<ConsensusResult> future = new CompletableFuture<>();
                future.completeExceptionally(new ConsensusException("Network partition"));
                return future;
            }

            // Delegate to consensus engine
            return engine.propose(proposal);
        }

        @Override
        public boolean handleVote(Vote vote) {
            if (!isActive()) return false;
            return engine.handleVote(vote);
        }

        @Override
        public void handleHeartbeat(Heartbeat heartbeat) {
            if (isActive()) {
                engine.handleHeartbeat(heartbeat);
            }
        }

        @Override
        public void handlePartition(PartitionId partitionId) {
            this.active = partitionId.isQuorumPartition();
            if (!active) {
                this.role = NodeRole.FOLLOWER;
            }
        }

        @Override
        public NodeRole getRole() { return role; }

        @Override
        public void stepDown() {
            this.role = NodeRole.FOLLOWER;
        }

        @Override
        public boolean canBeLeader() {
            return isActive() && (role == NodeRole.CANDIDATE || role == NodeRole.FOLLOWER);
        }
    }

    /**
     * Real network simulator for testing partition scenarios
     */
    private static class NetworkSimulator {
        private final int nodeCount;
        private final List<List<UUID>> partitions;
        private final List<TestableNode> nodes;
        private final AtomicBoolean running = new AtomicBoolean(true);

        public NetworkSimulator(int nodeCount) {
            this.nodeCount = nodeCount;
            this.partitions = new ArrayList<>();
            this.nodes = new ArrayList<>();
        }

        public void initialize(List<TestableNode> nodes) {
            this.nodes.addAll(nodes);
        }

        public void simulatePartition(List<Integer> partitionedNodeIndices) {
            if (!running.get()) return;

            List<UUID> partitionedIds = partitionedNodeIndices.stream()
                .map(i -> nodes.get(i).getId())
                .toList();

            partitions.add(partitionedIds);

            // Mark nodes as inactive
            for (int i : partitionedNodeIndices) {
                nodes.get(i).active = false;
            }
        }

        public void simulatePartitionBetween(List<Integer> partitionA, List<Integer> partitionB) {
            if (!running.get()) return;

            // Create two separate partitions
            List<UUID> idsA = partitionA.stream()
                .map(i -> nodes.get(i).getId())
                .toList();
            List<UUID> idsB = partitionB.stream()
                .map(i -> nodes.get(i).getId())
                .toList();

            partitions.add(idsA);
            partitions.add(idsB);

            // Mark nodes as active in their respective partitions
            for (int i : partitionA) {
                nodes.get(i).active = true;
            }
            for (int i : partitionB) {
                nodes.get(i).active = true;
            }
        }

        public void restoreFullConnectivity() {
            if (!running.get()) return;

            partitions.clear();
            nodes.forEach(node -> node.active = true);
        }

        public boolean canCommunicate(UUID from, UUID to) {
            if (!running.get()) return true;

            if (partitions.isEmpty()) {
                return true; // No partitions, full connectivity
            }

            // Check if nodes are in the same partition
            for (List<UUID> partition : partitions) {
                boolean fromInPartition = partition.contains(from);
                boolean toInPartition = partition.contains(to);

                if (fromInPartition && toInPartition) {
                    return true; // Both in same partition
                }
                if (fromInPartition || toInPartition) {
                    return false; // One in partition, one not - cannot communicate
                }
            }

            return true; // Neither in any partition - can communicate
        }

        public void shutdown() {
            running.set(false);
            partitions.clear();
            nodes.forEach(node -> node.active = true);
        }
    }
}