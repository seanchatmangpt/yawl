package org.yawlfoundation.yawl.integration.spiffe;

import org.yawlfoundation.yawl.integration.zai.ZaiHttpClient;
import org.yawlfoundation.yawl.integration.zai.ZaiService;

/**
 * SPIFFE-Enabled Z.AI Service for YAWL
 *
 * Drop-in replacement for ZaiService that automatically uses SPIFFE
 * workload identity when available, falling back to API keys.
 *
 * This allows seamless migration from API key authentication to
 * SPIFFE-based workload identity without code changes.
 *
 * Usage:
 * <pre>
 *   // Old way (API key from env)
 *   ZaiService service = new ZaiService();
 *
 *   // New way (SPIFFE or API key)
 *   ZaiService service = SpiffeEnabledZaiService.create();
 *   // Automatically uses SPIFFE JWT if SPIRE Agent is running
 * </pre>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class SpiffeEnabledZaiService extends ZaiService {

    private final SpiffeCredentialProvider credentialProvider;
    private final boolean usingSpiffe;

    /**
     * Create a SPIFFE-enabled Z.AI service
     */
    private SpiffeEnabledZaiService(String apiKey, boolean usingSpiffe) {
        super(apiKey);
        this.credentialProvider = SpiffeCredentialProvider.getInstance();
        this.usingSpiffe = usingSpiffe;

        if (usingSpiffe) {
            System.err.println("SPIFFE: Z.AI service using workload identity");
        }
    }

    /**
     * Create a Z.AI service with automatic credential selection
     *
     * @return ZaiService instance using best available credential source
     * @throws IllegalStateException if no credentials available
     */
    public static ZaiService create() {
        SpiffeCredentialProvider provider = SpiffeCredentialProvider.getInstance();

        try {
            String credential = provider.getCredential("zai-api");
            boolean usingSpiffe = provider.getCredentialSource("zai-api")
                == SpiffeCredentialProvider.CredentialSource.SPIFFE_JWT;
            return new SpiffeEnabledZaiService(credential, usingSpiffe);
        } catch (SpiffeException e) {
            throw new IllegalStateException(
                "No Z.AI credentials available. " + e.getMessage(), e);
        }
    }

    /**
     * Create with explicit credential override (for testing)
     */
    public static ZaiService createWithCredential(String apiKey) {
        return new SpiffeEnabledZaiService(apiKey, false);
    }

    /**
     * Check if this service is using SPIFFE credentials
     */
    public boolean isUsingSpiffe() {
        return usingSpiffe;
    }

    /**
     * Get the credential source
     */
    public SpiffeCredentialProvider.CredentialSource getCredentialSource() {
        return credentialProvider.getCredentialSource("zai-api");
    }
}
