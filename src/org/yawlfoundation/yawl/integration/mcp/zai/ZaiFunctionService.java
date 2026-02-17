package org.yawlfoundation.yawl.integration.mcp.zai;

/**
 * Z.AI Function Service for YAWL MCP integration.
 *
 * <p>This class provides integration between the YAWL workflow engine and Z.AI's
 * function calling capability for natural language workflow operations.
 * The implementation requires the Z.AI SDK to be available at runtime.</p>
 *
 * <p>To enable Z.AI integration:</p>
 * <ol>
 *   <li>Set the {@code ZAI_API_KEY} environment variable</li>
 *   <li>Add the Z.AI SDK dependency to yawl-integration/pom.xml</li>
 *   <li>Provide a concrete implementation that delegates to the Z.AI SDK</li>
 * </ol>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class ZaiFunctionService {

    private final String apiKey;
    private final String engineUrl;
    private final String username;
    private final String password;

    /**
     * Create a Z.AI function service.
     *
     * @param apiKey    Z.AI API key
     * @param engineUrl YAWL engine URL
     * @param username  YAWL username
     * @param password  YAWL password
     * @throws UnsupportedOperationException always - Z.AI SDK is not available.
     *         Add the Z.AI SDK dependency and provide a concrete implementation.
     */
    public ZaiFunctionService(String apiKey, String engineUrl, String username, String password) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("Z.AI API key is required");
        }
        if (engineUrl == null || engineUrl.isEmpty()) {
            throw new IllegalArgumentException("YAWL engine URL is required");
        }
        if (username == null || username.isEmpty()) {
            throw new IllegalArgumentException("YAWL username is required");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("YAWL password is required");
        }
        this.apiKey = apiKey;
        this.engineUrl = engineUrl;
        this.username = username;
        this.password = password;
        throw new UnsupportedOperationException(
            "Z.AI integration requires the Z.AI SDK to be available. " +
            "Add the Z.AI SDK dependency to yawl-integration/pom.xml and provide " +
            "a concrete implementation. See org.yawlfoundation.yawl.integration.zai " +
            "for the full implementation when the SDK is available.");
    }

    /**
     * Process a natural language query using Z.AI function calling.
     *
     * @param query natural language query about workflow operations
     * @return result string from Z.AI
     * @throws UnsupportedOperationException always - Z.AI SDK is not available
     */
    public String processWithFunctions(String query) {
        throw new UnsupportedOperationException(
            "Z.AI integration requires the Z.AI SDK to be available. " +
            "Add the Z.AI SDK dependency to yawl-integration/pom.xml and provide " +
            "a concrete implementation.");
    }
}
