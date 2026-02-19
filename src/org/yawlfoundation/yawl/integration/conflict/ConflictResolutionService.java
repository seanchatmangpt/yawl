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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Central coordination service for conflict resolution in multi-agent systems.
 *
 * Manages multiple conflict resolver strategies, routes conflicts to appropriate
 * resolvers based on context, and provides monitoring and reporting capabilities.
 *
 * <p>Key features:
 * - Strategy selection based on conflict characteristics
 * - Pluggable resolver architecture
 * - Asynchronous conflict resolution
 * - Comprehensive logging and audit trail
 * - Performance metrics and monitoring</p>
 *
 * @author YAWL Foundation
 * @version 5.2
 * @see ConflictResolver
 * @see ConflictResolutionIntegrationService
 */
public class ConflictResolutionService {

    private static final ConflictResolutionService INSTANCE = new ConflictResolutionService();

    private final Map<String, ConflictResolver> resolvers = new ConcurrentHashMap<>();
    private final Map<String, ResolutionResult> resolutionHistory = new ConcurrentHashMap<>();
    private final AtomicReference<ExecutorService> executorServiceRef;
    private final ConflictSelectionStrategy selectionStrategy;
    private final ResolutionObserver observer;
    private final Map<String, Object> globalConfiguration;

    /**
     * Default configuration for the service.
     */
    private static final Map<String, Object> DEFAULT_CONFIGURATION = new HashMap<>();
    static {
        DEFAULT_CONFIGURATION.put("defaultResolver", "MAJORITY_VOTE");
        DEFAULT_CONFIGURATION.put("asyncProcessing", true);
        DEFAULT_CONFIGURATION.put("executorThreads", 4);
        DEFAULT_CONFIGURATION.put("maxHistorySize", 1000);
        DEFAULT_CONFIGURATION.put("enableMetrics", true);
        DEFAULT_CONFIGURATION.put("logLevel", "INFO");
        DEFAULT_CONFIGURATION.put("auditRetentionDays", 30);
    }

    /**
     * Result of a conflict resolution operation.
     */
    public static class ResolutionResult {
        private final String resolutionId;
        private final String conflictId;
        private final ConflictResolver resolver;
        private final Decision decision;
        private final ResolutionStatus status;
        private final long startTime;
        private final long endTime;
        private final Exception error;
        private final Map<String, Object> metadata;

        public ResolutionResult(String resolutionId, String conflictId, ConflictResolver resolver,
                              Decision decision, ResolutionStatus status,
                              long startTime, long endTime, Exception error,
                              Map<String, Object> metadata) {
            this.resolutionId = resolutionId;
            this.conflictId = conflictId;
            this.resolver = resolver;
            this.decision = decision;
            this.status = status;
            this.startTime = startTime;
            this.endTime = endTime;
            this.error = error;
            this.metadata = metadata;
        }

        // Getters
        public String getResolutionId() { return resolutionId; }
        public String getConflictId() { return conflictId; }
        public ConflictResolver getResolver() { return resolver; }
        public Decision getDecision() { return decision; }
        public ResolutionStatus getStatus() { return status; }
        public long getStartTime() { return startTime; }
        public long getEndTime() { return endTime; }
        public long getDurationMs() { return endTime - startTime; }
        public Exception getError() { return error; }
        public Map<String, Object> getMetadata() { return metadata; }
    }

    /**
     * Resolution status enumeration.
     */
    public enum ResolutionStatus {
        PENDING, IN_PROGRESS, COMPLETED, FAILED, CANCELLED, TIMEOUT
    }

    /**
     * Strategy for selecting which resolver to use for a conflict.
     */
    public interface ConflictSelectionStrategy {
        /**
         * Select the most appropriate resolver for the given conflict.
         *
         * @param conflictContext The conflict context
         * @param availableResolvers Map of available resolvers
         * @return The selected resolver, or null if none suitable
         */
        ConflictResolver selectResolver(ConflictContext conflictContext,
                                      Map<String, ConflictResolver> availableResolvers);
    }

