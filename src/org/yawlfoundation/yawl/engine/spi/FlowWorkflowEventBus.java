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

import org.yawlfoundation.yawl.stateless.listener.event.YEventType;

import java.util.EnumMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.function.Consumer;

/**
 * Default in-JVM implementation of {@link WorkflowEventBus} using Java 21+ Flow API.
 *
 * <p>One {@link SubmissionPublisher} is maintained per {@link YEventType}, which avoids
 * head-of-line blocking between unrelated event streams. Each publisher dispatches to
 * subscribers via a {@code newVirtualThreadPerTaskExecutor()} — no fixed thread pool
 * that could exhaust under load.</p>
 *
 * <p>Back-pressure: if a subscriber's buffer ({@link Flow#defaultBufferSize()} = 256 items)
 * fills up, {@link SubmissionPublisher#submit} blocks the publisher. At 1M cases this
 * should not occur; if it does, configure a larger buffer or use a Kafka adapter.</p>
 *
 * <p>This implementation requires no external infrastructure. It ships with the engine
 * and is registered as the default provider in
 * {@code META-INF/services/org.yawlfoundation.yawl.engine.spi.WorkflowEventBus}.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see WorkflowEventBus
 */
public final class FlowWorkflowEventBus implements WorkflowEventBus {

    private final EnumMap<YEventType, SubmissionPublisher<WorkflowEvent>> _publishers;

    /**
     * Creates a new {@code FlowWorkflowEventBus} with one publisher per event type.
     */
    public FlowWorkflowEventBus() {
        _publishers = new EnumMap<>(YEventType.class);
        for (YEventType type : YEventType.values()) {
            // Virtual-thread-per-task: never exhausts; lightweight for bursty workloads
            _publishers.put(type, new SubmissionPublisher<>(
                    Executors.newVirtualThreadPerTaskExecutor(),
                    Flow.defaultBufferSize()
            ));
        }
    }

    /**
     * Publishes an event to all subscribers registered for its type.
     *
     * <p>Uses {@link SubmissionPublisher#submit}, which blocks the caller if a
     * subscriber's buffer is full (bounded back-pressure). For truly fire-and-forget
     * semantics, replace with {@link SubmissionPublisher#offer} with a drop policy.</p>
     *
     * @param event the event; must not be {@code null}
     * @throws NullPointerException if event is null
     */
    @Override
    public void publish(WorkflowEvent event) {
        if (event == null) throw new NullPointerException("event must not be null");
        SubmissionPublisher<WorkflowEvent> publisher = _publishers.get(event.type());
        if (publisher != null) {
            publisher.submit(event);
        }
    }

    /**
     * Subscribes a handler to events of the specified type.
     *
     * <p>The handler is wrapped in a {@link Flow.Subscriber} that requests unbounded
     * demand ({@link Long#MAX_VALUE}) and dispatches each item to the handler on a
     * virtual thread. Unhandled exceptions in the handler are logged but do not
     * cancel the subscription.</p>
     *
     * @param type    the event type; must not be {@code null}
     * @param handler the handler; must not be {@code null}
     * @throws NullPointerException if type or handler is null
     */
    @Override
    public void subscribe(YEventType type, Consumer<WorkflowEvent> handler) {
        if (type == null) throw new NullPointerException("type must not be null");
        if (handler == null) throw new NullPointerException("handler must not be null");
        SubmissionPublisher<WorkflowEvent> publisher = _publishers.get(type);
        if (publisher != null) {
            publisher.subscribe(new WorkflowEventSubscriber(handler));
        }
    }

    /**
     * Closes all publishers, completing all subscriber streams.
     */
    @Override
    public void close() {
        _publishers.values().forEach(SubmissionPublisher::close);
    }
}
