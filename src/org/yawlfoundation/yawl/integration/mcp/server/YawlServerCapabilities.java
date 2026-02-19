package org.yawlfoundation.yawl.integration.mcp.server;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * YAWL MCP Server Capabilities Configuration.
 *
 * Provides factory methods for creating server capabilities configurations
 * that declare what features the YAWL MCP server supports to connected clients.
 *
 * MCP capabilities in SDK 0.18.0 (MCP 2025-11-25 specification):
 * - Tools: Executable functions exposed to AI models (with listChanged notifications)
 * - Resources: Read-only data accessible via URIs (with subscribe and listChanged)
 * - Prompts: Pre-defined prompt templates (with listChanged notifications)
 * - Logging: Structured log notifications sent to clients
 * - Completions: Autocomplete support for prompt and resource references
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class YawlServerCapabilities {

    private YawlServerCapabilities() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Full capabilities configuration with all MCP features enabled.
     * This is the default configuration for the YAWL MCP server.
     *
     * @return ServerCapabilities with all features enabled
     */
    public static McpSchema.ServerCapabilities full() {
        return McpSchema.ServerCapabilities.builder()
                .resources(false, true)   // subscribe=false, listChanged=true
                .tools(true)              // listChanged=true
                .prompts(true)            // listChanged=true
                .logging()                // enabled
                .completions()            // enabled
                .build();
    }

    /**
     * Minimal capabilities configuration with only tools enabled.
     *
     * @return ServerCapabilities with only tools enabled
     */
    public static McpSchema.ServerCapabilities minimal() {
        return McpSchema.ServerCapabilities.builder()
                .tools(true)
                .build();
    }

    /**
     * Tools and resources capabilities without prompts or completions.
     *
     * @return ServerCapabilities with tools and resources
     */
    public static McpSchema.ServerCapabilities toolsAndResources() {
        return McpSchema.ServerCapabilities.builder()
                .resources(false, true)
                .tools(true)
                .logging()
                .build();
    }

    /**
     * Read-only capabilities for data inspection without modification.
     *
     * @return ServerCapabilities for read-only access
     */
    public static McpSchema.ServerCapabilities readOnly() {
        return McpSchema.ServerCapabilities.builder()
                .resources(false, true)
                .prompts(true)
                .completions()
                .build();
    }
}
