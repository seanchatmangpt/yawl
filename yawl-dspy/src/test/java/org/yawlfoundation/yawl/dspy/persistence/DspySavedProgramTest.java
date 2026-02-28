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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DspySavedProgram record and JSON deserialization.
 *
 * <p>Chicago TDD: Tests verify real JSON parsing behavior, no mocks.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
final class DspySavedProgramTest {

    @TempDir
    Path tempDir;

    private Path testProgramFile;

    @BeforeEach
    void setUp() throws IOException {
        testProgramFile = tempDir.resolve("test_program.json");
    }

    @Test
    @DisplayName("Should load program from valid JSON file")
    void shouldLoadProgramFromValidJson() throws IOException {
        // Arrange: Create a valid DSPy program JSON
        String json = """
                {
                  "name": "worklet_selector",
                  "version": "1.0.0",
                  "dspy_version": "2.5.0",
                  "source_hash": "abc123def456",
                  "predictors": {
                    "classify": {
                      "signature": {
                        "instructions": "Select the best worklet based on context",
                        "input_fields": [{"name": "context", "desc": "Task context"}],
                        "output_fields": [
                          {"name": "worklet_id", "desc": "Selected worklet"},
                          {"name": "confidence", "desc": "Confidence score"}
                        ]
                      },
                      "demos": [
                        {"context": "Task A", "worklet_id": "FastTrack", "confidence": "0.9"}
                      ],
                      "learned_instructions": "Optimized prompt for worklet selection"
                    }
                  },
                  "metadata": {
                    "optimizer": "GEPA",
                    "val_score": 0.95,
                    "train_size": 100
                  },
                  "serialized_at": "2026-02-27T10:00:00Z"
                }
                """;
        Files.writeString(testProgramFile, json);

        // Act: Load the program
        DspySavedProgram program = DspySavedProgram.loadFromJson(testProgramFile);

        // Assert: Verify all fields loaded correctly
        assertEquals("worklet_selector", program.name());
        assertEquals("1.0.0", program.version());
        assertEquals("2.5.0", program.dspyVersion());
        assertEquals("abc123def456", program.sourceHash());
        assertNotNull(program.loadedAt());
        assertEquals(testProgramFile, program.sourcePath());

        // Verify predictors
        assertEquals(1, program.predictorCount());
        assertTrue(program.predictors().containsKey("classify"));

        // Verify metadata
        assertEquals("GEPA", program.optimizerType());
        assertEquals(0.95, program.validationScore(), 0.001);
    }

    @Test
    @DisplayName("Should throw on missing required fields")
    void shouldThrowOnMissingRequiredFields() throws IOException {
        // Arrange: JSON missing required 'name' field
        String json = """
                {
                  "version": "1.0.0",
                  "source_hash": "abc123"
                }
                """;
        Files.writeString(testProgramFile, json);

        // Act & Assert: Should throw IllegalArgumentException
        assertThrows(IllegalArgumentException.class, () ->
                DspySavedProgram.loadFromJson(testProgramFile));
    }

    @Test
    @DisplayName("Should handle missing optional fields gracefully")
    void shouldHandleMissingOptionalFieldsGracefully() throws IOException {
        // Arrange: Minimal valid JSON
        String json = """
                {
                  "name": "minimal_program",
                  "version": "1.0.0",
                  "source_hash": "hash123"
                }
                """;
        Files.writeString(testProgramFile, json);

        // Act
        DspySavedProgram program = DspySavedProgram.loadFromJson(testProgramFile);

        // Assert: Optional fields should have defaults
        assertNull(program.dspyVersion());
        assertNull(program.serializedAt());
        assertEquals(0, program.predictorCount());
        assertEquals("unknown", program.optimizerType());
        assertEquals(0.0, program.validationScore(), 0.001);
    }

    @Test
    @DisplayName("Should throw on file not found")
    void shouldThrowOnFileNotFound() {
        // Arrange: Non-existent file
        Path nonExistent = tempDir.resolve("non_existent.json");

        // Act & Assert
        assertThrows(IOException.class, () ->
                DspySavedProgram.loadFromJson(nonExistent));
    }

    @Test
    @DisplayName("Should throw on invalid JSON")
    void shouldThrowOnInvalidJson() throws IOException {
        // Arrange: Invalid JSON syntax
        Files.writeString(testProgramFile, "{ invalid json }");

        // Act & Assert
        assertThrows(IOException.class, () ->
                DspySavedProgram.loadFromJson(testProgramFile));
    }

    @Test
    @DisplayName("Should return first predictor correctly")
    void shouldReturnFirstPredictorCorrectly() throws IOException {
        // Arrange
        String json = createValidProgramJson();
        Files.writeString(testProgramFile, json);

        DspySavedProgram program = DspySavedProgram.loadFromJson(testProgramFile);

        // Act
        DspyPredictorConfig first = program.firstPredictor();

        // Assert
        assertNotNull(first);
        assertEquals("classify", program.predictors().keySet().iterator().next());
    }

