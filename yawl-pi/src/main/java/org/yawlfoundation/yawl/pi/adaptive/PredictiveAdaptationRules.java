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

package org.yawlfoundation.yawl.pi.adaptive;

import org.yawlfoundation.yawl.integration.adaptation.AdaptationAction;
import org.yawlfoundation.yawl.integration.adaptation.AdaptationCondition;
import org.yawlfoundation.yawl.integration.adaptation.AdaptationRule;

import java.util.List;

/**
 * Factory for {@link AdaptationRule}s that respond to predictions emitted by
 * {@link PredictiveProcessObserver}.
 *
 * <h2>Diátaxis — How-to</h2>
 *
 * <p>Use this class to answer: <em>"How do I make my workflow respond to a
 * predicted SLA breach?"</em> or <em>"How do I auto-escalate high-risk cases?"</em>
 * Each factory method returns a ready-to-use rule that pairs an event-type condition
 * with a payload threshold and an {@link AdaptationAction}.</p>
 *
 * <h2>Composing rules</h2>
 *
 * <p>Rules are assembled into an {@link
 * org.yawlfoundation.yawl.integration.adaptation.EventDrivenAdaptationEngine}
 * and processed in priority order (lower number = higher priority).  Combine
 * freely:</p>
 *
 * <pre>{@code
 * EventDrivenAdaptationEngine engine = new EventDrivenAdaptationEngine(List.of(
 *     PredictiveAdaptationRules.timerExpiryBreach(),           // priority 5
 *     PredictiveAdaptationRules.fraudDetector(0.85),           // priority 10
 *     PredictiveAdaptationRules.slaGuardian(60),               // priority 20
 *     PredictiveAdaptationRules.highRiskEscalation(0.70),      // priority 30
 *     PredictiveAdaptationRules.anomalyAlert(0.6)              // priority 50
 * ));
 * }</pre>
 *
 * <h2>All rules respond to {@link PredictiveProcessObserver} event types</h2>
 *
 * <ul>
 *   <li>{@link PredictiveProcessObserver#TIMER_EXPIRY_BREACH} → {@link #timerExpiryBreach()}</li>
 *   <li>{@link PredictiveProcessObserver#SLA_BREACH_PREDICTED} → {@link #slaGuardian(int)}</li>
 *   <li>{@link PredictiveProcessObserver#PROCESS_ANOMALY_DETECTED} → {@link #fraudDetector(double)},
 *       {@link #anomalyAlert(double)}</li>
 *   <li>{@link PredictiveProcessObserver#CASE_OUTCOME_PREDICTION} → {@link #highRiskEscalation(double)},
 *       {@link #complexityRouter(double)}</li>
 *   <li>{@link PredictiveProcessObserver#NEXT_ACTIVITY_SUGGESTION} → {@link #nextActivityPriorityBoost(double)}</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @since 6.0
 * @see EnterpriseAutoMlPatterns
 * @see PredictiveProcessObserver
 */
public final class PredictiveAdaptationRules {

    private PredictiveAdaptationRules() {
        throw new UnsupportedOperationException(
                "PredictiveAdaptationRules is a static factory; do not instantiate");
    }

    // ── SLA / Time rules ─────────────────────────────────────────────────────

    /**
     * Escalates cases to manual review when predicted remaining time is below the
     * given threshold.
     *
     * <p><strong>Enterprise use case:</strong> Operations SLA Guardian — automatically
     * surface at-risk cases to supervisors before the breach occurs.</p>
     *
     * <p>Listens for: {@link PredictiveProcessObserver#SLA_BREACH_PREDICTED}
     * with {@code remainingMinutes} &lt; {@code thresholdMinutes}.</p>
     *
     * @param thresholdMinutes remaining-time threshold in minutes (e.g. 60 for 1-hour SLA)
     * @param priority         rule priority (lower = evaluated first)
     * @return configured AdaptationRule
     */
    public static AdaptationRule slaGuardian(int thresholdMinutes, int priority) {
        return new AdaptationRule(
                "rule-sla-guardian-" + thresholdMinutes + "m",
                "SLA Guardian (<" + thresholdMinutes + " min remaining)",
                AdaptationCondition.and(
                        AdaptationCondition.eventType(
                                PredictiveProcessObserver.SLA_BREACH_PREDICTED),
                        AdaptationCondition.payloadBelow("remainingMinutes", thresholdMinutes)
                ),
                AdaptationAction.ESCALATE_TO_MANUAL,
                priority,
                "Escalate to manual review when remaining time drops below "
                        + thresholdMinutes + " minutes"
        );
    }

