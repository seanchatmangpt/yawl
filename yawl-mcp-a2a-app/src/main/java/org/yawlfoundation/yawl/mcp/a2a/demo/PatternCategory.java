/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.demo;

/**
 * Enumeration of workflow pattern categories as defined by the Workflow Patterns Initiative.
 *
 * <p>This classification organizes the 68 workflow control-flow patterns into logical groups
 * based on their behavioral characteristics and application domains. Categories range from
 * fundamental control-flow constructs to advanced distributed computing and AI/ML patterns.</p>
 *
 * <h2>Category Hierarchy</h2>
 * <ul>
 *   <li>{@link #BASIC} - Fundamental control-flow patterns (WCP-1 to WCP-5)</li>
 *   <li>{@link #BRANCHING} - Advanced branching and synchronization (WCP-6 to WCP-11)</li>
 *   <li>{@link #MULTI_INSTANCE} - Multiple instance activity patterns (WCP-12 to WCP-17)</li>
 *   <li>{@link #STATE_BASED} - State-based control patterns (WCP-18 to WCP-21)</li>
 *   <li>{@link #DISTRIBUTED} - Distributed systems patterns (WCP-44 to WCP-50)</li>
 *   <li>{@link #EVENT_DRIVEN} - Event-driven architecture patterns (WCP-51 to WCP-59)</li>
 *   <li>{@link #AI_ML} - Machine learning integration patterns (WCP-60 to WCP-68)</li>
 *   <li>{@link #ENTERPRISE} - Enterprise workflow patterns (ENT-1 to ENT-8)</li>
 *   <li>{@link #AGENT} - Agentic AI patterns (AGT-1 to AGT-3)</li>
 * </ul>
 *
 * @see PatternInfo
 * @see PatternRegistry
 * @since 6.0.0
 */
public enum PatternCategory {

    /**
     * Basic control-flow patterns forming the foundation of workflow modeling.
     *
     * <p>These patterns represent the fundamental constructs that appear in virtually
     * every workflow language: sequence, parallelism, choice, and merging. Named after
     * van der Aalst et al.'s original workflow patterns taxonomy.</p>
     *
     * <p>Patterns: WCP-1 (Sequence), WCP-2 (Parallel Split), WCP-3 (Synchronization),
     * WCP-4 (Exclusive Choice), WCP-5 (Simple Merge)</p>
     */
    BASIC("Basic Control Flow", "Fundamental workflow constructs"),

    /**
     * Advanced branching and synchronization patterns.
     *
     * <p>These patterns extend basic control-flow with multi-choice, complex merging,
     * and loop constructs. They address the challenge of coordinating multiple
     * execution paths with various synchronization semantics.</p>
     *
     * <p>Patterns: WCP-6 (Multi-Choice), WCP-7 (Structured Synchronizing Merge),
     * WCP-8 (Multi-Merge), WCP-9 (Discriminator), WCP-10 (Structured Loop),
     * WCP-11 (Implicit Termination)</p>
     */
    BRANCHING("Advanced Branching", "Complex control flow with multiple paths"),

    /**
     * Multiple instance activity patterns for parallel task execution.
     *
     * <p>These patterns address scenarios where multiple instances of the same task
     * must be created and coordinated. The key dimensions are: when instances are
     * created (design-time vs. runtime), how many instances, and whether they
     * require synchronization.</p>
     *
     * <p>Patterns: WCP-12 (MI without Synchronization), WCP-13 (MI with Static Design),
     * WCP-14 (MI with Dynamic Design), WCP-15 (MI with Runtime Knowledge),
     * WCP-16 (MI without Runtime Knowledge), WCP-17 (Interleaved Parallel Routing)</p>
     */
    MULTI_INSTANCE("Multiple Instance", "Parallel task instantiation patterns"),

