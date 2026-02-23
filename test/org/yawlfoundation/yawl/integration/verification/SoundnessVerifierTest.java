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

package org.yawlfoundation.yawl.integration.verification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for SoundnessVerifier.
 *
 * <p>All tests use real Petri net structures (no mocks). Tests verify:
 * <ul>
 *   <li>Sound workflows (no findings)</li>
 *   <li>Detection of all 7 deadlock patterns</li>
 *   <li>Proper classification by severity</li>
 *   <li>Soundness reporting accuracy</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@Tag("integration")
public class SoundnessVerifierTest {

    private Map<String, Set<String>> placeToTransitions;
    private Map<String, Set<String>> transitionToPlaces;

    @BeforeEach
    void setUp() {
        placeToTransitions = new HashMap<>();
        transitionToPlaces = new HashMap<>();
    }

    // =========================================================================
    // Happy Path Tests
    // =========================================================================

    /**
     * Test: A simple linear workflow A→B→C produces no findings.
     *
     * <p>Petri net structure:
     * <pre>
     *   start → [task_a] → p_ab → [task_b] → p_bc → [task_c] → end
     * </pre>
     */
    @Test
    void testSoundWorkflow_noFindings() {
        // Build net: start → A → B → C → end
        placeToTransitions.put("start", Set.of("task_a"));
        transitionToPlaces.put("task_a", Set.of("p_ab"));

        placeToTransitions.put("p_ab", Set.of("task_b"));
        transitionToPlaces.put("task_b", Set.of("p_bc"));

        placeToTransitions.put("p_bc", Set.of("task_c"));
        transitionToPlaces.put("task_c", Set.of("end"));

        placeToTransitions.put("end", Set.of());

        var verifier = new SoundnessVerifier(
            placeToTransitions, transitionToPlaces, "start", "end"
        );
        VerificationReport report = verifier.verify();

        assertTrue(report.isSound(), "Linear workflow should be sound");
        assertEquals(0, report.deadlockCount(), "No deadlock patterns should be detected");
        assertEquals(0, report.warningCount(), "No warnings expected");
        assertEquals(0, report.infoCount(), "No info findings expected");
        assertEquals(0, report.findings().size(), "Findings list should be empty");
    }

    /**
     * Test: Parallel (OR-join) workflow is sound.
     *
     * <p>Petri net structure:
     * <pre>
     *   start → [fork] → p1 → [task_a] → p_sync
     *                  → p2 → [task_b] → ↗
     *   p_sync → [end_task] → end
     * </pre>
     */
    @Test
    void testSoundParallelWorkflow_noFindings() {
        // Fork
        placeToTransitions.put("start", Set.of("fork"));
        transitionToPlaces.put("fork", Set.of("p1", "p2"));

        // Branch A
        placeToTransitions.put("p1", Set.of("task_a"));
        transitionToPlaces.put("task_a", Set.of("p_sync"));

        // Branch B
        placeToTransitions.put("p2", Set.of("task_b"));
        transitionToPlaces.put("task_b", Set.of("p_sync"));

        // Join
        placeToTransitions.put("p_sync", Set.of("end_task"));
        transitionToPlaces.put("end_task", Set.of("end"));

        placeToTransitions.put("end", Set.of());

        var verifier = new SoundnessVerifier(
            placeToTransitions, transitionToPlaces, "start", "end"
        );
        VerificationReport report = verifier.verify();

        assertTrue(report.isSound(), "Parallel workflow should be sound");
        assertEquals(0, report.deadlockCount(), "No deadlocks expected");
    }

    // =========================================================================
    // Pattern Detection Tests
    // =========================================================================

    /**
     * Test: UNREACHABLE_TASK detection.
     *
     * <p>Task 'orphan_task' has no incoming arcs from reachable places.
     * <pre>
     *   start → [task_a] → end
     *   (somewhere) → [orphan_task] → (nowhere reachable)
     * </pre>
     */
    @Test
    void testUnreachableTask_detected() {
        // Main path: start → task_a → end
        placeToTransitions.put("start", Set.of("task_a"));
        transitionToPlaces.put("task_a", Set.of("end"));

        // Orphan task in disconnected place
        placeToTransitions.put("p_orphan", Set.of("orphan_task"));
        transitionToPlaces.put("orphan_task", Set.of("p_output"));
        placeToTransitions.put("p_output", Set.of());

        placeToTransitions.put("end", Set.of());

        var verifier = new SoundnessVerifier(
            placeToTransitions, transitionToPlaces, "start", "end"
        );
        VerificationReport report = verifier.verify();

        assertFalse(report.isSound(), "Workflow with unreachable task should be unsound");
        assertTrue(report.deadlockCount() > 0, "Should detect at least one error");

        boolean hasUnreachableTask = report.findings().stream()
            .anyMatch(f -> f.pattern() == DeadlockPattern.UNREACHABLE_TASK
                && f.taskId().equals("orphan_task"));
        assertTrue(hasUnreachableTask, "Should detect UNREACHABLE_TASK pattern");
    }

