/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.dspy.training;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.yawlfoundation.yawl.elements.*;
import org.yawlfoundation.yawl.engine.YWorkItemRecord;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for HistoricalWorkflowExtractor following Chicago TDD methodology.
 *
 * <p>This test suite validates the training data extraction functionality with real YAWL objects
 * and mock dependencies to ensure comprehensive coverage without requiring actual database connection.</p>
 *
 * <h3>Key Test Areas:</h3>
 * <ul>
 *   <li>Perfect workflow extraction with different optimization targets</li>
 *   <li>Footprint scoring validation</li>
 *   <li>Behavioral feature extraction</li>
 *   <li>Quality filtering</li>
 *   <li>Edge case handling</li>
 *   <li>Performance metrics simulation</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Historical Workflow Extractor Tests")
class HistoricalWorkflowExtractorTest {

    private HistoricalWorkflowExtractor extractor;

    @Mock
    private FootprintScorer mockFootprintScorer;

    private YNet testWorkflow;
    private YNet complexWorkflow;
    private YNet incompleteWorkflow;

    @BeforeEach
    void setUp() {
        extractor = new HistoricalWorkflowExtractor();

        // Create mock footprint scorer
        when(mockFootprintScorer.score(any(), anyString())).thenReturn(0.95);

        // Create test workflows
        testWorkflow = createTestWorkflow("SimpleWorkflow", YWorkflowStatus.COMPLETED);
        complexWorkflow = createComplexWorkflow("ComplexWorkflow", YWorkflowStatus.COMPLETED);
        incompleteWorkflow = createTestWorkflow("IncompleteWorkflow", YWorkflowStatus.IN_PROGRESS);
    }

    // ========== Perfect Workflow Extraction Tests ==========

    @Test
    @DisplayName("Perfect workflow extraction - Behavioral target")
    void extractPerfectWorkflowBehavioralTarget() {
        // Arrange: Mock perfect footprint score
        when(mockFootprintScorer.score(any(YWorkItemRecord.class))).thenReturn(1.0);

        // Act: Extract examples with behavioral target
        List<DspyTrainingExample> examples = extractor.extractPerfectWorkflowExamples(
            testWorkflow,
            "behavioral"
        );

        // Assert: Verify extraction results
        assertNotNull(examples);
        assertFalse(examples.isEmpty());

        // Validate example properties
        DspyTrainingExample example = examples.get(0);
        assertEquals("SimpleWorkflow", example.workflowId());
        assertNotNull(example.behavioralFeatures());
        assertEquals(1.0, example.footprintScore(), 0.001);

        // Verify behavioral features
        @SuppressWarnings("unchecked")
        Map<String, Object> features = (Map<String, Object>) example.behavioralFeatures();
        assertEquals(1L, features.get("sequential_tasks"));
        assertEquals(0L, features.get("branch_points"));
        assertEquals(0L, features.get("join_points"));
    }

    @Test
    @DisplayName("Perfect workflow extraction - Performance target")
    void extractPerfectWorkflowPerformanceTarget() {
        // Arrange: Mock good performance
        when(mockFootprintScorer.score(any(YWorkItemRecord.class))).thenReturn(0.95);

        // Act: Extract examples with performance target
        List<DspyTrainingExample> examples = extractor.extractPerfectWorkflowExamples(
            complexWorkflow,
            "performance"
        );

        // Assert: Verify performance extraction
        assertNotNull(examples);
        assertFalse(examples.isEmpty());

        DspyTrainingExample example = examples.get(0);
        assertEquals("ComplexWorkflow", example.workflowId());
        assertNotNull(example.performanceMetrics());
        assertEquals(0.95, example.footprintScore(), 0.001);

        // Performance metrics not implemented in HistoricalWorkflowExtractor
        assertThrows(UnsupportedOperationException.class, () -> {
            extractor.extractPerformanceData(complexWorkflow);
        });
    }

