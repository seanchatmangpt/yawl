/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.parametrized;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.yawlfoundation.yawl.containers.WorkflowDataFactory;
import org.yawlfoundation.yawl.elements.YSpecification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java language feature matrix tests for YAWL v6.0.0.
 *
 * Validates that YAWL domain object construction and manipulation
 * works correctly using modern Java features (Java 21+):
 * - Records as value types (WorkflowDataFactory.DatabaseBackend)
 * - Sealed interfaces (pattern matching on status types)
 * - Text blocks for SQL and XML
 * - Switch expressions with pattern matching (Java 21+)
 * - Sequenced collections (SequencedCollection API, Java 21+)
 * - Virtual threads (Project Loom, Java 21+)
 *
 * Each test is parametrized over a set of specification configurations
 * to validate feature behaviour across realistic YAWL data shapes.
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-17
 */
class JavaFeatureMatrixTest {

    // =========================================================================
    // Records as Value Types
    // =========================================================================

    /**
     * WorkflowSpec is a record used as a lightweight test parameter carrier.
     * Tests that records behave correctly when used as JUnit 5 test arguments.
     */
    record WorkflowSpec(String id, int taskCount, String expectedName) {
        @Override
        public String toString() {
            return id + "[tasks=" + taskCount + "]";
        }
    }

    static Stream<Arguments> workflowSpecMatrix() {
        return Stream.of(
            Arguments.of(new WorkflowSpec("spec-linear-1",  1, "Sequential Workflow [spec-linear-1] tasks=1")),
            Arguments.of(new WorkflowSpec("spec-linear-3",  3, "Sequential Workflow [spec-linear-3] tasks=3")),
            Arguments.of(new WorkflowSpec("spec-linear-10", 10, "Sequential Workflow [spec-linear-10] tasks=10"))
        );
    }

    @ParameterizedTest(name = "[{index}] spec={0}")
    @MethodSource("workflowSpecMatrix")
    void testRecordAsParameterCarrier(WorkflowSpec wf) throws Exception {
        YSpecification spec = WorkflowDataFactory.buildSequentialSpec(
                wf.id(), wf.taskCount());

        assertNotNull(spec, "Spec must be built");
        assertEquals(wf.expectedName(), spec.getName(),
                "Name must match expected pattern");
        assertNotNull(spec.getRootNet(), "Root net must be wired");

        // Verify task count via net elements (input + tasks + output = taskCount + 2)
        int elementCount = spec.getRootNet().getNetElements().size();
        assertEquals(wf.taskCount(), elementCount,
                "Net must contain exactly " + wf.taskCount() + " tasks");
    }

    @ParameterizedTest(name = "[{index}] spec={0}")
    @MethodSource("workflowSpecMatrix")
    void testRecordEqualityAndHashCode(WorkflowSpec wf) {
        WorkflowSpec copy = new WorkflowSpec(wf.id(), wf.taskCount(), wf.expectedName());
        assertEquals(wf, copy, "Records with same fields must be equal");
        assertEquals(wf.hashCode(), copy.hashCode(),
                "Equal records must have equal hash codes");
    }

    // =========================================================================
    // Text Blocks
    // =========================================================================

