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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for WorkflowSoundnessVerifier.
 *
 * <p>Covers structural soundness verification per van der Aalst's WF-net criteria:
 * reachability from i-top, option-to-complete to o-top, valid flow references,
 * non-empty task list, and at least one complete path.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
class WorkflowSoundnessVerifierTest {

    private WorkflowSoundnessVerifier verifier;

    /** Minimal sound workflow: single task leading directly to end. */
    private static final String SOUND_SINGLE_TASK_YAML = """
            name: SimpleWorkflow
            first: TaskA
            tasks:
              - id: TaskA
                flows: [end]
            """;

    /** Sound multi-task workflow with XOR split/merge. */
    private static final String SOUND_MULTI_TASK_YAML = """
            name: OrderFulfillment
            first: VerifyPayment
            tasks:
              - id: VerifyPayment
                flows: [CheckInventory, CancelOrder]
              - id: CheckInventory
                flows: [ShipOrder]
              - id: ShipOrder
                flows: [end]
              - id: CancelOrder
                flows: [end]
            """;

    @BeforeEach
    void setUp() {
        verifier = new WorkflowSoundnessVerifier();
    }

    // -------------------------------------------------------------------------
    // Guard: null / blank input
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Input Validation")
    class InputValidationTests {

        @Test
        @DisplayName("verifyYaml should throw on null YAML")
        void verifyYaml_throwsOnNull() {
            assertThrows(IllegalArgumentException.class,
                    () -> verifier.verifyYaml(null),
                    "Should throw for null YAML");
        }

        @Test
        @DisplayName("verifyYaml should throw on blank YAML")
        void verifyYaml_throwsOnBlank() {
            assertThrows(IllegalArgumentException.class,
                    () -> verifier.verifyYaml("   "),
                    "Should throw for blank YAML");
        }

        @Test
        @DisplayName("verifyYaml should throw on malformed YAML")
        void verifyYaml_throwsOnMalformedYaml() {
            assertThrows(IllegalArgumentException.class,
                    () -> verifier.verifyYaml(": this: is: not: valid: yaml: {{{{"),
                    "Should throw for unparseable YAML");
        }

        @Test
        @DisplayName("verify should throw on null spec map")
        void verify_throwsOnNullMap() {
            assertThrows(IllegalArgumentException.class,
                    () -> verifier.verify(null),
                    "Should throw for null map");
        }
    }

    // -------------------------------------------------------------------------
    // SoundnessResult record
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("SoundnessResult record")
    class SoundnessResultTests {

        @Test
        @DisplayName("SoundnessResult with no violations is sound")
        void result_noViolations_isSound() {
            var result = new WorkflowSoundnessVerifier.SoundnessResult(true, List.of());
            assertTrue(result.sound(), "Empty violation list means sound");
            assertTrue(result.violations().isEmpty(), "No violations");
        }

        @Test
        @DisplayName("SoundnessResult with violations is not sound")
        void result_withViolations_isNotSound() {
            var result = new WorkflowSoundnessVerifier.SoundnessResult(false,
                    List.of("Task 'X' is unreachable"));
            assertFalse(result.sound(), "Has violations means not sound");
            assertEquals(1, result.violations().size());
        }

        @Test
        @DisplayName("SoundnessResult violations list is immutable")
        void result_violationsAreImmutable() {
            var result = new WorkflowSoundnessVerifier.SoundnessResult(false,
                    List.of("violation one"));
            assertThrows(UnsupportedOperationException.class,
                    () -> result.violations().add("should fail"),
                    "Violations list must be unmodifiable");
        }
    }

    // -------------------------------------------------------------------------
    // Sound workflows
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Sound Workflows")
    class SoundWorkflowTests {

        @Test
        @DisplayName("Single task flowing to end is sound")
        void singleTask_isSound() {
            var result = verifier.verifyYaml(SOUND_SINGLE_TASK_YAML);
            assertNotNull(result);
            assertTrue(result.sound(), "Single-task workflow should be sound");
            assertTrue(result.violations().isEmpty(),
                    "No violations expected; got: " + result.violations());
        }

