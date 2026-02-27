package org.yawlfoundation.yawl.integration.util;

/**
 * Constants used across the YAWL integration module.
 */
public final class YawlConstants {

    private YawlConstants() {}

    // Project information
    public static final String PROJECT_NAME = "YAWL Integration";
    public static final String PROJECT_VERSION = "6.0.0-GA";
    public static final String YAWL_SCHEMA_VERSION = "2.2";

    // Integration types
    public static final String INTEGRATION_NAMESPACE = "http://www.yawlfoundation.org/yawl/integration";
    public static final String EVENT_TYPE = "YAWLProcessEvent";
    public static final String SIMULATION_TYPE = "YAWLSimulationEvent";
    public static final String REPORT_TYPE = "YAWLReportEvent";

    // Default values
    public static final String DEFAULT_EVENT_SEVERITY = "MEDIUM";
    public static final String DEFAULT_EVENT_TIMESTAMP = "now";

    // URLs
    public static final String YAWL_URL = "http://www.yawlfoundation.org";
    public static final String MCP_ENDPOINT_URL = "http://localhost:8080/mcp";
    public static final String A2A_SERVICE_URL = "http://localhost:8080/a2a";

    // API keys
    public static final String MCP_API_KEY = "yawl-mcp-api-key";
    public static final String A2A_API_KEY = "yawl-a2a-api-key";

    // Configuration
    public static final String MAX_PAYLOAD_SIZE = "1048576"; // 1MB
    public static final String DEFAULT_TIMEOUT_MS = "30000"; // 30 seconds
    public static final String ENABLE_METRICS = "true";
    public static final String METRICS_PORT = "9090";
    public static final String LOG_LEVEL = "INFO";
    public static final String ENABLE_DEBUG = "false";

    // MCP tool names
    public static final String TOOL_ADAPT = "yawl_adapt";
    public static final String TOOL_EVALUATE_EVENT = "yawl_evaluate_event";
    public static final String TOOL_CONFORMANCE = "yawl_conformance";
    public static final String TOOL_LIST_RULES = "yawl_list_adaptation_rules";
}