    /**
     * Convenience overload with default priority 20.
     *
     * @param thresholdMinutes remaining-time threshold in minutes
     */
    public static AdaptationRule slaGuardian(int thresholdMinutes) {
        return slaGuardian(thresholdMinutes, 20);
    }

    /**
     * Immediately escalates cases with timer expiry — a definite SLA breach requiring
     * urgent human intervention.
     *
     * <p><strong>Enterprise use case:</strong> Any time-bounded process (insurance
     * claims, regulatory filings, healthcare triage).</p>
     *
     * @return configured AdaptationRule with priority 5 (highest default)
     */
    public static AdaptationRule timerExpiryBreach() {
        return new AdaptationRule(
                "rule-timer-expiry-breach",
                "Timer Expiry SLA Breach",
                AdaptationCondition.eventType(PredictiveProcessObserver.TIMER_EXPIRY_BREACH),
                AdaptationAction.ESCALATE_TO_MANUAL,
                5,
                "Immediately escalate cases where a work-item timer has expired"
        );
    }

    // ── Anomaly / Risk rules ──────────────────────────────────────────────────

    /**
     * Rejects cases immediately when the anomaly score exceeds the fraud threshold.
     *
     * <p><strong>Enterprise use case:</strong> Financial services fraud detection —
     * reject high-confidence fraud cases before they route to fulfilment.</p>
     *
     * <p>Listens for: {@link PredictiveProcessObserver#PROCESS_ANOMALY_DETECTED}
     * with {@code anomalyScore} &gt; {@code fraudThreshold}.</p>
     *
     * @param fraudThreshold anomaly score threshold (0–1); typically 0.80–0.95
     * @param priority       rule priority
     */
    public static AdaptationRule fraudDetector(double fraudThreshold, int priority) {
        return new AdaptationRule(
                "rule-fraud-detector-" + (int) (fraudThreshold * 100),
                "Fraud Detector (score >" + fraudThreshold + ")",
                AdaptationCondition.and(
                        AdaptationCondition.eventType(
                                PredictiveProcessObserver.PROCESS_ANOMALY_DETECTED),
                        AdaptationCondition.payloadAbove("anomalyScore", fraudThreshold)
                ),
                AdaptationAction.REJECT_IMMEDIATELY,
                priority,
                "Reject cases where anomaly_detection score exceeds " + fraudThreshold
        );
    }

    /** Convenience overload with default priority 10. */
    public static AdaptationRule fraudDetector(double fraudThreshold) {
        return fraudDetector(fraudThreshold, 10);
    }

    /**
     * Notifies stakeholders when anomaly score exceeds a softer alert threshold.
     *
     * <p><strong>Enterprise use case:</strong> Process compliance monitoring —
     * alert process owners of unusual case behaviour without blocking execution.</p>
     *
     * @param alertThreshold anomaly score threshold (0–1); typically 0.50–0.75
     * @param priority       rule priority
     */
    public static AdaptationRule anomalyAlert(double alertThreshold, int priority) {
        return new AdaptationRule(
                "rule-anomaly-alert-" + (int) (alertThreshold * 100),
                "Anomaly Alert (score >" + alertThreshold + ")",
                AdaptationCondition.and(
                        AdaptationCondition.eventType(
                                PredictiveProcessObserver.PROCESS_ANOMALY_DETECTED),
                        AdaptationCondition.payloadAbove("anomalyScore", alertThreshold)
                ),
                AdaptationAction.NOTIFY_STAKEHOLDERS,
                priority,
                "Notify stakeholders when anomaly score exceeds " + alertThreshold
        );
    }

