/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.integration.processmining;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.yawlfoundation.yawl.elements.YAtomicTask;
import org.yawlfoundation.yawl.elements.YCompositeTask;
import org.yawlfoundation.yawl.elements.YCondition;
import org.yawlfoundation.yawl.elements.YExternalNetElement;
import org.yawlfoundation.yawl.elements.YInputCondition;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YOutputCondition;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.YTask;

/**
 * Exports YAWL workflow nets to PNML (Petri Net Markup Language) format.
 *
 * <p>Converts YAWL's internal Petri net representation (YNet, YTask, YCondition, YFlow)
 * to the standard PNML 2009 format consumed by process mining tools such as rust4pm,
 * ProM, and pm4py for conformance checking and analysis.</p>
 *
 * <h2>Mapping</h2>
 * <ul>
 *   <li><b>YCondition</b> (including YInputCondition, YOutputCondition) → PNML `<place>`</li>
 *   <li><b>YTask</b> (atomic or composite) → PNML `<transition>`</li>
 *   <li><b>YFlow</b> (condition→task or task→condition) → PNML `<arc>`</li>
 *   <li><b>Input condition</b> → place with `<initialMarking><text>1</text></initialMarking>`</li>
 * </ul>
 *
 * <h2>YAWL Extensions</h2>
 * <p>Split and join types are preserved in PNML toolspecific annotations:
 * <ul>
 *   <li>AND (1) - All branches synchronized</li>
 *   <li>OR (103) - One or more branches activated</li>
 *   <li>XOR (126) - Exactly one branch activated</li>
 * </ul>
 * </p>
 *
 * <h2>Usage Examples</h2>
 *
 * <pre>
 * // Export single net to PNML string
 * YNet net = specification.getRootNet();
 * String pnml = PnmlExporter.netToPnml(net);
 *
 * // Export entire specification with all decompositions
 * String pnml = PnmlExporter.specificationToPnml(specification);
 * </pre>
 *
 * @author YAWL Foundation
 * @version 5.2
 * @see YNet
 * @see YTask
 * @see YCondition
 * @see YSpecification
 */
public final class PnmlExporter {

    private PnmlExporter() {
        // Utility class, no instantiation
    }

    /**
     * Export a single YAWL net to PNML XML string.
     *
     * @param net the YNet to export (must have input and output conditions)
     * @return PNML XML string representing the net
     * @throws IllegalArgumentException if net is null or missing required conditions
     */
    public static String netToPnml(YNet net) {
        if (net == null) {
            throw new IllegalArgumentException("Net cannot be null");
        }
        if (net.getInputCondition() == null || net.getOutputCondition() == null) {
            throw new IllegalArgumentException("Net must have input and output conditions");
        }

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<pnml xmlns=\"http://www.pnml.org/version-2009/grammar/pnml\">\n");
        xml.append(String.format("  <net id=\"%s\" type=\"http://www.pnml.org/version-2009/grammar/ptnet\">\n",
                escapeXmlId(net.getID())));

        appendNetName(xml, net);
        appendPlaces(xml, net);
        appendTransitions(xml, net);
        appendArcs(xml, net);

        xml.append("  </net>\n");
        xml.append("</pnml>\n");