    @Test
    @DisplayName("Should throw on firstPredictor when no predictors exist")
    void shouldThrowOnFirstPredictorWhenNoPredictors() throws IOException {
        // Arrange: Program with no predictors
        String json = """
                {
                  "name": "empty_program",
                  "version": "1.0.0",
                  "source_hash": "hash123",
                  "predictors": {}
                }
                """;
        Files.writeString(testProgramFile, json);

        DspySavedProgram program = DspySavedProgram.loadFromJson(testProgramFile);

        // Act & Assert
        assertThrows(IllegalStateException.class, program::firstPredictor);
    }

    @Test
    @DisplayName("Should get predictor by name")
    void shouldGetPredictorByName() throws IOException {
        // Arrange
        String json = createValidProgramJson();
        Files.writeString(testProgramFile, json);

        DspySavedProgram program = DspySavedProgram.loadFromJson(testProgramFile);

        // Act
        DspyPredictorConfig config = program.getPredictor("classify");

        // Assert
        assertNotNull(config);
        assertTrue(config.getInputFieldNames().contains("context"));
        assertTrue(config.getOutputFieldNames().contains("worklet_id"));
    }

    @Test
    @DisplayName("Should throw on unknown predictor name")
    void shouldThrowOnUnknownPredictorName() throws IOException {
        // Arrange
        String json = createValidProgramJson();
        Files.writeString(testProgramFile, json);

        DspySavedProgram program = DspySavedProgram.loadFromJson(testProgramFile);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () ->
                program.getPredictor("unknown_predictor"));
    }

    @Test
    @DisplayName("Should generate correct cache key")
    void shouldGenerateCorrectCacheKey() throws IOException {
        // Arrange
        String json = createValidProgramJson();
        Files.writeString(testProgramFile, json);

        DspySavedProgram program = DspySavedProgram.loadFromJson(testProgramFile);

        // Act
        String cacheKey = program.cacheKey();

        // Assert
        assertEquals("worklet_selector:abc123def456", cacheKey);
    }

    @Test
    @DisplayName("Should include predictors in summary")
    void shouldIncludePredictorsInSummary() throws IOException {
        // Arrange
        String json = createValidProgramJson();
        Files.writeString(testProgramFile, json);

        DspySavedProgram program = DspySavedProgram.loadFromJson(testProgramFile);

        // Act
        String summary = program.summary();

        // Assert
        assertTrue(summary.contains("worklet_selector"));
        assertTrue(summary.contains("predictors=1"));
    }

    @Test
    @DisplayName("Should validate record compact constructor")
    void shouldValidateRecordCompactConstructor() {
        // Act & Assert: Null name should throw
        assertThrows(NullPointerException.class, () ->
                new DspySavedProgram(
                        null, "1.0.0", "2.5.0", "hash",
                        Map.of(), Map.of(), null, Instant.now(), null
                ));

        // Blank name should throw
        assertThrows(IllegalArgumentException.class, () ->
                new DspySavedProgram(
                        "  ", "1.0.0", "2.5.0", "hash",
                        Map.of(), Map.of(), null, Instant.now(), null
                ));

        // Null version should throw
        assertThrows(NullPointerException.class, () ->
                new DspySavedProgram(
                        "test", null, "2.5.0", "hash",
                        Map.of(), Map.of(), null, Instant.now(), null
                ));

        // Null source hash should throw
        assertThrows(NullPointerException.class, () ->
                new DspySavedProgram(
                        "test", "1.0.0", "2.5.0", null,
                        Map.of(), Map.of(), null, Instant.now(), null
                ));
    }

    @Test
    @DisplayName("Should handle multiple predictors")
    void shouldHandleMultiplePredictors() throws IOException {
        // Arrange: JSON with multiple predictors
        String json = """
                {
                  "name": "multi_predictor_program",
                  "version": "1.0.0",
                  "source_hash": "multi123",
                  "predictors": {
                    "classify": {
                      "signature": {
                        "instructions": "First predictor",
                        "input_fields": [{"name": "input1"}],
                        "output_fields": [{"name": "output1"}]
                      },
                      "demos": []
                    },
                    "analyze": {
                      "signature": {
                        "instructions": "Second predictor",
                        "input_fields": [{"name": "input2"}],
                        "output_fields": [{"name": "output2"}]
                      },
                      "demos": []
                    }
                  },
                  "metadata": {}
                }
                """;
        Files.writeString(testProgramFile, json);

        // Act
        DspySavedProgram program = DspySavedProgram.loadFromJson(testProgramFile);

        // Assert
        assertEquals(2, program.predictorCount());
        assertTrue(program.predictors().containsKey("classify"));
        assertTrue(program.predictors().containsKey("analyze"));
    }

    private String createValidProgramJson() {
        return """
                {
                  "name": "worklet_selector",
                  "version": "1.0.0",
                  "dspy_version": "2.5.0",
                  "source_hash": "abc123def456",
                  "predictors": {
                    "classify": {
                      "signature": {
                        "instructions": "Select the best worklet",
                        "input_fields": [{"name": "context", "desc": "Task context"}],
                        "output_fields": [
                          {"name": "worklet_id"},
                          {"name": "confidence"}
                        ]
                      },
                      "demos": [],
                      "learned_instructions": "Optimized instructions"
                    }
                  },
                  "metadata": {
                    "optimizer": "GEPA",
                    "val_score": 0.95
                  }
                }
                """;
    }
}
