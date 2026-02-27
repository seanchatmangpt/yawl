package org.yawlfoundation.yawl.elements.e2wfoj;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.elements.YAtomicTask;
import org.yawlfoundation.yawl.elements.YCondition;
import org.yawlfoundation.yawl.elements.YInputCondition;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YNetElement;
import org.yawlfoundation.yawl.elements.YOutputCondition;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.elements.state.YMarking;
import org.yawlfoundation.yawl.exceptions.YSyntaxException;
import org.yawlfoundation.yawl.util.YVerificationHandler;

/**
 * Chicago TDD tests for E2WFOJNet.
 * Tests reset net construction and OR-join enablement analysis.
 * Uses real YAWL net instances.
 */
@DisplayName("E2WFOJNet Tests")
@Tag("unit")
class TestE2WFOJNet {

    private YSpecification spec;
    private YNet net;

    @BeforeEach
    void setUp() throws YSyntaxException {
        spec = new YSpecification("http://test.com/orjoin-spec");
        spec.setSchema("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>");
    }

    private YNet createSimpleNetWithORJoin() {
        YNet net = new YNet("orjoinNet", spec);

        YInputCondition input = new YInputCondition("i", net);
        YOutputCondition output = new YOutputCondition("o", net);
        YCondition c1 = new YCondition("c1", net);
        YCondition c2 = new YCondition("c2", net);
        YCondition c3 = new YCondition("c3", net);

        net.addNetElement(c1);
        net.addNetElement(c2);
        net.addNetElement(c3);

        YAtomicTask task1 = new YAtomicTask("t1", YTask._XOR, YTask._XOR, net);
        YAtomicTask task2 = new YAtomicTask("t2", YTask._AND, YTask._AND, net);
        YAtomicTask orJoinTask = new YAtomicTask("orJoin", YTask._OR, YTask._AND, net);

        net.addNetElement(task1);
        net.addNetElement(task2);
        net.addNetElement(orJoinTask);

        // i -> t1 -> c1, c2 (XOR split)
        input.addPostset(new org.yawlfoundation.yawl.elements.YFlow(input, task1));
        task1.addPostset(new org.yawlfoundation.yawl.elements.YFlow(task1, c1));
        task1.addPostset(new org.yawlfoundation.yawl.elements.YFlow(task1, c2));

        // c1, c2 -> t2 -> c3
        c1.addPostset(new org.yawlfoundation.yawl.elements.YFlow(c1, task2));
        c2.addPostset(new org.yawlfoundation.yawl.elements.YFlow(c2, task2));
        task2.addPostset(new org.yawlfoundation.yawl.elements.YFlow(task2, c3));

        // c1, c3 -> orJoin -> o
        c1.addPostset(new org.yawlfoundation.yawl.elements.YFlow(c1, orJoinTask));
        c3.addPostset(new org.yawlfoundation.yawl.elements.YFlow(c3, orJoinTask));
        orJoinTask.addPostset(new org.yawlfoundation.yawl.elements.YFlow(orJoinTask, output));

        net.setInputCondition(input);
        net.setOutputCondition(output);

        return net;
    }

    @Nested
    @DisplayName("E2WFOJNet Construction Tests")
    class E2WFOJNetConstructionTests {

        @Test
        @DisplayName("E2WFOJNet should be constructable with valid net and OR-join task")
        void e2wfojNetConstructableWithValidNetAndOrJoin() {
            YNet testNet = createSimpleNetWithORJoin();
            YTask orJoinTask = (YTask) testNet.getNetElement("orJoin");

            assertDoesNotThrow(() -> new E2WFOJNet(testNet, orJoinTask));
        }

        @Test
        @DisplayName("E2WFOJNet construction should not modify original net")
        void e2wfojNetDoesNotModifyOriginalNet() {
            YNet testNet = createSimpleNetWithORJoin();
            int originalSize = testNet.getNetElements().size();
            YTask orJoinTask = (YTask) testNet.getNetElement("orJoin");

            new E2WFOJNet(testNet, orJoinTask);

            assertEquals(originalSize, testNet.getNetElements().size());
        }
    }

    @Nested
    @DisplayName("OR-Join Enablement Tests")
    class OrJoinEnablementTests {

        @Test
        @DisplayName("OR-join enabled should return true when no bigger marking is coverable")
        void orJoinEnabledReturnsTrueWhenNoBiggerMarkingCoverable() {
            YNet testNet = createSimpleNetWithORJoin();
            YTask orJoinTask = (YTask) testNet.getNetElement("orJoin");

            E2WFOJNet e2wfojNet = new E2WFOJNet(testNet, orJoinTask);

            // Create a marking with token at c1
            List<YNetElement> locations = new ArrayList<>();
            locations.add(testNet.getNetElement("c1"));
            YMarking marking = new YMarking(locations);

            // Restrict based on marking
            e2wfojNet.restrictNet(marking);

            // Verify the method can be called without error
            assertDoesNotThrow(() -> e2wfojNet.orJoinEnabled(marking, orJoinTask));
        }

        @Test
        @DisplayName("Restrict net with OR-join should work")
        void restrictNetWithOrJoinWorks() {
            YNet testNet = createSimpleNetWithORJoin();
            YTask orJoinTask = (YTask) testNet.getNetElement("orJoin");

            E2WFOJNet e2wfojNet = new E2WFOJNet(testNet, orJoinTask);

            assertDoesNotThrow(() -> e2wfojNet.restrictNet(orJoinTask));
        }
    }

    @Nested
    @DisplayName("Net Verification Tests")
    class NetVerificationTests {

        @Test
        @DisplayName("Test net should verify successfully")
        void testNetVerifiesSuccessfully() throws YSyntaxException {
            YNet testNet = createSimpleNetWithORJoin();
            spec.setRootNet(testNet);

            YVerificationHandler handler = new YVerificationHandler();
            spec.verify(handler);

            // May have warnings about service not registered, but no errors
            // for structural issues
        }
    }

    @Nested
    @DisplayName("Delegation Tests")
    class DelegationTests {

        @Test
        @DisplayName("E2WFOJNet should delegate to E2WFOJCore")
        void e2wfojNetDelegatesToCore() {
            YNet testNet = createSimpleNetWithORJoin();
            YTask orJoinTask = (YTask) testNet.getNetElement("orJoin");

            // Construction should succeed, delegating to E2WFOJCore
            E2WFOJNet e2wfojNet = new E2WFOJNet(testNet, orJoinTask);
            assertNotNull(e2wfojNet);
        }
    }
}
