package org.yawlfoundation.yawl.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for StringUtil - the core string manipulation utility class.
 *
 * Covers token replacement, string reversal, whitespace removal, XML wrapping,
 * unwrapping, capitalisation, encoding helpers, and type-conversion utilities.
 *
 * @author YAWL Foundation
 * @since 5.2
 */
class TestStringUtil {

    @Test
    void testReplaceTokensSubstitutesAllOccurrences() {
        String result = StringUtil.replaceTokens("hello world hello", "hello", "bye");
        assertEquals("bye world bye", result);
    }

    @Test
    void testReplaceTokensWithNoMatchReturnsOriginal() {
        String result = StringUtil.replaceTokens("unchanged text", "missing", "replacement");
        assertEquals("unchanged text", result);
    }

    @Test
    void testReplaceTokensWithEmptyToTokenRemovesFromToken() {
        String result = StringUtil.replaceTokens("aXbXc", "X", "");
        assertEquals("abc", result);
    }

    @Test
    void testReverseStringReturnsReversedSequence() {
        assertEquals("cba", StringUtil.reverseString("abc"));
    }

    @Test
    void testReverseStringOfSingleCharReturnsSameChar() {
        assertEquals("z", StringUtil.reverseString("z"));
    }

    @Test
    void testReverseStringOfPalindromeReturnsSelf() {
        assertEquals("racecar", StringUtil.reverseString("racecar"));
    }

    @Test
    void testRemoveAllWhiteSpaceEliminatesSpacesTabsNewlines() {
        String result = StringUtil.removeAllWhiteSpace("a b\tc\nd");
        assertEquals("abcd", result);
    }

    @Test
    void testCapitaliseConvertsFirstLetterToUpperCase() {
        assertEquals("Hello", StringUtil.capitalise("hello"));
    }

    @Test
    void testCapitaliseConvertsAllOtherLettersToLowerCase() {
        assertEquals("Hello world", StringUtil.capitalise("HELLO WORLD"));
    }

    @Test
    void testCapitaliseWithNullReturnsNull() {
        assertNull(StringUtil.capitalise(null));
    }

    @Test
    void testCapitaliseWithEmptyStringReturnsEmptyString() {
        assertEquals("", StringUtil.capitalise(""));
    }

    @Test
    void testWrapProducesOpenCloseTagsAroundContent() {
        String result = StringUtil.wrap("content", "tag");
        assertEquals("<tag>content</tag>", result);
    }

    @Test
    void testWrapWithNullContentProducesSelfClosingTag() {
        String result = StringUtil.wrap(null, "tag");
        assertEquals("<tag/>", result);
    }

    @Test
    void testUnwrapRemovesOuterTags() {
        String result = StringUtil.unwrap("<tag>inner content</tag>");
        assertEquals("inner content", result);
    }

    @Test
    void testUnwrapOfSelfClosingTagReturnsEmptyString() {
        String result = StringUtil.unwrap("<tag/>");
        assertEquals("", result);
    }

    @Test
    void testUnwrapWithNullReturnsNull() {
        assertNull(StringUtil.unwrap(null));
    }

    @Test
    void testIsNullOrEmptyReturnsTrueForNull() {
        assertTrue(StringUtil.isNullOrEmpty(null));
    }

    @Test
    void testIsNullOrEmptyReturnsTrueForEmpty() {
        assertTrue(StringUtil.isNullOrEmpty(""));
    }

    @Test
    void testIsNullOrEmptyReturnsFalseForNonEmpty() {
        assertFalse(StringUtil.isNullOrEmpty("x"));
    }

    @Test
    void testIsIntegerStringReturnsTrueForPositiveInteger() {
        assertTrue(StringUtil.isIntegerString("42"));
    }

    @Test
    void testIsIntegerStringReturnsFalseForNegativeInteger() {
        // isIntegerString only accepts digit characters 0-9; the '-' sign returns false
        assertFalse(StringUtil.isIntegerString("-7"));
    }

    @Test
    void testIsIntegerStringReturnsFalseForDecimal() {
        assertFalse(StringUtil.isIntegerString("3.14"));
    }

    @Test
    void testIsIntegerStringReturnsFalseForText() {
        assertFalse(StringUtil.isIntegerString("hello"));
    }

    @Test
    void testStrToIntConvertsValidString() {
        assertEquals(100, StringUtil.strToInt("100", 0));
    }

    @Test
    void testStrToIntReturnsDefaultForInvalidString() {
        assertEquals(-1, StringUtil.strToInt("notanumber", -1));
    }

    @Test
    void testStrToLongConvertsValidString() {
        assertEquals(9999999999L, StringUtil.strToLong("9999999999", 0L));
    }

    @Test
    void testStrToLongReturnsDefaultForInvalidString() {
        assertEquals(0L, StringUtil.strToLong("bad", 0L));
    }

    @Test
    void testStrToDoubleConvertsValidString() {
        assertEquals(3.14, StringUtil.strToDouble("3.14", 0.0), 0.0001);
    }

    @Test
    void testStrToDoubleReturnsDefaultForInvalidString() {
        assertEquals(0.0, StringUtil.strToDouble("bad", 0.0), 0.0001);
    }

    @Test
    void testStrToBooleanReturnsTrueForTrueString() {
        assertTrue(StringUtil.strToBoolean("true"));
    }

    @Test
    void testStrToBooleanReturnsFalseForFalseString() {
        assertFalse(StringUtil.strToBoolean("false"));
    }

    @Test
    void testFormatTimeFormatsMillisecondDurationCorrectly() {
        long oneHourInMs = 3600000L;
        String result = StringUtil.formatTime(oneHourInMs);
        assertNotNull(result);
        assertTrue(result.contains(":"), "Time format should contain ':' separators");
    }

    @Test
    void testFormatPostCodeInsertsSpaceBeforeLastThreeChars() {
        String result = StringUtil.formatPostCode("SW1A1AA");
        assertEquals("SW1A 1AA", result);
    }

    @Test
    void testFormatPostCodeWithNullReturnsNull() {
        assertNull(StringUtil.formatPostCode(null));
    }

    @Test
    void testFormatSortCodeFormatsWithDashes() {
        String result = StringUtil.formatSortCode("123456");
        assertEquals("12-34-56", result);
    }
}
