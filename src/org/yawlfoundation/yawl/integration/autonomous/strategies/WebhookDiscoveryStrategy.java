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
 * Webhook-based discovery strategy for YAWL work items.
 *
 * <p>This strategy will expose HTTP endpoints to receive webhook notifications
 * from the YAWL engine or external systems when work items become available.
 * This is a future enhancement and is not yet implemented.</p>
 *
 * <p>When implemented, this strategy will:
 * <ul>
 *   <li>Start an HTTP server with webhook endpoints</li>
 *   <li>Accept POST notifications with work item details</li>
 *   <li>Queue received work item IDs for processing</li>
 *   <li>Fetch full work item details from InterfaceB when needed</li>
 *   <li>Support authentication and validation of webhook sources</li>
 * </ul>
 * </p>
 *
 * <p>Example webhook payload (JSON):
 * <pre>
 * {
 *   "eventType": "workItemEnabled",
 *   "workItemId": "1.2:3",
 *   "caseId": "1.2",
 *   "taskName": "Approve_Purchase_Order",
 *   "timestamp": "2026-02-16T12:00:00Z"
 * }
 * </pre>
 * </p>
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public final class WebhookDiscoveryStrategy implements DiscoveryStrategy {

    /**
     * Create webhook-based discovery strategy.
     * Currently throws UnsupportedOperationException as this is a future feature.
     */
    public WebhookDiscoveryStrategy() {
        throw new UnsupportedOperationException(
            "Webhook-based discovery is not yet implemented. " +
            "This feature requires HTTP server setup, webhook endpoint handling, " +
            "and work item queue management. Use PollingDiscoveryStrategy for now.");
    }

    /**
     * Discover work items via webhook notifications.
     *
     * <p>This method will return work items from a queue populated by incoming
     * webhook notifications. The queue is maintained internally and updated as
     * webhooks are received.</p>
     *
     * @param interfaceBClient the InterfaceB client for YAWL operations
     * @param sessionHandle the authenticated session handle
     * @return list of discovered work items from webhook queue
     * @throws UnsupportedOperationException always, as this is not yet implemented
     */
    @Override
    public List<WorkItemRecord> discoverWorkItems(
            InterfaceB_EnvironmentBasedClient interfaceBClient,
            String sessionHandle) throws IOException {

        throw new UnsupportedOperationException(
            "Webhook-based discovery is not yet implemented. " +
            "This method will return work items queued from webhook notifications once implemented. " +
            "Use PollingDiscoveryStrategy for production deployments.");
    }
}
