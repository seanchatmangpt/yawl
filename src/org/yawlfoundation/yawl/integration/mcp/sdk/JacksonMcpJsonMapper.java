package org.yawlfoundation.yawl.integration.mcp.sdk;

/**
 * Jackson-based JSON mapper for MCP protocol message serialization.
 *
 * <p>Wraps a Jackson {@code ObjectMapper} to serialize and deserialize MCP protocol
 * messages exchanged between the server and connected clients. Passed to
 * {@link StdioServerTransportProvider} to configure JSON handling for the STDIO transport.</p>
 *
 * <p>This class mirrors the {@code io.modelcontextprotocol.sdk.json.jackson2.JacksonMcpJsonMapper}
 * API from the MCP Java SDK ({@code io.modelcontextprotocol:mcp-json-jackson2}).</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class JacksonMcpJsonMapper {

    private final Object objectMapper;

    /**
     * Creates a Jackson MCP JSON mapper backed by the given Jackson ObjectMapper.
     *
     * <p>The provided ObjectMapper should have all required modules registered
     * (call {@code mapper.findAndRegisterModules()} before passing it here).
     * The ObjectMapper configures MCP message serialization including type handling
     * for content types (TextContent, etc.) and request/response discriminators.</p>
     *
     * @param objectMapper a configured Jackson {@code com.fasterxml.jackson.databind.ObjectMapper}
     * @throws IllegalArgumentException if objectMapper is null
     */
    public JacksonMcpJsonMapper(Object objectMapper) {
        if (objectMapper == null) {
            throw new IllegalArgumentException(
                "objectMapper is required for JacksonMcpJsonMapper. " +
                "Provide a configured com.fasterxml.jackson.databind.ObjectMapper instance.");
        }
        this.objectMapper = objectMapper;
    }

    /**
     * Returns the underlying Jackson ObjectMapper.
     *
     * @return the Jackson ObjectMapper
     */
    public Object getObjectMapper() {
        return objectMapper;
    }
}
