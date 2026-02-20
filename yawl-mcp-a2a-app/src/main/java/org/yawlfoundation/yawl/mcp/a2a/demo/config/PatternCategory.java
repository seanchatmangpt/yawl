/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.mcp.a2a.demo.config;

/**
 * Enumeration of YAWL workflow pattern categories for demo classification.
 *
 * <p>This enum organizes the 43+ YAWL workflow control-flow patterns into
 * logical categories for demonstration, testing, and reporting purposes.
 * Each category includes an ANSI color code for console output formatting.</p>
 *
 * <h2>Categories</h2>
 * <ul>
 *   <li><b>BASIC</b>: Fundamental control flow patterns (Sequence, Parallel Split, etc.)</li>
 *   <li><b>BRANCHING</b>: Decision and synchronization patterns (Exclusive Choice, Merge, etc.)</li>
 *   <li><b>MULTI_INSTANCE</b>: Multiple instance patterns (parallel, sequential, deferred)</li>
 *   <li><b>STATE_BASED</b>: State-driven patterns (Deferred Choice, Interleaved Routing, Milestone)</li>
 *   <li><b>DISTRIBUTED</b>: Distributed workflow patterns (Multiple instances across systems)</li>
 *   <li><b>EVENT_DRIVEN</b>: Event-based patterns (Cancellation, Trigger, Compensation)</li>
 *   <li><b>AI_ML</b>: AI/ML integration patterns (Model inference, Agentic workflows)</li>
 *   <li><b>ENTERPRISE</b>: Enterprise patterns (Transaction, Audit, Compliance)</li>
 *   <li><b>AGENT</b>: Agent-based patterns (Multi-agent coordination, Handoff, Negotiation)</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * PatternCategory category = PatternCategory.BASIC;
 * System.out.println(category.getColorCode() + "Pattern Name" + category.getResetCode());
 * System.out.println("Category: " + category.getDisplayName());
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0
 */
public enum PatternCategory {

    /**
     * Basic Control Flow patterns.
     * <p>Includes: Sequence (WCP-1), Parallel Split (WCP-2), Synchronization (WCP-3),
     * Exclusive Choice (WCP-4), Simple Merge (WCP-5)</p>
     */
    BASIC("Basic Control Flow", "\u001B[32m"),

    /**
     * Branching and Synchronization patterns.
     * <p>Includes: Multi-Choice (WCP-6), Structured Synchronizing Merge (WCP-7),
     * Multi-Merge (WCP-8), Structured Discriminator (WCP-9)</p>
     */
    BRANCHING("Branching and Sync", "\u001B[34m"),

    /**
     * Multi-Instance patterns.
     * <p>Includes: Arbitrary Cycles (WCP-10), Implicit Termination (WCP-11),
     * MI without Synchronization (WCP-12), MI with a priori Design Time Knowledge (WCP-13),
     * MI with a priori Runtime Knowledge (WCP-14), MI without a priori Runtime Knowledge (WCP-15)</p>
     */
    MULTI_INSTANCE("Multi-Instance", "\u001B[35m"),

    /**
     * State-Based patterns.
     * <p>Includes: Deferred Choice (WCP-16), Interleaved Parallel Routing (WCP-17),
     * Milestone (WCP-18), Cancel Activity (WCP-19), Cancel Case (WCP-20)</p>
     */
    STATE_BASED("State-Based", "\u001B[36m"),

    /**
     * Distributed patterns.
     * <p>Includes patterns for distributed workflow execution across
     * multiple systems or organizational boundaries.</p>
     */
    DISTRIBUTED("Distributed", "\u001B[33m"),

    /**
     * Event-Driven patterns.
     * <p>Includes: External Trigger patterns, Event-based execution control,
     * Compensation handling, and exception management.</p>
     */
    EVENT_DRIVEN("Event-Driven", "\u001B[31m"),

    /**
     * AI/ML Integration patterns.
     * <p>Includes: Model inference workflows, Agentic decision making,
     * LLM-based routing, and ML pipeline orchestration.</p>
     */
    AI_ML("AI/ML Integration", "\u001B[38;5;208m"),

    /**
     * Enterprise Patterns.
     * <p>Includes: Transaction boundaries, Audit logging, Compliance checking,
     * and enterprise integration patterns.</p>
     */
    ENTERPRISE("Enterprise Patterns", "\u001B[38;5;93m"),

    /**
     * Agent Patterns.
     * <p>Includes: Multi-agent coordination, Task handoff, Negotiation protocols,
     * and autonomous agent workflows.</p>
     */
    AGENT("Agent Patterns", "\u001B[38;5;40m"),

    /**
     * Greg-Verse Scenario patterns.
     * <p>Multi-agent A2A business simulations.</p>
     */
    GREGVERSE_SCENARIO("GregVerse Scenarios", "\u001B[38;5;213m"),

    /**
     * Advanced Branching patterns.
     * <p>Includes: Multi-Choice, Structured Synchronizing Merge,
     * Multi-Merge, Structured Discriminator.</p>
     */
    ADVANCED_BRANCHING("Advanced Branching", "\u001B[38;5;69m"),

    /**
     * Structural patterns.
     * <p>Includes: Implicit Termination and structural workflow patterns.</p>
     */
    STRUCTURAL("Structural", "\u001B[38;5;102m"),