    /**
     * Default selection strategy that matches conflict severity to resolver capability.
     */
    public static class DefaultSelectionStrategy implements ConflictSelectionStrategy {

        @Override
        public ConflictResolver selectResolver(ConflictContext conflictContext,
                                            Map<String, ConflictResolver> availableResolvers) {
            if (conflictContext == null || availableResolvers.isEmpty()) {
                return null;
            }

            // Priority-based selection based on conflict severity
            ConflictResolver.Severity severity = conflictContext.getSeverity();

            // For critical conflicts, prefer human fallback
            if (severity == ConflictResolver.Severity.CRITICAL) {
                return findResolverByType(availableResolvers, ConflictResolver.Strategy.HUMAN_FALLBACK);
            }

            // For high severity, prefer escalating resolver
            if (severity == ConflictResolver.Severity.HIGH) {
                return findResolverByType(availableResolvers, ConflictResolver.Strategy.ESCALATING);
            }

            // For medium severity, use majority vote with escalation fallback
            ConflictResolver resolver = findResolverByType(availableResolvers, ConflictResolver.Strategy.MAJORITY_VOTE);
            if (resolver != null) {
                return resolver;
            }

            // Fallback to any available resolver
            return availableResolvers.values().stream()
                .findFirst()
                .orElse(null);
        }

        private ConflictResolver findResolverByType(Map<String, ConflictResolver> resolvers,
                                                 ConflictResolver.Strategy type) {
            return resolvers.values().stream()
                .filter(r -> r.getStrategy() == type)
                .findFirst()
                .orElse(null);
        }
    }

    /**
     * Observer interface for monitoring resolution events.
     */
    public interface ResolutionObserver {
        /**
         * Called when a resolution starts.
         */
        void onResolutionStart(String resolutionId, String conflictId, ConflictResolver resolver);

        /**
         * Called when a resolution completes successfully.
         */
        void onResolutionComplete(String resolutionId, Decision decision);

        /**
         * Called when a resolution fails.
         */
        void onResolutionFailure(String resolutionId, Exception error);
    }

    /**
     * Default observer that logs resolution events.
     */
    public static class DefaultObserver implements ResolutionObserver {

        @Override
        public void onResolutionStart(String resolutionId, String conflictId, ConflictResolver resolver) {
            System.out.printf("[INFO] Resolution started: ID=%s, Conflict=%s, Resolver=%s%n",
                            resolutionId, conflictId, resolver.getStrategy());
        }

        @Override
        public void onResolutionComplete(String resolutionId, Decision decision) {
            System.out.printf("[INFO] Resolution completed: ID=%s, Decision=%s, Duration=%dms%n",
                            resolutionId, decision.getResolvedValue(), decision.getResolutionTimestamp());
        }

        @Override
        public void onResolutionFailure(String resolutionId, Exception error) {
            System.err.printf("[ERROR] Resolution failed: ID=%s, Error=%s%n",
                            resolutionId, error.getMessage());
        }
    }

    /**
     * Get the singleton instance of the conflict resolution service.
     */
    public static ConflictResolutionService getInstance() {
        return INSTANCE;
    }

    /**
     * Private constructor for singleton pattern.
     */
    private ConflictResolutionService() {
        this.globalConfiguration = new HashMap<>(DEFAULT_CONFIGURATION);
        this.selectionStrategy = new DefaultSelectionStrategy();
        this.observer = new DefaultObserver();
        this.executorServiceRef = new AtomicReference<>(Executors.newFixedThreadPool(
            ((Number) globalConfiguration.get("executorThreads")).intValue()
        ));
    }

