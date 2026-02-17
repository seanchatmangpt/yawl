/*
 * Copyright (c) 2004-2026 The YAWL Foundation. All rights reserved.
 *
 * This file is part of YAWL. YAWL is free software: you can
 * redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation.
 */

package org.yawlfoundation.yawl.integration.multimodule;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Multi-module integration test suite for YAWL v6.0.0.
 *
 * Aggregates cross-cutting integration tests that span:
 * - yawl-elements (specification/net/task construction)
 * - yawl-engine (YEngine, YNetRunner, YWorkItem lifecycle)
 * - Persistence layer (H2 in-memory JDBC)
 *
 * Tests in this suite are designed to run in the standard Maven build
 * without Docker or external services.
 *
 * Run the full multi-module suite:
 * <pre>
 *   mvn test -Dtest=MultiModuleTestSuite
 * </pre>
 *
 * Run individual classes:
 * <pre>
 *   mvn test -Dtest=MultiModuleIntegrationTest
 *   mvn test -Dtest=EndToEndWorkflowExecutionTest
 *   mvn test -Dtest=TestDataSeedingPatterns
 * </pre>
 *
 * @author YAWL Foundation Test Team
 * @version 6.0.0
 * @since 2026-02-17
 */
@Suite
@SuiteDisplayName("YAWL v6 Multi-Module Integration Tests")
@SelectClasses({
    MultiModuleIntegrationTest.class,
    EndToEndWorkflowExecutionTest.class,
    TestDataSeedingPatterns.class
})
public class MultiModuleTestSuite {
    // JUnit 5 Platform Suite — no main method required.
}
