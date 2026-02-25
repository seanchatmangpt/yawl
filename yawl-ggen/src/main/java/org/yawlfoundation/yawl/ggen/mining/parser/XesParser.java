package org.yawlfoundation.yawl.ggen.mining.parser;

import org.yawlfoundation.yawl.ggen.mining.model.Arc;
import org.yawlfoundation.yawl.ggen.mining.model.PetriNet;
import org.yawlfoundation.yawl.ggen.mining.model.Place;
import org.yawlfoundation.yawl.ggen.mining.model.Transition;
import org.yawlfoundation.yawl.ggen.mining.model.XesEvent;
import org.yawlfoundation.yawl.ggen.mining.model.XesLog;
import org.yawlfoundation.yawl.ggen.mining.model.XesTrace;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * SAX-based parser for XES (eXtensible Event Stream) event log files.
 * Converts XES XML into an XesLog model for process mining tasks.
 * Also provides Petri net discovery from event sequences.
 */
public class XesParser {

    public XesLog parse(File xesFile) throws IOException, SAXException {
        try (FileInputStream fis = new FileInputStream(xesFile)) {
            return parse(fis);
        }
    }

    public XesLog parse(InputStream inputStream) throws IOException, SAXException {
        XMLReader xmlReader = XMLReaderFactory.createXMLReader();
        XesHandler handler = new XesHandler();
        xmlReader.setContentHandler(handler);
        xmlReader.parse(new InputSource(inputStream));
        return handler.getLog();
    }

    /**
     * Discover a Petri net from event log using simple algorithm:
     * 1. Create places for each activity boundary + start/end
     * 2. Create transitions for each activity
     * 3. Connect via arcs based on activity sequence
     * 4. Merge parallel paths from different traces
     * @param log XES event log
     * @return discovered PetriNet model
     */
    public PetriNet discoverPetriNet(XesLog log) {
        if (log == null || log.getTraces().isEmpty()) {
            return new PetriNet("discovered", "Discovered Petri Net (empty)");
        }

        PetriNet net = new PetriNet("discovered", "Discovered Petri Net");

        // Get all activities
        Set<String> activities = log.getActivities();
        if (activities.isEmpty()) {
            return net;
        }

        // Create start and end places
        Place startPlace = new Place("p_start", "START", 1);
        Place endPlace = new Place("p_end", "END", 0);
        net.addPlace(startPlace);
        net.addPlace(endPlace);

        // Create transition for each activity
        for (String activity : activities) {
            Transition t = new Transition("t_" + sanitizeId(activity), activity);
            net.addTransition(t);
        }

        // Track activity flow relationships (direct succession)
        boolean[][] activityFlow = new boolean[activities.size()][activities.size()];
        List<String> activityList = new ArrayList<>(activities);

        // Extract flow from all traces
        for (XesTrace trace : log.getTraces()) {
            List<XesEvent> events = trace.getEvents();
            for (int i = 0; i < events.size() - 1; i++) {
                String currentActivity = events.get(i).getActivityName();
                String nextActivity = events.get(i + 1).getActivityName();

                int currentIdx = activityList.indexOf(currentActivity);
                int nextIdx = activityList.indexOf(nextActivity);

                if (currentIdx >= 0 && nextIdx >= 0) {
                    activityFlow[currentIdx][nextIdx] = true;
                }
            }
        }

        // Create intermediate places between activities and connect with arcs
        for (int i = 0; i < activityList.size(); i++) {
            String activity = activityList.get(i);
            String transitionId = "t_" + sanitizeId(activity);
            Transition transition = net.getTransition(transitionId);

            if (transition == null) continue;

            // Check if this is a start activity (appears first in any trace)
            boolean isStart = false;
            boolean isEnd = false;

            for (XesTrace trace : log.getTraces()) {
                if (!trace.getEvents().isEmpty()) {
                    if (trace.getEvents().get(0).getActivityName().equals(activity)) {
                        isStart = true;
                    }
                    if (trace.getEvents().get(trace.getEvents().size() - 1).getActivityName().equals(activity)) {
                        isEnd = true;
                    }
                }
            }

            // Create place before activity
            String inPlaceId = "p_in_" + sanitizeId(activity);
            Place inPlace = new Place(inPlaceId, "Before " + activity, 0);
            net.addPlace(inPlace);

            // Create place after activity
            String outPlaceId = "p_out_" + sanitizeId(activity);
            Place outPlace = new Place(outPlaceId, "After " + activity, 0);
            net.addPlace(outPlace);

            // Connect start -> activity
            if (isStart) {
                Arc startArc = new Arc("arc_start_" + sanitizeId(activity), startPlace, transition);
                net.addArc(startArc);
            } else {
                Arc inArc = new Arc("arc_in_" + sanitizeId(activity), inPlace, transition);
                net.addArc(inArc);
            }

            // Connect activity -> place after
            Arc outArc = new Arc("arc_out_" + sanitizeId(activity), transition, outPlace);
            net.addArc(outArc);

            // Connect to end if this is an end activity
            if (isEnd) {
                Arc endArc = new Arc("arc_to_end_" + sanitizeId(activity), outPlace, endPlace);
                net.addArc(endArc);
            }
        }

        // Connect activity sequences
        for (int i = 0; i < activityList.size(); i++) {
            if (activityFlow[i].length == 0) continue;
            for (int j = 0; j < activityList.size(); j++) {
                if (activityFlow[i][j]) {
                    String fromActivity = activityList.get(i);
                    String toActivity = activityList.get(j);

                    String fromOutPlaceId = "p_out_" + sanitizeId(fromActivity);
                    String toInPlaceId = "p_in_" + sanitizeId(toActivity);

                    Place fromPlace = net.getPlace(fromOutPlaceId);
                    Place toPlace = net.getPlace(toInPlaceId);

                    if (fromPlace != null && toPlace != null) {
                        // Create intermediate transition to merge flows
                        String mergeTransId = "t_flow_" + sanitizeId(fromActivity) + "_" + sanitizeId(toActivity);
                        if (net.getTransition(mergeTransId) == null) {
                            Transition mergeT = new Transition(mergeTransId,
                                fromActivity + " -> " + toActivity);
                            net.addTransition(mergeT);

                            Arc flowIn = new Arc("arc_flow_in_" + mergeTransId, fromPlace, mergeT);
                            Arc flowOut = new Arc("arc_flow_out_" + mergeTransId, mergeT, toPlace);
                            net.addArc(flowIn);
                            net.addArc(flowOut);
                        }
                    }
                }
            }
        }

        return net;
    }

