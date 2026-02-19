package org.yawlfoundation.yawl.integration.mcp.zai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.spec.McpSchema;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Registry for Z.AI bridge tools exposed through YAWL MCP server.
 *
 * <p>Provides MCP tool specifications that wrap Z.AI tools for:
 * <ul>
 *   <li>UI-to-YAWL workflow generation</li>
 *   <li>Diagram-to-specification analysis</li>
 *   <li>Web content to workflow conversion</li>
 * </ul>
 *
 * <p><b>Registered Tools:</b>
 * <ul>
 *   <li>{@code zai_generate_workflow_from_ui} - Generate YAWL XML from UI screenshot</li>
 *   <li>{@code zai_analyze_workflow_diagram} - Analyze workflow diagram for spec generation</li>
 *   <li>{@code zai_fetch_workflow_docs} - Fetch and convert web docs to YAWL spec</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class ZaiBridgeToolRegistry {

    private static final Logger _logger = LogManager.getLogger(ZaiBridgeToolRegistry.class);

    private final ZaiMcpBridge bridge;

    /**
     * Create registry with Z.AI bridge.
     *
     * @param bridge the Z.AI MCP bridge to wrap
     */
    public ZaiBridgeToolRegistry(ZaiMcpBridge bridge) {
        this.bridge = bridge;
    }

    /**
     * Get all Z.AI bridge tool specifications.
     *
     * @return list of MCP tool specifications
     */
    public List<McpSchema.Tool> getToolSpecifications() {
        List<McpSchema.Tool> tools = new ArrayList<>();

        tools.add(createGenerateWorkflowFromUiTool());
        tools.add(createAnalyzeWorkflowDiagramTool());
        tools.add(createFetchWorkflowDocsTool());
        tools.add(createSearchWorkflowPatternsTool());
        tools.add(createAnalyzeDataVisualizationTool());
        tools.add(createExtractTextTool());

        _logger.info("Registered {} Z.AI bridge tools", tools.size());
        return tools;
    }

    private McpSchema.Tool createGenerateWorkflowFromUiTool() {
        return new McpSchema.Tool(
            "zai_generate_workflow_from_ui",
            "Generate YAWL XML workflow specification from a UI screenshot. " +
            "Uses Z.AI vision capabilities to analyze the UI and create a corresponding YAWL workflow.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "image_source", Map.of(
                        "type", "string",
                        "description", "Path or URL to the UI screenshot image"
                    ),
                    "workflow_name", Map.of(
                        "type", "string",
                        "description", "Name for the generated workflow"
                    ),
                    "output_format", Map.of(
                        "type", "string",
                        "enum", List.of("xml", "json", "description"),
                        "description", "Output format for the specification (default: xml)"
                    ),
                    "include_data_fields", Map.of(
                        "type", "boolean",
                        "description", "Include data field definitions (default: true)"
                    )
                ),
                "required", List.of("image_source")
            )
        );
    }

    private McpSchema.Tool createAnalyzeWorkflowDiagramTool() {
        return new McpSchema.Tool(
            "zai_analyze_workflow_diagram",
            "Analyze a workflow diagram (flowchart, BPMN, UML) and generate YAWL specification. " +
            "Supports multiple diagram types and extracts tasks, flows, and conditions.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "image_source", Map.of(
                        "type", "string",
                        "description", "Path or URL to the workflow diagram image"
                    ),
                    "diagram_type", Map.of(
                        "type", "string",
                        "enum", List.of("flowchart", "bpmn", "uml", "auto"),
                        "description", "Type of diagram for optimized parsing (default: auto)"
                    ),
                    "extract_conditions", Map.of(
                        "type", "boolean",
                        "description", "Extract conditional logic from diagram (default: true)"
                    ),
                    "target_spec_version", Map.of(
                        "type", "string",
                        "description", "YAWL specification version (default: 4.0)"
                    )
                ),
                "required", List.of("image_source")
            )
        );
    }

    private McpSchema.Tool createFetchWorkflowDocsTool() {
        return new McpSchema.Tool(
            "zai_fetch_workflow_docs",
            "Fetch web documentation and convert to YAWL workflow specification. " +
            "Extracts workflow patterns from process documentation.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "url", Map.of(
                        "type", "string",
                        "description", "URL of the workflow documentation"
                    ),
                    "spec_name", Map.of(
                        "type", "string",
                        "description", "Name for the generated specification"
                    ),
                    "extract_patterns", Map.of(
                        "type", "boolean",
                        "description", "Extract YAWL patterns from documentation (default: true)"
                    ),
                    "timeout_seconds", Map.of(
                        "type", "integer",
                        "description", "Fetch timeout in seconds (default: 30)"
                    )
                ),
                "required", List.of("url")
            )
        );
    }

    private McpSchema.Tool createSearchWorkflowPatternsTool() {
        return new McpSchema.Tool(
            "zai_search_workflow_patterns",
            "Search the web for YAWL workflow patterns and best practices. " +
            "Returns relevant documentation and code examples.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "query", Map.of(
                        "type", "string",
                        "description", "Search query for workflow patterns"
                    ),
                    "max_results", Map.of(
                        "type", "integer",
                        "description", "Maximum results to return (default: 5)"
                    ),
                    "include_code_examples", Map.of(
                        "type", "boolean",
                        "description", "Include code examples in results (default: true)"
                    )
                ),
                "required", List.of("query")
            )
        );
    }

    private McpSchema.Tool createAnalyzeDataVisualizationTool() {
        return new McpSchema.Tool(
            "zai_analyze_data_visualization",
            "Analyze data visualizations (charts, graphs, dashboards) from monitoring data. " +
            "Extracts metrics and trends for workflow decision support.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "image_source", Map.of(
                        "type", "string",
                        "description", "Path or URL to the visualization image"
                    ),
                    "analysis_focus", Map.of(
                        "type", "string",
                        "enum", List.of("trends", "anomalies", "comparisons", "metrics", "comprehensive"),
                        "description", "Focus area for analysis (default: comprehensive)"
                    ),
                    "extract_thresholds", Map.of(
                        "type", "boolean",
                        "description", "Extract threshold values for alerts (default: true)"
                    )
                ),
                "required", List.of("image_source")
            )
        );
    }

    private McpSchema.Tool createExtractTextTool() {
        return new McpSchema.Tool(
            "zai_extract_text",
            "Extract text from screenshots using OCR. Useful for reading error messages, " +
            "terminal output, or documentation images.",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "image_source", Map.of(
                        "type", "string",
                        "description", "Path or URL to the screenshot image"
                    ),
                    "programming_language", Map.of(
                        "type", "string",
                        "description", "Expected programming language if code (optional)"
                    ),
                    "format_output", Map.of(
                        "type", "boolean",
                        "description", "Format extracted text (default: true)"
                    )
                ),
                "required", List.of("image_source")
            )
        );
    }

    /**
     * Execute a Z.AI bridge tool.
     *
     * @param toolName   name of the tool to execute
     * @param parameters tool parameters
     * @return tool result
     */
    public Map<String, Object> executeTool(String toolName, Map<String, Object> parameters) {
        _logger.info("Executing Z.AI bridge tool: {}", toolName);

        String zaiTool = mapToZaiTool(toolName);
        Map<String, Object> zaiParams = transformParameters(toolName, parameters);

        return bridge.callToolSync(zaiTool, zaiParams, bridge.getConfig().getTimeout());
    }

    private String mapToZaiTool(String yawlToolName) {
        return switch (yawlToolName) {
            case "zai_generate_workflow_from_ui" -> "ui_to_artifact";
            case "zai_analyze_workflow_diagram" -> "understand_technical_diagram";
            case "zai_fetch_workflow_docs" -> "web_reader";
            case "zai_search_workflow_patterns" -> "web_search";
            case "zai_analyze_data_visualization" -> "analyze_data_visualization";
            case "zai_extract_text" -> "extract_text_from_screenshot";
            default -> throw new IllegalArgumentException("Unknown tool: " + yawlToolName);
        };
    }

    private Map<String, Object> transformParameters(String toolName, Map<String, Object> params) {
        return switch (toolName) {
            case "zai_generate_workflow_from_ui" -> {
                String prompt = buildWorkflowGenerationPrompt(params);
                yield Map.of(
                    "image_source", params.get("image_source"),
                    "output_type", "code",
                    "prompt", prompt
                );
            }
            case "zai_analyze_workflow_diagram" -> {
                String diagramType = (String) params.getOrDefault("diagram_type", "auto");
                String prompt = "Analyze this " + diagramType + " workflow diagram and extract " +
                               "tasks, flows, conditions, and data fields. Format as structured specification.";
                yield Map.of(
                    "image_source", params.get("image_source"),
                    "diagram_type", diagramType.equals("auto") ? null : diagramType,
                    "prompt", prompt
                );
            }
            case "zai_fetch_workflow_docs" -> Map.of(
                "url", params.get("url"),
                "return_format", "markdown"
            );
            case "zai_search_workflow_patterns" -> Map.of(
                "search_query", "YAWL workflow " + params.get("query"),
                "content_size", "high"
            );
            default -> params;
        };
    }

    private String buildWorkflowGenerationPrompt(Map<String, Object> params) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Generate a YAWL XML workflow specification from this UI screenshot. ");

        String workflowName = (String) params.get("workflow_name");
        if (workflowName != null && !workflowName.isEmpty()) {
            prompt.append("Workflow name: ").append(workflowName).append(". ");
        }

        prompt.append("Include:\n");
        prompt.append("1. Decomposition with proper net elements\n");
        prompt.append("2. Task definitions with input/output ports\n");
        prompt.append("3. Control flow conditions\n");
        prompt.append("4. Cancellation regions where appropriate\n");

        Boolean includeData = (Boolean) params.get("include_data_fields");
        if (includeData == null || includeData) {
            prompt.append("5. Data field definitions\n");
        }

        prompt.append("\nFollow YAWL_Schema4.0.xsd format.");

        return prompt.toString();
    }

    /**
     * Check if the bridge is healthy.
     *
     * @return true if bridge is operational
     */
    public boolean isHealthy() {
        return bridge != null && bridge.isHealthy();
    }
}
