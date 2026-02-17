package org.yawlfoundation.yawl.integration.mcp.stub;

/**
 * Z.AI Function Service stub.
 *
 * <p>This is a minimal stub for the Z.AI integration service.
 * Provides just enough API surface for YAWL MCP integration to compile.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0-Alpha
 * @deprecated Replace with real ZaiFunctionService when Z.AI integration is available.
 */
@Deprecated
public class ZaiFunctionService {

    /**
     * Create a Z.AI function service.
     *
     * @param apiKey Z.AI API key
     * @param engineUrl YAWL engine URL
     * @param username YAWL username
     * @param password YAWL password
     * @throws UnsupportedOperationException always - this is a stub
     */
    public ZaiFunctionService(String apiKey, String engineUrl, String username, String password) {
        throw new UnsupportedOperationException(
            "ZAI integration stub - cannot create real service. " +
            "ZAI integration requires the zai package to be available.");
    }

    /**
     * Process a natural language query using Z.AI.
     *
     * @param query natural language query
     * @return result string
     * @throws UnsupportedOperationException always - this is a stub
     */
    public String processWithFunctions(String query) {
        throw new UnsupportedOperationException(
            "ZAI integration stub - cannot process queries. " +
            "ZAI integration requires the zai package to be available.");
    }
}
