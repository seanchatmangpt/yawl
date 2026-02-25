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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Set;

/**
 * Comprehensive unit tests for {@link StringUtil} methods that are not covered
 * in TestStringUtilOptional.
 *
 * <p>This class tests:
 * - Legacy methods (deprecated but still used)
 * - Utility methods without Optional wrappers
 * - String manipulation methods
 * - Date/time formatting methods
 * - File I/O methods
 * - XML processing methods
 *
 * @author YAWL Foundation
 * @since YAWL 6.0.0
 */
@DisplayName("StringUtil Core Methods")
@Tag("unit")
class StringUtilTest {

    // =========================================================================
    // Basic String Manipulation Methods
    // =========================================================================

    @Nested
    @DisplayName("replaceTokens")
    class ReplaceTokens {

        @Test
        @DisplayName("replaces single token")
        void replacesSingleToken() {
            String result = StringUtil.replaceTokens("hello world", "world", "universe");
            assertEquals("hello universe", result);
        }

        @Test
        @DisplayName("replaces multiple tokens")
        void replacesMultipleTokens() {
            String result = StringUtil.replaceTokens("a b c a", "a", "x");
            assertEquals("x b c x", result);
        }

        @Test
        @DisplayName("handles empty from token")
        void handlesEmptyFromToken() {
            String result = StringUtil.replaceTokens("hello", "", "x");
            assertEquals("hello", result);
        }

        @Test
        @DisplayName("handles null from token")
        void handlesNullFromToken() {
            assertThrows(NullPointerException.class, () -> {
                StringUtil.replaceTokens("hello", null, "x");
            });
        }

        @Test
        @DisplayName("handles null to token")
        void handlesNullToToken() {
            String result = StringUtil.replaceTokens("hello world", "world", null);
            assertEquals("hello null", result);
        }

        @Test
        @DisplayName("handles empty buffer")
        void handlesEmptyBuffer() {
            String result = StringUtil.replaceTokens("", "hello", "world");
            assertEquals("", result);
        }

        @Test
        @DisplayName("replaces overlapping tokens")
        void replacesOverlappingTokens() {
            String result = StringUtil.replaceTokens("aaa", "aa", "b");
            assertEquals("ba", result);
        }
    }

    @Nested
    @DisplayName("reverseString")
    class ReverseString {

        @Test
        @DisplayName("reverses normal string")
        void reversesNormalString() {
            String result = StringUtil.reverseString("hello");
            assertEquals("olleh", result);
        }

        @Test
        @DisplayName("reverses empty string")
        void reversesEmptyString() {
            String result = StringUtil.reverseString("");
            assertEquals("", result);
        }

        @Test
        @DisplayName("reverses single character")
        void reversesSingleCharacter() {
            String result = StringUtil.reverseString("a");
            assertEquals("a", result);
        }

        @Test
        @DisplayName("throws NullPointerException for null input")
        void throwsNullPointerExceptionForNull() {
            assertThrows(NullPointerException.class, () -> {
                StringUtil.reverseString(null);
            });
        }
    }

    @Nested
    @DisplayName("removeAllWhiteSpace")
    class RemoveAllWhiteSpace {

        @Test
        @DisplayName("removes spaces")
        void removesSpaces() {
            String result = StringUtil.removeAllWhiteSpace("a b c");
            assertEquals("abc", result);
        }

        @Test
        @DisplayName("removes tabs and newlines")
        void removesTabsAndNewlines() {
            String result = StringUtil.removeAllWhiteSpace("a\tb\nc");
            assertEquals("abc", result);
        }

        @Test
        @DisplayName("handles mixed whitespace")
        void handlesMixedWhitespace() {
            String result = StringUtil.removeAllWhiteSpace("a \t b \n c");
            assertEquals("abc", result);
        }

        @Test
        @DisplayName("handles empty string")
        void handlesEmptyString() {
            String result = StringUtil.removeAllWhiteSpace("");
            assertEquals("", result);
        }

