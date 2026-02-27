package org.yawlfoundation.yawl.integration.wizard.autonomic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.integration.wizard.core.WizardEvent;
import org.yawlfoundation.yawl.integration.wizard.core.WizardPhase;
import org.yawlfoundation.yawl.integration.wizard.core.WizardSession;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the AutonomicMonitor class.
 *
 * <p>Verifies that the monitor correctly detects all 6 symptom types:
 * <ul>
 *   <li>DISCOVERY_EMPTY: no agents or tools discovered</li>
 *   <li>PHASE_STALLED: same phase repeated >3 times</li>
 *   <li>CONFIG_CONFLICT: both configs present but incompatible</li>
 *   <li>PATTERN_UNSOUND: petriNetSoundness is false</li>
 *   <li>VALIDATION_FAILED: validationPassed is false</li>
 *   <li>DEPLOYMENT_FAILED: deployment phase with error</li>
 * </ul>
 */
@DisplayName("AutonomicMonitor Tests")
class AutonomicMonitorTest {

    private final AutonomicMonitor monitor = new AutonomicMonitor();

    @Test
    @DisplayName("examine() returns empty list for healthy session")
    void testHealthySessionNoSymptoms() {
        WizardSession session = WizardSession.newSession();

        List<WizardSymptom> symptoms = monitor.examine(session);

        assertTrue(symptoms.isEmpty());
    }

    @Test
    @DisplayName("detects DISCOVERY_EMPTY symptom when no agents/tools found")
    void testDetectDiscoveryEmpty() {
        WizardSession session = WizardSession.newSession()
            .withPhase(WizardPhase.DISCOVERY, "test", "Start discovery")
            .withContext("discoveredA2AAgents", List.of())
            .withContext("discoveredMcpTools", List.of());

        Optional<WizardSymptom> symptom = monitor.examineFor(session, WizardSymptom.SymptomType.DISCOVERY_EMPTY);

        assertTrue(symptom.isPresent());
        assertEquals(WizardSymptom.SymptomType.DISCOVERY_EMPTY, symptom.get().type());
        assertEquals(WizardSymptom.Severity.WARNING, symptom.get().severity());
    }

    @Test
    @DisplayName("does not detect DISCOVERY_EMPTY when agents/tools exist")
    void testNoDiscoveryEmptyWhenToolsFound() {
        WizardSession session = WizardSession.newSession()
            .withPhase(WizardPhase.DISCOVERY, "test", "Start discovery")
            .withContext("discoveredA2AAgents", List.of("agent1"))
            .withContext("discoveredMcpTools", List.of("tool1"));

        Optional<WizardSymptom> symptom = monitor.examineFor(session, WizardSymptom.SymptomType.DISCOVERY_EMPTY);

        assertTrue(symptom.isEmpty());
    }

    @Test
    @DisplayName("detects PHASE_STALLED when >3 events in same phase")
    void testDetectPhaseStalled() {
        WizardSession session = WizardSession.newSession()
            .withPhase(WizardPhase.PATTERN_SELECTION, "step1", "Event 1")
            .recordEvent(WizardEvent.of(WizardPhase.PATTERN_SELECTION, "step2", "Event 2"))
            .recordEvent(WizardEvent.of(WizardPhase.PATTERN_SELECTION, "step3", "Event 3"))
            .recordEvent(WizardEvent.of(WizardPhase.PATTERN_SELECTION, "step4", "Event 4"));

        Optional<WizardSymptom> symptom = monitor.examineFor(session, WizardSymptom.SymptomType.PHASE_STALLED);

        assertTrue(symptom.isPresent());
        assertEquals(WizardSymptom.SymptomType.PHASE_STALLED, symptom.get().type());
        assertEquals(WizardSymptom.Severity.CRITICAL, symptom.get().severity());
    }

    @Test
    @DisplayName("does not detect PHASE_STALLED when phase advances")
    void testNoPhaseStallWhenPhaseAdvances() {
        WizardSession session = WizardSession.newSession()
            .withPhase(WizardPhase.DISCOVERY, "step1", "Event 1")
            .withPhase(WizardPhase.PATTERN_SELECTION, "step2", "Event 2");

        Optional<WizardSymptom> symptom = monitor.examineFor(session, WizardSymptom.SymptomType.PHASE_STALLED);

        assertTrue(symptom.isEmpty());
    }

    @Test
    @DisplayName("detects CONFIG_CONFLICT when tool/agent count mismatch >2x")
    void testDetectConfigConflict() {
        WizardSession session = WizardSession.newSession()
            .withPhase(WizardPhase.A2A_CONFIG, "test", "Config phase")
            .withContext("mcpConfiguration", Map.of("tools", "configured"))
            .withContext("a2aConfiguration", Map.of("agents", "configured"))
            .withContext("discoveredMcpTools", List.of("t1", "t2", "t3", "t4", "t5"))
            .withContext("discoveredA2AAgents", List.of("a1"));

        Optional<WizardSymptom> symptom = monitor.examineFor(session, WizardSymptom.SymptomType.CONFIG_CONFLICT);

        assertTrue(symptom.isPresent());
        assertEquals(WizardSymptom.SymptomType.CONFIG_CONFLICT, symptom.get().type());
    }