    /**
     * State-based control patterns driven by external events or conditions.
     *
     * <p>Unlike data-driven or explicit choice patterns, state-based patterns respond
     * to changes in the environment or workflow state. They enable reactive workflow
     * behavior and cancellation semantics.</p>
     *
     * <p>Patterns: WCP-18 (Deferred Choice), WCP-19 (Milestone),
     * WCP-20 (Cancel Activity), WCP-21 (Cancel Case)</p>
     */
    STATE_BASED("State Based", "Event and state-driven control flow"),

    /**
     * Distributed systems patterns for resilient microservice workflows.
     *
     * <p>These patterns address the challenges of distributed workflow execution:
     * partial failures, network partitions, and maintaining consistency across
     * services. Adapted from enterprise integration patterns and cloud-native
     * resilience patterns.</p>
     *
     * <p>Patterns: WCP-44 (Saga Orchestration), WCP-45 (Saga Choreography),
     * WCP-46 (Two-Phase Commit), WCP-47 (Circuit Breaker), WCP-48 (Retry Pattern),
     * WCP-49 (Bulkhead), WCP-50 (Timeout)</p>
     */
    DISTRIBUTED("Distributed Systems", "Resilience patterns for microservices"),

    /**
     * Event-driven architecture patterns for asynchronous workflow coordination.
     *
     * <p>These patterns enable loosely-coupled workflow designs where components
     * communicate through events rather than direct invocation. Essential for
     * building scalable, reactive workflow systems.</p>
     *
     * <p>Patterns: WCP-51 (Event Gateway), WCP-52 (Outbox Pattern),
     * WCP-53 (Scatter-Gather), WCP-54 (Event Router), WCP-55 (Event Stream),
     * WCP-56 (CQRS), WCP-57 (Event Sourcing), WCP-58 (Compensating Transaction),
     * WCP-59 (Side-by-Side)</p>
     */
    EVENT_DRIVEN("Event Driven", "Asynchronous event-based coordination"),

    /**
     * Machine learning integration patterns for AI-enhanced workflows.
     *
     * <p>These patterns address the unique challenges of incorporating ML models
     * into business workflows: model serving, confidence thresholds, fallback
     * strategies, and drift detection. Essential for production ML systems.</p>
     *
     * <p>Patterns: WCP-60 (Rules Engine), WCP-61 (ML Model), WCP-62 (Human-AI Handoff),
     * WCP-63 (Model Fallback), WCP-64 (Confidence Threshold), WCP-65 (Feature Store),
     * WCP-66 (Pipeline), WCP-67 (Drift Detection), WCP-68 (Auto-Retrain)</p>
     */
    AI_ML("AI/ML Integration", "Machine learning workflow patterns"),

    /**
     * Enterprise workflow patterns for business process management.
     *
     * <p>These patterns address common enterprise requirements: approvals, escalation,
     * delegation, and compliance. They build upon the foundational patterns with
     * domain-specific semantics for organizational workflows.</p>
     *
     * <p>Patterns: ENT-1 (Sequential Approval), ENT-2 (Parallel Approval),
     * ENT-3 (Escalation), ENT-4 (Compensation), ENT-5 (SLA Monitoring),
     * ENT-6 (Delegation), ENT-7 (Four-Eyes Principle), ENT-8 (Nomination)</p>
     */
    ENTERPRISE("Enterprise", "Business process management patterns"),

    /**
     * Agentic AI patterns for autonomous agent workflows.
     *
     * <p>These patterns address the emerging domain of AI agents operating within
     * workflows: human-agent collaboration, LLM decision making, and agent-assisted
     * tasks. Represents the frontier of workflow automation.</p>
     *
     * <p>Patterns: AGT-1 (Agent-Assisted), AGT-2 (LLM Decision),
     * AGT-3 (Human-Agent Handoff)</p>
     */
    AGENT("Agent", "Autonomous AI agent patterns");

    private final String displayName;
    private final String description;

    PatternCategory(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * Returns the human-readable display name for this category.
     *
     * @return the display name, never null
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns a brief description of this category's purpose.
     *
     * @return the description, never null
     */
    public String getDescription() {
        return description;
    }
}
