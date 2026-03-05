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

package org.yawlfoundation.yawl.integration.webhook;

import java.time.Instant;

/**
 * Audit log interface for webhook delivery attempts.
 *
 * <p>Records every delivery attempt (success, failure, dead-letter) for debugging
 * and SLA monitoring. Implementations may persist to JDBC, emit to a metrics system,
 * or do both.
 *
 * <p>JDBC implementation DDL:
 * <pre>
 * CREATE TABLE webhook_delivery_log (
 *   id               BIGINT PRIMARY KEY AUTO_INCREMENT,
 *   event_id         VARCHAR(36)   NOT NULL,
 *   subscription_id  VARCHAR(36)   NOT NULL,
 *   delivery_id      VARCHAR(36)   NOT NULL,
 *   attempt_number   INT           NOT NULL,
 *   attempted_at     TIMESTAMP(6)  NOT NULL,
 *   http_status      INT,
 *   latency_ms       BIGINT,
 *   outcome          VARCHAR(16)   NOT NULL,  -- 'SUCCESS', 'FAILURE', 'DEAD_LETTER'
 *   error_message    VARCHAR(1024),
 *   response_body    VARCHAR(512),
 *   INDEX idx_wdl_event (event_id),
 *   INDEX idx_wdl_sub   (subscription_id)
 * );
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public interface WebhookDeliveryLog {

    /**
     * Record a successful delivery attempt.
     *
     * @param eventId         ID of the delivered event
     * @param subscriptionId  ID of the target subscription
     * @param deliveryId      unique ID for this delivery attempt
     * @param attemptNumber   1-based attempt count
     * @param attemptAt       when the attempt was made
     * @param httpStatus      HTTP response status code (2xx)
     * @param latencyMs       round-trip latency in milliseconds
     * @param responseBody    HTTP response body (truncated to 512 bytes)
     */
    void recordSuccess(String eventId, String subscriptionId, String deliveryId,
                       int attemptNumber, Instant attemptAt, int httpStatus,
                       long latencyMs, String responseBody);

    /**
     * Record a failed delivery attempt that may be retried.
     *
     * @param eventId        ID of the event that failed to deliver
     * @param subscriptionId ID of the target subscription
     * @param deliveryId     unique ID for this delivery attempt
     * @param attemptNumber  1-based attempt count
     * @param attemptAt      when the attempt was made
     * @param httpStatus     HTTP response status code (0 if no response)
     * @param errorMessage   human-readable error description
     * @param responseBody   HTTP response body (truncated; null if no response)
     */
    void recordFailure(String eventId, String subscriptionId, String deliveryId,
                       int attemptNumber, Instant attemptAt, int httpStatus,
                       String errorMessage, String responseBody);

    /**
     * Record that all retry attempts have been exhausted and the delivery is being
     * routed to the dead-letter log for manual inspection.
     *
     * @param eventId        ID of the undeliverable event
     * @param subscriptionId ID of the target subscription
     * @param totalAttempts  total number of delivery attempts made
     * @param deadLetteredAt when the event was dead-lettered
     */
    void recordDeadLetter(String eventId, String subscriptionId,
                          int totalAttempts, Instant deadLetteredAt);
}
