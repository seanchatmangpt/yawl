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

package org.yawlfoundation.yawl.integration.mcp.spec;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.yawlfoundation.yawl.integration.adaptation.AdaptationAction;
import org.yawlfoundation.yawl.integration.adaptation.AdaptationCondition;
import org.yawlfoundation.yawl.integration.adaptation.AdaptationResult;
import org.yawlfoundation.yawl.integration.adaptation.AdaptationRule;
import org.yawlfoundation.yawl.integration.adaptation.EventDrivenAdaptationEngine;
import org.yawlfoundation.yawl.integration.adaptation.EventSeverity;
import org.yawlfoundation.yawl.integration.adaptation.ProcessEvent;
import org.yawlfoundation.yawl.integration.util.EventSeverityUtils;
import org.yawlfoundation.yawl.integration.mcp.util.McpResponseBuilder;
import org.yawlfoundation.yawl.integration.mcp.util.McpLogger;
import org.yawlfoundation.yawl.integration.util.PayloadParser;
import org.yawlfoundation.yawl.integration.util.YawlConstants;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * MCP tool specifications wrapping {@link EventDrivenAdaptationEngine} — no LLM required.
 *
 * <p>Exposes two tools:</p>
 * <ul>
 *   <li>{@code yawl_evaluate_event} — evaluates a process event against a built-in
 *       ruleset and returns the matching adaptation action (or NO_MATCH).</li>
 *   <li>{@code yawl_list_adaptation_rules} — returns the active adaptation ruleset
 *       as a human-readable list.</li>
 * </ul>
 *
 * <p>The default ruleset covers common workflow adaptation patterns:</p>
 * <ul>
 *   <li>DEADLINE_EXCEEDED → ESCALATE_TO_MANUAL</li>
 *   <li>RESOURCE_UNAVAILABLE → PAUSE_CASE</li>
 *   <li>SLA_BREACH → ESCALATE_TO_MANUAL</li>
 *   <li>FRAUD_ALERT → REJECT_IMMEDIATELY (if risk_score &gt; 0.8)</li>
 *   <li>ERROR → PAUSE_CASE</li>
 *   <li>CRITICAL_ERROR → REJECT_IMMEDIATELY</li>
 *   <li>PRIORITY_CHANGE → INCREASE_PRIORITY / DECREASE_PRIORITY</li>
 * </ul>
 *
 * @since YAWL 6.0
 */
public final class YawlAdaptationToolSpecifications {

    public static final List<AdaptationRule> DEFAULT_RULES = buildDefaultRules();
    private final EventDrivenAdaptationEngine engine;
    private final McpLogger logger = McpLogger.forTool(YawlConstants.TOOL_EVALUATE_EVENT);

    public YawlAdaptationToolSpecifications() {
        this.engine = new EventDrivenAdaptationEngine(DEFAULT_RULES);
    }

    /**
     * Creates all adaptation MCP tool specifications.
     *
     * @return list of two tool specifications
     */
    public List<McpServerFeatures.SyncToolSpecification> createAll() {
        return List.of(
            buildEvaluateEventTool(),
            buildListRulesTool()
        );
    }

    // =========================================================================
    // Tool: yawl_evaluate_event
    // =========================================================================

