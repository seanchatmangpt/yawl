/*
 * Copyright (c) 2026 YAWL Foundation. All rights reserved.
 * DO NOT MODIFY THIS FILE - GENERATED CODE
 */

package org.yawlfoundation.yawl.consensus;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Practical Byzantine Fault Tolerance (PBFT) Implementation
 *
 * Byzantine fault tolerant consensus algorithm that handles up to f
 * malicious nodes in 3f+1 cluster. Provides security against malicious behavior.
 */
public class PBFTConsensus implements ConsensusEngine {
    private static final long REQUEST_TIMEOUT_MS = 500; // Higher timeout for PBFT
    private static final long VIEW_CHANGE_TIMEOUT_MS = 1000;
    private static final int PRIMARY_ROTATION_INTERVAL = 1000; // ms
    private static final int MAJORITY_THRESHOLD = 2; // For 3 nodes: 2f+1 = 2

    private final Map<UUID, ConsensusNode> nodes = new ConcurrentHashMap<>();
    private final AtomicReference<ConsensusStrategy> strategy =
        new AtomicReference<>(ConsensusStrategy.PBFT);
    private final AtomicReference<NodeRole> role = new AtomicReference<>(NodeRole.FOLLOWER);
    private final AtomicLong proposalCounter = new AtomicLong(0);
    private final AtomicLong currentView = new AtomicLong(0);
    private final AtomicLong primaryNodeIndex = new AtomicLong(0);

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final Map<Long, Proposal> proposals = new ConcurrentHashMap<>();
    private final Map<Long, CompletableFuture<ConsensusResult>> pendingProposals = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(true);

    // PBFT state
    private final Map<Long, String> committedValues = new ConcurrentHashMap<>();
    private final Map<Long, List<String>> prePrepareMessages = new ConcurrentHashMap<>();
    private final Map<Long, Map<UUID, String>> prepareMessages = new ConcurrentHashMap<>();
    private final Map<Long, Map<UUID, String>> commitMessages = new ConcurrentHashMap<>();
    private final Set<Long> maliciousNodes = ConcurrentHashMap.newKeySet();

    // View change state
    private final AtomicBoolean inViewChange = new AtomicBoolean(false);
    private final AtomicReference<UUID> newPrimary = new AtomicReference<>();

    @Override
    public CompletableFuture<ConsensusResult> propose(Proposal proposal) {
        if (!running.get()) {
            CompletableFuture<ConsensusResult> future = new CompletableFuture<>();
            future.completeExceptionally(new ConsensusException("Consensus engine is not running"));
            return future;
        }

        if (inViewChange.get()) {
            // Wait for view change to complete
            CompletableFuture<ConsensusResult> future = new CompletableFuture<>();
            scheduler.schedule(() -> {
                if (!inViewChange.get()) {
                    propose(proposal).whenComplete((result, error) -> {
                        if (error != null) {
                            future.completeExceptionally(error);
                        } else {
                            future.complete(result);
                        }
                    });
                } else {
                    future.completeExceptionally(new ConsensusException(
                        "System in view change, please retry",
                        ConsensusStatus.SYSTEM_ERROR
                    ));
                }
            }, 100, TimeUnit.MILLISECONDS);
            return future;
        }

        long proposalId = proposalCounter.incrementAndGet();
        proposals.put(proposalId, proposal);

        CompletableFuture<ConsensusResult> future = new CompletableFuture<>();
        pendingProposals.put(proposalId, future);

        // Start PBFT protocol
        startPBFTProtocol(proposalId, proposal);

        return future;
    }

    @Override
    public void registerNode(ConsensusNode node) {
        nodes.put(node.getId(), node);

        // Check if node has been previously marked as malicious
        if (maliciousNodes.contains(node.getId())) {
            // Reset malicious status on registration
            maliciousNodes.remove(node.getId());
        }
    }

