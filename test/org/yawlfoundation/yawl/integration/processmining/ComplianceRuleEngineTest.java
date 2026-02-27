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

package org.yawlfoundation.yawl.integration.processmining;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for {@link ComplianceRuleEngine}.
 *
 * Tests real compliance evaluation behavior with real trace data.
 * No mocks — constructs actual trace lists and verifies real violation detection.
 */
class ComplianceRuleEngineTest {

    private ComplianceRuleEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ComplianceRuleEngine();
    }

    // -------------------------------------------------------------------------
    // addRule validation
    // -------------------------------------------------------------------------

    @Test
    void addRule_rejectsNullRule() {
        assertThrows(IllegalArgumentException.class, () -> engine.addRule(null));
    }

    @Test
    void addRules_rejectsNullCollection() {
        assertThrows(IllegalArgumentException.class, () -> engine.addRules(null));
    }

    @Test
    void getRules_returnsAddedRules() {
        ComplianceRuleEngine.ComplianceRule rule =
            ComplianceRuleEngine.ComplianceRule.mandatoryActivity("R1", "Approve", "desc");
        engine.addRule(rule);
        assertTrue(engine.getRules().contains(rule), "Added rule must appear in getRules()");
    }

    @Test
    void getRules_isUnmodifiable() {
        assertThrows(UnsupportedOperationException.class,
            () -> engine.getRules().add(
                ComplianceRuleEngine.ComplianceRule.mandatoryActivity("R1", "X", "d")));
    }

    // -------------------------------------------------------------------------
    // evaluate validation
    // -------------------------------------------------------------------------

    @Test
    void evaluate_rejectsNullTraces() {
        assertThrows(IllegalArgumentException.class, () -> engine.evaluate(null));
    }

    // -------------------------------------------------------------------------
    // No rules → always compliant
    // -------------------------------------------------------------------------

    @Test
    void noRules_emptyTraces_isCompliant() {
        ComplianceRuleEngine.ComplianceReport report = engine.evaluate(Collections.emptyList());
        assertTrue(report.compliant(), "No rules + no traces = compliant");
        assertEquals(0, report.violations().size());
    }

    @Test
    void noRules_withTraces_isCompliant() {
        List<ComplianceRuleEngine.Trace> traces = List.of(
            new ComplianceRuleEngine.Trace("case1", List.of("A", "B", "C"))
        );
        ComplianceRuleEngine.ComplianceReport report = engine.evaluate(traces);
        assertTrue(report.compliant(), "No rules = always compliant");
    }

    // -------------------------------------------------------------------------
    // MANDATORY_ACTIVITY
    // -------------------------------------------------------------------------

    @Test
    void mandatoryActivity_presentInAllTraces_compliant() {
        engine.addRule(ComplianceRuleEngine.ComplianceRule.mandatoryActivity(
            "M1", "Audit", "Audit step is required"));
        List<ComplianceRuleEngine.Trace> traces = List.of(
            new ComplianceRuleEngine.Trace("c1", List.of("Submit", "Audit", "Approve")),
            new ComplianceRuleEngine.Trace("c2", List.of("Apply", "Audit", "Close"))
        );
        ComplianceRuleEngine.ComplianceReport report = engine.evaluate(traces);
        assertTrue(report.compliant(), "All traces have Audit → compliant");
    }

    @Test
    void mandatoryActivity_missingFromOneTrace_violation() {
        engine.addRule(ComplianceRuleEngine.ComplianceRule.mandatoryActivity(
            "M1", "Audit", "Audit step required by SOX"));
        List<ComplianceRuleEngine.Trace> traces = List.of(
            new ComplianceRuleEngine.Trace("c1", List.of("Submit", "Audit", "Approve")),
            new ComplianceRuleEngine.Trace("c2", List.of("Submit", "Approve"))  // missing Audit
        );
        ComplianceRuleEngine.ComplianceReport report = engine.evaluate(traces);
        assertFalse(report.compliant(), "Missing Audit in c2 must cause violation");
        assertEquals(1, report.violations().size());
        assertEquals("c2", report.violations().get(0).traceId());
    }

    @Test
    void mandatoryActivity_missingFromMultipleTraces_allViolationsReported() {
        engine.addRule(ComplianceRuleEngine.ComplianceRule.mandatoryActivity(
            "M1", "Sign_Off", "Sign-off required"));
        List<ComplianceRuleEngine.Trace> traces = List.of(
            new ComplianceRuleEngine.Trace("c1", List.of("Work")),  // missing
            new ComplianceRuleEngine.Trace("c2", List.of("Work")),  // missing
            new ComplianceRuleEngine.Trace("c3", List.of("Work", "Sign_Off"))  // ok
        );
        ComplianceRuleEngine.ComplianceReport report = engine.evaluate(traces);
        assertEquals(2, report.violations().size(), "Both c1 and c2 must be reported");
    }

    // -------------------------------------------------------------------------
    // FORBIDDEN_SEQUENCE
    // -------------------------------------------------------------------------

    @Test
    void forbiddenSequence_absent_compliant() {
        engine.addRule(ComplianceRuleEngine.ComplianceRule.forbiddenSequence(
            "SOD1", "Prescribe", "Dispense", "SOD: prescriber != dispenser"));
        List<ComplianceRuleEngine.Trace> traces = List.of(
            new ComplianceRuleEngine.Trace("c1", List.of("Prescribe", "Review", "Dispense"))
        );
        // Prescribe and Dispense are separated by Review — not consecutive
        ComplianceRuleEngine.ComplianceReport report = engine.evaluate(traces);
        assertTrue(report.compliant(), "Non-consecutive Prescribe/Dispense must be compliant");
    }

    @Test
    void forbiddenSequence_consecutiveAB_violation() {
        engine.addRule(ComplianceRuleEngine.ComplianceRule.forbiddenSequence(
            "SOD1", "Prescribe", "Dispense", "SOD required"));
        List<ComplianceRuleEngine.Trace> traces = List.of(
            new ComplianceRuleEngine.Trace("c1", List.of("Prescribe", "Dispense"))
        );
        ComplianceRuleEngine.ComplianceReport report = engine.evaluate(traces);
        assertFalse(report.compliant(), "Consecutive Prescribe→Dispense must violate SOD rule");
        assertEquals("SOD1", report.violations().get(0).ruleId());
    }

    @Test
    void forbiddenSequence_consecutiveBA_violation() {
        // Rule is symmetric: B→A also forbidden
        engine.addRule(ComplianceRuleEngine.ComplianceRule.forbiddenSequence(
            "SOD1", "Prescribe", "Dispense", "SOD required"));
        List<ComplianceRuleEngine.Trace> traces = List.of(
            new ComplianceRuleEngine.Trace("c1", List.of("Dispense", "Prescribe"))
        );
        ComplianceRuleEngine.ComplianceReport report = engine.evaluate(traces);
        assertFalse(report.compliant(), "Dispense→Prescribe also violates SOD");
    }

    @Test
    void forbiddenSequence_reportedOncePerTrace() {
        engine.addRule(ComplianceRuleEngine.ComplianceRule.forbiddenSequence(
            "S1", "A", "B", "desc"));
        // A→B appears twice in the trace, but should be reported only once per trace
        List<ComplianceRuleEngine.Trace> traces = List.of(
            new ComplianceRuleEngine.Trace("c1", List.of("A", "B", "A", "B"))
        );
        ComplianceRuleEngine.ComplianceReport report = engine.evaluate(traces);
        assertEquals(1, report.violations().size(),
            "Forbidden sequence in same trace should be reported once");
    }

    // -------------------------------------------------------------------------
    // REQUIRED_PREDECESSOR
    // -------------------------------------------------------------------------

    @Test
    void requiredPredecessor_present_compliant() {
        engine.addRule(ComplianceRuleEngine.ComplianceRule.requiredPredecessor(
            "SOX1", "Approve_Payment", "Execute_Payment", "Payment needs prior approval"));
        List<ComplianceRuleEngine.Trace> traces = List.of(
            new ComplianceRuleEngine.Trace("c1",
                List.of("Create_Invoice", "Approve_Payment", "Execute_Payment"))
        );
        ComplianceRuleEngine.ComplianceReport report = engine.evaluate(traces);
        assertTrue(report.compliant(), "Approval before payment must be compliant");
    }

    @Test
    void requiredPredecessor_missingPredecessor_violation() {
        engine.addRule(ComplianceRuleEngine.ComplianceRule.requiredPredecessor(
            "SOX1", "Approve_Payment", "Execute_Payment", "SOX approval required"));
        List<ComplianceRuleEngine.Trace> traces = List.of(
            new ComplianceRuleEngine.Trace("c1",
                List.of("Create_Invoice", "Execute_Payment"))  // no Approve_Payment
        );
        ComplianceRuleEngine.ComplianceReport report = engine.evaluate(traces);
        assertFalse(report.compliant(), "Missing Approve_Payment must violate SOX rule");
        assertEquals("SOX1", report.violations().get(0).ruleId());
    }

    @Test
    void requiredPredecessor_predecessorAfterActivity_violation() {
        // Predecessor exists but AFTER the activity — wrong order
        engine.addRule(ComplianceRuleEngine.ComplianceRule.requiredPredecessor(
            "SOX1", "Approve_Payment", "Execute_Payment", "Approval must precede execution"));
        List<ComplianceRuleEngine.Trace> traces = List.of(
            new ComplianceRuleEngine.Trace("c1",
                List.of("Execute_Payment", "Approve_Payment"))  // wrong order
        );
        ComplianceRuleEngine.ComplianceReport report = engine.evaluate(traces);
        assertFalse(report.compliant(), "Predecessor after activity must violate ordering rule");
    }

    @Test
    void requiredPredecessor_activityAbsent_noViolation() {
        // If the guarded activity doesn't appear in the trace, rule is not triggered
        engine.addRule(ComplianceRuleEngine.ComplianceRule.requiredPredecessor(
            "SOX1", "Approve_Payment", "Execute_Payment", "SOX rule"));
        List<ComplianceRuleEngine.Trace> traces = List.of(
            new ComplianceRuleEngine.Trace("c1", List.of("Create_Invoice", "Archive"))
        );
        ComplianceRuleEngine.ComplianceReport report = engine.evaluate(traces);
        assertTrue(report.compliant(), "Rule not triggered when activity is absent from trace");
    }

    // -------------------------------------------------------------------------
    // FORBIDDEN_ACTIVITY
    // -------------------------------------------------------------------------

    @Test
    void forbiddenActivity_absent_compliant() {
        engine.addRule(ComplianceRuleEngine.ComplianceRule.forbiddenActivity(
            "SEC1", "Delete_Audit_Log", "Audit logs must not be deleted"));
        List<ComplianceRuleEngine.Trace> traces = List.of(
            new ComplianceRuleEngine.Trace("c1", List.of("Submit", "Approve", "Archive"))
        );
        ComplianceRuleEngine.ComplianceReport report = engine.evaluate(traces);
        assertTrue(report.compliant(), "Forbidden activity absent = compliant");
    }

    @Test
    void forbiddenActivity_present_violation() {
        engine.addRule(ComplianceRuleEngine.ComplianceRule.forbiddenActivity(
            "SEC1", "Delete_Audit_Log", "Audit logs protected"));
        List<ComplianceRuleEngine.Trace> traces = List.of(
            new ComplianceRuleEngine.Trace("c1",
                List.of("Submit", "Delete_Audit_Log", "Approve"))
        );
        ComplianceRuleEngine.ComplianceReport report = engine.evaluate(traces);
        assertFalse(report.compliant(), "Forbidden activity must cause violation");
        assertEquals("SEC1", report.violations().get(0).ruleId());
    }

    // -------------------------------------------------------------------------
    // MAX_REPETITIONS
    // -------------------------------------------------------------------------

    @Test
    void maxRepetitions_factory_rejectsZeroCount() {
        assertThrows(IllegalArgumentException.class,
            () -> ComplianceRuleEngine.ComplianceRule.maxRepetitions("R1", "A", 0, "desc"));
    }

    @Test
    void maxRepetitions_withinLimit_compliant() {
        engine.addRule(ComplianceRuleEngine.ComplianceRule.maxRepetitions(
            "REP1", "Retry", 3, "Max 3 retries per case"));
        List<ComplianceRuleEngine.Trace> traces = List.of(
            new ComplianceRuleEngine.Trace("c1", List.of("Submit", "Retry", "Retry", "Approve"))
        );
        ComplianceRuleEngine.ComplianceReport report = engine.evaluate(traces);
        assertTrue(report.compliant(), "2 retries within limit of 3 = compliant");
    }

    @Test
    void maxRepetitions_exceedsLimit_violation() {
        engine.addRule(ComplianceRuleEngine.ComplianceRule.maxRepetitions(
            "REP1", "Retry", 2, "Max 2 retries"));
        List<ComplianceRuleEngine.Trace> traces = List.of(
            new ComplianceRuleEngine.Trace("c1",
                List.of("Submit", "Retry", "Retry", "Retry", "Approve"))  // 3 retries
        );
        ComplianceRuleEngine.ComplianceReport report = engine.evaluate(traces);
        assertFalse(report.compliant(), "3 retries exceeds limit of 2");
        assertEquals("REP1", report.violations().get(0).ruleId());
    }

    @Test
    void maxRepetitions_exactlyAtLimit_compliant() {
        engine.addRule(ComplianceRuleEngine.ComplianceRule.maxRepetitions(
            "REP1", "Retry", 2, "Max 2 retries"));
        List<ComplianceRuleEngine.Trace> traces = List.of(
            new ComplianceRuleEngine.Trace("c1",
                List.of("Submit", "Retry", "Retry", "Approve"))  // exactly 2
        );
        ComplianceRuleEngine.ComplianceReport report = engine.evaluate(traces);
        assertTrue(report.compliant(), "Exactly at limit must be compliant");
    }

    // -------------------------------------------------------------------------
    // Multiple rules (HIPAA + SOX scenario)
    // -------------------------------------------------------------------------

    @Test
    void multipleRules_allSatisfied_compliant() {
        engine.addRule(ComplianceRuleEngine.ComplianceRule.mandatoryActivity(
            "HIPAA-1", "Document_Consent", "Patient consent required"));
        engine.addRule(ComplianceRuleEngine.ComplianceRule.forbiddenSequence(
            "HIPAA-SOD", "Prescribe_Medication", "Dispense_Medication", "SOD required"));
        engine.addRule(ComplianceRuleEngine.ComplianceRule.requiredPredecessor(
            "SOX-1", "Approve_Payment", "Execute_Payment", "SOX approval"));

        List<ComplianceRuleEngine.Trace> traces = List.of(
            new ComplianceRuleEngine.Trace("c1",
                List.of("Document_Consent", "Prescribe_Medication", "Review",
                        "Dispense_Medication", "Approve_Payment", "Execute_Payment"))
        );
        ComplianceRuleEngine.ComplianceReport report = engine.evaluate(traces);
        assertTrue(report.compliant(), "All HIPAA+SOX rules satisfied = compliant");
    }

    @Test
    void multipleRules_someViolated_allViolationsReported() {
        engine.addRule(ComplianceRuleEngine.ComplianceRule.mandatoryActivity(
            "M1", "Audit_Step", "Required audit"));
        engine.addRule(ComplianceRuleEngine.ComplianceRule.forbiddenActivity(
            "F1", "Bypass_Control", "Control bypass forbidden"));

        List<ComplianceRuleEngine.Trace> traces = List.of(
            new ComplianceRuleEngine.Trace("c1",
                List.of("Work", "Bypass_Control", "Close"))  // missing Audit_Step + has Bypass
        );
        ComplianceRuleEngine.ComplianceReport report = engine.evaluate(traces);
        assertFalse(report.compliant());
        assertEquals(2, report.violations().size(),
            "Both rule violations (missing Audit + forbidden Bypass) must be reported");
    }

    // -------------------------------------------------------------------------
    // ComplianceReport metadata
    // -------------------------------------------------------------------------

    @Test
    void report_tracksRulesCheckedCount() {
        engine.addRule(ComplianceRuleEngine.ComplianceRule.mandatoryActivity("R1", "X", "d1"));
        engine.addRule(ComplianceRuleEngine.ComplianceRule.forbiddenActivity("R2", "Y", "d2"));
        ComplianceRuleEngine.ComplianceReport report = engine.evaluate(
            List.of(new ComplianceRuleEngine.Trace("c1", List.of("X"))));
        assertEquals(2, report.rulesChecked(), "Must report 2 rules checked");
    }

    @Test
    void report_tracksTracesCheckedCount() {
        engine.addRule(ComplianceRuleEngine.ComplianceRule.mandatoryActivity("R1", "X", "d"));
        List<ComplianceRuleEngine.Trace> traces = List.of(
            new ComplianceRuleEngine.Trace("c1", List.of("X")),
            new ComplianceRuleEngine.Trace("c2", List.of("X")),
            new ComplianceRuleEngine.Trace("c3", List.of("X"))
        );
        ComplianceRuleEngine.ComplianceReport report = engine.evaluate(traces);
        assertEquals(3, report.tracesChecked(), "Must report 3 traces checked");
    }

    @Test
    void report_violationsListIsUnmodifiable() {
        engine.addRule(ComplianceRuleEngine.ComplianceRule.mandatoryActivity("R1", "X", "d"));
        ComplianceRuleEngine.ComplianceReport report = engine.evaluate(
            List.of(new ComplianceRuleEngine.Trace("c1", List.of("Y"))));
        assertThrows(UnsupportedOperationException.class,
            () -> report.violations().clear());
    }

    // -------------------------------------------------------------------------
    // ComplianceRule factory methods
    // -------------------------------------------------------------------------

    @Test
    void ruleFactory_mandatoryActivity_hasCorrectType() {
        ComplianceRuleEngine.ComplianceRule rule =
            ComplianceRuleEngine.ComplianceRule.mandatoryActivity("R1", "X", "desc");
        assertEquals(ComplianceRuleEngine.RuleType.MANDATORY_ACTIVITY, rule.type());
        assertEquals("X", rule.activityA());
        assertNull(rule.activityB());
    }

    @Test
    void ruleFactory_forbiddenSequence_hasCorrectType() {
        ComplianceRuleEngine.ComplianceRule rule =
            ComplianceRuleEngine.ComplianceRule.forbiddenSequence("R1", "A", "B", "desc");
        assertEquals(ComplianceRuleEngine.RuleType.FORBIDDEN_SEQUENCE, rule.type());
        assertEquals("A", rule.activityA());
        assertEquals("B", rule.activityB());
    }

    @Test
    void ruleFactory_requiredPredecessor_hasCorrectType() {
        ComplianceRuleEngine.ComplianceRule rule =
            ComplianceRuleEngine.ComplianceRule.requiredPredecessor("R1", "Pre", "Act", "desc");
        assertEquals(ComplianceRuleEngine.RuleType.REQUIRED_PREDECESSOR, rule.type());
        assertEquals("Pre", rule.activityA());
        assertEquals("Act", rule.activityB());
    }

    @Test
    void ruleFactory_forbiddenActivity_hasCorrectType() {
        ComplianceRuleEngine.ComplianceRule rule =
            ComplianceRuleEngine.ComplianceRule.forbiddenActivity("R1", "X", "desc");
        assertEquals(ComplianceRuleEngine.RuleType.FORBIDDEN_ACTIVITY, rule.type());
        assertEquals("X", rule.activityA());
    }

    @Test
    void ruleFactory_maxRepetitions_hasCorrectTypeAndCount() {
        ComplianceRuleEngine.ComplianceRule rule =
            ComplianceRuleEngine.ComplianceRule.maxRepetitions("R1", "X", 5, "desc");
        assertEquals(ComplianceRuleEngine.RuleType.MAX_REPETITIONS, rule.type());
        assertEquals(5, rule.maxCount());
    }
}
