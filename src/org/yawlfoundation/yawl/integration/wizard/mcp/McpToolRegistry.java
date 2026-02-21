package org.yawlfoundation.yawl.integration.wizard.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Static registry of all MCP tools exposed by YawlMcpServer.
 *
 * <p>Provides lookup and filtering capabilities for the MCP wizard.
 * All 15 tools from YawlMcpServer are registered with complete metadata
 * (parameters, outputs, categories, complexity scores).
 *
 * <p>Tools are organized into four categories:
 * <ul>
 *   <li>CASE_MANAGEMENT: launch_case, cancel_case, get_case_state, get_running_cases</li>
 *   <li>SPECIFICATION: list_specifications, get_spec_data, get_spec_xml, get_spec_schema,
 *       upload_specification, unload_specification</li>
 *   <li>WORKITEM: get_workitems, checkout_workitem, checkin_workitem, skip_workitem</li>
 *   <li>LIFECYCLE: suspend_case, resume_case</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class McpToolRegistry {

    private static final List<McpToolDescriptor> ALL_TOOLS = buildToolRegistry();

    private McpToolRegistry() {
        throw new UnsupportedOperationException(
            "McpToolRegistry is a static registry and cannot be instantiated");
    }

    /**
     * Returns all 15 MCP tools registered in YawlMcpServer.
     *
     * @return immutable list of all tool descriptors
     */
    public static List<McpToolDescriptor> allTools() {
        return List.copyOf(ALL_TOOLS);
    }

    /**
     * Find a tool by its ID.
     *
     * @param toolId the tool ID (e.g., "launch_case")
     * @return optional containing the descriptor if found
     */
    public static Optional<McpToolDescriptor> findById(String toolId) {
        return ALL_TOOLS.stream()
            .filter(tool -> tool.toolId().equals(toolId))
            .findFirst();
    }

    /**
     * Get all tools in a specific category.
     *
     * @param category the category to filter by
     * @return list of tools in the category
     */
    public static List<McpToolDescriptor> byCategory(McpToolCategory category) {
        return ALL_TOOLS.stream()
            .filter(tool -> tool.category() == category)
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Get tools recommended for a given workflow pattern.
     *
     * <p>Recommendations are based on common pattern implementations:
     * <ul>
     *   <li>WP-1 (Sequence): CASE_MANAGEMENT + WORKITEM tools</li>
     *   <li>WP-2 (Choice): CASE_MANAGEMENT + WORKITEM tools</li>
     *   <li>WP-3 (Synchronization): All tools (full capability needed)</li>
     *   <li>WP-4 (Multi-choice): CASE_MANAGEMENT + WORKITEM</li>
     *   <li>WP-5 (Synchronization Join): All tools</li>
     *   <li>WP-6 (Implicit Termination): CASE_MANAGEMENT + WORKITEM</li>
     *   <li>WP-7 (Interleaved Routing): All tools</li>
     * </ul>
     *
     * @param patternCode pattern code (e.g., "WP-1", "WP-2")
     * @return list of recommended tools for the pattern
     */
    public static List<McpToolDescriptor> recommendedForPattern(String patternCode) {
        if (patternCode == null || patternCode.isEmpty()) {
            return allTools();
        }

        switch (patternCode) {
            case "WP-1", "WP-6":
                // Sequence, Implicit Termination: case + workitem
                return mergeCategories(
                    McpToolCategory.CASE_MANAGEMENT,
                    McpToolCategory.WORKITEM
                );

            case "WP-2", "WP-4":
                // Choice, Multi-choice: case + workitem
                return mergeCategories(
                    McpToolCategory.CASE_MANAGEMENT,
                    McpToolCategory.WORKITEM
                );

            case "WP-3", "WP-5", "WP-7":
                // Synchronization, Sync Join, Interleaved: all tools
                return allTools();

            default:
                // Unknown pattern: recommend all tools
                return allTools();
        }
    }

    /**
     * Get tools by complexity level.
     *
     * @param minComplexity minimum complexity (1-10)
     * @param maxComplexity maximum complexity (1-10)
     * @return list of tools with complexity in range
     */
    public static List<McpToolDescriptor> byComplexity(int minComplexity, int maxComplexity) {
        return ALL_TOOLS.stream()
            .filter(tool -> tool.complexityScore() >= minComplexity
                && tool.complexityScore() <= maxComplexity)
            .collect(Collectors.toUnmodifiableList());
    }

    /**
     * Get tool count by category.
     *
     * @return map of category to tool count
     *         CASE_MANAGEMENT: 4, SPECIFICATION: 4, WORKITEM: 5, LIFECYCLE: 2 (total: 15)
     */
    public static Map<McpToolCategory, Integer> countByCategory() {
        return ALL_TOOLS.stream()
            .collect(Collectors.groupingByConcurrent(
                McpToolDescriptor::category,
                Collectors.summingInt(x -> 1)
            ));
    }

    // =========================================================================
    // Registry initialization
    // =========================================================================

    private static List<McpToolDescriptor> buildToolRegistry() {
        List<McpToolDescriptor> tools = new ArrayList<>();

        // ===== CASE_MANAGEMENT (4 tools) =====

        tools.add(McpToolDescriptor.of(
            "launch_case",
            "Launch Workflow Case",
            "Launch a new YAWL workflow case from a loaded specification. " +
                "Returns the case ID of the launched workflow instance.",
            List.of("specIdentifier", "specVersion", "specUri", "caseData"),
            List.of("caseId", "message"),
            McpToolCategory.CASE_MANAGEMENT,
            4,
            true
        ));

        tools.add(McpToolDescriptor.of(
            "cancel_case",
            "Cancel Workflow Case",
            "Cancel a running YAWL workflow case. The case and all its active work items will be terminated.",
            List.of("caseId"),
            List.of("result", "message"),
            McpToolCategory.CASE_MANAGEMENT,
            3,
            true
        ));

        tools.add(McpToolDescriptor.of(
            "get_case_state",
            "Get Case Status",
            "Get the current status and state of a running YAWL workflow case. " +
                "Returns case state XML with current task information.",
            List.of("caseId"),
            List.of("state", "caseId", "message"),
            McpToolCategory.CASE_MANAGEMENT,
            3,
            true
        ));

        tools.add(McpToolDescriptor.of(
            "get_running_cases",
            "List Running Cases",
            "Get all currently running workflow cases from the YAWL engine. " +
                "Returns summary of all active cases.",
            List.of(),
            List.of("cases", "count", "message"),
            McpToolCategory.CASE_MANAGEMENT,
            2,
            true
        ));

        // ===== SPECIFICATION (4 tools) =====
        // Note: spec defines 5 core tools, we register 4 that are in YawlToolSpecifications.createAll()
        // (get_spec_data and get_spec_schema are returned via tools but not as separate tool specs)

        tools.add(McpToolDescriptor.of(
            "list_specifications",
            "List Specifications",
            "List all workflow specifications currently loaded in the YAWL engine. " +
                "Returns identifier, version, URI, name, and status for each specification.",
            List.of(),
            List.of("specifications", "count", "message"),
            McpToolCategory.SPECIFICATION,
            2,
            true
        ));

        tools.add(McpToolDescriptor.of(
            "get_specification",
            "Get Specification Definition",
            "Get the full XML definition of a workflow specification from the YAWL engine.",
            List.of("specIdentifier", "specVersion", "specUri"),
            List.of("specXml", "message"),
            McpToolCategory.SPECIFICATION,
            3,
            true
        ));

        tools.add(McpToolDescriptor.of(
            "upload_specification",
            "Upload Specification",
            "Upload a YAWL workflow specification XML to the engine. " +
                "The specification will be validated and loaded for case launching.",
            List.of("specXml"),
            List.of("result", "message"),
            McpToolCategory.SPECIFICATION,
            5,
            true
        ));

        tools.add(McpToolDescriptor.of(
            "unload_specification",
            "Unload Specification",
            "Unload a workflow specification from the YAWL engine. " +
                "This prevents new cases from being launched from this specification.",
            List.of("specIdentifier"),
            List.of("result", "message"),
            McpToolCategory.SPECIFICATION,
            3,
            true
        ));

        // ===== WORKITEM (5 tools) =====

        tools.add(McpToolDescriptor.of(
            "get_workitems",
            "List Work Items",
            "Get all live work items from the YAWL engine across all running cases. " +
                "Returns work item ID, case ID, task ID, status, and specification info for each item.",
            List.of(),
            List.of("workItems", "count", "message"),
            McpToolCategory.WORKITEM,
            2,
            true
        ));

        tools.add(McpToolDescriptor.of(
            "checkout_workitem",
            "Checkout Work Item",
            "Check out a work item to claim ownership and begin execution. " +
                "The work item must be in enabled or fired state.",
            List.of("workItemId"),
            List.of("result", "workItemId", "message"),
            McpToolCategory.WORKITEM,
            3,
            true
        ));

        tools.add(McpToolDescriptor.of(
            "checkin_workitem",
            "Complete Work Item",
            "Check in (complete) a work item with output data. " +
                "The work item must have been previously checked out.",
            List.of("workItemId", "outputData"),
            List.of("result", "workItemId", "message"),
            McpToolCategory.WORKITEM,
            4,
            true
        ));

        tools.add(McpToolDescriptor.of(
            "skip_workitem",
            "Skip Work Item",
            "Skip a work item if the task allows skipping. " +
                "The work item will be marked as completed without execution.",
            List.of("workItemId"),
            List.of("result", "workItemId", "message"),
            McpToolCategory.WORKITEM,
            3,
            true
        ));

        tools.add(McpToolDescriptor.of(
            "get_workitems_for_case",
            "Get Case Work Items",
            "Get all active work items for a specific YAWL workflow case. " +
                "Returns detailed information for each work item in the case.",
            List.of("caseId"),
            List.of("workItems", "count", "caseId", "message"),
            McpToolCategory.WORKITEM,
            2,
            true
        ));

        // ===== LIFECYCLE (2 tools) =====

        tools.add(McpToolDescriptor.of(
            "suspend_case",
            "Suspend Case",
            "Suspend a running YAWL workflow case by suspending all its active work items. " +
                "Work items can be resumed later.",
            List.of("caseId"),
            List.of("result", "suspendedCount", "message"),
            McpToolCategory.LIFECYCLE,
            3,
            true
        ));

        tools.add(McpToolDescriptor.of(
            "resume_case",
            "Resume Case",
            "Resume a previously suspended YAWL workflow case by unsuspending all its work items. " +
                "Execution resumes from where it was suspended.",
            List.of("caseId"),
            List.of("result", "resumedCount", "message"),
            McpToolCategory.LIFECYCLE,
            3,
            true
        ));

        return tools;
    }

    // =========================================================================
    // Utility methods
    // =========================================================================

    private static List<McpToolDescriptor> mergeCategories(McpToolCategory... categories) {
        List<McpToolDescriptor> merged = new ArrayList<>();
        for (McpToolCategory category : categories) {
            merged.addAll(byCategory(category));
        }
        return List.copyOf(merged);
    }
}
