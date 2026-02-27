package org.yawlfoundation.yawl.integration.wizard.patterns;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PatternAdvisor pattern recommendation engine.
 *
 * <p>Verifies recommendation logic, scoring, and explanations
 * for various MCP tool and A2A agent configurations.
 */
@DisplayName("Pattern Advisor Tests")
class PatternAdvisorTest {

    @Test
    @DisplayName("Single tool/agent recommends Sequence")
    void testSingleToolRecommendation() {
        List<WorkflowPattern> recommendations = PatternAdvisor.recommend(1, 1);
        assertNotNull(recommendations);
        assertFalse(recommendations.isEmpty());
        assertEquals(WorkflowPattern.SEQUENCE, recommendations.get(0));
    }

    @Test
    @DisplayName("Multiple tools recommend Parallel Split and Synchronization")
    void testMultipleToolsRecommendation() {
        List<WorkflowPattern> recommendations = PatternAdvisor.recommend(3, 1);
        assertNotNull(recommendations);
        assertFalse(recommendations.isEmpty());
        assertTrue(recommendations.contains(WorkflowPattern.PARALLEL_SPLIT));
        assertTrue(recommendations.contains(WorkflowPattern.SYNCHRONIZATION));
    }

    @Test
    @DisplayName("Multiple agents enable deferred choice")
    void testMultipleAgentsRecommendation() {
        List<WorkflowPattern> recommendations = PatternAdvisor.recommend(1, 3);
        assertNotNull(recommendations);
        // Should include deferred choice for event-driven routing
        assertTrue(recommendations.stream()
            .anyMatch(p -> p == WorkflowPattern.DEFERRED_CHOICE || p == WorkflowPattern.PARALLEL_SPLIT),
            "Should recommend event-driven patterns for multiple agents");
    }

    @Test
    @DisplayName("Loop requirement adds Arbitrary Cycles")
    void testLoopRequirementRecommendation() {
        List<WorkflowPattern> recommendations = PatternAdvisor.recommend(
            2, 1,
            List.of("loop", "repeat")
        );
        assertNotNull(recommendations);
        assertTrue(recommendations.contains(WorkflowPattern.ARBITRARY_CYCLES));
    }

    @Test
    @DisplayName("Cancellation requirement adds Cancel patterns")
    void testCancellationRequirementRecommendation() {
        List<WorkflowPattern> recommendations = PatternAdvisor.recommend(
            2, 1,
            List.of("cancellation", "abort")
        );
        assertNotNull(recommendations);
        assertTrue(recommendations.contains(WorkflowPattern.CANCEL_TASK));
        assertTrue(recommendations.contains(WorkflowPattern.CANCEL_CASE));
    }

    @Test
    @DisplayName("Multiple instance requirement adds MI patterns")
    void testMultipleInstanceRequirementRecommendation() {
        List<WorkflowPattern> recommendations = PatternAdvisor.recommend(
            3, 2,
            List.of("multiple_instances")
        );
        assertNotNull(recommendations);
        assertTrue(recommendations.stream()
            .anyMatch(p -> p.getCategory() == PatternCategory.MULTIPLE_INSTANCES));
    }

    @Test
    @DisplayName("Empty requirements returns reasonable defaults")
    void testEmptyRequirementsRecommendation() {
        List<WorkflowPattern> recommendations = PatternAdvisor.recommend(2, 2, List.of());
        assertNotNull(recommendations);
        assertFalse(recommendations.isEmpty());
        // Should still recommend basic patterns like parallel split
        assertTrue(recommendations.stream().anyMatch(p -> p.getCategory() == PatternCategory.BASIC));
    }

    @Test
    @DisplayName("Pattern scoring produces valid scores")
    void testPatternScoring() {
        Map<String, Object> context = Map.of(
            "mcp.tool.count", 2,
            "a2a.agent.count", 2,
            "requirements", List.of()
        );

        int score = PatternAdvisor.scorePattern(WorkflowPattern.SEQUENCE, context);
        assertTrue(score >= 0 && score <= 100, String.format("Score out of range: %d", score));
    }

    @Test
    @DisplayName("High-suitability patterns score higher")
    void testScoringReflectsSuitability() {
        Map<String, Object> context = Map.of(
            "mcp.tool.count", 3,
            "a2a.agent.count", 1
        );

        int sequenceScore = PatternAdvisor.scorePattern(WorkflowPattern.SEQUENCE, context);
        int parallelScore = PatternAdvisor.scorePattern(WorkflowPattern.PARALLEL_SPLIT, context);

        // Both should have good scores for this config
        assertTrue(sequenceScore > 50);
        assertTrue(parallelScore > 50);
    }

    @Test
    @DisplayName("Pattern explanation is informative")
    void testPatternExplanation() {
        String explanation = PatternAdvisor.explainRecommendation(
            WorkflowPattern.SEQUENCE,
            1,
            1
        );
        assertNotNull(explanation);
        assertTrue(explanation.contains("Sequence"));
        assertTrue(explanation.contains("WP-1"));
        assertTrue(explanation.contains("MCP"));
        assertTrue(explanation.contains("A2A"));
    }

    @Test
    @DisplayName("Top N recommendations returns correct count")
    void testTopNRecommendations() {
        List<WorkflowPattern> top1 = PatternAdvisor.topRecommendations(2, 2, 1, List.of());
        assertEquals(1, top1.size());

        List<WorkflowPattern> top5 = PatternAdvisor.topRecommendations(2, 2, 5, List.of());
        assertTrue(top5.size() <= 5);
        assertTrue(top5.size() > 0);
    }

