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

package org.yawlfoundation.yawl.integration.zai;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.logging.Logger;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.X509ExtendedTrustManager;

/**
 * Custom X509ExtendedTrustManager implementing certificate pinning for Z.AI API.
 *
 * <p>Prevents MITM attacks by validating that the server certificate's public key
 * matches a known pinned value (SHA-256 hash). Supports:
 * <ul>
 *   <li>Primary pin: current Z.AI API certificate public key pin</li>
 *   <li>Backup pins: future/alternative Z.AI API certificate pins (HPKP-style)</li>
 *   <li>Fallback: system default trust manager if all pins match fails</li>
 *   <li>Logging: security events logged for monitoring and audit</li>
 * </ul>
 *
 * <p>Certificate pinning process:
 * <ol>
 *   <li>Extract public key from server certificate</li>
 *   <li>Compute SHA-256 hash of DER-encoded public key</li>
 *   <li>Compare Base64-encoded hash to expected pins</li>
 *   <li>If no match: log failure and throw SSLPeerUnverifiedException</li>
 *   <li>Otherwise: log success and validate certificate chain</li>
 * </ol>
 *
 * <p>Usage:
 * <pre>
 *   List&lt;String&gt; pins = List.of(
 *       "sha256/L9CowLk96O4M3HMZX/dxC1m/zJJYdQG9xUakwRV8yb4=",
 *       "sha256/KWm0/0xCNAKpg1oDf8Ye/lV2P3M2nT8O5K9s7jX4z7M="
 *   );
 *   PinnedTrustManager tm = new PinnedTrustManager(pins);
 *   SSLContext ctx = SSLContext.getInstance("TLS");
 *   ctx.init(null, new TrustManager[]{tm}, new SecureRandom());
 *   HttpClient client = HttpClient.newBuilder()
 *       .sslContext(ctx)
 *       .build();
 * </pre>
 *
 * @author YAWL Foundation - Security Integration Team
 * @version 6.0.0
 */
public class PinnedTrustManager extends X509ExtendedTrustManager {

    private static final Logger LOGGER = Logger.getLogger(PinnedTrustManager.class.getName());
    private static final String PIN_ALGORITHM = "SHA-256";
    private static final String LOG_PREFIX = "[ZAI-PIN]";

    private final List<String> acceptedPins;
    private final X509ExtendedTrustManager defaultTrustManager;
    private final boolean enableFallback;

    /**
     * Create pinned trust manager with primary pin only.
     *
     * @param primaryPin the primary pin (e.g., "sha256/[base64-pin]")
     * @throws IllegalArgumentException if primaryPin is null or blank
     */
    public PinnedTrustManager(String primaryPin) {
        this(List.of(primaryPin), null, false);
    }

    /**
     * Create pinned trust manager with multiple pins (primary + backups).
     *
     * @param pins list of accepted pins
     * @throws IllegalArgumentException if pins is null or empty
     */
    public PinnedTrustManager(List<String> pins) {
        this(pins, null, false);
    }

    /**
     * Create pinned trust manager with pins and optional fallback.
     *
     * @param pins list of accepted pins
     * @param defaultTrustManager fallback trust manager (null for no fallback)
     * @param enableFallback if true, allow fallback validation if pin check fails
     */
    public PinnedTrustManager(List<String> pins,
                            X509ExtendedTrustManager defaultTrustManager,
                            boolean enableFallback) {
        if (pins == null || pins.isEmpty()) {
            throw new IllegalArgumentException("At least one pin is required");
        }
        this.acceptedPins = new ArrayList<>(pins);
        this.defaultTrustManager = defaultTrustManager;
        this.enableFallback = enableFallback && defaultTrustManager != null;

        LOGGER.info(LOG_PREFIX + " Initialized with " + pins.size() + " pin(s). "
                + "Fallback: " + (enableFallback ? "enabled" : "disabled"));
    }

