package org.yawlfoundation.yawl.exceptions;

import junit.framework.TestCase;

import java.util.Map;

/**
 * Test cases for enhanced YAWLException features (context and troubleshooting).
 *
 * @author YAWL Development Team
 * @since 5.2
 */
public class TestYAWLExceptionEnhancements extends TestCase {

    public TestYAWLExceptionEnhancements(String name) {
        super(name);
    }

    /**
     * Test adding context to an exception.
     */
    public void testWithContext() {
        YAWLException exception = new YAWLException("Test error");
        exception.withContext("caseID", "case-123")
                 .withContext("taskID", "task-456");

        Map<String, String> context = exception.getContext();

        assertEquals("case-123", context.get("caseID"));
        assertEquals("task-456", context.get("taskID"));
        assertEquals(2, context.size());
    }

    /**
     * Test that context is immutable when retrieved.
     */
    public void testContextIsImmutable() {
        YAWLException exception = new YAWLException("Test error");
        exception.withContext("key", "value");

        Map<String, String> context = exception.getContext();
        context.put("newkey", "newvalue");

        Map<String, String> retrievedContext = exception.getContext();

        assertFalse("Context should not contain externally added keys",
                   retrievedContext.containsKey("newkey"));
        assertEquals(1, retrievedContext.size());
    }

    /**
     * Test adding troubleshooting guide.
     */
    public void testWithTroubleshootingGuide() {
        YAWLException exception = new YAWLException("Database connection failed");
        exception.withTroubleshootingGuide("Check database URL and credentials");

        String guide = exception.getTroubleshootingGuide();

        assertEquals("Check database URL and credentials", guide);
    }

    /**
     * Test method chaining for context.
     */
    public void testMethodChaining() {
        YAWLException exception = new YAWLException("Workflow error")
            .withContext("spec", "spec-1")
            .withContext("case", "case-1")
            .withTroubleshootingGuide("Review workflow definition");

        assertNotNull(exception.getContext());
        assertNotNull(exception.getTroubleshootingGuide());
        assertEquals(2, exception.getContext().size());
    }

    /**
     * Test toString includes context and troubleshooting.
     */
    public void testToStringIncludesContext() {
        YAWLException exception = new YAWLException("Test error");
        exception.withContext("caseID", "case-123")
                 .withTroubleshootingGuide("Check logs");

        String str = exception.toString();

        assertTrue("toString should include context",
                  str.contains("Context:"));
        assertTrue("toString should include caseID",
                  str.contains("caseID"));
        assertTrue("toString should include troubleshooting",
                  str.contains("Troubleshooting:"));
    }

    /**
     * Test toString without context or troubleshooting.
     */
    public void testToStringWithoutEnhancements() {
        YAWLException exception = new YAWLException("Simple error");

        String str = exception.toString();

        assertFalse("toString should not include context label",
                   str.contains("Context:"));
        assertFalse("toString should not include troubleshooting label",
                   str.contains("Troubleshooting:"));
    }

    /**
     * Test empty context.
     */
    public void testEmptyContext() {
        YAWLException exception = new YAWLException("Test error");

        Map<String, String> context = exception.getContext();

        assertNotNull("Context should not be null", context);
        assertTrue("Context should be empty", context.isEmpty());
    }

    /**
     * Test null troubleshooting guide.
     */
    public void testNullTroubleshootingGuide() {
        YAWLException exception = new YAWLException("Test error");

        String guide = exception.getTroubleshootingGuide();

        assertNull("Troubleshooting guide should be null by default", guide);
    }

    /**
     * Test exception with cause and enhancements.
     */
    public void testExceptionWithCauseAndEnhancements() {
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

    /**
     * Test updating context multiple times.
     */
    public void testUpdateContextMultipleTimes() {
        YAWLException exception = new YAWLException("Test error");

        exception.withContext("key1", "value1");
        assertEquals(1, exception.getContext().size());

        exception.withContext("key2", "value2");
        assertEquals(2, exception.getContext().size());

        exception.withContext("key1", "updated-value");
        assertEquals("updated-value", exception.getContext().get("key1"));
        assertEquals(2, exception.getContext().size());
    }

    /**
     * Test troubleshooting guide can be updated.
     */
    public void testUpdateTroubleshootingGuide() {
        YAWLException exception = new YAWLException("Test error");

        exception.withTroubleshootingGuide("First guide");
        assertEquals("First guide", exception.getTroubleshootingGuide());

        exception.withTroubleshootingGuide("Updated guide");
        assertEquals("Updated guide", exception.getTroubleshootingGuide());
    }
}
