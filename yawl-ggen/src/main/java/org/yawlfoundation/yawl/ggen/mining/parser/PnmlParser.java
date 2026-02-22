package org.yawlfoundation.yawl.ggen.mining.parser;

import org.yawlfoundation.yawl.ggen.mining.model.*;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLReaderFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * SAX-based parser for PNML (Petri Net Markup Language) files.
 * Converts PNML XML into a PetriNet model for further processing.
 */
public class PnmlParser {
    private static final String PNML_NAMESPACE = "http://www.pnml.org/version-2009-12-16";

    public PetriNet parse(File pnmlFile) throws IOException, SAXException {
        try (FileInputStream fis = new FileInputStream(pnmlFile)) {
            return parse(fis);
        }
    }

    public PetriNet parse(InputStream inputStream) throws IOException, SAXException {
        XMLReader xmlReader = XMLReaderFactory.createXMLReader();
        PnmlHandler handler = new PnmlHandler();
        xmlReader.setContentHandler(handler);
        xmlReader.parse(new InputSource(inputStream));
        return handler.getPetriNet();
    }

    /**
     * SAX content handler for parsing PNML elements.
     */
    private static class PnmlHandler extends DefaultHandler {
        private PetriNet petriNet;
        private String currentNetId;
        private StringBuilder textBuffer;
        private String currentElementId;
        private String currentElementName;
        private String currentPlaceId;
        private int currentInitialMarking;

        @Override
        public void startDocument() throws SAXException {
            textBuffer = new StringBuilder();
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            String tagName = localName.isEmpty() ? qName : localName;
            textBuffer.setLength(0);

            switch (tagName) {
                case "pnml":
                    // Root element, nothing to do
                    break;
                case "net":
                    String netId = attributes.getValue("id");
                    String netName = attributes.getValue("name");
                    petriNet = new PetriNet(netId, netName != null ? netName : netId);
                    currentNetId = netId;
                    break;
                case "place":
                    currentElementId = attributes.getValue("id");
                    currentPlaceId = currentElementId;
                    currentInitialMarking = 0;
                    break;
                case "transition":
                    currentElementId = attributes.getValue("id");
                    break;
                case "arc":
                    String arcId = attributes.getValue("id");
                    String source = attributes.getValue("source");
                    String target = attributes.getValue("target");
                    createArc(arcId, source, target);
                    break;
                case "initialMarking":
                    // Token count for place initial marking
                    break;
                case "text":
                    textBuffer.setLength(0);
                    break;
            }
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            textBuffer.append(new String(ch, start, length));
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            String tagName = localName.isEmpty() ? qName : localName;
            String content = textBuffer.toString().trim();

            switch (tagName) {
                case "name":
                    if (currentElementId != null) {
                        currentElementName = content;
                    }
                    break;
                case "text":
                    if (currentElementName == null && content.length() > 0) {
                        currentElementName = content;
                    }
                    break;
                case "value":
                    // Extract initial marking value
                    try {
                        currentInitialMarking = Integer.parseInt(content);
                    } catch (NumberFormatException e) {
                        currentInitialMarking = 0;
                    }
                    break;
                case "place":
                    if (currentElementId != null && petriNet != null) {
                        String placeName = currentElementName != null ? currentElementName : currentElementId;
                        Place place = new Place(currentElementId, placeName, currentInitialMarking);
                        petriNet.addPlace(place);
                    }
                    currentElementId = null;
                    currentElementName = null;
                    currentInitialMarking = 0;
                    break;
                case "transition":
                    if (currentElementId != null && petriNet != null) {
                        String transitionName = currentElementName != null ? currentElementName : currentElementId;
                        Transition transition = new Transition(currentElementId, transitionName);
                        petriNet.addTransition(transition);
                    }
                    currentElementId = null;
                    currentElementName = null;
                    break;
            }
        }

        @Override
        public void endDocument() throws SAXException {
            if (petriNet != null && !petriNet.isValid()) {
                throw new SAXException("Invalid Petri net: arcs reference non-existent elements");
            }
        }

        private void createArc(String arcId, String source, String target) {
            if (petriNet == null || source == null || target == null) {
                return;
            }

            PnmlElement sourceElem = petriNet.getPlace(source);
            if (sourceElem == null) {
                sourceElem = petriNet.getTransition(source);
            }

            PnmlElement targetElem = petriNet.getPlace(target);
            if (targetElem == null) {
                targetElem = petriNet.getTransition(target);
            }

            if (sourceElem != null && targetElem != null) {
                Arc arc = new Arc(arcId, sourceElem, targetElem);
                petriNet.addArc(arc);
            }
        }

        public PetriNet getPetriNet() {
            return petriNet;
        }
    }
}
