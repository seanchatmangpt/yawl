package org.yawlfoundation.yawl.integration.autonomous.marketplace.events;

import java.time.Instant;

/**
 * Sealed interface for all marketplace events flowing through YAWL integration.
 *
 * <p>Implementing records: {@link OrderEvent}, {@link VendorEvent}, {@link PaymentEvent}.
 * Use exhaustive pattern matching (switch) on the sealed hierarchy for type-safe dispatch.
 *
 * <p><b>Idempotency Guarantee</b>: Every event carries an idempotencyToken and
 * agentId that together form a unique key. If the same event is delivered twice,
 * YAWL will reject the duplicate (by design of the event handler).
 *
 * <p><b>Message Ordering</b>: Events from the same agent are delivered in order
 * (per YAWL workflow semantics). Events from different agents are unordered
 * relative to each other.
 *
 * <p><b>Schema Evolution</b>: The sealed hierarchy enables exhaustive pattern
 * matching in message processors. New event types require code changes (compile-time safety).
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public sealed interface MarketplaceEvent
        permits OrderEvent, VendorEvent, PaymentEvent {

    /** The concrete event type name (e.g. "OrderCreated", "VendorVerified"). */
    String eventType();

    /** Unique identifier of the agent sending this event. */
    String agentId();

    /** Deduplication key — unique per agent per logical event. */
    String idempotencyToken();

    /** When the event occurred in the agent's system (UTC). */
    Instant timestamp();

    /** Semantic version for backward compatibility. */
    String version();

    /** Optional contextual data (tracing, correlation IDs, etc). May be null. */
    EventMetadata metadata();

    /**
     * Extract the deduplication key for this event.
     *
     * <p>Use this key in event handlers to detect and reject duplicates:
     * <pre>
     * String dedupKey = event.deduplicationKey();
     * if (dedupStore.exists(dedupKey)) {
     *     log.info("Duplicate event, skipping: {}", dedupKey);
     *     return;
     * }
     * dedupStore.record(dedupKey);
     * </pre>
     *
     * @return a unique deduplication key combining agent ID and idempotency token
     */
    default String deduplicationKey() {
        return agentId() + ":" + idempotencyToken();
    }

    /**
     * Validate the six base fields common to all marketplace events.
     * Call from each implementing record's compact constructor.
     *
     * @throws IllegalArgumentException if any required field is null or blank
     */
    static void validateBaseFields(
            String eventType,
            String agentId,
            String idempotencyToken,
            Instant timestamp,
            String version,
            EventMetadata metadata) {
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
        // metadata may be null (optional contextual data)
    }
}
