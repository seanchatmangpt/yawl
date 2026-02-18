/*
 * Copyright (c) 2004-2025 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.patternmatching;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.jdom2.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.elements.YAWLServiceGateway;
import org.yawlfoundation.yawl.elements.YAtomicTask;
import org.yawlfoundation.yawl.elements.YCompositeTask;
import org.yawlfoundation.yawl.elements.YDecomposition;
import org.yawlfoundation.yawl.elements.YInputCondition;
import org.yawlfoundation.yawl.elements.YNet;
import org.yawlfoundation.yawl.elements.YOutputCondition;
import org.yawlfoundation.yawl.elements.YSpecification;
import org.yawlfoundation.yawl.elements.YTask;
import org.yawlfoundation.yawl.engine.YSpecificationID;
import org.yawlfoundation.yawl.engine.YWorkItemStatus;
import org.yawlfoundation.yawl.exceptions.YSyntaxException;
import org.yawlfoundation.yawl.schema.YSchemaVersion;
import org.yawlfoundation.yawl.unmarshal.YMarshal;
import org.yawlfoundation.yawl.unmarshal.YMetaData;

/**
 * Integration tests for pattern matching across YAWL subsystems.
 *
 * Tests cover:
 * - End-to-end workflow pattern matching
 * - Nested patterns in complex specifications
 * - Full specification roundtrip with pattern matching
 *
 * Chicago TDD: All tests use real objects and XML processing, no mocks.
 *
 * @author YAWL Development Team
 * @since 5.2
 */
@DisplayName("Pattern Matching Integration Tests")
@Tag("integration")
class PatternMatchingIntegrationTest {

    private YSpecification specification;

    @BeforeEach
    void setUp() throws YSyntaxException {
        specification = createComplexSpecification();
    }

    // ============================================================
    // End-to-End Workflow Tests
    // ============================================================

    @Nested
    @DisplayName("End-to-End Workflow Pattern Matching")
    class EndToEndWorkflowTests {

        @Test
        @DisplayName("Specification toXML uses pattern matching for all decompositions")
        void specificationToXmlUsesPatternMatchingForAllDecompositions() {
            String xml = specification.toXML();

            assertNotNull(xml);
            // Root net should have isRootNet attribute
            assertTrue(xml.contains("isRootNet=\"true\""));
            // All decompositions should have xsi:type
            assertTrue(xml.contains("NetFactsType"));
            assertTrue(xml.contains("WebServiceGatewayFactsType"));
        }

        @Test
        @DisplayName("Pattern matching handles all task types")
        void patternMatchingHandlesAllTaskTypes() {
            YNet rootNet = specification.getRootNet();

            int atomicCount = 0;
            int compositeCount = 0;

            for (var element : rootNet.getNetElements()) {
                if (element instanceof YAtomicTask) {
                    atomicCount++;
                } else if (element instanceof YCompositeTask) {
                    compositeCount++;
                }
            }

            assertTrue(atomicCount > 0, "Should have atomic tasks");
            assertTrue(compositeCount > 0, "Should have composite tasks");
        }

        @Test
        @DisplayName("Pattern matching in specification ID equals")
        void patternMatchingInSpecificationIdEquals() {
            YSpecificationID id1 = new YSpecificationID("spec-uri", "4.0", "owner");
            YSpecificationID id2 = new YSpecificationID("spec-uri", "4.0", "owner");
            YSpecificationID id3 = new YSpecificationID("different-uri", "4.0", "owner");

            // Uses pattern matching in equals (Object parameter pattern)
            assertEquals(id1, id2);
            assertNotEquals(id1, id3);
            assertNotEquals(id1, null);
            assertNotEquals(id1, "not-a-spec-id");
        }

        @Test
        @DisplayName("Pattern matching handles null gracefully")
        void patternMatchingHandlesNullGracefully() {
            YSpecificationID id = new YSpecificationID("uri", "4.0", "owner");

            // Pattern matching with null should return false
            assertFalse(id.equals(null));
        }

