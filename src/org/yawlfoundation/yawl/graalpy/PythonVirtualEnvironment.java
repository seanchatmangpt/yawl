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

package org.yawlfoundation.yawl.graalpy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages a GraalPy Python virtual environment (venv) for dependency isolation.
 *
 * <p>Automates creation and package installation for GraalPy virtual environments,
 * mirroring the Maven build phase integration described in the PRD. Supports
 * reproducible builds via {@code graalpy.lock} file generation.</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * PythonVirtualEnvironment venv = PythonVirtualEnvironment.create(
 *     Path.of("/app/python-venv"),
 *     "graalpy"   // or "python3" for CPython
 * );
 * venv.installRequirements(Path.of("requirements.txt"));
 * venv.generateLockFile(Path.of("graalpy.lock"));
 * }</pre>
 *
 * <h2>Build integration</h2>
 * <p>For Maven builds, configure the {@code graalpy-maven-plugin} in your
 * {@code pom.xml} to run venv creation in the {@code generate-resources} phase.
 * This class provides the Java API equivalent for runtime management.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
public final class PythonVirtualEnvironment {

    private static final Logger log = LoggerFactory.getLogger(PythonVirtualEnvironment.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path venvDir;
    private final String pythonExecutable;

    private PythonVirtualEnvironment(Path venvDir, String pythonExecutable) {
        this.venvDir = venvDir;
        this.pythonExecutable = pythonExecutable;
    }

    /**
     * Creates (or verifies) a GraalPy virtual environment at the given directory.
     *
     * <p>If the venv directory already exists and contains a valid Python interpreter,
     * creation is skipped. Otherwise, {@code graalpy -m venv <venvDir>} is executed.</p>
     *
     * @param venvDir  directory where the venv should be created; parent must exist
     * @param pythonExecutable  the GraalPy executable name or path (e.g. {@code "graalpy"})
     * @return an initialised {@code PythonVirtualEnvironment}
     * @throws PythonException  if the executable is not found or venv creation fails
     */
    public static PythonVirtualEnvironment create(Path venvDir, String pythonExecutable) {
        PythonVirtualEnvironment venv = new PythonVirtualEnvironment(venvDir, pythonExecutable);
        if (!venv.isInitialised()) {
            venv.initialise();
        }
        return venv;
    }

    /**
     * Installs Python packages listed in a {@code requirements.txt} file into this venv.
     *
     * <p>Runs {@code pip install -r <requirementsFile>} using this venv's pip.</p>
     *
     * @param requirementsFile  path to a {@code requirements.txt}; must exist
     * @throws PythonException  if the file does not exist or pip install fails
     */
    public void installRequirements(Path requirementsFile) {
        if (!Files.exists(requirementsFile)) {
            throw new PythonException(
                    "requirements.txt not found: " + requirementsFile,
                    PythonException.ErrorKind.VENV_ERROR);
        }
        log.info("Installing packages from {}", requirementsFile);
        runPip(List.of("install", "-r", requirementsFile.toAbsolutePath().toString()));
        log.info("Package installation complete");
    }

    /**
     * Installs a single Python package into this venv.
     *
     * @param packageSpec  package name with optional version (e.g. {@code "numpy==1.26.4"})
     * @throws PythonException  if pip install fails
     */
    public void installPackage(String packageSpec) {
        log.info("Installing package: {}", packageSpec);
        runPip(List.of("install", packageSpec));
    }

    /**
     * Generates a {@code graalpy.lock} file capturing the exact versions of all
     * installed packages.
     *
     * <p>The lock file is a JSON file with format:</p>
     * <pre>{@code
     * {
     *   "generated": "2026-02-26T12:00:00Z",
     *   "python_executable": "graalpy",
     *   "packages": [
     *     {"name": "numpy", "version": "1.26.4"},
     *     ...
     *   ]
     * }
     * }</pre>
     *
     * @param lockFile  path where the lock file should be written
     * @throws PythonException  if pip freeze fails or lock file cannot be written
     */
    public void generateLockFile(Path lockFile) {
        log.info("Generating lock file at {}", lockFile);
        List<String> installedPackages = listInstalledPackages();

        ObjectNode root = MAPPER.createObjectNode();
        root.put("generated", Instant.now().toString());
        root.put("python_executable", pythonExecutable);
        root.put("venv_dir", venvDir.toAbsolutePath().toString());

        ArrayNode packages = root.putArray("packages");
        for (String packageLine : installedPackages) {
            // Format: "package==version"
            String[] parts = packageLine.split("==", 2);
            if (parts.length == 2) {
                ObjectNode pkg = packages.addObject();
                pkg.put("name", parts[0].trim());
                pkg.put("version", parts[1].trim());
            }
        }

        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(lockFile.toFile(), root);
            log.info("Lock file generated: {} ({} packages)", lockFile, installedPackages.size());
        } catch (IOException e) {
            throw new PythonException(
                    "Cannot write lock file to " + lockFile + ": " + e.getMessage(),
                    PythonException.ErrorKind.VENV_ERROR, e);
        }
    }

