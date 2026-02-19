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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.a2a.handoff;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.yawlfoundation.yawl.integration.a2a.auth.JwtAuthenticationProvider;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Implements the agent-to-agent handoff protocol for secure work item transfer.
 *
 * <p>The HandoffProtocol provides JWT-based token generation and validation for
 * transferring workflow items between YAWL autonomous agents. When an agent that has
 * checked out a work item determines it cannot complete it, it can use this protocol
 * to securely transfer the item to another capable agent.
 *
 * <p>Protocol sequence:
 * 1. Source agent generates a handoff token via {@link #generateHandoffToken}
 * 2. Source agent sends A2A message to target agent via {@link #createHandoffMessage}
 * 3. Target agent verifies the handoff via {@link #verifyHandoffToken}
 * 4. Target agent checks out the work item using the session handle
 * 5. Source agent rolls back its checkout via Interface B
 *
 * <p>Token claims structure:
 * <pre>
 * {
 *   "sub": "handoff",
 *   "workItemId": "WI-42",
 *   "fromAgent": "source-agent-id",
 *   "toAgent": "target-agent-id",
 *   "engineSession": "<session-handle>",
 *   "exp": 1740000060
 * }
 * </pre>
 *
 * @param jwtProvider the JWT authentication provider for signing tokens
 * @param defaultTtl the default token time-to-live (default: 60 seconds)
 *
 * @since YAWL 5.2
 * @see HandoffToken
 * @see HandoffMessage
 * @see HandoffSession
 * @see HandoffException
 */
public final class HandoffProtocol {

    private final JwtAuthenticationProvider jwtProvider;
    private final Duration defaultTtl;

    /**
     * Creates a new handoff protocol with default TTL (60 seconds).
     *
     * @param jwtProvider the JWT authentication provider for signing tokens
     */
    public HandoffProtocol(JwtAuthenticationProvider jwtProvider) {
        this(jwtProvider, Duration.ofSeconds(60));
    }

    /**
     * Creates a new handoff protocol with custom TTL.
     *
     * @param jwtProvider the JWT authentication provider for signing tokens
     * @param defaultTtl the default token time-to-live
     */
    public HandoffProtocol(JwtAuthenticationProvider jwtProvider, Duration defaultTtl) {
        this.jwtProvider = Objects.requireNonNull(jwtProvider, "jwtProvider cannot be null");
        this.defaultTtl = Objects.requireNonNull(defaultTtl, "defaultTtl cannot be null");
    }

    /**
     * Generates a handoff token for transferring a work item to another agent.
     *
     * @param workItemId the YAWL work item ID being handed off
     * @param fromAgent the source agent ID initiating the handoff
     * @param toAgent the target agent ID receiving the handoff
     * @param engineSession the Interface B session handle for the work item
     * @param ttl the token time-to-live (optional, uses default if null)
     * @return the signed handoff token
     * @throws HandoffException if token generation fails
     */
    public HandoffToken generateHandoffToken(
        String workItemId,
        String fromAgent,
        String toAgent,
        String engineSession,
        Duration ttl
    ) throws HandoffException {
        try {
            Instant expiresAt = Instant.now().plus(ttl != null ? ttl : defaultTtl);

            // Create claims for the handoff token
            Claims claims = Jwts.claims()
                .subject("handoff")
                .add("workItemId", workItemId)
                .add("fromAgent", fromAgent)
                .add("toAgent", toAgent)
                .add("engineSession", engineSession)
                .build();

            // Generate the JWT using the provider's signing key
            String jwt = jwtProvider.issueToken("handoff", List.of(),
                java.time.Duration.between(Instant.now(), expiresAt).toMillis());

            // Extract the key to verify the token immediately
            SecretKey key = jwtProvider.getClass()
                .getDeclaredField("signingKey")
                .get(jwtProvider);
            Claims parsedClaims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(jwt)
                .getPayload();

            return new HandoffToken(
                parsedClaims.get("workItemId", String.class),
                parsedClaims.get("fromAgent", String.class),
                parsedClaims.get("toAgent", String.class),
                parsedClaims.get("engineSession", String.class),
                expiresAt
            );
        } catch (Exception e) {
            throw new HandoffException("Failed to generate handoff token", e);
        }
    }

    /**
     * Generates a handoff token using default TTL.
     *
     * @param workItemId the YAWL work item ID being handed off
     * @param fromAgent the source agent ID initiating the handoff
     * @param toAgent the target agent ID receiving the handoff
     * @param engineSession the Interface B session handle for the work item
     * @return the signed handoff token
     * @throws HandoffException if token generation fails
     */
    public HandoffToken generateHandoffToken(
        String workItemId,
        String fromAgent,
        String toAgent,
        String engineSession
    ) throws HandoffException {
        return generateHandoffToken(workItemId, fromAgent, toAgent, engineSession, null);
    }

    /**
     * Creates a handoff message for A2A transmission.
     *
     * @param workItemId the YAWL work item ID being handed off
     * @param fromAgent the source agent ID initiating the handoff
     * @param toAgent the target agent ID receiving the handoff
     * @param engineSession the Interface B session handle for the work item
     * @param payload optional additional context data
     * @param ttl the token time-to-live (optional, uses default if null)
     * @return the handoff message ready for A2A transmission
     * @throws HandoffException if message creation fails
     */
    public HandoffMessage createHandoffMessage(
        String workItemId,
        String fromAgent,
        String toAgent,
        String engineSession,
        Map<String, Object> payload,
        Duration ttl
    ) throws HandoffException {
        HandoffToken token = generateHandoffToken(
            workItemId, fromAgent, toAgent, engineSession, ttl);

        HandoffMessage message = new HandoffMessage(
            workItemId, fromAgent, toAgent, token, payload, Instant.now());

        return message.withPayload(payload);
    }

    /**
     * Creates a handoff message with no payload using default TTL.
     *
     * @param workItemId the YAWL work item ID being handed off
     * @param fromAgent the source agent ID initiating the handoff
     * @param toAgent the target agent ID receiving the handoff
     * @param engineSession the Interface B session handle for the work item
     * @return the handoff message ready for A2A transmission
     * @throws HandoffException if message creation fails
     */
    public HandoffMessage createHandoffMessage(
        String workItemId,
        String fromAgent,
        String toAgent,
        String engineSession
    ) throws HandoffException {
        return createHandoffMessage(
            workItemId, fromAgent, toAgent, engineSession, null, null);
    }

    /**
     * Verifies a handoff token extracted from a message.
     *
     * @param token the handoff token to verify
     * @return the verified handoff token with validated claims
     * @throws HandoffException if token verification fails
     */
    public HandoffToken verifyHandoffToken(HandoffToken token) throws HandoffException {
        if (!token.isValid()) {
            throw new HandoffException("Handoff token has expired");
        }

        try {
            // Extract the signing key from the provider
            SecretKey key = jwtProvider.getClass()
                .getDeclaredField("signingKey")
                .get(jwtProvider);

            // Reconstruct the JWT to verify its signature
            Claims claims = Jwts.claims()
                .subject("handoff")
                .add("workItemId", token.workItemId())
                .add("fromAgent", token.fromAgent())
                .add("toAgent", token.toAgent())
                .add("engineSession", token.engineSession())
                .build();

            var parser = Jwts.parser()
                .verifyWith(key)
                .build();

            parser.parseSignedClaims(claims.toString());

            return token;
        } catch (Exception e) {
            throw new HandoffException("Failed to verify handoff token", e);
        }
    }

    /**
     * Creates a handoff session from a verified token.
     *
     * @param token the verified handoff token
     * @return a complete handoff session
     */
    public HandoffSession createSession(HandoffToken token) {
        return new HandoffSession(
            token.workItemId(),
            token.fromAgent(),
            token.toAgent(),
            token.engineSession(),
            token.expiresAt()
        );
    }

    /**
     * Validates that a handoff message is properly formed and the token is valid.
     *
     * @param message the handoff message to validate
     * @return the validated handoff session
     * @throws HandoffException if validation fails
     */
    public HandoffSession validateHandoffMessage(HandoffMessage message) throws HandoffException {
        try {
            HandoffToken verifiedToken = verifyHandoffToken(message.token());
            return createSession(verifiedToken);
        } catch (Exception e) {
            throw new HandoffException("Handoff message validation failed", e);
        }
    }

    /**
     * Returns the default token time-to-live.
     *
     * @return the default TTL
     */
    public Duration getDefaultTtl() {
        return defaultTtl;
    }

    /**
     * Creates a handoff protocol instance from environment configuration.
     *
     * @return a configured handoff protocol instance
     * @throws IllegalStateException if required environment variables are missing
     */
    public static HandoffProtocol fromEnvironment() throws IllegalStateException {
        JwtAuthenticationProvider jwtProvider = JwtAuthenticationProvider.fromEnvironment();
        return new HandoffProtocol(jwtProvider);
    }
}