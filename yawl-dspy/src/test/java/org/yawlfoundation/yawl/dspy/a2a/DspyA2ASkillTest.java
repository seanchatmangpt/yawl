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

package org.yawlfoundation.yawl.dspy.a2a;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yawlfoundation.yawl.dspy.persistence.DspyProgramRegistry;
import org.yawlfoundation.yawl.graalpy.PythonExecutionEngine;
import org.yawlfoundation.yawl.integration.a2a.skills.A2ASkill;
import org.yawlfoundation.yawl.integration.a2a.skills.SkillRequest;
import org.yawlfoundation.yawl.integration.a2a.skills.SkillResult;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DspyA2ASkill A2A skill implementation.
 *
 * <p>Chicago TDD: Tests verify real A2A skill behavior with actual program execution.</p>
 *
 * @author YAWL Foundation
 * @version 6.0.0
 */
final class DspyA2ASkillTest {

    @TempDir
    Path tempDir;

    private Path programsDir;
    private PythonExecutionEngine pythonEngine;
    private DspyProgramRegistry registry;

    @BeforeEach
    void setUp() throws IOException {
        programsDir = tempDir.resolve("programs");
        Files.createDirectories(programsDir);

        pythonEngine = PythonExecutionEngine.builder()
                .contextPoolSize(1)
                .build();

        // Create test programs
        createTestProgram("worklet_selector");
        createTestProgram("resource_router");

        registry = new DspyProgramRegistry(programsDir, pythonEngine);
    }

    @Test
    @DisplayName("Should create skills for all registered programs")
    void shouldCreateSkillsForAllPrograms() {
        // Act
        List<A2ASkill> skills = DspyA2ASkill.createAll(registry);

        // Assert
        assertEquals(2, skills.size());

        List<String> skillIds = skills.stream().map(A2ASkill::getId).toList();
        assertTrue(skillIds.contains("dspy_worklet_selector"));
        assertTrue(skillIds.contains("dspy_resource_router"));
    }

    @Test
    @DisplayName("Should throw on null registry")
    void shouldThrowOnNullRegistry() {
        // Act & Assert
        assertThrows(NullPointerException.class, () ->
                DspyA2ASkill.createAll(null));
    }

    @Test
    @DisplayName("Should have correct skill properties")
    void shouldHaveCorrectSkillProperties() {
        // Arrange
        DspyA2ASkill skill = new DspyA2ASkill(
                "test_program",
                "Test Program Skill",
                "Description for test program",
                registry
        );

        // Assert
        assertEquals("dspy_test_program", skill.getId());
        assertEquals("Test Program Skill", skill.getName());
        assertEquals("Description for test program", skill.getDescription());
        assertEquals(Set.of("dspy:execute"), skill.getRequiredPermissions());
        assertTrue(skill.getTags().contains("dspy"));
        assertTrue(skill.getTags().contains("ml"));
    }

    @Test
    @DisplayName("Should execute skill and return result")
    void shouldExecuteSkillAndReturnResult() {
        // Arrange
        DspyA2ASkill skill = new DspyA2ASkill(
                "worklet_selector",
                "Worklet Selector Skill",
                "Selects the best worklet",
                registry
        );

        SkillRequest request = SkillRequest.builder("dspy_worklet_selector")
                .parameter("context", "Task: Review, Case: urgent")
                .build();

        // Act
        SkillResult result = skill.execute(request);

        // Assert
        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertTrue(result.getExecutionTimeMs() >= 0);
    }

    @Test
    @DisplayName("Should return error result on unknown program")
    void shouldReturnErrorOnUnknownProgram() {
        // Arrange
        DspyA2ASkill skill = new DspyA2ASkill(
                "unknown_program",
                "Unknown Program",
                "This program does not exist",
                registry
        );

        SkillRequest request = SkillRequest.builder("dspy_unknown_program")
                .parameter("context", "test")
                .build();

        // Act
        SkillResult result = skill.execute(request);

        // Assert
        assertTrue(result.isError());
        assertNotNull(result.getError());
    }

    @Test
    @DisplayName("Should include execution metrics in result")
    void shouldIncludeExecutionMetricsInResult() {
        // Arrange
        DspyA2ASkill skill = new DspyA2ASkill(
                "worklet_selector",
                "Worklet Selector",
                "Selects worklet",
                registry
        );

        SkillRequest request = SkillRequest.builder("dspy_worklet_selector")
                .parameter("context", "test context")
                .build();

        // Act
        SkillResult result = skill.execute(request);

        // Assert
        assertTrue(result.isSuccess());
        assertTrue(result.getData().containsKey("execution_time_ms"));
        assertTrue((Long) result.getData().get("execution_time_ms") >= 0);
    }

