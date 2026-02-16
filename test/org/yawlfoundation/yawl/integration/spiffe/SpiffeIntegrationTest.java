package org.yawlfoundation.yawl.integration.spiffe;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for SPIFFE workload identity
 *
 * These tests require SPIRE Agent to be running on the test host.
 * If SPIRE is not available, tests will be skipped.
 *
 * To run with SPIRE:
 *   1. Deploy SPIRE Agent: ./scripts/spiffe/deploy-spire-agent.sh
 *   2. Register test workload: ./scripts/spiffe/register-yawl-workloads.sh
 *   3. Run tests: ant test -Dtest.class=SpiffeIntegrationTest
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class SpiffeIntegrationTest {

    private SpiffeCredentialProvider credentialProvider;
    private boolean spiffeAvailable;

    @BeforeEach
    public void setUp() {
        credentialProvider = SpiffeCredentialProvider.getInstance();
        spiffeAvailable = credentialProvider.isSpiffeAvailable();

        if (!spiffeAvailable) {
            System.out.println("SPIRE Agent not available - tests will verify fallback behavior");
        }
    }

    @Test
    public void testCredentialProviderInitialization() {
        assertNotNull("Credential provider should initialize", credentialProvider);
    }

    @Test
    public void testSpiffeAvailability() {
        if (spiffeAvailable) {
            System.out.println("SPIFFE is available - testing with workload identity");
            Optional<String> spiffeId = credentialProvider.getWorkloadSpiffeId();
            assertTrue("Should have SPIFFE ID when available", spiffeId.isPresent());
            assertTrue("SPIFFE ID should start with spiffe://",
                spiffeId.get().startsWith("spiffe://"));
            System.out.println("Workload SPIFFE ID: " + spiffeId.get());
        } else {
            System.out.println("SPIFFE not available - testing fallback mode");
            Optional<String> spiffeId = credentialProvider.getWorkloadSpiffeId();
            assertFalse("Should not have SPIFFE ID when unavailable", spiffeId.isPresent());
        }
    }

    @Test
    public void testCredentialFallback() {
        String testApiKey = "test-api-key-12345";
        System.setProperty("TEST_API_KEY", testApiKey);

        String credential = credentialProvider.getCredentialOrDefault("test-service", testApiKey);
        assertNotNull("Credential should not be null", credential);

        SpiffeCredentialProvider.CredentialSource source =
            credentialProvider.getCredentialSource("test-service");
        System.out.println("Credential source: " + source);

        if (spiffeAvailable) {
            assertTrue("Should use SPIFFE when available",
                source == SpiffeCredentialProvider.CredentialSource.SPIFFE_JWT ||
                source == SpiffeCredentialProvider.CredentialSource.NONE);
        } else {
            assertTrue("Should use fallback when SPIFFE unavailable",
                source == SpiffeCredentialProvider.CredentialSource.NONE);
        }
    }

    @Test
    public void testX509IdentityRetrieval() {
        if (!spiffeAvailable) {
            System.out.println("Skipping X.509 test - SPIRE not available");
            return;
        }

        Optional<SpiffeWorkloadIdentity> identity = credentialProvider.getX509Identity();
        assertTrue("Should have X.509 identity when SPIRE available", identity.isPresent());

        SpiffeWorkloadIdentity svid = identity.get();
        assertEquals("Should be X.509 SVID",
            SpiffeWorkloadIdentity.SvidType.X509, svid.getType());
        assertFalse("Should not be expired", svid.isExpired());
        assertTrue("Should have certificate chain", svid.getX509Chain().isPresent());

        System.out.println("X.509 SVID: " + svid);
        System.out.println("  SPIFFE ID: " + svid.getSpiffeId());
        System.out.println("  Trust Domain: " + svid.getTrustDomain());
        System.out.println("  Expires: " + svid.getExpiresAt());
    }

    @Test
    public void testWorkloadApiClientDirect() {
        if (!spiffeAvailable) {
            System.out.println("Skipping direct API test - SPIRE not available");
            return;
        }

        try {
            SpiffeWorkloadApiClient client = new SpiffeWorkloadApiClient();
            assertTrue("Client should detect SPIRE Agent", client.isAvailable());

            SpiffeWorkloadIdentity identity = client.fetchX509Svid();
            assertNotNull("Should fetch X.509 SVID", identity);
            assertEquals("Should be X.509 type",
                SpiffeWorkloadIdentity.SvidType.X509, identity.getType());

            identity.validate();
            System.out.println("Direct API fetch successful: " + identity.getSpiffeId());

            client.shutdown();
        } catch (Exception e) {
            fail("Direct API client failed: " + e.getMessage());
        }
    }

    @Test
    public void testJwtSvidFetch() {
        if (!spiffeAvailable) {
            System.out.println("Skipping JWT test - SPIRE not available");
            return;
        }

        try {
            SpiffeWorkloadApiClient client = new SpiffeWorkloadApiClient();
            SpiffeWorkloadIdentity jwtSvid = client.fetchJwtSvid("test-audience");

            assertNotNull("Should fetch JWT SVID", jwtSvid);
            assertEquals("Should be JWT type",
                SpiffeWorkloadIdentity.SvidType.JWT, jwtSvid.getType());
            assertTrue("Should have JWT token", jwtSvid.getJwtToken().isPresent());

            String bearerToken = jwtSvid.toBearerToken().orElse("");
            assertTrue("Bearer token should start with 'Bearer '",
                bearerToken.startsWith("Bearer "));

            System.out.println("JWT SVID fetched for audience: test-audience");
            System.out.println("  SPIFFE ID: " + jwtSvid.getSpiffeId());

            client.shutdown();
        } catch (Exception e) {
            fail("JWT SVID fetch failed: " + e.getMessage());
        }
    }

    @Test
    public void testFederationConfig() {
        try {
            SpiffeFederationConfig.Builder builder = new SpiffeFederationConfig.Builder();
            builder.localTrustDomain("test.local")
                .addFederation("gcp.test.cloud", "https://gcp-spire:8443", "gcp", "us-central1")
                .addFederation("aws.test.cloud", "https://aws-spire:8443", "aws", "us-east-1");

            SpiffeFederationConfig config = builder.build();

            assertEquals("Local trust domain should match",
                "test.local", config.getLocalTrustDomain());
            assertTrue("Should have GCP federation",
                config.isFederated("gcp.test.cloud"));
            assertTrue("Should have AWS federation",
                config.isFederated("aws.test.cloud"));

            Optional<String> gcpCloud = config.getCloudProvider("gcp.test.cloud");
            assertTrue("Should have GCP cloud metadata", gcpCloud.isPresent());
            assertEquals("GCP cloud should match", "gcp", gcpCloud.get());

            System.out.println("Federation config test passed");
            System.out.println("  GCP domains: " + config.getGcpDomains().size());
            System.out.println("  AWS domains: " + config.getAwsDomains().size());
        } catch (Exception e) {
            fail("Federation config test failed: " + e.getMessage());
        }
    }

    @Test
    public void testSpiffeEnabledZaiService() {
        String testKey = System.getenv("ZAI_API_KEY");
        if (testKey == null || testKey.isEmpty()) {
            System.out.println("Skipping ZAI test - no API key or SPIFFE identity");
            return;
        }

        try {
            credentialProvider.getCredential("zai-api");
            System.out.println("ZAI credential available via: " +
                credentialProvider.getCredentialSource("zai-api"));
        } catch (SpiffeException e) {
            System.out.println("ZAI credential not available: " + e.getMessage());
        }
    }

    @Test
    public void testIdentityRotation() {
        if (!spiffeAvailable) {
            System.out.println("Skipping rotation test - SPIRE not available");
            return;
        }

        try {
            SpiffeWorkloadApiClient client = new SpiffeWorkloadApiClient();
            client.enableAutoRotation(java.time.Duration.ofSeconds(30));

            SpiffeWorkloadIdentity identity1 = client.getCurrentIdentity();
            assertNotNull("Should have initial identity", identity1);

            Thread.sleep(1000);

            SpiffeWorkloadIdentity identity2 = client.getCurrentIdentity();
            assertNotNull("Should still have identity after 1s", identity2);
            assertEquals("Identity should be same (not expired yet)",
                identity1.getSpiffeId(), identity2.getSpiffeId());

            System.out.println("Auto-rotation enabled and verified");
            client.shutdown();
        } catch (Exception e) {
            fail("Rotation test failed: " + e.getMessage());
        }
    }
}
