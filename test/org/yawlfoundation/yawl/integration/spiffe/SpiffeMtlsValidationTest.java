/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.spiffe;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.TestMethodOrder;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive SPIFFE mTLS authentication validation tests.
 * Tests workload identity, certificate validation, and federated trust.
 */
@TestMethodOrder(OrderAnnotation.class)
public class SpiffeMtlsValidationTest {

    private SpiffeWorkloadIdentity workloadIdentity;
    private SpiffeCredentialProvider credentialProvider;
    private SpiffeMtlsHttpClient httpClient;
    private static final String SPIFFE_TRUST_DOMAIN = "example.org";
    private static final String SERVICE_ID = "yawl-service";

    @BeforeEach
    void setUp() {
        workloadIdentity = new SpiffeWorkloadIdentity(
            SPIFFE_TRUST_DOMAIN,
            SERVICE_ID
        );
        credentialProvider = new SpiffeCredentialProvider(workloadIdentity);
        httpClient = new SpiffeMtlsHttpClient(credentialProvider);
    }

    @Test
    @Order(1)
    @DisplayName("SPIFFE: Workload Identity Generation")
    void testWorkloadIdentityGeneration() throws Exception {
        // Generate workload identity
        SpiffeWorkloadIdentity identity = workloadIdentity.generateIdentity();

        // Validate identity
        assertNotNull(identity.getSvid(), "SVID should not be null");
        assertEquals(SPIFFE_TRUST_DOMAIN, identity.getTrustDomain(),
            "Trust domain should match");
        assertEquals(SERVICE_ID, identity.getServiceId(),
            "Service ID should match");
        assertTrue(identity.getSvid().startsWith("spiffe://" + SPIFFE_TRUST_DOMAIN + "/"),
            "SVID should have correct format");

        // Validate certificate chain
        List<X509Certificate> certChain = identity.getCertificateChain();
        assertFalse(certChain.isEmpty(), "Certificate chain should not be empty");
        assertEquals("CN=" + identity.getSvid(), certChain.get(0).getSubjectX500Principal().getName(),
            "Certificate should match SVID");

        System.out.printf("✅ Workload Identity: %s%n", identity.getSvid());
    }

    @Test
    @Order(2)
    @DisplayName("SPIFFE: mTLS Authentication")
    void testMtlsAuthentication() throws Exception {
        // Create test HTTP client with SPIFFE credentials
        HttpClient client = SpiffeMtlsHttpClient.createWithSpiffeIdentity(
            SPIFFE_TRUST_DOMAIN + "/yawl-service",
            credentialProvider
        );

        // Test mutual TLS handshake
        HttpRequest request = HttpRequest.newBuilder()
            .uri(java.net.URI.create("https://localhost:8443/health"))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();

        // Send request with mTLS
        HttpResponse<String> response = client.send(request,
            HttpResponse.BodyHandlers.ofString());

        // Validate authentication success
        assertEquals(200, response.statusCode(), "mTLS authentication should succeed");
        assertTrue(response.body().contains("ok"),
            "Response should indicate success");

        System.out.println("✅ mTLS Authentication validated");
    }

    @Test
    @Order(3)
    @DisplayName("SPIFFE: Certificate Validation Chain")
    void testCertificateValidationChain() throws Exception {
        // Test certificate validation
        SpiffeWorkloadIdentity identity = workloadIdentity.generateIdentity();

        // Validate certificate chain against trust bundle
        boolean valid = SpiffeWorkloadIdentity.validateCertificateChain(
            identity.getCertificateChain(),
            SPIFFE_TRUST_DOMAIN
        );

        assertTrue(valid, "Certificate chain should be valid");

        // Test with invalid certificate
        SpiffeWorkloadIdentity invalidIdentity = workloadIdentity.generateIdentity();
        invalidIdentity.setTrustDomain("invalid-trust-domain.org");

        boolean invalidValid = SpiffeWorkloadIdentity.validateCertificateChain(
            invalidIdentity.getCertificateChain(),
            SPIFFE_TRUST_DOMAIN
        );

        assertFalse(invalidValid, "Certificate from wrong trust domain should be invalid");

        System.out.println("✅ Certificate validation chain validated");
    }

