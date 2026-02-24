package org.yawlfoundation.yawl.integration.autonomous.marketplace.events;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payment and transaction events from the payment processor agent.
 *
 * <p><b>Lifecycle</b>:
 * <ul>
 *   <li>PaymentAuthorized: Payment amount reserved on customer's payment method</li>
 *   <li>PaymentCaptured: Payment amount transferred to merchant account</li>
 *   <li>PaymentRefunded: Payment reversed (full or partial)</li>
 *   <li>PaymentFailed: Payment authorization/capture failed</li>
 * </ul>
 *
 * <p><b>Idempotency</b>: The combination of (agentId, idempotencyToken) uniquely
 * identifies a logical transaction. If a payment processor sends PaymentCaptured
 * twice with the same token, the second is a duplicate.
 *
 * <p><b>Message Ordering</b>: Events for the same transaction_id from the same
 * payment agent are delivered in order.
 *
 * @param eventType MUST be "PaymentAuthorized", "PaymentCaptured", "PaymentRefunded", or "PaymentFailed"
 * @param agentId unique identifier of the payment processor agent
 * @param idempotencyToken deduplication key (e.g. transaction_id:attempt_number)
 * @param timestamp when the event occurred in the payment system
 * @param version semantic version
 * @param metadata optional tracing data
 * @param transactionId unique payment transaction identifier
 * @param orderId associated order ID
 * @param status enum-like string: AUTHORIZED, CAPTURED, REFUNDED, FAILED
 * @param amount payment amount
 * @param currency ISO 4217 currency code
 * @param paymentMethod card, wallet, bank_transfer, etc
 * @param authorizationCode payment processor's authorization code (nullable until captured)
 * @param failureReason reason if failed (nullable)
 * @param refundAmount amount refunded if applicable (nullable)
 * @param processorResponse full response from payment processor (nullable)
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public record PaymentEvent(
    String eventType,
    String agentId,
    String idempotencyToken,
    Instant timestamp,
    String version,
    EventMetadata metadata,
    String transactionId,
    String orderId,
    String status,
    BigDecimal amount,
    String currency,
    String paymentMethod,
    String authorizationCode,
    String failureReason,
    BigDecimal refundAmount,
    String processorResponse
) implements MarketplaceEvent {

    /**
     * Canonical event type constants.
     */
    public static class EventTypes {
        public static final String PAYMENT_AUTHORIZED = "PaymentAuthorized";
        public static final String PAYMENT_CAPTURED = "PaymentCaptured";
        public static final String PAYMENT_REFUNDED = "PaymentRefunded";
        public static final String PAYMENT_FAILED = "PaymentFailed";
    }

    /**
     * Payment status constants.
     */
    public static class Status {
        public static final String AUTHORIZED = "AUTHORIZED";
        public static final String CAPTURED = "CAPTURED";
        public static final String REFUNDED = "REFUNDED";
        public static final String FAILED = "FAILED";
    }

    /**
     * Payment method constants.
     */
    public static class PaymentMethod {
        public static final String CREDIT_CARD = "CREDIT_CARD";
        public static final String DEBIT_CARD = "DEBIT_CARD";
        public static final String DIGITAL_WALLET = "DIGITAL_WALLET";
        public static final String BANK_TRANSFER = "BANK_TRANSFER";
    }

    /**
     * Construct a PaymentEvent with comprehensive validation.
     *
     * @throws IllegalArgumentException if required fields are missing or invalid
     */
    public PaymentEvent {
        // Validate base fields (delegated to sealed interface static helper)
        MarketplaceEvent.validateBaseFields(eventType, agentId, idempotencyToken, timestamp, version);

        // Validate payment-specific fields
        if (transactionId == null || transactionId.isBlank()) {
            throw new IllegalArgumentException("transactionId is required");
        }
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId is required");
        }
        if (status == null || status.isBlank()) {
            throw new IllegalArgumentException("status is required");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (currency == null || currency.length() != 3) {
            throw new IllegalArgumentException("currency must be ISO 4217 code (3 letters)");
        }
        if (paymentMethod == null || paymentMethod.isBlank()) {
            throw new IllegalArgumentException("paymentMethod is required");
        }

        // Validate consistency: status and optional fields
        if (Status.FAILED.equals(status)
                && (failureReason == null || failureReason.isBlank())) {
            throw new IllegalArgumentException(
                "failureReason is required for status " + status);
        }

        if (Status.REFUNDED.equals(status)
                && (refundAmount == null || refundAmount.signum() <= 0)) {
            throw new IllegalArgumentException(
                "refundAmount must be positive for status " + status);
        }
    }

    /**
     * Builder for fluent PaymentEvent construction.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for PaymentEvent.
     */
    public static class Builder {
        private String eventType = EventTypes.PAYMENT_AUTHORIZED;
        private String agentId;
        private String idempotencyToken;
        private Instant timestamp = Instant.now();
        private String version = "1.0";
        private EventMetadata metadata;
        private String transactionId;
        private String orderId;
        private String status = Status.AUTHORIZED;
        private BigDecimal amount;
        private String currency;
        private String paymentMethod;
        private String authorizationCode;
        private String failureReason;
        private BigDecimal refundAmount;
        private String processorResponse;

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

        public Builder transactionId(String transactionId) {
            this.transactionId = transactionId;
            return this;
        }

        public Builder orderId(String orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public Builder paymentMethod(String paymentMethod) {
            this.paymentMethod = paymentMethod;
            return this;
        }

        public Builder authorizationCode(String authorizationCode) {
            this.authorizationCode = authorizationCode;
            return this;
        }

        public Builder failureReason(String failureReason) {
            this.failureReason = failureReason;
            return this;
        }

        public Builder refundAmount(BigDecimal refundAmount) {
            this.refundAmount = refundAmount;
            return this;
        }

        public Builder processorResponse(String processorResponse) {
            this.processorResponse = processorResponse;
            return this;
        }

        public PaymentEvent build() {
            return new PaymentEvent(
                eventType, agentId, idempotencyToken, timestamp, version, metadata,
                transactionId, orderId, status, amount, currency, paymentMethod,
                authorizationCode, failureReason, refundAmount, processorResponse);
        }
    }

    /**
     * Check if payment is in a terminal state.
     */
    public boolean isTerminal() {
        return Status.CAPTURED.equals(status) || Status.FAILED.equals(status)
            || Status.REFUNDED.equals(status);
    }

    /**
     * Check if payment was successful.
     */
    public boolean isSuccessful() {
        return Status.CAPTURED.equals(status);
    }
}
