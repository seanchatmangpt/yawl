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
package org.yawlfoundation.yawl.erlang.workflow;

import java.time.Instant;

/**
 * Event fired when the {@code YawlServiceSupervisor} autonomously restarts a
 * supervised Java service after consecutive health check failures.
 *
 * <p>This event is the operational evidence of the system's self-healing property:
 * a service restart with no human operator intervention. Monitoring systems can
 * subscribe to this event to track restart frequency and detect restart storms.</p>
 *
 * @param serviceName  the name of the supervised service (from {@code ManagedService.serviceName()})
 * @param at           the instant the restart was triggered
 * @param restartCount cumulative restart count for this service since supervisor creation
 * @param reason       human-readable description of why the restart was triggered
 */
public record ServiceRestartEvent(
        String serviceName,
        Instant at,
        int restartCount,
        String reason) implements WorkflowEvent {

    public ServiceRestartEvent {
        if (serviceName == null || serviceName.isBlank())
            throw new IllegalArgumentException("serviceName must be non-blank");
        if (at == null)
            throw new IllegalArgumentException("at must be non-null");
        if (restartCount < 0)
            throw new IllegalArgumentException("restartCount must be non-negative");
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("reason must be non-blank");
    }
}
