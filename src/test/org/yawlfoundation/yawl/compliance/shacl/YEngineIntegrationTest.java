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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.mockito.Mockito.*;

/**
 * Integration tests for SHACL validator with YEngine.
 */
@ExtendWith(MockitoExtension.class)
class YEngineIntegrationTest {

    private ShaclValidator validator;
    private YEngine engine;

    @Mock
    private YNetRunner mockRunner;

    @Mock
    private YSpecification mockSpecification;

    @Mock
    private YSpecificationID mockSpecId;

    @BeforeEach
    void setUp() {
        validator = new ShaclValidatorImpl();

        // Create a real YEngine instance for integration testing
        engine = new YEngine();
    }

    @Test
    @DisplayName("Create a simple YAWL specification and validate compliance")
    void testCreateAndValidateSpecification() throws Exception {
        // Create a simple YAWL specification XML
        String specXML = createSimpleFinancialSpecXML();

        // Unmarshal the specification
        Document doc = JDOMUtil.stringToDocument(specXML);
        YSpecification spec = YMarshal.unmarshalSpecification(doc);

        // Validate the specification
        ShaclValidationResult result = validator.validate(spec, ComplianceDomain.SOX);

        assertNotNull(result, "Validation result should not be null");
        assertEquals(ComplianceDomain.SOX, result.complianceDomain());
        assertEquals(spec.getSpecURI(), result.target());

        // Performance assertion
        assertTrue(result.validationTime() < 100,
            "Validation should complete in less than 100ms");
    }

    @Test
    @DisplayName("Validate YNetRunner with mock engine")
    void testValidateYNetRunner() {
        // Setup mock runner
        when(mockRunner.getSpecificationID()).thenReturn(mockSpecId);
        when(mockSpecId.toString()).thenReturn("mock-spec-id");

        // Validate the runner
        ShaclValidationResult result = validator.validate(mockRunner, ComplianceDomain.SOX);

        assertNotNull(result, "Validation result should not be null");
        assertEquals(ComplianceDomain.SOX, result.complianceDomain());
        assertEquals("mock-spec-id", result.target());

        // Performance assertion
        assertTrue(result.validationTime() < 100,
            "Validation should complete in less than 100ms");
    }

    @Test
    @DisplayName("Validate YEngine with multiple specifications")
    void testValidateYEngineWithMultipleSpecifications() throws Exception {
        // Create two specifications
        String financialSpecXML = createSimpleFinancialSpecXML();
        String healthcareSpecXML = createSimpleHealthcareSpecXML();

        // Unmarshal specifications
        Document financialDoc = JDOMUtil.stringToDocument(financialSpecXML);
        YSpecification financialSpec = YMarshal.unmarshalSpecification(financialDoc);

        Document healthcareDoc = JDOMUtil.stringToDocument(healthcareSpecXML);
        YSpecification healthcareSpec = YMarshal.unmarshalSpecification(healthcareDoc);

        // Validate both specifications
        List<ShaclValidationResult> results = validator.validateAll(financialSpec);

        assertEquals(2, results.size(), "Should validate 2 specification domains");

        // Check SOX result for financial spec
        ShaclValidationResult soxResult = results.stream()
            .filter(r -> r.complianceDomain() == ComplianceDomain.SOX)
            .findFirst()
            .orElseThrow();

        assertEquals(ComplianceDomain.SOX, soxResult.complianceDomain());
        assertEquals(financialSpec.getSpecURI(), soxResult.target());

        // Check GDPR result for healthcare spec
        ShaclValidationResult gdprResult = results.stream()
            .filter(r -> r.complianceDomain() == ComplianceDomain.GDPR)
            .findFirst()
            .orElseThrow();

        assertEquals(ComplianceDomain.GDPR, gdprResult.complianceDomain());
        assertEquals(healthcareSpec.getSpecURI(), gdprResult.target());
    }

    @Test
    @DisplayName("Test engine validation with different domains")
    void testEngineValidationWithDifferentDomains() {
        // Setup mock runner for engine validation
        when(mockRunner.getSpecificationID()).thenReturn(mockSpecId);
        when(mockSpecId.toString()).thenReturn("mock-spec-id");

        // Test SOX validation
        ShaclValidationResult soxResult = validator.validate(mockRunner, ComplianceDomain.SOX);
        assertNotNull(soxResult);
        assertEquals(ComplianceDomain.SOX, soxResult.complianceDomain());

        // Test HIPAA validation
        ShaclValidationResult hipaaResult = validator.validate(mockRunner, ComplianceDomain.HIPAA);
        assertNotNull(hipaaResult);
        assertEquals(ComplianceDomain.HIPAA, hipaaResult.complianceDomain());

        // Test that GDPR is not supported for engine validation
        assertThrows(IllegalArgumentException.class, () -> {
            validator.supportsEngineDomain(ComplianceDomain.GDPR);
        });
    }

    @Test
    @DisplayName("Performance test with multiple validations")
    void testPerformanceWithMultipleValidations() {
        // Setup mock runner
        when(mockRunner.getSpecificationID()).thenReturn(mockSpecId);
        when(mockSpecId.toString()).thenReturn("mock-spec-id");

        // Perform multiple validations
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < 100; i++) {
            validator.validate(mockRunner, ComplianceDomain.SOX);
            validator.validate(mockRunner, ComplianceDomain.HIPAA);
        }

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;

