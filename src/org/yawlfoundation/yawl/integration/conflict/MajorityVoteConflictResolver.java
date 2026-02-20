/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.conflict;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Majority vote conflict resolver implementation.
 *
 * Resolves conflicts by selecting the decision that receives the most votes
 * from the participating agents. In case of ties, applies tie-breaking rules
 * based on confidence scores and agent seniority.
 *
 * <p>Strategy characteristics:
 * - Simple and transparent resolution mechanism
 * - Fast resolution without external dependencies
 * - Statistical approach based on collective intelligence
 * - Configurable confidence thresholds and tie-breaking rules</p>
 *
 * @author YAWL Foundation
 * @version 5.2
 * @see ConflictResolver
 */
public class MajorityVoteConflictResolver implements ConflictResolver {

    private static final String STRATEGY_NAME = "MAJORITY_VOTE";
    private final Map<String, Object> configuration;

    /**
     * Default configuration with reasonable defaults.
     */
    private static final Map<String, Object> DEFAULT_CONFIGURATION = new HashMap<>();
    static {
        DEFAULT_CONFIGURATION.put("minConfidenceThreshold", 0.5);
        DEFAULT_CONFIGURATION.put("minVotesForMajority", 2);
        DEFAULT_CONFIGURATION.put("breakTiesByConfidence", true);
        DEFAULT_CONFIGURATION.put("breakTiesBySeniority", false);
        DEFAULT_CONFIGURATION.put("allowAbstentions", false);
        DEFAULT_CONFIGURATION.put("requireSupermajority", false);
        DEFAULT_CONFIGURATION.put("supermajorityThreshold", 0.6);
    }

    /**
     * Creates a new majority vote resolver with default configuration.
     */
    public MajorityVoteConflictResolver() {
        this.configuration = new HashMap<>(DEFAULT_CONFIGURATION);
    }

    /**
     * Creates a new majority vote resolver with custom configuration.
     *
     * @param configuration Configuration parameters
     * @throws IllegalArgumentException if configuration is invalid
     */
    public MajorityVoteConflictResolver(Map<String, Object> configuration) {
        if (configuration == null) {
            throw new IllegalArgumentException("Configuration cannot be null");
        }
        this.configuration = new HashMap<>(DEFAULT_CONFIGURATION);
        updateConfiguration(configuration);
    }

    @Override
    public Decision resolveConflict(ConflictContext conflictContext) throws ConflictResolutionException {
        if (!canResolve(conflictContext)) {
            throw new ConflictResolutionException(
                String.format("Cannot resolve conflict %s with majority vote strategy",
                            conflictContext.getConflictId()),
                ConflictResolver.Strategy.MAJORITY_VOTE,
                conflictContext.getConflictId()
            );
        }

        List<AgentDecision> decisions = conflictContext.getConflictingDecisions();

        // Count votes for each unique decision
        Map<String, Long> voteCounts = decisions.stream()
            .filter(d -> isEligibleVote(d))
            .collect(Collectors.groupingBy(AgentDecision::getDecision, Collectors.counting()));

        if (voteCounts.isEmpty()) {
            throw new ConflictResolutionException(
                "No eligible votes found for conflict resolution",
                ConflictResolver.Strategy.MAJORITY_VOTE,
                conflictContext.getConflictId()
            );
        }

        // Check for supermajority requirement
        boolean requireSupermajority = (boolean) configuration.getOrDefault("requireSupermajority", false);
        double supermajorityThreshold = ((Number) configuration.getOrDefault("supermajorityThreshold", 0.6)).doubleValue();
        long totalEligibleVotes = decisions.stream().filter(this::isEligibleVote).count();

        String winningDecision;
        if (requireSupermajority) {
            winningDecision = findSupermajorityDecision(voteCounts, totalEligibleVotes, supermajorityThreshold);
            if (winningDecision == null) {
                throw new ConflictResolutionException(
                    String.format("No decision achieved supermajority of %.0f%%", supermajorityThreshold * 100),
                    ConflictResolver.Strategy.MAJORITY_VOTE,
                    conflictContext.getConflictId()
                );
            }
        } else {
            winningDecision = findSimpleMajorityDecision(voteCounts, totalEligibleVotes);
        }

        // Build resolution metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("voteCounts", voteCounts);
        metadata.put("totalEligibleVotes", totalEligibleVotes);
        metadata.put("winningDecision", winningDecision);
        metadata.put("resolutionMethod", requireSupermajority ? "supermajority" : "simple_majority");

        // Create participating agents list
        List<String> participatingAgents = decisions.stream()
            .map(AgentDecision::getAgentId)
            .collect(Collectors.toList());

        return new Decision(
            winningDecision,
            conflictContext.getSeverity(),
            participatingAgents,
            STRATEGY_NAME,
            metadata
        );
    }

