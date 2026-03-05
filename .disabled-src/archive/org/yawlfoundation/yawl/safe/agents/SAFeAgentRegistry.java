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

package org.yawlfoundation.yawl.safe.agents;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.integration.autonomous.GenericPartyAgent;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry and factory for SAFe participant agents.
 *
 * <p>Manages agent lifecycle and provides factory methods for creating
 * all SAFe role agents with standard configuration defaults.
 *
 * <p>Supports:
 * <ul>
 *   <li>Creating individual agents by role (ProductOwner, ScrumMaster, etc.)</li>
 *   <li>Creating complete SAFe team (all 5 agents)</li>
 *   <li>Managing agent registration and discovery</li>
 *   <li>Lifecycle management (start, stop, health check)</li>
 * </ul>
 *
 * @since YAWL 6.0
 */
public final class SAFeAgentRegistry {

    private static final Logger logger = LogManager.getLogger(SAFeAgentRegistry.class);

    private final Map<String, GenericPartyAgent> agents = new HashMap<>();
    private final String engineUrl;
    private final String username;
    private final String password;
    private final int basePort;

    /**
     * Create a SAFe agent registry.
     *
     * @param engineUrl YAWL engine URL
     * @param username engine username
     * @param password engine password
     * @param basePort base HTTP port (agents use basePort + offset)
     */
    public SAFeAgentRegistry(String engineUrl, String username, String password, int basePort) {
        this.engineUrl = engineUrl;
        this.username = username;
        this.password = password;
        this.basePort = basePort;
    }

    /**
     * Create and register a Product Owner agent.
     *
     * @return the created agent
     * @throws IOException if engine connection fails
     */
    public ProductOwnerAgent createProductOwnerAgent() throws IOException {
        ProductOwnerAgent agent = ProductOwnerAgent.create(
            engineUrl, username, password, basePort);
        agents.put("ProductOwner", agent);
        logger.info("Registered ProductOwnerAgent on port {}", basePort);
        return agent;
    }

    /**
     * Create and register a Scrum Master agent.
     *
     * @return the created agent
     * @throws IOException if engine connection fails
     */
    public ScrumMasterAgent createScrumMasterAgent() throws IOException {
        ScrumMasterAgent agent = ScrumMasterAgent.create(
            engineUrl, username, password, basePort + 1);
        agents.put("ScrumMaster", agent);
        logger.info("Registered ScrumMasterAgent on port {}", basePort + 1);
        return agent;
    }

    /**
     * Create and register a Developer agent.
     *
     * @return the created agent
     * @throws IOException if engine connection fails
     */
    public DeveloperAgent createDeveloperAgent() throws IOException {
        DeveloperAgent agent = DeveloperAgent.create(
            engineUrl, username, password, basePort + 2);
        agents.put("Developer", agent);
        logger.info("Registered DeveloperAgent on port {}", basePort + 2);
        return agent;
    }

    /**
     * Create and register a System Architect agent.
     *
     * @return the created agent
     * @throws IOException if engine connection fails
     */
    public SystemArchitectAgent createSystemArchitectAgent() throws IOException {
        SystemArchitectAgent agent = SystemArchitectAgent.create(
            engineUrl, username, password, basePort + 3);
        agents.put("SystemArchitect", agent);
        logger.info("Registered SystemArchitectAgent on port {}", basePort + 3);
        return agent;
    }

    /**
     * Create and register a Release Train Engineer agent.
     *
     * @return the created agent
     * @throws IOException if engine connection fails
     */
    public ReleaseTrainEngineerAgent createReleaseTrainEngineerAgent() throws IOException {
        ReleaseTrainEngineerAgent agent = ReleaseTrainEngineerAgent.create(
            engineUrl, username, password, basePort + 4);
        agents.put("ReleaseTrainEngineer", agent);
        logger.info("Registered ReleaseTrainEngineerAgent on port {}", basePort + 4);
        return agent;
    }

    /**
     * Create and register all SAFe agents for a complete team.
     *
     * @return list of all created agents
     * @throws IOException if any agent creation fails
     */
    public List<GenericPartyAgent> createCompleteTeam() throws IOException {
        try {
            ProductOwnerAgent po = createProductOwnerAgent();
            ScrumMasterAgent sm = createScrumMasterAgent();
            DeveloperAgent dev = createDeveloperAgent();
            SystemArchitectAgent sa = createSystemArchitectAgent();
            ReleaseTrainEngineerAgent rte = createReleaseTrainEngineerAgent();

            logger.info("Created complete SAFe team: 5 agents registered");
            return List.of(po, sm, dev, sa, rte);
        } catch (IOException e) {
            logger.error("Failed to create complete SAFe team: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Get an agent by role name.
     *
     * @param roleName the role (ProductOwner, ScrumMaster, Developer, SystemArchitect, ReleaseTrainEngineer)
     * @return the agent or null if not registered
     */
    public GenericPartyAgent getAgent(String roleName) {
        return agents.get(roleName);
    }

    /**
     * Get all registered agents.
     *
     * @return unmodifiable map of role to agent
     */
    public Map<String, GenericPartyAgent> getAllAgents() {
        return Collections.unmodifiableMap(agents);
    }

    /**
     * Start all registered agents.
     *
     * @throws IOException if any agent fails to start
     */
    public void startAll() throws IOException {
        for (Map.Entry<String, GenericPartyAgent> entry : agents.entrySet()) {
            try {
                entry.getValue().start();
                logger.info("Started agent: {}", entry.getKey());
            } catch (IOException e) {
                logger.error("Failed to start agent {}: {}", entry.getKey(), e.getMessage());
                throw e;
            }
        }
    }

    /**
     * Stop all registered agents.
     */
    public void stopAll() {
        for (Map.Entry<String, GenericPartyAgent> entry : agents.entrySet()) {
            try {
                entry.getValue().stop();
                logger.info("Stopped agent: {}", entry.getKey());
            } catch (Exception e) {
                logger.warn("Error stopping agent {}: {}", entry.getKey(), e.getMessage());
            }
        }
    }

    /**
     * Get health status of all agents.
     *
     * @return map of role to health status (true = healthy)
     */
    public Map<String, Boolean> getHealthStatus() {
        Map<String, Boolean> status = new HashMap<>();
        for (Map.Entry<String, GenericPartyAgent> entry : agents.entrySet()) {
            status.put(entry.getKey(), entry.getValue().getLifecycle().isActive());
        }
        return status;
    }

    /**
     * Check if all agents are healthy.
     *
     * @return true if all agents are in active lifecycle state
     */
    public boolean isHealthy() {
        return agents.values().stream()
            .allMatch(agent -> agent.getLifecycle().isActive());
    }

    /**
     * Get agent count.
     *
     * @return number of registered agents
     */
    public int size() {
        return agents.size();
    }

    /**
     * Clear all registered agents.
     */
    public void clear() {
        agents.clear();
        logger.info("Cleared agent registry");
    }
}
