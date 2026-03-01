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
package org.yawlfoundation.yawl.erlang;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Manages a real Erlang OTP node process for integration tests.
 *
 * <p>Requires OTP 28 installed at one of:
 * <ul>
 *   <li>{@code .erlmcp/otp-28.3.1/bin/erl} (relative to project root)</li>
 *   <li>System PATH ({@code erl} command)</li>
 * </ul>
 *
 * <p>Integration tests should guard with:
 * <pre>{@code
 *   Assumptions.assumeTrue(ErlangTestNode.isOtpAvailable(), "OTP 28 not installed");
 * }</pre>
 *
 * <p>The node starts with:
 * <ul>
 *   <li>Name: {@code yawl_test@127.0.0.1}</li>
 *   <li>Cookie: {@code test_cookie}</li>
 *   <li>Code path: compiled beam directory</li>
 * </ul>
 */
public final class ErlangTestNode implements AutoCloseable {

    /** Node name used by all integration tests. */
    public static final String NODE_NAME = "yawl_test@127.0.0.1";

    /** Distribution cookie used by all integration tests. */
    public static final String COOKIE = "test_cookie";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(5);

    private final Process erlProcess;

    private ErlangTestNode(Process erlProcess) {
        this.erlProcess = erlProcess;
    }

    /**
     * Returns {@code true} if an Erlang/OTP executable is available.
     * Used in {@code Assumptions.assumeTrue(ErlangTestNode.isOtpAvailable())} guards.
     */
    public static boolean isOtpAvailable() {
        return findErlBin() != null;
    }

    /**
     * Starts a new Erlang OTP node for integration testing.
     *
     * <p>Launches: {@code erl -name yawl_test@127.0.0.1 -setcookie test_cookie
     *                         -pa &lt;ebin&gt; -eval "application:start(yawl)" -noshell -noinput}
     *
     * @return a running ErlangTestNode
     * @throws IOException if the process cannot be started
     * @throws IllegalStateException if OTP is not available
     */
    public static ErlangTestNode start() throws IOException {
        String erlBin = findErlBin();
        if (erlBin == null) {
            throw new IllegalStateException("OTP 28 not available. "
                    + "Install it at .erlmcp/otp-28.3.1/bin/erl or on PATH.");
        }

        String ebinPath = resolveEbinPath();

        List<String> command = new ArrayList<>();
        command.add(erlBin);
        command.add("-name");
        command.add(NODE_NAME);
        command.add("-setcookie");
        command.add(COOKIE);
        command.add("-noshell");
        command.add("-noinput");
        if (ebinPath != null) {
            command.add("-pa");
            command.add(ebinPath);
        }
        command.add("-eval");
        command.add("application:start(yawl)");

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        Thread.ofVirtual().start(() -> {
            try (InputStream is = process.getInputStream()) {
                is.transferTo(InputStream.nullInputStream());
            } catch (IOException ignored) {
            }
        });

        return new ErlangTestNode(process);
    }

    /**
     * Waits until the Erlang node process is alive, with a 5-second timeout.
     *
     * @throws InterruptedException if interrupted while waiting
     * @throws IllegalStateException if the node failed to start within timeout
     */
    public void awaitReady() throws InterruptedException {
        Instant deadline = Instant.now().plus(STARTUP_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            if (!erlProcess.isAlive()) {
                throw new IllegalStateException(
                        "Erlang node process exited with code " + erlProcess.exitValue());
            }
            Thread.sleep(200);
            if (Duration.between(Instant.now(), deadline).toMillis() < 4500) {
                return;
            }
        }
        throw new IllegalStateException("Erlang node did not start within "
                + STARTUP_TIMEOUT.toSeconds() + " seconds");
    }

    /**
     * Returns {@code true} if the underlying Erlang process is still running.
     */
    public boolean isAlive() {
        return erlProcess.isAlive();
    }

    /**
     * Terminates the Erlang node process cleanly.
     */
    @Override
    public void close() {
        if (erlProcess.isAlive()) {
            erlProcess.destroy();
            try {
                if (!erlProcess.waitFor(3, TimeUnit.SECONDS)) {
                    erlProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                erlProcess.destroyForcibly();
            }
        }
    }

    // ================================================================ private

    private static String findErlBin() {
        try {
            ProcessBuilder pb = new ProcessBuilder("erl", "-eval", "halt(0)", "-noshell");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            if (p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0) {
                return "erl";
            }
        } catch (IOException | InterruptedException ignored) {
        }

        String[] candidates = {
            ".erlmcp/otp-28.3.1/bin/erl",
            ".erlmcp/otp-28/bin/erl"
        };
        for (String candidate : candidates) {
            Path p = findProjectRoot().resolve(candidate);
            if (Files.isExecutable(p)) {
                return p.toAbsolutePath().toString();
            }
        }
        return null;
    }

    private static String resolveEbinPath() {
        Path rebar3Ebin = findProjectRoot()
                .resolve("yawl-erlang/_build/default/lib/yawl/ebin");
        if (Files.isDirectory(rebar3Ebin)) {
            return rebar3Ebin.toAbsolutePath().toString();
        }

        Path resourceEbin = findProjectRoot()
                .resolve("yawl-erlang/src/main/resources/org/yawlfoundation/yawl/erlang/ebin");
        if (Files.isDirectory(resourceEbin)) {
            return resourceEbin.toAbsolutePath().toString();
        }
        return null;
    }

    private static Path findProjectRoot() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 5; i++) {
            Path pom = current.resolve("pom.xml");
            if (Files.exists(pom) && current.resolve("yawl-erlang").toFile().isDirectory()) {
                return current;
            }
            Path parent = current.getParent();
            if (parent == null) break;
            current = parent;
        }
        return Paths.get(System.getProperty("user.dir")).toAbsolutePath();
    }
}
