package org.yawlfoundation.yawl.integration;

import junit.framework.Test;
import junit.framework.TestSuite;
import junit.textui.TestRunner;

/**
 * Comprehensive Integration Test Suite for YAWL v5.2
 *
 * Aggregates all integration tests covering:
 * - Engine lifecycle and case management
 * - Stateless engine operations
 * - Work item repository and data access
 * - Event processing and listeners
 * - Database and ORM integration
 * - Virtual threads and concurrency
 * - Security and observability
 * - Library compatibility
 *
 * Chicago TDD style - real integrations, minimal mocking.
 * Target: 70%+ test coverage across all modules.
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class IntegrationTestSuite {

    public static Test suite() {
        TestSuite suite = new TestSuite("YAWL Integration Tests");

        // Core engine integration tests
        suite.addTestSuite(EngineLifecycleIntegrationTest.class);
        suite.addTestSuite(StatelessEngineIntegrationTest.class);
        suite.addTestSuite(WorkItemRepositoryIntegrationTest.class);
        suite.addTestSuite(EventProcessingIntegrationTest.class);

        // Infrastructure integration tests
        suite.addTestSuite(OrmIntegrationTest.class);
        suite.addTestSuite(DatabaseIntegrationTest.class);
        suite.addTestSuite(DatabaseTransactionTest.class);
        suite.addTestSuite(VirtualThreadIntegrationTest.class);
        suite.addTestSuite(CommonsLibraryCompatibilityTest.class);
        suite.addTestSuite(SecurityIntegrationTest.class);

        return suite;
    }

    public static void main(String[] args) {
        TestRunner.run(suite());
    }
}
