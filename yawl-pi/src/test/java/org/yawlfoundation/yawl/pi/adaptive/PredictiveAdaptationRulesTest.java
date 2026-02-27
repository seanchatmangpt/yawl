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

import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.integration.adaptation.AdaptationAction;
import org.yawlfoundation.yawl.integration.adaptation.AdaptationResult;
import org.yawlfoundation.yawl.integration.adaptation.AdaptationRule;
import org.yawlfoundation.yawl.integration.adaptation.EventDrivenAdaptationEngine;
import org.yawlfoundation.yawl.integration.adaptation.EventSeverity;
import org.yawlfoundation.yawl.integration.adaptation.ProcessEvent;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago-TDD tests for {@link PredictiveAdaptationRules}.
 *
 * <p>Every test wires rules into a real {@link EventDrivenAdaptationEngine} and
 * drives it with a real {@link ProcessEvent} — no doubles.</p>
 */
class PredictiveAdaptationRulesTest {

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ProcessEvent slaEvent(double remainingMinutes) {
        return new ProcessEvent(
                "evt-sla-" + System.nanoTime(),
                PredictiveProcessObserver.SLA_BREACH_PREDICTED,
                "test",
                Instant.now(),
                Map.of("caseId", "c1", "taskId", "t1",
                        "remainingMinutes", remainingMinutes,
                        "statusTransition", "statusFired->statusExecuting"),
                EventSeverity.HIGH
        );
    }

    private ProcessEvent anomalyEvent(double score) {
        return new ProcessEvent(
                "evt-anomaly-" + System.nanoTime(),
                PredictiveProcessObserver.PROCESS_ANOMALY_DETECTED,
                "test",
                Instant.now(),
                Map.of("caseId", "c1", "anomalyScore", score),
                EventSeverity.HIGH
        );
    }

    private ProcessEvent outcomeEvent(double outcomeScore, double confidence) {
        return new ProcessEvent(
                "evt-outcome-" + System.nanoTime(),
                PredictiveProcessObserver.CASE_OUTCOME_PREDICTION,
                "test",
                Instant.now(),
                Map.of("caseId", "c1", "specId", "s1",
                        "outcomeScore", outcomeScore,
                        "confidence", confidence),
                EventSeverity.LOW
        );
    }

    private ProcessEvent nextActivityEvent(double confidence) {
        return new ProcessEvent(
                "evt-next-" + System.nanoTime(),
                PredictiveProcessObserver.NEXT_ACTIVITY_SUGGESTION,
                "test",
                Instant.now(),
                Map.of("caseId", "c1", "completedTask", "t1",
                        "confidence", confidence),
                EventSeverity.MEDIUM
        );
    }

    private ProcessEvent timerBreachEvent() {
        return new ProcessEvent(
                "evt-timer-" + System.nanoTime(),
                PredictiveProcessObserver.TIMER_EXPIRY_BREACH,
                "test",
                Instant.now(),
                Map.of("caseId", "c1", "taskId", "t1",
                        "specId", "s1", "breachType", "TIMER_EXPIRY"),
                EventSeverity.CRITICAL
        );
    }

    private AdaptationResult run(AdaptationRule rule, ProcessEvent event) {
        return new EventDrivenAdaptationEngine(List.of(rule)).process(event);
    }

    // ── slaGuardian ───────────────────────────────────────────────────────────

    @Test
    void slaGuardian_escalatesWhenRemainingTimeBelowThreshold() {
        AdaptationRule rule = PredictiveAdaptationRules.slaGuardian(60);
        AdaptationResult result = run(rule, slaEvent(45.0));

        assertTrue(result.adapted());
        assertEquals(AdaptationAction.ESCALATE_TO_MANUAL, result.executedAction());
    }

    @Test
    void slaGuardian_doesNotFireWhenRemainingTimeAboveThreshold() {
        AdaptationRule rule = PredictiveAdaptationRules.slaGuardian(60);
        AdaptationResult result = run(rule, slaEvent(90.0));

        assertFalse(result.adapted());
    }

    @Test
    void slaGuardian_exactlyAtThresholdDoesNotFire() {
        AdaptationRule rule = PredictiveAdaptationRules.slaGuardian(60);
        // payloadBelow is strictly <, so exactly at threshold should not fire
        AdaptationResult result = run(rule, slaEvent(60.0));

        assertFalse(result.adapted());
    }

    @Test
    void slaGuardian_customPriorityPreserved() {
        AdaptationRule rule = PredictiveAdaptationRules.slaGuardian(120, 99);
        assertEquals(99, rule.priority());
    }

