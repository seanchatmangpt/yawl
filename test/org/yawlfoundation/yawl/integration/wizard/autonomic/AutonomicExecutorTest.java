package org.yawlfoundation.yawl.integration.wizard.autonomic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.integration.wizard.core.WizardPhase;
import org.yawlfoundation.yawl.integration.wizard.core.WizardSession;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the AutonomicExecutor class.
 *
 * <p>Verifies that the executor:
 * <ul>
 *   <li>Applies recovery plans to sessions</li>
 *   <li>Mutates session state correctly for each action type</li>
 *   <li>Records execution events</li>
 *   <li>Handles terminal ABORT_WIZARD action</li>
 *   <li>Returns ExecutionResult with applied actions and errors</li>
 * </ul>
 */
@DisplayName("AutonomicExecutor Tests")
class AutonomicExecutorTest {

    private final AutonomicExecutor executor = new AutonomicExecutor();

    @Test
    @DisplayName("execute() RETRY_DISCOVERY resets phase to DISCOVERY and clears discoveries")
    void testRetryDiscoveryAction() {
        WizardSession session = WizardSession.newSession()
            .withPhase(WizardPhase.MCP_CONFIG, "test", "In config")
            .withContext("discoveredA2AAgents", List.of("agent1"))
            .withContext("discoveredMcpTools", List.of("tool1"));

        AutonomicPlanner.PlannedAction action = AutonomicPlanner.PlannedAction.of(
            1,
            WizardDiagnosis.RecoveryAction.RETRY_DISCOVERY,
            Map.of("phase", WizardPhase.DISCOVERY),
            "Retry discovery"
        );

        AutonomicPlanner.RecoveryPlan plan = new AutonomicPlanner.RecoveryPlan(
            "test-plan", List.of(), List.of(action), WizardPhase.DISCOVERY
        );

        AutonomicExecutor.ExecutionResult result = executor.execute(plan, session);

        assertTrue(result.success());
        assertEquals(WizardPhase.DISCOVERY, result.recoveredSession().currentPhase());
        // Discoveries should be cleared for rediscovery
        List<?> discoveredAgents = result.recoveredSession()
            .get("discoveredA2AAgents", List.class)
            .orElse(List.of());
        assertTrue(discoveredAgents.isEmpty(), "Discovered agents should be cleared");
    }

    @Test
    @DisplayName("execute() FALLBACK_PATTERN sets selectedPattern to WP-1")
    void testFallbackPatternAction() {
        WizardSession session = WizardSession.newSession()
            .withPhase(WizardPhase.PATTERN_SELECTION, "test", "Selecting pattern");

        AutonomicPlanner.PlannedAction action = AutonomicPlanner.PlannedAction.of(
            1,
            WizardDiagnosis.RecoveryAction.FALLBACK_PATTERN,
            Map.of("fallbackPattern", "WP-1", "reason", "Pattern validation failed"),
            "Fall back to Sequence pattern"
        );

        AutonomicPlanner.RecoveryPlan plan = new AutonomicPlanner.RecoveryPlan(
            "test-plan", List.of(), List.of(action), WizardPhase.PATTERN_SELECTION
        );

        AutonomicExecutor.ExecutionResult result = executor.execute(plan, session);

        assertTrue(result.success());
        String selectedPattern = result.recoveredSession()
            .get("selectedPattern", String.class)
            .orElse("");
        assertEquals("WP-1", selectedPattern);
        assertTrue(result.recoveredSession().get("patternFallback", Boolean.class).orElse(false));
    }

