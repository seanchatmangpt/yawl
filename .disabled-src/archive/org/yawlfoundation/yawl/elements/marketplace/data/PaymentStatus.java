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

package org.yawlfoundation.yawl.elements.marketplace.data;

import java.math.BigDecimal;
import java.util.*;

/**
 * Immutable payment status data element for GCP Marketplace.
 *
 * <p>Tracks payment authorization, capture, and refund status throughout
 * order lifecycle. Integrates with payment gateway and reconciliation workflows.</p>
 *
 * @author YAWL Marketplace Extension
 * @since 6.0.0
 */
public record PaymentStatus(
        String paymentId,
        String orderId,
        String paymentMethod,
        String paymentMethodType,
        BigDecimal authorizedAmount,
        BigDecimal capturedAmount,
        BigDecimal refundedAmount,
        String authorizationStatus,
        String captureStatus,
        String refundStatus,
        String authorizationCode,
        long authorizationTime,
        long captureTime,
        long refundTime,
        String refundReason,
        String errorMessage,
        Map<String, String> gatewayMetadata
) {

    /**
     * Authorization status constants
     */
    public static final String AUTH_PENDING = "PENDING";
    public static final String AUTH_AUTHORIZED = "AUTHORIZED";
    public static final String AUTH_FAILED = "FAILED";
    public static final String AUTH_DECLINED = "DECLINED";
    public static final String AUTH_EXPIRED = "EXPIRED";

    /**
     * Capture status constants
     */
    public static final String CAPTURE_PENDING = "PENDING";
    public static final String CAPTURE_CAPTURED = "CAPTURED";
    public static final String CAPTURE_FAILED = "FAILED";
    public static final String CAPTURE_VOIDED = "VOIDED";

    /**
     * Refund status constants
     */
    public static final String REFUND_NONE = "NONE";
    public static final String REFUND_PENDING = "PENDING";
    public static final String REFUND_COMPLETED = "COMPLETED";
    public static final String REFUND_FAILED = "FAILED";
    public static final String REFUND_PARTIAL = "PARTIAL";

    /**
     * Compact constructor for validation.
     */
    public PaymentStatus {
        Objects.requireNonNull(paymentId, "Payment ID cannot be null");
        Objects.requireNonNull(orderId, "Order ID cannot be null");
        Objects.requireNonNull(authorizedAmount, "Authorized amount cannot be null");
        if (authorizedAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Authorized amount cannot be negative");
        }
        gatewayMetadata = gatewayMetadata != null ? new HashMap<>(gatewayMetadata) : new HashMap<>();
    }

    /**
     * Creates a PaymentStatus builder for fluent construction.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder class for PaymentStatus.
     */
    public static class Builder {
        private String paymentId;
        private String orderId;
        private String paymentMethod;
        private String paymentMethodType;
        private BigDecimal authorizedAmount;
        private BigDecimal capturedAmount = BigDecimal.ZERO;
        private BigDecimal refundedAmount = BigDecimal.ZERO;
        private String authorizationStatus = AUTH_PENDING;
        private String captureStatus = CAPTURE_PENDING;
        private String refundStatus = REFUND_NONE;
        private String authorizationCode;
        private long authorizationTime = 0;
        private long captureTime = 0;
        private long refundTime = 0;
        private String refundReason;
        private String errorMessage;
        private Map<String, String> gatewayMetadata = new HashMap<>();

        public Builder paymentId(String paymentId) {
            this.paymentId = paymentId;
            return this;
        }

        public Builder orderId(String orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder paymentMethod(String method) {
            this.paymentMethod = method;
            return this;
        }

        public Builder paymentMethodType(String type) {
            this.paymentMethodType = type;
            return this;
        }

        public Builder authorizedAmount(BigDecimal amount) {
            this.authorizedAmount = amount;
            return this;
        }

        public Builder capturedAmount(BigDecimal amount) {
            this.capturedAmount = amount;
            return this;
        }

        public Builder refundedAmount(BigDecimal amount) {
            this.refundedAmount = amount;
            return this;
        }

        public Builder authorizationStatus(String status) {
            this.authorizationStatus = status;
            return this;
        }

        public Builder authorizationCode(String code) {
            this.authorizationCode = code;
            if (code != null) {
                this.authorizationTime = System.currentTimeMillis();
                this.authorizationStatus = AUTH_AUTHORIZED;
            }
            return this;
        }

        public Builder captureStatus(String status) {
            this.captureStatus = status;
            if (CAPTURE_CAPTURED.equals(status)) {
                this.captureTime = System.currentTimeMillis();
            }
            return this;
        }

        public Builder refundStatus(String status) {
            this.refundStatus = status;
            if (!REFUND_NONE.equals(status)) {
                this.refundTime = System.currentTimeMillis();
            }
            return this;
        }

        public Builder refundReason(String reason) {
            this.refundReason = reason;
            return this;
        }

        public Builder errorMessage(String message) {
            this.errorMessage = message;
            return this;
        }

        public Builder gatewayMetadata(Map<String, String> metadata) {
            this.gatewayMetadata = new HashMap<>(metadata);
            return this;
        }

        public Builder gatewayMetadataEntry(String key, String value) {
            this.gatewayMetadata.put(key, value);
            return this;
        }

        public PaymentStatus build() {
            return new PaymentStatus(
                    paymentId, orderId, paymentMethod, paymentMethodType,
                    authorizedAmount, capturedAmount, refundedAmount,
                    authorizationStatus, captureStatus, refundStatus,
                    authorizationCode, authorizationTime, captureTime,
                    refundTime, refundReason, errorMessage, gatewayMetadata
            );
        }
    }

    /**
     * Checks if payment is authorized.
     *
     * @return true if authorization was successful
     */
    public boolean isAuthorized() {
        return AUTH_AUTHORIZED.equals(authorizationStatus);
    }

    /**
     * Checks if payment is captured.
     *
     * @return true if funds have been captured
     */
    public boolean isCaptured() {
        return CAPTURE_CAPTURED.equals(captureStatus);
    }

    /**
     * Calculates remaining balance after partial refund.
     *
     * @return amount not yet refunded
     */
    public BigDecimal getRemainingBalance() {
        return capturedAmount.subtract(refundedAmount);
    }

    /**
     * Checks if payment can be refunded.
     *
     * @return true if payment is captured and not fully refunded
     */
    public boolean canBeRefunded() {
        return isCaptured() && getRemainingBalance().compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Checks if payment is in final state.
     *
     * @return true if payment lifecycle is complete
     */
    public boolean isFinalized() {
        return (AUTH_FAILED.equals(authorizationStatus) ||
                AUTH_DECLINED.equals(authorizationStatus)) ||
                (isAuthorized() && isCaptured());
    }
}