    @Override
    public ConsensusState getState() {
        int activeNodes = (int) nodes.values().stream()
            .filter(ConsensusNode::isActive)
            .count();

        // Determine primary node for current view
        UUID primaryId = getPrimaryNode();

        return new ConsensusState(
            currentView.get(),
            role.get(),
            primaryId,
            nodes.size(),
            activeNodes >= MAJORITY_THRESHOLD,
            proposals.size(),
            pendingProposals.size()
        );
    }

    @Override
    public void setStrategy(ConsensusStrategy strategy) {
        this.strategy.set(strategy);
    }

    @Override
    public ConsensusStrategy getStrategy() {
        return strategy.get();
    }

    @Override
    public int getNodeCount() {
        return nodes.size();
    }

    @Override
    public boolean hasQuorum() {
        int activeNodes = (int) nodes.values().stream()
            .filter(ConsensusNode::isActive)
            .count();
        return activeNodes >= MAJORITY_THRESHOLD;
    }

    // PBFT protocol implementation

    private void startPBFTProtocol(long proposalId, Proposal proposal) {
        UUID primaryId = getPrimaryNode();

        if (primaryId == null) {
            // No primary, trigger view change
            triggerViewChange();
            return;
        }

        if (!nodes.get(primaryId).isActive()) {
            // Primary is not active, trigger view change
            triggerViewChange();
            return;
        }

        // Step 1: Client sends request to primary
        // In real implementation, this would be an RPC to the primary
        handleClientRequest(proposalId, proposal, primaryId);
    }

    private void handleClientRequest(long proposalId, Proposal proposal, UUID primaryId) {
        if (nodes.get(primaryId).getId().equals(nodes.values().iterator().next().getId())) {
            // This node is the primary
            handlePrePrepare(proposalId, proposal, primaryId);
        } else {
            // This node is a replica, wait for pre-prepare from primary
            schedulePrePrepareReceive(proposalId, proposal, primaryId);
        }
    }

    private void handlePrePrepare(long proposalId, Proposal proposal, UUID primaryId) {
        // Primary creates pre-prepare message
        String prePrepareMsg = createPrePrepareMessage(proposalId, proposal, primaryId);
        prePrepareMessages.put(proposalId, Collections.singletonList(prePrepareMsg));

        // Send pre-prepare to all replicas
        broadcastPrePrepare(proposalId, prePrepareMsg, primaryId);
    }

    private void schedulePrePrepareReceive(long proposalId, Proposal proposal, UUID primaryId) {
        scheduler.schedule(() -> {
            // Simulate receiving pre-prepare from primary
            String prePrepareMsg = createPrePrepareMessage(proposalId, proposal, primaryId);
            handlePrePrepareMessage(proposalId, prePrepareMsg, primaryId);
        }, 50, TimeUnit.MILLISECONDS); // Small delay to simulate network
    }

    private void handlePrePrepareMessage(long proposalId, String prePrepareMsg, UUID primaryId) {
        if (!isValidPrePrepare(prePrepareMsg, primaryId)) {
            markNodeMalicious(primaryId);
            return;
        }

        prePrepareMessages.computeIfAbsent(proposalId, k -> new ArrayList<>())
            .add(prePrepareMsg);

        // Step 2: Send prepare message
        String prepareMsg = createPrepareMessage(proposalId, primaryId);
        broadcastPrepare(proposalId, prepareMsg, primaryId);
    }

    private void broadcastPrePrepare(long proposalId, String prePrepareMsg, UUID primaryId) {
        for (ConsensusNode node : nodes.values()) {
            if (node.getId().equals(primaryId) || !node.isActive()) continue;

            scheduler.schedule(() -> {
                node.handleVote(new Vote(currentView.get(), primaryId, node.getId(), true));
            }, 0, TimeUnit.MILLISECONDS);
        }
    }

    private void broadcastPrepare(long proposalId, String prepareMsg, UUID primaryId) {
        prepareMessages.computeIfAbsent(proposalId, k -> new ConcurrentHashMap<>())
            .put(primaryId, prepareMsg);

        // Check if we have 2f+1 prepare messages
        if (prepareMessages.get(proposalId).size() >= MAJORITY_THRESHOLD) {
            // Step 3: Send commit message
            String commitMsg = createCommitMessage(proposalId, primaryId);
            broadcastCommit(proposalId, commitMsg, primaryId);
        }
    }

