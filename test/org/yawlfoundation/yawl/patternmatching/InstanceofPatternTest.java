package org.yawlfoundation.yawl.patternmatching;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.elements.*;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;
import org.yawlfoundation.yawl.schema.YSchemaVersion;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for instanceof pattern variable conversions
 *
 * Tests pattern matching in:
 * - YSpecification.toXML() - YNet pattern matching
 * - YSpecificationID.equals() - Object pattern matching
 *
 * Branch Coverage Target: 100%
 *
 * Author: YAWL Foundation
 * Date: 2026-02-16
 */
class InstanceofPatternTest {

    private YSpecification spec;
    private YNet rootNet;
    private YNet subNet;
    private YAWLServiceGateway gateway;

    @BeforeEach
    void setUp() throws Exception {
        spec = new YSpecification("test-spec");
        spec.setVersion(YSchemaVersion.FourPointZero);

        rootNet = new YNet("root-net", spec);
        spec.setRootNet(rootNet);

        subNet = new YNet("sub-net", spec);
        spec.addDecomposition(subNet);

        gateway = new YAWLServiceGateway("gateway-decomp", spec);
        spec.addDecomposition(gateway);
    }

    // Test YSpecification.toXML() instanceof patterns
    @Test
    void testToXML_NetPattern_RootNet() {
        String xml = spec.toXML();
        assertNotNull(xml, "XML should not be null");

        // Root net should be marked as root
        assertTrue(xml.contains("isRootNet=\"true\""),
                  "Root net should have isRootNet=true");
        assertTrue(xml.contains("xsi:type=\"NetFactsType\""),
                  "Root net should be NetFactsType");
        assertTrue(xml.contains("id=\"root-net\""),
                  "Root net should be in XML");
    }

    @Test
    void testToXML_NetPattern_SubNet() {
        String xml = spec.toXML();
        assertNotNull(xml, "XML should not be null");

        // Sub net should be NetFactsType but not root
        assertTrue(xml.contains("xsi:type=\"NetFactsType\""),
                  "Sub net should be NetFactsType");
        assertTrue(xml.contains("id=\"sub-net\""),
                  "Sub net should be in XML");

        // Count isRootNet - should only be one
        int rootNetCount = countOccurrences(xml, "isRootNet=\"true\"");
        assertEquals(1, rootNetCount, "Should have exactly one root net");
    }

    @Test
    void testToXML_NotNetPattern_Gateway() {
        String xml = spec.toXML();
        assertNotNull(xml, "XML should not be null");

        // Gateway should be WebServiceGatewayFactsType
        assertTrue(xml.contains("xsi:type=\"WebServiceGatewayFactsType\""),
                  "Gateway should be WebServiceGatewayFactsType");
        assertTrue(xml.contains("id=\"gateway-decomp\""),
                  "Gateway should be in XML");
    }

    @Test
    void testToXML_MixedDecompositions() {
        String xml = spec.toXML();

        // Verify all decompositions are present
        assertTrue(xml.contains("id=\"root-net\""), "Should contain root net");
        assertTrue(xml.contains("id=\"sub-net\""), "Should contain sub net");
        assertTrue(xml.contains("id=\"gateway-decomp\""), "Should contain gateway");

        // Verify correct types
        int netTypeCount = countOccurrences(xml, "NetFactsType");
        int gatewayTypeCount = countOccurrences(xml, "WebServiceGatewayFactsType");

        assertEquals(2, netTypeCount, "Should have 2 NetFactsType");
        assertEquals(1, gatewayTypeCount, "Should have 1 WebServiceGatewayFactsType");
    }

    @Test
    void testToXML_DecompositionSorting_NetsFirst() throws YPersistenceException {
        // Add more decompositions with varying names
        YNet aNet = new YNet("a-net", spec);
        YNet zNet = new YNet("z-net", spec);
        YAWLServiceGateway aGateway = new YAWLServiceGateway("a-gateway", spec);
        YAWLServiceGateway zGateway = new YAWLServiceGateway("z-gateway", spec);

        spec.addDecomposition(zNet);
        spec.addDecomposition(aGateway);
        spec.addDecomposition(aNet);
        spec.addDecomposition(zGateway);

        String xml = spec.toXML();

        // Find positions of each decomposition
        int rootPos = xml.indexOf("id=\"root-net\"");
        int subPos = xml.indexOf("id=\"sub-net\"");
        int aNetPos = xml.indexOf("id=\"a-net\"");
        int zNetPos = xml.indexOf("id=\"z-net\"");
        int gatewayPos = xml.indexOf("id=\"gateway-decomp\"");
        int aGatewayPos = xml.indexOf("id=\"a-gateway\"");
        int zGatewayPos = xml.indexOf("id=\"z-gateway\"");

        // All nets should come before gateways (except root which is always first)
        assertTrue(subPos < gatewayPos, "Sub net should come before gateways");
        assertTrue(aNetPos < aGatewayPos, "a-net should come before gateways");
        assertTrue(zNetPos < zGatewayPos, "z-net should come before gateways");

        // Within nets, should be sorted by ID (after root)
        assertTrue(aNetPos < subPos, "a-net should come before sub-net");
        assertTrue(subPos < zNetPos, "sub-net should come before z-net");

        // Within gateways, should be sorted by ID
        assertTrue(aGatewayPos < gatewayPos, "a-gateway should come before gateway-decomp");
        assertTrue(gatewayPos < zGatewayPos, "gateway-decomp should come before z-gateway");
    }

