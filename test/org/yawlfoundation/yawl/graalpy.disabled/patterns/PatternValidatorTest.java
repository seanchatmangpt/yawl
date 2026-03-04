/*
 * Copyright (c) 2026 YAWL Foundation
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.yawlfoundation.yawl.graalpy.patterns;

import org.junit.jupiter.api.*;
import org.yawlfoundation.yawl.elements.YAWLModel;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.YAWLServiceInterfaceRegistry;
import org.yawlfoundation.yawl.graalpy.patterns.PatternValidator.ValidationConfiguration;
import org.yawlfoundation.yawl.graalpy.validation.ValidationResult;
import org.yawlfoundation.yawl.integration.wizard.patterns.WorkflowPattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for PatternValidator
 * Tests the comprehensive validation capabilities for YAWL workflow patterns
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PatternValidatorTest {
    
    private YAWLModel simpleSequenceModel;
    private YAWLModel parallelSplitModel;
    private YAWLModel exclusiveChoiceModel;
    private YAWLModel problematicModel;
    
    private PatternValidator validator;
    private ValidationConfiguration config;
    
    @BeforeAll
    void setUpModels() throws Exception {
        // Initialize service registry
        YAWLServiceInterfaceRegistry registry = new YAWLServiceInterfaceRegistry();
        
        // Create simple sequence model (WP-1)
        simpleSequenceModel = createSequenceModel();
        
        // Create parallel split model (WP-2)
        parallelSplitModel = createParallelSplitModel();
        
        // Create exclusive choice model (WP-4)
        exclusiveChoiceModel = createExclusiveChoiceModel();
        
        // Create problematic model (for testing error cases)
        problematicModel = createProblematicModel();
    }
    
    @BeforeEach
    void setUp() {
        // Create configuration
        config = new ValidationConfiguration();
        config.setMode(ValidationConfiguration.Mode.STRICT);
        config.setTimeoutMillis(10000); // 10 seconds
        config.setMaxStateSpaceSize(5000);
        config.setEnablePerformanceBenchmark(true);
        config.setEnableDeadlockDetection(true);
        config.setEnableLivelockDetection(true);
    }
    
    @Test
    @DisplayName("Validate Simple Sequence Pattern")
    void testSimpleSequencePattern() {
        validator = new PatternValidator(simpleSequenceModel, config);
        
        ValidationResult result = validator.validatePattern();
        
        assertTrue(result.isPassed(), "Simple sequence pattern should validate successfully");
        assertEquals(0, result.getErrors().size(), "Should have no errors");
        assertEquals(0, result.getWarnings().size(), "Should have no warnings");
        
        // Check specific metrics
        assertTrue(result.hasMetrics(), "Should have metrics");
        assertTrue(result.getMetrics().getMetric("soundness_passed") == 1, "Soundness should pass");
        assertTrue(result.getMetrics().getMetric("performance_passed") == 1, "Performance should pass");
        assertTrue(result.getMetrics().getMetric("error_handling_passed") == 1, "Error handling should pass");
        assertTrue(result.getMetrics().getMetric("termination_passed") == 1, "Termination should pass");
    }
    
    @Test
    @DisplayName("Validate Parallel Split Pattern")
    void testParallelSplitPattern() {
        validator = new PatternValidator(parallelSplitModel, config);
        
        ValidationResult result = validator.validatePattern();
        
        assertTrue(result.isPassed(), "Parallel split pattern should validate successfully");
        
        // Check that performance metrics are reasonable
        assertTrue(result.hasMetrics(), "Should have performance metrics");
        long executionTime = result.getMetrics().getMetric("execution_time_ms");
        assertTrue(executionTime < 1000, "Execution time should be reasonable");
    }
    
    @Test
    @DisplayName("Validate Exclusive Choice Pattern")
    void testExclusiveChoicePattern() {
        validator = new PatternValidator(exclusiveChoiceModel, config);
        
        ValidationResult result = validator.validatePattern();
        
        assertTrue(result.isPassed(), "Exclusive choice pattern should validate successfully");
        
        // Check that the pattern was correctly identified
        assertNotNull(validator.getIdentifiedPattern());
        assertEquals(WorkflowPattern.EXCLUSIVE_CHOICE, validator.getIdentifiedPattern());
    }
    
    @Test
    @DisplayName("Validate Problematic Pattern - Should Fail")
    void testProblematicPattern() {
        validator = new PatternValidator(problematicModel, config);
        
        ValidationResult result = validator.validatePattern();
        
        assertFalse(result.isPassed(), "Problematic pattern should fail validation");
        assertTrue(result.getErrors().size() > 0, "Should have validation errors");
    }
    
    @Test
    @DisplayName("Test Permissive Mode")
    void testPermissiveMode() {
        config.setMode(ValidationConfiguration.Mode.PERMISSIVE);
        validator = new PatternValidator(problematicModel, config);
        
        ValidationResult result = validator.validatePattern();
        
        // In permissive mode, some violations might be allowed
        // The exact behavior depends on the specific problematic model
    }
    
    @Test
    @DisplayName("Test Report Only Mode")
    void testReportOnlyMode() {
        config.setMode(ValidationConfiguration.Mode.REPORT_ONLY);
        validator = new PatternValidator(problematicModel, config);
        
        ValidationResult result = validator.validatePattern();
        
        // In report-only mode, validation should always pass
        assertTrue(result.isPassed(), "Report-only mode should not fail");
        
        // Should still have the validation details
        assertNotNull(result.getValidationResults());
        assertFalse(result.getValidationResults().isEmpty());
    }
    
    @Test
    @DisplayName("Generate Validation Report")
    void testGenerateValidationReport() {
        validator = new PatternValidator(simpleSequenceModel, config);
        
        ValidationResult result = validator.validatePattern();
        String report = validator.generateValidationReport();
        
        assertNotNull(report, "Report should not be null");
        assertTrue(report.contains("YAWL Pattern Validation Report"), "Should contain header");
        assertTrue(report.contains("Overall Status: PASS"), "Should show success status");
        assertTrue(report.contains("Pattern: Sequence"), "Should identify the pattern");
    }
    
    @Test
    @DisplayName("Test Pattern Categorization")
    void testPatternCategorization() {
        validator = new PatternValidator(simpleSequenceModel, config);
        
        PatternValidator.PatternCategory category = validator.categorizePattern();
        
        assertEquals(PatternValidator.PatternCategory.BASIC, category, 
                    "Simple sequence should be categorized as BASIC");
    }
    
    @Test
    @DisplayName("Test Performance Benchmarking")
    void testPerformanceBenchmarking() {
        config.setEnablePerformanceBenchmark(true);
        validator = new PatternValidator(simpleSequenceModel, config);
        
        ValidationResult result = validator.validatePattern();
        
        assertTrue(result.hasMetrics(), "Should have performance metrics");
        assertTrue(result.getMetrics().getMetric("execution_time_ms") > 0, "Should measure execution time");
        assertTrue(result.getMetrics().getMetric("memory_usage_kb") > 0, "Should measure memory usage");
    }
    
    @Test
    @DisplayName("Test Soundness Validation")
    void testSoundnessValidation() {
        validator = new PatternValidator(simpleSequenceModel, config);
        
        ValidationResult soundnessResult = validator.validateSoundness();
        
        assertTrue(soundnessResult.isPassed(), "Soundness should pass for valid pattern");
        assertEquals("Soundness Validation", soundnessResult.getName());
        assertTrue(soundnessResult.getMetrics().containsKey("deadlock_free"));
        assertTrue(soundnessResult.getMetrics().containsKey("terminating"));
    }
    
    @Test
    @DisplayName("Test Error Handling Validation")
    void testErrorHandlingValidation() {
        validator = new PatternValidator(simpleSequenceModel, config);
        
        ValidationResult errorResult = validator.validateErrorHandling();
        
        assertTrue(errorResult.isPassed(), "Error handling should be adequate");
        assertEquals("Error Handling Validation", errorResult.getName());
        assertTrue(errorResult.getMetrics().containsKey("proper_exception_handling"));
    }
    
    @Test
    @DisplayName("Test Termination Validation")
    void testTerminationValidation() {
        validator = new PatternValidator(simpleSequenceModel, config);
        
        ValidationResult terminationResult = validator.validateTermination();
        
        assertTrue(terminationResult.isPassed(), "Termination should be guaranteed");
        assertEquals("Termination Validation", terminationResult.getName());
        assertTrue(terminationResult.getMetrics().containsKey("termination_guaranteed"));
    }
    
    // Helper methods to create test models
    
    private YAWLModel createSequenceModel() {
        // Create a simple sequence pattern: A -> B -> C
        YAWLModel model = new YAWLModel();
        YSpecification spec = new YSpecification();
        YNet net = new YNet();
        
        // Add start place
        YAWLPlace startPlace = new YAWLPlace("start");
        net.addPlace(startPlace);
        
        // Add tasks A, B, C
        YAWLTask taskA = new YAWLTask("A");
        YAWLTask taskB = new YAWLTask("B");
        YAWLTask taskC = new YAWLTask("C");
        net.addTask(taskA);
        net.addTask(taskB);
        net.addTask(taskC);
        
        // Add end place
        YAWLPlace endPlace = new YAWLPlace("end");
        net.addPlace(endPlace);
        
        // Create flow relations
        net.addFlowRelation(new YAWLFlowRelation(startPlace, taskA));
        net.addFlowRelation(new YAWLFlowRelation(taskA, taskB));
        net.addFlowRelation(new YAWLFlowRelation(taskB, taskC));
        net.addFlowRelation(new YAWLFlowRelation(taskC, endPlace));
        
        // Set initial marking
        YAWLMarking initialMarking = new YAWLMarking();
        initialMarking.addPlace(startPlace);
        net.setInitialMarking(initialMarking);
        
        return model;
    }
    
    private YAWLModel createParallelSplitModel() {
        // Create a parallel split pattern: Start -> Split -> {A, B} -> Join -> End
        YAWLModel model = new YAWLModel();
        YSpecification spec = new YSpecification();
        YNet net = new YNet();
        
        // Add start place
        YAWLPlace startPlace = new YAWLPlace("start");
        net.addPlace(startPlace);
        
        // Add split transition
        YAWLTransition split = new YAWLTransition("split");
        net.addTransition(split);
        
        // Add tasks A, B
        YAWLTask taskA = new YAWLTask("A");
        YAWLTask taskB = new YAWLTask("B");
        net.addTask(taskA);
        net.addTask(taskB);
        
        // Add join transition
        YAWLTransition join = new YAWLTransition("join");
        net.addTransition(join);
        
        // Add end place
        YAWLPlace endPlace = new YAWLPlace("end");
        net.addPlace(endPlace);
        
        // Create flow relations
        net.addFlowRelation(new YAWLFlowRelation(startPlace, split));
        net.addFlowRelation(new YAWLFlowRelation(split, taskA));
        net.addFlowRelation(new YAWLFlowRelation(split, taskB));
        net.addFlowRelation(new YAWLFlowRelation(taskA, join));
        net.addFlowRelation(new YAWLFlowRelation(taskB, join));
        net.addFlowRelation(new YAWLFlowRelation(join, endPlace));
        
        // Set initial marking
        YAWLMarking initialMarking = new YAWLMarking();
        initialMarking.addPlace(startPlace);
        net.setInitialMarking(initialMarking);
        
        return model;
    }
    
    private YAWLModel createExclusiveChoiceModel() {
        // Create an exclusive choice pattern: Start -> Choice -> {A or B} -> End
        YAWLModel model = new YAWLModel();
        YSpecification spec = new YSpecification();
        YNet net = new YNet();
        
        // Add start place
        YAWLPlace startPlace = new YAWLPlace("start");
        net.addPlace(startPlace);
        
        // Add choice transition
        YAWLTransition choice = new YAWLTransition("choice");
        net.addTransition(choice);
        
        // Add tasks A, B
        YAWLTask taskA = new YAWLTask("A");
        YAWLTask taskB = new YAWLTask("B");
        net.addTask(taskA);
        net.addTask(taskB);
        
        // Add merge transition
        YAWLTransition merge = new YAWLTransition("merge");
        net.addTransition(merge);
        
        // Add end place
        YAWLPlace endPlace = new YAWLPlace("end");
        net.addPlace(endPlace);
        
        // Create flow relations
        net.addFlowRelation(new YAWLFlowRelation(startPlace, choice));
        net.addFlowRelation(new YAWLFlowRelation(choice, taskA));
        net.addFlowRelation(new YAWLFlowRelation(choice, taskB));
        net.addFlowRelation(new YAWLFlowRelation(taskA, merge));
        net.addFlowRelation(new YAWLFlowRelation(taskB, merge));
        net.addFlowRelation(new YAWLFlowRelation(merge, endPlace));
        
        // Set initial marking
        YAWLMarking initialMarking = new YAWLMarking();
        initialMarking.addPlace(startPlace);
        net.setInitialMarking(initialMarking);
        
        return model;
    }
    
    private YAWLModel createProblematicModel() {
        // Create a problematic model that will fail validation
        YAWLModel model = new YAWLModel();
        YSpecification spec = new YSpecification();
        YNet net = new YNet();
        
        // Create a cycle that could cause a deadlock
        YAWLPlace place1 = new YAWLPlace("place1");
        YAWLPlace place2 = new YAWLPlace("place2");
        YAWLTask task1 = new YAWLTask("task1");
        YAWLTask task2 = new YAWLTask("task2");
        
        net.addPlace(place1);
        net.addPlace(place2);
        net.addTask(task1);
        net.addTask(task2);
        
        // Create a cycle
        net.addFlowRelation(new YAWLFlowRelation(place1, task1));
        net.addFlowRelation(new YAWLFlowRelation(task1, place2));
        net.addFlowRelation(new YAWLFlowRelation(place2, task2));
        net.addFlowRelation(new YAWLFlowRelation(task2, place1)); // Cycle
        
        // No exit condition - this should cause termination issues
        YAWLMarking initialMarking = new YAWLMarking();
        initialMarking.addPlace(place1);
        net.setInitialMarking(initialMarking);
        
        return model;
    }
}
