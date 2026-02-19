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

/**
 * Context for an autonomous agent, providing identity and configuration.
 *
 * <p>Agent context contains all the information needed to identify an agent
 * and its operational parameters within the YAWL workflow system.</p>
 *
 * @since YAWL 6.0
 */
public class AgentContext {

    private final String agentId;
    private final String agentName;
    private final String engineUrl;
    private final String sessionHandle;

    /**
     * Creates a new agent context.
     *
     * @param agentId the unique identifier for this agent
     * @param agentName the display name of this agent
     * @param engineUrl the URL of the YAWL engine
     * @param sessionHandle the current session handle for engine communication
     */
    public AgentContext(String agentId, String agentName, String engineUrl, String sessionHandle) {
        this.agentId = agentId;
        this.agentName = agentName;
        this.engineUrl = engineUrl;
        this.sessionHandle = sessionHandle;
    }

    /**
     * Creates an agent context from environment variables.
     *
     * @return the agent context from environment
     */
    public static AgentContext fromEnvironment() {
        String agentId = System.getenv().getOrDefault("AGENT_ID", "agent-unknown");
        String agentName = System.getenv().getOrDefault("AGENT_NAME", "Unknown Agent");
        String engineUrl = System.getenv().getOrDefault("YAWL_ENGINE_URL", "http://localhost:8080/yawl");
        String sessionHandle = System.getenv().getOrDefault("YAWL_SESSION_HANDLE", "");
        return new AgentContext(agentId, agentName, engineUrl, sessionHandle);
    }

    /**
     * Gets the unique identifier for this agent.
     *
     * @return the agent ID
     */
    public String getAgentId() {
        return agentId;
    }

    /**
     * Gets the display name of this agent.
     *
     * @return the agent name
     */
    public String getAgentName() {
        return agentName;
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
     * Gets the session handle for engine communication.
     *
     * @return the session handle
     */
    public String getSessionHandle() {
        return sessionHandle;
    }

    @Override
    public String toString() {
        return "AgentContext{" +
                "agentId='" + agentId + '\'' +
                ", agentName='" + agentName + '\'' +
                ", engineUrl='" + engineUrl + '\'' +
                '}';
    }
}
