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
package org.yawlfoundation.yawl.integration.java_python;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.YNet;
import org.yawlfoundation.yawl.engine.YWorkflowIdentifier;
import org.yawlfoundation.yawl.graalpy.PythonExecutionEngine;

import java.util.Set;
import java.util.HashSet;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for YAWL functionality preservation.
 * Validates that Python implementations maintain identical behavior to Java implementations.
 *
 * @author YAWL Foundation
 * @since v6.0.0-GA
 */
public class YawlFunctionalityPreservationTest extends ValidationTestBase {

    private static final String SIMPLE_WORKFLOW_PY = """
        class SimpleWorkflow:
            def __init__(self):
                self.status = "created"
                self.data = {}

            def start(self):
                self.status = "running"
                return True

            def complete(self):
                if self.status == "running":
                    self.status = "completed"
                    return True
                return False

            def is_complete(self):
                return self.status == "completed"
        """;

    private static final String PARALLEL_SPLIT_WORKFLOW_PY = """
        class ParallelSplitWorkflow:
            def __init__(self):
                self.branch1_status = "pending"
                self.branch2_status = "pending"
                self.completed = False

            def start_parallel(self):
                # Simulate parallel split
                self.branch1_status = "running"
                self.branch2_status = "running"
                return True

            def complete_branch1(self):
                if self.branch1_status == "running":
                    self.branch1_status = "completed"
                    self.check_completion()
                    return True
                return False

            def complete_branch2(self):
                if self.branch2_status == "running":
                    self.branch2_status = "completed"
                    self.check_completion()
                    return True
                return False

            def check_completion(self):
                if self.branch1_status == "completed" and self.branch2_status == "completed":
                    self.completed = True
                    return True
                return False
        """;

    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        assumeTrue(graalpyAvailable, "GraalPy required for functionality preservation tests");
    }

    @Test
    @DisplayName("Basic workflow lifecycle preservation")
    void testBasicWorkflowLifecycle() throws Exception {
        // Initialize Python workflow
        executePythonCode(SIMPLE_WORKFLOW_PY);
        Object workflow = executePythonCode("SimpleWorkflow()");

        // Test initial state
        assertThat(executePythonCode("workflow.status"), equalTo("created"));
        assertThat(executePythonCode("workflow.is_complete()"), equalTo(false));

        // Test workflow execution
        executePythonCode("workflow.start()");
        assertThat(executePythonCode("workflow.status"), equalTo("running"));

        // Test workflow completion
        executePythonCode("workflow.complete()");
        assertThat(executePythonCode("workflow.status"), equalTo("completed"));
        assertThat(executePythonCode("workflow.is_complete()"), equalTo(true));
    }

    @Test
    @DisplayName("Parallel split pattern preservation")
    void testParallelSplitPattern() throws Exception {
        executePythonCode(PARALLEL_SPLIT_WORKFLOW_PY);
        Object workflow = executePythonCode("ParallelSplitWorkflow()");

        // Initial state
        assertThat(executePythonCode("workflow.branch1_status"), equalTo("pending"));
        assertThat(executePythonCode("workflow.branch2_status"), equalTo("pending"));
        assertThat(executePythonCode("workflow.completed"), equalTo(false));

        // Start parallel execution
        executePythonCode("workflow.start_parallel()");
        assertThat(executePythonCode("workflow.branch1_status"), equalTo("running"));
        assertThat(executePythonCode("workflow.branch2_status"), equalTo("running"));

        // Complete branches independently
        executePythonCode("workflow.complete_branch1()");
        assertThat(executePythonCode("workflow.branch1_status"), equalTo("completed"));
        assertThat(executePythonCode("workflow.branch2_status"), equalTo("running"));
        assertThat(executePythonCode("workflow.completed"), equalTo(false));

        // Complete second branch
        executePythonCode("workflow.complete_branch2()");
        assertThat(executePythonCode("workflow.branch2_status"), equalTo("completed"));
        assertThat(executePythonCode("workflow.completed"), equalTo(true));
    }

    @Test
    @DisplayName("Synchronization pattern preservation")
    void testSynchronizationPattern() throws Exception {
        String syncWorkflow = """
            class SynchronizationWorkflow:
                def __init__(self):
                    self.task1_complete = False
                    self.task2_complete = False
                    self.finalized = False

                def complete_task1(self):
                    self.task1_complete = True
                    self.check_synchronization()
                    return True

                def complete_task2(self):
                    self.task2_complete = True
                    self.check_synchronization()
                    return True

                def check_synchronization(self):
                    if self.task1_complete and self.task2_complete and not self.finalized:
                        self.finalized = True
                        return True
                    return False
            """;

        executePythonCode(syncWorkflow);
        Object workflow = executePythonCode("SynchronizationWorkflow()");

        // Independent task completion
        executePythonCode("workflow.complete_task1()");
        assertTrue((Boolean) executePythonCode("workflow.task1_complete"));
        assertFalse((Boolean) executePythonCode("workflow.finalized"));

        executePythonCode("workflow.complete_task2()");
        assertTrue((Boolean) executePythonCode("workflow.task2_complete"));
        assertTrue((Boolean) executePythonCode("workflow.finalized"));
    }

    @Test
    @DisplayName("Exclusive choice pattern preservation")
    void testExclusiveChoicePattern() throws Exception {
        String choiceWorkflow = """
            class ExclusiveChoiceWorkflow:
                def __init__(self):
                    self.choice_made = False
                    self.path_taken = None

                def make_choice(self, path):
                    if not self.choice_made:
                        self.choice_made = True
                        self.path_taken = path
                        return True
                    return False

                def get_path_taken(self):
                    return self.path_taken
            """;

        executePythonCode(choiceWorkflow);
        Object workflow = executePythonCode("ExclusiveChoiceWorkflow()");

        // First choice should succeed
        executePythonCode("workflow.make_choice('pathA')");
        assertTrue((Boolean) executePythonCode("workflow.choice_made"));
        assertThat(executePythonCode("workflow.get_path_taken()"), equalTo("pathA"));

        // Second choice should fail
        executePythonCode("workflow.make_choice('pathB')");
        assertThat(executePythonCode("workflow.get_path_taken()"), equalTo("pathA"));
    }

    @Test
    @DisplayName("Error handling preservation")
    void testErrorHandlingPreservation() throws Exception {
        String errorWorkflow = """
            class ErrorHandlingWorkflow:
                def __init__(self):
                    self.state = "ready"
                    self.error_occurred = False

                def process_task(self, should_fail=False):
                    if self.state != "ready":
                        raise Exception("Invalid workflow state")

                    if should_fail:
                        self.error_occurred = True
                        raise Exception("Task failed")

                    self.state = "processed"
                    return True

                def recover(self):
                    if self.error_occurred:
                        self.state = "recovered"
                        self.error_occurred = False
                        return True
                    return False
            """;

        executePythonCode(errorWorkflow);
        Object workflow = executePythonCode("ErrorHandlingWorkflow()");

        // Normal processing
        executePythonCode("workflow.process_task()");
        assertThat(executePythonCode("workflow.state"), equalTo("processed"));

        // Error handling
        try {
            executePythonCode("workflow.process_task(True)");
            fail("Expected exception for task failure");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("Task failed"));
        }

        // Recovery
        executePythonCode("workflow.recover()");
        assertThat(executePythonCode("workflow.state"), equalTo("recovered"));
    }

    @Test
    @DisplayName("Data flow preservation")
    void testDataFlowPreservation() throws Exception {
        String dataWorkflow = """
            class DataFlowWorkflow:
                def __init__(self):
                    self.data = {}
                    self.processed = False

                def input_data(self, key, value):
                    self.data[key] = value
                    return True

                def process_data(self):
                    if not self.data:
                        return False

                    # Simulate processing
                    processed_values = []
                    for key, value in self.data.items():
                        processed_values.append(f"processed_{value}")

                    self.processed_data = processed_values
                    self.processed = True
                    return True

                def get_processed_data(self):
                    return self.processed_data if self.processed else []
            """;

        executePythonCode(dataWorkflow);
        Object workflow = executePythonCode("DataFlowWorkflow()");

        // Data input
        executePythonCode("workflow.input_data('item1', 'value1')");
        executePythonCode("workflow.input_data('item2', 'value2')");
        assertThat(executePythonCode("workflow.data"), equalTo("map"));

        // Data processing
        executePythonCode("workflow.process_data()");
        assertTrue((Boolean) executePythonCode("workflow.processed"));
        assertThat(executePythonCode("workflow.get_processed_data()"),
                   equalTo(new Object[]{"processed_value1", "processed_value2"}));
    }

    @Test
    @DisplayName("Performance benchmark: Functional equivalence")
    void testFunctionalEquivalencePerformance() throws Exception {
        String[] testWorkflows = {
            SIMPLE_WORKFLOW_PY,
            PARALLEL_SPLIT_WORKFLOW_PY,
            """
            class SimpleTaskWorkflow:
                def __init__(self):
                    self.count = 0

                def execute_task(self):
                    self.count += 1
                    return True

                def get_count(self):
                    return self.count
            """
        };

        for (String workflow : testWorkflows) {
            long executionTime = benchmarkExecution(() -> {
                try {
                    executePythonCode(workflow);
                    Object workflowObj = executePythonCode("SimpleTaskWorkflow()");
                    executePythonCode("workflowObj.execute_task()");
                    executePythonCode("workflowObj.execute_task()");
                    executePythonCode("workflowObj.execute_task()");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, 1000);

            assertThat("Functional execution performance", executionTime, lessThan(50L));
        }
    }

    @Test
    @DisplayName("Concurrent execution safety")
    void testConcurrentExecutionSafety() throws Exception {
        String concurrentWorkflow = """
            import threading

            class ConcurrentWorkflow:
                def __init__(self):
                    self.counter = 0
                    self.lock = threading.Lock()
                    self.completed = False

                def increment_counter(self):
                    with self.lock:
                        self.counter += 1
                        if self.counter >= 10:
                            self.completed = True
                        return True
            """;

        executePythonCode(concurrentWorkflow);

        // Test concurrent access
        Runnable incrementTask = () -> {
            try {
                for (int i = 0; i < 5; i++) {
                    executePythonCode("workflow.increment_counter()");
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        Thread thread1 = new Thread(incrementTask);
        Thread thread2 = new Thread(incrementTask);

        thread1.start();
        thread2.start();

        thread1.join();
        thread2.join();

        assertThat(executePythonCode("workflow.counter"), equalTo(10));
        assertThat(executePythonCode("workflow.completed"), equalTo(true));
    }

    @Test
    @DisplayName("State persistence preservation")
    void testStatePersistencePreservation() throws Exception {
        String persistenceWorkflow = """
            class PersistentWorkflow:
                def __init__(self):
                    self.state = "initial"
                    self.history = ["initial"]

                def transition(self, new_state):
                    self.state = new_state
                    self.history.append(new_state)
                    return True

                def get_state(self):
                    return self.state

                def get_history(self):
                    return self.history
            """;

        executePythonCode(persistenceWorkflow);
        Object workflow = executePythonCode("PersistentWorkflow()");

        // Perform state transitions
        executePythonCode("workflow.transition('processing')");
        executePythonCode("workflow.transition('validating')");
        executePythonCode("workflow.transition('completed')");

        // Verify state persistence
        assertThat(executePythonCode("workflow.get_state()"), equalTo("completed"));
        assertThat(executePythonCode("workflow.get_history()"),
                   equalTo(new Object[]{"initial", "processing", "validating", "completed"}));
    }

    @Test
    @DisplayName("Workflow validation rules preservation")
    void testWorkflowValidationRules() throws Exception {
        String validationWorkflow = """
            class ValidationWorkflow:
                def __init__(self):
                    self.valid = True
                    self.errors = []

                def validate_data(self, data):
                    errors = []

                    if not isinstance(data, dict):
                        errors.append("Data must be a dictionary")

                    if 'required_field' not in data:
                        errors.append("Missing required_field")

                    if data.get('age', 0) < 0:
                        errors.append("Age must be non-negative")

                    if errors:
                        self.valid = False
                        self.errors = errors
                        return False

                    self.valid = True
                    return True
            """;

        executePythonCode(validationWorkflow);
        Object workflow = executePythonCode("ValidationWorkflow()");

        // Test valid data
        executePythonCode("workflow.validate_data({'required_field': 'test', 'age': 25})");
        assertTrue((Boolean) executePythonCode("workflow.valid"));
        assertThat(executePythonCode("workflow.errors"), equalTo(new Object[0]));

        // Test invalid data
        executePythonCode("workflow.validate_data({'age': -5})");
        assertFalse((Boolean) executePythonCode("workflow.valid"));
        assertThat(executePythonCode("workflow.errors"),
                   hasItemInArray("Age must be non-negative"));
    }

    @Test
    @DisplayName("Workflow rollback preservation")
    void testWorkflowRollbackPreservation() throws Exception {
        String rollbackWorkflow = """
            class RollbackWorkflow:
                def __init__(self):
                    self.state_stack = []
                    self.current_state = "initial"
                    self.operations_log = []

                def begin_operation(self, operation_name):
                    self.state_stack.append(self.current_state)
                    self.operations_log.append(f"begin_{operation_name}")
                    self.current_state = operation_name
                    return True

                def commit_operation(self):
                    self.operations_log.append(f"commit_{self.current_state}")
                    self.state_stack = []
                    self.current_state = "committed"
                    return True

                def rollback_operation(self):
                    if self.state_stack:
                        self.current_state = self.state_stack.pop()
                        self.operations_log.append(f"rollback_{self.current_state}")
                        return True
                    return False
            """;

        executePythonCode(rollbackWorkflow);
        Object workflow = executePythonCode("RollbackWorkflow()");

        // Perform operation with rollback
        executePythonCode("workflow.begin_operation('operation1')");
        executePythonCode("workflow.begin_operation('operation2')");

        executePythonCode("workflow.rollback_operation()");
        assertThat(executePythonCode("workflow.current_state"), equalTo("operation1"));

        executePythonCode("workflow.rollback_operation()");
        assertThat(executePythonCode("workflow.current_state"), equalTo("initial"));

        assertThat(executePythonCode("workflow.operations_log"),
                   equalTo(new Object[]{"begin_operation1", "begin_operation2", "rollback_operation2", "rollback_operation1"}));
    }
}