        @Test
        @DisplayName("Work item status pattern matching")
        void workItemStatusPatternMatching() {
            // Test status value handling (used in switch expressions)
            YWorkItemStatus status = YWorkItemStatus.statusEnabled;

            String statusCategory = categorizeStatus(status);
            assertEquals("active", statusCategory);

            statusCategory = categorizeStatus(YWorkItemStatus.statusComplete);
            assertEquals("completed", statusCategory);

            statusCategory = categorizeStatus(YWorkItemStatus.statusFailed);
            assertEquals("terminated", statusCategory);
        }
    }

    // ============================================================
    // Nested Pattern Tests
    // ============================================================

    @Nested
    @DisplayName("Nested Patterns in Complex Specifications")
    class NestedPatternTests {

        @Test
        @DisplayName("Nested nets have correct types in XML")
        void nestedNetsHaveCorrectTypesInXml() throws YSyntaxException {
            YSpecification spec = createNestedSpecification();
            String xml = spec.toXML();

            // Root net
            assertTrue(xml.contains("isRootNet=\"true\""));

            // Count NetFactsType occurrences
            int netCount = countOccurrences(xml, "NetFactsType");
            assertTrue(netCount >= 2, "Should have at least 2 nets (root + nested)");
        }

        @Test
        @DisplayName("Composite task references correct decomposition")
        void compositeTaskReferencesCorrectDecomposition() {
            YNet rootNet = specification.getRootNet();

            for (var element : rootNet.getNetElements()) {
                if (element instanceof YCompositeTask composite) {
                    YDecomposition decomp = composite.getDecompositionPrototype();
                    assertNotNull(decomp, "Composite task should have decomposition");
                    assertTrue(decomp instanceof YNet, "Decomposition should be a net");
                }
            }
        }

        @Test
        @DisplayName("Gateway with codelet serialization")
        void gatewayWithCodeletSerialization() throws YSyntaxException {
            YSpecification spec = createTestSpecification("codelet-test");
            YAWLServiceGateway gateway = new YAWLServiceGateway("test-gateway", spec);
            gateway.setCodelet("com.example.TestCodelet");
            spec.addDecomposition(gateway);

            String xml = spec.toXML();

            assertTrue(xml.contains("<codelet>com.example.TestCodelet</codelet>"));
        }

        @Test
        @DisplayName("Multiple gateway types in same specification")
        void multipleGatewayTypesInSameSpecification() throws YSyntaxException {
            YSpecification spec = createTestSpecification("multi-gateway");

            YAWLServiceGateway gateway1 = new YAWLServiceGateway("gateway1", spec);
            gateway1.setExternalInteraction(true);
            spec.addDecomposition(gateway1);

            YAWLServiceGateway gateway2 = new YAWLServiceGateway("gateway2", spec);
            gateway2.setExternalInteraction(false);
            gateway2.setCodelet("codelet.Class");
            spec.addDecomposition(gateway2);

            String xml = spec.toXML();

            assertTrue(xml.contains("<externalInteraction>manual</externalInteraction>"));
            assertTrue(xml.contains("<externalInteraction>automated</externalInteraction>"));
            assertTrue(xml.contains("<codelet>codelet.Class</codelet>"));
        }

        @Test
        @DisplayName("Deep nesting preserves pattern match correctness")
        void deepNestingPreservesPatternMatchCorrectness() throws YSyntaxException {
            YSpecification spec = createDeeplyNestedSpecification(5);

            String xml = spec.toXML();

            // Should serialize without errors
            assertNotNull(xml);
            assertTrue(xml.contains("specification"));
        }
    }

    // ============================================================
    // Full Specification Roundtrip Tests
    // ============================================================

    @Nested
    @DisplayName("Full Specification Roundtrip")
    class FullSpecificationRoundtripTests {

        @Test
        @DisplayName("Marshal and unmarshal preserves decomposition types")
        void marshalAndUnmarshalPreservesDecompositionTypes() throws YSyntaxException {
            String xml = specification.toXML();
            List<YSpecification> specs = YMarshal.unmarshalSpecifications(xml, false);

            assertEquals(1, specs.size());

            YSpecification restored = specs.get(0);
            assertNotNull(restored.getRootNet());

            // Verify decomposition types are preserved
            for (YDecomposition decomp : restored.getDecompositions()) {
                if (decomp.getID().equals(restored.getRootNet().getID())) {
                    assertTrue(decomp instanceof YNet);
                }
            }
        }

