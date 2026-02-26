package org.yawlfoundation.yawl.integration.wizard.autonomic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.integration.wizard.core.WizardPhase;
import org.yawlfoundation.yawl.integration.wizard.core.WizardSession;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the AutonomicPlanner class.
 *
 * <p>Verifies that the planner:
 * <ul>
 *   <li>Converts diagnoses to planned actions</li>
 *   <li>Deduplicates actions (same action type once)</li>
 *   <li>Preserves ordering (critical actions first)</li>
 *   <li>Determines correct target phase</li>
 *   <li>Populates action parameters correctly</li>
 * </ul>
 */
@DisplayName("AutonomicPlanner Tests")
class AutonomicPlannerTest {

    private final AutonomicPlanner planner = new AutonomicPlanner();

    @Test
    @DisplayName("plan() produces RecoveryPlan with actions from diagnoses")
    void testPlanCreatesActions() {
        WizardSession session = WizardSession.newSession();

        WizardSymptom symptom = WizardSymptom.of(
            WizardPhase.DISCOVERY,
            WizardSymptom.SymptomType.DISCOVERY_EMPTY,
            "No agents"
        );

        WizardDiagnosis diagnosis = WizardDiagnosis.of(
            symptom,
            "No resources available",
            WizardDiagnosis.RecoveryAction.FALLBACK_PATTERN,
            0.8,
            List.of(WizardDiagnosis.RecoveryAction.FALLBACK_PATTERN)
        );

        AutonomicPlanner.RecoveryPlan plan = planner.plan(List.of(diagnosis), session);

        assertFalse(plan.actions().isEmpty());
        assertEquals(1, plan.actionCount());
        assertEquals(WizardDiagnosis.RecoveryAction.FALLBACK_PATTERN, plan.actions().get(0).action());
    }

    @Test
    @DisplayName("plan() deduplicates actions (same action type appears once)")
    void testPlanDeduplicatesActions() {
        WizardSession session = WizardSession.newSession();

        // Two diagnoses with same recovery action
        WizardDiagnosis diagnosis1 = WizardDiagnosis.of(
            WizardSymptom.of(WizardPhase.DISCOVERY, WizardSymptom.SymptomType.DISCOVERY_EMPTY, "No agents"),
            "No resources",
            WizardDiagnosis.RecoveryAction.FALLBACK_PATTERN,
            0.8,
            List.of(WizardDiagnosis.RecoveryAction.FALLBACK_PATTERN)
        );

        WizardDiagnosis diagnosis2 = WizardDiagnosis.of(
            WizardSymptom.of(WizardPhase.PATTERN_SELECTION, WizardSymptom.SymptomType.PATTERN_UNSOUND, "Pattern bad"),
            "Pattern failed validation",
            WizardDiagnosis.RecoveryAction.FALLBACK_PATTERN,
            0.9,
            List.of(WizardDiagnosis.RecoveryAction.FALLBACK_PATTERN)
        );

        AutonomicPlanner.RecoveryPlan plan = planner.plan(List.of(diagnosis1, diagnosis2), session);

        // Should have only 1 action (deduplicated)
        assertEquals(1, plan.actionCount());
    }

    @Test
    @DisplayName("plan() sorts diagnoses by severity (CRITICAL first)")
    void testPlanSortsBySeverity() {
        WizardSession session = WizardSession.newSession();

        List<WizardDiagnosis> diagnoses = List.of(
            WizardDiagnosis.of(
                WizardSymptom.of(WizardPhase.DISCOVERY, WizardSymptom.SymptomType.DISCOVERY_EMPTY, "No agents",
                    Map.of(), WizardSymptom.Severity.INFO),
                "No resources", WizardDiagnosis.RecoveryAction.FALLBACK_PATTERN, 0.5,
                List.of(WizardDiagnosis.RecoveryAction.FALLBACK_PATTERN)
            ),
            WizardDiagnosis.of(
                WizardSymptom.of(WizardPhase.VALIDATION, WizardSymptom.SymptomType.VALIDATION_FAILED, "Validation failed",
                    Map.of(), WizardSymptom.Severity.CRITICAL),
                "Config incomplete", WizardDiagnosis.RecoveryAction.USE_DEFAULTS, 0.7,
                List.of(WizardDiagnosis.RecoveryAction.USE_DEFAULTS)
            )
        );

        AutonomicPlanner.RecoveryPlan plan = planner.plan(diagnoses, session);

        // First action should be from CRITICAL diagnosis (USE_DEFAULTS)
        assertEquals(WizardDiagnosis.RecoveryAction.USE_DEFAULTS, plan.actions().get(0).action());
    }