        return xml.toString();
    }

    /**
     * Export a complete YAWL specification to PNML format.
     *
     * <p>This exports all nets (root net and sub-nets from decompositions) as separate
     * PNML nets within a single document.</p>
     *
     * @param specification the YSpecification to export
     * @return PNML XML string containing all nets in the specification
     * @throws IllegalArgumentException if specification or root net is null
     */
    public static String specificationToPnml(YSpecification specification) {
        if (specification == null) {
            throw new IllegalArgumentException("Specification cannot be null");
        }

        YNet rootNet = specification.getRootNet();
        if (rootNet == null) {
            throw new IllegalArgumentException("Specification must have a root net");
        }

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<pnml xmlns=\"http://www.pnml.org/version-2009/grammar/pnml\">\n");

        // Export root net first
        appendNetElement(xml, rootNet);

        // Export all decomposition nets
        for (YExternalNetElement element : rootNet.getNetElements().values()) {
            if (element instanceof YCompositeTask compositeTask) {
                YNet decompositionNet = (YNet) compositeTask.getDecompositionPrototype();
                if (decompositionNet != null) {
                    appendNetElement(xml, decompositionNet);
                }
            }
        }

        xml.append("</pnml>\n");

        return xml.toString();
    }

    /**
     * Append a complete net element to the output XML.
     */
    private static void appendNetElement(StringBuilder xml, YNet net) {
        xml.append(String.format("  <net id=\"%s\" type=\"http://www.pnml.org/version-2009/grammar/ptnet\">\n",
                escapeXmlId(net.getID())));

        appendNetName(xml, net);
        appendPlaces(xml, net);
        appendTransitions(xml, net);
        appendArcs(xml, net);

        xml.append("  </net>\n");
    }

    /**
     * Append the net's name element.
     */
    private static void appendNetName(StringBuilder xml, YNet net) {
        String name = net.getName();
        if (name == null || name.isEmpty()) {
            name = net.getID();
        }
        xml.append("    <name><text>");
        xml.append(escapeXml(name));
        xml.append("</text></name>\n");
    }

    /**
     * Append all places (conditions) to the PNML document.
     */
    private static void appendPlaces(StringBuilder xml, YNet net) {
        for (YExternalNetElement element : net.getNetElements().values()) {
            if (element instanceof YCondition condition) {
                appendPlace(xml, condition, net);
            }
        }
    }

    /**
     * Append a single place element.
     */
    private static void appendPlace(StringBuilder xml, YCondition condition, YNet net) {
        String placeId = "p_" + escapeXmlId(condition.getID());

        xml.append(String.format("    <place id=\"%s\">\n", placeId));

        String placeName = condition.getName();
        if (placeName == null || placeName.isEmpty()) {
            placeName = condition.getID();
        }
        xml.append("      <name><text>");
        xml.append(escapeXml(placeName));
        xml.append("</text></name>\n");

        // Input condition gets initial marking of 1 token
        if (condition instanceof YInputCondition) {
            xml.append("      <initialMarking><text>1</text></initialMarking>\n");
        }

        xml.append("    </place>\n");
    }

    /**
     * Append all transitions (tasks) to the PNML document.
     */
    private static void appendTransitions(StringBuilder xml, YNet net) {
        for (YExternalNetElement element : net.getNetElements().values()) {
            if (element instanceof YTask task) {
                appendTransition(xml, task);
            }
        }
    }

    /**
     * Append a single transition element with YAWL-specific annotations.
     */
    private static void appendTransition(StringBuilder xml, YTask task) {
        String transitionId = "t_" + escapeXmlId(task.getID());

        xml.append(String.format("    <transition id=\"%s\">\n", transitionId));

        String taskName = task.getName();
        if (taskName == null || taskName.isEmpty()) {
            taskName = task.getID();
        }
        xml.append("      <name><text>");
        xml.append(escapeXml(taskName));
        xml.append("</text></name>\n");

        // Add YAWL-specific annotations for split/join types
        appendYawlToolSpecific(xml, task);

        xml.append("    </transition>\n");
    }

    /**
     * Append YAWL toolspecific annotations for split and join types.
     */
    private static void appendYawlToolSpecific(StringBuilder xml, YTask task) {
        xml.append("      <toolspecific tool=\"yawl\" version=\"4.0\">\n");

        String joinType = getJoinTypeString(task.getJoinType());
        String splitType = getSplitTypeString(task.getSplitType());

        xml.append("        <joinType>");
        xml.append(joinType);
        xml.append("</joinType>\n");

        xml.append("        <splitType>");
        xml.append(splitType);
        xml.append("</splitType>\n");

        if (task instanceof YCompositeTask) {
            xml.append("        <composite>true</composite>\n");
        } else if (task instanceof YAtomicTask) {
            xml.append("        <composite>false</composite>\n");
        }

        xml.append("      </toolspecific>\n");
    }

    /**
     * Convert a join type integer to string representation.
     */
    private static String getJoinTypeString(int joinType) {
        return switch (joinType) {
            case YTask._AND -> "and";
            case YTask._OR -> "or";
            case YTask._XOR -> "xor";
            default -> "unknown";
        };
    }

    /**
     * Convert a split type integer to string representation.
     */
    private static String getSplitTypeString(int splitType) {
        return switch (splitType) {
            case YTask._AND -> "and";
            case YTask._OR -> "or";
            case YTask._XOR -> "xor";
            default -> "unknown";
        };
    }

    /**
     * Append all arcs (flows) to the PNML document.
     *
     * <p>Arcs are created from the postset flows of all conditions and transitions.
     * This ensures each flow is represented exactly once.</p>
     */
    private static void appendArcs(StringBuilder xml, YNet net) {
        Set<String> arcIds = new HashSet<>();
        AtomicInteger arcCounter = new AtomicInteger(0);

        for (YExternalNetElement element : net.getNetElements().values()) {
            for (YExternalNetElement nextElement : element.getPostsetElements()) {
                String arcId = generateArcId(element, nextElement, arcCounter, arcIds);
                appendArc(xml, arcId, element, nextElement);
            }
        }
    }

    /**
     * Generate a unique arc ID and track it to avoid duplicates.
     */
    private static String generateArcId(YExternalNetElement source, YExternalNetElement target,
                                        AtomicInteger counter, Set<String> usedIds) {
        String sourceEscaped = escapeXmlId(source.getID());
        String targetEscaped = escapeXmlId(target.getID());
        String baseId = "a_" + sourceEscaped + "_" + targetEscaped;

        if (!usedIds.contains(baseId)) {
            usedIds.add(baseId);
            return baseId;
        }

        // Handle multiple arcs between same source/target
        String id;
        int index = 1;
        do {
            id = baseId + "_" + index;
            index++;
        } while (usedIds.contains(id));

        usedIds.add(id);
        return id;
    }

    /**
     * Append a single arc element.
     */
    private static void appendArc(StringBuilder xml, String arcId,
                                   YExternalNetElement source, YExternalNetElement target) {
        String sourceId = getElementPnmlId(source);
        String targetId = getElementPnmlId(target);

        xml.append(String.format("    <arc id=\"%s\" source=\"%s\" target=\"%s\">\n",
                arcId, sourceId, targetId));

        xml.append("      <inscription><text>1</text></inscription>\n");

        xml.append("    </arc>\n");
    }

    /**
     * Get the PNML element ID for a YAWL net element (place or transition).
     */
    private static String getElementPnmlId(YExternalNetElement element) {
        String prefix = (element instanceof YCondition) ? "p_" : "t_";
        return prefix + escapeXmlId(element.getID());
    }

    /**
     * Escape XML special characters in text content.
     */
    private static String escapeXml(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Text cannot be null for XML escaping");
        }
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * Escape XML special characters in element IDs.
     *
     * <p>XML IDs must start with a letter or underscore, contain only alphanumeric
     * characters, hyphens, underscores, and periods. This method escapes invalid
     * characters using underscore encoding.</p>
     */
    private static String escapeXmlId(String id) {
        if (id == null || id.isEmpty()) {
            return "unknown";
        }

        StringBuilder escaped = new StringBuilder();

        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);

            // First character must be letter, underscore, or colon (we use underscore)
            if (i == 0) {
                if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_') {
                    escaped.append(c);
                } else {
                    escaped.append('_');
                }
            } else {
                // Subsequent characters can be letters, digits, underscores, hyphens, periods
                if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
                    (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.') {
                    escaped.append(c);
                } else {
                    // Replace invalid characters with underscore + hex code
                    escaped.append('_').append(String.format("%04X", (int) c));
                }
            }
        }

        return escaped.toString();
    }

    /**
     * Get the join type from a task.
     *
     * @param task the task to query
     * @return the join type constant (AND, OR, or XOR)
     */
    public static int getJoinType(YTask task) {
        return task.getJoinType();
    }

    /**
     * Get the split type from a task.
     *
     * @param task the task to query
     * @return the split type constant (AND, OR, or XOR)
     */
    public static int getSplitType(YTask task) {
        return task.getSplitType();
    }
}
