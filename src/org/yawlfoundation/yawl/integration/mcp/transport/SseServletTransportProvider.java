package org.yawlfoundation.yawl.integration.mcp.transport;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import jakarta.servlet.http.HttpServlet;

import java.time.Duration;

/**
 * SSE (Server-Sent Events) Servlet Transport Provider for YAWL MCP Server.
 *
 * Provides HTTP-based MCP server mode using Server-Sent Events for real-time communication.
 * This is ideal for Tomcat deployment with persistent connections.
 *
 * The transport creates two endpoints:
 * - SSE endpoint: For server-to-client event streaming
 * - Message endpoint: For client-to-server JSON-RPC messages
 *
 * Usage:
 * <pre>
 * YawlSseServletTransport transport = YawlSseServletTransport.create("/mcp/message");
 * HttpServlet servlet = transport.getServlet();
 * // Register servlet in web.xml or programmatically
 * </pre>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class YawlSseServletTransport {

    private final HttpServletSseServerTransportProvider transport;
    private final ObjectMapper objectMapper;
    private final String messageEndpoint;
    private final Duration keepAliveInterval;

    /**
     * Private constructor - use factory methods.
     */
    private YawlSseServletTransport(
            HttpServletSseServerTransportProvider transport,
            ObjectMapper objectMapper,
            String messageEndpoint,
            Duration keepAliveInterval) {
        this.transport = transport;
        this.objectMapper = objectMapper;
        this.messageEndpoint = messageEndpoint;
        this.keepAliveInterval = keepAliveInterval;
    }

    /**
     * Creates a new SSE servlet transport with default configuration.
     *
     * @param messageEndpoint the endpoint URL for receiving client messages (e.g., "/mcp/message")
     * @return a new YawlSseServletTransport instance
     */
    public static YawlSseServletTransport create(String messageEndpoint) {
        return create(messageEndpoint, Duration.ofSeconds(30));
    }

    /**
     * Creates a new SSE servlet transport with custom keep-alive interval.
     *
     * @param messageEndpoint the endpoint URL for receiving client messages
     * @param keepAliveInterval the interval for sending keep-alive events
     * @return a new YawlSseServletTransport instance
     */
    public static YawlSseServletTransport create(String messageEndpoint, Duration keepAliveInterval) {
        ObjectMapper objectMapper = createObjectMapper();

        HttpServletSseServerTransportProvider transport = HttpServletSseServerTransportProvider.builder()
                .objectMapper(objectMapper)
                .messageEndpoint(messageEndpoint)
                .keepAliveInterval(keepAliveInterval)
                .build();

        return new YawlSseServletTransport(transport, objectMapper, messageEndpoint, keepAliveInterval);
    }

    /**
     * Creates a new SSE servlet transport with custom ObjectMapper.
     *
     * @param messageEndpoint the endpoint URL for receiving client messages
     * @param objectMapper custom ObjectMapper for JSON serialization
     * @param keepAliveInterval the interval for sending keep-alive events
     * @return a new YawlSseServletTransport instance
     */
    public static YawlSseServletTransport create(
            String messageEndpoint,
            ObjectMapper objectMapper,
            Duration keepAliveInterval) {

        HttpServletSseServerTransportProvider transport = HttpServletSseServerTransportProvider.builder()
                .objectMapper(objectMapper)
                .messageEndpoint(messageEndpoint)
                .keepAliveInterval(keepAliveInterval)
                .build();

        return new YawlSseServletTransport(transport, objectMapper, messageEndpoint, keepAliveInterval);
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
     * Gets the underlying HttpServletSseServerTransportProvider.
     *
     * @return the transport provider
     */
    public HttpServletSseServerTransportProvider getTransport() {
        return transport;
    }

    /**
     * Gets the message endpoint URL.
     *
     * @return the message endpoint
     */
    public String getMessageEndpoint() {
        return messageEndpoint;
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
