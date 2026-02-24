/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.integration.autonomous.marketplace;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * GCP Marketplace event schema definitions for YAWL integration.
 *
 * Defines immutable records for marketplace events ensuring message ordering
 * and idempotency guarantees across vendor, order, fulfillment, and payment domains.
 *
 * @author YAWL Marketplace Integration
 * @since 6.0.0
 */
public final class MarketplaceEventSchema {

    // =========================================================================
    // Order Events
    // =========================================================================

    /**
     * Order created event. Triggered when a customer purchases a product.
     * Idempotency key: orderId (globally unique, immutable).
     */
    public record OrderCreatedEvent(
        @JsonProperty("order_id") String orderId,
        @JsonProperty("vendor_id") String vendorId,
        @JsonProperty("product_id") String productId,
        @JsonProperty("quantity") int quantity,
        @JsonProperty("unit_price_cents") long unitPriceCents,
        @JsonProperty("total_price_cents") long totalPriceCents,
        @JsonProperty("customer_id") String customerId,
        @JsonProperty("region") String region,
        @JsonProperty("timestamp_utc") String timestampUtc,
        @JsonProperty("sequence_number") long sequenceNumber,
        @JsonProperty("metadata") Map<String, String> metadata
    ) {
        public OrderCreatedEvent {
            Objects.requireNonNull(orderId, "orderId");
            Objects.requireNonNull(vendorId, "vendorId");
            Objects.requireNonNull(productId, "productId");
            Objects.requireNonNull(customerId, "customerId");
            if (quantity <= 0) throw new IllegalArgumentException("quantity must be > 0");
            if (totalPriceCents < 0) throw new IllegalArgumentException("totalPriceCents cannot be negative");
        }
    }

    /**
     * Order confirmed event. Triggered when order payment is authorized.
     * Idempotency key: (orderId, confirmationId) pair.
     */
    public record OrderConfirmedEvent(
        @JsonProperty("order_id") String orderId,
        @JsonProperty("confirmation_id") String confirmationId,
        @JsonProperty("payment_method") String paymentMethod,
        @JsonProperty("timestamp_utc") String timestampUtc,
        @JsonProperty("sequence_number") long sequenceNumber,
        @JsonProperty("metadata") Map<String, String> metadata
    ) {
        public OrderConfirmedEvent {
            Objects.requireNonNull(orderId, "orderId");
            Objects.requireNonNull(confirmationId, "confirmationId");
            Objects.requireNonNull(paymentMethod, "paymentMethod");
        }
    }

    /**
     * Order shipped event. Triggered when vendor ships the order.
     * Idempotency key: (orderId, shipmentId) pair.
     */
    public record OrderShippedEvent(
        @JsonProperty("order_id") String orderId,
        @JsonProperty("shipment_id") String shipmentId,
        @JsonProperty("carrier") String carrier,
        @JsonProperty("tracking_number") String trackingNumber,
        @JsonProperty("estimated_delivery_days") int estimatedDeliveryDays,
        @JsonProperty("shipped_timestamp_utc") String shippedTimestampUtc,
        @JsonProperty("sequence_number") long sequenceNumber,
        @JsonProperty("metadata") Map<String, String> metadata
    ) {
        public OrderShippedEvent {
            Objects.requireNonNull(orderId, "orderId");
            Objects.requireNonNull(shipmentId, "shipmentId");
            if (estimatedDeliveryDays <= 0) {
                throw new IllegalArgumentException("estimatedDeliveryDays must be > 0");
            }
        }
    }

    /**
     * Order delivered event. Triggered when shipment is delivered.
     * Idempotency key: (orderId, shipmentId, deliveryTimestamp) triple.
     */
    public record OrderDeliveredEvent(
        @JsonProperty("order_id") String orderId,
        @JsonProperty("shipment_id") String shipmentId,
        @JsonProperty("delivered_timestamp_utc") String deliveredTimestampUtc,
        @JsonProperty("signature_required") boolean signatureRequired,
        @JsonProperty("sequence_number") long sequenceNumber,
        @JsonProperty("metadata") Map<String, String> metadata
    ) {
        public OrderDeliveredEvent {
            Objects.requireNonNull(orderId, "orderId");
            Objects.requireNonNull(shipmentId, "shipmentId");
        }
    }

