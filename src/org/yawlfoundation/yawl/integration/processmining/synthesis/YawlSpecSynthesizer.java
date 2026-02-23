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

package org.yawlfoundation.yawl.integration.processmining.synthesis;

import org.yawlfoundation.yawl.integration.processmining.pnml.PnmlArc;
import org.yawlfoundation.yawl.integration.processmining.pnml.PnmlPlace;
import org.yawlfoundation.yawl.integration.processmining.pnml.PnmlProcess;
import org.yawlfoundation.yawl.integration.processmining.pnml.PnmlTransition;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Synthesizes YAWL specification XML from a parsed PNML process.
 * Converts Petri Net concepts to YAWL workflow constructs:
 * - PNML place → YAWL condition
 * - PNML transition → YAWL task
 * - PNML arc → YAWL flow
 * - Start place → input condition
 * - End place(s) → output condition
 *
 * @author YAWL Foundation
 * @version 6.0
 */
public final class YawlSpecSynthesizer {

    private final String specUri;
    private final String specName;

    /**
     * Creates a synthesizer with specification metadata.
     *
     * @param specUri  URI identifier for the specification (e.g., "http://example.com/spec")
     * @param specName Human-readable name for the specification
     * @throws IllegalArgumentException if specUri or specName are null/empty
     */
    public YawlSpecSynthesizer(String specUri, String specName) {
        if (specUri == null || specUri.isEmpty()) {
            throw new IllegalArgumentException("Specification URI cannot be null or empty");
        }
        if (specName == null || specName.isEmpty()) {
            throw new IllegalArgumentException("Specification name cannot be null or empty");
        }
        this.specUri = specUri;
        this.specName = specName;
    }

    /**
     * Synthesizes a YAWL specification XML string from a PNML process.
     *
     * @param process Parsed PNML process
     * @return YAWL specification XML as string
     * @throws IllegalArgumentException if process is invalid
     */
    public String synthesize(PnmlProcess process) {
        if (!process.isValid()) {
            throw new IllegalArgumentException("Process is not structurally valid");
        }

        StringBuilder xml = new StringBuilder();

        // XML declaration and root element
        xml.append("""
            <?xml version="1.0" encoding="UTF-8"?>
            <specificationSet xmlns="http://www.yawlfoundation.org/yawlschema"
                              xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                              version="4.0"
                              xsi:schemaLocation="http://www.yawlfoundation.org/yawlschema
                                                  http://www.yawlfoundation.org/yawlschema/YAWL_Schema4.0.xsd">
            """);

        // Specification element
        xml.append("  <specification uri=\"").append(escapeXml(specUri)).append("\">\n");
        xml.append("    <name>").append(escapeXml(specName)).append("</name>\n");
        xml.append("    <documentation>Auto-generated from PNML by process mining</documentation>\n");

        // Metadata
        xml.append("    <metaData>\n");
        xml.append("      <title>").append(escapeXml(specName)).append("</title>\n");
        xml.append("      <creator>YAWL Process Mining</creator>\n");
        xml.append("      <description>Synthesized from ").append(escapeXml(process.name())).append("</description>\n");
        xml.append("      <version>1.0</version>\n");
        xml.append("      <identifier>UID_").append(UUID.randomUUID()).append("</identifier>\n");
        xml.append("    </metaData>\n");

        // Empty schema
        xml.append("    <schema xmlns=\"http://www.w3.org/2001/XMLSchema\" />\n");

        // Root net decomposition
        String netId = sanitizeId(process.name());
        xml.append("    <decomposition id=\"").append(netId).append("\" isRootNet=\"true\" xsi:type=\"NetFactsType\">\n");

        // Local variables from places
        synthesizeLocalVariables(xml, process);

        // Process control elements
        xml.append("      <processControlElements>\n");

        // Input condition (from start place)
        synthesizeInputCondition(xml, process);

        // Tasks from transitions
        synthesizeTasks(xml, process);

        // Output condition (to end places)
        synthesizeOutputCondition(xml, process);

        xml.append("      </processControlElements>\n");

        xml.append("    </decomposition>\n");
        xml.append("  </specification>\n");
        xml.append("</specificationSet>\n");

        return xml.toString();
    }

    /**
     * Synthesizes with conformance analysis and timing.
     *
     * @param process Parsed PNML process
     * @return SynthesisResult with YAWL XML and metrics
     * @throws IllegalArgumentException if process is invalid
     */
    public SynthesisResult synthesizeWithConformance(PnmlProcess process) {
        if (!process.isValid()) {
            throw new IllegalArgumentException("Process is not structurally valid");
        }

        Instant start = Instant.now();

        String yawlXml = synthesize(process);

        Instant end = Instant.now();
        Duration elapsed = Duration.between(start, end);

        // Count observable transitions as tasks
        int tasksGenerated = (int) process.transitions().stream()
            .filter(PnmlTransition::isObservable)
            .count();

        // Count places as conditions (plus input/output conditions)
        int conditionsGenerated = process.places().size() + 2;

        ConformanceScore score = ConformanceScore.fromProcess(process);

        return new SynthesisResult(yawlXml, score, process, elapsed, tasksGenerated, conditionsGenerated);
    }

