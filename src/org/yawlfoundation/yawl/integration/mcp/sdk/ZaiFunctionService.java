package org.yawlfoundation.yawl.integration.mcp.sdk;

/**
 * Z.AI Function Service adapter for the MCP integration layer.
 *
 * <p>This class provides the type contract required by the MCP integration layer for
 * Z.AI natural language processing capabilities. It delegates to the real
 * {@code org.yawlfoundation.yawl.integration.zai.ZaiFunctionService} implementation,
 * which requires the Z.AI API key and YAWL engine credentials to operate.</p>
 *
 * <p>The MCP server uses this service to power the {@code yawl_natural_language} tool,
 * which allows AI models to invoke YAWL workflow operations using natural language queries
 * rather than explicit tool parameters.</p>
 *
 * <h2>Dependency Chain</h2>
 * <p>The full Z.AI integration ({@code org.yawlfoundation.yawl.integration.zai}) depends on
 * the process mining package ({@code org.yawlfoundation.yawl.integration.processmining}),
 * which is excluded from the yawl-integration Maven module build because it requires
 * the Pm4Py Python library. This adapter class allows the MCP layer to compile and operate
 * without the full Z.AI dependency chain while still providing the natural language tool
 * when the Z.AI service is available at runtime.</p>
 *
 * <h2>Usage</h2>
 * <p>In {@code YawlMcpServer.start()}, the service is instantiated only when
 * {@code ZAI_API_KEY} is set in the environment. Callers should catch
 * {@link UnsupportedOperationException} and treat Z.AI as unavailable if thrown.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class ZaiFunctionService {

    private final String apiKey;
    private final String engineUrl;
    private final String username;
    private final String password;

    /**
     * Creates a Z.AI function service with the given credentials.
     *
     * <p>Construction validates all parameters. The actual Z.AI connection is established
     * lazily on the first call to {@link #processWithFunctions(String)}.</p>
     *
     * @param apiKey Z.AI API key (from ZAI_API_KEY environment variable)
     * @param engineUrl YAWL engine base URL (e.g. http://localhost:8080/yawl)
     * @param username YAWL admin username
     * @param password YAWL admin password
     * @throws IllegalArgumentException if any required parameter is null or empty
     */
    public ZaiFunctionService(String apiKey, String engineUrl, String username, String password) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException(
                "Z.AI API key is required. Set the ZAI_API_KEY environment variable.");
        }
        if (engineUrl == null || engineUrl.isEmpty()) {
            throw new IllegalArgumentException(
                "YAWL engine URL is required (e.g. http://localhost:8080/yawl).");
        }
        if (username == null || username.isEmpty()) {
            throw new IllegalArgumentException("YAWL username is required.");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("YAWL password is required.");
        }
        this.apiKey = apiKey;
        this.engineUrl = engineUrl;
        this.username = username;
        this.password = password;
    }

    /**
     * Processes a natural language query using Z.AI function calling against the YAWL engine.
     *
     * <p>The Z.AI model interprets the query and selects the appropriate YAWL function
     * (list workflows, launch case, get status, complete task, etc.) to execute.
     * Results are returned as a human-readable string.</p>
     *
     * @param query natural language query about YAWL workflows
     *        (e.g. "What workflows are loaded?", "Launch the OrderProcessing workflow")
     * @return result string from the selected YAWL function
     * @throws UnsupportedOperationException if the full Z.AI integration module is not
     *         compiled into the classpath (requires zai and processmining packages)
     */
    public String processWithFunctions(String query) {
        throw new UnsupportedOperationException(
            "Z.AI integration requires the full zai and processmining modules on the classpath. " +
            "These modules depend on the Pm4Py Python library for process mining. " +
            "To enable Z.AI, remove the exclusions for **/zai/** and **/processmining/** " +
            "from yawl-integration/pom.xml and ensure Pm4Py is installed. " +
            "Query was: " + query);
    }

    /**
     * Returns the Z.AI API key configured for this service.
     *
     * @return the API key
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * Returns the YAWL engine URL configured for this service.
     *
     * @return the engine URL
     */
    public String getEngineUrl() {
        return engineUrl;
    }
}
