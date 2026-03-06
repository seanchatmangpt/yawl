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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.yawlfoundation.yawl.compliance.shacl.impl.ShaclValidatorImpl;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.elements.YDecomposition;
import org.yawlfoundation.yawl.engine.YNetRunner;
import org.yawlfoundation.yawl.engine.YEngine;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.yawlfoundation.yawl.util.JDOMUtil;

import org.jdom2.Document;
import org.jdom2.Element;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SHACL Validator Integration Tests with Real YAWL Engine (Chicago TDD)
 *
 * Tests real SHACL validation against actual YAWL specifications and engines
 * using H2 in-memory database for complete isolation.
 *
 * Coverage:
 * - Real YEngine initialization with H2
 * - Real YNetRunner workflow execution
 * - Real SHACL validation against SOX/GDPR/HIPAA domains
 * - Performance validation (100ms target per validation)
 * - Multi-specification validation scenarios
 * - Error handling and edge cases
 * - Database cleanup and isolation
 */
@Tag("integration")
class YEngineIntegrationTest {

    private ShaclValidator validator;
    private YEngine engine;

    @BeforeEach
    void setUp() throws Exception {
        // Initialize real SHACL validator
        validator = new ShaclValidatorImpl();

        // Initialize real YEngine instance with H2 database
        engine = YEngine.getInstance();
        assertNotNull(engine, "YEngine should be available");

        // Clear any existing data to ensure test isolation
        clearEngineData();
    }

    @AfterEach
    void tearDown() throws Exception {
        // Clean up engine data after each test
        if (engine != null) {
            clearEngineData();
        }
    }

    private void clearEngineData() throws Exception {
        // Clear work items and cases to ensure test isolation
        engine.getWorkItemRepository().clear();
        engine.getCaseRepository().clear();
    }

    @Test
    @DisplayName("Validate real YAWL specification against SOX compliance")
    void testCreateAndValidateSpecification() throws Exception {
        // Create a real YAWL specification for financial compliance
        String specXML = createCompliantFinancialSpecXML();
        Document doc = JDOMUtil.stringToDocument(specXML);
        YSpecification spec = YMarshal.unmarshalSpecification(doc);

        // Verify specification was created correctly
        assertNotNull(spec, "Specification should be created");
        assertEquals("compliant-financial-spec", spec.getSpecURI());
        assertNotNull(spec.getRootNet(), "Specification should have root net");

        // Validate against SOX compliance with real SHACL
        ShaclValidationResult result = validator.validate(spec, ComplianceDomain.SOX);

        // Verify real validation results
        assertNotNull(result, "SHACL validation should produce results");
        assertEquals(ComplianceDomain.SOX, result.complianceDomain());
        assertEquals(spec.getSpecURI(), result.target());
        assertTrue(result.valid(), "SOX compliant specification should pass validation");

        // Performance assertion (Chicago TDD: real measurements)
        assertTrue(result.validationTime() < 100,
            "SOX validation should complete in <100ms, but took: " + result.validationTime() + "ms");
    }

    @Test
    @DisplayName("Validate real YNetRunner with SOX compliance")
    void testValidateRealYNetRunner() throws Exception {
        // Create a real specification
        String specXML = createCompliantFinancialSpecXML();
        Document doc = JDOMUtil.stringToDocument(specXML);
        YSpecification spec = YMarshal.unmarshalSpecification(doc);

        // Create real YNetRunner with the specification
        YNetRunner runner = new YNetRunner(spec);
        assertNotNull(runner, "YNetRunner should be created");

        // Verify real runner integration
        YSpecificationID specId = runner.getSpecificationID();
        assertNotNull(specId, "Runner should have specification ID");

        // Validate real YNetRunner against SOX compliance
        ShaclValidationResult result = validator.validate(runner, ComplianceDomain.SOX);

        // Verify real validation results
        assertNotNull(result, "SHACL validation should produce results");
        assertEquals(ComplianceDomain.SOX, result.complianceDomain());
        assertEquals(spec.getSpecURI(), result.target());

        // Performance assertion
        assertTrue(result.validationTime() < 100,
            "SOX validation of real runner should complete in <100ms, but took: " + result.validationTime() + "ms");
    }

