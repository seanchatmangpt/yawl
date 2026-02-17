package org.yawlfoundation.yawl.integration.autonomous;

import org.junit.jupiter.api.Test;
import org.yawlfoundation.yawl.engine.interfce.WorkItemRecord;
import org.yawlfoundation.yawl.integration.autonomous.strategies.DecisionReasoner;
import org.yawlfoundation.yawl.integration.autonomous.strategies.DiscoveryStrategy;
import org.yawlfoundation.yawl.integration.autonomous.strategies.EligibilityReasoner;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for autonomous agent core logic.
 * Tests capability matching, configuration validation, and strategy selection.
 *
 * Chicago TDD: Real agent configuration objects, minimal mocking.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class AgentLogicUnitTest {

    @Test
    void testAgentCapabilityCreation() {
        AgentCapability capability = new AgentCapability("Ordering", "procurement, purchase orders, approvals");

        assertEquals("Ordering", capability.getDomainName(), "Domain name should match");
        assertEquals("procurement, purchase orders, approvals",
                capability.getDescription(), "Description should match");
    }

    @Test
    void testAgentCapabilityValidation() {
        assertThrows(IllegalArgumentException.class, () -> {
            new AgentCapability(null, "description");
        }, "Should reject null domain name");

        assertThrows(IllegalArgumentException.class, () -> {
            new AgentCapability("", "description");
        }, "Should reject empty domain name");

        assertThrows(IllegalArgumentException.class, () -> {
            new AgentCapability("Ordering", null);
        }, "Should reject null description");

        assertThrows(IllegalArgumentException.class, () -> {
            new AgentCapability("Ordering", "   ");
        }, "Should reject whitespace-only description");
    }

    @Test
    void testAgentCapabilityValidationMessages() {
        try {
            new AgentCapability(null, "description");
            fail("Should reject null domain name");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("domainName"), "Error should mention domain name");
        }

        try {
            new AgentCapability("", "description");
            fail("Should reject empty domain name");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("domainName"), "Error should mention domain name");
        }

        try {
            new AgentCapability("Ordering", null);
            fail("Should reject null description");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("description"), "Error should mention description");
        }

        try {
            new AgentCapability("Ordering", "   ");
            fail("Should reject whitespace-only description");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("description"), "Error should mention description");
        }
    }

    @Test
    void testAgentCapabilityTrimming() {
        AgentCapability capability = new AgentCapability("  Ordering  ", "  procurement, orders  ");

        assertEquals("Ordering", capability.getDomainName(), "Domain name should be trimmed");
        assertEquals("procurement, orders", capability.getDescription(), "Description should be trimmed");
    }

    @Test
    void testAgentCapabilityToString() {
        AgentCapability capability = new AgentCapability("Ordering", "procurement");

        String result = capability.toString();
        assertTrue(result.contains("Ordering"), "toString should contain domain name");
        assertTrue(result.contains("procurement"), "toString should contain description");
        assertEquals("Ordering: procurement", result, "Should format as 'domain: description'");
    }

    @Test
    void testAgentConfigurationBuilder() {
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

        assertNotNull(config, "Configuration should be created");
        assertEquals(capability, config.getCapability(), "Capability should match");
        assertEquals("http://localhost:8080/yawl", config.getEngineUrl(), "Engine URL should match");
        assertEquals("admin", config.getUsername(), "Username should match");
        assertEquals("password", config.getPassword(), "Password should match");
        assertEquals(8091, config.getPort(), "Port should match");
        assertEquals(5000L, config.getPollIntervalMs(), "Poll interval should match");
        assertEquals("5.2.0", config.getVersion(), "Version should match");
        assertEquals("Ordering", config.getAgentName(), "Agent name should derive from capability");
    }

    @Test
    void testAgentConfigurationDefaults() {
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

        assertEquals(8091, config.getPort(), "Default port should be 8091");
        assertEquals(3000L, config.getPollIntervalMs(), "Default poll interval should be 3000ms");
        assertEquals("5.2.0", config.getVersion(), "Default version should be 5.2.0");
    }

    @Test
    void testAgentConfigurationRequiredFields() {
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
            assertTrue(e.getMessage().contains("capability"), "Error should mention capability");
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
            assertTrue(e.getMessage().contains("engineUrl"), "Error should mention engineUrl");
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
            assertTrue(e.getMessage().contains("username"), "Error should mention username");
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
            assertTrue(e.getMessage().contains("password"), "Error should mention password");
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
            assertTrue(e.getMessage().contains("discoveryStrategy"), "Error should mention discoveryStrategy");
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
            assertTrue(e.getMessage().contains("eligibilityReasoner"), "Error should mention eligibilityReasoner");
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
            assertTrue(e.getMessage().contains("decisionReasoner"), "Error should mention decisionReasoner");
        }
    }

    @Test
    void testAgentConfigurationInvalidPollInterval() {
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
            assertTrue(e.getMessage().contains("pollIntervalMs"), "Error should mention pollIntervalMs");
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
            assertTrue(e.getMessage().contains("pollIntervalMs"), "Error should mention pollIntervalMs");
        }
    }

    @Test
    void testStrategyIntegration() {
        TestEligibilityReasoner eligibility = new TestEligibilityReasoner();
        TestDecisionReasoner decision = new TestDecisionReasoner();

        WorkItemRecord workItem = new WorkItemRecord();
        workItem.setTaskID("Approve_Purchase_Order");

        eligibility.setEligible(true);
        boolean isEligible = eligibility.isEligible(workItem);
        assertTrue(isEligible, "Should determine eligibility");

        decision.setOutput("<output>approved</output>");
        String output = decision.produceOutput(workItem);
        assertEquals("<output>approved</output>", output, "Should produce output");
    }

    @Test
    void testMultipleCapabilityFormats() {
        AgentCapability cap1 = new AgentCapability("Ordering", "procurement, purchase orders");
        AgentCapability cap2 = new AgentCapability("Carrier", "shipping, logistics, delivery");
        AgentCapability cap3 = new AgentCapability("Finance", "accounting, invoicing, payments");

        assertEquals("Ordering", cap1.getDomainName());
        assertEquals("Carrier", cap2.getDomainName());
        assertEquals("Finance", cap3.getDomainName());

        assertTrue(cap1.getDescription().contains("procurement"),
                "Should contain domain-specific terms");
        assertTrue(cap2.getDescription().contains("shipping"),
                "Should contain domain-specific terms");
        assertTrue(cap3.getDescription().contains("accounting"),
                "Should contain domain-specific terms");
    }

    @Test
    void testConfigurationImmutability() {
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
        assertSame(capability, retrievedCapability, "Should return same capability instance");
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
}
