package org.yawlfoundation.yawl.ggen.mining.generators;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.ggen.mining.model.Arc;
import org.yawlfoundation.yawl.ggen.mining.model.PetriNet;
import org.yawlfoundation.yawl.ggen.mining.model.Place;
import org.yawlfoundation.yawl.ggen.mining.model.Transition;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for process export generators.
 * Tests round-trip conversion of Petri net models to various formats.
 * Validates XML/HCL structure and completeness.
 */
class GeneratorIntegrationTest {

    private PetriNet simplePetriNet;

    /**
     * Set up a minimal Petri net for testing:
     * - 3 places (start, middle, end)
     * - 2 transitions (begin, finish)
     * - 4 arcs (start→begin→middle→finish→end)
     */
    @BeforeEach
    void setUp() {
        simplePetriNet = new PetriNet("test_process", "Test Process");

        // Create places
        Place startPlace = new Place("p_start", "Start", 1);
        Place middlePlace = new Place("p_middle", "Middle");
        Place endPlace = new Place("p_end", "End");

        simplePetriNet.addPlace(startPlace);
        simplePetriNet.addPlace(middlePlace);
        simplePetriNet.addPlace(endPlace);

        // Create transitions
        Transition beginTransition = new Transition("t_begin", "Begin Task");
        Transition finishTransition = new Transition("t_finish", "Finish Task");

        simplePetriNet.addTransition(beginTransition);
        simplePetriNet.addTransition(finishTransition);

        // Create arcs
        Arc arc1 = new Arc("a1", startPlace, beginTransition);
        Arc arc2 = new Arc("a2", beginTransition, middlePlace);
        Arc arc3 = new Arc("a3", middlePlace, finishTransition);
        Arc arc4 = new Arc("a4", finishTransition, endPlace);

        simplePetriNet.addArc(arc1);
        simplePetriNet.addArc(arc2);
        simplePetriNet.addArc(arc3);
        simplePetriNet.addArc(arc4);
    }

    /**
     * Test that Camunda BPMN export produces valid XML with required elements.
     */
    @Test
    void testCamundaExportProducesValidXml() {
        CamundaBpmnExporter exporter = new CamundaBpmnExporter();
        String bpmn = exporter.export(simplePetriNet);

        assertNotNull(bpmn, "BPMN output should not be null");
        assertTrue(bpmn.startsWith("<?xml version=\"1.0\""), "Should start with XML declaration");
        assertTrue(bpmn.contains("bpmn:definitions"), "Should contain BPMN definitions element");
        assertTrue(bpmn.contains("bpmn:process"), "Should contain BPMN process element");
        assertTrue(bpmn.contains("bpmn:serviceTask"), "Should contain service tasks for regular transitions");
        assertTrue(bpmn.contains("camunda:type=\"external\""), "Should contain Camunda extensions");
        assertTrue(bpmn.contains("bpmn:sequenceFlow"), "Should contain sequence flows for arcs");
        assertTrue(bpmn.contains("test_process"), "Should contain process ID");
        assertTrue(bpmn.contains("Test Process"), "Should contain process name");
    }

    /**
     * Test that Camunda BPMN export is well-formed (basic XML structure).
     */
    @Test
    void testCamundaBpmnIsWellFormed() {
        CamundaBpmnExporter exporter = new CamundaBpmnExporter();
        String bpmn = exporter.export(simplePetriNet);

        // Check for balanced XML tags
        int definitionsOpenCount = countOccurrences(bpmn, "<bpmn:definitions");
        int definitionsCloseCount = countOccurrences(bpmn, "</bpmn:definitions>");
        assertEquals(definitionsOpenCount, definitionsCloseCount, "Definitions tags should be balanced");

        int processOpenCount = countOccurrences(bpmn, "<bpmn:process");
        int processCloseCount = countOccurrences(bpmn, "</bpmn:process>");
        assertEquals(processOpenCount, processCloseCount, "Process tags should be balanced");
    }

