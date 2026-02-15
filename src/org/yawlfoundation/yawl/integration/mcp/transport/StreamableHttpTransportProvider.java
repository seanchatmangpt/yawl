package org.yawlfoundation.yawl.integration.mcp.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import jakarta.servlet.http.HttpServlet;

import java.time.Duration;

/**
 * Streamable HTTP Servlet Transport Provider for YAWL MCP Server.
 *
 * Provides bidirectional HTTP transport using Servlet 6.0 async support.
 * This enables single-endpoint MCP communication with streaming responses.
 *
 * Features:
 * - Single endpoint for all MCP operations
 * - Async Servlet 6.0 support for non-blocking I/O
 * - Session management for concurrent clients
 * - Ideal for modern HTTP/2 deployments
 *
 * Usage:
 * <pre>
 * YawlStreamableHttpTransport transport = YawlStreamableHttpTransport.create("/mcp");
 * HttpServlet servlet = transport.getServlet();
 * // Register servlet at /mcp endpoint
 * </pre>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlStreamableHttpTransport {

    private final HttpServletStreamableServerTransportProvider transport;
    private final ObjectMapper objectMapper;
    private final String mcpEndpoint;
    private final Duration keepAliveInterval;

    /**
     * Private constructor - use factory methods.
     */
    private YawlStreamableHttpTransport(
            HttpServletStreamableServerTransportProvider transport,
            ObjectMapper objectMapper,
            String mcpEndpoint,
            Duration keepAliveInterval) {
        this.transport = transport;
        this.objectMapper = objectMapper;
        this.mcpEndpoint = mcpEndpoint;
        this.keepAliveInterval = keepAliveInterval;
    }

    /**
     * Creates a new streamable HTTP transport with default configuration.
     *
     * @param mcpEndpoint the endpoint URL for all MCP operations (e.g., "/mcp")
     * @return a new YawlStreamableHttpTransport instance
     */
    public static YawlStreamableHttpTransport create(String mcpEndpoint) {
        return create(mcpEndpoint, Duration.ofSeconds(30));
    }

    /**
     * Creates a new streamable HTTP transport with custom keep-alive interval.
     *
     * @param mcpEndpoint the endpoint URL for all MCP operations
     * @param keepAliveInterval the interval for keep-alive events
     * @return a new YawlStreamableHttpTransport instance
     */
    public static YawlStreamableHttpTransport create(String mcpEndpoint, Duration keepAliveInterval) {
        ObjectMapper objectMapper = createObjectMapper();

        HttpServletStreamableServerTransportProvider transport = HttpServletStreamableServerTransportProvider.builder()
                .objectMapper(objectMapper)
                .mcpEndpoint(mcpEndpoint)
                .keepAliveInterval(keepAliveInterval)
                .build();

        return new YawlStreamableHttpTransport(transport, objectMapper, mcpEndpoint, keepAliveInterval);
    }

    /**
     * Creates a new streamable HTTP transport with custom ObjectMapper.
     *
     * @param mcpEndpoint the endpoint URL for all MCP operations
     * @param objectMapper custom ObjectMapper for JSON serialization
     * @param keepAliveInterval the interval for keep-alive events
     * @return a new YawlStreamableHttpTransport instance
     */
    public static YawlStreamableHttpTransport create(
            String mcpEndpoint,
            ObjectMapper objectMapper,
            Duration keepAliveInterval) {

        HttpServletStreamableServerTransportProvider transport = HttpServletStreamableServerTransportProvider.builder()
                .objectMapper(objectMapper)
                .mcpEndpoint(mcpEndpoint)
                .keepAliveInterval(keepAliveInterval)
                .build();

        return new YawlStreamableHttpTransport(transport, objectMapper, mcpEndpoint, keepAliveInterval);
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
     * Gets the underlying HttpServletStreamableServerTransportProvider.
     *
     * @return the transport provider
     */
    public HttpServletStreamableServerTransportProvider getTransport() {
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
     * Gets the keep-alive interval.
     *
     * @return the keep-alive interval
     */
    public Duration getKeepAliveInterval() {
        return keepAliveInterval;
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
}