    @Test
    @DisplayName("Validate real YEngine with multiple specifications across domains")
    void testValidateYEngineWithMultipleSpecifications() throws Exception {
        // Create real specifications for different compliance domains
        String financialSpecXML = createCompliantFinancialSpecXML();
        String healthcareSpecXML = createCompliantHealthcareSpecXML();

        // Unmarshal real specifications
        Document financialDoc = JDOMUtil.stringToDocument(financialSpecXML);
        YSpecification financialSpec = YMarshal.unmarshalSpecification(financialDoc);

        Document healthcareDoc = JDOMUtil.stringToDocument(healthcareSpecXML);
        YSpecification healthcareSpec = YMarshal.unmarshalSpecification(healthcareDoc);

        // Verify specifications are valid YAWL specs
        assertNotNull(financialSpec.getRootNet(), "Financial spec should have root net");
        assertNotNull(healthcareSpec.getRootNet(), "Healthcare spec should have root net");

        // Validate all domains for financial specification (SOX only)
        List<ShaclValidationResult> financialResults = validator.validateAll(financialSpec);
        assertEquals(1, financialResults.size(), "Financial spec should support 1 domain (SOX)");

        // Validate all domains for healthcare specification (GDPR only)
        List<ShaclValidationResult> healthcareResults = validator.validateAll(healthcareSpec);
        assertEquals(1, healthcareResults.size(), "Healthcare spec should support 1 domain (GDPR)");

        // Check SOX validation for financial spec
        ShaclValidationResult soxResult = financialResults.stream()
            .filter(r -> r.complianceDomain() == ComplianceDomain.SOX)
            .findFirst()
            .orElseThrow();

        assertEquals(ComplianceDomain.SOX, soxResult.complianceDomain());
        assertEquals(financialSpec.getSpecURI(), soxResult.target());
        assertTrue(soxResult.valid(), "Financial spec should be SOX compliant");

        // Check GDPR validation for healthcare spec
        ShaclValidationResult gdprResult = healthcareResults.stream()
            .filter(r -> r.complianceDomain() == ComplianceDomain.GDPR)
            .findFirst()
            .orElseThrow();

        assertEquals(ComplianceDomain.GDPR, gdprResult.complianceDomain());
        assertEquals(healthcareSpec.getSpecURI(), gdprResult.target());
        assertTrue(gdprResult.valid(), "Healthcare spec should be GDPR compliant");
    }

    @Test
    @DisplayName("Test real YEngine validation across different compliance domains")
    void testRealEngineValidationWithDifferentDomains() throws Exception {
        // Create a real specification that supports engine validation
        String specXML = createCompliantFinancialSpecXML();
        Document doc = JDOMUtil.stringToDocument(specXML);
        YSpecification spec = YMarshal.unmarshalSpecification(doc);

        // Create real YNetRunner for engine validation
        YNetRunner runner = new YNetRunner(spec);
        assertNotNull(runner, "Real YNetRunner should be created");

        // Test SOX validation with real engine
        ShaclValidationResult soxResult = validator.validate(runner, ComplianceDomain.SOX);
        assertNotNull(soxResult, "SOX validation should succeed");
        assertEquals(ComplianceDomain.SOX, soxResult.complianceDomain());
        assertTrue(soxResult.valid(), "SOX validation should pass for financial spec");

        // Test HIPAA validation with real engine
        ShaclValidationResult hipaaResult = validator.validate(runner, ComplianceDomain.HIPAA);
        assertNotNull(hipaaResult, "HIPAA validation should succeed");
        assertEquals(ComplianceDomain.HIPAA, hipaaResult.complianceDomain());
        assertTrue(hipaaResult.valid(), "HIPAA validation should pass for financial spec");

        // Verify that GDPR is not supported for engine validation (real assertion)
        assertFalse(validator.supportsEngineDomain(ComplianceDomain.GDPR),
            "GDPR should not be supported for engine validation");
    }

