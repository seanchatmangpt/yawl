/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 * This file is part of YAWL. YAWL is free software: you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License.
 */
package org.yawlfoundation.yawl.ggen.sandbox;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Executes code in a Docker sandbox using the docker CLI.
 *
 * <p>Sandbox lifecycle:
 * <ol>
 *   <li>Create ephemeral workspace directory</li>
 *   <li>Write code to workspace/script.js</li>
 *   <li>Copy model-runner-bridge.js to workspace</li>
 *   <li>Create Docker sandbox: docker sandbox create</li>
 *   <li>Configure network proxy: docker sandbox network proxy</li>
 *   <li>Execute script: docker sandbox exec</li>
 *   <li>Capture stdout/stderr</li>
 *   <li>Cleanup workspace and sandbox (always, in finally block)</li>
 * </ol>
 */
public class DockerSandboxExecutor implements SandboxExecutor {

    private static final String BRIDGE_RESOURCE = "sandbox/model-runner-bridge.js";

    /**
     * Executes code in a Docker sandbox with the given configuration.
     *
     * @param code   the code to execute (e.g., Node.js script)
     * @param config sandbox configuration
     * @return the execution result
     * @throws IOException if the sandbox cannot be created or the process fails at the OS level
     * @throws IllegalArgumentException if code is blank or config is null
     */
    @Override
    public SandboxResult execute(String code, SandboxConfig config) throws IOException {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }

        String sandboxName = "yawl-sandbox-" + UUID.randomUUID().toString().substring(0, 8);
        Path workspace = config.workspaceDir() != null
            ? Files.createTempDirectory(config.workspaceDir(), "sandbox-")
            : Files.createTempDirectory("yawl-sandbox-");

        long startTime = System.currentTimeMillis();

        try {
            // Write code file
            Path scriptFile = workspace.resolve("script.js");
            Files.writeString(scriptFile, code, StandardCharsets.UTF_8);

            // Copy bridge script from resources
            copyBridgeScript(workspace);

            // Create sandbox
            runDockerCommand(List.of(
                "docker", "sandbox", "create",
                "--name", sandboxName,
                "-t", "shell",
                workspace.toAbsolutePath().toString()
            ), config.timeoutSeconds());

            // Configure network proxy
            runDockerCommand(List.of(
                "docker", "sandbox", "network", "proxy",
                sandboxName,
                "--allow-host", "localhost:12434"
            ), config.timeoutSeconds());

            // Execute script
            ProcessResult execResult = runDockerCommandWithOutput(List.of(
                "docker", "sandbox", "exec",
                sandboxName,
                "node", "script.js"
            ), config.timeoutSeconds());

            long durationMs = System.currentTimeMillis() - startTime;
            return new SandboxResult(execResult.stdout(), execResult.stderr(),
                execResult.exitCode(), durationMs);

        } finally {
            // Always clean up
            cleanupSandbox(sandboxName);
            deleteDirectory(workspace);
        }
    }

    /**
     * Copies the model-runner-bridge.js resource from the classpath to the workspace.
     *
     * @param workspace the workspace directory
     * @throws IOException if the resource cannot be found or copied
     */
    private void copyBridgeScript(Path workspace) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(BRIDGE_RESOURCE)) {
            if (is == null) {
                throw new IOException("Bridge resource not found: " + BRIDGE_RESOURCE);
            }
            Path dest = workspace.resolve("model-runner-bridge.js");
            Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Runs a Docker command and throws IOException if it exits with non-zero code.
     *
     * @param command    the command and arguments to run
     * @param timeoutSecs timeout in seconds
     * @throws IOException if the command fails or times out
     */
    private void runDockerCommand(List<String> command, int timeoutSecs) throws IOException {
        ProcessResult result = runDockerCommandWithOutput(command, timeoutSecs);
        if (result.exitCode() != 0) {
            throw new IOException("Docker command failed (exit " + result.exitCode() + "): "
                + String.join(" ", command) + "\nstderr: " + result.stderr());
        }
    }

    /**
     * Runs a Docker command and returns stdout, stderr, and exit code.
     *
     * @param command    the command and arguments to run
     * @param timeoutSecs timeout in seconds
     * @return the process result
     * @throws IOException if the command times out or is interrupted
     */
    private ProcessResult runDockerCommandWithOutput(List<String> command, int timeoutSecs) throws IOException {
        Process process = new ProcessBuilder(command)
            .redirectErrorStream(false)
            .start();
        try {
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = process.waitFor(timeoutSecs, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IOException("Docker command timed out after " + timeoutSecs + "s: "
                    + String.join(" ", command));
            }
            return new ProcessResult(stdout.trim(), stderr.trim(), process.exitValue());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Docker command interrupted: " + String.join(" ", command), e);
        }
    }

    /**
     * Attempts to clean up the Docker sandbox.
     * This is best-effort; errors are silently ignored.
     *
     * @param sandboxName the name of the sandbox to remove
     */
    private void cleanupSandbox(String sandboxName) {
        try {
            new ProcessBuilder("docker", "sandbox", "rm", "--force", sandboxName)
                .start()
                .waitFor(10, TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // Cleanup is best-effort; do not propagate
        }
    }

    /**
     * Recursively deletes a directory and all its contents.
     * This is best-effort; errors are silently ignored.
     *
     * @param dir the directory to delete
     */
    private void deleteDirectory(Path dir) {
        try {
            Files.walk(dir)
                .sorted(java.util.Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        } catch (IOException ignored) {
            // Cleanup is best-effort
        }
    }

    /**
     * Immutable result of running a process.
     */
    private record ProcessResult(String stdout, String stderr, int exitCode) {
    }
}
