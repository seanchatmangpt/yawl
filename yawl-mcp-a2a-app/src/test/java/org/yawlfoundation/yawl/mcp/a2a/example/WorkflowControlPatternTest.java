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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprehensive unit tests for YAWL Workflow Control Patterns (WCP) not yet covered
 * in {@link WorkflowSoundnessVerifierTest}.
 *
 * <p>Tests WCP patterns using real {@link WorkflowSoundnessVerifier} instances
 * on YAML specifications that demonstrate each pattern's structure.
 * Coverage includes:
 * <ul>
 *   <li>WCP-3: Synchronization (AND-join)</li>
 *   <li>WCP-5: Simple Merge (XOR-join)</li>
 *   <li>WCP-6: Multi-Choice (OR-split)</li>
 *   <li>WCP-7: Structured Synchronizing Merge</li>
 *   <li>WCP-8: Multi-Merge</li>
 *   <li>WCP-9: Structured Discriminator</li>
 *   <li>WCP-11: Implicit Termination</li>
 *   <li>WCP-12: Multiple Instances Without Synchronization</li>
 *   <li>WCP-13: Multiple Instances With a Priori Design-Time Knowledge</li>
 *   <li>WCP-14: Multiple Instances With a Priori Run-Time Knowledge</li>
 *   <li>WCP-16: Deferred Choice</li>
 *   <li>WCP-17: Interleaved Parallel Routing</li>
 *   <li>WCP-19: Cancel Task</li>
 *   <li>WCP-20: Cancel Case</li>
 * </ul>
 *
 * <p>Chicago/Detroit TDD — no mocks, stubs, or placeholder implementations.
 * All tests operate on real {@link WorkflowSoundnessVerifier} with real workflow data.
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@DisplayName("WorkflowControlPatternTest — WCP-3,5,6,7,8,9,11,12,13,14,16,17,19,20")
class WorkflowControlPatternTest {

    private WorkflowSoundnessVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new WorkflowSoundnessVerifier();
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    private Map<String, Object> buildTask(String id, String... flows) {
        Map<String, Object> task = new HashMap<>();
        task.put("id", id);
        task.put("flows", List.of(flows));
        return task;
    }

    private Map<String, Object> buildSpec(String name, String first,
                                          List<Map<String, Object>> tasks) {
        Map<String, Object> spec = new HashMap<>();
        spec.put("name", name);
        if (first != null) {
            spec.put("first", first);
        }
        spec.put("tasks", tasks);
        return spec;
    }

    // =========================================================================
    // WCP-3: Synchronization (AND-join)
    // =========================================================================

    @Nested
    @DisplayName("WCP-3 Synchronization (AND-join)")
    class WCP3SynchronizationTests {

