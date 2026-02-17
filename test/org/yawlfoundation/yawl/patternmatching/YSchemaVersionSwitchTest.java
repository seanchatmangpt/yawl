package org.yawlfoundation.yawl.patternmatching;

import org.yawlfoundation.yawl.schema.YSchemaVersion;

import junit.framework.TestCase;

/**
 * Comprehensive tests for YSchemaVersion switch expressions
 *
 * Tests all branches of switch expression in YSchemaVersion.isBetaVersion():
 * - All enum values tested
 * - Both beta and release versions
 * - Exhaustiveness verified
 *
 * Branch Coverage Target: 100%
 *
 * Author: YAWL Foundation
 * Date: 2026-02-16
 */
public class YSchemaVersionSwitchTest extends TestCase {

    // Test isBetaVersion() switch - all enum values
    public void testIsBetaVersion_ReleaseVersions() {
        assertFalse("4.0 should not be beta", YSchemaVersion.FourPointZero.isBetaVersion());
        assertFalse("3.0 should not be beta", YSchemaVersion.ThreePointZero.isBetaVersion());
        assertFalse("2.2 should not be beta", YSchemaVersion.TwoPointTwo.isBetaVersion());
        assertFalse("2.1 should not be beta", YSchemaVersion.TwoPointOne.isBetaVersion());
        assertFalse("2.0 should not be beta", YSchemaVersion.TwoPointZero.isBetaVersion());
    }

    public void testIsBetaVersion_BetaVersions() {
        assertTrue("Beta 2 should be beta", YSchemaVersion.Beta2.isBetaVersion());
        assertTrue("Beta 3 should be beta", YSchemaVersion.Beta3.isBetaVersion());
        assertTrue("Beta 4 should be beta", YSchemaVersion.Beta4.isBetaVersion());
        assertTrue("Beta 6 should be beta", YSchemaVersion.Beta6.isBetaVersion());
        assertTrue("Beta 7 should be beta", YSchemaVersion.Beta7.isBetaVersion());
    }

    // Test all enum values are handled (exhaustiveness)
    public void testIsBetaVersion_Exhaustive() {
        for (YSchemaVersion version : YSchemaVersion.values()) {
            // Should not throw exception - all cases handled
            boolean isBeta = version.isBetaVersion();
            assertNotNull("isBetaVersion should return non-null for " + version, Boolean.valueOf(isBeta));
        }
    }

    // Test specific version characteristics
    public void testBeta2_SpecialHandling() {
        assertTrue("Beta2 should be beta version", YSchemaVersion.Beta2.isBetaVersion());
        assertTrue("Beta2 should use simple root data", YSchemaVersion.Beta2.usesSimpleRootData());
        assertFalse("Beta2 should not validate schema", YSchemaVersion.Beta2.isSchemaValidating());
    }

    public void testBeta3_SpecialHandling() {
        assertTrue("Beta3 should be beta version", YSchemaVersion.Beta3.isBetaVersion());
        assertTrue("Beta3 should use simple root data", YSchemaVersion.Beta3.usesSimpleRootData());
        assertTrue("Beta3 should validate schema", YSchemaVersion.Beta3.isSchemaValidating());
    }

    public void testReleaseVersions_StandardBehavior() {
        assertFalse("4.0 should not use simple root data",
                    YSchemaVersion.FourPointZero.usesSimpleRootData());
        assertTrue("4.0 should validate schema",
                   YSchemaVersion.FourPointZero.isSchemaValidating());

        assertFalse("2.0 should not use simple root data",
                    YSchemaVersion.TwoPointZero.usesSimpleRootData());
        assertTrue("2.0 should validate schema",
                   YSchemaVersion.TwoPointZero.isSchemaValidating());
    }

    // Test version comparison
    public void testIsVersionAtLeast_AllVersions() {
        // 4.0 is at least any version
        assertTrue(YSchemaVersion.FourPointZero.isVersionAtLeast(YSchemaVersion.Beta2));
        assertTrue(YSchemaVersion.FourPointZero.isVersionAtLeast(YSchemaVersion.TwoPointZero));
        assertTrue(YSchemaVersion.FourPointZero.isVersionAtLeast(YSchemaVersion.FourPointZero));

        // 2.0 is at least beta versions
        assertTrue(YSchemaVersion.TwoPointZero.isVersionAtLeast(YSchemaVersion.Beta2));
        assertTrue(YSchemaVersion.TwoPointZero.isVersionAtLeast(YSchemaVersion.Beta7));
        assertTrue(YSchemaVersion.TwoPointZero.isVersionAtLeast(YSchemaVersion.TwoPointZero));

        // 2.0 is not at least later versions
        assertFalse(YSchemaVersion.TwoPointZero.isVersionAtLeast(YSchemaVersion.TwoPointOne));
        assertFalse(YSchemaVersion.TwoPointZero.isVersionAtLeast(YSchemaVersion.FourPointZero));

        // Beta2 is at least itself but not later
        assertTrue(YSchemaVersion.Beta2.isVersionAtLeast(YSchemaVersion.Beta2));
        assertFalse(YSchemaVersion.Beta2.isVersionAtLeast(YSchemaVersion.Beta3));
    }

