package org.yawlfoundation.yawl.integration.wizard.autonomic;

import org.yawlfoundation.yawl.integration.wizard.core.WizardPhase;
import org.yawlfoundation.yawl.integration.wizard.core.WizardSession;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Autonomic planner for the wizard system.
 *
 * <p>The planner is the 'P' in MAPE-K. It selects and sequences recovery
 * actions from diagnoses to form a recovery plan.
 *
 * <p>Planning strategy:
 * <ul>
 *   <li>Sort diagnoses by severity (CRITICAL first)</li>
 *   <li>For each diagnosis, map RecoveryAction to PlannedAction with parameters</li>
 *   <li>Deduplicate actions (same action type appears once)</li>
 *   <li>Preserve ordering (critical actions first)</li>
 *   <li>Set target phase based on actions planned</li>
 * </ul>
 *
 * <p>Plans are minimal and ordered. They specify exactly what the executor
 * should do to recover the wizard session.
 *
 * <p>Implementations are stateless and thread-safe.
 *
 * @see AutonomicAnalyzer for input (diagnoses)
 * @see AutonomicExecutor for execution of plans
 */
public class AutonomicPlanner {

    /**
     * A recovery plan produced by the planner.
     *
     * <p>A plan is a sequence of actions to be executed in order to recover
     * the wizard from detected problems. Each action has parameters that
     * customize its behavior.
     *
     * @param planId unique identifier for this plan
     * @param addressedDiagnoses diagnoses that this plan addresses
     * @param actions ordered list of actions to execute (sequence order matters)
     * @param targetPhase the phase the session should be in after plan execution
     */
    public record RecoveryPlan(
            String planId,
            List<WizardDiagnosis> addressedDiagnoses,
            List<PlannedAction> actions,
            WizardPhase targetPhase
    ) {
        /**
         * Compact constructor validates non-null fields.
         */
        public RecoveryPlan {
            Objects.requireNonNull(planId, "planId must not be null");
            Objects.requireNonNull(addressedDiagnoses, "addressedDiagnoses must not be null");
            Objects.requireNonNull(actions, "actions must not be null");
            Objects.requireNonNull(targetPhase, "targetPhase must not be null");

            addressedDiagnoses = List.copyOf(addressedDiagnoses);
            actions = List.copyOf(actions);
        }

        /**
         * Returns the number of actions in this plan.
         *
         * @return action count
         */
        public int actionCount() {
            return actions.size();
        }

        /**
         * Returns the first action, if any.
         *
         * @return first action or empty if plan is empty
         */
        public java.util.Optional<PlannedAction> firstAction() {
            return actions.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(actions.get(0));
        }
    }

    /**
     * A single action in a recovery plan.
     *
     * <p>An action specifies what the executor should do, with parameters
     * that customize behavior. The description is for logging/debugging.
     *
     * @param sequenceNumber position in execution order (1-indexed)
     * @param action the type of recovery action (RETRY_DISCOVERY, etc)
     * @param parameters customization parameters for the action
     * @param description human-readable description of what this action does
     */
    public record PlannedAction(
            int sequenceNumber,
            WizardDiagnosis.RecoveryAction action,
            Map<String, Object> parameters,
            String description
    ) {
        /**
         * Compact constructor ensures immutability of parameters.
         */
        public PlannedAction {
            if (sequenceNumber < 1) {
                throw new IllegalArgumentException("sequenceNumber must be >= 1");
            }
            Objects.requireNonNull(action, "action must not be null");
            Objects.requireNonNull(parameters, "parameters must not be null");
            Objects.requireNonNull(description, "description must not be null");

            parameters = Map.copyOf(parameters);
        }

        /**
         * Factory method: creates a planned action with given parameters.
         *
         * @param sequenceNumber position in execution order
         * @param action the recovery action type
         * @param parameters customization map
         * @param description human-readable description
         * @return new planned action
         */
        public static PlannedAction of(
                int sequenceNumber,
                WizardDiagnosis.RecoveryAction action,
                Map<String, Object> parameters,
                String description
        ) {
            return new PlannedAction(sequenceNumber, action, parameters, description);
        }

        /**
         * Factory method: creates a planned action without parameters.
         *
         * @param sequenceNumber position in execution order
         * @param action the recovery action type
         * @param description human-readable description
         * @return new planned action with empty parameters
         */
        public static PlannedAction of(
                int sequenceNumber,
                WizardDiagnosis.RecoveryAction action,
                String description
        ) {
            return new PlannedAction(sequenceNumber, action, Map.of(), description);
        }
    }

    /**
     * Creates a new autonomic planner with default configuration.
     */
    public AutonomicPlanner() {
        // No state needed; planner is stateless
    }

