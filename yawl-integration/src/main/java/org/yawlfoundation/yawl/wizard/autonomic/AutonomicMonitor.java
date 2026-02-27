package org.yawlfoundation.yawl.integration.wizard.autonomic;

import org.yawlfoundation.yawl.integration.wizard.core.WizardPhase;
import org.yawlfoundation.yawl.integration.wizard.core.WizardSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Autonomic monitor for the wizard system.
 *
 * <p>The monitor is the 'M' in MAPE-K. It observes the wizard session state
 * and collects observable symptoms—raw evidence of problems without yet
 * assigning root cause.
 *
 * <p>Monitoring is a real-time health check that runs continuously on the
 * wizard session. It detects:
 * <ul>
 *   <li>DISCOVERY_EMPTY: discoveredAgents or discoveredTools is empty</li>
 *   <li>PHASE_STALLED: same phase persists across >3 events</li>
 *   <li>CONFIG_CONFLICT: both MCP and A2A configs present but incompatible</li>
 *   <li>PATTERN_UNSOUND: petriNetSoundness context is false</li>
 *   <li>VALIDATION_FAILED: validationPassed context is false</li>
 *   <li>DEPLOYMENT_FAILED: phase is DEPLOYMENT and deploymentError is set</li>
 * </ul>
 *
 * <p>Implementations are stateless and thread-safe. Call {@link #examine(WizardSession)}
 * to collect all symptoms in one scan.
 *
 * @see WizardSymptom for symptom structure
 * @see AutonomicAnalyzer for downstream analysis
 */
public class AutonomicMonitor {

    /**
     * Creates a new autonomic monitor with default configuration.
     */
    public AutonomicMonitor() {
        // No state needed; monitor is stateless
    }

    /**
     * Examine a wizard session and return all detected symptoms.
     *
     * <p>Scans the session state for all 6 symptom types in a single pass.
     * Returns a list of symptoms (possibly empty if session is healthy).
     *
     * <p>This is a pure observation function with no side effects.
     *
     * @param session the wizard session to examine (immutable)
     * @return list of detected symptoms (empty if none detected)
     * @throws NullPointerException if session is null
     */
    public List<WizardSymptom> examine(WizardSession session) {
        Objects.requireNonNull(session, "session must not be null");

        List<WizardSymptom> symptoms = new ArrayList<>();

        // Check each symptom type
        examineFor(session, WizardSymptom.SymptomType.DISCOVERY_EMPTY).ifPresent(symptoms::add);
        examineFor(session, WizardSymptom.SymptomType.PHASE_STALLED).ifPresent(symptoms::add);
        examineFor(session, WizardSymptom.SymptomType.CONFIG_CONFLICT).ifPresent(symptoms::add);
        examineFor(session, WizardSymptom.SymptomType.PATTERN_UNSOUND).ifPresent(symptoms::add);
        examineFor(session, WizardSymptom.SymptomType.VALIDATION_FAILED).ifPresent(symptoms::add);
        examineFor(session, WizardSymptom.SymptomType.DEPLOYMENT_FAILED).ifPresent(symptoms::add);

        return symptoms;
    }

    /**
     * Monitor a single symptom type in isolation.
     *
     * <p>Examines the session for one specific symptom type and returns
     * a symptom if detected, or empty if the session is healthy for that type.
     *
     * @param session the wizard session to examine
     * @param type the symptom type to check for
     * @return optional containing detected symptom, or empty if healthy
     * @throws NullPointerException if session or type is null
     */
    public Optional<WizardSymptom> examineFor(WizardSession session, WizardSymptom.SymptomType type) {
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(type, "type must not be null");

        return switch (type) {
            case DISCOVERY_EMPTY -> checkDiscoveryEmpty(session);
            case PHASE_STALLED -> checkPhaseStalled(session);
            case CONFIG_CONFLICT -> checkConfigConflict(session);
            case PATTERN_UNSOUND -> checkPatternUnsound(session);
            case VALIDATION_FAILED -> checkValidationFailed(session);
            case DEPLOYMENT_FAILED -> checkDeploymentFailed(session);
        };
    }

    /**
     * Check for DISCOVERY_EMPTY symptom.
     *
     * <p>Detects when discovery phase found no MCP tools or A2A agents.
     * Evidence includes the actual counts discovered.
     */
    private Optional<WizardSymptom> checkDiscoveryEmpty(WizardSession session) {
        // Check if discovery has been run and found nothing
        boolean hasDiscoveredAgents = session.has("discoveredA2AAgents")
            && !((List<?>) session.context().get("discoveredA2AAgents")).isEmpty();
        boolean hasDiscoveredTools = session.has("discoveredMcpTools")
            && !((List<?>) session.context().get("discoveredMcpTools")).isEmpty();

        // Only a symptom if discovery was attempted and found nothing
        if (session.currentPhase().ordinal() >= WizardPhase.DISCOVERY.ordinal()
            && !hasDiscoveredAgents && !hasDiscoveredTools) {

            int agentCount = session.has("discoveredA2AAgents")
                ? ((List<?>) session.context().get("discoveredA2AAgents")).size() : 0;
            int toolCount = session.has("discoveredMcpTools")
                ? ((List<?>) session.context().get("discoveredMcpTools")).size() : 0;

            Map<String, Object> evidence = Map.of(
                "agentCount", agentCount,
                "toolCount", toolCount,
                "phase", session.currentPhase().toString()
            );

            return Optional.of(WizardSymptom.of(
                session.currentPhase(),
                WizardSymptom.SymptomType.DISCOVERY_EMPTY,
                "Discovery found no MCP tools (" + toolCount + ") and no A2A agents (" + agentCount + ")",
                evidence,
                WizardSymptom.Severity.WARNING
            ));
        }

        return Optional.empty();
    }

    /**
     * Check for PHASE_STALLED symptom.
     *
     * <p>Detects when wizard is stuck in the same phase for too long
     * (measured by >3 events in the same phase).
     */
    private Optional<WizardSymptom> checkPhaseStalled(WizardSession session) {
        WizardPhase currentPhase = session.currentPhase();

        // Count consecutive events in current phase
        int eventsInPhase = 0;
        for (int i = session.events().size() - 1; i >= 0; i--) {
            if (session.events().get(i).phase() == currentPhase) {
                eventsInPhase++;
            } else {
                break;
            }
        }

        // Stalled if >3 events in same phase (indicates lack of progress)
        if (eventsInPhase > 3) {
            Map<String, Object> evidence = Map.of(
                "phase", currentPhase.toString(),
                "eventsInPhase", eventsInPhase,
                "totalEvents", session.events().size()
            );

            return Optional.of(WizardSymptom.of(
                currentPhase,
                WizardSymptom.SymptomType.PHASE_STALLED,
                "Session stalled in phase " + currentPhase + " with " + eventsInPhase + " events",
                evidence,
                WizardSymptom.Severity.CRITICAL
            ));
        }

        return Optional.empty();
    }

    /**
     * Check for CONFIG_CONFLICT symptom.
     *
     * <p>Detects when MCP and A2A configurations are both present
     * but have incompatible requirements (e.g., different tool/skill counts).
     */
    private Optional<WizardSymptom> checkConfigConflict(WizardSession session) {
        boolean hasMcpConfig = session.has("mcpConfiguration");
        boolean hasA2AConfig = session.has("a2aConfiguration");

        if (hasMcpConfig && hasA2AConfig) {
            // Both configs present; check for conflicts
            Object mcpCfg = session.context().get("mcpConfiguration");
            Object a2aCfg = session.context().get("a2aConfiguration");

            // Simple conflict detection: if both are maps, check for mismatched sizes
            if (mcpCfg instanceof Map<?, ?> mcpMap && a2aCfg instanceof Map<?, ?> a2aMap) {
                // Check if tool count and agent count mismatch significantly
                int toolCount = session.get("discoveredMcpTools", List.class)
                    .map(List::size).orElse(0);
                int agentCount = session.get("discoveredA2AAgents", List.class)
                    .map(List::size).orElse(0);

                // If more tools than agents (or vice versa by >50%), that's a conflict
                if (toolCount > 0 && agentCount > 0) {
                    double ratio = Math.max(toolCount, agentCount) / (double) Math.min(toolCount, agentCount);
                    if (ratio > 2.0) {
                        Map<String, Object> evidence = Map.of(
                            "toolCount", toolCount,
                            "agentCount", agentCount,
                            "ratio", ratio
                        );

                        return Optional.of(WizardSymptom.of(
                            session.currentPhase(),
                            WizardSymptom.SymptomType.CONFIG_CONFLICT,
                            "Tool/agent count mismatch: " + toolCount + " tools vs " + agentCount + " agents",
                            evidence,
                            WizardSymptom.Severity.WARNING
                        ));
                    }
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Check for PATTERN_UNSOUND symptom.
     *
     * <p>Detects when the Petri net soundness validation of the selected
     * pattern returned false.
     */
    private Optional<WizardSymptom> checkPatternUnsound(WizardSession session) {
        if (session.has("petriNetSoundness")) {
            Object value = session.context().get("petriNetSoundness");
            if (value instanceof Boolean && !(Boolean) value) {
                Map<String, Object> evidence = Map.of(
                    "petriNetSoundness", false,
                    "phase", session.currentPhase().toString()
                );

                return Optional.of(WizardSymptom.of(
                    session.currentPhase(),
                    WizardSymptom.SymptomType.PATTERN_UNSOUND,
                    "Selected pattern failed Petri net soundness validation",
                    evidence,
                    WizardSymptom.Severity.CRITICAL
                ));
            }
        }

        return Optional.empty();
    }

    /**
     * Check for VALIDATION_FAILED symptom.
     *
     * <p>Detects when overall configuration validation failed against constraints.
     */
    private Optional<WizardSymptom> checkValidationFailed(WizardSession session) {
        if (session.has("validationPassed")) {
            Object value = session.context().get("validationPassed");
            if (value instanceof Boolean && !(Boolean) value) {
                String errorMsg = session.get("validationError", String.class)
                    .orElse("Unknown validation error");

                Map<String, Object> evidence = Map.of(
                    "validationPassed", false,
                    "error", errorMsg,
                    "phase", session.currentPhase().toString()
                );

                return Optional.of(WizardSymptom.of(
                    session.currentPhase(),
                    WizardSymptom.SymptomType.VALIDATION_FAILED,
                    "Configuration validation failed: " + errorMsg,
                    evidence,
                    WizardSymptom.Severity.CRITICAL
                ));
            }
        }

        return Optional.empty();
    }

    /**
     * Check for DEPLOYMENT_FAILED symptom.
     *
     * <p>Detects when the deployment phase encountered a connection or
     * deployment error that prevented applying the configuration.
     */
    private Optional<WizardSymptom> checkDeploymentFailed(WizardSession session) {
        if (session.currentPhase() == WizardPhase.DEPLOYMENT && session.has("deploymentError")) {
            String error = session.get("deploymentError", String.class)
                .orElse("Unknown deployment error");

            Map<String, Object> evidence = Map.of(
                "deploymentError", error,
                "phase", WizardPhase.DEPLOYMENT.toString()
            );

            return Optional.of(WizardSymptom.of(
                session.currentPhase(),
                WizardSymptom.SymptomType.DEPLOYMENT_FAILED,
                "Deployment failed: " + error,
                evidence,
                WizardSymptom.Severity.CRITICAL
            ));
        }

        return Optional.empty();
    }
}
