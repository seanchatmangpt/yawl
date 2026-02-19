/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
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

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Currency;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



public class StringUtil {
    private static final Logger _log = LogManager.getLogger(StringUtil.class);
    private static final String TIMESTAMP_DELIMITER = " ";
    private static final String DATE_DELIMITER = "-";
    private static final String TIME_DELIMITER = ":";
    private static final String TIME_FORMAT = "HH" + TIME_DELIMITER + "mm" + TIME_DELIMITER + "ss";
    private static final String DATE_FORMAT = "yyyy" + DATE_DELIMITER + "MM" + DATE_DELIMITER + "dd";
    private static final String TIMESTAMP_FORMAT = DATE_FORMAT + TIMESTAMP_DELIMITER + TIME_FORMAT;


    /**
     * Utility routine to replace one token with another within a string object.
     *
     * @param buffer    String object to be manipulated
     * @param fromToken Token to be replaced
     * @param toToken   Token used in replacement
     * @return String object holding modified String
     */
    public static String replaceTokens(String buffer, String fromToken, String toToken) {
        /*
         * Note: We don't use the StringTokenizer class as it doesn't cope with '\n' substrings
         * correctly.
         */
        int old = 0;
        StringBuilder temp = new StringBuilder();
        while (true) {
            int pos = buffer.indexOf(fromToken, old);

            if (pos != -1) {
                String subText = buffer.substring(old, pos);
                temp.append(subText + toToken);
                old = pos + fromToken.length();
            } else {
                String subText = buffer.substring(old);
                temp.append(subText);
                break;
            }
        }
        return temp.toString();
    }

    /**
     * Utility routine to return the date supplied as an ISO formatted string.
     *
     * @param date Date object to be formatted
     * @return String object holding ISO formatted representation of date supplied
     */
    public static String getISOFormattedDate(Date date) {
        SimpleDateFormat fmt = new SimpleDateFormat(TIMESTAMP_FORMAT);
        return fmt.format(date);
    }

    /**
     * Utility routine to return a debug message suitable for logging. It basically prefixes the supplied message
     * with the current timestamp in ISO format.
     *
     * @param msg Body of debug message to be prefixed with the current timestamp
     * @return String object holding debug message prefixed with ISO formatted current timestamp
     */
    public static String getDebugMessage(String msg) {
        return getISOFormattedDate(new Date()) + " " + msg;
    }

    /**
     * Utility method to take a string and return the string in reverse sequence.
     * <p>
     * For backward compatibility, this method throws NullPointerException on null input.
     * </p>
     *
     * @param inputString String to be reversed
     * @return Reversed string
     * @throws NullPointerException if inputString is null
     * @deprecated Use {@link #reverseStringOptional(String)} for proper null handling
     */
    @Deprecated(since = "6.0.0", forRemoval = false)
    public static String reverseString(String inputString) {
        return reverseStringOptional(inputString).orElseThrow(() ->
                new NullPointerException("Input string must not be null"));
    }

    /**
     * Utility method to take a string and return the string in reverse sequence
     * with proper Optional handling.
     * <p>
     * This method follows TPS principles by making null inputs visible through
     * Optional.empty() rather than throwing NullPointerException.
     * </p>
     *
     * @param inputString String to be reversed (may be null)
     * @return Optional containing the reversed string, or Optional.empty() if input is null
     */
    public static Optional<String> reverseStringOptional(String inputString) {
        if (inputString == null) {
            _log.debug("Null string passed to reverseStringOptional");
            return Optional.empty();
        }
        char[] inputChars = new char[inputString.length()];
        char[] outputChars = new char[inputString.length()];

        inputString.getChars(0, inputString.length(), inputChars, 0);
        int pointer = inputChars.length - 1;

        for (int i = 0; i <= inputChars.length - 1; i++) {
            outputChars[pointer] = inputChars[i];
            pointer--;
        }

        return Optional.of(new String(outputChars));
    }

    /**
     * Removes all white space from a string.
     * <p>
     * For backward compatibility, this method throws NullPointerException on null input.
     * </p>
     *
     * @param string String to remove white space from
     * @return Resulting whitespaceless string.
     * @throws NullPointerException if string is null
     * @deprecated Use {@link #removeAllWhiteSpaceOptional(String)} for proper null handling
     */
    @Deprecated(since = "6.0.0", forRemoval = false)
    public static String removeAllWhiteSpace(String string) {
        return removeAllWhiteSpaceOptional(string).orElseThrow(() ->
                new NullPointerException("String must not be null"));
    }

    /**
     * Removes all white space from a string with proper Optional handling.
     * <p>
     * This method follows TPS principles by making null inputs visible through
     * Optional.empty() rather than throwing NullPointerException.
     * </p>
     *
     * @param string String to remove white space from (may be null)
     * @return Optional containing the whitespaceless string, or Optional.empty() if input is null
     */
    public static Optional<String> removeAllWhiteSpaceOptional(String string) {
        if (string == null) {
            _log.debug("Null string passed to removeAllWhiteSpaceOptional");
            return Optional.empty();
        }
        Pattern p = Pattern.compile("[\\s]");
        Matcher m;
        String result = string;
        do {
            m = p.matcher(result);
            if (m.find()) {
                result = m.replaceAll("");
            }
        } while (m.find());

        return Optional.of(result);
    }

    /**
     * Formats a postcode into standard Royal Mail format.
     * <p>
     * This method returns the input unchanged if it is null or empty.
     * For backward compatibility, null input returns null.
     * </p>
     *
     * @param postcode the postcode to format (may be null)
     * @return Postcode correctly formatted, or null if input was null
     * @deprecated Use {@link #formatPostCodeOptional(String)} for proper null handling
     */
    @Deprecated(since = "6.0.0", forRemoval = false)
    public static String formatPostCode(String postcode) {
        return formatPostCodeOptional(postcode).orElse(null);
    }

    /**
     * Formats a postcode into standard Royal Mail format with proper Optional handling.
     * <p>
     * This method follows TPS principles by making null inputs visible through
     * Optional.empty() rather than silent null returns.
     * </p>
     *
     * @param postcode the postcode to format (may be null)
     * @return Optional containing the formatted postcode, or Optional.empty() if input is null or empty
     */
    public static Optional<String> formatPostCodeOptional(String postcode) {
        if (postcode == null) {
            _log.debug("Null postcode passed to formatPostCodeOptional");
            return Optional.empty();
        }
        if (postcode.isEmpty()) {
            _log.debug("Empty postcode passed to formatPostCodeOptional");
            return Optional.empty();
        }
        String formatted = removeAllWhiteSpaceOptional(postcode)
                .orElseThrow(() -> new IllegalStateException("Unexpected null from removeAllWhiteSpaceOptional"))
                .toUpperCase();
        if (formatted.length() < 3) {
            return Optional.of(formatted);
        }
        return Optional.of(formatted.substring(0, formatted.length() - 3) + " " +
                formatted.substring(formatted.length() - 3));
    }

