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

package org.yawlfoundation.yawl.integration.fmea;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.yawlfoundation.yawl.integration.fmea.MarketplaceFmeaAnalyzer.NO_PRIOR_SEQUENCE;

/**
 * Chicago TDD tests for {@link MarketplaceFmeaAnalyzer}.
 *
 * <p>Real objects only — no mocks. Each nested class covers one analyzer method.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@DisplayName("MarketplaceFmeaAnalyzer")
class MarketplaceFmeaAnalyzerTest {

    private final MarketplaceFmeaAnalyzer analyzer = new MarketplaceFmeaAnalyzer();

    private static final Set<String> KNOWN_TYPES = Set.of(
        "OrderCreatedEvent", "OrderConfirmedEvent", "OrderShippedEvent",
        "OrderDeliveredEvent", "OrderReturnedEvent",
        "VendorOnboardedEvent", "VendorVerifiedEvent", "VendorSuspendedEvent",
        "PaymentAuthorizedEvent", "PaymentCapturedEvent",
        "PaymentFailedEvent", "PayoutInitiatedEvent"
    );

    private static final String ENGINE_URL = "http://localhost:8080/yawl";

    // -----------------------------------------------------------------------
    // analyzeEventEnvelope — FM_E1, FM_E2, FM_E3, FM_E4
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("analyzeEventEnvelope")
    class AnalyzeEventEnvelope {

        @Test
        @DisplayName("sequence <= lastSeen → FM_E1 out-of-order violation")
        void outOfOrder_fm_e1() {
            MarketplaceFmeaReport report = analyzer.analyzeEventEnvelope(
                "EVT-1", "OrderCreatedEvent", "key-1",
                /*sequenceNumber=*/ 5L,
                "vendor-agent-1",
                Set.of(),
                /*lastSeenSequence=*/ 5L,   // same → out of order
                KNOWN_TYPES);

            assertAll(
                () -> assertFalse(report.isClean()),
                () -> assertEquals(1, report.violations().size()),
                () -> assertEquals(MarketplaceFailureModeType.FM_E1_EVENT_OUT_OF_ORDER,
                                   report.violations().get(0).mode())
            );
        }

        @Test
        @DisplayName("sequence < lastSeen → FM_E1 late delivery")
        void lateDelivery_fm_e1() {
            MarketplaceFmeaReport report = analyzer.analyzeEventEnvelope(
                "EVT-2", "OrderShippedEvent", "key-2",
                3L, "vendor-agent-1", Set.of(), 7L, KNOWN_TYPES);

            assertTrue(report.violations().stream()
                .anyMatch(v -> v.mode() == MarketplaceFailureModeType.FM_E1_EVENT_OUT_OF_ORDER));
        }

        @Test
        @DisplayName("first event (NO_PRIOR_SEQUENCE) → no FM_E1")
        void firstEvent_noFm_e1() {
            MarketplaceFmeaReport report = analyzer.analyzeEventEnvelope(
                "EVT-3", "OrderCreatedEvent", "key-3",
                1L, "vendor-agent-1", Set.of(), NO_PRIOR_SEQUENCE, KNOWN_TYPES);

            assertTrue(report.violations().stream()
                .noneMatch(v -> v.mode() == MarketplaceFailureModeType.FM_E1_EVENT_OUT_OF_ORDER));
        }

        @Test
        @DisplayName("idempotency key already processed → FM_E2 duplicate violation")
        void duplicateEvent_fm_e2() {
            MarketplaceFmeaReport report = analyzer.analyzeEventEnvelope(
                "EVT-4", "OrderCreatedEvent", "order-42-create",
                2L, "vendor-agent-1",
                Set.of("order-42-create"),   // already processed
                1L, KNOWN_TYPES);

            assertAll(
                () -> assertFalse(report.isClean()),
                () -> assertTrue(report.violations().stream()
                    .anyMatch(v -> v.mode() == MarketplaceFailureModeType.FM_E2_DUPLICATE_EVENT))
            );
        }

        @Test
        @DisplayName("fresh idempotency key → no FM_E2")
        void freshKey_noFm_e2() {
            MarketplaceFmeaReport report = analyzer.analyzeEventEnvelope(
                "EVT-5", "OrderCreatedEvent", "order-99-create",
                2L, "vendor-agent-1",
                Set.of("order-42-create"),   // different key
                1L, KNOWN_TYPES);

            assertTrue(report.violations().stream()
                .noneMatch(v -> v.mode() == MarketplaceFailureModeType.FM_E2_DUPLICATE_EVENT));
        }

        @Test
        @DisplayName("unknown event type → FM_E3 violation")
        void unknownEventType_fm_e3() {
            MarketplaceFmeaReport report = analyzer.analyzeEventEnvelope(
                "EVT-6", "PricingUpdatedEvent", "key-6",
                2L, "vendor-agent-1", Set.of(), 1L, KNOWN_TYPES);

            assertAll(
                () -> assertFalse(report.isClean()),
                () -> assertTrue(report.violations().stream()
                    .anyMatch(v -> v.mode() == MarketplaceFailureModeType.FM_E3_UNKNOWN_EVENT_TYPE))
            );
        }

        @Test
        @DisplayName("known event type → no FM_E3")
        void knownEventType_noFm_e3() {
            MarketplaceFmeaReport report = analyzer.analyzeEventEnvelope(
                "EVT-7", "OrderCreatedEvent", "key-7",
                2L, "vendor-agent-1", Set.of(), 1L, KNOWN_TYPES);

            assertTrue(report.violations().stream()
                .noneMatch(v -> v.mode() == MarketplaceFailureModeType.FM_E3_UNKNOWN_EVENT_TYPE));
        }

        @Test
        @DisplayName("sequence gap (seq > lastSeen + 1) → FM_E4 gap violation")
        void sequenceGap_fm_e4() {
            MarketplaceFmeaReport report = analyzer.analyzeEventEnvelope(
                "EVT-8", "OrderShippedEvent", "key-8",
                /*seq=*/ 10L, "vendor-agent-1", Set.of(),
                /*lastSeen=*/ 5L,   // gap: expected 6, got 10
                KNOWN_TYPES);

            assertAll(
                () -> assertFalse(report.isClean()),
                () -> assertTrue(report.violations().stream()
                    .anyMatch(v -> v.mode() == MarketplaceFailureModeType.FM_E4_SEQUENCE_GAP))
            );
        }

        @Test
        @DisplayName("next-in-sequence event → no FM_E4")
        void nextInSequence_noFm_e4() {
            MarketplaceFmeaReport report = analyzer.analyzeEventEnvelope(
                "EVT-9", "OrderShippedEvent", "key-9",
                6L, "vendor-agent-1", Set.of(), 5L, KNOWN_TYPES);

            assertTrue(report.violations().stream()
                .noneMatch(v -> v.mode() == MarketplaceFailureModeType.FM_E4_SEQUENCE_GAP));
        }

        @Test
        @DisplayName("first event with gap-check skipped (NO_PRIOR_SEQUENCE) → no FM_E4")
        void firstEvent_noFm_e4() {
            MarketplaceFmeaReport report = analyzer.analyzeEventEnvelope(
                "EVT-10", "OrderCreatedEvent", "key-10",
                100L, "vendor-agent-1", Set.of(), NO_PRIOR_SEQUENCE, KNOWN_TYPES);

            assertTrue(report.violations().stream()
                .noneMatch(v -> v.mode() == MarketplaceFailureModeType.FM_E4_SEQUENCE_GAP));
        }

        @Test
        @DisplayName("clean envelope → GREEN")
        void cleanEnvelope_green() {
            MarketplaceFmeaReport report = analyzer.analyzeEventEnvelope(
                "EVT-OK", "OrderCreatedEvent", "fresh-key",
                6L, "vendor-agent-1", Set.of(), 5L, KNOWN_TYPES);

            assertAll(
                () -> assertTrue(report.isClean()),
                () -> assertEquals("GREEN", report.status()),
                () -> assertEquals(0, report.totalRpn())
            );
        }

        @Test
        @DisplayName("null eventId → NullPointerException")
        void nullEventId_throws() {
            assertThrows(NullPointerException.class,
                () -> analyzer.analyzeEventEnvelope(
                    null, "OrderCreatedEvent", "key", 1L, "agent", Set.of(), 0L, KNOWN_TYPES));
        }

        @Test
        @DisplayName("null knownEventTypes → NullPointerException")
        void nullKnownTypes_throws() {
            assertThrows(NullPointerException.class,
                () -> analyzer.analyzeEventEnvelope(
                    "EVT-1", "OrderCreatedEvent", "key", 1L, "agent", Set.of(), 0L, null));
        }
    }

    // -----------------------------------------------------------------------
    // analyzePaymentEvent — FM_E5
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("analyzePaymentEvent")
    class AnalyzePaymentEvent {

        @Test
        @DisplayName("non-blank failureCode → FM_E5 payment failure")
        void paymentFailed_fm_e5() {
            MarketplaceFmeaReport report = analyzer.analyzePaymentEvent(
                "order-42", "INSUFFICIENT_FUNDS", false, 0);

            assertAll(
                () -> assertFalse(report.isClean()),
                () -> assertEquals(1, report.violations().size()),
                () -> assertEquals(MarketplaceFailureModeType.FM_E5_PAYMENT_FAILURE,
                                   report.violations().get(0).mode())
            );
        }

        @Test
        @DisplayName("retryable failure → FM_E5 with retryAfterSeconds in evidence")
        void retryableFailure_evidenceIncludesRetry() {
            MarketplaceFmeaReport report = analyzer.analyzePaymentEvent(
                "order-42", "CARD_BLOCKED", true, 300);

            String evidence = report.violations().get(0).evidence();
            assertTrue(evidence.contains("retryAfterSeconds=300"));
        }

        @Test
        @DisplayName("null failureCode → no FM_E5 (payment succeeded)")
        void nullFailureCode_noFm_e5() {
            MarketplaceFmeaReport report = analyzer.analyzePaymentEvent(
                "order-42", null, false, 0);

            assertTrue(report.isClean());
        }

        @Test
        @DisplayName("blank failureCode → no FM_E5")
        void blankFailureCode_noFm_e5() {
            MarketplaceFmeaReport report = analyzer.analyzePaymentEvent(
                "order-42", "  ", false, 0);

            assertTrue(report.isClean());
        }

        @Test
        @DisplayName("successful payment → GREEN")
        void paymentSuccess_green() {
            MarketplaceFmeaReport report = analyzer.analyzePaymentEvent(
                "order-99", null, false, 0);

            assertAll(
                () -> assertTrue(report.isClean()),
                () -> assertEquals("GREEN", report.status())
            );
        }

        @Test
        @DisplayName("null orderId → NullPointerException")
        void nullOrderId_throws() {
            assertThrows(NullPointerException.class,
                () -> analyzer.analyzePaymentEvent(null, "FAIL", false, 0));
        }
    }

    // -----------------------------------------------------------------------
    // analyzeVendorStatus — FM_E6
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("analyzeVendorStatus")
    class AnalyzeVendorStatus {

        @Test
        @DisplayName("vendor suspended → FM_E6 violation")
        void vendorSuspended_fm_e6() {
            MarketplaceFmeaReport report = analyzer.analyzeVendorStatus(
                "vendor-42", true, "Policy violation: counterfeit goods");

            assertAll(
                () -> assertFalse(report.isClean()),
                () -> assertEquals(1, report.violations().size()),
                () -> assertEquals(MarketplaceFailureModeType.FM_E6_VENDOR_SUSPENDED,
                                   report.violations().get(0).mode())
            );
        }

        @Test
        @DisplayName("vendor not suspended → no FM_E6")
        void vendorActive_noFm_e6() {
            MarketplaceFmeaReport report = analyzer.analyzeVendorStatus(
                "vendor-42", false, null);

            assertTrue(report.isClean());
        }

        @Test
        @DisplayName("suspended with null reason → violation with 'unspecified' in evidence")
        void suspendedNullReason_evidenceUnspecified() {
            MarketplaceFmeaReport report = analyzer.analyzeVendorStatus(
                "vendor-42", true, null);

            assertTrue(report.violations().get(0).evidence().contains("unspecified"));
        }

        @Test
        @DisplayName("active vendor → GREEN")
        void activeVendor_green() {
            MarketplaceFmeaReport report = analyzer.analyzeVendorStatus(
                "vendor-99", false, null);

            assertAll(
                () -> assertTrue(report.isClean()),
                () -> assertEquals("GREEN", report.status())
            );
        }

        @Test
        @DisplayName("null vendorId → NullPointerException")
        void nullVendorId_throws() {
            assertThrows(NullPointerException.class,
                () -> analyzer.analyzeVendorStatus(null, true, "reason"));
        }
    }

    // -----------------------------------------------------------------------
    // analyzeEngineConnectivity — FM_E7
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("analyzeEngineConnectivity")
    class AnalyzeEngineConnectivity {

        @Test
        @DisplayName("sessionHandle contains <failure> → FM_E7 violation")
        void failureSessionHandle_fm_e7() {
            MarketplaceFmeaReport report = analyzer.analyzeEngineConnectivity(
                "<failure>Session expired</failure>", ENGINE_URL);

            assertAll(
                () -> assertFalse(report.isClean()),
                () -> assertEquals(1, report.violations().size()),
                () -> assertEquals(MarketplaceFailureModeType.FM_E7_ENGINE_SESSION_EXPIRED,
                                   report.violations().get(0).mode())
            );
        }

        @Test
        @DisplayName("null sessionHandle → FM_E7 violation")
        void nullSessionHandle_fm_e7() {
            MarketplaceFmeaReport report = analyzer.analyzeEngineConnectivity(
                null, ENGINE_URL);

            assertFalse(report.isClean());
            assertEquals(MarketplaceFailureModeType.FM_E7_ENGINE_SESSION_EXPIRED,
                         report.violations().get(0).mode());
        }

        @Test
        @DisplayName("blank sessionHandle → FM_E7 violation")
        void blankSessionHandle_fm_e7() {
            MarketplaceFmeaReport report = analyzer.analyzeEngineConnectivity(
                "   ", ENGINE_URL);

            assertFalse(report.isClean());
        }

        @Test
        @DisplayName("valid sessionHandle → no FM_E7")
        void validSession_noFm_e7() {
            MarketplaceFmeaReport report = analyzer.analyzeEngineConnectivity(
                "valid-session-abc123", ENGINE_URL);

            assertTrue(report.isClean());
        }

        @Test
        @DisplayName("valid session → GREEN")
        void validSession_green() {
            MarketplaceFmeaReport report = analyzer.analyzeEngineConnectivity(
                "session-xyz", ENGINE_URL);

            assertAll(
                () -> assertTrue(report.isClean()),
                () -> assertEquals("GREEN", report.status()),
                () -> assertEquals(0, report.totalRpn())
            );
        }

        @Test
        @DisplayName("null engineUrl → NullPointerException")
        void nullEngineUrl_throws() {
            assertThrows(NullPointerException.class,
                () -> analyzer.analyzeEngineConnectivity("session", null));
        }
    }

    // -----------------------------------------------------------------------
    // FailureModeTypeRpn
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("FailureModeTypeRpn")
    class FailureModeTypeRpn {

        @Test
        @DisplayName("all RPN values are positive")
        void allRpnsPositive() {
            for (MarketplaceFailureModeType mode : MarketplaceFailureModeType.values()) {
                assertTrue(mode.rpn() > 0,
                    "Expected positive RPN for " + mode.name() + " but got " + mode.rpn());
            }
        }

        @Test
        @DisplayName("RPN = Severity × Occurrence × Detection")
        void rpnFormula() {
            for (MarketplaceFailureModeType mode : MarketplaceFailureModeType.values()) {
                int expected = mode.getSeverity() * mode.getOccurrence() * mode.getDetection();
                assertEquals(expected, mode.rpn(),
                    "RPN mismatch for " + mode.name());
            }
        }

        @Test
        @DisplayName("all descriptions and mitigations are non-blank")
        void descriptionsAndMitigationsNonBlank() {
            for (MarketplaceFailureModeType mode : MarketplaceFailureModeType.values()) {
                assertAll(
                    () -> assertFalse(mode.getDescription().isBlank(),
                        "description blank for " + mode.name()),
                    () -> assertFalse(mode.getMitigation().isBlank(),
                        "mitigation blank for " + mode.name())
                );
            }
        }

        @Test
        @DisplayName("FM_E4 sequence gap has RPN = 96")
        void fm_e4_rpn() {
            assertEquals(96, MarketplaceFailureModeType.FM_E4_SEQUENCE_GAP.rpn());
        }

        @Test
        @DisplayName("FM_E5 payment failure has RPN = 72")
        void fm_e5_rpn() {
            assertEquals(72, MarketplaceFailureModeType.FM_E5_PAYMENT_FAILURE.rpn());
        }
    }

    // -----------------------------------------------------------------------
    // ReportBehaviour
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("ReportBehaviour")
    class ReportBehaviour {

        @Test
        @DisplayName("violations list is immutable")
        void violationsList_isImmutable() {
            MarketplaceFmeaReport report = analyzer.analyzePaymentEvent(
                "order-1", "DECLINED", false, 0);
            assertThrows(UnsupportedOperationException.class,
                () -> report.violations().clear());
        }

        @Test
        @DisplayName("status GREEN when no violations")
        void statusGreen_noViolations() {
            MarketplaceFmeaReport report = analyzer.analyzeVendorStatus(
                "vendor-1", false, null);
            assertEquals("GREEN", report.status());
        }

        @Test
        @DisplayName("status RED when any violation")
        void statusRed_anyViolation() {
            MarketplaceFmeaReport report = analyzer.analyzeVendorStatus(
                "vendor-1", true, "fraud");
            assertEquals("RED", report.status());
        }

        @Test
        @DisplayName("totalRpn sums violation RPNs")
        void totalRpn_sumOfViolationRpns() {
            // FM_E5 + FM_E7 combined by separate reports — test single report with one violation
            MarketplaceFmeaReport report = analyzer.analyzePaymentEvent(
                "order-1", "CARD_BLOCKED", false, 0);
            assertEquals(MarketplaceFailureModeType.FM_E5_PAYMENT_FAILURE.rpn(),
                         report.totalRpn());
        }

        @Test
        @DisplayName("totalRpn = 0 when clean")
        void totalRpn_zeroWhenClean() {
            MarketplaceFmeaReport report = analyzer.analyzeEngineConnectivity(
                "valid-session", ENGINE_URL);
            assertEquals(0, report.totalRpn());
        }
    }
}
