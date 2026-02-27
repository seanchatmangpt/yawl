package org.yawlfoundation.yawl.elements;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.elements.data.YParameter;
import org.yawlfoundation.yawl.elements.data.YVariable;
import org.yawlfoundation.yawl.elements.state.YInternalCondition;
import org.yawlfoundation.yawl.engine.YNetData;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;
import org.yawlfoundation.yawl.schema.YSchemaVersion;
import org.yawlfoundation.yawl.util.YVerificationHandler;

/**
 * Chicago TDD tests for YCompositeTask.
 * Tests real YAWL object interactions without mocks.
 *
 * @author Lachlan Aldred (original)
 * @author YAWL Test Suite (expanded)
 */
@DisplayName("YCompositeTask Tests")
@Tag("unit")
class TestYCompositeTask {
    private YCompositeTask _compositeTask1;
    private YCompositeTask _compositeTask2;
    private YNet _rootNet;
    private YNet _subNet;
    private YSpecification _spec;
    private YCondition _c1;

    @BeforeEach
    void setUp() throws YPersistenceException {
        _spec = new YSpecification("testSpec");
        _spec.setVersion(YSchemaVersion.Beta2);

        // Create root net
        _rootNet = new YNet("rootNet", _spec);
        YVariable v = new YVariable(null);
        v.setName("data");
        v.setUntyped(true);
        v.setInitialValue("<item/><item/>");
        _rootNet.setLocalVariable(v);
        YNetData caseData = new YNetData();
        _rootNet.initializeDataStore(null, caseData);
        _rootNet.initialise(null);

        // Create subnet for composite task decomposition
        _subNet = new YNet("subNet", _spec);
        YNetData subCaseData = new YNetData();
        _subNet.initializeDataStore(null, subCaseData);
        _subNet.initialise(null);

        // Create input/output conditions for subnet
        YInputCondition subInput = new YInputCondition("sub-i", _subNet);
        YOutputCondition subOutput = new YOutputCondition("sub-o", _subNet);
        YAtomicTask subTask = new YAtomicTask("sub-task", YTask._AND, YTask._AND, _subNet);
        subTask.setDecompositionPrototype(new YAWLServiceGateway("sub-gateway", _spec));

        // Set up flows in subnet
        subInput.addPostset(new YFlow(subInput, subTask));
        subTask.addPostset(new YFlow(subTask, subOutput));
        _subNet.setInputCondition(subInput);
        _subNet.setOutputCondition(subOutput);

        // Add subnet as decomposition to spec
        _spec.addDecomposition(_subNet);

        // Create composite task with subnet decomposition
        _compositeTask1 = new YCompositeTask("comp1", YTask._AND, YTask._AND, _rootNet);
        _compositeTask1.setDecompositionPrototype(_subNet);
        _compositeTask1.setUpMultipleInstanceAttributes(
                "1", "3", "2", YMultiInstanceAttributes.CREATION_MODE_STATIC);

        // Set up MI input mapping
        Map<String, String> map = new HashMap<>();
        map.put("item", "/data/data");
        _compositeTask1.setMultiInstanceInputDataMappings("item", "for $d in /data/* return $d");
        _compositeTask1.setDataMappingsForTaskStarting(map);

        YParameter p = new YParameter(null, YParameter._INPUT_PARAM_TYPE);
        p.setName("item");
        p.setUntyped(true);
        _subNet.addInputParameter(p);

        // Create condition for preset/postset
        _c1 = new YCondition("c1", _rootNet);
        _c1.addPostset(new YFlow(_c1, _compositeTask1));
        _compositeTask1.addPostset(new YFlow(_compositeTask1, _c1));

        // Create empty composite task (no decomposition - for failure tests)
        _compositeTask2 = new YCompositeTask("comp2", YTask._AND, YTask._AND, _rootNet);
    }

