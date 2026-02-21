package org.yawlfoundation.yawl.integration.autonomous.marketplace.events;

import java.time.Instant;

/**
 * Base record for all marketplace events flowing through YAWL integration.
 *
 * <p><b>Idempotency Guarantee</b>: Every event carries an idempotency_token and
 * agent_id that together form a unique key. If the same event is delivered twice,
 * YAWL will reject the duplicate (by design of event handler).
 *
 * <p><b>Message Ordering</b>: Events from the same agent are delivered in order
 * (per YAWL workflow semantics). Events from different agents are unordered
 * relative to each other.
 *
 * <p><b>Schema Evolution</b>: Use sealed hierarchy to enable exhaustive pattern
 * matching in message processors. New event types require code changes (compile-time safety).
 *
 * @param eventType the concrete event type (OrderCreated, OrderShipped, etc)
 * @param agentId unique identifier of the agent sending this event
 * @param idempotencyToken deduplication key (unique per agent per logical event)
 * @param timestamp when the event occurred in the agent's system (UTC)
 * @param version semantic version for backward compatibility
 * @param metadata optional contextual data (tracing, correlation IDs, etc)
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public sealed record MarketplaceEvent(
    String eventType,
    String agentId,
    String idempotencyToken,
    Instant timestamp,
    String version,
    EventMetadata metadata
)
    permits OrderEvent, VendorEvent, PaymentEvent {

    /**
     * Construct a marketplace event with validation.
     *
     * @throws IllegalArgumentException if any required field is null or empty
     */
    public MarketplaceEvent {
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType is required");
        }
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId is required");
        }
        if (idempotencyToken == null || idempotencyToken.isBlank()) {
            throw new IllegalArgumentException("idempotencyToken is required");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp is required");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version is required");
        }
        // metadata may be null (optional)
    }

    /**
     * Extract the deduplication key for this event.
     *
     * <p>This key should be used by event handlers to detect and reject duplicates:
     * <pre>
     * String dedupKey = event.deduplicationKey();
     * if (dedupStore.exists(dedupKey)) {
     *     log.info("Duplicate event, skipping: {}", dedupKey);
     *     return;
     * }
     * // Process event...
     * dedupStore.record(dedupKey);
     * </pre>
     *
     * @return a unique deduplication key combining agent ID and idempotency token
     */
    public String deduplicationKey() {
        return agentId + ":" + idempotencyToken;
    }
}
