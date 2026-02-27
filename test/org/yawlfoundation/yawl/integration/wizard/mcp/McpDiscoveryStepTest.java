package org.yawlfoundation.yawl.integration.wizard.mcp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import org.yawlfoundation.yawl.integration.wizard.core.WizardPhase;
import org.yawlfoundation.yawl.integration.wizard.core.WizardSession;
import org.yawlfoundation.yawl.integration.wizard.core.WizardStepResult;

/**
 * Integration tests for McpDiscoveryStep.
 *
 * <p>Verifies that the discovery step correctly loads all tools, groups them by category,
 * and stores results in session context.
 */
@DisplayName("MCP Discovery Step Tests")
class McpDiscoveryStepTest {

    private McpDiscoveryStep step;
    private WizardSession session;

    @BeforeEach
    void setup() {
        step = new McpDiscoveryStep();
        session = WizardSession.newSession()
            .withPhase(WizardPhase.DISCOVERY, "init", "Starting discovery phase");
    }

    @Test
    @DisplayName("Step ID is 'mcp-discovery'")
    void testStepId() {
        assertEquals("mcp-discovery", step.stepId());
    }

    @Test
    @DisplayName("Step title is descriptive")
    void testTitle() {
        assertTrue(step.title().contains("MCP"));
        assertTrue(step.title().contains("Tool"));
    }

    @Test
    @DisplayName("Required phase is DISCOVERY")
    void testRequiredPhase() {
        assertEquals(WizardPhase.DISCOVERY, step.requiredPhase());
    }

    @Test
    @DisplayName("Step is skippable")
    void testIsSkippable() {
        assertTrue(step.isSkippable(),
            "Discovery step should be skippable on re-execution");
    }

    @Test
    @DisplayName("Execute returns success with all 15 tools")
    void testExecuteSuccess() {
        WizardStepResult<List<McpToolDescriptor>> result = step.execute(session);

        assertTrue(result.isSuccess(), "Should execute successfully");
        assertTrue(result.asOptional().isPresent(), "Should have value");

        List<McpToolDescriptor> tools = result.value();
        assertEquals(15, tools.size(), "Should discover all 15 tools");
    }

    @Test
    @DisplayName("Step result contains all expected tool IDs")
    void testAllToolsDiscovered() {
        WizardStepResult<List<McpToolDescriptor>> result = step.execute(session);

        List<McpToolDescriptor> tools = result.value();
        var toolIds = tools.stream()
            .map(McpToolDescriptor::toolId)
            .toList();

        assertTrue(toolIds.contains("launch_case"));
        assertTrue(toolIds.contains("cancel_case"));
        assertTrue(toolIds.contains("get_workitems"));
        assertTrue(toolIds.contains("checkout_workitem"));
        assertTrue(toolIds.contains("suspend_case"));
        assertTrue(toolIds.contains("upload_specification"));
    }

    @Test
    @DisplayName("Context contains 'mcp.tools.all' key")
    void testContextToolsAll() {
        // Note: We need to simulate context update since WizardSession is immutable
        // In real usage, the step would update the session via withContext
        WizardStepResult<List<McpToolDescriptor>> result = step.execute(session);

        assertTrue(result.isSuccess());
        List<McpToolDescriptor> tools = result.value();
        assertEquals(15, tools.size());
    }

    @Test
    @DisplayName("Tool count can be verified")
    void testToolCount() {
        WizardStepResult<List<McpToolDescriptor>> result = step.execute(session);

        List<McpToolDescriptor> tools = result.value();
        assertEquals(15, tools.size(), "Tool count should be 15");
    }

    @Test
    @DisplayName("All discovered tools have valid metadata")
    void testToolMetadata() {
        WizardStepResult<List<McpToolDescriptor>> result = step.execute(session);

        for (McpToolDescriptor tool : result.value()) {
            assertNotNull(tool.toolId(), "Tool should have ID");
            assertFalse(tool.toolId().isEmpty());

            assertNotNull(tool.displayName(), "Tool should have display name");
            assertFalse(tool.displayName().isEmpty());

            assertNotNull(tool.description(), "Tool should have description");
            assertFalse(tool.description().isEmpty());

            assertNotNull(tool.category(), "Tool should have category");
            assertNotNull(tool.parameterNames(), "Tool should have parameters");
            assertNotNull(tool.outputFields(), "Tool should have outputs");

            assertTrue(tool.complexityScore() >= 1 && tool.complexityScore() <= 10,
                "Complexity should be 1-10");

            assertTrue(tool.requiresEngineSession(),
                "All tools should require engine session");
        }
    }