    // Test namespace retrieval
    public void testGetNameSpace_BetaVsRelease() {
        String betaNS = "http://www.citi.qut.edu.au/yawl";
        String releaseNS = "http://www.yawlfoundation.org/yawlschema";

        // Beta versions use beta namespace
        assertEquals(betaNS, YSchemaVersion.Beta2.getNameSpace());
        assertEquals(betaNS, YSchemaVersion.Beta3.getNameSpace());
        assertEquals(betaNS, YSchemaVersion.Beta7.getNameSpace());

        // Release versions use release namespace
        assertEquals(releaseNS, YSchemaVersion.TwoPointZero.getNameSpace());
        assertEquals(releaseNS, YSchemaVersion.TwoPointOne.getNameSpace());
        assertEquals(releaseNS, YSchemaVersion.ThreePointZero.getNameSpace());
        assertEquals(releaseNS, YSchemaVersion.FourPointZero.getNameSpace());
    }

    // Test header generation
    public void testGetHeader_BetaVersions() {
        String header = YSchemaVersion.Beta2.getHeader();
        assertNotNull(header);
        assertTrue(header.contains("Beta 7.1"));
        assertTrue(header.contains("http://www.citi.qut.edu.au/yawl"));
    }

    public void testGetHeader_ReleaseVersions() {
        String header = YSchemaVersion.FourPointZero.getHeader();
        assertNotNull(header);
        assertTrue(header.contains("4.0"));
        assertTrue(header.contains("http://www.yawlfoundation.org/yawlschema"));

        header = YSchemaVersion.TwoPointZero.getHeader();
        assertNotNull(header);
        assertTrue(header.contains("2.0"));
    }

    // Test schema URL retrieval
    public void testGetSchemaURL_AllVersions() {
        for (YSchemaVersion version : YSchemaVersion.values()) {
            assertNotNull("Schema URL should not be null for " + version,
                         version.getSchemaURL());
        }
    }

    // Test schema location
    public void testGetSchemaLocation_AllVersions() {
        for (YSchemaVersion version : YSchemaVersion.values()) {
            String location = version.getSchemaLocation();
            assertNotNull("Schema location should not be null for " + version, location);
            assertTrue("Schema location should contain namespace",
                      location.contains("http://"));
        }
    }

    // Test fromString conversion
    public void testFromString_AllVersions() {
        assertEquals(YSchemaVersion.Beta2, YSchemaVersion.fromString("Beta 2"));
        assertEquals(YSchemaVersion.Beta3, YSchemaVersion.fromString("Beta 3"));
        assertEquals(YSchemaVersion.Beta4, YSchemaVersion.fromString("Beta 4"));
        assertEquals(YSchemaVersion.Beta6, YSchemaVersion.fromString("Beta 6"));
        assertEquals(YSchemaVersion.Beta7, YSchemaVersion.fromString("Beta 7.1"));
        assertEquals(YSchemaVersion.TwoPointZero, YSchemaVersion.fromString("2.0"));
        assertEquals(YSchemaVersion.TwoPointOne, YSchemaVersion.fromString("2.1"));
        assertEquals(YSchemaVersion.TwoPointTwo, YSchemaVersion.fromString("2.2"));
        assertEquals(YSchemaVersion.ThreePointZero, YSchemaVersion.fromString("3.0"));
        assertEquals(YSchemaVersion.FourPointZero, YSchemaVersion.fromString("4.0"));
    }

    public void testFromString_InvalidVersions() {
        assertNull(YSchemaVersion.fromString(null));
        assertNull(YSchemaVersion.fromString("invalid"));
        assertNull(YSchemaVersion.fromString(""));
        assertNull(YSchemaVersion.fromString("1.0"));
        assertNull(YSchemaVersion.fromString("5.0"));
    }

    // Test isValidVersionString
    public void testIsValidVersionString_ValidVersions() {
        assertTrue(YSchemaVersion.isValidVersionString("Beta 2"));
        assertTrue(YSchemaVersion.isValidVersionString("2.0"));
        assertTrue(YSchemaVersion.isValidVersionString("4.0"));
    }

    public void testIsValidVersionString_InvalidVersions() {
        assertFalse(YSchemaVersion.isValidVersionString(null));
        assertFalse(YSchemaVersion.isValidVersionString("invalid"));
        assertFalse(YSchemaVersion.isValidVersionString(""));
        assertFalse(YSchemaVersion.isValidVersionString("1.0"));
    }

    // Test default version
    public void testDefaultVersion() {
        assertEquals(YSchemaVersion.FourPointZero, YSchemaVersion.defaultVersion());
        assertEquals(YSchemaVersion.FourPointZero, YSchemaVersion.DEFAULT_VERSION);
    }

    // Test toString
    public void testToString_AllVersions() {
        assertEquals("Beta 2", YSchemaVersion.Beta2.toString());
        assertEquals("Beta 3", YSchemaVersion.Beta3.toString());
        assertEquals("Beta 4", YSchemaVersion.Beta4.toString());
        assertEquals("Beta 6", YSchemaVersion.Beta6.toString());
        assertEquals("Beta 7.1", YSchemaVersion.Beta7.toString());
        assertEquals("2.0", YSchemaVersion.TwoPointZero.toString());
        assertEquals("2.1", YSchemaVersion.TwoPointOne.toString());
        assertEquals("2.2", YSchemaVersion.TwoPointTwo.toString());
        assertEquals("3.0", YSchemaVersion.ThreePointZero.toString());
        assertEquals("4.0", YSchemaVersion.FourPointZero.toString());
    }
}
