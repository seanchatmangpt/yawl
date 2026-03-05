package org.yawlfoundation.yawl.integration.wizard.core;

import java.util.List;
import java.util.Objects;

/**
 * Single step in the Autonomic A2A/MCP Wizard.
 *
 * <p>Each step transforms a {@link WizardSession} and produces a typed result.
 * Steps are executed by the {@link AutonomicWizardEngine} in a controlled manner,
 * with phase validation and prerequisite checking.
 *
 * <p>Steps implement van der Aalst's task-based workflow decomposition, where
 * the overall wizard is decomposed into small, focused units of work that
 * can be executed, monitored, and composed.
 *
 * <p>Implementations must be thread-safe and stateless (all state is in WizardSession).
 * Steps should be idempotent where possible—re-executing a step with the same
 * session state should produce the same result.
 *
 * @param <T> the type of value produced by this step when successful
 */
public interface WizardStep<T> {

    /**
     * Unique identifier for this step within the wizard.
     *
     * <p>Must be a valid Java identifier and should be stable across
     * wizard versions (for audit trail compatibility).
     *
     * @return the step identifier (non-null, non-empty)
     */
    String stepId();

    /**
     * Human-readable title of this step.
     *
     * <p>Used in UI/logs to describe what the step does.
     *
     * @return the step title (non-null, non-empty)
     */
    String title();

    /**
     * The wizard phase in which this step must be executed.
     *
     * <p>The {@link AutonomicWizardEngine} enforces that a step is only
     * executed when the session is in this phase. Attempts to execute a step
     * in the wrong phase will raise {@code IllegalStateException}.
     *
     * @return the required phase (non-null)
     */
    WizardPhase requiredPhase();

    /**
     * Executes this step given the current session state.
     *
     * <p>The implementation must:
     * <ul>
     *   <li>Not mutate the session (WizardSession is immutable)</li>
     *   <li>Return a successful result on normal completion</li>
     *   <li>Return a failure result with error messages on validation/processing failure</li>
     *   <li>Throw only in case of catastrophic/unexpected errors (not recoverable)</li>
     * </ul>
     *
     * <p>The step may read context from the session and should record its
     * findings in the result (via value field). The AutonomicWizardEngine
     * will integrate the result and update session context.
     *
     * @param session the current wizard session (immutable)
     * @return the step result (success or failure with typed value or errors)
     * @throws NullPointerException if session is null
     * @throws IllegalArgumentException if session is in wrong phase or missing required context
     */
    WizardStepResult<T> execute(WizardSession session);

    /**
     * Whether this step can be skipped if already configured.
     *
     * <p>If true, the engine may skip this step if it detects the step's
     * configuration is already present in the session context. This allows
     * for resumable/re-entrant wizards.
     *
     * <p>Default implementation returns false (step is always mandatory).
     *
     * @return true if step can be skipped on re-execution; false if always required
     */
    default boolean isSkippable() {
        return false;
    }

    /**
     * Validates prerequisites before execution.
     *
     * <p>Called by {@link AutonomicWizardEngine} before invoking {@link #execute(WizardSession)}.
     * Implementations should check that all required context keys are present,
     * that session state is valid, etc.
     *
     * <p>If prerequisites are not met, return a non-empty list of errors.
     * If all prerequisites pass, return an empty list.
     *
     * <p>Default implementation returns empty list (no prerequisites checked).
     * Subclasses should override to add actual validation.
     *
     * @param session the session to validate
     * @return list of error messages (empty if all prerequisites met)
     * @throws NullPointerException if session is null
     */
    default List<String> validatePrerequisites(WizardSession session) {
        Objects.requireNonNull(session, "session cannot be null");
        return List.of();
    }

    /**
     * Returns a human-readable description of this step's purpose.
     *
     * <p>Default implementation returns the title. Can be overridden
     * to provide more detailed documentation.
     *
     * @return description text (non-null)
     */
    default String description() {
        return title();
    }
}