    /**
     * Formats a sortcode into the common form nn-nn-nn.
     * <p>
     * For backward compatibility, this method throws NullPointerException on null input.
     * </p>
     *
     * @param sortcode the sortcode to format (must not be null, must be at least 6 characters)
     * @return Sortcode correctly formatted
     * @throws NullPointerException if sortcode is null
     * @throws StringIndexOutOfBoundsException if sortcode is less than 6 characters
     * @deprecated Use {@link #formatSortCodeOptional(String)} for proper error handling
     */
    @Deprecated(since = "6.0.0", forRemoval = false)
    public static String formatSortCode(String sortcode) {
        return formatSortCodeOptional(sortcode).orElseThrow(() ->
                new NullPointerException("Sortcode must not be null"));
    }

    /**
     * Formats a sortcode into the common form nn-nn-nn with proper Optional handling.
     * <p>
     * This method follows TPS principles by making invalid inputs visible through
     * Optional.empty() rather than throwing exceptions or returning null.
     * </p>
     *
     * @param sortcode the sortcode to format (may be null)
     * @return Optional containing the formatted sortcode, or Optional.empty() if input is null or too short
     */
    public static Optional<String> formatSortCodeOptional(String sortcode) {
        if (sortcode == null) {
            _log.debug("Null sortcode passed to formatSortCodeOptional");
            return Optional.empty();
        }
        if (sortcode.length() < 6) {
            _log.warn("Sortcode '{}' is too short (minimum 6 characters required)", sortcode);
            return Optional.empty();
        }
        return Optional.of(sortcode.substring(0, 2) + "-" +
                sortcode.substring(2, 4) + "-" + sortcode.substring(4, 6));
    }

    /**
     * Converts a string to all lower case, and capitalises the first letter of the string.
     * <p>
     * For backward compatibility, this method returns null for null input and empty string for empty input.
     * </p>
     *
     * @param s unformatted string.
     * @return The formatted string, or null if input is null.
     * @deprecated Use {@link #capitaliseOptional(String)} for proper null handling
     */
    @Deprecated(since = "6.0.0", forRemoval = false)
    public static String capitalise(String s) {
        return capitaliseOptional(s).orElse(null);
    }

    /**
     * Converts a string to all lower case, and capitalises the first letter of the string
     * with proper Optional handling.
     * <p>
     * This method follows TPS principles by making null inputs visible through
     * Optional.empty() rather than silent null returns.
     * </p>
     *
     * @param s unformatted string (may be null)
     * @return Optional containing the formatted string, Optional.empty() for null input,
     *         or Optional.of("") for empty input
     */
    public static Optional<String> capitaliseOptional(String s) {
        if (s == null) {
            _log.debug("Null string passed to capitaliseOptional");
            return Optional.empty();
        }
        if (s.isEmpty()) {
            return Optional.of("");
        }
        char[] chars = s.toLowerCase().toCharArray();
        chars[0] = Character.toUpperCase(chars[0]);
        return Optional.of(String.valueOf(chars));
    }

    /**
     * Utility routine that takes in a Calendar reference and returns a date/time stamp suitable for use
     * in a Portlets environment.
     * <p>
     * For backward compatibility, this method throws NullPointerException on null input.
     * </p>
     *
     * @param calendar the calendar to format
     * @return Date/timestamp suitable for display.
     * @throws NullPointerException if calendar is null
     * @deprecated Use {@link #formatUIDateOptional(Calendar)} for proper null handling,
     *             or use TimeUtil.formatUIDate for newer code
     */
    @Deprecated(since = "6.0.0", forRemoval = false)
    public static String formatUIDate(Calendar calendar) {
        return formatUIDateOptional(calendar).orElseThrow(() ->
                new NullPointerException("Calendar must not be null"));
    }

    /**
     * Utility routine that takes in a Calendar reference and returns a date/time stamp suitable for use
     * in a Portlets environment with proper Optional handling.
     * <p>
     * This method follows TPS principles by making null inputs visible through
     * Optional.empty() rather than throwing NullPointerException.
     * </p>
     *
     * @param calendar the calendar to format (may be null)
     * @return Optional containing the formatted date/timestamp, or Optional.empty() if calendar is null
     */
    public static Optional<String> formatUIDateOptional(Calendar calendar) {
        if (calendar == null) {
            _log.debug("Null calendar passed to formatUIDateOptional");
            return Optional.empty();
        }
        SimpleDateFormat fmt;
        // Set format depending upon whether we have a timestamp component to the calendar.
        // Note: this is slightly flawed as an assumption as we could be bang on midnight.
        if ((calendar.get(Calendar.HOUR) == 0) && (calendar.get(Calendar.MINUTE) == 0)
                && (calendar.get(Calendar.SECOND) == 0)) {
            fmt = new SimpleDateFormat("dd-MMM-yy");
        } else {
            fmt = new SimpleDateFormat("dd-MMM-yy hh:mm a");
        }
        return Optional.of(fmt.format(calendar.getTime()));
    }

    /**
     * Utility routine which takes a decimal value as a string (e.g. 0.25 equating to 25p) and returns the
     * value in UI currency format (e.g. L0.25).
     * <p>
     * For backward compatibility, this method throws NullPointerException on null input.
     * </p>
     *
     * @param value the decimal value to format
     * @return A formatted currency string
     * @throws NullPointerException if value is null
     * @deprecated Use {@link #formatDecimalCostOptional(BigDecimal)} for proper null handling
     */
    @Deprecated(since = "6.0.0", forRemoval = false)
    public static String formatDecimalCost(BigDecimal value) {
        return formatDecimalCostOptional(value).orElseThrow(() ->
                new NullPointerException("Value must not be null"));
    }

    /**
     * Utility routine which takes a decimal value and returns the value in UI currency format
     * with proper Optional handling.
     * <p>
     * This method follows TPS principles by making null inputs visible through
     * Optional.empty() rather than throwing NullPointerException.
     * </p>
     *
     * @param value the decimal value to format (may be null)
     * @return Optional containing the formatted currency string, or Optional.empty() if value is null
     */
    public static Optional<String> formatDecimalCostOptional(BigDecimal value) {
        if (value == null) {
            _log.debug("Null BigDecimal passed to formatDecimalCostOptional");
            return Optional.empty();
        }
        Currency currency = Currency.getInstance(Locale.getDefault());
        NumberFormat fmt = DecimalFormat.getInstance();
        fmt.setMinimumFractionDigits(2);
        fmt.setMaximumFractionDigits(2);
        return Optional.of(currency.getSymbol() + fmt.format(value));
    }


