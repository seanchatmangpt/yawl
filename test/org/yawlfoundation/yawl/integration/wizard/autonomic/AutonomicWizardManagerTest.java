package org.yawlfoundation.yawl.integration.wizard.autonomic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.integration.wizard.core.WizardPhase;
import org.yawlfoundation.yawl.integration.wizard.core.WizardSession;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the AutonomicWizardManager class (MAPE-K orchestrator).
 *
 * <p>Verifies that the manager:
 * <ul>
 *   <li>Runs the complete MAPE-K loop (Monitor → Analyze → Plan → Execute)</li>
 *   <li>Returns unchanged session if no symptoms detected</li>
 *   <li>Detects and recovers from problems automatically</li>
 *   <li>Checks session health without recovery</li>
 *   <li>Exposes all components for advanced use</li>
 * </ul>
 */
@DisplayName("AutonomicWizardManager Tests (MAPE-K Loop)")
class AutonomicWizardManagerTest {

    private final AutonomicWizardManager manager = AutonomicWizardManager.withDefaults();

    @Test
    @DisplayName("withDefaults() creates fully configured manager")
    void testWithDefaults() {
        AutonomicWizardManager m = AutonomicWizardManager.withDefaults();

        assertNotNull(m.monitor());
        assertNotNull(m.analyzer());
        assertNotNull(m.planner());
        assertNotNull(m.executor());
        assertNotNull(m.knowledgeBase());
    }

    @Test
    @DisplayName("runLoop() returns unchanged session when healthy")
    void testRunLoopHealthySession() {
        WizardSession session = WizardSession.newSession()
            .withPhase(WizardPhase.PATTERN_SELECTION, "test", "Selecting pattern")
            .withContext("discoveredA2AAgents", List.of("agent1"))
            .withContext("discoveredMcpTools", List.of("tool1"))
            .withContext("validationPassed", true);

        WizardSession recovered = manager.runLoop(session);

        assertEquals(session.sessionId(), recovered.sessionId());
        assertEquals(session.currentPhase(), recovered.currentPhase());
    }

    @Test
    @DisplayName("runLoop() detects and recovers from DISCOVERY_EMPTY symptom")
    void testRunLoopRecoveryFromDiscoveryEmpty() {
        WizardSession session = WizardSession.newSession()
            .withPhase(WizardPhase.DISCOVERY, "test", "Discovery")
            .withContext("discoveredA2AAgents", List.of())
            .withContext("discoveredMcpTools", List.of());

        WizardSession recovered = manager.runLoop(session);

        // Should have applied fallback pattern recovery
        assertTrue(recovered.has("selectedPattern"));
        assertTrue(recovered.has("patternFallback"));
    }

    @Test
    @DisplayName("runLoop() detects and recovers from PHASE_STALLED symptom")
    void testRunLoopRecoveryFromPhaseStalled() {
        WizardSession session = WizardSession.newSession()
            .withPhase(WizardPhase.PATTERN_SELECTION, "step1", "Event 1")
            .recordEvent(org.yawlfoundation.yawl.integration.wizard.core.WizardEvent.of(
                WizardPhase.PATTERN_SELECTION, "step2", "Event 2"))
            .recordEvent(org.yawlfoundation.yawl.integration.wizard.core.WizardEvent.of(
                WizardPhase.PATTERN_SELECTION, "step3", "Event 3"))
            .recordEvent(org.yawlfoundation.yawl.integration.wizard.core.WizardEvent.of(
                WizardPhase.PATTERN_SELECTION, "step4", "Event 4"));

        WizardSession recovered = manager.runLoop(session);

        // Should have recovered by retrying discovery
        assertEquals(WizardPhase.DISCOVERY, recovered.currentPhase());
    }

    @Test
    @DisplayName("runLoop() detects and recovers from PATTERN_UNSOUND symptom")
    void testRunLoopRecoveryFromPatternUnsound() {
        WizardSession session = WizardSession.newSession()
            .withPhase(WizardPhase.VALIDATION, "test", "Validation")
            .withContext("petriNetSoundness", false);

        WizardSession recovered = manager.runLoop(session);

        // Should have applied fallback pattern recovery
        assertTrue(recovered.has("selectedPattern"));
        assertEquals("WP-1", recovered.get("selectedPattern", String.class).orElse(""));
    }

    @Test
    @DisplayName("runLoop() detects and recovers from VALIDATION_FAILED symptom")
    void testRunLoopRecoveryFromValidationFailed() {
        WizardSession session = WizardSession.newSession()
            .withPhase(WizardPhase.VALIDATION, "test", "Validation")
            .withContext("validationPassed", false)
            .withContext("validationError", "Missing config");

        WizardSession recovered = manager.runLoop(session);

        // Should have applied defaults recovery
        assertTrue(recovered.has("defaultsApplied"));
    }

    @Test
    @DisplayName("runLoop() detects and aborts on DEPLOYMENT_FAILED symptom")
    void testRunLoopAbortOnDeploymentFailed() {
        WizardSession session = WizardSession.newSession()
            .withPhase(WizardPhase.DEPLOYMENT, "test", "Deployment")
            .withContext("deploymentError", "Engine unreachable");

        WizardSession recovered = manager.runLoop(session);

        // Should have aborted to FAILED phase
        assertEquals(WizardPhase.FAILED, recovered.currentPhase());
    }