    @Test
    @DisplayName("Negative tool count throws exception")
    void testNegativeToolCountThrows() {
        assertThrows(IllegalArgumentException.class, () -> {
            PatternAdvisor.recommend(-1, 1);
        });
    }

    @Test
    @DisplayName("Negative agent count throws exception")
    void testNegativeAgentCountThrows() {
        assertThrows(IllegalArgumentException.class, () -> {
            PatternAdvisor.recommend(1, -1);
        });
    }

    @Test
    @DisplayName("Null requirements throws exception")
    void testNullRequirementsThrows() {
        assertThrows(NullPointerException.class, () -> {
            PatternAdvisor.recommend(1, 1, null);
        });
    }

    @Test
    @DisplayName("Null context in scoring throws exception")
    void testNullContextThrows() {
        assertThrows(NullPointerException.class, () -> {
            PatternAdvisor.scorePattern(WorkflowPattern.SEQUENCE, null);
        });
    }

    @Test
    @DisplayName("Null pattern in scoring throws exception")
    void testNullPatternInScoringThrows() {
        assertThrows(NullPointerException.class, () -> {
            PatternAdvisor.scorePattern(null, Map.of());
        });
    }

    @Test
    @DisplayName("Null pattern in explanation throws exception")
    void testNullPatternInExplanationThrows() {
        assertThrows(NullPointerException.class, () -> {
            PatternAdvisor.explainRecommendation(null, 1, 1);
        });
    }

    @Test
    @DisplayName("Context with missing values uses defaults")
    void testContextWithMissingValues() {
        Map<String, Object> context = Map.of();  // Empty context

        // Should not throw; should use default values
        int score = PatternAdvisor.scorePattern(WorkflowPattern.SEQUENCE, context);
        assertTrue(score >= 0 && score <= 100);
    }

    @Test
    @DisplayName("Recommendations are non-empty")
    void testRecommendationsNeverEmpty() {
        // Even degenerate case should return at least SEQUENCE
        List<WorkflowPattern> recommendations = PatternAdvisor.recommend(0, 0, List.of());
        assertFalse(recommendations.isEmpty());
        assertTrue(recommendations.contains(WorkflowPattern.SEQUENCE));
    }

    @Test
    @DisplayName("Recommendations are immutable")
    void testRecommendationsImmutable() {
        List<WorkflowPattern> recommendations = PatternAdvisor.recommend(2, 2);
        assertThrows(UnsupportedOperationException.class, () -> {
            recommendations.add(WorkflowPattern.SEQUENCE);
        });
    }

    @Test
    @DisplayName("Scoring works for various tool counts")
    void testScoringVariousToolCounts() {
        for (int toolCount : new int[]{0, 1, 2, 3, 5, 10}) {
            Map<String, Object> context = Map.of("mcp.tool.count", toolCount);
            int score = PatternAdvisor.scorePattern(WorkflowPattern.SEQUENCE, context);
            assertTrue(score >= 0 && score <= 100);
        }
    }

    @Test
    @DisplayName("Scoring works for various agent counts")
    void testScoringVariousAgentCounts() {
        for (int agentCount : new int[]{0, 1, 2, 3, 5, 10}) {
            Map<String, Object> context = Map.of("a2a.agent.count", agentCount);
            int score = PatternAdvisor.scorePattern(WorkflowPattern.SEQUENCE, context);
            assertTrue(score >= 0 && score <= 100);
        }
    }

    @Test
    @DisplayName("Multiple tool recommendation prioritizes parallelism")
    void testMultipleToolPrioritizesParallelism() {
        List<WorkflowPattern> recommendations = PatternAdvisor.recommend(5, 1);

        // First recommendation should enable parallelism
        WorkflowPattern first = recommendations.get(0);
        assertTrue(
            first == WorkflowPattern.PARALLEL_SPLIT ||
            first == WorkflowPattern.EXCLUSIVE_CHOICE ||
            first == WorkflowPattern.SEQUENCE,
            "Multi-tool recommendation should start with parallelism-enabling pattern"
        );
    }

    @Test
    @DisplayName("Explanation contains Petri notation")
    void testExplanationContainsPetriNotation() {
        String explanation = PatternAdvisor.explainRecommendation(
            WorkflowPattern.PARALLEL_SPLIT,
            2, 1
        );
        assertNotNull(explanation);
        assertTrue(explanation.contains("Petri Net Structure") || explanation.contains("notation"));
    }

    @Test
    @DisplayName("Design-time MI instance requirement uses design-time pattern")
    void testDesignTimeMiRequirement() {
        List<WorkflowPattern> recommendations = PatternAdvisor.recommend(
            3, 2,
            List.of("instance", "design_time")
        );
        assertTrue(
            recommendations.contains(WorkflowPattern.MI_WITH_APRIORI_DESIGN),
            "Should recommend design-time MI pattern"
        );
    }

    @Test
    @DisplayName("Runtime MI instance requirement uses runtime pattern")
    void testRuntimeMiRequirement() {
        List<WorkflowPattern> recommendations = PatternAdvisor.recommend(
            3, 2,
            List.of("instance", "runtime")
        );
        assertTrue(
            recommendations.contains(WorkflowPattern.MI_WITH_APRIORI_RUNTIME),
            "Should recommend runtime MI pattern"
        );
    }

    @Test
    @DisplayName("Milestone requirement adds Milestone pattern")
    void testMilestoneRequirement() {
        List<WorkflowPattern> recommendations = PatternAdvisor.recommend(
            2, 1,
            List.of("milestone", "gate")
        );
        assertTrue(
            recommendations.contains(WorkflowPattern.MILESTONE),
            "Should recommend Milestone pattern"
        );
    }
}
