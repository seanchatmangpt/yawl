package org.yawlfoundation.yawl.ggen.mining.cloud;

import java.util.Map;

/**
 * Factory for creating CloudMiningClient instances.
 * Provides static methods to instantiate clients for different mining platforms:
 * - Celonis (conformance mining)
 * - UiPath (RPA automation logs)
 * - Signavio (BPMN model management)
 */
public class CloudMiningClientFactory {

    private CloudMiningClientFactory() {
        // Utility class, prevent instantiation
    }

    /**
     * Create a Celonis mining client.
     * @param apiKey Celonis API key
     * @param teamId Celonis team ID
     * @return CelonicsMiningClient instance
     */
    public static CelonicsMiningClient createCelonis(String apiKey, String teamId) {
        return new CelonicsMiningClient(apiKey, teamId);
    }

    /**
     * Create a UiPath automation client.
     * @param apiToken UiPath API token
     * @param tenantName UiPath tenant name
     * @param accountName UiPath account name
     * @return UiPathAutomationClient instance
     */
    public static UiPathAutomationClient createUiPath(String apiToken, String tenantName, String accountName) {
        return new UiPathAutomationClient(apiToken, tenantName, accountName);
    }

    /**
     * Create a Signavio process governance client.
     * @param email Signavio account email
     * @param password Signavio account password
     * @param serverUrl Signavio server URL (e.g., https://editor.signavio.com/g)
     * @return SignavioClient instance
     */
    public static SignavioClient createSignavio(String email, String password, String serverUrl) {
        return new SignavioClient(email, password, serverUrl);
    }

    /**
     * Create a CloudMiningClient based on platform name and configuration.
     * @param platform Platform identifier: "celonis", "uipath", "signavio"
     * @param config Configuration map with platform-specific keys:
     *        - celonis: apiKey, teamId
     *        - uipath: apiToken, tenantName, accountName
     *        - signavio: email, password, serverUrl
     * @return CloudMiningClient instance
     * @throws IllegalArgumentException if platform is unknown or config is missing required keys
     */
    public static CloudMiningClient create(String platform, Map<String, String> config) {
        if (platform == null) {
            throw new IllegalArgumentException("Platform cannot be null");
        }

        return switch (platform.toLowerCase()) {
            case "celonis" -> {
                String apiKey = config.get("apiKey");
                String teamId = config.get("teamId");
                if (apiKey == null || teamId == null) {
                    throw new IllegalArgumentException("Celonis requires 'apiKey' and 'teamId' in config");
                }
                yield createCelonis(apiKey, teamId);
            }

            case "uipath" -> {
                String apiToken = config.get("apiToken");
                String tenantName = config.get("tenantName");
                String accountName = config.get("accountName");
                if (apiToken == null || tenantName == null || accountName == null) {
                    throw new IllegalArgumentException(
                        "UiPath requires 'apiToken', 'tenantName', and 'accountName' in config"
                    );
                }
                yield createUiPath(apiToken, tenantName, accountName);
            }

            case "signavio" -> {
                String email = config.get("email");
                String password = config.get("password");
                String serverUrl = config.get("serverUrl");
                if (email == null || password == null || serverUrl == null) {
                    throw new IllegalArgumentException(
                        "Signavio requires 'email', 'password', and 'serverUrl' in config"
                    );
                }
                yield createSignavio(email, password, serverUrl);
            }

            default -> throw new IllegalArgumentException(
                "Unknown platform: " + platform + ". " +
                "Supported platforms: celonis, uipath, signavio"
            );
        };
    }
}
