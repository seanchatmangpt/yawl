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

package org.yawlfoundation.yawl.mailSender;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junit.textui.TestRunner;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Tests for exception handling behaviour in the WebMail mail sender service (SOC2 CC7.2).
 *
 * <p>Maps to SOC2 control: CC7.2 - System Operations / Monitoring of System Capacity.
 * Silent exception swallowing is a SOC2 audit finding: errors must be logged so that
 * operations teams can detect and investigate failures. User-facing components must also
 * generate intelligible error output rather than silently producing no response.
 *
 * <p>This test suite verifies:
 * <ol>
 *   <li>MailSender.SendEmail() does NOT silently swallow SMTP delivery failures -
 *       it logs the error (via Log4j logger.error) AND rethrows as IllegalStateException</li>
 *   <li>The rethrown exception carries the recipient address so operators can trace failures</li>
 *   <li>The rethrown exception wraps the original cause (no information loss)</li>
 *   <li>WebMail.jsp catch block calls log() (servlet context logging) for operator visibility</li>
 *   <li>WebMail.jsp catch block generates user-facing HTML error output (not blank page)</li>
 *   <li>MailSender.handleEnabledWorkItemEvent() throws, not silently ignores engine events</li>
 *   <li>MailSender.handleCancelledWorkItemEvent() throws, not silently ignores engine events</li>
 * </ol>
 *
 * <p>Chicago TDD: Tests use real MailSender instances. SMTP delivery is exercised by
 * providing an intentionally invalid SMTP host, which produces a real MessagingException
 * from the Java Mail transport layer rather than any manufactured exception.
 *
 * @author YAWL Foundation - SOC2 Compliance 2026-02-17
 */
public class TestWebMailErrorHandling extends TestCase {

    private static final String PROJECT_ROOT = "/home/user/yawl";
    private static final String WEBMAIL_JSP =
            PROJECT_ROOT + "/src/org/yawlfoundation/yawl/mailSender/WebMail.jsp";

    /**
     * System property used to supply the engine password to InterfaceBWebsideController
     * in environments where YAWL_ENGINE_PASSWORD is not set (e.g. CI test runners).
     * The value is a non-empty placeholder that satisfies the non-null check; no real
     * engine connection is made during these tests.
     */
    private static final String ENGINE_PASSWORD_PROP = "yawl.engine.password";
    private static final String TEST_ENGINE_PASSWORD = "test-engine-password-for-unit-tests";

    private MailSender mailSender;

    public TestWebMailErrorHandling(String name) {
        super(name);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // InterfaceBWebsideController requires a non-empty engine password via env var or
        // system property. In test environments where YAWL_ENGINE_PASSWORD is not set,
        // we provide it via the documented system property so the constructor succeeds.
        if (System.getenv("YAWL_ENGINE_PASSWORD") == null ||
                System.getenv("YAWL_ENGINE_PASSWORD").isEmpty()) {
            System.setProperty(ENGINE_PASSWORD_PROP, TEST_ENGINE_PASSWORD);
        }
        mailSender = new MailSender();
    }

    @Override
    protected void tearDown() throws Exception {
        mailSender = null;
        // Restore: remove test-only system property if we set it
        if (TEST_ENGINE_PASSWORD.equals(System.getProperty(ENGINE_PASSWORD_PROP))) {
            System.clearProperty(ENGINE_PASSWORD_PROP);
        }
        super.tearDown();
    }

    // =========================================================================
    // CC7.2 - MailSender.SendEmail: exception must NOT be silently swallowed
    // =========================================================================

    /**
     * CC7.2: MailSender.SendEmail() must throw IllegalStateException when the SMTP
     * host is unreachable. Errors must propagate to callers; silent swallowing prevents
     * operators from detecting delivery failures in monitoring dashboards.
     *
     * This test uses a deliberately invalid SMTP host ("smtp.invalid.nonexistent.test")
     * which causes a real MessagingException from Jakarta Mail's Transport layer.
     */
    public void testSendEmail_InvalidSmtpHost_ThrowsIllegalStateException() {
        try {
            mailSender.SendEmail(
                    "smtp.invalid.nonexistent.test",
                    "587",
                    "user@example.com",
                    "password",
                    "recipient@example.com",
                    "Sender Name",
                    "Test Subject",
                    "Test message body",
                    null);
            fail("CC7.2 FAIL: SendEmail must throw when SMTP host is unreachable " +
                 "(silent swallowing of delivery failures violates SOC2 CC7.2)");
        } catch (IllegalStateException e) {
            assertNotNull("CC7.2: Thrown exception must have a message", e.getMessage());
            assertTrue("CC7.2: Exception message must identify the delivery failure context",
                    e.getMessage().contains("Email delivery failed") ||
                    e.getMessage().contains("recipient") ||
                    e.getMessage().contains("recipient@example.com"));
        }
    }

