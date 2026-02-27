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

package org.yawlfoundation.yawl.tooling;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import org.junit.BeforeClass;
import org.junit.Test;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.tooling.docgen.WorkflowDocumentationGenerator;
import org.yawlfoundation.yawl.tooling.docgen.WorkflowDocumentationGenerator.WorkflowMetrics;
import org.yawlfoundation.yawl.tooling.intellij.visualization.YawlNetGraphBuilder;
import org.yawlfoundation.yawl.tooling.intellij.visualization.YawlNetGraphBuilder.NetGraph;
import org.yawlfoundation.yawl.unmarshal.YMarshal;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Integration tests for WorkflowDocumentationGenerator and YawlNetGraphBuilder.
 *
 * All tests use real YSpecification and YNet objects loaded by YMarshal.
 *
 * @author YAWL Development Team
 * @since 6.0.0
 */
@Tag("unit")
public class TestWorkflowDocumentation {

    private static YSpecification spec;

    private static final String SPEC_XML =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<specificationSet version=\"4.0\"\n" +
            "    xmlns=\"http://www.yawlfoundation.org/yawlschema\"\n" +
            "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
            "    xsi:schemaLocation=\"http://www.yawlfoundation.org/yawlschema " +
            "http://www.yawlfoundation.org/yawlschema/YAWL_Schema4.0.xsd\">\n" +
            "  <specification uri=\"DocTestSpec\">\n" +
            "    <name>Documentation Test Specification</name>\n" +
            "    <documentation>Used for testing docgen tooling</documentation>\n" +
            "    <metaData/>\n" +
            "    <decomposition id=\"DocNet\" xsi:type=\"NetFactsType\" isRootNet=\"true\">\n" +
            "      <name>Documentation Net</name>\n" +
            "      <processControlElements>\n" +
            "        <inputCondition id=\"start\">\n" +
            "          <name>Start</name>\n" +
            "          <flowsInto><nextElementRef id=\"TaskA\"/></flowsInto>\n" +
            "        </inputCondition>\n" +
            "        <task id=\"TaskA\">\n" +
            "          <name>Task Alpha</name>\n" +
            "          <flowsInto><nextElementRef id=\"TaskB\"/></flowsInto>\n" +
            "          <join code=\"xor\"/><split code=\"and\"/>\n" +
            "        </task>\n" +
            "        <task id=\"TaskB\">\n" +
            "          <name>Task Beta</name>\n" +
            "          <flowsInto><nextElementRef id=\"end\"/></flowsInto>\n" +
            "          <join code=\"and\"/><split code=\"and\"/>\n" +
            "        </task>\n" +
            "        <outputCondition id=\"end\"><name>End</name></outputCondition>\n" +
            "      </processControlElements>\n" +
            "    </decomposition>\n" +
            "  </specification>\n" +
            "</specificationSet>\n";

    @BeforeClass
    public static void loadSpec() throws Exception {
        List<YSpecification> specs = YMarshal.unmarshalSpecifications(SPEC_XML, false);
        assertFalse("Should load at least one specification", specs.isEmpty());
        spec = specs.get(0);
        assertNotNull("Specification should not be null", spec);
    }

    // ---- WorkflowDocumentationGenerator tests --------------------------------

