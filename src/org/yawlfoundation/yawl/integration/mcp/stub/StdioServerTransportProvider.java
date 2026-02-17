package org.yawlfoundation.yawl.integration.mcp.stub;

/**
 * STDIO Transport Provider for MCP Server.
 *
 * <p>This is a minimal stub interface for the MCP SDK's STDIO transport.
 * Provides just enough API surface for YAWL MCP integration to compile.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0-Alpha
 * @deprecated Replace with official MCP SDK (io.modelcontextprotocol.sdk:mcp-core) when available.
 *             See https://github.com/modelcontextprotocol/java-sdk
 */
@Deprecated
public class StdioServerTransportProvider {

    /**
     * Create a STDIO transport provider with the given JSON mapper.
     *
     * @param jsonMapper the JSON mapper (typically Jackson-based)
     * @throws UnsupportedOperationException always - this is a stub
     */
    public StdioServerTransportProvider(Object jsonMapper) {
        throw new UnsupportedOperationException(
            "MCP SDK stub - cannot create real transport. " +
            "Replace with official MCP SDK from https://github.com/modelcontextprotocol/java-sdk");
    }
}
