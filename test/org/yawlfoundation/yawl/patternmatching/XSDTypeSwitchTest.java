package org.yawlfoundation.yawl.patternmatching;

import org.junit.jupiter.api.Tag;

import org.yawlfoundation.yawl.schema.XSDType;

import junit.framework.TestCase;

/**
 * Comprehensive tests for XSDType switch expressions
 *
 * Tests all branches of switch expressions in XSDType class:
 * - getString(int type) - 45 cases
 * - getSampleValue(String type) - 29 cases
 * - getConstrainingFacetMap(String type) - 13 cases
 *
 * Branch Coverage Target: 100%
 *
 * Author: YAWL Foundation
 * Date: 2026-02-16
 */
@Tag("unit")
public class XSDTypeSwitchTest extends TestCase {

    // Test getString() switch - all 45 type cases
    public void testGetString_AllNumericTypes() {
        assertEquals("anyType", XSDType.getString(XSDType.ANY_TYPE));
        assertEquals("integer", XSDType.getString(XSDType.INTEGER));
        assertEquals("positiveInteger", XSDType.getString(XSDType.POSITIVE_INTEGER));
        assertEquals("negativeInteger", XSDType.getString(XSDType.NEGATIVE_INTEGER));
        assertEquals("nonPositiveInteger", XSDType.getString(XSDType.NON_POSITIVE_INTEGER));
        assertEquals("nonNegativeInteger", XSDType.getString(XSDType.NON_NEGATIVE_INTEGER));
        assertEquals("int", XSDType.getString(XSDType.INT));
        assertEquals("long", XSDType.getString(XSDType.LONG));
        assertEquals("short", XSDType.getString(XSDType.SHORT));
        assertEquals("byte", XSDType.getString(XSDType.BYTE));
        assertEquals("unsignedLong", XSDType.getString(XSDType.UNSIGNED_LONG));
        assertEquals("unsignedInt", XSDType.getString(XSDType.UNSIGNED_INT));
        assertEquals("unsignedShort", XSDType.getString(XSDType.UNSIGNED_SHORT));
        assertEquals("unsignedByte", XSDType.getString(XSDType.UNSIGNED_BYTE));
        assertEquals("double", XSDType.getString(XSDType.DOUBLE));
        assertEquals("float", XSDType.getString(XSDType.FLOAT));
        assertEquals("decimal", XSDType.getString(XSDType.DECIMAL));
    }

    public void testGetString_AllStringTypes() {
        assertEquals("string", XSDType.getString(XSDType.STRING));
        assertEquals("normalizedString", XSDType.getString(XSDType.NORMALIZED_STRING));
        assertEquals("token", XSDType.getString(XSDType.TOKEN));
        assertEquals("language", XSDType.getString(XSDType.LANGUAGE));
        assertEquals("NMTOKEN", XSDType.getString(XSDType.NMTOKEN));
        assertEquals("NMTOKENS", XSDType.getString(XSDType.NMTOKENS));
        assertEquals("Name", XSDType.getString(XSDType.NAME));
        assertEquals("NCName", XSDType.getString(XSDType.NCNAME));
    }

    public void testGetString_AllDateTimeTypes() {
        assertEquals("date", XSDType.getString(XSDType.DATE));
        assertEquals("time", XSDType.getString(XSDType.TIME));
        assertEquals("dateTime", XSDType.getString(XSDType.DATETIME));
        assertEquals("duration", XSDType.getString(XSDType.DURATION));
        assertEquals("gDay", XSDType.getString(XSDType.GDAY));
        assertEquals("gMonth", XSDType.getString(XSDType.GMONTH));
        assertEquals("gYear", XSDType.getString(XSDType.GYEAR));
        assertEquals("gMonthDay", XSDType.getString(XSDType.GMONTHDAY));
        assertEquals("gYearMonth", XSDType.getString(XSDType.GYEARMONTH));
    }

