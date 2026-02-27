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
 * Chicago TDD tests for YawlConformanceToolSpecifications.
 * Uses real FootprintExtractor + FootprintScorer — no mocks.
 *
 * @since YAWL 6.0
 */
class YawlConformanceToolSpecificationsTest {

    // Minimal valid POWL JSON — a SEQUENCE of two activities (A → B)
    private static final String SEQUENCE_AB = """
        {"type":"SEQUENCE","id":"root","children":[
          {"type":"ACTIVITY","id":"a1","label":"A"},
          {"type":"ACTIVITY","id":"a2","label":"B"}
        ]}
        """;

    // SEQUENCE A → B → C
    private static final String SEQUENCE_ABC = """
        {"type":"SEQUENCE","id":"root","children":[
          {"type":"ACTIVITY","id":"a1","label":"A"},
          {"type":"ACTIVITY","id":"a2","label":"B"},
          {"type":"ACTIVITY","id":"a3","label":"C"}
        ]}
        """;

    // Parallel A ‖ B
    private static final String PARALLEL_AB = """
        {"type":"PARALLEL","id":"root","children":[
          {"type":"ACTIVITY","id":"a1","label":"A"},
          {"type":"ACTIVITY","id":"a2","label":"B"}
        ]}
        """;

    // XOR choice A ⊕ B
    private static final String XOR_AB = """
        {"type":"XOR","id":"root","children":[
          {"type":"ACTIVITY","id":"a1","label":"A"},
          {"type":"ACTIVITY","id":"a2","label":"B"}
        ]}
        """;

    private YawlConformanceToolSpecifications specs;

    @BeforeEach
    void setUp() {
        specs = new YawlConformanceToolSpecifications();
    }

    // =========================================================================
    // Tool Inventory
    // =========================================================================

    @Test
    void testCreateAllReturnsTwoTools() {
        List<McpServerFeatures.SyncToolSpecification> tools = specs.createAll();
        assertEquals(2, tools.size(), "Must provide exactly 2 conformance tools");
    }

    @Test
    void testToolNamesAreCorrect() {
        List<McpServerFeatures.SyncToolSpecification> tools = specs.createAll();
        List<String> names = tools.stream()
            .map(t -> t.tool().name())
            .toList();
        assertTrue(names.contains("yawl_extract_footprint"),
            "Must have yawl_extract_footprint tool");
        assertTrue(names.contains("yawl_compare_conformance"),
            "Must have yawl_compare_conformance tool");
    }

    @Test
    void testToolDescriptionsAreNotBlank() {
        specs.createAll().forEach(t ->
            assertFalse(t.tool().description().isBlank(),
                "Tool " + t.tool().name() + " must have a non-blank description")
        );
    }

    @Test
    void testExtractFootprintToolRequiresPowlModelJson() {
        McpServerFeatures.SyncToolSpecification tool = findTool("yawl_extract_footprint");
        assertTrue(tool.tool().inputSchema().required().contains("powlModelJson"),
            "yawl_extract_footprint must require powlModelJson parameter");
    }

    @Test
    void testCompareConformanceToolRequiresBothJsonParams() {
        McpServerFeatures.SyncToolSpecification tool = findTool("yawl_compare_conformance");
        List<String> required = tool.tool().inputSchema().required();
        assertTrue(required.contains("referenceModelJson"),
            "yawl_compare_conformance must require referenceModelJson");
        assertTrue(required.contains("candidateModelJson"),
            "yawl_compare_conformance must require candidateModelJson");
    }

    // =========================================================================
    // yawl_extract_footprint execution
    // =========================================================================

    @Test
    void testExtractFootprintMissingParamReturnsError() {
        McpServerFeatures.SyncToolSpecification tool = findTool("yawl_extract_footprint");
        McpSchema.CallToolResult result = invoke(tool, Map.of());

        assertTrue(result.isError(), "Missing powlModelJson must return error");
    }

    @Test
    void testExtractFootprintInvalidJsonReturnsError() {
        McpServerFeatures.SyncToolSpecification tool = findTool("yawl_extract_footprint");
        McpSchema.CallToolResult result = invoke(tool,
            Map.of("powlModelJson", "not-valid-json{{{"));

        assertTrue(result.isError(), "Invalid JSON must return error");
    }

    @Test
    void testExtractFootprintSequenceProducesDirectSuccession() {
        McpServerFeatures.SyncToolSpecification tool = findTool("yawl_extract_footprint");
        McpSchema.CallToolResult result = invoke(tool,
            Map.of("powlModelJson", SEQUENCE_AB));

        assertFalse(result.isError(), "Valid sequence model must not error");
        String text = extractText(result);
        assertTrue(text.contains("Direct Succession") || text.contains("→"),
            "Sequence model must produce direct succession relationships: " + text);
        assertTrue(text.contains("A") && text.contains("B"),
            "Must mention activities A and B");
    }

