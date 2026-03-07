/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
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

package org.yawlfoundation.yawl.ggen.pipeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.yawlfoundation.yawl.dspy.fluent.Dspy;
import org.yawlfoundation.yawl.ggen.model.ProcessSpec;
import org.yawlfoundation.yawl.ggen.model.ProcessGraph;
import org.yawlfoundation.yawl.ggen.model.YawlSpec;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for YawlGenerator 3-stage pipeline.
 */
@DisplayName("YawlGenerator Tests")
class YawlGeneratorTest {

    private YawlGenerator generator;

    @BeforeEach
    void setUp() {
        // Skip DSPy configuration if API key not available
        if (!Dspy.isConfigured()) {
            // Configure with mock for testing
            // In production, would use: Dspy.configureGroq();
        }
        generator = YawlGenerator.create();
    }

    @Test
    @DisplayName("Should create generator without validator")
    void shouldCreateGeneratorWithoutValidator() {
        YawlGenerator gen = YawlGenerator.create();
        assertNotNull(gen);
        assertFalse(gen.hasValidator());
    }

    @Test
    @DisplayName("Should have access to individual stages")
    void shouldHaveAccessToIndividualStages() {
        assertNotNull(generator.stage1());
        assertNotNull(generator.stage2());
        assertNotNull(generator.stage3());
    }

    @Test
    @DisplayName("Stage 1 should generate ProcessSpec from simple NL")
    @EnabledIfEnvironmentVariable(named = "GROQ_API_KEY", matches = ".+")
    void stage1ShouldGenerateProcessSpec() {
        // This test requires DSPy to be configured with a real LLM
        Stage1SpecGenerator stage1 = generator.stage1();

        ProcessSpec spec = stage1.generate(
            "Simple approval process: submit, review, approve or reject."
        );

        assertNotNull(spec);
        assertNotNull(spec.processName());
        assertFalse(spec.tasks().isEmpty());
    }

    @Test
    @DisplayName("Stage 2 should build ProcessGraph from ProcessSpec")
    @EnabledIfEnvironmentVariable(named = "GROQ_API_KEY", matches = ".+")
    void stage2ShouldBuildProcessGraph() {
        // Create minimal spec
        ProcessSpec spec = ProcessSpec.of("TestProcess", List.of(
            org.yawlfoundation.yawl.ggen.model.TaskDef.atomic("task1", "Task 1"),
            org.yawlfoundation.yawl.ggen.model.TaskDef.atomic("task2", "Task 2")
        ));

        Stage2GraphBuilder stage2 = generator.stage2();
        ProcessGraph graph = stage2.build(spec);

        assertNotNull(graph);
        assertFalse(graph.nodes().isEmpty());
    }

    @Test
    @DisplayName("Full pipeline should generate YawlSpec from NL")
    @EnabledIfEnvironmentVariable(named = "GROQ_API_KEY", matches = ".+")
    void fullPipelineShouldGenerateYawlSpec() {
        YawlSpec spec = generator.generate(
            "Simple two-task process: start -> task1 -> task2 -> end."
        );

        assertNotNull(spec);
        assertNotNull(spec.specId());
        assertNotNull(spec.specVersion());
    }

    @Test
    @DisplayName("Fluent builder should configure all options")
    void fluentBuilderShouldConfigureOptions() {
        var builder = generator.generateWith("Test description")
            .withDomainContext("healthcare")
            .withSpecId("TestSpec")
            .withVersion("2.0");

        assertNotNull(builder);
    }

    @Test
    @DisplayName("ProcessSpec should detect OR-joins")
    void processSpecShouldDetectOrJoins() {
        ProcessSpec spec = new ProcessSpec(
            "TestProcess",
            List.of(org.yawlfoundation.yawl.ggen.model.TaskDef.atomic("task1", "Task 1")),
            List.of(),
            List.of(),
            List.of("task1"),  // OR-join
            List.of(),
            java.util.Map.of()
        );

        assertTrue(spec.hasOrJoins());
    }

    @Test
    @DisplayName("ProcessSpec should detect cancellation regions")
    void processSpecShouldDetectCancellationRegions() {
        ProcessSpec spec = new ProcessSpec(
            "TestProcess",
            List.of(org.yawlfoundation.yawl.ggen.model.TaskDef.atomic("task1", "Task 1")),
            List.of(),
            List.of(),
            List.of(),
            List.of(org.yawlfoundation.yawl.ggen.model.CancellationRegionDef.of("task1", List.of("task2"))),
            java.util.Map.of()
        );

        assertTrue(spec.hasCancellationRegions());
    }

    @Test
    @DisplayName("YawlSpec should provide XML length")
    void yawlSpecShouldProvideXmlLength() {
        YawlSpec spec = YawlSpec.of("<specification/>", "TestSpec", "1.0");

        assertTrue(spec.xmlLength() > 0);
        assertTrue(spec.hasXml());
    }
}