    public void testGetString_AllMagicTypes() {
        assertEquals("ID", XSDType.getString(XSDType.ID));
        assertEquals("IDREF", XSDType.getString(XSDType.IDREF));
        assertEquals("IDREFS", XSDType.getString(XSDType.IDREFS));
        assertEquals("ENTITY", XSDType.getString(XSDType.ENTITY));
        assertEquals("ENTITIES", XSDType.getString(XSDType.ENTITIES));
    }

    public void testGetString_AllOtherTypes() {
        assertEquals("QName", XSDType.getString(XSDType.QNAME));
        assertEquals("boolean", XSDType.getString(XSDType.BOOLEAN));
        assertEquals("hexBinary", XSDType.getString(XSDType.HEX_BINARY));
        assertEquals("base64Binary", XSDType.getString(XSDType.BASE64_BINARY));
        assertEquals("notation", XSDType.getString(XSDType.NOTATION));
        assertEquals("anyURI", XSDType.getString(XSDType.ANY_URI));
    }

    public void testGetString_InvalidType() {
        assertEquals("invalid_type", XSDType.getString(XSDType.INVALID_TYPE));
        assertEquals("invalid_type", XSDType.getString(-999));
        assertEquals("invalid_type", XSDType.getString(9999));
    }

    // Test getSampleValue() switch - all value types
    public void testGetSampleValue_IntegerTypes() {
        assertEquals("100", XSDType.getSampleValue("integer"));
        assertEquals("100", XSDType.getSampleValue("positiveInteger"));
        assertEquals("100", XSDType.getSampleValue("int"));
        assertEquals("100", XSDType.getSampleValue("long"));
        assertEquals("100", XSDType.getSampleValue("short"));
        assertEquals("100", XSDType.getSampleValue("unsignedLong"));
        assertEquals("100", XSDType.getSampleValue("unsignedInt"));
        assertEquals("100", XSDType.getSampleValue("unsignedShort"));
        assertEquals("100", XSDType.getSampleValue("unsignedByte"));
        assertEquals("100", XSDType.getSampleValue("nonNegativeInteger"));
        assertEquals("100", XSDType.getSampleValue("gYear"));
        assertEquals("100", XSDType.getSampleValue("byte"));
        assertEquals("100", XSDType.getSampleValue("decimal"));
    }

    public void testGetSampleValue_NegativeIntegerTypes() {
        assertEquals("-100", XSDType.getSampleValue("negativeInteger"));
        assertEquals("-100", XSDType.getSampleValue("nonPositiveInteger"));
    }

    public void testGetSampleValue_StringTypes() {
        assertEquals("a string", XSDType.getSampleValue("string"));
        assertEquals("a string", XSDType.getSampleValue("normalizedString"));
        assertEquals("token", XSDType.getSampleValue("token"));
        assertEquals("token", XSDType.getSampleValue("NMTOKEN"));
        assertEquals("token", XSDType.getSampleValue("NMTOKENS"));
    }

    public void testGetSampleValue_NameTypes() {
        assertEquals("name", XSDType.getSampleValue("Name"));
        assertEquals("name", XSDType.getSampleValue("NCName"));
        assertEquals("name", XSDType.getSampleValue("ID"));
        assertEquals("name", XSDType.getSampleValue("IDREF"));
        assertEquals("name", XSDType.getSampleValue("IDREFS"));
        assertEquals("name", XSDType.getSampleValue("ENTITY"));
        assertEquals("name", XSDType.getSampleValue("ENTITIES"));
        assertEquals("name", XSDType.getSampleValue("base64Binary"));
        assertEquals("name", XSDType.getSampleValue("notation"));
        assertEquals("name", XSDType.getSampleValue("anyURI"));
        assertEquals("name", XSDType.getSampleValue("anyType"));
    }

