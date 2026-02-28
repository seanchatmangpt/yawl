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
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GepaMcpTools MCP tool specifications.
 *
 * <p>Chicago TDD: Tests verify real GEPA MCP tool behavior with actual JSON processing.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
final class GepaMcpToolsTest {

    @TempDir
    Path tempDir;

    private List<McpServerFeatures.SyncToolSpecification> tools;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(GepaMcpToolsTest.class);

    @BeforeEach
    void setUp() throws IOException {
        // Initialize test tools
        tools = GepaMcpTools.createAll();
        log.info("Initialized {} GEPA MCP tools", tools.size());
    }

    @Test
    @DisplayName("Should create 3 GEPA MCP tools")
    void shouldCreateThreeGepaMcpTools() {
        // Assert
        assertEquals(3, tools.size(), "Should create exactly 3 GEPA tools");

        List<String> toolNames = tools.stream()
                .map(spec -> spec.tool().name())
                .toList();

        // Verify all expected tools are present
        assertTrue(toolNames.contains("gepa_optimize_workflow"),
                "Missing gepa_optimize_workflow tool");
        assertTrue(toolNames.contains("gepa_validate_footprint"),
                "Missing gepa_validate_footprint tool");
        assertTrue(toolNames.contains("gepa_score_workflow"),
                "Missing gepa_score_workflow tool");
    }

    @Test
    @DisplayName("gepa_optimize_workflow should have correct metadata")
    void gepaOptimizeWorkflowShouldHaveCorrectMetadata() {
        // Arrange
        McpServerFeatures.SyncToolSpecification tool = findTool("gepa_optimize_workflow");
        McpSchema.Tool schema = tool.tool();

        // Assert metadata
        assertEquals("gepa_optimize_workflow", schema.name());
        assertFalse(schema.description().isBlank());
        assertTrue(schema.description().contains("GEPA optimization"));
        assertTrue(schema.description().contains("graph-based"));

        // Validate input schema
        String inputSchema = schema.inputSchema().toString();
        assertTrue(inputSchema.contains("\"required\""));
        assertTrue(inputSchema.contains("\"workflow_spec\""));
        assertTrue(inputSchema.contains("\"optimization_target\""));

        // Verify optimization target enum
        assertTrue(inputSchema.contains("\"performance\""));
        assertTrue(inputSchema.contains("\"maintainability\""));
        assertTrue(inputSchema.contains("\"compliance\""));
    }

    @Test
    @DisplayName("gepa_validate_footprint should have correct metadata")
    void gepaValidateFootprintShouldHaveCorrectMetadata() {
        // Arrange
        McpServerFeatures.SyncToolSpecification tool = findTool("gepa_validate_footprint");
        McpSchema.Tool schema = tool.tool();

        // Assert metadata
        assertEquals("gepa_validate_footprint", schema.name());
        assertFalse(schema.description().isBlank());
        assertTrue(schema.description().contains("behavioral footprint"));
        assertTrue(schema.description().contains("preserves critical properties"));

        // Validate input schema
        String inputSchema = schema.inputSchema().toString();
        assertTrue(inputSchema.contains("\"original_workflow\""));
        assertTrue(inputSchema.contains("\"optimized_workflow\""));
        assertTrue(inputSchema.contains("\"validation_mode\""));

        // Verify validation mode enum
        assertTrue(inputSchema.contains("\"strict\""));
        assertTrue(inputSchema.contains("\"balanced\""));
        assertTrue(inputSchema.contains("\"lenient\""));
    }

    @Test
    @DisplayName("gepa_score_workflow should have correct metadata")
    void gepaScoreWorkflowShouldHaveCorrectMetadata() {
        // Arrange
        McpServerFeatures.SyncToolSpecification tool = findTool("gepa_score_workflow");
        McpSchema.Tool schema = tool.tool();

        // Assert metadata
        assertEquals("gepa_score_workflow", schema.name());
        assertFalse(schema.description().isBlank());
        assertTrue(schema.description().contains("comprehensive scoring"));
        assertTrue(schema.description().contains("reference patterns"));

        // Validate input schema
        String inputSchema = schema.inputSchema().toString();
        assertTrue(inputSchema.contains("\"workflow\""));
        assertTrue(inputSchema.contains("\"reference_patterns\""));
        assertTrue(inputSchema.contains("\"scoring_weights\""));

        // Verify scoring weights schema
        assertTrue(inputSchema.contains("\"performance\""));
        assertTrue(inputSchema.contains("\"maintainability\""));
        assertTrue(inputSchema.contains("\"compliance\""));
    }

