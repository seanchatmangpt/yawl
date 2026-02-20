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
 * Enumeration representing the implementation complexity level of a workflow pattern.
 *
 * <p>Difficulty levels help users understand the cognitive and technical effort
 * required to implement and maintain a pattern correctly. This classification
 * aids in pattern selection and learning path planning.</p>
 *
 * <h2>Level Definitions</h2>
 * <ul>
 *   <li>{@link #BASIC} - Single construct, no concurrency concerns</li>
 *   <li>{@link #INTERMEDIATE} - Multiple constructs, simple synchronization</li>
 *   <li>{@link #ADVANCED} - Complex state management, error handling</li>
 *   <li>{@link #EXPERT} - Distributed systems, formal verification recommended</li>
 * </ul>
 *
 * @see PatternInfo
 * @see PatternRegistry
 * @since 6.0.0
 */
public enum Difficulty {

    /**
     * Basic patterns with minimal implementation complexity.
     *
     * <p>Characteristics:</p>
     * <ul>
     *   <li>Single control-flow construct</li>
     *   <li>No concurrency or synchronization concerns</li>
     *   <li>Direct mapping to YAWL elements</li>
     *   <li>Suitable for workflow beginners</li>
     * </ul>
     *
     * <p>Examples: Sequence, Parallel Split, Exclusive Choice</p>
     */
    BASIC("Basic", 1, "Single construct, no concurrency"),

    /**
     * Intermediate patterns requiring moderate implementation skill.
     *
     * <p>Characteristics:</p>
     * <ul>
     *   <li>Multiple interacting constructs</li>
     *   <li>Simple synchronization semantics</li>
     *   <li>Predictable execution paths</li>
     *   <li>Some error handling required</li>
     * </ul>
     *
     * <p>Examples: Multi-Choice, Discriminator, Simple Merge</p>
     */
    INTERMEDIATE("Intermediate", 2, "Multiple constructs, simple sync"),

    /**
     * Advanced patterns requiring significant implementation expertise.
     *
     * <p>Characteristics:</p>
     * <ul>
     *   <li>Complex state management</li>
     *   <li>Non-deterministic execution</li>
     *   <li>Comprehensive error handling</li>
     *   <li>Testing complexity increases</li>
     * </ul>
     *
     * <p>Examples: Deferred Choice, Milestone, Cancel Region, CQRS</p>
     */
    ADVANCED("Advanced", 3, "Complex state, error handling"),

    /**
     * Expert patterns requiring deep systems knowledge.
     *
     * <p>Characteristics:</p>
     * <ul>
     *   <li>Distributed systems concerns</li>
     *   <li>Formal verification recommended</li>
     *   <li>Critical failure modes</li>
     *   <li>Production hardening essential</li>
     * </ul>
     *
     * <p>Examples: Two-Phase Commit, Saga Orchestration, Event Sourcing</p>
     */
    EXPERT("Expert", 4, "Distributed systems, formal verification");

    private final String displayName;
    private final int level;
    private final String characteristics;

    Difficulty(String displayName, int level, String characteristics) {
        this.displayName = displayName;
        this.level = level;
        this.characteristics = characteristics;
    }

    /**
     * Returns the human-readable display name for this difficulty level.
     *
     * @return the display name, never null
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the numeric level (1-4) for ordering and comparison.
     *
     * @return the difficulty level, from 1 (easiest) to 4 (hardest)
     */
    public int getLevel() {
        return level;
    }

    /**
     * Returns a brief description of the characteristics at this level.
     *
     * @return the characteristics description, never null
     */
    public String getCharacteristics() {
        return characteristics;
    }
}
