package org.yawlfoundation.yawl.integration.mcp.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpServerTransportProvider;

/**
 * STDIO Transport Provider for YAWL MCP Server.
 *
 * Provides process-based MCP server mode using stdin/stdout for communication.
 * This is ideal for CLI integration with AI tools that spawn the MCP server as a subprocess.
 *
 * Usage:
 * <pre>
 * YawlStdioTransportProvider transport = new YawlStdioTransportProvider();
 * McpSyncServer server = McpServer.sync(transport.getDelegate())
 *     .serverInfo("yawl-mcp-server", "5.2")
 *     .build();
 * </pre>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlStdioTransportProvider {

    private final StdioServerTransportProvider delegate;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new STDIO transport provider with default ObjectMapper.
     */
    public YawlStdioTransportProvider() {
        this.objectMapper = createObjectMapper();
        this.delegate = new StdioServerTransportProvider(this.objectMapper);
    }

    /**
     * Creates a new STDIO transport provider with custom ObjectMapper.
     *
     * @param objectMapper the ObjectMapper to use for JSON serialization
     */
    public YawlStdioTransportProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.delegate = new StdioServerTransportProvider(objectMapper);
    }

    /**
     * Gets the underlying StdioServerTransportProvider delegate.
     *
     * @return the delegate transport provider
     */
    public McpServerTransportProvider getDelegate() {
        return delegate;
    }

    /**
     * Gets the ObjectMapper used for JSON serialization.
     *
     * @return the ObjectMapper instance
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * Creates a configured ObjectMapper with YAWL-specific settings.
     *
     * @return a configured ObjectMapper instance
     */
    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Configure for consistent serialization
        mapper.findAndRegisterModules();
        return mapper;
    }

    /**
     * Creates a builder for constructing a McpSyncServer with this transport.
     *
     * @return a McpServer.SyncBuilder instance
     */
    public McpServer.SyncBuilder createServerBuilder() {
        return McpServer.sync(delegate);
    }

    /**
     * Creates a builder for constructing a McpAsyncServer with this transport.
     *
     * @return a McpServer.AsyncBuilder instance
     */
    public McpServer.AsyncBuilder createAsyncServerBuilder() {
        return McpServer.async(delegate);
    }
}
