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
 * Event-driven discovery strategy for YAWL work items.
 *
 * <p>This strategy will subscribe to engine events (InterfaceE) to receive
 * notifications when work items become available, eliminating the need for
 * polling. This is a future enhancement and is not yet implemented.</p>
 *
 * <p>When implemented, this strategy will:
 * <ul>
 *   <li>Register as an InterfaceE observer with the YAWL engine</li>
 *   <li>Receive event notifications when work items are enabled</li>
 *   <li>Maintain a queue of available work items</li>
 *   <li>Return queued items from discoverWorkItems()</li>
 * </ul>
 * </p>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class EventDrivenDiscoveryStrategy implements DiscoveryStrategy {

    /**
     * Create event-driven discovery strategy.
     * Currently throws UnsupportedOperationException as this is a future feature.
     */
    public EventDrivenDiscoveryStrategy() {
        throw new UnsupportedOperationException(
            "Event-driven discovery is not yet implemented. " +
            "This feature requires InterfaceE observer registration and event handling. " +
            "Use PollingDiscoveryStrategy for now.");
    }

    /**
     * Discover work items via event subscription.
     *
     * @param interfaceBClient the InterfaceB client for YAWL operations
     * @param sessionHandle the authenticated session handle
     * @return list of discovered work items from event queue
     * @throws UnsupportedOperationException always, as this is not yet implemented
     */
    @Override
    public List<WorkItemRecord> discoverWorkItems(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            String sessionHandle) throws IOException {

        throw new UnsupportedOperationException(
            "Event-driven discovery is not yet implemented. " +
            "This method will return work items from an event-driven queue once implemented. " +
            "Use PollingDiscoveryStrategy for production deployments.");
    }
}
