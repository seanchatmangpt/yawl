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

/**
 * Blue Ocean #9: Event-driven process adaptation engine.
 *
 * <p>This package provides a real-time workflow adaptation system that responds to
 * external events by applying predefined adaptation rules. The engine supports CEP-style
 * (Complex Event Processing) pattern matching using composable conditions, priority-ordered
 * rule evaluation, and immediate action execution.</p>
 *
 * <h2>Core Components</h2>
 * <ul>
 *   <li><strong>{@link org.yawlfoundation.yawl.integration.adaptation.ProcessEvent}</strong>:
 *       Immutable event record capturing event type, source, timestamp, payload, and severity.</li>
 *   <li><strong>{@link org.yawlfoundation.yawl.integration.adaptation.EventSeverity}</strong>:
 *       Enum representing event urgency levels (LOW, MEDIUM, HIGH, CRITICAL).</li>
 *   <li><strong>{@link org.yawlfoundation.yawl.integration.adaptation.AdaptationCondition}</strong>:
 *       Functional interface for matching events. Includes factory methods for common patterns
 *       (event type, payload value, severity) and combinators (AND, OR).</li>
 *   <li><strong>{@link org.yawlfoundation.yawl.integration.adaptation.AdaptationRule}</strong>:
 *       Immutable rule combining a condition, action, and priority.</li>
 *   <li><strong>{@link org.yawlfoundation.yawl.integration.adaptation.AdaptationAction}</strong>:
 *       Enum of workflow modifications: REJECT_IMMEDIATELY, ESCALATE_TO_MANUAL, PAUSE_CASE,
 *       CANCEL_CASE, REROUTE_TO_SUBPROCESS, NOTIFY_STAKEHOLDERS, INCREASE_PRIORITY,
 *       DECREASE_PRIORITY.</li>
 *   <li><strong>{@link org.yawlfoundation.yawl.integration.adaptation.AdaptationResult}</strong>:
 *       Immutable result record containing matched rules, executed action, and explanation.</li>
 *   <li><strong>{@link org.yawlfoundation.yawl.integration.adaptation.EventDrivenAdaptationEngine}</strong>:
 *       Core engine that evaluates events against a priority-ordered rule set and returns
 *       adaptation results.</li>
 * </ul>
 *
 * <h2>Key Features</h2>
 * <dl>
 *   <dt>Composable Conditions</dt>
 *   <dd>Build complex matching logic from simple conditions using AND and OR combinators.
 *       Example: {@code AdaptationCondition.and(eventType("FRAUD"), payloadAbove("risk", 0.8))}
 *   </dd>
 *   <dt>Priority-Ordered Rules</dt>
 *   <dd>Rules are sorted by priority (lower number = higher priority) at engine creation.
 *       First matching rule determines the executed action.
 *   </dd>
 *   <dt>Thread-Safe & Immutable</dt>
 *   <dd>Engine and all record types are immutable; safe for concurrent use from multiple threads.
 *       New rules are added via {@code withRule()}, returning a new engine instance.
 *   </dd>
 *   <dt>Audit Trail</dt>
 *   <dd>Results include all matched rules, not just the executed one, enabling audit logs
 *       and decision visibility.
 *   </dd>
 *   <dt>Batch Processing</dt>
 *   <dd>Process multiple events in a single call via {@code processBatch()}.
 *   </dd>
 *   <dt>Zero External Dependencies</dt>
 *   <dd>Pure Java implementation; no CEP library required. Conditions are simple boolean
 *       functions evaluated sequentially.
 *   </dd>
 * </dl>
 *
 * <h2>Example: Fraud Detection & Adaptation</h2>
 * <pre>{@code
 * import java.time.Instant;
 * import java.util.Map;
 *
 * // Define adaptation rules
 * AdaptationRule rejectHighRisk = new AdaptationRule(
 *     "fraud-high-risk",
 *     "Reject high-risk fraud",
 *     AdaptationCondition.and(
 *         AdaptationCondition.eventType("FRAUD_ALERT"),
 *         AdaptationCondition.payloadAbove("risk_score", 0.9)
 *     ),
 *     AdaptationAction.REJECT_IMMEDIATELY,
 *     10,
 *     "Automatically reject transactions with risk score > 0.9"
 * );
 *
 * AdaptationRule escalateMediumRisk = new AdaptationRule(
 *     "fraud-medium-risk",
 *     "Escalate medium-risk fraud",
 *     AdaptationCondition.and(
 *         AdaptationCondition.eventType("FRAUD_ALERT"),
 *         AdaptationCondition.payloadAbove("risk_score", 0.7)
 *     ),
 *     AdaptationAction.ESCALATE_TO_MANUAL,
 *     20,
 *     "Escalate to human review for risk score > 0.7"
 * );
 *
 * // Create engine with rules
 * EventDrivenAdaptationEngine engine = new EventDrivenAdaptationEngine(
 *     List.of(rejectHighRisk, escalateMediumRisk)
 * );
 *
 * // Process fraud event
 * ProcessEvent event = new ProcessEvent(
 *     "evt-fraud-001",
 *     "FRAUD_ALERT",
 *     "fraud-detection-system",
 *     Instant.now(),
 *     Map.of("risk_score", 0.95, "transaction_id", "tx-12345"),
 *     EventSeverity.CRITICAL
 * );
 *
 * AdaptationResult result = engine.process(event);
 *
 * // Handle result
 * if (result.adapted()) {
 *     switch (result.executedAction()) {
 *         case REJECT_IMMEDIATELY -> rejectTransaction(event);
 *         case ESCALATE_TO_MANUAL -> escalateToAnalyst(event);
 *         default -> {} // Handle other actions
 *     }
 * }
 * }</pre>
 *
 * <h2>Integration with YAWL Workflows</h2>
 * <p>The adaptation engine integrates with YAWL's case execution layer via a service
 * that listens for external events (from MCP, A2A, or direct API calls) and applies
 * the resulting adaptations to executing cases. Typical integration points:</p>
 * <ul>
 *   <li>{@code YWorkItem.setState()}: Check for adaptation actions before committing state</li>
 *   <li>{@code YNetRunner.executeTask()}: Inject adaptation points at task execution</li>
 *   <li>External event listener (MCP, A2A): Forward events to adaptation engine</li>
 *   <li>Case monitor / UI: Display adaptation results and audit trail</li>
 * </ul>
 *
 * <h2>Performance Considerations</h2>
 * <ul>
 *   <li><strong>Rule Evaluation:</strong> Rules are evaluated sequentially in priority order.
 *       With short-circuit AND/OR evaluation, most events terminate early.</li>
 *   <li><strong>Memory:</strong> Rules and events are immutable; GC-friendly.</li>
 *   <li><strong>Concurrency:</strong> No synchronization needed; safe for millions of
 *       concurrent events from virtual threads.</li>
 *   <li><strong>Scalability:</strong> For systems with hundreds of rules, consider partitioning
 *       by event type or using a rule indexing strategy (future enhancement).</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @since 6.0
 */
package org.yawlfoundation.yawl.integration.adaptation;
