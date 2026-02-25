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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Stateless analyser that checks YAWL v6 GCP Marketplace event objects for FMEA
 * failure conditions across the end-to-end round-trip.
 *
 * <p>Each {@code analyze*} method evaluates one marketplace subject against the relevant
 * subset of {@link MarketplaceFailureModeType} failure modes and returns a
 * {@link MarketplaceFmeaReport}. A report with {@link MarketplaceFmeaReport#isClean()}
 * {@code true} means no violations were detected; {@code false} means at least one
 * failure mode fired and the caller should reject or log the event.
 *
 * <p>This class is intentionally stateless — instantiate once and reuse across
 * requests, or create per-request; both patterns are safe for concurrent use.
 *
 * <h2>Failure modes by method</h2>
 * <table>
 *   <tr><th>Method</th><th>Failure Modes Checked</th></tr>
 *   <tr><td>{@link #analyzeEventEnvelope}</td>
 *       <td>FM_E1 (out of order), FM_E2 (duplicate), FM_E3 (unknown type),
 *           FM_E4 (sequence gap)</td></tr>
 *   <tr><td>{@link #analyzePaymentEvent}</td>
 *       <td>FM_E5 (payment failure)</td></tr>
 *   <tr><td>{@link #analyzeVendorStatus}</td>
 *       <td>FM_E6 (vendor suspended)</td></tr>
 *   <tr><td>{@link #analyzeEngineConnectivity}</td>
 *       <td>FM_E7 (engine session expired)</td></tr>
 * </table>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * MarketplaceFmeaAnalyzer analyzer = new MarketplaceFmeaAnalyzer();
 *
 * MarketplaceFmeaReport report = analyzer.analyzeEventEnvelope(
 *     envelope.eventId(), envelope.eventType(), envelope.idempotencyKey(),
 *     envelope.sequenceNumber(), envelope.sourceAgent(),
 *     processedKeys, lastSeenSequence, KNOWN_EVENT_TYPES);
 * if (!report.isClean()) {
 *     throw new IllegalStateException("Marketplace FMEA " + report.status()
 *         + " RPN=" + report.totalRpn());
 * }
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public final class MarketplaceFmeaAnalyzer {

    /**
     * Sentinel string embedded in YAWL engine session handles when authentication fails.
     */
    private static final String ENGINE_FAILURE_MARKER = "<failure>";

    /**
     * Sequence number sentinel indicating no events have been seen yet for this stream.
     */
    public static final long NO_PRIOR_SEQUENCE = -1L;

    /**
     * Analyse a marketplace event envelope for ordering, deduplication, type routing,
     * and sequence integrity failures.
     *
     * <p>Checks performed:
     * <ul>
     *   <li><b>FM_E1</b> — fires when {@code sequenceNumber <= lastSeenSequence},
     *       meaning the event arrived out of order or is a duplicate by sequence</li>
     *   <li><b>FM_E2</b> — fires when {@code processedIdempotencyKeys} contains
     *       {@code idempotencyKey}, meaning this exact event was already processed</li>
     *   <li><b>FM_E3</b> — fires when {@code eventType} is not present in
     *       {@code knownEventTypes}, meaning no handler is registered for this event</li>
     *   <li><b>FM_E4</b> — fires when {@code sequenceNumber > lastSeenSequence + 1}
     *       (and {@code lastSeenSequence != NO_PRIOR_SEQUENCE}), meaning one or more
     *       events were never received for this source+region stream</li>
     * </ul>
     *
     * <p>Note: FM_E1 and FM_E4 are evaluated independently. An event can trigger FM_E4
     * (gap detected) without triggering FM_E1 (the event itself is in the correct
     * relative order, but prior events are missing).
     *
     * @param eventId                the unique event identifier (for evidence context);
     *                               must not be {@code null}
     * @param eventType              the type discriminator string (e.g. {@code "OrderCreatedEvent"});
     *                               must not be {@code null}
     * @param idempotencyKey         the idempotency key for deduplication; must not be {@code null}
     * @param sequenceNumber         the monotonically increasing sequence number from the source agent
     * @param sourceAgent            the agent identifier that emitted the event; must not be {@code null}
     * @param processedIdempotencyKeys the set of idempotency keys already successfully processed;
     *                               must not be {@code null}
     * @param lastSeenSequence       the highest sequence number previously accepted from this
     *                               source agent ({@link #NO_PRIOR_SEQUENCE} if this is the first)
     * @param knownEventTypes        the set of event type strings that have registered handlers;
     *                               must not be {@code null}
     * @return a {@link MarketplaceFmeaReport} with zero or more violations; never {@code null}
     */
    public MarketplaceFmeaReport analyzeEventEnvelope(String eventId,
                                                       String eventType,
                                                       String idempotencyKey,
                                                       long sequenceNumber,
                                                       String sourceAgent,
                                                       Set<String> processedIdempotencyKeys,
                                                       long lastSeenSequence,
                                                       Set<String> knownEventTypes) {
        Objects.requireNonNull(eventId,                 "eventId must not be null");
        Objects.requireNonNull(eventType,               "eventType must not be null");
        Objects.requireNonNull(idempotencyKey,          "idempotencyKey must not be null");
        Objects.requireNonNull(sourceAgent,             "sourceAgent must not be null");
        Objects.requireNonNull(processedIdempotencyKeys,"processedIdempotencyKeys must not be null");
        Objects.requireNonNull(knownEventTypes,         "knownEventTypes must not be null");

        List<MarketplaceFmeaViolation> violations = new ArrayList<>();
        String envelopeContext = "eventId=" + eventId
            + ", source=" + sourceAgent
            + ", type=" + eventType
            + ", seq=" + sequenceNumber;

        // FM_E1 — event out of order (seq <= lastSeen)
        if (lastSeenSequence != NO_PRIOR_SEQUENCE && sequenceNumber <= lastSeenSequence) {
            violations.add(new MarketplaceFmeaViolation(
                MarketplaceFailureModeType.FM_E1_EVENT_OUT_OF_ORDER,
                envelopeContext,
                "sequenceNumber=" + sequenceNumber
                    + " is <= lastSeenSequence=" + lastSeenSequence
                    + " for source=" + sourceAgent
            ));
        }

        // FM_E2 — duplicate event (idempotency key already seen)
        if (processedIdempotencyKeys.contains(idempotencyKey)) {
            violations.add(new MarketplaceFmeaViolation(
                MarketplaceFailureModeType.FM_E2_DUPLICATE_EVENT,
                envelopeContext,
                "idempotencyKey='" + idempotencyKey
                    + "' already present in the processed-events cache"
            ));
        }

        // FM_E3 — unknown event type (no handler registered)
        if (!knownEventTypes.contains(eventType)) {
            violations.add(new MarketplaceFmeaViolation(
                MarketplaceFailureModeType.FM_E3_UNKNOWN_EVENT_TYPE,
                envelopeContext,
                "eventType='" + eventType
                    + "' has no registered handler in the marketplace router"
            ));
        }

        // FM_E4 — sequence gap (events were lost upstream)
        if (lastSeenSequence != NO_PRIOR_SEQUENCE && sequenceNumber > lastSeenSequence + 1) {
            violations.add(new MarketplaceFmeaViolation(
                MarketplaceFailureModeType.FM_E4_SEQUENCE_GAP,
                envelopeContext,
                "sequence gap detected: expected=" + (lastSeenSequence + 1)
                    + ", got=" + sequenceNumber
                    + " (missing " + (sequenceNumber - lastSeenSequence - 1) + " event(s))"
            ));
        }

        return new MarketplaceFmeaReport(Instant.now(), violations);
    }

    /**
     * Analyse a payment failure event for revenue-blocking failure conditions.
     *
     * <p>Checks performed:
     * <ul>
     *   <li><b>FM_E5</b> — fires when {@code failureCode} is non-null and non-blank,
     *       meaning a payment was explicitly declined by the payment processor;
     *       the evidence includes whether the failure is retryable</li>
     * </ul>
     *
     * @param orderId            the order identifier associated with this payment;
     *                           must not be {@code null}
     * @param failureCode        the payment processor failure code (e.g. {@code "INSUFFICIENT_FUNDS"},
     *                           {@code "CARD_BLOCKED"}), or {@code null} if payment succeeded
     * @param retryable          {@code true} if the payment processor indicates a retry may succeed
     * @param retryAfterSeconds  the minimum seconds to wait before retrying (0 if not applicable)
     * @return a {@link MarketplaceFmeaReport} with zero or one violation; never {@code null}
     */
    public MarketplaceFmeaReport analyzePaymentEvent(String orderId,
                                                      String failureCode,
                                                      boolean retryable,
                                                      int retryAfterSeconds) {
        Objects.requireNonNull(orderId, "orderId must not be null");

        List<MarketplaceFmeaViolation> violations = new ArrayList<>();

        // FM_E5 — payment failure
        if (failureCode != null && !failureCode.isBlank()) {
            violations.add(new MarketplaceFmeaViolation(
                MarketplaceFailureModeType.FM_E5_PAYMENT_FAILURE,
                "orderId=" + orderId + ", failureCode=" + failureCode,
                "payment declined: failureCode='" + failureCode
                    + "', retryable=" + retryable
                    + (retryable ? ", retryAfterSeconds=" + retryAfterSeconds : "")
            ));
        }

        return new MarketplaceFmeaReport(Instant.now(), violations);
    }

    /**
     * Analyse a vendor status update for suspension failures that affect in-flight orders.
     *
     * <p>Checks performed:
     * <ul>
     *   <li><b>FM_E6</b> — fires when {@code suspended} is {@code true}, meaning the
     *       vendor has been suspended and any active orders from this vendor must be
     *       placed on hold pending the appeal deadline</li>
     * </ul>
     *
     * @param vendorId          the vendor identifier; must not be {@code null}
     * @param suspended         {@code true} if the vendor is currently suspended
     * @param suspensionReason  the reason for suspension, or {@code null} if not applicable
     * @return a {@link MarketplaceFmeaReport} with zero or one violation; never {@code null}
     */
    public MarketplaceFmeaReport analyzeVendorStatus(String vendorId,
                                                      boolean suspended,
                                                      String suspensionReason) {
        Objects.requireNonNull(vendorId, "vendorId must not be null");

        List<MarketplaceFmeaViolation> violations = new ArrayList<>();

        // FM_E6 — vendor suspended
        if (suspended) {
            violations.add(new MarketplaceFmeaViolation(
                MarketplaceFailureModeType.FM_E6_VENDOR_SUSPENDED,
                "vendorId=" + vendorId,
                "vendor is suspended: reason='"
                    + (suspensionReason != null ? suspensionReason : "unspecified")
                    + "' — in-flight orders must be placed on hold"
            ));
        }

        return new MarketplaceFmeaReport(Instant.now(), violations);
    }

    /**
     * Analyse the YAWL engine session handle for connectivity failures.
     *
     * <p>Checks performed:
     * <ul>
     *   <li><b>FM_E7</b> — fires when {@code sessionHandle} is {@code null}, blank,
     *       or contains the literal string {@code "<failure>"}, meaning the engine
     *       session has expired or authentication was rejected; all subsequent
     *       {@code launchCase()} calls will fail until the session is renewed</li>
     * </ul>
     *
     * @param sessionHandle  the YAWL engine session handle to evaluate;
     *                       may be {@code null} or blank to trigger the violation
     * @param engineUrl      the YAWL engine URL (for evidence context);
     *                       must not be {@code null}
     * @return a {@link MarketplaceFmeaReport} with zero or one violation; never {@code null}
     */
    public MarketplaceFmeaReport analyzeEngineConnectivity(String sessionHandle,
                                                            String engineUrl) {
        Objects.requireNonNull(engineUrl, "engineUrl must not be null");

        List<MarketplaceFmeaViolation> violations = new ArrayList<>();

        // FM_E7 — engine session expired or invalid
        boolean sessionInvalid = sessionHandle == null
            || sessionHandle.isBlank()
            || sessionHandle.contains(ENGINE_FAILURE_MARKER);

        if (sessionInvalid) {
            String evidence = sessionHandle == null
                ? "sessionHandle is null"
                : sessionHandle.isBlank()
                    ? "sessionHandle is blank"
                    : "sessionHandle contains '" + ENGINE_FAILURE_MARKER + "'";

            violations.add(new MarketplaceFmeaViolation(
                MarketplaceFailureModeType.FM_E7_ENGINE_SESSION_EXPIRED,
                "engineUrl=" + engineUrl,
                evidence + " — re-authenticate before next launchCase() call"
            ));
        }

        return new MarketplaceFmeaReport(Instant.now(), violations);
    }
}
