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

package org.yawlfoundation.yawl.integration.a2a.skills;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.integration.adaptation.AdaptationResult;
import org.yawlfoundation.yawl.integration.adaptation.EventDrivenAdaptationEngine;
import org.yawlfoundation.yawl.integration.adaptation.EventSeverity;
import org.yawlfoundation.yawl.integration.adaptation.ProcessEvent;
import org.yawlfoundation.yawl.integration.mcp.spec.YawlAdaptationToolSpecifications;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A2A skill that evaluates process events against the YAWL adaptation ruleset.
 * No LLM required — pure deterministic rule engine.
 *
 * <h2>Request parameters</h2>
 * <ul>
 *   <li>{@code eventType} — required, the type of event (e.g. DEADLINE_EXCEEDED)</li>
 *   <li>{@code severity} — optional, LOW/MEDIUM/HIGH/CRITICAL (default: MEDIUM)</li>
 *   <li>{@code payload} — optional, key=value pairs, e.g. "risk_score=0.95"</li>
 * </ul>
 *
 * <h2>Result data keys</h2>
 * <ul>
 *   <li>{@code adapted} — boolean, whether a rule matched</li>
 *   <li>{@code action} — the matched AdaptationAction name (or "NO_MATCH")</li>
 *   <li>{@code actionDescription} — human-readable description of the action</li>
 *   <li>{@code matchedRuleCount} — number of rules that matched</li>
 *   <li>{@code explanation} — textual explanation of the result</li>
 *   <li>{@code elapsed_ms} — processing time in milliseconds</li>
 * </ul>
 *
 * @since YAWL 6.0
 */
public class AdaptationSkill implements A2ASkill {

    private static final Logger _log = LoggerFactory.getLogger(AdaptationSkill.class);
    private final EventDrivenAdaptationEngine engine;

    public AdaptationSkill() {
        this.engine = new EventDrivenAdaptationEngine(
            YawlAdaptationToolSpecifications.DEFAULT_RULES);
    }

    @Override
    public String getId() {
        return "adapt_to_event";
    }

    @Override
    public String getName() {
        return "Event-Driven Process Adaptation";
    }

    @Override
    public String getDescription() {
        return "Evaluates a process event against the YAWL adaptation ruleset and returns the "
            + "matching action (ESCALATE_TO_MANUAL, PAUSE_CASE, REJECT_IMMEDIATELY, etc.). "
            + "No LLM required — pure deterministic rule matching.";
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of("workflow:adapt");
    }

    @Override
    public List<String> getTags() {
        return List.of("adaptation", "events", "no-llm", "rules", "deterministic");
    }

    @Override
    public SkillResult execute(SkillRequest request) {
        String eventType = request.getParameter("eventType");
        if (eventType == null || eventType.isBlank()) {
            return SkillResult.error(
                "Parameter 'eventType' is required. "
                + "Valid types include: DEADLINE_EXCEEDED, RESOURCE_UNAVAILABLE, SLA_BREACH, "
                + "FRAUD_ALERT, ERROR, PRIORITY_INCREASE");
        }

        EventSeverity severity = parseSeverity(request.getParameter("severity"));
        Map<String, Object> payload = parsePayload(request.getParameter("payload"));

        long start = System.currentTimeMillis();

        ProcessEvent event = new ProcessEvent(
            UUID.randomUUID().toString(),
            eventType.trim().toUpperCase(),
            "a2a-client",
            Instant.now(),
            payload,
            severity
        );

        AdaptationResult result = engine.process(event);
        long elapsed = System.currentTimeMillis() - start;

        _log.info("AdaptationSkill: eventType={}, adapted={}, action={}, elapsed={}ms",
            event.eventType(), result.adapted(),
            result.adapted() ? result.executedAction() : "NO_MATCH", elapsed);

        Map<String, Object> data = new HashMap<>();
        data.put("adapted", result.adapted());
        data.put("action", result.adapted() ? result.executedAction().name() : "NO_MATCH");
        data.put("actionDescription", result.adapted()
            ? result.executedAction().description()
            : "No adaptation rules matched this event");
        data.put("matchedRuleCount", result.matchedRules().size());
        data.put("explanation", result.explanation());
        data.put("eventType", event.eventType());
        data.put("severity", event.severity().name());
        data.put("elapsed_ms", elapsed);
        return SkillResult.success(data, elapsed);
    }

    private EventSeverity parseSeverity(String severityStr) {
        if (severityStr == null || severityStr.isBlank()) return EventSeverity.MEDIUM;
        try {
            return EventSeverity.valueOf(severityStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return EventSeverity.MEDIUM;
        }
    }

    private Map<String, Object> parsePayload(String payloadStr) {
        if (payloadStr == null || payloadStr.isBlank()) return Map.of();
        Map<String, Object> payload = new HashMap<>();
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
        return Map.copyOf(payload);
    }
}
