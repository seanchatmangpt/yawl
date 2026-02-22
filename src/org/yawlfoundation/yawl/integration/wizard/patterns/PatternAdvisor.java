package org.yawlfoundation.yawl.integration.wizard.patterns;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Autonomic pattern advisor for workflow pattern selection.
 *
 * <p>Recommends van der Aalst workflow patterns based on the number and
 * capabilities of available MCP tools and A2A agents, following van der Aalst's
 * workflow pattern selection guidelines.
 *
 * <p>The advisor implements a rules-based recommendation engine that:
 * <ul>
 *   <li>Analyzes the topology of MCP tools and A2A agents</li>
 *   <li>Evaluates user-specified requirements</li>
 *   <li>Recommends patterns best suited to the specific configuration</li>
 *   <li>Provides explanations for recommendations</li>
 * </ul>
 *
 * <p>Recommendations follow these principles:
 * <ul>
 *   <li>Sequential workflows (1 tool): SEQUENCE</li>
 *   <li>Parallel workflows (2+ tools): PARALLEL_SPLIT + SYNCHRONIZATION</li>
 *   <li>Conditional routing (decision points): EXCLUSIVE_CHOICE + SIMPLE_MERGE</li>
 *   <li>Optional parallel paths: MULTI_CHOICE</li>
 *   <li>Repeated tasks (loops): ARBITRARY_CYCLES</li>
 *   <li>Multiple agent instances: MI_WITH_APRIORI_DESIGN or MI_WITH_APRIORI_RUNTIME</li>
 *   <li>Cancellable workflows: CANCEL_TASK or CANCEL_CASE</li>
 * </ul>
 *
 * @see WorkflowPattern for pattern definitions
 * @see WorkflowPatternCatalog for pattern catalog operations
 */
public final class PatternAdvisor {

    /**
     * Private constructor: this is a utility class with static methods only.
     */
    private PatternAdvisor() {
        throw new AssertionError("Cannot instantiate PatternAdvisor");
    }

