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

package org.yawlfoundation.yawl.integration.processmining;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for {@link ProcessSoundnessChecker}.
 *
 * Tests real soundness detection behavior using real process graph structures.
 * No mocks — constructs actual DFG graphs and verifies violation detection.
 */
class ProcessSoundnessCheckerTest {

    private ProcessSoundnessChecker checker;

    @BeforeEach
    void setUp() {
        checker = new ProcessSoundnessChecker();
    }

    // -------------------------------------------------------------------------
    // Input validation
    // -------------------------------------------------------------------------

    @Test
    void check_rejectsNullActivities() {
        assertThrows(IllegalArgumentException.class,
            () -> checker.check(null, Set.of("A"), Set.of("B"), Map.of()));
    }

    @Test
    void check_rejectsNullModel() {
        assertThrows(IllegalArgumentException.class,
            () -> checker.check((XesToYawlSpecGenerator.DiscoveredModel) null));
    }

    // -------------------------------------------------------------------------
    // Sound process: linear chain A → B → C
    // -------------------------------------------------------------------------

    @Test
    void soundLinearChain_returnsSound() {
        Set<String> activities = orderedSet("A", "B", "C");
        Set<String> start = Set.of("A");
        Set<String> end = Set.of("C");
        Map<String, Set<String>> dfg = Map.of(
            "A", Set.of("B"),
            "B", Set.of("C"),
            "C", Set.of()
        );
        ProcessSoundnessChecker.SoundnessResult result = checker.check(activities, start, end, dfg);
        assertTrue(result.isSound(), "Linear chain A→B→C must be sound");
        assertTrue(result.violations().isEmpty(), "No violations expected for linear chain");
    }

    // -------------------------------------------------------------------------
    // Missing start / end
    // -------------------------------------------------------------------------

    @Test
    void emptyStartActivities_reportsNoStartViolation() {
        Set<String> activities = Set.of("A", "B");
        ProcessSoundnessChecker.SoundnessResult result =
            checker.check(activities, Collections.emptySet(), Set.of("B"), Map.of());
        assertFalse(result.isSound(), "Empty start activities must be unsound");
        assertTrue(result.violations().stream()
            .anyMatch(v -> v.type() == ProcessSoundnessChecker.SoundnessViolation.NO_START_ACTIVITY),
            "Must report NO_START_ACTIVITY violation");
    }

    @Test
    void nullStartActivities_reportsNoStartViolation() {
        Set<String> activities = Set.of("A", "B");
        ProcessSoundnessChecker.SoundnessResult result =
            checker.check(activities, null, Set.of("B"), Map.of());
        assertFalse(result.isSound());
        assertTrue(result.violations().stream()
            .anyMatch(v -> v.type() == ProcessSoundnessChecker.SoundnessViolation.NO_START_ACTIVITY));
    }

    @Test
    void emptyEndActivities_reportsNoEndViolation() {
        Set<String> activities = Set.of("A", "B");
        ProcessSoundnessChecker.SoundnessResult result =
            checker.check(activities, Set.of("A"), Collections.emptySet(), Map.of());
        assertFalse(result.isSound(), "Empty end activities must be unsound");
        assertTrue(result.violations().stream()
            .anyMatch(v -> v.type() == ProcessSoundnessChecker.SoundnessViolation.NO_END_ACTIVITY),
            "Must report NO_END_ACTIVITY violation");
    }

    // -------------------------------------------------------------------------
    // Unreachable tasks
    // -------------------------------------------------------------------------

    @Test
    void unreachableTask_reportsViolation() {
        // A → B; C is unreachable from A
        Set<String> activities = orderedSet("A", "B", "C");
        Set<String> start = Set.of("A");
        Set<String> end = Set.of("B");
        Map<String, Set<String>> dfg = buildDfg("A", "B");  // C has no incoming edge from A

        ProcessSoundnessChecker.SoundnessResult result = checker.check(activities, start, end, dfg);
        assertFalse(result.isSound(), "Unreachable C must make process unsound");
        assertTrue(result.violations().stream()
            .anyMatch(v -> v.type() == ProcessSoundnessChecker.SoundnessViolation.UNREACHABLE_TASK
                       && v.task().equals("C")),
            "Must report UNREACHABLE_TASK for C");
    }

