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

package org.yawlfoundation.yawl.compliance.shacl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.yawlfoundation.yawl.compliance.shacl.impl.ShaclValidatorImpl;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.elements.YDecomposition;
import org.yawlfoundation.yawl.engine.YNetRunner;
import org.yawlfoundation.yawl.engine.YSpecificationID;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SHACL validator functionality.
 */
class ShaclValidatorTest {

    private ShaclValidator validator;
    private ShaclShapeRegistry shapeRegistry;
    private YSpecification specification;
    private YNetRunner runner;

    @BeforeEach
    void setUp() {
        // Create validator with mock shape registry
        shapeRegistry = mock(ShaclShapeRegistry.class);
        when(shapeRegistry.hasShapes(any(ComplianceDomain.class))).thenReturn(true);

        validator = new ShaclValidatorImpl(shapeRegistry);

        // Create a mock specification
        specification = mock(YSpecification.class);
        when(specification.getSpecURI()).thenReturn("test-spec-uri");
        when(specification.getVersion()).thenReturn(null);

        YNet net = mock(YNet.class);
        when(specification.getRootNet()).thenReturn(net);
        when(net.getTasks()).thenReturn(List.of());

        // Create a mock runner
        runner = mock(YNetRunner.class);
        YSpecificationID specId = mock(YSpecificationID.class);
        when(specId.toString()).thenReturn("test-spec-id");
        when(runner.getSpecificationID()).thenReturn(specId);
    }

    @Test
    @DisplayName("Validate SOX compliance for specification")
    void testValidateSOXCompliance() {
        // Test SOX validation
        ShaclValidationResult result = validator.validate(specification, ComplianceDomain.SOX);

        assertNotNull(result, "Result should not be null");
        assertEquals(ComplianceDomain.SOX, result.complianceDomain());
        assertEquals("test-spec-uri", result.target());
        assertTrue(result.timestamp().isBefore(Instant.now()));

        // Performance assertion (<100ms)
        assertTrue(result.validationTime() < 100,
            "Validation should complete in less than 100ms, but took: " + result.validationTime() + "ms");
    }

    @Test
    @DisplayName("Validate GDPR compliance for specification")
    void testValidateGDPRCompliance() {
        // Test GDPR validation
        ShaclValidationResult result = validator.validate(specification, ComplianceDomain.GDPR);

        assertNotNull(result, "Result should not be null");
        assertEquals(ComplianceDomain.GDPR, result.complianceDomain());
        assertEquals("test-spec-uri", result.target());

        // Performance assertion (<100ms)
        assertTrue(result.validationTime() < 100,
            "Validation should complete in less than 100ms, but took: " + result.validationTime() + "ms");
    }

    @Test
    @DisplayName("Validate HIPAA compliance for specification")
    void testValidateHIPAACompliance() {
        // Test HIPAA validation
        ShaclValidationResult result = validator.validate(specification, ComplianceDomain.HIPAA);

        assertNotNull(result, "Result should not be null");
        assertEquals(ComplianceDomain.HIPAA, result.complianceDomain());
        assertEquals("test-spec-uri", result.target());

        // Performance assertion (<100ms)
        assertTrue(result.validationTime() < 100,
            "Validation should complete in less than 100ms, but took: " + result.validationTime() + "ms");
    }

    @Test
    @DisplayName("Validate SOX compliance for engine")
    void testValidateSOXEngineCompliance() {
        // Test SOX engine validation
        ShaclValidationResult result = validator.validate(runner, ComplianceDomain.SOX);

        assertNotNull(result, "Result should not be null");
        assertEquals(ComplianceDomain.SOX, result.complianceDomain());
        assertEquals("test-spec-id", result.target());

        // Performance assertion (<100ms)
        assertTrue(result.validationTime() < 100,
            "Validation should complete in less than 100ms, but took: " + result.validationTime() + "ms");
    }

    @Test
    @DisplayName("Validate HIPAA compliance for engine")
    void testValidateHIPAAEngineCompliance() {
        // Test HIPAA engine validation
        ShaclValidationResult result = validator.validate(runner, ComplianceDomain.HIPAA);

        assertNotNull(result, "Result should not be null");
        assertEquals(ComplianceDomain.HIPAA, result.complianceDomain());
        assertEquals("test-spec-id", result.target());

        // Performance assertion (<100ms)
        assertTrue(result.validationTime() < 100,
            "Validation should complete in less than 100ms, but took: " + result.validationTime() + "ms");
    }

    @Test
    @DisplayName("Validate multiple domains for specification")
    void testValidateMultipleDomains() {
        // Test validation against multiple domains
        List<ComplianceDomain> domains = List.of(ComplianceDomain.SOX, ComplianceDomain.GDPR);
        List<ShaclValidationResult> results = validator.validate(specification, domains);

        assertEquals(2, results.size(), "Should validate 2 domains");

        for (ShaclValidationResult result : results) {
            assertNotNull(result, "Result should not be null");
            assertTrue(domains.contains(result.complianceDomain()));
            assertTrue(result.validationTime() < 100,
                "Validation should complete in less than 100ms");
        }
    }