        @Test
        @DisplayName("throws NullPointerException for null input")
        void throwsNullPointerExceptionForNull() {
            assertThrows(NullPointerException.class, () -> {
                StringUtil.removeAllWhiteSpace(null);
            });
        }
    }

    @Nested
    @DisplayName("formatPostCode")
    class FormatPostCode {

        @Test
        @DisplayName("formats UK postcode correctly")
        void formatsPostcode() {
            String result = StringUtil.formatPostCode("SW1A1AA");
            assertEquals("SW1A 1AA", result);
        }

        @Test
        @DisplayName("formats lowercase postcode")
        void formatsLowercase() {
            String result = StringUtil.formatPostCode("sw1a1aa");
            assertEquals("SW1A 1AA", result);
        }

        @Test
        @DisplayName("returns null for null input")
        void returnsNullForNull() {
            String result = StringUtil.formatPostCode(null);
            assertNull(result);
        }

        @Test
        @DisplayName("returns null for empty input")
        void returnsNullForEmpty() {
            String result = StringUtil.formatPostCode("");
            assertNull(result);
        }

        @Test
        @DisplayName("handles short postcode")
        void handlesShortPostcode() {
            String result = StringUtil.formatPostCode("SW1");
            assertEquals("SW1", result);
        }
    }

    @Nested
    @DisplayName("formatSortCode")
    class FormatSortCode {

        @Test
        @DisplayName("formats 6-digit sortcode")
        void formatsSortcode() {
            String result = StringUtil.formatSortCode("123456");
            assertEquals("12-34-56", result);
        }

        @Test
        @DisplayName("throws NullPointerException for null input")
        void throwsNullPointerExceptionForNull() {
            assertThrows(NullPointerException.class, () -> {
                StringUtil.formatSortCode(null);
            });
        }

        @Test
        @DisplayName("throws StringIndexOutOfBoundsException for short input")
        void throwsStringIndexOutOfBoundsExceptionForShort() {
            assertThrows(StringIndexOutOfBoundsException.class, () -> {
                StringUtil.formatSortCode("12345");
            });
        }
    }

    @Nested
    @DisplayName("capitalise")
    class Capitalise {

        @Test
        @DisplayName("capitalises first letter")
        void capitalisesFirstLetter() {
            String result = StringUtil.capitalise("hello");
            assertEquals("Hello", result);
        }

        @Test
        @DisplayName("handles already capitalised")
        void handlesAlreadyCapitalised() {
            String result = StringUtil.capitalise("Hello");
            assertEquals("Hello", result);
        }

        @Test
        @DisplayName("handles single character")
        void handlesSingleCharacter() {
            String result = StringUtil.capitalise("a");
            assertEquals("A", result);
        }

        @Test
        @DisplayName("returns null for null input")
        void returnsNullForNull() {
            String result = StringUtil.capitalise(null);
            assertNull(result);
        }

        @Test
        @DisplayName("returns empty string for empty input")
        void returnsEmptyForEmpty() {
            String result = StringUtil.capitalise("");
            assertEquals("", result);
        }
    }

    // =========================================================================
    // Date/Time Formatting Methods
    // =========================================================================

    @Nested
    @DisplayName("getISOFormattedDate")
    class GetISOFormattedDate {

        @Test
        @DisplayName("formats date to ISO format")
        void formatsDateToIso() {
            Date date = new GregorianCalendar(2025, Calendar.JANUARY, 15, 10, 30, 0).getTime();
            String result = StringUtil.getISOFormattedDate(date);
            assertTrue(result.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
            assertEquals("2025-01-15 10:30:00", result);
        }

        @Test
        @DisplayName("throws NullPointerException for null input")
        void throwsNullPointerExceptionForNull() {
            assertThrows(NullPointerException.class, () -> {
                StringUtil.getISOFormattedDate(null);
            });
        }
    }

    @Nested
    @DisplayName("getDebugMessage")
    class GetDebugMessage {

        @Test
        @DisplayName("prefixes message with timestamp")
        void prefixesMessageWithTimestamp() {
            String result = StringUtil.getDebugMessage("test message");
            assertTrue(result.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} test message"));
        }
    }

