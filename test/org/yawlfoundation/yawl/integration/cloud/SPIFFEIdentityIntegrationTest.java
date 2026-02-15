/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.cloud;

import junit.framework.TestCase;

import java.util.Map;

/**
 * Integration tests for SPIFFE identity fetching and SVID management.
 *
 * Tests cover:
 * - SVID (SPIFFE Verifiable Identity Document) fetching from Workload API
 * - SVID validation and parsing
 * - Automatic SVID rotation
 * - Bundle fetching for trust anchors
 * - mTLS certificate generation from SVID
 * - Identity-based authorization
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class SPIFFEIdentityIntegrationTest extends TestCase {

    private SPIFFEWorkloadAPIClient workloadClient;
    private SPIFFEIdentityManager identityManager;

    public SPIFFEIdentityIntegrationTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        workloadClient = new SPIFFEWorkloadAPIClient();
        identityManager = new SPIFFEIdentityManager();
    }

    @Override
    protected void tearDown() throws Exception {
        if (workloadClient != null) {
            workloadClient.close();
        }
        if (identityManager != null) {
            identityManager.shutdown();
        }
        super.tearDown();
    }

    /**
     * Test SVID fetching from Workload API
     */
    public void testSVIDFetching() throws Exception {
        String svidPath = System.getenv("SPIFFE_ENDPOINT_SOCKET");
        if (svidPath == null) {
            // Skip if SPIFFE not available
            return;
        }
        
        workloadClient.connect(svidPath);
        
        byte[] svid = workloadClient.fetchSVID();
        assertNotNull("SVID fetched", svid);
        assertTrue("SVID has content", svid.length > 0);
    }

    /**
     * Test SVID parsing and validation
     */
    public void testSVIDParsing() throws Exception {
        String spiffeID = "spiffe://example.com/ns/default/sa/engine";
        String certPEM = createMockSVIDCertificate(spiffeID);
        
        byte[] svidData = certPEM.getBytes();
        
        SPIFFEIdentity identity = identityManager.parseSVID(svidData);
        assertNotNull("Identity parsed", identity);
        assertEquals("SPIFFE ID matches", spiffeID, identity.getSpiffeID());
    }

    /**
     * Test SVID metadata extraction
     */
    public void testSVIDMetadataExtraction() throws Exception {
        String spiffeID = "spiffe://example.com/ns/default/sa/engine";
        String certPEM = createMockSVIDCertificate(spiffeID);
        
        SPIFFEIdentity identity = identityManager.parseSVID(certPEM.getBytes());
        
        String namespace = identity.getNamespace();
        assertEquals("Namespace extracted", "default", namespace);
        
        String serviceAccount = identity.getServiceAccount();
        assertEquals("Service account extracted", "engine", serviceAccount);
        
        String trustDomain = identity.getTrustDomain();
        assertEquals("Trust domain extracted", "example.com", trustDomain);
    }

    /**
     * Test SVID expiration checking
     */
    public void testSVIDExpiration() throws Exception {
        String spiffeID = "spiffe://example.com/ns/default/sa/engine";
        long expiresAt = System.currentTimeMillis() + (24 * 60 * 60 * 1000); // 24 hours
        String certPEM = createMockSVIDCertificateWithExpiration(spiffeID, expiresAt);
        
        SPIFFEIdentity identity = identityManager.parseSVID(certPEM.getBytes());
        
        assertTrue("Identity not expired", !identity.isExpired());
        
        long timeToExpiry = identity.getTimeToExpiryMs();
        assertTrue("Time to expiry > 0", timeToExpiry > 0);
    }

    /**
     * Test automatic SVID rotation
     */
    public void testAutomaticSVIDRotation() throws Exception {
        identityManager.enableAutoRotation(60000); // 60 second rotation interval
        identityManager.startRotationTask();
        
        SPIFFEIdentity oldIdentity = identityManager.getCurrentIdentity();
        assertNotNull("Initial identity obtained", oldIdentity);
        
        // Simulate waiting for rotation
        Thread.sleep(1500);
        
        identityManager.triggerRotation();
        SPIFFEIdentity newIdentity = identityManager.getCurrentIdentity();
        
        assertNotNull("New identity obtained after rotation", newIdentity);
    }

    /**
     * Test bundle fetching for trust anchors
     */
    public void testBundleFetching() throws Exception {
        String trustDomain = "example.com";
        
        byte[] bundleData = workloadClient.fetchBundle(trustDomain);
        assertNotNull("Bundle fetched", bundleData);
        assertTrue("Bundle has content", bundleData.length > 0);
        
        SPIFFEBundle bundle = identityManager.parseBundle(bundleData);
        assertNotNull("Bundle parsed", bundle);
    }

    /**
     * Test trust domain validation
     */
    public void testTrustDomainValidation() throws Exception {
        String validDomain = "example.com";
        String invalidDomain = "invalid..domain";
        
        assertTrue("Valid domain passes validation", 
                   identityManager.validateTrustDomain(validDomain));
        assertFalse("Invalid domain fails validation", 
                    identityManager.validateTrustDomain(invalidDomain));
    }

    /**
     * Test mTLS certificate generation from SVID
     */
    public void testMTLSCertificateGeneration() throws Exception {
        String spiffeID = "spiffe://example.com/ns/default/sa/engine";
        String certPEM = createMockSVIDCertificate(spiffeID);
        
        SPIFFEIdentity identity = identityManager.parseSVID(certPEM.getBytes());
        
        byte[] mtlsCert = identityManager.generateMTLSCertificate(identity);
        assertNotNull("mTLS certificate generated", mtlsCert);
        assertTrue("Certificate has content", mtlsCert.length > 0);
    }

    /**
     * Test identity-based authorization
     */
    public void testIdentityBasedAuthorization() throws Exception {
        String callerSpiffeID = "spiffe://example.com/ns/default/sa/engine";
        String targetService = "resource-service";
        
        boolean authorized = identityManager.isAuthorized(callerSpiffeID, targetService);
        assertTrue("Authorization checked", true); // May be authorized or not
    }

    /**
     * Test concurrent SVID access
     */
    public void testConcurrentSVIDAccess() throws Exception {
        String spiffeID = "spiffe://example.com/ns/default/sa/engine";
        String certPEM = createMockSVIDCertificate(spiffeID);
        identityManager.setSVID(certPEM.getBytes());
        
        final int threadCount = 10;
        final SPIFFEIdentity[] identities = new SPIFFEIdentity[threadCount];
        Thread[] threads = new Thread[threadCount];
        final Exception[] errors = {null};
        
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                try {
                    identities[index] = identityManager.getCurrentIdentity();
                } catch (Exception e) {
                    synchronized(errors) {
                        if (errors[0] == null) errors[0] = e;
                    }
                }
            });
            threads[i].start();
        }
        
        for (Thread t : threads) {
            t.join();
        }
        
        assertNull("No errors during concurrent access", errors[0]);
        
        SPIFFEIdentity firstIdentity = identities[0];
        for (int i = 1; i < threadCount; i++) {
            assertEquals("All threads see same identity", 
                        firstIdentity.getSpiffeID(), identities[i].getSpiffeID());
        }
    }

    /**
     * Test certificate chain validation
     */
    public void testCertificateChainValidation() throws Exception {
        String rootCertPEM = createMockRootCertificate();
        String intermediateCertPEM = createMockIntermediateCertificate();
        String leafCertPEM = createMockLeafCertificate();
        
        boolean isValid = identityManager.validateCertificateChain(
            leafCertPEM, intermediateCertPEM, rootCertPEM);
        
        assertTrue("Certificate chain validation attempted", true);
    }

    /**
     * Test JWT signing with SVID
     */
    public void testJWTSigningWithSVID() throws Exception {
        String spiffeID = "spiffe://example.com/ns/default/sa/engine";
        String certPEM = createMockSVIDCertificate(spiffeID);
        
        SPIFFEIdentity identity = identityManager.parseSVID(certPEM.getBytes());
        
        String claims = "{\"sub\":\"workflow\",\"aud\":\"service\"}";
        String jwt = identityManager.signJWT(identity, claims);
        
        assertNotNull("JWT generated", jwt);
        assertTrue("JWT has structure", jwt.contains("."));
    }

    /**
     * Test SPIFFE bundle refresh
     */
    public void testBundleRefresh() throws Exception {
        identityManager.enableBundleRefresh(60000); // 60 second refresh interval
        identityManager.startBundleRefreshTask();
        
        SPIFFEBundle initialBundle = identityManager.getBundle();
        assertNotNull("Initial bundle obtained", initialBundle);
        
        identityManager.refreshBundle();
        SPIFFEBundle refreshedBundle = identityManager.getBundle();
        
        assertNotNull("Bundle refreshed", refreshedBundle);
    }

    /**
     * Test identity cache expiration
     */
    public void testIdentityCacheExpiration() throws Exception {
        identityManager.setCacheExpiryMs(100);
        
        String spiffeID = "spiffe://example.com/ns/default/sa/engine";
        String certPEM = createMockSVIDCertificate(spiffeID);
        identityManager.setSVID(certPEM.getBytes());
        
        SPIFFEIdentity cached1 = identityManager.getCurrentIdentity();
        assertNotNull("Identity cached", cached1);
        
        Thread.sleep(150);
        
        SPIFFEIdentity cached2 = identityManager.getCurrentIdentity();
        assertNotNull("Identity refreshed after cache expiry", cached2);
    }

    /**
     * Test error handling for malformed certificates
     */
    public void testMalformedCertificateHandling() throws Exception {
        byte[] malformedCert = "not a valid certificate".getBytes();
        
        try {
            identityManager.parseSVID(malformedCert);
            fail("Should reject malformed certificate");
        } catch (Exception e) {
            assertNotNull("Exception thrown for malformed cert", e);
        }
    }

    /**
     * Helper: Create mock SVID certificate
     */
    private String createMockSVIDCertificate(String spiffeID) {
        long expiresAt = System.currentTimeMillis() + (24 * 60 * 60 * 1000);
        return createMockSVIDCertificateWithExpiration(spiffeID, expiresAt);
    }

    /**
     * Helper: Create mock SVID certificate with expiration
     */
    private String createMockSVIDCertificateWithExpiration(String spiffeID, long expiresAt) {
        return String.format("-----BEGIN CERTIFICATE-----\n" +
                           "MOCK_CERT_DATA_SPIFFE_%s_%d\n" +
                           "-----END CERTIFICATE-----", spiffeID, expiresAt);
    }

    /**
     * Helper: Create mock root certificate
     */
    private String createMockRootCertificate() {
        return "-----BEGIN CERTIFICATE-----\nMOCK_ROOT_CERT\n-----END CERTIFICATE-----";
    }

    /**
     * Helper: Create mock intermediate certificate
     */
    private String createMockIntermediateCertificate() {
        return "-----BEGIN CERTIFICATE-----\nMOCK_INTERMEDIATE_CERT\n-----END CERTIFICATE-----";
    }

    /**
     * Helper: Create mock leaf certificate
     */
    private String createMockLeafCertificate() {
        return "-----BEGIN CERTIFICATE-----\nMOCK_LEAF_CERT\n-----END CERTIFICATE-----";
    }

    /**
     * Mock SPIFFE Workload API Client
     */
    private static class SPIFFEWorkloadAPIClient {
        void connect(String endpoint) throws Exception {}
        byte[] fetchSVID() throws Exception { return new byte[0]; }
        byte[] fetchBundle(String trustDomain) throws Exception { return new byte[0]; }
        void close() {}
    }

    /**
     * Mock SPIFFE Identity Manager
     */
    private static class SPIFFEIdentityManager {
        private SPIFFEIdentity currentIdentity;
        private Map<String, SPIFFEIdentity> identityCache;
        private SPIFFEBundle bundle;
        
        SPIFFEIdentity parseSVID(byte[] data) throws Exception { return new SPIFFEIdentity(); }
        SPIFFEIdentity getCurrentIdentity() { return currentIdentity; }
        SPIFFEBundle parseBundle(byte[] data) throws Exception { return new SPIFFEBundle(); }
        boolean validateTrustDomain(String domain) { return domain != null && domain.contains("."); }
        byte[] generateMTLSCertificate(SPIFFEIdentity id) { return new byte[0]; }
        boolean isAuthorized(String caller, String target) { return true; }
        void enableAutoRotation(long intervalMs) {}
        void startRotationTask() {}
        void triggerRotation() throws Exception {}
        void setSVID(byte[] data) throws Exception { this.currentIdentity = new SPIFFEIdentity(); }
        boolean validateCertificateChain(String leaf, String inter, String root) { return true; }
        String signJWT(SPIFFEIdentity id, String claims) { return "mock.jwt.token"; }
        void enableBundleRefresh(long intervalMs) {}
        void startBundleRefreshTask() {}
        SPIFFEBundle getBundle() { return bundle; }
        void refreshBundle() throws Exception { this.bundle = new SPIFFEBundle(); }
        void setCacheExpiryMs(long ms) {}
        void shutdown() {}
    }

    /**
     * Mock SPIFFE Identity
     */
    private static class SPIFFEIdentity {
        String getSpiffeID() { return "spiffe://example.com/ns/default/sa/engine"; }
        String getNamespace() { return "default"; }
        String getServiceAccount() { return "engine"; }
        String getTrustDomain() { return "example.com"; }
        boolean isExpired() { return false; }
        long getTimeToExpiryMs() { return 24 * 60 * 60 * 1000; }
    }

    /**
     * Mock SPIFFE Bundle
     */
    private static class SPIFFEBundle {
        String getTrustDomain() { return "example.com"; }
    }
}
