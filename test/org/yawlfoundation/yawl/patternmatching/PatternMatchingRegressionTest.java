package org.yawlfoundation.yawl.patternmatching;

import org.yawlfoundation.yawl.elements.*;
import org.yawlfoundation.yawl.engine.WorkItemCompletion;
import org.yawlfoundation.yawl.schema.XSDType;
import org.yawlfoundation.yawl.schema.YSchemaVersion;
import org.yawlfoundation.yawl.unmarshal.YMetaData;

import junit.framework.TestCase;

/**
 * Regression tests for pattern matching conversions
 *
 * Ensures that pattern matching conversions:
 * - Preserve original behavior
 * - Don't introduce new bugs
 * - Maintain backward compatibility
 * - Produce identical results to old code
 *
 * Branch Coverage Target: 100%
 *
 * Author: YAWL Foundation
 * Date: 2026-02-16
 */
public class PatternMatchingRegressionTest extends TestCase {

    // Test that XSDType.getString() behavior is preserved
    public void testXSDType_GetString_BackwardCompatibility() {
        // Test all types still return expected strings
        assertEquals("anyType", XSDType.getString(XSDType.ANY_TYPE));
        assertEquals("integer", XSDType.getString(XSDType.INTEGER));
        assertEquals("string", XSDType.getString(XSDType.STRING));
        assertEquals("boolean", XSDType.getString(XSDType.BOOLEAN));
        assertEquals("date", XSDType.getString(XSDType.DATE));
        assertEquals("double", XSDType.getString(XSDType.DOUBLE));

        // Invalid type should still return "invalid_type"
        assertEquals("invalid_type", XSDType.getString(-1));
        assertEquals("invalid_type", XSDType.getString(999));
    }

    // Test that XSDType.getSampleValue() behavior is preserved
    public void testXSDType_GetSampleValue_BackwardCompatibility() {
        // Integer types should return "100"
        assertEquals("100", XSDType.getSampleValue("integer"));
        assertEquals("100", XSDType.getSampleValue("positiveInteger"));
        assertEquals("100", XSDType.getSampleValue("int"));

        // Negative types should return "-100"
        assertEquals("-100", XSDType.getSampleValue("negativeInteger"));

        // String types should return "a string"
        assertEquals("a string", XSDType.getSampleValue("string"));

        // Boolean should return "false"
        assertEquals("false", XSDType.getSampleValue("boolean"));

        // Float types should return "3.142"
        assertEquals("3.142", XSDType.getSampleValue("double"));
        assertEquals("3.142", XSDType.getSampleValue("float"));

        // Invalid should return "name"
        assertEquals("name", XSDType.getSampleValue("invalid"));
    }

    // Test that XSDType type checking is preserved
    public void testXSDType_TypeChecking_BackwardCompatibility() {
        // Numeric type checking
        assertTrue(XSDType.isNumericType("integer"));
        assertTrue(XSDType.isNumericType("double"));
        assertFalse(XSDType.isNumericType("string"));

        // String type checking
        assertTrue(XSDType.isStringType("string"));
        assertFalse(XSDType.isStringType("integer"));

        // Boolean type checking
        assertTrue(XSDType.isBooleanType("boolean"));
        assertFalse(XSDType.isBooleanType("string"));

        // Date type checking
        assertTrue(XSDType.isDateType("date"));
        assertFalse(XSDType.isDateType("duration"));
    }

    // Test that YSchemaVersion.isBetaVersion() behavior is preserved
    public void testYSchemaVersion_IsBetaVersion_BackwardCompatibility() {
        // Beta versions
        assertTrue(YSchemaVersion.Beta2.isBetaVersion());
        assertTrue(YSchemaVersion.Beta3.isBetaVersion());
        assertTrue(YSchemaVersion.Beta4.isBetaVersion());
        assertTrue(YSchemaVersion.Beta6.isBetaVersion());
        assertTrue(YSchemaVersion.Beta7.isBetaVersion());

        // Release versions
        assertFalse(YSchemaVersion.TwoPointZero.isBetaVersion());
        assertFalse(YSchemaVersion.TwoPointOne.isBetaVersion());
        assertFalse(YSchemaVersion.TwoPointTwo.isBetaVersion());
        assertFalse(YSchemaVersion.ThreePointZero.isBetaVersion());
        assertFalse(YSchemaVersion.FourPointZero.isBetaVersion());
    }

