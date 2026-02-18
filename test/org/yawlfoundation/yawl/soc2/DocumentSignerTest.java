/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and organisations who
 * are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License along
 * with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.soc2;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import org.yawlfoundation.yawl.security.pki.CertificateManager;
import org.yawlfoundation.yawl.security.pki.DocumentSigner;
import org.yawlfoundation.yawl.security.pki.SignatureBlock;
import org.yawlfoundation.yawl.security.pki.SignatureException;
import org.yawlfoundation.yawl.security.pki.SignatureVerifier;
import sun.security.tools.keytool.CertAndKeyGen;
import sun.security.x509.X500Name;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;

/**
 * SOC2 CC4.1 - Document Signing and PKI Integrity Tests.
 *
 * <p>Tests map to SOC 2 Trust Service Criteria:
 * CC4.1 - The entity uses detection and monitoring procedures to identify
 *         changes to configurations or content that affect the entity's
 *         ability to meet its objectives. Document signing provides an
 *         integrity control for workflow specifications and audit evidence.
 *
 * <p>Also maps to:
 * CC6.1 - Non-repudiation: signed workflow documents prove provenance.
 * CC6.6 - Tamper evidence: invalid signatures on modified documents are detected.
 *
 * <p>Covers:
 * <ul>
 *   <li>RSA (SHA256withRSA) signing produces a non-empty signature</li>
 *   <li>ECDSA (SHA256withECDSA) signing produces a non-empty signature</li>
 *   <li>Signature verification succeeds for valid RSA signature</li>
 *   <li>Signature verification succeeds for valid ECDSA signature</li>
 *   <li>Signature verification fails for tampered document (bit flip)</li>
 *   <li>Signature verification fails for tampered signature bytes</li>
 *   <li>signText() produces same result as signDocument() for UTF-8 bytes</li>
 *   <li>signXML() wraps signText() correctly</li>
 *   <li>createSignatureBlock() populates all metadata fields</li>
 *   <li>SignatureBlock.getSignatureAsBase64() is decodable</li>
 *   <li>verifySignatureBlock() works end-to-end for RSA</li>
 *   <li>verifySignatureBlock() works end-to-end for ECDSA</li>
 *   <li>getVerificationDetails() reports expired cert correctly</li>
 *   <li>encodeSignatureToBase64 / decodeSignatureFromBase64 round-trip</li>
 *   <li>Key rotation: new key pair signs; old public key cannot verify</li>
 *   <li>Missing alias throws SignatureException (not NPE)</li>
 *   <li>verifySignatureWithChain() validates against trusted root</li>
 *   <li>verifySignatureWithChain() rejects untrusted root</li>
 * </ul>
 *
 * <p>Chicago TDD: all tests use real PKCS12 keystores written to temporary files,
 * real cryptographic operations, and real CertificateManager / DocumentSigner /
 * SignatureVerifier instances. No mocks, no network, no external PKI.
 *
 * @author YAWL Foundation - SOC2 Compliance 2026-02-17
 */
@SuppressWarnings("deprecation")
public class DocumentSignerTest extends TestCase {

    private static final String KS_PASSWORD = "test-keystore-password-2026";
    private static final String RSA_ALIAS  = "rsa-signing-key";
    private static final String ECDSA_ALIAS = "ec-signing-key";

    /** Temporary PKCS12 keystore file containing RSA and ECDSA key pairs. */
    private File keystoreFile;

    /** RSA certificate stored in the test keystore. */
    private X509Certificate rsaCert;

    /** ECDSA certificate stored in the test keystore. */
    private X509Certificate ecCert;

    public DocumentSignerTest(String name) {
        super(name);
    }

    // =========================================================================
    // Test fixture setup
    // =========================================================================

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Generate RSA key pair and self-signed certificate
        CertAndKeyGen rsaGen = new CertAndKeyGen("RSA", "SHA256withRSA");
        rsaGen.generate(2048);
        rsaCert = rsaGen.getSelfCertificate(
                new X500Name("CN=YAWL Document Signer RSA,O=YAWL Test"),
                new Date(), 365L * 24 * 3600);

