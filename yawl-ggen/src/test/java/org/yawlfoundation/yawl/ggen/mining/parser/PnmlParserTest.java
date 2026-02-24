package org.yawlfoundation.yawl.ggen.mining.parser;

import org.junit.Before;
import org.junit.Test;
import org.yawlfoundation.yawl.ggen.mining.model.PetriNet;
import org.yawlfoundation.yawl.ggen.mining.model.Place;
import org.yawlfoundation.yawl.ggen.mining.model.Transition;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

/**
 * Test cases for PNML parser.
 * Validates parsing of PNML XML into Petri net models.
 */
public class PnmlParserTest {
    private PnmlParser parser;
    private static final String FIXTURES_PATH = "src/test/resources/fixtures/";

    @Before
    public void setUp() {
        parser = new PnmlParser();
    }

    @Test
    public void testParseLoanProcessingPnml() throws Exception {
        File pnmlFile = new File(FIXTURES_PATH + "loan-processing.pnml");
        assertTrue("Test fixture not found: " + pnmlFile.getAbsolutePath(), pnmlFile.exists());

        PetriNet net = parser.parse(pnmlFile);

        assertNotNull("PetriNet should not be null", net);
        assertEquals("Net ID should match", "LoanProcessing", net.getId());
        assertEquals("Net name should be parsed", "Loan Processing Workflow", net.getName());
    }

    @Test
    public void testPlacesParsed() throws Exception {
        File pnmlFile = new File(FIXTURES_PATH + "loan-processing.pnml");
        PetriNet net = parser.parse(pnmlFile);

        assertEquals("Should have 7 places", 7, net.getPlaces().size());

        Place startPlace = net.getPlace("p1");
        assertNotNull("Start place should exist", startPlace);
        assertEquals("Start place name should match", "Start", startPlace.getName());
        assertEquals("Start place should have initial marking of 1", 1, startPlace.getInitialMarking());
        assertTrue("p1 should be initial place", startPlace.isInitialPlace());
    }

    @Test
    public void testTransitionsParsed() throws Exception {
        File pnmlFile = new File(FIXTURES_PATH + "loan-processing.pnml");
        PetriNet net = parser.parse(pnmlFile);

        assertEquals("Should have 7 transitions", 7, net.getTransitions().size());

        Transition receiveApp = net.getTransition("t1");
        assertNotNull("Receive Application transition should exist", receiveApp);
        assertEquals("Transition name should match", "Receive Application", receiveApp.getName());
        assertTrue("t1 should be start transition", receiveApp.isStartTransition());
    }

    @Test
    public void testArcsParsed() throws Exception {
        File pnmlFile = new File(FIXTURES_PATH + "loan-processing.pnml");
        PetriNet net = parser.parse(pnmlFile);

        assertEquals("Should have 14 arcs", 14, net.getArcs().size());
    }

    @Test
    public void testGatewayDetection() throws Exception {
        File pnmlFile = new File(FIXTURES_PATH + "loan-processing.pnml");
        PetriNet net = parser.parse(pnmlFile);

        Transition riskSplit = net.getTransition("t3");
        assertNotNull("Risk split gateway should exist", riskSplit);
        assertTrue("t3 should be detected as gateway", riskSplit.isGateway());
        assertEquals("Risk split should have 2 branches", 2, riskSplit.getBranchCount());
    }

    @Test
    public void testNetValidation() throws Exception {
        File pnmlFile = new File(FIXTURES_PATH + "loan-processing.pnml");
        PetriNet net = parser.parse(pnmlFile);

        assertTrue("Net should be valid", net.isValid());
    }

    @Test
    public void testFinalPlaceDetection() throws Exception {
        File pnmlFile = new File(FIXTURES_PATH + "loan-processing.pnml");
        PetriNet net = parser.parse(pnmlFile);

        Place endPlace = net.getPlace("p6");
        assertNotNull("End place should exist", endPlace);
        assertTrue("p6 should be final place", endPlace.isFinalPlace());
    }

    @Test
    public void testStartTransitionsDetection() throws Exception {
        File pnmlFile = new File(FIXTURES_PATH + "loan-processing.pnml");
        PetriNet net = parser.parse(pnmlFile);

        assertEquals("Should have 1 start transition", 1, net.getStartTransitions().size());
        assertTrue("t1 should be start transition",
            net.getStartTransitions().stream()
                .anyMatch(t -> t.getId().equals("t1")));
    }

    @Test
    public void testEndTransitionsDetection() throws Exception {
        File pnmlFile = new File(FIXTURES_PATH + "loan-processing.pnml");
        PetriNet net = parser.parse(pnmlFile);

        assertEquals("Should have 1 end transition", 1, net.getEndTransitions().size());
        assertTrue("t6 should be end transition",
            net.getEndTransitions().stream()
                .anyMatch(t -> t.getId().equals("t6")));
    }
}
