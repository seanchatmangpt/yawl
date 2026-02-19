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

import org.yawlfoundation.yawl.integration.autonomous.registry.AgentInfo;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Context information for an autonomous agent in the YAWL system.
 *
 * <p>Provides agent identity, security context, and operational state.
 * Used by the handoff service to coordinate agent-to-agent communication
 * and workflow item transfers.</p>
 *
 * @since YAWL 6.0
 */
public class AgentContext {

    private final String agentId;
    private final String agentName;
    private final AgentCapability capability;
    private final String principalName;
    private final Instant registrationTime;
    private final Map<String, Object> properties;
    private final String endpointUrl;

    /**
     * Creates a new agent context.
     *
     * @param agentId the unique identifier for this agent
     * @param agentName the display name of this agent
     * @param capability the capability describing what this agent can handle
     * @param principalName the principal name for authentication (can be null)
     * @param endpointUrl the URL where this agent can be reached
     */
    public AgentContext(String agentId, String agentName, AgentCapability capability,
                       String principalName, String endpointUrl) {
        this.agentId = agentId;
        this.agentName = agentName;
        this.capability = capability;
        this.principalName = principalName;
        this.endpointUrl = endpointUrl;
        this.registrationTime = Instant.now();
        this.properties = new ConcurrentHashMap<>();
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
     * Gets the capability describing what this agent can handle.
     *
     * @return the agent capability
     */
    public AgentCapability getCapability() {
        return capability;
    }

    /**
     * Gets the principal name for authentication.
     *
     * @return the principal name
     */
    public String getPrincipalName() {
        return principalName;
    }

    /**
     * Gets the URL where this agent can be reached.
     *
     * @return the endpoint URL
     */
    public String getEndpointUrl() {
        return endpointUrl;
    }

    /**
     * Gets when this agent was registered.
     *
     * @return the registration time
     */
    public Instant getRegistrationTime() {
        return registrationTime;
    }

    /**
     * Gets a property value by name.
     *
     * @param key the property name
     * @return the property value, or null if not set
     */
    public Object getProperty(String key) {
        return properties.get(key);
    }

    /**
     * Sets a property value.
     *
     * @param key the property name
     * @param value the property value
     */
    public void setProperty(String key, Object value) {
        properties.put(key, value);
    }

    /**
     * Removes a property by name.
     *
     * @param key the property name
     * @return the removed value, or null if not set
     */
    public Object removeProperty(String key) {
        return properties.remove(key);
    }

    /**
     * Gets all properties as a map.
     *
     * @return the properties map
     */
    public Map<String, Object> getProperties() {
        return new ConcurrentHashMap<>(properties);
    }
}