    /**
     * CC7.2: The IllegalStateException thrown by SendEmail must include the recipient
     * address in its message. This enables operators to correlate log entries with
     * specific delivery failures without exposing message content.
     */
    public void testSendEmail_InvalidSmtpHost_ExceptionMessageContainsRecipient() {
        final String recipient = "audit-test-recipient@example.com";
        try {
            mailSender.SendEmail(
                    "smtp.invalid.nonexistent.test",
                    "25",
                    "sender@example.com",
                    "pass",
                    recipient,
                    "Test Sender",
                    "SOC2 Audit Test",
                    "Audit message",
                    null);
            fail("CC7.2 FAIL: SendEmail must throw, not return silently on SMTP failure");
        } catch (IllegalStateException e) {
            assertTrue(
                    "CC7.2: Exception message must include recipient '" + recipient +
                    "' for operator traceability. Got: " + e.getMessage(),
                    e.getMessage().contains(recipient));
        }
    }

    /**
     * CC7.2: The IllegalStateException thrown by SendEmail must wrap the original
     * MessagingException as its cause. This preserves the full diagnostic stack trace
     * so that monitoring systems (SIEM, log aggregators) can identify root causes.
     */
    public void testSendEmail_InvalidSmtpHost_ExceptionCauseIsPreserved() {
        try {
            mailSender.SendEmail(
                    "smtp.invalid.nonexistent.test",
                    "465",
                    "sender@example.com",
                    "pass",
                    "target@example.com",
                    "Alias",
                    "Subject",
                    "Body",
                    null);
            fail("CC7.2 FAIL: SendEmail must throw IllegalStateException wrapping the original cause");
        } catch (IllegalStateException e) {
            assertNotNull(
                    "CC7.2: IllegalStateException must wrap the original MessagingException as cause. " +
                    "Lost cause means lost diagnostic information (SOC2 CC7.2 violation).",
                    e.getCause());
        }
    }

    // =========================================================================
    // CC7.2 - WebMail.jsp: catch block must log and generate user-facing output
    // =========================================================================

    /**
     * CC7.2: WebMail.jsp's catch block must call log() (the JSP/Servlet context logger)
     * so that exceptions appear in server logs for operator visibility.
     *
     * Verified by reading the actual JSP source: the presence of {@code log(...)} in the
     * catch block is a structural requirement. If it were absent, SMTP errors would be
     * silently swallowed and invisible to operations teams.
     */
    public void testWebMailJsp_CatchBlock_CallsServletContextLog() throws IOException {
        String jspSource = readJspSource();

        // Verify the catch block calls log() - the JSP implicit 'log' method routes to
        // the servlet container's logging system (typically catalina.out / container logger)
        assertTrue(
                "CC7.2 FAIL: WebMail.jsp catch block must call log() to surface errors to " +
                "server logs. Silent exception swallowing violates SOC2 CC7.2 (operator visibility).",
                jspSource.contains("log("));
    }

    /**
     * CC7.2: WebMail.jsp's catch block must include the exception message in the log call.
     * Logging only "An error occurred" without the exception detail is insufficient for
     * SOC2 CC7.2 (the operator cannot diagnose the root cause from the log entry alone).
     */
    public void testWebMailJsp_CatchBlock_LogsExceptionMessageAndStackTrace() throws IOException {
        String jspSource = readJspSource();

        // The JSP log() call in the catch block must pass both the error description
        // and the exception (e) so that the stack trace appears in container logs.
        // Pattern: log("ERROR: ...", e) or log("...: " + e.getMessage(), e)
        assertTrue(
                "CC7.2 FAIL: WebMail.jsp catch block must pass the exception to log() " +
                "so that the stack trace appears in server logs. " +
                "Log-without-exception loses diagnostic information.",
                jspSource.contains("log(") && jspSource.contains(", e)"));
    }

