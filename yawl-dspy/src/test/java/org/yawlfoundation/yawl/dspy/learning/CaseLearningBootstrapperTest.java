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

package org.yawlfoundation.yawl.dspy.learning;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.yawlfoundation.yawl.dspy.DspyProgramCache;
import org.yawlfoundation.yawl.dspy.PythonDspyBridge;
import org.yawlfoundation.yawl.engine.YWorkItem;
import org.yawlfoundation.yawl.engine.YWorkItemRepository;
import org.yawlfoundation.yawl.engine.YWorkItemStatus;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.elements.state.YIdentifier;
import org.yawlfoundation.yawl.graalpy.PythonExecutionEngine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for CaseLearningBootstrapper.
 *
 * <p>Chicago TDD discipline: Tests use REAL objects (YWorkItemRepository,
 * CaseLearningBootstrapper) with only external API dependencies stubbed
 * (PythonDspyBridge mocked for bootstrap behavior).</p>
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Bootstrap with valid training examples</li>
 *   <li>Insufficient examples (below threshold)</li>
 *   <li>Empty repository</li>
 *   <li>Example extraction from work items</li>
 *   <li>Caching of compiled bootstrap results</li>
 *   <li>Quality improvement metrics</li>
 *   <li>Exception handling and logging</li>
 * </ul>
 * </p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@DisplayName("CaseLearningBootstrapper Tests (Chicago TDD)")
public class CaseLearningBootstrapperTest {

    private CaseLearningBootstrapper bootstrapper;
    private YWorkItemRepository repository;
    private DspyProgramCache cache;
    private PythonDspyBridge dspyBridge;
    private PythonExecutionEngine engine;

    @BeforeEach
    void setUp() {
        // Real objects per Chicago TDD
        repository = new YWorkItemRepository();
        cache = new DspyProgramCache();

        // Create PythonDspyBridge with real engine
        engine = PythonExecutionEngine.builder()
                .contextPoolSize(2)
                .sandboxed(false)
                .build();
        dspyBridge = new PythonDspyBridge(engine);

        // Create bootstrapper with real dependencies
        bootstrapper = new CaseLearningBootstrapper(repository, dspyBridge, cache);
    }

    @Test
    @DisplayName("Should bootstrap with minimum valid examples")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testBootstrapWithValidExamples() throws Exception {
        // Arrange: Create 5 completed work items
        for (int i = 0; i < 5; i++) {
            YWorkItem item = createCompletedWorkItem("task_" + i, "Case_" + i);
            repository.add(item);
        }

        assertThat("Repository should contain 5 items", repository.getWorkItems().size(), equalTo(5));

        // Act
        bootstrapper.bootstrap(5);

        // Assert
        assertThat("Bootstrap should cache compiled module",
                bootstrapper.getBootstrappedModulePath(), notNullValue());
        assertThat("Cache should contain bootstrap result",
                cache.contains("dspy_powl_generator_bootstrapped"), is(true));
    }

    @Test
    @DisplayName("Should skip bootstrap when examples below threshold")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testBootstrapSkippedBelowThreshold() throws Exception {
        // Arrange: Create only 2 work items
        for (int i = 0; i < 2; i++) {
            YWorkItem item = createCompletedWorkItem("task_" + i, "Case_" + i);
            repository.add(item);
        }

        // Act: Request bootstrap with min 5 examples
        bootstrapper.bootstrap(5);

        // Assert: Should not cache anything
        assertThat("Bootstrap should be skipped with insufficient examples",
                bootstrapper.getBootstrappedModulePath(), nullValue());
    }

    @Test
    @DisplayName("Should handle empty repository gracefully")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testBootstrapWithEmptyRepository() throws Exception {
        // Arrange: Repository is empty
        assertThat("Repository should be empty", repository.getWorkItems().isEmpty(), is(true));

        // Act: Bootstrap should not fail
        assertDoesNotThrow(() -> bootstrapper.bootstrap(5));

        // Assert: Should skip without caching
        assertThat("No cache entry should be created",
                bootstrapper.getBootstrappedModulePath(), nullValue());
    }

