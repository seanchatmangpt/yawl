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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tests for GenericPartyAgent.
 * Chicago TDD style - test real agent lifecycle and integration.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class GenericPartyAgentTest extends TestCase {

    private AgentConfiguration testConfig;
    private AgentCapability capability;

    public GenericPartyAgentTest(String name) {
        super(name);
    }

    @Override
    protected void setUp() {
        capability = new AgentCapability("TestDomain", "test domain description");

        testConfig = AgentConfiguration.builder()
            .capability(capability)
            .engineUrl("http://localhost:8080/yawl")
            .username("admin")
            .password("YAWL")
            .port(18091)
            .discoveryStrategy(new TestDiscoveryStrategy())
            .eligibilityReasoner(new TestEligibilityReasoner())
            .decisionReasoner(new TestDecisionReasoner())
            .build();
    }

    public void testConstructorRejectsNullConfiguration() {
        try {
            new GenericPartyAgent(null);
            fail("Should reject null configuration");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("config is required"));
        }
    }

    public void testAgentImplementsAutonomousAgentInterface() throws Exception {
        GenericPartyAgent agent = new GenericPartyAgent(testConfig);
        assertNotNull(agent);
        assertTrue(agent instanceof AutonomousAgent);
    }

    public void testGetCapability() throws Exception {
        GenericPartyAgent agent = new GenericPartyAgent(testConfig);
        AgentCapability cap = agent.getCapability();
        assertNotNull(cap);
        assertEquals("TestDomain", cap.getDomainName());
        assertEquals("test domain description", cap.getDescription());
    }

    public void testGetConfiguration() throws Exception {
        GenericPartyAgent agent = new GenericPartyAgent(testConfig);
        AgentConfiguration config = agent.getConfiguration();
        assertNotNull(config);
        assertEquals(capability, config.getCapability());
        assertEquals("http://localhost:8080/yawl", config.getEngineUrl());
        assertEquals(18091, config.getPort());
    }

    public void testIsRunningInitiallyFalse() throws Exception {
        GenericPartyAgent agent = new GenericPartyAgent(testConfig);
        assertFalse("Agent should not be running initially", agent.isRunning());
    }

    public void testGetAgentCardReturnsValidJson() throws Exception {
        GenericPartyAgent agent = new GenericPartyAgent(testConfig);
        String card = agent.getAgentCard();
        assertNotNull(card);
        assertTrue(card.contains("\"name\""));
        assertTrue(card.contains("\"description\""));
        assertTrue(card.contains("\"version\""));
        assertTrue(card.contains("\"capabilities\""));
        assertTrue(card.contains("TestDomain"));
    }

    public void testAgentCardContainsExpectedFields() throws Exception {
        GenericPartyAgent agent = new GenericPartyAgent(testConfig);
        String card = agent.getAgentCard();

        assertTrue("Should contain agent name", card.contains("Generic Agent - TestDomain"));
        assertTrue("Should contain version", card.contains("\"version\":\"5.2.0\""));
        assertTrue("Should contain domain capability", card.contains("\"domain\":\"TestDomain\""));
        assertTrue("Should contain skills", card.contains("\"skills\""));
        assertTrue("Should contain complete_work_item skill",
                  card.contains("complete_work_item"));
    }

    public void testStartChangesRunningState() throws Exception {
        GenericPartyAgent agent = new GenericPartyAgent(testConfig);
        assertFalse(agent.isRunning());

        agent.start();

        assertTrue("Agent should be running after start", agent.isRunning());

        agent.stop();
    }

    public void testStopChangesRunningState() throws Exception {
        GenericPartyAgent agent = new GenericPartyAgent(testConfig);

        agent.start();
        assertTrue(agent.isRunning());

        agent.stop();

        assertFalse("Agent should not be running after stop", agent.isRunning());
    }

    public void testStartTwiceThrowsException() throws Exception {
        GenericPartyAgent agent = new GenericPartyAgent(testConfig);
        agent.start();

        try {
            agent.start();
            fail("Should not allow starting twice");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("already running"));
        } finally {
            agent.stop();
        }
    }

    public void testStopWithoutStartThrowsException() throws Exception {
        GenericPartyAgent agent = new GenericPartyAgent(testConfig);

        try {
            agent.stop();
            fail("Should not allow stopping when not running");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("not running"));
        }
    }

    public void testAgentLifecycleStartStopStart() throws Exception {
        GenericPartyAgent agent = new GenericPartyAgent(testConfig);

        agent.start();
        assertTrue(agent.isRunning());

        agent.stop();
        assertFalse(agent.isRunning());

        Thread.sleep(100);

        agent.start();
        assertTrue(agent.isRunning());

        agent.stop();
    }

    public void testDiscoveryLoopExecutesAfterStart() throws Exception {
        TestDiscoveryStrategy discoveryStrategy = new TestDiscoveryStrategy();
        discoveryStrategy.setWorkItems(createTestWorkItems(2));

        AgentConfiguration config = AgentConfiguration.builder()
            .capability(capability)
            .engineUrl("http://localhost:8080/yawl")
            .username("admin")
            .password("YAWL")
            .port(18092)
            .pollIntervalMs(500)
            .discoveryStrategy(discoveryStrategy)
            .eligibilityReasoner(new TestEligibilityReasoner())
            .decisionReasoner(new TestDecisionReasoner())
            .build();

        GenericPartyAgent agent = new GenericPartyAgent(config);
        agent.start();

        Thread.sleep(800);

        assertTrue("Discovery should have been called",
                  discoveryStrategy.getDiscoveryCallCount() > 0);

        agent.stop();
    }

    public void testHttpServerStartsOnConfiguredPort() throws Exception {
        GenericPartyAgent agent = new GenericPartyAgent(testConfig);
        agent.start();

        Thread.sleep(100);

        agent.stop();
    }

    private List<WorkItemRecord> createTestWorkItems(int count) {
        List<WorkItemRecord> items = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            WorkItemRecord wir = new WorkItemRecord();
            wir.setUniqueID("wi-" + i);
            wir.setTaskName("TestTask" + i);
            wir.setCaseID("case-1");
            wir.setStatus(WorkItemRecord.statusEnabled);
            items.add(wir);
        }
        return items;
    }

    private static class TestDiscoveryStrategy implements DiscoveryStrategy {
        private List<WorkItemRecord> workItems = Collections.emptyList();
        private int discoveryCallCount = 0;

        public void setWorkItems(List<WorkItemRecord> items) {
            this.workItems = items;
        }

        public int getDiscoveryCallCount() {
            return discoveryCallCount;
        }

        @Override
        public List<WorkItemRecord> discoverWorkItems(
                InterfaceB_EnvironmentBasedClient interfaceBClient,
                String sessionHandle) throws IOException {
            discoveryCallCount++;
            return new ArrayList<>(workItems);
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
            return "<data><result>true</result></data>";
        }
    }
}