    public void testGetSampleValue_SpecialTypes() {
        assertEquals("false", XSDType.getSampleValue("boolean"));
        assertEquals("en", XSDType.getSampleValue("language"));
        assertEquals("xs:name", XSDType.getSampleValue("QName"));
        assertEquals("FF", XSDType.getSampleValue("hexBinary"));
        assertEquals("3.142", XSDType.getSampleValue("double"));
        assertEquals("3.142", XSDType.getSampleValue("float"));
        assertEquals("P2Y", XSDType.getSampleValue("duration"));
    }

    public void testGetSampleValue_DateTimeTypes() {
        String dateValue = XSDType.getSampleValue("date");
        assertNotNull(dateValue);
        assertTrue(dateValue.matches("\\d{4}-\\d{2}-\\d{2}"));

        String timeValue = XSDType.getSampleValue("time");
        assertNotNull(timeValue);
        assertTrue(timeValue.matches("\\d{2}:\\d{2}:\\d{2}"));

        String dateTimeValue = XSDType.getSampleValue("dateTime");
        assertNotNull(dateTimeValue);
        assertTrue(dateTimeValue.contains("T"));

        String gDayValue = XSDType.getSampleValue("gDay");
        assertNotNull(gDayValue);
        assertTrue(gDayValue.startsWith("---"));

        String gMonthValue = XSDType.getSampleValue("gMonth");
        assertNotNull(gMonthValue);
        assertTrue(gMonthValue.startsWith("--"));

        String gMonthDayValue = XSDType.getSampleValue("gMonthDay");
        assertNotNull(gMonthDayValue);
        assertTrue(gMonthDayValue.startsWith("--"));

        String gYearMonthValue = XSDType.getSampleValue("gYearMonth");
        assertNotNull(gYearMonthValue);
        assertTrue(gYearMonthValue.matches("\\d{4}-\\d{2}"));
    }

    public void testGetSampleValue_InvalidType() {
        assertEquals("name", XSDType.getSampleValue("invalid"));
        assertEquals("name", XSDType.getSampleValue(null));
        assertEquals("name", XSDType.getSampleValue(""));
    }

