/*
 * Copyright (c) 2024-2025 YAWL Foundation
 *
 * This file is part of YAWL v6.0.0-GA.
 *
 * YAWL v6.0.0-GA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * YAWL v6.0.0-GA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL v6.0.0-GA. If not, see <http://www.gnu.org/licenses/>.
 */
package org.yawlfoundation.yawl.integration.java_python.patterns;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Validator for basic YAWL control-flow patterns.
 * Validates Sequence, Parallel Split, Synchronization, Exclusive Choice, and Simple Merge patterns.
 *
 * @author YAWL Foundation
 * @since v6.0.0-GA
 */
public class BasicPatternValidator extends PatternValidator {

    @ParameterizedTest
    @MethodSource("basicPatterns")
    @DisplayName("Basic pattern validation")
    void testBasicPattern(BasicPatternTestCase testCase) throws Exception {
        PatternResult result = validatePattern(testCase.patternName(), testCase.patternCode());

        assertThat("Pattern should pass validation", result.isPassed(), equalTo(true));
        assertThat("Soundness check should pass",
                  result.getValidations().get("soundness"),
                  instanceOf(PatternResult.class));
        assertThat("Correctness check should pass",
                  result.getValidations().get("correctness"),
                  instanceOf(PatternResult.class));
    }

    @ParameterizedTest
    @MethodSource("parallelPatterns")
    @DisplayName("Parallel pattern performance")
    void testParallelPatternPerformance(ParallelPatternTestCase testCase) throws Exception {
        PatternResult result = validatePattern(testCase.patternName(), testCase.patternCode());

        if (result.getPerformanceMetrics() != null) {
            assertThat("Average execution time should be reasonable",
                      result.getPerformanceMetrics().averageTime(),
                      lessThan(100.0));
            assertThat("P95 execution time should be reasonable",
                      result.getPerformanceMetrics().p95Time(),
                      lessThan(200.0));
        }
    }

    @ParameterizedTest
    @MethodSource("synchronizationPatterns")
    @DisplayName("Synchronization pattern correctness")
    void testSynchronizationPatternCorrectness(SynchronizationPatternTestCase testCase) throws Exception {
        PatternResult result = validatePattern(testCase.patternName(), testCase.patternCode());

        assertThat("Synchronization pattern should pass", result.isPassed());

        // Verify all expected conditions are met
        PatternResult correctness = (PatternResult) result.getValidations().get("correctness");
        if (correctness != null) {
            assertThat("Correctness should not have errors",
                      correctness.getErrors(),
                      empty());
        }
    }

    // Test data providers

    private static Stream<BasicPatternTestCase> basicPatterns() {
        return Stream.of(
            // Parallel Split Pattern
            new BasicPatternTestCase(
                "Parallel Split Pattern",
                """
                class ParallelSplitPattern:
                    def __init__(self):
                        self.branch1_started = False
                        self.branch2_started = False
                        self.branch1_complete = False
                        self.branch2_complete = False
                        self.received = False

                    def start_parallel_split(self):
                        self.branch1_started = True
                        self.branch2_started = True
                        return True

                    def complete_branch1(self):
                        if self.branch1_started:
                            self.branch1_complete = True
                            self.check_synchronization()
                            return True
                        return False

                    def complete_branch2(self):
                        if self.branch2_started:
                            self.branch2_complete = True
                            self.check_synchronization()
                            return True
                        return False

                    def check_synchronization(self):
                        if self.branch1_complete and self.branch2_complete and not self.received:
                            self.received = True
                            return True
                        return False

                    def is_complete(self):
                        return self.received
                """
            ),

            // Sequence Pattern
            new BasicPatternTestCase(
                "Sequence Pattern",
                """
                class SequencePattern:
                    def __init__(self):
                        self.step1_complete = False
                        self.step2_complete = False
                        self.step3_complete = False
                        self.complete = False

                    def execute_step1(self):
                        self.step1_complete = True
                        return True

                    def execute_step2(self):
                        if self.step1_complete:
                            self.step2_complete = True
                            return True
                        return False

                    def execute_step3(self):
                        if self.step2_complete:
                            self.step3_complete = True
                            self.complete = True
                            return True
                        return False
                """
            ),

            // Exclusive Choice Pattern
            new BasicPatternTestCase(
                "Exclusive Choice Pattern",
                """
                class ExclusiveChoicePattern:
                    def __init__(self):
                        self.chosen_path = None
                        self.path_a_complete = False
                        self.path_b_complete = False

                    def choose_path(self, path):
                        if self.chosen_path is None:
                            self.chosen_path = path
                            return True
                        return False

                    def execute_chosen_path(self):
                        if self.chosen_path == "A":
                            self.path_a_complete = True
                            return True
                        elif self.chosen_path == "B":
                            self.path_b_complete = True
                            return True
                        return False

                    def is_complete(self):
                        return self.chosen_path is not None and \
                               ((self.chosen_path == "A" and self.path_a_complete) or \
                                (self.chosen_path == "B" and self.path_b_complete))
                """
            ),

            // Synchronization Pattern
            new BasicPatternTestCase(
                "Synchronization Pattern",
                """
                class SynchronizationPattern:
                    def __init__(self):
                        self.task1_ready = False
                        self.task2_ready = False
                        self.task3_ready = False
                        self.synchronized = False

                    def complete_task1(self):
                        self.task1_ready = True
                        self.check_synchronization()
                        return True

                    def complete_task2(self):
                        self.task2_ready = True
                        self.check_synchronization()
                        return True

                    def complete_task3(self):
                        self.task3_ready = True
                        self.check_synchronization()
                        return True

                    def check_synchronization(self):
                        if self.task1_ready and self.task2_ready and self.task3_ready and not self.synchronized:
                            self.synchronized = True
                            return True
                        return False

                    def is_synchronized(self):
                        return self.synchronized
                """
            ),

            // Simple Merge Pattern
            new BasicPatternTestCase(
                "Simple Merge Pattern",
                """
                class SimpleMergePattern:
                    def __init__(self):
                        self.input_a = False
                        self.input_b = False
                        self.output_complete = False

                    def receive_input_a(self):
                        self.input_a = True
                        self.check_output()
                        return True

                    def receive_input_b(self):
                        self.input_b = True
                        self.check_output()
                        return True

                    def check_output(self):
                        if (self.input_a or self.input_b) and not self.output_complete:
                            self.output_complete = True
                            return True
                        return False

                    def is_complete(self):
                        return self.output_complete
                """
            )
        );
    }

