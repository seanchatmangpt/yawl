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

package org.yawlfoundation.yawl.integration.autonomous.strategies;

import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;

import java.io.IOException;
import java.util.List;

/**
 * Strategy for discovering work items from the YAWL engine.
 *
 * <p>Implementations can use different mechanisms:
 * <ul>
 *   <li>Polling: Query InterfaceB at regular intervals</li>
 *   <li>Event-driven: Subscribe to engine events (future)</li>
 *   <li>Hybrid: Combination of polling and notifications</li>
 * </ul>
 * </p>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public interface DiscoveryStrategy {

    /**
     * Discover available work items from the YAWL engine.
     *
     * <p>This method is called periodically by the agent's discovery loop.
     * Implementations should query the engine and return work items that
     * match the strategy's criteria (e.g., all live work items, specific
     * case IDs, filtered by task name).</p>
     *
     * @param interfaceBClient the InterfaceB client for YAWL operations
     * @param sessionHandle the authenticated session handle
     * @return list of discovered work items, or empty list if none available
     * @throws IOException if engine communication fails
     */
    List<WorkItemRecord> discoverWorkItems(
        InterfaceB_EnvironmentBasedClient interfaceBClient,
        String sessionHandle) throws IOException;
}