    @Test
    @DisplayName("does not detect CONFIG_CONFLICT when counts are balanced")
    void testNoConfigConflictWhenBalanced() {
        WizardSession session = WizardSession.newSession()
            .withPhase(WizardPhase.A2A_CONFIG, "test", "Config phase")
            .withContext("mcpConfiguration", Map.of("tools", "configured"))
            .withContext("a2aConfiguration", Map.of("agents", "configured"))
            .withContext("discoveredMcpTools", List.of("t1", "t2"))
            .withContext("discoveredA2AAgents", List.of("a1", "a2"));

        Optional<WizardSymptom> symptom = monitor.examineFor(session, WizardSymptom.SymptomType.CONFIG_CONFLICT);

        assertTrue(symptom.isEmpty());
    }

    @Test
    @DisplayName("detects PATTERN_UNSOUND when petriNetSoundness is false")
    void testDetectPatternUnsound() {
        WizardSession session = WizardSession.newSession()
            .withPhase(WizardPhase.VALIDATION, "test", "Validation")
            .withContext("petriNetSoundness", false);

        Optional<WizardSymptom> symptom = monitor.examineFor(session, WizardSymptom.SymptomType.PATTERN_UNSOUND);

        assertTrue(symptom.isPresent());
        assertEquals(WizardSymptom.SymptomType.PATTERN_UNSOUND, symptom.get().type());
        assertEquals(WizardSymptom.Severity.CRITICAL, symptom.get().severity());
    }

    @Test
    @DisplayName("does not detect PATTERN_UNSOUND when petriNetSoundness is true")
    void testNoPatternUnsoundWhenTrue() {
        WizardSession session = WizardSession.newSession()
            .withPhase(WizardPhase.VALIDATION, "test", "Validation")
            .withContext("petriNetSoundness", true);

        Optional<WizardSymptom> symptom = monitor.examineFor(session, WizardSymptom.SymptomType.PATTERN_UNSOUND);

        assertTrue(symptom.isEmpty());
    }

    @Test
    @DisplayName("detects VALIDATION_FAILED when validationPassed is false")
    void testDetectValidationFailed() {
        WizardSession session = WizardSession.newSession()
            .withPhase(WizardPhase.VALIDATION, "test", "Validation")
            .withContext("validationPassed", false)
            .withContext("validationError", "Missing tool bindings");

        Optional<WizardSymptom> symptom = monitor.examineFor(session, WizardSymptom.SymptomType.VALIDATION_FAILED);

        assertTrue(symptom.isPresent());
        assertEquals(WizardSymptom.SymptomType.VALIDATION_FAILED, symptom.get().type());
        assertEquals(WizardSymptom.Severity.CRITICAL, symptom.get().severity());
    }

    @Test
    @DisplayName("detects DEPLOYMENT_FAILED when in DEPLOYMENT phase with error")
    void testDetectDeploymentFailed() {
        WizardSession session = WizardSession.newSession()
            .withPhase(WizardPhase.DEPLOYMENT, "test", "Deploying")
            .withContext("deploymentError", "Engine connection refused");

        Optional<WizardSymptom> symptom = monitor.examineFor(session, WizardSymptom.SymptomType.DEPLOYMENT_FAILED);

        assertTrue(symptom.isPresent());
        assertEquals(WizardSymptom.SymptomType.DEPLOYMENT_FAILED, symptom.get().type());
        assertEquals(WizardSymptom.Severity.CRITICAL, symptom.get().severity());
    }

    @Test
    @DisplayName("examine() detects multiple symptoms simultaneously")
    void testExamineMultipleSymptoms() {
        WizardSession session = WizardSession.newSession()
            .withPhase(WizardPhase.VALIDATION, "test", "Validation")
            .withContext("discoveredA2AAgents", List.of())
            .withContext("discoveredMcpTools", List.of())
            .withContext("validationPassed", false)
            .withContext("petriNetSoundness", false);

        List<WizardSymptom> symptoms = monitor.examine(session);

        // Should detect multiple problems
        assertTrue(symptoms.size() >= 2, "Should detect at least 2 symptoms");
        assertTrue(symptoms.stream().anyMatch(s -> s.type() == WizardSymptom.SymptomType.DISCOVERY_EMPTY));
        assertTrue(symptoms.stream().anyMatch(s -> s.type() == WizardSymptom.SymptomType.VALIDATION_FAILED));
    }

    @Test
    @DisplayName("symptom includes evidence map with diagnostic data")
    void testSymptomIncludesEvidence() {
        WizardSession session = WizardSession.newSession()
            .withPhase(WizardPhase.DISCOVERY, "test", "Discovery")
            .withContext("discoveredA2AAgents", List.of())
            .withContext("discoveredMcpTools", List.of("tool1"));

        Optional<WizardSymptom> symptom = monitor.examineFor(session, WizardSymptom.SymptomType.DISCOVERY_EMPTY);

        assertTrue(symptom.isPresent());
        assertTrue(symptom.get().evidence().containsKey("agentCount"));
        assertTrue(symptom.get().evidence().containsKey("toolCount"));
    }
}
