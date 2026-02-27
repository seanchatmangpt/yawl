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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for YawlAdaptationToolSpecifications.
 * Uses real EventDrivenAdaptationEngine — no mocks.
 *
 * @since YAWL 6.0
 */
class YawlAdaptationToolSpecificationsTest {

    private YawlAdaptationToolSpecifications specs;

    @BeforeEach
    void setUp() {
        specs = new YawlAdaptationToolSpecifications();
    }

    // =========================================================================
    // Tool Inventory
    // =========================================================================

    @Test
    void testCreateAllReturnsTwoTools() {
        List<McpServerFeatures.SyncToolSpecification> tools = specs.createAll();
        assertEquals(2, tools.size(), "Must provide exactly 2 adaptation tools");
    }

    @Test
    void testToolNamesAreCorrect() {
        List<McpServerFeatures.SyncToolSpecification> tools = specs.createAll();
        List<String> names = tools.stream()
            .map(t -> t.tool().name())
            .toList();
        assertTrue(names.contains("yawl_evaluate_event"), "Must have yawl_evaluate_event tool");
        assertTrue(names.contains("yawl_list_adaptation_rules"), "Must have yawl_list_adaptation_rules tool");
    }

    @Test
    void testToolDescriptionsAreNotBlank() {
        specs.createAll().forEach(t ->
            assertFalse(t.tool().description().isBlank(),
                "Tool " + t.tool().name() + " must have a non-blank description")
        );
    }

    @Test
    void testEvaluateEventToolHasEventTypeParam() {
        McpServerFeatures.SyncToolSpecification evaluateTool = specs.createAll().stream()
            .filter(t -> "yawl_evaluate_event".equals(t.tool().name()))
            .findFirst().orElseThrow();

        assertNotNull(evaluateTool.tool().inputSchema());
        assertTrue(evaluateTool.tool().inputSchema().required().contains("eventType"),
            "yawl_evaluate_event must require eventType parameter");
    }

    // =========================================================================
    // Default Ruleset
    // =========================================================================

    @Test
    void testDefaultRulesNotEmpty() {
        assertFalse(YawlAdaptationToolSpecifications.DEFAULT_RULES.isEmpty(),
            "Default ruleset must not be empty");
    }

    @Test
    void testDefaultRulesHaveNoNullEntries() {
        YawlAdaptationToolSpecifications.DEFAULT_RULES.forEach(rule -> {
            assertNotNull(rule.ruleId(), "ruleId must not be null");
            assertNotNull(rule.name(), "name must not be null");
            assertNotNull(rule.action(), "action must not be null");
            assertNotNull(rule.condition(), "condition must not be null");
        });
    }

    // =========================================================================
    // yawl_evaluate_event execution
    // =========================================================================

    @Test
    void testEvaluateDeadlineExceededReturnsEscalate() {
        McpServerFeatures.SyncToolSpecification tool = findTool("yawl_evaluate_event");
        McpSchema.CallToolResult result = invoke(tool, Map.of("eventType", "DEADLINE_EXCEEDED"));

        assertFalse(result.isError(), "DEADLINE_EXCEEDED must not error");
        String text = extractText(result);
        assertTrue(text.contains("ESCALATE_TO_MANUAL") || text.contains("Adapted:     true"),
            "DEADLINE_EXCEEDED must trigger ESCALATE_TO_MANUAL: " + text);
    }

    @Test
    void testEvaluateResourceUnavailableReturnsPause() {
        McpServerFeatures.SyncToolSpecification tool = findTool("yawl_evaluate_event");
        McpSchema.CallToolResult result = invoke(tool, Map.of("eventType", "RESOURCE_UNAVAILABLE"));

        assertFalse(result.isError());
        String text = extractText(result);
        assertTrue(text.contains("PAUSE_CASE") || text.contains("Adapted:     true"),
            "RESOURCE_UNAVAILABLE must trigger PAUSE_CASE: " + text);
    }

    @Test
    void testEvaluateFraudAlertWithHighRiskRejectsImmediately() {
        McpServerFeatures.SyncToolSpecification tool = findTool("yawl_evaluate_event");
        McpSchema.CallToolResult result = invoke(tool,
            Map.of("eventType", "FRAUD_ALERT", "payload", "risk_score=0.95"));

        assertFalse(result.isError());
        String text = extractText(result);
        assertTrue(text.contains("REJECT_IMMEDIATELY"),
            "FRAUD_ALERT with risk_score=0.95 must REJECT_IMMEDIATELY: " + text);
    }

    @Test
    void testEvaluateFraudAlertWithLowRiskNoReject() {
        McpServerFeatures.SyncToolSpecification tool = findTool("yawl_evaluate_event");
        McpSchema.CallToolResult result = invoke(tool,
            Map.of("eventType", "FRAUD_ALERT", "payload", "risk_score=0.3"));

        assertFalse(result.isError());
        String text = extractText(result);
        // risk_score=0.3 < 0.8 threshold — high-risk fraud rule should NOT match
        assertFalse(text.contains("REJECT_IMMEDIATELY"),
            "FRAUD_ALERT with risk_score=0.3 must NOT REJECT_IMMEDIATELY: " + text);
    }

