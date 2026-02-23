package org.yawlfoundation.yawl.ggen.mining.parser;

import org.yawlfoundation.yawl.ggen.mining.model.*;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

/**
 * SAX-based parser for BPMN 2.0 (Business Process Model and Notation) files.
 * Converts BPMN XML into a unified Petri net model compatible with PNML.
 * Supports: Celonis, UiPath, Signavio, SAP exports.
 */
public class BpmnParser {
    private static final String BPMN_NAMESPACE = "http://www.omg.org/spec/BPMN/20100524/MODEL";

    public PetriNet parse(File bpmnFile) throws IOException, SAXException {
        try (FileInputStream fis = new FileInputStream(bpmnFile)) {
            return parse(fis);
        }
    }

    public PetriNet parse(InputStream inputStream) throws IOException, SAXException {
        XMLReader xmlReader = XMLReaderFactory.createXMLReader();
        BpmnHandler handler = new BpmnHandler();
        xmlReader.setContentHandler(handler);
        xmlReader.parse(new InputSource(inputStream));
        return handler.getPetriNet();
    }

    /**
     * SAX content handler for parsing BPMN elements.
     * Maps BPMN concepts to Petri net model:
     * - Task/Event → Transition
     * - Exclusive/Parallel Gateway → Split/Join
     * - Sequence Flow → Arc
     * - Start/End Event → Initial/Final place
     */
    private static class BpmnHandler extends DefaultHandler {
        private PetriNet petriNet;
        private Map<String, String> bpmnId2Name;
        private Map<String, String> bpmnElementTypes;
        private StringBuilder textBuffer;
        private Stack<String> elementStack;

        public BpmnHandler() {
            this.bpmnId2Name = new HashMap<>();
            this.bpmnElementTypes = new HashMap<>();
            this.elementStack = new Stack<>();
        }

        @Override
        public void startDocument() {
            textBuffer = new StringBuilder();
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            String tagName = localName.isEmpty() ? qName : localName;
            elementStack.push(tagName);
            textBuffer.setLength(0);

            String id = attributes.getValue("id");

            switch (tagName) {
                case "process":
                    petriNet = new PetriNet(id != null ? id : "BPMN_Process",
                        attributes.getValue("name"));
                    break;

                case "startEvent":
                    if (id != null) {
                        // Create initial place
                        Place startPlace = new Place(id + "_place",
                            attributes.getValue("name") + " (Start)", 1);
                        petriNet.addPlace(startPlace);

                        // Create start transition
                        Transition startTrans = new Transition(id,
                            attributes.getValue("name"));
                        petriNet.addTransition(startTrans);
                        bpmnElementTypes.put(id, "StartTransition");
                        bpmnId2Name.put(id, attributes.getValue("name"));
                    }
                    break;

                case "endEvent":
                    if (id != null) {
                        // Create end transition
                        Transition endTrans = new Transition(id,
                            attributes.getValue("name"));
                        petriNet.addTransition(endTrans);

                        // Create final place
                        Place endPlace = new Place(id + "_place",
                            attributes.getValue("name") + " (End)");
                        petriNet.addPlace(endPlace);
                        bpmnElementTypes.put(id, "EndTransition");
                        bpmnId2Name.put(id, attributes.getValue("name"));
                    }
                    break;

                case "task":
                case "serviceTask":
                case "userTask":
                case "manualTask":
                case "scriptTask":
                    if (id != null) {
                        Transition task = new Transition(id,
                            attributes.getValue("name"));
                        petriNet.addTransition(task);
                        bpmnElementTypes.put(id, "Transition");
                        bpmnId2Name.put(id, attributes.getValue("name"));
                    }
                    break;

                case "exclusiveGateway":
                    if (id != null) {
                        Transition xorGateway = new Transition(id,
                            attributes.getValue("name"));
                        petriNet.addTransition(xorGateway);
                        bpmnElementTypes.put(id, "XorGateway");
                        bpmnId2Name.put(id, attributes.getValue("name"));
                    }
                    break;

                case "parallelGateway":
                    if (id != null) {
                        Transition andGateway = new Transition(id,
                            attributes.getValue("name"));
                        petriNet.addTransition(andGateway);
                        bpmnElementTypes.put(id, "AndGateway");
                        bpmnId2Name.put(id, attributes.getValue("name"));
                    }
                    break;

                case "sequenceFlow":
                    // Handle flow connections
                    if (id != null) {
                        String sourceRef = attributes.getValue("sourceRef");
                        String targetRef = attributes.getValue("targetRef");

                        if (sourceRef != null && targetRef != null) {
                            createFlow(id, sourceRef, targetRef);
                        }
                    }
                    break;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            textBuffer.append(new String(ch, start, length));
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            elementStack.pop();
        }

        private void createFlow(String flowId, String sourceId, String targetId) {
            if (petriNet == null) return;

            // Get source and target elements
            PnmlElement source = getElement(sourceId);
            PnmlElement target = getElement(targetId);

            if (source == null || target == null) return;

            // For BPMN conversion to Petri net, we need to ensure proper P→T→P flow
            // If both are transitions, insert an intermediate place
            if (source instanceof Transition && target instanceof Transition) {
                String placeId = sourceId + "_to_" + targetId;
                Place intermediatePlace = new Place(placeId, sourceId + " → " + targetId);
                petriNet.addPlace(intermediatePlace);

                Arc arc1 = new Arc(flowId + "_1", source, intermediatePlace);
                Arc arc2 = new Arc(flowId + "_2", intermediatePlace, target);
                petriNet.addArc(arc1);
                petriNet.addArc(arc2);
            } else {
                // Normal P→T or T→P flow
                Arc arc = new Arc(flowId, source, target);
                petriNet.addArc(arc);
            }
        }

        private PnmlElement getElement(String bpmnId) {
            // Try to find as place
            Place place = petriNet.getPlace(bpmnId);
            if (place != null) return place;

            // Try to find as transition
            Transition trans = petriNet.getTransition(bpmnId);
            if (trans != null) return trans;

            // Try to find the associated place (for events)
            place = petriNet.getPlace(bpmnId + "_place");
            if (place != null) return place;

            return null;
        }

        public PetriNet getPetriNet() {
            return petriNet;
        }
    }
}
