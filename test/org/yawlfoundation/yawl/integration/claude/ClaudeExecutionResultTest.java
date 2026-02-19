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

import java.time.Duration;
import java.util.Optional;

/**
 * Chicago TDD tests for ClaudeExecutionResult record.
 *
 * Tests the factory methods, accessor methods, and utility methods
 * of the ClaudeExecutionResult record class.
 *
 * Coverage targets:
 * - success() factory method
 * - failure() factory methods
 * - timeout() factory method
 * - hasSession() detection
 * - durationMs() calculation
 * - output() and error() null-safe accessors
 * - Optional-based accessors (outputOpt, errorOpt, sessionIdOpt)
 * - toString() representation
 * - Record component accessors
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public class ClaudeExecutionResultTest extends TestCase {

    public ClaudeExecutionResultTest(String name) {
        super(name);
    }

    // =========================================================================
    // success() factory method tests
    // =========================================================================

    public void testSuccessFactoryMethodWithOutput() {
        Duration duration = Duration.ofMillis(1500);
        ClaudeExecutionResult result = ClaudeExecutionResult.success("Test output", duration);

        assertTrue("Result should be successful", result.success());
        assertEquals("Output should match", "Test output", result.output());
        assertEquals("Error should be empty", "", result.error());
        assertEquals("Exit code should be 0", 0, result.exitCode());
        assertEquals("Duration should match", duration, result.duration());
        assertNull("Session ID should be null", result.sessionId());
        assertFalse("Should not have session", result.hasSession());
    }

    public void testSuccessFactoryMethodWithSessionId() {
        Duration duration = Duration.ofSeconds(5);
        String sessionId = "session-12345";

        ClaudeExecutionResult result = ClaudeExecutionResult.success("Output", duration, sessionId);

        assertTrue("Result should be successful", result.success());
        assertEquals("Session ID should match", sessionId, result.sessionId());
        assertTrue("Should have session", result.hasSession());
    }

    public void testSuccessFactoryMethodWithEmptyOutput() {
        ClaudeExecutionResult result = ClaudeExecutionResult.success("", Duration.ZERO);

        assertTrue("Result should be successful", result.success());
        assertEquals("Output should be empty string", "", result.output());
    }

    public void testSuccessFactoryMethodWithNullOutput() {
        ClaudeExecutionResult result = ClaudeExecutionResult.success(null, Duration.ofMillis(100));

        assertTrue("Result should be successful", result.success());
        assertEquals("Null output should become empty string", "", result.output());
    }

    public void testSuccessFactoryMethodWithNullSessionId() {
        ClaudeExecutionResult result = ClaudeExecutionResult.success("Output", Duration.ZERO, null);

        assertTrue("Result should be successful", result.success());
        assertNull("Session ID should be null", result.sessionId());
        assertFalse("Should not have session with null sessionId", result.hasSession());
    }

    public void testSuccessFactoryMethodWithEmptySessionId() {
        ClaudeExecutionResult result = ClaudeExecutionResult.success("Output", Duration.ZERO, "");

        assertTrue("Result should be successful", result.success());
        assertEquals("Empty session ID should be preserved", "", result.sessionId());
        assertFalse("Should not have session with empty sessionId", result.hasSession());
    }

    // =========================================================================
    // failure() factory method tests
    // =========================================================================

    public void testFailureFactoryMethodWithErrorAndExitCode() {
        ClaudeExecutionResult result = ClaudeExecutionResult.failure("Something went wrong", 1);

        assertFalse("Result should not be successful", result.success());
        assertEquals("Output should be empty", "", result.output());
        assertEquals("Error should match", "Something went wrong", result.error());
        assertEquals("Exit code should be 1", 1, result.exitCode());
        assertEquals("Duration should be zero", Duration.ZERO, result.duration());
        assertNull("Session ID should be null", result.sessionId());
    }

    public void testFailureFactoryMethodWithOutputErrorAndExitCode() {
        ClaudeExecutionResult result = ClaudeExecutionResult.failure(
            "Partial output",
            "Error message",
            2
        );

        assertFalse("Result should not be successful", result.success());
        assertEquals("Output should match", "Partial output", result.output());
        assertEquals("Error should match", "Error message", result.error());
        assertEquals("Exit code should be 2", 2, result.exitCode());
    }

    public void testFailureFactoryMethodWithNegativeExitCode() {
        ClaudeExecutionResult result = ClaudeExecutionResult.failure("Process killed", -1);

        assertFalse("Result should not be successful", result.success());
        assertEquals("Exit code should be -1", -1, result.exitCode());
    }

    public void testFailureFactoryMethodWithNullError() {
        ClaudeExecutionResult result = ClaudeExecutionResult.failure(null, 1);

        assertFalse("Result should not be successful", result.success());
        assertEquals("Null error should become empty string", "", result.error());
    }

    public void testFailureFactoryMethodWithNullOutputAndError() {
        ClaudeExecutionResult result = ClaudeExecutionResult.failure(null, null, 1);

        assertFalse("Result should not be successful", result.success());
        assertEquals("Null output should become empty string", "", result.output());
        assertEquals("Null error should become empty string", "", result.error());
    }

    // =========================================================================
    // timeout() factory method tests
    // =========================================================================

    public void testTimeoutFactoryMethod() {
        Duration timeout = Duration.ofSeconds(30);
        ClaudeExecutionResult result = ClaudeExecutionResult.timeout(timeout);

        assertFalse("Result should not be successful", result.success());
        assertEquals("Output should be empty", "", result.output());
        assertTrue("Error should mention timeout", result.error().contains("timed out"));
        assertTrue("Error should include timeout value", result.error().contains("30"));
        assertEquals("Exit code should be 124 (standard timeout)", 124, result.exitCode());
        assertEquals("Duration should match timeout", timeout, result.duration());
        assertNull("Session ID should be null", result.sessionId());
    }

    public void testTimeoutFactoryMethodWithMinuteTimeout() {
        Duration timeout = Duration.ofMinutes(5);
        ClaudeExecutionResult result = ClaudeExecutionResult.timeout(timeout);

        assertFalse("Result should not be successful", result.success());
        assertEquals("Exit code should be 124", 124, result.exitCode());
        assertTrue("Error should include seconds", result.error().contains("300"));
    }

    public void testTimeoutFactoryMethodWithZeroTimeout() {
        ClaudeExecutionResult result = ClaudeExecutionResult.timeout(Duration.ZERO);

        assertFalse("Result should not be successful", result.success());
        assertEquals("Exit code should be 124", 124, result.exitCode());
        assertTrue("Error should mention 0 seconds", result.error().contains("0"));
    }

    // =========================================================================
    // hasSession() tests
    // =========================================================================

    public void testHasSessionWithValidSessionId() {
        ClaudeExecutionResult result = new ClaudeExecutionResult(
            true, "output", "", 0, Duration.ZERO, "session-abc123"
        );

        assertTrue("Should have session with valid sessionId", result.hasSession());
    }

    public void testHasSessionWithNullSessionId() {
        ClaudeExecutionResult result = new ClaudeExecutionResult(
            true, "output", "", 0, Duration.ZERO, null
        );

        assertFalse("Should not have session with null sessionId", result.hasSession());
    }

    public void testHasSessionWithEmptySessionId() {
        ClaudeExecutionResult result = new ClaudeExecutionResult(
            true, "output", "", 0, Duration.ZERO, ""
        );

        assertFalse("Should not have session with empty sessionId", result.hasSession());
    }

    public void testHasSessionWithWhitespaceSessionId() {
        ClaudeExecutionResult result = new ClaudeExecutionResult(
            true, "output", "", 0, Duration.ZERO, "   "
        );

        // Whitespace-only sessionId is not empty string, so hasSession returns true
        assertTrue("Whitespace sessionId is still present", result.hasSession());
    }

    // =========================================================================
    // durationMs() tests
    // =========================================================================

    public void testDurationMsWithOneSecond() {
        ClaudeExecutionResult result = ClaudeExecutionResult.success(
            "output",
            Duration.ofSeconds(1)
        );

        assertEquals("Duration in ms should be 1000", 1000L, result.durationMs());
    }

    public void testDurationMsWithMilliseconds() {
        ClaudeExecutionResult result = ClaudeExecutionResult.success(
            "output",
            Duration.ofMillis(500)
        );

        assertEquals("Duration in ms should be 500", 500L, result.durationMs());
    }

    public void testDurationMsWithZeroDuration() {
        ClaudeExecutionResult result = ClaudeExecutionResult.success("output", Duration.ZERO);

        assertEquals("Duration in ms should be 0", 0L, result.durationMs());
    }

    public void testDurationMsWithNullDuration() {
        ClaudeExecutionResult result = new ClaudeExecutionResult(
            true, "output", "", 0, null, null
        );

        assertEquals("Null duration should return 0 ms", 0L, result.durationMs());
    }

    public void testDurationMsWithLargeDuration() {
        ClaudeExecutionResult result = ClaudeExecutionResult.success(
            "output",
            Duration.ofHours(1)
        );

        assertEquals("Duration in ms should be 3,600,000", 3_600_000L, result.durationMs());
    }

    // =========================================================================
    // output() and error() null-safe accessor tests
    // =========================================================================

    public void testOutputWithNonNullOutput() {
        ClaudeExecutionResult result = ClaudeExecutionResult.success("test output", Duration.ZERO);

        assertEquals("Output should match", "test output", result.output());
    }

    public void testOutputWithNullOutput() {
        ClaudeExecutionResult result = new ClaudeExecutionResult(
            true, null, "", 0, Duration.ZERO, null
        );

        assertEquals("Null output should return empty string", "", result.output());
    }

    public void testErrorWithNonNullError() {
        ClaudeExecutionResult result = ClaudeExecutionResult.failure("test error", 1);

        assertEquals("Error should match", "test error", result.error());
    }

    public void testErrorWithNullError() {
        ClaudeExecutionResult result = new ClaudeExecutionResult(
            false, "", null, 1, Duration.ZERO, null
        );

        assertEquals("Null error should return empty string", "", result.error());
    }

    // =========================================================================
    // Optional accessor tests (outputOpt, errorOpt, sessionIdOpt)
    // =========================================================================

    public void testOutputOptWithNonEmptyOutput() {
        ClaudeExecutionResult result = ClaudeExecutionResult.success("output", Duration.ZERO);

        Optional<String> opt = result.outputOpt();
        assertTrue("Optional should be present", opt.isPresent());
        assertEquals("Optional value should match", "output", opt.get());
    }

    public void testOutputOptWithEmptyOutput() {
        ClaudeExecutionResult result = ClaudeExecutionResult.success("", Duration.ZERO);

        Optional<String> opt = result.outputOpt();
        assertFalse("Optional should be empty for empty output", opt.isPresent());
    }

    public void testOutputOptWithNullOutput() {
        ClaudeExecutionResult result = new ClaudeExecutionResult(
            true, null, "", 0, Duration.ZERO, null
        );

        Optional<String> opt = result.outputOpt();
        assertFalse("Optional should be empty for null output", opt.isPresent());
    }

    public void testErrorOptWithNonEmptyError() {
        ClaudeExecutionResult result = ClaudeExecutionResult.failure("error", 1);

        Optional<String> opt = result.errorOpt();
        assertTrue("Optional should be present", opt.isPresent());
        assertEquals("Optional value should match", "error", opt.get());
    }

    public void testErrorOptWithEmptyError() {
        ClaudeExecutionResult result = ClaudeExecutionResult.success("output", Duration.ZERO);

        Optional<String> opt = result.errorOpt();
        assertFalse("Optional should be empty for empty error", opt.isPresent());
    }

    public void testSessionIdOptWithValidSession() {
        ClaudeExecutionResult result = ClaudeExecutionResult.success(
            "output", Duration.ZERO, "session-123"
        );

        Optional<String> opt = result.sessionIdOpt();
        assertTrue("Optional should be present", opt.isPresent());
        assertEquals("Optional value should match", "session-123", opt.get());
    }

    public void testSessionIdOptWithNullSession() {
        ClaudeExecutionResult result = ClaudeExecutionResult.success("output", Duration.ZERO);

        Optional<String> opt = result.sessionIdOpt();
        assertFalse("Optional should be empty for null session", opt.isPresent());
    }

    public void testSessionIdOptWithEmptySession() {
        ClaudeExecutionResult result = ClaudeExecutionResult.success("output", Duration.ZERO, "");

        Optional<String> opt = result.sessionIdOpt();
        assertFalse("Optional should be empty for empty session", opt.isPresent());
    }

    // =========================================================================
    // toString() tests
    // =========================================================================

    public void testToStringContainsSuccess() {
        ClaudeExecutionResult result = ClaudeExecutionResult.success("output", Duration.ZERO);

        String str = result.toString();
        assertTrue("toString should contain 'success=true'", str.contains("success=true"));
    }

    public void testToStringContainsFailure() {
        ClaudeExecutionResult result = ClaudeExecutionResult.failure("error", 1);

        String str = result.toString();
        assertTrue("toString should contain 'success=false'", str.contains("success=false"));
        assertTrue("toString should contain exitCode", str.contains("exitCode=1"));
    }

    public void testToStringContainsDuration() {
        ClaudeExecutionResult result = ClaudeExecutionResult.success("output", Duration.ofMillis(500));

        String str = result.toString();
        assertTrue("toString should contain duration", str.contains("duration="));
        assertTrue("toString should contain ms", str.contains("ms"));
    }

    public void testToStringContainsSessionId() {
        ClaudeExecutionResult result = ClaudeExecutionResult.success(
            "output", Duration.ZERO, "session-xyz"
        );

        String str = result.toString();
        assertTrue("toString should contain sessionId", str.contains("sessionId="));
        assertTrue("toString should contain session value", str.contains("session-xyz"));
    }

    public void testToStringDoesNotContainSessionIdWhenNull() {
        ClaudeExecutionResult result = ClaudeExecutionResult.success("output", Duration.ZERO);

        String str = result.toString();
        assertFalse("toString should not contain sessionId when null", str.contains("sessionId="));
    }

    public void testToStringContainsOutputLength() {
        ClaudeExecutionResult result = ClaudeExecutionResult.success("1234567890", Duration.ZERO);

        String str = result.toString();
        assertTrue("toString should contain outputLength", str.contains("outputLength=10"));
    }

    public void testToStringContainsErrorLength() {
        ClaudeExecutionResult result = ClaudeExecutionResult.failure("error123", 1);

        String str = result.toString();
        assertTrue("toString should contain errorLength", str.contains("errorLength=7"));
    }

    public void testToStringStartsWithClassName() {
        ClaudeExecutionResult result = ClaudeExecutionResult.success("output", Duration.ZERO);

        String str = result.toString();
        assertTrue("toString should start with ClaudeExecutionResult{",
            str.startsWith("ClaudeExecutionResult{"));
        assertTrue("toString should end with }", str.endsWith("}"));
    }

    // =========================================================================
    // Record component tests (direct field access)
    // =========================================================================

    public void testSuccessComponentAccessor() {
        ClaudeExecutionResult result = ClaudeExecutionResult.success("output", Duration.ZERO);
        assertTrue("success() should return true", result.success());
    }

    public void testOutputComponentAccessor() {
        ClaudeExecutionResult result = ClaudeExecutionResult.success("test output", Duration.ZERO);
        assertEquals("output() should return test output", "test output", result.output());
    }

    public void testErrorComponentAccessor() {
        ClaudeExecutionResult result = ClaudeExecutionResult.failure("test error", 1);
        assertEquals("error() should return test error", "test error", result.error());
    }

    public void testExitCodeComponentAccessor() {
        ClaudeExecutionResult result = ClaudeExecutionResult.failure("error", 42);
        assertEquals("exitCode() should return 42", 42, result.exitCode());
    }

    public void testDurationComponentAccessor() {
        Duration duration = Duration.ofSeconds(30);
        ClaudeExecutionResult result = ClaudeExecutionResult.success("output", duration);
        assertEquals("duration() should return same duration", duration, result.duration());
    }

    public void testSessionIdComponentAccessor() {
        String sessionId = "test-session-id";
        ClaudeExecutionResult result = ClaudeExecutionResult.success("output", Duration.ZERO, sessionId);
        assertEquals("sessionId() should return same sessionId", sessionId, result.sessionId());
    }

    // =========================================================================
    // Record equality tests
    // =========================================================================

    public void testEqualsWithIdenticalResults() {
        ClaudeExecutionResult result1 = ClaudeExecutionResult.success("output", Duration.ofMillis(100), "session");
        ClaudeExecutionResult result2 = ClaudeExecutionResult.success("output", Duration.ofMillis(100), "session");

        assertEquals("Identical results should be equal", result1, result2);
        assertEquals("Hash codes should be equal", result1.hashCode(), result2.hashCode());
    }

    public void testEqualsWithDifferentOutput() {
        ClaudeExecutionResult result1 = ClaudeExecutionResult.success("output1", Duration.ZERO);
        ClaudeExecutionResult result2 = ClaudeExecutionResult.success("output2", Duration.ZERO);

        assertFalse("Results with different outputs should not be equal", result1.equals(result2));
    }

    public void testEqualsWithDifferentSuccess() {
        ClaudeExecutionResult result1 = ClaudeExecutionResult.success("output", Duration.ZERO);
        ClaudeExecutionResult result2 = ClaudeExecutionResult.failure("output", 1);

        assertFalse("Results with different success should not be equal", result1.equals(result2));
    }

    public void testEqualsWithDifferentExitCode() {
        ClaudeExecutionResult result1 = ClaudeExecutionResult.failure("error", 1);
        ClaudeExecutionResult result2 = ClaudeExecutionResult.failure("error", 2);

        assertFalse("Results with different exit codes should not be equal", result1.equals(result2));
    }

    public void testEqualsWithDifferentSessionId() {
        ClaudeExecutionResult result1 = ClaudeExecutionResult.success("output", Duration.ZERO, "session1");
        ClaudeExecutionResult result2 = ClaudeExecutionResult.success("output", Duration.ZERO, "session2");

        assertFalse("Results with different session IDs should not be equal", result1.equals(result2));
    }

    public void testEqualsWithNull() {
        ClaudeExecutionResult result = ClaudeExecutionResult.success("output", Duration.ZERO);

        assertFalse("Result should not equal null", result.equals(null));
    }

    public void testEqualsWithDifferentClass() {
        ClaudeExecutionResult result = ClaudeExecutionResult.success("output", Duration.ZERO);

        assertFalse("Result should not equal different class", result.equals("output"));
    }

    public void testEqualsWithSelf() {
        ClaudeExecutionResult result = ClaudeExecutionResult.success("output", Duration.ZERO);

        assertEquals("Result should equal itself", result, result);
    }
}
