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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Chicago TDD unit tests for {@link PythonVirtualEnvironment}.
 *
 * <p>Tests venv detection, requirements validation, and lock file generation —
 * all without requiring the GraalPy executable on the PATH.</p>
 */
@DisplayName("PythonVirtualEnvironment")
class PythonVirtualEnvironmentTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ── isInitialised() ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("isInitialised() returns false for empty directory")
    void isInitialisedReturnsFalseForEmptyDir(@TempDir Path tempDir) throws Exception {
        PythonVirtualEnvironment venv = buildVenvFromExistingDir(tempDir);
        assertFalse(venv.isInitialised());
    }

    @Test
    @DisplayName("isInitialised() returns true when bin/python exists (Unix)")
    void isInitialisedReturnsTrueWhenUnixPythonExists(@TempDir Path tempDir) throws Exception {
        Path binDir = tempDir.resolve("bin");
        Files.createDirectories(binDir);
        Files.createFile(binDir.resolve("python"));

        PythonVirtualEnvironment venv = buildVenvFromExistingDir(tempDir);
        assertTrue(venv.isInitialised());
    }

    @Test
    @DisplayName("isInitialised() returns true when Scripts/python.exe exists (Windows)")
    void isInitialisedReturnsTrueWhenWindowsPythonExists(@TempDir Path tempDir) throws Exception {
        Path scriptsDir = tempDir.resolve("Scripts");
        Files.createDirectories(scriptsDir);
        Files.createFile(scriptsDir.resolve("python.exe"));

        PythonVirtualEnvironment venv = buildVenvFromExistingDir(tempDir);
        assertTrue(venv.isInitialised());
    }

    @Test
    @DisplayName("isInitialised() returns false when only bin directory exists without python binary")
    void isInitialisedReturnsFalseWhenBinDirExistsButNoPython(@TempDir Path tempDir) throws Exception {
        Files.createDirectories(tempDir.resolve("bin"));

        PythonVirtualEnvironment venv = buildVenvFromExistingDir(tempDir);
        assertFalse(venv.isInitialised());
    }

    // ── getVenvDir() / getPythonExecutable() accessors ───────────────────────────

    @Test
    @DisplayName("getVenvDir() returns the directory passed to create()")
    void getVenvDirReturnsConfiguredDir(@TempDir Path tempDir) throws Exception {
        Files.createDirectories(tempDir.resolve("bin"));
        Files.createFile(tempDir.resolve("bin").resolve("python"));

        PythonVirtualEnvironment venv = PythonVirtualEnvironment.create(tempDir, "graalpy");
        assertThat(venv.getVenvDir(), is(tempDir));
    }

    @Test
    @DisplayName("getPythonExecutable() returns the executable name passed to create()")
    void getPythonExecutableReturnsConfiguredName(@TempDir Path tempDir) throws Exception {
        Files.createDirectories(tempDir.resolve("bin"));
        Files.createFile(tempDir.resolve("bin").resolve("python"));

        PythonVirtualEnvironment venv = PythonVirtualEnvironment.create(tempDir, "graalpy");
        assertThat(venv.getPythonExecutable(), is("graalpy"));
    }

    // ── create() with non-existent executable ────────────────────────────────────

    @Test
    @DisplayName("create() with non-existent graalpy binary throws PythonException")
    void createWithNonExistentExecutableThrowsPythonException(@TempDir Path tempDir) {
        assertThrows(PythonException.class,
                () -> PythonVirtualEnvironment.create(tempDir, "nonexistent-graalpy-xyz-9999"));
    }

    @Test
    @DisplayName("create() with non-existent binary throws PythonException with VENV_ERROR kind")
    void createWithNonExistentBinaryThrowsVenvErrorKind(@TempDir Path tempDir) {
        PythonException ex = assertThrows(PythonException.class,
                () -> PythonVirtualEnvironment.create(tempDir, "nonexistent-graalpy-xyz-9999"));
        assertThat(ex.getErrorKind(), is(PythonException.ErrorKind.VENV_ERROR));
    }

    // ── installRequirements() ─────────────────────────────────────────────────────

    @Test
    @DisplayName("installRequirements() throws PythonException when requirements file not found")
    void installRequirementsThrowsWhenFileNotFound(@TempDir Path tempDir) throws Exception {
        PythonVirtualEnvironment venv = buildVenvFromExistingDir(tempDir);
        Path missingFile = tempDir.resolve("requirements.txt");

        assertThrows(PythonException.class,
                () -> venv.installRequirements(missingFile));
    }

    @Test
    @DisplayName("installRequirements() throws PythonException with VENV_ERROR kind for missing file")
    void installRequirementsThrowsVenvErrorForMissingFile(@TempDir Path tempDir) throws Exception {
        PythonVirtualEnvironment venv = buildVenvFromExistingDir(tempDir);
        Path missingFile = tempDir.resolve("nonexistent-requirements.txt");

        PythonException ex = assertThrows(PythonException.class,
                () -> venv.installRequirements(missingFile));
        assertThat(ex.getErrorKind(), is(PythonException.ErrorKind.VENV_ERROR));
    }

    @Test
    @DisplayName("installRequirements() exception message includes the missing file path")
    void installRequirementsMessageIncludesFilePath(@TempDir Path tempDir) throws Exception {
        PythonVirtualEnvironment venv = buildVenvFromExistingDir(tempDir);
        Path missingFile = tempDir.resolve("requirements.txt");

        PythonException ex = assertThrows(PythonException.class,
                () -> venv.installRequirements(missingFile));
        assertThat(ex.getMessage(), containsString(missingFile.toString()));
    }

    // ── generateLockFile() ────────────────────────────────────────────────────────

    @Test
    @DisplayName("generateLockFile() creates a file at the specified path")
    void generateLockFileCreatesFile(@TempDir Path tempDir) throws Exception {
        PythonVirtualEnvironment venv = buildVenvWithNopPip(tempDir);
        Path lockFile = tempDir.resolve("graalpy.lock");

        venv.generateLockFile(lockFile);

        assertTrue(Files.exists(lockFile));
    }

    @Test
    @DisplayName("generateLockFile() writes valid JSON")
    void generateLockFileWritesValidJson(@TempDir Path tempDir) throws Exception {
        PythonVirtualEnvironment venv = buildVenvWithNopPip(tempDir);
        Path lockFile = tempDir.resolve("graalpy.lock");
        venv.generateLockFile(lockFile);

        JsonNode root = MAPPER.readTree(lockFile.toFile());
        assertThat(root, notNullValue());
        assertTrue(root.isObject());
    }

    @Test
    @DisplayName("generateLockFile() JSON contains required top-level fields")
    void generateLockFileJsonContainsRequiredFields(@TempDir Path tempDir) throws Exception {
        PythonVirtualEnvironment venv = buildVenvWithNopPip(tempDir);
        Path lockFile = tempDir.resolve("graalpy.lock");
        venv.generateLockFile(lockFile);

        JsonNode root = MAPPER.readTree(lockFile.toFile());
        assertTrue(root.has("generated"),         "'generated' field missing");
        assertTrue(root.has("python_executable"), "'python_executable' field missing");
        assertTrue(root.has("venv_dir"),          "'venv_dir' field missing");
        assertTrue(root.has("packages"),          "'packages' field missing");
    }

    @Test
    @DisplayName("generateLockFile() JSON 'packages' is an array")
    void generateLockFileJsonPackagesIsArray(@TempDir Path tempDir) throws Exception {
        PythonVirtualEnvironment venv = buildVenvWithNopPip(tempDir);
        Path lockFile = tempDir.resolve("graalpy.lock");
        venv.generateLockFile(lockFile);

        JsonNode root = MAPPER.readTree(lockFile.toFile());
        assertTrue(root.get("packages").isArray());
    }

    @Test
    @DisplayName("generateLockFile() JSON 'python_executable' matches configured executable")
    void generateLockFileJsonPythonExecutableMatchesConfig(@TempDir Path tempDir) throws Exception {
        PythonVirtualEnvironment venv = buildVenvWithNopPip(tempDir);
        Path lockFile = tempDir.resolve("graalpy.lock");
        venv.generateLockFile(lockFile);

        JsonNode root = MAPPER.readTree(lockFile.toFile());
        assertThat(root.get("python_executable").asText(), is("graalpy"));
    }

    @Test
    @DisplayName("generateLockFile() JSON 'venv_dir' is the absolute path to venv")
    void generateLockFileJsonVenvDirIsAbsolutePath(@TempDir Path tempDir) throws Exception {
        Path venvDir = tempDir.resolve("venv");
        PythonVirtualEnvironment venv = buildVenvWithNopPip(venvDir);
        Path lockFile = tempDir.resolve("graalpy.lock");
        venv.generateLockFile(lockFile);

        JsonNode root = MAPPER.readTree(lockFile.toFile());
        assertThat(root.get("venv_dir").asText(), is(venvDir.toAbsolutePath().toString()));
    }

    // ── Private helpers ───────────────────────────────────────────────────────────

    /**
     * Creates a {@link PythonVirtualEnvironment} by invoking the private constructor
     * directly, bypassing the venv initialisation step that requires a graalpy binary.
     * The venv is pointed at an existing directory and uses "graalpy" as the executable name.
     */
    private static PythonVirtualEnvironment buildVenvFromExistingDir(Path dir) throws Exception {
        var ctor = PythonVirtualEnvironment.class.getDeclaredConstructor(Path.class, String.class);
        ctor.setAccessible(true);
        return (PythonVirtualEnvironment) ctor.newInstance(dir, "graalpy");
    }

    /**
     * Creates a {@link PythonVirtualEnvironment} whose bin/pip is a shell script that
     * exits with code 0 and produces no output (simulating a freshly-created venv with
     * no packages installed). Used to test {@code generateLockFile()} without a real
     * GraalPy installation.
     */
    private static PythonVirtualEnvironment buildVenvWithNopPip(Path venvDir) throws Exception {
        Path binDir = venvDir.resolve("bin");
        Files.createDirectories(binDir);

        // A real shell script: succeeds immediately, outputs nothing — models pip freeze on empty venv
        Path pipScript = binDir.resolve("pip");
        Files.writeString(pipScript, "#!/bin/sh\nexit 0\n");
        pipScript.toFile().setExecutable(true);
        Files.createFile(binDir.resolve("python"));

        return buildVenvFromExistingDir(venvDir);
    }
}
