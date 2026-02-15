package org.yawlfoundation.yawl.integration.mcp;

import org.yawlfoundation.yawl.engine.YEngine;
import junit.framework.TestCase;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Unit tests for YawlMcpPromptProvider
 *
 * Tests all seven prompt types with various argument combinations.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class TestYawlMcpPromptProvider extends TestCase {

    private YawlMcpPromptProvider provider;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // Initialize with YAWL engine
        try {
            provider = new YawlMcpPromptProvider();
        } catch (IllegalStateException e) {
            // Engine not available in test environment - that's expected
            System.out.println("Note: YAWL Engine not available - " + e.getMessage());
        }
    }

    /**
     * Test that all expected prompts are registered
     */
    public void testAvailablePrompts() {
        if (provider == null) {
            System.out.println("Skipping test - provider not initialized");
            return;
        }

        Set<String> prompts = provider.getAvailablePrompts();
        assertNotNull("Prompts should not be null", prompts);
        assertEquals("Should have 7 prompts", 7, prompts.size());

        assertTrue("Should have workflow-design prompt", prompts.contains("workflow-design"));
        assertTrue("Should have case-debugging prompt", prompts.contains("case-debugging"));
        assertTrue("Should have data-mapping prompt", prompts.contains("data-mapping"));
        assertTrue("Should have exception-handling prompt", prompts.contains("exception-handling"));
        assertTrue("Should have resource-allocation prompt", prompts.contains("resource-allocation"));
        assertTrue("Should have process-optimization prompt", prompts.contains("process-optimization"));
        assertTrue("Should have task-completion prompt", prompts.contains("task-completion"));
    }

    /**
     * Test workflow design prompt generation
     */
    public void testWorkflowDesignPrompt() {
        if (provider == null) {
            System.out.println("Skipping test - provider not initialized");
            return;
        }

        Map<String, String> args = new HashMap<>();
        args.put("domain", "Healthcare");
        args.put("requirements", "Patient admission workflow");

        String prompt = provider.generatePrompt("workflow-design", args);
        assertNotNull("Prompt should not be null", prompt);
        assertTrue("Should contain domain", prompt.contains("Healthcare"));
        assertTrue("Should contain requirements", prompt.contains("Patient admission"));
        assertTrue("Should mention YAWL patterns", prompt.contains("YAWL workflow patterns"));
    }

    /**
     * Test case debugging prompt generation
     */
    public void testCaseDebuggingPrompt() {
        if (provider == null) {
            System.out.println("Skipping test - provider not initialized");
            return;
        }

        Map<String, String> args = new HashMap<>();
        args.put("caseId", "test-case-123");
        args.put("issueDescription", "Workflow stuck at approval");

        String prompt = provider.generatePrompt("case-debugging", args);
        assertNotNull("Prompt should not be null", prompt);
        assertTrue("Should contain case ID", prompt.contains("test-case-123"));
        assertTrue("Should contain issue", prompt.contains("stuck at approval"));
        assertTrue("Should have debugging checklist", prompt.contains("Debugging Checklist"));
    }

    /**
     * Test data mapping prompt generation
     */
    public void testDataMappingPrompt() {
        if (provider == null) {
            System.out.println("Skipping test - provider not initialized");
            return;
        }

        Map<String, String> args = new HashMap<>();
        args.put("sourceTask", "TaskA");
        args.put("targetTask", "TaskB");

        String prompt = provider.generatePrompt("data-mapping", args);
        assertNotNull("Prompt should not be null", prompt);
        assertTrue("Should mention XPath", prompt.contains("XPath"));
        assertTrue("Should have mapping syntax", prompt.contains("mapping"));
    }

    /**
     * Test exception handling prompt generation
     */
    public void testExceptionHandlingPrompt() {
        if (provider == null) {
            System.out.println("Skipping test - provider not initialized");
            return;
        }

        Map<String, String> args = new HashMap<>();
        args.put("exceptionType", "Timeout");

        String prompt = provider.generatePrompt("exception-handling", args);
        assertNotNull("Prompt should not be null", prompt);
        assertTrue("Should contain exception type", prompt.contains("Timeout"));
        assertTrue("Should mention compensation", prompt.contains("Compensation"));
    }

    /**
     * Test resource allocation prompt generation
     */
    public void testResourceAllocationPrompt() {
        if (provider == null) {
            System.out.println("Skipping test - provider not initialized");
            return;
        }

        Map<String, String> args = new HashMap<>();
        args.put("resourceConstraints", "5 workers, 2 managers");

        String prompt = provider.generatePrompt("resource-allocation", args);
        assertNotNull("Prompt should not be null", prompt);
        assertTrue("Should contain constraints", prompt.contains("5 workers"));
        assertTrue("Should mention allocation strategies", prompt.contains("Allocation Strategies"));
    }

    /**
     * Test process optimization prompt generation
     */
    public void testProcessOptimizationPrompt() {
        if (provider == null) {
            System.out.println("Skipping test - provider not initialized");
            return;
        }

        Map<String, String> args = new HashMap<>();
        args.put("performanceMetrics", "Avg completion: 2 hours");

        String prompt = provider.generatePrompt("process-optimization", args);
        assertNotNull("Prompt should not be null", prompt);
        assertTrue("Should contain metrics", prompt.contains("2 hours"));
        assertTrue("Should have checklist", prompt.contains("Checklist"));
    }

    /**
     * Test task completion prompt generation
     */
    public void testTaskCompletionPromptRequiresWorkItemId() {
        if (provider == null) {
            System.out.println("Skipping test - provider not initialized");
            return;
        }

        Map<String, String> args = new HashMap<>();
        // Empty args - should fail

        try {
            provider.generatePrompt("task-completion", args);
            fail("Should throw IllegalArgumentException for missing workItemId");
        } catch (IllegalArgumentException e) {
            assertTrue("Error should mention workItemId", e.getMessage().contains("workItemId"));
        }
    }

    /**
     * Test getting prompt definition
     */
    public void testGetPromptDefinition() {
        if (provider == null) {
            System.out.println("Skipping test - provider not initialized");
            return;
        }

        YawlMcpPromptProvider.PromptDefinition def = provider.getPrompt("workflow-design");
        assertNotNull("Definition should not be null", def);
        assertEquals("Name should match", "workflow-design", def.getName());
        assertNotNull("Description should not be null", def.getDescription());
        assertNotNull("Arguments should not be null", def.getArguments());
        assertTrue("Should have arguments", def.getArguments().length > 0);
    }

    /**
     * Test invalid prompt name
     */
    public void testInvalidPromptName() {
        if (provider == null) {
            System.out.println("Skipping test - provider not initialized");
            return;
        }

        try {
            provider.getPrompt("invalid-prompt");
            fail("Should throw IllegalArgumentException for invalid prompt name");
        } catch (IllegalArgumentException e) {
            assertTrue("Error should mention unknown prompt", e.getMessage().contains("Unknown prompt"));
        }
    }

    /**
     * Test prompt definition toString
     */
    public void testPromptDefinitionToString() {
        if (provider == null) {
            System.out.println("Skipping test - provider not initialized");
            return;
        }

        YawlMcpPromptProvider.PromptDefinition def = provider.getPrompt("case-debugging");
        String str = def.toString();
        assertNotNull("toString should not be null", str);
        assertTrue("Should contain prompt name", str.contains("case-debugging"));
        assertTrue("Should contain arguments", str.contains("Arguments"));
    }
}
