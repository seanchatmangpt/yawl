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

package org.yawlfoundation.yawl.security.pki;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.X509Certificate;

/**
 * Signs documents using RSA and ECDSA algorithms with X.509 certificates.
 * Supports signing arbitrary byte content.
 */
public class DocumentSigner {
    private final CertificateManager certificateManager;
    private final String signatureAlgorithm;

    /**
     * Creates a DocumentSigner using the specified certificate manager.
     *
     * @param certificateManager The certificate manager to use for key retrieval
     * @param signatureAlgorithm The algorithm to use (e.g., "SHA256withRSA", "SHA256withECDSA")
     */
    public DocumentSigner(CertificateManager certificateManager, String signatureAlgorithm) {
        this.certificateManager = certificateManager;
        this.signatureAlgorithm = signatureAlgorithm;
    }

    /**
     * Signs a document (arbitrary bytes).
     *
     * @param data   The data to sign
     * @param alias  The alias of the signing certificate/key
     * @return The signature bytes
     * @throws SignatureException if signing fails
     */
    public byte[] signDocument(byte[] data, String alias) throws SignatureException {
        try {
            X509Certificate certificate = certificateManager.getCertificate(alias);
            certificateManager.validateCertificate(certificate);

            PrivateKey privateKey = certificateManager.getPrivateKey(alias);
            if (privateKey == null) {
                throw new SignatureException("Private key not found for alias: " + alias);
            }

            Signature signature = Signature.getInstance(signatureAlgorithm);
            signature.initSign(privateKey);
            signature.update(data);

            return signature.sign();
        } catch (SignatureException e) {
            throw e;
        } catch (PkiException e) {
            throw new SignatureException("Certificate error during signing: " + e.getMessage(), e);
        } catch (java.security.SignatureException | java.security.NoSuchAlgorithmException |
                 java.security.InvalidKeyException e) {
            throw new SignatureException("Failed to sign document: " + e.getMessage(), e);
        }
    }

    /**
     * Signs a text document (UTF-8 encoded string).
     *
     * @param text   The text to sign
     * @param alias  The alias of the signing certificate/key
     * @return The signature bytes
     * @throws SignatureException if signing fails
     */
    public byte[] signText(String text, String alias) throws SignatureException {
        return signDocument(text.getBytes(StandardCharsets.UTF_8), alias);
    }

    /**
     * Signs an XML document (as UTF-8 encoded string).
     *
     * @param xmlContent The XML content to sign
     * @param alias      The alias of the signing certificate/key
     * @return The signature bytes
     * @throws SignatureException if signing fails
     */
    public byte[] signXML(String xmlContent, String alias) throws SignatureException {
        return signText(xmlContent, alias);
    }

    /**
     * Creates a signature block containing certificate and signature data.
     *
     * @param data  The data that was signed
     * @param sig   The signature bytes
     * @param alias The alias of the signing certificate
     * @return A SignatureBlock containing metadata and signature
     * @throws SignatureException if the signature block cannot be created
     */
    public SignatureBlock createSignatureBlock(byte[] data, byte[] sig, String alias)
            throws SignatureException {
        try {
            X509Certificate certificate = certificateManager.getCertificate(alias);
            return new SignatureBlock(data, sig, certificate, signatureAlgorithm);
        } catch (PkiException e) {
            throw new SignatureException("Certificate not found for alias: " + alias, e);
        }
    }

    /**
     * Encodes a signature to Base64 for transport/storage.
     *
     * @param signature The signature bytes
     * @return Base64-encoded signature string
     */
    public String encodeSignatureToBase64(byte[] signature) {
        return java.util.Base64.getEncoder().encodeToString(signature);
    }

    /**
     * Decodes a Base64-encoded signature back to bytes.
     *
     * @param encodedSignature The Base64-encoded signature
     * @return The signature bytes
     */
    public byte[] decodeSignatureFromBase64(String encodedSignature) {
        return java.util.Base64.getDecoder().decode(encodedSignature);
    }
}
