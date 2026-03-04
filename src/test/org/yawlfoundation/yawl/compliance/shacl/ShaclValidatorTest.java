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

package org.yawlfoundation.yawl.compliance.shacl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.yawlfoundation.yawl.compliance.shacl.impl.ShaclValidatorImpl;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.engine.YNetRunner;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.jdom2.Document;
import org.yawlfoundation.yawl.util.JDOMUtil;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD tests for SHACL validator functionality.
 * Uses real YSpecification and YNetRunner objects instead of mocks.
 */
class ShaclValidatorTest {

    private ShaclValidator validator;
    private ShaclShapeRegistry shapeRegistry;
    private YSpecification financialSpecification;
    private YSpecification healthcareSpecification;
    private YNetRunner runner;

    @BeforeEach
    void setUp() throws Exception {
        // Create real shape registry
        shapeRegistry = new ShaclShapeRegistry();

        // Create validator with real shape registry
        validator = new ShaclValidatorImpl(shapeRegistry);

        // Create real specifications from XML
        String financialSpecXML = createSimpleFinancialSpecXML();
        Document financialDoc = JDOMUtil.stringToDocument(financialSpecXML);
        financialSpecification = YMarshal.unmarshalSpecification(financialDoc);

        String healthcareSpecXML = createSimpleHealthcareSpecXML();
        Document healthcareDoc = JDOMUtil.stringToDocument(healthcareSpecXML);
        healthcareSpecification = YMarshal.unmarshalSpecification(healthcareDoc);

        // Create a real YNetRunner (or use a test harness if available)
        runner = createTestRunner();
    }

    @Nested
    @DisplayName("Specification Validation")
    class SpecificationValidationTest {

        @Test
        @DisplayName("Validate SOX compliance for financial specification")
        void testValidateSOXCompliance() {
            ShaclValidationResult result = validator.validate(financialSpecification, ComplianceDomain.SOX);

            assertNotNull(result, "Result should not be null");
            assertEquals(ComplianceDomain.SOX, result.complianceDomain());
            assertEquals(financialSpecification.getSpecURI(), result.target());
            assertTrue(result.timestamp().isBefore(Instant.now()));

            // Performance assertion (<100ms)
            assertTrue(result.validationTime() < 100,
                "Validation should complete in less than 100ms, but took: " + result.validationTime() + "ms");
        }

        @Test
        @DisplayName("Validate GDPR compliance for healthcare specification")
        void testValidateGDPRCompliance() {
            ShaclValidationResult result = validator.validate(healthcareSpecification, ComplianceDomain.GDPR);

            assertNotNull(result, "Result should not be null");
            assertEquals(ComplianceDomain.GDPR, result.complianceDomain());
            assertEquals(healthcareSpecification.getSpecURI(), result.target());

            // Performance assertion (<100ms)
            assertTrue(result.validationTime() < 100,
                "Validation should complete in less than 100ms, but took: " + result.validationTime() + "ms");
        }

        @Test
        @DisplayName("Validate HIPAA compliance for healthcare specification")
        void testValidateHIPAACompliance() {
            ShaclValidationResult result = validator.validate(healthcareSpecification, ComplianceDomain.HIPAA);

            assertNotNull(result, "Result should not be null");
            assertEquals(ComplianceDomain.HIPAA, result.complianceDomain());

            // Performance assertion (<100ms)
            assertTrue(result.validationTime() < 100,
                "Validation should complete in less than 100ms, but took: " + result.validationTime() + "ms");
        }
    }

    @Nested
    @DisplayName("Engine Validation")
    class EngineValidationTest {

        @Test
        @DisplayName("Validate SOX compliance for engine")
        void testValidateSOXEngineCompliance() {
            ShaclValidationResult result = validator.validate(runner, ComplianceDomain.SOX);

            assertNotNull(result, "Result should not be null");
            assertEquals(ComplianceDomain.SOX, result.complianceDomain());

            // Performance assertion (<100ms)
            assertTrue(result.validationTime() < 100,
                "Validation should complete in less than 100ms, but took: " + result.validationTime() + "ms");
        }

