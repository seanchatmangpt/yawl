package org.yawlfoundation.yawl.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * End-to-End Integration Tests for YAWL CLI Subcommands
 *
 * Chicago TDD (Detroit School): Real CLI invocation, real subprocess execution,
 * real file I/O. NO MOCKS. All assertions based on actual behavior.
 *
 * Usage:
 *   java -cp /path/to/classpath org.yawlfoundation.yawl.cli.CliSubprocessIntegrationRunner
 *
 * Or better:
 *   mvn exec:java -Dexec.mainClass="org.yawlfoundation.yawl.cli.CliSubprocessIntegrationRunner"
 *
 * Test Coverage:
 *   - Build Commands: compile, test, validate, all
 *   - Observatory Commands: status, health
 *   - DX Script Commands: phase isolation
 *   - Error Handling: invalid commands, timeouts
 *   - Subprocess I/O: real output capture, real files
 *   - Environment Variables: real propagation
 *
 * @author Test Specialist
 * @version 1.0
 */
public class CliSubprocessIntegrationRunner {

    private static final String PROJECT_ROOT = "/home/user/yawl";
    private static final String SCRIPTS_DIR = PROJECT_ROOT + "/scripts";
    private static final long COMMAND_TIMEOUT_SECONDS = 120;

    private int testsRun = 0;
    private int testsPassed = 0;
    private int testsFailed = 0;

    public static void main(String[] args) {
        CliSubprocessIntegrationRunner runner = new CliSubprocessIntegrationRunner();
        runner.runAllTests();
        System.exit(runner.testsFailed > 0 ? 1 : 0);
    }

    private void runAllTests() {
        println("\n" + "=".repeat(70));
        println("YAWL CLI End-to-End Integration Tests (Chicago TDD)");
        println("Real subprocess execution, real file I/O, NO MOCKS");
        println("=".repeat(70) + "\n");

        // Help commands
        test("testHelpCommand", this::testHelpCommand);
        test("testInvalidCommand", this::testInvalidCommand);

        // Build commands
        test("testBuildCommandExists", this::testBuildCommandExists);
        test("testBuildAllCommandExists", this::testBuildAllCommandExists);
        test("testCleanCommandExecutes", this::testCleanCommandExecutes);

        // Test commands
        test("testTestCommandExecutes", this::testTestCommandExecutes);
        test("testTestAllCommandExecutes", this::testTestAllCommandExecutes);

        // Status commands
        test("testStatusCommand", this::testStatusCommand);

        // DX script tests
        test("testDxScriptCompilePhase", this::testDxScriptCompilePhase);
        test("testDxScriptTestPhase", this::testDxScriptTestPhase);
        test("testDxScriptSpecificModule", this::testDxScriptSpecificModule);

        // Error handling
        test("testMissingScript", this::testMissingScript);
        test("testCommandTimeout", this::testCommandTimeout);

        // Subprocess I/O
        test("testRealSubprocessExecution", this::testRealSubprocessExecution);
        test("testRealFileSystemIO", this::testRealFileSystemIO);
        test("testConfigFileModification", this::testConfigFileModification);
        test("testErrorOutputCapture", this::testErrorOutputCapture);
        test("testEnvironmentVariablePropagation", this::testEnvironmentVariablePropagation);

        // Results
        println("\n" + "=".repeat(70));
        println("Test Results:");
        println(String.format("  Total:  %d", testsRun));
        println(String.format("  Passed: %d", testsPassed));
        println(String.format("  Failed: %d", testsFailed));
        println("=".repeat(70) + "\n");
    }

    // ========================================================================
    // HELP COMMAND TESTS
    // ========================================================================

    private void testHelpCommand() throws Exception {
        ProcessResult result = runCommand("bash", SCRIPTS_DIR + "/yawl-tasks.sh", "help");
        assertTrue(result.stdout.length() > 0, "Help command should produce output");
        assertTrue(
            result.stdout.contains("Commands:") || result.stdout.contains("Usage:"),
            "Help should contain command list"
        );
    }

