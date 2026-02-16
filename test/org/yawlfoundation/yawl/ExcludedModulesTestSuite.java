package org.yawlfoundation.yawl;

import junit.framework.Test;
import junit.framework.TestSuite;
import org.yawlfoundation.yawl.engine.interfce.rest.RestResourceUnitTest;
import org.yawlfoundation.yawl.integration.autonomous.AgentLogicUnitTest;
import org.yawlfoundation.yawl.integration.cloud.CloudConfigurationUnitTest;
import org.yawlfoundation.yawl.resilience.CircuitBreakerUnitTest;
import org.yawlfoundation.yawl.resourcing.ResourceLogicUnitTest;

/**
 * Test suite for excluded modules unit tests.
 * Covers core logic without requiring full infrastructure setup.
 *
 * Test Areas:
 * - Circuit Breaker resilience pattern (state transitions, thread safety)
 * - Autonomous Agent logic (capability matching, configuration validation)
 * - REST API resource layer (error formatting, validation)
 * - Cloud Configuration (health checks, metrics collection)
 * - Resourcing Logic (resource allocation, work item distribution)
 *
 * Chicago TDD Methodology:
 * - Real objects, minimal mocking
 * - Tests focus on isolated logic units
 * - No infrastructure dependencies (database, network, etc.)
 * - Tests are deterministic and fast
 *
 * @author YAWL Foundation
 * @version 5.2
 */
public class ExcludedModulesTestSuite {

    public static Test suite() {
        TestSuite suite = new TestSuite("Excluded Modules Unit Tests");

        suite.addTest(CircuitBreakerUnitTest.suite());
        suite.addTest(AgentLogicUnitTest.suite());
        suite.addTest(RestResourceUnitTest.suite());
        suite.addTest(CloudConfigurationUnitTest.suite());
        suite.addTest(ResourceLogicUnitTest.suite());

        return suite;
    }

    public static void printSuiteSummary() {
        System.out.println("================================================================================");
        System.out.println("YAWL EXCLUDED MODULES UNIT TEST SUITE");
        System.out.println("================================================================================");
        System.out.println();
        System.out.println("Test Categories:");
        System.out.println("  1. CircuitBreaker Unit Tests         : 12 tests (resilience patterns)");
        System.out.println("  2. Agent Logic Unit Tests            : 11 tests (autonomous agents)");
        System.out.println("  3. REST Resource Unit Tests          : 13 tests (REST API layer)");
        System.out.println("  4. Cloud Configuration Unit Tests    : 15 tests (observability)");
        System.out.println("  5. Resource Logic Unit Tests         : 13 tests (work allocation)");
        System.out.println("                                        --------");
        System.out.println("  Total New Tests                      : 64 tests");
        System.out.println();
        System.out.println("Coverage Focus:");
        System.out.println("  - Circuit breaker state machine logic");
        System.out.println("  - Agent capability matching and configuration");
        System.out.println("  - REST error response formatting");
        System.out.println("  - Health check aggregation and status evaluation");
        System.out.println("  - Metrics collection and Prometheus export");
        System.out.println("  - Resource availability calculation");
        System.out.println("  - Work item allocation and deallocation");
        System.out.println();
        System.out.println("Testing Methodology:");
        System.out.println("  - Chicago TDD (Detroit School)");
        System.out.println("  - Real objects, minimal mocking");
        System.out.println("  - No infrastructure dependencies");
        System.out.println("  - Tests focus on business logic");
        System.out.println("  - Deterministic and fast execution");
        System.out.println();
        System.out.println("Run Command:");
        System.out.println("  ant unitTest");
        System.out.println("  OR");
        System.out.println("  java -cp classes:lib/* junit.textui.TestRunner \\");
        System.out.println("    org.yawlfoundation.yawl.ExcludedModulesTestSuite");
        System.out.println();
        System.out.println("================================================================================");
    }

    public static void main(String[] args) {
        printSuiteSummary();
        System.out.println();
        System.out.println("Running test suite...");
        System.out.println();

        long startTime = System.currentTimeMillis();
        junit.textui.TestRunner.run(suite());
        long endTime = System.currentTimeMillis();

        System.out.println();
        System.out.println("================================================================================");
        System.out.println("Test suite completed in " + (endTime - startTime) / 1000.0 + " seconds");
        System.out.println("================================================================================");
    }
}