    @Test
    @DisplayName("execute() RELAX_CONSTRAINTS sets relaxation flag")
    void testRelaxConstraintsAction() {
        WizardSession session = WizardSession.newSession()
            .withPhase(WizardPhase.A2A_CONFIG, "test", "Config");

        AutonomicPlanner.PlannedAction action = AutonomicPlanner.PlannedAction.of(
            1,
            WizardDiagnosis.RecoveryAction.RELAX_CONSTRAINTS,
            Map.of("constraintRelaxation", true),
            "Relax constraints"
        );

        AutonomicPlanner.RecoveryPlan plan = new AutonomicPlanner.RecoveryPlan(
            "test-plan", List.of(), List.of(action), WizardPhase.A2A_CONFIG
        );

        AutonomicExecutor.ExecutionResult result = executor.execute(plan, session);

        assertTrue(result.success());
        assertTrue(result.recoveredSession()
            .get("constraintRelaxation", Boolean.class)
            .orElse(false));
    }

    @Test
    @DisplayName("execute() USE_DEFAULTS populates missing config with defaults")
    void testUseDefaultsAction() {
        WizardSession session = WizardSession.newSession()
            .withPhase(WizardPhase.VALIDATION, "test", "Validation");

        AutonomicPlanner.PlannedAction action = AutonomicPlanner.PlannedAction.of(
            1,
            WizardDiagnosis.RecoveryAction.USE_DEFAULTS,
            "Use defaults"
        );

        AutonomicPlanner.RecoveryPlan plan = new AutonomicPlanner.RecoveryPlan(
            "test-plan", List.of(), List.of(action), WizardPhase.VALIDATION
        );

        AutonomicExecutor.ExecutionResult result = executor.execute(plan, session);

        assertTrue(result.success());
        // Should have set default pattern
        assertTrue(result.recoveredSession().has("selectedPattern"));
        assertEquals("WP-1", result.recoveredSession().get("selectedPattern", String.class).orElse(""));
    }

    @Test
    @DisplayName("execute() ABORT_WIZARD transitions session to FAILED phase")
    void testAbortWizardAction() {
        WizardSession session = WizardSession.newSession()
            .withPhase(WizardPhase.DEPLOYMENT, "test", "Deploying");

        AutonomicPlanner.PlannedAction action = AutonomicPlanner.PlannedAction.of(
            1,
            WizardDiagnosis.RecoveryAction.ABORT_WIZARD,
            Map.of("reason", "Deployment failed"),
            "Abort wizard"
        );

        AutonomicPlanner.RecoveryPlan plan = new AutonomicPlanner.RecoveryPlan(
            "test-plan", List.of(), List.of(action), WizardPhase.FAILED
        );

        AutonomicExecutor.ExecutionResult result = executor.execute(plan, session);

        assertTrue(result.success());
        assertEquals(WizardPhase.FAILED, result.recoveredSession().currentPhase());
    }

    @Test
    @DisplayName("execute() stops processing after ABORT_WIZARD")
    void testExecutionStopsAtAbort() {
        WizardSession session = WizardSession.newSession()
            .withPhase(WizardPhase.DEPLOYMENT, "test", "Deploying");

        List<AutonomicPlanner.PlannedAction> actions = List.of(
            AutonomicPlanner.PlannedAction.of(
                1,
                WizardDiagnosis.RecoveryAction.ABORT_WIZARD,
                "Abort"
            ),
            AutonomicPlanner.PlannedAction.of(
                2,
                WizardDiagnosis.RecoveryAction.FALLBACK_PATTERN,
                "Fallback (should not execute)"
            )
        );

        AutonomicPlanner.RecoveryPlan plan = new AutonomicPlanner.RecoveryPlan(
            "test-plan", List.of(), actions, WizardPhase.FAILED
        );

        AutonomicExecutor.ExecutionResult result = executor.execute(plan, session);

        // Only first action should be applied
        assertEquals(1, result.appliedActions().size());
    }