    @Test
    void allReachable_noUnreachableViolation() {
        Set<String> activities = orderedSet("Start", "Middle", "End");
        Map<String, Set<String>> dfg = Map.of(
            "Start", Set.of("Middle"),
            "Middle", Set.of("End"),
            "End", Set.of()
        );
        ProcessSoundnessChecker.SoundnessResult result =
            checker.check(activities, Set.of("Start"), Set.of("End"), dfg);
        assertTrue(result.violations().stream()
            .noneMatch(v -> v.type() == ProcessSoundnessChecker.SoundnessViolation.UNREACHABLE_TASK),
            "No unreachable tasks in linear chain");
    }

    // -------------------------------------------------------------------------
    // Dead-end tasks (cannot reach end)
    // -------------------------------------------------------------------------

    @Test
    void deadEndTask_reportsViolation() {
        // A → B → End, but also A → C (C has no path to End)
        Set<String> activities = orderedSet("A", "B", "C", "End");
        Set<String> start = Set.of("A");
        Set<String> end = Set.of("End");
        Map<String, Set<String>> dfg = Map.of(
            "A", Set.of("B", "C"),
            "B", Set.of("End"),
            "C", Set.of(),    // dead end - no outgoing edges
            "End", Set.of()
        );
        ProcessSoundnessChecker.SoundnessResult result = checker.check(activities, start, end, dfg);
        assertFalse(result.isSound());
        assertTrue(result.violations().stream()
            .anyMatch(v -> v.type() == ProcessSoundnessChecker.SoundnessViolation.DEAD_END_TASK
                       && v.task().equals("C")),
            "C (dead-end) must trigger DEAD_END_TASK violation");
    }

    // -------------------------------------------------------------------------
    // Isolated tasks
    // -------------------------------------------------------------------------

    @Test
    void isolatedTask_reportsViolation() {
        // D exists in activities but has no edges at all and is not start/end
        Set<String> activities = orderedSet("A", "B", "D");
        Set<String> start = Set.of("A");
        Set<String> end = Set.of("B");
        Map<String, Set<String>> dfg = Map.of(
            "A", Set.of("B"),
            "B", Set.of(),
            "D", Set.of()   // isolated
        );
        ProcessSoundnessChecker.SoundnessResult result = checker.check(activities, start, end, dfg);
        assertFalse(result.isSound());
        assertTrue(result.violations().stream()
            .anyMatch(v -> v.type() == ProcessSoundnessChecker.SoundnessViolation.ISOLATED_TASK
                       && v.task().equals("D")),
            "Must report ISOLATED_TASK for D");
    }

    @Test
    void startActivityIsNotIsolated_evenWithoutIncoming() {
        // Start has no incoming but is the start activity — should not be flagged isolated
        Set<String> activities = orderedSet("Start", "End");
        Map<String, Set<String>> dfg = Map.of(
            "Start", Set.of("End"),
            "End", Set.of()
        );
        ProcessSoundnessChecker.SoundnessResult result =
            checker.check(activities, Set.of("Start"), Set.of("End"), dfg);
        assertTrue(result.isSound(), "Simple 2-node chain must be sound");
        assertTrue(result.violations().stream()
            .noneMatch(v -> v.type() == ProcessSoundnessChecker.SoundnessViolation.ISOLATED_TASK),
            "Start activity must not be reported as isolated");
    }

    // -------------------------------------------------------------------------
    // Cycle detection (livelock)
    // -------------------------------------------------------------------------

    @Test
    void cycleWithExitPath_doesNotReportLivelock() {
        // A → B → A (cycle), but B → End also exists
        Set<String> activities = orderedSet("A", "B", "End");
        Set<String> start = Set.of("A");
        Set<String> end = Set.of("End");
        Map<String, Set<String>> dfg = Map.of(
            "A", Set.of("B"),
            "B", Set.of("A", "End"),  // can exit to End
            "End", Set.of()
        );
        ProcessSoundnessChecker.SoundnessResult result = checker.check(activities, start, end, dfg);
        assertTrue(result.violations().stream()
            .noneMatch(v -> v.type() == ProcessSoundnessChecker.SoundnessViolation.CYCLE_WITHOUT_EXIT),
            "Cycle with exit path to End must not be flagged as livelock");
    }

