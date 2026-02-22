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
import java.util.List;
import java.util.Objects;

/**
 * Immutable descriptor for a discovered A2A (Agent-to-Agent) agent.
 *
 * <p>Describes an agent discovered during the DISCOVERY phase. Corresponds to
 * the A2A agent card format (/.well-known/agent.json). Includes the agent's
 * identity, capabilities (skills), supported authentication methods, and
 * current connection status.
 *
 * @param agentId unique identifier for the agent (UUID or hostname)
 * @param agentName human-readable name (e.g. "YAWL Workflow Engine")
 * @param agentUrl base URL where the agent is accessible
 * @param port port the agent is listening on (typically 8081 for YAWL A2A)
 * @param version A2A protocol version the agent implements
 * @param skills list of A2A skills exposed by the agent
 * @param supportedAuthMethods list of auth methods (["JWT", "API_KEY", "SPIFFE_MTLS"])
 * @param status current connection status of the agent
 * @param discoveredAt timestamp when the agent was discovered
 *
 * @since YAWL 6.0
 */
public record A2AAgentDescriptor(
    String agentId,
    String agentName,
    String agentUrl,
    int port,
    String version,
    List<A2ASkillDescriptor> skills,
    List<String> supportedAuthMethods,
    A2AAgentStatus status,
    Instant discoveredAt
) {
    /**
     * Compact constructor ensures immutability of mutable fields.
     */
    public A2AAgentDescriptor {
        Objects.requireNonNull(agentId, "agentId cannot be null");
        Objects.requireNonNull(agentName, "agentName cannot be null");
        Objects.requireNonNull(agentUrl, "agentUrl cannot be null");
        Objects.requireNonNull(version, "version cannot be null");
        Objects.requireNonNull(skills, "skills cannot be null");
        Objects.requireNonNull(supportedAuthMethods, "supportedAuthMethods cannot be null");
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(discoveredAt, "discoveredAt cannot be null");

        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        if (agentId.isBlank()) {
            throw new IllegalArgumentException("agentId cannot be blank");
        }
        if (agentName.isBlank()) {
            throw new IllegalArgumentException("agentName cannot be blank");
        }

        skills = Collections.unmodifiableList(List.copyOf(skills));
        supportedAuthMethods = Collections.unmodifiableList(List.copyOf(supportedAuthMethods));
    }

    /**
     * Gets the endpoint URL for this agent (combines URL and port).
     *
     * @return full endpoint URL (e.g. "http://localhost:8081")
     */
    public String endpointUrl() {
        return agentUrl + ":" + port;
    }

    /**
     * Checks if this agent has a skill with the given ID.
     *
     * @param skillId skill identifier
     * @return true if skill is available, false otherwise
     */
    public boolean hasSkill(String skillId) {
        Objects.requireNonNull(skillId, "skillId cannot be null");
        return skills.stream()
            .anyMatch(s -> s.skillId().equals(skillId));
    }

    /**
     * Checks if the agent supports a given authentication method.
     *
     * @param authMethod authentication method name
     * @return true if supported, false otherwise
     */
    public boolean supportsAuthMethod(String authMethod) {
        Objects.requireNonNull(authMethod, "authMethod cannot be null");
        return supportedAuthMethods.contains(authMethod);
    }

    /**
     * Checks if this agent is available for use.
     *
     * @return true if status is AVAILABLE or DEGRADED, false otherwise
     */
    public boolean isAvailable() {
        return status == A2AAgentStatus.AVAILABLE || status == A2AAgentStatus.DEGRADED;
    }

    /**
     * Creates a new descriptor with updated status.
     *
     * @param newStatus new status
     * @return new descriptor with updated status
     */
    public A2AAgentDescriptor withStatus(A2AAgentStatus newStatus) {
        return new A2AAgentDescriptor(
            this.agentId,
            this.agentName,
            this.agentUrl,
            this.port,
            this.version,
            this.skills,
            this.supportedAuthMethods,
            newStatus,
            this.discoveredAt
        );
    }
}