        @Test
        @DisplayName("Multi-task XOR workflow is sound")
        void multiTaskXor_isSound() {
            var result = verifier.verifyYaml(SOUND_MULTI_TASK_YAML);
            assertNotNull(result);
            assertTrue(result.sound(),
                    "Multi-task workflow should be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("Linear chain of tasks is sound")
        void linearChain_isSound() {
            String yaml = """
                    name: LinearChain
                    first: A
                    tasks:
                      - id: A
                        flows: [B]
                      - id: B
                        flows: [C]
                      - id: C
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(),
                    "Linear chain should be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("AND-split then join is sound when all branches reach end")
        void andSplitAllBranchesReachEnd_isSound() {
            String yaml = """
                    name: AndSplit
                    first: Split
                    tasks:
                      - id: Split
                        flows: [BranchA, BranchB]
                        split: and
                      - id: BranchA
                        flows: [end]
                      - id: BranchB
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(),
                    "AND-split with all branches reaching end should be sound; violations: "
                            + result.violations());
        }

        @Test
        @DisplayName("First task inferred from task list when 'first' field absent")
        void inferredFirstTask_isSound() {
            String yaml = """
                    name: InferredStart
                    tasks:
                      - id: OnlyTask
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(),
                    "Inferred first task should produce sound result; violations: "
                            + result.violations());
        }

        @Test
        @DisplayName("Markdown code-fence YAML is parsed and verified correctly")
        void markdownFencedYaml_isSound() {
            String fenced = """
                    ```yaml
                    name: FencedWorkflow
                    first: TaskX
                    tasks:
                      - id: TaskX
                        flows: [end]
                    ```
                    """;
            var result = verifier.verifyYaml(fenced);
            assertTrue(result.sound(),
                    "Markdown-fenced YAML should be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("verify(Map) with pre-parsed spec is sound")
        void verifyMap_soundSpec() {
            var spec = Map.<String, Object>of(
                    "name", "MapSpec",
                    "first", "T1",
                    "tasks", List.of(
                            Map.of("id", "T1", "flows", List.of("end"))
                    )
            );
            var result = verifier.verify(spec);
            assertTrue(result.sound(),
                    "Pre-parsed map spec should be sound; violations: " + result.violations());
        }
    }

    // -------------------------------------------------------------------------
    // Violation: empty task list
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Empty Task List Violations")
    class EmptyTaskListTests {

        @Test
        @DisplayName("Empty tasks list is a violation")
        void emptyTasksList_isViolation() {
            String yaml = """
                    name: EmptyWorkflow
                    tasks: []
                    """;
            var result = verifier.verifyYaml(yaml);
            assertFalse(result.sound(), "Empty task list should not be sound");
            assertTrue(result.violations().stream()
                            .anyMatch(v -> v.contains("no tasks")),
                    "Should report 'no tasks' violation; got: " + result.violations());
        }

        @Test
        @DisplayName("Missing tasks key is a violation")
        void missingTasksKey_isViolation() {
            var spec = Map.<String, Object>of("name", "NoTasks");
            var result = verifier.verify(spec);
            assertFalse(result.sound(), "Missing tasks key should not be sound");
            assertFalse(result.violations().isEmpty(),
                    "Should have at least one violation");
        }
    }

    // -------------------------------------------------------------------------
    // Violation: unreachable tasks
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Unreachable Task Violations")
    class UnreachableTaskTests {

        @Test
        @DisplayName("Task not referenced in any flow is unreachable")
        void orphanedTask_isUnreachable() {
            String yaml = """
                    name: OrphanWorkflow
                    first: TaskA
                    tasks:
                      - id: TaskA
                        flows: [end]
                      - id: OrphanTask
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);
            assertFalse(result.sound(), "Orphaned task should make workflow unsound");
            assertTrue(result.violations().stream()
                            .anyMatch(v -> v.contains("OrphanTask") && v.contains("unreachable")),
                    "Should report OrphanTask as unreachable; got: " + result.violations());
        }

