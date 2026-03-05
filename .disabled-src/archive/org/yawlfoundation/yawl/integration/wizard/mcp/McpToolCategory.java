package org.yawlfoundation.yawl.integration.wizard.mcp;

/**
 * Functional categories for MCP tools exposed by YAWL.
 *
 * <p>Tools are grouped into four main categories based on their operational focus.
 * This categorization supports tool recommendation and matching in the wizard.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public enum McpToolCategory {
    /**
     * Tools for launching, cancelling, and managing workflow cases.
     * Includes: launch_case, cancel_case, get_case_state, get_running_cases.
     */
    CASE_MANAGEMENT,

    /**
     * Tools for managing workflow specifications.
     * Includes: list_specifications, get_spec_data (XML/schema), upload_specification, unload_specification.
     */
    SPECIFICATION,

    /**
     * Tools for managing work items (tasks).
     * Includes: get_workitems, complete_workitem, checkout_workitem, checkin_workitem, skip_workitem.
     */
    WORKITEM,

    /**
     * Tools for case lifecycle control (suspend, resume).
     * Includes: suspend_case, resume_case.
     */
    LIFECYCLE
}