    /**
     * Test: ORPHANED_PLACE detection.
     *
     * <p>A place 'trap' has no outgoing transitions (dead-end).
     * <pre>
     *   start → [task_a] → trap (no outputs!)
     * </pre>
     */
    @Test
    void testOrphanedPlace_detected() {
        // Build net with orphaned place
        placeToTransitions.put("start", Set.of("task_a"));
        transitionToPlaces.put("task_a", Set.of("trap"));
        placeToTransitions.put("trap", Set.of()); // No outputs → orphaned!

        var verifier = new SoundnessVerifier(
            placeToTransitions, transitionToPlaces, "start", "trap"
        );
        VerificationReport report = verifier.verify();

        assertFalse(report.isSound(), "Workflow with orphaned place should be unsound");
        assertTrue(report.deadlockCount() > 0, "Should detect at least one error");

        boolean hasOrphanedPlace = report.findings().stream()
            .anyMatch(f -> f.pattern() == DeadlockPattern.ORPHANED_PLACE
                && f.taskId().equals("trap"));
        assertTrue(hasOrphanedPlace, "Should detect ORPHANED_PLACE pattern");
    }

    /**
     * Test: LIVELOCK detection.
     *
     * <p>A cycle A → B → A with no exit path.
     * <pre>
     *   start → [task_a] → p1 → [task_b] → p_a (back to task_a)
     *   No path to end from the cycle.
     * </pre>
     */
    @Test
    void testLivelock_detected() {
        // Entry point
        placeToTransitions.put("start", Set.of("task_a"));

        // Cycle: task_a → p1 → task_b → p_a → (back to task_a)
        transitionToPlaces.put("task_a", Set.of("p1"));
        placeToTransitions.put("p1", Set.of("task_b"));

        transitionToPlaces.put("task_b", Set.of("p_a"));
        placeToTransitions.put("p_a", Set.of("task_a")); // Cycle back!

        // End place exists but unreachable from cycle
        placeToTransitions.put("end", Set.of());

        var verifier = new SoundnessVerifier(
            placeToTransitions, transitionToPlaces, "start", "end"
        );
        VerificationReport report = verifier.verify();

        assertFalse(report.isSound(), "Workflow with livelock should be unsound");
        assertTrue(report.deadlockCount() > 0, "Should detect at least one error");

        boolean hasLivelock = report.findings().stream()
            .anyMatch(f -> f.pattern() == DeadlockPattern.LIVELOCK);
        assertTrue(hasLivelock, "Should detect LIVELOCK pattern");
    }

    /**
     * Test: IMPLICIT_DEADLOCK detection.
     *
     * <p>AND-join with one unreachable input:
     * <pre>
     *   start → [fork] → p1 → [task_a] → p_join
     *                 → p2 (unreachable) → p_join
     *   p_join → [and_join] (waits forever!)
     * </pre>
     */
    @Test
    void testImplicitDeadlock_detected() {
        // Fork: start → p1, p2
        placeToTransitions.put("start", Set.of("fork"));
        transitionToPlaces.put("fork", Set.of("p1", "p2_unreachable"));

        // Path 1: p1 → task_a → p_join
        placeToTransitions.put("p1", Set.of("task_a"));
        transitionToPlaces.put("task_a", Set.of("p_join"));

        // Path 2: p2 is unreachable (fork only produces p1)
        // But p2_unreachable also feeds into p_join somehow
        placeToTransitions.put("p2_unreachable", Set.of("task_b"));
        transitionToPlaces.put("task_b", Set.of("p_join"));

        // AND-join waits for both inputs
        placeToTransitions.put("p_join", Set.of("and_join"));
        transitionToPlaces.put("and_join", Set.of("end"));

        placeToTransitions.put("end", Set.of());

        var verifier = new SoundnessVerifier(
            placeToTransitions, transitionToPlaces, "start", "end"
        );
        VerificationReport report = verifier.verify();

        assertFalse(report.isSound(), "AND-join with unreachable input is unsound");
        assertTrue(report.deadlockCount() > 0, "Should detect error");

        boolean hasImplicitDeadlock = report.findings().stream()
            .anyMatch(f -> f.pattern() == DeadlockPattern.IMPLICIT_DEADLOCK);
        assertTrue(hasImplicitDeadlock, "Should detect IMPLICIT_DEADLOCK pattern");
    }

