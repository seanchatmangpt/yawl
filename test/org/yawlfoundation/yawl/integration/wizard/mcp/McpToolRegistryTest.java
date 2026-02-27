package org.yawlfoundation.yawl.integration.wizard.mcp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

/**
 * Integration tests for McpToolRegistry.
 *
 * <p>Verifies that all 15 MCP tools are registered with correct metadata,
 * categories, and lookup operations work as expected.
 */
@DisplayName("MCP Tool Registry Tests")
class McpToolRegistryTest {

    @BeforeEach
    void setup() {
        // Registry is stateless, no setup needed
    }

    @Test
    @DisplayName("Registry contains all 15 MCP tools")
    void testAllToolsPresent() {
        List<McpToolDescriptor> tools = McpToolRegistry.allTools();

        assertEquals(15, tools.size(), "Should have exactly 15 MCP tools");
    }

    @Test
    @DisplayName("All tool IDs are unique")
    void testToolIdsUnique() {
        List<McpToolDescriptor> tools = McpToolRegistry.allTools();
        Set<String> toolIds = tools.stream()
            .map(McpToolDescriptor::toolId)
            .collect(Collectors.toSet());

        assertEquals(tools.size(), toolIds.size(),
            "All tool IDs should be unique");
    }

    @Test
    @DisplayName("All 15 expected tool IDs present")
    void testExpectedToolIds() {
        List<McpToolDescriptor> tools = McpToolRegistry.allTools();
        Set<String> toolIds = tools.stream()
            .map(McpToolDescriptor::toolId)
            .collect(Collectors.toSet());

        // Expected tool IDs
        Set<String> expected = Set.of(
            "launch_case",
            "cancel_case",
            "get_case_state",
            "get_running_cases",
            "list_specifications",
            "get_specification",
            "get_specification_data",
            "get_specification_schema",
            "upload_specification",
            "unload_specification",
            "get_workitems",
            "get_workitems_for_case",
            "checkout_workitem",
            "checkin_workitem",
            "skip_workitem",
            "suspend_case",
            "resume_case"
        );

        // All expected tools should be present
        assertTrue(toolIds.containsAll(expected),
            "All 15 expected tools should be present");
    }

    @Test
    @DisplayName("CASE_MANAGEMENT category has 4 tools")
    void testCaseManagementCategory() {
        List<McpToolDescriptor> tools = McpToolRegistry.byCategory(
            McpToolCategory.CASE_MANAGEMENT);

        assertEquals(4, tools.size(),
            "CASE_MANAGEMENT should have exactly 4 tools");

        Set<String> toolIds = tools.stream()
            .map(McpToolDescriptor::toolId)
            .collect(Collectors.toSet());

        assertTrue(toolIds.containsAll(Set.of(
            "launch_case",
            "cancel_case",
            "get_case_state",
            "get_running_cases"
        )), "CASE_MANAGEMENT should have correct tools");
    }

    @Test
    @DisplayName("SPECIFICATION category has 4 tools")
    void testSpecificationCategory() {
        List<McpToolDescriptor> tools = McpToolRegistry.byCategory(
            McpToolCategory.SPECIFICATION);

        assertEquals(4, tools.size(),
            "SPECIFICATION should have exactly 4 tools");

        Set<String> toolIds = tools.stream()
            .map(McpToolDescriptor::toolId)
            .collect(Collectors.toSet());

        assertTrue(toolIds.containsAll(Set.of(
            "list_specifications",
            "get_specification",
            "upload_specification",
            "unload_specification"
        )), "SPECIFICATION should have correct tools");
    }

    @Test
    @DisplayName("WORKITEM category has 5 tools")
    void testWorkitemCategory() {
        List<McpToolDescriptor> tools = McpToolRegistry.byCategory(
            McpToolCategory.WORKITEM);

        assertEquals(5, tools.size(),
            "WORKITEM should have exactly 5 tools");

        Set<String> toolIds = tools.stream()
            .map(McpToolDescriptor::toolId)
            .collect(Collectors.toSet());

        assertTrue(toolIds.containsAll(Set.of(
            "get_workitems",
            "get_workitems_for_case",
            "checkout_workitem",
            "checkin_workitem",
            "skip_workitem"
        )), "WORKITEM should have correct tools");
    }

    @Test
    @DisplayName("LIFECYCLE category has 2 tools")
    void testLifecycleCategory() {
        List<McpToolDescriptor> tools = McpToolRegistry.byCategory(
            McpToolCategory.LIFECYCLE);

        assertEquals(2, tools.size(),
            "LIFECYCLE should have exactly 2 tools");

        Set<String> toolIds = tools.stream()
            .map(McpToolDescriptor::toolId)
            .collect(Collectors.toSet());

        assertTrue(toolIds.containsAll(Set.of(
            "suspend_case",
            "resume_case"
        )), "LIFECYCLE should have correct tools");
    }

