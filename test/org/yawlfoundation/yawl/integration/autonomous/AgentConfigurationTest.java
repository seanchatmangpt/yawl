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

import junit.framework.TestCase;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.engine.interfce.interfaceB.InterfaceB_EnvironmentBasedClient;
import org.yawlfoundation.yawl.integration.autonomous.strategies.DecisionReasoner;
import org.yawlfoundation.yawl.integration.autonomous.strategies.DiscoveryStrategy;
import org.yawlfoundation.yawl.integration.autonomous.strategies.EligibilityReasoner;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Tests for AgentConfiguration builder.
 * Chicago TDD style - testing real object construction.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class AgentConfigurationTest extends TestCase {

    private AgentCapability capability;
    private DiscoveryStrategy discoveryStrategy;
    private EligibilityReasoner eligibilityReasoner;
    private DecisionReasoner decisionReasoner;

    public AgentConfigurationTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() {
        capability = new AgentCapability("Test", "test domain");
        discoveryStrategy = new TestDiscoveryStrategy();
        eligibilityReasoner = new TestEligibilityReasoner();
        decisionReasoner = new TestDecisionReasoner();
    }

    public void testBuilderWithAllRequiredFields() {
        AgentConfiguration config = AgentConfiguration.builder()
            .capability(capability)
            .engineUrl("http://localhost:8080/yawl/ia")
            .username("admin")
            .password("YAWL")
            .discoveryStrategy(discoveryStrategy)
            .eligibilityReasoner(eligibilityReasoner)
            .decisionReasoner(decisionReasoner)
            .build();

        assertNotNull(config);
        assertEquals(capability, config.getCapability());
        assertEquals("http://localhost:8080/yawl/ia", config.getEngineUrl());
        assertEquals("admin", config.getUsername());
        assertEquals("YAWL", config.getPassword());
        assertEquals(8091, config.getPort());
        assertEquals("5.2.0", config.getVersion());
        assertEquals(discoveryStrategy, config.getDiscoveryStrategy());
        assertEquals(eligibilityReasoner, config.getEligibilityReasoner());
        assertEquals(decisionReasoner, config.getDecisionReasoner());
    }

    public void testBuilderWithCustomPort() {
        AgentConfiguration config = AgentConfiguration.builder()
            .capability(capability)
            .engineUrl("http://localhost:8080/yawl/ia")
            .username("admin")
            .password("YAWL")
            .port(9090)
            .discoveryStrategy(discoveryStrategy)
            .eligibilityReasoner(eligibilityReasoner)
            .decisionReasoner(decisionReasoner)
            .build();

        assertEquals(9090, config.getPort());
    }

    public void testBuilderWithCustomVersion() {
        AgentConfiguration config = AgentConfiguration.builder()
            .capability(capability)
            .engineUrl("http://localhost:8080/yawl/ia")
            .username("admin")
            .password("YAWL")
            .version("6.0.0")
            .discoveryStrategy(discoveryStrategy)
            .eligibilityReasoner(eligibilityReasoner)
            .decisionReasoner(decisionReasoner)
            .build();

        assertEquals("6.0.0", config.getVersion());
    }

    public void testBuilderRejectsMissingCapability() {
        try {
            AgentConfiguration.builder()
                .engineUrl("http://localhost:8080/yawl/ia")
                .username("admin")
                .password("YAWL")
                .discoveryStrategy(discoveryStrategy)
                .eligibilityReasoner(eligibilityReasoner)
                .decisionReasoner(decisionReasoner)
                .build();
            fail("Should reject missing capability");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("capability is required"));
        }
    }

    public void testBuilderRejectsMissingEngineUrl() {
        try {
            AgentConfiguration.builder()
                .capability(capability)
                .username("admin")
                .password("YAWL")
                .discoveryStrategy(discoveryStrategy)
                .eligibilityReasoner(eligibilityReasoner)
                .decisionReasoner(decisionReasoner)
                .build();
            fail("Should reject missing engineUrl");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("engineUrl is required"));
        }
    }

    public void testBuilderRejectsEmptyEngineUrl() {
        try {
            AgentConfiguration.builder()
                .capability(capability)
                .engineUrl("")
                .username("admin")
                .password("YAWL")
                .discoveryStrategy(discoveryStrategy)
                .eligibilityReasoner(eligibilityReasoner)
                .decisionReasoner(decisionReasoner)
                .build();
            fail("Should reject empty engineUrl");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("engineUrl is required"));
        }
    }

    public void testBuilderRejectsMissingUsername() {
        try {
            AgentConfiguration.builder()
                .capability(capability)
                .engineUrl("http://localhost:8080/yawl/ia")
                .password("YAWL")
                .discoveryStrategy(discoveryStrategy)
                .eligibilityReasoner(eligibilityReasoner)
                .decisionReasoner(decisionReasoner)
                .build();
            fail("Should reject missing username");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("username and password are required"));
        }
    }

    public void testBuilderRejectsMissingPassword() {
        try {
            AgentConfiguration.builder()
                .capability(capability)
                .engineUrl("http://localhost:8080/yawl/ia")
                .username("admin")
                .discoveryStrategy(discoveryStrategy)
                .eligibilityReasoner(eligibilityReasoner)
                .decisionReasoner(decisionReasoner)
                .build();
            fail("Should reject missing password");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("username and password are required"));
        }
    }

    public void testBuilderRejectsMissingDiscoveryStrategy() {
        try {
            AgentConfiguration.builder()
                .capability(capability)
                .engineUrl("http://localhost:8080/yawl/ia")
                .username("admin")
                .password("YAWL")
                .eligibilityReasoner(eligibilityReasoner)
                .decisionReasoner(decisionReasoner)
                .build();
            fail("Should reject missing discoveryStrategy");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("discoveryStrategy is required"));
        }
    }

    public void testBuilderRejectsMissingEligibilityReasoner() {
        try {
            AgentConfiguration.builder()
                .capability(capability)
                .engineUrl("http://localhost:8080/yawl/ia")
                .username("admin")
                .password("YAWL")
                .discoveryStrategy(discoveryStrategy)
                .decisionReasoner(decisionReasoner)
                .build();
            fail("Should reject missing eligibilityReasoner");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("eligibilityReasoner is required"));
        }
    }

    public void testBuilderRejectsMissingDecisionReasoner() {
        try {
            AgentConfiguration.builder()
                .capability(capability)
                .engineUrl("http://localhost:8080/yawl/ia")
                .username("admin")
                .password("YAWL")
                .discoveryStrategy(discoveryStrategy)
                .eligibilityReasoner(eligibilityReasoner)
                .build();
            fail("Should reject missing decisionReasoner");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("decisionReasoner is required"));
        }
    }

    private static class TestDiscoveryStrategy implements DiscoveryStrategy {
        @Override
        public List<WorkItemRecord> discoverWorkItems(
                InterfaceB_EnvironmentBasedClient interfaceBClient,
                String sessionHandle) throws IOException {
            return Collections.emptyList();
        }
    }

    private static class TestEligibilityReasoner implements EligibilityReasoner {
        @Override
        public boolean isEligible(WorkItemRecord workItem) {
            return true;
        }
    }

    private static class TestDecisionReasoner implements DecisionReasoner {
        @Override
        public String produceOutput(WorkItemRecord workItem) {
            return "<data/>";
        }
    }
}