        @Test
        @DisplayName("WCP-3 Sound: Split->Join with both branches merging synchronously")
        void wcp3SoundAndJoinTest() {
            String yaml = """
                    name: Synchronization
                    first: SplitTask
                    tasks:
                      - id: SplitTask
                        flows: [Branch1, Branch2]
                        split: and
                      - id: Branch1
                        flows: [JoinTask]
                      - id: Branch2
                        flows: [JoinTask]
                      - id: JoinTask
                        flows: [end]
                        join: and
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "WCP-3 synchronous AND-join must be sound; violations: " + result.violations());
            assertTrue(result.violations().isEmpty(),
                    "Sound WCP-3 must report no violations, got: " + result.violations());
        }

        @Test
        @DisplayName("WCP-3 Sound: Three parallel branches merging with AND-join")
        void wcp3ThreeBranchesAndJoinTest() {
            List<Map<String, Object>> tasks = List.of(
                    buildTask("Split", "B1", "B2", "B3"),
                    buildTask("B1", "Join"),
                    buildTask("B2", "Join"),
                    buildTask("B3", "Join"),
                    buildTask("Join", "end")
            );
            var spec = buildSpec("ThreeBranchJoin", "Split", tasks);
            var result = verifier.verify(spec);

            assertTrue(result.sound(),
                    "Three parallel branches with AND-join must be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("WCP-3 Unsound: AND-join missing one incoming branch")
        void wcp3UnsoundMissingBranchTest() {
            String yaml = """
                    name: IncompleteJoin
                    first: Split
                    tasks:
                      - id: Split
                        flows: [Branch1, Branch2]
                        split: and
                      - id: Branch1
                        flows: [Join]
                      - id: Branch2
                        flows: [end]
                      - id: Join
                        flows: [end]
                        join: and
                    """;
            var result = verifier.verifyYaml(yaml);

            assertFalse(result.sound(),
                    "AND-join with missing branch edge must be unsound");
            assertTrue(result.violations().stream()
                            .anyMatch(v -> v.contains("Join") && v.contains("dead-end")),
                    "Should report Join as dead-end or unreachable; got: " + result.violations());
        }
    }

    // =========================================================================
    // WCP-5: Simple Merge (XOR-join)
    // =========================================================================

    @Nested
    @DisplayName("WCP-5 Simple Merge (XOR-join)")
    class WCP5SimpleMergeTests {

        @Test
        @DisplayName("WCP-5 Sound: Two branches merging with XOR-join")
        void wcp5SimpleXorJoinTest() {
            String yaml = """
                    name: SimpleMerge
                    first: Decision
                    tasks:
                      - id: Decision
                        flows: [Path1, Path2]
                        split: xor
                        condition: flag -> Path1
                        default: Path2
                      - id: Path1
                        flows: [Merge]
                      - id: Path2
                        flows: [Merge]
                      - id: Merge
                        flows: [end]
                        join: xor
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "WCP-5 XOR-join simple merge must be sound; violations: " + result.violations());
            assertTrue(result.violations().isEmpty(),
                    "Sound WCP-5 must report no violations, got: " + result.violations());
        }

        @Test
        @DisplayName("WCP-5 Sound: Three alternative paths merging with XOR-join")
        void wcp5ThreePathsMergeTest() {
            List<Map<String, Object>> tasks = List.of(
                    buildTask("Choice", "PathA", "PathB", "PathC"),
                    buildTask("PathA", "Merge"),
                    buildTask("PathB", "Merge"),
                    buildTask("PathC", "Merge"),
                    buildTask("Merge", "end")
            );
            var spec = buildSpec("ThreePathMerge", "Choice", tasks);
            var result = verifier.verify(spec);

            assertTrue(result.sound(),
                    "Three alternative paths with XOR-join must be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("WCP-5 Unsound: One branch cannot reach merge point")
        void wcp5UnsoundIsolatedBranchTest() {
            String yaml = """
                    name: BrokenMerge
                    first: Decision
                    tasks:
                      - id: Decision
                        flows: [Path1, Path2]
                        split: xor
                      - id: Path1
                        flows: [Merge]
                      - id: Path2
                        flows: [DeadEnd]
                      - id: DeadEnd
                        flows: []
                      - id: Merge
                        flows: [end]
                        join: xor
                    """;
            var result = verifier.verifyYaml(yaml);

            assertFalse(result.sound(),
                    "XOR-join with dead-end branch must be unsound");
            assertTrue(result.violations().size() >= 1,
                    "Should report at least one violation; got: " + result.violations());
        }
    }

    // =========================================================================
    // WCP-6: Multi-Choice (OR-split)
    // =========================================================================

    @Nested
    @DisplayName("WCP-6 Multi-Choice (OR-split)")
    class WCP6MultiChoiceTests {

        @Test
        @DisplayName("WCP-6 Sound: OR-split to multiple branches all reaching end")
        void wcp6OrSplitAllBranchesReachEndTest() {
            String yaml = """
                    name: MultiChoice
                    first: Decision
                    tasks:
                      - id: Decision
                        flows: [Option1, Option2, Option3]
                        split: or
                      - id: Option1
                        flows: [end]
                      - id: Option2
                        flows: [end]
                      - id: Option3
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "WCP-6 OR-split where all options reach end must be sound; violations: " + result.violations());
            assertTrue(result.violations().isEmpty(),
                    "Sound WCP-6 must report no violations, got: " + result.violations());
        }

        @Test
        @DisplayName("WCP-6 Sound: OR-split with two branches and common merge")
        void wcp6OrSplitWithMergeTest() {
            List<Map<String, Object>> tasks = List.of(
                    buildTask("Decision", "Option1", "Option2"),
                    buildTask("Option1", "Common"),
                    buildTask("Option2", "Common"),
                    buildTask("Common", "end")
            );
            var spec = buildSpec("OrWithMerge", "Decision", tasks);
            var result = verifier.verify(spec);

            assertTrue(result.sound(),
                    "OR-split with common merge must be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("WCP-6 Unsound: OR-split with one unreachable branch")
        void wcp6UnsoundUnreachableBranchTest() {
            String yaml = """
                    name: BrokenOr
                    first: Decision
                    tasks:
                      - id: Decision
                        flows: [Option1, Option2]
                        split: or
                      - id: Option1
                        flows: [end]
                      - id: Option2
                        flows: [end]
                      - id: Orphan
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);

            assertFalse(result.sound(),
                    "OR-split with orphan task must be unsound");
            assertTrue(result.violations().stream().anyMatch(v -> v.contains("Orphan")),
                    "Should identify Orphan as unreachable; got: " + result.violations());
        }
    }