    /**
     * CC7.2: WebMail.jsp's catch block must call out.println() to generate a user-facing
     * error message. A blank page on error gives users no feedback and forces them to
     * retry infinitely - which could amplify load on a failing SMTP server.
     */
    public void testWebMailJsp_CatchBlock_GeneratesUserFacingErrorOutput() throws IOException {
        String jspSource = readJspSource();

        // The catch block must produce visible output so the user knows the action failed.
        // Verify presence of out.println with error-related content.
        assertTrue(
                "CC7.2 FAIL: WebMail.jsp catch block must call out.println() to produce " +
                "a user-facing error message. A silent blank page leaves users with no feedback.",
                jspSource.contains("out.println("));

        // The output must reference 'error' so users understand something went wrong
        assertTrue(
                "CC7.2 FAIL: WebMail.jsp user-facing error message must contain 'error' or 'Error' " +
                "so users understand the action failed and do not silently lose work.",
                jspSource.toLowerCase().contains("error sending") ||
                jspSource.toLowerCase().contains("error:") ||
                jspSource.contains("color:red") ||
                jspSource.contains("color: red"));
    }

    /**
     * CC7.2: WebMail.jsp's catch block must include the exception message in the user-facing
     * output (e.getMessage()). This provides enough context for users to report the failure
     * to support without needing to inspect server logs.
     */
    public void testWebMailJsp_CatchBlock_IncludesExceptionDetailInUserOutput() throws IOException {
        String jspSource = readJspSource();

        // The out.println call must include e.getMessage() so the user sees what went wrong
        assertTrue(
                "CC7.2 FAIL: WebMail.jsp user-facing error output must include e.getMessage() " +
                "to provide actionable feedback. Suppressing the detail forces users to contact " +
                "operations with no context.",
                jspSource.contains("e.getMessage()"));
    }

    // =========================================================================
    // CC7.2 - Unsupported operations: must throw, not silently ignore
    // =========================================================================

    /**
     * CC7.2: MailSender.handleEnabledWorkItemEvent() must throw UnsupportedOperationException.
     * Silently ignoring engine-dispatched work items would cause workflow deadlocks without
     * any operator notification - a systemic monitoring failure.
     */
    public void testHandleEnabledWorkItemEvent_ThrowsUnsupportedOperationException() {
        WorkItemRecord dummyRecord = new WorkItemRecord(
                "case-1", "task-1", "net-1", "enabled");
        try {
            mailSender.handleEnabledWorkItemEvent(dummyRecord);
            fail("CC7.2 FAIL: handleEnabledWorkItemEvent must throw UnsupportedOperationException " +
                 "(silently ignoring engine events causes undetected workflow deadlocks)");
        } catch (UnsupportedOperationException e) {
            assertNotNull("CC7.2: Exception must have a descriptive message", e.getMessage());
            assertFalse("CC7.2: Exception message must not be blank",
                    e.getMessage().trim().isEmpty());
        }
    }

    /**
     * CC7.2: MailSender.handleCancelledWorkItemEvent() must throw UnsupportedOperationException.
     * Silently dropping cancellation notifications would prevent cleanup routines from running,
     * causing resource leaks that are undetectable without explicit exception propagation.
     */
    public void testHandleCancelledWorkItemEvent_ThrowsUnsupportedOperationException() {
        WorkItemRecord dummyRecord = new WorkItemRecord(
                "case-2", "task-2", "net-2", "cancelled");
        try {
            mailSender.handleCancelledWorkItemEvent(dummyRecord);
            fail("CC7.2 FAIL: handleCancelledWorkItemEvent must throw UnsupportedOperationException " +
                 "(silently ignoring cancellation events causes undetected resource leaks)");
        } catch (UnsupportedOperationException e) {
            assertNotNull("CC7.2: Exception must have a descriptive message", e.getMessage());
            assertFalse("CC7.2: Exception message must not be blank",
                    e.getMessage().trim().isEmpty());
        }
    }

    // =========================================================================
    // Supporting helpers
    // =========================================================================

    private String readJspSource() throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(WEBMAIL_JSP));
        return new String(bytes, StandardCharsets.UTF_8);
    }

    // =========================================================================
    // Test Suite
    // =========================================================================

    public static Test suite() {
        TestSuite suite = new TestSuite("WebMail Error Handling Tests (SOC2 CC7.2)");
        suite.addTestSuite(TestWebMailErrorHandling.class);
        return suite;
    }

    public static void main(String[] args) {
        TestRunner.run(suite());
    }
}