    @Test
    public void testConstructor_NullSpec_ThrowsIllegalArgument() {
        try {
            new WorkflowDocumentationGenerator(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testGenerateMarkdown_ContainsSpecUri() {
        WorkflowDocumentationGenerator gen = new WorkflowDocumentationGenerator(spec);
        String md = gen.generateMarkdown();
        assertNotNull(md);
        assertTrue("Markdown should contain spec URI", md.contains("DocTestSpec"));
    }

    @Test
    public void testGenerateMarkdown_ContainsNetName() {
        WorkflowDocumentationGenerator gen = new WorkflowDocumentationGenerator(spec);
        String md = gen.generateMarkdown();
        assertTrue("Markdown should mention the net", md.contains("Documentation Net"));
    }

    @Test
    public void testGenerateMarkdown_ContainsMetricsTable() {
        WorkflowDocumentationGenerator gen = new WorkflowDocumentationGenerator(spec);
        String md = gen.generateMarkdown();
        assertTrue("Markdown should have metrics", md.contains("Workflow Metrics"));
        assertTrue("Metrics should mention atomic tasks", md.contains("Atomic tasks"));
    }

    @Test
    public void testGenerateMarkdown_ContainsTaskIds() {
        WorkflowDocumentationGenerator gen = new WorkflowDocumentationGenerator(spec);
        String md = gen.generateMarkdown();
        assertTrue("Markdown should list TaskA", md.contains("TaskA"));
        assertTrue("Markdown should list TaskB", md.contains("TaskB"));
    }

    @Test
    public void testGenerateHtml_WellFormedDoc() {
        WorkflowDocumentationGenerator gen = new WorkflowDocumentationGenerator(spec);
        String html = gen.generateHtml();
        assertNotNull(html);
        assertTrue("HTML should start with DOCTYPE", html.startsWith("<!DOCTYPE html>"));
        assertTrue("HTML should have closing html tag", html.contains("</html>"));
        assertTrue("HTML should contain spec URI", html.contains("DocTestSpec"));
    }

    @Test
    public void testGenerateHtml_ContainsStyleSection() {
        WorkflowDocumentationGenerator gen = new WorkflowDocumentationGenerator(spec);
        String html = gen.generateHtml();
        assertTrue("HTML should have style", html.contains("<style>"));
    }

    @Test
    public void testGenerateHtml_ContainsNetElementsTable() {
        WorkflowDocumentationGenerator gen = new WorkflowDocumentationGenerator(spec);
        String html = gen.generateHtml();
        assertTrue("HTML should have elements table", html.contains("Net Elements"));
        assertTrue("HTML should list TaskA", html.contains("TaskA"));
    }

    // ---- WorkflowMetrics tests -----------------------------------------------

    @Test
    public void testComputeMetrics_AtomicTaskCount() {
        WorkflowDocumentationGenerator gen = new WorkflowDocumentationGenerator(spec);
        WorkflowMetrics metrics = gen.computeMetrics();
        assertEquals("Should have 2 atomic tasks", 2, metrics.atomicTaskCount());
    }

    @Test
    public void testComputeMetrics_ConditionCount() {
        WorkflowDocumentationGenerator gen = new WorkflowDocumentationGenerator(spec);
        WorkflowMetrics metrics = gen.computeMetrics();
        // inputCondition + outputCondition = 2 conditions
        assertEquals("Should have 2 conditions (input + output)", 2, metrics.conditionCount());
    }

    @Test
    public void testComputeMetrics_FlowCount() {
        WorkflowDocumentationGenerator gen = new WorkflowDocumentationGenerator(spec);
        WorkflowMetrics metrics = gen.computeMetrics();
        // start->TaskA, TaskA->TaskB, TaskB->end = 3 flows
        assertEquals("Should have 3 flows", 3, metrics.flowCount());
    }

    @Test
    public void testComputeMetrics_DecompositionCount() {
        WorkflowDocumentationGenerator gen = new WorkflowDocumentationGenerator(spec);
        WorkflowMetrics metrics = gen.computeMetrics();
        assertEquals("Should have 1 decomposition", 1, metrics.decompositionCount());
    }

    @Test
    public void testComputeMetrics_CyclomaticComplexityIsPositive() {
        WorkflowDocumentationGenerator gen = new WorkflowDocumentationGenerator(spec);
        WorkflowMetrics metrics = gen.computeMetrics();
        assertTrue("Cyclomatic complexity should be positive", metrics.cyclomaticComplexity() > 0);
    }

    // ---- YawlNetGraphBuilder tests -------------------------------------------

    @Test
    public void testBuildGraph_NullNet_ThrowsIllegalArgument() {
        try {
            YawlNetGraphBuilder.build(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testBuildGraph_ReturnsNonNullGraph() {
        YNet net = spec.getRootNet();
        assertNotNull("Root net should not be null", net);
        NetGraph graph = YawlNetGraphBuilder.build(net);
        assertNotNull("Graph should not be null", graph);
    }

    @Test
    public void testBuildGraph_NodeCount() {
        YNet net = spec.getRootNet();
        NetGraph graph = YawlNetGraphBuilder.build(net);
        // start + TaskA + TaskB + end = 4
        assertEquals("Graph should have 4 nodes", 4, graph.nodeCount());
    }

    @Test
    public void testBuildGraph_EdgeCount() {
        YNet net = spec.getRootNet();
        NetGraph graph = YawlNetGraphBuilder.build(net);
        // start->TaskA, TaskA->TaskB, TaskB->end = 3 edges
        assertEquals("Graph should have 3 edges", 3, graph.edgeCount());
    }

    @Test
    public void testBuildGraph_TaskCount() {
        YNet net = spec.getRootNet();
        NetGraph graph = YawlNetGraphBuilder.build(net);
        assertEquals("Graph should have 2 tasks", 2, graph.taskCount());
    }

    @Test
    public void testBuildGraph_ConditionCount() {
        YNet net = spec.getRootNet();
        NetGraph graph = YawlNetGraphBuilder.build(net);
        assertEquals("Graph should have 2 conditions", 2, graph.conditionCount());
    }

    @Test
    public void testBuildGraph_NodesHaveLabels() {
        YNet net = spec.getRootNet();
        NetGraph graph = YawlNetGraphBuilder.build(net);
        for (YawlNetGraphBuilder.GraphNode node : graph.nodes()) {
            assertNotNull("Node label should not be null", node.label());
            assertFalse("Node label should not be blank", node.label().isBlank());
        }
    }

    @Test
    public void testBuildGraph_EdgesHaveEndpoints() {
        YNet net = spec.getRootNet();
        NetGraph graph = YawlNetGraphBuilder.build(net);
        for (YawlNetGraphBuilder.GraphEdge edge : graph.edges()) {
            assertNotNull("Edge fromId should not be null", edge.fromId());
            assertNotNull("Edge toId should not be null", edge.toId());
            assertNotEquals("Edge endpoints should differ", edge.fromId(), edge.toId());
        }
    }

    @Test
    public void testBuildGraph_NodesListIsUnmodifiable() {
        YNet net = spec.getRootNet();
        NetGraph graph = YawlNetGraphBuilder.build(net);
        try {
            graph.nodes().clear();
            fail("Nodes list should be unmodifiable");
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    public void testBuildGraph_InputConditionHasCorrectShape() {
        YNet net = spec.getRootNet();
        NetGraph graph = YawlNetGraphBuilder.build(net);
        boolean hasInputCondition = graph.nodes().stream()
                .anyMatch(n -> n.shape() == YawlNetGraphBuilder.NodeShape.INPUT_CONDITION);
        assertTrue("Graph should have an INPUT_CONDITION node", hasInputCondition);
    }

    @Test
    public void testBuildGraph_OutputConditionHasCorrectShape() {
        YNet net = spec.getRootNet();
        NetGraph graph = YawlNetGraphBuilder.build(net);
        boolean hasOutputCondition = graph.nodes().stream()
                .anyMatch(n -> n.shape() == YawlNetGraphBuilder.NodeShape.OUTPUT_CONDITION);
        assertTrue("Graph should have an OUTPUT_CONDITION node", hasOutputCondition);
    }
}