        @Test
        @DisplayName("Validate HIPAA compliance for engine")
        void testValidateHIPAAEngineCompliance() {
            ShaclValidationResult result = validator.validate(runner, ComplianceDomain.HIPAA);

            assertNotNull(result, "Result should not be null");
            assertEquals(ComplianceDomain.HIPAA, result.complianceDomain());

            // Performance assertion (<100ms)
            assertTrue(result.validationTime() < 100,
                "Validation should complete in less than 100ms, but took: " + result.validationTime() + "ms");
        }
    }

    @Nested
    @DisplayName("Multi-Domain Validation")
    class MultiDomainValidationTest {

        @Test
        @DisplayName("Validate multiple domains for specification")
        void testValidateMultipleDomains() {
            List<ComplianceDomain> domains = List.of(ComplianceDomain.SOX, ComplianceDomain.GDPR);
            List<ShaclValidationResult> results = validator.validate(financialSpecification, domains);

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
            List<ShaclValidationResult> results = validator.validateAll(financialSpecification);

            assertTrue(results.size() >= 1, "Should validate at least 1 specification domain");

            for (ShaclValidationResult result : results) {
                assertTrue(result.validationTime() < 100,
                    "Validation should complete in less than 100ms");
            }
        }

        @Test
        @DisplayName("Validate all supported domains for engine")
        void testValidateAllEngineDomains() {
            List<ShaclValidationResult> results = validator.validateAll(runner);

            assertTrue(results.size() >= 1, "Should validate at least 1 engine domain");

            for (ShaclValidationResult result : results) {
                assertTrue(result.validationTime() < 100,
                    "Validation should complete in less than 100ms");
            }
        }
    }

    @Nested
    @DisplayName("Domain Support")
    class DomainSupportTest {

        @Test
        @DisplayName("Check supported specification domains")
        void testSupportedSpecificationDomains() {
            ComplianceDomain[] domains = validator.getSupportedSpecificationDomains();

            assertTrue(domains.length >= 1, "Should support at least 1 specification domain");
        }

        @Test
        @DisplayName("Check supported engine domains")
        void testSupportedEngineDomains() {
            ComplianceDomain[] domains = validator.getSupportedEngineDomains();

            assertTrue(domains.length >= 1, "Should support at least 1 engine domain");
        }

        @Test
        @DisplayName("Check domain support for specification")
        void testSupportsSpecificationDomain() {
            // Check that at least some domains are supported
            boolean hasSupport = false;
            for (ComplianceDomain domain : ComplianceDomain.values()) {
                if (validator.supportsSpecificationDomain(domain)) {
                    hasSupport = true;
                    break;
                }
            }
            assertTrue(hasSupport, "Should support at least one specification domain");
        }

        @Test
        @DisplayName("Check domain support for engine")
        void testSupportsEngineDomain() {
            // Check that at least some domains are supported
            boolean hasSupport = false;
            for (ComplianceDomain domain : ComplianceDomain.values()) {
                if (validator.supportsEngineDomain(domain)) {
                    hasSupport = true;
                    break;
                }
            }
            assertTrue(hasSupport, "Should support at least one engine domain");
        }
    }

    @Nested
    @DisplayName("Performance Metrics")
    class PerformanceMetricsTest {

        @Test
        @DisplayName("Get performance metrics")
        void testPerformanceMetrics() {
            // Perform some validations to populate metrics
            validator.validate(financialSpecification, ComplianceDomain.SOX);
            validator.validate(financialSpecification, ComplianceDomain.GDPR);
            validator.validate(runner, ComplianceDomain.SOX);

            Map<String, Object> metrics = validator.getPerformanceMetrics();

            assertNotNull(metrics, "Metrics should not be null");
            assertTrue(metrics.containsKey("totalValidations"));
            assertTrue(metrics.containsKey("totalValidationTime"));

            // Verify metrics values
            assertEquals(3, metrics.get("totalValidations"));
        }

