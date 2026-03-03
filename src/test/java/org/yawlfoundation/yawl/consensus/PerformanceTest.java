/*
 * Copyright (c) 2026 YAWL Foundation. All rights reserved.
 * DO NOT MODIFY THIS FILE - GENERATED CODE
 */

package org.yawlfoundation.yawl.consensus;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance test suite for consensus framework
 * Verifies <100ms latency requirement
 */
@ExtendWith(MockitoExtension.class)
class PerformanceTest {
    private ConsensusEngine consensusEngine;
    private ConsensusNode node1;
    private ConsensusNode node2;
    private ConsensusNode node3;
    private List<ConsensusNode> nodes;

    @BeforeEach
    void setUp() {
        // Test with Raft (optimized for performance)
        consensusEngine = new RaftConsensus();
        node1 = new ConsensusNodeImpl("node1:8080", consensusEngine);
        node2 = new ConsensusNodeImpl("node2:8080", consensusEngine);
        node3 = new ConsensusNodeImpl("node3:8080", consensusEngine);
        nodes = Arrays.asList(node1, node2, node3);

        // Register all nodes
        for (ConsensusNode node : nodes) {
            consensusEngine.registerNode(node);
        }
    }

    @Test
    void testSingleProposalLatency() throws Exception {
        // Measure latency for single proposal
        Proposal proposal = new Proposal("test-value", node1.getId(), ProposalType.WORKFLOW_STATE, 1);

        long startTime = System.currentTimeMillis();
        CompletableFuture<ConsensusResult> future = consensusEngine.propose(proposal);
        ConsensusResult result = future.get(1, TimeUnit.SECONDS);
        long endTime = System.currentTimeMillis();

        long latency = endTime - startTime;

        assertTrue(result.isSuccess());
        assertTrue(latency < 100, String.format("Single proposal latency %dms exceeds 100ms target", latency));
    }

    @Test
    void testConcurrentProposalsLatency() throws Exception {
        // Measure latency for multiple concurrent proposals
        int proposalCount = 10;
        CountDownLatch latch = new CountDownLatch(proposalCount);
        AtomicLong totalLatency = new AtomicLong(0);
        AtomicInteger successCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        // Submit multiple concurrent proposals
        for (int i = 0; i < proposalCount; i++) {
            Proposal proposal = new Proposal("value-" + i, node1.getId(), ProposalType.WORKFLOW_STATE, i);

            consensusEngine.propose(proposal).thenAccept(result -> {
                long endTime = System.currentTimeMillis();
                totalLatency.addAndGet(endTime - startTime);
                if (result.isSuccess()) {
                    successCount.incrementAndGet();
                }
                latch.countDown();
            });
        }

        // Wait for all proposals
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        double averageLatency = (double) totalLatency.get() / proposalCount;

        // Performance check: average latency should be <100ms
        assertTrue(averageLatency < 100.0,
            String.format("Average latency %fms exceeds 100ms target", averageLatency));

        // Success rate should be high
        assertTrue(successCount.get() >= proposalCount * 0.9,
            String.format("Success rate %d/%d below 90%%", successCount.get(), proposalCount));
    }

    @Test
    void testHighThroughputPerformance() throws Exception {
        // Measure throughput with 100 proposals
        int proposalCount = 100;
        CountDownLatch latch = new CountDownLatch(proposalCount);
        AtomicLong totalTime = new AtomicLong(0);
        AtomicInteger successCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        // Submit proposals rapidly
        for (int i = 0; i < proposalCount; i++) {
            final int proposalId = i;
            Proposal proposal = new Proposal("value-" + i, node1.getId(), ProposalType.WORKFLOW_STATE, i);

            consensusEngine.propose(proposal).whenComplete((result, error) -> {
                if (error == null && result.isSuccess()) {
                    successCount.incrementAndGet();
                }
                long endTime = System.currentTimeMillis();
                totalTime.addAndGet(endTime - startTime);
                latch.countDown();
            });
        }

        // Wait for completion
        assertTrue(latch.await(3, TimeUnit.SECONDS));

        long endTime = System.currentTimeMillis();
        long totalTimeMs = endTime - startTime;
        double throughput = (double) proposalCount / (totalTimeMs / 1000.0); // proposals/second
        double avgLatency = (double) totalTime.get() / proposalCount;

        // Performance assertions
        assertTrue(avgLatency < 100.0,
            String.format("Average latency %fms exceeds 100ms target", avgLatency));
        assertTrue(throughput > 50.0,
            String.format("Throughput %.2f proposals/sec below 50 target", throughput));
        assertTrue(successCount.get() >= proposalCount * 0.9,
            String.format("Success rate %d/%d below 90%%", successCount.get(), proposalCount));
    }