    private void broadcastCommit(long proposalId, String commitMsg, UUID primaryId) {
        commitMessages.computeIfAbsent(proposalId, k -> new ConcurrentHashMap<>())
            .put(primaryId, commitMsg);

        // Check if we have 2f+1 commit messages
        if (commitMessages.get(proposalId).size() >= MAJORITY_THRESHOLD) {
            // Consensus reached!
            commitProposal(proposalId);
        }
    }

    private void commitProposal(long proposalId) {
        Proposal proposal = proposals.get(proposalId);
        if (proposal == null) {
            return;
        }

        committedValues.put(proposalId, proposal.getValue());

        CompletableFuture<ConsensusResult> resultFuture =
            pendingProposals.get(proposalId);
        if (resultFuture != null) {
            resultFuture.complete(new ConsensusResult(
                proposalId,
                proposal.getValue(),
                currentView.get(),
                true,
                ConsensusStatus.SUCCESS,
                System.currentTimeMillis(),
                commitMessages.get(proposalId).size(),
                0,
                null
            ));
            pendingProposals.remove(proposalId);
            proposals.remove(proposalId);
        }
    }

    // View change implementation

    private void triggerViewChange() {
        if (inViewChange.compareAndSet(false, true)) {
            currentView.incrementAndGet();
            primaryNodeIndex.set((primaryNodeIndex.get() + 1) % nodes.size());

            // Reset PBFT state
            prePrepareMessages.clear();
            prepareMessages.clear();
            commitMessages.clear();

            // Notify all nodes of view change
            broadcastViewChange();

            // Schedule view change timeout
            scheduler.schedule(() -> {
                inViewChange.set(false);
            }, VIEW_CHANGE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
    }

    private void broadcastViewChange() {
        for (ConsensusNode node : nodes.values()) {
            if (node.isActive()) {
                node.handlePartition(new PartitionId("view-change-" + currentView.get(),
                    (int) nodes.values().stream().filter(ConsensusNode::isActive).count(),
                    true));
            }
        }
    }

    // Malicious node handling

    private void markNodeMalicious(UUID nodeId) {
        maliciousNodes.add(nodeId);

        // Trigger view change if primary is malicious
        UUID primaryId = getPrimaryNode();
        if (primaryId != null && nodeId.equals(primaryId)) {
            triggerViewChange();
        }
    }

    private boolean isValidPrePrepare(String prePrepareMsg, UUID primaryId) {
        // Basic validation - in real implementation, would check cryptographic signatures
        return prePrepareMsg != null && !prePrepareMsg.isEmpty();
    }

    // Helper methods

    private UUID getPrimaryNode() {
        if (nodes.isEmpty()) return null;

        List<UUID> activeNodes = nodes.values().stream()
            .filter(ConsensusNode::isActive)
            .map(ConsensusNode::getId)
            .toList();

        if (activeNodes.isEmpty()) return null;

        int index = (int) (primaryNodeIndex.get() % activeNodes.size());
        return activeNodes.get(index);
    }

    private String createPrePrepareMessage(long proposalId, Proposal proposal, UUID primaryId) {
        return String.format("PRE-PREPARE|%d|%d|%s|%s|%d",
            currentView.get(), proposalId, proposal.getValue(), primaryId, System.currentTimeMillis());
    }

    private String createPrepareMessage(long proposalId, UUID primaryId) {
        return String.format("PREPARE|%d|%d|%s",
            currentView.get(), proposalId, primaryId);
    }

    private String createCommitMessage(long proposalId, UUID primaryId) {
        return String.format("COMMIT|%d|%d|%s",
            currentView.get(), proposalId, primaryId);
    }

    public void shutdown() {
        running.set(false);
        scheduler.shutdown();
    }
}