    // Test getConstrainingFacetMap() switch - all facet patterns
    public void testGetConstrainingFacetMap_IntegerTypes() {
        char[] expected = "111100010111".toCharArray();
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("integer"));
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("positiveInteger"));
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("negativeInteger"));
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("nonPositiveInteger"));
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("nonNegativeInteger"));
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("int"));
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("long"));
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("short"));
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("unsignedLong"));
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("unsignedInt"));
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("unsignedShort"));
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("unsignedByte"));
    }

    public void testGetConstrainingFacetMap_StringTypes() {
        char[] expected = "000011100111".toCharArray();
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("string"));
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("normalizedString"));
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("token"));
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("language"));
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("NMTOKEN"));
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("NMTOKENS"));
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("Name"));
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("NCName"));
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("ID"));
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("IDREF"));
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("IDREFS"));
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("ENTITY"));
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("ENTITIES"));
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("QName"));
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("hexBinary"));
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("base64Binary"));
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("notation"));
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("anyURI"));
    }

    public void testGetConstrainingFacetMap_DateTimeAndFloatTypes() {
        char[] expected = "111100000111".toCharArray();
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("double"));
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("float"));
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("date"));
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("time"));
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("dateTime"));
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("duration"));
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("gDay"));
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("gMonth"));
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("gYear"));
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("gMonthDay"));
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("gYearMonth"));
    }

    public void testGetConstrainingFacetMap_BooleanType() {
        char[] expected = "000000000110".toCharArray();
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("boolean"));
    }

    public void testGetConstrainingFacetMap_ByteType() {
        char[] expected = "111100110111".toCharArray();
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("byte"));
    }

    public void testGetConstrainingFacetMap_DecimalType() {
        char[] expected = "111100011111".toCharArray();
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("decimal"));
    }

    public void testGetConstrainingFacetMap_AnyTypeOrInvalid() {
        char[] expected = "000000000000".toCharArray();
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("anyType"));
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap("invalid"));
        assertArrayEquals(expected, XSDType.getConstrainingFacetMap(null));
    }

    // Test type checking methods
    public void testIsNumericType_AllCases() {
        // Positive cases
        assertTrue(XSDType.isNumericType("integer"));
        assertTrue(XSDType.isNumericType("int"));
        assertTrue(XSDType.isNumericType("long"));
        assertTrue(XSDType.isNumericType("double"));
        assertTrue(XSDType.isNumericType("float"));
        assertTrue(XSDType.isNumericType("decimal"));

        // Negative cases
        assertFalse(XSDType.isNumericType("string"));
        assertFalse(XSDType.isNumericType("boolean"));
        assertFalse(XSDType.isNumericType("date"));
        assertFalse(XSDType.isNumericType(null));
    }

    public void testIsStringType_AllCases() {
        // Positive cases
        assertTrue(XSDType.isStringType("string"));
        assertTrue(XSDType.isStringType("normalizedString"));
        assertTrue(XSDType.isStringType("token"));

        // Negative cases
        assertFalse(XSDType.isStringType("integer"));
        assertFalse(XSDType.isStringType("boolean"));
        assertFalse(XSDType.isStringType(null));
    }

    public void testIsIntegralType_AllCases() {
        // Positive cases
        assertTrue(XSDType.isIntegralType("integer"));
        assertTrue(XSDType.isIntegralType("int"));
        assertTrue(XSDType.isIntegralType("long"));
        assertTrue(XSDType.isIntegralType("short"));
        assertTrue(XSDType.isIntegralType("byte"));
        assertTrue(XSDType.isIntegralType("unsignedByte"));

        // Negative cases
        assertFalse(XSDType.isIntegralType("double"));
        assertFalse(XSDType.isIntegralType("string"));
        assertFalse(XSDType.isIntegralType(null));
    }

    public void testIsFloatType_AllCases() {
        // Positive cases
        assertTrue(XSDType.isFloatType("double"));
        assertTrue(XSDType.isFloatType("float"));
        assertTrue(XSDType.isFloatType("decimal"));

        // Negative cases
        assertFalse(XSDType.isFloatType("integer"));
        assertFalse(XSDType.isFloatType("string"));
        assertFalse(XSDType.isFloatType(null));
    }

    public void testIsBooleanType_AllCases() {
        // Positive case
        assertTrue(XSDType.isBooleanType("boolean"));

        // Negative cases
        assertFalse(XSDType.isBooleanType("integer"));
        assertFalse(XSDType.isBooleanType("string"));
        assertFalse(XSDType.isBooleanType(null));
    }

    public void testIsDateType_AllCases() {
        // Positive cases
        assertTrue(XSDType.isDateType("date"));
        assertTrue(XSDType.isDateType("time"));
        assertTrue(XSDType.isDateType("dateTime"));

        // Negative cases
        assertFalse(XSDType.isDateType("duration"));
        assertFalse(XSDType.isDateType("string"));
        assertFalse(XSDType.isDateType(null));
    }

    public void testIsListType_AllCases() {
        // Positive cases
        assertTrue(XSDType.isListType("NMTOKENS"));
        assertTrue(XSDType.isListType("ENTITIES"));
        assertTrue(XSDType.isListType("IDREFS"));

        // Negative cases
        assertFalse(XSDType.isListType("NMTOKEN"));
        assertFalse(XSDType.isListType("string"));
        assertFalse(XSDType.isListType(null));
    }

    public void testIsBinaryType_AllCases() {
        // Positive cases
        assertTrue(XSDType.isBinaryType("hexBinary"));
        assertTrue(XSDType.isBinaryType("base64Binary"));

        // Negative cases
        assertFalse(XSDType.isBinaryType("string"));
        assertFalse(XSDType.isBinaryType("integer"));
        assertFalse(XSDType.isBinaryType(null));
    }

    // Helper method
    private void assertArrayEquals(char[] expected, char[] actual) {
        assertEquals("Array length mismatch", expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals("Mismatch at index " + i, expected[i], actual[i]);
        }
    }
}
