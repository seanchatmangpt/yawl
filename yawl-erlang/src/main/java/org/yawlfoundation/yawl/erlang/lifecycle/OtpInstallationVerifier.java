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
import java.util.Optional;

/**
 * Discovers OTP 28 installation on the host machine.
 *
 * <p>Discovery priority:
 * <ol>
 *   <li>Environment variable {@code YAWL_OTP_HOME} → use {@code $YAWL_OTP_HOME/bin/erl}</li>
 *   <li>Bundled OTP at {@code .erlmcp/otp-28.3.1/bin/erl} relative to user.home or cwd</li>
 *   <li>System PATH via {@code which erl} / {@code where erl} (Windows)</li>
 * </ol>
 *
 * <p>All methods are static and thread-safe. No state is maintained between calls.
 */
public final class OtpInstallationVerifier {

    private OtpInstallationVerifier() {}

    /**
     * Finds the OTP bin directory (containing {@code erl}).
     *
     * @return Optional of the bin directory path, empty if OTP is not found
     */
    public static Optional<Path> findOtpBin() {
        // Priority 1: YAWL_OTP_HOME environment variable
        String yawlOtpHome = System.getenv("YAWL_OTP_HOME");
        if (yawlOtpHome != null && !yawlOtpHome.isBlank()) {
            Path binDir = Path.of(yawlOtpHome).resolve("bin");
            if (Files.isDirectory(binDir) && Files.isExecutable(binDir.resolve("erl"))) {
                return Optional.of(binDir);
            }
        }

        // Priority 2: .erlmcp bundled OTP
        for (String base : new String[]{System.getProperty("user.home"), System.getProperty("user.dir")}) {
            if (base == null) continue;
            Path bundled = Path.of(base).resolve(".erlmcp").resolve("otp-28.3.1").resolve("bin");
            if (Files.isDirectory(bundled) && Files.isExecutable(bundled.resolve("erl"))) {
                return Optional.of(bundled);
            }
        }

        // Priority 3: System PATH
        try {
            boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
            ProcessBuilder pb = new ProcessBuilder(isWindows ? "where" : "which", "erl");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = reader.readLine();
                if (line != null && !line.isBlank()) {
                    Path erlPath = Path.of(line.trim());
                    if (Files.isExecutable(erlPath)) {
                        return Optional.of(erlPath.getParent());
                    }
                }
            }
            p.waitFor();
        } catch (IOException | InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        return Optional.empty();
    }

    /**
     * Returns true if OTP 28 (or any OTP) is available on this machine.
     * Fast check — does not verify the OTP version.
     *
     * @return true if {@code erl} is discoverable
     */
    public static boolean isOtp28Available() {
        return findOtpBin().isPresent();
    }

    /**
     * Returns the OTP version string by running {@code erl -eval 'erlang:display(erlang:system_info(otp_release)), halt()' -noshell}.
     *
     * @return OTP release string (e.g. "28")
     * @throws OtpNodeUnavailableException if OTP is not installed or version cannot be determined
     */
    public static String getOtpVersion() throws OtpNodeUnavailableException {
        Optional<Path> binOpt = findOtpBin();
        if (binOpt.isEmpty()) {
            throw new OtpNodeUnavailableException(
                "OTP not found. Set YAWL_OTP_HOME or install Erlang/OTP 28.");
        }
        Path erl = binOpt.get().resolve("erl");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                erl.toString(),
                "-eval", "erlang:display(erlang:system_info(otp_release)), halt()",
                "-noshell"
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line = reader.readLine();
                p.waitFor();
                if (line != null && !line.isBlank()) {
                    return line.trim().replace("\"", "");
                }
            }
            throw new OtpNodeUnavailableException("Could not read OTP version from: " + erl);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OtpNodeUnavailableException("Failed to run erl to get OTP version: " + e.getMessage(), e);
        }
    }

    /**
     * Finds the ei.h header file, which is required for Panama FFM compilation.
     * Searches relative to the OTP installation root.
     *
     * @return Optional path to ei.h, empty if not found
     */
    public static Optional<Path> findEiHeader() {
        return findOtpBin().flatMap(bin -> {
            // bin is $OTP_ROOT/bin — go up one level to get $OTP_ROOT
            Path otpRoot = bin.getParent();
            // Standard location: $OTP_ROOT/usr/include/ei.h
            Path eiHeader = otpRoot.resolve("usr").resolve("include").resolve("ei.h");
            if (Files.isRegularFile(eiHeader)) {
                return Optional.of(eiHeader);
            }
            // Alternative: $OTP_ROOT/lib/erl_interface-*/include/ei.h
            try {
                Path eiInterface = otpRoot.resolve("lib");
                if (Files.isDirectory(eiInterface)) {
                    try (var stream = Files.list(eiInterface)) {
                        return stream
                            .filter(p -> p.getFileName().toString().startsWith("erl_interface"))
                            .map(p -> p.resolve("include").resolve("ei.h"))
                            .filter(Files::isRegularFile)
                            .findFirst();
                    }
                }
            } catch (IOException ignored) {}
            return Optional.empty();
        });
    }
}
