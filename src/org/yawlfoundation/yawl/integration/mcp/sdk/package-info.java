/**
 * MCP SDK Bridge API for YAWL.
 *
 * <p>This package defines the Model Context Protocol (MCP) type system used by YAWL's
 * MCP integration layer. These classes implement the MCP protocol specification directly,
 * providing a complete, self-contained MCP type library that exactly mirrors the official
 * MCP Java SDK API surface ({@code io.modelcontextprotocol.sdk:mcp-core}).</p>
 *
 * <h2>Design Rationale</h2>
 * <p>The official MCP Java SDK ({@code io.modelcontextprotocol:mcp}) is published by
 * the Model Context Protocol organization and follows the MCP specification defined at
 * https://modelcontextprotocol.io/. At the time of the YAWL 6.0 release, the SDK is
 * distributed via GitHub packages rather than Maven Central.</p>
 *
 * <p>This package provides a complete implementation of the MCP type system, enabling
 * YAWL to implement and validate the full MCP protocol without a separate SDK dependency.
 * When the official SDK becomes available on Maven Central, the migration path is:</p>
 * <ol>
 *   <li>Add {@code io.modelcontextprotocol:mcp} dependency to yawl-integration/pom.xml</li>
 *   <li>Update all imports from this package to {@code io.modelcontextprotocol.sdk.*}</li>
 *   <li>Delete this package</li>
 * </ol>
 *
 * <h2>Package Contents</h2>
 * <ul>
 *   <li>{@link org.yawlfoundation.yawl.integration.mcp.sdk.McpSchema} - Core MCP schema types
 *       (Tool, Resource, Prompt, ServerCapabilities, etc.)</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.mcp.sdk.McpServerFeatures} - Server feature
 *       specifications for tools, resources, prompts, and completions</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.mcp.sdk.McpServer} - Server builder factory</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.mcp.sdk.McpSyncServer} - Synchronous server
 *       lifecycle interface</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.mcp.sdk.McpSyncServerExchange} - Per-request
 *       exchange context interface</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.mcp.sdk.StdioServerTransportProvider} - STDIO
 *       transport implementation</li>
 *   <li>{@link org.yawlfoundation.yawl.integration.mcp.sdk.JacksonMcpJsonMapper} - Jackson
 *       JSON serialization for MCP protocol messages</li>
 * </ul>
 *
 * <h2>MCP SDK Official Location</h2>
 * <ul>
 *   <li>GitHub: https://github.com/modelcontextprotocol/java-sdk</li>
 *   <li>Spec: https://modelcontextprotocol.io/specification</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
package org.yawlfoundation.yawl.integration.mcp.sdk;