    /**
     * Iteration patterns.
     * <p>Includes: Structured Loop and looping constructs.</p>
     */
    ITERATION("Iteration", "\u001B[38;5;132m"),

    /**
     * Termination patterns.
     * <p>Includes: Discriminator with completion and termination handling.</p>
     */
    TERMINATION("Termination", "\u001B[38;5;167m"),

    /**
     * Extended patterns.
     * <p>Includes: Blocked And-Split, Critical Section, Saga patterns.</p>
     */
    EXTENDED("Extended", "\u001B[38;5;243m"),

    /**
     * Cancellation patterns.
     * <p>Includes: Cancel Task, Cancel Case, Cancel Region patterns.</p>
     */
    CANCELLATION("Cancellation", "\u001B[38;5;197m"),

    /**
     * Multi-Instance patterns.
     * <p>Includes patterns for handling multiple task instances.</p>
     */
    MULTIINSTANCE("Multi-Instance", "\u001B[38;5;141m"),

    /**
     * Unclassified patterns that don't fit other categories.
     */
    UNCLASSIFIED("Unclassified", "\u001B[37m");

    /**
     * ANSI reset code to return to default terminal colors.
     */
    public static final String RESET_CODE = "\u001B[0m";

    private final String displayName;
    private final String colorCode;

    /**
     * Construct a pattern category with display name and ANSI color code.
     *
     * @param displayName human-readable category name for display
     * @param colorCode ANSI escape code for terminal coloring
     */
    PatternCategory(String displayName, String colorCode) {
        this.displayName = displayName;
        this.colorCode = colorCode;
    }

    /**
     * Get the human-readable display name for this category.
     *
     * @return display name suitable for UI and reports
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Get the ANSI color code for terminal output.
     *
     * <p>Use this code to colorize console output for this category:</p>
     * <pre>{@code
     * System.out.println(category.getColorCode() + text + category.getResetCode());
     * }</pre>
     *
     * @return ANSI escape sequence for the category color
     */
    public String getColorCode() {
        return colorCode;
    }

    /**
     * Get the ANSI reset code to return to default terminal colors.
     *
     * <p>Always use this after outputting colored text to reset the terminal:</p>
     * <pre>{@code
     * System.out.println(category.getColorCode() + "Colored" + PatternCategory.getResetCode());
     * }</pre>
     *
     * @return ANSI reset escape sequence
     */
    public static String getResetCode() {
        return RESET_CODE;
    }

    /**
     * Find a category by its display name (case-insensitive).
     *
     * @param name the display name to search for
     * @return matching category, or null if not found
     */
    public static PatternCategory fromDisplayName(String name) {
        if (name == null) {
            return null;
        }
        for (PatternCategory category : values()) {
            if (category.displayName.equalsIgnoreCase(name)) {
                return category;
            }
        }
        return null;
    }

    /**
     * Find a category by its enum name (case-insensitive).
     *
     * @param name the enum name to search for
     * @return matching category, or null if not found
     */
    public static PatternCategory fromName(String name) {
        if (name == null) {
            return null;
        }
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Check if this category is enabled in the given filter list.
     *
     * <p>If the filter list is empty or null, all categories are considered enabled.</p>
     *
     * @param enabledCategories list of enabled categories, may be null or empty
     * @return true if this category should be included
     */
    public boolean isEnabledIn(java.util.List<PatternCategory> enabledCategories) {
        if (enabledCategories == null || enabledCategories.isEmpty()) {
            return true;
        }
        return enabledCategories.contains(this);
    }

    /**
     * Format text with this category's color.
     *
     * @param text the text to colorize
     * @return colored text with reset code appended
     */
    public String colorize(String text) {
        return colorCode + text + RESET_CODE;
    }

    /**
     * Determine the pattern category from a pattern ID.
     *
     * @param patternId the pattern ID (e.g., "WCP-1", "ENT-1", "AGT-1")
     * @return the matching category, or UNCLASSIFIED if not determinable
     */
    public static PatternCategory fromPatternId(String patternId) {
        if (patternId == null || patternId.isEmpty()) {
            return UNCLASSIFIED;
        }

        String id = patternId.toUpperCase();

        // Control-flow patterns (WCP-1 to WCP-43)
        if (id.startsWith("WCP-")) {
            try {
                int num = Integer.parseInt(id.substring(4));
                if (num >= 1 && num <= 5) return BASIC;
                if (num >= 6 && num <= 11) return BRANCHING;
                if (num >= 12 && num <= 17) return MULTI_INSTANCE;
                if (num >= 18 && num <= 21) return STATE_BASED;
                if (num >= 22 && num <= 43) return EXTENDED;
                if (num >= 44 && num <= 50) return DISTRIBUTED;
                if (num >= 51 && num <= 59) return EVENT_DRIVEN;
                if (num >= 60 && num <= 68) return AI_ML;
            } catch (NumberFormatException e) {
                return UNCLASSIFIED;
            }
        }

        // Enterprise patterns
        if (id.startsWith("ENT-")) {
            return ENTERPRISE;
        }

        // Agent patterns
        if (id.startsWith("AGT-")) {
            return AGENT;
        }

        // GregVerse Scenario patterns
        if (id.startsWith("GVS-") || id.startsWith("GV-")) {
            return GREGVERSE_SCENARIO;
        }

        // Distributed patterns
        if (id.startsWith("DIST-")) {
            return DISTRIBUTED;
        }

        return UNCLASSIFIED;
    }
}
