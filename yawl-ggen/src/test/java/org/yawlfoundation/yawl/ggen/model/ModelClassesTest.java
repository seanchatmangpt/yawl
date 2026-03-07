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
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.ggen.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for model classes.
 */
@DisplayName("Model Classes Tests")
class ModelClassesTest {

    @Test
    @DisplayName("TaskDef should create atomic task")
    void taskDefShouldCreateAtomicTask() {
        TaskDef task = TaskDef.atomic("triage", "Triage Assessment");

        assertEquals("triage", task.id());
        assertEquals("Triage Assessment", task.name());
        assertEquals("atomic", task.type());
        assertFalse(task.isComposite());
        assertFalse(task.isMultiple());
    }

    @Test
    @DisplayName("TaskDef should create composite task")
    void taskDefShouldCreateCompositeTask() {
        TaskDef task = TaskDef.composite("review", "Review Process", "review_subprocess");

        assertTrue(task.isComposite());
        assertEquals("review_subprocess", task.decomposesTo());
    }

    @Test
    @DisplayName("TaskDef should detect resource requirement")
    void taskDefShouldDetectResourceRequirement() {
        TaskDef task = TaskDef.withResource("surgery", "Surgery", "Surgeon");

        assertTrue(task.hasResource());
        assertEquals("Surgeon", task.resourceClass());
    }

    @Test
    @DisplayName("DataObjectDef should create simple data object")
    void dataObjectDefShouldCreateSimpleDataObject() {
        DataObjectDef data = DataObjectDef.string("patient_id", "Patient ID");

        assertEquals("patient_id", data.id());
        assertEquals("Patient ID", data.name());
        assertEquals("string", data.type());
    }

    @Test
    @DisplayName("ConstraintDef should create sequence constraint")
    void constraintDefShouldCreateSequenceConstraint() {
        ConstraintDef constraint = ConstraintDef.sequence("task1", "task2");

        assertEquals("sequence", constraint.type());
        assertEquals("task1", constraint.source());
        assertEquals("task2", constraint.target());
    }

    @Test
    @DisplayName("ConstraintDef should create parallel constraint")
    void constraintDefShouldCreateParallelConstraint() {
        ConstraintDef constraint = ConstraintDef.parallel("task1", "task2");

        assertEquals("parallel", constraint.type());
    }

    @Test
    @DisplayName("ConstraintDef should create conditional constraint")
    void constraintDefShouldCreateConditionalConstraint() {
        ConstraintDef constraint = ConstraintDef.conditionalSequence("task1", "task2", "approved == true");

        assertTrue(constraint.isConditional());
        assertEquals("approved == true", constraint.condition());
    }

    @Test
    @DisplayName("CancellationRegionDef should create simple region")
    void cancellationRegionDefShouldCreateSimpleRegion() {
        CancellationRegionDef region = CancellationRegionDef.of(
            "emergency",
            List.of("registration", "insurance")
        );

        assertEquals("emergency", region.triggerTask());
        assertEquals(2, region.cancelledTasks().size());
        assertFalse(region.isConditional());
    }

    @Test
    @DisplayName("CancellationRegionDef should create conditional region")
    void cancellationRegionDefShouldCreateConditionalRegion() {
        CancellationRegionDef region = CancellationRegionDef.conditional(
            "emergency",
            List.of("registration"),
            "critical == true"
        );

        assertTrue(region.isConditional());
        assertEquals("critical == true", region.condition());
    }

    @Test
    @DisplayName("GraphNode should create task node")
    void graphNodeShouldCreateTaskNode() {
        GraphNode node = GraphNode.task("triage_node", "Triage", "triage");

        assertTrue(node.isTask());
        assertFalse(node.isGateway());
        assertFalse(node.isCondition());
    }

    @Test
    @DisplayName("GraphNode should create gateway nodes")
    void graphNodeShouldCreateGatewayNodes() {
        GraphNode xor = GraphNode.xorGateway("xor1", "XOR Split");
        GraphNode and = GraphNode.andGateway("and1", "AND Split");
        GraphNode or = GraphNode.orGateway("or1", "OR Split");

        assertTrue(xor.isGateway());
        assertTrue(and.isGateway());
        assertTrue(or.isGateway());
        assertTrue(or.isOrGateway());
    }

    @Test
    @DisplayName("GraphEdge should create conditional edge")
    void graphEdgeShouldCreateConditionalEdge() {
        GraphEdge edge = GraphEdge.conditional("node1", "node2", "approved == true");

        assertTrue(edge.isConditional());
        assertEquals("approved == true", edge.condition());
    }

    @Test
    @DisplayName("ProcessSpec should serialize to JSON")
    void processSpecShouldSerializeToJson() {
        ProcessSpec spec = ProcessSpec.of("TestProcess", List.of(
            TaskDef.atomic("task1", "Task 1")
        ));

        String json = spec.toJson();

        assertNotNull(json);
        assertTrue(json.contains("TestProcess"));
        assertTrue(json.contains("task1"));
    }

    @Test
    @DisplayName("ProcessGraph should find nodes and edges")
    void processGraphShouldFindNodesAndEdges() {
        ProcessGraph graph = new ProcessGraph(
            List.of(
                GraphNode.inputCondition("input"),
                GraphNode.task("task1", "Task 1", "task1_ref"),
                GraphNode.outputCondition("output")
            ),
            List.of(
                GraphEdge.of("input", "task1"),
                GraphEdge.of("task1", "output")
            ),
            "input",
            "output",
            List.of(),
            List.of(),
            List.of(),
            Map.of()
        );

        assertNotNull(graph.findNode("task1"));
        assertEquals(1, graph.edgesFrom("input").size());
        assertEquals(1, graph.edgesTo("output").size());
        assertEquals(3, graph.nodeCount());
        assertEquals(2, graph.edgeCount());
    }

    @Test
    @DisplayName("ValidationResult should create success result")
    void validationResultShouldCreateSuccessResult() {
        ValidationResult result = ValidationResult.success();

        assertTrue(result.valid());
        assertTrue(result.xsdValid());
        assertTrue(result.soundnessValid());
        assertTrue(result.executionValid());
    }

    @Test
    @DisplayName("ValidationResult should create XSD failure result")
    void validationResultShouldCreateXsdFailureResult() {
        ValidationResult result = ValidationResult.xsdFailure(
            List.of("Line 10: Invalid element")
        );

        assertFalse(result.valid());
        assertFalse(result.xsdValid());
        assertEquals(1, result.xsdErrors().size());
    }

    @Test
    @DisplayName("ValidationResult should create soundness failure result")
    void validationResultShouldCreateSoundnessFailureResult() {
        ValidationResult result = ValidationResult.soundnessFailure(
            List.of("task1"),
            List.of("task2")
        );

        assertFalse(result.valid());
        assertFalse(result.soundnessValid());
        assertEquals(1, result.deadlocks().size());
        assertEquals(1, result.lackOfSync().size());
    }

    @Test
    @DisplayName("ValidationResult should provide summary")
    void validationResultShouldProvideSummary() {
        ValidationResult success = ValidationResult.success();
        assertEquals("Validation passed", success.getSummary());

        ValidationResult failure = ValidationResult.xsdFailure(List.of("Error"));
        assertTrue(failure.getSummary().contains("XSD errors"));
    }
}