    /**
     * Test that BPEL export produces valid XML with required elements.
     */
    @Test
    void testBpelExportProducesValidXml() {
        BpelExporter exporter = new BpelExporter();
        String bpel = exporter.export(simplePetriNet);

        assertNotNull(bpel, "BPEL output should not be null");
        assertTrue(bpel.startsWith("<?xml version=\"1.0\""), "Should start with XML declaration");
        assertTrue(bpel.contains("<bpel:process"), "Should contain BPEL process element");
        assertTrue(bpel.contains("bpel:partnerLinks"), "Should contain partner links");
        assertTrue(bpel.contains("bpel:variables"), "Should contain variables");
        assertTrue(bpel.contains("bpel:sequence"), "Should contain sequence element");
        assertTrue(bpel.contains("bpel:invoke"), "Should contain invoke activities for transitions");
        assertTrue(bpel.contains("bpel:receive"), "Should contain receive activity");
        assertTrue(bpel.contains("bpel:reply"), "Should contain reply activity");
    }

    /**
     * Test that BPEL export is well-formed (basic XML structure).
     */
    @Test
    void testBpelIsWellFormed() {
        BpelExporter exporter = new BpelExporter();
        String bpel = exporter.export(simplePetriNet);

        // Check for balanced XML tags
        int processOpenCount = countOccurrences(bpel, "<bpel:process");
        int processCloseCount = countOccurrences(bpel, "</bpel:process>");
        assertEquals(processOpenCount, processCloseCount, "Process tags should be balanced");

        int sequenceOpenCount = countOccurrences(bpel, "<bpel:sequence");
        int sequenceCloseCount = countOccurrences(bpel, "</bpel:sequence>");
        assertEquals(sequenceOpenCount, sequenceCloseCount, "Sequence tags should be balanced");
    }

    /**
     * Test that Terraform AWS export produces valid HCL with required resources.
     */
    @Test
    void testTerraformAwsExportProducesHcl() {
        TerraformGenerator exporter = new TerraformGenerator();
        String terraform = exporter.generateTerraform(simplePetriNet, "aws");

        assertNotNull(terraform, "Terraform output should not be null");
        assertTrue(terraform.contains("terraform {"), "Should contain terraform configuration");
        assertTrue(terraform.contains("provider \"aws\""), "Should contain AWS provider block");
        assertTrue(terraform.contains("resource \"aws_lambda_function\""), "Should contain Lambda function resources");
        assertTrue(terraform.contains("resource \"aws_sfn_state_machine\""), "Should contain Step Functions state machine");
        assertTrue(terraform.contains("resource \"aws_sqs_queue\""), "Should contain SQS queue resource");
    }

    /**
     * Test that factory dispatches to Camunda exporter correctly.
     */
    @Test
    void testFactoryDispatchesCamunda() {
        String bpmn = ProcessExporterFactory.export(simplePetriNet, "CAMUNDA");

        assertNotNull(bpmn, "Factory should return non-null BPMN");
        assertTrue(bpmn.startsWith("<?xml"), "Should return valid XML");
        assertTrue(bpmn.contains("bpmn:definitions"), "Should contain BPMN definitions");
        assertTrue(bpmn.contains("bpmn:process"), "Should contain BPMN process");
    }

    /**
     * Test that factory dispatches to BPEL exporter correctly.
     */
    @Test
    void testFactoryDispatchesBpel() {
        String bpel = ProcessExporterFactory.export(simplePetriNet, "BPEL");

        assertNotNull(bpel, "Factory should return non-null BPEL");
        assertTrue(bpel.startsWith("<?xml"), "Should return valid XML");
        assertTrue(bpel.contains("bpel:process"), "Should contain BPEL process");
        assertTrue(bpel.contains("bpel:partnerLinks"), "Should contain partner links");
    }

    /**
     * Test that factory dispatches to Terraform AWS exporter correctly.
     */
    @Test
    void testFactoryDispatchesTerraformAws() {
        String terraform = ProcessExporterFactory.export(simplePetriNet, "TERRAFORM_AWS");

        assertNotNull(terraform, "Factory should return non-null Terraform");
        assertTrue(terraform.contains("provider \"aws\""), "Should contain AWS provider");
        assertTrue(terraform.contains("resource \"aws_lambda_function\""), "Should contain Lambda resources");
    }

    /**
     * Test that factory dispatches to Terraform Azure exporter correctly.
     */
    @Test
    void testFactoryDispatchesTerraformAzure() {
        String terraform = ProcessExporterFactory.export(simplePetriNet, "TERRAFORM_AZURE");

        assertNotNull(terraform, "Factory should return non-null Terraform");
        assertTrue(terraform.contains("azurerm"), "Should contain Azure resources");
    }

