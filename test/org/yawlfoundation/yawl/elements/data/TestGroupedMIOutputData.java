package org.yawlfoundation.yawl.elements.data;

import static org.junit.jupiter.api.Assertions.*;

import org.jdom2.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.elements.GroupedMIOutputData;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.YWorkItemID;
import org.yawlfoundation.yawl.util.JDOMUtil;

/**
 * Chicago TDD tests for GroupedMIOutputData.
 * Tests multi-instance output aggregation.
 */
@DisplayName("GroupedMIOutputData Tests")
@Tag("unit")
class TestGroupedMIOutputData {

    private YIdentifier caseId;
    private static final String TASK_ID = "miTask";
    private static final String ROOT_NAME = "data";

    @BeforeEach
    void setUp() {
        caseId = new YIdentifier("testCase1");
    }

    @Nested
    @DisplayName("GroupedMIOutputData Creation Tests")
    class CreationTests {

        @Test
        @DisplayName("Default constructor should work for Hibernate")
        void defaultConstructorWorks() {
            GroupedMIOutputData data = new GroupedMIOutputData();
            assertNotNull(data);
        }

        @Test
        @DisplayName("Constructor with case ID, task ID, and root name should initialize fields")
        void constructorWithIdsInitializesFields() {
            GroupedMIOutputData data = new TestableGroupedMIOutputData(caseId, TASK_ID, ROOT_NAME);

            assertEquals("testCase1:miTask", data.getUniqueIdentifier());
            assertEquals("testCase1", data.getCaseID());
        }
    }

    @Nested
    @DisplayName("Unique Identifier Tests")
    class UniqueIdentifierTests {

        @Test
        @DisplayName("GetUniqueIdentifier returns caseId:taskId format")
        void getUniqueIdentifierReturnsCorrectFormat() {
            GroupedMIOutputData data = new TestableGroupedMIOutputData(caseId, TASK_ID, ROOT_NAME);
            assertEquals("testCase1:miTask", data.getUniqueIdentifier());
        }

        @Test
        @DisplayName("SetUniqueIdentifier should update identifier")
        void setUniqueIdentifierUpdatesValue() {
            GroupedMIOutputData data = new GroupedMIOutputData();
            data.setUniqueIdentifier("newCase:newTask");
            assertEquals("newCase:newTask", data.getUniqueIdentifier());
        }

        @Test
        @DisplayName("GetCaseID extracts case ID from unique identifier")
        void getCaseIdExtractsCaseId() {
            GroupedMIOutputData data = new TestableGroupedMIOutputData(caseId, TASK_ID, ROOT_NAME);
            assertEquals("testCase1", data.getCaseID());
        }
    }

    @Nested
    @DisplayName("Completed Work Items Tests")
    class CompletedWorkItemsTests {

        @Test
        @DisplayName("GetCompletedWorkItems returns empty list initially")
        void getCompletedWorkItemsReturnsEmptyInitially() {
            GroupedMIOutputData data = new TestableGroupedMIOutputData(caseId, TASK_ID, ROOT_NAME);
            assertTrue(data.getCompletedWorkItems().isEmpty());
        }

        @Test
        @DisplayName("AddCompletedWorkItem adds item to list")
        void addCompletedWorkItemAddsToList() {
            GroupedMIOutputData data = new TestableGroupedMIOutputData(caseId, TASK_ID, ROOT_NAME);

            YWorkItem item = createTestWorkItem("child1");
            data.addCompletedWorkItem(item);

            assertEquals(1, data.getCompletedWorkItems().size());
        }

        @Test
        @DisplayName("AddCompletedWorkItem preserves multiple items")
        void addCompletedWorkItemPreservesMultipleItems() {
            GroupedMIOutputData data = new TestableGroupedMIOutputData(caseId, TASK_ID, ROOT_NAME);

            data.addCompletedWorkItem(createTestWorkItem("child1"));
            data.addCompletedWorkItem(createTestWorkItem("child2"));
            data.addCompletedWorkItem(createTestWorkItem("child3"));

            assertEquals(3, data.getCompletedWorkItems().size());
        }
    }

    @Nested
    @DisplayName("Hibernate Serialization Tests")
    class HibernateSerializationTests {

        @Test
        @DisplayName("SetDataDocString and GetDataDocString roundtrip")
        void dataDocStringRoundtrip() {
            GroupedMIOutputData data = new TestableGroupedMIOutputData(caseId, TASK_ID, ROOT_NAME);

            String xml = "<data><item>test</item></data>";
            data.setDataDocString(xml);

            assertEquals(xml, data.getDataDocString());
        }

        @Test
        @DisplayName("SetCompletedItemsString and GetCompletedItemsString roundtrip")
        void completedItemsStringRoundtrip() {
            TestableGroupedMIOutputData data = new TestableGroupedMIOutputData(caseId, TASK_ID, ROOT_NAME);

            String xml = "<items><item>test</item></items>";
            data.setCompletedItemsString(xml);

            assertEquals(xml, data.getCompletedItemsString());
        }
    }

    /**
     * Testable subclass that exposes protected constructor.
     */
    private static class TestableGroupedMIOutputData extends GroupedMIOutputData {
        TestableGroupedMIOutputData(YIdentifier caseID, String taskID, String rootName) {
            super(caseID, taskID, rootName);
        }

        @Override public String getCompletedItemsString() { return super.getCompletedItemsString(); }
        @Override public void setCompletedItemsString(String xml) { super.setCompletedItemsString(xml); }
    }

    /**
     * Creates a test work item with minimal required fields.
     */
    private YWorkItem createTestWorkItem(String uniqueId) {
        YIdentifier childId = new YIdentifier(caseId.get_idString() + "." + uniqueId);
        YWorkItemID workItemId = new YWorkItemID(caseId, TASK_ID, uniqueId);

        YWorkItem item = new YWorkItem();
        item.setWorkItemID(workItemId);
        item.set_thisID(workItemId.toString() + "!" + uniqueId);
        item.set_status("Completed");

        return item;
    }
}
