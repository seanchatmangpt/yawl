/**
 * Model Context Protocol (MCP) server implementation with HTTP/SSE transport support.
 *
 * <p>This package provides MCP server capabilities that expose YAWL workflow
 * operations as MCP tools. The implementation follows the MCP 2025-11-25 specification
 * and uses the official MCP Java SDK v1.0.0-RC1.</p>
 *
 * <h2>Transport Modes</h2>
 * <ul>
 *   <li><strong>STDIO</strong>: Standard input/output for local CLI integration
 *       (default for Claude Desktop, IDEs)</li>
 *   <li><strong>HTTP/SSE</strong>: HTTP with Server-Sent Events for cloud deployment
 *       (via Spring WebMVC integration)</li>
 *   <li><strong>Dual</strong>: Both transports active simultaneously</li>
 * </ul>
 *
 * <h2>Core Classes</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.mcp.a2a.mcp.YawlMcpHttpServer} -
 *       Main MCP server with HTTP/SSE transport support</li>
 *   <li>{@link org.yawlfoundation.yawl.mcp.a2a.mcp.McpTransportConfig} -
 *       Configuration record for transport settings</li>
 *   <li>{@link org.yawlfoundation.yawl.mcp.a2a.mcp.YawlMcpHttpConfiguration} -
 *       Spring Boot auto-configuration for HTTP transport</li>
 *   <li>{@link org.yawlfoundation.yawl.mcp.a2a.mcp.McpHealthEndpoint} -
 *       REST endpoint for health checks and metrics</li>
 * </ul>
 *
 * <h2>HTTP Endpoints</h2>
 * <ul>
 *   <li>{@code GET /mcp/sse} - SSE connection for server-to-client messages</li>
 *   <li>{@code POST /mcp/message?sessionId=...} - Client-to-server JSON-RPC messages</li>
 *   <li>{@code GET /mcp/health} - Health check for load balancers</li>
 *   <li>{@code GET /mcp/health/live} - Kubernetes liveness probe</li>
 *   <li>{@code GET /mcp/health/ready} - Kubernetes readiness probe</li>
 *   <li>{@code GET /mcp/metrics} - Connection metrics and statistics</li>
 * </ul>
 *
 * <h2>MCP Tools Provided (15 total)</h2>
 * <ul>
 *   <li>{@code launch_case} - Start a new workflow case</li>
 *   <li>{@code cancel_case} - Cancel a running case</li>
 *   <li>{@code get_case_state} - Query case status</li>
 *   <li>{@code get_work_items} - List available work items</li>
 *   <li>{@code check_out_work_item} - Claim a work item</li>
 *   <li>{@code check_in_work_item} - Complete a work item</li>
 *   <li>{@code get_specification} - Get specification details</li>
 *   <li>{@code list_specifications} - List all loaded specifications</li>
 *   <li>{@code upload_specification} - Upload a new specification</li>
 *   <li>{@code unload_specification} - Remove a specification</li>
 *   <li>{@code get_case_data} - Get case variable data</li>
 *   <li>{@code set_case_data} - Set case variable data</li>
 *   <li>{@code skip_work_item} - Skip a work item</li>
 *   <li>{@code suspend_case} - Suspend a running case</li>
 *   <li>{@code resume_case} - Resume a suspended case</li>
 * </ul>
 *
 * <h2>MCP Resources (3 static + 3 templates)</h2>
 * <ul>
 *   <li>{@code yawl://specifications} - All loaded specifications</li>
 *   <li>{@code yawl://cases} - All running cases</li>
 *   <li>{@code yawl://workitems} - All live work items</li>
 *   <li>{@code yawl://cases/{caseId}} - Specific case details</li>
 *   <li>{@code yawl://cases/{caseId}/data} - Case variable data</li>
 *   <li>{@code yawl://workitems/{workItemId}} - Work item details</li>
 * </ul>
 *
 * <h2>Configuration Example</h2>
 * <pre>{@code
 * # application.yml
 * yawl:
 *   mcp:
 *     http:
 *       enabled: true
 *       port: 8081
 *       path: /mcp
 *       sse-path: /sse
 *       message-path: /message
 *       max-connections: 100
 *       connection-timeout-seconds: 300
 *       enable-stdio: true
 *       enable-health-check: true
 *       heartbeat-interval-seconds: 30
 * }</pre>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Programmatic usage
 * McpTransportConfig config = McpTransportConfig.defaults();
 * YawlMcpHttpServer server = new YawlMcpHttpServer(
 *     "http://localhost:8080/yawl", "admin", "password", config);
 * server.start();
 *
 * // Get router for Spring integration
 * RouterFunction<ServerResponse> router = server.getRouterFunction();
 *
 * // When shutting down:
 * server.stop();
 * }</pre>
 *
 * @see io.modelcontextprotocol.server.McpSyncServer
 * @see io.modelcontextprotocol.server.transport.WebMvcSseServerTransportProvider
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.mcp.a2a.mcp;