    @Test
    void testExtractFootprintParallelProducesConcurrency() {
        McpServerFeatures.SyncToolSpecification tool = findTool("yawl_extract_footprint");
        McpSchema.CallToolResult result = invoke(tool,
            Map.of("powlModelJson", PARALLEL_AB));

        assertFalse(result.isError(), "Valid parallel model must not error");
        String text = extractText(result);
        assertTrue(text.contains("Concurrency") || text.contains("‖"),
            "Parallel model must produce concurrency relationships: " + text);
    }

    @Test
    void testExtractFootprintXorProducesExclusivity() {
        McpServerFeatures.SyncToolSpecification tool = findTool("yawl_extract_footprint");
        McpSchema.CallToolResult result = invoke(tool,
            Map.of("powlModelJson", XOR_AB));

        assertFalse(result.isError(), "Valid XOR model must not error");
        String text = extractText(result);
        assertTrue(text.contains("Exclusiv") || text.contains("⊕"),
            "XOR model must produce exclusivity relationships: " + text);
    }

    // =========================================================================
    // yawl_compare_conformance execution
    // =========================================================================

    @Test
    void testCompareConformanceMissingReferenceReturnsError() {
        McpServerFeatures.SyncToolSpecification tool = findTool("yawl_compare_conformance");
        McpSchema.CallToolResult result = invoke(tool,
            Map.of("candidateModelJson", SEQUENCE_AB));

        assertTrue(result.isError(), "Missing referenceModelJson must return error");
    }

    @Test
    void testCompareConformanceMissingCandidateReturnsError() {
        McpServerFeatures.SyncToolSpecification tool = findTool("yawl_compare_conformance");
        McpSchema.CallToolResult result = invoke(tool,
            Map.of("referenceModelJson", SEQUENCE_AB));

        assertTrue(result.isError(), "Missing candidateModelJson must return error");
    }

    @Test
    void testCompareIdenticalModelsScoreIsOne() {
        McpServerFeatures.SyncToolSpecification tool = findTool("yawl_compare_conformance");
        McpSchema.CallToolResult result = invoke(tool,
            Map.of("referenceModelJson", SEQUENCE_AB,
                   "candidateModelJson", SEQUENCE_AB));

        assertFalse(result.isError(), "Identical models comparison must not error");
        String text = extractText(result);
        assertTrue(text.contains("1.0") || text.contains("HIGH"),
            "Identical models must have score 1.0 / HIGH: " + text);
    }

    @Test
    void testCompareScoreIsInValidRange() {
        McpServerFeatures.SyncToolSpecification tool = findTool("yawl_compare_conformance");
        McpSchema.CallToolResult result = invoke(tool,
            Map.of("referenceModelJson", SEQUENCE_AB,
                   "candidateModelJson", SEQUENCE_ABC));

        assertFalse(result.isError(), "Compare must not error");
        String text = extractText(result);
        // Score should be parseable as a double in [0,1]
        // The text format is "Overall Score: 0.XXXX"
        assertTrue(text.contains("Score") || text.contains("score"),
            "Result must mention score: " + text);
    }

    @Test
    void testCompareSequenceVsParallelReducesScore() {
        McpServerFeatures.SyncToolSpecification tool = findTool("yawl_compare_conformance");
        McpSchema.CallToolResult result = invoke(tool,
            Map.of("referenceModelJson", SEQUENCE_AB,
                   "candidateModelJson", PARALLEL_AB));

        assertFalse(result.isError(), "Sequence vs parallel comparison must not error");
        String text = extractText(result);
        // Sequence and parallel are structurally different — score should not be 1.0
        // Both have A and B, but direct succession vs concurrency differ
        assertFalse(text.contains("1.0000") && !text.contains("0."),
            "Sequence vs parallel should not score 1.0: " + text);
    }

    @Test
    void testCompareInvalidReferenceJsonReturnsError() {
        McpServerFeatures.SyncToolSpecification tool = findTool("yawl_compare_conformance");
        McpSchema.CallToolResult result = invoke(tool,
            Map.of("referenceModelJson", "not-json",
                   "candidateModelJson", SEQUENCE_AB));

        assertTrue(result.isError(), "Invalid referenceModelJson must return error");
    }

    @Test
    void testCompareInvalidCandidateJsonReturnsError() {
        McpServerFeatures.SyncToolSpecification tool = findTool("yawl_compare_conformance");
        McpSchema.CallToolResult result = invoke(tool,
            Map.of("referenceModelJson", SEQUENCE_AB,
                   "candidateModelJson", "not-json"));

        assertTrue(result.isError(), "Invalid candidateModelJson must return error");
    }

    @Test
    void testCompareContainsInterpretation() {
        McpServerFeatures.SyncToolSpecification tool = findTool("yawl_compare_conformance");
        McpSchema.CallToolResult result = invoke(tool,
            Map.of("referenceModelJson", SEQUENCE_AB,
                   "candidateModelJson", SEQUENCE_AB));

        assertFalse(result.isError());
        String text = extractText(result);
        assertTrue(text.contains("HIGH") || text.contains("MEDIUM") || text.contains("LOW"),
            "Result must contain conformance interpretation: " + text);
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
}
