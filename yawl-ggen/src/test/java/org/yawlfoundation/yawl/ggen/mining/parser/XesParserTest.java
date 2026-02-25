package org.yawlfoundation.yawl.ggen.mining.parser;

import org.yawlfoundation.yawl.ggen.mining.model.PetriNet;
import org.yawlfoundation.yawl.ggen.mining.model.XesEvent;
import org.yawlfoundation.yawl.ggen.mining.model.XesLog;
import org.yawlfoundation.yawl.ggen.mining.model.XesTrace;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class XesParserTest {

    private static File getTestResource(String filename) {
        URL url = XesParserTest.class.getClassLoader().getResource("fixtures/" + filename);
        assertNotNull(url, "Test resource not found: " + filename);
        return new File(url.getFile());
    }

    @Test
    public void testParseXesFile() throws Exception {
        XesParser parser = new XesParser();
        File xesFile = getTestResource("loan-process.xes");

        XesLog log = parser.parse(xesFile);

        assertNotNull(log, "XesLog should not be null");
        assertFalse(log.getTraces().isEmpty(), "Log should have traces");
    }

    @Test
    public void testTraceCount() throws Exception {
        XesParser parser = new XesParser();
        File xesFile = getTestResource("loan-process.xes");

        XesLog log = parser.parse(xesFile);

        assertEquals(2, log.getTraces().size(), "Log should have 2 traces");
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

        assertEquals(8, totalEvents, "Log should have 8 total events");
    }

    @Test
    public void testEventCountPerTrace() throws Exception {
        XesParser parser = new XesParser();
        File xesFile = getTestResource("loan-process.xes");

        XesLog log = parser.parse(xesFile);

        for (XesTrace trace : log.getTraces()) {
            assertEquals(4, trace.getEvents().size(), "Each trace should have 4 events");
        }
    }

    @Test
    public void testActivityNamesParsed() throws Exception {
        XesParser parser = new XesParser();
        File xesFile = getTestResource("loan-process.xes");

        XesLog log = parser.parse(xesFile);
        Set<String> activities = log.getActivities();

        assertTrue(activities.contains("Receive Application"), "Should contain 'Receive Application'");
        assertTrue(activities.contains("Assess Risk"), "Should contain 'Assess Risk'");
        assertTrue(activities.contains("Approve Application"), "Should contain 'Approve Application'");
        assertTrue(activities.contains("Reject Application"), "Should contain 'Reject Application'");
        assertTrue(activities.contains("Send Notification"), "Should contain 'Send Notification'");
    }

    @Test
    public void testActivityCount() throws Exception {
        XesParser parser = new XesParser();
        File xesFile = getTestResource("loan-process.xes");

        XesLog log = parser.parse(xesFile);
        Set<String> activities = log.getActivities();

        assertEquals(5, activities.size(), "Should have 5 distinct activities");
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

        assertTrue(caseIds.contains("Case1"), "Should contain Case1");
        assertTrue(caseIds.contains("Case2"), "Should contain Case2");
        assertEquals(2, caseIds.size(), "Should have 2 case IDs");
    }

    @Test
    public void testTimestampsParsed() throws Exception {
        XesParser parser = new XesParser();
        File xesFile = getTestResource("loan-process.xes");

        XesLog log = parser.parse(xesFile);

        for (XesTrace trace : log.getTraces()) {
            for (XesEvent event : trace.getEvents()) {
                assertNotNull(event.getTimestamp(), "Event should have timestamp");
                assertTrue(!event.getTimestamp().isEmpty(), "Timestamp should be non-empty");
                assertTrue(event.getTimestamp().contains("T"), "Timestamp should contain T (ISO format)");
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
                assertEquals("complete", event.getLifecycle(), "All events should have 'complete' lifecycle");
            }
        }
    }

    @Test
    public void testEventOrderPreserved() throws Exception {
        XesParser parser = new XesParser();
        File xesFile = getTestResource("loan-process.xes");

        XesLog log = parser.parse(xesFile);

        XesTrace firstTrace = log.getTraces().get(0);
        assertEquals("Receive Application", firstTrace.getEvents().get(0).getActivityName(),
            "First event should be Receive Application");
        assertEquals("Assess Risk", firstTrace.getEvents().get(1).getActivityName(),
            "Second event should be Assess Risk");
        assertEquals("Send Notification", firstTrace.getEvents().get(3).getActivityName(),
            "Fourth event should be Send Notification");
    }

    @Test
    public void testTraceDurationCalculation() throws Exception {
        XesParser parser = new XesParser();
        File xesFile = getTestResource("loan-process.xes");

        XesLog log = parser.parse(xesFile);

        for (XesTrace trace : log.getTraces()) {
            long duration = trace.getDuration();
            // Trace spans from 10:00 to 13:00 = 3 hours = 10800000 milliseconds
            assertEquals(10800000, duration, "Trace should have 3-hour duration");
        }
    }

    @Test
    public void testDiscoverPetriNet() throws Exception {
        XesParser parser = new XesParser();
        File xesFile = getTestResource("loan-process.xes");

        XesLog log = parser.parse(xesFile);
        PetriNet net = parser.discoverPetriNet(log);

        assertNotNull(net, "Discovered PetriNet should not be null");
        assertTrue(net.isValid(), "Petri net should be valid");
    }

    @Test
    public void testDiscoveredNetHasTransitions() throws Exception {
        XesParser parser = new XesParser();
        File xesFile = getTestResource("loan-process.xes");

        XesLog log = parser.parse(xesFile);
        PetriNet net = parser.discoverPetriNet(log);

        assertFalse(net.getTransitions().isEmpty(), "Net should have transitions");
        assertTrue(net.getTransitions().size() >= 5, "Net should have at least 5 transitions (one per activity)");
    }

    @Test
    public void testDiscoveredNetHasPlaces() throws Exception {
        XesParser parser = new XesParser();
        File xesFile = getTestResource("loan-process.xes");

        XesLog log = parser.parse(xesFile);
        PetriNet net = parser.discoverPetriNet(log);

        assertFalse(net.getPlaces().isEmpty(), "Net should have places");
        assertTrue(net.getPlaces().size() >= 2, "Net should have at least 2 places (start and end)");
    }

    @Test
    public void testDiscoveredNetHasArcs() throws Exception {
        XesParser parser = new XesParser();
        File xesFile = getTestResource("loan-process.xes");

        XesLog log = parser.parse(xesFile);
        PetriNet net = parser.discoverPetriNet(log);

        assertFalse(net.getArcs().isEmpty(), "Net should have arcs");
    }

    @Test
    public void testDiscoverPetriNetWithEmptyLog() throws Exception {
        XesParser parser = new XesParser();
        XesLog log = new XesLog("empty");

        PetriNet net = parser.discoverPetriNet(log);

        assertNotNull(net, "Net should not be null even for empty log");
    }

    @Test
    public void testDiscoverPetriNetWithNullLog() throws Exception {
        XesParser parser = new XesParser();

        PetriNet net = parser.discoverPetriNet(null);

        assertNotNull(net, "Net should not be null for null log");
    }

    @Test
    public void testEventAttributes() throws Exception {
        XesParser parser = new XesParser();
        File xesFile = getTestResource("loan-process.xes");

        XesLog log = parser.parse(xesFile);

        // Check that first event has expected attributes
        XesTrace firstTrace = log.getTraces().get(0);
        XesEvent firstEvent = firstTrace.getEvents().get(0);

        assertEquals("Receive Application", firstEvent.getActivityName(), "Event should have activity name");
        assertEquals("complete", firstEvent.getLifecycle(), "Event should have lifecycle");
        assertNotNull(firstEvent.getTimestamp(), "Event should have timestamp");
    }

    @Test
    public void testXesLogGetEventCount() throws Exception {
        XesParser parser = new XesParser();
        File xesFile = getTestResource("loan-process.xes");

        XesLog log = parser.parse(xesFile);

        assertEquals(8, log.getEventCount(), "Log should report 8 total events");
    }

    @Test
    public void testXesLogToString() throws Exception {
        XesParser parser = new XesParser();
        File xesFile = getTestResource("loan-process.xes");

        XesLog log = parser.parse(xesFile);
        String str = log.toString();

        assertNotNull(str, "toString should not be null");
        assertTrue(str.contains("traces=2"), "toString should contain trace count");
        assertTrue(str.contains("events=8"), "toString should contain event count");
    }

    @Test
    public void testXesTraceToString() throws Exception {
        XesParser parser = new XesParser();
        File xesFile = getTestResource("loan-process.xes");

        XesLog log = parser.parse(xesFile);
        XesTrace trace = log.getTraces().get(0);
        String str = trace.toString();

        assertNotNull(str, "toString should not be null");
        assertTrue(str.contains("Case1"), "toString should contain case ID");
        assertTrue(str.contains("events=4"), "toString should contain event count");
    }

    @Test
    public void testXesEventToString() throws Exception {
        XesParser parser = new XesParser();
        File xesFile = getTestResource("loan-process.xes");

        XesLog log = parser.parse(xesFile);
        XesTrace trace = log.getTraces().get(0);
        XesEvent event = trace.getEvents().get(0);
        String str = event.toString();

        assertNotNull(str, "toString should not be null");
        assertTrue(str.contains("Receive Application"), "toString should contain activity");
    }

    @Test
    public void testParseFromInputStream() throws Exception {
        XesParser parser = new XesParser();
        File xesFile = getTestResource("loan-process.xes");

        XesLog log = parser.parse(new java.io.FileInputStream(xesFile));

        assertNotNull(log, "XesLog should not be null");
        assertEquals(2, log.getTraces().size(), "Log should have 2 traces");
    }
}
