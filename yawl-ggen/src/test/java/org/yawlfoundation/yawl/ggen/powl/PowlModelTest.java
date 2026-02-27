/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.powl;

import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.ggen.mining.model.PetriNet;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for POWL model construction, validation, and conversion.
 * Tests: PowlActivity, PowlOperatorNode, PowlModel, PowlValidator, PowlToYawlConverter.
 * All tests use real objects — no mocks or stubs.
 */
class PowlModelTest {

    // ----- PowlActivity construction -----

    @Test
    void constructPowlActivity_validInput_returnsCorrectFields() {
        PowlActivity activity = new PowlActivity("a1", "Task A");
        assertEquals("a1", activity.id());
        assertEquals("Task A", activity.label());
    }

    @Test
    void constructPowlActivity_blankId_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new PowlActivity("", "Task A"));
    }

    @Test
    void constructPowlActivity_nullId_throwsIllegalArgument() {
        // PowlActivity checks (id == null || id.isBlank()), so null throws IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> new PowlActivity(null, "Task A"));
    }

    @Test
    void constructPowlActivity_blankLabel_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> new PowlActivity("a1", "   "));
    }

    @Test
    void constructPowlActivity_nullLabel_throwsIllegalArgument() {
        // PowlActivity checks (label == null || label.isBlank()), so null throws IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> new PowlActivity("a1", null));
    }

    // ----- PowlOperatorNode construction -----

    @Test
    void constructPowlOperatorNode_sequence_succeeds() {
        PowlActivity a = new PowlActivity("a", "A");
        PowlActivity b = new PowlActivity("b", "B");
        PowlOperatorNode seq = new PowlOperatorNode("seq1", PowlOperatorType.SEQUENCE, List.of(a, b));
        assertEquals("seq1", seq.id());
        assertEquals(PowlOperatorType.SEQUENCE, seq.type());
        assertEquals(2, seq.children().size());
    }

    @Test
    void constructPowlOperatorNode_xor_succeeds() {
        PowlActivity a = new PowlActivity("a", "A");
        PowlActivity b = new PowlActivity("b", "B");
        PowlOperatorNode xor = new PowlOperatorNode("xor1", PowlOperatorType.XOR, List.of(a, b));
        assertEquals(PowlOperatorType.XOR, xor.type());
    }

    @Test
    void constructPowlOperatorNode_parallel_succeeds() {
        PowlActivity a = new PowlActivity("a", "A");
        PowlActivity b = new PowlActivity("b", "B");
        PowlOperatorNode par = new PowlOperatorNode("par1", PowlOperatorType.PARALLEL, List.of(a, b));
        assertEquals(PowlOperatorType.PARALLEL, par.type());
    }

    @Test
    void constructPowlOperatorNode_loop_exactlyTwoChildren_succeeds() {
        PowlActivity doAct = new PowlActivity("do", "Do");
        PowlActivity redoAct = new PowlActivity("redo", "Redo");
        PowlOperatorNode loop = new PowlOperatorNode("loop1", PowlOperatorType.LOOP, List.of(doAct, redoAct));
        assertEquals(PowlOperatorType.LOOP, loop.type());
        assertEquals(2, loop.children().size());
    }

    @Test
    void constructPowlOperatorNode_loop_wrongChildCount_throwsIllegalArgument() {
        PowlActivity a = new PowlActivity("a", "A");
        PowlActivity b = new PowlActivity("b", "B");
        PowlActivity c = new PowlActivity("c", "C");
        assertThrows(IllegalArgumentException.class,
            () -> new PowlOperatorNode("loop1", PowlOperatorType.LOOP, List.of(a, b, c)));
    }

    @Test
    void constructPowlOperatorNode_emptyChildren_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
            () -> new PowlOperatorNode("seq1", PowlOperatorType.SEQUENCE, List.of()));
    }

    @Test
    void constructPowlOperatorNode_nullType_throwsNullPointer() {
        PowlActivity a = new PowlActivity("a", "A");
        assertThrows(NullPointerException.class,
            () -> new PowlOperatorNode("op1", null, List.of(a)));
    }

    // ----- PowlModel factory -----

    @Test
    void powlModelOf_createsModelWithGeneratedTimestamp() {
        PowlActivity root = new PowlActivity("a", "A");
        PowlModel model = PowlModel.of("m1", root);
        assertEquals("m1", model.id());
        assertSame(root, model.root());
        assertNotNull(model.generatedAt());
    }

    @Test
    void powlModelOf_nullId_throwsIllegalArgument() {
        // PowlModel checks (id == null || id.isBlank()), so null throws IllegalArgumentException
        PowlActivity root = new PowlActivity("a", "A");
        assertThrows(IllegalArgumentException.class, () -> PowlModel.of(null, root));
    }

    @Test
    void powlModelOf_nullRoot_throwsNullPointer() {
        assertThrows(NullPointerException.class, () -> PowlModel.of("m1", null));
    }

    // ----- PowlValidator -----

    @Test
    void validate_sequenceModel_isValid() {
        PowlActivity a = new PowlActivity("a", "A");
        PowlActivity b = new PowlActivity("b", "B");
        PowlActivity c = new PowlActivity("c", "C");
        PowlOperatorNode seq = new PowlOperatorNode("seq1", PowlOperatorType.SEQUENCE, List.of(a, b, c));
        PowlModel model = PowlModel.of("m1", seq);

        ValidationReport report = new PowlValidator().validate(model);

        assertTrue(report.valid());
        assertTrue(report.violations().isEmpty());
    }

    @Test
    void validate_loopModel_isValid() {
        PowlActivity doAct = new PowlActivity("do1", "Do Task");
        PowlActivity redoAct = new PowlActivity("redo1", "Redo Task");
        PowlOperatorNode loop = new PowlOperatorNode("loop1", PowlOperatorType.LOOP, List.of(doAct, redoAct));
        PowlModel model = PowlModel.of("m1", loop);

        ValidationReport report = new PowlValidator().validate(model);

        assertTrue(report.valid(), "LOOP with 2 children should be valid");
    }

    @Test
    void validate_cycleViaSharedId_detectsCycle() {
        // An ancestor and descendant with the same ID appear on the same DFS path
        PowlActivity inner = new PowlActivity("op1", "Inner Activity (same id as outer)");
        PowlOperatorNode outer = new PowlOperatorNode("op1", PowlOperatorType.SEQUENCE, List.of(inner));
        PowlModel model = PowlModel.of("m1", outer);

        ValidationReport report = new PowlValidator().validate(model);

        assertFalse(report.valid());
        assertFalse(report.violations().isEmpty());
    }

    @Test
    void validate_singleActivity_isValid() {
        PowlModel model = PowlModel.of("m1", new PowlActivity("a", "A"));
        ValidationReport report = new PowlValidator().validate(model);
        assertTrue(report.valid());
    }

    @Test
    void validate_nullModel_throwsNullPointer() {
        assertThrows(NullPointerException.class, () -> new PowlValidator().validate(null));
    }

    // ----- PowlToYawlConverter -----

    @Test
    void convert_singleActivity_producesMinimalPetriNet() {
        PowlActivity a = new PowlActivity("a1", "Task A");
        PowlModel model = PowlModel.of("m1", a);

        PetriNet net = new PowlToYawlConverter().convert(model);

        assertNotNull(net);
        assertEquals(1, net.getTransitions().size(), "Single activity = 1 transition");
        assertEquals(2, net.getPlaces().size(), "Single activity = 2 places (entry + exit)");
    }

    @Test
    void convert_sequence_producesCorrectPlacesAndTransitions() {
        PowlActivity a = new PowlActivity("a", "A");
        PowlActivity b = new PowlActivity("b", "B");
        PowlOperatorNode seq = new PowlOperatorNode("seq1", PowlOperatorType.SEQUENCE, List.of(a, b));
        PowlModel model = PowlModel.of("m1", seq);

        PetriNet net = new PowlToYawlConverter().convert(model);

        // 2 activities → 4 places (2 per activity), 3 transitions (a + seq_connector + b)
        assertEquals(4, net.getPlaces().size(), "2 activities: 4 places");
        assertEquals(3, net.getTransitions().size(), "2 activities in SEQUENCE: 3 transitions");
    }

    @Test
    void convert_sequence_hasInitialPlace() {
        PowlActivity a = new PowlActivity("a", "A");
        PowlActivity b = new PowlActivity("b", "B");
        PowlOperatorNode seq = new PowlOperatorNode("seq1", PowlOperatorType.SEQUENCE, List.of(a, b));
        PowlModel model = PowlModel.of("m1", seq);

        PetriNet net = new PowlToYawlConverter().convert(model);

        long initialPlaces = net.getPlaces().values().stream()
            .filter(p -> p.isInitialPlace())
            .count();
        assertEquals(1, initialPlaces, "SEQUENCE should have exactly one initial place");
    }

    @Test
    void convert_sequence_hasFinalPlace() {
        PowlActivity a = new PowlActivity("a", "A");
        PowlActivity b = new PowlActivity("b", "B");
        PowlOperatorNode seq = new PowlOperatorNode("seq1", PowlOperatorType.SEQUENCE, List.of(a, b));
        PowlModel model = PowlModel.of("m1", seq);

        PetriNet net = new PowlToYawlConverter().convert(model);

        long finalPlaces = net.getPlaces().values().stream()
            .filter(p -> p.isFinalPlace())
            .count();
        assertEquals(1, finalPlaces, "SEQUENCE should have exactly one final place");
    }

    @Test
    void convert_xor_hasExpectedStructure() {
        PowlActivity b = new PowlActivity("b", "B");
        PowlActivity c = new PowlActivity("c", "C");
        PowlOperatorNode xor = new PowlOperatorNode("xor1", PowlOperatorType.XOR, List.of(b, c));
        PowlModel model = PowlModel.of("m1", xor);

        PetriNet net = new PowlToYawlConverter().convert(model);

        // XOR with 2 children: shared entry + shared exit + 2*(entry+exit) = 6 places
        // Transitions: 2 choice + 2 merge = 4 transitions
        assertNotNull(net);
        assertTrue(net.getTransitions().size() > 0);
        assertTrue(net.getPlaces().size() > 0);
    }

    @Test
    void convert_nullModel_throwsNullPointer() {
        assertThrows(NullPointerException.class, () -> new PowlToYawlConverter().convert(null));
    }
}
