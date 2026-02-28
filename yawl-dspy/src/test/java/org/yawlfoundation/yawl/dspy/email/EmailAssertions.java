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
 * You should have received a copy of the GNU Lesser General
 * License along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.dspy.email;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Chicago TDD email assertion utilities for DSPy integration tests.
 *
 * <p>Provides expressive assertions for email content validation, enabling
 * board/executive stakeholders to consume reports via email-style assertions.
 * These assertions verify business outcomes, not just method calls.</p>
 *
 * <h2>Usage Pattern</h2>
 * <pre>{@code
 * assertEmailContains(email, """
 *     Subject: Daily AI Operations Summary
 *     Average confidence: ≥0.70
 *     Cache hit rate: ≥90%
 *     """);
 * }</pre>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class EmailAssertions {

    private EmailAssertions() {
        // Utility class - no instantiation
    }

    /**
     * Asserts that email contains all expected content fragments.
     *
     * <p>Each line in expectedContent is checked independently. Lines starting
     * with special markers enable threshold assertions:</p>
     * <ul>
     *   <li>{@code key: ≥value} - Asserts numeric value is at least threshold</li>
     *   <li>{@code key: ≤value} - Asserts numeric value is at most threshold</li>
     *   <li>{@code key: [a, b, c]} - Asserts list contains all items</li>
     *   <li>{@code key: present} - Asserts key exists with any value</li>
     *   <li>{@code key: numeric} - Asserts value is numeric</li>
     * </ul>
     *
     * @param email the email to validate
     * @param expectedContent multi-line expected content
     */
    public static void assertEmailContains(Email email, String expectedContent) {
        assertThat("Email must not be null", email, notNullValue());
        assertThat("Email body must not be null", email.body(), notNullValue());

        String body = email.body();
        String[] lines = expectedContent.strip().split("\n");

        for (String line : lines) {
            String trimmedLine = line.strip();
            if (trimmedLine.isEmpty()) {
                continue;
            }

            if (trimmedLine.contains(": ≥")) {
                assertThresholdAtLeast(body, trimmedLine);
            } else if (trimmedLine.contains(": ≤")) {
                assertThresholdAtMost(body, trimmedLine);
            } else if (trimmedLine.contains(": [") && trimmedLine.endsWith("]")) {
                assertListContains(body, trimmedLine);
            } else if (trimmedLine.contains(": present")) {
                assertKeyPresent(body, trimmedLine);
            } else if (trimmedLine.contains(": numeric")) {
                assertKeyNumeric(body, trimmedLine);
            } else if (trimmedLine.contains(": improvement")) {
                assertImprovement(body, trimmedLine);
            } else {
                assertContainsText(body, trimmedLine);
            }
        }
    }

    /**
     * Asserts email has the expected subject line.
     */
    public static void assertSubjectContains(Email email, String expectedSubjectFragment) {
        assertThat("Email subject must contain: " + expectedSubjectFragment,
                email.subject(), containsString(expectedSubjectFragment));
    }

    /**
     * Asserts email has the expected recipient.
     */
    public static void assertRecipient(Email email, String expectedRecipient) {
        assertThat("Email recipient must be: " + expectedRecipient,
                email.to(), containsString(expectedRecipient));
    }

    /**
     * Asserts email has the expected sender.
     */
    public static void assertSender(Email email, String expectedSender) {
        assertThat("Email sender must be: " + expectedSender,
                email.from(), containsString(expectedSender));
    }

    /**
     * Asserts email timestamp is within expected range.
     */
    public static void assertTimestampRecent(Email email, long maxAgeMs) {
        long age = System.currentTimeMillis() - email.timestamp().toEpochMilli();
        assertThat("Email timestamp should be recent",
                age, lessThanOrEqualTo(maxAgeMs));
    }

    // ========================================================================
    // Private assertion helpers
    // ========================================================================

    private static void assertThresholdAtLeast(String body, String line) {
        // Parse "key: ≥value" format
        String[] parts = line.split(":\\s*≥");
        assertThat("Invalid threshold format: " + line, parts.length, equalTo(2));

        String key = parts[0].strip();
        double threshold = Double.parseDouble(parts[1].strip());

        // Extract actual value from body
        double actualValue = extractNumericValue(body, key);
        assertThat(key + " should be at least " + threshold,
                actualValue, greaterThanOrEqualTo(threshold));
    }

    private static void assertThresholdAtMost(String body, String line) {
        // Parse "key: ≤value" format
        String[] parts = line.split(":\\s*≤");
        assertThat("Invalid threshold format: " + line, parts.length, equalTo(2));

        String key = parts[0].strip();
        double threshold = Double.parseDouble(parts[1].strip());

        double actualValue = extractNumericValue(body, key);
        assertThat(key + " should be at most " + threshold,
                actualValue, lessThanOrEqualTo(threshold));
    }

    private static void assertListContains(String body, String line) {
        // Parse "key: [item1, item2, item3]" format
        int colonIdx = line.indexOf(':');
        String key = line.substring(0, colonIdx).strip();
        String listContent = line.substring(colonIdx + 1).strip();

        // Remove brackets and parse items
        String itemsStr = listContent.substring(1, listContent.length() - 1);
        String[] items = itemsStr.split(",\\s*");

        for (String item : items) {
            assertThat("Email should contain " + key + " item: " + item,
                    body, containsString(item));
        }
    }

    private static void assertKeyPresent(String body, String line) {
        // Parse "key: present" format
        String key = line.replace(": present", "").strip();
        assertThat("Email should contain key: " + key,
                body.toLowerCase(), containsString(key.toLowerCase()));
    }

    private static void assertKeyNumeric(String body, String line) {
        // Parse "key: numeric" format
        String key = line.replace(": numeric", "").strip();
        double value = extractNumericValue(body, key);
        assertThat(key + " should be numeric", !Double.isNaN(value));
    }

    private static void assertImprovement(String body, String line) {
        // Parse "key: improvement" format - looks for upward arrow or percentage increase
        String key = line.replace(": improvement", "").strip();

        // Look for patterns like "85.0% (+4.1%)" or "85.0% → 89.2%"
        boolean hasImprovement = body.contains("→") ||
                body.contains("+") ||
                body.contains("improvement") ||
                body.contains("increase");

        assertThat(key + " should show improvement", hasImprovement);
    }

    private static void assertContainsText(String body, String text) {
        assertThat("Email should contain: " + text,
                body, containsString(text));
    }

    /**
     * Extracts a numeric value from email body for a given key.
     *
     * <p>Handles formats like:</p>
     * <ul>
     *   <li>"Average confidence: 0.87"</li>
     *   <li>"Cache hit rate: 94.2%"</li>
     *   <li>"Total executions: 1,247"</li>
     * </ul>
     */
    private static double extractNumericValue(String body, String key) {
        // Create pattern to find key followed by a number
        String escapedKey = Pattern.quote(key);
        Pattern pattern = Pattern.compile(
                escapedKey + "[\\s:]+([\\d,]+(?:\\.\\d+)?%?)",
                Pattern.CASE_INSENSITIVE
        );

        java.util.regex.Matcher matcher = pattern.matcher(body);
        if (matcher.find()) {
            String valueStr = matcher.group(1)
                    .replace(",", "")
                    .replace("%", "");
            return Double.parseDouble(valueStr);
        }

        fail("Could not find numeric value for key: " + key);
        return Double.NaN; // Unreachable due to fail()
    }
}
