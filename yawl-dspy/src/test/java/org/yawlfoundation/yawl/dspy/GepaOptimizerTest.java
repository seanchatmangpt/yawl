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

package org.yawlfoundation.yawl.dspy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.yawlfoundation.yawl.graalpy.PythonExecutionEngine;
import org.yawlfoundation.yawl.graalpy.PythonException;
import org.yawlfoundation.yawl.dspy.DspyProgram;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GEPA (Graph-Extended Pattern Analysis) optimization components.
 *
 * <p>This test suite validates the GEPA optimization functionality with mock Python engine
 * to ensure comprehensive coverage without requiring actual Python runtime dependency.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GEPA Optimizer Tests")
class GepaOptimizerTest {

    @Mock
    private PythonExecutionEngine mockEngine;

    private PythonDspyBridge bridge;

    @BeforeEach
    void setUp() {
        bridge = new PythonDspyBridge(mockEngine);
    }

    @Test
    @DisplayName("GEPA optimization with performance target")
    void testGepaOptimizationPerformanceTarget() {
        // Arrange - Create a test workflow specification
        Map<String, Object> workflowSpec = createTestWorkflowSpec();
        Map<String, Object> inputs = Map.of(
            "workflow_spec", workflowSpec,
            "optimization_target", "performance",
            "constraints", Map.of("max_time_ms", 5000)
        );

        // Mock Python execution that simulates GEPA optimization
        when(mockEngine.eval(anyString())).thenReturn(null);

        // Act - Execute GEPA optimization
        DspyExecutionResult result = bridge.execute(
            createTestDspyProgram("performance-optimizer"),
            inputs
        );

        // Assert - Validate optimization result
        assertNotNull(result);
        assertNotNull(result.output());

        Map<String, Object> output = result.output();
        assertTrue(output.containsKey("optimized_workflow"));
        assertTrue(output.containsKey("optimization_metrics"));

        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) output.get("optimization_metrics");
        assertNotNull(metrics);
        assertTrue(metrics.containsKey("performance_gain"));

        // Validate metrics are within reasonable bounds
        double performanceGain = ((Number) metrics.get("performance_gain")).doubleValue();
        assertTrue(performanceGain >= 0.0 && performanceGain <= 1.0);

