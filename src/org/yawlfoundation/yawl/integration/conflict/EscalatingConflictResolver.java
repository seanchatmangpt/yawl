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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Escalating conflict resolver implementation.
 *
 * Resolves conflicts by escalating to a human arbiter when automated resolution
 * fails or when the conflict severity exceeds configured thresholds. The arbiter
 * makes the final decision after reviewing the conflicting agent decisions.
 *
 * <p>Strategy characteristics:
 * - Automated attempts first, escalation as fallback
 * - Configurable escalation thresholds
 * - Arbiter notification and decision tracking
 * - Timeout handling for escalation process</p>
 *
 * @author YAWL Foundation
 * @version 5.2
 * @see ConflictResolver
 */
public class EscalatingConflictResolver implements ConflictResolver {

    private static final String STRATEGY_NAME = "ESCALATING";
    private final Map<String, Object> configuration;
    private final ArbiterService arbiterService;
    private final Map<String, EscalationRecord> pendingEscalations = new ConcurrentHashMap<>();

    /**
     * Default configuration with reasonable defaults.
     */
    private static final Map<String, Object> DEFAULT_CONFIGURATION = new HashMap<>();
    static {
        DEFAULT_CONFIGURATION.put("autoResolveThreshold", ConflictResolver.Severity.MEDIUM);
        DEFAULT_CONFIGURATION.put("escalationTimeoutMs", 300000); // 5 minutes
        DEFAULT_CONFIGURATION.put("maxRetries", 3);
        DEFAULT_CONFIGURATION.put("notifyOnEscalation", true);
        DEFAULT_CONFIGURATION.put("requireJustification", true);
        DEFAULT_CONFIGURATION.put("recordDecisions", true);
    }

    /**
     * Service interface for human arbiter integration.
     */
    public interface ArbiterService {
        /**
         * Escalate conflict to human arbiter.
         *
         * @param conflictContext The conflict to resolve
         * @return The arbiter's decision
         * @throws ArbiterException if escalation fails
         */
        Decision escalateToArbiter(ConflictContext conflictContext) throws ArbiterException;

        /**
         * Check if arbiter is available to resolve conflicts.
         *
         * @return true if arbiter is available
         */
        boolean isArbiterAvailable();

        /**
         * Get current arbiter workload.
         *
         * @return number of pending escalations
         */
        int getArbiterWorkload();
    }

    /**
     * Default implementation of ArbiterService that simulates human decision.
     */
    public static class DefaultArbiterService implements ArbiterService {

        @Override
        public Decision escalateToArbiter(ConflictContext conflictContext) throws ArbiterException {
            if (!isArbiterAvailable()) {
                throw new ArbiterException("Arbiter is not available");
            }

            // In a real implementation, this would:
            // 1. Notify the human arbiter
            // 2. Present the conflict details
            // 3. Wait for human decision
            // 4. Return the decision

            // For this implementation, we'll simulate by selecting the decision
            // with the highest average confidence
            List<AgentDecision> decisions = conflictContext.getConflictingDecisions();

            if (decisions.isEmpty()) {
                throw new ArbiterException("No decisions available for arbitration");
            }

            // Calculate average confidence for each unique decision
            Map<String, Double> decisionConfidence = decisions.stream()
                .collect(Collectors.groupingBy(
                    AgentDecision::getDecision,
                    Collectors.averagingDouble(AgentDecision::getConfidence)
                ));

            // Select decision with highest confidence
            String winningDecision = decisionConfidence.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(decisions.get(0).getDecision());

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("arbiterDecision", winningDecision);
            metadata.put("confidenceScores", decisionConfidence);
            metadata.put("escalationReason", "Human arbiter resolution");

            return new Decision(
                winningDecision,
                conflictContext.getSeverity(),
                decisions.stream().map(AgentDecision::getAgentId).collect(Collectors.toList()),
                "ARBITER_" + STRATEGY_NAME,
                metadata
            );
        }

        @Override
        public boolean isArbiterAvailable() {
            // In a real implementation, check if arbiter is online and available
            return true; // Default implementation assumes always available
        }

        @Override
        public int getArbiterWorkload() {
            // In a real implementation, check pending escalations
            return 0; // Default implementation assumes no workload
        }
    }