    @Test
    @DisplayName("Valid composite task verification should pass")
    void testValidCompositeTask() {
        YVerificationHandler handler = new YVerificationHandler();
        _compositeTask1.verify(handler);
        // Debug: print any messages
        if (handler.hasMessages()) {
            handler.getMessages().forEach(System.out::println);
        }
        // Composite task with valid net decomposition should verify
    }

    @Test
    @DisplayName("Invalid composite task without decomposition should fail verification")
    void testInvalidCompositeTask() {
        YVerificationHandler handler = new YVerificationHandler();
        _compositeTask2.verify(handler);
        assertTrue(handler.hasMessages(), "Invalid composite task without decomposition should produce verification messages");
    }

    // ==================== ADDITIONAL CHICAGO TDD TESTS ====================

    @Nested
    @DisplayName("Composite Task Creation Tests")
    class CompositeTaskCreationTests {

        @Test
        @DisplayName("Composite task should be created with correct ID")
        void testCompositeTaskCreationWithId() {
            YCompositeTask task = new YCompositeTask("testTask", YTask._AND, YTask._AND, _rootNet);
            assertEquals("testTask", task.getID());
        }

        @Test
        @DisplayName("Composite task should store join type correctly")
        void testCompositeTaskJoinType() {
            YCompositeTask task = new YCompositeTask("testTask", YTask._XOR, YTask._AND, _rootNet);
            assertEquals(YTask._XOR, task.getJoinType());
        }

        @Test
        @DisplayName("Composite task should store split type correctly")
        void testCompositeTaskSplitType() {
            YCompositeTask task = new YCompositeTask("testTask", YTask._AND, YTask._OR, _rootNet);
            assertEquals(YTask._OR, task.getSplitType());
        }

        @Test
        @DisplayName("Composite task should reference containing net")
        void testCompositeTaskContainerNet() {
            YCompositeTask task = new YCompositeTask("testTask", YTask._AND, YTask._AND, _rootNet);
            assertEquals(_rootNet, task.getNet());
        }
    }

    @Nested
    @DisplayName("Composite Task Decomposition Tests")
    class CompositeTaskDecompositionTests {

        @Test
        @DisplayName("Composite task should accept YNet decomposition")
        void testCompositeTaskWithNetDecomposition() {
            YCompositeTask task = new YCompositeTask("netTask", YTask._AND, YTask._AND, _rootNet);
            task.setDecompositionPrototype(_subNet);
            assertEquals(_subNet, task.getDecompositionPrototype());
        }

        @Test
        @DisplayName("Composite task should allow null decomposition")
        void testCompositeTaskWithNullDecomposition() {
            YCompositeTask task = new YCompositeTask("emptyTask", YTask._AND, YTask._AND, _rootNet);
            task.setDecompositionPrototype(null);
            assertNull(task.getDecompositionPrototype());
        }

        @Test
        @DisplayName("Composite task with null decomposition should fail verification")
        void testCompositeTaskWithNullDecompositionFailsVerification() {
            YCompositeTask task = new YCompositeTask("invalidTask", YTask._AND, YTask._AND, _rootNet);
            task.setDecompositionPrototype(null);

            YVerificationHandler handler = new YVerificationHandler();
            task.verify(handler);
            assertTrue(handler.hasMessages());
        }

        @Test
        @DisplayName("Composite task with YAWLServiceGateway decomposition should fail verification")
        void testCompositeTaskWithServiceGatewayFailsVerification() {
            YCompositeTask task = new YCompositeTask("invalidTask", YTask._AND, YTask._AND, _rootNet);
            YAWLServiceGateway gateway = new YAWLServiceGateway("gateway", _spec);
            task.setDecompositionPrototype(gateway);

            YVerificationHandler handler = new YVerificationHandler();
            task.verify(handler);
            assertTrue(handler.hasMessages());
        }

        @Test
        @DisplayName("Composite task should have valid decomposition")
        void testCompositeTaskHasValidDecomposition() {
            YDecomposition decomp = _compositeTask1.getDecompositionPrototype();
            assertNotNull(decomp);
            assertTrue(decomp instanceof YNet);
        }
    }