    /** Convenience overload with default priority 50. */
    public static AdaptationRule anomalyAlert(double alertThreshold) {
        return anomalyAlert(alertThreshold, 50);
    }

    // ── Case outcome rules ────────────────────────────────────────────────────

    /**
     * Escalates high-risk cases to manual review based on outcome score.
     *
     * <p><strong>Enterprise use case:</strong> Healthcare case management — surface
     * cases predicted to fail so coordinators can intervene proactively.</p>
     *
     * <p>Listens for: {@link PredictiveProcessObserver#CASE_OUTCOME_PREDICTION}
     * with {@code outcomeScore} &gt; {@code riskThreshold}.</p>
     *
     * @param riskThreshold outcome score above which the case is high-risk (0–1)
     * @param priority       rule priority
     */
    public static AdaptationRule highRiskEscalation(double riskThreshold, int priority) {
        return new AdaptationRule(
                "rule-high-risk-escalation-" + (int) (riskThreshold * 100),
                "High-Risk Escalation (score >" + riskThreshold + ")",
                AdaptationCondition.and(
                        AdaptationCondition.eventType(
                                PredictiveProcessObserver.CASE_OUTCOME_PREDICTION),
                        AdaptationCondition.payloadAbove("outcomeScore", riskThreshold)
                ),
                AdaptationAction.ESCALATE_TO_MANUAL,
                priority,
                "Escalate cases predicted to fail (outcome score > " + riskThreshold + ")"
        );
    }

    /** Convenience overload with default priority 30. */
    public static AdaptationRule highRiskEscalation(double riskThreshold) {
        return highRiskEscalation(riskThreshold, 30);
    }

    /**
     * Reroutes complex cases to a specialist subprocess when the model is confident.
     *
     * <p><strong>Enterprise use case:</strong> Insurance claims complexity routing —
     * automatically route predicted complex claims to specialist adjusters rather
     * than the standard queue.</p>
     *
     * <p>Listens for: {@link PredictiveProcessObserver#CASE_OUTCOME_PREDICTION}
     * with {@code confidence} &gt; {@code confidenceThreshold}.</p>
     *
     * @param confidenceThreshold prediction confidence above which rerouting fires (0–1)
     * @param priority            rule priority
     */
    public static AdaptationRule complexityRouter(double confidenceThreshold, int priority) {
        return new AdaptationRule(
                "rule-complexity-router-" + (int) (confidenceThreshold * 100),
                "Complexity Router (confidence >" + confidenceThreshold + ")",
                AdaptationCondition.and(
                        AdaptationCondition.eventType(
                                PredictiveProcessObserver.CASE_OUTCOME_PREDICTION),
                        AdaptationCondition.payloadAbove("confidence", confidenceThreshold)
                ),
                AdaptationAction.REROUTE_TO_SUBPROCESS,
                priority,
                "Reroute complex cases when prediction confidence exceeds "
                        + confidenceThreshold
        );
    }

    /** Convenience overload with default priority 40. */
    public static AdaptationRule complexityRouter(double confidenceThreshold) {
        return complexityRouter(confidenceThreshold, 40);
    }

    // ── Next-activity rules ───────────────────────────────────────────────────

    /**
     * Increases case priority when next-activity prediction is highly confident,
     * signalling a clear fast-path exists.
     *
     * <p><strong>Enterprise use case:</strong> Government benefit processing —
     * accelerate straightforward cases by boosting their queue priority when the
     * model is certain about the next step.</p>
     *
     * <p>Listens for: {@link PredictiveProcessObserver#NEXT_ACTIVITY_SUGGESTION}
     * with {@code confidence} &gt; {@code confidenceThreshold}.</p>
     *
     * @param confidenceThreshold next-activity confidence above which priority is raised
     * @param priority            rule priority
     */
    public static AdaptationRule nextActivityPriorityBoost(double confidenceThreshold,
                                                            int priority) {
        return new AdaptationRule(
                "rule-next-activity-boost-" + (int) (confidenceThreshold * 100),
                "Next-Activity Priority Boost (confidence >" + confidenceThreshold + ")",
                AdaptationCondition.and(
                        AdaptationCondition.eventType(
                                PredictiveProcessObserver.NEXT_ACTIVITY_SUGGESTION),
                        AdaptationCondition.payloadAbove("confidence", confidenceThreshold)
                ),
                AdaptationAction.INCREASE_PRIORITY,
                priority,
                "Increase priority when next-activity confidence exceeds "
                        + confidenceThreshold
        );
    }