    /**
     * Synthesizes local variable declarations from places.
     * Each place becomes a variable holding the current number of tokens.
     *
     * @param xml     StringBuilder to append to
     * @param process Source process
     */
    private void synthesizeLocalVariables(StringBuilder xml, PnmlProcess process) {
        for (PnmlPlace place : process.places()) {
            xml.append("      <localVariable>\n");
            xml.append("        <index>0</index>\n");
            xml.append("        <name>").append(escapeXml(place.name())).append("</name>\n");
            xml.append("        <type>int</type>\n");
            xml.append("        <namespace>http://www.w3.org/2001/XMLSchema</namespace>\n");
            xml.append("        <initialValue>").append(place.initialMarking()).append("</initialValue>\n");
            xml.append("      </localVariable>\n");
        }
    }

    /**
     * Synthesizes input condition (flows from start place to first transitions).
     *
     * @param xml     StringBuilder to append to
     * @param process Source process
     */
    private void synthesizeInputCondition(StringBuilder xml, PnmlProcess process) {
        PnmlPlace startPlace = process.startPlace();
        List<PnmlArc> outgoingArcs = process.outgoingArcs(startPlace.id());

        xml.append("        <inputCondition id=\"InputCondition\">\n");
        xml.append("          <flowsInto>\n");

        for (PnmlArc arc : outgoingArcs) {
            Optional<PnmlTransition> targetTransition = process.transitionById(arc.targetId());
            if (targetTransition.isPresent()) {
                String taskId = sanitizeId(targetTransition.get().name());
                xml.append("            <nextElementRef id=\"").append(taskId).append("\" />\n");
            }
        }

        xml.append("          </flowsInto>\n");
        xml.append("        </inputCondition>\n");
    }

    /**
     * Synthesizes task elements (one per observable transition).
     *
     * @param xml     StringBuilder to append to
     * @param process Source process
     */
    private void synthesizeTasks(StringBuilder xml, PnmlProcess process) {
        for (PnmlTransition transition : process.transitions()) {
            if (!transition.isObservable()) {
                continue;  // Skip silent transitions
            }

            String taskId = sanitizeId(transition.name());

            xml.append("        <task id=\"").append(taskId).append("\">\n");
            xml.append("          <name>").append(escapeXml(transition.name())).append("</name>\n");

            // Flows from this task to downstream transitions
            xml.append("          <flowsInto>\n");
            List<PnmlArc> outgoingArcs = process.outgoingArcs(transition.id());
            List<String> targetTransitions = outgoingArcs.stream()
                .map(arc -> {
                    Optional<PnmlTransition> target = process.transitionById(arc.targetId());
                    return target.map(t -> sanitizeId(t.name())).orElse(null);
                })
                .filter(id -> id != null)
                .distinct()
                .collect(Collectors.toList());

            if (targetTransitions.isEmpty()) {
                // Task flows to output condition
                xml.append("            <nextElementRef id=\"OutputCondition\" />\n");
            } else {
                for (String targetId : targetTransitions) {
                    xml.append("            <nextElementRef id=\"").append(targetId).append("\" />\n");
                }
            }

            xml.append("          </flowsInto>\n");

            // Simple split and join (XOR by default; AND for parallelism)
            xml.append("          <join code=\"xor\" />\n");
            xml.append("          <split code=\"xor\" />\n");

            xml.append("        </task>\n");
        }
    }

    /**
     * Synthesizes output condition (flows from end transitions to sink).
     *
     * @param xml     StringBuilder to append to
     * @param process Source process
     */
    private void synthesizeOutputCondition(StringBuilder xml, PnmlProcess process) {
        xml.append("        <outputCondition id=\"OutputCondition\">\n");
        xml.append("        </outputCondition>\n");
    }

    /**
     * Sanitizes a string to be a valid YAWL element ID.
     * Removes spaces, special characters, and leading numbers.
     *
     * @param name Input string
     * @return sanitized ID
     */
    private String sanitizeId(String name) {
        if (name == null || name.isEmpty()) {
            return "Element";
        }

        // Replace spaces and special chars with underscores
        String sanitized = name.replaceAll("[^a-zA-Z0-9_]", "_");

        // Remove leading digits
        sanitized = sanitized.replaceAll("^[0-9]+", "");

        // Ensure non-empty
        if (sanitized.isEmpty()) {
            sanitized = "Element";
        }

        return sanitized;
    }

    /**
     * Escapes XML special characters.
     *
     * @param text Input text
     * @return escaped for XML
     * @throws IllegalArgumentException if text is null
     */
    private String escapeXml(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Text to escape cannot be null");
        }
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }
}
