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

/**
 * Sealed marker interface for all YAWL workflow lifecycle events.
 *
 * <p>All event records are immutable (Java records). Publishers call
 * {@link WorkflowEventBus#publish(WorkflowEvent)} to dispatch events;
 * subscribers receive them via {@link EventListener}.</p>
 *
 * <p>Permitted event types:
 * <ul>
 *   <li>{@link WorkflowInstanceCreated} — a new case has been started</li>
 *   <li>{@link WorkflowInstanceCompleted} — a case has completed normally</li>
 *   <li>{@link TaskStarted} — a task has begun execution</li>
 *   <li>{@link TaskCompleted} — a task has completed successfully</li>
 *   <li>{@link TaskFailed} — a task has failed with an error</li>
 *   <li>{@link TaskSchemaViolation} — a task received data violating its contract</li>
 *   <li>{@link ServiceRestartEvent} — a supervised Java service was restarted</li>
 * </ul>
 *
 * @see WorkflowEventBus
 * @see EventListener
 */
public sealed interface WorkflowEvent
        permits WorkflowInstanceCreated, WorkflowInstanceCompleted,
                TaskStarted, TaskCompleted, TaskFailed,
                TaskSchemaViolation, ServiceRestartEvent {
}
