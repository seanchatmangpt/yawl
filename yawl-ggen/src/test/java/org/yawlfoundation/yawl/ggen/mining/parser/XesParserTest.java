package org.yawlfoundation.yawl.ggen.mining.parser;

import org.yawlfoundation.yawl.ggen.mining.model.PetriNet;
import org.yawlfoundation.yawl.ggen.mining.model.XesEvent;
import org.yawlfoundation.yawl.ggen.mining.model.XesLog;
import org.yawlfoundation.yawl.ggen.mining.model.XesTrace;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Set;

import static org.junit.Assert.*;

public class XesParserTest {

    private static File getTestResource(String filename) {
        URL url = XesParserTest.class.getClassLoader().getResource("fixtures/" + filename);
        assertNotNull("Test resource not found: " + filename, url);
        return new File(url.getFile());
    }

    @Test
    public void testParseXesFile() throws Exception {
        XesParser parser = new XesParser();
        File xesFile = getTestResource("loan-process.xes");

        XesLog log = parser.parse(xesFile);

        assertNotNull("XesLog should not be null", log);
        assertFalse("Log should have traces", log.getTraces().isEmpty());
    }

    @Test
    public void testTraceCount() throws Exception {
        XesParser parser = new XesParser();
        File xesFile = getTestResource("loan-process.xes");

        XesLog log = parser.parse(xesFile);

        assertEquals("Log should have 2 traces", 2, log.getTraces().size());
    }

    @Test
    public void testEventCount() throws Exception {
        XesParser parser = new XesParser();
        File xesFile = getTestResource("loan-process.xes");

        XesLog log = parser.parse(xesFile);

        int totalEvents = 0;
        for (XesTrace trace : log.getTraces()) {
            totalEvents += trace.getEvents().size();
        }

        assertEquals("Log should have 8 total events", 8, totalEvents);
    }

    @Test
    public void testEventCountPerTrace() throws Exception {
        XesParser parser = new XesParser();
        File xesFile = getTestResource("loan-process.xes");

        XesLog log = parser.parse(xesFile);

        for (XesTrace trace : log.getTraces()) {
            assertEquals("Each trace should have 4 events", 4, trace.getEvents().size());
        }
    }

    @Test
    public void testActivityNamesParsed() throws Exception {
        XesParser parser = new XesParser();
        File xesFile = getTestResource("loan-process.xes");

        XesLog log = parser.parse(xesFile);
        Set<String> activities = log.getActivities();

        assertTrue("Should contain 'Receive Application'", activities.contains("Receive Application"));
        assertTrue("Should contain 'Assess Risk'", activities.contains("Assess Risk"));
        assertTrue("Should contain 'Approve Application'", activities.contains("Approve Application"));
        assertTrue("Should contain 'Reject Application'", activities.contains("Reject Application"));
        assertTrue("Should contain 'Send Notification'", activities.contains("Send Notification"));
    }

    @Test
    public void testActivityCount() throws Exception {
        XesParser parser = new XesParser();
        File xesFile = getTestResource("loan-process.xes");

        XesLog log = parser.parse(xesFile);
        Set<String> activities = log.getActivities();

        assertEquals("Should have 5 distinct activities", 5, activities.size());
    }

    @Test
    public void testCaseIdsParsed() throws Exception {
        XesParser parser = new XesParser();
        File xesFile = getTestResource("loan-process.xes");

        XesLog log = parser.parse(xesFile);

        Set<String> caseIds = new java.util.HashSet<>();
        for (XesTrace trace : log.getTraces()) {
            caseIds.add(trace.getCaseId());
        }

        assertTrue("Should contain Case1", caseIds.contains("Case1"));
        assertTrue("Should contain Case2", caseIds.contains("Case2"));
        assertEquals("Should have 2 case IDs", 2, caseIds.size());
    }

    @Test
    public void testTimestampsParsed() throws Exception {
        XesParser parser = new XesParser();
        File xesFile = getTestResource("loan-process.xes");

        XesLog log = parser.parse(xesFile);

        for (XesTrace trace : log.getTraces()) {
            for (XesEvent event : trace.getEvents()) {
                assertNotNull("Event should have timestamp", event.getTimestamp());
                assertTrue("Timestamp should be non-empty", !event.getTimestamp().isEmpty());
                assertTrue("Timestamp should contain T (ISO format)", event.getTimestamp().contains("T"));
            }
        }
    }

    @Test
    public void testLifecycleTransitionsParsed() throws Exception {
        XesParser parser = new XesParser();
        File xesFile = getTestResource("loan-process.xes");

        XesLog log = parser.parse(xesFile);

        for (XesTrace trace : log.getTraces()) {
            for (XesEvent event : trace.getEvents()) {
                assertEquals("All events should have 'complete' lifecycle", "complete", event.getLifecycle());
            }
        }
    }

    @Test
    public void testEventOrderPreserved() throws Exception {
        XesParser parser = new XesParser();
        File xesFile = getTestResource("loan-process.xes");

        XesLog log = parser.parse(xesFile);

        XesTrace firstTrace = log.getTraces().get(0);
        assertEquals("First event should be Receive Application",
            "Receive Application", firstTrace.getEvents().get(0).getActivityName());
        assertEquals("Second event should be Assess Risk",
            "Assess Risk", firstTrace.getEvents().get(1).getActivityName());
        assertEquals("Fourth event should be Send Notification",
            "Send Notification", firstTrace.getEvents().get(3).getActivityName());
    }

