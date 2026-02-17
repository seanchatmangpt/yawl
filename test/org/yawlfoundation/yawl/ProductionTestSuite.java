package org.yawlfoundation.yawl;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;
import org.yawlfoundation.yawl.build.BuildSystemTest;
import org.yawlfoundation.yawl.database.DatabaseCompatibilityTest;
import org.yawlfoundation.yawl.deployment.DeploymentReadinessTest;
import org.yawlfoundation.yawl.engine.EngineIntegrationTest;
import org.yawlfoundation.yawl.integration.JakartaEEMigrationTest;
import org.yawlfoundation.yawl.performance.PerformanceTest;

/**
 * Production Test Suite - Comprehensive production readiness tests
 *
 * This suite runs all production-critical tests following Chicago TDD principles:
 * - Real integrations (no mocks)
 * - Actual YAWL Engine instances
 * - Real database connections
 * - Performance benchmarks
 * - Deployment validation
 *
 * Test Coverage:
 * 1. Build System Tests (Maven, Ant)
 * 2. Database Compatibility (H2, PostgreSQL, MySQL, Derby, HSQLDB, Oracle)
 * 3. Jakarta EE Migration (javax -> jakarta)
 * 4. Engine Core Integration (YEngine, YNetRunner, YWorkItem)
 * 5. Virtual Thread Tests (Java 25+)
 * 6. Security Tests (OWASP, SQL injection, XSS)
 * 7. Performance Tests (throughput, latency, scalability)
 * 8. Cloud Modernization (OpenTelemetry, Spring Boot, Resilience4j)
 * 9. Deployment Readiness (health checks, configuration)
 * 10. Integration Framework (A2A, MCP, Z.AI)
 *
 * Execution: mvn test -Dtest=ProductionTestSuite
 */
@Suite
@SuiteDisplayName("YAWL Production Test Suite")
@SelectClasses({
    BuildSystemTest.class,
    DatabaseCompatibilityTest.class,
    JakartaEEMigrationTest.class,
    EngineIntegrationTest.class,
    PerformanceTest.class,
    DeploymentReadinessTest.class,
    TestAllYAWLSuites.class
})
public class ProductionTestSuite {
    // JUnit 5 suite uses annotations - no main method needed
}
