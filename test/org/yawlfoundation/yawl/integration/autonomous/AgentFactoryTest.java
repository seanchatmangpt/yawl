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
 * Tests for AgentFactory.
 * Chicago TDD style - test real agent creation patterns.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class AgentFactoryTest extends TestCase {

    private AgentConfiguration testConfig;
    private AgentCapability capability;

    public AgentFactoryTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() {
        capability = new AgentCapability("Ordering", "procurement, purchase orders");

        testConfig = AgentConfiguration.builder()
            .capability(capability)
            .engineUrl("http://localhost:8080/yawl")
            .username("admin")
            .password("YAWL")
            .port(18093)
            .discoveryStrategy(new TestDiscoveryStrategy())
            .eligibilityReasoner(new TestEligibilityReasoner())
            .decisionReasoner(new TestDecisionReasoner())
            .build();
    }

    public void testFactoryCannotBeInstantiated() {
        try {
            java.lang.reflect.Constructor<?> constructor =
                AgentFactory.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            constructor.newInstance();
            fail("Factory should not be instantiable");
        } catch (Exception e) {
            assertTrue(e.getCause() instanceof UnsupportedOperationException);
            assertTrue(e.getCause().getMessage().contains("utility class"));
        }
    }

    public void testCreateFromConfiguration() throws Exception {
        AutonomousAgent agent = AgentFactory.create(testConfig);

        assertNotNull("Agent should not be null", agent);
        assertTrue("Agent should be GenericPartyAgent",
                  agent instanceof GenericPartyAgent);
        assertEquals("Capability should match", capability, agent.getCapability());
        assertFalse("Agent should not be running", agent.isRunning());
    }

    public void testCreateRejectsNullConfiguration() {
        try {
            AgentFactory.create(null);
            fail("Should reject null configuration");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("configuration is required"));
        } catch (IOException e) {
            fail("Should throw IllegalArgumentException, not IOException");
        }
    }

    public void testCreateReturnsUniqueInstances() throws Exception {
        AutonomousAgent agent1 = AgentFactory.create(testConfig);
        AutonomousAgent agent2 = AgentFactory.create(testConfig);

        assertNotNull(agent1);
        assertNotNull(agent2);
        assertNotSame("Should create different instances", agent1, agent2);
    }

    public void testCreatedAgentHasCorrectConfiguration() throws Exception {
        AgentConfiguration config = AgentConfiguration.builder()
            .capability(new AgentCapability("Shipping", "logistics"))
            .engineUrl("http://test:9090/yawl")
            .username("testuser")
            .password("testpass")
            .port(19094)
            .version("6.0.0")
            .discoveryStrategy(new TestDiscoveryStrategy())
            .eligibilityReasoner(new TestEligibilityReasoner())
            .decisionReasoner(new TestDecisionReasoner())
            .build();

        AutonomousAgent agent = AgentFactory.create(config);

        assertNotNull(agent);
        assertEquals("Shipping", agent.getCapability().getDomainName());
        assertEquals("6.0.0", agent.getConfiguration().getVersion());
        assertEquals(19094, agent.getConfiguration().getPort());
    }

    public void testCreatedAgentCanStart() throws Exception {
        AutonomousAgent agent = AgentFactory.create(testConfig);

        assertFalse(agent.isRunning());

        agent.start();

        assertTrue(agent.isRunning());

        agent.stop();
    }

    public void testCreateWithMinimalConfiguration() throws Exception {
        AgentConfiguration minimalConfig = AgentConfiguration.builder()
            .capability(new AgentCapability("Test", "test"))
            .engineUrl("http://localhost:8080/yawl")
            .username("admin")
            .password("YAWL")
            .discoveryStrategy(new TestDiscoveryStrategy())
            .eligibilityReasoner(new TestEligibilityReasoner())
            .decisionReasoner(new TestDecisionReasoner())
            .build();

        AutonomousAgent agent = AgentFactory.create(minimalConfig);

        assertNotNull(agent);
        assertEquals(8091, agent.getConfiguration().getPort());
        assertEquals("5.2.0", agent.getConfiguration().getVersion());
    }

    public void testCreateWithAllConfigurationOptions() throws Exception {
        AgentConfiguration fullConfig = AgentConfiguration.builder()
            .capability(new AgentCapability("Finance", "accounting, invoicing"))
            .engineUrl("http://production:8080/yawl")
            .username("finance-agent")
            .password("secure-password")
            .port(9999)
            .version("7.0.0")
            .pollIntervalMs(1000)
            .discoveryStrategy(new TestDiscoveryStrategy())
            .eligibilityReasoner(new TestEligibilityReasoner())
            .decisionReasoner(new TestDecisionReasoner())
            .build();

        AutonomousAgent agent = AgentFactory.create(fullConfig);

        assertNotNull(agent);
        assertEquals("Finance", agent.getCapability().getDomainName());
        assertEquals("accounting, invoicing", agent.getCapability().getDescription());
        assertEquals(9999, agent.getConfiguration().getPort());
        assertEquals("7.0.0", agent.getConfiguration().getVersion());
        assertEquals(1000, agent.getConfiguration().getPollIntervalMs());
    }

    public void testMultipleAgentsCanBeCreatedSimultaneously() throws Exception {
        AgentConfiguration config1 = AgentConfiguration.builder()
            .capability(new AgentCapability("Agent1", "domain1"))
            .engineUrl("http://localhost:8080/yawl")
            .username("admin")
            .password("YAWL")
            .port(18095)
            .discoveryStrategy(new TestDiscoveryStrategy())
            .eligibilityReasoner(new TestEligibilityReasoner())
            .decisionReasoner(new TestDecisionReasoner())
            .build();

        AgentConfiguration config2 = AgentConfiguration.builder()
            .capability(new AgentCapability("Agent2", "domain2"))
            .engineUrl("http://localhost:8080/yawl")
            .username("admin")
            .password("YAWL")
            .port(18096)
            .discoveryStrategy(new TestDiscoveryStrategy())
            .eligibilityReasoner(new TestEligibilityReasoner())
            .decisionReasoner(new TestDecisionReasoner())
            .build();

        AutonomousAgent agent1 = AgentFactory.create(config1);
        AutonomousAgent agent2 = AgentFactory.create(config2);

        assertNotNull(agent1);
        assertNotNull(agent2);
        assertNotSame(agent1, agent2);

        assertEquals("Agent1", agent1.getCapability().getDomainName());
        assertEquals("Agent2", agent2.getCapability().getDomainName());
    }

    public void testCreateWithCustomStrategies() throws Exception {
        CustomDiscoveryStrategy discoveryStrategy = new CustomDiscoveryStrategy();
        CustomEligibilityReasoner eligibilityReasoner = new CustomEligibilityReasoner();
        CustomDecisionReasoner decisionReasoner = new CustomDecisionReasoner();

        AgentConfiguration config = AgentConfiguration.builder()
            .capability(new AgentCapability("Custom", "custom domain"))
            .engineUrl("http://localhost:8080/yawl")
            .username("admin")
            .password("YAWL")
            .port(18097)
            .discoveryStrategy(discoveryStrategy)
            .eligibilityReasoner(eligibilityReasoner)
            .decisionReasoner(decisionReasoner)
            .build();

        AutonomousAgent agent = AgentFactory.create(config);

        assertNotNull(agent);
        assertEquals(discoveryStrategy, agent.getConfiguration().getDiscoveryStrategy());
        assertEquals(eligibilityReasoner, agent.getConfiguration().getEligibilityReasoner());
        assertEquals(decisionReasoner, agent.getConfiguration().getDecisionReasoner());
    }

    public void testAgentCardAvailableAfterCreation() throws Exception {
        AutonomousAgent agent = AgentFactory.create(testConfig);

        String card = agent.getAgentCard();

        assertNotNull(card);
        assertTrue(card.contains("Ordering"));
        assertTrue(card.contains("procurement"));
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

    private static class CustomDiscoveryStrategy implements DiscoveryStrategy {
        @Override
        public List<WorkItemRecord> discoverWorkItems(
                InterfaceB_EnvironmentBasedClient interfaceBClient,
                String sessionHandle) throws IOException {
            return Collections.emptyList();
        }
    }

    private static class CustomEligibilityReasoner implements EligibilityReasoner {
        @Override
        public boolean isEligible(WorkItemRecord workItem) {
            return false;
        }
    }

    private static class CustomDecisionReasoner implements DecisionReasoner {
        @Override
        public String produceOutput(WorkItemRecord workItem) {
            return "<custom/>";
        }
    }
}