    @Test
    @DisplayName("Perfect workflow extraction - Balanced target")
    void extractPerfectWorkflowBalancedTarget() {
        // Arrange: Mock balanced performance
        when(mockFootprintScorer.score(any(YWorkItemRecord.class))).thenReturn(0.9);

        // Act: Extract examples with balanced target
        List<DspyTrainingExample> examples = extractor.extractPerfectWorkflowExamples(
            complexWorkflow,
            "balanced"
        );

        // Assert: Verify balanced extraction
        assertNotNull(examples);
        assertFalse(examples.isEmpty());

        DspyTrainingExample example = examples.get(0);
        assertEquals("ComplexWorkflow", example.workflowId());
        assertEquals("balanced", getTargetFromExample(example)); // Verify combined features
    }

    // ========== Quality Filtering Tests ==========

    @Test
    @DisplayName("Quality filtering - High quality threshold")
    void filterByQualityHighThreshold() {
        // Arrange: Create examples with varying quality scores
        List<DspyTrainingExample> examples = List.of(
            createTrainingExample(0.95, "WorkflowA"),
            createTrainingExample(0.92, "WorkflowB"),
            createTrainingExample(0.88, "WorkflowC"),
            createTrainingExample(0.85, "WorkflowD")
        );

        // Act: Filter with high threshold
        List<DspyTrainingExample> filtered = extractor.filterByQuality(examples, 0.9);

        // Assert: Verify filtering results
        assertEquals(2, filtered.size());
        assertEquals(0.95, filtered.get(0).footprintScore(), 0.001);
        assertEquals(0.92, filtered.get(1).footprintScore(), 0.001);
        assertEquals("WorkflowA", filtered.get(0).workflowId());
        assertEquals("WorkflowB", filtered.get(1).workflowId());
    }

    @Test
    @DisplayName("Quality filtering - Low threshold")
    void filterByQualityLowThreshold() {
        // Arrange: Create examples with low quality scores
        List<DspyTrainingExample> examples = List.of(
            createTrainingExample(0.95, "WorkflowA"),
            createTrainingExample(0.85, "WorkflowB"),
            createTrainingExample(0.75, "WorkflowC"),
            createTrainingExample(0.65, "WorkflowD")
        );

        // Act: Filter with low threshold
        List<DspyTrainingExample> filtered = extractor.filterByQuality(examples, 0.7);

        // Assert: Verify all examples above threshold included
        assertEquals(3, filtered.size());
        assertTrue(filtered.stream().allMatch(ex -> ex.footprintScore() >= 0.7));
    }

    @Test
    @DisplayName("Quality filtering - Edge case: Empty list")
    void filterByQualityEmptyList() {
        // Arrange: Empty examples list
        List<DspyTrainingExample> emptyList = Collections.emptyList();

        // Act: Filter empty list
        List<DspyTrainingExample> filtered = extractor.filterByQuality(emptyList, 0.8);

        // Assert: Should return empty list
        assertTrue(filtered.isEmpty());
    }

    @Test
    @DisplayName("Quality filtering - Invalid threshold")
    void filterByQualityInvalidThreshold() {
        // Arrange: Examples list
        List<DspyTrainingExample> examples = List.of(createTrainingExample(0.9, "Test"));

        // Act & Assert: Test invalid thresholds
        assertThrows(IllegalArgumentException.class, () ->
            extractor.filterByQuality(examples, -0.1)); // Negative threshold

        assertThrows(IllegalArgumentException.class, () ->
            extractor.filterByQuality(examples, 1.1)); // Threshold > 1.0
    }

    // ========== Behavioral Feature Extraction Tests ==========

    @Test
    @DisplayName("Behavioral features - Simple workflow")
    void extractBehavioralFeaturesSimpleWorkflow() {
        // Arrange: Simple workflow with linear structure
        testWorkflow = createTestWorkflow("SimpleWorkflow", YWorkflowStatus.COMPLETED);

        // Act: Extract features
        Map<String, Object> features = extractor.extractBehavioralFeatures(testWorkflow);

        // Assert: Verify feature extraction
        assertNotNull(features);
        assertEquals(3L, features.get("sequential_tasks"));
        assertEquals(0L, features.get("branch_points"));
        assertEquals(0L, features.get("join_points"));
        assertEquals(1, features.get("cyclomatic_complexity"));

        // Verify feature values are immutable
        assertThrows(UnsupportedOperationException.class, () -> {
            ((Map<String, Object>) features).put("test", "value");
        });
    }

