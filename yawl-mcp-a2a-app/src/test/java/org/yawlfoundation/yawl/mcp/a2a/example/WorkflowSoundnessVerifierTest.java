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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprehensive unit tests for {@link WorkflowSoundnessVerifier}.
 *
 * <p>Tests verify structural soundness analysis based on van der Aalst's WF-net soundness
 * properties applied to YAWL compact YAML workflow specifications:</p>
 *
 * <ul>
 *   <li><strong>Option to complete</strong> (i-top reachability): from the input condition,
 *       every task in the workflow is reachable.</li>
 *   <li><strong>Proper completion</strong> (o-top reachability): from every reachable task,
 *       the output condition (o-top) is reachable.</li>
 *   <li><strong>No dead transitions</strong>: every task participates in at least one valid
 *       firing sequence from i-top to o-top.</li>
 *   <li><strong>Valid flow references</strong>: every flow target is either a declared task
 *       id or the sentinel {@code "end"} (which maps to o-top).</li>
 *   <li><strong>At least one complete path</strong>: there exists at least one path from
 *       i-top all the way to o-top.</li>
 * </ul>
 *
 * <p>Chicago/Detroit TDD — all tests operate on real
 * {@link WorkflowSoundnessVerifier} instances with real workflow data built
 * from {@code Map<String, Object>} and YAML strings. No mocks or stubs are used.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 * @see WorkflowSoundnessVerifier
 * @see <a href="https://doi.org/10.1007/3-540-65935-X_29">van der Aalst (1998):
 *      The Application of Petri Nets to Workflow Management</a>
 */
