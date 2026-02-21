package org.yawlfoundation.yawl.integration.wizard.autonomic;

import org.yawlfoundation.yawl.integration.wizard.core.WizardPhase;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A symptom detected by the autonomic monitor.
 *
 * <p>Symptoms are the raw observations before analysis and diagnosis.
 * They capture quantitative evidence (empty discovery, stalled phase, etc)
 * without yet assigning root cause or recovery strategy.
 *
 * <p>Symptom detection is the first phase (Monitor) of the MAPE-K
 * autonomic control loop. See {@link AutonomicMonitor}.
 *
 * <p>All fields are immutable; use static factory methods to create instances.
 *
 * @param symptomId unique identifier for this symptom (auto-generated)
 * @param phase wizard phase when symptom was detected
 * @param type the category of symptom (DISCOVERY_EMPTY, PHASE_STALLED, etc)
 * @param description human-readable symptom description
 * @param evidence immutable map of diagnostic evidence (raw measurements)
 * @param detectedAt timestamp when symptom was first detected (UTC)
 * @param severity urgency classification (INFO, WARNING, CRITICAL)
 *
 * @see AutonomicMonitor for how symptoms are detected
 * @see WizardDiagnosis for analysis of symptoms
 */
public record WizardSymptom(
        String symptomId,
        WizardPhase phase,
        SymptomType type,
        String description,
        Map<String, Object> evidence,
        Instant detectedAt,
        Severity severity
) {
    /**
     * Compact constructor ensures immutability of mutable fields.
     */
    public WizardSymptom {
        Objects.requireNonNull(symptomId, "symptomId must not be null");
        Objects.requireNonNull(phase, "phase must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(description, "description must not be null");
        Objects.requireNonNull(severity, "severity must not be null");

        // Defensive copy: ensure evidence is immutable
        evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
        if (detectedAt == null) {
            detectedAt = Instant.now();
        }
    }

    /**
     * Factory method: creates a symptom with auto-generated ID and current timestamp.
     *
     * @param phase the wizard phase where symptom was detected
     * @param type the symptom type (category)
     * @param description human-readable symptom description
     * @return new symptom with WARNING severity and empty evidence
     */
    public static WizardSymptom of(WizardPhase phase, SymptomType type, String description) {
        return new WizardSymptom(
            UUID.randomUUID().toString(),
            phase, type, description, Map.of(), Instant.now(), Severity.WARNING
        );
    }

    /**
     * Factory method: creates a symptom with evidence data.
     *
     * @param phase the wizard phase where symptom was detected
     * @param type the symptom type
     * @param description human-readable description
     * @param evidence diagnostic evidence map
     * @param severity urgency level
     * @return new symptom
     */
    public static WizardSymptom of(
            WizardPhase phase,
            SymptomType type,
            String description,
            Map<String, Object> evidence,
            Severity severity
    ) {
        return new WizardSymptom(
            UUID.randomUUID().toString(),
            phase, type, description, evidence, Instant.now(), severity
        );
    }

    /**
     * Classification of symptom types encountered during wizard execution.
     * Maps directly to recoverable failure modes in the autonomic system.
     */
    public enum SymptomType {
        /** Discovery phase found no MCP tools or A2A agents available. */
        DISCOVERY_EMPTY,

        /** Wizard session stuck in same phase for too long (stalled progress). */
        PHASE_STALLED,

        /** MCP and A2A configurations are incompatible or conflicting. */
        CONFIG_CONFLICT,

        /** Petri net validation of pattern failed soundness check. */
        PATTERN_UNSOUND,

        /** Overall configuration validation failed against constraints. */
        VALIDATION_FAILED,

        /** Deployment phase could not connect to or apply configuration to engine. */
        DEPLOYMENT_FAILED
    }

    /**
     * Severity classification for symptoms.
     * Used to prioritize recovery actions.
     */
    public enum Severity {
        /** Informational: detected but not actionable yet. */
        INFO,

        /** Warning: detectable problem, should be addressed. */
        WARNING,

        /** Critical: blocking problem requiring immediate recovery. */
        CRITICAL
    }
}
