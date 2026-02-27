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

package org.yawlfoundation.yawl.engine.spi;

import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;

import java.time.Instant;

/**
 * Immutable record representing a workflow event published on the {@link WorkflowEventBus}.
 *
 * <p>Events are produced by the YAWL engine at key lifecycle points (case start/complete,
 * work item enabled/completed, etc.) and consumed by subscribers such as
 * {@code ResourceManager}, {@code MappedEventLog}, and optional external adapters.</p>
 *
 * @param type      the type of workflow event
 * @param caseId    the case identifier (may be {@code null} for global events)
 * @param payload   the event payload (type-specific; caller must know the concrete type)
 * @param timestamp wall-clock time the event was created
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see WorkflowEventBus
 */
public record WorkflowEvent(
        YEventType type,
        YIdentifier caseId,
        Object payload,
        Instant timestamp) {

    /**
     * Convenience constructor that stamps the event with the current time.
     *
     * @param type    the event type
     * @param caseId  the case identifier (may be {@code null})
     * @param payload the event payload
     */
    public WorkflowEvent(YEventType type, YIdentifier caseId, Object payload) {
        this(type, caseId, payload, Instant.now());
    }
}
