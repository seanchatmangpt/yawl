/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.mining.generators;

import org.yawlfoundation.yawl.ggen.mining.model.Arc;
import org.yawlfoundation.yawl.ggen.mining.model.PetriNet;
import org.yawlfoundation.yawl.ggen.mining.model.Place;
import org.yawlfoundation.yawl.ggen.mining.model.Transition;

import java.util.Collection;
import java.util.List;

/**
 * Converts a {@link PetriNet} to a YAWL v4 XML specification string.
 *
 * <p>The output conforms to the YAWL v4 XML schema. Mapping rules:
 * <ul>
 *   <li>Initial places ({@link Place#isInitialPlace()}) → {@code <inputCondition>}</li>
 *   <li>Final places ({@link Place#isFinalPlace()}) → {@code <outputCondition>}</li>
 *   <li>All other places → {@code <condition>}</li>
 *   <li>Start transitions (no incoming arcs) → treated as regular tasks unless
 *       the net has no separate start place (then the first task receives flow
 *       from the generated inputCondition)</li>
 *   <li>Gateway transitions (multiple outgoing arcs) → tasks with XOR split type</li>
 *   <li>All transitions → {@code <task>} with a {@code <decomposesTo>} reference
 *       and a corresponding {@code <decomposition xsi:type="WebServiceGatewayFactsType"/>}</li>
 *   <li>Every arc → a {@code <flowsInto>/<nextElementRef>} on the source element</li>
 * </ul>
 *
 * <p>The exporter is stateless: call {@link #export(PetriNet)} repeatedly with
 * different nets without side effects.
 *
 * <p>Follows the same pattern as {@link CamundaBpmnExporter}: a single export method,
 * StringBuilder-based XML construction, strict null checks.
 */
public class YawlSpecExporter {

    private static final String YAWL_NS =
            "http://www.yawl.edu.au/schema/YAWLSchema";
    private static final String XSI_NS =
            "http://www.w3.org/2001/XMLSchema-instance";

    /**
     * Exports a PetriNet to a YAWL v4 XML specification string.
     *
     * @param model the PetriNet to export; must not be null
     * @return a valid YAWL XML specification string
     * @throws IllegalArgumentException if model is null
     * @throws YawlExportException      if the net has no initial place (input condition)
     *                                  or no final place (output condition)
     */
    public String export(PetriNet model) {
        if (model == null) {
            throw new IllegalArgumentException("PetriNet model cannot be null");
        }

        // Identify input and output places
        List<Place> inputPlaces = model.getPlaces().values().stream()
                .filter(Place::isInitialPlace)
                .toList();
        List<Place> outputPlaces = model.getPlaces().values().stream()
                .filter(Place::isFinalPlace)
                .toList();

        if (inputPlaces.isEmpty()) {
            throw new YawlExportException(
                    "Net '" + model.getName() + "' has no input condition: " +
                    "at least one place must have no incoming arcs and initialMarking > 0");
        }
        if (outputPlaces.isEmpty()) {
            throw new YawlExportException(
                    "Net '" + model.getName() + "' has no output condition: " +
                    "at least one place must have no outgoing arcs");
        }

        // Use the first found input/output place (sorted by id for determinism)
        Place inputPlace = inputPlaces.stream()
                .min((a, b) -> a.getId().compareTo(b.getId()))
                .orElseThrow();
        Place outputPlace = outputPlaces.stream()
                .min((a, b) -> a.getId().compareTo(b.getId()))
                .orElseThrow();

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<specificationSet\n");
        xml.append("    xmlns=\"").append(YAWL_NS).append("\"\n");
        xml.append("    xmlns:xsi=\"").append(XSI_NS).append("\"\n");
        xml.append("    version=\"4.0\">\n\n");

        xml.append("  <specification uri=\"").append(escapeXml(model.getId()))
                .append("\" version=\"0.1\">\n");
        xml.append("    <metaData>\n");
        xml.append("      <title>").append(escapeXml(model.getName())).append("</title>\n");
        xml.append("      <creator>ggen</creator>\n");
        xml.append("      <version>0.1</version>\n");
        xml.append("    </metaData>\n\n");

        // Root net decomposition
        xml.append("    <decomposition id=\"").append(escapeXml(model.getId()))
                .append("\" isRootNet=\"true\" xsi:type=\"NetFactsType\">\n");
        xml.append("      <processControlElements>\n\n");

        // inputCondition
        xml.append(buildInputCondition(inputPlace));

        // outputCondition
        xml.append(buildOutputCondition(outputPlace));

        // Intermediate conditions (places that are neither input nor output)
        for (Place place : model.getPlaces().values()) {
            if (!place.isInitialPlace() && !place.isFinalPlace()) {
                xml.append(buildCondition(place));
            }
        }

        // Tasks
        for (Transition transition : model.getTransitions().values()) {
            xml.append(buildTask(transition));
        }

        xml.append("      </processControlElements>\n");
        xml.append("    </decomposition>\n\n");

        // WebService decompositions for each task
        for (Transition transition : model.getTransitions().values()) {
            xml.append("    <decomposition id=\"").append(escapeXml(transition.getId()))
                    .append("\" xsi:type=\"WebServiceGatewayFactsType\"/>\n");
        }

        xml.append("  </specification>\n\n");
        xml.append("</specificationSet>\n");

        return xml.toString();
    }

