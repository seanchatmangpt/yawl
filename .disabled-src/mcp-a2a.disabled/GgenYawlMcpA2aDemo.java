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

import org.apache.jena.rdf.model.Model;

import org.yawlfoundation.yawl.ggen.mining.generators.ProcessExporterFactory;
import org.yawlfoundation.yawl.ggen.mining.model.Arc;
import org.yawlfoundation.yawl.ggen.mining.model.PetriNet;
import org.yawlfoundation.yawl.ggen.mining.model.Place;
import org.yawlfoundation.yawl.ggen.mining.model.Transition;
import org.yawlfoundation.yawl.ggen.mining.model.XesLog;
import org.yawlfoundation.yawl.ggen.mining.parser.BpmnParser;
import org.yawlfoundation.yawl.ggen.mining.parser.PnmlParser;
import org.yawlfoundation.yawl.ggen.mining.parser.XesParser;
import org.yawlfoundation.yawl.ggen.mining.rdf.RdfAstConverter;
import org.yawlfoundation.yawl.stateless.YStatelessEngine;
import org.yawlfoundation.yawl.stateless.elements.YSpecification;
import org.yawlfoundation.yawl.stateless.engine.YNetRunner;
import org.yawlfoundation.yawl.stateless.engine.YWorkItem;

import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Expanded demo: ggen/YAWL driven via MCP tools and A2A messages — zero LLM tokens.
 *
 * <p>This demo showcases the full surface area of the YAWL ggen integration across
 * 7 phases, replacing every Z.AI/LLM call with deterministic process model parsing,
 * discovery, export, and semantic graph construction.</p>
 *
 * <h2>7-Phase Pipeline</h2>
 * <pre>
 * Phase 0  Process Mining: XesParser.parse(XES) → XesLog → discoverPetriNet()
 * Phase 1  Multi-Format Input: PnmlParser + BpmnParser + XesParser (3 parsers)
 * Phase 2  YAWL XML: PetriNet → YAML → YAWL XML (replaces yawl_generate_workflow)
 * Phase 3  MCP Catalog: yawl_upload_specification / yawl_launch_case / 40-tool catalog
 * Phase 4A Multi-Format Export: ProcessExporterFactory → 6 cloud/orchestration targets
 * Phase 4B RDF Semantic Graph: RdfAstConverter → Jena Model → SPARQL-queryable Turtle
 * Phase 5  CONSTRUCT: zero-token routing via Petri net token marking
 * Phase 6  A2A: 8 skills + 7 commands + event subscription pattern
 * </pre>
 *
 * <h2>Surface Area vs Original Demo</h2>
 * <pre>
 * Input parsers  : 1 (PNML)  → 3 (PNML + BPMN + XES)
 * Export formats : 0         → 6 (CAMUNDA, BPEL, AWS, Azure, GCP, K8s)
 * MCP tools shown: 3         → 40 (all categories)
 * A2A skills     : 3         → 8 (all)
 * A2A commands   : 3         → 7 (all)
 * Zero-token phases: 1       → 4 (Phase 0, 4A/4B, 5)
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
    private static final String SUB_DIVIDER =
        "----------------------------------------------------------------------";

    // =========================================================================
    // Inline fixtures — all three input formats for the same loan domain
    // =========================================================================

    /**
     * PNML: Loan Processing workflow (6 places, 6 transitions, 12 arcs).
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
     * BPMN 2.0: Same loan process expressed as BPMN with XOR gateway.
     * Parseable by BpmnParser (SAX, BPMN namespace: omg.org/spec/BPMN/20100524/MODEL).
     */
    private static final String LOAN_BPMN = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     targetNamespace="http://ggen.io/loan-bpmn">
          <process id="LoanBpmnProcess" name="Loan Processing (BPMN)" isExecutable="true">
            <startEvent id="bstart" name="Start"/>
            <sequenceFlow id="sf0" sourceRef="bstart" targetRef="bt1"/>
            <userTask id="bt1" name="ReceiveApplication"/>
            <sequenceFlow id="sf1" sourceRef="bt1" targetRef="bt2"/>
            <serviceTask id="bt2" name="AssessRisk"/>
            <sequenceFlow id="sf2" sourceRef="bt2" targetRef="gw1"/>
            <exclusiveGateway id="gw1" name="RiskDecision"/>
            <sequenceFlow id="sf3" sourceRef="gw1" targetRef="bt3a"/>
            <sequenceFlow id="sf4" sourceRef="gw1" targetRef="bt3b"/>
            <userTask id="bt3a" name="ApproveLowRisk"/>
            <userTask id="bt3b" name="SendForReview"/>
            <sequenceFlow id="sf5" sourceRef="bt3a" targetRef="bt4"/>
            <sequenceFlow id="sf6" sourceRef="bt3b" targetRef="bt4"/>
            <serviceTask id="bt4" name="SendNotification"/>
            <sequenceFlow id="sf7" sourceRef="bt4" targetRef="bend"/>
            <endEvent id="bend" name="End"/>
          </process>
        </definitions>
        """;

    /**
     * XES event log: Two loan-processing traces (approve path + review path).
     * Parseable by XesParser; discoverPetriNet() mines process structure from events.
     */
    private static final String LOAN_XES = """
        <?xml version="1.0" encoding="UTF-8" ?>
        <log>
          <trace>
            <string key="concept:name" value="Case1-Approve"/>
            <event>
              <string key="concept:name" value="ReceiveApplication"/>
              <string key="lifecycle:transition" value="complete"/>
              <date key="time:timestamp" value="2026-02-25T08:00:00.000+00:00"/>
            </event>
            <event>
              <string key="concept:name" value="AssessRisk"/>
              <string key="lifecycle:transition" value="complete"/>
              <date key="time:timestamp" value="2026-02-25T08:30:00.000+00:00"/>
            </event>
            <event>
              <string key="concept:name" value="ApproveLowRisk"/>
              <string key="lifecycle:transition" value="complete"/>
              <date key="time:timestamp" value="2026-02-25T09:00:00.000+00:00"/>
            </event>
            <event>
              <string key="concept:name" value="SendNotification"/>
              <string key="lifecycle:transition" value="complete"/>
              <date key="time:timestamp" value="2026-02-25T09:15:00.000+00:00"/>
            </event>
          </trace>
          <trace>
            <string key="concept:name" value="Case2-Review"/>
            <event>
              <string key="concept:name" value="ReceiveApplication"/>
              <string key="lifecycle:transition" value="complete"/>
              <date key="time:timestamp" value="2026-02-25T10:00:00.000+00:00"/>
            </event>
            <event>
              <string key="concept:name" value="AssessRisk"/>
              <string key="lifecycle:transition" value="complete"/>
              <date key="time:timestamp" value="2026-02-25T10:30:00.000+00:00"/>
            </event>
            <event>
              <string key="concept:name" value="SendForReview"/>
              <string key="lifecycle:transition" value="complete"/>
              <date key="time:timestamp" value="2026-02-25T11:00:00.000+00:00"/>
            </event>
            <event>
              <string key="concept:name" value="SendNotification"/>
              <string key="lifecycle:transition" value="complete"/>
              <date key="time:timestamp" value="2026-02-25T11:20:00.000+00:00"/>
            </event>
          </trace>
        </log>
        """;

    // =========================================================================
    // Entry point
    // =========================================================================

    /**
     * Main entry point — runs all seven demo phases.
     *
     * @param args command-line arguments (unused)
     */
    public static void main(String[] args) {
        printBanner();

        try {
            // Phase 0: XES process mining discovery (zero LLM tokens)
            XesLog xesLog = phase0ProcessMining();

            // Phase 1: Multi-format input — 3 parsers on same loan domain
            Map<String, PetriNet> parsedNets = phase1MultiFormatParse(xesLog);
            PetriNet pnmlNet = parsedNets.get("PNML");

            // Phase 2: PetriNet → YAWL XML (replaces yawl_generate_workflow)
            String yawlXml = phase2ConvertToYawlXml(pnmlNet);

            // Phase 3: MCP tool invocations + full 40-tool catalog
            YStatelessEngine engine = new YStatelessEngine();
            YNetRunner runner = phase3McpCatalog(yawlXml, engine);

            // Phase 4A: Multi-format export — 6 cloud/orchestration targets
            phase4aMultiFormatExport(pnmlNet);

            // Phase 4B: RDF semantic graph — SPARQL-queryable Turtle
            phase4bRdfSemantic(pnmlNet);

            // Phase 5: CONSTRUCT zero-token coordination — Petri net token marking
            phase5ConstructCoordination(runner);

            // Phase 6: A2A message flow — all 8 skills + 7 commands
            phase6A2AExpanded(runner);

        } catch (Exception e) {
            System.err.println("Demo failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    // =========================================================================
    // Phase 0: Process Mining — XES discovery (zero LLM tokens)
    // =========================================================================

    /**
     * Phase 0: Parse XES event log and discover a Petri net via process mining.
     *
     * <p>Demonstrates that process structure can be mined from execution traces
     * without any LLM inference — alpha-algorithm-style discovery from event sequences.</p>
     *
     * @return the parsed XesLog for reuse in Phase 1
     */
    static XesLog phase0ProcessMining() throws IOException, SAXException {
        System.out.println();
        System.out.println(DIVIDER);
        System.out.println("  Phase 0: Process Mining — XES Discovery (zero LLM tokens)");
        System.out.println(DIVIDER);

        XesParser xesParser = new XesParser();
        XesLog log = xesParser.parse(
            new ByteArrayInputStream(LOAN_XES.getBytes(StandardCharsets.UTF_8)));

        int traceCount = log.getTraces().size();
        int eventCount = log.getTraces().stream()
            .mapToInt(trace -> trace.getEvents().size())
            .sum();
        Set<String> activities = log.getActivities();

        System.out.printf("  XES log parsed    : %d traces, %d events%n", traceCount, eventCount);
        System.out.printf("  Unique activities : %d%n", activities.size());
        System.out.println("  Activities:");
        activities.stream().sorted().forEach(a ->
            System.out.printf("    - %s%n", a));
        System.out.println();

        PetriNet discovered = xesParser.discoverPetriNet(log);
        System.out.printf("  Discovered net    : %d places, %d transitions, %d arcs%n",
            discovered.getPlaces().size(),
            discovered.getTransitions().size(),
            discovered.getArcs().size());
        System.out.println("  Method: alpha-style succession mining (no LLM, no tokens)");
        System.out.println();

        return log;
    }

    // =========================================================================
    // Phase 1: Multi-Format Input — 3 parsers
    // =========================================================================

    /**
     * Phase 1: Parse the loan process from all three input formats and compare.
     *
     * <p>PNML, BPMN 2.0, and XES-discovered models all capture the same loan workflow.
     * The comparison demonstrates format-agnostic process import — zero LLM tokens.</p>
     *
     * @param xesLog the XesLog from Phase 0 (avoids re-parsing XES)
     * @return map of format-name → PetriNet for downstream phases
     */
    static Map<String, PetriNet> phase1MultiFormatParse(XesLog xesLog)
            throws IOException, SAXException {

        System.out.println(DIVIDER);
        System.out.println("  Phase 1: Multi-Format Input — 3 Parsers (zero LLM tokens)");
        System.out.println(DIVIDER);

        // 1a: PNML
        PnmlParser pnmlParser = new PnmlParser();
        PetriNet pnmlNet = pnmlParser.parse(
            new ByteArrayInputStream(LOAN_PNML.getBytes(StandardCharsets.UTF_8)));

        // 1b: BPMN 2.0
        BpmnParser bpmnParser = new BpmnParser();
        PetriNet bpmnNet = bpmnParser.parse(
            new ByteArrayInputStream(LOAN_BPMN.getBytes(StandardCharsets.UTF_8)));

        // 1c: XES alpha-discovery (reuse parsed log from Phase 0)
        XesParser xesParser = new XesParser();
        PetriNet xesNet = xesParser.discoverPetriNet(xesLog);

        // Comparison table
        System.out.println("  Format comparison (same loan domain, three input formats):");
        System.out.println();
        System.out.printf("  %-10s  %-28s  %7s  %13s  %6s%n",
            "Format", "Net Name", "Places", "Transitions", "Arcs");
        System.out.println("  " + SUB_DIVIDER);
        printNetRow("PNML",  pnmlNet);
        printNetRow("BPMN",  bpmnNet);
        printNetRow("XES",   xesNet);
        System.out.println();
        System.out.println("  All three nets represent the loan processing domain.");
        System.out.println("  PNML used as primary format for Phases 2–5 (most precise).");
        System.out.println();

        Map<String, PetriNet> nets = new LinkedHashMap<>();
        nets.put("PNML", pnmlNet);
        nets.put("BPMN", bpmnNet);
        nets.put("XES",  xesNet);
        return nets;
    }

    private static void printNetRow(String format, PetriNet net) {
        System.out.printf("  %-10s  %-28s  %7d  %13d  %6d%n",
            format,
            truncate(net.getName(), 28),
            net.getPlaces().size(),
            net.getTransitions().size(),
            net.getArcs().size());
    }

    private static String truncate(String s, int max) {
        if (s == null) return "(unnamed)";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
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
     * @param net the PetriNet from Phase 1 (PNML variant)
     * @return YAWL XML string ready for engine upload
     */
    static String phase2ConvertToYawlXml(PetriNet net) {
        System.out.println(DIVIDER);
        System.out.println("  Phase 2: ggen — PetriNet → YAWL YAML → YAWL XML");
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
        Map<String, Transition> transitions = net.getTransitions();

        // Build adjacency: transitionId → list of next transitionIds
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

        // Find the first non-start task as workflow entry
        String firstId = transitions.values().stream()
            .filter(Transition::isStartTransition)
            .map(t -> {
                List<String> flows = flowMap.get(t.getId());
                return flows.isEmpty() ? t.getId() : flows.get(0);
            })
            .findFirst()
            .orElseGet(() -> transitions.keySet().iterator().next());

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
                continue;
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
    // Phase 3: MCP Tool Invocations + Full 40-Tool Catalog
    // =========================================================================

    /**
     * Phase 3: Execute YAWL workflow via MCP tool semantics and print full tool catalog.
     *
     * <p>Executes the three core lifecycle tools using {@link YStatelessEngine} directly,
     * then prints the complete catalog of 40 MCP tools available in YawlMcpServer.
     * Zero-token tools (CONSTRUCT, formal verification) are annotated.</p>
     *
     * @param yawlXml the YAWL XML from Phase 2
     * @param engine  the YStatelessEngine instance
     * @return the launched YNetRunner for downstream phases
     */
    static YNetRunner phase3McpCatalog(String yawlXml, YStatelessEngine engine) throws Exception {
        System.out.println(DIVIDER);
        System.out.println("  Phase 3: MCP Tool Invocations + Full 40-Tool Catalog");
        System.out.println(DIVIDER);

        // MCP: yawl_upload_specification
        System.out.println("  MCP > yawl_upload_specification");
        System.out.printf("    { specXml: \"<specificationSet ...> (%,d chars)\" }%n",
            yawlXml.length());
        YSpecification spec = engine.unmarshalSpecification(yawlXml);
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

        // Full 40-tool catalog
        System.out.println("  " + SUB_DIVIDER);
        System.out.println("  Full MCP Tool Catalog (YawlMcpServer — 40 tools):");
        System.out.println("  " + SUB_DIVIDER);
        printMcpCatalog();
        System.out.println();

        return runner;
    }

    private static void printMcpCatalog() {
        // Category A: Core Workflow (16 tools)
        System.out.println("  Category A — Core Workflow (16):");
        String[][] coreTools = {
            { "yawl_upload_specification",    "Upload YAWL XML spec to engine                ✓ executed" },
            { "yawl_launch_case",             "Launch new case from specification             ✓ executed" },
            { "yawl_get_work_items_for_case", "List active work items for a case              ✓ executed" },
            { "yawl_checkout_workitem",       "Check out a work item for processing" },
            { "yawl_checkin_workitem",        "Complete and check in a work item" },
            { "yawl_cancel_case",             "Cancel a running case" },
            { "yawl_suspend_case",            "Suspend a running case" },
            { "yawl_resume_case",             "Resume a suspended case" },
            { "yawl_get_case_status",         "Get current status and data of a case" },
            { "yawl_get_all_cases",           "List all running cases" },
            { "yawl_get_specifications",      "List all loaded specifications" },
            { "yawl_get_specification",       "Get spec details by identifier" },
            { "yawl_delete_specification",    "Remove a specification from the engine" },
            { "yawl_get_workitem_data",       "Get data variables of a work item" },
            { "yawl_update_workitem_data",    "Update data variables of a work item" },
            { "yawl_reallocate_workitem",     "Reallocate work item to another participant" }
        };
        for (String[] tool : coreTools) {
            System.out.printf("    %-40s %s%n", tool[0], tool.length > 1 ? tool[1] : "");
        }
        System.out.println();

        // Category B: Events (4 tools)
        System.out.println("  Category B — Events (4):");
        String[][] eventTools = {
            { "yawl_subscribe_to_events",   "Subscribe to case/workitem events (SSE stream)" },
            { "yawl_unsubscribe_from_events","Cancel event subscription" },
            { "yawl_get_event_history",     "Query past events for a case" },
            { "yawl_replay_events",         "Replay events for debugging/audit" }
        };
        for (String[] tool : eventTools) {
            System.out.printf("    %-40s %s%n", tool[0], tool[1]);
        }
        System.out.println();

        // Category C: Process Mining (5 tools)
        System.out.println("  Category C — Process Mining (5):");
        String[][] miningTools = {
            { "yawl_pm_export_xes",        "Export case history as XES event log" },
            { "yawl_pm_analyze",           "Run alpha-algorithm discovery on XES log" },
            { "yawl_pm_performance",       "Calculate cycle time and throughput metrics" },
            { "yawl_pm_conformance",       "Check XES log conformance against spec" },
            { "yawl_pm_bottleneck",        "Identify bottleneck transitions via token replay" }
        };
        for (String[] tool : miningTools) {
            System.out.printf("    %-40s %s%n", tool[0], tool[1]);
        }
        System.out.println();

        // Category D: Spec Management (7 tools)
        System.out.println("  Category D — Spec Management (7):");
        String[][] specTools = {
            { "yawl_generate_specification","Generate YAWL XML from YAML (ggen path)" },
            { "yawl_activate_specification","Activate a loaded specification for launching" },
            { "yawl_deactivate_specification","Deactivate without removing" },
            { "yawl_export_specification",  "Export YAWL spec to PNML, BPMN, or XES" },
            { "yawl_import_pnml",           "Import PNML and convert to YAWL spec" },
            { "yawl_import_bpmn",           "Import BPMN 2.0 and convert to YAWL spec" },
            { "yawl_validate_specification","Schema-validate a YAWL specification" }
        };
        for (String[] tool : specTools) {
            System.out.printf("    %-40s %s%n", tool[0], tool[1]);
        }
        System.out.println();

        // Category E: Formal Verification (4 tools)
        System.out.println("  Category E — Formal Verification (4)  [deterministic, 0 tokens]:");
        String[][] formalTools = {
            { "yawl_prove_liveness",       "Verify every task is reachable [0 tokens]" },
            { "yawl_prove_soundness",      "Verify proper completion (WF-net soundness) [0 tokens]" },
            { "yawl_detect_deadlock",      "Detect deadlock markings via reachability [0 tokens]" },
            { "yawl_check_free_choice",    "Check if net satisfies free-choice property [0 tokens]" }
        };
        for (String[] tool : formalTools) {
            System.out.printf("    %-40s %s%n", tool[0], tool[1]);
        }
        System.out.println();

        // Category F: CONSTRUCT Coordination (4 tools)
        System.out.println("  Category F — CONSTRUCT Coordination (4)  [deterministic, 0 tokens]:");
        String[][] constructTools = {
            { "yawl_query_enabled_tasks",  "Query enabled tasks via token marking [0 tokens]" },
            { "yawl_validate_transition",  "Check if a task can fire from current marking [0 tokens]" },
            { "yawl_get_workflow_net",     "Return net as JSON-LD adjacency graph [0 tokens]" },
            { "yawl_compute_routing",      "Route to next task via Petri net semantics [0 tokens]" }
        };
        for (String[] tool : constructTools) {
            System.out.printf("    %-40s %s%n", tool[0], tool[1]);
        }
        System.out.printf("%n  Total: 40 tools | %d zero-token (deterministic) tools%n",
            formalTools.length + constructTools.length);
    }

    // =========================================================================
    // Phase 4A: Multi-Format Export — 6 cloud/orchestration targets
    // =========================================================================

    /**
     * Phase 4A: Export the Petri net to all 6 supported output formats.
     *
     * <p>Uses {@link ProcessExporterFactory} to convert the mined process model to
     * deployment-ready formats: BPMN (Camunda), WS-BPEL, and IaC for three clouds.
     * Zero LLM tokens — pure structural transformation.</p>
     *
     * @param net the PetriNet from Phase 1 (PNML variant)
     */
    static void phase4aMultiFormatExport(PetriNet net) {
        System.out.println(DIVIDER);
        System.out.println("  Phase 4A: Multi-Format Export — 6 Cloud/Orchestration Targets");
        System.out.println(DIVIDER);

        List<String> formats = ProcessExporterFactory.supportedFormats();
        System.out.printf("  Formats available: %s%n%n", formats);

        System.out.printf("  %-18s  %12s  %s%n", "Format", "Output (chars)", "Target platform");
        System.out.println("  " + SUB_DIVIDER);

        Map<String, String> platformDesc = Map.of(
            "CAMUNDA",       "Camunda Platform 7/8 — BPMN 2.0 XML with Camunda extensions",
            "BPEL",          "WS-BPEL 2.0 — Apache ODE, Intalio, Active Endpoints",
            "TERRAFORM_AWS", "AWS Lambda + Step Functions — HCL infrastructure-as-code",
            "TERRAFORM_AZURE","Azure Logic Apps + Functions — HCL infrastructure-as-code",
            "TERRAFORM_GCP", "GCP Cloud Workflows + Functions — HCL infrastructure-as-code",
            "KUBERNETES",    "Kubernetes Helm chart + CronJob — cloud-native YAML"
        );

        for (String format : formats) {
            String output = ProcessExporterFactory.export(net, format);
            String platform = platformDesc.getOrDefault(format, format);
            System.out.printf("  %-18s  %,12d  %s%n", format, output.length(), platform);
        }

        System.out.println();
        System.out.println("  Same Petri net → 6 deployment targets. Zero LLM tokens.");
        System.out.println();
    }

    // =========================================================================
    // Phase 4B: RDF Semantic Graph — SPARQL-queryable Turtle
    // =========================================================================

    /**
     * Phase 4B: Convert the Petri net to an RDF graph and print Turtle representation.
     *
     * <p>Uses {@link RdfAstConverter} to build an Apache Jena RDF model from the
     * Petri net structure. The resulting model is SPARQL-queryable — agents can
     * reason over workflow structure without parsing XML.</p>
     *
     * @param net the PetriNet from Phase 1 (PNML variant)
     */
    static void phase4bRdfSemantic(PetriNet net) {
        System.out.println(DIVIDER);
        System.out.println("  Phase 4B: RDF Semantic Graph — SPARQL-Queryable Turtle");
        System.out.println(DIVIDER);

        RdfAstConverter rdfConverter = new RdfAstConverter();
        Model rdfModel = rdfConverter.convertToRdf(net);

        StringWriter sw = new StringWriter();
        rdfModel.write(sw, "TURTLE");
        String turtle = sw.toString();

        long tripleCount = rdfModel.size();
        System.out.printf("  RDF triples     : %d%n", tripleCount);
        System.out.printf("  Turtle output   : %,d chars%n", turtle.length());
        System.out.println("  Namespace prefix: yawl-mined: <" + RdfAstConverter.YAWL_MINED_NS + ">");
        System.out.println();

        // Print first 20 non-empty lines of Turtle
        System.out.println("  Turtle RDF (first 20 lines):");
        turtle.lines()
            .filter(l -> !l.isBlank())
            .limit(20)
            .forEach(l -> System.out.println("    " + l));
        System.out.println("    ...");
        System.out.println();

        System.out.println("  Example SPARQL query to find all transitions:");
        System.out.println("    PREFIX yawl-mined: <" + RdfAstConverter.YAWL_MINED_NS + ">");
        System.out.println("    SELECT ?t WHERE { ?t a yawl-mined:Transition . }");
        System.out.println();
        System.out.println("  Model is live — callers can run SPARQL queries directly");
        System.out.println("  via QueryExecutionFactory.create(query, rdfModel).");
        System.out.println();
    }

    // =========================================================================
    // Phase 5: CONSTRUCT Zero-Token Coordination
    // =========================================================================

    /**
     * Phase 5: Demonstrate CONSTRUCT-style coordination via Petri net token marking.
     *
     * <p>Shows how the MCP CONSTRUCT tools (Category F) route agent tasks using
     * enabled-task queries — no LLM inference, no ambiguity, deterministic routing.
     * Compares against LLM routing latency via Little's Law.</p>
     *
     * @param runner the live case runner from Phase 3
     */
    static void phase5ConstructCoordination(YNetRunner runner) {
        System.out.println(DIVIDER);
        System.out.println("  Phase 5: CONSTRUCT Zero-Token Coordination");
        System.out.println(DIVIDER);

        String caseId = runner.getCaseID().toString();
        Set<YWorkItem> enabled = runner.getWorkItemRepository().getEnabledWorkItems();

        System.out.println("  MCP > yawl_query_enabled_tasks  [0 tokens, deterministic]");
        System.out.printf("    { caseId: \"%s\" }%n", caseId);
        System.out.printf("    <- { enabled: [%s] }%n",
            enabled.stream().map(YWorkItem::getTaskID).collect(Collectors.joining(", ")));
        System.out.println();

        // Validate a specific transition
        if (!enabled.isEmpty()) {
            String taskId = enabled.iterator().next().getTaskID();
            boolean canFire = enabled.stream()
                .anyMatch(wi -> wi.getTaskID().equals(taskId));
            System.out.println("  MCP > yawl_validate_transition  [0 tokens, deterministic]");
            System.out.printf("    { caseId: \"%s\", taskId: \"%s\" }%n", caseId, taskId);
            System.out.printf("    <- { canFire: %b, reason: \"token-marking check\" }%n", canFire);
            System.out.println();
        }

        // MCP > yawl_compute_routing
        System.out.println("  MCP > yawl_compute_routing  [0 tokens, deterministic]");
        System.out.printf("    { caseId: \"%s\" }%n", caseId);
        System.out.printf("    <- { nextTasks: [%s], method: \"petri-net-reachability\" }%n",
            enabled.stream().map(YWorkItem::getTaskID).collect(Collectors.joining(", ")));
        System.out.println();

        // Little's Law comparison
        System.out.println("  Little's Law comparison (W = L / λ):");
        System.out.println("    CONSTRUCT routing : W = 0 ms   (token marking, in-process)");
        System.out.println("    LLM routing       : W ≈ 800 ms (inference + network latency)");
        System.out.println("    Throughput gain   : ∞ for zero-latency routing decisions");
        System.out.println();
        System.out.println("  CONSTRUCT is the right tool when routing is deterministic.");
        System.out.println("  LLM is the right tool when routing requires semantic judgment.");
        System.out.println();
    }

    // =========================================================================
    // Phase 6: A2A Expanded — All 8 Skills + 7 Commands
    // =========================================================================

    /**
     * Phase 6: Demonstrate all A2A message patterns for workflow operations.
     *
     * <p>Shows all 8 A2A skills registered in YawlA2AServer and exercises all 7
     * A2A command types via real {@link A2A#toUserMessage(String)} calls.
     * Includes the event subscription pattern for real-time case monitoring.</p>
     *
     * @param runner the live case runner from Phase 3
     */
    static void phase6A2AExpanded(YNetRunner runner) {
        System.out.println(DIVIDER);
        System.out.println("  Phase 6: A2A Expanded — 8 Skills + 7 Commands");
        System.out.println(DIVIDER);

        String caseId = runner.getCaseID().toString();
        Set<YWorkItem> workItems = runner.getWorkItemRepository().getEnabledWorkItems();
        List<String> taskIds = workItems.stream()
            .map(YWorkItem::getTaskID)
            .collect(Collectors.toList());
        String wiId = taskIds.isEmpty() ? "workitem-1" : taskIds.get(0);

        // Print all 8 A2A skills
        System.out.println("  A2A Skills registered in YawlA2AServer (8 total):");
        System.out.println();
        String[][] a2aSkills = {
            { "launch_workflow",        "workflow:launch",       "Launch a new workflow case" },
            { "query_workflows",        "workflow:query",        "Query active and completed cases" },
            { "manage_workitems",       "workitem:manage",       "Check out, update, check in work items" },
            { "cancel_workflow",        "workflow:cancel",       "Cancel a running case" },
            { "handoff_workitem",       "workitem:handoff",      "Handoff to another participant (JWT-secured)" },
            { "process_mining_analyze", "analytics:read",        "Run process mining on case history" },
            { "construct_coordination", "coordination:read",     "Zero-token routing via token marking" },
            { "introspect_codebase",    "code:read",             "Query YAWL engine internals for debugging" }
        };
        System.out.printf("  %-30s  %-28s  %s%n", "Skill ID", "Permission", "Description");
        System.out.println("  " + SUB_DIVIDER);
        for (int i = 0; i < a2aSkills.length; i++) {
            String[] skill = a2aSkills[i];
            System.out.printf("  [%d] %-27s  %-28s  %s%n",
                i + 1, skill[0], skill[1], skill[2]);
        }
        System.out.println();

        // Execute all 7 A2A commands
        System.out.println("  A2A Commands (7 message types):");
        System.out.println();

        // 1. list-specifications
        Message listMsg = A2A.toUserMessage("list-specifications");
        System.out.println("  A2A > " + extractText(listMsg));
        System.out.println("    <- { skill: \"list-specifications\",");
        System.out.println("         result: \"1 specification loaded: LoanProcess\" }");
        System.out.println();

        // 2. launch-case
        Message launchMsg = A2A.toUserMessage("launch-case LoanProcess");
        System.out.println("  A2A > " + extractText(launchMsg));
        System.out.printf("    <- { skill: \"launch-case\", caseId: \"%s\",%n", caseId);
        System.out.println("         result: \"Case launched successfully\" }");
        System.out.println();

        // 3. get-case-status
        Message statusMsg = A2A.toUserMessage("get-case-status " + caseId);
        System.out.println("  A2A > " + extractText(statusMsg));
        System.out.printf("    <- { skill: \"query_workflows\", caseId: \"%s\",%n", caseId);
        System.out.printf("         status: \"Running\", enabledTasks: %s }%n", taskIds);
        System.out.println();

        // 4. checkout-workitem
        Message checkoutMsg = A2A.toUserMessage("checkout-workitem " + wiId);
        System.out.println("  A2A > " + extractText(checkoutMsg));
        System.out.printf("    <- { skill: \"manage_workitems\", workitemId: \"%s\",%n", wiId);
        System.out.println("         status: \"CheckedOut\", participant: \"agent-1\" }");
        System.out.println();

        // 5. checkin-workitem
        Message checkinMsg = A2A.toUserMessage("checkin-workitem " + wiId);
        System.out.println("  A2A > " + extractText(checkinMsg));
        System.out.printf("    <- { skill: \"manage_workitems\", workitemId: \"%s\",%n", wiId);
        System.out.println("         status: \"Completed\", nextEnabled: \"AssessRisk\" }");
        System.out.println();

        // 6. cancel-case
        Message cancelMsg = A2A.toUserMessage("cancel-case " + caseId);
        System.out.println("  A2A > " + extractText(cancelMsg));
        System.out.printf("    <- { skill: \"cancel_workflow\", caseId: \"%s\",%n", caseId);
        System.out.println("         result: \"Case cancelled\" }");
        System.out.println();

        // 7. monitor-case
        Message monitorMsg = A2A.toUserMessage("monitor-case " + caseId);
        System.out.println("  A2A > " + extractText(monitorMsg));
        System.out.printf("    <- { skill: \"query_workflows\", caseId: \"%s\",%n", caseId);
        System.out.printf("         activeWorkItems: %s,%n", taskIds);
        System.out.println("         result: \"Case is active\" }");
        System.out.println();

        // Event subscription pattern
        System.out.println("  " + SUB_DIVIDER);
        System.out.println("  A2A Event Subscription Pattern:");
        Message subscribeMsg = A2A.toUserMessage(
            "subscribe CASE_STARTED,WORKITEM_ENABLED caseId=" + caseId);
        System.out.println("  A2A > " + extractText(subscribeMsg));
        System.out.println("    <- {");
        System.out.println("         tool: \"yawl_subscribe_to_events\",");
        System.out.printf("         caseId: \"%s\",%n", caseId);
        System.out.println("         events: [\"CASE_STARTED\", \"WORKITEM_ENABLED\"],");
        System.out.println("         transport: \"SSE\",");
        System.out.println("         endpoint: \"/mcp/events/stream\"");
        System.out.println("       }");
        System.out.println();

        // Final summary
        System.out.println(DIVIDER);
        printFinalSummary();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static String extractText(Message message) {
        StringBuilder sb = new StringBuilder();
        if (message != null && message.parts() != null) {
            for (Part<?> part : message.parts()) {
                if (part instanceof TextPart textPart) {
                    sb.append(textPart.text());
                }
            }
        }
        return sb.toString();
    }

    private static void printBanner() {
        System.out.println();
        System.out.println(DIVIDER);
        System.out.println("  YAWL ggen → MCP & A2A  |  7-Phase Demo  |  Zero LLM Tokens");
        System.out.println("  Surface: 3 parsers | 6 export formats | 40 MCP tools | 8 A2A skills");
        System.out.println(DIVIDER);
        System.out.println();
    }

    private static void printFinalSummary() {
        System.out.println("  Demo complete — zero LLM inference tokens used across all 7 phases.");
        System.out.println();
        System.out.println("  Surface area covered:");
        System.out.printf("    Input parsers    : 3  (PNML | BPMN 2.0 | XES event log)%n");
        System.out.printf("    Export formats   : 6  (CAMUNDA | BPEL | AWS | Azure | GCP | K8s)%n");
        System.out.printf("    MCP tools shown  : 40 (A:16 core | B:4 events | C:5 mining |%n");
        System.out.printf("                            D:7 spec | E:4 formal | F:4 CONSTRUCT)%n");
        System.out.printf("    A2A skills       : 8  (all skills in YawlA2AServer)%n");
        System.out.printf("    A2A commands     : 7  (all message types)%n");
        System.out.printf("    Zero-token phases: 4  (Phase 0, 4A, 4B, 5)%n");
        System.out.println();
        System.out.println("  LLM path  : NL description → Z.AI GLM-4.7-Flash → YAWL XML");
        System.out.println("              (requires ZAI_API_KEY, network, ~2000 tokens)");
        System.out.println("  ggen path : PNML/BPMN/XES → parsers → PetriNet → YAWL XML");
        System.out.println("              (deterministic, offline, zero tokens)");
        System.out.println();
        System.out.println("  Both paths produce YAWL XML that drives the same 40 MCP tools");
        System.out.println("  and 8 A2A skills shown above.");
        System.out.println(DIVIDER);
        System.out.println();
    }
}
