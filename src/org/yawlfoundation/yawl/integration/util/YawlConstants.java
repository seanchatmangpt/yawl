package org.yawlfoundation.yawl.integration.util;

import java.time.Duration;
import java.util.Set;

/**
 * A utility class containing shared constants used across A2A skills and MCP tools.
 * This class provides centralized definitions for common permissions, tags,
 * timeout values, event types, and MCP tool names.
 *
 * @since YAWL v6.0.0
 */
public final class YawlConstants {

    /**
     * Private constructor to prevent instantiation of utility class.
     */
    private YawlConstants() {}

    // ─── Permissions ─────────────────────────────────────────────────────────

    /**
     * Permission for A2A client operations.
     */
    public static final String PERMISSION_A2A_CLIENT = "a2a-client";

    /**
     * Permission for MCP server operations.
     */
    public static final String PERMISSION_MCP_SERVER = "mcp-server";

    /**
     * Administrator permission.
     */
    public static final String PERMISSION_ADMIN = "admin";

    /**
     * Permission for executing workflow tasks.
     */
    public static final String PERMISSION_WORKFLOW_EXECUTE = "workflow-execute";

    /**
     * Permission for workflow administration.
     */
    public static final String PERMISSION_WORKFLOW_ADMIN = "workflow-admin";

    /**
     * Set of default permissions assigned to new users.
     */
    public static final Set<String> DEFAULT_PERMISSIONS =
        Set.of(PERMISSION_A2A_CLIENT);

    // ─── Skill/Tool Tags ─────────────────────────────────────────────────────

    /**
     * Tag for synthesis-related skills/tools.
     */
    public static final String TAG_SYNTHESIS = "synthesis";

    /**
     * Tag for mining-related skills/tools.
     */
    public static final String TAG_MINING = "mining";

    /**
     * Tag for conformance checking skills/tools.
     */
    public static final String TAG_CONFORMANCE = "conformance";

    /**
     * Tag for adaptation-related skills/tools.
     */
    public static final String TAG_ADAPTATION = "adaptation";

    /**
     * Tag for temporal analysis skills/tools.
     */
    public static final String TAG_TEMPORAL = "temporal";

    /**
     * Tag for process intelligence skills/tools.
     */
    public static final String TAG_PROCESS_INTELLIGENCE = "process-intelligence";

    // ─── Default Timeouts ────────────────────────────────────────────────────

    /**
     * Default timeout for most operations (30 seconds).
     */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Short timeout for quick operations (5 seconds).
     */
    public static final Duration SHORT_TIMEOUT = Duration.ofSeconds(5);

    /**
     * Long timeout for complex operations (5 minutes).
     */
    public static final Duration LONG_TIMEOUT = Duration.ofMinutes(5);

    /**
     * Default maximum time for operations in seconds (5 minutes).
     */
    public static final int DEFAULT_MAX_SECONDS = 300;

    // ─── Event Types ─────────────────────────────────────────────────────────

    /**
     * Event type for when a case (workflow instance) is started.
     */
    public static final String EVENT_TYPE_CASE_STARTED = "case-started";

    /**
     * Event type for when a case (workflow instance) is completed.
     */
    public static final String EVENT_TYPE_CASE_COMPLETED = "case-completed";

    /**
     * Event type for when a task becomes enabled (ready to be allocated).
     */
    public static final String EVENT_TYPE_TASK_ENABLED = "task-enabled";

    /**
     * Event type for when a task is started (begins execution).
     */
    public static final String EVENT_TYPE_TASK_STARTED = "task-started";

    /**
     * Event type for when a task is completed (execution finished).
     */
    public static final String EVENT_TYPE_TASK_COMPLETED = "task-completed";

    /**
     * Event type for when a work item is created.
     */
    public static final String EVENT_TYPE_WORKITEM_CREATED = "workitem-created";

    /**
     * Set of all valid event types for event subscriptions and processing.
     */
    public static final Set<String> VALID_EVENT_TYPES = Set.of(
        EVENT_TYPE_CASE_STARTED,
        EVENT_TYPE_CASE_COMPLETED,
        EVENT_TYPE_TASK_ENABLED,
        EVENT_TYPE_TASK_STARTED,
        EVENT_TYPE_TASK_COMPLETED,
        EVENT_TYPE_WORKITEM_CREATED
    );

    // ─── Size Limits ─────────────────────────────────────────────────────────

    /**
     * Maximum size for data payloads in bytes (10MB).
     */
    public static final int MAX_DATA_SIZE_BYTES = 10 * 1024 * 1024; // 10MB

    /**
     * Maximum size for sample data in bytes (2KB).
     */
    public static final int MAX_SAMPLE_SIZE_BYTES = 2 * 1024; // 2KB

    /**
     * Maximum size for XES event logs in bytes (50MB).
     */
    public static final int MAX_XES_SIZE_BYTES = 50 * 1024 * 1024; // 50MB

    // ─── MCP Tool Names ──────────────────────────────────────────────────────

    /**
     * MCP tool name for synthesizing new workflows.
     */
    public static final String TOOL_SYNTHESIZE = "yawl_synthesize";

    /**
     * MCP tool name for mining patterns from XES logs.
     */
    public static final String TOOL_MINE_FROM_XES = "yawl_mine_from_xes";

    /**
     * MCP tool name for checking conformance of workflows.
     */
    public static final String TOOL_CHECK_CONFORMANCE = "yawl_check_conformance";

    /**
     * MCP tool name for adapting workflows.
     */
    public static final String TOOL_ADAPT = "yawl_adapt";

    /**
     * MCP tool name for temporal analysis of workflows.
     */
    public static final String TOOL_TEMPORAL_FORK = "yawl_temporal_fork";

    /**
     * MCP tool name for OCEL conversion operations.
     */
    public static final String TOOL_OCEL_CONVERT = "yawl_ocel_convert";

    /**
     * MCP tool name for evaluating events.
     */
    public static final String TOOL_EVALUATE_EVENT = "yawl_evaluate_event";

    /**
     * MCP tool name for conformance checking.
     */
    public static final String TOOL_CONFORMANCE = "yawl_conformance";

    /**
     * MCP tool name for listing adaptation rules.
     */
    public static final String TOOL_LIST_RULES = "yawl_list_adaptation_rules";

    /**
     * MCP tool name for extracting footprint.
     */
    public static final String TOOL_EXTRACT_FOOTPRINT = "yawl_extract_footprint";

    /**
     * MCP tool name for comparing conformance.
     */
    public static final String TOOL_COMPARE_CONFORMANCE = "yawl_compare_conformance";
}