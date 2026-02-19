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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.autonomous;

import org.yawlfoundation.yawl.integration.a2a.handoff.HandoffProtocol;
import org.yawlfoundation.yawl.integration.a2a.handoff.HandoffRequestService;
import org.yawlfoundation.yawl.integration.a2a.YawlA2AClient;
import org.yawlfoundation.yawl.integration.autonomous.strategies.DecisionReasoner;
import org.yawlfoundation.yawl.integration.autonomous.strategies.DiscoveryStrategy;
import org.yawlfoundation.yawl.integration.autonomous.strategies.EligibilityReasoner;
import org.yawlfoundation.yawl.integration.autonomous.registry.AgentRegistryClient;
import org.yawlfoundation.yawl.integration.conflict.ConflictResolver;
import org.yawlfoundation.yawl.integration.orderfulfillment.AgentCapability;

/**
 * Configuration for a generic autonomous agent.
 *
 * <p>Provides all dependencies needed for agent operation through
 * constructor injection. This enables testability and flexible
 * composition of agent capabilities.</p>
 *
 * @since YAWL 6.0
 */
public class AgentConfiguration {

    private final String id;
    private final String engineUrl;
    private final String username;
    private final String password;
    private final AgentCapability capability;
    private final DiscoveryStrategy discoveryStrategy;
    private final EligibilityReasoner eligibilityReasoner;
    private final DecisionReasoner decisionReasoner;
    private final AgentRegistryClient registryClient;
    private final HandoffProtocol handoffProtocol;
    private final HandoffRequestService handoffService;
    private final ConflictResolver conflictResolver;
    private final YawlA2AClient a2aClient;
    private final PartitionConfig partitionConfig;
    private final int port;
    private final String version;
    private final long pollIntervalMs;

    /**
     * Creates a new agent configuration.
     *
     * @param id the unique identifier for this agent
     * @param engineUrl the URL of the YAWL engine
     * @param username the username for engine authentication
     * @param password the password for engine authentication
     * @param capability the agent's capability
     * @param discoveryStrategy strategy for discovering work items
     * @param eligibilityReasoner reasoner for work item eligibility
     * @param decisionReasoner reasoner for producing output
     * @param registryClient client for agent registry
     * @param handoffProtocol protocol for work item handoff
     * @param handoffService service for managing handoff requests
     * @param conflictResolver resolver for conflicting work items
     * @param a2aClient client for agent-to-agent communication
     * @param partitionConfig partition configuration for distributed processing
     * @param port the HTTP port for this agent
     * @param version the agent version
     * @param pollIntervalMs the polling interval in milliseconds
     */
    public AgentConfiguration(String id, String engineUrl, String username, String password,
                             AgentCapability capability, DiscoveryStrategy discoveryStrategy,
                             EligibilityReasoner eligibilityReasoner, DecisionReasoner decisionReasoner,
                             AgentRegistryClient registryClient, HandoffProtocol handoffProtocol,
                             HandoffRequestService handoffService, ConflictResolver conflictResolver,
                             YawlA2AClient a2aClient, PartitionConfig partitionConfig,
                             int port, String version, long pollIntervalMs) {
        this.id = id;
        this.engineUrl = engineUrl;
        this.username = username;
        this.password = password;
        this.capability = capability;
        this.discoveryStrategy = discoveryStrategy;
        this.eligibilityReasoner = eligibilityReasoner;
        this.decisionReasoner = decisionReasoner;
        this.registryClient = registryClient;
        this.handoffProtocol = handoffProtocol;
        this.handoffService = handoffService;
        this.conflictResolver = conflictResolver;
        this.a2aClient = a2aClient;
        this.partitionConfig = partitionConfig;
        this.port = port;
        this.version = version;
        this.pollIntervalMs = pollIntervalMs;
    }

    // Getters
    public String getId() { return id; }
    public String getEngineUrl() { return engineUrl; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public AgentCapability getCapability() { return capability; }
    public DiscoveryStrategy getDiscoveryStrategy() { return discoveryStrategy; }
    public EligibilityReasoner getEligibilityReasoner() { return eligibilityReasoner; }
    public DecisionReasoner getDecisionReasoner() { return decisionReasoner; }
    public AgentRegistryClient getAgentRegistryClient() { return registryClient; }
    public HandoffProtocol getHandoffProtocol() { return handoffProtocol; }
    public HandoffRequestService getHandoffService() { return handoffService; }
    public ConflictResolver getConflictResolver() { return conflictResolver; }
    public YawlA2AClient getA2AClient() { return a2aClient; }
    public PartitionConfig getPartitionConfig() { return partitionConfig; }
    public int getPort() { return port; }
    public String getVersion() { return version; }
    public long getPollIntervalMs() { return pollIntervalMs; }
}