package org.yawlfoundation.yawl.patternmatching;

import junit.framework.TestCase;
import org.yawlfoundation.yawl.elements.*;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;
import org.yawlfoundation.yawl.schema.YSchemaVersion;

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
public class InstanceofPatternTest extends TestCase {

    private YSpecification spec;
    private YNet rootNet;
    private YNet subNet;
    private YAWLServiceGateway gateway;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
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
    public void testToXML_NetPattern_RootNet() {
        String xml = spec.toXML();
        assertNotNull("XML should not be null", xml);

        // Root net should be marked as root
        assertTrue("Root net should have isRootNet=true",
                  xml.contains("isRootNet=\"true\""));
        assertTrue("Root net should be NetFactsType",
                  xml.contains("xsi:type=\"NetFactsType\""));
        assertTrue("Root net should be in XML",
                  xml.contains("id=\"root-net\""));
    }

    public void testToXML_NetPattern_SubNet() {
        String xml = spec.toXML();
        assertNotNull("XML should not be null", xml);

        // Sub net should be NetFactsType but not root
        assertTrue("Sub net should be NetFactsType",
                  xml.contains("xsi:type=\"NetFactsType\""));
        assertTrue("Sub net should be in XML",
                  xml.contains("id=\"sub-net\""));

        // Count isRootNet - should only be one
        int rootNetCount = countOccurrences(xml, "isRootNet=\"true\"");
        assertEquals("Should have exactly one root net", 1, rootNetCount);
    }

    public void testToXML_NotNetPattern_Gateway() {
        String xml = spec.toXML();
        assertNotNull("XML should not be null", xml);

        // Gateway should be WebServiceGatewayFactsType
        assertTrue("Gateway should be WebServiceGatewayFactsType",
                  xml.contains("xsi:type=\"WebServiceGatewayFactsType\""));
        assertTrue("Gateway should be in XML",
                  xml.contains("id=\"gateway-decomp\""));
    }

    public void testToXML_MixedDecompositions() {
        String xml = spec.toXML();

        // Verify all decompositions are present
        assertTrue("Should contain root net", xml.contains("id=\"root-net\""));
        assertTrue("Should contain sub net", xml.contains("id=\"sub-net\""));
        assertTrue("Should contain gateway", xml.contains("id=\"gateway-decomp\""));

        // Verify correct types
        int netTypeCount = countOccurrences(xml, "NetFactsType");
        int gatewayTypeCount = countOccurrences(xml, "WebServiceGatewayFactsType");

        assertEquals("Should have 2 NetFactsType", 2, netTypeCount);
        assertEquals("Should have 1 WebServiceGatewayFactsType", 1, gatewayTypeCount);
    }

    public void testToXML_DecompositionSorting_NetsFirst() throws YPersistenceException {
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
        assertTrue("Sub net should come before gateways", subPos < gatewayPos);
        assertTrue("a-net should come before gateways", aNetPos < aGatewayPos);
        assertTrue("z-net should come before gateways", zNetPos < zGatewayPos);

        // Within nets, should be sorted by ID (after root)
        assertTrue("a-net should come before sub-net", aNetPos < subPos);
        assertTrue("sub-net should come before z-net", subPos < zNetPos);

        // Within gateways, should be sorted by ID
        assertTrue("a-gateway should come before gateway-decomp", aGatewayPos < gatewayPos);
        assertTrue("gateway-decomp should come before z-gateway", gatewayPos < zGatewayPos);
    }

    // Test instanceof with null decomposition ID
    public void testToXML_NullDecompositionID() throws YPersistenceException {
        // Create decomposition with null ID (edge case)
        YNet netWithNullId = new YNet(null, spec);
        spec.addDecomposition(netWithNullId);

        String xml = spec.toXML();
        assertNotNull("XML should not be null even with null ID", xml);

        // Should still generate valid XML structure
        assertTrue("Should contain decomposition element",
                  xml.contains("<decomposition"));
    }

