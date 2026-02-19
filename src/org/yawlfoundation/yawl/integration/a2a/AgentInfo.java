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

package org.yawlfoundation.yawl.integration.a2a;

import java.util.List;
import java.util.Map;

/**
 * Information about an autonomous agent in the YAWL system.
 *
 * <p>Provides agent identity, capabilities, and network endpoint for
 * agent-to-agent communication and work item handoff.</p>
 *
 * @since YAWL 6.0
 */
public class AgentInfo {

    private final String id;
    private final String name;
    private final List<String> capabilities;
    private final String host;
    private final int port;
    private final Map<String, Object> metadata;

    /**
     * Creates a new agent info.
     *
     * @param id the unique identifier for this agent
     * @param name the display name of this agent
     * @param capabilities the list of capabilities this agent has
     * @param host the host where this agent is running
     * @param port the port where this agent can be reached
     */
    public AgentInfo(String id, String name, List<String> capabilities,
                    String host, int port) {
        this(id, name, capabilities, host, port, Map.of());
    }

    /**
     * Creates a new agent info with metadata.
     *
     * @param id the unique identifier for this agent
     * @param name the display name of this agent
     * @param capabilities the list of capabilities this agent has
     * @param host the host where this agent is running
     * @param port the port where this agent can be reached
     * @param metadata additional metadata about the agent
     */
    public AgentInfo(String id, String name, List<String> capabilities,
                    String host, int port, Map<String, Object> metadata) {
        this.id = id;
        this.name = name;
        this.capabilities = capabilities;
        this.host = host;
        this.port = port;
        this.metadata = metadata;
    }

    /**
     * Gets the unique identifier for this agent.
     *
     * @return the agent ID
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the display name of this agent.
     *
     * @return the agent name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the capabilities of this agent.
     *
     * @return list of capabilities
     */
    public List<String> getCapabilities() {
        return capabilities;
    }

    /**
     * Gets the host where this agent is running.
     *
     * @return the host
     */
    public String getHost() {
        return host;
    }

    /**
     * Gets the port where this agent can be reached.
     *
     * @return the port
     */
    public int getPort() {
        return port;
    }

    /**
     * Gets additional metadata about this agent.
     *
     * @return metadata map
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }

    /**
     * Gets the endpoint URL for this agent.
     *
     * @return the endpoint URL
     */
    public String getEndpointUrl() {
        return "http://" + host + ":" + port;
    }

    @Override
    public String toString() {
        return "AgentInfo{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", capabilities=" + capabilities +
                ", host='" + host + '\'' +
                ", port=" + port +
                '}';
    }
}