    /**
     * Order returned event. Triggered when customer initiates return.
     * Idempotency key: (orderId, returnId) pair.
     */
    public record OrderReturnedEvent(
        @JsonProperty("order_id") String orderId,
        @JsonProperty("return_id") String returnId,
        @JsonProperty("reason") String reason,
        @JsonProperty("refund_cents") long refundCents,
        @JsonProperty("returned_timestamp_utc") String returnedTimestampUtc,
        @JsonProperty("sequence_number") long sequenceNumber,
        @JsonProperty("metadata") Map<String, String> metadata
    ) {
        public OrderReturnedEvent {
            Objects.requireNonNull(orderId, "orderId");
            Objects.requireNonNull(returnId, "returnId");
            Objects.requireNonNull(reason, "reason");
        }
    }

    // =========================================================================
    // Vendor Events
    // =========================================================================

    /**
     * Vendor onboarded event. Triggered when a vendor registers on marketplace.
     * Idempotency key: vendorId (globally unique, immutable).
     */
    public record VendorOnboardedEvent(
        @JsonProperty("vendor_id") String vendorId,
        @JsonProperty("company_name") String companyName,
        @JsonProperty("contact_email") String contactEmail,
        @JsonProperty("region") String region,
        @JsonProperty("tier") String tier,
        @JsonProperty("onboarded_timestamp_utc") String onboardedTimestampUtc,
        @JsonProperty("sequence_number") long sequenceNumber,
        @JsonProperty("metadata") Map<String, String> metadata
    ) {
        public VendorOnboardedEvent {
            Objects.requireNonNull(vendorId, "vendorId");
            Objects.requireNonNull(companyName, "companyName");
            Objects.requireNonNull(contactEmail, "contactEmail");
        }
    }

    /**
     * Vendor verified event. Triggered when vendor passes compliance checks.
     * Idempotency key: (vendorId, verificationId) pair.
     */
    public record VendorVerifiedEvent(
        @JsonProperty("vendor_id") String vendorId,
        @JsonProperty("verification_id") String verificationId,
        @JsonProperty("verification_level") String verificationLevel,
        @JsonProperty("verified_timestamp_utc") String verifiedTimestampUtc,
        @JsonProperty("sequence_number") long sequenceNumber,
        @JsonProperty("metadata") Map<String, String> metadata
    ) {
        public VendorVerifiedEvent {
            Objects.requireNonNull(vendorId, "vendorId");
            Objects.requireNonNull(verificationId, "verificationId");
            Objects.requireNonNull(verificationLevel, "verificationLevel");
        }
    }

    /**
     * Vendor suspended event. Triggered when vendor violates marketplace policies.
     * Idempotency key: (vendorId, suspensionId) pair.
     */
    public record VendorSuspendedEvent(
        @JsonProperty("vendor_id") String vendorId,
        @JsonProperty("suspension_id") String suspensionId,
        @JsonProperty("reason") String reason,
        @JsonProperty("suspended_timestamp_utc") String suspendedTimestampUtc,
        @JsonProperty("appeal_deadline_utc") String appealDeadlineUtc,
        @JsonProperty("sequence_number") long sequenceNumber,
        @JsonProperty("metadata") Map<String, String> metadata
    ) {
        public VendorSuspendedEvent {
            Objects.requireNonNull(vendorId, "vendorId");
            Objects.requireNonNull(suspensionId, "suspensionId");
            Objects.requireNonNull(reason, "reason");
        }
    }

    // =========================================================================
    // Payment Events
    // =========================================================================

    /**
     * Payment authorized event. Triggered when payment gateway authorizes charge.
     * Idempotency key: (orderId, authorizationId) pair.
     */
    public record PaymentAuthorizedEvent(
        @JsonProperty("order_id") String orderId,
        @JsonProperty("authorization_id") String authorizationId,
        @JsonProperty("amount_cents") long amountCents,
        @JsonProperty("currency") String currency,
        @JsonProperty("payment_method") String paymentMethod,
        @JsonProperty("authorized_timestamp_utc") String authorizedTimestampUtc,
        @JsonProperty("expiration_timestamp_utc") String expirationTimestampUtc,
        @JsonProperty("sequence_number") long sequenceNumber,
        @JsonProperty("metadata") Map<String, String> metadata
    ) {
        public PaymentAuthorizedEvent {
            Objects.requireNonNull(orderId, "orderId");
            Objects.requireNonNull(authorizationId, "authorizationId");
            Objects.requireNonNull(currency, "currency");
        }
    }

