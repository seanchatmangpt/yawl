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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Event-driven process adaptation engine for real-time workflow modification.
 *
 * <p>EventDrivenAdaptationEngine processes external process events and determines
 * what (if any) adaptation actions should be applied to executing workflows.
 * The engine maintains an immutable, sorted list of adaptation rules and matches
 * incoming events against these rules in priority order. The first matching rule
 * determines the action to be executed.</p>
 *
 * <h2>Design principles</h2>
 * <ul>
 *   <li><strong>Immutable rules:</strong> Rules are never mutated; new engines with
 *       updated rules are created via {@link #withRule(AdaptationRule)}.</li>
 *   <li><strong>Priority ordering:</strong> Rules are always sorted by priority
 *       (lower number = higher priority = evaluated first).</li>
 *   <li><strong>Thread-safe:</strong> No mutable state; safe to use from multiple threads.</li>
 *   <li><strong>Fail-safe:</strong> Non-matching events return a documented "no match" result.</li>
 * </ul>
 *
 * <h2>Example usage</h2>
 * <pre>{@code
 * // Build adaptation rules
 * AdaptationRule fraudRule = new AdaptationRule(
 *     "rule-fraud-high",
 *     "Reject high-risk fraud",
 *     AdaptationCondition.and(
 *         AdaptationCondition.eventType("FRAUD_ALERT"),
 *         AdaptationCondition.payloadAbove("risk_score", 0.9)
 *     ),
 *     AdaptationAction.REJECT_IMMEDIATELY,
 *     10,
 *     "Automatically reject high-risk fraud"
 * );
 *
 * // Create engine with rules
 * EventDrivenAdaptationEngine engine = new EventDrivenAdaptationEngine(List.of(fraudRule));
 *
 * // Process event
 * ProcessEvent event = new ProcessEvent(
 *     "evt-001",
 *     "FRAUD_ALERT",
 *     "fraud-detector",
 *     Instant.now(),
 *     Map.of("risk_score", 0.95),
 *     EventSeverity.CRITICAL
 * );
 *
 * AdaptationResult result = engine.process(event);
 * if (result.adapted()) {
 *     System.out.println("Execute: " + result.executedAction());
 * }
 *
 * // Dynamically add a rule
 * AdaptationRule lowRiskRule = new AdaptationRule(...);
 * EventDrivenAdaptationEngine updatedEngine = engine.withRule(lowRiskRule);
 * }</pre>
 *
 * @author YAWL Foundation
 * @since 6.0
 */
public final class EventDrivenAdaptationEngine {
    private final List<AdaptationRule> rules;

    /**
     * Creates an engine with the given adaptation rules.
     *
     * <p>Rules are sorted by priority (lower numbers first) during construction.
     * The input list is not modified; a new sorted copy is created internally.</p>
     *
     * @param rules the adaptation rules to use (may be null or empty)
     * @throws NullPointerException if any rule in the list is null
     */
    public EventDrivenAdaptationEngine(List<AdaptationRule> rules) {
        Objects.requireNonNull(rules, "rules list must not be null");

        // Validate no null rules
        if (rules.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("rules list must not contain null elements");
        }

        // Sort by priority and store as immutable list
        this.rules = Collections.unmodifiableList(
            new ArrayList<>(rules).stream()
                .sorted((r1, r2) -> Integer.compare(r1.priority(), r2.priority()))
                .toList()
        );
    }

    /**
     * Processes a single event through the adaptation engine.
     *
     * <p>This method evaluates the event against all rules in priority order.
     * Returns the result of the first matching rule, or a "no match" result
     * if no rules match.</p>
     *
     * <p>All matching rules (not just the executed one) are included in the result
     * for audit and visibility purposes.</p>
     *
     * @param event the process event to evaluate
     * @return an adaptation result containing matched rules and executed action
     * @throws NullPointerException if event is null
     */
    public AdaptationResult process(ProcessEvent event) {
        Objects.requireNonNull(event, "event must not be null");

        // Find all matching rules
        List<AdaptationRule> matchedRules = rules.stream()
            .filter(rule -> rule.condition().matches(event))
            .toList();

        // If no matches, return early
        if (matchedRules.isEmpty()) {
            return AdaptationResult.noMatch(event);
        }

        // Execute first matched rule's action
        AdaptationRule executedRule = matchedRules.get(0);
        return new AdaptationResult(
            event,
            matchedRules,
            executedRule.action(),
            true,
            "Executed rule: " + executedRule.name() + " (id: " + executedRule.ruleId() + ")",
            Instant.now()
        );
    }

    /**
     * Processes a batch of events through the adaptation engine.
     *
     * <p>Each event is processed independently via {@link #process(ProcessEvent)}.
     * Results are returned in the same order as input events.</p>
     *
     * @param events the events to process
     * @return a list of adaptation results (same length as input)
     * @throws NullPointerException if events list is null or contains null
     */
    public List<AdaptationResult> processBatch(List<ProcessEvent> events) {
        Objects.requireNonNull(events, "events list must not be null");

        return events.stream()
            .map(this::process)
            .collect(Collectors.toList());
    }

    /**
     * Returns a new engine with an additional rule.
     *
     * <p>This engine is not modified; the new rule is added to a copy of the rules list,
     * and a new engine is returned. This supports immutable updates and functional patterns.</p>
     *
     * @param rule the new rule to add
     * @return a new engine with the rule added (sorted by priority)
     * @throws NullPointerException if rule is null
     */
    public EventDrivenAdaptationEngine withRule(AdaptationRule rule) {
        Objects.requireNonNull(rule, "rule must not be null");

        List<AdaptationRule> updatedRules = new ArrayList<>(this.rules);
        updatedRules.add(rule);
        return new EventDrivenAdaptationEngine(updatedRules);
    }

    /**
     * Returns the number of adaptation rules in this engine.
     *
     * @return the count of rules
     */
    public int ruleCount() {
        return rules.size();
    }

    /**
     * Returns all rules that execute a specific action.
     *
     * <p>Useful for understanding the consequences of an action type or
     * for filtering rules by their effect.</p>
     *
     * @param action the action to filter by
     * @return an unmodifiable list of rules with the given action, ordered by priority
     * @throws NullPointerException if action is null
     */
    public List<AdaptationRule> rulesForAction(AdaptationAction action) {
        Objects.requireNonNull(action, "action must not be null");

        return Collections.unmodifiableList(
            rules.stream()
                .filter(rule -> rule.action() == action)
                .toList()
        );
    }
}
