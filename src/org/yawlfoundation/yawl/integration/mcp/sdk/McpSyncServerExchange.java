package org.yawlfoundation.yawl.integration.mcp.sdk;

/**
 * MCP Synchronous Server Exchange context.
 *
 * <p>Provides per-request context for MCP server handler functions. An exchange instance
 * is passed to every tool, resource, prompt, and completion handler, giving the handler
 * access to client metadata and protocol capabilities for the current connection.</p>
 *
 * <p>This interface mirrors the {@code io.modelcontextprotocol.sdk.McpSyncServerExchange}
 * API as defined in the MCP specification.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public interface McpSyncServerExchange {

    /**
     * Returns the name of the connected MCP client.
     *
     * @return client name string, or null if the client did not declare a name
     */
    String getClientName();

    /**
     * Returns the version of the connected MCP client.
     *
     * @return client version string, or null if the client did not declare a version
     */
    String getClientVersion();

    /**
     * Returns the MCP protocol version negotiated for this connection.
     *
     * @return protocol version string (e.g. "2024-11-05")
     */
    String getProtocolVersion();

    /**
     * Checks whether the connected client supports a named MCP capability.
     *
     * @param capability the capability name to check (e.g. "sampling", "roots")
     * @return true if the client declared support for the named capability
     */
    boolean supportsCapability(String capability);
}
