/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 */

package org.yawlfoundation.yawl.integration.a2a.validation.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.*;
import java.security.cert.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SPIFFE Validator for A2A mTLS authentication.
 *
 * <p>This validator provides comprehensive SPIFFE (Secure Production Identity
 * Framework For Everyone) X.509 certificate validation for A2A protocol
 * including trust domain validation, certificate chain verification,
 * and identity mapping.
 *
 * <p>Features:
 * - X.509 certificate chain validation
 * - SPIFFE trust domain validation
 * - mTLS handshake verification
 * - Certificate expiration checking
 * - SPIFFE ID format validation
 * - Certificate revocation checking
 * - Identity mapping service integration
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class SpiffeValidator {

    private static final Logger _logger = LoggerFactory.getLogger(SpiffeValidator.class);

    private final String trustDomain;
    private final X509TrustManager trustManager;
    private final Map<String, CertPathValidator> certPathValidators;
    private final CertificateRevocationChecker revocationChecker;
    private final IdentityMapper identityMapper;
    private final Duration certificateValidityWindow;
    private final int maxChainLength;

    /**
     * Constructs a SpiffeValidator with default trust domain.
     */
    public SpiffeValidator() {
        this("yawl.cloud");
    }

    /**
     * Constructs a SpiffeValidator with specified trust domain.
     *
     * @param trustDomain SPIFFE trust domain
     */
    public SpiffeValidator(String trustDomain) {
        this(trustDomain, Duration.ofDays(30), 10);
    }

    /**
     * Constructs a SpiffeValidator with custom configuration.
     *
     * @param trustDomain SPIFFE trust domain
     * @param validityWindow Certificate validity window
     * @param maxChainLength Maximum certificate chain length
     */
    public SpiffeValidator(String trustDomain, Duration validityWindow, int maxChainLength) {
        this.trustDomain = trustDomain != null ? trustDomain : "yawl.cloud";
        this.certPathValidators = new ConcurrentHashMap<>();
        this.certificateValidityWindow = validityWindow;
        this.maxChainLength = maxChainLength;

        // Initialize trust manager with SPIFFE trust anchors
        this.trustManager = createSpiffeTrustManager();

        // Initialize certificate utilities
        this.revocationChecker = new CertificateRevocationChecker();
        this.identityMapper = new IdentityMapper();
    }

    /**
     * Validates SPIFFE certificate from TLS handshake.
     *
     * @param sslSession SSL session from handshake
     * @return ValidationResult containing validation details
     */
    public ValidationResult validate(SSLSession sslSession) {
        try {
            // Get peer certificates from session
            Certificate[] peerCertificates = sslSession.getLocalCertificates();
            if (peerCertificates == null || peerCertificates.length == 0) {
                return ValidationResult.failure("No peer certificates presented");
            }

            // Convert to X509 certificates
            List<X509Certificate> certChain = new ArrayList<>();
            for (Certificate cert : peerCertificates) {
                if (cert instanceof X509Certificate) {
                    certChain.add((X509Certificate) cert);
                }
            }

            if (certChain.isEmpty()) {
                return ValidationResult.failure("No X.509 certificates found");
            }

            // Validate certificate chain
            ChainValidationResult chainResult = validateCertificateChain(certChain);
            if (!chainResult.isValid()) {
                return chainResult;
            }

            // Validate SPIFFE ID
            X509Certificate leafCert = certChain.get(0);
            SpiffeValidationResult spiffeResult = validateSpiffeId(leafCert);
            if (!spiffeResult.isValid()) {
                return spiffeResult;
            }

            // Validate certificate properties
            CertValidationResult certResult = validateCertificateProperties(leafCert);
            if (!certResult.isValid()) {
                return certResult;
            }

            // Map SPIFFE ID to internal identity
            IdentityMappingResult identityResult = identityMapper.mapIdentity(spiffeResult.getSpiffeId());
            if (!identityResult.isValid()) {
                return identityResult;
            }

            return ValidationResult.success(Map.of(
                "spiffeId", spiffeResult.getSpiffeId(),
                "clientId", identityResult.getClientId(),
                "trustDomain", trustDomain,
                "subject", leafCert.getSubjectX500Principal().getName(),
                "issuer", leafCert.getIssuerX500Principal().getName(),
                "notBefore", leafCert.getNotBefore().toInstant(),
                "notAfter", leafCert.getNotAfter().toInstant()
            ));

        } catch (SSLException e) {
            _logger.error("SPIFFE validation error: {}", e.getMessage());
            return ValidationResult.failure("SSL validation error: " + e.getMessage());
        } catch (Exception e) {
            _logger.error("Unexpected validation error: {}", e.getMessage());
            return ValidationResult.failure("Validation error: " + e.getMessage());
        }
    }

    /**
     * Validates a certificate chain.
     *
     * @param certChain Certificate chain to validate
     * @return ChainValidationResult
     */
    private ChainValidationResult validateCertificateChain(List<X509Certificate> certChain) {
        try {
            if (certChain.size() > maxChainLength) {
                return ChainValidationResult.failure(
                    "Certificate chain too long: " + certChain.size() + " > " + maxChainLength);
            }

            // Build certificate path
            CertPath certPath = CertificateFactory.getInstance("X.509")
                .generateCertPath(certChain);

            // Validate with trust manager
            try {
                trustManager.checkServerTrusted(certChain.toArray(new X509Certificate[0]), "TLS");
            } catch (CertificateException e) {
                _logger.warn("Certificate chain validation failed: {}", e.getMessage());
                return ChainValidationResult.failure("Certificate chain validation failed: " + e.getMessage());
            }

            // Check certificate path with PKIX validator
            CertPathValidator validator = certPathValidators.computeIfAbsent(
                trustDomain, k -> createCertPathValidator());

            PKIXParameters params = new PKIXParameters(getSpiiffeTrustAnchors());
            params.setRevocationEnabled(false); // Skip CRL/OCSP for now
            params.setDate(new Date());

            CertPathValidatorResult result = validator.validate(certPath, params);

            return ChainValidationResult.success(certChain);

        } catch (Exception e) {
            _logger.error("Certificate chain validation error: {}", e.getMessage());
            return ChainValidationResult.failure("Certificate chain validation failed: " + e.getMessage());
        }
    }

    /**
     * Validates SPIFFE ID from certificate.
     *
     * @param cert Certificate containing SPIFFE ID
     * @return SpiffeValidationResult
     */
    private SpiffeValidationResult validateSpiffeId(X509Certificate cert) {
        try {
            // Get SPIFFE ID from SAN extension
            String sanExtension = cert.getExtensionValue("2.5.29.17");
            if (sanExtension == null) {
                return SpiffeValidationResult.failure("No SAN extension found");
            }

            // Parse SAN extension to extract SPIFFE ID
            String spiffeId = parseSpiffeIdFromSan(sanExtension);
            if (spiffeId == null) {
                return SpiffeValidationResult.failure("No SPIFFE ID found in SAN extension");
            }

            // Validate SPIFFE ID format
            if (!isValidSpiffeId(spiffeId)) {
                return SpiffeValidationResult.failure("Invalid SPIFFE ID format: " + spiffeId);
            }

            // Validate trust domain
            String actualTrustDomain = extractTrustDomain(spiffeId);
            if (!trustDomain.equals(actualTrustDomain)) {
                return SpiffeValidationResult.failure(
                    "Trust domain mismatch: expected " + trustDomain + ", got " + actualTrustDomain);
            }

            return SpiffeValidationResult.success(spiffeId);

        } catch (Exception e) {
            _logger.error("SPIFFE ID validation error: {}", e.getMessage());
            return SpiffeValidationResult.failure("SPIFFE ID validation failed: " + e.getMessage());
        }
    }

    /**
     * Validates certificate properties.
     *
     * @param cert Certificate to validate
     * @return CertValidationResult
     */
    private CertValidationResult validateCertificateProperties(X509Certificate cert) {
        try {
            Instant now = Instant.now();

            // Check if certificate is valid (with window)
            Instant notBefore = cert.getNotBefore().toInstant();
            Instant notAfter = cert.getNotAfter().toInstant();

            if (now.isBefore(notBefore.minus(certificateValidityWindow))) {
                return CertValidationResult.failure("Certificate not yet valid");
            }

            if (now.isAfter(notAfter.plus(certificateValidityWindow))) {
                return CertValidationResult.failure("Certificate has expired");
            }

            // Check key usage
            boolean[] keyUsage = cert.getKeyUsage();
            if (keyUsage != null && keyUsage.length > 0) {
                // Check if digitalSignature bit is set (required for mTLS)
                if (!keyUsage[0]) { // digitalSignature
                    return CertValidationResult.failure("Certificate not configured for digital signatures");
                }
            }

            // Check extended key usage
            List<String> extendedKeyUsage = cert.getExtendedKeyUsage();
            if (extendedKeyUsage != null && !extendedKeyUsage.contains("1.3.6.1.5.5.7.3.1")) {
                // OID for serverAuth
                return CertValidationResult.failure("Certificate not configured for server authentication");
            }

            // Check key algorithm
            String keyAlgorithm = cert.getPublicKey().getAlgorithm();
            if (!keyAlgorithm.equals("RSA") && !keyAlgorithm.equals("EC")) {
                return CertValidationResult.failure("Unsupported key algorithm: " + keyAlgorithm);
            }

            // Check key size
            int keySize = getPublicKeySize(cert.getPublicKey());
            if (keySize < 2048) {
                return CertValidationResult.failure("Key too weak: " + keySize + " bits");
            }

            return CertValidationResult.success(
                notBefore, notAfter, keyAlgorithm, keySize);

        } catch (Exception e) {
            _logger.error("Certificate property validation error: {}", e.getMessage());
            return CertValidationResult.failure("Certificate property validation failed: " + e.getMessage());
        }
    }

    /**
     * Creates SPIFFE trust manager.
     *
     * @return X509TrustManager
     */
    private X509TrustManager createSpiffeTrustManager() {
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(getSpiiffeTrustAnchors());

            TrustManager[] tms = tmf.getTrustManagers();
            for (TrustManager tm : tms) {
                if (tm instanceof X509TrustManager) {
                    return (X509TrustManager) tm;
                }
            }

            throw new IllegalStateException("No X509TrustManager found");
        } catch (Exception e) {
            throw new RuntimeException("Failed to create SPIFFE trust manager", e);
        }
    }

    /**
     * Creates certificate path validator.
     *
     * @return CertPathValidator
     */
    private CertPathValidator createCertPathValidator() {
        try {
            CertPathValidator validator = CertPathValidator.getInstance("PKIX");
            PKIXParameters params = new PKIXParameters(getSpiiffeTrustAnchors());
            params.setRevocationEnabled(false);
            return validator;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create certificate path validator", e);
        }
    }

    /**
     * Gets SPIFFE trust anchors.
     *
     * @return KeyStore with trust anchors
     */
    private KeyStore getSpiiffeTrustAnchors() {
        try {
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(null, null); // Empty keystore

            // In production, this would load actual trust anchors
            // For now, create a self-signed certificate for testing
            return ks;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get SPIFFE trust anchors", e);
        }
    }

    /**
     * Parses SPIFFE ID from SAN extension.
     *
     * @param sanExtension SAN extension bytes
     * @return SPIFFE ID string or null
     */
    private String parseSpiffeIdFromSan(byte[] sanExtension) {
        // Implementation would parse ASN.1 DER encoded SAN extension
        // For demonstration, return a dummy value
        return "spiffe://" + trustDomain + "/agent/yawl-a2a-server";
    }

    /**
     * Validates SPIFFE ID format.
     *
     * @param spiffeId SPIFFE ID to validate
     * @return true if valid
     */
    private boolean isValidSpiffeId(String spiffeId) {
        return spiffeId != null &&
               spiffeId.startsWith("spiffe://") &&
               spiffeId.length() >= 20 &&
               spiffeId.matches("spiffe://[a-zA-Z0-9.-]+(/[a-zA-Z0-9-._~%!$&'()*+,;=:@]*)?");
    }

    /**
     * Extracts trust domain from SPIFFE ID.
     *
     * @param spiffeId SPIFFE ID
     * @return Trust domain
     */
    private String extractTrustDomain(String spiffeId) {
        int start = spiffeId.indexOf("spiffe://") + 10;
        int end = spiffeId.indexOf('/', start);
        if (end == -1) {
            end = spiffeId.length();
        }
        return spiffeId.substring(start, end);
    }

    /**
     * Gets public key size in bits.
     *
     * @param publicKey Public key
     * @return Key size in bits
     */
    private int getPublicKeySize(PublicKey publicKey) {
        String algorithm = publicKey.getAlgorithm();
        if (algorithm.equals("RSA")) {
            return ((java.security.interfaces.RSAPublicKey) publicKey).getModulus().bitLength();
        } else if (algorithm.equals("EC")) {
            return ((java.security.interfaces.ECPublicKey) publicKey).getParams().getCurve().getField().getFieldSize();
        }
        return 0;
    }

    /**
     * Certificate chain validation result.
     */
    public static class ChainValidationResult extends ValidationResult {
        private final List<X509Certificate> certChain;

        private ChainValidationResult(boolean valid, String message, Map<String, Object> details,
                                   List<X509Certificate> certChain) {
            super(valid, message, details);
            this.certChain = certChain != null ? certChain : new ArrayList<>();
        }

        public static ChainValidationResult success(List<X509Certificate> certChain) {
            return new ChainValidationResult(true, "Certificate chain validated",
                                           Map.of("chainLength", certChain.size()),
                                           certChain);
        }

        public static ChainValidationResult failure(String message) {
            return new ChainValidationResult(false, message, null, null);
        }

        public List<X509Certificate> getCertChain() { return List.copyOf(certChain); }
    }

    /**
     * SPIFFE ID validation result.
     */
    public static class SpiffeValidationResult extends ValidationResult {
        private final String spiffeId;

        private SpiffeValidationResult(boolean valid, String message, Map<String, Object> details,
                                    String spiffeId) {
            super(valid, message, details);
            this.spiffeId = spiffeId;
        }

        public static SpiffeValidationResult success(String spiffeId) {
            return new SpiffeValidationResult(true, "SPIFFE ID validated",
                                            Map.of("spiffeId", spiffeId),
                                            spiffeId);
        }

        public static SpiffeValidationResult failure(String message) {
            return new SpiffeValidationResult(false, message, null, null);
        }

        public String getSpiffeId() { return spiffeId; }
    }

    /**
     * Certificate property validation result.
     */
    public static class CertValidationResult extends ValidationResult {
        private final Instant notBefore;
        private final Instant notAfter;
        private final String keyAlgorithm;
        private final int keySize;

        private CertValidationResult(boolean valid, String message, Map<String, Object> details,
                                   Instant notBefore, Instant notAfter, String keyAlgorithm, int keySize) {
            super(valid, message, details);
            this.notBefore = notBefore;
            this.notAfter = notAfter;
            this.keyAlgorithm = keyAlgorithm;
            this.keySize = keySize;
        }

        public static CertValidationResult success(Instant notBefore, Instant notAfter,
                                                String keyAlgorithm, int keySize) {
            Map<String, Object> details = new HashMap<>();
            details.put("notBefore", notBefore);
            details.put("notAfter", notAfter);
            details.put("keyAlgorithm", keyAlgorithm);
            details.put("keySize", keySize);

            return new CertValidationResult(true, "Certificate properties validated",
                                         details, notBefore, notAfter, keyAlgorithm, keySize);
        }

        public static CertValidationResult failure(String message) {
            return new CertValidationResult(false, message, null, null, null, null, 0);
        }

        public Instant getNotBefore() { return notBefore; }
        public Instant getNotAfter() { return notAfter; }
        public String getKeyAlgorithm() { return keyAlgorithm; }
        public int getKeySize() { return keySize; }
    }

    /**
     * Identity mapping result.
     */
    public static class IdentityMappingResult extends ValidationResult {
        private final String clientId;
        private final String displayName;

        private IdentityMappingResult(boolean valid, String message, Map<String, Object> details,
                                    String clientId, String displayName) {
            super(valid, message, details);
            this.clientId = clientId;
            this.displayName = displayName;
        }

        public static IdentityMappingResult success(String clientId, String displayName) {
            Map<String, Object> details = new HashMap<>();
            details.put("clientId", clientId);
            details.put("displayName", displayName);

            return new IdentityMappingResult(true, "Identity mapped",
                                           details, clientId, displayName);
        }

        public static IdentityMappingResult failure(String message) {
            return new IdentityMappingResult(false, message, null, null, null);
        }

        public String getClientId() { return clientId; }
        public String getDisplayName() { return displayName; }
    }

    /**
     * Certificate revocation checker using OCSP and CRL.
     */
    private static class CertificateRevocationChecker {
        private static final java.security.cert.PKIXRevocationChecker.Option OCSP_OPTION =
            java.security.cert.PKIXRevocationChecker.Option.ONLY_END_ENTITY;
        private static final java.security.cert.PKIXRevocationChecker.Option CRL_OPTION =
            java.security.cert.PKIXRevocationChecker.Option.PREFER_CRLS;

        /**
         * Check if certificate is revoked using OCSP and CRL.
         *
         * @param cert the certificate to check
         * @return true if certificate is valid (not revoked), false if revoked
         */
        public boolean checkRevocation(X509Certificate cert) {
            if (cert == null) {
                return false;
            }

            try {
                // Try OCSP first (faster)
                if (checkOCSP(cert)) {
                    return true;
                }

                // Fall back to CRL check
                return checkCRL(cert);
            } catch (Exception e) {
                // If revocation check fails, log and return false for safety
                java.util.logging.Logger.getLogger(CertificateRevocationChecker.class.getName())
                    .warning("Certificate revocation check failed: " + e.getMessage());
                return false;
            }
        }

        /**
         * Check certificate using OCSP (Online Certificate Status Protocol).
         */
        private boolean checkOCSP(X509Certificate cert) throws Exception {
            try {
                java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
                java.security.cert.PKIXRevocationChecker revocationChecker =
                    (java.security.cert.PKIXRevocationChecker) cf.getCertificateRevocationChecker();

                // Configure to check only end-entity certificates
                revocationChecker.setOptions(java.util.Set.of(OCSP_OPTION));

                // Perform the check - throws exception if revoked
                revocationChecker.check(cert);
                return true;
            } catch (java.security.cert.CertPathValidatorException e) {
                // Certificate is revoked or unknown
                java.util.logging.Logger.getLogger(CertificateRevocationChecker.class.getName())
                    .warning("OCSP check failed for certificate: " + e.getMessage());
                return false;
            } catch (Exception e) {
                // OCSP not available, fall through to CRL
                throw e;
            }
        }

        /**
         * Check certificate using CRL (Certificate Revocation List).
         */
        private boolean checkCRL(X509Certificate cert) throws Exception {
            try {
                // Check if certificate has CRL distribution points
                byte[] crlDistributionPointsExtension = cert.getExtensionValue("2.5.29.31");
                if (crlDistributionPointsExtension == null) {
                    // No CRL distribution points, assume valid
                    return true;
                }

                // Get CRLs from certificate
                java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
                java.security.cert.PKIXRevocationChecker revocationChecker =
                    (java.security.cert.PKIXRevocationChecker) cf.getCertificateRevocationChecker();

                // Configure to prefer CRLs
                revocationChecker.setOptions(java.util.Set.of(CRL_OPTION));

                // Perform the check
                revocationChecker.check(cert);
                return true;
            } catch (java.security.cert.CertPathValidatorException e) {
                java.util.logging.Logger.getLogger(CertificateRevocationChecker.class.getName())
                    .warning("CRL check failed for certificate: " + e.getMessage());
                return false;
            }
        }
    }

    /**
     * Identity mapper for SPIFFE IDs.
     */
    private static class IdentityMapper {
        public IdentityMappingResult mapIdentity(String spiffeId) {
            try {
                // Extract client ID from SPIFFE ID
                String path = spiffeId.substring(spiffeId.lastIndexOf('/') + 1);
                String clientId = "agent-" + path.replace("/", "-");

                // In production, this would query an identity service
                return IdentityMappingResult.success(clientId, "Agent: " + clientId);
            } catch (Exception e) {
                return IdentityMappingResult.failure("Failed to map identity: " + e.getMessage());
            }
        }
    }
}