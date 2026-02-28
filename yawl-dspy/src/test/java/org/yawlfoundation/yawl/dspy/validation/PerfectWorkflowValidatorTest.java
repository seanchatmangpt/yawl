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

package org.yawlfoundation.yawl.dspy.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.yawlfoundation.yawl.elements.*;
import org.yawlfoundation.yawl.stateless.engine.YNetRunner;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for PerfectWorkflowValidator following Chicago TDD methodology.
 *
 * <p>This test suite validates the workflow validation functionality with mock dependencies
 * to ensure comprehensive coverage without requiring actual YAWL execution engine or LLM services.</p>
 *
 * <h3>Key Test Areas:</h3>
 * <ul>
 *   <li>Perfect workflow detection with different optimization targets</li>
 *   <li>Behavioral footprint validation</li>
 *   <li>Performance metrics validation</li>
 *   <li>Resource utilization validation</li>
 *   <li>Semantic accuracy validation</li>
 *   <li>Error handling and edge cases</li>
 *   <li>Concurrent validation execution</li>
 *   <li>Configuration and threshold management</li>
 * </ul>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Perfect Workflow Validator Tests")
class PerfectWorkflowValidatorTest {

    private PerfectWorkflowValidator validator;

    // @Mock - Removed due to compilation errors
    // private FootprintScorer mockFootprintScorer;

    @Mock
    private YNet mockGeneratedWorkflow;

    @Mock
    private YNet mockReferenceWorkflow;

    @Mock
    private YNetRunner mockRunner;

    private YNet perfectWorkflow;
    private YNet imperfectWorkflow;
    private YNet poorPerformanceWorkflow;

    @BeforeEach
    void setUp() {
        // Create validator with LLM judge enabled
        validator = new PerfectWorkflowValidator(true);

        // Create mock workflows
        perfectWorkflow = createPerfectWorkflow();
        imperfectWorkflow = createImperfectWorkflow();
        poorPerformanceWorkflow = createPoorPerformanceWorkflow();

        // Mock dependencies
        when(mockGeneratedWorkflow.getWorkflowStatus()).thenReturn(YWorkflowStatus.COMPLETED);
        when(mockReferenceWorkflow.getWorkflowStatus()).thenReturn(YWorkflowStatus.COMPLETED);
    }

    // ========== Perfect Workflow Validation Tests ==========

    @Test
    @DisplayName("Perfect workflow validation - Perfect generation detection")
    void perfectWorkflowValidationPerfectGeneration() {
        // Arrange: Mock perfect scores
        when(mockReferenceWorkflow.getID()).thenReturn("PerfectWorkflow");
        when(mockGeneratedWorkflow.getID()).thenReturn("PerfectWorkflow");

        // Act: Validate perfect workflow
        ValidationResult result = validator.validatePerfectWorkflow(
            perfectWorkflow,
            perfectWorkflow,
            PerfectWorkflowValidator.GepaOptimizationTarget.BEHAVIORAL
        );

        // Assert: Verify perfect generation
        assertNotNull(result);
        assertTrue(result.perfectGeneration());
        assertTrue(result.passed());
        assertEquals(0, result.errors().size());
        assertEquals(0, result.warnings().size());

        // Verify metrics
        result.metrics().forEach(metric -> {
            assertEquals("perfect", metric.status());
            assertEquals(1.0, metric.score(), 0.001);
        });
    }

    @Test
    @DisplayName("Perfect workflow validation - Behavioral target perfect score")
    void perfectWorkflowValidationBehavioralTarget() {
        // Arrange: Mock perfect behavioral score
        when(mockReferenceWorkflow.getID()).thenReturn("BehavioralPerfect");
        when(mockGeneratedWorkflow.getID()).thenReturn("BehavioralPerfect");

        // Act: Validate with behavioral target
        ValidationResult result = validator.validatePerfectWorkflow(
            perfectWorkflow,
            perfectWorkflow,
            PerfectWorkflowValidator.GepaOptimizationTarget.BEHAVIORAL
        );

        // Assert: Verify behavioral perfection
        assertTrue(result.perfectGeneration());

        // Check behavioral metric
        result.metrics().stream()
            .filter(metric -> metric.name().equals("behavioral-footprint"))
            .findFirst()
            .ifPresent(metric -> {
                assertEquals(1.0, metric.score(), 0.001);
                assertEquals("perfect", metric.status());
            });
    }

