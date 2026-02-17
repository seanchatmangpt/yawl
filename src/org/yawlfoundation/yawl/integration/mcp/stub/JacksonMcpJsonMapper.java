package org.yawlfoundation.yawl.integration.mcp.stub;

/**
 * Jackson-based JSON Mapper for MCP.
 *
 * <p>This is a minimal stub interface for the MCP SDK's Jackson JSON mapper.
 * Provides just enough API surface for YAWL MCP integration to compile.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0-Alpha
 * @deprecated Replace with official MCP SDK (io.modelcontextprotocol.sdk:mcp-json-jackson2) when available.
 *             See https://github.com/modelcontextprotocol/java-sdk
 */
@Deprecated
public class JacksonMcpJsonMapper {

    /**
     * Create a Jackson MCP JSON mapper.
     *
     * @param objectMapper the Jackson ObjectMapper to use
     * @throws UnsupportedOperationException always - this is a stub
     */
    public JacksonMcpJsonMapper(Object objectMapper) {
        throw new UnsupportedOperationException(
            "MCP SDK stub - cannot create real JSON mapper. " +
            "Replace with official MCP SDK from https://github.com/modelcontextprotocol/java-sdk");
    }
}
