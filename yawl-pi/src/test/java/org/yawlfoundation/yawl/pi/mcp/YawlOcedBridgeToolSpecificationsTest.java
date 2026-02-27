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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for YawlOcedBridgeToolSpecifications.
 *
 * <p>Verifies that both MCP tools (yawl_convert_to_ocel, yawl_infer_oced_schema)
 * are correctly specified and that their handlers process real CSV, JSON, and XML
 * event data into OCEL 2.0 format using heuristic schema inference (no LLM).</p>
 *
 * @since YAWL 6.0
 */
public class YawlOcedBridgeToolSpecificationsTest {

    private static final String CSV_DATA =
        "case_id,activity,timestamp,resource\n" +
        "case001,Login,2024-01-01T10:00:00,alice\n" +
        "case001,Approve,2024-01-01T11:00:00,bob\n" +
        "case002,Login,2024-01-01T12:00:00,charlie\n";

    private static final String JSON_DATA =
        "[{\"case_id\":\"case001\",\"activity\":\"Login\"," +
        "\"timestamp\":\"2024-01-01T10:00:00\",\"resource\":\"alice\"}," +
        "{\"case_id\":\"case001\",\"activity\":\"Approve\"," +
        "\"timestamp\":\"2024-01-01T11:00:00\",\"resource\":\"bob\"}]";

    private static final String XML_DATA =
        "<events>\n" +
        "  <event>\n" +
        "    <caseId>case001</caseId>\n" +
        "    <activity>Login</activity>\n" +
        "    <timestamp>2024-01-01T10:00:00</timestamp>\n" +
        "  </event>\n" +
        "  <event>\n" +
        "    <caseId>case001</caseId>\n" +
        "    <activity>Approve</activity>\n" +
        "    <timestamp>2024-01-01T11:00:00</timestamp>\n" +
        "  </event>\n" +
        "</events>";

    private YawlOcedBridgeToolSpecifications specs;
    private List<McpServerFeatures.SyncToolSpecification> tools;

    @BeforeEach
    void setUp() {
        specs = new YawlOcedBridgeToolSpecifications();
        tools = specs.createAll();
    }

    // =========================================================================
    // createAll() — tool count and names
    // =========================================================================

    @Test
    void createAll_returnsExactlyTwoTools() {
        assertEquals(2, tools.size());
    }

    @Test
    void createAll_firstToolIsConvertToOcel() {
        assertEquals("yawl_convert_to_ocel", tools.get(0).tool().name());
    }

    @Test
    void createAll_secondToolIsInferOcedSchema() {
        assertEquals("yawl_infer_oced_schema", tools.get(1).tool().name());
    }

    @Test
    void createAll_toolDescriptionsAreNonEmpty() {
        for (McpServerFeatures.SyncToolSpecification spec : tools) {
            assertNotNull(spec.tool().description());
            assertFalse(spec.tool().description().isBlank(),
                "Tool " + spec.tool().name() + " must have a description");
        }
    }

    // =========================================================================
    // yawl_convert_to_ocel — CSV input
    // =========================================================================

    @Test
    void convertToOcel_withCsvData_returnsSuccessResult() {
        McpSchema.CallToolResult result = invokeConvert(Map.of("eventData", CSV_DATA));
        assertFalse(result.isError(), "Expected isError=false for valid CSV input");
    }

    @Test
    void convertToOcel_withCsvData_containsOcelJson() {
        McpSchema.CallToolResult result = invokeConvert(Map.of("eventData", CSV_DATA));
        String text = extractText(result);
        assertTrue(text.contains("OCEL 2.0"), "Response should contain OCEL 2.0 header");
    }

    @Test
    void convertToOcel_withCsvData_detectsFormatAsCsv() {
        McpSchema.CallToolResult result = invokeConvert(Map.of("eventData", CSV_DATA));
        String text = extractText(result);
        assertTrue(text.toUpperCase().contains("CSV"),
            "Response should report detected format as CSV");
    }

    // =========================================================================
    // yawl_convert_to_ocel — JSON input
    // =========================================================================

    @Test
    void convertToOcel_withJsonData_returnsSuccessResult() {
        McpSchema.CallToolResult result = invokeConvert(Map.of("eventData", JSON_DATA));
        assertFalse(result.isError(), "Expected isError=false for valid JSON input");
    }

