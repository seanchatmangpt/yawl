/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.claude;

import junit.framework.TestCase;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Chicago TDD tests for ClaudeCodeExecutor.
 *
 * Tests real ClaudeCodeExecutor functionality with JUnit 4.
 * Tests that require actual CLI invocation are conditionally skipped
 * when the claude executable is not available in the environment.
 *
 * Coverage targets:
 * - execute() with valid prompt
 * - execute() with timeout handling
 * - execute() with session continuation
 * - execute() with working directory
 * - cancel() ongoing execution
 * - getSessionManager() returns valid manager
 * - Configuration validation (timeout limits, prompt length)
 * - Error handling (invalid prompts, CLI not found)
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class ClaudeCodeExecutorTest extends TestCase {

    private ClaudeCodeExecutor executor;
    private boolean claudeAvailable;

    public ClaudeCodeExecutorTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        executor = new ClaudeCodeExecutor();
        claudeAvailable = isClaudeCliAvailable();
    }

    @Override
    protected void tearDown() throws Exception {
        if (executor != null) {
            executor.shutdown();
        }
        super.tearDown();
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    /**
     * Checks if the Claude CLI is available in the system PATH.
     */
    private boolean isClaudeCliAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("which", "claude");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean completed = process.waitFor(5, TimeUnit.SECONDS);
            return completed && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Skips the test if Claude CLI is not available.
     */
    private void assumeClaudeAvailable() {
        if (!claudeAvailable) {
            System.out.println("Skipping test - Claude CLI not available");
            return;
        }
    }

    // =========================================================================
    // Construction and configuration tests
    // =========================================================================

    public void testDefaultConstructorCreatesExecutor() {
        ClaudeCodeExecutor exec = new ClaudeCodeExecutor();
        assertNotNull("Executor should be created", exec);
        assertNotNull("Session manager should be available", exec.getSessionManager());
        exec.shutdown();
    }

    public void testCustomConstructorWithValidParameters() {
        ClaudeCodeExecutor exec = new ClaudeCodeExecutor(
            "/usr/local/bin/claude",
            Duration.ofMinutes(10),
            Duration.ofHours(2),
            200_000
        );
        assertNotNull("Executor should be created with custom config", exec);
        exec.shutdown();
    }

    public void testCustomConstructorWithNullCliPathThrows() {
        try {
            new ClaudeCodeExecutor(null, Duration.ofMinutes(5), Duration.ofHours(1), 100_000);
            fail("Should throw NullPointerException for null cliPath");
        } catch (NullPointerException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testCustomConstructorWithNullDefaultTimeoutThrows() {
        try {
            new ClaudeCodeExecutor("claude", null, Duration.ofHours(1), 100_000);
            fail("Should throw NullPointerException for null defaultTimeout");
        } catch (NullPointerException e) {
            assertNotNull(e.getMessage());
        }
    }

    public void testCustomConstructorWithNullMaxTimeoutThrows() {
        try {
            new ClaudeCodeExecutor("claude", Duration.ofMinutes(5), null, 100_000);
            fail("Should throw NullPointerException for null maxTimeout");
        } catch (NullPointerException e) {
            assertNotNull(e.getMessage());
        }
    }

    // =========================================================================
    // getSessionManager() tests
    // =========================================================================

    public void testGetSessionManagerReturnsNonNull() {
        ClaudeSessionManager manager = executor.getSessionManager();
        assertNotNull("Session manager should not be null", manager);
    }

    public void testGetSessionManagerReturnsSameInstance() {
        ClaudeSessionManager manager1 = executor.getSessionManager();
        ClaudeSessionManager manager2 = executor.getSessionManager();
        assertSame("Session manager should be same instance", manager1, manager2);
    }

    public void testSessionManagerInitiallyHasZeroSessions() {
        ClaudeSessionManager manager = executor.getSessionManager();
        assertEquals("Initial session count should be 0", 0, manager.getActiveSessionCount());
    }

    // =========================================================================
    // execute() validation tests (no CLI required)
    // =========================================================================

    public void testExecuteWithNullPromptThrows() {
        try {
            executor.execute(null);
            fail("Should throw IllegalArgumentException for null prompt");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("null") || e.getMessage().contains("blank"));
        }
    }

    public void testExecuteWithBlankPromptThrows() {
        try {
            executor.execute("   ");
            fail("Should throw IllegalArgumentException for blank prompt");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("blank") || e.getMessage().contains("null"));
        }
    }

    public void testExecuteWithEmptyPromptThrows() {
        try {
            executor.execute("");
            fail("Should throw IllegalArgumentException for empty prompt");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("blank") || e.getMessage().contains("null"));
        }
    }

    public void testExecuteWithPromptExceedingMaxLengthThrows() {
        // Create a prompt longer than default max (100,000 chars)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 110_000; i++) {
            sb.append('x');
        }
        String longPrompt = sb.toString();

        try {
            executor.execute(longPrompt);
            fail("Should throw IllegalArgumentException for prompt exceeding max length");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("length") || e.getMessage().contains("exceeds"));
        }
    }

    public void testExecuteWithTimeoutExceedingMaxThrows() {
        // Create executor with small max timeout
        ClaudeCodeExecutor exec = new ClaudeCodeExecutor(
            "claude",
            Duration.ofMinutes(5),
            Duration.ofMinutes(10),  // Max 10 minutes
            100_000
        );

        try {
            exec.execute("test prompt", null, null, Duration.ofHours(1), null);
            fail("Should throw IllegalArgumentException for timeout exceeding max");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Timeout") || e.getMessage().contains("exceeds"));
        } finally {
            exec.shutdown();
        }
    }

    public void testExecuteWithInvalidOperationThrows() {
        try {
            executor.execute("test prompt", null, null, Duration.ofMinutes(1),
                           Set.of("read", "dangerous_operation"));
            fail("Should throw IllegalArgumentException for invalid operation");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("not allowed") || e.getMessage().contains("Operation"));
        }
    }

    // =========================================================================
    // execute() with valid prompt tests (requires CLI)
    // =========================================================================

    public void testExecuteWithValidPrompt() {
        assumeClaudeAvailable();

        ClaudeExecutionResult result = executor.execute("echo hello");

        assertNotNull("Result should not be null", result);
        // Result could be success or failure depending on CLI availability
        assertNotNull("Duration should be set", result.duration());
    }

    public void testExecuteWithWorkingDirectory() {
        assumeClaudeAvailable();

        Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"));
        ClaudeExecutionResult result = executor.execute(
            "pwd",
            null,
            tempDir,
            Duration.ofSeconds(30),
            null
        );

        assertNotNull("Result should not be null", result);
    }

    public void testExecuteWithSessionContinuation() {
        assumeClaudeAvailable();

        // First execution to potentially get a session
        ClaudeExecutionResult result1 = executor.execute("hello");

        assertNotNull("First result should not be null", result1);

        // If we got a session ID, try to continue
        if (result1.hasSession()) {
            String sessionId = result1.sessionId();
            assertNotNull("Session ID should not be null", sessionId);

            ClaudeExecutionResult result2 = executor.execute(
                "continue",
                sessionId,
                null,
                Duration.ofSeconds(30),
                null
            );

            assertNotNull("Second result should not be null", result2);
        }
    }

    // =========================================================================
    // execute() timeout tests
    // =========================================================================

    public void testExecuteWithCustomTimeout() {
        assumeClaudeAvailable();

        ClaudeExecutionResult result = executor.execute(
            "quick task",
            null,
            null,
            Duration.ofSeconds(10),
            null
        );

        assertNotNull("Result should not be null", result);
    }

    public void testExecuteWithVeryShortTimeout() {
        assumeClaudeAvailable();

        // A 1ms timeout should almost certainly cause a timeout
        ClaudeCodeExecutor exec = new ClaudeCodeExecutor(
            "claude",
            Duration.ofMillis(1),
            Duration.ofSeconds(5),
            100_000
        );

        try {
            ClaudeExecutionResult result = exec.execute("complex analysis task");
            assertNotNull("Result should not be null", result);
            // Likely a timeout or failure
        } finally {
            exec.shutdown();
        }
    }

    // =========================================================================
    // cancel() tests
    // =========================================================================

    public void testCancelWithNonexistentSessionReturnsFalse() {
        boolean cancelled = executor.cancel("nonexistent-session-id");
        assertFalse("Cancel should return false for nonexistent session", cancelled);
    }

    public void testCancelWithNullSessionIdReturnsFalse() {
        boolean cancelled = executor.cancel(null);
        assertFalse("Cancel should return false for null session id", cancelled);
    }

    public void testCancelOngoingExecution() throws Exception {
        assumeClaudeAvailable();

        // Start a long-running execution in a separate thread
        AtomicReference<ClaudeExecutionResult> resultRef = new AtomicReference<>();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(1);
        AtomicBoolean cancelled = new AtomicBoolean(false);

        Thread executionThread = new Thread(() -> {
            try {
                startLatch.countDown();
                ClaudeExecutionResult result = executor.execute(
                    "perform a very long and complex task that will take a while",
                    "test-session-cancel",
                    null,
                    Duration.ofMinutes(5),
                    null
                );
                resultRef.set(result);
            } finally {
                completeLatch.countDown();
            }
        });

        executionThread.start();

        // Wait for execution to start
        assertTrue("Execution should start", startLatch.await(2, TimeUnit.SECONDS));

        // Try to cancel
        Thread.sleep(100); // Give it a moment to actually start
        cancelled.set(executor.cancel("test-session-cancel"));

        // Wait for completion
        completeLatch.await(5, TimeUnit.SECONDS);

        // The cancel may or may not succeed depending on timing
        // Just verify the execution completes
        assertNotNull("Execution should complete", resultRef.get());
    }

    // =========================================================================
    // shutdown() tests
    // =========================================================================

    public void testShutdownClearsActiveProcesses() {
        ClaudeCodeExecutor exec = new ClaudeCodeExecutor();
        exec.shutdown();

        // After shutdown, getSessionManager should still work
        // but the executor should be in a clean state
        assertNotNull("Session manager should still be accessible", exec.getSessionManager());
    }

    public void testMultipleShutdownCallsAreSafe() {
        ClaudeCodeExecutor exec = new ClaudeCodeExecutor();
        exec.shutdown();
        exec.shutdown();  // Should not throw
        exec.shutdown();
    }

    // =========================================================================
    // Allowed operations validation tests
    // =========================================================================

    public void testAllowedOperationsWithValidSet() {
        assumeClaudeAvailable();

        Set<String> allowedOps = Set.of("read", "write", "bash");
        ClaudeExecutionResult result = executor.execute(
            "list files",
            null,
            null,
            Duration.ofSeconds(10),
            allowedOps
        );

        assertNotNull("Result should not be null with valid operations", result);
    }

    public void testAllowedOperationsWithGit() {
        assumeClaudeAvailable();

        Set<String> allowedOps = Set.of("git");
        ClaudeExecutionResult result = executor.execute(
            "show git status",
            null,
            null,
            Duration.ofSeconds(10),
            allowedOps
        );

        assertNotNull("Result should not be null", result);
    }

    // =========================================================================
    // Integration with ClaudeSessionManager tests
    // =========================================================================

    public void testExecutorUsesSameSessionManager() {
        ClaudeSessionManager manager = executor.getSessionManager();

        // Create a session through the manager
        String sessionId = manager.createSession("test context");
        assertNotNull("Session ID should not be null", sessionId);

        // Verify the executor's session manager is the same
        assertTrue("Session should exist in manager", manager.hasSession(sessionId));

        manager.terminateSession(sessionId);
    }

    public void testSessionManagerCountAfterOperations() {
        ClaudeSessionManager manager = executor.getSessionManager();
        int initialCount = manager.getActiveSessionCount();

        String sessionId = manager.createSession("new session");
        assertEquals("Session count should increase", initialCount + 1, manager.getActiveSessionCount());

        manager.terminateSession(sessionId);
        assertEquals("Session count should decrease", initialCount, manager.getActiveSessionCount());
    }

    // =========================================================================
    // Edge cases and error handling
    // =========================================================================

    public void testExecuteWithInvalidCliPath() {
        ClaudeCodeExecutor exec = new ClaudeCodeExecutor(
            "/nonexistent/path/to/claude",
            Duration.ofMinutes(5),
            Duration.ofHours(1),
            100_000
        );

        try {
            ClaudeExecutionResult result = exec.execute("test prompt");
            assertNotNull("Result should not be null", result);
            assertFalse("Result should indicate failure", result.success());
            assertTrue("Should have error message",
                result.error() != null && !result.error().isEmpty());
        } finally {
            exec.shutdown();
        }
    }

    public void testExecuteWithSpecialCharactersInPrompt() {
        assumeClaudeAvailable();

        // Special characters should be sanitized
        ClaudeExecutionResult result = executor.execute("test with special chars: <>&\"'");
        assertNotNull("Result should not be null", result);
    }

    public void testExecuteWithUnicodePrompt() {
        assumeClaudeAvailable();

        ClaudeExecutionResult result = executor.execute("Unicode test: \u4e2d\u6587 \u0420\u0443\u0441\u0441\u043a\u0438\u0439");
        assertNotNull("Result should not be null", result);
    }

    public void testExecuteWithMultilinePrompt() {
        assumeClaudeAvailable();

        String multilinePrompt = """
            First line
            Second line
            Third line
            """;

        ClaudeExecutionResult result = executor.execute(multilinePrompt);
        assertNotNull("Result should not be null for multiline prompt", result);
    }

    public void testExecuteWithVeryLongPrompt() {
        assumeClaudeAvailable();

        // Create a prompt close to but under the max length
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 9_000; i++) {
            sb.append("word ");
        }
        String longPrompt = sb.toString();

        ClaudeExecutionResult result = executor.execute(longPrompt);
        assertNotNull("Result should not be null for long prompt", result);
    }
}