    // Test multiple instanceof patterns in same method
    public void testToXML_MultiplePatternMatches() {
        // The toXML method has two instanceof patterns:
        // 1. In the comparator (d1 instanceof YNet, d2 instanceof YNet)
        // 2. In the loop (decomposition instanceof YNet)

        String xml = spec.toXML();

        // Both YNets and gateways should be properly typed
        assertTrue("Should have NetFactsType for nets",
                  xml.contains("xsi:type=\"NetFactsType\""));
        assertTrue("Should have WebServiceGatewayFactsType for gateways",
                  xml.contains("xsi:type=\"WebServiceGatewayFactsType\""));
    }

    // Test instanceof with codelet (gateway-specific behavior)
    public void testToXML_GatewayWithCodelet() {
        gateway.setCodelet("test.codelet.Class");
        String xml = spec.toXML();

        assertTrue("Gateway with codelet should have codelet element",
                  xml.contains("<codelet>test.codelet.Class</codelet>"));
    }

    public void testToXML_GatewayWithoutCodelet() {
        gateway.setCodelet(null);
        String xml = spec.toXML();

        assertFalse("Gateway without codelet should not have codelet element",
                   xml.contains("<codelet>"));
    }

    // Test externalInteraction element (gateway-specific, not for nets)
    public void testToXML_GatewayExternalInteraction_Manual() {
        gateway.setManualInteraction(true);
        String xml = spec.toXML();

        assertTrue("Gateway requiring manual interaction should have manual tag",
                  xml.contains("<externalInteraction>manual</externalInteraction>"));
    }

    public void testToXML_GatewayExternalInteraction_Automated() {
        gateway.setManualInteraction(false);
        String xml = spec.toXML();

        assertTrue("Gateway not requiring manual interaction should have automated tag",
                  xml.contains("<externalInteraction>automated</externalInteraction>"));
    }

    public void testToXML_NetNoExternalInteraction() {
        String xml = spec.toXML();

        // Count externalInteraction elements - should only be for gateway
        int count = countOccurrences(xml, "<externalInteraction>");

        assertEquals("Should have externalInteraction only for gateway", 1, count);
    }

    // Edge case: Empty specification
    public void testToXML_OnlyRootNet() {
        YSpecification minimalSpec = new YSpecification("minimal");
        minimalSpec.setVersion(YSchemaVersion.FourPointZero);
        YNet minimal = new YNet("minimal-net", minimalSpec);
        minimalSpec.setRootNet(minimal);

        String xml = minimalSpec.toXML();
        assertNotNull("Minimal spec XML should not be null", xml);
        assertTrue("Should contain root net", xml.contains("id=\"minimal-net\""));
        assertTrue("Should have isRootNet", xml.contains("isRootNet=\"true\""));
    }

    // Edge case: Pattern matching with beta version (no externalInteraction)
    public void testToXML_BetaVersionNoExternalInteraction() {
        YSpecification betaSpec = new YSpecification("beta-spec");
        betaSpec.setVersion(YSchemaVersion.Beta7);
        YNet betaNet = new YNet("beta-net", betaSpec);
        betaSpec.setRootNet(betaNet);
        YAWLServiceGateway betaGateway = new YAWLServiceGateway("beta-gateway", betaSpec);
        betaSpec.addDecomposition(betaGateway);

        String xml = betaSpec.toXML();

        // Beta versions should NOT have externalInteraction element
        assertFalse("Beta version should not have externalInteraction",
                   xml.contains("<externalInteraction>"));
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
    public void testToXML_ComparatorPatternMatching() throws YPersistenceException {
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
        assertTrue("Net a-second should come before gateway z-first", n1Pos < g1Pos);
        assertTrue("Net a-second should come before gateway a-third", n1Pos < g2Pos);
        assertTrue("Net z-fourth should come before gateway z-first", n2Pos < g1Pos);
        assertTrue("Net z-fourth should come before gateway a-third", n2Pos < g2Pos);

        // Within each group, should be sorted alphabetically
        assertTrue("Net a-second should come before net z-fourth", n1Pos < n2Pos);
        assertTrue("Gateway a-third should come before gateway z-first", g2Pos < g1Pos);
    }
}