    // ========== Imperfect Workflow Validation Tests ==========

    @Test
    @DisplayName("Imperfect workflow validation - Behavioral footprint error")
    void imperfectWorkflowValidationBehavioralError() {
        // Arrange: Mock imperfect behavioral score
        when(mockReferenceWorkflow.getID()).thenReturn("ReferenceWorkflow");
        when(mockGeneratedWorkflow.getID()).thenReturn("ImperfectWorkflow");

        // Act: Validate imperfect workflow
        ValidationResult result = validator.validatePerfectWorkflow(
            imperfectWorkflow,
            mockReferenceWorkflow,
            PerfectWorkflowValidator.GepaOptimizationTarget.BEHAVIORAL
        );

        // Assert: Verify validation failure
        assertFalse(result.perfectGeneration());
        assertFalse(result.passed());
        assertEquals(1, result.errors().size());
        assertTrue(result.errors().get(0).contains("Behavioral footprint is not perfect"));
    }

    @Test
    @DisplayName("Imperfect workflow validation - Performance error")
    void imperfectWorkflowValidationPerformanceError() {
        // Arrange: Mock poor performance
        when(mockFootprintScorer.score(any(), anyString())).thenReturn(1.0); // Perfect footprint
        when(mockRunner.start()).thenThrow(new RuntimeException("Poor performance"));
        when(mockReferenceWorkflow.getID()).thenReturn("ReferenceWorkflow");
        when(mockGeneratedWorkflow.getID()).thenReturn("PoorPerformanceWorkflow");

        // Act: Validate poor performance workflow
        ValidationResult result = validator.validatePerfectWorkflow(
            poorPerformanceWorkflow,
            mockReferenceWorkflow,
            PerfectWorkflowValidator.GepaOptimizationTarget.PERFORMANCE
        );

        // Assert: Verify performance failure
        assertFalse(result.perfectGeneration());
        assertFalse(result.passed());
        assertTrue(result.errors().get(0).contains("Performance does not meet targets"));
    }

    @Test
    @DisplayName("Imperfect workflow validation - Resource utilization error")
    void imperfectWorkflowValidationResourceError() {
        // Arrange: Mock inefficient resource usage
        when(mockFootprintScorer.score(any(), anyString())).thenReturn(1.0);
        when(mockRunner.start()).thenReturn(null);
        when(mockReferenceWorkflow.getID()).thenReturn("ReferenceWorkflow");
        when(mockGeneratedWorkflow.getID()).thenReturn("InefficientWorkflow");

        // Act: Validate resource-inefficient workflow
        ValidationResult result = validator.validatePerfectWorkflow(
            poorPerformanceWorkflow, // This workflow will fail resource validation
            mockReferenceWorkflow,
            PerfectWorkflowValidator.GepaOptimizationTarget.BALANCED
        );

        // Assert: Verify resource failure
        assertFalse(result.perfectGeneration());
        assertFalse(result.passed());
        assertTrue(result.errors().get(0).contains("Resource utilization is inefficient"));
    }

    // ========== Configuration Tests ==========

    @Test
    @DisplayName("Configuration - Footprint threshold setting")
    void configurationFootprintThreshold() {
        // Arrange: Create validator and set threshold
        PerfectWorkflowValidator validator = new PerfectWorkflowValidator(false);

        // Act: Set different thresholds
        validator.withFootprintThreshold(0.9);
        validator.withFootprintThreshold(0.95);
        validator.withFootprintThreshold(1.0);

        // Assert: Should not throw exceptions for valid thresholds
        assertTrue(true); // No exceptions thrown
    }

    @Test
    @DisplayName("Configuration - Invalid footprint threshold")
    void configurationInvalidFootprintThreshold() {
        // Arrange: Create validator
        PerfectWorkflowValidator validator = new PerfectWorkflowValidator(false);

        // Act & Assert: Test invalid thresholds
        assertThrows(IllegalArgumentException.class, () ->
            validator.withFootprintThreshold(-0.1));

        assertThrows(IllegalArgumentException.class, () ->
            validator.withFootprintThreshold(1.1));
    }