@DisplayName("WorkflowSoundnessVerifier — van der Aalst Structural Soundness")
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

    /** Sound multi-task workflow with XOR split covering two branches to end. */
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

    // =========================================================================
    // Helper methods for building workflow spec Maps
    // =========================================================================

    /**
     * Builds a minimal workflow spec map with the given name, first-task id,
     * and task list.
     */
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

    /**
     * Builds a task entry with a given id and list of flow targets.
     * Targets may use the special string {@code "end"} to indicate o-top.
     */
    private Map<String, Object> buildTask(String id, String... flows) {
        Map<String, Object> task = new HashMap<>();
        task.put("id", id);
        List<String> flowList = new ArrayList<>(List.of(flows));
        task.put("flows", flowList);
        return task;
    }

    // =========================================================================
    // Nested: Input Validation
    // =========================================================================

    @Nested
    @DisplayName("Input Validation — null, blank, and malformed inputs")
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
        @DisplayName("verifyYaml should throw on empty string YAML")
        void verifyYaml_throwsOnEmpty() {
            assertThrows(IllegalArgumentException.class,
                    () -> verifier.verifyYaml(""),
                    "Should throw for empty YAML");
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

    // =========================================================================
    // Nested: SoundnessResult Record Tests
    // =========================================================================

    @Nested
    @DisplayName("SoundnessResult record — construction, accessors, and immutability")
    class SoundnessResultTests {

        @Test
        @DisplayName("SoundnessResult with no violations is sound")
        void result_noViolations_isSound() {
            var result = new WorkflowSoundnessVerifier.SoundnessResult(true, List.of());
            assertTrue(result.sound(),
                    "SoundnessResult(true, []) must report sound=true");
            assertTrue(result.violations().isEmpty(),
                    "SoundnessResult(true, []) must have no violations");
        }

        @Test
        @DisplayName("SoundnessResult with violations is not sound")
        void result_withViolations_isNotSound() {
            var result = new WorkflowSoundnessVerifier.SoundnessResult(false,
                    List.of("Task 'X' is unreachable from i-top"));
            assertFalse(result.sound(),
                    "SoundnessResult with violations must report sound=false");
            assertEquals(1, result.violations().size(),
                    "Should have exactly one violation");
            assertTrue(result.violations().get(0).contains("unreachable"),
                    "Violation message must contain the reason");
        }

        @Test
        @DisplayName("SoundnessResult violations list is immutable")
        void result_violationsAreImmutable() {
            var result = new WorkflowSoundnessVerifier.SoundnessResult(false,
                    List.of("violation one"));
            assertThrows(UnsupportedOperationException.class,
                    () -> result.violations().add("should fail"),
                    "Violations list must be unmodifiable to preserve record semantics");
        }

        @Test
        @DisplayName("SoundnessResult.sound() accessor does not throw")
        void result_soundAccessorDoesNotThrow() {
            var result = new WorkflowSoundnessVerifier.SoundnessResult(true, List.of());
            assertDoesNotThrow(result::sound,
                    "sound() accessor must never throw");
        }

        @Test
        @DisplayName("SoundnessResult.violations() accessor returns non-null list")
        void result_violationsAccessorReturnsNonNull() {
            var result = new WorkflowSoundnessVerifier.SoundnessResult(true, List.of());
            assertNotNull(result.violations(),
                    "violations() must always return a non-null List");
        }
    }

    // =========================================================================
    // Nested: Sound Workflow Tests
    // =========================================================================

    @Nested
    @DisplayName("SoundWorkflowTests — workflows satisfying all three soundness properties")
    class SoundWorkflowTests {

        @Test
        @DisplayName("WCP-1 Sequence: A->B->C->end is sound")
        void sequenceWorkflowIsSoundTest() {
            String yaml = """
                    name: Sequence
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

            assertNotNull(result,
                    "verifyYaml must return non-null for valid YAML");
            assertTrue(result.sound(),
                    "WCP-1 Sequence A->B->C->end must be sound; violations: " + result.violations());
            assertTrue(result.violations().isEmpty(),
                    "Sound sequence workflow must report no violations, got: " + result.violations());
        }

        @Test
        @DisplayName("WCP-2 Parallel Split: A->{B,C}->D->end is sound")
        void parallelSplitWorkflowIsSoundTest() {
            String yaml = """
                    name: ParallelSplit
                    first: A
                    tasks:
                      - id: A
                        flows: [B, C]
                        split: and
                      - id: B
                        flows: [D]
                      - id: C
                        flows: [D]
                      - id: D
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "WCP-2 Parallel Split A->{B,C}->D->end must be sound; violations: " + result.violations());
            assertTrue(result.violations().isEmpty(),
                    "Sound parallel split must report no violations, got: " + result.violations());
        }

        @Test
        @DisplayName("WCP-4 Exclusive Choice: A->{B,C}->end with both branches reaching end is sound")
        void exclusiveChoiceWorkflowIsSoundTest() {
            String yaml = """
                    name: ExclusiveChoice
                    first: A
                    tasks:
                      - id: A
                        flows: [B, C]
                        split: xor
                        condition: amount > 0 -> B
                        default: C
                      - id: B
                        flows: [end]
                      - id: C
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "WCP-4 Exclusive Choice where all branches reach end must be sound; violations: "
                            + result.violations());
            assertTrue(result.violations().isEmpty(),
                    "Sound XOR choice must report no violations, got: " + result.violations());
        }

        @Test
        @DisplayName("Single task A->end is sound")
        void singleTaskWorkflowIsSoundTest() {
            var result = verifier.verifyYaml(SOUND_SINGLE_TASK_YAML);

            assertNotNull(result,
                    "verifyYaml must return non-null for single-task workflow");
            assertTrue(result.sound(),
                    "Single-task A->end must be sound; violations: " + result.violations());
            assertTrue(result.violations().isEmpty(),
                    "Single-task sound workflow must have no violations, got: " + result.violations());
        }

        @Test
        @DisplayName("Multi-task XOR workflow from constant YAML is sound")
        void multiTaskXorWorkflow_isSound() {
            var result = verifier.verifyYaml(SOUND_MULTI_TASK_YAML);

            assertNotNull(result,
                    "verifyYaml must return non-null for multi-task workflow");
            assertTrue(result.sound(),
                    "Multi-task XOR workflow must be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("Diamond merge A->B->D->end, A->C->D->end is sound")
        void diamondMergeWorkflowIsSoundTest() {
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
                    "Converging diamond must be sound; violations: " + result.violations());
            assertTrue(result.violations().isEmpty(),
                    "Sound diamond must report no violations, got: " + result.violations());
        }

        @Test
        @DisplayName("Long chain A->B->C->D->E->end is sound")
        void longSequenceWorkflowIsSoundTest() {
            List<Map<String, Object>> tasks = List.of(
                    buildTask("A", "B"),
                    buildTask("B", "C"),
                    buildTask("C", "D"),
                    buildTask("D", "E"),
                    buildTask("E", "end")
            );
            var spec = buildSpec("LongChain", "A", tasks);
            var result = verifier.verify(spec);

            assertTrue(result.sound(),
                    "5-task linear chain must be sound; violations: " + result.violations());
            assertTrue(result.violations().isEmpty(),
                    "Sound 5-task chain must report no violations, got: " + result.violations());
        }

        @Test
        @DisplayName("AND-split where all branches reach end is sound")
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
                    "AND-split with all branches reaching end must be sound; violations: "
                            + result.violations());
        }

        @Test
        @DisplayName("First task inferred from task list when 'first' field is absent")
        void inferredFirstTask_isSound() {
            String yaml = """
                    name: InferredStart
                    tasks:
                      - id: OnlyTask
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "Workflow with inferred first task must be sound; violations: "
                            + result.violations());
        }

        @Test
        @DisplayName("verify(Map) with pre-parsed sound spec is sound")
        void verifyMap_soundSpec() {
            var spec = Map.<String, Object>of(
                    "name", "MapSpec",
                    "first", "T1",
                    "tasks", List.of(
                            Map.of("id", "T1", "flows", List.of("end"))
                    )
            );
            var result = verifier.verify(spec);

            assertNotNull(result, "verify(Map) must return non-null for valid spec");
            assertTrue(result.sound(),
                    "Pre-parsed map spec with T1->end must be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("Extended YAML with variables and timers is structurally sound")
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
                    "Extended workflow with variables and timers must be structurally sound; violations: "
                            + result.violations());
        }

        @Test
        @DisplayName("Multi-instance tasks reaching end are structurally sound")
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
                    "Multi-instance task reaching end must be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("Order Fulfillment canonical example (VerifyPayment->CheckInventory->ShipOrder or CancelOrder) is sound")
        void orderFulfillmentWorkflowIsSoundTest() {
            String yaml = """
                    name: OrderFulfillment
                    first: VerifyPayment
                    tasks:
                      - id: VerifyPayment
                        flows: [CheckInventory, CancelOrder]
                        split: xor
                        condition: payment_ok -> CheckInventory
                        default: CancelOrder
                      - id: CheckInventory
                        flows: [ShipOrder, CancelOrder]
                        split: xor
                        condition: in_stock -> ShipOrder
                        default: CancelOrder
                      - id: ShipOrder
                        flows: [end]
                      - id: CancelOrder
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "Order Fulfillment workflow must be sound; violations: " + result.violations());
            assertTrue(result.violations().isEmpty(),
                    "Sound Order Fulfillment must have no violations, got: " + result.violations());
        }
    }

    // =========================================================================
    // Nested: Unsound Workflow Tests
    // =========================================================================

    @Nested
    @DisplayName("UnsoundWorkflowTests — workflows violating at least one soundness property")
    class UnsoundWorkflowTests {

        @Test
        @DisplayName("Disconnected task C not reachable from start makes workflow unsound")
        void disconnectedTaskIsUnsoundTest() {
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

            assertFalse(result.sound(),
                    "Workflow with disconnected task must be unsound");
            assertTrue(result.violations().stream()
                            .anyMatch(v -> v.contains("OrphanTask") && v.contains("unreachable")),
                    "Should report OrphanTask as unreachable; got: " + result.violations());
        }

        @Test
        @DisplayName("Dead-end task B with no outgoing flows cannot reach end — is unsound")
        void deadEndTaskIsUnsoundTest() {
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

            assertFalse(result.sound(),
                    "Workflow where task Sink has no outgoing flows must be unsound");
            assertTrue(result.violations().stream()
                            .anyMatch(v -> v.contains("Sink") && v.contains("dead-end")),
                    "Should report Sink as dead-end; got: " + result.violations());
        }

        @Test
        @DisplayName("Invalid flow reference to non-existent task makes workflow unsound")
        void invalidFlowReferenceIsUnsoundTest() {
            String yaml = """
                    name: BadRef
                    first: TaskA
                    tasks:
                      - id: TaskA
                        flows: [NonExistent]
                    """;
            var result = verifier.verifyYaml(yaml);

            assertFalse(result.sound(),
                    "Workflow with a flow to a non-existent task must be unsound");
            assertTrue(result.violations().stream()
                            .anyMatch(v -> v.contains("NonExistent")),
                    "Should name the unknown target; got: " + result.violations());
        }

        @Test
        @DisplayName("Empty task list is unsound")
        void emptyTaskListIsUnsoundTest() {
            String yaml = """
                    name: EmptyWorkflow
                    tasks: []
                    """;
            var result = verifier.verifyYaml(yaml);

            assertFalse(result.sound(),
                    "A workflow with no tasks must be unsound");
            assertTrue(result.violations().stream()
                            .anyMatch(v -> v.contains("no tasks")),
                    "Should report 'no tasks' violation; got: " + result.violations());
        }

        @Test
        @DisplayName("Missing tasks key in spec map is unsound")
        void missingTasksKey_isViolation() {
            var spec = Map.<String, Object>of("name", "NoTasks");
            var result = verifier.verify(spec);

            assertFalse(result.sound(),
                    "Spec missing 'tasks' key must not be sound");
            assertFalse(result.violations().isEmpty(),
                    "Missing tasks key must produce at least one violation");
        }

        @Test
        @DisplayName("'first' pointing to undeclared task id is unsound")
        void firstTaskNotDeclared_isViolation() {
            String yaml = """
                    name: BadFirst
                    first: Ghost
                    tasks:
                      - id: TaskA
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);

            assertFalse(result.sound(),
                    "Undeclared first task must make workflow unsound");
            assertTrue(result.violations().stream()
                            .anyMatch(v -> v.contains("Ghost")),
                    "Should name the missing first task; got: " + result.violations());
        }

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

            assertFalse(result.sound(),
                    "Duplicate task IDs must make workflow unsound");
            assertTrue(result.violations().stream()
                            .anyMatch(v -> v.toLowerCase().contains("duplicate")),
                    "Should report duplicate id violation; got: " + result.violations());
        }

        @Test
        @DisplayName("Branch where one path leads to dead-end makes entire workflow unsound")
        void oneDeadBranchMakesWorkflowUnsoundTest() {
            // A -> {B, C}, B -> end, C -> DeadPath (no path to end from C)
            List<Map<String, Object>> tasks = List.of(
                    buildTask("A", "B", "DeadPath"),
                    buildTask("B", "end"),
                    buildTask("DeadPath")    // no outgoing flows
            );
            var spec = buildSpec("OneBranchDead", "A", tasks);
            var result = verifier.verify(spec);

            assertFalse(result.sound(),
                    "Workflow where one branch cannot reach end must be unsound");
            assertFalse(result.violations().isEmpty(),
                    "Dead branch must produce at least one violation, got empty list");
        }

        @Test
        @DisplayName("Self-loop task A->A with no exit to end is unsound")
        void selfLoopWithNoExitIsUnsoundTest() {
            List<Map<String, Object>> tasks = List.of(
                    buildTask("A", "A")   // loops forever, no path to end
            );
            var spec = buildSpec("SelfLoop", "A", tasks);
            var result = verifier.verify(spec);

            assertFalse(result.sound(),
                    "Self-looping task with no exit must be unsound");
            assertFalse(result.violations().isEmpty(),
                    "Self-loop-only workflow must report violations, got empty list");
        }

        @Test
        @DisplayName("Spec with unreachable and dead-end tasks reports multiple violations")
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

            assertFalse(result.sound(),
                    "Spec with multiple issues must be unsound");
            assertTrue(result.violations().size() >= 2,
                    "Should report at least 2 violations (dead-end + orphan); got: " + result.violations());
        }

        @Test
        @DisplayName("All violations are collected — verifier does not fail fast on first error")
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
            // AlsoOrphan is unreachable; DeadEnd cannot reach o-top
            var result = verifier.verifyYaml(yaml);

            assertFalse(result.sound(),
                    "Spec with multiple structural issues must be unsound");
            assertTrue(result.violations().size() >= 2,
                    "Should collect all violations without stopping at first; got: " + result.violations());
        }

        @Test
        @DisplayName("Each violation message is non-null and non-blank")
        void violationMessagesAreDescriptiveTest() {
            List<Map<String, Object>> tasks = List.of(
                    buildTask("A", "B"),
                    buildTask("B", "end"),
                    buildTask("Orphan", "end")
            );
            var spec = buildSpec("DescriptiveViolation", "A", tasks);
            var result = verifier.verify(spec);

            assertFalse(result.sound(),
                    "Workflow with orphan task must be unsound");
            assertFalse(result.violations().isEmpty(),
                    "Unsound workflow must have at least one violation");
            for (String violation : result.violations()) {
                assertNotNull(violation, "Every violation message must be non-null");
                assertFalse(violation.isBlank(),
                        "Every violation message must not be blank, got: '" + violation + "'");
            }
        }
    }

    // =========================================================================
    // Nested: Cycle Tests
    // =========================================================================

    @Nested
    @DisplayName("CycleTests — loops must have at least one exit path to end")
    class CycleTests {

        @Test
        @DisplayName("WCP-10 Structured loop: CheckCondition->LoopBody->CheckCondition + CheckCondition->end is sound")
        void cycleWithExitIsSoundTest() {
            String yaml = """
                    name: LoopWithExit
                    first: CheckCondition
                    tasks:
                      - id: CheckCondition
                        flows: [LoopBody, end]
                        split: xor
                        condition: continueLoop -> LoopBody
                        default: end
                      - id: LoopBody
                        flows: [CheckCondition]
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "Structured loop with exit path to end must be sound; violations: " + result.violations());
            assertTrue(result.violations().isEmpty(),
                    "Sound loop must report no violations, got: " + result.violations());
        }

        @Test
        @DisplayName("Infinite loop A->B->A with NO exit to end is unsound")
        void cycleWithoutExitIsUnsoundTest() {
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

            assertFalse(result.sound(),
                    "Infinite loop with no exit must be unsound");
            assertTrue(result.violations().stream()
                            .anyMatch(v -> v.contains("dead-end")),
                    "Should report dead-end violation for cycle without exit; got: " + result.violations());
        }

        @Test
        @DisplayName("Multi-step loop CheckCondition->Body1->Body2->CheckCondition with exit is sound")
        void multiStepLoopWithExitIsSoundTest() {
            List<Map<String, Object>> tasks = List.of(
                    buildTask("CheckCondition", "Body1", "end"),
                    buildTask("Body1", "Body2"),
                    buildTask("Body2", "CheckCondition")
            );
            var spec = buildSpec("MultiStepLoop", "CheckCondition", tasks);
            var result = verifier.verify(spec);

            assertTrue(result.sound(),
                    "Multi-step loop with exit must be sound; violations: " + result.violations());
            assertTrue(result.violations().isEmpty(),
                    "Sound multi-step loop must report no violations, got: " + result.violations());
        }

        @Test
        @DisplayName("WCP-28 Structured loop pattern (StartTask->Initialize->CheckCondition<->LoopBody with exit) is sound")
        void wcp28StructuredLoopPatternIsSoundTest() {
            // Matches /patterns/controlflow/wcp-28-structured-loop.yaml structure
            List<Map<String, Object>> tasks = List.of(
                    buildTask("StartTask", "Initialize"),
                    buildTask("Initialize", "CheckCondition"),
                    buildTask("CheckCondition", "LoopBody", "ExitLoop"),
                    buildTask("LoopBody", "IncrementCounter"),
                    buildTask("IncrementCounter", "CheckCondition"),
                    buildTask("ExitLoop", "Finalize"),
                    buildTask("Finalize", "end")
            );
            var spec = buildSpec("StructuredLoopPattern", "StartTask", tasks);
            var result = verifier.verify(spec);

            assertTrue(result.sound(),
                    "WCP-28 Structured Loop with exit must be sound; violations: " + result.violations());
            assertTrue(result.violations().isEmpty(),
                    "Sound WCP-28 loop must report no violations, got: " + result.violations());
        }

        @Test
        @DisplayName("3-member cycle A->B->C->A with no exit is unsound")
        void threeMemberCycleWithNoExitIsUnsoundTest() {
            List<Map<String, Object>> tasks = List.of(
                    buildTask("A", "B"),
                    buildTask("B", "C"),
                    buildTask("C", "A")
            );
            var spec = buildSpec("ThreeCycle", "A", tasks);
            var result = verifier.verify(spec);

            assertFalse(result.sound(),
                    "3-member cycle A->B->C->A with no exit must be unsound");
            assertFalse(result.violations().isEmpty(),
                    "3-member cycle without exit must report violations, got empty list");
        }
    }

    // =========================================================================
    // Nested: Van der Aalst Property Tests
    // =========================================================================

    @Nested
    @DisplayName("VanDerAalstPropertyTests — individual verification of each soundness property")
    class VanDerAalstPropertyTests {

        @Test
        @DisplayName("Proper completion: every task can reach o-top (output condition)")
        void properCompletionAllTasksReachOutputConditionTest() {
            // Router -> {Path1, Path2, Path3}, all paths terminate at o-top
            List<Map<String, Object>> tasks = List.of(
                    buildTask("Router", "Path1", "Path2", "Path3"),
                    buildTask("Path1", "end"),
                    buildTask("Path2", "end"),
                    buildTask("Path3", "end")
            );
            var spec = buildSpec("ProperCompletion", "Router", tasks);
            var result = verifier.verify(spec);

            assertTrue(result.sound(),
                    "3-branch XOR where every branch reaches o-top satisfies proper-completion; violations: "
                            + result.violations());
            assertTrue(result.violations().isEmpty(),
                    "Proper-completion workflow must produce no violations, got: " + result.violations());
        }

        @Test
        @DisplayName("Option to complete: every state reachable from i-top can reach o-top")
        void optionToCompleteEveryStateCanReachOutputTest() {
            // Linear sequence — from every intermediate state there is exactly one forward path to o-top
            List<Map<String, Object>> tasks = List.of(
                    buildTask("Intake", "Review"),
                    buildTask("Review", "Approve"),
                    buildTask("Approve", "end")
            );
            var spec = buildSpec("OptionToComplete", "Intake", tasks);
            var result = verifier.verify(spec);

            assertTrue(result.sound(),
                    "Linear sequence satisfies option-to-complete; violations: " + result.violations());
            assertTrue(result.violations().isEmpty(),
                    "Option-to-complete workflow must have no violations, got: " + result.violations());
        }

        @Test
        @DisplayName("No dead transitions: every task participates in at least one valid path")
        void noDeadTransitionsEveryTaskParticipatesTest() {
            // A -> {B, C}, both B and C reach end — no task is dead
            List<Map<String, Object>> tasks = List.of(
                    buildTask("A", "B", "C"),
                    buildTask("B", "end"),
                    buildTask("C", "end")
            );
            var spec = buildSpec("NoDeadTransitions", "A", tasks);
            var result = verifier.verify(spec);

            assertTrue(result.sound(),
                    "Workflow where every task participates in a valid path must be sound; violations: "
                            + result.violations());
            assertTrue(result.violations().isEmpty(),
                    "No-dead-transitions workflow must have no violations, got: " + result.violations());
        }

        @Test
        @DisplayName("Dead task violation: task reachable from start but with no path to end is unsound")
        void deadTaskViolationReachableButNotConnectedToEndTest() {
            // A -> {B, DeadBranch}; B -> end; DeadBranch -> AnotherDead (no outgoing flows)
            List<Map<String, Object>> tasks = List.of(
                    buildTask("A", "B", "DeadBranch"),
                    buildTask("B", "end"),
                    buildTask("DeadBranch", "AnotherDead"),
                    buildTask("AnotherDead")    // no outgoing flows
            );
            var spec = buildSpec("DeadTransition", "A", tasks);
            var result = verifier.verify(spec);

            assertFalse(result.sound(),
                    "Workflow with a dead-end branch violates no-dead-transitions; must be unsound");
            assertTrue(result.violations().stream().anyMatch(v -> !v.isBlank()),
                    "Dead-transition violation must produce at least one non-blank violation message");
        }

        @Test
        @DisplayName("Reachability from i-top: task not reachable from 'first' violates soundness")
        void taskNotReachableFromInputConditionViolatesSoundnessTest() {
            String yaml = """
                    name: Reachability
                    first: Start
                    tasks:
                      - id: Start
                        flows: [end]
                      - id: Orphan
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);

            assertFalse(result.sound(),
                    "Task unreachable from i-top violates reachability property; workflow must be unsound");
            assertTrue(result.violations().stream()
                            .anyMatch(v -> v.contains("Orphan")),
                    "Should identify Orphan as unreachable; got: " + result.violations());
        }

        @Test
        @DisplayName("At least one complete path: 'end' sentinel is always a valid flow target")
        void endSentinel_isAlwaysValidAndYieldsCompletePath() {
            String yaml = """
                    name: EndSentinel
                    first: TaskA
                    tasks:
                      - id: TaskA
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);

            assertTrue(result.sound(),
                    "'end' sentinel must be recognized as o-top; violations: " + result.violations());
            assertTrue(result.violations().isEmpty(),
                    "Single task using 'end' sentinel must have no violations, got: " + result.violations());
        }
    }

    // =========================================================================
    // Nested: YAML Parsing Tests
    // =========================================================================

    @Nested
    @DisplayName("YamlParsingTests — verifyYaml input handling and YAML format support")
    class YamlParsingTests {

        @Test
        @DisplayName("verifyYaml with valid YAML string returns non-null SoundnessResult")
        void verifyYamlWithValidYamlStringTest() {
            String yaml = """
                    name: ValidYaml
                    first: TaskA
                    tasks:
                      - id: TaskA
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);

            assertNotNull(result,
                    "verifyYaml must return non-null SoundnessResult for valid YAML");
            assertTrue(result.sound(),
                    "Single-task valid YAML must be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("verifyYaml strips markdown YAML code block wrapper and processes correctly")
        void verifyYamlStripsMarkdownYamlCodeBlockTest() {
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

            assertNotNull(result,
                    "verifyYaml must handle ```yaml code blocks and return non-null SoundnessResult");
            assertTrue(result.sound(),
                    "Single-task workflow in yaml code block must be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("verifyYaml strips generic triple-backtick code block wrapper")
        void verifyYamlStripsGenericCodeBlockTest() {
            String fenced = """
                    ```
                    name: GenericBlock
                    first: TaskA
                    tasks:
                      - id: TaskA
                        flows: [end]
                    ```
                    """;
            var result = verifier.verifyYaml(fenced);

            assertNotNull(result,
                    "verifyYaml must handle generic ``` code blocks and return non-null SoundnessResult");
            assertTrue(result.sound(),
                    "Single-task workflow in generic code block must be sound; violations: " + result.violations());
        }

        @Test
        @DisplayName("verifyYaml with null input throws IllegalArgumentException")
        void verifyYamlWithNullInputTest() {
            assertThrows(IllegalArgumentException.class,
                    () -> verifier.verifyYaml(null),
                    "verifyYaml(null) must throw IllegalArgumentException");
        }

        @Test
        @DisplayName("verifyYaml with empty string throws IllegalArgumentException")
        void verifyYamlWithEmptyStringTest() {
            assertThrows(IllegalArgumentException.class,
                    () -> verifier.verifyYaml(""),
                    "verifyYaml(\"\") must throw IllegalArgumentException");
        }

        @Test
        @DisplayName("verifyYaml correctly analyzes multi-task YAML with flow chains")
        void verifyYamlMultiTaskFlowChainTest() {
            String yaml = """
                    name: MultiTask
                    first: Initialize
                    tasks:
                      - id: Initialize
                        flows: [Validate]
                      - id: Validate
                        flows: [Execute]
                      - id: Execute
                        flows: [Finalize]
                      - id: Finalize
                        flows: [end]
                    """;
            var result = verifier.verifyYaml(yaml);

            assertNotNull(result,
                    "verifyYaml must return non-null for multi-task YAML");
            assertTrue(result.sound(),
                    "4-task sequence must be sound; violations: " + result.violations());
            assertTrue(result.violations().isEmpty(),
                    "Sound 4-task sequence must have no violations, got: " + result.violations());
        }

        @Test
        @DisplayName("verifyYaml result is consistent with verify(Map) for the same workflow")
        void verifyYamlAndVerifyMapProduceConsistentResultsTest() {
            String yaml = """
                    name: ConsistencyCheck
                    first: A
                    tasks:
                      - id: A
                        flows: [B]
                      - id: B
                        flows: [end]
                    """;
            List<Map<String, Object>> tasks = List.of(
                    buildTask("A", "B"),
                    buildTask("B", "end")
            );
            var spec = buildSpec("ConsistencyCheck", "A", tasks);

            var yamlResult = verifier.verifyYaml(yaml);
            var mapResult = verifier.verify(spec);

            assertNotNull(yamlResult, "YAML result must not be null");
            assertNotNull(mapResult, "Map result must not be null");
            assertEquals(yamlResult.sound(), mapResult.sound(),
                    "verifyYaml and verify(Map) must agree on soundness for the same workflow");
        }

        @Test
        @DisplayName("verifyYaml handles YAML with task-level extra properties (join, split, description)")
        void verifyYamlHandlesExtraTaskPropertiesTest() {
            String yaml = """
                    name: ExtraProps
                    first: A
                    tasks:
                      - id: A
                        flows: [end]
                        join: xor
                        split: and
                        description: Extra properties task
                    """;
            var result = verifier.verifyYaml(yaml);

            assertNotNull(result,
                    "verifyYaml must handle extra task properties without throwing");
            assertTrue(result.sound(),
                    "Task with extra props flowing to end must be sound; violations: " + result.violations());
        }
    }

    // =========================================================================
    // Nested: SoundnessResult Contract Tests
    // =========================================================================

    @Nested
    @DisplayName("SoundnessResult Contract Tests — record semantics across multiple calls")
    class SoundnessResultContractTests {

        @Test
        @DisplayName("SoundnessResult is non-null for any non-exceptional verify(Map) call")
        void soundnessResultIsNeverNullForValidSpecTest() {
            List<Map<String, Object>> tasks = List.of(buildTask("T", "end"));
            var spec = buildSpec("NullCheck", "T", tasks);

            var result = verifier.verify(spec);

            assertNotNull(result,
                    "verify(Map) must always return a non-null SoundnessResult");
        }

        @Test
        @DisplayName("SoundnessResult.violations() returns empty list (not null) when sound")
        void soundResultHasEmptyNotNullViolationsListTest() {
            List<Map<String, Object>> tasks = List.of(buildTask("A", "end"));
            var spec = buildSpec("EmptyViolations", "A", tasks);

            var result = verifier.verify(spec);

            assertTrue(result.sound(),
                    "Single-task workflow must be sound");
            assertNotNull(result.violations(),
                    "violations() must return an empty list, not null, when sound");
            assertTrue(result.violations().isEmpty(),
                    "violations() must be empty for a sound workflow, got: " + result.violations());
        }

        @Test
        @DisplayName("SoundnessResult.sound() returns false when violations list is non-empty")
        void soundIsFalseWhenViolationsNonEmptyTest() {
            List<Map<String, Object>> tasks = List.of(buildTask("A", "B")); // B doesn't exist
            var spec = buildSpec("ViolationCheck", "A", tasks);

            var result = verifier.verify(spec);

            if (!result.violations().isEmpty()) {
                assertFalse(result.sound(),
                        "If violations are present, sound() must return false");
            }
        }

        @Test
        @DisplayName("Multiple verify calls on same verifier instance return independent results")
        void multipleVerifyCallsReturnIndependentResultsTest() {
            var sound = verifier.verify(
                    buildSpec("Sound", "A", List.of(buildTask("A", "end")))
            );
            var unsound = verifier.verify(
                    buildSpec("Unsound", "A", List.of(buildTask("A", "B")))   // B missing
            );

            assertNotNull(sound, "First result must not be null");
            assertNotNull(unsound, "Second result must not be null");
            assertNotSame(sound, unsound,
                    "Two verify calls must return distinct SoundnessResult instances");
            assertTrue(sound.sound(),
                    "First call on sound spec must return sound=true");
            assertFalse(unsound.sound(),
                    "Second call on unsound spec must return sound=false");
        }
    }

    // =========================================================================
    // Nested: Boundary and Edge Case Tests
    // =========================================================================

    @Nested
    @DisplayName("Boundary and Edge Case Tests")
    class BoundaryAndEdgeCaseTests {

        @Test
        @DisplayName("Task with 'end' as only flow directly maps to o-top and is sound")
        void taskWithEndFlowDirectlyToOutputConditionIsSoundTest() {
            List<Map<String, Object>> tasks = List.of(
                    buildTask("OnlyTask", "end")
            );
            var spec = buildSpec("DirectEnd", "OnlyTask", tasks);
            var result = verifier.verify(spec);

            assertTrue(result.sound(),
                    "Task flowing directly to 'end' (o-top) must be sound; violations: "
                            + result.violations());
        }

        @Test
        @DisplayName("Workflow name containing special characters does not affect soundness analysis")
        void workflowNameWithSpecialCharactersTest() {
            List<Map<String, Object>> tasks = List.of(buildTask("A", "end"));
            var spec = buildSpec("Workflow<>&\"'Name", "A", tasks);
            var result = verifier.verify(spec);

            assertNotNull(result,
                    "Workflow with special chars in name must still produce a non-null result");
            assertTrue(result.sound(),
                    "Special chars in name must not affect soundness; violations: " + result.violations());
        }

        @Test
        @DisplayName("Large workflow with 10 sequential tasks is sound and analyzed without error")
        void largeSequentialWorkflowWithTenTasksIsSoundTest() {
            List<Map<String, Object>> tasks = new ArrayList<>();
            for (int i = 1; i <= 10; i++) {
                String nextId = (i < 10) ? "T" + (i + 1) : "end";
                tasks.add(buildTask("T" + i, nextId));
            }
            var spec = buildSpec("LargeSequence", "T1", tasks);
            var result = verifier.verify(spec);

            assertNotNull(result,
                    "10-task sequential workflow must produce a non-null result");
            assertTrue(result.sound(),
                    "10-task linear sequence must be sound; violations: " + result.violations());
            assertTrue(result.violations().isEmpty(),
                    "10-task linear sequence must report no violations, got: " + result.violations());
        }

        @Test
        @DisplayName("verifyYaml task referencing isolated subtask on an unreachable branch is unsound")
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

            assertFalse(result.sound(),
                    "Isolated task not reachable from Start must make workflow unsound");
            assertTrue(result.violations().stream()
                            .anyMatch(v -> v.contains("Isolated")),
                    "Should flag Isolated as unreachable; got: " + result.violations());
        }

        @Test
        @DisplayName("Spec map with no 'name' key is handled gracefully")
        void specWithNoNameKeyIsHandledGracefullyTest() {
            List<Map<String, Object>> tasks = List.of(buildTask("A", "end"));
            var spec = new HashMap<String, Object>();
            spec.put("first", "A");
            spec.put("tasks", tasks);
            // deliberately omit "name" key

            var result = verifier.verify(spec);

            assertNotNull(result,
                    "Spec without 'name' key must still produce a non-null SoundnessResult");
        }
    }
}
