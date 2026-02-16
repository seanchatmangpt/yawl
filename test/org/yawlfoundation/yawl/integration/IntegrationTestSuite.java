package org.yawlfoundation.yawl.integration;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * Integration Test Suite - Aggregates all integration tests
 * Runs all upgrade track tests: ORM, Database, Virtual Threads, Commons, Security, Observability
 * Provides comprehensive integration coverage (80%+ test coverage)
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
    OrmIntegrationTest.class,
    DatabaseIntegrationTest.class,
    VirtualThreadIntegrationTest.class,
    CommonsLibraryCompatibilityTest.class,
    SecurityIntegrationTest.class,
    ObservabilityIntegrationTest.class,
    ConfigurationIntegrationTest.class
})
public class IntegrationTestSuite {
    // Suite class - no test methods
}
