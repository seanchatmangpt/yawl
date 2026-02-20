/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.util;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Comprehensive unit tests for {@link StringUtil} Optional methods.
 *
 * <p>Tests the TPS-compliant methods that return Optional instead of null,
 * ensuring proper error visibility and null handling.
 *
 * @author YAWL Foundation
 * @since YAWL 6.0.0
 */
@DisplayName("StringUtil Optional Methods")
@Tag("unit")
class TestStringUtilOptional {


    // =========================================================================
    // formatSortCodeOptional
    // =========================================================================

    @Nested
    @DisplayName("formatSortCodeOptional")
    class FormatSortCodeOptional {

        @Test
        @DisplayName("formats valid 6-character sortcode")
        void formatsValidSortcode() {
            Optional<String> result = StringUtil.formatSortCodeOptional("123456");
            assertTrue(result.isPresent());
            assertEquals("12-34-56", result.get());
        }

        @Test
        @DisplayName("returns empty for null input")
        void returnsEmptyForNull() {
            Optional<String> result = StringUtil.formatSortCodeOptional(null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("returns empty for too-short sortcode")
        void returnsEmptyForTooShort() {
            Optional<String> result = StringUtil.formatSortCodeOptional("12345");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("formats longer sortcode using first 6 chars")
        void formatsLongerSortcode() {
            Optional<String> result = StringUtil.formatSortCodeOptional("123456789");
            assertTrue(result.isPresent());
            assertEquals("12-34-56", result.get());
        }
    }


    // =========================================================================
    // capitaliseOptional
    // =========================================================================

    @Nested
    @DisplayName("capitaliseOptional")
    class CapitaliseOptional {

        @Test
        @DisplayName("capitalises first letter and lowercases rest")
        void capitalisesCorrectly() {
            Optional<String> result = StringUtil.capitaliseOptional("hELLO");
            assertTrue(result.isPresent());
            assertEquals("Hello", result.get());
        }

        @Test
        @DisplayName("returns empty for null input")
        void returnsEmptyForNull() {
            Optional<String> result = StringUtil.capitaliseOptional(null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("returns empty string for empty input")
        void returnsEmptyForEmpty() {
            Optional<String> result = StringUtil.capitaliseOptional("");
            assertTrue(result.isPresent());
            assertEquals("", result.get());
        }

        @Test
        @DisplayName("handles single character")
        void handlesSingleCharacter() {
            Optional<String> result = StringUtil.capitaliseOptional("a");
            assertTrue(result.isPresent());
            assertEquals("A", result.get());
        }
    }


    // =========================================================================
    // formatUIDateOptional
    // =========================================================================

    @Nested
    @DisplayName("formatUIDateOptional")
    class FormatUIDateOptional {

        @Test
        @DisplayName("formats calendar with time component")
        void formatsCalendarWithTime() {
            Calendar cal = new GregorianCalendar(2025, Calendar.JANUARY, 15, 10, 30, 0);
            Optional<String> result = StringUtil.formatUIDateOptional(cal);
            assertTrue(result.isPresent());
            assertTrue(result.get().contains("10:30"));
        }

        @Test
        @DisplayName("formats calendar without time component (midnight)")
        void formatsCalendarWithoutTime() {
            Calendar cal = new GregorianCalendar(2025, Calendar.JANUARY, 15, 0, 0, 0);
            Optional<String> result = StringUtil.formatUIDateOptional(cal);
            assertTrue(result.isPresent());
            assertFalse(result.get().contains(":"));
        }

        @Test
        @DisplayName("returns empty for null input")
        void returnsEmptyForNull() {
            Optional<String> result = StringUtil.formatUIDateOptional(null);
            assertTrue(result.isEmpty());
        }
    }


    // =========================================================================
    // formatDecimalCostOptional
    // =========================================================================

    @Nested
    @DisplayName("formatDecimalCostOptional")
    class FormatDecimalCostOptional {

        @Test
        @DisplayName("formats BigDecimal with currency symbol")
        void formatsWithCurrency() {
            Optional<String> result = StringUtil.formatDecimalCostOptional(new BigDecimal("10.50"));
            assertTrue(result.isPresent());
            assertTrue(result.get().contains("10.50"));
        }

        @Test
        @DisplayName("returns empty for null input")
        void returnsEmptyForNull() {
            Optional<String> result = StringUtil.formatDecimalCostOptional(null);
            assertTrue(result.isEmpty());
        }
    }


    // =========================================================================
    // convertThrowableToStringOptional
    // =========================================================================

    @Nested
    @DisplayName("convertThrowableToStringOptional")
    class ConvertThrowableToStringOptional {

        @Test
        @DisplayName("converts exception to string with stack trace")
        void convertsException() {
            Exception ex = new RuntimeException("Test error");
            Optional<String> result = StringUtil.convertThrowableToStringOptional(ex);
            assertTrue(result.isPresent());
            assertTrue(result.get().contains("RuntimeException"));
            assertTrue(result.get().contains("Test error"));
        }

        @Test
        @DisplayName("returns empty for null input")
        void returnsEmptyForNull() {
            Optional<String> result = StringUtil.convertThrowableToStringOptional(null);
            assertTrue(result.isEmpty());
        }
    }


    // =========================================================================
    // formatForHTMLOptional
    // =========================================================================

    @Nested
    @DisplayName("formatForHTMLOptional")
    class FormatForHTMLOptional {

        @Test
        @DisplayName("escapes HTML entities")
        void escapesHtml() {
            Optional<String> result = StringUtil.formatForHTMLOptional("<script>alert('xss')</script>");
            assertTrue(result.isPresent());
            assertTrue(result.get().contains("&lt;"));
            assertTrue(result.get().contains("&gt;"));
        }

        @Test
        @DisplayName("replaces newlines with br tags")
        void replacesNewlines() {
            Optional<String> result = StringUtil.formatForHTMLOptional("line1\nline2");
            assertTrue(result.isPresent());
            assertTrue(result.get().contains("<br>"));
        }

        @Test
        @DisplayName("returns empty for null input")
        void returnsEmptyForNull() {
            Optional<String> result = StringUtil.formatForHTMLOptional(null);
            assertTrue(result.isEmpty());
        }
    }


    // =========================================================================
    // reverseStringOptional
    // =========================================================================

    @Nested
    @DisplayName("reverseStringOptional")
    class ReverseStringOptional {

        @Test
        @DisplayName("reverses string")
        void reversesString() {
            Optional<String> result = StringUtil.reverseStringOptional("hello");
            assertTrue(result.isPresent());
            assertEquals("olleh", result.get());
        }

        @Test
        @DisplayName("returns empty for null input")
        void returnsEmptyForNull() {
            Optional<String> result = StringUtil.reverseStringOptional(null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("handles empty string")
        void handlesEmptyString() {
            Optional<String> result = StringUtil.reverseStringOptional("");
            assertTrue(result.isPresent());
            assertEquals("", result.get());
        }
    }


    // =========================================================================
    // removeAllWhiteSpaceOptional
    // =========================================================================

    @Nested
    @DisplayName("removeAllWhiteSpaceOptional")
    class RemoveAllWhiteSpaceOptional {

        @Test
        @DisplayName("removes all whitespace")
        void removesWhitespace() {
            Optional<String> result = StringUtil.removeAllWhiteSpaceOptional("a b c");
            assertTrue(result.isPresent());
            assertEquals("abc", result.get());
        }

        @Test
        @DisplayName("returns empty for null input")
        void returnsEmptyForNull() {
            Optional<String> result = StringUtil.removeAllWhiteSpaceOptional(null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("removes tabs and newlines")
        void removesTabsAndNewlines() {
            Optional<String> result = StringUtil.removeAllWhiteSpaceOptional("a\tb\nc");
            assertTrue(result.isPresent());
            assertEquals("abc", result.get());
        }
    }


    // =========================================================================
    // formatPostCodeOptional
    // =========================================================================

    @Nested
    @DisplayName("formatPostCodeOptional")
    class FormatPostCodeOptional {

        @Test
        @DisplayName("formats UK postcode with space")
        void formatsPostcode() {
            Optional<String> result = StringUtil.formatPostCodeOptional("SW1A1AA");
            assertTrue(result.isPresent());
            assertEquals("SW1A 1AA", result.get());
        }

        @Test
        @DisplayName("returns empty for null input")
        void returnsEmptyForNull() {
            Optional<String> result = StringUtil.formatPostCodeOptional(null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("returns empty for empty input")
        void returnsEmptyForEmpty() {
            Optional<String> result = StringUtil.formatPostCodeOptional("");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("handles short postcode")
        void handlesShortPostcode() {
            Optional<String> result = StringUtil.formatPostCodeOptional("SW1");
            assertTrue(result.isPresent());
            // formatPostCode adds space separator, but for short codes it's at the front
            assertTrue(result.get().contains("SW1"));
        }
    }


    // =========================================================================
    // unwrapOptional
    // =========================================================================

    @Nested
    @DisplayName("unwrapOptional")
    class UnwrapOptional {

        @Test
        @DisplayName("unwraps XML tags")
        void unwrapsXml() {
            Optional<String> result = StringUtil.unwrapOptional("<root>content</root>");
            assertTrue(result.isPresent());
            assertEquals("content", result.get());
        }

        @Test
        @DisplayName("returns empty for null input")
        void returnsEmptyForNull() {
            Optional<String> result = StringUtil.unwrapOptional(null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("returns empty for malformed XML")
        void returnsEmptyForMalformed() {
            Optional<String> result = StringUtil.unwrapOptional("no tags here");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("returns empty string for self-closing tag")
        void returnsEmptyForSelfClosing() {
            Optional<String> result = StringUtil.unwrapOptional("<tag/>");
            assertTrue(result.isPresent());
            assertEquals("", result.get());
        }
    }


    // =========================================================================
    // enQuoteOptional
    // =========================================================================

    @Nested
    @DisplayName("enQuoteOptional")
    class EnQuoteOptional {

        @Test
        @DisplayName("wraps string in quotes")
        void wrapsInQuotes() {
            Optional<String> result = StringUtil.enQuoteOptional("hello", '"');
            assertTrue(result.isPresent());
            assertEquals("\"hello\"", result.get());
        }

        @Test
        @DisplayName("returns empty for null input")
        void returnsEmptyForNull() {
            Optional<String> result = StringUtil.enQuoteOptional(null, '"');
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("handles single quotes")
        void handlesSingleQuotes() {
            Optional<String> result = StringUtil.enQuoteOptional("hello", '\'');
            assertTrue(result.isPresent());
            assertEquals("'hello'", result.get());
        }
    }


    // =========================================================================
    // xmlEncodeOptional / xmlDecodeOptional
    // =========================================================================

    @Nested
    @DisplayName("xmlEncodeOptional / xmlDecodeOptional")
    class XmlEncodeDecodeOptional {

        @Test
        @DisplayName("encodes string for XML")
        void encodesXml() {
            Optional<String> result = StringUtil.xmlEncodeOptional("hello world");
            assertTrue(result.isPresent());
            assertEquals("hello+world", result.get());
        }

        @Test
        @DisplayName("returns empty for null input")
        void returnsEmptyForNullEncode() {
            Optional<String> result = StringUtil.xmlEncodeOptional(null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("decodes XML string")
        void decodesXml() {
            Optional<String> result = StringUtil.xmlDecodeOptional("hello+world");
            assertTrue(result.isPresent());
            assertEquals("hello world", result.get());
        }

        @Test
        @DisplayName("returns empty for null input decode")
        void returnsEmptyForNullDecode() {
            Optional<String> result = StringUtil.xmlDecodeOptional(null);
            assertTrue(result.isEmpty());
        }
    }


    // =========================================================================
    // fileToStringOptional
    // =========================================================================

    @Nested
    @DisplayName("fileToStringOptional")
    class FileToStringOptional {

        @Test
        @DisplayName("reads file contents")
        void readsFileContents(@TempDir File tempDir) throws IOException {
            File testFile = new File(tempDir, "test.txt");
            Files.writeString(testFile.toPath(), "test content");
            Optional<String> result = StringUtil.fileToStringOptional(testFile);
            assertTrue(result.isPresent());
            assertEquals("test content", result.get());
        }

        @Test
        @DisplayName("returns empty for null file")
        void returnsEmptyForNull() {
            Optional<String> result = StringUtil.fileToStringOptional((File) null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("returns empty for non-existent file")
        void returnsEmptyForNonExistent() {
            Optional<String> result = StringUtil.fileToStringOptional(new File("/nonexistent/file.txt"));
            assertTrue(result.isEmpty());
        }
    }


    // =========================================================================
    // stringToFileOptional
    // =========================================================================

    @Nested
    @DisplayName("stringToFileOptional")
    class StringToFileOptional {

        @Test
        @DisplayName("writes string to file")
        void writesToFile(@TempDir File tempDir) throws IOException {
            File testFile = new File(tempDir, "output.txt");
            Optional<File> result = StringUtil.stringToFileOptional(testFile, "test content");
            assertTrue(result.isPresent());
            assertEquals("test content", Files.readString(testFile.toPath()));
        }

        @Test
        @DisplayName("returns empty for null file")
        void returnsEmptyForNullFile() {
            Optional<File> result = StringUtil.stringToFileOptional(null, "content");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("returns empty for null contents")
        void returnsEmptyForNullContents(@TempDir File tempDir) {
            File testFile = new File(tempDir, "output.txt");
            Optional<File> result = StringUtil.stringToFileOptional(testFile, null);
            assertTrue(result.isEmpty());
        }
    }


    // =========================================================================
    // extractOptional
    // =========================================================================

    @Nested
    @DisplayName("extractOptional")
    class ExtractOptional {

        @Test
        @DisplayName("extracts matching pattern")
        void extractsPattern() {
            Optional<String> result = StringUtil.extractOptional("hello123world", "\\d+");
            assertTrue(result.isPresent());
            assertEquals("123", result.get());
        }

        @Test
        @DisplayName("returns empty for no match")
        void returnsEmptyForNoMatch() {
            Optional<String> result = StringUtil.extractOptional("hello world", "\\d+");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("returns empty for null source")
        void returnsEmptyForNullSource() {
            Optional<String> result = StringUtil.extractOptional(null, "\\d+");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("returns empty for null pattern")
        void returnsEmptyForNullPattern() {
            Optional<String> result = StringUtil.extractOptional("hello", null);
            assertTrue(result.isEmpty());
        }
    }


    // =========================================================================
    // xmlDateToLongOptional
    // =========================================================================

    @Nested
    @DisplayName("xmlDateToLongOptional")
    class XmlDateToLongOptional {

        @Test
        @DisplayName("converts valid XML date")
        void convertsValidDate() {
            Optional<Long> result = StringUtil.xmlDateToLongOptional("2025-01-15T10:30:00Z");
            assertTrue(result.isPresent());
            assertTrue(result.get() > 0);
        }

        @Test
        @DisplayName("returns empty for null input")
        void returnsEmptyForNull() {
            Optional<Long> result = StringUtil.xmlDateToLongOptional(null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("returns empty for invalid format")
        void returnsEmptyForInvalid() {
            Optional<Long> result = StringUtil.xmlDateToLongOptional("not-a-date");
            assertTrue(result.isEmpty());
        }
    }


    // =========================================================================
    // longToDateTimeOptional
    // =========================================================================

    @Nested
    @DisplayName("longToDateTimeOptional")
    class LongToDateTimeOptional {

        @Test
        @DisplayName("converts timestamp to XML datetime")
        void convertsTimestamp() {
            long timestamp = System.currentTimeMillis();
            Optional<String> result = StringUtil.longToDateTimeOptional(timestamp);
            assertTrue(result.isPresent());
            assertTrue(result.get().contains("202"));
        }
    }


    // =========================================================================
    // findOptional
    // =========================================================================

    @Nested
    @DisplayName("findOptional")
    class FindOptional {

        @Test
        @DisplayName("finds substring index")
        void findsSubstring() {
            Optional<Integer> result = StringUtil.findOptional("hello world", "world", 0);
            assertTrue(result.isPresent());
            assertEquals(6, result.get());
        }

        @Test
        @DisplayName("returns empty for null source")
        void returnsEmptyForNullSource() {
            Optional<Integer> result = StringUtil.findOptional(null, "test", 0);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("returns empty for null pattern")
        void returnsEmptyForNullPattern() {
            Optional<Integer> result = StringUtil.findOptional("hello", null, 0);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("returns empty when not found")
        void returnsEmptyWhenNotFound() {
            Optional<Integer> result = StringUtil.findOptional("hello", "xyz", 0);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("finds with case insensitive")
        void findsCaseInsensitive() {
            Optional<Integer> result = StringUtil.findOptional("HELLO WORLD", "world", 0, true);
            assertTrue(result.isPresent());
            assertEquals(6, result.get());
        }
    }


    // =========================================================================
    // findAllOptional
    // =========================================================================

    @Nested
    @DisplayName("findAllOptional")
    class FindAllOptional {

        @Test
        @DisplayName("finds all occurrences")
        void findsAll() {
            Optional<List<Integer>> result = StringUtil.findAllOptional("ababab", "ab");
            assertTrue(result.isPresent());
            assertEquals(3, result.get().size());
            assertEquals(0, result.get().get(0));
            assertEquals(2, result.get().get(1));
            assertEquals(4, result.get().get(2));
        }

        @Test
        @DisplayName("returns empty for null source")
        void returnsEmptyForNullSource() {
            Optional<List<Integer>> result = StringUtil.findAllOptional(null, "test");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("returns empty for null pattern")
        void returnsEmptyForNullPattern() {
            Optional<List<Integer>> result = StringUtil.findAllOptional("hello", null);
            assertTrue(result.isEmpty());
        }
    }


    // =========================================================================
    // joinOptional
    // =========================================================================

    @Nested
    @DisplayName("joinOptional")
    class JoinOptional {

        @Test
        @DisplayName("joins list with separator")
        void joinsList() {
            Optional<String> result = StringUtil.joinOptional(Arrays.asList("a", "b", "c"), ',');
            assertTrue(result.isPresent());
            assertEquals("a,b,c", result.get());
        }

        @Test
        @DisplayName("returns empty for null list")
        void returnsEmptyForNull() {
            Optional<String> result = StringUtil.joinOptional(null, ',');
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("returns empty string for empty list")
        void returnsEmptyForEmptyList() {
            Optional<String> result = StringUtil.joinOptional(Collections.emptyList(), ',');
            assertTrue(result.isPresent());
            assertEquals("", result.get());
        }

        @Test
        @DisplayName("handles single element")
        void handlesSingleElement() {
            Optional<String> result = StringUtil.joinOptional(Collections.singletonList("only"), ',');
            assertTrue(result.isPresent());
            assertEquals("only", result.get());
        }
    }


    // =========================================================================
    // setToXMLOptional
    // =========================================================================

    @Nested
    @DisplayName("setToXMLOptional")
    class SetToXMLOptional {

        @Test
        @DisplayName("converts set to XML")
        void convertsSetToXml() {
            Optional<String> result = StringUtil.setToXMLOptional(Set.of("a", "b"));
            assertTrue(result.isPresent());
            assertTrue(result.get().startsWith("<set>"));
            assertTrue(result.get().endsWith("</set>"));
        }

        @Test
        @DisplayName("returns empty for null input")
        void returnsEmptyForNull() {
            Optional<String> result = StringUtil.setToXMLOptional(null);
            assertTrue(result.isEmpty());
        }
    }


    // =========================================================================
    // strToDuration
    // =========================================================================

    @Nested
    @DisplayName("strToDuration")
    class StrToDuration {

        @Test
        @DisplayName("parses valid duration")
        void parsesValidDuration() {
            var result = StringUtil.strToDuration("P1Y2M3DT4H5M6S");
            assertTrue(result.isPresent());
        }

        @Test
        @DisplayName("returns empty for null input")
        void returnsEmptyForNull() {
            var result = StringUtil.strToDuration(null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("returns empty for invalid format")
        void returnsEmptyForInvalid() {
            var result = StringUtil.strToDuration("invalid");
            assertTrue(result.isEmpty());
        }
    }


    // =========================================================================
    // msecsToDuration
    // =========================================================================

    @Nested
    @DisplayName("msecsToDuration")
    class MsecsToDuration {

        @Test
        @DisplayName("creates duration from milliseconds")
        void createsFromMillis() {
            var result = StringUtil.msecsToDuration(3600000); // 1 hour
            assertTrue(result.isPresent());
        }

        @Test
        @DisplayName("returns empty for negative value")
        void returnsEmptyForNegative() {
            var result = StringUtil.msecsToDuration(-1);
            assertTrue(result.isEmpty());
        }
    }


    // =========================================================================
    // Backward compatibility - deprecated methods
    // =========================================================================

    @Nested
    @DisplayName("Deprecated methods backward compatibility")
    class BackwardCompatibility {

        @Test
        @DisplayName("formatSortCode throws on null")
        void formatSortCodeThrowsOnNull() {
            assertThrows(NullPointerException.class, () -> StringUtil.formatSortCode(null));
        }

        @Test
        @DisplayName("capitalise returns null on null")
        void capitaliseReturnsNullOnNull() {
            assertNull(StringUtil.capitalise(null));
        }

        @Test
        @DisplayName("formatUIDate throws on null")
        void formatUIDateThrowsOnNull() {
            assertThrows(NullPointerException.class, () -> StringUtil.formatUIDate(null));
        }

        @Test
        @DisplayName("formatDecimalCost throws on null")
        void formatDecimalCostThrowsOnNull() {
            assertThrows(NullPointerException.class, () -> StringUtil.formatDecimalCost(null));
        }

        @Test
        @DisplayName("convertThrowableToString returns null on null")
        void convertThrowableToStringReturnsNullOnNull() {
            assertNull(StringUtil.convertThrowableToString(null));
        }

        @Test
        @DisplayName("formatForHTML returns null on null")
        void formatForHTMLReturnsNullOnNull() {
            assertNull(StringUtil.formatForHTML(null));
        }

        @Test
        @DisplayName("reverseString throws on null")
        void reverseStringThrowsOnNull() {
            assertThrows(NullPointerException.class, () -> StringUtil.reverseString(null));
        }

        @Test
        @DisplayName("removeAllWhiteSpace throws on null")
        void removeAllWhiteSpaceThrowsOnNull() {
            assertThrows(NullPointerException.class, () -> StringUtil.removeAllWhiteSpace(null));
        }

        @Test
        @DisplayName("xmlDateToLong returns -1 on null")
        void xmlDateToLongReturnsNegOneOnNull() {
            assertEquals(-1, StringUtil.xmlDateToLong(null));
        }

        @Test
        @DisplayName("find returns -1 on null")
        void findReturnsNegOneOnNull() {
            assertEquals(-1, StringUtil.find(null, "test", 0));
            assertEquals(-1, StringUtil.find("test", null, 0));
        }
    }
}
