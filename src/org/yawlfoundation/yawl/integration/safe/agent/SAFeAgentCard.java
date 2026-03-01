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

package org.yawlfoundation.yawl.integration.safe.agent;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Agent card for SAFe agents in YAWL, describing identity, role, capabilities, and endpoint.
 *
 * <p>An immutable record that represents a SAFe role agent participating in agile
 * ceremonies and workflow orchestration. The card serves as the agent's identity
 * and capability descriptor for A2A and MCP communication.</p>
 *
 * <p>Example:
 * <pre>{@code
 * SAFeAgentCard poCard = SAFeAgentCard.builder()
 *     .agentId("po-001")
 *     .name("Product Owner Bot")
 *     .role(SAFeAgentRole.PRODUCT_OWNER)
 *     .capabilities(List.of(
 *         AgentCapability.STORY_REFINEMENT,
 *         AgentCapability.PRIORITY_MANAGEMENT
 *     ))
 *     .host("localhost")
 *     .port(8080)
 *     .build();
 * }</pre>
 *
 * @param agentId unique identifier for this agent
 * @param name display name of the agent
 * @param role the SAFe role this agent fulfills
 * @param capabilities list of capabilities this agent has
 * @param host the host where this agent runs (for A2A communication)
 * @param port the port where this agent listens
 * @param ceremonies list of ceremony types this agent participates in
 * @param metadata additional metadata about this agent
 * @param registeredAt timestamp when agent was registered
 * @param version protocol version this agent implements
 *
 * @since YAWL 6.0
 */
public record SAFeAgentCard(
    String agentId,
    String name,
    SAFeAgentRole role,
    List<AgentCapability> capabilities,
    String host,
    int port,
    List<String> ceremonies,
    Map<String, Object> metadata,
    Instant registeredAt,
    String version
) {

    /**
     * Validates and constructs a new SAFeAgentCard.
     *
     * @param agentId unique identifier for this agent
     * @param name display name of the agent
     * @param role the SAFe role this agent fulfills
     * @param capabilities list of capabilities this agent has
     * @param host the host where this agent runs
     * @param port the port where this agent listens
     * @param ceremonies list of ceremony types this agent participates in
     * @param metadata additional metadata about this agent
     * @param registeredAt timestamp when agent was registered
     * @param version protocol version this agent implements
     *
     * @throws NullPointerException if agentId, name, role, or host is null
     * @throws IllegalArgumentException if port is invalid or agentId is empty
     */
    public SAFeAgentCard {
        Objects.requireNonNull(agentId, "agentId cannot be null");
        Objects.requireNonNull(name, "name cannot be null");
        Objects.requireNonNull(role, "role cannot be null");
        Objects.requireNonNull(host, "host cannot be null");

        if (agentId.isBlank()) {
            throw new IllegalArgumentException("agentId cannot be blank");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
    }

    /**
     * Returns the endpoint URL for this agent.
     *
     * @return the endpoint URL in format http://host:port
     */
    public String getEndpointUrl() {
        return "http://" + host + ":" + port;
    }

    /**
     * Creates a new builder for SAFeAgentCard.
     *
     * @return a builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for SAFeAgentCard.
     */
    public static class Builder {
        private String agentId;
        private String name;
        private SAFeAgentRole role;
        private List<AgentCapability> capabilities = List.of();
        private String host = "localhost";
        private int port = 8080;
        private List<String> ceremonies = List.of();
        private Map<String, Object> metadata = Map.of();
        private Instant registeredAt = Instant.now();
        private String version = "1.0.0";

        /**
         * Sets the agent ID.
         *
         * @param agentId the unique identifier
         * @return this builder
         */
        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        /**
         * Sets the agent name.
         *
         * @param name the display name
         * @return this builder
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Sets the agent role.
         *
         * @param role the SAFe role
         * @return this builder
         */
        public Builder role(SAFeAgentRole role) {
            this.role = role;
            return this;
        }

        /**
         * Sets the agent capabilities.
         *
         * @param capabilities list of capabilities
         * @return this builder
         */
        public Builder capabilities(List<AgentCapability> capabilities) {
            this.capabilities = Objects.requireNonNull(capabilities, "capabilities cannot be null");
            return this;
        }

        /**
         * Sets the agent host.
         *
         * @param host the hostname or IP address
         * @return this builder
         */
        public Builder host(String host) {
            this.host = Objects.requireNonNull(host, "host cannot be null");
            return this;
        }

        /**
         * Sets the agent port.
         *
         * @param port the port number (1-65535)
         * @return this builder
         */
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /**
         * Sets the ceremony types this agent participates in.
         *
         * @param ceremonies list of ceremony type identifiers
         * @return this builder
         */
        public Builder ceremonies(List<String> ceremonies) {
            this.ceremonies = Objects.requireNonNull(ceremonies, "ceremonies cannot be null");
            return this;
        }

        /**
         * Sets additional metadata.
         *
         * @param metadata metadata map
         * @return this builder
         */
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = Objects.requireNonNull(metadata, "metadata cannot be null");
            return this;
        }

        /**
         * Sets the registration timestamp.
         *
         * @param registeredAt the registration time
         * @return this builder
         */
        public Builder registeredAt(Instant registeredAt) {
            this.registeredAt = Objects.requireNonNull(registeredAt, "registeredAt cannot be null");
            return this;
        }

        /**
         * Sets the protocol version.
         *
         * @param version the version string
         * @return this builder
         */
        public Builder version(String version) {
            this.version = Objects.requireNonNull(version, "version cannot be null");
            return this;
        }

        /**
         * Builds the SAFeAgentCard.
         *
         * @return a new SAFeAgentCard
         * @throws IllegalStateException if required fields are not set
         */
        public SAFeAgentCard build() {
            if (agentId == null) {
                throw new IllegalStateException("agentId is required");
            }
            if (name == null) {
                throw new IllegalStateException("name is required");
            }
            if (role == null) {
                throw new IllegalStateException("role is required");
            }

            return new SAFeAgentCard(
                agentId, name, role, capabilities, host, port,
                ceremonies, metadata, registeredAt, version
            );
        }
    }
}
