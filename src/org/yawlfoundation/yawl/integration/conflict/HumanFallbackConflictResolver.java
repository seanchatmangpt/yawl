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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Human fallback conflict resolver implementation.
 *
 * Always escalates conflicts to human oversight for review and decision.
 * Provides comprehensive context about the conflict, agent decisions, and
 * recommendations to support human decision-making.
 *
 * <p>Strategy characteristics:
 * - Human-in-the-loop for all conflicts
 * - Comprehensive context presentation
 * - Decision justification and documentation
 * - Configurable review workflows</p>
 *
 * @author YAWL Foundation
 * @version 5.2
 * @see ConflictResolver
 */
public class HumanFallbackConflictResolver implements ConflictResolver {

    private static final String STRATEGY_NAME = "HUMAN_FALLBACK";
    private final Map<String, Object> configuration;
    private final HumanReviewService reviewService;
    private final Map<String, ReviewRecord> pendingReviews = new ConcurrentHashMap<>();

    /**
     * Default configuration with reasonable defaults.
     */
    private static final Map<String, Object> DEFAULT_CONFIGURATION = new HashMap<>();
    static {
        DEFAULT_CONFIGURATION.put("reviewTimeoutMs", 600000); // 10 minutes
        DEFAULT_CONFIGURATION.put("maxPendingReviews", 100);
        DEFAULT_CONFIGURATION.put("provideRecommendations", true);
        DEFAULT_CONFIGURATION.put("recordRationale", true);
        DEFAULT_CONFIGURATION.put("notifyReviewers", true);
        DEFAULT_CONFIGURATION.put("allowDelegation", true);
        DEFAULT_CONFIGURATION.put("auditTrailEnabled", true);
    }

    /**
     * Service interface for human review process.
     */
    public interface HumanReviewService {
        /**
         * Submit conflict for human review.
         *
         * @param reviewRequest The review request containing conflict details
         * @return CompletableFuture that completes with the human decision
         */
        CompletableFuture<Decision> submitForReview(ReviewRequest reviewRequest);

        /**
         * Get current review queue status.
         *
         * @return review queue statistics
         */
        ReviewQueueStatus getReviewQueueStatus();

        /**
         * Cancel pending review if possible.
         *
         * @param reviewId The review ID to cancel
         * @return true if cancellation was successful
         */
        boolean cancelReview(String reviewId);
    }

    /**
     * Review request containing all conflict details for human review.
     */
    public static class ReviewRequest {
        private final String reviewId;
        private final String conflictId;
        private final String workflowId;
        private final String taskId;
        private final ConflictResolver.Severity severity;
        private final List<AgentDecision> conflictingDecisions;
        private final Map<String, Object> contextData;
        private final String justification;
        private final Map<String, Object> metadata;

        public ReviewRequest(String conflictId, String workflowId, String taskId,
                           ConflictResolver.Severity severity, List<AgentDecision> conflictingDecisions,
                           Map<String, Object> contextData, String justification) {
            this.reviewId = UUID.randomUUID().toString();
            this.conflictId = conflictId;
            this.workflowId = workflowId;
            this.taskId = taskId;
            this.severity = severity;
            this.conflictingDecisions = conflictingDecisions;
            this.contextData = contextData;
            this.justification = justification;
            this.metadata = new HashMap<>();
        }

        // Getters
        public String getReviewId() { return reviewId; }
        public String getConflictId() { return conflictId; }
        public String getWorkflowId() { return workflowId; }
        public String getTaskId() { return taskId; }
        public ConflictResolver.Severity getSeverity() { return severity; }
        public List<AgentDecision> getConflictingDecisions() { return conflictingDecisions; }
        public Map<String, Object> getContextData() { return contextData; }
        public String getJustification() { return justification; }
        public Map<String, Object> getMetadata() { return metadata; }
        public void addMetadata(String key, Object value) { metadata.put(key, value); }
    }

    /**
     * Review queue status information.
     */
    public static class ReviewQueueStatus {
        private final int pendingReviews;
        private final int averageReviewTimeMs;
        private final int maxQueueSize;
        private final boolean overloaded;

        public ReviewQueueStatus(int pendingReviews, int averageReviewTimeMs,
                               int maxQueueSize, boolean overloaded) {
            this.pendingReviews = pendingReviews;
            this.averageReviewTimeMs = averageReviewTimeMs;
            this.maxQueueSize = maxQueueSize;
            this.overloaded = overloaded;
        }