    @Test
    @Order(4)
    @DisplayName("SPIFFE: Federated Trust Configuration")
    void testFederatedTrustConfiguration() {
        // Create federated trust configuration
        SpiffeFederationConfig config = new SpiffeFederationConfig();
        config.addTrustDomain("example.org");
        config.addTrustDomain("trusted-partner.org");
        config.setTrustDomainMapping(
            "trusted-partner.org",
            "https://trust-anchor.trusted-partner.org/spiffe/v1/roots"
        );

        // Validate trust domain membership
        assertTrue(config.isTrustDomainAllowed("example.org"),
            "Example.org should be trusted");
        assertTrue(config.isTrustDomainAllowed("trusted-partner.org"),
            "Trusted partner should be trusted");
        assertFalse(config.isTrustDomainAllowed("malicious.org"),
            "Malicious domain should not be trusted");

        // Test trust anchor verification
        String svid = "spiffe://trusted-partner.org/service";
        assertTrue(config.isSvidValid(svid),
            "Federated SVID should be valid");

        System.out.println("✅ Federated trust configuration validated");
    }

    @Test
    @Order(5)
    @DisplayName("SPIFFE: SPIFFE Agent Authentication")
    void testSpiffeAgentAuthentication() throws Exception {
        // Test SPIFFE authentication provider
        SpiffeAuthenticationProvider authProvider = new SpiffeAuthenticationProvider();

        // Test valid authentication
        Map<String, Object> validClaims = Map.of(
            "sub", "spiffe://example.org/yawl-service",
            "aud", "yawl-api",
            "exp", System.currentTimeMillis() / 1000 + 3600
        );

        assertTrue(authProvider.authenticate(validClaims),
            "Valid SPIFFE identity should be authenticated");

        // Test invalid authentication
        Map<String, Object> invalidClaims = Map.of(
            "sub", "spiffe://invalid.org/yawl-service", // Wrong trust domain
            "aud", "yawl-api",
            "exp", System.currentTimeMillis() / 1000 + 3600
        );

        assertFalse(authProvider.authenticate(invalidClaims),
            "Invalid SPIFFE identity should be rejected");

        System.out.println("✅ SPIFFE agent authentication validated");
    }

    @Test
    @Order(6)
    @DisplayName("SPIFFE: Secure Credential Rotation")
    void testSecureCredentialRotation() throws Exception {
        // Generate initial credentials
        SpiffeCredentialProvider initialProvider = credentialProvider;
        X509Certificate initialCert = initialProvider.getCertificate();

        // Rotate credentials
        SpiffeWorkloadIdentity newIdentity = workloadIdentity.generateIdentity();
        credentialProvider.updateCredentials(newIdentity);

        // Verify credentials were updated
        X509Certificate newCert = credentialProvider.getCertificate();
        assertNotEquals(initialCert.getSerialNumber(), newCert.getSerialNumber(),
            "Rotated certificate should have different serial number");

        // Test that old credentials are no longer valid
        assertFalse(credentialProvider.isCertificateValid(initialCert),
            "Old certificate should no longer be valid");

        System.out.println("✅ Secure credential rotation validated");
    }

    @Test
    @Order(7)
    @DisplayName("SPIFFE: Performance Validation (mTLS < 100ms)")
    void testMtlsPerformance() throws Exception {
        // Measure mTLS handshake performance
        HttpClient client = SpiffeMtlsHttpClient.createWithSpiffeIdentity(
            SPIFFE_TRUST_DOMAIN + "/yawl-service",
            credentialProvider
        );

        // Warm up
        HttpRequest warmupRequest = HttpRequest.newBuilder()
            .uri(java.net.URI.create("https://localhost:8443/health"))
            .GET()
            .build();
        client.send(warmupRequest, HttpResponse.BodyHandlers.ofString());

        // Measure performance
        long startTime = System.nanoTime();
        int iterations = 100;

        for (int i = 0; i < iterations; i++) {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(java.net.URI.create("https://localhost:8443/health"))
                .GET()
                .build();
            client.send(request, HttpResponse.BodyHandlers.ofString());
        }

        long duration = System.nanoTime() - startTime;
        double avgMs = duration / 1_000_000.0 / iterations;

        // Performance assertion: must be under 100ms average
        assertTrue(avgMs < 100.0,
            String.format("mTLS handshake average time: %.2f ms (must be < 100ms)", avgMs));

        System.out.printf("✅ mTLS Performance: %.2f ms average%n", avgMs);
    }