    @Test
    @DisplayName("Total tools across all categories = 15")
    void testCategoryDistribution() {
        Map<McpToolCategory, Integer> counts = McpToolRegistry.countByCategory();

        int total = counts.values().stream()
            .mapToInt(Integer::intValue)
            .sum();

        assertEquals(15, total,
            "All categories combined should have exactly 15 tools");
        assertEquals(4, counts.get(McpToolCategory.CASE_MANAGEMENT));
        assertEquals(4, counts.get(McpToolCategory.SPECIFICATION));
        assertEquals(5, counts.get(McpToolCategory.WORKITEM));
        assertEquals(2, counts.get(McpToolCategory.LIFECYCLE));
    }

    @Test
    @DisplayName("findById returns correct tool")
    void testFindById() {
        Optional<McpToolDescriptor> tool = McpToolRegistry.findById("launch_case");

        assertTrue(tool.isPresent(), "launch_case should be found");
        assertEquals("launch_case", tool.get().toolId());
        assertEquals(McpToolCategory.CASE_MANAGEMENT, tool.get().category());
    }

    @Test
    @DisplayName("findById returns empty for non-existent tool")
    void testFindByIdNotFound() {
        Optional<McpToolDescriptor> tool = McpToolRegistry.findById(
            "nonexistent_tool");

        assertTrue(tool.isEmpty(),
            "Non-existent tool should not be found");
    }

    @Test
    @DisplayName("All tools have non-empty displayName and description")
    void testToolMetadata() {
        List<McpToolDescriptor> tools = McpToolRegistry.allTools();

        for (McpToolDescriptor tool : tools) {
            assertFalse(tool.displayName().isEmpty(),
                "Tool " + tool.toolId() + " should have displayName");
            assertFalse(tool.description().isEmpty(),
                "Tool " + tool.toolId() + " should have description");
            assertNotNull(tool.category(),
                "Tool " + tool.toolId() + " should have category");
        }
    }

    @Test
    @DisplayName("All tools have valid complexity scores (1-10)")
    void testComplexityScores() {
        List<McpToolDescriptor> tools = McpToolRegistry.allTools();

        for (McpToolDescriptor tool : tools) {
            assertTrue(tool.complexityScore() >= 1 && tool.complexityScore() <= 10,
                "Tool " + tool.toolId() + " should have complexity between 1-10");
        }
    }

    @Test
    @DisplayName("All tools require engine session")
    void testEngineSessionRequirement() {
        List<McpToolDescriptor> tools = McpToolRegistry.allTools();

        for (McpToolDescriptor tool : tools) {
            assertTrue(tool.requiresEngineSession(),
                "Tool " + tool.toolId() + " should require engine session");
        }
    }

    @Test
    @DisplayName("All tools have parameter and output definitions")
    void testToolSignatures() {
        List<McpToolDescriptor> tools = McpToolRegistry.allTools();

        for (McpToolDescriptor tool : tools) {
            assertNotNull(tool.parameterNames(),
                "Tool " + tool.toolId() + " should have parameterNames");
            assertNotNull(tool.outputFields(),
                "Tool " + tool.toolId() + " should have outputFields");
        }
    }

    @Test
    @DisplayName("Recommended tools for WP-1 (Sequence)")
    void testRecommendedForSequence() {
        List<McpToolDescriptor> recommended = McpToolRegistry
            .recommendedForPattern("WP-1");

        assertFalse(recommended.isEmpty(),
            "Should recommend tools for WP-1");

        // WP-1 should recommend case management and workitem tools
        Set<McpToolCategory> categories = recommended.stream()
            .map(McpToolDescriptor::category)
            .collect(Collectors.toSet());

        assertTrue(categories.contains(McpToolCategory.CASE_MANAGEMENT) ||
                  categories.contains(McpToolCategory.WORKITEM),
            "WP-1 should include case or workitem tools");
    }

    @Test
    @DisplayName("Recommended tools for WP-3 (Parallel)")
    void testRecommendedForParallel() {
        List<McpToolDescriptor> recommended = McpToolRegistry
            .recommendedForPattern("WP-3");

        assertEquals(15, recommended.size(),
            "WP-3 (synchronization) should recommend all 15 tools");
    }

    @Test
    @DisplayName("Recommended tools for unknown pattern defaults to all")
    void testRecommendedUnknownPattern() {
        List<McpToolDescriptor> recommended = McpToolRegistry
            .recommendedForPattern("WP-999");

        assertEquals(15, recommended.size(),
            "Unknown pattern should default to all tools");
    }

    @Test
    @DisplayName("Tools by complexity level filtering")
    void testComplexityFiltering() {
        // Get tools with complexity 1-3 (simple tools)
        List<McpToolDescriptor> simple = McpToolRegistry
            .byComplexity(1, 3);

        assertFalse(simple.isEmpty(),
            "Should find some simple tools (complexity 1-3)");

        for (McpToolDescriptor tool : simple) {
            assertTrue(tool.complexityScore() >= 1 && tool.complexityScore() <= 3,
                "Tool should be in complexity range");
        }
    }

    @Test
    @DisplayName("Registry is immutable")
    void testRegistryImmutability() {
        List<McpToolDescriptor> tools1 = McpToolRegistry.allTools();
        List<McpToolDescriptor> tools2 = McpToolRegistry.allTools();

        assertEquals(tools1.size(), tools2.size(),
            "Multiple calls should return same count");

        // Verify returned list is not the same instance (defensive copy)
        assertNotSame(tools1, tools2,
            "Each call should return independent immutable list");
    }
}