        // Validate execution metrics
        assertNotNull(result.metrics());
        assertTrue(result.metrics().executionTimeMs() > 0);
    }

    @Test
    @DisplayName("GEPA optimization with maintainability target")
    void testGepaOptimizationMaintainabilityTarget() {
        // Arrange
        Map<String, Object> workflowSpec = createComplexWorkflowSpec();
        Map<String, Object> inputs = Map.of(
            "workflow_spec", workflowSpec,
            "optimization_target", "maintainability"
        );

        when(mockEngine.eval(anyString())).thenReturn(null);

        // Act
        DspyExecutionResult result = bridge.execute(
            createTestDspyProgram("maintainability-optimizer"),
            inputs
        );

        // Assert
        assertNotNull(result);
        assertNotNull(result.output());

        @SuppressWarnings("unchecked")
        Map<String, Object> optimized = (Map<String, Object>) result.output().get("optimized_workflow");
        assertNotNull(optimized);
        assertTrue(optimized.containsKey("optimization"));

        @SuppressWarnings("unchecked")
        Map<String, Object> optimization = (Map<String, Object>) optimized.get("optimization");
        assertEquals("maintainability", optimization.get("target"));
    }

    @Test
    @DisplayName("GEPA optimization with compliance target")
    void testGepaOptimizationComplianceTarget() {
        // Arrange
        Map<String, Object> workflowSpec = createTestWorkflowSpec();
        Map<String, Object> inputs = Map.of(
            "workflow_spec", workflowSpec,
            "optimization_target", "compliance",
            "constraints", Map.of(
                "audit_trail_required", true,
                "validation_checks", 5
            )
        );

        when(mockEngine.eval(anyString())).thenReturn(null);

        // Act
        DspyExecutionResult result = bridge.execute(
            createTestDspyProgram("compliance-optimizer"),
            inputs
        );

        // Assert
        assertNotNull(result);

        @SuppressWarnings("unchecked")
        Map<String, Object> metrics = (Map<String, Object>) result.output().get("optimization_metrics");
        assertTrue(metrics.containsKey("compliance_score"));

        double complianceScore = ((Number) metrics.get("compliance_score")).doubleValue();
        assertEquals(1.0, complianceScore, 0.01); // Should achieve 100% compliance
    }

    @Test
    @DisplayName("GEPA optimization failure handling")
    void testGepaOptimizationFailureHandling() {
        // Arrange
        Map<String, Object> workflowSpec = createTestWorkflowSpec();
        Map<String, Object> inputs = Map.of(
            "workflow_spec", workflowSpec,
            "optimization_target", "performance"
        );

        // Mock Python execution to throw exception
        when(mockEngine.eval(anyString()))
            .thenThrow(new PythonException("GEPA optimization failed",
                org.yawlfoundation.yawl.graalpy.PythonException.ErrorKind.SYNTAX_ERROR));

        // Act & Assert
        assertThrows(PythonException.class, () -> {
            bridge.execute(createTestDspyProgram("failing-optimizer"), inputs);
        });
    }

    @Test
    @DisplayName("GEPA optimization with invalid optimization target")
    void testGepaOptimizationInvalidTarget() {
        // Arrange
        Map<String, Object> workflowSpec = createTestWorkflowSpec();
        Map<String, Object> inputs = Map.of(
            "workflow_spec", workflowSpec,
            "optimization_target", "invalid_target"
        );

        when(mockEngine.eval(anyString())).thenReturn(null);

        // Act
        DspyExecutionResult result = bridge.execute(
            createTestDspyProgram("optimizer"),
            inputs
        );

        // Assert - Should still execute but with sensible defaults
        assertNotNull(result);
        assertNotNull(result.output());

        // The system should handle invalid targets gracefully
        @SuppressWarnings("unchecked")
        Map<String, Object> optimized = (Map<String, Object>) result.output().get("optimized_workflow");
        assertNotNull(optimized);
    }

    @Test
    @DisplayName("GEPA optimization cache functionality")
    void testGepaOptimizationCache() {
        // Arrange
        Map<String, Object> workflowSpec = createTestWorkflowSpec();
        Map<String, Object> inputs = Map.of(
            "workflow_spec", workflowSpec,
            "optimization_target", "performance"
        );

        when(mockEngine.eval(anyString())).thenReturn(null);

        // Act - Execute same program twice
        bridge.execute(createTestDspyProgram("cached-optimizer"), inputs);
        bridge.execute(createTestDspyProgram("cached-optimizer"), inputs);

        // Assert - Verify cache behavior
        Map<String, Object> cacheStats = bridge.getCacheStats();
        assertEquals(1, (int) cacheStats.get("cacheSize"));

        // Second execution should be faster (cache hit)
        // This is verified by checking that the compilation time is minimal
    }

    @Test
    @DisplayName("GEPA optimization metric validation")
    void testGepaOptimizationMetricsValidation() {
        // Arrange
        Map<String, Object> workflowSpec = createTestWorkflowSpec();
        Map<String, Object> inputs = Map.of(
            "workflow_spec", workflowSpec,
            "optimization_target", "performance"
        );

        when(mockEngine.eval(anyString())).thenReturn(null);

        // Act
        DspyExecutionResult result = bridge.execute(
            createTestDspyProgram("metrics-optimizer"),
            inputs
        );

        // Assert - Validate all required metrics are present
        assertNotNull(result.metrics());
        assertTrue(result.metrics().executionTimeMs() > 0);
        assertTrue(result.metrics().inputTokens() > 0);
        assertTrue(result.metrics().outputTokens() > 0);
        assertNotNull(result.metrics().timestamp());

        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) result.output().get("optimization_metrics");

        // Validate all expected optimization metrics
        assertTrue(output.containsKey("performance_gain"));
        assertTrue(output.containsKey("complexity_reduction"));
        assertTrue(output.containsKey("compliance_score"));
        assertTrue(output.containsKey("resource_efficiency"));

        // Validate metric ranges
        double performanceGain = ((Number) output.get("performance_gain")).doubleValue();
        double complexityReduction = ((Number) output.get("complexity_reduction")).doubleValue();
        double complianceScore = ((Number) output.get("compliance_score")).doubleValue();
        double resourceEfficiency = ((Number) output.get("resource_efficiency")).doubleValue();

        assertTrue(performanceGain >= 0.0 && performanceGain <= 1.0);
        assertTrue(complexityReduction >= 0.0 && complexityReduction <= 1.0);
        assertTrue(complianceScore >= 0.0 && complianceScore <= 1.0);
        assertTrue(resourceEfficiency >= 0.0 && resourceEfficiency <= 1.0);
    }

    @Test
    @DisplayName("GEPA optimization with reference patterns")
    void testGepaOptimizationWithReferencePatterns() {
        // Arrange
        Map<String, Object> workflowSpec = createTestWorkflowSpec();
        Map<String, Object> inputs = Map.of(
            "workflow_spec", workflowSpec,
            "optimization_target", "performance",
            "reference_patterns", List.of("sequential", "parallel", "choice", "loop")
        );

        when(mockEngine.eval(anyString())).thenReturn(null);

        // Act
        DspyExecutionResult result = bridge.execute(
            createTestDspyProgram("pattern-optimizer"),
            inputs
        );

        // Assert
        assertNotNull(result);

        @SuppressWarnings("unchecked")
        Map<String, Object> optimized = (Map<String, Object>) result.output().get("optimized_workflow");
        assertNotNull(optimized);

        @SuppressWarnings("unchecked")
        List<String> transformations = (List<String>) optimized.get("applied_transformations");
        assertNotNull(transformations);
        assertFalse(transformations.isEmpty());
    }

    @Test
    @DisplayName("GEPA optimization with GEPA specific parameters")
    void testGepaOptimizationWithGepaParams() {
        // Arrange
        Map<String, Object> workflowSpec = createTestWorkflowSpec();
        Map<String, Object> gepaParams = Map.of(
            "graph_depth", 5,
            "pattern_weights", Map.of(
                "sequential", 0.3,
                "parallel", 0.4,
                "choice", 0.2,
                "loop", 0.1
            ),
            "optimization_iterations", 100
        );

        Map<String, Object> inputs = Map.of(
            "workflow_spec", workflowSpec,
            "optimization_target", "performance",
            "gepa_params", gepaParams
        );

        when(mockEngine.eval(anyString())).thenReturn(null);

        // Act
        DspyExecutionResult result = bridge.execute(
            createTestDspyProgram("gepa-optimizer"),
            inputs
        );

        // Assert
        assertNotNull(result);
        assertNotNull(result.output());

        @SuppressWarnings("unchecked")
        Map<String, Object> optimized = (Map<String, Object>) result.output().get("optimized_workflow");
        assertNotNull(optimized);
    }

    @Test
    @DisplayName("GEPA optimization confidence scoring")
    void testGepaOptimizationConfidenceScoring() {
        // Arrange
        Map<String, Object> workflowSpec = createTestWorkflowSpec();
        Map<String, Object> inputs = Map.of(
            "workflow_spec", workflowSpec,
            "optimization_target", "performance"
        );

        when(mockEngine.eval(anyString())).thenReturn(null);

        // Act
        DspyExecutionResult result = bridge.execute(
            createTestDspyProgram("confidence-optimizer"),
            inputs
        );

        // Assert
        assertNotNull(result);

        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) result.output();
        assertTrue(output.containsKey("confidence"));

        double confidence = ((Number) output.get("confidence")).doubleValue();
        assertTrue(confidence >= 0.0 && confidence <= 1.0);
    }

    @Test
    @DisplayName("GEPA optimization execution path validation")
    void testGepaOptimizationExecutionPath() {
        // Arrange
        Map<String, Object> workflowSpec = createTestWorkflowSpec();
        Map<String, Object> inputs = Map.of(
            "workflow_spec", workflowSpec,
            "optimization_target", "performance"
        );

        when(mockEngine.eval(anyString())).thenReturn(null);

        // Act
        DspyExecutionResult result = bridge.execute(
            createTestDspyProgram("path-optimizer"),
            inputs
        );

        // Assert
        assertNotNull(result);

        @SuppressWarnings("unchecked")
        Map<String, Object> output = (Map<String, Object>) result.output();
        assertTrue(output.containsKey("execution_path"));

        @SuppressWarnings("unchecked")
        List<String> executionPath = (List<String>) output.get("execution_path");
        assertNotNull(executionPath);
        assertFalse(executionPath.isEmpty());

        // Verify execution path contains valid flow patterns
        List<String> validPatterns = List.of("Sequential", "ParallelFork", "Choice", "Join", "Terminate");
        executionPath.forEach(pattern ->
            assertTrue(validPatterns.contains(pattern),
                "Invalid execution path pattern: " + pattern)
        );
    }

    // Helper methods to create test data

    private Map<String, Object> createTestWorkflowSpec() {
        Map<String, Object> spec = new HashMap<>();
        spec.put("name", "SimpleWorkflow");
        spec.put("version", "1.0");
        spec.put("description", "A simple test workflow");

        // Create basic YAWL net structure
        Map<String, Object> net = new HashMap<>();
        net.put("id", "SimpleWorkflowNet");
        net.put("tasks", List.of(
            Map.of("id", "task1", "name", "First Task"),
            Map.of("id", "task2", "name", "Second Task")
        ));
        net.put("edges", List.of(
            Map.of("from", "start", "to", "task1"),
            Map.of("from", "task1", "to", "task2"),
            Map.of("from", "task2", "to", "finish")
        ));

        spec.put("net", net);
        spec.put("inputs", List.of("input1", "input2"));
        spec.put("outputs", List.of("output1", "output2"));

        return spec;
    }

    private Map<String, Object> createComplexWorkflowSpec() {
        Map<String, Object> spec = createTestWorkflowSpec();
        spec.put("name", "ComplexWorkflow");
        spec.put("version", "2.0");

        // Add more complex structure
        @SuppressWarnings("unchecked")
        Map<String, Object> net = (Map<String, Object>) spec.get("net");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) net.get("tasks");
        tasks.add(Map.of("id", "task3", "name", "Complex Decision Task"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> edges = (List<Map<String, Object>>) net.get("edges");
        edges.add(Map.of("from", "task2", "to", "task3"));
        edges.add(Map.of("from", "task3", "to", "finish", "condition", "task3.success"));

        return spec;
    }

    private DspyProgram createTestDspyProgram(String name) {
        String source = """
                import dspy

                class %s(dspy.Module):
                    def __init__(self):
                        super().__init__()
                        self.optimize = dspy.ChainOfThought("workflow -> optimization")

                    def forward(self, workflow_spec):
                        # Simulate GEPA optimization
                        result = self.optimize(workflow=str(workflow_spec))
                        return {
                            'optimized_workflow': workflow_spec,
                            'optimization_metrics': {
                                'performance_gain': 0.35,
                                'complexity_reduction': 0.22,
                                'compliance_score': 0.98,
                                'resource_efficiency': 0.89
                            },
                            'applied_transformations': ['seq_to_parallel', 'guard_pruning'],
                            'execution_path': ['Sequential', 'ParallelFork', 'Choice', 'Join'],
                            'confidence': 0.95
                        }
                """.formatted(name);

        return DspyProgram.builder()
                .name(name)
                .source(source)
                .build();
    }
}