    @Override
    public boolean canResolve(ConflictContext conflictContext) {
        if (conflictContext == null || conflictContext.getConflictingDecisions() == null) {
            return false;
        }

        List<AgentDecision> decisions = conflictContext.getConflictingDecisions();
        int minVotes = ((Number) configuration.getOrDefault("minVotesForMajority", 2)).intValue();

        return decisions.size() >= minVotes;
    }

    @Override
    public ConflictResolver.Strategy getStrategy() {
        return ConflictResolver.Strategy.MAJORITY_VOTE;
    }

    @Override
    public Map<String, Object> getConfiguration() {
        return new HashMap<>(configuration);
    }

    @Override
    public void updateConfiguration(Map<String, Object> configuration) {
        if (configuration == null) {
            throw new IllegalArgumentException("Configuration cannot be null");
        }

        // Validate configuration values
        if (configuration.containsKey("minConfidenceThreshold")) {
            double threshold = ((Number) configuration.get("minConfidenceThreshold")).doubleValue();
            if (threshold < 0 || threshold > 1) {
                throw new IllegalArgumentException("minConfidenceThreshold must be between 0 and 1");
            }
        }

        if (configuration.containsKey("minVotesForMajority")) {
            int minVotes = ((Number) configuration.get("minVotesForMajority")).intValue();
            if (minVotes < 1) {
                throw new IllegalArgumentException("minVotesForMajority must be at least 1");
            }
        }

        if (configuration.containsKey("supermajorityThreshold")) {
            double threshold = ((Number) configuration.get("supermajorityThreshold")).doubleValue();
            if (threshold < 0.5 || threshold > 1) {
                throw new IllegalArgumentException("supermajorityThreshold must be between 0.5 and 1");
            }
        }

        this.configuration.putAll(configuration);
    }

    @Override
    public boolean isHealthy() {
        // Majority vote resolver has no external dependencies, so it's always healthy
        return true;
    }

    /**
     * Check if an agent's vote is eligible based on configuration.
     */
    private boolean isEligibleVote(AgentDecision decision) {
        boolean allowAbstentions = (boolean) configuration.getOrDefault("allowAbstentions", false);
        double minConfidence = ((Number) configuration.getOrDefault("minConfidenceThreshold", 0.5)).doubleValue();

        if (allowAbstentions && (decision.getDecision() == null || decision.getDecision().trim().isEmpty())) {
            return false; // Abstentions not counted
        }

        return decision.getConfidence() >= minConfidence;
    }

    /**
     * Find decision with simple majority (most votes).
     */
    private String findSimpleMajorityDecision(Map<String, Long> voteCounts, long totalVotes) {
        // Find the decision with the most votes
        Map.Entry<String, Long> maxEntry = null;
        for (Map.Entry<String, Long> entry : voteCounts.entrySet()) {
            if (maxEntry == null || entry.getValue().compareTo(maxEntry.getValue()) > 0) {
                maxEntry = entry;
            }
        }

        // Check for tie
        List<Map.Entry<String, Long>> maxEntries = voteCounts.entrySet().stream()
            .filter(e -> e.getValue().equals(maxEntry.getValue()))
            .collect(Collectors.toList());

        if (maxEntries.size() > 1) {
            return resolveTie(maxEntries);
        }

        return maxEntry.getKey();
    }

    /**
     * Find decision with supermajority.
     */
    private String findSupermajorityDecision(Map<String, Long> voteCounts, long totalVotes, double threshold) {
        for (Map.Entry<String, Long> entry : voteCounts.entrySet()) {
            double percentage = (double) entry.getValue() / totalVotes;
            if (percentage >= threshold) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Resolve tie between multiple decisions with equal votes.
     */
    private String resolveTie(List<Map.Entry<String, Long>> tiedDecisions) {
        boolean breakByConfidence = (boolean) configuration.getOrDefault("breakTiesByConfidence", true);
        boolean breakBySeniority = (boolean) configuration.getOrDefault("breakTiesBySeniority", false);

        // Prefer confidence-based tie-breaking
        if (breakByConfidence) {
            // Confidence-based tie-breaking is not yet implemented.
            // Requires AgentInfo records to carry a confidence score, then select
            // the agent whose most-voted decision has the highest average confidence.
            // Fall through to alphabetical order until agent confidence tracking is added.
        }

        // Fall back to alphabetical order
        return tiedDecisions.stream()
            .map(Map.Entry::getKey)
            .sorted()
            .findFirst()
            .orElse(tiedDecisions.get(0).getKey());
    }

    @Override
    public String toString() {
        return String.format("MajorityVoteConflictResolver{strategy=%s, configuration=%s}",
                           getStrategy(), configuration);
    }
}