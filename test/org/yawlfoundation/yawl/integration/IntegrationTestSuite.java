package org.yawlfoundation.yawl.integration;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Comprehensive Integration Test Suite for YAWL v5.2
 *
 * Aggregates all integration tests covering:
 * - Engine lifecycle and case management
 * - Stateless engine operations
 * - Work item repository and data access
 * - Jakarta EE migration
 *
 * Chicago TDD style - real integrations, minimal mocking.
 * Target: 70%+ test coverage across all modules.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
@Suite
@SuiteDisplayName("YAWL Integration Tests")
@SelectClasses({
    StatelessEngineIntegrationTest.class,
    WorkItemRepositoryIntegrationTest.class,
    JakartaEEMigrationTest.class
})
public class IntegrationTestSuite {
    // JUnit 5 suite uses annotations - no main method needed
}
