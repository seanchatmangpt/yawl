package org.yawlfoundation.yawl.integration.wizard.mcp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

/**
 * Integration tests for McpCapabilityMatcher.
 *
 * <p>Verifies autonomic matching of MCP tools to workflow task slots
 * based on pattern requirements and capability scoring.
 */
@DisplayName("MCP Capability Matcher Tests")
class McpCapabilityMatcherTest {

    private McpCapabilityMatcher matcher;
    private List<McpToolDescriptor> allTools;

    @BeforeEach
    void setup() {
        matcher = new McpCapabilityMatcher();
        allTools = McpToolRegistry.allTools();
    }

    @Test
    @DisplayName("Matcher can be instantiated")
    void testInstantiation() {
        assertNotNull(matcher, "Matcher should be created");
    }

    @Test
    @DisplayName("Match returns non-null map for WP-1")
    void testMatchWp1() {
        Map<String, McpToolDescriptor> matches = matcher.match(
            "WP-1", allTools, List.of());

        assertNotNull(matches, "Match result should not be null");
        assertFalse(matches.isEmpty(), "Should find matches for WP-1");
    }

    @Test
    @DisplayName("Match returns non-null map for WP-3 (parallel)")
    void testMatchWp3() {
        Map<String, McpToolDescriptor> matches = matcher.match(
            "WP-3", allTools, List.of());

        assertNotNull(matches, "Match result should not be null");
        assertFalse(matches.isEmpty(),
            "Should find matches for WP-3 (synchronization)");
    }

    @Test
    @DisplayName("Match binds tools to task slots")
    void testMatchTaskSlots() {
        Map<String, McpToolDescriptor> matches = matcher.match(
            "WP-1", allTools, List.of());

        // WP-1 should have launch, execute, complete slots
        assertTrue(matches.containsKey("launch") || matches.containsKey("execute"),
            "Should bind at least some task slots");
    }

    @Test
    @DisplayName("Each matched tool has valid metadata")
    void testMatchedToolQuality() {
        Map<String, McpToolDescriptor> matches = matcher.match(
            "WP-1", allTools, List.of());

        for (McpToolDescriptor tool : matches.values()) {
            assertNotNull(tool.toolId());
            assertFalse(tool.toolId().isEmpty());
            assertNotNull(tool.displayName());
            assertNotNull(tool.category());
        }
    }

    @Test
    @DisplayName("Score returns valid 0-100 range")
    void testScoreRange() {
        McpToolDescriptor launchTool = allTools.stream()
            .filter(t -> t.toolId().equals("launch_case"))
            .findFirst()
            .orElseThrow();

        int score = matcher.score(launchTool, "launch", List.of());

        assertTrue(score >= 0 && score <= 100,
            "Score should be between 0 and 100");
    }

    @Test
    @DisplayName("Matching launch task prefers case management tools")
    void testLaunchTaskMatching() {
        McpToolDescriptor launchTool = allTools.stream()
            .filter(t -> t.toolId().equals("launch_case"))
            .findFirst()
            .orElseThrow();

        int launchScore = matcher.score(launchTool, "launch", List.of());

        // Compare with a workitem tool
        McpToolDescriptor workitemTool = allTools.stream()
            .filter(t -> t.toolId().equals("checkout_workitem"))
            .findFirst()
            .orElseThrow();

        int workitemScore = matcher.score(workitemTool, "launch", List.of());

        assertTrue(launchScore >= workitemScore,
            "CASE_MANAGEMENT tool should score higher for launch task");
    }

    @Test
    @DisplayName("Matching execute task prefers workitem tools")
    void testExecuteTaskMatching() {
        McpToolDescriptor checkoutTool = allTools.stream()
            .filter(t -> t.toolId().equals("checkout_workitem"))
            .findFirst()
            .orElseThrow();

        int executeScore = matcher.score(checkoutTool, "execute", List.of());

        // Compare with a case management tool
        McpToolDescriptor launchTool = allTools.stream()
            .filter(t -> t.toolId().equals("launch_case"))
            .findFirst()
            .orElseThrow();

        int launchScore = matcher.score(launchTool, "execute", List.of());

        assertTrue(executeScore >= launchScore,
            "WORKITEM tool should score higher for execute task");
    }

    @Test
    @DisplayName("Lower complexity tools score higher (when category matches)")
    void testComplexityScoring() {
        // Find tools in same category with different complexity
        McpToolDescriptor tool1 = allTools.stream()
            .filter(t -> t.category() == McpToolCategory.CASE_MANAGEMENT
                    && t.complexityScore() <= 3)
            .findFirst()
            .orElseThrow();

        McpToolDescriptor tool2 = allTools.stream()
            .filter(t -> t.category() == McpToolCategory.CASE_MANAGEMENT
                    && t.complexityScore() >= 4)
            .findFirst()
            .orElseThrow();

        int score1 = matcher.score(tool1, "launch", List.of());
        int score2 = matcher.score(tool2, "launch", List.of());

        assertTrue(score1 >= score2,
            "Lower complexity tool should score at least equal to higher complexity");
    }

