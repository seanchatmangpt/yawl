package org.yawlfoundation.yawl.ggen.mining.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.ggen.mining.model.PetriNet;
import org.yawlfoundation.yawl.ggen.mining.model.Transition;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for BPMN 2.0 parser.
 * Validates conversion of BPMN diagrams to unified Petri net model.
 */
public class BpmnParserTest {
    private BpmnParser parser;
    private static final String FIXTURES_PATH = "src/test/resources/fixtures/";

    @BeforeEach
    public void setUp() {
        parser = new BpmnParser();
    }

    @Test
    public void testParseLoanProcessingBpmn() throws Exception {
        File bpmnFile = new File(FIXTURES_PATH + "loan-processing.bpmn");
        assertTrue(bpmnFile.exists(), "Test fixture not found: " + bpmnFile.getAbsolutePath());

        PetriNet net = parser.parse(bpmnFile);

        assertNotNull(net, "PetriNet should not be null");
        assertEquals("LoanProcessingBpmn", net.getId(), "Process ID should match");
        assertEquals("Loan Processing", net.getName(), "Process name should be parsed");
    }

    @Test
    public void testTasksConverted() throws Exception {
        File bpmnFile = new File(FIXTURES_PATH + "loan-processing.bpmn");
        PetriNet net = parser.parse(bpmnFile);

        // BPMN tasks should be converted to transitions
        assertTrue(net.getTransitions().size() >= 5, "Should have tasks");

        // Verify specific tasks
        Transition receiveApp = net.getTransition("Task_ReceiveApp");
        assertNotNull(receiveApp, "Receive Application task should exist");
        assertEquals("Receive Application", receiveApp.getName(), "Task name should match");
    }

    @Test
    public void testStartEventConverted() throws Exception {
        File bpmnFile = new File(FIXTURES_PATH + "loan-processing.bpmn");
        PetriNet net = parser.parse(bpmnFile);

        Transition startEvent = net.getTransition("StartEvent_1");
        assertNotNull(startEvent, "Start event should be converted");
        assertTrue(startEvent.isStartTransition(), "Start event should be identified");
    }

    @Test
    public void testExclusiveGatewayDetected() throws Exception {
        File bpmnFile = new File(FIXTURES_PATH + "loan-processing.bpmn");
        PetriNet net = parser.parse(bpmnFile);

        Transition gateway = net.getTransition("Gateway_RiskSplit");
        assertNotNull(gateway, "Exclusive gateway should exist");
        assertTrue(gateway.isGateway(), "Gateway should be detected as gateway");
        assertTrue(gateway.getBranchCount() >= 2, "Gateway should be identified");
    }

    @Test
    public void testEndEventConverted() throws Exception {
        File bpmnFile = new File(FIXTURES_PATH + "loan-processing.bpmn");
        PetriNet net = parser.parse(bpmnFile);

        Transition endEvent = net.getTransition("EndEvent_1");
        assertNotNull(endEvent, "End event should be converted");
        assertTrue(endEvent.isEndTransition(), "End event should be identified");
    }

    @Test
    public void testSequenceFlowsConverted() throws Exception {
        File bpmnFile = new File(FIXTURES_PATH + "loan-processing.bpmn");
        PetriNet net = parser.parse(bpmnFile);

        // Should have multiple arcs connecting tasks
        assertTrue(net.getArcs().size() >= 8, "Should have sequence flows");
    }

    @Test
    public void testNetIsValid() throws Exception {
        File bpmnFile = new File(FIXTURES_PATH + "loan-processing.bpmn");
        PetriNet net = parser.parse(bpmnFile);

        assertTrue(net.isValid(), "Converted net should be valid");
    }

    @Test
    public void testParallelGatewayDetected() throws Exception {
        File bpmnFile = new File(FIXTURES_PATH + "loan-processing.bpmn");
        PetriNet net = parser.parse(bpmnFile);

        Transition parallelGateway = net.getTransition("Gateway_Merge");
        assertNotNull(parallelGateway, "Parallel gateway should exist");
        // Should have multiple incoming flows
        assertTrue(parallelGateway.getIncomingArcs().size() >= 1, "Gateway should have incoming flows");
    }
}
