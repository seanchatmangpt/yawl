/*
 * Copyright (c) 2004-2020 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 *
 * YAWL is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 */

package org.yawlfoundation.yawl.integration.autonomous;

import org.junit.jupiter.api.Tag;

import junit.framework.TestCase;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.integration.autonomous.strategies.DecisionReasoner;
import org.yawlfoundation.yawl.integration.autonomous.strategies.DiscoveryStrategy;
import org.yawlfoundation.yawl.integration.autonomous.strategies.EligibilityReasoner;

import java.util.Collections;
import java.util.List;

/**
 * Integration tests for AgentConfiguration builder (V6 feature).
 *
 * Chicago TDD: tests real AgentConfiguration construction through the
 * builder with no mocks. Uses real strategy implementations.
 *
 * Coverage targets:
 * - Builder validation (all required fields)
 * - Default value assignment
 * - Complete configuration access
 * - Builder guard conditions
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@Tag("unit")
public class AgentConfigurationTest extends TestCase {

    // Real strategy implementations for testing (no mocks)
    private static final DiscoveryStrategy NOOP_DISCOVERY =
        (client, session) -> Collections.emptyList();
    // Note: lambda implements throws IOException from interface signature

    private static final EligibilityReasoner ALWAYS_ELIGIBLE =
        workItem -> true;

    private static final DecisionReasoner EMPTY_OUTPUT =
        workItem -> "<" + workItem.getTaskID() + "/>";

    private final AgentCapability TEST_CAPABILITY =
        new AgentCapability("TestDomain", "test domain for unit tests");

    public AgentConfigurationTest(String name) {
        super(name);
    }

    // =========================================================================
    // Successful builder construction
    // =========================================================================

    public void testBuildWithAllRequiredFields() {
        AgentConfiguration config = AgentConfiguration.builder()
            .capability(TEST_CAPABILITY)
            .engineUrl("http://localhost:8080/yawl")
            .username("admin")
            .password("YAWL")
            .discoveryStrategy(NOOP_DISCOVERY)
            .eligibilityReasoner(ALWAYS_ELIGIBLE)
            .decisionReasoner(EMPTY_OUTPUT)
            .build();

        assertNotNull("Configuration should not be null", config);
        assertEquals(TEST_CAPABILITY, config.getCapability());
        assertEquals("http://localhost:8080/yawl", config.getEngineUrl());
        assertEquals("admin", config.getUsername());
        assertEquals("YAWL", config.getPassword());
        assertNotNull(config.getDiscoveryStrategy());
        assertNotNull(config.getEligibilityReasoner());
        assertNotNull(config.getDecisionReasoner());
    }

    public void testDefaultPortIs8091() {
        AgentConfiguration config = buildMinimalConfig();
        assertEquals("Default port should be 8091", 8091, config.getPort());
    }

    public void testDefaultPollIntervalIs3000Ms() {
        AgentConfiguration config = buildMinimalConfig();
        assertEquals("Default poll interval should be 3000ms", 3000L, config.getPollIntervalMs());
    }

    public void testDefaultVersionIs5_2() {
        AgentConfiguration config = buildMinimalConfig();
        assertEquals("Default version should be 5.2.0", "5.2.0", config.getVersion());
    }

    public void testCustomPortSetting() {
        AgentConfiguration config = AgentConfiguration.builder()
            .capability(TEST_CAPABILITY)
            .engineUrl("http://localhost:8080/yawl")
            .username("admin")
            .password("YAWL")
            .discoveryStrategy(NOOP_DISCOVERY)
            .eligibilityReasoner(ALWAYS_ELIGIBLE)
            .decisionReasoner(EMPTY_OUTPUT)
            .port(9090)
            .build();

        assertEquals(9090, config.getPort());
    }

    public void testCustomPollIntervalSetting() {
        AgentConfiguration config = AgentConfiguration.builder()
            .capability(TEST_CAPABILITY)
            .engineUrl("http://localhost:8080/yawl")
            .username("admin")
            .password("YAWL")
            .discoveryStrategy(NOOP_DISCOVERY)
            .eligibilityReasoner(ALWAYS_ELIGIBLE)
            .decisionReasoner(EMPTY_OUTPUT)
            .pollIntervalMs(5000)
            .build();

        assertEquals(5000L, config.getPollIntervalMs());
    }

    public void testCustomVersionSetting() {
        AgentConfiguration config = AgentConfiguration.builder()
            .capability(TEST_CAPABILITY)
            .engineUrl("http://localhost:8080/yawl")
            .username("admin")
            .password("YAWL")
            .discoveryStrategy(NOOP_DISCOVERY)
            .eligibilityReasoner(ALWAYS_ELIGIBLE)
            .decisionReasoner(EMPTY_OUTPUT)
            .version("6.0.0")
            .build();

        assertEquals("6.0.0", config.getVersion());
    }

    public void testAgentNameDelegatesToCapabilityDomainName() {
        AgentConfiguration config = buildMinimalConfig();
        assertEquals("TestDomain", config.getAgentName());
    }

    // =========================================================================
    // Builder guard conditions
    // =========================================================================

    public void testBuildWithoutCapabilityThrows() {
        try {
            AgentConfiguration.builder()
                .engineUrl("http://localhost:8080/yawl")
                .username("admin")
                .password("YAWL")
                .discoveryStrategy(NOOP_DISCOVERY)
                .eligibilityReasoner(ALWAYS_ELIGIBLE)
                .decisionReasoner(EMPTY_OUTPUT)
                .build();
            fail("Expected IllegalStateException for missing capability");
        } catch (IllegalStateException e) {
            assertTrue("Message should mention capability",
                    e.getMessage().toLowerCase().contains("capability"));
        }
    }

    public void testBuildWithoutEngineUrlThrows() {
        try {
            AgentConfiguration.builder()
                .capability(TEST_CAPABILITY)
                .username("admin")
                .password("YAWL")
                .discoveryStrategy(NOOP_DISCOVERY)
                .eligibilityReasoner(ALWAYS_ELIGIBLE)
                .decisionReasoner(EMPTY_OUTPUT)
                .build();
            fail("Expected IllegalStateException for missing engineUrl");
        } catch (IllegalStateException e) {
            assertTrue("Message should mention engineUrl",
                    e.getMessage().toLowerCase().contains("engineurl") ||
                    e.getMessage().toLowerCase().contains("engine"));
        }
    }

    public void testBuildWithoutUsernameThrows() {
        try {
            AgentConfiguration.builder()
                .capability(TEST_CAPABILITY)
                .engineUrl("http://localhost:8080/yawl")
                .password("YAWL")
                .discoveryStrategy(NOOP_DISCOVERY)
                .eligibilityReasoner(ALWAYS_ELIGIBLE)
                .decisionReasoner(EMPTY_OUTPUT)
                .build();
            fail("Expected IllegalStateException for missing username");
        } catch (IllegalStateException e) {
            assertTrue("Message should mention username or password",
                    e.getMessage().toLowerCase().contains("username") ||
                    e.getMessage().toLowerCase().contains("password"));
        }
    }

    public void testBuildWithoutDiscoveryStrategyThrows() {
        try {
            AgentConfiguration.builder()
                .capability(TEST_CAPABILITY)
                .engineUrl("http://localhost:8080/yawl")
                .username("admin")
                .password("YAWL")
                .eligibilityReasoner(ALWAYS_ELIGIBLE)
                .decisionReasoner(EMPTY_OUTPUT)
                .build();
            fail("Expected IllegalStateException for missing discoveryStrategy");
        } catch (IllegalStateException e) {
            assertTrue("Message should mention discoveryStrategy",
                    e.getMessage().toLowerCase().contains("discovery"));
        }
    }

    public void testBuildWithoutEligibilityReasonerThrows() {
        try {
            AgentConfiguration.builder()
                .capability(TEST_CAPABILITY)
                .engineUrl("http://localhost:8080/yawl")
                .username("admin")
                .password("YAWL")
                .discoveryStrategy(NOOP_DISCOVERY)
                .decisionReasoner(EMPTY_OUTPUT)
                .build();
            fail("Expected IllegalStateException for missing eligibilityReasoner");
        } catch (IllegalStateException e) {
            assertTrue("Message should mention eligibilityReasoner",
                    e.getMessage().toLowerCase().contains("eligibility"));
        }
    }

    public void testBuildWithoutDecisionReasonerThrows() {
        try {
            AgentConfiguration.builder()
                .capability(TEST_CAPABILITY)
                .engineUrl("http://localhost:8080/yawl")
                .username("admin")
                .password("YAWL")
                .discoveryStrategy(NOOP_DISCOVERY)
                .eligibilityReasoner(ALWAYS_ELIGIBLE)
                .build();
            fail("Expected IllegalStateException for missing decisionReasoner");
        } catch (IllegalStateException e) {
            assertTrue("Message should mention decisionReasoner",
                    e.getMessage().toLowerCase().contains("decision"));
        }
    }

    public void testBuildWithZeroPollIntervalThrows() {
        try {
            AgentConfiguration.builder()
                .capability(TEST_CAPABILITY)
                .engineUrl("http://localhost:8080/yawl")
                .username("admin")
                .password("YAWL")
                .discoveryStrategy(NOOP_DISCOVERY)
                .eligibilityReasoner(ALWAYS_ELIGIBLE)
                .decisionReasoner(EMPTY_OUTPUT)
                .pollIntervalMs(0)
                .build();
            fail("Expected IllegalStateException for zero pollIntervalMs");
        } catch (IllegalStateException e) {
            assertTrue("Message should mention pollIntervalMs",
                    e.getMessage().toLowerCase().contains("poll"));
        }
    }

    public void testBuildWithNegativePollIntervalThrows() {
        try {
            AgentConfiguration.builder()
                .capability(TEST_CAPABILITY)
                .engineUrl("http://localhost:8080/yawl")
                .username("admin")
                .password("YAWL")
                .discoveryStrategy(NOOP_DISCOVERY)
                .eligibilityReasoner(ALWAYS_ELIGIBLE)
                .decisionReasoner(EMPTY_OUTPUT)
                .pollIntervalMs(-1000)
                .build();
            fail("Expected IllegalStateException for negative pollIntervalMs");
        } catch (IllegalStateException e) {
            assertTrue("Message should mention pollIntervalMs",
                    e.getMessage().toLowerCase().contains("poll"));
        }
    }

    // =========================================================================
    // Strategy accessor tests
    // =========================================================================

    public void testDiscoveryStrategyIsAccessible() throws java.io.IOException {
        AgentConfiguration config = buildMinimalConfig();
        DiscoveryStrategy strategy = config.getDiscoveryStrategy();
        assertNotNull(strategy);
        // Test real strategy execution (NOOP_DISCOVERY returns empty list)
        List<WorkItemRecord> items = strategy.discoverWorkItems(null, null);
        assertNotNull(items);
        assertTrue(items.isEmpty());
    }

    public void testEligibilityReasonerIsAccessible() {
        AgentConfiguration config = buildMinimalConfig();
        EligibilityReasoner reasoner = config.getEligibilityReasoner();
        assertNotNull(reasoner);
        WorkItemRecord wir = new WorkItemRecord("case1", "task1",
                "http://spec", WorkItemRecord.statusEnabled);
        assertTrue("Always-eligible reasoner should return true",
                reasoner.isEligible(wir));
    }

    public void testDecisionReasonerIsAccessible() {
        AgentConfiguration config = buildMinimalConfig();
        DecisionReasoner reasoner = config.getDecisionReasoner();
        assertNotNull(reasoner);
        WorkItemRecord wir = new WorkItemRecord("case1", "task1",
                "http://spec", WorkItemRecord.statusEnabled);
        String output = reasoner.produceOutput(wir);
        assertNotNull(output);
        assertFalse(output.isEmpty());
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private AgentConfiguration buildMinimalConfig() {
        return AgentConfiguration.builder()
            .capability(TEST_CAPABILITY)
            .engineUrl("http://localhost:8080/yawl")
            .username("admin")
            .password("YAWL")
            .discoveryStrategy(NOOP_DISCOVERY)
            .eligibilityReasoner(ALWAYS_ELIGIBLE)
            .decisionReasoner(EMPTY_OUTPUT)
            .build();
    }
}
