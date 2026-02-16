package org.yawlfoundation.yawl.integration.autonomous;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

/**
 * Comprehensive test suite for Generic Autonomous Agent Framework.
 * Chicago TDD style - real integrations, minimal mocking.
 *
 * @author YAWL Foundation
 * @version 5.2
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
}
