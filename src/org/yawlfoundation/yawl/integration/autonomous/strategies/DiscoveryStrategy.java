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
import java.util.Collections;
import java.util.List;

/**
 * Strategy for discovering work items by autonomous agents.
 *
 * <p>This is a stub implementation that returns no work items.
 * In a real implementation, this would query the YAWL engine
 * for work items that match the agent's capabilities.</p>
 *
 * @since YAWL 6.0
 */
public class DiscoveryStrategy {

    /**
     * Discovers work items that this agent can handle.
     *
     * @param client the Interface B client for engine communication
     * @param sessionHandle the session handle for authentication
     * @return list of available work items, empty if none
     * @throws IOException if communication with engine fails
     */
    public List<WorkItemRecord> discoverWorkItems(InterfaceB_EnvironmentBasedClient client,
                                                 String sessionHandle) throws IOException {
        // Stub implementation - return no work items
        // In a real implementation, this would:
        // 1. Query the engine for enabled work items
        // 2. Filter based on agent capabilities
        // 3. Return matching work items
        return Collections.emptyList();
    }
}