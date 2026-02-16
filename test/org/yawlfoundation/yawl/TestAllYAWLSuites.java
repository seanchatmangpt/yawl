package org.yawlfoundation.yawl;

import org.yawlfoundation.yawl.authentication.AuthenticationTestSuite;
import org.yawlfoundation.yawl.elements.ElementsTestSuite;
import org.yawlfoundation.yawl.elements.state.StateTestSuite;
import org.yawlfoundation.yawl.engine.EngineTestSuite;
import org.yawlfoundation.yawl.exceptions.ExceptionTestSuite;
import org.yawlfoundation.yawl.integration.IntegrationTestSuite;
import org.yawlfoundation.yawl.logging.LoggingTestSuite;
import org.yawlfoundation.yawl.schema.SchemaTestSuite;
import org.yawlfoundation.yawl.stateless.StatelessTestSuite;
import org.yawlfoundation.yawl.unmarshal.UnmarshallerTestSuite;
import org.yawlfoundation.yawl.util.UtilTestSuite;
import junit.framework.Test;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * Master YAWL Test Suite - Aggregates all test suites
 *
 * Includes:
 * - Core engine and elements tests
 * - Stateless engine tests
 * - Integration tests (Chicago TDD style)
 * - Autonomous agent tests
 * - Authentication, logging, schema tests
 * - Utility and worklist tests
 *
 * Target: 70%+ overall test coverage
 *
 * Author: Lachlan Aldred (original)
 * Updated: YAWL Foundation v5.2
 * Date: 9/05/2003 (original), 2026-02-16 (updated)
 */
public class TestAllYAWLSuites extends TestSuite {

    public TestAllYAWLSuites(String name) {
        super(name);
    }

    public static Test suite() {
        TestSuite suite = new TestSuite("All YAWL Test Suites");

        // Core component tests
        suite.addTest(ElementsTestSuite.suite());
        suite.addTest(StateTestSuite.suite());
        suite.addTest(StatelessTestSuite.suite());
        suite.addTest(EngineTestSuite.suite());
        suite.addTest(ExceptionTestSuite.suite());
        suite.addTest(LoggingTestSuite.suite());
        suite.addTest(SchemaTestSuite.suite());
        suite.addTest(UnmarshallerTestSuite.suite());
        suite.addTest(UtilTestSuite.suite());
        suite.addTest(org.yawlfoundation.yawl.swingWorklist.WorklistTestSuite.suite());
        suite.addTest(AuthenticationTestSuite.suite());

        // Integration tests (Chicago TDD - real integrations)
        suite.addTest(IntegrationTestSuite.suite());

        return suite;
    }

    public static void main(String args[]) {
        TestRunner runner = new TestRunner();
        runner.doRun(suite());
        System.exit(0);
    }
}
