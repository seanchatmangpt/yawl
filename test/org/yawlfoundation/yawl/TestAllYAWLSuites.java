package org.yawlfoundation.yawl;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;
import org.yawlfoundation.yawl.authentication.AuthenticationTestSuite;
import org.yawlfoundation.yawl.elements.ElementsTestSuite;
import org.yawlfoundation.yawl.elements.state.StateTestSuite;
import org.yawlfoundation.yawl.engine.EngineTestSuite;
import org.yawlfoundation.yawl.exceptions.ExceptionTestSuite;
import org.yawlfoundation.yawl.logging.LoggingTestSuite;
import org.yawlfoundation.yawl.schema.SchemaTestSuite;
import org.yawlfoundation.yawl.stateless.StatelessTestSuite;
import org.yawlfoundation.yawl.swingWorklist.WorklistTestSuite;
import org.yawlfoundation.yawl.unmarshal.UnmarshallerTestSuite;
import org.yawlfoundation.yawl.util.UtilTestSuite;

/**
 * Master YAWL Test Suite - Aggregates all test suites
 *
 * Includes:
 * - Core engine and elements tests
 * - Stateless engine tests
 * - Authentication, logging, schema tests
 * - Utility and worklist tests
 *
 * Target: 70%+ overall test coverage
 *
 * Author: Lachlan Aldred (original)
 * Updated: YAWL Foundation v6.0.0-Alpha
 * Date: 9/05/2003 (original), 2026-02-16 (updated)
 */
@Suite
@SuiteDisplayName("All YAWL Test Suites")
@SelectClasses({
    ElementsTestSuite.class,
    StateTestSuite.class,
    StatelessTestSuite.class,
    EngineTestSuite.class,
    ExceptionTestSuite.class,
    LoggingTestSuite.class,
    SchemaTestSuite.class,
    UnmarshallerTestSuite.class,
    UtilTestSuite.class,
    WorklistTestSuite.class,
    AuthenticationTestSuite.class
})
public class TestAllYAWLSuites {
    // JUnit 5 suite uses annotations - no main method needed
}
