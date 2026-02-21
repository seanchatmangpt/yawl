package org.yawlfoundation.yawl.integration.wizard.core;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Orchestrates the Autonomic A2A/MCP Wizard execution.
 *
 * <p>The AutonomicWizardEngine implements van der Aalst's workflow engine
 * control flow principles adapted for the wizard:
 * <ul>
 *   <li>Sequential task execution (WP-1 Sequence): steps execute in order</li>
 *   <li>Conditional branching (WP-4 Exclusive Choice): phase transitions based on results</li>
 *   <li>Autonomous self-management (MAPE-K): monitoring via audit trail integration point</li>
 * </ul>
 *
 * <p>The engine is stateless and thread-safe. All session state is encapsulated
 * in {@link WizardSession} records. Steps are registered and executed in a controlled manner.
 *
 * <p>Typical usage:
 * <pre>
 *   AutonomicWizardEngine engine = new AutonomicWizardEngine();
 *   WizardSession session = engine.initSession();
 *
 *   // Execute discovery step
 *   WizardStep<Map> discoveryStep = new DiscoveryStep();
 *   WizardStepResult<Map> result = engine.executeStep(session, discoveryStep);
 *
 *   if (result.isSuccess()) {
 *       session = engine.advance(session, result, WizardPhase.PATTERN_SELECTION);
 *       // ... execute next step
 *   } else {
 *       WizardResult failure = engine.fail(session, result.errors());
 *   }
 *
 *   WizardResult finalResult = engine.complete(session);
 * </pre>
 *
 * @see WizardSession
 * @see WizardStep
 * @see WizardPhase
 */
public class AutonomicWizardEngine {

    /**
     * Constructs a new AutonomicWizardEngine.
     *
     * <p>No configuration is required; the engine is a pure orchestrator
     * with no mutable state.
     */
    public AutonomicWizardEngine() {
        // Stateless orchestrator
    }

    /**
     * Creates a new wizard session in INIT phase.
     *
     * <p>The session is ready for the first step to execute. Use this to
     * begin a fresh wizard execution.
     *
     * @return fresh session with unique sessionId
     */
    public WizardSession initSession() {
        return WizardSession.newSession();
    }

    /**
     * Creates a new wizard session with pre-populated context.
     *
     * <p>Useful for resuming a wizard or importing initial configuration data.
     * The session is in INIT phase but contains provided context.
     *
     * @param initialContext map of initial context key-value pairs
     * @return fresh session with provided context (immutable copy)
     * @throws NullPointerException if initialContext is null
     */
    public WizardSession initSession(Map<String, Object> initialContext) {
        Objects.requireNonNull(initialContext, "initialContext cannot be null");
        return WizardSession.newSession(initialContext);
    }

    /**
     * Executes a single step given the current session state.
     *
     * <p>The method performs the following:
     * <ol>
     *   <li>Validates that session is in the step's required phase (throws if mismatch)</li>
     *   <li>Runs prerequisite validation; returns failure if prerequisites not met</li>
     *   <li>Invokes the step's execute method with the session</li>
     *   <li>Records the step execution in audit trail</li>
     *   <li>Returns the step's result</li>
     * </ol>
     *
     * <p>On success, the caller should use {@link #advance(WizardSession, WizardStepResult, WizardPhase)}
     * to transition to the next phase. On failure, use {@link #fail(WizardSession, List)}
     * to record the error.
     *
     * @param session the current session (must be in step's required phase)
     * @param step the step to execute
     * @param <T> the type of value the step produces
     * @return the step's result (success or failure)
     * @throws NullPointerException if session or step is null
     * @throws IllegalStateException if session is not in step's required phase
     */
    public <T> WizardStepResult<T> executeStep(WizardSession session, WizardStep<T> step) {
        Objects.requireNonNull(session, "session cannot be null");
        Objects.requireNonNull(step, "step cannot be null");

        // Phase validation: step can only execute in its required phase
        if (session.currentPhase() != step.requiredPhase()) {
            throw new IllegalStateException(
                String.format(
                    "Step %s requires phase %s but session is in phase %s",
                    step.stepId(),
                    step.requiredPhase(),
                    session.currentPhase()
                )
            );
        }

        // Prerequisite validation: if prerequisites fail, return failure result
        List<String> prerequisiteErrors = step.validatePrerequisites(session);
        if (!prerequisiteErrors.isEmpty()) {
            return WizardStepResult.failure(step.stepId(), prerequisiteErrors);
        }

        // Execute the step and return result
        return step.execute(session);
    }

    /**
     * Advances the session to a new phase after a successful step execution.
     *
     * <p>Creates a new session with:
     * <ul>
     *   <li>Updated currentPhase to nextPhase</li>
     *   <li>Step result's value merged into context under key "step_{stepId}_result"</li>
     *   <li>Audit event recorded for the phase transition</li>
     *   <li>lastModifiedAt timestamp updated</li>
     * </ul>
     *
     * <p>The result's value is automatically stored in context for downstream steps
     * to access. This pattern enables steps to pass data to each other via the context.
     *
     * @param session the current session
     * @param result the successful step result
     * @param nextPhase the phase to transition to
     * @return new session in the next phase with result integrated
     * @throws NullPointerException if any parameter is null
     */
    public WizardSession advance(
        WizardSession session,
        WizardStepResult<?> result,
        WizardPhase nextPhase
    ) {
        Objects.requireNonNull(session, "session cannot be null");
        Objects.requireNonNull(result, "result cannot be null");
        Objects.requireNonNull(nextPhase, "nextPhase cannot be null");

        // Store the step result in context for downstream steps
        String resultKey = "step_" + result.stepId() + "_result";
        WizardSession updatedSession = session.withContext(resultKey, result.value());

        // Transition to next phase with audit event
        return updatedSession.withPhase(
            nextPhase,
            result.stepId(),
            String.format("Completed %s, transitioning to %s", result.stepId(), nextPhase)
        );
    }