    @Test
    @DisplayName("Validate all supported domains for specification")
    void testValidateAllSpecificationDomains() {
        // Test validation of all supported domains
        List<ShaclValidationResult> results = validator.validateAll(specification);

        assertEquals(2, results.size(), "Should validate 2 specification domains");

        for (ShaclValidationResult result : results) {
            assertTrue(result.complianceDomain() == ComplianceDomain.SOX ||
                      result.complianceDomain() == ComplianceDomain.GDPR);
            assertTrue(result.validationTime() < 100,
                "Validation should complete in less than 100ms");
        }
    }

    @Test
    @DisplayName("Validate all supported domains for engine")
    void testValidateAllEngineDomains() {
        // Test validation of all supported domains for engine
        List<ShaclValidationResult> results = validator.validateAll(runner);

        assertEquals(2, results.size(), "Should validate 2 engine domains");

        for (ShaclValidationResult result : results) {
            assertTrue(result.complianceDomain() == ComplianceDomain.SOX ||
                      result.complianceDomain() == ComplianceDomain.HIPAA);
            assertTrue(result.validationTime() < 100,
                "Validation should complete in less than 100ms");
        }
    }

    @Test
    @DisplayName("Check supported specification domains")
    void testSupportedSpecificationDomains() {
        ComplianceDomain[] domains = validator.getSupportedSpecificationDomains();

        assertEquals(2, domains.length, "Should support 2 specification domains");
        assertTrue(List.of(domains).contains(ComplianceDomain.SOX));
        assertTrue(List.of(domains).contains(ComplianceDomain.GDPR));
    }

    @Test
    @DisplayName("Check supported engine domains")
    void testSupportedEngineDomains() {
        ComplianceDomain[] domains = validator.getSupportedEngineDomains();

        assertEquals(2, domains.length, "Should support 2 engine domains");
        assertTrue(List.of(domains).contains(ComplianceDomain.SOX));
        assertTrue(List.of(domains).contains(ComplianceDomain.HIPAA));
    }

    @Test
    @DisplayName("Check domain support for specification")
    void testSupportsSpecificationDomain() {
        assertTrue(validator.supportsSpecificationDomain(ComplianceDomain.SOX));
        assertTrue(validator.supportsSpecificationDomain(ComplianceDomain.GDPR));
        assertFalse(validator.supportsSpecificationDomain(ComplianceDomain.HIPAA));
    }

    @Test
    @DisplayName("Check domain support for engine")
    void testSupportsEngineDomain() {
        assertTrue(validator.supportsEngineDomain(ComplianceDomain.SOX));
        assertTrue(validator.supportsEngineDomain(ComplianceDomain.HIPAA));
        assertFalse(validator.supportsEngineDomain(ComplianceDomain.GDPR));
    }

    @Test
    @DisplayName("Get performance metrics")
    void testPerformanceMetrics() {
        // Perform some validations to populate metrics
        validator.validate(specification, ComplianceDomain.SOX);
        validator.validate(specification, ComplianceDomain.GDPR);
        validator.validate(runner, ComplianceDomain.SOX);

        Map<String, Object> metrics = validator.getPerformanceMetrics();

        assertNotNull(metrics, "Metrics should not be null");
        assertTrue(metrics.containsKey("totalValidations"));
        assertTrue(metrics.containsKey("totalValidationTime"));
        assertTrue(metrics.containsKey("averageValidationTime"));
        assertTrue(metrics.containsKey("lastValidationTime"));

        // Verify metrics values
        assertEquals(3, metrics.get("totalValidations"));
        assertTrue((long) metrics.get("totalValidationTime") > 0);
        assertTrue((long) metrics.get("averageValidationTime") > 0);
        assertTrue((long) metrics.get("lastValidationTime") > 0);
    }

    @Test
    @DisplayName("Reset performance metrics")
    void testResetPerformanceMetrics() {
        // Perform some validations
        validator.validate(specification, ComplianceDomain.SOX);

        // Get initial metrics
        Map<String, Object> initialMetrics = validator.getPerformanceMetrics();
        int initialValidations = (int) initialMetrics.get("totalValidations");

        // Reset metrics
        validator.resetPerformanceMetrics();

        // Verify metrics are reset
        Map<String, Object> resetMetrics = validator.getPerformanceMetrics();
        assertEquals(0, resetMetrics.get("totalValidations"));
        assertEquals(0L, resetMetrics.get("totalValidationTime"));
        assertEquals(0L, resetMetrics.get("averageValidationTime"));
    }

    @Test
    @DisplayName("Handle invalid specification")
    void testInvalidSpecification() {
        // Test with null specification
        assertThrows(NullPointerException.class, () -> {
            validator.validate(null, ComplianceDomain.SOX);
        });
    }

    @Test
    @DisplayName("Handle invalid runner")
    void testInvalidRunner() {
        // Test with null runner
        assertThrows(NullPointerException.class, () -> {
            validator.validate(null, ComplianceDomain.SOX);
        });
    }

    @Test
    @DisplayName("Validate SHA shapes availability")
    void testShapesAvailability() {
        // Test shapes availability with mock registry
        when(shapeRegistry.getAvailableDomains()).thenReturn(List.of(ComplianceDomain.SOX));

        List<ComplianceDomain> available = shapeRegistry.getAvailableDomains();
        assertEquals(1, available.size());
        assertEquals(ComplianceDomain.SOX, available.get(0));
    }
}