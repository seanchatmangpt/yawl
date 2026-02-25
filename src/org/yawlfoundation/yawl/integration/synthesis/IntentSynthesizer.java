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
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.integration.synthesis;

import org.yawlfoundation.yawl.integration.autonomous.marketplace.OxigraphSparqlEngine;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.SparqlEngineException;
import org.yawlfoundation.yawl.integration.autonomous.marketplace.SparqlEngineUnavailableException;
import org.yawlfoundation.yawl.integration.verification.SoundnessVerifier;
import org.yawlfoundation.yawl.integration.verification.VerificationReport;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Synthesizes YAWL workflow specifications from business intent using SPARQL queries
 * and soundness verification.
 *
 * <p>The synthesizer performs the following steps:
 * <ol>
 *   <li>Serializes intent to Turtle RDF and loads into the SPARQL engine</li>
 *   <li>Runs CONSTRUCT queries to extract net structure hints</li>
 *   <li>Builds adjacency maps representing the Petri net</li>
 *   <li>Validates soundness using SoundnessVerifier</li>
 *   <li>Generates a descriptive XML specification</li>
 * </ol>
 *
 * <p>The synthesizer gracefully handles engine unavailability by throwing
 * {@link SparqlEngineUnavailableException}. Callers must check
 * {@link #canSynthesize()} before attempting synthesis.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class IntentSynthesizer {

    private final OxigraphSparqlEngine engine;

    /**
     * Constructs an IntentSynthesizer with the given SPARQL engine.
     *
     * @param engine the Oxigraph SPARQL engine (non-null)
     * @throws NullPointerException if engine is null
     */
    public IntentSynthesizer(OxigraphSparqlEngine engine) {
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
    }

    /**
     * Synthesizes a YAWL workflow from business intent.
     *
     * <p>Uses SPARQL CONSTRUCT chain to derive net structure, builds adjacency maps,
     * verifies soundness, and generates specification XML.</p>
     *
     * @param intent the business intent (non-null)
     * @return synthesis result with generated spec and soundness report
     * @throws SparqlEngineException if engine unavailable or query execution fails
     * @throws NullPointerException if intent is null
     */
    public SynthesisResult synthesize(WorkflowIntent intent) throws SparqlEngineException {
        Objects.requireNonNull(intent, "intent must not be null");

        Instant startTime = Instant.now();

        try {
            // Load intent RDF into engine
            String intentTurtle = IntentRdfSerializer.toTurtle(intent);
            engine.loadTurtle(intentTurtle);

            // Extract WCP hints via CONSTRUCT
            String wcpGraph = engine.constructToTurtle(SynthesisConstructQueries.SELECT_WCP_FOR_INTENT);
            List<String> wcpPatternsUsed = extractWcpPatterns(wcpGraph);

            // Build sequential net from activities
            Map<String, Set<String>> placeToTransitions = new HashMap<>();
            Map<String, Set<String>> transitionToPlaces = new HashMap<>();
            buildSequentialNet(intent.activities(), placeToTransitions, transitionToPlaces);

            // Run soundness verification
            VerificationReport soundnessReport = null;
            if (!placeToTransitions.isEmpty() && !transitionToPlaces.isEmpty()) {
                SoundnessVerifier verifier = new SoundnessVerifier(
                    placeToTransitions,
                    transitionToPlaces,
                    "p_start",
                    "p_end"
                );
                soundnessReport = verifier.verify();
            }

            // Generate specification XML
            String specXml = buildSpecXml(intent, wcpPatternsUsed);

            Duration elapsed = Duration.between(startTime, Instant.now());
            boolean successful = soundnessReport == null || soundnessReport.isSound();

            return new SynthesisResult(
                specXml,
                soundnessReport,
                wcpPatternsUsed,
                elapsed,
                successful
            );

        } catch (SparqlEngineUnavailableException e) {
            throw e;
        } catch (SparqlEngineException e) {
            Duration elapsed = Duration.between(startTime, Instant.now());
            throw new SparqlEngineException("Synthesis failed: " + e.getMessage(), e);
        }
    }

    /**
     * Returns true if the backing SPARQL engine is available.
     *
     * @return true iff engine is reachable
     */
    public boolean canSynthesize() {
        return engine.isAvailable();
    }

    /**
     * Builds a sequential Petri net from an ordered list of activities.
     *
     * <p>Creates:
     * <ul>
     *   <li>Places: p_start, p_0, p_1, ..., p_{n-1}, p_end</li>
     *   <li>Transitions: t_{activity} for each activity</li>
     *   <li>Arcs: linear sequence with no branching</li>
     * </ul>
     *
     * @param activities ordered activity names
     * @param placeToTransitions output map: place ID → set of output transition IDs
     * @param transitionToPlaces output map: transition ID → set of output place IDs
     */
    private void buildSequentialNet(
        List<String> activities,
        Map<String, Set<String>> placeToTransitions,
        Map<String, Set<String>> transitionToPlaces
    ) {
        // Initialize start place
        String currentPlace = "p_start";
        placeToTransitions.put(currentPlace, new HashSet<>());

        // Create transition for each activity
        for (int i = 0; i < activities.size(); i++) {
            String activity = activities.get(i);
            String transitionId = "t_" + i + "_" + sanitizeActivityName(activity);
            String nextPlace = "p_" + i;

            // Add arc: currentPlace → transition
            placeToTransitions.computeIfAbsent(currentPlace, k -> new HashSet<>()).add(transitionId);

            // Add arc: transition → nextPlace
            transitionToPlaces.put(transitionId, new HashSet<>(Set.of(nextPlace)));
            placeToTransitions.put(nextPlace, new HashSet<>());

            currentPlace = nextPlace;
        }

        // Add final place and connect last transition to it
        String endPlace = "p_end";
        placeToTransitions.put(endPlace, new HashSet<>());
        if (!activities.isEmpty()) {
            String lastTransitionId = transitionToPlaces.keySet().stream()
                .max(Comparator.naturalOrder())
                .orElse("t_0");
            transitionToPlaces.put(lastTransitionId, new HashSet<>(Set.of(endPlace)));
        }
    }

    /**
     * Builds a descriptive YAWL specification XML from the intent and applied patterns.
     *
     * <p>Generates a simple XML structure (not full YAWL schema) describing the workflow.</p>
     *
     * @param intent the original intent
     * @param wcpPatternsUsed list of WCP patterns applied
     * @return XML string describing the workflow
     */
    private String buildSpecXml(WorkflowIntent intent, List<String> wcpPatternsUsed) {
        StringBuilder xml = new StringBuilder();

        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<yawlSpec goal=\"").append(escapeXml(intent.goal())).append("\">\n");
        xml.append("  <net id=\"Net_1\">\n");
        xml.append("    <place id=\"p_start\" type=\"inputCondition\"/>\n");

        // Add tasks for each activity
        for (int i = 0; i < intent.activities().size(); i++) {
            String activity = intent.activities().get(i);
            String taskId = "t_" + i + "_" + sanitizeActivityName(activity);
            xml.append("    <task id=\"").append(taskId)
                .append("\" label=\"").append(escapeXml(activity)).append("\"/>\n");
        }

        xml.append("    <place id=\"p_end\" type=\"outputCondition\"/>\n");
        xml.append("  </net>\n");

        if (!wcpPatternsUsed.isEmpty()) {
            xml.append("  <wcpPatterns>\n");
            for (String pattern : wcpPatternsUsed) {
                xml.append("    <pattern>").append(escapeXml(pattern)).append("</pattern>\n");
            }
            xml.append("  </wcpPatterns>\n");
        }

        xml.append("</yawlSpec>\n");

        return xml.toString();
    }

    /**
     * Extracts WCP pattern identifiers from the CONSTRUCT result graph.
     *
     * <p>Simple heuristic: looks for strings matching "WCP-\\d+" pattern.</p>
     *
     * @param graph Turtle RDF graph string
     * @return list of found WCP pattern IDs
     */
    private List<String> extractWcpPatterns(String graph) {
        List<String> patterns = new ArrayList<>();
        String[] lines = graph.split("\n");
        for (String line : lines) {
            if (line.contains("intent:wcpHint")) {
                // Extract quoted string value
                int startQuote = line.indexOf('"');
                int endQuote = line.lastIndexOf('"');
                if (startQuote >= 0 && endQuote > startQuote) {
                    String value = line.substring(startQuote + 1, endQuote);
                    if (value.matches("WCP-\\d+")) {
                        patterns.add(value);
                    }
                }
            }
        }
        return patterns.isEmpty() ? List.of("WCP-1") : patterns;
    }

    /**
     * Sanitizes an activity name for use in XML IDs.
     *
     * @param activity the activity name
     * @return sanitized name suitable for XML id attribute
     */
    private String sanitizeActivityName(String activity) {
        return activity.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
    }

    /**
     * Escapes a string for safe use in XML attribute values.
     *
     * @param value the value to escape
     * @return escaped value
     */
    private String escapeXml(String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }
}
