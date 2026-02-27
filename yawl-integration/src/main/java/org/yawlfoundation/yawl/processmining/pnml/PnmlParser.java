/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * The YAWL Foundation is a collaboration of individuals and
 * organisations who are committed to improving workflow technology.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.processmining.pnml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for PNML (Petri Net Markup Language) XML documents.
 * Converts PNML XML to PnmlProcess object.
 *
 * PNML format (simplified):
 * <pre>
 * &lt;pnml&gt;
 *   &lt;net id="..." name="..."&gt;
 *     &lt;place id="..."&gt;
 *       &lt;name&gt;&lt;text&gt;...&lt;/text&gt;&lt;/name&gt;
 *       &lt;initialMarking&gt;&lt;text&gt;1&lt;/text&gt;&lt;/initialMarking&gt;
 *     &lt;/place&gt;
 *     &lt;transition id="..."&gt;
 *       &lt;name&gt;&lt;text&gt;...&lt;/text&gt;&lt;/name&gt;
 *     &lt;/transition&gt;
 *     &lt;arc id="..." source="..." target="..."/&gt;
 *   &lt;/net&gt;
 * &lt;/pnml&gt;
 * </pre>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public final class PnmlParser {

    private static final String PNML_NS = "http://www.pnml.org/version-2009-05-13/grammar/pnml";

    /**
     * Parses PNML XML string to PnmlProcess.
     *
     * @param pnmlXml PNML XML as string
     * @return parsed PnmlProcess
     * @throws PnmlParseException if parsing fails
     */
    public PnmlProcess parse(String pnmlXml) throws PnmlParseException {
        if (pnmlXml == null || pnmlXml.isEmpty()) {
            throw new PnmlParseException("PNML XML cannot be null or empty");
        }

        try {
            DocumentBuilderFactory factory = createSecureDocumentBuilderFactory();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(pnmlXml.getBytes()));
            return parseDocument(doc);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new PnmlParseException("Failed to parse PNML XML: " + e.getMessage(), e);
        }
    }

    /**
     * Parses PNML XML file to PnmlProcess.
     *
     * @param pnmlFile Path to PNML XML file
     * @return parsed PnmlProcess
     * @throws PnmlParseException if parsing fails
     * @throws IOException        if file cannot be read
     */
    public PnmlProcess parseFile(Path pnmlFile) throws PnmlParseException, IOException {
        if (pnmlFile == null) {
            throw new PnmlParseException("File path cannot be null");
        }

        String xml = Files.readString(pnmlFile);
        return parse(xml);
    }

    /**
     * Parses Document to PnmlProcess.
     *
     * @param doc W3C DOM Document
     * @return parsed PnmlProcess
     * @throws PnmlParseException if required elements are missing
     */
    private PnmlProcess parseDocument(Document doc) throws PnmlParseException {
        Element root = doc.getDocumentElement();
        if (!"pnml".equals(root.getTagName())) {
            throw new PnmlParseException("Root element must be 'pnml'");
        }

        NodeList netNodes = doc.getElementsByTagName("net");
        if (netNodes.getLength() == 0) {
            throw new PnmlParseException("No 'net' element found in PNML");
        }

        Element netElement = (Element) netNodes.item(0);
        return parseNet(netElement);
    }

    /**
     * Parses a 'net' element to PnmlProcess.
     *
     * @param netElement 'net' XML element
     * @return parsed PnmlProcess
     * @throws PnmlParseException if parsing fails
     */
    private PnmlProcess parseNet(Element netElement) throws PnmlParseException {
        String id = netElement.getAttribute("id");
        if (id.isEmpty()) {
            throw new PnmlParseException("Net element missing 'id' attribute");
        }

        String name = parseNameElement(netElement, "net");

        List<PnmlPlace> places = parsePlaces(netElement);
        List<PnmlTransition> transitions = parseTransitions(netElement);
        List<PnmlArc> arcs = parseArcs(netElement);

        return new PnmlProcess(id, name, places, transitions, arcs);
    }

    /**
     * Parses all 'place' elements.
     *
     * @param netElement Parent 'net' element
     * @return list of PnmlPlace objects
     * @throws PnmlParseException if parsing fails
     */
    private List<PnmlPlace> parsePlaces(Element netElement) throws PnmlParseException {
        List<PnmlPlace> places = new ArrayList<>();
        NodeList placeNodes = netElement.getElementsByTagName("place");

        for (int i = 0; i < placeNodes.getLength(); i++) {
            Element placeElement = (Element) placeNodes.item(i);
            String id = placeElement.getAttribute("id");
            if (id.isEmpty()) {
                throw new PnmlParseException("Place element missing 'id' attribute");
            }

            String name = parseNameElement(placeElement, id);
            int initialMarking = parseInitialMarking(placeElement);

            places.add(new PnmlPlace(id, name, initialMarking));
        }

        return places;
    }

    /**
     * Parses all 'transition' elements.
     *
     * @param netElement Parent 'net' element
     * @return list of PnmlTransition objects
     * @throws PnmlParseException if parsing fails
     */
    private List<PnmlTransition> parseTransitions(Element netElement) throws PnmlParseException {
        List<PnmlTransition> transitions = new ArrayList<>();
        NodeList transitionNodes = netElement.getElementsByTagName("transition");

        for (int i = 0; i < transitionNodes.getLength(); i++) {
            Element transElement = (Element) transitionNodes.item(i);
            String id = transElement.getAttribute("id");
            if (id.isEmpty()) {
                throw new PnmlParseException("Transition element missing 'id' attribute");
            }

            String name = parseNameElement(transElement, id);
            boolean isSilent = name.isEmpty() || "tau".equalsIgnoreCase(name);

            transitions.add(new PnmlTransition(id, name, isSilent));
        }

        return transitions;
    }

    /**
     * Parses all 'arc' elements.
     *
     * @param netElement Parent 'net' element
     * @return list of PnmlArc objects
     * @throws PnmlParseException if parsing fails
     */
    private List<PnmlArc> parseArcs(Element netElement) throws PnmlParseException {
        List<PnmlArc> arcs = new ArrayList<>();
        NodeList arcNodes = netElement.getElementsByTagName("arc");

        for (int i = 0; i < arcNodes.getLength(); i++) {
            Element arcElement = (Element) arcNodes.item(i);
            String id = arcElement.getAttribute("id");
            if (id.isEmpty()) {
                throw new PnmlParseException("Arc element missing 'id' attribute");
            }

            String source = arcElement.getAttribute("source");
            if (source.isEmpty()) {
                throw new PnmlParseException("Arc element missing 'source' attribute");
            }

            String target = arcElement.getAttribute("target");
            if (target.isEmpty()) {
                throw new PnmlParseException("Arc element missing 'target' attribute");
            }

            arcs.add(new PnmlArc(id, source, target));
        }

        return arcs;
    }

    /**
     * Parses the name of an element.
     * Looks for child 'name' → 'text' or attribute 'name'.
     *
     * @param element  XML element
     * @param defaultName Default name if none found
     * @return element name or defaultName
     */
    private String parseNameElement(Element element, String defaultName) {
        NodeList nameNodes = element.getElementsByTagName("name");
        if (nameNodes.getLength() > 0) {
            Element nameElement = (Element) nameNodes.item(0);
            NodeList textNodes = nameElement.getElementsByTagName("text");
            if (textNodes.getLength() > 0) {
                Element textElement = (Element) textNodes.item(0);
                String text = textElement.getTextContent();
                if (text != null && !text.isEmpty()) {
                    return text;
                }
            }
        }

        String nameAttr = element.getAttribute("name");
        if (!nameAttr.isEmpty()) {
            return nameAttr;
        }

        return defaultName;
    }

    /**
     * Parses the initial marking of a place.
     * Looks for 'initialMarking' → 'text' element.
     *
     * @param placeElement Place XML element
     * @return initial marking (0 if not specified)
     */
    private int parseInitialMarking(Element placeElement) {
        NodeList markingNodes = placeElement.getElementsByTagName("initialMarking");
        if (markingNodes.getLength() > 0) {
            Element markingElement = (Element) markingNodes.item(0);
            NodeList textNodes = markingElement.getElementsByTagName("text");
            if (textNodes.getLength() > 0) {
                Element textElement = (Element) textNodes.item(0);
                String text = textElement.getTextContent();
                if (text != null && !text.isEmpty()) {
                    try {
                        return Integer.parseInt(text.trim());
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                }
            }
        }
        return 0;
    }

    /**
     * Creates a secure DocumentBuilderFactory that disables XXE attacks.
     *
     * @return secure DocumentBuilderFactory
     * @throws ParserConfigurationException if configuration fails
     */
    private DocumentBuilderFactory createSecureDocumentBuilderFactory()
            throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        try {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
        } catch (ParserConfigurationException e) {
            throw new ParserConfigurationException("Failed to configure secure parser: " + e.getMessage());
        }

        return factory;
    }
}