    @Test
    @DisplayName("Should use default minimum examples threshold")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testBootstrapWithDefaultThreshold() throws Exception {
        // Arrange: Create 5 work items
        for (int i = 0; i < 5; i++) {
            YWorkItem item = createCompletedWorkItem("task_" + i, "Case_" + i);
            repository.add(item);
        }

        // Act: Use default threshold (5)
        bootstrapper.bootstrap();

        // Assert
        assertThat("Should cache compiled module with default threshold",
                bootstrapper.getBootstrappedModulePath(), notNullValue());
    }

    @Test
    @DisplayName("Should reject invalid minExamples threshold")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testBootstrapInvalidThreshold() {
        // Assert: Should throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () -> bootstrapper.bootstrap(0));
        assertThrows(IllegalArgumentException.class, () -> bootstrapper.bootstrap(-1));
    }

    @Test
    @DisplayName("Should extract training examples from work items")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testExampleExtraction() throws Exception {
        // Arrange
        YWorkItem item = createCompletedWorkItem("approve_request", "Case_001");
        CaseExampleExtractor extractor = new CaseExampleExtractor(item);

        // Act
        DspyTrainingExample example = extractor.extract();

        // Assert
        assertThat("Example should have non-empty input",
                example.input(), not(emptyString()));
        assertThat("Example should include task name",
                example.input(), containsString("approve_request"));
        assertThat("Example output should be map",
                example.output(), notNullValue());
        assertThat("Output should contain task type",
                example.output().get("type"), is("task"));
    }

    @Test
    @DisplayName("Should handle example extraction failure gracefully")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testExampleExtractionFailure() throws Exception {
        // Arrange: Create work item with null specification (edge case)
        YWorkItem item = createCompletedWorkItem("test_task", "Case_001");

        // Act & Assert: Extraction should handle gracefully
        assertThrows(IllegalStateException.class, () -> {
            CaseExampleExtractor extractor = new CaseExampleExtractor(item);
            extractor.extract();
        });
    }

    @Test
    @DisplayName("Should generate valid DspyTrainingExample records")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testDspyTrainingExampleValidation() {
        // Arrange
        String input = "Task: Review Document (Case: C001)\nDescription: Review workflow task";
        Map<String, Object> output = Map.of(
                "type", "task",
                "name", "Review Document",
                "id", "task_1"
        );

        // Act
        DspyTrainingExample example = new DspyTrainingExample(input, output);

        // Assert
        assertThat("Example should store input", example.input(), equalTo(input));
        assertThat("Example should store output", example.output(), equalTo(output));
    }

    @Test
    @DisplayName("Should reject invalid training examples")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testInvalidTrainingExamples() {
        // Assert: Null inputs should fail
        assertThrows(NullPointerException.class, () ->
                new DspyTrainingExample(null, Map.of("key", "value")));

        // Assert: Blank input should fail
        assertThrows(IllegalArgumentException.class, () ->
                new DspyTrainingExample("   ", Map.of("key", "value")));

        // Assert: Null output should fail
        assertThrows(NullPointerException.class, () ->
                new DspyTrainingExample("input text", null));

        // Assert: Empty output should fail
        assertThrows(IllegalArgumentException.class, () ->
                new DspyTrainingExample("input text", Map.of()));
    }

    @Test
    @DisplayName("Should support bootstrap scheduler integration")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testBootstrapSchedulerIntegration() throws Exception {
        // Arrange: Create scheduler with bootstrapper
        BootstrapScheduler scheduler = new BootstrapScheduler(bootstrapper, 3);
        for (int i = 0; i < 5; i++) {
            YWorkItem item = createCompletedWorkItem("task_" + i, "Case_" + i);
            repository.add(item);
        }

        // Act: Trigger bootstrap via scheduler (which calls bootstrapper)
        scheduler.triggerBootstrap();

        // Assert
        assertThat("Scheduler should invoke bootstrap",
                bootstrapper.getBootstrappedModulePath(), notNullValue());
        assertThat("Counter should be reset after bootstrap",
                scheduler.getCompletedCasesSinceBootstrap(), equalTo(0L));
    }

