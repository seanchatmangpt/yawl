package org.yawlfoundation.yawl.integration.spiffe;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SPIFFE Credential Provider for YAWL
 *
 * Provides a unified interface for obtaining credentials that abstracts away
 * whether the credential comes from:
 *   - SPIFFE/SPIRE workload identity (production)
 *   - Environment variables (development/testing)
 *   - Configuration files (legacy)
 *
 * This allows applications to seamlessly migrate from API keys to SPIFFE
 * without changing their code. Simply configure SPIRE and restart - the
 * application automatically uses workload identity.
 *
 * Usage:
 * <pre>
 *   SpiffeCredentialProvider provider = SpiffeCredentialProvider.getInstance();
 *   String credential = provider.getCredential("zai-api");
 *   // Returns SPIFFE JWT if available, falls back to ZAI_API_KEY env var
 * </pre>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class SpiffeCredentialProvider {

    private static final SpiffeCredentialProvider INSTANCE = new SpiffeCredentialProvider();

    private final SpiffeWorkloadApiClient spiffeClient;
    private final Map<String, CredentialSource> credentialCache;
    private final boolean spiffeAvailable;

    /**
     * Credential source type
     */
    public enum CredentialSource {
        SPIFFE_X509,
        SPIFFE_JWT,
        ENVIRONMENT_VARIABLE,
        CONFIGURATION_FILE,
        NONE
    }

    private SpiffeCredentialProvider() {
        SpiffeWorkloadApiClient client = null;
        boolean available = false;

        try {
            client = new SpiffeWorkloadApiClient();
            available = client.isAvailable();
            if (available) {
                client.enableAutoRotation(java.time.Duration.ofSeconds(30));
                System.err.println("SPIFFE: Workload API available, using SPIFFE credentials");
            }
        } catch (Exception e) {
            System.err.println("SPIFFE: Not available, falling back to environment variables");
        }

        this.spiffeClient = client;
        this.spiffeAvailable = available;
        this.credentialCache = new ConcurrentHashMap<>();
    }

    /**
     * Get singleton instance
     */
    public static SpiffeCredentialProvider getInstance() {
        return INSTANCE;
    }

    /**
     * Get credential for a service
     *
     * Priority:
     * 1. SPIFFE JWT SVID (if available)
     * 2. Environment variable
     * 3. Exception thrown
     *
     * @param serviceName Service name (e.g., "zai-api", "yawl-engine")
     * @return Credential string (JWT token or API key)
     * @throws SpiffeException if no credential available
     */
    public String getCredential(String serviceName) throws SpiffeException {
        if (serviceName == null || serviceName.isEmpty()) {
            throw new IllegalArgumentException("Service name is required");
        }

        if (spiffeAvailable && spiffeClient != null) {
            try {
                String audience = mapServiceToAudience(serviceName);
                SpiffeWorkloadIdentity identity = spiffeClient.fetchJwtSvid(audience);
                credentialCache.put(serviceName, CredentialSource.SPIFFE_JWT);
                return identity.getJwtToken().orElseThrow(
                    () -> new SpiffeException("JWT token not present in SVID"));
            } catch (Exception e) {
                System.err.println("SPIFFE: Failed to fetch JWT for " + serviceName + ": " + e.getMessage());
            }
        }

        String envVarName = mapServiceToEnvVar(serviceName);
        String envValue = System.getenv(envVarName);
        if (envValue != null && !envValue.isEmpty()) {
            credentialCache.put(serviceName, CredentialSource.ENVIRONMENT_VARIABLE);
            return envValue;
        }

        throw new SpiffeException(
            "No credential available for service: " + serviceName + "\n" +
            "Options:\n" +
            "  1. Deploy SPIRE Agent and configure workload attestation\n" +
            "  2. Set environment variable: " + envVarName + "\n" +
            "See: https://spiffe.io/docs/latest/deploying/"
        );
    }

    /**
     * Get credential with fallback value (for backwards compatibility)
     */
    public String getCredentialOrDefault(String serviceName, String defaultValue) {
        try {
            return getCredential(serviceName);
        } catch (SpiffeException e) {
            credentialCache.put(serviceName, CredentialSource.NONE);
            return defaultValue;
        }
    }

    /**
     * Get X.509 identity for mTLS connections
     */
    public Optional<SpiffeWorkloadIdentity> getX509Identity() {
        if (!spiffeAvailable || spiffeClient == null) {
            return Optional.empty();
        }

        try {
            return Optional.of(spiffeClient.getValidIdentity());
        } catch (SpiffeException e) {
            System.err.println("SPIFFE: Failed to get X.509 identity: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Get current credential source for a service
     */
    public CredentialSource getCredentialSource(String serviceName) {
        return credentialCache.getOrDefault(serviceName, CredentialSource.NONE);
    }

    /**
     * Check if SPIFFE is available
     */
    public boolean isSpiffeAvailable() {
        return spiffeAvailable;
    }

    /**
     * Get SPIFFE ID of this workload
     */
    public Optional<String> getWorkloadSpiffeId() {
        if (!spiffeAvailable || spiffeClient == null) {
            return Optional.empty();
        }

        try {
            SpiffeWorkloadIdentity identity = spiffeClient.getValidIdentity();
            return Optional.of(identity.getSpiffeId());
        } catch (SpiffeException e) {
            return Optional.empty();
        }
    }

    /**
     * Map service name to audience (for JWT SVIDs)
     */
    private String mapServiceToAudience(String serviceName) {
        return switch (serviceName.toLowerCase()) {
            case "zai-api", "zai" -> "api.z.ai";
            case "yawl-engine", "yawl" -> "yawl.engine";
            case "mcp-server" -> "mcp.server";
            case "a2a-server" -> "a2a.server";
            default -> serviceName;
        };
    }

    /**
     * Map service name to environment variable name
     */
    private String mapServiceToEnvVar(String serviceName) {
        return switch (serviceName.toLowerCase()) {
            case "zai-api", "zai" -> "ZAI_API_KEY";
            case "yawl-engine", "yawl" -> "YAWL_PASSWORD";
            case "yawl-username" -> "YAWL_USERNAME";
            default -> serviceName.toUpperCase().replace("-", "_") + "_API_KEY";
        };
    }

    /**
     * Refresh all cached credentials
     */
    public void refreshAll() {
        credentialCache.clear();
    }

    /**
     * Shutdown the provider
     */
    public void shutdown() {
        if (spiffeClient != null) {
            spiffeClient.shutdown();
        }
        credentialCache.clear();
    }
}
