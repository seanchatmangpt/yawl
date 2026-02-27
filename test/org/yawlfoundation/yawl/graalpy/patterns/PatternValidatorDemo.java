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
import org.yawlfoundation.yawl.integration.wizard.patterns.WorkflowPattern;

/**
 * Demo class showing how to use the PatternValidator
 */
public class PatternValidatorDemo {
    
    public static void main(String[] args) {
        System.out.println("=== YAWL Pattern Validator Demo ===\n");
        
        try {
            // Create a validation configuration
            ValidationConfiguration config = new ValidationConfiguration();
            config.setMode(ValidationConfiguration.Mode.STRICT);
            config.setTimeoutMillis(30000); // 30 seconds
            config.setMaxStateSpaceSize(10000);
            config.setEnablePerformanceBenchmark(true);
            
            // Create a simple sequence pattern model
            YAWLModel model = createSimpleSequenceModel();
            
            // Create the validator
            PatternValidator validator = new PatternValidator(model, config);
            
            // Validate the pattern
            System.out.println("Validating pattern...");
            ValidationResult result = validator.validatePattern();
            
            // Display results
            System.out.println("\n=== Validation Results ===");
            System.out.println("Overall Status: " + (result.isPassed() ? "PASS" : "FAIL"));
            System.out.println("Errors: " + result.getErrors().size());
            System.out.println("Warnings: " + result.getWarnings().size());
            
            if (result.hasMetrics()) {
                System.out.println("\nMetrics:");
                result.getMetrics().forEach((name, value) -> {
                    System.out.println("  " + name + ": " + value);
                });
            }
            
            // Generate detailed report
            System.out.println("\n=== Detailed Report ===");
            String report = validator.generateValidationReport();
            System.out.println(report);
            
            // Show identified pattern information
            WorkflowPattern identified = validator.getIdentifiedPattern();
            if (identified != null) {
                System.out.println("\n=== Identified Pattern ===");
                System.out.println("Pattern: " + identified.getLabel());
                System.out.println("Category: " + identified.getCategory());
                System.out.println("Description: " + identified.getDescription());
            }
            
        } catch (Exception e) {
            System.err.println("Error during validation: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Creates a simple sequence pattern model for demonstration
     */
    private static YAWLModel createSimpleSequenceModel() {
        // This is a simplified model creation
        // In practice, you would load from a YAWL file or create a proper model structure
        return new YAWLModel();
    }
    
    /**
     * Demonstrates different validation modes
     */
    public static void demonstrateValidationModes() {
        System.out.println("\n=== Validation Mode Comparison ===");
        
        ValidationConfiguration[] configs = {
            new ValidationConfiguration(), // Default STRICT
            createPermissiveConfig(),
            createReportOnlyConfig()
        };
        
        String[] modeNames = {"STRICT", "PERMISSIVE", "REPORT_ONLY"};
        
        for (int i = 0; i < configs.length; i++) {
            System.out.println("\n" + modeNames[i] + " Mode:");
            System.out.println("  Mode: " + configs[i].getMode());
            System.out.println("  Timeout: " + configs[i].getTimeoutMillis() + "ms");
            System.out.println("  Max State Space: " + configs[i].getMaxStateSpaceSize());
        }
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