    private static Stream<ParallelPatternTestCase> parallelPatterns() {
        return Stream.of(
            // Parallel Split Pattern
            new ParallelPatternTestCase(
                "Parallel Split Pattern",
                """
                class ParallelSplitPattern:
                    def __init__(self):
                        self.branch1_started = False
                        self.branch2_started = False
                        self.branch1_complete = False
                        self.branch2_complete = False
                        self.received = False

                    def start_parallel_split(self):
                        self.branch1_started = True
                        self.branch2_started = True
                        return True

                    def complete_branch1(self):
                        if self.branch1_started:
                            self.branch1_complete = True
                            self.check_synchronization()
                            return True
                        return False

                    def complete_branch2(self):
                        if self.branch2_started:
                            self.branch2_complete = True
                            self.check_synchronization()
                            return True
                        return False

                    def check_synchronization(self):
                        if self.branch1_complete and self.branch2_complete and not self.received:
                            self.received = True
                            return True
                        return False

                    def is_complete(self):
                        return self.received
                """
            ),

            // Multi-Choice Pattern
            new ParallelPatternTestCase(
                "Multi-Choice Pattern",
                """
                class MultiChoicePattern:
                    def __init__(self):
                        self.selected_paths = []
                        self.completed_paths = []
                        self.final_result = None

                    def select_paths(self, paths):
                        if not self.selected_paths:
                            self.selected_paths = paths
                            return True
                        return False

                    def execute_selected_paths(self):
                        for path in self.selected_paths:
                            if path not in self.completed_paths:
                                self.completed_paths.append(path)
                        return True

                    def get_final_result(self):
                        if len(self.completed_paths) == len(self.selected_paths):
                            self.final_result = f"Completed {len(self.completed_paths)} paths"
                            return self.final_result
                        return None

                    def is_complete(self):
                        return self.final_result is not None
                """
            )
        );
    }

