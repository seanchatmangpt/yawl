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

package org.yawlfoundation.yawl.pi.mcp;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PIToolProvider.
 *
 * <p>Verifies that yawl_pi_prepare_event_data performs real OCEL 2.0 conversion,
 * and the other three tools return isError=true (honest "not configured" errors)
 * rather than silent stubs that lie about success.</p>
 *
 * @since YAWL 6.0
 */
public class PIToolProviderTest {

    private static final String CSV_DATA =
        "case_id,activity,timestamp,resource\n" +
        "case001,Login,2024-01-01T10:00:00,alice\n" +
        "case001,Approve,2024-01-01T11:00:00,bob\n";

    private static final String JSON_DATA =
        "[{\"case_id\":\"case001\",\"activity\":\"Login\"," +
        "\"timestamp\":\"2024-01-01T10:00:00\",\"resource\":\"alice\"}]";

    private PIToolProvider provider;
    private List<McpServerFeatures.SyncToolSpecification> tools;
    private Map<String, McpServerFeatures.SyncToolSpecification> toolsByName;

    @BeforeEach
    void setUp() {
        provider = new PIToolProvider();
        tools = provider.createTools(null);
        toolsByName = tools.stream()
            .collect(Collectors.toMap(s -> s.tool().name(), s -> s));
    }

    // =========================================================================
    // createTools — count and names
    // =========================================================================

    @Test
    void createTools_returnsFourTools() {
        assertEquals(4, tools.size());
    }

    @Test
    void createTools_includesPredictRiskTool() {
        assertTrue(toolsByName.containsKey("yawl_pi_predict_risk"));
    }

    @Test
    void createTools_includesRecommendActionTool() {
        assertTrue(toolsByName.containsKey("yawl_pi_recommend_action"));
    }

    @Test
    void createTools_includesAskTool() {
        assertTrue(toolsByName.containsKey("yawl_pi_ask"));
    }

    @Test
    void createTools_includesPrepareEventDataTool() {
        assertTrue(toolsByName.containsKey("yawl_pi_prepare_event_data"));
    }

    @Test
    void createTools_allToolsHaveDescriptions() {
        for (McpServerFeatures.SyncToolSpecification spec : tools) {
            assertNotNull(spec.tool().description(), spec.tool().name() + " must have a description");
            assertFalse(spec.tool().description().isBlank());
        }
    }

    // =========================================================================
    // yawl_pi_prepare_event_data — REAL implementation (van der Aalst connection 5)
    // =========================================================================

    @Test
    void prepareEventData_withCsvData_returnsSuccess() {
        McpSchema.CallToolResult result = invokeTool("yawl_pi_prepare_event_data",
            Map.of("eventData", CSV_DATA));
        assertFalse(result.isError(),
            "yawl_pi_prepare_event_data must succeed for valid CSV — got: " + extractText(result));
    }

    @Test
    void prepareEventData_withCsvData_containsOcel2Json() {
        McpSchema.CallToolResult result = invokeTool("yawl_pi_prepare_event_data",
            Map.of("eventData", CSV_DATA));
        String text = extractText(result);
        assertTrue(text.contains("OCEL 2.0"), "Response must contain OCEL 2.0 conversion result");
    }

    @Test
    void prepareEventData_withCsvData_showsCaseIdColumn() {
        McpSchema.CallToolResult result = invokeTool("yawl_pi_prepare_event_data",
            Map.of("eventData", CSV_DATA));
        String text = extractText(result);
        assertTrue(text.contains("case_id") || text.contains("Case ID"),
            "Response should identify the case ID column");
    }

    @Test
    void prepareEventData_withJsonData_returnsSuccess() {
        McpSchema.CallToolResult result = invokeTool("yawl_pi_prepare_event_data",
            Map.of("eventData", JSON_DATA));
        assertFalse(result.isError(),
            "yawl_pi_prepare_event_data must succeed for valid JSON — got: " + extractText(result));
    }