    // Test that YSchemaVersion.fromString() behavior is preserved
    public void testYSchemaVersion_FromString_BackwardCompatibility() {
        // Valid versions
        assertEquals(YSchemaVersion.Beta2,
                    YSchemaVersion.fromString("Beta 2"));
        assertEquals(YSchemaVersion.TwoPointZero,
                    YSchemaVersion.fromString("2.0"));
        assertEquals(YSchemaVersion.FourPointZero,
                    YSchemaVersion.fromString("4.0"));

        // Invalid versions
        assertNull(YSchemaVersion.fromString(null));
        assertNull(YSchemaVersion.fromString(""));
        assertNull(YSchemaVersion.fromString("invalid"));
    }

    // Test that YSpecification.toXML() instanceof behavior is preserved
    public void testYSpecification_ToXML_InstanceofBehavior() throws Exception {
        YSpecification spec = createTestSpecification("test-spec");

        String xml = spec.toXML();

        // Root net should be marked as root
        assertTrue(xml.contains("isRootNet=\"true\""));

        // Nets should be NetFactsType
        assertTrue(xml.contains("xsi:type=\"NetFactsType\""));
    }

    // Test that decomposition sorting is preserved
    public void testYSpecification_ToXML_SortingBehavior() throws Exception {
        YSpecification spec = createTestSpecification("test-spec");

        // Add in non-sorted order
        YAWLServiceGateway g1 = new YAWLServiceGateway("z-gateway", spec);
        YNet n1 = createNetWithConditions("a-net", spec);
        YAWLServiceGateway g2 = new YAWLServiceGateway("a-gateway", spec);

        spec.addDecomposition(g1);
        spec.addDecomposition(n1);
        spec.addDecomposition(g2);

        String xml = spec.toXML();

        // Nets should come before gateways
        int aNetPos = xml.indexOf("id=\"a-net\"");
        int aGatewayPos = xml.indexOf("id=\"a-gateway\"");
        int zGatewayPos = xml.indexOf("id=\"z-gateway\"");

        assertTrue("Net should come before gateway", aNetPos < aGatewayPos);
        assertTrue("Net should come before gateway", aNetPos < zGatewayPos);

        // Within gateways, should be sorted
        assertTrue("a-gateway before z-gateway", aGatewayPos < zGatewayPos);
    }

    // Test that XSDType facet maps are preserved
    public void testXSDType_FacetMaps_BackwardCompatibility() {
        // Integer types should have pattern "111100010111"
        assertArrayEquals("111100010111".toCharArray(),
                         XSDType.getConstrainingFacetMap("integer"));

        // String types should have pattern "000011100111"
        assertArrayEquals("000011100111".toCharArray(),
                         XSDType.getConstrainingFacetMap("string"));

        // Boolean should have pattern "000000000110"
        assertArrayEquals("000000000110".toCharArray(),
                         XSDType.getConstrainingFacetMap("boolean"));

        // Byte should have pattern "111100110111"
        assertArrayEquals("111100110111".toCharArray(),
                         XSDType.getConstrainingFacetMap("byte"));

        // Decimal should have pattern "111100011111"
        assertArrayEquals("111100011111".toCharArray(),
                         XSDType.getConstrainingFacetMap("decimal"));
    }

    // Test that type constants haven't changed
    public void testXSDType_Constants_BackwardCompatibility() {
        assertEquals(0, XSDType.ANY_TYPE);
        assertEquals(1, XSDType.INTEGER);
        assertEquals(17, XSDType.STRING);
        assertEquals(25, XSDType.DATE);
        assertEquals(40, XSDType.BOOLEAN);
        assertEquals(44, XSDType.ANY_URI);
        assertEquals(-1, XSDType.INVALID_TYPE);
    }