    @Test
    void cycleWithoutExit_reportsLivelockViolation() {
        // A → B → A (cycle), no path to End
        Set<String> activities = orderedSet("A", "B", "End");
        Set<String> start = Set.of("A");
        Set<String> end = Set.of("End");
        Map<String, Set<String>> dfg = Map.of(
            "A", Set.of("B"),
            "B", Set.of("A"),   // cycles back only, no exit
            "End", Set.of()
        );
        ProcessSoundnessChecker.SoundnessResult result = checker.check(activities, start, end, dfg);
        assertFalse(result.isSound(), "Cycle without exit must be unsound");
        assertTrue(result.violations().stream()
            .anyMatch(v -> v.type() == ProcessSoundnessChecker.SoundnessViolation.CYCLE_WITHOUT_EXIT),
            "Must report CYCLE_WITHOUT_EXIT for non-exiting cycle");
    }

    // -------------------------------------------------------------------------
    // Empty activities set
    // -------------------------------------------------------------------------

    @Test
    void emptyActivities_withEmptyStart_reportsNoStartAndNoEnd() {
        ProcessSoundnessChecker.SoundnessResult result =
            checker.check(Collections.emptySet(), Collections.emptySet(),
                          Collections.emptySet(), Map.of());
        assertFalse(result.isSound());
        assertTrue(result.violations().stream()
            .anyMatch(v -> v.type() == ProcessSoundnessChecker.SoundnessViolation.NO_START_ACTIVITY));
        assertTrue(result.violations().stream()
            .anyMatch(v -> v.type() == ProcessSoundnessChecker.SoundnessViolation.NO_END_ACTIVITY));
    }

    // -------------------------------------------------------------------------
    // Integration with DiscoveredModel
    // -------------------------------------------------------------------------

    @Test
    void checkDiscoveredModel_soundLinearModel_returnsSound() {
        XesToYawlSpecGenerator generator = new XesToYawlSpecGenerator(1);
        List<List<String>> traces = List.of(List.of("Apply", "Review", "Approve"));
        XesToYawlSpecGenerator.DiscoveredModel model = generator.buildModel(traces);

        ProcessSoundnessChecker.SoundnessResult result = checker.check(model);
        assertTrue(result.isSound(), "Linear discovered model must be sound");
    }

    @Test
    void checkDiscoveredModel_parallelPaths_returnsSound() {
        XesToYawlSpecGenerator generator = new XesToYawlSpecGenerator(1);
        List<List<String>> traces = List.of(
            List.of("Start", "PathA", "End"),
            List.of("Start", "PathB", "End")
        );
        XesToYawlSpecGenerator.DiscoveredModel model = generator.buildModel(traces);

        ProcessSoundnessChecker.SoundnessResult result = checker.check(model);
        assertTrue(result.isSound(), "Diamond pattern (parallel paths to same end) must be sound");
    }

    // -------------------------------------------------------------------------
    // Violation messages
    // -------------------------------------------------------------------------

    @Test
    void violationMessages_areNonEmpty() {
        Set<String> activities = orderedSet("A", "B");
        ProcessSoundnessChecker.SoundnessResult result =
            checker.check(activities, Collections.emptySet(), Set.of("B"), Map.of());
        for (ProcessSoundnessChecker.Violation v : result.violations()) {
            assertFalse(v.message().isBlank(), "Violation message must not be blank");
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Set<String> orderedSet(String... items) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String item : items) set.add(item);
        return set;
    }

    /** Builds a DFG with a single directed edge from → to, all other nodes empty. */
    private Map<String, Set<String>> buildDfg(String from, String to) {
        Map<String, Set<String>> dfg = new LinkedHashMap<>();
        dfg.put(from, new LinkedHashSet<>(Set.of(to)));
        dfg.put(to, new LinkedHashSet<>());
        return dfg;
    }
}