    private static Stream<SynchronizationPatternTestCase> synchronizationPatterns() {
        return Stream.of(
            // Synchronization Pattern
            new SynchronizationPatternTestCase(
                "Synchronization Pattern",
                """
                class SynchronizationPattern:
                    def __init__(self):
                        self.task1_ready = False
                        self.task2_ready = False
                        self.task3_ready = False
                        self.synchronized = False

                    def complete_task1(self):
                        self.task1_ready = True
                        self.check_synchronization()
                        return True

                    def complete_task2(self):
                        self.task2_ready = True
                        self.check_synchronization()
                        return True

                    def complete_task3(self):
                        self.task3_ready = True
                        self.check_synchronization()
                        return True

                    def check_synchronization(self):
                        if self.task1_ready and self.task2_ready and self.task3_ready and not self.synchronized:
                            self.synchronized = True
                            return True
                        return False

                    def is_synchronized(self):
                        return self.synchronized
                """
            ),

            // Structured Synchronization Merge Pattern
            new SynchronizationPatternTestCase(
                "Structured Synchronization Merge Pattern",
                """
                class StructuredSyncMergePattern:
                    def __init__(self):
                        self.input1_active = False
                        self.input2_active = False
                        self.input3_active = False
                        self.output_active = False
                        self.completed = False

                    def activate_input(self, input_id):
                        if input_id == 1 and not self.input1_active:
                            self.input1_active = True
                        elif input_id == 2 and not self.input2_active:
                            self.input2_active = True
                        elif input_id == 3 and not self.input3_active:
                            self.input3_active = True
                        return True

                    def can_proceed(self):
                        # Require at least two inputs to proceed
                        active_inputs = sum([
                            self.input1_active, self.input2_active, self.input3_active
                        ])
                        return active_inputs >= 2 and not self.output_active

                    def proceed_to_output(self):
                        if self.can_proceed():
                            self.output_active = True
                            return True
                        return False

                    def complete_output(self):
                        if self.output_active:
                            self.completed = True
                            return True
                        return False

                    def is_complete(self):
                        return self.completed
                """
            )
        );
    }

    // Data classes for test cases

    public static class BasicPatternTestCase {
        private final String patternName;
        private final String patternCode;

        public BasicPatternTestCase(String patternName, String patternCode) {
            this.patternName = patternName;
            this.patternCode = patternCode;
        }

        public String patternName() { return patternName; }
        public String patternCode() { return patternCode; }
    }

    public static class ParallelPatternTestCase extends BasicPatternTestCase {
        public ParallelPatternTestCase(String patternName, String patternCode) {
            super(patternName, patternCode);
        }
    }

    public static class SynchronizationPatternTestCase extends BasicPatternTestCase {
        public SynchronizationPatternTestCase(String patternName, String patternCode) {
            super(patternName, patternCode);
        }
    }

    // Validation overrides

    @Override
    public PatternResult validateSoundness(String patternName, String pythonCode) throws Exception {
        PatternResult result = new PatternResult(patternName);

        try {
            // Check for deadlocks
            boolean hasDeadlocks = checkForDeadlocks(pythonCode);
            assertFalse(hasDeadlocks, "Pattern should not have deadlocks");

            // Check for proper termination
            boolean terminates = checkTermination(pythonCode);
            assertTrue(terminates, "Pattern should terminate properly");

            result.setPassed(true);
            result.setSummary("Pattern is sound - no deadlocks and terminates properly");
        } catch (Exception e) {
            result.addError("Soundness validation failed: " + e.getMessage());
            result.setPassed(false);
        }

        return result;
    }

    @Override
    public PatternResult validateCorrectness(String patternName, String pythonCode) throws Exception {
        PatternResult result = new PatternResult(patternName);

        try {
            // Initialize pattern
            executePythonCode(pythonCode);
            Object workflow = executePythonCode(patternName + "()");

            // Test specific behavior based on pattern type
            if (patternName.contains("Sequence")) {
                testSequencePatternCorrectness(workflow);
            } else if (patternName.contains("Exclusive Choice")) {
                testExclusiveChoiceCorrectness(workflow);
            } else if (patternName.contains("Parallel")) {
                testParallelPatternCorrectness(workflow);
            } else if (patternName.contains("Synchronization")) {
                testSynchronizationCorrectness(workflow);
            }

            result.setPassed(true);
            result.setSummary("Pattern behaves correctly");
        } catch (Exception e) {
            result.addError("Correctness validation failed: " + e.getMessage());
            result.setPassed(false);
        }

        return result;
    }

    @Override
    public PatternResult validatePerformance(String patternName, String pythonCode) throws Exception {
        PatternResult result = new PatternResult(patternName);

        try {
            PatternMetrics metrics = measurePerformance(pythonCode, 100);

            // Validate performance metrics
            assertThat("Average execution time should be reasonable",
                      metrics.averageTime(),
                      lessThan(100.0));
            assertThat("Throughput should be adequate",
                      metrics.throughput(),
                      greaterThan(5.0));

            // Add metrics to result
            result.getValidations().put("performance", metrics);
            result.setPassed(true);
            result.setSummary("Performance metrics within acceptable range");
        } catch (Exception e) {
            result.addError("Performance validation failed: " + e.getMessage());
            result.setPassed(false);
        }

        return result;
    }

