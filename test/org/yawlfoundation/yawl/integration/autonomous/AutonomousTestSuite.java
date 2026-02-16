package org.yawlfoundation.yawl.integration.autonomous;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * Comprehensive test suite for Generic Autonomous Agent Framework.
 * Chicago TDD style - real integrations, minimal mocking.
 *
 * @author YAWL Foundation
 * @version 5.2
 *
 * JUnit 5 Test Suite (with JUnit 3 backward compatibility)
 */
@Suite
@SelectClasses({
    AgentCapabilityTest.class,
    AgentConfigurationTest.class,
    RetryPolicyTest.class,
    CircuitBreakerTest.class,
    ZaiEligibilityReasonerTest.class
})
public class AutonomousTestSuite {

    /**
     * JUnit 3 compatible suite() method for backward compatibility
     */
    public static Test suite() {
        TestSuite suite = new TestSuite("Autonomous Test Suite");
        suite.addTestSuite(AgentCapabilityTest.class);
        suite.addTestSuite(AgentConfigurationTest.class);
        suite.addTestSuite(RetryPolicyTest.class);
        suite.addTestSuite(CircuitBreakerTest.class);
        suite.addTestSuite(ZaiEligibilityReasonerTest.class);
        return suite;
    }
}