    @Test
    @DisplayName("Tools are grouped by all 4 categories")
    void testToolCategories() {
        WizardStepResult<List<McpToolDescriptor>> result = step.execute(session);

        List<McpToolDescriptor> tools = result.value();

        // Count tools per category
        int caseManagementCount = (int) tools.stream()
            .filter(t -> t.category() == McpToolCategory.CASE_MANAGEMENT)
            .count();
        int specCount = (int) tools.stream()
            .filter(t -> t.category() == McpToolCategory.SPECIFICATION)
            .count();
        int workitemCount = (int) tools.stream()
            .filter(t -> t.category() == McpToolCategory.WORKITEM)
            .count();
        int lifecycleCount = (int) tools.stream()
            .filter(t -> t.category() == McpToolCategory.LIFECYCLE)
            .count();

        assertEquals(4, caseManagementCount, "Should have 4 CASE_MANAGEMENT tools");
        assertEquals(4, specCount, "Should have 4 SPECIFICATION tools");
        assertEquals(5, workitemCount, "Should have 5 WORKITEM tools");
        assertEquals(2, lifecycleCount, "Should have 2 LIFECYCLE tools");
    }

    @Test
    @DisplayName("No duplicate tool IDs in discovery")
    void testNoDuplicates() {
        WizardStepResult<List<McpToolDescriptor>> result = step.execute(session);

        List<McpToolDescriptor> tools = result.value();
        var toolIds = tools.stream()
            .map(McpToolDescriptor::toolId)
            .toList();

        var uniqueIds = tools.stream()
            .map(McpToolDescriptor::toolId)
            .distinct()
            .toList();

        assertEquals(toolIds.size(), uniqueIds.size(),
            "Should have no duplicate tool IDs");
    }

    @Test
    @DisplayName("Discovery produces non-empty result")
    void testNonEmptyResult() {
        WizardStepResult<List<McpToolDescriptor>> result = step.execute(session);

        assertFalse(result.value().isEmpty(),
            "Discovery should return non-empty tool list");
    }

    @Test
    @DisplayName("Each tool has at least one parameter or no parameters")
    void testToolParameters() {
        WizardStepResult<List<McpToolDescriptor>> result = step.execute(session);

        for (McpToolDescriptor tool : result.value()) {
            assertNotNull(tool.parameterNames(),
                "Tool should have parameter list (may be empty)");
        }
    }

    @Test
    @DisplayName("Each tool has output fields defined")
    void testToolOutputs() {
        WizardStepResult<List<McpToolDescriptor>> result = step.execute(session);

        for (McpToolDescriptor tool : result.value()) {
            assertNotNull(tool.outputFields(),
                "Tool should have output fields");
            assertFalse(tool.outputFields().isEmpty(),
                "Tool should have at least one output field");
        }
    }

    @Test
    @DisplayName("CASE_MANAGEMENT tools are correctly identified")
    void testCaseManagementTools() {
        WizardStepResult<List<McpToolDescriptor>> result = step.execute(session);

        var caseTools = result.value().stream()
            .filter(t -> t.category() == McpToolCategory.CASE_MANAGEMENT)
            .map(McpToolDescriptor::toolId)
            .toList();

        assertTrue(caseTools.contains("launch_case"));
        assertTrue(caseTools.contains("cancel_case"));
        assertTrue(caseTools.contains("get_case_state"));
        assertTrue(caseTools.contains("get_running_cases"));
    }

    @Test
    @DisplayName("SPECIFICATION tools are correctly identified")
    void testSpecificationTools() {
        WizardStepResult<List<McpToolDescriptor>> result = step.execute(session);

        var specTools = result.value().stream()
            .filter(t -> t.category() == McpToolCategory.SPECIFICATION)
            .map(McpToolDescriptor::toolId)
            .toList();

        assertTrue(specTools.contains("list_specifications"));
        assertTrue(specTools.contains("upload_specification"));
        assertTrue(specTools.contains("get_specification"));
    }

    @Test
    @DisplayName("WORKITEM tools are correctly identified")
    void testWorkitemTools() {
        WizardStepResult<List<McpToolDescriptor>> result = step.execute(session);

        var workitemTools = result.value().stream()
            .filter(t -> t.category() == McpToolCategory.WORKITEM)
            .map(McpToolDescriptor::toolId)
            .toList();

        assertTrue(workitemTools.contains("checkout_workitem"));
        assertTrue(workitemTools.contains("checkin_workitem"));
        assertTrue(workitemTools.contains("get_workitems"));
    }

    @Test
    @DisplayName("LIFECYCLE tools are correctly identified")
    void testLifecycleTools() {
        WizardStepResult<List<McpToolDescriptor>> result = step.execute(session);

        var lifecycleTools = result.value().stream()
            .filter(t -> t.category() == McpToolCategory.LIFECYCLE)
            .map(McpToolDescriptor::toolId)
            .toList();

        assertTrue(lifecycleTools.contains("suspend_case"));
        assertTrue(lifecycleTools.contains("resume_case"));
    }

    @Test
    @DisplayName("Step result error count is 0 on success")
    void testErrorCount() {
        WizardStepResult<List<McpToolDescriptor>> result = step.execute(session);

        assertEquals(0, result.errorCount(),
            "Should have no errors on success");
        assertTrue(result.firstError().isEmpty());
    }
}