        // Getters
        public int getPendingReviews() { return pendingReviews; }
        public int getAverageReviewTimeMs() { return averageReviewTimeMs; }
        public int getMaxQueueSize() { return maxQueueSize; }
        public boolean isOverloaded() { return overloaded; }
    }

    /**
     * Default implementation of HumanReviewService.
     */
    public static class DefaultHumanReviewService implements HumanReviewService {

        @Override
        public CompletableFuture<Decision> submitForReview(ReviewRequest reviewRequest) {
            // In a real implementation, this would:
            // 1. Store the review request in a database
            // 2. Notify human reviewers
            // 3. Wait for human decision
            // 4. Update with decision
            // 5. Complete the future

            // For this implementation, simulate human review with a delay
            return CompletableFuture.supplyAsync(() -> {
                try {
                    // Simulate review time
                    Thread.sleep(1000);

                    // Create a decision (in real implementation, this comes from human input)
                    List<String> agentIds = reviewRequest.getConflictingDecisions().stream()
                        .map(AgentDecision::getAgentId)
                        .collect(Collectors.toList());

                    // Default to first decision if no better logic
                    String resolvedValue = reviewRequest.getConflictingDecisions().get(0).getDecision();

                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("reviewId", reviewRequest.getReviewId());
                    metadata.put("reviewMethod", "simulated_human_review");
                    metadata.put("reviewDurationMs", 1000L);

                    return new Decision(
                        resolvedValue,
                        reviewRequest.getSeverity(),
                        agentIds,
                        "HUMAN_REVIEW_" + STRATEGY_NAME,
                        metadata
                    );

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Human review interrupted", e);
                }
            });
        }

        @Override
        public ReviewQueueStatus getReviewQueueStatus() {
            // In a real implementation, query actual queue status
            return new ReviewQueueStatus(0, 0, 100, false);
        }

        @Override
        public boolean cancelReview(String reviewId) {
            // In a real implementation, mark review as cancelled
            return true;
        }
    }

    /**
     * Record tracking human review progress.
     */
    private static class ReviewRecord {
        private final String reviewId;
        private final String conflictId;
        private final long submissionTime;
        private ReviewStatus status;
        private CompletableFuture<Decision> reviewFuture;
        private long completionTime;

        public ReviewRecord(String reviewId, String conflictId) {
            this.reviewId = reviewId;
            this.conflictId = conflictId;
            this.submissionTime = System.currentTimeMillis();
            this.status = ReviewStatus.PENDING;
            this.reviewFuture = null;
            this.completionTime = 0;
        }

        // Getters and setters
        public String getReviewId() { return reviewId; }
        public String getConflictId() { return conflictId; }
        public long getSubmissionTime() { return submissionTime; }
        public ReviewStatus getStatus() { return status; }
        public void setStatus(ReviewStatus status) { this.status = status; }
        public CompletableFuture<Decision> getReviewFuture() { return reviewFuture; }
        public void setReviewFuture(CompletableFuture<Decision> reviewFuture) { this.reviewFuture = reviewFuture; }
        public long getCompletionTime() { return completionTime; }
        public void setCompletionTime(long completionTime) { this.completionTime = completionTime; }
    }

    private enum ReviewStatus {
        PENDING, IN_PROGRESS, COMPLETED, CANCELLED, FAILED
    }

    /**
     * Creates a new human fallback resolver with default review service.
     */
    public HumanFallbackConflictResolver() {
        this.configuration = new HashMap<>(DEFAULT_CONFIGURATION);
        this.reviewService = new DefaultHumanReviewService();
    }

    /**
     * Creates a new human fallback resolver with custom review service.
     *
     * @param reviewService The review service to use
     * @throws IllegalArgumentException if reviewService is null
     */
    public HumanFallbackConflictResolver(HumanReviewService reviewService) {
        if (reviewService == null) {
            throw new IllegalArgumentException("Review service cannot be null");
        }
        this.configuration = new HashMap<>(DEFAULT_CONFIGURATION);
        this.reviewService = reviewService;
    }

    /**
     * Creates a new human fallback resolver with custom configuration and review service.
     *
     * @param configuration Configuration parameters
     * @param reviewService The review service to use
     * @throws IllegalArgumentException if arguments are invalid
     */
    public HumanFallbackConflictResolver(Map<String, Object> configuration, HumanReviewService reviewService) {
        if (reviewService == null) {
            throw new IllegalArgumentException("Review service cannot be null");
        }
        this.configuration = new HashMap<>(DEFAULT_CONFIGURATION);
        this.reviewService = reviewService;
        updateConfiguration(configuration);
    }

