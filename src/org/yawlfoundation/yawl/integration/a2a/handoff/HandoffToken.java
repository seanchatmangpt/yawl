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

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable record representing a handoff token for secure agent-to-agent work transfer.
 *
 * <p>The handoff token is a JWT-based credential that allows one agent (the source)
 * to transfer a work item to another agent (the target) while preserving the session
 * state and preventing unauthorized access. The token is short-lived (default 60s)
 * and cryptographically signed by the source agent.
 *
 * <p>Token structure:
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
 * @param workItemId the YAWL work item ID being handed off
 * @param fromAgent the source agent ID initiating the handoff
 * @param toAgent the target agent ID receiving the handoff
 * @param engineSession the Interface B session handle for the work item
 * @param expiresAt the token expiration time (UTC)
 * @param jwt the signed JWT string for authorization headers
 *
 * @since YAWL 5.2
 * @see HandoffProtocol
 * @see HandoffMessage
 */
public record HandoffToken(
    String workItemId,
    String fromAgent,
    String toAgent,
    String engineSession,
    Instant expiresAt,
    String jwt
) {

    /**
     * Creates a new handoff token with all required fields.
     *
     * @param workItemId the YAWL work item ID being handed off
     * @param fromAgent the source agent ID initiating the handoff
     * @param toAgent the target agent ID receiving the handoff
     * @param engineSession the Interface B session handle for the work item
     * @param expiresAt the token expiration time (UTC)
     * @param jwt the signed JWT string for authorization headers
     * @throws IllegalArgumentException if any field is null or blank
     */
    public HandoffToken {
        Objects.requireNonNull(workItemId, "workItemId cannot be null");
        Objects.requireNonNull(fromAgent, "fromAgent cannot be null");
        Objects.requireNonNull(toAgent, "toAgent cannot be null");
        Objects.requireNonNull(engineSession, "engineSession cannot be null");
        Objects.requireNonNull(expiresAt, "expiresAt cannot be null");
        Objects.requireNonNull(jwt, "jwt cannot be null");

        if (workItemId.isBlank()) {
            throw new IllegalArgumentException("workItemId cannot be blank");
        }
        if (fromAgent.isBlank()) {
            throw new IllegalArgumentException("fromAgent cannot be blank");
        }
        if (toAgent.isBlank()) {
            throw new IllegalArgumentException("toAgent cannot be blank");
        }
        if (engineSession.isBlank()) {
            throw new IllegalArgumentException("engineSession cannot be blank");
        }
        if (jwt.isBlank()) {
            throw new IllegalArgumentException("jwt cannot be blank");
        }
    }

    /**
     * Returns a copy of this token with the expiration time updated.
     * Note: The JWT is preserved from the original token. If the expiration
     * changes significantly, a new token should be generated via HandoffProtocol.
     *
     * @param newExpiresAt the new expiration time
     * @return a new handoff token with updated expiration
     */
    public HandoffToken withExpiresAt(Instant newExpiresAt) {
        return new HandoffToken(workItemId, fromAgent, toAgent, engineSession, newExpiresAt, jwt);
    }

    /**
     * Returns true if this token is still valid (not expired).
     *
     * @return true if the token is valid, false if expired
     */
    public boolean isValid() {
        return expiresAt.isAfter(Instant.now());
    }

    /**
     * Returns the time remaining until this token expires.
     *
     * @return the duration until expiration, or zero if already expired
     */
    public java.time.Duration timeToExpiry() {
        return java.time.Duration.between(Instant.now(), expiresAt);
    }

    /**
     * Returns the JWT string representation of this token.
     * This method is used for authorization headers in HTTP requests.
     *
     * @return the JWT string
     */
    public String getJwt() {
        return jwt;
    }
}