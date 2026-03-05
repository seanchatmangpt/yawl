package org.yawlfoundation.yawl.integration.wizard.autonomic;

import org.yawlfoundation.yawl.integration.wizard.core.WizardEvent;
import org.yawlfoundation.yawl.integration.wizard.core.WizardPhase;
import org.yawlfoundation.yawl.integration.wizard.core.WizardSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Autonomic executor for the wizard system.
 *
 * <p>The executor is the 'E' in MAPE-K. It applies recovery plans to
 * restore impaired wizard sessions by mutating session state according
 * to the plan.
 *
 * <p>Execution strategy:
 * <ul>
 *   <li>Process planned actions in sequence order</li>
 *   <li>For each action, apply state mutations to the session</li>
 *   <li>Record execution events in the session</li>
 *   <li>If ABORT_WIZARD encountered, stop and mark session FAILED</li>
 *   <li>Return ExecutionResult with success status and recovered session</li>
 * </ul>
 *
 * <p>State mutations map to session context updates and phase transitions:
 * <ul>
 *   <li>RETRY_DISCOVERY: reset to DISCOVERY phase, clear stale discovery data</li>
 *   <li>FALLBACK_PATTERN: set selectedPattern to fallback in context</li>
 *   <li>RELAX_CONSTRAINTS: set constraintRelaxation flag in context</li>
 *   <li>RESET_TO_PHASE: advance/revert to target phase</li>
 *   <li>USE_DEFAULTS: populate default values in context</li>
 *   <li>ABORT_WIZARD: transition to FAILED phase</li>
 * </ul>
 *
 * <p>Implementations are stateless and thread-safe.
 *
 * @see AutonomicPlanner for input (recovery plans)
 * @see AutonomicWizardManager for orchestration
 */
public class AutonomicExecutor {

    /**
     * Result of executing a recovery plan.
     *
     * @param success whether execution completed successfully
     * @param recoveredSession the session after recovery (may have updated state)
     * @param appliedActions list of action descriptions that were applied
     * @param errors list of error messages encountered during execution
     */
    public record ExecutionResult(
            boolean success,
            WizardSession recoveredSession,
            List<String> appliedActions,
            List<String> errors
    ) {
        /**
         * Compact constructor ensures immutability of list fields.
         */
        public ExecutionResult {
            Objects.requireNonNull(recoveredSession, "recoveredSession must not be null");
            Objects.requireNonNull(appliedActions, "appliedActions must not be null");
            Objects.requireNonNull(errors, "errors must not be null");

            appliedActions = Collections.unmodifiableList(List.copyOf(appliedActions));
            errors = Collections.unmodifiableList(List.copyOf(errors));
        }

        /**
         * Factory method: creates a successful result.
         *
         * @param recoveredSession the recovered session
         * @param appliedActions actions that were applied
         * @return successful execution result
         */
        public static ExecutionResult success(WizardSession recoveredSession, List<String> appliedActions) {
            return new ExecutionResult(true, recoveredSession, appliedActions, List.of());
        }

        /**
         * Factory method: creates a failed result.
         *
         * @param recoveredSession the session (may be unchanged or partially recovered)
         * @param appliedActions actions that were applied before failure
         * @param errors error messages
         * @return failed execution result
         */
        public static ExecutionResult failure(WizardSession recoveredSession, List<String> appliedActions, List<String> errors) {
            return new ExecutionResult(false, recoveredSession, appliedActions, errors);
        }
    }

    /**
     * Creates a new autonomic executor with default configuration.
     */
    public AutonomicExecutor() {
        // No state needed; executor is stateless
    }