    @Test
    @DisplayName("PlannedAction has sequential numbering (1-indexed)")
    void testPlannedActionsAreNumbered() {
        WizardSession session = WizardSession.newSession();

        List<WizardDiagnosis> diagnoses = List.of(
            WizardDiagnosis.of(
                WizardSymptom.of(WizardPhase.DISCOVERY, WizardSymptom.SymptomType.DISCOVERY_EMPTY, "No agents"),
                "No resources", WizardDiagnosis.RecoveryAction.RETRY_DISCOVERY, 0.8,
                List.of(WizardDiagnosis.RecoveryAction.RETRY_DISCOVERY)
            ),
            WizardDiagnosis.of(
                WizardSymptom.of(WizardPhase.VALIDATION, WizardSymptom.SymptomType.VALIDATION_FAILED, "Validation failed"),
                "Config incomplete", WizardDiagnosis.RecoveryAction.USE_DEFAULTS, 0.7,
                List.of(WizardDiagnosis.RecoveryAction.USE_DEFAULTS)
            )
        );

        AutonomicPlanner.RecoveryPlan plan = planner.plan(diagnoses, session);

        assertEquals(1, plan.actions().get(0).sequenceNumber());
        assertEquals(2, plan.actions().get(1).sequenceNumber());
    }

    @Test
    @DisplayName("plan() stops processing after ABORT_WIZARD action")
    void testPlanStopsAtAbort() {
        WizardSession session = WizardSession.newSession();

        List<WizardDiagnosis> diagnoses = List.of(
            WizardDiagnosis.of(
                WizardSymptom.of(WizardPhase.DEPLOYMENT, WizardSymptom.SymptomType.DEPLOYMENT_FAILED, "Deployment failed"),
                "Cannot connect", WizardDiagnosis.RecoveryAction.ABORT_WIZARD, 0.95,
                List.of(WizardDiagnosis.RecoveryAction.ABORT_WIZARD)
            ),
            WizardDiagnosis.of(
                WizardSymptom.of(WizardPhase.DISCOVERY, WizardSymptom.SymptomType.DISCOVERY_EMPTY, "No agents"),
                "No resources", WizardDiagnosis.RecoveryAction.FALLBACK_PATTERN, 0.8,
                List.of(WizardDiagnosis.RecoveryAction.FALLBACK_PATTERN)
            )
        );

        AutonomicPlanner.RecoveryPlan plan = planner.plan(diagnoses, session);

        // Should only have ABORT_WIZARD action (stops before FALLBACK_PATTERN)
        assertEquals(1, plan.actionCount());
        assertEquals(WizardDiagnosis.RecoveryAction.ABORT_WIZARD, plan.actions().get(0).action());
    }

    @Test
    @DisplayName("plan() determines target phase based on actions")
    void testPlanDeterminatesTargetPhase() {
        WizardSession session = WizardSession.newSession()
            .withPhase(WizardPhase.VALIDATION, "test", "Validating");

        WizardDiagnosis diagnosis = WizardDiagnosis.of(
            WizardSymptom.of(WizardPhase.DISCOVERY, WizardSymptom.SymptomType.DISCOVERY_EMPTY, "No agents"),
            "No resources", WizardDiagnosis.RecoveryAction.RETRY_DISCOVERY, 0.8,
            List.of(WizardDiagnosis.RecoveryAction.RETRY_DISCOVERY)
        );

        AutonomicPlanner.RecoveryPlan plan = planner.plan(List.of(diagnosis), session);

        // RETRY_DISCOVERY should target DISCOVERY phase
        assertEquals(WizardPhase.DISCOVERY, plan.targetPhase());
    }