    /**
     * formats a long time value into a string of the form 'ddd:hh:mm:ss'
     *
     * @param time the time value (in milliseconds)
     * @return the formatted time string
     */
    public static String formatTime(long time) {
        long secsPerHour = 60 * 60;
        long secsPerDay = 24 * secsPerHour;

        long millis = time % 1000;
        time /= 1000;
        long days = time / secsPerDay;
        time %= secsPerDay;
        long hours = time / secsPerHour;
        time %= secsPerHour;
        long mins = time / 60;
        time %= 60;

        return String.format("%d:%02d:%02d:%02d.%04d", days, hours, mins, time, millis);
    }

    /**
     * Converts the throwable object into the standard Java stack trace format.
     * <p>
     * For backward compatibility, this method returns null for null input.
     * </p>
     *
     * @param t Throwable to convert to a String
     * @return String representation of Throwable t, or null if t is null
     * @deprecated Use {@link #convertThrowableToStringOptional(Throwable)} for proper null handling
     */
    @Deprecated(since = "6.0.0", forRemoval = false)
    public static String convertThrowableToString(Throwable t) {
        return convertThrowableToStringOptional(t).orElse(null);
    }

    /**
     * Converts the throwable object into the standard Java stack trace format
     * with proper Optional handling.
     * <p>
     * This method follows TPS principles by making null inputs visible through
     * Optional.empty() rather than silent null returns.
     * </p>
     *
     * @param t Throwable to convert to a String (may be null)
     * @return Optional containing the string representation of the throwable,
     *         or Optional.empty() if t is null
     */
    public static Optional<String> convertThrowableToStringOptional(Throwable t) {
        if (t == null) {
            _log.debug("Null throwable passed to convertThrowableToStringOptional");
            return Optional.empty();
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(baos);
        t.printStackTrace(writer);
        writer.flush();
        return Optional.of(baos.toString());
    }

    /**
     * Escapes all HTML entities and "funky accents" into the HTML 4.0 encodings, replacing
     * new lines with "&lt;br&gt;", tabs with four "&amp;nbsp;" and single spaces with "&amp;nbsp;".
     * <p>
     * For backward compatibility, this method returns null for null input.
     * </p>
     *
     * @param string to escape
     * @return escaped string, or null if input is null
     * @deprecated Use {@link #formatForHTMLOptional(String)} for proper null handling
     */
    @Deprecated(since = "6.0.0", forRemoval = false)
    public static String formatForHTML(String string) {
        return formatForHTMLOptional(string).orElse(null);
    }

    /**
     * Escapes all HTML entities and "funky accents" into the HTML 4.0 encodings, replacing
     * new lines with "&lt;br&gt;", tabs with four "&amp;nbsp;" and single spaces with "&amp;nbsp;"
     * with proper Optional handling.
     * <p>
     * This method follows TPS principles by making null inputs visible through
     * Optional.empty() rather than silent null returns.
     * </p>
     *
     * @param string the string to escape (may be null)
     * @return Optional containing the escaped string, or Optional.empty() if input is null
     */
    public static Optional<String> formatForHTMLOptional(String string) {
        if (string == null) {
            _log.debug("Null string passed to formatForHTMLOptional");
            return Optional.empty();
        }
        String result = StringEscapeUtils.escapeHtml4(string);
        result = result.replaceAll("\n", "<br>");
        result = result.replaceAll("\t", "&nbsp;&nbsp;&nbsp;&nbsp;");
        result = result.replaceAll(" ", "&nbsp;");
        return Optional.of(result);
    }

    /**
     * encases a string with a pair of xml tags
     *
     * @param core    the text to encase
     * @param wrapTag the name of the tag to encase the text
     * @return the encased string (e.g. "<wrapTag>core</wrapTag>")
     */
    public static String wrap(String core, String wrapTag) {
        StringBuilder sb = new StringBuilder(50);
        sb.append('<').append(wrapTag);
        if (core != null) {
            sb.append('>').append(core).append("</").append(wrapTag).append('>');
        } else {
            sb.append("/>");
        }
        return sb.toString();
    }

    public static String wrapEscaped(String core, String wrapTag) {
        return wrap(JDOMUtil.encodeEscapes(core), wrapTag);
    }


    /**
     * Removes an outer set of xml tags from an xml string, if possible.
     * <p>
     * For backward compatibility, null input returns null.
     * </p>
     *
     * @param xml the xml string to strip
     * @return the stripped xml string, or empty string for self-closing tags
     * @deprecated Use {@link #unwrapOptional(String)} for proper null handling
     */
    @Deprecated(since = "6.0.0", forRemoval = false)
    public static String unwrap(String xml) {
        return unwrapOptional(xml).orElse(null);
    }

    /**
     * Removes an outer set of xml tags from an xml string with proper Optional handling.
     * <p>
     * This method follows TPS principles by making invalid inputs visible through
     * Optional.empty() rather than silent null returns.
     * </p>
     *
     * @param xml the xml string to strip (may be null)
     * @return Optional containing the stripped xml string (empty string for self-closing tags),
     *         or Optional.empty() if input is null or malformed
     */
    public static Optional<String> unwrapOptional(String xml) {
        if (xml == null) {
            _log.debug("Null XML passed to unwrapOptional");
            return Optional.empty();
        }
        if (xml.isEmpty()) {
            _log.debug("Empty XML passed to unwrapOptional");
            return Optional.empty();
        }
        // Self-closing tags have empty content by definition
        if (xml.matches("^<\\w+/>$")) {
            return Optional.of("");
        }
        int start = xml.indexOf('>') + 1;
        int end = xml.lastIndexOf('<');
        if (start < 1) {
            _log.warn("Malformed XML passed to unwrapOptional: no opening tag found in '{}'", xml);
            return Optional.empty();
        }
        if (end < start) {
            _log.warn("Malformed XML passed to unwrapOptional: no closing tag found in '{}'", xml);
            return Optional.empty();
        }
        return Optional.of(xml.substring(start, end));
    }


    /**
     * Removes single or double quotes surrounding a string
     * @param s the string
     * @return the string with the quotes removed
     */
    public static String deQuote(String s) {
        if (! isNullOrEmpty(s)) {
            char first = s.charAt(0);
            if (first == '\'' || first == '"') {
                int last = s.lastIndexOf(first);
                if (last > 0) {
                    return s.substring(1, last);
                }
            }
        }
        return s;
    }


    /**
     * Wraps a string in the specified quote marks.
     * <p>
     * For backward compatibility, null input returns null.
     * </p>
     *
     * @param s the string to wrap
     * @param quoteMark the quote character to use
     * @return the wrapped string, or null if input is null
     * @deprecated Use {@link #enQuoteOptional(String, char)} for proper null handling
     */
    @Deprecated(since = "6.0.0", forRemoval = false)
    public static String enQuote(String s, char quoteMark) {
        return enQuoteOptional(s, quoteMark).orElse(null);
    }

    /**
     * Wraps a string in the specified quote marks with proper Optional handling.
     * <p>
     * This method follows TPS principles by making null inputs visible through
     * Optional.empty() rather than silent null returns.
     * </p>
     *
     * @param s the string to wrap (may be null)
     * @param quoteMark the quote character to use
     * @return Optional containing the wrapped string, or Optional.empty() if input is null
     */
    public static Optional<String> enQuoteOptional(String s, char quoteMark) {
        if (s == null) {
            _log.debug("Null string passed to enQuoteOptional");
            return Optional.empty();
        }
        return Optional.of(new StringBuilder(s.length() + 2)
                .append(quoteMark).append(s).append(quoteMark).toString());
    }


    /**
     * Encodes reserved characters in an xml string.
     * <p>
     * For backward compatibility, null input returns null.
     * </p>
     *
     * @param s the string to encode
     * @return the newly encoded string, or null if input is null
     * @deprecated Use {@link #xmlEncodeOptional(String)} for proper null handling
     */
    @Deprecated(since = "6.0.0", forRemoval = false)
    public static String xmlEncode(String s) {
        return xmlEncodeOptional(s).orElse(null);
    }

    /**
     * Encodes reserved characters in an xml string with proper Optional handling.
     * <p>
     * This method follows TPS principles by making null inputs visible through
     * Optional.empty() rather than silent null returns.
     * </p>
     *
     * @param s the string to encode (may be null)
     * @return Optional containing the encoded string, or Optional.empty() if input is null
     */
    public static Optional<String> xmlEncodeOptional(String s) {
        if (s == null) {
            _log.debug("Null string passed to xmlEncodeOptional");
            return Optional.empty();
        }
        return Optional.of(URLEncoder.encode(s, StandardCharsets.UTF_8));
    }

    /**
     * Decodes reserved characters in an xml string.
     * <p>
     * For backward compatibility, null input returns null.
     * </p>
     *
     * @param s the string to decode
     * @return the newly decoded string, or null if input is null
     * @deprecated Use {@link #xmlDecodeOptional(String)} for proper null handling
     */
    @Deprecated(since = "6.0.0", forRemoval = false)
    public static String xmlDecode(String s) {
        return xmlDecodeOptional(s).orElse(null);
    }

    /**
     * Decodes reserved characters in an xml string with proper Optional handling.
     * <p>
     * This method follows TPS principles by making null inputs visible through
     * Optional.empty() rather than silent null returns.
     * </p>
     *
     * @param s the string to decode (may be null)
     * @return Optional containing the decoded string, or Optional.empty() if input is null
     */
    public static Optional<String> xmlDecodeOptional(String s) {
        if (s == null) {
            _log.debug("Null string passed to xmlDecodeOptional");
            return Optional.empty();
        }
        return Optional.of(URLDecoder.decode(s, StandardCharsets.UTF_8));
    }

    public static boolean isIntegerString(String s) {
        if (s == null) return false;
        char[] digits = s.toCharArray();
        for (char digit : digits) {
            if ((digit < '0') || (digit > '9')) return false;
        }
        return true;
    }


    /**
     * Writes string contents to a file specified by path.
     * <p>
     * This method throws IllegalArgumentException for null arguments, providing
     * explicit error visibility rather than silent failures.
     * </p>
     *
     * @param path the path to the file to write to (must not be null or empty)
     * @param contents the contents to write (must not be null)
     * @return the file if successful
     * @throws IllegalArgumentException if path is null/empty or contents is null
     * @throws RuntimeException if file writing fails (wrapped IOException)
     */
    public static File stringToFile(String path, String contents) {
        if (isNullOrEmpty(path)) {
            throw new IllegalArgumentException("Path must not be null or empty");
        }
        if (contents == null) {
            throw new IllegalArgumentException("Contents must not be null");
        }
        int sepPos = path.lastIndexOf(File.separator);
        File toFile;
        if (sepPos > -1) {
            File dir = new File(path.substring(0, sepPos));
            if (!dir.exists()) {
                dir.mkdirs();
            }
            toFile = new File(dir, path.substring(sepPos + 1));
        }
        else {
            toFile = new File(path);
        }

        return stringToFileOptional(toFile, contents)
                .orElseThrow(() -> new RuntimeException(
                        "Failed to write to file: " + path));
    }


    /**
     * Creates a temporary file with the given contents.
     * <p>
     * For backward compatibility, this method returns null on failure.
     * </p>
     *
     * @param contents the contents to write to the temporary file
     * @return the created temporary file, or null if creation fails
     * @deprecated Use {@link #stringToTempFileOptional(String)} for proper error handling
     */
    @Deprecated(since = "6.0.0", forRemoval = false)
    public static File stringToTempFile(String contents) {
        return stringToTempFileOptional(contents).orElse(null);
    }

    /**
     * Creates a temporary file with the given contents with proper Optional handling.
     * <p>
     * This method follows TPS principles by making errors visible through
     * Optional.empty() rather than silent null returns.
     * </p>
     *
     * @param contents the contents to write to the temporary file (must not be null)
     * @return Optional containing the created temporary file, or Optional.empty() if creation fails
     */
    public static Optional<File> stringToTempFileOptional(String contents) {
        if (contents == null) {
            _log.error("Null contents passed to stringToTempFileOptional");
            return Optional.empty();
        }
        try {
            File tempFile = File.createTempFile(
                    RandomStringUtils.randomAlphanumeric(12), null);
            return stringToFileOptional(tempFile, contents);
        } catch (IOException e) {
            _log.error("Failed to create temporary file", e);
            return Optional.empty();
        }
    }


    /**
     * Writes string contents to a file.
     * <p>
     * For backward compatibility, this method returns null on failure.
     * </p>
     *
     * @param f the file to write to
     * @param contents the contents to write
     * @return the file if successful, or null if writing fails
     * @deprecated Use {@link #stringToFileOptional(File, String)} for proper error handling
     */
    @Deprecated(since = "6.0.0", forRemoval = false)
    public static File stringToFile(File f, String contents) {
        return stringToFileOptional(f, contents).orElse(null);
    }

    /**
     * Writes string contents to a file with proper Optional handling.
     * <p>
     * This method follows TPS principles by making errors visible through
     * Optional.empty() rather than silent null returns.
     * </p>
     *
     * @param f the file to write to (must not be null)
     * @param contents the contents to write (must not be null)
     * @return Optional containing the file if successful, or Optional.empty() if writing fails
     */
    public static Optional<File> stringToFileOptional(File f, String contents) {
        if (f == null) {
            _log.error("Null file passed to stringToFileOptional");
            return Optional.empty();
        }
        if (contents == null) {
            _log.error("Null contents passed to stringToFileOptional for file: {}", f.getAbsolutePath());
            return Optional.empty();
        }
        try (BufferedWriter buf = new BufferedWriter(new FileWriter(f))) {
            buf.write(contents, 0, contents.length());
            return Optional.of(f);
        } catch (IOException ioe) {
            _log.error("Failed to write to file: {}", f.getAbsolutePath(), ioe);
            return Optional.empty();
        }
    }


    /**
     * Reads a file's contents into a string.
     * <p>
     * For backward compatibility, this method returns null on failure or if file does not exist.
     * </p>
     *
     * @param f the file to read
     * @return the file contents as a string, or null if reading fails or file does not exist
     * @deprecated Use {@link #fileToStringOptional(File)} for proper error handling
     */
    @Deprecated(since = "6.0.0", forRemoval = false)
    public static String fileToString(File f) {
        return fileToStringOptional(f).orElse(null);
    }

    /**
     * Reads a file's contents into a string with proper Optional handling.
     * <p>
     * This method follows TPS principles by making errors visible through
     * Optional.empty() rather than silent null returns.
     * </p>
     *
     * @param f the file to read (must not be null)
     * @return Optional containing the file contents, or Optional.empty() if reading fails
     */
    public static Optional<String> fileToStringOptional(File f) {
        if (f == null) {
            _log.error("Null file passed to fileToStringOptional");
            return Optional.empty();
        }
        if (!f.exists()) {
            _log.warn("File does not exist: {}", f.getAbsolutePath());
            return Optional.empty();
        }
        try (InputStream fis = new FileInputStream(f)) {
            int bufsize = (int) f.length();
            return streamToString(fis, bufsize);
        } catch (Exception e) {
            _log.error("Failed to read file to string: {}", f.getAbsolutePath(), e);
            return Optional.empty();
        }
    }

    /**
     * Converts an InputStream to a String using a default buffer size.
     *
     * @param is the InputStream to convert
     * @return an Optional containing the string content, or Optional.empty() if conversion fails
     */
    public static Optional<String> streamToString(InputStream is) {
        return streamToString(is, 32768);  // default bufsize
    }


    /**
     * Converts an InputStream to a String using the specified buffer size.
     * <p>
     * This method reads all bytes from the input stream and converts them to a UTF-8 string.
     * The conversion preserves UTF-8 encoding by using a buffered byte stream.
     * </p>
     *
     * @param is      the InputStream to convert (must not be null)
     * @param bufSize the buffer size to use for reading
     * @return an Optional containing the string content if conversion succeeds,
     *         or Optional.empty() if an IOException occurs during reading
     */
    public static Optional<String> streamToString(InputStream is, int bufSize) {
        try {

            // read reply into a buffered byte stream - to preserve UTF-8
            BufferedInputStream inStream = new BufferedInputStream(is);
            ByteArrayOutputStream outStream = new ByteArrayOutputStream(bufSize);
            byte[] buffer = new byte[bufSize];
            int bytesRead;

            while ((bytesRead = inStream.read(buffer, 0, bufSize)) > 0) {
                outStream.write(buffer, 0, bytesRead);
            }

            outStream.close();
            inStream.close();

            // convert the bytes to a UTF-8 string
            return Optional.of(outStream.toString(StandardCharsets.UTF_8));

        } catch (IOException ioe) {
            _log.error("Failed to convert stream to string", ioe);
            return Optional.empty();
        }
    }


    /**
     * Reads a file's contents into a string by filename.
     * <p>
     * For backward compatibility, this method returns null on failure.
     * </p>
     *
     * @param filename the name of the file to read
     * @return the file contents as a string, or null if reading fails
     * @deprecated Use {@link #fileToStringOptional(String)} for proper error handling
     */
    @Deprecated(since = "6.0.0", forRemoval = false)
    public static String fileToString(String filename) {
        return fileToStringOptional(filename).orElse(null);
    }

    /**
     * Reads a file's contents into a string by filename with proper Optional handling.
     * <p>
     * This method follows TPS principles by making errors visible through
     * Optional.empty() rather than silent null returns.
     * </p>
     *
     * @param filename the name of the file to read (must not be null or empty)
     * @return Optional containing the file contents, or Optional.empty() if reading fails
     */
    public static Optional<String> fileToStringOptional(String filename) {
        if (isNullOrEmpty(filename)) {
            _log.error("Null or empty filename passed to fileToStringOptional");
            return Optional.empty();
        }
        return fileToStringOptional(new File(filename));
    }


    /**
     * Replaces all occurrences of a character sequence in a file.
     * <p>
     * This method follows TPS principles by returning a boolean success indicator
     * and logging errors rather than failing silently.
     * </p>
     *
     * @param f the file to modify
     * @param oldChars the character sequence to replace
     * @param newChars the replacement character sequence
     * @return true if the replacement was successful, false otherwise
     */
    public static boolean replaceInFile(File f, CharSequence oldChars, CharSequence newChars) {
        if (f == null) {
            _log.error("Null file passed to replaceInFile");
            return false;
        }
        Optional<String> contentOpt = fileToStringOptional(f);
        if (contentOpt.isPresent()) {
            String modified = contentOpt.get().replace(oldChars, newChars);
            return stringToFileOptional(f, modified).isPresent();
        }
        return false;
    }


    /**
     * Replaces all occurrences of a character sequence in a file by filename.
     * <p>
     * This method follows TPS principles by returning a boolean success indicator
     * and logging errors rather than failing silently.
     * </p>
     *
     * @param fileName the name of the file to modify
     * @param oldChars the character sequence to replace
     * @param newChars the replacement character sequence
     * @return true if the replacement was successful, false otherwise
     */
    public static boolean replaceInFile(String fileName, CharSequence oldChars,
                                        CharSequence newChars) {
        if (isNullOrEmpty(fileName)) {
            _log.error("Null or empty filename passed to replaceInFile");
            return false;
        }
        Optional<String> contentOpt = fileToStringOptional(fileName);
        if (contentOpt.isPresent()) {
            String modified = contentOpt.get().replace(oldChars, newChars);
            try {
                stringToFile(fileName, modified);
                return true;
            } catch (RuntimeException e) {
                _log.error("Failed to write modified content to file: {}", fileName, e);
                return false;
            }
        }
        return false;
    }


    /**
     * Extracts a substring matching a regex pattern.
     * <p>
     * For backward compatibility, this method returns null if no match is found.
     * </p>
     *
     * @param source the string to search in
     * @param pattern the regex pattern to match
     * @return the first matching substring, or null if no match
     * @deprecated Use {@link #extractOptional(String, String)} for proper null handling
     */
    @Deprecated(since = "6.0.0", forRemoval = false)
    public static String extract(String source, String pattern) {
        return extractOptional(source, pattern).orElse(null);
    }

    /**
     * Extracts a substring matching a regex pattern with proper Optional handling.
     * <p>
     * This method follows TPS principles by making null inputs and no-match cases
     * visible through Optional.empty() rather than silent null returns.
     * </p>
     *
     * @param source the string to search in (may be null)
     * @param pattern the regex pattern to match (may be null)
     * @return Optional containing the first matching substring, or Optional.empty() if no match or invalid input
     */
    public static Optional<String> extractOptional(String source, String pattern) {
        if (source == null) {
            _log.debug("Null source passed to extractOptional");
            return Optional.empty();
        }
        if (pattern == null) {
            _log.debug("Null pattern passed to extractOptional");
            return Optional.empty();
        }
        try {
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(source);
            if (m.find()) {
                return Optional.ofNullable(m.group());
            }
            _log.debug("No match found for pattern '{}' in source", pattern);
            return Optional.empty();
        } catch (Exception e) {
            _log.warn("Invalid pattern '{}' in extractOptional: {}", pattern, e.getMessage());
            return Optional.empty();
        }
    }


    public static String getRandomString(int length) {
        return RandomStringUtils.random(length);
    }

    public static boolean isNullOrEmpty(String s) {
        return s == null || s.isEmpty();
    }


    public static int strToInt(String s, int def) {
        if (isNullOrEmpty(s)) return def;      // short circuit
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }


    public static long strToLong(String s, long def) {
        if (isNullOrEmpty(s)) return def;      // short circuit
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }


    public static double strToDouble(String s, double def) {
        if (isNullOrEmpty(s)) return def;      // short circuit
        try {
            return Double.valueOf(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }


    public static boolean strToBoolean(String s) {
        return !isNullOrEmpty(s) && s.equalsIgnoreCase("true");
    }


    /**
     * Parses a string representation of an XML Schema duration into a Duration object.
     * <p>
     * Valid duration strings follow the XML Schema duration format, e.g., "P1Y2M3DT4H5M6S".
     * The string must start with 'P' (for period).
     * </p>
     *
     * @param s the duration string to parse (may be null)
     * @return an Optional containing the parsed Duration if the string is valid,
     *         or Optional.empty() if the string is null, empty, or malformed
     * @see Duration
     */
    public static Optional<Duration> strToDuration(String s) {
        if (s != null) {
            try {
                return Optional.of(DatatypeFactory.newInstance().newDuration(s));
            } catch (DatatypeConfigurationException dce) {
                _log.warn("Failed to create Duration from string '{}': {}", s, dce.getMessage());
            } catch (IllegalArgumentException dce) {
                _log.warn("Invalid duration format '{}': {}", s, dce.getMessage());
            }
        }
        return Optional.empty();
    }


    /**
     * Converts a duration in milliseconds to a Duration object.
     * <p>
     * Creates a javax.xml.datatype.Duration from a millisecond value. Valid values
     * must be non-negative (greater than -1).
     * </p>
     *
     * @param msecs the duration in milliseconds (must be greater than -1)
     * @return an Optional containing the Duration if conversion succeeds,
     *         or Optional.empty() if msecs is negative or conversion fails
     * @see Duration
     */
    public static Optional<Duration> msecsToDuration(long msecs) {
        if (msecs > -1) {
            try {
                return Optional.of(DatatypeFactory.newInstance().newDuration(msecs));
            } catch (DatatypeConfigurationException | IllegalArgumentException dce) {
                _log.warn("Failed to create Duration from {} milliseconds: {}", msecs, dce.getMessage());
            }
        }
        return Optional.empty();
    }


    public static boolean isValidDurationString(String s) {
        try {
            DatatypeFactory.newInstance().newDuration(s);
            return true;
        } catch (DatatypeConfigurationException | IllegalArgumentException e) {
            return false;
        }
    }


    public static long durationToMSecs(Duration d, long def) {
        return (d != null) ? d.getTimeInMillis(new Date()) : def;
    }

    public static long durationToMSecs(Duration d) {
        return durationToMSecs(d, 0);
    }

    public static long durationStrToMSecs(String s) {
        return strToDuration(s)
                .map(StringUtil::durationToMSecs)
                .orElse(0L);
    }

    /**
     * Converts an XML date string to a long timestamp.
     * <p>
     * For backward compatibility, this method returns -1 on failure.
     * </p>
     *
     * @param s the XML date string to convert
     * @return the timestamp in milliseconds, or -1 if conversion fails
     * @deprecated Use {@link #xmlDateToLongOptional(String)} for proper error handling
     */
    @Deprecated(since = "6.0.0", forRemoval = false)
    public static long xmlDateToLong(String s) {
        return xmlDateToLongOptional(s).orElse(-1L);
    }

    /**
     * Converts an XML date string to a long timestamp with proper Optional handling.
     * <p>
     * This method follows TPS principles by making errors visible through
     * Optional.empty() rather than silent -1 returns.
     * </p>
     *
     * @param s the XML date string to convert (may be null)
     * @return Optional containing the timestamp in milliseconds, or Optional.empty() if conversion fails
     */
    public static Optional<Long> xmlDateToLongOptional(String s) {
        if (s == null) {
            _log.debug("Null XML date string passed to xmlDateToLongOptional");
            return Optional.empty();
        }
        try {
            XMLGregorianCalendar cal =
                    DatatypeFactory.newInstance().newXMLGregorianCalendar(s);
            return Optional.of(cal.toGregorianCalendar().getTimeInMillis());
        } catch (DatatypeConfigurationException dce) {
            _log.warn("Failed to create XML calendar from string '{}': {}", s, dce.getMessage());
            return Optional.empty();
        } catch (IllegalArgumentException iae) {
            _log.warn("Invalid XML date format '{}': {}", s, iae.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Converts a long timestamp to an XML datetime string.
     * <p>
     * For backward compatibility, this method returns null on failure.
     * </p>
     *
     * @param time the timestamp in milliseconds
     * @return the XML datetime string, or null if conversion fails
     * @deprecated Use {@link #longToDateTimeOptional(long)} for proper error handling
     */
    @Deprecated(since = "6.0.0", forRemoval = false)
    public static String longToDateTime(long time) {
        return longToDateTimeOptional(time).orElse(null);
    }

    /**
     * Converts a long timestamp to an XML datetime string with proper Optional handling.
     * <p>
     * This method follows TPS principles by making errors visible through
     * Optional.empty() rather than silent null returns.
     * </p>
     *
     * @param time the timestamp in milliseconds
     * @return Optional containing the XML datetime string, or Optional.empty() if conversion fails
     */
    public static Optional<String> longToDateTimeOptional(long time) {
        GregorianCalendar gregCal = new GregorianCalendar();
        gregCal.setTimeInMillis(time);
        try {
            XMLGregorianCalendar cal =
                    DatatypeFactory.newInstance().newXMLGregorianCalendar(gregCal);
            return Optional.of(cal.toXMLFormat());
        } catch (DatatypeConfigurationException dce) {
            _log.error("Failed to convert long {} to DateTime", time, dce);
            return Optional.empty();
        }
    }


    /**
     * Finds the first occurrence of a substring within a string, starting at the specified position.
     * <p>
     * This method returns -1 for null inputs, consistent with {@link String#indexOf(String, int)} behavior.
     * </p>
     *
     * @param toSearch the string to search in
     * @param toFind the string to find
     * @param start the starting position for the search
     * @param ignoreCase whether to ignore case when searching
     * @return the index of the first occurrence, or -1 if not found or inputs are null
     * @deprecated Use {@link #findOptional(String, String, int, boolean)} for proper null handling
     */
    @Deprecated(since = "6.0.0", forRemoval = false)
    public static int find(String toSearch, String toFind, int start, boolean ignoreCase) {
        if ((toSearch == null) || (toFind == null)) return -1;
        if (ignoreCase) {
            toSearch = toSearch.toUpperCase();
            toFind = toFind.toUpperCase();
        }
        return find(toSearch, toFind, start);
    }

    /**
     * Finds the first occurrence of a substring within a string with proper Optional handling.
     * <p>
     * This method follows TPS principles by making null inputs visible through
     * Optional.empty() rather than silent -1 returns.
     * </p>
     *
     * @param toSearch the string to search in (may be null)
     * @param toFind the string to find (may be null)
     * @param start the starting position for the search
     * @param ignoreCase whether to ignore case when searching
     * @return Optional containing the index of the first occurrence, or Optional.empty() if not found or inputs are null
     */
    public static Optional<Integer> findOptional(String toSearch, String toFind, int start, boolean ignoreCase) {
        if (toSearch == null) {
            _log.debug("Null toSearch passed to findOptional");
            return Optional.empty();
        }
        if (toFind == null) {
            _log.debug("Null toFind passed to findOptional");
            return Optional.empty();
        }
        int result = find(toSearch, toFind, start, ignoreCase);
        return result >= 0 ? Optional.of(result) : Optional.empty();
    }


    /**
     * Finds the first occurrence of a substring within a string, starting at the specified position.
     * <p>
     * This method returns -1 for null inputs, consistent with {@link String#indexOf(String, int)} behavior.
     * For strings shorter than 2048 characters or search patterns shorter than 4 characters,
     * it delegates to the standard String.indexOf method. Otherwise, it uses a Boyer-Moore-like
     * algorithm for better performance on large strings.
     * </p>
     *
     * @param toSearch the string to search in
     * @param toFind the string to find
     * @param start the starting position for the search
     * @return the index of the first occurrence, or -1 if not found or inputs are null
     * @deprecated Use {@link #findOptional(String, String, int)} for proper null handling
     */
    @Deprecated(since = "6.0.0", forRemoval = false)
    public static int find(String toSearch, String toFind, int start) {
        if ((toSearch == null) || (toFind == null)) return -1;
        if ((toSearch.length() < 2048) || (toFind.length() < 4)) {
            return toSearch.indexOf(toFind, start);
        }
        if (start < 0) start = 0;
        final int lastCharToFindIndex = toFind.length() - 1;
        final char lastCharToFind = toFind.charAt(toFind.length() - 1);

        int[] skipTable = new int[255];
        for (int i = 0; i < 255; i++) skipTable[i] = toFind.length();
        for (int i = 0; i < lastCharToFindIndex; i++) {
            skipTable[toFind.charAt(i) & 255] = lastCharToFindIndex - i;
        }

        for (int i = start + lastCharToFindIndex; i < toSearch.length();
             i += skipTable[toSearch.charAt(i) & 255]) {

            if (toSearch.charAt(i) != lastCharToFind) {
                while ((i += skipTable[toSearch.charAt(i) & 255]) < toSearch.length()
                        && toSearch.charAt(i) != lastCharToFind) ;

                if (i < toSearch.length()) {
                    int j = i - 1;
                    int index = i - toFind.length() + 1;
                    for (int k = lastCharToFindIndex - 1;
                         j > index && toSearch.charAt(j) == toFind.charAt(k);
                         j--, k--);

                    if (j == index) return index;
                } else break;
            }
        }
        return -1;
    }

    /**
     * Finds the first occurrence of a substring within a string with proper Optional handling.
     * <p>
     * This method follows TPS principles by making null inputs visible through
     * Optional.empty() rather than silent -1 returns.
     * </p>
     *
     * @param toSearch the string to search in (may be null)
     * @param toFind the string to find (may be null)
     * @param start the starting position for the search
     * @return Optional containing the index of the first occurrence, or Optional.empty() if not found or inputs are null
     */
    public static Optional<Integer> findOptional(String toSearch, String toFind, int start) {
        if (toSearch == null) {
            _log.debug("Null toSearch passed to findOptional");
            return Optional.empty();
        }
        if (toFind == null) {
            _log.debug("Null toFind passed to findOptional");
            return Optional.empty();
        }
        int result = find(toSearch, toFind, start);
        return result >= 0 ? Optional.of(result) : Optional.empty();
    }

    /**
     * Finds the first occurrence of a substring within a string.
     * <p>
     * This method returns -1 for null inputs, consistent with {@link String#indexOf(String)} behavior.
     * </p>
     *
     * @param toSearch the string to search in
     * @param toFind the string to find
     * @return the index of the first occurrence, or -1 if not found or inputs are null
     * @deprecated Use {@link #findOptional(String, String)} for proper null handling
     */
    @Deprecated(since = "6.0.0", forRemoval = false)
    public static int find(String toSearch, String toFind) {
        return find(toSearch, toFind, 0);
    }

    /**
     * Finds the first occurrence of a substring within a string with proper Optional handling.
     * <p>
     * This method follows TPS principles by making null inputs visible through
     * Optional.empty() rather than silent -1 returns.
     * </p>
     *
     * @param toSearch the string to search in (may be null)
     * @param toFind the string to find (may be null)
     * @return Optional containing the index of the first occurrence, or Optional.empty() if not found or inputs are null
     */
    public static Optional<Integer> findOptional(String toSearch, String toFind) {
        return findOptional(toSearch, toFind, 0);
    }


    /**
     * Finds all occurrences of a substring within a string.
     * <p>
     * For backward compatibility, this method returns an empty list for null inputs.
     * </p>
     *
     * @param toSearch the string to search in
     * @param toFind the string to find
     * @param ignoreCase whether to ignore case when searching
     * @return a list of all occurrence indices, or empty list if inputs are null
     * @deprecated Use {@link #findAllOptional(String, String, boolean)} for proper null handling
     */
    @Deprecated(since = "6.0.0", forRemoval = false)
    public static List<Integer> findAll(String toSearch, String toFind, boolean ignoreCase) {
        if (ignoreCase) {
            if (!((toSearch == null) || (toFind == null))) {
                toSearch = toSearch.toUpperCase();
                toFind = toFind.toUpperCase();
            }
        }
        return findAll(toSearch, toFind);
    }

    /**
     * Finds all occurrences of a substring within a string with proper Optional handling.
     * <p>
     * This method follows TPS principles by making null inputs visible through
     * Optional.empty() rather than silent empty list returns.
     * </p>
     *
     * @param toSearch the string to search in (may be null)
     * @param toFind the string to find (may be null)
     * @param ignoreCase whether to ignore case when searching
     * @return Optional containing a list of all occurrence indices, or Optional.empty() if inputs are null
     */
    public static Optional<List<Integer>> findAllOptional(String toSearch, String toFind, boolean ignoreCase) {
        if (toSearch == null) {
            _log.debug("Null toSearch passed to findAllOptional");
            return Optional.empty();
        }
        if (toFind == null) {
            _log.debug("Null toFind passed to findAllOptional");
            return Optional.empty();
        }
        return Optional.of(findAll(toSearch, toFind, ignoreCase));
    }


    /**
     * Finds all occurrences of a substring within a string.
     * <p>
     * For backward compatibility, this method returns an empty list for null inputs.
     * </p>
     *
     * @param toSearch the string to search in
     * @param toFind the string to find
     * @return a list of all occurrence indices, or empty list if inputs are null
     * @deprecated Use {@link #findAllOptional(String, String)} for proper null handling
     */
    @Deprecated(since = "6.0.0", forRemoval = false)
    public static List<Integer> findAll(String toSearch, String toFind) {
        List<Integer> foundList = new ArrayList<Integer>();
        int start = 0;
        while (start > -1) {
            start = find(toSearch, toFind, start);
            if (start > -1) foundList.add(start++);
        }
        return foundList;
    }

    /**
     * Finds all occurrences of a substring within a string with proper Optional handling.
     * <p>
     * This method follows TPS principles by making null inputs visible through
     * Optional.empty() rather than silent empty list returns.
     * </p>
     *
     * @param toSearch the string to search in (may be null)
     * @param toFind the string to find (may be null)
     * @return Optional containing a list of all occurrence indices, or Optional.empty() if inputs are null
     */
    public static Optional<List<Integer>> findAllOptional(String toSearch, String toFind) {
        if (toSearch == null) {
            _log.debug("Null toSearch passed to findAllOptional");
            return Optional.empty();
        }
        if (toFind == null) {
            _log.debug("Null toFind passed to findAllOptional");
            return Optional.empty();
        }
        return Optional.of(findAll(toSearch, toFind));
    }


    public static String repeat(char c, int count) {
        char[] chars = new char[count];
        for (int i = 0; i < count; i++) chars[i] = c;
        return new String(chars);
    }


    /**
     * Joins a list of objects into a single string with the specified separator.
     * <p>
     * For backward compatibility, this method returns an empty string for null or empty lists.
     * </p>
     *
     * @param list the list of objects to join
     * @param separator the character to use as separator
     * @return the joined string, or empty string if list is null or empty
     * @deprecated Use {@link #joinOptional(List, char)} for proper null handling
     */
    @Deprecated(since = "6.0.0", forRemoval = false)
    public static String join(List<?> list, char separator) {
        if (list == null || list.isEmpty()) return new String();  // Empty list join produces empty string
        if (list.size() == 1) return list.get(0).toString();
        StringBuilder sb = new StringBuilder();
        for (Object s : list) {
            if (sb.length() > 0) sb.append(separator);
            sb.append(s);
        }
        return sb.toString();
    }

    /**
     * Joins a list of objects into a single string with the specified separator
     * with proper Optional handling.
     * <p>
     * This method follows TPS principles by making null inputs visible through
     * Optional.empty() rather than silent empty string returns.
     * </p>
     *
     * @param list the list of objects to join (may be null)
     * @param separator the character to use as separator
     * @return Optional containing the joined string, or Optional.empty() if list is null,
     *         or Optional.of("") if list is empty
     */
    public static Optional<String> joinOptional(List<?> list, char separator) {
        if (list == null) {
            _log.debug("Null list passed to joinOptional");
            return Optional.empty();
        }
        if (list.isEmpty()) {
            return Optional.of("");
        }
        if (list.size() == 1) {
            return Optional.of(list.get(0).toString());
        }
        StringBuilder sb = new StringBuilder();
        for (Object s : list) {
            if (sb.length() > 0) sb.append(separator);
            sb.append(s);
        }
        return Optional.of(sb.toString());
    }


    public static List<String> splitToList(String s, String separator) {
        if (isNullOrEmpty(s)) return Collections.emptyList();
        return Arrays.asList(s.split(separator));
    }


    public static String insert(String base, String addition, int position) {
        if (base == null || addition == null ||
                position < 0 || position > base.length() - 1) {
            return base;
        }
        StringBuilder sb = new StringBuilder(base.length() + addition.length());
        sb.append(base);
        sb.insert(position, addition);
        return sb.toString();
    }


    public static String pad(String s, int len, char padChar) {
        return pad(s, len, padChar, true);                       // default to left
    }

    public static String pad(String s, int len, char padChar, boolean left) {
        if (len <= s.length()) return s;

        StringBuilder sb = new StringBuilder();
        char[] pad = new char[len - s.length()];
        Arrays.fill(pad, padChar);
        if (left) sb.append(pad).append(s);
        else sb.append(s).append(pad);
        return sb.toString();
    }


    public static String firstWord(String s) {
        if (s == null) return s;
        return s.split("\\s")[0];
    }


    /**
     * Converts a Set of strings to an XML representation.
     * <p>
     * For backward compatibility, this method returns null if the set is null.
     * </p>
     *
     * @param set the set of strings to convert
     * @return the XML representation, or null if the set is null
     * @deprecated Use {@link #setToXMLOptional(Set)} for proper null handling
     */
    @Deprecated(since = "6.0.0", forRemoval = false)
    public static String setToXML(Set<String> set) {
        return setToXMLOptional(set).orElse(null);
    }

    /**
     * Converts a Set of strings to an XML representation with proper Optional handling.
     * <p>
     * This method follows TPS principles by making null inputs visible through
     * Optional.empty() rather than silent null returns.
     * </p>
     *
     * @param set the set of strings to convert (may be null)
     * @return Optional containing the XML representation, or Optional.empty() if the set is null
     */
    public static Optional<String> setToXMLOptional(Set<String> set) {
        if (set == null) {
            _log.debug("Null set passed to setToXMLOptional");
            return Optional.empty();
        }
        StringBuilder sb = new StringBuilder("<set>");
        for (String s : set) {
            sb.append(wrap(s, "item"));
        }
        sb.append("</set>");
        return Optional.of(sb.toString());
    }

    public static Set<String> xmlToSet(String xml) {
        Set<String> set = new HashSet<String>();
        XNode setNode = new XNodeParser(true).parse(xml);
        if (setNode != null) {
            for (XNode item : setNode.getChildren()) {
                set.add(item.getText());
            }
        }
        return set;
    }

}