    /** Convenience overload with default priority 60. */
    public static AdaptationRule nextActivityPriorityBoost(double confidenceThreshold) {
        return nextActivityPriorityBoost(confidenceThreshold, 60);
    }

    // ── Pre-built rule sets ───────────────────────────────────────────────────

    /**
     * Returns the standard rule set for insurance claims triage: timer breach (p5),
     * fraud detection (p10), complexity routing (p40), anomaly alert (p50).
     *
     * @param fraudThreshold    anomaly score for immediate rejection (e.g. 0.85)
     * @param complexityConfidence confidence threshold for specialist routing (e.g. 0.75)
     */
    public static List<AdaptationRule> insuranceClaimsRuleSet(double fraudThreshold,
                                                               double complexityConfidence) {
        return List.of(
                timerExpiryBreach(),
                fraudDetector(fraudThreshold, 10),
                complexityRouter(complexityConfidence, 40),
                anomalyAlert(0.55, 50)
        );
    }

    /**
     * Returns the standard rule set for healthcare case management: timer breach (p5),
     * SLA guardian (p20), high-risk escalation (p30), next-activity boost (p60).
     *
     * @param slaThresholdMinutes remaining-time threshold for escalation
     * @param riskThreshold       outcome score for escalation (e.g. 0.70)
     */
    public static List<AdaptationRule> healthcareRuleSet(int slaThresholdMinutes,
                                                          double riskThreshold) {
        return List.of(
                timerExpiryBreach(),
                slaGuardian(slaThresholdMinutes, 20),
                highRiskEscalation(riskThreshold, 30),
                nextActivityPriorityBoost(0.80, 60)
        );
    }

    /**
     * Returns the standard rule set for financial risk monitoring: timer breach (p5),
     * fraud rejection (p10), high-risk escalation (p30), broad anomaly alert (p50).
     *
     * @param fraudThreshold  anomaly score for immediate rejection (e.g. 0.90)
     * @param riskThreshold   outcome score for escalation (e.g. 0.75)
     */
    public static List<AdaptationRule> financialRiskRuleSet(double fraudThreshold,
                                                             double riskThreshold) {
        return List.of(
                timerExpiryBreach(),
                fraudDetector(fraudThreshold, 10),
                highRiskEscalation(riskThreshold, 30),
                anomalyAlert(0.60, 50)
        );
    }

    /**
     * Returns the standard rule set for operations SLA management: timer breach (p5),
     * tight SLA guardian (p20), loose SLA guardian (p25), anomaly alert (p50).
     *
     * @param criticalSlaMinutes  remaining-time threshold for critical escalation
     * @param warningSlaminutes   remaining-time threshold for early-warning notification
     */
    public static List<AdaptationRule> operationsSlaRuleSet(int criticalSlaMinutes,
                                                             int warningSlaminutes) {
        return List.of(
                timerExpiryBreach(),
                slaGuardian(criticalSlaMinutes, 20),
                new AdaptationRule(
                        "rule-sla-warning-" + warningSlaminutes + "m",
                        "SLA Warning (<" + warningSlaminutes + " min remaining)",
                        AdaptationCondition.and(
                                AdaptationCondition.eventType(
                                        PredictiveProcessObserver.SLA_BREACH_PREDICTED),
                                AdaptationCondition.payloadBelow("remainingMinutes",
                                        warningSlaminutes)
                        ),
                        AdaptationAction.NOTIFY_STAKEHOLDERS,
                        25,
                        "Notify stakeholders when remaining time drops below "
                                + warningSlaminutes + " minutes"
                ),
                anomalyAlert(0.55, 50)
        );
    }
}