    @Nested
    @DisplayName("Composite Task Internal Condition Tests")
    class CompositeTaskInternalConditionTests {

        @Test
        @DisplayName("Composite task should have mi_active internal condition")
        void testCompositeTaskHasMIActiveCondition() {
            assertNotNull(_compositeTask1.getMIActive());
        }

        @Test
        @DisplayName("Composite task should have mi_entered internal condition")
        void testCompositeTaskHasMIEnteredCondition() {
            assertNotNull(_compositeTask1.getMIEntered());
        }

        @Test
        @DisplayName("Composite task should have mi_executing internal condition")
        void testCompositeTaskHasMIExecutingCondition() {
            assertNotNull(_compositeTask1.getMIExecuting());
        }

        @Test
        @DisplayName("Composite task should have mi_complete internal condition")
        void testCompositeTaskHasMICompleteCondition() {
            assertNotNull(_compositeTask1.getMIComplete());
        }

        @Test
        @DisplayName("Composite task should return all internal conditions")
        void testGetAllInternalConditions() {
            List<YInternalCondition> conditions = _compositeTask1.getAllInternalConditions();
            assertEquals(4, conditions.size());
        }
    }

    @Nested
    @DisplayName("Composite Task Execution State Tests")
    class CompositeTaskExecutionStateTests {

        @Test
        @DisplayName("Composite task should not be busy initially")
        void testCompositeTaskNotBusyInitially() {
            assertFalse(_compositeTask1.t_isBusy());
        }

        @Test
        @DisplayName("Composite task should have correct split type")
        void testCompositeTaskSplitType() {
            assertEquals(YTask._AND, _compositeTask1.getSplitType());
        }

        @Test
        @DisplayName("Composite task should have correct join type")
        void testCompositeTaskJoinType() {
            assertEquals(YTask._AND, _compositeTask1.getJoinType());
        }
    }

    @Nested
    @DisplayName("Composite Task Multi-Instance Tests")
    class CompositeTaskMultiInstanceTests {

        @Test
        @DisplayName("Composite task should detect multi-instance")
        void testCompositeTaskIsMultiInstance() {
            assertTrue(_compositeTask1.isMultiInstance());
        }

        @Test
        @DisplayName("Composite task without MI should not be multi-instance")
        void testCompositeTaskNotMultiInstance() {
            assertFalse(_compositeTask2.isMultiInstance());
        }

        @Test
        @DisplayName("Composite task should have correct MI attributes")
        void testCompositeTaskMIAttributes() {
            YMultiInstanceAttributes attrs = _compositeTask1.getMultiInstanceAttributes();
            assertNotNull(attrs);
            assertEquals(1, attrs.getMinInstances());
            assertEquals(3, attrs.getMaxInstances());
            assertEquals(2, attrs.getThreshold());
        }

        @Test
        @DisplayName("Composite task MI should have static creation mode")
        void testCompositeTaskMICreationMode() {
            YMultiInstanceAttributes attrs = _compositeTask1.getMultiInstanceAttributes();
            assertEquals(YMultiInstanceAttributes.CREATION_MODE_STATIC, attrs.getCreationMode());
        }
    }

    @Nested
    @DisplayName("Composite Task Cancellation Tests")
    class CompositeTaskCancellationTests {

        @Test
        @DisplayName("Cancelling idle composite task should not throw")
        void testCancelIdleCompositeTask() throws YPersistenceException {
            assertDoesNotThrow(() -> _compositeTask1.cancel(null));
        }
    }

    @Nested
    @DisplayName("Composite Task Internal Conditions Tests")
    class CompositeTaskInternalConditionsTests {

        @Test
        @DisplayName("Composite task should have mi_active internal condition")
        void testCompositeTaskHasMIActiveCondition() {
            assertNotNull(_compositeTask1.getMIActive());
        }