        @Test
        @DisplayName("Roundtrip preserves specification ID")
        void roundtripPreservesSpecificationId() throws YSyntaxException {
            String originalXml = specification.toXML();

            List<YSpecification> specs = YMarshal.unmarshalSpecifications(originalXml, false);
            YSpecification restored = specs.get(0);

            String restoredXml = restored.toXML();

            // Both should contain the spec ID
            assertTrue(originalXml.contains("complex-spec"));
            assertTrue(restoredXml.contains("complex-spec"));
        }

        @Test
        @DisplayName("Roundtrip preserves task configuration")
        void roundtripPreservesTaskConfiguration() throws YSyntaxException {
            YNet originalNet = specification.getRootNet();
            int originalTaskCount = 0;
            for (var element : originalNet.getNetElements()) {
                if (element instanceof YTask) {
                    originalTaskCount++;
                }
            }

            String xml = specification.toXML();
            List<YSpecification> specs = YMarshal.unmarshalSpecifications(xml, false);
            YSpecification restored = specs.get(0);

            YNet restoredNet = restored.getRootNet();
            int restoredTaskCount = 0;
            for (var element : restoredNet.getNetElements()) {
                if (element instanceof YTask) {
                    restoredTaskCount++;
                }
            }

            assertEquals(originalTaskCount, restoredTaskCount);
        }

        @Test
        @DisplayName("Multiple roundtrips produce consistent output")
        void multipleRoundtripsProduceConsistentOutput() throws YSyntaxException {
            String xml1 = specification.toXML();

            List<YSpecification> specs1 = YMarshal.unmarshalSpecifications(xml1, false);
            String xml2 = specs1.get(0).toXML();

            List<YSpecification> specs2 = YMarshal.unmarshalSpecifications(xml2, false);
            String xml3 = specs2.get(0).toXML();

            // Second and third roundtrip should be equivalent
            // (after initial normalization)
            assertEquals(
                countOccurrences(xml2, "decomposition"),
                countOccurrences(xml3, "decomposition")
            );
        }

        @Test
        @DisplayName("External interaction preserved in roundtrip")
        void externalInteractionPreservedInRoundtrip() throws YSyntaxException {
            YSpecification spec = createTestSpecification("ext-test");

            YAWLServiceGateway gateway = new YAWLServiceGateway("ext-gateway", spec);
            gateway.setExternalInteraction(true);
            spec.addDecomposition(gateway);

            String xml = spec.toXML();
            List<YSpecification> restored = YMarshal.unmarshalSpecifications(xml, false);

            YAWLServiceGateway restoredGateway = (YAWLServiceGateway)
                restored.get(0).getDecomposition("ext-gateway");

            assertTrue(restoredGateway.requiresManualInteraction());
        }

        @Test
        @DisplayName("Beta version serialization differs from release")
        void betaVersionSerializationDiffersFromRelease() throws YSyntaxException {
            YSpecification releaseSpec = createTestSpecification("release-spec");
            releaseSpec.setVersion(YSchemaVersion.FourPointZero);

            YSpecification betaSpec = createTestSpecification("beta-spec");
            betaSpec.setVersion(YSchemaVersion.Beta7);

            YAWLServiceGateway gateway1 = new YAWLServiceGateway("gw1", releaseSpec);
            gateway1.setExternalInteraction(true);
            releaseSpec.addDecomposition(gateway1);

            YAWLServiceGateway gateway2 = new YAWLServiceGateway("gw2", betaSpec);
            gateway2.setExternalInteraction(true);
            betaSpec.addDecomposition(gateway2);

            String releaseXml = releaseSpec.toXML();
            String betaXml = betaSpec.toXML();

            // Release should have externalInteraction
            assertTrue(releaseXml.contains("<externalInteraction>"));

            // Beta should NOT have externalInteraction
            assertFalse(betaXml.contains("<externalInteraction>"));
        }
    }

