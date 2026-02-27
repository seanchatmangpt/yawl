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

package org.yawlfoundation.yawl.integration.messagequeue;

/**
 * Abstraction for publishing YAWL workflow events to a message broker.
 *
 * <p>Implementations must guarantee:
 * <ul>
 *   <li>Events returned by {@link WorkflowEvent#requiresExactlyOnceDelivery()} must be
 *       delivered exactly-once using broker-native idempotency mechanisms
 *       (Kafka transactions / RabbitMQ publisher confirms + deduplication headers).</li>
 *   <li>{@link #publish(WorkflowEvent)} is thread-safe for concurrent callers.</li>
 *   <li>{@link #close()} flushes all in-flight events and releases broker resources
 *       without data loss when called from application shutdown hooks.</li>
 * </ul>
 *
 * <p>Calling convention: the YAWL engine calls {@link #publish(WorkflowEvent)} from
 * its event announcement path. The publisher must not block the calling thread for
 * more than the configured send timeout; if the broker is unavailable, the publisher
 * should buffer the event internally and deliver it asynchronously, rethrowing only
 * if the internal buffer is also full.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public interface WorkflowEventPublisher extends AutoCloseable {

    /**
     * Publish a workflow event to the message broker.
     *
     * <p>For events where {@link WorkflowEvent#requiresExactlyOnceDelivery()} returns
     * true, the implementation must use broker-native exactly-once guarantees.
     *
     * @param event the event to publish (must not be null)
     * @throws EventPublishException    if the event cannot be published after internal retries
     * @throws IllegalArgumentException if event is null
     */
    void publish(WorkflowEvent event) throws EventPublishException;

    /**
     * Flush all buffered events and close broker connections.
     * Safe to call multiple times; subsequent calls after first are no-ops.
     *
     * @throws EventPublishException if flushing fails and events would be lost
     */
    @Override
    void close() throws EventPublishException;

    /**
     * Returns the name of this publisher for logging and monitoring.
     *
     * @return publisher name (e.g. "kafka", "rabbitmq")
     */
    String publisherName();

    /**
     * Thrown when event publication fails after internal retry exhaustion.
     */
    final class EventPublishException extends Exception {

        private final WorkflowEvent failedEvent;

        /**
         * Construct with failed event and cause.
         *
         * @param message     human-readable error description
         * @param failedEvent the event that could not be published (may be null for flush errors)
         * @param cause       underlying exception
         */
        public EventPublishException(String message, WorkflowEvent failedEvent, Throwable cause) {
            super(message, cause);
            this.failedEvent = failedEvent;
        }

        /**
         * Returns the event that could not be published.
         *
         * @return failed event, or null for flush/close errors
         */
        public WorkflowEvent getFailedEvent() {
            return failedEvent;
        }
    }
}
