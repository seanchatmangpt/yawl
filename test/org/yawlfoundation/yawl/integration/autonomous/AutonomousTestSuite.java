package org.yawlfoundation.yawl.integration.autonomous;

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * Test suite for autonomous agent integration tests.
 *
 * Registers all JUnit 4 (TestCase) tests in the autonomous package.
 * Run via: java -cp ... junit.textui.TestRunner
 *   org.yawlfoundation.yawl.integration.autonomous.AutonomousTestSuite
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class AutonomousTestSuite {

    public static Test suite() {
        TestSuite suite = new TestSuite("Autonomous Agent Tests");
        suite.addTestSuite(StaticMappingReasonerTest.class);
        suite.addTestSuite(AgentRegistryTest.class);
        return suite;
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(suite());
    }
}
