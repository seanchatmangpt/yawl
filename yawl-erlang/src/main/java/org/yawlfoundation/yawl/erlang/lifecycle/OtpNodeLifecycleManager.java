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
package org.yawlfoundation.yawl.erlang.lifecycle;

import org.yawlfoundation.yawl.erlang.error.OtpNodeUnavailableException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Manages the lifecycle of an OTP Erlang node: start, monitor, and restart.
 *
 * <p>Start sequence:
 * <ol>
 *   <li>Locate OTP bin via {@link OtpInstallationVerifier#findOtpBin()}</li>
 *   <li>Launch: {@code erl -name <nodeName> -setcookie <cookie> -noshell -noinput}</li>
 *   <li>Poll {@code epmd -names} every 500ms until the node appears (timeout 10s)</li>
 *   <li>Throw {@link OtpNodeUnavailableException} on timeout</li>
 * </ol>
 *
 * <p>Watchdog: polls {@link #isAlive()} every 5s; on failure, calls {@link #restart()}
 * and emits a {@link NodeRestartEvent} to the registered listener.
 *
 * <p>Thread-safe: volatile fields + AtomicInteger for restart count.
 */
public final class OtpNodeLifecycleManager implements AutoCloseable {

    private final String nodeName;
    private final String cookie;
    private final Path erlBin;

    private volatile Process nodeProcess;
    private volatile boolean started = false;
    private final AtomicInteger restartCount = new AtomicInteger(0);
    private final AtomicBoolean watchdogRunning = new AtomicBoolean(false);
    private volatile Thread watchdogThread;
    private volatile Consumer<NodeRestartEvent> restartListener;

    private OtpNodeLifecycleManager(String nodeName, String cookie, Path erlBin) {
        this.nodeName = nodeName;
        this.cookie = cookie;
        this.erlBin = erlBin;
    }

    /**
     * Creates a lifecycle manager for the given node.
     * Does NOT start the node — call {@link #start()} explicitly.
     *
     * @param nodeName Erlang node name (e.g. {@code "yawl_test@127.0.0.1"})
     * @param cookie   distribution cookie
     * @return configured lifecycle manager
     * @throws OtpNodeUnavailableException if OTP is not installed
     */
    public static OtpNodeLifecycleManager forNode(String nodeName, String cookie)
            throws OtpNodeUnavailableException {
        if (nodeName == null || nodeName.isBlank()) {
            throw new IllegalArgumentException("nodeName must be non-blank");
        }
        if (cookie == null || cookie.isBlank()) {
            throw new IllegalArgumentException("cookie must be non-blank");
        }
        Optional<Path> binOpt = OtpInstallationVerifier.findOtpBin();
        if (binOpt.isEmpty()) {
            throw new OtpNodeUnavailableException(
                "OTP not found. Install Erlang/OTP 28 or set YAWL_OTP_HOME.");
        }
        return new OtpNodeLifecycleManager(nodeName, cookie, binOpt.get());
    }

    /**
     * Starts the OTP node and waits up to 10 seconds for it to register in EPMD.
     *
     * @throws OtpNodeUnavailableException if the node fails to start or does not
     *                                      register in EPMD within 10 seconds
     */
    public void start() throws OtpNodeUnavailableException {
        Path erlExec = erlBin.resolve("erl");
        List<String> cmd = new ArrayList<>(List.of(
            erlExec.toString(),
            "-name", nodeName,
            "-setcookie", cookie,
            "-noshell",
            "-noinput"
        ));

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            nodeProcess = pb.start();

            // Drain stdout to prevent blocking
            Thread.ofVirtual().start(() -> {
                try (var is = nodeProcess.getInputStream()) {
                    is.transferTo(java.io.OutputStream.nullOutputStream());
                } catch (IOException ignored) {}
            });

            // Poll epmd -names for up to 10 seconds
            long deadline = System.currentTimeMillis() + 10_000;
            String shortName = nodeName.split("@")[0];

            while (System.currentTimeMillis() < deadline) {
                if (!nodeProcess.isAlive()) {
                    throw new OtpNodeUnavailableException(
                        "OTP node process exited prematurely (exit code: "
                        + nodeProcess.exitValue() + ")");
                }
                if (isRegisteredInEpmd(shortName)) {
                    started = true;
                    return;
                }
                Thread.sleep(500);
            }

            nodeProcess.destroyForcibly();
            throw new OtpNodeUnavailableException(
                "OTP node '" + nodeName + "' did not register in EPMD within 10 seconds");

        } catch (IOException e) {
            throw new OtpNodeUnavailableException("Failed to launch OTP node: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OtpNodeUnavailableException("Interrupted while waiting for OTP node", e);
        }
    }

    /**
     * Returns true if the managed node is alive (responds to a net_adm:ping probe).
     * Uses a separate probe erl node to ping — exit code 0 means alive.
     *
     * @return true if the node is alive and responds to ping
     */
    public boolean isAlive() {
        if (nodeProcess == null || !nodeProcess.isAlive()) {
            return false;
        }
        Path erlExec = erlBin.resolve("erl");
        String pingExpr = "case net_adm:ping('" + nodeName + "') of pong -> halt(0); _ -> halt(1) end";
        try {
            ProcessBuilder pb = new ProcessBuilder(
                erlExec.toString(),
                "-name", "probe_" + System.currentTimeMillis() + "@127.0.0.1",
                "-setcookie", cookie,
                "-eval", pingExpr,
                "-noshell",
                "-noinput"
            );
            pb.redirectErrorStream(true);
            Process probe = pb.start();
            try (var is = probe.getInputStream()) {
                is.transferTo(java.io.OutputStream.nullOutputStream());
            }
            int exit = probe.waitFor();
            return exit == 0;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Shuts down the OTP node. First attempts a graceful stop,
     * waits up to 5 seconds, then destroys the process forcibly.
     *
     * @throws InterruptedException if interrupted during shutdown
     */
    public void shutdown() throws InterruptedException {
        started = false;
        if (nodeProcess == null) {
            return;
        }
        nodeProcess.destroy();
        // Wait up to 5 seconds for graceful shutdown
        boolean terminated = nodeProcess.waitFor(5, TimeUnit.SECONDS);
        if (!terminated) {
            nodeProcess.destroyForcibly();
        }
    }

    /**
     * Restarts the node: shutdown + start. Preserves cookie.
     *
     * @throws OtpNodeUnavailableException if start fails
     * @throws InterruptedException        if interrupted during shutdown
     */
    public void restart() throws OtpNodeUnavailableException, InterruptedException {
        shutdown();
        start();
    }

    /**
     * Starts a watchdog virtual thread that polls {@link #isAlive()} every 5 seconds.
     * On failure, calls {@link #restart()} and emits a {@link NodeRestartEvent}.
     * The registered listener (if any) is called synchronously from the watchdog thread.
     */
    public void startWatchdog() {
        if (watchdogRunning.compareAndSet(false, true)) {
            watchdogThread = Thread.ofVirtual()
                .name("otp-watchdog-" + nodeName)
                .start(() -> {
                    while (watchdogRunning.get()) {
                        try {
                            Thread.sleep(5_000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        if (!watchdogRunning.get()) break;
                        if (!isAlive()) {
                            int count = restartCount.incrementAndGet();
                            NodeRestartEvent event = new NodeRestartEvent(
                                nodeName,
                                Instant.now(),
                                count,
                                "watchdog detected node death"
                            );
                            try {
                                restart();
                            } catch (OtpNodeUnavailableException | InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            Consumer<NodeRestartEvent> listener = restartListener;
                            if (listener != null) {
                                listener.accept(event);
                            }
                        }
                    }
                });
        }
    }

    /**
     * Stops the watchdog thread.
     */
    public void stopWatchdog() {
        watchdogRunning.set(false);
        Thread wt = watchdogThread;
        if (wt != null) {
            wt.interrupt();
        }
    }

    /**
     * Sets a listener that is called whenever the watchdog restarts the node.
     *
     * @param listener consumer of restart events, or null to remove
     */
    public void setRestartListener(Consumer<NodeRestartEvent> listener) {
        this.restartListener = listener;
    }

    /**
     * Returns the underlying OS process for the OTP node.
     * Useful for forcibly killing the node in tests.
     *
     * @return the Process, or null if not started
     */
    public Process getProcess() {
        return nodeProcess;
    }

    /**
     * Returns the cumulative restart count.
     *
     * @return number of times the node has been restarted by the watchdog
     */
    public int getRestartCount() {
        return restartCount.get();
    }

    /**
     * Returns a path where .beam files should be placed for hot reload tests.
     * Uses a temp directory named after the node.
     *
     * @return ebin directory for .beam files
     */
    public Path getEbinPath() {
        Path ebinDir = Path.of(System.getProperty("java.io.tmpdir"))
            .resolve("yawl-otp-ebin-" + nodeName.replace("@", "_").replace(".", "_"));
        try {
            Files.createDirectories(ebinDir);
        } catch (IOException ignored) {}
        return ebinDir;
    }

    /**
     * Closes the lifecycle manager: stops watchdog and shuts down the node.
     */
    @Override
    public void close() {
        stopWatchdog();
        try {
            shutdown();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private boolean isRegisteredInEpmd(String shortName) {
        Path epmdExec = erlBin.resolve("epmd");
        try {
            ProcessBuilder pb = new ProcessBuilder(epmdExec.toString(), "-names");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // epmd -names output: "name <nodeName> at port <portNumber>"
                    if (line.contains("name " + shortName + " ")) {
                        return true;
                    }
                }
            }
            p.waitFor();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return false;
    }
}
