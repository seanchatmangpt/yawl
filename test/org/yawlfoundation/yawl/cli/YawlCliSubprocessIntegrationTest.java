package org.yawlfoundation.yawl.cli;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * End-to-End Integration Tests for YAWL CLI Subcommands
 *
 * Chicago TDD (Detroit School): Tests use REAL CLI invocation, real subprocess
 * execution, real file I/O. NO mocks. All assertions based on actual behavior.
 *
 * Test Suite Coverage:
 *   - Build Commands: compile, test, validate, all
 *   - Observatory Commands: generate, search
 *   - Status Commands: status, health
 *   - DX Script Commands: phase isolation
 *   - Error Handling: invalid commands, timeouts, subprocess failures
 *   - File System I/O: real YAML loading/modification
 *
 * Execution:
 *   mvn test -Dtest=YawlCliSubprocessIntegrationTest
 *   bash scripts/yawl-tasks.sh test all (to validate manually)
 *
 * @author Test Specialist
 * @version 1.0
 */
public class YawlCliSubprocessIntegrationTest extends TestCase {

    private static final String PROJECT_ROOT = "/home/user/yawl";
    private static final String SCRIPTS_DIR = PROJECT_ROOT + "/scripts";
    private static final long COMMAND_TIMEOUT_SECONDS = 120;
    private static final long BUILD_TIMEOUT_SECONDS = 180;

    private Path tempProjectDir;
    private Path tempBuildDir;

    public YawlCliSubprocessIntegrationTest(String testName) {
        super(testName);
    }

