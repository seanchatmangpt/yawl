/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.engine;

import java.util.HashMap;
import java.util.Map;

/**
 * Enumeration of all possible states a YWorkItem can be in during its lifecycle.
 *
 * <p>Work items progress through states in response to engine operations:
 * <pre>
 *   Enabled -> Fired -> Executing -> Complete
 *                   \-> Failed
 *                   \-> ForcedComplete
 * </pre>
 * </p>
 *
 * <h2>State Descriptions</h2>
 * <ul>
 *   <li><b>Enabled</b> - Task has tokens in all required preset conditions</li>
 *   <li><b>Fired</b> - Task has been claimed but not yet started executing</li>
 *   <li><b>Executing</b> - Task is actively being performed by a service or user</li>
 *   <li><b>Complete</b> - Task finished normally</li>
 *   <li><b>IsParent</b> - Multi-instance parent task with child instances</li>
 *   <li><b>Deadlocked</b> - Task cannot proceed (OR-join deadlock detection)</li>
 *   <li><b>Deleted</b> - Task cancelled by cancel set or exception handling</li>
 *   <li><b>Withdrawn</b> - Task withdrawn due to deferred choice resolution</li>
 *   <li><b>ForcedComplete</b> - Task completed via force complete operation</li>
 *   <li><b>Failed</b> - Task execution failed</li>
 *   <li><b>Suspended</b> - Task execution temporarily paused</li>
 *   <li><b>CancelledByCase</b> - Task cancelled due to case cancellation</li>
 *   <li><b>Discarded</b> - Tokens remaining in net after case completion</li>
 * </ul>
 *
 * @author Mike Fowler
 * @see YWorkItem
 */
public enum YWorkItemStatus {

    statusEnabled("Enabled"),
    statusFired("Fired"),
    statusExecuting("Executing"),
    statusComplete("Complete"),
    statusIsParent("Is parent"),
    statusDeadlocked("Deadlocked"),
    statusDeleted("Cancelled"),                       // by cancel set or exception
    statusWithdrawn("Withdrawn"),                     // by deferred choice etc
    statusForcedComplete("ForcedComplete"),
    statusFailed("Failed"),
    statusSuspended("Suspended"),
    statusCancelledByCase("CancelledByCase"),
    statusDiscarded("Discarded");                     // tokens in net after completion

    private final String statusString;

    private static final Map<String, YWorkItemStatus> _fromStringMap =
            new HashMap<String, YWorkItemStatus>(13);

    static {
        for (YWorkItemStatus itemStatus : values()) {
            _fromStringMap.put(itemStatus.toString(), itemStatus);
        }
    }

    YWorkItemStatus(String status) {
        statusString = status;
    }

    @Override
    public String toString() {
        return statusString;
    }

    public static YWorkItemStatus fromString(String s) {
        return (s != null) ? _fromStringMap.get(s) : null;
    }
}
