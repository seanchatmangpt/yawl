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
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a registered webhook subscription for YAWL workflow event delivery.
 *
 * <p>A subscription binds an HTTP endpoint URL to a set of event types to be delivered.
 * Each subscription holds an HMAC-SHA256 secret used to sign outbound requests.
 * Receivers must verify the signature to ensure events originate from YAWL.
 *
 * <h2>Subscription Lifecycle</h2>
 * <pre>
 * ACTIVE   - events are being delivered (default state on creation)
 * PAUSED   - delivery temporarily suspended (manual or automatic on too many failures)
 * DISABLED - permanently disabled; no events delivered until re-activated via API
 * </pre>
 *
 * <h2>Secret Rotation</h2>
 * <p>YAWL supports zero-downtime secret rotation. During rotation:
 * <ol>
 *   <li>New secret is set; both old and new secrets are active for a configurable overlap window</li>
 *   <li>Receiver updates their verification logic to accept the new secret</li>
 *   <li>After the overlap window expires, the old secret is invalidated</li>
 * </ol>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class WebhookSubscription {

    /**
     * Subscription lifecycle status.
     */
    public enum Status {
        /** Webhook is active and events are being delivered. */
        ACTIVE,
        /** Delivery paused; will resume after {@code pausedUntil}. */
        PAUSED,
        /** Permanently disabled; requires explicit re-activation via management API. */
        DISABLED
    }

    private final String                          subscriptionId;
    private final String                          targetUrl;
    private final String                          secretKey;
    private final Set<WorkflowEvent.EventType>    eventTypeFilter;
    private final Status                          status;
    private final Instant                         createdAt;
    private final Instant                         pausedUntil;
    private final String                          description;

    /**
     * Construct a new active webhook subscription with a generated ID and current timestamp.
     *
     * @param targetUrl       HTTP endpoint URL to deliver events to (must use HTTPS in production)
     * @param secretKey       HMAC-SHA256 secret (minimum 32 characters; generate with
     *                        {@code openssl rand -base64 32})
     * @param eventTypeFilter event types to deliver; empty set means all event types
     * @param description     optional human-readable description
     */
    public WebhookSubscription(String targetUrl, String secretKey,
                               Set<WorkflowEvent.EventType> eventTypeFilter,
                               String description) {
        this(UUID.randomUUID().toString(), targetUrl, secretKey, eventTypeFilter,
             Status.ACTIVE, Instant.now(), null, description);
    }

    /**
     * Full constructor used for deserialization and copy-on-write updates.
     *
     * @param subscriptionId  unique subscription ID
     * @param targetUrl       HTTP endpoint URL
     * @param secretKey       HMAC-SHA256 secret
     * @param eventTypeFilter event types to deliver (empty = all)
     * @param status          current subscription status
     * @param createdAt       creation timestamp
     * @param pausedUntil     when PAUSED status expires (null if not paused)
     * @param description     optional description
     */
    public WebhookSubscription(String subscriptionId, String targetUrl, String secretKey,
                               Set<WorkflowEvent.EventType> eventTypeFilter,
                               Status status, Instant createdAt, Instant pausedUntil,
                               String description) {
        if (subscriptionId == null || subscriptionId.isBlank()) {
            throw new IllegalArgumentException("subscriptionId must not be blank");
        }
        if (targetUrl == null || targetUrl.isBlank()) {
            throw new IllegalArgumentException("targetUrl must not be blank");
        }
        if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
            throw new IllegalArgumentException(
                    "targetUrl must start with http:// or https://, got: " + targetUrl);
        }
        if (secretKey == null || secretKey.length() < 32) {
            throw new IllegalArgumentException(
                    "secretKey must be at least 32 characters for HMAC-SHA256. "
                    + "Generate with: openssl rand -base64 32");
        }
        this.subscriptionId  = subscriptionId;
        this.targetUrl       = targetUrl;
        this.secretKey       = secretKey;
        this.eventTypeFilter = Collections.unmodifiableSet(
                                   eventTypeFilter != null ? eventTypeFilter : Set.of());
        this.status          = Objects.requireNonNull(status, "status");
        this.createdAt       = Objects.requireNonNull(createdAt, "createdAt");
        this.pausedUntil     = pausedUntil;
        this.description     = description != null ? description : "";
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Unique subscription identifier. */
    public String getSubscriptionId()   { return subscriptionId; }

    /** HTTP endpoint URL for event delivery. */
    public String getTargetUrl()        { return targetUrl; }

    /** HMAC-SHA256 secret key for request signing. */
    public String getSecretKey()        { return secretKey; }

    /**
     * Event type filter. An empty set means all event types are delivered.
     * A non-empty set restricts delivery to only the listed event types.
     */
    public Set<WorkflowEvent.EventType> getEventTypeFilter() { return eventTypeFilter; }

    /** Current subscription status. */
    public Status getStatus()           { return status; }

    /** Subscription creation timestamp. */
    public Instant getCreatedAt()       { return createdAt; }

    /** When a PAUSED subscription will resume; null if not paused. */
    public Instant getPausedUntil()     { return pausedUntil; }

    /** Human-readable description of this subscription. */
    public String getDescription()      { return description; }

    // -------------------------------------------------------------------------
    // Business logic
    // -------------------------------------------------------------------------

    /**
     * Returns whether this event should be delivered to this subscription.
     * Considers both the event type filter and the current subscription status.
     *
     * @param event the event to check
     * @return true if the event should be delivered
     */
    public boolean shouldDeliver(WorkflowEvent event) {
        Objects.requireNonNull(event, "event must not be null");

        if (status == Status.DISABLED) {
            return false;
        }
        if (status == Status.PAUSED) {
            if (pausedUntil == null || Instant.now().isBefore(pausedUntil)) {
                return false;
            }
            // Pause window has expired - would need status update to re-activate
            return false;
        }
        if (eventTypeFilter.isEmpty()) {
            return true; // empty filter = all events
        }
        return eventTypeFilter.contains(event.getEventType());
    }

    /**
     * Returns a new subscription with the given status.
     * The original subscription is not modified.
     *
     * @param newStatus   the new status
     * @param pausedUntil when paused status expires (only relevant for PAUSED status)
     * @return new subscription with updated status
     */
    public WebhookSubscription withStatus(Status newStatus, Instant pausedUntil) {
        return new WebhookSubscription(subscriptionId, targetUrl, secretKey, eventTypeFilter,
                                       newStatus, createdAt, pausedUntil, description);
    }

    /**
     * Returns a new subscription with a rotated secret key.
     * The original subscription is not modified.
     *
     * @param newSecretKey the new secret key (minimum 32 characters)
     * @return new subscription with updated secret
     */
    public WebhookSubscription withSecretKey(String newSecretKey) {
        return new WebhookSubscription(subscriptionId, targetUrl, newSecretKey, eventTypeFilter,
                                       status, createdAt, pausedUntil, description);
    }

    @Override
    public String toString() {
        return "WebhookSubscription{id='" + subscriptionId
             + "', url='" + targetUrl
             + "', status=" + status
             + ", filter=" + eventTypeFilter
             + ", createdAt=" + createdAt + '}';
    }
}