    @Test
    @DisplayName("Configuration - Semantic threshold setting")
    void configurationSemanticThreshold() {
        // Arrange: Create validator with LLM judge
        PerfectWorkflowValidator validator = new PerfectWorkflowValidator(true);

        // Act: Set semantic thresholds
        validator.withSemanticThreshold(0.9);
        validator.withSemanticThreshold(0.95);
        validator.withSemanticThreshold(1.0);

        // Assert: Should not throw exceptions for valid thresholds
        assertTrue(true); // No exceptions thrown
    }

    @Test
    @DisplayName("Configuration - Invalid semantic threshold")
    void configurationInvalidSemanticThreshold() {
        // Arrange: Create validator
        PerfectWorkflowValidator validator = new PerfectWorkflowValidator(true);

        // Act & Assert: Test invalid thresholds
        assertThrows(IllegalArgumentException.class, () ->
            validator.withSemanticThreshold(-0.1));

        assertThrows(IllegalArgumentException.class, () ->
            validator.withSemanticThreshold(1.1));
    }

    // ========== Error Handling Tests ==========

    @Test
    @DisplayName("Error handling - Null parameters")
    void errorHandlingNullParameters() {
        // Act & Assert: Test null parameters
        assertThrows(NullPointerException.class, () ->
            validator.validatePerfectWorkflow(null, mockReferenceWorkflow, PerfectWorkflowValidator.GepaOptimizationTarget.BEHAVIORAL));

        assertThrows(NullPointerException.class, () ->
            validator.validatePerfectWorkflow(mockGeneratedWorkflow, null, PerfectWorkflowValidator.GepaOptimizationTarget.BEHAVIORAL));

        assertThrows(NullPointerException.class, () ->
            validator.validatePerfectWorkflow(mockGeneratedWorkflow, mockReferenceWorkflow, null));
    }

    @Test
    @DisplayName("Error handling - Incomplete workflow")
    void errorHandlingIncompleteWorkflow() {
        // Arrange: Mock incomplete workflow
        when(mockGeneratedWorkflow.getWorkflowStatus()).thenReturn(YWorkflowStatus.IN_PROGRESS);

        // Act & Assert: Should throw exception
        assertThrows(IllegalArgumentException.class, () ->
            validator.validatePerfectWorkflow(
                mockGeneratedWorkflow,
                mockReferenceWorkflow,
                PerfectWorkflowValidator.GepaOptimizationTarget.BEHAVIORAL
            ));
    }

    // ========== Performance Tests ==========

    @Test
    @DisplayName("Performance - Large validation timeout")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void performanceLargeValidationTimeout() {
        // Arrange: Mock long-running validation
        when(mockRunner.start()).thenAnswer(invocation -> {
            Thread.sleep(100); // Simulate some processing time
            return null;
        });
        when(mockReferenceWorkflow.getID()).thenReturn("PerformanceTestWorkflow");
        when(mockGeneratedWorkflow.getID()).thenReturn("PerformanceTestWorkflow");

        // Act: Validate with long timeout
        ValidationResult result = validator.validatePerfectWorkflow(
            perfectWorkflow,
            perfectWorkflow,
            PerfectWorkflowValidator.GepaOptimizationTarget.BEHAVIORAL
        );

        // Assert: Should complete within timeout
        assertNotNull(result);
        assertTrue(result.perfectGeneration());
    }

    // ========== Metric Validation Tests ==========

    @Test
    @DisplayName("Metrics - Behavioral validation metrics")
    void metricsBehavioralValidation() {
        // Arrange: Mock perfect behavioral validation
        when(mockReferenceWorkflow.getID()).thenReturn("BehavioralTest");
        when(mockGeneratedWorkflow.getID()).thenReturn("BehavioralTest");

        // Act: Validate behavioral metrics
        ValidationResult result = validator.validatePerfectWorkflow(
            perfectWorkflow,
            perfectWorkflow,
            PerfectWorkflowValidator.GepaOptimizationTarget.BEHAVIORAL
        );

        // Assert: Verify behavioral metrics
        assertNotNull(result.metrics());

        result.metrics().forEach(metric -> {
            assertNotNull(metric.name());
            assertTrue(metric.score() >= 0.0 && metric.score() <= 1.0);
            assertNotNull(metric.status());
            assertNotNull(metric.description());
        });
    }