    @Test
    @DisplayName("execute() records execution events in session")
    void testExecutionRecordsEvents() {
        WizardSession session = WizardSession.newSession()
            .withPhase(WizardPhase.PATTERN_SELECTION, "test", "Initial");

        AutonomicPlanner.PlannedAction action = AutonomicPlanner.PlannedAction.of(
            1,
            WizardDiagnosis.RecoveryAction.FALLBACK_PATTERN,
            "Fallback pattern"
        );

        AutonomicPlanner.RecoveryPlan plan = new AutonomicPlanner.RecoveryPlan(
            "test-plan", List.of(), List.of(action), WizardPhase.PATTERN_SELECTION
        );

        AutonomicExecutor.ExecutionResult result = executor.execute(plan, session);

        // Session should have more events than before
        assertTrue(result.recoveredSession().eventCount() > session.eventCount());
    }

    @Test
    @DisplayName("execute() returns list of applied action descriptions")
    void testExecutionReturnsAppliedActions() {
        WizardSession session = WizardSession.newSession();

        AutonomicPlanner.PlannedAction action = AutonomicPlanner.PlannedAction.of(
            1,
            WizardDiagnosis.RecoveryAction.FALLBACK_PATTERN,
            "Test action description"
        );

        AutonomicPlanner.RecoveryPlan plan = new AutonomicPlanner.RecoveryPlan(
            "test-plan", List.of(), List.of(action), WizardPhase.DISCOVERY
        );

        AutonomicExecutor.ExecutionResult result = executor.execute(plan, session);

        assertTrue(result.appliedActions().size() > 0);
        assertTrue(result.appliedActions().get(0).contains("Test action description"));
    }

    @Test
    @DisplayName("ExecutionResult.success() creates successful result")
    void testExecutionResultSuccess() {
        WizardSession session = WizardSession.newSession();
        List<String> actions = List.of("action1", "action2");

        AutonomicExecutor.ExecutionResult result = AutonomicExecutor.ExecutionResult.success(session, actions);

        assertTrue(result.success());
        assertEquals(session, result.recoveredSession());
        assertEquals(2, result.appliedActions().size());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    @DisplayName("ExecutionResult.failure() creates failed result with errors")
    void testExecutionResultFailure() {
        WizardSession session = WizardSession.newSession();
        List<String> errors = List.of("Error 1", "Error 2");

        AutonomicExecutor.ExecutionResult result = AutonomicExecutor.ExecutionResult.failure(
            session, List.of(), errors
        );

        assertFalse(result.success());
        assertEquals(2, result.errors().size());
    }

    @Test
    @DisplayName("execute() handles multiple actions in sequence")
    void testExecutionWithMultipleActions() {
        WizardSession session = WizardSession.newSession()
            .withPhase(WizardPhase.DISCOVERY, "test", "Discovery");

        List<AutonomicPlanner.PlannedAction> actions = List.of(
            AutonomicPlanner.PlannedAction.of(
                1,
                WizardDiagnosis.RecoveryAction.FALLBACK_PATTERN,
                "Step 1: Fallback pattern"
            ),
            AutonomicPlanner.PlannedAction.of(
                2,
                WizardDiagnosis.RecoveryAction.USE_DEFAULTS,
                "Step 2: Use defaults"
            )
        );

        AutonomicPlanner.RecoveryPlan plan = new AutonomicPlanner.RecoveryPlan(
            "test-plan", List.of(), actions, WizardPhase.DISCOVERY
        );

        AutonomicExecutor.ExecutionResult result = executor.execute(plan, session);

        assertTrue(result.success());
        assertEquals(2, result.appliedActions().size());
        // Both actions should be applied
        assertTrue(result.recoveredSession().has("selectedPattern"));
    }

    @Test
    @DisplayName("execute() with empty plan returns unchanged session")
    void testExecutionWithEmptyPlan() {
        WizardSession session = WizardSession.newSession()
            .withPhase(WizardPhase.DISCOVERY, "test", "Discovery");

        AutonomicPlanner.RecoveryPlan plan = new AutonomicPlanner.RecoveryPlan(
            "empty-plan", List.of(), List.of(), WizardPhase.DISCOVERY
        );

        AutonomicExecutor.ExecutionResult result = executor.execute(plan, session);

        assertTrue(result.success());
        assertTrue(result.appliedActions().isEmpty());
    }
}
