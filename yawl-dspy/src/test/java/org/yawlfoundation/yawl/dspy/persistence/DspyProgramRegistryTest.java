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

package org.yawlfoundation.yawl.dspy.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.yawlfoundation.yawl.graalpy.PythonExecutionEngine;
import org.yawlfoundation.yawl.graalpy.PythonException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for DspyProgramRegistry.
 *
 * <p>Chicago TDD: Tests verify real file system and program loading behavior.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DSPy Program Registry Tests")
class DspyProgramRegistryTest {

    @TempDir
    Path tempDir;

    @Mock
    private PythonExecutionEngine mockEngine;

    private Path programsDir;
    private DspyProgramRegistry registry;

    @BeforeEach
    void setUp() throws IOException {
        programsDir = tempDir.resolve("programs");
        Files.createDirectories(programsDir);
    }

    @Test
    @DisplayName("Should initialize with empty directory")
    void shouldInitializeWithEmptyDirectory() {
        // Act
        registry = new DspyProgramRegistry(programsDir, mockEngine);

        // Assert
        assertEquals(0, registry.programCount());
        assertTrue(registry.listProgramNames().isEmpty());
    }

    @Test
    @DisplayName("Should load programs from directory on initialization")
    void shouldLoadProgramsFromDirectoryOnInit() throws IOException {
        // Arrange: Create test program files
        createTestProgram("worklet_selector");
        createTestProgram("resource_router");

        // Act
        registry = new DspyProgramRegistry(programsDir, mockEngine);

        // Assert
        assertEquals(2, registry.programCount());
        assertTrue(registry.hasProgram("worklet_selector"));
        assertTrue(registry.hasProgram("resource_router"));
    }

    @Test
    @DisplayName("Should skip invalid JSON files during initialization")
    void shouldSkipInvalidJsonFilesOnInit() throws IOException {
        // Arrange: Mix of valid and invalid files
        createTestProgram("valid_program");
        Files.writeString(programsDir.resolve("invalid.json"), "{ not valid json }");
        Files.writeString(programsDir.resolve("not_json.txt"), "plain text");

        // Act
        registry = new DspyProgramRegistry(programsDir, mockEngine);

        // Assert: Only valid program loaded
        assertEquals(1, registry.programCount());
        assertTrue(registry.hasProgram("valid_program"));
    }

    @Test
    @DisplayName("Should handle non-existent directory gracefully")
    void shouldHandleNonExistentDirectoryGracefully() {
        // Arrange: Non-existent directory
        Path nonExistent = tempDir.resolve("non_existent");

        // Act: Should not throw
        registry = new DspyProgramRegistry(nonExistent, pythonEngine);

        // Assert
        assertEquals(0, registry.programCount());
    }

    @Test
    @DisplayName("Should load program by name")
    void shouldLoadProgramByName() throws IOException {
        // Arrange
        createTestProgram("test_program");
        registry = new DspyProgramRegistry(programsDir, mockEngine);

        // Act
        Optional<DspySavedProgram> program = registry.load("test_program");

        // Assert
        assertTrue(program.isPresent());
        assertEquals("test_program", program.get().name());
    }

    @Test
    @DisplayName("Should return empty for unknown program")
    void shouldReturnEmptyForUnknownProgram() throws IOException {
        // Arrange
        registry = new DspyProgramRegistry(programsDir, mockEngine);

        // Act
        Optional<DspySavedProgram> program = registry.load("unknown");

        // Assert
        assertFalse(program.isPresent());
    }

    @Test
    @DisplayName("Should throw on null program name")
    void shouldThrowOnNullProgramName() throws IOException {
        // Arrange
        registry = new DspyProgramRegistry(programsDir, mockEngine);

        // Act & Assert
        assertThrows(NullPointerException.class, () -> registry.load(null));
    }

    @Test
    @DisplayName("Should throw DspyProgramNotFoundException when executing unknown program")
    void shouldThrowWhenExecutingUnknownProgram() throws IOException {
        // Arrange
        registry = new DspyProgramRegistry(programsDir, mockEngine);

        // Act & Assert
        assertThrows(DspyProgramNotFoundException.class, () ->
                registry.execute("unknown", Map.of("input", "test")));
    }

