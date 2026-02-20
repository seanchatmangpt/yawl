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

        // Advanced Branching Patterns (WCP 6-10)
        registerPattern("WCP-6", "Multi-Choice", "Choose multiple branches based on conditions",
            Difficulty.INTERMEDIATE, PatternCategory.ADVANCED_BRANCHING, "controlflow/wcp-6-multi-choice.yaml");
        registerPattern("WCP-7", "Structured Synchronizing Merge", "Synchronize multiple activated branches",
            Difficulty.INTERMEDIATE, PatternCategory.ADVANCED_BRANCHING, "controlflow/wcp-7-synchronizing-merge.yaml");
        registerPattern("WCP-8", "Multi-Merge", "Merge without synchronization",
            Difficulty.INTERMEDIATE, PatternCategory.ADVANCED_BRANCHING, "controlflow/wcp-8-multi-merge.yaml");
        registerPattern("WCP-9", "Structured Discriminator", "Discard all but first completion",
            Difficulty.INTERMEDIATE, PatternCategory.ADVANCED_BRANCHING, "controlflow/wcp-9-discriminator.yaml");
        registerPattern("WCP-10", "Parallel Split + Discriminator", "Combined parallel execution with discrimination",
            Difficulty.ADVANCED, PatternCategory.ADVANCED_BRANCHING, "controlflow/wcp-10-parallel-discriminator.yaml");

        // Structural Patterns (WCP 11-15)
        registerPattern("WCP-11", "Implicit Termination", "Case terminates when no active work",
            Difficulty.BASIC, PatternCategory.STRUCTURAL, "controlflow/wcp-11-implicit-termination.yaml");
        registerPattern("WCP-12", "MI Without Synchronization", "Multiple instances without sync",
            Difficulty.INTERMEDIATE, PatternCategory.MULTIINSTANCE, "multiinstance/wcp-12-mi-no-sync.yaml");
        registerPattern("WCP-13", "MI With A Priori Design Time Knowledge", "Fixed number of instances",
            Difficulty.INTERMEDIATE, PatternCategory.MULTIINSTANCE, "multiinstance/wcp-13-mi-design-time.yaml");
        registerPattern("WCP-14", "MI With A Priori Runtime Knowledge", "Dynamic instance count at runtime",
            Difficulty.INTERMEDIATE, PatternCategory.MULTIINSTANCE, "multiinstance/wcp-14-mi-runtime.yaml");
        registerPattern("WCP-15", "MI Without A Priori Knowledge", "Instance count determined during execution",
            Difficulty.ADVANCED, PatternCategory.MULTIINSTANCE, "multiinstance/wcp-15-mi-dynamic.yaml");

        // State-Based Patterns (WCP 16-20)
        registerPattern("WCP-16", "Deferred Choice", "Choice deferred until one branch executes",
            Difficulty.INTERMEDIATE, PatternCategory.STATE_BASED, "statebased/wcp-16-deferred-choice.yaml");
        registerPattern("WCP-17", "Interleaved Parallel Routing", "Parallel tasks executed one at a time",
            Difficulty.INTERMEDIATE, PatternCategory.STATE_BASED, "statebased/wcp-17-interleaved.yaml");
        registerPattern("WCP-18", "Milestone", "Task enabled only when milestone reached",
            Difficulty.ADVANCED, PatternCategory.STATE_BASED, "statebased/wcp-18-milestone.yaml");
        registerPattern("WCP-19", "Cancel Task", "Cancel a specific task",
            Difficulty.BASIC, PatternCategory.CANCELLATION, "controlflow/wcp-19-cancel-task.yaml");
        registerPattern("WCP-20", "Cancel Case", "Cancel entire case",
            Difficulty.BASIC, PatternCategory.CANCELLATION, "controlflow/wcp-20-cancel-case.yaml");

        // Cancellation Patterns (WCP 21-25)
        registerPattern("WCP-21", "Structured Discriminator + Cancel Region", "Cancel region after first completion",
            Difficulty.ADVANCED, PatternCategory.CANCELLATION, "controlflow/wcp-21-discriminator-cancel.yaml");
        registerPattern("WCP-22", "Cancel Region", "Cancel a region of the workflow",
            Difficulty.INTERMEDIATE, PatternCategory.CANCELLATION, "controlflow/wcp-22-cancel-region.yaml");
        registerPattern("WCP-23", "Cancel Multiple Instances", "Cancel all instances of a task",
            Difficulty.INTERMEDIATE, PatternCategory.CANCELLATION, "controlflow/wcp-23-cancel-mi.yaml");
        registerPattern("WCP-24", "Complete Multiple Instances", "Complete all instances simultaneously",
            Difficulty.INTERMEDIATE, PatternCategory.MULTIINSTANCE, "multiinstance/wcp-24-complete-mi.yaml");
        registerPattern("WCP-25", "Cancel Multiple Instances + Complete MI", "Combined cancel and complete",
            Difficulty.ADVANCED, PatternCategory.CANCELLATION, "controlflow/wcp-25-cancel-complete-mi.yaml");

        // Multi-Instance Patterns (WCP 26-35)
        registerPattern("WCP-26", "Sequential MI Without A Priori Knowledge", "Sequential dynamic instances",
            Difficulty.ADVANCED, PatternCategory.MULTIINSTANCE, "multiinstance/wcp-26-sequential-mi.yaml");
        registerPattern("WCP-27", "Concurrent MI Without A Priori Knowledge", "Concurrent dynamic instances",
            Difficulty.ADVANCED, PatternCategory.MULTIINSTANCE, "multiinstance/wcp-27-concurrent-mi.yaml");

        // Iteration Patterns (WCP 28-31)
        registerPattern("WCP-28", "Structured Loop", "Loop with explicit structure",
            Difficulty.BASIC, PatternCategory.ITERATION, "controlflow/wcp-28-structured-loop.yaml");
        registerPattern("WCP-29", "Structured Loop + Cancel Task", "Loop with cancellation",
            Difficulty.INTERMEDIATE, PatternCategory.ITERATION, "controlflow/wcp-29-loop-cancel.yaml");
        registerPattern("WCP-30", "Structured Loop + Cancel Region", "Loop with region cancellation",
            Difficulty.INTERMEDIATE, PatternCategory.ITERATION, "controlflow/wcp-30-loop-cancel-region.yaml");
        registerPattern("WCP-31", "Structured Loop + Complete MI", "Loop with instance completion",
            Difficulty.ADVANCED, PatternCategory.ITERATION, "controlflow/wcp-31-loop-complete-mi.yaml");

        // State-Based Patterns continued (WCP 32-35)
        registerPattern("WCP-32", "Synchronizing Merge + Cancel Region", "Sync merge with cancellation",
            Difficulty.ADVANCED, PatternCategory.STATE_BASED, "statebased/wcp-32-sync-cancel.yaml");
        registerPattern("WCP-33", "Generalized And-Join", "Join that handles dynamic parallelism",
            Difficulty.EXPERT, PatternCategory.STATE_BASED, "statebased/wcp-33-generalized-join.yaml");
        registerPattern("WCP-34", "Static Partial Join", "Join with partial synchronization",
            Difficulty.EXPERT, PatternCategory.STATE_BASED, "statebased/wcp-34-static-partial-join.yaml");
        registerPattern("WCP-35", "Dynamic Partial Join", "Join with dynamic partial sync",
            Difficulty.EXPERT, PatternCategory.STATE_BASED, "statebased/wcp-35-dynamic-partial-join.yaml");

        // Termination Patterns (WCP 36-40)
        registerPattern("WCP-36", "Discriminator + Complete MI", "Discriminator with completion",
            Difficulty.ADVANCED, PatternCategory.TERMINATION, "controlflow/wcp-36-discriminator-complete.yaml");
        registerPattern("WCP-37", "Local Trigger", "Trigger task from external event",
            Difficulty.INTERMEDIATE, PatternCategory.EVENT_DRIVEN, "eventdriven/wcp-37-local-trigger.yaml");
        registerPattern("WCP-38", "Global Trigger", "Trigger from global event",
            Difficulty.INTERMEDIATE, PatternCategory.EVENT_DRIVEN, "eventdriven/wcp-38-global-trigger.yaml");
        registerPattern("WCP-39", "Reset Trigger", "Reset workflow state on trigger",
            Difficulty.ADVANCED, PatternCategory.EVENT_DRIVEN, "eventdriven/wcp-39-reset-trigger.yaml");
        registerPattern("WCP-40", "Reset Trigger + Cancel Region", "Reset with cancellation",
            Difficulty.ADVANCED, PatternCategory.EVENT_DRIVEN, "eventdriven/wcp-40-reset-cancel.yaml");

        // Extended Patterns (WCP 41-43)
        registerPattern("WCP-41", "Blocked And-Split", "Split that blocks until all branches ready",
            Difficulty.EXPERT, PatternCategory.EXTENDED, "extended/wcp-41-blocked-split.yaml");
        registerPattern("WCP-42", "Critical Section", "Protected region for concurrent access",
            Difficulty.ADVANCED, PatternCategory.EXTENDED, "extended/wcp-42-critical-section.yaml");
        registerPattern("WCP-43", "Critical Section + Cancel Region", "Critical section with cancellation",
            Difficulty.EXPERT, PatternCategory.EXTENDED, "extended/wcp-43-critical-cancel.yaml");
        registerPattern("WCP-44", "Saga Pattern", "Long-running transaction with compensation",
            Difficulty.ADVANCED, PatternCategory.EXTENDED, "extended/wcp-44-saga.yaml");

        // Agent Patterns
        registerPattern("AGT-1", "Agent Task", "Task performed by autonomous agent",
            Difficulty.INTERMEDIATE, PatternCategory.AGENT, "agent/agt-1-agent-task.yaml");
        registerPattern("AGT-2", "LLM Decision", "Decision made by LLM-based agent",
            Difficulty.INTERMEDIATE, PatternCategory.AGENT, "agent/agt-2-llm-decision.yaml");
        registerPattern("AGT-3", "Multi-Agent Collaboration", "Multiple agents working together",
            Difficulty.ADVANCED, PatternCategory.AGENT, "agent/agt-3-multi-agent.yaml");
        registerPattern("AGT-4", "Agent Handoff", "Handoff between agents",
            Difficulty.INTERMEDIATE, PatternCategory.AGENT, "agent/agt-4-handoff.yaml");
        registerPattern("AGT-5", "Agent Orchestration", "Orchestrate multiple agents",
            Difficulty.ADVANCED, PatternCategory.AGENT, "agent/agt-5-orchestration.yaml");

        // AI/ML Patterns
        registerPattern("WCP-60", "ML Pipeline", "Machine learning training pipeline",
            Difficulty.ADVANCED, PatternCategory.AI_ML, "aiml/wcp-60-ml-pipeline.yaml");
        registerPattern("WCP-61", "ML Model Inference", "Model inference workflow",
            Difficulty.INTERMEDIATE, PatternCategory.AI_ML, "aiml/wcp-61-ml-model.yaml");
        registerPattern("WCP-62", "Data Preprocessing", "Data preprocessing pipeline",
            Difficulty.INTERMEDIATE, PatternCategory.AI_ML, "aiml/wcp-62-preprocessing.yaml");
        registerPattern("WCP-63", "Feature Engineering", "Feature engineering pipeline",
            Difficulty.ADVANCED, PatternCategory.AI_ML, "aiml/wcp-63-features.yaml");

        // Enterprise Patterns
        registerPattern("ENT-1", "Approval Workflow", "Multi-level approval workflow",
            Difficulty.INTERMEDIATE, PatternCategory.ENTERPRISE, "enterprise/ent-approval.yaml");
        registerPattern("ENT-2", "Escalation", "Escalation on timeout",
            Difficulty.INTERMEDIATE, PatternCategory.ENTERPRISE, "enterprise/ent-escalation.yaml");
        registerPattern("ENT-3", "Delegation", "Task delegation pattern",
            Difficulty.INTERMEDIATE, PatternCategory.ENTERPRISE, "enterprise/ent-delegation.yaml");

        // Distributed Patterns
        registerPattern("DIST-1", "Scatter-Gather", "Distribute work and collect results",
            Difficulty.ADVANCED, PatternCategory.DISTRIBUTED, "distributed/dist-1-scatter-gather.yaml");
        registerPattern("DIST-2", "Map-Reduce", "Map-reduce distributed processing",
            Difficulty.ADVANCED, PatternCategory.DISTRIBUTED, "distributed/dist-2-map-reduce.yaml");
        registerPattern("DIST-3", "Pub-Sub", "Publish-subscribe event distribution",
            Difficulty.INTERMEDIATE, PatternCategory.DISTRIBUTED, "distributed/dist-3-pubsub.yaml");
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