    @Test
    @DisplayName("Should throw on null constructor parameters")
    void shouldThrowOnNullConstructorParameters() {
        // Act & Assert
        assertThrows(NullPointerException.class, () ->
                new DspyA2ASkill(null, "Name", "Desc", registry));

        assertThrows(NullPointerException.class, () ->
                new DspyA2ASkill("program", null, "Desc", registry));

        assertThrows(NullPointerException.class, () ->
                new DspyA2ASkill("program", "Name", null, registry));

        assertThrows(NullPointerException.class, () ->
                new DspyA2ASkill("program", "Name", "Desc", null));
    }

    @Test
    @DisplayName("Should use predefined skill configurations for known programs")
    void shouldUsePredefinedSkillConfigurations() throws IOException {
        // Arrange
        createTestProgram("anomaly_forensics");
        createTestProgram("runtime_adaptation");
        registry = new DspyProgramRegistry(programsDir, pythonEngine);

        // Act
        List<A2ASkill> skills = DspyA2ASkill.createAll(registry);

        // Find the predefined skills
        A2ASkill forensicsSkill = skills.stream()
                .filter(s -> s.getId().equals("dspy_anomaly_forensics"))
                .findFirst()
                .orElseThrow();

        A2ASkill adaptationSkill = skills.stream()
                .filter(s -> s.getId().equals("dspy_runtime_adaptation"))
                .findFirst()
                .orElseThrow();

        // Assert
        assertEquals("DSPy Anomaly Forensics", forensicsSkill.getName());
        assertTrue(forensicsSkill.getDescription().contains("Root cause"));

        assertEquals("DSPy Runtime Adaptation", adaptationSkill.getName());
        assertTrue(adaptationSkill.getDescription().contains("ReAct"));
    }

    @Test
    @DisplayName("Should generate default configuration for unknown programs")
    void shouldGenerateDefaultConfigurationForUnknownPrograms() throws IOException {
        // Arrange
        createTestProgram("custom_ml_program");
        registry = new DspyProgramRegistry(programsDir, pythonEngine);

        // Act
        List<A2ASkill> skills = DspyA2ASkill.createAll(registry);

        A2ASkill customSkill = skills.stream()
                .filter(s -> s.getId().equals("dspy_custom_ml_program"))
                .findFirst()
                .orElseThrow();

        // Assert
        assertEquals("DSPy Custom Ml Program", customSkill.getName());
        assertTrue(customSkill.getDescription().contains("GEPA-optimized"));
    }

    @Test
    @DisplayName("Should check permissions correctly")
    void shouldCheckPermissionsCorrectly() {
        // Arrange
        DspyA2ASkill skill = new DspyA2ASkill(
                "test_program",
                "Test",
                "Test skill",
                registry
        );

        // Assert
        assertTrue(skill.canExecute(Set.of("dspy:execute")));
        assertTrue(skill.canExecute(Set.of("*")));  // Wildcard
        assertFalse(skill.canExecute(Set.of("other:permission")));
    }

    @Test
    @DisplayName("Should handle empty parameters gracefully")
    void shouldHandleEmptyParametersGracefully() {
        // Arrange
        DspyA2ASkill skill = new DspyA2ASkill(
                "worklet_selector",
                "Worklet Selector",
                "Selects worklet",
                registry
        );

        SkillRequest request = SkillRequest.builder("dspy_worklet_selector")
                .build();  // No parameters

        // Act
        SkillResult result = skill.execute(request);

        // Assert - Should still execute (program handles empty inputs)
        // The result depends on the DSPy program's behavior
        assertNotNull(result);
    }

    // Helper methods

    private void createTestProgram(String name) throws IOException {
        String json = createProgramJson(name);
        Files.writeString(programsDir.resolve(name + ".json"), json);
    }

    private String createProgramJson(String name) {
        return String.format("""
                {
                  "name": "%s",
                  "version": "1.0.0",
                  "dspy_version": "2.5.0",
                  "source_hash": "hash_%s_123",
                  "predictors": {
                    "classify": {
                      "signature": {
                        "instructions": "Instructions for %s",
                        "input_fields": [{"name": "context"}],
                        "output_fields": [{"name": "result"}, {"name": "confidence"}]
                      },
                      "demos": []
                    }
                  },
                  "metadata": {
                    "optimizer": "GEPA",
                    "val_score": 0.95
                  }
                }
                """, name, name, name);
    }
}