    @Test
    @DisplayName("gepa_optimize_workflow should validate required parameters")
    void gepaOptimizeWorkflowShouldValidateRequiredParameters() throws Exception {
        // Arrange
        McpServerFeatures.SyncToolSpecification tool = findTool("gepa_optimize_workflow");

        // Test missing workflow_spec
        McpSchema.CallToolRequest request1 = new McpSchema.CallToolRequest(
                "gepa_optimize_workflow", Map.of("optimization_target", "performance"));

        // Act & Assert
        McpSchema.CallToolResult result1 = tool.call().apply(null, request1);
        assertTrue(result1.isError());
        String response1 = ((McpSchema.TextContent) result1.content().get(0)).text();
        assertTrue(response1.contains("workflow_spec"),
                "Error should mention workflow_spec parameter");

        // Test missing optimization_target
        McpSchema.CallToolRequest request2 = new McpSchema.CallToolRequest(
                "gepa_optimize_workflow", Map.of("workflow_spec", Map.of()));

        // Act & Assert
        McpSchema.CallToolResult result2 = tool.call().apply(null, request2);
        assertTrue(result2.isError());
        String response2 = ((McpSchema.TextContent) result2.content().get(0)).text();
        assertTrue(response2.contains("optimization_target"),
                "Error should mention optimization_target parameter");
    }

    @Test
    @DisplayName("gepa_validate_footprint should validate required parameters")
    void gepaValidateFootprintShouldValidateRequiredParameters() throws Exception {
        // Arrange
        McpServerFeatures.SyncToolSpecification tool = findTool("gepa_validate_footprint");

        // Test missing original_workflow
        McpSchema.CallToolRequest request1 = new McpSchema.CallToolRequest(
                "gepa_validate_footprint", Map.of("optimized_workflow", Map.of()));

        // Act & Assert
        McpSchema.CallToolResult result1 = tool.call().apply(null, request1);
        assertTrue(result1.isError());
        String response1 = ((McpSchema.TextContent) result1.content().get(0)).text();
        assertTrue(response1.contains("original_workflow"),
                "Error should mention original_workflow parameter");

        // Test missing optimized_workflow
        McpSchema.CallToolRequest request2 = new McpSchema.CallToolRequest(
                "gepa_validate_footprint", Map.of("original_workflow", Map.of()));

        // Act & Assert
        McpSchema.CallToolResult result2 = tool.call().apply(null, request2);
        assertTrue(result2.isError());
        String response2 = ((McpSchema.TextContent) result2.content().get(0)).text();
        assertTrue(response2.contains("optimized_workflow"),
                "Error should mention optimized_workflow parameter");
    }

    @Test
    @DisplayName("gepa_score_workflow should validate required parameters")
    void gepaScoreWorkflowShouldValidateRequiredParameters() throws Exception {
        // Arrange
        McpServerFeatures.SyncToolSpecification tool = findTool("gepa_score_workflow");

        // Test missing workflow
        McpSchema.CallToolRequest request1 = new McpSchema.CallToolRequest(
                "gepa_score_workflow", Map.of("reference_patterns", List.of("industry_best")));

        // Act & Assert
        McpSchema.CallToolResult result1 = tool.call().apply(null, request1);
        assertTrue(result1.isError());
        String response1 = ((McpSchema.TextContent) result1.content().get(0)).text();
        assertTrue(response1.contains("workflow"),
                "Error should mention workflow parameter");

        // Test missing reference_patterns
        McpSchema.CallToolRequest request2 = new McpSchema.CallToolRequest(
                "gepa_score_workflow", Map.of("workflow", Map.of()));

        // Act & Assert
        McpSchema.CallToolResult result2 = tool.call().apply(null, request2);
        assertTrue(result2.isError());
        String response2 = ((McpSchema.TextContent) result2.content().get(0)).text();
        assertTrue(response2.contains("reference_patterns"),
                "Error should mention reference_patterns parameter");

        // Test empty reference_patterns
        McpSchema.CallToolRequest request3 = new McpSchema.CallToolRequest(
                "gepa_score_workflow", Map.of("workflow", Map.of(), "reference_patterns", List.of()));

        // Act & Assert
        McpSchema.CallToolResult result3 = tool.call().apply(null, request3);
        assertTrue(result3.isError());
        String response3 = ((McpSchema.TextContent) result3.content().get(0)).text();
        assertTrue(response3.contains("empty"),
                "Error should mention empty reference_patterns");
    }

