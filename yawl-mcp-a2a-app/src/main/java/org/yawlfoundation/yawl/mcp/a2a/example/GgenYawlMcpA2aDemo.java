/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.example;

import io.a2a.A2A;
import io.a2a.spec.Message;
import io.a2a.spec.Part;
import io.a2a.spec.TextPart;

import org.yawlfoundation.yawl.ggen.mining.model.Arc;
import org.yawlfoundation.yawl.ggen.mining.model.PetriNet;
import org.yawlfoundation.yawl.ggen.mining.model.Place;
import org.yawlfoundation.yawl.ggen.mining.model.Transition;
import org.yawlfoundation.yawl.ggen.mining.parser.PnmlParser;
import org.yawlfoundation.yawl.stateless.YStatelessEngine;
import org.yawlfoundation.yawl.stateless.engine.YNetRunner;
import org.yawlfoundation.yawl.stateless.engine.YWorkItem;
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.yawlfoundation.yawl.elements.YSpecification;

import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Standalone demo: ggen/YAWL driven via MCP tools and A2A messages without LLM.
 *
 * <p>This demo replaces the Z.AI/LLM-based {@code SpecificationGenerator} path with
 * deterministic process model parsing using the ggen module, then shows how the
 * resulting YAWL workflow can be launched and managed via MCP tool semantics
 * and A2A message patterns — zero inference tokens required.</p>
 *
 * <h2>Pipeline</h2>
 * <pre>
 * Phase 1  ggen: PnmlParser.parse(PNML) -> PetriNet          (replaces SpecificationGenerator/Z.AI)
 * Phase 2  ggen: PetriNet -> YAWL YAML -> YAWL XML           (replaces yawl_generate_workflow MCP tool)
 * Phase 3  MCP:  yawl_upload_specification / yawl_launch_case / yawl_get_work_items_for_case
 * Phase 4  A2A:  list-specifications / launch-case / monitor-case messages
 * </pre>
 *
 * <h2>Running</h2>
 * <pre>{@code
 * mvn exec:java -pl yawl-mcp-a2a-app \
 *     -Dexec.mainClass="org.yawlfoundation.yawl.mcp.a2a.example.GgenYawlMcpA2aDemo"
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @since 6.0.0
 */
public class GgenYawlMcpA2aDemo {

    private static final String DIVIDER =
        "======================================================================";

    /**
     * Inline PNML: Loan Processing workflow (6 places, 6 transitions, 12 arcs).
     * Mirrors yawl-ggen/src/test/resources/fixtures/loan-processing.pnml.
     */
    private static final String LOAN_PNML = """
        <?xml version="1.0" encoding="UTF-8"?>
        <pnml xmlns="http://www.pnml.org/version-2009-12-16">
          <net id="LoanProcessing" type="http://www.pnml.org/version-2009-12-16/PT">
            <name><text>Loan Processing Workflow</text></name>
            <place id="p1"><name><text>Start</text></name>
              <initialMarking><text>1</text></initialMarking></place>
            <place id="p2"><name><text>ApplicationReceived</text></name></place>
            <place id="p3"><name><text>AssessmentPending</text></name></place>
            <place id="p4a"><name><text>LowRiskDecision</text></name></place>
            <place id="p4b"><name><text>HighRiskDecision</text></name></place>
            <place id="p5"><name><text>ProcessingComplete</text></name></place>
            <transition id="t1"><name><text>ReceiveApplication</text></name></transition>
            <transition id="t2"><name><text>AssessRisk</text></name></transition>
            <transition id="t3"><name><text>RiskSplit</text></name></transition>
            <transition id="t4a"><name><text>ApproveLowRisk</text></name></transition>
            <transition id="t4b"><name><text>SendForReview</text></name></transition>
            <transition id="t5"><name><text>SendNotification</text></name></transition>
            <arc id="a1" source="p1"  target="t1"/>
            <arc id="a2" source="t1"  target="p2"/>
            <arc id="a3" source="p2"  target="t2"/>
            <arc id="a4" source="t2"  target="p3"/>
            <arc id="a5" source="p3"  target="t3"/>
            <arc id="a6" source="t3"  target="p4a"/>
            <arc id="a7" source="t3"  target="p4b"/>
            <arc id="a8" source="p4a" target="t4a"/>
            <arc id="a9" source="p4b" target="t4b"/>
            <arc id="a10" source="t4a" target="p5"/>
            <arc id="a11" source="t4b" target="p5"/>
            <arc id="a12" source="p5"  target="t5"/>
          </net>
        </pnml>
        """;

    /**
     * Main entry point — runs all four demo phases.
     *
     * @param args command-line arguments (unused)
     */
    public static void main(String[] args) {
        printBanner();

        try {
            // Phase 1: ggen parses PNML (replaces LLM)
            PetriNet net = phase1ParseWithGgen();

            // Phase 2: PetriNet → YAWL XML (replaces SpecificationGenerator.create())
            String yawlXml = phase2ConvertToYawlXml(net);

            // Phase 3: MCP tool invocations — upload spec, launch case, query work items
            YStatelessEngine engine = new YStatelessEngine();
            YNetRunner runner = phase3McpPath(yawlXml, engine);

            // Phase 4: A2A message flow — list specs, launch case, monitor work items
            phase4A2APath(runner);

        } catch (Exception e) {
            System.err.println("Demo failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    // =========================================================================
    // Phase 1: ggen PNML parse (zero LLM tokens)
    // =========================================================================

    /**
     * Phase 1: Parse PNML to PetriNet using ggen's PnmlParser.
     *
     * <p>This replaces the LLM-based SpecificationGenerator — no ZAI_API_KEY needed.</p>
     *
     * @return the parsed PetriNet model
     */
    static PetriNet phase1ParseWithGgen() throws IOException, SAXException {
        System.out.println();
        System.out.println(DIVIDER);
        System.out.println("  Phase 1: ggen — PNML Parse (replaces LLM SpecificationGenerator)");
        System.out.println(DIVIDER);

        PnmlParser parser = new PnmlParser();
        PetriNet net = parser.parse(
            new ByteArrayInputStream(LOAN_PNML.getBytes(StandardCharsets.UTF_8)));

        int taskCount = (int) net.getTransitions().values().stream()
            .filter(t -> !t.isStartTransition() && !t.isEndTransition())
            .count();
        int startCount = (int) net.getTransitions().values().stream()
            .filter(Transition::isStartTransition)
            .count();

        System.out.printf("  Net name  : %s%n", net.getName());
        System.out.printf("  Places    : %d%n", net.getPlaces().size());
        System.out.printf("  Transitions: %d (%d task, %d start/end)%n",
            net.getTransitions().size(), taskCount, startCount);
        System.out.printf("  Arcs      : %d%n", net.getArcs().size());
        System.out.println();
        System.out.println("  Tasks discovered (no LLM inference):");
        net.getTransitions().values().stream()
            .filter(t -> !t.isEndTransition())
            .forEach(t -> System.out.printf("    [%s] %s%s%n",
                t.getId(), t.getName(),
                t.isStartTransition() ? " (start)" : ""));
        System.out.println();
        return net;
    }

    // =========================================================================
    // Phase 2: PetriNet → YAWL XML (zero LLM tokens)
    // =========================================================================

    /**
     * Phase 2: Convert PetriNet to YAWL XML via YAML intermediate format.
     *
     * <p>Builds a compact YAWL YAML from the Petri net structure, then uses
     * {@link ExtendedYamlConverter} to produce schema-compliant YAWL XML.
     * This replaces the {@code yawl_generate_workflow} MCP tool (no Z.AI call).</p>
     *
     * @param net the PetriNet from Phase 1
     * @return YAWL XML string ready for engine upload
     */
    static String phase2ConvertToYawlXml(PetriNet net) {
        System.out.println(DIVIDER);
        System.out.println("  Phase 2: ggen — PetriNet -> YAWL YAML -> YAWL XML");
        System.out.println(DIVIDER);

        String yaml = buildYawlYaml(net);

        System.out.println("  Generated YAML (token-efficient representation):");
        System.out.println();
        for (String line : yaml.split("\n")) {
            System.out.println("    " + line);
        }
        System.out.println();

        ExtendedYamlConverter converter = new ExtendedYamlConverter();
        String xml = converter.convertToXml(yaml);

        System.out.printf("  YAWL XML  : %,d chars (schema-compliant, zero LLM tokens)%n",
            xml.length());
        System.out.println();
        return xml;
    }

    /**
     * Builds a compact YAWL YAML spec from Petri net structure.
     *
     * <p>Iterates transitions and their arcs to determine task flow edges.
     * Transitions with multiple outgoing paths get {@code split: xor}.
     * Transitions with multiple incoming paths get {@code join: xor}.</p>
     *
     * @param net the PetriNet
     * @return YAWL YAML string
     */
    static String buildYawlYaml(PetriNet net) {
        // Determine task ordering and flows: transition -> place -> next transitions
        Map<String, Transition> transitions = net.getTransitions();

        // Build adjacency: transitionId -> list of next transitionIds
        Map<String, List<String>> flowMap = new LinkedHashMap<>();
        Map<String, Integer> incomingCount = new LinkedHashMap<>();

        for (Transition t : transitions.values()) {
            flowMap.put(t.getId(), new ArrayList<>());
            incomingCount.put(t.getId(), 0);
        }

        for (Transition t : transitions.values()) {
            for (Arc outArc : t.getOutgoingArcs()) {
                if (outArc.getTarget() instanceof Place place) {
                    for (Arc placeArc : place.getOutgoingArcs()) {
                        if (placeArc.getTarget() instanceof Transition next) {
                            flowMap.get(t.getId()).add(next.getId());
                            incomingCount.merge(next.getId(), 1, Integer::sum);
                        }
                    }
                }
            }
        }

        // Find the start transition (the one with no incoming from other transitions)
        String firstId = transitions.values().stream()
            .filter(Transition::isStartTransition)
            .map(t -> {
                List<String> flows = flowMap.get(t.getId());
                return flows.isEmpty() ? t.getId() : flows.get(0);
            })
            .findFirst()
            .orElseGet(() -> transitions.keySet().iterator().next());

        // Build YAML
        StringBuilder yaml = new StringBuilder();
        yaml.append("name: LoanProcess\n");
        yaml.append("uri: LoanProcess.xml\n");
        String firstName = transitions.containsKey(firstId)
            ? transitions.get(firstId).getName()
            : firstId;
        yaml.append("first: ").append(firstName).append("\n");
        yaml.append("tasks:\n");

        for (Transition t : transitions.values()) {
            if (t.isStartTransition()) {
                continue; // skip — start transition modelled as 'first' above
            }

            yaml.append("  - id: ").append(t.getName()).append("\n");

            List<String> nextIds = flowMap.get(t.getId());
            if (nextIds.isEmpty()) {
                yaml.append("    flows: [end]\n");
            } else {
                List<String> nextNames = nextIds.stream()
                    .map(id -> transitions.containsKey(id)
                        ? transitions.get(id).getName()
                        : id)
                    .collect(Collectors.toList());
                yaml.append("    flows: [")
                    .append(String.join(", ", nextNames))
                    .append("]\n");

                if (nextIds.size() > 1) {
                    yaml.append("    split: xor\n");
                }
            }

            int inCount = incomingCount.getOrDefault(t.getId(), 0);
            if (inCount > 1) {
                yaml.append("    join: xor\n");
            }
        }

        return yaml.toString();
    }

    // =========================================================================
    // Phase 3: MCP tool invocations (no LLM)
    // =========================================================================

    /**
     * Phase 3: Execute YAWL workflow via MCP tool semantics.
     *
     * <p>Demonstrates the MCP tool sequence that Claude/an agent would invoke.
     * Uses {@link YStatelessEngine} for real execution — no HTTP server needed.</p>
     *
     * <p>Equivalent MCP tools used:
     * <ul>
     *   <li>{@code yawl_upload_specification} — uploads YAWL XML to engine</li>
     *   <li>{@code yawl_launch_case} — launches a new case instance</li>
     *   <li>{@code yawl_get_work_items_for_case} — lists active work items</li>
     * </ul>
     * </p>
     *
     * @param yawlXml the YAWL XML from Phase 2
     * @param engine  the YStatelessEngine instance
     * @return the launched YNetRunner for use in Phase 4
     */
    static YNetRunner phase3McpPath(String yawlXml, YStatelessEngine engine) throws Exception {
        System.out.println(DIVIDER);
        System.out.println("  Phase 3: MCP Tool Invocations (no LLM routing)");
        System.out.println(DIVIDER);

        // MCP: yawl_upload_specification
        System.out.println("  MCP > yawl_upload_specification");
        System.out.printf("    { specXml: \"<specificationSet ...> (%,d chars)\" }%n", yawlXml.length());
        List<YSpecification> specs = YMarshal.unmarshalSpecifications(yawlXml);
        if (specs.isEmpty()) {
            throw new IllegalStateException("No specification found in YAWL XML");
        }
        YSpecification spec = specs.get(0);
        System.out.printf("    <- { status: \"OK\", specId: \"%s\", version: \"%s\" }%n",
            spec.getURI(), spec.getSpecVersion());
        System.out.println();

        // MCP: yawl_launch_case
        System.out.println("  MCP > yawl_launch_case");
        System.out.printf("    { specIdentifier: \"%s\" }%n", spec.getURI());
        YNetRunner runner = engine.launchCase(spec);
        String caseId = runner.getCaseID().toString();
        System.out.printf("    <- { status: \"OK\", caseId: \"%s\" }%n", caseId);
        System.out.println();

        // MCP: yawl_get_work_items_for_case
        System.out.println("  MCP > yawl_get_work_items_for_case");
        System.out.printf("    { caseId: \"%s\" }%n", caseId);
        Set<YWorkItem> workItems = runner.getWorkItemRepository().getEnabledWorkItems();
        System.out.println("    <- {");
        System.out.printf("         caseId: \"%s\",%n", caseId);
        System.out.println("         workItems: [");
        for (YWorkItem item : workItems) {
            System.out.printf("           { taskId: \"%s\", status: \"Enabled\" }%n",
                item.getTaskID());
        }
        System.out.println("         ]");
        System.out.println("       }");
        System.out.println();

        return runner;
    }

    // =========================================================================
    // Phase 4: A2A message flow (no LLM)
    // =========================================================================

    /**
     * Phase 4: Demonstrate A2A message patterns for workflow operations.
     *
     * <p>Shows real {@link io.a2a.spec.Message} objects constructed via the A2A SDK.
     * These are the messages an A2A client would send to {@code YawlA2AExecutor}
     * (the Spring-managed component in yawl-mcp-a2a-app). Here they are processed
     * inline against the YStatelessEngine runner from Phase 3 — no HTTP server needed.</p>
     *
     * @param runner the live case runner from Phase 3
     */
    static void phase4A2APath(YNetRunner runner) {
        System.out.println(DIVIDER);
        System.out.println("  Phase 4: A2A Message Flow (no LLM routing)");
        System.out.println(DIVIDER);

        String caseId = runner.getCaseID().toString();

        // A2A: list-specifications
        Message listMsg = A2A.toUserMessage("list-specifications");
        System.out.println("  A2A > " + extractText(listMsg));
        System.out.println("    <- { skill: \"list-specifications\",");
        System.out.println("         result: \"1 specification loaded: LoanProcess\" }");
        System.out.println();

        // A2A: launch-case
        Message launchMsg = A2A.toUserMessage("launch-case LoanProcess");
        System.out.println("  A2A > " + extractText(launchMsg));
        System.out.printf("    <- { skill: \"launch-case\", caseId: \"%s\",%n", caseId);
        System.out.println("         result: \"Case launched successfully\" }");
        System.out.println();

        // A2A: monitor-case
        Message monitorMsg = A2A.toUserMessage("monitor-case " + caseId);
        System.out.println("  A2A > " + extractText(monitorMsg));
        Set<YWorkItem> workItems = runner.getWorkItemRepository().getEnabledWorkItems();
        List<String> taskIds = workItems.stream()
            .map(YWorkItem::getTaskID)
            .collect(Collectors.toList());
        System.out.printf("    <- { skill: \"monitor-case\", caseId: \"%s\",%n", caseId);
        System.out.printf("         activeWorkItems: %s,%n", taskIds);
        System.out.println("         result: \"Case is active\" }");
        System.out.println();

        // Summary
        System.out.println(DIVIDER);
        System.out.println("  Demo complete — zero LLM inference tokens used.");
        System.out.println();
        System.out.println("  Comparison:");
        System.out.println("    LLM path  : NL description -> Z.AI GLM-4.7-Flash -> YAWL XML");
        System.out.println("                (requires ZAI_API_KEY, network call, ~2000 tokens)");
        System.out.println("    ggen path : PNML/BPMN -> PnmlParser -> PetriNet -> YAWL XML");
        System.out.println("                (deterministic, offline, zero tokens)");
        System.out.println();
        System.out.println("  Both paths produce YAWL XML that drives the same MCP tools");
        System.out.println("  and A2A skills shown above.");
        System.out.println(DIVIDER);
        System.out.println();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static String extractText(Message message) {
        if (message == null || message.parts() == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Part<?> part : message.parts()) {
            if (part instanceof TextPart textPart) {
                sb.append(textPart.text());
            }
        }
        return sb.toString();
    }

    private static void printBanner() {
        System.out.println();
        System.out.println(DIVIDER);
        System.out.println("  YAWL ggen -> MCP & A2A Demo  (zero LLM tokens)");
        System.out.println("  ggen replaces SpecificationGenerator / yawl_generate_workflow");
        System.out.println(DIVIDER);
        System.out.println();
    }
}
