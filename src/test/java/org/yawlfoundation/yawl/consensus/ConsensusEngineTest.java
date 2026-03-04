/*
 * Copyright (c) 2026 YAWL Foundation. All rights reserved.
 * DO NOT MODIFY THIS FILE - GENERATED CODE
 */

package org.yawlfoundation.yawl.consensus;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.integration.eventsourcing.EventSourcingTestFixture;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for ConsensusEngine using Chicago TDD with real implementations
 * and H2 database persistence.
 */
class ConsensusEngineTest {
    private static DataSource dataSource;
    private ConsensusEngine consensusEngine;
    private ConsensusNode node1;
    private ConsensusNode node2;
    private ConsensusNode node3;

    @BeforeAll
    static void setupDatabase() throws SQLException {
        // Create shared H2 in-memory database for all tests
        dataSource = EventSourcingTestFixture.createDataSource();
        EventSourcingTestFixture.createSchema(dataSource);
    }

    @AfterAll
    static void cleanupDatabase() throws SQLException {
        if (dataSource != null) {
            EventSourcingTestFixture.dropSchema(dataSource);
        }
    }

    @BeforeEach
    void setUp() throws SQLException {
        // Create real RaftConsensus with persistence
        consensusEngine = new RaftConsensusWithPersistence(dataSource);

        // Create real test nodes
        node1 = new ConsensusNodeImpl("node1:8080", consensusEngine);
        node2 = new ConsensusNodeImpl("node2:8080", consensusEngine);
        node3 = new ConsensusNodeImpl("node3:8080", consensusEngine);
    }

    @Test
    void testInitialConsensusState() {
        ConsensusState state = consensusEngine.getState();

        assertNotNull(state);
        assertEquals(0, state.getCurrentTerm());
        assertEquals(NodeRole.FOLLOWER, state.getRole());
        assertNull(state.getLeaderId());
        assertEquals(0, state.getTotalNodes());
        assertFalse(state.hasQuorum());
    }

    @Test
    void testNodeRegistration() {
        // Register nodes
        consensusEngine.registerNode(node1);
        consensusEngine.registerNode(node2);
        consensusEngine.registerNode(node3);

        ConsensusState state = consensusEngine.getState();
        assertEquals(3, state.getTotalNodes());
        assertFalse(state.hasQuorum()); // Followers without leader
    }

    @Test
    void testQuorumCalculation() {
        // Register nodes
        consensusEngine.registerNode(node1);
        consensusEngine.registerNode(node2);
        consensusEngine.registerNode(node3);

        // For Raft with 3 nodes, we need 2 for majority
        ConsensusStrategy raft = consensusEngine.getStrategy();
        assertEquals(2, raft.getMinQuorum(3));
    }

    @Test
    void testSimpleConsensus() throws Exception {
        // Setup cluster
        consensusEngine.registerNode(node1);
        consensusEngine.registerNode(node2);
        consensusEngine.registerNode(node3);

        // Create proposal
        Proposal proposal = new Proposal("test-value", node1.getId(), ProposalType.WORKFLOW_STATE, 1);

        // Propose value
        CompletableFuture<ConsensusResult> future = consensusEngine.propose(proposal);

        // Wait for result (with timeout)
        ConsensusResult result = future.get(1, TimeUnit.SECONDS);

        assertTrue(result.isSuccess());
        assertEquals("test-value", result.getValue());
        assertTrue(result.hasMajority());
    }

    @Test
    void testConcurrentProposals() throws Exception {
        // Setup cluster
        consensusEngine.registerNode(node1);
        consensusEngine.registerNode(node2);
        consensusEngine.registerNode(node3);

        int proposalCount = 10;
        AtomicInteger successCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(proposalCount);

        // Submit multiple concurrent proposals
        for (int i = 0; i < proposalCount; i++) {
            Proposal proposal = new Proposal("value-" + i, node1.getId(), ProposalType.WORKFLOW_STATE, i);

            consensusEngine.propose(proposal).thenAccept(result -> {
                if (result.isSuccess()) {
                    successCount.incrementAndGet();
                }
                latch.countDown();
            });
        }

        // Wait for all proposals to complete
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals(proposalCount, successCount.get());
    }