    /**
     * Sanitize an activity name to use as element ID (alphanumeric + underscore only).
     */
    private String sanitizeId(String name) {
        return name.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
    }

    /**
     * SAX content handler for parsing XES elements.
     */
    private static class XesHandler extends DefaultHandler {
        private XesLog log;
        private XesTrace currentTrace;
        private XesEvent currentEvent;
        private boolean insideEvent;
        private boolean insideTrace;

        @Override
        public void startDocument() {
            log = new XesLog();
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            String tagName = localName.isEmpty() ? qName : localName;
            String key = attributes.getValue("key");
            String value = attributes.getValue("value");

            switch (tagName) {
                case "log":
                    String logName = attributes.getValue("name");
                    if (logName != null) {
                        log.setName(logName);
                    }
                    break;

                case "trace":
                    currentTrace = new XesTrace("");
                    insideTrace = true;
                    break;

                case "event":
                    currentEvent = null;
                    insideEvent = true;
                    break;

                case "string":
                case "date":
                case "int":
                    // Handle XES attributes (key-value pairs)
                    if (key != null) {
                        if (insideEvent && currentTrace != null) {
                            if (currentEvent == null && "concept:name".equals(key)) {
                                // First attribute in event should be concept:name
                                currentEvent = new XesEvent(currentTrace.getCaseId(), value);
                            } else if (currentEvent != null) {
                                // Handle attributes inside event
                                if ("concept:name".equals(key)) {
                                    // Override activity name if needed
                                    if (currentEvent.getActivityName() == null || currentEvent.getActivityName().isEmpty()) {
                                        currentEvent = new XesEvent(currentTrace.getCaseId(), value);
                                    }
                                } else if ("lifecycle:transition".equals(key)) {
                                    currentEvent.setLifecycle(value);
                                } else if ("time:timestamp".equals(key)) {
                                    currentEvent.setTimestamp(value);
                                } else {
                                    currentEvent.setAttribute(key, value);
                                }
                            }
                        } else if (insideTrace && !insideEvent && "concept:name".equals(key)) {
                            // Trace-level concept:name sets case ID
                            currentTrace = new XesTrace(value);
                        }
                    }
                    break;
                default:
                    break; // Ignore unknown XES elements
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            String tagName = localName.isEmpty() ? qName : localName;

            if ("event".equals(tagName)) {
                if (currentEvent != null && currentTrace != null) {
                    // Ensure event has activity name
                    if (currentEvent.getActivityName() != null) {
                        currentTrace.addEvent(currentEvent);
                    }
                }
                currentEvent = null;
                insideEvent = false;
            } else if ("trace".equals(tagName)) {
                if (currentTrace != null && !currentTrace.getCaseId().isEmpty()) {
                    log.addTrace(currentTrace);
                }
                currentTrace = null;
                insideTrace = false;
            }
        }

        public XesLog getLog() {
            return log;
        }
    }
}