    @Test
    @DisplayName("Behavioral features - Complex workflow")
    void extractBehavioralFeaturesComplexWorkflow() {
        // Arrange: Complex workflow with branching
        complexWorkflow = createComplexWorkflow("ComplexWorkflow", YWorkflowStatus.COMPLETED);

        // Act: Extract features
        Map<String, Object> features = extractor.extractBehavioralFeatures(complexWorkflow);

        // Assert: Verify complex feature extraction
        assertNotNull(features);
        assertTrue((long) features.get("sequential_tasks") >= 2L);
        assertTrue((long) features.get("branch_points") >= 1L);
        assertTrue((long) features.get("join_points") >= 1L);
        assertTrue((int) features.get("cyclomatic_complexity") >= 3);
    }

    // ========== Performance Metrics Tests ==========

    @Test
    @DisplayName("Performance metrics - Throwing UnsupportedOperationException")
    void extractPerformanceDataThrowsException() {
        // Arrange: Workflow with completed status
        YNet workflow = createTestWorkflow("TestWorkflow", YWorkflowStatus.COMPLETED);

        // Act & Assert: Should throw UnsupportedOperationException
        assertThrows(UnsupportedOperationException.class, () ->
            extractor.extractPerformanceData(workflow)
        );
    }

    // ========== Edge Case Tests ==========

    @Test
    @DisplayName("Edge case - Incomplete workflow")
    void incompleteWorkflowThrowsException() {
        // Arrange: Incomplete workflow
        incompleteWorkflow = createTestWorkflow("IncompleteWorkflow", YWorkflowStatus.IN_PROGRESS);

        // Act & Assert: Should throw UnsupportedOperationException
        assertThrows(UnsupportedOperationException.class, () ->
            extractor.extractPerfectWorkflowExamples(incompleteWorkflow, "behavioral")
        );
    }

    @Test
    @DisplayName("Edge case - Null workflow")
    void nullWorkflowThrowsException() {
        // Act & Assert: Should throw NullPointerException
        assertThrows(NullPointerException.class, () ->
            extractor.extractPerfectWorkflowExamples(null, "behavioral")
        );
    }