        // Generate ECDSA key pair and self-signed certificate
        CertAndKeyGen ecGen = new CertAndKeyGen("EC", "SHA256withECDSA");
        ecGen.generate("secp256r1");
        ecCert = ecGen.getSelfCertificate(
                new X500Name("CN=YAWL Document Signer EC,O=YAWL Test"),
                new Date(), 365L * 24 * 3600);

        // Build a PKCS12 keystore with both key pairs
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, KS_PASSWORD.toCharArray());
        ks.setKeyEntry(RSA_ALIAS, rsaGen.getPrivateKey(), KS_PASSWORD.toCharArray(),
                new Certificate[]{ rsaCert });
        ks.setKeyEntry(ECDSA_ALIAS, ecGen.getPrivateKey(), KS_PASSWORD.toCharArray(),
                new Certificate[]{ ecCert });

        // Write to a temp file (CertificateManager reads from a file path)
        keystoreFile = File.createTempFile("yawl-test-ks-", ".p12");
        keystoreFile.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(keystoreFile)) {
            ks.store(fos, KS_PASSWORD.toCharArray());
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (keystoreFile != null && keystoreFile.exists()) {
            keystoreFile.delete();
        }
        super.tearDown();
    }

    // =========================================================================
    // Helper: create a real CertificateManager backed by the test keystore
    // =========================================================================

    private CertificateManager newCertManager() throws Exception {
        return new CertificateManager(keystoreFile.getAbsolutePath(), KS_PASSWORD, "PKCS12");
    }

    private DocumentSigner newRsaSigner() throws Exception {
        return new DocumentSigner(newCertManager(), "SHA256withRSA");
    }

    private DocumentSigner newEcSigner() throws Exception {
        return new DocumentSigner(newCertManager(), "SHA256withECDSA");
    }

    private SignatureVerifier newVerifier() {
        return new SignatureVerifier();
    }

    // =========================================================================
    // CC4.1 - RSA signing
    // =========================================================================

    /**
     * SOC2 CC4.1: RSA (SHA256withRSA) signing must produce a non-empty signature.
     * Non-empty proves the JCA computed a real cryptographic signature.
     */
    public void testRsaSignDocumentProducesNonEmptySignature() throws Exception {
        DocumentSigner signer = newRsaSigner();
        byte[] data = "YAWL workflow specification v1".getBytes(StandardCharsets.UTF_8);

        byte[] signature = signer.signDocument(data, RSA_ALIAS);

        assertNotNull("RSA signature must not be null", signature);
        assertTrue("RSA signature must be non-empty", signature.length > 0);
    }

    /**
     * SOC2 CC4.1: RSA signature must be verifiable with the corresponding public key.
     * Verifies the complete sign-verify round-trip for document integrity control.
     */
    public void testRsaSignatureVerifiesWithCorrectPublicKey() throws Exception {
        DocumentSigner signer = newRsaSigner();
        SignatureVerifier verifier = newVerifier();
        byte[] data = "YAWL workflow: launch-order-process v2.1".getBytes(StandardCharsets.UTF_8);

        byte[] signature = signer.signDocument(data, RSA_ALIAS);

        boolean valid = verifier.verifySignature(data, signature, rsaCert, "SHA256withRSA");
        assertTrue("RSA signature must verify against the signing certificate", valid);
    }

    // =========================================================================
    // CC4.1 - ECDSA signing
    // =========================================================================

    /**
     * SOC2 CC4.1: ECDSA (SHA256withECDSA) signing must produce a non-empty signature.
     */
    public void testEcdsaSignDocumentProducesNonEmptySignature() throws Exception {
        DocumentSigner signer = newEcSigner();
        byte[] data = "YAWL workflow specification v1".getBytes(StandardCharsets.UTF_8);

        byte[] signature = signer.signDocument(data, ECDSA_ALIAS);

        assertNotNull("ECDSA signature must not be null", signature);
        assertTrue("ECDSA signature must be non-empty", signature.length > 0);
    }

    /**
     * SOC2 CC4.1: ECDSA signature must verify with the corresponding EC public key.
     */
    public void testEcdsaSignatureVerifiesWithCorrectPublicKey() throws Exception {
        DocumentSigner signer = newEcSigner();
        SignatureVerifier verifier = newVerifier();
        byte[] data = "YAWL EC-signed document".getBytes(StandardCharsets.UTF_8);

        byte[] signature = signer.signDocument(data, ECDSA_ALIAS);

        boolean valid = verifier.verifySignature(data, signature, ecCert, "SHA256withECDSA");
        assertTrue("ECDSA signature must verify against the signing certificate", valid);
    }

    // =========================================================================
    // CC4.1 - Tamper detection
    // =========================================================================

    /**
     * SOC2 CC4.1: Tampered document content must fail signature verification.
     * Single-bit change in document body invalidates the digital signature.
     */
    public void testTamperedDocumentFailsVerification() throws Exception {
        DocumentSigner signer = newRsaSigner();
        SignatureVerifier verifier = newVerifier();
        byte[] original = "Original YAWL specification content".getBytes(StandardCharsets.UTF_8);

        byte[] signature = signer.signDocument(original, RSA_ALIAS);

        // Tamper: flip the first byte
        byte[] tampered = Arrays.copyOf(original, original.length);
        tampered[0] = (byte) ~tampered[0];

        boolean valid = verifier.verifySignature(tampered, signature, rsaCert, "SHA256withRSA");
        assertFalse("Tampered document must NOT verify successfully", valid);
    }

    /**
     * SOC2 CC4.1: Tampered signature bytes must fail verification.
     * Attackers cannot forge a valid signature for a legitimate document.
     */
    public void testTamperedSignatureFailsVerification() throws Exception {
        DocumentSigner signer = newRsaSigner();
        SignatureVerifier verifier = newVerifier();
        byte[] data = "Legitimate YAWL document content".getBytes(StandardCharsets.UTF_8);

        byte[] signature = signer.signDocument(data, RSA_ALIAS);

        // Tamper: corrupt the signature
        byte[] tamperedSig = Arrays.copyOf(signature, signature.length);
        tamperedSig[signature.length / 2] = (byte) ~tamperedSig[signature.length / 2];

        boolean valid = verifier.verifySignature(data, tamperedSig, rsaCert, "SHA256withRSA");
        assertFalse("Tampered signature must NOT verify successfully", valid);
    }

    /**
     * SOC2 CC4.1: A signature produced for different data must fail verification
     * against the original data. Demonstrates that signatures are data-bound.
     */
    public void testSignatureForDifferentDataFailsVerification() throws Exception {
        DocumentSigner signer = newRsaSigner();
        SignatureVerifier verifier = newVerifier();
        byte[] originalData = "YAWL document original".getBytes(StandardCharsets.UTF_8);
        byte[] differentData = "YAWL document altered".getBytes(StandardCharsets.UTF_8);

        // Sign the different data - real cryptographic signature, just for the wrong content
        byte[] signatureForDifferentData = signer.signDocument(differentData, RSA_ALIAS);

        // Verify that signature (of differentData) against originalData - must fail
        boolean valid = verifier.verifySignature(originalData, signatureForDifferentData,
                rsaCert, "SHA256withRSA");
        assertFalse("Signature produced for different data must not verify against original", valid);
    }

    // =========================================================================
    // CC4.1 - signText() and signXML()
    // =========================================================================

    /**
     * SOC2 CC4.1: signText() must produce identical output to signDocument() for UTF-8 bytes.
     * This property is required because signText() is the bridge for YAWL specification signing.
     */
    public void testSignTextProducesVerifiableSignature() throws Exception {
        DocumentSigner signer = newRsaSigner();
        SignatureVerifier verifier = newVerifier();
        String text = "YAWL workflow text document for signing";

        byte[] signature = signer.signText(text, RSA_ALIAS);

        boolean valid = verifier.verifySignature(
                text.getBytes(StandardCharsets.UTF_8), signature, rsaCert, "SHA256withRSA");
        assertTrue("signText() signature must verify against UTF-8 bytes of text", valid);
    }

    /**
     * SOC2 CC4.1: signXML() must produce a verifiable signature for XML content.
     * XML workflow specifications must be signable for integrity control.
     */
    public void testSignXmlProducesVerifiableSignature() throws Exception {
        DocumentSigner signer = newRsaSigner();
        SignatureVerifier verifier = newVerifier();
        String xml = "<yawlSpecificationSet xmlns=\"http://www.yawl-system.com/schema/YAWL_Schema4.0\">"
                   + "<specification id=\"test\"><name>Test Workflow</name></specification>"
                   + "</yawlSpecificationSet>";

        byte[] signature = signer.signXML(xml, RSA_ALIAS);

        boolean valid = verifier.verifySignature(
                xml.getBytes(StandardCharsets.UTF_8), signature, rsaCert, "SHA256withRSA");
        assertTrue("signXML() signature must verify against UTF-8 bytes of XML", valid);
    }

    // =========================================================================
    // CC4.1 - SignatureBlock
    // =========================================================================

    /**
     * SOC2 CC4.1: createSignatureBlock() must populate all metadata fields.
     * SignatureBlock is used in audit trails and non-repudiation records.
     */
    public void testCreateSignatureBlockPopulatesAllFields() throws Exception {
        DocumentSigner signer = newRsaSigner();
        byte[] data = "Signed workflow document".getBytes(StandardCharsets.UTF_8);
        byte[] signature = signer.signDocument(data, RSA_ALIAS);

        SignatureBlock block = signer.createSignatureBlock(data, signature, RSA_ALIAS);

        assertNotNull("SignatureBlock must not be null", block);
        assertNotNull("Original data must be set", block.getOriginalData());
        assertNotNull("Signature bytes must be set", block.getSignatureBytes());
        assertNotNull("Signing certificate must be set", block.getSigningCertificate());
        assertNotNull("Signature algorithm must be set", block.getSignatureAlgorithm());
        assertEquals("Signature algorithm must match signer", "SHA256withRSA",
                block.getSignatureAlgorithm());
        assertNotNull("SignedAt must be set", block.getSignedAt());
        assertNotNull("Certificate subject DN must be set", block.getCertificateSubjectDN());
        assertNotNull("Certificate issuer DN must be set", block.getCertificateIssuerDN());
        assertNotNull("Certificate serial number must be set", block.getCertificateSerialNumber());
    }

    /**
     * SOC2 CC4.1: SignatureBlock.getSignatureAsBase64() must produce a decodable Base64 string.
     */
    public void testSignatureBlockBase64IsDecodable() throws Exception {
        DocumentSigner signer = newRsaSigner();
        byte[] data = "Document for base64 test".getBytes(StandardCharsets.UTF_8);
        byte[] signature = signer.signDocument(data, RSA_ALIAS);
        SignatureBlock block = signer.createSignatureBlock(data, signature, RSA_ALIAS);

        String b64 = block.getSignatureAsBase64();

        assertNotNull("Base64 signature must not be null", b64);
        assertFalse("Base64 signature must not be empty", b64.isEmpty());

        byte[] decoded = Base64.getDecoder().decode(b64);
        assertArrayEquals("Decoded Base64 must match original signature bytes",
                signature, decoded);
    }

    /**
     * SOC2 CC4.1: verifySignatureBlock() must verify an RSA signature block end-to-end.
     */
    public void testVerifySignatureBlockSucceedsForRsa() throws Exception {
        DocumentSigner signer = newRsaSigner();
        SignatureVerifier verifier = newVerifier();
        byte[] data = "YAWL RSA signature block test".getBytes(StandardCharsets.UTF_8);
        byte[] signature = signer.signDocument(data, RSA_ALIAS);
        SignatureBlock block = signer.createSignatureBlock(data, signature, RSA_ALIAS);

        boolean valid = verifier.verifySignatureBlock(block);

        assertTrue("verifySignatureBlock() must return true for valid RSA block", valid);
    }

    /**
     * SOC2 CC4.1: verifySignatureBlock() must verify an ECDSA signature block end-to-end.
     */
    public void testVerifySignatureBlockSucceedsForEcdsa() throws Exception {
        DocumentSigner signer = newEcSigner();
        SignatureVerifier verifier = newVerifier();
        byte[] data = "YAWL ECDSA signature block test".getBytes(StandardCharsets.UTF_8);
        byte[] signature = signer.signDocument(data, ECDSA_ALIAS);
        SignatureBlock block = signer.createSignatureBlock(data, signature, ECDSA_ALIAS);

        boolean valid = verifier.verifySignatureBlock(block);

        assertTrue("verifySignatureBlock() must return true for valid ECDSA block", valid);
    }

    // =========================================================================
    // CC4.1 - Base64 encoding round-trip
    // =========================================================================

    /**
     * SOC2 CC4.1: encodeSignatureToBase64 / decodeSignatureFromBase64 must round-trip correctly.
     * Signatures stored in YAWL's audit log are transported as Base64.
     */
    public void testBase64EncodingRoundTrip() throws Exception {
        DocumentSigner signer = newRsaSigner();
        byte[] data = "Round-trip test document".getBytes(StandardCharsets.UTF_8);
        byte[] original = signer.signDocument(data, RSA_ALIAS);

        String encoded = signer.encodeSignatureToBase64(original);
        byte[] decoded = signer.decodeSignatureFromBase64(encoded);

        assertArrayEquals("Base64 decode(encode(sig)) must equal original signature", original, decoded);
    }

    // =========================================================================
    // CC4.1 - Key rotation
    // =========================================================================

    /**
     * SOC2 CC4.1: Key rotation scenario - document signed with old key must NOT
     * verify against new key. This validates that key rotation invalidates old signatures,
     * which is required for forward-secrecy in audit evidence.
     */
    public void testKeyRotationOldSignatureDoesNotVerifyWithNewKey() throws Exception {
        // Sign with the existing RSA key
        DocumentSigner signerOldKey = newRsaSigner();
        byte[] data = "YAWL specification that will be re-signed after key rotation".getBytes(StandardCharsets.UTF_8);
        byte[] oldSignature = signerOldKey.signDocument(data, RSA_ALIAS);

        // Simulate key rotation: generate a fresh RSA key pair
        CertAndKeyGen newGen = new CertAndKeyGen("RSA", "SHA256withRSA");
        newGen.generate(2048);
        X509Certificate newCert = newGen.getSelfCertificate(
                new X500Name("CN=YAWL New Signing Key,O=YAWL Test"),
                new Date(), 365L * 24 * 3600);

        // Verify the old signature against the NEW certificate (must fail)
        SignatureVerifier verifier = newVerifier();
        boolean crossVerifies = verifier.verifySignature(data, oldSignature, newCert, "SHA256withRSA");

        assertFalse("Signature created with old key must NOT verify against new key after rotation",
                crossVerifies);
    }

    /**
     * SOC2 CC4.1: Key rotation scenario - new key signs correctly, old cert cannot verify.
     * Documents signed after rotation must use the new key exclusively.
     */
    public void testKeyRotationNewKeySignsAndVerifiesCorrectly() throws Exception {
        // Rotate: create a new keystore with only the new key
        CertAndKeyGen newGen = new CertAndKeyGen("RSA", "SHA256withRSA");
        newGen.generate(2048);
        X509Certificate newCert = newGen.getSelfCertificate(
                new X500Name("CN=YAWL Rotated Key,O=YAWL Test"),
                new Date(), 365L * 24 * 3600);

        KeyStore rotatedKs = KeyStore.getInstance("PKCS12");
        rotatedKs.load(null, KS_PASSWORD.toCharArray());
        rotatedKs.setKeyEntry("rotated-key", newGen.getPrivateKey(), KS_PASSWORD.toCharArray(),
                new Certificate[]{ newCert });

        File rotatedKsFile = File.createTempFile("yawl-rotated-ks-", ".p12");
        rotatedKsFile.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(rotatedKsFile)) {
            rotatedKs.store(fos, KS_PASSWORD.toCharArray());
        }

        CertificateManager rotatedMgr = new CertificateManager(
                rotatedKsFile.getAbsolutePath(), KS_PASSWORD, "PKCS12");
        DocumentSigner newSigner = new DocumentSigner(rotatedMgr, "SHA256withRSA");
        SignatureVerifier verifier = newVerifier();

        byte[] data = "Document signed after key rotation".getBytes(StandardCharsets.UTF_8);
        byte[] newSignature = newSigner.signDocument(data, "rotated-key");

        // New key verifies against new cert
        boolean validWithNew = verifier.verifySignature(data, newSignature, newCert, "SHA256withRSA");
        assertTrue("New key signature must verify against new certificate", validWithNew);

        // New signature must NOT verify against old cert
        boolean validWithOld = verifier.verifySignature(data, newSignature, rsaCert, "SHA256withRSA");
        assertFalse("New key signature must NOT verify against old (rotated-out) certificate",
                validWithOld);

        rotatedKsFile.delete();
    }

    // =========================================================================
    // CC4.1 - Error handling
    // =========================================================================

    /**
     * SOC2 CC4.1: Signing with a non-existent alias must throw SignatureException.
     * Clear error prevents silent failure to sign important documents.
     */
    public void testSignDocumentWithMissingAliasThrowsSignatureException() throws Exception {
        DocumentSigner signer = newRsaSigner();
        byte[] data = "test".getBytes(StandardCharsets.UTF_8);

        try {
            signer.signDocument(data, "non-existent-alias-xyz");
            fail("Expected SignatureException for non-existent alias");
        } catch (SignatureException e) {
            assertNotNull("Exception must have a message", e.getMessage());
        }
    }

    /**
     * SOC2 CC4.1: getVerificationDetails() must report expired certificate status.
     * Used to detect signing with expired certificates in audit review.
     *
     * <p>The expired key pair is generated here; the private key is used directly
     * via JCA to produce a real cryptographic signature. The certificate's
     * NotAfter is set in the past so that getVerificationDetails() exercises the
     * CertificateExpiredException branch and reports the cert as invalid.
     */
    public void testGetVerificationDetailsReportsExpiredCertificate() throws Exception {
        // Generate key pair for the expired signer
        CertAndKeyGen expiredGen = new CertAndKeyGen("RSA", "SHA256withRSA");
        expiredGen.generate(2048);
        // NotBefore = 2 seconds ago, validity = 1 second -> NotAfter = 1 second ago
        Date notBefore = new Date(System.currentTimeMillis() - 2000);
        X509Certificate expiredCert = expiredGen.getSelfCertificate(
                new X500Name("CN=Expired Signer,O=YAWL Test"),
                notBefore, 1L);

        assertTrue("Test setup: certificate must already be expired",
                expiredCert.getNotAfter().before(new Date()));

        // Produce a real cryptographic signature using the expired key's private key.
        // We bypass CertificateManager (which calls checkValidity) and call JCA directly
        // because the cert is already expired - the key material is still cryptographically
        // valid even though the cert is past its validity period.
        byte[] data = "Data signed with expired cert".getBytes(StandardCharsets.UTF_8);
        java.security.Signature jcaSig = java.security.Signature.getInstance("SHA256withRSA");
        jcaSig.initSign(expiredGen.getPrivateKey());
        jcaSig.update(data);
        byte[] realSignature = jcaSig.sign();

        SignatureBlock block = new SignatureBlock(data, realSignature, expiredCert, "SHA256withRSA");

        SignatureVerifier verifier = newVerifier();
        SignatureVerifier.VerificationResult result = verifier.getVerificationDetails(block);

        assertFalse("getVerificationDetails() must report cert as NOT valid when expired",
                result.isCertificateValid());
        assertNotNull("Validation error must be set for expired cert", result.getValidationError());
        assertTrue("Validation error must mention expir",
                result.getValidationError().toLowerCase().contains("expir"));
    }

    /**
     * SOC2 CC4.1: getVerificationDetails() must report valid certificate status.
     */
    public void testGetVerificationDetailsReportsValidCertificate() throws Exception {
        DocumentSigner signer = newRsaSigner();
        SignatureVerifier verifier = newVerifier();
        byte[] data = "Valid cert document".getBytes(StandardCharsets.UTF_8);
        byte[] signature = signer.signDocument(data, RSA_ALIAS);
        SignatureBlock block = signer.createSignatureBlock(data, signature, RSA_ALIAS);

        SignatureVerifier.VerificationResult result = verifier.getVerificationDetails(block);

        assertTrue("getVerificationDetails() must report cert as valid", result.isCertificateValid());
        assertEquals("Signature algorithm must be reported", "SHA256withRSA",
                result.getSignatureAlgorithm());
        assertNotNull("SignedAt must be reported", result.getSignedAt());
        assertNotNull("Subject DN must be reported", result.getCertificateSubjectDN());
    }

    // =========================================================================
    // CC4.1 - Chain validation
    // =========================================================================

    /**
     * SOC2 CC4.1: verifySignatureWithChain() must accept signature when cert signed by trusted root.
     * Self-signed certificate is its own root; verifying against itself must pass.
     */
    public void testVerifySignatureWithChainAcceptsTrustedRoot() throws Exception {
        DocumentSigner signer = newRsaSigner();
        SignatureVerifier verifier = newVerifier();
        byte[] data = "Document for chain verification".getBytes(StandardCharsets.UTF_8);
        byte[] signature = signer.signDocument(data, RSA_ALIAS);

        // Self-signed cert: passes when listed as its own trusted root
        boolean valid = verifier.verifySignatureWithChain(
                data, signature, rsaCert, "SHA256withRSA", new X509Certificate[]{ rsaCert });

        assertTrue("Signature must verify when cert is its own trusted root", valid);
    }

    /**
     * SOC2 CC4.1: verifySignatureWithChain() must reject signature with untrusted root.
     * Prevents accepting documents signed by unauthorized certificate authorities.
     */
    public void testVerifySignatureWithChainRejectsUntrustedRoot() throws Exception {
        // Generate a separate "untrusted" root CA
        CertAndKeyGen untrustedGen = new CertAndKeyGen("RSA", "SHA256withRSA");
        untrustedGen.generate(2048);
        X509Certificate untrustedRoot = untrustedGen.getSelfCertificate(
                new X500Name("CN=Untrusted Root CA,O=YAWL Test"),
                new Date(), 365L * 24 * 3600);

        DocumentSigner signer = newRsaSigner();
        SignatureVerifier verifier = newVerifier();
        byte[] data = "Document signed by our cert".getBytes(StandardCharsets.UTF_8);
        byte[] signature = signer.signDocument(data, RSA_ALIAS);

        // Verify against a DIFFERENT root (not the one that signed rsaCert)
        boolean valid = verifier.verifySignatureWithChain(
                data, signature, rsaCert, "SHA256withRSA", new X509Certificate[]{ untrustedRoot });

        assertFalse("Signature must NOT verify when root CA is not trusted", valid);
    }

    // =========================================================================
    // Utility assertion
    // =========================================================================

    private static void assertArrayEquals(String message, byte[] expected, byte[] actual) {
        assertNotNull(message + " (expected)", expected);
        assertNotNull(message + " (actual)", actual);
        assertTrue(message + " (length: expected=" + expected.length + " actual=" + actual.length + ")",
                Arrays.equals(expected, actual));
    }

    // =========================================================================
    // Test suite
    // =========================================================================

    public static Test suite() {
        return new TestSuite(DocumentSignerTest.class);
    }

    public static void main(String[] args) {
        TestRunner.run(suite());
    }
}
