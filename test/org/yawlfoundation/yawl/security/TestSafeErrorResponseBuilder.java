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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SafeErrorResponseBuilder.
 */
@DisplayName("Safe Error Response Builder Tests")
class TestSafeErrorResponseBuilder {

    @Test
    @DisplayName("Error response from exception")
    void testFromException() {
        Exception ex = new IllegalArgumentException("Invalid input value");
        SafeErrorResponseBuilder.SafeErrorResponse response =
                SafeErrorResponseBuilder.fromException(400, ex, "req-123");

        assertEquals(400, response.statusCode());
        assertEquals("req-123", response.requestId());
        assertNotNull(response.timestamp());
        assertNotNull(response.message());
    }

    @Test
    @DisplayName("Error response from custom message")
    void testFromMessage() {
        SafeErrorResponseBuilder.SafeErrorResponse response =
                SafeErrorResponseBuilder.fromMessage(500, "Database connection failed", "req-456");

        assertEquals(500, response.statusCode());
        assertEquals("req-456", response.requestId());
    }

    @Test
    @DisplayName("Rate limit exceeded response")
    void testRateLimitExceeded() {
        SafeErrorResponseBuilder.SafeErrorResponse response =
                SafeErrorResponseBuilder.rateLimitExceeded("client-192.168.1.100", "req-789");

        assertEquals(429, response.statusCode());
        assertTrue(response.message().contains("Rate limit exceeded"));
        assertEquals("req-789", response.requestId());
    }

    @Test
    @DisplayName("Validation error response")
    void testValidationError() {
        SafeErrorResponseBuilder.SafeErrorResponse response =
                SafeErrorResponseBuilder.validationError("email", "Invalid email format", "req-101");

        assertEquals(400, response.statusCode());
        assertTrue(response.message().contains("validation failed"));
    }

    @Test
    @DisplayName("Credentials redacted from message")
    void testCredentialsRedacted() {
        String message = "Failed: password=secret123 for user admin";
        String sanitized = SafeErrorResponseBuilder.sanitizeMessage(message);

        assertFalse(sanitized.contains("secret123"));
        assertTrue(sanitized.contains("[REDACTED]"));
    }

    @Test
    @DisplayName("File paths redacted from message")
    void testFilePathsRedacted() {
        String message = "Error in /home/user/app/config.properties line 42";
        String sanitized = SafeErrorResponseBuilder.sanitizeMessage(message);

        assertFalse(sanitized.contains("/home/user"));
        assertTrue(sanitized.contains("[PATH]"));
    }

    @Test
    @DisplayName("IP addresses redacted from message")
    void testIpAddressesRedacted() {
        String message = "Connection from 192.168.1.100 failed";
        String sanitized = SafeErrorResponseBuilder.sanitizeMessage(message);

        assertFalse(sanitized.contains("192.168.1.100"));
        assertTrue(sanitized.contains("[IP]"));
    }

    @Test
    @DisplayName("Long messages truncated")
    void testLongMessagesTruncated() {
        String longMessage = "x".repeat(500);
        String sanitized = SafeErrorResponseBuilder.sanitizeMessage(longMessage);

        assertTrue(sanitized.length() <= 204); // 200 + "..."
    }

    @Test
    @DisplayName("Safe message for different status codes")
    void testSafeMessages() {
        assertEquals("Invalid request format", SafeErrorResponseBuilder.getSafeMessage(400));
        assertEquals("Unauthorized access", SafeErrorResponseBuilder.getSafeMessage(401));
        assertEquals("Access forbidden", SafeErrorResponseBuilder.getSafeMessage(403));
        assertEquals("Resource not found", SafeErrorResponseBuilder.getSafeMessage(404));
        assertEquals("Rate limit exceeded", SafeErrorResponseBuilder.getSafeMessage(429));
        assertEquals("Internal server error", SafeErrorResponseBuilder.getSafeMessage(500));
        assertEquals("An error occurred", SafeErrorResponseBuilder.getSafeMessage(999));
    }

    @Test
    @DisplayName("Error response as JSON")
    void testToJson() {
        SafeErrorResponseBuilder.SafeErrorResponse response =
                SafeErrorResponseBuilder.rateLimitExceeded("client-1", "req-123");

        String json = response.toJson();
        assertTrue(json.startsWith("{"));
        assertTrue(json.contains("\"status\":429"));
        assertTrue(json.contains("\"requestId\":\"req-123\""));
    }

    @Test
    @DisplayName("Error response as XML")
    void testToXml() {
        SafeErrorResponseBuilder.SafeErrorResponse response =
                SafeErrorResponseBuilder.rateLimitExceeded("client-1", "req-123");

        String xml = response.toXml();
        assertTrue(xml.startsWith("<error>"));
        assertTrue(xml.contains("<status>429</status>"));
        assertTrue(xml.contains("<requestId>req-123</requestId>"));
    }

    @Test
    @DisplayName("JSON escaping for special characters")
    void testJsonEscaping() {
        SafeErrorResponseBuilder.SafeErrorResponse response =
                SafeErrorResponseBuilder.fromMessage(400, "Test\"quote", "req-123");

        String json = response.toJson();
        assertTrue(json.contains("\\\""));
    }

    @Test
    @DisplayName("XML escaping for special characters")
    void testXmlEscaping() {
        SafeErrorResponseBuilder.SafeErrorResponse response =
                SafeErrorResponseBuilder.fromMessage(400, "Test<tag>", "req-123");

        String xml = response.toXml();
        assertTrue(xml.contains("&lt;"));
        assertTrue(xml.contains("&gt;"));
    }

    @Test
    @DisplayName("Null parameters rejected")
    void testNullParametersRejected() {
        assertThrows(NullPointerException.class,
                () -> SafeErrorResponseBuilder.fromException(400, null, "req-123"));

        assertThrows(NullPointerException.class,
                () -> SafeErrorResponseBuilder.fromMessage(400, null, "req-123"));

        assertThrows(NullPointerException.class,
                () -> SafeErrorResponseBuilder.rateLimitExceeded(null, "req-123"));

        assertThrows(NullPointerException.class,
                () -> SafeErrorResponseBuilder.sanitizeMessage(null));
    }

    @Test
    @DisplayName("Invalid status code rejected")
    void testInvalidStatusCode() {
        assertThrows(IllegalArgumentException.class,
                () -> SafeErrorResponseBuilder.fromMessage(99, "message", "req-123"));

        assertThrows(IllegalArgumentException.class,
                () -> SafeErrorResponseBuilder.fromMessage(600, "message", "req-123"));
    }

    @Test
    @DisplayName("Secure logging")
    void testSecureLogging() {
        // Should not throw exception
        SafeErrorResponseBuilder.logSecurely("req-123",
                new RuntimeException("Detailed error"),
                "Additional context");
    }
}