    /**
     * Test: IMPROPER_TERMINATION detection.
     *
     * <p>Multiple transitions output directly to end without merge:
     * <pre>
     *   start → [fork] → p1 → [task_a] → end
     *                 → p2 → [task_b] → end (parallel, no merge!)
     * </pre>
     */
    @Test
    void testImproperTermination_detected() {
        // Fork
        placeToTransitions.put("start", Set.of("fork"));
        transitionToPlaces.put("fork", Set.of("p1", "p2"));

        // Path 1: straight to end
        placeToTransitions.put("p1", Set.of("task_a"));
        transitionToPlaces.put("task_a", Set.of("end"));

        // Path 2: also straight to end (no merge!)
        placeToTransitions.put("p2", Set.of("task_b"));
        transitionToPlaces.put("task_b", Set.of("end"));

        placeToTransitions.put("end", Set.of());

        var verifier = new SoundnessVerifier(
            placeToTransitions, transitionToPlaces, "start", "end"
        );
        VerificationReport report = verifier.verify();

        assertFalse(report.isSound(), "Improper termination is unsound");
        assertTrue(report.deadlockCount() > 0, "Should detect error");

        boolean hasImproperTermination = report.findings().stream()
            .anyMatch(f -> f.pattern() == DeadlockPattern.IMPROPER_TERMINATION);
        assertTrue(hasImproperTermination, "Should detect IMPROPER_TERMINATION pattern");
    }

    /**
     * Test: MISSING_OR_JOIN detection.
     *
     * <p>Multiple transitions feed the same place without merge:
     * <pre>
     *   start → [fork] → p1 → [task_a] → p_converge
     *                 → p2 → [task_b] → ↗ (no explicit merge)
     * </pre>
     */
    @Test
    void testMissingOrJoin_detected() {
        // Fork
        placeToTransitions.put("start", Set.of("fork"));
        transitionToPlaces.put("fork", Set.of("p1", "p2"));

        // Both paths feed p_converge without merge
        placeToTransitions.put("p1", Set.of("task_a"));
        transitionToPlaces.put("task_a", Set.of("p_converge"));

        placeToTransitions.put("p2", Set.of("task_b"));
        transitionToPlaces.put("task_b", Set.of("p_converge"));

        // p_converge has multiple inputs (WARNING)
        placeToTransitions.put("p_converge", Set.of("end_task"));
        transitionToPlaces.put("end_task", Set.of("end"));

        placeToTransitions.put("end", Set.of());

        var verifier = new SoundnessVerifier(
            placeToTransitions, transitionToPlaces, "start", "end"
        );
        VerificationReport report = verifier.verify();

        // This might not fail soundness but should warn
        assertTrue(report.warningCount() > 0 || report.deadlockCount() > 0,
            "Should detect at least a warning for missing OR-join");

        boolean hasMissingOrJoin = report.findings().stream()
            .anyMatch(f -> f.pattern() == DeadlockPattern.MISSING_OR_JOIN);
        assertTrue(hasMissingOrJoin, "Should detect MISSING_OR_JOIN pattern");
    }

    // =========================================================================
    // Report Validation Tests
    // =========================================================================

    /**
     * Test: isSound() is false when ERROR-level findings exist.
     */
    @Test
    void testVerificationReport_isSound_false_whenErrors() {
        // Create a net with unreachable task
        placeToTransitions.put("start", Set.of("task_a"));
        transitionToPlaces.put("task_a", Set.of("end"));
        placeToTransitions.put("end", Set.of());

        // Orphan transition
        placeToTransitions.put("p_dead", Set.of("dead_task"));
        transitionToPlaces.put("dead_task", Set.of("p_out"));

        var verifier = new SoundnessVerifier(
            placeToTransitions, transitionToPlaces, "start", "end"
        );
        VerificationReport report = verifier.verify();

        assertFalse(report.isSound(), "Should be unsound with ERROR findings");
        assertTrue(report.deadlockCount() > 0, "deadlockCount should be > 0");
        assertEquals(report.deadlockCount(), (int) report.findings().stream()
            .filter(f -> f.severity() == VerificationFinding.Severity.ERROR)
            .count(), "deadlockCount should match ERROR findings");
    }

    /**
     * Test: isSound() is true when no ERROR findings.
     */
    @Test
    void testVerificationReport_isSound_true_whenNoErrors() {
        // Simple sound net
        placeToTransitions.put("start", Set.of("task_a"));
        transitionToPlaces.put("task_a", Set.of("end"));
        placeToTransitions.put("end", Set.of());

        var verifier = new SoundnessVerifier(
            placeToTransitions, transitionToPlaces, "start", "end"
        );
        VerificationReport report = verifier.verify();

        assertTrue(report.isSound(), "Should be sound with no ERROR findings");
        assertEquals(0, report.deadlockCount(), "deadlockCount should be 0");
    }

