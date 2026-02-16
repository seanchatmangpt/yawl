package org.yawlfoundation.yawl.integration.autonomous;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.integration.autonomous.strategies.DecisionReasoner;
import org.yawlfoundation.yawl.integration.autonomous.strategies.DiscoveryStrategy;
import org.yawlfoundation.yawl.integration.autonomous.strategies.EligibilityReasoner;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for autonomous agent core logic.
 * Tests capability matching, configuration validation, and strategy selection.
 *
 * Chicago TDD: Real agent configuration objects, minimal mocking.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class AgentLogicUnitTest extends TestCase {

    public AgentLogicUnitTest(String name) {
        super(name);
    }

    public void testAgentCapabilityCreation() {
        AgentCapability capability = new AgentCapability("Ordering", "procurement, purchase orders, approvals");

        assertEquals("Domain name should match", "Ordering", capability.getDomainName());
        assertEquals("Description should match", "procurement, purchase orders, approvals",
                capability.getDescription());
    }

    public void testAgentCapabilityValidation() {
        try {
            new AgentCapability(null, "description");
            fail("Should reject null domain name");
        } catch (IllegalArgumentException e) {
            assertTrue("Error should mention domain name", e.getMessage().contains("domainName"));
        }

        try {
            new AgentCapability("", "description");
            fail("Should reject empty domain name");
        } catch (IllegalArgumentException e) {
            assertTrue("Error should mention domain name", e.getMessage().contains("domainName"));
        }

        try {
            new AgentCapability("Ordering", null);
            fail("Should reject null description");
        } catch (IllegalArgumentException e) {
            assertTrue("Error should mention description", e.getMessage().contains("description"));
        }

        try {
            new AgentCapability("Ordering", "   ");
            fail("Should reject whitespace-only description");
        } catch (IllegalArgumentException e) {
            assertTrue("Error should mention description", e.getMessage().contains("description"));
        }
    }

    public void testAgentCapabilityTrimming() {
        AgentCapability capability = new AgentCapability("  Ordering  ", "  procurement, orders  ");

        assertEquals("Domain name should be trimmed", "Ordering", capability.getDomainName());
        assertEquals("Description should be trimmed", "procurement, orders", capability.getDescription());
    }

    public void testAgentCapabilityToString() {
        AgentCapability capability = new AgentCapability("Ordering", "procurement");

        String result = capability.toString();
        assertTrue("toString should contain domain name", result.contains("Ordering"));
        assertTrue("toString should contain description", result.contains("procurement"));
        assertEquals("Should format as 'domain: description'", "Ordering: procurement", result);
    }

    public void testAgentConfigurationBuilder() {
        AgentCapability capability = new AgentCapability("Ordering", "procurement");
        TestDiscoveryStrategy discovery = new TestDiscoveryStrategy();
        TestEligibilityReasoner eligibility = new TestEligibilityReasoner();
        TestDecisionReasoner decision = new TestDecisionReasoner();

        AgentConfiguration config = AgentConfiguration.builder()
                .capability(capability)
                .engineUrl("http://localhost:8080/yawl")
                .username("admin")
                .password("password")
                .port(8091)
                .pollIntervalMs(5000)
                .version("5.2.0")
                .discoveryStrategy(discovery)
                .eligibilityReasoner(eligibility)
                .decisionReasoner(decision)
                .build();

        assertNotNull("Configuration should be created", config);
        assertEquals("Capability should match", capability, config.getCapability());
        assertEquals("Engine URL should match", "http://localhost:8080/yawl", config.getEngineUrl());
        assertEquals("Username should match", "admin", config.getUsername());
        assertEquals("Password should match", "password", config.getPassword());
        assertEquals("Port should match", 8091, config.getPort());
        assertEquals("Poll interval should match", 5000L, config.getPollIntervalMs());
        assertEquals("Version should match", "5.2.0", config.getVersion());
        assertEquals("Agent name should derive from capability", "Ordering", config.getAgentName());
    }

    public void testAgentConfigurationDefaults() {
        AgentCapability capability = new AgentCapability("Ordering", "procurement");
        TestDiscoveryStrategy discovery = new TestDiscoveryStrategy();
        TestEligibilityReasoner eligibility = new TestEligibilityReasoner();
        TestDecisionReasoner decision = new TestDecisionReasoner();

        AgentConfiguration config = AgentConfiguration.builder()
                .capability(capability)
                .engineUrl("http://localhost:8080/yawl")
                .username("admin")
                .password("password")
                .discoveryStrategy(discovery)
                .eligibilityReasoner(eligibility)
                .decisionReasoner(decision)
                .build();

        assertEquals("Default port should be 8091", 8091, config.getPort());
        assertEquals("Default poll interval should be 3000ms", 3000L, config.getPollIntervalMs());
        assertEquals("Default version should be 5.2.0", "5.2.0", config.getVersion());
    }

    public void testAgentConfigurationRequiredFields() {
        AgentCapability capability = new AgentCapability("Ordering", "procurement");
        TestDiscoveryStrategy discovery = new TestDiscoveryStrategy();
        TestEligibilityReasoner eligibility = new TestEligibilityReasoner();
        TestDecisionReasoner decision = new TestDecisionReasoner();

        try {
            AgentConfiguration.builder()
                    .engineUrl("http://localhost:8080/yawl")
                    .username("admin")
                    .password("password")
                    .discoveryStrategy(discovery)
                    .eligibilityReasoner(eligibility)
                    .decisionReasoner(decision)
                    .build();
            fail("Should require capability");
        } catch (IllegalStateException e) {
            assertTrue("Error should mention capability", e.getMessage().contains("capability"));
        }

        try {
            AgentConfiguration.builder()
                    .capability(capability)
                    .username("admin")
                    .password("password")
                    .discoveryStrategy(discovery)
                    .eligibilityReasoner(eligibility)
                    .decisionReasoner(decision)
                    .build();
            fail("Should require engineUrl");
        } catch (IllegalStateException e) {
            assertTrue("Error should mention engineUrl", e.getMessage().contains("engineUrl"));
        }

        try {
            AgentConfiguration.builder()
                    .capability(capability)
                    .engineUrl("http://localhost:8080/yawl")
                    .password("password")
                    .discoveryStrategy(discovery)
                    .eligibilityReasoner(eligibility)
                    .decisionReasoner(decision)
                    .build();
            fail("Should require username");
        } catch (IllegalStateException e) {
            assertTrue("Error should mention username", e.getMessage().contains("username"));
        }

        try {
            AgentConfiguration.builder()
                    .capability(capability)
                    .engineUrl("http://localhost:8080/yawl")
                    .username("admin")
                    .discoveryStrategy(discovery)
                    .eligibilityReasoner(eligibility)
                    .decisionReasoner(decision)
                    .build();
            fail("Should require password");
        } catch (IllegalStateException e) {
            assertTrue("Error should mention password", e.getMessage().contains("password"));
        }

        try {
            AgentConfiguration.builder()
                    .capability(capability)
                    .engineUrl("http://localhost:8080/yawl")
                    .username("admin")
                    .password("password")
                    .eligibilityReasoner(eligibility)
                    .decisionReasoner(decision)
                    .build();
            fail("Should require discoveryStrategy");
        } catch (IllegalStateException e) {
            assertTrue("Error should mention discoveryStrategy", e.getMessage().contains("discoveryStrategy"));
        }

        try {
            AgentConfiguration.builder()
                    .capability(capability)
                    .engineUrl("http://localhost:8080/yawl")
                    .username("admin")
                    .password("password")
                    .discoveryStrategy(discovery)
                    .decisionReasoner(decision)
                    .build();
            fail("Should require eligibilityReasoner");
        } catch (IllegalStateException e) {
            assertTrue("Error should mention eligibilityReasoner", e.getMessage().contains("eligibilityReasoner"));
        }

        try {
            AgentConfiguration.builder()
                    .capability(capability)
                    .engineUrl("http://localhost:8080/yawl")
                    .username("admin")
                    .password("password")
                    .discoveryStrategy(discovery)
                    .eligibilityReasoner(eligibility)
                    .build();
            fail("Should require decisionReasoner");
        } catch (IllegalStateException e) {
            assertTrue("Error should mention decisionReasoner", e.getMessage().contains("decisionReasoner"));
        }
    }

    public void testAgentConfigurationInvalidPollInterval() {
        AgentCapability capability = new AgentCapability("Ordering", "procurement");
        TestDiscoveryStrategy discovery = new TestDiscoveryStrategy();
        TestEligibilityReasoner eligibility = new TestEligibilityReasoner();
        TestDecisionReasoner decision = new TestDecisionReasoner();

        try {
            AgentConfiguration.builder()
                    .capability(capability)
                    .engineUrl("http://localhost:8080/yawl")
                    .username("admin")
                    .password("password")
                    .pollIntervalMs(0)
                    .discoveryStrategy(discovery)
                    .eligibilityReasoner(eligibility)
                    .decisionReasoner(decision)
                    .build();
            fail("Should reject zero poll interval");
        } catch (IllegalStateException e) {
            assertTrue("Error should mention pollIntervalMs", e.getMessage().contains("pollIntervalMs"));
        }

        try {
            AgentConfiguration.builder()
                    .capability(capability)
                    .engineUrl("http://localhost:8080/yawl")
                    .username("admin")
                    .password("password")
                    .pollIntervalMs(-1000)
                    .discoveryStrategy(discovery)
                    .eligibilityReasoner(eligibility)
                    .decisionReasoner(decision)
                    .build();
            fail("Should reject negative poll interval");
        } catch (IllegalStateException e) {
            assertTrue("Error should mention pollIntervalMs", e.getMessage().contains("pollIntervalMs"));
        }
    }

    public void testStrategyIntegration() {
        TestEligibilityReasoner eligibility = new TestEligibilityReasoner();
        TestDecisionReasoner decision = new TestDecisionReasoner();

        WorkItemRecord workItem = new WorkItemRecord();
        workItem.setTaskID("Approve_Purchase_Order");

        eligibility.setEligible(true);
        boolean isEligible = eligibility.isEligible(workItem);
        assertTrue("Should determine eligibility", isEligible);

        decision.setOutput("<output>approved</output>");
        String output = decision.produceOutput(workItem);
        assertEquals("Should produce output", "<output>approved</output>", output);
    }

    public void testMultipleCapabilityFormats() {
        AgentCapability cap1 = new AgentCapability("Ordering", "procurement, purchase orders");
        AgentCapability cap2 = new AgentCapability("Carrier", "shipping, logistics, delivery");
        AgentCapability cap3 = new AgentCapability("Finance", "accounting, invoicing, payments");

        assertEquals("Ordering", cap1.getDomainName());
        assertEquals("Carrier", cap2.getDomainName());
        assertEquals("Finance", cap3.getDomainName());

        assertTrue("Should contain domain-specific terms",
                cap1.getDescription().contains("procurement"));
        assertTrue("Should contain domain-specific terms",
                cap2.getDescription().contains("shipping"));
        assertTrue("Should contain domain-specific terms",
                cap3.getDescription().contains("accounting"));
    }

    public void testConfigurationImmutability() {
        AgentCapability capability = new AgentCapability("Ordering", "procurement");
        TestDiscoveryStrategy discovery = new TestDiscoveryStrategy();
        TestEligibilityReasoner eligibility = new TestEligibilityReasoner();
        TestDecisionReasoner decision = new TestDecisionReasoner();

        AgentConfiguration config = AgentConfiguration.builder()
                .capability(capability)
                .engineUrl("http://localhost:8080/yawl")
                .username("admin")
                .password("password")
                .discoveryStrategy(discovery)
                .eligibilityReasoner(eligibility)
                .decisionReasoner(decision)
                .build();

        AgentCapability retrievedCapability = config.getCapability();
        assertSame("Should return same capability instance", capability, retrievedCapability);
    }

    private static class TestDiscoveryStrategy implements DiscoveryStrategy {
        @Override
        public List<WorkItemRecord> discoverWorkItems(String sessionHandle) {
            return new ArrayList<>();
        }
    }

    private static class TestEligibilityReasoner implements EligibilityReasoner {
        private boolean eligible = false;

        public void setEligible(boolean eligible) {
            this.eligible = eligible;
        }

        @Override
        public boolean isEligible(WorkItemRecord workItem) {
            return eligible;
        }
    }

    private static class TestDecisionReasoner implements DecisionReasoner {
        private String output = "";

        public void setOutput(String output) {
            this.output = output;
        }

        @Override
        public String produceOutput(WorkItemRecord workItem) {
            return output;
        }
    }

    public static Test suite() {
        TestSuite suite = new TestSuite("Autonomous Agent Logic Unit Tests");
        suite.addTestSuite(AgentLogicUnitTest.class);
        return suite;
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }
}