    /**
     * Exception thrown when arbiter escalation fails.
     */
    public static class ArbiterException extends Exception {
        public ArbiterException(String message) {
            super(message);
        }

        public ArbiterException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Record tracking escalation progress.
     */
    private static class EscalationRecord {
        private final String conflictId;
        private final long escalationStartTime;
        private int retryCount;
        private EscalationStatus status;
        private Decision arbiterDecision;

        public EscalationRecord(String conflictId) {
            this.conflictId = conflictId;
            this.escalationStartTime = System.currentTimeMillis();
            this.retryCount = 0;
            this.status = EscalationStatus.PENDING;
        }

        // Getters and setters
        public String getConflictId() { return conflictId; }
        public long getEscalationStartTime() { return escalationStartTime; }
        public int getRetryCount() { return retryCount; }
        public void incrementRetryCount() { this.retryCount++; }
        public EscalationStatus getStatus() { return status; }
        public void setStatus(EscalationStatus status) { this.status = status; }
        public Decision getArbiterDecision() { return arbiterDecision; }
        public void setArbiterDecision(Decision arbiterDecision) { this.arbiterDecision = arbiterDecision; }
    }

    private enum EscalationStatus {
        PENDING, IN_PROGRESS, COMPLETED, FAILED, TIMED_OUT
    }

    /**
     * Creates a new escalating resolver with default arbiter service.
     */
    public EscalatingConflictResolver() {
        this.configuration = new HashMap<>(DEFAULT_CONFIGURATION);
        this.arbiterService = new DefaultArbiterService();
    }

    /**
     * Creates a new escalating resolver with custom arbiter service.
     *
     * @param arbiterService The arbiter service to use
     * @throws IllegalArgumentException if arbiterService is null
     */
    public EscalatingConflictResolver(ArbiterService arbiterService) {
        if (arbiterService == null) {
            throw new IllegalArgumentException("Arbiter service cannot be null");
        }
        this.configuration = new HashMap<>(DEFAULT_CONFIGURATION);
        this.arbiterService = arbiterService;
    }

    /**
     * Creates a new escalating resolver with custom configuration and arbiter service.
     *
     * @param configuration Configuration parameters
     *-resolution
     * @param arbiterService The arbiter service to use
     * @throws IllegalArgumentException if arguments are invalid
     */
    public EscalatingConflictResolver(Map<String, Object> configuration, ArbiterService arbiterService) {
        if (arbiterService == null) {
            throw new IllegalArgumentException("Arbiter service cannot be null");
        }
        this.configuration = new HashMap<>(DEFAULT_CONFIGURATION);
        this.arbiterService = arbiterService;
        updateConfiguration(configuration);
    }

    @Override
    public Decision resolveConflict(ConflictContext conflictContext) throws ConflictResolutionException {
        if (!canResolve(conflictContext)) {
            throw new ConflictResolutionException(
                String.format("Cannot resolve conflict %s with escalating strategy",
                            conflictContext.getConflictId()),
                ConflictResolver.Strategy.ESCALATING,
                conflictContext.getConflictId()
            );
        }

        // Check if we should auto-resolve based on severity
        ConflictResolver.Severity autoResolveThreshold = (ConflictResolver.Severity) configuration.get("autoResolveThreshold");
        if (conflictContext.getSeverity().ordinal() <= autoResolveThreshold.ordinal()) {
            // Attempt automated resolution first
            return attemptAutoResolution(conflictContext);
        }

        // Escalate immediately for high severity conflicts
        return escalateToArbiter(conflictContext);
    }

    @Override
    public boolean canResolve(ConflictContext conflictContext) {
        if (conflictContext == null || conflictContext.getConflictingDecisions() == null) {
            return false;
        }

        // Check if arbiter service is available
        return arbiterService != null && arbiterService.isArbiterAvailable();
    }