    @Override
    public Decision resolveConflict(ConflictContext conflictContext) throws ConflictResolutionException {
        if (!canResolve(conflictContext)) {
            throw new ConflictResolutionException(
                String.format("Cannot resolve conflict %s with human fallback strategy",
                            conflictContext.getConflictId()),
                ConflictResolver.Strategy.HUMAN_FALLBACK,
                conflictContext.getConflictId()
            );
        }

        // Check if review queue is overloaded
        ReviewQueueStatus queueStatus = reviewService.getReviewQueueStatus();
        int maxPendingReviews = ((Number) configuration.get("maxPendingReviews")).intValue();
        if (queueStatus.getPendingReviews() >= maxPendingReviews) {
            throw new ConflictResolutionException(
                String.format("Review queue is overloaded (%d pending reviews, max: %d)",
                            queueStatus.getPendingReviews(), maxPendingReviews),
                ConflictResolver.Strategy.HUMAN_FALLBACK,
                conflictContext.getConflictId()
            );
        }

        // Create review request
        String justification = generateJustification(conflictContext);
        ReviewRequest reviewRequest = new ReviewRequest(
            conflictContext.getConflictId(),
            conflictContext.getWorkflowId(),
            conflictContext.getTaskId(),
            conflictContext.getSeverity(),
            conflictContext.getConflictingDecisions(),
            conflictContext.getContextData(),
            justification
        );

        // Add recommendations if configured
        if ((boolean) configuration.getOrDefault("provideRecommendations", true)) {
            reviewRequest.addMetadata("recommendations", generateRecommendations(conflictContext));
        }

        // Create review record
        ReviewRecord record = new ReviewRecord(reviewRequest.getReviewId(), conflictContext.getConflictId());
        pendingReviews.put(reviewRequest.getReviewId(), record);

        // Submit for review
        CompletableFuture<Decision> reviewFuture = reviewService.submitForReview(reviewRequest);
        record.setReviewFuture(reviewFuture);
        record.setStatus(ReviewStatus.IN_PROGRESS);

        // Handle review timeout
        long timeout = ((Number) configuration.get("reviewTimeoutMs")).longValue();
        CompletableFuture<Decision> result = reviewFuture.orTimeout(timeout, TimeUnit.MILLISECONDS)
            .exceptionally(ex -> {
                record.setStatus(ReviewStatus.FAILED);
                throw new ConflictResolutionException(
                    String.format("Human review timed out after %dms: %s", timeout, ex.getMessage()),
                    getStrategy(),
                    conflictContext.getConflictId()
                ).initCause(ex);
            });

        try {
            return result.get();
        } catch (Exception e) {
            throw new ConflictResolutionException(
                "Human review failed: " + e.getMessage(),
                getStrategy(),
                conflictContext.getConflictId()
            ).initCause(e);
        } finally {
            // Update record status
            if (!reviewFuture.isCompletedExceptionally()) {
                record.setStatus(ReviewStatus.COMPLETED);
                record.setCompletionTime(System.currentTimeMillis());
            }
        }
    }

    @Override
    public boolean canResolve(ConflictContext conflictContext) {
        if (conflictContext == null || conflictContext.getConflictingDecisions() == null) {
            return false;
        }

        // Check if review service is available
        return reviewService != null;
    }

    @Override
    public ConflictResolver.Strategy getStrategy() {
        return ConflictResolver.Strategy.HUMAN_FALLBACK;
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
        if (configuration.containsKey("reviewTimeoutMs")) {
            long timeout = ((Number) configuration.get("reviewTimeoutMs")).longValue();
            if (timeout < 1000) {
                throw new IllegalArgumentException("reviewTimeoutMs must be at least 1000ms");
            }
        }

        if (configuration.containsKey("maxPendingReviews")) {
            int maxReviews = ((Number) configuration.get("maxPendingReviews")).intValue();
            if (maxReviews < 1) {
                throw new IllegalArgumentException("maxPendingReviews must be at least 1");
            }
        }

        this.configuration.putAll(configuration);
    }

    @Override
    public boolean isHealthy() {
        return reviewService != null;
    }

