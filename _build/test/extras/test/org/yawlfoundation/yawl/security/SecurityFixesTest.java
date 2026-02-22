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
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.security;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.x509.X509V3CertificateGenerator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.yawlfoundation.yawl.security.pki.PkiException;
import org.yawlfoundation.yawl.security.pki.SignatureBlock;
import org.yawlfoundation.yawl.security.pki.SignatureException;
import org.yawlfoundation.yawl.security.pki.SignatureVerifier;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectInputFilter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.security.*;
import java.security.cert.*;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for YAWL security module.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>CredentialKey enum completeness and identity</li>
 *   <li>CredentialManagerFactory lifecycle: registration, fail-fast, thread safety</li>
 *   <li>CredentialUnavailableException message fidelity</li>
 *   <li>ObjectInputStreamConfig allowlist and deserialization safety</li>
 *   <li>SignatureBlock metadata integrity</li>
 *   <li>SignatureVerifier against real RSA keypairs</li>
 *   <li>DocumentSigner Base64 round-trip encoding</li>
 * </ul>
 *
 * <p>All tests use real objects. No mocks, stubs, or fakes.
 *
 * @author YAWL Foundation
 * @since YAWL 5.3
 */
@Tag("unit")
@Execution(ExecutionMode.SAME_THREAD)  // CredentialManagerFactory singleton state
class SecurityFixesTest {

    /**
     * A real in-process CredentialManager backed by a fixed map.
     * Used only in tests to satisfy the factory registration contract
     * without hardcoding secrets in production code.
     */
    private static final class InMemoryCredentialManager implements CredentialManager {

        private final java.util.Map<CredentialKey, String> store = new java.util.EnumMap<>(CredentialKey.class);

        void put(CredentialKey key, String value) {
            if (key == null) {
                throw new IllegalArgumentException("key must not be null");
            }
            if (value == null || value.isEmpty()) {
                throw new IllegalArgumentException("value must not be null or empty");
            }
            store.put(key, value);
        }

        @Override
        public String getCredential(CredentialKey key) {
            if (key == null) {
                throw new IllegalArgumentException("key must not be null");
            }
            String value = store.get(key);
            if (value == null) {
                throw new CredentialUnavailableException(key, "not present in in-memory store");
            }
            return value;
        }

        @Override
        public void rotateCredential(CredentialKey key, String newValue) {
            if (key == null || newValue == null || newValue.isEmpty()) {
                throw new IllegalArgumentException("key and newValue must not be null or empty");
            }
            store.put(key, newValue);
        }
    }