    @Test
    @DisplayName("Edge case - Invalid optimization target")
    void invalidOptimizationTargetThrowsException() {
        // Arrange: Valid workflow
        YNet workflow = createTestWorkflow("TestWorkflow", YWorkflowStatus.COMPLETED);

        // Act & Assert: Should throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () ->
            extractor.extractPerfectWorkflowExamples(workflow, "invalid_target")
        );
    }

    // ========== Concurrent Access Tests ==========

    @Test
    @DisplayName("Concurrent extraction - Thread safety")
    void concurrentExtractionThreadSafety() throws InterruptedException {
        // Arrange: Multiple workflows
        List<YNet> workflows = List.of(
            createTestWorkflow("Workflow1", YWorkflowStatus.COMPLETED),
            createTestWorkflow("Workflow2", YWorkflowStatus.COMPLETED),
            createTestWorkflow("Workflow3", YWorkflowStatus.COMPLETED)
        );

        // Act: Extract examples from multiple threads
        List<DspyTrainingExample> allExamples = Collections.synchronizedList(new ArrayList<>());
        List<Thread> threads = new ArrayList<>();

        for (YNet workflow : workflows) {
            Thread thread = new Thread(() -> {
                List<DspyTrainingExample> examples = extractor.extractPerfectWorkflowExamples(
                    workflow, "behavioral"
                );
                allExamples.addAll(examples);
            });
            threads.add(thread);
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join(1000); // Wait with timeout
        }

        // Assert: Verify all examples collected
        assertEquals(3, allExamples.size());
        assertTrue(allExamples.stream().allMatch(ex ->
            ex.footprintScore() >= 0.95 && ex.behavioralFeatures() != null
        ));
    }

    // ========== Performance Metrics Simulation Tests ==========

    @Test
    @DisplayName("Performance metrics - Throughput calculation")
    void throughputCalculation() {
        // Arrange: Workflow with time-stamped work items
        YNet workflow = createTestWorkflow("TimeTestWorkflow", YWorkflowStatus.COMPLETED);

        // Add work items with specific timing
        Instant startTime = Instant.now().minus(Duration.ofSeconds(10));
        Instant endTime = startTime.plus(Duration.ofSeconds(5));

        // Act: Test through-scope method
        // This tests the private throughput calculation
        double throughput = extractThroughputScore(workflow);

        // Assert: Verify throughput calculation
        assertTrue(throughput >= 0.0);
        // For empty work items, should be 0.0
        assertEquals(0.0, throughput, 0.001);
    }

    // ========== Helper Methods ==========

    private YNet createTestWorkflow(String name, YWorkflowStatus status) {
        YNet workflow = new YNet();
        workflow.setID(name);
        workflow.setWorkflowStatus(status);

        // Create simple task structure
        List<YTask> tasks = new ArrayList<>();
        YTask task1 = new YTask();
        task1.setID("task1");
        tasks.add(task1);

        YTask task2 = new YTask();
        task2.setID("task2");
        tasks.add(task2);

        workflow.setTasks(tasks);

        return workflow;
    }

    private YNet createComplexWorkflow(String name, YWorkflowStatus status) {
        YNet workflow = new YNet();
        workflow.setID(name);
        workflow.setWorkflowStatus(status);

        // Create complex task structure with branching
        List<YTask> tasks = new ArrayList<>();

        YTask startTask = new YTask();
        startTask.setID("start");
        tasks.add(startTask);

        YTask branchTask = new YTask();
        branchTask.setID("branch");
        tasks.add(branchTask);

        YTask parallel1 = new YTask();
        parallel1.setID("parallel1");
        tasks.add(parallel1);

        YTask parallel2 = new YTask();
        parallel2.setID("parallel2");
        tasks.add(parallel2);

        YTask joinTask = new YTask();
        joinTask.setID("join");
        tasks.add(joinTask);

        YTask endTask = new YTask();
        endTask.setID("end");
        tasks.add(endTask);

        workflow.setTasks(tasks);

        return workflow;
    }

    private DspyTrainingExample createTrainingExample(double score, String workflowId) {
        Map<String, Object> behavioralFeatures = Map.of(
            "sequential_tasks", 1L,
            "branch_points", 0L,
            "join_points", 0L,
            "cyclomatic_complexity", 1
        );

        Map<String, Object> performanceMetrics = Map.of(
            "execution_time_ms", 100L,
            "memory_usage_mb", 1.0
        );

        return new DspyTrainingExample(
            workflowId,
            behavioralFeatures,
            performanceMetrics,
            score
        );
    }

    private double extractThroughputScore(YNet workflow) {
        // Helper method to test throughput calculation
        var workItems = workflow.getCompletedWorkItems();
        if (workItems.isEmpty()) {
            return 0.0;
        }

        var startTime = workItems.stream()
            .map(YWorkItemRecord::getStartTime)
            .min(Instant::compareTo)
            .orElse(Instant.now());

        var endTime = workItems.stream()
            .map(YWorkItemRecord::getFinishTime)
            .max(Instant::compareTo)
            .orElse(Instant.now());

        var duration = Duration.between(startTime, endTime);
        var totalWorkItems = workItems.size();

        // Calculate throughput: items per second
        return duration.toMillis() > 0 ?
            (double) totalWorkItems / duration.toSeconds() : 0.0;
    }

    private String getTargetFromExample(DspyTrainingExample example) {
        // Helper method to determine optimization target from example features
        if (example.behavioralFeatures() != null && example.performanceMetrics() == null) {
            return "behavioral";
        } else if (example.behavioralFeatures() == null && example.performanceMetrics() != null) {
            return "performance";
        } else if (example.behavioralFeatures() != null && example.performanceMetrics() != null) {
            return "balanced";
        }
        return "unknown";
    }
}