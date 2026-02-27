/**
 * MCP-ZAI Bridge for YAWL self-upgrading codebase.
 *
 * <p>This package provides a bridge between the YAWL MCP server and Z.AI
 * MCP tools, enabling:
 * <ul>
 *   <li>UI-to-YAWL workflow generation via {@code ui_to_artifact}</li>
 *   <li>Diagram analysis for workflow specifications</li>
 *   <li>Web content fetching for workflow documentation</li>
 * </ul>
 *
 * <p><b>Architecture:</b>
 * <pre>
 * ┌─────────────────┐     ┌──────────────────┐     ┌─────────────────┐
 * │  YawlMcpServer  │────►│  ZaiMcpBridge    │────►│  Z.AI MCP Tools │
 * │  (STDIO)        │     │  (STDIO/HTTP)    │     │  (Remote)       │
 * └─────────────────┘     └──────────────────┘     └─────────────────┘
 * </pre>
 *
 * <p><b>Configuration:</b>
 * <pre>
 * yawl:
 *   mcp:
 *     zai-bridge:
 *       enabled: true
 *       mode: stdio
 *       timeout-ms: 30000
 *       cache-results: true
 * </pre>
 *
 * @author YAWL Foundation
 * @version 5.2
 * @see org.yawlfoundation.yawl.integration.mcp.YawlMcpServer
 */
package org.yawlfoundation.yawl.integration.mcp.zai;