    /**
     * Generate justification for human review.
     */
    private String generateJustification(ConflictContext conflictContext) {
        StringBuilder justification = new StringBuilder();
        justification.append("Conflict detected in workflow '").append(conflictContext.getWorkflowId())
                     .append("' for task '").append(conflictContext.getTaskId()).append("'.\n\n");

        justification.append("Severity: ").append(conflictContext.getSeverity()).append("\n");
        justification.append("Participating agents: ").append(conflictContext.getConflictingDecisions().size()).append("\n\n");

        justification.append("Conflicting decisions:\n");
        for (AgentDecision decision : conflictContext.getConflictingDecisions()) {
            justification.append("- Agent ").append(decision.getAgentId())
                         .append(": ").append(decision.getDecision())
                         .append(" (confidence: ").append(String.format("%.2f", decision.getConfidence()))
                         .append(")\n");
            if (decision.getRationale() != null && !decision.getRationale().isEmpty()) {
                justification.append("  Rationale: ").append(decision.getRationale()).append("\n");
            }
        }

        return justification.toString();
    }

    /**
     * Generate recommendations for human reviewer.
     */
    private Map<String, Object> generateRecommendations(ConflictContext conflictContext) {
        Map<String, Object> recommendations = new HashMap<>();

        // Analyze confidence scores
        double avgConfidence = conflictContext.getConflictingDecisions().stream()
            .mapToDouble(AgentDecision::getConfidence)
            .average()
            .orElse(0.0);

        recommendations.put("averageConfidence", avgConfidence);
        recommendations.put("confidenceSpread", calculateConfidenceSpread(conflictContext.getConflictingDecisions()));

        // Check for consensus
        boolean hasConsensus = checkForConsensus(conflictContext.getConflictingDecisions());
        recommendations.put("hasConsensus", hasConsensus);

        // Generate suggestions based on conflict characteristics
        List<String> suggestions = new ArrayList<>();
        if (!hasConsensus) {
            suggestions.add("Consider additional factors or consult domain experts");
        }
        if (avgConfidence < 0.5) {
            suggestions.add("Low confidence across all agents - review carefully");
        }
        if (conflictContext.getSeverity() == ConflictResolver.Severity.CRITICAL) {
            suggestions.add("Critical decision - require multiple approvers");
        }

        recommendations.put("suggestions", suggestions);
        return recommendations;
    }

    /**
     * Calculate spread of confidence scores.
     */
    private double calculateConfidenceSpread(List<AgentDecision> decisions) {
        if (decisions.size() < 2) return 0.0;

        double min = decisions.stream().mapToDouble(AgentDecision::getConfidence).min().orElse(0.0);
        double max = decisions.stream().mapToDouble(AgentDecision::getConfidence).max().orElse(1.0);
        return max - min;
    }

    /**
     * Check if all agents agree on the decision.
     */
    private boolean checkForConsensus(List<AgentDecision> decisions) {
        if (decisions.isEmpty()) return false;

        String firstDecision = decisions.get(0).getDecision();
        return decisions.stream().allMatch(d -> d.getDecision().equals(firstDecision));
    }

    /**
     * Get pending reviews.
     */
    public Map<String, ReviewRecord> getPendingReviews() {
        return new HashMap<>(pendingReviews);
    }

    /**
     * Cancel a pending review.
     */
    public boolean cancelReview(String reviewId) {
        ReviewRecord record = pendingReviews.get(reviewId);
        if (record != null && record.getStatus() == ReviewStatus.PENDING) {
            boolean cancelled = reviewService.cancelReview(reviewId);
            if (cancelled) {
                record.setStatus(ReviewStatus.CANCELLED);
            }
            return cancelled;
        }
        return false;
    }

    /**
     * Clean up completed reviews older than specified time.
     */
    public void cleanupCompletedReviews(long olderThanMs) {
        long cutoffTime = System.currentTimeMillis() - olderThanMs;
        pendingReviews.entrySet().removeIf(entry -> {
            ReviewRecord record = entry.getValue();
            return record.getStatus() == ReviewStatus.COMPLETED &&
                   record.getCompletionTime() > 0 &&
                   record.getCompletionTime() < cutoffTime;
        });
    }

    @Override
    public String toString() {
        return String.format("HumanFallbackConflictResolver{strategy=%s, pendingReviews=%d}",
                           getStrategy(), pendingReviews.size());
    }
}