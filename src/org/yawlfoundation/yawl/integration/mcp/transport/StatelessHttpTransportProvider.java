package org.yawlfoundation.yawl.integration.mcp.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStatelessServerTransport;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import jakarta.servlet.http.HttpServlet;

/**
 * Stateless HTTP Transport Provider for YAWL MCP Server.
 *
 * Provides stateless HTTP transport ideal for cloud-native deployments.
 * Each request is self-contained with no session state between requests.
 *
 * Features:
 * - Returns application/json responses (no SSE)
 * - No session state between requests
 * - Ideal for microservices and load-balanced environments
 * - Works behind API gateways and CDN caching
 *
 * Usage:
 * <pre>
 * YawlStatelessTransport transport = YawlStatelessTransport.create("/mcp");
 * McpSyncServer server = transport.createServer();
 * HttpServlet servlet = transport.getServlet();
 * </pre>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlStatelessTransport {

    private final HttpServletStatelessServerTransport transport;
    private final ObjectMapper objectMapper;
    private final String mcpEndpoint;

    /**
     * Private constructor - use factory methods.
     */
    private YawlStatelessTransport(
            HttpServletStatelessServerTransport transport,
            ObjectMapper objectMapper,
            String mcpEndpoint) {
        this.transport = transport;
        this.objectMapper = objectMapper;
        this.mcpEndpoint = mcpEndpoint;
    }

    /**
     * Creates a new stateless HTTP transport with default configuration.
     *
     * @param mcpEndpoint the endpoint URL for MCP operations (e.g., "/mcp")
     * @return a new YawlStatelessTransport instance
     */
    public static YawlStatelessTransport create(String mcpEndpoint) {
        ObjectMapper objectMapper = createObjectMapper();
        HttpServletStatelessServerTransport transport = new HttpServletStatelessServerTransport(objectMapper);
        return new YawlStatelessTransport(transport, objectMapper, mcpEndpoint);
    }

    /**
     * Creates a new stateless HTTP transport with custom ObjectMapper.
     *
     * @param mcpEndpoint the endpoint URL for MCP operations
     * @param objectMapper custom ObjectMapper for JSON serialization
     * @return a new YawlStatelessTransport instance
     */
    public static YawlStatelessTransport create(String mcpEndpoint, ObjectMapper objectMapper) {
        HttpServletStatelessServerTransport transport = new HttpServletStatelessServerTransport(objectMapper);
        return new YawlStatelessTransport(transport, objectMapper, mcpEndpoint);
    }

    /**
     * Creates a McpSyncServer with this transport.
     *
     * @return a configured McpSyncServer instance
     */
    public McpSyncServer createServer() {
        return McpServer.sync(transport)
                .serverInfo("yawl-mcp-server", "5.2")
                .build();
    }

    /**
     * Gets the McpServerTransportProvider for custom server configuration.
     *
     * @return the transport provider
     */
    public McpServerTransportProvider getTransportProvider() {
        return transport;
    }

    /**
     * Gets the HttpServlet for registration in servlet container.
     *
     * @return the HttpServlet instance
     */
    public HttpServlet getServlet() {
        return transport;
    }

    /**
     * Gets the underlying HttpServletStatelessServerTransport.
     *
     * @return the transport instance
     */
    public HttpServletStatelessServerTransport getTransport() {
        return transport;
    }

    /**
     * Gets the MCP endpoint URL.
     *
     * @return the MCP endpoint
     */
    public String getMcpEndpoint() {
        return mcpEndpoint;
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
        mapper.findAndRegisterModules();
        return mapper;
    }

    /**
     * Creates a builder for constructing a McpSyncServer with this transport.
     *
     * @return a McpServer.SyncBuilder instance
     */
    public McpServer.SyncBuilder createServerBuilder() {
        return McpServer.sync(transport);
    }
}
