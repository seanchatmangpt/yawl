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
 * Event fired when a new workflow instance (case) is created.
 *
 * @param instanceId unique identifier for the workflow case
 * @param specId     the workflow specification that the case instantiates
 * @param at         the instant the case was created
 */
public record WorkflowInstanceCreated(
        String instanceId,
        String specId,
        Instant at) implements WorkflowEvent {

    public WorkflowInstanceCreated {
        if (instanceId == null || instanceId.isBlank())
            throw new IllegalArgumentException("instanceId must be non-blank");
        if (specId == null || specId.isBlank())
            throw new IllegalArgumentException("specId must be non-blank");
        if (at == null)
            throw new IllegalArgumentException("at must be non-null");
    }
}