    private String buildInputCondition(Place place) {
        StringBuilder sb = new StringBuilder();
        sb.append("        <inputCondition id=\"").append(escapeXml(place.getId())).append("\">\n");
        sb.append("          <name>").append(escapeXml(place.getName())).append("</name>\n");
        appendFlowsInto(sb, place.getOutgoingArcs());
        sb.append("        </inputCondition>\n\n");
        return sb.toString();
    }

    private String buildOutputCondition(Place place) {
        StringBuilder sb = new StringBuilder();
        sb.append("        <outputCondition id=\"").append(escapeXml(place.getId())).append("\">\n");
        sb.append("          <name>").append(escapeXml(place.getName())).append("</name>\n");
        sb.append("        </outputCondition>\n\n");
        return sb.toString();
    }

    private String buildCondition(Place place) {
        StringBuilder sb = new StringBuilder();
        sb.append("        <condition id=\"").append(escapeXml(place.getId())).append("\">\n");
        sb.append("          <name>").append(escapeXml(place.getName())).append("</name>\n");
        appendFlowsInto(sb, place.getOutgoingArcs());
        sb.append("        </condition>\n\n");
        return sb.toString();
    }

    private String buildTask(Transition transition) {
        StringBuilder sb = new StringBuilder();
        sb.append("        <task id=\"").append(escapeXml(transition.getId())).append("\">\n");
        sb.append("          <name>").append(escapeXml(transition.getName())).append("</name>\n");

        if (transition.isGateway()) {
            sb.append("          <split code=\"XOR\"/>\n");
        }

        appendFlowsInto(sb, transition.getOutgoingArcs());

        sb.append("          <decomposesTo id=\"").append(escapeXml(transition.getId()))
                .append("\"/>\n");
        sb.append("        </task>\n\n");
        return sb.toString();
    }

    private void appendFlowsInto(StringBuilder sb, Collection<Arc> outgoingArcs) {
        if (outgoingArcs.isEmpty()) {
            return;
        }
        sb.append("          <flowsInto>\n");
        for (Arc arc : outgoingArcs) {
            sb.append("            <nextElementRef id=\"")
                    .append(escapeXml(arc.getTarget().getId())).append("\"/>\n");
        }
        sb.append("          </flowsInto>\n");
    }

    private String escapeXml(String str) {
        if (str == null) {
            throw new IllegalArgumentException("Cannot escape null string: element ID cannot be null");
        }
        return str.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
