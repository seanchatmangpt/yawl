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

package org.yawlfoundation.yawl.integration.autonomous;

import org.yawlfoundation.yawl.integration.a2a.YawlA2AClient;
import org.yawlfoundation.yawl.integration.a2a.handoff.HandoffProtocol;
import org.yawlfoundation.yawl.integration.a2a.handoff.HandoffRequestService;
import org.yawlfoundation.yawl.integration.autonomous.registry.AgentRegistryClient;
import org.yawlfoundation.yawl.integration.autonomous.strategies.DecisionReasoner;
import org.yawlfoundation.yawl.integration.autonomous.strategies.DiscoveryStrategy;
import org.yawlfoundation.yawl.integration.autonomous.strategies.EligibilityReasoner;
import org.yawlfoundation.yawl.integration.conflict.ConflictResolver;

/**
 * Configuration for a generic autonomous agent — Java 25 record edition.
 *
 * <p>Provides all dependencies needed for agent operation through constructor injection.
 * This enables testability and flexible composition of agent capabilities.
 *
 * <p>Converted from a plain class to a Java 25 record:
 * <ul>
 *   <li>Immutable by construction — all components are final</li>
 *   <li>Auto-generated equals, hashCode, and toString</li>
 *   <li>Eliminated 125+ lines of boilerplate getters/constructor</li>
 *   <li>Canonical constructor validates required fields</li>
 * </ul>
 *
 * <p>Use {@link Builder} to construct instances with optional fields defaulted.
 *
 * @param id                unique identifier for this agent
 * @param engineUrl         URL of the YAWL engine
 * @param username          username for engine authentication
 * @param password          password for engine authentication
 * @param capability        the agent's domain capability descriptor
 * @param discoveryStrategy strategy for discovering available work items
 * @param eligibilityReasoner reasoner for determining work item eligibility
 * @param decisionReasoner  reasoner for producing output decisions
 * @param registryClient    client for the agent registry
 * @param handoffProtocol   protocol for work item handoff between agents
 * @param handoffService    service for managing handoff requests
 * @param conflictResolver  resolver for conflicting work item assignments
 * @param a2aClient         client for agent-to-agent communication
 * @param partitionConfig   partition configuration for distributed processing
 * @param port              HTTP port for this agent's A2A endpoint
 * @param version           agent version string
 * @param pollIntervalMs    polling interval in milliseconds
 *
 * @since YAWL 6.0
 */