    // =========================================================================
    // WCP-7: Structured Synchronizing Merge
    // =========================================================================

    @Nested
    @DisplayName("WCP-7 Structured Synchronizing Merge")
    class WCP7StructuredSynchronizingMergeTests {

        @Test
        @DisplayName("WCP-7 Sound: AND-split->work->XOR-join->AND-join path")
        void wcp7SynchronizingMergeTest() {
            String yaml = """
                    name: StructuredSyncMerge
                    first: AndSplit
                    tasks:
                      - id: AndSplit
                        flows: [Path1, Path2]
                        split: and
                      - id: Path1
                        flows: [Decision1]
                      - id: Path2
                        flows: [Decision2]
                      - id: Decision1
                        flows: [Work1, Work2]
                        split: xor
                      - id: Decision2
                        flows: [Work3, Work4]
                        split: xor
                      - id: Work1
                        flows: [Sync]
                      - id: Work2
                        flows: [Sync]
                      - id: Work3
                        flows: [Sync]
                      - id: Work4
                        flows: [Sync]
                      - id: Sync
                        flows: [end]
                        join: and
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "WCP-7 structured synchronizing merge must be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("WCP-7 Sound: Two parallel branches with conditional paths re-merging")
        void wcp7TwoBranchesWithConditionalsTest() {
            List<Map<String, Object>> tasks = List.of(
                    buildTask("Start", "Branch1", "Branch2"),
                    buildTask("Branch1", "Choice1"),
                    buildTask("Branch2", "Choice2"),
                    buildTask("Choice1", "Task1", "Task2"),
                    buildTask("Choice2", "Task3", "Task4"),
                    buildTask("Task1", "Merge"),
                    buildTask("Task2", "Merge"),
                    buildTask("Task3", "Merge"),
                    buildTask("Task4", "Merge"),
                    buildTask("Merge", "end")
            );
            var spec = buildSpec("TwoBranchConditional", "Start", tasks);
            var result = verifier.verify(spec);

            assertTrue(result.sound(),
                    "Two branches with conditionals and re-merge must be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("WCP-7 Unsound: Synchronizing merge with dead-end path")
        void wcp7UnsoundDeadEndInMergeTest() {
            String yaml = """
                    name: BrokenSync
                    first: Start
                    tasks:
                      - id: Start
                        flows: [Left, Right]
                        split: and
                      - id: Left
                        flows: [Decision]
                      - id: Right
                        flows: [end]
                      - id: Decision
                        flows: [DeadEnd]
                      - id: DeadEnd
                        flows: []
                    """;
            var result = verifier.verifyYaml(yaml);

            assertFalse(result.sound(),
                    "Synchronizing merge with dead-end must be unsound");
        }
    }

    // =========================================================================
    // WCP-8: Multi-Merge
    // =========================================================================

    @Nested
    @DisplayName("WCP-8 Multi-Merge")
    class WCP8MultiMergeTests {

        @Test
        @DisplayName("WCP-8 Sound: Multiple entry paths from different sources merge at single task")
        void wcp8MultiMergeTest() {
            String yaml = """
                    name: MultiMerge
                    first: Entry
                    tasks:
                      - id: Entry
                        flows: [Path1, Path2]
                        split: or
                      - id: Path1
                        flows: [ProcessA]
                      - id: Path2
                        flows: [ProcessB]
                      - id: ProcessA
                        flows: [Merge]
                      - id: ProcessB
                        flows: [Merge]
                      - id: Merge
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "WCP-8 multi-merge with multiple sources must be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("WCP-8 Sound: Three independent chains merging into single task")
        void wcp8ThreeChainsMergeTest() {
            List<Map<String, Object>> tasks = List.of(
                    buildTask("Start", "Chain1", "Chain2", "Chain3"),
                    buildTask("Chain1", "Work1"),
                    buildTask("Chain2", "Work2"),
                    buildTask("Chain3", "Work3"),
                    buildTask("Work1", "Merge"),
                    buildTask("Work2", "Merge"),
                    buildTask("Work3", "Merge"),
                    buildTask("Merge", "end")
            );
            var spec = buildSpec("ThreeChainMerge", "Start", tasks);
            var result = verifier.verify(spec);

            assertTrue(result.sound(),
                    "Three independent chains merging must be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("WCP-8 Unsound: One chain in merge cannot reach end")
        void wcp8UnsoundOneChainDeadTest() {
            String yaml = """
                    name: BrokenMultiMerge
                    first: Start
                    tasks:
                      - id: Start
                        flows: [Chain1, Chain2]
                        split: or
                      - id: Chain1
                        flows: [Task1]
                      - id: Chain2
                        flows: [Task2]
                      - id: Task1
                        flows: [Merge]
                      - id: Task2
                        flows: [DeadPoint]
                      - id: DeadPoint
                        flows: []
                      - id: Merge
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);

            assertFalse(result.sound(),
                    "Multi-merge with dead-end chain must be unsound");
        }
    }

    // =========================================================================
    // WCP-9: Structured Discriminator
    // =========================================================================

    @Nested
    @DisplayName("WCP-9 Structured Discriminator")
    class WCP9StructuredDiscriminatorTests {

        @Test
        @DisplayName("WCP-9 Sound: AND-split converging to XOR-merge (discriminator pattern)")
        void wcp9DiscriminatorTest() {
            String yaml = """
                    name: StructuredDiscriminator
                    first: ParallelStart
                    tasks:
                      - id: ParallelStart
                        flows: [Branch1, Branch2]
                        split: and
                      - id: Branch1
                        flows: [Discriminate]
                      - id: Branch2
                        flows: [Discriminate]
                      - id: Discriminate
                        flows: [end]
                        join: xor
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "WCP-9 structured discriminator must be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("WCP-9 Sound: Three parallel tasks converging to first-one-wins XOR")
        void wcp9ThreeWayDiscriminatorTest() {
            List<Map<String, Object>> tasks = List.of(
                    buildTask("Fork", "Task1", "Task2", "Task3"),
                    buildTask("Task1", "FirstWins"),
                    buildTask("Task2", "FirstWins"),
                    buildTask("Task3", "FirstWins"),
                    buildTask("FirstWins", "end")
            );
            var spec = buildSpec("ThreeWayDiscriminator", "Fork", tasks);
            var result = verifier.verify(spec);

            assertTrue(result.sound(),
                    "Three-way discriminator must be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("WCP-9 Unsound: Discriminator with one branch missing edge to join")
        void wcp9UnsoundMissingEdgeTest() {
            String yaml = """
                    name: BrokenDiscriminator
                    first: Fork
                    tasks:
                      - id: Fork
                        flows: [Task1, Task2]
                        split: and
                      - id: Task1
                        flows: [Join]
                      - id: Task2
                        flows: [end]
                      - id: Join
                        flows: [end]
                        join: xor
                    """;
            var result = verifier.verifyYaml(yaml);

            assertFalse(result.sound(),
                    "Discriminator with missing branch edge must be unsound");
        }
    }

    // =========================================================================
    // WCP-11: Implicit Termination
    // =========================================================================

    @Nested
    @DisplayName("WCP-11 Implicit Termination")
    class WCP11ImplicitTerminationTests {

        @Test
        @DisplayName("WCP-11 Sound: All paths lead to end (no explicit termination condition)")
        void wcp11ImplicitTerminationTest() {
            String yaml = """
                    name: ImplicitTermination
                    first: Processing
                    tasks:
                      - id: Processing
                        flows: [Choice]
                      - id: Choice
                        flows: [PathA, PathB]
                        split: xor
                      - id: PathA
                        flows: [end]
                      - id: PathB
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "WCP-11 implicit termination (all paths reach end) must be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("WCP-11 Sound: Multiple termination points all mapping to end")
        void wcp11MultipleTerminationPointsTest() {
            List<Map<String, Object>> tasks = List.of(
                    buildTask("Start", "Branch1", "Branch2", "Branch3"),
                    buildTask("Branch1", "end"),
                    buildTask("Branch2", "end"),
                    buildTask("Branch3", "end")
            );
            var spec = buildSpec("MultiTerminate", "Start", tasks);
            var result = verifier.verify(spec);

            assertTrue(result.sound(),
                    "Multiple implicit termination points must be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("WCP-11 Sound: Complex workflow where every path eventually reaches end")
        void wcp11ComplexImplicitTerminationTest() {
            List<Map<String, Object>> tasks = List.of(
                    buildTask("A", "B", "C"),
                    buildTask("B", "D"),
                    buildTask("C", "D", "E"),
                    buildTask("D", "end"),
                    buildTask("E", "end")
            );
            var spec = buildSpec("ComplexImplicitTerm", "A", tasks);
            var result = verifier.verify(spec);

            assertTrue(result.sound(),
                    "Complex workflow with implicit termination must be sound; violations: " + result.violations());
        }
    }

    // =========================================================================
    // WCP-12: Multiple Instances Without Synchronization
    // =========================================================================

    @Nested
    @DisplayName("WCP-12 Multiple Instances Without Synchronization")
    class WCP12MultipleInstancesWithoutSyncTests {

        @Test
        @DisplayName("WCP-12 Sound: Multi-instance task without join requirement")
        void wcp12MultiInstanceNoSyncTest() {
            String yaml = """
                    name: MultiInstanceNoSync
                    first: Prepare
                    tasks:
                      - id: Prepare
                        flows: [ProcessItems]
                      - id: ProcessItems
                        flows: [end]
                        multiInstance:
                          min: 1
                          max: 5
                          mode: parallel
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "WCP-12 multi-instance without sync must be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("WCP-12 Sound: Multi-instance flowing directly to end")
        void wcp12DirectMultiInstanceFlowTest() {
            List<Map<String, Object>> tasks = List.of(
                    buildTask("Start", "Process"),
                    buildTask("Process", "end")
            );
            var spec = buildSpec("DirectMultiInstance", "Start", tasks);
            var result = verifier.verify(spec);

            assertTrue(result.sound(),
                    "Direct multi-instance flow to end must be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("WCP-12 Sound: Multiple independent branches with multi-instance")
        void wcp12MultiIndependentInstancesTest() {
            String yaml = """
                    name: MultiIndependent
                    first: Fork
                    tasks:
                      - id: Fork
                        flows: [Process1, Process2]
                        split: and
                      - id: Process1
                        flows: [end]
                        multiInstance:
                          min: 1
                          max: 3
                      - id: Process2
                        flows: [end]
                        multiInstance:
                          min: 1
                          max: 5
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "Multiple independent multi-instances must be sound; violations: " + result.violations());
        }
    }

    // =========================================================================
    // WCP-13: Multiple Instances With a Priori Design-Time Knowledge
    // =========================================================================

    @Nested
    @DisplayName("WCP-13 Multiple Instances With a Priori Design-Time Knowledge")
    class WCP13MultiInstanceDesignTimeTests {

        @Test
        @DisplayName("WCP-13 Sound: Multi-instance with fixed design-time collection count")
        void wcp13FixedCountMultiInstanceTest() {
            String yaml = """
                    name: MultiInstanceFixedCount
                    first: Initialize
                    tasks:
                      - id: Initialize
                        flows: [ProcessTasks]
                      - id: ProcessTasks
                        flows: [end]
                        multiInstance:
                          min: 3
                          max: 3
                          collection: items
                          element: item
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "WCP-13 fixed-count multi-instance must be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("WCP-13 Sound: Fixed number of parallel instances with synchronization")
        void wcp13FixedCountWithSyncTest() {
            List<Map<String, Object>> tasks = List.of(
                    buildTask("Setup", "Task1", "Task2", "Task3"),
                    buildTask("Task1", "Join"),
                    buildTask("Task2", "Join"),
                    buildTask("Task3", "Join"),
                    buildTask("Join", "end")
            );
            var spec = buildSpec("FixedInstancesWithSync", "Setup", tasks);
            var result = verifier.verify(spec);

            assertTrue(result.sound(),
                    "Fixed design-time instances with sync must be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("WCP-13 Sound: Design-time known bound with AND-split and AND-join")
        void wcp13DesignTimeKnownBoundTest() {
            String yaml = """
                    name: DesignTimeKnownBound
                    first: Split
                    tasks:
                      - id: Split
                        flows: [Item1, Item2, Item3, Item4]
                        split: and
                      - id: Item1
                        flows: [Merge]
                      - id: Item2
                        flows: [Merge]
                      - id: Item3
                        flows: [Merge]
                      - id: Item4
                        flows: [Merge]
                      - id: Merge
                        flows: [end]
                        join: and
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "Design-time known bound must be sound; violations: " + result.violations());
        }
    }

    // =========================================================================
    // WCP-14: Multiple Instances With a Priori Run-Time Knowledge
    // =========================================================================

    @Nested
    @DisplayName("WCP-14 Multiple Instances With a Priori Run-Time Knowledge")
    class WCP14MultiInstanceRuntimeTests {

        @Test
        @DisplayName("WCP-14 Sound: Multi-instance with run-time collection determination")
        void wcp14RuntimeCollectionMultiInstanceTest() {
            String yaml = """
                    name: MultiInstanceRuntime
                    first: DetermineCount
                    tasks:
                      - id: DetermineCount
                        flows: [ProcessItems]
                      - id: ProcessItems
                        flows: [end]
                        multiInstance:
                          min: 1
                          max: 100
                          collection: runtimeItems
                          element: item
                          mode: parallel
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "WCP-14 runtime-determined multi-instance must be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("WCP-14 Sound: Query-based collection at runtime with parallelism")
        void wcp14QueryBasedRuntimeCollectionTest() {
            List<Map<String, Object>> tasks = List.of(
                    buildTask("FetchList", "ProcessDynamic"),
                    buildTask("ProcessDynamic", "Aggregate")
            );
            var spec = buildSpec("QueryBasedRuntime", "FetchList", tasks);
            var result = verifier.verify(spec);

            assertTrue(result.sound(),
                    "Query-based runtime collection must be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("WCP-14 Sound: Run-time count with aggregation and consolidation")
        void wcp14RuntimeCountWithAggregationTest() {
            String yaml = """
                    name: RuntimeAggregation
                    first: Analyze
                    tasks:
                      - id: Analyze
                        flows: [Process]
                      - id: Process
                        flows: [Consolidate]
                        multiInstance:
                          mode: parallel
                      - id: Consolidate
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "Runtime count with aggregation must be sound; violations: " + result.violations());
        }
    }

    // =========================================================================
    // WCP-16: Deferred Choice
    // =========================================================================

    @Nested
    @DisplayName("WCP-16 Deferred Choice")
    class WCP16DeferredChoiceTests {

        @Test
        @DisplayName("WCP-16 Sound: Deferred choice based on external event")
        void wcp16DeferredChoiceTest() {
            String yaml = """
                    name: DeferredChoice
                    first: Wait
                    tasks:
                      - id: Wait
                        flows: [Option1, Option2]
                        split: or
                      - id: Option1
                        flows: [end]
                      - id: Option2
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "WCP-16 deferred choice must be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("WCP-16 Sound: Wait-event task with multiple possible continuations")
        void wcp16WaitEventMultiContinuationTest() {
            List<Map<String, Object>> tasks = List.of(
                    buildTask("WaitForSignal", "EventA", "EventB", "EventC"),
                    buildTask("EventA", "end"),
                    buildTask("EventB", "end"),
                    buildTask("EventC", "end")
            );
            var spec = buildSpec("WaitMultiSignal", "WaitForSignal", tasks);
            var result = verifier.verify(spec);

            assertTrue(result.sound(),
                    "Wait-event with multiple paths must be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("WCP-16 Sound: External event determines workflow path")
        void wcp16ExternalEventPathTest() {
            String yaml = """
                    name: ExternalEventPath
                    first: AwaitInput
                    tasks:
                      - id: AwaitInput
                        flows: [Fast, Slow, Cancel]
                        split: or
                      - id: Fast
                        flows: [end]
                      - id: Slow
                        flows: [end]
                      - id: Cancel
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "External event path determination must be sound; violations: " + result.violations());
        }
    }

    // =========================================================================
    // WCP-17: Interleaved Parallel Routing
    // =========================================================================

    @Nested
    @DisplayName("WCP-17 Interleaved Parallel Routing")
    class WCP17InterleavedParallelRoutingTests {

        @Test
        @DisplayName("WCP-17 Sound: Parallel branches with intermediate merges and splits")
        void wcp17InterleavedParallelTest() {
            String yaml = """
                    name: InterleavedParallel
                    first: Start
                    tasks:
                      - id: Start
                        flows: [PathA, PathB]
                        split: and
                      - id: PathA
                        flows: [MergeA]
                      - id: PathB
                        flows: [MergeB]
                      - id: MergeA
                        flows: [ContinueA, ContinueB]
                        split: or
                      - id: MergeB
                        flows: [ContinueA, ContinueB]
                        split: or
                      - id: ContinueA
                        flows: [Final]
                      - id: ContinueB
                        flows: [Final]
                      - id: Final
                        flows: [end]
                        join: and
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "WCP-17 interleaved parallel routing must be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("WCP-17 Sound: Multiple overlapping parallel regions")
        void wcp17OverlappingParallelRegionsTest() {
            List<Map<String, Object>> tasks = List.of(
                    buildTask("Fork1", "Region1", "Region2"),
                    buildTask("Region1", "Region3"),
                    buildTask("Region2", "Region3"),
                    buildTask("Region3", "Join1"),
                    buildTask("Join1", "Fork2"),
                    buildTask("Fork2", "Path1", "Path2"),
                    buildTask("Path1", "End"),
                    buildTask("Path2", "End"),
                    buildTask("End", "end")
            );
            var spec = buildSpec("OverlappingRegions", "Fork1", tasks);
            var result = verifier.verify(spec);

            assertTrue(result.sound(),
                    "Overlapping parallel regions must be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("WCP-17 Unsound: Interleaved routing with incomplete join")
        void wcp17UnsoundIncompleteJoinTest() {
            String yaml = """
                    name: BrokenInterleaved
                    first: Start
                    tasks:
                      - id: Start
                        flows: [PathA, PathB]
                        split: and
                      - id: PathA
                        flows: [Task1]
                      - id: PathB
                        flows: [Task2]
                      - id: Task1
                        flows: [Merge]
                      - id: Task2
                        flows: [DeadEnd]
                      - id: DeadEnd
                        flows: []
                      - id: Merge
                        flows: [end]
                        join: and
                    """;
            var result = verifier.verifyYaml(yaml);

            assertFalse(result.sound(),
                    "Interleaved routing with dead-end must be unsound");
        }
    }

    // =========================================================================
    // WCP-19: Cancel Task
    // =========================================================================

    @Nested
    @DisplayName("WCP-19 Cancel Task")
    class WCP19CancelTaskTests {

        @Test
        @DisplayName("WCP-19 Sound: Task with cancel condition flowing to compensating task")
        void wcp19CancelTaskTest() {
            String yaml = """
                    name: CancelTask
                    first: Process
                    tasks:
                      - id: Process
                        flows: [Cancel, Continue]
                        split: xor
                        condition: cancelled -> Cancel
                        default: Continue
                      - id: Cancel
                        flows: [Compensate]
                      - id: Compensate
                        flows: [end]
                      - id: Continue
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "WCP-19 cancel task with compensation must be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("WCP-19 Sound: Cancellation condition with multiple compensating tasks")
        void wcp19MultiCompensationTest() {
            List<Map<String, Object>> tasks = List.of(
                    buildTask("Work", "CancelPath", "CompletePath"),
                    buildTask("CancelPath", "Undo1"),
                    buildTask("Undo1", "Undo2"),
                    buildTask("Undo2", "end"),
                    buildTask("CompletePath", "end")
            );
            var spec = buildSpec("MultiCompensation", "Work", tasks);
            var result = verifier.verify(spec);

            assertTrue(result.sound(),
                    "Multiple compensating tasks on cancel must be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("WCP-19 Sound: Cancel signal triggers rollback sequence")
        void wcp19RollbackSequenceTest() {
            String yaml = """
                    name: RollbackSequence
                    first: Transaction
                    tasks:
                      - id: Transaction
                        flows: [Commit, Rollback]
                        split: xor
                      - id: Commit
                        flows: [end]
                      - id: Rollback
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "Cancel signal with rollback must be sound; violations: " + result.violations());
        }
    }

    // =========================================================================
    // WCP-20: Cancel Case
    // =========================================================================

    @Nested
    @DisplayName("WCP-20 Cancel Case")
    class WCP20CancelCaseTests {

        @Test
        @DisplayName("WCP-20 Sound: Cancel case branches from any task to cleanup and termination")
        void wcp20CancelCaseTest() {
            String yaml = """
                    name: CancelCase
                    first: Processing
                    tasks:
                      - id: Processing
                        flows: [Cleanup, Continue]
                        split: xor
                        condition: cancelled -> Cleanup
                        default: Continue
                      - id: Cleanup
                        flows: [end]
                      - id: Continue
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "WCP-20 cancel case must be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("WCP-20 Sound: Complex workflow with cancel-case entry from multiple points")
        void wcp20MultiPointCancelTest() {
            List<Map<String, Object>> tasks = List.of(
                    buildTask("Start", "Task1", "Task2"),
                    buildTask("Task1", "Task3", "Cleanup"),
                    buildTask("Task2", "Task4", "Cleanup"),
                    buildTask("Task3", "end"),
                    buildTask("Task4", "end"),
                    buildTask("Cleanup", "end")
            );
            var spec = buildSpec("MultiPointCancel", "Start", tasks);
            var result = verifier.verify(spec);

            assertTrue(result.sound(),
                    "Cancel-case from multiple points must be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("WCP-20 Sound: Case cancellation triggers comprehensive cleanup cascade")
        void wcp20CleanupCascadeTest() {
            String yaml = """
                    name: CleanupCascade
                    first: MainProcess
                    tasks:
                      - id: MainProcess
                        flows: [StepA, AbortAll]
                        split: xor
                      - id: StepA
                        flows: [StepB, AbortAll]
                        split: xor
                      - id: StepB
                        flows: [Complete]
                      - id: AbortAll
                        flows: [Cleanup]
                      - id: Cleanup
                        flows: [end]
                      - id: Complete
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "Case cancellation with cleanup cascade must be sound; violations: " + result.violations());
        }
    }
}
