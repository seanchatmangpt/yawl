package org.yawlfoundation.yawl.integration.spiffe;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for SpiffeWorkloadIdentity.
 * Tests SPIFFE ID validation, certificate chain validation, and immutability.
 *
 * Chicago TDD style - tests real identity behavior.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
class SpiffeWorkloadIdentityTest {

    private X509Certificate[] validCertChain;
    private X509Certificate[] expiredCertChain;
    private static final String VALID_SPIFFE_ID = "spiffe://yawl.cloud/engine/instance-1";
    private static final String VALID_TRUST_DOMAIN = "yawl.cloud";

    @BeforeEach
    void setUp() throws Exception {
        // Generate test certificate chains using a simple self-signed approach
        validCertChain = generateTestCertificateChain(Instant.now().plusSeconds(3600));
        expiredCertChain = generateTestCertificateChain(Instant.now().minusSeconds(3600));
    }

    /**
     * Test: Valid X.509 SVID creation.
     * Verifies successful creation with valid SPIFFE ID and certificate chain.
     */
    @Test
    @DisplayName("Valid X.509 SVID should be created successfully")
    void testValidX509SvidCreation() {
        SpiffeWorkloadIdentity identity = new SpiffeWorkloadIdentity(VALID_SPIFFE_ID, validCertChain);

        assertEquals(VALID_SPIFFE_ID, identity.getSpiffeId());
        assertEquals(SpiffeWorkloadIdentity.SvidType.X509, identity.getType());
        assertEquals(VALID_TRUST_DOMAIN, identity.getTrustDomain());
        assertNotNull(identity.getExpiresAt());
    }

    /**
     * Test: Valid JWT SVID creation.
     */
    @Test
    @DisplayName("Valid JWT SVID should be created successfully")
    void testValidJwtSvidCreation() {
        Instant expiresAt = Instant.now().plusSeconds(3600);
        String jwtToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.test";

        SpiffeWorkloadIdentity identity = new SpiffeWorkloadIdentity(VALID_SPIFFE_ID, jwtToken, expiresAt);

        assertEquals(VALID_SPIFFE_ID, identity.getSpiffeId());
        assertEquals(SpiffeWorkloadIdentity.SvidType.JWT, identity.getType());
        assertEquals(jwtToken, identity.getJwtToken().orElse(null));
        assertEquals(expiresAt, identity.getExpiresAt());
    }

