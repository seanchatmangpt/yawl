package org.yawlfoundation.yawl.ggen.mining.parser;

import org.junit.Before;
import org.junit.Test;
import org.yawlfoundation.yawl.ggen.mining.model.PetriNet;
import org.yawlfoundation.yawl.ggen.mining.model.Transition;

import java.io.File;

import static org.junit.Assert.*;

/**
 * Test cases for BPMN 2.0 parser.
 * Validates conversion of BPMN diagrams to unified Petri net model.
 */
public class BpmnParserTest {
    private BpmnParser parser;
    private static final String FIXTURES_PATH = "yawl-ggen/src/test/resources/fixtures/";

    @Before
    public void setUp() {
        parser = new BpmnParser();
    }

    @Test
    public void testParseLoanProcessingBpmn() throws Exception {
        File bpmnFile = new File(FIXTURES_PATH + "loan-processing.bpmn");
        assertTrue("Test fixture not found: " + bpmnFile.getAbsolutePath(), bpmnFile.exists());

        PetriNet net = parser.parse(bpmnFile);

        assertNotNull("PetriNet should not be null", net);
        assertEquals("Process ID should match", "LoanProcessingBpmn", net.getId());
        assertEquals("Process name should be parsed", "Loan Processing", net.getName());
    }

    @Test
    public void testTasksConverted() throws Exception {
        File bpmnFile = new File(FIXTURES_PATH + "loan-processing.bpmn");
        PetriNet net = parser.parse(bpmnFile);

        // BPMN tasks should be converted to transitions
        assertTrue("Should have tasks", net.getTransitions().size() >= 5);

        // Verify specific tasks
        Transition receiveApp = net.getTransition("Task_ReceiveApp");
        assertNotNull("Receive Application task should exist", receiveApp);
        assertEquals("Task name should match", "Receive Application", receiveApp.getName());
    }

    @Test
    public void testStartEventConverted() throws Exception {
        File bpmnFile = new File(FIXTURES_PATH + "loan-processing.bpmn");
        PetriNet net = parser.parse(bpmnFile);

        Transition startEvent = net.getTransition("StartEvent_1");
        assertNotNull("Start event should be converted", startEvent);
        assertTrue("Start event should be identified", startEvent.isStartTransition());
    }

    @Test
    public void testExclusiveGatewayDetected() throws Exception {
        File bpmnFile = new File(FIXTURES_PATH + "loan-processing.bpmn");
        PetriNet net = parser.parse(bpmnFile);

        Transition gateway = net.getTransition("Gateway_RiskSplit");
        assertNotNull("Exclusive gateway should exist", gateway);
        assertTrue("Gateway should be detected as gateway", gateway.isGateway());
        assertTrue("Gateway should be identified", gateway.getBranchCount() >= 2);
    }

    @Test
    public void testEndEventConverted() throws Exception {
        File bpmnFile = new File(FIXTURES_PATH + "loan-processing.bpmn");
        PetriNet net = parser.parse(bpmnFile);

        Transition endEvent = net.getTransition("EndEvent_1");
        assertNotNull("End event should be converted", endEvent);
        assertTrue("End event should be identified", endEvent.isEndTransition());
    }

    @Test
    public void testSequenceFlowsConverted() throws Exception {
        File bpmnFile = new File(FIXTURES_PATH + "loan-processing.bpmn");
        PetriNet net = parser.parse(bpmnFile);

        // Should have multiple arcs connecting tasks
        assertTrue("Should have sequence flows", net.getArcs().size() >= 8);
    }

    @Test
    public void testNetIsValid() throws Exception {
        File bpmnFile = new File(FIXTURES_PATH + "loan-processing.bpmn");
        PetriNet net = parser.parse(bpmnFile);

        assertTrue("Converted net should be valid", net.isValid());
    }

    @Test
    public void testParallelGatewayDetected() throws Exception {
        File bpmnFile = new File(FIXTURES_PATH + "loan-processing.bpmn");
        PetriNet net = parser.parse(bpmnFile);

        Transition parallelGateway = net.getTransition("Gateway_Merge");
        assertNotNull("Parallel gateway should exist", parallelGateway);
        // Should have multiple incoming flows
        assertTrue("Gateway should have incoming flows", parallelGateway.getIncomingArcs().size() >= 1);
    }
}
