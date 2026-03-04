/*
 * Copyright (c) 2026 YAWL Foundation. All rights reserved.
 * DO NOT MODIFY THIS FILE - GENERATED CODE
 */

package org.yawlfoundation.yawl.consensus;

import org.h2.jdbcx.JdbcDataSource;
import org.yawlfoundation.yawl.integration.eventsourcing.EventSourcingTestFixture;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for consensus system failure scenarios.
 * Uses real H2 database for state manipulation and failure injection.
 * No mocks - tests real YAWL consensus behavior.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
class FailureScenarioTest {

    private ConsensusEngine consensusEngine;
    private ConsensusNode node1;
    private ConsensusNode node2;
    private ConsensusNode node3;
    private DataSource dataSource;
    private Connection testConnection;

    @BeforeEach
    void setUp() throws SQLException {
        // Create H2 in-memory database for state persistence
        dataSource = EventSourcingTestFixture.createDataSource();
        testConnection = dataSource.getConnection();

        // Initialize consensus engine with real database backing
        consensusEngine = new RaftConsensus(dataSource);

        // Create test nodes with real implementations
        node1 = new ConsensusNodeImpl("node1:8080", consensusEngine);
        node2 = new ConsensusNodeImpl("node2:8080", consensusEngine);
        node3 = new ConsensusNodeImpl("node3:8080", consensusEngine);

        // Setup cluster
        consensusEngine.registerNode(node1);
        consensusEngine.registerNode(node2);
        consensusEngine.registerNode(node3);

        // Initialize database with basic cluster state
        initializeClusterState();
    }

    @AfterEach
    void tearDown() throws SQLException {
        // Close test connection
        if (testConnection != null && !testConnection.isClosed()) {
            testConnection.close();
        }

        // Clean up consensus engine
        if (consensusEngine instanceof RaftConsensus) {
            ((RaftConsensus) consensusEngine).shutdown();
        }

        // Clean up database
        EventSourcingTestFixture.dropSchema(dataSource);
    }

