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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.synthesis;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Structured business intent for workflow synthesis.
 * Describes what the workflow should do without specifying HOW (no WCP assumptions).
 *
 * <p>A WorkflowIntent captures the declarative specification of a desired business process,
 * including the overarching goal, ordered activity hints, desired WCP patterns, and constraints.
 * This intent is then synthesized into a concrete YAWL specification via SPARQL queries
 * and soundness verification.</p>
 *
 * @param goal the high-level business goal (e.g., "Approve purchase order")
 * @param activities ordered hints of activities to include (e.g., ["Submit", "Review", "Approve"])
 * @param wcpHints WCP pattern IDs to apply (e.g., ["WCP-1", "WCP-2"])
 * @param constraints domain-specific constraints as key-value pairs
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record WorkflowIntent(
    String goal,
    List<String> activities,
    List<String> wcpHints,
    Map<String, Object> constraints
) {
    /**
     * Constructs a WorkflowIntent with validation.
     *
     * @throws NullPointerException if goal is null
     * @throws IllegalArgumentException if goal is blank
     */
    public WorkflowIntent {
        Objects.requireNonNull(goal, "goal must not be null");
        if (goal.isBlank()) {
            throw new IllegalArgumentException("goal must not be blank");
        }
        if (activities == null) {
            activities = List.of();
        }
        if (wcpHints == null) {
            wcpHints = List.of();
        }
        if (constraints == null) {
            constraints = Map.of();
        }
    }

    /**
     * Factory method for creating a sequential workflow intent.
     * Convenience constructor for simple linear workflows (WCP-1: Sequence).
     *
     * @param goal the business goal
     * @param activities ordered activity names
     * @return a new WorkflowIntent with WCP-1 (Sequence) pattern
     */
    public static WorkflowIntent sequential(String goal, String... activities) {
        return new WorkflowIntent(goal, List.of(activities), List.of("WCP-1"), Map.of());
    }
}
