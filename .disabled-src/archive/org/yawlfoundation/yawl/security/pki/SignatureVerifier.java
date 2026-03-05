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

import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.Date;

/**
 * Verifies digital signatures using X.509 certificates.
 * Supports RSA and ECDSA signature verification.
 */
public class SignatureVerifier {

    /**
     * Verifies a signature using a public key and certificate.
     *
     * @param data                The original data that was signed
     * @param signatureBytes      The signature bytes to verify
     * @param certificate         The certificate containing the public key
     * @param signatureAlgorithm  The algorithm used for signing
     * @return true if signature is valid, false otherwise
     * @throws SignatureException if verification fails
     */
    public boolean verifySignature(byte[] data, byte[] signatureBytes,
                                  X509Certificate certificate, String signatureAlgorithm)
            throws SignatureException {
        try {
            X509Certificate cert = validateCertificate(certificate);
            PublicKey publicKey = cert.getPublicKey();

            Signature signature = Signature.getInstance(signatureAlgorithm);
            signature.initVerify(publicKey);
            signature.update(data);

            return signature.verify(signatureBytes);
        } catch (java.security.SignatureException e) {
            return false;
        } catch (java.security.NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new SignatureException("Failed to verify signature: " + e.getMessage(), e);
        } catch (PkiException e) {
            throw new SignatureException("Certificate validation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Verifies a signature block.
     *
     * @param signatureBlock The signature block to verify
     * @return true if signature is valid, false otherwise
     * @throws SignatureException if verification fails
     */
    public boolean verifySignatureBlock(SignatureBlock signatureBlock) throws SignatureException {
        return verifySignature(
                signatureBlock.getOriginalData(),
                signatureBlock.getSignatureBytes(),
                signatureBlock.getSigningCertificate(),
                signatureBlock.getSignatureAlgorithm()
        );
    }

    /**
     * Verifies both the signature and the certificate chain validity.
     *
     * @param data                 The original data that was signed
     * @param signatureBytes       The signature bytes to verify
     * @param certificate          The certificate containing the public key
     * @param signatureAlgorithm   The algorithm used for signing
     * @param trustedCertificates  The trusted root certificates for chain validation
     * @return true if signature is valid and certificate chain is trusted, false otherwise
     * @throws SignatureException if verification fails
     */
    public boolean verifySignatureWithChain(byte[] data, byte[] signatureBytes,
                                           X509Certificate certificate,
                                           String signatureAlgorithm,
                                           X509Certificate[] trustedCertificates)
            throws SignatureException {
        if (!verifySignature(data, signatureBytes, certificate, signatureAlgorithm)) {
            return false;
        }

        return validateCertificateChain(certificate, trustedCertificates);
    }

    /**
     * Validates that a certificate is not expired.
     *
     * @param certificate The certificate to validate
     * @return The same certificate if valid
     * @throws CertificateException if the certificate is expired or invalid
     */
    private X509Certificate validateCertificate(X509Certificate certificate)
            throws PkiException {
        try {
            certificate.checkValidity();
            return certificate;
        } catch (java.security.cert.CertificateExpiredException e) {
            throw new PkiException("Certificate has expired", e);
        } catch (java.security.cert.CertificateNotYetValidException e) {
            throw new PkiException("Certificate is not yet valid", e);
        } catch (java.security.cert.CertificateException e) {
            throw new PkiException("Certificate validation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Validates a certificate chain against trusted root certificates.
     *
     * @param certificate       The certificate to validate
     * @param trustedRoots      The trusted root certificates
     * @return true if the chain is valid and trusted, false otherwise
     */
    private boolean validateCertificateChain(X509Certificate certificate,
                                            X509Certificate[] trustedRoots) {
        try {
            for (X509Certificate root : trustedRoots) {
                try {
                    certificate.verify(root.getPublicKey());
                    return true;
                } catch (Exception e) {
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Gets signature validity information.
     *
     * @param signatureBlock The signature block
     * @return A VerificationResult with detailed status information
     */
    public VerificationResult getVerificationDetails(SignatureBlock signatureBlock) {
        X509Certificate cert = signatureBlock.getSigningCertificate();
        VerificationResult result = new VerificationResult();

        result.setSignatureAlgorithm(signatureBlock.getSignatureAlgorithm());
        result.setSignedAt(signatureBlock.getSignedAt());
        result.setCertificateSubjectDN(signatureBlock.getCertificateSubjectDN());
        result.setCertificateIssuerDN(signatureBlock.getCertificateIssuerDN());
        result.setCertificateSerialNumber(signatureBlock.getCertificateSerialNumber());

        try {
            cert.checkValidity();
            result.setCertificateValid(true);
        } catch (java.security.cert.CertificateExpiredException e) {
            result.setCertificateValid(false);
            result.setValidationError("Certificate has expired: " + cert.getNotAfter());
        } catch (java.security.cert.CertificateNotYetValidException e) {
            result.setCertificateValid(false);
            result.setValidationError("Certificate is not yet valid: " + cert.getNotBefore());
        } catch (java.security.cert.CertificateException e) {
            result.setCertificateValid(false);
            result.setValidationError("Certificate validation error: " + e.getMessage());
        }

        return result;
    }

    /**
     * Result container for signature verification operations.
     */
    public static class VerificationResult {
        private String signatureAlgorithm;
        private Date signedAt;
        private String certificateSubjectDN;
        private String certificateIssuerDN;
        private String certificateSerialNumber;
        private boolean certificateValid;
        private String validationError;

        public String getSignatureAlgorithm() {
            return signatureAlgorithm;
        }

        public void setSignatureAlgorithm(String signatureAlgorithm) {
            this.signatureAlgorithm = signatureAlgorithm;
        }

        public Date getSignedAt() {
            return signedAt;
        }

        public void setSignedAt(Date signedAt) {
            this.signedAt = signedAt;
        }

        public String getCertificateSubjectDN() {
            return certificateSubjectDN;
        }

        public void setCertificateSubjectDN(String certificateSubjectDN) {
            this.certificateSubjectDN = certificateSubjectDN;
        }

        public String getCertificateIssuerDN() {
            return certificateIssuerDN;
        }

        public void setCertificateIssuerDN(String certificateIssuerDN) {
            this.certificateIssuerDN = certificateIssuerDN;
        }

        public String getCertificateSerialNumber() {
            return certificateSerialNumber;
        }

        public void setCertificateSerialNumber(String certificateSerialNumber) {
            this.certificateSerialNumber = certificateSerialNumber;
        }

        public boolean isCertificateValid() {
            return certificateValid;
        }

        public void setCertificateValid(boolean certificateValid) {
            this.certificateValid = certificateValid;
        }

        public String getValidationError() {
            return validationError;
        }

        public void setValidationError(String validationError) {
            this.validationError = validationError;
        }
    }
}