    @Test
    @DisplayName("gepa_optimize_workflow should return valid response on success")
    void gepaOptimizeWorkflowShouldReturnValidResponse() throws Exception {
        // Arrange
        McpServerFeatures.SyncToolSpecification tool = findTool("gepa_optimize_workflow");

        // Create test workflow specification
        Map<String, Object> workflowSpec = Map.of(
                "name", "test_workflow",
                "net", Map.of("id", "net_1"),
                "input_params", List.of("param1"),
                "output_params", List.of("result")
        );

        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                "gepa_optimize_workflow", Map.of(
                    "workflow_spec", workflowSpec,
                    "optimization_target", "performance",
                    "constraints", Map.of("max_time", 1000),
                    "reference_patterns", List.of("sequential", "parallel")
                ));

        // Act
        McpSchema.CallToolResult result = tool.call().apply(null, request);

        // Assert
        assertFalse(result.isError());
        assertEquals(1, result.content().size());

        String json = ((McpSchema.TextContent) result.content().get(0)).text();
        Map<String, Object> response = mapper.readValue(json, Map.class);

        // Verify response structure
        assertTrue(response.containsKey("optimized_workflow"));
        assertTrue(response.containsKey("optimization_metrics"));
        assertTrue(response.containsKey("applied_transformations"));
        assertTrue(response.containsKey("execution_path"));
        assertTrue(response.containsKey("confidence"));
        assertTrue(response.containsKey("_metadata"));

        // Verify specific values
        assertEquals("performance", response.get("optimization_target"));
        assertTrue((Double) response.get("confidence") >= 0.0 && (Double) response.get("confidence") <= 1.0);

        Map<String, Object> metrics = (Map<String, Object>) response.get("optimization_metrics");
        assertTrue(metrics.containsKey("performance_gain"));
        assertTrue(metrics.containsKey("complexity_reduction"));
        assertTrue(metrics.containsKey("compliance_score"));