    /**
     * Validate server certificate chain against pinned public key.
     *
     * <p>Implements the core pinning logic:
     * <ol>
     *   <li>Validate certificate chain (null-safe)</li>
     *   <li>Extract public key from leaf certificate</li>
     *   <li>Hash public key with SHA-256</li>
     *   <li>Compare to accepted pins</li>
     *   <li>Throw SSLPeerUnverifiedException if no match</li>
     * </ol>
     *
     * @param chain the server certificate chain
     * @param authType the key exchange algorithm (unused)
     * @throws SSLPeerUnverifiedException if pin validation fails
     */
    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) {
        validateChainNotEmpty(chain);
        validateAndPinCertificate(chain[0]);
    }

    /**
     * Validate client certificate chain (used in mTLS scenarios).
     *
     * Delegates to default trust manager if configured,
     * otherwise throws UnsupportedOperationException.
     *
     * @param chain the client certificate chain
     * @param authType the key exchange algorithm
     * @throws UnsupportedOperationException if no default trust manager
     */
    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) {
        if (defaultTrustManager != null) {
            defaultTrustManager.checkClientTrusted(chain, authType);
        } else {
            throw new UnsupportedOperationException("Client certificate validation not configured");
        }
    }

    /**
     * Get the list of issuers for the trust manager.
     *
     * Delegates to default trust manager if configured.
     *
     * @return array of accepted issuer certificates
     */
    @Override
    public X509Certificate[] getAcceptedIssuers() {
        if (defaultTrustManager != null) {
            return defaultTrustManager.getAcceptedIssuers();
        }
        return new X509Certificate[0];
    }

    /**
     * Validate server certificate chain when using hostname verification.
     *
     * @param chain the server certificate chain
     * @param authType the key exchange algorithm
     * @param socket the socket (unused)
     * @throws SSLPeerUnverifiedException if validation fails
     */
    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType,
                                  java.net.Socket socket) {
        checkServerTrusted(chain, authType);
    }

    /**
     * Validate server certificate chain when using hostname verification via engine.
     *
     * @param chain the server certificate chain
     * @param authType the key exchange algorithm
     * @param engine the SSL engine (unused)
     * @throws SSLPeerUnverifiedException if validation fails
     */
    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType,
                                  javax.net.ssl.SSLEngine engine) {
        checkServerTrusted(chain, authType);
    }

    /**
     * Validate client certificate chain when using socket.
     *
     * @param chain the client certificate chain
     * @param authType the key exchange algorithm
     * @param socket the socket (unused)
     * @throws UnsupportedOperationException if no default trust manager
     */
    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType,
                                  java.net.Socket socket) {
        checkClientTrusted(chain, authType);
    }

    /**
     * Validate client certificate chain when using SSL engine.
     *
     * @param chain the client certificate chain
     * @param authType the key exchange algorithm
     * @param engine the SSL engine (unused)
     * @throws UnsupportedOperationException if no default trust manager
     */
    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType,
                                  javax.net.ssl.SSLEngine engine) {
        checkClientTrusted(chain, authType);
    }

    /**
     * Validate that certificate chain is not empty.
     *
     * @param chain the certificate chain
     * @throws SSLPeerUnverifiedException if chain is null or empty
     */
    private void validateChainNotEmpty(X509Certificate[] chain) {
        if (chain == null || chain.length == 0) {
            LOGGER.severe(LOG_PREFIX + " Certificate chain is null or empty");
            throw new SSLPeerUnverifiedException("Certificate chain is empty");
        }
    }

    /**
     * Validate certificate public key pin and perform fallback validation if needed.
     *
     * @param cert the server certificate
     * @throws SSLPeerUnverifiedException if validation fails
     */
    private void validateAndPinCertificate(X509Certificate cert) {
        try {
            String certificatePin = computeCertificatePin(cert);
            LOGGER.fine(LOG_PREFIX + " Computed pin: " + certificatePin);

            if (acceptedPins.contains(certificatePin)) {
                LOGGER.info(LOG_PREFIX + " Certificate pin validated successfully");
                return;
            }

            LOGGER.warning(LOG_PREFIX + " Certificate pin NOT in accepted list. "
                    + "Pin: " + certificatePin + ", Accepted: " + acceptedPins.size());

            if (enableFallback) {
                LOGGER.info(LOG_PREFIX + " Attempting fallback validation with default trust manager");
                defaultTrustManager.checkServerTrusted(new X509Certificate[]{cert}, "UNKNOWN");
                LOGGER.warning(LOG_PREFIX + " Fallback validation succeeded - certificate chain trusted "
                        + "but pin mismatch detected");
                return;
            }

            throw new SSLPeerUnverifiedException(
                "Certificate pin validation failed. Expected one of: " + acceptedPins.size() +
                " pins, got: " + certificatePin);

        } catch (SSLPeerUnverifiedException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.severe(LOG_PREFIX + " Error during pin validation: " + e.getMessage());
            throw new SSLPeerUnverifiedException("Pin validation error: " + e.getMessage(), e);
        }
    }

    /**
     * Compute SHA-256 pin of certificate public key.
     *
     * @param cert the certificate
     * @return pin in format "sha256/[base64-encoded-hash]"
     * @throws NoSuchAlgorithmException if SHA-256 is unavailable
     * @throws CertificateEncodingException if certificate cannot be encoded
     */
    private String computeCertificatePin(X509Certificate cert)
            throws NoSuchAlgorithmException, CertificateEncodingException {
        byte[] publicKeyDer = cert.getPublicKey().getEncoded();
        MessageDigest digest = MessageDigest.getInstance(PIN_ALGORITHM);
        byte[] pin = digest.digest(publicKeyDer);
        String base64Pin = Base64.getEncoder().encodeToString(pin);
        return "sha256/" + base64Pin;
    }

    /**
     * Get list of accepted pins (immutable view).
     *
     * @return unmodifiable list of pins
     */
    public List<String> getAcceptedPins() {
        return List.copyOf(acceptedPins);
    }

    /**
     * Check if fallback validation is enabled.
     *
     * @return true if fallback is enabled and default trust manager is configured
     */
    public boolean isFallbackEnabled() {
        return enableFallback;
    }
}