    /**
     * Apply a recovery plan to an impaired wizard session.
     *
     * <p>Processes each PlannedAction in sequence order:
     * <ol>
     *   <li>Extract action type and parameters</li>
     *   <li>Apply state mutations to session</li>
     *   <li>Record execution event</li>
     *   <li>If ABORT_WIZARD encountered, stop and mark FAILED</li>
     *   <li>Collect applied action descriptions</li>
     *   <li>Return ExecutionResult with final session state</li>
     * </ol>
     *
     * @param plan the recovery plan to execute
     * @param session the current wizard session
     * @return execution result with recovered session and applied actions
     * @throws NullPointerException if plan or session is null
     */
    public ExecutionResult execute(AutonomicPlanner.RecoveryPlan plan, WizardSession session) {
        Objects.requireNonNull(plan, "plan must not be null");
        Objects.requireNonNull(session, "session must not be null");

        List<String> appliedActions = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        WizardSession currentSession = session;

        // Process each action in the plan
        for (AutonomicPlanner.PlannedAction plannedAction : plan.actions()) {
            try {
                // Execute the action
                ExecutionContext ctx = new ExecutionContext(
                    currentSession,
                    plannedAction.action(),
                    plannedAction.parameters(),
                    appliedActions,
                    errors
                );

                // Apply action-specific mutations
                switch (plannedAction.action()) {
                    case RETRY_DISCOVERY:
                        currentSession = executeRetryDiscovery(currentSession, plannedAction);
                        break;

                    case FALLBACK_PATTERN:
                        currentSession = executeFallbackPattern(currentSession, plannedAction);
                        break;

                    case RELAX_CONSTRAINTS:
                        currentSession = executeRelaxConstraints(currentSession, plannedAction);
                        break;

                    case RESET_TO_PHASE:
                        currentSession = executeResetToPhase(currentSession, plannedAction);
                        break;

                    case USE_DEFAULTS:
                        currentSession = executeUseDefaults(currentSession, plannedAction);
                        break;

                    case ABORT_WIZARD:
                        currentSession = executeAbortWizard(currentSession, plannedAction);
                        appliedActions.add(plannedAction.description());
                        // Terminal action: stop processing
                        return ExecutionResult.success(currentSession, appliedActions);
                }

                // Record successful action execution
                appliedActions.add(plannedAction.description());

            } catch (Exception e) {
                // Record error and continue (best effort recovery)
                String errorMsg = "Failed to execute " + plannedAction.action() + ": " + e.getMessage();
                errors.add(errorMsg);
            }
        }

        // Determine success: true if no errors, false if any error
        boolean success = errors.isEmpty();

        if (success) {
            return ExecutionResult.success(currentSession, appliedActions);
        } else {
            return ExecutionResult.failure(currentSession, appliedActions, errors);
        }
    }

    /**
     * Execute RETRY_DISCOVERY action.
     *
     * <p>Resets the session to DISCOVERY phase and clears stale discovery data
     * (discoveredA2AAgents, discoveredMcpTools) to allow rediscovery.
     */
    private WizardSession executeRetryDiscovery(WizardSession session, AutonomicPlanner.PlannedAction action) {
        WizardSession result = session.withPhase(
            WizardPhase.DISCOVERY,
            "autonomic.recovery",
            "Retrying discovery phase"
        );

        // Clear stale discovery results to force rediscovery
        result = result.withContext("discoveredA2AAgents", List.of());
        result = result.withContext("discoveredMcpTools", List.of());

        // Record recovery event
        WizardEvent recoveryEvent = WizardEvent.of(
            WizardPhase.DISCOVERY,
            "autonomic.recovery",
            "Discovery rediscovery initiated by autonomic system",
            action.parameters()
        );
        result = result.recordEvent(recoveryEvent);

        return result;
    }

    /**
     * Execute FALLBACK_PATTERN action.
     *
     * <p>Sets the selected pattern to the fallback (WP-1: Sequence) in context
     * and records the reason for fallback.
     */
    private WizardSession executeFallbackPattern(WizardSession session, AutonomicPlanner.PlannedAction action) {
        WizardSession result = session.withContext("selectedPattern", "WP-1");  // Sequence pattern
        result = result.withContext("patternFallback", true);
        result = result.withContext("fallbackReason", action.parameters().getOrDefault("reason", ""));

        // Record recovery event
        WizardEvent recoveryEvent = WizardEvent.of(
            session.currentPhase(),
            "autonomic.recovery",
            "Pattern fallback to WP-1 (Sequence) initiated",
            action.parameters()
        );
        result = result.recordEvent(recoveryEvent);

        return result;
    }

