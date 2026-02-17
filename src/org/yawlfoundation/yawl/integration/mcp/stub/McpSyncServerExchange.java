package org.yawlfoundation.yawl.integration.mcp.stub;

/**
 * MCP Sync Server Exchange context.
 *
 * <p>This is a minimal stub interface for the MCP SDK's exchange context.
 * Provides just enough API surface for YAWL MCP integration to compile.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0-Alpha
 * @deprecated Replace with official MCP SDK (io.modelcontextprotocol.sdk:mcp-core) when available.
 *             See https://github.com/modelcontextprotocol/java-sdk
 */
@Deprecated
public interface McpSyncServerExchange {

    /**
     * Get the client name.
     *
     * @return client name
     */
    String getClientName();

    /**
     * Get the client version.
     *
     * @return client version
     */
    String getClientVersion();

    /**
     * Get the protocol version.
     *
     * @return protocol version
     */
    String getProtocolVersion();

    /**
     * Check if the client supports a capability.
     *
     * @param capability the capability name
     * @return true if supported
     */
    boolean supportsCapability(String capability);
}