    // Test that YTask constants haven't changed
    public void testYTask_Constants_BackwardCompatibility() {
        assertEquals(95, YTask._AND);
        assertEquals(103, YTask._OR);
        assertEquals(126, YTask._XOR);
    }

    // Test that schema version behavior is consistent
    public void testYSchemaVersion_Behavior_BackwardCompatibility() {
        // Default version should be 4.0
        assertEquals(YSchemaVersion.FourPointZero,
                    YSchemaVersion.defaultVersion());

        // Beta2 should use simple root data
        assertTrue(YSchemaVersion.Beta2.usesSimpleRootData());
        assertFalse(YSchemaVersion.FourPointZero.usesSimpleRootData());

        // Beta2 should not validate schema
        assertFalse(YSchemaVersion.Beta2.isSchemaValidating());
        assertTrue(YSchemaVersion.FourPointZero.isSchemaValidating());

        // Version comparison should work
        assertTrue(YSchemaVersion.FourPointZero
                  .isVersionAtLeast(YSchemaVersion.TwoPointZero));
        assertFalse(YSchemaVersion.TwoPointZero
                   .isVersionAtLeast(YSchemaVersion.FourPointZero));
    }

    // Test that all enum values are accounted for
    public void testEnumValues_NoChanges() {
        // Completion enum (WorkItemCompletion has 4 values: Normal, Force, Fail, Invalid)
        assertEquals(4, WorkItemCompletion.values().length);

        // Timer type enum (Duration, Expiry, Interval, LateBound, Nil)
        assertEquals(5, org.yawlfoundation.yawl.elements.YTimerParameters.TimerType.values().length);

        // Trigger type enum (OnEnabled, OnExecuting)
        assertEquals(2, org.yawlfoundation.yawl.elements.YTimerParameters.TriggerType.values().length);

        // Schema version enum
        assertEquals(10, YSchemaVersion.values().length);
    }

    // Test that no functionality was lost in conversion
    public void testNoFunctionalityLost() {
        // All XSD types should still be accessible
        for (int i = 0; i <= XSDType.ANY_URI; i++) {
            String typeStr = XSDType.getString(i);
            assertNotNull("Type " + i + " should have string representation", typeStr);

            if (i != XSDType.INVALID_TYPE) {
                assertFalse("Valid type should not be 'invalid_type'",
                           typeStr.equals("invalid_type"));
            }
        }

        // All schema versions should still be accessible
        for (YSchemaVersion version : YSchemaVersion.values()) {
            assertNotNull("Version should have string representation",
                         version.toString());
            assertNotNull("Version should have namespace",
                         version.getNameSpace());
            assertNotNull("Version should have schema URL",
                         version.getSchemaURL());
        }
    }

    /**
     * Creates a fully initialized YSpecification with proper input/output conditions.
     */
    private YSpecification createTestSpecification(String specId) throws Exception {
        YSpecification spec = new YSpecification(specId);
        spec.setVersion(YSchemaVersion.FourPointZero);
        spec.setMetaData(new YMetaData());
        spec.setSchema("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>");

        YNet rootNet = createNetWithConditions("root-net", spec);
        spec.setRootNet(rootNet);

        YNet subNet = createNetWithConditions("sub-net", spec);
        spec.addDecomposition(subNet);

        YAWLServiceGateway gateway = new YAWLServiceGateway("gateway", spec);
        spec.addDecomposition(gateway);

        return spec;
    }

    /**
     * Creates a YNet with input and output condition set.
     */
    private YNet createNetWithConditions(String netId, YSpecification spec) {
        YNet net = new YNet(netId, spec);
        YInputCondition input = new YInputCondition("input-" + netId, net);
        YOutputCondition output = new YOutputCondition("output-" + netId, net);
        net.setInputCondition(input);
        net.setOutputCondition(output);
        net.addNetElement(input);
        net.addNetElement(output);
        return net;
    }

    // Helper method
    private void assertArrayEquals(char[] expected, char[] actual) {
        assertEquals("Array length mismatch", expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals("Mismatch at index " + i, expected[i], actual[i]);
        }
    }
}
