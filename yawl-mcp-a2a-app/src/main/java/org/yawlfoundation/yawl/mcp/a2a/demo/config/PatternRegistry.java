/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.demo.config;

import org.yawlfoundation.yawl.mcp.a2a.demo.report.PatternResult.Difficulty;
import org.yawlfoundation.yawl.mcp.a2a.demo.report.PatternResult.ResultPatternCategory;
import org.yawlfoundation.yawl.mcp.a2a.demo.report.PatternResult.PatternInfo;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Registry of all available YAWL workflow patterns.
 *
 * <p>This registry provides access to pattern metadata, including IDs, names,
 * descriptions, difficulty levels, and resource paths. It supports pattern
 * discovery by ID, category, and similarity matching.</p>
 *
 * <p>The registry includes patterns from the Workflow Patterns initiative
 * (WCP 1-43) plus extended patterns for AI/ML, agent orchestration, and
 * enterprise workflows.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class PatternRegistry {

    private final Map<String, PatternInfo> patternsById;
    private final Map<PatternCategory, List<PatternInfo>> patternsByCategory;

    /**
     * Create a new pattern registry and initialize with all known patterns.
     */
    public PatternRegistry() {
        this.patternsById = new HashMap<>();
        this.patternsByCategory = new EnumMap<>(PatternCategory.class);
        initializePatterns();
    }

    /**
     * Initialize all known patterns.
     */
    private void initializePatterns() {
        // Basic Control Flow Patterns (WCP 1-5)
        registerPattern("WCP-1", "Sequence", "Tasks executed in sequential order",
            Difficulty.BASIC, PatternCategory.BASIC, "controlflow/wcp-1-sequence.yaml");
        registerPattern("WCP-2", "Parallel Split", "Split execution into parallel branches",
            Difficulty.BASIC, PatternCategory.BASIC, "controlflow/wcp-2-parallel-split.yaml");
        registerPattern("WCP-3", "Synchronization", "Synchronize parallel branches",
            Difficulty.BASIC, PatternCategory.BASIC, "controlflow/wcp-3-synchronization.yaml");
        registerPattern("WCP-4", "Exclusive Choice", "Choose one branch based on condition",
            Difficulty.BASIC, PatternCategory.BASIC, "controlflow/wcp-4-exclusive-choice.yaml");
        registerPattern("WCP-5", "Simple Merge", "Merge two alternative branches without sync",
            Difficulty.BASIC, PatternCategory.BASIC, "controlflow/wcp-5-simple-merge.yaml");

        // Advanced Branching Patterns (WCP 6-11)
        registerPattern("WCP-6", "Multi-Choice", "Choose multiple branches based on conditions",
            Difficulty.INTERMEDIATE, PatternCategory.ADVANCED_BRANCHING, "branching/wcp-6-multi-choice.yaml");
        registerPattern("WCP-7", "Structured Synchronizing Merge", "Synchronize multiple activated branches",
            Difficulty.INTERMEDIATE, PatternCategory.ADVANCED_BRANCHING, "branching/wcp-7-sync-merge.yaml");
        registerPattern("WCP-8", "Multi-Merge", "Merge without synchronization",
            Difficulty.INTERMEDIATE, PatternCategory.ADVANCED_BRANCHING, "branching/wcp-8-multi-merge.yaml");
        registerPattern("WCP-9", "Structured Discriminator", "Discard all but first completion",
            Difficulty.INTERMEDIATE, PatternCategory.ADVANCED_BRANCHING, "branching/wcp-9-discriminator.yaml");
        registerPattern("WCP-10", "Structured Loop", "Loop with explicit structure",
            Difficulty.BASIC, PatternCategory.ITERATION, "branching/wcp-10-structured-loop.yaml");
        registerPattern("WCP-11", "Implicit Termination", "Case terminates when no active work",
            Difficulty.BASIC, PatternCategory.STRUCTURAL, "branching/wcp-11-implicit-termination.yaml");

        // Multi-Instance Patterns (WCP 12-17)
        registerPattern("WCP-12", "MI Without Synchronization", "Multiple instances without sync",
            Difficulty.INTERMEDIATE, PatternCategory.MULTIINSTANCE, "multiinstance/wcp-12-mi-no-sync.yaml");
        registerPattern("WCP-13", "MI With A Priori Design Time Knowledge", "Fixed number of instances",
            Difficulty.INTERMEDIATE, PatternCategory.MULTIINSTANCE, "multiinstance/wcp-13-mi-static.yaml");
        registerPattern("WCP-14", "MI With A Priori Runtime Knowledge", "Dynamic instance count at runtime",
            Difficulty.INTERMEDIATE, PatternCategory.MULTIINSTANCE, "multiinstance/wcp-14-mi-dynamic.yaml");
        registerPattern("WCP-15", "MI Without A Priori Runtime Knowledge", "Instance count determined during execution",
            Difficulty.ADVANCED, PatternCategory.MULTIINSTANCE, "multiinstance/wcp-15-mi-runtime.yaml");
        registerPattern("WCP-16", "MI Without A Priori Knowledge", "Instance count unknown at design and runtime",
            Difficulty.ADVANCED, PatternCategory.MULTIINSTANCE, "multiinstance/wcp-16-mi-without-runtime.yaml");
        registerPattern("WCP-17", "Interleaved Parallel Routing", "Parallel tasks executed one at a time",
            Difficulty.INTERMEDIATE, PatternCategory.STATE_BASED, "multiinstance/wcp-17-interleaved-routing.yaml");

        // State-Based Patterns (WCP 18-21)
        registerPattern("WCP-18", "Deferred Choice", "Choice deferred until one branch executes",
            Difficulty.ADVANCED, PatternCategory.STATE_BASED, "statebased/wcp-18-deferred-choice.yaml");
        registerPattern("WCP-19", "Milestone", "Task enabled only when milestone reached",
            Difficulty.BASIC, PatternCategory.STATE_BASED, "statebased/wcp-19-milestone.yaml");
        registerPattern("WCP-20", "Cancel Activity", "Cancel a specific activity",
            Difficulty.BASIC, PatternCategory.CANCELLATION, "statebased/wcp-20-cancel-activity.yaml");
        registerPattern("WCP-21", "Cancel Case", "Cancel entire case",
            Difficulty.BASIC, PatternCategory.CANCELLATION, "statebased/wcp-21-cancel-case.yaml");

        // Extended Patterns (WCP 44)
        registerPattern("WCP-44", "Saga Pattern", "Long-running transaction with compensation",
            Difficulty.ADVANCED, PatternCategory.EXTENDED, "extended/wcp-44-saga.yaml");

        // Distributed Patterns (WCP 45-50)
        registerPattern("WCP-45", "Saga Choreography", "Saga pattern with choreography",
            Difficulty.ADVANCED, PatternCategory.DISTRIBUTED, "distributed/wcp-45-saga-choreography.yaml");
        registerPattern("WCP-46", "Two-Phase Commit", "Distributed transaction with prepare/commit",
            Difficulty.ADVANCED, PatternCategory.DISTRIBUTED, "distributed/wcp-46-two-phase-commit.yaml");
        registerPattern("WCP-47", "Circuit Breaker", "Protect against cascading failures",
            Difficulty.INTERMEDIATE, PatternCategory.DISTRIBUTED, "distributed/wcp-47-circuit-breaker.yaml");
        registerPattern("WCP-48", "Retry", "Retry failed operations",
            Difficulty.INTERMEDIATE, PatternCategory.DISTRIBUTED, "distributed/wcp-48-retry.yaml");
        registerPattern("WCP-49", "Bulkhead", "Isolate failures to prevent cascade",
            Difficulty.INTERMEDIATE, PatternCategory.DISTRIBUTED, "distributed/wcp-49-bulkhead.yaml");
        registerPattern("WCP-50", "Timeout", "Timeout pattern for long operations",
            Difficulty.BASIC, PatternCategory.DISTRIBUTED, "distributed/wcp-50-timeout.yaml");

        // Event-Driven Patterns (WCP 51-59)
        registerPattern("WCP-51", "Event Gateway", "Event-based workflow gateway",
            Difficulty.INTERMEDIATE, PatternCategory.EVENT_DRIVEN, "eventdriven/wcp-51-event-gateway.yaml");
        registerPattern("WCP-52", "Outbox", "Reliable event publishing with outbox pattern",
            Difficulty.ADVANCED, PatternCategory.EVENT_DRIVEN, "eventdriven/wcp-52-outbox.yaml");
        registerPattern("WCP-53", "Scatter-Gather", "Distribute work and collect results",
            Difficulty.ADVANCED, PatternCategory.EVENT_DRIVEN, "eventdriven/wcp-53-scatter-gather.yaml");
        registerPattern("WCP-54", "Event Router", "Route events to appropriate handlers",
            Difficulty.INTERMEDIATE, PatternCategory.EVENT_DRIVEN, "eventdriven/wcp-54-event-router.yaml");
        registerPattern("WCP-55", "Event Stream", "Process events from event stream",
            Difficulty.ADVANCED, PatternCategory.EVENT_DRIVEN, "eventdriven/wcp-55-event-stream.yaml");
        registerPattern("WCP-56", "CQRS", "Command Query Responsibility Segregation",
            Difficulty.ADVANCED, PatternCategory.EVENT_DRIVEN, "eventdriven/wcp-56-cqrs.yaml");
        registerPattern("WCP-57", "Event Sourcing", "Store state as sequence of events",
            Difficulty.EXPERT, PatternCategory.EVENT_DRIVEN, "eventdriven/wcp-57-event-sourcing.yaml");
        registerPattern("WCP-58", "Compensating Transaction", "Undo operations on failure",
            Difficulty.ADVANCED, PatternCategory.EVENT_DRIVEN, "eventdriven/wcp-58-compensating-transaction.yaml");
        registerPattern("WCP-59", "Side-by-Side", "Run new version alongside old for validation",
            Difficulty.ADVANCED, PatternCategory.EVENT_DRIVEN, "eventdriven/wcp-59-side-by-side.yaml");

        // AI/ML Patterns (WCP 60-68)
        registerPattern("WCP-60", "Rules Engine", "Rule-based decision engine",
            Difficulty.INTERMEDIATE, PatternCategory.AI_ML, "aiml/wcp-60-rules-engine.yaml");
        registerPattern("WCP-61", "ML Model", "Machine learning model inference",
            Difficulty.INTERMEDIATE, PatternCategory.AI_ML, "aiml/wcp-61-ml-model.yaml");
        registerPattern("WCP-62", "Human-AI Handoff", "Seamless transition between AI and human",
            Difficulty.ADVANCED, PatternCategory.AI_ML, "aiml/wcp-62-human-ai-handoff.yaml");
        registerPattern("WCP-63", "Model Fallback", "Fallback to alternative model on failure",
            Difficulty.ADVANCED, PatternCategory.AI_ML, "aiml/wcp-63-model-fallback.yaml");
        registerPattern("WCP-64", "Confidence Threshold", "Route based on model confidence",
            Difficulty.INTERMEDIATE, PatternCategory.AI_ML, "aiml/wcp-64-confidence-threshold.yaml");
        registerPattern("WCP-65", "Feature Store", "Centralized feature management",
            Difficulty.ADVANCED, PatternCategory.AI_ML, "aiml/wcp-65-feature-store.yaml");
        registerPattern("WCP-66", "Pipeline", "ML pipeline orchestration",
            Difficulty.ADVANCED, PatternCategory.AI_ML, "aiml/wcp-66-pipeline.yaml");
        registerPattern("WCP-67", "Drift Detection", "Detect model drift over time",
            Difficulty.ADVANCED, PatternCategory.AI_ML, "aiml/wcp-67-drift-detection.yaml");
        registerPattern("WCP-68", "Auto-Retrain", "Automatic model retraining trigger",
            Difficulty.EXPERT, PatternCategory.AI_ML, "aiml/wcp-68-auto-retrain.yaml");

        // Enterprise Patterns (ENT-1-8)
        registerPattern("ENT-1", "Sequential Approval", "Multi-level sequential approval workflow",
            Difficulty.INTERMEDIATE, PatternCategory.ENTERPRISE, "enterprise/ent-1-sequential-approval.yaml");
        registerPattern("ENT-2", "Parallel Approval", "Multi-level parallel approval workflow",
            Difficulty.INTERMEDIATE, PatternCategory.ENTERPRISE, "enterprise/ent-2-parallel-approval.yaml");
        registerPattern("ENT-3", "Escalation", "Escalation on timeout",
            Difficulty.INTERMEDIATE, PatternCategory.ENTERPRISE, "enterprise/ent-3-escalation.yaml");
        registerPattern("ENT-4", "Compensation", "Compensation pattern for rollback",
            Difficulty.ADVANCED, PatternCategory.ENTERPRISE, "enterprise/ent-4-compensation.yaml");
        registerPattern("ENT-5", "SLA Monitoring", "Service level agreement monitoring",
            Difficulty.INTERMEDIATE, PatternCategory.ENTERPRISE, "enterprise/ent-5-sla-monitoring.yaml");
        registerPattern("ENT-6", "Delegation", "Task delegation pattern",
            Difficulty.INTERMEDIATE, PatternCategory.ENTERPRISE, "enterprise/ent-6-delegation.yaml");
        registerPattern("ENT-7", "Four Eyes", "Two-person approval requirement",
            Difficulty.INTERMEDIATE, PatternCategory.ENTERPRISE, "enterprise/ent-7-four-eyes.yaml");
        registerPattern("ENT-8", "Nomination", "Nomination-based task assignment",
            Difficulty.INTERMEDIATE, PatternCategory.ENTERPRISE, "enterprise/ent-8-nomination.yaml");

        // Agent Patterns (AGT-1-5)
        registerPattern("AGT-1", "Agent Assisted", "Human works with AI agent assistance",
            Difficulty.INTERMEDIATE, PatternCategory.AGENT, "agent/agt-1-agent-assisted.yaml");
        registerPattern("AGT-2", "LLM Decision", "Decision made by LLM-based agent",
            Difficulty.INTERMEDIATE, PatternCategory.AGENT, "agent/agt-2-llm-decision.yaml");
        registerPattern("AGT-3", "Human-Agent Handoff", "Handoff between human and agent",
            Difficulty.ADVANCED, PatternCategory.AGENT, "agent/agt-3-human-agent-handoff.yaml");
        registerPattern("AGT-4", "Agent-to-Agent Handoff", "Transfer work between specialized agents with context preservation",
            Difficulty.ADVANCED, PatternCategory.AGENT, "agent/agt-4-handoff.yaml");
        registerPattern("AGT-5", "Multi-Agent Orchestration", "Coordinate multiple agents working on complex tasks",
            Difficulty.EXPERT, PatternCategory.AGENT, "agent/agt-5-orchestration.yaml");

        // Cancellation Patterns - Control Flow variants (WCP-19-CF, WCP-20-CF, WCP-22, WCP-23, WCP-25)
        registerPattern("WCP-19-CF", "Cancel Task (Control Flow)", "Cancel a specific task via control flow cancellation region",
            Difficulty.INTERMEDIATE, PatternCategory.CANCELLATION, "controlflow/wcp-19-cancel-task.yaml");
        registerPattern("WCP-20-CF", "Cancel Case (Control Flow)", "Cancel entire workflow case via control flow trigger",
            Difficulty.INTERMEDIATE, PatternCategory.CANCELLATION, "controlflow/wcp-20-cancel-case.yaml");
        registerPattern("WCP-22", "Cancel Region", "Cancel all tasks in a specific region of the workflow",
            Difficulty.INTERMEDIATE, PatternCategory.CANCELLATION, "controlflow/wcp-22-cancel-region.yaml");
        registerPattern("WCP-23", "Cancel Multiple Instances", "Cancel all instances of a multi-instance task",
            Difficulty.ADVANCED, PatternCategory.CANCELLATION, "controlflow/wcp-23-cancel-mi.yaml");
        registerPattern("WCP-25", "Cancel and Complete MI", "Cancel remaining instances and force-complete a multi-instance task",
            Difficulty.ADVANCED, PatternCategory.CANCELLATION, "controlflow/wcp-25-cancel-complete-mi.yaml");

        // Iteration Patterns - Control Flow variants (WCP-28, WCP-29, WCP-30, WCP-31)
        registerPattern("WCP-28", "Structured Loop (Control Flow)", "Loop with explicit structure and entry/exit conditions",
            Difficulty.INTERMEDIATE, PatternCategory.ITERATION, "controlflow/wcp-28-structured-loop.yaml");
        registerPattern("WCP-29", "Loop with Cancel Task", "Structured loop with ability to cancel a task within an iteration",
            Difficulty.ADVANCED, PatternCategory.ITERATION, "controlflow/wcp-29-loop-cancel.yaml");
        registerPattern("WCP-30", "Loop with Cancel Region", "Structured loop with ability to cancel a region of tasks",
            Difficulty.ADVANCED, PatternCategory.ITERATION, "controlflow/wcp-30-loop-cancel-region.yaml");
        registerPattern("WCP-31", "Loop with Complete MI", "Structured loop with ability to force-complete multi-instance tasks",
            Difficulty.ADVANCED, PatternCategory.ITERATION, "controlflow/wcp-31-loop-complete-mi.yaml");

        // Advanced Branching - Discriminator variant (WCP-36)
        registerPattern("WCP-36", "Discriminator with Complete MI", "Discriminator that force-completes remaining multi-instance tasks after first completion",
            Difficulty.ADVANCED, PatternCategory.ADVANCED_BRANCHING, "controlflow/wcp-36-discriminator-complete.yaml");

        // Event-Driven Trigger Patterns (WCP-37, WCP-38, WCP-39, WCP-40)
        registerPattern("WCP-37", "Local Trigger", "Trigger a task from an external event within the workflow scope",
            Difficulty.INTERMEDIATE, PatternCategory.EVENT_DRIVEN, "eventdriven/wcp-37-local-trigger.yaml");
        registerPattern("WCP-38", "Global Trigger", "Trigger from a global event broadcast across all workflow instances",
            Difficulty.INTERMEDIATE, PatternCategory.EVENT_DRIVEN, "eventdriven/wcp-38-global-trigger.yaml");
        registerPattern("WCP-39", "Reset Trigger", "Reset workflow state to a checkpoint on external trigger",
            Difficulty.ADVANCED, PatternCategory.EVENT_DRIVEN, "eventdriven/wcp-39-reset-trigger.yaml");
        registerPattern("WCP-40", "Reset Trigger with Cancel Region", "Reset workflow to checkpoint and cancel an active region",
            Difficulty.ADVANCED, PatternCategory.EVENT_DRIVEN, "eventdriven/wcp-40-reset-cancel.yaml");

        // Extended Structural Patterns (WCP-41, WCP-42, WCP-43)
        registerPattern("WCP-41", "Blocked And-Split", "And-split that blocks until all branches are ready to proceed simultaneously",
            Difficulty.ADVANCED, PatternCategory.EXTENDED, "extended/wcp-41-blocked-split.yaml");
        registerPattern("WCP-42", "Critical Section", "Protected region enforcing mutually exclusive concurrent access via semaphore",
            Difficulty.ADVANCED, PatternCategory.EXTENDED, "extended/wcp-42-critical-section.yaml");
        registerPattern("WCP-43", "Critical Section with Cancel", "Critical section with cancellation capability for the protected region",
            Difficulty.EXPERT, PatternCategory.EXTENDED, "extended/wcp-43-critical-cancel.yaml");

        // Multi-Instance Patterns (WCP-24, WCP-26, WCP-27)
        registerPattern("WCP-24", "Complete Multiple Instances", "Force-complete all active instances of a multi-instance task simultaneously",
            Difficulty.ADVANCED, PatternCategory.MULTIINSTANCE, "multiinstance/wcp-24-complete-mi.yaml");
        registerPattern("WCP-26", "Sequential MI Without A Priori Knowledge", "Sequential multi-instance where item count is determined dynamically during execution",
            Difficulty.ADVANCED, PatternCategory.MULTIINSTANCE, "multiinstance/wcp-26-sequential-mi.yaml");
        registerPattern("WCP-27", "Concurrent MI Without A Priori Knowledge", "Concurrent multi-instance where item count is discovered at runtime",
            Difficulty.ADVANCED, PatternCategory.MULTIINSTANCE, "multiinstance/wcp-27-concurrent-mi.yaml");

        // State-Based Patterns (WCP-32, WCP-33, WCP-34, WCP-35)
        registerPattern("WCP-32", "Synchronizing Merge with Cancel", "Synchronize multiple parallel branches with optional region cancellation",
            Difficulty.ADVANCED, PatternCategory.STATE_BASED, "statebased/wcp-32-sync-cancel.yaml");
        registerPattern("WCP-33", "Generalized And-Join", "And-join that handles dynamically created parallel branches",
            Difficulty.ADVANCED, PatternCategory.STATE_BASED, "statebased/wcp-33-generalized-join.yaml");
        registerPattern("WCP-34", "Static Partial Join", "Join that proceeds after a fixed threshold of branches complete, cancelling the rest",
            Difficulty.ADVANCED, PatternCategory.STATE_BASED, "statebased/wcp-34-static-partial-join.yaml");
        registerPattern("WCP-35", "Dynamic Partial Join", "Join that proceeds after a runtime-calculated percentage of branches complete",
            Difficulty.EXPERT, PatternCategory.STATE_BASED, "statebased/wcp-35-dynamic-partial-join.yaml");

        // AI/ML Supplementary Patterns (WCP-60-ML, WCP-62-PP, WCP-63-FE)
        registerPattern("WCP-60-ML", "ML Pipeline", "End-to-end machine learning training pipeline with data ingestion, training, evaluation, and registration",
            Difficulty.ADVANCED, PatternCategory.AI_ML, "aiml/wcp-60-ml-pipeline.yaml");
        registerPattern("WCP-62-PP", "Data Preprocessing", "Data preprocessing pipeline with schema validation, cleaning, normalization, and encoding",
            Difficulty.INTERMEDIATE, PatternCategory.AI_ML, "aiml/wcp-62-preprocessing.yaml");
        registerPattern("WCP-63-FE", "Feature Engineering", "Feature engineering pipeline generating polynomial, interaction, time, and text features",
            Difficulty.ADVANCED, PatternCategory.AI_ML, "aiml/wcp-63-features.yaml");

        // Enterprise Supplementary Patterns (ENT-9, ENT-10, ENT-11)
        registerPattern("ENT-9", "Approval Workflow", "Amount-threshold approval with manager and director escalation paths",
            Difficulty.BASIC, PatternCategory.ENTERPRISE, "enterprise/ent-approval.yaml");
        registerPattern("ENT-10", "Delegation Workflow", "Task delegation with validation, transfer, and dual-notification of original and delegate",
            Difficulty.INTERMEDIATE, PatternCategory.ENTERPRISE, "enterprise/ent-delegation.yaml");
        registerPattern("ENT-11", "Escalation Workflow", "Time-based escalation with multi-level reassignment up to emergency action",
            Difficulty.INTERMEDIATE, PatternCategory.ENTERPRISE, "enterprise/ent-escalation.yaml");
    }

    /**
     * Register a pattern in the registry.
     */
    private void registerPattern(String id, String name, String description,
                                 Difficulty difficulty, PatternCategory configCategory,
                                 String yamlPath) {
        // Convert config enum to PatternResult's nested record
        ResultPatternCategory resultCategory = new ResultPatternCategory(
            configCategory.getDisplayName(),
            configCategory.getColorCode()
        );

        PatternInfo info = new PatternInfo(id, name, description, difficulty, resultCategory, yamlPath);
        patternsById.put(id, info);
        patternsByCategory.computeIfAbsent(configCategory, k -> new ArrayList<>()).add(info);
    }

    /**
     * Get a pattern by its ID.
     *
     * @param id the pattern ID (e.g., "WCP-1")
     * @return Optional containing the pattern info, or empty if not found
     */
    public Optional<PatternInfo> getPattern(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(patternsById.get(id.toUpperCase()));
    }

    /**
     * Get all patterns in a category.
     *
     * @param category the pattern category
     * @return list of patterns in the category
     */
    public List<PatternInfo> getPatternsByCategory(PatternCategory category) {
        return patternsByCategory.getOrDefault(category, Collections.emptyList());
    }

    /**
     * Get all registered patterns.
     *
     * @return list of all patterns
     */
    public List<PatternInfo> getAllPatterns() {
        return new ArrayList<>(patternsById.values());
    }

    /**
     * Get all pattern IDs.
     *
     * @return set of all pattern IDs
     */
    public Set<String> getAllPatternIds() {
        return Collections.unmodifiableSet(patternsById.keySet());
    }

    /**
     * Find patterns similar to the given ID using Levenshtein distance.
     *
     * @param patternId the pattern ID to find similar patterns for
     * @return list of similar pattern IDs (up to 3)
     */
    public List<String> findSimilarPatterns(String patternId) {
        if (patternId == null || patternId.isEmpty()) {
            return Collections.emptyList();
        }

        String normalized = patternId.toUpperCase();
        return patternsById.keySet().stream()
            .sorted(Comparator.comparingInt(id -> levenshteinDistance(normalized, id.toUpperCase())))
            .limit(3)
            .collect(Collectors.toList());
    }

    /**
     * Calculate Levenshtein distance between two strings.
     */
    private int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];

        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[a.length()][b.length()];
    }

    /**
     * Get the total number of registered patterns.
     */
    public int getPatternCount() {
        return patternsById.size();
    }

    /**
     * Check if a pattern exists.
     *
     * @param id the pattern ID
     * @return true if the pattern exists
     */
    public boolean hasPattern(String id) {
        return patternsById.containsKey(id != null ? id.toUpperCase() : null);
    }
}
