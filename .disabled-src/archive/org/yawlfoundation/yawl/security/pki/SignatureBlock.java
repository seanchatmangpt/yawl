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

import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;

/**
 * Contains a signature and associated metadata including the signing certificate,
 * signature algorithm, and timestamp.
 */
public class SignatureBlock {
    private final byte[] originalData;
    private final byte[] signatureBytes;
    private final X509Certificate signingCertificate;
    private final String signatureAlgorithm;
    private final Date signedAt;

    /**
     * Creates a SignatureBlock.
     *
     * @param originalData        The original data that was signed
     * @param signatureBytes      The signature bytes
     * @param signingCertificate  The certificate used to sign
     * @param signatureAlgorithm  The algorithm used for signing
     */
    public SignatureBlock(byte[] originalData, byte[] signatureBytes,
                         X509Certificate signingCertificate, String signatureAlgorithm) {
        this.originalData = originalData;
        this.signatureBytes = signatureBytes;
        this.signingCertificate = signingCertificate;
        this.signatureAlgorithm = signatureAlgorithm;
        this.signedAt = new Date();
    }

    /**
     * Gets the original data that was signed.
     *
     * @return The original data bytes
     */
    public byte[] getOriginalData() {
        return originalData;
    }

    /**
     * Gets the signature bytes.
     *
     * @return The signature bytes
     */
    public byte[] getSignatureBytes() {
        return signatureBytes;
    }

    /**
     * Gets the signature as a Base64-encoded string.
     *
     * @return Base64-encoded signature
     */
    public String getSignatureAsBase64() {
        return Base64.getEncoder().encodeToString(signatureBytes);
    }

    /**
     * Gets the signing certificate.
     *
     * @return The X.509 certificate used for signing
     */
    public X509Certificate getSigningCertificate() {
        return signingCertificate;
    }

    /**
     * Gets the signature algorithm used.
     *
     * @return The signature algorithm (e.g., "SHA256withRSA")
     */
    public String getSignatureAlgorithm() {
        return signatureAlgorithm;
    }

    /**
     * Gets the timestamp of when the signature was created.
     *
     * @return The signing timestamp
     */
    public Date getSignedAt() {
        return signedAt;
    }

    /**
     * Gets the certificate subject distinguished name.
     *
     * @return The certificate subject DN
     */
    public String getCertificateSubjectDN() {
        return signingCertificate.getSubjectDN().toString();
    }

    /**
     * Gets the certificate issuer distinguished name.
     *
     * @return The certificate issuer DN
     */
    public String getCertificateIssuerDN() {
        return signingCertificate.getIssuerDN().toString();
    }

    /**
     * Gets the certificate serial number.
     *
     * @return The certificate serial number
     */
    public String getCertificateSerialNumber() {
        return signingCertificate.getSerialNumber().toString();
    }
}