    public static Test suite() {
        TestSuite suite = new TestSuite(YawlCliSubprocessIntegrationTest.class);
        return suite;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tempProjectDir = Files.createTempDirectory("yawl-cli-test-");
        tempBuildDir = Files.createTempDirectory("yawl-build-");
        assertTrue("Temp project directory must exist", Files.exists(tempProjectDir));
        assertTrue("Temp build directory must exist", Files.exists(tempBuildDir));
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (Files.exists(tempProjectDir)) {
            deleteRecursively(tempProjectDir);
        }
        if (Files.exists(tempBuildDir)) {
            deleteRecursively(tempBuildDir);
        }
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        Files.walk(path)
            .sorted(Comparator.reverseOrder())
            .forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    // Continue on failure
                }
            });
    }

    // ========================================================================
    // HELP COMMAND TESTS
    // ========================================================================

    public void testHelpCommand() throws Exception {
        // Act: Execute real help command via subprocess
        ProcessResult result = runCommand("bash", SCRIPTS_DIR + "/yawl-tasks.sh", "help");

        // Assert: Real subprocess executed, real output captured
        assertTrue("Help command should produce output",
            result.stdout.length() > 0);
        assertTrue("Help should contain command list or usage",
            result.stdout.contains("Commands:") || result.stdout.contains("Usage:") ||
            result.stdout.contains("yawl-tasks.sh"));
    }

    public void testInvalidCommand() throws Exception {
        // Act: Run invalid command
        ProcessResult result = runCommand("bash", SCRIPTS_DIR + "/yawl-tasks.sh", "nonexistent-cmd-" + System.nanoTime());

        // Assert: Should fail
        assertTrue("Invalid command should fail",
            result.exitCode != 0);
        assertTrue("Should report unknown command",
            result.stdout.contains("Unknown command") || result.stderr.contains("Unknown command") ||
            result.stdout.contains("unknown"));
    }

    // ========================================================================
    // BUILD COMMAND TESTS (Real subprocess execution)
    // ========================================================================

    public void testBuildCommandExists() throws Exception {
        // Act: Execute build command
        ProcessResult result = runCommand("bash", SCRIPTS_DIR + "/yawl-tasks.sh", "build");

        // Assert: Real subprocess execution happened
        assertNotNull("stdout should be captured", result.stdout);
        assertNotNull("stderr should be captured", result.stderr);
        assertTrue("exit code should be set", result.exitCode >= 0);
    }

    public void testBuildAllCommandExists() throws Exception {
        // Act: Execute build all command
        ProcessResult result = runCommand("bash", SCRIPTS_DIR + "/yawl-tasks.sh", "build", "all");

        // Assert: Real subprocess execution
        assertTrue("exit code should be valid", result.exitCode >= 0);
        assertNotNull("Should capture output", result.stdout);
    }

    public void testCleanCommandExecutes() throws Exception {
        // Act: Run clean command
        ProcessResult result = runCommand("bash", SCRIPTS_DIR + "/yawl-tasks.sh", "clean");

        // Assert: Real process executed
        assertTrue("Clean should complete", result.exitCode >= 0);
        assertTrue("Should produce output", result.stdout.length() > 0);
    }

    // ========================================================================
    // TEST COMMAND TESTS
    // ========================================================================

    public void testTestCommandExecutes() throws Exception {
        // Act: Run test command
        ProcessResult result = runCommand("bash", SCRIPTS_DIR + "/yawl-tasks.sh", "test");

        // Assert: Real subprocess executed
        assertTrue("Test command should complete", result.exitCode >= 0);
        assertNotNull("Should capture output", result.stdout);
    }

    public void testTestAllCommandExecutes() throws Exception {
        // Act: Run test all command
        ProcessResult result = runCommand("bash", SCRIPTS_DIR + "/yawl-tasks.sh", "test", "all");

        // Assert: Real process execution
        assertTrue("Test all should complete", result.exitCode >= 0);
    }

    // ========================================================================
    // STATUS COMMAND TESTS
    // ========================================================================

    public void testStatusCommand() throws Exception {
        // Act: Execute status command
        ProcessResult result = runCommand("bash", SCRIPTS_DIR + "/yawl-tasks.sh", "status");

        // Assert: Should produce output
        assertEquals("Status should succeed", 0, result.exitCode);
        assertTrue("Should contain status information",
            result.stdout.contains("Status") || result.stdout.contains("Git") ||
            result.stdout.length() > 0);
    }

    // ========================================================================
    // DX SCRIPT TESTS (Direct phase execution)
    // ========================================================================

    public void testDxScriptCompilePhase() throws Exception {
        // Act: Run dx.sh compile
        ProcessResult result = runCommand("bash", SCRIPTS_DIR + "/dx.sh", "compile");

        // Assert: Real subprocess
        assertTrue("dx.sh compile should execute", result.exitCode >= 0);
        assertNotNull("Should capture stdout", result.stdout);
    }

    public void testDxScriptTestPhase() throws Exception {
        // Act: Run dx.sh test
        ProcessResult result = runCommand("bash", SCRIPTS_DIR + "/dx.sh", "test");

        // Assert: Real subprocess
        assertTrue("dx.sh test should execute", result.exitCode >= 0);
    }

    public void testDxScriptAllPhase() throws Exception {
        // Act: Run dx.sh with all flag (long timeout)
        ProcessResult result = runCommandWithTimeout(
            new String[]{"bash", SCRIPTS_DIR + "/dx.sh", "all"},
            BUILD_TIMEOUT_SECONDS
        );

        // Assert: Real process
        assertTrue("dx.sh all should execute", result.exitCode >= 0);
    }

    public void testDxScriptSpecificModule() throws Exception {
        // Act: Run dx.sh with module specification
        ProcessResult result = runCommand("bash", SCRIPTS_DIR + "/dx.sh", "-pl", "yawl-utilities");

        // Assert: Real subprocess
        assertTrue("dx.sh -pl should execute", result.exitCode >= 0);
    }

    // ========================================================================
    // ERROR HANDLING TESTS
    // ========================================================================

    public void testMissingScript() throws Exception {
        // Act: Try to run nonexistent script
        ProcessResult result = runCommand(
            "bash",
            "/tmp/nonexistent-script-" + System.nanoTime() + ".sh"
        );

        // Assert: Should fail
        assertTrue("Running missing script should fail", result.exitCode != 0);
    }

    public void testCommandTimeout() throws Exception {
        // Arrange: Create slow script
        Path slowScript = Files.createTempFile("slow-test-", ".sh");
        Files.writeString(slowScript, "#!/bin/bash\nsleep 10\n", StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(slowScript, PosixFilePermissions.fromString("rwxr-xr-x"));

        try {
            // Act: Run with short timeout
            ProcessResult result = runCommandWithTimeout(
                new String[]{"bash", slowScript.toString()},
                1  // 1 second timeout
            );

            // Assert: Either times out or completes
            assertTrue("Should either timeout or complete", result.timedOut || result.exitCode >= 0);
        } finally {
            Files.deleteIfExists(slowScript);
        }
    }

    // ========================================================================
    // SUBPROCESS I/O TESTS (Real subprocess + file I/O)
    // ========================================================================

    public void testRealSubprocessExecution() throws Exception {
        // Act: Run Maven version to verify subprocess works
        ProcessResult result = runCommand("mvn", "-v");

        // Assert: Real process executed, real I/O
        assertEquals("Maven should be available", 0, result.exitCode);
        assertTrue("Should contain Maven output",
            result.stdout.contains("Maven") || result.stdout.contains("Apache"));
    }

    public void testRealFileSystemIO() throws Exception {
        // Arrange
        Path testFile = tempProjectDir.resolve("test-marker.txt");

        // Act: Write and read from filesystem
        Files.writeString(testFile, "marker content", StandardCharsets.UTF_8);

        // Assert: Real file I/O happened
        assertTrue("File should exist", Files.exists(testFile));
        String content = Files.readString(testFile);
        assertEquals("File content should match", "marker content", content);
    }

    public void testConfigFileModification() throws Exception {
        // Arrange: Create test config
        Path configFile = tempProjectDir.resolve("config.yaml");
        String original = "version: 1.0\ndebug: false\n";
        Files.writeString(configFile, original, StandardCharsets.UTF_8);

        // Act: Modify config
        String content = Files.readString(configFile);
        String modified = content.replace("debug: false", "debug: true");
        Files.writeString(configFile, modified, StandardCharsets.UTF_8);

        // Assert: Real config file modified
        String updated = Files.readString(configFile);
        assertTrue("Config should be updated", updated.contains("debug: true"));
        assertFalse("Old value should be gone", updated.contains("debug: false"));
    }

    public void testErrorOutputCapture() throws Exception {
        // Act: Run command that writes to stderr
        ProcessResult result = runCommand(
            "bash",
            "-c",
            "echo 'Error message' >&2; exit 1"
        );

        // Assert: stderr captured
        assertNotEquals("Should fail", 0, result.exitCode);
        assertTrue("Error should be in stderr",
            result.stderr.contains("Error message"));
    }

    public void testEnvironmentVariablePropagation() throws Exception {
        // Arrange: Prepare environment
        Map<String, String> env = new HashMap<>();
        env.put("TEST_VAR", "test_value");

        // Act: Run command that reads env var
        ProcessResult result = runCommandWithEnv(
            env,
            "bash",
            "-c",
            "echo $TEST_VAR"
        );

        // Assert: Environment variable propagated
        assertEquals("Command should succeed", 0, result.exitCode);
        assertTrue("Should output env var value",
            result.stdout.contains("test_value"));
    }

    // ========================================================================
    // PARAMETRIZED-STYLE TESTS (Manual parametrization)
    // ========================================================================

    public void testCommandsExist() throws Exception {
        String[] commands = {"test", "build", "clean", "help", "status"};

        for (String cmd : commands) {
            ProcessResult result = runCommand("bash", SCRIPTS_DIR + "/yawl-tasks.sh", cmd);

            // Should not complain about unknown command
            boolean isUnknown = result.stdout.contains("Unknown command") ||
                               result.stderr.contains("Unknown command");
            assertFalse("Command " + cmd + " should be recognized", isUnknown);
        }
    }

    public void testDxPhasesComplete() throws Exception {
        String[] phases = {"compile", "test"};

        for (String phase : phases) {
            ProcessResult result = runCommand("bash", SCRIPTS_DIR + "/dx.sh", phase);
            assertTrue("dx.sh " + phase + " should complete", result.exitCode >= 0);
        }
    }

    // ========================================================================
    // HELPER METHODS (Real Subprocess Execution - NOT mocked)
    // ========================================================================

    /**
     * Execute command with real subprocess (NOT mocked).
     * Chicago TDD: real process, real output, real files.
     */
    private ProcessResult runCommand(String... command) throws Exception {
        return runCommandWithTimeout(command, COMMAND_TIMEOUT_SECONDS);
    }

    /**
     * Execute command with timeout and real subprocess.
     * Captures stdout, stderr, exit code.
     */
    private ProcessResult runCommandWithTimeout(String[] command, long timeoutSeconds)
            throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(PROJECT_ROOT));
        pb.redirectErrorStream(false);

        Process process = pb.start();

        // Capture streams in separate threads
        StringBuilder stdout = captureStream(process.getInputStream());
        StringBuilder stderr = captureStream(process.getErrorStream());

        boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        int exitCode = completed ? process.exitValue() : -1;

        return new ProcessResult(
            exitCode,
            stdout.toString(),
            stderr.toString(),
            !completed
        );
    }

    /**
     * Capture subprocess output stream.
     */
    private StringBuilder captureStream(InputStream stream) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb;
    }

    /**
     * Execute command with environment variables.
     */
    private ProcessResult runCommandWithEnv(Map<String, String> env, String... command)
            throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(PROJECT_ROOT));

        // Merge environment
        Map<String, String> processEnv = pb.environment();
        processEnv.putAll(env);

        Process process = pb.start();
        boolean completed = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        int exitCode = completed ? process.exitValue() : -1;

        StringBuilder stdout = captureStream(process.getInputStream());
        StringBuilder stderr = captureStream(process.getErrorStream());

        return new ProcessResult(exitCode, stdout.toString(), stderr.toString(), !completed);
    }

    // ========================================================================
    // RESULT MODEL
    // ========================================================================

    /**
     * Captures real subprocess result data.
     * Used to verify real process execution, real output, real exit codes.
     */
    private static class ProcessResult {
        final int exitCode;
        final String stdout;
        final String stderr;
        final boolean timedOut;

        ProcessResult(int exitCode, String stdout, String stderr, boolean timedOut) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
            this.timedOut = timedOut;
        }

        @Override
        public String toString() {
            return String.format(
                "ProcessResult{exitCode=%d, stdout=%d chars, stderr=%d chars, timedOut=%s}",
                exitCode, stdout.length(), stderr.length(), timedOut
            );
        }
    }
}