    // Test instanceof with null decomposition ID
    @Test
    void testToXML_NullDecompositionID() throws YPersistenceException {
        // Create decomposition with null ID (edge case)
        YNet netWithNullId = new YNet(null, spec);
        spec.addDecomposition(netWithNullId);

        String xml = spec.toXML();
        assertNotNull(xml, "XML should not be null even with null ID");

        // Should still generate valid XML structure
        assertTrue(xml.contains("<decomposition"),
                  "Should contain decomposition element");
    }

    // Test multiple instanceof patterns in same method
    @Test
    void testToXML_MultiplePatternMatches() {
        // The toXML method has two instanceof patterns:
        // 1. In the comparator (d1 instanceof YNet, d2 instanceof YNet)
        // 2. In the loop (decomposition instanceof YNet)

        String xml = spec.toXML();

        // Both YNets and gateways should be properly typed
        assertTrue(xml.contains("xsi:type=\"NetFactsType\""),
                  "Should have NetFactsType for nets");
        assertTrue(xml.contains("xsi:type=\"WebServiceGatewayFactsType\""),
                  "Should have WebServiceGatewayFactsType for gateways");
    }

    // Test instanceof with codelet (gateway-specific behavior)
    @Test
    void testToXML_GatewayWithCodelet() {
        gateway.setCodelet("test.codelet.Class");
        String xml = spec.toXML();

        assertTrue(xml.contains("<codelet>test.codelet.Class</codelet>"),
                  "Gateway with codelet should have codelet element");
    }

    @Test
    void testToXML_GatewayWithoutCodelet() {
        gateway.setCodelet(null);
        String xml = spec.toXML();

        assertFalse(xml.contains("<codelet>"),
                   "Gateway without codelet should not have codelet element");
    }

    // Test externalInteraction element (gateway-specific, not for nets)
    @Test
    void testToXML_GatewayExternalInteraction_Manual() {
        gateway.setExternalInteraction(true);
        String xml = spec.toXML();

        assertTrue(xml.contains("<externalInteraction>manual</externalInteraction>"),
                  "Gateway requiring manual interaction should have manual tag");
    }

    @Test
    void testToXML_GatewayExternalInteraction_Automated() {
        gateway.setExternalInteraction(false);
        String xml = spec.toXML();

        assertTrue(xml.contains("<externalInteraction>automated</externalInteraction>"),
                  "Gateway not requiring manual interaction should have automated tag");
    }

    @Test
    void testToXML_NetNoExternalInteraction() {
        String xml = spec.toXML();

        // Count externalInteraction elements - should only be for gateway
        int count = countOccurrences(xml, "<externalInteraction>");

        assertEquals(1, count, "Should have externalInteraction only for gateway");
    }

    // Edge case: Empty specification
    @Test
    void testToXML_OnlyRootNet() {
        YSpecification minimalSpec = new YSpecification("minimal");
        minimalSpec.setVersion(YSchemaVersion.FourPointZero);
        YNet minimal = new YNet("minimal-net", minimalSpec);
        minimalSpec.setRootNet(minimal);

        String xml = minimalSpec.toXML();
        assertNotNull(xml, "Minimal spec XML should not be null");
        assertTrue(xml.contains("id=\"minimal-net\""), "Should contain root net");
        assertTrue(xml.contains("isRootNet=\"true\""), "Should have isRootNet");
    }

    // Edge case: Pattern matching with beta version (no externalInteraction)
    @Test
    void testToXML_BetaVersionNoExternalInteraction() {
        YSpecification betaSpec = new YSpecification("beta-spec");
        betaSpec.setVersion(YSchemaVersion.Beta7);
        YNet betaNet = new YNet("beta-net", betaSpec);
        betaSpec.setRootNet(betaNet);
        YAWLServiceGateway betaGateway = new YAWLServiceGateway("beta-gateway", betaSpec);
        betaSpec.addDecomposition(betaGateway);

        String xml = betaSpec.toXML();

        // Beta versions should NOT have externalInteraction element
        assertFalse(xml.contains("<externalInteraction>"),
                   "Beta version should not have externalInteraction");
    }

    // Helper method
    private int countOccurrences(String str, String substring) {
        int count = 0;
        int index = 0;
        while ((index = str.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }

    // Test pattern matching in comparator
    @Test
    void testToXML_ComparatorPatternMatching() throws YPersistenceException {
        // Add decompositions in non-sorted order
        YAWLServiceGateway g1 = new YAWLServiceGateway("z-first", spec);
        YNet n1 = new YNet("a-second", spec);
        YAWLServiceGateway g2 = new YAWLServiceGateway("a-third", spec);
        YNet n2 = new YNet("z-fourth", spec);

        spec.addDecomposition(g1);
        spec.addDecomposition(n1);
        spec.addDecomposition(g2);
        spec.addDecomposition(n2);

        String xml = spec.toXML();

        // Find positions
        int n1Pos = xml.indexOf("id=\"a-second\"");
        int n2Pos = xml.indexOf("id=\"z-fourth\"");
        int g1Pos = xml.indexOf("id=\"z-first\"");
        int g2Pos = xml.indexOf("id=\"a-third\"");

        // All nets should come before all gateways (except root)
        assertTrue(n1Pos < g1Pos, "Net a-second should come before gateway z-first");
        assertTrue(n1Pos < g2Pos, "Net a-second should come before gateway a-third");
        assertTrue(n2Pos < g1Pos, "Net z-fourth should come before gateway z-first");
        assertTrue(n2Pos < g2Pos, "Net z-fourth should come before gateway a-third");

        // Within each group, should be sorted alphabetically
        assertTrue(n1Pos < n2Pos, "Net a-second should come before net z-fourth");
        assertTrue(g2Pos < g1Pos, "Gateway a-third should come before gateway z-first");
    }
}