    /**
     * Recommends workflow patterns for a given configuration of MCP tools and A2A agents.
     *
     * <p>Returns a prioritized list of patterns, with the most appropriate first.
     * The list is ordered by recommendation strength; later patterns are viable
     * alternatives or complementary patterns.
     *
     * @param mcpToolCount the number of available MCP tools
     * @param a2aAgentCount the number of available A2A agents
     * @param requirements optional list of functional requirements (e.g., "loops", "cancellation")
     * @return immutable list of recommended patterns, ordered by priority
     * @throws IllegalArgumentException if tool/agent counts are negative
     * @throws NullPointerException if requirements is null
     */
    public static List<WorkflowPattern> recommend(
        int mcpToolCount,
        int a2aAgentCount,
        List<String> requirements
    ) {
        if (mcpToolCount < 0) {
            throw new IllegalArgumentException("mcpToolCount cannot be negative");
        }
        if (a2aAgentCount < 0) {
            throw new IllegalArgumentException("a2aAgentCount cannot be negative");
        }
        Objects.requireNonNull(requirements, "requirements cannot be null");

        List<WorkflowPattern> recommendations = new ArrayList<>();

        // Base recommendation on tool/agent topology
        if (mcpToolCount == 0 && a2aAgentCount == 0) {
            // No tools/agents: degenerate case
            recommendations.add(WorkflowPattern.SEQUENCE);
        } else if (mcpToolCount == 1 && a2aAgentCount <= 1) {
            // Single tool/agent: sequential execution
            recommendations.add(WorkflowPattern.SEQUENCE);
        } else if (mcpToolCount >= 2 || a2aAgentCount >= 2) {
            // Multiple tools/agents: enable parallelism
            recommendations.add(WorkflowPattern.PARALLEL_SPLIT);
            recommendations.add(WorkflowPattern.SYNCHRONIZATION);

            // If agents present, consider deferred choice (event-driven routing)
            if (a2aAgentCount >= 2) {
                recommendations.add(WorkflowPattern.DEFERRED_CHOICE);
            }
        }

        // Add conditional routing patterns
        if (mcpToolCount >= 2 || requirements.stream().anyMatch(r -> r.contains("condition") || r.contains("choice"))) {
            if (!recommendations.contains(WorkflowPattern.EXCLUSIVE_CHOICE)) {
                recommendations.add(WorkflowPattern.EXCLUSIVE_CHOICE);
            }
            if (!recommendations.contains(WorkflowPattern.SIMPLE_MERGE)) {
                recommendations.add(WorkflowPattern.SIMPLE_MERGE);
            }
        }

        // Add multi-choice if multiple conditional paths
        if (mcpToolCount >= 3 && requirements.stream().anyMatch(r -> r.contains("optional") || r.contains("multi"))) {
            if (!recommendations.contains(WorkflowPattern.MULTI_CHOICE)) {
                recommendations.add(WorkflowPattern.MULTI_CHOICE);
            }
        }

        // Add loop patterns if required
        if (requirements.stream().anyMatch(r -> r.contains("loop") || r.contains("cycle") || r.contains("repeat"))) {
            if (!recommendations.contains(WorkflowPattern.ARBITRARY_CYCLES)) {
                recommendations.add(WorkflowPattern.ARBITRARY_CYCLES);
            }
        }

        // Add multiple instance patterns if required
        if (requirements.stream().anyMatch(r -> r.contains("instance") || r.contains("multiple") || r.contains("parallel_instances"))) {
            if (a2aAgentCount >= 2 || mcpToolCount >= 3) {
                if (requirements.stream().anyMatch(r -> r.contains("design_time"))) {
                    recommendations.add(WorkflowPattern.MI_WITH_APRIORI_DESIGN);
                } else if (requirements.stream().anyMatch(r -> r.contains("runtime"))) {
                    recommendations.add(WorkflowPattern.MI_WITH_APRIORI_RUNTIME);
                } else {
                    recommendations.add(WorkflowPattern.MI_WITH_APRIORI_DESIGN);
                    recommendations.add(WorkflowPattern.MI_WITH_APRIORI_RUNTIME);
                }
            }
        }

        // Add cancellation patterns if required
        if (requirements.stream().anyMatch(r -> r.contains("cancel") || r.contains("abort") || r.contains("timeout"))) {
            if (!recommendations.contains(WorkflowPattern.CANCEL_TASK)) {
                recommendations.add(WorkflowPattern.CANCEL_TASK);
            }
            if (!recommendations.contains(WorkflowPattern.CANCEL_CASE)) {
                recommendations.add(WorkflowPattern.CANCEL_CASE);
            }
        }

        // Add milestone if conditional enablement required
        if (requirements.stream().anyMatch(r -> r.contains("milestone") || r.contains("gate") || r.contains("barrier"))) {
            if (!recommendations.contains(WorkflowPattern.MILESTONE)) {
                recommendations.add(WorkflowPattern.MILESTONE);
            }
        }

        // Ensure recommendations are immutable and non-empty
        if (recommendations.isEmpty()) {
            recommendations.add(WorkflowPattern.SEQUENCE);
        }

        return Collections.unmodifiableList(recommendations);
    }

    /**
     * Recommends patterns with default (empty) requirements.
     *
     * @param mcpToolCount the number of available MCP tools
     * @param a2aAgentCount the number of available A2A agents
     * @return immutable list of recommended patterns
     */
    public static List<WorkflowPattern> recommend(int mcpToolCount, int a2aAgentCount) {
        return recommend(mcpToolCount, a2aAgentCount, List.of());
    }

    /**
     * Scores a pattern for suitability in a specific configuration context.
     *
     * <p>Returns a composite score (0-100) based on:
     * <ul>
     *   <li>MCP suitability (40% weight)</li>
     *   <li>A2A suitability (40% weight)</li>
     *   <li>Category appropriateness (20% weight)</li>
     * </ul>
     *
     * @param pattern the pattern to score
     * @param context configuration context (e.g., "mcp.tool.count", "a2a.agent.count", "requirements")
     * @return score from 0-100
     * @throws NullPointerException if pattern or context is null
     */
    public static int scorePattern(WorkflowPattern pattern, Map<String, Object> context) {
        Objects.requireNonNull(pattern, "pattern cannot be null");
        Objects.requireNonNull(context, "context cannot be null");

        int mcpToolCount = getIntFromContext(context, "mcp.tool.count", 1);
        int a2aAgentCount = getIntFromContext(context, "a2a.agent.count", 1);
        @SuppressWarnings("unchecked")
        List<String> requirements = (List<String>) context.getOrDefault("requirements", List.of());

        int score = 0;

        // MCP suitability contribution (40% weight, max 40 points)
        score += (pattern.getMcpSuitability() * 4);

        // A2A suitability contribution (40% weight, max 40 points)
        score += (pattern.getA2aSuitability() * 4);

        // Category appropriateness contribution (20% weight, max 20 points)
        score += categoryScore(pattern, mcpToolCount, a2aAgentCount, requirements);

        return Math.min(score, 100);  // Cap at 100
    }