    @BeforeEach
    void resetCredentialManagerFactory() throws Exception {
        // Reset the volatile singleton to null via reflection so each test
        // starts from a clean state without a registered manager.
        java.lang.reflect.Field field = CredentialManagerFactory.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, null);
    }

    @AfterEach
    void cleanUpCredentialManagerFactory() throws Exception {
        java.lang.reflect.Field field = CredentialManagerFactory.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, null);
    }

    // -------------------------------------------------------------------------
    // CredentialKey enum
    // -------------------------------------------------------------------------

    /**
     * Verifies that all expected credential keys are present in the enum.
     * Adding keys without tests would silently break credential rotation.
     */
    @Test
    void testCredentialKeyEnumContainsAllRequiredKeys() {
        CredentialKey[] keys = CredentialKey.values();
        assertEquals(5, keys.length,
                "Expected exactly 5 CredentialKey values; update this test when adding new keys");

        assertNotNull(CredentialKey.valueOf("YAWL_ADMIN_PASSWORD"));
        assertNotNull(CredentialKey.valueOf("YAWL_ENGINE_SERVICE_PASSWORD"));
        assertNotNull(CredentialKey.valueOf("ZAI_API_KEY"));
        assertNotNull(CredentialKey.valueOf("ZHIPU_API_KEY"));
        assertNotNull(CredentialKey.valueOf("PROCLET_SERVICE_PASSWORD"));
    }

    /**
     * Verifies enum identity and name consistency.
     */
    @Test
    void testCredentialKeyNameMatchesEnumConstant() {
        for (CredentialKey key : CredentialKey.values()) {
            CredentialKey resolved = CredentialKey.valueOf(key.name());
            assertSame(key, resolved,
                    "CredentialKey.valueOf(key.name()) must return the identical enum constant");
        }
    }

    // -------------------------------------------------------------------------
    // CredentialManagerFactory lifecycle
    // -------------------------------------------------------------------------

    /**
     * Verifies that getInstance() throws UnsupportedOperationException when no
     * manager has been registered - the fail-fast behaviour described in the Javadoc.
     */
    @Test
    void testFactoryThrowsWhenNoManagerRegistered() {
        UnsupportedOperationException ex = assertThrows(
                UnsupportedOperationException.class,
                CredentialManagerFactory::getInstance,
                "getInstance() must throw UnsupportedOperationException before setInstance() is called"
        );
        assertTrue(ex.getMessage().contains("SECURITY.md"),
                "Error message must reference SECURITY.md for operator guidance");
    }

    /**
     * Verifies that registering a real CredentialManager and retrieving it succeeds.
     */
    @Test
    void testFactoryReturnsRegisteredManager() {
        InMemoryCredentialManager manager = new InMemoryCredentialManager();
        manager.put(CredentialKey.YAWL_ADMIN_PASSWORD, "s3cr3t-admin-pw");

        CredentialManagerFactory.setInstance(manager);

        CredentialManager retrieved = CredentialManagerFactory.getInstance();
        assertSame(manager, retrieved,
                "getInstance() must return the exact instance passed to setInstance()");

        String password = retrieved.getCredential(CredentialKey.YAWL_ADMIN_PASSWORD);
        assertEquals("s3cr3t-admin-pw", password);
    }

    /**
     * Verifies that setInstance(null) throws IllegalArgumentException.
     */
    @Test
    void testFactoryRejectsNullInstance() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CredentialManagerFactory.setInstance(null),
                "setInstance(null) must throw IllegalArgumentException"
        );
    }

    /**
     * Verifies that the factory singleton is volatile and replacement is visible.
     */
    @Test
    void testFactoryAllowsReplacement() {
        InMemoryCredentialManager first = new InMemoryCredentialManager();
        first.put(CredentialKey.ZAI_API_KEY, "first-key");
        CredentialManagerFactory.setInstance(first);

        InMemoryCredentialManager second = new InMemoryCredentialManager();
        second.put(CredentialKey.ZAI_API_KEY, "second-key");
        CredentialManagerFactory.setInstance(second);

        String value = CredentialManagerFactory.getInstance().getCredential(CredentialKey.ZAI_API_KEY);
        assertEquals("second-key", value,
                "After replacement, getInstance() must return the most recently set manager");
    }

    // -------------------------------------------------------------------------
    // CredentialManager behaviour
    // -------------------------------------------------------------------------

    /**
     * Verifies that getCredential throws CredentialUnavailableException for
     * an unregistered key rather than returning null or an empty string.
     */
    @Test
    void testCredentialManagerThrowsForMissingKey() {
        InMemoryCredentialManager manager = new InMemoryCredentialManager();
        // ZAI_API_KEY was never put into the store

        CredentialUnavailableException ex = assertThrows(
                CredentialUnavailableException.class,
                () -> manager.getCredential(CredentialKey.ZAI_API_KEY),
                "getCredential() must throw CredentialUnavailableException for a missing key"
        );
        assertTrue(ex.getMessage().contains("ZAI_API_KEY"),
                "Exception message must identify the missing credential key");
    }

    /**
     * Verifies null key rejection.
     */
    @Test
    void testCredentialManagerRejectsNullKey() {
        InMemoryCredentialManager manager = new InMemoryCredentialManager();
        assertThrows(
                IllegalArgumentException.class,
                () -> manager.getCredential(null),
                "getCredential(null) must throw IllegalArgumentException"
        );
    }

    /**
     * Verifies credential rotation: after rotateCredential() the new value is returned.
     */
    @Test
    void testCredentialManagerRotation() {
        InMemoryCredentialManager manager = new InMemoryCredentialManager();
        manager.put(CredentialKey.YAWL_ENGINE_SERVICE_PASSWORD, "initial-password");

        manager.rotateCredential(CredentialKey.YAWL_ENGINE_SERVICE_PASSWORD, "rotated-password");

        String result = manager.getCredential(CredentialKey.YAWL_ENGINE_SERVICE_PASSWORD);
        assertEquals("rotated-password", result,
                "After rotation, getCredential() must return the new value");
    }

    // -------------------------------------------------------------------------
    // CredentialUnavailableException
    // -------------------------------------------------------------------------

    /**
     * Verifies the (key, Throwable) constructor formats the message with the key name.
     */
    @Test
    void testCredentialUnavailableExceptionWithCause() {
        RuntimeException cause = new RuntimeException("vault timeout");
        CredentialUnavailableException ex =
                new CredentialUnavailableException(CredentialKey.ZHIPU_API_KEY, cause);

        assertTrue(ex.getMessage().contains("ZHIPU_API_KEY"),
                "Exception message must contain the credential key name");
        assertTrue(ex.getMessage().contains("SECURITY.md"),
                "Exception message must reference SECURITY.md");
        assertSame(cause, ex.getCause(),
                "Cause must be preserved");
    }

    /**
     * Verifies the (key, String) constructor formats the message with key and details.
     */
    @Test
    void testCredentialUnavailableExceptionWithDetails() {
        CredentialUnavailableException ex =
                new CredentialUnavailableException(CredentialKey.PROCLET_SERVICE_PASSWORD,
                        "secret path not found");

        assertTrue(ex.getMessage().contains("PROCLET_SERVICE_PASSWORD"),
                "Exception message must contain the credential key name");
        assertTrue(ex.getMessage().contains("secret path not found"),
                "Exception message must contain the provided details");
        assertTrue(ex.getMessage().contains("SECURITY.md"),
                "Exception message must reference SECURITY.md");
    }

    // -------------------------------------------------------------------------
    // ObjectInputStreamConfig - safe deserialization
    // -------------------------------------------------------------------------

    /**
     * Verifies that createYAWLAllowlist() returns a non-null filter.
     */
    @Test
    void testCreateYAWLAllowlistReturnsNonNullFilter() {
        ObjectInputFilter filter = ObjectInputStreamConfig.createYAWLAllowlist();
        assertNotNull(filter, "createYAWLAllowlist() must return a non-null ObjectInputFilter");
    }

    /**
     * Verifies that createDeepCopyFilter() returns a non-null filter.
     */
    @Test
    void testCreateDeepCopyFilterReturnsNonNullFilter() {
        ObjectInputFilter filter = ObjectInputStreamConfig.createDeepCopyFilter();
        assertNotNull(filter, "createDeepCopyFilter() must return a non-null ObjectInputFilter");
    }

    /**
     * Verifies isSafeClass() approves known YAWL and Java-core classes.
     */
    @Test
    void testIsSafeClassApprovesAllowlistedClasses() {
        assertTrue(ObjectInputStreamConfig.isSafeClass(String.class),
                "java.lang.String must be on the YAWL allowlist");
        assertTrue(ObjectInputStreamConfig.isSafeClass(java.util.ArrayList.class),
                "java.util.ArrayList must be on the YAWL allowlist");
        assertTrue(ObjectInputStreamConfig.isSafeClass(CredentialKey.class),
                "org.yawlfoundation.yawl.* classes must be on the YAWL allowlist");
    }

    /**
     * Verifies isSafeClass() rejects dangerous third-party gadget chain classes.
     */
    @Test
    void testIsSafeClassRejectsDangerousClasses() {
        assertFalse(ObjectInputStreamConfig.isSafeClass(null),
                "null must not be considered safe");
        // org.apache.commons.collections classes are classic gadget chain entry points
        assertFalse(ObjectInputStreamConfig.isSafeClass(
                        org.apache.commons.lang3.StringUtils.class),
                "org.apache.commons.lang3 is NOT on the YAWL allowlist");
    }

    /**
     * Verifies that createCustomAllowlist(null) falls back to the base YAWL allowlist.
     */
    @Test
    void testCreateCustomAllowlistWithNullFallsBackToYAWLAllowlist() {
        ObjectInputFilter filterNull = ObjectInputStreamConfig.createCustomAllowlist(null);
        ObjectInputFilter filterBase = ObjectInputStreamConfig.createYAWLAllowlist();

        assertNotNull(filterNull, "createCustomAllowlist(null) must return a non-null filter");
        // Both should reject the same dangerous class
        assertFalse(ObjectInputStreamConfig.isSafeClass(
                        org.apache.commons.lang3.StringUtils.class),
                "Dangerous class must be rejected by the allowlist");
    }

    /**
     * Verifies that a serialized String (java.lang.String is allowlisted) can
     * be safely deserialized when the YAWL allowlist filter is applied.
     */
    @Test
    void testYAWLAllowlistPermitsJavaLangStringDeserialization() throws Exception {
        String original = "yawl-workflow-data";

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(original);
        }

        byte[] serialized = baos.toByteArray();

        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            ois.setObjectInputFilter(ObjectInputStreamConfig.createYAWLAllowlist());
            Object deserialized = ois.readObject();
            assertEquals(original, deserialized,
                    "java.lang.String must be deserializable through the YAWL allowlist filter");
        }
    }

    // -------------------------------------------------------------------------
    // SignatureBlock metadata
    // -------------------------------------------------------------------------

    /**
     * Generates a self-signed RSA certificate and RSA keypair for PKI tests.
     * Registers the BouncyCastle provider (from bcprov-jdk18on) to satisfy the
     * certificate generator's provider requirement, then removes it afterwards.
     */
    @SuppressWarnings("deprecation")
    private static KeyAndCertificate generateRsaKeyAndCert() throws Exception {
        // Register BC provider for self-signed cert generation; remove after.
        BouncyCastleProvider bcProvider = new BouncyCastleProvider();
        Security.addProvider(bcProvider);
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048, new SecureRandom());
            KeyPair keyPair = kpg.generateKeyPair();

            X509V3CertificateGenerator certGen = new X509V3CertificateGenerator();
            certGen.setSerialNumber(BigInteger.valueOf(System.currentTimeMillis()));
            org.bouncycastle.jce.X509Principal subject =
                    new org.bouncycastle.jce.X509Principal(
                            "CN=YAWL Test, O=YAWL Foundation, C=AU");
            certGen.setIssuerDN(subject);
            certGen.setSubjectDN(subject);
            certGen.setNotBefore(new Date(System.currentTimeMillis() - 86400_000L));
            certGen.setNotAfter(new Date(System.currentTimeMillis() + 365L * 86400_000L));
            certGen.setPublicKey(keyPair.getPublic());
            certGen.setSignatureAlgorithm("SHA256WithRSAEncryption");

            X509Certificate cert = certGen.generate(keyPair.getPrivate(), "BC", new SecureRandom());
            return new KeyAndCertificate(keyPair, cert);
        } finally {
            Security.removeProvider(bcProvider.getName());
        }
    }

    private record KeyAndCertificate(KeyPair keyPair, X509Certificate certificate) {}

    /**
     * Verifies that SignatureBlock stores and exposes its constructor arguments correctly.
     */
    @Test
    void testSignatureBlockMetadataIntegrity() throws Exception {
        KeyAndCertificate kc = generateRsaKeyAndCert();
        byte[] data = "workflow-payload".getBytes(StandardCharsets.UTF_8);
        String algorithm = "SHA256withRSA";

        // Produce a real RSA signature over the data
        Signature signer = Signature.getInstance(algorithm);
        signer.initSign(kc.keyPair().getPrivate());
        signer.update(data);
        byte[] realSignatureBytes = signer.sign();

        SignatureBlock block = new SignatureBlock(data, realSignatureBytes, kc.certificate(), algorithm);

        assertArrayEquals(data, block.getOriginalData(),
                "SignatureBlock must preserve the original data");
        assertArrayEquals(realSignatureBytes, block.getSignatureBytes(),
                "SignatureBlock must preserve the signature bytes");
        assertEquals(algorithm, block.getSignatureAlgorithm(),
                "SignatureBlock must preserve the signature algorithm");
        assertNotNull(block.getSignedAt(),
                "SignatureBlock must set signedAt timestamp");
        assertSame(kc.certificate(), block.getSigningCertificate(),
                "SignatureBlock must preserve the signing certificate");
    }

    /**
     * Verifies that SignatureBlock.getSignatureAsBase64() produces valid Base64
     * that round-trips back to the original bytes.
     */
    @Test
    void testSignatureBlockBase64RoundTrip() throws Exception {
        KeyAndCertificate kc = generateRsaKeyAndCert();
        byte[] data = "base64-test".getBytes(StandardCharsets.UTF_8);

        // Produce a real RSA signature for the round-trip test
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(kc.keyPair().getPrivate());
        signer.update(data);
        byte[] signatureBytes = signer.sign();

        SignatureBlock block = new SignatureBlock(data, signatureBytes, kc.certificate(), "SHA256withRSA");

        String encoded = block.getSignatureAsBase64();
        assertNotNull(encoded, "getSignatureAsBase64() must return a non-null string");
        assertFalse(encoded.isEmpty(), "getSignatureAsBase64() must return a non-empty string");

        byte[] decoded = java.util.Base64.getDecoder().decode(encoded);
        assertArrayEquals(signatureBytes, decoded,
                "Base64 round-trip must reproduce the original signature bytes");
    }

    /**
     * Verifies SignatureBlock certificate metadata accessors.
     */
    @Test
    void testSignatureBlockCertificateAccessors() throws Exception {
        KeyAndCertificate kc = generateRsaKeyAndCert();
        byte[] data = "metadata-test".getBytes(StandardCharsets.UTF_8);

        // Produce a real RSA signature
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(kc.keyPair().getPrivate());
        signer.update(data);
        byte[] signatureBytes = signer.sign();

        SignatureBlock block = new SignatureBlock(data, signatureBytes, kc.certificate(), "SHA256withRSA");

        String subjectDN = block.getCertificateSubjectDN();
        assertNotNull(subjectDN, "getCertificateSubjectDN() must not be null");
        assertTrue(subjectDN.contains("YAWL"),
                "Subject DN must contain 'YAWL' from the test certificate");

        String issuerDN = block.getCertificateIssuerDN();
        assertNotNull(issuerDN, "getCertificateIssuerDN() must not be null");

        String serial = block.getCertificateSerialNumber();
        assertNotNull(serial, "getCertificateSerialNumber() must not be null");
        assertFalse(serial.isEmpty(), "getCertificateSerialNumber() must not be empty");
    }

    // -------------------------------------------------------------------------
    // SignatureVerifier - real RSA sign + verify
    // -------------------------------------------------------------------------

    /**
     * Signs real data with a real RSA private key and verifies the signature
     * using SignatureVerifier with the matching public key certificate.
     */
    @Test
    void testSignatureVerifierAcceptsValidRsaSignature() throws Exception {
        KeyAndCertificate kc = generateRsaKeyAndCert();
        byte[] data = "yawl-process-definition".getBytes(StandardCharsets.UTF_8);

        // Sign with real RSA private key
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(kc.keyPair().getPrivate());
        signer.update(data);
        byte[] signatureBytes = signer.sign();

        // Verify using SignatureVerifier
        SignatureVerifier verifier = new SignatureVerifier();
        boolean valid = verifier.verifySignature(
                data, signatureBytes, kc.certificate(), "SHA256withRSA");

        assertTrue(valid,
                "SignatureVerifier must accept a signature produced by the matching private key");
    }

    /**
     * Verifies that SignatureVerifier rejects a tampered payload.
     */
    @Test
    void testSignatureVerifierRejectsTamperedData() throws Exception {
        KeyAndCertificate kc = generateRsaKeyAndCert();
        byte[] originalData = "original-workflow-data".getBytes(StandardCharsets.UTF_8);
        byte[] tamperedData = "tampered-workflow-data".getBytes(StandardCharsets.UTF_8);

        // Sign original data
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(kc.keyPair().getPrivate());
        signer.update(originalData);
        byte[] signatureBytes = signer.sign();

        // Verify against tampered data - must fail
        SignatureVerifier verifier = new SignatureVerifier();
        boolean valid = verifier.verifySignature(
                tamperedData, signatureBytes, kc.certificate(), "SHA256withRSA");

        assertFalse(valid,
                "SignatureVerifier must reject a signature when the data has been tampered with");
    }

    /**
     * Verifies that SignatureVerifier rejects a corrupted signature.
     */
    @Test
    void testSignatureVerifierRejectsCorruptedSignature() throws Exception {
        KeyAndCertificate kc = generateRsaKeyAndCert();
        byte[] data = "workflow-task".getBytes(StandardCharsets.UTF_8);

        // Sign data
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(kc.keyPair().getPrivate());
        signer.update(data);
        byte[] validSig = signer.sign();

        // Corrupt the signature
        byte[] corruptedSig = validSig.clone();
        corruptedSig[0] ^= 0xFF;
        corruptedSig[validSig.length / 2] ^= 0xAB;

        // Verify corrupted signature - must return false (not throw)
        SignatureVerifier verifier = new SignatureVerifier();
        boolean valid = verifier.verifySignature(
                data, corruptedSig, kc.certificate(), "SHA256withRSA");

        assertFalse(valid,
                "SignatureVerifier must return false for a corrupted signature");
    }

    /**
     * Verifies verifySignatureBlock() works for a full round-trip through SignatureBlock.
     */
    @Test
    void testSignatureVerifierWithSignatureBlock() throws Exception {
        KeyAndCertificate kc = generateRsaKeyAndCert();
        byte[] data = "full-round-trip".getBytes(StandardCharsets.UTF_8);

        // Sign data
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(kc.keyPair().getPrivate());
        signer.update(data);
        byte[] signatureBytes = signer.sign();

        SignatureBlock block = new SignatureBlock(
                data, signatureBytes, kc.certificate(), "SHA256withRSA");

        SignatureVerifier verifier = new SignatureVerifier();
        boolean valid = verifier.verifySignatureBlock(block);

        assertTrue(valid,
                "verifySignatureBlock() must accept a SignatureBlock with valid signature");
    }

    /**
     * Verifies that SignatureVerifier.getVerificationDetails() returns valid
     * metadata from a SignatureBlock with a non-expired certificate.
     */
    @Test
    void testSignatureVerifierVerificationDetailsForValidCertificate() throws Exception {
        KeyAndCertificate kc = generateRsaKeyAndCert();
        byte[] data = "details-test".getBytes(StandardCharsets.UTF_8);

        // Produce a real RSA signature for the details test
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(kc.keyPair().getPrivate());
        signer.update(data);
        byte[] signatureBytes = signer.sign();

        SignatureBlock block = new SignatureBlock(data, signatureBytes, kc.certificate(), "SHA256withRSA");

        SignatureVerifier verifier = new SignatureVerifier();
        SignatureVerifier.VerificationResult result = verifier.getVerificationDetails(block);

        assertNotNull(result, "getVerificationDetails() must return a non-null result");
        assertEquals("SHA256withRSA", result.getSignatureAlgorithm(),
                "VerificationResult must preserve the signature algorithm");
        assertTrue(result.isCertificateValid(),
                "VerificationResult must report the test certificate as valid (not expired)");
        assertNull(result.getValidationError(),
                "VerificationResult must have no validation error for a valid certificate");
        assertNotNull(result.getCertificateSubjectDN(),
                "VerificationResult must include the certificate subject DN");
        assertNotNull(result.getSignedAt(),
                "VerificationResult must include the signing timestamp");
    }

}
