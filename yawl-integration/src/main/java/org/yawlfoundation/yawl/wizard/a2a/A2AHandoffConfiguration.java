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

package org.yawlfoundation.yawl.integration.wizard.a2a;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable configuration for A2A handoff protocol between agents.
 *
 * <p>Configures the secure handoff of work items from one agent to another,
 * following ADR-025 (YAWL A2A Handoff Protocol). Handoff allows an agent to
 * transfer a work item to a specialized agent when it cannot complete the work.
 *
 * <p>Handoff flow:
 * <ol>
 *   <li>Source agent checks out work item from YAWL engine</li>
 *   <li>Source issues HandoffToken (JWT-signed with caseId, taskId, target)</li>
 *   <li>Target agent validates token signature and claims work item</li>
 *   <li>Source agent rolls back its checkout (releases the lock)</li>
 *   <li>Target executes work item and completes it</li>
 * </ol>
 *
 * @param handoffConfigId unique identifier for this handoff configuration
 * @param sourceAgentId ID of the agent initiating the handoff
 * @param targetAgentId ID of the agent receiving the work item
 * @param authMethod authentication method for handoff token (JWT, API_KEY, SPIFFE_MTLS)
 * @param tokenExpirySeconds JWT token time-to-live in seconds
 * @param requiresMutualTls whether mTLS is required between agents
 * @param agentEndpoints mapping of agent ID → endpoint URL
 * @param configuredAt timestamp when configuration was created
 *
 * @since YAWL 6.0
 * @see org.yawlfoundation.yawl.integration.a2a.handoff.HandoffProtocol
 * @see org.yawlfoundation.yawl.integration.a2a.handoff.HandoffToken
 */
public record A2AHandoffConfiguration(
    String handoffConfigId,
    String sourceAgentId,
    String targetAgentId,
    String authMethod,
    long tokenExpirySeconds,
    boolean requiresMutualTls,
    Map<String, String> agentEndpoints,
    Instant configuredAt
) {
    /**
     * Compact constructor ensures immutability of mutable fields.
     */
    public A2AHandoffConfiguration {
        Objects.requireNonNull(handoffConfigId, "handoffConfigId cannot be null");
        Objects.requireNonNull(sourceAgentId, "sourceAgentId cannot be null");
        Objects.requireNonNull(targetAgentId, "targetAgentId cannot be null");
        Objects.requireNonNull(authMethod, "authMethod cannot be null");
        Objects.requireNonNull(agentEndpoints, "agentEndpoints cannot be null");
        Objects.requireNonNull(configuredAt, "configuredAt cannot be null");

        if (tokenExpirySeconds <= 0) {
            throw new IllegalArgumentException("tokenExpirySeconds must be positive");
        }
        if (handoffConfigId.isBlank()) {
            throw new IllegalArgumentException("handoffConfigId cannot be blank");
        }
        if (sourceAgentId.isBlank()) {
            throw new IllegalArgumentException("sourceAgentId cannot be blank");
        }
        if (targetAgentId.isBlank()) {
            throw new IllegalArgumentException("targetAgentId cannot be blank");
        }

        agentEndpoints = Collections.unmodifiableMap(Map.copyOf(agentEndpoints));
    }

    /**
     * Gets the endpoint URL for the source agent.
     *
     * @return source agent endpoint, or null if not configured
     */
    public String sourceEndpoint() {
        return agentEndpoints.get(sourceAgentId);
    }

    /**
     * Gets the endpoint URL for the target agent.
     *
     * @return target agent endpoint, or null if not configured
     */
    public String targetEndpoint() {
        return agentEndpoints.get(targetAgentId);
    }

    /**
     * Checks if JWT authentication is configured.
     *
     * @return true if authMethod is "JWT"
     */
    public boolean isJwtAuth() {
        return "JWT".equalsIgnoreCase(authMethod);
    }

    /**
     * Checks if API Key authentication is configured.
     *
     * @return true if authMethod is "API_KEY"
     */
    public boolean isApiKeyAuth() {
        return "API_KEY".equalsIgnoreCase(authMethod);
    }

    /**
     * Checks if SPIFFE mTLS authentication is configured.
     *
     * @return true if authMethod is "SPIFFE_MTLS"
     */
    public boolean isSpiffeMtlsAuth() {
        return "SPIFFE_MTLS".equalsIgnoreCase(authMethod);
    }

    /**
     * Creates a new configuration with updated token expiry.
     *
     * @param newExpiry new token expiry in seconds
     * @return new configuration with updated expiry
     */
    public A2AHandoffConfiguration withTokenExpiry(long newExpiry) {
        return new A2AHandoffConfiguration(
            this.handoffConfigId,
            this.sourceAgentId,
            this.targetAgentId,
            this.authMethod,
            newExpiry,
            this.requiresMutualTls,
            this.agentEndpoints,
            this.configuredAt
        );
    }
}
