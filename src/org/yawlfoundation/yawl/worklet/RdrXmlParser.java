/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.worklet;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Parses {@code <specId>.rdr.xml} files into {@link RdrSet} instances.
 *
 * <p>The expected XML format:
 * <pre>{@code
 * <rdrSet specId="OrderProcessing">
 *   <tree taskName="ApprovalTask">
 *     <!-- root node: parentId="-1", branch="none" -->
 *     <node id="1" parentId="-1" branch="none"
 *           condition="amount > 1000"
 *           conclusion="FinanceApprovalWorklet"/>
 *     <!-- true-child of node 1 (more specific exception) -->
 *     <node id="2" parentId="1" branch="true"
 *           condition="amount > 10000"
 *           conclusion="CFOApprovalWorklet"/>
 *     <!-- false-child of node 1 (alternative case) -->
 *     <node id="3" parentId="1" branch="false"
 *           condition="priority = urgent"
 *           conclusion="a2a:http://agent:8090/urgent_review"/>
 *   </tree>
 * </rdrSet>
 * }</pre>
 *
 * <p>Conditions use the format supported by {@link RdrCondition}: {@code "attribute operator value"}.
 * A2A conclusions start with {@code "a2a:"} and follow the format
 * {@code "a2a:http://host:port/skill"}.
 */
final class RdrXmlParser {

    private RdrXmlParser() {
        throw new UnsupportedOperationException("Utility class — not instantiable");
    }

    /**
     * Parses an RDR rule file from the given input stream.
     *
     * @param specId the specification ID (used as fallback if the XML root attribute is missing)
     * @param is     the input stream of the .rdr.xml file
     * @return a populated {@link RdrSet}
     * @throws WorkletServiceException if the XML cannot be parsed or has invalid structure
     * @throws IOException             if reading the stream fails
     */
    static RdrSet parse(String specId, InputStream is) throws IOException {
        Document doc = parseXml(is);
        Element root = doc.getDocumentElement();

        String resolvedSpecId = root.hasAttribute("specId")
                ? root.getAttribute("specId")
                : specId;

        RdrSet rdrSet = new RdrSet(resolvedSpecId);

        NodeList trees = root.getElementsByTagName("tree");
        for (int i = 0; i < trees.getLength(); i++) {
            Element treeElem = (Element) trees.item(i);
            RdrTree tree = parseTree(treeElem);
            rdrSet.putTree(tree);
        }

        return rdrSet;
    }

    private static RdrTree parseTree(Element treeElem) {
        String taskName = treeElem.getAttribute("taskName");
        if (taskName.isBlank()) {
            throw new WorkletServiceException(
                    "RDR tree element missing 'taskName' attribute");
        }

        RdrTree tree = new RdrTree(taskName);

        // Collect all nodes first, then wire parent→child links
        NodeList nodeElems = treeElem.getElementsByTagName("node");
        Map<Integer, RdrNode> nodeById = new HashMap<>();

        for (int i = 0; i < nodeElems.getLength(); i++) {
            Element nodeElem = (Element) nodeElems.item(i);
            int id = parseInt(nodeElem, "id");
            String condition = nodeElem.getAttribute("condition");
            String conclusion = nodeElem.getAttribute("conclusion");

            if (condition.isBlank()) {
                throw new WorkletServiceException(
                        "RDR node " + id + " missing 'condition' attribute");
            }
            if (conclusion.isBlank()) {
                throw new WorkletServiceException(
                        "RDR node " + id + " missing 'conclusion' attribute");
            }

            nodeById.put(id, new RdrNode(id, condition, conclusion));
        }

        // Wire root and children
        for (int i = 0; i < nodeElems.getLength(); i++) {
            Element nodeElem = (Element) nodeElems.item(i);
            int id = parseInt(nodeElem, "id");
            int parentId = parseInt(nodeElem, "parentId");
            String branch = nodeElem.getAttribute("branch");
            RdrNode node = nodeById.get(id);

            if (parentId < 0) {
                // This is the root node
                tree.setRoot(node);
            } else {
                RdrNode parent = nodeById.get(parentId);
                if (parent == null) {
                    throw new WorkletServiceException(
                            "RDR node " + id + " references unknown parentId " + parentId);
                }
                boolean asTrueChild = "true".equalsIgnoreCase(branch);
                tree.addNode(node, parentId, asTrueChild);
            }
        }

        return tree;
    }

    private static Document parseXml(InputStream is) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder().parse(is);
        } catch (ParserConfigurationException | SAXException e) {
            throw new WorkletServiceException("Failed to parse RDR XML: " + e.getMessage(), e);
        }
    }

    private static int parseInt(Element elem, String attribute) {
        String value = elem.getAttribute(attribute);
        if (value.isBlank()) {
            throw new WorkletServiceException(
                    "RDR node element missing '" + attribute + "' attribute");
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new WorkletServiceException(
                    "RDR node attribute '" + attribute + "' is not an integer: " + value);
        }
    }
}