    /**
     * Returns the list of installed packages in {@code name==version} format.
     *
     * @return list of package specifiers; never null
     * @throws PythonException  if pip freeze fails
     */
    public List<String> listInstalledPackages() {
        return runPip(List.of("freeze"));
    }

    /** Returns the path to the venv directory. */
    public Path getVenvDir() { return venvDir; }

    /** Returns the Python executable name or path used by this venv. */
    public String getPythonExecutable() { return pythonExecutable; }

    /**
     * Returns {@code true} if this venv directory exists and contains a Python interpreter.
     *
     * @return {@code true} if the venv appears initialised
     */
    public boolean isInitialised() {
        Path interpreter = venvDir.resolve("bin").resolve("python");
        Path interpreterWin = venvDir.resolve("Scripts").resolve("python.exe");
        return Files.exists(interpreter) || Files.exists(interpreterWin);
    }

    // ── Private helpers ──────────────────────────────────────────────────────────

    private void initialise() {
        log.info("Creating GraalPy venv at {} using {}", venvDir, pythonExecutable);
        List<String> command = List.of(pythonExecutable, "-m", "venv", venvDir.toAbsolutePath().toString());
        runCommand(command, List.of());
    }

    private List<String> runPip(List<String> pipArgs) {
        Path pip = venvDir.resolve("bin").resolve("pip");
        if (!Files.exists(pip)) {
            pip = venvDir.resolve("Scripts").resolve("pip.exe");
        }
        if (!Files.exists(pip)) {
            throw new PythonException(
                    "pip not found in venv at " + venvDir + ". "
                    + "Ensure the venv is properly initialised.",
                    PythonException.ErrorKind.VENV_ERROR);
        }

        List<String> command = new ArrayList<>();
        command.add(pip.toAbsolutePath().toString());
        command.addAll(pipArgs);

        return runCommand(command, List.of());
    }

    private List<String> runCommand(List<String> command, List<String> expectedOutputLines) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            pb.environment().put("VIRTUAL_ENV", venvDir.toAbsolutePath().toString());

            log.debug("Running: {}", String.join(" ", command));
            Process process = pb.start();

            List<String> outputLines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    outputLines.add(line);
                    log.debug("  > {}", line);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String output = String.join("\n", outputLines);
                throw new PythonException(
                        "Command failed with exit code " + exitCode + ": "
                        + String.join(" ", command) + "\nOutput:\n" + output,
                        PythonException.ErrorKind.VENV_ERROR);
            }
            return outputLines;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new PythonException(
                    "Failed to execute command: " + String.join(" ", command) + " — " + e.getMessage(),
                    PythonException.ErrorKind.VENV_ERROR, e);
        }
    }
}
