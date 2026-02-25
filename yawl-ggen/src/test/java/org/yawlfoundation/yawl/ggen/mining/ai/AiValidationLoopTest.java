/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.ggen.mining.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.ggen.mining.generators.YawlSpecExporter;
import org.yawlfoundation.yawl.ggen.mining.model.Arc;
import org.yawlfoundation.yawl.ggen.mining.model.PetriNet;
import org.yawlfoundation.yawl.ggen.mining.model.Place;
import org.yawlfoundation.yawl.ggen.mining.model.Transition;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AiValidationLoop}: applyFixes deterministic transforms and
 * Q-invariant enforcement (never return unvalidated specs).
 *
 * <p>Network calls are avoided by testing applyFixes directly and using a concrete
 * inner subclass that overrides validate() for the exhaustion test.
 */
class AiValidationLoopTest {

    private AiValidationLoop loop;
    private PetriNet simplePetriNet;

    @BeforeEach
    void setUp() {
        YawlSpecExporter exporter = new YawlSpecExporter();
        OllamaValidationClient client =
                new OllamaValidationClient("http://localhost:11434", "qwen2.5-coder", 5);
        loop = new AiValidationLoop(exporter, client, 3);

        // Build minimal valid net: p_start(marking=1) → t1 → p_end
        simplePetriNet = new PetriNet("loop_test", "Loop Test");
        Place start = new Place("p_start", "Start", 1);
        Place end = new Place("p_end", "End");
        Transition t1 = new Transition("t1", "Task1");
        simplePetriNet.addPlace(start);
        simplePetriNet.addPlace(end);
        simplePetriNet.addTransition(t1);
        simplePetriNet.addArc(new Arc("a1", start, t1));
        simplePetriNet.addArc(new Arc("a2", t1, end));
    }

    /**
     * Scenario 1: applyFixes with a "todo" issue strips lines matching the TODO
     * comment pattern.
     */
    @Test
    void applyFixes_todoIssue_stripsCommentLines() {
        String xml = """
                <task id="t1">
                  // TODO: implement decomposition
                  <name>Task1</name>
                </task>
                """;

        String fixed = loop.applyFixes(xml, List.of("found todo comment needs removal"));

        assertFalse(fixed.contains("TODO"), "TODO comment line should be stripped");
        assertTrue(fixed.contains("<name>Task1</name>"), "Non-TODO content should be preserved");
    }

    /**
     * Scenario 2: applyFixes with an "empty return" issue replaces return ""; with
     * UnsupportedOperationException throw.
     */
    @Test
    void applyFixes_emptyReturnIssue_replacesWithThrow() {
        String xml = """
                <task id="t1">
                  return "";
                  <name>Task1</name>
                </task>
                """;

        String fixed = loop.applyFixes(xml, List.of("empty return statement detected"));

        assertFalse(fixed.contains("return \"\";"), "Empty return should be replaced");
        assertTrue(fixed.contains("UnsupportedOperationException"),
                "Empty return should be replaced with UnsupportedOperationException throw");
    }

    /**
     * Scenario 3: applyFixes with no matching issue leaves the XML unchanged.
     */
    @Test
    void applyFixes_noMatchingIssue_xmlUnchanged() {
        String xml = "<task id=\"t1\"><name>Task1</name></task>";

        String fixed = loop.applyFixes(xml, List.of("unrecognised issue category"));

        assertEquals(xml, fixed, "XML should be unchanged when no fix rule matches");
    }

    /**
     * Scenario 4: Q invariant — generateAndValidate with an always-failing client
     * throws ValidationExhaustedException after maxIterations=1.
     *
     * <p>Uses AlwaysFailingClient — a concrete subclass of OllamaValidationClient
     * with overridden validate() — to avoid any network calls.
     */
    @Test
    void generateAndValidate_alwaysFailingClient_throwsValidationExhaustedException() {
        YawlSpecExporter exporter = new YawlSpecExporter();
        AiValidationLoop exhaustingLoop = new AiValidationLoop(
                exporter, new AlwaysFailingClient(), 1);

        ValidationExhaustedException ex = assertThrows(
                ValidationExhaustedException.class,
                () -> exhaustingLoop.generateAndValidate(simplePetriNet),
                "Loop should throw ValidationExhaustedException when max iterations exhausted");

        assertEquals(1, ex.getIterationsAttempted(),
                "Should report exactly 1 iteration attempted");
        assertFalse(ex.getLastIssues().isEmpty(),
                "Last issues list should not be empty on failure");
    }

    // -----------------------------------------------------------------------
    // Inner test helper — concrete subclass (NOT a mock), real logic
    // -----------------------------------------------------------------------

    /**
     * Concrete OllamaValidationClient subclass that always returns a failing
     * ValidationResult. No network calls. Used to exercise the Q invariant.
     */
    static class AlwaysFailingClient extends OllamaValidationClient {

        AlwaysFailingClient() {
            super("http://localhost:11434", "test-model", 5);
        }

        @Override
        public ValidationResult validate(String yawlXml, int iteration) {
            return new ValidationResult(false,
                    List.of("persistent structural issue: missing decomposition"),
                    "",
                    iteration);
        }
    }
}
