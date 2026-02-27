package org.yawlfoundation.yawl.exceptions;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Test cases for enhanced YAWLException features (context and troubleshooting).
 *
 * @author YAWL Development Team
 * @since 5.2
 */
@Tag("unit")
class TestYAWLExceptionEnhancements {

    @Test
    void testWithContext() {
        YAWLException exception = new YAWLException("Test error");
        exception.withContext("caseID", "case-123")
                 .withContext("taskID", "task-456");

        Map<String, String> context = exception.getContext();

        assertEquals("case-123", context.get("caseID"));
        assertEquals("task-456", context.get("taskID"));
        assertEquals(2, context.size());
    }

    @Test
    void testContextIsImmutable() {
        YAWLException exception = new YAWLException("Test error");
        exception.withContext("key", "value");

        Map<String, String> context = exception.getContext();
        context.put("newkey", "newvalue");

        Map<String, String> retrievedContext = exception.getContext();

        assertFalse(retrievedContext.containsKey("newkey"),
                   "Context should not contain externally added keys");
        assertEquals(1, retrievedContext.size());
    }

    @Test
    void testWithTroubleshootingGuide() {
        YAWLException exception = new YAWLException("Database connection failed");
        exception.withTroubleshootingGuide("Check database URL and credentials");

        String guide = exception.getTroubleshootingGuide();

        assertEquals("Check database URL and credentials", guide);
    }

    @Test
    void testMethodChaining() {
        YAWLException exception = new YAWLException("Workflow error")
            .withContext("spec", "spec-1")
            .withContext("case", "case-1")
            .withTroubleshootingGuide("Review workflow definition");

        assertNotNull(exception.getContext());
        assertNotNull(exception.getTroubleshootingGuide());
        assertEquals(2, exception.getContext().size());
    }

    @Test
    void testToStringIncludesContext() {
        YAWLException exception = new YAWLException("Test error");
        exception.withContext("caseID", "case-123")
                 .withTroubleshootingGuide("Check logs");

        String str = exception.toString();

        assertTrue(str.contains("Context:"),
                  "toString should include context");
        assertTrue(str.contains("caseID"),
                  "toString should include caseID");
        assertTrue(str.contains("Troubleshooting:"),
                  "toString should include troubleshooting");
    }

    @Test
    void testToStringWithoutEnhancements() {
        YAWLException exception = new YAWLException("Simple error");

        String str = exception.toString();

        assertFalse(str.contains("Context:"),
                   "toString should not include context label");
        assertFalse(str.contains("Troubleshooting:"),
                   "toString should not include troubleshooting label");
    }

    @Test
    void testEmptyContext() {
        YAWLException exception = new YAWLException("Test error");

        Map<String, String> context = exception.getContext();

        assertNotNull(context, "Context should not be null");
        assertTrue(context.isEmpty(), "Context should be empty");
    }

    @Test
    void testNullTroubleshootingGuide() {
        YAWLException exception = new YAWLException("Test error");

        String guide = exception.getTroubleshootingGuide();

        assertNull(guide, "Troubleshooting guide should be null by default");
    }

    @Test
    void testExceptionWithCauseAndEnhancements() {
        Exception cause = new RuntimeException("Original error");
        YAWLException exception = new YAWLException("Wrapper error", cause);
        exception.withContext("location", "module-A")
                 .withTroubleshootingGuide("Check module A configuration");

        assertEquals("Wrapper error", exception.getMessage());
        assertEquals(cause, exception.getCause());
        assertEquals("module-A", exception.getContext().get("location"));
        assertEquals("Check module A configuration",
                    exception.getTroubleshootingGuide());
    }

    @Test
    void testUpdateContextMultipleTimes() {
        YAWLException exception = new YAWLException("Test error");

        exception.withContext("key1", "value1");
        assertEquals(1, exception.getContext().size());

        exception.withContext("key2", "value2");
        assertEquals(2, exception.getContext().size());

        exception.withContext("key1", "updated-value");
        assertEquals("updated-value", exception.getContext().get("key1"));
        assertEquals(2, exception.getContext().size());
    }

    @Test
    void testUpdateTroubleshootingGuide() {
        YAWLException exception = new YAWLException("Test error");

        exception.withTroubleshootingGuide("First guide");
        assertEquals("First guide", exception.getTroubleshootingGuide());

        exception.withTroubleshootingGuide("Updated guide");
        assertEquals("Updated guide", exception.getTroubleshootingGuide());
    }
}