public record AgentConfiguration(
        String id,
        String engineUrl,
        String username,
        String password,
        AgentCapability capability,
        DiscoveryStrategy discoveryStrategy,
        EligibilityReasoner eligibilityReasoner,
        DecisionReasoner decisionReasoner,
        AgentRegistryClient registryClient,
        HandoffProtocol handoffProtocol,
        HandoffRequestService handoffService,
        ConflictResolver conflictResolver,
        YawlA2AClient a2aClient,
        PartitionConfig partitionConfig,
        int port,
        String version,
        long pollIntervalMs) {

    /**
     * Gets the agent's capability description.
     *
     * @return the agent capability
     */
    public AgentCapability getCapability() {
        return capability;
    }

    /**
     * Gets the agent display name, derived from the capability domain name.
     *
     * @return the agent name
     */
    public String getAgentName() {
        return capability.domainName();
    }

    /**
     * Gets the YAWL engine URL.
     *
     * @return the engine URL
     */
    public String getEngineUrl() {
        return engineUrl;
    }

    /**
     * Gets the username for engine authentication.
     *
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * Gets the password for engine authentication.
     *
     * @return the password
     */
    public String getPassword() {
        return password;
    }

    /**
     * Canonical constructor with validation of required fields.
     */
    public AgentConfiguration {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("AgentConfiguration id is required");
        }
        if (engineUrl == null || engineUrl.isBlank()) {
            throw new IllegalArgumentException("AgentConfiguration engineUrl is required");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("AgentConfiguration username is required");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("AgentConfiguration password is required");
        }
        if (capability == null) {
            throw new IllegalArgumentException("AgentConfiguration capability is required");
        }
        if (discoveryStrategy == null) {
            throw new IllegalArgumentException("AgentConfiguration discoveryStrategy is required");
        }
        if (eligibilityReasoner == null) {
            throw new IllegalArgumentException("AgentConfiguration eligibilityReasoner is required");
        }
        if (decisionReasoner == null) {
            throw new IllegalArgumentException("AgentConfiguration decisionReasoner is required");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException(
                "AgentConfiguration port must be in [1, 65535], got: " + port);
        }
        if (pollIntervalMs <= 0) {
            throw new IllegalArgumentException(
                "AgentConfiguration pollIntervalMs must be positive, got: " + pollIntervalMs);
        }
        // partitionConfig defaults to single-agent if null
        if (partitionConfig == null) {
            partitionConfig = PartitionConfig.single();
        }
        if (version == null || version.isBlank()) {
            version = "6.0.0";
        }
    }

    /**
     * Create a builder with the required fields pre-populated.
     *
     * @param id        agent identifier
     * @param engineUrl YAWL engine URL
     * @param username  engine username
     * @param password  engine password
     * @return a new builder
     */
    public static Builder builder(String id, String engineUrl, String username, String password) {
        return new Builder(id, engineUrl, username, password);
    }

    /**
     * Fluent builder for {@link AgentConfiguration}.
     */
    public static final class Builder {

        private final String id;
        private final String engineUrl;
        private final String username;
        private final String password;
        private AgentCapability capability;
        private DiscoveryStrategy discoveryStrategy;
        private EligibilityReasoner eligibilityReasoner;
        private DecisionReasoner decisionReasoner;
        private AgentRegistryClient registryClient;
        private HandoffProtocol handoffProtocol;
        private HandoffRequestService handoffService;
        private ConflictResolver conflictResolver;
        private YawlA2AClient a2aClient;
        private PartitionConfig partitionConfig = PartitionConfig.single();
        private int port = 8082;
        private String version = "6.0.0";
        private long pollIntervalMs = 5000L;

        private Builder(String id, String engineUrl, String username, String password) {
            this.id = id;
            this.engineUrl = engineUrl;
            this.username = username;
            this.password = password;
        }

        public Builder capability(AgentCapability capability) {
            this.capability = capability;
            return this;
        }

        public Builder discoveryStrategy(DiscoveryStrategy discoveryStrategy) {
            this.discoveryStrategy = discoveryStrategy;
            return this;
        }

        public Builder eligibilityReasoner(EligibilityReasoner eligibilityReasoner) {
            this.eligibilityReasoner = eligibilityReasoner;
            return this;
        }

        public Builder decisionReasoner(DecisionReasoner decisionReasoner) {
            this.decisionReasoner = decisionReasoner;
            return this;
        }

        public Builder registryClient(AgentRegistryClient registryClient) {
            this.registryClient = registryClient;
            return this;
        }

        public Builder handoffProtocol(HandoffProtocol handoffProtocol) {
            this.handoffProtocol = handoffProtocol;
            return this;
        }

        public Builder handoffService(HandoffRequestService handoffService) {
            this.handoffService = handoffService;
            return this;
        }

        public Builder conflictResolver(ConflictResolver conflictResolver) {
            this.conflictResolver = conflictResolver;
            return this;
        }

        public Builder a2aClient(YawlA2AClient a2aClient) {
            this.a2aClient = a2aClient;
            return this;
        }

        public Builder partitionConfig(PartitionConfig partitionConfig) {
            this.partitionConfig = partitionConfig;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder pollIntervalMs(long pollIntervalMs) {
            this.pollIntervalMs = pollIntervalMs;
            return this;
        }

        /**
         * Build the immutable {@link AgentConfiguration} record.
         *
         * @return the agent configuration
         * @throws IllegalArgumentException if any required field is missing
         */
        public AgentConfiguration build() {
            return new AgentConfiguration(
                id, engineUrl, username, password,
                capability, discoveryStrategy, eligibilityReasoner, decisionReasoner,
                registryClient, handoffProtocol, handoffService, conflictResolver,
                a2aClient, partitionConfig, port, version, pollIntervalMs);
        }
    }
}
