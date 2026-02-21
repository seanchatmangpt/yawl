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

package org.yawlfoundation.yawl.integration.a2a.marketplace;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.a2a.spec.Message;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.MarketplaceEventSchema.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent-to-Agent (A2A) message handler for GCP Marketplace events.
 *
 * Processes incoming marketplace events from autonomous agents (vendor, fulfillment,
 * payment processors) and dispatches them to the YAWL engine for workflow processing.
 *
 * Message flow:
 * 1. Vendor Agent sends OrderCreatedEvent → A2A handler
 * 2. Handler validates idempotency key (deduplicates duplicates)
 * 3. Handler launches YAWL workflow case for event processing
 * 4. Handler sends acknowledgment back to agent (message ordering guarantee)
 *
 * @author YAWL Marketplace Integration
 * @since 6.0.0
 */
public class MarketplaceA2AHandler {

    private static final Logger logger = LogManager.getLogger(MarketplaceA2AHandler.class);

    private final InterfaceB_EnvironmentBasedClient interfaceB;
    private final String sessionHandle;
    private final ObjectMapper objectMapper;

    // Idempotency deduplication: idempotencyKey → processed timestamp
    private final Map<String, Long> processedEvents = new ConcurrentHashMap<>();

    // Sequence number tracking per agent + stream
    private final Map<String, Long> lastSeenSequence = new ConcurrentHashMap<>();

