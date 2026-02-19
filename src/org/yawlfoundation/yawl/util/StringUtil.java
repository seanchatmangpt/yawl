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
     * Utility method to take a string and return the string in revserse sequence.
     *
     * @param inputString String to be reversed
     * @return Reversed string
     */
    public static String reverseString(String inputString) {
        char[] inputChars = new char[inputString.length()];
        char[] outputChars = new char[inputString.length()];

        inputString.getChars(0, inputString.length(), inputChars, 0);
        int pointer = inputChars.length - 1;


        for (int i = 0; i <= inputChars.length - 1; i++) {
            outputChars[pointer] = inputChars[i];
            pointer--;
        }

        return new String(outputChars);
    }

    /**
     * Removes all white space from a string.
     *
     * @param string String to remove white space from
     * @return Resulting whitespaceless string.
     */
    public static String removeAllWhiteSpace(String string) {
        Pattern p = Pattern.compile("[\\s]");
        Matcher m;
        do {
            m = p.matcher(string);
            if (m.find()) {
                string = m.replaceAll("");
            }
        } while (m.find());

        return string;
    }

    /**
     * Formats a postcode into standard Royal Mail format
     *
     * @param postcode
     * @return Postcode correctly formatted
     */
    public static String formatPostCode(String postcode) {
        if (postcode == null) return null;
        postcode = removeAllWhiteSpace(postcode).toUpperCase();
        if (postcode.length() < 3) return postcode;
        else
            return postcode.substring(0, postcode.length() - 3) + " " + postcode.substring(postcode.length() - 3, postcode.length());
    }

    /**
     * Formats a sortcode into the common form nn-nn-nn
     *
     * @param sortcode
     * @return Sortcode correctly formatted
     */
    public static String formatSortCode(String sortcode) {
        return sortcode.substring(0, 2) + "-" + sortcode.substring(2, 4) + "-" + sortcode.substring(4, 6);
    }

    /**
     * Converts a string to all lower case, and capitalises the first letter of the string
     *
     * @param s unformated string.
     * @return The formated string.
     */
    public static String capitalise(String s) {
        if ((s == null) || (s.length() == 0)) return s;
        char[] chars = s.toLowerCase().toCharArray();
        chars[0] = Character.toUpperCase(chars[0]);
        return String.valueOf(chars);
    }

    /**
     * Utility routine that takes in a Calendar referece and returns a date/time stamp suitable for use
     * in a Portlets environment.
     *
     * @param calendar
     * @return Date/timestamp suitable for display.
     * @deprecated Use TimeUtil.formatUIDate
     */
    public static String formatUIDate(Calendar calendar) {
        SimpleDateFormat fmt = null;

        /**
         * Set format depending upon whether we have a timestamp component to the calendar.
         * Ok, this is slightly flawed as an assumption as we could be bang on midnight.......
         */
        if ((calendar.get(Calendar.HOUR) == 0) && (calendar.get(Calendar.MINUTE) == 0)
                && (calendar.get(Calendar.SECOND) == 0)) {
            fmt = new SimpleDateFormat("dd-MMM-yy");
        } else {
            fmt = new SimpleDateFormat("dd-MMM-yy hh:mm a");
        }

        return fmt.format(calendar.getTime());
    }

    /**
     * Utility routine which takes a decimal value as a string (e.g. 0.25 equating to 25p) and returns the
     * value in UI currency format (e.g. L0.25).
     *
     * @return A formatted currency
     */
    public static String formatDecimalCost(BigDecimal value) {
        Currency currency = Currency.getInstance(Locale.getDefault());
        NumberFormat fmt = DecimalFormat.getInstance();
        fmt.setMinimumFractionDigits(2);
        fmt.setMaximumFractionDigits(2);
        return currency.getSymbol() + fmt.format(value);
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
     *
     * @param t Throwable to convert to a String
     * @return String representation of Throwable t
     */
    public static String convertThrowableToString(Throwable t) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(baos);
        t.printStackTrace(writer);
        writer.flush();
        return baos.toString();
    }

    /**
     * Esacpes all HTML entities and "funky accents" into the HTML 4.0 encodings, replacing
     * new lines with "&lt;br&gt;", tabs with four "&amp;nbsp;" and single spaces with "&amp;nbsp;".
     *
     * @param string to escape
     * @return escaped string
     */
    public static String formatForHTML(String string) {
        string = StringEscapeUtils.escapeHtml4(string);
        string = string.replaceAll("\n", "<br>");
        string = string.replaceAll("\t", "&nbsp;&nbsp;&nbsp;&nbsp;");
        string = string.replaceAll(" ", "&nbsp;");
        return string;
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
     * Removes an outer set of xml tags from an xml string, if possible
     *
     * @param xml the xml string to strip
     * @return the stripped xml string, or empty string for self-closing tags
     */
    public static String unwrap(String xml) {
        if (xml != null) {
            if (xml.matches("^<\\w+/>$")) {                      // shortened tag pair - no content
                return new String();  // Self-closing tags have empty content by definition
            }
            int start = xml.indexOf('>') + 1;
            int end = xml.lastIndexOf('<');
            if (end >= start) {
                return xml.substring(start, end);
            }
        }
        return xml;
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
     * Wraps a string in the specified quote marks
     * @param s the string to wrap
     * @param quoteMark the quote character to use
     * @return the wrapped sgtring
     */
    public static String enQuote(String s, char quoteMark) {
        return s == null ? s :
                new StringBuilder(s.length() + 2)
                        .append(quoteMark).append(s).append(quoteMark).toString();
    }


    /**
     * Encodes reserved characters in an xml string
     *
     * @param s the string to encode
     * @return the newly encoded string
     */
    public static String xmlEncode(String s) {
        if (s == null) return s;
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /**
     * Decodes reserved characters in an xml string
     *
     * @param s the string to decode
     * @return the newly decoded string
     */
    public static String xmlDecode(String s) {
        if (s == null) return s;
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    public static boolean isIntegerString(String s) {
        if (s == null) return false;
        char[] digits = s.toCharArray();
        for (char digit : digits) {
            if ((digit < '0') || (digit > '9')) return false;
        }
        return true;
    }


    public static File stringToFile(String path, String contents) {
        if (isNullOrEmpty(path) || contents == null) {
            throw new IllegalArgumentException("Arguments must not be null");
        }
        int sepPos = path.lastIndexOf(File.separator);
        File toFile;
        if (sepPos > -1) {
            File dir = new File(path.substring(0, sepPos));
            dir.mkdirs();
            toFile = new File(dir, path.substring(sepPos + 1));
        }
        else toFile = new File(path);

        return stringToFile(toFile, contents);
    }


    public static File stringToTempFile(String contents) {
        try {
            return stringToFile(
                    File.createTempFile(
                            RandomStringUtils.randomAlphanumeric(12), null), contents);
        } catch (IOException e) {
            _log.error("Failed to create temporary file", e);
            return null;
        }
    }


    public static File stringToFile(File f, String contents) {
        try {
            BufferedWriter buf = new BufferedWriter(new FileWriter(f));
            buf.write(contents, 0, contents.length());
            buf.close();
        } catch (IOException ioe) {
            f = null;
        }
        return f;
    }


    public static String fileToString(File f) {
        if (f.exists()) {
            try {
                int bufsize = (int) f.length();
                InputStream fis = new FileInputStream(f);
                return streamToString(fis, bufsize).orElse(null);
            } catch (Exception e) {
                _log.error("Failed to read file to string", e);
                return null;
            }
        } else return null;
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


    public static String fileToString(String filename) {
        return fileToString(new File(filename));
    }


    public static boolean replaceInFile(File f, CharSequence oldChars, CharSequence newChars) {
        String s = fileToString(f);
        if (s != null) {
            s = s.replace(oldChars, newChars);
            stringToFile(f, s);
        }
        return s != null;
    }


    public static boolean replaceInFile(String fileName, CharSequence oldChars,
                                        CharSequence newChars) {
        String s = fileToString(fileName);
        if (s != null) {
            s = s.replace(oldChars, newChars);
            stringToFile(fileName, s);
        }
        return s != null;
    }


    public static String extract(String source, String pattern) {
        String extracted = null;
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(source);
        if (m.find()) {
            extracted = m.group();
        }
        return extracted;
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

    public static long xmlDateToLong(String s) {
        if (s == null) return -1;
        try {
            XMLGregorianCalendar cal =
                    DatatypeFactory.newInstance().newXMLGregorianCalendar(s);
            return cal.toGregorianCalendar().getTimeInMillis();
        } catch (DatatypeConfigurationException dce) {
            return -1;
        }
    }

    public static String longToDateTime(long time) {
        GregorianCalendar gregCal = new GregorianCalendar();
        gregCal.setTimeInMillis(time);
        try {
            XMLGregorianCalendar cal =
                    DatatypeFactory.newInstance().newXMLGregorianCalendar(gregCal);
            return cal.toXMLFormat();
        } catch (DatatypeConfigurationException dce) {
            _log.error("Failed to convert long to DateTime", dce);
            return null;
        }
    }


    public static int find(String toSearch, String toFind, int start, boolean ignoreCase) {
        if ((toSearch == null) || (toFind == null)) return -1;
        if (ignoreCase) {
            toSearch = toSearch.toUpperCase();
            toFind = toFind.toUpperCase();
        }
        return find(toSearch, toFind, start);
    }


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

    public static int find(String toSearch, String toFind) {
        return find(toSearch, toFind, 0);
    }


    public static List<Integer> findAll(String toSearch, String toFind, boolean ignoreCase) {
        if (ignoreCase) {
            if (!((toSearch == null) || (toFind == null))) {
                toSearch = toSearch.toUpperCase();
                toFind = toFind.toUpperCase();
            }
        }
        return findAll(toSearch, toFind);
    }


    public static List<Integer> findAll(String toSearch, String toFind) {
        List<Integer> foundList = new ArrayList<Integer>();
        int start = 0;
        while (start > -1) {
            start = find(toSearch, toFind, start);
            if (start > -1) foundList.add(start++);
        }
        return foundList;
    }


    public static String repeat(char c, int count) {
        char[] chars = new char[count];
        for (int i = 0; i < count; i++) chars[i] = c;
        return new String(chars);
    }


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


    public static String setToXML(Set<String> set) {
        if (set == null) return null;
        StringBuilder sb = new StringBuilder("<set>");
        for (String s : set) sb.append(wrap(s, "item"));
        sb.append("</set>");
        return sb.toString();
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