    @Test
    void testLatencyUnderLoad() throws Exception {
        // Test latency with varying load levels
        int[] loadLevels = {1, 5, 10, 25, 50};
        Map<Integer, Double> latencies = new HashMap<>();

        for (int load : loadLevels) {
            CountDownLatch latch = new CountDownLatch(load);
            AtomicLong totalLatency = new AtomicLong(0);

            long startTime = System.currentTimeMillis();

            // Submit load proposals
            for (int i = 0; i < load; i++) {
                Proposal proposal = new Proposal("load-test-" + i, node1.getId(), ProposalType.WORKFLOW_STATE, i);

                consensusEngine.propose(proposal).thenAccept(result -> {
                    long endTime = System.currentTimeMillis();
                    totalLatency.addAndGet(endTime - startTime);
                    latch.countDown();
                });
            }

            assertTrue(latch.await(2, TimeUnit.SECONDS));

            double avgLatency = (double) totalLatency.get() / load;
            latencies.put(load, avgLatency);

            // Verify latency remains under 100ms even under load
            assertTrue(avgLatency < 100.0,
                String.format("Latency at load %d is %fms, exceeds 100ms", load, avgLatency));
        }

        // Verify latency doesn't degrade significantly with load
        double baseline = latencies.get(1);
        for (int load : loadLevels) {
            double increase = latencies.get(load) - baseline;
            assertTrue(increase < 50.0,
                String.format("Latency increase at load %d is %fms, exceeds 50ms", load, increase));
        }
    }

    @Test
    void testLatencyConsistency() throws Exception {
        // Test that latency remains consistent across multiple runs
        int runs = 10;
        List<Long> latencies = new ArrayList<>();

        for (int run = 0; run < runs; run++) {
            Proposal proposal = new Proposal("consistency-test", node1.getId(), ProposalType.WORKFLOW_STATE, 1);

            long startTime = System.currentTimeMillis();
            CompletableFuture<ConsensusResult> future = consensusEngine.propose(proposal);
            ConsensusResult result = future.get(1, TimeUnit.SECONDS);
            long endTime = System.currentTimeMillis();

            long latency = endTime - startTime;
            latencies.add(latency);

            assertTrue(result.isSuccess());
            assertTrue(latency < 100, String.format("Run %d latency %dms exceeds 100ms", run, latency));
        }

        // Calculate consistency metrics
        double avgLatency = latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        double stdDev = Math.sqrt(
            latencies.stream()
                .mapToDouble(lat -> Math.pow(lat - avgLatency, 2))
                .average()
                .orElse(0)
        );

        // Latency should be consistent (low standard deviation)
        assertTrue(stdDev < 20.0,
            String.format("Standard deviation %fms exceeds 20ms target", stdDev));

        // All latencies should be under 100ms
        assertTrue(latencies.stream().allMatch(lat -> lat < 100),
            "Some latencies exceed 100ms target");
    }

    @Test
    void testDifferentProposalTypesLatency() throws Exception {
        // Test latency for different proposal types
        ProposalType[] types = ProposalType.values();

        for (ProposalType type : types) {
            Proposal proposal = new Proposal("test-value", node1.getId(), type, 1);

            long startTime = System.currentTimeMillis();
            CompletableFuture<ConsensusResult> future = consensusEngine.propose(proposal);
            ConsensusResult result = future.get(1, TimeUnit.SECONDS);
            long endTime = System.currentTimeMillis();

            long latency = endTime - startTime;

            assertTrue(result.isSuccess(), String.format("%s proposal failed", type));
            assertTrue(latency < 100, String.format("%s proposal latency %dms exceeds 100ms", type, latency));
        }
    }

    @Test
    void testLatencyWithDifferentStrategies() throws Exception {
        // Test latency with different consensus strategies
        ConsensusStrategy[] strategies = {ConsensusStrategy.RAFT, ConsensusStrategy.PAXOS};

        for (ConsensusStrategy strategy : strategies) {
            consensusEngine.setStrategy(strategy);

            Proposal proposal = new Proposal("strategy-test", node1.getId(), ProposalType.WORKFLOW_STATE, 1);

            long startTime = System.currentTimeMillis();
            CompletableFuture<ConsensusResult> future = consensusEngine.propose(proposal);
            ConsensusResult result = future.get(1, TimeUnit.SECONDS);
            long endTime = System.currentTimeMillis();

            long latency = endTime - startTime;

            assertTrue(result.isSuccess(), String.format("%s strategy failed", strategy));
            assertTrue(latency < 100, String.format("%s strategy latency %dms exceeds 100ms", strategy, latency));
        }
    }

    @Test
    void testWorstCaseScenarioLatency() throws Exception {
        // Test latency in worst-case scenario (multiple failures)
        // Deactivate two nodes, leaving only one
        ((ConsensusNodeImpl) node2).active = false;
        ((ConsensusNodeImpl) node3).active = false;

        // Should fail quickly due to no quorum
        Proposal proposal = new Proposal("worst-case-test", node1.getId(), ProposalType.WORKFLOW_STATE, 1);

        long startTime = System.currentTimeMillis();
        CompletableFuture<ConsensusResult> future = consensusEngine.propose(proposal);
        ConsensusResult result = future.get(200, TimeUnit.MILLISECONDS); // Short timeout
        long endTime = System.currentTimeMillis();

        long latency = endTime - startTime;

        // Should fail quickly, not timeout
        assertFalse(result.isSuccess());
        assertTrue(latency < 200, String.format("Worst-case latency %dms exceeds 200ms", latency));
        assertEquals(ConsensusStatus.INSUFFICIENT_NODES, result.getStatus());
    }

    @AfterEach
    void tearDown() {
        if (consensusEngine instanceof RaftConsensus) {
            ((RaftConsensus) consensusEngine).shutdown();
        }
    }
}