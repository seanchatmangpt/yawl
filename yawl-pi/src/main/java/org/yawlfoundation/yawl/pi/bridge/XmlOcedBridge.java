/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.pi.bridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.yawlfoundation.yawl.pi.PIException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * OCEL2 bridge for XML event logs.
 *
 * <p>Parses XML event elements and converts to OCEL2 v2.0 JSON format.
 * Uses heuristics to identify case ID, activity, and timestamp elements.</p>
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public final class XmlOcedBridge implements OcedBridge {

    private final SchemaInferenceEngine schemaInferenceEngine;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Create XML OCEL2 bridge.
     *
     * @param schemaInferenceEngine engine for inferring element roles
     */
    public XmlOcedBridge(SchemaInferenceEngine schemaInferenceEngine) {
        if (schemaInferenceEngine == null) {
            throw new IllegalArgumentException("schemaInferenceEngine is required");
        }
        this.schemaInferenceEngine = schemaInferenceEngine;
    }

    @Override
    public OcedSchema inferSchema(String rawSample) throws PIException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(rawSample.getBytes()));
            Element root = doc.getDocumentElement();

            NodeList children = root.getChildNodes();
            if (children.getLength() == 0) {
                throw new PIException("Empty XML document", "dataprep");
            }

            String caseIdCol = findElementName(doc, "caseId", "case_id", "CaseId");
            String activityCol = findElementName(doc, "activity", "task", "Activity");
            String timestampCol = findElementName(doc, "timestamp", "time", "date");

            if (caseIdCol == null || activityCol == null || timestampCol == null) {
                throw new PIException(
                    String.format(
                        "Cannot identify required XML elements: case=%s, activity=%s, timestamp=%s",
                        caseIdCol, activityCol, timestampCol
                    ),
                    "dataprep"
                );
            }

            return new OcedSchema(
                "schema-" + System.currentTimeMillis(),
                caseIdCol,
                activityCol,
                timestampCol,
                java.util.List.of("case"),
                java.util.List.of(),
                "xml",
                false,
                java.time.Instant.now()
            );
        } catch (PIException e) {
            throw e;
        } catch (Exception e) {
            throw new PIException(
                "XML schema inference failed: " + e.getMessage(),
                "dataprep",
                e
            );
        }
    }

    @Override
    public String convert(String rawData, OcedSchema schema) throws PIException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(rawData.getBytes()));
            Element root = doc.getDocumentElement();

            ObjectNode ocelRoot = objectMapper.createObjectNode();
            ocelRoot.put("ocel:version", "2.0");
            ocelRoot.put("ocel:ordering", "timestamp");

            ArrayNode attrArray = objectMapper.createArrayNode();
            attrArray.add("org:resource");
            ocelRoot.set("ocel:attribute-names", attrArray);

            ArrayNode typeArray = objectMapper.createArrayNode();
            typeArray.add("case");
            ocelRoot.set("ocel:object-types", typeArray);

            ObjectNode eventsNode = objectMapper.createObjectNode();
            ObjectNode objectsNode = objectMapper.createObjectNode();
            Set<String> seenCases = new HashSet<>();

            NodeList eventElements = root.getElementsByTagName("*");
            int eventCount = 0;

            for (int i = 0; i < eventElements.getLength(); i++) {
                Element elem = (Element) eventElements.item(i);

                Element caseIdElem = getChildElement(elem, schema.caseIdColumn());
                Element activityElem = getChildElement(elem, schema.activityColumn());
                Element timestampElem = getChildElement(elem, schema.timestampColumn());

                if (caseIdElem == null || activityElem == null || timestampElem == null) {
                    continue;
                }

                String caseId = caseIdElem.getTextContent().trim();
                String activity = activityElem.getTextContent().trim();
                String timestamp = timestampElem.getTextContent().trim();

                if (caseId.isEmpty() || activity.isEmpty()) {
                    continue;
                }

                ObjectNode event = objectMapper.createObjectNode();
                event.put("ocel:activity", activity);
                event.put("ocel:timestamp", timestamp);

                ObjectNode omap = objectMapper.createObjectNode();
                ArrayNode caseArray = objectMapper.createArrayNode();
                caseArray.add(caseId);
                omap.set("case", caseArray);
                event.set("ocel:omap", omap);

                event.set("ocel:vmap", objectMapper.createObjectNode());

                eventsNode.set("evt-" + eventCount, event);
                eventCount++;

                if (!seenCases.contains(caseId)) {
                    ObjectNode caseObj = objectMapper.createObjectNode();
                    caseObj.put("ocel:type", "case");
                    ObjectNode caseVmap = objectMapper.createObjectNode();
                    caseVmap.put("case:id", caseId);
                    caseObj.set("ocel:ovmap", caseVmap);
                    objectsNode.set(caseId, caseObj);
                    seenCases.add(caseId);
                }
            }

            ocelRoot.set("ocel:events", eventsNode);
            ocelRoot.set("ocel:objects", objectsNode);

            return objectMapper.writeValueAsString(ocelRoot);
        } catch (Exception e) {
            throw new PIException(
                "XML conversion failed: " + e.getMessage(),
                "dataprep",
                e
            );
        }
    }

    @Override
    public String formatName() {
        return "xml";
    }

    private String findElementName(Document doc, String... candidates) {
        for (String candidate : candidates) {
            NodeList nodes = doc.getElementsByTagName(candidate);
            if (nodes.getLength() > 0) {
                return candidate;
            }
        }
        return null;
    }

    private Element getChildElement(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return (Element) nodes.item(0);
        }
        return null;
    }
}
