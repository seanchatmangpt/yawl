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

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * JUnit 5 Test Suite for Autonomous Agent integration tests (V6 feature set).
 *
 * Covers:
 * - AgentCapability record semantics and validation
 * - AgentConfiguration builder pattern
 * - StaticMappingReasoner eligibility logic
 * - AgentRegistry heartbeat and discovery
 * - CircuitBreaker state machine
 * - RetryPolicy exponential backoff
 *
 * All tests use real YAWL objects - no mocks.
 * Chicago TDD methodology.
 *
 * @author YAWL Foundation
 * @version 6.0
 */
@Suite
@SuiteDisplayName("Autonomous Agent Integration Tests")
@SelectClasses({
    AgentCapabilityTest.class,
    AgentConfigurationTest.class,
    StaticMappingReasonerTest.class,
    AgentRegistryTest.class,
    CircuitBreakerTest.class,
    RetryPolicyTest.class
})
public class AutonomousTestSuite {
    // JUnit 5 suite uses annotations - no main method needed
}
