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
 * Functional interface for receiving typed {@link WorkflowEvent} notifications.
 *
 * <p>Listeners are registered with {@link WorkflowEventBus#subscribe} and receive
 * events of their declared type {@code T}. Events are delivered on a virtual thread;
 * listeners must not assume they execute on the publisher's thread.</p>
 *
 * <p>Listeners that throw exceptions cause the exception to be logged but do not
 * propagate to the publisher or other listeners.</p>
 *
 * @param <T> the specific {@link WorkflowEvent} subtype this listener handles
 */
@FunctionalInterface
public interface EventListener<T extends WorkflowEvent> {

    /**
     * Called when an event of type {@code T} is published.
     *
     * @param event the published event (never null)
     * @throws Exception any exception (will be logged, not propagated)
     */
    void onEvent(T event) throws Exception;
}