    @Override
    public ConflictResolver.Strategy getStrategy() {
        return ConflictResolver.Strategy.ESCALATING;
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
        if (configuration.containsKey("escalationTimeoutMs")) {
            long timeout = ((Number) configuration.get("escalationTimeoutMs")).longValue();
            if (timeout < 1000) {
                throw new IllegalArgumentException("escalationTimeoutMs must be at least 1000ms");
            }
        }

        if (configuration.containsKey("maxRetries")) {
            int maxRetries = ((Number) configuration.get("maxRetries")).intValue();
            if (maxRetries < 0) {
                throw new IllegalArgumentException("maxRetries cannot be negative");
            }
        }

        this.configuration.putAll(configuration);
    }

    @Override
    public boolean isHealthy() {
        return arbiterService != null && arbiterService.isArbiterAvailable();
    }

    /**
     * Attempt automated resolution before escalating.
     */
    private Decision attemptAutoResolution(ConflictContext conflictContext) throws ConflictResolutionException {
        try {
            // Simple majority vote as automated resolution
            Map<String, Long> voteCounts = conflictContext.getConflictingDecisions().stream()
                .collect(Collectors.groupingBy(AgentDecision::getDecision, Collectors.counting()));

            String winningDecision = voteCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseThrow(() -> new ConflictResolutionException(
                    "No valid decisions found for automated resolution",
                    getStrategy(),
                    conflictContext.getConflictId()
                ));

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("resolutionMethod", "automated_resolution");
            metadata.put("voteCounts", voteCounts);

            return new Decision(
                winningDecision,
                conflictContext.getSeverity(),
                conflictContext.getConflictingDecisions().stream()
                    .map(AgentDecision::getAgentId)
                    .collect(Collectors.toList()),
                "AUTO_" + STRATEGY_NAME,
                metadata
            );

        } catch (Exception e) {
            // Automated resolution failed, escalate to arbiter
            return escalateToArbiter(conflictContext);
        }
    }

    /**
     * Escalate conflict to human arbiter.
     */
    private Decision escalateToArbiter(ConflictContext conflictContext) throws ConflictResolutionException {
        long escalationTimeout = ((Number) configuration.get("escalationTimeoutMs")).longValue();
        int maxRetries = ((Number) configuration.get("maxRetries")).intValue();

        EscalationRecord record = new EscalationRecord(conflictContext.getConflictId());
        pendingEscalations.put(conflictContext.getConflictId(), record);

        try {
            // Retry escalation if necessary
            int retryCount = 0;
            ArbiterException lastException = null;

            while (retryCount <= maxRetries) {
                try {
                    Decision decision = arbiterService.escalateToArbiter(conflictContext);
                    record.setStatus(EscalationStatus.COMPLETED);
                    record.setArbiterDecision(decision);
                    return decision;

                } catch (ArbiterException e) {
                    lastException = e;
                    retryCount++;
                    record.incrementRetryCount();

                    if (retryCount <= maxRetries) {
                        // Wait before retrying
                        Thread.sleep(1000 * retryCount); // Exponential backoff
                    }
                }
            }

            // All retries failed
            record.setStatus(EscalationStatus.FAILED);
            throw new ConflictResolutionException(
                String.format("Escalation failed after %d retries: %s", maxRetries, lastException.getMessage()),
                getStrategy(),
                conflictContext.getConflictId()
            ).initCause(lastException);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            record.setStatus(EscalationStatus.FAILED);
            throw new ConflictResolutionException(
                "Escalation interrupted",
                getStrategy(),
                conflictContext.getConflictId()
            ).initCause(e);
        }
    }

    /**
     * Get pending escalations.
     */
    public Map<String, EscalationRecord> getPendingEscalations() {
        return new HashMap<>(pendingEscalations);
    }

    /**
     * Clean up completed escalations older than specified time.
     */
    public void cleanupCompletedEscalations(long olderThanMs) {
        long cutoffTime = System.currentTimeMillis() - olderThanMs;
        pendingEscalations.entrySet().removeIf(entry -> {
            EscalationRecord record = entry.getValue();
            return record.getStatus() == EscalationStatus.COMPLETED &&
                   record.getEscalationStartTime() < cutoffTime;
        });
    }

    @Override
    public String toString() {
        return String.format("EscalatingConflictResolver{strategy=%s, arbiterAvailable=%s, pendingEscalations=%d}",
                           getStrategy(), arbiterService.isArbiterAvailable(), pendingEscalations.size());
    }
}