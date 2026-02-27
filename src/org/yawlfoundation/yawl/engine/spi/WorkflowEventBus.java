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

import java.util.function.Consumer;

/**
 * SPI interface for the YAWL workflow event bus.
 *
 * <p>The event bus decouples the engine's internal processing from downstream consumers
 * (resource manager, event log, monitoring) without blocking the hot execution path.
 * Publishing is non-blocking; back-pressure is applied if subscribers are slow.</p>
 *
 * <p><b>Default implementation</b>: {@link FlowWorkflowEventBus} uses the Java 21+
 * {@code java.util.concurrent.Flow} API ({@code SubmissionPublisher}) with one publisher
 * per event type. No external infrastructure required.</p>
 *
 * <p><b>Optional adapters</b> (opt-in via Maven dependency + {@code ServiceLoader}):</p>
 * <ul>
 *   <li>{@code KafkaWorkflowEventBus} — publishes to Kafka topic {@code yawl.workflow.events}</li>
 * </ul>
 *
 * <p>Implementations are loaded via {@code ServiceLoader.load(WorkflowEventBus.class)}.
 * The default {@link FlowWorkflowEventBus} is registered in
 * {@code META-INF/services/org.yawlfoundation.yawl.engine.spi.WorkflowEventBus}.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see FlowWorkflowEventBus
 * @see WorkflowEvent
 */
public interface WorkflowEventBus extends AutoCloseable {

    /**
     * Publishes a workflow event to all registered subscribers for the event's type.
     *
     * <p>This method is non-blocking: it enqueues the event in the subscriber's buffer.
     * If the buffer is full, the publisher applies back-pressure (drops or waits,
     * depending on the implementation).</p>
     *
     * @param event the event to publish; must not be {@code null}
     */
    void publish(WorkflowEvent event);

    /**
     * Registers a handler that will be called for all events of the specified type.
     *
     * <p>Handlers are invoked asynchronously on virtual threads. Multiple handlers
     * may be registered for the same event type.</p>
     *
     * @param type    the event type to subscribe to; must not be {@code null}
     * @param handler the handler to invoke on each matching event; must not be {@code null}
     */
    void subscribe(YEventType type, Consumer<WorkflowEvent> handler);

    /**
     * Shuts down the event bus, releasing all resources.
     * After this call, {@link #publish} and {@link #subscribe} have undefined behaviour.
     */
    @Override
    void close();

    /**
     * Returns the default in-JVM event bus instance, loading it via {@code ServiceLoader}.
     * Falls back to {@link FlowWorkflowEventBus} if no provider is registered.
     *
     * @return the default {@link WorkflowEventBus} for the current JVM
     */
    static WorkflowEventBus defaultBus() {
        return java.util.ServiceLoader.load(WorkflowEventBus.class)
                .findFirst()
                .orElseGet(FlowWorkflowEventBus::new);
    }
}