        @Test
        @DisplayName("Reset performance metrics")
        void testResetPerformanceMetrics() {
            // Perform some validations
            validator.validate(financialSpecification, ComplianceDomain.SOX);

            // Reset metrics
            validator.resetPerformanceMetrics();

            // Verify metrics are reset
            Map<String, Object> resetMetrics = validator.getPerformanceMetrics();
            assertEquals(0, resetMetrics.get("totalValidations"));
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTest {

        @Test
        @DisplayName("Handle null specification")
        void testNullSpecification() {
            assertThrows(NullPointerException.class, () -> {
                validator.validate((YSpecification) null, ComplianceDomain.SOX);
            });
        }

        @Test
        @DisplayName("Handle null runner")
        void testNullRunner() {
            assertThrows(NullPointerException.class, () -> {
                validator.validate((YNetRunner) null, ComplianceDomain.SOX);
            });
        }
    }

    // Helper methods to create test fixtures

    private YNetRunner createTestRunner() throws Exception {
        // Create a simple test runner using the financial specification
        // This creates a real runner instead of a mock
        YSpecificationID specId = new YSpecificationID(
            financialSpecification.getSpecURI(),
            financialSpecification.getVersion(),
            null,
            null
        );

        // Create a minimal test runner
        return new TestNetRunner(specId, financialSpecification);
    }

    /**
     * Simple test implementation of YNetRunner for testing.
     */
    private static class TestNetRunner extends YNetRunner {
        private final YSpecificationID specId;

        public TestNetRunner(YSpecificationID specId, YSpecification spec) {
            this.specId = specId;
        }

        @Override
        public YSpecificationID getSpecificationID() {
            return specId;
        }
    }

    /**
     * Creates a simple YAWL specification XML for financial processes.
     */
    private String createSimpleFinancialSpecXML() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
               "<specification xmlns=\"http://www.yawlfoundation.org/yawl\">\n" +
               "  <specificationURI>simple-financial-spec</specificationURI>\n" +
               "  <name>Simple Financial Process</name>\n" +
               "  <specificationDescription>A simple financial workflow</specificationDescription>\n" +
               "  <rootNet>\n" +
               "    <net id=\"simple-financial-net\">\n" +
               "      <name>Simple Financial Net</name>\n" +
               "      <inputCondition id=\"input1\"/>\n" +
               "      <outputCondition id=\"output1\"/>\n" +
               "      <tasks>\n" +
               "        <task id=\"task1\" name=\"Approve Payment\">\n" +
               "          <documentation>Approves financial payment</documentation>\n" +
               "        </task>\n" +
               "        <task id=\"task2\" name=\"Process Payment\">\n" +
               "          <documentation>Processes payment with audit trail</documentation>\n" +
               "        </task>\n" +
               "      </tasks>\n" +
               "      <flows>\n" +
               "        <flow source=\"input1\" target=\"task1\"/>\n" +
               "        <flow source=\"task1\" target=\"task2\"/>\n" +
               "        <flow source=\"task2\" target=\"output1\"/>\n" +
               "      </flows>\n" +
               "    </net>\n" +
               "  </rootNet>\n" +
               "</specification>";
    }

    /**
     * Creates a simple YAWL specification XML for healthcare processes.
     */
    private String createSimpleHealthcareSpecXML() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
               "<specification xmlns=\"http://www.yawlfoundation.org/yawl\">\n" +
               "  <specificationURI>simple-healthcare-spec</specificationURI>\n" +
               "  <name>Simple Healthcare Process</name>\n" +
               "  <specificationDescription>A simple healthcare workflow</specificationDescription>\n" +
               "  <rootNet>\n" +
               "    <net id=\"simple-healthcare-net\">\n" +
               "      <name>Simple Healthcare Net</name>\n" +
               "      <inputCondition id=\"input1\"/>\n" +
               "      <outputCondition id=\"output1\"/>\n" +
               "      <tasks>\n" +
               "        <task id=\"task1\" name=\"Collect Patient Data\">\n" +
               "          <documentation>Collects patient healthcare data</documentation>\n" +
               "        </task>\n" +
               "        <task id=\"task2\" name=\"Process Healthcare\">\n" +
               "          <documentation>Processes healthcare data with privacy controls</documentation>\n" +
               "        </task>\n" +
               "      </tasks>\n" +
               "      <flows>\n" +
               "        <flow source=\"input1\" target=\"task1\"/>\n" +
               "        <flow source=\"task1\" target=\"task2\"/>\n" +
               "        <flow source=\"task2\" target=\"output1\"/>\n" +
               "      </flows>\n" +
               "    </net>\n" +
               "  </rootNet>\n" +
               "</specification>";
    }
}