    /**
     * Test that factory dispatches to Terraform GCP exporter correctly.
     */
    @Test
    void testFactoryDispatchesTerraformGcp() {
        String terraform = ProcessExporterFactory.export(simplePetriNet, "TERRAFORM_GCP");

        assertNotNull(terraform, "Factory should return non-null Terraform");
        assertTrue(terraform.contains("google"), "Should contain GCP resources");
    }

    /**
     * Test that factory dispatches to Kubernetes exporter correctly.
     */
    @Test
    void testFactoryDispatchesKubernetes() {
        String kubernetes = ProcessExporterFactory.export(simplePetriNet, "KUBERNETES");

        assertNotNull(kubernetes, "Factory should return non-null Kubernetes");
        assertTrue(kubernetes.contains("apiVersion"), "Should contain Kubernetes resources");
        assertTrue(kubernetes.contains("kind"), "Should contain Kubernetes kind");
    }

    /**
     * Test that factory throws on unknown format.
     */
    @Test
    void testFactoryThrowsOnUnknown() {
        assertThrows(IllegalArgumentException.class, () ->
                ProcessExporterFactory.export(simplePetriNet, "UNKNOWN_FORMAT"),
                "Factory should throw IllegalArgumentException for unknown format"
        );
    }

    /**
     * Test that factory throws on null model.
     */
    @Test
    void testFactoryThrowsOnNullModel() {
        assertThrows(IllegalArgumentException.class, () ->
                ProcessExporterFactory.export(null, "CAMUNDA"),
                "Factory should throw IllegalArgumentException for null model"
        );
    }

    /**
     * Test that factory throws on null format.
     */
    @Test
    void testFactoryThrowsOnNullFormat() {
        assertThrows(IllegalArgumentException.class, () ->
                ProcessExporterFactory.export(simplePetriNet, null),
                "Factory should throw IllegalArgumentException for null format"
        );
    }

    /**
     * Test that supported formats list is not empty.
     */
    @Test
    void testSupportedFormatsNotEmpty() {
        var formats = ProcessExporterFactory.supportedFormats();

        assertNotNull(formats, "Supported formats list should not be null");
        assertFalse(formats.isEmpty(), "Supported formats list should not be empty");
        assertTrue(formats.size() >= 6, "Should support at least 6 formats (CAMUNDA, BPEL, TERRAFORM_AWS, TERRAFORM_AZURE, TERRAFORM_GCP, KUBERNETES)");
    }

    /**
     * Test that all documented formats are in supported list.
     */
    @Test
    void testAllDocumentedFormatsSupported() {
        var formats = ProcessExporterFactory.supportedFormats();

        assertTrue(formats.contains("CAMUNDA"), "Should support CAMUNDA format");
        assertTrue(formats.contains("BPEL"), "Should support BPEL format");
        assertTrue(formats.contains("TERRAFORM_AWS"), "Should support TERRAFORM_AWS format");
        assertTrue(formats.contains("TERRAFORM_AZURE"), "Should support TERRAFORM_AZURE format");
        assertTrue(formats.contains("TERRAFORM_GCP"), "Should support TERRAFORM_GCP format");
        assertTrue(formats.contains("KUBERNETES"), "Should support KUBERNETES format");
    }

    /**
     * Test case insensitivity of factory format parameter.
     */
    @Test
    void testFactoryFormatIsCaseInsensitive() {
        String bpmn1 = ProcessExporterFactory.export(simplePetriNet, "camunda");
        String bpmn2 = ProcessExporterFactory.export(simplePetriNet, "CAMUNDA");
        String bpmn3 = ProcessExporterFactory.export(simplePetriNet, "Camunda");

        assertTrue(bpmn1.contains("bpmn:definitions"), "Should work with lowercase");
        assertTrue(bpmn2.contains("bpmn:definitions"), "Should work with uppercase");
        assertTrue(bpmn3.contains("bpmn:definitions"), "Should work with mixed case");
    }

    /**
     * Test that exporters handle element IDs correctly.
     */
    @Test
    void testExportersPreserveElementIds() {
        CamundaBpmnExporter bpmnExporter = new CamundaBpmnExporter();
        String bpmn = bpmnExporter.export(simplePetriNet);

        assertTrue(bpmn.contains("test_process"), "Should preserve process ID");
        assertTrue(bpmn.contains("t_begin") || bpmn.contains("Begin"), "Should preserve transition IDs or names");
        assertTrue(bpmn.contains("t_finish") || bpmn.contains("Finish"), "Should preserve transition IDs or names");
    }

