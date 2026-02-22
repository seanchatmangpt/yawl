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

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable record representing the result of processing an event through
 * the adaptation engine.
 *
 * <p>AdaptationResult captures the outcome of event processing: which rules matched,
 * what action was taken, and a human-readable explanation of the decision.
 * The result includes all matched rules (ordered by priority) even though only
 * the first rule's action is executed; this supports audit trails and decision
 * visibility.</p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * AdaptationResult result = engine.process(event);
 * if (result.adapted()) {
 *     System.out.println("Action: " + result.executedAction());
 *     System.out.println("Matched rules: " + result.matchedRules().size());
 * } else {
 *     System.out.println("No rules matched: " + result.explanation());
 * }
 * }</pre>
 *
 * @param triggeringEvent  the event that was processed
 * @param matchedRules     list of rules that matched (ordered by priority)
 * @param executedAction   the action from the first matched rule, or null if no match
 * @param adapted          true if at least one rule matched and action executed
 * @param explanation      human-readable description of the result
 * @param processedAt      timestamp when this result was created
 *
 * @author YAWL Foundation
 * @since 6.0
 */
public record AdaptationResult(
    ProcessEvent triggeringEvent,
    List<AdaptationRule> matchedRules,
    AdaptationAction executedAction,
    boolean adapted,
    String explanation,
    Instant processedAt
) {
    /**
     * Compact constructor with validation.
     *
     * @throws NullPointerException if triggeringEvent, matchedRules, explanation,
     *                              or processedAt is null
     * @throws IllegalArgumentException if adapted is true but matchedRules is empty
     *                                  or executedAction is null
     */
    public AdaptationResult {
        Objects.requireNonNull(triggeringEvent, "triggeringEvent must not be null");
        Objects.requireNonNull(matchedRules, "matchedRules must not be null");
        Objects.requireNonNull(explanation, "explanation must not be null");
        Objects.requireNonNull(processedAt, "processedAt must not be null");

        if (adapted && (matchedRules.isEmpty() || executedAction == null)) {
            throw new IllegalArgumentException(
                "If adapted is true, matchedRules must not be empty and executedAction must not be null");
        }
        if (!adapted && executedAction != null) {
            throw new IllegalArgumentException(
                "If adapted is false, executedAction must be null");
        }
    }

    /**
     * Factory method to create a result for an event that didn't match any rules.
     *
     * @param event the event that was processed
     * @return a non-adapted result with no matched rules or executed action
     * @throws NullPointerException if event is null
     */
    public static AdaptationResult noMatch(ProcessEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        return new AdaptationResult(
            event,
            List.of(),
            null,
            false,
            "No adaptation rules matched this event",
            Instant.now()
        );
    }
}
