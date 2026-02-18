package org.yawlfoundation.yawl.integration.autonomous;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.integration.autonomous.reasoners.StaticMappingReasoner;

import junit.framework.TestCase;

/**
 * Integration tests for StaticMappingReasoner using real WorkItemRecord objects.
 *
 * Chicago TDD: No mocks. Real WorkItemRecord instances with real eligibility
 * evaluation against real StaticMappingReasoner logic.
 *
 * Coverage target: 90%+ line coverage on StaticMappingReasoner.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class StaticMappingReasonerTest extends TestCase {

    private AgentCapability orderingCapability;
    private AgentCapability financeCapability;
    private StaticMappingReasoner orderingReasoner;
    private StaticMappingReasoner financeReasoner;

    public StaticMappingReasonerTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        orderingCapability = new AgentCapability("Ordering",
                "procurement, purchase orders, approvals, supplier management");
        financeCapability = new AgentCapability("Finance",
                "invoicing, payments, financial reporting, budget control");

        orderingReasoner = new StaticMappingReasoner(orderingCapability);
        orderingReasoner.addMapping("Approve_Purchase_Order", "Ordering, Finance");
        orderingReasoner.addMapping("Create_Purchase_Order", "Ordering");
        orderingReasoner.addMapping("Review_Supplier", "Ordering");

        financeReasoner = new StaticMappingReasoner(financeCapability);
        financeReasoner.addMapping("Create_Invoice", "Finance, Accounting");
        financeReasoner.addMapping("Approve_Payment", "Finance");
        financeReasoner.addMapping("Approve_Purchase_Order", "Ordering, Finance");
    }

    @Override
    protected void tearDown() throws Exception {
        orderingReasoner = null;
        financeReasoner = null;
        super.tearDown();
    }

    /**
     * Verify ordering agent is eligible for a task it is mapped to (exact match).
     */
    public void testIsEligibleExactTaskNameMatch() {
        WorkItemRecord workItem = buildWorkItem("100.1", "Create_Purchase_Order");

        boolean eligible = orderingReasoner.isEligible(workItem);

        assertTrue("Ordering agent should be eligible for Create_Purchase_Order", eligible);
    }

    /**
     * Verify ordering agent is not eligible for a finance-only task.
     */
    public void testIsNotEligibleForOtherDomainTask() {
        WorkItemRecord workItem = buildWorkItem("100.2", "Create_Invoice");

        boolean eligible = orderingReasoner.isEligible(workItem);

        assertFalse("Ordering agent should not be eligible for Create_Invoice", eligible);
    }

    /**
     * Verify both ordering and finance agents are eligible for a shared task.
     */
    public void testBothAgentsEligibleForSharedTask() {
        WorkItemRecord workItem = buildWorkItem("100.3", "Approve_Purchase_Order");

        assertTrue("Ordering should be eligible for Approve_Purchase_Order",
                orderingReasoner.isEligible(workItem));
        assertTrue("Finance should be eligible for Approve_Purchase_Order",
                financeReasoner.isEligible(workItem));
    }

    /**
     * Verify no agent is eligible for an unmapped task.
     */
    public void testNoAgentEligibleForUnmappedTask() {
        WorkItemRecord workItem = buildWorkItem("100.4", "Ship_Order");

        assertFalse("Ordering should not be eligible for unmapped Ship_Order",
                orderingReasoner.isEligible(workItem));
        assertFalse("Finance should not be eligible for unmapped Ship_Order",
                financeReasoner.isEligible(workItem));
    }

    /**
     * Verify wildcard * pattern matches any task name.
     */
    public void testWildcardStarMatchesAnyTask() {
        StaticMappingReasoner catchAllReasoner = new StaticMappingReasoner(orderingCapability);
        catchAllReasoner.addMapping("*", "Ordering");

        WorkItemRecord item1 = buildWorkItem("200.1", "Any_Task_Name");
        WorkItemRecord item2 = buildWorkItem("200.2", "Some_Other_Task");

        assertTrue("Wildcard * should match Any_Task_Name", catchAllReasoner.isEligible(item1));
        assertTrue("Wildcard * should match Some_Other_Task", catchAllReasoner.isEligible(item2));
    }

    /**
     * Verify ? wildcard matches single character substitution.
     */
    public void testWildcardQuestionMarkMatchesSingleChar() {
        StaticMappingReasoner reasoner = new StaticMappingReasoner(orderingCapability);
        reasoner.addMapping("Task_?", "Ordering");

        WorkItemRecord matchItem = buildWorkItem("300.1", "Task_A");
        WorkItemRecord noMatchItem = buildWorkItem("300.2", "Task_AB");

        assertTrue("? should match single char Task_A", reasoner.isEligible(matchItem));
        assertFalse("? should not match two chars Task_AB", reasoner.isEligible(noMatchItem));
    }

    /**
     * Verify null work item throws IllegalArgumentException.
     */
    public void testNullWorkItemThrowsException() {
        try {
            orderingReasoner.isEligible(null);
            fail("Expected IllegalArgumentException for null workItem");
        } catch (IllegalArgumentException e) {
            assertTrue("Exception message should mention workItem",
                    e.getMessage().contains("workItem"));
        }
    }

    /**
     * Verify null capability in constructor throws IllegalArgumentException.
     */
    public void testNullCapabilityThrowsException() {
        try {
            new StaticMappingReasoner(null);
            fail("Expected IllegalArgumentException for null capability");
        } catch (IllegalArgumentException e) {
            assertTrue("Exception should mention capability",
                    e.getMessage().contains("capability"));
        }
    }

    /**
     * Verify task name falls back to task ID when task name is null.
     */
    public void testFallsBackToTaskIdWhenTaskNameNull() {
        orderingReasoner.addMapping("task_id_fallback", "Ordering");
        WorkItemRecord workItem = new WorkItemRecord("400.1", "task_id_fallback",
                "http://test/spec.yawl", WorkItemRecord.statusEnabled);

        boolean eligible = orderingReasoner.isEligible(workItem);

        assertTrue("Should match by task ID when task name is null", eligible);
    }

    /**
     * Verify getConfiguredTasks returns all added task names.
     */
    public void testGetConfiguredTasksReturnsAllMappings() {
        Set<String> tasks = orderingReasoner.getConfiguredTasks();

        assertEquals("Should have 3 configured tasks", 3, tasks.size());
        assertTrue("Should contain Approve_Purchase_Order",
                tasks.contains("Approve_Purchase_Order"));
        assertTrue("Should contain Create_Purchase_Order",
                tasks.contains("Create_Purchase_Order"));
        assertTrue("Should contain Review_Supplier", tasks.contains("Review_Supplier"));
    }

    /**
     * Verify getCapabilitiesForTask returns capabilities for a known task.
     */
    public void testGetCapabilitiesForKnownTask() {
        Set<String> caps = orderingReasoner.getCapabilitiesForTask("Approve_Purchase_Order");

        assertEquals("Approve_Purchase_Order should have 2 capabilities", 2, caps.size());
        assertTrue("Should contain Ordering", caps.contains("Ordering"));
        assertTrue("Should contain Finance", caps.contains("Finance"));
    }

    /**
     * Verify getCapabilitiesForTask returns empty set for unknown task.
     */
    public void testGetCapabilitiesForUnknownTaskReturnsEmpty() {
        Set<String> caps = orderingReasoner.getCapabilitiesForTask("Unknown_Task");

        assertNotNull("Should never return null", caps);
        assertTrue("Should be empty for unknown task", caps.isEmpty());
    }

    /**
     * Verify getCapabilitiesForTask returns empty set for null task name.
     */
    public void testGetCapabilitiesForNullTaskReturnsEmpty() {
        Set<String> caps = orderingReasoner.getCapabilitiesForTask(null);

        assertNotNull("Should never return null", caps);
        assertTrue("Should be empty for null task", caps.isEmpty());
    }

    /**
     * Verify clearMappings removes all configured tasks.
     */
    public void testClearMappingsRemovesAllTasks() {
        orderingReasoner.clearMappings();

        Set<String> tasks = orderingReasoner.getConfiguredTasks();
        assertTrue("All tasks should be removed after clear", tasks.isEmpty());

        WorkItemRecord workItem = buildWorkItem("500.1", "Create_Purchase_Order");
        assertFalse("No task should be eligible after clear",
                orderingReasoner.isEligible(workItem));
    }

    /**
     * Verify constructor with pre-built mappings map works correctly.
     */
    public void testConstructorWithMappingsMap() {
        Map<String, Set<String>> mappings = new HashMap<>();
        Set<String> caps = new HashSet<>();
        caps.add("Ordering");
        mappings.put("Process_Order", caps);

        StaticMappingReasoner reasoner = new StaticMappingReasoner(orderingCapability, mappings);
        WorkItemRecord workItem = buildWorkItem("600.1", "Process_Order");

        assertTrue("Agent should be eligible from constructor-injected mappings",
                reasoner.isEligible(workItem));
    }

    /**
     * Verify null mappings map throws IllegalArgumentException.
     */
    public void testNullMappingsMapThrowsException() {
        try {
            new StaticMappingReasoner(orderingCapability, null);
            fail("Expected IllegalArgumentException for null mappings");
        } catch (IllegalArgumentException e) {
            assertTrue("Exception should mention mappings",
                    e.getMessage().contains("mappings"));
        }
    }

    /**
     * Verify addMapping with empty taskName throws IllegalArgumentException.
     */
    public void testAddMappingEmptyTaskNameThrowsException() {
        try {
            orderingReasoner.addMapping("", "Ordering");
            fail("Expected IllegalArgumentException for empty taskName");
        } catch (IllegalArgumentException e) {
            assertTrue("Exception should mention taskName",
                    e.getMessage().contains("taskName"));
        }
    }

    /**
     * Verify addMapping with null capabilities string throws IllegalArgumentException.
     */
    public void testAddMappingNullCapabilitiesThrowsException() {
        try {
            orderingReasoner.addMapping("Some_Task", (String) null);
            fail("Expected IllegalArgumentException for null capabilities");
        } catch (IllegalArgumentException e) {
            assertNotNull("Exception message should be non-null", e.getMessage());
        }
    }

    /**
     * Verify eligibility check is case-sensitive on capability domain name.
     */
    public void testEligibilityIsCaseSensitiveOnDomainName() {
        AgentCapability lowerCaseCapability = new AgentCapability("ordering",
                "lower case domain name test");
        StaticMappingReasoner lowerReasoner = new StaticMappingReasoner(lowerCaseCapability);
        lowerReasoner.addMapping("Create_Purchase_Order", "Ordering");

        WorkItemRecord workItem = buildWorkItem("700.1", "Create_Purchase_Order");
        assertFalse("Case-sensitive: 'ordering' should not match 'Ordering' capability",
                lowerReasoner.isEligible(workItem));
    }

    /**
     * Verify prefix wildcard pattern (e.g., Approve_*) matches correctly.
     */
    public void testPrefixWildcardMatchesCorrectly() {
        StaticMappingReasoner reasoner = new StaticMappingReasoner(orderingCapability);
        reasoner.addMapping("Approve_*", "Ordering");

        WorkItemRecord match1 = buildWorkItem("800.1", "Approve_Purchase_Order");
        WorkItemRecord match2 = buildWorkItem("800.2", "Approve_Leave_Request");
        WorkItemRecord noMatch = buildWorkItem("800.3", "Create_Purchase_Order");

        assertTrue("Approve_* should match Approve_Purchase_Order",
                reasoner.isEligible(match1));
        assertTrue("Approve_* should match Approve_Leave_Request",
                reasoner.isEligible(match2));
        assertFalse("Approve_* should not match Create_Purchase_Order",
                reasoner.isEligible(noMatch));
    }

    private WorkItemRecord buildWorkItem(String caseId, String taskId) {
        WorkItemRecord wir = new WorkItemRecord(caseId, taskId,
                "http://test/spec.yawl", WorkItemRecord.statusEnabled);
        wir.setTaskName(taskId);
        return wir;
    }
}
