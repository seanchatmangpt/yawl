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

import org.yawlfoundation.yawl.integration.messagequeue.WorkflowEvent;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for webhook subscription persistence and management.
 *
 * <p>Implementations must be thread-safe; the delivery service calls these methods
 * from multiple concurrent virtual threads.
 *
 * <p>JDBC implementation DDL:
 * <pre>
 * CREATE TABLE webhook_subscriptions (
 *   subscription_id  VARCHAR(36)  PRIMARY KEY,
 *   target_url       VARCHAR(2048) NOT NULL,
 *   secret_key       VARCHAR(512)  NOT NULL,   -- store encrypted at rest
 *   event_types      TEXT          NOT NULL,   -- comma-separated EventType names; empty = all
 *   status           VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
 *   created_at       TIMESTAMP(6)  NOT NULL,
 *   paused_until     TIMESTAMP(6),
 *   description      VARCHAR(512)
 * );
 *
 * CREATE INDEX idx_ws_status ON webhook_subscriptions(status);
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public interface WebhookSubscriptionRepository {

    /**
     * Persist a new webhook subscription. The subscription ID must not already exist.
     *
     * @param subscription the subscription to save (must not be null)
     * @throws RepositoryException      if persistence fails
     * @throws IllegalArgumentException if a subscription with the same ID already exists
     */
    void save(WebhookSubscription subscription) throws RepositoryException;

    /**
     * Update an existing webhook subscription. The subscription ID must already exist.
     *
     * @param subscription the updated subscription (must not be null)
     * @throws RepositoryException      if persistence fails
     * @throws IllegalArgumentException if no subscription exists with this ID
     */
    void update(WebhookSubscription subscription) throws RepositoryException;

    /**
     * Delete a webhook subscription by ID. No-op if the ID does not exist.
     *
     * @param subscriptionId subscription to delete (must not be blank)
     * @throws RepositoryException if deletion fails
     */
    void delete(String subscriptionId) throws RepositoryException;

    /**
     * Find a subscription by ID.
     *
     * @param subscriptionId subscription ID (must not be blank)
     * @return the subscription, or empty if not found
     * @throws RepositoryException if the read fails
     */
    Optional<WebhookSubscription> findById(String subscriptionId) throws RepositoryException;

    /**
     * Find all active subscriptions that match the given event type.
     * Returns subscriptions whose event type filter either:
     * <ul>
     *   <li>is empty (meaning all event types), or</li>
     *   <li>contains the specified event type</li>
     * </ul>
     * Only subscriptions with {@link WebhookSubscription.Status#ACTIVE} status are returned.
     *
     * @param eventType the event type to match (must not be null)
     * @return list of matching active subscriptions (may be empty)
     * @throws RepositoryException if the read fails
     */
    List<WebhookSubscription> findActive(WorkflowEvent.EventType eventType)
            throws RepositoryException;

    /**
     * Find all subscriptions regardless of status.
     *
     * @return all subscriptions
     * @throws RepositoryException if the read fails
     */
    List<WebhookSubscription> findAll() throws RepositoryException;

    /**
     * Pause a subscription until the given time. The subscription status is set to
     * {@link WebhookSubscription.Status#PAUSED} with the given pause-until timestamp.
     *
     * @param subscriptionId subscription to pause (must not be blank)
     * @param pausedUntil    when the pause expires and the subscription auto-resumes
     * @throws RepositoryException      if the update fails
     * @throws IllegalArgumentException if no subscription exists with this ID
     */
    void pauseSubscription(String subscriptionId, Instant pausedUntil) throws RepositoryException;

    /**
     * Re-activate a paused or disabled subscription.
     *
     * @param subscriptionId subscription to activate (must not be blank)
     * @throws RepositoryException      if the update fails
     * @throws IllegalArgumentException if no subscription exists with this ID
     */
    void activateSubscription(String subscriptionId) throws RepositoryException;

    /**
     * Thrown when repository operations fail.
     */
    final class RepositoryException extends RuntimeException {

        /**
         * Construct with message.
         *
         * @param message error description
         */
        public RepositoryException(String message) {
            super(message);
        }

        /**
         * Construct with message and cause.
         *
         * @param message error description
         * @param cause   underlying exception
         */
        public RepositoryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