    @Test
    void convertToOcel_withJsonData_containsOcelJson() {
        McpSchema.CallToolResult result = invokeConvert(Map.of("eventData", JSON_DATA));
        String text = extractText(result);
        assertTrue(text.contains("OCEL 2.0"), "Response should contain OCEL 2.0 header");
    }

    @Test
    void convertToOcel_withJsonData_detectsFormatAsJson() {
        McpSchema.CallToolResult result = invokeConvert(Map.of("eventData", JSON_DATA));
        String text = extractText(result);
        assertTrue(text.toUpperCase().contains("JSON"),
            "Response should report detected format as JSON");
    }

    // =========================================================================
    // yawl_convert_to_ocel — XML input
    // =========================================================================

    @Test
    void convertToOcel_withXmlData_returnsSuccessResult() {
        McpSchema.CallToolResult result = invokeConvert(Map.of("eventData", XML_DATA));
        assertFalse(result.isError(), "Expected isError=false for valid XML input");
    }

    @Test
    void convertToOcel_withXmlData_detectsFormatAsXml() {
        McpSchema.CallToolResult result = invokeConvert(Map.of("eventData", XML_DATA));
        String text = extractText(result);
        assertTrue(text.toUpperCase().contains("XML"),
            "Response should report detected format as XML");
    }

    // =========================================================================
    // yawl_convert_to_ocel — explicit format parameter
    // =========================================================================

    @Test
    void convertToOcel_withExplicitCsvFormat_usesRequestedFormat() {
        McpSchema.CallToolResult result = invokeConvert(
            Map.of("eventData", CSV_DATA, "format", "csv"));
        assertFalse(result.isError());
        assertTrue(extractText(result).toUpperCase().contains("CSV"));
    }

    // =========================================================================
    // yawl_convert_to_ocel — error cases
    // =========================================================================

    @Test
    void convertToOcel_withMissingEventData_returnsError() {
        McpSchema.CallToolResult result = invokeConvert(Map.of());
        assertTrue(result.isError(), "Missing eventData must return isError=true");
    }

    @Test
    void convertToOcel_withBlankEventData_returnsError() {
        McpSchema.CallToolResult result = invokeConvert(Map.of("eventData", "   "));
        assertTrue(result.isError(), "Blank eventData must return isError=true");
    }

    // =========================================================================
    // yawl_infer_oced_schema — CSV sample
    // =========================================================================

    @Test
    void inferSchema_withCsvSample_returnsSuccessResult() {
        McpSchema.CallToolResult result = invokeInferSchema(Map.of("dataSample", CSV_DATA));
        assertFalse(result.isError(), "Expected isError=false for valid CSV sample");
    }

    @Test
    void inferSchema_withCsvSample_returnsSchemaFields() {
        McpSchema.CallToolResult result = invokeInferSchema(Map.of("dataSample", CSV_DATA));
        String text = extractText(result);
        assertTrue(text.contains("caseIdColumn") || text.contains("Case ID"),
            "Response should contain case ID column info");
    }

    @Test
    void inferSchema_withJsonSample_returnsSuccessResult() {
        McpSchema.CallToolResult result = invokeInferSchema(Map.of("dataSample", JSON_DATA));
        assertFalse(result.isError(), "Expected isError=false for valid JSON sample");
    }

    @Test
    void inferSchema_withMissingDataSample_returnsError() {
        McpSchema.CallToolResult result = invokeInferSchema(Map.of());
        assertTrue(result.isError(), "Missing dataSample must return isError=true");
    }

    @Test
    void inferSchema_withBlankDataSample_returnsError() {
        McpSchema.CallToolResult result = invokeInferSchema(Map.of("dataSample", ""));
        assertTrue(result.isError(), "Blank dataSample must return isError=true");
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private McpSchema.CallToolResult invokeConvert(Map<String, Object> args) {
        McpServerFeatures.SyncToolSpecification spec = tools.get(0);
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
            "yawl_convert_to_ocel", args);
        return spec.callHandler().apply(null, request);
    }

    private McpSchema.CallToolResult invokeInferSchema(Map<String, Object> args) {
        McpServerFeatures.SyncToolSpecification spec = tools.get(1);
        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
            "yawl_infer_oced_schema", args);
        return spec.callHandler().apply(null, request);
    }

    private String extractText(McpSchema.CallToolResult result) {
        assertFalse(result.content().isEmpty(), "Result must have at least one content item");
        Object first = result.content().get(0);
        assertInstanceOf(McpSchema.TextContent.class, first, "First content must be TextContent");
        return ((McpSchema.TextContent) first).text();
    }
}
