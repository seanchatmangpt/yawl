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

package org.yawlfoundation.yawl.integration.autonomous.strategies;

import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;

import java.io.IOException;
import java.util.List;

/**
 * Functional interface for discovering work items available for processing by autonomous agents.
 *
 * <p>Implementers of this interface are responsible for querying the YAWL engine
 * and filtering work items based on agent capabilities and availability.
 * Implementations should:</p>
 * <ul>
 *   <li>Query the engine for enabled work items using the provided client</li>
 *   <li>Filter results based on agent expertise and current load</li>
 *   <li>Return only work items suitable for agent processing</li>
 * </ul>
 *
 * <p>This is a functional interface and can be implemented using lambda expressions:</p>
 * <pre>{@code
 * DiscoveryStrategy strategy = (client, session) ->
 *     client.getWorkItems(session);
 * }</pre>
 *
 * @since YAWL 6.0
 */
@FunctionalInterface
public interface DiscoveryStrategy {

    /**
     * Discovers work items available for processing by this agent.
     *
     * <p>This method queries the YAWL engine via the provided Interface B client
     * and returns a list of work items that the agent can potentially process.
     * The discovery process typically involves:
     * <ol>
     *   <li>Authenticating with the engine using the provided session handle</li>
     *   <li>Retrieving all enabled work items</li>
     *   <li>Filtering based on agent capabilities, expertise domains, and availability</li>
     *   <li>Returning the filtered list of eligible work items</li>
     * </ol></p>
     *
     * @param client the Interface B client for communicating with the YAWL engine
     * @param sessionHandle the session handle for authentication and authorization
     * @return a list of work items available for processing; empty list if none are available
     * @throws IOException if communication with the engine fails or network issues occur
     */
    List<WorkItemRecord> discoverWorkItems(
            InterfaceB_EnvironmentBasedClient client,
            String sessionHandle
    ) throws IOException;
}
