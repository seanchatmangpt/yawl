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
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

/**
 * Default in-JVM implementation of {@link WorkflowEventBus} using Java 21+ Flow API.
 *
 * <p>One {@link SubmissionPublisher} is maintained per {@link YEventType}, which avoids
 * head-of-line blocking between unrelated event streams. Each publisher dispatches to
 * subscribers via a {@code newVirtualThreadPerTaskExecutor()} — no fixed thread pool
 * that could exhaust under load.</p>
 *
 * <p>Back-pressure: events use non-blocking {@link SubmissionPublisher#offer} with a drop
 * policy. Ring buffer capacity is 32768 items per event type (128× headroom vs default 256).
 * Dropped events are tracked per type and exposed via {@link #getDropCount(YEventType)} and
 * {@link #getTotalDropCount()}. Publisher never blocks the caller.</p>
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

    private static final int RING_BUFFER_CAPACITY = 32768;

    private final EnumMap<YEventType, SubmissionPublisher<WorkflowEvent>> _publishers;
    private final EnumMap<YEventType, LongAdder> _dropCounters;

    /**
     * Creates a new {@code FlowWorkflowEventBus} with one publisher per event type.
     */
    public FlowWorkflowEventBus() {
        _publishers = new EnumMap<>(YEventType.class);
        _dropCounters = new EnumMap<>(YEventType.class);
        for (YEventType type : YEventType.values()) {
            // Virtual-thread-per-task: never exhausts; lightweight for bursty workloads
            _publishers.put(type, new SubmissionPublisher<>(
                    Executors.newVirtualThreadPerTaskExecutor(),
                    RING_BUFFER_CAPACITY
            ));
            _dropCounters.put(type, new LongAdder());
        }
    }

    /**
     * Publishes an event to all subscribers registered for its type.
     *
     * <p>Uses non-blocking {@link SubmissionPublisher#offer} with a drop policy: if the
     * ring buffer is full, the event is dropped and the drop counter for this event type
     * is incremented. The publisher never blocks the caller.</p>
     *
     * @param event the event; must not be {@code null}
     * @throws NullPointerException if event is null
     */
    @Override
    public void publish(WorkflowEvent event) {
        if (event == null) throw new NullPointerException("event must not be null");
        SubmissionPublisher<WorkflowEvent> publisher = _publishers.get(event.type());
        if (publisher != null) {
            publisher.offer(event, (subscriber, dropped) -> {
                _dropCounters.get(dropped.type()).increment();
                return false;  // drop, never retry
            });
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
     * Returns the number of events dropped for the specified type due to buffer overflow.
     *
     * @param type the event type
     * @return the count of dropped events, or 0 if type is unknown
     */
    public long getDropCount(YEventType type) {
        LongAdder counter = _dropCounters.get(type);
        return counter != null ? counter.sum() : 0L;
    }

    /**
     * Returns the total number of events dropped across all types.
     *
     * @return the aggregate drop count
     */
    public long getTotalDropCount() {
        return _dropCounters.values().stream().mapToLong(LongAdder::sum).sum();
    }

    /**
     * Closes all publishers, completing all subscriber streams.
     */
    @Override
    public void close() {
        _publishers.values().forEach(SubmissionPublisher::close);
    }
}
