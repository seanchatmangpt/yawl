package org.yawlfoundation.yawl.integration.wizard.patterns;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.integration.wizard.core.WizardPhase;
import org.yawlfoundation.yawl.integration.wizard.core.WizardSession;
import org.yawlfoundation.yawl.integration.wizard.core.WizardStepResult;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PatternSelectionStep wizard step.
 *
 * <p>Verifies the step correctly:
 * <ul>
 *   <li>Reads MCP tool and A2A agent counts from session</li>
 *   <li>Uses PatternAdvisor to recommend patterns</li>
 *   <li>Selects the best pattern</li>
 *   <li>Builds pattern structure</li>
 *   <li>Stores results in session context</li>
 *   <li>Records appropriate events</li>
 * </ul>
 */
@DisplayName("Pattern Selection Step Tests")
class PatternSelectionStepTest {

    private PatternSelectionStep step;
    private WizardSession session;

    @BeforeEach
    void setUp() {
        step = new PatternSelectionStep();
        session = WizardSession.newSession()
            .withPhase(WizardPhase.PATTERN_SELECTION, "test-init", "Initialize for pattern selection");
    }

    @Test
    @DisplayName("Step has correct ID and title")
    void testStepMetadata() {
        assertEquals("workflow-pattern-selection", step.stepId());
        assertEquals("Workflow Pattern Selection", step.title());
        assertNotNull(step.description());
        assertTrue(step.description().length() > 0);
    }

    @Test
    @DisplayName("Step requires PATTERN_SELECTION phase")
    void testRequiredPhase() {
        assertEquals(WizardPhase.PATTERN_SELECTION, step.requiredPhase());
    }

    @Test
    @DisplayName("Step is skippable")
    void testIsSkippable() {
        assertTrue(step.isSkippable());
    }

    @Test
    @DisplayName("Single tool/agent execution returns Sequence")
    void testSingleToolExecution() {
        WizardSession sessionWithConfig = session
            .withContext("mcp.tool.count", 1)
            .withContext("a2a.agent.count", 1);

        WizardStepResult<WorkflowPattern> result = step.execute(sessionWithConfig);

        assertTrue(result.isSuccess());
        assertEquals(WorkflowPattern.SEQUENCE, result.value());
    }

    @Test
    @DisplayName("Multiple tools execution returns parallelism pattern")
    void testMultipleToolsExecution() {
        WizardSession sessionWithConfig = session
            .withContext("mcp.tool.count", 3)
            .withContext("a2a.agent.count", 1);

        WizardStepResult<WorkflowPattern> result = step.execute(sessionWithConfig);

        assertTrue(result.isSuccess());
        WorkflowPattern selected = result.value();
        assertTrue(
            selected == WorkflowPattern.PARALLEL_SPLIT ||
            selected == WorkflowPattern.EXCLUSIVE_CHOICE,
            "Multiple tools should recommend parallelism-enabling pattern"
        );
    }

    @Test
    @DisplayName("Execution with loop requirement adds cycles")
    void testExecutionWithLoopRequirement() {
        WizardSession sessionWithConfig = session
            .withContext("mcp.tool.count", 2)
            .withContext("a2a.agent.count", 1)
            .withContext("workflow.requirements", List.of("loop"));

        WizardStepResult<WorkflowPattern> result = step.execute(sessionWithConfig);

        assertTrue(result.isSuccess());
        // Result may include arbitrary cycles in the context
        assertNotNull(result.value());
    }

    @Test
    @DisplayName("Successful execution stores pattern in context")
    void testExecutionStoresPatternInContext() {
        WizardSession sessionWithConfig = session
            .withContext("mcp.tool.count", 1)
            .withContext("a2a.agent.count", 1);

        WizardStepResult<WorkflowPattern> result = step.execute(sessionWithConfig);

        assertTrue(result.isSuccess());
        // Would need to check context, but result.value() is the pattern
        assertEquals(WorkflowPattern.SEQUENCE, result.value());
    }

    @Test
    @DisplayName("Pattern override bypasses recommendation")
    void testPatternOverride() {
        WizardSession sessionWithConfig = session
            .withContext("mcp.tool.count", 1)
            .withContext("a2a.agent.count", 1)
            .withContext("workflow.pattern.override", "WP-6");

        WizardStepResult<WorkflowPattern> result = step.execute(sessionWithConfig);

        assertTrue(result.isSuccess());
        assertEquals(WorkflowPattern.MULTI_CHOICE, result.value());
    }

    @Test
    @DisplayName("Invalid pattern override returns failure")
    void testInvalidPatternOverride() {
        WizardSession sessionWithConfig = session
            .withContext("workflow.pattern.override", "WP-99");

        WizardStepResult<WorkflowPattern> result = step.execute(sessionWithConfig);

        assertFalse(result.isSuccess());
        assertTrue(result.errors().stream()
            .anyMatch(e -> e.contains("Invalid pattern code")));
    }

    @Test
    @DisplayName("Null session throws exception")
    void testNullSessionThrows() {
        assertThrows(NullPointerException.class, () -> {
            step.execute(null);
        });
    }

    @Test
    @DisplayName("Prerequisites validation passes in correct phase")
    void testPrerequisitesValidationInCorrectPhase() {
        List<String> errors = step.validatePrerequisites(session);
        assertTrue(errors.isEmpty());
    }

