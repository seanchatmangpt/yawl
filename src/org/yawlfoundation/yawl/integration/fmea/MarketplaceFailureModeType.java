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

/**
 * Enumeration of end-to-end marketplace FMEA failure modes for YAWL v6.
 *
 * <p>Models the N-dimensional marketplace round-trip failure surface:
 * Vendor Agent → A2A handler → YAWL engine → workflow cases → payment/vendor lifecycle.
 * Each constant encodes Severity (S), Occurrence (O), and Detection (D) scores
 * on a 1–10 scale (10 = worst).
 *
 * <pre>
 *   RPN = Severity × Occurrence × Detection
 * </pre>
 *
 * <table>
 *   <tr><th>ID</th><th>Name</th><th>S</th><th>O</th><th>D</th><th>RPN</th></tr>
 *   <tr><td>FM_E1</td><td>Event Out of Order</td>
 *       <td>7</td><td>4</td><td>3</td><td>84</td></tr>
 *   <tr><td>FM_E2</td><td>Duplicate Event</td>
 *       <td>6</td><td>5</td><td>2</td><td>60</td></tr>
 *   <tr><td>FM_E3</td><td>Unknown Event Type</td>
 *       <td>7</td><td>3</td><td>2</td><td>42</td></tr>
 *   <tr><td>FM_E4</td><td>Sequence Gap</td>
 *       <td>8</td><td>3</td><td>4</td><td>96</td></tr>
 *   <tr><td>FM_E5</td><td>Payment Failure</td>
 *       <td>9</td><td>4</td><td>2</td><td>72</td></tr>
 *   <tr><td>FM_E6</td><td>Vendor Suspended</td>
 *       <td>8</td><td>2</td><td>4</td><td>64</td></tr>
 *   <tr><td>FM_E7</td><td>Engine Session Expired</td>
 *       <td>9</td><td>3</td><td>2</td><td>54</td></tr>
 * </table>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public enum MarketplaceFailureModeType {

    /**
     * FM_E1 — Incoming event has a sequence number that is not the next expected value.
     * A2A agents guarantee in-order delivery within a source+region stream; a sequence
     * number lower than or equal to the last seen number indicates late delivery or
     * message reordering, and the event must be rejected to preserve workflow state.
     * S=7 (workflow state corruption risk), O=4 (monthly: network reordering),
     * D=3 (sequence validation detects it).
     */
    FM_E1_EVENT_OUT_OF_ORDER(7, 4, 3,
        "Incoming event sequence number is out of order for this agent+region stream",
        "Validate envelope.sequenceNumber() > lastSeenSequence before processing; "
            + "reject and log out-of-order events for manual replay if needed"),

    /**
     * FM_E2 — An event with the same idempotency key has already been processed.
     * At-least-once delivery guarantees that duplicates will arrive; the A2A handler
     * tracks processed idempotency keys and must return a cached acknowledgment rather
     * than launching a duplicate workflow case.
     * S=6 (double-charge or duplicate workflow risk), O=5 (weekly: at-least-once delivery),
     * D=2 (idempotency key lookup detects it immediately).
     */
    FM_E2_DUPLICATE_EVENT(6, 5, 2,
        "Event idempotency key has already been processed — duplicate delivery detected",
        "Maintain a processed-keys cache (ConcurrentHashMap) and return cached ACK; "
            + "set TTL on the cache matching the upstream retry window"),

    /**
     * FM_E3 — The event type in the envelope has no registered handler.
     * The A2A handler's switch expression cannot route the event; the switch will
     * throw {@code IllegalArgumentException}, and the event is dropped without
     * launching a workflow case.
     * S=7 (event silently lost, workflow not started), O=3 (monthly: new event types),
     * D=2 (exception immediately logged).
     */
    FM_E3_UNKNOWN_EVENT_TYPE(7, 3, 2,
        "Event type has no registered handler in the A2A marketplace router",
        "Extend the routeEvent() switch with a handler for the new event type; "
            + "add the event type to the known-types registry before deployment"),

    /**
     * FM_E4 — A gap is detected in the sequence numbers from a given agent+region stream.
     * When {@code sequenceNumber > lastSeen + 1}, one or more events were never received.
     * The missing events must be replayed before downstream workflow state is consistent.
     * S=8 (silent data loss — workflow incomplete), O=3 (monthly: network partitions),
     * D=4 (gap arithmetic detects it but requires tracking per-stream state).
     */
    FM_E4_SEQUENCE_GAP(8, 3, 4,
        "Sequence gap detected — one or more events from this agent stream were not received",
        "Alert on gaps and trigger replay from the upstream event store; "
            + "implement a dead-letter queue for events that cannot be replayed"),

    /**
     * FM_E5 — A payment event carries a failure code indicating the charge was declined.
     * {@code PaymentFailedEvent.failureCode()} is non-null; the order cannot proceed to
     * fulfilment without a successful payment. A retry-eligible failure
     * ({@code retryable == true}) should launch a retry workflow; a hard failure
     * should escalate to the customer.
     * S=9 (revenue impact, order unprocessed), O=4 (monthly: card declines),
     * D=2 (explicit failure code in event payload).
     */
    FM_E5_PAYMENT_FAILURE(9, 4, 2,
        "PaymentFailedEvent received — payment declined with failure code",
        "Branch on retryable flag: launch HandlePaymentFailure workflow for retryable errors; "
            + "escalate to customer for hard declines (insufficient funds, card blocked)"),

    /**
     * FM_E6 — A vendor has been suspended while their orders are still in-flight.
     * {@code VendorSuspendedEvent} signals that the vendor's selling privileges have been
     * revoked; active orders belonging to this vendor must be placed on hold pending
     * the appeal deadline or fulfilment completion.
     * S=8 (active orders blocked, customer SLA risk), O=2 (rare: policy violation),
     * D=4 (suspension event must be correlated to open orders — requires cross-event state).
     */
    FM_E6_VENDOR_SUSPENDED(8, 2, 4,
        "VendorSuspendedEvent received — vendor is suspended with orders in-flight",
        "Launch HandleVendorSuspension workflow immediately; place in-flight orders on hold "
            + "and notify affected customers before the appeal deadline"),

    /**
     * FM_E7 — The YAWL engine session handle held by the A2A handler has expired or is invalid.
     * All calls to {@code InterfaceB_EnvironmentBasedClient.launchCase()} will fail or
     * return a {@code <failure>} result until a fresh session handle is obtained.
     * S=9 (all workflow launches fail), O=3 (monthly: long-running integrations),
     * D=2 (detectable by checking sessionHandle for &lt;failure&gt; prefix).
     */
    FM_E7_ENGINE_SESSION_EXPIRED(9, 3, 2,
        "YAWL engine session handle is expired or invalid — workflow launches will fail",
        "Re-authenticate with the engine before each event batch; wrap launchCase() with "
            + "a session-renewal interceptor that detects <failure> and re-connects");

    // -----------------------------------------------------------------------

    private final int severity;
    private final int occurrence;
    private final int detection;
    private final String description;
    private final String mitigation;

    MarketplaceFailureModeType(int severity, int occurrence, int detection,
                                String description, String mitigation) {
        this.severity    = severity;
        this.occurrence  = occurrence;
        this.detection   = detection;
        this.description = description;
        this.mitigation  = mitigation;
    }

    /**
     * Risk Priority Number: {@code Severity × Occurrence × Detection}.
     * Higher RPN = higher risk. Maximum possible RPN = 1000 (10 × 10 × 10).
     *
     * @return computed RPN value
     */
    public int rpn() {
        return severity * occurrence * detection;
    }

    /** Severity score (1–10, 10 = catastrophic impact). */
    public int getSeverity()   { return severity; }

    /** Occurrence score (1–10, 10 = occurs on every change). */
    public int getOccurrence() { return occurrence; }

    /** Detection score (1–10, 10 = no detection possible). */
    public int getDetection()  { return detection; }

    /** Human-readable description of the failure mode. */
    public String getDescription() { return description; }

    /** Recommended mitigation action. */
    public String getMitigation()  { return mitigation; }
}