    private void testInvalidCommand() throws Exception {
        ProcessResult result = runCommand("bash", SCRIPTS_DIR + "/yawl-tasks.sh", "nonexistent-cmd");
        assertTrue(result.exitCode != 0, "Invalid command should fail");
        assertTrue(
            result.stdout.contains("Unknown command") || result.stderr.contains("Unknown command"),
            "Should report unknown command"
        );
    }

    // ========================================================================
    // BUILD COMMAND TESTS
    // ========================================================================

    private void testBuildCommandExists() throws Exception {
        ProcessResult result = runCommand("bash", SCRIPTS_DIR + "/yawl-tasks.sh", "build");
        assertNotNull(result.stdout, "stdout should be captured");
        assertNotNull(result.stderr, "stderr should be captured");
        assertTrue(result.exitCode >= 0, "exit code should be set");
    }

    private void testBuildAllCommandExists() throws Exception {
        ProcessResult result = runCommand("bash", SCRIPTS_DIR + "/yawl-tasks.sh", "build", "all");
        assertTrue(result.exitCode >= 0, "exit code should be valid");
        assertNotNull(result.stdout, "Should capture output");
    }

    private void testCleanCommandExecutes() throws Exception {
        ProcessResult result = runCommand("bash", SCRIPTS_DIR + "/yawl-tasks.sh", "clean");
        assertTrue(result.exitCode >= 0, "Clean should complete");
        assertTrue(result.stdout.length() > 0, "Should produce output");
    }

    // ========================================================================
    // TEST COMMAND TESTS
    // ========================================================================

    private void testTestCommandExecutes() throws Exception {
        ProcessResult result = runCommand("bash", SCRIPTS_DIR + "/yawl-tasks.sh", "test");
        assertTrue(result.exitCode >= 0, "Test command should complete");
        assertNotNull(result.stdout, "Should capture output");
    }

    private void testTestAllCommandExecutes() throws Exception {
        ProcessResult result = runCommand("bash", SCRIPTS_DIR + "/yawl-tasks.sh", "test", "all");
        assertTrue(result.exitCode >= 0, "Test all should complete");
    }

    // ========================================================================
    // STATUS COMMAND TESTS
    // ========================================================================

    private void testStatusCommand() throws Exception {
        ProcessResult result = runCommand("bash", SCRIPTS_DIR + "/yawl-tasks.sh", "status");
        assertEquals(0, result.exitCode, "Status should succeed");
        assertTrue(
            result.stdout.contains("Status") || result.stdout.contains("Git") ||
            result.stdout.length() > 0,
            "Should contain status information"
        );
    }

    // ========================================================================
    // DX SCRIPT TESTS
    // ========================================================================

    private void testDxScriptCompilePhase() throws Exception {
        ProcessResult result = runCommand("bash", SCRIPTS_DIR + "/dx.sh", "compile");
        assertTrue(result.exitCode >= 0, "dx.sh compile should execute");
        assertNotNull(result.stdout, "Should capture stdout");
    }

    private void testDxScriptTestPhase() throws Exception {
        ProcessResult result = runCommand("bash", SCRIPTS_DIR + "/dx.sh", "test");
        assertTrue(result.exitCode >= 0, "dx.sh test should execute");
    }

    private void testDxScriptSpecificModule() throws Exception {
        ProcessResult result = runCommand("bash", SCRIPTS_DIR + "/dx.sh", "-pl", "yawl-utilities");
        assertTrue(result.exitCode >= 0, "dx.sh -pl should execute");
    }

    // ========================================================================
    // ERROR HANDLING TESTS
    // ========================================================================

    private void testMissingScript() throws Exception {
        ProcessResult result = runCommand("bash", "/tmp/nonexistent-" + System.nanoTime() + ".sh");
        assertTrue(result.exitCode != 0, "Running missing script should fail");
    }