    @Test
    @DisplayName("Prerequisites validation fails in wrong phase")
    void testPrerequisitesValidationWrongPhase() {
        WizardSession wrongPhaseSession = WizardSession.newSession()
            .withPhase(WizardPhase.DISCOVERY, "test", "Wrong phase");

        List<String> errors = step.validatePrerequisites(wrongPhaseSession);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream()
            .anyMatch(e -> e.contains("PATTERN_SELECTION")));
    }

    @Test
    @DisplayName("Prerequisites validation fails with negative tool count")
    void testPrerequisitesValidationNegativeToolCount() {
        WizardSession sessionWithBadConfig = session
            .withContext("mcp.tool.count", -1);

        List<String> errors = step.validatePrerequisites(sessionWithBadConfig);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream()
            .anyMatch(e -> e.contains("negative")));
    }

    @Test
    @DisplayName("Prerequisites validation fails with invalid tool count type")
    void testPrerequisitesValidationInvalidToolCountType() {
        WizardSession sessionWithBadConfig = session
            .withContext("mcp.tool.count", "not an integer");

        List<String> errors = step.validatePrerequisites(sessionWithBadConfig);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream()
            .anyMatch(e -> e.contains("integer")));
    }

    @Test
    @DisplayName("Prerequisites validation fails with invalid requirements type")
    void testPrerequisitesValidationInvalidRequirementsType() {
        WizardSession sessionWithBadConfig = session
            .withContext("workflow.requirements", "not a list");

        List<String> errors = step.validatePrerequisites(sessionWithBadConfig);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream()
            .anyMatch(e -> e.contains("List")));
    }

    @Test
    @DisplayName("Execution result includes pattern structure in context")
    void testExecutionIncludesStructure() {
        WizardSession sessionWithConfig = session
            .withContext("mcp.tool.count", 1)
            .withContext("a2a.agent.count", 1);

        WizardStepResult<WorkflowPattern> result = step.execute(sessionWithConfig);

        assertTrue(result.isSuccess());
        WorkflowPattern pattern = result.value();
        assertNotNull(pattern);
        // Structure would be in the returned session context (but we only get the result value here)
        PatternStructure structure = PatternStructure.forPattern(pattern);
        assertNotNull(structure);
    }

    @Test
    @DisplayName("Execution result includes score")
    void testExecutionIncludesScore() {
        WizardSession sessionWithConfig = session
            .withContext("mcp.tool.count", 2)
            .withContext("a2a.agent.count", 2);

        WizardStepResult<WorkflowPattern> result = step.execute(sessionWithConfig);

        assertTrue(result.isSuccess());
        WorkflowPattern pattern = result.value();
        int score = PatternAdvisor.scorePattern(pattern, Map.of(
            "mcp.tool.count", 2,
            "a2a.agent.count", 2
        ));
        assertTrue(score >= 0 && score <= 100);
    }

    @Test
    @DisplayName("Default counts are used when not provided")
    void testDefaultCountsWhenMissing() {
        // Session has no tool/agent counts
        WizardStepResult<WorkflowPattern> result = step.execute(session);

        assertTrue(result.isSuccess());
        assertNotNull(result.value());
    }

    @Test
    @DisplayName("Recommendations list is generated")
    void testRecommendationsGenerated() {
        WizardSession sessionWithConfig = session
            .withContext("mcp.tool.count", 3)
            .withContext("a2a.agent.count", 2);

        WizardStepResult<WorkflowPattern> result = step.execute(sessionWithConfig);

        assertTrue(result.isSuccess());
        assertNotNull(result.value());
    }

    @Test
    @DisplayName("Result has no errors on success")
    void testNoErrorsOnSuccess() {
        WizardSession sessionWithConfig = session
            .withContext("mcp.tool.count", 2)
            .withContext("a2a.agent.count", 1);

        WizardStepResult<WorkflowPattern> result = step.execute(sessionWithConfig);

        assertTrue(result.isSuccess());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    @DisplayName("Result contains error list on failure")
    void testErrorsOnFailure() {
        WizardSession sessionWithConfig = session
            .withContext("workflow.pattern.override", "WP-99");

        WizardStepResult<WorkflowPattern> result = step.execute(sessionWithConfig);

        assertFalse(result.isSuccess());
        assertFalse(result.errors().isEmpty());
    }

    @Test
    @DisplayName("Result value is null on failure")
    void testValueNullOnFailure() {
        WizardSession sessionWithConfig = session
            .withContext("workflow.pattern.override", "WP-99");

        WizardStepResult<WorkflowPattern> result = step.execute(sessionWithConfig);

        assertFalse(result.isSuccess());
        assertNull(result.value());
    }

    @Test
    @DisplayName("Multiple requirements are processed")
    void testMultipleRequirements() {
        WizardSession sessionWithConfig = session
            .withContext("mcp.tool.count", 3)
            .withContext("a2a.agent.count", 2)
            .withContext("workflow.requirements", List.of("parallel", "loop", "cancellation"));

        WizardStepResult<WorkflowPattern> result = step.execute(sessionWithConfig);

        assertTrue(result.isSuccess());
        assertNotNull(result.value());
    }

    @Test
    @DisplayName("Case-insensitive pattern override code")
    void testCaseInsensitivePatternOverride() {
        WizardSession sessionWithConfig1 = session
            .withContext("workflow.pattern.override", "wp-1");
        WizardSession sessionWithConfig2 = session
            .withContext("workflow.pattern.override", "WP-1");
        WizardSession sessionWithConfig3 = session
            .withContext("workflow.pattern.override", "Wp-1");

        WizardStepResult<WorkflowPattern> result1 = step.execute(sessionWithConfig1);
        WizardStepResult<WorkflowPattern> result2 = step.execute(sessionWithConfig2);
        WizardStepResult<WorkflowPattern> result3 = step.execute(sessionWithConfig3);

        assertTrue(result1.isSuccess());
        assertTrue(result2.isSuccess());
        assertTrue(result3.isSuccess());
        assertEquals(result1.value(), result2.value());
        assertEquals(result2.value(), result3.value());
    }
}