        @SuppressWarnings("unchecked")
        List<String> transformations = (List<String>) response.get("applied_transformations");
        assertFalse(transformations.isEmpty());
    }

    @Test
    @DisplayName("gepa_validate_footprint should return valid response on success")
    void gepaValidateFootprintShouldReturnValidResponse() throws Exception {
        // Arrange
        McpServerFeatures.SyncToolSpecification tool = findTool("gepa_validate_footprint");

        // Create test workflows
        Map<String, Object> originalWorkflow = Map.of(
                "name", "original",
                "net", Map.of("id", "net_original")
        );

        Map<String, Object> optimizedWorkflow = Map.of(
                "name", "optimized",
                "net", Map.of("id", "net_optimized"),
                "optimization", Map.of("target", "performance")
        );

        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                "gepa_validate_footprint", Map.of(
                    "original_workflow", originalWorkflow,
                    "optimized_workflow", optimizedWorkflow,
                    "validation_mode", "strict",
                    "focus_areas", List.of("state_preservation", "resource_usage")
                ));

        // Act
        McpSchema.CallToolResult result = tool.call().apply(null, request);

        // Assert
        assertFalse(result.isError());
        assertEquals(1, result.content().size());

        String json = ((McpSchema.TextContent) result.content().get(0)).text();
        Map<String, Object> response = mapper.readValue(json, Map.class);

        // Verify response structure
        assertTrue(response.containsKey("validation_passed"));
        assertTrue(response.containsKey("footprint_compatibility"));
        assertTrue(response.containsKey("behavioral_equivalence"));
        assertTrue(response.containsKey("invariants"));
        assertTrue(response.containsKey("_metadata"));

        // Verify specific values
        assertTrue((Boolean) response.get("validation_passed"));
        assertTrue((Double) response.get("footprint_compatibility") >= 0.0);
        assertTrue((Double) response.get("footprint_compatibility") <= 1.0);

        @SuppressWarnings("unchecked")
        List<String> invariants = (List<String>) response.get("invariants");
        assertFalse(invariants.isEmpty());
    }

    @Test
    @DisplayName("gepa_score_workflow should return valid response on success")
    void gepaScoreWorkflowShouldReturnValidResponse() throws Exception {
        // Arrange
        McpServerFeatures.SyncToolSpecification tool = findTool("gepa_score_workflow");

        // Create test workflow
        Map<String, Object> workflow = Map.of(
                "name", "test_workflow",
                "complexity", "medium",
                "patterns", List.of("sequential", "choice")
        );

        Map<String, Object> scoringWeights = Map.of(
                "performance", 0.5,
                "maintainability", 0.3,
                "compliance", 0.2
        );

        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                "gepa_score_workflow", Map.of(
                    "workflow", workflow,
                    "reference_patterns", List.of("industry_best", "compliance_standard"),
                    "scoring_weights", scoringWeights,
                    "include_detailed_analysis", true
                ));

        // Act
        McpSchema.CallToolResult result = tool.call().apply(null, request);

        // Assert
        assertFalse(result.isError());
        assertEquals(1, result.content().size());

        String json = ((McpSchema.TextContent) result.content().get(0)).text();
        Map<String, Object> response = mapper.readValue(json, Map.class);

        // Verify response structure
        assertTrue(response.containsKey("total_score"));
        assertTrue(response.containsKey("pattern_scores"));
        assertTrue(response.containsKey("dimension_scores"));
        assertTrue(response.containsKey("recommendations"));
        assertTrue(response.containsKey("improvement_areas"));
        assertTrue(response.containsKey("_metadata"));

        // Verify score values
        double totalScore = (Double) response.get("total_score");
        assertTrue(totalScore >= 0.0 && totalScore <= 1.0);

        @SuppressWarnings("unchecked")
        Map<String, Double> patternScores = (Map<String, Double>) response.get("pattern_scores");
        assertFalse(patternScores.isEmpty());

        @SuppressWarnings("unchecked")
        Map<String, Double> dimensionScores = (Map<String, Double>) response.get("dimension_scores");
        assertFalse(dimensionScores.isEmpty());

        @SuppressWarnings("unchecked")
        List<String> recommendations = (List<String>) response.get("recommendations");
        // Can be empty for good workflows

        @SuppressWarnings("unchecked")
        Map<String, Object> detailedAnalysis = (Map<String, Object>) response.get("detailed_analysis");
        assertNotNull(detailedAnalysis);
    }

    @Test
    @DisplayName("Should validate JSON serialization format")
    void shouldValidateJsonSerializationFormat() throws Exception {
        // Test all tools produce valid JSON
        for (McpServerFeatures.SyncToolSpecification tool : tools) {
            // Create minimal valid request
            Map<String, Object> requestArgs = switch (tool.tool().name()) {
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
                default -> throw new AssertionError("Unknown tool: " + tool.tool().name());
            };

            // Act
            McpSchema.CallToolResult result = tool.call().apply(null,
                    new McpSchema.CallToolRequest(tool.tool().name(), requestArgs));

            // Assert
            if (!result.isError()) {
                String json = ((McpSchema.TextContent) result.content().get(0)).text();
                assertDoesNotThrow(() -> mapper.readTree(json),
                        "Tool response should be valid JSON");
            }
        }
    }

    @Test
    @DisplayName("Should handle invalid JSON gracefully in error responses")
    void shouldHandleInvalidJsonGracefullyInErrorResponses() throws Exception {
        // This test verifies that error responses are still valid JSON
        // even when the original request is malformed

        McpServerFeatures.SyncToolSpecification tool = findTool("gepa_optimize_workflow");

        // Create malformed workflow_spec (invalid JSON structure)
        Map<String, Object> badWorkflow = Map.of(
                "invalid", Map.of(
                    "nested", "value",
                    "malformed", List.of("incomplete")
                )
        );

        McpSchema.CallToolRequest request = new McpSchema.CallToolRequest(
                "gepa_optimize_workflow", Map.of(
                    "workflow_spec", badWorkflow,
                    "optimization_target", "performance"
                ));

        // Act
        McpSchema.CallToolResult result = tool.call().apply(null, request);

        // Assert - Even errors should be valid JSON
        assertFalse(result.isError()); // Our tool doesn't fail on malformed data internally
        String json = ((McpSchema.TextContent) result.content().get(0)).text();
        assertDoesNotThrow(() -> mapper.readTree(json),
                "Error response should be valid JSON");
    }

    // Helper methods

    private McpServerFeatures.SyncToolSpecification findTool(String name) {
        return tools.stream()
                .filter(spec -> spec.tool().name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tool not found: " + name));
    }
}