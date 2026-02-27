package org.yawlfoundation.yawl.integration.wizard.core;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link AutonomicWizardEngine}.
 *
 * <p>Tests step execution, phase validation, session advancement,
 * and result building. Uses concrete test implementations of {@link WizardStep}.
 */
@DisplayName("AutonomicWizardEngine")
class AutonomicWizardEngineTest {

    private AutonomicWizardEngine engine;

    @BeforeEach
    void setUp() {
        engine = new AutonomicWizardEngine();
    }

    @Test
    @DisplayName("initSession creates new session in INIT phase")
    void testInitSession() {
        WizardSession session = engine.initSession();

        assertNotNull(session.sessionId());
        assertEquals(WizardPhase.INIT, session.currentPhase());
        assertTrue(session.context().isEmpty());
        assertTrue(session.events().isEmpty());
    }

    @Test
    @DisplayName("initSession with context pre-populates context")
    void testInitSessionWithContext() {
        Map<String, Object> initial = Map.of("key", "value");
        WizardSession session = engine.initSession(initial);

        assertEquals("value", session.context().get("key"));
    }

    @Test
    @DisplayName("executeStep validates phase before execution")
    void testExecuteStepPhaseValidation() {
        WizardSession session = engine.initSession();
        WizardStep<String> step = new TestStep("step1", WizardPhase.DISCOVERY, "result");

        // Step requires DISCOVERY but session is in INIT
        assertThrows(IllegalStateException.class, () ->
            engine.executeStep(session, step)
        );
    }

    @Test
    @DisplayName("executeStep runs prerequisite validation")
    void testExecuteStepPrerequisiteValidation() {
        WizardSession session = engine.initSession();
        WizardStep<String> step = new TestStepWithPrerequisites(
            "step1",
            WizardPhase.INIT,
            List.of("Missing key1", "Missing key2"),
            "result"
        );

        WizardStepResult<String> result = engine.executeStep(session, step);

        assertFalse(result.isSuccess());
        assertEquals(2, result.errors().size());
        assertEquals("Missing key1", result.errors().get(0));
    }