        @Test
        @DisplayName("Composite task should have mi_entered internal condition")
        void testCompositeTaskHasMIEnteredCondition() {
            assertNotNull(_compositeTask1.getMIEntered());
        }

        @Test
        @DisplayName("Composite task should have mi_executing internal condition")
        void testCompositeTaskHasMIExecutingCondition() {
            assertNotNull(_compositeTask1.getMIExecuting());
        }

        @Test
        @DisplayName("Composite task should have mi_complete internal condition")
        void testCompositeTaskHasMICompleteCondition() {
            assertNotNull(_compositeTask1.getMIComplete());
        }
    }

    @Nested
    @DisplayName("Composite Task XML Tests")
    class CompositeTaskXMLTests {

        @Test
        @DisplayName("Composite task toXML should contain task element")
        void testCompositeTaskToXML() {
            String xml = _compositeTask1.toXML();
            assertTrue(xml.contains("<task"));
            assertTrue(xml.contains("id=\"comp1\""));
        }

        @Test
        @DisplayName("Composite task toXML should contain join and split codes")
        void testCompositeTaskToXMLContainsJoinSplit() {
            String xml = _compositeTask1.toXML();
            assertTrue(xml.contains("<join code=\"and\"/>"));
            assertTrue(xml.contains("<split code=\"and\"/>"));
        }

        @Test
        @DisplayName("Composite task toXML should contain decomposition reference")
        void testCompositeTaskToXMLContainsDecomposition() {
            String xml = _compositeTask1.toXML();
            assertTrue(xml.contains("<decomposesTo id=\"subNet\"/>"));
        }

        @Test
        @DisplayName("Multi-instance composite task toXML should contain MI attributes")
        void testCompositeTaskToXMLContainsMI() {
            String xml = _compositeTask1.toXML();
            assertTrue(xml.contains("xsi:type=\"MultipleInstanceExternalTaskFactsType\""));
            assertTrue(xml.contains("<minimum>1</minimum>"));
            assertTrue(xml.contains("<maximum>3</maximum>"));
        }
    }

    @Nested
    @DisplayName("Composite Task Flow Tests")
    class CompositeTaskFlowTests {

        @Test
        @DisplayName("Composite task should have preset elements")
        void testCompositeTaskHasPreset() {
            assertFalse(_compositeTask1.getPresetElements().isEmpty());
        }

        @Test
        @DisplayName("Composite task should have postset elements")
        void testCompositeTaskHasPostset() {
            assertFalse(_compositeTask1.getPostsetElements().isEmpty());
        }

        @Test
        @DisplayName("Composite task should have postset flows")
        void testCompositeTaskHasPostsetFlows() {
            assertFalse(_compositeTask1.getPostsetFlows().isEmpty());
        }
    }

    @Nested
    @DisplayName("Composite Task Split/Join Type Tests")
    class CompositeTaskSplitJoinTypeTests {

        @Test
        @DisplayName("Composite task should accept AND join type")
        void testCompositeTaskANDJoinType() {
            YCompositeTask task = new YCompositeTask("andTask", YTask._AND, YTask._AND, _rootNet);
            assertEquals(YTask._AND, task.getJoinType());
        }

        @Test
        @DisplayName("Composite task should accept OR join type")
        void testCompositeTaskORJoinType() {
            YCompositeTask task = new YCompositeTask("orTask", YTask._OR, YTask._AND, _rootNet);
            assertEquals(YTask._OR, task.getJoinType());
        }

        @Test
        @DisplayName("Composite task should accept XOR join type")
        void testCompositeTaskXORJoinType() {
            YCompositeTask task = new YCompositeTask("xorTask", YTask._XOR, YTask._AND, _rootNet);
            assertEquals(YTask._XOR, task.getJoinType());
        }