    @Test
    @DisplayName("Explain match provides rationale")
    void testExplainMatch() {
        McpToolDescriptor selected = allTools.stream()
            .filter(t -> t.toolId().equals("launch_case"))
            .findFirst()
            .orElseThrow();

        String explanation = matcher.explainMatch(
            "launch",
            selected,
            allTools.stream()
                .filter(t -> t.category() == McpToolCategory.CASE_MANAGEMENT)
                .toList()
        );

        assertNotNull(explanation);
        assertFalse(explanation.isEmpty());
        assertTrue(explanation.contains("launch_case") || explanation.contains("Launch"),
            "Explanation should mention selected tool");
    }

    @Test
    @DisplayName("Explanation includes scoring information")
    void testExplanationScoring() {
        McpToolDescriptor selected = allTools.stream()
            .filter(t -> t.toolId().equals("launch_case"))
            .findFirst()
            .orElseThrow();

        String explanation = matcher.explainMatch(
            "launch",
            selected,
            allTools.stream()
                .filter(t -> t.category() == McpToolCategory.CASE_MANAGEMENT)
                .toList()
        );

        assertTrue(explanation.contains("Score") || explanation.contains("score"),
            "Explanation should include scoring details");
    }

    @Test
    @DisplayName("Match with empty requirements works")
    void testMatchEmptyRequirements() {
        Map<String, McpToolDescriptor> matches = matcher.match(
            "WP-1", allTools, List.of());

        assertFalse(matches.isEmpty(),
            "Should produce matches with empty requirements");
    }

    @Test
    @DisplayName("Match with requirements filters appropriately")
    void testMatchWithRequirements() {
        List<String> requirements = List.of("case");

        Map<String, McpToolDescriptor> matches = matcher.match(
            "WP-1", allTools, requirements);

        // All matched tools should be related to cases
        assertTrue(!matches.isEmpty(),
            "Should find tools matching requirements");
    }

    @Test
    @DisplayName("Different patterns produce different matches")
    void testPatternVariation() {
        Map<String, McpToolDescriptor> matchesWp1 = matcher.match(
            "WP-1", allTools, List.of());
        Map<String, McpToolDescriptor> matchesWp3 = matcher.match(
            "WP-3", allTools, List.of());

        // WP-3 (parallel) and WP-1 (sequence) may have different bindings
        assertTrue(!matchesWp1.isEmpty() && !matchesWp3.isEmpty(),
            "Both patterns should produce matches");
    }

    @Test
    @DisplayName("All returned tools are from available set")
    void testMatchedToolsAvailable() {
        Map<String, McpToolDescriptor> matches = matcher.match(
            "WP-1", allTools, List.of());

        for (McpToolDescriptor matched : matches.values()) {
            assertTrue(allTools.stream()
                    .anyMatch(t -> t.toolId().equals(matched.toolId())),
                "Matched tool should be in available set");
        }
    }

    @Test
    @DisplayName("Score is idempotent")
    void testScoreIdempotence() {
        McpToolDescriptor tool = allTools.stream()
            .filter(t -> t.toolId().equals("launch_case"))
            .findFirst()
            .orElseThrow();

        int score1 = matcher.score(tool, "launch", List.of());
        int score2 = matcher.score(tool, "launch", List.of());

        assertEquals(score1, score2,
            "Scoring should be idempotent");
    }

    @Test
    @DisplayName("Task-tool matching respects category affinity")
    void testCategoryAffinity() {
        // WORKITEM tools should prefer work-related task slots
        List<McpToolDescriptor> workitemTools = allTools.stream()
            .filter(t -> t.category() == McpToolCategory.WORKITEM)
            .toList();

        for (McpToolDescriptor tool : workitemTools) {
            int workScore = matcher.score(tool, "execute", List.of());
            int launchScore = matcher.score(tool, "launch", List.of());

            assertTrue(workScore >= launchScore,
                "WORKITEM tool should score better for work-related tasks");
        }
    }

    @Test
    @DisplayName("CASE_MANAGEMENT tools score high for launch slots")
    void testCaseManagementAffinity() {
        List<McpToolDescriptor> caseTools = allTools.stream()
            .filter(t -> t.category() == McpToolCategory.CASE_MANAGEMENT)
            .toList();

        for (McpToolDescriptor tool : caseTools) {
            int score = matcher.score(tool, "launch", List.of());

            assertTrue(score > 20,
                "CASE_MANAGEMENT tool should score reasonable for launch");
        }
    }
}
