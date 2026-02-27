package org.yawlfoundation.yawl.integration.a2a.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple verification test to ensure our test setup works
 */
public class SimpleVerificationTest {

    @Test
    @DisplayName("testSetupVerification_simpleAssertion")
    void testSetupVerification_simpleAssertion() {
        // Simple test to verify the test environment works
        assertEquals(2 + 2, 4);
        assertTrue(true);
        assertNotNull("Hello, World!");
    }
}