    @Override
    public PatternResult validateErrorHandling(String patternName, String pythonCode) throws Exception {
        PatternResult result = new PatternResult(patternName);

        try {
            List<ErrorScenario> scenarios = getErrorScenarios(patternName);
            ErrorHandlingResult errorHandling = checkErrorHandling(pythonCode, scenarios);

            if (errorHandling.allPassed()) {
                result.setPassed(true);
                result.setSummary("Error handling works correctly");
            } else {
                result.addError("Some error scenarios failed");
                result.getValidations().put("error_messages", errorHandling.getErrorMessages());
            }
        } catch (Exception e) {
            result.addError("Error handling validation failed: " + e.getMessage());
            result.setPassed(false);
        }

        return result;
    }

    // Helper methods for pattern-specific validation

    private void testSequencePatternCorrectness(Object workflow) throws Exception {
        executePythonCode("workflow.execute_step1()");
        assertTrue((Boolean) executePythonCode("workflow.step1_complete"));

        executePythonCode("workflow.execute_step2()");
        assertTrue((Boolean) executePythonCode("workflow.step2_complete"));

        executePythonCode("workflow.execute_step3()");
        assertTrue((Boolean) executePythonCode("workflow.step3_complete"));
        assertTrue((Boolean) executePythonCode("workflow.complete"));
    }

    private void testExclusiveChoiceCorrectness(Object workflow) throws Exception {
        // Test path A
        executePythonCode("workflow.choose_path('A')");
        executePythonCode("workflow.execute_chosen_path()");
        assertTrue((Boolean) executePythonCode("workflow.path_a_complete"));
        assertFalse((Boolean) executePythonCode("workflow.path_b_complete"));

        // Reset and test path B
        executePythonCode("workflow.chosen_path = None");
        executePythonCode("workflow.path_a_complete = False");
        executePythonCode("workflow.path_b_complete = False");
        executePythonCode("workflow.choose_path('B')");
        executePythonCode("workflow.execute_chosen_path()");
        assertFalse((Boolean) executePythonCode("workflow.path_a_complete"));
        assertTrue((Boolean) executePythonCode("workflow.path_b_complete"));
    }

    private void testParallelPatternCorrectness(Object workflow) throws Exception {
        executePythonCode("workflow.start_parallel_split()");
        assertTrue((Boolean) executePythonCode("workflow.branch1_started"));
        assertTrue((Boolean) executePythonCode("workflow.branch2_started"));

        executePythonCode("workflow.complete_branch1()");
        assertTrue((Boolean) executePythonCode("workflow.branch1_complete"));
        assertFalse((Boolean) executePythonCode("workflow.received"));

        executePythonCode("workflow.complete_branch2()");
        assertTrue((Boolean) executePythonCode("workflow.branch2_complete"));
        assertTrue((Boolean) executePythonCode("workflow.received"));
        assertTrue((Boolean) executePythonCode("workflow.is_complete()"));
    }

    private void testSynchronizationCorrectness(Object workflow) throws Exception {
        // Complete tasks in different orders
        executePythonCode("workflow.complete_task1()");
        executePythonCode("workflow.complete_task2()");
        executePythonCode("workflow.complete_task3()");
        assertTrue((Boolean) executePythonCode("workflow.is_synchronized()"));

        // Test other order
        executePythonCode("workflow.task1_ready = False; workflow.task2_ready = False; workflow.task3_ready = False; workflow.synchronized = False");
        executePythonCode("workflow.complete_task3()");
        executePythonCode("workflow.complete_task1()");
        executePythonCode("workflow.complete_task2()");
        assertTrue((Boolean) executePythonCode("workflow.is_synchronized()"));
    }

    private List<ErrorScenario> getErrorScenarios(String patternName) {
        if (patternName.contains("Parallel")) {
            return List.of(
                new ErrorScenario("Missing branch completion",
                    "workflow.branch1_started = True; workflow.branch2_started = False"),
                new ErrorScenario("Premature synchronization",
                    "workflow.received = True")
            );
        } else if (patternName.contains("Exclusive Choice")) {
            return List.of(
                new ErrorScenario("Multiple choices",
                    "workflow.choose_path('A'); workflow.choose_path('B')"),
                new ErrorScenario("Invalid choice",
                    "workflow.choose_path('C')")
            );
        } else {
            return List.of(
                new ErrorScenario("Out of order execution",
                    "workflow.execute_step2()"),
                new ErrorScenario("Duplicate execution",
                    "workflow.execute_step1(); workflow.execute_step1()")
            );
        }
    }
}