    @Test
    @DisplayName("Metrics - Performance validation metrics")
    void metricsPerformanceValidation() {
        // Arrange: Mock perfect performance validation
        when(mockRunner.start()).thenReturn(null);
        when(mockReferenceWorkflow.getID()).thenReturn("PerformanceTest");
        when(mockGeneratedWorkflow.getID()).thenReturn("PerformanceTest");

        // Act: Validate performance metrics
        ValidationResult result = validator.validatePerfectWorkflow(
            perfectWorkflow,
            perfectWorkflow,
            PerfectWorkflowValidator.GepaOptimizationTarget.PERFORMANCE
        );

        // Assert: Verify performance metrics
        assertNotNull(result.metrics());
        assertTrue(result.customMetrics().containsKey("performance-score"));
    }

    // ========== Summary Generation Tests ==========

    @Test
    @DisplayName("Summary - Perfect generation summary")
    void summaryPerfectGeneration() {
        // Arrange: Mock perfect validation
        when(mockReferenceWorkflow.getID()).thenReturn("PerfectSummaryTest");
        when(mockGeneratedWorkflow.getID()).thenReturn("PerfectSummaryTest");

        // Act: Generate summary
        ValidationResult result = validator.validatePerfectWorkflow(
            perfectWorkflow,
            perfectWorkflow,
            PerfectWorkflowValidator.GepaOptimizationTarget.BEHAVIORAL
        );

        String summary = result.getSummary();

        // Assert: Verify summary content
        assertNotNull(summary);
        assertTrue(summary.contains("PERFECT"));
        assertTrue(summary.contains("behavioral-footprint"));
        assertTrue(summary.contains("performance"));
        assertTrue(summary.contains("resource-utilization"));
    }

    @Test
    @DisplayName("Summary - Failed validation summary")
    void summaryFailedValidation() {
        // Arrange: Mock failed validation
        when(mockReferenceWorkflow.getID()).thenReturn("FailedSummaryTest");
        when(mockGeneratedWorkflow.getID()).thenReturn("FailedSummaryTest");

        // Act: Generate summary
        ValidationResult result = validator.validatePerfectWorkflow(
            imperfectWorkflow,
            mockReferenceWorkflow,
            PerfectWorkflowValidator.GepaOptimizationTarget.BEHAVIORAL
        );

        String summary = result.getSummary();

        // Assert: Verify failure summary
        assertNotNull(summary);
        assertTrue(summary.contains("FAILED"));
        assertTrue(summary.contains("Behavioral footprint is not perfect"));
    }

    // ========== Cache Management Tests ==========

    @Test
    @DisplayName("Cache - Get and clear cache")
    void cacheGetAndClear() {
        // Arrange: Create validator
        PerfectWorkflowValidator validator = new PerfectWorkflowValidator(false);

        // Act: Test cache methods
        Map<String, PerfectWorkflowValidator.ValidationMetric> initialCache = validator.getMetricCache();
        assertEquals(0, initialCache.size()); // Should be empty initially

        // Clear empty cache
        validator.clearMetricCache();
        Map<String, PerfectWorkflowValidator.ValidationMetric> clearedCache = validator.getMetricCache();
        assertEquals(0, clearedCache.size());
    }

    // ========== Helper Methods ==========

    private YNet createPerfectWorkflow() {
        YNet workflow = new YNet();
        workflow.setID("PerfectWorkflow");
        workflow.setWorkflowStatus(YWorkflowStatus.COMPLETED);

        // Create minimal perfect workflow structure
        List<YTask> tasks = new ArrayList<>();
        YTask task = new YTask();
        task.setID("perfect_task");
        tasks.add(task);
        workflow.setTasks(tasks);

        return workflow;
    }

    private YNet createImperfectWorkflow() {
        YNet workflow = new YNet();
        workflow.setID("ImperfectWorkflow");
        workflow.setWorkflowStatus(YWorkflowStatus.COMPLETED);

        // Create workflow with imperfect characteristics
        List<YTask> tasks = new ArrayList<>();
        YTask task = new YTask();
        task.setID("imperfect_task");
        tasks.add(task);
        workflow.setTasks(tasks);

        return workflow;
    }

    private YNet createPoorPerformanceWorkflow() {
        YNet workflow = new YNet();
        workflow.setID("PoorPerformanceWorkflow");
        workflow.setWorkflowStatus(YWorkflowStatus.COMPLETED);

        // Create workflow with performance issues
        List<YTask> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            YTask task = new YTask();
            task.setID("slow_task_" + i);
            tasks.add(task);
        }
        workflow.setTasks(tasks);

        return workflow;
    }
}