    @Test
    public void testTraceDurationCalculation() throws Exception {
        XesParser parser = new XesParser();
        File xesFile = getTestResource("loan-process.xes");

        XesLog log = parser.parse(xesFile);

        for (XesTrace trace : log.getTraces()) {
            long duration = trace.getDuration();
            // Trace spans from 10:00 to 13:00 = 3 hours = 10800000 milliseconds
            assertEquals("Trace should have 3-hour duration", 10800000, duration);
        }
    }

    @Test
    public void testDiscoverPetriNet() throws Exception {
        XesParser parser = new XesParser();
        File xesFile = getTestResource("loan-process.xes");

        XesLog log = parser.parse(xesFile);
        PetriNet net = parser.discoverPetriNet(log);

        assertNotNull("Discovered PetriNet should not be null", net);
        assertTrue("Petri net should be valid", net.isValid());
    }

    @Test
    public void testDiscoveredNetHasTransitions() throws Exception {
        XesParser parser = new XesParser();
        File xesFile = getTestResource("loan-process.xes");

        XesLog log = parser.parse(xesFile);
        PetriNet net = parser.discoverPetriNet(log);

        assertFalse("Net should have transitions", net.getTransitions().isEmpty());
        assertTrue("Net should have at least 5 transitions (one per activity)",
            net.getTransitions().size() >= 5);
    }

    @Test
    public void testDiscoveredNetHasPlaces() throws Exception {
        XesParser parser = new XesParser();
        File xesFile = getTestResource("loan-process.xes");

        XesLog log = parser.parse(xesFile);
        PetriNet net = parser.discoverPetriNet(log);

        assertFalse("Net should have places", net.getPlaces().isEmpty());
        assertTrue("Net should have at least 2 places (start and end)",
            net.getPlaces().size() >= 2);
    }

    @Test
    public void testDiscoveredNetHasArcs() throws Exception {
        XesParser parser = new XesParser();
        File xesFile = getTestResource("loan-process.xes");

        XesLog log = parser.parse(xesFile);
        PetriNet net = parser.discoverPetriNet(log);

        assertFalse("Net should have arcs", net.getArcs().isEmpty());
    }

    @Test
    public void testDiscoverPetriNetWithEmptyLog() throws Exception {
        XesParser parser = new XesParser();
        XesLog log = new XesLog("empty");

        PetriNet net = parser.discoverPetriNet(log);

        assertNotNull("Net should not be null even for empty log", net);
    }

    @Test
    public void testDiscoverPetriNetWithNullLog() throws Exception {
        XesParser parser = new XesParser();

        PetriNet net = parser.discoverPetriNet(null);

        assertNotNull("Net should not be null for null log", net);
    }

    @Test
    public void testEventAttributes() throws Exception {
        XesParser parser = new XesParser();
        File xesFile = getTestResource("loan-process.xes");

        XesLog log = parser.parse(xesFile);

        // Check that first event has expected attributes
        XesTrace firstTrace = log.getTraces().get(0);
        XesEvent firstEvent = firstTrace.getEvents().get(0);

        assertEquals("Event should have activity name", "Receive Application", firstEvent.getActivityName());
        assertEquals("Event should have lifecycle", "complete", firstEvent.getLifecycle());
        assertNotNull("Event should have timestamp", firstEvent.getTimestamp());
    }

    @Test
    public void testXesLogGetEventCount() throws Exception {
        XesParser parser = new XesParser();
        File xesFile = getTestResource("loan-process.xes");

        XesLog log = parser.parse(xesFile);

        assertEquals("Log should report 8 total events", 8, log.getEventCount());
    }

    @Test
    public void testXesLogToString() throws Exception {
        XesParser parser = new XesParser();
        File xesFile = getTestResource("loan-process.xes");

        XesLog log = parser.parse(xesFile);
        String str = log.toString();

        assertNotNull("toString should not be null", str);
        assertTrue("toString should contain trace count", str.contains("traces=2"));
        assertTrue("toString should contain event count", str.contains("events=8"));
    }

    @Test
    public void testXesTraceToString() throws Exception {
        XesParser parser = new XesParser();
        File xesFile = getTestResource("loan-process.xes");

        XesLog log = parser.parse(xesFile);
        XesTrace trace = log.getTraces().get(0);
        String str = trace.toString();

        assertNotNull("toString should not be null", str);
        assertTrue("toString should contain case ID", str.contains("Case1"));
        assertTrue("toString should contain event count", str.contains("events=4"));
    }

    @Test
    public void testXesEventToString() throws Exception {
        XesParser parser = new XesParser();
        File xesFile = getTestResource("loan-process.xes");

        XesLog log = parser.parse(xesFile);
        XesTrace trace = log.getTraces().get(0);
        XesEvent event = trace.getEvents().get(0);
        String str = event.toString();

        assertNotNull("toString should not be null", str);
        assertTrue("toString should contain activity", str.contains("Receive Application"));
    }

    @Test
    public void testParseFromInputStream() throws Exception {
        XesParser parser = new XesParser();
        File xesFile = getTestResource("loan-process.xes");

        XesLog log = parser.parse(new java.io.FileInputStream(xesFile));

        assertNotNull("XesLog should not be null", log);
        assertEquals("Log should have 2 traces", 2, log.getTraces().size());
    }
}