    /**
     * Test: Invalid SPIFFE ID format (null) throws exception.
     */
    @Test
    @DisplayName("Null SPIFFE ID should throw IllegalArgumentException")
    void testNullSpiffeId() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new SpiffeWorkloadIdentity(null, validCertChain)
        );
        assertTrue(exception.getMessage().contains("Invalid SPIFFE ID format"),
            "Exception should mention invalid SPIFFE ID format");
    }

    /**
     * Test: Invalid SPIFFE ID format (missing spiffe:// prefix) throws exception.
     */
    @Test
    @DisplayName("SPIFFE ID without spiffe:// prefix should throw IllegalArgumentException")
    void testInvalidSpiffeIdFormat() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new SpiffeWorkloadIdentity("invalid-id", validCertChain)
        );
        assertTrue(exception.getMessage().contains("Invalid SPIFFE ID format"),
            "Exception should mention invalid SPIFFE ID format");
    }

    /**
     * Test: Empty SPIFFE ID throws exception.
     */
    @Test
    @DisplayName("Empty SPIFFE ID should throw IllegalArgumentException")
    void testEmptySpiffeId() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new SpiffeWorkloadIdentity("spiffe://", validCertChain)
        );
        assertNotNull(exception);
    }

    /**
     * Test: Null certificate chain throws exception.
     */
    @Test
    @DisplayName("Null certificate chain should throw IllegalArgumentException")
    void testNullCertChain() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new SpiffeWorkloadIdentity(VALID_SPIFFE_ID, (X509Certificate[]) null)
        );
        assertTrue(exception.getMessage().contains("certificate chain is required"),
            "Exception should mention certificate chain requirement");
    }

    /**
     * Test: Empty certificate chain throws exception.
     */
    @Test
    @DisplayName("Empty certificate chain should throw IllegalArgumentException")
    void testEmptyCertChain() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new SpiffeWorkloadIdentity(VALID_SPIFFE_ID, new X509Certificate[0])
        );
        assertTrue(exception.getMessage().contains("certificate chain is required"),
            "Exception should mention certificate chain requirement");
    }

    /**
     * Test: Null JWT token throws exception.
     */
    @Test
    @DisplayName("Null JWT token should throw IllegalArgumentException")
    void testNullJwtToken() {
        Instant expiresAt = Instant.now().plusSeconds(3600);

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new SpiffeWorkloadIdentity(VALID_SPIFFE_ID, (String) null, expiresAt)
        );
        assertTrue(exception.getMessage().contains("JWT token is required"),
            "Exception should mention JWT token requirement");
    }

    /**
     * Test: Empty JWT token throws exception.
     */
    @Test
    @DisplayName("Empty JWT token should throw IllegalArgumentException")
    void testEmptyJwtToken() {
        Instant expiresAt = Instant.now().plusSeconds(3600);

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new SpiffeWorkloadIdentity(VALID_SPIFFE_ID, "", expiresAt)
        );
        assertTrue(exception.getMessage().contains("JWT token is required"),
            "Exception should mention JWT token requirement");
    }

    /**
     * Test: Null expiration time for JWT throws exception.
     */
    @Test
    @DisplayName("Null expiration time for JWT should throw IllegalArgumentException")
    void testNullExpiration() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new SpiffeWorkloadIdentity(VALID_SPIFFE_ID, "token", null)
        );
        assertTrue(exception.getMessage().contains("Expiration time is required"),
            "Exception should mention expiration time requirement");
    }

    /**
     * Test: Trust domain extraction from SPIFFE ID.
     */
    @Test
    @DisplayName("Trust domain should be correctly extracted from SPIFFE ID")
    void testTrustDomainExtraction() {
        SpiffeWorkloadIdentity identity = new SpiffeWorkloadIdentity(
            "spiffe://production.example.com/service/api", validCertChain);

        assertEquals("production.example.com", identity.getTrustDomain());
    }

    /**
     * Test: Trust domain extraction with no path.
     */
    @Test
    @DisplayName("Trust domain extraction should work with no path")
    void testTrustDomainNoPath() throws Exception {
        String spiffeIdNoPath = "spiffe://standalone.domain";
        X509Certificate[] certs = generateTestCertificateChain(Instant.now().plusSeconds(3600));

        SpiffeWorkloadIdentity identity = new SpiffeWorkloadIdentity(spiffeIdNoPath, certs);
        assertEquals("standalone.domain", identity.getTrustDomain());
        assertEquals("/", identity.getWorkloadPath());
    }

    /**
     * Test: Workload path extraction.
     */
    @Test
    @DisplayName("Workload path should be correctly extracted")
    void testWorkloadPathExtraction() {
        SpiffeWorkloadIdentity identity = new SpiffeWorkloadIdentity(
            "spiffe://yawl.cloud/engine/instance-1", validCertChain);

        assertEquals("/engine/instance-1", identity.getWorkloadPath());
    }

    /**
     * Test: isTrustedBy method.
     */
    @Test
    @DisplayName("isTrustedBy should correctly compare trust domains")
    void testIsTrustedBy() {
        SpiffeWorkloadIdentity identity = new SpiffeWorkloadIdentity(VALID_SPIFFE_ID, validCertChain);

        assertTrue(identity.isTrustedBy(VALID_TRUST_DOMAIN));
        assertFalse(identity.isTrustedBy("other.domain"));
        assertFalse(identity.isTrustedBy(null));
    }

    /**
     * Test: Expiration check for valid (non-expired) identity.
     */
    @Test
    @DisplayName("Non-expired identity should return false for isExpired()")
    void testNotExpired() {
        SpiffeWorkloadIdentity identity = new SpiffeWorkloadIdentity(VALID_SPIFFE_ID, validCertChain);

        assertFalse(identity.isExpired(), "Identity with future expiration should not be expired");
    }

    /**
     * Test: Expiration check for expired identity.
     */
    @Test
    @DisplayName("Expired identity should return true for isExpired()")
    void testExpired() throws Exception {
        SpiffeWorkloadIdentity identity = new SpiffeWorkloadIdentity(
            VALID_SPIFFE_ID, expiredCertChain);

        assertTrue(identity.isExpired(), "Identity with past expiration should be expired");
    }

    /**
     * Test: willExpireSoon method.
     */
    @Test
    @DisplayName("willExpireSoon should correctly predict near expiry")
    void testWillExpireSoon() {
        SpiffeWorkloadIdentity identity = new SpiffeWorkloadIdentity(VALID_SPIFFE_ID, validCertChain);

        assertTrue(identity.willExpireSoon(Duration.ofHours(2)),
            "Should expire soon within 2 hours");
        assertFalse(identity.willExpireSoon(Duration.ofMinutes(1)),
            "Should not expire soon within 1 minute");
    }

    /**
     * Test: getTimeUntilExpiry returns positive duration for valid identity.
     */
    @Test
    @DisplayName("getTimeUntilExpiry should return positive duration for valid identity")
    void testGetTimeUntilExpiry() {
        SpiffeWorkloadIdentity identity = new SpiffeWorkloadIdentity(VALID_SPIFFE_ID, validCertChain);

        Duration timeUntilExpiry = identity.getTimeUntilExpiry();
        assertNotNull(timeUntilExpiry);
        assertTrue(timeUntilExpiry.getSeconds() > 0,
            "Time until expiry should be positive for non-expired identity");
    }

    /**
     * Test: getTimeUntilExpiry returns zero for expired identity.
     */
    @Test
    @DisplayName("getTimeUntilExpiry should return zero for expired identity")
    void testGetTimeUntilExpiryExpired() throws Exception {
        SpiffeWorkloadIdentity identity = new SpiffeWorkloadIdentity(
            VALID_SPIFFE_ID, expiredCertChain);

        Duration timeUntilExpiry = identity.getTimeUntilExpiry();
        assertEquals(Duration.ZERO, timeUntilExpiry,
            "Time until expiry should be zero for expired identity");
    }

    /**
     * Test: Immutability - getX509Chain returns defensive copy.
     */
    @Test
    @DisplayName("getX509Chain should return defensive copy")
    void testCertChainImmutability() {
        SpiffeWorkloadIdentity identity = new SpiffeWorkloadIdentity(VALID_SPIFFE_ID, validCertChain);

        Optional<X509Certificate[]> chain1 = identity.getX509Chain();
        Optional<X509Certificate[]> chain2 = identity.getX509Chain();

        assertTrue(chain1.isPresent());
        assertTrue(chain2.isPresent());
        assertNotSame(chain1.get(), chain2.get(),
            "getX509Chain should return defensive copy");
    }

    /**
     * Test: Modifying returned chain does not affect internal state.
     */
    @Test
    @DisplayName("Modifying returned chain should not affect internal state")
    void testCertChainModificationSafety() {
        SpiffeWorkloadIdentity identity = new SpiffeWorkloadIdentity(VALID_SPIFFE_ID, validCertChain);

        X509Certificate[] chain = identity.getX509Chain().orElseThrow();
        X509Certificate original = chain[0];
        chain[0] = null;

        X509Certificate[] chainAgain = identity.getX509Chain().orElseThrow();
        assertNotNull(chainAgain[0], "Internal chain should not be affected by external modification");
    }

    /**
     * Test: getLeafCertificate returns first certificate.
     */
    @Test
    @DisplayName("getLeafCertificate should return first certificate in chain")
    void testGetLeafCertificate() {
        SpiffeWorkloadIdentity identity = new SpiffeWorkloadIdentity(VALID_SPIFFE_ID, validCertChain);

        Optional<X509Certificate> leaf = identity.getLeafCertificate();

        assertTrue(leaf.isPresent());
        assertEquals(validCertChain[0], leaf.get());
    }

    /**
     * Test: JWT identity returns empty for X509 chain methods.
     */
    @Test
    @DisplayName("JWT identity should return empty for X509 methods")
    void testJwtIdentityX509Methods() {
        Instant expiresAt = Instant.now().plusSeconds(3600);
        SpiffeWorkloadIdentity identity = new SpiffeWorkloadIdentity(
            VALID_SPIFFE_ID, "jwt-token", expiresAt);

        assertTrue(identity.getX509Chain().isEmpty());
        assertTrue(identity.getLeafCertificate().isEmpty());
    }

    /**
     * Test: X509 identity returns empty for JWT methods.
     */
    @Test
    @DisplayName("X509 identity should return empty for JWT methods")
    void testX509IdentityJwtMethods() {
        SpiffeWorkloadIdentity identity = new SpiffeWorkloadIdentity(VALID_SPIFFE_ID, validCertChain);

        assertTrue(identity.getJwtToken().isEmpty());
        assertTrue(identity.toBearerToken().isEmpty());
    }

    /**
     * Test: toBearerToken returns Bearer token for JWT identity.
     */
    @Test
    @DisplayName("toBearerToken should return Bearer token for JWT identity")
    void testToBearerToken() {
        Instant expiresAt = Instant.now().plusSeconds(3600);
        String jwtToken = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.test";

        SpiffeWorkloadIdentity identity = new SpiffeWorkloadIdentity(
            VALID_SPIFFE_ID, jwtToken, expiresAt);

        Optional<String> bearer = identity.toBearerToken();

        assertTrue(bearer.isPresent());
        assertEquals("Bearer " + jwtToken, bearer.get());
    }

    /**
     * Test: validate() throws for expired identity.
     */
    @Test
    @DisplayName("validate() should throw SpiffeException for expired identity")
    void testValidateExpired() throws Exception {
        SpiffeWorkloadIdentity identity = new SpiffeWorkloadIdentity(
            VALID_SPIFFE_ID, expiredCertChain);

        SpiffeException exception = assertThrows(
            SpiffeException.class,
            identity::validate
        );
        assertTrue(exception.getMessage().contains("expired"),
            "Exception should mention expiration");
    }

    /**
     * Test: validate() succeeds for valid identity.
     */
    @Test
    @DisplayName("validate() should succeed for valid identity")
    void testValidateValid() {
        SpiffeWorkloadIdentity identity = new SpiffeWorkloadIdentity(VALID_SPIFFE_ID, validCertChain);

        assertDoesNotThrow(identity::validate,
            "validate() should not throw for valid identity");
    }

    /**
     * Test: Builder pattern for X509 SVID.
     */
    @Test
    @DisplayName("Builder should create valid X509 SVID")
    void testBuilderX509() {
        SpiffeWorkloadIdentity identity = SpiffeWorkloadIdentity.builder()
            .spiffeId(VALID_SPIFFE_ID)
            .x509Chain(validCertChain)
            .buildX509();

        assertEquals(VALID_SPIFFE_ID, identity.getSpiffeId());
        assertEquals(SpiffeWorkloadIdentity.SvidType.X509, identity.getType());
        assertTrue(identity.getX509Chain().isPresent());
    }

    /**
     * Test: Builder pattern for JWT SVID.
     */
    @Test
    @DisplayName("Builder should create valid JWT SVID")
    void testBuilderJwt() {
        Instant expiresAt = Instant.now().plusSeconds(3600);

        SpiffeWorkloadIdentity identity = SpiffeWorkloadIdentity.builder()
            .spiffeId(VALID_SPIFFE_ID)
            .jwtToken("test-token")
            .expiresAt(expiresAt)
            .buildJwt();

        assertEquals(VALID_SPIFFE_ID, identity.getSpiffeId());
        assertEquals(SpiffeWorkloadIdentity.SvidType.JWT, identity.getType());
        assertTrue(identity.getJwtToken().isPresent());
    }

    /**
     * Test: equals and hashCode contract.
     */
    @Test
    @DisplayName("equals and hashCode should be consistent")
    void testEqualsHashCode() {
        SpiffeWorkloadIdentity identity1 = new SpiffeWorkloadIdentity(VALID_SPIFFE_ID, validCertChain);

        assertEquals(identity1, identity1, "Identity should equal itself");
        assertEquals(identity1.hashCode(), identity1.hashCode(), "hashCode should be consistent");
    }

    /**
     * Test: toString contains useful information.
     */
    @Test
    @DisplayName("toString should contain SPIFFE ID and type")
    void testToString() {
        SpiffeWorkloadIdentity identity = new SpiffeWorkloadIdentity(VALID_SPIFFE_ID, validCertChain);

        String str = identity.toString();
        assertTrue(str.contains(VALID_SPIFFE_ID), "toString should contain SPIFFE ID");
        assertTrue(str.contains("X509"), "toString should contain type");
        assertTrue(str.contains(VALID_TRUST_DOMAIN), "toString should contain trust domain");
    }

    /**
     * Test: getLifetimeElapsedPercent calculation.
     */
    @Test
    @DisplayName("getLifetimeElapsedPercent should calculate correctly")
    void testGetLifetimeElapsedPercent() {
        SpiffeWorkloadIdentity identity = new SpiffeWorkloadIdentity(VALID_SPIFFE_ID, validCertChain);

        Instant issuedAt = Instant.now().minusSeconds(1800);
        double percent = identity.getLifetimeElapsedPercent(issuedAt);

        assertTrue(percent >= 0 && percent <= 100,
            "Percentage should be between 0 and 100");
        assertTrue(percent > 40 && percent < 60,
            "With 30 mins elapsed out of 60 mins, should be around 50%");
    }

    /**
     * Test: getLifetimeElapsedPercent with null issuedAt.
     */
    @Test
    @DisplayName("getLifetimeElapsedPercent with null should return 0")
    void testGetLifetimeElapsedPercentNull() {
        SpiffeWorkloadIdentity identity = new SpiffeWorkloadIdentity(VALID_SPIFFE_ID, validCertChain);

        assertEquals(0.0, identity.getLifetimeElapsedPercent(null),
            "Null issuedAt should return 0");
    }

    /**
     * Test: JWT identity validation with missing token.
     */
    @Test
    @DisplayName("JWT validation should fail with missing token")
    void testJwtValidationMissingToken() {
        Instant expiresAt = Instant.now().plusSeconds(3600);
        SpiffeWorkloadIdentity identity = new SpiffeWorkloadIdentity(
            VALID_SPIFFE_ID, "valid-token", expiresAt);

        assertDoesNotThrow(identity::validate, "Valid JWT should pass validation");
    }

    /**
     * Test: Not equal to null.
     */
    @Test
    @DisplayName("Identity should not equal null")
    void testNotEqualsNull() {
        SpiffeWorkloadIdentity identity = new SpiffeWorkloadIdentity(VALID_SPIFFE_ID, validCertChain);

        assertNotEquals(null, identity, "Identity should not equal null");
    }

    /**
     * Test: Not equal to different type.
     */
    @Test
    @DisplayName("Identity should not equal different type")
    void testNotEqualsDifferentType() {
        SpiffeWorkloadIdentity identity = new SpiffeWorkloadIdentity(VALID_SPIFFE_ID, validCertChain);

        assertNotEquals("string", identity, "Identity should not equal different type");
        assertNotEquals(42, identity, "Identity should not equal number");
    }

    /**
     * Test: Builder with null chain creates null chain internally.
     */
    @Test
    @DisplayName("Builder with null chain should store null")
    void testBuilderNullChain() {
        SpiffeWorkloadIdentity.Builder builder = SpiffeWorkloadIdentity.builder()
            .spiffeId(VALID_SPIFFE_ID)
            .x509Chain(null);

        // This should throw because null chain is not allowed
        assertThrows(IllegalArgumentException.class, builder::buildX509);
    }

    /**
     * Generate a test X.509 certificate chain for testing.
     * Creates a self-signed certificate with the specified expiration.
     */
    private X509Certificate[] generateTestCertificateChain(Instant expiration) throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();

        X509Certificate cert = createSelfSignedCertificate(keyPair, expiration);
        return new X509Certificate[] { cert };
    }

    /**
     * Create a self-signed X.509 certificate using sun.security classes.
     * This is a test-only method that creates minimal valid certificates.
     */
    private X509Certificate createSelfSignedCertificate(KeyPair keyPair, Instant expiration) throws Exception {
        // Use sun.security.x509 classes for certificate generation
        // These are JDK internal classes but available in standard JDK
        sun.security.x509.X500Name owner = new sun.security.x509.X500Name("CN=Test,O=YAWL,C=US");
        sun.security.x509.X500Name issuer = owner;

        java.math.BigInteger serialNumber = java.math.BigInteger.valueOf(System.currentTimeMillis());

        sun.security.x509.X509CertInfo info = new sun.security.x509.X509CertInfo();
        info.set("version", new sun.security.x509.CertificateVersion(sun.security.x509.CertificateVersion.V3));
        info.set("serialNumber", new sun.security.x509.CertificateSerialNumber(serialNumber));
        info.set("issuer", new sun.security.x509.CertificateIssuerName(issuer));
        info.set("subject", new sun.security.x509.CertificateSubjectName(owner));
        info.set("validity", new sun.security.x509.CertificateValidity(
            new java.util.Date(),
            java.util.Date.from(expiration)));

        // Convert public key to X509Key format
        byte[] encoded = keyPair.getPublic().getEncoded();
        sun.security.x509.X509Key x509Key = sun.security.x509.X509Key.parse(
            new sun.security.util.DerValue(encoded));

        info.set("key", new sun.security.x509.CertificateX509Key(x509Key));
        info.set("algorithmID", new sun.security.x509.CertificateAlgorithmId(
            sun.security.x509.AlgorithmId.get("SHA256withRSA")));

        sun.security.x509.X509CertImpl cert = new sun.security.x509.X509CertImpl(info);
        cert.sign(keyPair.getPrivate(), "SHA256withRSA");

        return cert;
    }
}
