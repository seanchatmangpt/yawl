package org.yawlfoundation.yawl.integration.wizard.autonomic;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Root cause analysis result from the autonomic analyzer.
 *
 * <p>A diagnosis maps a detected symptom to a root cause explanation,
 * a recommended recovery action, and a confidence score based on evidence.
 *
 * <p>Diagnosis is produced during the Analyze phase of the MAPE-K
 * autonomic control loop. See {@link AutonomicAnalyzer}.
 *
 * <p>All fields are immutable; use static factory methods to create instances.
 *
 * @param diagnosisId unique identifier for this diagnosis
 * @param symptom the symptom being diagnosed
 * @param rootCause human-readable explanation of root cause
 * @param recommendation recovery action recommendation
 * @param confidence confidence level (0.0 to 1.0) based on evidence matching
 * @param possibleActions recovery actions that could address this diagnosis
 *
 * @see AutonomicAnalyzer for how diagnoses are produced from symptoms
 * @see AutonomicPlanner for how diagnoses are converted to recovery plans
 */
public record WizardDiagnosis(
        String diagnosisId,
        WizardSymptom symptom,
        String rootCause,
        RecoveryAction recommendation,
        double confidence,
        List<RecoveryAction> possibleActions
) {
    /**
     * Compact constructor validates and normalizes fields.
     */
    public WizardDiagnosis {
        Objects.requireNonNull(diagnosisId, "diagnosisId must not be null");
        Objects.requireNonNull(symptom, "symptom must not be null");
        Objects.requireNonNull(rootCause, "rootCause must not be null");
        Objects.requireNonNull(recommendation, "recommendation must not be null");
        Objects.requireNonNull(possibleActions, "possibleActions must not be null");

        // Validate confidence is in valid range
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException(
                String.format("confidence must be in range [0.0, 1.0], got %.2f", confidence)
            );
        }

        // Defensive copy: ensure possibleActions is immutable
        possibleActions = Collections.unmodifiableList(List.copyOf(possibleActions));
    }

    /**
     * Factory method: creates a diagnosis with auto-generated ID.
     *
     * @param symptom the symptom being diagnosed
     * @param rootCause explanation of root cause
     * @param recommendation primary recovery action
     * @param confidence confidence score (0.0-1.0)
     * @param possibleActions alternative recovery actions
     * @return new diagnosis
     */
    public static WizardDiagnosis of(
            WizardSymptom symptom,
            String rootCause,
            RecoveryAction recommendation,
            double confidence,
            List<RecoveryAction> possibleActions
    ) {
        return new WizardDiagnosis(
            UUID.randomUUID().toString(),
            symptom,
            rootCause,
            recommendation,
            confidence,
            possibleActions
        );
    }

    /**
     * Recovery actions that the planner can select to address a diagnosis.
     *
     * <p>These map 1:1 to executable corrective actions in {@link AutonomicExecutor}.
     * Each action has specific parameters and side effects on the wizard session.
     */
    public enum RecoveryAction {
        /**
         * Re-run the DISCOVERY phase to look for agents/tools again.
         * Typically used when DISCOVERY_EMPTY symptom indicates resources became unavailable.
         */
        RETRY_DISCOVERY,

        /**
         * Fall back to a simpler, well-known workflow pattern (WP-1: Sequence).
         * Used when pattern validation fails or discovery is empty.
         */
        FALLBACK_PATTERN,

        /**
         * Relax configuration constraints (e.g., allow missing optional tools).
         * Used when CONFIG_CONFLICT indicates over-constraining requirements.
         */
        RELAX_CONSTRAINTS,

        /**
         * Reset wizard to a previous phase and resume from there.
         * Used when phase is stalled and earlier steps need re-execution.
         */
        RESET_TO_PHASE,

        /**
         * Populate missing configuration with sensible defaults.
         * Used when VALIDATION_FAILED due to incomplete configuration.
         */
        USE_DEFAULTS,

        /**
         * Terminate the wizard and mark it as FAILED.
         * Terminal action: no further recovery is possible.
         */
        ABORT_WIZARD
    }
}
