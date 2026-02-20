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
 * Abstract base class for work item discovery strategies by autonomous agents.
 *
 * <p>Subclasses must implement {@link #discoverWorkItems} to query the YAWL engine
 * for available work items that match agent capabilities. Discovery typically involves:
 * <ol>
 *   <li>Calling Interface B to get available work items</li>
 *   <li>Filtering items based on agent eligibility criteria</li>
 *   <li>Handling session expiry and authentication failures</li>
 *   <li>Applying any load-balancing or priority-based filtering</li>
 * </ol></p>
 *
 * @since YAWL 6.0
 */
public abstract class DiscoveryStrategy {

    /**
     * Discovers work items that this agent can handle.
     *
     * <p>Subclasses must implement this method to query the engine for work items
     * and return those eligible for the agent to process.</p>
     *
     * @param client the Interface B client for engine communication
     * @param sessionHandle the session handle for authentication
     * @return list of available and eligible work items (non-null, possibly empty)
     * @throws IOException if communication with the engine fails
     * @throws IllegalArgumentException if client or sessionHandle is null/invalid
     */
    public abstract List<WorkItemRecord> discoverWorkItems(
        InterfaceB_EnvironmentBasedClient client,
        String sessionHandle) throws IOException;

    /**
     * Gets the maximum number of work items to discover per poll.
     *
     * <p>May be overridden to limit discovery batch size. Default implementation
     * returns -1 (unlimited).</p>
     *
     * @return maximum item count, or -1 for unlimited
     */
    public int getMaxItemsPerDiscovery() {
        return -1;
    }

    /**
     * Gets the polling interval in milliseconds.
     *
     * <p>May be overridden to customize how frequently discovery is performed.
     * Default implementation returns 5000 (5 seconds).</p>
     *
     * @return polling interval in milliseconds
     */
    public long getPollingIntervalMs() {
        return 5000;
    }
}