    @Nested
    @DisplayName("formatUIDate")
    class FormatUIDate {

        @Test
        @DisplayName("formats calendar with time")
        void formatsCalendarWithTime() {
            Calendar cal = new GregorianCalendar(2025, Calendar.JANUARY, 15, 10, 30, 0);
            String result = StringUtil.formatUIDate(cal);
            assertTrue(result.contains("10:30"));
            assertTrue(result.contains("Jan"));
        }

        @Test
        @DisplayName("formats calendar without time")
        void formatsCalendarWithoutTime() {
            Calendar cal = new GregorianCalendar(2025, Calendar.JANUARY, 15, 0, 0, 0);
            String result = StringUtil.formatUIDate(cal);
            assertFalse(result.contains(":"));
            assertTrue(result.contains("Jan"));
        }

        @Test
        @DisplayName("throws NullPointerException for null input")
        void throwsNullPointerExceptionForNull() {
            assertThrows(NullPointerException.class, () -> {
                StringUtil.formatUIDate(null);
            });
        }
    }

    @Nested
    @DisplayName("formatDecimalCost")
    class FormatDecimalCost {

        @Test
        @DisplayName("formats BigDecimal with currency")
        void formatsWithCurrency() {
            BigDecimal value = new BigDecimal("10.50");
            String result = StringUtil.formatDecimalCost(value);
            assertTrue(result.contains("10.50"));
        }

        @Test
        @DisplayName("throws NullPointerException for null input")
        void throwsNullPointerExceptionForNull() {
            assertThrows(NullPointerException.class, () -> {
                StringUtil.formatDecimalCost(null);
            });
        }
    }

    @Nested
    @DisplayName("formatTime")
    class FormatTime {

        @Test
        @DisplayName("formats milliseconds to time string")
        void formatsMilliseconds() {
            long time = 3661000; // 1 hour, 1 minute, 1 second, 1 millisecond
            String result = StringUtil.formatTime(time);
            assertEquals("0:01:01:01.0001", result);
        }

        @Test
        @DisplayName("formats zero time")
        void formatsZeroTime() {
            String result = StringUtil.formatTime(0);
            assertEquals("0:00:00:00.0000", result);
        }

        @Test
        @DisplayName("formats large time value")
        void formatsLargeTime() {
            long time = 90061000; // 1 day, 1 hour, 1 minute, 1 second
            String result = StringUtil.formatTime(time);
            assertEquals("1:01:01:01.0000", result);
        }
    }

    // =========================================================================
    // XML Processing Methods
    // =========================================================================

    @Nested
    @DisplayName("wrap")
    class Wrap {

        @Test
        @DisplayName("wraps string in XML tag")
        void wrapsString() {
            String result = StringUtil.wrap("content", "root");
            assertEquals("<root>content</root>", result);
        }

        @Test
        @DisplayName("handles null core content")
        void handlesNullCore() {
            String result = StringUtil.wrap(null, "root");
            assertEquals("<root/>", result);
        }

        @Test
        @DisplayName("handles empty tag name")
        void handlesEmptyTagName() {
            String result = StringUtil.wrap("content", "");
            assertEquals("<></>", result);
        }
    }

    @Nested
    @DisplayName("wrapEscaped")
    class WrapEscaped {

        @Test
        @DisplayName("wraps escaped string")
        void wrapsEscapedString() {
            String result = StringUtil.wrapEscaped("content<>&", "root");
            assertTrue(result.contains("<root>"));
            assertTrue(result.contains("</root>"));
        }
    }

    @Nested
    @DisplayName("unwrap")
    class Unwrap {

        @Test
        @DisplayName("unwraps XML tags")
        void unwrapsXml() {
            String result = StringUtil.unwrap("<root>content</root>");
            assertEquals("content", result);
        }

        @Test
        @DisplayName("returns null for null input")
        void returnsNullForNull() {
            String result = StringUtil.unwrap(null);
            assertNull(result);
        }