    /**
     * Register a conflict resolver with the service.
     *
     * @param name Unique name for the resolver
     * @param resolver The resolver to register
     * @throws IllegalArgumentException if name is empty or resolver is null
     */
    public void registerResolver(String name, ConflictResolver resolver) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Resolver name cannot be empty");
        }
        if (resolver == null) {
            throw new IllegalArgumentException("Resolver cannot be null");
        }

        resolvers.put(name, resolver);
        System.out.printf("[INFO] Registered resolver: %s (%s)%n", name, resolver.getStrategy());
    }

    /**
     * Unregister a conflict resolver.
     *
     * @param name The name of the resolver to unregister
     * @return true if resolver was found and removed, false otherwise
     */
    public boolean unregisterResolver(String name) {
        return resolvers.remove(name) != null;
    }

    /**
     * Resolve a conflict synchronously.
     *
     * @param conflictContext The conflict to resolve
     * @return The resolution result
     * @throws ConflictResolutionException if resolution fails
     */
    public ResolutionResult resolveConflict(ConflictContext conflictContext) throws ConflictResolutionException {
        if (conflictContext == null) {
            throw new IllegalArgumentException("Conflict context cannot be null");
        }

        String resolutionId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();

        try {
            // Select resolver
            ConflictResolver resolver = selectResolver(conflictContext);
            if (resolver == null) {
                throw new ConflictResolutionException(
                    "No suitable resolver available for conflict",
                    ConflictResolver.Strategy.HYBRID,
                    conflictContext.getConflictId()
                );
            }

            // Notify observer
            observer.onResolutionStart(resolutionId, conflictContext.getConflictId(), resolver);

            // Resolve conflict
            Decision decision = resolver.resolveConflict(conflictContext);

            // Create result
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("resolverName", getResolverName(resolver));
            ResolutionResult result = new ResolutionResult(
                resolutionId,
                conflictContext.getConflictId(),
                resolver,
                decision,
                ResolutionStatus.COMPLETED,
                startTime,
                System.currentTimeMillis(),
                null,
                metadata
            );

            // Store in history
            addToHistory(result);

            // Notify observer
            observer.onResolutionComplete(resolutionId, decision);

            return result;

        } catch (Exception e) {
            Map<String, Object> metadata = new HashMap<>();
            ResolutionResult result = new ResolutionResult(
                resolutionId,
                conflictContext.getConflictId(),
                null,
                null,
                ResolutionStatus.FAILED,
                startTime,
                System.currentTimeMillis(),
                e,
                metadata
            );

            addToHistory(result);
            observer.onResolutionFailure(resolutionId, e);
            throw e;
        }
    }

    /**
     * Resolve a conflict asynchronously.
     *
     * @param conflictContext The conflict to resolve
     * @return CompletableFuture that will complete with the resolution result
     */
    public CompletableFuture<ResolutionResult> resolveConflictAsync(ConflictContext conflictContext) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return resolveConflict(conflictContext);
            } catch (Exception e) {
                throw new RuntimeException("Asynchronous resolution failed", e);
            }
        }, executorServiceRef.get());
    }

    /**
     * Get resolution metrics for monitoring.
     */
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        // Basic counts
        metrics.put("totalResolvers", resolvers.size());
        metrics.put("totalResolutions", resolutionHistory.size());

        // Status breakdown
        Map<ResolutionStatus, Long> statusCounts = resolutionHistory.values().stream()
            .collect(Collectors.groupingBy(
                ResolutionResult::getStatus,
                Collectors.counting()
            ));
        metrics.put("statusBreakdown", statusCounts);

        // Average resolution time
        OptionalDouble avgDuration = resolutionHistory.values().stream()
            .mapToLong(ResolutionResult::getDurationMs)
            .average();
        metrics.put("averageResolutionTimeMs", avgDuration.orElse(0.0));

        // Resolver usage
        Map<String, Long> resolverUsage = resolutionHistory.values().stream()
            .filter(r -> r.getResolver() != null)
            .collect(Collectors.groupingBy(
                r -> r.getResolver().getStrategy().toString(),
                Collectors.counting()
            ));
        metrics.put("resolverUsage", resolverUsage);

        // Pending resolutions (for async)
        long pendingCount = resolutionHistory.values().stream()
            .filter(r -> r.getStatus() == ResolutionStatus.IN_PROGRESS)
            .count();
        metrics.put("pendingResolutions", pendingCount);

        return metrics;
    }

    /**
     * Get recent resolution history.
     *
     * @param limit Maximum number of results to return
     * @return List of recent resolution results
     */
    public List<ResolutionResult> getRecentHistory(int limit) {
        return resolutionHistory.values().stream()
            .sorted((r1, r2) -> Long.compare(r2.getEndTime(), r1.getEndTime()))
            .limit(limit)
            .collect(Collectors.toList());
    }

    /**
     * Update global configuration.
     *
     * @param configuration New configuration parameters
     * @throws IllegalArgumentException if configuration is invalid
     */
    public void updateConfiguration(Map<String, Object> configuration) {
        if (configuration == null) {
            throw new IllegalArgumentException("Configuration cannot be null");
        }

        globalConfiguration.putAll(configuration);

        // Update executor service if thread count changed
        if (configuration.containsKey("executorThreads")) {
            int newThreads = ((Number) configuration.get("executorThreads")).intValue();
            if (newThreads > 0) {
                ExecutorService oldExecutor = executorServiceRef.get();
                executorServiceRef.set(Executors.newFixedThreadPool(newThreads));
                oldExecutor.shutdown();
            }
        }
    }

    /**
     * Check if the service is healthy.
     *
     * @return true if service is healthy
     */
    public boolean isHealthy() {
        ExecutorService executor = executorServiceRef.get();
        return !resolvers.isEmpty() && executor != null &&
               !executor.isShutdown() &&
               resolvers.values().stream().allMatch(ConflictResolver::isHealthy);
    }

    /**
     * Shutdown the service and clean up resources.
     */
    public void shutdown() {
        ExecutorService executor = executorServiceRef.get();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Clean up history
        int maxHistory = ((Number) globalConfiguration.get("maxHistorySize")).intValue();
        if (resolutionHistory.size() > maxHistory) {
            resolutionHistory.entrySet().removeIf(entry -> {
                long age = System.currentTimeMillis() - entry.getValue().getEndTime();
                return age > TimeUnit.DAYS.toMillis(
                    ((Number) globalConfiguration.get("auditRetentionDays")).intValue()
                );
            });
        }
    }

    /**
     * Select the appropriate resolver for a conflict.
     */
    private ConflictResolver selectResolver(ConflictContext conflictContext) {
        return selectionStrategy.selectResolver(conflictContext, resolvers);
    }

    /**
     * Get the name of a resolver from the registry.
     */
    private String getResolverName(ConflictResolver resolver) {
        return resolvers.entrySet().stream()
            .filter(entry -> entry.getValue() == resolver)
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse("unknown");
    }

    /**
     * Add a resolution result to the history.
     */
    private void addToHistory(ResolutionResult result) {
        int maxHistory = ((Number) globalConfiguration.get("maxHistorySize")).intValue();
        resolutionHistory.put(result.getResolutionId(), result);

        // Maintain history size
        if (resolutionHistory.size() > maxHistory) {
            resolutionHistory.entrySet().removeIf(entry -> {
                // Remove oldest completed entries
                return entry.getValue().getStatus() == ResolutionStatus.COMPLETED &&
                       resolutionHistory.size() > maxHistory;
            });
        }
    }

    // Getters
    public Map<String, ConflictResolver> getResolvers() {
        return new HashMap<>(resolvers);
    }

    public Map<String, ResolutionResult> getResolutionHistory() {
        return new HashMap<>(resolutionHistory);
    }

    public ConflictSelectionStrategy getSelectionStrategy() {
        return selectionStrategy;
    }

    public ResolutionObserver getObserver() {
        return observer;
    }

    public Map<String, Object> getGlobalConfiguration() {
        return new HashMap<>(globalConfiguration);
    }
}