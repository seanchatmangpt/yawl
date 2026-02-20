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
 * <p>Work item discovery is not yet implemented. The method throws
 * {@link UnsupportedOperationException} to prevent the agent loop from
 * silently processing zero items. A concrete subclass must query the
 * YAWL engine for work items that match the agent's capabilities.</p>
 *
 * @since YAWL 6.0
 */
public class DiscoveryStrategy {

    /**
     * Discovers work items that this agent can handle.
     *
     * @param client the Interface B client for engine communication
     * @param sessionHandle the session handle for authentication
     * @return list of available work items
     * @throws UnsupportedOperationException always — not yet implemented
     * @throws IOException if communication with engine fails
     */
    public List<WorkItemRecord> discoverWorkItems(InterfaceB_EnvironmentBasedClient client,
                                                 String sessionHandle) throws IOException {
        throw new UnsupportedOperationException(
            "discoverWorkItems() is not implemented. Work item discovery requires:\n" +
            "  1. Call client.getAvailableWorkItems(sessionHandle) via Interface B\n" +
            "  2. Deserialise the XML response into List<WorkItemRecord>\n" +
            "  3. Filter items against agent capabilities (see EligibilityReasoner)\n" +
            "  4. Handle session expiry by re-authenticating and retrying once\n" +
            "Create a concrete subclass of DiscoveryStrategy and inject it into the agent."
        );
    }
}