    /**
     * Test that exporters handle element names correctly.
     */
    @Test
    void testExportersPreserveElementNames() {
        CamundaBpmnExporter bpmnExporter = new CamundaBpmnExporter();
        String bpmn = bpmnExporter.export(simplePetriNet);

        assertTrue(bpmn.contains("Test Process"), "Should preserve process name");
        assertTrue(bpmn.contains("Begin Task"), "Should preserve transition names");
        assertTrue(bpmn.contains("Finish Task"), "Should preserve transition names");
    }

    /**
     * Test that Camunda exporter handles multiple transitions.
     */
    @Test
    void testCamundaHandlesMultipleTransitions() {
        // Create a more complex net with 5 transitions
        PetriNet complexNet = new PetriNet("complex_process", "Complex Process");

        Place p1 = new Place("p1", "Start");
        Place p2 = new Place("p2", "Middle1");
        Place p3 = new Place("p3", "Middle2");
        Place p4 = new Place("p4", "End");

        Transition t1 = new Transition("t1", "Task 1");
        Transition t2 = new Transition("t2", "Task 2");
        Transition t3 = new Transition("t3", "Task 3");

        complexNet.addPlace(p1);
        complexNet.addPlace(p2);
        complexNet.addPlace(p3);
        complexNet.addPlace(p4);

        complexNet.addTransition(t1);
        complexNet.addTransition(t2);
        complexNet.addTransition(t3);

        Arc a1 = new Arc("a1", p1, t1);
        Arc a2 = new Arc("a2", t1, p2);
        Arc a3 = new Arc("a3", p2, t2);
        Arc a4 = new Arc("a4", t2, p3);
        Arc a5 = new Arc("a5", p3, t3);
        Arc a6 = new Arc("a6", t3, p4);

        complexNet.addArc(a1);
        complexNet.addArc(a2);
        complexNet.addArc(a3);
        complexNet.addArc(a4);
        complexNet.addArc(a5);
        complexNet.addArc(a6);

        CamundaBpmnExporter exporter = new CamundaBpmnExporter();
        String bpmn = exporter.export(complexNet);

        assertTrue(bpmn.contains("Task 1"), "Should contain Task 1");
        assertTrue(bpmn.contains("Task 2"), "Should contain Task 2");
        assertTrue(bpmn.contains("Task 3"), "Should contain Task 3");
        assertTrue(countOccurrences(bpmn, "bpmn:serviceTask") >= 3, "Should have at least 3 service tasks");
    }

    /**
     * Test that BPEL exporter handles multiple transitions.
     */
    @Test
    void testBpelHandlesMultipleTransitions() {
        // Create a more complex net
        PetriNet complexNet = new PetriNet("complex_process", "Complex Process");

        Place p1 = new Place("p1", "Start");
        Place p2 = new Place("p2", "Middle1");
        Place p3 = new Place("p3", "Middle2");
        Place p4 = new Place("p4", "End");

        Transition t1 = new Transition("t1", "Task 1");
        Transition t2 = new Transition("t2", "Task 2");
        Transition t3 = new Transition("t3", "Task 3");

        complexNet.addPlace(p1);
        complexNet.addPlace(p2);
        complexNet.addPlace(p3);
        complexNet.addPlace(p4);

        complexNet.addTransition(t1);
        complexNet.addTransition(t2);
        complexNet.addTransition(t3);

        Arc a1 = new Arc("a1", p1, t1);
        Arc a2 = new Arc("a2", t1, p2);
        Arc a3 = new Arc("a3", p2, t2);
        Arc a4 = new Arc("a4", t2, p3);
        Arc a5 = new Arc("a5", p3, t3);
        Arc a6 = new Arc("a6", t3, p4);

        complexNet.addArc(a1);
        complexNet.addArc(a2);
        complexNet.addArc(a3);
        complexNet.addArc(a4);
        complexNet.addArc(a5);
        complexNet.addArc(a6);

        BpelExporter exporter = new BpelExporter();
        String bpel = exporter.export(complexNet);

        assertTrue(countOccurrences(bpel, "bpel:invoke") >= 3, "Should have at least 3 invoke activities");
        assertTrue(bpel.contains("Task 1"), "Should contain Task 1 name");
        assertTrue(bpel.contains("Task 2"), "Should contain Task 2 name");
        assertTrue(bpel.contains("Task 3"), "Should contain Task 3 name");
    }

    /**
     * Helper method to count occurrences of a substring.
     */
    private int countOccurrences(String text, String substring) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }
}
