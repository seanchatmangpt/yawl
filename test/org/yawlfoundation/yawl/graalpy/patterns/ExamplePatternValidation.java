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

import org.yawlfoundation.yawl.elements.YAWLModel;
import org.yawlfoundation.yawl.graalpy.patterns.PatternValidator.ValidationConfiguration;
import org.yawlfoundation.yawl.graalpy.validation.ValidationResult;

/**
 * Example demonstrating PatternValidator usage with different pattern types
 */
public class ExamplePatternValidation {
    
    public static void main(String[] args) {
        System.out.println("=== PatternValidator Example ===\n");
        
        // Example 1: Basic validation
        demonstrateBasicValidation();
        
        // Example 2: Different validation modes
        demonstrateValidationModes();
        
        // Example 3: Detailed report generation
        demonstrateDetailedReport();
    }
    
    /**
     * Demonstrates basic pattern validation
     */
    public static void demonstrateBasicValidation() {
        System.out.println("1. Basic Pattern Validation\n");
        
        // Create a YAWL model (simplified)
        YAWLModel model = createSimpleModel();
        
        // Create validation configuration
        ValidationConfiguration config = new ValidationConfiguration();
        config.setMode(ValidationConfiguration.Mode.STRICT);
        config.setTimeoutMillis(5000);
        
        // Create validator
        PatternValidator validator = new PatternValidator(model, config);
        
        // Run validation
        ValidationResult result = validator.validatePattern();
        
        // Display results
        System.out.println("Validation Status: " + (result.isPassed() ? "PASS" : "FAIL"));
        System.out.println("Errors: " + result.getErrors().size());
        System.out.println("Warnings: " + result.getWarnings().size());
        
        if (result.hasMetrics()) {
            System.out.println("Metrics:");
            result.getMetrics().forEach((name, value) -> {
                System.out.println("  " + name + ": " + value);
            });
        }
        
        System.out.println();
    }
    
    /**
     * Demonstrates different validation modes
     */
    public static void demonstrateValidationModes() {
        System.out.println("2. Validation Modes Comparison\n");
        
        ValidationConfiguration[] configs = {
            createStrictConfig(),
            createPermissiveConfig(),
            createReportOnlyConfig()
        };
        
        String[] modeNames = {"STRICT", "PERMISSIVE", "REPORT_ONLY"};
        
        for (int i = 0; i < configs.length; i++) {
            System.out.println("Mode: " + modeNames[i]);
            
            YAWLModel model = createSimpleModel();
            PatternValidator validator = new PatternValidator(model, configs[i]);
            ValidationResult result = validator.validatePattern();
            
            System.out.println("  Status: " + (result.isPassed() ? "PASS" : "FAIL"));
            System.out.println("  Errors: " + result.getErrors().size());
            System.out.println("  Warnings: " + result.getWarnings().size());
            System.out.println();
        }
    }
    
    /**
     * Demonstrates detailed report generation
     */
    public static void demonstrateDetailedReport() {
        System.out.println("3. Detailed Validation Report\n");
        
        YAWLModel model = createComplexModel();
        ValidationConfiguration config = new ValidationConfiguration();
        config.setEnablePerformanceBenchmark(true);
        config.setEnableDeadlockDetection(true);
        config.setEnableLivelockDetection(true);
        
        PatternValidator validator = new PatternValidator(model, config);
        ValidationResult result = validator.validatePattern();
        
        // Generate and display report
        String report = validator.generateValidationReport();
        System.out.println(report);
    }
    
    // Helper methods
    
    private static YAWLModel createSimpleModel() {
        // In a real implementation, this would create a proper YAWL model
        // For demonstration, we create a simple model
        return new YAWLModel();
    }
    
    private static YAWLModel createComplexModel() {
        // Create a more complex model for detailed validation
        return new YAWLModel();
    }
    
    private static ValidationConfiguration createStrictConfig() {
        ValidationConfiguration config = new ValidationConfiguration();
        config.setMode(ValidationConfiguration.Mode.STRICT);
        return config;
    }
    
    private static ValidationConfiguration createPermissiveConfig() {
        ValidationConfiguration config = new ValidationConfiguration();
        config.setMode(ValidationConfiguration.Mode.PERMISSIVE);
        return config;
    }
    
    private static ValidationConfiguration createReportOnlyConfig() {
        ValidationConfiguration config = new ValidationConfiguration();
        config.setMode(ValidationConfiguration.Mode.REPORT_ONLY);
        return config;
    }
}