    // ============================================================
    // Cross-Subsystem Pattern Matching Tests
    // ============================================================

    @Nested
    @DisplayName("Cross-Subsystem Pattern Matching")
    class CrossSubsystemPatternMatchingTests {

        @Test
        @DisplayName("Pattern matching in YDataStateException unmarshalling")
        void patternMatchingInYDataStateExceptionUnmarshalling() throws Exception {
            org.jdom2.Document doc = new org.jdom2.Document();
            Element root = new Element("YDataQueryException");
            root.addContent(new Element("message").setText("Query failed"));
            root.addContent(new Element("queryString").setText("//test"));
            root.addContent(new Element("source").setText("TestTask"));
            doc.setRootElement(root);

            org.yawlfoundation.yawl.exceptions.YAWLException ex =
                org.yawlfoundation.yawl.exceptions.YAWLException.unmarshal(doc);

            assertTrue(ex instanceof org.yawlfoundation.yawl.exceptions.YDataQueryException);
        }

        @Test
        @DisplayName("Pattern matching in YAWLException.rethrow")
        void patternMatchingInYawlExceptionRethrow() {
            org.yawlfoundation.yawl.exceptions.YStateException stateEx =
                new org.yawlfoundation.yawl.exceptions.YStateException("State error");

            assertThrows(org.yawlfoundation.yawl.exceptions.YStateException.class, () -> {
                stateEx.rethrow();
            });
        }

        @Test
        @DisplayName("Pattern matching in specification element sorting")
        void patternMatchingInSpecificationElementSorting() throws YSyntaxException {
            YSpecification spec = createTestSpecification("sort-test");

            // Add decompositions in random order
            YNet netZ = new YNet("z-net", spec);
            netZ.setInputCondition(new YInputCondition("input", netZ));
            netZ.setOutputCondition(new YOutputCondition("output", netZ));
            spec.addDecomposition(netZ);

            YAWLServiceGateway gwA = new YAWLServiceGateway("a-gateway", spec);
            spec.addDecomposition(gwA);

            YNet netA = new YNet("a-net", spec);
            netA.setInputCondition(new YInputCondition("input", netA));
            netA.setOutputCondition(new YOutputCondition("output", netA));
            spec.addDecomposition(netA);

            String xml = spec.toXML();

            // Nets should come before gateways
            int aNetPos = xml.indexOf("id=\"a-net\"");
            int zNetPos = xml.indexOf("id=\"z-net\"");
            int gwPos = xml.indexOf("id=\"a-gateway\"");

            assertTrue(aNetPos < gwPos, "Net should come before gateway");
            assertTrue(zNetPos < gwPos, "Net should come before gateway");
        }
    }

    // ============================================================
    // Edge Cases and Error Handling
    // ============================================================

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesTests {

        @Test
        @DisplayName("Empty specification serializes correctly")
        void emptySpecificationSerializesCorrectly() throws YSyntaxException {
            YSpecification spec = createTestSpecification("empty-spec");

            String xml = spec.toXML();

            assertNotNull(xml);
            assertTrue(xml.contains("empty-spec"));
        }

        @Test
        @DisplayName("Specification with only root net serializes")
        void specificationWithOnlyRootNetSerializes() throws YSyntaxException {
            YSpecification spec = createTestSpecification("minimal-spec");

            String xml = spec.toXML();

            assertTrue(xml.contains("isRootNet=\"true\""));
            assertTrue(xml.contains("NetFactsType"));
        }

        @Test
        @DisplayName("Null decomposition ID throws exception")
        void nullDecompositionIdThrowsException() {
            assertThrows(IllegalArgumentException.class, () -> {
                new YNet(null, specification);
            });
        }

        @Test
        @DisplayName("Special characters in specification ID")
        void specialCharactersInSpecificationId() throws YSyntaxException {
            YSpecification spec = createTestSpecification("spec-with-special_chars.123");

            String xml = spec.toXML();

            assertTrue(xml.contains("spec-with-special_chars.123"));
        }
    }