    @Test
    @DisplayName("isHealthy() returns true when no CRITICAL symptoms detected")
    void testIsHealthyReturnsTrue() {
        WizardSession session = WizardSession.newSession()
            .withPhase(WizardPhase.PATTERN_SELECTION, "test", "Good state")
            .withContext("discoveredA2AAgents", List.of("agent1"))
            .withContext("validationPassed", true);

        boolean healthy = manager.isHealthy(session);

        assertTrue(healthy);
    }

    @Test
    @DisplayName("isHealthy() returns false when CRITICAL symptom detected")
    void testIsHealthyReturnsFalse() {
        WizardSession session = WizardSession.newSession()
            .withPhase(WizardPhase.VALIDATION, "test", "Validation")
            .withContext("validationPassed", false);

        boolean healthy = manager.isHealthy(session);

        assertFalse(healthy);
    }

    @Test
    @DisplayName("isHealthy() does not mutate session")
    void testIsHealthyDoesNotMutateSession() {
        WizardSession session = WizardSession.newSession()
            .withPhase(WizardPhase.DISCOVERY, "test", "Discovery");

        int eventCountBefore = session.eventCount();

        manager.isHealthy(session);

        // Session should be unchanged
        assertEquals(eventCountBefore, session.eventCount());
    }

    @Test
    @DisplayName("runLoop() handles multiple simultaneous symptoms")
    void testRunLoopMultipleSymptoms() {
        WizardSession session = WizardSession.newSession()
            .withPhase(WizardPhase.VALIDATION, "test", "Validation")
            .withContext("discoveredA2AAgents", List.of())
            .withContext("discoveredMcpTools", List.of())
            .withContext("validationPassed", false)
            .withContext("petriNetSoundness", false);

        WizardSession recovered = manager.runLoop(session);

        // Should handle all symptoms through recovery plan
        assertNotNull(recovered);
        // Session should have been modified by recovery
        assertTrue(recovered.eventCount() > session.eventCount());
    }

    @Test
    @DisplayName("runLoop() is idempotent on healthy sessions")
    void testRunLoopIdempotent() {
        WizardSession session = WizardSession.newSession()
            .withPhase(WizardPhase.PATTERN_SELECTION, "test", "OK")
            .withContext("discoveredA2AAgents", List.of("agent1"));

        WizardSession recovered1 = manager.runLoop(session);
        WizardSession recovered2 = manager.runLoop(recovered1);

        // Both runs should produce same result
        assertEquals(recovered1.currentPhase(), recovered2.currentPhase());
        assertEquals(recovered1.context(), recovered2.context());
    }

    @Test
    @DisplayName("Manager exposes all components for advanced use")
    void testManagerExposesComponents() {
        assertNotNull(manager.monitor());
        assertNotNull(manager.analyzer());
        assertNotNull(manager.planner());
        assertNotNull(manager.executor());
        assertNotNull(manager.knowledgeBase());
    }

    @Test
    @DisplayName("Custom manager with specific knowledge base can be created")
    void testCustomManagerWithSpecificKB() {
        KnowledgeBase customKB = KnowledgeBase.defaultBase();

        AutonomicWizardManager customManager = new AutonomicWizardManager(
            new AutonomicMonitor(),
            new AutonomicAnalyzer(customKB),
            new AutonomicPlanner(),
            new AutonomicExecutor(),
            customKB
        );

        assertNotNull(customManager);
        assertEquals(customKB, customManager.knowledgeBase());
    }

    @Test
    @DisplayName("Manager requires non-null components")
    void testManagerRequiresNonNullComponents() {
        KnowledgeBase kb = KnowledgeBase.defaultBase();

        assertThrows(NullPointerException.class, () ->
            new AutonomicWizardManager(null, new AutonomicAnalyzer(kb), new AutonomicPlanner(), new AutonomicExecutor(), kb)
        );
    }

    @Test
    @DisplayName("Complete MAPE-K loop end-to-end test")
    void testCompleteMAPEKLoop() {
        // Scenario: wizard has unsound pattern and discovery is empty
        WizardSession session = WizardSession.newSession()
            .withPhase(WizardPhase.VALIDATION, "test", "Validation started")
            .withContext("discoveredA2AAgents", List.of())
            .withContext("discoveredMcpTools", List.of())
            .withContext("selectedPattern", "WP-5")  // complex pattern
            .withContext("petriNetSoundness", false);

        // Run MAPE-K loop
        WizardSession recovered = manager.runLoop(session);

        // Verify recovery:
        // 1. Should detect multiple symptoms
        // 2. Should analyze and diagnose
        // 3. Should plan recovery (fallback pattern, etc)
        // 4. Should execute plan

        assertNotNull(recovered);
        assertTrue(recovered.eventCount() > session.eventCount(), "Recovery should add events");

        // After recovery, pattern should be fallback
        String pattern = recovered.get("selectedPattern", String.class).orElse("");
        assertTrue(pattern.equals("WP-1") || pattern.contains("fallback"));
    }
}