    /**
     * Test: VerificationReport tracks verification time.
     */
    @Test
    void testVerificationReport_verificationTime_isRecorded() {
        // Build a net
        placeToTransitions.put("start", Set.of("task_a"));
        transitionToPlaces.put("task_a", Set.of("end"));
        placeToTransitions.put("end", Set.of());

        var verifier = new SoundnessVerifier(
            placeToTransitions, transitionToPlaces, "start", "end"
        );
        VerificationReport report = verifier.verify();

        assertNotNull(report.verificationTime(), "verificationTime should be set");
        assertFalse(report.verificationTime().isNegative(), "Duration should not be negative");
    }

    /**
     * Test: VerificationReport provides human-readable summary.
     */
    @Test
    void testVerificationReport_summary_isHumanReadable() {
        // Sound net
        placeToTransitions.put("start", Set.of("task_a"));
        transitionToPlaces.put("task_a", Set.of("end"));
        placeToTransitions.put("end", Set.of());

        var verifier = new SoundnessVerifier(
            placeToTransitions, transitionToPlaces, "start", "end"
        );
        VerificationReport report = verifier.verify();

        assertNotNull(report.summary(), "summary should not be null");
        assertTrue(report.summary().contains("sound"), "summary should mention soundness");
    }

    /**
     * Test: VerificationFinding toString() formats correctly.
     */
    @Test
    void testVerificationFinding_toString_formatted() {
        var finding = new VerificationFinding(
            DeadlockPattern.UNREACHABLE_TASK,
            "task_x",
            "Example description",
            VerificationFinding.Severity.ERROR
        );

        String str = finding.toString();
        assertTrue(str.contains("ERROR"), "Should contain severity");
        assertTrue(str.contains("Unreachable Task"), "Should contain pattern name");
        assertTrue(str.contains("task_x"), "Should contain task ID");
        assertTrue(str.contains("Example description"), "Should contain description");
    }

    /**
     * Test: DeadlockPattern enums have valid SPARQL queries.
     */
    @Test
    void testDeadlockPattern_sparqlQueries_nonEmpty() {
        for (DeadlockPattern pattern : DeadlockPattern.values()) {
            assertNotNull(pattern.displayName(), pattern + " should have displayName");
            assertNotNull(pattern.sparqlQuery(), pattern + " should have SPARQL query");
            assertFalse(pattern.sparqlQuery().isBlank(), pattern + " SPARQL should not be empty");
            assertNotNull(pattern.remediation(), pattern + " should have remediation");
        }
    }

    /**
     * Test: Verification handles large net gracefully.
     */
    @Test
    void testVerification_largeNet_completesQuickly() {
        // Build a large linear net: start → t1 → p1 → t2 → p2 → ... → t100 → end
        String current = "start";
        for (int i = 1; i <= 100; i++) {
            String nextPlace = i == 100 ? "end" : "p" + i;
            String task = "t" + i;

            placeToTransitions.put(current, Set.of(task));
            transitionToPlaces.put(task, Set.of(nextPlace));

            current = nextPlace;
        }
        placeToTransitions.put("end", Set.of());

        var verifier = new SoundnessVerifier(
            placeToTransitions, transitionToPlaces, "start", "end"
        );

        long startMs = System.currentTimeMillis();
        VerificationReport report = verifier.verify();
        long elapsedMs = System.currentTimeMillis() - startMs;

        assertTrue(report.isSound(), "Large sound net should verify as sound");
        assertTrue(elapsedMs < 1000, "Verification should complete within 1 second");
    }

    /**
     * Test: Multiple DEAD_TRANSITION errors detected.
     */
    @Test
    void testDeadTransition_multiple_detected() {
        // Main path
        placeToTransitions.put("start", Set.of("task_a"));
        transitionToPlaces.put("task_a", Set.of("end"));

        // Two disconnected dead tasks
        placeToTransitions.put("p_dead1", Set.of("dead_task_1"));
        transitionToPlaces.put("dead_task_1", Set.of("p_out1"));

        placeToTransitions.put("p_dead2", Set.of("dead_task_2"));
        transitionToPlaces.put("dead_task_2", Set.of("p_out2"));

        placeToTransitions.put("end", Set.of());
        placeToTransitions.put("p_out1", Set.of());
        placeToTransitions.put("p_out2", Set.of());

        var verifier = new SoundnessVerifier(
            placeToTransitions, transitionToPlaces, "start", "end"
        );
        VerificationReport report = verifier.verify();

        assertTrue(report.deadlockCount() >= 2, "Should detect at least 2 dead transitions");
    }
}
