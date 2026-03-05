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

package org.yawlfoundation.yawl.integration.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * HMAC-SHA256 signer and verifier for outbound webhook requests.
 *
 * <p>Every outbound webhook request carries an {@code X-YAWL-Signature-256} header
 * with value {@code sha256=<hex-encoded-hmac>}. The HMAC is computed over the raw
 * UTF-8 request body bytes using the subscription's secret key.
 *
 * <h2>Header Format</h2>
 * <pre>
 * X-YAWL-Signature-256: sha256=3d9fef4e8d3b2e1a7c4b9f0e5d1a2c3b4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b
 * </pre>
 *
 * <h2>Receiver Verification (Java)</h2>
 * <pre>
 * {@code
 * // Extract signature from header
 * String receivedHeader = request.getHeader("X-YAWL-Signature-256");
 * // receivedHeader = "sha256=abc123..."
 * if (receivedHeader == null || !receivedHeader.startsWith("sha256=")) {
 *     throw new SecurityException("Missing or malformed X-YAWL-Signature-256 header");
 * }
 * String receivedHex = receivedHeader.substring(7);  // strip "sha256="
 *
 * // Compute expected HMAC over raw body
 * byte[] body = request.getInputStream().readAllBytes();
 * String computedHex = WebhookSigner.computeHmacHex(webhookSecret, body);
 *
 * // Constant-time comparison to prevent timing attacks
 * if (!WebhookSigner.secureEquals(computedHex, receivedHex)) {
 *     throw new SecurityException("Webhook signature verification failed");
 * }
 * }
 * </pre>
 *
 * <h2>Receiver Verification (Python)</h2>
 * <pre>
 * import hmac, hashlib
 * def verify_webhook(secret: str, body: bytes, received_header: str) -> bool:
 *     expected = "sha256=" + hmac.new(
 *         secret.encode("utf-8"),
 *         body,
 *         hashlib.sha256
 *     ).hexdigest()
 *     return hmac.compare_digest(expected, received_header)
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class WebhookSigner {

    /** HTTP header name for the YAWL webhook signature. */
    public static final String SIGNATURE_HEADER = "X-YAWL-Signature-256";

    /** Prefix of the signature header value. */
    public static final String SIGNATURE_PREFIX = "sha256=";

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private WebhookSigner() {
        throw new UnsupportedOperationException(
                "WebhookSigner is a utility class and cannot be instantiated.");
    }

    /**
     * Compute the HMAC-SHA256 of the given body bytes using the given secret key.
     *
     * @param secretKey UTF-8 secret key string (minimum 32 characters recommended)
     * @param bodyBytes raw request body bytes
     * @return lowercase hex-encoded HMAC-SHA256 digest
     * @throws SignatureException if HMAC computation fails (algorithm unavailable or invalid key)
     */
    public static String computeHmacHex(String secretKey, byte[] bodyBytes)
            throws SignatureException {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException("secretKey must not be blank");
        }
        if (bodyBytes == null) {
            throw new IllegalArgumentException("bodyBytes must not be null");
        }
        try {
            byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, HMAC_ALGORITHM);
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(bodyBytes);
            return HexFormat.of().formatHex(hmacBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new SignatureException("HMAC-SHA256 algorithm not available on this JVM", e);
        } catch (InvalidKeyException e) {
            throw new SignatureException("Invalid HMAC-SHA256 key", e);
        }
    }

    /**
     * Build the full {@code X-YAWL-Signature-256} header value for a given body.
     *
     * @param secretKey UTF-8 secret key string
     * @param bodyBytes raw request body bytes
     * @return header value in format {@code sha256=<hex>}
     * @throws SignatureException if HMAC computation fails
     */
    public static String buildSignatureHeader(String secretKey, byte[] bodyBytes)
            throws SignatureException {
        return SIGNATURE_PREFIX + computeHmacHex(secretKey, bodyBytes);
    }

    /**
     * Verify that the received signature header matches the expected HMAC
     * computed over the request body. Uses constant-time comparison.
     *
     * @param secretKey      UTF-8 secret key string
     * @param bodyBytes      raw request body bytes
     * @param receivedHeader value of the {@code X-YAWL-Signature-256} header
     * @return true if the signature is valid
     * @throws SignatureException if HMAC computation fails
     */
    public static boolean verify(String secretKey, byte[] bodyBytes, String receivedHeader)
            throws SignatureException {
        if (receivedHeader == null || !receivedHeader.startsWith(SIGNATURE_PREFIX)) {
            return false;
        }
        String receivedHex = receivedHeader.substring(SIGNATURE_PREFIX.length());
        String expectedHex = computeHmacHex(secretKey, bodyBytes);
        return secureEquals(expectedHex, receivedHex);
    }

    /**
     * Constant-time string equality check to prevent timing oracle attacks.
     * Both strings are compared as UTF-8 byte arrays using
     * {@link MessageDigest#isEqual(byte[], byte[])}.
     *
     * @param a first string
     * @param b second string
     * @return true if strings are equal
     */
    public static boolean secureEquals(String a, String b) {
        if (a == null || b == null) {
            return a == b;
        }
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(aBytes, bBytes);
    }

    /**
     * Thrown when webhook signature computation or verification fails.
     */
    public static final class SignatureException extends Exception {

        /**
         * Construct with message.
         *
         * @param message error description
         */
        public SignatureException(String message) {
            super(message);
        }

        /**
         * Construct with message and cause.
         *
         * @param message error description
         * @param cause   underlying exception
         */
        public SignatureException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