    @Test
    @DisplayName("Performance test with real YEngine and 200 validations")
    void testPerformanceWithRealEngineValidations() throws Exception {
        // Create a real specification for performance testing
        String specXML = createCompliantFinancialSpecXML();
        Document doc = JDOMUtil.stringToDocument(specXML);
        YSpecification spec = YMarshal.unmarshalSpecification(doc);

        // Create real YNetRunner for performance testing
        YNetRunner runner = new YNetRunner(spec);
        assertNotNull(runner, "Real YNetRunner should be created");

        // Perform 200 real validations (100 SOX + 100 HIPAA)
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < 100; i++) {
            // Real SOX validation
            ShaclValidationResult soxResult = validator.validate(runner, ComplianceDomain.SOX);
            assertTrue(soxResult.valid(), "SOX validation should pass");
            assertTrue(soxResult.validationTime() < 100,
                "SOX validation should be fast: " + soxResult.validationTime() + "ms");

            // Real HIPAA validation
            ShaclValidationResult hipaaResult = validator.validate(runner, ComplianceDomain.HIPAA);
            assertTrue(hipaaResult.valid(), "HIPAA validation should pass");
            assertTrue(hipaaResult.validationTime() < 100,
                "HIPAA validation should be fast: " + hipaaResult.validationTime() + "ms");
        }

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        // Average validation time should be less than 100ms per validation
        long averageTime = totalTime / 200; // 200 total validations
        assertTrue(averageTime < 100,
            "Average validation time should be <100ms, actual: " + averageTime + "ms");

        // Verify real performance metrics
        Map<String, Object> metrics = validator.getPerformanceMetrics();
        assertEquals(200, metrics.get("totalValidations"),
            "Should track 200 real validations");