        @Test
        @DisplayName("returns null for malformed XML")
        void returnsNullForMalformed() {
            String result = StringUtil.unwrap("no tags");
            assertNull(result);
        }

        @Test
        @DisplayName("returns empty string for self-closing tag")
        void returnsEmptyForSelfClosing() {
            String result = StringUtil.unwrap("<tag/>");
            assertEquals("", result);
        }
    }

    @Nested
    @DisplayName("deQuote")
    class DeQuote {

        @Test
        @DisplayName("removes single quotes")
        void removesSingleQuotes() {
            String result = StringUtil.deQuote("'hello'");
            assertEquals("hello", result);
        }

        @Test
        @DisplayName("removes double quotes")
        void removesDoubleQuotes() {
            String result = StringUtil.deQuote("\"hello\"");
            assertEquals("hello", result);
        }

        @Test
        @DisplayName("handles no quotes")
        void handlesNoQuotes() {
            String result = StringUtil.deQuote("hello");
            assertEquals("hello", result);
        }

        @Test
        @DisplayName("handles partial quotes")
        void handlesPartialQuotes() {
            String result = StringUtil.deQuote("'hello");
            assertEquals("'hello", result);
        }

        @Test
        @DisplayName("handles null input")
        void handlesNullInput() {
            String result = StringUtil.deQuote(null);
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("enQuote")
    class EnQuote {

        @Test
        @DisplayName("wraps in single quotes")
        void wrapsInSingleQuotes() {
            String result = StringUtil.enQuote("hello", '\'');
            assertEquals("'hello'", result);
        }

        @Test
        @DisplayName("wraps in double quotes")
        void wrapsInDoubleQuotes() {
            String result = StringUtil.enQuote("hello", '"');
            assertEquals("\"hello\"", result);
        }

        @Test
        @DisplayName("returns null for null input")
        void returnsNullForNull() {
            String result = StringUtil.enQuote(null, '"');
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("xmlEncode")
    class XmlEncode {

        @Test
        @DisplayName("encodes string for XML")
        void encodesString() {
            String result = StringUtil.xmlEncode("hello world");
            assertEquals("hello+world", result);
        }

        @Test
        @DisplayName("returns null for null input")
        void returnsNullForNull() {
            String result = StringUtil.xmlEncode(null);
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("xmlDecode")
    class XmlDecode {

        @Test
        @DisplayName("decodes XML string")
        void decodesString() {
            String result = StringUtil.xmlDecode("hello+world");
            assertEquals("hello world", result);
        }

        @Test
        @DisplayName("returns null for null input")
        void returnsNullForNull() {
            String result = StringUtil.xmlDecode(null);
            assertNull(result);
        }
    }

    // =========================================================================
    // File I/O Methods
    // =========================================================================

    @Nested
    @DisplayName("stringToFile")
    class StringToFile {

        @Test
        @DisplayName("writes string to file")
        void writesStringToFile(@TempDir File tempDir) throws IOException {
            File testFile = new File(tempDir, "test.txt");
            File result = StringUtil.stringToFile(testFile.getAbsolutePath(), "test content");
            assertEquals(testFile, result);
            assertEquals("test content", Files.readString(testFile.toPath()));
        }

        @Test
        @DisplayName("creates directories if needed")
        void createsDirectories(@TempDir File tempDir) throws IOException {
            File nestedFile = new File(tempDir, "nested/directory/test.txt");
            File result = StringUtil.stringToFile(nestedFile.getAbsolutePath(), "test");
            assertEquals(nestedFile, result);
            assertTrue(nestedFile.exists());
        }

        @Test
        @DisplayName("throws IllegalArgumentException for null path")
        void throwsIllegalArgumentExceptionForNullPath() {
            assertThrows(IllegalArgumentException.class, () -> {
                StringUtil.stringToFile(null, "content");
            });
        }

        @Test
        @DisplayName("throws IllegalArgumentException for empty path")
        void throwsIllegalArgumentExceptionForEmptyPath() {
            assertThrows(IllegalArgumentException.class, () -> {
                StringUtil.stringToFile("", "content");
            });
        }

        @Test
        @DisplayName("throws IllegalArgumentException for null content")
        void throwsIllegalArgumentExceptionForNullContent() {
            assertThrows(IllegalArgumentException.class, () -> {
                StringUtil.stringToFile("path", null);
            });
        }
    }

    @Nested
    @DisplayName("fileToString")
    class FileToString {

        @Test
        @DisplayName("reads file contents")
        void readsFileContents(@TempDir File tempDir) throws IOException {
            File testFile = new File(tempDir, "test.txt");
            Files.writeString(testFile.toPath(), "test content");
            String result = StringUtil.fileToString(testFile);
            assertEquals("test content", result);
        }

        @Test
        @DisplayName("returns null for null file")
        void returnsNullForNull() {
            String result = StringUtil.fileToString((File) null);
            assertNull(result);
        }

        @Test
        @DisplayName("returns null for non-existent file")
        void returnsNullForNonExistent() {
            String result = StringUtil.fileToString(new File("/nonexistent/file.txt"));
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("replaceInFile")
    class ReplaceInFile {

        @Test
        @DisplayName("replaces content in file")
        void replacesContentInFile(@TempDir File tempDir) throws IOException {
            File testFile = new File(tempDir, "test.txt");
            Files.writeString(testFile.toPath(), "hello world");
            boolean result = StringUtil.replaceInFile(testFile, "hello", "hi");
            assertTrue(result);
            assertEquals("hi world", Files.readString(testFile.toPath()));
        }

        @Test
        @DisplayName("returns false for null file")
        void returnsFalseForNullFile() {
            assertFalse(StringUtil.replaceInFile(null, "old", "new"));
        }

        @Test
        @DisplayName("returns false for non-existent file")
        void returnsFalseForNonExistentFile(@TempDir File tempDir) {
            File nonExistent = new File(tempDir, "doesnotexist.txt");
            assertFalse(StringUtil.replaceInFile(nonExistent, "old", "new"));
        }
    }

    // =========================================================================
    // Search Methods
    // =========================================================================

    @Nested
    @DisplayName("find")
    class Find {

        @Test
        @DisplayName("finds substring at start")
        void findsSubstringAtStart() {
            int result = StringUtil.find("hello world", "hello", 0);
            assertEquals(0, result);
        }

        @Test
        @DisplayName("finds substring at end")
        void findsSubstringAtEnd() {
            int result = StringUtil.find("hello world", "world", 0);
            assertEquals(6, result);
        }

        @Test
        @DisplayName("returns -1 when not found")
        void returnsMinusOneWhenNotFound() {
            int result = StringUtil.find("hello world", "xyz", 0);
            assertEquals(-1, result);
        }

        @Test
        @DisplayName("searches from specified position")
        void searchesFromPosition() {
            int result = StringUtil.find("hello hello world", "hello", 6);
            assertEquals(6, result);
        }

        @Test
        @DisplayName("returns -1 for null inputs")
        void returnsMinusOneForNull() {
            assertEquals(-1, StringUtil.find(null, "test", 0));
            assertEquals(-1, StringUtil.find("test", null, 0));
        }

        @Test
        @DisplayName("handles large strings efficiently")
        void handlesLargeStrings() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 3000; i++) {
                sb.append("a");
            }
            sb.append("b");
            int result = StringUtil.find(sb.toString(), "b", 0);
            assertEquals(3000, result);
        }
    }

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @DisplayName("finds all occurrences")
        void findsAllOccurrences() {
            List<Integer> result = StringUtil.findAll("ababab", "ab");
            assertEquals(3, result.size());
            assertEquals(0, result.get(0));
            assertEquals(2, result.get(1));
            assertEquals(4, result.get(2));
        }

        @Test
        @DisplayName("returns empty list when not found")
        void returnsEmptyListWhenNotFound() {
            List<Integer> result = StringUtil.findAll("hello", "xyz");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("returns empty list for null inputs")
        void returnsEmptyListForNull() {
            assertTrue(StringUtil.findAll(null, "test").isEmpty());
            assertTrue(StringUtil.findAll("hello", null).isEmpty());
        }
    }

    // =========================================================================
    // String Construction Methods
    // =========================================================================

    @Nested
    @DisplayName("repeat")
    class Repeat {

        @Test
        @DisplayName("repeats character multiple times")
        void repeatsCharacter() {
            String result = StringUtil.repeat('a', 5);
            assertEquals("aaaaa", result);
        }

        @Test
        @DisplayName("handles zero repeats")
        void handlesZeroRepeats() {
            String result = StringUtil.repeat('a', 0);
            assertEquals("", result);
        }

        @Test
        @DisplayName("handles negative repeats")
        void handlesNegativeRepeats() {
            String result = StringUtil.repeat('a', -1);
            assertEquals("", result);
        }
    }

    @Nested
    @DisplayName("join")
    class Join {

        @Test
        @DisplayName("joins list with separator")
        void joinsList() {
            List<String> list = Arrays.asList("a", "b", "c");
            String result = StringUtil.join(list, ',');
            assertEquals("a,b,c", result);
        }

        @Test
        @DisplayName("handles empty list")
        void handlesEmptyList() {
            List<String> list = Collections.emptyList();
            String result = StringUtil.join(list, ',');
            assertEquals("", result);
        }

        @Test
        @DisplayName("handles single element")
        void handlesSingleElement() {
            List<String> list = Collections.singletonList("only");
            String result = StringUtil.join(list, ',');
            assertEquals("only", result);
        }

        @Test
        @DisplayName("returns empty string for null list")
        void returnsEmptyStringForNull() {
            String result = StringUtil.join(null, ',');
            assertEquals("", result);
        }
    }

    @Nested
    @DisplayName("splitToList")
    class SplitToList {

        @Test
        @DisplayName("splits string by separator")
        void splitsString() {
            List<String> result = StringUtil.splitToList("a,b,c", ",");
            assertEquals(Arrays.asList("a", "b", "c"), result);
        }

        @Test
        @DisplayName("handles empty string")
        void handlesEmptyString() {
            List<String> result = StringUtil.splitToList("", ",");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("handles null string")
        void handlesNullString() {
            List<String> result = StringUtil.splitToList(null, ",");
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("insert")
    class Insert {

        @Test
        @DisplayName("inserts string at position")
        void insertsString() {
            String result = StringUtil.insert("hello", " world", 5);
            assertEquals("hello world", result);
        }

        @Test
        @DisplayName("handles invalid position")
        void handlesInvalidPosition() {
            String result = StringUtil.insert("hello", " world", -1);
            assertEquals("hello", result);
        }

        @Test
        @DisplayName("handles position beyond string length")
        void handlesPositionBeyondLength() {
            String result = StringUtil.insert("hello", " world", 10);
            assertEquals("hello", result);
        }

        @Test
        @DisplayName("handles null base string")
        void handlesNullBase() {
            String result = StringUtil.insert(null, "world", 0);
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("pad")
    class Pad {

        @Test
        @DisplayName("pads left by default")
        void padsLeft() {
            String result = StringUtil.pad("hello", 10, ' ');
            assertEquals("     hello", result);
        }

        @Test
        @DisplayName("pads right when specified")
        void padsRight() {
            String result = StringUtil.pad("hello", 10, ' ', false);
            assertEquals("hello     ", result);
        }

        @Test
        @DisplayName("does not pad if string is long enough")
        void doesNotPadIfLongEnough() {
            String result = StringUtil.pad("hello", 3, ' ');
            assertEquals("hello", result);
        }
    }

    @Nested
    @DisplayName("firstWord")
    class FirstWord {

        @Test
        @DisplayName("extracts first word")
        void extractsFirstWord() {
            String result = StringUtil.firstWord("hello world");
            assertEquals("hello", result);
        }

        @Test
        @DisplayName("handles single word")
        void handlesSingleWord() {
            String result = StringUtil.firstWord("hello");
            assertEquals("hello", result);
        }

        @Test
        @DisplayName("handles null input")
        void handlesNullInput() {
            String result = StringUtil.firstWord(null);
            assertNull(result);
        }
    }

    // =========================================================================
    // Collection Processing Methods
    // =========================================================================

    @Nested
    @DisplayName("setToXML")
    class SetToXML {

        @Test
        @DisplayName("converts set to XML")
        void convertsSetToXml() {
            Set<String> set = Set.of("a", "b", "c");
            String result = StringUtil.setToXML(set);
            assertTrue(result.contains("<set>"));
            assertTrue(result.contains("</set>"));
            assertTrue(result.contains("<item>a</item>"));
            assertTrue(result.contains("<item>b</item>"));
            assertTrue(result.contains("<item>c</item>"));
        }

        @Test
        @DisplayName("returns null for null set")
        void returnsNullForNullSet() {
            String result = StringUtil.setToXML(null);
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("xmlToSet")
    class XmlToSet {

        @Test
        @DisplayName("converts XML to set")
        void convertsXmlToSet() {
            String xml = "<set><item>a</item><item>b</item><item>c</item></set>";
            Set<String> result = StringUtil.xmlToSet(xml);
            assertEquals(3, result.size());
            assertTrue(result.contains("a"));
            assertTrue(result.contains("b"));
            assertTrue(result.contains("c"));
        }

        @Test
        @DisplayName("handles malformed XML")
        void handlesMalformedXml() {
            String xml = "not xml";
            Set<String> result = StringUtil.xmlToSet(xml);
            assertTrue(result.isEmpty());
        }
    }

    // =========================================================================
    // HTML Processing Methods
    // =========================================================================

    @Nested
    @DisplayName("formatForHTML")
    class FormatForHTML {

        @Test
        @DisplayName("escapes HTML entities")
        void escapesHtmlEntities() {
            String result = StringUtil.formatForHTML("<script>alert('xss')</script>");
            assertTrue(result.contains("&lt;"));
            assertTrue(result.contains("&gt;"));
        }

        @Test
        @DisplayName("replaces newlines with br tags")
        void replacesNewlines() {
            String result = StringUtil.formatForHTML("line1\nline2");
            assertTrue(result.contains("<br>"));
        }

        @Test
        @DisplayName("replaces tabs with nbsp")
        void replacesTabs() {
            String result = StringUtil.formatForHTML("a\tb");
            assertTrue(result.contains("&nbsp;"));
        }

        @Test
        @DisplayName("returns null for null input")
        void returnsNullForNull() {
            String result = StringUtil.formatForHTML(null);
            assertNull(result);
        }
    }

    // =========================================================================
    // Exception Handling Methods
    // =========================================================================

    @Nested
    @DisplayName("convertThrowableToString")
    class ConvertThrowableToString {

        @Test
        @DisplayName("converts exception to string")
        void convertsException() {
            Exception ex = new RuntimeException("Test error");
            String result = StringUtil.convertThrowableToString(ex);
            assertTrue(result.contains("RuntimeException"));
            assertTrue(result.contains("Test error"));
        }

        @Test
        @DisplayName("returns null for null input")
        void returnsNullForNull() {
            String result = StringUtil.convertThrowableToString(null);
            assertNull(result);
        }

        @Test
        @DisplayName("includes stack trace")
        void includesStackTrace() {
            Exception ex = new RuntimeException("Test");
            String result = StringUtil.convertThrowableToString(ex);
            assertTrue(result.contains("at "));
            assertTrue(result.contains("Test"));
        }
    }

    // =========================================================================
    // Validation Methods
    // =========================================================================

    @Nested
    @DisplayName("isIntegerString")
    class IsIntegerString {

        @Test
        @DisplayName("recognizes integer strings")
        void recognizesIntegerStrings() {
            assertTrue(StringUtil.isIntegerString("123"));
            assertTrue(StringUtil.isIntegerString("0"));
            assertTrue(StringUtil.isIntegerString("-456"));
        }

        @Test
        @DisplayName("rejects non-integer strings")
        void rejectsNonIntegerStrings() {
            assertFalse(StringUtil.isIntegerString("12.3"));
            assertFalse(StringUtil.isIntegerString("abc"));
            assertFalse(StringUtil.isIntegerString("12a"));
        }

        @Test
        @DisplayName("handles null input")
        void handlesNullInput() {
            assertFalse(StringUtil.isIntegerString(null));
        }
    }

    @Nested
    @DisplayName("isNullOrEmpty")
    class IsNullOrEmpty {

        @Test
        @DisplayName("returns true for null")
        void returnsTrueForNull() {
            assertTrue(StringUtil.isNullOrEmpty(null));
        }

        @Test
        @DisplayName("returns true for empty string")
        void returnsTrueForEmpty() {
            assertTrue(StringUtil.isNullOrEmpty(""));
        }

        @Test
        @DisplayName("returns false for non-empty")
        void returnsFalseForNonEmpty() {
            assertFalse(StringUtil.isNullOrEmpty("hello"));
        }
    }

    // =========================================================================
    // Type Conversion Methods
    // =========================================================================

    @Nested
    @DisplayName("strToInt")
    class StrToInt {

        @Test
        @DisplayName("converts valid integer string")
        void convertsValidIntegerString() {
            assertEquals(123, StringUtil.strToInt("123", 0));
        }

        @Test
        @DisplayName("returns default for invalid string")
        void returnsDefaultForInvalid() {
            assertEquals(0, StringUtil.strToInt("abc", 0));
        }

        @Test
        @DisplayName("returns default for null string")
        void returnsDefaultForNull() {
            assertEquals(0, StringUtil.strToInt(null, 0));
        }

        @Test
        @DisplayName("returns default for empty string")
        void returnsDefaultForEmpty() {
            assertEquals(0, StringUtil.strToInt("", 0));
        }
    }

    @Nested
    @DisplayName("strToLong")
    class StrToLong {

        @Test
        @DisplayName("converts valid long string")
        void convertsValidLongString() {
            assertEquals(123456L, StringUtil.strToLong("123456", 0L));
        }

        @Test
        @DisplayName("returns default for invalid string")
        void returnsDefaultForInvalid() {
            assertEquals(0L, StringUtil.strToLong("abc", 0L));
        }
    }

    @Nested
    @DisplayName("strToDouble")
    class StrToDouble {

        @Test
        @DisplayName("converts valid double string")
        void convertsValidDoubleString() {
            assertEquals(12.34, StringUtil.strToDouble("12.34", 0.0));
        }

        @Test
        @DisplayName("returns default for invalid string")
        void returnsDefaultForInvalid() {
            assertEquals(0.0, StringUtil.strToDouble("abc", 0.0));
        }
    }

    @Nested
    @DisplayName("strToBoolean")
    class StrToBoolean {

        @Test
        @DisplayName("converts true string")
        void convertsTrueString() {
            assertTrue(StringUtil.strToBoolean("true"));
            assertTrue(StringUtil.strToBoolean("TRUE"));
            assertTrue(StringUtil.strToBoolean("True"));
        }

        @Test
        @DisplayName("converts false string")
        void convertsFalseString() {
            assertFalse(StringUtil.strToBoolean("false"));
            assertFalse(StringUtil.strToBoolean("FALSE"));
            assertFalse(StringUtil.strToBoolean("False"));
        }

        @Test
        @DisplayName("returns false for null or empty")
        void returnsFalseForNullOrEmpty() {
            assertFalse(StringUtil.strToBoolean(null));
            assertFalse(StringUtil.strToBoolean(""));
        }

        @Test
        @DisplayName("returns false for non-true strings")
        void returnsFalseForNonTrueStrings() {
            assertFalse(StringUtil.strToBoolean("hello"));
            assertFalse(StringUtil.strToBoolean("1"));
            assertFalse(StringUtil.strToBoolean("yes"));
        }
    }
}