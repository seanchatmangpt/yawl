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

package org.yawlfoundation.yawl.integration.immune;

import junit.framework.TestCase;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Chicago TDD test suite for WorkflowImmuneSystem.
 * Tests real integration with SoundnessVerifier, no mocks.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class WorkflowImmuneSystemTest extends TestCase {

    /**
     * Test that a deadlock-prone net emits a prediction.
     *
     * Net structure:
     * - p_start → t_split → {p1, p2}
     * - p1 and p2 are orphaned (no outgoing transitions)
     * - This creates an ORPHANED_PLACE deadlock pattern
     */
    public void testPredictOnDeadlockProneNetEmitsPrediction() {
        var config = ImmuneSystemConfig.defaults();
        var predictions = new ArrayList<DeadlockPrediction>();
        var immuneSystem = new WorkflowImmuneSystem(
            config,
            predictions::add
        );

        // Build deadlock-prone net
        Map<String, Set<String>> placeToTransitions = new HashMap<>();
        placeToTransitions.put("p_start", Set.of("t_split"));
        placeToTransitions.put("p1", Set.of());      // Orphaned!
        placeToTransitions.put("p2", Set.of());      // Orphaned!
        placeToTransitions.put("p_end", Set.of());

        Map<String, Set<String>> transitionToPlaces = new HashMap<>();
        transitionToPlaces.put("t_split", Set.of("p1", "p2"));

        // Run prediction
        var emitted = immuneSystem.predict(
            "case-001",
            "task-A",
            placeToTransitions,
            transitionToPlaces,
            "p_start",
            "p_end"
        );

        // Verify predictions emitted
        assertFalse("Expected deadlock predictions", emitted.isEmpty());
        assertEquals("Expected predictions for orphaned places", 2, emitted.size());

        // Verify listener was called
        assertEquals("Listener should have received all predictions", 2, predictions.size());
    }

    /**
     * Test that a sound net emits no predictions.
     *
     * Net structure: p_start → t1 → p_end (simple linear flow)
     */
    public void testPredictOnSoundNetEmitsNoPredictions() {
        var config = ImmuneSystemConfig.defaults();
        var predictions = new ArrayList<DeadlockPrediction>();
        var immuneSystem = new WorkflowImmuneSystem(
            config,
            predictions::add
        );

        // Build sound net
        Map<String, Set<String>> placeToTransitions = new HashMap<>();
        placeToTransitions.put("p_start", Set.of("t1"));
        placeToTransitions.put("p_end", Set.of());

        Map<String, Set<String>> transitionToPlaces = new HashMap<>();
        transitionToPlaces.put("t1", Set.of("p_end"));

        // Run prediction
        var emitted = immuneSystem.predict(
            "case-002",
            "task-B",
            placeToTransitions,
            transitionToPlaces,
            "p_start",
            "p_end"
        );

        // Verify no predictions emitted
        assertTrue("Expected no predictions for sound net", emitted.isEmpty());
        assertTrue("Listener should not have been called", predictions.isEmpty());
    }

    /**
     * Test that getReport() returns cumulative statistics.
     */
    public void testGetReportAfterPrediction() {
        var config = ImmuneSystemConfig.defaults();
        var predictions = new ArrayList<DeadlockPrediction>();
        var immuneSystem = new WorkflowImmuneSystem(
            config,
            predictions::add
        );

        // Build deadlock-prone net
        Map<String, Set<String>> placeToTransitions = new HashMap<>();
        placeToTransitions.put("p_start", Set.of("t_split"));
        placeToTransitions.put("p_orphan", Set.of());

        Map<String, Set<String>> transitionToPlaces = new HashMap<>();
        transitionToPlaces.put("t_split", Set.of("p_orphan"));

        // Run prediction
        immuneSystem.predict(
            "case-003",
            "task-C",
            placeToTransitions,
            transitionToPlaces,
            "p_start",
            "p_end"
        );

        // Get report
        var report = immuneSystem.getReport();

        // Verify report structure
        assertNotNull("Report should not be null", report);
        assertFalse("Report should have predictions", report.predictions().isEmpty());
        assertTrue("Cases scanned should be >= 1", report.casesScanned() >= 1);
        assertTrue("Deadlocks avoided should be >= 1", report.deadlocksAvoided() >= 1);
    }

    /**
     * Test that DeadlockPrediction record holds data correctly.
     */
    public void testDeadlockPredictionRecordHoldsData() {
        var instant = Instant.now();
        var affected = Set.of("task1", "place2");

        var prediction = new DeadlockPrediction(
            "case-456",
            "fired-task",
            "IMPLICIT_DEADLOCK",
            affected,
            0.95,
            instant
        );

        assertEquals("caseId should match", "case-456", prediction.caseId());
        assertEquals("firedTaskId should match", "fired-task", prediction.firedTaskId());
        assertEquals("findingType should match", "IMPLICIT_DEADLOCK", prediction.findingType());
        assertEquals("affectedElements should match", affected, prediction.affectedElements());
        assertEquals("confidence should match", 0.95, prediction.confidence(), 0.001);
        assertEquals("timestamp should match", instant, prediction.timestamp());
    }

    /**
     * Test that ImmuneSystemConfig.defaults() creates correct defaults.
     */
    public void testImmuneSystemConfigDefaults() {
        var config = ImmuneSystemConfig.defaults();

        assertFalse("autoCompensate should be false", config.autoCompensate());
        assertEquals("lookaheadDepth should be 3", 3, config.lookaheadDepth());
        assertTrue("ignoredPatterns should be empty", config.ignoredPatterns().isEmpty());
    }

    /**
     * Test that the listener is called when a deadlock is predicted.
     */
    public void testListenerCalledOnDeadlockPrediction() {
        var capturedPrediction = new AtomicReference<DeadlockPrediction>();
        var immuneSystem = new WorkflowImmuneSystem(
            ImmuneSystemConfig.defaults(),
            capturedPrediction::set
        );

        // Build deadlock-prone net
        Map<String, Set<String>> placeToTransitions = new HashMap<>();
        placeToTransitions.put("p_start", Set.of("t1"));
        placeToTransitions.put("p_orphan", Set.of());

        Map<String, Set<String>> transitionToPlaces = new HashMap<>();
        transitionToPlaces.put("t1", Set.of("p_orphan"));

        // Run prediction
        immuneSystem.predict(
            "case-789",
            "task-X",
            placeToTransitions,
            transitionToPlaces,
            "p_start",
            "p_end"
        );

        // Verify listener was called
        assertNotNull("Listener should have been called", capturedPrediction.get());
        assertEquals("Prediction caseId should match", "case-789", capturedPrediction.get().caseId());
    }

    /**
     * Test that DeadlockPrediction rejects invalid confidence.
     */
    public void testDeadlockPredictionRejectsInvalidConfidence() {
        try {
            new DeadlockPrediction(
                "case-001",
                "task-A",
                "IMPLICIT_DEADLOCK",
                Set.of(),
                1.5,  // Invalid: > 1.0
                Instant.now()
            );
            fail("Should have thrown IllegalArgumentException for confidence > 1.0");
        } catch (IllegalArgumentException e) {
            assertTrue("Error should mention confidence range", e.getMessage().contains("confidence"));
        }

        try {
            new DeadlockPrediction(
                "case-001",
                "task-A",
                "IMPLICIT_DEADLOCK",
                Set.of(),
                -0.1,  // Invalid: < 0.0
                Instant.now()
            );
            fail("Should have thrown IllegalArgumentException for confidence < 0.0");
        } catch (IllegalArgumentException e) {
            assertTrue("Error should mention confidence range", e.getMessage().contains("confidence"));
        }
    }

    /**
     * Test that getScheme() returns the correct value.
     */
    public void testGetSchemeReturnsYawlImmune() {
        var immuneSystem = new WorkflowImmuneSystem(
            ImmuneSystemConfig.defaults(),
            pred -> {}
        );

        assertEquals("Scheme should be 'yawl-immune'", "yawl-immune", immuneSystem.getScheme());
    }

    /**
     * Test that ignored patterns are not reported as predictions.
     */
    public void testIgnoredPatternsNotReported() {
        var config = new ImmuneSystemConfig(
            false,
            3,
            Set.of("Orphaned Place")  // Ignore orphaned places
        );
        var predictions = new ArrayList<DeadlockPrediction>();
        var immuneSystem = new WorkflowImmuneSystem(
            config,
            predictions::add
        );

        // Build net with orphaned place
        Map<String, Set<String>> placeToTransitions = new HashMap<>();
        placeToTransitions.put("p_start", Set.of("t1"));
        placeToTransitions.put("p_orphan", Set.of());

        Map<String, Set<String>> transitionToPlaces = new HashMap<>();
        transitionToPlaces.put("t1", Set.of("p_orphan"));

        // Run prediction
        var emitted = immuneSystem.predict(
            "case-ignored",
            "task-Y",
            placeToTransitions,
            transitionToPlaces,
            "p_start",
            "p_end"
        );

        // Verify no predictions emitted (orphaned place is ignored)
        assertTrue("Predictions should be empty when pattern is ignored", emitted.isEmpty());
        assertTrue("Listener should not have been called", predictions.isEmpty());
    }

    /**
     * Test that multiple cases are tracked in the report.
     */
    public void testMultipleCasesTrackedInReport() {
        var config = ImmuneSystemConfig.defaults();
        var immuneSystem = new WorkflowImmuneSystem(
            config,
            pred -> {}
        );

        // Build deadlock-prone net
        Map<String, Set<String>> placeToTransitions = new HashMap<>();
        placeToTransitions.put("p_start", Set.of("t1"));
        placeToTransitions.put("p_orphan", Set.of());

        Map<String, Set<String>> transitionToPlaces = new HashMap<>();
        transitionToPlaces.put("t1", Set.of("p_orphan"));

        // Run predictions for multiple cases
        immuneSystem.predict("case-1", "task-A", placeToTransitions, transitionToPlaces, "p_start", "p_end");
        immuneSystem.predict("case-2", "task-B", placeToTransitions, transitionToPlaces, "p_start", "p_end");
        immuneSystem.predict("case-3", "task-C", placeToTransitions, transitionToPlaces, "p_start", "p_end");

        var report = immuneSystem.getReport();

        assertEquals("Report should track 3 cases scanned", 3, report.casesScanned());
        assertTrue("Report should have multiple predictions", report.predictions().size() >= 3);
    }

    /**
     * Test that null listener throws exception.
     */
    public void testNullListenerThrowsNullPointerException() {
        try {
            new WorkflowImmuneSystem(
                ImmuneSystemConfig.defaults(),
                null
            );
            fail("Should have thrown NullPointerException for null listener");
        } catch (NullPointerException e) {
            assertTrue("Error should mention listener", e.getMessage().contains("listener"));
        }
    }

    /**
     * Test that null config throws exception.
     */
    public void testNullConfigThrowsNullPointerException() {
        try {
            new WorkflowImmuneSystem(
                null,
                pred -> {}
            );
            fail("Should have thrown NullPointerException for null config");
        } catch (NullPointerException e) {
            assertTrue("Error should mention config", e.getMessage().contains("config"));
        }
    }

    /**
     * Test implicit deadlock detection (AND-join with unreachable input).
     *
     * Net structure:
     * - p_start → t_split → {p1, p2}
     * - p1 → t_join
     * - p2 is orphaned (never reaches t_join)
     * - This creates IMPLICIT_DEADLOCK at t_join
     */
    public void testImplicitDeadlockDetection() {
        var config = ImmuneSystemConfig.defaults();
        var predictions = new ArrayList<DeadlockPrediction>();
        var immuneSystem = new WorkflowImmuneSystem(
            config,
            predictions::add
        );

        // Build net with implicit deadlock (AND-join missing one branch)
        Map<String, Set<String>> placeToTransitions = new HashMap<>();
        placeToTransitions.put("p_start", Set.of("t_split"));
        placeToTransitions.put("p1", Set.of("t_join"));
        placeToTransitions.put("p2", Set.of());      // Orphaned - never feeds t_join
        placeToTransitions.put("p_end", Set.of());

        Map<String, Set<String>> transitionToPlaces = new HashMap<>();
        transitionToPlaces.put("t_split", Set.of("p1", "p2"));
        transitionToPlaces.put("t_join", Set.of("p_end"));

        // Run prediction
        var emitted = immuneSystem.predict(
            "case-deadlock",
            "task-split",
            placeToTransitions,
            transitionToPlaces,
            "p_start",
            "p_end"
        );

        // Should detect orphaned place p2 and implicit deadlock at t_join
        assertTrue("Expected deadlock predictions", !emitted.isEmpty());
    }
}
