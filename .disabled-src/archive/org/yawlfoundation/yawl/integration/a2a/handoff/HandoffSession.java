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
import java.util.Map;
import java.util.Objects;

/**
 * Immutable record representing a handoff session for preserving context during work transfer.
 *
 * <p>A handoff session captures the complete state of a work item being transferred
 * between agents. This includes the basic work item information plus any additional
 * context data that the source agent wants to preserve during the transfer.
 *
 * <p>Usage example:
 * <pre>{@code
 * HandoffSession session = new HandoffSession(
 *     "WI-42",
 *     "source-agent",
 *     "target-agent",
 *     "session-handle-123",
 *     Instant.now().plusSeconds(60),
 *     Map.of(
 *         "reasoning_so_far", "Document partially reviewed, language detected as Spanish",
 *         "confidence_level", 0.85,
 *         "pages_processed", 5,
 *         "total_pages", 12
 *     )
 * );
 * }</pre>
 *
 * @param workItemId the YAWL work item ID being handed off
 * @param fromAgent the source agent ID initiating the handoff
 * @param toAgent the target agent ID receiving the handoff
 * @param engineSession the Interface B session handle for the work item
 * @param tokenExpiration when the handoff token expires
 * @param context additional context data from the source agent
 *
 * @since YAWL 5.2
 * @see HandoffProtocol
 * @see HandoffMessage
 */
public record HandoffSession(
    String workItemId,
    String fromAgent,
    String toAgent,
    String engineSession,
    Instant tokenExpiration,
    Map<String, Object> context
) {

    /**
     * Creates a new handoff session with minimal context.
     *
     * @param workItemId the YAWL work item ID being handed off
     * @param fromAgent the source agent ID initiating the handoff
     * @param toAgent the target agent ID receiving the handoff
     * @param engineSession the Interface B session handle for the work item
     * @param tokenExpiration when the handoff token expires
     */
    public HandoffSession(
        String workItemId,
        String fromAgent,
        String toAgent,
        String engineSession,
        Instant tokenExpiration
    ) {
        this(workItemId, fromAgent, toAgent, engineSession, tokenExpiration, java.util.Collections.emptyMap());
    }

    /**
     * Creates a new handoff session with all fields.
     *
     * @param workItemId the YAWL work item ID being handed off
     * @param fromAgent the source agent ID initiating the handoff
     * @param toAgent the target agent ID receiving the handoff
     * @param engineSession the Interface B session handle for the work item
     * @param tokenExpiration when the handoff token expires
     * @param context additional context data from the source agent
     */
    public HandoffSession {
        Objects.requireNonNull(workItemId, "workItemId cannot be null");
        Objects.requireNonNull(fromAgent, "fromAgent cannot be null");
        Objects.requireNonNull(toAgent, "toAgent cannot be null");
        Objects.requireNonNull(engineSession, "engineSession cannot be null");
        Objects.requireNonNull(tokenExpiration, "tokenExpiration cannot be null");
        Objects.requireNonNull(context, "context cannot be null");

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
    }

    /**
     * Creates a handoff session from a handoff token.
     *
     * @param token the handoff token
     * @return a new handoff session with the token's basic information
     */
    public static HandoffSession fromToken(HandoffToken token) {
        return new HandoffSession(
            token.workItemId(),
            token.fromAgent(),
            token.toAgent(),
            token.engineSession(),
            token.expiresAt()
        );
    }

    /**
     * Returns a copy of this session with additional context merged.
     *
     * @param additionalContext the context data to add
     * @return a new handoff session with merged context
     */
    public HandoffSession withContext(Map<String, Object> additionalContext) {
        Map<String, Object> merged = new java.util.HashMap<>(this.context);
        merged.putAll(additionalContext);
        return new HandoffSession(
            workItemId,
            fromAgent,
            toAgent,
            engineSession,
            tokenExpiration,
            Map.copyOf(merged)
        );
    }

    /**
     * Returns a copy of this session with updated token expiration.
     *
     * @param newExpiration the new token expiration time
     * @return a new handoff session with updated expiration
     */
    public HandoffSession withTokenExpiration(Instant newExpiration) {
        return new HandoffSession(
            workItemId,
            fromAgent,
            toAgent,
            engineSession,
            newExpiration,
            context
        );
    }

    /**
     * Returns the handoff token derived from this session.
     *
     * <p>Note: This method throws UnsupportedOperationException because the session
     * does not store the JWT. To get a valid token, use HandoffProtocol.generateHandoffToken()
     * to create a new signed token.
     *
     * @return the handoff token (never returns)
     * @throws UnsupportedOperationException always, since JWT cannot be reconstructed
     */
    public HandoffToken toToken() {
        throw new UnsupportedOperationException(
            "Cannot reconstruct HandoffToken from session: JWT is not stored. " +
            "Use HandoffProtocol.generateHandoffToken() to create a new signed token."
        );
    }
}