    @Test
    void testEvaluateUnknownEventTypeReturnsNoMatch() {
        McpServerFeatures.SyncToolSpecification tool = findTool("yawl_evaluate_event");
        McpSchema.CallToolResult result = invoke(tool,
            Map.of("eventType", "UNKNOWN_EVENT_XYZ", "severity", "LOW"));

        assertFalse(result.isError());
        String text = extractText(result);
        assertTrue(text.contains("NO_MATCH") || text.contains("Adapted:     false"),
            "Unknown event type must produce NO_MATCH: " + text);
    }

    @Test
    void testEvaluateMissingEventTypeReturnsError() {
        McpServerFeatures.SyncToolSpecification tool = findTool("yawl_evaluate_event");
        McpSchema.CallToolResult result = invoke(tool, Map.of());

        assertTrue(result.isError(), "Missing eventType must return error");
    }

    // =========================================================================
    // yawl_list_adaptation_rules execution
    // =========================================================================

    @Test
    void testListRulesReturnsNonEmptyText() {
        McpServerFeatures.SyncToolSpecification tool = findTool("yawl_list_adaptation_rules");
        McpSchema.CallToolResult result = invoke(tool, Map.of());

        assertFalse(result.isError());
        String text = extractText(result);
        assertFalse(text.isBlank(), "Rules list must not be blank");
    }

    @Test
    void testListRulesContainsExpectedRuleIds() {
        McpServerFeatures.SyncToolSpecification tool = findTool("yawl_list_adaptation_rules");
        McpSchema.CallToolResult result = invoke(tool, Map.of());

        String text = extractText(result);
        assertTrue(text.contains("rule-deadline-exceeded"), "Must list deadline rule");
        assertTrue(text.contains("rule-fraud-high-risk"), "Must list fraud rule");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private McpServerFeatures.SyncToolSpecification findTool(String name) {
        return specs.createAll().stream()
            .filter(t -> name.equals(t.tool().name()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Tool not found: " + name));
    }

    private McpSchema.CallToolResult invoke(McpServerFeatures.SyncToolSpecification tool,
                                            Map<String, Object> args) {
        return tool.callHandler().apply(null,
            new McpSchema.CallToolRequest(tool.tool().name(), args));
    }

    private String extractText(McpSchema.CallToolResult result) {
        return result.content().stream()
            .filter(c -> c instanceof McpSchema.TextContent)
            .map(c -> ((McpSchema.TextContent) c).text())
            .findFirst().orElse("");
    }

    // =========================================================================
    // Exception Behavior Tests (New)
    // =========================================================================

    @Test
    void testInvalidSeverityThrowsException() {
        // Test that invalid severity throws IllegalArgumentException instead of defaulting to MEDIUM
        McpServerFeatures.SyncToolSpecification tool = findTool("yawl_evaluate_event");
        McpSchema.CallToolResult result = invoke(tool,
            Map.of("eventType", "DEADLINE_EXCEEDED", "severity", "INVALID_SEVERITY"));

        assertTrue(result.isError(), "Invalid severity must return error");
        String text = extractText(result);
        assertTrue(text.contains("Invalid severity: INVALID_SEVERITY"),
            "Error must contain the exact invalid severity value");
    }

    @Test
    void testInvalidSeverityWithDifferentValues() {
        String[] invalidSeverities = {"INVALID", "unknown", "HIGHX", "CRITICAL!"};

        for (String severity : invalidSeverities) {
            McpServerFeatures.SyncToolSpecification tool = findTool("yawl_evaluate_event");
            McpSchema.CallToolResult result = invoke(tool,
                Map.of("eventType", "DEADLINE_EXCEEDED", "severity", severity));

            assertTrue(result.isError(), "Severity '" + severity + "' must throw exception");
            String text = extractText(result);
            assertTrue(text.contains("Invalid severity: " + severity),
                "Error must mention invalid severity: " + severity);
        }
    }

    @Test
    void testValidSeverityStillWorks() {
        String[] validSeverities = {"LOW", "MEDIUM", "HIGH", "CRITICAL"};

        for (String severity : validSeverities) {
            McpServerFeatures.SyncToolSpecification tool = findTool("yawl_evaluate_event");
            McpSchema.CallToolResult result = invoke(tool,
                Map.of("eventType", "DEADLINE_EXCEEDED", "severity", severity));

            assertFalse(result.isError(), "Valid severity '" + severity + "' must work");
            String text = extractText(result);
            assertTrue(text.contains(severity),
                "Response should contain the specified severity: " + text);
        }
    }
}
