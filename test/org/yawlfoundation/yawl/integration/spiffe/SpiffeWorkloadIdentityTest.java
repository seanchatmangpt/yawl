package org.yawlfoundation.yawl.integration.spiffe;

import static org.junit.jupiter.api.Assertions.*;

import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for SpiffeWorkloadIdentity.
 * Tests SPIFFE ID validation, JWT SVID functionality, and immutability.
 *
 * Uses JWT SVIDs for most tests as they don't require complex certificate setup.
 * For X509 tests, uses a test certificate implementation.
 *
 * Chicago TDD style - tests real identity behavior.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
@Tag("unit")
class SpiffeWorkloadIdentityTest {

    private X509Certificate[] validCertChain;
    private static final String VALID_SPIFFE_ID = "spiffe://yawl.cloud/engine/instance-1";
    private static final String VALID_TRUST_DOMAIN = "yawl.cloud";

    @BeforeEach
    void setUp() throws Exception {
        // Create a test certificate chain for X509 tests
        validCertChain = createTestCertificateChain(Instant.now().plusSeconds(3600));
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
     * Test: Valid X.509 SVID creation with test certificates.
     */
    @Test
    @DisplayName("Valid X.509 SVID should be created with test certificates")
    void testValidX509SvidCreation() {
        SpiffeWorkloadIdentity identity = new SpiffeWorkloadIdentity(VALID_SPIFFE_ID, validCertChain);

        assertEquals(VALID_SPIFFE_ID, identity.getSpiffeId());
        assertEquals(SpiffeWorkloadIdentity.SvidType.X509, identity.getType());
        assertEquals(VALID_TRUST_DOMAIN, identity.getTrustDomain());
        assertNotNull(identity.getExpiresAt());
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
     * Test: Expiration check for valid (non-expired) JWT identity.
     */
    @Test
    @DisplayName("Non-expired JWT identity should return false for isExpired()")
    void testJwtNotExpired() {
        Instant expiresAt = Instant.now().plusSeconds(3600);
        SpiffeWorkloadIdentity identity = new SpiffeWorkloadIdentity(VALID_SPIFFE_ID, "token", expiresAt);

        assertFalse(identity.isExpired(), "Identity with future expiration should not be expired");
    }

    /**
     * Test: Expiration check for expired JWT identity.
     */
    @Test
    @DisplayName("Expired JWT identity should return true for isExpired()")
    void testJwtExpired() {
        Instant expiresAt = Instant.now().minusSeconds(3600);
        SpiffeWorkloadIdentity identity = new SpiffeWorkloadIdentity(VALID_SPIFFE_ID, "token", expiresAt);

        assertTrue(identity.isExpired(), "Identity with past expiration should be expired");
    }

    /**
     * Test: willExpireSoon method.
     */
    @Test
    @DisplayName("willExpireSoon should correctly predict near expiry")
    void testWillExpireSoon() {
        Instant expiresAt = Instant.now().plusSeconds(3600);
        SpiffeWorkloadIdentity identity = new SpiffeWorkloadIdentity(VALID_SPIFFE_ID, "token", expiresAt);

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
        Instant expiresAt = Instant.now().plusSeconds(3600);
        SpiffeWorkloadIdentity identity = new SpiffeWorkloadIdentity(VALID_SPIFFE_ID, "token", expiresAt);

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
    void testGetTimeUntilExpiryExpired() {
        Instant expiresAt = Instant.now().minusSeconds(3600);
        SpiffeWorkloadIdentity identity = new SpiffeWorkloadIdentity(VALID_SPIFFE_ID, "token", expiresAt);

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
    void testValidateExpired() {
        Instant expiresAt = Instant.now().minusSeconds(3600);
        SpiffeWorkloadIdentity identity = new SpiffeWorkloadIdentity(
            VALID_SPIFFE_ID, "token", expiresAt);

        SpiffeException exception = assertThrows(
            SpiffeException.class,
            identity::validate
        );
        assertTrue(exception.getMessage().contains("expired"),
            "Exception should mention expiration");
    }

    /**
     * Test: validate() succeeds for valid JWT identity.
     */
    @Test
    @DisplayName("validate() should succeed for valid JWT identity")
    void testValidateValidJwt() {
        Instant expiresAt = Instant.now().plusSeconds(3600);
        SpiffeWorkloadIdentity identity = new SpiffeWorkloadIdentity(
            VALID_SPIFFE_ID, "token", expiresAt);

        assertDoesNotThrow(identity::validate,
            "validate() should not throw for valid JWT identity");
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
        Instant expiresAt = Instant.now().plusSeconds(3600);
        SpiffeWorkloadIdentity identity1 = new SpiffeWorkloadIdentity(VALID_SPIFFE_ID, "token", expiresAt);
        SpiffeWorkloadIdentity identity2 = new SpiffeWorkloadIdentity(VALID_SPIFFE_ID, "token", expiresAt);

        assertEquals(identity1, identity2, "Same JWT identities should be equal");
        assertEquals(identity1.hashCode(), identity2.hashCode(), "hashCode should be consistent");
    }

    /**
     * Test: toString contains useful information.
     */
    @Test
    @DisplayName("toString should contain SPIFFE ID and type")
    void testToString() {
        Instant expiresAt = Instant.now().plusSeconds(3600);
        SpiffeWorkloadIdentity identity = new SpiffeWorkloadIdentity(VALID_SPIFFE_ID, "token", expiresAt);

        String str = identity.toString();
        assertTrue(str.contains(VALID_SPIFFE_ID), "toString should contain SPIFFE ID");
        assertTrue(str.contains("JWT"), "toString should contain type");
        assertTrue(str.contains(VALID_TRUST_DOMAIN), "toString should contain trust domain");
    }

    /**
     * Test: getLifetimeElapsedPercent calculation.
     */
    @Test
    @DisplayName("getLifetimeElapsedPercent should calculate correctly")
    void testGetLifetimeElapsedPercent() {
        // Total lifetime = 60 mins: issued 30 mins ago, expires 30 mins from now
        Instant issuedAt = Instant.now().minusSeconds(1800);
        Instant expiresAt = Instant.now().plusSeconds(1800);
        SpiffeWorkloadIdentity identity = new SpiffeWorkloadIdentity(VALID_SPIFFE_ID, "token", expiresAt);

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
        Instant expiresAt = Instant.now().plusSeconds(3600);
        SpiffeWorkloadIdentity identity = new SpiffeWorkloadIdentity(VALID_SPIFFE_ID, "token", expiresAt);

        assertEquals(0.0, identity.getLifetimeElapsedPercent(null),
            "Null issuedAt should return 0");
    }

    /**
     * Test: Not equal to null.
     */
    @Test
    @DisplayName("Identity should not equal null")
    void testNotEqualsNull() {
        Instant expiresAt = Instant.now().plusSeconds(3600);
        SpiffeWorkloadIdentity identity = new SpiffeWorkloadIdentity(VALID_SPIFFE_ID, "token", expiresAt);

        assertNotEquals(null, identity, "Identity should not equal null");
    }

    /**
     * Test: Not equal to different type.
     */
    @Test
    @DisplayName("Identity should not equal different type")
    void testNotEqualsDifferentType() {
        Instant expiresAt = Instant.now().plusSeconds(3600);
        SpiffeWorkloadIdentity identity = new SpiffeWorkloadIdentity(VALID_SPIFFE_ID, "token", expiresAt);

        assertNotEquals("string", identity, "Identity should not equal different type");
        assertNotEquals(42, identity, "Identity should not equal number");
    }

    /**
     * Create a test certificate chain using a concrete X509Certificate implementation.
     * This provides the minimum implementation needed for testing SpiffeWorkloadIdentity.
     */
    private X509Certificate[] createTestCertificateChain(Instant expiration) {
        X509Certificate testCert = new TestX509Certificate(expiration);
        return new X509Certificate[] { testCert };
    }

    /**
     * Concrete X509Certificate implementation for testing.
     * Provides real certificate behavior for the methods used by SpiffeWorkloadIdentity.
     */
    private static class TestX509Certificate extends X509Certificate {
        private static final long serialVersionUID = 1L;
        private final Date notAfter;
        private final Date notBefore;

        TestX509Certificate(Instant expiration) {
            this.notAfter = Date.from(expiration);
            this.notBefore = new Date();
        }

        @Override
        public byte[] getEncoded() {
            return new byte[0];
        }

        @Override
        public void verify(java.security.PublicKey key) {
            // No external public key verification in test
        }

        @Override
        public void verify(java.security.PublicKey key, String sigProvider) {
            // No external public key verification in test
        }

        @Override
        public String toString() {
            return "TestX509Certificate[notAfter=" + notAfter + "]";
        }

        @Override
        public java.security.PublicKey getPublicKey() {
            return null;
        }

        @Override
        public java.math.BigInteger getSerialNumber() {
            return java.math.BigInteger.ONE;
        }

        @Override
        public java.security.Principal getSubjectDN() {
            return () -> "CN=Test,O=YAWL,C=US";
        }

        @Override
        public java.security.Principal getIssuerDN() {
            return () -> "CN=Test,O=YAWL,C=US";
        }

        @Override
        public Date getNotBefore() {
            return notBefore;
        }

        @Override
        public Date getNotAfter() {
            return notAfter;
        }

        @Override
        public byte[] getTBSCertificate() {
            return new byte[0];
        }

        @Override
        public byte[] getSignature() {
            return new byte[0];
        }

        @Override
        public String getSigAlgName() {
            return "SHA256withRSA";
        }

        @Override
        public String getSigAlgOID() {
            return "1.2.840.113549.1.1.11";
        }

        @Override
        public byte[] getSigAlgParams() {
            return new byte[0];
        }

        @Override
        public boolean[] getIssuerUniqueID() {
            return null;
        }

        @Override
        public boolean[] getSubjectUniqueID() {
            return null;
        }

        @Override
        public boolean[] getKeyUsage() {
            return new boolean[] { true };
        }

        @Override
        public int getBasicConstraints() {
            return -1;
        }

        @Override
        public void checkValidity() throws java.security.cert.CertificateExpiredException {
            if (Instant.now().isAfter(notAfter.toInstant())) {
                throw new java.security.cert.CertificateExpiredException("Certificate expired");
            }
        }

        @Override
        public void checkValidity(Date date) throws java.security.cert.CertificateExpiredException {
            if (date.toInstant().isAfter(notAfter.toInstant())) {
                throw new java.security.cert.CertificateExpiredException("Certificate expired");
            }
        }

        @Override
        public int getVersion() {
            return 3;
        }

        @Override
        public java.util.Set<String> getCriticalExtensionOIDs() {
            return java.util.Collections.emptySet();
        }

        @Override
        public java.util.Set<String> getNonCriticalExtensionOIDs() {
            return java.util.Collections.emptySet();
        }

        @Override
        public byte[] getExtensionValue(String oid) {
            return null;
        }

        @Override
        public boolean hasUnsupportedCriticalExtension() {
            return false;
        }
    }
}
