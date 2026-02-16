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

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * Comprehensive test suite for Generic Autonomous Agent Framework.
 * Chicago TDD style - real integrations, minimal mocking.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class AutonomousTestSuite {

    public static Test suite() {
        TestSuite suite = new TestSuite("Autonomous Agent Framework Tests");

        suite.addTestSuite(AgentCapabilityTest.class);
        suite.addTestSuite(AgentConfigurationTest.class);
        suite.addTestSuite(AgentFactoryTest.class);
        suite.addTestSuite(GenericPartyAgentTest.class);
        suite.addTestSuite(RetryPolicyTest.class);
        suite.addTestSuite(CircuitBreakerTest.class);
        suite.addTestSuite(ZaiEligibilityReasonerTest.class);
        suite.addTestSuite(PollingDiscoveryStrategyTest.class);
        suite.addTestSuite(StaticMappingReasonerTest.class);
        suite.addTestSuite(XmlOutputGeneratorTest.class);
        suite.addTestSuite(TemplateOutputGeneratorTest.class);

        return suite;
    }

    public static void main(String[] args) {
        TestRunner.run(suite());
    }
}