    @Test
    @DisplayName("executeStep executes step when prerequisites pass")
    void testExecuteStepSuccess() {
        WizardSession session = engine.initSession();
        WizardStep<String> step = new TestStep("step1", WizardPhase.INIT, "success_value");

        WizardStepResult<String> result = engine.executeStep(session, step);

        assertTrue(result.isSuccess());
        assertEquals("success_value", result.value());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    @DisplayName("executeStep captures step result value and stepId")
    void testExecuteStepResult() {
        WizardSession session = engine.initSession();
        WizardStep<Map> step = new TestStep("complex_step", WizardPhase.INIT,
            Map.of("key1", "value1", "key2", 42));

        WizardStepResult<Map> result = engine.executeStep(session, step);

        assertTrue(result.isSuccess());
        assertEquals("complex_step", result.stepId());
        assertEquals("value1", result.value().get("key1"));
        assertEquals(42, result.value().get("key2"));
    }

    @Test
    @DisplayName("executeStep with null step throws")
    void testExecuteStepNullStep() {
        WizardSession session = engine.initSession();
        assertThrows(NullPointerException.class, () ->
            engine.executeStep(session, null)
        );
    }

    @Test
    @DisplayName("advance transitions phase and stores result in context")
    void testAdvance() {
        WizardSession session = engine.initSession();
        WizardStepResult<String> result = WizardStepResult.success("step1", "result_value");

        WizardSession advanced = engine.advance(session, result, WizardPhase.DISCOVERY);

        assertEquals(WizardPhase.DISCOVERY, advanced.currentPhase());
        assertEquals("result_value", advanced.get("step_step1_result", String.class).get());
    }

    @Test
    @DisplayName("advance records transition event in audit trail")
    void testAdvanceRecordsEvent() {
        WizardSession session = engine.initSession();
        WizardStepResult<String> result = WizardStepResult.success("step1", "value");

        WizardSession advanced = engine.advance(session, result, WizardPhase.DISCOVERY);

        assertEquals(1, advanced.eventCount());
        WizardEvent event = advanced.lastEvent().get();
        assertEquals("step1", event.stepId());
        assertEquals(WizardPhase.DISCOVERY, event.phase());
    }

    @Test
    @DisplayName("advance preserves session ID and creation time")
    void testAdvancePreservesIdentity() {
        WizardSession session = engine.initSession();
        String originalId = session.sessionId();

        WizardSession advanced = engine.advance(
            session,
            WizardStepResult.success("step", "value"),
            WizardPhase.DISCOVERY
        );

        assertEquals(originalId, advanced.sessionId());
    }

    @Test
    @DisplayName("advance with null result throws")
    void testAdvanceNullResult() {
        WizardSession session = engine.initSession();
        assertThrows(NullPointerException.class, () ->
            engine.advance(session, null, WizardPhase.DISCOVERY)
        );
    }

    @Test
    @DisplayName("complete builds successful result from session")
    void testCompleteSuccess() {
        WizardSession session = engine.initSession()
            .withContext("mcp_config", Map.of("tool", "config"))
            .withContext("a2a_config", Map.of("agent", "skill"))
            .withContext("workflow_config", Map.of("pattern", "Sequence"));

        WizardResult result = engine.complete(session);

        assertTrue(result.isSuccess());
        assertEquals(WizardPhase.COMPLETE, result.terminalPhase());
        assertEquals(session.sessionId(), result.sessionId());
        assertEquals(Map.of("tool", "config"), result.mcpConfiguration());
        assertEquals(Map.of("agent", "skill"), result.a2aConfiguration());
    }

    @Test
    @DisplayName("complete with missing config maps uses empty maps")
    void testCompleteWithoutConfigs() {
        WizardSession session = engine.initSession();

        WizardResult result = engine.complete(session);

        assertTrue(result.isSuccess());
        assertTrue(result.mcpConfiguration().isEmpty());
        assertTrue(result.a2aConfiguration().isEmpty());
        assertTrue(result.workflowConfiguration().isEmpty());
    }

    @Test
    @DisplayName("fail builds failure result with errors")
    void testFail() {
        WizardSession session = engine.initSession();
        List<String> errors = List.of("Error 1", "Error 2");

        WizardResult result = engine.fail(session, errors);

        assertFalse(result.isSuccess());
        assertEquals(WizardPhase.FAILED, result.terminalPhase());
        assertEquals(2, result.errorCount());
        assertEquals("Error 1", result.firstError().get());
        assertTrue(result.mcpConfiguration().isEmpty());
    }

    @Test
    @DisplayName("recordEvent appends event to session")
    void testRecordEvent() {
        WizardSession session = engine.initSession();
        WizardEvent event = WizardEvent.of(WizardPhase.INIT, "step1", "message");

        WizardSession updated = engine.recordEvent(session, event);

        assertEquals(1, updated.eventCount());
        assertEquals(event, updated.lastEvent().get());
    }

    @Test
    @DisplayName("recordInfo creates and records event")
    void testRecordInfo() {
        WizardSession session = engine.initSession();

        WizardSession updated = engine.recordInfo(session, "step1", "Test message");

        assertEquals(1, updated.eventCount());
        WizardEvent event = updated.lastEvent().get();
        assertEquals("step1", event.stepId());
        assertEquals("Test message", event.message());
    }

    @Test
    @DisplayName("validatePhase throws when phase mismatches")
    void testValidatePhaseFail() {
        WizardSession session = engine.initSession().withPhase(
            WizardPhase.DISCOVERY, "step", "msg"
        );

        assertThrows(IllegalStateException.class, () ->
            engine.validatePhase(session, WizardPhase.INIT, "Expected INIT")
        );
    }

    @Test
    @DisplayName("validatePhase succeeds when phase matches")
    void testValidatePhaseSuccess() {
        WizardSession session = engine.initSession();

        // Should not throw
        engine.validatePhase(session, WizardPhase.INIT, "Expected INIT");
    }

    @Test
    @DisplayName("isTerminal returns true for COMPLETE phase")
    void testIsTerminalComplete() {
        WizardSession session = engine.initSession()
            .withPhase(WizardPhase.COMPLETE, "step", "msg");

        assertTrue(engine.isTerminal(session));
    }

    @Test
    @DisplayName("isTerminal returns true for FAILED phase")
    void testIsTerminalFailed() {
        WizardSession session = engine.initSession()
            .withPhase(WizardPhase.FAILED, "step", "msg");

        assertTrue(engine.isTerminal(session));
    }

    @Test
    @DisplayName("isTerminal returns false for non-terminal phases")
    void testIsTerminalNonTerminal() {
        WizardSession session = engine.initSession();
        assertFalse(engine.isTerminal(session));

        session = session.withPhase(WizardPhase.DISCOVERY, "step", "msg");
        assertFalse(engine.isTerminal(session));
    }

    @Test
    @DisplayName("isComplete returns true only for COMPLETE phase")
    void testIsComplete() {
        WizardSession session = engine.initSession();
        assertFalse(engine.isComplete(session));

        session = session.withPhase(WizardPhase.COMPLETE, "step", "msg");
        assertTrue(engine.isComplete(session));

        session = session.withPhase(WizardPhase.FAILED, "step", "msg");
        assertFalse(engine.isComplete(session));
    }

    @Test
    @DisplayName("isFailed returns true only for FAILED phase")
    void testIsFailed() {
        WizardSession session = engine.initSession();
        assertFalse(engine.isFailed(session));

        session = session.withPhase(WizardPhase.FAILED, "step", "msg");
        assertTrue(engine.isFailed(session));

        session = session.withPhase(WizardPhase.COMPLETE, "step", "msg");
        assertFalse(engine.isFailed(session));
    }

    @Nested
    @DisplayName("Typical wizard workflow")
    class TypicalWorkflow {

        @Test
        @DisplayName("execute discovery -> pattern selection -> configuration -> completion")
        void testCompleteWorkflow() {
            // Start
            WizardSession session = engine.initSession();

            // Discovery step
            WizardStep<Map> discoveryStep = new TestStep(
                "discovery",
                WizardPhase.INIT,
                Map.of("tool1", "config1", "tool2", "config2")
            );
            WizardStepResult<Map> discResult = engine.executeStep(session, discoveryStep);
            assertTrue(discResult.isSuccess());

            // Advance to DISCOVERY
            session = engine.advance(session, discResult, WizardPhase.DISCOVERY);
            assertEquals(WizardPhase.DISCOVERY, session.currentPhase());

            // Pattern selection step
            WizardStep<String> patternStep = new TestStep(
                "pattern",
                WizardPhase.DISCOVERY,
                "Sequence"
            );
            WizardStepResult<String> patResult = engine.executeStep(session, patternStep);
            assertTrue(patResult.isSuccess());

            // Advance to PATTERN_SELECTION
            session = engine.advance(session, patResult, WizardPhase.PATTERN_SELECTION);

            // MCP config step
            WizardStep<Map> mcpStep = new TestStep(
                "mcp_config",
                WizardPhase.PATTERN_SELECTION,
                Map.of("tools", List.of("tool1", "tool2"))
            );
            WizardStepResult<Map> mcpResult = engine.executeStep(session, mcpStep);
            assertTrue(mcpResult.isSuccess());

            session = engine.advance(session, mcpResult, WizardPhase.MCP_CONFIG);

            // Store final config
            session = session.withContext("mcp_config", Map.of("tool1", "endpoint1"));

            // Complete
            WizardResult result = engine.complete(session);
            assertTrue(result.isSuccess());
            assertEquals(3, result.auditTrailSize()); // 3 phase transitions
        }

        @Test
        @DisplayName("handle prerequisite failure and recover")
        void testRecoverFromPrerequisiteFailure() {
            WizardSession session = engine.initSession();

            // Step with failed prerequisites
            WizardStep<String> step1 = new TestStepWithPrerequisites(
                "step1",
                WizardPhase.INIT,
                List.of("Missing requirement A", "Missing requirement B"),
                "value"
            );

            WizardStepResult<String> result = engine.executeStep(session, step1);
            assertFalse(result.isSuccess());
            assertEquals(2, result.errors().size());

            // Record failure event
            String errorSummary = String.join("; ", result.errors());
            session = engine.recordInfo(session, result.stepId(),
                "Prerequisites failed: " + errorSummary);

            WizardResult finalResult = engine.fail(session, result.errors());
            assertFalse(finalResult.isSuccess());
            assertEquals(1, finalResult.auditTrailSize());
        }
    }

    /**
     * Concrete test implementation of WizardStep.
     */
    private static class TestStep<T> implements WizardStep<T> {
        private final String id;
        private final WizardPhase requiredPhase;
        private final T resultValue;

        TestStep(String id, WizardPhase requiredPhase, T resultValue) {
            this.id = id;
            this.requiredPhase = requiredPhase;
            this.resultValue = resultValue;
        }

        @Override
        public String stepId() {
            return id;
        }

        @Override
        public String title() {
            return "Test Step: " + id;
        }

        @Override
        public WizardPhase requiredPhase() {
            return requiredPhase;
        }

        @Override
        public WizardStepResult<T> execute(WizardSession session) {
            return WizardStepResult.success(id, resultValue);
        }
    }

    /**
     * Test step with prerequisite validation that fails.
     */
    private static class TestStepWithPrerequisites<T> implements WizardStep<T> {
        private final String id;
        private final WizardPhase requiredPhase;
        private final List<String> prerequisiteErrors;
        private final T resultValue;

        TestStepWithPrerequisites(String id, WizardPhase requiredPhase,
                                  List<String> prerequisiteErrors, T resultValue) {
            this.id = id;
            this.requiredPhase = requiredPhase;
            this.prerequisiteErrors = prerequisiteErrors;
            this.resultValue = resultValue;
        }

        @Override
        public String stepId() {
            return id;
        }

        @Override
        public String title() {
            return "Test Step with Prerequisites: " + id;
        }

        @Override
        public WizardPhase requiredPhase() {
            return requiredPhase;
        }

        @Override
        public WizardStepResult<T> execute(WizardSession session) {
            return WizardStepResult.success(id, resultValue);
        }

        @Override
        public List<String> validatePrerequisites(WizardSession session) {
            return prerequisiteErrors;
        }
    }
}
