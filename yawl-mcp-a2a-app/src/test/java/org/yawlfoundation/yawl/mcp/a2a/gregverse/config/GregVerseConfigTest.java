/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can redistribute it
 * and/or modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with YAWL. If not, see <http://www.gnu.org/licenses/>.
 */

package org.yawlfoundation.yawl.mcp.a2a.gregverse.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GregVerseConfig class.
 *
 * <p>Tests verify configuration building, defaults, and validation.</p>
 */
@DisplayName("GregVerse Configuration Tests")
class GregVerseConfigTest {

    @Test
    @DisplayName("should create config from scenario ID")
    void testForScenarioConstructor() {
        GregVerseConfig config = GregVerseConfig.forScenario("gvs-1-startup-idea");
        assertNotNull(config);
        assertEquals("gvs-1-startup-idea", config.scenarioId());
    }

    @Test
    @DisplayName("should build config with all options")
    void testBuilderWithAllOptions() {
        GregVerseConfig config = GregVerseConfig.builder()
            .scenarioId("gvs-2-content-business")
            .parallelExecution(true)
            .timeoutDuration(Duration.ofSeconds(30))
            .agentIds("greg-isenberg", "james")
            .singleAgentId("nicolas-cole")
            .singleSkillId("evaluate-skill")
            .skillInput("test input")
            .build();

        assertNotNull(config);
        assertEquals("gvs-2-content-business", config.scenarioId());
        assertTrue(config.parallelExecution());
        assertEquals(Duration.ofSeconds(30), config.getTimeoutDuration());
    }

    @Test
    @DisplayName("should use default timeout duration")
    void testDefaultTimeoutDuration() {
        GregVerseConfig config = GregVerseConfig.forScenario("gvs-1-startup-idea");
        assertNotNull(config.getTimeoutDuration());
        assertTrue(config.getTimeoutDuration().toMillis() > 0);
    }

    @Test
    @DisplayName("should use default parallel execution mode")
    void testDefaultParallelMode() {
        GregVerseConfig config = GregVerseConfig.forScenario("gvs-1-startup-idea");
        assertFalse(config.parallelExecution());
    }

    @Test
    @DisplayName("should validate single skill mode")
    void testSingleSkillMode() {
        GregVerseConfig config = GregVerseConfig.builder()
            .scenarioId("gvs-1-startup-idea")
            .singleAgentId("greg-isenberg")
            .singleSkillId("evaluate-market")
            .build();

        assertTrue(config.isSingleSkillMode());
        assertEquals("greg-isenberg", config.singleAgentId());
        assertEquals("evaluate-market", config.singleSkillId());
    }

    @Test
    @DisplayName("should validate agent filter")
    void testHasAgentFilter() {
        GregVerseConfig filtered = GregVerseConfig.builder()
            .scenarioId("gvs-1-startup-idea")
            .agentIds("james", "nicolas-cole")
            .build();

        assertTrue(filtered.hasAgentFilter());
        assertEquals(2, filtered.agentIds().size());
    }

    @Test
    @DisplayName("should indicate no filter when no agents specified")
    void testNoAgentFilter() {
        GregVerseConfig config = GregVerseConfig.forScenario("gvs-1-startup-idea");
        assertFalse(config.hasAgentFilter());
    }

    @Test
    @DisplayName("should accept custom timeout duration")
    void testCustomTimeoutDuration() {
        Duration custom = Duration.ofSeconds(60);
        GregVerseConfig config = GregVerseConfig.builder()
            .scenarioId("gvs-1-startup-idea")
            .timeoutDuration(custom)
            .build();

        assertEquals(custom, config.getTimeoutDuration());
    }

    @Test
    @DisplayName("should support skill input in config")
    void testSkillInput() {
        String input = "test input data";
        GregVerseConfig config = GregVerseConfig.builder()
            .scenarioId("gvs-1-startup-idea")
            .skillInput(input)
            .build();

        assertEquals(input, config.skillInput());
    }

    @Test
    @DisplayName("should require non-null scenario ID")
    void testNullScenarioIdValidation() {
        assertThrows(Exception.class, () -> GregVerseConfig.forScenario(null));
    }

    @Test
    @DisplayName("should support all scenario identifiers")
    void testAllScenarios() {
        String[] scenarios = {
            "gvs-1-startup-idea",
            "gvs-2-content-business",
            "gvs-3-api-infrastructure",
            "gvs-4-skill-transaction",
            "gvs-5-product-launch"
        };

        for (String scenario : scenarios) {
            GregVerseConfig config = GregVerseConfig.forScenario(scenario);
            assertNotNull(config);
            assertEquals(scenario, config.scenarioId());
        }
    }
}
