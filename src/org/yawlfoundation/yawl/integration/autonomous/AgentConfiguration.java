/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.autonomous;

import org.yawlfoundation.yawl.integration.autonomous.strategies.DecisionReasoner;
import org.yawlfoundation.yawl.integration.autonomous.strategies.DiscoveryStrategy;
import org.yawlfoundation.yawl.integration.autonomous.strategies.EligibilityReasoner;

/**
 * Configuration for generic autonomous agents.
 *
 * Encapsulates all dependencies and settings required to construct
 * a GenericPartyAgent. Uses builder pattern for flexible configuration.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class AgentConfiguration {

    private final AgentCapability capability;
    private final String engineUrl;
    private final String username;
    private final String password;
    private final int port;
    private final long pollIntervalMs;
    private final String version;
    private final DiscoveryStrategy discoveryStrategy;
    private final EligibilityReasoner eligibilityReasoner;
    private final DecisionReasoner decisionReasoner;

    private AgentConfiguration(Builder builder) {
        this.capability = builder.capability;
        this.engineUrl = builder.engineUrl;
        this.username = builder.username;
        this.password = builder.password;
        this.port = builder.port;
        this.pollIntervalMs = builder.pollIntervalMs;
        this.version = builder.version;
        this.discoveryStrategy = builder.discoveryStrategy;
        this.eligibilityReasoner = builder.eligibilityReasoner;
        this.decisionReasoner = builder.decisionReasoner;
    }

    public AgentCapability getCapability() {
        return capability;
    }

    public String getEngineUrl() {
        return engineUrl;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public int getPort() {
        return port;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public String getVersion() {
        return version;
    }

    public String getAgentName() {
        return capability.getDomainName();
    }

    public DiscoveryStrategy getDiscoveryStrategy() {
        return discoveryStrategy;
    }

    public EligibilityReasoner getEligibilityReasoner() {
        return eligibilityReasoner;
    }

    public DecisionReasoner getDecisionReasoner() {
        return decisionReasoner;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private AgentCapability capability;
        private String engineUrl;
        private String username;
        private String password;
        private int port = 8091;
        private long pollIntervalMs = 3000;
        private String version = "5.2.0";
        private DiscoveryStrategy discoveryStrategy;
        private EligibilityReasoner eligibilityReasoner;
        private DecisionReasoner decisionReasoner;

        private Builder() {
        }

        public Builder capability(AgentCapability capability) {
            this.capability = capability;
            return this;
        }

        public Builder engineUrl(String engineUrl) {
            this.engineUrl = engineUrl;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder pollIntervalMs(long pollIntervalMs) {
            this.pollIntervalMs = pollIntervalMs;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
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

        public AgentConfiguration build() {
            if (capability == null) {
                throw new IllegalStateException("capability is required");
            }
            if (engineUrl == null || engineUrl.isEmpty()) {
                throw new IllegalStateException("engineUrl is required");
            }
            if (username == null || password == null) {
                throw new IllegalStateException("username and password are required");
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