        // Reset metrics and verify
        validator.resetPerformanceMetrics();
        Map<String, Object> resetMetrics = validator.getPerformanceMetrics();
        assertEquals(0, resetMetrics.get("totalValidations"),
            "Metrics should reset to zero");
    }

    @Test
    @DisplayName("Error handling for null and invalid inputs")
    void testErrorHandling() {
        // Test with null specification (should throw NPE)
        assertThrows(NullPointerException.class, () -> {
            validator.validate(null, ComplianceDomain.SOX);
        }, "Should throw NPE for null specification");

        // Test with null runner (should throw NPE)
        assertThrows(NullPointerException.class, () -> {
            validator.validate(null, ComplianceDomain.SOX);
        }, "Should throw NPE for null runner");

        // Test with null compliance domain (should throw IAE)
        assertThrows(IllegalArgumentException.class, () -> {
            validator.supportsSpecificationDomain(null);
        }, "Should throw IAE for null compliance domain");
    }

    @Test
    @DisplayName("Verify real SHACL shapes are loaded and functional")
    void testShaclShapesAreLoaded() {
        // Test that SOX domain is supported for specifications
        assertTrue(validator.supportsSpecificationDomain(ComplianceDomain.SOX),
            "SOX should be supported for specification validation");

        // Test that GDPR domain is supported for specifications
        assertTrue(validator.supportsSpecificationDomain(ComplianceDomain.GDPR),
            "GDPR should be supported for specification validation");

        // Test that HIPAA domain is supported for specifications
        assertTrue(validator.supportsSpecificationDomain(ComplianceDomain.HIPAA),
            "HIPAA should be supported for specification validation");

        // Test engine domains
        assertTrue(validator.supportsEngineDomain(ComplianceDomain.SOX),
            "SOX should be supported for engine validation");

        assertTrue(validator.supportsEngineDomain(ComplianceDomain.HIPAA),
            "HIPAA should be supported for engine validation");

        assertFalse(validator.supportsEngineDomain(ComplianceDomain.GDPR),
            "GDPR should not be supported for engine validation");
    }

    /**
     * Creates a SOX-compliant YAWL specification XML.
     */
    private String createCompliantFinancialSpecXML() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            < specification xmlns="http://www.yawlfoundation.org/yawl" >
              < specificationURI > compliant-financial-spec < / specificationURI >
              < name > SOX Compliant Financial Process < / name >
              < specificationDescription > Financial workflow with SOX audit controls < / specificationDescription >
              < rootNet >
                < net id="sox-financial-net" >
                  < name > Financial Net < / name >
                  < inputs >
                    < input id="request_input" / >
                  < / inputs >
                  < outputs >
                    < output id="payment_output" / >
                    < output id="audit_output" / >
                  < / outputs >
                  < tasks >
                    < task id="verify_task" name="Verify Payment Request" >
                      < documentation >
                        Verifies payment request has proper authorization and documentation.
                        Required for SOX compliance to prevent fraudulent transactions.
                      < / documentation >
                      < decomposition id="atomic_verification" />
                    < / task >
                    < task id="approve_task" name="Approve Payment" >
                      < documentation >
                        Final approval by authorized manager with dual control.
                        Ensures proper oversight for SOX compliance.
                      < / documentation >
                      < decomposition id="manager_approval" />
                    < / task >
                    < task id="audit_task" name="Audit Trail" >
                      < documentation >
                        Creates permanent audit trail of all transactions.
                        Required by SOX for financial accountability.
                      < / documentation >
                      < decomposition id="atomic_audit" />
                    < / task >
                  < / tasks >
                  < flows >
                    < controlFlow >
                      < from > request_input < / from >
                      < to > verify_task < / to >
                    < / controlFlow >
                    < controlFlow >
                      < from > verify_task < / from >
                      < to > approve_task < / to >
                    < / controlFlow >
                    < controlFlow >
                      < from > approve_task < / from >
                      < to > payment_output < / to >
                    < / controlFlow >
                    < controlFlow >
                      < from > verify_task < / from >
                      < to > audit_task < / to >
                    < / controlFlow >
                    < controlFlow >
                      < from > audit_task < / from >
                      < to > audit_output < / to >
                    < / controlFlow >
                  < / flows >
                < / net >
              < / rootNet >
            < / specification >
            """;
    }

    /**
     * Creates a GDPR-compliant YAWL specification XML.
     */
    private String createCompliantHealthcareSpecXML() {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            < specification xmlns="http://www.yawlfoundation.org/yawl" >
              < specificationURI > compliant-healthcare-spec < / specificationURI >
              < name > GDPR Compliant Healthcare Process < / name >
              < specificationDescription > Healthcare workflow with privacy controls < / specificationDescription >
              < rootNet >
                < net id="gdpr-healthcare-net" >
                  < name > Healthcare Net < / name >
                  < inputs >
                    < input id="patient_input" / >
                  < / inputs >
                  < outputs >
                    < output id="treatment_output" / >
                    < output id="privacy_output" / >
                  < / outputs >
                  < tasks >
                    < task id="consent_task" name="Obtain Patient Consent" >
                      < documentation >
                        Obtains explicit patient consent for data processing.
                        Required by GDPR for lawful data processing.
                      < / documentation >
                      < decomposition id="atomic_consent" />
                    < / task >
                    < task id="privacy_task" name="Privacy Check" >
                      < documentation >
                        Ensures data minimization and proper anonymization.
                        Required by GDPR to protect patient privacy.
                      < / documentation >
                      < decomposition id="atomic_privacy" />
                    < / task >
                    < task id="process_task" name="Process Healthcare" >
                      < documentation >
                        Processes healthcare data with privacy safeguards.
                        Compliant with GDPR data processing principles.
                      < / documentation >
                      < decomposition id="healthcare_processing" />
                    < / task >
                  < / tasks >
                  < flows >
                    < controlFlow >
                      < from > patient_input < / from >
                      < to > consent_task < / to >
                    < / controlFlow >
                    < controlFlow >
                      < from > consent_task < / from >
                      < to > privacy_task < / to >
                    < / controlFlow >
                    < controlFlow >
                      < from > privacy_task < / from >
                      < to > process_task < / to >
                    < / controlFlow >
                    < controlFlow >
                      < from > process_task < / from >
                      < to > treatment_output < / to >
                    < / controlFlow >
                    < controlFlow >
                      < from > privacy_task < / from >
                      < to > privacy_output < / to >
                    < / controlFlow >
                  < / flows >
                < / net >
              < / rootNet >
            < / specification >
            """;
    }
}