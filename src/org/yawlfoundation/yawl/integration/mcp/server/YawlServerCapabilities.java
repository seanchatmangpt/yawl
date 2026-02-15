package org.yawlfoundation.yawl.integration.mcp.server;

import io.modelcontextprotocol.spec.ServerCapabilities;

/**
 * YAWL MCP Server Capabilities Configuration.
 *
 * Provides factory methods for creating server capabilities configurations
 * that declare what features the YAWL MCP server supports.
 *
 * Capabilities include:
 * - Tools: Executable functions exposed to AI models
 * - Resources: Read-only data accessible via URIs
 * - Prompts: Pre-defined prompt templates
 * - Logging: Structured log notifications
 * - Completions: Autocomplete support for prompts/resources
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlServerCapabilities {

    /**
     * Full capabilities configuration with all features enabled.
     *
     * @return ServerCapabilities with all features enabled
     */
    public static ServerCapabilities full() {
        return ServerCapabilities.builder()
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
    public static ServerCapabilities minimal() {
        return ServerCapabilities.builder()
                .tools(true)
                .build();
    }

    /**
     * Tools-only capabilities for AI model execution.
     *
     * @return ServerCapabilities with only tools
     */
    public static ServerCapabilities toolsOnly() {
        return ServerCapabilities.builder()
                .tools(true)
                .build();
    }

    /**
     * Resources-only capabilities for data access.
     *
     * @return ServerCapabilities with only resources
     */
    public static ServerCapabilities resourcesOnly() {
        return ServerCapabilities.builder()
                .resources(false, true)
                .build();
    }

    /**
     * Tools and resources capabilities without prompts or completions.
     *
     * @return ServerCapabilities with tools and resources
     */
    public static ServerCapabilities toolsAndResources() {
        return ServerCapabilities.builder()
                .resources(false, true)
                .tools(true)
                .logging()
                .build();
    }

    /**
     * Tools and prompts capabilities for guided AI interactions.
     *
     * @return ServerCapabilities with tools and prompts
     */
    public static ServerCapabilities toolsAndPrompts() {
        return ServerCapabilities.builder()
                .tools(true)
                .prompts(true)
                .logging()
                .build();
    }

    /**
     * Read-only capabilities for data inspection without modification.
     *
     * @return ServerCapabilities for read-only access
     */
    public static ServerCapabilities readOnly() {
        return ServerCapabilities.builder()
                .resources(false, true)
                .prompts(true)
                .completions()
                .build();
    }

    /**
     * Streaming capabilities with SSE support.
     *
     * @return ServerCapabilities with resources subscription
     */
    public static ServerCapabilities streaming() {
        return ServerCapabilities.builder()
                .resources(true, true)     // subscribe=true for streaming updates
                .tools(true)
                .logging()
                .build();
    }
}