    /**
     * Builds a successful final result from a completed session.
     *
     * <p>Extracts all configuration from the session context and builds
     * a {@link WizardResult}. This is called when the wizard reaches
     * the terminal COMPLETE phase.
     *
     * <p>Configuration keys expected in session context:
     * <ul>
     *   <li>"mcp_config" → MCP configuration map</li>
     *   <li>"a2a_config" → A2A configuration map</li>
     *   <li>"workflow_config" → workflow configuration map</li>
     * </ul>
     *
     * <p>If any key is missing, an empty map is used.
     *
     * @param session the completed session
     * @return successful WizardResult with all configurations
     * @throws NullPointerException if session is null
     */
    public WizardResult complete(WizardSession session) {
        Objects.requireNonNull(session, "session cannot be null");
        return WizardResult.success(session);
    }

    /**
     * Builds a failure result from a session with error messages.
     *
     * <p>This is called when the wizard encounters an error and cannot
     * continue. The audit trail is preserved for debugging.
     *
     * @param session the failed session
     * @param errors list of error messages explaining the failure
     * @return failed WizardResult
     * @throws NullPointerException if session or errors is null
     */
    public WizardResult fail(WizardSession session, List<String> errors) {
        Objects.requireNonNull(session, "session cannot be null");
        Objects.requireNonNull(errors, "errors cannot be null");
        return WizardResult.failure(session, errors);
    }

    /**
     * Records an informational event in the session without changing phase.
     *
     * <p>Use this to log diagnostic information, warnings, or intermediate
     * results that don't trigger phase transitions.
     *
     * @param session the current session
     * @param event the event to record
     * @return new session with event appended to audit trail
     * @throws NullPointerException if session or event is null
     */
    public WizardSession recordEvent(WizardSession session, WizardEvent event) {
        Objects.requireNonNull(session, "session cannot be null");
        Objects.requireNonNull(event, "event cannot be null");
        return session.recordEvent(event);
    }

    /**
     * Helper method to create an informational event and record it.
     *
     * <p>Equivalent to:
     * <pre>
     *   recordEvent(session, WizardEvent.of(session.currentPhase(), stepId, message))
     * </pre>
     *
     * @param session the current session
     * @param stepId identifier of the step generating the event
     * @param message human-readable event description
     * @return new session with event recorded
     * @throws NullPointerException if any parameter is null
     */
    public WizardSession recordInfo(WizardSession session, String stepId, String message) {
        Objects.requireNonNull(session, "session cannot be null");
        Objects.requireNonNull(stepId, "stepId cannot be null");
        Objects.requireNonNull(message, "message cannot be null");

        WizardEvent event = WizardEvent.of(session.currentPhase(), stepId, message);
        return recordEvent(session, event);
    }

    /**
     * Helper method to validate that a session is in an expected phase.
     *
     * <p>Throws IllegalStateException if the session is not in the expected phase.
     * Useful for defensive checks before operations that require a specific phase.
     *
     * @param session the session to validate
     * @param expectedPhase the expected phase
     * @param contextMessage optional context message for error (may be null)
     * @throws NullPointerException if session or expectedPhase is null
     * @throws IllegalStateException if session is in different phase
     */
    public void validatePhase(WizardSession session, WizardPhase expectedPhase, String contextMessage) {
        Objects.requireNonNull(session, "session cannot be null");
        Objects.requireNonNull(expectedPhase, "expectedPhase cannot be null");

        if (session.currentPhase() != expectedPhase) {
            String message = contextMessage != null
                ? contextMessage
                : String.format("Expected phase %s but in %s", expectedPhase, session.currentPhase());
            throw new IllegalStateException(message);
        }
    }

    /**
     * Helper method to check if session is in a terminal state.
     *
     * <p>A session is terminal if it's in COMPLETE or FAILED phase.
     * No steps can be executed from a terminal state.
     *
     * @param session the session to check
     * @return true if session is in COMPLETE or FAILED phase
     * @throws NullPointerException if session is null
     */
    public boolean isTerminal(WizardSession session) {
        Objects.requireNonNull(session, "session cannot be null");
        return session.currentPhase() == WizardPhase.COMPLETE ||
               session.currentPhase() == WizardPhase.FAILED;
    }

    /**
     * Helper method to check if session is in a successful terminal state.
     *
     * @param session the session to check
     * @return true if session is in COMPLETE phase
     * @throws NullPointerException if session is null
     */
    public boolean isComplete(WizardSession session) {
        Objects.requireNonNull(session, "session cannot be null");
        return session.currentPhase() == WizardPhase.COMPLETE;
    }

    /**
     * Helper method to check if session is in a failed terminal state.
     *
     * @param session the session to check
     * @return true if session is in FAILED phase
     * @throws NullPointerException if session is null
     */
    public boolean isFailed(WizardSession session) {
        Objects.requireNonNull(session, "session cannot be null");
        return session.currentPhase() == WizardPhase.FAILED;
    }
}
