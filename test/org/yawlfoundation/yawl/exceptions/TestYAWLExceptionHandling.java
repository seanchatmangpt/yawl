package org.yawlfoundation.yawl.exceptions;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for YAWL exception class behavior.
 * Verifies exception construction, messages, causes, and hierarchy.
 *
 * Chicago TDD - tests real exception class behavior.
 *
 * @author YAWL Test Team
 * Date: 2026-02-16
 */
public class TestYAWLExceptionHandling {

    /**
     * Test YPersistenceException construction and message
     */
    @Test
    void testYPersistenceException_message() {
        YPersistenceException pe = new YPersistenceException("Database connection failed");
        assertNotNull(pe.getMessage(), "Exception should have message");
        assertEquals("Database connection failed", pe.getMessage(),
                     "Message should match constructor argument");
    }

    @Test
    void testYPersistenceException_withCause() {
        Exception cause = new Exception("Root cause: timeout");
        YPersistenceException pe = new YPersistenceException("Operation failed", cause);
        assertNotNull(pe.getCause(), "Exception should have cause");
        assertEquals(cause, pe.getCause(), "Cause should be preserved");
        assertEquals("Operation failed", pe.getMessage(), "Message should match");
    }

    @Test
    void testYPersistenceException_isException() {
        YPersistenceException pe = new YPersistenceException("error");
        assertTrue(pe instanceof Exception, "Should be an Exception");
    }

    /**
     * Test YStateException construction
     */
    @Test
    void testYStateException_message() {
        YStateException se = new YStateException("Invalid state transition");
        assertNotNull(se.getMessage(), "State exception should have message");
        assertEquals("Invalid state transition", se.getMessage(),
                     "Message should match");
    }

    @Test
    void testYStateException_isException() {
        YStateException se = new YStateException("error");
        assertTrue(se instanceof Exception, "Should be an Exception");
    }

    /**
     * Test YConnectivityException construction
     */
    @Test
    void testYConnectivityException_message() {
        String msg = "Connection refused";
        YConnectivityException ce = new YConnectivityException(msg);
        assertNotNull(ce.getMessage(), "Exception should have message");
        assertEquals(msg, ce.getMessage(), "Message should match");
    }

    @Test
    void testYConnectivityException_isException() {
        YConnectivityException ce = new YConnectivityException("error");
        assertTrue(ce instanceof Exception, "Should be an Exception");
    }

    /**
     * Test YSyntaxException construction
     */
    @Test
    void testYSyntaxException_message() {
        YSyntaxException se = new YSyntaxException("Invalid syntax");
        assertNotNull(se.getMessage(), "Exception should have message");
        assertEquals("Invalid syntax", se.getMessage(), "Message should match");
    }

    /**
     * Test YQueryException construction
     */
    @Test
    void testYQueryException_message() {
        YQueryException qe = new YQueryException("Invalid query");
        assertNotNull(qe.getMessage(), "Exception should have message");
        assertEquals("Invalid query", qe.getMessage(), "Message should match");
    }

    /**
     * Test exception hierarchy
     */
    @Test
    void testExceptionHierarchy() {
        YPersistenceException pe = new YPersistenceException("error");
        assertTrue(pe instanceof Exception, "YPersistenceException is Exception");

        YStateException se = new YStateException("error");
        assertTrue(se instanceof Exception, "YStateException is Exception");

        YConnectivityException ce = new YConnectivityException("error");
        assertTrue(ce instanceof Exception, "YConnectivityException is Exception");
    }
}
