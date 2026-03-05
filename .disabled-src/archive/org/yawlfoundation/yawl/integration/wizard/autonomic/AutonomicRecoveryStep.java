package org.yawlfoundation.yawl.integration.wizard.autonomic;

import org.yawlfoundation.yawl.integration.wizard.core.WizardPhase;
import org.yawlfoundation.yawl.integration.wizard.core.WizardSession;
import org.yawlfoundation.yawl.integration.wizard.core.WizardStep;
import org.yawlfoundation.yawl.integration.wizard.core.WizardStepResult;

import java.util.List;
import java.util.Objects;

/**
 * A wizard step that runs the autonomic self-healing loop.
 *
 * <p>This step is called during the VALIDATION phase to perform a health
 * check and self-heal any detected problems before proceeding to DEPLOYMENT.
 *
 * <p>Step execution flow:
 * <ol>
 *   <li>Run AutonomicWizardManager.runLoop() on the session</li>
 *   <li>If loop recovers the session successfully, return success with recovered session</li>
 *   <li>If loop marks session as FAILED, return failure</li>
 * </ol>
 *
 * <p>The step is idempotent: running it multiple times on a healthy session
 * will return the session unchanged each time.
 *
 * <p>Step metadata:
 * <ul>
 *   <li>stepId: "autonomic.recovery"</li>
 *   <li>title: "Autonomic Self-Healing"</li>
 *   <li>requiredPhase: VALIDATION</li>
 *   <li>skippable: false (always run, even if previously completed)</li>
 * </ul>
 *
 * @see AutonomicWizardManager for the MAPE-K orchestrator
 */
public class AutonomicRecoveryStep implements WizardStep<WizardSession> {

    private static final String STEP_ID = "autonomic.recovery";
    private static final String STEP_TITLE = "Autonomic Self-Healing";

    private final AutonomicWizardManager manager;

    /**
     * Constructor: creates a recovery step with the given autonomic manager.
     *
     * @param manager the autonomic manager instance (typically withDefaults())
     * @throws NullPointerException if manager is null
     */
    public AutonomicRecoveryStep(AutonomicWizardManager manager) {
        this.manager = Objects.requireNonNull(manager, "manager must not be null");
    }

    /**
     * Factory method: creates a recovery step with default manager configuration.
     *
     * <p>This is the typical way to instantiate the step.
     *
     * @return new recovery step with default autonomic manager
     */
    public static AutonomicRecoveryStep withDefaults() {
        return new AutonomicRecoveryStep(AutonomicWizardManager.withDefaults());
    }

    /**
     * Returns the unique identifier for this step.
     *
     * @return "autonomic.recovery"
     */
    @Override
    public String stepId() {
        return STEP_ID;
    }

    /**
     * Returns the human-readable title of this step.
     *
     * @return "Autonomic Self-Healing"
     */
    @Override
    public String title() {
        return STEP_TITLE;
    }

    /**
     * Returns the required wizard phase for this step.
     *
     * <p>This step must be executed during the VALIDATION phase to perform
     * health checks and self-healing before deployment.
     *
     * @return WizardPhase.VALIDATION
     */
    @Override
    public WizardPhase requiredPhase() {
        return WizardPhase.VALIDATION;
    }

    /**
     * Executes the autonomic self-healing loop on the session.
     *
     * <p>Execution:
     * <ol>
     *   <li>Run the complete MAPE-K loop via manager.runLoop()</li>
     *   <li>If recovered session is in FAILED phase, return failure</li>
     *   <li>Otherwise return success with recovered session</li>
     * </ol>
     *
     * @param session the current wizard session
     * @return successful result with recovered session, or failure if recovery led to FAILED phase
     * @throws NullPointerException if session is null
     */
    @Override
    public WizardStepResult<WizardSession> execute(WizardSession session) {
        Objects.requireNonNull(session, "session must not be null");

        // Run the complete MAPE-K autonomic loop
        WizardSession recoveredSession = manager.runLoop(session);

        // Check if recovery led to terminal FAILED state
        if (recoveredSession.currentPhase() == WizardPhase.FAILED) {
            // Extract failure reason from context
            String reason = recoveredSession.get("failureReason", String.class)
                .orElse("Autonomic recovery determined the wizard cannot proceed");

            return WizardStepResult.failure(STEP_ID, reason);
        }

        // Recovery was successful; return the recovered session
        return WizardStepResult.success(STEP_ID, recoveredSession);
    }

    /**
     * Returns a description of this step's purpose.
     *
     * @return detailed description
     */
    @Override
    public String description() {
        return "Runs the MAPE-K autonomic self-healing loop to detect and recover from problems. " +
               "Monitors for symptoms like discovery failures, stalled phases, and configuration conflicts. " +
               "Plans and executes recovery actions automatically without human intervention.";
    }

    /**
     * Returns whether this step can be skipped.
     *
     * <p>This step is never skipped because health checking is always necessary
     * before deploying the configuration.
     *
     * @return false (step is always mandatory)
     */
    @Override
    public boolean isSkippable() {
        return false;
    }

    /**
     * Validates prerequisites before execution.
     *
     * <p>This step has no prerequisites beyond being in VALIDATION phase.
     * The autonomic loop will detect any actual problems.
     *
     * @param session the session to validate
     * @return empty list (no prerequisites)
     */
    @Override
    public List<String> validatePrerequisites(WizardSession session) {
        Objects.requireNonNull(session, "session must not be null");
        return List.of();
    }
}
