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
 * Polling-based discovery strategy for YAWL work items.
 *
 * Queries InterfaceB for all live work items. This is extracted from
 * the original PartyAgent implementation and made configurable.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class PollingDiscoveryStrategy implements DiscoveryStrategy {

    /**
     * Create a polling discovery strategy.
     */
    public PollingDiscoveryStrategy() {
    }

    @Override
    public List<WorkItemRecord> discoverWorkItems(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            String sessionHandle) throws IOException {

        if (interfaceBClient == null) {
            throw new IllegalArgumentException("interfaceBClient is required");
        }
        if (sessionHandle == null || sessionHandle.isEmpty()) {
            throw new IllegalArgumentException("sessionHandle is required");
        }

        List<WorkItemRecord> items = interfaceBClient.getCompleteListOfLiveWorkItems(sessionHandle);

        if (items == null) {
            throw new IOException("Failed to retrieve work items from YAWL engine (null response)");
        }

        return items;
    }
}
