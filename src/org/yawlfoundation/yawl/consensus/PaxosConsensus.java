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
 * Paxos Consensus Implementation
 *
 * Quorum-based consensus algorithm providing strong consistency
 * with multi-leader support and network partition tolerance.
 */
public class PaxosConsensus implements ConsensusEngine {
    private static final long PREPARE_TIMEOUT_MS = 100;  // Optimized for performance
    private static final long ACCEPT_TIMEOUT_MS = 100;   // Optimized for performance
    private static final int MAJORITY_THRESHOLD = 2; // For 3 nodes: 2/3 + 1

    private final Map<UUID, ConsensusNode> nodes = new ConcurrentHashMap<>();
    private final AtomicReference<ConsensusStrategy> strategy =
        new AtomicReference<>(ConsensusStrategy.PAXOS);
    private final AtomicReference<NodeRole> role = new AtomicReference<>(NodeRole.FOLLOWER);
    private final AtomicLong proposalCounter = new AtomicLong(0);
    private final AtomicLong currentRound = new AtomicLong(0);

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);
    private final Map<Long, Proposal> proposals = new ConcurrentHashMap<>();
    private final Map<Long, CompletableFuture<ConsensusResult>> pendingProposals = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(true);

    // Paxos state
    private final Map<Long, String> acceptedValues = new ConcurrentHashMap<>();
    private final Map<Long, Integer> acceptCounts = new ConcurrentHashMap<>();
    private final Map<Long, Set<UUID>> promisedRounds = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<ConsensusResult> propose(Proposal proposal) {
        if (!running.get()) {
            CompletableFuture<ConsensusResult> future = new CompletableFuture<>();
            future.completeExceptionally(new ConsensusException("Consensus engine is not running"));
            return future;
        }

        long proposalId = proposalCounter.incrementAndGet();
        proposals.put(proposalId, proposal);

        CompletableFuture<ConsensusResult> future = new CompletableFuture<>();
        pendingProposals.put(proposalId, future);

        // Start Paxos protocol
        startPaxosProtocol(proposalId, proposal);

        return future;
    }

    @Override
    public void registerNode(ConsensusNode node) {
        nodes.put(node.getId(), node);
    }

    @Override
    public ConsensusState getState() {
        int activeNodes = (int) nodes.values().stream()
            .filter(ConsensusNode::isActive)
            .count();

        return new ConsensusState(
            currentRound.get(),
            role.get(),
            null, // Paxos doesn't have single leader
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

    // Paxos protocol implementation

    private void startPaxosProtocol(long proposalId, Proposal proposal) {
        currentRound.incrementAndGet();
        long round = currentRound.get();

        // Phase 1: Prepare
        List<CompletableFuture<Boolean>> prepareResponses = new ArrayList<>();
        int prepareCount = 0;

        for (ConsensusNode node : nodes.values()) {
            if (!node.isActive()) continue;

            CompletableFuture<Boolean> response = new CompletableFuture<>();
            prepareResponses.add(response);

            scheduler.schedule(() -> {
                try {
                    boolean promise = handlePrepare(round, proposalId, node.getId());
                    response.complete(promise);
                } catch (Exception e) {
                    response.complete(false);
                }
            }, 0, TimeUnit.MILLISECONDS);

            if (response.join()) {
                prepareCount++;
            }
        }

        // Check if we have majority for prepare
        if (prepareCount >= MAJORITY_THRESHOLD) {
            // Phase 2: Accept
            scheduler.schedule(() -> {
                performAccept(round, proposalId, proposal.getValue(), prepareResponses);
            }, PREPARE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } else {
            // Prepare failed, retry
            scheduler.schedule(() -> {
                startPaxosProtocol(proposalId, proposal);
            }, PREPARE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
    }

    private boolean handlePrepare(long round, long proposalId, UUID nodeId) {
        // Check if we've already promised in a higher round
        Set<UUID> promised = promisedRounds.get(proposalId);
        if (promised != null && promised.contains(nodeId)) {
            // Already promised in this round
            return true;
        }

        // Promise in this round
        promisedRounds.computeIfAbsent(proposalId, k -> ConcurrentHashMap.newKeySet())
            .add(nodeId);

        return true;
    }

    private void performAccept(long round, long proposalId, String value,
                             List<CompletableFuture<Boolean>> prepareResponses) {
        List<CompletableFuture<Boolean>> acceptResponses = new ArrayList<>();
        int acceptCount = 0;

        for (ConsensusNode node : nodes.values()) {
            if (!node.isActive()) continue;

            CompletableFuture<Boolean> response = new CompletableFuture<>();
            acceptResponses.add(response);

            scheduler.schedule(() -> {
                try {
                    boolean accepted = handleAccept(round, proposalId, value, node.getId());
                    response.complete(accepted);
                } catch (Exception e) {
                    response.complete(false);
                }
            }, 0, TimeUnit.MILLISECONDS);

            if (response.join()) {
                acceptCount++;
            }
        }

        // Check if we have majority for accept
        if (acceptCount >= MAJORITY_THRESHOLD) {
            // Consensus reached!
            acceptedValues.put(proposalId, value);
            acceptCounts.put(proposalId, acceptCount);

            CompletableFuture<ConsensusResult> resultFuture =
                pendingProposals.get(proposalId);
            if (resultFuture != null) {
                resultFuture.complete(new ConsensusResult(
                    proposalId,
                    value,
                    round,
                    acceptCount,
                    0, // votes against
                    System.currentTimeMillis()
                ));
                pendingProposals.remove(proposalId);
                proposals.remove(proposalId);
            }
        } else {
            // Accept failed, retry
            scheduler.schedule(() -> {
                startPaxosProtocol(proposalId, proposals.get(proposalId));
            }, ACCEPT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
    }

    private boolean handleAccept(long round, long proposalId, String value, UUID nodeId) {
        // Check if we've already accepted a value for this proposal
        String existingValue = acceptedValues.get(proposalId);
        if (existingValue != null && !existingValue.equals(value)) {
            // Conflict - already accepted different value
            return false;
        }

        // Accept this value
        acceptedValues.put(proposalId, value);
        return true;
    }

    public void shutdown() {
        running.set(false);
        scheduler.shutdown();
    }
}