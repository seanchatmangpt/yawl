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

/**
 * <h2>Public Key Infrastructure (PKI) Module</h2>
 *
 * <p>Provides digital signature and certificate management capabilities for YAWL workflows.
 * Enterprise-grade PKI implementation supporting X.509 certificates, RSA/ECDSA signatures,
 * and certificate chain validation.</p>
 *
 * <h3>Core Components</h3>
 * <ul>
 *   <li><strong>CertificateManager</strong> - Manages X.509 certificates, keystores (JKS/PKCS12),
 *       and certificate validation. Loads certificates and private keys from secure keystores.</li>
 *   <li><strong>DocumentSigner</strong> - Signs documents using RSA/ECDSA algorithms with X.509
 *       certificates. Supports arbitrary bytes, text, and XML content.</li>
 *   <li><strong>SignatureVerifier</strong> - Verifies signatures against X.509 certificates and
 *       validates certificate chains against trusted roots.</li>
 *   <li><strong>SignatureBlock</strong> - Container for signature data and certificate metadata,
 *       including subject DN, issuer DN, and timestamp.</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre>
 * // Initialize certificate manager
 * CertificateManager certMgr = new CertificateManager(
 *     "/path/to/keystore.jks",
 *     "keystorePassword",
 *     "JKS"
 * );
 *
 * // Create signer
 * DocumentSigner signer = new DocumentSigner(certMgr, "SHA256withRSA");
 *
 * // Sign a document
 * byte[] document = "Important workflow data".getBytes();
 * byte[] signature = signer.signDocument(document, "signing_cert_alias");
 *
 * // Verify signature
 * SignatureVerifier verifier = new SignatureVerifier();
 * X509Certificate cert = certMgr.getCertificate("signing_cert_alias");
 * boolean isValid = verifier.verifySignature(
 *     document,
 *     signature,
 *     cert,
 *     "SHA256withRSA"
 * );
 * </pre>
 *
 * <h3>Security Considerations</h3>
 * <ul>
 *   <li>Keystores should be protected with strong passwords.</li>
 *   <li>Private keys must never be exposed or transmitted.</li>
 *   <li>Certificates should be regularly validated for expiration.</li>
 *   <li>Use certificate pinning for high-security applications.</li>
 *   <li>Signatures provide non-repudiation but not confidentiality.</li>
 * </ul>
 *
 * <h3>Supported Algorithms</h3>
 * <ul>
 *   <li>RSA: SHA1withRSA, SHA256withRSA, SHA384withRSA, SHA512withRSA</li>
 *   <li>ECDSA: SHA256withECDSA, SHA384withECDSA, SHA512withECDSA</li>
 * </ul>
 *
 * @since YAWL 5.2
 */
package org.yawlfoundation.yawl.security.pki;