    @Test
    @DisplayName("plan() targets FAILED when ABORT_WIZARD is in plan")
    void testPlanTargetsFailed() {
        WizardSession session = WizardSession.newSession();

        WizardDiagnosis diagnosis = WizardDiagnosis.of(
            WizardSymptom.of(WizardPhase.DEPLOYMENT, WizardSymptom.SymptomType.DEPLOYMENT_FAILED, "Deployment failed"),
            "Cannot connect", WizardDiagnosis.RecoveryAction.ABORT_WIZARD, 0.95,
            List.of(WizardDiagnosis.RecoveryAction.ABORT_WIZARD)
        );

        AutonomicPlanner.RecoveryPlan plan = planner.plan(List.of(diagnosis), session);

        assertEquals(WizardPhase.FAILED, plan.targetPhase());
    }

    @Test
    @DisplayName("PlannedAction includes parameters for action customization")
    void testPlannedActionHasParameters() {
        WizardSession session = WizardSession.newSession();

        WizardDiagnosis diagnosis = WizardDiagnosis.of(
            WizardSymptom.of(WizardPhase.DISCOVERY, WizardSymptom.SymptomType.DISCOVERY_EMPTY, "No agents"),
            "No resources", WizardDiagnosis.RecoveryAction.FALLBACK_PATTERN, 0.8,
            List.of(WizardDiagnosis.RecoveryAction.FALLBACK_PATTERN)
        );

        AutonomicPlanner.RecoveryPlan plan = planner.plan(List.of(diagnosis), session);

        AutonomicPlanner.PlannedAction action = plan.actions().get(0);
        assertFalse(action.parameters().isEmpty());
        assertTrue(action.parameters().containsKey("fallbackPattern"));
    }

    @Test
    @DisplayName("PlannedAction has human-readable description")
    void testPlannedActionHasDescription() {
        WizardSession session = WizardSession.newSession();

        WizardDiagnosis diagnosis = WizardDiagnosis.of(
            WizardSymptom.of(WizardPhase.DISCOVERY, WizardSymptom.SymptomType.DISCOVERY_EMPTY, "No agents"),
            "No resources", WizardDiagnosis.RecoveryAction.RETRY_DISCOVERY, 0.8,
            List.of(WizardDiagnosis.RecoveryAction.RETRY_DISCOVERY)
        );

        AutonomicPlanner.RecoveryPlan plan = planner.plan(List.of(diagnosis), session);

        String description = plan.actions().get(0).description();
        assertFalse(description.isEmpty());
        assertTrue(description.length() > 5);
    }

    @Test
    @DisplayName("plan() with empty diagnoses creates empty plan")
    void testPlanWithEmptyDiagnoses() {
        WizardSession session = WizardSession.newSession();

        AutonomicPlanner.RecoveryPlan plan = planner.plan(List.of(), session);

        assertTrue(plan.actions().isEmpty());
    }

    @Test
    @DisplayName("RecoveryPlan.firstAction() returns first action if present")
    void testRecoveryPlanFirstAction() {
        WizardSession session = WizardSession.newSession();

        WizardDiagnosis diagnosis = WizardDiagnosis.of(
            WizardSymptom.of(WizardPhase.DISCOVERY, WizardSymptom.SymptomType.DISCOVERY_EMPTY, "No agents"),
            "No resources", WizardDiagnosis.RecoveryAction.FALLBACK_PATTERN, 0.8,
            List.of(WizardDiagnosis.RecoveryAction.FALLBACK_PATTERN)
        );

        AutonomicPlanner.RecoveryPlan plan = planner.plan(List.of(diagnosis), session);

        assertTrue(plan.firstAction().isPresent());
        assertEquals(WizardDiagnosis.RecoveryAction.FALLBACK_PATTERN, plan.firstAction().get().action());
    }
}