    /**
     * Provides a human-readable explanation for a pattern recommendation.
     *
     * <p>Explains why the pattern is suitable for the given configuration.
     *
     * @param pattern the recommended pattern
     * @param mcpToolCount the number of available MCP tools
     * @param a2aAgentCount the number of available A2A agents
     * @return explanation string
     * @throws NullPointerException if pattern is null
     */
    public static String explainRecommendation(
        WorkflowPattern pattern,
        int mcpToolCount,
        int a2aAgentCount
    ) {
        Objects.requireNonNull(pattern, "pattern cannot be null");

        StringBuilder explanation = new StringBuilder();
        explanation.append(pattern.getLabel()).append(" (").append(pattern.getCode()).append(") ");
        explanation.append("is recommended because:\n\n");

        explanation.append("Pattern Definition:\n");
        explanation.append("  ").append(pattern.getDescription()).append("\n\n");

        explanation.append("Configuration Match:\n");
        explanation.append(String.format("  - MCP tools available: %d\n", mcpToolCount));
        explanation.append(String.format("  - A2A agents available: %d\n", a2aAgentCount));
        explanation.append(String.format("  - Pattern MCP suitability: %d/10\n", pattern.getMcpSuitability()));
        explanation.append(String.format("  - Pattern A2A suitability: %d/10\n", pattern.getA2aSuitability()));

        explanation.append("\nPetri Net Structure:\n");
        explanation.append("  ").append(pattern.getPetriNotation()).append("\n");

        if (pattern.isSuitableForMcp()) {
            explanation.append("\nThis pattern is well-suited for MCP tool orchestration.\n");
        }
        if (pattern.isSuitableForA2a()) {
            explanation.append("This pattern is well-suited for A2A agent coordination.\n");
        }

        return explanation.toString();
    }

    /**
     * Gets the top N recommended patterns for a configuration.
     *
     * @param mcpToolCount the number of available MCP tools
     * @param a2aAgentCount the number of available A2A agents
     * @param topN number of top recommendations to return
     * @param requirements optional functional requirements
     * @return immutable list of top N recommended patterns
     */
    public static List<WorkflowPattern> topRecommendations(
        int mcpToolCount,
        int a2aAgentCount,
        int topN,
        List<String> requirements
    ) {
        List<WorkflowPattern> allRecommended = recommend(mcpToolCount, a2aAgentCount, requirements);
        int limit = Math.min(topN, allRecommended.size());
        return Collections.unmodifiableList(allRecommended.subList(0, limit));
    }

    /**
     * Evaluates category appropriateness for a pattern in a specific configuration.
     */
    private static int categoryScore(
        WorkflowPattern pattern,
        int mcpToolCount,
        int a2aAgentCount,
        List<String> requirements
    ) {
        PatternCategory category = pattern.getCategory();
        int score = 0;

        switch (category) {
            case BASIC:
                // Basic patterns score high for any configuration
                score = 18;
                break;
            case ADVANCED_BRANCHING:
                // Advanced patterns score high for multi-tool/agent configs
                if (mcpToolCount >= 2 || a2aAgentCount >= 2) {
                    score = 18;
                } else {
                    score = 5;
                }
                break;
            case STRUCTURAL:
                // Structural patterns score high if loops/cycles required
                if (requirements.stream().anyMatch(r -> r.contains("loop") || r.contains("cycle"))) {
                    score = 18;
                } else {
                    score = 5;
                }
                break;
            case MULTIPLE_INSTANCES:
                // MI patterns score high if multiple instances required
                if (requirements.stream().anyMatch(r -> r.contains("instance") || r.contains("multiple"))) {
                    score = 18;
                } else {
                    score = 3;
                }
                break;
            case STATE_BASED:
                // State-based patterns score high for event-driven configs (A2A agents)
                if (a2aAgentCount >= 2) {
                    score = 18;
                } else {
                    score = 8;
                }
                break;
            case CANCELLATION:
                // Cancellation patterns score high if cancellation/abort required
                if (requirements.stream().anyMatch(r -> r.contains("cancel") || r.contains("abort"))) {
                    score = 18;
                } else {
                    score = 3;
                }
                break;
        }

        return Math.min(score, 20);  // Cap at 20 (max category score)
    }

    /**
     * Helper: retrieves an integer value from context map with default fallback.
     */
    private static int getIntFromContext(Map<String, Object> context, String key, int defaultValue) {
        Object value = context.get(key);
        if (value instanceof Integer) {
            return (Integer) value;
        }
        return defaultValue;
    }
}
