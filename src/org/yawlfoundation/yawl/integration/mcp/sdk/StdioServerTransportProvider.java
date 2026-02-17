package org.yawlfoundation.yawl.integration.mcp.sdk;

/**
 * STDIO Transport Provider for MCP Server.
 *
 * <p>Configures an MCP server to communicate with clients over standard input/output streams.
 * STDIO transport is the standard mechanism for MCP servers launched as child processes by
 * AI host applications (e.g. Claude Desktop, VS Code Copilot).</p>
 *
 * <p>This class mirrors the {@code io.modelcontextprotocol.sdk.server.StdioServerTransportProvider}
 * API as defined in the MCP specification transport section.</p>
 *
 * <p><b>Runtime Note:</b> This class serves as the transport configuration type recognized by
 * {@link McpServer#sync(Object)}. An actual live STDIO transport connection requires the official
 * MCP Java SDK ({@code io.modelcontextprotocol:mcp}). Construction will succeed to allow type-safe
 * configuration building; the server will throw {@link UnsupportedOperationException} at
 * {@link McpServer.SyncServerBuilder#build()} time until the real SDK is available.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class StdioServerTransportProvider {

    private final Object jsonMapper;

    /**
     * Creates a STDIO transport provider configured to use the given JSON mapper.
     *
     * <p>The JSON mapper serializes and deserializes MCP protocol messages exchanged
     * over stdin/stdout with the MCP client. Pass a {@link JacksonMcpJsonMapper} instance
     * constructed with a configured Jackson {@code ObjectMapper}.</p>
     *
     * @param jsonMapper the JSON mapper for MCP protocol message serialization
     *        (use {@link JacksonMcpJsonMapper} with a configured Jackson ObjectMapper)
     */
    public StdioServerTransportProvider(Object jsonMapper) {
        if (jsonMapper == null) {
            throw new IllegalArgumentException(
                "jsonMapper is required for StdioServerTransportProvider. " +
                "Provide a JacksonMcpJsonMapper instance.");
        }
        this.jsonMapper = jsonMapper;
    }

    /**
     * Returns the JSON mapper configured for this transport.
     *
     * @return the JSON mapper
     */
    public Object getJsonMapper() {
        return jsonMapper;
    }
}