    @Test
    @DisplayName("Should throw on null inputs for execution")
    void shouldThrowOnNullInputsForExecution() throws IOException {
        // Arrange
        createTestProgram("test_program");
        registry = new DspyProgramRegistry(programsDir, mockEngine);

        // Act & Assert
        assertThrows(NullPointerException.class, () ->
                registry.execute("test_program", null));
    }

    @Test
    @DisplayName("Should list all program names")
    void shouldListAllProgramNames() throws IOException {
        // Arrange
        createTestProgram("alpha");
        createTestProgram("beta");
        createTestProgram("gamma");
        registry = new DspyProgramRegistry(programsDir, mockEngine);

        // Act
        List<String> names = registry.listProgramNames();

        // Assert
        assertEquals(3, names.size());
        assertTrue(names.contains("alpha"));
        assertTrue(names.contains("beta"));
        assertTrue(names.contains("gamma"));
    }

    @Test
    @DisplayName("Should reload program from disk")
    void shouldReloadProgramFromDisk() throws IOException {
        // Arrange
        createTestProgram("reloadable");
        registry = new DspyProgramRegistry(programsDir, mockEngine);

        // Modify the file
        String updatedJson = createProgramJson("reloadable", "updated_hash_123");
        Files.writeString(programsDir.resolve("reloadable.json"), updatedJson);

        // Act
        DspySavedProgram reloaded = registry.reload("reloadable");

        // Assert
        assertEquals("updated_hash_123", reloaded.sourceHash());
    }

    @Test
    @DisplayName("Should throw when reloading non-existent program")
    void shouldThrowWhenReloadingNonExistentProgram() throws IOException {
        // Arrange
        registry = new DspyProgramRegistry(programsDir, mockEngine);

        // Act & Assert
        assertThrows(DspyProgramNotFoundException.class, () ->
                registry.reload("non_existent"));
    }

    @Test
    @DisplayName("Should reload all programs")
    void shouldReloadAllPrograms() throws IOException {
        // Arrange
        createTestProgram("program1");
        createTestProgram("program2");
        registry = new DspyProgramRegistry(programsDir, mockEngine);

        // Add a new program file after initialization
        createTestProgram("program3");

        // Act
        int count = registry.reloadAll();

        // Assert
        assertEquals(3, count);
        assertTrue(registry.hasProgram("program3"));
    }

    @Test
    @DisplayName("Should return registry stats")
    void shouldReturnRegistryStats() throws IOException {
        // Arrange
        createTestProgram("stats_program");
        registry = new DspyProgramRegistry(programsDir, mockEngine);

        // Act
        Map<String, Object> stats = registry.getStats();

        // Assert
        assertEquals(1, stats.get("programCount"));
        assertEquals(programsDir.toString(), stats.get("programsDir"));
        assertTrue(stats.get("programs") instanceof List);
    }

    @Test
    @DisplayName("Should throw on null registry parameters")
    void shouldThrowOnNullRegistryParameters() {
        // Act & Assert
        assertThrows(NullPointerException.class, () ->
                new DspyProgramRegistry(null, mockEngine));

        assertThrows(NullPointerException.class, () ->
                new DspyProgramRegistry(programsDir, null));
    }

    // Helper methods

    private void createTestProgram(String name) throws IOException {
        String json = createProgramJson(name, "hash_" + name + "_123");
        Files.writeString(programsDir.resolve(name + ".json"), json);
    }

    private String createProgramJson(String name, String hash) {
        return String.format("""
                {
                  "name": "%s",
                  "version": "1.0.0",
                  "dspy_version": "2.5.0",
                  "source_hash": "%s",
                  "predictors": {
                    "classify": {
                      "signature": {
                        "instructions": "Test instructions",
                        "input_fields": [{"name": "context"}],
                        "output_fields": [{"name": "result"}]
                      },
                      "demos": []
                    }
                  },
                  "metadata": {
                    "optimizer": "GEPA",
                    "val_score": 0.95
                  }
                }
                """, name, hash);
    }
}
