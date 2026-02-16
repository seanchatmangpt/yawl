package org.yawlfoundation.yawl.patternmatching;

import junit.framework.TestCase;
import org.yawlfoundation.yawl.elements.*;
import org.yawlfoundation.yawl.exceptions.YPersistenceException;
import org.yawlfoundation.yawl.schema.YSchemaVersion;

/**
 * Tests for YSpecification pattern matching in toXML()
 *
 * Tests the instanceof pattern matching in YSpecification.toXML():
 * - Comparator pattern matching (sorting decompositions)
 * - Loop pattern matching (generating XML)
 * - Beta version vs release version behavior
 *
 * Branch Coverage Target: 100%
 *
 * Author: YAWL Foundation
 * Date: 2026-02-16
 */
public class YSpecificationPatternTest extends TestCase {

    private YSpecification spec;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        spec = new YSpecification("test-spec");
        spec.setVersion(YSchemaVersion.FourPointZero);
    }

    // Test basic pattern matching: net vs non-net
    public void testToXML_PatternMatching_NetVsGateway() throws YPersistenceException {
        YNet rootNet = new YNet("root", spec);
        spec.setRootNet(rootNet);

        YNet subnet = new YNet("subnet", spec);
        spec.addDecomposition(subnet);

        YAWLServiceGateway gateway = new YAWLServiceGateway("gateway", spec);
        spec.addDecomposition(gateway);

        String xml = spec.toXML();

        // Count NetFactsType (should be 2: root + subnet)
        int netCount = countOccurrences(xml, "NetFactsType");
        assertEquals("Should have 2 NetFactsType", 2, netCount);

        // Count WebServiceGatewayFactsType (should be 1)
        int gatewayCount = countOccurrences(xml, "WebServiceGatewayFactsType");
        assertEquals("Should have 1 WebServiceGatewayFactsType", 1, gatewayCount);
    }

    // Test comparator pattern: both are nets
    public void testToXML_Comparator_BothNets() throws YPersistenceException {
        YNet rootNet = new YNet("z-root", spec);
        spec.setRootNet(rootNet);

        YNet net1 = new YNet("b-net", spec);
        YNet net2 = new YNet("a-net", spec);
        YNet net3 = new YNet("c-net", spec);

        spec.addDecomposition(net3);
        spec.addDecomposition(net1);
        spec.addDecomposition(net2);

        String xml = spec.toXML();

        // Nets should be sorted alphabetically (after root)
        int aPos = xml.indexOf("id=\"a-net\"");
        int bPos = xml.indexOf("id=\"b-net\"");
        int cPos = xml.indexOf("id=\"c-net\"");

        assertTrue("a-net should come before b-net", aPos < bPos);
        assertTrue("b-net should come before c-net", bPos < cPos);
    }

    // Test comparator pattern: both are gateways
    public void testToXML_Comparator_BothGateways() throws YPersistenceException {
        YNet rootNet = new YNet("root", spec);
        spec.setRootNet(rootNet);

        YAWLServiceGateway g1 = new YAWLServiceGateway("z-gateway", spec);
        YAWLServiceGateway g2 = new YAWLServiceGateway("a-gateway", spec);
        YAWLServiceGateway g3 = new YAWLServiceGateway("m-gateway", spec);

        spec.addDecomposition(g1);
        spec.addDecomposition(g2);
        spec.addDecomposition(g3);

        String xml = spec.toXML();

        // Gateways should be sorted alphabetically
        int aPos = xml.indexOf("id=\"a-gateway\"");
        int mPos = xml.indexOf("id=\"m-gateway\"");
        int zPos = xml.indexOf("id=\"z-gateway\"");

        assertTrue("a-gateway should come before m-gateway", aPos < mPos);
        assertTrue("m-gateway should come before z-gateway", mPos < zPos);
    }

    // Test comparator pattern: net vs gateway (net comes first)
    public void testToXML_Comparator_NetBeforeGateway() throws YPersistenceException {
        YNet rootNet = new YNet("root", spec);
        spec.setRootNet(rootNet);

        YNet net = new YNet("z-net", spec);
        YAWLServiceGateway gateway = new YAWLServiceGateway("a-gateway", spec);

        spec.addDecomposition(gateway);
        spec.addDecomposition(net);

        String xml = spec.toXML();

        // Net should come before gateway despite alphabetical order
        int netPos = xml.indexOf("id=\"z-net\"");
        int gatewayPos = xml.indexOf("id=\"a-gateway\"");

        assertTrue("Net should come before gateway regardless of name", netPos < gatewayPos);
    }

    // Test comparator pattern: gateway vs net (net comes first)
    public void testToXML_Comparator_GatewayVsNet() throws YPersistenceException {
        YNet rootNet = new YNet("root", spec);
        spec.setRootNet(rootNet);

        YAWLServiceGateway gateway = new YAWLServiceGateway("a-gateway", spec);
        YNet net = new YNet("z-net", spec);

        spec.addDecomposition(gateway);
        spec.addDecomposition(net);

        String xml = spec.toXML();

        // Net should come before gateway
        int netPos = xml.indexOf("id=\"z-net\"");
        int gatewayPos = xml.indexOf("id=\"a-gateway\"");

        assertTrue("Net should come before gateway", netPos < gatewayPos);
    }

    // Test loop pattern: gateway with codelet
    public void testToXML_LoopPattern_GatewayWithCodelet() throws YPersistenceException {
        YNet rootNet = new YNet("root", spec);
        spec.setRootNet(rootNet);

        YAWLServiceGateway gateway = new YAWLServiceGateway("gateway", spec);
        gateway.setCodelet("org.example.TestCodelet");
        spec.addDecomposition(gateway);

        String xml = spec.toXML();

        // Gateway should have codelet element
        assertTrue("Should contain codelet element",
                  xml.contains("<codelet>org.example.TestCodelet</codelet>"));
    }

    // Test loop pattern: gateway without codelet
    public void testToXML_LoopPattern_GatewayWithoutCodelet() throws YPersistenceException {
        YNet rootNet = new YNet("root", spec);
        spec.setRootNet(rootNet);

        YAWLServiceGateway gateway = new YAWLServiceGateway("gateway", spec);
        gateway.setCodelet(null);
        spec.addDecomposition(gateway);

        String xml = spec.toXML();

        // Gateway should not have codelet element
        assertFalse("Should not contain codelet element",
                   xml.contains("<codelet>"));
    }

    // Test loop pattern: net never has codelet
    public void testToXML_LoopPattern_NetNoCodelet() throws YPersistenceException {
        YNet rootNet = new YNet("root", spec);
        spec.setRootNet(rootNet);

        YNet subnet = new YNet("subnet", spec);
        spec.addDecomposition(subnet);

        String xml = spec.toXML();

        // Nets should never have codelet element
        assertFalse("Net should not have codelet element",
                   xml.contains("<codelet>"));
    }

    // Test loop pattern: gateway externalInteraction for release version
    public void testToXML_LoopPattern_ReleaseVersionExternalInteraction() throws YPersistenceException {
        YNet rootNet = new YNet("root", spec);
        spec.setRootNet(rootNet);

        YAWLServiceGateway gateway = new YAWLServiceGateway("gateway", spec);
        gateway.setManualInteraction(true);
        spec.addDecomposition(gateway);

        String xml = spec.toXML();

        // Release version should have externalInteraction
        assertTrue("Should contain externalInteraction element",
                  xml.contains("<externalInteraction>"));
        assertTrue("Should be manual",
                  xml.contains("<externalInteraction>manual</externalInteraction>"));
    }

    // Test loop pattern: gateway externalInteraction for beta version
    public void testToXML_LoopPattern_BetaVersionNoExternalInteraction() throws YPersistenceException {
        YSpecification betaSpec = new YSpecification("beta-spec");
        betaSpec.setVersion(YSchemaVersion.Beta7);

        YNet rootNet = new YNet("root", betaSpec);
        betaSpec.setRootNet(rootNet);

        YAWLServiceGateway gateway = new YAWLServiceGateway("gateway", betaSpec);
        gateway.setManualInteraction(true);
        betaSpec.addDecomposition(gateway);

        String xml = betaSpec.toXML();

        // Beta version should NOT have externalInteraction
        assertFalse("Beta version should not have externalInteraction element",
                   xml.contains("<externalInteraction>"));
    }

    // Test loop pattern: net never has externalInteraction
    public void testToXML_LoopPattern_NetNoExternalInteraction() throws YPersistenceException {
        YNet rootNet = new YNet("root", spec);
        spec.setRootNet(rootNet);

        YNet subnet = new YNet("subnet", spec);
        spec.addDecomposition(subnet);

        YAWLServiceGateway gateway = new YAWLServiceGateway("gateway", spec);
        spec.addDecomposition(gateway);

        String xml = spec.toXML();

        // Count externalInteraction - should only be 1 (for gateway)
        int count = countOccurrences(xml, "<externalInteraction>");
        assertEquals("Should have externalInteraction only for gateway", 1, count);
    }

    // Test comparator pattern: null ID handling
    public void testToXML_Comparator_NullID() throws YPersistenceException {
        YNet rootNet = new YNet("root", spec);
        spec.setRootNet(rootNet);

        // This is an edge case - normally IDs are not null
        YNet netWithNullId = new YNet(null, spec);
        spec.addDecomposition(netWithNullId);

        YNet normalNet = new YNet("normal", spec);
        spec.addDecomposition(normalNet);

        String xml = spec.toXML();

        // Should not throw exception
        assertNotNull("XML should be generated even with null ID", xml);
    }

    // Test multiple pattern matches in single method
    public void testToXML_MultiplePatterns_Complex() throws YPersistenceException {
        YNet rootNet = new YNet("root", spec);
        spec.setRootNet(rootNet);

        // Add multiple of each type
        YNet n1 = new YNet("net-1", spec);
        YNet n2 = new YNet("net-2", spec);
        YAWLServiceGateway g1 = new YAWLServiceGateway("gateway-1", spec);
        YAWLServiceGateway g2 = new YAWLServiceGateway("gateway-2", spec);

        g1.setCodelet("Codelet1");
        g1.setManualInteraction(true);
        g2.setManualInteraction(false);

        spec.addDecomposition(g2);
        spec.addDecomposition(n2);
        spec.addDecomposition(g1);
        spec.addDecomposition(n1);

        String xml = spec.toXML();

        // Verify sorting (nets before gateways)
        int n1Pos = xml.indexOf("id=\"net-1\"");
        int n2Pos = xml.indexOf("id=\"net-2\"");
        int g1Pos = xml.indexOf("id=\"gateway-1\"");
        int g2Pos = xml.indexOf("id=\"gateway-2\"");

        assertTrue("net-1 before gateway-1", n1Pos < g1Pos);
        assertTrue("net-1 before gateway-2", n1Pos < g2Pos);
        assertTrue("net-2 before gateway-1", n2Pos < g1Pos);
        assertTrue("net-2 before gateway-2", n2Pos < g2Pos);

        // Verify within-group sorting
        assertTrue("net-1 before net-2", n1Pos < n2Pos);
        assertTrue("gateway-1 before gateway-2", g1Pos < g2Pos);

        // Verify gateway-specific elements
        assertTrue("Should have codelet", xml.contains("<codelet>Codelet1</codelet>"));
        assertTrue("Should have manual interaction",
                  xml.contains("<externalInteraction>manual</externalInteraction>"));
        assertTrue("Should have automated interaction",
                  xml.contains("<externalInteraction>automated</externalInteraction>"));

        // Verify correct counts
        assertEquals("Should have 2 NetFactsType", 2,
                    countOccurrences(xml, "NetFactsType"));
        assertEquals("Should have 2 WebServiceGatewayFactsType", 2,
                    countOccurrences(xml, "WebServiceGatewayFactsType"));
        assertEquals("Should have 2 externalInteraction elements", 2,
                    countOccurrences(xml, "<externalInteraction>"));
        assertEquals("Should have 1 codelet element", 1,
                    countOccurrences(xml, "<codelet>"));
    }

    // Test root net is always first
    public void testToXML_RootNetAlwaysFirst() throws YPersistenceException {
        YNet rootNet = new YNet("z-should-be-last", spec);
        spec.setRootNet(rootNet);

        YNet net1 = new YNet("a-first-alphabetically", spec);
        spec.addDecomposition(net1);

        String xml = spec.toXML();

        // Root net should appear first despite alphabetical order
        int rootPos = xml.indexOf("id=\"z-should-be-last\"");
        int net1Pos = xml.indexOf("id=\"a-first-alphabetically\"");

        assertTrue("Root net should be first", rootPos < net1Pos);
        assertTrue("Root net should have isRootNet",
                  xml.substring(0, net1Pos).contains("isRootNet=\"true\""));
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
}
