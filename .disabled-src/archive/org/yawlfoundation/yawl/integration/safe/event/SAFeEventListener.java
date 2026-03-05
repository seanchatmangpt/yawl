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
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.safe.event;

import org.yawlfoundation.yawl.integration.safe.agent.SAFeAgent;

/**
 * Listener for SAFe events published via {@link SAFeEventBus}.
 *
 * <p>Implementations handle all types of SAFe events:
 * <ul>
 *   <li>Ceremony events (PI Planning started, Sprint Review completed, etc.)</li>
 *   <li>Work item events (task started, completed, blocked, escalated)</li>
 *   <li>Dependency events (dependency declared, resolved, cycle detected)</li>
 *   <li>Risk events (risk identified, mitigated)</li>
 *   <li>Decision events (approval, rejection, escalation)</li>
 * </ul>
 *
 * <p>Implementations should be:
 * <ul>
 *   <li><strong>Fast</strong>: Event handlers execute on virtual threads and should return quickly</li>
 *   <li><strong>Thread-safe</strong>: Handlers may be invoked concurrently from multiple virtual threads</li>
 *   <li><strong>Non-blocking</strong>: Avoid synchronous I/O or expensive operations in handlers</li>
 *   <li><strong>Idempotent</strong>: Same event may be delivered multiple times (at-least-once semantics)</li>
 * </ul>
 *
 * <p>Common implementations:
 * <ul>
 *   <li><strong>Metrics Collector</strong>: Counts events by type, measures latencies</li>
 *   <li><strong>Audit Logger</strong>: Persists events to audit trail (database, file)</li>
 *   <li><strong>Alert Generator</strong>: Triggers alerts on critical events</li>
 *   <li><strong>State Machine</strong>: Updates PI/Sprint/Work Item state on events</li>
 *   <li><strong>Workflow Trigger</strong>: Initiates YAWL workflows based on events</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0
 * @since YAWL 6.0
 */
@FunctionalInterface
public interface SAFeEventListener {

    /**
     * Handle a SAFe event.
     *
     * <p>This method is called asynchronously when an event is published to the event bus.
     * Implementation should return quickly to avoid blocking other listeners.
     *
     * <p>If an exception is thrown, it is logged by the event bus and does not affect
     * other listeners.
     *
     * @param event the event to handle
     * @throws Exception if handling fails (will be logged but not propagated)
     */
    void onEvent(SAFeAgent.SAFeEvent event) throws Exception;
}
