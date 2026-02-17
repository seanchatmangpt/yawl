package org.yawlfoundation.yawl.performance;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Complete performance test suite for YAWL Engine.
 *
 * Includes:
 * - Baseline measurements (latency, throughput, memory)
 * - Load tests (sustained, burst, ramp-up)
 * - Scalability tests
 *
 * Usage:
 *   mvn test -Dtest=PerformanceTestSuite
 *
 * @author YAWL Performance Team
 * @version 5.2
 * @since 2026-02-16
 */
@Suite
@SuiteDisplayName("YAWL Performance Test Suite")
@SelectClasses({
    EnginePerformanceBaseline.class,
    LoadTestSuite.class,
    ScalabilityTest.class
})
public class PerformanceTestSuite {
    // JUnit 5 suite uses annotations - no main method needed
}