    @Test
    void slaGuardian_defaultPriorityIs20() {
        AdaptationRule rule = PredictiveAdaptationRules.slaGuardian(60);
        assertEquals(20, rule.priority());
    }

    // ── timerExpiryBreach ─────────────────────────────────────────────────────

    @Test
    void timerExpiryBreach_escalatesOnTimerExpiry() {
        AdaptationRule rule = PredictiveAdaptationRules.timerExpiryBreach();
        AdaptationResult result = run(rule, timerBreachEvent());

        assertTrue(result.adapted());
        assertEquals(AdaptationAction.ESCALATE_TO_MANUAL, result.executedAction());
    }

    @Test
    void timerExpiryBreach_doesNotFireOnSlaEvent() {
        AdaptationRule rule = PredictiveAdaptationRules.timerExpiryBreach();
        AdaptationResult result = run(rule, slaEvent(10.0));

        assertFalse(result.adapted());
    }

    @Test
    void timerExpiryBreach_priorityIs5() {
        AdaptationRule rule = PredictiveAdaptationRules.timerExpiryBreach();
        assertEquals(5, rule.priority());
    }

    // ── fraudDetector ─────────────────────────────────────────────────────────

    @Test
    void fraudDetector_rejectsWhenAnomalyScoreAboveThreshold() {
        AdaptationRule rule = PredictiveAdaptationRules.fraudDetector(0.85);
        AdaptationResult result = run(rule, anomalyEvent(0.90));

        assertTrue(result.adapted());
        assertEquals(AdaptationAction.REJECT_IMMEDIATELY, result.executedAction());
    }

    @Test
    void fraudDetector_doesNotFireWhenScoreBelowThreshold() {
        AdaptationRule rule = PredictiveAdaptationRules.fraudDetector(0.85);
        AdaptationResult result = run(rule, anomalyEvent(0.70));

        assertFalse(result.adapted());
    }

    @Test
    void fraudDetector_defaultPriorityIs10() {
        AdaptationRule rule = PredictiveAdaptationRules.fraudDetector(0.85);
        assertEquals(10, rule.priority());
    }

    // ── anomalyAlert ──────────────────────────────────────────────────────────

    @Test
    void anomalyAlert_notifiesWhenScoreAboveThreshold() {
        AdaptationRule rule = PredictiveAdaptationRules.anomalyAlert(0.55);
        AdaptationResult result = run(rule, anomalyEvent(0.65));

        assertTrue(result.adapted());
        assertEquals(AdaptationAction.NOTIFY_STAKEHOLDERS, result.executedAction());
    }

    @Test
    void anomalyAlert_defaultPriorityIs50() {
        AdaptationRule rule = PredictiveAdaptationRules.anomalyAlert(0.55);
        assertEquals(50, rule.priority());
    }

    // ── highRiskEscalation ────────────────────────────────────────────────────

    @Test
    void highRiskEscalation_escalatesWhenOutcomeScoreAboveThreshold() {
        AdaptationRule rule = PredictiveAdaptationRules.highRiskEscalation(0.70);
        AdaptationResult result = run(rule, outcomeEvent(0.85, 0.80));

        assertTrue(result.adapted());
        assertEquals(AdaptationAction.ESCALATE_TO_MANUAL, result.executedAction());
    }

    @Test
    void highRiskEscalation_doesNotFireOnLowRiskCase() {
        AdaptationRule rule = PredictiveAdaptationRules.highRiskEscalation(0.70);
        AdaptationResult result = run(rule, outcomeEvent(0.40, 0.90));

        assertFalse(result.adapted());
    }

    @Test
    void highRiskEscalation_defaultPriorityIs30() {
        AdaptationRule rule = PredictiveAdaptationRules.highRiskEscalation(0.70);
        assertEquals(30, rule.priority());
    }

    // ── complexityRouter ──────────────────────────────────────────────────────

    @Test
    void complexityRouter_reroutesWhenConfidenceAboveThreshold() {
        AdaptationRule rule = PredictiveAdaptationRules.complexityRouter(0.75);
        AdaptationResult result = run(rule, outcomeEvent(0.55, 0.90));

        assertTrue(result.adapted());
        assertEquals(AdaptationAction.REROUTE_TO_SUBPROCESS, result.executedAction());
    }

    @Test
    void complexityRouter_doesNotFireOnLowConfidence() {
        AdaptationRule rule = PredictiveAdaptationRules.complexityRouter(0.75);
        AdaptationResult result = run(rule, outcomeEvent(0.90, 0.60));

        assertFalse(result.adapted());
    }