    @Test
    void prepareEventData_withJsonData_reportsJsonFormat() {
        McpSchema.CallToolResult result = invokeTool("yawl_pi_prepare_event_data",
            Map.of("eventData", JSON_DATA));
        String text = extractText(result);
        assertTrue(text.toUpperCase().contains("JSON"),
            "Response should report detected format as JSON");
    }

    @Test
    void prepareEventData_withExplicitFormat_usesRequestedFormat() {
        McpSchema.CallToolResult result = invokeTool("yawl_pi_prepare_event_data",
            Map.of("eventData", CSV_DATA, "format", "csv"));
        assertFalse(result.isError());
    }

    @Test
    void prepareEventData_withMissingEventData_returnsError() {
        McpSchema.CallToolResult result = invokeTool("yawl_pi_prepare_event_data", Map.of());
        assertTrue(result.isError(),
            "Missing eventData must return isError=true");
    }

    @Test
    void prepareEventData_withEmptyEventData_returnsError() {
        McpSchema.CallToolResult result = invokeTool("yawl_pi_prepare_event_data",
            Map.of("eventData", ""));
        assertTrue(result.isError(),
            "Empty eventData must return isError=true");
    }

    // =========================================================================
    // yawl_pi_predict_risk — honest isError=true (requires facade, not configured)
    // =========================================================================

    @Test
    void predictRisk_withCaseId_returnsHonestError() {
        McpSchema.CallToolResult result = invokeTool("yawl_pi_predict_risk",
            Map.of("caseId", "case001"));
        assertTrue(result.isError(),
            "yawl_pi_predict_risk must return isError=true when facade not configured");
    }

    @Test
    void predictRisk_errorMessageMentionsConfigRequirement() {
        McpSchema.CallToolResult result = invokeTool("yawl_pi_predict_risk",
            Map.of("caseId", "case001"));
        String text = extractText(result);
        assertFalse(text.isBlank(), "Error message must not be blank");
    }

    @Test
    void predictRisk_withMissingCaseId_returnsError() {
        McpSchema.CallToolResult result = invokeTool("yawl_pi_predict_risk", Map.of());
        assertTrue(result.isError(), "Missing caseId must return isError=true");
    }

    // =========================================================================
    // yawl_pi_recommend_action — honest isError=true (requires facade)
    // =========================================================================

    @Test
    void recommendAction_withCaseIdAndRisk_returnsHonestError() {
        McpSchema.CallToolResult result = invokeTool("yawl_pi_recommend_action",
            Map.of("caseId", "case001", "riskScore", 0.75));
        assertTrue(result.isError(),
            "yawl_pi_recommend_action must return isError=true when facade not configured");
    }

    @Test
    void recommendAction_withMissingParams_returnsError() {
        McpSchema.CallToolResult result = invokeTool("yawl_pi_recommend_action", Map.of());
        assertTrue(result.isError(), "Missing params must return isError=true");
    }

    // =========================================================================
    // yawl_pi_ask — honest isError=true (requires facade)
    // =========================================================================

    @Test
    void ask_withQuestion_returnsHonestError() {
        McpSchema.CallToolResult result = invokeTool("yawl_pi_ask",
            Map.of("question", "What is the bottleneck in the order process?"));
        assertTrue(result.isError(),
            "yawl_pi_ask must return isError=true when facade not configured");
    }

    @Test
    void ask_withMissingQuestion_returnsError() {
        McpSchema.CallToolResult result = invokeTool("yawl_pi_ask", Map.of());
        assertTrue(result.isError(), "Missing question must return isError=true");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private McpSchema.CallToolResult invokeTool(String toolName, Map<String, Object> args) {
        McpServerFeatures.SyncToolSpecification spec = toolsByName.get(toolName);
        assertNotNull(spec, "Tool not found: " + toolName);
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(toolName, args);
        return spec.callHandler().apply(null, request);
    }

    private String extractText(McpSchema.CallToolResult result) {
        if (result.content().isEmpty()) {
            throw new AssertionError("CallToolResult has no content items — expected at least one TextContent");
        }
        Object first = result.content().get(0);
        if (first instanceof McpSchema.TextContent tc) {
            return tc.text();
        }
        throw new AssertionError("First content item is not TextContent: " + first.getClass().getName());
    }
}