        // Average validation time should be less than 100ms
        long averageTime = totalTime / 200; // 100 iterations * 2 validations
        assertTrue(averageTime < 100,
            "Average validation time should be less than 100ms, but was: " + averageTime + "ms");

        // Get performance metrics
        Map<String, Object> metrics = validator.getPerformanceMetrics();
        assertEquals(200, metrics.get("totalValidations"));

        // Reset metrics
        validator.resetPerformanceMetrics();
        assertEquals(0, validator.getPerformanceMetrics().get("totalValidations"));
    }

    @Test
    @DisplayName("Error handling for invalid specifications")
    void testErrorHandling() {
        // Test with null specification
        assertThrows(NullPointerException.class, () -> {
            validator.validate(null, ComplianceDomain.SOX);
        });

        // Test with null runner
        assertThrows(NullPointerException.class, () -> {
            validator.validate(null, ComplianceDomain.SOX);
        });

        // Test with invalid compliance domain
        assertThrows(IllegalArgumentException.class, () -> {
            validator.supportsSpecificationDomain(null);
        });
    }

    /**
     * Creates a simple YAWL specification XML for financial processes.
     */
    private String createSimpleFinancialSpecXML() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
               "< specification xmlns=\"http://www.yawlfoundation.org/yawl\" >\n" +
               "  < specificationURI > simple-financial-spec < / specificationURI >\n" +
               "  < name > Simple Financial Process < / name >\n" +
               "  < specificationDescription > A simple financial workflow < / specificationDescription >\n" +
               "  < rootNet >\n" +
               "    < net id=\"simple-financial-net\" >\n" +
               "      < name > Simple Financial Net < / name >\n" +
               "      < inputs >\n" +
               "        < input id=\"input1\" / >\n" +
               "      < / inputs >\n" +
               "      < outputs >\n" +
               "        < output id=\"output1\" / >\n" +
               "      < / outputs >\n" +
               "      < tasks >\n" +
               "        < task id=\"task1\" name=\"Approve Payment\" >\n" +
               "          < documentation > Approves financial payment < / documentation >\n" +
               "        < / task >\n" +
               "        < task id=\"task2\" name=\"Process Payment\" >\n" +
               "          < documentation > Processes payment with audit trail < / documentation >\n" +
               "        < / task >\n" +
               "      < / tasks >\n" +
               "      < flows >\n" +
               "        < controlFlow >\n" +
               "          < from > input1 < / from >\n" +
               "          < to > task1 < / to >\n" +
               "        < / controlFlow >\n" +
               "        < controlFlow >\n" +
               "          < from > task1 < / from >\n" +
               "          < to > task2 < / to >\n" +
               "        < / controlFlow >\n" +
               "        < controlFlow >\n" +
               "          < from > task2 < / from >\n" +
               "          < to > output1 < / to >\n" +
               "        < / controlFlow >\n" +
               "      < / flows >\n" +
               "    < / net >\n" +
               "  < / rootNet >\n" +
               "< / specification >";
    }

    /**
     * Creates a simple YAWL specification XML for healthcare processes.
     */
    private String createSimpleHealthcareSpecXML() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
               "< specification xmlns=\"http://www.yawlfoundation.org/yawl\" >\n" +
               "  < specificationURI > simple-healthcare-spec < / specificationURI >\n" +
               "  < name > Simple Healthcare Process < / name >\n" +
               "  < specificationDescription > A simple healthcare workflow < / specificationDescription >\n" +
               "  < rootNet >\n" +
               "    < net id=\"simple-healthcare-net\" >\n" +
               "      < name > Simple Healthcare Net < / name >\n" +
               "      < inputs >\n" +
               "        < input id=\"input1\" / >\n" +
               "      < / inputs >\n" +
               "      < outputs >\n" +
               "        < output id=\"output1\" / >\n" +
               "      < / outputs >\n" +
               "      < tasks >\n" +
               "        < task id=\"task1\" name=\"Collect Patient Data\" >\n" +
               "          < documentation > Collects patient healthcare data < / documentation >\n" +
               "        < / task >\n" +
               "        < task id=\"task2\" name=\"Process Healthcare\" >\n" +
               "          < documentation > Processes healthcare data with privacy controls < / documentation >\n" +
               "        < / task >\n" +
               "      < / tasks >\n" +
               "      < flows >\n" +
               "        < controlFlow >\n" +
               "          < from > input1 < / from >\n" +
               "          < to > task1 < / to >\n" +
               "        < / controlFlow >\n" +
               "        < controlFlow >\n" +
               "          < from > task1 < / from >\n" +
               "          < to > task2 < / to >\n" +
               "        < / controlFlow >\n" +
               "        < controlFlow >\n" +
               "          < from > task2 < / from >\n" +
               "          < to > output1 < / to >\n" +
               "        < / controlFlow >\n" +
               "      < / flows >\n" +
               "    < / net >\n" +
               "  < / rootNet >\n" +
               "< / specification >";
    }
}