        @Test
        @DisplayName("Task on an unreachable branch (not referenced from first) is a violation")
        void taskOnUnreachableBranch_isViolation() {
            String yaml = """
                    name: UnreachableBranch
                    first: Start
                    tasks:
                      - id: Start
                        flows: [End]
                      - id: End
                        flows: [end]
                      - id: Isolated
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);
            assertFalse(result.sound());
            assertTrue(result.violations().stream()
                            .anyMatch(v -> v.contains("Isolated")),
                    "Should flag Isolated as unreachable; got: " + result.violations());
        }
    }

    // -------------------------------------------------------------------------
    // Violation: dead-end tasks (cannot reach o-top)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Dead-End Task Violations")
    class DeadEndTaskTests {

        @Test
        @DisplayName("Task with no flows is a dead-end")
        void taskWithNoFlows_isDeadEnd() {
            String yaml = """
                    name: DeadEndWorkflow
                    first: TaskA
                    tasks:
                      - id: TaskA
                        flows: [Sink]
                      - id: Sink
                        flows: []
                    """;
            var result = verifier.verifyYaml(yaml);
            assertFalse(result.sound(), "Dead-end task should make workflow unsound");
            assertTrue(result.violations().stream()
                            .anyMatch(v -> v.contains("Sink") && v.contains("dead-end")),
                    "Should report Sink as dead-end; got: " + result.violations());
        }

        @Test
        @DisplayName("Task flowing only to other tasks that never reach end is a dead-end")
        void cyclicSinkWithoutEnd_isDeadEnd() {
            String yaml = """
                    name: CyclicSink
                    first: Entry
                    tasks:
                      - id: Entry
                        flows: [LoopA]
                      - id: LoopA
                        flows: [LoopB]
                      - id: LoopB
                        flows: [LoopA]
                    """;
            var result = verifier.verifyYaml(yaml);
            assertFalse(result.sound(), "Cyclic sink without path to end should be unsound");
            // At least the tasks in the cycle cannot reach o-top
            assertTrue(result.violations().stream()
                            .anyMatch(v -> v.contains("dead-end")),
                    "Should report dead-end violation; got: " + result.violations());
        }
    }

    // -------------------------------------------------------------------------
    // Violation: unknown flow target
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Invalid Flow Reference Violations")
    class InvalidFlowReferenceTests {

        @Test
        @DisplayName("Flow referencing undeclared task ID is a violation")
        void flowToUndeclaredTask_isViolation() {
            String yaml = """
                    name: BadRef
                    first: TaskA
                    tasks:
                      - id: TaskA
                        flows: [NonExistent]
                    """;
            var result = verifier.verifyYaml(yaml);
            assertFalse(result.sound(), "Reference to undeclared task should be unsound");
            assertTrue(result.violations().stream()
                            .anyMatch(v -> v.contains("NonExistent")),
                    "Should name the unknown target; got: " + result.violations());
        }

        @Test
        @DisplayName("'end' sentinel is always a valid flow target")
        void endSentinel_isAlwaysValid() {
            String yaml = """
                    name: EndSentinel
                    first: TaskA
                    tasks:
                      - id: TaskA
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(),
                    "'end' sentinel must be recognized; violations: " + result.violations());
        }
    }

    // -------------------------------------------------------------------------
    // Violation: unknown 'first' field
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("First Task Resolution Violations")
    class FirstTaskResolutionTests {

        @Test
        @DisplayName("'first' pointing to undeclared task is a violation")
        void firstTaskNotDeclared_isViolation() {
            String yaml = """
                    name: BadFirst
                    first: Ghost
                    tasks:
                      - id: TaskA
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);
            assertFalse(result.sound(), "Undeclared first task should be unsound");
            assertTrue(result.violations().stream()
                            .anyMatch(v -> v.contains("Ghost")),
                    "Should name the missing first task; got: " + result.violations());
        }
    }

    // -------------------------------------------------------------------------
    // Multiple violations in a single spec
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Multiple Violations")
    class MultipleViolationsTests {

        @Test
        @DisplayName("Spec with unreachable and dead-end tasks has multiple violations")
        void multipleViolations_reported() {
            String yaml = """
                    name: MultiViolation
                    first: Entry
                    tasks:
                      - id: Entry
                        flows: [DeadEnd]
                      - id: DeadEnd
                        flows: []
                      - id: Orphan
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);
            assertFalse(result.sound());
            assertTrue(result.violations().size() >= 2,
                    "Expected at least 2 violations; got: " + result.violations());
        }

        @Test
        @DisplayName("All violations are reported even after first failure")
        void allViolationsReported_notFailFast() {
            String yaml = """
                    name: ThreeIssues
                    first: Entry
                    tasks:
                      - id: Entry
                        flows: [Unreferenced, DeadEnd]
                      - id: DeadEnd
                        flows: []
                      - id: Unreferenced
                        flows: [end]
                      - id: AlsoOrphan
                        flows: [end]
                    """;
            // AlsoOrphan is unreachable, DeadEnd cannot complete
            var result = verifier.verifyYaml(yaml);
            assertFalse(result.sound());
            assertTrue(result.violations().size() >= 2,
                    "Should collect all violations; got: " + result.violations());
        }
    }

    // -------------------------------------------------------------------------
    // Duplicate task IDs
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Duplicate ID Violations")
    class DuplicateIdTests {

        @Test
        @DisplayName("Duplicate task IDs are reported as violations")
        void duplicateTaskId_isViolation() {
            String yaml = """
                    name: Duplicates
                    first: TaskA
                    tasks:
                      - id: TaskA
                        flows: [end]
                      - id: TaskA
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);
            assertFalse(result.sound(), "Duplicate IDs should make workflow unsound");
            assertTrue(result.violations().stream()
                            .anyMatch(v -> v.toLowerCase().contains("duplicate")),
                    "Should report duplicate id violation; got: " + result.violations());
        }
    }

    // -------------------------------------------------------------------------
    // Complex / real-world-like patterns
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Complex Workflow Patterns")
    class ComplexWorkflowTests {

        @Test
        @DisplayName("Converging diamond (XOR split + XOR join) is sound")
        void diamondPattern_isSound() {
            String yaml = """
                    name: Diamond
                    first: Split
                    tasks:
                      - id: Split
                        flows: [Left, Right]
                        split: xor
                      - id: Left
                        flows: [Join]
                      - id: Right
                        flows: [Join]
                      - id: Join
                        flows: [end]
                        join: xor
                    """;
            var result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(),
                    "Diamond pattern should be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("Extended YAML with variables and timers is sound structurally")
        void extendedYamlWithExtras_isSound() {
            String yaml = """
                    name: ExtendedWorkflow
                    uri: extended.xml
                    first: StartProcess
                    variables:
                      - name: customerId
                        type: xs:string
                    tasks:
                      - id: StartProcess
                        flows: [CheckOrder]
                        split: xor
                        join: and
                      - id: CheckOrder
                        flows: [ProcessOrder, CancelOrder]
                        condition: orderValue > 0 -> ProcessOrder
                        default: CancelOrder
                        split: xor
                        join: xor
                        timer:
                          trigger: OnEnabled
                          duration: PT10M
                      - id: ProcessOrder
                        flows: [end]
                        split: xor
                        join: and
                      - id: CancelOrder
                        flows: [end]
                        split: xor
                        join: xor
                    """;
            var result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(),
                    "Extended workflow should be structurally sound; violations: "
                            + result.violations());
        }

        @Test
        @DisplayName("Multi-instance tasks reaching end are sound")
        void multiInstanceTask_isSound() {
            String yaml = """
                    name: MultiInstance
                    first: Prepare
                    tasks:
                      - id: Prepare
                        flows: [ShipItems]
                      - id: ShipItems
                        flows: [end]
                        multiInstance:
                          min: 1
                          max: 10
                          mode: dynamic
                          threshold: 10
                    """;
            var result = verifier.verifyYaml(yaml);
            assertTrue(result.sound(),
                    "Multi-instance task reaching end should be sound; violations: "
                            + result.violations());
        }
    }
}
