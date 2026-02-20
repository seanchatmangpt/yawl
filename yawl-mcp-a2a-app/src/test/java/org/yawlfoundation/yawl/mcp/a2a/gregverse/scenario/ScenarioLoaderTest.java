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

package org.yawlfoundation.yawl.mcp.a2a.gregverse.scenario;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ScenarioLoader class.
 *
 * <p>Tests verify YAML scenario loading, parsing, and validation.</p>
 */
@DisplayName("GregVerse Scenario Loader Tests")
class ScenarioLoaderTest {

    @Test
    @DisplayName("should load startup idea scenario")
    void testLoadStartupIdeaScenario() {
        Scenario scenario = ScenarioLoader.loadScenario("gvs-1-startup-idea");
        assertNotNull(scenario);
        assertEquals("gvs-1-startup-idea", scenario.id());
    }

    @Test
    @DisplayName("should load content business scenario")
    void testLoadContentBusinessScenario() {
        Scenario scenario = ScenarioLoader.loadScenario("gvs-2-content-business");
        assertNotNull(scenario);
        assertEquals("gvs-2-content-business", scenario.id());
    }

    @Test
    @DisplayName("should load API infrastructure scenario")
    void testLoadApiInfrastructureScenario() {
        Scenario scenario = ScenarioLoader.loadScenario("gvs-3-api-infrastructure");
        assertNotNull(scenario);
        assertEquals("gvs-3-api-infrastructure", scenario.id());
    }

    @Test
    @DisplayName("should load skill transaction scenario")
    void testLoadSkillTransactionScenario() {
        Scenario scenario = ScenarioLoader.loadScenario("gvs-4-skill-transaction");
        assertNotNull(scenario);
        assertEquals("gvs-4-skill-transaction", scenario.id());
    }

    @Test
    @DisplayName("should load product launch scenario")
    void testLoadProductLaunchScenario() {
        Scenario scenario = ScenarioLoader.loadScenario("gvs-5-product-launch");
        assertNotNull(scenario);
        assertEquals("gvs-5-product-launch", scenario.id());
    }

    @Test
    @DisplayName("should parse scenario steps")
    void testParseScenarioSteps() {
        Scenario scenario = ScenarioLoader.loadScenario("gvs-1-startup-idea");
        assertNotNull(scenario.steps());
        assertFalse(scenario.steps().isEmpty());
    }

    @Test
    @DisplayName("should extract agents from scenario")
    void testExtractAgentsFromScenario() {
        Scenario scenario = ScenarioLoader.loadScenario("gvs-1-startup-idea");
        assertNotNull(scenario.agents());
        assertTrue(scenario.agents().size() > 0);
    }

    @Test
    @DisplayName("should extract skills from scenario")
    void testExtractSkillsFromScenario() {
        Scenario scenario = ScenarioLoader.loadScenario("gvs-1-startup-idea");
        assertNotNull(scenario.skills());
        assertTrue(scenario.skills().size() > 0);
    }

    @Test
    @DisplayName("should throw on missing scenario")
    void testMissingScenarioThrows() {
        assertThrows(Exception.class, () -> ScenarioLoader.loadScenario("nonexistent-scenario"));
    }

    @Test
    @DisplayName("should validate scenario structure")
    void testScenarioValidation() {
        Scenario scenario = ScenarioLoader.loadScenario("gvs-1-startup-idea");
        assertNotNull(scenario.id());
        assertNotNull(scenario.title());
        assertNotNull(scenario.description());
    }

    @Test
    @DisplayName("should load scenario with valid YAML")
    void testYamlValidation() {
        String[] scenarios = {
            "gvs-1-startup-idea",
            "gvs-2-content-business",
            "gvs-3-api-infrastructure",
            "gvs-4-skill-transaction",
            "gvs-5-product-launch"
        };

        for (String scenarioId : scenarios) {
            Scenario scenario = ScenarioLoader.loadScenario(scenarioId);
            assertNotNull(scenario, "Failed to load " + scenarioId);
            assertNotNull(scenario.id());
            assertNotNull(scenario.steps());
        }
    }
}
