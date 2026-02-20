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

import org.yawlfoundation.yawl.integration.autonomous.strategies.DecisionReasoner;
import org.yawlfoundation.yawl.integration.autonomous.strategies.DiscoveryStrategy;
import org.yawlfoundation.yawl.integration.autonomous.strategies.EligibilityReasoner;

/**
 * Configuration for a generic autonomous agent, constructed via the builder pattern.
 *
 * <p>Provides all dependencies needed for agent operation. Required fields are validated
 * at build time, and sensible defaults are provided for optional settings.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * AgentConfiguration config = AgentConfiguration.builder()
 *     .capability(new AgentCapability("Ordering", "procurement, purchase orders"))
 *     .engineUrl("http://localhost:8080/yawl")
 *     .username("admin")
 *     .password("YAWL")
 *     .discoveryStrategy((client, session) -> client.getCompleteListOfLiveWorkItems(session))
 *     .eligibilityReasoner(workItem -> true)
 *     .decisionReasoner(workItem -> "<output/>")
 *     .build();
 * }</pre>
 *
 * @since YAWL 6.0
 */
public class AgentConfiguration {

    private static final int DEFAULT_PORT = 8091;
    private static final long DEFAULT_POLL_INTERVAL_MS = 3000L;
    private static final String DEFAULT_VERSION = "5.2.0";

    private final AgentCapability capability;
    private final String engineUrl;
    private final String username;
    private final String password;
    private final DiscoveryStrategy discoveryStrategy;
    private final EligibilityReasoner eligibilityReasoner;
    private final DecisionReasoner decisionReasoner;
    private final int port;
    private final String version;
    private final long pollIntervalMs;

    private AgentConfiguration(Builder builder) {
        this.capability = builder.capability;
        this.engineUrl = builder.engineUrl;
        this.username = builder.username;
        this.password = builder.password;
        this.discoveryStrategy = builder.discoveryStrategy;
        this.eligibilityReasoner = builder.eligibilityReasoner;
        this.decisionReasoner = builder.decisionReasoner;
        this.port = builder.port;
        this.version = builder.version;
        this.pollIntervalMs = builder.pollIntervalMs;
    }

    /**
     * Creates a new builder for AgentConfiguration.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

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
     * Gets the strategy for discovering work items.
     *
     * @return the discovery strategy
     */
    public DiscoveryStrategy getDiscoveryStrategy() {
        return discoveryStrategy;
    }

    /**
     * Gets the reasoner for evaluating work item eligibility.
     *
     * @return the eligibility reasoner
     */
    public EligibilityReasoner getEligibilityReasoner() {
        return eligibilityReasoner;
    }

    /**
     * Gets the reasoner for producing work item output.
     *
     * @return the decision reasoner
     */
    public DecisionReasoner getDecisionReasoner() {
        return decisionReasoner;
    }

    /**
     * Gets the HTTP port for this agent.
     *
     * @return the port number
     */
    public int getPort() {
        return port;
    }

    /**
     * Gets the agent version string.
     *
     * @return the version
     */
    public String getVersion() {
        return version;
    }

    /**
     * Gets the polling interval in milliseconds.
     *
     * @return the poll interval in ms
     */
    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    /**
     * Builder for constructing AgentConfiguration instances with validation.
     *
     * <p>Required fields: capability, engineUrl, username, password,
     * discoveryStrategy, eligibilityReasoner, decisionReasoner.</p>
     *
     * <p>Optional fields with defaults: port (8091), pollIntervalMs (3000),
     * version ("5.2.0").</p>
     */
    public static final class Builder {

        private AgentCapability capability;
        private String engineUrl;
        private String username;
        private String password;
        private DiscoveryStrategy discoveryStrategy;
        private EligibilityReasoner eligibilityReasoner;
        private DecisionReasoner decisionReasoner;
        private int port = DEFAULT_PORT;
        private String version = DEFAULT_VERSION;
        private long pollIntervalMs = DEFAULT_POLL_INTERVAL_MS;

        private Builder() {}

        /**
         * Sets the agent capability (required).
         *
         * @param capability the agent capability
         * @return this builder
         */
        public Builder capability(AgentCapability capability) {
            this.capability = capability;
            return this;
        }

        /**
         * Sets the YAWL engine URL (required).
         *
         * @param engineUrl the engine URL
         * @return this builder
         */
        public Builder engineUrl(String engineUrl) {
            this.engineUrl = engineUrl;
            return this;
        }

        /**
         * Sets the engine authentication username (required).
         *
         * @param username the username
         * @return this builder
         */
        public Builder username(String username) {
            this.username = username;
            return this;
        }

        /**
         * Sets the engine authentication password (required).
         *
         * @param password the password
         * @return this builder
         */
        public Builder password(String password) {
            this.password = password;
            return this;
        }

        /**
         * Sets the work item discovery strategy (required).
         *
         * @param discoveryStrategy the discovery strategy
         * @return this builder
         */
        public Builder discoveryStrategy(DiscoveryStrategy discoveryStrategy) {
            this.discoveryStrategy = discoveryStrategy;
            return this;
        }

        /**
         * Sets the eligibility reasoner (required).
         *
         * @param eligibilityReasoner the eligibility reasoner
         * @return this builder
         */
        public Builder eligibilityReasoner(EligibilityReasoner eligibilityReasoner) {
            this.eligibilityReasoner = eligibilityReasoner;
            return this;
        }

        /**
         * Sets the decision reasoner (required).
         *
         * @param decisionReasoner the decision reasoner
         * @return this builder
         */
        public Builder decisionReasoner(DecisionReasoner decisionReasoner) {
            this.decisionReasoner = decisionReasoner;
            return this;
        }

        /**
         * Sets the HTTP port for this agent (default: 8091).
         *
         * @param port the port number
         * @return this builder
         */
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /**
         * Sets the agent version (default: "5.2.0").
         *
         * @param version the version string
         * @return this builder
         */
        public Builder version(String version) {
            this.version = version;
            return this;
        }

        /**
         * Sets the polling interval in milliseconds (default: 3000).
         *
         * @param pollIntervalMs the poll interval in ms
         * @return this builder
         */
        public Builder pollIntervalMs(long pollIntervalMs) {
            this.pollIntervalMs = pollIntervalMs;
            return this;
        }

        /**
         * Builds the AgentConfiguration, validating all required fields.
         *
         * @return the constructed AgentConfiguration
         * @throws IllegalStateException if any required field is missing or invalid
         */
        public AgentConfiguration build() {
            if (capability == null) {
                throw new IllegalStateException("capability is required");
            }
            if (engineUrl == null || engineUrl.isBlank()) {
                throw new IllegalStateException("engineUrl is required");
            }
            if (username == null || username.isBlank()) {
                throw new IllegalStateException("username is required");
            }
            if (password == null || password.isBlank()) {
                throw new IllegalStateException("password is required");
            }
            if (discoveryStrategy == null) {
                throw new IllegalStateException("discoveryStrategy is required");
            }
            if (eligibilityReasoner == null) {
                throw new IllegalStateException("eligibilityReasoner is required");
            }
            if (decisionReasoner == null) {
                throw new IllegalStateException("decisionReasoner is required");
            }
            if (pollIntervalMs <= 0) {
                throw new IllegalStateException("pollIntervalMs must be positive");
            }
            return new AgentConfiguration(this);
        }
    }
}
