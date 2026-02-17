package org.yawlfoundation.yawl;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;
import org.yawlfoundation.yawl.engine.interfce.rest.RestResourceUnitTest;
import org.yawlfoundation.yawl.integration.autonomous.AgentLogicUnitTest;
import org.yawlfoundation.yawl.integration.cloud.CloudConfigurationUnitTest;
import org.yawlfoundation.yawl.resilience.CircuitBreakerUnitTest;
import org.yawlfoundation.yawl.resourcing.ResourceLogicUnitTest;

/**
 * Test suite for excluded modules unit tests.
 * Covers core logic without requiring full infrastructure setup.
 *
 * Test Areas:
 * - Circuit Breaker resilience pattern (state transitions, thread safety)
 * - Autonomous Agent logic (capability matching, configuration validation)
 * - REST API resource layer (error formatting, validation)
 * - Cloud Configuration (health checks, metrics collection)
 * - Resourcing Logic (resource allocation, work item distribution)
 *
 * Chicago TDD Methodology:
 * - Real objects, minimal mocking
 * - Tests focus on isolated logic units
 * - No infrastructure dependencies (database, network, etc.)
 * - Tests are deterministic and fast
 *
 * @author YAWL Foundation
 * @version 5.2
 */
@Suite
@SuiteDisplayName("Excluded Modules Unit Tests")
@SelectClasses({
    CircuitBreakerUnitTest.class,
    AgentLogicUnitTest.class,
    RestResourceUnitTest.class,
    CloudConfigurationUnitTest.class,
    ResourceLogicUnitTest.class
})
public class ExcludedModulesTestSuite {
    // JUnit 5 suite uses annotations - no main method needed
}