    // ============================================================
    // Helper Methods
    // ============================================================

    private YSpecification createTestSpecification(String specId) throws YSyntaxException {
        YSpecification spec = new YSpecification(specId);
        spec.setVersion(YSchemaVersion.FourPointZero);
        spec.setMetaData(new YMetaData());
        spec.setSchema("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>");

        YNet net = new YNet("root", spec);
        YInputCondition input = new YInputCondition("input", net);
        YOutputCondition output = new YOutputCondition("output", net);
        net.setInputCondition(input);
        net.setOutputCondition(output);
        net.addNetElement(input);
        net.addNetElement(output);
        spec.setRootNet(net);

        return spec;
    }

    private YSpecification createComplexSpecification() throws YSyntaxException {
        YSpecification spec = new YSpecification("complex-spec");
        spec.setVersion(YSchemaVersion.FourPointZero);
        spec.setMetaData(new YMetaData());
        spec.setSchema("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>");

        // Root net
        YNet rootNet = new YNet("root-net", spec);
        YInputCondition input = new YInputCondition("input", rootNet);
        YOutputCondition output = new YOutputCondition("output", rootNet);
        rootNet.setInputCondition(input);
        rootNet.setOutputCondition(output);
        rootNet.addNetElement(input);
        rootNet.addNetElement(output);

        // Add atomic task
        YAtomicTask atomicTask = new YAtomicTask("atomic-task", YTask._AND, YTask._AND, rootNet);
        rootNet.addNetElement(atomicTask);

        // Add composite task with sub-net
        YNet subNet = new YNet("sub-net", spec);
        YInputCondition subInput = new YInputCondition("sub-input", subNet);
        YOutputCondition subOutput = new YOutputCondition("sub-output", subNet);
        subNet.setInputCondition(subInput);
        subNet.setOutputCondition(subOutput);
        subNet.addNetElement(subInput);
        subNet.addNetElement(subOutput);
        spec.addDecomposition(subNet);

        YCompositeTask compositeTask = new YCompositeTask("composite-task", YTask._AND, YTask._AND, rootNet);
        compositeTask.setDecompositionPrototype(subNet);
        rootNet.addNetElement(compositeTask);

        spec.setRootNet(rootNet);

        // Add gateway
        YAWLServiceGateway gateway = new YAWLServiceGateway("service-gateway", spec);
        spec.addDecomposition(gateway);

        return spec;
    }

    private YSpecification createNestedSpecification() throws YSyntaxException {
        YSpecification spec = createTestSpecification("nested-spec");

        YNet subNet = new YNet("sub-net", spec);
        subNet.setInputCondition(new YInputCondition("sub-input", subNet));
        subNet.setOutputCondition(new YOutputCondition("sub-output", subNet));
        spec.addDecomposition(subNet);

        return spec;
    }

    private YSpecification createDeeplyNestedSpecification(int depth) throws YSyntaxException {
        YSpecification spec = createTestSpecification("deep-spec");

        YNet currentNet = spec.getRootNet();
        for (int i = 0; i < depth; i++) {
            YNet subNet = new YNet("level-" + i, spec);
            subNet.setInputCondition(new YInputCondition("input-" + i, subNet));
            subNet.setOutputCondition(new YOutputCondition("output-" + i, subNet));
            spec.addDecomposition(subNet);

            YCompositeTask task = new YCompositeTask("task-" + i, YTask._AND, YTask._AND, currentNet);
            task.setDecompositionPrototype(subNet);
            currentNet.addNetElement(task);

            currentNet = subNet;
        }

        return spec;
    }

    private String categorizeStatus(YWorkItemStatus status) {
        if (status == null) return "unknown";

        String name = status.toString();
        if (name.contains("Enabled") || name.contains("Executing") || name.contains("Fired")) {
            return "active";
        } else if (name.contains("Complete") || name.contains("Finished")) {
            return "completed";
        } else {
            return "terminated";
        }
    }

    private int countOccurrences(String str, String substring) {
        int count = 0;
        int index = 0;
        while ((index = str.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }
}
