package org.yawlfoundation.yawl.workflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for developer workflow scripts in .claude/
 *
 * Tests verify that dev-workflow.sh and watch-and-test.sh scripts:
 * - Are executable
 * - Have correct permissions
 * - Provide help output
 * - Handle basic commands correctly
 *
 * @author YAWL Development Team
 * @version 5.2
 */
public class WorkflowScriptsTest {

    private static final Path PROJECT_ROOT = resolveProjectRoot();
    private static final Path DEV_WORKFLOW_SCRIPT = PROJECT_ROOT.resolve(".claude").resolve("dev-workflow.sh");
    private static final Path WATCH_TEST_SCRIPT = PROJECT_ROOT.resolve(".claude").resolve("watch-and-test.sh");

    /**
     * Resolves the YAWL project root directory, which contains the .claude/ directory.
     * When running inside a Maven module (e.g. yawl-engine), the project root is the
     * parent of user.dir. Falls back to user.dir if .claude already exists there.
     */
    private static Path resolveProjectRoot() {
        Path moduleDir = Paths.get(System.getProperty("user.dir"));
        if (Files.exists(moduleDir.resolve(".claude"))) {
            return moduleDir;
        }
        Path parentDir = moduleDir.getParent();
        if (parentDir != null && Files.exists(parentDir.resolve(".claude"))) {
            return parentDir;
        }
        return moduleDir;
    }

    /**
     * Verify dev-workflow.sh exists and is executable
     */
    @Test
    public void testDevWorkflowScriptExists() {
        assertTrue(Files.exists(DEV_WORKFLOW_SCRIPT),
            "dev-workflow.sh should exist at " + DEV_WORKFLOW_SCRIPT);
        assertTrue(Files.isExecutable(DEV_WORKFLOW_SCRIPT),
            "dev-workflow.sh should be executable");
    }

    /**
     * Verify watch-and-test.sh exists and is executable
     */
    @Test
    public void testWatchTestScriptExists() {
        assertTrue(Files.exists(WATCH_TEST_SCRIPT),
            "watch-and-test.sh should exist at " + WATCH_TEST_SCRIPT);
        assertTrue(Files.isExecutable(WATCH_TEST_SCRIPT),
            "watch-and-test.sh should be executable");
    }

    /**
     * Test dev-workflow.sh help command produces output
     */
    @Test
    public void testDevWorkflowHelpOutput() throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            DEV_WORKFLOW_SCRIPT.toString(), "help"
        );
        pb.directory(PROJECT_ROOT.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        StringBuilder output = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        boolean completed = process.waitFor(10, TimeUnit.SECONDS);
        assertTrue(completed, "Help command should complete within 10 seconds");

        String helpOutput = output.toString();
        assertNotNull(helpOutput, "Help output should not be null");
        assertTrue(helpOutput.contains("YAWL Developer Workflow"),
            "Help should contain script name");
        assertTrue(helpOutput.contains("quick"),
            "Help should document 'quick' command");
        assertTrue(helpOutput.contains("compile"),
            "Help should document 'compile' command");
        assertTrue(helpOutput.contains("module"),
            "Help should document 'module' command");
    }

    /**
     * Test watch-and-test.sh help command produces output
     */
    @Test
    public void testWatchTestHelpOutput() throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            WATCH_TEST_SCRIPT.toString(), "--help"
        );
        pb.directory(PROJECT_ROOT.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        StringBuilder output = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        boolean completed = process.waitFor(10, TimeUnit.SECONDS);
        assertTrue(completed, "Help command should complete within 10 seconds");

        String helpOutput = output.toString();
        assertNotNull(helpOutput, "Help output should not be null");
        assertTrue(helpOutput.contains("YAWL File Watcher"),
            "Help should contain script name");
        assertTrue(helpOutput.contains("--all"),
            "Help should document '--all' option");
        assertTrue(helpOutput.contains("--compile-only"),
            "Help should document '--compile-only' option");
        assertTrue(helpOutput.contains("debounce"),
            "Help should mention debounce feature");
    }

    /**
     * Test dev-workflow.sh status command executes successfully
     */
    @Test
    public void testDevWorkflowStatusCommand() throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            DEV_WORKFLOW_SCRIPT.toString(), "status"
        );
        pb.directory(PROJECT_ROOT.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        StringBuilder output = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        boolean completed = process.waitFor(30, TimeUnit.SECONDS);
        assertTrue(completed, "Status command should complete within 30 seconds");

        String statusOutput = output.toString();
        assertNotNull(statusOutput, "Status output should not be null");
        assertTrue(statusOutput.contains("Build Health Status"),
            "Status should show build health");
    }

    /**
     * Test dev-workflow.sh handles invalid command gracefully
     */
    @Test
    public void testDevWorkflowInvalidCommand() throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            DEV_WORKFLOW_SCRIPT.toString(), "invalid-command-xyz"
        );
        pb.directory(PROJECT_ROOT.toFile());
        pb.redirectErrorStream(true);

        Process process = pb.start();
        StringBuilder output = new StringBuilder();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        boolean completed = process.waitFor(10, TimeUnit.SECONDS);
        assertTrue(completed, "Invalid command should complete within 10 seconds");

        int exitCode = process.exitValue();
        assertEquals(1, exitCode, "Invalid command should return exit code 1");

        String errorOutput = output.toString();
        assertTrue(errorOutput.contains("Unknown command"),
            "Should report unknown command error");
    }

    /**
     * Test scripts have Unix line endings (LF not CRLF)
     */
    @Test
    public void testScriptsHaveUnixLineEndings() throws Exception {
        byte[] devWorkflowBytes = Files.readAllBytes(DEV_WORKFLOW_SCRIPT);
        byte[] watchTestBytes = Files.readAllBytes(WATCH_TEST_SCRIPT);

        String devWorkflowContent = new String(devWorkflowBytes);
        String watchTestContent = new String(watchTestBytes);

        assertTrue(!devWorkflowContent.contains("\r\n"),
            "dev-workflow.sh should use Unix line endings (LF), not Windows (CRLF)");
        assertTrue(!watchTestContent.contains("\r\n"),
            "watch-and-test.sh should use Unix line endings (LF), not Windows (CRLF)");
    }

    /**
     * Test scripts start with proper shebang
     */
    @Test
    public void testScriptsHaveShebang() throws Exception {
        String devWorkflowFirstLine = Files.readAllLines(DEV_WORKFLOW_SCRIPT).get(0);
        String watchTestFirstLine = Files.readAllLines(WATCH_TEST_SCRIPT).get(0);

        assertEquals("#!/bin/bash", devWorkflowFirstLine,
            "dev-workflow.sh should start with #!/bin/bash");
        assertEquals("#!/bin/bash", watchTestFirstLine,
            "watch-and-test.sh should start with #!/bin/bash");
    }

    /**
     * Test README exists for workflow scripts
     */
    @Test
    public void testWorkflowScriptsReadmeExists() throws java.io.IOException {
        Path readme = PROJECT_ROOT.resolve(".claude").resolve("WORKFLOW_SCRIPTS_README.md");
        assertTrue(Files.exists(readme),
            "WORKFLOW_SCRIPTS_README.md should exist in .claude/");

        assertTrue(Files.size(readme) > 1000,
            "README should contain substantial documentation (> 1KB)");
    }
}
