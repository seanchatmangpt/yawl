package org.yawlfoundation.yawl.ggen.mining.parser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.ggen.mining.model.PetriNet;
import org.yawlfoundation.yawl.ggen.mining.model.Place;
import org.yawlfoundation.yawl.ggen.mining.model.Transition;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cases for PNML parser.
 * Validates parsing of PNML XML into Petri net models.
 */
public class PnmlParserTest {
    private PnmlParser parser;
    private static final String FIXTURES_PATH = "src/test/resources/fixtures/";

    @BeforeEach
    public void setUp() {
        parser = new PnmlParser();
    }

    @Test
    public void testParseLoanProcessingPnml() throws Exception {
        File pnmlFile = new File(FIXTURES_PATH + "loan-processing.pnml");
        assertTrue(pnmlFile.exists(), "Test fixture not found: " + pnmlFile.getAbsolutePath());

        PetriNet net = parser.parse(pnmlFile);

        assertNotNull(net, "PetriNet should not be null");
        assertEquals("LoanProcessing", net.getId(), "Net ID should match");
        assertEquals("Loan Processing Workflow", net.getName(), "Net name should be parsed");
    }

    @Test
    public void testPlacesParsed() throws Exception {
        File pnmlFile = new File(FIXTURES_PATH + "loan-processing.pnml");
        PetriNet net = parser.parse(pnmlFile);

        assertEquals(7, net.getPlaces().size(), "Should have 7 places");

        Place startPlace = net.getPlace("p1");
        assertNotNull(startPlace, "Start place should exist");
        assertEquals("Start", startPlace.getName(), "Start place name should match");
        assertEquals(1, startPlace.getInitialMarking(), "Start place should have initial marking of 1");
        assertTrue(startPlace.isInitialPlace(), "p1 should be initial place");
    }

    @Test
    public void testTransitionsParsed() throws Exception {
        File pnmlFile = new File(FIXTURES_PATH + "loan-processing.pnml");
        PetriNet net = parser.parse(pnmlFile);

        assertEquals(7, net.getTransitions().size(), "Should have 7 transitions");

        Transition receiveApp = net.getTransition("t1");
        assertNotNull(receiveApp, "Receive Application transition should exist");
        assertEquals("Receive Application", receiveApp.getName(), "Transition name should match");
        assertTrue(receiveApp.isStartTransition(), "t1 should be start transition");
    }

    @Test
    public void testArcsParsed() throws Exception {
        File pnmlFile = new File(FIXTURES_PATH + "loan-processing.pnml");
        PetriNet net = parser.parse(pnmlFile);

        assertEquals(14, net.getArcs().size(), "Should have 14 arcs");
    }

    @Test
    public void testGatewayDetection() throws Exception {
        File pnmlFile = new File(FIXTURES_PATH + "loan-processing.pnml");
        PetriNet net = parser.parse(pnmlFile);

        Transition riskSplit = net.getTransition("t3");
        assertNotNull(riskSplit, "Risk split gateway should exist");
        assertTrue(riskSplit.isGateway(), "t3 should be detected as gateway");
        assertEquals(2, riskSplit.getBranchCount(), "Risk split should have 2 branches");
    }

    @Test
    public void testNetValidation() throws Exception {
        File pnmlFile = new File(FIXTURES_PATH + "loan-processing.pnml");
        PetriNet net = parser.parse(pnmlFile);

        assertTrue(net.isValid(), "Net should be valid");
    }

    @Test
    public void testFinalPlaceDetection() throws Exception {
        File pnmlFile = new File(FIXTURES_PATH + "loan-processing.pnml");
        PetriNet net = parser.parse(pnmlFile);

        Place endPlace = net.getPlace("p6");
        assertNotNull(endPlace, "End place should exist");
        assertTrue(endPlace.isFinalPlace(), "p6 should be final place");
    }

    @Test
    public void testStartTransitionsDetection() throws Exception {
        File pnmlFile = new File(FIXTURES_PATH + "loan-processing.pnml");
        PetriNet net = parser.parse(pnmlFile);

        assertEquals(1, net.getStartTransitions().size(), "Should have 1 start transition");
        assertTrue(
            net.getStartTransitions().stream().anyMatch(t -> t.getId().equals("t1")),
            "t1 should be start transition");
    }

    @Test
    public void testEndTransitionsDetection() throws Exception {
        File pnmlFile = new File(FIXTURES_PATH + "loan-processing.pnml");
        PetriNet net = parser.parse(pnmlFile);

        assertEquals(1, net.getEndTransitions().size(), "Should have 1 end transition");
        assertTrue(
            net.getEndTransitions().stream().anyMatch(t -> t.getId().equals("t6")),
            "t6 should be end transition");
    }
}
