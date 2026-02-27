package org.yawlfoundation.yawl.integration.spiffe;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for SpiffeException.
 * Tests exception construction, error codes, and helper methods.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
@Tag("unit")
class SpiffeExceptionTest {

    private static final String TEST_SPIFFE_ID = "spiffe://yawl.cloud/test";

    /**
     * Test: Constructor with message only.
     */
    @Test
    @DisplayName("Exception with message should store message correctly")
    void testConstructorWithMessage() {
        SpiffeException ex = new SpiffeException("Test error");

        assertEquals("Test error", ex.getMessage());
        assertNull(ex.getErrorCode());
        assertNull(ex.getSpiffeId());
    }

    /**
     * Test: Constructor with message and cause.
     */
    @Test
    @DisplayName("Exception with message and cause should store both")
    void testConstructorWithMessageAndCause() {
        Exception cause = new RuntimeException("Root cause");
        SpiffeException ex = new SpiffeException("Test error", cause);

        assertEquals("Test error", ex.getMessage());
        assertSame(cause, ex.getCause());
        assertNull(ex.getErrorCode());
    }

    /**
     * Test: Constructor with cause only.
     */
    @Test
    @DisplayName("Exception with cause only should store cause")
    void testConstructorWithCause() {
        Exception cause = new RuntimeException("Root cause");
        SpiffeException ex = new SpiffeException(cause);

        assertSame(cause, ex.getCause());
        assertSame(cause, ex.getCause());
    }

    /**
     * Test: Constructor with error code, message, and SPIFFE ID.
     */
    @Test
    @DisplayName("Exception with error code should store all fields")
    void testConstructorWithErrorCode() {
        SpiffeException ex = new SpiffeException(
            SpiffeException.ERR_SVID_INVALID,
            "SVID validation failed",
            TEST_SPIFFE_ID
        );

        assertEquals("SVID validation failed", ex.getMessage());
        assertEquals(SpiffeException.ERR_SVID_INVALID, ex.getErrorCode());
        assertEquals(TEST_SPIFFE_ID, ex.getSpiffeId());
    }

    /**
     * Test: Constructor with all parameters.
     */
    @Test
    @DisplayName("Exception with all parameters should store all fields")
    void testConstructorWithAllParams() {
        Exception cause = new RuntimeException("Root cause");
        SpiffeException ex = new SpiffeException(
            SpiffeException.ERR_AGENT_UNREACHABLE,
            "Agent unreachable",
            TEST_SPIFFE_ID,
            cause
        );

        assertEquals("Agent unreachable", ex.getMessage());
        assertEquals(SpiffeException.ERR_AGENT_UNREACHABLE, ex.getErrorCode());
        assertEquals(TEST_SPIFFE_ID, ex.getSpiffeId());
        assertSame(cause, ex.getCause());
    }

    /**
     * Test: isAgentUnreachable returns true for agent unreachable error.
     */
    @Test
    @DisplayName("isAgentUnreachable should return true for agent unreachable code")
    void testIsAgentUnreachable() {
        SpiffeException ex = new SpiffeException(
            SpiffeException.ERR_AGENT_UNREACHABLE,
            "Agent unreachable",
            TEST_SPIFFE_ID
        );

        assertTrue(ex.isAgentUnreachable());
        assertFalse(ex.isCertExpired());
    }

    /**
     * Test: isCertExpired returns true for expired certificate error.
     */
    @Test
    @DisplayName("isCertExpired should return true for expired cert code")
    void testIsCertExpired() {
        SpiffeException ex = new SpiffeException(
            SpiffeException.ERR_CERT_EXPIRED,
            "Certificate expired",
            TEST_SPIFFE_ID
        );

        assertTrue(ex.isCertExpired());
        assertFalse(ex.isAgentUnreachable());
    }

    /**
     * Test: Error code constants are defined.
     */
    @Test
    @DisplayName("Error code constants should be defined")
    void testErrorConstants() {
        assertEquals("SPIFFE_AGENT_UNREACHABLE", SpiffeException.ERR_AGENT_UNREACHABLE);
        assertEquals("SPIFFE_SVID_INVALID", SpiffeException.ERR_SVID_INVALID);
        assertEquals("SPIFFE_TRUST_DOMAIN_MISMATCH", SpiffeException.ERR_TRUST_DOMAIN);
        assertEquals("SPIFFE_CERT_EXPIRED", SpiffeException.ERR_CERT_EXPIRED);
        assertEquals("SPIFFE_JWT_INVALID", SpiffeException.ERR_JWT_INVALID);
    }

    /**
     * Test: toString contains relevant information.
     */
    @Test
    @DisplayName("toString should contain error code and message")
    void testToString() {
        SpiffeException ex = new SpiffeException(
            SpiffeException.ERR_SVID_INVALID,
            "Test error",
            TEST_SPIFFE_ID
        );

        String str = ex.toString();
        assertTrue(str.contains("SpiffeException"), "Should contain class name");
        assertTrue(str.contains(SpiffeException.ERR_SVID_INVALID), "Should contain error code");
        assertTrue(str.contains("Test error"), "Should contain message");
        assertTrue(str.contains(TEST_SPIFFE_ID), "Should contain SPIFFE ID");
    }

    /**
     * Test: toString with cause includes cause information.
     */
    @Test
    @DisplayName("toString with cause should include cause info")
    void testToStringWithCause() {
        Exception cause = new RuntimeException("Root cause");
        SpiffeException ex = new SpiffeException(
            SpiffeException.ERR_SVID_INVALID,
            "Test error",
            TEST_SPIFFE_ID,
            cause
        );

        String str = ex.toString();
        assertTrue(str.contains("RuntimeException"), "Should contain cause type");
        assertTrue(str.contains("Root cause"), "Should contain cause message");
    }

    /**
     * Test: toString without error code works correctly.
     */
    @Test
    @DisplayName("toString without error code should work")
    void testToStringWithoutErrorCode() {
        SpiffeException ex = new SpiffeException("Simple error");

        String str = ex.toString();
        assertTrue(str.contains("SpiffeException"), "Should contain class name");
        assertTrue(str.contains("Simple error"), "Should contain message");
        assertFalse(str.contains("["), "Should not contain error code bracket");
    }
}