    @Test
    @Order(8)
    @DisplayName("SPIFFE: Concurrent Authentication")
    void testConcurrentAuthentication() throws Exception {
        int concurrentRequests = 50;
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();

        // Submit concurrent authentication requests
        for (int i = 0; i < concurrentRequests; i++) {
            final int requestNum = i;
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                try {
                    // Test SPIFFE authentication
                    Map<String, Object> claims = Map.of(
                        "sub", "spiffe://" + SPIFFE_TRUST_DOMAIN + "/service-" + requestNum,
                        "aud", "yawl-api",
                        "exp", System.currentTimeMillis() / 1000 + 3600
                    );

                    SpiffeAuthenticationProvider authProvider = new SpiffeAuthenticationProvider();
                    return authProvider.authenticate(claims);
                } catch (Exception e) {
                    return false;
                }
            }, executor);
            futures.add(future);
        }

        // Wait for all requests to complete
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0]));
        allFutures.get(10, TimeUnit.SECONDS);

        // Validate results
        int successful = 0;
        for (CompletableFuture<Boolean> future : futures) {
            try {
                if (future.get()) {
                    successful++;
                }
            } catch (Exception e) {
                // Count as failed
            }
        }

        // All requests should succeed
        assertEquals(concurrentRequests, successful,
            String.format("All %d authentication requests should succeed", concurrentRequests));

        System.out.printf("✅ Concurrent authentication: %d/%d successful%n",
            successful, concurrentRequests);
    }

    @Test
    @Order(9)
    @DisplayName("SPIFFE: Security Events Audit Trail")
    void testSecurityEventsAuditTrail() throws Exception {
        // Initialize SPIFFE-enabled service
        SpiffeEnabledZaiService service = new SpiffeEnabledZaiService(
            SPIFFE_TRUST_DOMAIN + "/yawl-service",
            credentialProvider
        );

        // Test authentication events
        service.authenticate("spiffe://" + SPIFFE_TRUST_DOMAIN + "/authorized-service");

        // Retrieve audit trail
        List<SpiffeAuditEvent> auditTrail = service.getAuditTrail();

        // Verify security events
        assertFalse(auditTrail.isEmpty(), "Should have audit events");
        assertTrue(auditTrail.stream().anyMatch(
            event -> event.getEventType().equals("AUTHENTICATION_SUCCESS")),
            "Should have successful authentication event");

        // Test failure scenarios
        try {
            service.authenticate("spiffe://invalid.org/malicious-service");
        } catch (SecurityException e) {
            // Expected
        }

        List<SpiffeAuditEvent> failureEvents = service.getAuditTrail().stream()
            .filter(event -> event.getEventType().equals("AUTHENTICATION_FAILURE"))
            .toList();

        assertFalse(failureEvents.isEmpty(), "Should have authentication failure events");

        System.out.printf("✅ Security events audit trail: %d total events%n",
            auditTrail.size());
    }

    // Helper classes

    public static class SpiffeAuditEvent {
        private final String eventType;
        private final String svid;
        private final Instant timestamp;
        private final String details;

        public SpiffeAuditEvent(String eventType, String svid, Instant timestamp, String details) {
            this.eventType = eventType;
            this.svid = svid;
            this.timestamp = timestamp;
            this.details = details;
        }

        public String getEventType() { return eventType; }
        public String getSvid() { return svid; }
        public Instant getTimestamp() { return timestamp; }
        public String getDetails() { return details; }
    }
}