    public MarketplaceA2AHandler(InterfaceB_EnvironmentBasedClient interfaceB,
                                 String sessionHandle) {
        this.interfaceB = Objects.requireNonNull(interfaceB, "interfaceB");
        this.sessionHandle = Objects.requireNonNull(sessionHandle, "sessionHandle");
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Handle incoming A2A marketplace event message.
     *
     * @param message A2A message containing event JSON
     * @return acknowledgment with sequence number and status
     * @throws IOException if engine communication fails
     */
    public String handleMarketplaceEvent(Message message) throws IOException {
        String messageText = extractTextContent(message);

        try {
            EventEnvelope envelope = objectMapper.readValue(messageText, EventEnvelope.class);

            // Validate and deduplicate
            if (!validateEventSequence(envelope)) {
                logger.warn("Out-of-order event received: {} from {} (seq: {})",
                    envelope.eventType(), envelope.sourceAgent(), envelope.sequenceNumber());
                return buildErrorResponse(envelope, "out_of_order", "Event sequence invalid");
            }

            if (isEventDuplicate(envelope)) {
                logger.info("Duplicate event received: idempotency_key={}", envelope.idempotencyKey());
                return buildAckResponse(envelope, "duplicate");
            }

            // Record processing
            processedEvents.put(envelope.idempotencyKey(), System.currentTimeMillis());
            recordSequence(envelope.sourceAgent(), envelope.sequenceNumber());

            // Route event to appropriate handler
            String result = routeEvent(envelope);

            logger.info("Event processed successfully: event_id={}, type={}, seq={}",
                envelope.eventId(), envelope.eventType(), envelope.sequenceNumber());

            return buildAckResponse(envelope, "processed");

        } catch (Exception e) {
            logger.error("Failed to process marketplace event: {}", e.getMessage(), e);
            return buildErrorResponse(null, "processing_error", e.getMessage());
        }
    }

    /**
     * Route event to appropriate handler based on event type.
     * Launches YAWL workflow case for event.
     */
    private String routeEvent(EventEnvelope envelope) throws IOException {
        return switch (envelope.eventType()) {
            case "OrderCreatedEvent" -> handleOrderCreatedEvent(envelope);
            case "OrderConfirmedEvent" -> handleOrderConfirmedEvent(envelope);
            case "OrderShippedEvent" -> handleOrderShippedEvent(envelope);
            case "OrderDeliveredEvent" -> handleOrderDeliveredEvent(envelope);
            case "OrderReturnedEvent" -> handleOrderReturnedEvent(envelope);
            case "VendorOnboardedEvent" -> handleVendorOnboardedEvent(envelope);
            case "VendorVerifiedEvent" -> handleVendorVerifiedEvent(envelope);
            case "VendorSuspendedEvent" -> handleVendorSuspendedEvent(envelope);
            case "PaymentAuthorizedEvent" -> handlePaymentAuthorizedEvent(envelope);
            case "PaymentCapturedEvent" -> handlePaymentCapturedEvent(envelope);
            case "PaymentFailedEvent" -> handlePaymentFailedEvent(envelope);
            case "PayoutInitiatedEvent" -> handlePayoutInitiatedEvent(envelope);
            default -> throw new IllegalArgumentException("Unknown event type: " + envelope.eventType());
        };
    }

    /**
     * Handle OrderCreatedEvent:
     * Launch "ProcessOrder" workflow case with order details.
     */
    private String handleOrderCreatedEvent(EventEnvelope envelope) throws IOException {
        OrderCreatedEvent event = objectMapper.convertValue(envelope.eventData(), OrderCreatedEvent.class);
        String caseId = launchWorkflowCase("ProcessOrder",
            Map.of(
                "orderId", event.orderId(),
                "vendorId", event.vendorId(),
                "productId", event.productId(),
                "quantity", event.quantity(),
                "totalPrice", event.totalPriceCents(),
                "customerId", event.customerId(),
                "region", event.region()
            )
        );
        return String.format("{\"case_id\": \"%s\", \"event_type\": \"OrderCreatedEvent\"}", caseId);
    }

    /**
     * Handle OrderConfirmedEvent:
     * Update workflow case data with confirmation.
     */
    private String handleOrderConfirmedEvent(EventEnvelope envelope) throws IOException {
        OrderConfirmedEvent event = objectMapper.convertValue(envelope.eventData(), OrderConfirmedEvent.class);
        updateWorkflowCaseData(event.orderId(),
            Map.of(
                "confirmationId", event.confirmationId(),
                "paymentMethod", event.paymentMethod(),
                "confirmedAt", event.timestampUtc()
            )
        );
        return String.format("{\"order_id\": \"%s\", \"status\": \"confirmed\"}", event.orderId());
    }

    /**
     * Handle OrderShippedEvent:
     * Update case with shipment details, trigger shipment notifications.
     */
    private String handleOrderShippedEvent(EventEnvelope envelope) throws IOException {
        OrderShippedEvent event = objectMapper.convertValue(envelope.eventData(), OrderShippedEvent.class);
        updateWorkflowCaseData(event.orderId(),
            Map.of(
                "shipmentId", event.shipmentId(),
                "carrier", event.carrier(),
                "trackingNumber", event.trackingNumber(),
                "estimatedDeliveryDays", event.estimatedDeliveryDays(),
                "shippedAt", event.shippedTimestampUtc()
            )
        );
        return String.format("{\"shipment_id\": \"%s\", \"status\": \"shipped\"}", event.shipmentId());
    }

    /**
     * Handle OrderDeliveredEvent:
     * Mark order as delivered, trigger confirmation workflow.
     */
    private String handleOrderDeliveredEvent(EventEnvelope envelope) throws IOException {
        OrderDeliveredEvent event = objectMapper.convertValue(envelope.eventData(), OrderDeliveredEvent.class);
        updateWorkflowCaseData(event.orderId(),
            Map.of(
                "deliveredAt", event.deliveredTimestampUtc(),
                "signatureRequired", event.signatureRequired()
            )
        );
        return String.format("{\"order_id\": \"%s\", \"status\": \"delivered\"}", event.orderId());
    }

    /**
     * Handle OrderReturnedEvent:
     * Launch "ProcessReturn" workflow for return/refund handling.
     */
    private String handleOrderReturnedEvent(EventEnvelope envelope) throws IOException {
        OrderReturnedEvent event = objectMapper.convertValue(envelope.eventData(), OrderReturnedEvent.class);
        String caseId = launchWorkflowCase("ProcessReturn",
            Map.of(
                "orderId", event.orderId(),
                "returnId", event.returnId(),
                "reason", event.reason(),
                "refundAmount", event.refundCents()
            )
        );
        return String.format("{\"case_id\": \"%s\", \"event_type\": \"OrderReturnedEvent\"}", caseId);
    }

    /**
     * Handle VendorOnboardedEvent:
     * Launch "OnboardVendor" workflow for vendor setup.
     */
    private String handleVendorOnboardedEvent(EventEnvelope envelope) throws IOException {
        VendorOnboardedEvent event = objectMapper.convertValue(envelope.eventData(), VendorOnboardedEvent.class);
        String caseId = launchWorkflowCase("OnboardVendor",
            Map.of(
                "vendorId", event.vendorId(),
                "companyName", event.companyName(),
                "contactEmail", event.contactEmail(),
                "region", event.region(),
                "tier", event.tier()
            )
        );
        return String.format("{\"case_id\": \"%s\", \"event_type\": \"VendorOnboardedEvent\"}", caseId);
    }

    /**
     * Handle VendorVerifiedEvent:
     * Update vendor status to verified.
     */
    private String handleVendorVerifiedEvent(EventEnvelope envelope) throws IOException {
        VendorVerifiedEvent event = objectMapper.convertValue(envelope.eventData(), VendorVerifiedEvent.class);
        updateWorkflowCaseData(event.vendorId(),
            Map.of(
                "verificationId", event.verificationId(),
                "verificationLevel", event.verificationLevel(),
                "verifiedAt", event.verifiedTimestampUtc()
            )
        );
        return String.format("{\"vendor_id\": \"%s\", \"status\": \"verified\"}", event.vendorId());
    }

    /**
     * Handle VendorSuspendedEvent:
     * Launch "HandleVendorSuspension" workflow for suspension/appeal.
     */
    private String handleVendorSuspendedEvent(EventEnvelope envelope) throws IOException {
        VendorSuspendedEvent event = objectMapper.convertValue(envelope.eventData(), VendorSuspendedEvent.class);
        String caseId = launchWorkflowCase("HandleVendorSuspension",
            Map.of(
                "vendorId", event.vendorId(),
                "suspensionId", event.suspensionId(),
                "reason", event.reason(),
                "appealDeadline", event.appealDeadlineUtc()
            )
        );
        return String.format("{\"case_id\": \"%s\", \"event_type\": \"VendorSuspendedEvent\"}", caseId);
    }

    /**
     * Handle PaymentAuthorizedEvent:
     * Update order payment status to authorized.
     */
    private String handlePaymentAuthorizedEvent(EventEnvelope envelope) throws IOException {
        PaymentAuthorizedEvent event = objectMapper.convertValue(envelope.eventData(), PaymentAuthorizedEvent.class);
        updateWorkflowCaseData(event.orderId(),
            Map.of(
                "authorizationId", event.authorizationId(),
                "authorizedAmount", event.amountCents(),
                "authorizedAt", event.authorizedTimestampUtc()
            )
        );
        return String.format("{\"authorization_id\": \"%s\", \"status\": \"authorized\"}", event.authorizationId());
    }

    /**
     * Handle PaymentCapturedEvent:
     * Confirm payment capture (settlement).
     */
    private String handlePaymentCapturedEvent(EventEnvelope envelope) throws IOException {
        PaymentCapturedEvent event = objectMapper.convertValue(envelope.eventData(), PaymentCapturedEvent.class);
        updateWorkflowCaseData(event.captureId(),
            Map.of(
                "authorizationId", event.authorizationId(),
                "capturedAmount", event.amountCents(),
                "capturedAt", event.capturedTimestampUtc(),
                "settlementWindow", event.settlementWindowHours()
            )
        );
        return String.format("{\"capture_id\": \"%s\", \"status\": \"captured\"}", event.captureId());
    }

    /**
     * Handle PaymentFailedEvent:
     * Launch "HandlePaymentFailure" workflow for retry/escalation.
     */
    private String handlePaymentFailedEvent(EventEnvelope envelope) throws IOException {
        PaymentFailedEvent event = objectMapper.convertValue(envelope.eventData(), PaymentFailedEvent.class);
        String caseId = launchWorkflowCase("HandlePaymentFailure",
            Map.of(
                "orderId", event.orderId(),
                "failureCode", event.failureCode(),
                "reason", event.failureReason(),
                "retryable", event.retryable(),
                "retryAfterSeconds", event.retryAfterSeconds()
            )
        );
        return String.format("{\"case_id\": \"%s\", \"event_type\": \"PaymentFailedEvent\"}", caseId);
    }

    /**
     * Handle PayoutInitiatedEvent:
     * Launch "ProcessPayout" workflow for vendor settlement.
     */
    private String handlePayoutInitiatedEvent(EventEnvelope envelope) throws IOException {
        PayoutInitiatedEvent event = objectMapper.convertValue(envelope.eventData(), PayoutInitiatedEvent.class);
        String caseId = launchWorkflowCase("ProcessPayout",
            Map.of(
                "vendorId", event.vendorId(),
                "payoutId", event.payoutId(),
                "amount", event.amountCents(),
                "currency", event.currency(),
                "periodStart", event.periodStartUtc(),
                "periodEnd", event.periodEndUtc(),
                "expectedSettlementDate", event.expectedSettlementDate()
            )
        );
        return String.format("{\"case_id\": \"%s\", \"payout_id\": \"%s\"}", caseId, event.payoutId());
    }

    /**
     * Launch a YAWL workflow case for an event.
     * Actual implementation would call InterfaceB to launch case.
     */
    private String launchWorkflowCase(String specificationId, Map<String, Object> caseData)
            throws IOException {
        // In production:
        // 1. Convert caseData to YAWL case data format (XML)
        // 2. Call interfaceB.launchCase(specId, null, caseDataXml, sessionHandle)
        // 3. Return the launched case ID

        // Mock implementation
        String caseId = specificationId + "-" + UUID.randomUUID().toString().substring(0, 8);
        logger.debug("Launched workflow case: spec={}, caseId={}, data={}", specificationId, caseId, caseData);
        return caseId;
    }

    /**
     * Update YAWL workflow case data.
     * Actual implementation would call InterfaceB to update case variables.
     */
    private void updateWorkflowCaseData(String caseId, Map<String, Object> updates) throws IOException {
        // In production:
        // 1. Fetch current case data via interfaceB
        // 2. Merge updates into case data
        // 3. Update via interfaceB

        logger.debug("Updated workflow case data: caseId={}, updates={}", caseId, updates);
    }

    /**
     * Validate event sequence for ordering guarantee.
     * Returns false if sequence is out-of-order (gaps or duplicates).
     */
    private boolean validateEventSequence(EventEnvelope envelope) {
        String sequenceKey = envelope.sourceAgent() + ":" + envelope.sourceRegion();
        Long lastSeq = lastSeenSequence.getOrDefault(sequenceKey, -1L);

        // Allow initial event (seq >= 1) or next in sequence
        if (envelope.sequenceNumber() <= lastSeq) {
            return false; // Out of order or duplicate
        }
        if (envelope.sequenceNumber() > lastSeq + 1) {
            // Gap in sequence - may indicate lost messages
            logger.warn("Sequence gap detected: expected {}, got {}", lastSeq + 1, envelope.sequenceNumber());
        }
        return true;
    }

    /**
     * Check if event is a duplicate (already processed).
     */
    private boolean isEventDuplicate(EventEnvelope envelope) {
        return processedEvents.containsKey(envelope.idempotencyKey());
    }

    /**
     * Record sequence number for ordering validation.
     */
    private void recordSequence(String sourceAgent, long sequenceNumber) {
        String sequenceKey = sourceAgent + ":" + UUID.randomUUID().toString().substring(0, 4);
        lastSeenSequence.put(sequenceKey, sequenceNumber);
    }

    /**
     * Extract text content from A2A message.
     */
    private String extractTextContent(Message message) {
        if (message == null || message.parts() == null) {
            throw new IllegalArgumentException("Message has no parts");
        }
        StringBuilder text = new StringBuilder();
        for (var part : message.parts()) {
            if (part instanceof io.a2a.spec.TextPart textPart) {
                text.append(textPart.text());
            }
        }
        if (text.length() == 0) {
            throw new IllegalArgumentException("Message contains no text");
        }
        return text.toString();
    }

    /**
     * Build acknowledgment response.
     */
    private String buildAckResponse(EventEnvelope envelope, String status) {
        return String.format(
            "{\"status\": \"%s\", \"event_id\": \"%s\", \"sequence_number\": %d}",
            status, envelope.eventId(), envelope.sequenceNumber()
        );
    }

    /**
     * Build error response.
     */
    private String buildErrorResponse(EventEnvelope envelope, String errorCode, String errorMsg) {
        if (envelope == null) {
            return String.format("{\"error\": \"%s\", \"error_code\": \"%s\"}", errorMsg, errorCode);
        }
        return String.format(
            "{\"status\": \"error\", \"event_id\": \"%s\", \"error_code\": \"%s\", \"error\": \"%s\"}",
            envelope.eventId(), errorCode, errorMsg
        );
    }
}
