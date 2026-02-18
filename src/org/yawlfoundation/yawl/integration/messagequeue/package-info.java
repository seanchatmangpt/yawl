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

/**
 * Message queue integration for asynchronous workflow event publishing (YAWL v6.0.0).
 *
 * <p>This package provides the abstraction layer and concrete implementations for
 * publishing YAWL workflow lifecycle events to external message brokers. Two broker
 * implementations ship out of the box:
 *
 * <ul>
 *   <li><b>Kafka</b> - high-throughput, ordered, log-based delivery via
 *       {@link org.yawlfoundation.yawl.integration.messagequeue.kafka.KafkaWorkflowEventPublisher}.
 *       Suitable for event sourcing, audit streams, and analytics pipelines.</li>
 *   <li><b>RabbitMQ</b> - AMQP 0-9-1 delivery via
 *       {@link org.yawlfoundation.yawl.integration.messagequeue.rabbitmq.RabbitMqWorkflowEventPublisher}.
 *       Suitable for transient event fan-out, work queues, and dead-letter routing.</li>
 * </ul>
 *
 * <h2>Event Schema</h2>
 * <p>All events implement {@link org.yawlfoundation.yawl.integration.messagequeue.WorkflowEvent}
 * and carry a stable, versioned JSON envelope:
 * <pre>
 * {
 *   "eventId":    "uuid-v4",
 *   "eventType":  "CASE_STARTED | CASE_COMPLETED | CASE_CANCELLED |
 *                  WORKITEM_ENABLED | WORKITEM_STARTED | WORKITEM_COMPLETED |
 *                  WORKITEM_CANCELLED | WORKITEM_FAILED | SPEC_LOADED | SPEC_UNLOADED",
 *   "specId":     "specificationURI:version",
 *   "caseId":     "case-identifier",
 *   "workItemId": "taskId:caseId (null for case-level events)",
 *   "timestamp":  "ISO-8601 UTC",
 *   "schemaVersion": "1.0",
 *   "payload":    { ... event-specific data ... }
 * }
 * </pre>
 *
 * <h2>Exactly-Once Delivery</h2>
 * <p>Critical workflow events (CASE_COMPLETED, WORKITEM_COMPLETED) are published with
 * idempotency guarantees:
 * <ul>
 *   <li>Kafka: transactions + idempotent producers ({@code enable.idempotence=true},
 *       {@code acks=all}, {@code transactional.id=yawl-{caseId}})</li>
 *   <li>RabbitMQ: publisher confirms + deduplication header
 *       ({@code x-dedup-id: eventId}) consumed by deduplication middleware</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
package org.yawlfoundation.yawl.integration.messagequeue;