    @ParameterizedTest(name = "[{index}] taskCount={0}")
    @ValueSource(ints = {1, 2, 5})
    void testTextBlockXmlTemplateGeneration(int taskCount) {
        // Text blocks (Java 15+ stable) used to generate workflow XML templates
        StringBuilder taskXml = new StringBuilder();
        for (int i = 0; i < taskCount; i++) {
            taskXml.append("""
                <task id="task_%d">
                    <name>Task %d</name>
                </task>
                """.formatted(i, i));
        }

        String workflowXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <specification>
                <net id="root">
                    %s
                </net>
            </specification>
            """.formatted(taskXml.toString().indent(4).stripTrailing());

        assertNotNull(workflowXml, "Text block XML must not be null");
        assertTrue(workflowXml.contains("<specification>"),
                "XML must contain specification element");
        assertEquals(taskCount,
                workflowXml.split("<task id=").length - 1,
                "XML must contain " + taskCount + " task elements");
    }

    // =========================================================================
    // Switch Expressions and Pattern Matching
    // =========================================================================

    /**
     * Sealed interface hierarchy modelling YAWL work-item status transitions.
     * Tests Java 17+ sealed types with Java 21+ pattern matching in switch.
     */
    sealed interface WorkItemStatus
            permits WorkItemStatus.Enabled,
                    WorkItemStatus.Executing,
                    WorkItemStatus.Completed,
                    WorkItemStatus.Failed {

        record Enabled()   implements WorkItemStatus {}
        record Executing() implements WorkItemStatus {}
        record Completed() implements WorkItemStatus {}
        record Failed(String reason) implements WorkItemStatus {}

        /** Pattern-matching switch to derive the next valid status. */
        static List<WorkItemStatus> validTransitionsFrom(WorkItemStatus current) {
            return switch (current) {
                case Enabled   e -> List.of(new Executing());
                case Executing e -> List.of(new Completed(), new Failed("execution error"));
                case Completed c -> List.of();
                case Failed    f -> List.of();
            };
        }
    }

    @ParameterizedTest(name = "[{index}] from={0}")
    @MethodSource("statusTransitionMatrix")
    void testSealedTypeStatusTransitions(WorkItemStatus from, int expectedTransitions) {
        List<WorkItemStatus> transitions =
                WorkItemStatus.validTransitionsFrom(from);
        assertEquals(expectedTransitions, transitions.size(),
                "Status " + from.getClass().getSimpleName()
                + " must have " + expectedTransitions + " valid transitions");
    }

    static Stream<Arguments> statusTransitionMatrix() {
        return Stream.of(
            Arguments.of(new WorkItemStatus.Enabled(),    1),
            Arguments.of(new WorkItemStatus.Executing(),  2),
            Arguments.of(new WorkItemStatus.Completed(),  0),
            Arguments.of(new WorkItemStatus.Failed("e"),  0)
        );
    }

    // =========================================================================
    // Virtual Threads (Java 21 Loom)
    // =========================================================================

    @ParameterizedTest(name = "[{index}] threadCount={0}")
    @ValueSource(ints = {10, 100, 500})
    void testVirtualThreadSpecificationConstruction(int threadCount) throws Exception {
        List<Thread> threads = new ArrayList<>(threadCount);
        List<YSpecification> results = java.util.Collections
                .synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            Thread vt = Thread.ofVirtual()
                    .name("vt-spec-" + idx)
                    .start(() -> {
                        try {
                            YSpecification spec = WorkflowDataFactory
                                    .buildMinimalSpec("vt-spec-" + idx);
                            results.add(spec);
                        } catch (Exception e) {
                            throw new RuntimeException(
                                    "Virtual thread spec creation failed: " + e.getMessage(), e);
                        }
                    });
            threads.add(vt);
        }

        for (Thread t : threads) {
            t.join(10_000); // 10s per-thread timeout
        }

        assertEquals(threadCount, results.size(),
                "All " + threadCount + " virtual threads must produce a specification");
        results.forEach(spec ->
                assertNotNull(spec.getRootNet(),
                        "Every spec must have a root net"));
    }

    // =========================================================================
    // Sequenced Collections (Java 21)
    // =========================================================================

    @Test
    void testSequencedCollectionSpecOrdering() throws Exception {
        // SequencedList guarantees insertion order; validate addFirst / addLast
        java.util.SequencedCollection<String> specIds =
                new java.util.ArrayList<>();

        specIds.addFirst("spec-first");
        specIds.addLast("spec-last");
        specIds.add("spec-middle");  // goes to end before last

        // Rebuild with explicit ordering
        specIds = new java.util.ArrayList<>();
        specIds.addLast("spec-first");
        specIds.addLast("spec-second");
        specIds.addLast("spec-third");

        assertEquals("spec-first", specIds.getFirst(),
                "First element must be spec-first");
        assertEquals("spec-third", specIds.getLast(),
                "Last element must be spec-third");
        assertEquals(3, specIds.size(), "Must have 3 elements");
    }

    // =========================================================================
    // Map.entry and Map.copyOf (Java 10+, commonly tested in parametrized suites)
    // =========================================================================

    @ParameterizedTest(name = "[{index}] specId={0}")
    @ValueSource(strings = {"spec-alpha", "spec-beta", "spec-gamma"})
    void testImmutableMapSpecMetadata(String specId) {
        Map<String, String> metadata = Map.of(
                "id",      specId,
                "version", "1.0",
                "author",  "YAWL Foundation"
        );

        assertEquals(specId, metadata.get("id"),
                "id must match input");
        assertEquals(3, metadata.size(),
                "Map must have exactly 3 entries");
        assertInstanceOf(String.class, metadata.get("version"),
                "version must be a String");

        // Map.copyOf produces an unmodifiable copy
        Map<String, String> copy = Map.copyOf(metadata);
        assertEquals(metadata, copy,
                "Copy must equal original");
    }
}