    /**
     * Execute RELAX_CONSTRAINTS action.
     *
     * <p>Sets a flag in context indicating that configuration constraints
     * should be relaxed (allowing partial configs, fewer required fields, etc).
     */
    private WizardSession executeRelaxConstraints(WizardSession session, AutonomicPlanner.PlannedAction action) {
        WizardSession result = session.withContext("constraintRelaxation", true);
        result = result.withContext("relaxationReason", action.parameters().getOrDefault("reason", ""));

        // Record recovery event
        WizardEvent recoveryEvent = WizardEvent.of(
            session.currentPhase(),
            "autonomic.recovery",
            "Configuration constraints relaxed",
            action.parameters()
        );
        result = result.recordEvent(recoveryEvent);

        return result;
    }

    /**
     * Execute RESET_TO_PHASE action.
     *
     * <p>Transitions the session to the specified phase (typically an earlier
     * phase to allow re-execution of earlier steps).
     */
    private WizardSession executeResetToPhase(WizardSession session, AutonomicPlanner.PlannedAction action) {
        Object resetPhaseObj = action.parameters().get("resetPhase");
        WizardPhase resetPhase;

        if (resetPhaseObj instanceof WizardPhase phase) {
            resetPhase = phase;
        } else {
            throw new IllegalArgumentException("resetPhase parameter must be a WizardPhase");
        }

        WizardSession result = session.withPhase(
            resetPhase,
            "autonomic.recovery",
            "Session reset to phase " + resetPhase + " for retry"
        );

        // Record recovery event
        WizardEvent recoveryEvent = WizardEvent.of(
            resetPhase,
            "autonomic.recovery",
            "Session reset by autonomic system",
            action.parameters()
        );
        result = result.recordEvent(recoveryEvent);

        return result;
    }

    /**
     * Execute USE_DEFAULTS action.
     *
     * <p>Populates missing configuration fields with sensible defaults:
     * <ul>
     *   <li>selectedPattern: "WP-1" (Sequence)</li>
     *   <li>mcpToolBinding: empty map</li>
     *   <li>a2aAgentSkills: empty map</li>
     * </ul>
     */
    private WizardSession executeUseDefaults(WizardSession session, AutonomicPlanner.PlannedAction action) {
        WizardSession result = session;

        // Set defaults only if not already set
        if (!result.has("selectedPattern")) {
            result = result.withContext("selectedPattern", "WP-1");
        }

        if (!result.has("mcpToolBinding")) {
            result = result.withContext("mcpToolBinding", new HashMap<>());
        }

        if (!result.has("a2aAgentSkills")) {
            result = result.withContext("a2aAgentSkills", new HashMap<>());
        }

        result = result.withContext("defaultsApplied", true);

        // Record recovery event
        WizardEvent recoveryEvent = WizardEvent.of(
            session.currentPhase(),
            "autonomic.recovery",
            "Configuration defaults applied",
            action.parameters()
        );
        result = result.recordEvent(recoveryEvent);

        return result;
    }

    /**
     * Execute ABORT_WIZARD action.
     *
     * <p>Transitions the session to the FAILED phase (terminal state).
     * This is a no-recovery action indicating the wizard cannot proceed.
     */
    private WizardSession executeAbortWizard(WizardSession session, AutonomicPlanner.PlannedAction action) {
        return session.withPhase(
            WizardPhase.FAILED,
            "autonomic.recovery",
            "Wizard aborted by autonomic system. Reason: " + action.parameters().getOrDefault("reason", "")
        );
    }

    /**
     * Helper record for maintaining context during action execution.
     * (Not exposed publicly; used internally by executor)
     */
    private record ExecutionContext(
            WizardSession session,
            WizardDiagnosis.RecoveryAction action,
            Map<String, Object> parameters,
            List<String> appliedActions,
            List<String> errors
    ) {
    }
}
