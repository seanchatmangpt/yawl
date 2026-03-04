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

import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.elements.YAWLModel;
import org.yawlfoundation.yawl.graalpy.patterns.PatternValidator.ValidationConfiguration;
import org.yawlfoundation.yawl.graalpy.validation.ValidationResult;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic tests for PatternValidator
 */
class BasicPatternValidationTest {
    
    @Test
    void testPatternValidatorCreation() {
        // Test that we can create a PatternValidator instance
        ValidationConfiguration config = new ValidationConfiguration();
        YAWLModel model = new YAWLModel(); // Simplified model
        
        assertDoesNotThrow(() -> {
            PatternValidator validator = new PatternValidator(model, config);
            assertNotNull(validator);
        });
    }
    
    @Test
    void testValidationConfiguration() {
        ValidationConfiguration config = new ValidationConfiguration();
        
        // Test default values
        assertEquals(ValidationConfiguration.Mode.STRICT, config.getMode());
        assertEquals(30000L, config.getTimeoutMillis());
        assertEquals(10000, config.getMaxStateSpaceSize());
        assertTrue(config.isEnablePerformanceBenchmark());
        assertTrue(config.isEnableDeadlockDetection());
        assertTrue(config.isEnableLivelockDetection());
        
        // Test configuration changes
        config.setMode(ValidationConfiguration.Mode.PERMISSIVE);
        assertEquals(ValidationConfiguration.Mode.PERMISSIVE, config.getMode());
        
        config.setTimeoutMillis(60000L);
        assertEquals(60000L, config.getTimeoutMillis());
        
        config.setMaxStateSpaceSize(20000);
        assertEquals(20000, config.getMaxStateSpaceSize());
        
        config.setEnablePerformanceBenchmark(false);
        assertFalse(config.isEnablePerformanceBenchmark());
        
        config.setEnableDeadlockDetection(false);
        assertFalse(config.isEnableDeadlockDetection());
        
        config.setEnableLivelockDetection(false);
        assertFalse(config.isEnableLivelockDetection());
    }
    
    @Test
    void testValidationMetrics() {
        ValidationConfiguration config = new ValidationConfiguration();
        YAWLModel model = new YAWLModel();
        PatternValidator validator = new PatternValidator(model, config);
        
        // Test that we can get validation results
        ValidationResult result = validator.validatePattern();
        
        assertNotNull(result);
        assertNotNull(result.getErrors());
        assertNotNull(result.getWarnings());
        assertNotNull(result.getMetrics());
        
        // Test metrics manipulation
        result.addMetric("test_metric", 42L, "units");
        assertEquals(42L, result.getMetric("test_metric"));
        
        result.addError("Test error");
        assertEquals(1, result.getErrors().size());
        
        result.addWarning("Test warning");
        assertEquals(1, result.getWarnings().size());
    }
    
    @Test
    void testPatternCategorization() {
        ValidationConfiguration config = new ValidationConfiguration();
        YAWLModel model = new YAWLModel();
        PatternValidator validator = new PatternValidator(model, config);
        
        // Test pattern categorization
        PatternValidator.PatternCategory category = validator.categorizePattern();
        
        assertNotNull(category);
        // Category should be one of the predefined values
        assertTrue(
            category == PatternValidator.PatternCategory.BASIC ||
            category == PatternValidator.PatternCategory.ADVANCED ||
            category == PatternValidator.PatternCategory.CANCEL ||
            category == PatternValidator.PatternCategory.MILESTONE ||
            category == PatternValidator.PatternCategory.ITERATION ||
            category == PatternValidator.PatternCategory.DEPENDENCY ||
            category == PatternValidator.PatternCategory.INTERLEAVED
        );
    }
    
    @Test
    void testValidationModes() {
        ValidationConfiguration[] configs = {
            createStrictConfig(),
            createPermissiveConfig(),
            createReportOnlyConfig()
        };
        
        for (ValidationConfiguration config : configs) {
            YAWLModel model = new YAWLModel();
            PatternValidator validator = new PatternValidator(model, config);
            
            ValidationResult result = validator.validatePattern();
            
            // All modes should produce valid results
            assertNotNull(result);
            assertNotNull(result.getErrors());
            assertNotNull(result.getWarnings());
            assertNotNull(result.getMetrics());
        }
    }
    
    private ValidationConfiguration createStrictConfig() {
        ValidationConfiguration config = new ValidationConfiguration();
        config.setMode(ValidationConfiguration.Mode.STRICT);
        return config;
    }
    
    private ValidationConfiguration createPermissiveConfig() {
        ValidationConfiguration config = new ValidationConfiguration();
        config.setMode(ValidationConfiguration.Mode.PERMISSIVE);
        return config;
    }
    
    private ValidationConfiguration createReportOnlyConfig() {
        ValidationConfiguration config = new ValidationConfiguration();
        config.setMode(ValidationConfiguration.Mode.REPORT_ONLY);
        return config;
    }
}