    /**
     * Payment captured event. Triggered when payment is captured (settled).
     * Idempotency key: (authorizationId, captureId) pair.
     */
    public record PaymentCapturedEvent(
        @JsonProperty("authorization_id") String authorizationId,
        @JsonProperty("capture_id") String captureId,
        @JsonProperty("amount_cents") long amountCents,
        @JsonProperty("captured_timestamp_utc") String capturedTimestampUtc,
        @JsonProperty("settlement_window_hours") int settlementWindowHours,
        @JsonProperty("sequence_number") long sequenceNumber,
        @JsonProperty("metadata") Map<String, String> metadata
    ) {
        public PaymentCapturedEvent {
            Objects.requireNonNull(authorizationId, "authorizationId");
            Objects.requireNonNull(captureId, "captureId");
        }
    }

    /**
     * Payment failed event. Triggered when payment authorization/capture fails.
     * Idempotency key: (orderId, failureId, failureTimestamp) triple.
     * Retryable: Yes, depends on failure reason code.
     */
    public record PaymentFailedEvent(
        @JsonProperty("order_id") String orderId,
        @JsonProperty("failure_id") String failureId,
        @JsonProperty("failure_reason") String failureReason,
        @JsonProperty("failure_code") String failureCode,
        @JsonProperty("failed_timestamp_utc") String failedTimestampUtc,
        @JsonProperty("retryable") boolean retryable,
        @JsonProperty("retry_after_seconds") int retryAfterSeconds,
        @JsonProperty("sequence_number") long sequenceNumber,
        @JsonProperty("metadata") Map<String, String> metadata
    ) {
        public PaymentFailedEvent {
            Objects.requireNonNull(orderId, "orderId");
            Objects.requireNonNull(failureId, "failureId");
            Objects.requireNonNull(failureCode, "failureCode");
        }
    }

    /**
     * Payout initiated event. Triggered when marketplace initiates payout to vendor.
     * Idempotency key: (vendorId, payoutId) pair.
     */
    public record PayoutInitiatedEvent(
        @JsonProperty("vendor_id") String vendorId,
        @JsonProperty("payout_id") String payoutId,
        @JsonProperty("amount_cents") long amountCents,
        @JsonProperty("currency") String currency,
        @JsonProperty("period_start_utc") String periodStartUtc,
        @JsonProperty("period_end_utc") String periodEndUtc,
        @JsonProperty("initiated_timestamp_utc") String initiatedTimestampUtc,
        @JsonProperty("expected_settlement_date") String expectedSettlementDate,
        @JsonProperty("sequence_number") long sequenceNumber,
        @JsonProperty("metadata") Map<String, String> metadata
    ) {
        public PayoutInitiatedEvent {
            Objects.requireNonNull(vendorId, "vendorId");
            Objects.requireNonNull(payoutId, "payoutId");
            Objects.requireNonNull(currency, "currency");
        }
    }

    // =========================================================================
    // Event Base Type (for routing and processing)
    // =========================================================================

    /**
     * Event metadata carrier with ordering guarantees.
     *
     * Ensures message ordering and idempotency via:
     * - sequenceNumber: monotonic increasing within stream
     * - idempotencyKey: unique identifier for deduplication
     * - timestamp: causality tracking
     */
    public record EventEnvelope(
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("event") Object eventData,
        @JsonProperty("sequence_number") long sequenceNumber,
        @JsonProperty("idempotency_key") String idempotencyKey,
        @JsonProperty("timestamp_utc") String timestampUtc,
        @JsonProperty("source_agent") String sourceAgent,
        @JsonProperty("source_region") String sourceRegion
    ) {
        public EventEnvelope {
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(eventType, "eventType");
            Objects.requireNonNull(idempotencyKey, "idempotencyKey");
            Objects.requireNonNull(timestampUtc, "timestampUtc");
        }
    }

    /**
     * Prevent instantiation.
     */
    private MarketplaceEventSchema() {
        throw new UnsupportedOperationException("Utility class");
    }
}