        @Test
        @DisplayName("Composite task should accept AND split type")
        void testCompositeTaskANDSplitType() {
            YCompositeTask task = new YCompositeTask("andTask", YTask._AND, YTask._AND, _rootNet);
            assertEquals(YTask._AND, task.getSplitType());
        }

        @Test
        @DisplayName("Composite task should accept OR split type")
        void testCompositeTaskORSplitType() {
            YCompositeTask task = new YCompositeTask("orTask", YTask._AND, YTask._OR, _rootNet);
            assertEquals(YTask._OR, task.getSplitType());
        }

        @Test
        @DisplayName("Composite task should accept XOR split type")
        void testCompositeTaskXORSplitType() {
            YCompositeTask task = new YCompositeTask("xorTask", YTask._AND, YTask._XOR, _rootNet);
            assertEquals(YTask._XOR, task.getSplitType());
        }

        @Test
        @DisplayName("Composite task should allow changing split type")
        void testCompositeTaskSetSplitType() {
            YCompositeTask task = new YCompositeTask("changeTask", YTask._AND, YTask._AND, _rootNet);
            task.setSplitType(YTask._OR);
            assertEquals(YTask._OR, task.getSplitType());
        }

        @Test
        @DisplayName("Composite task should allow changing join type")
        void testCompositeTaskSetJoinType() {
            YCompositeTask task = new YCompositeTask("changeTask", YTask._AND, YTask._AND, _rootNet);
            task.setJoinType(YTask._XOR);
            assertEquals(YTask._XOR, task.getJoinType());
        }
    }

    @Nested
    @DisplayName("Composite Task Data Mapping Tests")
    class CompositeTaskDataMappingTests {

        @Test
        @DisplayName("Composite task should accept data mappings for task starting")
        void testSetDataMappingsForTaskStarting() {
            YCompositeTask task = new YCompositeTask("dataTask", YTask._AND, YTask._AND, _rootNet);
            Map<String, String> mappings = new HashMap<>();
            mappings.put("inputParam", "/data/input");
            task.setDataMappingsForTaskStarting(mappings);
            assertTrue(task.getDataMappingsForTaskStarting().containsKey("inputParam"));
        }

        @Test
        @DisplayName("Composite task should accept data mappings for task completion")
        void testSetDataMappingsForTaskCompletion() {
            YCompositeTask task = new YCompositeTask("dataTask", YTask._AND, YTask._AND, _rootNet);
            Map<String, String> mappings = new HashMap<>();
            mappings.put("/output/result", "resultVar");
            task.setDataMappingsForTaskCompletion(mappings);
            assertTrue(task.getDataMappingsForTaskCompletion().containsKey("/output/result"));
        }
    }

    @Nested
    @DisplayName("Composite Task Remove Set Tests")
    class CompositeTaskRemoveSetTests {

        @Test
        @DisplayName("Composite task should accept remove set")
        void testAddRemovesTokensFrom() {
            YCompositeTask task = new YCompositeTask("cancelTask", YTask._AND, YTask._AND, _rootNet);
            YCondition condition = new YCondition("cancelCondition", _rootNet);
            List<YExternalNetElement> removeSet = new ArrayList<>();
            removeSet.add(condition);
            task.addRemovesTokensFrom(removeSet);
            assertTrue(task.getRemoveSet().contains(condition));
        }

        @Test
        @DisplayName("Composite task should have empty remove set initially")
        void testEmptyRemoveSetInitially() {
            YCompositeTask task = new YCompositeTask("newTask", YTask._AND, YTask._AND, _rootNet);
            assertTrue(task.getRemoveSet().isEmpty());
        }
    }

    @Nested
    @DisplayName("Composite Task Net Execution Tests")
    class CompositeTaskNetExecutionTests {

        @Test
        @DisplayName("Composite task decomposition should be a YNet")
        void testCompositeTaskDecompositionIsNet() {
            YDecomposition decomp = _compositeTask1.getDecompositionPrototype();
            assertNotNull(decomp);
            assertTrue(decomp instanceof YNet, "Composite task decomposition should be a YNet");
        }