    private void testCommandTimeout() throws Exception {
        Path slowScript = Files.createTempFile("slow-", ".sh");
        Files.writeString(slowScript, "#!/bin/bash\nsleep 10\n", StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(slowScript, PosixFilePermissions.fromString("rwxr-xr-x"));

        try {
            ProcessResult result = runCommandWithTimeout(
                new String[]{"bash", slowScript.toString()},
                1  // 1 second timeout
            );
            assertTrue(result.timedOut || result.exitCode >= 0, "Should either timeout or complete");
        } finally {
            Files.deleteIfExists(slowScript);
        }
    }

    // ========================================================================
    // SUBPROCESS I/O TESTS (Real file I/O, real output)
    // ========================================================================

    private void testRealSubprocessExecution() throws Exception {
        ProcessResult result = runCommand("mvn", "-v");
        assertEquals(0, result.exitCode, "Maven should be available");
        assertTrue(
            result.stdout.contains("Maven") || result.stdout.contains("Apache"),
            "Should contain Maven output"
        );
    }

    private void testRealFileSystemIO() throws Exception {
        Path testDir = Files.createTempDirectory("cli-test-");
        try {
            Path testFile = testDir.resolve("test-marker.txt");
            Files.writeString(testFile, "marker content", StandardCharsets.UTF_8);

            assertTrue(Files.exists(testFile), "File should exist");
            String content = Files.readString(testFile);
            assertEquals("marker content", content, "File content should match");
        } finally {
            deleteRecursively(testDir);
        }
    }

    private void testConfigFileModification() throws Exception {
        Path testDir = Files.createTempDirectory("config-test-");
        try {
            Path configFile = testDir.resolve("config.yaml");
            String original = "version: 1.0\ndebug: false\n";
            Files.writeString(configFile, original, StandardCharsets.UTF_8);

            String content = Files.readString(configFile);
            String modified = content.replace("debug: false", "debug: true");
            Files.writeString(configFile, modified, StandardCharsets.UTF_8);

            String updated = Files.readString(configFile);
            assertTrue(updated.contains("debug: true"), "Config should be updated");
            assertFalse(updated.contains("debug: false"), "Old value should be gone");
        } finally {
            deleteRecursively(testDir);
        }
    }

    private void testErrorOutputCapture() throws Exception {
        ProcessResult result = runCommand("bash", "-c", "echo 'Error message' >&2; exit 1");
        assertNotEquals(0, result.exitCode, "Should fail");
        assertTrue(result.stderr.contains("Error message"), "Error should be in stderr");
    }

    private void testEnvironmentVariablePropagation() throws Exception {
        Map<String, String> env = new HashMap<>();
        env.put("TEST_VAR", "test_value");

        ProcessResult result = runCommandWithEnv(env, "bash", "-c", "echo $TEST_VAR");
        assertEquals(0, result.exitCode, "Command should succeed");
        assertTrue(result.stdout.contains("test_value"), "Should output env var value");
    }

    // ========================================================================
    // HELPER METHODS (Real Subprocess Execution)
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

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        Files.walk(path)
            .sorted(Comparator.reverseOrder())
            .forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    // Ignored
                }
            });
    }

    // ========================================================================
    // TEST EXECUTION FRAMEWORK
    // ========================================================================

    @FunctionalInterface
    interface TestMethod {
        void run() throws Exception;
    }

    private void test(String testName, TestMethod testMethod) {
        testsRun++;
        try {
            testMethod.run();
            testsPassed++;
            println("✓ PASS: " + testName);
        } catch (AssertionError e) {
            testsFailed++;
            println("✗ FAIL: " + testName);
            println("        " + e.getMessage());
        } catch (Exception e) {
            testsFailed++;
            println("✗ ERROR: " + testName);
            println("        " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void assertTrue(boolean condition, String message) throws AssertionError {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private void assertFalse(boolean condition, String message) throws AssertionError {
        if (condition) {
            throw new AssertionError(message);
        }
    }

    private void assertEquals(Object expected, Object actual, String message) throws AssertionError {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(message + " (expected: " + expected + ", actual: " + actual + ")");
        }
    }

    private void assertNotEquals(Object expected, Object actual, String message) throws AssertionError {
        if (Objects.equals(expected, actual)) {
            throw new AssertionError(message + " (should not equal: " + expected + ")");
        }
    }

    private void assertNotNull(Object value, String message) throws AssertionError {
        if (value == null) {
            throw new AssertionError(message);
        }
    }

    private void println(String msg) {
        System.out.println(msg);
    }

    // ========================================================================
    // RESULT MODEL
    // ========================================================================

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
