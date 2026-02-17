package org.yawlfoundation.yawl;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;
import org.yawlfoundation.yawl.engine.TestConcurrentCaseExecution;
import org.yawlfoundation.yawl.engine.TestYPersistenceManager;
import org.yawlfoundation.yawl.exceptions.TestYAWLExceptionHandling;

/**
 * Comprehensive test suite for coverage improvement initiative.
 * Targets 60%+ overall coverage with focus on critical gaps.
 *
 * Test Areas:
 * - Persistence layer (YPersistenceManager): 70%+ target
 * - Concurrency and thread safety: 50%+ target
 * - Database transactions and ACID compliance: 60%+ target
 * - Exception handling and recovery: 60%+ target
 *
 * Chicago TDD Methodology:
 * - ALL tests use REAL YAWL Engine
 * - ALL tests use REAL H2 in-memory database
 * - NO MOCKS in production test code
 * - Tests verify actual integrations and behaviors
 *
 * @author YAWL Test Team
 * Date: 2026-02-16
 */
@Suite
@SuiteDisplayName("YAWL Coverage Improvement Tests")
@SelectClasses({
    TestYPersistenceManager.class,
    TestConcurrentCaseExecution.class,
    TestYAWLExceptionHandling.class
})
public class CoverageImprovementTestSuite {
    // JUnit 5 suite uses annotations - no main method needed
}
