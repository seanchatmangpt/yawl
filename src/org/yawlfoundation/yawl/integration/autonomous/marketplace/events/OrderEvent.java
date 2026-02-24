package org.yawlfoundation.yawl.integration.autonomous.marketplace.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Order events from the marketplace vendor agent.
 *
 * <p><b>Lifecycle</b>:
 * <ul>
 *   <li>OrderCreated: New order placed by customer</li>
 *   <li>OrderConfirmed: Vendor confirmed inventory availability</li>
 *   <li>OrderShipped: Vendor shipped items</li>
 *   <li>OrderDelivered: Fulfillment confirms customer receipt</li>
 *   <li>OrderCancelled: Order cancelled (by customer or vendor)</li>
 * </ul>
 *
 * <p><b>Idempotency</b>: The combination of (agentId, idempotencyToken) uniquely
 * identifies a logical event. If a vendor sends OrderCreated twice with the same
 * token, the second delivery is a duplicate and should be discarded by the handler.
 *
 * <p><b>Message Ordering</b>: Events for the same order_id from the same vendor
 * agent are delivered in order. The workflow engine enforces sequential processing.
 *
 * @param eventType MUST be "OrderCreated", "OrderConfirmed", "OrderShipped", "OrderDelivered", or "OrderCancelled"
 * @param agentId unique identifier of the vendor agent
 * @param idempotencyToken vendor-assigned deduplication key (e.g. order_id:event_number)
 * @param timestamp when the event occurred in the vendor's system
 * @param version semantic version (e.g. "1.0", "1.1")
 * @param metadata optional tracing and contextual data
 * @param orderId global order identifier
 * @param customerId GCP customer ID
 * @param status enum-like string: PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED
 * @param items list of line items with quantities and prices
 * @param totalAmount final order amount including tax/shipping
 * @param currency ISO 4217 currency code (e.g. "USD")
 * @param shippingAddress full shipping address if applicable
 * @param trackingNumber carrier tracking number (nullable until shipped)
 * @param estimatedDelivery expected delivery date (nullable until shipped)
 * @param cancellationReason reason for cancellation if applicable
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record OrderEvent(
    String eventType,
    String agentId,
    String idempotencyToken,
    Instant timestamp,
    String version,
    EventMetadata metadata,
    String orderId,
    String customerId,
    String status,
    List<OrderLineItem> items,
    BigDecimal totalAmount,
    String currency,
    String shippingAddress,
    String trackingNumber,
    String estimatedDelivery,
    String cancellationReason
) implements MarketplaceEvent {

    /**
     * Canonical event type constants.
     */
    public static class EventTypes {
        public static final String ORDER_CREATED = "OrderCreated";
        public static final String ORDER_CONFIRMED = "OrderConfirmed";
        public static final String ORDER_SHIPPED = "OrderShipped";
        public static final String ORDER_DELIVERED = "OrderDelivered";
        public static final String ORDER_CANCELLED = "OrderCancelled";
    }

    /**
     * Order status constants.
     */
    public static class Status {
        public static final String PENDING = "PENDING";
        public static final String CONFIRMED = "CONFIRMED";
        public static final String SHIPPED = "SHIPPED";
        public static final String DELIVERED = "DELIVERED";
        public static final String CANCELLED = "CANCELLED";
    }

    /**
     * Construct an OrderEvent with comprehensive validation.
     *
     * @throws IllegalArgumentException if required fields are missing or invalid
     */
    public OrderEvent {
        // Validate base fields (delegated to sealed interface static helper)
        MarketplaceEvent.validateBaseFields(eventType, agentId, idempotencyToken, timestamp, version);

        // Validate order-specific fields
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId is required");
        }
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("customerId is required");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status is required");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items list is required and cannot be empty");
        }
        if (totalAmount == null || totalAmount.signum() <= 0) {
            throw new IllegalArgumentException("totalAmount must be positive");
        }
        if (currency == null || currency.length() != 3) {
            throw new IllegalArgumentException("currency must be ISO 4217 code (3 letters)");
        }

        // Validate consistency: status and tracking/delivery info
        if ((status.equals(Status.SHIPPED) || status.equals(Status.DELIVERED))
                && (trackingNumber == null || trackingNumber.isBlank())) {
            throw new IllegalArgumentException(
                "trackingNumber is required for status " + status);
        }
    }

    /**
     * Builder for fluent OrderEvent construction.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for OrderEvent.
     */
    public static class Builder {
        private String eventType = OrderEvent.EventTypes.ORDER_CREATED;
        private String agentId;
        private String idempotencyToken;
        private Instant timestamp = Instant.now();
        private String version = "1.0";
        private EventMetadata metadata;
        private String orderId;
        private String customerId;
        private String status = Status.PENDING;
        private List<OrderLineItem> items = List.of();
        private BigDecimal totalAmount;
        private String currency;
        private String shippingAddress;
        private String trackingNumber;
        private String estimatedDelivery;
        private String cancellationReason;

        public Builder eventType(String eventType) {
            this.eventType = eventType;
            return this;
        }

        public Builder agentId(String agentId) {
            this.agentId = agentId;
            return this;
        }

        public Builder idempotencyToken(String idempotencyToken) {
            this.idempotencyToken = idempotencyToken;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder metadata(EventMetadata metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder orderId(String orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder customerId(String customerId) {
            this.customerId = customerId;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder items(List<OrderLineItem> items) {
            this.items = items;
            return this;
        }

        public Builder totalAmount(BigDecimal totalAmount) {
            this.totalAmount = totalAmount;
            return this;
        }

        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public Builder shippingAddress(String shippingAddress) {
            this.shippingAddress = shippingAddress;
            return this;
        }

        public Builder trackingNumber(String trackingNumber) {
            this.trackingNumber = trackingNumber;
            return this;
        }

        public Builder estimatedDelivery(String estimatedDelivery) {
            this.estimatedDelivery = estimatedDelivery;
            return this;
        }

        public Builder cancellationReason(String cancellationReason) {
            this.cancellationReason = cancellationReason;
            return this;
        }

        public OrderEvent build() {
            return new OrderEvent(
                eventType, agentId, idempotencyToken, timestamp, version, metadata,
                orderId, customerId, status, items, totalAmount, currency,
                shippingAddress, trackingNumber, estimatedDelivery, cancellationReason);
        }
    }

    /**
     * Check if this order is in a terminal state.
     */
    public boolean isTerminal() {
        return Status.DELIVERED.equals(status) || Status.CANCELLED.equals(status);
    }

    /**
     * Get tracking information if available.
     */
    public Optional<String> getTrackingInfo() {
        return Optional.ofNullable(trackingNumber);
    }
}