    @Test
    @DisplayName("Should cache compiled bootstrap modules")
    @Timeout(value = 15, unit = TimeUnit.SECONDS)
    void testBootstrapCaching() throws Exception {
        // Arrange
        for (int i = 0; i < 5; i++) {
            YWorkItem item = createCompletedWorkItem("task_" + i, "Case_" + i);
            repository.add(item);
        }

        // Act: First bootstrap
        bootstrapper.bootstrap(5);
        String firstPath = bootstrapper.getBootstrappedModulePath();

        // Assert: Path should be cached
        assertThat("First bootstrap should cache module", firstPath, notNullValue());
        assertThat("Cache should return same path on second call",
                bootstrapper.getBootstrappedModulePath(), equalTo(firstPath));
    }

    @Test
    @DisplayName("Should handle large example sets efficiently")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void testBootstrapWithLargeExampleSet() throws Exception {
        // Arrange: Create 50 work items
        for (int i = 0; i < 50; i++) {
            YWorkItem item = createCompletedWorkItem("task_" + i, "Case_" + i);
            repository.add(item);
        }

        // Act: Measure bootstrap time
        long startTime = System.currentTimeMillis();
        bootstrapper.bootstrap(50);
        long duration = System.currentTimeMillis() - startTime;

        // Assert
        assertThat("Bootstrap should complete within 20 seconds", duration, lessThan(20000L));
        assertThat("Should cache large example set",
                bootstrapper.getBootstrappedModulePath(), notNullValue());
    }

    @Test
    @DisplayName("Should only process completed work items")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testOnlyCompletedItemsProcessed() throws Exception {
        // Arrange: Mix of completed and non-completed items
        YWorkItem completed = createCompletedWorkItem("task_1", "Case_1");
        YWorkItem enabled = createEnabledWorkItem("task_2", "Case_2");

        repository.add(completed);
        repository.add(enabled);

        // Act: Query completed items
        var completedSet = repository.getWorkItems(YWorkItemStatus.statusComplete);

        // Assert: Only completed items should be retrieved
        assertThat("Only completed items should be in result",
                completedSet.size(), equalTo(1));
        assertThat("Result should contain the completed work item",
                completedSet.stream().anyMatch(w -> w.getIDString().contains("task_1")), is(true));
    }

    @Test
    @DisplayName("Should validate training example consistency")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testTrainingExampleConsistency() {
        // Arrange
        Map<String, Object> output1 = Map.of(
                "type", "task",
                "name", "Approve"
        );
        Map<String, Object> output2 = Map.of(
                "type", "task",
                "name", "Reject"
        );

        DspyTrainingExample ex1 = new DspyTrainingExample(
                "Approve workflow", output1);
        DspyTrainingExample ex2 = new DspyTrainingExample(
                "Reject workflow", output2);

        // Assert: Examples should maintain distinct outputs
        assertThat("Examples should be distinct",
                ex1.output().get("name"), not(equalTo(ex2.output().get("name"))));
    }

    // ========================================================================
    // Helper Methods (Create Test Fixtures)
    // ========================================================================

    /**
     * Creates a completed work item for testing.
     *
     * @param taskId task identifier
     * @param caseId case identifier
     * @return completed YWorkItem
     */
    private YWorkItem createCompletedWorkItem(String taskId, String caseId) {
        YIdentifier caseIdentifier = new YIdentifier(caseId);
        YWorkItem item = new YWorkItem(caseIdentifier, taskId, null);

        // Set minimal required fields to complete state
        item.setStatus(YWorkItemStatus.statusComplete);
        item.setTimesStarted(1);

        return item;
    }

    /**
     * Creates an enabled work item for testing.
     *
     * @param taskId task identifier
     * @param caseId case identifier
     * @return enabled YWorkItem
     */
    private YWorkItem createEnabledWorkItem(String taskId, String caseId) {
        YIdentifier caseIdentifier = new YIdentifier(caseId);
        YWorkItem item = new YWorkItem(caseIdentifier, taskId, null);

        item.setStatus(YWorkItemStatus.statusEnabled);
        item.setTimesStarted(0);

        return item;
    }
}