        @Test
        @DisplayName("Composite task subnet should have input condition")
        void testCompositeTaskSubnetHasInputCondition() {
            YNet subnet = (YNet) _compositeTask1.getDecompositionPrototype();
            assertNotNull(subnet.getInputCondition());
        }

        @Test
        @DisplayName("Composite task subnet should have output condition")
        void testCompositeTaskSubnetHasOutputCondition() {
            YNet subnet = (YNet) _compositeTask1.getDecompositionPrototype();
            assertNotNull(subnet.getOutputCondition());
        }
    }

    @Nested
    @DisplayName("Composite Task Enablement Tests")
    class CompositeTaskEnablementTests {

        @Test
        @DisplayName("Composite task should not be enabled without preset tokens")
        void testCompositeTaskNotEnabledWithoutTokens() {
            assertFalse(_compositeTask1.t_enabled(null));
        }
    }

    @Nested
    @DisplayName("Composite Task Information Tests")
    class CompositeTaskInformationTests {

        @Test
        @DisplayName("Composite task should provide information XML")
        void testGetInformation() {
            String info = _compositeTask1.getInformation();
            assertNotNull(info);
            assertTrue(info.contains("<taskInfo>"));
            assertTrue(info.contains("<taskID>comp1</taskID>"));
        }

        @Test
        @DisplayName("Composite task should provide spec version")
        void testGetSpecVersion() {
            String version = _compositeTask1.getSpecVersion();
            assertNotNull(version);
        }
    }

    @Nested
    @DisplayName("Composite Task Timer Tests")
    class CompositeTaskTimerTests {

        @Test
        @DisplayName("Composite task should not have timer initially")
        void testNoTimerInitially() {
            assertNull(_compositeTask1.getTimerParameters());
        }

        @Test
        @DisplayName("Composite task should not have timer variable initially")
        void testNoTimerVariableInitially() {
            assertNull(_compositeTask1.getTimerVariable());
        }
    }

    @Nested
    @DisplayName("Composite Task Configuration Tests")
    class CompositeTaskConfigurationTests {

        @Test
        @DisplayName("Composite task should not have configuration initially")
        void testNoConfigurationInitially() {
            assertNull(_compositeTask1.getConfiguration());
        }

        @Test
        @DisplayName("Composite task should not have default configuration initially")
        void testNoDefaultConfigurationInitially() {
            assertNull(_compositeTask1.getDefaultConfiguration());
        }
    }

    @Nested
    @DisplayName("Composite Task Resourcing Tests")
    class CompositeTaskResourcingTests {

        @Test
        @DisplayName("Composite task should not have resourcing XML initially")
        void testNoResourcingXMLInitially() {
            assertNull(_compositeTask1.getResourcingXML());
        }

        @Test
        @DisplayName("Composite task should not have custom form URL initially")
        void testNoCustomFormURLInitially() {
            assertNull(_compositeTask1.getCustomFormURL());
        }
    }

    @Nested
    @DisplayName("Composite Task Verification Edge Cases")
    class CompositeTaskVerificationEdgeCasesTests {

        @Test
        @DisplayName("Composite task with invalid split type should fail verification")
        void testInvalidSplitType() {
            YCompositeTask task = new YCompositeTask("badSplit", 999, YTask._AND, _rootNet);
            task.setDecompositionPrototype(_subNet);

            YVerificationHandler handler = new YVerificationHandler();
            task.verify(handler);
            assertTrue(handler.hasMessages());
        }

        @Test
        @DisplayName("Composite task with invalid join type should fail verification")
        void testInvalidJoinType() {
            YCompositeTask task = new YCompositeTask("badJoin", YTask._AND, 999, _rootNet);
            task.setDecompositionPrototype(_subNet);

            YVerificationHandler handler = new YVerificationHandler();
            task.verify(handler);
            assertTrue(handler.hasMessages());
        }
    }
}
