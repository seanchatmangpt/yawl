package org.yawlfoundation.yawl.patternmatching;

import org.junit.jupiter.api.Tag;

import org.yawlfoundation.yawl.schema.XSDType;
import org.yawlfoundation.yawl.schema.YSchemaVersion;

import junit.framework.TestCase;

/**
 * Edge case tests for pattern matching conversions
 *
 * Tests edge cases:
 * - Null inputs
 * - Invalid types
 * - Boundary values
 * - Empty strings
 * - Type mismatches
 *
 * Branch Coverage Target: 100%
 *
 * Author: YAWL Foundation
 * Date: 2026-02-16
 */
@Tag("unit")
public class PatternMatchingEdgeCaseTest extends TestCase {

    // Test null inputs to switch expressions
    public void testXSDType_NullType() {
        String result = XSDType.getSampleValue(null);
        assertNotNull("Should handle null type", result);
        assertEquals("name", result);
    }

    public void testXSDType_EmptyType() {
        String result = XSDType.getSampleValue("");
        assertNotNull("Should handle empty type", result);
        assertEquals("name", result);
    }

    public void testXSDType_InvalidType() {
        String result = XSDType.getSampleValue("nonexistent");
        assertNotNull("Should handle invalid type", result);
        assertEquals("name", result);
    }

    // Test boundary values for XSDType.getString()
    public void testXSDType_GetString_MinValue() {
        String result = XSDType.getString(Integer.MIN_VALUE);
        assertEquals("invalid_type", result);
    }

    public void testXSDType_GetString_MaxValue() {
        String result = XSDType.getString(Integer.MAX_VALUE);
        assertEquals("invalid_type", result);
    }

    public void testXSDType_GetString_NegativeValue() {
        String result = XSDType.getString(-1);
        assertEquals("invalid_type", result);

        result = XSDType.getString(-999);
        assertEquals("invalid_type", result);
    }

    public void testXSDType_GetString_BeyondRange() {
        String result = XSDType.getString(1000);
        assertEquals("invalid_type", result);
    }

    // Test type checking with null
    public void testXSDType_IsNumericType_Null() {
        try {
            boolean result = XSDType.isNumericType(null);
            assertFalse("Null should not be numeric type", result);
        } catch (NullPointerException e) {
            // Also acceptable - depends on implementation
        }
    }

    public void testXSDType_IsStringType_Null() {
        try {
            boolean result = XSDType.isStringType(null);
            assertFalse("Null should not be string type", result);
        } catch (NullPointerException e) {
            // Also acceptable
        }
    }

    public void testXSDType_IsBooleanType_Null() {
        try {
            boolean result = XSDType.isBooleanType(null);
            assertFalse("Null should not be boolean type", result);
        } catch (NullPointerException e) {
            // Also acceptable
        }
    }

    // Test YSchemaVersion with null and invalid strings
    public void testYSchemaVersion_FromString_Null() {
        YSchemaVersion version = YSchemaVersion.fromString(null);
        assertNull("Null should return null version", version);
    }

    public void testYSchemaVersion_FromString_Empty() {
        YSchemaVersion version = YSchemaVersion.fromString("");
        assertNull("Empty string should return null version", version);
    }

    public void testYSchemaVersion_FromString_Whitespace() {
        YSchemaVersion version = YSchemaVersion.fromString("   ");
        assertNull("Whitespace should return null version", version);
    }

    public void testYSchemaVersion_FromString_InvalidVersion() {
        YSchemaVersion version = YSchemaVersion.fromString("99.99");
        assertNull("Invalid version should return null", version);

        version = YSchemaVersion.fromString("NotAVersion");
        assertNull("Non-version string should return null", version);
    }

    public void testYSchemaVersion_FromString_AlmostValid() {
        YSchemaVersion version = YSchemaVersion.fromString("2.0.0");
        assertNull("Should require exact match", version);

        version = YSchemaVersion.fromString("2.0 ");
        assertNull("Trailing space should fail", version);

        version = YSchemaVersion.fromString(" 2.0");
        assertNull("Leading space should fail", version);
    }

    // Test case sensitivity
    public void testYSchemaVersion_FromString_CaseSensitive() {
        YSchemaVersion version = YSchemaVersion.fromString("beta 2");
        assertNull("Should be case sensitive", version);

        version = YSchemaVersion.fromString("BETA 2");
        assertNull("Should be case sensitive", version);
    }

    // Test XSDType ordinal edge cases
    public void testXSDType_GetOrdinal_NullType() {
        int ordinal = XSDType.getOrdinal(null);
        assertEquals("Null type should return -1", -1, ordinal);
    }

    public void testXSDType_GetOrdinal_InvalidType() {
        int ordinal = XSDType.getOrdinal("invalid");
        assertEquals("Invalid type should return -1", -1, ordinal);
    }

    // Test XSDType.isBuiltInType edge cases
    public void testXSDType_IsBuiltInType_Null() {
        assertFalse("Null should not be built-in type",
                   XSDType.isBuiltInType(null));
    }

