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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.Flow;
import java.util.function.Consumer;

/**
 * {@link Flow.Subscriber} adapter that delegates each received {@link WorkflowEvent}
 * to a {@link Consumer} handler.
 *
 * <p>Requests unbounded demand on subscription ({@link Long#MAX_VALUE}) — the
 * {@link FlowWorkflowEventBus} applies back-pressure at the publisher side if the
 * subscriber's internal buffer fills up.</p>
 *
 * <p>Exceptions thrown by the handler are caught and logged; the subscription is
 * <em>not</em> cancelled so a misbehaving handler does not block the event stream.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
final class WorkflowEventSubscriber implements Flow.Subscriber<WorkflowEvent> {

    private static final Logger log = LogManager.getLogger(WorkflowEventSubscriber.class);

    private final Consumer<WorkflowEvent> _handler;
    private Flow.Subscription _subscription;

    WorkflowEventSubscriber(Consumer<WorkflowEvent> handler) {
        _handler = handler;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        _subscription = subscription;
        _subscription.request(Long.MAX_VALUE); // unbounded demand
    }

    @Override
    public void onNext(WorkflowEvent event) {
        try {
            _handler.accept(event);
        } catch (Exception e) {
            log.warn("WorkflowEvent handler threw exception for event {}: {}",
                    event.type(), e.getMessage(), e);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        log.error("WorkflowEventBus stream error: {}", throwable.getMessage(), throwable);
    }

    @Override
    public void onComplete() {
        log.debug("WorkflowEventBus stream completed");
    }
}