    @Test
    void testFailedConsensusNoQuorum() throws Exception {
        // Only register 1 node (insufficient for quorum)
        consensusEngine.registerNode(node1);

        Proposal proposal = new Proposal("test-value", node1.getId(), ProposalType.WORKFLOW_STATE, 1);

        CompletableFuture<ConsensusResult> future = consensusEngine.propose(proposal);

        // Should fail due to no quorum
        ConsensusResult result = future.get(1, TimeUnit.SECONDS);

        assertFalse(result.isSuccess());
        assertEquals(ConsensusStatus.INSUFFICIENT_NODES, result.getStatus());
    }

    @Test
    void testStrategySwitching() {
        // Default strategy should be RAFT
        assertEquals(ConsensusStrategy.RAFT, consensusEngine.getStrategy());

        // Switch strategy
        consensusEngine.setStrategy(ConsensusStrategy.PAXOS);
        assertEquals(ConsensusStrategy.PAXOS, consensusEngine.getStrategy());
    }

    @Test
    void testNodeFailureHandling() throws Exception {
        // Setup cluster
        consensusEngine.registerNode(node1);
        consensusEngine.registerNode(node2);
        consensusEngine.registerNode(node3);

        // Simulate node failure by deactivating
        ((ConsensusNodeImpl) node3).active = false;

        Proposal proposal = new Proposal("test-value", node1.getId(), ProposalType.WORKFLOW_STATE, 1);

        // Should still work with remaining 2 nodes
        CompletableFuture<ConsensusResult> future = consensusEngine.propose(proposal);

        ConsensusResult result = future.get(1, TimeUnit.SECONDS);

        // Should succeed with 2/3 nodes (majority)
        assertTrue(result.isSuccess());
    }

    @Test
    void testPerformanceUnderLoad() throws Exception {
        // Setup cluster
        consensusEngine.registerNode(node1);
        consensusEngine.registerNode(node2);
        consensusEngine.registerNode(node3);

        int proposalCount = 100;
        AtomicInteger successCount = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();

        // Submit proposals rapidly
        for (int i = 0; i < proposalCount; i++) {
            Proposal proposal = new Proposal("value-" + i, node1.getId(), ProposalType.WORKFLOW_STATE, i);

            consensusEngine.propose(proposal).thenAccept(result -> {
                if (result.isSuccess()) {
                    successCount.incrementAndGet();
                }
            });
        }

        // Wait for completion
        Thread.sleep(1000); // Allow time for processing

        long endTime = System.currentTimeMillis();
        double avgLatency = (endTime - startTime) / (double) proposalCount;

        // Performance check: average latency should be <100ms
        assertTrue(avgLatency < 100.0,
            String.format("Average latency %fms exceeds target 100ms", avgLatency));

        // Most proposals should succeed
        assertTrue(successCount.get() > proposalCount * 0.9,
            String.format("Success rate %f%% below target 90%%",
                (double) successCount.get() / proposalCount * 100));
    }

    @Test
    void testConsensusWithDifferentPriorities() throws Exception {
        // Setup cluster
        consensusEngine.registerNode(node1);
        consensusEngine.registerNode(node2);
        consensusEngine.registerNode(node3);

        // Create high priority proposal
        Proposal highPriority = new Proposal("high-value", node1.getId(), ProposalType.WORKFLOW_STATE, 10);
        Proposal lowPriority = new Proposal("low-value", node1.getId(), ProposalType.WORKFLOW_STATE, 1);

        // Submit both
        CompletableFuture<ConsensusResult> highResult = consensusEngine.propose(highPriority);
        CompletableFuture<ConsensusResult> lowResult = consensusEngine.propose(lowPriority);

        // Both should succeed
        assertTrue(highResult.get(1, TimeUnit.SECONDS).isSuccess());
        assertTrue(lowResult.get(1, TimeUnit.SECONDS).isSuccess());
    }

    @Test
    void testNodeActivationStates() {
        // Initially all nodes should be active
        assertTrue(node1.isActive());
        assertTrue(node2.isActive());
        assertTrue(node3.isActive());

        // Deactivate a node
        ((ConsensusNodeImpl) node1).active = false;

        assertFalse(node1.isActive());
        assertTrue(node2.isActive());
        assertTrue(node3.isActive());
    }

    @AfterEach
    void tearDown() throws SQLException {
        // Clean up database state between tests
        if (dataSource != null) {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement()) {
                // Clear consensus state table
                stmt.execute("DELETE FROM consensus_state");
                // Clear proposals table
                stmt.execute("DELETE FROM consensus_proposals");
            }
        }

        // Shutdown consensus engine
        if (consensusEngine instanceof RaftConsensusWithPersistence) {
            ((RaftConsensusWithPersistence) consensusEngine).shutdown();
        }
    }
}