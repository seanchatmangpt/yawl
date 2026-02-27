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

package org.yawlfoundation.yawl.integration.adaptation;

import java.util.Objects;

/**
 * Immutable record representing an adaptation rule.
 *
 * <p>An adaptation rule combines a condition (what events match) with an action
 * (what to do when matched) and a priority (which rule takes precedence when
 * multiple rules match the same event). Rules are evaluated in priority order:
 * lower priority numbers are evaluated first.</p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * AdaptationRule rule = new AdaptationRule(
 *     "rule-fraud-high-risk",
 *     "Reject high-risk fraud transactions",
 *     AdaptationCondition.and(
 *         AdaptationCondition.eventType("FRAUD_ALERT"),
 *         AdaptationCondition.payloadAbove("risk_score", 0.9)
 *     ),
 *     AdaptationAction.REJECT_IMMEDIATELY,
 *     10,
 *     "Automatically reject fraud events with risk_score > 0.9"
 * );
 * }</pre>
 *
 * @param ruleId      unique identifier for this rule
 * @param name        human-readable name of the rule
 * @param condition   the condition that determines if this rule matches
 * @param action      the action to execute when this rule matches
 * @param priority    priority for rule evaluation (lower number = higher priority)
 * @param description detailed description of the rule's purpose and behavior
 *
 * @author YAWL Foundation
 * @since 6.0
 */
public record AdaptationRule(
    String ruleId,
    String name,
    AdaptationCondition condition,
    AdaptationAction action,
    int priority,
    String description
) {
    /**
     * Compact constructor with validation.
     *
     * @throws NullPointerException if ruleId, name, condition, action, or description is null
     * @throws IllegalArgumentException if ruleId or name is empty, or priority is negative
     */
    public AdaptationRule {
        Objects.requireNonNull(ruleId, "ruleId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(condition, "condition must not be null");
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(description, "description must not be null");

        if (ruleId.isBlank()) {
            throw new IllegalArgumentException("ruleId must not be blank");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (priority < 0) {
            throw new IllegalArgumentException("priority must not be negative");
        }
    }

    /**
     * Checks if this rule has high priority.
     *
     * <p>High priority is defined as a priority value of 10 or lower.
     * Lower numbers indicate higher priority in execution order.</p>
     *
     * @return true if priority &lt;= 10, false otherwise
     */
    public boolean isHighPriority() {
        return priority <= 10;
    }
}
