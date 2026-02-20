/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.gregverse.config;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Generates and manages handoff tokens for A2A agent-to-agent communication.
 *
 * <p>Tokens are signed JWT-like structures containing agent identity, scope, and expiration.
 * All tokens are signed with HMAC-SHA256 using a server-side secret key.</p>
 *
 * <h2>Token Format</h2>
 * <pre>
 *   base64(header).base64(payload).base64(signature)
 *   where header = {"alg":"HS256"}
 *         payload = {"iss":"yawl-a2a","agentId":"...","scope":"...","exp":...}
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class HandoffToken {

    private static final String ALGORITHM = "HS256";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String ISSUER = "yawl-a2a";
    private static final long DEFAULT_TTL_SECONDS = 3600; // 1 hour

    private final byte[] secret;
    private final long ttlSeconds;

    /**
     * Creates a HandoffToken generator with the specified secret.
     *
     * @param secret the HMAC signing secret (must be at least 32 bytes)
     * @throws IllegalArgumentException if secret is null or too short
     */
    public HandoffToken(byte[] secret) {
        this(secret, DEFAULT_TTL_SECONDS);
    }

    /**
     * Creates a HandoffToken generator with the specified secret and TTL.
     *
     * @param secret the HMAC signing secret (must be at least 32 bytes)
     * @param ttlSeconds the token time-to-live in seconds
     * @throws IllegalArgumentException if secret is null or too short
     */
    public HandoffToken(byte[] secret, long ttlSeconds) {
        Objects.requireNonNull(secret, "secret cannot be null");
        if (secret.length < 32) {
            throw new IllegalArgumentException("secret must be at least 32 bytes");
        }
        this.secret = secret.clone();
        this.ttlSeconds = ttlSeconds;
    }

    /**
     * Generates a new handoff token for the specified agent with given scope.
     *
     * @param agentId the agent identifier
     * @param scope the permission scope (e.g., "PERM_SKILL_INVOKE", "PERM_HANDOFF")
     * @return the signed JWT token
     * @throws IllegalArgumentException if agentId or scope is null or empty
     */
    public String generateToken(String agentId, String scope) {
        if (agentId == null || agentId.isEmpty()) {
            throw new IllegalArgumentException("agentId cannot be null or empty");
        }
        if (scope == null || scope.isEmpty()) {
            throw new IllegalArgumentException("scope cannot be null or empty");
        }

        long now = System.currentTimeMillis() / 1000;
        long exp = now + ttlSeconds;

        // Build header
        String header = encodeJson(Map.of("alg", ALGORITHM, "typ", "JWT"));

        // Build payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("iss", ISSUER);
        payload.put("agentId", agentId);
        payload.put("scope", scope);
        payload.put("iat", now);
        payload.put("exp", exp);
        payload.put("jti", generateJti());
        String payloadEncoded = encodeJson(payload);

        // Sign
        String signature = sign(header + "." + payloadEncoded);

        return header + "." + payloadEncoded + "." + signature;
    }

    /**
     * Verifies and parses a handoff token.
     *
     * @param token the JWT token to verify
     * @return the parsed token claims
     * @throws TokenVerificationException if token is invalid or expired
     */
    public TokenClaims verifyToken(String token) throws TokenVerificationException {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new TokenVerificationException("Invalid token format");
        }

        String header = parts[0];
        String payload = parts[1];
        String signature = parts[2];

        // Verify signature
        String expectedSignature = sign(header + "." + payload);
        if (!constantTimeEquals(signature, expectedSignature)) {
            throw new TokenVerificationException("Invalid token signature");
        }

        // Decode and parse payload
        Map<String, Object> claims = decodeJson(payload);

        // Verify issuer
        if (!ISSUER.equals(claims.get("iss"))) {
            throw new TokenVerificationException("Invalid issuer");
        }

        // Verify expiration
        Object expObj = claims.get("exp");
        if (expObj == null) {
            throw new TokenVerificationException("Missing expiration claim");
        }
        long exp = ((Number) expObj).longValue();
        long now = System.currentTimeMillis() / 1000;
        if (now > exp) {
            throw new TokenVerificationException("Token expired");
        }

        String agentId = (String) claims.get("agentId");
        String scope = (String) claims.get("scope");
        if (agentId == null || agentId.isEmpty() || scope == null || scope.isEmpty()) {
            throw new TokenVerificationException("Missing required claims");
        }

        return new TokenClaims(agentId, scope, exp);
    }

    /**
     * Exception thrown when token verification fails.
     */
    public static class TokenVerificationException extends Exception {
        public TokenVerificationException(String message) {
            super(message);
        }

        public TokenVerificationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Parsed token claims.
     */
    public record TokenClaims(String agentId, String scope, long expiration) {
        public TokenClaims {
            Objects.requireNonNull(agentId);
            Objects.requireNonNull(scope);
            if (expiration <= 0) {
                throw new IllegalArgumentException("expiration must be positive");
            }
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, 0, secret.length, HMAC_ALGORITHM));
            byte[] signature = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign token", e);
        }
    }

    private String encodeJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var entry : map.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof String) {
                sb.append("\"").append(value).append("\"");
            } else {
                sb.append(value);
            }
            first = false;
        }
        sb.append("}");
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> decodeJson(String encoded) throws TokenVerificationException {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(encoded);
            String json = new String(decoded, StandardCharsets.UTF_8);
            // Simple JSON parsing - not using external library per YAWL minimalism
            Map<String, Object> result = new HashMap<>();
            String content = json.substring(1, json.length() - 1); // Remove {}

            for (String pair : content.split(",")) {
                String[] kv = pair.split(":");
                if (kv.length == 2) {
                    String key = kv[0].replaceAll("[\"\\s]", "");
                    String rawValue = kv[1].replaceAll("[\"\\s]", "");
                    Object value;
                    try {
                        value = Long.parseLong(rawValue);
                    } catch (NumberFormatException e) {
                        value = rawValue;
                    }
                    result.put(key, value);
                }
            }
            return result;
        } catch (Exception e) {
            throw new TokenVerificationException("Failed to decode token payload", e);
        }
    }

    private String generateJti() {
        byte[] jti = new byte[16];
        new SecureRandom().nextBytes(jti);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(jti);
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        if (aBytes.length != bBytes.length) return false;
        int result = 0;
        for (int i = 0; i < aBytes.length; i++) {
            result |= aBytes[i] ^ bBytes[i];
        }
        return result == 0;
    }
}