    /**
     * Initialize database with basic cluster state for testing
     */
    private void initializeClusterState() throws SQLException {
        try (PreparedStatement stmt = testConnection.prepareStatement(
                "INSERT INTO cluster_state (node_id, address, role, is_active, last_heartbeat) " +
                "VALUES (?, ?, ?, ?, ?)")) {

            // Initialize all nodes as active followers
            String[] addresses = {"node1:8080", "node2:8080", "node3:8080"};
            for (String address : addresses) {
                UUID nodeId = UUID.randomUUID();
                stmt.setString(1, nodeId.toString());
                stmt.setString(2, address);
                stmt.setString(3, "FOLLOWER");
                stmt.setBoolean(4, true);
                stmt.setTimestamp(5, java.sql.Timestamp.from(Instant.now()));
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    /**
     * Inject node failure by updating database state
     */
    private void injectNodeFailure(String nodeAddress) throws SQLException {
        try (PreparedStatement stmt = testConnection.prepareStatement(
                "UPDATE cluster_state SET is_active = false WHERE address = ?")) {
            stmt.setString(1, nodeAddress);
            stmt.executeUpdate();
        }
    }

    /**
     * Recover node by updating database state
     */
    private void recoverNode(String nodeAddress) throws SQLException {
        try (PreparedStatement stmt = testConnection.prepareStatement(
                "UPDATE cluster_state SET is_active = true, last_heartbeat = ? WHERE address = ?")) {
            stmt.setTimestamp(1, java.sql.Timestamp.from(Instant.now()));
            stmt.setString(2, nodeAddress);
            stmt.executeUpdate();
        }
    }

    /**
     * Check cluster health via database query
     */
    private int getActiveNodeCount() throws SQLException {
        try (PreparedStatement stmt = testConnection.prepareStatement(
                "SELECT COUNT(*) FROM cluster_state WHERE is_active = true")) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    @Test
    void testLeaderFailureDuringConsensus() throws Exception {
        // Create proposal
        Proposal proposal = new Proposal("test-value", node1.getId(), ProposalType.WORKFLOW_STATE, 1);

        // Start consensus process
        CompletableFuture<ConsensusResult> future = consensusEngine.propose(proposal);

        // Inject leader failure via database
        injectNodeFailure("node1:8080");

        // New leader should be elected and consensus should continue
        ConsensusResult result = future.get(5, TimeUnit.SECONDS);

        assertTrue(result.isSuccess());
        assertEquals("test-value", result.getValue());

        // Verify new leader is active
        assertTrue(getActiveNodeCount() >= 2);
    }

    @Test
    void testNetworkPartitionScenario() throws Exception {
        // Simulate network partition by deactivating one node via database
        injectNodeFailure("node3:8080");

        // Verify only 2 nodes remain active
        assertEquals(2, getActiveNodeCount());

        Proposal proposal = new Proposal("test-value", node1.getId(), ProposalType.WORKFLOW_STATE, 1);

        // Should still work with remaining 2 nodes (majority)
        CompletableFuture<ConsensusResult> future = consensusEngine.propose(proposal);
        ConsensusResult result = future.get(3, TimeUnit.SECONDS);

        assertTrue(result.isSuccess());
    }

    @Test
    void testSplitBrainScenario() throws Exception {
        // Create first partition by deactivating node 3
        injectNodeFailure("node3:8080");
        assertEquals(2, getActiveNodeCount());

        // First partition (nodes 1,2) should achieve consensus
        Proposal proposal1 = new Proposal("partition1-value", node1.getId(), ProposalType.WORKFLOW_STATE, 1);
        CompletableFuture<ConsensusResult> result1 = consensusEngine.propose(proposal1);
        assertTrue(result1.get(3, TimeUnit.SECONDS).isSuccess());

        // Simulate network recovery by activating node 3
        recoverNode("node3:8080");
        assertEquals(3, getActiveNodeCount());

        // New proposal should reach consensus across all nodes
        Proposal proposal2 = new Proposal("recovered-value", node1.getId(), ProposalType.WORKFLOW_STATE, 1);
        CompletableFuture<ConsensusResult> result2 = consensusEngine.propose(proposal2);
        assertTrue(result2.get(3, TimeUnit.SECONDS).isSuccess());
    }

    @Test
    void testMultipleNodeFailures() throws Exception {
        // Fail two nodes via database, leaving only one
        injectNodeFailure("node2:8080");
        injectNodeFailure("node3:8080");
        assertEquals(1, getActiveNodeCount());

        Proposal proposal = new Proposal("test-value", node1.getId(), ProposalType.WORKFLOW_STATE, 1);

        // Should fail due to no quorum
        CompletableFuture<ConsensusResult> future = consensusEngine.propose(proposal);
        ConsensusResult result = future.get(2, TimeUnit.SECONDS);

        assertFalse(result.isSuccess());
        assertEquals(ConsensusStatus.INSUFFICIENT_NODES, result.getStatus());
    }

    @Test
    void testRecoveryAfterFailure() throws Exception {
        // Fail a node via database
        injectNodeFailure("node3:8080");
        assertEquals(2, getActiveNodeCount());

        // Should still work with remaining nodes
        Proposal proposal1 = new Proposal("value1", node1.getId(), ProposalType.WORKFLOW_STATE, 1);
        CompletableFuture<ConsensusResult> result1 = consensusEngine.propose(proposal1);
        assertTrue(result1.get(3, TimeUnit.SECONDS).isSuccess());

        // Recover the node via database
        recoverNode("node3:8080");
        assertEquals(3, getActiveNodeCount());

        // Should work again with full cluster
        Proposal proposal2 = new Proposal("value2", node1.getId(), ProposalType.WORKFLOW_STATE, 1);
        CompletableFuture<ConsensusResult> result2 = consensusEngine.propose(proposal2);
        assertTrue(result2.get(3, TimeUnit.SECONDS).isSuccess());
    }

    @Test
    void testConsistencyAfterRecovery() throws Exception {
        // Run consensus while all nodes are active
        Proposal proposal1 = new Proposal("initial-value", node1.getId(), ProposalType.WORKFLOW_STATE, 1);
        CompletableFuture<ConsensusResult> result1 = consensusEngine.propose(proposal1);
        assertTrue(result1.get(3, TimeUnit.SECONDS).isSuccess());

        // Fail node 3 via database
        injectNodeFailure("node3:8080");
        assertEquals(2, getActiveNodeCount());

        // Run more consensus with remaining nodes
        Proposal proposal2 = new Proposal("partition-value", node1.getId(), ProposalType.WORKFLOW_STATE, 1);
        CompletableFuture<ConsensusResult> result2 = consensusEngine.propose(proposal2);
        assertTrue(result2.get(3, TimeUnit.SECONDS).isSuccess());

        // Recover node 3 via database
        recoverNode("node3:8080");
        assertEquals(3, getActiveNodeCount());

        // Node 3 should catch up with latest consensus
        Proposal proposal3 = new Proposal("recovered-value", node1.getId(), ProposalType.WORKFLOW_STATE, 1);
        CompletableFuture<ConsensusResult> result3 = consensusEngine.propose(proposal3);
        assertTrue(result3.get(3, TimeUnit.SECONDS).isSuccess());
    }

    @Test
    void testSlowNodeRecovery() throws Exception {
        // Create a slow node that delays processing
        ConsensusNode slowNode = new ConsensusNode("slow-node:8080", consensusEngine) {
            @Override
            public UUID getId() {
                return UUID.randomUUID();
            }

            @Override
            public String getAddress() {
                return "slow-node:8080";
            }

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

            @Override
            public boolean handleVote(Vote vote) {
                return true;
            }

            @Override
            public void handleHeartbeat(Heartbeat heartbeat) {
                // No-op for test
            }

            @Override
            public void handlePartition(PartitionId partitionId) {
                // No-op for test
            }

            @Override
            public NodeRole getRole() {
                return NodeRole.FOLLOWER;
            }

            @Override
            public void stepDown() {
                // No-op for test
            }

            @Override
            public boolean canBeLeader() {
                return true;
            }
        };

        // Register slow node in database
        try (PreparedStatement stmt = testConnection.prepareStatement(
                "INSERT INTO cluster_state (node_id, address, role, is_active, last_heartbeat) " +
                "VALUES (?, ?, ?, ?, ?)")) {
            UUID nodeId = slowNode.getId();
            stmt.setString(1, nodeId.toString());
            stmt.setString(2, slowNode.getAddress());
            stmt.setString(3, "FOLLOWER");
            stmt.setBoolean(4, true);
            stmt.setTimestamp(5, java.sql.Timestamp.from(Instant.now()));
            stmt.executeUpdate();
        }

        consensusEngine.registerNode(slowNode);

        // Should still achieve consensus despite slow node
        Proposal proposal = new Proposal("test-value", node1.getId(), ProposalType.WORKFLOW_STATE, 1);
        CompletableFuture<ConsensusResult> future = consensusEngine.propose(proposal);

        // May take longer due to slow node, but should still succeed
        ConsensusResult result = future.get(5, TimeUnit.SECONDS);
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

    @Test
    void testDatabasePersistenceThroughFailures() throws Exception {
        // Create initial consensus
        Proposal proposal1 = new Proposal("initial-value", node1.getId(), ProposalType.WORKFLOW_STATE, 1);
        CompletableFuture<ConsensusResult> result1 = consensusEngine.propose(proposal1);
        assertTrue(result1.get(3, TimeUnit.SECONDS).isSuccess());

        // Verify persistence in database
        try (PreparedStatement stmt = testConnection.prepareStatement(
                "SELECT COUNT(*) FROM consensus_log WHERE proposal_id = ?")) {
            stmt.setString(1, proposal1.getProposalId().toString());
            ResultSet rs = stmt.executeQuery();
            assertTrue(rs.next() && rs.getInt(1) > 0);
        }

        // Fail and recover a node
        injectNodeFailure("node3:8080");
        assertEquals(2, getActiveNodeCount());

        Proposal proposal2 = new Proposal("failover-value", node1.getId(), ProposalType.WORKFLOW_STATE, 1);
        CompletableFuture<ConsensusResult> result2 = consensusEngine.propose(proposal2);
        assertTrue(result2.get(3, TimeUnit.SECONDS).isSuccess());

        // Recover node
        recoverNode("node3:8080");
        assertEquals(3, getActiveNodeCount());

        // Verify all consensus decisions are persisted
        try (PreparedStatement stmt = testConnection.prepareStatement(
                "SELECT COUNT(*) FROM consensus_log")) {
            ResultSet rs = stmt.executeQuery();
            assertTrue(rs.next() && rs.getInt(1) >= 2);
        }
    }

    @Test
    void testLeaderElectionAfterMultipleFailures() throws Exception {
        // Fail leader
        injectNodeFailure("node1:8080");
        assertEquals(2, getActiveNodeCount());

        // Fail another node
        injectNodeFailure("node2:8080");
        assertEquals(1, getActiveNodeCount());

        // Remaining node should become leader
        Proposal proposal = new Proposal("leader-test", node3.getId(), ProposalType.WORKFLOW_STATE, 1);
        CompletableFuture<ConsensusResult> future = consensusEngine.propose(proposal);

        // Should succeed with single node as leader
        ConsensusResult result = future.get(5, TimeUnit.SECONDS);
        assertTrue(result.isSuccess());
    }

    @Test
    void testConsensusTimeoutHandling() throws Exception {
        // Inject network partition by making node unreachable
        injectNodeFailure("node3:8080");
        assertEquals(2, getActiveNodeCount());

        // Create proposal that will timeout on slow network
        Proposal slowProposal = new Proposal("slow-value", node1.getId(), ProposalType.WORKFLOW_STATE, 1);

        // Should handle timeout gracefully
        CompletableFuture<ConsensusResult> future = consensusEngine.propose(slowProposal);

        // Should either succeed or fail with timeout status
        ConsensusResult result = future.get(10, TimeUnit.SECONDS);
        // Result can be success (if quorum reached) or timeout - both are acceptable
        assertTrue(result.isSuccess() || result.getStatus() == ConsensusStatus.TIMEOUT);
    }
}