    /**
     * Build a recovery plan from a list of diagnoses.
     *
     * <p>Strategy:
     * <ul>
     *   <li>Sort diagnoses by severity (CRITICAL first)</li>
     *   <li>For each diagnosis, convert recommendation to PlannedAction</li>
     *   <li>Deduplicate actions (keep first occurrence)</li>
     *   <li>Number actions sequentially (1-indexed)</li>
     *   <li>Determine target phase based on actions</li>
     * </ul>
     *
     * @param diagnoses list of diagnoses from analyzer
     * @param session the current wizard session
     * @return a recovery plan to execute
     * @throws NullPointerException if diagnoses or session is null
     */
    public RecoveryPlan plan(List<WizardDiagnosis> diagnoses, WizardSession session) {
        Objects.requireNonNull(diagnoses, "diagnoses must not be null");
        Objects.requireNonNull(session, "session must not be null");

        // Sort diagnoses by severity (CRITICAL first)
        List<WizardDiagnosis> sortedDiagnoses = diagnoses.stream()
            .sorted(Comparator.comparing((WizardDiagnosis d) -> d.symptom().severity())
                .reversed())
            .toList();

        // Convert diagnoses to planned actions, deduplicating by action type
        List<PlannedAction> plannedActions = new ArrayList<>();
        Set<WizardDiagnosis.RecoveryAction> seenActions = new LinkedHashSet<>();

        for (WizardDiagnosis diagnosis : sortedDiagnoses) {
            WizardDiagnosis.RecoveryAction recommendedAction = diagnosis.recommendation();

            // Skip if we've already planned this action type
            if (seenActions.contains(recommendedAction)) {
                continue;
            }

            seenActions.add(recommendedAction);

            // Create planned action with parameters based on action type
            PlannedAction plannedAction = createPlannedAction(
                plannedActions.size() + 1,  // sequence number (1-indexed)
                recommendedAction,
                diagnosis,
                session
            );

            plannedActions.add(plannedAction);

            // If ABORT_WIZARD is encountered, stop processing further actions
            if (recommendedAction == WizardDiagnosis.RecoveryAction.ABORT_WIZARD) {
                break;
            }
        }

        // Determine target phase
        WizardPhase targetPhase = determineTargetPhase(plannedActions, session);

        return new RecoveryPlan(
            UUID.randomUUID().toString(),
            sortedDiagnoses,
            plannedActions,
            targetPhase
        );
    }

    /**
     * Create a planned action for a recovery recommendation.
     *
     * <p>Maps action types to PlannedAction with appropriate parameters.
     *
     * @param sequenceNumber the action's position in the sequence
     * @param action the recovery action type
     * @param diagnosis the diagnosis that led to this action
     * @param session the current wizard session
     * @return a planned action ready to execute
     */
    private PlannedAction createPlannedAction(
            int sequenceNumber,
            WizardDiagnosis.RecoveryAction action,
            WizardDiagnosis diagnosis,
            WizardSession session
    ) {
        Map<String, Object> parameters = new HashMap<>();

        String description = switch (action) {
            case RETRY_DISCOVERY -> {
                parameters.put("phase", WizardPhase.DISCOVERY);
                yield "Retry DISCOVERY phase to discover agents/tools again";
            }

            case FALLBACK_PATTERN -> {
                parameters.put("fallbackPattern", "WP-1");  // Sequence pattern
                parameters.put("reason", diagnosis.rootCause());
                yield "Fall back to Sequence pattern (WP-1)";
            }

            case RELAX_CONSTRAINTS -> {
                parameters.put("constraintRelaxation", true);
                parameters.put("reason", diagnosis.rootCause());
                yield "Relax configuration constraints to allow partial configs";
            }

            case RESET_TO_PHASE -> {
                WizardPhase resetPhase = diagnosis.symptom().phase();
                parameters.put("resetPhase", resetPhase);
                parameters.put("reason", diagnosis.rootCause());
                yield "Reset session to phase " + resetPhase;
            }

            case USE_DEFAULTS -> {
                parameters.put("useDefaultConfig", true);
                parameters.put("reason", diagnosis.rootCause());
                yield "Populate missing config with defaults";
            }

            case ABORT_WIZARD -> {
                parameters.put("reason", diagnosis.rootCause());
                yield "Abort wizard (terminal action)";
            }
        };

        return PlannedAction.of(sequenceNumber, action, parameters, description);
    }

    /**
     * Determine the target phase after executing the recovery plan.
     *
     * <p>Logic:
     * <ul>
     *   <li>If plan contains ABORT_WIZARD: target is FAILED</li>
     *   <li>If plan contains RETRY_DISCOVERY: target is DISCOVERY</li>
     *   <li>If plan contains RESET_TO_PHASE: target is the reset phase</li>
     *   <li>Otherwise: target is current phase (no phase change)</li>
     * </ul>
     *
     * @param actions list of planned actions
     * @param session current wizard session
     * @return the target phase after recovery
     */
    private WizardPhase determineTargetPhase(List<PlannedAction> actions, WizardSession session) {
        for (PlannedAction action : actions) {
            switch (action.action()) {
                case ABORT_WIZARD:
                    return WizardPhase.FAILED;

                case RETRY_DISCOVERY:
                    return WizardPhase.DISCOVERY;

                case RESET_TO_PHASE:
                    Object resetPhase = action.parameters().get("resetPhase");
                    if (resetPhase instanceof WizardPhase phase) {
                        return phase;
                    }
                    break;

                default:
                    // Other actions don't change target phase
                    break;
            }
        }

        // Default: stay in current phase
        return session.currentPhase();
    }
}
