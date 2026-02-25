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

package org.yawlfoundation.yawl.integration.eventsourcing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.integration.messagequeue.WorkflowEvent;
import org.yawlfoundation.yawl.stateless.engine.YWorkItem;
import org.yawlfoundation.yawl.stateless.listener.YCaseEventListener;
import org.yawlfoundation.yawl.stateless.listener.YWorkItemEventListener;
import org.yawlfoundation.yawl.stateless.listener.event.YCaseEvent;
import org.yawlfoundation.yawl.stateless.listener.event.YEvent;
import org.yawlfoundation.yawl.stateless.listener.event.YEventType;
import org.yawlfoundation.yawl.stateless.listener.event.YWorkItemEvent;

import java.util.Map;
import java.util.Objects;

/**
 * Bridges {@link org.yawlfoundation.yawl.stateless.YStatelessEngine} lifecycle events
 * into the {@link WorkflowEventStore}.
 *
 * <p>Register an instance of this listener with both
 * {@code YStatelessEngine.addCaseEventListener()} and
 * {@code YStatelessEngine.addWorkItemEventListener()} to capture all meaningful
 * case and work item transitions as immutable {@link WorkflowEvent} records.
 * The preferred entry point is {@code YStatelessEngine.wireEventStore(WorkflowEventStore)}.
 *
 * <p>Events that do not have a direct {@link WorkflowEvent.EventType} mapping
 * (e.g., {@code CASE_IDLE_TIMEOUT}, {@code NET_STARTED}) are silently ignored
 * to avoid polluting the event log with internal engine housekeeping events.
 *
 * <p>Append failures are logged at ERROR level and swallowed so that a degraded
 * or disconnected event store never disrupts workflow execution.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 * @see WorkflowEventStore
 * @see org.yawlfoundation.yawl.stateless.YStatelessEngine
 */
public final class WorkflowEventStoreListener
        implements YCaseEventListener, YWorkItemEventListener {

    private static final Logger log =
            LoggerFactory.getLogger(WorkflowEventStoreListener.class);

    private final WorkflowEventStore eventStore;

    /**
     * Construct a listener that writes all mapped events to the given store.
     *
     * @param eventStore the target event store (must not be null)
     */
    public WorkflowEventStoreListener(WorkflowEventStore eventStore) {
        this.eventStore = Objects.requireNonNull(eventStore, "eventStore must not be null");
    }

    // -------------------------------------------------------------------------
    // YCaseEventListener
    // -------------------------------------------------------------------------

    /**
     * Handles a case lifecycle event from the engine.
     * Unmapped event types (e.g., CASE_IDLE_TIMEOUT) are silently skipped.
     *
     * @param event the case event from the engine
     */
    @Override
    public void handleCaseEvent(YCaseEvent event) {
        WorkflowEvent.EventType type = toCaseEventType(event.getEventType());
        if (type == null) {
            return;
        }
        String specId  = extractSpecId(event);
        String caseId  = event.getCaseID() != null ? event.getCaseID().getId() : null;
        appendQuietly(new WorkflowEvent(type, specId, caseId, null, Map.of()));
    }

    // -------------------------------------------------------------------------
    // YWorkItemEventListener
    // -------------------------------------------------------------------------

    /**
     * Handles a work item lifecycle event from the engine.
     * Unmapped event types (e.g., ITEM_DATA_VALUE_CHANGE) are silently skipped.
     *
     * @param event the work item event from the engine
     */
    @Override
    public void handleWorkItemEvent(YWorkItemEvent event) {
        WorkflowEvent.EventType type = toWorkItemEventType(event.getEventType());
        if (type == null) {
            return;
        }
        String specId     = extractSpecId(event);
        String caseId     = event.getCaseID() != null ? event.getCaseID().getId() : null;
        YWorkItem item    = event.getWorkItem();
        String workItemId = item != null ? item.getIDString() : null;
        appendQuietly(new WorkflowEvent(type, specId, caseId, workItemId, Map.of()));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void appendQuietly(WorkflowEvent event) {
        try {
            eventStore.appendNext(event);
        } catch (WorkflowEventStore.EventStoreException e) {
            log.error("Failed to append {} event for case '{}': {}",
                    event.getEventType(), event.getCaseId(), e.getMessage(), e);
        }
    }

    /**
     * Extracts a stable spec ID string in {@code key:version} format from a YAWL event.
     * Falls back to the runner's spec ID for case events, then to {@code "unknown:0"}.
     */
    private String extractSpecId(YEvent event) {
        YSpecificationID specID = event.getSpecID();
        if (specID != null) {
            return specID.toKeyString();
        }
        if (event instanceof YCaseEvent ce && ce.getRunner() != null) {
            YSpecificationID runnerSpec = ce.getRunner().getSpecificationID();
            if (runnerSpec != null) {
                return runnerSpec.toKeyString();
            }
        }
        return "unknown:0";
    }

    /**
     * Maps a YAWL engine {@link YEventType} to a {@link WorkflowEvent.EventType} for
     * case-level events. Returns {@code null} for unmapped types that should not be
     * written to the event store.
     */
    private static WorkflowEvent.EventType toCaseEventType(YEventType type) {
        return switch (type) {
            case CASE_STARTED   -> WorkflowEvent.EventType.CASE_STARTED;
            case CASE_COMPLETED -> WorkflowEvent.EventType.CASE_COMPLETED;
            case CASE_CANCELLED -> WorkflowEvent.EventType.CASE_CANCELLED;
            case CASE_SUSPENDED -> WorkflowEvent.EventType.CASE_SUSPENDED;
            case CASE_RESUMED   -> WorkflowEvent.EventType.CASE_RESUMED;
            default             -> null;
        };
    }

    /**
     * Maps a YAWL engine {@link YEventType} to a {@link WorkflowEvent.EventType} for
     * work item-level events. Returns {@code null} for unmapped types.
     */
    private static WorkflowEvent.EventType toWorkItemEventType(YEventType type) {
        return switch (type) {
            case ITEM_ENABLED   -> WorkflowEvent.EventType.WORKITEM_ENABLED;
            case ITEM_STARTED   -> WorkflowEvent.EventType.WORKITEM_STARTED;
            case ITEM_COMPLETED -> WorkflowEvent.EventType.WORKITEM_COMPLETED;
            case ITEM_CANCELLED -> WorkflowEvent.EventType.WORKITEM_CANCELLED;
            default             -> null;
        };
    }
}
