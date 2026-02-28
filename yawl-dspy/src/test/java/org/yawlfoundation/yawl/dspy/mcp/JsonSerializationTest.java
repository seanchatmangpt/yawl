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

package org.yawlfoundation.yawl.dspy.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for JSON serialization in GEPA MCP tools.
 *
 * <p>Ensures all GEPA tools produce valid JSON output in all scenarios.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
final class JsonSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonNodeFactory nodeFactory = JsonNodeFactory.withExactBigDecimals(true);

    @Test
    @DisplayName("All MCP tools should produce valid JSON")
    void allMcpToolsShouldProduceValidJson() {
        // Arrange
        List<McpServerFeatures.SyncToolSpecification> tools = GepaMcpTools.createAll();

        // Test each tool with minimal valid input
        for (McpServerFeatures.SyncToolSpecification tool : tools) {
            String toolName = tool.tool().name();

            // Create valid request based on tool requirements
            Map<String, Object> requestArgs = switch (toolName) {
                case "gepa_optimize_workflow" -> Map.of(
                        "workflow_spec", Map.of("test", "workflow"),
                        "optimization_target", "performance"
                );
                case "gepa_validate_footprint" -> Map.of(
                        "original_workflow", Map.of("test", "original"),
                        "optimized_workflow", Map.of("test", "optimized")
                );
                case "gepa_score_workflow" -> Map.of(
                        "workflow", Map.of("test", "workflow"),
                        "reference_patterns", List.of("industry_best")
                );
                default -> throw new AssertionError("Unknown tool: " + toolName);
            };

            // Act
            McpSchema.CallToolResult result = tool.call().apply(null,
                    new McpSchema.CallToolRequest(toolName, requestArgs));

            // Assert - Result should always be valid JSON
            assertFalse(result.isError(),
                toolName + " should not produce error for valid input");

            String json = ((McpSchema.TextContent) result.content().get(0)).text();
            assertDoesNotThrow(() -> mapper.readTree(json),
                toolName + " should produce valid JSON response");
        }
    }

    @Test
    @DisplayName("Error responses should be valid JSON")
    void errorResponsesShouldBeValidJson() throws Exception {
        // Arrange
        List<McpServerFeatures.SyncToolSpecification> tools = GepaMcpTools.createAll();

        // Test each tool with invalid input that causes errors
        for (McpServerFeatures.SyncToolSpecification tool : tools) {
            String toolName = tool.tool().name();

            // Create invalid request that should trigger error
            Map<String, Object> requestArgs = switch (toolName) {
                case "gepa_optimize_workflow" -> Map.of(); // Missing required parameters
                case "gepa_validate_footprint" -> Map.of(); // Missing required parameters
                case "gepa_score_workflow" -> Map.of(); // Missing required parameters
                default -> throw new AssertionError("Unknown tool: " + toolName);
            };

            // Act
            McpSchema.CallToolResult result = tool.call().apply(null,
                    new McpSchema.CallToolRequest(toolName, requestArgs));

            // Assert - Even errors should be valid JSON
            assertTrue(result.isError(),
                toolName + " should produce error for invalid input");

            String json = ((McpSchema.TextContent) result.content().get(0)).text();
            assertDoesNotThrow(() -> mapper.readTree(json),
                toolName + " should produce valid JSON error response");

            // Verify error structure
            var errorNode = mapper.readTree(json);
            assertTrue(errorNode.has("error"), "Error response should have 'error' field");
            assertTrue(errorNode.get("error").asBoolean(), "Error field should be true");
            assertTrue(errorNode.has("message"), "Error response should have 'message' field");
            assertFalse(errorNode.get("message").asText().isEmpty(), "Message should not be empty");
        }
    }

    @Test
    @DisplayName("Tool schemas should be valid JSON")
    void toolSchemasShouldBeValidJson() throws Exception {
        // Arrange
        List<McpServerFeatures.SyncToolSpecification> tools = GepaMcpTools.createAll();

        // Test each tool's input schema
        for (McpServerFeatures.SyncToolSpecification tool : tools) {
            String toolName = tool.tool().name();
            String inputSchema = tool.tool().inputSchema();

            // Assert schema should be valid JSON
            assertDoesNotThrow(() -> mapper.readTree(inputSchema),
                toolName + " input schema should be valid JSON");

            // Verify schema structure
            var schema = mapper.readTree(inputSchema);
            assertTrue(schema.has("type"), "Schema should have 'type' field");
            assertEquals("object", schema.get("type").asText(), "Schema type should be 'object'");
            assertTrue(schema.has("properties"), "Schema should have 'properties' field");
            assertTrue(schema.has("required"), "Schema should have 'required' field");
        }
    }

    @Test
    @DisplayName("Response structure should be consistent")
    void responseStructureShouldBeConsistent() throws Exception {
        // Arrange
        List<McpServerFeatures.SyncToolSpecification> tools = GepaMcpTools.createAll();

        // Create valid request for each tool
        for (McpServerFeatures.SyncToolSpecification tool : tools) {
            String toolName = tool.tool().name();

            Map<String, Object> requestArgs = switch (toolName) {
                case "gepa_optimize_workflow" -> Map.of(
                        "workflow_spec", createSampleWorkflow(),
                        "optimization_target", "performance",
                        "reference_patterns", List.of("sequential", "parallel")
                );
                case "gepa_validate_footprint" -> Map.of(
                        "original_workflow", createSampleWorkflow(),
                        "optimized_workflow", createOptimizedWorkflow(),
                        "validation_mode", "balanced"
                );
                case "gepa_score_workflow" -> Map.of(
                        "workflow", createSampleWorkflow(),
                        "reference_patterns", List.of("industry_best", "compliance_standard")
                );
                default -> throw new AssertionError("Unknown tool: " + toolName);
            };

            // Act
            McpSchema.CallToolResult result = tool.call().apply(null,
                    new McpSchema.CallToolRequest(toolName, requestArgs));

            // Assert
            assertFalse(result.isError());
            String json = ((McpSchema.TextContent) result.content().get(0)).text();
            var response = mapper.readTree(json);

            // Common fields in successful responses
            assertTrue(response.has("_metadata"), "Response should have _metadata field");
            var metadata = response.get("_metadata");
            assertTrue(metadata.has("timestamp"), "Metadata should have timestamp");

            // Tool-specific fields
            switch (toolName) {
                case "gepa_optimize_workflow":
                    assertTrue(response.has("optimized_workflow"),
                        "Optimize workflow response should have optimized_workflow");
                    assertTrue(response.has("optimization_metrics"),
                        "Optimize workflow response should have optimization_metrics");
                    break;
                case "gepa_validate_footprint":
                    assertTrue(response.has("validation_passed"),
                        "Validate footprint response should have validation_passed");
                    assertTrue(response.has("footprint_compatibility"),
                        "Validate footprint response should have footprint_compatibility");
                    break;
                case "gepa_score_workflow":
                    assertTrue(response.has("total_score"),
                        "Score workflow response should have total_score");
                    assertTrue(response.has("pattern_scores"),
                        "Score workflow response should have pattern_scores");
                    break;
            }
        }
    }

    @Test
    @DisplayName("Metadata should contain required fields")
    void metadataShouldContainRequiredFields() throws Exception {
        // Arrange
        List<McpServerFeatures.SyncToolSpecification> tools = GepaMcpTools.createAll();

        // Test with valid request for optimization tool
        McpServerFeatures.SyncToolSpecification tool = tools.stream()
                .filter(t -> "gepa_optimize_workflow".equals(t.tool().name()))
                .findFirst()
                .orElseThrow();

        Map<String, Object> requestArgs = Map.of(
                "workflow_spec", createSampleWorkflow(),
                "optimization_target", "performance"
        );

        // Act
        McpSchema.CallToolResult result = tool.call().apply(null,
                new McpSchema.CallToolRequest("gepa_optimize_workflow", requestArgs));

        // Assert
        assertFalse(result.isError());
        String json = ((McpSchema.TextContent) result.content().get(0)).text();
        var response = mapper.readTree(json);

        var metadata = response.get("_metadata");
        assertTrue(metadata.has("timestamp"), "Metadata should have timestamp");
        assertTrue(metadata.has("optimizer"), "Metadata should have optimizer");
        assertEquals("GEPA-6.0.0", metadata.get("optimizer").asText(),
            "Optimizer should be GEPA-6.0.0");
    }

    @Test
    @DisplayName("Numbers should be serialized correctly")
    void numbersShouldBeSerializedCorrectly() throws Exception {
        // Arrange
        List<McpServerFeatures.SyncToolSpecification> tools = GepaMcpTools.createAll();

        // Test scoring tool with specific test data
        McpServerFeatures.SyncToolSpecification tool = tools.stream()
                .filter(t -> "gepa_score_workflow".equals(t.tool().name()))
                .findFirst()
                .orElseThrow();

        Map<String, Object> requestArgs = Map.of(
                "workflow", createSampleWorkflow(),
                "reference_patterns", List.of("industry_best", "compliance_standard"),
                "scoring_weights", Map.of(
                        "performance", 0.5,
                        "maintainability", 0.3,
                        "compliance", 0.2
                ),
                "include_detailed_analysis", true
        );

        // Act
        McpSchema.CallToolResult result = tool.call().apply(null,
                new McpSchema.CallToolRequest("gepa_score_workflow", requestArgs));

        // Assert
        assertFalse(result.isError());
        String json = ((McpSchema.TextContent) result.content().get(0)).text();
        var response = mapper.readTree(json);

        // Verify numeric fields
        assertTrue(response.has("total_score"));
        double totalScore = response.get("total_score").asDouble();
        assertTrue(totalScore >= 0.0 && totalScore <= 1.0,
            "Total score should be between 0 and 1");

        var patternScores = response.get("pattern_scores");
        assertTrue(patternScores.isObject());
        for (var field : patternScores.fields()) {
            double score = field.getValue().asDouble();
            assertTrue(score >= 0.0 && score <= 1.0,
                PatternScore should be between 0 and 1");
        }

        var dimensionScores = response.get("dimension_scores");
        assertTrue(dimensionScores.isObject());
        for (var field : dimensionScores.fields()) {
            double score = field.getValue().asDouble();
            assertTrue(score >= 0.0 && score <= 1.0,
                "Dimension score should be between 0 and 1");
        }
    }

    @Test
    @DisplayName("Arrays should be serialized correctly")
    void arraysShouldBeSerializedCorrectly() throws Exception {
        // Arrange
        List<McpServerFeatures.SyncToolSpecification> tools = GepaMcpTools.createAll();

        // Test optimization tool
        McpServerFeatures.SyncToolSpecification tool = tools.stream()
                .filter(t -> "gepa_optimize_workflow".equals(t.tool().name()))
                .findFirst()
                .orElseThrow();

        Map<String, Object> requestArgs = Map.of(
                "workflow_spec", createSampleWorkflow(),
                "optimization_target", "performance",
                "reference_patterns", List.of("sequential", "parallel", "choice", "loop")
        );

        // Act
        McpSchema.CallToolResult result = tool.call().apply(null,
                new McpSchema.CallToolRequest("gepa_optimize_workflow", requestArgs));

        // Assert
        assertFalse(result.isError());
        String json = ((McpSchema.TextContent) result.content().get(0)).text();
        var response = mapper.readTree(json);

        // Verify array fields
        assertTrue(response.has("applied_transformations"));
        var transformations = response.get("applied_transformations");
        assertTrue(transformations.isArray());
        assertFalse(transformations.isEmpty());

        assertTrue(response.has("execution_path"));
        var executionPath = response.get("execution_path");
        assertTrue(executionPath.isArray());
        assertFalse(executionPath.isEmpty());
    }

    // Helper methods

    private Map<String, Object> createSampleWorkflow() {
        return Map.of(
                "name", "sample_workflow",
                "net", Map.of("id", "net_1", "type", "workflow_net"),
                "input_params", List.of("param1", "param2"),
                "output_params", List.of("result1", "result2"),
                "variables", List.of(
                        Map.of("name", "var1", "type", "string"),
                        Map.of("name", "var2", "type", "integer")
                )
        );
    }

    private Map<String, Object> createOptimizedWorkflow() {
        return Map.of(
                "name", "optimized_workflow",
                "net", Map.of("id", "net_optimized", "type", "workflow_net"),
                "input_params", List.of("param1", "param2"),
                "output_params", List.of("result1", "result2"),
                "optimization", Map.of(
                        "target", "performance",
                        "applied", true,
                        "version", "GEPA-6.0.0"
                )
        );
    }
}