    // ── nextActivityPriorityBoost ─────────────────────────────────────────────

    @Test
    void nextActivityBoost_increasesPriorityWhenHighConfidence() {
        AdaptationRule rule = PredictiveAdaptationRules.nextActivityPriorityBoost(0.80);
        AdaptationResult result = run(rule, nextActivityEvent(0.92));

        assertTrue(result.adapted());
        assertEquals(AdaptationAction.INCREASE_PRIORITY, result.executedAction());
    }

    @Test
    void nextActivityBoost_doesNotFireOnLowConfidence() {
        AdaptationRule rule = PredictiveAdaptationRules.nextActivityPriorityBoost(0.80);
        AdaptationResult result = run(rule, nextActivityEvent(0.65));

        assertFalse(result.adapted());
    }

    @Test
    void nextActivityBoost_defaultPriorityIs60() {
        AdaptationRule rule = PredictiveAdaptationRules.nextActivityPriorityBoost(0.80);
        assertEquals(60, rule.priority());
    }

    // ── pre-built rule sets ───────────────────────────────────────────────────

    @Test
    void insuranceClaimsRuleSet_timerBreachIsHighestPriority() {
        List<AdaptationRule> rules = PredictiveAdaptationRules.insuranceClaimsRuleSet(0.85, 0.75);
        AdaptationRule highestPriority = rules.stream()
                .min((a, b) -> Integer.compare(a.priority(), b.priority()))
                .orElseThrow();
        assertEquals(PredictiveProcessObserver.TIMER_EXPIRY_BREACH,
                highestPriority.condition().matches(timerBreachEvent())
                        ? timerBreachEvent().eventType() : null,
                "timer breach rule should be highest priority and match timer events");
        assertEquals(5, highestPriority.priority());
    }

    @Test
    void insuranceClaimsRuleSet_fraudRejectionWinsOverComplexityRouting() {
        List<AdaptationRule> rules = PredictiveAdaptationRules.insuranceClaimsRuleSet(0.85, 0.75);
        EventDrivenAdaptationEngine engine = new EventDrivenAdaptationEngine(rules);

        // A fraud-detected anomaly should be rejected, not rerouted
        AdaptationResult result = engine.process(anomalyEvent(0.95));

        assertTrue(result.adapted());
        assertEquals(AdaptationAction.REJECT_IMMEDIATELY, result.executedAction());
    }

    @Test
    void healthcareRuleSet_hasCorrectRuleCount() {
        List<AdaptationRule> rules = PredictiveAdaptationRules.healthcareRuleSet(60, 0.70);
        assertEquals(4, rules.size());
    }

    @Test
    void financialRiskRuleSet_fraudThresholdRespected() {
        List<AdaptationRule> rules = PredictiveAdaptationRules.financialRiskRuleSet(0.90, 0.75);
        EventDrivenAdaptationEngine engine = new EventDrivenAdaptationEngine(rules);

        // Score 0.88 is below 0.90 fraud threshold — should not reject
        AdaptationResult result = engine.process(anomalyEvent(0.88));

        // Should match anomaly alert (0.60) but not fraud rejection (0.90)
        assertTrue(result.adapted());
        assertNotEquals(AdaptationAction.REJECT_IMMEDIATELY, result.executedAction());
    }

    @Test
    void operationsSlaRuleSet_criticalTierEscalates() {
        List<AdaptationRule> rules = PredictiveAdaptationRules.operationsSlaRuleSet(30, 120);
        EventDrivenAdaptationEngine engine = new EventDrivenAdaptationEngine(rules);

        AdaptationResult result = engine.process(slaEvent(20.0));

        assertTrue(result.adapted());
        assertEquals(AdaptationAction.ESCALATE_TO_MANUAL, result.executedAction());
    }

    @Test
    void operationsSlaRuleSet_warningTierNotifiesWithoutEscalating() {
        List<AdaptationRule> rules = PredictiveAdaptationRules.operationsSlaRuleSet(30, 120);
        EventDrivenAdaptationEngine engine = new EventDrivenAdaptationEngine(rules);

        // 80 min: below 120 warning threshold but above 30 critical
        AdaptationResult result = engine.process(slaEvent(80.0));

        assertTrue(result.adapted());
        assertEquals(AdaptationAction.NOTIFY_STAKEHOLDERS, result.executedAction());
    }

    // ── static factory guard ──────────────────────────────────────────────────

    @Test
    void constructorThrowsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class, () -> {
            var constructor = PredictiveAdaptationRules.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            try {
                constructor.newInstance();
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
            }
        });
    }
}
