package org.yawlfoundation.yawl.integration.spiffe.spring;

import org.yawlfoundation.yawl.integration.spiffe.*;

/**
 * Spring Configuration for SPIFFE/SPIRE Integration
 *
 * This class would provide Spring bean definitions for SPIFFE components
 * if Spring Framework is available. Currently, YAWL does not use Spring,
 * but this provides a template for future integration.
 *
 * If you need Spring integration, add Spring Framework dependencies and
 * uncomment the @Configuration and @Bean annotations below.
 *
 * Example usage with Spring:
 * <pre>
 * {@literal @}Configuration
 * public class SpiffeConfiguration {
 *     {@literal @}Bean
 *     public SpiffeWorkloadApiClient spiffeClient() { ... }
 * }
 * </pre>
 *
 * For non-Spring YAWL applications, use direct instantiation:
 * <pre>
 *   SpiffeCredentialProvider provider = SpiffeCredentialProvider.getInstance();
 *   String credential = provider.getCredential("zai-api");
 * </pre>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class SpiffeConfiguration {

    /**
     * Create SPIFFE Workload API Client bean
     */
    public SpiffeWorkloadApiClient spiffeWorkloadApiClient() {
        SpiffeWorkloadApiClient client = new SpiffeWorkloadApiClient();
        client.enableAutoRotation(java.time.Duration.ofSeconds(30));
        return client;
    }

    /**
     * Create SPIFFE Credential Provider bean (singleton)
     */
    public SpiffeCredentialProvider spiffeCredentialProvider() {
        return SpiffeCredentialProvider.getInstance();
    }

    /**
     * Create SPIFFE Federation Config bean
     */
    public SpiffeFederationConfig spiffeFederationConfig() {
        try {
            return new SpiffeFederationConfig();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load SPIFFE federation config", e);
        }
    }

    /**
     * Create SPIFFE mTLS HTTP Client bean
     */
    public SpiffeMtlsHttpClient spiffeMtlsHttpClient() {
        return new SpiffeMtlsHttpClient();
    }

    /**
     * Lifecycle callback - shutdown SPIFFE clients on application shutdown
     */
    public void destroy() {
        SpiffeCredentialProvider.getInstance().shutdown();
    }
}
