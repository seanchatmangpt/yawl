package org.yawlfoundation.yawl.integration.wizard.mcp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Matches available MCP tools to workflow task slots in a pattern.
 *
 * <p>Implements autonomic capability matching based on tool metadata.
 * Follows van der Aalst's resource pattern RP-1 (Direct Allocation) —
 * tools are allocated directly to tasks based on capability compatibility.
 *
 * <p>Matching strategy:
 * <ul>
 *   <li>For each task slot in the pattern, find the best matching tool</li>
 *   <li>Score tools based on category fit, complexity, and keyword matching</li>
 *   <li>Prefer tools with exact category match and lower complexity (easier to configure)</li>
 *   <li>Record scoring rationale for transparency</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class McpCapabilityMatcher {

    /**
     * Match tools to task slots for a given pattern.
     *
     * <p>Returns a mapping from task slot name to the best matching tool descriptor.
     * If no suitable tool can be matched to a task slot, that slot is omitted from
     * the result (partial matching is allowed).
     *
     * @param patternCode the workflow pattern code (e.g., "WP-1", "WP-2")
     * @param availableTools list of available MCP tool descriptors
     * @param requirements list of user-specified requirements or constraints
     * @return map from task slot name to best matching tool descriptor
     * @throws NullPointerException if any parameter is null
     */
    public Map<String, McpToolDescriptor> match(
            String patternCode,
            List<McpToolDescriptor> availableTools,
            List<String> requirements) {

        Objects.requireNonNull(patternCode, "patternCode cannot be null");
        Objects.requireNonNull(availableTools, "availableTools cannot be null");
        Objects.requireNonNull(requirements, "requirements cannot be null");

        Map<String, McpToolDescriptor> result = new HashMap<>();

        // Get task slots for pattern
        List<String> taskSlots = getTaskSlotsForPattern(patternCode);

        // For each task slot, find best matching tool
        for (String taskSlot : taskSlots) {
            Optional<McpToolDescriptor> bestMatch = findBestMatch(
                taskSlot, availableTools, requirements);

            if (bestMatch.isPresent()) {
                result.put(taskSlot, bestMatch.get());
            }
        }

        return result;
    }

    /**
     * Score a tool's suitability for a task slot (0-100).
     *
     * <p>Scoring considers:
     * <ul>
     *   <li>Category match: exact category match in description (40 points)</li>
     *   <li>Keyword match: task slot name keywords appear in tool description (30 points)</li>
     *   <li>Complexity: lower complexity is preferred (20 points, max 20)</li>
     *   <li>Requirements match: tool meets user requirements (10 points)</li>
     * </ul>
     *
     * @param tool the tool to score
     * @param taskSlot the task slot name/description
     * @param requirements list of user requirements
     * @return score 0-100 (higher is better)
     */
    public int score(McpToolDescriptor tool, String taskSlot, List<String> requirements) {
        int score = 0;

        // 1. Category matching (40 points max)
        if (matchesTaskCategory(tool.category(), taskSlot)) {
            score += 40;
        } else if (relatedCategory(tool.category(), taskSlot)) {
            score += 20;
        }

        // 2. Keyword matching (30 points max)
        int keywordMatches = countKeywordMatches(tool, taskSlot);
        score += Math.min(30, keywordMatches * 10);

        // 3. Complexity (20 points max, inversely scored)
        // Lower complexity is better (score higher for lower complexity)
        int complexityScore = Math.max(0, 20 - (tool.complexityScore() - 1) * 2);
        score += complexityScore;

        // 4. Requirements matching (10 points max)
        if (meetsRequirements(tool, requirements)) {
            score += 10;
        }

        return Math.min(100, score);
    }

    /**
     * Explain a match decision with human-readable rationale.
     *
     * <p>Provides a detailed explanation of why a tool was selected for a task,
     * including score comparisons with other candidates.
     *
     * @param taskSlot the task slot name
     * @param selected the selected tool
     * @param candidates list of candidate tools considered
     * @return human-readable explanation string
     */
    public String explainMatch(String taskSlot, McpToolDescriptor selected,
                               List<McpToolDescriptor> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("Match explanation for task slot: ").append(taskSlot).append("\n");
        sb.append("Selected: ").append(selected.displayName());
        sb.append(" [").append(selected.toolId()).append("]\n");
        sb.append("Category: ").append(selected.category()).append("\n");
        sb.append("Complexity: ").append(selected.complexityScore()).append("/10\n\n");

        if (candidates.size() > 1) {
            sb.append("Candidates ranked by suitability:\n");
            List<McpToolDescriptor> ranked = candidates.stream()
                .sorted((a, b) -> Integer.compare(
                    score(b, taskSlot, List.of()),
                    score(a, taskSlot, List.of())))
                .collect(Collectors.toList());

            for (int i = 0; i < ranked.size(); i++) {
                McpToolDescriptor tool = ranked.get(i);
                int matchScore = score(tool, taskSlot, List.of());
                sb.append(String.format("  %d. %s (%s) - Score: %d/100\n",
                    i + 1, tool.displayName(), tool.toolId(), matchScore));
            }
        }

        return sb.toString();
    }

    // =========================================================================
    // Matching logic
    // =========================================================================

    private Optional<McpToolDescriptor> findBestMatch(
            String taskSlot,
            List<McpToolDescriptor> availableTools,
            List<String> requirements) {

        return availableTools.stream()
            .max((a, b) -> Integer.compare(
                score(a, taskSlot, requirements),
                score(b, taskSlot, requirements)));
    }

    private List<String> getTaskSlotsForPattern(String patternCode) {
        return switch (patternCode) {
            case "WP-1":
                // Sequence: launch, execute, complete
                yield List.of("launch", "execute", "complete");
            case "WP-2":
                // Choice: launch, choose, execute
                yield List.of("launch", "route", "execute");
            case "WP-3":
                // Parallel: launch, parallel_task_1, parallel_task_2, sync
                yield List.of("launch", "parallel_1", "parallel_2", "synchronize");
            case "WP-4":
                // Multi-choice: launch, multi_route, execute
                yield List.of("launch", "route", "execute");
            case "WP-5":
                // Sync Join: launch, parallel_1, parallel_2, join
                yield List.of("launch", "parallel_1", "parallel_2", "join");
            case "WP-6":
                // Implicit Termination: launch, execute, complete
                yield List.of("launch", "execute", "complete");
            case "WP-7":
                // Interleaved Routing: launch, interleave, execute
                yield List.of("launch", "interleave", "execute");
            default:
                yield List.of("launch", "execute", "complete");
        };
    }

    private boolean matchesTaskCategory(McpToolCategory toolCategory, String taskSlot) {
        String lower = taskSlot.toLowerCase();

        return switch (toolCategory) {
            case CASE_MANAGEMENT -> lower.contains("launch") || lower.contains("case") ||
                                   lower.contains("cancel") || lower.contains("suspend") ||
                                   lower.contains("resume");
            case SPECIFICATION -> lower.contains("spec") || lower.contains("upload") ||
                                 lower.contains("definition") || lower.contains("schema");
            case WORKITEM -> lower.contains("execute") || lower.contains("task") ||
                            lower.contains("complete") || lower.contains("work") ||
                            lower.contains("item") || lower.contains("checkin") ||
                            lower.contains("checkout");
            case LIFECYCLE -> lower.contains("suspend") || lower.contains("resume") ||
                             lower.contains("lifecycle");
        };
    }

    private boolean relatedCategory(McpToolCategory toolCategory, String taskSlot) {
        String lower = taskSlot.toLowerCase();

        return switch (toolCategory) {
            case CASE_MANAGEMENT -> lower.contains("sync") || lower.contains("join");
            case SPECIFICATION -> lower.contains("route");
            case WORKITEM -> lower.contains("route") || lower.contains("decide");
            case LIFECYCLE -> lower.contains("control");
        };
    }

    private int countKeywordMatches(McpToolDescriptor tool, String taskSlot) {
        String[] slotWords = taskSlot.toLowerCase().split("_");
        String toolDesc = (tool.description() + " " + tool.displayName()).toLowerCase();

        int matches = 0;
        for (String word : slotWords) {
            if (!word.isEmpty() && toolDesc.contains(word)) {
                matches++;
            }
        }
        return matches;
    }

    private boolean meetsRequirements(McpToolDescriptor tool, List<String> requirements) {
        if (requirements.isEmpty()) {
            return true;
        }

        String toolInfo = (tool.description() + " " + tool.displayName() +
                          " " + tool.category()).toLowerCase();

        return requirements.stream()
            .allMatch(req -> toolInfo.contains(req.toLowerCase()));
    }
}
