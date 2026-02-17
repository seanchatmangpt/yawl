package org.yawlfoundation.yawl;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;
import org.yawlfoundation.yawl.authentication.AuthenticationTestSuite;
import org.yawlfoundation.yawl.chaos.ChaosTestSuite;
import org.yawlfoundation.yawl.containers.ContainerTestSuite;
import org.yawlfoundation.yawl.elements.ElementsTestSuite;
import org.yawlfoundation.yawl.elements.state.StateTestSuite;
import org.yawlfoundation.yawl.engine.EngineTestSuite;
import org.yawlfoundation.yawl.exceptions.ExceptionTestSuite;
import org.yawlfoundation.yawl.integration.multimodule.MultiModuleTestSuite;
import org.yawlfoundation.yawl.logging.LoggingTestSuite;
import org.yawlfoundation.yawl.parametrized.ParametrizedTestSuite;
import org.yawlfoundation.yawl.reporting.ReportingTestSuite;
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
 * - Container integration tests (PostgreSQL, MySQL via TestContainers)
 * - Parametrized tests (DB backends, performance regression, Java features)
 * - Chaos engineering tests (network delay, service failure)
 * - Multi-module integration tests (engine + elements + persistence)
 * - Test reporting and analytics (JUnit XML parsing, flaky detection)
 *
 * Target: 80%+ overall test coverage
 *
 * Author: Lachlan Aldred (original)
 * Updated: YAWL Foundation v6.0.0
 * Date: 9/05/2003 (original), 2026-02-17 (v6.0.0 advanced testing framework)
 */
@Suite
@SuiteDisplayName("All YAWL Test Suites")
@SelectClasses({
    // Core domain tests (pre-existing)
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
    AuthenticationTestSuite.class,

    // v6.0.0 Advanced Testing Framework
    ParametrizedTestSuite.class,
    MultiModuleTestSuite.class,
    ReportingTestSuite.class,

    // Container and chaos tests (optional: require Docker / tagged execution)
    ContainerTestSuite.class,
    ChaosTestSuite.class
})
public class TestAllYAWLSuites {
    // JUnit 5 suite uses annotations - no main method needed
}