    public void testXSDType_IsBuiltInType_Empty() {
        assertFalse("Empty string should not be built-in type",
                   XSDType.isBuiltInType(""));
    }

    public void testXSDType_IsBuiltInType_Invalid() {
        assertFalse("Invalid type should not be built-in",
                   XSDType.isBuiltInType("notAType"));
    }

    // Test XSDType facet validation edge cases
    public void testXSDType_IsValidFacet_NullFacet() {
        // isValidFacet calls valueOf(null) which throws NPE
        try {
            boolean result = XSDType.isValidFacet(null, "string");
            fail("Should throw NullPointerException for null facet");
        } catch (NullPointerException e) {
            // Expected - valueOf(null) throws NPE
        }
    }

    public void testXSDType_IsValidFacet_NullType() {
        assertFalse("Null type should not support any facet",
                   XSDType.isValidFacet("minLength", null));
    }

    public void testXSDType_IsValidFacet_InvalidFacet() {
        assertFalse("Invalid facet should not be valid",
                   XSDType.isValidFacet("notAFacet", "string"));
    }

    public void testXSDType_IsValidFacet_EmptyFacet() {
        assertFalse("Empty facet should not be valid",
                   XSDType.isValidFacet("", "string"));
    }

    // Test pattern matching with very long strings
    public void testXSDType_GetSampleValue_LongTypeName() {
        String longType = "a".repeat(1000);
        String result = XSDType.getSampleValue(longType);
        assertNotNull("Should handle long type name", result);
        assertEquals("name", result);
    }

    // Test special characters in type names
    public void testXSDType_SpecialCharacters() {
        String result = XSDType.getSampleValue("type-with-dash");
        assertEquals("name", result);

        result = XSDType.getSampleValue("type_with_underscore");
        assertEquals("name", result);

        result = XSDType.getSampleValue("type.with.dot");
        assertEquals("name", result);

        result = XSDType.getSampleValue("type:with:colon");
        assertEquals("name", result);
    }

    // Test version comparison edge cases
    public void testYSchemaVersion_IsVersionAtLeast_SameVersion() {
        assertTrue("Version should be at least itself",
                  YSchemaVersion.FourPointZero.isVersionAtLeast(YSchemaVersion.FourPointZero));
        assertTrue("Version should be at least itself",
                  YSchemaVersion.Beta2.isVersionAtLeast(YSchemaVersion.Beta2));
    }

    public void testYSchemaVersion_IsVersionAtLeast_BoundaryVersions() {
        // Test oldest vs newest
        assertTrue("Newest should be at least oldest",
                  YSchemaVersion.FourPointZero.isVersionAtLeast(YSchemaVersion.Beta2));
        assertFalse("Oldest should not be at least newest",
                   YSchemaVersion.Beta2.isVersionAtLeast(YSchemaVersion.FourPointZero));
    }

    // Test schema URL and location edge cases
    public void testYSchemaVersion_GetSchemaLocation_AllVersions() {
        for (YSchemaVersion version : YSchemaVersion.values()) {
            String location = version.getSchemaLocation();
            assertNotNull("Schema location should not be null for " + version, location);
            assertFalse("Schema location should not be empty for " + version,
                       location.isEmpty());
            assertTrue("Schema location should contain http",
                      location.contains("http"));
        }
    }

    // Test XSDType constraint facet map edge cases
    public void testXSDType_GetConstrainingFacetMap_AllValid() {
        String[] allTypes = XSDType.getBuiltInTypeArray();
        for (String type : allTypes) {
            char[] facetMap = XSDType.getConstrainingFacetMap(type);
            assertNotNull("Facet map should not be null for " + type, facetMap);
            assertEquals("Facet map should have 12 characters",
                        12, facetMap.length);

            // All characters should be 0 or 1
            for (char c : facetMap) {
                assertTrue("Facet map should only contain 0 or 1",
                          c == '0' || c == '1');
            }
        }
    }

    // Test type list consistency
    public void testXSDType_TypeListConsistency() {
        String[] typeArray = XSDType.getBuiltInTypeArray();
        assertNotNull("Type array should not be null", typeArray);
        assertEquals("Type array should have 45 types", 45, typeArray.length);

        // Verify getString() returns all types
        for (int i = 0; i < 45; i++) {
            String type = XSDType.getString(i);
            assertNotNull("getString(" + i + ") should not be null", type);
            assertFalse("getString(" + i + ") should not be 'invalid_type'",
                       type.equals("invalid_type"));
        }
    }

    // Test that invalid type returns default in all methods
    public void testXSDType_InvalidTypeConsistency() {
        String invalidType = "definitelyNotAType";

        String sample = XSDType.getSampleValue(invalidType);
        assertEquals("Should return default sample", "name", sample);

        char[] facets = XSDType.getConstrainingFacetMap(invalidType);
        assertEquals("Should return empty facet map", "000000000000",
                    new String(facets));

        assertFalse("Should not be built-in", XSDType.isBuiltInType(invalidType));
        assertFalse("Should not be numeric", XSDType.isNumericType(invalidType));
        assertFalse("Should not be string type", XSDType.isStringType(invalidType));
    }
}