    private McpServerFeatures.SyncToolSpecification buildEvaluateEventTool() {
        java.util.LinkedHashMap<String, Object> props = new java.util.LinkedHashMap<>();
        props.put("eventType", Map.of("type", "string",
            "description", "Type of process event, e.g. DEADLINE_EXCEEDED, FRAUD_ALERT, ERROR"));
        props.put("severity", Map.of("type", "string",
            "description", "Event severity: LOW, MEDIUM, HIGH, CRITICAL (default: MEDIUM)"));
        props.put("payload", Map.of("type", "string",
            "description", "Optional key=value pairs, e.g. 'risk_score=0.95'"));

        McpSchema.Tool tool = McpSchema.Tool.builder()
            .name(YawlConstants.TOOL_EVALUATE_EVENT)
            .description("Evaluate a process event against the built-in YAWL adaptation ruleset and return "
                + "the matching adaptation action. No LLM required — pure deterministic rule matching. "
                + "Built-in rules cover: DEADLINE_EXCEEDED, RESOURCE_UNAVAILABLE, SLA_BREACH, "
                + "FRAUD_ALERT (with risk_score payload), ERROR, CRITICAL_ERROR, PRIORITY_CHANGE.")
            .inputSchema(new McpSchema.JsonSchema("object", props, List.of("eventType"),
                false, null, Map.of()))
            .build();

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, request) -> {
            long start = System.currentTimeMillis();
            String eventType = getString(request.arguments(), "eventType", null);
            if (eventType == null || eventType.isBlank()) {
                return McpResponseBuilder.error("'eventType' parameter is required.");
            }

            EventSeverity severity = org.yawlfoundation.yawl.integration.adaptation.EventSeverity.valueOf(
                EventSeverityUtils.parseSeverity(getString(request.arguments(), "severity", "MEDIUM")));
            String payloadStr = getString(request.arguments(), "payload", "");
            Map<String, Object> payload = new java.util.HashMap<>();
            if (!payloadStr.isBlank()) {
                // Parse simple key=value pairs separated by commas or semicolons
                for (String pair : payloadStr.split("[,;]")) {
                    String[] parts = pair.trim().split("=", 2);
                    if (parts.length == 2) {
                        String key = parts[0].trim();
                        String val = parts[1].trim();
                        try {
                            payload.put(key, Double.parseDouble(val));
                        } catch (NumberFormatException e) {
                            payload.put(key, val);
                        }
                    }
                }
            }

            ProcessEvent event = new ProcessEvent(
                UUID.randomUUID().toString(),
                eventType.trim().toUpperCase(),
                "mcp-client",
                Instant.now(),
                payload,
                severity
            );

            AdaptationResult result = engine.process(event);
            long elapsed = System.currentTimeMillis() - start;

            String response = buildEvaluateResponse(event, result, elapsed);
            return McpResponseBuilder.successWithTiming(response, "evaluateEvent", elapsed);
        });
    }

    // =========================================================================
    // Tool: yawl_list_adaptation_rules
    // =========================================================================

    private McpServerFeatures.SyncToolSpecification buildListRulesTool() {
        McpSchema.Tool tool = McpSchema.Tool.builder()
            .name(YawlConstants.TOOL_LIST_RULES)
            .description("List all active adaptation rules in the YAWL event-driven adaptation engine. "
                + "Returns each rule's ID, event type trigger, action, priority, and description. "
                + "No LLM required — purely reads the configured ruleset.")
            .inputSchema(new McpSchema.JsonSchema("object", Map.of(), List.of(),
                false, null, Map.of()))
            .build();

        return new McpServerFeatures.SyncToolSpecification(tool, (exchange, args) -> {
            long start = System.currentTimeMillis();
            StringBuilder sb = new StringBuilder();
            sb.append("YAWL Adaptation Rules (").append(DEFAULT_RULES.size()).append(" total):\n\n");

            for (int i = 0; i < DEFAULT_RULES.size(); i++) {
                AdaptationRule rule = DEFAULT_RULES.get(i);
                sb.append(i + 1).append(". [").append(rule.ruleId()).append("]\n");
                sb.append("   Name:     ").append(rule.name()).append("\n");
                sb.append("   Action:   ").append(rule.action()).append(" — ").append(rule.action().description()).append("\n");
                sb.append("   Priority: ").append(rule.priority()).append("\n");
                sb.append("   Desc:     ").append(rule.description()).append("\n\n");
            }
            return McpResponseBuilder.successWithTiming(sb.toString().trim(), "listRules", System.currentTimeMillis() - start);
        });
    }

    // =========================================================================
    // Default Ruleset
    // =========================================================================

    static List<AdaptationRule> buildDefaultRules() {
        List<AdaptationRule> rules = new ArrayList<>();

        rules.add(new AdaptationRule(
            "rule-deadline-exceeded",
            "Deadline Exceeded",
            AdaptationCondition.eventType("DEADLINE_EXCEEDED"),
            AdaptationAction.ESCALATE_TO_MANUAL,
            10,
            "Escalate cases where a deadline has been exceeded to manual review"
        ));

        rules.add(new AdaptationRule(
            "rule-resource-unavailable",
            "Resource Unavailable",
            AdaptationCondition.eventType("RESOURCE_UNAVAILABLE"),
            AdaptationAction.PAUSE_CASE,
            20,
            "Pause case execution when required resources are unavailable"
        ));

        rules.add(new AdaptationRule(
            "rule-sla-breach",
            "SLA Breach",
            AdaptationCondition.eventType("SLA_BREACH"),
            AdaptationAction.ESCALATE_TO_MANUAL,
            15,
            "Escalate cases that have breached SLA thresholds to manual review"
        ));

        rules.add(new AdaptationRule(
            "rule-fraud-high-risk",
            "High-Risk Fraud Alert",
            AdaptationCondition.and(
                AdaptationCondition.eventType("FRAUD_ALERT"),
                AdaptationCondition.payloadAbove("risk_score", 0.8)
            ),
            AdaptationAction.REJECT_IMMEDIATELY,
            5,
            "Immediately reject cases with FRAUD_ALERT and risk_score > 0.8"
        ));

        rules.add(new AdaptationRule(
            "rule-critical-error",
            "Critical Error",
            AdaptationCondition.and(
                AdaptationCondition.eventType("ERROR"),
                AdaptationCondition.severityAtLeast(EventSeverity.CRITICAL)
            ),
            AdaptationAction.REJECT_IMMEDIATELY,
            8,
            "Immediately reject cases with critical-severity errors"
        ));

        rules.add(new AdaptationRule(
            "rule-error-pause",
            "Error — Pause for Investigation",
            AdaptationCondition.eventType("ERROR"),
            AdaptationAction.PAUSE_CASE,
            25,
            "Pause case execution on any error for investigation"
        ));

        rules.add(new AdaptationRule(
            "rule-priority-increase",
            "Priority Increase",
            AdaptationCondition.eventType("PRIORITY_INCREASE"),
            AdaptationAction.INCREASE_PRIORITY,
            30,
            "Increase case priority when a priority increase event is received"
        ));

        rules.add(new AdaptationRule(
            "rule-notify",
            "Notify Stakeholders",
            AdaptationCondition.severityAtLeast(EventSeverity.HIGH),
            AdaptationAction.NOTIFY_STAKEHOLDERS,
            50,
            "Notify stakeholders for any HIGH or CRITICAL severity event"
        ));

        return List.copyOf(rules);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String buildEvaluateResponse(ProcessEvent event, AdaptationResult result, long elapsed) {
        StringBuilder sb = new StringBuilder();
        sb.append("Event Adaptation Result\n");
        sb.append("=======================\n");
        sb.append("Event Type:  ").append(event.eventType()).append("\n");
        sb.append("Severity:    ").append(event.severity()).append("\n");
        sb.append("Adapted:     ").append(result.adapted()).append("\n");

        if (result.adapted()) {
            sb.append("Action:      ").append(result.executedAction())
              .append(" — ").append(result.executedAction().description()).append("\n");
            sb.append("Explanation: ").append(result.explanation()).append("\n");
            sb.append("Matched Rules: ").append(result.matchedRules().size()).append("\n");
            result.matchedRules().forEach(rule ->
                sb.append("  • [").append(rule.ruleId()).append("] ").append(rule.name()).append("\n")
            );
        } else {
            sb.append("Action:      NO_MATCH — no adaptation rules matched this event\n");
        }

        sb.append("Elapsed:     ").append(elapsed).append("ms");
        return sb.toString();
    }

    private String getString(Map<String, Object> args, String key, String defaultValue) {
        Object val = args.get(key);
        return val instanceof String s ? s : defaultValue;
    }
}
